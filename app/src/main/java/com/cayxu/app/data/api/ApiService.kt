package com.cayxu.app.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {

    @FormUrlEncoded
    @POST
    suspend fun verifyKey(
        @Url endpoint: String,
        @Field("key") key: String,
        @Field("device_id") deviceId: String
    ): Response<ResponseBody>
}
