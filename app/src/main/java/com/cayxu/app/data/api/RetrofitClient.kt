package com.cayxu.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 🔥 THAY URL NÀY BẰNG URL CỦA RENDER SAU KHI DEPLOY
    private const val BASE_URL = "https://facebook-login-api.onrender.com/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
