package com.cayxu.app.automation.instagram

import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * Module tương tác Instagram chuẩn 100% từ mã nguồn Python TA Tool
 * Bỏ hoàn toàn GoMax / Web Gateway cũ.
 */
object IgTaToolClient {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val SEC_CH_UA = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""

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

    fun getIgHeaders(cookie: String, csrftoken: String, lsd: String = "", referer: String = "https://www.instagram.com/"): Headers {
        val builder = Headers.Builder()
            .add("accept", "*/*")
            .add("accept-language", "vi-VN,vi;q=0.9,fr-FR;q=0.8,fr;q=0.7,en-US;q=0.6,en;q=0.5")
            .add("content-type", "application/x-www-form-urlencoded")
            .add("cookie", cookie)
            .add("origin", "https://www.instagram.com")
            .add("priority", "u=1, i")
            .add("referer", referer)
            .add("sec-ch-ua", SEC_CH_UA)
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "same-origin")
            .add("user-agent", USER_AGENT)
            .add("x-asbd-id", "129477")
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

    // ================= 1. HÀM CHECK COOKIE IG (check_cookie_ig) =================
    fun checkCookieIg(client: OkHttpClient, cookie: String): IgCookieInfo {
        val url = "https://www.instagram.com/api/v1/accounts/edit/web_form_data/"
        val headers = Headers.Builder()
            .add("x-ig-app-id", "936619743392459")
            .add("x-requested-with", "XMLHttpRequest")
            .add("referer", "https://www.instagram.com/accounts/edit/")
            .add("cookie", cookie)
            .add("user-agent", USER_AGENT)
            .add("sec-ch-ua", SEC_CH_UA)
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

    // ================= TRÍCH XUẤT TOKENS (fb_dtsg, lsd, jazoest) =================
    data class IgPageTokens(val dtsg: String, val lsd: String, val jazoest: String)

    fun extractPageTokens(
        client: OkHttpClient,
        cookie: String,
        targetUrl: String = "https://www.instagram.com/",
        cachedDtsg: String? = null,
        cachedLsd: String? = null
    ): IgPageTokens {
        var fbDtsg = cachedDtsg?.takeIf { it.isNotBlank() } ?: ""
        var lsd = cachedLsd?.takeIf { it.isNotBlank() } ?: ""
        var jazoest = "26328"

        val req = Request.Builder()
            .url(if (targetUrl.isNotBlank()) targetUrl else "https://www.instagram.com/")
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

                // 1. Trích xuất lsd
                val lsdPatterns = listOf(
                    Pattern.compile("""\["LSD",\s*\[\],\s*\{"token":"([^"]+)""""),
                    Pattern.compile(""""LSD",\s*\[\],\s*\{"token":"([^"]+)""""),
                    Pattern.compile("""name="lsd"\s+value="([^"]+)""""),
                    Pattern.compile(""""lsd":\s*\{"token":"([^"]+)"""")
                )
                for (p in lsdPatterns) {
                    val m = p.matcher(html)
                    if (m.find()) {
                        val token = m.group(1)
                        if (!token.isNullOrBlank()) {
                            lsd = token
                            break
                        }
                    }
                }

                // 2. Trích xuất fb_dtsg
                val dtsgPatterns = listOf(
                    Pattern.compile("""\["DTSGInitialData",\s*\[\],\s*\{"token":"([^"]+)""""),
                    Pattern.compile(""""DTSGInitData":\s*\{"token":"([^"]+)""""),
                    Pattern.compile(""""dtsg":\s*\{"token":"([^"]+)""""),
                    Pattern.compile("""name="fb_dtsg"\s+value="([^"]+)""""),
                    Pattern.compile(""""token":"(AQ[^"]+)"""")
                )
                for (p in dtsgPatterns) {
                    val m = p.matcher(html)
                    if (m.find()) {
                        val token = m.group(1)
                        if (!token.isNullOrBlank()) {
                            fbDtsg = token
                            break
                        }
                    }
                }

                val jazoestMatch = Pattern.compile("""name="jazoest"\s+value="(\d+)"""").matcher(html)
                if (jazoestMatch.find()) jazoest = jazoestMatch.group(1) ?: jazoest
            }
        } catch (_: Exception) {}

        return IgPageTokens(fbDtsg, lsd, jazoest)
    }

    private fun getCsrfToken(cookie: String): String {
        val m = Pattern.compile("csrftoken=([^;]+)").matcher(cookie)
        return if (m.find()) m.group(1) ?: "missing" else "missing"
    }

    private fun getActorId(cookie: String): String {
        val m = Pattern.compile("ds_user_id=(\\d+)").matcher(cookie)
        return if (m.find()) m.group(1) ?: "0" else "0"
    }

    // ================= 2. HÀM FOLLOW (GraphQL Query Chuẩn Meta Web) =================
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
        val tokens = extractPageTokens(client, cookie, "https://www.instagram.com/", cachedDtsg, cachedLsd)
        if (tokens.dtsg.isBlank() || tokens.lsd.isBlank()) {
            return "{\"status\": \"error\", \"message\": \"Không lấy được fb_dtsg/lsd của Instagram\"}"
        }
        val csrftoken = getCsrfToken(cookie)
        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(cookie)
        val avId = if (actorId.isNotBlank() && actorId != "0") {
            if (!actorId.startsWith("178414")) "178414$actorId" else actorId
        } else actorId

        val docIds = listOf("9740159112729312", "9663809173698092", "26508036048874888")
        var lastResult = ""

        for (docId in docIds) {
            val variables = JSONObject().apply {
                put("target_user_id", targetId)
            }
            val formBody = FormBody.Builder()
                .add("av", avId)
                .add("__user", actorId)
                .add("fb_dtsg", tokens.dtsg)
                .add("jazoest", tokens.jazoest)
                .add("lsd", tokens.lsd)
                .add("doc_id", docId)
                .add("variables", variables.toString())
                .build()

            val reqHeaders = getIgHeaders(cookie, csrftoken, tokens.lsd, if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/")
                .newBuilder()
                .add("x-fb-friendly-name", "usePolarisFollowMutation")
                .build()

            val targetEndpoint = if (docId == "26508036048874888") "https://www.instagram.com/api/graphql" else "https://www.instagram.com/graphql/query"

            val req = Request.Builder()
                .url(targetEndpoint)
                .headers(reqHeaders)
                .post(formBody)
                .build()

            try {
                val resBody = client.newCall(req).execute().use { res ->
                    res.body?.string()?.trim() ?: ""
                }
                lastResult = resBody
                if (resBody.isNotBlank() && (resBody.contains("\"following\":true") || resBody.contains("xdt_create_friendship") || resBody.contains("\"status\":\"ok\""))) {
                    return resBody
                }
                // Nếu bị lỗi 1357004 hoặc rỗng, thử tiếp docId tiếp theo
                if (!resBody.contains("1357004") && resBody.isNotBlank()) {
                    return resBody
                }
            } catch (e: Exception) {
                lastResult = "{\"status\": \"error\", \"message\": \"${e.message}\"}"
            }
        }
        return if (lastResult.isNotBlank()) lastResult else "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram\"}"
    }

    // ================= 3. HÀM TYM / LIKE (tym trong Python) =================
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
        val csrftoken = getCsrfToken(cookie)
        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(cookie)
        val avId = if (actorId.isNotBlank() && actorId != "0") {
            if (!actorId.startsWith("178414")) "178414$actorId" else actorId
        } else actorId

        // 1. Ưu tiên hàng đầu: REST API Web Like chuẩn của Instagram (/api/v1/web/likes/{mediaId}/like/)
        try {
            val restReq = Request.Builder()
                .url("https://www.instagram.com/api/v1/web/likes/$mediaId/like/")
                .header("User-Agent", USER_AGENT)
                .header("X-CSRFToken", csrftoken)
                .header("X-Instagram-AJAX", "1006309104")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-IG-App-ID", "936619743392459")
                .header("X-ASBD-ID", "129477")
                .header("Referer", if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")
                .header("Cookie", cookie)
                .post(FormBody.Builder().build())
                .build()

            val restRes = client.newCall(restReq).execute().use { res ->
                res.body?.string()?.trim() ?: ""
            }
            if (restRes.isNotBlank() && (restRes.contains("\"status\":\"ok\"") || restRes.contains("\"status\": \"ok\"") || restRes.contains("\"viewer_has_liked\":true"))) {
                return restRes
            }
        } catch (_: Exception) {}

        // 2. Fallback: GraphQL Like (Doc ID 9595477160535898 hoặc 27182485238052618)
        val tokens = extractPageTokens(client, cookie, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/", cachedDtsg, cachedLsd)
        if (tokens.dtsg.isBlank() || tokens.lsd.isBlank()) {
            return "{\"status\": \"error\", \"message\": \"Không lấy được fb_dtsg/lsd của Instagram\"}"
        }

        val graphConfigs = listOf(
            Pair("9595477160535898", JSONObject().apply {
                put("media_id", mediaId)
                put("container_module", "feed_timeline")
            }),
            Pair("27182485238052618", JSONObject().apply {
                put("input", JSONObject().apply {
                    put("actor_id", actorId)
                    put("client_mutation_id", Random.nextInt(1000000, 9999999).toString())
                    put("container_module", "single_post")
                    put("media_id", mediaId)
                })
            })
        )

        var lastResult = ""
        for ((docId, vars) in graphConfigs) {
            val formBody = FormBody.Builder()
                .add("av", avId)
                .add("__d", "www")
                .add("__user", actorId)
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
                .add("doc_id", docId)
                .add("variables", vars.toString())
                .build()

            val reqHeaders = getIgHeaders(cookie, csrftoken, tokens.lsd, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")
                .newBuilder()
                .add("x-fb-friendly-name", "usePolarisLikeMediaXIGLikeMutation")
                .build()

            val req = Request.Builder()
                .url("https://www.instagram.com/graphql/query")
                .headers(reqHeaders)
                .post(formBody)
                .build()

            try {
                val resBody = client.newCall(req).execute().use { res ->
                    res.body?.string()?.trim() ?: ""
                }
                lastResult = resBody
                if (resBody.isNotBlank() && (resBody.contains("\"status\":\"ok\"") || resBody.contains("xdt_like_media") || resBody.contains("\"viewer_has_liked\":true"))) {
                    return resBody
                }
            } catch (e: Exception) {
                lastResult = "{\"status\": \"error\", \"message\": \"${e.message}\"}"
            }
        }
        return if (lastResult.isNotBlank()) lastResult else "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram khi Tym\"}"
    }

    // ================= 4. HÀM COMMENT (cmt trong Python) =================
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
        if (tokens.dtsg.isBlank() || tokens.lsd.isBlank()) {
            return "{\"status\": \"error\", \"message\": \"Không lấy được fb_dtsg/lsd của Instagram\"}"
        }
        val csrftoken = getCsrfToken(cookie)
        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(cookie)
        val avId = if (actorId.isNotBlank() && actorId != "0") {
            if (!actorId.startsWith("178414")) "178414$actorId" else actorId
        } else actorId

        val variables = JSONObject().apply {
            val connArray = org.json.JSONArray().apply {
                put("client:root:__PolarisPostComments__xdt_api__v1__media__media_id__comments__connection_connection(data:{},media_id:\"$mediaId\",sort_order:\"popular\")")
            }
            put("connections", connArray)
            put("data", JSONObject().apply {
                put("comment_text", text)
                put("media_id", mediaId)
            })
        }
        val formBody = FormBody.Builder()
            .add("av", avId)
            .add("__d", "www")
            .add("__user", actorId)
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

        val reqHeaders = getIgHeaders(cookie, csrftoken, tokens.lsd, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")
            .newBuilder()
            .add("x-fb-friendly-name", "PolarisPostCommentInputRevampedMutation")
            .build()

        val endpoints = listOf(
            "https://www.instagram.com/graphql/query",
            "https://www.instagram.com/api/graphql"
        )
        var lastResult = ""
        for (ep in endpoints) {
            val req = Request.Builder()
                .url(ep)
                .headers(reqHeaders)
                .post(formBody)
                .build()
            try {
                val resBody = client.newCall(req).execute().use { res ->
                    res.body?.string()?.trim() ?: ""
                }
                lastResult = resBody
                if (resBody.isNotBlank() && (resBody.contains("\"status\":\"ok\"") || resBody.contains("xdt_comment") || resBody.contains("\"id\":"))) {
                    return resBody
                }
                if (!resBody.contains("1357004") && resBody.isNotBlank()) {
                    return resBody
                }
            } catch (e: Exception) {
                lastResult = "{\"status\": \"error\", \"message\": \"${e.message}\"}"
            }
        }
        return if (lastResult.isNotBlank()) lastResult else "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram\"}"
    }

    // ================= 5. TRÍCH XUẤT TARGET ID (CHO FOLLOW) =================
    fun extractTargetIdFromUrl(client: OkHttpClient, cookie: String, targetUrl: String): String? {
        val req = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                val html = res.body?.string() ?: ""
                var m = Pattern.compile("\"profile_id\":\"(\\d+)\"").matcher(html)
                if (m.find()) return m.group(1)
                m = Pattern.compile("\"user_id\":\"(\\d+)\"").matcher(html)
                if (m.find()) return m.group(1)
                m = Pattern.compile("profilePage_(\\d+)").matcher(html)
                if (m.find()) return m.group(1)
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
