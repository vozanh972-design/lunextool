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

        // Web App ID chuẩn của Instagram Web
        const val APP_ID = "936619743392459"
        const val ASBD_ID = "359341"
        const val AJAX_ROLLOUT = "1047437269"
        const val HS = "20710.HYP:instagram_web_pkg.2.1...0"

        const val DOC_ID_FOLLOW_MUTATION = "26508036048874888"
        const val DOC_ID_LIKE_MUTATION = "9595477160535898"
        const val DOC_ID_LIKE_MUTATION_POLARIS = "27358573637160660"
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

        fun calculateJazoest(token: String?): String {
            if (token.isNullOrBlank()) return "26016"
            var sum = 0
            for (c in token) {
                sum += c.code
            }
            return "2$sum"
        }

        fun generateSessionS(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            fun randPart(len: Int) = (1..len).map { chars.random() }.joinToString("")
            return "${randPart(6)}:${randPart(6)}:${randPart(6)}"
        }

        fun generateHsi(): String {
            val randomPart = (1000000000000000L..9999999999999999L).random()
            return "768$randomPart"
        }

        fun generateMobileSafariUA(seed: String = ""): String {
            val iosVersions = listOf(
                "18_5" to "15E148",
                "18_4_1" to "15E148",
                "18_3_2" to "15E148",
                "17_7_2" to "15E148",
                "18_2_1" to "15E148"
            )
            val index = if (seed.isNotBlank()) Math.abs(seed.hashCode()) % iosVersions.size else (0 until iosVersions.size).random()
            val (version, build) = iosVersions[index]
            return "Mozilla/5.0 (iPhone; CPU iPhone OS $version like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/${version.replace("_", ".")} Mobile/$build Safari/604.1"
        }

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

        fun shortcodeToMediaId(code: String): String {
            val cleanCode = code.trim()
            if (cleanCode.all { it.isDigit() }) return cleanCode
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            var id = java.math.BigInteger.ZERO
            val base = java.math.BigInteger.valueOf(64)
            for (c in cleanCode) {
                val index = alphabet.indexOf(c)
                if (index == -1) return ""
                id = id.multiply(base).add(java.math.BigInteger.valueOf(index.toLong()))
            }
            return id.toString()
        }

        fun extractShortcode(input: String): String {
            val clean = input.trim()
            val match = Regex("""/(?:p|reel|reels|tv)/([a-zA-Z0-9_-]+)""").find(clean)
            if (match != null) {
                return match.groupValues[1]
            }
            val cleanNoQuery = clean.substringBefore("?").substringBefore("#").trim().trimEnd('/')
            return cleanNoQuery.substringAfterLast("/")
        }

        fun parseProxy(proxyStr: String?): ProxyConfig? {
            if (proxyStr.isNullOrBlank()) return null
            val parts = proxyStr.trim().split(":")
            if (parts.size < 2) return null
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: return null
            val user = if (parts.size >= 4) parts[2].trim() else null
            val pass = if (parts.size >= 4) parts[3].trim() else null
            return ProxyConfig(host = host, port = port, username = user, password = pass)
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

    // Dynamic session fields per account instance
    var activeCsrfToken: String = ""
    var activeUserId: String = ""
    var activeActorId: String = ""
    var activeFbDtsg: String = ""
    var activeLsd: String = ""
    var activeHs: String = HS
    var activeHsi: String = ""
    var activeRev: String = AJAX_ROLLOUT
    var activeSpinR: String = AJAX_ROLLOUT
    var activeS: String = ""

    private var httpClient: OkHttpClient

    init {
        if (userAgent.isBlank()) {
            userAgent = generateMobileSafariUA(cookie)
        }
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

    private fun buildDocumentHeaders(referer: String? = null): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .add("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .add("Sec-Fetch-Dest", "document")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-Site", if (referer != null) "same-origin" else "none")
            .add("Sec-Fetch-User", "?1")
            .add("Upgrade-Insecure-Requests", "1")

        if (!referer.isNullOrBlank()) {
            builder.add("Referer", referer)
        }
        return builder.build()
    }

    private fun buildStandardHeaders(
        csrfToken: String? = null,
        referer: String? = null,
        appId: String = APP_ID,
        friendlyName: String? = null,
        lsdToken: String? = null
    ): Headers {
        val csrf = csrfToken ?: activeCsrfToken.ifBlank { extractCsrfToken() ?: "" }
        val ref = referer ?: "$BASE_URL/"
        val builder = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .add("Origin", BASE_URL)
            .add("Referer", ref)
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", appId)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-IG-WWW-Claim", "0")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")

        if (!friendlyName.isNullOrBlank()) {
            builder.add("X-FB-Friendly-Name", friendlyName)
        }
        if (!lsdToken.isNullOrBlank()) {
            builder.add("X-FB-LSD", lsdToken)
        }

        return builder.build()
    }

    /**
     * Lấy thông tin tài khoản & trích xuất tokens (fb_dtsg, lsd, actorID, __spin_r, __hs) động cho từng acc
     */
    @Throws(Exception::class)
    fun fetchUserInfo(): UserProfile {
        activeCsrfToken = extractCsrfToken() ?: ""
        activeUserId = extractDsUserId() ?: ""
        activeActorId = activeUserId

        val request = Request.Builder()
            .url(BASE_URL)
            .headers(buildDocumentHeaders())
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.code in listOf(401, 403) || body.contains("login_required")) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }

            val uid = PATTERN_USER_ID.matcher(body).let { if (it.find()) it.group(1).orEmpty() else extractDsUserId() ?: "" }
            val actorId = PATTERN_ACTOR_ID.matcher(body).let { if (it.find()) it.group(1).orEmpty() else uid }
            val uname = PATTERN_USERNAME.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1).orEmpty()) else "" }
            val fname = PATTERN_FULL_NAME.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1).orEmpty()) else "" }
            val pic = PATTERN_PROFILE_PIC.matcher(body).let {
                if (it.find()) (it.group(1) ?: "").replace("\\u0026", "&").replace("\\/", "/") else null
            }
            val bio = PATTERN_BIOGRAPHY.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1).orEmpty()) else "" }
            val followers = PATTERN_FOLLOWERS.matcher(body).let { if (it.find()) it.group(1)?.toIntOrNull() ?: 0 else 0 }
            val following = PATTERN_FOLLOWING.matcher(body).let { if (it.find()) it.group(1)?.toIntOrNull() ?: 0 else 0 }
            val posts = PATTERN_POSTS.matcher(body).let { if (it.find()) it.group(1)?.toIntOrNull() ?: 0 else 0 }

            var dtsg = PATTERN_DTSG.matcher(body).let { if (it.find()) it.group(1) else null }
            if (dtsg == null) {
                dtsg = PATTERN_DTSG_SIMPLE.matcher(body).let { if (it.find()) it.group(1) else null }
            }
            val lsd = PATTERN_LSD.matcher(body).let { if (it.find()) it.group(1) else null }
            val spinR = PATTERN_SPIN_R.matcher(body).let { if (it.find()) it.group(1) else null }
            val hs = PATTERN_HS.matcher(body).let { if (it.find()) it.group(1) else null }

            // Lưu vào session fields của instance tài khoản này
            if (uid.isNotBlank()) activeUserId = uid
            if (actorId.isNotBlank()) activeActorId = actorId
            if (!dtsg.isNullOrBlank()) activeFbDtsg = dtsg
            if (!lsd.isNullOrBlank()) activeLsd = lsd
            if (!spinR.isNullOrBlank()) {
                activeSpinR = spinR
                activeRev = spinR
            }
            if (!hs.isNullOrBlank()) activeHs = hs
            activeS = generateSessionS()
            activeHsi = generateHsi()

            return UserProfile(
                userId = uid,
                actorId = actorId,
                username = uname,
                fullName = fname,
                profilePicUrl = pic,
                csrfToken = activeCsrfToken,
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
     * Kiểm tra trạng thái Live của tài khoản (nhẹ, nhanh, không tốn quota follow/like).
     * Trả về true nếu Cookie/Tài khoản đang Live.
     * Trả về false nếu Cookie DIE, checkpoint, login_required hoặc tài khoản bị khóa.
     */
    fun checkAccountLive(): Boolean {
        return try {
            val request = Request.Builder()
                .url(BASE_URL)
                .headers(buildDocumentHeaders())
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code in listOf(401, 403, 301, 302, 303, 307, 308)) {
                    return false
                }
                val body = response.body?.string() ?: ""
                if (body.contains("login_required") || body.contains("checkpoint_required") || body.contains("login-form")) {
                    return false
                }
                val uid = PATTERN_USER_ID.matcher(body).let { if (it.find()) it.group(1).orEmpty() else extractDsUserId() ?: "" }
                uid.isNotBlank()
            }
        } catch (_: Exception) {
            true // Lỗi mạng đơn thuần không kết luận là Die
        }
    }

    /**
     * Lấy đầy đủ thông tin chi tiết tài khoản qua trang Web cá nhân (không gọi REST API)
     */
    @Throws(Exception::class)
    fun fetchAccountDetails(targetUsername: String? = null): UserProfile {
        val baseInfo = try {
            fetchUserInfo()
        } catch (e: Exception) {
            null
        }

        val targetUser = targetUsername?.takeIf { it.isNotBlank() } 
            ?: baseInfo?.username?.takeIf { it.isNotBlank() }
            ?: return baseInfo ?: throw IllegalStateException("Không xác định được username")

        val cleanUser = cleanInstagramUsername(targetUser)
        val csrf = activeCsrfToken.ifBlank { extractCsrfToken() ?: "" }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/$cleanUser/")
                .headers(buildDocumentHeaders(referer = "$BASE_URL/"))
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    
                    val uid = PATTERN_USER_ID.matcher(body).let { if (it.find()) it.group(1).orEmpty() else baseInfo?.userId ?: "" }
                    val fname = PATTERN_FULL_NAME.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1).orEmpty()) else baseInfo?.fullName ?: "" }
                    val pic = PATTERN_PROFILE_PIC.matcher(body).let {
                        if (it.find()) (it.group(1) ?: "").replace("\\u0026", "&").replace("\\/", "/") else baseInfo?.profilePicUrl
                    }
                    val bio = PATTERN_BIOGRAPHY.matcher(body).let { if (it.find()) unescapeUnicode(it.group(1).orEmpty()) else baseInfo?.biography ?: "" }
                    val followers = PATTERN_FOLLOWERS.matcher(body).let { if (it.find()) it.group(1)?.toIntOrNull() ?: (baseInfo?.followersCount ?: 0) else (baseInfo?.followersCount ?: 0) }
                    val following = PATTERN_FOLLOWING.matcher(body).let { if (it.find()) it.group(1)?.toIntOrNull() ?: (baseInfo?.followingCount ?: 0) else (baseInfo?.followingCount ?: 0) }
                    val posts = PATTERN_POSTS.matcher(body).let { if (it.find()) it.group(1)?.toIntOrNull() ?: (baseInfo?.postsCount ?: 0) else (baseInfo?.postsCount ?: 0) }

                    return UserProfile(
                        userId = uid,
                        actorId = baseInfo?.actorId ?: uid,
                        username = cleanUser,
                        fullName = fname,
                        profilePicUrl = pic,
                        csrfToken = baseInfo?.csrfToken ?: csrf,
                        fbDtsg = baseInfo?.fbDtsg ?: activeFbDtsg,
                        lsd = baseInfo?.lsd ?: activeLsd,
                        spinR = baseInfo?.spinR ?: activeSpinR,
                        hs = baseInfo?.hs ?: activeHs,
                        biography = bio,
                        followersCount = followers,
                        followingCount = following,
                        postsCount = posts
                    )
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
     * Tra cứu user ID từ URL hoặc username bằng cách mở trang cá nhân Instagram Web
     */
    fun getUserIdFromUsername(username: String): String? {
        val cleanName = cleanInstagramUsername(username)
        if (cleanName.isBlank()) return null
        if (cleanName.all { it.isDigit() }) return cleanName

        val csrf = activeCsrfToken.ifBlank { extractCsrfToken() ?: "" }

        // Truy cập trực tiếp link trang cá nhân Instagram Web (không gọi REST API)
        try {
            val targetUrl = if (username.startsWith("http://") || username.startsWith("https://")) {
                username
            } else {
                "$BASE_URL/$cleanName/"
            }
            val request = Request.Builder()
                .url(targetUrl)
                .headers(buildDocumentHeaders(referer = "$BASE_URL/"))
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                
                val m0 = Pattern.compile("profilePage_([0-9]+)").matcher(body)
                if (m0.find()) return m0.group(1)

                val mMeta = Pattern.compile("instapp:owner_user_id[\"']?\\s*content=[\"']?([0-9]+)").matcher(body)
                if (mMeta.find()) return mMeta.group(1)

                val m1 = Pattern.compile("\"(?:profile_id|user_id|target_id)\"\\s*:\\s*\"?([0-9]+)\"?").matcher(body)
                if (m1.find()) return m1.group(1)

                val m2 = Pattern.compile("\"owner\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"([0-9]+)\"").matcher(body)
                if (m2.find()) return m2.group(1)
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Follow tài khoản Instagram chuẩn xác 100% bằng GraphQL usePolarisFollowUserMutation (Doc ID: 26508036048874888)
     */
    @Throws(Exception::class)
    fun followUser(targetUserId: String, fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val csrf = activeCsrfToken.ifBlank { extractCsrfToken() ?: throw IllegalStateException("Cookie thiếu CSRF token") }
        val cleanTargetId = targetUserId.trim()
        val av = if (!actorId.isNullOrBlank()) actorId else activeActorId.ifBlank { activeUserId.ifBlank { extractDsUserId() ?: "0" } }
        val currentFbDtsg = fbDtsg?.takeIf { it.isNotBlank() } ?: activeFbDtsg
        val currentLsd = lsd?.takeIf { it.isNotBlank() } ?: activeLsd
        val currentJazoest = calculateJazoest(currentFbDtsg)
        val currentHs = activeHs.ifBlank { HS }
        val currentRev = activeRev.ifBlank { activeSpinR.ifBlank { AJAX_ROLLOUT } }
        val currentSpinR = activeSpinR.ifBlank { AJAX_ROLLOUT }
        val currentSpinT = (System.currentTimeMillis() / 1000L).toString()
        val currentS = activeS.ifBlank { generateSessionS() }
        val currentHsi = activeHsi.ifBlank { generateHsi() }

        val variables = JSONObject().apply {
            put("target_user_id", cleanTargetId)
            put("container_module", "profile")
            put("nav_chain", "PolarisProfilePostsTabRoot:profilePage:1:via_cold_start")
        }.toString()

        val gqlFormBuilder = FormBody.Builder()
            .add("av", av)
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "1")
            .add("__hs", currentHs)
            .add("dpr", "3")
            .add("__ccg", "GOOD")
            .add("__rev", currentRev)
            .add("__s", currentS)
            .add("__hsi", currentHsi)

        if (currentFbDtsg.isNotBlank()) {
            gqlFormBuilder.add("fb_dtsg", currentFbDtsg)
            gqlFormBuilder.add("jazoest", currentJazoest)
        }
        if (currentLsd.isNotBlank()) {
            gqlFormBuilder.add("lsd", currentLsd)
        }

        val gqlBody = gqlFormBuilder
            .add("__spin_r", currentSpinR)
            .add("__spin_b", "trunk")
            .add("__spin_t", currentSpinT)
            .add("__crn", "comet.igweb.PolarisExploreRoute")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisFollowUserMutation")
            .add("server_timestamps", "true")
            .add("variables", variables)
            .add("doc_id", DOC_ID_FOLLOW_MUTATION)
            .build()

        val gqlHeaders = buildStandardHeaders(
            csrfToken = csrf,
            friendlyName = "usePolarisFollowUserMutation",
            lsdToken = currentLsd,
            referer = "$BASE_URL/"
        )

        val gqlRequest = Request.Builder()
            .url("$BASE_URL/api/graphql")
            .headers(gqlHeaders)
            .post(gqlBody)
            .build()

        httpClient.newCall(gqlRequest).execute().use { gqlResponse ->
            if (gqlResponse.code == 429) {
                throw IllegalStateException("Instagram giới hạn tạm thời (HTTP 429)")
            }
            if (gqlResponse.code in listOf(301, 302, 303, 307, 308)) {
                throw IllegalStateException("Cookie DIE hoặc hết phiên đăng nhập (Redirect ${gqlResponse.code})")
            }
            val gqlResponseBody = gqlResponse.body?.string() ?: ""
            if (gqlResponseBody.isBlank()) {
                throw IllegalStateException("Instagram không phản hồi (HTTP ${gqlResponse.code})")
            }

            if (gqlResponse.isSuccessful || gqlResponse.code == 200) {
                if (gqlResponseBody.contains("\"status\":\"ok\"") ||
                    gqlResponseBody.contains("\"following\":true") ||
                    gqlResponseBody.contains("\"outgoing_request\":true") ||
                    gqlResponseBody.contains("\"xdt_create_friendship\"") ||
                    (!gqlResponseBody.trimStart().startsWith("<") && !gqlResponseBody.contains("\"error\"") && !gqlResponseBody.contains("\"errors\""))
                ) {
                    return true
                }
            }

            if (gqlResponseBody.contains("\"spam\"", ignoreCase = true) || gqlResponseBody.contains("feedback_required", ignoreCase = true)) {
                throw IllegalStateException("Instagram chặn follow (Spam/Action blocked)")
            }
            if (gqlResponseBody.contains("\"require_login\"", ignoreCase = true) || gqlResponseBody.contains("login_required", ignoreCase = true) || gqlResponseBody.contains("checkpoint_required", ignoreCase = true)) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }
            if (gqlResponseBody.trimStart().startsWith("<")) {
                throw IllegalStateException("Instagram trả về trang HTML - Cookie hết hạn hoặc checkpoint")
            }

            var errDetail = ""
            try {
                val json = JSONObject(gqlResponseBody)
                val errArr = json.optJSONArray("errors")
                if (errArr != null && errArr.length() > 0) {
                    errDetail = errArr.getJSONObject(0).optString("message", "")
                } else if (json.has("message")) {
                    errDetail = json.optString("message", "")
                }
            } catch (_: Exception) {}

            if (errDetail.isNotBlank()) {
                throw IllegalStateException("Instagram từ chối follow: $errDetail")
            }
            throw IllegalStateException("Instagram từ chối follow đối tượng $cleanTargetId (HTTP ${gqlResponse.code})")
        }
    }

    /**
     * Follow tài khoản theo link, username hoặc target_id
     */
    @Throws(Exception::class)
    fun followTarget(targetIdOrUsername: String, fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val clean = cleanInstagramUsername(targetIdOrUsername)
        if (clean.isBlank()) {
            throw IllegalStateException("Link hoặc ID đối tượng rỗng")
        }
        val targetId = if (clean.all { it.isDigit() }) {
            clean
        } else {
            getUserIdFromUsername(targetIdOrUsername) ?: clean
        }
        return followUser(targetId, fbDtsg, lsd, actorId)
    }

    /**
     * Like bài viết qua Instagram GraphQL PolarisAPILikePostMutation (Doc ID: 27358573637160660)
     */
    /**
     * Like bài viết qua Instagram GraphQL chuẩn Web
     * Đầy đủ Header chuẩn trình duyệt Web Instagram (X-Instagram-AJAX, X-IG-App-ID, X-ASBD-ID, X-FB-Friendly-Name, X-FB-LSD)
     */
    @Throws(Exception::class)
    fun likeMediaGraphQL(mediaId: String, shortcode: String = "", fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val csrf = activeCsrfToken.ifBlank { extractCsrfToken() ?: throw IllegalStateException("Không có CSRF token trong cookie") }
        val effectiveActorId = if (!actorId.isNullOrBlank() && actorId != "0") {
            actorId
        } else {
            activeActorId.ifBlank { activeUserId.ifBlank { extractDsUserId() ?: "0" } }
        }
        val av = effectiveActorId.ifBlank { "0" }
        val currentFbDtsg = fbDtsg?.takeIf { it.isNotBlank() } ?: activeFbDtsg
        val currentLsd = lsd?.takeIf { it.isNotBlank() } ?: activeLsd
        val currentJazoest = calculateJazoest(currentFbDtsg)
        val currentHs = activeHs.ifBlank { HS }
        val currentRev = activeRev.ifBlank { activeSpinR.ifBlank { AJAX_ROLLOUT } }
        val currentSpinR = activeSpinR.ifBlank { AJAX_ROLLOUT }
        val currentSpinT = (System.currentTimeMillis() / 1000L).toString()
        val currentS = activeS.ifBlank { generateSessionS() }
        val currentHsi = activeHsi.ifBlank { generateHsi() }

        var lastErrorDetail = ""
        val ref = if (shortcode.isNotBlank()) "$BASE_URL/p/$shortcode/" else "$BASE_URL/"

        // 1. Thử qua PolarisAPILikePostMutation (Doc ID: 27358573637160660)
        try {
            val variables1 = JSONObject().apply {
                val inputObj = JSONObject().apply {
                    put("media_id", mediaId)
                    if (av.isNotBlank() && av != "0") {
                        put("actor_id", av)
                    }
                    put("client_mutation_id", "1")
                }
                put("input", inputObj)
            }.toString()

            val formBuilder1 = FormBody.Builder()
                .add("av", av)
                .add("__d", "www")
                .add("__user", "0")
                .add("__a", "1")
                .add("__req", "1")
                .add("__hs", currentHs)
                .add("dpr", "3")
                .add("__ccg", "GOOD")
                .add("__rev", currentRev)
                .add("__s", currentS)
                .add("__hsi", currentHsi)

            if (currentFbDtsg.isNotBlank()) {
                formBuilder1.add("fb_dtsg", currentFbDtsg)
                formBuilder1.add("jazoest", currentJazoest)
            }
            if (currentLsd.isNotBlank()) {
                formBuilder1.add("lsd", currentLsd)
            }

            val body1 = formBuilder1
                .add("__spin_r", currentSpinR)
                .add("__spin_b", "trunk")
                .add("__spin_t", currentSpinT)
                .add("__crn", "comet.igweb.PolarisExploreRoute")
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "PolarisAPILikePostMutation")
                .add("server_timestamps", "true")
                .add("variables", variables1)
                .add("doc_id", DOC_ID_LIKE_MUTATION_POLARIS)
                .build()

            val headers1 = Headers.Builder()
                .add("User-Agent", userAgent)
                .add("Cookie", cookie)
                .add("Accept", "*/*")
                .add("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                .add("Origin", BASE_URL)
                .add("Referer", ref)
                .add("X-CSRFToken", csrf)
                .add("X-IG-App-ID", APP_ID)
                .add("X-ASBD-ID", ASBD_ID)
                .add("X-Instagram-AJAX", currentRev)
                .add("X-FB-Friendly-Name", "PolarisAPILikePostMutation")
                .apply {
                    if (currentLsd.isNotBlank()) add("X-FB-LSD", currentLsd)
                }
                .add("Sec-Fetch-Dest", "empty")
                .add("Sec-Fetch-Mode", "cors")
                .add("Sec-Fetch-Site", "same-origin")
                .build()

            val request1 = Request.Builder()
                .url("$BASE_URL/api/graphql")
                .headers(headers1)
                .post(body1)
                .build()

            httpClient.newCall(request1).execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    throw IllegalStateException("Cookie DIE hoặc hết phiên đăng nhập (redirect ${response.code})")
                }
                val responseBody = response.body?.string() ?: ""
                if (response.code == 429) {
                    throw IllegalStateException("Instagram giới hạn tạm thời (HTTP 429)")
                }
                if (response.isSuccessful || response.code == 200) {
                    if (responseBody.contains("\"viewer_has_liked\":true") ||
                        responseBody.contains("\"status\":\"ok\"") ||
                        responseBody.contains("\"xdt_like_media\"") ||
                        responseBody.contains("\"is_final\":true") ||
                        (responseBody.contains("\"data\"") && !responseBody.contains("\"errors\"") && !responseBody.contains("\"error\""))
                    ) {
                        return true
                    }
                }
                if (responseBody.contains("\"spam\"", ignoreCase = true) || responseBody.contains("feedback_required", ignoreCase = true)) {
                    throw IllegalStateException("Instagram chặn Like (Spam/Action blocked)")
                }
                if (responseBody.contains("\"require_login\"", ignoreCase = true) || responseBody.contains("login_required", ignoreCase = true) || responseBody.contains("checkpoint_required", ignoreCase = true)) {
                    throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
                }
                if (responseBody.isNotBlank()) {
                    try {
                        val json = JSONObject(responseBody)
                        val errArr = json.optJSONArray("errors")
                        if (errArr != null && errArr.length() > 0) {
                            lastErrorDetail = errArr.getJSONObject(0).optString("message", "")
                        } else if (json.has("message")) {
                            lastErrorDetail = json.optString("message", "")
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("chặn") == true || e.message?.contains("DIE") == true || e.message?.contains("429") == true) throw e
            lastErrorDetail = e.message ?: ""
        }

        // 2. Fallback: Thử qua usePolarisLikeMediaLikeMutation (Doc ID: 9595477160535898)
        val variables2 = JSONObject().apply {
            put("media_id", mediaId)
            put("container_module", "feed_timeline")
        }.toString()

        val formBuilder2 = FormBody.Builder()
            .add("av", av)
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "1")
            .add("__hs", currentHs)
            .add("dpr", "3")
            .add("__ccg", "GOOD")
            .add("__rev", currentRev)
            .add("__s", currentS)
            .add("__hsi", currentHsi)

        if (currentFbDtsg.isNotBlank()) {
            formBuilder2.add("fb_dtsg", currentFbDtsg)
            formBuilder2.add("jazoest", currentJazoest)
        }
        if (currentLsd.isNotBlank()) {
            formBuilder2.add("lsd", currentLsd)
        }

        val body2 = formBuilder2
            .add("__spin_r", currentSpinR)
            .add("__spin_b", "trunk")
            .add("__spin_t", currentSpinT)
            .add("__crn", "comet.igweb.PolarisExploreRoute")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisLikeMediaLikeMutation")
            .add("server_timestamps", "true")
            .add("variables", variables2)
            .add("doc_id", DOC_ID_LIKE_MUTATION)
            .build()

        val headers2 = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Cookie", cookie)
            .add("Accept", "*/*")
            .add("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .add("Origin", BASE_URL)
            .add("Referer", ref)
            .add("X-CSRFToken", csrf)
            .add("X-IG-App-ID", APP_ID)
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-Instagram-AJAX", currentRev)
            .add("X-FB-Friendly-Name", "usePolarisLikeMediaLikeMutation")
            .apply {
                if (currentLsd.isNotBlank()) add("X-FB-LSD", currentLsd)
            }
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .build()

        val request2 = Request.Builder()
            .url("$BASE_URL/api/graphql")
            .headers(headers2)
            .post(body2)
            .build()

        httpClient.newCall(request2).execute().use { response ->
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                throw IllegalStateException("Cookie DIE hoặc hết phiên đăng nhập (redirect ${response.code})")
            }
            if (response.code == 429) {
                throw IllegalStateException("Instagram giới hạn tạm thời (HTTP 429)")
            }
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful || response.code == 200) {
                if (responseBody.contains("\"viewer_has_liked\":true") ||
                    responseBody.contains("\"status\":\"ok\"") ||
                    responseBody.contains("\"is_final\":true") ||
                    (responseBody.contains("\"data\"") && !responseBody.contains("\"errors\"") && !responseBody.contains("\"error\""))
                ) {
                    return true
                }
            }
            if (responseBody.contains("\"spam\"", ignoreCase = true) || responseBody.contains("feedback_required", ignoreCase = true)) {
                throw IllegalStateException("Instagram chặn Like (Spam/Action blocked)")
            }
            if (responseBody.contains("\"require_login\"", ignoreCase = true) || responseBody.contains("login_required", ignoreCase = true) || responseBody.contains("checkpoint_required", ignoreCase = true)) {
                throw IllegalStateException("Cookie DIE hoặc yêu cầu đăng nhập lại")
            }
            if (responseBody.trimStart().startsWith("<")) {
                throw IllegalStateException("Instagram trả về HTML - Cookie hết hạn hoặc checkpoint")
            }
            if (responseBody.isNotBlank()) {
                try {
                    val json = JSONObject(responseBody)
                    val errArr = json.optJSONArray("errors")
                    if (errArr != null && errArr.length() > 0) {
                        lastErrorDetail = errArr.getJSONObject(0).optString("message", "")
                    } else if (json.has("message")) {
                        lastErrorDetail = json.optString("message", "")
                    }
                } catch (_: Exception) {}
            }
            if (lastErrorDetail.isNotBlank()) {
                throw IllegalStateException("Instagram từ chối Like: $lastErrorDetail")
            }
            return false
        }
    }

    /**
     * Like bài viết dựa trên ID hoặc URL bài viết hoàn toàn bằng GraphQL
     */
    @Throws(Exception::class)
    fun likeTarget(targetMediaIdOrUrl: String, fbDtsg: String? = null, lsd: String? = null, actorId: String? = null): Boolean {
        val clean = targetMediaIdOrUrl.trim()
        if (clean.isBlank()) {
            throw IllegalStateException("ID hoặc liên kết bài viết rỗng")
        }

        var shortcode = ""
        var mediaId = ""

        if (clean.all { it.isDigit() }) {
            mediaId = clean
        } else {
            shortcode = extractShortcode(clean)
            val decodedId = shortcodeToMediaId(shortcode)
            if (decodedId.isNotBlank()) {
                mediaId = decodedId
            }
        }

        val finalMediaId = if (mediaId.isNotBlank()) mediaId else clean

        // Thực hiện Like qua GraphQL của Instagram Web
        val okGraphQL = likeMediaGraphQL(finalMediaId, shortcode, fbDtsg, lsd, actorId)
        if (okGraphQL) return true

        throw IllegalStateException("Instagram từ chối Like bài viết (ID: $finalMediaId)")
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
                    return if (json.has("profile_pic_url")) json.getString("profile_pic_url") else null
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


