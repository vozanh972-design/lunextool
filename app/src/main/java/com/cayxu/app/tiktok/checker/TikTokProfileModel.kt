package com.cayxu.app.tiktok.checker

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model đầy đủ thông tin tài khoản TikTok trích xuất từ ByteDance Web/API & Snowflake ID.
 */
data class TikTokFullProfile(
    val userId: String = "",                    // UID 64-bit của TikTok: e.g. "6714932179850478593"
    val secUid: String? = null,                 // sec_user_id bảo mật
    val username: String = "",                  // Handle / Unique ID: e.g. "theanhgmn"
    val nickname: String = "",                  // Tên hiển thị: e.g. "gmn"
    val avatarHdUrl: String? = null,            // Ảnh đại diện HD (avatarLarger)
    val avatarThumbUrl: String? = null,         // Ảnh đại diện thumbnail
    val biography: String = "",                 // Tiểu sử
    val followerCount: Long = 0L,               // Số người theo dõi
    val followingCount: Long = 0L,              // Số người đang theo dõi
    val totalFavorited: Long = 0L,              // Tổng lượt Like / Tim nhận được
    val videoCount: Long = 0L,                  // Tổng số video đã đăng
    val isPrivate: Boolean = false,             // Tài khoản riêng tư
    val isVerified: Boolean = false,            // Tích xanh xác thực
    val createTimestampSec: Long = 0L,          // Timestamp ngày tạo tài khoản (giây)
    val isLive: Boolean = true                  // Trạng thái tài khoản tồn tại (không bị ban/die)
) : Serializable {

    /**
     * Ngày tạo tài khoản định dạng ngày tháng năm (dd/MM/yyyy)
     */
    val formattedCreateDate: String
        get() {
            if (createTimestampSec <= 0L) return ""
            return try {
                val date = Date(createTimestampSec * 1000L)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.format(date)
            } catch (e: Exception) {
                ""
            }
        }
}
