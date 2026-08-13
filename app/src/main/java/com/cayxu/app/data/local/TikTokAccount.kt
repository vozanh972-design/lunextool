package com.cayxu.app.data.local

/**
 * Model tài khoản TikTok - RIÊNG cho TikTok, không đụng tới LinkedAccountsStore
 * (dùng chung cho Instagram/LinkedIn/...) hay FacebookAccountsStore.
 */
enum class TikTokAccountStatus {
    ACTIVE,     // Hoạt động
    CHECKING,   // Đang kiểm tra
    LOCKED      // Bị khóa
}

/** Loại TikTok mà tài khoản này được lấy về (ảnh hưởng cách tool tự động mở app). */
enum class TikTokAppVariant(val packageName: String) {
    STANDARD("com.ss.android.ugc.trill"),
    LITE("com.zhiliaoapp.musically.go"),
    STUDIO("com.ss.android.tt.creator")
}

data class TikTokAccount(
    val uid: String,                 // Khóa duy nhất nội bộ, sinh khi thêm tài khoản
    val handle: String = "",         // @handle lấy được từ trang "Tôi" trong app TikTok
    val displayName: String = "",
    val subName: String = "",        // Tên phụ do người dùng tự đặt thêm
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: TikTokAccountStatus = TikTokAccountStatus.ACTIVE,
    val enabled: Boolean = true,
    val taskCount: Int = 0,
    val variant: TikTokAppVariant = TikTokAppVariant.STANDARD
)
