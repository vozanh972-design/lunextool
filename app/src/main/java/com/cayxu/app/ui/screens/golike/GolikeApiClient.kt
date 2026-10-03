package com.cayxu.app.ui.screens.golike

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client kết nối 100% chuẩn Golike trích xuất từ GoMax 1.2.2
 */
class GolikeApiClient(
    var authToken: String? = null,
    var tToken: String? = null,
    var deviceId: String? = null,
    var username: String? = null,
    var gAuth: String? = null
) {
    companion object {
        const val BASE_URL = "https://gateway.golike.net/api"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun updateSession(authToken: String, tToken: String?, deviceId: String?, username: String?, gAuth: String?) {
        this.authToken = if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
        this.tToken = tToken
        this.deviceId = deviceId
        this.username = username
        this.gAuth = gAuth
    }

    private fun newRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://app.golike.net")
            .header("Referer", "https://app.golike.net/")
            .header("Accept", "application/json, text/plain, */*")

        authToken?.let { builder.header("Authorization", if (it.startsWith("Bearer ", ignoreCase = true)) it else "Bearer $it") }
        tToken?.let { if (it.isNotBlank()) builder.header("t", it) }
        deviceId?.let { if (it.isNotBlank()) builder.header("g-device-id", it) }
        username?.let { if (it.isNotBlank()) builder.header("g-username", it) }
        gAuth?.let { if (it.isNotBlank()) builder.header("g-auth", it) }

        return builder
    }

    // 0. Đồng bộ Protocol Golike (Chuẩn GoMax bắt buộc sau khi đăng nhập)
    fun syncProtocol(): JSONObject? {
        val req = newRequestBuilder("$BASE_URL/app/golike-protocol").get().build()
        return execute(req)
    }

    // 1. Lấy thông tin user và số dư
    fun getMe(): JSONObject? {
        val req = newRequestBuilder("$BASE_URL/users/me").get().build()
        return execute(req)
    }

    // 2. Lấy danh sách tài khoản liên kết
    fun getTikTokAccounts(): JSONObject? = execute(newRequestBuilder("$BASE_URL/tiktok-account?limit=200").get().build())
    fun getInstagramAccounts(): JSONObject? = execute(newRequestBuilder("$BASE_URL/instagram-account?limit=200").get().build())
    fun getFacebookAccounts(): JSONObject? = execute(newRequestBuilder("$BASE_URL/facebook-account").get().build())

    // 2b. Khai báo và xác minh nick TikTok vào Golike (Chuẩn GoMax)
    fun declareTikTokAccount(username: String): JSONObject? {
        val cleanUsername = username.trim().removePrefix("@")
        val body = JSONObject().apply {
            put("unique_id", cleanUsername)
            put("unique_username", cleanUsername)
            put("username", cleanUsername)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/tiktok-account").post(body).build())
    }

    fun verifyTikTokAccountId(accountId: String, username: String): JSONObject? {
        val cleanUsername = username.trim().removePrefix("@")
        val body = JSONObject().apply {
            val numId = accountId.toLongOrNull()
            if (numId != null) {
                put("account_id", numId)
                put("id", numId)
            } else {
                put("account_id", accountId)
                put("id", accountId)
            }
            put("unique_id", cleanUsername)
            put("unique_username", cleanUsername)
            put("username", cleanUsername)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/tiktok-account/verify-account-id").post(body).build())
    }

    // Lấy thông tin nick chỉ định cần follow để xác minh cấu hình: GET /api/tiktok-account/verify-account-id?account_id={accountId}
    fun getTikTokVerifyTarget(accountId: String, username: String = ""): JSONObject? {
        val cleanUsername = username.trim().removePrefix("@")
        val query = if (cleanUsername.isNotBlank()) {
            "$BASE_URL/tiktok-account/verify-account-id?account_id=$accountId&unique_id=$cleanUsername"
        } else {
            "$BASE_URL/tiktok-account/verify-account-id?account_id=$accountId"
        }
        return execute(newRequestBuilder(query).get().build())
    }

    // Xác nhận hoàn tất cấu hình lên Golike: POST /api/tiktok-account/verify {"account_id": accountId}
    fun verifyTikTokAccount(accountId: String, username: String = ""): JSONObject? {
        val cleanUsername = username.trim().removePrefix("@")
        val body = JSONObject().apply {
            val numId = accountId.toLongOrNull()
            if (numId != null) {
                put("account_id", numId)
                put("id", numId)
            } else {
                put("account_id", accountId)
                put("id", accountId)
            }
            if (cleanUsername.isNotBlank()) {
                put("unique_id", cleanUsername)
                put("unique_username", cleanUsername)
                put("username", cleanUsername)
            }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/tiktok-account/verify").post(body).build())
    }

    // 3. Lấy nhiệm vụ (Get Job)
    fun getTikTokJob(accountId: String): JSONObject? =
        execute(newRequestBuilder("$BASE_URL/advertising/publishers/tiktok/jobs?account_id=$accountId").get().build())

    fun getInstagramJob(accountId: String): JSONObject? =
        execute(newRequestBuilder("$BASE_URL/advertising/publishers/instagram/jobs?instagram_account_id=$accountId").get().build())

    fun getFacebookJob(fbId: String): JSONObject? =
        execute(newRequestBuilder("$BASE_URL/advertising/publishers/get-jobs-2026?fb_id=$fbId").get().build())

    // 4. Hoàn thành nhiệm vụ (Complete Job)
    fun completeTikTokJob(adsId: Long, accountId: String): JSONObject? {
        val body = JSONObject().apply {
            put("ads_id", adsId)
            put("account_id", accountId)
            put("async", true)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/advertising/publishers/tiktok/complete-jobs").post(body).build())
    }

    fun completeInstagramJob(adsId: Long, accountId: String): JSONObject? {
        val body = JSONObject().apply {
            put("ads_id", adsId)
            put("instagram_account_id", accountId)
            put("async", true)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/advertising/publishers/instagram/complete-jobs").post(body).build())
    }

    fun completeFacebookJob(adsId: Long, fbId: String): JSONObject? {
        val body = JSONObject().apply {
            put("ads_id", adsId)
            put("fb_id", fbId)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/advertising/publishers/complete-jobs-2026").post(body).build())
    }

    // 5. Bỏ qua nhiệm vụ (Skip Job)
    fun skipJob(adsId: Long, objectId: String, accountId: String, type: String): JSONObject? {
        val body = JSONObject().apply {
            put("ads_id", adsId)
            put("object_id", objectId)
            put("account_id", accountId)
            put("type", type)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/advertising/publishers/skip-jobs").post(body).build())
    }

    private fun execute(request: Request): JSONObject? {
        val url = request.url.toString()
        val method = request.method
        return try {
            httpClient.newCall(request).execute().use { res ->
                val code = res.code
                val bodyStr = res.body?.string().orEmpty()
                android.util.Log.d("GolikeApi", "[$method] $url -> HTTP $code | Response: $bodyStr")
                android.util.Log.d("GolikeVerify", "Status: $code, Body: $bodyStr")
                if (bodyStr.isBlank()) return null
                try {
                    val json = JSONObject(bodyStr)
                    if (!json.has("http_code")) {
                        json.put("http_code", code)
                    }
                    json
                } catch (e: Exception) {
                    android.util.Log.w("GolikeApi", "Response is not a valid JSONObject: $bodyStr")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GolikeApi", "Network error calling $url: ${e.message}", e)
            null
        }
    }
}
