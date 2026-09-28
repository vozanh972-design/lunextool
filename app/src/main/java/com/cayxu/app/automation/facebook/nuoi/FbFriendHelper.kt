package com.cayxu.app.automation.facebook.nuoi

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object FbFriendHelper {
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v19.0"

    /**
     * Gửi lời mời kết bạn / theo dõi tác giả bài viết bằng Access Token (Chuẩn Kahara Mod)
     */
    fun followOrAddFriend(
        client: OkHttpClient,
        token: String,
        cookie: String = "",
        authorId: String,
        targetActorId: String? = null
    ): Boolean {
        if (authorId.isBlank() || !authorId.matches(Regex("^\\d+$"))) return false

        if (token.isNotBlank()) {
            // 1. Thử gửi kết bạn: /me/friends/{authorId}
            val friendUrl = "$GRAPH_API_BASE/me/friends/$authorId"
            val formBody = FormBody.Builder()
                .add("access_token", token)
                .build()

            val reqBuilder = Request.Builder()
                .url(friendUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(formBody)

            if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                reqBuilder.header("X-FB-Actor-ID", targetActorId)
            }

            val friendSuccess = try {
                client.newCall(reqBuilder.build()).execute().use { res ->
                    res.isSuccessful || res.body?.string().orEmpty().contains("\"success\":true")
                }
            } catch (_: Exception) {
                false
            }

            if (friendSuccess) return true

            // 2. Thử theo dõi (Subscribe / Follow) tác giả: /{authorId}/subscribers
            val followUrl = "$GRAPH_API_BASE/$authorId/subscribers"
            val followReq = Request.Builder()
                .url(followUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(FormBody.Builder().add("access_token", token).build())

            if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                followReq.header("X-FB-Actor-ID", targetActorId)
            }

            val followSuccess = try {
                client.newCall(followReq.build()).execute().use { res ->
                    res.isSuccessful || res.body?.string().orEmpty().contains("\"success\":true")
                }
            } catch (_: Exception) {
                false
            }

            return followSuccess
        }

        return false
    }
}
