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

        // 1. Phân giải Node ID đầy đủ {author_id}_{post_id} nếu gặp lỗi (#12) singular status hoặc ID not exist
        if (restErrorMsg.contains("deprecated") || restErrorMsg.contains("(#12)") || restErrorMsg.contains("does not exist") || restErrorMsg.contains("missing permissions")) {
            try {
                val resolveReq = Request.Builder()
                    .url("${graphApi()}/$cleanTargetId?fields=id&access_token=$token")
                    .get()
                    .header("User-Agent", ua())
                    .build()
                val scopedId = httpClient.newCall(resolveReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    json?.optString("id", null)
                }
                if (!scopedId.isNullOrBlank() && scopedId != cleanTargetId && scopedId.contains("_")) {
                    val scopedReq = Request.Builder()
                        .url("${graphApi()}/$scopedId${FbVault.pathReactions()}")
                        .post(formBody)
                        .header("User-Agent", ua())
                        .build()
                    val scopedRes = httpClient.newCall(scopedReq).execute().use { res ->
                        val body = res.body?.string() ?: ""
                        val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                        if (isOk) InteractionResult(true, scopedId, "REACT_${reactionType.value}", null, "Success", body) else null
                    }
                    if (scopedRes != null) return scopedRes

                    // Thử /likes với scoped ID nếu là LIKE
                    if (reactionType == ReactionType.LIKE) {
                        val scopedLikeReq = Request.Builder()
                            .url("${graphApi()}/$scopedId${FbVault.pathLikes()}")
                            .post(FormBody.Builder().add(fat, token).build())
                            .header("User-Agent", ua())
                            .build()
                        val scopedLikeRes = httpClient.newCall(scopedLikeReq).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                            if (isOk) InteractionResult(true, scopedId, "REACT_LIKE_FALLBACK", null, "Success", body) else null
                        }
                        if (scopedLikeRes != null) return scopedLikeRes
                    }
                }
            } catch (_: Exception) {}

            // 2. Thử endpoint Graph API không version
            try {
                val unversionedReq = Request.Builder()
                    .url("https://graph.facebook.com/$cleanTargetId${FbVault.pathReactions()}")
                    .post(formBody)
                    .header("User-Agent", ua())
                    .build()
                val unversionedRes = httpClient.newCall(unversionedReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                    if (isOk) InteractionResult(true, cleanTargetId, "REACT_${reactionType.value}", null, "Success", body) else null
                }
                if (unversionedRes != null) return unversionedRes

                if (reactionType == ReactionType.LIKE) {
                    val unversionedLikeReq = Request.Builder()
                        .url("https://graph.facebook.com/$cleanTargetId${FbVault.pathLikes()}")
                        .post(FormBody.Builder().add(fat, token).build())
                        .header("User-Agent", ua())
                        .build()
                    val unversionedLikeRes = httpClient.newCall(unversionedLikeReq).execute().use { res ->
                        val body = res.body?.string() ?: ""
                        val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                        if (isOk) InteractionResult(true, cleanTargetId, "REACT_LIKE_FALLBACK", null, "Success", body) else null
                    }
                    if (unversionedLikeRes != null) return unversionedLikeRes
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback sang GraphQL độc lập của chính Page 615 bằng pageToken và actor_id
        val actor = pageId615?.takeIf { it.isNotBlank() } ?: ""
        val gqlRes = reactGraphQLPage(cleanTargetId, token, reactionType, actor)
        if (gqlRes.isSuccess) {
            return gqlRes
        }

        val finalErrMsg = if (restErrorMsg.contains("deprecated") || restErrorMsg.contains("(#12)") || restErrorMsg.contains("does not exist") || restErrorMsg.contains("missing permissions") || restErrorMsg.contains("Unsupported post request")) {
            gqlRes.message ?: restErrorMsg
        } else {
            restErrorMsg.ifBlank { gqlRes.message ?: "Lỗi tương tác cảm xúc" }
        }

        return InteractionResult(false, cleanTargetId, "REACT_${reactionType.value}", null, finalErrMsg, gqlRes.rawResponse.ifBlank { restBody })
    }

    /**
     * Tương tác cảm xúc chuẩn Page 615 qua GraphQL:
     * - actor_id là UID của Page 615 (Profile+)
     * - Chỉ sử dụng doc_id hợp lệ của Page, KHÔNG sử dụng doc_id Profile (5411782298894101)
     * - Chuẩn hóa feedback_id cho GraphQL nếu là Post ID dạng số
     */
    fun reactGraphQLPage(
        feedbackId: String,
        token: String,
        reactionType: ReactionType = ReactionType.LIKE,
        pageActorId: String = pageId615 ?: ""
    ): InteractionResult {
        val cleanFeedbackId = if (!feedbackId.startsWith("http")) feedbackId.trim() else FacebookTuongTacEngine.extractId(feedbackId)
        val actor = pageActorId.ifBlank { pageId615 ?: "" }

        // Chuẩn hóa feedback_id: Nếu là số numeric, query feedback node ID từ Graph API hoặc Base64
        var targetFeedbackId = cleanFeedbackId
        if (cleanFeedbackId.all { it.isDigit() }) {
            try {
                val fReq = Request.Builder()
                    .url("${graphApi()}/$cleanFeedbackId?fields=feedback{id}&access_token=$token")
                    .get()
                    .header("User-Agent", ua())
                    .build()
                httpClient.newCall(fReq).execute().use { fRes ->
                    val fBody = fRes.body?.string() ?: ""
                    val fJson = try { JSONObject(fBody) } catch (_: Exception) { null }
                    val resolvedId = fJson?.optJSONObject("feedback")?.optString("id")
                    if (!resolvedId.isNullOrBlank()) {
                        targetFeedbackId = resolvedId
                    }
                }
            } catch (_: Exception) {}

            if (targetFeedbackId == cleanFeedbackId) {
                try {
                    val encoded = android.util.Base64.encodeToString(
                        "feedback:$cleanFeedbackId".toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP
                    )
                    if (!encoded.isNullOrBlank()) targetFeedbackId = encoded
                } catch (_: Exception) {}
            }
        }

        val variables = JSONObject().apply {
            put("input", JSONObject().apply {
                put("feedback_id", targetFeedbackId)
                put("feedback_reaction", reactionType.graphqlCode)
                if (actor.isNotBlank()) put("actor_id", actor)
                put("client_mutation_id", java.util.UUID.randomUUID().toString())
            })
            if (actor.isNotBlank()) put("actor_id", actor)
        }

        val fv  = FbVault.fieldVariables()
        val fdi = FbVault.fieldDocId()
        val fat = FbVault.fieldAccessToken()

        // Sửa đúng bug: Dùng doc_id của Page 615 GraphQL, loại bỏ hoàn toàn docIdProfileReact (5411782298894101)
        val pageDocId = FbVault.docIdPageReact()

        val formBody = FormBody.Builder()
            .add(fdi, pageDocId)
            .add(fv, variables.toString())
            .add(fat, token)
            .build()

        val request = Request.Builder()
            .url(graphql())
            .post(formBody)
            .header("User-Agent", ua())
            .header("Authorization", "OAuth $token")
            .header("X-FB-Friendly-Name", "CometUFIFeedbackReactMutation")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"errors\":")
                val errMsg = if (isOk) "Success" else parseErrorMessage(body)
                InteractionResult(isOk, cleanFeedbackId, "REACT_GQL_PAGE_${reactionType.value}", null, errMsg, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanFeedbackId, "REACT_GQL_PAGE", null, e.message ?: "Lỗi mạng", "")
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
                put("client_mutation_id", java.util.UUID.randomUUID().toString())
            })
            put("actor_id", actor)
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
            .header("X-FB-Friendly-Name", "CometUFIFeedbackReactMutation")
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

        var lastErrorMsg = ""
        var lastBody = ""

        try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                lastBody = body
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                if (isOk) {
                    return InteractionResult(true, cleanTargetId, "COMMENT", commentId, "Success", body)
                }

                lastErrorMsg = parseErrorMessage(body)
            }
        } catch (e: Exception) {
            lastErrorMsg = e.message ?: "Lỗi kết nối mạng"
        }

        // Nếu gặp lỗi (#12) singular statuses API is deprecated hoặc không tìm thấy:
        if (lastErrorMsg.contains("deprecated") || lastErrorMsg.contains("(#12)") || lastErrorMsg.contains("does not exist") || lastErrorMsg.contains("missing permissions")) {
            // 2. Phân giải Node ID đầy đủ {author_id}_{post_id} qua Graph API
            try {
                val resolveReq = Request.Builder()
                    .url("${graphApi()}/$cleanTargetId?fields=id&access_token=$token")
                    .get()
                    .header("User-Agent", ua())
                    .build()
                val scopedId = httpClient.newCall(resolveReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    json?.optString("id", null)
                }
                if (!scopedId.isNullOrBlank() && scopedId != cleanTargetId && scopedId.contains("_")) {
                    val scopedReq = Request.Builder()
                        .url("${graphApi()}/$scopedId${FbVault.pathComments()}")
                        .post(formBuilder.build())
                        .header("User-Agent", ua())
                        .build()
                    val scopedRes = httpClient.newCall(scopedReq).execute().use { res ->
                        val body = res.body?.string() ?: ""
                        val json = try { JSONObject(body) } catch (_: Exception) { null }
                        val commentId = json?.optString("id", null)
                        val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                        if (isOk) {
                            InteractionResult(true, scopedId, "COMMENT", commentId, "Success", body)
                        } else null
                    }
                    if (scopedRes != null) return scopedRes
                }
            } catch (_: Exception) {}

            // 3. Thử gọi unversioned Graph API endpoint (https://graph.facebook.com/{id}/comments)
            // Endpoint unversioned không áp dụng hạn chế v2.4+ cho status
            try {
                val unversionedReq = Request.Builder()
                    .url("https://graph.facebook.com/$cleanTargetId${FbVault.pathComments()}")
                    .post(formBuilder.build())
                    .header("User-Agent", ua())
                    .build()
                val unversionedRes = httpClient.newCall(unversionedReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val commentId = json?.optString("id", null)
                    val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                    if (isOk) {
                        InteractionResult(true, cleanTargetId, "COMMENT", commentId, "Success", body)
                    } else null
                }
                if (unversionedRes != null) return unversionedRes
            } catch (_: Exception) {}

            // 4. Thử ghép ID Page nếu là bài viết trên trang
            val pId = pageId615
            if (!pId.isNullOrBlank() && !cleanTargetId.contains("_")) {
                try {
                    val pageScopedReq = Request.Builder()
                        .url("${graphApi()}/${pId}_$cleanTargetId${FbVault.pathComments()}")
                        .post(formBuilder.build())
                        .header("User-Agent", ua())
                        .build()
                    val pageRes = httpClient.newCall(pageScopedReq).execute().use { res ->
                        val body = res.body?.string() ?: ""
                        val json = try { JSONObject(body) } catch (_: Exception) { null }
                        val commentId = json?.optString("id", null)
                        val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                        if (isOk) {
                            InteractionResult(true, "${pId}_$cleanTargetId", "COMMENT", commentId, "Success", body)
                        } else null
                    }
                    if (pageRes != null) return pageRes
                } catch (_: Exception) {}
            }
        }

        return InteractionResult(false, cleanTargetId, "COMMENT", null, lastErrorMsg.ifBlank { "Bình luận không thành công" }, lastBody)
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

    fun sharePost(
        targetPostId: String,
        message: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPostId, "SHARE", null, "Page token required", "")

        val cleanId = if (!targetPostId.startsWith("http")) targetPostId.trim() else FacebookTuongTacEngine.extractId(targetPostId)
        val pId = pageId615?.takeIf { it.isNotBlank() } ?: "me"

        val formBuilder = FormBody.Builder()
            .add("link", "https://www.facebook.com/$cleanId")
            .add(FbVault.fieldAccessToken(), token)
        if (!message.isNullOrBlank()) {
            formBuilder.add(FbVault.fieldMessage(), message)
        }

        val request = Request.Builder()
            .url("${graphApi()}/$pId/feed")
            .post(formBuilder.build())
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"id\":") || body.contains("\"success\":true") || !body.contains("\"error\""))
                if (isOk) {
                    val shareId = try { JSONObject(body).optString("id", null) } catch (_: Exception) { null }
                    InteractionResult(true, cleanId, "SHARE", shareId, "Success", body)
                } else {
                    val fb2 = FormBody.Builder().add(FbVault.fieldAccessToken(), token)
                    if (!message.isNullOrBlank()) fb2.add(FbVault.fieldMessage(), message)
                    val req2 = Request.Builder()
                        .url("${graphApi()}/$cleanId/sharedposts")
                        .post(fb2.build())
                        .header("User-Agent", ua())
                        .build()
                    val res2 = httpClient.newCall(req2).execute().use { r2 ->
                        val b2 = r2.body?.string() ?: ""
                        val ok2 = r2.isSuccessful && (b2.contains("\"id\":") || !b2.contains("\"error\""))
                        InteractionResult(ok2, cleanId, "SHARE", null, if (ok2) "Success" else parseErrorMessage(b2), b2)
                    }
                    if (res2.isSuccess) res2 else InteractionResult(false, cleanId, "SHARE", null, parseErrorMessage(body), body)
                }
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanId, "SHARE", null, e.message ?: "Lỗi mạng", "")
        }
    }
}
