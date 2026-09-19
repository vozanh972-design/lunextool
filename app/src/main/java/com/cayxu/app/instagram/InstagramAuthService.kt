package com.cayxu.app.instagram

import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import com.cayxu.app.util.NativeSecurity

/**
 * Logic đăng nhập, xác thực và quản lý phiên Instagram được trích xuất từ APK (slunexAUTO / MToolMax).
 * 
 * Các thành phần gốc trong classes.dex:
 * - `Ll2/z;` : InstagramApiClient (xử lý cookie, session, headers, GraphQL & REST API)
 * - `Ll2/C;` : Web Account Check / Create Attempt API
 * - `Lk1/c;` : CAA Bloks Login Request (com.bloks.www.bloks.caa.login.async.send_login_request)
 * - `Lk2/I0;`: 2FA Verify Code (com.bloks.www.two_step_verification.verify_code.async)
 */
class InstagramAuthService {

    companion object {
        const val BASE_URL = "https://www.instagram.com"
        const val API_BASE_URL = "https://i.instagram.com"
        
        // App ID và ID định danh client chuẩn của Instagram Web (lấy động từ NativeSecurity C++)
        val IG_APP_ID: String get() = NativeSecurity.getIgAppId()
        val ASBD_ID: String get() = NativeSecurity.getIgAsbdId()
        const val IG_AJAX_VERSION = "1006309104"
        const val GRAPHQL_DOC_ID_LIKE = "9595477160535898"
        const val GRAPHQL_DOC_ID_PROFILE = "24644030398570558"

        // Regex trích xuất token & thông tin người dùng từ HTML/Cookies
        val CSRF_TOKEN_REGEX: Pattern = Pattern.compile("csrftoken=([^;]+)")
        val USER_ID_REGEX: Pattern = Pattern.compile("userID\":\"([^\"]+)\"")
        val USERNAME_REGEX: Pattern = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"")
        val FULL_NAME_REGEX: Pattern = Pattern.compile("\"full_name\"\\s*:\\s*\"([^\"]+)\"")
        val PROFILE_PIC_REGEX: Pattern = Pattern.compile("\"profile_pic_url\"\\s*:\\s*\"([^\"]+)\"")
        val DTSG_TOKEN_REGEX: Pattern = Pattern.compile("DTSGInitialData[^\"]*\"token\":\"([^\"]+)\"")
        val LSD_TOKEN_REGEX: Pattern = Pattern.compile("\"LSD\"\\s*,\\s*\\[\\s*],\\s*\\{\"token\"\\s*:\\s*\"([^\"]+)\"")
    }

    data class InstagramUserInfo(
        val userId: String,
        val username: String,
        val fullName: String,
        val profilePicUrl: String?,
        val csrfToken: String?,
        val fbDtsg: String?,
        val lsd: String?
    )

    data class ProxyConfig(
        val host: String,
        val port: Int,
        val type: Proxy.Type = Proxy.Type.HTTP,
        val username: String? = null,
        val password: String? = null
    )

    private var okHttpClient: OkHttpClient
    private var currentUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    init {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * Cấu hình Proxy cho HTTP Client
     */
    fun setProxy(proxyConfig: ProxyConfig?) {
        val builder = okHttpClient.newBuilder()
        if (proxyConfig != null) {
            val address = InetSocketAddress(proxyConfig.host, proxyConfig.port)
            builder.proxy(Proxy(proxyConfig.type, address))
            
            if (!proxyConfig.username.isNullOrEmpty() && !proxyConfig.password.isNullOrEmpty()) {
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(proxyConfig.username, proxyConfig.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        } else {
            builder.proxy(Proxy.NO_PROXY)
        }
        okHttpClient = builder.build()
    }

    /**
     * Trích xuất CSRF Token từ chuỗi Cookie
     */
    fun extractCsrfToken(cookieString: String): String? {
        val matcher = CSRF_TOKEN_REGEX.matcher(cookieString)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * 1. Xác thực và Lấy thông tin tài khoản Instagram từ Cookie (tương ứng Ll2/z; -> D & getInfo)
     */
    @Throws(Exception::class)
    fun verifyCookieAndGetUserInfo(cookieString: String, userAgent: String? = null): InstagramUserInfo {
        val ua = userAgent ?: currentUserAgent
        val csrfToken = extractCsrfToken(cookieString) 
            ?: throw IllegalArgumentException("Không thể lấy CSRF token từ cookies")

        val request = Request.Builder()
            .url(BASE_URL)
            .header("User-Agent", ua)
            .header("Cookie", cookieString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
            .header("Sec-Fetch-Site", "same-origin")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""

            // Kiểm tra trạng thái Cookie Die / Chặn đăng nhập
            if (response.code in listOf(401, 403) || body.contains("login_required") || body.contains("checkpoint_required")) {
                throw IllegalStateException("Cookie Die hoặc yêu cầu đăng nhập lại/checkpoint!")
            }

            // Trích xuất các trường thông tin
            val userIdMatcher = USER_ID_REGEX.matcher(body)
            val usernameMatcher = USERNAME_REGEX.matcher(body)
            val fullNameMatcher = FULL_NAME_REGEX.matcher(body)
            val picMatcher = PROFILE_PIC_REGEX.matcher(body)
            val dtsgMatcher = DTSG_TOKEN_REGEX.matcher(body)
            val lsdMatcher = LSD_TOKEN_REGEX.matcher(body)

            val userId = if (userIdMatcher.find()) userIdMatcher.group(1) else ""
            val username = if (usernameMatcher.find()) usernameMatcher.group(1) else ""
            val fullName = if (fullNameMatcher.find()) fullNameMatcher.group(1) else ""
            val profilePic = if (picMatcher.find()) {
                picMatcher.group(1).replace("\\u0026", "&").replace("\\/", "/")
            } else null
            val fbDtsg = if (dtsgMatcher.find()) dtsgMatcher.group(1) else null
            val lsd = if (lsdMatcher.find()) lsdMatcher.group(1) else null

            if (userId.isEmpty() && username.isEmpty()) {
                throw IllegalStateException("Không thể lấy thông tin cần thiết từ cookies. Có thể cookies đã hết hạn hoặc không hợp lệ.")
            }

            return InstagramUserInfo(
                userId = userId,
                username = username,
                fullName = fullName,
                profilePicUrl = profilePic,
                csrfToken = csrfToken,
                fbDtsg = fbDtsg,
                lsd = lsd
            )
        }
    }

    /**
     * 2. Đăng nhập qua Bloks CAA Request (tương ứng Lk1/c; -> send_login_request)
     */
    @Throws(Exception::class)
    fun sendBloksLoginRequest(
        username: String,
        passwordEncrypted: String,
        deviceId: String,
        familyDeviceId: String,
        waterfallId: String
    ): String {
        val formBody = FormBody.Builder()
            .add("params", JSONObject().apply {
                put("client_input_params", JSONObject().apply {
                    put("contact_point", username)
                    put("password", passwordEncrypted)
                    put("device_id", deviceId)
                    put("family_device_id", familyDeviceId)
                    put("login_source", "Login")
                    put("waterfall_id", waterfallId)
                    put("credential_type", "password")
                })
                put("server_params", JSONObject().apply {
                    put("credential_type", "password")
                    put("server_login_source", "login")
                })
            }.toString())
            .add("bloks_versioning_id", "3469837656910fc29c9aa968ab33845cd52eb5253ae110610b944c8e9028d8f6")
            .add("app_id", "com.bloks.www.bloks.caa.login.async.send_login_request")
            .add("fb_api_req_friendly_name", "FbBloksActionRootQuery-com.bloks.www.bloks.caa.login.async.send_login_request")
            .add("fb_api_caller_class", "graphservice")
            .build()

        val request = Request.Builder()
            .url("https://b-graph.facebook.com/graphql")
            .header("User-Agent", "[FBAN/FB4A;FBAV/542.0.0.46.151;FBBV/840338789;FBDM/{density=0.75,width=300,height=540};FBLC/vi_VN;FBRV/0;FBCR/MobiFone;FBMF/MTool-Max;FBBD/MTool-Max;FBPN/com.facebook.katana;FBDV/MTool-Max;FBSV/9;FBOP/1;FBCA/x86_64:arm64-v8a;]")
            .header("Authorization", "OAuth 350685531728|62f8ce9f74b12f84c123cc23437a4a32")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .post(formBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    /**
     * 3. Kiểm tra tài khoản / Web Create Attempt (tương ứng Ll2/C;)
     */
    @Throws(Exception::class)
    fun attemptWebAccountCheck(emailOrUsername: String): String {
        val formBody = FormBody.Builder()
            .add("email", emailOrUsername)
            .add("first_name", "")
            .add("username", emailOrUsername)
            .add("opt_into_one_tap", "false")
            .build()

        val request = Request.Builder()
            .url("https://www.instagram.com/api/v1/web/accounts/web_create_ajax/attempt/")
            .header("Origin", BASE_URL)
            .header("Referer", "$BASE_URL/accounts/emailsignup/")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }
}
