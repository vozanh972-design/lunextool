package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit

@Keep
class FacebookTuongTacEngine(
    private var accessToken: String? = null,
    private var userId: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPHQL_URL = "https://graph.facebook.com/graphql"
        const val GRAPH_API_URL = "https://graph.facebook.com/v21.0"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
    }

    @Keep
    enum class ReactionType(val code: Int) {
        LIKE(1),
        LOVE(2),
        CARE(16),
        HAHA(4),
        WOW(3),
        SAD(7),
        ANGRY(8)
    }

    @Keep
    data class EngineResult(
        val isSuccess: Boolean,
        val action: String,
        val targetId: String? = null,
        val message: String? = null,
        val rawResponse: String = ""
    )

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            builder.proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
        }
        builder.build()
    }

    fun setAccessToken(token: String) {
        this.accessToken = token
    }

    fun setUserId(uid: String) {
        this.userId = uid
    }

    fun reactPost(postId: String, reaction: ReactionType = ReactionType.LIKE): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return EngineResult(false, "react", postId, "Token required", "")

        val url = "$GRAPH_API_URL/$postId/reactions"
        val formBody = FormBody.Builder()
            .add("type", reaction.name)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("error")
                EngineResult(isOk, "react", postId, if (isOk) "Reaction ${reaction.name} success" else body, body)
            }
        } catch (e: Exception) {
            EngineResult(false, "react", postId, e.message, "")
        }
    }

    fun commentPost(postId: String, messageText: String): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return EngineResult(false, "comment", postId, "Token required", "")

        val url = "$GRAPH_API_URL/$postId/comments"
        val formBody = FormBody.Builder()
            .add("message", messageText)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("error")
                EngineResult(isOk, "comment", postId, if (isOk) "Comment success" else body, body)
            }
        } catch (e: Exception) {
            EngineResult(false, "comment", postId, e.message, "")
        }
    }

    fun followUser(targetUserId: String): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return EngineResult(false, "follow", targetUserId, "Token required", "")

        val url = "$GRAPH_API_URL/$targetUserId/subscribers"
        val formBody = FormBody.Builder().build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("error")
                EngineResult(isOk, "follow", targetUserId, if (isOk) "Follow success" else body, body)
            }
        } catch (e: Exception) {
            EngineResult(false, "follow", targetUserId, e.message, "")
        }
    }
}
