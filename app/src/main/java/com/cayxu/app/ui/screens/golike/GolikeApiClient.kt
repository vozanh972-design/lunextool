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
    var context: Context? = null,
    var userUid: String? = null
) {
    var lastTokenError: String? = null
    var serverTimeOffset: Long = 0L

    companion object {
        val BASE_URL: String get() = GolikeNativeBridge.fetchBaseUrl()
        val SECURITY_URL: String get() = GolikeNativeBridge.fetchSecurityUrl()
        val USER_AGENT: String get() = GolikeNativeBridge.fetchUserAgent()
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
        context: Context? = null,
        userUid: String? = null
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
        this.userUid = userUid ?: this.userUid
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
            .header("sec-fetch-dest", "empty")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-site", "same-site")

        // Bộ token xác thực
        safeHeader(authToken)?.let { builder.header("Authorization", if (it.startsWith("Bearer ", ignoreCase = true)) it else "Bearer $it") }

        // CHỈ gửi g-scheme khi tạo session bảo mật
        if (url.contains("/security/session")) {
            val finalScheme = scheme?.takeIf { it.isNotBlank() } ?: "gweb-v3"
            safeHeader(finalScheme)?.let { builder.header("g-scheme", it) }
        }

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
    fun getInstagramAccounts(): JSONObject? = execute(newRequestBuilder("$BASE_URL/instagram-account").get().build())
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

    // 2c. Xác minh tài khoản Instagram vào Golike: POST /api/instagram-account/verify-account {"object_id": username}
    fun verifyInstagramAccount(username: String): JSONObject? {
        val cleanUsername = username.trim().removePrefix("@")
        val body = JSONObject().apply {
            put("object_id", cleanUsername)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/instagram-account/verify-account").post(body).build())
    }

    // 3. Lấy nhiệm vụ (Get Job)
    suspend fun getSecurityTokenForGetJob(
        accountId: Any,
        customWebData: String? = null,
        path: String = GolikeNativeBridge.fetchPathGetJob(),
        onStatus: (String) -> Unit = {}
    ): String? {
        lastTokenError = null
        context?.let { ctx ->
            val syncErr = GolikeNativeBridge.checkSessionIntegrity(ctx)
            if (syncErr != null) {
                lastTokenError = syncErr
                onStatus(syncErr)
                return null
            }
        }
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val effectiveData = customWebData?.takeIf { it.isNotBlank() && it != "null" }
            ?: webData?.takeIf { it.isNotBlank() && it != "null" }
            ?: "null"
        val telPayloadStr = GolikeNativeBridge.fetchTelemetryJson(0)
        val reqPayload = JSONObject().apply {
            put("plt", "tiktok")
            put("act", "get_job")
            put("req", JSONObject().apply {
                put("method", "GET")
                put("path", path)
                put("query", "account_id=$numAccId&data=$effectiveData")
                put("body", "")
            })
            put("tel", JSONObject(telPayloadStr))
        }

        val bodyStr = reqPayload.toString()
        onStatus("Đang tạo chữ ký WASM...")
        val dynamicGAuth = generateGAuth(bodyStr, path = GolikeNativeBridge.fetchPathToken())
        if (dynamicGAuth.isNullOrBlank()) {
            val err = "Lỗi tạo chữ ký WASM g-auth (chưa có signing_key hoặc session hết hạn)"
            lastTokenError = err
            onStatus(err)
            android.util.Log.e("GolikeApi", err)
            return null
        }

        val reqBuilder = newRequestBuilder("$SECURITY_URL/token")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
            .header("g-auth", dynamicGAuth)

        onStatus("Đang lấy token nhận nhiệm vụ (sig)...")
        val res = execute(reqBuilder.build())
        val httpCode = res?.optInt("http_code", 0) ?: 0
        val directToken = res?.optString("token")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
            ?: res?.optString("sig")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("sig")?.takeIf { it.isNotBlank() }

        if (directToken != null) {
            return directToken
        }

        // Tự động giải ngầm nếu gặp yêu cầu xác minh Captcha
        val action = res?.optString("action")
        val reason = res?.optString("reason")
        val message = res?.optString("message").orEmpty()
        if (action == "captcha" || reason == "captcha" || message.contains("xác minh", ignoreCase = true)) {
            onStatus("🛡️ Golike yêu cầu giải Captcha -> Đang tự động kích hoạt giải ngầm...")
            android.util.Log.w("GolikeApi", "Gặp yêu cầu Captcha khi lấy Token Job. Đang tự động giải ngầm...")
            val solved = solveCaptchaChallenge(onStatus)
            if (solved) {
                onStatus("🟢 Giải Captcha OK TRUE -> Đang lấy lại chữ ký sig...")
                android.util.Log.i("GolikeApi", "Đã giải Captcha ngầm thành công! Đang yêu cầu lại token sig...")
                val retryBodyStr = reqPayload.toString()
                val retryGAuth = generateGAuth(retryBodyStr, path = "/api/v1/security/token")
                val retryReqBuilder = newRequestBuilder("$SECURITY_URL/token")
                    .post(retryBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                if (!retryGAuth.isNullOrBlank()) {
                    retryReqBuilder.header("g-auth", retryGAuth)
                }
                val retryRes = execute(retryReqBuilder.build())
                val retryToken = retryRes?.optString("token")?.takeIf { it.isNotBlank() }
                    ?: retryRes?.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
                    ?: retryRes?.optString("sig")?.takeIf { it.isNotBlank() }
                    ?: retryRes?.optJSONObject("data")?.optString("sig")?.takeIf { it.isNotBlank() }
                if (retryToken != null) {
                    onStatus("🟢 Đã có chữ ký sig hợp lệ! Chuẩn bị nhận việc...")
                    return retryToken
                }
                val retryErr = retryRes?.optString("message")?.ifBlank { "HTTP ${retryRes.optInt("http_code", 0)}" }
                lastTokenError = "Lỗi cấp chữ ký sig sau khi giải Captcha: $retryErr"
                onStatus(lastTokenError.orEmpty())
            } else {
                lastTokenError = "Lỗi: Server từ chối xác minh Captcha hoặc giải thất bại"
                onStatus(lastTokenError.orEmpty())
                android.util.Log.e("GolikeApi", "Giải Captcha ngầm không thành công.")
            }
        } else {
            val errDetail = when {
                message.isNotBlank() && httpCode > 0 -> "HTTP $httpCode: $message"
                message.isNotBlank() -> message
                httpCode > 0 -> "HTTP $httpCode"
                else -> "Server không phản hồi token (HTTP $httpCode)"
            }
            lastTokenError = "Thiếu chữ ký sig: $errDetail (Không thể gọi job khi chưa có sig)"
            onStatus(lastTokenError.orEmpty())
        }

        return null
    }

    fun checkPrivateAccount(accountId: Any): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val body = JSONObject().apply {
            put("id", numAccId)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/tiktok-account/check-private/$numAccId").post(body).build())
    }

    fun clearCacheAccount(accountId: Any): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val body = JSONObject().apply {
            put("id", numAccId)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/tiktok-account/clear-cache/$numAccId").post(body).build())
    }

    suspend fun getTikTokJob(
        accountId: Any,
        customWebData: String? = null,
        onStatus: (String) -> Unit = {}
    ): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val effectiveData = customWebData?.takeIf { it.isNotBlank() && it != "null" }
            ?: webData?.takeIf { it.isNotBlank() && it != "null" }
            ?: "null"

        // 1. Kích hoạt/xác thực nick kiếm thưởng trên Golike (tương đương check-private của Web)
        try {
            checkPrivateAccount(numAccId)
        } catch (_: Exception) {}

        // 2. Lấy chữ ký sig theo đường dẫn chuẩn Golike Web (/advertising/publishers/tiktok/jobs)
        var currentPath = GolikeNativeBridge.fetchPathGetJob()
        var sigToken = getSecurityTokenForGetJob(numAccId, effectiveData, currentPath, onStatus)
        if (sigToken.isNullOrBlank()) {
            val err = lastTokenError ?: "Thiếu chữ ký sig (Không lấy được token bảo mật từ Golike)"
            return JSONObject().apply {
                put("success", false)
                put("status", 400)
                put("message", err)
            }
        }

        onStatus("Đang nhận việc từ Golike...")
        val url = "$BASE_URL/advertising/publishers/tiktok/jobs?account_id=$numAccId&data=$effectiveData"
        var reqBuilder = newRequestBuilder(url).header("sig", sigToken)
        var res = execute(reqBuilder.get().build())
        var code = res?.optInt("status", res.optInt("http_code", 0)) ?: 0

        // 3. Nếu Gateway trả về 403 (sig bị từ chối), tự động fallback thử lại với path có tiền tố /api
        if (code == 403) {
            onStatus("Chữ ký cần tiền tố /api -> Đang tạo lại chữ ký...")
            currentPath = "/api/advertising/publishers/tiktok/jobs"
            val fallbackSig = getSecurityTokenForGetJob(numAccId, effectiveData, currentPath, onStatus)
            if (!fallbackSig.isNullOrBlank()) {
                reqBuilder = newRequestBuilder(url).header("sig", fallbackSig)
                res = execute(reqBuilder.get().build())
                code = res?.optInt("status", res.optInt("http_code", 0)) ?: 0
            }
        }

        if (res != null && !res.optBoolean("success", false)) {
            val msg = res.optString("message").orEmpty()
            if (msg.contains("không hợp lệ", ignoreCase = true)) {
                res.put("message", "Golike [HTTP $code]: Yêu cầu không hợp lệ (Nick ID $numAccId chưa khớp hoặc sig bị từ chối)")
            }
        }
        return res
    }

    private fun extractToken(res: JSONObject?): String? {
        if (res == null) return null
        fun isValid(s: String?): Boolean = !s.isNullOrBlank() && s != "null" && s != "undefined"
        res.optString("token").takeIf { isValid(it) }?.let { return it }
        res.optJSONObject("data")?.optString("token")?.takeIf { isValid(it) }?.let { return it }
        res.optString("sig").takeIf { isValid(it) }?.let { return it }
        res.optJSONObject("data")?.optString("sig")?.takeIf { isValid(it) }?.let { return it }
        return null
    }

    // 3a-IG. Lấy chữ ký token bảo mật (sig) cho nhận job Instagram
    suspend fun getInstagramSecurityTokenForGetJob(
        accountId: Any,
        path: String = "/advertising/publishers/instagram/jobs",
        onStatus: (String) -> Unit = {}
    ): String? {
        context?.let { ctx ->
            val syncErr = GolikeNativeBridge.checkSessionIntegrity(ctx)
            if (syncErr != null) {
                lastTokenError = syncErr
                return null
            }
        }
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val telPayloadStr = GolikeNativeBridge.fetchTelemetryJson(0)
        val reqPayload = JSONObject().apply {
            put("plt", "instagram")
            put("act", "get_job")
            put("req", JSONObject().apply {
                put("method", "GET")
                put("path", path)
                put("query", "instagram_account_id=$numAccId&data=null")
                put("body", "")
            })
            put("tel", JSONObject(telPayloadStr))
        }

        val bodyStr = reqPayload.toString()
        onStatus("Đang tạo chữ ký WASM Instagram...")
        val dynamicGAuth = generateGAuth(bodyStr, path = GolikeNativeBridge.fetchPathToken())
        if (dynamicGAuth.isNullOrBlank()) {
            val err = "Lỗi tạo chữ ký WASM g-auth IG"
            lastTokenError = err
            onStatus(err)
            return null
        }

        val reqBuilder = newRequestBuilder("$SECURITY_URL/token")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
            .header("g-auth", dynamicGAuth)

        onStatus("Đang lấy token nhận việc IG (sig)...")
        var res = execute(reqBuilder.build())
        var directToken = extractToken(res)

        if (directToken != null) {
            lastTokenError = null
            return directToken
        }

        var action = res?.optString("action")
        var reason = res?.optString("reason")
        var message = res?.optString("message").orEmpty()

        // 1. Tự động làm mới session nếu server yêu cầu retry/rejected (chuẩn Web Golike nM và fc)
        if (action == "retry" || reason == "rejected") {
            onStatus("Làm mới session bảo mật Golike...")
            this.signingKey = null
            createSecuritySession()
            val retryBodyStr = reqPayload.toString()
            val retryGAuth = generateGAuth(retryBodyStr, path = GolikeNativeBridge.fetchPathToken())
            if (!retryGAuth.isNullOrBlank()) {
                val retryReq = newRequestBuilder("$SECURITY_URL/token")
                    .post(retryBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                    .header("g-auth", retryGAuth)
                    .build()
                res = execute(retryReq)
                directToken = extractToken(res)
                if (directToken != null) {
                    lastTokenError = null
                    return directToken
                }
                action = res?.optString("action")
                reason = res?.optString("reason")
                message = res?.optString("message").orEmpty()
            }
        }

        // 2. Tự động giải ngầm nếu gặp Captcha
        if (action == "captcha" || reason == "captcha" || message.contains("xác minh", ignoreCase = true)) {
            onStatus("🛡️ Golike yêu cầu Captcha -> Đang tự động giải ngầm...")
            val solved = solveCaptchaChallenge(onStatus)
            if (solved) {
                onStatus("🟢 Đã giải Captcha -> Đang lấy lại sig IG...")
                val retryBodyStr = reqPayload.toString()
                val retryGAuth = generateGAuth(retryBodyStr, path = GolikeNativeBridge.fetchPathToken())
                val retryReqBuilder = newRequestBuilder("$SECURITY_URL/token")
                    .post(retryBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                if (!retryGAuth.isNullOrBlank()) {
                    retryReqBuilder.header("g-auth", retryGAuth)
                }
                val retryRes = execute(retryReqBuilder.build())
                val retToken = extractToken(retryRes)
                if (retToken != null) {
                    lastTokenError = null
                    return retToken
                }
                lastTokenError = "Giải Captcha xong nhưng server token vẫn từ chối: ${retryRes?.toString() ?: "rỗng"}"
            } else {
                lastTokenError = "Golike yêu cầu xác minh Captcha nhưng giải tự động chưa thành công ($action / $reason / $message)"
            }
        } else {
            val serverErr = res?.optString("message")?.takeIf { it.isNotBlank() }
                ?: res?.optString("error")?.takeIf { it.isNotBlank() }
                ?: (if (res == null) "Không có kết nối tới server bảo mật Golike (/security/token)" else "Server bảo mật từ chối cấp token sig: $res")
            lastTokenError = serverErr
        }

        return null
    }

    suspend fun getInstagramJob(accountId: String, onStatus: (String) -> Unit = {}): JSONObject? {
        val numAccId = accountId.toLongOrNull() ?: accountId
        var currentPath = "/advertising/publishers/instagram/jobs"
        var sigToken = getInstagramSecurityTokenForGetJob(numAccId, path = currentPath, onStatus = onStatus)
        if (sigToken.isNullOrBlank()) {
            val err = lastTokenError ?: "Thiếu chữ ký sig (Không lấy được token bảo mật từ Golike)"
            return JSONObject().apply {
                put("success", false)
                put("status", 400)
                put("message", err)
            }
        }
        val url = "$BASE_URL/advertising/publishers/instagram/jobs?instagram_account_id=$numAccId&data=null"
        var reqBuilder = newRequestBuilder(url).header("sig", sigToken)
        onStatus("Đang nhận việc Instagram...")
        var res = execute(reqBuilder.get().build())
        var code = res?.optInt("http code", res.optInt("http_code", res.optInt("status", 0))) ?: 0

        // Fallback thử lại nếu Gateway trả về 403 (Đổi path xin lại sig, giữ nguyên 100% URL Gateway chuẩn)
        if (code == 403) {
            onStatus("Chữ ký cần tạo lại -> Đang lấy lại chữ ký...")
            currentPath = "/api/advertising/publishers/instagram/jobs"
            val fallbackSig = getInstagramSecurityTokenForGetJob(numAccId, path = currentPath, onStatus = onStatus)
            if (!fallbackSig.isNullOrBlank()) {
                reqBuilder = newRequestBuilder(url).header("sig", fallbackSig)
                res = execute(reqBuilder.get().build())
                code = res?.optInt("http code", res.optInt("http_code", res.optInt("status", 0))) ?: 0
            }
        }

        if (res != null && !res.optBoolean("success", false)) {
            val msg = res.optString("message").orEmpty()
            if (msg.contains("không hợp lệ", ignoreCase = true)) {
                res.put("message", "Golike [HTTP $code]: Yêu cầu không hợp lệ (Nick ID $numAccId chưa khớp hoặc sig bị từ chối)")
            }
        }
        return res
    }

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
            context?.let { ctx ->
                try {
                    GolikeAccountsStore.saveSigningKey(ctx, key)
                } catch (_: Exception) {}
            }
        }
        val rawUid = res?.optString("uid")?.takeIf { it.isNotBlank() }
        if (!rawUid.isNullOrBlank()) {
            this.userUid = rawUid
            val numUid = rawUid.toLongOrNull() ?: 0L
            if (numUid > 0L) {
                this.userId = numUid
            }
            context?.let { ctx ->
                try {
                    GolikeAccountsStore.saveUserId(ctx, rawUid)
                } catch (_: Exception) {}
            }
        } else {
            val uid = res?.optLong("uid", 0L) ?: 0L
            if (uid > 0L) {
                this.userId = uid
                this.userUid = uid.toString()
            }
        }
        val sTime = res?.optLong("server_time", 0L) ?: 0L
        if (sTime > 0L) {
            this.serverTimeOffset = sTime - System.currentTimeMillis()
        }
        return res
    }

    // 3c. Sinh chữ ký g-auth chuẩn WebAssembly (WASM sign_v3 của Golike Web)
    private suspend fun generateGAuth(bodyString: String, path: String = "/api/v1/security/token"): String? {
        val ctx = context ?: return null
        var keyB64 = signingKey?.takeIf { it.isNotBlank() }
        if (keyB64.isNullOrBlank()) {
            createSecuritySession()
            keyB64 = signingKey?.takeIf { it.isNotBlank() }
        }
        if (keyB64.isNullOrBlank()) return null

        // Đảm bảo có userId hợp lệ trước khi sinh chữ ký WASM (hỗ trợ cả chuỗi ULID lẫn Long)
        val effectiveUid: Any = this.userUid?.takeIf { it.isNotBlank() }
            ?: GolikeAccountsStore.getUserId(ctx).takeIf { it.isNotBlank() }
            ?: (if ((userId ?: 0L) > 0L) userId!! else null)
            ?: try {
                val meRes = getMe()
                val fetchedStr = meRes?.optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: meRes?.optString("id")?.takeIf { it.isNotBlank() }
                if (!fetchedStr.isNullOrBlank()) {
                    this.userUid = fetchedStr
                    fetchedStr.toLongOrNull()?.let { this.userId = it }
                    GolikeAccountsStore.saveUserId(ctx, fetchedStr)
                    fetchedStr
                } else {
                    0L
                }
            } catch (_: Exception) {
                0L
            }

        return try {
            val sha256 = MessageDigest.getInstance("SHA-256")
            val bodyHash = sha256.digest(bodyString.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val timestamp = System.currentTimeMillis() + serverTimeOffset
            val devId = deviceId?.takeIf { it.isNotBlank() }
                ?: GolikeAccountsStore.getDeviceId(ctx)

            val randBytes = ByteArray(16)
            SecureRandom().nextBytes(randBytes)
            val randHex = Base64.encodeToString(randBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            GolikeWasmSigner.sign(
                context = ctx,
                rawKeyB64 = keyB64,
                timestamp = timestamp,
                nonceB64 = randHex,
                deviceId = devId,
                userId = effectiveUid,
                method = "POST",
                path = path,
                bodyHash = bodyHash
            )
        } catch (e: Exception) {
            android.util.Log.e("GolikeSecurity", "Lỗi tạo g-auth WASM cho $path: ${e.message}")
            null
        }
    }

    // 3d. Tự động giải Captcha kéo thả của Golike qua API ngầm (WASM + Bézier trajectory)
    suspend fun solveCaptchaChallenge(onStatus: (String) -> Unit = {}): Boolean {
        try {
            onStatus("Đang lấy thử thách Captcha...")
            android.util.Log.d("GolikeCaptcha", "Bắt đầu lấy challenge captcha kéo thả...")
            val emptyBody = "{}"
            val challengeGAuth = generateGAuth(emptyBody, path = GolikeNativeBridge.fetchPathChallenge())
            val challengeReqBuilder = newRequestBuilder("$SECURITY_URL/captcha/challenge")
                .post(emptyBody.toRequestBody(JSON_MEDIA_TYPE))
            if (!challengeGAuth.isNullOrBlank()) {
                challengeReqBuilder.header("g-auth", challengeGAuth)
            }
            val challengeRes = execute(challengeReqBuilder.build()) ?: run {
                onStatus("Lỗi kết nối tải Captcha")
                return false
            }
            val challengeObj = challengeRes.optJSONObject("challenge") ?: run {
                val msg = challengeRes.optString("message").ifBlank { "Không có dữ liệu challenge" }
                onStatus("Lỗi Captcha: $msg")
                return false
            }
            val kind = challengeObj.optString("kind")
            val challengeId = challengeObj.optString("challengeId")
            if (kind != "drag" || challengeId.isBlank()) {
                onStatus("Thử thách không hỗ trợ: $kind")
                android.util.Log.w("GolikeCaptcha", "Thử thách không phải drag hoặc thiếu challengeId: kind=$kind, id=$challengeId")
                return false
            }

            val geometry = challengeObj.optJSONObject("geometry") ?: return false
            val startObj = geometry.optJSONObject("start") ?: return false
            val targetObj = geometry.optJSONObject("target") ?: return false
            val waypointObj = geometry.optJSONObject("waypoint")
                ?: geometry.optJSONArray("waypoints")?.optJSONObject(0)
                ?: return false

            val startX = startObj.optDouble("x", 0.0)
            val startY = startObj.optDouble("y", 0.0)
            val targetX = targetObj.optDouble("x", 0.0)
            val targetY = targetObj.optDouble("y", 0.0)
            val waypointX = waypointObj.optDouble("x", 0.0)
            val waypointY = waypointObj.optDouble("y", 0.0)

            onStatus("Đang tính quỹ đạo Bézier (${kotlin.math.round(startX).toInt()},${kotlin.math.round(startY).toInt()} -> ${kotlin.math.round(targetX).toInt()},${kotlin.math.round(targetY).toInt()})...")
            android.util.Log.d("GolikeCaptcha", "Tọa độ giải: start=($startX, $startY), waypoint=($waypointX, $waypointY), target=($targetX, $targetY)")

            val nativePointsStr = GolikeNativeBridge.fetchBezierPoints(startX, startY, waypointX, waypointY, targetX, targetY)
            val pointsArr = if (!nativePointsStr.isNullOrBlank()) {
                org.json.JSONArray(nativePointsStr)
            } else {
                // Fallback tính toán nội bộ nếu Native chưa sẵn sàng
                val bx = 2 * waypointX - 0.5 * startX - 0.5 * targetX
                val by = 2 * waypointY - 0.5 * startY - 0.5 * targetY
                val totalDuration = (720..820).random().toDouble()
                val steps = (24..28).random()
                val arr = org.json.JSONArray()
                for (i in 0..steps) {
                    val s = i.toDouble() / steps
                    val u = s * s * (3.0 - 2.0 * s)
                    val t = kotlin.math.round(s * totalDuration * 100.0) / 100.0
                    var x = (1.0 - u) * (1.0 - u) * startX + 2.0 * (1.0 - u) * u * bx + u * u * targetX
                    var y = (1.0 - u) * (1.0 - u) * startY + 2.0 * (1.0 - u) * u * by + u * u * targetY
                    if (i in 1 until steps) {
                        val jitterX = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6
                        val jitterY = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6
                        x += jitterX
                        y += jitterY
                    }
                    arr.put(org.json.JSONArray().apply {
                        put(t)
                        put(kotlin.math.round(x * 100.0) / 100.0)
                        put(kotlin.math.round(y * 100.0) / 100.0)
                    })
                }
                arr
            }

            onStatus("Đang gửi xác minh Captcha (${pointsArr.length()} điểm)...")
            val verifyPayload = JSONObject().apply {
                put("challengeId", challengeId)
                put("points", pointsArr)
            }
            val verifyBodyStr = verifyPayload.toString()
            val verifyGAuth = generateGAuth(verifyBodyStr, path = GolikeNativeBridge.fetchPathVerify())
            val verifyReqBuilder = newRequestBuilder("$SECURITY_URL/captcha/verify")
                .post(verifyBodyStr.toRequestBody(JSON_MEDIA_TYPE))
            if (!verifyGAuth.isNullOrBlank()) {
                verifyReqBuilder.header("g-auth", verifyGAuth)
            }

            val verifyRes = execute(verifyReqBuilder.build())
            val isOk = verifyRes?.optBoolean("ok", false) == true || verifyRes?.optBoolean("success", false) == true
            if (isOk) {
                onStatus("✅ Xác minh Captcha thành công!")
            } else {
                val err = verifyRes?.optString("message") ?: "Xác minh chưa thành công"
                onStatus("❌ Captcha không đạt: $err")
            }
            android.util.Log.d("GolikeCaptcha", "Kết quả verify captcha ngầm: ok=$isOk | Res: $verifyRes")
            return isOk
        } catch (e: Exception) {
            onStatus("Lỗi giải Captcha: ${e.message}")
            android.util.Log.e("GolikeCaptcha", "Lỗi trong quá trình giải captcha ngầm: ${e.message}", e)
            return false
        }
    }

    // 3e. Lấy chữ ký Token chứng thực cho complete_job
    suspend fun getSecurityToken(adsId: Any, accountId: Any, path: String = GolikeNativeBridge.fetchPathCompleteJob()): String? {
        context?.let { ctx ->
            val syncErr = GolikeNativeBridge.checkSessionIntegrity(ctx)
            if (syncErr != null) {
                lastTokenError = syncErr
                return null
            }
        }
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val bodyObj = JSONObject().apply {
            put("ads_id", adsId.toString())
            put("account_id", numAccId)
            put("async", true)
            put("data", JSONObject.NULL)
        }
        val telPayloadStr = GolikeNativeBridge.fetchTelemetryJson(1)
        val reqPayload = JSONObject().apply {
            put("plt", "tiktok")
            put("act", "complete_job")
            put("req", JSONObject().apply {
                put("method", "POST")
                put("path", path)
                put("query", "")
                put("body", bodyObj.toString())
            })
            put("tel", JSONObject(telPayloadStr))
        }

        val bodyStr = reqPayload.toString()
        val dynamicGAuth = generateGAuth(bodyStr, path = GolikeNativeBridge.fetchPathToken())

        val reqBuilder = newRequestBuilder("$SECURITY_URL/token")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
        if (!dynamicGAuth.isNullOrBlank()) {
            reqBuilder.header("g-auth", dynamicGAuth)
        }

        val res = execute(reqBuilder.build())
        val directToken = res?.optString("token")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
            ?: res?.optString("sig")?.takeIf { it.isNotBlank() }
            ?: res?.optJSONObject("data")?.optString("sig")?.takeIf { it.isNotBlank() }

        if (directToken != null) {
            return directToken
        }

        // Tự động giải ngầm nếu gặp yêu cầu xác minh Captcha
        val action = res?.optString("action")
        val reason = res?.optString("reason")
        if (action == "captcha" || reason == "captcha") {
            android.util.Log.w("GolikeApi", "Gặp yêu cầu Captcha khi hoàn thành Job. Đang tự động giải ngầm...")
            val solved = solveCaptchaChallenge()
            if (solved) {
                val retryBodyStr = reqPayload.toString()
                val retryGAuth = generateGAuth(retryBodyStr, path = GolikeNativeBridge.fetchPathToken())
                val retryReqBuilder = newRequestBuilder("$SECURITY_URL/token")
                    .post(retryBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                if (!retryGAuth.isNullOrBlank()) {
                    retryReqBuilder.header("g-auth", retryGAuth)
                }
                val retryRes = execute(retryReqBuilder.build())
                return retryRes?.optString("token")?.takeIf { it.isNotBlank() }
                    ?: retryRes?.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
                    ?: retryRes?.optString("sig")?.takeIf { it.isNotBlank() }
                    ?: retryRes?.optJSONObject("data")?.optString("sig")?.takeIf { it.isNotBlank() }
            }
        }

        return null
    }

    // 4. Hoàn thành nhiệm vụ (Complete Job)
    suspend fun completeTikTokJob(adsId: Any, accountId: Any): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        var currentPath = GolikeNativeBridge.fetchPathCompleteJob()
        var sigToken = getSecurityToken(adsId, numAccId, currentPath)

        val body = JSONObject().apply {
            put("ads_id", adsId.toString())
            put("account_id", numAccId)
            put("async", true)
            put("data", JSONObject.NULL)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        var reqBuilder = newRequestBuilder("$BASE_URL/advertising/publishers/tiktok/complete-jobs")
            .post(body)
        if (!sigToken.isNullOrBlank()) {
            reqBuilder.header("sig", sigToken)
        }
        var res = execute(reqBuilder.build())
        var code = res?.optInt("status", res.optInt("http_code", 0)) ?: 0

        // Fallback sang /api nếu server yêu cầu
        if (code == 403) {
            currentPath = "/api/advertising/publishers/tiktok/complete-jobs"
            val fallbackSig = getSecurityToken(adsId, numAccId, currentPath)
            if (!fallbackSig.isNullOrBlank()) {
                reqBuilder = newRequestBuilder("$BASE_URL/advertising/publishers/tiktok/complete-jobs")
                    .post(body)
                    .header("sig", fallbackSig)
                res = execute(reqBuilder.build())
            }
        }
        return res
    }

    // 4a-IG. Lấy chữ ký token bảo mật (sig) cho hoàn thành job Instagram
    suspend fun getInstagramSecurityTokenForCompleteJob(
        adsId: Any,
        accountId: Any,
        path: String = "/advertising/publishers/instagram/complete-jobs",
        onStatus: (String) -> Unit = {}
    ): String? {
        context?.let { ctx ->
            val syncErr = GolikeNativeBridge.checkSessionIntegrity(ctx)
            if (syncErr != null) {
                lastTokenError = syncErr
                return null
            }
        }
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val bodyObj = JSONObject().apply {
            put("instagram_users_advertising_id", adsId.toString())
            put("instagram_account_id", numAccId)
            put("async", true)
            put("data", JSONObject.NULL)
        }
        val telPayloadStr = GolikeNativeBridge.fetchTelemetryJson(1)
        val reqPayload = JSONObject().apply {
            put("plt", "instagram")
            put("act", "complete_job")
            put("req", JSONObject().apply {
                put("method", "POST")
                put("path", path)
                put("query", "")
                put("body", bodyObj.toString())
            })
            put("tel", JSONObject(telPayloadStr))
        }

        val bodyStr = reqPayload.toString()
        val dynamicGAuth = generateGAuth(bodyStr, path = GolikeNativeBridge.fetchPathToken())

        val reqBuilder = newRequestBuilder("$SECURITY_URL/token")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
        if (!dynamicGAuth.isNullOrBlank()) {
            reqBuilder.header("g-auth", dynamicGAuth)
        }

        var res = execute(reqBuilder.build())
        var directToken = extractToken(res)

        if (directToken != null) {
            lastTokenError = null
            return directToken
        }

        var action = res?.optString("action")
        var reason = res?.optString("reason")
        var message = res?.optString("message").orEmpty()

        // 1. Tự động làm mới session nếu server yêu cầu retry/rejected khi hoàn thành job
        if (action == "retry" || reason == "rejected") {
            onStatus("Làm mới session bảo mật Golike...")
            this.signingKey = null
            createSecuritySession()
            val retryBodyStr = reqPayload.toString()
            val retryGAuth = generateGAuth(retryBodyStr, path = GolikeNativeBridge.fetchPathToken())
            if (!retryGAuth.isNullOrBlank()) {
                val retryReq = newRequestBuilder("$SECURITY_URL/token")
                    .post(retryBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                    .header("g-auth", retryGAuth)
                    .build()
                res = execute(retryReq)
                directToken = extractToken(res)
                if (directToken != null) {
                    lastTokenError = null
                    return directToken
                }
                action = res?.optString("action")
                reason = res?.optString("reason")
                message = res?.optString("message").orEmpty()
            }
        }

        // 2. Tự động giải ngầm nếu gặp Captcha
        if (action == "captcha" || reason == "captcha" || message.contains("xác minh", ignoreCase = true)) {
            onStatus("🛡️ Golike yêu cầu Captcha khi hoàn thành IG -> Đang tự động giải ngầm...")
            val solved = solveCaptchaChallenge(onStatus)
            if (solved) {
                val retryBodyStr = reqPayload.toString()
                val retryGAuth = generateGAuth(retryBodyStr, path = GolikeNativeBridge.fetchPathToken())
                val retryReqBuilder = newRequestBuilder("$SECURITY_URL/token")
                    .post(retryBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                if (!retryGAuth.isNullOrBlank()) {
                    retryReqBuilder.header("g-auth", retryGAuth)
                }
                val retryRes = execute(retryReqBuilder.build())
                val retToken = extractToken(retryRes)
                if (retToken != null) {
                    lastTokenError = null
                    return retToken
                }
                lastTokenError = "Giải Captcha hoàn thành job xong nhưng server token vẫn từ chối: ${retryRes?.toString() ?: "rỗng"}"
            } else {
                lastTokenError = "Golike yêu cầu xác minh Captcha khi hoàn thành job nhưng giải tự động chưa thành công ($action / $reason / $message)"
            }
        } else {
            val serverErr = res?.optString("message")?.takeIf { it.isNotBlank() }
                ?: res?.optString("error")?.takeIf { it.isNotBlank() }
                ?: (if (res == null) "Không có kết nối tới server bảo mật Golike (/security/token)" else "Server bảo mật từ chối cấp token sig hoàn thành: $res")
            lastTokenError = serverErr
        }
        return null
    }

    suspend fun completeInstagramJob(
        adsId: Any,
        accountId: String,
        captchaToken: String? = null,
        onStatus: (String) -> Unit = {}
    ): JSONObject? {
        val numAccId = accountId.toLongOrNull() ?: accountId
        val adsIdStr = adsId.toString()
        var currentPath = "/advertising/publishers/instagram/complete-jobs"
        var sigToken = getInstagramSecurityTokenForCompleteJob(adsIdStr, numAccId, path = currentPath, onStatus = onStatus)
        if (sigToken.isNullOrBlank()) {
            val err = lastTokenError ?: "Thiếu chữ ký sig hoàn thành (Không lấy được token bảo mật từ Golike)"
            return JSONObject().apply {
                put("success", false)
                put("status", 400)
                put("message", err)
            }
        }

        val body = JSONObject().apply {
            put("instagram_users_advertising_id", adsIdStr)
            put("instagram_account_id", numAccId)
            put("async", true)
            put("data", JSONObject.NULL)
            if (!captchaToken.isNullOrBlank()) {
                put("captcha_token", captchaToken)
            }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        var reqBuilder = newRequestBuilder("$BASE_URL/advertising/publishers/instagram/complete-jobs")
            .post(body)
            .header("sig", sigToken)

        var res = execute(reqBuilder.build())
        val code = res?.optInt("status", res.optInt("http_code", 0)) ?: 0
        if (code == 403) {
            currentPath = "/api/advertising/publishers/instagram/complete-jobs"
            val fallbackSig = getInstagramSecurityTokenForCompleteJob(adsIdStr, numAccId, path = currentPath, onStatus = onStatus)
            if (!fallbackSig.isNullOrBlank()) {
                reqBuilder = newRequestBuilder("$BASE_URL/advertising/publishers/instagram/complete-jobs")
                    .post(body)
                    .header("sig", fallbackSig)
                res = execute(reqBuilder.build())
            }
        }
        return res
    }

    // 5-IG. Bỏ qua nhiệm vụ Instagram (Skip Job)
    fun skipInstagramJob(adsId: Any, objectId: String, accountId: Any, type: String): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val body = JSONObject().apply {
            put("ads_id", adsId.toString())
            put("object_id", objectId)
            put("account_id", numAccId)
            put("type", type)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/advertising/publishers/instagram/skip-jobs").post(body).build())
    }

    fun completeFacebookJob(adsId: Long, fbId: String): JSONObject? {
        val body = JSONObject().apply {
            put("ads_id", adsId)
            put("fb_id", fbId)
            put("async", true)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/advertising/publishers/complete-jobs-2026").post(body).build())
    }

    // 5. Bỏ qua nhiệm vụ (Skip Job)
    fun skipJob(adsId: Any, objectId: String, accountId: Any, type: String): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val body = JSONObject().apply {
            put("ads_id", adsId.toString())
            put("object_id", objectId)
            put("account_id", numAccId)
            put("type", type)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/advertising/publishers/tiktok/skip-jobs").post(body).build())
    }

    // 6. Gửi báo cáo lỗi nhiệm vụ (Send Report)
    fun sendReport(adsId: Any, accountId: Any, description: String = "Job đủ số lượng", provider: String = "tiktok", type: String = "ads"): JSONObject? {
        val numAccId = when (accountId) {
            is Number -> accountId
            is String -> accountId.toLongOrNull() ?: accountId
            else -> accountId
        }
        val body = JSONObject().apply {
            put("description", description)
            put("users_advertising_id", adsId.toString())
            put("type", type)
            put("provider", provider)
            put("fb_id", numAccId)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(newRequestBuilder("$BASE_URL/report/send").post(body).build())
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
