package com.cayxu.app.ui.screens.golike

import android.content.Context
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Client kết nối 100% chuẩn Golike trích xuất từ GoMax 1.2.2
 */
class GolikeApiClient(
    var authToken: String? = null,
    var tToken: String? = null,
    var deviceId: String? = null,
    var username: String? = null,
    var gAuth: String? = null,
    var version: String? = null,
    var client: String? = null,
    var scheme: String? = null,
    var webVersion: String? = null,
    var webVersionText: String? = null,
    var webData: String? = null,
    var signingKey: String? = null,
    var userId: Long? = null,
    var context: Context? = null
) {
    companion object {
        const val BASE_URL = "https://gateway.golike.net/api"
        const val SECURITY_URL = "https://api.golike.net/api/v1/security"
        const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                synchronized(cookieStore) {
                    val existing = cookieStore.getOrPut(host) { mutableListOf() }
                    for (cookie in cookies) {
                        existing.removeAll { it.name == cookie.name }
                        existing.add(cookie)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val host = url.host
                synchronized(cookieStore) {
                    val result = mutableListOf<Cookie>()
                    cookieStore.forEach { (savedHost, cookies) ->
                        if (host.endsWith(savedHost) || savedHost.endsWith(host) || (savedHost.contains("golike.net") && host.contains("golike.net"))) {
                            result.addAll(cookies)
                        }
                    }
                    return result
                }
            }
        })
        .build()

    fun updateSession(
        authToken: String,
        tToken: String?,
        deviceId: String?,
        username: String?,
        gAuth: String?,
        version: String? = null,
        client: String? = null,
        scheme: String? = null,
        webVersion: String? = null,
        webVersionText: String? = null,
        webData: String? = null,
        signingKey: String? = null,
        userId: Long? = null,
        context: Context? = null
    ) {
        this.authToken = if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
        this.tToken = tToken
        this.deviceId = deviceId
        this.username = username
        this.gAuth = gAuth
        this.version = version ?: this.version
        this.client = client ?: this.client
        this.scheme = scheme ?: this.scheme
        this.webVersion = webVersion ?: this.webVersion
        this.webVersionText = webVersionText ?: this.webVersionText
        this.webData = webData ?: this.webData
        this.signingKey = signingKey ?: this.signingKey
        this.userId = userId ?: this.userId
        this.context = context ?: this.context
    }

    private fun safeHeader(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val filtered = value.filter { it.code in 0x20..0x7E }.trim()
        return filtered.ifBlank { null }
    }

    private fun newRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://app.golike.net")
            .header("Referer", "https://app.golike.net/")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Content-Type", "application/json;charset=utf-8")
            .header("priority", "u=1, i")
            .header("sec-ch-ua", "\"Google Chrome\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"")
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", "\"iOS\"")
            .header("sec-fetch-dest", "empty")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-site", "same-site")

        // Bộ token xác thực
        safeHeader(authToken)?.let { builder.header("Authorization", if (it.startsWith("Bearer ", ignoreCase = true)) it else "Bearer $it") }
        safeHeader(tToken)?.let { builder.header("t", it) }

        // Bộ headers định danh của Golike Web
        safeHeader(deviceId)?.let { builder.header("g-device-id", it) }
        safeHeader(username)?.let { builder.header("g-username", it) }
        safeHeader(gAuth)?.let { builder.header("g-auth", it) }

        // Chuẩn scheme gweb-v3 của Golike Web
        val finalScheme = scheme?.takeIf { it.isNotBlank() } ?: "gweb-v3"
        safeHeader(finalScheme)?.let { builder.header("g-scheme", it) }

        return builder
    }

    // 0a. Lấy realtime ticket (theo cURL iOS)
    fun getRealtimeTicket(): JSONObject? {
        val req = newRequestBuilder("https://api.golike.net/api/v1/realtime/ticket")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(req)
    }

    // 0b. Lấy config filters (theo cURL iOS)
    fun getConfigFilters(): JSONObject? {
        val req = newRequestBuilder("$BASE_URL/config/filters").get().build()
        return execute(req)
    }

    // 0c. Đồng bộ Protocol Golike (Chuẩn GoMax bắt buộc sau khi đăng nhập)
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
    suspend fun getSecurityTokenForGetJob(accountId: String, customWebData: String? = null): String? {
        val effectiveData = customWebData?.takeIf { it.isNotBlank() }
            ?: webData?.takeIf { it.isNotBlank() && it != "null" }
            ?: "null"
        val reqPayload = JSONObject().apply {
            put("plt", "tiktok")
            put("act", "get_job")
            put("req", JSONObject().apply {
                put("method", "GET")
                put("path", "/api/advertising/publishers/tiktok/jobs")
                put("query", "account_id=$accountId&data=$effectiveData")
                put("body", "")
            })
            put("tel", JSONObject().apply {
                put("trg", "click")
                put("aut", 0)
                put("idle", (90..130).random())
                put("iev", (6..12).random())
                put("vis", 1)
                put("foc", 1)
            })
        }

        val bodyStr = reqPayload.toString()
        val dynamicGAuth = generateGAuth(bodyStr)

        val reqBuilder = newRequestBuilder("$SECURITY_URL/token")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
        if (!dynamicGAuth.isNullOrBlank()) {
            reqBuilder.header("g-auth", dynamicGAuth)
        }

        val res = execute(reqBuilder.build())
        return res?.optString("sig")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("sig")?.takeIf { it.isNotBlank() }
            ?: res?.optString("token")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
    }

    suspend fun getTikTokJob(accountId: String, customWebData: String? = null): JSONObject? {
        val effectiveData = customWebData?.takeIf { it.isNotBlank() }
            ?: webData?.takeIf { it.isNotBlank() && it != "null" }
            ?: "null"
        val sigToken = getSecurityTokenForGetJob(accountId, effectiveData)
        val url = "$BASE_URL/advertising/publishers/tiktok/jobs?account_id=$accountId&data=$effectiveData"
        val reqBuilder = newRequestBuilder(url)
        if (!sigToken.isNullOrBlank()) {
            reqBuilder.header("sig", sigToken)
        }
        return execute(reqBuilder.get().build())
    }

    fun getInstagramJob(accountId: String): JSONObject? =
        execute(newRequestBuilder("$BASE_URL/advertising/publishers/instagram/jobs?instagram_account_id=$accountId").get().build())

    fun getFacebookJob(fbId: String): JSONObject? =
        execute(newRequestBuilder("$BASE_URL/advertising/publishers/get-jobs-2026?fb_id=$fbId").get().build())

    // 3b. Khởi tạo Security Session (Lấy signing_key chuẩn gweb-v3)
    fun createSecuritySession(): JSONObject? {
        val req = newRequestBuilder("$SECURITY_URL/session")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val res = execute(req)
        val key = res?.optString("signing_key").orEmpty()
        if (key.isNotBlank()) {
            this.signingKey = key
        }
        val uid = res?.optLong("uid", 0L) ?: 0L
        if (uid > 0L) {
            this.userId = uid
        }
        return res
    }

    // 3c. Sinh chữ ký g-auth chuẩn WebAssembly (WASM sign_v3 của Golike Web)
    private suspend fun generateGAuth(bodyString: String): String? {
        val ctx = context ?: return null
        var keyB64 = signingKey?.takeIf { it.isNotBlank() }
        if (keyB64.isNullOrBlank()) {
            createSecuritySession()
            keyB64 = signingKey?.takeIf { it.isNotBlank() }
        }
        if (keyB64.isNullOrBlank()) return null

        return try {
            val sha256 = MessageDigest.getInstance("SHA-256")
            val bodyHash = sha256.digest(bodyString.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val timestamp = System.currentTimeMillis()
            val uId = userId ?: 0L
            val devId = deviceId.orEmpty().ifBlank { "98ecbe1b-e52d-4537-8cfb-ae5f9d7f0237" }

            val randBytes = ByteArray(16)
            SecureRandom().nextBytes(randBytes)
            val randHex = Base64.encodeToString(randBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            GolikeWasmSigner.sign(
                context = ctx,
                rawKeyB64 = keyB64,
                timestamp = timestamp,
                nonceB64 = randHex,
                deviceId = devId,
                userId = uId,
                method = "POST",
                path = "/api/v1/security/token",
                bodyHash = bodyHash
            )
        } catch (e: Exception) {
            android.util.Log.e("GolikeSecurity", "Lỗi tạo g-auth WASM: ${e.message}")
            null
        }
    }

    // 3d. Lấy chữ ký Token chứng thực cho complete_job
    suspend fun getSecurityToken(adsId: Any, accountId: Any): String? {
        val bodyObj = JSONObject().apply {
            put("ads_id", adsId)
            put("account_id", accountId)
            put("async", true)
            put("data", JSONObject.NULL)
        }
        val reqPayload = JSONObject().apply {
            put("plt", "tiktok")
            put("act", "complete_job")
            put("req", JSONObject().apply {
                put("method", "POST")
                put("path", "/api/advertising/publishers/tiktok/complete-jobs")
                put("query", "")
                put("body", bodyObj.toString())
            })
            put("tel", JSONObject().apply {
                put("trg", "click")
                put("aut", 0)
                put("idle", (70..110).random())
                put("iev", (12..18).random())
                put("vis", 1)
                put("foc", 1)
            })
        }

        val bodyStr = reqPayload.toString()
        val dynamicGAuth = generateGAuth(bodyStr)

        val reqBuilder = newRequestBuilder("$SECURITY_URL/token")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
        if (!dynamicGAuth.isNullOrBlank()) {
            reqBuilder.header("g-auth", dynamicGAuth)
        }

        val res = execute(reqBuilder.build())
        return res?.optString("sig")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("sig")?.takeIf { it.isNotBlank() }
            ?: res?.optString("token")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
    }

    // 4. Hoàn thành nhiệm vụ (Complete Job)
    suspend fun completeTikTokJob(adsId: Any, accountId: Any): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val sigToken = getSecurityToken(adsId, numAccId)

        val body = JSONObject().apply {
            put("ads_id", adsId)
            put("account_id", numAccId)
            put("async", true)
            put("data", JSONObject.NULL)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val reqBuilder = newRequestBuilder("$BASE_URL/advertising/publishers/tiktok/complete-jobs")
            .post(body)
        if (!sigToken.isNullOrBlank()) {
            reqBuilder.header("sig", sigToken)
        }
        return execute(reqBuilder.build())
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
