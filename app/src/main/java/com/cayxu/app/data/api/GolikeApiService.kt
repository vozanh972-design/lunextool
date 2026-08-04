package com.cayxu.app.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * API RIÊNG của Golike (gateway.golike.net) - HOÀN TOÀN ĐỘC LẬP với ApiService/
 * RetrofitClient ở trên (đó là API bản quyền/license riêng của app CayXu, khác domain,
 * khác cơ chế xác thực) - KHÔNG đụng gì tới các file đó.
 *
 * Token do người dùng tự lấy từ tài khoản Golike của họ (ví dụ lấy từ DevTools khi đăng
 * nhập web app.golike.net) và dán vào màn Đăng nhập Golike trong app - app KHÔNG đăng nhập
 * bằng mật khẩu, KHÔNG lưu mật khẩu Golike.
 */
interface GolikeApiService {
    @GET("api/users/me")
    suspend fun getMe(@Header("Authorization") authorization: String): Response<JsonObject>

    /** Danh sách tài khoản TikTok đã thêm vào GoLike - dùng để biết acc nào CHƯA có trong
     *  GoLike (hiện nút "Thêm" cho acc đó). Kiểu trả về JsonElement vì chưa rõ API trả
     *  thẳng mảng hay bọc trong object "data". */
    @GET("api/tiktok-account")
    suspend fun getTikTokAccounts(@Header("Authorization") authorization: String): Response<JsonElement>

    /** Số dư + thu nhập hôm nay theo TỪNG nền tảng (facebook/instagram/tiktok/...) - dùng
     *  cho "Thu nhập hôm nay" và biểu đồ phân bổ theo nền tảng ở Wallet/Home. */
    @GET("api/statistics/report")
    suspend fun getStatisticsReport(@Header("Authorization") authorization: String): Response<JsonObject>

    /** Hỏi GoLike xem 1 tài khoản TikTok đã follow đúng kênh chỉ định hay chưa - gọi SAU
     *  KHI đã tự bấm Follow, để xác nhận với server trước khi coi acc đủ điều kiện thêm vào
     *  GoLike. Xem GolikeVerifyAccountRepository để rõ cấu trúc body. */
    @POST("api/tiktok-account/verify-account-id")
    suspend fun verifyTikTokAccountId(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): Response<JsonObject>
}
