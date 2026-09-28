package com.cayxu.app.automation.facebook.nuoi

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object FbFriendHelper {
    private const val BASE_URL = "https://mbasic.facebook.com"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Kết bạn dạo từ danh sách gợi ý "Những người bạn có thể biết" (Chuẩn swNhungNguoiBanCoTheBiet của Kahara)
     */
    fun sendFriendRequestFromSuggestions(
        client: OkHttpClient,
        cookie: String,
        targetActorId: String? = null
    ): Boolean {
        val url = "$BASE_URL/friends/center/suggestions/"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .header("Referer", "$BASE_URL/home.php")

        if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            reqBuilder.header("X-FB-Actor-ID", targetActorId)
        }

        val html = try {
            client.newCall(reqBuilder.build()).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }
        } catch (_: Exception) { "" }

        if (html.isBlank()) return false

        // Bóc link "Thêm bạn bè"
        val addFriendPattern = Pattern.compile("href=\"(/a/mobile/friends/profile_add_friend\\.php[^\"]+)\"")
        val matcher = addFriendPattern.matcher(html)
        if (matcher.find()) {
            val actionPath = matcher.group(1).replace("&amp;", "&")
            val fullUrl = if (actionPath.startsWith("http")) actionPath else BASE_URL + actionPath

            val addReq = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .header("Referer", url)
                .build()

            return try {
                client.newCall(addReq).execute().use { res ->
                    res.isSuccessful || res.isRedirect
                }
            } catch (_: Exception) {
                false
            }
        }

        return false
    }

    /**
     * Theo dõi (Follow) tác giả bài viết
     */
    fun followAuthor(
        client: OkHttpClient,
        cookie: String,
        authorId: String,
        targetActorId: String? = null
    ): Boolean {
        if (authorId.isBlank() || !authorId.matches(Regex("^\\d+$"))) return false
        val followUrl = "$BASE_URL/subscriptions/add/?user_id=$authorId"

        val reqBuilder = Request.Builder()
            .url(followUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .header("Referer", "$BASE_URL/home.php")

        if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            reqBuilder.header("X-FB-Actor-ID", targetActorId)
        }

        return try {
            client.newCall(reqBuilder.build()).execute().use { res ->
                res.isSuccessful || res.isRedirect
            }
        } catch (_: Exception) {
            false
        }
    }
}
