package com.cayxu.app.ui.locale

/**
 * Kho chữ dùng chung toàn app, đọc tự động theo LanguageState.language.
 * Vì LanguageState.language là Compose mutableStateOf, mọi Composable đọc Str
 * sẽ tự động recompose sang ngôn ngữ mới theo thời gian thực mà không cần
 * khai báo @Composable trên từng getter (cho phép gọi an toàn từ cả callback, Toast, v.v.).
 */
object Str {

    private fun pick(vi: String, en: String): String =
        if (LanguageState.language == AppLanguage.VI) vi else en

    fun get(vi: String, en: String): String =
        if (LanguageState.language == AppLanguage.VI) vi else en

    // ---- Thanh Dock điều hướng (MainShell.kt) ----
    val tabHome: String get() = pick("Trang chủ", "Home")
    val tabTasks: String get() = pick("Nhiệm vụ", "Tasks")
    val tabUtilities: String get() = pick("Tiện ích", "Utilities")
    val tabProfile: String get() = pick("Hồ sơ", "Profile")

    // ---- Trang chủ (HomeScreen.kt) ----
    val homeTotalCoins: String get() = pick("Tổng xu", "Total Coins")
    val homeServices: String get() = pick("Dịch vụ", "Services")
    val homeRemaining: String get() = pick("Còn lại", "Remaining")
    val homeDaysUnit: String get() = pick(" ngày", " days")
    val homeTodayPerformance: String get() = pick("Hiệu suất hôm nay", "Today's Performance")
    val homeExploreTasks: String get() = pick("Khám phá nhiệm vụ", "Explore Tasks")
    val homeExploreDesc: String get() = pick("Chọn nền tảng để bắt đầu kiếm xu", "Choose a platform to start earning coins")
    val homeQuickAccess: String get() = pick("Truy cập nhanh", "Quick Access")
    val homeReady: String get() = pick("Sẵn sàng", "Ready")
    val homeNotSupported: String get() = pick("Chưa hỗ trợ", "Not Supported")
    val search: String get() = pick("Tìm kiếm", "Search")
    val notifications: String get() = pick("Thông báo", "Notifications")

    fun greeting(hour: Int): String {
        val isEn = LanguageState.language == AppLanguage.EN
        return when (hour) {
            in 5..11 -> if (isEn) "Good morning" else "Chào buổi sáng"
            in 12..17 -> if (isEn) "Good afternoon" else "Chào buổi chiều"
            else -> if (isEn) "Good evening" else "Chào buổi tối"
        }
    }

    // ---- Nhiệm vụ (TasksScreen.kt) ----
    val tasksTitle: String get() = pick("Nhiệm vụ", "Tasks")
    fun tasksSubtitle(count: Int): String = get("$count nền tảng kiếm xu đang kết nối", "$count earning platforms connected")
    val tasksTotalCoins: String get() = pick("Tổng xu", "Total Coins")
    val tasksTodayPerformance: String get() = pick("Hiệu suất hôm nay", "Today's Performance")
    val tasksNotSupported: String get() = pick("Chưa hỗ trợ", "Not Supported")

    // ---- Tiện ích (UtilitiesScreen.kt) ----
    val utilitiesTitle: String get() = pick("Tiện ích mở rộng", "Extensions & Utilities")
    val utilitiesSubtitle: String get() = pick("Tối ưu và tự động hóa quy trình nuôi tài khoản mạng xã hội", "Optimize and automate social media account workflows")
    val utilitiesFeaturedBadge: String get() = pick("NỔI BẬT", "FEATURED")
    val utilitiesMultiThread: String get() = pick("Đa luồng", "Multi-thread")
    val utilitiesFbCareTitle: String get() = pick("Nuôi & tương tác Facebook", "Facebook Automation & Care")
    val utilitiesFbCareDesc: String get() = pick("Quy trình lướt feed, thả cảm xúc và xem story tự động theo kịch bản thông minh.", "Automated feed scrolling, reactions, and story viewing via smart scenarios.")
    val utilitiesStepFeed: String get() = pick("Lướt feed", "Browse Feed")
    val utilitiesStepReactions: String get() = pick("Cảm xúc", "Reactions")
    val utilitiesStepStory: String get() = pick("Xem story", "View Story")
    val utilitiesConfigureNow: String get() = pick("Cấu hình ngay", "Configure Now")
    val utilitiesOtherTools: String get() = pick("Công cụ khác", "Other Tools")
    val utilitiesToolPageRegTitle: String get() = pick("Reg & chuyển page", "Create & Transfer Pages")
    val utilitiesToolPageRegDesc: String get() = pick("Tạo trang và chuyển quyền quản trị", "Auto create pages and transfer admin")
    val utilitiesToolFbCareTitle: String get() = pick("Nuôi nick FB", "Facebook Account Care")
    val utilitiesToolFbCareDesc: String get() = pick("Lướt feed, kết bạn, tương tác nick chính", "Feed scroll, friend add, main account interaction")
    val utilitiesToolScenarioTitle: String get() = pick("Thiết lập kịch bản nuôi", "Nurturing Scenario Setup")
    val utilitiesToolScenarioDesc: String get() = pick("Tùy chỉnh delay, số lượng hành động", "Custom delay, action counts")

