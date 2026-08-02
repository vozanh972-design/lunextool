package com.cayxu.app.data.api

import com.cayxu.app.data.model.VerifyKeyResponse
import retrofit2.Response
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Định nghĩa duy nhất 1 endpoint theo đúng yêu cầu:
 * POST <base_url_giải_mã_lúc_chạy>/api/verify_key.php  (xem RetrofitClient.VERIFY_KEY_PATH)
 * Body: key=<key>, device_id=<ANDROID_ID>
 *
 * KHÔNG được thêm endpoint khác, KHÔNG được đổi tham số.
 *
 * Lưu ý: @POST không còn ghi cứng "api/verify_key.php" nữa. Annotation của Retrofit
 * BẮT BUỘC tham số phải là hằng số lúc biên dịch (compile-time constant) nên không thể
 * gọi decodeText() ngay trong @POST(...). Thay vào đó, path được giải mã lúc chạy ở
 * RetrofitClient rồi truyền vào qua @Url - nhờ vậy chuỗi "verify_key.php" cũng không
 * còn nằm dạng chữ trực tiếp trong file .dex sau khi build.
 */
interface ApiService {

    @FormUrlEncoded
    @POST
    suspend fun verifyKey(
        @Url endpoint: String,
        @Field("key") key: String,
        @Field("device_id") deviceId: String
    ): Response<VerifyKeyResponse>
}
