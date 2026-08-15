package com.cayxu.app.data.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

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
}