    // ---- Hồ sơ (ProfileScreen.kt) ----
    val profileMemberPro: String get() = pick("Thành viên PRO", "PRO Member")
    fun profileDaysLeft(days: Int, expiryDate: String): String = get("Còn $days ngày · Hết hạn $expiryDate", "$days days left · Expires $expiryDate")
    val profileCoinHistory: String get() = pick("Lịch sử xu", "Coin History")
    val profileSeeAll: String get() = pick("Xem tất cả", "See All")
    val profileNoCoinHistory: String get() = pick("Chưa có lịch sử nhận xu", "No coin history yet")
    val profileAccountSupport: String get() = pick("Tài khoản & hỗ trợ", "Account & Support")
    val profileSettings: String get() = pick("Cài đặt", "Settings")
    val profileLanguage: String get() = pick("Ngôn ngữ", "Language")
    val profileContactSupport: String get() = pick("Liên hệ hỗ trợ", "Contact Support")
    val profileTermsPolicies: String get() = pick("Điều khoản & chính sách", "Terms & Policies")
    val profileLogout: String get() = pick("Đăng xuất", "Log Out")
    val profileLanguageSheetTitle: String get() = pick("Ngôn ngữ", "Language")
    val profileLanguageSheetSubtitle: String get() = pick("Chọn ngôn ngữ hiển thị của ứng dụng", "Select the display language for the application")
    val profileContactSheetTitle: String get() = pick("Liên hệ hỗ trợ", "Contact Support")
    val profileContactSheetSubtitle: String get() = pick("Chọn kênh để nhắn trực tiếp cho chúng tôi", "Choose a channel to message us directly")
    val profileMsgTelegram: String get() = pick("Nhắn tin qua Telegram", "Message via Telegram")
    val profileMsgZalo: String get() = pick("Nhắn tin qua Zalo", "Message via Zalo")
    val profileLogoutConfirmTitle: String get() = pick("Đăng xuất", "Log Out")
    val profileLogoutConfirmMsg: String get() = pick("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?", "Are you sure you want to log out of your account?")
    val profileCancel: String get() = pick("Hủy", "Cancel")
    val profileTermsUpdating: String get() = pick("Điều khoản & chính sách đang được cập nhật", "Terms & Policies are currently being updated")

    // ---- Lịch sử xu (HistoryScreen.kt) ----
    val historyTitle: String get() = pick("Lịch sử xu", "Coin History")
    val historyEmptyDay: String get() = pick("Không có hoạt động nào trong ngày này", "No activity on this day")
    val historyPrevDay: String get() = pick("Ngày trước", "Previous day")
    val historyNextDay: String get() = pick("Ngày sau", "Next day")

    // ---- Cài đặt (SettingsScreen.kt) ----
    val settingsTitle: String get() = pick("Cài đặt", "Settings")
    val settingsBack: String get() = pick("Quay lại", "Back")
    val settingsSectionNotifications: String get() = pick("THÔNG BÁO", "NOTIFICATIONS")
    val settingsPushNotifications: String get() = pick("Thông báo đẩy", "Push Notifications")
    val settingsPushSubtitle: String get() = pick("Nhận thông báo khi có sự kiện mới", "Receive alerts when new events occur")
    val settingsBackgroundService: String get() = pick("Chạy ngầm", "Background Service")
    val settingsBackgroundSubtitle: String get() = pick("Tiếp tục chạy khi thoát ứng dụng", "Keep running when app is closed")
    val settingsPushEnabledToast: String get() = pick("Đã bật cảnh báo khi tài khoản lỗi", "Account error alerts enabled")
    val settingsPushDisabledToast: String get() = pick("Đã tắt cảnh báo khi tài khoản lỗi", "Account error alerts disabled")
    val settingsBackgroundEnabledToast: String get() = pick("Đã bật chạy ngầm - app tiếp tục chạy khi đóng", "Background service enabled - app continues running")
    val settingsBackgroundDisabledToast: String get() = pick("Đã tắt chạy ngầm", "Background service disabled")
    val settingsSectionAppearance: String get() = pick("GIAO DIỆN", "APPEARANCE")
    val settingsKeepScreenOn: String get() = pick("Giữ màn hình sáng", "Keep Screen On")
    val settingsTheme: String get() = pick("Giao diện", "Theme")
    val settingsThemeValue: String get() = pick("Sáng cao cấp (Swiss Light)", "Swiss Light (Clean White)")
    val settingsThemeToast: String get() = pick("Ứng dụng hoạt động ở chuẩn giao diện sáng tối giản", "The application runs in minimalist light theme")
    val settingsLanguage: String get() = pick("Ngôn ngữ", "Language")
    val settingsLanguageValue: String get() = pick("Tiếng Việt", "English")
    val settingsSectionSupport: String get() = pick("HỖ TRỢ & HỆ THỐNG", "SUPPORT & SYSTEM")
    val settingsSupportCenter: String get() = pick("Trung tâm hỗ trợ", "Support Center")
    val settingsTermsOfUse: String get() = pick("Điều khoản sử dụng", "Terms of Service")
    val settingsPrivacyPolicy: String get() = pick("Chính sách bảo mật", "Privacy Policy")
    val settingsAppVersion: String get() = pick("Phiên bản ứng dụng", "App Version")
    val settingsLogout: String get() = pick("Đăng xuất tài khoản", "Log Out Account")
    val settingsLogoutConfirmTitle: String get() = pick("Đăng xuất tài khoản", "Log Out Account")
    val settingsLogoutConfirmMsg: String get() = pick("Bạn có chắc muốn đăng xuất? Key đã lưu trên máy sẽ bị xoá, bạn cần nhập lại key ở lần mở app sau.", "Are you sure you want to log out? Saved key will be cleared, you will need to re-enter your key next time.")
    val settingsLogoutConfirmBtn: String get() = pick("Đăng xuất", "Log Out")
    val settingsCancel: String get() = pick("Huỷ", "Cancel")

