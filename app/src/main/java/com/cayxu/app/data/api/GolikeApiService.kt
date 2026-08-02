package com.cayxu.app.data.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * API RIÊNG của Golike (gateway.golike.net) - HOÀN TOÀN ĐỘC LẬP với ApiService/
 * RetrofitClient ở trên (đó là API bản quyền/license riêng của app CayXu, khác domain,
 * khác cơ chế xác thực) - KHÔNG đụng gì tới các file đó.
 *
 * Chỉ 1 endpoint duy nhất theo đúng yêu cầu: GET /api/users/me, xác thực bằng header
 * Authorization: Bearer <token>. Token do người dùng tự lấy từ tài khoản Golike của họ
 * (ví dụ lấy từ DevTools khi đăng nhập web app.golike.net) và dán vào màn Đăng nhập
 * Golike trong app - app KHÔNG đăng nhập bằng mật khẩu, KHÔNG lưu mật khẩu Golike.
 */
interface GolikeApiService {
    @GET("api/users/me")
    suspend fun getMe(@Header("Authorization") authorization: String): Response<JsonObject>
}
