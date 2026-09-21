package com.cayxu.app.tuongtaccheo

import java.io.Serializable

/**
 * Model tài khoản TuongTacCheo (TTC)
 */
data class TuongTacCheoAccount(
    val username: String,
    val token: String,
    var cookie: String? = null,
    var sodu: Long = 0,
    var proxy: String? = null,
    var isSelected: Boolean = false,
    var status: String = "Live",
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Kiểu nhiệm vụ TTC
 */
enum class TTCJobType(val apiType: String, val displayName: String) {
    FB_LIKE("like", "Facebook Like"),
    FB_FOLLOW("follow", "Facebook Follow"),
    FB_COMMENT("comment", "Facebook Comment"),
    FB_SHARE("share", "Facebook Share"),
    FB_PAGE("page", "Facebook Like Page"),
    FB_MEMBER("member", "Facebook Tham gia nhóm"),
    FB_VIP("vip", "Nhiệm vụ VIP"),
    TIKTOK_LIKE("tiktok_like", "TikTok Like"),
    TIKTOK_FOLLOW("tiktok_follow", "TikTok Follow")
}

/**
 * Model nhiệm vụ trả về từ /getpost.php
 */
data class TTCJob(
    val id: String,                     // Job ID
    val idfb: String? = null,           // UID Facebook đích
    val idpost: String? = null,         // Post ID
    val link: String? = null,           // Link thực hiện
    val loaicx: String? = null,         // Loại cảm xúc (LIKE, LOVE, WOW,...)
    val cmt: String? = null,            // Nội dung comment
    val uid: String? = null             // User ID thực hiện
) : Serializable

/**
 * Kết quả nhận tiền / nhận xu
 */
data class TTCNhanTienResult(
    val isSuccess: Boolean,
    val sodu: Long,
    val xuThem: Long = 0,
    val message: String
)
