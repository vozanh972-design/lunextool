package com.cayxu.app.instagram.xsmm

import java.io.Serializable

data class InstagramProfile(
    val userId: String,                     // UID Instagram: e.g. "5482910482"
    val username: String,                   // Username: e.g. "minh_tran99"
    val fullName: String,                   // Tên đầy đủ
    val profilePicUrl: String? = null,      // Link ảnh đại diện
    val biography: String? = null,          // Tiểu sử
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
    val isLive: Boolean = true,
    val cookie: String? = null,
    val csrfToken: String? = null
) : Serializable

enum class InstagramActionType {
    FOLLOW,
    UNFOLLOW,
    LIKE,
    UNLIKE,
    COMMENT
}

data class InstagramActionResult(
    val isSuccess: Boolean,
    val actionType: InstagramActionType,
    val target: String,
    val message: String,
    val rawResponse: String = ""
) : Serializable

data class XsmmUserProfile(
    val username: String,
    val points: Long = 0,
    val token: String
) : Serializable

data class XsmmAccountItem(
    val id: Long,                           // ID quản lý trên XSMM: e.g. 15829
    val accountId: String,                  // UID hoặc Username: e.g. "minh_tran99"
    val name: String,
    val isActive: Boolean = false,
    val type: String = "instagram"
) : Serializable

data class XsmmTaskItem(
    val taskId: String,                     // task_id
    val type: String,                       // "instagram_follow", "instagram_like", "instagram_comment"
    val targetId: String,                   // UID hoặc ID bài viết
    val idOrLink: String,                   // URL hoặc Username đích
    val points: Int = 0,                    // Điểm thưởng (xu)
    val commentText: String? = null
) : Serializable

data class XsmmCompleteResult(
    val isSuccess: Boolean,
    val pointsEarned: Int = 0,
    val successCount: Int = 0,
    val message: String,
    val countdownSeconds: Int = 5
) : Serializable
