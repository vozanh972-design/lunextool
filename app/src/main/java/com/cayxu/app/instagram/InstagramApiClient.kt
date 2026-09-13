package com.cayxu.app.instagram

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
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
    var userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    proxyConfig: ProxyConfig? = null
) {
    companion object {
        const val BASE_URL = "https://www.instagram.com"
        const val API_BASE_URL = "https://i.instagram.com"

        const val APP_ID = "936619743392459"
        const val ASBD_ID = "198387"
        const val AJAX_ROLLOUT = "1006309104"

        const val DOC_ID_LIKE_MUTATION = "9595477160535898"
        const val DOC_ID_PROFILE_QUERY = "24644030398570558"

        val PATTERN_CSRF: Pattern = Pattern.compile("csrftoken=([^;]+)")
        val PATTERN_USER_ID: Pattern = Pattern.compile("userID\":\"([^\"]+)\"")
        val PATTERN_USERNAME: Pattern = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_FULL_NAME: Pattern = Pattern.compile("\"full_name\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_PROFILE_PIC: Pattern = Pattern.compile("\"profile_pic_url\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_DTSG: Pattern = Pattern.compile("DTSGInitialData[^\"]*\"token\":\"([^\"]+)\"")
        val PATTERN_LSD: Pattern = Pattern.compile("\"LSD\"\\s*,\\s*\\[\\s*],\\s*\\{\"token\"\\s*:\\s*\"([^\"]+)\"")
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
            .followRedirects(true)
            .followSslRedirects(true)

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

    private fun buildStandardHeaders(csrfToken: String? = null): Headers {
        val csrf = csrfToken ?: extractCsrfToken() ?: ""
        return Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
            .add("Origin", BASE_URL)
            .add("Referer", "$BASE_URL/")
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", APP_ID)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-Instagram-AJAX", AJAX_ROLLOUT)
            .add("X-Requested-With", "XMLHttpRequest")
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
     * Tra cứu user ID từ username với nhiều tầng fallback (API, HTML, TopSearch)
     */
    fun getUserIdFromUsername(username: String): String? {
        val cleanName = cleanInstagramUsername(username)
        if (cleanName.isBlank()) return null
        if (cleanName.all { it.isDigit() }) return cleanName

        // Cách 1: Qua web_profile_info
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/users/web_profile_info/?username=$cleanName")
                .headers(buildStandardHeaders())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val id = json.optJSONObject("data")?.optJSONObject("user")?.optString("id")
                    if (!id.isNullOrBlank()) return id
                }
            }
        } catch (_: Exception) {}

        // Cách 2: Qua web search topsearch API
        try {
            val request = Request.Builder()
                .url("$BASE_URL/web/search/topsearch/?context=blended&query=$cleanName&rank_token=0.5")
                .headers(buildStandardHeaders())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val usersArr = json.optJSONArray("users")
                    if (usersArr != null && usersArr.length() > 0) {
                        for (i in 0 until usersArr.length()) {
                            val u = usersArr.getJSONObject(i).optJSONObject("user")
                            val uName = u?.optString("username")
                            if (uName.equals(cleanName, ignoreCase = true)) {
                                val pk = u.optString("pk").ifBlank { u.optString("id") }
                                if (pk.isNotBlank()) return pk
                            }
                        }
                        // Lấy kết quả đầu tiên nếu khớp
                        val firstUser = usersArr.getJSONObject(0).optJSONObject("user")
                        val pk = firstUser?.optString("pk")?.ifBlank { firstUser.optString("id") }
                        if (!pk.isNullOrBlank()) return pk
                    }
                }
            }
        } catch (_: Exception) {}

        // Cách 3: Đọc trực tiếp HTML profile page
        try {
            val request = Request.Builder()
                .url("$BASE_URL/$cleanName/")
                .headers(buildStandardHeaders())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val m1 = Pattern.compile("\"id\":\"(\\d+)\"").matcher(body)
                    if (m1.find()) return m1.group(1)
                    val m2 = Pattern.compile("\"user_id\":\"(\\d+)\"").matcher(body)
                    if (m2.find()) return m2.group(1)
                    val m3 = Pattern.compile("\"profile_id\":\"(\\d+)\"").matcher(body)
                    if (m3.find()) return m3.group(1)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Follow tài khoản Instagram bằng User ID
     */
    @Throws(Exception::class)
    fun followUser(targetUserId: String): Boolean {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Không có CSRF token trong Cookie")
        val cleanTargetId = targetUserId.trim()

        val requestBody = FormBody.Builder()
            .add("user_id", cleanTargetId)
            .build()

        // 1. Thử endpoint Web API chuẩn của Instagram
        val webUrl = "$BASE_URL/api/v1/web/friendships/$cleanTargetId/follow/"
        val webRequest = Request.Builder()
            .url(webUrl)
            .headers(buildStandardHeaders(csrf))
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(webRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && (body.contains("\"status\":\"ok\"") || body.contains("\"result\":\"following\"") || body.contains("\"result\":\"requested\""))) {
                    return true
                }
            }
        } catch (_: Exception) {}

        // 2. Thử endpoint i.instagram.com
        val apiUrl = "$API_BASE_URL/api/v1/web/friendships/$cleanTargetId/follow/"
        val apiRequest = Request.Builder()
            .url(apiUrl)
            .headers(buildStandardHeaders(csrf))
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(apiRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && (body.contains("\"status\":\"ok\"") || body.contains("\"result\":\"following\"") || body.contains("\"result\":\"requested\""))) {
                    return true
                }
            }
        } catch (_: Exception) {}

        // 3. Thử friendships create
        val createUrl = "$BASE_URL/api/v1/friendships/create/$cleanTargetId/"
        val createReq = Request.Builder()
            .url(createUrl)
            .headers(buildStandardHeaders(csrf))
            .post(requestBody)
            .build()

        httpClient.newCall(createReq).execute().use { response ->
            val body = response.body?.string() ?: ""
            return response.isSuccessful && (body.contains("\"status\":\"ok\"") || body.contains("\"result\":\"following\"") || body.contains("\"result\":\"requested\""))
        }
    }

    /**
     * Follow tài khoản theo username hoặc target_id
     */
    @Throws(Exception::class)
    fun followTarget(targetIdOrUsername: String): Boolean {
        val clean = cleanInstagramUsername(targetIdOrUsername)
        if (clean.all { it.isDigit() }) {
            return followUser(clean)
        }
        val userId = getUserIdFromUsername(clean)
        if (!userId.isNullOrBlank()) {
            return followUser(userId)
        }
        return followUser(clean)
    }

    /**
     * Like bài viết qua REST API
     */
    @Throws(Exception::class)
    fun likeMedia(mediaId: String): Boolean {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Không có CSRF token")
        val cleanMediaId = mediaId.trim()
        val url = "$BASE_URL/api/v1/web/likes/$cleanMediaId/like/"
        val requestBody = FormBody.Builder().build()

        val request = Request.Builder()
            .url(url)
            .headers(buildStandardHeaders(csrf))
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            return response.isSuccessful && body.contains("\"status\":\"ok\"")
        }
    }

    /**
     * Like bài viết qua Instagram GraphQL Mutation
     */
    @Throws(Exception::class)
    fun likeMediaGraphQL(mediaId: String, fbDtsg: String?): Boolean {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Không có CSRF token")
        val variables = JSONObject().apply {
            put("media_id", mediaId)
            put("container_module", "feed_timeline")
        }.toString()

        val formBody = FormBody.Builder()
            .add("fb_dtsg", fbDtsg ?: "")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisLikeMediaLikeMutation")
            .add("variables", variables)
            .add("doc_id", DOC_ID_LIKE_MUTATION)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/graphql/query")
            .headers(buildStandardHeaders(csrf))
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && (body.contains("\"status\":\"ok\"") || body.contains("\"is_final\":true"))) {
                return true
            }
        }

        // Fallback to REST like
        return likeMedia(mediaId)
    }

    /**
     * Like bài viết dựa trên ID hoặc URL bài viết
     */
    @Throws(Exception::class)
    fun likeTarget(targetMediaIdOrUrl: String, fbDtsg: String? = null): Boolean {
        val clean = targetMediaIdOrUrl.trim()
        if (clean.all { it.isDigit() }) {
            return if (!fbDtsg.isNullOrBlank()) likeMediaGraphQL(clean, fbDtsg) else likeMedia(clean)
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
                    return if (!fbDtsg.isNullOrBlank()) likeMediaGraphQL(mediaId, fbDtsg) else likeMedia(mediaId)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return likeMedia(clean)
    }
}
