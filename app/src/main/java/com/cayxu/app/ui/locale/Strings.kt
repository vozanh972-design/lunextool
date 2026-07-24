package com.cayxu.app.ui.locale

import androidx.compose.runtime.Composable

/**
 * Kho chữ dùng chung toàn app, đọc theo LanguageState.language - giống cách Color.kt (theme)
 * đọc theo ThemeState để đổi "Chế độ tối" mà không cần sửa từng màn hình.
 *
 * Muốn thêm chữ cho 1 màn hình khác (Home, Wallet, Cài đặt...): thêm 1 property vào object
 * dưới đây (kiểu vi = "...", en = "..."), rồi dùng Str.tenChu tại nơi cần thay vì hardcode
 * chuỗi tiếng Việt trực tiếp trong Composable. Hiện tại mới chỉ chuyển màn Welcome sang dùng
 * Str.xxx - các màn còn lại vẫn đang hardcode tiếng Việt, cần chuyển dần theo mẫu này.
 */
object Str {

    private val lang: AppLanguage @Composable get() = LanguageState.language

    private fun pick(vi: String, en: String): String
        @Composable get() = if (lang == AppLanguage.VI) vi else en

    // ---- Màn Welcome ----

    val welcomeLanguageLabel: String
        @Composable get() = pick("Tiếng Việt", "English")

    val welcomeAppBadge: String
        @Composable get() = pick("CâyXu", "CayXu")

    val welcomeTitleLine1: String
        @Composable get() = pick("Kiếm tiền online", "Earn money online")

    val welcomeTitleLine2: String
        @Composable get() = pick("mọi lúc, mọi nơi", "anytime, anywhere")

    val welcomeSubtitle: String
        @Composable get() = pick(
            "CâyXu giúp bạn kiếm tiền dễ dàng với nhiều nhiệm vụ hấp dẫn và thu nhập hấp dẫn.",
            "CayXu helps you earn money easily with exciting tasks and attractive income."
        )

    val welcomeGetStarted: String
        @Composable get() = pick("Bắt đầu ngay", "Get Started")

    val welcomeTerms: String
        @Composable get() = pick(
            "Bằng việc tiếp tục, bạn đồng ý với Điều khoản sử dụng và Chính sách bảo mật của chúng tôi.",
            "By continuing, you agree to our Terms of Service and Privacy Policy."
        )
}
