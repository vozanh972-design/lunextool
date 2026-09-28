package com.cayxu.app.automation.facebook.nuoi

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object FbReactionHelper {
    private const val BASE_URL = "https://mbasic.facebook.com"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Thả cảm xúc ngẫu nhiên vào bài viết thông qua Picker Reaction của mbasic
     */
    fun sendReaction(
        client: OkHttpClient,
        cookie: String,
        post: FbPost,
        selectedReactionTypes: Set<String>,
        targetActorId: String? = null
    ): Boolean {
        val pickerUrl = if (post.reactionPickerUrl.isNotBlank()) {
            post.reactionPickerUrl
        } else {
            "$BASE_URL/reactions/picker/?is_permalink=1&ft_id=${post.postId}"
        }

        // 1. Tải giao diện Picker cảm xúc
        val reqBuilder = Request.Builder()
            .url(pickerUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .header("Referer", "$BASE_URL/home.php")

        if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            reqBuilder.header("X-FB-Actor-ID", targetActorId)
        }

        val pickerHtml = try {
            client.newCall(reqBuilder.build()).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }
        } catch (_: Exception) { "" }

        if (pickerHtml.isBlank()) return false

        // 2. Chọn 1 cảm xúc ngẫu nhiên từ danh sách đã cấu hình (hoặc mặc định Like)
        val rawTarget = selectedReactionTypes.randomOrNull() ?: "1"
        val targetTypeId = when (rawTarget.uppercase()) {
            "1", "LIKE" -> "1"
            "2", "LOVE" -> "2"
            "16", "CARE" -> "16"
            "4", "HAHA" -> "4"
            "3", "WOW" -> "3"
            "7", "SAD" -> "7"
            "8", "ANGRY" -> "8"
            else -> rawTarget
        }

        // 3. Bóc link thực thi cảm xúc tương ứng trong picker HTML
        val reactionLinkPattern = Pattern.compile("href=\"(/ufi/reaction/[^\"]*reaction_type=$targetTypeId[^\"]*)\"")
        var matcher = reactionLinkPattern.matcher(pickerHtml)
        var actionPath: String? = null

        if (matcher.find()) {
            actionPath = matcher.group(1).replace("&amp;", "&")
        } else {
            // Fallback: Tìm link reaction bất kỳ nếu không khớp đúng loại
            val anyReactionPattern = Pattern.compile("href=\"(/ufi/reaction/[^\"]+)\"")
            matcher = anyReactionPattern.matcher(pickerHtml)
            if (matcher.find()) {
                actionPath = matcher.group(1).replace("&amp;", "&")
            }
        }

        if (actionPath.isNullOrBlank()) return false

        val fullActionUrl = if (actionPath.startsWith("http")) actionPath else BASE_URL + actionPath

        // 4. Gửi GET để kích hoạt thả cảm xúc
        val submitReq = Request.Builder()
            .url(fullActionUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .header("Referer", pickerUrl)
            .build()

        return try {
            client.newCall(submitReq).execute().use { response ->
                response.isSuccessful || response.isRedirect
            }
        } catch (_: Exception) {
            false
        }
    }
}
