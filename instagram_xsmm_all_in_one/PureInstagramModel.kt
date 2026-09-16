package com.cayxu.app.instagram.pure

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
