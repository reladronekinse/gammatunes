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

/**
 * Поднимает бэкенд (backend_server.py) прямо на устройстве через Chaquopy,
 * вместо того чтобы требовать отдельный ПК в той же Wi-Fi сети.
 *
 * Сервер слушает только 127.0.0.1 — наружу из телефона ничего не торчит.
 */
object LocalBackend {

    const val PORT = 8765
    const val BASE_URL = "http://127.0.0.1:$PORT/"

    private const val TAG = "LocalBackend"
    private val started = AtomicBoolean(false)

    /** Если фоновый поток с сервером упал — здесь будет текст причины (для показа в UI). */
    @Volatile
    var lastError: String? = null
        private set

    /** true, пока поток с сервером жив и не упал с исключением. */
    @Volatile
    private var crashed = false

    /**
     * Живой статус запуска — можно показать в UI без adb, чтобы видеть, на
     * каком именно шаге всё встало.
     */
    private val _status = MutableStateFlow("Ожидание запуска…")
    val status: StateFlow<String> = _status

    private val healthCheckClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        // Тот же обход системного прокси/VPN, что и в ApiClient — иначе
        // health-check к localhost может зависать/падать при включённом VPN.
        .proxy(Proxy.NO_PROXY)
        .build()

    /**
     * Запускает Python-интерпретатор (если ещё не запущен) и сам HTTP-сервер
     * в отдельном фоновом потоке-демоне. Безопасно вызывать несколько раз —
     * реально стартует только один раз за жизнь процесса.
     */
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        Thread({
            try {
                _status.value = "Стартую Python-интерпретатор Chaquopy…"
                Log.i(TAG, _status.value)
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }

                // Импортируем тяжёлые пакеты по отдельности, а не одним модулем
                // backend_server — так видно (в статусе и в logcat), какой именно
                // импорт долго думает или падает, если что-то пойдёт не так.
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
                // serve_forever() внутри — поток блокируется здесь на всё время жизни приложения.
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

    /**
     * Ждёт, пока встроенный бэкенд начнёт отвечать на /health, но не дольше
     * [timeoutMs]. Если фоновый поток уже упал с исключением, выходим сразу,
     * не дожидаясь таймаута.
     */
    suspend fun awaitReady(timeoutMs: Long = 30_000, pollEveryMs: Long = 500): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (crashed) return false
            if (isHealthy()) return true
            delay(pollEveryMs)
        }
        return isHealthy()
    }

    /**
     * ВАЖНО: сетевой вызов обязательно на Dispatchers.IO — иначе на главном
     * потоке Android кидает NetworkOnMainThreadException, которое здесь же
     * тихо ловится как обычный Exception и всегда даёт "не готово", вне
     * зависимости от того, поднялся сервер или нет.
     */
    private suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${BASE_URL}health").build()
            healthCheckClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
