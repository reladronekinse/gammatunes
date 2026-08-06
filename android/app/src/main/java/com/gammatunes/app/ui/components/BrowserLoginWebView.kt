package com.gammatunes.app.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gammatunes.app.ui.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserLoginDialog(
    onDismiss: () -> Unit,
    onCookiesCaptured: (headersRaw: String) -> Unit,
) {
    val strings = LocalStrings.current
    var status by remember { mutableStateOf(strings.browserLoginHint) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var captured by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(true) }
    var googleBlocked by remember { mutableStateOf(false) }
    var visitorData by remember { mutableStateOf("") }
    var dataSyncId by remember { mutableStateOf("") }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun musicCookie(): String =
        CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()

    fun isUsableCookie(cookie: String): Boolean {
        if (cookie.isBlank()) return false

        return cookie.contains("SAPISID=") || cookie.contains("__Secure-3PAPISID=")
    }

    fun buildHeadersRaw(cookie: String): String = buildString {
        append("Cookie: ")
        append(cookie)
        append("\n")
        append("User-Agent: ")
        append(DESKTOP_CHROME_UA)
        append("\n")
        append("Accept: */*\n")
        append("Accept-Language: en-US,en;q=0.9\n")
        append("Content-Type: application/json\n")
        append("X-Goog-AuthUser: 0\n")
        append("x-origin: https://music.youtube.com\n")
        append("Origin: https://music.youtube.com\n")
        if (visitorData.isNotBlank() && visitorData != "undefined" && visitorData != "null") {
            append("X-Goog-Visitor-Id: ")
            append(visitorData)
            append("\n")
        }
        if (dataSyncId.isNotBlank() && dataSyncId != "undefined" && dataSyncId != "null") {
            append("X-Goog-DataSyncId: ")
            append(dataSyncId)
            append("\n")
        }
    }

    fun completeLogin(force: Boolean = false) {
        if (captured || isSaving) return
        val cookie = musicCookie()
        if (!isUsableCookie(cookie)) {
            if (force) status = strings.needHeaders
            return
        }
        captured = true
        isSaving = true
        status = strings.sessionActive

        mainHandler.postDelayed({
            onCookiesCaptured(buildHeadersRaw(cookie))
        }, 400)
    }

    Dialog(
        onDismissRequest = {

            if (!captured) completeLogin(force = true)
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(strings.browserLoginTitle, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!captured) completeLogin(force = true)
                            onDismiss()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { completeLogin(force = true) },
                            enabled = !isSaving && !captured,
                        ) {
                            Text(strings.saveAndLogin)
                        }
                    },
                )
                if (pageLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = if (googleBlocked) strings.browserBlockedHint else status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (googleBlocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 4,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }


                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                cm.setAcceptThirdPartyCookies(this, true)
                            }

                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onRetrieveVisitorData(value: String?) {
                                        if (!value.isNullOrBlank() && value != "undefined" && value != "null") {
                                            mainHandler.post { visitorData = value }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onRetrieveDataSyncId(value: String?) {
                                        if (!value.isNullOrBlank() && value != "undefined" && value != "null") {
                                            mainHandler.post {
                                                dataSyncId = value.substringBefore("||")
                                            }
                                        }
                                    }
                                },
                                "Android",
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    pageLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    pageLoading = false
                                    if (view == null || captured) return


                                    view.loadUrl(
                                        "javascript:Android.onRetrieveVisitorData(window.yt && window.yt.config_ && window.yt.config_.VISITOR_DATA)",
                                    )
                                    view.loadUrl(
                                        "javascript:Android.onRetrieveDataSyncId(window.yt && window.yt.config_ && window.yt.config_.DATASYNC_ID)",
                                    )

                                    view.evaluateJavascript(
                                        "(function(){try{return document.body?document.body.innerText.slice(0,600):'';}catch(e){return '';}})()",
                                    ) { text ->
                                        val plain = text.trim('"').replace("\\n", " ")
                                        if (
                                            plain.contains("may not be secure", ignoreCase = true) ||
                                            plain.contains("couldn't sign you in", ignoreCase = true) ||
                                            plain.contains("This browser or app", ignoreCase = true) ||
                                            plain.contains("небезопасн", ignoreCase = true)
                                        ) {
                                            googleBlocked = true
                                        }
                                    }


                                    if (url != null && url.contains("music.youtube.com") && !captured) {
                                        val cookie = musicCookie()
                                        if (isUsableCookie(cookie)) {

                                            mainHandler.postDelayed({
                                                if (!captured) completeLogin(force = false)
                                            }, 800)
                                        }
                                    }
                                }
                            }
                            webViewRef = this

                            loadUrl(LOGIN_URL)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    update = { webViewRef = it },
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }
}

private const val DESKTOP_CHROME_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
