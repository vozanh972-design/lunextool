package com.cayxu.app.instagram

import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.util.regex.Pattern

/**
 * Model biểu diễn phiên làm việc Instagram đã xác thực
 */
data class IgSession(
    val rawCookie: String,
    val userId: String,
    val username: String,
    val csrfToken: String,
    val fbDtsg: String? = null,
    val lsd: String? = null
)

/**
 * Trạng thái kết quả sau khi thực thi hành động
 */
sealed class IgResult<out T> {
    data class Success<out T>(val data: T) : IgResult<T>()
    data class Error(val message: String, val isCheckpoint: Boolean = false, val isRateLimited: Boolean = false) : IgResult<Nothing>()
}

/**
 * Client thực thi tương tác Instagram chuẩn hóa từ GoMax 1.2.2
 */
class InstagramEngine(private val httpClient: OkHttpClient) {

    companion object {
        const val BASE_GRAPHQL = "https://www.instagram.com/graphql/query"
        const val APP_ID = "936619743392459"
        const val ASBD_ID = "359341"
        const val BLOKS_VERSION = "61fc9465e13b77eaa110f317859102ba7fb93a0a2bcc08c46473da6713640739"
        const val SHORTCODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        // Document IDs
        const val DOC_FOLLOW = "9740159112729312"
        const val DOC_FOLLOW_BACKUP = "9663809173698092"
        const val DOC_LIKE_MEDIA = "9595477160535898"
        const val DOC_LIKE_COMMENT = "7358156687612196"
        const val DOC_COMMENT_DIRECT = "7755358241198424"

        /**
         * Chuyển đổi Shortcode (trong URL /p/, /reel/, /tv/) sang numeric Media ID bằng BigInteger
         */
        fun shortcodeToMediaId(shortcode: String): String? {
            if (shortcode.isBlank()) return null
            var id = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (char in shortcode) {
                val index = SHORTCODE_ALPHABET.indexOf(char)
                if (index == -1) return null
                id = id.multiply(base).add(BigInteger.valueOf(index.toLong()))
            }
            return id.toString()
        }

        /**
         * Trích xuất shortcode từ URL bài viết
         */
        fun extractShortcode(url: String): String? {
            val matcher = Pattern.compile("/(?:p|reel|tv)/([A-Za-z0-9_-]+)").matcher(url)
            return if (matcher.find()) matcher.group(1) else null
        }

        /**
         * Tạo InstagramEngine với OkHttpClient chuẩn hóa (hỗ trợ ProxyConfig)
         */
        fun create(proxyConfig: InstagramApiClient.ProxyConfig? = null, timeoutSec: Long = 20L): InstagramEngine {
            val client = InstagramApiClient.buildOkHttpClient(proxyConfig, timeoutSec)
            return InstagramEngine(client)
        }
    }

    // ── 1. KIỂM TRA COOKIE / ĐĂNG NHẬP (CHECK LIVE) ─────────────────────────

    fun verifySession(cookie: String): IgResult<IgSession> {
        val cookieMap = parseCookies(cookie)
        val userId = cookieMap["ds_user_id"]
        val csrfToken = cookieMap["csrftoken"]
        val sessionId = cookieMap["sessionid"]

        if (userId.isNullOrBlank() || csrfToken.isNullOrBlank() || sessionId.isNullOrBlank()) {
            return IgResult.Error("Missing required cookies (sessionid, ds_user_id, or csrftoken)")
        }

        // Bước 1: Gọi trang chủ trích xuất token & kiểm tra live
        val request = Request.Builder()
            .url("https://www.instagram.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cookie", cookie)
            .get()
            .build()

        val html = executeCall(request) ?: return IgResult.Error("Network failure on homepage check")

        // Kiểm tra checkpoint / die
        if (isAccountBlockedOrExpired(html)) {
            return IgResult.Error("Cookie died or checkpoint required", isCheckpoint = true)
        }

        var username = extractRegex(html, "\"username\"\\s*:\\s*\"([^\"]+)\"")
            ?: extractRegex(html, "username\":\"([^\"]+)\"")
            ?: ""
        val fbDtsg = extractRegex(html, "\"fb_dtsg\":\"([^\"]+)\"")
            ?: extractRegex(html, "DTSGInitialData[^\"]*\"token\":\"([^\"]+)\"")
        val lsd = extractRegex(html, "\"lsd\":\"([^\"]+)\"")
            ?: extractRegex(html, "\"LSD\"[\\s\\S]{0,1200}?\"token\"\\s*:\\s*\"([^\"]+)\"")

        // Fallback REST nếu HTML không chứa username
        if (username.isBlank()) {
            try {
                val restReq = Request.Builder()
                    .url("https://www.instagram.com/api/v1/accounts/current_user/?edit=true")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .header("X-IG-App-ID", APP_ID)
                    .header("Cookie", cookie)
                    .get()
                    .build()
                val restRes = executeCall(restReq)
                if (restRes != null) {
                    val json = JSONObject(restRes)
                    username = json.optJSONObject("user")?.optString("username").orEmpty()
                }
            } catch (_: Exception) {}
        }

        return IgResult.Success(
            IgSession(
                rawCookie = cookie,
                userId = userId,
                username = username,
                csrfToken = csrfToken,
                fbDtsg = fbDtsg,
                lsd = lsd
            )
        )
    }

