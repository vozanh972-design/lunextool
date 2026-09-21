package com.cayxu.app.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoint XSMM (Task API đa luồng) - theo đúng API Documentation (/api/taskapi).
 * Base URL: https://xsmm.net/
 */
interface XsmmApiService {

    /** Lấy thông tin acc + số dư hiện tại.
     *  Thành công: { "user": { "username": "...", "points": 1500 } }
     *  Lỗi: { "error": "Chi tiết lỗi" } */
    @GET("api/taskapi/user")
    suspend fun getUser(@Header("Authorization") authorization: String): Response<JsonObject>

    /** Lấy danh sách tài khoản (GET /api/taskapi/accounts) */
    @GET("api/taskapi/accounts")
    suspend fun getAccounts(
        @Header("Authorization") authorization: String,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("account_type") accountType: String? = null
    ): Response<JsonObject>

    /** Lấy danh sách tài khoản đa luồng (GET /api/taskapi/accounts2) */
    @GET("api/taskapi/accounts2")
    suspend fun getAccounts2(
        @Header("Authorization") authorization: String,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("account_type") accountType: String? = null
    ): Response<JsonObject>

    /** Lấy tài khoản đang active (GET /api/taskapi/accounts/active) */
    @GET("api/taskapi/accounts/active")
    suspend fun getActiveAccount(@Header("Authorization") authorization: String): Response<JsonObject>

    /** Thêm tài khoản mới (POST /api/taskapi/accounts) */
    @POST("api/taskapi/accounts")
    suspend fun addAccount(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    /** Thêm tài khoản mới đa luồng (POST /api/taskapi/accounts2) */
    @POST("api/taskapi/accounts2")
    suspend fun addAccount2(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    /** Đặt 1 tài khoản đã có làm "nick chạy" (PUT /api/taskapi/accounts/{id}/set-active). */
    @PUT("api/taskapi/accounts/{id}/set-active")
    suspend fun setActiveAccount(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body body: JsonObject = JsonObject()
    ): Response<JsonObject>

    /** Lấy danh sách nhiệm vụ khả dụng (GET /api/taskapi/tasks - chuẩn 100% tài liệu XSMM) */
    @GET("api/taskapi/tasks")
    suspend fun getTasks(
        @Header("Authorization") authorization: String,
        @Query("type") type: String,
        @Query("typejob") typejob: String? = "normal,better,best"
    ): Response<com.google.gson.JsonElement>

    /** Hoàn thành nhiệm vụ (POST /api/taskapi/tasks/complete - chuẩn 100% tài liệu XSMM) */
    @POST("api/taskapi/tasks/complete")
    suspend fun completeTasks(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    /** Lấy danh sách nhiệm vụ khả dụng tasks2 (đa luồng TikTok) */
    @GET("api/taskapi/tasks2")
    suspend fun getTasks2(
        @Header("Authorization") authorization: String,
        @Query("type") type: String,
        @Query("uid") uid: String,
        @Query("typejob") typejob: String? = "normal,better,best"
    ): Response<com.google.gson.JsonElement>

    /** Hoàn thành nhiệm vụ tasks2 */
    @POST("api/taskapi/tasks2/complete")
    suspend fun completeTasks2(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): Response<JsonObject>
}
