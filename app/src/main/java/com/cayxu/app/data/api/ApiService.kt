package com.cayxu.app.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    val email: String,
    val password: String,
    val auth: String? = "",
    val app_token: String = "350685531728|62f8ce9f74b12f84c123cc23437a4a32"
)

data class LoginResponse(
    val success: Boolean,
    val uid: String?,
    val token: String?,
    val cookies: String?,
    val error: String?
)

data class MultipleLoginRequest(
    val accounts: List<LoginRequest>,
    val app_token: String = "350685531728|62f8ce9f74b12f84c123cc23437a4a32"
)

data class MultipleLoginResponse(
    val results: List<LoginResponse>
)

interface ApiService {
    @POST("/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("/login-multiple")
    fun loginMultiple(@Body request: MultipleLoginRequest): Call<MultipleLoginResponse>
}
