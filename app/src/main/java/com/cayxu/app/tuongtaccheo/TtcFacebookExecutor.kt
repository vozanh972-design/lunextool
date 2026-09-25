package com.cayxu.app.tuongtaccheo

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bộ điều phối tương tác Facebook cho TuongTacCheo được sao chép 100% nguyên bản từ module XSMM.
 * Đảm bảo tính ổn định tối đa cho Page (Profile+ / 615) và Profile (Nick mẹ) với cơ chế Graph API v21.0 fallback.
 */
object TtcFacebookExecutor {

    data class FbTaskResult(
        val isSuccess: Boolean,
        val message: String? = null,
        val isPostUnavailable: Boolean = false
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isPostUnavailableError(error: String?): Boolean {
        if (error.isNullOrBlank()) return false
        return error.contains("1446034") ||
               error.contains("Content Not Available", ignoreCase = true) ||
               error.contains("not available anymore", ignoreCase = true) ||
               error.contains("bài viết đã bị xóa", ignoreCase = true) ||
               error.contains("bài viết không còn khả dụng", ignoreCase = true)
    }

    fun executeFacebookTask(
        taskType: String,
        targetId: String,
        comment: String,
        reactionStr: String = "",
        token: String,
        cookie: String,
        proxyStr: String?,
        uid: String? = null,
        isPage: Boolean = false,
        pageId615: String? = null,
        userToken: String? = null,
        page615ReactionMethod: String = "auto"
    ): FbTaskResult {
        if (targetId.isBlank()) return FbTaskResult(false, "Thiếu ID đối tượng (targetId trống)")
        val cleanToken = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return FbTaskResult(false, "Token Facebook trống hoặc chưa được cấp quyền")

        val proxyParts = proxyStr?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        val lower = taskType.lowercase()
        if (lower.contains("comment") && comment.isBlank()) {
            return FbTaskResult(false, "Không có nội dung bình luận từ nhiệm vụ TTC")
        }

        // Xác định chính xác loại cảm xúc cần tương tác (LOVE, CARE, HAHA, WOW, SAD, ANGRY, LIKE)
        val reactTarget = if (reactionStr.isNotBlank()) reactionStr else taskType
        val pageReaction = com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.fromString(reactTarget)
        val profileReaction = com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.fromString(reactTarget)

        // =========================================================================
        // 1. NẾU LÀ TÀI KHOẢN PAGE (PROFILE+ / 615):
        // SỬ DỤNG FULL LOGIC TƯƠNG TÁC TỪ Page615TuongTacEngine (Graph API v21.0)
        // =========================================================================
        if (isPage) {
            val pageEngine = com.cayxu.app.facebook.Page615TuongTacEngine(
                pageToken = cleanToken,
                pageId615 = pageId615 ?: uid,
                userToken = userToken,
                proxyHost = proxyHost,
                proxyPort = proxyPort
            )
            val res = when {
                lower.contains("comment") -> {
                    pageEngine.commentPost(targetId, comment)
                }
                lower.contains("follow") || lower.contains("sub") -> {
                    pageEngine.followTarget(targetId)
                }
                lower.contains("page") -> {
                    pageEngine.likeOtherPage(targetId)
                }
                lower.contains("group") || lower.contains("member") || lower.contains("join") -> {
                    pageEngine.joinGroup(targetId)
                }
                lower.contains("review") || lower.contains("danhgia") -> {
                    pageEngine.reviewOtherPage(targetId, reviewText = comment.ifBlank { "Tuyệt vời!" }, recommendationType = "positive")
                }
                lower.contains("like") || lower.contains("love") || lower.contains("care") ||
                lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") || reactionStr.isNotBlank() -> {
                    pageEngine.reactPost(targetId, pageReaction, forceMethod = page615ReactionMethod)
                }
                else -> {
                    pageEngine.reactPost(targetId, pageReaction, forceMethod = page615ReactionMethod)
                }
            }
            val rawMsg = if (res.isSuccess) "Thành công" else (res.message ?: res.rawResponse)
            val unavailable = isPostUnavailableError(rawMsg) || isPostUnavailableError(res.rawResponse)
            return FbTaskResult(res.isSuccess, rawMsg, unavailable)
        }

        // =========================================================================
        // 2. NẾU LÀ TÀI KHOẢN PROFILE CÁ NHÂN MẸ:
        // GIỮ NGUYÊN 100% LOGIC CỦA FacebookTuongTacEngine KÈM FALLBACK GRAPH API
        // =========================================================================
        val engine = com.cayxu.app.facebook.FacebookTuongTacEngine(
            accessToken = cleanToken,
            userId = uid,
            proxyHost = proxyHost,
            proxyPort = proxyPort
        )

        val result = when {
            lower.contains("comment") -> {
                engine.comment(targetId, comment)
            }
            lower.contains("follow") || lower.contains("sub") -> {
                engine.follow(targetId)
            }
            lower.contains("page") -> {
                val r = engine.likePage(targetId)
                if (!r.isSuccess && targetId.startsWith("615")) {
                    engine.follow(targetId)
                } else {
                    r
                }
            }
            lower.contains("group") || lower.contains("member") || lower.contains("join") -> {
                engine.joinGroup(targetId)
            }
            lower.contains("review") || lower.contains("danhgia") -> {
                engine.reviewPage(targetId, isPositive = true, reviewText = comment.ifBlank { "Tuyệt vời!" })
            }
            lower.contains("like") || lower.contains("love") || lower.contains("care") ||
            lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") || reactionStr.isNotBlank() -> {
                engine.react(targetId, profileReaction)
            }
            else -> {
                engine.react(targetId, profileReaction)
            }
        }

