package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookPageItem
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
class FacebookAccountManager {

    companion object {
        private val ENC_BASE_URL = byteArrayOf(0x32.toByte(), 0x13.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xFD.toByte(), 0xA1.toByte(), 0x87.toByte(), 0x9A.toByte(), 0xB5.toByte(), 0xB8.toByte(), 0xAB.toByte(), 0xC7.toByte(), 0x90.toByte(), 0x62.toByte(), 0x73.toByte(), 0x78.toByte(), 0x48.toByte(), 0x58.toByte(), 0x2B.toByte(), 0x3A.toByte(), 0x70.toByte(), 0x08.toByte(), 0x17.toByte(), 0xE8.toByte())
        private val ENC_GRAPH_BASE_URL = byteArrayOf(0x32.toByte(), 0x13.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xFD.toByte(), 0xA1.toByte(), 0x87.toByte(), 0x9A.toByte(), 0xA5.toByte(), 0xBD.toByte(), 0xBD.toByte(), 0x99.toByte(), 0x9E.toByte(), 0x2D.toByte(), 0x76.toByte(), 0x7C.toByte(), 0x49.toByte(), 0x52.toByte(), 0x26.toByte(), 0x3E.toByte(), 0x31.toByte(), 0x00.toByte(), 0x56.toByte(), 0xE6.toByte(), 0xFD.toByte(), 0xF2.toByte())
        private val ENC_GRAPH_API_VERSION = byteArrayOf(0x2C.toByte(), 0x56.toByte(), 0x4D.toByte(), 0xAF.toByte(), 0xBE.toByte())
        private val ENC_GRAPH_ME_FIELDS = byteArrayOf(0x33.toByte(), 0x03.toByte(), 0x58.toByte(), 0xEF.toByte(), 0xEF.toByte(), 0xF6.toByte(), 0xCD.toByte(), 0x99.toByte(), 0xA7.toByte(), 0xA2.toByte(), 0xBD.toByte(), 0x80.toByte(), 0x9A.toByte())

        private val ENC_C_USER_REGEX = byteArrayOf(0x39.toByte(), 0x38.toByte(), 0x01.toByte(), 0xF2.toByte(), 0xEB.toByte(), 0xE9.toByte(), 0x95.toByte(), 0x9D.toByte(), 0x99.toByte(), 0xFF.toByte(), 0xF1.toByte(), 0xD0.toByte(), 0xAB.toByte(), 0x28.toByte(), 0x39.toByte())
        private val ENC_XS_REGEX = byteArrayOf(0x22.toByte(), 0x14.toByte(), 0x49.toByte(), 0xA9.toByte(), 0xD5.toByte(), 0xC5.toByte(), 0x93.toByte(), 0xE8.toByte(), 0xE9.toByte(), 0xE6.toByte())
        private val ENC_DTSG_REGEX = byteArrayOf(0x06.toByte(), 0x3C.toByte(), 0x56.toByte(), 0xC5.toByte(), 0xDA.toByte(), 0xC8.toByte(), 0xEF.toByte(), 0xFC.toByte(), 0xAC.toByte(), 0xA6.toByte(), 0xA8.toByte(), 0x80.toByte(), 0x97.toByte(), 0x6F.toByte(), 0x54.toByte(), 0x7C.toByte(), 0x5E.toByte(), 0x56.toByte(), 0x66.toByte(), 0x7D.toByte(), 0x02.toByte(), 0x30.toByte(), 0x24.toByte(), 0xD8.toByte(), 0xBE.toByte(), 0xC3.toByte(), 0xD7.toByte(), 0x9B.toByte(), 0xB2.toByte(), 0xBC.toByte(), 0x8B.toByte(), 0x88.toByte(), 0x94.toByte(), 0x25.toByte(), 0x2E.toByte(), 0x03.toByte(), 0x06.toByte(), 0x15.toByte(), 0x62.toByte(), 0x6A.toByte(), 0x4B.toByte(), 0x4D.toByte(), 0x20.toByte(), 0xF4.toByte())
        private val ENC_LSD_REGEX = byteArrayOf(0x06.toByte(), 0x3C.toByte(), 0x56.toByte(), 0xCD.toByte(), 0xDD.toByte(), 0xDF.toByte(), 0x8A.toByte(), 0x99.toByte(), 0x9E.toByte(), 0x94.toByte(), 0x80.toByte(), 0xB4.toByte(), 0xDA.toByte(), 0x5F.toByte(), 0x6B.toByte(), 0x3F.toByte(), 0x5E.toByte(), 0x58.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x30.toByte(), 0x49.toByte(), 0x42.toByte(), 0xA7.toByte(), 0xBA.toByte(), 0xB1.toByte(), 0x86.toByte(), 0x86.toByte(), 0xEF.toByte(), 0xF1.toByte(), 0xBC.toByte(), 0x90.toByte())
        private val ENC_JAZOEST_REGEX = byteArrayOf(0x30.toByte(), 0x06.toByte(), 0x0E.toByte(), 0xEE.toByte(), 0xEB.toByte(), 0xE8.toByte(), 0xDC.toByte(), 0x88.toByte(), 0xEA.toByte(), 0xE1.toByte(), 0xF6.toByte(), 0xD6.toByte(), 0xDF.toByte(), 0x21.toByte())

        private val ENC_USER_ID_REGEX = byteArrayOf(0x78.toByte(), 0x38.toByte(), 0x2B.toByte(), 0xF5.toByte(), 0xF7.toByte(), 0xEB.toByte(), 0xCD.toByte(), 0xDB.toByte(), 0xA3.toByte(), 0xA2.toByte(), 0xB9.toByte(), 0xCB.toByte(), 0xAA.toByte(), 0x70.toByte(), 0x3A.toByte(), 0x27.toByte(), 0x76.toByte(), 0x44.toByte(), 0x6E.toByte(), 0x73.toByte(), 0x0B.toByte(), 0x18.toByte(), 0x1D.toByte(), 0xF7.toByte(), 0xB0.toByte(), 0xC4.toByte(), 0xF2.toByte(), 0xC4.toByte(), 0x9B.toByte(), 0xF9.toByte(), 0xDF.toByte(), 0xCF.toByte(), 0x93.toByte(), 0x63.toByte(), 0x36.toByte(), 0x7D.toByte(), 0x5D.toByte(), 0x11.toByte(), 0x72.toByte(), 0x09.toByte(), 0x11.toByte(), 0x45.toByte(), 0x5E.toByte(), 0xB6.toByte(), 0xBE.toByte(), 0xF8.toByte(), 0x80.toByte(), 0x90.toByte(), 0xF3.toByte(), 0x8A.toByte(), 0xCF.toByte(), 0xD8.toByte())
        private val ENC_USER_NAME_REGEX = byteArrayOf(0x78.toByte(), 0x38.toByte(), 0x2B.toByte(), 0xF5.toByte(), 0xF7.toByte(), 0xEB.toByte(), 0xCD.toByte(), 0xDB.toByte(), 0xA3.toByte(), 0xA2.toByte(), 0xB9.toByte(), 0xCB.toByte(), 0xAA.toByte(), 0x70.toByte(), 0x3A.toByte(), 0x27.toByte(), 0x76.toByte(), 0x44.toByte(), 0x6E.toByte(), 0x73.toByte(), 0x0B.toByte(), 0x18.toByte(), 0x1D.toByte(), 0xF7.toByte(), 0xB0.toByte(), 0xC4.toByte(), 0xF2.toByte(), 0xC4.toByte(), 0x9B.toByte(), 0xF9.toByte(), 0xDF.toByte(), 0xCF.toByte(), 0x94.toByte(), 0x66.toByte(), 0x79.toByte(), 0x44.toByte(), 0x0C.toByte(), 0x67.toByte(), 0x3B.toByte(), 0x7F.toByte(), 0x58.toByte(), 0x33.toByte(), 0x0F.toByte(), 0xA3.toByte(), 0xB4.toByte(), 0x8B.toByte(), 0x98.toByte(), 0x82.toByte(), 0xF0.toByte(), 0x8B.toByte(), 0xB8.toByte(), 0xDF.toByte(), 0x82.toByte(), 0x50.toByte(), 0x46.toByte(), 0x07.toByte(), 0x6F.toByte(), 0x16.toByte(), 0x66.toByte(), 0x70.toByte(), 0x44.toByte())

        val BASE_URL: String get() = com.cayxu.app.util.StringFog.decrypt(ENC_BASE_URL)
        val GRAPH_BASE_URL: String get() = com.cayxu.app.util.StringFog.decrypt(ENC_GRAPH_BASE_URL)
        val GRAPH_API_VERSION: String get() = com.cayxu.app.util.StringFog.decrypt(ENC_GRAPH_API_VERSION)
        val GRAPH_ME_FIELDS: String get() = com.cayxu.app.util.StringFog.decrypt(ENC_GRAPH_ME_FIELDS)

        val C_USER_REGEX: Pattern by lazy { Pattern.compile(com.cayxu.app.util.StringFog.decrypt(ENC_C_USER_REGEX)) }
        val XS_REGEX: Pattern by lazy { Pattern.compile(com.cayxu.app.util.StringFog.decrypt(ENC_XS_REGEX)) }
        val DTSG_REGEX: Pattern by lazy { Pattern.compile(com.cayxu.app.util.StringFog.decrypt(ENC_DTSG_REGEX)) }
        val LSD_REGEX: Pattern by lazy { Pattern.compile(com.cayxu.app.util.StringFog.decrypt(ENC_LSD_REGEX)) }
        val JAZOEST_REGEX: Pattern by lazy { Pattern.compile(com.cayxu.app.util.StringFog.decrypt(ENC_JAZOEST_REGEX)) }

        val USER_ID_REGEX: Pattern by lazy { Pattern.compile(com.cayxu.app.util.StringFog.decrypt(ENC_USER_ID_REGEX)) }
        val USER_NAME_REGEX: Pattern by lazy { Pattern.compile(com.cayxu.app.util.StringFog.decrypt(ENC_USER_NAME_REGEX)) }

        val DEFAULT_DESKTOP_UA: String get() = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val DEFAULT_DALVIK_UA: String get() = com.cayxu.app.util.NativeSecurity.getFbDalvikUA()
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
                if (json.has("access_token")) json.getString("access_token") else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lấy token và info trực tiếp từ Cookie qua getSessionForApp (chuẩn PHP)
     */
    fun getTokenFromCookie(cookieStr: String, proxyStr: String? = null): FacebookAccount? {
        val client = if (!proxyStr.isNullOrEmpty()) buildProxiedClient(proxyStr) else httpClient
        val cleanCookie = cookieStr.replace("\r", "").replace("\n", "").filter { it.code in 32..126 }.trim()

        val cUserMatcher = C_USER_REGEX.matcher(cleanCookie)
        var uid = if (cUserMatcher.find()) cUserMatcher.group(1) ?: "" else ""

        val form = FormBody.Builder()
            .add("format", "json")
            .add("generate_session_cookies", "1")
            .build()

        val reqBuilder = Request.Builder()
            .url("https://api.facebook.com/method/auth.getSessionForApp")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "*/*")
        if (cleanCookie.isNotBlank()) {
            reqBuilder.header("Cookie", cleanCookie)
        }
        val request = reqBuilder.post(form).build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                if (json.has("access_token")) {
                    val rawToken = json.getString("access_token")
                    val eaaaa = convertToken(rawToken, "350685531728", proxyStr) ?: rawToken
                    val realUid = if (uid.isNotBlank()) uid else json.optString("uid", "")
                    
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

                    var name = realUid
                    var avatarUrl = if (realUid.isNotBlank()) "$GRAPH_BASE_URL/$realUid/picture?type=large" else ""
                    var coverUrl = ""
                    var email = ""
                    var pages = emptyList<FacebookPageItem>()

                    try {
                        val details = fetchAccountDetailsWithToken(eaaaa, proxyStr)
                        if (details.name.isNotBlank()) name = details.name
                        if (details.avatar.isNotBlank()) avatarUrl = details.avatar
                        if (details.cover.isNotBlank()) coverUrl = details.cover
                        if (details.email.isNotBlank()) email = details.email
                        if (details.pages.isNotEmpty()) pages = details.pages
                    } catch (_: Exception) {}

                    return FacebookAccount(
                        uid = realUid.ifBlank { "FB_${System.currentTimeMillis() % 1000000}" },
                        name = name.ifBlank { realUid },
                        avatar = avatarUrl,
                        cover = coverUrl,
                        note = finalCookie,
                        bio = eaaaa,
                        email = email,
                        pages = pages,
                        phone = proxyStr.orEmpty(),
                        isLive = true
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback chuẩn GoMax: Xác thực trực tiếp qua FacebookAuthEngine.loginWithCookie
        val proxyParts = proxyStr?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
        val authEngine = FacebookAuthEngine(proxyHost = proxyHost, proxyPort = proxyPort)
        val cookieRes = authEngine.loginWithCookie(cleanCookie)
        if (cookieRes.isSuccess) {
            val realUid = cookieRes.userId?.ifBlank { uid } ?: uid.ifBlank { "FB_${System.currentTimeMillis() % 1000000}" }
            val realName = cookieRes.userName?.ifBlank { realUid } ?: realUid
            val avatar = cookieRes.avatarUrl ?: if (realUid.startsWith("615") || realUid.matches(Regex("\\d+"))) "$GRAPH_BASE_URL/$realUid/picture?type=large" else ""

            val pageEngine = FacebookPageEngine(proxyHost = proxyHost, proxyPort = proxyPort)
            val pages = pageEngine.getAdminedPages(cookieRes.accessToken)

            return FacebookAccount(
                uid = realUid,
                name = realName,
                avatar = avatar,
                note = cleanCookie,
                bio = cookieRes.accessToken.orEmpty(),
                pages = pages,
                phone = proxyStr.orEmpty(),
                isLive = true
            )
        }

        return null
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
                if (!trimmed.contains("|")) {
                    // Không có dấu | -> coi là cookie hoặc token
                    if (trimmed.startsWith("EAA")) {
                        token = trimmed
                    } else {
                        cookie = trimmed
                    }
                } else {
                    // Có dấu | -> tách theo chuẩn logic Python
                    val parts = trimmed.split("|").map { it.trim() }
                    username = parts.getOrNull(0) ?: ""
                    password = parts.getOrNull(1) ?: ""

                    // 1. Tìm datr trong tất cả các phần
                    var foundDatr = ""
                    for (p in parts) {
                        if (p.contains("datr=")) {
                            foundDatr = p
                            break
                        }
                    }

                    // 2. Tìm full cookie có c_user hoặc xs trong tất cả các phần
                    var foundCookie = ""
                    for (p in parts) {
                        if (p.contains("c_user=") || p.contains("xs=")) {
                            foundCookie = p
                            break
                        }
                    }

                    // 3. Xử lý 2FA (cột 3 nếu không phải cookie/datr)
                    if (parts.size >= 3) {
                        val p2 = parts[2]
                        if (!p2.contains("datr=") && !p2.contains("c_user=") && !p2.contains("xs=") && !p2.startsWith("EAA") && p2.length <= 40) {
                            twoFactor = p2
                        }
                    }

                    // 4. Xử lý Cookie
                    if (foundCookie.isNotBlank()) {
                        cookie = foundCookie
                    } else if (foundDatr.isNotBlank()) {
                        cookie = foundDatr
                    } else if (parts.size >= 4) {
                        cookie = parts.subList(3, parts.size).joinToString("|")
                    } else if (parts.size == 3 && (parts[2].contains("=") || parts[2].length > 40)) {
                        cookie = parts[2]
                    }
                }
            } else {
                val rawParts = trimmed.split(delimiter).map { it.trim() }
                for (i in formatFields.indices) {
                    val fieldType = formatFields[i]
                    val value = if (i == formatFields.lastIndex && i < rawParts.size) {
                        rawParts.subList(i, rawParts.size).joinToString(delimiter)
                    } else {
                        rawParts.getOrElse(i) { "" }
                    }

                    when (fieldType) {
                        AccountFieldType.USERNAME -> username = value
                        AccountFieldType.PASSWORD -> password = value
                        AccountFieldType.TWO_FACTOR -> twoFactor = value
                        AccountFieldType.COOKIE -> cookie = value
                        AccountFieldType.PROXY -> proxy = value
                        AccountFieldType.TOKEN -> token = value
                    }
                }
            }

            // Tinh chỉnh: Nếu trường 2FA vô tình chứa Cookie
            if (twoFactor.contains("c_user=") || twoFactor.contains("xs=") || twoFactor.startsWith("datr=")) {
                cookie = if (cookie.isBlank()) twoFactor else "$twoFactor; $cookie"
                twoFactor = ""
            } else if (twoFactor.startsWith("EAA")) {
                if (token.isBlank()) token = twoFactor
                twoFactor = ""
            }

            var uid = username
            if (cookie.contains("c_user=")) {
                val matcher = C_USER_REGEX.matcher(cookie)
                if (matcher.find()) {
                    val cUser = matcher.group(1) ?: ""
                    if (uid.isEmpty() || uid.contains("c_user=") || uid.startsWith("FB_") || uid == "Cookie") {
                        uid = cUser
                    }
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

            val displayName = if (username.isNotBlank() && !username.contains("@") && !username.contains("c_user=")) username else uid

            results.add(
                FacebookAccount(
                    uid = uid,
                    name = displayName,
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

        // Lấy User Profile (theo Python me = requests.get('https://graph.facebook.com/me?access_token=' + eaaaa))
        val url = "$GRAPH_BASE_URL/me?access_token=$token"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        var id = ""
        var name = ""
        var email = ""
        var avatarUrl = ""
        var coverUrl = ""
        var pageList = emptyList<FacebookPageItem>()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && !body.contains("\"error\"")) {
                    val json = JSONObject(body)
                    id = json.optString("id", "")
                    name = json.optString("name", id)
                    email = json.optString("email", "")
                }
            }
        } catch (_: Exception) {}

        // Lấy Avatar HD & Bìa từ FacebookMediaEngine
        try {
            val mediaEngine = FacebookMediaEngine(accessToken = token)
            val media = mediaEngine.getProfileMedia(if (id.isNotBlank()) id else "me", token)
            if (media != null) {
                if (!media.avatarUrl.isNullOrBlank()) avatarUrl = media.avatarUrl
                if (!media.coverUrl.isNullOrBlank()) coverUrl = media.coverUrl
                if (!media.name.isNullOrBlank() && name.isBlank()) name = media.name
            }
        } catch (_: Exception) {}

        if (avatarUrl.isBlank() && id.isNotBlank()) {
            avatarUrl = "$GRAPH_BASE_URL/$id/picture?type=large"
        }

        // Lấy danh sách Pages với UID 615 chuẩn từ FacebookPageEngine
        try {
            val pageEngine = FacebookPageEngine(accessToken = token)
            pageList = pageEngine.getAdminedPages(token)
        } catch (_: Exception) {}

        return FacebookAccount(
            uid = id.ifBlank { "Token" },
            name = name.ifBlank { id },
            avatar = avatarUrl,
            cover = coverUrl,
            note = "",
            bio = token,
            email = email,
            pages = pageList,
            phone = proxyStr.orEmpty(),
            isLive = true
        )
    }

    /**
     * Đổi avatar Facebook qua FacebookMediaEngine 100% Graph API (Token)
     */
    @Throws(Exception::class)
    fun changeProfilePicture(
        token: String?,
        imageBytes: ByteArray,
        proxyStr: String? = null,
        uid: String? = null
    ): String? {
        if (token.isNullOrBlank()) return null
        val proxyParts = proxyStr?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        val mediaEngine = FacebookMediaEngine(
            accessToken = token,
            proxyHost = proxyHost,
            proxyPort = proxyPort
        )
        val result = mediaEngine.updateAvatar(
            imageBytes = imageBytes,
            targetId = uid,
            tokenParam = token
        )
        if (result.isSuccess) {
            val media = mediaEngine.getProfileMedia(uid, tokenParam = token)
            return media?.avatarUrl ?: "$GRAPH_BASE_URL/${uid ?: "me"}/picture?type=large&access_token=$token"
        }
        return null
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

