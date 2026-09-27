package com.cayxu.app.instagram

import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class IgAccount(
    val userId: String,
    val username: String,
    val cookie: String,
    val proxy: String? = null,
    val fbDtsg: String? = null,
    val lsd: String? = null,
    var isLive: Boolean = true
)

data class IgFollowResponse(
    val isSuccess: Boolean,
    val httpCode: Int,
    val rawBody: String,
    val errorMessage: String? = null
)

class GoMaxInstagramEngine(private val client: OkHttpClient) {
    companion object {
        private const val GRAPHQL_URL = "https://www.instagram.com/graphql/query"
        private const val APP_ID = "936619743392459"
        private const val ASBD_ID = "359341"
        private const val BLOKS_VER = "61fc9465e13b77eaa110f317859102ba7fb93a0a2bcc08c46473da6713640739"
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        // Doc IDs chính xác từ GoMax
        private const val DOC_FOLLOW = "9740159112729312"
        private const val DOC_LIKE = "9595477160535898"
        private const val DOC_LIKE_CMT = "7358156687612196"
        private const val DOC_COMMENT = "7755358241198424"

        // Thuật toán GoMax: Giải mã shortcode sang Media ID
        fun shortcodeToMediaId(shortcode: String): String? {
            if (shortcode.isBlank()) return null
            var id = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (c in shortcode) {
                val idx = ALPHABET.indexOf(c)
                if (idx == -1) return null
                id = id.multiply(base).add(BigInteger.valueOf(idx.toLong()))
            }
            return id.toString()
        }

        fun extractShortcode(url: String): String? {
            val m = Pattern.compile("/(?:p|reel|tv)/([A-Za-z0-9_-]+)").matcher(url)
            return if (m.find()) m.group(1) else null
        }

        fun buildClientForAccount(proxyStr: String?, timeoutSec: Long = 20L): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(timeoutSec.coerceAtLeast(15L), TimeUnit.SECONDS)
                .readTimeout(timeoutSec.coerceAtLeast(20L), TimeUnit.SECONDS)
                .writeTimeout(timeoutSec.coerceAtLeast(20L), TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)

            if (!proxyStr.isNullOrBlank()) {
                var s = proxyStr.trim()
                if (s.contains("://")) {
                    s = s.substringAfter("://")
                }
                try {
                    if (s.contains("@")) {
                        val atParts = s.split("@", limit = 2)
                        val auth = atParts[0].split(":", limit = 2)
                        val hostPort = atParts[1].split(":", limit = 2)
                        val host = hostPort[0].trim()
                        val port = hostPort.getOrNull(1)?.trim()?.toIntOrNull() ?: 8080
                        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                        if (auth.size >= 2) {
                            val user = auth[0].trim()
                            val pass = auth[1].trim()
                            builder.proxyAuthenticator { _, response ->
                                if (response.request.header("Proxy-Authorization") != null) null
                                else {
                                    val credential = Credentials.basic(user, pass)
                                    response.request.newBuilder().header("Proxy-Authorization", credential).build()
                                }
                            }
                        }
                    } else {
                        val parts = s.split(":")
                        val host = parts[0].trim()
                        val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 8080
                        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                        if (parts.size >= 4) {
                            val user = parts[2].trim()
                            val pass = parts[3].trim()
                            builder.proxyAuthenticator { _, response ->
                                if (response.request.header("Proxy-Authorization") != null) null
                                else {
                                    val credential = Credentials.basic(user, pass)
                                    response.request.newBuilder().header("Proxy-Authorization", credential).build()
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            return builder.build()
        }

        fun create(proxy: String? = null, timeoutSec: Long = 20L): GoMaxInstagramEngine {
            val client = buildClientForAccount(proxy, timeoutSec)
            return GoMaxInstagramEngine(client)
        }
    }

    // ── 1. CHECK LIVE & TỰ ĐỘNG LẤY USERNAME/ID TỪ COOKIE ─────────────
    fun checkLiveCookie(cookie: String, proxy: String? = null): IgAccount? {
        val cookies = parseCookie(cookie)
        val uid = cookies["ds_user_id"] ?: return null
        val csrf = cookies["csrftoken"] ?: return null
        if (cookies["sessionid"].isNullOrBlank()) return null

        val req = Request.Builder()
            .url("https://www.instagram.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cookie", cookie)
            .get().build()
        val html = execute(req) ?: return null

        // Check die / checkpoint
        val lower = html.lowercase()
        if (lower.contains("login_required") || lower.contains("checkpoint_required") || 
            lower.contains("challenge_required") || lower.contains("\"is_logged_in\":false") ||
            lower.contains("accounts/suspended") || lower.contains("1357031")) {
            return null
        }

        // Bóc username
        var username = regex(html, "\"username\"\\s*:\\s*\"([^\"]+)\"") 
            ?: regex(html, "username\":\"([^\"]+)\"")

        if (username.isNullOrBlank()) {
            try {
                val restReq = Request.Builder()
                    .url("https://www.instagram.com/api/v1/accounts/current_user/?edit=true")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .header("X-IG-App-ID", APP_ID)
                    .header("Cookie", cookie)
                    .get().build()
                val restRes = execute(restReq)
                if (restRes != null) {
                    val json = JSONObject(restRes)
                    username = json.optJSONObject("user")?.optString("username")
                }
            } catch (_: Exception) {}
        }

        val finalUsername = if (!username.isNullOrBlank()) username else uid

        // Bóc token bảo mật
        val fbDtsg = regex(html, "\"fb_dtsg\":\"([^\"]+)\"") 
            ?: regex(html, "DTSGInitialData[^\"]*\"token\":\"([^\"]+)\"")
        val lsd = regex(html, "\"lsd\":\"([^\"]+)\"")

        return IgAccount(
            userId = uid,
            username = finalUsername,
            cookie = cookie,
            proxy = proxy,
            fbDtsg = fbDtsg,
            lsd = lsd,
            isLive = true
        )
    }

    // ── 2. FOLLOW ─────────────────────────────────────────────────────
    fun computeJazoest(fbDtsg: String?): String {
        if (fbDtsg.isNullOrBlank()) return "26738"
        var sum = 0
        for (char in fbDtsg) {
            sum += char.code
        }
        return "2$sum"
    }

    fun follow(account: com.cayxu.app.automation.instagram.IgXsmmAccount, targetUid: String, targetUsername: String = ""): IgFollowResponse {
        val igAcc = IgAccount(
            userId = account.userId,
            username = account.username,
            cookie = account.cookie,
            proxy = account.proxy,
            fbDtsg = account.fbDtsg,
            lsd = account.lsd
        )
        return follow(igAcc, targetUid, targetUsername)
    }

    fun follow(acc: IgAccount, targetUid: String, targetUsername: String = ""): IgFollowResponse {
        var uid = targetUid.trim()
        if ((!uid.all { it.isDigit() } || uid.isBlank()) && targetUsername.isNotBlank()) {
            val resolved = InstagramApiClient.resolveTargetUserId(targetUsername, acc.proxy)
            if (!resolved.isNullOrBlank()) {
                uid = resolved
            }
        }
        val csrf = csrf(acc.cookie)
        val referer = if (targetUsername.isNotBlank()) {
            if (targetUsername.startsWith("http")) targetUsername else "https://www.instagram.com/$targetUsername/"
        } else "https://www.instagram.com/"

        var lastHttpCode = -1
        var lastBodyStr = ""

        // ── CÁCH 1: NẾU CÓ fb_dtsg -> GỌI GRAPHQL MUTATION VỚI JAZOEST CHUẨN ──
        if (!acc.fbDtsg.isNullOrBlank()) {
            try {
                val realJazoest = computeJazoest(acc.fbDtsg)
                val avActor = if (acc.userId.isNotBlank()) "178414${acc.userId}" else {
                    val uidFromCookie = parseCookie(acc.cookie)["ds_user_id"] ?: ""
                    if (uidFromCookie.isNotBlank()) "178414$uidFromCookie" else "178414"
                }

                val vars = JSONObject().apply {
                    put("target_user_id", uid)
                    put("container_module", "profile")
                    put("nav_chain", "PolarisProfilePostsTabRoot:profilePage:1:via_cold_start,PolarisProfilePostsTabRoot:profilePage:3:unexpected")
                }

                val headerBuilder = Headers.Builder()
                    .add("Accept", "*/*")
                    .add("Accept-Language", "vi,en;q=0.9")
                    .add("Cache-Control", "no-cache")
                    .add("Content-Type", "application/x-www-form-urlencoded")
                    .add("Origin", "https://www.instagram.com")
                    .add("Pragma", "no-cache")
                    .add("Referer", referer)
                    .add("Sec-Ch-Ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"")
                    .add("Sec-Ch-Ua-Mobile", "?0")
                    .add("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .add("Sec-Fetch-Dest", "empty")
                    .add("Sec-Fetch-Mode", "cors")
                    .add("Sec-Fetch-Site", "same-origin")
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .add("X-ASBD-ID", ASBD_ID)
                    .add("X-Bloks-Version-Id", BLOKS_VER)
                    .add("X-CSRFToken", csrf)
                    .add("X-FB-Friendly-Name", "usePolarisFollowMutation")
                    .add("X-IG-App-ID", APP_ID)
                    .add("X-Root-Field-Name", "xdt_create_friendship")
                    .add("Cookie", acc.cookie)
                    .add("X-FB-DTSG", acc.fbDtsg)
                acc.lsd?.takeIf { it.isNotBlank() }?.let { headerBuilder.add("X-FB-LSD", it) }

                val formBuilder = FormBody.Builder()
                    .add("av", avActor)
                    .add("__d", "www")
                    .add("__user", "0")
                    .add("__a", "1")
                    .add("__req", "1j")
                    .add("__hs", "20519.HYP:instagram_web_pkg.2.1...0")
                    .add("dpr", "1")
                    .add("__ccg", "EXCELLENT")
                    .add("__comet_req", "7")
                    .add("jazoest", realJazoest)
                    .add("fb_api_caller_class", "RelayModern")
                    .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
                    .add("variables", vars.toString())
                    .add("server_timestamps", "true")
                    .add("doc_id", DOC_FOLLOW)
                    .add("fb_dtsg", acc.fbDtsg)
                acc.lsd?.takeIf { it.isNotBlank() }?.let { formBuilder.add("lsd", it) }

                val req = Request.Builder()
                    .url(GRAPHQL_URL)
                    .headers(headerBuilder.build())
                    .post(formBuilder.build())
                    .build()

                val response = client.newCall(req).execute()
                lastHttpCode = response.code
                lastBodyStr = response.use { it.body?.string().orEmpty() }
                if (parseMethodU(lastBodyStr)) {
                    return IgFollowResponse(isSuccess = true, httpCode = lastHttpCode, rawBody = lastBodyStr)
                }
            } catch (e: Exception) {
                lastHttpCode = -1
                lastBodyStr = e.message ?: "Exception GraphQL"
            }
        }

        // ── CÁCH 2: NẾU THIẾU fb_dtsg HOẶC GRAPHQL BỊ TỪ CHỐI -> GỌI REST API WEB ──
        try {
            val restHeaders = Headers.Builder()
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
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .add("X-CSRFToken", csrf)
                .add("X-Instagram-AJAX", "1006309104")
                .add("X-IG-App-ID", APP_ID)
                .add("X-Requested-With", "XMLHttpRequest")
                .add("Cookie", acc.cookie)
                .build()

            val restReq = Request.Builder()
                .url("https://www.instagram.com/api/v1/web/friendships/$uid/follow/")
                .headers(restHeaders)
                .post(FormBody.Builder().build())
                .build()

            val response = client.newCall(restReq).execute()
            val code = response.code
            val bodyStr = response.use { it.body?.string().orEmpty() }
            if (bodyStr.contains("\"following\":true") || bodyStr.contains("\"status\":\"ok\"") ||
                bodyStr.contains("\"outgoing_request\":true") || bodyStr.contains("already") ||
                bodyStr.contains("\"result\":\"following\"")) {
                return IgFollowResponse(isSuccess = true, httpCode = code, rawBody = bodyStr)
            }
            return IgFollowResponse(
                isSuccess = false,
                httpCode = code,
                rawBody = bodyStr,
                errorMessage = "HTTP $code: $bodyStr"
            )
        } catch (e: Exception) {
            return IgFollowResponse(
                isSuccess = false,
                httpCode = lastHttpCode.takeIf { it != -1 } ?: -1,
                rawBody = lastBodyStr.ifBlank { e.message ?: "Exception REST" },
                errorMessage = "Lỗi kết nối mạng: ${e.message}"
            )
        }
    }

    private fun parseMethodU(response: String): Boolean {
        if (response.isBlank()) return false
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data")
            val friendship = data?.optJSONObject("xdt_create_friendship")
            val statusObj = friendship?.optJSONObject("friendship_status")
            if (statusObj?.optBoolean("following") == true || statusObj?.optBoolean("outgoing_request") == true) return true
            if (root.optString("status").equals("ok", ignoreCase = true)) return true
            val errors = root.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val msg = errors.getJSONObject(0).optString("message").lowercase()
                if (msg.contains("already")) return true
            }
            false
        } catch (_: Exception) {
            response.contains("\"following\":true") || response.contains("\"outgoing_request\":true") || response.contains("already")
        }
    }

    // ── 3. LIKE MEDIA ─────────────────────────────────────────────────
    fun likeMedia(acc: IgAccount, mediaId: String): Boolean {
        val vars = JSONObject().apply {
            put("media_id", mediaId)
            put("container_module", "feed_timeline")
        }
        val headers = commonHeaders(acc.cookie, csrf(acc.cookie), "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisLikeMediaLikeMutation")
            .build()
        val body = formBody(DOC_LIKE, "usePolarisLikeMediaLikeMutation", vars.toString(), acc.fbDtsg, acc.lsd)
        val res = execute(Request.Builder().url(GRAPHQL_URL).headers(headers).post(body).build())
        if (res != null && (res.contains("\"status\":\"ok\"") || res.contains("\"viewer_has_liked\":true") || res.contains("\"success\":true") || res.contains("xdt_like_media"))) {
            return true
        }

        // REST fallback đúng GoMax
        val restReq = Request.Builder()
            .url("https://www.instagram.com/api/v1/web/likes/$mediaId/like/")
            .header("X-CSRFToken", csrf(acc.cookie))
            .header("X-Instagram-AJAX", "1006309104")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-IG-App-ID", APP_ID)
            .header("Cookie", acc.cookie)
            .post(FormBody.Builder().build())
            .build()
        val restRes = execute(restReq)
        return restRes != null && (restRes.contains("\"status\":\"ok\"") || restRes.contains("\"success\":true"))
    }

    // ── 4. LIKE COMMENT ───────────────────────────────────────────────
    fun likeComment(acc: IgAccount, commentId: String): Boolean {
        val vars = JSONObject().apply {
            put("comment_id", commentId)
            put("container_module", "self_comments_v2")
        }
        val headers = commonHeaders(acc.cookie, csrf(acc.cookie), "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisLikeCommentLikeMutation")
            .build()
        val body = formBody(DOC_LIKE_CMT, "usePolarisLikeCommentLikeMutation", vars.toString(), acc.fbDtsg, acc.lsd)
        val res = execute(Request.Builder().url(GRAPHQL_URL).headers(headers).post(body).build())
        return res != null && res.contains("\"status\":\"ok\"")
    }

    // ── 5. COMMENT DIRECT ─────────────────────────────────────────────
    fun comment(acc: IgAccount, mediaId: String, text: String): Boolean {
        val vars = JSONObject().apply {
            put("id", mediaId)
            put("comment_text", text)
            put("container_module", "self_comments_v2")
        }
        val headers = commonHeaders(acc.cookie, csrf(acc.cookie), "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisCommentDirectMutation")
            .build()
        val body = formBody(DOC_COMMENT, "usePolarisCommentDirectMutation", vars.toString(), acc.fbDtsg, acc.lsd)
        val res = execute(Request.Builder().url(GRAPHQL_URL).headers(headers).post(body).build())
        return res != null && !res.contains("\"errors\":") && res.contains("\"id\":")
    }

    // ── UTILITIES ─────────────────────────────────────────────────────
    private fun commonHeaders(cookie: String, csrf: String, referer: String) = Headers.Builder()
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
        .add("X-Bloks-Version-Id", BLOKS_VER)
        .add("X-CSRFToken", csrf)
        .add("X-IG-App-ID", APP_ID)
        .add("Cookie", cookie)
        .build()

    private fun formBody(docId: String, name: String, vars: String, dtsg: String?, lsd: String?) = FormBody.Builder()
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
        .add("fb_api_req_friendly_name", name)
        .add("variables", vars)
        .add("server_timestamps", "true")
        .add("doc_id", docId)
        .apply {
            dtsg?.let { if (it.isNotBlank()) add("fb_dtsg", it) }
            lsd?.let { if (it.isNotBlank()) add("lsd", it) }
        }.build()

    private fun execute(req: Request): String? = try {
        client.newCall(req).execute().use { it.body?.string() }
    } catch (_: Exception) { null }

    private fun parseCookie(cookie: String) = cookie.split(";").mapNotNull {
        val idx = it.indexOf("=")
        if (idx > 0) it.substring(0, idx).trim() to it.substring(idx + 1).trim() else null
    }.toMap()

    private fun csrf(cookie: String) = parseCookie(cookie)["csrftoken"] ?: ""

    private fun regex(s: String, p: String): String? {
        val m = Pattern.compile(p).matcher(s)
        return if (m.find()) m.group(1) else null
    }
}
