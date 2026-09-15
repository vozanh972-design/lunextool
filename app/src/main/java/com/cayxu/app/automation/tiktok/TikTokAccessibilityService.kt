package com.cayxu.app.automation.tiktok

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cayxu.app.automation.nurture.NurtureBridge
import com.cayxu.app.automation.nurture.NurtureState
import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Dịch vụ hỗ trợ - lo TOÀN BỘ việc tự động lấy tài khoản TikTok, KHÔNG cần người dùng
 * bấm tay: ngay khi TikTokCaptureBridge chuyển sang "Waiting" (do người dùng bấm
 * "Tiếp tục" trong tool), service tự dò liên tục (polling) cho tới khi:
 *   - Tìm thấy app TikTok/Lite/Studio đang ở foreground
 *   - Tự bấm tab "Tôi/Me/Profile" ở thanh dưới cùng, CHỈ bấm khi đã thật sự thấy tab đó
 *   - Tự tìm text "@handle" trên màn hình
 *   - Tự lưu vào TikTokCaptureBridge, tự đóng lớp nổi, tự đưa tool trở lại màn hình
 *
 * Dùng polling (dò lặp lại mỗi ~700ms, không có mốc thời gian cố định) thay vì chỉ dựa vào
 * onAccessibilityEvent, vì trên nhiều máy/emulator sự kiện đổi nội dung màn hình khi chuyển
 * tab không bắn đủ để bắt kịp - polling đảm bảo vẫn tự chạy được dù event có tới hay không.
 */
