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
    // ================= CẤU HÌNH HEADERS & USER-AGENT CHUẨN 100% TỪ PYTHON V4 =================
    private const val USERAGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val SEC_CH_UA = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""
    const val ASBD_ID = "129477"
    const val IG_APP_ID = "936619743392459"

    fun unquoteCookie(cookie: String): String {
        return try {
            java.net.URLDecoder.decode(cookie.trim(), "UTF-8")
        } catch (_: Exception) {
            cookie.trim()
        }
    }

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

    // ================= getIgHeaders chuẩn 100% Python v4 =================
    fun getIgHeaders(
        cookie: String,
        csrftoken: String,
        referer: String = "https://www.instagram.com/",
        friendlyName: String? = null,
        lsd: String? = null
    ): Headers {
        val b = Headers.Builder()
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
            .add("user-agent", USERAGENT)
            .add("x-asbd-id", ASBD_ID)
            .add("x-csrftoken", csrftoken)
            .add("x-ig-app-id", IG_APP_ID)
            .add("x-ig-www-claim", "0")
            .add("x-requested-with", "XMLHttpRequest")

        if (!friendlyName.isNullOrBlank()) {
            b.add("x-fb-friendly-name", friendlyName)
        }
        if (!lsd.isNullOrBlank()) {
            b.add("x-fb-lsd", lsd)
        }
        return b.build()
    }

    data class IgCookieInfo(
        val isLive: Boolean,
        val username: String = "",
        val userId: String = ""
    )

    // ================= check_cookie_ig chuẩn Web Meta 2026 =================
    fun checkCookieIg(client: OkHttpClient, cookie: String): IgCookieInfo {
        val cleanCookie = unquoteCookie(cookie)
        val csrf = getCsrfToken(cleanCookie)
        val url = "https://www.instagram.com/api/v1/accounts/edit/web_form_data/"
        val headers = Headers.Builder()
            .add("x-ig-app-id", IG_APP_ID)
            .add("x-asbd-id", ASBD_ID)
            .add("x-requested-with", "XMLHttpRequest")
            .add("x-csrftoken", csrf)
            .add("referer", "https://www.instagram.com/accounts/edit/")
            .add("cookie", cleanCookie)
            .add("user-agent", USERAGENT)
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
                        val dsMatch = Pattern.compile("ds_user_id=(\\d+)").matcher(cleanCookie)
                        val uid = if (dsMatch.find()) dsMatch.group(1) else formData.optString("id", "")
                        return IgCookieInfo(isLive = true, username = username, userId = uid ?: "")
                    }
                }
            }
        } catch (_: Exception) {}
        return IgCookieInfo(isLive = false)
    }

    data class IgPageTokens(val dtsg: String, val lsd: String, val jazoest: String)

    // ================= TRÍCH XUẤT TOKEN CHUẨN WEB META 2026 =================
    fun extractPageTokens(
        client: OkHttpClient,
        cookie: String,
        targetUrl: String = "https://www.instagram.com/",
        cachedDtsg: String? = null,
        cachedLsd: String? = null
    ): IgPageTokens {
        val cleanCookie = unquoteCookie(cookie)
        var fbDtsg = cachedDtsg?.takeIf { it.isNotBlank() } ?: ""
        var lsd = cachedLsd?.takeIf { it.isNotBlank() } ?: "nOMctdLzh1GM0vIym-YNtf"
        var jazoest = "26449"

        val target = if (targetUrl.isNotBlank()) targetUrl else "https://www.instagram.com/"
        try {
            val req = Request.Builder()
                .url(target)
                .header("User-Agent", USERAGENT)
                .header("Sec-Ch-Ua", SEC_CH_UA)
                .header("Cookie", cleanCookie)
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
        val cleanCookie = unquoteCookie(cookie)
        // 1. Ưu tiên bóc Actor ID chuẩn từ cookie rur (ví dụ: rur=VLL,17841480879673473,... hoặc rur="VLL,17841480879673473,...")
        val rurMatch = Pattern.compile("""rur=[^,;]+,(\d+)""").matcher(cleanCookie)
        if (rurMatch.find()) {
            val aid = rurMatch.group(1)
            if (!aid.isNullOrBlank() && aid != "0") return aid
        }
        // 2. Fallback ds_user_id
        val m = Pattern.compile("ds_user_id=(\\d+)").matcher(cleanCookie)
        return if (m.find()) m.group(1) ?: "0" else "0"
    }

    // ================= follow chuẩn 100% Web Meta 2026 =================
    fun follow(
        client: OkHttpClient,
        rawCookie: String = "",
        targetId: String,
        profileUrl: String = "",
        cachedDtsg: String? = null,
        cachedLsd: String? = null,
        userId: String? = null,
        cookie: String = rawCookie
    ): String {
        val effectiveCookie = unquoteCookie(cookie.ifBlank { rawCookie })
        if (targetId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Target ID\"}"
        val target = if (profileUrl.isNotBlank()) profileUrl else "https://www.instagram.com/"

        var curCookie = effectiveCookie
        var dynamicCsrftoken = getCsrfToken(curCookie)
        var fbDtsg = cachedDtsg?.takeIf { it.isNotBlank() } ?: ""
        var lsd = cachedLsd?.takeIf { it.isNotBlank() } ?: "Jfq8VQNmkkkJufHSbEE9bf"
        var jazoest = "26328"

        val cookieMap = mutableMapOf<String, String>()
        for (item in curCookie.split(";")) {
            val idx = item.indexOf("=")
            if (idx > 0) {
                val k = item.substring(0, idx).trim()
                val v = item.substring(idx + 1).trim()
                if (k.isNotBlank()) cookieMap[k] = v
            }
        }

        // BƯỚC 1: Truy cập trang profile để thiết lập navigation chain và lấy token phiên mới nhất
        try {
            val reqVisit = Request.Builder()
                .url(target)
                .header("User-Agent", USERAGENT)
                .header("Sec-Ch-Ua", SEC_CH_UA)
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cookie", curCookie)
                .get()
                .build()
            client.newCall(reqVisit).execute().use { res ->
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
                val setCookies = res.headers("Set-Cookie")
                for (sc in setCookies) {
                    val firstPart = sc.substringBefore(";")
                    val eqIdx = firstPart.indexOf("=")
                    if (eqIdx > 0) {
                        val k = firstPart.substring(0, eqIdx).trim()
                        val v = firstPart.substring(eqIdx + 1).trim()
                        cookieMap[k] = v
                    }
                }
                cookieMap["csrftoken"]?.let { dynamicCsrftoken = it }
                curCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
        } catch (_: Exception) {}

        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(curCookie)

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
            .add("doc_id", "27767812149509802")
            .add("variables", variables.toString())
            .build()

        val reqHeaders = getIgHeaders(curCookie, dynamicCsrftoken, target, friendlyName = "usePolarisFollowMutation", lsd = lsd)

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string()?.trim().orEmpty()
                if (text.isNotBlank()) return text
                return "{\"status\": \"error\", \"message\": \"HTTP ${res.code}: Phản hồi rỗng từ Instagram\"}"
            }
        } catch (e: Exception) {
            return "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
    }

    // ================= tym (chuẩn 100% Web Meta 2026 DevTools RelayModern GraphQL) =================
    fun tym(
        client: OkHttpClient,
        rawCookie: String = "",
        mediaId: String,
        linkJob: String = "",
        cachedDtsg: String? = null,
        cachedLsd: String? = null,
        userId: String? = null,
        cookie: String = rawCookie
    ): String {
        val effectiveCookie = unquoteCookie(cookie.ifBlank { rawCookie })
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"

        var curCookie = effectiveCookie
        var dynamicCsrftoken = getCsrfToken(curCookie)
        var fbDtsg = cachedDtsg?.takeIf { it.isNotBlank() } ?: ""
        var lsd = cachedLsd?.takeIf { it.isNotBlank() } ?: "GyeZl-huflHZ0K5L3-pzBi"
        var jazoest = "26492"

        val cookieMap = mutableMapOf<String, String>()
        for (item in curCookie.split(";")) {
            val idx = item.indexOf("=")
            if (idx > 0) {
                val k = item.substring(0, idx).trim()
                val v = item.substring(idx + 1).trim()
                if (k.isNotBlank()) cookieMap[k] = v
            }
        }

        val target = if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/"
        if (fbDtsg.isBlank()) {
            try {
                val reqHome = Request.Builder()
                    .url(target)
                    .header("User-Agent", USERAGENT)
                    .header("Sec-Ch-Ua", SEC_CH_UA)
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cookie", curCookie)
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
                    val setCookies = res.headers("Set-Cookie")
                    for (sc in setCookies) {
                        val firstPart = sc.substringBefore(";")
                        val eqIdx = firstPart.indexOf("=")
                        if (eqIdx > 0) {
                            val k = firstPart.substring(0, eqIdx).trim()
                            val v = firstPart.substring(eqIdx + 1).trim()
                            cookieMap[k] = v
                        }
                    }
                    cookieMap["csrftoken"]?.let { dynamicCsrftoken = it }
                    curCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                }
            } catch (_: Exception) {}
        }

        var trackingToken = ""
        if (linkJob.isNotBlank()) {
            try {
                val reqJob = Request.Builder()
                    .url(linkJob)
                    .header("User-Agent", USERAGENT)
                    .header("Sec-Ch-Ua", SEC_CH_UA)
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cookie", curCookie)
                    .get()
                    .build()
                client.newCall(reqJob).execute().use { res ->
                    val resGet = res.body?.string().orEmpty()
                    val ttMatch = Pattern.compile(""""tracking_token":"([^"]+)"""").matcher(resGet)
                    if (ttMatch.find()) {
                        trackingToken = ttMatch.group(1) ?: ""
                    }
                    val setCookies = res.headers("Set-Cookie")
                    for (sc in setCookies) {
                        val firstPart = sc.substringBefore(";")
                        val eqIdx = firstPart.indexOf("=")
                        if (eqIdx > 0) {
                            val k = firstPart.substring(0, eqIdx).trim()
                            val v = firstPart.substring(eqIdx + 1).trim()
                            cookieMap[k] = v
                        }
                    }
                    cookieMap["csrftoken"]?.let { dynamicCsrftoken = it }
                    curCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                }
            } catch (_: Exception) {}
        }

        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(curCookie)

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

        val reqHeaders = getIgHeaders(
            curCookie,
            dynamicCsrftoken,
            if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/",
            friendlyName = "usePolarisLikeMediaXIGLikeMutation",
            lsd = lsd
        )

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string()?.trim().orEmpty()
                if (text.isNotBlank()) return text
                return "{\"status\": \"error\", \"message\": \"HTTP ${res.code}: Phản hồi rỗng từ Instagram khi Tym\"}"
            }
        } catch (e: Exception) {
            return "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
    }

    // ================= cmt (chuẩn 100% Web Meta 2026 DevTools RelayModern GraphQL) =================
    fun cmt(
        client: OkHttpClient,
        rawCookie: String = "",
        mediaId: String,
        text: String,
        linkJob: String = "",
        cachedDtsg: String? = null,
        cachedLsd: String? = null,
        userId: String? = null,
        cookie: String = rawCookie
    ): String {
        val effectiveCookie = unquoteCookie(cookie.ifBlank { rawCookie })
        if (mediaId.isBlank()) return "{\"status\": \"error\", \"message\": \"Lỗi Media ID\"}"

        var curCookie = effectiveCookie
        var dynamicCsrftoken = getCsrfToken(curCookie)
        var fbDtsg = cachedDtsg?.takeIf { it.isNotBlank() } ?: ""
        var lsd = cachedLsd?.takeIf { it.isNotBlank() } ?: "9zei3OjvTBQ-9YG6E0OMzm"
        var jazoest = "26312"

        val cookieMap = mutableMapOf<String, String>()
        for (item in curCookie.split(";")) {
            val idx = item.indexOf("=")
            if (idx > 0) {
                val k = item.substring(0, idx).trim()
                val v = item.substring(idx + 1).trim()
                if (k.isNotBlank()) cookieMap[k] = v
            }
        }

        val target = if (linkJob.isNotBlank()) linkJob else "https://www.instagram.com/"
        if (fbDtsg.isBlank()) {
            try {
                val reqHome = Request.Builder()
                    .url(target)
                    .header("User-Agent", USERAGENT)
                    .header("Sec-Ch-Ua", SEC_CH_UA)
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cookie", curCookie)
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
                    val setCookies = res.headers("Set-Cookie")
                    for (sc in setCookies) {
                        val firstPart = sc.substringBefore(";")
                        val eqIdx = firstPart.indexOf("=")
                        if (eqIdx > 0) {
                            val k = firstPart.substring(0, eqIdx).trim()
                            val v = firstPart.substring(eqIdx + 1).trim()
                            cookieMap[k] = v
                        }
                    }
                    cookieMap["csrftoken"]?.let { dynamicCsrftoken = it }
                    curCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                }
            } catch (_: Exception) {}
        }

        val actorId = userId?.takeIf { it.isNotBlank() } ?: getActorId(curCookie)

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

        val reqHeaders = getIgHeaders(
            curCookie,
            dynamicCsrftoken,
            target,
            friendlyName = "PolarisPostCommentInputRevampedMutation",
            lsd = lsd
        )

        val req = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .headers(reqHeaders)
            .post(formBody)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string()?.trim().orEmpty()
                if (text.isNotBlank()) return text
                return "{\"status\": \"error\", \"message\": \"HTTP ${res.code}: Phản hồi rỗng từ Instagram khi Comment\"}"
            }
        } catch (e: Exception) {
            return "{\"status\": \"error\", \"message\": \"${e.message}\"}"
        }
    }

    // ================= TRÍCH XUẤT TARGET ID CHUẨN 100% WEB META 2026 =================
    fun extractTargetIdFromUrl(client: OkHttpClient, cookie: String, targetUrl: String): String? {
        val cleanCookie = unquoteCookie(cookie)
        val myActorId = getActorId(cleanCookie)
        val cleanUrl = if (targetUrl.startsWith("http")) targetUrl else "https://www.instagram.com/${targetUrl.trim().trim('/')}/"
        val req = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", USERAGENT)
            .header("Sec-Ch-Ua", SEC_CH_UA)
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"Windows\"")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cookie", cleanCookie)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val resHtml = res.body?.string().orEmpty()
                // 1. Thử bắt profile_id dạng JSON array hoặc string trong web script
                val patterns = listOf(
                    Pattern.compile("""profile_id:\s*\['(\d+)'"""),
                    Pattern.compile(""""profile_id":\s*"(\d+)""""),
                    Pattern.compile("""'profile_id':\s*'(\d+)'"""),
                    Pattern.compile("""profilePage_(\d+)"""),
                    Pattern.compile(""""user_id":\s*"(\d+)""""),
                    Pattern.compile(""""owner":\{"id":"(\d+)""""),
                    Pattern.compile(""""id":"(\d+)","username":"""),
                    Pattern.compile("""instagram://user\?username=[^&]*&id=(\d+)"""),
                    Pattern.compile(""""target_id":\s*"(\d+)""""),
                    Pattern.compile(""""pk":\s*"(\d+)""""),
                    Pattern.compile(""""pk_id":\s*"(\d+)"""")
                )
                for (p in patterns) {
                    val m = p.matcher(resHtml)
                    while (m.find()) {
                        val id = m.group(1)
                        if (!id.isNullOrBlank() && id != myActorId && id != "0") {
                            return id
                        }
                    }
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

            // 1. Kiểm tra lỗi nghiệp vụ từ Meta (error, errorSummary, errorDescription)
            val errCode = g.optInt("error", 0)
            val errSummary = g.optString("errorSummary", "")
            val errDesc = g.optString("errorDescription", "")
            if (errCode > 0 || errSummary.isNotBlank() || errDesc.isNotBlank()) {
                val detail = listOfNotNull(errSummary.takeIf { it.isNotBlank() }, errDesc.takeIf { it.isNotBlank() }).joinToString(": ")
                val msg = if (detail.isNotBlank()) detail else "Meta từ chối yêu cầu (Mã $errCode)"
                return IgActionResultV2(false, msg)
            }

            // 2. Kiểm tra mảng lỗi GraphQL errors
            val errors = g.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val first = errors.getJSONObject(0)
                val errMsg = first.optString("message", "Lỗi GraphQL")
                return IgActionResultV2(false, errMsg)
            }

            // 3. Kiểm tra thành công
            val data = g.optJSONObject("data")
            if (data != null) {
                val friendship = data.optJSONObject("xdt_create_friendship")
                val status = friendship?.optJSONObject("friendship_status")
                if (status != null && (status.optBoolean("following") || status.optBoolean("outgoing_request"))) {
                    return IgActionResultV2(true, "Thành công")
                }
                return IgActionResultV2(true, "Thành công")
            }

            val status = g.optString("status", "").lowercase()
            if (status == "ok" || status == "success") {
                return IgActionResultV2(true, "Thành công")
            }

            val feedbackTitle = g.optString("feedback_title", "")
            val feedbackMsg = g.optString("feedback_message", "")
            val fbDetail = listOfNotNull(feedbackTitle.takeIf { it.isNotBlank() }, feedbackMsg.takeIf { it.isNotBlank() }).joinToString(": ")
            if (fbDetail.isNotBlank()) {
                return IgActionResultV2(false, fbDetail)
            }

            val msg = g.optString("message", "Thao tác không thành công")
            return IgActionResultV2(false, msg)
        } catch (e: Exception) {
            return IgActionResultV2(false, "Lỗi phản hồi: ${e.message}")
        }
    }

    fun parseTymResponseV2(responseText: String): IgActionResultV2 {
        if (responseText.isBlank()) return IgActionResultV2(false, "Phản hồi rỗng từ Instagram khi Tym")
        try {
            val g = JSONObject(responseText.trim().removePrefix("for (;;);"))

            // 1. Kiểm tra lỗi nghiệp vụ từ Meta
            val errCode = g.optInt("error", 0)
            val errSummary = g.optString("errorSummary", "")
            val errDesc = g.optString("errorDescription", "")
            if (errCode > 0 || errSummary.isNotBlank() || errDesc.isNotBlank()) {
                val detail = listOfNotNull(errSummary.takeIf { it.isNotBlank() }, errDesc.takeIf { it.isNotBlank() }).joinToString(": ")
                val msg = if (detail.isNotBlank()) detail else "Meta từ chối Tym (Mã $errCode)"
                return IgActionResultV2(false, msg)
            }

            // 2. Kiểm tra mảng lỗi GraphQL errors
            val errors = g.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val first = errors.getJSONObject(0)
                return IgActionResultV2(false, first.optString("message", "Lỗi Tym GraphQL"))
            }

            // 3. Kiểm tra thành công
            val data = g.optJSONObject("data")
            if (data != null) {
                return IgActionResultV2(true, "Thành công")
            }

            val status = g.optString("status", "").lowercase()
            if (status == "ok" || status == "success") {
                return IgActionResultV2(true, "Thành công")
            }

            val feedbackTitle = g.optString("feedback_title", "")
            val feedbackMsg = g.optString("feedback_message", "")
            val fbDetail = listOfNotNull(feedbackTitle.takeIf { it.isNotBlank() }, feedbackMsg.takeIf { it.isNotBlank() }).joinToString(": ")
            if (fbDetail.isNotBlank()) {
                return IgActionResultV2(false, fbDetail)
            }

            val msg = g.optString("message", "Không thể Tym bài viết")
            return IgActionResultV2(false, msg)
        } catch (e: Exception) {
            return IgActionResultV2(false, "Lỗi phản hồi: ${e.message}")
        }
    }

    fun parseCmtResponseV2(responseText: String): IgActionResultV2 {
        if (responseText.isBlank()) return IgActionResultV2(false, "Phản hồi rỗng từ Instagram khi Comment")
        try {
            val g = JSONObject(responseText.trim().removePrefix("for (;;);"))

            val errCode = g.optInt("error", 0)
            val errSummary = g.optString("errorSummary", "")
            val errDesc = g.optString("errorDescription", "")
            if (errCode > 0 || errSummary.isNotBlank() || errDesc.isNotBlank()) {
                val detail = listOfNotNull(errSummary.takeIf { it.isNotBlank() }, errDesc.takeIf { it.isNotBlank() }).joinToString(": ")
                val msg = if (detail.isNotBlank()) detail else "Meta từ chối Comment (Mã $errCode)"
                return IgActionResultV2(false, msg)
            }

            val errors = g.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val first = errors.getJSONObject(0)
                return IgActionResultV2(false, first.optString("message", "Lỗi Cmt GraphQL"))
            }

            val data = g.optJSONObject("data")
            if (data != null) {
                return IgActionResultV2(true, "Thành công")
            }

            val status = g.optString("status", "").lowercase()
            if (status == "ok" || status == "success") {
                return IgActionResultV2(true, "Thành công")
            }

            val msg = g.optString("message", "Không thể Comment bài viết")
            return IgActionResultV2(false, msg)
        } catch (e: Exception) {
            return IgActionResultV2(false, "Lỗi phản hồi: ${e.message}")
        }
    }
}
