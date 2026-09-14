package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookPageItem
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Keep
enum class AccountFieldType(val displayName: String) {
    USERNAME("Tài khoản"),
    PASSWORD("Mật khẩu"),
    TWO_FACTOR("2FA"),
    COOKIE("Cookie"),
    PROXY("Proxy"),
    TOKEN("Token")
}

@Keep
data class FacebookPage(
    val pageId: String,
    val pageName: String,
    val pageToken: String? = null,
    val additionalProfileId: String? = null,
    val parentUserId: String? = null
)

class FacebookAccountManager {

    companion object {
        const val BASE_URL = "https://www.facebook.com"
        const val GRAPH_BASE_URL = "https://graph.facebook.com"
        const val GRAPH_API_VERSION = "v19.0"
        const val GRAPH_ME_FIELDS = "id,name,birthday,email,facebook_pages{access_token,additional_profile_id,id,name}"

        val C_USER_REGEX = Pattern.compile("c_user=([0-9]+)")
        val XS_REGEX = Pattern.compile("xs=([^;]+)")
        val DTSG_REGEX = Pattern.compile("\\[\"DTSGInitialData\",\\[\\],\\{\"token\":\"(.*?)\"\\}")
        val LSD_REGEX = Pattern.compile("\\[\"LSD\",\\[\\],\\{\"token\":\"(.*?)\"\\}")
        val JAZOEST_REGEX = Pattern.compile("jazoest=(.*?)\"")

        val USER_ID_REGEX = Pattern.compile("\"__typename\"\\s*:\\s*\"User\"[^}]*?\"id\"\\s*:\\s*\"?([0-9]+)")
        val USER_NAME_REGEX = Pattern.compile("\"__typename\"\\s*:\\s*\"User\"[^}]*?\"name\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")

        const val DEFAULT_DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val DEFAULT_DALVIK_UA = "Dalvik/2.1.0 (Linux; U; Android 9; 23113RKC6C) [FBAN/FB4A;FBAV/417.0.0.33.65;]"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Chuyển đổi token sang App ID 350685531728 (EAAAA)
     */
    fun convertToken(accessToken: String, targetAppId: String = "350685531728", proxyStr: String? = null): String? {
        val client = if (!proxyStr.isNullOrEmpty()) buildProxiedClient(proxyStr) else httpClient
        val form = FormBody.Builder()
            .add("access_token", accessToken)
            .add("format", "json")
            .add("new_app_id", targetAppId)
            .add("generate_session_cookies", "1")
            .build()

        val request = Request.Builder()
            .url("https://api.facebook.com/method/auth.getSessionforApp")
            .post(form)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                json.optString("access_token", null)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lấy token và info trực tiếp từ Cookie qua getSessionForApp
     */
    fun getTokenFromCookie(cookieStr: String, proxyStr: String? = null): FacebookAccount? {
        val client = if (!proxyStr.isNullOrEmpty()) buildProxiedClient(proxyStr) else httpClient

        val cUserMatcher = C_USER_REGEX.matcher(cookieStr)
        val uid = if (cUserMatcher.find()) cUserMatcher.group(1) ?: "" else ""

        val form = FormBody.Builder()
            .add("format", "json")
            .add("generate_session_cookies", "1")
            .build()

        val request = Request.Builder()
            .url("https://api.facebook.com/method/auth.getSessionForApp")
            .header("Cookie", cookieStr)
            .header("User-Agent", com.cayxu.app.util.NativeSecurity.getFbDalvikUA())
            .post(form)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                if (json.has("access_token")) {
                    val rawToken = json.getString("access_token")
                    val eaaaa = convertToken(rawToken, "350685531728", proxyStr) ?: rawToken
                    
                    // Lấy session cookies mới
                    val cookieBuilder = StringBuilder()
                    if (json.has("session_cookies")) {
                        val arr = json.getJSONArray("session_cookies")
                        for (i in 0 until arr.length()) {
                            val c = arr.getJSONObject(i)
                            val name = c.optString("name")
                            val value = c.optString("value")
                            if (name.isNotBlank()) cookieBuilder.append("$name=$value; ")
                        }
                    }
                    val finalCookie = if (cookieBuilder.isNotEmpty()) cookieBuilder.toString().trimEnd(' ', ';') else cookieStr

                    // Lấy đầy đủ thông tin bằng Token
                    fetchAccountDetailsWithToken(eaaaa, proxyStr).copy(
                        uid = if (uid.isNotBlank()) uid else json.optString("uid", ""),
                        note = finalCookie
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 1. Parse danh sách tài khoản nhập từ Dialog Thêm tài khoản
     */
    fun parseAccountsInput(
        rawText: String,
        formatFields: List<AccountFieldType>,
        delimiter: String = "|"
    ): List<FacebookAccount> {
        val results = mutableListOf<FacebookAccount>()
        val lines = rawText.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            var username = ""
            var password = ""
            var twoFactor = ""
            var cookie = ""
            var proxy = ""
            var token = ""

            if (formatFields.isEmpty()) {
                // Tự động nhận diện thông minh khi người dùng không chọn nút định dạng
                if (trimmed.startsWith("EAA") && !trimmed.contains("|")) {
                    token = trimmed
                } else if ((trimmed.contains("c_user=") || trimmed.contains("xs=")) && !trimmed.contains("|")) {
                    cookie = trimmed
                } else if (trimmed.contains("|")) {
                    val parts = trimmed.split("|").map { it.trim() }
                    if (parts.size == 1) {
                        if (parts[0].startsWith("EAA")) token = parts[0]
                        else if (parts[0].contains("c_user=")) cookie = parts[0]
                        else username = parts[0]
                    } else if (parts.size == 2) {
                        username = parts[0]
                        if (parts[1].contains("c_user=") || parts[1].contains("xs=")) {
                            cookie = parts[1]
                        } else if (parts[1].startsWith("EAA")) {
                            token = parts[1]
                        } else {
                            password = parts[1]
                        }
                    } else if (parts.size == 3) {
                        username = parts[0]
                        password = parts[1]
                        if (parts[2].contains("c_user=") || parts[2].contains("xs=")) {
                            cookie = parts[2]
                        } else if (parts[2].startsWith("EAA")) {
                            token = parts[2]
                        } else {
                            twoFactor = parts[2]
                        }
                    } else if (parts.size >= 4) {
                        username = parts[0]
                        password = parts[1]
                        twoFactor = parts[2]
                        val remaining = parts.subList(3, parts.size)
                        remaining.forEach { p ->
                            if (p.startsWith("EAA")) token = p
                            else if (p.contains("c_user=") || p.contains("xs=")) cookie = p
                            else if (p.contains(":") && p.any { it.isDigit() }) proxy = p
                        }
                    }
                } else {
                    username = trimmed
                }
            } else {
                val parts = trimmed.split(delimiter).map { it.trim() }
                for (i in formatFields.indices) {
                    val value = parts.getOrElse(i) { "" }
                    when (formatFields[i]) {
                        AccountFieldType.USERNAME -> username = value
                        AccountFieldType.PASSWORD -> password = value
                        AccountFieldType.TWO_FACTOR -> twoFactor = value
                        AccountFieldType.COOKIE -> cookie = value
                        AccountFieldType.PROXY -> proxy = value
                        AccountFieldType.TOKEN -> token = value
                    }
                }
            }

            var uid = username
            if (uid.isEmpty() && cookie.isNotEmpty()) {
                val matcher = C_USER_REGEX.matcher(cookie)
                if (matcher.find()) {
                    uid = matcher.group(1) ?: ""
                }
            }
            if (uid.isEmpty() && token.isNotEmpty()) {
                uid = "Token"
            }
            if (uid.isEmpty()) {
                uid = "FB_${System.currentTimeMillis() % 1000000}"
            }

            val avatarUrl = if (uid.isNotBlank() && !uid.startsWith("FB_") && uid != "Token") {
                "$GRAPH_BASE_URL/$uid/picture?type=large"
            } else ""

            results.add(
                FacebookAccount(
                    uid = uid,
                    name = if (password.isNotBlank()) password else username.ifEmpty { uid },
                    link = twoFactor,
                    note = cookie,
                    phone = proxy,
                    bio = token,
                    isLive = false,
                    avatar = avatarUrl,
                    email = if (username.contains("@")) username else "",
                    password = password
                )
            )
        }
        return results
    }

    /**
     * 2. Xác thực và Lấy thông tin tài khoản Facebook từ Cookie (kết hợp getSessionForApp + SSR HTML)
     */
    @Throws(Exception::class)
    fun verifyCookieAndGetInfo(cookie: String, proxyStr: String? = null): FacebookAccount {
        // Thử lấy token & thông tin trực tiếp bằng getSessionForApp trước
        val directAcc = getTokenFromCookie(cookie, proxyStr)
        if (directAcc != null && directAcc.isLive) {
            return directAcc
        }

        val client = if (!proxyStr.isNullOrEmpty()) buildProxiedClient(proxyStr) else httpClient

        val cUserMatcher = C_USER_REGEX.matcher(cookie)
        if (!cUserMatcher.find()) {
            throw IllegalArgumentException("Không tìm thấy c_user trong cookie")
        }
        val uid = cUserMatcher.group(1) ?: throw IllegalArgumentException("Không tìm thấy c_user trong cookie")

        val request = Request.Builder()
            .url("$BASE_URL/me")
            .header("User-Agent", DEFAULT_DESKTOP_UA)
            .header("Cookie", cookie)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Sec-Fetch-Site", "same-origin")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""

            if (response.code in listOf(401, 403) || body.contains("login_required") || body.contains("checkpoint")) {
                throw IllegalStateException("Cookie die hoặc lỗi do FB Chặn")
            }

            var name = uid
            val nameMatcher = USER_NAME_REGEX.matcher(body)
            if (nameMatcher.find()) {
                val rawName = nameMatcher.group(1) ?: ""
                name = unescapeUnicode(rawName.replace("\\\"", "\"").replace("\\/", "/"))
            } else {
                val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(body)
                if (titleMatcher.find()) {
                    val rawTitle = titleMatcher.group(1) ?: ""
                    val cleanTitle = rawTitle.substringBefore(" | ").substringBefore(" - ").trim()
                    if (cleanTitle.isNotBlank() && !cleanTitle.equals("Facebook", ignoreCase = true) && !cleanTitle.equals("Log in", ignoreCase = true)) {
                        name = cleanTitle
                    }
                }
            }

            val avatarUrl = "$GRAPH_BASE_URL/$uid/picture?type=large"

            return FacebookAccount(
                uid = uid,
                name = name,
                avatar = avatarUrl,
                note = cookie,
                phone = proxyStr.orEmpty(),
                isLive = true
            )
        }
    }

    /**
     * 3. Lấy thông tin đầy đủ & danh sách Pages từ Token (Graph API /me + /me/accounts)
     */
    @Throws(Exception::class)
    fun fetchAccountDetailsWithToken(token: String, proxyStr: String? = null): FacebookAccount {
        val client = if (!proxyStr.isNullOrEmpty()) buildProxiedClient(proxyStr) else httpClient

        // Lấy User Profile
        val url = "$GRAPH_BASE_URL/me?fields=$GRAPH_ME_FIELDS&access_token=$token"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        var id = ""
        var name = ""
        var email = ""
        val pageList = mutableListOf<FacebookPageItem>()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && !body.contains("\"error\"")) {
                    val json = JSONObject(body)
                    id = json.optString("id", "")
                    name = json.optString("name", id)
                    email = json.optString("email", "")

                    if (json.has("facebook_pages")) {
                        val pagesObj = json.getJSONObject("facebook_pages")
                        if (pagesObj.has("data")) {
                            val dataArr = pagesObj.getJSONArray("data")
                            for (i in 0 until dataArr.length()) {
                                val p = dataArr.getJSONObject(i)
                                val pageId = p.optString("id", "")
                                val pageName = p.optString("name", "")
                                val pageToken = p.optString("access_token", "")
                                val additionalProfileId = p.optString("additional_profile_id", "")
                                val pageAvatar = "$GRAPH_BASE_URL/$pageId/picture?type=large"
                                pageList.add(
                                    FacebookPageItem(
                                        pageId = pageId,
                                        pageName = pageName,
                                        pageToken = pageToken,
                                        additionalProfileId = additionalProfileId,
                                        avatar = pageAvatar,
                                        isLive = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Lấy thêm/dự phòng danh sách Pages từ endpoint /me/accounts (như trong Python get_page_list)
        try {
            val accountsUrl = "$GRAPH_BASE_URL/me/accounts?access_token=$token"
            val accountsReq = Request.Builder().url(accountsUrl).get().build()
            client.newCall(accountsReq).execute().use { res ->
                val resBody = res.body?.string() ?: ""
                if (res.isSuccessful && !resBody.contains("\"error\"")) {
                    val accJson = JSONObject(resBody)
                    if (accJson.has("data")) {
                        val arr = accJson.getJSONArray("data")
                        for (i in 0 until arr.length()) {
                            val p = arr.getJSONObject(i)
                            val pageId = p.optString("id", "")
                            if (pageList.none { it.pageId == pageId }) {
                                val pageName = p.optString("name", "")
                                val pageToken = p.optString("access_token", "")
                                val pageAvatar = "$GRAPH_BASE_URL/$pageId/picture?type=large"
                                pageList.add(
                                    FacebookPageItem(
                                        pageId = pageId,
                                        pageName = pageName,
                                        pageToken = pageToken,
                                        avatar = pageAvatar,
                                        isLive = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (id.isBlank()) {
            throw IllegalStateException("Token DIE hoặc không hợp lệ")
        }

        val avatarUrl = "$GRAPH_BASE_URL/$id/picture?type=large&access_token=$token"

        return FacebookAccount(
            uid = id,
            name = name.ifBlank { id },
            email = email,
            avatar = avatarUrl,
            bio = token,
            phone = proxyStr.orEmpty(),
            pages = pageList,
            isLive = true
        )
    }

    private fun unescapeUnicode(str: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\' && i + 1 < str.length && str[i + 1] == 'u' && i + 5 < str.length) {
                try {
                    val unicodeHex = str.substring(i + 2, i + 6)
                    val codePoint = unicodeHex.toInt(16)
                    sb.append(codePoint.toChar())
                    i += 6
                    continue
                } catch (_: Exception) {}
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun buildProxiedClient(proxyStr: String): OkHttpClient {
        val parts = proxyStr.split(":")
        val host = parts[0]
        val port = parts[1].toInt()
        val builder = httpClient.newBuilder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))

        if (parts.size >= 4) {
            val user = parts[2]
            val pass = parts[3]
            builder.proxyAuthenticator { _, response ->
                val credential = Credentials.basic(user, pass)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        }
        return builder.build()
    }
}

