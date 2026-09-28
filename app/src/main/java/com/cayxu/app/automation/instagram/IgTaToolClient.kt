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

    private fun getIgHeaders(cookie: String, csrftoken: String, referer: String = "https://www.instagram.com/"): Headers {
        return Headers.Builder()
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
            .build()
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

    private fun extractPageTokens(client: OkHttpClient, cookie: String, targetUrl: String, defaultLsd: String, defaultJazoest: String): IgPageTokens {
        var fbDtsg = ""
        var lsd = defaultLsd
        var jazoest = defaultJazoest
        val req = Request.Builder()
            .url(if (targetUrl.isNotBlank()) targetUrl else "https://www.instagram.com/")
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val html = res.body?.string() ?: ""
                val lsdMatch = Pattern.compile("\"LSD\",\\[\\],\\{\"token\":\"([^\"]+)\"\\}").matcher(html)
                if (lsdMatch.find()) lsd = lsdMatch.group(1) ?: lsd
                var dtsgMatch = Pattern.compile("\"dtsg\":\\{\"token\":\"([^\"]+)\"").matcher(html)
                if (!dtsgMatch.find()) {
                    dtsgMatch = Pattern.compile("name=\"fb_dtsg\" value=\"([^\"]+)\"").matcher(html)
                }
                if (dtsgMatch.find()) fbDtsg = dtsgMatch.group(1) ?: ""
                val jazoestMatch = Pattern.compile("name=\"jazoest\" value=\"(\\d+)\"").matcher(html)
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

    // ================= 2. HÀM FOLLOW (follow trong Python) =================
    fun follow(client: OkHttpClient, rawCookie: String, targetId: String, profileUrl: String = ""): String {
        if (targetId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Target ID\"}"
        val cookie = try { URLDecoder.decode(rawCookie, "UTF-8") } catch (_: Exception) { rawCookie }
        val tokens = extractPageTokens(client, cookie, profileUrl, "Jfq8VQNmkkkJufHSbEE9bf", "26328")
        val csrftoken = getCsrfToken(cookie)
        val actorId = getActorId(cookie)
        val variables = JSONObject().apply {
            put("target_user_id", targetId)
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
        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(getIgHeaders(cookie, csrftoken, if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/"))
            .post(formBody)
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                res.body?.string()?.trim() ?: "{\"status\": \"error\"}"
            }
        } catch (e: Exception) {
            "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
    }

    // ================= 3. HÀM TYM / LIKE (tym trong Python) =================
    fun tym(client: OkHttpClient, rawCookie: String, mediaId: String, linkJob: String = ""): String {
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"
        val cookie = try { URLDecoder.decode(rawCookie, "UTF-8") } catch (_: Exception) { rawCookie }
        val tokens = extractPageTokens(client, cookie, "https://www.instagram.com/", "GyeZl-huflHZ0K5L3-pzBi", "26492")
        val csrftoken = getCsrfToken(cookie)
        val actorId = getActorId(cookie)
        var trackingToken = ""
        if (linkJob.isNotBlank()) {
            try {
                val reqPage = Request.Builder().url(linkJob).header("User-Agent", USER_AGENT).header("Cookie", cookie).get().build()
                client.newCall(reqPage).execute().use { res ->
                    val html = res.body?.string() ?: ""
                    val m = Pattern.compile("\"tracking_token\":\"([^\"]+)\"").matcher(html)
                    if (m.find()) trackingToken = m.group(1) ?: ""
                }
            } catch (_: Exception) {}
        }
        val inputObj = JSONObject().apply {
            put("actor_id", actorId)
            put("client_mutation_id", Random.nextInt(1000000, 9999999).toString())
            put("container_module", "single_post")
            put("media_id", mediaId)
            if (trackingToken.isNotBlank()) put("tracking_token", trackingToken)
        }
        val variables = JSONObject().apply {
            put("input", inputObj)
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
        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(getIgHeaders(cookie, csrftoken, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/"))
            .post(formBody)
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                res.body?.string()?.trim() ?: "{\"status\": \"error\"}"
            }
        } catch (e: Exception) {
            "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
    }

    // ================= 4. HÀM COMMENT (cmt trong Python) =================
    fun cmt(client: OkHttpClient, rawCookie: String, mediaId: String, text: String, linkJob: String = ""): String {
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"
        val cookie = try { URLDecoder.decode(rawCookie, "UTF-8") } catch (_: Exception) { rawCookie }
        val tokens = extractPageTokens(client, cookie, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/", "9zei3OjvTBQ-9YG6E0OMzm", "26312")
        val csrftoken = getCsrfToken(cookie)
        val actorId = getActorId(cookie)
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
        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(getIgHeaders(cookie, csrftoken, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/"))
            .post(formBody)
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                res.body?.string()?.trim() ?: "{\"status\": \"error\"}"
            }
        } catch (e: Exception) {
            "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
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
