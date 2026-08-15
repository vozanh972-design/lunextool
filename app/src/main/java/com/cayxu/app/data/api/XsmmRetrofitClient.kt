package com.cayxu.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Retrofit client RIÊNG cho XSMM (https://xsmm.net/) - độc lập với RetrofitClient chính
 *  của app (dùng cho server key/license) và với GoLike (đã gỡ bỏ). */
object XsmmRetrofitClient {
    private const val BASE_URL = "https://xsmm.net/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.cayxu.app.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val api: XsmmApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XsmmApiService::class.java)
    }
}
