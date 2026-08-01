package com.gammatunes.app.backend

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy


object LocalBackend {

    const val PORT = 8765
    const val BASE_URL = "http://127.0.0.1:$PORT/"

    private const val TAG = "LocalBackend"
    private val started = AtomicBoolean(false)


    @Volatile
    var lastError: String? = null
        private set


    @Volatile
    private var crashed = false


    private val _status = MutableStateFlow("Ожидание запуска…")
    val status: StateFlow<String> = _status

    private val healthCheckClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)


        .proxy(Proxy.NO_PROXY)
        .build()


    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        Thread({
            try {
                _status.value = "Стартую Python-интерпретатор Chaquopy…"
                Log.i(TAG, _status.value)
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }


                val py = Python.getInstance()

                _status.value = "Импортирую ytmusicapi…"
                Log.i(TAG, _status.value)
                py.getModule("ytmusicapi")

                _status.value = "Импортирую yt-dlp…"
                Log.i(TAG, _status.value)
                py.getModule("yt_dlp")

                _status.value = "Импортирую backend_server…"
                Log.i(TAG, _status.value)
                val module = py.getModule("backend_server")

                _status.value = "Поднимаю HTTP-сервер на порту $PORT…"
                Log.i(TAG, _status.value)

                module.callAttr("start", PORT)
            } catch (t: Throwable) {
                crashed = true
                lastError = t.message ?: t.toString()
                _status.value = "Упал на шаге: ${_status.value}"
                Log.e(TAG, "Встроенный бэкенд упал: $lastError", t)
            }
        }, "local-backend-thread").apply {
            isDaemon = true
            start()
        }
    }


    suspend fun awaitReady(timeoutMs: Long = 30_000, pollEveryMs: Long = 500): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (crashed) return false
            if (isHealthy()) return true
            delay(pollEveryMs)
        }
        return isHealthy()
    }


    private suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${BASE_URL}health").build()
            healthCheckClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}

