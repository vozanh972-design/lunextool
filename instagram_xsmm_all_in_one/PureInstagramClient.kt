package com.cayxu.app.instagram.pure

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * CLIENT TƯƠNG TÁC THUẦN INSTAGRAM GRAPHQL (COMET MWEB).
 * Sinh động 1 lần duy nhất trong vòng đời: User-Agent Mobile Android, ig_did, __s, x-ig-app-id: 1217981644879628.
 * Chạy 100% GraphQL Comet MWeb để chống triệt để HTTP 429.
 * Hỗ trợ đổi avatar (Avatar bút chì).
 */
class PureInstagramClient(
    private var proxyStr: String? = null,
    private var customUserAgent: String? = null
) {
    companion object {
        const val BASE_URL = "https://www.instagram.com"
        const val GRAPHQL_ENDPOINT = "https://www.instagram.com/api/graphql"

        // Instagram Standard Identifiers
        const val IG_APP_ID = "1217981644879628"
        const val ASBD_ID = "359341"
        const val AJAX_ROLLOUT = "1047675214"
        const val HS = "20712.HYP:instagram_web_pkg.2.1...0"

        // GraphQL Doc IDs
        const val DOC_ID_FOLLOW_MUTATION = "26508036048874888"
        const val DOC_ID_LIKE_MUTATION_POLARIS = "27358573637160660"

        // Regex
        val UID_COOKIE_REGEX = Pattern.compile("(?:ds_user_id|c_user|user_id|account_id|uid)=(\\d+)")
        val CSRF_COOKIE_REGEX = Pattern.compile("(?:^|;\\s*)csrftoken=([^;]+)")
        val PATTERN_USER_ID = Pattern.compile("\"(?:profile_id|user_id|target_id|actor_id)\"\\s*:\\s*\"?([0-9]+)\"?")
        val PATTERN_USERNAME = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_FULL_NAME = Pattern.compile("\"full_name\"\\s*:\\s*\"([^\"]*)\"")
        val PATTERN_PROFILE_PIC = Pattern.compile("\"profile_pic_url(?:_hd)?\"\\s*:\\s*\"([^\"]+)\"")
        val PATTERN_DTSG = Pattern.compile("\\[\"DTSGInitData\",\\[\\],\\{\"token\":\"([^\"]+)\"")
        val PATTERN_DTSG_SIMPLE = Pattern.compile("\"token\"\\s*:\\s*\"(NA[a-zA-Z0-9_-]+:[0-9]+:[0-9]+)\"")
        val PATTERN_LSD = Pattern.compile("\\[\"LSD\",\\[\\],\\{\"token\":\"([^\"]+)\"")
        val PATTERN_SPIN_R = Pattern.compile("\"__spin_r\"\\s*:\\s*([0-9]+)")
        val PATTERN_HS = Pattern.compile("\"haste_session\"\\s*:\\s*\"([^\"]+)\"")

        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S928B Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.107 Mobile Safari/537.36"

        fun calculateJazoest(token: String): String {
            if (token.isBlank()) return "26425"
            var sum = 0
            for (c in token) {
                sum += c.code
            }
            return "2$sum"
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
    }

    private val userAgent = customUserAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
    private var httpClient: OkHttpClient

    val cookieMap = LinkedHashMap<String, String>()
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
    var activeDeviceId: String = ""
    var activeWwwClaim: String = "0"
    private var reqIndex = 1

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .addNetworkInterceptor { chain ->
                val resp = chain.proceed(chain.request())
                resp.header("x-ig-set-www-claim")?.let { claim ->
                    if (claim.isNotBlank() && claim != "0") {
                        activeWwwClaim = claim
                    }
                }
                resp.headers("Set-Cookie").let { setCookieHeaders ->
                    if (setCookieHeaders.isNotEmpty()) {
                        updateFromSetCookie(setCookieHeaders)
                    }
                }
                resp
            }

        if (!proxyStr.isNullOrEmpty()) {
            setupProxy(builder, proxyStr!!)
        }
        httpClient = builder.build()
    }

    private fun setupProxy(builder: OkHttpClient.Builder, proxyStr: String) {
        val parts = proxyStr.split(":")
        val host = parts[0]
        val port = parts[1].toInt()
        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))

        if (parts.size >= 4) {
            val user = parts[2]
            val pass = parts[3]
            builder.proxyAuthenticator { _, response ->
                val credential = Credentials.basic(user, pass)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        }
    }

    fun loadCookie(rawCookie: String) {
        if (rawCookie.isBlank()) return
        val parts = rawCookie.split(";")
        for (part in parts) {
            val item = part.trim()
            if (item.isEmpty()) continue
            val eqIdx = item.indexOf('=')
            if (eqIdx > 0) {
                val key = item.substring(0, eqIdx).trim()
                val value = item.substring(eqIdx + 1).trim()
                cookieMap[key] = value
            }
        }
        if (!cookieMap.containsKey("ig_did") || cookieMap["ig_did"].isNullOrBlank()) {
            activeDeviceId = java.util.UUID.randomUUID().toString().uppercase()
            cookieMap["ig_did"] = activeDeviceId
        } else {
            activeDeviceId = cookieMap["ig_did"]!!
        }
        if (!cookieMap.containsKey("__s") || cookieMap["__s"].isNullOrBlank()) {
            activeS = generateSessionS()
            cookieMap["__s"] = activeS
        } else {
            activeS = cookieMap["__s"]!!
        }
        if (!cookieMap.containsKey("dpr")) cookieMap["dpr"] = "3"
        if (!cookieMap.containsKey("wd")) cookieMap["wd"] = "360x740"
        if (!cookieMap.containsKey("ps_l")) cookieMap["ps_l"] = "1"
        if (!cookieMap.containsKey("ps_n")) cookieMap["ps_n"] = "1"
        if (!cookieMap.containsKey("ig_nrcb")) cookieMap["ig_nrcb"] = "1"

        cookieMap["csrftoken"]?.let { if (it.isNotBlank()) activeCsrfToken = it }
        cookieMap["ds_user_id"]?.let { if (it.isNotBlank()) activeUserId = it }
        activeActorId = activeUserId
    }

    private fun getCookieString(): String {
        return cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
    }

    fun updateFromSetCookie(setCookieHeaders: List<String>) {
        for (header in setCookieHeaders) {
            val cookiePart = header.substringBefore(";").trim()
            val eqIdx = cookiePart.indexOf('=')
            if (eqIdx > 0) {
                val key = cookiePart.substring(0, eqIdx).trim()
                val value = cookiePart.substring(eqIdx + 1).trim()
                cookieMap[key] = value
                if (key.equals("csrftoken", ignoreCase = true)) {
                    activeCsrfToken = value
                }
            }
        }
    }

    private fun generateSessionS(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        fun randStr(len: Int) = (1..len).map { alphabet.random() }.joinToString("")
        return "${randStr(6)}:${randStr(6)}:${randStr(6)}"
    }

    private fun generateHsi(): String {
        val r = (1000000000000000L..9999999999999999L).random()
        return "768$r"
    }

    private fun nextReq(): String {
        synchronized(this) {
            return (reqIndex++).toString()
        }
    }

    private fun buildStandardHeaders(
        csrfToken: String = activeCsrfToken,
        friendlyName: String? = null,
        lsdToken: String? = null,
        referer: String = "$BASE_URL/"
    ): Headers {
        val builder = Headers.Builder()
            .add("user-agent", userAgent)
            .add("cookie", getCookieString())
            .add("accept", "*/*")
            .add("accept-language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .add("origin", BASE_URL)
            .add("referer", referer)
            .add("sec-ch-prefers-color-scheme", "dark")
            .add("sec-ch-ua", "\"Chromium\";v=\"130\", \"Not?A_Brand\";v=\"24\", \"Google Chrome\";v=\"130\"")
            .add("sec-ch-ua-mobile", "?1")
            .add("sec-ch-ua-platform", "\"Android\"")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "same-origin")
            .add("x-asbd-id", ASBD_ID)
            .add("x-ig-app-id", IG_APP_ID)
            .add("x-ig-d", "www")
            .add("x-ig-max-touch-points", "1")

        if (csrfToken.isNotBlank()) {
            builder.add("x-csrftoken", csrfToken)
        }
        if (!friendlyName.isNullOrBlank()) {
            builder.add("x-fb-friendly-name", friendlyName)
        }
        val effectiveLsd = lsdToken?.takeIf { it.isNotBlank() } ?: activeLsd
        if (effectiveLsd.isNotBlank()) {
            builder.add("x-fb-lsd", effectiveLsd)
        }
        if (activeWwwClaim.isNotBlank() && activeWwwClaim != "0") {
            builder.add("x-ig-www-claim", activeWwwClaim)
        }
        if (activeS.isNotBlank()) {
            builder.add("x-web-session-id", activeS)
        }
        return builder.build()
    }

    /**
     * 1. Xác thực & Bóc tách thông tin tài khoản từ Cookie
     */
    @Throws(Exception::class)
    fun verifyCookieAndGetProfile(cookie: String, username: String? = null): InstagramProfile {
        loadCookie(cookie)
        val uid = activeUserId.ifBlank {
            val m = UID_COOKIE_REGEX.matcher(cookie)
            if (m.find()) m.group(1) else ""
        }
        val csrf = activeCsrfToken.ifBlank {
            val m = CSRF_COOKIE_REGEX.matcher(cookie)
            if (m.find()) m.group(1) else ""
        }
        if (!cookie.contains("sessionid")) {
            throw IllegalArgumentException("Cookie không hợp lệ: Thiếu sessionid")
        }

        val targetUser = username ?: uid
        val request = Request.Builder()
            .url("$BASE_URL/$targetUser/")
            .headers(buildStandardHeaders(csrfToken = csrf, referer = "$BASE_URL/"))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""

            if (response.code in listOf(401, 403) || body.contains("login_required") || body.contains("checkpoint_required")) {
                throw IllegalStateException("Cookie Die hoặc tài khoản bị Checkpoint!")
            }

            val dtsg = PATTERN_DTSG.matcher(body).let { if (it.find()) it.group(1) else null }
                ?: PATTERN_DTSG_SIMPLE.matcher(body).let { if (it.find()) it.group(1) else null }
            val lsd = PATTERN_LSD.matcher(body).let { if (it.find()) it.group(1) else null }
            val spinR = PATTERN_SPIN_R.matcher(body).let { if (it.find()) it.group(1) else null }
            val hs = PATTERN_HS.matcher(body).let { if (it.find()) it.group(1) else null }

            if (!dtsg.isNullOrBlank()) activeFbDtsg = dtsg
            if (!lsd.isNullOrBlank()) activeLsd = lsd
            if (!spinR.isNullOrBlank()) activeSpinR = spinR
            if (!hs.isNullOrBlank()) activeHs = hs

            val uname = PATTERN_USERNAME.matcher(body).let { if (it.find()) it.group(1) else targetUser }
            val fname = PATTERN_FULL_NAME.matcher(body).let { if (it.find()) it.group(1) else uname }
            val pic = PATTERN_PROFILE_PIC.matcher(body).let {
                if (it.find()) it.group(1)?.replace("\\u0026", "&")?.replace("\\/", "/") else null
            }

            return InstagramProfile(
                userId = uid,
                username = uname ?: targetUser,
                fullName = fname ?: targetUser,
                profilePicUrl = pic,
                isLive = true,
                cookie = getCookieString(),
                csrfToken = activeCsrfToken
            )
        }
    }

    /**
     * 2. Follow tài khoản Instagram bằng Comet MWeb GraphQL Mutation
     */
    fun followUser(targetUserId: String, account: InstagramProfile): InstagramActionResult {
        loadCookie(account.cookie ?: "")
        val csrf = activeCsrfToken.ifBlank { account.csrfToken ?: "" }
        val cleanTarget = targetUserId.trim()

        val av = activeUserId.ifBlank { account.userId }
        val currentDtsg = activeFbDtsg
        val currentLsd = activeLsd
        val currentJazoest = calculateJazoest(currentDtsg)
        val currentSpinT = (System.currentTimeMillis() / 1000L).toString()
        if (activeS.isBlank()) activeS = generateSessionS()
        if (activeHsi.isBlank()) activeHsi = generateHsi()

        val variables = JSONObject().apply {
            put("target_user_id", cleanTarget)
            put("container_module", "profile")
            put("nav_chain", "PolarisFeedRoot:feedPage:1:via_cold_start,PolarisProfilePostsTabRoot:profilePage:2:unexpected")
        }.toString()

        val formBody = FormBody.Builder()
            .add("av", av)
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", nextReq())
            .add("__hs", activeHs)
            .add("dpr", "3")
            .add("__ccg", "GOOD")
            .add("__rev", activeSpinR)
            .add("__s", activeS)
            .add("__hsi", activeHsi)
            .add("__comet_req", "7")
            .apply {
                if (currentDtsg.isNotBlank()) {
                    add("fb_dtsg", currentDtsg)
                    add("jazoest", currentJazoest)
                }
                if (currentLsd.isNotBlank()) {
                    add("lsd", currentLsd)
                }
            }
            .add("__spin_r", activeSpinR)
            .add("__spin_b", "trunk")
            .add("__spin_t", currentSpinT)
            .add("__crn", "comet.igweb.PolarisProfilePostsTabRoute")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
            .add("server_timestamps", "true")
            .add("variables", variables)
            .add("doc_id", DOC_ID_FOLLOW_MUTATION)
            .build()

        val headers = buildStandardHeaders(
            csrfToken = csrf,
            friendlyName = "usePolarisFollowMutation",
            lsdToken = currentLsd,
            referer = "$BASE_URL/"
        )

        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .headers(headers)
            .post(formBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val isSuccess = response.isSuccessful && (
                    body.contains("\"status\":\"ok\"") ||
                    body.contains("\"following\":true") ||
                    body.contains("\"xdt_create_friendship\"") ||
                    (!body.trimStart().startsWith("<") && !body.contains("\"error\"") && !body.contains("\"errors\""))
                )

                InstagramActionResult(
                    isSuccess = isSuccess,
                    actionType = InstagramActionType.FOLLOW,
                    target = cleanTarget,
                    message = if (isSuccess) "Follow Instagram thành công!" else "Follow thất bại: $body",
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            InstagramActionResult(false, InstagramActionType.FOLLOW, cleanTarget, "Lỗi kết nối: ${e.message}")
        }
    }

    /**
     * 3. Like bài viết Instagram bằng GraphQL Mutation
     */
    fun likeMedia(mediaIdOrUrl: String, account: InstagramProfile): InstagramActionResult {
        loadCookie(account.cookie ?: "")
        val csrf = activeCsrfToken.ifBlank { account.csrfToken ?: "" }

        val clean = mediaIdOrUrl.trim()
        val shortcode = if (clean.all { it.isDigit() }) "" else extractShortcode(clean)
        val mediaId = if (clean.all { it.isDigit() }) clean else shortcodeToMediaId(shortcode).ifBlank { clean }

        val av = activeUserId.ifBlank { account.userId }
        val currentDtsg = activeFbDtsg
        val currentLsd = activeLsd
        val currentJazoest = calculateJazoest(currentDtsg)
        val currentSpinT = (System.currentTimeMillis() / 1000L).toString()
        if (activeS.isBlank()) activeS = generateSessionS()
        if (activeHsi.isBlank()) activeHsi = generateHsi()

        val ref = if (shortcode.isNotBlank()) "$BASE_URL/p/$shortcode/" else "$BASE_URL/"

        val variables = JSONObject().apply {
            val inputObj = JSONObject().apply {
                put("media_id", mediaId)
                if (av.isNotBlank() && av != "0") {
                    put("actor_id", av)
                }
                put("client_mutation_id", "1")
            }
            put("input", inputObj)
        }.toString()

        val formBody = FormBody.Builder()
            .add("av", av)
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", nextReq())
            .add("__hs", activeHs)
            .add("dpr", "3")
            .add("__ccg", "GOOD")
            .add("__rev", activeSpinR)
            .add("__s", activeS)
            .add("__hsi", activeHsi)
            .add("__comet_req", "7")
            .apply {
                if (currentDtsg.isNotBlank()) {
                    add("fb_dtsg", currentDtsg)
                    add("jazoest", currentJazoest)
                }
                if (currentLsd.isNotBlank()) {
                    add("lsd", currentLsd)
                }
            }
            .add("__spin_r", activeSpinR)
            .add("__spin_b", "trunk")
            .add("__spin_t", currentSpinT)
            .add("__crn", "comet.igweb.PolarisPostRouteNext")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "PolarisAPILikePostMutation")
            .add("server_timestamps", "true")
            .add("variables", variables)
            .add("doc_id", DOC_ID_LIKE_MUTATION_POLARIS)
            .build()

        val headers = buildStandardHeaders(
            csrfToken = csrf,
            friendlyName = "PolarisAPILikePostMutation",
            lsdToken = currentLsd,
            referer = ref
        )

        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .headers(headers)
            .post(formBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val isSuccess = response.isSuccessful && (
                    body.contains("\"viewer_has_liked\":true") ||
                    body.contains("\"status\":\"ok\"") ||
                    body.contains("\"xdt_like_media\"") ||
                    body.contains("\"is_final\":true") ||
                    (body.contains("\"data\"") && !body.contains("\"errors\"") && !body.contains("\"error\""))
                )

                InstagramActionResult(
                    isSuccess = isSuccess,
                    actionType = InstagramActionType.LIKE,
                    target = mediaId,
                    message = if (isSuccess) "Like bài viết Instagram thành công!" else "Like thất bại: $body",
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            InstagramActionResult(false, InstagramActionType.LIKE, mediaId, "Lỗi kết nối: ${e.message}")
        }
    }

    /**
     * 4. Đổi ảnh đại diện (Avatar bút chì) qua Web API
     */
    @Throws(Exception::class)
    fun changeProfilePicture(imageBytes: ByteArray): String? {
        val csrf = activeCsrfToken.ifBlank { cookieMap["csrftoken"] ?: throw IllegalStateException("Cookie thiếu CSRF token") }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "profile_pic",
                "profile_pic.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val headers = buildStandardHeaders(csrfToken = csrf, referer = "$BASE_URL/accounts/edit/")
            .newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
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

            val json = JSONObject(responseBody)
            if (json.optString("status") == "ok" || json.optBoolean("has_profile_pic", false)) {
                return if (json.has("profile_pic_url")) json.getString("profile_pic_url") else null
            }
            return null
        }
    }

    @Throws(Exception::class)
    fun changeProfilePicture(imageFile: File): String? {
        if (!imageFile.exists() || !imageFile.canRead()) {
            throw IllegalArgumentException("File ảnh không tồn tại: ${imageFile.absolutePath}")
        }
        return changeProfilePicture(imageFile.readBytes())
    }
}

