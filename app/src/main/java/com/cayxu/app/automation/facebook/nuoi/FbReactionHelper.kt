package com.cayxu.app.automation.facebook.nuoi

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object FbReactionHelper {
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v19.0"

    /**
     * Thả cảm xúc dạo vào bài viết bằng Access Token theo chuẩn Graph API của Kahara Mod
     */
    fun sendReaction(
        client: OkHttpClient,
        token: String,
        cookie: String = "",
        post: FbPost,
        selectedReactionTypes: Set<String>,
        targetActorId: String? = null
    ): Boolean {
        // 1. Chuẩn hóa loại cảm xúc (LIKE, LOVE, CARE, HAHA, WOW, SAD, ANGRY)
        val rawTarget = selectedReactionTypes.randomOrNull() ?: "LIKE"
        val reactionType = when (rawTarget.uppercase()) {
            "1", "LIKE" -> "LIKE"
            "2", "LOVE" -> "LOVE"
            "16", "CARE" -> "CARE"
            "4", "HAHA" -> "HAHA"
            "3", "WOW" -> "WOW"
            "7", "SAD" -> "SAD"
            "8", "ANGRY" -> "ANGRY"
            else -> "LIKE"
        }

        if (token.isNotBlank()) {
            // Thử postId đầy đủ (VD: 1000123_456789) hoặc ID số (456789)
            val candidateIds = linkedSetOf(post.postId, post.ftEntIdentifier).filter { it.isNotBlank() }

            for (targetId in candidateIds) {
                val url = "$GRAPH_API_BASE/$targetId/reactions"
                val formBody = FormBody.Builder()
                    .add("type", reactionType)
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
                        code in 200..299 || body.contains("\"success\":true") || body.contains("\"success\": true")
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
