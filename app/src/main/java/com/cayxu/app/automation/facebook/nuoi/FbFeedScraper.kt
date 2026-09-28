package com.cayxu.app.automation.facebook.nuoi

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class TokenExpiredException(val rawJsonError: String) : Exception(rawJsonError)

object FbFeedScraper {
    private const val TAG = "FbFeedScraper"
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v21.0"
    const val KATANA_UA = "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"

    var lastDiagnosticLog: String = ""

    private fun logDiagnostic(msg: String, diag: StringBuilder? = null) {
        Log.d(TAG, msg)
        diag?.appendLine(msg)
    }

    /**
     * Xác thực Token nhanh qua Graph API Meta bằng Katana Native Header.
     * Trả về (true, rawResponse) nếu Token hợp lệ (Live), hoặc (false, rawJsonError) nếu Token bị từ chối/hết hạn.
     */
    fun validateToken(client: OkHttpClient, token: String): Pair<Boolean, String> {
        if (token.isBlank()) {
            return false to "Tài khoản chưa có Access Token (Vui lòng kiểm tra lại Token)"
        }
        val url = "$GRAPH_API_BASE/me?fields=id,name&access_token=$token"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful && !body.contains("\"error\"")) {
                    true to body
                } else {
                    val rawError = try {
                        val root = JSONObject(body)
                        root.optJSONObject("error")?.toString(2) ?: body
                    } catch (_: Exception) {
                        body.ifBlank { "HTTP ${res.code}: Không nhận được phản hồi từ Meta" }
                    }
                    false to rawError
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: "Lỗi kết nối mạng khi xác thực Token")
        }
    }

    fun parseCount(raw: String): Int {
        val clean = raw.trim().lowercase()
        if (clean.isBlank()) return 0
        return try {
            when {
                clean.endsWith("k") -> {
                    val num = clean.removeSuffix("k").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                    (num * 1000).toInt()
                }
                clean.endsWith("m") -> {
                    val num = clean.removeSuffix("m").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                    (num * 1000000).toInt()
                }
                clean.contains(".") && clean.indexOf(".") == clean.length - 4 -> {
                    clean.replace(".", "").toIntOrNull() ?: 0
                }
                clean.contains(",") && clean.indexOf(",") == clean.length - 4 -> {
                    clean.replace(",", "").toIntOrNull() ?: 0
                }
                clean.contains(",") -> {
                    clean.replace(",", ".").toDoubleOrNull()?.toInt() ?: 0
                }
                else -> clean.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun createSnippet(text: String, maxLength: Int = 28): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return "Bài viết không có văn bản"
        return if (clean.length <= maxLength) clean else clean.take(maxLength).trimEnd() + "..."
    }

    /**
     * Thu thập danh sách bài viết để nuôi Facebook:
     * 100% CƠ CHẾ ACCESS TOKEN (GRAPH API & NATIVE GRAPHQL) - TUYỆT ĐỐI KHÔNG DÙNG MBASIC / WEB HTML.
     *
     * Các nguồn lấy bài (100% Token API thật từ tài khoản):
     * 1. Fanpage nick đã Like/Follow (/me/likes -> /{page_id}/posts)
     * 2. Nhóm/Group nick đã tham gia (/me/groups -> /{group_id}/feed)
     * 3. Bạn bè của nick (/me/friends -> /{friend_id}/posts)
     * 4. Bảng tin Dòng thời gian của nick (/me/feed)
     * 5. Katana Native Android GraphQL (HomeFeed / FeedUnits)
     *
     * Tuyệt đối KHÔNG hardcode bất kỳ Fanpage hay ID mẫu nào.
     */
    fun fetchFeedWithScroll(
        client: OkHttpClient,
        cookie: String = "",
        token: String = "",
        targetActorId: String? = null,
        myUid: String = "",
        myName: String = "",
        maxPages: Int = 4,
        targetCount: Int = 15
    ): List<FbPost> {
        val diag = StringBuilder()
        val collectedPosts = mutableListOf<FbPost>()
        val seenPostIds = mutableSetOf<String>()

        logDiagnostic("=== BẮT ĐẦU THU THẬP BÀI VIẾT 100% BẰNG ACCESS TOKEN (GRAPH API & NATIVE) ===", diag)
        logDiagnostic("UID: $myUid | Tên: $myName | Mục tiêu: $targetCount bài", diag)

        if (token.isBlank()) {
            throw IllegalArgumentException("Tài khoản chưa có Access Token. Nuôi tương tác Facebook chạy 100% bằng API Token, cấm dùng Web/mbasic.")
        }

        // 1. Nguồn 1: Kéo bài từ các Fanpage tài khoản đã bấm Like/Follow (/me/likes)
        if (collectedPosts.size < targetCount) {
            val remaining = targetCount - collectedPosts.size
            logDiagnostic("-> [Nguồn 1] Quét bài từ các Fanpage nick đã Like/Follow (/me/likes)...", diag)
            val pagePosts = fetchPostsFromLikedPages(client, token, myUid, myName, remaining, seenPostIds, diag)
            collectedPosts.addAll(pagePosts)
            logDiagnostic("-> [Nguồn 1] Thu được ${pagePosts.size} bài viết từ các Fanpage đã Like", diag)
        }

        // 2. Nguồn 2: Kéo bài từ các Nhóm/Group tài khoản đã tham gia (/me/groups)
        if (collectedPosts.size < targetCount) {
            val remaining = targetCount - collectedPosts.size
            logDiagnostic("-> [Nguồn 2] Quét bài từ các Nhóm nick đã tham gia (/me/groups)...", diag)
            val groupPosts = fetchPostsFromJoinedGroups(client, token, myUid, myName, remaining, seenPostIds, diag)
            collectedPosts.addAll(groupPosts)
            logDiagnostic("-> [Nguồn 2] Thu được ${groupPosts.size} bài viết từ các Nhóm đã tham gia", diag)
        }

        // 3. Nguồn 3: Kéo bài từ Bạn bè của nick (/me/friends)
        if (collectedPosts.size < targetCount) {
            val remaining = targetCount - collectedPosts.size
            logDiagnostic("-> [Nguồn 3] Quét bài từ Bạn bè của nick (/me/friends)...", diag)
            val friendPosts = fetchPostsFromFriends(client, token, myUid, myName, remaining, seenPostIds, diag)
            collectedPosts.addAll(friendPosts)
            logDiagnostic("-> [Nguồn 3] Thu được ${friendPosts.size} bài viết từ Bạn bè", diag)
        }

        // 4. Nguồn 4: Kéo bài từ Dòng thời gian của nick (/me/feed)
        if (collectedPosts.size < targetCount) {
            val remaining = targetCount - collectedPosts.size
            logDiagnostic("-> [Nguồn 4] Quét bài từ Bảng tin Dòng thời gian (/me/feed)...", diag)
            val meFeedPosts = fetchPostsFromMeFeed(client, token, myUid, myName, remaining, seenPostIds, diag)
            collectedPosts.addAll(meFeedPosts)
            logDiagnostic("-> [Nguồn 4] Thu được ${meFeedPosts.size} bài viết từ /me/feed", diag)
        }

        // 5. Nguồn 5: Katana Native Android GraphQL
        if (collectedPosts.isEmpty()) {
            logDiagnostic("-> [Nguồn 5] Thử truy vấn qua Katana Native GraphQL Engine...", diag)
            val nativePosts = fetchFeedViaNativeGraphQL(client, token, targetActorId, myUid, myName, maxPages, targetCount, seenPostIds, diag)
            collectedPosts.addAll(nativePosts)
            logDiagnostic("-> [Nguồn 5] Thu được ${nativePosts.size} bài từ Native GraphQL", diag)
        }

        lastDiagnosticLog = diag.toString()
        logDiagnostic("=== HOÀN TẤT THU THẬP: ${collectedPosts.size} bài viết thực tế ===", diag)
        return collectedPosts
    }

    /**
     * Nguồn 1: Lấy bài viết từ các Page mà tài khoản đã bấm Like / Follow
     * Endpoint: GET /v21.0/me/likes?fields=id,name&limit=15
     */
    private fun fetchPostsFromLikedPages(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val likesUrl = "$GRAPH_API_BASE/me/likes?fields=id,name&limit=15&access_token=$token"
        logDiagnostic("  [GET Page Likes] URL: $likesUrl", diag)

        val req = Request.Builder()
            .url(likesUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        val responseBody = try {
            client.newCall(req).execute().use { res ->
                val code = res.code
                val body = res.body?.string().orEmpty()
                logDiagnostic("  [Page Likes Response] HTTP $code | Length: ${body.length}", diag)
                body
            }
        } catch (e: Exception) {
            logDiagnostic("  [Page Likes Exception] ${e.message}", diag)
            ""
        }

        if (responseBody.isBlank()) return result
        checkTokenError(responseBody)

        val root = try { JSONObject(responseBody) } catch (_: Exception) { return result }
        val dataArray = root.optJSONArray("data") ?: JSONArray()
        logDiagnostic("  -> Đã tìm thấy ${dataArray.length()} Page nick đã Like", diag)

        for (i in 0 until dataArray.length()) {
            if (result.size >= targetCount) break
            val pageObj = dataArray.optJSONObject(i) ?: continue
            val pageId = pageObj.optString("id")
            val pageName = pageObj.optString("name")
            if (pageId.isBlank()) continue

            val postsUrl = "$GRAPH_API_BASE/$pageId/posts?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=3&access_token=$token"
            val pReq = Request.Builder()
                .url(postsUrl)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .get()
                .build()

            try {
                client.newCall(pReq).execute().use { pRes ->
                    val pCode = pRes.code
                    val pBody = pRes.body?.string().orEmpty()
                    if (pCode in 200..299 && pBody.isNotBlank()) {
                        val pRoot = JSONObject(pBody)
                        val pData = pRoot.optJSONArray("data") ?: JSONArray()
                        for (j in 0 until pData.length()) {
                            val item = pData.optJSONObject(j) ?: continue
                            val parsedPost = extractPostFromJson(item, pageName, pageId, myUid, myName, seenPostIds)
                            if (parsedPost != null) {
                                result.add(parsedPost)
                                logDiagnostic("    + [$pageName] ${parsedPost.messageSnippet} (💬 ${parsedPost.commentCount} | 👍 ${parsedPost.reactionCount})", diag)
                                if (result.size >= targetCount) break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }

    /**
     * Nguồn 2: Lấy bài viết từ các Nhóm / Group đã tham gia
     * Endpoint: GET /v21.0/me/groups?fields=id,name&limit=15
     */
    private fun fetchPostsFromJoinedGroups(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val groupsUrl = "$GRAPH_API_BASE/me/groups?fields=id,name&limit=15&access_token=$token"
        logDiagnostic("  [GET Groups] URL: $groupsUrl", diag)

        val req = Request.Builder()
            .url(groupsUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        val responseBody = try {
            client.newCall(req).execute().use { res ->
                val code = res.code
                val body = res.body?.string().orEmpty()
                logDiagnostic("  [Groups Response] HTTP $code | Length: ${body.length}", diag)
                body
            }
        } catch (e: Exception) {
            logDiagnostic("  [Groups Exception] ${e.message}", diag)
            ""
        }

        if (responseBody.isBlank()) return result
        checkTokenError(responseBody)

        val root = try { JSONObject(responseBody) } catch (_: Exception) { return result }
        val dataArray = root.optJSONArray("data") ?: JSONArray()
        logDiagnostic("  -> Đã tìm thấy ${dataArray.length()} Group đã tham gia", diag)

        for (i in 0 until dataArray.length()) {
            if (result.size >= targetCount) break
            val grpObj = dataArray.optJSONObject(i) ?: continue
            val groupId = grpObj.optString("id")
            val groupName = grpObj.optString("name")
            if (groupId.isBlank()) continue

            val feedUrl = "$GRAPH_API_BASE/$groupId/feed?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=3&access_token=$token"
            val gReq = Request.Builder()
                .url(feedUrl)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .get()
                .build()

            try {
                client.newCall(gReq).execute().use { gRes ->
                    val gCode = gRes.code
                    val gBody = gRes.body?.string().orEmpty()
                    if (gCode in 200..299 && gBody.isNotBlank()) {
                        val gRoot = JSONObject(gBody)
                        val gData = gRoot.optJSONArray("data") ?: JSONArray()
                        for (j in 0 until gData.length()) {
                            val item = gData.optJSONObject(j) ?: continue
                            val parsedPost = extractPostFromJson(item, groupName, groupId, myUid, myName, seenPostIds)
                            if (parsedPost != null) {
                                result.add(parsedPost)
                                logDiagnostic("    + [$groupName] ${parsedPost.messageSnippet} (💬 ${parsedPost.commentCount} | 👍 ${parsedPost.reactionCount})", diag)
                                if (result.size >= targetCount) break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }

    /**
     * Nguồn 3: Lấy bài viết từ Bạn bè của nick
     * Endpoint: GET /v21.0/me/friends?fields=id,name&limit=15
     */
    private fun fetchPostsFromFriends(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val friendsUrl = "$GRAPH_API_BASE/me/friends?fields=id,name&limit=15&access_token=$token"
        logDiagnostic("  [GET Friends] URL: $friendsUrl", diag)

        val req = Request.Builder()
            .url(friendsUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        val responseBody = try {
            client.newCall(req).execute().use { res ->
                val code = res.code
                val body = res.body?.string().orEmpty()
                logDiagnostic("  [Friends Response] HTTP $code | Length: ${body.length}", diag)
                body
            }
        } catch (e: Exception) {
            logDiagnostic("  [Friends Exception] ${e.message}", diag)
            ""
        }

        if (responseBody.isBlank()) return result
        checkTokenError(responseBody)

        val root = try { JSONObject(responseBody) } catch (_: Exception) { return result }
        val dataArray = root.optJSONArray("data") ?: JSONArray()
        logDiagnostic("  -> Đã tìm thấy ${dataArray.length()} Bạn bè", diag)

        for (i in 0 until dataArray.length()) {
            if (result.size >= targetCount) break
            val friendObj = dataArray.optJSONObject(i) ?: continue
            val friendId = friendObj.optString("id")
            val friendName = friendObj.optString("name")
            if (friendId.isBlank()) continue

            val postsUrl = "$GRAPH_API_BASE/$friendId/posts?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=3&access_token=$token"
            val fReq = Request.Builder()
                .url(postsUrl)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .get()
                .build()

            try {
                client.newCall(fReq).execute().use { fRes ->
                    val fCode = fRes.code
                    val fBody = fRes.body?.string().orEmpty()
                    if (fCode in 200..299 && fBody.isNotBlank()) {
                        val fRoot = JSONObject(fBody)
                        val fData = fRoot.optJSONArray("data") ?: JSONArray()
                        for (j in 0 until fData.length()) {
                            val item = fData.optJSONObject(j) ?: continue
                            val parsedPost = extractPostFromJson(item, friendName, friendId, myUid, myName, seenPostIds)
                            if (parsedPost != null) {
                                result.add(parsedPost)
                                logDiagnostic("    + [$friendName] ${parsedPost.messageSnippet} (💬 ${parsedPost.commentCount} | 👍 ${parsedPost.reactionCount})", diag)
                                if (result.size >= targetCount) break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }

    /**
     * Nguồn 4: Lấy bài viết từ Dòng thời gian của tài khoản (/me/feed)
     * Endpoint: GET /v21.0/me/feed?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=15
     */
    private fun fetchPostsFromMeFeed(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val feedUrl = "$GRAPH_API_BASE/me/feed?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=15&access_token=$token"
        logDiagnostic("  [GET /me/feed] URL: $feedUrl", diag)

        val req = Request.Builder()
            .url(feedUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        val responseBody = try {
            client.newCall(req).execute().use { res ->
                val code = res.code
                val body = res.body?.string().orEmpty()
                logDiagnostic("  [/me/feed Response] HTTP $code | Length: ${body.length}", diag)
                body
            }
        } catch (e: Exception) {
            logDiagnostic("  [/me/feed Exception] ${e.message}", diag)
            ""
        }

        if (responseBody.isBlank()) return result
        checkTokenError(responseBody)

        val root = try { JSONObject(responseBody) } catch (_: Exception) { return result }
        val dataArray = root.optJSONArray("data") ?: JSONArray()
        for (i in 0 until dataArray.length()) {
            if (result.size >= targetCount) break
            val item = dataArray.optJSONObject(i) ?: continue
            val parsedPost = extractPostFromJson(item, "Người dùng Facebook", "", myUid, myName, seenPostIds)
            if (parsedPost != null) {
                result.add(parsedPost)
                logDiagnostic("    + [Feed] ${parsedPost.authorName}: ${parsedPost.messageSnippet} (💬 ${parsedPost.commentCount} | 👍 ${parsedPost.reactionCount})", diag)
            }
        }

        return result
    }

    /**
     * Nguồn 5: Cào Newfeed qua Native Katana Android GraphQL (DocID HomeFeedQuery / FeedUnitsPaginatingQuery)
     */
    private fun fetchFeedViaNativeGraphQL(
        client: OkHttpClient,
        token: String,
        targetActorId: String?,
        myUid: String,
        myName: String,
        maxPages: Int,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        var currentCursor: String? = null
        var pagesLoaded = 0

        val docIds = listOf("7112046835581177", "4463426747065985", "5579753175402517")
        var currentDocIndex = 0

        while (pagesLoaded < maxPages && result.size < targetCount) {
            pagesLoaded++
            val docId = docIds.getOrElse(currentDocIndex) { docIds[0] }
            try {
                val vars = JSONObject().apply {
                    put("count", 10)
                    put("cursor", if (currentCursor.isNullOrBlank()) JSONObject.NULL else currentCursor)
                    put("feedLocation", "NEWSFEED")
                    put("feedbackSource", 1)
                }

                val formBuilder = FormBody.Builder()
                    .add("doc_id", docId)
                    .add("variables", vars.toString())
                    .add("access_token", token)
                    .add("method", "post")

                if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                    formBuilder.add("actor_id", targetActorId)
                }

                val req = Request.Builder()
                    .url("https://graph.facebook.com/graphql")
                    .header("Authorization", "OAuth $token")
                    .header("User-Agent", KATANA_UA)
                    .header("X-FB-Connection-Type", "WIFI")
                    .header("Accept-Encoding", "gzip, deflate")
                    .post(formBuilder.build())
                    .build()

                val responseBody = client.newCall(req).execute().use { res ->
                    res.body?.string().orEmpty()
                }

                if (responseBody.isBlank()) break
                checkTokenError(responseBody)

                val parsed = parseGraphQLResponse(
                    rawBody = responseBody,
                    myUid = myUid,
                    myName = myName,
                    targetActorId = targetActorId,
                    seenPostIds = seenPostIds,
                    targetCount = targetCount - result.size
                )

                if (parsed.posts.isNotEmpty()) {
                    result.addAll(parsed.posts)
                    if (result.size >= targetCount || parsed.endCursor.isNullOrBlank()) {
                        break
                    }
                    currentCursor = parsed.endCursor
                } else if (pagesLoaded == 1 && currentDocIndex < docIds.size - 1) {
                    currentDocIndex++
                    pagesLoaded--
                } else {
                    break
                }
            } catch (e: Exception) {
                if (e is TokenExpiredException) throw e
                break
            }
        }

        return result
    }

    private fun extractPostFromJson(
        item: JSONObject,
        fallbackAuthorName: String,
        fallbackAuthorId: String,
        myUid: String,
        myName: String,
        seenPostIds: MutableSet<String>
    ): FbPost? {
        val rawPostId = item.optString("id")
        if (rawPostId.isBlank()) return null

        val realPostId = if (rawPostId.contains("_")) rawPostId.substringAfter("_") else rawPostId
        if (!seenPostIds.add(realPostId)) return null

        val from = item.optJSONObject("from")
        val authorName = from?.optString("name")?.ifBlank { fallbackAuthorName } ?: fallbackAuthorName
        val authorId = from?.optString("id")?.ifBlank { fallbackAuthorId } ?: fallbackAuthorId

        val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || rawPostId.startsWith("${myUid}_") || realPostId == myUid)) ||
                (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                authorId == "me"

        if (isSelfPost) return null

        val message = item.optString("message").ifBlank { item.optString("story", "") }
        val reactionCount = item.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
            ?: item.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
            ?: 0

        val commentCount = item.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
            ?: 0

        val feedbackId = item.optJSONObject("feedback")?.optString("id")?.ifBlank { realPostId } ?: realPostId

        return FbPost(
            postId = realPostId,
            authorName = authorName.ifBlank { "Trang Facebook" },
            authorId = authorId,
            message = message,
            messageSnippet = createSnippet(message),
            commentCount = commentCount,
            reactionCount = reactionCount,
            ftEntIdentifier = feedbackId
        )
    }

    private data class ParsedFeedResult(
        val posts: List<FbPost>,
        val endCursor: String?
    )

    private fun parseGraphQLResponse(
        rawBody: String,
        myUid: String,
        myName: String,
        targetActorId: String?,
        seenPostIds: MutableSet<String>,
        targetCount: Int
    ): ParsedFeedResult {
        val posts = mutableListOf<FbPost>()
        var endCursor: String? = null

        val root = try { JSONObject(rawBody) } catch (_: Exception) { return ParsedFeedResult(emptyList(), null) }
        val data = root.optJSONObject("data") ?: return ParsedFeedResult(emptyList(), null)

        val viewer = data.optJSONObject("viewer")
        val feed = viewer?.optJSONObject("news_feed")
            ?: viewer?.optJSONObject("home_feed")
            ?: data.optJSONObject("viewer_feed")
            ?: data.optJSONObject("feed")

        if (feed != null) {
            val pageInfo = feed.optJSONObject("page_info")
            endCursor = pageInfo?.optString("end_cursor")

            val edges = feed.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val rawPostId = node.optString("id")
                if (rawPostId.isBlank()) continue

                val realPostId = if (rawPostId.contains("_")) rawPostId.substringAfter("_") else rawPostId
                if (!seenPostIds.add(realPostId)) continue

                val actors = node.optJSONArray("actors")
                val firstActor = actors?.optJSONObject(0)
                val authorName = firstActor?.optString("name") ?: "Người dùng Facebook"
                val authorId = firstActor?.optString("id") ?: ""

                val isSelf = (myUid.isNotBlank() && (authorId == myUid || rawPostId.startsWith("${myUid}_") || realPostId.startsWith("${myUid}_"))) ||
                        (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                        authorId == "me" ||
                        (!targetActorId.isNullOrBlank() && (authorId == targetActorId || rawPostId.startsWith("${targetActorId}_")))

                if (isSelf) continue

                val msgObj = node.optJSONObject("message")
                val messageText = msgObj?.optString("text")
                    ?: node.optString("story", "")

                val feedback = node.optJSONObject("feedback")
                val reactionCount = feedback?.optJSONObject("reactors")?.optInt("count")
                    ?: feedback?.optJSONObject("reaction_count")?.optInt("count")
                    ?: 0

                val commentCount = feedback?.optJSONObject("total_comment_count")?.optInt("count")
                    ?: feedback?.optInt("total_comment_count")
                    ?: feedback?.optJSONObject("comment_count")?.optInt("count")
                    ?: 0

                val feedbackId = feedback?.optString("id")?.ifBlank { realPostId } ?: realPostId

                posts.add(
                    FbPost(
                        postId = realPostId,
                        authorName = authorName.ifBlank { "Người dùng Facebook" },
                        authorId = authorId,
                        message = messageText,
                        messageSnippet = createSnippet(messageText),
                        commentCount = commentCount,
                        reactionCount = reactionCount,
                        ftEntIdentifier = feedbackId
                    )
                )

                if (posts.size >= targetCount) break
            }
        }

        return ParsedFeedResult(posts, endCursor)
    }

    private fun checkTokenError(jsonStr: String) {
        if (!jsonStr.contains("\"error\"")) return
        try {
            val root = JSONObject(jsonStr)
            val error = root.optJSONObject("error")
            if (error != null) {
                val code = error.optInt("code")
                val subcode = error.optInt("error_subcode")
                val message = error.optString("message")
                val isTokenError = code == 190 || subcode in listOf(458, 459, 460, 463, 467, 490) ||
                        (message.contains("Session has expired", ignoreCase = true) ||
                         message.contains("Error validating access token", ignoreCase = true) ||
                         message.contains("The access token could not be decrypted", ignoreCase = true))

                if (isTokenError) {
                    val rawJson = error.toString(2)
                    throw TokenExpiredException(rawJson)
                }
            }
        } catch (e: Exception) {
            if (e is TokenExpiredException) throw e
        }
    }
}
