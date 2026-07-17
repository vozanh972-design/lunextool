package com.cayxu.app.data.api

import com.cayxu.app.data.model.VerifyKeyResponse
import retrofit2.Response
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field
import retrofit2.http.POST

/**
 * Định nghĩa duy nhất 1 endpoint theo đúng yêu cầu:
 * POST https://lunex.io.vn/api/verify_key.php
 * Body: key=<key>, device_id=<ANDROID_ID>
 *
 * KHÔNG được thêm endpoint khác, KHÔNG được đổi tham số.
 */
interface ApiService {

    @FormUrlEncoded
    @POST("api/verify_key.php")
    suspend fun verifyKey(
        @Field("key") key: String,
        @Field("device_id") deviceId: String
    ): Response<VerifyKeyResponse>
}
