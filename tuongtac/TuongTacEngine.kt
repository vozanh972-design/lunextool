package com.facebook.engine.tuongtac

import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit

class TuongTacEngine(
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

    enum class ReactionType(val code: Int) {
        LIKE(1),
        LOVE(2),
        CARE(16),
        HAHA(4),
        WOW(3),
        SAD(7),
        ANGRY(8)
    }

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

    private fun postGraphQL(params: Map<String, String>, friendlyName: String, actionName: String, targetId: String? = null): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val formBuilder = FormBody.Builder()
        params.forEach { (k, v) -> formBuilder.add(k, v) }

        val reqBuilder = Request.Builder()
            .url(GRAPHQL_URL)
            .post(formBuilder.build())
            .header("User-Agent", KATANA_USER_AGENT)
            .header("X-FB-Friendly-Name", friendlyName)
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")

        if (token.isNotEmpty()) {
            reqBuilder.header("Authorization", "OAuth $token")
        }

        return try {
            httpClient.newCall(reqBuilder.build()).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"errors\":")
                EngineResult(isOk, actionName, targetId, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            EngineResult(false, actionName, targetId, e.message, "")
        }
    }

    fun comment(feedbackId: String, text: String, replyToCommentId: String? = null): EngineResult {
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("feedback_id", feedbackId)
            put("message", JSONObject().put("text", text))
            if (!replyToCommentId.isNullOrBlank()) {
                put("parent_comment_id", replyToCommentId)
            }
        }
        val params = mapOf(
            "doc_id" to "6739921102758190",
            "variables" to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "CommentCreateMutation", "COMMENT", feedbackId)
    }

    fun react(feedbackId: String, reaction: ReactionType = ReactionType.LIKE): EngineResult {
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("feedback_id", feedbackId)
            put("feedback_reaction", reaction.code)
        }
        val params = mapOf(
            "doc_id" to "5411782298894101",
            "variables" to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "UFIFeedbackReactMutation", "REACT_${reaction.name}", feedbackId)
    }

    fun follow(targetUid: String): EngineResult {
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("subscribee_id", targetUid)
            put("subscribe_location", "PROFILE")
        }
        val params = mapOf(
            "doc_id" to "4268153066598920",
            "variables" to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "ActorSubscribeCoreMutation", "FOLLOW", targetUid)
    }

    fun likePage(pageId: String): EngineResult {
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("page_id", pageId)
        }
        val params = mapOf(
            "doc_id" to "3628174981029411",
            "variables" to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "PageLikeMutation", "LIKE_PAGE", pageId)
    }

    fun joinGroup(groupId: String): EngineResult {
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("group_id", groupId)
            put("source", "group_mall")
        }
        val params = mapOf(
            "doc_id" to "4981273901928471",
            "variables" to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "GroupJoinMutation", "JOIN_GROUP", groupId)
    }

    fun reviewPage(pageId: String, isPositive: Boolean = true, reviewText: String): EngineResult {
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("page_id", pageId)
            put("recommendation_type", if (isPositive) "POSITIVE" else "NEGATIVE")
            put("review_text", JSONObject().put("text", reviewText))
        }
        val params = mapOf(
            "doc_id" to "5892019284719201",
            "variables" to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "PageRecommendationCreateMutation", "REVIEW_PAGE", pageId)
    }

    fun editComment(commentId: String, newText: String): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val formBody = FormBody.Builder().add("message", newText).build()
        val request = Request.Builder()
            .url("$GRAPH_API_URL/$commentId")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                EngineResult(res.isSuccessful && !body.contains("error"), "EDIT_COMMENT", commentId, body, body)
            }
        } catch (e: Exception) {
            EngineResult(false, "EDIT_COMMENT", commentId, e.message, "")
        }
    }

    fun deleteComment(commentId: String): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val request = Request.Builder()
            .url("$GRAPH_API_URL/$commentId")
            .delete()
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                EngineResult(res.isSuccessful && !body.contains("error"), "DELETE_COMMENT", commentId, body, body)
            }
        } catch (e: Exception) {
            EngineResult(false, "DELETE_COMMENT", commentId, e.message, "")
        }
    }
}
