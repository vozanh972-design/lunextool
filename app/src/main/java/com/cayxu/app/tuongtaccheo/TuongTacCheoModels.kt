package com.cayxu.app.tuongtaccheo

import java.io.Serializable

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

enum class TTCJobType(val apiType: String, val displayName: String, val alternateApiType: String? = null) {
    FB_LIKE_VIP("likevip", "Facebook Like VIP"),
    FB_LIKE("like", "Facebook Like"),
    FB_CX_VIP("cxvip", "Facebook Cảm xúc VIP", "camxucvip"),
    FB_CX("cx", "Facebook Cảm xúc", "camxuc"),
    FB_CX_CMT("cxcmt", "Facebook Cảm xúc cmt", "camxuccmt"),
    FB_COMMENT("cmt", "Facebook Comment", "comment"),
    FB_FOLLOW("sub", "Facebook Follow", "follow"),
    FB_SUB_VIP("subvip", "Facebook Follow VIP", "followvip"),
    FB_SHARE("share", "Facebook Share"),
    FB_SHARE_ND("sharend", "Facebook Share kèm nội dung", "sharent"),
    FB_PAGE("page", "Facebook Like Page", "likepage"),
    FB_MEMBER("member", "Facebook Tham gia nhóm", "group"),
    FB_REVIEW("danhgia", "Facebook Đánh giá page", "review"),
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

data class TTCJob(
    val id: String,
    val idfb: String? = null,
    val idpost: String? = null,
    val link: String? = null,
    val loaicx: String? = null,
    val cmt: String? = null,
    val uid: String? = null
) : Serializable

data class TTCDatNickResult(
    val isSuccess: Boolean,
    val code: Int, // 1: Thành công, 2: Chưa thêm nick vào web TTC
    val message: String,
    val rawResponse: String
)

data class TTCThemNickResult(
    val isSuccess: Boolean,
    val message: String,
    val rawResponse: String
)

data class TTCNhanTienResult(
    val isSuccess: Boolean,
    val sodu: Long,
    val xuThem: Long = 0,
    val message: String
)
