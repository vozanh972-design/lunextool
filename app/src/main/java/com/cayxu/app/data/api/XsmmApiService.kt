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
 * Endpoint XSMM (Task API) - xem "API Documentation - Task Endpoints (/api/taskapi)" người
 * dùng cung cấp. Base URL: https://xsmm.net/ (xem XsmmRetrofitClient).
 */
interface XsmmApiService {

    /** Lấy thông tin acc + số dư hiện tại.
     *  Thành công: { "user": { "username": "...", "points": 1500 } }
     *  Lỗi: { "error": "Chi tiết lỗi" } */
    @GET("api/taskapi/user")
    suspend fun getUser(@Header("Authorization") authorization: String): Response<JsonObject>

    /** Lấy danh sách tài khoản đã thêm vào XSMM (có phân trang) - dùng account_type để lọc
     *  riêng từng loại (vd "tiktok"), search để tìm theo tên/link cụ thể. */
    @GET("api/taskapi/accounts")
    suspend fun getAccounts(
        @Header("Authorization") authorization: String,
        @Query("search") search: String? = null,
        @Query("page") page: Int? = null,
        @Query("account_type") accountType: String? = null
    ): Response<JsonObject>

    /** Lấy tài khoản đang được đặt làm "nick chạy" (active). */
    @GET("api/taskapi/accounts/active")
    suspend fun getActiveAccount(@Header("Authorization") authorization: String): Response<JsonObject>

    /** Thêm tài khoản mới. Body: {"type": "...", "link_account": "...", "active": true?} */
    @POST("api/taskapi/accounts")
    suspend fun addAccount(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    /** Đặt 1 tài khoản đã có làm "nick chạy". */
    @PUT("api/taskapi/accounts/{id}/set-active")
    suspend fun setActiveAccount(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): Response<JsonObject>

    /** Lấy danh sách nhiệm vụ khả dụng (tasks2 - có uid của acc đang chạy).
     *  [type]: vd "tiktok_follow", [uid]: account_id của acc TikTok đã set-active trên XSMM. */
    @GET("api/taskapi/tasks2")
    suspend fun getTasks2(
        @Header("Authorization") authorization: String,
        @Query("type") type: String,
        @Query("uid") uid: String,
        @Query("typejob") typejob: String? = null
    ): Response<com.google.gson.JsonArray>

    /** Hoàn thành nhiệm vụ (tasks2/complete - có uid). Body: {"type":..., "task_id":[...], "uid":...} */
    @POST("api/taskapi/tasks2/complete")
    suspend fun completeTasks2(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): Response<JsonObject>
}
