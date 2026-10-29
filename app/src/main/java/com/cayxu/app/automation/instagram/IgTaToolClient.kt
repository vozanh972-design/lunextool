package com.cayxu.app.automation.instagram

import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * Module tương tác Instagram chuẩn 100% từ xsmmigv2.py (Bản TA Tool V2)
 * Giữ nguyên các tham số, headers, default tokens, parser chuẩn V2.
 */
object IgTaToolClient {
    // ================= CẤU HÌNH HEADERS & USER-AGENT CHUẨN V2 =================
    private const val USERAGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val SEC_CH_UA_120 = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""

    fun buildClient(proxyStr: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
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

    // ================= get_ig_headers (xsmmigv2.py) =================
    fun getIgHeaders(cookie: String, csrftoken: String, referer: String = "https://www.instagram.com/"): Headers {
        return Headers.Builder()
            .add("accept", "*/*")
            .add("accept-language", "vi-VN,vi;q=0.9,fr-FR;q=0.8,fr;q=0.7,en-US;q=0.6,en;q=0.5")
            .add("content-type", "application/x-www-form-urlencoded")
            .add("cookie", cookie)
            .add("origin", "https://www.instagram.com")
            .add("priority", "u=1, i")
            .add("referer", referer)
            .add("sec-ch-ua", SEC_CH_UA_120)
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "same-origin")
            .add("user-agent", USERAGENT)
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

    // ================= check_cookie_ig (xsmmigv2.py) =================
    fun checkCookieIg(client: OkHttpClient, cookie: String): IgCookieInfo {
        val url = "https://www.instagram.com/api/v1/accounts/edit/web_form_data/"
        val headers = Headers.Builder()
            .add("x-ig-app-id", "936619743392459")
            .add("x-requested-with", "XMLHttpRequest")
            .add("referer", "https://www.instagram.com/accounts/edit/")
            .add("cookie", cookie)
            .add("user-agent", USERAGENT)
            .add("sec-ch-ua", SEC_CH_UA_120)
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

    data class IgPageTokens(val dtsg: String, val lsd: String, val jazoest: String)

    // ================= TRÍCH XUẤT TOKEN CHUẨN V2 =================
    fun extractPageTokens(
        client: OkHttpClient,
        cookie: String,
        targetUrl: String = "https://www.instagram.com/",
        cachedDtsg: String? = null,
        cachedLsd: String? = null
    ): IgPageTokens {
        var fbDtsg = cachedDtsg?.takeIf { it.isNotBlank() } ?: ""
        var lsd = cachedLsd?.takeIf { it.isNotBlank() } ?: "Jfq8VQNmkkkJufHSbEE9bf"
        var jazoest = "26328"

        val target = if (targetUrl.isNotBlank()) targetUrl else "https://www.instagram.com/"
        try {
            val req = Request.Builder()
                .url(target)
                .header("User-Agent", USERAGENT)
                .header("Sec-Ch-Ua", SEC_CH_UA_120)
                .header("Cookie", cookie)
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val resHome = res.body?.string().orEmpty()
                val lsdMatch = Pattern.compile(""""LSD",\s*\[\],\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (lsdMatch.find()) {
                    lsd = lsdMatch.group(1) ?: lsd
                }
                var dtsgMatch = Pattern.compile(""""dtsg":\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (!dtsgMatch.find()) {
                    dtsgMatch = Pattern.compile("""name="fb_dtsg"\s+value="([^"]+)"""").matcher(resHome)
                }
                if (dtsgMatch.find()) {
                    fbDtsg = dtsgMatch.group(1) ?: fbDtsg
                }
                val jazoestMatch = Pattern.compile("""name="jazoest"\s+value="(\d+)"""").matcher(resHome)
                if (jazoestMatch.find()) {
                    jazoest = jazoestMatch.group(1) ?: jazoest
                }
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

    // ================= follow (chuẩn 100% xsmmigv2.py dòng 222-277) =================
    fun follow(
        client: OkHttpClient,
        cookie: String,
        targetId: String,
        profileUrl: String = "",
        userId: String? = null
    ): String {
        if (targetId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Target ID\"}"
        val target = if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/"

        var fbDtsg = ""
        var lsd = "Jfq8VQNmkkkJufHSbEE9bf"
        var jazoest = "26328"

        try {
            val reqHome = Request.Builder()
                .url(target)
                .header("User-Agent", USERAGENT)
                .header("Sec-Ch-Ua", SEC_CH_UA_120)
                .header("Cookie", cookie)
                .get()
                .build()
            client.newCall(reqHome).execute().use { res ->
                val resHome = res.body?.string().orEmpty()
                val lsdMatch = Pattern.compile(""""LSD",\s*\[\],\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (lsdMatch.find()) {
                    lsd = lsdMatch.group(1) ?: lsd
                }
                var dtsgMatch = Pattern.compile(""""dtsg":\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (!dtsgMatch.find()) {
                    dtsgMatch = Pattern.compile("""name="fb_dtsg"\s+value="([^"]+)"""").matcher(resHome)
                }
                if (dtsgMatch.find()) {
                    fbDtsg = dtsgMatch.group(1) ?: fbDtsg
                }
                val jazoestMatch = Pattern.compile("""name="jazoest"\s+value="(\d+)"""").matcher(resHome)
                if (jazoestMatch.find()) {
                    jazoest = jazoestMatch.group(1) ?: jazoest
                }
            }
        } catch (_: Exception) {}

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
            .add("fb_dtsg", fbDtsg)
            .add("jazoest", jazoest)
            .add("lsd", lsd)
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
            .add("server_timestamps", "true")
            .add("doc_id", "26508036048874888")
            .add("variables", variables.toString())
            .build()

        val reqHeaders = getIgHeaders(cookie, dynamicCsrftoken, target)

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string()?.trim().orEmpty()
                if (text.isNotBlank()) return text
            }
        } catch (e: Exception) {
            return "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
        return "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram\"}"
    }

    // ================= tym (chuẩn 100% xsmmigv2.py dòng 278-345) =================
    fun tym(
        client: OkHttpClient,
        cookie: String,
        mediaId: String,
        linkJob: String = "",
        userId: String? = null
    ): String {
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"

        var fbDtsg = ""
        var lsd = "GyeZl-huflHZ0K5L3-pzBi"
        var jazoest = "26492"

        try {
            val reqHome = Request.Builder()
                .url("https://www.instagram.com/")
                .header("User-Agent", USERAGENT)
                .header("Sec-Ch-Ua", SEC_CH_UA_120)
                .header("Cookie", cookie)
                .get()
                .build()
            client.newCall(reqHome).execute().use { res ->
                val resHome = res.body?.string().orEmpty()
                val lsdMatch = Pattern.compile(""""LSD",\s*\[\],\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (lsdMatch.find()) {
                    lsd = lsdMatch.group(1) ?: lsd
                }
                var dtsgMatch = Pattern.compile(""""dtsg":\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (!dtsgMatch.find()) {
                    dtsgMatch = Pattern.compile("""name="fb_dtsg"\s+value="([^"]+)"""").matcher(resHome)
                }
                if (dtsgMatch.find()) {
                    fbDtsg = dtsgMatch.group(1) ?: fbDtsg
                }
                val jazoestMatch = Pattern.compile("""name="jazoest"\s+value="(\d+)"""").matcher(resHome)
                if (jazoestMatch.find()) {
                    jazoest = jazoestMatch.group(1) ?: jazoest
                }
            }
        } catch (_: Exception) {}

        val dynamicCsrftoken = getCsrfToken(cookie)
        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(cookie)

        var trackingToken = ""
        if (linkJob.isNotBlank()) {
            try {
                val reqJob = Request.Builder()
                    .url(linkJob)
                    .header("User-Agent", USERAGENT)
                    .header("Sec-Ch-Ua", SEC_CH_UA_120)
                    .header("Cookie", cookie)
                    .get()
                    .build()
                client.newCall(reqJob).execute().use { res ->
                    val resGet = res.body?.string().orEmpty()
                    val ttMatch = Pattern.compile(""""tracking_token":"([^"]+)"""").matcher(resGet)
                    if (ttMatch.find()) {
                        trackingToken = ttMatch.group(1) ?: ""
                    }
                }
            } catch (_: Exception) {}
        }

        val variables = JSONObject().apply {
            val inputObj = JSONObject().apply {
                put("actor_id", actorId)
                put("client_mutation_id", Random.nextInt(1000000, 9999999).toString())
                put("container_module", "single_post")
                put("media_id", mediaId.trim())
                if (trackingToken.isNotBlank()) {
                    put("tracking_token", trackingToken)
                }
            }
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
            .add("fb_dtsg", fbDtsg)
            .add("jazoest", jazoest)
            .add("lsd", lsd)
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "usePolarisLikeMediaXIGLikeMutation")
            .add("server_timestamps", "true")
            .add("doc_id", "27182485238052618")
            .add("variables", variables.toString())
            .build()

        val reqHeaders = getIgHeaders(cookie, dynamicCsrftoken, if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/")

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string()?.trim().orEmpty()
                if (text.isNotBlank()) return text
            }
        } catch (e: Exception) {
            return "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
        return "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram khi Tym\"}"
    }

    // ================= cmt (chuẩn 100% xsmmigv2.py dòng 346-407) =================
    fun cmt(
        client: OkHttpClient,
        cookie: String,
        mediaId: String,
        text: String,
        linkJob: String = "",
        userId: String? = null
    ): String {
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"

        var fbDtsg = ""
        var lsd = "9zei3OjvTBQ-9YG6E0OMzm"
        var jazoest = "26312"

        val target = if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/"
        try {
            val reqHome = Request.Builder()
                .url(target)
                .header("User-Agent", USERAGENT)
                .header("Sec-Ch-Ua", SEC_CH_UA_120)
                .header("Cookie", cookie)
                .get()
                .build()
            client.newCall(reqHome).execute().use { res ->
                val resHome = res.body?.string().orEmpty()
                val lsdMatch = Pattern.compile(""""LSD",\s*\[\],\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (lsdMatch.find()) {
                    lsd = lsdMatch.group(1) ?: lsd
                }
                var dtsgMatch = Pattern.compile(""""dtsg":\s*\{"token":"([^"]+)"""").matcher(resHome)
                if (!dtsgMatch.find()) {
                    dtsgMatch = Pattern.compile("""name="fb_dtsg"\s+value="([^"]+)"""").matcher(resHome)
                }
                if (dtsgMatch.find()) {
                    fbDtsg = dtsgMatch.group(1) ?: fbDtsg
                }
                val jazoestMatch = Pattern.compile("""name="jazoest"\s+value="(\d+)"""").matcher(resHome)
                if (jazoestMatch.find()) {
                    jazoest = jazoestMatch.group(1) ?: jazoest
                }
            }
        } catch (_: Exception) {}

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
            .add("fb_dtsg", fbDtsg)
            .add("jazoest", jazoest)
            .add("lsd", lsd)
            .add("fb_api_caller_class", "RelayModern")
            .add("fb_api_req_friendly_name", "PolarisPostCommentInputRevampedMutation")
            .add("server_timestamps", "true")
            .add("doc_id", "27261905640092552")
            .add("variables", variables.toString())
            .build()

        val reqHeaders = getIgHeaders(cookie, dynamicCsrftoken, target)

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string()?.trim().orEmpty()
                if (text.isNotBlank()) return text
            }
        } catch (e: Exception) {
            return "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
        return "{\"status\": \"error\", \"message\": \"Phản hồi rỗng từ Instagram khi Comment\"}"
    }

    // ================= TRÍCH XUẤT TARGET ID (xsmmigv2.py dòng 600-615) =================
    fun extractTargetIdFromUrl(client: OkHttpClient, cookie: String, targetUrl: String): String? {
        val myActorId = getActorId(cookie)
        val cleanTarget = targetUrl.trim().removeSuffix("/").substringAfterLast("/").removePrefix("@")

        // 1. Thử lấy qua API web_profile_info có Cookie
        if (cleanTarget.isNotBlank() && !cleanTarget.startsWith("p") && !cleanTarget.startsWith("reel")) {
            val apiReq = Request.Builder()
                .url("https://www.instagram.com/api/v1/users/web_profile_info/?username=$cleanTarget")
                .header("User-Agent", USERAGENT)
                .header("X-IG-App-ID", "936619743392459")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://www.instagram.com/$cleanTarget/")
                .header("Cookie", cookie)
                .get()
                .build()
            try {
                client.newCall(apiReq).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val id = root.optJSONObject("data")?.optJSONObject("user")?.optString("id")
                    if (!id.isNullOrBlank() && id != myActorId) return id
                }
            } catch (_: Exception) {}

            // 2. Thử qua topsearch có Cookie
            val searchReq = Request.Builder()
                .url("https://www.instagram.com/web/search/topsearch/?context=blended&query=$cleanTarget")
                .header("User-Agent", USERAGENT)
                .header("X-IG-App-ID", "936619743392459")
                .header("Cookie", cookie)
                .get()
                .build()
            try {
                client.newCall(searchReq).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val users = root.optJSONArray("users")
                    if (users != null) {
                        for (i in 0 until users.length()) {
                            val u = users.getJSONObject(i).optJSONObject("user") ?: continue
                            if (u.optString("username").equals(cleanTarget, ignoreCase = true)) {
                                val pk = u.optString("pk").ifBlank { u.optString("id") }
                                if (pk.isNotBlank() && pk != myActorId) return pk
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Chuẩn V2: GET link_job với Cookie và parse regex
        val req = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", USERAGENT)
            .header("Cookie", cookie)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val resHtml = res.body?.string().orEmpty()
                var m = Pattern.compile(""""profile_id":"(\d+)"""").matcher(resHtml)
                if (!m.find()) {
                    m = Pattern.compile(""""user_id":"(\d+)"""").matcher(resHtml)
                }
                if (!m.find()) {
                    m = Pattern.compile("""profilePage_(\d+)""").matcher(resHtml)
                }
                if (m.find()) {
                    val id = m.group(1)
                    if (!id.isNullOrBlank() && id != myActorId) return id
                }
            }
        } catch (_: Exception) {}

        return null
    }

    // ================= BỘ PARSE PHẢN HỒI CHUẨN V2 =================
    data class IgActionResultV2(val isSuccess: Boolean, val message: String)

    fun parseFollowResponseV2(responseText: String): IgActionResultV2 {
        if (responseText.isBlank()) return IgActionResultV2(false, "Phản hồi rỗng từ Instagram")
        try {
            val g = JSONObject(responseText.trim().removePrefix("for (;;);"))
            val hasData = g.has("data") && !g.isNull("data")
            val status = g.optString("status", "").lowercase()
            if (!hasData && status != "ok" && status != "success") {
                val msg = g.optString("message", "Block")
                return IgActionResultV2(false, msg)
            }
            return IgActionResultV2(true, "Thành công")
        } catch (e: Exception) {
            return IgActionResultV2(false, "Lỗi JSON: ${e.message}")
        }
    }

    fun parseTymResponseV2(responseText: String): IgActionResultV2 {
        if (responseText.isBlank()) return IgActionResultV2(false, "Phản hồi rỗng từ Instagram khi Tym")
        try {
            val g = JSONObject(responseText.trim().removePrefix("for (;;);"))
            val hasData = g.has("data") && !g.isNull("data")
            val status = g.optString("status", "").lowercase()
            if (!hasData && status != "ok") {
                val msg = g.optString("message", "Bị IG chặn thao tác")
                return IgActionResultV2(false, msg)
            }
            return IgActionResultV2(true, "Thành công")
        } catch (e: Exception) {
            return IgActionResultV2(false, "Lỗi JSON: ${e.message}")
        }
    }

    fun parseCmtResponseV2(responseText: String): IgActionResultV2 {
        if (responseText.isBlank()) return IgActionResultV2(false, "Phản hồi rỗng từ Instagram khi Comment")
        try {
            val g = JSONObject(responseText.trim().removePrefix("for (;;);"))
            val hasData = g.has("data") && !g.isNull("data")
            val status = g.optString("status", "").lowercase()
            if (!hasData && status != "ok") {
                val msg = g.optString("message", "Bị IG chặn cmt")
                return IgActionResultV2(false, msg)
            }
            return IgActionResultV2(true, "Thành công")
        } catch (e: Exception) {
            return IgActionResultV2(false, "Lỗi JSON: ${e.message}")
        }
    }
}
