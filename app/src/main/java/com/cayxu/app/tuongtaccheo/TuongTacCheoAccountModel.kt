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
    FB_LIKE_VIP("likevip", "Like chéo VIP"),
    FB_LIKE("like", "Like chéo"),
    FB_CX_VIP("cxvip", "Cảm xúc chéo VIP", "camxucvip"),
    FB_CX("cx", "Cảm xúc chéo thường", "camxuc"),
    FB_CX_CMT("cxcmt", "Cảm xúc chéo bình luận", "camxuccmt"),
    FB_COMMENT("cmt", "Bình luận chéo", "comment"),
    FB_FOLLOW("sub", "Theo dõi chéo", "follow"),
    FB_SUB_VIP("subvip", "Theo dõi chéo VIP", "followvip"),
    FB_SHARE("share", "Share chéo"),
    FB_SHARE_ND("sharend", "Share chéo kèm nội dung", "sharent"),
    FB_PAGE("page", "Like page chéo", "likepage"),
    FB_MEMBER("member", "Tham gia nhóm chéo", "group"),
    FB_REVIEW("danhgia", "Đánh giá page chéo", "review"),
    TIKTOK_LIKE("tiktok_like", "TikTok Like"),
    TIKTOK_FOLLOW("tiktok_follow", "TikTok Follow");

    companion object {
        fun fromKey(key: String): TTCJobType = when (key.lowercase()) {
            "likevip" -> FB_LIKE_VIP
            "like" -> FB_LIKE
            "cxvip", "camxucvip" -> FB_CX_VIP
            "cx", "camxuc" -> FB_CX
            "cxcmt", "camxuccmt" -> FB_CX_CMT
            "cmt", "comment", "cmtvip" -> FB_COMMENT
            "sub", "follow" -> FB_FOLLOW
            "subvip", "followvip" -> FB_SUB_VIP
            "share" -> FB_SHARE
            "sharend", "sharent" -> FB_SHARE_ND
            "page", "likepage" -> FB_PAGE
            "member", "group" -> FB_MEMBER
            "danhgia", "review" -> FB_REVIEW
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
