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

    @Composable
    private fun pick(vi: String, en: String): String =
        if (LanguageState.language == AppLanguage.VI) vi else en

    // ---- Màn Welcome ----

    val welcomeLanguageLabel: String
        @Composable get() = pick("Tiếng Việt", "English")

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

    // Câu điều khoản được tách thành 3 mảnh để 2 cụm "Điều khoản sử dụng" / "Chính sách bảo
    // mật" bấm vào được (mở bảng đọc nội dung), phần còn lại là chữ thường không bấm được.
    val welcomeTermsPrefix: String
        @Composable get() = pick("Bằng việc tiếp tục, bạn đồng ý với ", "By continuing, you agree to our ")

    val welcomeTermsLink: String
        @Composable get() = pick("Điều khoản sử dụng", "Terms of Service")

    val welcomeTermsMiddle: String
        @Composable get() = pick(" và ", " and ")

    val welcomePrivacyLink: String
        @Composable get() = pick("Chính sách bảo mật", "Privacy Policy")

    val welcomeTermsSuffix: String
        @Composable get() = pick(" của chúng tôi.", ".")

    val termsDialogTitle: String
        @Composable get() = pick("Điều khoản sử dụng", "Terms of Service")

    val termsDialogBody: String
        @Composable get() = pick(
            "Bằng việc sử dụng CâyXu, bạn đồng ý sử dụng ứng dụng đúng mục đích, không can thiệp, " +
                "chỉnh sửa hay khai thác trái phép hệ thống dưới bất kỳ hình thức nào.\n\n" +
                "CâyXu có quyền tạm ngưng hoặc thu hồi quyền truy cập nếu phát hiện hành vi gian lận, " +
                "vi phạm điều khoản hoặc vi phạm pháp luật hiện hành.\n\n" +
                "Nội dung, nhiệm vụ và phần thưởng trong ứng dụng có thể được điều chỉnh theo thời gian " +
                "để đảm bảo trải nghiệm an toàn, minh bạch cho tất cả người dùng. Ứng dụng không chứa " +
                "và không hỗ trợ bất kỳ nội dung, chức năng nào trái quy định pháp luật.",
            "By using CayXu, you agree to use the app for its intended purpose only, and not to " +
                "interfere with, modify, or exploit the system in any unauthorized way.\n\n" +
                "CayXu reserves the right to suspend or revoke access if fraudulent activity, a " +
                "violation of these terms, or a violation of applicable law is detected.\n\n" +
                "Content, tasks, and rewards within the app may be adjusted over time to keep the " +
                "experience safe and transparent for all users. The app does not contain or support " +
                "any content or functionality that violates applicable regulations."
        )

    val privacyDialogTitle: String
        @Composable get() = pick("Chính sách bảo mật", "Privacy Policy")

    val privacyDialogBody: String
        @Composable get() = pick(
            "CâyXu không thu thập, lưu trữ hay chia sẻ bất kỳ thông tin cá nhân nhạy cảm nào của " +
                "bạn cho bên thứ ba.\n\n" +
                "Ứng dụng chỉ sử dụng những dữ liệu tối thiểu, cần thiết để vận hành tính năng (như xác " +
                "thực key kích hoạt) và không truy cập mật khẩu hay dữ liệu riêng tư khác trên thiết bị " +
                "của bạn.\n\n" +
                "CâyXu cam kết hoạt động minh bạch, tuân thủ quy định pháp luật hiện hành về bảo vệ " +
                "dữ liệu người dùng, không chứa và không thực hiện bất kỳ hành vi thu thập dữ liệu trái phép nào.",
            "CayXu does not collect, store, or share any of your sensitive personal information with " +
                "third parties.\n\n" +
                "The app only uses the minimum data necessary to operate its features (such as " +
                "activation key verification) and does not access your passwords or other private data " +
                "on your device.\n\n" +
                "CayXu is committed to operating transparently, in compliance with applicable data " +
                "protection regulations, and does not contain or engage in any unauthorized data " +
                "collection."
        )

    val dialogCloseButton: String
        @Composable get() = pick("Đã hiểu", "Got it")
}
