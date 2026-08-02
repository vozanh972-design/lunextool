package com.cayxu.app.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Client Retrofit RIÊNG cho Golike (gateway.golike.net) - độc lập hoàn toàn với
 * RetrofitClient ở trên (không dùng chung OkHttpClient/certificate pinning/interceptor
 * của license server, vì đây là domain thật của bên thứ 3 - Golike - không phải server
 * của app CayXu).
 */
object GolikeRetrofitClient {
    private const val BASE_URL = "https://gateway.golike.net/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: GolikeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GolikeApiService::class.java)
    }
}
