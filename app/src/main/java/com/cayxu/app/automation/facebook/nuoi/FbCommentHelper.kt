package com.cayxu.app.automation.facebook.nuoi

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object FbCommentHelper {
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v19.0"

    /**
     * Gửi bình luận dạo bằng Access Token theo chuẩn Graph API của Kahara Mod
     */
    fun sendComment(
        client: OkHttpClient,
        token: String,
        cookie: String = "",
        post: FbPost,
        commentList: List<String>,
        targetActorId: String? = null
    ): Boolean {
        val validComments = commentList.map { it.trim() }.filter { it.isNotBlank() }
        if (validComments.isEmpty()) return false

        val commentText = validComments.random()

        if (token.isNotBlank()) {
            val candidateIds = linkedSetOf(post.postId, post.ftEntIdentifier).filter { it.isNotBlank() }

            for (targetId in candidateIds) {
                val url = "$GRAPH_API_BASE/$targetId/comments"
                val formBody = FormBody.Builder()
                    .add("message", commentText)
                    .add("access_token", token)
                    .build()

                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .post(formBody)

                if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                    reqBuilder.header("X-FB-Actor-ID", targetActorId)
                }

                val success = try {
                    client.newCall(reqBuilder.build()).execute().use { res ->
                        val code = res.code
                        val body = res.body?.string().orEmpty()
                        code in 200..299 || body.contains("\"id\":")
                    }
                } catch (_: Exception) {
                    false
                }

                if (success) return true
            }
        }

        return false
    }
}
