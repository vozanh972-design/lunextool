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
        // Không hardcode URL/UA — lấy từ FbVault (native XOR hoặc inline fallback)
        private fun graphApi() = FbVault.graphApiUrl()
        private fun graphql()  = FbVault.graphqlUrl()
        private fun ua()       = FbVault.userAgent()

        /**
         * Tự động trích xuất ID (Post ID, UID, Page ID, Feedback ID) từ URL link nếu server trả về dạng link
         */
        fun extractId(rawTarget: String): String {
            val trimmed = rawTarget.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                return trimmed
            }
            val patterns = listOf(
                Regex("""(?:\/posts\/|\/videos\/|\/reels\/|\/stories\/|story_fbid=|fbid=)(\d+)"""),
                Regex("""(?:[?&]id=)(\d+)"""),
                Regex("""facebook\.com\/(\d{10,})"""),
                Regex("""facebook\.com\/[^\/]+\/posts\/(\d+)""")
            )
            for (p in patterns) {
                val match = p.find(trimmed)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) return match
            }
            return trimmed.substringAfterLast("/").substringBefore("?").ifBlank { trimmed }
        }
    }

    @Keep
    enum class ReactionType(val code: Int) {
        LIKE(1),
        LOVE(2),
        CARE(16),
        HAHA(4),
        WOW(3),
        SAD(7),
        ANGRY(8);

        companion object {
            fun fromString(str: String): ReactionType {
                val upper = str.uppercase()
                return values().firstOrNull { upper.contains(it.name) } ?: LIKE
            }
        }
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

    private fun postGraphQL(params: Map<String, String>, friendlyName: String, actionName: String, targetId: String? = null): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val fat = FbVault.fieldAccessToken()
        val formBuilder = FormBody.Builder()
        params.forEach { (k, v) -> formBuilder.add(k, v) }
        if (token.isNotEmpty()) {
            formBuilder.add(fat, token)
        }

        val reqBuilder = Request.Builder()
            .url(graphql())
            .post(formBuilder.build())
            .header("User-Agent", ua())
            .header("X-FB-Friendly-Name", friendlyName)
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")

        if (token.isNotEmpty()) {
            reqBuilder.header("Authorization", "OAuth $token")
        }

        return try {
            httpClient.newCall(reqBuilder.build()).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"errors\":")
                val errMsg = if (isOk) "Success" else parseErrorMessage(body)
                EngineResult(isOk, actionName, targetId, errMsg, body)
            }
        } catch (e: Exception) {
            EngineResult(false, actionName, targetId, "Lỗi kết nối mạng: ${e.message}", "")
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
                val msg = err?.optString("message")
                if (!msg.isNullOrBlank()) return msg
                val errStr = json.optString("error")
                if (errStr.isNotBlank()) return errStr
            }
        } catch (_: Exception) {}
        return if (body.isNotBlank()) body.take(300) else "Phản hồi lỗi không xác định từ Facebook"
    }

    /**
     * 1. Comment & Reply bài viết (doc_id 6739921102758190)
     */
    fun comment(feedbackId: String, text: String, replyToCommentId: String? = null): EngineResult {
        val cleanId = extractId(feedbackId)
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("feedback_id", cleanId)
            put("message", JSONObject().put("text", text))
            if (!replyToCommentId.isNullOrBlank()) {
                put("parent_comment_id", replyToCommentId)
            }
        }
        val fdi = FbVault.fieldDocId()
        val fv  = FbVault.fieldVariables()
        val params = mapOf(
            fdi to "6739921102758190",
            fv  to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "CommentCreateMutation", "COMMENT", cleanId)
    }

    /**
     * 2. Thả cảm xúc React (LIKE, LOVE, CARE, HAHA, WOW, SAD, ANGRY) (doc_id 5411782298894101)
     */
    fun react(feedbackId: String, reaction: ReactionType = ReactionType.LIKE): EngineResult {
        val cleanId = extractId(feedbackId)
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("feedback_id", cleanId)
            put("feedback_reaction", reaction.code)
        }
        val fdi = FbVault.fieldDocId()
        val fv  = FbVault.fieldVariables()
        val params = mapOf(
            fdi to FbVault.docIdProfileReact(),
            fv  to JSONObject().put("input", input).toString()
        )
        return postGraphQL(params, "UFIFeedbackReactMutation", "REACT_${reaction.name}", cleanId)
    }


    /**
     * 3. Theo dõi UID (doc_id 4268153066598920)
     */
    fun follow(targetUid: String): EngineResult {
        val cleanId = extractId(targetUid)
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("subscribee_id", cleanId)
            put("subscribe_location", "PROFILE")
        }
        val fdi = FbVault.fieldDocId(); val fv = FbVault.fieldVariables()
        val params = mapOf(fdi to "4268153066598920", fv to JSONObject().put("input", input).toString())
        return postGraphQL(params, "ActorSubscribeCoreMutation", "FOLLOW", cleanId)
    }

    /**
     * 4. Like Page (doc_id 3628174981029411)
     */
    fun likePage(pageId: String): EngineResult {
        val cleanId = extractId(pageId)
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("page_id", cleanId)
        }
        val fdi = FbVault.fieldDocId(); val fv = FbVault.fieldVariables()
        val params = mapOf(fdi to "3628174981029411", fv to JSONObject().put("input", input).toString())
        return postGraphQL(params, "PageLikeMutation", "LIKE_PAGE", cleanId)
    }

    /**
     * 5. Tham gia Group (doc_id 4981273901928471)
     */
    fun joinGroup(groupId: String): EngineResult {
        val cleanId = extractId(groupId)
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("group_id", cleanId)
            put("source", "group_mall")
        }
        val fdi = FbVault.fieldDocId(); val fv = FbVault.fieldVariables()
        val params = mapOf(fdi to "4981273901928471", fv to JSONObject().put("input", input).toString())
        return postGraphQL(params, "GroupJoinMutation", "JOIN_GROUP", cleanId)
    }

    /**
     * 6. Review 5 sao Page (doc_id 5892019284719201)
     */
    fun reviewPage(pageId: String, isPositive: Boolean = true, reviewText: String): EngineResult {
        val cleanId = extractId(pageId)
        val input = JSONObject().apply {
            put("client_mutation_id", UUID.randomUUID().toString())
            put("actor_id", userId ?: "")
            put("page_id", cleanId)
            put("recommendation_type", if (isPositive) "POSITIVE" else "NEGATIVE")
            put("review_text", JSONObject().put("text", reviewText))
        }
        val fdi = FbVault.fieldDocId(); val fv = FbVault.fieldVariables()
        val params = mapOf(fdi to "5892019284719201", fv to JSONObject().put("input", input).toString())
        return postGraphQL(params, "PageRecommendationCreateMutation", "REVIEW_PAGE", cleanId)
    }

    /**
     * 7. Chỉnh sửa bình luận
     */
    fun editComment(commentId: String, newText: String): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val formBody = FormBody.Builder().add(FbVault.fieldMessage(), newText).build()
        val request = Request.Builder()
            .url("${graphApi()}/$commentId")
            .post(formBody)
            .header("User-Agent", ua())
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

    /**
     * 8. Xóa bình luận
     */
    fun deleteComment(commentId: String): EngineResult {
        val token = (accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val request = Request.Builder()
            .url("${graphApi()}/$commentId")
            .delete()
            .header("User-Agent", ua())
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

    // Tiện ích tương thích ngược
    fun reactPost(postId: String, reaction: ReactionType = ReactionType.LIKE): EngineResult = react(postId, reaction)
    fun commentPost(postId: String, messageText: String): EngineResult = comment(postId, messageText)
    fun followUser(targetUserId: String): EngineResult = follow(targetUserId)
}

