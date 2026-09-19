package com.cayxu.app.instagram

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random
import com.cayxu.app.util.NativeSecurity
import com.cayxu.app.facebook.TotpGenerator

/**
 * Client tương tác Instagram chuẩn 100% INSTAGRAM APP NATIVE (không lai tạp Instagram Web):
 * - Device Fingerprint Engine: Sinh cấu hình thiết bị Android App (Instagram 447.0.0.55.81 Android) riêng biệt cho từng acc dựa trên UID.
 * - InstagramSession: Lưu trữ cache Session chuẩn Instagram App REST API (Cookie, X-IG-App-ID, X-IG-Capabilities, X-IG-Connection-Type, X-CSRFToken).
 * - 100% App REST API: Đăng nhập (/api/v1/accounts/login/), 2FA (/api/v1/accounts/two_factor_login/), Lấy Avatar HD (/api/v1/users/{userId}/info/), Đổi Avatar (/api/v1/accounts/change_profile_picture/), Xóa Avatar (/api/v1/accounts/remove_profile_picture/), Follow (/api/v1/friendships/create/), Like (/api/v1/media/{mediaId}/like/), Comment (/api/v1/media/{mediaId}/comment/).
 */
class InstagramApiClient(
    var cookie: String = "",
    var userAgent: String = "",
    var proxyConfig: ProxyConfig? = null,
    var initialFbDtsg: String? = null,
    var initialLsd: String? = null
) {
    data class ProxyConfig(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
        val type: Proxy.Type = Proxy.Type.HTTP
    )

    data class CookieCheckResult(
        val isLive: Boolean,
        val username: String = "",
        val userId: String = "",
        val fullName: String = "",
        val biography: String = "",
        val email: String = "",
        val phoneNumber: String = "",
        val profilePicUrl: String = "",
        val rawJson: String = "",
        val fbDtsg: String = "",
        val lsd: String = "",
        val followersCount: Int = 0,
        val followingCount: Int = 0,
        val postsCount: Int = 0
    )

    data class IgActionResult(
        val success: Boolean,
        val message: String = "",
        val rawResponse: String = ""
    )

    data class IgLoginResult(
        val isSuccess: Boolean,
        val userId: String = "",
        val username: String = "",
        val fullName: String = "",
        val cookie: String = "",
        val avatarUrl: String = "",
        val isTwoFactorRequired: Boolean = false,
        val message: String = "",
        val rawResponse: String = ""
    )

    data class InstagramUserInfo(
        val username: String = "",
        val userId: String = "",
        val fullName: String = "",
        val profilePicUrl: String? = null,
        val biography: String = "",
        val followersCount: Int = 0,
        val followingCount: Int = 0,
        val postsCount: Int = 0,
        val isLive: Boolean = true,
        val fbDtsg: String? = null,
        val lsd: String? = null,
        val actorId: String? = null
    )

    data class DeviceProfile(
        val userAgent: String,
        val secChUa: String,
        val secChUaMobile: String,
        val secChUaModel: String,
        val secChUaPlatform: String,
        val secChUaPlatformVersion: String,
        val appId: String,
        val asbdId: String,
        val dpr: String = "3"
    )

    data class InstagramSession(
        val accountKey: String,
        val cookie: String,
        val csrfToken: String,
        val actorId: String,
        var fbDtsg: String,
        var lsd: String,
        var jazoest: String,
        var profilePicUrl: String,
        val deviceProfile: DeviceProfile,
        val baseHeaders: MutableMap<String, String>
    )

    var activeFbDtsg: String = ""
    var activeLsd: String = ""
    var activeActorId: String = ""

    private var currentSession: InstagramSession? = null

    init {
        cookie = normalizeToIosCookie(cookie)
        val uid = if (cookie.isNotBlank()) extractActorId(cookie) else ""
        if (userAgent.isBlank() || !userAgent.contains("Instagram")) {
            val profile = getDeviceProfileFor(uid)
            userAgent = profile.userAgent
        }
    }

    /**
     * Khởi tạo hoặc lấy session đã cache cho instance này.
     * Đảm bảo mọi token và header trình duyệt được nạp đủ 100% trước khi gửi bất kỳ request nào.
     */
    fun ensureSession(): InstagramSession {
        currentSession?.let { return it }
        cookie = normalizeToIosCookie(cookie)
        val session = getOrCreateSession(cookie, userAgent, proxyConfig, initialFbDtsg, initialLsd)
        currentSession = session
        activeFbDtsg = session.fbDtsg
        activeLsd = session.lsd
        activeActorId = session.actorId
        return session
    }

    // ============================================================================
    // CÁC HÀM THAO TÁC THEO ĐÚNG INSTANCE ĐÃ CÓ SESSION (DÙNG CHO RUNNER & SCREEN)
    // ============================================================================

    fun follow(targetId: String, profileUrl: String = ""): IgActionResult {
        val session = ensureSession()
        return executeFollow(session, targetId, profileUrl, proxyConfig)
    }

    fun tym(mediaId: String, linkJob: String = ""): IgActionResult {
        val session = ensureSession()
        return executeTym(session, mediaId, linkJob, proxyConfig)
    }

    fun cmt(mediaId: String, text: String, linkJob: String = ""): IgActionResult {
        val session = ensureSession()
        return executeCmt(session, mediaId, text, linkJob, proxyConfig)
    }

    fun checkCookieIg(): CookieCheckResult {
        val proxyStr = proxyConfig?.let { "${it.host}:${it.port}:${it.username.orEmpty()}:${it.password.orEmpty()}" }
        return checkCookieIg(cookie, proxyStr)
    }

    fun fetchUserInfo(): InstagramUserInfo {
        val session = ensureSession()
        val check = checkCookieIg()
        if (!check.isLive) {
            throw IllegalStateException("Cookie DIE hoặc không hợp lệ")
        }
        val pic = check.profilePicUrl.ifBlank { session.profilePicUrl }
        return InstagramUserInfo(
            username = check.username,
            userId = check.userId,
            fullName = check.fullName,
            profilePicUrl = pic.takeIf { it.isNotBlank() },
            biography = check.biography,
            isLive = true,
            fbDtsg = session.fbDtsg,
            lsd = session.lsd,
            actorId = check.userId
        )
    }

    fun fetchAccountDetails(targetUsername: String? = null): InstagramUserInfo {
        val session = ensureSession()
        val check = checkCookieIg()
        val targetName = targetUsername?.trim()?.removePrefix("@").orEmpty()
        val finalUsername = check.username.ifBlank {
            if (!targetName.startsWith("IG_")) targetName else ""
        }
        var pic = check.profilePicUrl
        val proxyStr = proxyConfig?.let { "${it.host}:${it.port}:${it.username.orEmpty()}:${it.password.orEmpty()}" }
        if ((pic.isBlank() || !pic.startsWith("http")) && finalUsername.isNotBlank()) {
            pic = fetchProfilePic(finalUsername, cookie, proxyStr) ?: ""
        }
        if ((pic.isBlank() || !pic.startsWith("http")) && check.userId.isNotBlank() && check.userId != "0") {
            pic = fetchProfilePic(check.userId, cookie, proxyStr) ?: ""
        }
        if (pic.isNotBlank() && pic.startsWith("http")) {
            session.profilePicUrl = pic
        }
        val finalPic = pic.takeIf { it.isNotBlank() && it.startsWith("http") }
            ?: session.profilePicUrl.takeIf { it.isNotBlank() && it.startsWith("http") }
        return InstagramUserInfo(
            username = finalUsername,
            userId = check.userId.ifBlank { session.actorId },
            fullName = check.fullName,
            profilePicUrl = finalPic,
            biography = check.biography,
            followersCount = check.followersCount,
            followingCount = check.followingCount,
            postsCount = check.postsCount,
            isLive = check.isLive,
            fbDtsg = session.fbDtsg,
            lsd = session.lsd,
            actorId = check.userId.ifBlank { session.actorId }
        )
    }

    fun followTarget(
        targetIdOrUsername: String,
        profileUrl: String = "",
        fbDtsg: String? = null,
        lsd: String? = null,
        actorId: String? = null
    ): Boolean {
        var finalId = targetIdOrUsername.trim().removePrefix("@")
        val proxyStr = proxyConfig?.let { "${it.host}:${it.port}:${it.username.orEmpty()}:${it.password.orEmpty()}" }
        if (!finalId.all { it.isDigit() }) {
            val resolved = resolveTargetUserId(profileUrl.ifBlank { finalId }, proxyStr)
            if (!resolved.isNullOrBlank()) {
                finalId = resolved
            }
        }
        val res = follow(finalId, profileUrl)
        if (!res.success) {
            throw IllegalStateException(res.message)
        }
        return true
    }

    fun likeTarget(
        mediaIdOrUrl: String,
        linkJob: String = "",
        fbDtsg: String? = null,
        lsd: String? = null,
        actorId: String? = null
    ): Boolean {
        var finalMediaId = mediaIdOrUrl.trim()
        val proxyStr = proxyConfig?.let { "${it.host}:${it.port}:${it.username.orEmpty()}:${it.password.orEmpty()}" }
        if (!finalMediaId.all { it.isDigit() }) {
            val resolved = resolveMediaId(linkJob.ifBlank { finalMediaId }, proxyStr)
            if (!resolved.isNullOrBlank()) {
                finalMediaId = resolved
            }
        }
        val res = tym(finalMediaId, linkJob.ifBlank { if (mediaIdOrUrl.startsWith("http")) mediaIdOrUrl else "" })
        if (!res.success) {
            throw IllegalStateException(res.message)
        }
        return true
    }

    fun commentTarget(
        mediaIdOrUrl: String,
        commentText: String,
        linkJob: String = ""
    ): Boolean {
        var finalMediaId = mediaIdOrUrl.trim()
        val proxyStr = proxyConfig?.let { "${it.host}:${it.port}:${it.username.orEmpty()}:${it.password.orEmpty()}" }
        if (!finalMediaId.all { it.isDigit() }) {
            val resolved = resolveMediaId(linkJob.ifBlank { finalMediaId }, proxyStr)
            if (!resolved.isNullOrBlank()) {
                finalMediaId = resolved
            }
        }
        val res = cmt(finalMediaId, commentText, linkJob)
        if (!res.success) {
            throw IllegalStateException(res.message)
        }
        return true
    }

    fun changeProfilePicture(imageBytes: ByteArray): String? {
        val session = ensureSession()
        val proxyStr = proxyConfig?.let { "${it.host}:${it.port}:${it.username.orEmpty()}:${it.password.orEmpty()}" }
        val newUrl = changeProfilePicture(imageBytes, session.cookie, proxyStr)
        if (!newUrl.isNullOrBlank()) {
            session.profilePicUrl = newUrl
        }
        return newUrl
    }

    // ============================================================================
    // COMPANION OBJECT: TOÀN BỘ LOGIC STATIC, FINGERPRINT VÀ CURL CONSTANTS
    // ============================================================================

    companion object {
        const val BASE_URL = "https://i.instagram.com/api/v1"
        const val IG_APP_ID_PRIVATE = "567067343352427"
        const val IG_CAPABILITIES = "3brTvw=="
        const val IG_CONN_TYPE = "WIFI"
        const val IG_APP_UA = "Instagram 447.0.0.55.81 Android (36/15; 480dpi; 1080x2340; samsung; SM-S928B; e3q; qcom; vi_VN; 385311890)"

        const val APP_ID = IG_APP_ID_PRIVATE
        const val CONNECTION_TYPE = IG_CONN_TYPE
        const val CAPABILITIES = IG_CAPABILITIES

        val USER_AGENT_MOBILE: String get() = IG_APP_UA
        val SEC_CH_UA_MOBILE: String get() = ""
        val APP_ID_MOBILE: String get() = IG_APP_ID_PRIVATE
        val ASBD_ID_MOBILE: String get() = ""

        // ========================================================================
        // DEVICE FINGERPRINT ENGINE: 100% Android App Instagram 447
        // Chuẩn Instagram App, không dùng iOS Safari hay Web UA
        // ========================================================================
        private val ANDROID_APP_DEVICE_PROFILES by lazy {
            listOf(
                // Samsung Galaxy S24 Ultra
                DeviceProfile(
                    userAgent = "Instagram 447.0.0.55.81 Android (36/15; 480dpi; 1080x2340; samsung; SM-S928B; e3q; qcom; vi_VN; 385311890)",
                    secChUa = "",
                    secChUaMobile = "",
                    secChUaModel = "SM-S928B",
                    secChUaPlatform = "Android",
                    secChUaPlatformVersion = "15",
                    appId = IG_APP_ID_PRIVATE,
                    asbdId = "",
                    dpr = "3"
                ),
                // Google Pixel 9 Pro
                DeviceProfile(
                    userAgent = "Instagram 447.0.0.55.81 Android (35/14; 420dpi; 1080x2400; Google; Pixel 9 Pro; tokay; tensor; vi_VN; 385311890)",
                    secChUa = "",
                    secChUaMobile = "",
                    secChUaModel = "Pixel 9 Pro",
                    secChUaPlatform = "Android",
                    secChUaPlatformVersion = "14",
                    appId = IG_APP_ID_PRIVATE,
                    asbdId = "",
                    dpr = "2"
                ),
                // Samsung Galaxy A55
                DeviceProfile(
                    userAgent = "Instagram 447.0.0.55.81 Android (34/14; 420dpi; 1080x2340; samsung; SM-A556B; a55xq; qcom; vi_VN; 385311890)",
                    secChUa = "",
                    secChUaMobile = "",
                    secChUaModel = "SM-A556B",
                    secChUaPlatform = "Android",
                    secChUaPlatformVersion = "14",
                    appId = IG_APP_ID_PRIVATE,
                    asbdId = "",
                    dpr = "2"
                ),
                // Xiaomi 14
                DeviceProfile(
                    userAgent = "Instagram 447.0.0.55.81 Android (35/14; 440dpi; 1220x2712; Xiaomi; 24116PN5BC; houhou; qcom; vi_VN; 385311890)",
                    secChUa = "",
                    secChUaMobile = "",
                    secChUaModel = "24116PN5BC",
                    secChUaPlatform = "Android",
                    secChUaPlatformVersion = "14",
                    appId = IG_APP_ID_PRIVATE,
                    asbdId = "",
                    dpr = "3"
                ),
                // OnePlus 12
                DeviceProfile(
                    userAgent = "Instagram 447.0.0.55.81 Android (34/14; 450dpi; 1440x3168; OnePlus; CPH2573; OP5929L1; qcom; vi_VN; 385311890)",
                    secChUa = "",
                    secChUaMobile = "",
                    secChUaModel = "CPH2573",
                    secChUaPlatform = "Android",
                    secChUaPlatformVersion = "14",
                    appId = IG_APP_ID_PRIVATE,
                    asbdId = "",
                    dpr = "3"
                ),
                // OPPO Reno12
                DeviceProfile(
                    userAgent = "Instagram 447.0.0.55.81 Android (34/14; 420dpi; 1080x2412; OPPO; CPH2625; OP5665L1; qcom; vi_VN; 385311890)",
                    secChUa = "",
                    secChUaMobile = "",
                    secChUaModel = "CPH2625",
                    secChUaPlatform = "Android",
                    secChUaPlatformVersion = "14",
                    appId = IG_APP_ID_PRIVATE,
                    asbdId = "",
                    dpr = "2"
                )
            )
        }

        /**
         * Ánh xạ cấu hình thiết bị Android App theo UID/Key tài khoản.
         * Mỗi tài khoản sử dụng UA riêng biệt từ bộ Android thực.
         */
        fun getDeviceProfileFor(accountKey: String, userAgentHint: String? = null): DeviceProfile {
            val key = accountKey.ifBlank { "default_acc" }
            val idx = Math.abs(key.hashCode()) % ANDROID_APP_DEVICE_PROFILES.size
            return ANDROID_APP_DEVICE_PROFILES[idx]
        }

        // Cache session theo từng tài khoản
        private val sessionCache = ConcurrentHashMap<String, InstagramSession>()

        /**
         * Lấy hoặc khởi tạo InstagramSession cho một tài khoản.
         * Nạp sẵn toàn bộ Token và Headers chuẩn Instagram App REST API một lần duy nhất.
         */
        fun getOrCreateSession(
            cookie: String,
            userAgentHint: String? = null,
            proxyConfig: ProxyConfig? = null,
            initialFbDtsg: String? = null,
            initialLsd: String? = null
        ): InstagramSession {
            val normalized = normalizeToIosCookie(cookie)
            val actorId = extractActorId(normalized)
            val sessionKey = if (actorId.isNotBlank() && actorId != "0") actorId else normalized.hashCode().toString()

            sessionCache[sessionKey]?.let { return it }

            val csrf = extractCsrfToken(normalized)
            val profile = getDeviceProfileFor(sessionKey, userAgentHint)

            val fbDtsg = initialFbDtsg?.takeIf { it.isNotBlank() } ?: ""
            val lsd = initialLsd?.takeIf { it.isNotBlank() } ?: ""
            val jazoest = ""

            // Xây dựng bộ baseHeaders chuẩn 100% Instagram App REST API
            val headers = mutableMapOf(
                "User-Agent" to profile.userAgent,
                "X-IG-App-ID" to IG_APP_ID_PRIVATE,
                "X-IG-Connection-Type" to IG_CONN_TYPE,
                "X-IG-Capabilities" to IG_CAPABILITIES,
                "Accept-Language" to "vi-VN, en-US",
                "Cookie" to normalized,
                "Accept-Encoding" to "gzip, deflate"
            )
            if (csrf.isNotBlank()) {
                headers["X-CSRFToken"] = csrf
            }

            val session = InstagramSession(
                accountKey = sessionKey,
                cookie = normalized,
                csrfToken = csrf,
                actorId = actorId,
                fbDtsg = fbDtsg,
                lsd = lsd,
                jazoest = jazoest,
                profilePicUrl = "",
                deviceProfile = profile,
                baseHeaders = headers
            )
            sessionCache[sessionKey] = session
            return session
        }

        // ========================================================================
        // 100% INSTAGRAM APP REST API (KHÔNG LAI TẠP GRAPHQL / WEB)
        // Lấy nguyên bản từ Instagram Engine (App Instagram 447 Android APK)
        // ========================================================================

        private fun executeFollow(
            session: InstagramSession,
            targetId: String,
            profileUrl: String,
            proxyConfig: ProxyConfig?
        ): IgActionResult {
            if (targetId.isBlank()) return IgActionResult(false, "Lỗi Target ID")
            val client = buildOkHttpClient(proxyConfig, timeoutSec = 15L)

            val formBody = FormBody.Builder()
                .add("user_id", targetId)
                .add("radio_type", "wifi-none")
                .build()

            val reqBuilder = Request.Builder()
                .url("https://i.instagram.com/api/v1/friendships/create/$targetId/")
                .post(formBody)
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Cookie", session.cookie)

            val csrf = Regex("csrftoken=([^;]+)").find(session.cookie)?.groupValues?.get(1).orEmpty()
            if (csrf.isNotBlank()) reqBuilder.header("X-CSRFToken", csrf)

            return try {
                val res = client.newCall(reqBuilder.build()).execute()
                val rawBody = res.body?.string().orEmpty()
                val json = try { JSONObject(rawBody) } catch (_: Exception) { null }
                val isOk = res.isSuccessful && (json?.optString("status") == "ok" || rawBody.contains("\"following\":true"))
                if (isOk) {
                    IgActionResult(true, "Theo dõi thành công", rawBody)
                } else {
                    val msg = json?.optString("message", "Lỗi gửi Follow") ?: "Lỗi gửi Follow"
                    IgActionResult(false, msg, rawBody)
                }
            } catch (e: Exception) {
                IgActionResult(false, e.message ?: "Lỗi gửi Follow", "")
            }
        }

        private fun executeTym(
            session: InstagramSession,
            mediaId: String,
            linkJob: String,
            proxyConfig: ProxyConfig?
        ): IgActionResult {
            if (mediaId.isBlank()) return IgActionResult(false, "Lỗi Media ID")
            val client = buildOkHttpClient(proxyConfig, timeoutSec = 15L)

            val formBody = FormBody.Builder()
                .add("media_id", mediaId)
                .add("radio_type", "wifi-none")
                .build()

            val reqBuilder = Request.Builder()
                .url("https://i.instagram.com/api/v1/media/$mediaId/like/")
                .post(formBody)
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Cookie", session.cookie)

            val csrf = Regex("csrftoken=([^;]+)").find(session.cookie)?.groupValues?.get(1).orEmpty()
            if (csrf.isNotBlank()) reqBuilder.header("X-CSRFToken", csrf)

            return try {
                val res = client.newCall(reqBuilder.build()).execute()
                val rawBody = res.body?.string().orEmpty()
                val json = try { JSONObject(rawBody) } catch (_: Exception) { null }
                val isOk = res.isSuccessful && json?.optString("status") == "ok"
                if (isOk) {
                    IgActionResult(true, "Thả tim thành công", rawBody)
                } else {
                    val msg = json?.optString("message", "Lỗi thả tim") ?: "Lỗi thả tim"
                    IgActionResult(false, msg, rawBody)
                }
            } catch (e: Exception) {
                IgActionResult(false, e.message ?: "Lỗi gửi Tym", "")
            }
        }

        private fun executeCmt(
            session: InstagramSession,
            mediaId: String,
            text: String,
            linkJob: String,
            proxyConfig: ProxyConfig?
        ): IgActionResult {
            if (mediaId.isBlank()) return IgActionResult(false, "Lỗi Media ID")
            val client = buildOkHttpClient(proxyConfig, timeoutSec = 15L)

            val formBody = FormBody.Builder()
                .add("comment_text", text)
                .add("radio_type", "wifi-none")
                .build()

            val reqBuilder = Request.Builder()
                .url("https://i.instagram.com/api/v1/media/$mediaId/comment/")
                .post(formBody)
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Cookie", session.cookie)

            val csrf = Regex("csrftoken=([^;]+)").find(session.cookie)?.groupValues?.get(1).orEmpty()
            if (csrf.isNotBlank()) reqBuilder.header("X-CSRFToken", csrf)

            return try {
                val res = client.newCall(reqBuilder.build()).execute()
                val rawBody = res.body?.string().orEmpty()
                val json = try { JSONObject(rawBody) } catch (_: Exception) { null }
                val isOk = res.isSuccessful && json?.optString("status") == "ok"
                if (isOk) {
                    IgActionResult(true, "Bình luận thành công", rawBody)
                } else {
                    val msg = json?.optString("message", "Lỗi gửi bình luận") ?: "Lỗi gửi bình luận"
                    IgActionResult(false, msg, rawBody)
                }
            } catch (e: Exception) {
                IgActionResult(false, e.message ?: "Lỗi gửi CMT", "")
            }
        }

        // ========================================================================
        // CÁC HÀM STATIC PHỤC VỤ TƯƠNG THÍCH CODE GỐC (KHÔNG LÀM HỎNG GIAO DIỆN)
        // ========================================================================

        fun follow(
            targetId: String,
            cookie: String,
            csrftoken: String = "",
            profileUrl: String = "",
            proxy: String? = null
        ): IgActionResult {
            val session = getOrCreateSession(cookie, proxyConfig = parseProxy(proxy))
            return executeFollow(session, targetId, profileUrl, parseProxy(proxy))
        }

        fun tym(
            mediaId: String,
            cookie: String,
            csrftoken: String = "",
            linkJob: String = "",
            proxy: String? = null
        ): IgActionResult {
            val session = getOrCreateSession(cookie, proxyConfig = parseProxy(proxy))
            return executeTym(session, mediaId, linkJob, parseProxy(proxy))
        }

        fun cmt(
            mediaId: String,
            text: String,
            cookie: String,
            csrftoken: String = "",
            linkJob: String = "",
            proxy: String? = null
        ): IgActionResult {
            val session = getOrCreateSession(cookie, proxyConfig = parseProxy(proxy))
            return executeCmt(session, mediaId, text, linkJob, parseProxy(proxy))
        }

        /**
         * ĐỔI ẢNH ĐẠI DIỆN CHUẨN 100% THEO INSTAGRAM ENGINE (APP REST API):
         * Endpoint: POST https://i.instagram.com/api/v1/accounts/change_profile_picture/
         */
        fun changeProfilePicture(
            imageBytes: ByteArray,
            cookie: String,
            proxy: String? = null
        ): String? {
            if (imageBytes.isEmpty()) return null
            val unquoted = unquoteCookie(cookie)
            val session = getOrCreateSession(unquoted, proxyConfig = parseProxy(proxy))
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 35L)

            val mediaType = "image/jpeg".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "profile_pic",
                    "profilepic.jpg",
                    imageBytes.toRequestBody(mediaType)
                )
                .build()

            val reqBuilder = Request.Builder()
                .url("https://i.instagram.com/api/v1/accounts/change_profile_picture/")
                .post(requestBody)
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Cookie", unquoted)

            val csrf = Regex("csrftoken=([^;]+)").find(unquoted)?.groupValues?.get(1).orEmpty()
            if (csrf.isNotBlank()) reqBuilder.header("X-CSRFToken", csrf)

            return try {
                val res = client.newCall(reqBuilder.build()).execute()
                val rawBody = res.body?.string().orEmpty()
                val body = cleanJsonResponse(rawBody)
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val directUrl = json?.optJSONObject("user")?.optString("profile_pic_url", "").orEmpty().ifBlank {
                    json?.optString("profile_pic_url", "").orEmpty().ifBlank {
                        json?.optString("profile_pic_url_hd", "").orEmpty()
                    }
                }
                if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                    session.profilePicUrl = directUrl
                    directUrl
                } else if (res.isSuccessful || rawBody.contains("\"status\":\"ok\"")) {
                    val actorId = session.actorId.ifBlank { extractActorId(unquoted) }
                    var freshPic: String? = null
                    if (actorId.isNotBlank() && actorId != "0") {
                        freshPic = fetchProfilePic(actorId, unquoted, proxy)
                    }
                    if (freshPic.isNullOrBlank() || !freshPic.startsWith("http")) {
                        val check = checkCookieIg(unquoted, proxy)
                        if (check.username.isNotBlank()) {
                            freshPic = fetchProfilePic(check.username, unquoted, proxy)
                        }
                    }
                    if (freshPic?.startsWith("http") == true) {
                        session.profilePicUrl = freshPic
                        freshPic
                    } else {
                        session.profilePicUrl.takeIf { it.startsWith("http") }
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        }

        /**
         * LẤY ẢNH ĐẠI DIỆN CHUẨN 100% THEO INSTAGRAM APP REST API:
         * 1. GET https://i.instagram.com/api/v1/users/{userId|username}/info/ (App API lấy HD Avatar)
         * 2. GET https://i.instagram.com/api/v1/users/web_profile_info/?username={username} (App REST Fallback)
         */
        fun fetchProfilePic(
            username: String,
            cookie: String,
            proxy: String? = null
        ): String? {
            val cleanUser = username.trim().removePrefix("@")
            if (cleanUser.isBlank()) return null
            val unquoted = normalizeToIosCookie(unquoteCookie(cookie))
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 15L)

            // 1. App REST API chuẩn lấy User Info & HD Avatar
            try {
                val req = Request.Builder()
                    .url("https://i.instagram.com/api/v1/users/$cleanUser/info/")
                    .get()
                    .header("User-Agent", IG_APP_UA)
                    .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                    .header("X-IG-Connection-Type", IG_CONN_TYPE)
                    .header("X-IG-Capabilities", IG_CAPABILITIES)
                    .header("Cookie", unquoted)
                    .build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val user = json?.optJSONObject("user")
                    val hdPic = user?.optJSONObject("hd_profile_pic_url_info")?.optString("url")
                    val regPic = user?.optString("profile_pic_url")
                    val pic = hdPic?.takeIf { it.isNotBlank() && it.startsWith("http") }
                        ?: regPic?.takeIf { it.isNotBlank() && it.startsWith("http") }
                    if (pic != null) return pic
                }
            } catch (_: Exception) {}

            // 2. App API Fallback: web_profile_info qua REST App Headers
            try {
                val req = Request.Builder()
                    .url("https://i.instagram.com/api/v1/users/web_profile_info/?username=$cleanUser")
                    .get()
                    .header("User-Agent", IG_APP_UA)
                    .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                    .header("X-IG-Connection-Type", IG_CONN_TYPE)
                    .header("X-IG-Capabilities", IG_CAPABILITIES)
                    .header("Cookie", unquoted)
                    .build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = cleanJsonResponse(res.body?.string().orEmpty())
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val userObj = json?.optJSONObject("data")?.optJSONObject("user")
                    val pic = userObj?.optString("profile_pic_url_hd").orEmpty().ifBlank {
                        userObj?.optString("profile_pic_url").orEmpty()
                    }
                    if (pic.isNotBlank() && pic.startsWith("http")) {
                        return pic
                    }
                }
            } catch (_: Exception) {}

            return null
        }

        /**
         * LẤY AVATAR HD VÀ THÔNG TIN PROFILE (100% INSTAGRAM APP REST API):
         * Endpoint: GET https://i.instagram.com/api/v1/users/{userId}/info/
         */
        fun getAvatar(
            targetUserId: String,
            cookie: String,
            proxy: String? = null
        ): InstagramUserInfo? {
            val unquoted = normalizeToIosCookie(unquoteCookie(cookie))
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 15L)

            val req = Request.Builder()
                .url("https://i.instagram.com/api/v1/users/$targetUserId/info/")
                .get()
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Cookie", unquoted)
                .build()

            return try {
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val user = json?.optJSONObject("user") ?: return null
                    val uname = user.optString("username", "")
                    val fName = user.optString("full_name", "")
                    val bio = user.optString("biography", "")
                    val hdPic = user.optJSONObject("hd_profile_pic_url_info")?.optString("url")
                    val regPic = user.optString("profile_pic_url")
                    val pic = hdPic?.takeIf { it.isNotBlank() && it.startsWith("http") } ?: regPic
                    val followers = user.optInt("follower_count", 0)
                    val following = user.optInt("following_count", 0)
                    val posts = user.optInt("media_count", 0)

                    InstagramUserInfo(
                        username = uname,
                        userId = targetUserId,
                        fullName = fName,
                        profilePicUrl = pic,
                        biography = bio,
                        followersCount = followers,
                        followingCount = following,
                        postsCount = posts,
                        isLive = true
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * XÓA ẢNH ĐẠI DIỆN CHUẨN 100% THEO INSTAGRAM APP REST API:
         * Endpoint: POST https://i.instagram.com/api/v1/accounts/remove_profile_picture/
         */
        fun removeAvatar(cookie: String, proxy: String? = null): Boolean {
            val unquoted = normalizeToIosCookie(unquoteCookie(cookie))
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 15L)
            val formBody = FormBody.Builder().build()
            val reqBuilder = Request.Builder()
                .url("https://i.instagram.com/api/v1/accounts/remove_profile_picture/")
                .post(formBody)
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Cookie", unquoted)

            val csrf = Regex("csrftoken=([^;]+)").find(unquoted)?.groupValues?.get(1).orEmpty()
            if (csrf.isNotBlank()) reqBuilder.header("X-CSRFToken", csrf)

            return try {
                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    res.isSuccessful || body.contains("\"status\":\"ok\"")
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * KIỂM TRA ĐỘ SỐNG CỦA COOKIE THEO 100% INSTAGRAM APP REST API:
         * 1. GET https://i.instagram.com/api/v1/users/{userId}/info/ (Lấy Full Profile, Followers, Avatar HD)
         * 2. GET https://i.instagram.com/api/v1/accounts/current_user/?edit=true (Lấy Email, Phone, First Name)
         * Loại bỏ 100% Web GraphQL Polaris & web_form_data.
         */
        fun checkCookieIg(cookie: String, proxy: String? = null): CookieCheckResult {
            val unquoted = normalizeToIosCookie(unquoteCookie(cookie))
            val session = getOrCreateSession(unquoted, proxyConfig = parseProxy(proxy))
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 20L)
            val actorId = session.actorId.ifBlank { extractActorId(unquoted) }

            // 1. Kiểm tra bằng App REST API: /api/v1/users/{actorId}/info/
            if (actorId.isNotBlank() && actorId != "0") {
                try {
                    val req = Request.Builder()
                        .url("https://i.instagram.com/api/v1/users/$actorId/info/")
                        .get()
                        .header("User-Agent", session.deviceProfile.userAgent)
                        .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                        .header("X-IG-Connection-Type", IG_CONN_TYPE)
                        .header("X-IG-Capabilities", IG_CAPABILITIES)
                        .header("Cookie", unquoted)
                        .build()

                    val res = client.newCall(req).execute()
                    val rawBody = res.body?.string().orEmpty()
                    val body = cleanJsonResponse(rawBody)

                    if (body.contains("checkpoint_required") || body.contains("accounts/suspended") || body.contains("1357031")) {
                        val isSuspended = body.contains("accounts/suspended") || body.contains("1357031")
                        val msg = if (isSuspended) "Tài khoản bị tạm khóa / Checkpoint (accounts/suspended)" else "Checkpoint / Xác minh danh tính"
                        return CookieCheckResult(isLive = false, userId = actorId, rawJson = msg)
                    }
                    if (body.contains("login_required") || res.code == 401 || res.code == 403) {
                        return CookieCheckResult(isLive = false, userId = actorId, rawJson = "Cookie hết hạn / Cần đăng nhập lại")
                    }

                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val user = json?.optJSONObject("user")
                    val username = user?.optString("username").orEmpty()
                    if (username.isNotBlank()) {
                        val fullName = user?.optString("full_name").orEmpty()
                        val bio = user?.optString("biography").orEmpty()
                        val hdPic = user?.optJSONObject("hd_profile_pic_url_info")?.optString("url")
                        val regPic = user?.optString("profile_pic_url").orEmpty()
                        val pic = hdPic?.takeIf { it.isNotBlank() && it.startsWith("http") } ?: regPic
                        val followers = user?.optInt("follower_count") ?: 0
                        val following = user?.optInt("following_count") ?: 0
                        val posts = user?.optInt("media_count") ?: 0

                        if (pic.isNotBlank() && pic.startsWith("http")) {
                            session.profilePicUrl = pic
                        }

                        return CookieCheckResult(
                            isLive = true,
                            username = username,
                            userId = actorId,
                            fullName = fullName,
                            biography = bio,
                            profilePicUrl = pic,
                            rawJson = body,
                            followersCount = followers,
                            followingCount = following,
                            postsCount = posts
                        )
                    }
                } catch (_: Exception) {}
            }

            // 2. Fallback App API: /api/v1/accounts/current_user/?edit=true
            try {
                val req = Request.Builder()
                    .url("https://i.instagram.com/api/v1/accounts/current_user/?edit=true")
                    .get()
                    .header("User-Agent", session.deviceProfile.userAgent)
                    .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                    .header("X-IG-Connection-Type", IG_CONN_TYPE)
                    .header("X-IG-Capabilities", IG_CAPABILITIES)
                    .header("Cookie", unquoted)
                    .build()

                val res = client.newCall(req).execute()
                val rawBody = res.body?.string().orEmpty()
                val body = cleanJsonResponse(rawBody)

                if (body.contains("checkpoint_required") || body.contains("accounts/suspended") || body.contains("1357031")) {
                    val isSuspended = body.contains("accounts/suspended") || body.contains("1357031")
                    val msg = if (isSuspended) "Tài khoản bị tạm khóa / Checkpoint (accounts/suspended)" else "Checkpoint / Xác minh danh tính"
                    return CookieCheckResult(isLive = false, userId = actorId, rawJson = msg)
                }
                if (body.contains("login_required") || res.code == 401 || res.code == 403) {
                    return CookieCheckResult(isLive = false, userId = actorId, rawJson = "Cookie hết hạn / Cần đăng nhập lại")
                }

                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val user = json?.optJSONObject("user")
                val username = user?.optString("username").orEmpty()
                if (username.isNotBlank()) {
                    val fullName = user?.optString("first_name").orEmpty().ifBlank { user?.optString("full_name").orEmpty() }
                    val bio = user?.optString("biography").orEmpty()
                    val email = user?.optString("email").orEmpty()
                    val phone = user?.optString("phone_number").orEmpty()
                    val pic = user?.optString("profile_pic_url").orEmpty()
                    val uid = user?.optString("pk").orEmpty().ifBlank { actorId }

                    if (pic.isNotBlank() && pic.startsWith("http")) {
                        session.profilePicUrl = pic
                    }

                    return CookieCheckResult(
                        isLive = true,
                        username = username,
                        userId = uid,
                        fullName = fullName,
                        biography = bio,
                        email = email,
                        phoneNumber = phone,
                        profilePicUrl = pic,
                        rawJson = body
                    )
                }
            } catch (_: Exception) {}

            return CookieCheckResult(isLive = false, userId = actorId, rawJson = "Không thể xác thực cookie Instagram")
        }


        fun resolveTargetUserId(linkJob: String, proxy: String? = null): String? {
            if (linkJob.isBlank()) return null
            val clean = linkJob.trim()
            val usernameCandidate = if (!clean.startsWith("http")) {
                clean.removePrefix("@").trim()
            } else {
                val m = Pattern.compile("instagram\\.com/([a-zA-Z0-9._]+)").matcher(clean)
                if (m.find()) m.group(1) else null
            }

            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 10L)

            // 1. Ưu tiên tra cứu UID bằng App REST API
            if (!usernameCandidate.isNullOrBlank() && !usernameCandidate.equals("p", true) && !usernameCandidate.equals("reel", true)) {
                try {
                    val req = Request.Builder()
                        .url("https://i.instagram.com/api/v1/users/web_profile_info/?username=$usernameCandidate")
                        .get()
                        .header("User-Agent", IG_APP_UA)
                        .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                        .header("X-IG-Connection-Type", IG_CONN_TYPE)
                        .header("X-IG-Capabilities", IG_CAPABILITIES)
                        .build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val body = cleanJsonResponse(res.body?.string().orEmpty())
                        val json = try { JSONObject(body) } catch (_: Exception) { null }
                        val uid = json?.optJSONObject("data")?.optJSONObject("user")?.optString("id")
                        if (!uid.isNullOrBlank()) return uid
                    }
                } catch (_: Exception) {}
            }

            // 2. Fallback tra cứu bằng App REST API /api/v1/users/{clean}/info/
            if (!clean.startsWith("http")) {
                try {
                    val req = Request.Builder()
                        .url("https://i.instagram.com/api/v1/users/$clean/info/")
                        .get()
                        .header("User-Agent", IG_APP_UA)
                        .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                        .header("X-IG-Connection-Type", IG_CONN_TYPE)
                        .header("X-IG-Capabilities", IG_CAPABILITIES)
                        .build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val body = cleanJsonResponse(res.body?.string().orEmpty())
                        val json = try { JSONObject(body) } catch (_: Exception) { null }
                        val uid = json?.optJSONObject("user")?.optString("pk")
                        if (!uid.isNullOrBlank()) return uid
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback trích xuất UID từ link nếu truyền vào dạng URL
            if (clean.startsWith("http")) {
                return try {
                    val req = Request.Builder()
                        .url(clean)
                        .addHeader("User-Agent", IG_APP_UA)
                        .get()
                        .build()
                    val html = client.newCall(req).execute().body?.string().orEmpty()

                    var m = Pattern.compile("\"profile_id\":\"(\\d+)\"").matcher(html)
                    if (m.find()) return m.group(1)

                    m = Pattern.compile("\"user_id\":\"(\\d+)\"").matcher(html)
                    if (m.find()) return m.group(1)

                    m = Pattern.compile("profilePage_(\\d+)").matcher(html)
                    if (m.find()) return m.group(1)

                    m = Pattern.compile("\"id\":\"(\\d{5,})\"").matcher(html)
                    if (m.find()) return m.group(1)

                    null
                } catch (_: Exception) {
                    null
                }
            }

            return null
        }

        fun resolveMediaId(linkJob: String, proxy: String? = null): String? {
            if (linkJob.isBlank()) return null
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 10L)
            return try {
                val req = Request.Builder()
                    .url(linkJob)
                    .addHeader("User-Agent", IG_APP_UA)
                    .get()
                    .build()
                val html = client.newCall(req).execute().body?.string().orEmpty()

                var m = Pattern.compile("instagram://media\\?id=(\\d+)").matcher(html)
                if (m.find()) return m.group(1)

                m = Pattern.compile("\"media_id\":\"(\\d+)\"").matcher(html)
                if (m.find()) return m.group(1)

                m = Pattern.compile("media\\?id=(\\d+)").matcher(html)
                if (m.find()) return m.group(1)

                null
            } catch (_: Exception) {
                null
            }
        }

        fun cleanJsonResponse(raw: String): String {
            val s = raw.trim()
            val firstBrace = s.indexOf('{')
            val lastBrace = s.lastIndexOf('}')
            if (firstBrace in 0..lastBrace) {
                return s.substring(firstBrace, lastBrace + 1)
            }
            return s
        }

        fun extractTokensFromHtml(html: String, fallbackLsd: String = "", fallbackJazoest: String = ""): Triple<String, String, String> {
            var lsd = fallbackLsd
            var m = Pattern.compile("\"LSD\",\\[],\\{\"token\":\"([^\"]+)\"}").matcher(html)
            if (m.find()) {
                lsd = m.group(1)
            } else {
                m = Pattern.compile("\"lsd\":\\{\"token\":\"([^\"]+)\"").matcher(html)
                if (m.find()) {
                    lsd = m.group(1)
                } else {
                    m = Pattern.compile("name=\"lsd\" value=\"([^\"]+)\"").matcher(html)
                    if (m.find()) lsd = m.group(1)
                }
            }

            var fbDtsg = ""
            m = Pattern.compile("\"DTSGInitialData\",\\[],\\{\"token\":\"([^\"]+)\"}").matcher(html)
            if (m.find()) {
                fbDtsg = m.group(1)
            } else {
                m = Pattern.compile("\"DTSGInitData\",\\[],\\{\"token\":\"([^\"]+)\"}").matcher(html)
                if (m.find()) {
                    fbDtsg = m.group(1)
                } else {
                    m = Pattern.compile("\"dtsg\":\\{\"token\":\"([^\"]+)\"").matcher(html)
                    if (m.find()) {
                        fbDtsg = m.group(1)
                    } else {
                        m = Pattern.compile("name=\"fb_dtsg\" value=\"([^\"]+)\"").matcher(html)
                        if (m.find()) {
                            fbDtsg = m.group(1)
                        } else {
                            m = Pattern.compile("(NA[a-zA-Z0-9_-]+:[0-9]+:[0-9]+)").matcher(html)
                            if (m.find()) {
                                fbDtsg = m.group(1)
                            }
                        }
                    }
                }
            }

            var jazoest = fallbackJazoest
            m = Pattern.compile("name=\"jazoest\" value=\"(\\d+)\"").matcher(html)
            if (m.find()) {
                jazoest = m.group(1)
            } else {
                m = Pattern.compile("\"jazoest\":\"?(\\d+)\"?").matcher(html)
                if (m.find()) {
                    jazoest = m.group(1)
                }
            }

            return Triple(lsd, fbDtsg, jazoest)
        }

        /**
         * CHUẨN HÓA COOKIE CHO INSTAGRAM APP NATIVE:
         * - Tự động trích xuất ds_user_id từ sessionid nếu cookie nguồn thiếu ds_user_id.
         * - Giữ nguyên toàn bộ token xác thực phiên App: sessionid, ds_user_id, csrftoken, mid, ig_did, datr, rur,...
         */
        fun normalizeToIosCookie(rawCookie: String): String {
            if (rawCookie.isBlank()) return ""
            var cleaned = rawCookie.trim()
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length > 1) {
                cleaned = cleaned.substring(1, cleaned.length - 1).trim()
            }
            if (cleaned.startsWith("'") && cleaned.endsWith("'") && cleaned.length > 1) {
                cleaned = cleaned.substring(1, cleaned.length - 1).trim()
            }

            val cookiePairs = cleaned.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() && it.contains("=") }

            val cookieMap = LinkedHashMap<String, String>()
            for (pair in cookiePairs) {
                val eqIdx = pair.indexOf('=')
                if (eqIdx > 0) {
                    val key = pair.substring(0, eqIdx).trim()
                    val value = pair.substring(eqIdx + 1).trim()
                    cookieMap[key] = value
                }
            }

            // Nếu thiếu ds_user_id nhưng có sessionid chứa UID phía trước (vd: 32847204267%3A... hoặc 32847204267:...)
            if (!cookieMap.containsKey("ds_user_id") && cookieMap.containsKey("sessionid")) {
                val sVal = cookieMap["sessionid"].orEmpty()
                val potentialUid = sVal.substringBefore("%3A").substringBefore(":")
                if (potentialUid.isNotEmpty() && potentialUid.all { it.isDigit() }) {
                    cookieMap["ds_user_id"] = potentialUid
                }
            }

            return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        private fun unquoteCookie(cookie: String): String {
            return try {
                val pattern = Pattern.compile("%([0-9a-fA-F]{2})")
                val matcher = pattern.matcher(cookie)
                val sb = StringBuffer()
                while (matcher.find()) {
                    val hex = matcher.group(1)
                    val ch = hex.toInt(16).toChar()
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(ch.toString()))
                }
                matcher.appendTail(sb)
                sb.toString()
            } catch (_: Exception) {
                cookie
            }
        }

        private fun extractCsrfToken(cookie: String): String {
            val matcher = Pattern.compile("csrftoken=([^;\\s]+)").matcher(cookie)
            return if (matcher.find()) matcher.group(1).removeSurrounding("\"") else "missing"
        }

        private fun extractActorId(cookie: String): String {
            val matcher = Pattern.compile("ds_user_id=(\\d+)").matcher(cookie)
            if (matcher.find()) return matcher.group(1).orEmpty()
            val rurMatcher = Pattern.compile("rur=[^,]+,(\\d+)").matcher(cookie)
            if (rurMatcher.find()) return rurMatcher.group(1).orEmpty()
            return "0"
        }

        fun parseProxy(proxyStr: String?): ProxyConfig? {
            if (proxyStr.isNullOrBlank()) return null
            var s = proxyStr.trim()
            if (s.isBlank()) return null
            var proxyType = Proxy.Type.HTTP
            if (s.contains("://")) {
                val split = s.split("://", limit = 2)
                if (split[0].lowercase().contains("socks")) {
                    proxyType = Proxy.Type.SOCKS
                }
                s = split[1]
            }
            return try {
                if (s.contains("@")) {
                    val atParts = s.split("@", limit = 2)
                    val auth = atParts[0].split(":", limit = 2)
                    val hostPort = atParts[1].split(":", limit = 2)
                    ProxyConfig(
                        host = hostPort[0],
                        port = hostPort[1].toInt(),
                        username = auth.getOrNull(0),
                        password = auth.getOrNull(1),
                        type = proxyType
                    )
                } else {
                    val parts = s.split(":")
                    if (parts.size == 4) {
                        ProxyConfig(
                            host = parts[0],
                            port = parts[1].toInt(),
                            username = parts[2],
                            password = parts[3],
                            type = proxyType
                        )
                    } else if (parts.size == 2) {
                        ProxyConfig(
                            host = parts[0],
                            port = parts[1].toInt(),
                            type = proxyType
                        )
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }

        private val sharedConnectionPool = ConnectionPool(15, 5, TimeUnit.MINUTES)
        private val clientMap = ConcurrentHashMap<String, OkHttpClient>()

        fun buildOkHttpClient(proxyConfig: ProxyConfig? = null, timeoutSec: Long = 25L): OkHttpClient {
            val key = "${proxyConfig?.type}:${proxyConfig?.host}:${proxyConfig?.port}:${proxyConfig?.username}_$timeoutSec"
            return clientMap.getOrPut(key) {
                val builder = OkHttpClient.Builder()
                    .connectionPool(sharedConnectionPool)
                    .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                    .readTimeout(timeoutSec, TimeUnit.SECONDS)
                    .writeTimeout(timeoutSec, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)

                try {
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    })
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    builder.hostnameVerifier { _, _ -> true }
                } catch (_: Exception) {}

                if (proxyConfig != null && proxyConfig.host.isNotBlank() && proxyConfig.port > 0) {
                    val proxy = Proxy(proxyConfig.type, InetSocketAddress(proxyConfig.host, proxyConfig.port))
                    builder.proxy(proxy)
                    if (!proxyConfig.username.isNullOrBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) {
                                null
                            } else {
                                val credential = Credentials.basic(proxyConfig.username, proxyConfig.password.orEmpty())
                                response.request.newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build()
                            }
                        }
                    }
                }
                builder.build()
            }
        }

        fun unescapeUnicode(str: String): String {
            if (!str.contains("\\u")) return str
            val regex = Regex("""\\u([0-9a-fA-F]{4})""")
            return regex.replace(str) { matchResult ->
                try {
                    matchResult.groupValues[1].toInt(16).toChar().toString()
                } catch (_: Exception) {
                    matchResult.value
                }
            }
        }



        /**
         * Lấy khóa mã hóa mật khẩu — 100% từ InstagramEngine gốc.
         * POST /api/v1/qe/sync/ (body rỗng) → header ig-set-password-encryption-pub-key
         */
        fun fetchPasswordEncryptionKey(client: OkHttpClient): Pair<Int, String>? {
            val endpoints = listOf(
                "https://i.instagram.com/api/v1/qe/sync/",
                "https://i.instagram.com/api/v2/qe/sync/"
            )
            for (ep in endpoints) {
                try {
                    val req = Request.Builder()
                        .url(ep)
                        .post(FormBody.Builder().build())
                        .header("User-Agent", IG_APP_UA)
                        .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                        .header("X-IG-Connection-Type", IG_CONN_TYPE)
                        .header("X-IG-Capabilities", IG_CAPABILITIES)
                        .build()
                    client.newCall(req).execute().use { res ->
                        val pubKey   = res.header("ig-set-password-encryption-pub-key")
                        val keyIdStr = res.header("ig-set-password-encryption-key-id")
                        if (!pubKey.isNullOrEmpty() && !keyIdStr.isNullOrEmpty()) {
                            return Pair(keyIdStr.toInt(), pubKey)
                        }
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        /**
         * Mã hóa mật khẩu theo chuẩn Meta Hybrid RSA + AES-256-GCM (#PWD_INSTAGRAM:4:).
         * Đóng gói nhị phân: [1][key_id][12B IV][2B length][RSA Encrypted AES Key][16B Tag][Ciphertext]
         */
        fun encryptPassword(password: String, keyId: Int, publicKeyBase64OrPem: String): String {
            val timestampSeconds = System.currentTimeMillis() / 1000L
            val timestampStr = timestampSeconds.toString()

            val random = java.security.SecureRandom()
            val aesKeyBytes = ByteArray(32).also { random.nextBytes(it) }
            val ivBytes = ByteArray(12).also { random.nextBytes(it) }

            var pemText = publicKeyBase64OrPem.trim()
            if (!pemText.contains("-----BEGIN")) {
                try {
                    val decodedBytes = android.util.Base64.decode(pemText, android.util.Base64.DEFAULT)
                    val decodedStr = String(decodedBytes, Charsets.UTF_8)
                    if (decodedStr.contains("-----BEGIN")) {
                        pemText = decodedStr
                    }
                } catch (_: Exception) {}
            }

            val cleanKey = pemText
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim()
            val keyBytes = android.util.Base64.decode(cleanKey, android.util.Base64.DEFAULT)
            val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
            val rsaPublicKey = java.security.KeyFactory.getInstance("RSA").generatePublic(keySpec)

            val rsaCipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, rsaPublicKey)
            val encryptedAesKey = rsaCipher.doFinal(aesKeyBytes)

            val gcmCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES")
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, ivBytes)
            gcmCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            gcmCipher.updateAAD(timestampStr.toByteArray(Charsets.UTF_8))

            val gcmCiphertextAndTag = gcmCipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val tagLength = 16
            val ciphertextLength = gcmCiphertextAndTag.size - tagLength
            val tagBytes = gcmCiphertextAndTag.copyOfRange(ciphertextLength, gcmCiphertextAndTag.size)
            val ciphertextBytes = gcmCiphertextAndTag.copyOfRange(0, ciphertextLength)

            val output = java.io.ByteArrayOutputStream()
            output.write(1)
            output.write(keyId)
            output.write(ivBytes)
            output.write(java.nio.ByteBuffer.allocate(2).order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort(encryptedAesKey.size.toShort()).array())
            output.write(encryptedAesKey)
            output.write(tagBytes)
            output.write(ciphertextBytes)

            val b64Payload = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
            return "#PWD_INSTAGRAM:4:$timestampStr:$b64Payload"
        }

        /**
         * Đăng nhập Instagram — 100% Instagram App REST API.
         * Ưu tiên: CAA Bloks login → nếu fail → REST /accounts/login/
         * Toàn bộ đều dùng i.instagram.com, KHÔNG dùng www.instagram.com.
         * Hiển thị chính xác lỗi từ Instagram trả về.
         */
        fun loginWithCredentials(
            usernameInput: String,
            passwordRaw: String,
            twoFaSecret: String? = null,
            proxy: String? = null
        ): IgLoginResult {
            val proxyConfig = parseProxy(proxy)
            val username = usernameInput.trim()

            val cookieStore = ConcurrentHashMap<String, String>()
            val client = buildOkHttpClient(proxyConfig, timeoutSec = 30L).newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .cookieJar(object : CookieJar {
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        for (c in cookies) cookieStore[c.name] = c.value
                    }
                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        return cookieStore.map { (name, value) ->
                            Cookie.Builder().domain(url.host).name(name).value(value).build()
                        }
                    }
                })
                .build()

            // 1. Lấy khóa mã hóa mật khẩu từ i.instagram.com/api/v2/qe/sync/
            val keyInfo = fetchPasswordEncryptionKey(client)
            val encPassword = if (keyInfo != null) {
                try {
                    encryptPassword(passwordRaw, keyInfo.first, keyInfo.second)
                } catch (_: Exception) {
                    "#PWD_INSTAGRAM:0:${System.currentTimeMillis() / 1000L}:$passwordRaw"
                }
            } else {
                "#PWD_INSTAGRAM:0:${System.currentTimeMillis() / 1000L}:$passwordRaw"
            }

            fun buildFinalCookie(): String {
                val sb = StringBuilder()
                cookieStore.forEach { (k, v) -> sb.append("$k=$v; ") }
                return normalizeToIosCookie(sb.toString())
            }

            // ─────────────────────────────────────────────────────────────────
            // BƯỚC 2a: CAA Bloks Login (ưu tiên hàng đầu — đúng engine mới)
            // ─────────────────────────────────────────────────────────────────
            val waterfallId  = java.util.UUID.randomUUID().toString()
            val familyDevId  = java.util.UUID.randomUUID().toString()
            val deviceId     = "android-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)

            val caaParams = JSONObject().apply {
                put("client_input_params", JSONObject().apply {
                    put("contact_point", username)
                    put("password", encPassword)
                    put("login_source", "Login")
                    put("device_id", deviceId)
                    put("family_device_id", familyDevId)
                    put("waterfall_id", waterfallId)
                    put("access_flow_version", "F2_FLOW")
                    put("credential_type", "password")
                    put("headers_flow_mode", "regular")
                })
                put("server_params", JSONObject().apply {
                    put("server_login_source", "Login")
                    put("credential_type", "password")
                })
            }

            val caaBody = FormBody.Builder()
                .add("params", caaParams.toString())
                .add("bk_client_context", "{\"bk_client_context\":{}}")
                .add("bloks_versioning_id", "45a55ce624fec64fefb3d8756c602dc8992c3a37b12d5930263f35c6e8e8ce4a")
                .build()

            val caaReq = Request.Builder()
                .url("$BASE_URL/bloks/async_action/com.bloks.www.bloks.caa.login.async.send_login_request/")
                .post(caaBody)
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-FB-Friendly-Name", "send_login_request")
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Accept-Language", "vi-VN, en-US")
                .build()

            val caaRaw = try {
                client.newCall(caaReq).execute().use { it.body?.string() ?: "" }
            } catch (_: Exception) { "" }

            if (caaRaw.isNotBlank()) {
                val sessionId = cookieStore["sessionid"]
                val uid = Regex("""["'](?:user_id|pk|actor_id)["']\s*:\s*["']?(\d+)["']?""").find(caaRaw)?.groupValues?.get(1)
                    ?: cookieStore["ds_user_id"] ?: ""

                val caaCheckpoint = caaRaw.contains("checkpoint_required") || caaRaw.contains("checkpoint_url")
                val caaTwoFactor  = caaRaw.contains("two_factor_required") || caaRaw.contains("two_factor_info")
                val caaSuccess    = !sessionId.isNullOrEmpty() || uid.isNotEmpty()

                if (caaSuccess && !caaCheckpoint && !caaTwoFactor) {
                    val finalCookie = buildFinalCookie()
                    val checkInfo = try { checkCookieIg(finalCookie, proxy) } catch (_: Exception) { CookieCheckResult(isLive = true) }
                    return IgLoginResult(
                        isSuccess = true,
                        userId = uid.ifBlank { checkInfo.userId },
                        username = checkInfo.username.ifBlank { username },
                        fullName = checkInfo.fullName,
                        cookie = finalCookie,
                        avatarUrl = checkInfo.profilePicUrl,
                        message = "Đăng nhập thành công",
                        rawResponse = caaRaw
                    )
                }

                if (caaCheckpoint) {
                    // Trả về lỗi chính xác từ Instagram
                    val errMsg = try {
                        val j = JSONObject(caaRaw)
                        j.optString("message", "").ifBlank {
                            j.optJSONObject("challenge")?.optString("api_path", "") ?: ""
                        }.ifBlank { "Tài khoản bị checkpoint. Vui lòng xác minh tại Instagram." }
                    } catch (_: Exception) { "Tài khoản bị checkpoint. Vui lòng xác minh tại Instagram." }
                    return IgLoginResult(isSuccess = false, message = errMsg, rawResponse = caaRaw)
                }

                if (caaTwoFactor) {
                    val caaJson = try { JSONObject(caaRaw) } catch (_: Exception) { null }
                    val twoFaInfo = caaJson?.optJSONObject("two_factor_info")
                    val identifier = twoFaInfo?.optString("two_factor_identifier").orEmpty()
                    val secretClean = twoFaSecret?.trim().orEmpty()

                    if (secretClean.isNotBlank()) {
                        val otpCode = if (secretClean.length == 6 && secretClean.all { it.isDigit() }) secretClean
                                      else TotpGenerator.generateTotp(secretClean)

                        if (otpCode.isNotBlank()) {
                            val twoFaBody = FormBody.Builder()
                                .add("username", username)
                                .add("verification_code", otpCode)
                                .add("two_factor_identifier", identifier)
                                .add("trust_this_device", "1")
                                .add("device_id", deviceId)
                                .build()

                            val twoFaReq = Request.Builder()
                                .url("$BASE_URL/accounts/two_factor_login/")
                                .post(twoFaBody)
                                .header("User-Agent", IG_APP_UA)
                                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                                .header("X-IG-Capabilities", IG_CAPABILITIES)
                                .build()

                            val twoFaRaw = try {
                                client.newCall(twoFaReq).execute().use { it.body?.string() ?: "" }
                            } catch (e: Exception) {
                                return IgLoginResult(isSuccess = false, isTwoFactorRequired = true,
                                    message = "Lỗi kết nối 2FA: ${e.message}")
                            }

                            val twoFaJson = try { JSONObject(twoFaRaw) } catch (_: Exception) { null }
                            val loggedIn2fa = twoFaJson?.optJSONObject("logged_in_user")
                            if (loggedIn2fa != null) {
                                val finalCookie = buildFinalCookie()
                                val checkInfo = try { checkCookieIg(finalCookie, proxy) } catch (_: Exception) { CookieCheckResult(isLive = true) }
                                return IgLoginResult(
                                    isSuccess = true,
                                    userId = loggedIn2fa.optString("pk", "").ifBlank { checkInfo.userId },
                                    username = loggedIn2fa.optString("username", "").ifBlank { checkInfo.username }.ifBlank { username },
                                    fullName = checkInfo.fullName,
                                    cookie = finalCookie,
                                    avatarUrl = loggedIn2fa.optString("profile_pic_url", "").ifBlank { checkInfo.profilePicUrl },
                                    message = "Xác thực 2FA thành công",
                                    rawResponse = twoFaRaw
                                )
                            }
                            // Hiển thị lỗi chính xác từ Instagram
                            val err2fa = twoFaJson?.optString("message", "").orEmpty()
                                .ifBlank { "Mã 2FA không chính xác hoặc đã hết hạn" }
                            return IgLoginResult(isSuccess = false, isTwoFactorRequired = true,
                                message = err2fa, rawResponse = twoFaRaw)
                        }
                    }
                    return IgLoginResult(isSuccess = false, isTwoFactorRequired = true,
                        message = "Tài khoản yêu cầu mã 2FA. Vui lòng nhập 2FA Secret hoặc OTP 6 số!")
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // BƯỚC 2b: REST Login fallback — i.instagram.com/api/v1/accounts/login/
            // ─────────────────────────────────────────────────────────────────
            val phoneId = java.util.UUID.randomUUID().toString()
            val guid    = java.util.UUID.randomUUID().toString()

            val restBody = FormBody.Builder()
                .add("username", username)
                .add("enc_password", encPassword)
                .add("device_id", deviceId)
                .add("phone_id", phoneId)
                .add("guid", guid)
                .add("login_attempt_count", "0")
                .build()

            val restReq = Request.Builder()
                .url("$BASE_URL/accounts/login/")
                .post(restBody)
                .header("User-Agent", IG_APP_UA)
                .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                .header("X-IG-Connection-Type", IG_CONN_TYPE)
                .header("X-IG-Capabilities", IG_CAPABILITIES)
                .header("Accept-Language", "vi-VN, en-US")
                .build()

            val restRaw: String
            val restJson: JSONObject?
            try {
                val restRes = client.newCall(restReq).execute()
                restRaw  = restRes.body?.string().orEmpty()
                restJson = try { JSONObject(restRaw) } catch (_: Exception) { null }
            } catch (e: Exception) {
                return IgLoginResult(isSuccess = false, message = "Lỗi kết nối: ${e.message}")
            }

            val loggedInUser = restJson?.optJSONObject("logged_in_user")
            val isTwoFactor  = restJson?.optBoolean("two_factor_required", false) == true

            if (loggedInUser != null) {
                val finalCookie = buildFinalCookie()
                val checkInfo = try { checkCookieIg(finalCookie, proxy) } catch (_: Exception) { CookieCheckResult(isLive = true) }
                return IgLoginResult(
                    isSuccess = true,
                    userId = loggedInUser.optString("pk", "").ifBlank { checkInfo.userId },
                    username = loggedInUser.optString("username", "").ifBlank { checkInfo.username }.ifBlank { username },
                    fullName = loggedInUser.optString("full_name", "").ifBlank { checkInfo.fullName },
                    cookie = finalCookie,
                    avatarUrl = loggedInUser.optString("profile_pic_url", "").ifBlank { checkInfo.profilePicUrl },
                    message = "Đăng nhập thành công",
                    rawResponse = restRaw
                )
            }

            if (isTwoFactor) {
                val twoFaInfo   = restJson?.optJSONObject("two_factor_info")
                val identifier  = twoFaInfo?.optString("two_factor_identifier").orEmpty()
                val secretClean = twoFaSecret?.trim().orEmpty()

                if (secretClean.isNotBlank()) {
                    val otpCode = if (secretClean.length == 6 && secretClean.all { it.isDigit() }) secretClean
                                  else TotpGenerator.generateTotp(secretClean)

                    if (otpCode.isNotBlank()) {
                        val twoFaBody = FormBody.Builder()
                            .add("username", username)
                            .add("verification_code", otpCode)
                            .add("two_factor_identifier", identifier)
                            .add("trust_this_device", "1")
                            .add("device_id", deviceId)
                            .add("guid", guid)
                            .build()

                        val twoFaReq = Request.Builder()
                            .url("$BASE_URL/accounts/two_factor_login/")
                            .post(twoFaBody)
                            .header("User-Agent", IG_APP_UA)
                            .header("X-IG-App-ID", IG_APP_ID_PRIVATE)
                            .header("X-IG-Connection-Type", IG_CONN_TYPE)
                            .header("X-IG-Capabilities", IG_CAPABILITIES)
                            .build()

                        val twoFaRaw = try {
                            client.newCall(twoFaReq).execute().use { it.body?.string() ?: "" }
                        } catch (e: Exception) {
                            return IgLoginResult(isSuccess = false, isTwoFactorRequired = true,
                                message = "Lỗi kết nối 2FA: ${e.message}")
                        }

                        val twoFaJson   = try { JSONObject(twoFaRaw) } catch (_: Exception) { null }
                        val loggedIn2fa = twoFaJson?.optJSONObject("logged_in_user")
                        if (loggedIn2fa != null) {
                            val finalCookie = buildFinalCookie()
                            val checkInfo = try { checkCookieIg(finalCookie, proxy) } catch (_: Exception) { CookieCheckResult(isLive = true) }
                            return IgLoginResult(
                                isSuccess = true,
                                userId = loggedIn2fa.optString("pk", "").ifBlank { checkInfo.userId },
                                username = loggedIn2fa.optString("username", "").ifBlank { username },
                                fullName = loggedIn2fa.optString("full_name", "").ifBlank { checkInfo.fullName },
                                cookie = finalCookie,
                                avatarUrl = loggedIn2fa.optString("profile_pic_url", "").ifBlank { checkInfo.profilePicUrl },
                                message = "Xác thực 2FA thành công",
                                rawResponse = twoFaRaw
                            )
                        }
                        // Hiển thị chính xác lỗi 2FA từ Instagram
                        val err2fa = twoFaJson?.optString("message", "").orEmpty()
                            .ifBlank { "Mã 2FA không chính xác hoặc đã hết hạn" }
                        return IgLoginResult(isSuccess = false, isTwoFactorRequired = true,
                            message = err2fa, rawResponse = twoFaRaw)
                    }
                }
                return IgLoginResult(isSuccess = false, isTwoFactorRequired = true,
                    message = "Tài khoản yêu cầu mã 2FA. Vui lòng nhập 2FA Secret hoặc OTP 6 số!")
            }

            // Hiển thị chính xác lỗi từ Instagram trả về
            val igError = restJson?.optString("message", "").orEmpty().ifBlank {
                restJson?.optString("error_type", "").orEmpty()
            }.ifBlank {
                if (restJson?.optBoolean("user") == false) "Tài khoản không tồn tại"
                else "Sai mật khẩu hoặc tài khoản bị giới hạn"
            }
            return IgLoginResult(isSuccess = false, message = igError, rawResponse = restRaw)
        }

        /**
         * Tự động re-login khi cookie bị hết hạn/văng phiên.
         */
        fun reloginIfExpired(account: com.cayxu.app.data.local.InstagramAccount, context: android.content.Context): Boolean {
            if (account.password.isBlank()) return false
            val loginRes = loginWithCredentials(
                usernameInput = account.username,
                passwordRaw = account.password,
                twoFaSecret = account.twoFactor,
                proxy = account.proxy
            )
            if (loginRes.isSuccess && loginRes.cookie.isNotBlank()) {
                val updated = account.copy(
                    cookie = loginRes.cookie,
                    isLive = true,
                    avatar = if (loginRes.avatarUrl.isNotBlank()) loginRes.avatarUrl else account.avatar,
                    fullName = if (loginRes.fullName.isNotBlank()) loginRes.fullName else account.fullName
                )
                com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, updated)
                return true
            }
            return false
        }
    }
}
