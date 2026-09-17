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
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
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
        const val USER_AGENT_MOBILE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"
        const val SEC_CH_UA_MOBILE = "\"Chromium\";v=\"152\", \"Not?A_Brand\";v=\"24\", \"Google Chrome\";v=\"152\""
        const val APP_ID_MOBILE = "1217981644879628"
        const val ASBD_ID_MOBILE = "359341"

        const val HS_VERSION = "20713.HYP:instagram_web_pkg.2.1...0"
        const val REV_VERSION = "1047775310"

        const val USER_AGENT_WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val SEC_CH_UA_120 = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""

        const val DOC_ID_FOLLOW = "26508036048874888"
        const val DOC_ID_LIKE = "27182485238052618"
        const val DOC_ID_COMMENT = "27261905640092552"

        const val DEFAULT_LSD_FOLLOW = "Jfq8VQNmkkkJufHSbEE9bf"
        const val DEFAULT_JAZOEST_FOLLOW = "26328"

        const val DEFAULT_LSD_LIKE = "8evCqFXFXIMbNmJjHja_w2"
        const val DEFAULT_JAZOEST_LIKE = "26442"

        const val DEFAULT_LSD_COMMENT = "9zei3OjvTBQ-9YG6E0OMzm"
        const val DEFAULT_JAZOEST_COMMENT = "26312"

        private val tokenCache = java.util.concurrent.ConcurrentHashMap<String, Triple<String, String, String>>()

        fun getOrFetchTokens(cookie: String, client: OkHttpClient, defaultLsd: String, defaultJazoest: String): Triple<String, String, String> {
            val actorId = extractActorId(cookie)
            val cacheKey = if (actorId != "0" && actorId.isNotBlank()) actorId else cookie.hashCode().toString()
            tokenCache[cacheKey]?.let { return it }

            var lsd = defaultLsd
            var fbDtsg = ""
            var jazoest = defaultJazoest

            try {
                val homeReq = Request.Builder()
                    .url("https://www.instagram.com/")
                    .addHeader("user-agent", USER_AGENT_MOBILE)
                    .addHeader("cookie", cookie)
                    .addHeader("sec-ch-ua", SEC_CH_UA_MOBILE)
                    .get()
                    .build()
                val homeRes = client.newCall(homeReq).execute()
                val resHome = homeRes.body?.string().orEmpty()
                val tokens = extractTokensFromHtml(resHome, defaultLsd, defaultJazoest)
                lsd = tokens.first
                fbDtsg = tokens.second
                jazoest = tokens.third
            } catch (_: Exception) {}

            val result = Triple(lsd, fbDtsg, jazoest)
            tokenCache[cacheKey] = result
            return result
        }

        fun getIgHeaders(
            cookie: String,
            csrftoken: String,
            referer: String = "https://www.instagram.com/",
            friendlyName: String = "",
            lsd: String = ""
        ): Map<String, String> {
            val headers = mutableMapOf(
                "accept" to "*/*",
                "accept-language" to "vi-VN,vi;q=0.9,ja-JP;q=0.8,ja;q=0.7,en-JP;q=0.6,en;q=0.5,es-ES;q=0.4,es;q=0.3,fr-FR;q=0.2,fr;q=0.1,en-US;q=0.1",
                "content-type" to "application/x-www-form-urlencoded",
                "cookie" to cookie,
                "origin" to "https://www.instagram.com",
                "priority" to "u=1, i",
                "referer" to referer,
                "sec-ch-prefers-color-scheme" to "dark",
                "sec-ch-ua" to SEC_CH_UA_MOBILE,
                "sec-ch-ua-mobile" to "?1",
                "sec-ch-ua-model" to "\"iPhone\"",
                "sec-ch-ua-platform" to "\"iOS\"",
                "sec-ch-ua-platform-version" to "\"18.5\"",
                "sec-fetch-dest" to "empty",
                "sec-fetch-mode" to "cors",
                "sec-fetch-site" to "same-origin",
                "user-agent" to USER_AGENT_MOBILE,
                "x-asbd-id" to ASBD_ID_MOBILE,
                "x-csrftoken" to csrftoken,
                "x-ig-app-id" to APP_ID_MOBILE,
                "x-ig-max-touch-points" to "1",
                "x-requested-with" to "XMLHttpRequest"
            )
            if (friendlyName.isNotBlank()) {
                headers["x-fb-friendly-name"] = friendlyName
            }
            if (lsd.isNotBlank()) {
                headers["x-fb-lsd"] = lsd
            }
            return headers
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
        private val clientMap = java.util.concurrent.ConcurrentHashMap<String, OkHttpClient>()

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

                // Bỏ qua rào cản SSL để mọi Proxy/Canary/Fiddler/Mitm không bị từ chối kết nối
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
                                null // Đã gửi xác thực nhưng vẫn lỗi, tránh loop
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
         * Trích xuất token: LSD, FB_DTSG, JAZOEST chuẩn 100% y hệt Python TA Tool
         */
        fun extractTokensFromHtml(html: String, fallbackLsd: String, fallbackJazoest: String): Triple<String, String, String> {
            var lsd = fallbackLsd
            val lsdMatch = Pattern.compile("\"LSD\",\\[],\\{\"token\":\"([^\"]+)\"}").matcher(html)
            if (lsdMatch.find()) {
                lsd = lsdMatch.group(1)
            }

            var fbDtsg = ""
            var dtsgMatch = Pattern.compile("\"dtsg\":\\{\"token\":\"([^\"]+)\"").matcher(html)
            if (dtsgMatch.find()) {
                fbDtsg = dtsgMatch.group(1)
            } else {
                dtsgMatch = Pattern.compile("name=\"fb_dtsg\" value=\"([^\"]+)\"").matcher(html)
                if (dtsgMatch.find()) {
                    fbDtsg = dtsgMatch.group(1)
                }
            }

            var jazoest = fallbackJazoest
            val jazoestMatch = Pattern.compile("name=\"jazoest\" value=\"(\\d+)\"").matcher(html)
            if (jazoestMatch.find()) {
                jazoest = jazoestMatch.group(1)
            }

            return Triple(lsd, fbDtsg, jazoest)
        }

        private fun parseGraphqlResult(rawResponse: String, httpCode: Int): IgActionResult {
            val clean = cleanJsonResponse(rawResponse)
            val json = try { JSONObject(clean) } catch (_: Exception) { null }
            val status = json?.optString("status", "") ?: ""

            val hasData = json != null && json.has("data") && !json.isNull("data")
            val isOkStatus = status.equals("ok", ignoreCase = true) || status.equals("success", ignoreCase = true)
            val isSuccess = hasData || isOkStatus || clean.contains("\"following\":true") || clean.contains("\"status\":\"ok\"")

            if (isSuccess) {
                return IgActionResult(true, "Thành công", clean)
            }

            val errorObj = json?.optJSONArray("errors")?.optJSONObject(0)
            val errorDesc = json?.optString("errorDescription")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optString("description")?.takeIf { it.isNotBlank() }
            val errorSumm = json?.optString("errorSummary")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optString("summary")?.takeIf { it.isNotBlank() }
            val msgField = json?.optString("message")?.takeIf { it.isNotBlank() }
                ?: errorObj?.optString("message")?.takeIf { it.isNotBlank() }

            val reason = msgField ?: errorDesc ?: errorSumm
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
                .addHeader("x-ig-app-id", APP_ID_MOBILE)
                .addHeader("x-asbd-id", ASBD_ID_MOBILE)
                .addHeader("x-requested-with", "XMLHttpRequest")
                .addHeader("referer", "https://www.instagram.com/accounts/edit/")
                .addHeader("cookie", unquoted)
                .addHeader("user-agent", USER_AGENT_MOBILE)
                .addHeader("sec-ch-ua", SEC_CH_UA_MOBILE)
                .addHeader("sec-ch-ua-mobile", "?1")
                .addHeader("sec-ch-ua-platform", "\"iOS\"")
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
                    .addHeader("user-agent", USER_AGENT_MOBILE)
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
                    .addHeader("user-agent", USER_AGENT_MOBILE)
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
         * 2. Nhiệm vụ FOLLOW theo chuẩn GraphQL (usePolarisFollowMutation)
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

            val dynamicCsrf = if (csrftoken.isNotBlank() && csrftoken != "missing") csrftoken else extractCsrfToken(unquoted)

            val tokens = getOrFetchTokens(unquoted, client, DEFAULT_LSD_FOLLOW, DEFAULT_JAZOEST_FOLLOW)
            val lsd = tokens.first
            val fbDtsg = tokens.second
            val jazoest = tokens.third
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
                .add("__hs", HS_VERSION)
                .add("dpr", "3")
                .add("__ccg", "GOOD")
                .add("__rev", REV_VERSION)
                .add("__comet_req", "7")
                .add("fb_dtsg", fbDtsg)
                .add("jazoest", jazoest)
                .add("lsd", lsd)
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
                .add("server_timestamps", "true")
                .add("doc_id", DOC_ID_FOLLOW)
                .add("variables", variables.toString())

            val reqHeaders = getIgHeaders(
                cookie = unquoted,
                csrftoken = dynamicCsrf,
                referer = if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/",
                friendlyName = "usePolarisFollowMutation",
                lsd = lsd
            )
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
         * 3. Nhiệm vụ TYM (LIKE) theo chuẩn GraphQL (usePolarisLikeMediaXIGLikeMutation)
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

            val dynamicCsrf = if (csrftoken.isNotBlank() && csrftoken != "missing") csrftoken else extractCsrfToken(unquoted)

            val tokens = getOrFetchTokens(unquoted, client, DEFAULT_LSD_LIKE, DEFAULT_JAZOEST_LIKE)
            val lsd = tokens.first
            val fbDtsg = tokens.second
            val jazoest = tokens.third
            val actorId = extractActorId(unquoted)

            val inputObj = JSONObject().apply {
                put("actor_id", actorId)
                put("client_mutation_id", Random.nextInt(1000000, 9999999).toString())
                put("container_module", "single_post")
                put("media_id", mediaId)
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
                .add("__hs", HS_VERSION)
                .add("dpr", "3")
                .add("__ccg", "GOOD")
                .add("__rev", REV_VERSION)
                .add("__comet_req", "7")
                .add("fb_dtsg", fbDtsg)
                .add("jazoest", jazoest)
                .add("lsd", lsd)
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "usePolarisLikeMediaXIGLikeMutation")
                .add("server_timestamps", "true")
                .add("doc_id", DOC_ID_LIKE)
                .add("variables", variables.toString())

            val reqHeaders = getIgHeaders(
                cookie = unquoted,
                csrftoken = dynamicCsrf,
                referer = if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/",
                friendlyName = "usePolarisLikeMediaXIGLikeMutation",
                lsd = lsd
            )
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
         * 4. Nhiệm vụ COMMENT theo chuẩn GraphQL (PolarisPostCommentInputRevampedMutation)
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

            val dynamicCsrf = if (csrftoken.isNotBlank() && csrftoken != "missing") csrftoken else extractCsrfToken(unquoted)

            val tokens = getOrFetchTokens(unquoted, client, DEFAULT_LSD_COMMENT, DEFAULT_JAZOEST_COMMENT)
            val lsd = tokens.first
            val fbDtsg = tokens.second
            val jazoest = tokens.third
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
                .add("__hs", HS_VERSION)
                .add("dpr", "3")
                .add("__ccg", "GOOD")
                .add("__rev", REV_VERSION)
                .add("__comet_req", "7")
                .add("fb_dtsg", fbDtsg)
                .add("jazoest", jazoest)
                .add("lsd", lsd)
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "PolarisPostCommentInputRevampedMutation")
                .add("server_timestamps", "true")
                .add("doc_id", DOC_ID_COMMENT)
                .add("variables", variables.toString())

            val reqHeaders = getIgHeaders(
                cookie = unquoted,
                csrftoken = dynamicCsrf,
                referer = if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/",
                friendlyName = "PolarisPostCommentInputRevampedMutation",
                lsd = lsd
            )
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
