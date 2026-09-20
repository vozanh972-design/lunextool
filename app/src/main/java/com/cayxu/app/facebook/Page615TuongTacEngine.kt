package com.cayxu.app.facebook

import androidx.annotation.Keep

import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Keep class Page615TuongTacEngine(
    private var pageToken: String? = null,
    private var pageId615: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPH_API_URL = "https://graph.facebook.com/v21.0"
        const val GRAPHQL_URL = "https://graph.facebook.com/graphql"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
    }

    @Keep enum class ReactionType(val value: String, val graphqlCode: Int) {
        LIKE("LIKE", 1),
        LOVE("LOVE", 2),
        WOW("WOW", 3),
        HAHA("HAHA", 4),
        SAD("SAD", 7),
        ANGRY("ANGRY", 8),
        CARE("CARE", 16)
    }

    @Keep data class InteractionResult(
        val isSuccess: Boolean,
        val targetId: String,
        val actionType: String,
        val resultId: String? = null,
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

    fun setPageToken(token: String) {
        this.pageToken = token
    }

    fun setPageId615(id: String) {
        this.pageId615 = id
    }

    private fun getCleanToken(overrideToken: String?): String {
        return (overrideToken ?: pageToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
    }

    fun reactPost(
        postId: String,
        reactionType: ReactionType = ReactionType.LIKE,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, postId, "REACT", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("type", reactionType.value)
            .add("access_token", token)
            .build()

        // Facebook v2.4+ yêu cầu scoped {owner_id}_{post_id}.
        // Nếu postId không chứa '_' và có pageId615 thì tự ghép.
        val scopedPostId = if (!postId.contains("_") && !pageId615.isNullOrBlank()) {
            "${pageId615}_$postId"
        } else postId

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$scopedPostId/reactions")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, scopedPostId, "REACT_${reactionType.value}", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, scopedPostId, "REACT", null, e.message, "")
        }
    }


    fun reactGraphQLVoice(
        feedbackId: String,
        userToken: String,
        reactionType: ReactionType = ReactionType.LIKE,
        overridePageId: String? = null
    ): InteractionResult {
        val actor = overridePageId ?: pageId615 ?: ""
        if (actor.isEmpty()) return InteractionResult(false, feedbackId, "REACT_GQL", null, "Page 615 ID required", "")

        val variables = JSONObject().apply {
            put("input", JSONObject().apply {
                put("feedback_id", feedbackId)
                put("feedback_reaction", reactionType.graphqlCode)
                put("actor_id", actor)
                put("client_mutation_id", "1")
            })
        }

        val formBody = FormBody.Builder()
            .add("variables", variables.toString())
            .add("doc_id", "4715426135182900")
            .build()

        val cleanUserToken = userToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $cleanUserToken")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"errors\"")
                InteractionResult(isOk, feedbackId, "REACT_GQL_${reactionType.value}", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, feedbackId, "REACT_GQL", null, e.message, "")
        }
    }

    fun commentPost(
        postId: String,
        message: String,
        attachmentId: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, postId, "COMMENT", null, "Page token required", "")

        // Facebook v2.4+ yêu cầu scoped {owner_id}_{post_id}.
        val scopedPostId = if (!postId.contains("_") && !pageId615.isNullOrBlank()) {
            "${pageId615}_$postId"
        } else postId

        val formBuilder = FormBody.Builder()
            .add("message", message)
            .add("access_token", token)

        if (!attachmentId.isNullOrEmpty()) {
            formBuilder.add("attachment_id", attachmentId)
        }

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$scopedPostId/comments")
            .post(formBuilder.build())
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                InteractionResult(isOk, scopedPostId, "COMMENT", commentId, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, scopedPostId, "COMMENT", null, e.message, "")
        }
    }


    fun replyComment(
        parentCommentId: String,
        message: String,
        attachmentId: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, parentCommentId, "REPLY_COMMENT", null, "Page token required", "")

        val formBuilder = FormBody.Builder()
            .add("message", message)
            .add("access_token", token)

        if (!attachmentId.isNullOrEmpty()) {
            formBuilder.add("attachment_id", attachmentId)
        }

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$parentCommentId/comments")
            .post(formBuilder.build())
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                InteractionResult(isOk, parentCommentId, "REPLY_COMMENT", commentId, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, parentCommentId, "REPLY_COMMENT", null, e.message, "")
        }
    }

    fun followTarget(
        targetId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetId, "FOLLOW", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$targetId/subscribers")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetId, "FOLLOW", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetId, "FOLLOW", null, e.message, "")
        }
    }

    fun unfollowTarget(
        targetId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetId, "UNFOLLOW", null, "Page token required", "")

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$targetId/subscribers?access_token=$token")
            .delete()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetId, "UNFOLLOW", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetId, "UNFOLLOW", null, e.message, "")
        }
    }

    fun likeOtherPage(
        targetPageId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPageId, "LIKE_PAGE", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$targetPageId/likes")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetPageId, "LIKE_PAGE", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetPageId, "LIKE_PAGE", null, e.message, "")
        }
    }

    fun unlikeOtherPage(
        targetPageId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPageId, "UNLIKE_PAGE", null, "Page token required", "")

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$targetPageId/likes?access_token=$token")
            .delete()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetPageId, "UNLIKE_PAGE", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetPageId, "UNLIKE_PAGE", null, e.message, "")
        }
    }

    fun joinGroup(
        groupId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, groupId, "JOIN_GROUP", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$groupId/members")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, groupId, "JOIN_GROUP", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, groupId, "JOIN_GROUP", null, e.message, "")
        }
    }

    fun reviewOtherPage(
        targetPageId: String,
        reviewText: String,
        recommendationType: String = "positive",
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPageId, "REVIEW_PAGE", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("recommendation_type", recommendationType)
            .add("review_text", reviewText)
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$targetPageId/ratings")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetPageId, "REVIEW_PAGE", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetPageId, "REVIEW_PAGE", null, e.message, "")
        }
    }
}