        if (result.isSuccess) return FbTaskResult(true, "Thành công")

        // Fallback sang Graph API v21.0 nếu GraphQL mutation gặp lỗi
        var fallbackErrMsg = result.message
        try {
            when {
                lower.contains("page") -> {
                    val fRes = engine.follow(targetId)
                    if (fRes.isSuccess) return FbTaskResult(true, "Thành công")
                    fallbackErrMsg = fRes.message ?: fallbackErrMsg
                }
                lower.contains("like") || lower.contains("love") || lower.contains("care") ||
                lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") || reactionStr.isNotBlank() -> {
                    val reactName = when (profileReaction) {
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LOVE -> "LOVE"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.CARE -> "CARE"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.HAHA -> "HAHA"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.WOW -> "WOW"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.SAD -> "SAD"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.ANGRY -> "ANGRY"
                        else -> "LIKE"
                    }
                    val url = if (reactName == "LIKE") {
                        "https://graph.facebook.com/v21.0/$targetId/likes?access_token=$cleanToken"
                    } else {
                        "https://graph.facebook.com/v21.0/$targetId/reactions?type=$reactName&access_token=$cleanToken"
                    }
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
                lower.contains("follow") || lower.contains("sub") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/subscribers?access_token=$cleanToken"
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
                lower.contains("comment") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/comments?access_token=$cleanToken"
                    val form = FormBody.Builder().add("message", comment).build()
                    val req = Request.Builder().url(url).post(form).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
            }
        } catch (e: Exception) {
            fallbackErrMsg = e.message ?: fallbackErrMsg
        }

        val finalMsg = fallbackErrMsg ?: "Lỗi thực hiện tương tác Facebook"
        val unavailable = isPostUnavailableError(finalMsg) || isPostUnavailableError(result.rawResponse)
        return FbTaskResult(false, finalMsg, unavailable)
    }

    private fun cleanFbError(body: String): String {
        try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                val title = err?.optString("error_user_title")?.takeIf { it.isNotBlank() }
                val userMsg = err?.optString("error_user_msg")?.takeIf { it.isNotBlank() }
                if (!title.isNullOrBlank() || !userMsg.isNullOrBlank()) {
                    return listOfNotNull(title, userMsg).joinToString(": ")
                }
                val code = err?.optInt("code", 0) ?: 0
                val subcode = err?.optInt("error_subcode", 0) ?: 0
                if (code == 368 || subcode == 1390008) {
                    return "Tài khoản bị Facebook giới hạn tính năng tạm thời (Spam Block - Mã 368)"
                }
                val msg = err?.optString("message")
                if (!msg.isNullOrBlank()) return msg
            }
        } catch (_: Exception) {}
        return body
    }
}
