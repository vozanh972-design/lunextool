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
    var userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    proxyConfig: ProxyConfig? = null
) {
    companion object {
        const val BASE_URL = "https://www.instagram.com"
        const val API_BASE_URL = "https://i.instagram.com"

        // Dùng đúng giá trị từ Python script hoạt động được
        const val APP_ID = "1217981644879628"
        const val ASBD_ID = "359341"
        const val AJAX_ROLLOUT = "1047437269"
        const val HS = "20710.HYP:instagram_web_pkg.2.1...0"

        const val DOC_ID_FOLLOW_MUTATION = "26508036048874888"
        const val DOC_ID_LIKE_MUTATION = "9595477160535898"
        const val DOC_ID_PROFILE_POSTS = "28322872020710458"

        val PATTERN_CSRF: Pattern = Pattern.compile("csrftoken=([^;]+)")
        val PATTERN_USER_ID: Pattern = Pattern.compile("(?:userID|raw_user_id)\\\":\\\"([^\\\"]+)\\\"")
        val PATTERN_ACTOR_ID: Pattern = Pattern.compile("(?:actorID|actor_id)\\\":\\\"([^\\\"]+)\\\"")
        val PATTERN_DS_USER_ID: Pattern = Pattern.compile("ds_user_id=([^;]+)")
        val PATTERN_USERNAME: Pattern = Pattern.compile("\\\"username\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        val PATTERN_FULL_NAME: Pattern = Pattern.compile("\\\"full_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        val PATTERN_PROFILE_PIC: Pattern = Pattern.compile("\\\"profile_pic_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        val PATTERN_BIOGRAPHY: Pattern = Pattern.compile("\\\"biography\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        val PATTERN_FOLLOWERS: Pattern = Pattern.compile("(?:edge_followed_by|followed_by)\\\"\\s*:\\s*\\{\\s*\\\"count\\\"\\s*:\\s*(\\d+)")
        val PATTERN_FOLLOWING: Pattern = Pattern.compile("(?:edge_follow|follow)\\\"\\s*:\\s*\\{\\s*\\\"count\\\"\\s*:\\s*(\\d+)")
        val PATTERN_POSTS: Pattern = Pattern.compile("(?:edge_owner_to_timeline_media|media)\\\"\\s*:\\s*\\{\\s*\\\"count\\\"\\s*:\\s*(\\d+)")
        val PATTERN_DTSG: Pattern = Pattern.compile("(?:DTSGInitialData[^\"]*\"token\"|\"DTSGInitialData\"[^\"]*\"token\"|\"token\"\\s*:\\s*\"NA[^\"]+\")[^\"]*\"([^\"]+)\"")
        val PATTERN_DTSG_SIMPLE: Pattern = Pattern.compile("\"token\"\\s*:\\s*\"(NA[^\"]+)\"")
        val PATTERN_LSD: Pattern = Pattern.compile("\\\"LSD\\\"\\s*,\\s*\\[\\s*],\\s*\\{\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        val PATTERN_SPIN_R: Pattern = Pattern.compile("\"__spin_r\"\\s*:\\s*(\\d+)")
        val PATTERN_HS: Pattern = Pattern.compile("\"haste_session\"\\s*:\\s*\"([^\"]+)\"")

        fun unescapeUnicode(input: String): String {
            if (!input.contains("\\u")) return input
            val regex = Regex("""\\u([0-9a-fA-F]{4})""")
            return regex.replace(input) { matchResult ->
                try {
                    val hex = matchResult.groupValues[1]
                    hex.toInt(16).toChar().toString()
                } catch (_: Exception) {
                    matchResult.value
                }
            }
        }
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
        val actorId: String? = null,
        val username: String,
        val fullName: String,
        val profilePicUrl: String?,
        val csrfToken: String?,
        val fbDtsg: String?,
        val lsd: String?,
        val spinR: String? = null,
        val hs: String? = null,
        val biography: String = "",
        val followersCount: Int = 0,
        val followingCount: Int = 0,
        val postsCount: Int = 0
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

    fun extractDsUserId(): String? {
        val matcher = PATTERN_DS_USER_ID.matcher(cookie)
        if (matcher.find()) return matcher.group(1)
        return null
    }

    private fun buildStandardHeaders(csrfToken: String? = null, referer: String? = null, appId: String = APP_ID, friendlyName: String? = null, lsdToken: String? = null, rolloutAjax: String = AJAX_ROLLOUT): Headers {
        val csrf = csrfToken ?: extractCsrfToken() ?: ""
        val ref = referer ?: "$BASE_URL/"
        val builder = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi-VN,vi;q=0.9,fr-FR;q=0.8,fr;q=0.7,en-US;q=0.6,en;q=0.5")
            .add("Origin", BASE_URL)
            .add("Referer", ref)
            .add("Priority", "u=1, i")
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", appId)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-Instagram-AJAX", rolloutAjax)
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("sec-ch-prefers-color-scheme", "dark")
            .add("sec-ch-ua", "\"Chromium\";v=\"152\", \"Not?A_Brand\";v=\"24\", \"Google Chrome\";v=\"152\"")
            .add("sec-ch-ua-mobile", "?1")
            .add("sec-ch-ua-model", "\"iPhone\"")
            .add("sec-ch-ua-platform", "\"iOS\"")
            .add("sec-ch-ua-platform-version", "\"18.5\"")
            .add("x-ig-max-touch-points", "1")

        if (!friendlyName.isNullOrBlank()) {
            builder.add("X-FB-Friendly-Name", friendlyName)
        }
        if (!lsdToken.isNullOrBlank()) {
            builder.add("X-FB-LSD", lsdToken)
        }

        return builder.build()
    }

    /**
     * Lấy thông tin tài khoản & trích xuất tokens (fb_dtsg, lsd, actorID, __spin_r, __hs)
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
            if (response.code in listOf(401, 403) || body.contains("login_required")) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }

            val uid = PATTERN_USER_ID.matcher(body).let { if (it.find()) it.group(1) else extractDsUserId() ?: "" }
            val actorId = PATTERN_ACTOR_ID.matcher(body).let { if (it.find()) it.group(1) else uid }
            val uname = PATTERN_USERNAME.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1)) else "" }
            val fname = PATTERN_FULL_NAME.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1)) else "" }
            val pic = PATTERN_PROFILE_PIC.matcher(body).let {
                if (it.find()) it.group(1).replace("\\u0026", "&").replace("\\/", "/") else null
            }
            val bio = PATTERN_BIOGRAPHY.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1)) else "" }
            val followers = PATTERN_FOLLOWERS.matcher(body).let { if (it.find()) it.group(1).toIntOrNull() ?: 0 else 0 }
            val following = PATTERN_FOLLOWING.matcher(body).let { if (it.find()) it.group(1).toIntOrNull() ?: 0 else 0 }
            val posts = PATTERN_POSTS.matcher(body).let { if (it.find()) it.group(1).toIntOrNull() ?: 0 else 0 }

            var dtsg = PATTERN_DTSG.matcher(body).let { if (it.find()) it.group(1) else null }
            if (dtsg == null) {
                dtsg = PATTERN_DTSG_SIMPLE.matcher(body).let { if (it.find()) it.group(1) else null }
            }
            val lsd = PATTERN_LSD.matcher(body).let { if (it.find()) it.group(1) else null }
            val spinR = PATTERN_SPIN_R.matcher(body).let { if (it.find()) it.group(1) else null }
            val hs = PATTERN_HS.matcher(body).let { if (it.find()) it.group(1) else null }

            return UserProfile(
                userId = uid,
                actorId = actorId,
                username = uname,
                fullName = fname,
                profilePicUrl = pic,
                csrfToken = extractCsrfToken(),
                fbDtsg = dtsg,
                lsd = lsd,
                spinR = spinR,
                hs = hs,
                biography = bio,
                followersCount = followers,
                followingCount = following,
                postsCount = posts
            )
        }
    }

    /**
     * Lấy đầy đủ thông tin chi tiết tài khoản (tên, tiểu sử, số follow, bài viết, avatar) qua Web Profile API
     */
    @Throws(Exception::class)
    fun fetchAccountDetails(targetUsername: String? = null): UserProfile {
        // Nếu không truyền username, trước tiên lấy cơ bản từ fetchUserInfo
        val baseInfo = try {
            fetchUserInfo()
        } catch (e: Exception) {
            null
        }

        val targetUser = targetUsername?.takeIf { it.isNotBlank() } 
            ?: baseInfo?.username?.takeIf { it.isNotBlank() }
            ?: return baseInfo ?: throw IllegalStateException("Không xác định được username")

        val cleanUser = cleanInstagramUsername(targetUser)
        val csrf = extractCsrfToken() ?: ""

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/users/web_profile_info/?username=$cleanUser")
            .headers(buildStandardHeaders(csrfToken = csrf, referer = "$BASE_URL/$cleanUser/"))
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val userData = json.optJSONObject("data")?.optJSONObject("user")
                    if (userData != null) {
                        val uid = userData.optString("id", baseInfo?.userId ?: "")
                        val uname = unescapeUnicode(userData.optString("username", cleanUser))
                        val fname = unescapeUnicode(userData.optString("full_name", ""))
                        val bio = unescapeUnicode(userData.optString("biography", ""))
                        val picUrl = userData.optString("profile_pic_url_hd", userData.optString("profile_pic_url", ""))
                        val followers = userData.optJSONObject("edge_followed_by")?.optInt("count") ?: 0
                        val following = userData.optJSONObject("edge_follow")?.optInt("count") ?: 0
                        val posts = userData.optJSONObject("edge_owner_to_timeline_media")?.optInt("count") ?: 0

                        return UserProfile(
                            userId = uid,
                            actorId = baseInfo?.actorId ?: uid,
                            username = uname,
                            fullName = fname,
                            profilePicUrl = picUrl.takeIf { it.isNotBlank() } ?: baseInfo?.profilePicUrl,
                            csrfToken = baseInfo?.csrfToken ?: csrf,
                            fbDtsg = baseInfo?.fbDtsg,
                            lsd = baseInfo?.lsd,
                            spinR = baseInfo?.spinR,
                            hs = baseInfo?.hs,
                            biography = bio,
                            followersCount = followers,
                            followingCount = following,
                            postsCount = posts
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return baseInfo ?: throw IllegalStateException("Không thể lấy thông tin tài khoản $cleanUser")
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

            val headers = buildStandardHeaders(
                csrfToken = csrf,
                referer = "$BASE_URL/$cleanName/",
                friendlyName = "PolarisProfilePostsQuery"
            )

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
     * Follow tài khoản Instagram chuẩn xác 100% GraphQL usePolarisFollowMutation (Đã xác thực test thực tế thành công)
     */
    @Throws(Exception::class)
    fun followUser(targetUserId: String, fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Cookie thiếu CSRF token")
        val cleanTargetId = targetUserId.trim()
        val av = if (!actorId.isNullOrBlank()) actorId else (extractDsUserId() ?: "0")

        val variables = JSONObject().apply {
            put("target_user_id", cleanTargetId)
            put("container_module", "profile")
            put("nav_chain", "PolarisProfilePostsTabRoot:profilePage:1:via_cold_start")
        }.toString()

        val formBuilder = FormBody.Builder()
            .add("av", av)
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "4")
            .add("__hs", HS)
            .add("dpr", "3")
            .add("__ccg", "GOOD")
            .add("__rev", AJAX_ROLLOUT)
            .add("__s", "735m3e:f1m8w5:glfkmo")
            .add("__hsi", "7685253187564392241")

        if (!fbDtsg.isNullOrBlank()) {
            formBuilder.add("fb_dtsg", fbDtsg)
            formBuilder.add("jazoest", "26190")
        }
        if (!lsd.isNullOrBlank()) {
            formBuilder.add("lsd", lsd)
        }

        val body = formBuilder
            .add("__spin_r", AJAX_ROLLOUT)
            .add("__spin_b", "trunk")
            .add("__spin_t", "1789362446")
            .add("__crn", "comet.igweb.PolarisFeedRoute")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
            .add("server_timestamps", "true")
            .add("variables", variables)
            .add("doc_id", DOC_ID_FOLLOW_MUTATION)
            .build()

        val headers = buildStandardHeaders(
            csrfToken = csrf,
            friendlyName = "usePolarisFollowMutation",
            lsdToken = lsd
        )

        val request = Request.Builder()
            .url("$BASE_URL/api/graphql")
            .headers(headers)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (responseBody.contains("\"xdt_create_friendship\"") || responseBody.contains("\"following\":true") || responseBody.contains("\"outgoing_request\":true") || responseBody.contains("\"status\":\"ok\"")) {
                return true
            }
            if (responseBody.contains("\"spam\":true") || responseBody.contains("feedback_required")) {
                throw IllegalStateException("Instagram chặn follow (Spam/Action blocked)")
            }
            if (responseBody.contains("\"require_login\":true") || responseBody.contains("login_required") || responseBody.contains("checkpoint_required")) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }
            throw IllegalStateException("Instagram trả về lỗi: $responseBody")
        }
    }

    /**
     * Follow tài khoản theo username hoặc target_id
     */
    @Throws(Exception::class)
    fun followTarget(targetIdOrUsername: String, fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val clean = cleanInstagramUsername(targetIdOrUsername)
        if (clean.isBlank()) {
            throw IllegalStateException("Link hoặc ID đối tượng rỗng")
        }
        if (clean.all { it.isDigit() }) {
            return followUser(clean, fbDtsg, lsd, actorId)
        }
        val userId = getUserIdFromUsername(clean)
        if (!userId.isNullOrBlank()) {
            return followUser(userId, fbDtsg, lsd, actorId)
        }
        // Thử follow trực tiếp bằng clean identifier
        return followUser(clean, fbDtsg, lsd, actorId)
    }




    /**
     * Like bài viết qua Instagram GraphQL usePolarisLikeMediaLikeMutation (DOC_ID: 9595477160535898)
     */
    @Throws(Exception::class)
    fun likeMediaGraphQL(mediaId: String, fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Không có CSRF token")
        val av = if (!actorId.isNullOrBlank()) actorId else (extractDsUserId() ?: "0")

        val variables = JSONObject().apply {
            put("media_id", mediaId)
            put("container_module", "feed_timeline")
        }.toString()

        val formBuilder = FormBody.Builder()
            .add("av", av)
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "4")
            .add("__hs", HS)
            .add("dpr", "3")
            .add("__ccg", "GOOD")
            .add("__rev", AJAX_ROLLOUT)
            .add("__s", "735m3e:f1m8w5:glfkmo")
            .add("__hsi", "7685253187564392241")

        if (!fbDtsg.isNullOrBlank()) {
            formBuilder.add("fb_dtsg", fbDtsg)
            formBuilder.add("jazoest", "26190")
        }
        if (!lsd.isNullOrBlank()) {
            formBuilder.add("lsd", lsd)
        }

        val body = formBuilder
            .add("__spin_r", AJAX_ROLLOUT)
            .add("__spin_b", "trunk")
            .add("__spin_t", "1789362446")
            .add("__crn", "comet.igweb.PolarisFeedRoute")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisLikeMediaLikeMutation")
            .add("server_timestamps", "true")
            .add("variables", variables)
            .add("doc_id", DOC_ID_LIKE_MUTATION)
            .build()

        val headers = buildStandardHeaders(
            csrfToken = csrf,
            friendlyName = "usePolarisLikeMediaLikeMutation",
            lsdToken = lsd
        )

        val request = Request.Builder()
            .url("$BASE_URL/api/graphql")
            .headers(headers)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (responseBody.contains("\"viewer_has_liked\":true") || responseBody.contains("\"status\":\"ok\"") || responseBody.contains("\"is_final\":true")) {
                return true
            }
            if (responseBody.contains("\"spam\":true") || responseBody.contains("feedback_required")) {
                throw IllegalStateException("Instagram chặn Like (Spam/Action blocked)")
            }
            if (responseBody.contains("\"require_login\":true") || responseBody.contains("login_required") || responseBody.contains("checkpoint_required")) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }
            throw IllegalStateException("Instagram trả về lỗi Like: $responseBody")
        }
    }

    /**
     * Like bài viết dựa trên ID hoặc URL bài viết
     */
    @Throws(Exception::class)
    fun likeTarget(targetMediaIdOrUrl: String, fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val clean = targetMediaIdOrUrl.trim()
        if (clean.all { it.isDigit() }) {
            return likeMediaGraphQL(clean, fbDtsg, lsd, actorId)
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
                    return likeMediaGraphQL(mediaId, fbDtsg, lsd, actorId)
                }
            }
        } catch (_: Exception) {}

        return likeMediaGraphQL(clean, fbDtsg, lsd, actorId)
    }

    /**
     * Đổi ảnh đại diện (Avatar) tài khoản Instagram qua Web API
     * @param imageBytes Dữ liệu nhị phân của ảnh (JPEG/PNG)
     * @return URL của ảnh đại diện mới nếu thành công
     */
    @Throws(Exception::class)
    fun changeProfilePicture(imageBytes: ByteArray): String? {
        val csrf = extractCsrfToken() ?: throw IllegalStateException("Cookie thiếu CSRF token")
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "profile_pic",
                "profile_pic.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val headers = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .add("Origin", BASE_URL)
            .add("Referer", "$BASE_URL/accounts/edit/")
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", APP_ID)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-Instagram-AJAX", AJAX_ROLLOUT)
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/web/accounts/web_change_profile_picture/")
            .headers(headers)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful && response.code !in listOf(200, 201)) {
                if (responseBody.contains("login_required") || responseBody.contains("checkpoint_required")) {
                    throw IllegalStateException("Cookie DIE hoặc yêu cầu checkpoint đăng nhập lại")
                }
                throw IllegalStateException("Lỗi đổi ảnh (${response.code}): $responseBody")
            }

            try {
                val json = JSONObject(responseBody)
                if (json.optString("status") == "ok" || json.optBoolean("has_profile_pic", false)) {
                    return json.optString("profile_pic_url", null)
                }
                val message = json.optString("message", responseBody)
                throw IllegalStateException("Đổi avatar thất bại: $message")
            } catch (e: Exception) {
                if (responseBody.contains("\"status\":\"ok\"")) {
                    return null
                }
                throw e
            }
        }
    }

    /**
     * Đổi ảnh đại diện qua File ảnh
     */
    @Throws(Exception::class)
    fun changeProfilePicture(imageFile: File): String? {
        if (!imageFile.exists() || !imageFile.canRead()) {
            throw IllegalArgumentException("File ảnh không tồn tại hoặc không thể đọc: ${imageFile.absolutePath}")
        }
        return changeProfilePicture(imageFile.readBytes())
    }
}


