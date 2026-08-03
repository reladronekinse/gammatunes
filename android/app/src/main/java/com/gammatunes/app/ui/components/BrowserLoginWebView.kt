package com.gammatunes.app.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gammatunes.app.ui.i18n.LocalStrings
import kotlinx.coroutines.delay

/**
 * WebView-логин на music.youtube.com.
 *
 * Google часто отвечает «This browser or app may not be secure», потому что
 * системный WebView:
 *  - добавляет "; wv" в User-Agent;
 *  - шлёт заголовок X-Requested-With: <package name>.
 *
 * Здесь:
 *  - подставляем обычный Chrome Mobile UA без "wv";
 *  - на каждую навигацию грузим URL с пустым X-Requested-With;
 *  - если всё равно упёрлись в блок Google — показываем короткую подсказку
 *    про ручную вставку headers.
 */
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
    var googleBlocked by remember { mutableStateOf(false) }

    fun collectCookieHeader(): String? {
        val cm = CookieManager.getInstance()
        cm.flush()
        val domains = listOf(
            "https://music.youtube.com",
            "https://www.youtube.com",
            "https://youtube.com",
            "https://accounts.google.com",
            "https://www.google.com",
            "https://google.com",
        )
        val jar = linkedMapOf<String, String>()
        for (domain in domains) {
            val raw = cm.getCookie(domain) ?: continue
            for (part in raw.split(";")) {
                val trimmed = part.trim()
                if (trimmed.isEmpty() || "=" !in trimmed) continue
                val eq = trimmed.indexOf('=')
                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (name.isNotEmpty()) jar[name] = value
            }
        }
        if (jar.isEmpty()) return null
        // Без __Secure-3PAPISID/SAPISID ytmusicapi не сможет собрать SAPISIDHASH.
        val hasSapi = jar.containsKey("__Secure-3PAPISID") || jar.containsKey("SAPISID")
        val hasSession = jar.containsKey("SID") || jar.containsKey("__Secure-3PSID") ||
            jar.containsKey("LOGIN_INFO")
        if (!hasSapi || !hasSession) return null
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun buildHeadersRaw(cookie: String, userAgent: String): String = buildString {
        append("Cookie: ")
        append(cookie)
        append("\n")
        append("User-Agent: ")
        append(userAgent)
        append("\n")
        append("Accept: */*\n")
        append("Accept-Language: en-US,en;q=0.9\n")
        append("Content-Type: application/json\n")
        append("X-Goog-AuthUser: 0\n")
        append("x-origin: https://music.youtube.com\n")
    }

    fun tryCapture(webView: WebView, force: Boolean = false): Boolean {
        if (captured) return true
        val cookie = collectCookieHeader() ?: return false
        val url = webView.url.orEmpty()
        val onMusic = url.startsWith("https://music.youtube.com")
        if (!force && !onMusic) return false

        val ua = webView.settings.userAgentString ?: CHROME_MOBILE_UA
        captured = true
        status = strings.sessionActive
        onCookiesCaptured(buildHeadersRaw(cookie, ua))
        return true
    }

    fun loadWithoutWebViewMarker(view: WebView, url: String) {
        // Пустой X-Requested-With убирает главный сигнал «это WebView» для Google.
        view.loadUrl(url, mapOf("X-Requested-With" to ""))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(strings.browserLoginTitle, maxLines = 1)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val wv = webViewRef
                                if (wv == null) {
                                    status = strings.browserLoginHint
                                    return@TextButton
                                }
                                isSaving = true
                                if (!tryCapture(wv, force = true)) {
                                    isSaving = false
                                    status = strings.needHeaders
                                }
                            },
                            enabled = !isSaving && !captured,
                        ) {
                            Text(strings.saveAndLogin)
                        }
                    },
                )
                Text(
                    text = if (googleBlocked) strings.browserBlockedHint else status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (googleBlocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 4,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(false)
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }
                            // Важно: без "; wv" и без десктопного UA —
                            // Google меньше подозревает automation/WebView.
                            settings.userAgentString = CHROME_MOBILE_UA

                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                cm.setAcceptThirdPartyCookies(this, true)
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url?.toString() ?: return false
                                    if (view != null) {
                                        loadWithoutWebViewMarker(view, target)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (view == null || captured) return

                                    // Детект страницы блокировки Google.
                                    view.evaluateJavascript(
                                        "(function(){try{return document.body?document.body.innerText.slice(0,500):'';}catch(e){return '';}})()",
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

                                    if (url != null && url.startsWith("https://music.youtube.com")) {
                                        tryCapture(view, force = false)
                                    }
                                }
                            }
                            webViewRef = this
                            loadWithoutWebViewMarker(this, "https://music.youtube.com")
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

    LaunchedEffect(Unit) {
        while (!captured) {
            delay(2000)
            val wv = webViewRef ?: continue
            val url = wv.url.orEmpty()
            if (url.startsWith("https://music.youtube.com")) {
                if (tryCapture(wv, force = false)) break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }
}

// Обычный Chrome на Android — без маркера "; wv", который WebView добавляет сам.
private const val CHROME_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