    // ── 2. FOLLOW TÀI KHOẢN (GRAPHQL MUTATION) ──────────────────────────

    fun follow(session: IgSession, targetUserId: String, targetUsername: String = ""): IgResult<Boolean> {
        val variables = JSONObject().apply {
            put("target_user_id", targetUserId)
            put("container_module", "profile")
            put("nav_chain", "PolarisProfilePostsTabRoot:profilePage:1:via_cold_start,PolarisProfilePostsTabRoot:profilePage:3:unexpected")
        }

        val body = buildBaseFormBody(DOC_FOLLOW, "usePolarisFollowMutation", variables.toString(), session)

        val referer = if (targetUsername.isNotBlank()) {
            if (targetUsername.startsWith("http")) targetUsername else "https://www.instagram.com/$targetUsername/"
        } else {
            "https://www.instagram.com/"
        }

        val headers = buildCommonHeaders(session, referer).newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisFollowMutation")
            .add("X-Root-Field-Name", "xdt_create_friendship")
            .build()

        val request = Request.Builder()
            .url(BASE_GRAPHQL)
            .headers(headers)
            .post(body)
            .build()

        val responseStr = executeCall(request) ?: return IgResult.Error("Network failure on follow")

        if (parseFollowSuccess(responseStr)) {
            return IgResult.Success(true)
        }

        val isBlocked = isRateLimited(responseStr)
        // Backup doc_id nếu doc_id chính thất bại và không phải bị rate limit
        if (!isBlocked && DOC_FOLLOW_BACKUP.isNotBlank()) {
            val backupBody = buildBaseFormBody(DOC_FOLLOW_BACKUP, "usePolarisFollowMutation", variables.toString(), session)
            val backupReq = Request.Builder()
                .url(BASE_GRAPHQL)
                .headers(headers)
                .post(backupBody)
                .build()
            val backupRes = executeCall(backupReq)
            if (backupRes != null && parseFollowSuccess(backupRes)) {
                return IgResult.Success(true)
            }
        }

        return IgResult.Error("Follow rejected: $responseStr", isRateLimited = isBlocked)
    }

    // ── 3. LIKE BÀI VIẾT / REELS (GRAPHQL + REST FALLBACK) ───────────────

    fun likeMedia(session: IgSession, mediaId: String): IgResult<Boolean> {
        val variables = JSONObject().apply {
            put("media_id", mediaId)
            put("container_module", "feed_timeline")
        }

        val body = buildBaseFormBody(DOC_LIKE_MEDIA, "usePolarisLikeMediaLikeMutation", variables.toString(), session)
        val headers = buildCommonHeaders(session, "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisLikeMediaLikeMutation")
            .build()

        val request = Request.Builder()
            .url(BASE_GRAPHQL)
            .headers(headers)
            .post(body)
            .build()

        val responseStr = executeCall(request)

        if (responseStr != null) {
            if (parseLikeSuccess(responseStr)) {
                return IgResult.Success(true)
            }
            if (isRateLimited(responseStr)) {
                return IgResult.Error("Like media action blocked: $responseStr", isRateLimited = true)
            }
        }

        // Fallback sang REST endpoint nếu GraphQL chưa thành công
        return likeMediaRestFallback(session, mediaId)
    }

    private fun likeMediaRestFallback(session: IgSession, mediaId: String): IgResult<Boolean> {
        val url = "https://www.instagram.com/api/v1/web/likes/$mediaId/like/"
        val request = Request.Builder()
            .url(url)
            .header("X-CSRFToken", session.csrfToken)
            .header("X-Instagram-AJAX", "1006309104")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-IG-App-ID", APP_ID)
            .header("Cookie", session.rawCookie)
            .post(FormBody.Builder().build())
            .build()

        val res = executeCall(request) ?: return IgResult.Error("Network failure on like REST fallback")
        return if (parseLikeSuccess(res)) {
            IgResult.Success(true)
        } else {
            IgResult.Error("Like media failed: $res", isRateLimited = isRateLimited(res))
        }
    }

    // ── 4. LIKE COMMENT (GRAPHQL MUTATION) ──────────────────────────────

    fun likeComment(session: IgSession, commentId: String): IgResult<Boolean> {
        val variables = JSONObject().apply {
            put("comment_id", commentId)
            put("container_module", "self_comments_v2")
        }

        val body = buildBaseFormBody(DOC_LIKE_COMMENT, "usePolarisLikeCommentLikeMutation", variables.toString(), session)
        val headers = buildCommonHeaders(session, "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisLikeCommentLikeMutation")
            .build()

        val request = Request.Builder()
            .url(BASE_GRAPHQL)
            .headers(headers)
            .post(body)
            .build()

        val res = executeCall(request) ?: return IgResult.Error("Network failure on like comment")
        return if (parseLikeSuccess(res)) {
            IgResult.Success(true)
        } else {
            IgResult.Error("Like comment failed: $res", isRateLimited = isRateLimited(res))
        }
    }

