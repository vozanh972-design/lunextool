package com.cayxu.app.instagram

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * Client tương tác Instagram theo 100% logic từ Python tool TA Tool (XSMM API Multi-Thread V2):
 * - User-Agent chuẩn Windows Chrome 120 Desktop
 * - Bóc tách chính xác fb_dtsg (DTSGInitialData), lsd, jazoest
 * - Tự động loại bỏ tiền tố bảo mật "for (;;);" của Meta khi phân tích JSON
 * - Trích xuất chi tiết errorDescription/errorSummary từ Instagram trả về
 * - usePolarisFollowMutation (Doc ID: 26508036048874888)
 * - usePolarisLikeMediaXIGLikeMutation (Doc ID: 27182485238052618)
 * - PolarisPostCommentInputRevampedMutation (Doc ID: 27261905640092552)
 */
class InstagramApiClient(
    var cookie: String = "",
    var userAgent: String = USER_AGENT_WIN,
    var proxyConfig: ProxyConfig? = null
) {
    data class ProxyConfig(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null
    )

    data class CookieCheckResult(
        val isLive: Boolean,
        val username: String = "",
        val userId: String = "",
        val fullName: String = "",
        val biography: String = "",
        val email: String = "",
        val phoneNumber: String = "",
        val rawJson: String = ""
    )

    data class IgActionResult(
        val success: Boolean,
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

    var activeFbDtsg: String = ""
    var activeLsd: String = ""
    var activeActorId: String = ""

    init {
        if (userAgent.isBlank()) {
            userAgent = USER_AGENT_WIN
        }
    }

    companion object {
        const val USER_AGENT_WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val SEC_CH_UA_120 = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""

        const val DOC_ID_FOLLOW = "26508036048874888"
        const val DOC_ID_LIKE = "27182485238052618"
        const val DOC_ID_COMMENT = "27261905640092552"

        const val DEFAULT_LSD_FOLLOW = "Jfq8VQNmkkkJufHSbEE9bf"
        const val DEFAULT_JAZOEST_FOLLOW = "26328"

        const val DEFAULT_LSD_LIKE = "GyeZl-huflHZ0K5L3-pzBi"
        const val DEFAULT_JAZOEST_LIKE = "26492"

        const val DEFAULT_LSD_COMMENT = "9zei3OjvTBQ-9YG6E0OMzm"
        const val DEFAULT_JAZOEST_COMMENT = "26312"

        fun getIgHeaders(cookie: String, csrftoken: String, referer: String = "https://www.instagram.com/"): Map<String, String> {
            return mapOf(
                "accept" to "*/*",
                "accept-language" to "vi-VN,vi;q=0.9,fr-FR;q=0.8,fr;q=0.7,en-US;q=0.6,en;q=0.5",
                "cookie" to cookie,
                "origin" to "https://www.instagram.com",
                "priority" to "u=1, i",
                "referer" to referer,
                "sec-ch-ua" to SEC_CH_UA_120,
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "sec-fetch-dest" to "empty",
                "sec-fetch-mode" to "cors",
                "sec-fetch-site" to "same-origin",
                "user-agent" to USER_AGENT_WIN,
                "x-asbd-id" to "129477",
                "x-csrftoken" to csrftoken,
                "x-ig-app-id" to "936619743392459",
                "x-ig-www-claim" to "0",
                "x-requested-with" to "XMLHttpRequest"
            )
        }

        fun parseProxy(proxyStr: String?): ProxyConfig? {
            if (proxyStr.isNullOrBlank()) return null
            var s = proxyStr.trim()
            if (s.isBlank()) return null
            if (s.contains("://")) {
                val split = s.split("://", limit = 2)
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
                        password = auth.getOrNull(1)
                    )
                } else {
                    val parts = s.split(":")
                    if (parts.size == 4) {
                        ProxyConfig(
                            host = parts[0],
                            port = parts[1].toInt(),
                            username = parts[2],
                            password = parts[3]
                        )
                    } else if (parts.size == 2) {
                        ProxyConfig(
                            host = parts[0],
                            port = parts[1].toInt()
                        )
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun buildOkHttpClient(proxyConfig: ProxyConfig? = null, timeoutSec: Long = 25L): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                .readTimeout(timeoutSec, TimeUnit.SECONDS)
                .writeTimeout(timeoutSec, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            if (proxyConfig != null && proxyConfig.host.isNotBlank() && proxyConfig.port > 0) {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.host, proxyConfig.port))
                builder.proxy(proxy)
                if (!proxyConfig.username.isNullOrBlank()) {
                    builder.proxyAuthenticator(okhttp3.Authenticator { _, response ->
                        val credential = Credentials.basic(proxyConfig.username, proxyConfig.password.orEmpty())
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    })
                }
            }
            return builder.build()
        }

        private fun unquoteCookie(cookie: String): String {
            // Python's unquote decodes %xx escapes without turning '+' into space
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
            return if (matcher.find()) matcher.group(1).orEmpty() else "0"
        }

        /**
         * Loại bỏ tiền tố "for (;;);" của Meta/Instagram trước khi phân tích cú pháp JSON
         */
        fun cleanJsonResponse(raw: String): String {
            val s = raw.trim()
            val firstBrace = s.indexOf('{')
            val lastBrace = s.lastIndexOf('}')
            if (firstBrace in 0..lastBrace) {
                return s.substring(firstBrace, lastBrace + 1)
            }
            return s
        }

        /**
         * Trích xuất chính xác bộ token: LSD, FB_DTSG, JAZOEST từ mã HTML trang Instagram
         */
        fun extractTokensFromHtml(html: String, fallbackLsd: String, fallbackJazoest: String): Triple<String, String, String> {
            var lsd = fallbackLsd
            val lsdPatterns = listOf(
                Pattern.compile("\"LSD\",\\[],\\{\"token\":\"([^\"]+)\"}"),
                Pattern.compile("\"LSDInitialData\"[^\"]*\"token\"\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("name=\"lsd\" value=\"([^\"]+)\"")
            )
            for (p in lsdPatterns) {
                val m = p.matcher(html)
                if (m.find()) {
                    lsd = m.group(1)
                    break
                }
            }

            var fbDtsg = ""
            val dtsgPatterns = listOf(
                Pattern.compile("\"DTSGInitialData\"[^\"]*\"token\"\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("\\[\"DTSGInitData\",\\[],\\{\"token\":\"([^\"]+)\""),
                Pattern.compile("\"dtsg\":\\{\"token\":\"([^\"]+)\""),
                Pattern.compile("name=\"fb_dtsg\" value=\"([^\"]+)\""),
                Pattern.compile("\"token\"\\s*:\\s*\"(NA[^\"]+)\"")
            )
            for (p in dtsgPatterns) {
                val m = p.matcher(html)
                if (m.find()) {
                    fbDtsg = m.group(1)
                    break
                }
            }

            var jazoest = fallbackJazoest
            val jazoestMatch = Pattern.compile("name=\"jazoest\" value=\"(\\d+)\"").matcher(html)
            if (jazoestMatch.find()) {
                jazoest = jazoestMatch.group(1)
            } else if (fbDtsg.isNotBlank()) {
                var sum = 0
                for (c in fbDtsg) sum += c.code
                jazoest = "2$sum"
            }

            return Triple(lsd, fbDtsg, jazoest)
        }

        private fun parseGraphqlResult(rawResponse: String, httpCode: Int): IgActionResult {
            val clean = cleanJsonResponse(rawResponse)
            val json = try { JSONObject(clean) } catch (_: Exception) { null }
            val status = json?.optString("status", "") ?: ""

            val errorObj = json?.optJSONArray("errors")?.optJSONObject(0)
            val hasErrors = (json?.has("errors") == true || errorObj != null) &&
                !clean.contains("\"following\":true") &&
                !clean.contains("\"status\":\"ok\"")

            val isSuccess = json != null && !hasErrors && (
                (json.has("data") && !json.isNull("data")) ||
                status.equals("ok", ignoreCase = true) ||
                status.equals("success", ignoreCase = true) ||
                clean.contains("\"following\":true") ||
                clean.contains("\"status\":\"ok\"")
            )

            if (isSuccess) {
                return IgActionResult(true, "Thành công", clean)
            }

            val errorDesc = json?.optString("errorDescription")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optString("description")?.takeIf { it.isNotBlank() }
            val errorSumm = json?.optString("errorSummary")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optString("summary")?.takeIf { it.isNotBlank() }
            val msgField = json?.optString("message")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optString("message")?.takeIf { it.isNotBlank() }

            val reason = errorDesc ?: errorSumm ?: msgField
                ?: if (clean.contains("checkpoint")) "Checkpoint / Xác minh nick"
                else if (clean.contains("login_required") || clean.contains("unauthorized")) "Cookie hết hạn"
                else if (httpCode == 429) "Instagram giới hạn tạm thời (429)"
                else "Bị IG chặn thao tác (mã $httpCode)"

            return IgActionResult(false, reason, clean)
        }

        /**
         * 1. Kiểm tra độ sống của cookie Instagram theo hàm check_cookie_ig trong Python
         * URL: https://www.instagram.com/api/v1/accounts/edit/web_form_data/
         */
        fun checkCookieIg(cookie: String, proxy: String? = null): CookieCheckResult {
            val unquoted = unquoteCookie(cookie)
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 30L)
            val url = "https://www.instagram.com/api/v1/accounts/edit/web_form_data/"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-ig-app-id", "936619743392459")
                .addHeader("x-requested-with", "XMLHttpRequest")
                .addHeader("referer", "https://www.instagram.com/accounts/edit/")
                .addHeader("cookie", unquoted)
                .addHeader("user-agent", USER_AGENT_WIN)
                .addHeader("sec-ch-ua", SEC_CH_UA_120)
                .get()
                .build()

            return try {
                val response = client.newCall(request).execute()
                val rawBody = response.body?.string().orEmpty()
                val body = cleanJsonResponse(rawBody)
                if (!response.isSuccessful || body.isBlank()) {
                    return CookieCheckResult(isLive = false, rawJson = body)
                }

                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val formData = json?.optJSONObject("form_data")
                val username = formData?.optString("username").orEmpty()
                if (username.isNotBlank()) {
                    var uid = extractActorId(unquoted)
                    if (uid == "0" || uid.isBlank()) {
                        uid = formData?.optString("id", "") ?: ""
                    }
                    val fullName = formData?.optString("first_name", "") ?: ""
                    val bio = formData?.optString("biography", "") ?: ""
                    val email = formData?.optString("email", "") ?: ""
                    val phone = formData?.optString("phone_number", "") ?: ""
                    CookieCheckResult(
                        isLive = true,
                        username = username,
                        userId = uid,
                        fullName = fullName,
                        biography = bio,
                        email = email,
                        phoneNumber = phone,
                        rawJson = body
                    )
                } else {
                    CookieCheckResult(isLive = false, rawJson = body)
                }
            } catch (e: Exception) {
                CookieCheckResult(isLive = false, rawJson = "Lỗi kết nối: ${e.message}")
            }
        }

        /**
         * Trích xuất Target User ID số từ link bài viết/profile nếu target_id ban đầu không phải số
         */
        fun resolveTargetUserId(linkJob: String, proxy: String? = null): String? {
            if (linkJob.isBlank()) return null
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 10L)
            return try {
                val req = Request.Builder()
                    .url(linkJob)
                    .addHeader("user-agent", USER_AGENT_WIN)
                    .get()
                    .build()
                val res = client.newCall(req).execute()
                val html = res.body?.string().orEmpty()

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

        /**
         * Trích xuất Media ID từ link bài viết nếu thiếu
         */
        fun resolveMediaId(linkJob: String, proxy: String? = null): String? {
            if (linkJob.isBlank()) return null
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 10L)
            return try {
                val req = Request.Builder()
                    .url(linkJob)
                    .addHeader("user-agent", USER_AGENT_WIN)
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

        /**
         * 2. Nhiệm vụ FOLLOW theo chuẩn 100% Python TA Tool
         * GraphQL Doc ID: 26508036048874888 (usePolarisFollowMutation)
         */
        fun follow(
            targetId: String,
            cookie: String,
            csrftoken: String = "",
            profileUrl: String = "",
            proxy: String? = null
        ): IgActionResult {
            if (targetId.isBlank()) {
                return IgActionResult(false, "Lỗi Target ID")
            }
            val unquoted = unquoteCookie(cookie)
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 15L)

            val homeUrl = if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/"
            var dynamicCsrf = if (csrftoken.isNotBlank() && csrftoken != "missing") csrftoken else extractCsrfToken(unquoted)

            var lsd = DEFAULT_LSD_FOLLOW
            var fbDtsg = ""
            var jazoest = DEFAULT_JAZOEST_FOLLOW

            try {
                val homeReq = Request.Builder()
                    .url(homeUrl)
                    .addHeader("user-agent", USER_AGENT_WIN)
                    .addHeader("cookie", unquoted)
                    .addHeader("sec-ch-ua", SEC_CH_UA_120)
                    .get()
                    .build()
                val homeRes = client.newCall(homeReq).execute()
                val resHome = homeRes.body?.string().orEmpty()

                // Cập nhật csrftoken từ response nếu có
                homeRes.headers("Set-Cookie").forEach { sc ->
                    val cm = Pattern.compile("csrftoken=([^;\\s]+)").matcher(sc)
                    if (cm.find()) {
                        val tokenVal = cm.group(1).removeSurrounding("\"")
                        if (tokenVal.isNotBlank()) {
                            dynamicCsrf = tokenVal
                        }
                    }
                }

                val tokens = extractTokensFromHtml(resHome, DEFAULT_LSD_FOLLOW, DEFAULT_JAZOEST_FOLLOW)
                lsd = tokens.first
                fbDtsg = tokens.second
                jazoest = tokens.third
            } catch (_: Exception) {}

            val actorId = extractActorId(unquoted)

            val variables = JSONObject().apply {
                put("target_user_id", targetId)
                put("container_module", "profile")
                put("nav_chain", "PolarisFeedRoot:feedPage:5:topnav-link,PolarisProfileRoot:profilePage:6:unexpected")
            }

            val formBuilder = FormBody.Builder()
                .add("av", actorId)
                .add("__d", "www")
                .add("__user", "0")
                .add("__a", "1")
                .add("__req", "s")
                .add("__hs", "20702.HYP:instagram_web_pkg.2.1...0")
                .add("dpr", "1")
                .add("__ccg", "EXCELLENT")
                .add("__rev", "1046917461")
                .add("__comet_req", "7")
                .add("fb_dtsg", fbDtsg)
                .add("jazoest", jazoest)
                .add("lsd", lsd)
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
                .add("server_timestamps", "true")
                .add("doc_id", DOC_ID_FOLLOW)
                .add("variables", variables.toString())

            val reqHeaders = getIgHeaders(unquoted, dynamicCsrf, if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/")
            val requestBuilder = Request.Builder()
                .url("https://www.instagram.com/api/graphql")
                .post(formBuilder.build())

            reqHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            return try {
                val response = client.newCall(requestBuilder.build()).execute()
                val rawBody = response.body?.string().orEmpty()
                parseGraphqlResult(rawBody, response.code)
            } catch (e: Exception) {
                IgActionResult(false, e.message ?: "Lỗi gửi Follow", "")
            }
        }

        /**
         * 3. Nhiệm vụ TYM (LIKE) theo chuẩn 100% Python TA Tool
         * GraphQL Doc ID: 27182485238052618 (usePolarisLikeMediaXIGLikeMutation)
         */
        fun tym(
            mediaId: String,
            cookie: String,
            csrftoken: String = "",
            linkJob: String = "",
            proxy: String? = null
        ): IgActionResult {
            if (mediaId.isBlank()) {
                return IgActionResult(false, "Lỗi Media ID")
            }
            val unquoted = unquoteCookie(cookie)
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 15L)

            var dynamicCsrf = if (csrftoken.isNotBlank() && csrftoken != "missing") csrftoken else extractCsrfToken(unquoted)

            var lsd = DEFAULT_LSD_LIKE
            var fbDtsg = ""
            var jazoest = DEFAULT_JAZOEST_LIKE

            try {
                val homeReq = Request.Builder()
                    .url("https://www.instagram.com/")
                    .addHeader("user-agent", USER_AGENT_WIN)
                    .addHeader("cookie", unquoted)
                    .addHeader("sec-ch-ua", SEC_CH_UA_120)
                    .get()
                    .build()
                val homeRes = client.newCall(homeReq).execute()
                val resHome = homeRes.body?.string().orEmpty()

                homeRes.headers("Set-Cookie").forEach { sc ->
                    val cm = Pattern.compile("csrftoken=([^;\\s]+)").matcher(sc)
                    if (cm.find()) {
                        val tokenVal = cm.group(1).removeSurrounding("\"")
                        if (tokenVal.isNotBlank()) {
                            dynamicCsrf = tokenVal
                        }
                    }
                }

                val tokens = extractTokensFromHtml(resHome, DEFAULT_LSD_LIKE, DEFAULT_JAZOEST_LIKE)
                lsd = tokens.first
                fbDtsg = tokens.second
                jazoest = tokens.third
            } catch (_: Exception) {}

            val actorId = extractActorId(unquoted)

            var trackingToken = ""
            if (linkJob.isNotBlank()) {
                try {
                    val linkReq = Request.Builder()
                        .url(linkJob)
                        .addHeader("user-agent", USER_AGENT_WIN)
                        .addHeader("cookie", unquoted)
                        .get()
                        .build()
                    val resLink = client.newCall(linkReq).execute().body?.string().orEmpty()
                    val ttMatch = Pattern.compile("\"tracking_token\":\"([^\"]+)\"").matcher(resLink)
                    if (ttMatch.find()) {
                        trackingToken = ttMatch.group(1)
                    }
                } catch (_: Exception) {}
            }

            val inputObj = JSONObject().apply {
                put("actor_id", actorId)
                put("client_mutation_id", Random.nextInt(1000000, 9999999).toString())
                put("container_module", "single_post")
                put("media_id", mediaId)
                if (trackingToken.isNotBlank()) {
                    put("tracking_token", trackingToken)
                }
            }
            val variables = JSONObject().apply {
                put("input", inputObj)
            }

            val formBuilder = FormBody.Builder()
                .add("av", actorId)
                .add("__d", "www")
                .add("__user", "0")
                .add("__a", "1")
                .add("__req", "h")
                .add("__hs", "20702.HYP:instagram_web_pkg.2.1...0")
                .add("dpr", "1")
                .add("__ccg", "EXCELLENT")
                .add("__rev", "1046913831")
                .add("__comet_req", "7")
                .add("fb_dtsg", fbDtsg)
                .add("jazoest", jazoest)
                .add("lsd", lsd)
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "usePolarisLikeMediaXIGLikeMutation")
                .add("server_timestamps", "true")
                .add("doc_id", DOC_ID_LIKE)
                .add("variables", variables.toString())

            val reqHeaders = getIgHeaders(unquoted, dynamicCsrf, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")
            val requestBuilder = Request.Builder()
                .url("https://www.instagram.com/api/graphql")
                .post(formBuilder.build())

            reqHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            return try {
                val response = client.newCall(requestBuilder.build()).execute()
                val rawBody = response.body?.string().orEmpty()
                parseGraphqlResult(rawBody, response.code)
            } catch (e: Exception) {
                IgActionResult(false, e.message ?: "Lỗi gửi Tym", "")
            }
        }

        /**
         * 4. Nhiệm vụ COMMENT theo chuẩn 100% Python TA Tool
         * GraphQL Doc ID: 27261905640092552 (PolarisPostCommentInputRevampedMutation)
         */
        fun cmt(
            mediaId: String,
            text: String,
            cookie: String,
            csrftoken: String = "",
            linkJob: String = "",
            proxy: String? = null
        ): IgActionResult {
            if (mediaId.isBlank()) {
                return IgActionResult(false, "Lỗi Media ID")
            }
            val unquoted = unquoteCookie(cookie)
            val client = buildOkHttpClient(parseProxy(proxy), timeoutSec = 15L)

            val homeUrl = if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/"
            var dynamicCsrf = if (csrftoken.isNotBlank() && csrftoken != "missing") csrftoken else extractCsrfToken(unquoted)

            var lsd = DEFAULT_LSD_COMMENT
            var fbDtsg = ""
            var jazoest = DEFAULT_JAZOEST_COMMENT

            try {
                val homeReq = Request.Builder()
                    .url(homeUrl)
                    .addHeader("user-agent", USER_AGENT_WIN)
                    .addHeader("cookie", unquoted)
                    .addHeader("sec-ch-ua", SEC_CH_UA_120)
                    .get()
                    .build()
                val homeRes = client.newCall(homeReq).execute()
                val resHome = homeRes.body?.string().orEmpty()

                homeRes.headers("Set-Cookie").forEach { sc ->
                    val cm = Pattern.compile("csrftoken=([^;\\s]+)").matcher(sc)
                    if (cm.find()) {
                        val tokenVal = cm.group(1).removeSurrounding("\"")
                        if (tokenVal.isNotBlank()) {
                            dynamicCsrf = tokenVal
                        }
                    }
                }

                val tokens = extractTokensFromHtml(resHome, DEFAULT_LSD_COMMENT, DEFAULT_JAZOEST_COMMENT)
                lsd = tokens.first
                fbDtsg = tokens.second
                jazoest = tokens.third
            } catch (_: Exception) {}

            val actorId = extractActorId(unquoted)

            val connectionsArr = JSONArray().apply {
                put("client:root:__PolarisPostComments__xdt_api__v1__media__media_id__comments__connection_connection(data:{},media_id:\"$mediaId\",sort_order:\"popular\")")
            }
            val dataObj = JSONObject().apply {
                put("comment_text", text)
                put("media_id", mediaId)
            }
            val variables = JSONObject().apply {
                put("connections", connectionsArr)
                put("data", dataObj)
            }

            val formBuilder = FormBody.Builder()
                .add("av", actorId)
                .add("__d", "www")
                .add("__user", "0")
                .add("__a", "1")
                .add("__req", "10")
                .add("__hs", "20702.HYP:instagram_web_pkg.2.1...0")
                .add("dpr", "1")
                .add("__ccg", "EXCELLENT")
                .add("__rev", "1046917461")
                .add("__comet_req", "7")
                .add("fb_dtsg", fbDtsg)
                .add("jazoest", jazoest)
                .add("lsd", lsd)
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "PolarisPostCommentInputRevampedMutation")
                .add("server_timestamps", "true")
                .add("doc_id", DOC_ID_COMMENT)
                .add("variables", variables.toString())

            val reqHeaders = getIgHeaders(unquoted, dynamicCsrf, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")
            val requestBuilder = Request.Builder()
                .url("https://www.instagram.com/api/graphql")
                .post(formBuilder.build())

            reqHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            return try {
                val response = client.newCall(requestBuilder.build()).execute()
                val rawBody = response.body?.string().orEmpty()
                parseGraphqlResult(rawBody, response.code)
            } catch (e: Exception) {
                IgActionResult(false, e.message ?: "Lỗi gửi Comment", "")
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
    }

    // ============================================================================
    // Helper methods cho instance InstagramApiClient (tương thích giao diện app)
    // ============================================================================

    fun fetchUserInfo(): InstagramUserInfo {
        val check = checkCookieIg(cookie, proxyConfig?.let { "${it.host}:${it.port}:${it.username.orEmpty()}:${it.password.orEmpty()}" })
        if (!check.isLive) {
            throw IllegalStateException("Cookie DIE hoặc không hợp lệ")
        }
        activeActorId = check.userId
        return InstagramUserInfo(
            username = check.username,
            userId = check.userId,
            fullName = check.fullName,
            biography = check.biography,
            isLive = true,
            actorId = check.userId
        )
    }

    fun fetchAccountDetails(targetUsername: String? = null): InstagramUserInfo {
        return fetchUserInfo()
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
            val resolved = resolveTargetUserId(profileUrl.ifBlank { "https://www.instagram.com/$finalId/" }, proxyStr)
            if (!resolved.isNullOrBlank()) {
                finalId = resolved
            }
        }
        val res = follow(
            targetId = finalId,
            cookie = cookie,
            csrftoken = "",
            profileUrl = profileUrl,
            proxy = proxyStr
        )
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
        val res = tym(
            mediaId = finalMediaId,
            cookie = cookie,
            csrftoken = "",
            linkJob = linkJob.ifBlank { if (mediaIdOrUrl.startsWith("http")) mediaIdOrUrl else "" },
            proxy = proxyStr
        )
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
        val res = cmt(
            mediaId = finalMediaId,
            text = commentText,
            cookie = cookie,
            csrftoken = "",
            linkJob = linkJob,
            proxy = proxyStr
        )
        if (!res.success) {
            throw IllegalStateException(res.message)
        }
        return true
    }

    fun changeProfilePicture(imageBytes: ByteArray): String? {
        return null
    }
}