    // ---- Màn Welcome ----
    val welcomeLanguageLabel: String get() = pick("Tiếng Việt", "English")
    val welcomeTitleLine1: String get() = pick("Kiếm tiền online", "Earn money online")
    val welcomeTitleLine2: String get() = pick("mọi lúc, mọi nơi", "anytime, anywhere")
    val welcomeSubtitle: String get() = pick(
        "AutoLunex giúp bạn kiếm tiền dễ dàng với nhiều nhiệm vụ hấp dẫn và thu nhập hấp dẫn.",
        "AutoLunex helps you earn money easily with exciting tasks and attractive income."
    )
    val welcomeGetStarted: String get() = pick("Bắt đầu ngay", "Get Started")
    val welcomeTermsPrefix: String get() = pick("Bằng việc tiếp tục, bạn đồng ý với ", "By continuing, you agree to our ")
    val welcomeTermsLink: String get() = pick("Điều khoản sử dụng", "Terms of Service")
    val welcomeTermsMiddle: String get() = pick(" và ", " and ")
    val welcomePrivacyLink: String get() = pick("Chính sách bảo mật", "Privacy Policy")
    val welcomeTermsSuffix: String get() = pick(" của chúng tôi.", ".")

    val termsDialogTitle: String get() = pick("Điều khoản sử dụng", "Terms of Service")
    val termsDialogBody: String get() = pick(
        "Bằng việc sử dụng AutoLunex, bạn đồng ý sử dụng ứng dụng đúng mục đích, không can thiệp, " +
            "chỉnh sửa hay khai thác trái phép hệ thống dưới bất kỳ hình thức nào.\n\n" +
            "AutoLunex có quyền tạm ngưng hoặc thu hồi quyền truy cập nếu phát hiện hành vi gian lận, " +
            "vi phạm điều khoản hoặc vi phạm pháp luật hiện hành.\n\n" +
            "Nội dung, nhiệm vụ và phần thưởng trong ứng dụng có thể được điều chỉnh theo thời gian " +
            "để đảm bảo trải nghiệm an toàn, minh bạch cho tất cả người dùng. Ứng dụng không chứa " +
            "và không hỗ trợ bất kỳ nội dung, chức năng nào trái quy định pháp luật.",
        "By using AutoLunex, you agree to use the app for its intended purpose only, and not to " +
            "interfere with, modify, or exploit the system in any unauthorized way.\n\n" +
            "AutoLunex reserves the right to suspend or revoke access if fraudulent activity, a " +
            "violation of these terms, or a violation of applicable law is detected.\n\n" +
            "Content, tasks, and rewards within the app may be adjusted over time to keep the " +
            "experience safe and transparent for all users. The app does not contain or support " +
            "any content or functionality that violates applicable regulations."
    )

    val privacyDialogTitle: String get() = pick("Chính sách bảo mật", "Privacy Policy")
    val privacyDialogBody: String get() = pick(
        "AutoLunex không thu thập, lưu trữ hay chia sẻ bất kỳ thông tin cá nhân nhạy cảm nào của " +
            "bạn cho bên thứ ba.\n\n" +
            "Ứng dụng chỉ sử dụng những dữ liệu tối thiểu, cần thiết để vận hành tính năng (như xác " +
            "thực key kích hoạt) và không truy cập mật khẩu hay dữ liệu riêng tư khác trên thiết bị " +
            "của bạn.\n\n" +
            "AutoLunex cam kết hoạt động minh bạch, tuân thủ quy định pháp luật hiện hành về bảo vệ " +
            "dữ liệu người dùng, không chứa và không thực hiện bất kỳ hành vi thu thập dữ liệu trái phép nào.",
        "AutoLunex does not collect, store, or share any of your sensitive personal information with " +
            "third parties.\n\n" +
            "The app only uses the minimum data necessary to operate its features (such as " +
            "activation key verification) and does not access your passwords or other private data " +
            "on your device.\n\n" +
            "AutoLunex is committed to operating transparently, in compliance with applicable data " +
            "protection regulations, and does not contain or engage in any unauthorized data " +
            "collection."
    )

    val dialogCloseButton: String get() = pick("Đã hiểu", "Got it")
}
