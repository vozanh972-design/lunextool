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

/**
 * Danh mục chuẩn xác 100% từng thư mục job của TuongTacCheo:
 */
enum class TTCJobType(val path: String, val displayName: String) {
    FB_LIKE("likepostvipre", "Facebook Like thường"),
    FB_LIKE_VIP("likepostvipre", "Facebook Like VIP"),
    FB_REACTION("camxucvipre", "Facebook Cảm xúc thường"),
    FB_CX("camxucvipre", "Facebook Cảm xúc thường"),
    FB_CX_VIP("camxucvipre", "Facebook Cảm xúc VIP"),
    FB_CX_COMMENT("camxuccheobinhluan", "Facebook Cảm xúc bình luận"),
    FB_CX_CMT("camxuccheobinhluan", "Facebook Cảm xúc bình luận"),
    FB_COMMENT("cmtcheo", "Facebook Bình luận"),
    FB_FOLLOW("subcheo", "Facebook Theo dõi"),
    FB_SUB_VIP("subcheo", "Facebook Theo dõi VIP"),
    FB_PAGE("likepagecheo", "Facebook Like Page"),
    FB_JOIN_GROUP("thamgianhomcheo", "Facebook Tham gia nhóm"),
    FB_MEMBER("thamgianhomcheo", "Facebook Tham gia nhóm"),
    FB_REVIEW_PAGE("danhgiapage", "Facebook Đánh giá Page"),
    FB_REVIEW("danhgiapage", "Facebook Đánh giá Page"),
    FB_SHARE("sharecheo", "Facebook Share thường"),
    FB_SHARE_CONTENT("sharecheokemnoidung", "Facebook Share kèm nội dung"),
    FB_SHARE_ND("sharecheokemnoidung", "Facebook Share kèm nội dung");

    val apiType: String get() = path

    companion object {
        fun fromKey(key: String): TTCJobType = when (key.lowercase()) {
            "like", "likevip", "likepostvipre" -> FB_LIKE
            "cx", "camxuc", "camxucvip", "camxucvipre", "reaction" -> FB_REACTION
            "cxcmt", "camxuccmt", "camxuccheobinhluan" -> FB_CX_COMMENT
            "cmt", "comment", "cmtcheo", "cmtvip" -> FB_COMMENT
            "sub", "subvip", "follow", "followvip", "subcheo" -> FB_FOLLOW
            "page", "likepage", "likepagecheo" -> FB_PAGE
            "member", "group", "thamgianhomcheo" -> FB_JOIN_GROUP
            "danhgia", "review", "danhgiapage" -> FB_REVIEW_PAGE
            "share", "sharecheo" -> FB_SHARE
            "sharend", "sharent", "sharecheokemnoidung" -> FB_SHARE_CONTENT
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
    val code: Int, // 1: Thành công, 2: Chưa thêm nick vào TTC, -1: Vui lòng thao tác chậm lại
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
