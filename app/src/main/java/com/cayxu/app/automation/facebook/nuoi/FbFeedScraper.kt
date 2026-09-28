package com.cayxu.app.automation.facebook.nuoi

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object FbFeedScraper {
    private const val BASE_URL = "https://mbasic.facebook.com"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Thu thập danh sách bài viết trên Newsfeed bằng cơ chế cuộn trang đa tầng (Trượt từ dưới lên)
     */
    fun fetchFeedWithScroll(
        client: OkHttpClient,
        cookie: String,
        targetActorId: String? = null,
        maxPages: Int = 4
    ): List<FbPost> {
        val collectedPosts = mutableListOf<FbPost>()
        val seenPostIds = mutableSetOf<String>()

        var currentUrl = "$BASE_URL/home.php"
        var pageCount = 0

        while (currentUrl.isNotBlank() && pageCount < maxPages) {
            pageCount++
            val requestBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Cookie", cookie)
                .header("Referer", "$BASE_URL/")

            if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                requestBuilder.header("X-FB-Actor-ID", targetActorId)
            }

            val html = try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() ?: "" else ""
                }
            } catch (_: Exception) {
                ""
            }

            if (html.isBlank()) break

            val postsOnPage = parsePostsFromHtml(html)
            for (p in postsOnPage) {
                if (p.postId.isNotBlank() && seenPostIds.add(p.postId)) {
                    collectedPosts.add(p)
                }
            }

            // Tìm con trỏ cuộn trang tiếp theo (Giả lập thao tác vuốt từ dưới lên)
            val nextPageUrl = extractNextPageCursor(html)
            if (nextPageUrl.isNullOrBlank() || nextPageUrl == currentUrl) {
                break
            }
            currentUrl = nextPageUrl
        }

        // Nếu là Page hoặc nick mới không có bài trên Newfeed, fallback sang trang Video/Reels hoặc nhóm
        if (collectedPosts.isEmpty()) {
            collectedPosts.addAll(fetchFallbackPosts(client, cookie, targetActorId))
        }

        return collectedPosts
    }

    private fun parsePostsFromHtml(html: String): List<FbPost> {
        val list = mutableListOf<FbPost>()

        // 1. Bóc tách các khối bài viết hoặc liên kết phản hồi picker
        val reactionPickerPattern = Pattern.compile("href=\"(/reactions/picker/[^\"]*(?:ft_id|ft_ent_identifier)=(\\d+)[^\"]*)\"")
        val pickerMatcher = reactionPickerPattern.matcher(html)
        while (pickerMatcher.find()) {
            val pickerUrl = BASE_URL + pickerMatcher.group(1).replace("&amp;", "&")
            val postId = pickerMatcher.group(2)
            if (!postId.isNullOrBlank()) {
                list.add(FbPost(
                    postId = postId,
                    reactionPickerUrl = pickerUrl,
                    ftEntIdentifier = postId
                ))
            }
        }

        // 2. Bóc từ các liên kết bài viết /story.php?story_fbid= hoặc ft_ent_identifier
        val storyPattern = Pattern.compile("(?:story_fbid=|/posts/|ft_ent_identifier=)(\\d+)")
        val storyMatcher = storyPattern.matcher(html)
        while (storyMatcher.find()) {
            val pid = storyMatcher.group(1)
            if (!pid.isNullOrBlank() && list.none { it.postId == pid }) {
                list.add(FbPost(
                    postId = pid,
                    reactionPickerUrl = "$BASE_URL/reactions/picker/?is_permalink=1&ft_id=$pid",
                    ftEntIdentifier = pid
                ))
            }
        }

        return list
    }

    /**
     * Bóc tách liên kết phân trang cuộn tiếp "Xem tin tiếp theo"
     */
    private fun extractNextPageCursor(html: String): String? {
        val pattern = Pattern.compile("href=\"(/home\\.php\\?[^\"]*(?:cursor|start_time|offset)=[^\"]+)\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return BASE_URL + matcher.group(1).replace("&amp;", "&")
        }

        // Fallback: Tìm thẻ chứa chữ "Xem tin tiếp theo" / "See more posts"
        val seeMorePattern = Pattern.compile("href=\"(/[^\"]+)\"[^>]*><span>(?:Xem tin tiếp theo|Xem thêm tin|See more posts)</span>", Pattern.CASE_INSENSITIVE)
        val seeMoreMatcher = seeMorePattern.matcher(html)
        if (seeMoreMatcher.find()) {
            val path = seeMoreMatcher.group(1).replace("&amp;", "&")
            return if (path.startsWith("http")) path else BASE_URL + path
        }

        return null
    }

    /**
     * Nguồn dự phòng khi Newsfeed rỗng (Dành cho Fanpage Profile+ hoặc nick mới)
     */
    private fun fetchFallbackPosts(client: OkHttpClient, cookie: String, targetActorId: String?): List<FbPost> {
        val fallbackUrls = listOf(
            "$BASE_URL/watch",
            "$BASE_URL/groups",
            "$BASE_URL/friends/center/suggestions/"
        )
        for (url in fallbackUrls) {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)

            if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                reqBuilder.header("X-FB-Actor-ID", targetActorId)
            }

            val html = try {
                client.newCall(reqBuilder.build()).execute().use { if (it.isSuccessful) it.body?.string() ?: "" else "" }
            } catch (_: Exception) { "" }

            val posts = parsePostsFromHtml(html)
            if (posts.isNotEmpty()) return posts
        }
        return emptyList()
    }
}
