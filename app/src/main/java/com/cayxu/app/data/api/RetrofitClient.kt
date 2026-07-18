package com.cayxu.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Base URL KHÔNG được lưu dạng chữ trực tiếp trong code, để tránh việc
    // ai đó chỉ cần decompile APK rồi grep chuỗi là thấy ngay địa chỉ server.
    // Chuỗi được mã hoá XOR đơn giản và chỉ giải mã lúc chạy (runtime).
    // Lưu ý: đây chỉ chống được kiểu "tìm chuỗi tĩnh" như Dex Editor/jadx;
    // không chống được việc bắt gói tin (proxy/Frida) khi app đang chạy thật,
    // vì bản chất app luôn phải gửi request thật tới đúng domain này.
    private val OBFUSCATED_BASE_URL = intArrayOf(
        50, 46, 46, 42, 41, 96, 117, 117, 54, 47, 52, 63, 34, 116, 51, 53, 116, 44, 52, 117
    )
    private const val XOR_KEY = 0x5A

    private fun resolveBaseUrl(): String {
        val chars = CharArray(OBFUSCATED_BASE_URL.size)
        for (i in OBFUSCATED_BASE_URL.indices) {
            chars[i] = (OBFUSCATED_BASE_URL[i] xor XOR_KEY).toChar()
        }
        return String(chars)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Chỉ log chi tiết body/header (chứa key người dùng) khi app đang debug.
        // Bản release KHÔNG log body để tránh lộ key qua Logcat trên máy đã root.
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

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(resolveBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
