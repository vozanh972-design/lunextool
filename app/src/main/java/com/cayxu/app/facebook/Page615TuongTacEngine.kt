package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Engine tương tác chuyên biệt dành riêng cho Facebook Page (Profile+ / Fanpage đầu số 615).
 * Sử dụng trực tiếp Page Access Token và Facebook Graph API v21.0 / GraphQL.
 * Hoàn toàn tách biệt khỏi tương tác của Profile cá nhân mẹ.
 */
@Keep
class Page615TuongTacEngine(
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

        fun extractId(urlOrId: String): String {
            val trimmed = urlOrId.trim()
            if (trimmed.all { it.isDigit() }) return trimmed

            val patterns = listOf(
                Regex("""(?:posts|videos|reel|photos|fbid=)(\d+)"""),
                Regex("""story_fbid=(\d+)"""),
                Regex("""facebook\.com/(\d+)"""),
                Regex("""facebook\.com/[^/]+/posts/(\d+)"""),
                Regex("""facebook\.com/[^/]+/videos/(\d+)"""),
                Regex("""facebook\.com/watch/\?v=(\d+)"""),
                Regex("""facebook\.com/reel/(\d+)"""),
                Regex("""facebook\.com/groups/[^/]+/permalink/(\d+)"""),
                Regex("""facebook\.com/share/[pr]/([a-zA-Z0-9]+)"""),
                Regex("""id=(\d+)""")
            )

            for (p in patterns) {
                val match = p.find(trimmed)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) return match
            }
            return trimmed.substringAfterLast("/").substringBefore("?").ifBlank { trimmed }
        }
    }

    @Keep
    enum class ReactionType(val value: String, val graphqlCode: Int) {
        LIKE("LIKE", 1),
        LOVE("LOVE", 2),
        WOW("WOW", 3),
        HAHA("HAHA", 4),
        SAD("SAD", 7),
        ANGRY("ANGRY", 8),
        CARE("CARE", 16)
    }

    @Keep
    data class InteractionResult(
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

    private fun parseErrorMessage(body: String): String {
        try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                val msg = err?.optString("message")
                if (!msg.isNullOrBlank()) return msg
                val errStr = json.optString("error")
                if (errStr.isNotBlank()) return errStr
            }
            if (json.has("errors")) {
                val errs = json.optJSONArray("errors")
                if (errs != null && errs.length() > 0) {
                    val first = errs.optJSONObject(0)
                    val msg = first?.optString("message")
                    if (!msg.isNullOrBlank()) return msg
                }
            }
        } catch (_: Exception) {}
        return if (body.isNotBlank()) body.take(300) else "Lỗi thực hiện tương tác Facebook"
    }

    /**
     * Thả cảm xúc vào bài viết / comment dưới danh nghĩa Page
     */
    fun reactPost(
        postId: String,
        reactionType: ReactionType = ReactionType.LIKE,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanPostId = extractId(postId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanPostId, "REACT", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("type", reactionType.value)
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanPostId/reactions")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, cleanPostId, "REACT_${reactionType.value}", null, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanPostId, "REACT", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    /**
     * Fallback react qua GraphQL Voice của Page
     */
    fun reactGraphQLVoice(
        feedbackId: String,
        userToken: String,
        reactionType: ReactionType = ReactionType.LIKE,
        overridePageId: String? = null
    ): InteractionResult {
        val cleanId = extractId(feedbackId)
        val actor = overridePageId ?: pageId615 ?: ""
        if (actor.isEmpty()) return InteractionResult(false, cleanId, "REACT_GQL", null, "Page 615 ID required", "")

        val variables = JSONObject().apply {
            put("input", JSONObject().apply {
                put("feedback_id", cleanId)
                put("feedback_reaction", reactionType.graphqlCode)
                put("actor_id", actor)
                put("client_mutation_id", UUID.randomUUID().toString())
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
                InteractionResult(isOk, cleanId, "REACT_GQL_${reactionType.value}", null, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanId, "REACT_GQL", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    /**
     * Bình luận bài viết dưới danh nghĩa Page
     */
    fun commentPost(
        postId: String,
        message: String,
        attachmentId: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanPostId = extractId(postId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanPostId, "COMMENT", null, "Page token required", "")

        val formBuilder = FormBody.Builder()
            .add("message", message)
            .add("access_token", token)

        if (!attachmentId.isNullOrEmpty()) {
            formBuilder.add("attachment_id", attachmentId)
        }

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanPostId/comments")
            .post(formBuilder.build())
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                InteractionResult(isOk, cleanPostId, "COMMENT", commentId, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanPostId, "COMMENT", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    /**
     * Trả lời bình luận dưới danh nghĩa Page
     */
    fun replyComment(
        parentCommentId: String,
        message: String,
        attachmentId: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanCommentId = extractId(parentCommentId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanCommentId, "REPLY_COMMENT", null, "Page token required", "")

        val formBuilder = FormBody.Builder()
            .add("message", message)
            .add("access_token", token)

        if (!attachmentId.isNullOrEmpty()) {
            formBuilder.add("attachment_id", attachmentId)
        }

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanCommentId/comments")
            .post(formBuilder.build())
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                InteractionResult(isOk, cleanCommentId, "REPLY_COMMENT", commentId, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanCommentId, "REPLY_COMMENT", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    /**
     * Theo dõi (Follow) UID hoặc Page Profile+ khác dưới danh nghĩa Page
     */
    fun followTarget(
        targetId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanTargetId = extractId(targetId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanTargetId, "FOLLOW", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanTargetId/subscribers")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, cleanTargetId, "FOLLOW", null, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanTargetId, "FOLLOW", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    fun unfollowTarget(
        targetId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanTargetId = extractId(targetId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanTargetId, "UNFOLLOW", null, "Page token required", "")

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanTargetId/subscribers?access_token=$token")
            .delete()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, cleanTargetId, "UNFOLLOW", null, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanTargetId, "UNFOLLOW", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    /**
     * Thích Page khác dưới danh nghĩa Page (Tự động thích ứng Fanpage cổ điển và Profile+ 615)
     */
    fun likeOtherPage(
        targetPageId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanTargetId = extractId(targetPageId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanTargetId, "LIKE_PAGE", null, "Page token required", "")

        // Đối với Fanpage Profile+ mới (UID 615...), Facebook chuyển cơ chế like sang follow (/subscribers)
        if (cleanTargetId.startsWith("615")) {
            return followTarget(cleanTargetId, overrideToken)
        }

        val formBody = FormBody.Builder()
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanTargetId/likes")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                if (!isOk) {
                    // Nếu lỗi do trang không hỗ trợ POST /likes (ví dụ là Profile+), tự động fallback sang followTarget
                    val followRes = followTarget(cleanTargetId, overrideToken)
                    if (followRes.isSuccess) return followRes
                    InteractionResult(false, cleanTargetId, "LIKE_PAGE", null, parseErrorMessage(body), body)
                } else {
                    InteractionResult(true, cleanTargetId, "LIKE_PAGE", null, "Success", body)
                }
            }
        } catch (e: Exception) {
            // Fallback sang followTarget
            val followRes = followTarget(cleanTargetId, overrideToken)
            if (followRes.isSuccess) followRes else InteractionResult(false, cleanTargetId, "LIKE_PAGE", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    fun unlikeOtherPage(
        targetPageId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanTargetId = extractId(targetPageId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanTargetId, "UNLIKE_PAGE", null, "Page token required", "")

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanTargetId/likes?access_token=$token")
            .delete()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, cleanTargetId, "UNLIKE_PAGE", null, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanTargetId, "UNLIKE_PAGE", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    /**
     * Tham gia nhóm dưới danh nghĩa Page
     */
    fun joinGroup(
        groupId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val cleanGroupId = extractId(groupId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanGroupId, "JOIN_GROUP", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanGroupId/members")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, cleanGroupId, "JOIN_GROUP", null, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanGroupId, "JOIN_GROUP", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }

    /**
     * Đánh giá Page khác dưới danh nghĩa Page
     */
    fun reviewOtherPage(
        targetPageId: String,
        reviewText: String,
        recommendationType: String = "positive",
        overrideToken: String? = null
    ): InteractionResult {
        val cleanTargetId = extractId(targetPageId)
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, cleanTargetId, "REVIEW_PAGE", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("recommendation_type", recommendationType)
            .add("review_text", reviewText)
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$cleanTargetId/ratings")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, cleanTargetId, "REVIEW_PAGE", null, if (isOk) "Success" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanTargetId, "REVIEW_PAGE", null, e.message ?: "Lỗi kết nối mạng", "")
        }
    }
}