class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        // "Hồ sơ" là nhãn tab TikTok bản mới hay dùng; vẫn giữ "tôi"/"me"/"profile" để không hỏng
        // các bản TikTok cũ hơn dùng nhãn khác.
        private val PROFILE_TAB_LABELS = setOf("hồ sơ", "tôi", "me", "profile")
        private const val POLL_INTERVAL_MS = 700L
        // Không có mốc cố định vì máy nhanh/chậm khác nhau - cho dò tới ~5 phút rồi mới
        // báo lỗi (chỉ để tránh treo dịch vụ mãi mãi, không phải để giới hạn thời gian chờ
        // TikTok load thật sự).
        private const val MAX_POLL_ATTEMPTS = 420 // ~5 phút

        // Mục "Cài đặt và quyền riêng tư" trong menu (☰) mở ra từ trang Hồ sơ.
        private val SETTINGS_PRIVACY_LABELS = setOf("cài đặt và quyền riêng tư", "settings and privacy", "settings")

        // Dòng "Chuyển đổi tài khoản" - xuất hiện Ở CẢ 2 nơi: (1) là 1 DÒNG trong màn Cài đặt
        // (chỉ để bấm vào), và (2) là TIÊU ĐỀ của sheet hiện ra sau khi bấm. Chỉ dựa vào text
        // này KHÔNG đủ để biết sheet đã mở hay chưa (đây chính là lý do trước đây tool quét
        // nhầm cả màn Cài đặt, lưu luôn "Giải phóng dung lượng" làm tài khoản) - phải kết hợp
        // thêm ADD_ACCOUNT_LABELS bên dưới, vì "Thêm tài khoản" CHỈ xuất hiện trong sheet.
        private val SWITCH_SHEET_TITLE = setOf("chuyển đổi tài khoản", "switch account", "switch accounts")
        private val ADD_ACCOUNT_LABELS = setOf("thêm tài khoản", "add account")
        // Nhãn không phải là 1 dòng tài khoản trong sheet - loại các dòng này ra khi quét.
        // Danh sách được mở rộng thêm các mục của menu ☰ và màn Cài đặt (Số dư, Trung tâm
        // hoạt động, Giải phóng dung lượng...) làm lưới an toàn thứ 2, phòng khi vì lý do gì
        // đó việc quét vẫn lỡ chạy nhầm màn khác - dù về logic giờ chỉ quét khi đã xác nhận
        // đúng sheet.
        private val SWITCH_SHEET_IGNORE_LABELS = setOf(
            "chuyển đổi tài khoản", "switch account", "switch accounts",
            "thêm tài khoản", "add account", "quản lý tài khoản", "manage accounts",
            "số dư", "trung tâm hoạt động", "video ngoại tuyến", "mã qr của bạn", "nhạc của bạn",
            "tiktok studio", "tiktok shop cho nhà sáng tạo", "quảng bá",
            "cài đặt và quyền riêng tư", "bộ nhớ đệm", "giải phóng dung lượng",
            "trình tiết kiệm dữ liệu", "hình nền", "trung tâm trợ giúp", "trung tâm quyền riêng tư",
            "điều khoản và chính sách", "đăng xuất", "đăng nhập",
            "đóng", "close", "huỷ", "hủy", "cancel", "quay lại", "back", "ok", "đồng ý"
        )
        // Gợi ý nhận diện icon menu (☰) ở đầu trang Hồ sơ, khi không có contentDescription rõ ràng.
        private val MENU_ICON_HINTS = listOf("menu", "more", "tùy chọn", "cài đặt")

        // Nhãn cho tính năng "Nuôi tài khoản" - chỉ chạy ĐÚNG hành động nào cấu hình đã bật.
        private val COMMENT_LABELS = setOf("bình luận", "comment", "comments")
        private val SHARE_LABELS = setOf("chia sẻ", "share")
        private val COPY_LINK_LABELS = setOf("sao chép liên kết", "copy link")
        private val REPOST_LABELS = setOf("đăng lại", "repost")
    }

    // QUAN TRỌNG: dùng SupervisorJob thay vì Job thường. Nếu không, một lỗi bất ngờ (crash)
    // ở NHÁNH NÀY (vd luồng check tài khoản gặp lỗi khi dò node lạ) sẽ làm HUỶ LUÔN toàn bộ
    // scope, kéo theo nhánh "Nuôi tài khoản" cũng bị dừng ngầm dù không liên quan gì - đây
    // chính là lý do trước đó có lúc nuôi tài khoản không tự lướt video / không chạy hành
    // động nào dù cấu hình đã bật đúng: một lỗi ở phiên check acc trước đó đã "giết" luôn
    // coroutine đang theo dõi NurtureBridge, tới khi service khởi động lại (mở lại app) mới
    // sống lại được. SupervisorJob đảm bảo lỗi ở nhánh này KHÔNG lan sang nhánh khác.
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollingJob: Job? = null
    private var nurtureJob: Job? = null
    private var xsmmTaskJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Theo dõi bridge: hễ chuyển sang "Waiting" là tự bắt đầu dò; chuyển sang trạng
        // thái khác thì dừng dò lại.
        scope.launch {
            TikTokCaptureBridge.state.collect { state ->
                if (state is TikTokCaptureState.Waiting) {
                    startPolling(state.variant)
                } else {
                    pollingJob?.cancel()
                }
            }
        }
        // Theo dõi riêng phiên "Nuôi tài khoản" - tự lướt video/xem bình luận/sao chép liên
        // kết/đăng lại ĐÚNG những gì cấu hình đã bật, tới khi hết giờ hoặc bị Dừng.
        scope.launch {
            NurtureBridge.state.collect { state ->
                if (state is NurtureState.Running) {
                    startNurtureLoop(state)
                } else {
                    nurtureJob?.cancel()
                }
            }
        }
        // Theo dõi nhiệm vụ chạy tự động XSMM (bấm Follow, like, quay về Home lướt tin, kiểm tra đúng tài khoản)
        scope.launch {
            XsmmTaskAutomationBridge.action.collect { action ->
                when (action) {
                    is XsmmTaskAction.VerifyAndSwitchAccount -> {
                        startXsmmVerifyAccountExecution(action)
                    }
                    is XsmmTaskAction.DoTask -> {
                        startXsmmTaskExecution(action)
                    }
                    else -> {
                        xsmmTaskJob?.cancel()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        nurtureJob?.cancel()
        xsmmTaskJob?.cancel()
    }

    // Không cần xử lý gì ở đây - toàn bộ logic tự động nằm ở vòng lặp polling để không phụ
    // thuộc việc event có bắn đúng lúc chuyển tab hay không.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /**
     * Vòng lặp "Nuôi tài khoản" - CHỈ chạy đúng hành động nào cấu hình đã bật:
     *   - autoWatch: lướt (vuốt) sang video kế tiếp sau khi "xem" một lúc.
     *   - viewComments: thi thoảng mở bình luận đọc thử rồi đóng lại.
     *   - copyLink: thi thoảng mở chia sẻ rồi bấm "Sao chép liên kết".
     *   - repost: thi thoảng bấm "Đăng lại".
     * Dừng khi hết mốc thời gian (endAtMillis) hoặc khi NurtureBridge chuyển về Idle (bấm "Dừng").
     */
    private fun startNurtureLoop(state: NurtureState.Running) {
        nurtureJob?.cancel()
        nurtureJob = scope.launch {
            val pkg = TikTokAppLauncher.packageNameOf(state.variant)
            var cycle = 0
            while (
                System.currentTimeMillis() < state.endAtMillis &&
                NurtureBridge.state.value is NurtureState.Running
            ) {
                val root = findRootForPackage(pkg)
                if (root == null) {
                    delay(1000)
                    continue
                }
                cycle++

                // Bọc try-catch quanh MỖI vòng: lỗi lẻ tẻ (node lạ, view đổi cấu trúc...) chỉ
                // bỏ qua vòng đó, KHÔNG được phép làm chết cả phiên nuôi đang chạy dở.
                try {
                    if (state.viewComments && cycle % 3 == 0) {
                        val commentNode = findNodeByText(root, COMMENT_LABELS, exact = false)
                        if (commentNode != null) {
                            clickNode(commentNode)
                            delay(Random.nextLong(2500L, 4500L))
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            delay(700)
                        }
                    }

                    if (state.copyLink && cycle % 5 == 0) {
                        val shareRoot = findRootForPackage(pkg)
                        val shareNode = shareRoot?.let { findNodeByText(it, SHARE_LABELS, exact = false) }
                        if (shareNode != null) {
                            clickNode(shareNode)
                            delay(900)
                            val sheetRoot = findRootForPackage(pkg)
                            val copyNode = sheetRoot?.let { findNodeByText(it, COPY_LINK_LABELS, exact = false) }
                            if (copyNode != null) {
                                clickNode(copyNode)
                                delay(500)
                            }
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            delay(700)
                        }
                    }

                    if (state.repost && cycle % 6 == 0) {
                        val repostRoot = findRootForPackage(pkg)
                        val repostNode = repostRoot?.let { findNodeByText(it, REPOST_LABELS, exact = false) }
                        if (repostNode != null) {
                            clickNode(repostNode)
                            delay(1200)
                        }
                    }

                    if (state.autoWatch) {
                        // "Xem" một lúc như người thật rồi mới lướt tiếp, không lướt liên tục.
                        delay(Random.nextLong(4000L, 9000L))
                        swipeUpNextVideo()
                        delay(600)
                    } else {
                        delay(1500)
                    }
                } catch (e: Exception) {
                    delay(1000)
                }
            }
            NurtureBridge.stop()
        }
    }

    /** Vuốt lên để lướt sang video kế tiếp - gesture chuẩn của TikTok, không phụ thuộc tìm nút. */
    private fun swipeUpNextVideo() {
        val root = rootInActiveWindow ?: return
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        val startX = (bounds.left + bounds.right) / 2f
        val startY = bounds.top + bounds.height() * 0.75f
        val endY = bounds.top + bounds.height() * 0.25f
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 260))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun startPolling(variant: TikTokAppVariant) {
        if (variant == TikTokAppVariant.STANDARD) {
            startPollingSwitchAccountList(variant)
        } else {
            startPollingSingleHandle(variant)
        }
    }

    private fun startPollingSingleHandle(variant: TikTokAppVariant) {
        pollingJob?.cancel()
        var hasTappedProfileTab = false
        pollingJob = scope.launch {
            var attempt = 0
            // Không có mốc thời gian cố định vì máy nào cũng khởi động TikTok nhanh/chậm
            // khác nhau - cứ dò tới khi thấy tab "Tôi" ở THANH DƯỚI CÙNG của app TikTok thật
            // rồi mới bấm, không đoán/bấm bừa lúc app còn chưa load xong.
            while (attempt < MAX_POLL_ATTEMPTS) {
                attempt++
                val expectedPkg = TikTokAppLauncher.packageNameOf(variant)
                val root = findRootForPackage(expectedPkg)

                if (root == null) {
                    TikTokCaptureBridge.updateProgress("Đang đợi TikTok tải xong...")
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val handleNode = findHandleNode(root)
                val handleText = handleNode?.text?.toString()?.trim().orEmpty()
                if (handleNode != null && handleText.length > 1) {
                    TikTokCaptureBridge.updateProgress("Đã thấy @, đang lưu...")
                    val displayName = findDisplayNameNear(handleNode)
                    TikTokCaptureBridge.onCaptured(
                        handle = handleText,
                        displayName = displayName,
                        avatarUrl = "",
                        variant = variant
                    )
                    // Gọi THẲNG tại đây, không đợi lớp nổi/màn tool "nghe" lại state - tránh
                    // trường hợp màn tool reset state gần như ngay lập tức khiến lớp nổi lỡ
                    // mất thời điểm Captured và không tự đóng/không tự đưa tool lên được.
                    stopService(Intent(applicationContext, TikTokCaptureOverlayService::class.java))
                    TikTokAppLauncher.bringToolToFront(applicationContext)
                    return@launch
                }

                if (!hasTappedProfileTab) {
                    val tabNode = findProfileTabNode(root)
                    if (tabNode != null) {
                        // Chỉ thấy tab "Tôi" (thanh dưới cùng) MỚI bấm, không bấm khi chưa thấy.
                        TikTokCaptureBridge.updateProgress("Đã thấy tab \"Tôi\", đang bấm...")
                        clickNode(tabNode)
                        hasTappedProfileTab = true
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang tìm tab \"Tôi\" ở thanh dưới cùng...")
                    }
                } else {
                    TikTokCaptureBridge.updateProgress("Đang chờ trang \"Tôi\" hiện @...")
                }

                delay(POLL_INTERVAL_MS)
            }
            // Dò rất lâu (vài phút) mà vẫn chưa xong mới báo lỗi.
            if (TikTokCaptureBridge.state.value is TikTokCaptureState.Waiting) {
                TikTokCaptureBridge.onFailed("Không tự tìm thấy @ sau nhiều lần thử, hãy mở lại và thử lại")
            }
        }
    }

    /**
     * RIÊNG cho TikTok bản chuẩn: luồng THẬT trên TikTok hiện tại (không có mũi tên cạnh tên
     * ở trang Hồ sơ như bản cũ) là:
     *   1) Bấm tab "Hồ sơ" ở thanh dưới cùng, chờ tới khi thấy @handle (đã vào đúng trang).
     *   2) Bấm icon menu (☰) ở góc trên bên phải trang Hồ sơ.
     *   3) Trong menu vừa mở, bấm "Cài đặt và quyền riêng tư".
     *   4) Ở màn Cài đặt, cuộn xuống tới cuối (mục "Đăng nhập") để thấy dòng "Chuyển đổi tài khoản".
     *   5) Bấm dòng đó để mở sheet "Chuyển đổi tài khoản" THẬT (có avatar + tên từng acc +
     *      nút "Thêm tài khoản") - CHỈ khi chắc chắn đây là sheet (không phải dòng text cùng
     *      tên trong màn Cài đặt) mới quét và lưu, tránh lưu nhầm các mục cài đặt khác
     *      ("Giải phóng dung lượng", "Trình Tiết Kiệm Dữ liệu"...) làm tài khoản.
     */
    private fun startPollingSwitchAccountList(variant: TikTokAppVariant) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var attempt = 0
            while (attempt < MAX_POLL_ATTEMPTS) {
                attempt++
                try {
                    val expectedPkg = TikTokAppLauncher.packageNameOf(variant)
                    val root = findRootForPackage(expectedPkg)

                    if (root == null) {
                        TikTokCaptureBridge.updateProgress("Đang đợi TikTok tải xong...")
                        delay(POLL_INTERVAL_MS)
                        continue
                    }

                    // 1. KIỂM TRA MÀN HÌNH DANH SÁCH TÀI KHOẢN (Sheet Chuyển đổi tài khoản)
                    val addAccountNode = findNodeByText(root, ADD_ACCOUNT_LABELS, exact = false)
                    val sheetTitleNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                    if (addAccountNode != null || (sheetTitleNode != null && isSheetVisible(root))) {
                        TikTokCaptureBridge.updateProgress("Đã mở danh sách tài khoản, đang quét...")
                        val entries = collectSwitchAccountEntries(root)
                        if (entries.isNotEmpty()) {
                            TikTokCaptureBridge.updateProgress("Đã quét ${entries.size} tài khoản, đang lưu...")
                            TikTokCaptureBridge.onCapturedBatch(entries, variant)
                            stopService(Intent(applicationContext, TikTokCaptureOverlayService::class.java))
                            TikTokAppLauncher.bringToolToFront(applicationContext)
                            return@launch
                        }
                        delay(2500)
                        continue
                    }

                    // 2. KIỂM TRA MENU SIDEBAR / BOTTOM SHEET 3 GẠCH (Đang có mục "Cài đặt và quyền riêng tư")
                    val settingsRowNode = findNodeByText(root, SETTINGS_PRIVACY_LABELS, exact = false)
                    val isMenuDrawer = settingsRowNode != null && 
                        findNodeByText(root, setOf("tiktok studio", "quảng bá", "mã qr của bạn", "nhạc của bạn", "tài nguyên"), exact = false) != null
                    
                    if (isMenuDrawer || settingsRowNode != null && findNodeByText(root, setOf("đăng xuất", "bộ nhớ đệm"), exact = false) == null) {
                        TikTokCaptureBridge.updateProgress("Đã thấy \"Cài đặt và quyền riêng tư\", đang bấm...")
                        if (settingsRowNode != null) {
                            clickNode(settingsRowNode)
                        }
                        delay(6500)
                        continue
                    }

                    // 3. KIỂM TRA MÀN HÌNH "CÀI ĐẶT VÀ QUYỀN RIÊNG TƯ" (Đã bấm vào trong màn cài đặt)
                    val isSettingsScreen = findNodeByText(root, setOf("bộ nhớ đệm", "trung tâm trợ giúp", "điều khoản và chính sách", "đăng xuất", "tài khoản", "nội dung & hiển thị"), exact = false) != null
                    if (isSettingsScreen) {
                        val switchRowNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                        if (switchRowNode != null) {
                            TikTokCaptureBridge.updateProgress("Đã thấy \"Chuyển đổi tài khoản\", đang bấm...")
                            clickNode(switchRowNode)
                            delay(6500)
                        } else {
                            TikTokCaptureBridge.updateProgress("Đang cuộn xuống tìm \"Chuyển đổi tài khoản\"...")
                            scrollDown(root)
                            delay(5000)
                        }
                        continue
                    }

                    // 4. KIỂM TRA ĐANG Ở TRANG HỒ SƠ CHÍNH CHỦ (Có Sửa hồ sơ, Chia sẻ hồ sơ, Menu ☰)
                    val isProfileScreen = isUserSelfProfileScreen(root)
                    if (isProfileScreen) {
                        val menuNode = findMenuIcon(root)
                        if (menuNode != null) {
                            TikTokCaptureBridge.updateProgress("Đã vào Hồ sơ, đang mở menu (☰)...")
                            clickNode(menuNode)
                            delay(6500)
                        } else {
                            TikTokCaptureBridge.updateProgress("Đang chờ trang Hồ sơ tải xong...")
                            delay(3000)
                        }
                        continue
                    }

                    // 5. NẾU ĐANG BỊ MỞ TRANG NGƯỜI DÙNG KHÁC (Có nút Follow đỏ, nút Nhắn tin hoặc nút mũi tên Quay lại ở góc trên)
                    val isOtherUserProfile = findNodeByText(root, setOf("nhắn tin", "tin nhắn", "message", "đã follow", "following"), exact = false) != null &&
                                             findNodeByText(root, setOf("sửa hồ sơ", "chỉnh sửa hồ sơ", "edit profile"), exact = false) == null
                    if (isOtherUserProfile) {
                        val tabNode = findProfileTabNode(root, root)
                        if (tabNode != null) {
                            TikTokCaptureBridge.updateProgress("Đang bấm tab \"Hồ sơ\" ở dưới cùng...")
                            clickNode(tabNode)
                            delay(6500)
                            continue
                        } else {
                            TikTokCaptureBridge.updateProgress("Đang thoát trang người dùng khác về trang chính...")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            delay(5000)
                            continue
                        }
                    }

                    // 6. ĐANG Ở TRANG CHỦ / BẠN BÈ / VIDEO / FEED -> Bấm tab "Hồ sơ" ở thanh dưới cùng
                    val tabNode = findProfileTabNode(root, root)
                    if (tabNode != null) {
                        TikTokCaptureBridge.updateProgress("Đang mở trang Hồ sơ...")
                        clickNode(tabNode)
                        delay(6500)
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang đợi TikTok sẵn sàng...")
                        delay(2500)
                    }
                } catch (e: Exception) {
                    delay(POLL_INTERVAL_MS)
                }
            }
            if (TikTokCaptureBridge.state.value is TikTokCaptureState.Waiting) {
                TikTokCaptureBridge.onFailed("Không tự mở được danh sách tài khoản sau nhiều lần thử, hãy thử lại")
            }
        }
    }

    private fun isSheetVisible(root: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        return findNodeByText(root, ADD_ACCOUNT_LABELS, exact = false) != null
    }

    /**
     * Tìm chính xác icon menu (☰) ở góc trên bên phải trang Hồ sơ
     */
    private fun findMenuIcon(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)
        if (rootBounds.height() <= 0 || rootBounds.width() <= 0) return null

        // Icon 3 gạch nằm ở 15% phía trên cùng và 25% phía bên phải màn hình
        val headerBottomLimit = rootBounds.top + (rootBounds.height() * 0.15f).toInt()
        val rightSideLimit = rootBounds.left + (rootBounds.width() * 0.70f).toInt()

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectTopRightClickableNodes(root, headerBottomLimit, rightSideLimit, candidates)
        if (candidates.isEmpty()) return null

        // Sắp xếp ưu tiên icon nằm ngoài cùng bên phải nhất
        candidates.sortByDescending { node ->
            val b = Rect()
            node.getBoundsInScreen(b)
            b.right
        }
        return candidates.firstOrNull()
    }

    private fun collectTopRightClickableNodes(
        node: AccessibilityNodeInfo,
        topLimit: Int,
        rightLimit: Int,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int = 0
    ) {
        if (depth > 40) return
        if (node.isClickable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.top >= 0 && bounds.bottom <= topLimit && bounds.right >= rightLimit) {
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                // Bỏ qua các icon chia sẻ, bookmark, lịch nếu có
                if (!desc.contains("share") && !desc.contains("chia sẻ") && !desc.contains("lịch")) {
                    out.add(node)
                    return
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTopRightClickableNodes(child, topLimit, rightLimit, out, depth + 1)
        }
    }

    /** Quét cây tìm 1 node clickable trong vùng đầu màn hình có contentDescription gợi ý menu. */
    private fun findClickableIconInRegion(
        node: AccessibilityNodeInfo,
        headerBottomLimit: Int,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 40) return null
        if (node.isClickable) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.bottom in 1..headerBottomLimit) {
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                if (MENU_ICON_HINTS.any { desc.contains(it) }) return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableIconInRegion(child, headerBottomLimit, depth + 1)
            if (found != null) return found
        }
        return null
    }

    /**
     * Gom các node clickable nằm trong vùng đầu màn hình, có kích thước NHỎ kiểu icon (không
     * quá [maxWidth] x [maxHeight]). KHÔNG còn lọc theo "không có text" như trước - icon dạng
     * font chữ (icon font, rất phổ biến trong app TikTok) vẫn có 1 ký tự riêng làm text, lọc
     * theo text trống sẽ loại nhầm icon thật ra khỏi danh sách candidate. Dừng đệ quy ngay khi
     * gặp 1 node clickable hợp lệ (lấy đúng vùng bấm ngoài cùng, không lặn sâu vào con của nó).
     */
    private fun collectHeaderIconCandidates(
        node: AccessibilityNodeInfo,
        headerBottomLimit: Int,
        maxWidth: Int,
        maxHeight: Int,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int = 0
    ) {
        if (depth > 40) return
        if (node.isClickable) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val w = bounds.width()
            val h = bounds.height()
            if (bounds.bottom in 1..headerBottomLimit && w in 1..maxWidth && h in 1..maxHeight) {
                out.add(node)
                return
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectHeaderIconCandidates(child, headerBottomLimit, maxWidth, maxHeight, out, depth + 1)
        }
    }

    /** Tìm node có thể cuộn (scrollable) rồi cuộn xuống 1 nấc; kết hợp gesture vuốt từ dưới lên trên để cuộn trang Cài đặt */
    private fun scrollDown(root: AccessibilityNodeInfo) {
        val scrollable = findScrollableNode(root)
        @Suppress("DEPRECATION")
        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        swipeUpSettings()
    }

    /** Vuốt từ dưới lên trên (y từ 80% lên 20%) để cuộn nội dung Cài đặt xuống cuối */
    private fun swipeUpSettings() {
        val root = rootInActiveWindow ?: return
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        val centerX = (bounds.left + bounds.right) / 2f
        val startY = bounds.top + bounds.height() * 0.82f // Điểm bắt đầu ở phía dưới màn hình
        val endY = bounds.top + bounds.height() * 0.22f   // Vuốt kéo lên phía trên màn hình
        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 320))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 40) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    /**
     * Quét toàn bộ dòng tên tài khoản trong sheet "Chuyển đổi tài khoản" đang mở. Mỗi dòng
     * trong sheet thường là 1 node clickable chứa avatar + tên; lấy text ngắn gọn nhất (không
     * rỗng) trong mỗi dòng làm tên hiển thị, bỏ qua tiêu đề sheet và nút "Thêm tài khoản".
     */
    private fun collectSwitchAccountEntries(root: AccessibilityNodeInfo): List<CapturedAccountEntry> {
        val rows = mutableListOf<AccessibilityNodeInfo>()
        findClickableRowsWithText(root, rows)

        val seen = LinkedHashSet<String>()
        val entries = mutableListOf<CapturedAccountEntry>()
        for (row in rows) {
            val label = firstMeaningfulText(row) ?: continue
            val normalized = label.trim()
            val lower = normalized.lowercase()
            if (normalized.isBlank()) continue
            if (SWITCH_SHEET_IGNORE_LABELS.any { lower == it || lower.contains(it) }) continue
            if (!seen.add(normalized)) continue
            val isActive = row.isSelected ||
                (row.contentDescription?.toString()?.lowercase()?.contains("đang chọn") == true)
            entries.add(CapturedAccountEntry(displayName = normalized, isActive = isActive))
        }
        return entries
    }

    /** Tìm các node clickable mà bên trong có chứa chữ (ứng viên cho 1 "dòng" tài khoản). */
    private fun findClickableRowsWithText(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int = 0
    ) {
        if (depth > 40) return
        if (node.isClickable && firstMeaningfulText(node) != null) {
            out.add(node)
            // Không cần lặn sâu hơn vào bên trong 1 dòng đã nhận diện, tránh trùng lặp.
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableRowsWithText(child, out, depth + 1)
        }
    }

    /** Lấy đoạn text đầu tiên, không rỗng, tìm được trong cây con của node (kể cả contentDescription). */
    private fun firstMeaningfulText(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 20) return null
        val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()
        if (!text.isNullOrBlank()) return text
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = firstMeaningfulText(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    /** Ưu tiên cửa sổ đang active; nếu không đúng gói, dò qua windows() để tìm đúng gói TikTok. */
    private fun findRootForPackage(expectedPkg: String): AccessibilityNodeInfo? {
        val activeRoot = rootInActiveWindow
        if (activeRoot?.packageName?.toString() == expectedPkg) return activeRoot

        return try {
            windows.firstNotNullOfOrNull { w ->
                w.root?.takeIf { it.packageName?.toString() == expectedPkg }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Tìm node có text bắt đầu bằng "@" (định danh công khai, không phải thông tin đăng nhập). */
    private fun findHandleNode(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 40) return null
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && text.trim().startsWith("@") && text.trim().length > 2) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findHandleNode(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    /** Best-effort: tên hiển thị thường là dòng text anh em gần nhất với node @handle. */
    private fun findDisplayNameNear(handleNode: AccessibilityNodeInfo): String {
        val parent = handleNode.parent ?: return ""
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            val text = sibling.text?.toString()?.trim()
            if (!text.isNullOrBlank() && !text.startsWith("@")) {
                return text
            }
        }
        return ""
    }

    /**
     * Nhiều app (kể cả TikTok Lite) đặt chữ "Tôi" vào TextView bên trong một nhóm tab
     * KHÔNG tự click được - node thật sự nhận click là node cha gần nhất có isClickable
     * = true. Bấm thẳng vào TextView không có tác dụng, nên phải leo lên tìm node cha
     * clickable rồi mới performAction ở đó.
     */
    /**
     * Bấm bằng CHẠM THẬT (gesture tap qua toạ độ trên màn hình) thay vì chỉ dựa vào
     * performAction(ACTION_CLICK). Lý do: nhiều nút icon (vd nút 3 gạch ☰) chỉ xử lý sự kiện
     * chạm thật (onTouch) chứ không có click-listener chuẩn mà accessibility framework nhận
     * ra được - bấm tay thì ăn nhưng gọi ACTION_CLICK qua node thì im re, đúng hiện tượng đã
     * gặp. Chạm thật mô phỏng đúng một cú chạm ngón tay nên chắc ăn hơn nhiều.
     */
    private fun clickNode(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            tapAt(bounds.exactCenterX(), bounds.exactCenterY())
        }
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && !target.isClickable && depth < 10) {
            target = target.parent
            depth++
        }
        (target ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Chạm thật tại 1 toạ độ màn hình - dùng gesture, mô phỏng đúng 1 cú chạm ngón tay. */
    private fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Nhận diện màn hình Hồ sơ chính chủ (Profile) của người dùng:
     * Phải có các đặc trưng như nút "Sửa hồ sơ" (Edit profile), "Chia sẻ hồ sơ", "Đơn hàng của bạn", "Thêm bạn bè"
     * VÀ KHÔNG PHẢI là video feed hoặc trang của người khác.
     */
    private fun isUserSelfProfileScreen(root: AccessibilityNodeInfo): Boolean {
        // Nút chỉnh sửa hồ sơ / chia sẻ hồ sơ / đơn hàng của bạn chỉ có ở trang cá nhân của chính mình
        val profileSelfMarkers = setOf(
            "sửa hồ sơ", "chỉnh sửa hồ sơ", "edit profile",
            "chia sẻ hồ sơ", "share profile",
            "đơn hàng của bạn", "your orders",
            "phần trưng bày", "showcase"
        )
        if (findNodeByText(root, profileSelfMarkers, exact = false) != null) {
            return true
        }
        // Hoặc có icon Menu (☰) ở góc trên bên phải kết hợp với nút Thêm bạn bè
        val hasMenu = findMenuIcon(root) != null
        val hasAddFriends = findNodeByText(root, setOf("thêm bạn bè", "add friends"), exact = false) != null
        return hasMenu && hasAddFriends
    }

    /**
     * Dò riêng cho tab "Hồ sơ/Tôi" ở thanh điều hướng DƯỚI CÙNG của màn hình TikTok (nằm cạnh Hộp thư).
     * Chỉ tìm ở vùng 15% phía dưới đáy màn hình (Bottom Navigation Bar, top >= 82% chiều cao)
     * và nằm ở góc bên phải (right >= 65% chiều rộng màn hình).
     * Tuyệt đối không bấm vào bất kỳ avatar người dùng / nút follow dấu + / story / live nào ở phía trên!
     */
    private fun findProfileTabNode(
        node: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo = node,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 40) return null
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)

        val rootH = rootBounds.height()
        val rootW = rootBounds.width()

        // Tab Hồ sơ luôn nằm ở thanh bar dưới đáy (top >= 82% chiều cao) và ở phía bên phải (right >= 65% chiều rộng)
        val isAtBottomNavigation = if (rootH > 0 && rootW > 0) {
            bounds.top >= (rootBounds.top + rootH * 0.82f) && bounds.right >= (rootBounds.left + rootW * 0.65f)
        } else {
            true
        }

        if (isAtBottomNavigation) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()?.lowercase()
            if (!text.isNullOrBlank()) {
                val match = PROFILE_TAB_LABELS.any { text == it || (it.length >= 4 && text.contains(it)) }
                if (match) return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findProfileTabNode(child, root, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        labels: Set<String>,
        exact: Boolean,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 40) return null
        val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()?.lowercase()
        if (text != null) {
            // Nhánh "không cần khớp chính xác" dùng contains, nhưng CHỈ cho nhãn đủ dài
            // (>=4 ký tự) để tránh khớp nhầm - vd nhãn "me" ngắn mà cho contains thì chữ
            // "Home" (chứa "me") sẽ bị khớp nhầm.
            val match = if (exact) {
                text in labels
            } else {
                labels.any { text == it || (it.length >= 4 && text.contains(it)) }
            }
            if (match) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, labels, exact, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun isTikTokPackage(pkg: String?): Boolean {
        if (pkg == null) return false
        val p = pkg.lowercase()
        return p.contains("trill") || p.contains("musically") || p.contains("tiktok") || p.contains("ss.android") || p.contains("zhiliaoapp")
    }

    private fun findTikTokRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && isTikTokPackage(active.packageName?.toString())) {
            return active
        }

        return try {
            windows.firstNotNullOfOrNull { w ->
                val r = w.root
                if (r != null && isTikTokPackage(r.packageName?.toString())) r else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findFollowButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectFollowCandidates(root, candidates)
        return candidates.firstOrNull()
    }

    private fun collectFollowCandidates(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (depth > 40) return
        val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()?.lowercase()
        if (!text.isNullOrBlank()) {
            if (text == "follow" || text == "theo dõi" || text == "follow lại" || text == "theo dõi lại") {
                out.add(node)
                return
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectFollowCandidates(child, out, depth + 1)
        }
    }

    private fun findHomeTabNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val labels = setOf("trang chủ", "home", "dành cho bạn", "for you")
        return findNodeByText(root, labels, exact = false)
    }

    private fun doubleTapCenter() {
        val root = findTikTokRoot() ?: return
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        tapAt(cx, cy)
        scope.launch {
            delay(120)
            tapAt(cx, cy)
        }
    }

    private fun startXsmmTaskExecution(action: XsmmTaskAction.DoTask) {
        xsmmTaskJob?.cancel()
        xsmmTaskJob = scope.launch {
            try {
                if (action.swipeBefore) {
                    XsmmTaskAutomationBridge.updateProgress("Đang lướt trước khi làm...")
                    delay(1500)
                    swipeUpNextVideo()
                    delay(1500)
                }

                if (action.taskType.contains("follow", ignoreCase = true)) {
                    XsmmTaskAutomationBridge.updateProgress("Đang mở trang cá nhân và tìm nút Follow...")
                    var followed = false
                    val maxTries = 15 // ~10 seconds
                    for (i in 0 until maxTries) {
                        val root = findTikTokRoot()
                        if (root != null) {
                            // Check if already followed
                            val alreadyNode = findNodeByText(
                                root,
                                setOf("đang follow", "đang theo dõi", "following", "bạn bè", "friends"),
                                exact = false
                            )
                            if (alreadyNode != null && !alreadyNode.text.toString().equals("follow", ignoreCase = true)) {
                                XsmmTaskAutomationBridge.updateProgress("Tài khoản đã được follow từ trước")
                                followed = true
                                delay(1000)
                                break
                            }

                            // Find follow button
                            val followNode = findFollowButtonNode(root)
                            if (followNode != null) {
                                XsmmTaskAutomationBridge.updateProgress("Đã thấy nút Follow màu đỏ, đang bấm...")
                                clickNode(followNode)
                                delay(1200)
                                followed = true
                                break
                            }
                        }
                        delay(700)
                    }
                    if (!followed) {
                        XsmmTaskAutomationBridge.updateProgress("Đã qua bước kiểm tra Follow")
                    }
                } else if (action.taskType.contains("like", ignoreCase = true)) {
                    XsmmTaskAutomationBridge.updateProgress("Đang thả tim video...")
                    delay(1500)
                    doubleTapCenter()
                    delay(1200)
                }

                if (action.returnHomeAndSwipe) {
                    XsmmTaskAutomationBridge.updateProgress("Bấm Follow xong -> Đang quay về Home...")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(800)
                    val root = findTikTokRoot()
                    val homeTab = root?.let { findHomeTabNode(it) }
                    if (homeTab != null) {
                        clickNode(homeTab)
                        delay(1000)
                    }

                    val duration = action.durationSeconds.coerceAtLeast(3)
                    val startTime = System.currentTimeMillis()
                    val endTime = startTime + duration * 1000L

                    while (System.currentTimeMillis() < endTime && scope.isActive) {
                        val remainingSec = ((endTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(1)
                        XsmmTaskAutomationBridge.updateProgress("Đang lướt tin TikTok... còn ${remainingSec}s")
                        delay(Random.nextLong(2500L, 4000L))
                        swipeUpNextVideo()
                    }
                } else {
                    val duration = action.durationSeconds.coerceAtLeast(1)
                    for (s in duration downTo 1) {
                        XsmmTaskAutomationBridge.updateProgress("Đang làm nhiệm vụ... còn ${s}s")
                        delay(1000L)
                    }
                }

                XsmmTaskAutomationBridge.completeTask(action.actionId, true, "Đã hoàn thành thao tác")
            } catch (e: Exception) {
                XsmmTaskAutomationBridge.completeTask(action.actionId, false, e.message ?: "Lỗi tự động hóa")
            }
        }
    }

    private fun startXsmmVerifyAccountExecution(action: XsmmTaskAction.VerifyAndSwitchAccount) {
        xsmmTaskJob?.cancel()
        var menuTapAttempts = 0
        val target = action.targetHandle.trim().removePrefix("@").lowercase()

        xsmmTaskJob = scope.launch {
            var attempt = 0
            while (attempt < MAX_POLL_ATTEMPTS && isActive) {
                attempt++
                try {
                    val root = findTikTokRoot()
                    if (root == null) {
                        XsmmTaskAutomationBridge.updateProgress("Đang đợi TikTok tải xong...")
                        delay(POLL_INTERVAL_MS)
                        continue
                    }

                    // 1. Kiểm tra: Đang mở Sheet "Chuyển đổi tài khoản" (có tiêu đề sheet + nút Thêm tài khoản / danh sách nick)
                    val sheetTitleNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                    val addAccountNode = findNodeByText(root, ADD_ACCOUNT_LABELS, exact = false)
                    if (sheetTitleNode != null && addAccountNode != null) {
                        XsmmTaskAutomationBridge.updateProgress("Đang tìm @$target trong danh sách tài khoản...")
                        val rows = mutableListOf<AccessibilityNodeInfo>()
                        findClickableRowsWithText(root, rows)
                        var foundTargetRow: AccessibilityNodeInfo? = null
                        for (row in rows) {
                            val label = firstMeaningfulText(row)?.trim()?.lowercase() ?: continue
                            if (label.contains(target) || target.contains(label)) {
                                foundTargetRow = row
                                break
                            }
                        }
                        if (foundTargetRow != null) {
                            XsmmTaskAutomationBridge.updateProgress("Đã thấy @$target, đang bấm chuyển...")
                            clickNode(foundTargetRow)
                            delay(2500)
                            XsmmTaskAutomationBridge.completeTask(action.actionId, true, "Đã chuyển sang tài khoản @$target")
                            return@launch
                        } else {
                            XsmmTaskAutomationBridge.updateProgress("Không thấy @$target trong danh sách")
                            delay(1500)
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            XsmmTaskAutomationBridge.completeTask(action.actionId, false, "Không tìm thấy @$target")
                            return@launch
                        }
                    }

                    // 2. Kiểm tra: Đang ở màn Cài đặt và quyền riêng tư (Settings)
                    val switchRowNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                    if (switchRowNode != null && addAccountNode == null) {
                        XsmmTaskAutomationBridge.updateProgress("Đã thấy \"Chuyển đổi tài khoản\", đang bấm...")
                        clickNode(switchRowNode)
                        delay(1200)
                        continue
                    }

                    val settingsTitle = findNodeByText(root, setOf("cài đặt và quyền riêng tư", "settings and privacy", "bảo mật", "quyền riêng tư"), exact = false)
                    if (settingsTitle != null && switchRowNode == null) {
                        XsmmTaskAutomationBridge.updateProgress("Đang cuộn xuống tìm \"Chuyển đổi tài khoản\"...")
                        scrollDown(root)
                        delay(POLL_INTERVAL_MS)
                        continue
                    }

                    // 3. Kiểm tra: Đang mở Menu (☰) popup / bottom sheet (có nút "Cài đặt và quyền riêng tư")
                    val settingsMenuNode = findNodeByText(root, SETTINGS_PRIVACY_LABELS, exact = false)
                    if (settingsMenuNode != null) {
                        XsmmTaskAutomationBridge.updateProgress("Đã thấy \"Cài đặt và quyền riêng tư\", đang bấm...")
                        clickNode(settingsMenuNode)
                        delay(1200)
                        continue
                    }

                    // 4. Kiểm tra: Đang ở trang Hồ sơ (Profile) - có @handle
                    val handleNode = findHandleNode(root)
                    if (handleNode != null) {
                        val currentHandle = handleNode.text?.toString()?.trim()?.removePrefix("@")?.lowercase().orEmpty()
                        if (currentHandle.isNotBlank()) {
                            if (currentHandle == target || currentHandle.contains(target) || target.contains(currentHandle)) {
                                XsmmTaskAutomationBridge.updateProgress("Đã khớp tài khoản @$target")
                                delay(800)
                                XsmmTaskAutomationBridge.completeTask(action.actionId, true, "Đúng tài khoản @$target")
                                return@launch
                            } else {
                                // Đang ở tài khoản khác -> Mở menu (☰)
                                XsmmTaskAutomationBridge.updateProgress("Đang ở @$currentHandle -> Mở menu chuyển sang @$target...")
                                val menuNode = findMenuIcon(root)
                                if (menuNode != null) {
                                    clickNode(menuNode)
                                    menuTapAttempts++
                                    delay(1000)
                                } else {
                                    XsmmTaskAutomationBridge.updateProgress("Đang tìm nút menu (☰)...")
                                }
                                delay(POLL_INTERVAL_MS)
                                continue
                            }
                        }
                    }

                    // 5. Nếu chưa ở trang Hồ sơ (đang ở Home/Trang chủ/Feed/Khám phá...)
                    val profileTab = findProfileTabNode(root)
                    if (profileTab != null) {
                        XsmmTaskAutomationBridge.updateProgress("Đã thấy tab \"Hồ sơ\", đang bấm...")
                        clickNode(profileTab)
                        delay(1000)
                    } else {
                        XsmmTaskAutomationBridge.updateProgress("Đang tìm tab \"Hồ sơ\" ở thanh dưới cùng...")
                        delay(POLL_INTERVAL_MS)
                    }
                } catch (e: Exception) {
                    delay(POLL_INTERVAL_MS)
                }
            }

            XsmmTaskAutomationBridge.completeTask(action.actionId, true, "Hoàn tất kiểm tra")
        }
    }
}
