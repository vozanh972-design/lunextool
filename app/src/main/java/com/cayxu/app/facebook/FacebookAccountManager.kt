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

@Keep
data class FacebookAccountDetails(
    val id: String,
    val name: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val token: String? = null,
    val proxy: String? = null,
    val pages: List<FacebookPage> = emptyList()
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
        const val DEFAULT_MOBILE_UA = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

            val parts = trimmed.split(delimiter).map { it.trim() }
            if (parts.size < formatFields.size) continue

            var username = ""
            var password = ""
            var twoFactor = ""
            var cookie = ""
            var proxy = ""
            var token = ""

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

            var uid = username
            if (uid.isEmpty() && cookie.isNotEmpty()) {
                val matcher = C_USER_REGEX.matcher(cookie)
                if (matcher.find()) {
                    uid = matcher.group(1) ?: ""
                }
            }
            if (uid.isEmpty()) {
                uid = "FB_${System.currentTimeMillis() % 1000000}"
            }

            val avatarUrl = if (uid.isNotBlank() && !uid.startsWith("FB_")) {
                "$GRAPH_BASE_URL/$GRAPH_API_VERSION/$uid/picture?type=large"
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
     * 2. Xác thực và Lấy thông tin tài khoản Facebook từ Cookie (Li2/f0; + Li2/X;)
     */
    @Throws(Exception::class)
    fun verifyCookieAndGetInfo(cookie: String, proxyStr: String? = null): FacebookAccount {
        val client = if (!proxyStr.isNullOrEmpty()) buildProxiedClient(proxyStr) else httpClient

        // Bước 1: Kiểm tra c_user trong cookie
        val cUserMatcher = C_USER_REGEX.matcher(cookie)
        if (!cUserMatcher.find()) {
            throw IllegalArgumentException("Không tìm thấy c_user trong cookie")
        }
        val uid = cUserMatcher.group(1) ?: throw IllegalArgumentException("Không tìm thấy c_user trong cookie")

        // Bước 2: Request trang chủ facebook.com/me để bóc tách Name, DTSG, LSD
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

            // Bóc tách Name
            var name = uid
            val nameMatcher = USER_NAME_REGEX.matcher(body)
            if (nameMatcher.find()) {
                val rawName = nameMatcher.group(1) ?: ""
                name = unescapeUnicode(rawName.replace("\\\"", "\"").replace("\\/", "/"))
            } else {
                // Fallback bóc tách từ Title nếu có
                val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(body)
                if (titleMatcher.find()) {
                    val rawTitle = titleMatcher.group(1) ?: ""
                    val cleanTitle = rawTitle.substringBefore(" | ").substringBefore(" - ").trim()
                    if (cleanTitle.isNotBlank() && !cleanTitle.equals("Facebook", ignoreCase = true) && !cleanTitle.equals("Log in", ignoreCase = true)) {
                        name = cleanTitle
                    }
                }
            }

            val avatarUrl = "$GRAPH_BASE_URL/$GRAPH_API_VERSION/$uid/picture?type=large"

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
     * 3. Lấy thông tin đầy đủ & danh sách Pages từ Token (Lz2/m; + Li2/B;)
     */
    @Throws(Exception::class)
    fun fetchAccountDetailsWithToken(token: String, proxyStr: String? = null): FacebookAccount {
        val client = if (!proxyStr.isNullOrEmpty()) buildProxiedClient(proxyStr) else httpClient

        val url = "$GRAPH_BASE_URL/$GRAPH_API_VERSION/me?fields=$GRAPH_ME_FIELDS&access_token=$token"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful || body.contains("\"error\"")) {
                throw IllegalStateException("Token DIE hoặc không hợp lệ: $body")
            }

            val json = JSONObject(body)
            val id = json.optString("id", "")
            val name = json.optString("name", id)
            val email = json.optString("email", "")
            val avatarUrl = "$GRAPH_BASE_URL/$GRAPH_API_VERSION/$id/picture?type=large&access_token=$token"

            val pageList = mutableListOf<FacebookPageItem>()
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
                        val pageAvatar = "$GRAPH_BASE_URL/$GRAPH_API_VERSION/$pageId/picture?type=large"
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

            return FacebookAccount(
                uid = id,
                name = name,
                email = email,
                avatar = avatarUrl,
                bio = token,
                phone = proxyStr.orEmpty(),
                pages = pageList,
                isLive = true
            )
        }
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