    // ── 5. COMMENT TRỰC TIẾP (GRAPHQL MUTATION) ─────────────────────────

    fun comment(session: IgSession, mediaId: String, text: String): IgResult<Boolean> {
        val variables = JSONObject().apply {
            put("id", mediaId)
            put("comment_text", text)
            put("container_module", "self_comments_v2")
        }

        val body = buildBaseFormBody(DOC_COMMENT_DIRECT, "usePolarisCommentDirectMutation", variables.toString(), session)
        val headers = buildCommonHeaders(session, "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisCommentDirectMutation")
            .build()

        val request = Request.Builder()
            .url(BASE_GRAPHQL)
            .headers(headers)
            .post(body)
            .build()

        val res = executeCall(request) ?: return IgResult.Error("Network failure on comment")
        return if (!res.contains("\"errors\":") && res.contains("\"id\":")) {
            IgResult.Success(true)
        } else {
            IgResult.Error("Comment failed: $res", isRateLimited = isRateLimited(res))
        }
    }

    // ── HELPERS: HEADERS & BUILDERS ─────────────────────────────────────

    private fun buildCommonHeaders(session: IgSession, referer: String): Headers {
        return Headers.Builder()
            .add("Accept", "*/*")
            .add("Accept-Language", "vi,en;q=0.9")
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Origin", "https://www.instagram.com")
            .add("Referer", referer)
            .add("Sec-Ch-Ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"")
            .add("Sec-Ch-Ua-Mobile", "?0")
            .add("Sec-Ch-Ua-Platform", "\"Windows\"")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("X-ASBD-ID", ASBD_ID)
            .add("X-Bloks-Version-Id", BLOKS_VERSION)
            .add("X-CSRFToken", session.csrfToken)
            .add("X-IG-App-ID", APP_ID)
            .add("Cookie", session.rawCookie)
            .build()
    }

    private fun buildBaseFormBody(docId: String, friendlyName: String, variablesJson: String, session: IgSession): FormBody {
        val builder = FormBody.Builder()
            .add("av", "178414")
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "1j")
            .add("__hs", "20519.HYP:instagram_web_pkg.2.1...0")
            .add("dpr", "1")
            .add("__ccg", "EXCELLENT")
            .add("__comet_req", "7")
            .add("jazoest", "26738")
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", friendlyName)
            .add("variables", variablesJson)
            .add("server_timestamps", "true")
            .add("doc_id", docId)

        session.fbDtsg?.let { if (it.isNotBlank()) builder.add("fb_dtsg", it) }
        session.lsd?.let { if (it.isNotBlank()) builder.add("lsd", it) }
        return builder.build()
    }

    private fun executeCall(request: Request): String? {
        return try {
            httpClient.newCall(request).execute().use { it.body?.string() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCookies(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        raw.split(";").forEach { item ->
            val idx = item.indexOf("=")
            if (idx > 0) {
                map[item.substring(0, idx).trim()] = item.substring(idx + 1).trim()
            }
        }
        return map
    }

    private fun extractRegex(content: String, patternStr: String): String? {
        val m = Pattern.compile(patternStr).matcher(content)
        return if (m.find()) m.group(1) else null
    }

    private fun isAccountBlockedOrExpired(content: String): Boolean {
        val lower = content.lowercase()
        return lower.contains("login_required") ||
                lower.contains("checkpoint_required") ||
                lower.contains("challenge_required") ||
                lower.contains("not-logged-in") ||
                lower.contains("\"is_logged_in\":false") ||
                lower.contains("accounts/suspended") ||
                lower.contains("1357031")
    }

    private fun isRateLimited(content: String): Boolean {
        val lower = content.lowercase()
        return lower.contains("feedback_required") ||
                lower.contains("sentry_block") ||
                lower.contains("rate limit") ||
                lower.contains("please wait a few minutes") ||
                lower.contains("action_blocked") ||
                lower.contains("spam")
    }

    private fun parseFollowSuccess(response: String): Boolean {
        if (response.isBlank()) return false
        return try {
            val json = JSONObject(response)
            val data = json.optJSONObject("data")
            val friendship = data?.optJSONObject("xdt_create_friendship")
            val status = friendship?.optJSONObject("friendship_status")
            status?.optBoolean("following") == true || json.optString("status").equals("ok", true)
        } catch (_: Exception) {
            response.contains("\"following\":true") || response.contains("already") || response.contains("\"status\":\"ok\"")
        }
    }

    private fun parseLikeSuccess(response: String): Boolean {
        val lower = response.lowercase()
        return lower.contains("\"status\":\"ok\"") ||
                lower.contains("\"success\":true") ||
                lower.contains("\"viewer_has_liked\":true") ||
                lower.contains("\"has_liked\":true") ||
                lower.contains("xdt_like_media") ||
                lower.contains("xig_media_like")
    }
}
