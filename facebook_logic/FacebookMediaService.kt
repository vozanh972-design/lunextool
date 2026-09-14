package com.cayxu.app.facebook

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Trích xuất từ `Lw2/u;` trong APK.
 * Xử lý các tác vụ:
 * 1. Nút "avatar": Đổi ảnh đại diện Facebook.
 * 2. Nút "bìa": Đổi ảnh bìa Facebook (Cover Photo).
 * 3. Nút "Đăng bài": Đăng status / bài viết lên trang cá nhân hoặc page.
 */
class FacebookMediaService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 1. Đổi Avatar (Ảnh đại diện)
     */
    @Throws(Exception::class)
    fun updateAvatar(imageFile: File, userToken: String): Boolean {
        if (!imageFile.exists()) throw IllegalArgumentException("File ảnh không tồn tại")

        val mediaType = "image/jpeg".toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", imageFile.name, imageFile.asRequestBody(mediaType))
            .addFormDataPart("access_token", userToken)
            .addFormDataPart("published", "false")
            .addFormDataPart("audience_exp", "true")
            .addFormDataPart("composer_entry_point", "camera_roll")
            .build()

        val request = Request.Builder()
            .url("https://graph.facebook.com/me/photos")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            return response.isSuccessful && body.contains(""id"")
        }
    }

    /**
     * 2. Đổi Ảnh bìa (Cover Photo)
     */
    @Throws(Exception::class)
    fun updateCoverPhoto(imageFile: File, userToken: String): Boolean {
        if (!imageFile.exists()) throw IllegalArgumentException("File ảnh không tồn tại")

        val mediaType = "image/jpeg".toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", imageFile.name, imageFile.asRequestBody(mediaType))
            .addFormDataPart("access_token", userToken)
            .build()

        val request = Request.Builder()
            .url("https://graph.facebook.com/me/photos")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            return response.isSuccessful && body.contains(""id"")
        }
    }

    /**
     * 3. Đăng bài viết (Post status)
     */
    @Throws(Exception::class)
    fun postStatus(message: String, targetId: String = "me", token: String): String {
        val formBody = FormBody.Builder()
            .add("message", message)
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("https://graph.facebook.com/v19.0/$targetId/feed")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }
}
