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
        // Không dùng const string — lấy từ FbVault (native XOR hoặc inline fallback)
        private fun graphApi() = FbVault.graphApiUrl()
        private fun graphql()  = FbVault.graphqlUrl()
        private fun ua()       = FbVault.userAgent()
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

    fun setPageToken(token: String) { this.pageToken = token }
    fun setPageId615(id: String)    { this.pageId615 = id }

    private fun getCleanToken(overrideToken: String?): String =
        (overrideToken ?: pageToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()

    private fun scopePost(postId: String): String =
        if (!postId.contains("_") && !pageId615.isNullOrBlank()) "${pageId615}_$postId" else postId

    fun reactPost(
        postId: String,
        reactionType: ReactionType = ReactionType.LIKE,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, postId, "REACT", null, "Page token required", "")

        val ft = FbVault.fieldType()
        val fat = FbVault.fieldAccessToken()
        val scopedPostId = scopePost(postId)

        val formBody = FormBody.Builder()
            .add(ft, reactionType.value)
            .add(fat, token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$scopedPostId${FbVault.pathReactions()}")
            .post(formBody)
            .header("User-Agent", ua())
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

        val fv  = FbVault.fieldVariables()
        val fdi = FbVault.fieldDocId()
        val formBody = FormBody.Builder()
            .add(fv, variables.toString())
            .add(fdi, FbVault.docIdPageReact())
            .build()

        val cleanUserToken = userToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val request = Request.Builder()
            .url(graphql())
            .post(formBody)
            .header("User-Agent", ua())
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

        val scopedPostId = scopePost(postId)
        val fm  = FbVault.fieldMessage()
        val fat = FbVault.fieldAccessToken()
        val fai = FbVault.fieldAttachmentId()

        val formBuilder = FormBody.Builder()
            .add(fm, message)
            .add(fat, token)

        if (!attachmentId.isNullOrEmpty()) formBuilder.add(fai, attachmentId)

        val request = Request.Builder()
            .url("${graphApi()}/$scopedPostId${FbVault.pathComments()}")
            .post(formBuilder.build())
            .header("User-Agent", ua())
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

        val fm  = FbVault.fieldMessage()
        val fat = FbVault.fieldAccessToken()
        val fai = FbVault.fieldAttachmentId()

        val formBuilder = FormBody.Builder()
            .add(fm, message)
            .add(fat, token)

        if (!attachmentId.isNullOrEmpty()) formBuilder.add(fai, attachmentId)

        val request = Request.Builder()
            .url("${graphApi()}/$parentCommentId${FbVault.pathComments()}")
            .post(formBuilder.build())
            .header("User-Agent", ua())
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
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$targetId${FbVault.pathSubscribers()}")
            .post(formBody)
            .header("User-Agent", ua())
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
            .url("${graphApi()}/$targetId${FbVault.pathSubscribers()}?${FbVault.fieldAccessToken()}=$token")
            .delete()
            .header("User-Agent", ua())
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
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$targetPageId${FbVault.pathLikes()}")
            .post(formBody)
            .header("User-Agent", ua())
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
            .url("${graphApi()}/$targetPageId${FbVault.pathLikes()}?${FbVault.fieldAccessToken()}=$token")
            .delete()
            .header("User-Agent", ua())
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
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$groupId/members")
            .post(formBody)
            .header("User-Agent", ua())
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
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$targetPageId/ratings")
            .post(formBody)
            .header("User-Agent", ua())
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
