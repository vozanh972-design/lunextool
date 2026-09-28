package com.cayxu.app.automation.facebook.nuoi

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object FbCommentHelper {
    private const val BASE_URL = "https://mbasic.facebook.com"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Gửi bình luận tùy chỉnh ngẫu nhiên vào bài viết
     */
    fun sendComment(
        client: OkHttpClient,
        cookie: String,
        post: FbPost,
        commentList: List<String>,
        targetActorId: String? = null
    ): Boolean {
        val validComments = commentList.map { it.trim() }.filter { it.isNotBlank() }
        if (validComments.isEmpty()) return false

        val commentText = validComments.random()
        val postUrl = "$BASE_URL/story.php?story_fbid=${post.postId}&id=${post.authorId.ifBlank { "0" }}"

        // 1. Tải trang chi tiết bài viết để bóc form comment và token bảo mật
        val pageReq = Request.Builder()
            .url(postUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .header("Referer", "$BASE_URL/home.php")
            .build()

        val html = try {
            client.newCall(pageReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }
        } catch (_: Exception) { "" }

        if (html.isBlank()) return false

        // 2. Bóc action URL của form bình luận
        val formActionPattern = Pattern.compile("action=\"(/a/comment\\.php[^\"]+)\"")
        val formMatcher = formActionPattern.matcher(html)
        val actionPath = if (formMatcher.find()) formMatcher.group(1).replace("&amp;", "&") else null

        if (actionPath.isNullOrBlank()) return false
        val fullActionUrl = if (actionPath.startsWith("http")) actionPath else BASE_URL + actionPath

        // 3. Bóc các trường ẩn: fb_dtsg, jazoest
        val fbDtsg = extractHiddenInput(html, "fb_dtsg")
        val jazoest = extractHiddenInput(html, "jazoest")

        // 4. Đóng gói dữ liệu form gửi đi
        val formBodyBuilder = FormBody.Builder()
            .add("comment_text", commentText)

        if (!fbDtsg.isNullOrBlank()) formBodyBuilder.add("fb_dtsg", fbDtsg)
        if (!jazoest.isNullOrBlank()) formBodyBuilder.add("jazoest", jazoest)

        val postReqBuilder = Request.Builder()
            .url(fullActionUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .header("Referer", postUrl)
            .post(formBodyBuilder.build())

        if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            postReqBuilder.header("X-FB-Actor-ID", targetActorId)
        }

        return try {
            client.newCall(postReqBuilder.build()).execute().use { res ->
                res.isSuccessful || res.isRedirect
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractHiddenInput(html: String, name: String): String? {
        val pattern = Pattern.compile("name=\"$name\"\\s+value=\"([^\"]*)\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) return matcher.group(1)

        val patternReverse = Pattern.compile("value=\"([^\"]*)\"\\s+name=\"$name\"")
        val matcherReverse = patternReverse.matcher(html)
        if (matcherReverse.find()) return matcherReverse.group(1)

        return null
    }
}
