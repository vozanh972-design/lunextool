package com.cayxu.app.facebook

import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Quản lý đăng nhập, bóc tách dữ liệu tài khoản và đồng bộ danh sách Page từ Facebook Graph API.
 * Trích xuất từ các class `Lz2/m;`, `Li2/B;`, `Li2/f0;` trong APK.
 */
class FacebookAccountManager {

    companion object {
        const val GRAPH_BASE_URL = "https://graph.facebook.com"
        const val GRAPH_API_VERSION = "v19.0"
        const val GRAPH_ME_FIELDS = "id,name,birthday,email,facebook_pages{access_token,additional_profile_id,id,name}"

        val C_USER_REGEX = Pattern.compile("c_user=([0-9]+)")
        val XS_REGEX = Pattern.compile("xs=([^;]+)")
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Parse danh sách dòng tài khoản từ ô nhập TextArea theo định dạng đã chọn (Ảnh 1)
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
                val value = parts[i]
                when (formatFields[i]) {
                    AccountFieldType.USERNAME -> username = value
                    AccountFieldType.PASSWORD -> password = value
                    AccountFieldType.TWO_FACTOR -> twoFactor = value
                    AccountFieldType.COOKIE -> cookie = value
                    AccountFieldType.PROXY -> proxy = value
                    AccountFieldType.TOKEN -> token = value
                }
            }

            // Tìm UID từ username hoặc cookie
            var uid = username
            if (uid.isEmpty() && cookie.isNotEmpty()) {
                val matcher = C_USER_REGEX.matcher(cookie)
                if (matcher.find()) {
                    uid = matcher.group(1)
                }
            }

            results.add(
                FacebookAccount(
                    id = uid,
                    name = if (username.isNotEmpty()) username else uid,
                    email = if (username.contains("@")) username else null,
                    avatarUrl = if (uid.isNotEmpty()) "$GRAPH_BASE_URL/$GRAPH_API_VERSION/$uid/picture?type=large" else null,
                    cookie = cookie.ifEmpty { null },
                    token = token.ifEmpty { null },
                    proxy = proxy.ifEmpty { null },
                    password = password.ifEmpty { null },
                    twoFactorSecret = twoFactor.ifEmpty { null }
                )
            )
        }
        return results
    }

    /**
     * Xác thực Token / Cookie và lấy thông tin Avatar, Tên, Email, Pages (Ảnh 2)
     */
    @Throws(Exception::class)
    fun fetchAccountDetails(token: String, proxyStr: String? = null): FacebookAccount {
        val client = if (!proxyStr.isNullOrEmpty()) {
            buildProxiedClient(proxyStr)
        } else {
            httpClient
        }

        val url = "$GRAPH_BASE_URL/$GRAPH_API_VERSION/me?fields=$GRAPH_ME_FIELDS&access_token=$token"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful || body.contains(""error"")) {
                throw IllegalStateException("Token DIE hoặc không có quyền truy cập: $body")
            }

            val json = JSONObject(body)
            val id = json.getString("id")
            val name = json.optString("name", "Unknown")
            val email = json.optString("email", "")
            val avatarUrl = "$GRAPH_BASE_URL/$GRAPH_API_VERSION/$id/picture?type=large&access_token=$token"

            val pageList = mutableListOf<FacebookPage>()
            if (json.has("facebook_pages")) {
                val pagesObj = json.getJSONObject("facebook_pages")
                if (pagesObj.has("data")) {
                    val dataArr = pagesObj.getJSONArray("data")
                    for (i in 0 until dataArr.length()) {
                        val p = dataArr.getJSONObject(i)
                        pageList.add(
                            FacebookPage(
                                pageId = p.getString("id"),
                                pageName = p.getString("name"),
                                pageToken = p.optString("access_token", null),
                                additionalProfileId = p.optString("additional_profile_id", null),
                                parentUserId = id
                            )
                        )
                    }
                }
            }

            return FacebookAccount(
                id = id,
                name = name,
                email = email.ifEmpty { null },
                avatarUrl = avatarUrl,
                token = token,
                proxy = proxyStr,
                pages = pageList
            )
        }
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
