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
        // Lazy cache: gọi FbVault 1 lần, tái sử dụng cho mọi request
        private val GRAPH_API by lazy { FbVault.graphApiUrl() }
        private val GRAPHQL   by lazy { FbVault.graphqlUrl()  }
        private val UA        by lazy { FbVault.userAgent()   }
        private fun graphApi() = GRAPH_API
        private fun graphql()  = GRAPHQL
        private fun ua()       = UA

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

    fun reactPost(
        postId: String,
        reactionType: ReactionType = ReactionType.LIKE,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, postId, "REACT", null, "Page token required", "")

        val cleanTargetId = if (!postId.startsWith("http")) postId.trim() else FacebookTuongTacEngine.extractId(postId)

        val ft = FbVault.fieldType()
        val fat = FbVault.fieldAccessToken()

        val formBody = FormBody.Builder()
            .add(ft, reactionType.value)
            .add(fat, token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$cleanTargetId${FbVault.pathReactions()}")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        var restErrorMsg = ""
        var restBody = ""

        try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                restBody = body
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                if (isOk) {
                    return InteractionResult(true, cleanTargetId, "REACT_${reactionType.value}", null, "Success", body)
                }

                // Fallback nếu gọi /reactions thất bại: thử gọi /likes nếu là LIKE
                if (reactionType == ReactionType.LIKE) {
                    val likeBody = FormBody.Builder()
                        .add(fat, token)
                        .build()
                    val likeReq = Request.Builder()
                        .url("${graphApi()}/$cleanTargetId${FbVault.pathLikes()}")
                        .post(likeBody)
                        .header("User-Agent", ua())
                        .build()
                    try {
                        httpClient.newCall(likeReq).execute().use { res2 ->
                            val body2 = res2.body?.string() ?: ""
                            val isOk2 = res2.isSuccessful && (body2.contains("\"success\":true") || !body2.contains("\"error\""))
                            if (isOk2) {
                                return InteractionResult(true, cleanTargetId, "REACT_LIKE_FALLBACK", null, "Success", body2)
                            }
                        }
                    } catch (_: Exception) {}
                }

                restErrorMsg = parseErrorMessage(body)
            }
        } catch (e: Exception) {
            restErrorMsg = e.message ?: "Lỗi kết nối mạng"
        }

        // Fallback sang Katana GraphQL độc lập của chính Page615 bằng pageToken
        // Xử lý triệt để lỗi "Object with ID does not exist, cannot be loaded due to missing permissions" cho bài viết cá nhân/status/photo/reel
        val actor = pageId615?.takeIf { it.isNotBlank() } ?: ""
        val gqlRes = reactGraphQLPage(cleanTargetId, token, reactionType, actor)
        if (gqlRes.isSuccess) {
            return gqlRes
        }

        val finalErrMsg = if (restErrorMsg.contains("does not exist") || restErrorMsg.contains("missing permissions") || restErrorMsg.contains("Unsupported post request")) {
            gqlRes.message ?: restErrorMsg
        } else {
            restErrorMsg.ifBlank { gqlRes.message ?: "Lỗi tương tác cảm xúc" }
        }

        return InteractionResult(false, cleanTargetId, "REACT_${reactionType.value}", null, finalErrMsg, gqlRes.rawResponse.ifBlank { restBody })
    }

    fun reactGraphQLPage(
        feedbackId: String,
        token: String,
        reactionType: ReactionType = ReactionType.LIKE,
        pageActorId: String = pageId615 ?: ""
    ): InteractionResult {
        val cleanFeedbackId = if (!feedbackId.startsWith("http")) feedbackId.trim() else FacebookTuongTacEngine.extractId(feedbackId)
        val actor = pageActorId.ifBlank { pageId615 ?: "" }

        val variables = JSONObject().apply {
            put("input", JSONObject().apply {
                put("feedback_id", cleanFeedbackId)
                put("feedback_reaction", reactionType.graphqlCode)
                if (actor.isNotBlank()) put("actor_id", actor)
                put("client_mutation_id", java.util.UUID.randomUUID().toString())
            })
        }

        val fv  = FbVault.fieldVariables()
        val fdi = FbVault.fieldDocId()
        val fat = FbVault.fieldAccessToken()

        val formBody = FormBody.Builder()
            .add(fdi, "5411782298894101")
            .add(fv, variables.toString())
            .add(fat, token)
            .build()

        val request = Request.Builder()
            .url(graphql())
            .post(formBody)
            .header("User-Agent", ua())
            .header("Authorization", "OAuth $token")
            .header("X-FB-Friendly-Name", "UFIFeedbackReactMutation")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"errors\":")
                val errMsg = if (isOk) "Success" else parseErrorMessage(body)
                InteractionResult(isOk, cleanFeedbackId, "REACT_GQL_${reactionType.value}", null, errMsg, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanFeedbackId, "REACT_GQL", null, e.message ?: "Lỗi mạng", "")
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
            .add(fdi, "4715426135182900")
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
        if (message.isBlank()) return InteractionResult(false, postId, "COMMENT", null, "Nội dung comment trống", "")
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, postId, "COMMENT", null, "Page token required", "")

        val cleanTargetId = if (!postId.startsWith("http")) postId.trim() else FacebookTuongTacEngine.extractId(postId)

        val fm  = FbVault.fieldMessage()
        val fat = FbVault.fieldAccessToken()
        val fai = FbVault.fieldAttachmentId()

        val formBuilder = FormBody.Builder()
            .add(fm, message)
            .add(fat, token)

        if (!attachmentId.isNullOrEmpty()) formBuilder.add(fai, attachmentId)

        val request = Request.Builder()
            .url("${graphApi()}/$cleanTargetId${FbVault.pathComments()}")
            .post(formBuilder.build())
            .header("User-Agent", ua())
            .build()

        var restErrorMsg = ""
        var restBody = ""

        try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                restBody = body
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                if (isOk) {
                    return InteractionResult(true, cleanTargetId, "COMMENT", commentId, "Success", body)
                }

                restErrorMsg = parseErrorMessage(body)
            }
        } catch (e: Exception) {
            restErrorMsg = e.message ?: "Lỗi kết nối mạng"
        }

        // Fallback sang Katana GraphQL độc lập của chính Page615 bằng pageToken
        // Xử lý triệt để lỗi "(#12) singular statuses API is deprecated for versions v2.4 and higher"
        val actor = pageId615?.takeIf { it.isNotBlank() } ?: ""
        val gqlRes = commentGraphQLPage(cleanTargetId, message, token, actor)
        if (gqlRes.isSuccess) {
            return gqlRes
        }

        val finalErrMsg = if (restErrorMsg.contains("deprecated") || restErrorMsg.contains("(#12)")) {
            gqlRes.message ?: restErrorMsg
        } else {
            restErrorMsg.ifBlank { gqlRes.message ?: "Bình luận không thành công" }
        }

        return InteractionResult(false, cleanTargetId, "COMMENT", null, finalErrMsg, gqlRes.rawResponse.ifBlank { restBody })
    }

    fun commentGraphQLPage(
        cleanTargetId: String,
        message: String,
        token: String,
        pageActorId: String = pageId615 ?: ""
    ): InteractionResult {
        val actor = pageActorId.ifBlank { pageId615 ?: "" }
        val input = JSONObject().apply {
            put("client_mutation_id", java.util.UUID.randomUUID().toString())
            if (actor.isNotBlank()) put("actor_id", actor)
            put("feedback_id", cleanTargetId)
            put("message", JSONObject().put("text", message))
        }

        val fv  = FbVault.fieldVariables()
        val fdi = FbVault.fieldDocId()
        val fat = FbVault.fieldAccessToken()

        val formBody = FormBody.Builder()
            .add(fdi, "6739921102758190")
            .add(fv, JSONObject().put("input", input).toString())
            .add(fat, token)
            .build()

        val request = Request.Builder()
            .url(graphql())
            .post(formBody)
            .header("User-Agent", ua())
            .header("Authorization", "OAuth $token")
            .header("X-FB-Friendly-Name", "CommentCreateMutation")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"errors\":")
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optJSONObject("data")
                    ?.optJSONObject("comment_create")
                    ?.optJSONObject("comment")
                    ?.optString("id", null)
                val errMsg = if (isOk) "Success" else parseErrorMessage(body)
                InteractionResult(isOk, cleanTargetId, "COMMENT_GQL", commentId, errMsg, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanTargetId, "COMMENT_GQL", null, e.message ?: "Lỗi kết nối", "")
        }
    }

    private fun parseErrorMessage(body: String): String {
        try {
            val json = JSONObject(body)
            if (json.has("errors")) {
                val errs = json.optJSONArray("errors")
                if (errs != null && errs.length() > 0) {
                    val first = errs.optJSONObject(0)
                    val msg = first?.optString("message")
                    val summary = first?.optString("summary")
                    if (!msg.isNullOrBlank()) return if (!summary.isNullOrBlank()) "$summary: $msg" else msg
                }
            }
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                val code = err?.optInt("code", 0) ?: 0
                val subcode = err?.optInt("error_subcode", 0) ?: 0
                val title = err?.optString("error_user_title")?.takeIf { it.isNotBlank() }
                val userMsg = err?.optString("error_user_msg")?.takeIf { it.isNotBlank() }
                if (!title.isNullOrBlank() || !userMsg.isNullOrBlank()) {
                    return listOfNotNull(title, userMsg).joinToString(": ")
                }
                if (code == 368 || subcode == 1390008) {
                    return "Tài khoản bị Facebook giới hạn tính năng tạm thời (Spam Block - Mã 368)"
                }
                val msg = err?.optString("message")
                if (!msg.isNullOrBlank()) return msg
                val errStr = json.optString("error")
                if (errStr.isNotBlank()) return errStr
            }
        } catch (_: Exception) {}
        return if (body.isNotBlank()) body.take(300) else "Phản hồi lỗi không xác định từ Facebook"
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
