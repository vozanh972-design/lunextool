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
enum class TTCJobType(
    val apiType: String,
    val displayName: String,
    val alternateApiType: String? = null
) {
    FB_LIKE("like", "Facebook Cảm xúc", "cx"),
    FB_FOLLOW("sub", "Facebook Theo dõi", "follow"),
    FB_COMMENT("cmt", "Facebook Bình luận", "comment"),
    FB_SHARE("share", "Facebook Share"),
    FB_PAGE("page", "Facebook Like Page"),
    FB_MEMBER("member", "Facebook Tham gia nhóm"),
    FB_CX_VIP("cxvip", "Facebook Cảm xúc VIP", "likevip"),
    FB_SUB_VIP("subvip", "Facebook Theo dõi VIP"),
    FB_CMT_VIP("cmtvip", "Facebook Bình luận VIP"),
    TIKTOK_LIKE("tiktok_like", "TikTok Like"),
    TIKTOK_FOLLOW("tiktok_follow", "TikTok Follow");

    companion object {
        fun fromKey(key: String): TTCJobType = when (key.lowercase()) {
            "like" -> FB_LIKE
            "follow", "sub" -> FB_FOLLOW
            "comment", "cmt" -> FB_COMMENT
            "page" -> FB_PAGE
            "member" -> FB_MEMBER
            "cxvip", "likevip" -> FB_CX_VIP
            "subvip", "followvip" -> FB_SUB_VIP
            "cmtvip" -> FB_CMT_VIP
            else -> FB_LIKE
        }
    }
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
