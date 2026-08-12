package com.gammatunes.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases of reladronekinse/gammatunes for a newer build, downloads the APK
 * asset and triggers a "seamless" in-app install via the PackageInstaller session API —
 * no browser, no file manager, only the standard system install confirmation.
 */
object AppUpdateRepository {

    private const val TAG = "AppUpdateRepository"
    private const val OWNER = "reladronekinse"
    private const val REPO = "gammatunes"
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private const val PREFS = "ytm_update_settings"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_SKIPPED_TAG = "skipped_tag"
    private const val MIN_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Checking : UpdateState()
        data object UpToDate : UpdateState()
        data class Available(val release: GithubRelease, val asset: GithubAsset) : UpdateState()
        data class Downloading(val progressPercent: Int) : UpdateState()
        data object AwaitingInstallPermission : UpdateState()
        data object Installing : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    private val gson = Gson()

    // Kept around so the install step can be resumed once permission is granted.
    private var pendingApkFile: File? = null

    /** Silent, throttled check meant to be called on app startup. */
    fun autoCheck(context: Context) {
        val prefs = prefs(context)
        val last = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (System.currentTimeMillis() - last < MIN_CHECK_INTERVAL_MS) return
        checkForUpdate(context, silent = true)
    }

    fun checkForUpdate(context: Context, silent: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        val appContext = context.applicationContext
        _state.value = UpdateState.Checking
        repoScope.launch {
            try {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "GammaTunes-Android")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    prefs(appContext).edit()
                        .putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis())
                        .apply()

                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty body")
                    val release = gson.fromJson(bodyString, GithubRelease::class.java)
                        ?: throw IOException("Malformed release JSON")
                    val asset = release.apkAsset
                        ?: throw IOException("No .apk asset in latest release")

                    val currentVersion = currentVersionName(appContext)
                    val skippedTag = prefs(appContext).getString(KEY_SKIPPED_TAG, null)

                    _state.value = when {
                        !isNewer(release.tag_name, currentVersion) -> UpdateState.UpToDate
                        silent && release.tag_name == skippedTag -> UpdateState.UpToDate
                        else -> UpdateState.Available(release, asset)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "checkForUpdate failed", t)
                _state.value = if (silent) {
                    UpdateState.Idle
                } else {
                    UpdateState.Error(t.message ?: "Update check failed")
                }
            }
        }
    }

    fun skipRelease(context: Context, release: GithubRelease) {
        prefs(context).edit().putString(KEY_SKIPPED_TAG, release.tag_name).apply()
        _state.value = UpdateState.Idle
    }

    fun downloadAndInstall(context: Context, release: GithubRelease, asset: GithubAsset) {
        val appContext = context.applicationContext
        if (_state.value is UpdateState.Downloading) return
        repoScope.launch {
            try {
                _state.value = UpdateState.Downloading(0)
                val apkFile = File(appContext.cacheDir, "gammatunes_update.apk")
                val tmpFile = File(appContext.cacheDir, "gammatunes_update.apk.part")
                if (tmpFile.exists()) tmpFile.delete()

                val request = Request.Builder().url(asset.browser_download_url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                    tmpFile.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                    _state.value = UpdateState.Downloading(pct)
                                }
                            }
                        }
                    }
                }
                if (apkFile.exists()) apkFile.delete()
                if (!tmpFile.renameTo(apkFile)) {
                    tmpFile.copyTo(apkFile, overwrite = true)
                    tmpFile.delete()
                }
                if (!apkFile.exists() || apkFile.length() < 1024L) {
                    throw IOException("Downloaded file looks invalid")
                }

                installApk(appContext, apkFile)
            } catch (t: Throwable) {
                Log.e(TAG, "downloadAndInstall failed", t)
                _state.value = UpdateState.Error(t.message ?: "Download failed")
            }
        }
    }

    /** Call after the user grants the "install unknown apps" permission for this app. */
    fun resumeInstallAfterPermission(context: Context) {
        val apkFile = pendingApkFile
        if (apkFile != null && apkFile.exists()) {
            installApk(context.applicationContext, apkFile)
        } else {
            _state.value = UpdateState.Idle
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingApkFile = apkFile
            _state.value = UpdateState.AwaitingInstallPermission
            return
        }

        try {
            _state.value = UpdateState.Installing
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.setAppPackageName(context.packageName)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("gammatunes_update", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }

                val intent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = InstallResultReceiver.ACTION_INSTALL_STATUS
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
            pendingApkFile = null
        } catch (t: Throwable) {
            Log.e(TAG, "installApk failed", t)
            _state.value = UpdateState.Error(t.message ?: "Install failed")
        }
    }

    internal fun onInstallSuccess() {
        pendingApkFile = null
        _state.value = UpdateState.Idle
    }

    internal fun onInstallFailed(message: String?) {
        _state.value = UpdateState.Error(message ?: "Install failed")
    }

    internal fun onInstallStatusChanged(status: Int) {
        // Session was committed but is still pending user confirmation from the system UI.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            _state.value = UpdateState.Installing
        }
    }

    private fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (t: Throwable) {
        "0"
    }

    private fun normalize(version: String): String = version.trim().removePrefix("v").removePrefix("V")

    /** Best-effort semantic-ish comparison; falls back to plain inequality. */
    internal fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = normalize(remoteTag)
        val current = normalize(currentVersion)
        if (remote == current) return false

        fun numericParts(v: String): List<Int> =
            v.takeWhile { it.isDigit() || it == '.' }
                .split(".")
                .mapNotNull { it.toIntOrNull() }

        val remoteNums = numericParts(remote)
        val currentNums = numericParts(current)
        if (remoteNums.isEmpty() || currentNums.isEmpty()) {
            return remote != current
        }
        val len = maxOf(remoteNums.size, currentNums.size)
        for (i in 0 until len) {
            val r = remoteNums.getOrElse(i) { 0 }
            val c = currentNums.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return remote != current
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
