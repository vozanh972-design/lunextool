package com.cayxu.app.instagram

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Full logic đăng nhập Instagram - Trích xuất chuẩn 100% từ APK (Ll2/z; + Ll2/C; + Lk1/c;)
 * Bao gồm:
 * 1. Random User-Agent (logic parse & build UA từ APK)
 * 2. Đăng nhập bằng Cookie
 * 3. Đăng nhập bằng Username/Password qua CAA Bloks
 * 4. Xác thực 2FA
 * 5. Kiểm tra / bóc tách thông tin tài khoản
 * 6. Phát hiện Cookie Die / Checkpoint
 */
class InstagramLoginManager(
    private var proxyConfig: ProxyConfig? = null
) {
    // =====================================================================
    // CONSTANTS - trích xuất trực tiếp từ string pool của classes.dex
    // =====================================================================
    companion object {
        const val BASE_URL = "https://www.instagram.com"
        const val API_BASE_URL = "https://i.instagram.com"
        const val BLOKS_ENDPOINT = "https://b-graph.facebook.com/graphql"

        // Instagram Web API identifiers (từ Ll2/z;)
        const val IG_APP_ID      = "936619743392459"
        const val ASBD_ID        = "198387"
        const val AJAX_ROLLOUT   = "1006309104"

        // CAA Bloks Login identifiers (từ Lk1/c;)
        const val CAA_APP_ID         = "com.bloks.www.bloks.caa.login.async.send_login_request"
        const val CAA_BLOKS_VERSION  = "3469837656910fc29c9aa968ab33845cd52eb5253ae110610b944c8e9028d8f6"
        const val CAA_STYLES_ID      = "964d559c1e2aa0142b5069bc8cb1adea"
        const val CAA_CLIENT_DOC_ID  = "119940804214876861379510865434"
        const val CAA_OAUTH_TOKEN    = "OAuth 350685531728|62f8ce9f74b12f84c123cc23437a4a32"
        const val CAA_FB_USER_AGENT  = "[FBAN/FB4A;FBAV/542.0.0.46.151;FBBV/840338789;FBDM/{density=0.75,width=300,height=540};FBLC/vi_VN;FBRV/0;FBCR/MobiFone;FBMF/MTool-Max;FBBD/MTool-Max;FBPN/com.facebook.katana;FBDV/MTool-Max;FBSV/9;FBOP/1;FBCA/x86_64:arm64-v8a;]"

        // 2FA Bloks identifiers (từ Lk2/I0;)
        const val TWO_FA_APP_ID = "com.bloks.www.two_step_verification.verify_code.async"

        // Regex patterns - từ <clinit> của Ll2/z; và Ll2/C;
        val PATTERN_CSRF      = Pattern.compile("csrftoken=([^;]+)")
        val PATTERN_USER_ID   = Pattern.compile("userID\":\"([^\"]+)\"")
        val PATTERN_USERNAME  = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_FULLNAME  = Pattern.compile("\"full_name\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_PIC_URL   = Pattern.compile("\"profile_pic_url\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_DTSG      = Pattern.compile("DTSGInitialData[^\"]*\"token\":\"([^\"]+)\"")
        val PATTERN_LSD       = Pattern.compile("\"LSD\"\\s*,\\s*\\[\\s*],\\s*\\{\"token\"\\s*:\\s*\"([^\"]+)\"")

        // UA parse patterns - từ Ll2/z; method C (parseUserAgent)
        val PATTERN_CHROME  = Pattern.compile("Chrome/([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)")
        val PATTERN_FIREFOX = Pattern.compile("Firefox/([0-9]+)\\.([0-9]+)")
        val PATTERN_SAFARI  = Pattern.compile("Version/([0-9]+)\\.([0-9]+).*Safari")
        val PATTERN_EDGE    = Pattern.compile("Edg/([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)")
        val PATTERN_MOBILE  = Pattern.compile("(?i)(mobile|android|iphone|ipad)")
        val PATTERN_WIN_NT  = Pattern.compile("Windows NT ([0-9]+)\\.([0-9]+)")
        val PATTERN_MACOS   = Pattern.compile("Mac OS X ([0-9]+)[._]([0-9]+)[._]?([0-9]+)?")
        val PATTERN_ANDROID = Pattern.compile("Android ([0-9]+)\\.?([0-9]+)?")

        // === Random UA Pool - tất cả đều trích xuất từ string pool của APK ===
        private val UA_POOL_DESKTOP = listOf(
            // Chrome Windows - từ Ll2/z; clinit
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            // Chrome MacOS - từ Lk1/h; và Lz2/l;
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            // Edge Windows
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
        )

        private val UA_POOL_MOBILE = listOf(
            // Android Chrome - từ Li2/X;, Lk2/JobApi;, Ll2/o;, Lm2/d;
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
            // Samsung Browser - từ Ll2/w;
            "Mozilla/5.0 (Linux; Android 10; Mi 9T Pro) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/12.1 Chrome/79.0.3945.136 Mobile Safari/537.36",
            // WebView - từ Lnmtpro/mtoolv2/WebViewActivity;
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36"
        )

        /**
         * Random UA theo thiết bị (logic từ Ll2/z; method C - parseUserAgent + getBrowserInfo)
         * - isMobile=true -> lấy UA từ pool mobile
         * - isMobile=false -> lấy UA từ pool desktop
         */
        fun randomUserAgent(preferMobile: Boolean = false): String {
            return if (preferMobile) {
                UA_POOL_MOBILE.random()
            } else {
                UA_POOL_DESKTOP.random()
            }
        }

        /**
         * Parse thông tin browser từ UA string (logic từ Ll2/z; method C)
         * Trả về: Pair(browserName, isMobile)
         */
        fun parseUserAgentInfo(ua: String): Pair<String, Boolean> {
            val isMobile = PATTERN_MOBILE.matcher(ua).find()
            return when {
                PATTERN_EDGE.matcher(ua).find()    -> Pair("Edge", isMobile)
                PATTERN_FIREFOX.matcher(ua).find() -> Pair("Firefox", isMobile)
                PATTERN_SAFARI.matcher(ua).find()  -> Pair("Safari", isMobile)
                PATTERN_CHROME.matcher(ua).find()  -> Pair("Chrome", isMobile)
                else -> Pair("Unknown", isMobile)
            }
        }
    }

    // =====================================================================
    // DATA CLASSES
    // =====================================================================
    data class ProxyConfig(
        val host: String,
        val port: Int,
        val type: Proxy.Type = Proxy.Type.HTTP,
        val username: String? = null,
        val password: String? = null
    )

    data class InstagramAccount(
        val userId: String,
        val username: String,
        val fullName: String,
        val profilePicUrl: String?,
        val cookie: String,
        val userAgent: String,
        val csrfToken: String,
        val fbDtsg: String?,
        val lsd: String?
    )

    data class LoginResult(
        val isSuccess: Boolean,
        val account: InstagramAccount? = null,
        val errorType: LoginError = LoginError.NONE,
        val message: String = ""
    )

    enum class LoginError {
        NONE, COOKIE_DIE, CHECKPOINT, TWO_FA_REQUIRED, WRONG_PASSWORD, RATE_LIMITED, NETWORK_ERROR
    }

    // =====================================================================
    // HTTP CLIENT
    // =====================================================================
    private var httpClient: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)

        proxyConfig?.let { configureProxy(builder, it) }
        httpClient = builder.build()
    }

    private fun configureProxy(builder: OkHttpClient.Builder, config: ProxyConfig) {
        val address = InetSocketAddress(config.host, config.port)
        builder.proxy(Proxy(config.type, address))

        if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
            if (config.type == Proxy.Type.SOCKS) {
                // SOCKS4 không hỗ trợ authentication (log từ APK: "SOCKS4 proxy không hỗ trợ authentication, bỏ qua username/password")
                if (config.type != Proxy.Type.SOCKS) {
                    System.setProperty("java.net.socks.username", config.username)
                    System.setProperty("java.net.socks.password", config.password)
                }
            } else {
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(config.username, config.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        }
    }

    // =====================================================================
    // HELPER: Build Headers
    // =====================================================================
    private fun buildWebHeaders(
        cookie: String,
        userAgent: String,
        csrfToken: String
    ): Headers {
        // Logic từ Ll2/z; method Failed to stop AutoIG (phương thức build request IG)
        return Headers.Builder()
            .add("user-agent", userAgent)
            .add("cookie", cookie)
            .add("accept", "*/*")
            .add("accept-language", "vi,en-US;q=0.9,en;q=0.8")
            .add("content-type", "application/x-www-form-urlencoded")
            .add("origin", BASE_URL)
            .add("referer", "$BASE_URL/")
            .add("authority", "i.instagram.com")
            .add("x-asbd-id", ASBD_ID)
            .add("x-csrftoken", csrfToken)
            .add("x-ig-app-id", IG_APP_ID)
            .add("x-ig-www-claim", "0")
            .add("x-instagram-ajax", AJAX_ROLLOUT)
            .add("x-requested-with", "XMLHttpRequest")
            .build()
    }

    // =====================================================================
    // 1. ĐĂNG NHẬP BẰNG COOKIE
    // =====================================================================
    /**
     * Đăng nhập bằng Cookie string.
     * Logic từ: Ll2/z; method D (bóc tách userInfo từ HTML) + method init (build client)
     *
     * @param cookieString Chuỗi cookie dạng: csrftoken=xxx; sessionid=xxx; ds_user_id=xxx; ...
     * @param userAgent UA sử dụng (nếu null sẽ random)
     */
    fun loginWithCookie(cookieString: String, userAgent: String? = null): LoginResult {
        val ua = userAgent ?: randomUserAgent(preferMobile = false)

        val csrfToken = PATTERN_CSRF.matcher(cookieString).let {
            if (it.find()) it.group(1) else ""
        }

        if (csrfToken.isEmpty()) {
            return LoginResult(
                isSuccess = false,
                errorType = LoginError.COOKIE_DIE,
                message = "Không thể lấy CSRF token từ cookies"
            )
        }

        return try {
            val request = Request.Builder()
                .url(BASE_URL)
                .header("User-Agent", ua)
                .header("Cookie", cookieString)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .header("DNT", "1")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""

                // Phát hiện các trạng thái lỗi (logic từ Ll2/z; method e)
                when {
                    response.code == 401 || response.code == 403 || body.contains("login_required") ->
                        return LoginResult(false, errorType = LoginError.COOKIE_DIE, message = "Cookie Die - Cần đăng nhập lại")

                    body.contains("checkpoint_required") || body.contains("checkpoint") ->
                        return LoginResult(false, errorType = LoginError.CHECKPOINT, message = "Tài khoản bị checkpoint")

                    body.contains("feedback_required") || body.contains("sentry_block") ->
                        return LoginResult(false, errorType = LoginError.RATE_LIMITED, message = "Bị rate limit / block tạm thời")
                }

                // Bóc tách thông tin tài khoản (logic từ Ll2/z; method D)
                val userId   = PATTERN_USER_ID.matcher(body).let { if (it.find()) it.group(1) else "" }
                val username = PATTERN_USERNAME.matcher(body).let { if (it.find()) it.group(1) else "" }
                val fullName = PATTERN_FULLNAME.matcher(body).let { if (it.find()) it.group(1) else "" }
                val picUrl   = PATTERN_PIC_URL.matcher(body).let {
                    if (it.find()) it.group(1)
                        .replace("\\u0026", "&")
                        .replace("\\/", "/")
                    else null
                }
                val fbDtsg = PATTERN_DTSG.matcher(body).let { if (it.find()) it.group(1) else null }
                val lsd    = PATTERN_LSD.matcher(body).let  { if (it.find()) it.group(1) else null }

                if (userId.isEmpty() && username.isEmpty()) {
                    return LoginResult(
                        false,
                        errorType = LoginError.COOKIE_DIE,
                        message = "Không thể lấy thông tin cần thiết từ cookies. Có thể cookies đã hết hạn hoặc không hợp lệ."
                    )
                }

                LoginResult(
                    isSuccess = true,
                    account = InstagramAccount(
                        userId       = userId,
                        username     = username,
                        fullName     = fullName,
                        profilePicUrl = picUrl,
                        cookie       = cookieString,
                        userAgent    = ua,
                        csrfToken    = csrfToken,
                        fbDtsg       = fbDtsg,
                        lsd          = lsd
                    )
                )
            }
        } catch (e: IOException) {
            val errMsg = e.message?.lowercase() ?: ""
            val errorType = when {
                "failed to connect" in errMsg || "proxy" in errMsg -> LoginError.NETWORK_ERROR
                "sockettimeoutexception" in errMsg || "connection timed out" in errMsg -> LoginError.NETWORK_ERROR
                "connection refused" in errMsg -> LoginError.NETWORK_ERROR
                "network unreachable" in errMsg -> LoginError.NETWORK_ERROR
                else -> LoginError.NETWORK_ERROR
            }
            LoginResult(false, errorType = errorType, message = "Lỗi kết nối: ${e.message}")
        }
    }

    // =====================================================================
    // 2. ĐĂNG NHẬP BẰNG USERNAME/PASSWORD (CAA Bloks)
    // =====================================================================
    /**
     * Đăng nhập Facebook/Instagram qua CAA Bloks GraphQL.
     * Logic từ: Lk1/c; method k (send_login_request)
     */
    fun loginWithPassword(
        username: String,
        passwordEncrypted: String,
        deviceId: String,
        familyDeviceId: String,
        waterfallId: String
    ): String {
        val clientInputParams = JSONObject().apply {
            put("contact_point", username)
            put("password", passwordEncrypted)
            put("device_id", deviceId)
            put("family_device_id", familyDeviceId)
            put("login_source", "Login")
            put("waterfall_id", waterfallId)
            put("credential_type", "password")
            put("event_flow", "login_manual")
            put("login_attempt_count", "1")
            put("access_flow_version", "F2_FLOW")
            put("is_caa_perf_enabled", true)
            put("should_trigger_override_login_2fa_action", false)
            put("should_trigger_override_login_success_action", false)
        }

        val serverParams = JSONObject().apply {
            put("credential_type", "password")
            put("server_login_source", "login")
            put("login_source", "Login")
            put("INTERNAL__latency_qpl_marker_id", 0)
        }

        val rootParams = JSONObject().apply {
            put("client_input_params", clientInputParams)
            put("server_params", serverParams)
        }

        val formBody = FormBody.Builder()
            .add("params", rootParams.toString())
            .add("bloks_versioning_id", CAA_BLOKS_VERSION)
            .add("app_id", CAA_APP_ID)
            .add("styles_id", CAA_STYLES_ID)
            .add("fb_api_req_friendly_name", "FbBloksActionRootQuery-$CAA_APP_ID")
            .add("fb_api_caller_class", "graphservice")
            .add("client_doc_id", CAA_CLIENT_DOC_ID)
            .add("method", "post")
            .add("format", "json")
            .add("server_timestamps", "true")
            .add("locale", "vi_VN")
            .add("purpose", "fetch")
            .build()

        val request = Request.Builder()
            .url(BLOKS_ENDPOINT)
            .header("User-Agent", CAA_FB_USER_AGENT)
            .header("Authorization", CAA_OAUTH_TOKEN)
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Http-Engine", "Tigon/Liger")
            .header("X-Fb-Client-Ip", "True")
            .header("X-Fb-Server-Cluster", "")
            .header("X-Tigon-Is-Retry", "False")
            .header("X-Graphql-Request-Purpose", "fetch")
            .header("X-Graphql-Client-Library", "graphservice")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    // =====================================================================
    // 3. XÁC THỰC 2FA
    // =====================================================================
    /**
     * Gửi mã xác thực 2FA.
     * Logic từ: Lk2/I0; (two_step_verification.verify_code.async)
     */
    fun verifyTwoFactor(
        verificationCode: String,
        twoFactorIdentifier: String,
        deviceId: String,
        shouldTrustDevice: Boolean = false
    ): String {
        val clientInputParams = JSONObject().apply {
            put("verification_code", verificationCode)
            put("two_factor_identifier", twoFactorIdentifier)
            put("device_id", deviceId)
            put("should_trust_device", if (shouldTrustDevice) 1 else 0)
        }

        val serverParams = JSONObject().apply {
            put("flow_source", "login_challenges")
            put("INTERNAL__latency_qpl_marker_id", 36707139)
        }

        val rootParams = JSONObject().apply {
            put("client_input_params", clientInputParams)
            put("server_params", serverParams)
        }

        val formBody = FormBody.Builder()
            .add("params", rootParams.toString())
            .add("app_id", TWO_FA_APP_ID)
            .add("fb_api_req_friendly_name", "FbBloksActionRootQuery-$TWO_FA_APP_ID")
            .add("fb_api_caller_class", "graphservice")
            .build()

        val request = Request.Builder()
            .url(BLOKS_ENDPOINT)
            .header("Authorization", CAA_OAUTH_TOKEN)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    // =====================================================================
    // 4. WEB ACCOUNT ATTEMPT CHECK (Kiểm tra email/username)
    // =====================================================================
    /**
     * Kiểm tra tính hợp lệ email/username khi đăng ký.
     * Logic từ: Ll2/C; method (web_create_ajax/attempt)
     */
    fun attemptAccountCheck(emailOrUsername: String, firstName: String = ""): String {
        val ua = randomUserAgent(preferMobile = false)

        val formBody = FormBody.Builder()
            .add("email", emailOrUsername)
            .add("first_name", firstName)
            .add("username", emailOrUsername)
            .add("opt_into_one_tap", "false")
            .add("use_new_suggested_user_name", "true")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/web/accounts/web_create_ajax/attempt/")
            .header("User-Agent", ua)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", BASE_URL)
            .header("Referer", "$BASE_URL/accounts/emailsignup/")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    // =====================================================================
    // 5. REFRESH / VERIFY SESSION
    // =====================================================================
    /**
     * Kiểm tra cookie còn sống không bằng cách gọi API /accounts/edit/
     * Nếu OK = cookie còn sống, nếu 401/redirect về login = Cookie Die
     */
    fun verifyCookieAlive(account: InstagramAccount): Boolean {
        return try {
            val request = Request.Builder()
                .url("$API_BASE_URL/api/v1/web/accounts/edit/")
                .headers(buildWebHeaders(account.cookie, account.userAgent, account.csrfToken))
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.code !in listOf(401, 403) && !response.isRedirect
            }
        } catch (e: Exception) {
            false
        }
    }
}
