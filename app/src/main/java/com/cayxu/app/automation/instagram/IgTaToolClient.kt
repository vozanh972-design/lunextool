package com.cayxu.app.automation.instagram

import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * Module tương tác Instagram chuyển đổi 100% từ xsmmigv5.2.py
 * Không chế thêm - Không xen kẽ nhiều logic.
 */
object IgTaToolClient {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val SEC_CH_UA = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""

    // ================= BỘ NHỚ ĐỆM TOKEN (CACHE_TOKENS theo xsmmigv5.2 dòng 83) =================
    // Map: uid -> IgPageTokens(dtsg, lsd, jazoest)
    private val CACHE_TOKENS = ConcurrentHashMap<String, IgPageTokens>()

    data class IgPageTokens(val dtsg: String, val lsd: String, val jazoest: String)

    fun buildClient(proxyStr: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)

        if (!proxyStr.isNullOrBlank()) {
            val clean = proxyStr.trim().removePrefix("http://").removePrefix("https://")
            try {
                if (clean.contains("@")) {
                    val atParts = clean.split("@", limit = 2)
                    val auth = atParts[0].split(":", limit = 2)
                    val hostPort = atParts[1].split(":", limit = 2)
                    val host = hostPort[0].trim()
                    val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 8080
                    builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                    if (auth.size >= 2) {
                        builder.proxyAuthenticator { _, response ->
                            response.request.newBuilder()
                                .header("Proxy-Authorization", Credentials.basic(auth[0].trim(), auth[1].trim()))
                                .build()
                        }
                    }
                } else {
                    val p = clean.split(":")
                    if (p.size >= 2) {
                        val host = p[0].trim()
                        val port = p[1].trim().toIntOrNull() ?: 8080
                        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                        if (p.size >= 4) {
                            builder.proxyAuthenticator { _, response ->
                                response.request.newBuilder()
                                    .header("Proxy-Authorization", Credentials.basic(p[2].trim(), p[3].trim()))
                                    .build()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return builder.build()
    }

    // ================= get_ig_headers (xsmmigv5.2 dòng 444-465) =================
    fun getIgHeaders(cookie: String, csrftoken: String, lsd: String = "", referer: String = "https://www.instagram.com/"): Headers {
        val builder = Headers.Builder()
            .add("accept", "*/*")
            .add("accept-language", "vi,en-US;q=0.9,en;q=0.8,vi-VN;q=0.7")
            .add("content-type", "application/x-www-form-urlencoded")
            .add("cookie", cookie)
            .add("origin", "https://www.instagram.com")
            .add("priority", "u=1, i")
            .add("referer", referer)
            .add("sec-ch-prefers-color-scheme", "dark")
            .add("sec-ch-ua", SEC_CH_UA)
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "same-origin")
            .add("user-agent", USER_AGENT)
            .add("x-asbd-id", "359341")
            .add("x-csrftoken", csrftoken)
            .add("x-ig-app-id", "936619743392459")
            .add("x-ig-www-claim", "0")
            .add("x-requested-with", "XMLHttpRequest")

        if (lsd.isNotBlank()) {
            builder.add("x-fb-lsd", lsd)
        }
        return builder.build()
    }

    data class IgCookieInfo(
        val isLive: Boolean,
        val username: String = "",
        val userId: String = ""
    )

    // ================= check_cookie_ig (xsmmigv5.2 dòng 551-563) =================
    fun checkCookieIg(client: OkHttpClient, cookie: String): IgCookieInfo {
        val url = "https://www.instagram.com/api/v1/accounts/edit/web_form_data/"
        val headers = Headers.Builder()
            .add("x-ig-app-id", "936619743392459")
            .add("x-requested-with", "XMLHttpRequest")
            .add("referer", "https://www.instagram.com/accounts/edit/")
            .add("cookie", cookie)
            .add("user-agent", USER_AGENT)
            .add("sec-ch-ua", SEC_CH_UA)
            .add("sec-ch-ua-platform", "\"Windows\"")
            .build()
        val req = Request.Builder().url(url).headers(headers).get().build()
        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val formData = json.optJSONObject("form_data")
                if (formData != null && formData.has("username")) {
                    val username = formData.optString("username", "")
                    if (username.isNotBlank()) {
                        val dsMatch = Pattern.compile("ds_user_id=(\\d+)").matcher(cookie)
                        val uid = if (dsMatch.find()) dsMatch.group(1) else formData.optString("id", "")
                        return IgCookieInfo(isLive = true, username = username, userId = uid ?: "")
                    }
                }
            }
        } catch (_: Exception) {}
        return IgCookieInfo(isLive = false)
    }

    // ================= get_tokens_for_account (xsmmigv5.2 dòng 85-106) =================
    fun extractPageTokens(
        client: OkHttpClient,
        cookie: String,
        targetUrl: String = "https://www.instagram.com/",
        cachedDtsg: String? = null,
        cachedLsd: String? = null
    ): IgPageTokens {
        val actorIdMatch = Pattern.compile("ds_user_id=(\\d+)").matcher(cookie)
        val uid = if (actorIdMatch.find()) actorIdMatch.group(1) else "default"

        if (CACHE_TOKENS.containsKey(uid) && CACHE_TOKENS[uid]?.lsd?.isNotBlank() == true) {
            val cached = CACHE_TOKENS[uid]!!
            if (cachedDtsg.isNullOrBlank() || cached.dtsg.isNotBlank()) {
                return cached
            }
        }

        var fbDtsg = cachedDtsg?.takeIf { it.isNotBlank() } ?: ""
        var lsd = cachedLsd?.takeIf { it.isNotBlank() } ?: "7q44S3aBoCQ_1VuGgqlys8"
        var jazoest = "26485"

        val target = if (targetUrl.isNotBlank()) targetUrl else "https://www.instagram.com/"
        val req = Request.Builder()
            .url(target)
            .header("User-Agent", USER_AGENT)
            .header("Sec-Ch-Ua", SEC_CH_UA)
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"Windows\"")
            .header("Cookie", cookie)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val html = res.body?.string() ?: ""

                // 1. LSD regex: r'"LSD",\[\],{"token":"([^"]+)"}'
                val lsdMatch = Pattern.compile(""""LSD",\s*\[\],\s*\{"token":"([^"]+)"""").matcher(html)
                if (lsdMatch.find()) {
                    lsd = lsdMatch.group(1) ?: lsd
                } else {
                    val lsdFallback = Pattern.compile("""name="lsd"\s+value="([^"]+)"""").matcher(html)
                    if (lsdFallback.find()) lsd = lsdFallback.group(1) ?: lsd
                }

                // 2. fb_dtsg regex: r'"dtsg":\{"token":"([^"]+)"' or r'name="fb_dtsg" value="([^"]+)"'
                val dtsgMatch = Pattern.compile(""""dtsg":\s*\{"token":"([^"]+)"""").matcher(html)
                if (dtsgMatch.find()) {
                    fbDtsg = dtsgMatch.group(1) ?: fbDtsg
                } else {
                    val dtsgMatch2 = Pattern.compile("""name="fb_dtsg"\s+value="([^"]+)"""").matcher(html)
                    if (dtsgMatch2.find()) {
                        fbDtsg = dtsgMatch2.group(1) ?: fbDtsg
                    } else {
                        val dtsgMatch3 = Pattern.compile(""""DTSGInitialData",\s*\[\],\s*\{"token":"([^"]+)"""").matcher(html)
                        if (dtsgMatch3.find()) fbDtsg = dtsgMatch3.group(1) ?: fbDtsg
                    }
                }

                // 3. jazoest regex: r'name="jazoest" value="(\d+)"'
                val jazoestMatch = Pattern.compile("""name="jazoest"\s+value="(\d+)"""").matcher(html)
                if (jazoestMatch.find()) {
                    jazoest = jazoestMatch.group(1) ?: jazoest
                }
            }
        } catch (_: Exception) {}

        val result = IgPageTokens(fbDtsg, lsd, jazoest)
        CACHE_TOKENS[uid] = result
        return result
    }

    private fun getCsrfToken(cookie: String): String {
        val m = Pattern.compile("csrftoken=([^;]+)").matcher(cookie)
        return if (m.find()) m.group(1) ?: "missing" else "missing"
    }

    private fun getActorId(cookie: String): String {
        val m = Pattern.compile("ds_user_id=(\\d+)").matcher(cookie)
        return if (m.find()) m.group(1) ?: "0" else "0"
    }

    // ================= follow (xsmmigv5.2 dòng 703-753) =================
    fun follow(
        client: OkHttpClient,
        rawCookie: String,
        targetId: String,
        profileUrl: String = "",
        cachedDtsg: String? = null,
        cachedLsd: String? = null,
        userId: String? = null
    ): String {
        if (targetId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Target ID\"}"
        val cookie = try { URLDecoder.decode(rawCookie, "UTF-8") } catch (_: Exception) { rawCookie }
        val tokens = extractPageTokens(client, cookie, if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/", cachedDtsg, cachedLsd)
        val dynamicCsrftoken = getCsrfToken(cookie)
        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(cookie)

        val variables = JSONObject().apply {
            put("target_user_id", targetId.trim())
            put("container_module", "profile")
            put("nav_chain", "PolarisFeedRoot:feedPage:5:topnav-link,PolarisProfileRoot:profilePage:6:unexpected")
        }

        val formBody = FormBody.Builder()
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
            .add("fb_dtsg", tokens.dtsg)
            .add("jazoest", tokens.jazoest)
            .add("lsd", tokens.lsd)
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
            .add("server_timestamps", "true")
            .add("doc_id", "26508036048874888")
            .add("variables", variables.toString())
            .build()

        val reqHeaders = getIgHeaders(cookie, dynamicCsrftoken, tokens.lsd, if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/")
            .newBuilder()
            .add("x-fb-friendly-name", "usePolarisFollowMutation")
            .build()

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        var lastResult = ""
        for (attempt in 0..1) {
            try {
                val resBody = client.newCall(req).execute().use { res ->
                    res.body?.string()?.trim() ?: ""
                }
                lastResult = resBody
                if (resBody.isNotBlank()) {
                    return resBody
                }
            } catch (e: Exception) {
                lastResult = "{\"status\": \"error\", \"message\": \"${e.message}\"}"
                if (attempt == 0) {
                    try { Thread.sleep(2000L) } catch (_: Exception) {}
                    continue
                }
            }
        }
        return if (lastResult.isNotBlank()) lastResult else "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram\"}"
    }

    // ================= tym (xsmmigv5.2 dòng 754-795) =================
    // 100% GraphQL usePolarisLikeMediaXIGLikeMutation - doc_id: 27182485238052618
    fun tym(
        client: OkHttpClient,
        rawCookie: String,
        mediaId: String,
        linkJob: String = "",
        cachedDtsg: String? = null,
        cachedLsd: String? = null,
        userId: String? = null
    ): String {
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"
        val cookie = try { URLDecoder.decode(rawCookie, "UTF-8") } catch (_: Exception) { rawCookie }
        val tokens = extractPageTokens(client, cookie, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/", cachedDtsg, cachedLsd)
        val dynamicCsrftoken = getCsrfToken(cookie)
        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(cookie)

        val variables = JSONObject().apply {
            put("input", JSONObject().apply {
                put("actor_id", actorId)
                put("client_mutation_id", Random.nextInt(1000000, 9999999).toString())
                put("container_module", "single_post")
                put("media_id", mediaId.trim())
            })
        }

        val formBody = FormBody.Builder()
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
            .add("fb_dtsg", tokens.dtsg)
            .add("jazoest", tokens.jazoest)
            .add("lsd", tokens.lsd)
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisLikeMediaXIGLikeMutation")
            .add("server_timestamps", "true")
            .add("doc_id", "27182485238052618")
            .add("variables", variables.toString())
            .build()

        val reqHeaders = getIgHeaders(cookie, dynamicCsrftoken, tokens.lsd, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")
            .newBuilder()
            .add("x-fb-friendly-name", "usePolarisLikeMediaXIGLikeMutation")
            .build()

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        var lastResult = ""
        for (attempt in 0..1) {
            try {
                val resBody = client.newCall(req).execute().use { res ->
                    res.body?.string()?.trim() ?: ""
                }
                lastResult = resBody
                if (resBody.isNotBlank()) {
                    return resBody
                }
            } catch (e: Exception) {
                lastResult = "{\"status\": \"error\", \"message\": \"${e.message}\"}"
                if (attempt == 0) {
                    try { Thread.sleep(2000L) } catch (_: Exception) {}
                    continue
                }
            }
        }
        return if (lastResult.isNotBlank()) lastResult else "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram khi Tym\"}"
    }

    // ================= cmt (xsmmigv5.2 dòng 796-840) =================
    // 100% GraphQL PolarisPostCommentInputRevampedMutation - doc_id: 27261905640092552
    fun cmt(
        client: OkHttpClient,
        rawCookie: String,
        mediaId: String,
        text: String,
        linkJob: String = "",
        cachedDtsg: String? = null,
        cachedLsd: String? = null,
        userId: String? = null
    ): String {
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"
        val cookie = try { URLDecoder.decode(rawCookie, "UTF-8") } catch (_: Exception) { rawCookie }
        val tokens = extractPageTokens(client, cookie, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/", cachedDtsg, cachedLsd)
        val dynamicCsrftoken = getCsrfToken(cookie)
        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(cookie)

        val variables = JSONObject().apply {
            val connArray = org.json.JSONArray().apply {
                put("client:root:__PolarisPostComments__xdt_api__v1__media__media_id__comments__connection_connection(data:{},media_id:\"$mediaId\",sort_order:\"popular\")")
            }
            put("connections", connArray)
            put("data", JSONObject().apply {
                put("comment_text", text)
                put("media_id", mediaId.trim())
            })
        }

        val formBody = FormBody.Builder()
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
            .add("fb_dtsg", tokens.dtsg)
            .add("jazoest", tokens.jazoest)
            .add("lsd", tokens.lsd)
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "PolarisPostCommentInputRevampedMutation")
            .add("server_timestamps", "true")
            .add("doc_id", "27261905640092552")
            .add("variables", variables.toString())
            .build()

        val reqHeaders = getIgHeaders(cookie, dynamicCsrftoken, tokens.lsd, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")
            .newBuilder()
            .add("x-fb-friendly-name", "PolarisPostCommentInputRevampedMutation")
            .build()

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        var lastResult = ""
        for (attempt in 0..1) {
            try {
                val resBody = client.newCall(req).execute().use { res ->
                    res.body?.string()?.trim() ?: ""
                }
                lastResult = resBody
                if (resBody.isNotBlank()) {
                    return resBody
                }
            } catch (e: Exception) {
                lastResult = "{\"status\": \"error\", \"message\": \"${e.message}\"}"
                if (attempt == 0) {
                    try { Thread.sleep(2000L) } catch (_: Exception) {}
                    continue
                }
            }
        }
        return if (lastResult.isNotBlank()) lastResult else "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram\"}"
    }

    // ================= TRÍCH XUẤT TARGET ID (Thay thế temp_sess + impersonate bằng API chuẩn IG) =================
    fun extractTargetIdFromUrl(client: OkHttpClient, cookie: String, targetUrl: String): String? {
        val myActorId = getActorId(cookie)
        val cleanTarget = targetUrl.trim().removeSuffix("/").substringAfterLast("/").removePrefix("@")

        // 1. Thử lấy nhanh bằng API web_profile_info chuẩn Instagram Web
        if (cleanTarget.isNotBlank() && !cleanTarget.startsWith("p") && !cleanTarget.startsWith("reel")) {
            val apiReq = Request.Builder()
                .url("https://www.instagram.com/api/v1/users/web_profile_info/?username=$cleanTarget")
                .header("User-Agent", USER_AGENT)
                .header("X-IG-App-ID", "936619743392459")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://www.instagram.com/$cleanTarget/")
                .get()
                .build()
            try {
                client.newCall(apiReq).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (body.contains("\"id\":\"")) {
                        val m = Pattern.compile(""""id":"(\d+)"""").matcher(body)
                        if (m.find()) {
                            val id = m.group(1)
                            if (!id.isNullOrBlank() && id != myActorId) return id
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Lấy HTML trực tiếp với cookie session của nick (timeout ngắn 8s không gây treo)
        val fastClient = client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val reqWithCookie = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .get()
            .build()
        try {
            fastClient.newCall(reqWithCookie).execute().use { res ->
                val html = res.body?.string().orEmpty()
                var m = Pattern.compile("\"profile_id\":\"(\\d+)\"").matcher(html)
                if (m.find()) {
                    val id = m.group(1)
                    if (!id.isNullOrBlank() && id != myActorId) return id
                }
                m = Pattern.compile("profilePage_(\\d+)").matcher(html)
                if (m.find()) {
                    val id = m.group(1)
                    if (!id.isNullOrBlank() && id != myActorId) return id
                }
                m = Pattern.compile("property=\"instapp:owner_user_id\"\\s+content=\"(\\d+)\"").matcher(html)
                if (m.find()) {
                    val id = m.group(1)
                    if (!id.isNullOrBlank() && id != myActorId) return id
                }
                m = Pattern.compile("\"user_id\":\"(\\d+)\"").matcher(html)
                while (m.find()) {
                    val id = m.group(1)
                    if (!id.isNullOrBlank() && id != myActorId) return id
                }
            }
        } catch (_: Exception) {}

        return null
    }

    // ================= parse_follow_response (xsmmigv5.2 dòng 492-533) =================
    data class IgFollowParseResult(
        val isSuccess: Boolean,
        val isPrivate: Boolean = false,
        val message: String
    )

    fun parseFollowResponse(responseText: String): IgFollowParseResult {
        if (responseText.isBlank()) {
            return IgFollowParseResult(false, message = "Phản hồi rỗng từ Instagram")
        }
        var cleanBody = responseText.trim()
        if (cleanBody.startsWith("for (;;);")) {
            cleanBody = cleanBody.removePrefix("for (;;);").trim()
        }
        val g = try { JSONObject(cleanBody) } catch (e: Exception) {
            return IgFollowParseResult(false, message = "Lỗi parse JSON: ${e.message}")
        }

        // 1. Kiểm tra errors GraphQL
        val errors = g.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val errObj = errors.optJSONObject(0)
            val errStr = errObj?.optString("message") ?: errors.optString(0)
            val code = errObj?.optInt("code", 0) ?: 0
            val friendly = when (code) {
                1357004 -> "Chặn tính năng tương tác (Mã 1357004)"
                1357001 -> "Cookie hết hạn / Lỗi phiên (Mã 1357001)"
                else -> if (errStr.isNotBlank()) errStr else "Lỗi GraphQL Meta ($code)"
            }
            return IgFollowParseResult(false, message = "Instagram báo lỗi: $friendly")
        }

        // 2. Kiểm tra status == fail
        if (g.optString("status").equals("fail", ignoreCase = true)) {
            val msg = g.optString("message", "status fail")
            if (msg.contains("feedback_required", ignoreCase = true)) {
                return IgFollowParseResult(false, message = "Chặn tính năng: $msg")
            }
            if (msg.contains("checkpoint_required", ignoreCase = true) || g.has("checkpoint_url")) {
                return IgFollowParseResult(false, message = "Dính Checkpoint Instagram")
            }
            if (msg.contains("login_required", ignoreCase = true)) {
                return IgFollowParseResult(false, message = "Cookie DIE / Yêu cầu đăng nhập lại")
            }
            return IgFollowParseResult(false, message = "Instagram thất bại: $msg")
        }

        val data = g.optJSONObject("data")
        if (data == null) {
            return IgFollowParseResult(false, message = "Instagram không trả về data")
        }

        // 3. Tìm friendship_status trong data
        var friendship: JSONObject? = null
        val keys = data.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = data.opt(k)
            if (v is JSONObject && v.has("friendship_status")) {
                friendship = v.optJSONObject("friendship_status")
                break
            } else if (k == "friendship_status" && v is JSONObject) {
                friendship = v
                break
            }
        }

        if (friendship != null) {
            if (friendship.optBoolean("outgoing_request", false)) {
                return IgFollowParseResult(
                    isSuccess = false,
                    isPrivate = true,
                    message = "Tài khoản Riêng tư (Private) - Chỉ gửi yêu cầu chờ duyệt, chưa follow thật!"
                )
            }
            if (friendship.optBoolean("following", false)) {
                return IgFollowParseResult(true, isPrivate = false, message = "Thành công")
            }
            return IgFollowParseResult(false, isPrivate = false, message = "Instagram chưa xác nhận theo dõi (following: false)")
        }

        if (g.optString("status") in listOf("ok", "success")) {
            return IgFollowParseResult(true, isPrivate = false, message = "Thành công")
        }

        return IgFollowParseResult(false, isPrivate = false, message = "Không xác định được trạng thái follow")
    }

    // ================= parse_tym_cmt_response (xsmmigv5.2 dòng 534-549) =================
    data class IgActionParseResult(
        val isSuccess: Boolean,
        val message: String
    )

    fun parseTymCmtResponse(responseText: String): IgActionParseResult {
        if (responseText.isBlank()) {
            return IgActionParseResult(false, "Phản hồi rỗng từ Instagram")
        }
        var cleanBody = responseText.trim()
        if (cleanBody.startsWith("for (;;);")) {
            cleanBody = cleanBody.removePrefix("for (;;);").trim()
        }
        val g = try { JSONObject(cleanBody) } catch (e: Exception) {
            return IgActionParseResult(false, "Lỗi parse JSON: ${e.message}")
        }
        val errors = g.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val errObj = errors.optJSONObject(0)
            val errStr = errObj?.optString("message") ?: errors.optString(0)
            return IgActionParseResult(false, errStr)
        }
        if (g.optString("status").equals("fail", ignoreCase = true)) {
            return IgActionParseResult(false, g.optString("message", "Instagram báo fail"))
        }
        if (g.optJSONObject("data") == null && g.optString("status") != "ok") {
            return IgActionParseResult(false, "Không có data phản hồi")
        }
        return IgActionParseResult(true, "Thành công")
    }
}
