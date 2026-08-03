package com.gammatunes.app.network

import com.gammatunes.app.backend.LocalBackend
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Бэкенд теперь встроен в само приложение (см. backend/LocalBackend.kt) и
 * поднимается на телефоне автоматически при запуске APK — никакой ПК рядом
 * не нужен. Поэтому базовый адрес — просто localhost самого устройства.
 *
 * Если когда-нибудь понадобится вернуться к внешнему бэкенду на ПК (папка
 * backend/ в корне репозитория по-прежнему рабочая, для разработки/отладки
 * логики на десктопе), достаточно поменять LocalBackend.BASE_URL здесь на
 * "http://<IP_компьютера>:8000/".
 */
object ApiClient {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Бэкенд живёт на 127.0.0.1 внутри самого приложения. Без явного
        // NO_PROXY OkHttp подхватывает системные настройки прокси, и при
        // включённом VPN трафик к localhost пытается уйти через туннель
        // VPN-клиента — тот его не проксирует, соединение падает с
        // исключением, из-за чего приложение вылетало при поиске.
        .proxy(Proxy.NO_PROXY)
        .build()

    val api: YtmApi = Retrofit.Builder()
        .baseUrl(LocalBackend.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(YtmApi::class.java)
}
