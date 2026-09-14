package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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
        const val GRAPH_BASE_URL = "https://graph.facebook.com"
        const val GRAPH_API_VERSION = "v19.0"
        const val GRAPH_ME_FIELDS = "id,name,birthday,email,facebook_pages{access_token,additional_profile_id,id,name}"

        val C_USER_REGEX = Pattern.compile("c_user=([0-9]+)")
        val XS_REGEX = Pattern.compile("xs=([^;]+)")
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Throws(Exception::class)
    fun fetchAccountDetails(token: String, proxyStr: String? = null): FacebookAccountDetails {
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
            if (!response.isSuccessful || body.contains("\"error\"")) {
                throw IllegalStateException("Token DIE hoặc không có quyền truy cập: $body")
            }

            val json = JSONObject(body)
            val id = json.optString("id", "")
            val name = json.optString("name", "Facebook User")
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
                                pageId = p.optString("id", ""),
                                pageName = p.optString("name", ""),
                                pageToken = p.optString("access_token", null),
                                additionalProfileId = p.optString("additional_profile_id", null),
                                parentUserId = id
                            )
                        )
                    }
                }
            }

            return FacebookAccountDetails(
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
