package com.cayxu.app.facebook

import java.io.Serializable

/**
 * Model đại diện cho 1 Tài khoản Facebook (tương ứng item trong Ảnh 2).
 */
data class FacebookAccount(
    val id: String,                         // UID Facebook: e.g. "61594124635612"
    val name: String,                       // Tên Facebook: e.g. "Khong Minh"
    val email: String? = null,              // Email/Username: e.g. "bwt94475@laola.com"
    val avatarUrl: String? = null,          // URL ảnh đại diện (https://graph.facebook.com/v19.0/{id}/picture?type=large)
    val cookie: String? = null,             // Chuỗi Cookie (chứa c_user, xs,...)
    val token: String? = null,              // Access Token (EAAG...)
    val proxy: String? = null,              // Proxy (host:port hoặc host:port:user:pass)
    val password: String? = null,
    val twoFactorSecret: String? = null,
    val pages: MutableList<FacebookPage> = mutableListOf(), // Danh sách Fanpage / Profile Plus con
    var isSelected: Boolean = false,
    var status: String = "Live",
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Model đại diện cho 1 Page con của tài khoản Facebook.
 */
data class FacebookPage(
    val pageId: String,                     // ID của Page
    val pageName: String,                   // Tên của Page
    val pageToken: String? = null,          // Token của Page (nếu có)
    val additionalProfileId: String? = null,// Profile Plus ID (nếu là dạng Profile Plus)
    val parentUserId: String,               // UID của tài khoản Facebook sở hữu
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Kiểu trường dữ liệu khi chọn định dạng trong Dialog Thêm nhiều tài khoản (Ảnh 1).
 */
enum class AccountFieldType(val displayName: String) {
    USERNAME("Tài khoản"),
    PASSWORD("Mật khẩu"),
    TWO_FACTOR("2FA"),
    COOKIE("Cookie"),
    PROXY("Proxy"),
    TOKEN("Token")
}
