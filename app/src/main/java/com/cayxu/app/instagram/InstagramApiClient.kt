package com.cayxu.app.instagram

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Client tương tác trực tiếp với Instagram Web API qua Cookie:
 * - Lấy thông tin user
 * - Follow user (qua user id hoặc username)
 * - Like bài viết (qua media id hoặc link bài viết)
 */
class InstagramApiClient(
    var cookie: String = "",
    var userAgent: String = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1",
    proxyConfig: ProxyConfig? = null
) {
    companion object {
        const val BASE_URL = "https://www.instagram.com"
        const val API_BASE_URL = "https://i.instagram.com"

        // Dùng đúng giá trị từ Python script hoạt động được
        const val APP_ID = "936619743392459"          // x-ig-app-id (desktop web)
        const val ASBD_ID = "129477"                   // x-asbd-id
        const val AJAX_ROLLOUT = "1014868636"          // x-instagram-ajax
        const val IG_WWW_CLAIM = "hmac.AR1Jw2LrciyrzAQskwSVGREElPZZJZjW74y38oTjDnNHOu9e"

        // GraphQL doc_ids (chỉ dùng cho getUserIdFromUsername)
        const val DOC_ID_PROFILE_POSTS = "28322872020710458"

        val PATTERN_CSRF: Pattern = Pattern.compile("csrftoken=([^;]+)")
        val PATTERN_USER_ID: Pattern = Pattern.compile("userID\\\":\\\"([^\\\"]+)\\\"")
        val PATTERN_USERNAME: Pattern = Pattern.compile("\\\"username\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        val PATTERN_FULL_NAME: Pattern = Pattern.compile("\\\"full_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        val PATTERN_PROFILE_PIC: Pattern = Pattern.compile("\\\"profile_pic_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        val PATTERN_DTSG: Pattern = Pattern.compile("DTSGInitialData[^\\\"]*\\\"token\\\":\\\"([^\\\"]+)\\\"")
        val PATTERN_LSD: Pattern = Pattern.compile("\\\"LSD\\\"\\s*,\\s*\\[\\s*],\\s*\\{\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
    }

    data class ProxyConfig(
        val host: String,
        val port: Int,
        val type: Proxy.Type = Proxy.Type.HTTP,
        val username: String? = null,
        val password: String? = null
    )

    data class UserProfile(
        val userId: String,
        val username: String,
        val fullName: String,
        val profilePicUrl: String?,
        val csrfToken: String?,
        val fbDtsg: String?,
        val lsd: String?
    )

    private var httpClient: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)

        if (proxyConfig != null) {
            setupProxy(builder, proxyConfig)
        }
        httpClient = builder.build()
    }

    private fun setupProxy(builder: OkHttpClient.Builder, config: ProxyConfig) {
        val address = InetSocketAddress(config.host, config.port)
        builder.proxy(Proxy(config.type, address))

        if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
            if (config.type == Proxy.Type.SOCKS) {
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(config.username, config.password.toCharArray())
                    }
                })
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

    fun extractCsrfToken(): String? {
        val matcher = PATTERN_CSRF.matcher(cookie)
        if (matcher.find()) return matcher.group(1)
        val altMatcher = Pattern.compile("(?:^|;\\s*)csrftoken=([^;]+)").matcher(cookie)
        if (altMatcher.find()) return altMatcher.group(1)
        return null
    }

    private fun buildStandardHeaders(csrfToken: String? = null, referer: String? = null, appId: String = APP_ID): Headers {
        val csrf = csrfToken ?: extractCsrfToken() ?: ""
        val ref = referer ?: "$BASE_URL/"
        return Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi-VN,vi;q=0.9,ja-JP;q=0.8,ja;q=0.7,en-JP;q=0.6,en;q=0.5")
            .add("Origin", BASE_URL)
            .add("Referer", ref)
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", appId)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-Instagram-AJAX", AJAX_ROLLOUT)
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("sec-ch-ua-mobile", "?1")
            .add("sec-ch-ua-platform", "\"iOS\"")
            .add("x-ig-max-touch-points", "1")
            .build()
    }

    /**
     * Lấy thông tin tài khoản & kiểm tra trạng thái cookie
     */
    @Throws(Exception::class)
    fun fetchUserInfo(): UserProfile {
        val request = Request.Builder()
            .url(BASE_URL)
            .headers(buildStandardHeaders())
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.code in listOf(401, 403) || body.contains("login_required") || body.contains("checkpoint_required")) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu checkpoint / đăng nhập lại")
            }

            val uid = PATTERN_USER_ID.matcher(body).let { if (it.find()) it.group(1) else "" }
            val uname = PATTERN_USERNAME.matcher(body).let { if (it.find()) it.group(1) else "" }
            val fname = PATTERN_FULL_NAME.matcher(body).let { if (it.find()) it.group(1) else "" }
            val pic = PATTERN_PROFILE_PIC.matcher(body).let {
                if (it.find()) it.group(1).replace("\\u0026", "&").replace("\\/", "/") else null
            }
            val dtsg = PATTERN_DTSG.matcher(body).let { if (it.find()) it.group(1) else null }
            val lsd = PATTERN_LSD.matcher(body).let { if (it.find()) it.group(1) else null }

            return UserProfile(
                userId = uid,
                username = uname,
                fullName = fname,
                profilePicUrl = pic,
                csrfToken = extractCsrfToken(),
                fbDtsg = dtsg,
                lsd = lsd
            )
        }
    }

    /**
     * Chuẩn hóa link / username Instagram thành username hoặc user_id sạch
     */
    fun cleanInstagramUsername(target: String): String {
        var t = target.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) {
            val uri = android.net.Uri.parse(t)
            val segments = uri.pathSegments.filter { it.isNotBlank() }
            if (segments.isNotEmpty()) {
                // Ví dụ: https://instagram.com/username/?igsh=...
                val first = segments[0]
                if (first != "p" && first != "reel" && first != "tv" && first != "stories") {
                    t = first
                } else if (segments.size >= 2) {
                    t = segments[1]
                }
            }
        }
        return t.substringBefore("?").substringBefore("/").removePrefix("@").trim()
    }

    /**
     * Tra cứu user ID từ username bằng GraphQL PolarisProfilePageContentQuery / PolarisProfilePostsQuery
     */
    fun getUserIdFromUsername(username: String): String? {
        val cleanName = cleanInstagramUsername(username)
        if (cleanName.isBlank()) return null
        if (cleanName.all { it.isDigit() }) return cleanName

        val csrf = extractCsrfToken() ?: ""

        try {
            val variables = JSONObject().apply {
                put("data", JSONObject().apply {
                    put("count", 12)
                    put("include_reel_media_seen_timestamp", true)
                    put("include_relationship_info", true)
                    put("latest_besties_reel_media", true)
                    put("latest_reel_media", true)
                })
                put("username", cleanName)
                put("__relay_internal__pv__PolarisMultiCaptionCarouselEnabledrelayprovider", true)
                put("__relay_internal__pv__PolarisShortDramaEnabledrelayprovider", false)
                put("__relay_internal__pv__PolarisReelsRecoDebugOverlayEnabledrelayprovider", false)
            }.toString()

            val formBody = FormBody.Builder()
                .add("variables", variables)
                .add("doc_id", DOC_ID_PROFILE_POSTS)
                .build()

            val headers = Headers.Builder()
                .add("User-Agent", userAgent)
                .add("Cookie", cookie)
                .add("Accept", "*/*")
                .add("Accept-Language", "vi-VN,vi;q=0.9,ja-JP;q=0.8,ja;q=0.7,en-JP;q=0.6,en;q=0.5")
                .add("Content-Type", "application/x-www-form-urlencoded")
                .add("Origin", BASE_URL)
                .add("Referer", "$BASE_URL/$cleanName/")
                .add("X-CSRFToken", csrf)
                .add("X-IG-App-ID", APP_ID)
                .add("X-ASBD-ID", ASBD_ID)
                .add("X-FB-Friendly-Name", "PolarisProfilePostsQuery")
                .add("Sec-Fetch-Dest", "empty")
                .add("Sec-Fetch-Mode", "cors")
                .add("Sec-Fetch-Site", "same-origin")
                .add("sec-ch-ua-mobile", "?1")
                .add("sec-ch-ua-platform", "\"iOS\"")
                .add("x-ig-max-touch-points", "1")
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/graphql/query")
                .headers(headers)
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val m = Pattern.compile("\"owner\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"([0-9]+)\"").matcher(body)
                    if (m.find()) return m.group(1)
                    val m2 = Pattern.compile("\"id\"\\s*:\\s*\"([0-9]+)\"").matcher(body)
                    if (m2.find()) return m2.group(1)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Follow tài khoản Instagram bằng REST API v1 — giống hệt Python script hoạt động được
     * POST /api/v1/friendships/create/{userId}/
     * Không cần fb_dtsg, không cần lsd, không cần GraphQL
     */
    @Throws(Exception::class)
    fun followUser(targetUserId: String, fbDtsg: String? = null, lsd: String? = null): Boolean {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Cookie thiếu CSRF token")
        val cleanTargetId = targetUserId.trim()

        val body = FormBody.Builder()
            .add("container_module", "profile")
            .add("nav_chain", "PolarisFeedRoot:feedPage:8:topnav-link")
            .add("user_id", cleanTargetId)
            .build()

        val headers = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Origin", BASE_URL)
            .add("Referer", "$BASE_URL/")
            .add("Priority", "u=1, i")
            .add("Sec-Ch-Prefers-Color-Scheme", "dark")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", APP_ID)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-IG-WWW-Claim", IG_WWW_CLAIM)
            .add("X-Instagram-AJAX", AJAX_ROLLOUT)
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/friendships/create/$cleanTargetId/")
            .headers(headers)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (responseBody.contains("\"status\":\"ok\"")) {
                return true
            }
            if (responseBody.contains("\"spam\":true")) {
                throw IllegalStateException("Instagram chặn follow (Spam/Action blocked)")
            }
            if (responseBody.contains("\"require_login\":true") || responseBody.contains("login_required")) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }
            throw IllegalStateException("Instagram trả về lỗi (code ${response.code}): $responseBody")
        }
    }

    /**
     * Follow tài khoản theo username hoặc target_id
     */
    @Throws(Exception::class)
    fun followTarget(targetIdOrUsername: String, fbDtsg: String? = null, lsd: String? = null): Boolean {
        val clean = cleanInstagramUsername(targetIdOrUsername)
        if (clean.isBlank()) {
            throw IllegalStateException("Link hoặc ID đối tượng rỗng")
        }
        if (clean.all { it.isDigit() }) {
            return followUser(clean)
        }
        val userId = getUserIdFromUsername(clean)
        if (!userId.isNullOrBlank()) {
            return followUser(userId)
        }
        throw IllegalStateException("Không tìm được user ID của: $clean")
    }


    /**
     * Like bài viết qua REST API v1 — giống hệt Python script hoạt động được
     * POST /api/v1/web/likes/{mediaId}/like/
     * Không cần fb_dtsg, không cần GraphQL
     */
    @Throws(Exception::class)
    fun likeMediaGraphQL(mediaId: String, fbDtsg: String? = null): Boolean {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Không có CSRF token")

        val headers = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Origin", BASE_URL)
            .add("Referer", "$BASE_URL/")
            .add("Priority", "u=1, i")
            .add("Sec-Ch-Prefers-Color-Scheme", "dark")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", APP_ID)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-IG-WWW-Claim", IG_WWW_CLAIM)
            .add("X-Instagram-AJAX", AJAX_ROLLOUT)
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/web/likes/$mediaId/like/")
            .headers(headers)
            .post("".toRequestBody(null))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (responseBody.contains("\"status\":\"ok\"")) {
                return true
            }
            if (responseBody.contains("\"spam\":true")) {
                throw IllegalStateException("Instagram chặn Like (Spam/Action blocked)")
            }
            if (responseBody.contains("\"require_login\":true") || responseBody.contains("login_required")) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }
            throw IllegalStateException("Instagram trả về lỗi Like (code ${response.code}): $responseBody")
        }
    }


    /**
     * Like bài viết dựa trên ID hoặc URL bài viết
     */
    @Throws(Exception::class)
    fun likeTarget(targetMediaIdOrUrl: String, fbDtsg: String? = null): Boolean {
        val clean = targetMediaIdOrUrl.trim()
        if (clean.all { it.isDigit() }) {
            return likeMediaGraphQL(clean, fbDtsg)
        }

        val codeMatch = Regex("""/(?:p|reel|tv)/([a-zA-Z0-9_-]+)""").find(clean)
        val shortcode = codeMatch?.groupValues?.getOrNull(1) ?: clean

        try {
            val req = Request.Builder()
                .url("$BASE_URL/p/$shortcode/?__a=1&__d=dis")
                .headers(buildStandardHeaders())
                .get()
                .build()

            httpClient.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                val mediaIdMatch = Pattern.compile("\"id\"\\s*:\\s*\"([0-9]+)\"").matcher(body)
                if (mediaIdMatch.find()) {
                    val mediaId = mediaIdMatch.group(1)
                    return likeMediaGraphQL(mediaId, fbDtsg)
                }
            }
        } catch (_: Exception) {}

        return likeMediaGraphQL(clean, fbDtsg)
    }
}
