package com.gammatunes.app.network

import com.gammatunes.app.backend.LocalBackend
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ApiClient {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)





        .proxy(Proxy.NO_PROXY)
        .build()

    val api: YtmApi = Retrofit.Builder()
        .baseUrl(LocalBackend.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(YtmApi::class.java)
}
