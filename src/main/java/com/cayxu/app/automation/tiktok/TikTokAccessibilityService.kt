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
import kotlinx.coroutines.launch
import android.util.Log
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

        // Nhãn nút Follow - dùng so sánh TUYỆT ĐỐI (không contains) để không dính nhầm
        // "Đang theo dõi"/"Following" (trạng thái đã follow rồi, bấm vào sẽ là unfollow).
        private val FOLLOW_EXACT_LABELS = setOf("theo dõi", "follow")
        private const val FOLLOW_WAIT_BEFORE_MS = 5000L
        private const val FOLLOW_WAIT_AFTER_RELOAD_MS = 1800L
        private const val FOLLOW_MAX_ATTEMPTS = 15
        private const val FOLLOW_RELOAD_SWIPE_COUNT = 3
        private const val FOLLOW_SWIPE_INTERVAL_MS = 1000L
        private const val TAG = "GolikeFollow"
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
    private var followJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected - AccessibilityService đã kết nối, bắt đầu lắng nghe các bridge")
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
        // Theo dõi yêu cầu "vuốt xuống để tải lại" từ luồng Thêm acc vào GoLike (một số máy
        // bị lag/đứng hình sau khi mở link, không tự load được) - CHỈ 1 cử chỉ vuốt, không
        // phải tự động hoá tương tác gì khác.
        scope.launch {
            GolikeReloadBridge.requestTick.collect { tick ->
                if (tick > 0) {
                    performPullToRefresh()
                }
            }
        }
        // Theo dõi yêu cầu "tự bấm Follow" từ luồng Thêm acc vào GoLike: đợi 1 nhịp cho
        // TikTok tải xong, kéo xuống tải lại, rồi tìm và bấm nút Follow.
        scope.launch {
            GolikeFollowBridge.state.collect { state ->
                if (state is GolikeFollowState.Pending) {
                    Log.d(TAG, "Nhận yêu cầu follow: username=${state.targetUsername} pkg=${state.packageName} requestId=${state.requestId}")
                    startFollowFlow(state)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        nurtureJob?.cancel()
        followJob?.cancel()
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

    /** Kéo từ TRÊN màn hình xuống (đúng kiểu pull-to-refresh) - CHỈ để ép TikTok tải lại khi
     *  bị lag/đứng hình sau khi mở link (một số máy load chậm hơn máy khác), y hệt cử chỉ
     *  người dùng tự kéo tay từ mép trên xuống để refresh. Không phải Follow/Like/Comment
     *  hay bất kỳ thao tác tương tác nào. */
    private fun performPullToRefresh() {
        val root = rootInActiveWindow ?: return
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        val startX = (bounds.left + bounds.right) / 2f
        val startY = bounds.top + bounds.height() * 0.15f
        val endY = bounds.top + bounds.height() * 0.75f
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Luồng tự bấm Follow sau khi mở link trang cá nhân TikTok từ GoLike:
     *   1) Đợi ~5 giây cho TikTok kịp mở/tải xong.
     *   2) Vuốt từ trên xuống 3 LẦN (cách nhau ~1 giây) để ép tải lại - vuốt 1 lần đôi khi
     *      không ăn (bị TikTok bỏ qua do đang trong lúc load, hoặc do độ nhạy máy khác nhau).
     *   3) Đợi trang load lại rồi dò tìm nút "Theo dõi"/"Follow" và bấm.
     * Không tìm thấy sau nhiều lần thử thì bỏ qua (có thể đã follow rồi hoặc UI khác dự kiến),
     * không báo lỗi làm phiền người dùng.
     */
    private fun startFollowFlow(state: GolikeFollowState.Pending) {
        followJob?.cancel()
        followJob = scope.launch {
            try {
                Log.d(TAG, "Bắt đầu đợi ${FOLLOW_WAIT_BEFORE_MS}ms trước khi thao tác...")
                delay(FOLLOW_WAIT_BEFORE_MS)

                Log.d(TAG, "Bắt đầu vuốt xuống $FOLLOW_RELOAD_SWIPE_COUNT lần để reload...")
                repeat(FOLLOW_RELOAD_SWIPE_COUNT) { index ->
                    val rootBeforeSwipe = rootInActiveWindow
                    Log.d(TAG, "Vuốt lần ${index + 1}: rootInActiveWindow.packageName=${rootBeforeSwipe?.packageName}")
                    performPullToRefresh()
                    if (index < FOLLOW_RELOAD_SWIPE_COUNT - 1) {
                        delay(FOLLOW_SWIPE_INTERVAL_MS)
                    }
                }
                Log.d(TAG, "Đã vuốt xong, đợi ${FOLLOW_WAIT_AFTER_RELOAD_MS}ms cho trang load lại...")
                delay(FOLLOW_WAIT_AFTER_RELOAD_MS)

                var attempt = 0
                var clicked = false
                while (attempt < FOLLOW_MAX_ATTEMPTS) {
                    attempt++
                    val root = findRootForPackage(state.packageName)
                    if (root == null) {
                        Log.d(TAG, "Lần $attempt/$FOLLOW_MAX_ATTEMPTS: không tìm thấy root cho package ${state.packageName}")
                        delay(POLL_INTERVAL_MS)
                        continue
                    }
                    val followNode = findFollowButton(root)
                    if (followNode != null) {
                        Log.d(TAG, "Lần $attempt: TÌM THẤY nút Follow, đang bấm...")
                        clickNode(followNode)
                        clicked = true
                        break
                    } else {
                        Log.d(TAG, "Lần $attempt/$FOLLOW_MAX_ATTEMPTS: có root nhưng KHÔNG tìm thấy nút Follow trong cây UI")
                    }
                    delay(POLL_INTERVAL_MS)
                }
                if (!clicked) {
                    Log.d(TAG, "KẾT THÚC: không tìm thấy/bấm được nút Follow sau $FOLLOW_MAX_ATTEMPTS lần thử")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi trong startFollowFlow: ${e.message}", e)
            } finally {
                GolikeFollowBridge.clear()
            }
        }
    }

    /** Tìm nút Follow bằng so khớp TUYỆT ĐỐI text (không contains) để tránh bấm nhầm "Đang
     *  theo dõi". KHÔNG yêu cầu chính node này phải isClickable - trên TikTok, chữ "Follow"
     *  thường nằm trong 1 TextView con bên trong nút bấm (Button/ViewGroup cha mới thực sự
     *  clickable), nên nếu bắt buộc isClickable ngay trên node chứa text sẽ bị bỏ sót, không
     *  bao giờ tìm thấy. Việc bấm (tap theo toạ độ hoặc leo lên cha clickable) do clickNode()
     *  xử lý riêng ở bước gọi. */
    private fun findFollowButton(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 40) return null
        val text = (node.text?.toString() ?: node.contentDescription?.toString())
            ?.trim()?.lowercase()
        if (text != null && text in FOLLOW_EXACT_LABELS) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFollowButton(child, depth + 1)
            if (found != null) return found
        }
        return null
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
        var hasTappedProfileTab = false
        var hasTappedMenuIcon = false
        var menuTapAttempts = 0
        var hasTappedSettingsRow = false
        var hasTappedSwitchRow = false
        pollingJob = scope.launch {
            var attempt = 0
            while (attempt < MAX_POLL_ATTEMPTS) {
                attempt++
                // Bọc try-catch quanh CẢ vòng dò: lỗi lẻ tẻ (node lạ/màn hình đổi cấu trúc)
                // chỉ bỏ qua vòng đó rồi thử lại, KHÔNG được phép làm chết cả coroutine.
                try {
                val expectedPkg = TikTokAppLauncher.packageNameOf(variant)
                val root = findRootForPackage(expectedPkg)

                if (root == null) {
                    TikTokCaptureBridge.updateProgress("Đang đợi TikTok tải xong...")
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 5: đã bấm dòng "Chuyển đổi tài khoản" trong màn Cài đặt - chờ sheet
                // THẬT mở ra rồi mới quét. Chỉ coi là sheet thật khi có CẢ tiêu đề "Chuyển đổi
                // tài khoản" LẪN nút "Thêm tài khoản" (nút này CHỈ có trong sheet, không có ở
                // dòng cùng tên trong màn Cài đặt) - tránh lặp lại lỗi quét nhầm màn Cài đặt.
                // Không có giới hạn số lần thử ở đây - máy chậm/animation chậm thì cứ dò và
                // bấm lại tới khi thấy, không bỏ cuộc theo 1 mốc thời gian cứng nào.
                if (hasTappedSwitchRow) {
                    val sheetTitleNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                    val addAccountNode = findNodeByText(root, ADD_ACCOUNT_LABELS, exact = false)
                    if (sheetTitleNode != null && addAccountNode != null) {
                        TikTokCaptureBridge.updateProgress("Đã mở danh sách tài khoản, đang quét...")
                        val entries = collectSwitchAccountEntries(root)
                        if (entries.isNotEmpty()) {
                            TikTokCaptureBridge.updateProgress("Đã quét ${entries.size} tài khoản, đang lưu...")
                            TikTokCaptureBridge.onCapturedBatch(entries, variant)
                            stopService(Intent(applicationContext, TikTokCaptureOverlayService::class.java))
                            TikTokAppLauncher.bringToolToFront(applicationContext)
                            return@launch
                        }
                        TikTokCaptureBridge.updateProgress("Đang chờ danh sách tài khoản hiện ra...")
                    } else if (sheetTitleNode != null && addAccountNode == null) {
                        // Thấy chữ "Chuyển đổi tài khoản" nhưng KHÔNG có "Thêm tài khoản" đi
                        // kèm - đây vẫn là dòng trong màn Cài đặt, chưa phải sheet thật, chưa
                        // bấm gì cả, chỉ chờ tiếp (sheet có thể đang trong lúc hiện animation).
                        TikTokCaptureBridge.updateProgress("Đang mở danh sách tài khoản...")
                    } else {
                        // Chưa thấy gì cả - có thể bấm bị trượt, thử tìm lại đúng dòng và bấm lại.
                        val switchRowNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                        if (switchRowNode != null) {
                            TikTokCaptureBridge.updateProgress("Đang mở danh sách tài khoản...")
                            clickNode(switchRowNode)
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 4: đã ở màn Cài đặt (đã bấm "Cài đặt và quyền riêng tư") - cuộn xuống
                // tới khi thấy dòng "Chuyển đổi tài khoản" rồi bấm vào. Cứ cuộn tiếp mỗi vòng
                // dò tới khi thấy, không giới hạn số lần cuộn - màn hình dài/máy chậm thì cuộn
                // lâu hơn, không sao cả.
                if (hasTappedSettingsRow) {
                    val switchRowNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                    if (switchRowNode != null) {
                        TikTokCaptureBridge.updateProgress("Đã thấy \"Chuyển đổi tài khoản\", đang bấm...")
                        clickNode(switchRowNode)
                        hasTappedSwitchRow = true
                        TikTokCaptureBridge.updateProgress("Đã bấm \"Chuyển đổi tài khoản\" xong, đang chờ danh sách hiện ra...")
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang cuộn xuống tìm \"Chuyển đổi tài khoản\"...")
                        scrollDown(root)
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 3: menu (☰) đã bấm thử - tìm và bấm "Cài đặt và quyền riêng tư". Nếu
                // chưa thấy (có thể lần bấm ☰ trước bị trượt), tự quay lại bấm ☰ lần nữa với
                // candidate khác - KHÔNG dừng lại sau vài lần thử như trước, cứ thử tới khi
                // thấy được menu thật sự mở ra.
                if (hasTappedMenuIcon) {
                    val settingsRowNode = findNodeByText(root, SETTINGS_PRIVACY_LABELS, exact = false)
                    if (settingsRowNode != null) {
                        TikTokCaptureBridge.updateProgress("Đã thấy \"Cài đặt và quyền riêng tư\", đang bấm...")
                        clickNode(settingsRowNode)
                        hasTappedSettingsRow = true
                        TikTokCaptureBridge.updateProgress("Đã bấm \"Cài đặt và quyền riêng tư\" xong, đang chờ trang tải...")
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang mở menu...")
                        val menuNode = findMenuIcon(root, menuTapAttempts)
                        if (menuNode != null) {
                            clickNode(menuNode)
                            menuTapAttempts++
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 2: đã ở trang Hồ sơ (thấy @handle) - bấm icon menu (☰) góc trên bên phải.
                if (hasTappedProfileTab) {
                    val handleNode = findHandleNode(root)
                    if (handleNode != null) {
                        val menuNode = findMenuIcon(root, menuTapAttempts)
                        if (menuNode != null) {
                            TikTokCaptureBridge.updateProgress("Đã thấy @, đang mở menu...")
                            clickNode(menuNode)
                            menuTapAttempts++
                            // Bấm xong coi như đã thử mở menu - bước sau (Bước 3) tự kiểm tra
                            // menu có thật sự mở hay chưa (tìm "Cài đặt và quyền riêng tư"); nếu
                            // chưa thấy, Bước 3 tự quay lại thử candidate khác, không cần quay
                            // lại đây nữa.
                            hasTappedMenuIcon = true
                            TikTokCaptureBridge.updateProgress("Đã bấm menu (☰) xong, đang chờ menu hiện ra...")
                        } else {
                            TikTokCaptureBridge.updateProgress("Đang tìm icon menu (☰)...")
                        }
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang chờ trang Hồ sơ hiện @...")
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 1: bấm tab "Hồ sơ" ở thanh dưới cùng.
                val tabNode = findProfileTabNode(root)
                if (tabNode != null) {
                    TikTokCaptureBridge.updateProgress("Đã thấy tab \"Hồ sơ\", đang bấm...")
                    clickNode(tabNode)
                    hasTappedProfileTab = true
                    // Báo NGAY là đã bấm xong (không chờ tới vòng dò kế tiếp mới đổi chữ),
                    // để không bị hiểu lầm là còn đang treo/chưa rõ đã bấm hay chưa.
                    TikTokCaptureBridge.updateProgress("Đã bấm tab \"Hồ sơ\" xong, đang chờ trang tải...")
                } else {
                    TikTokCaptureBridge.updateProgress("Đang tìm tab \"Hồ sơ\" ở thanh dưới cùng...")
                }
                delay(POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    delay(POLL_INTERVAL_MS)
                }
            }
            if (TikTokCaptureBridge.state.value is TikTokCaptureState.Waiting) {
                TikTokCaptureBridge.onFailed("Không tự mở được danh sách tài khoản sau nhiều lần thử, hãy mở lại và thử lại")
            }
        }
    }

    /**
     * Icon menu (☰) ở góc trên bên phải trang Hồ sơ thường KHÔNG có contentDescription rõ
     * ràng (khác các icon còn lại như share/bookmark). Thử theo thứ tự:
     *   1) Node clickable có contentDescription gợi ý (menu/more/tùy chọn/cài đặt).
     *   2) Nếu không có, gom tất cả icon clickable KHÔNG CÓ TEXT ở vùng đầu màn hình (top
     *      ~10%), sắp theo thứ tự từ PHẢI qua TRÁI (☰ luôn là icon NGOÀI CÙNG bên phải trong
     *      thanh trên của trang Hồ sơ) và chọn candidate thứ [attemptIndex] - để nếu lần bấm
     *      trước trượt, lần sau tự thử candidate khác thay vì bấm lại đúng chỗ cũ.
     */
    private fun findMenuIcon(root: AccessibilityNodeInfo, attemptIndex: Int): AccessibilityNodeInfo? {
        val rootBounds = android.graphics.Rect()
        root.getBoundsInScreen(rootBounds)
        if (rootBounds.height() <= 0 || rootBounds.width() <= 0) return null
        // Nới vùng đầu màn hình rộng hơn (14% thay vì 10%) - một số máy status bar/header
        // cao hơn dự tính khiến icon nằm ngoài vùng cũ.
        val headerBottomLimit = rootBounds.top + (rootBounds.height() * 0.14f).toInt()
        val maxIconWidth = (rootBounds.width() * 0.16f).toInt()
        val maxIconHeight = (rootBounds.height() * 0.07f).toInt()

        val byHint = findClickableIconInRegion(root, headerBottomLimit)
        if (byHint != null) return byHint

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectHeaderIconCandidates(root, headerBottomLimit, maxIconWidth, maxIconHeight, candidates)
        if (candidates.isEmpty()) return null
        candidates.sortByDescending { node ->
            val b = android.graphics.Rect()
            node.getBoundsInScreen(b)
            b.right
        }
        // Dùng modulo thay vì coerceIn: số lần thử giờ KHÔNG giới hạn (xem startPollingSwitchAccountList),
        // nên phải quay vòng lại candidate đầu thay vì kẹt mãi ở candidate cuối cùng.
        val index = attemptIndex % candidates.size
        return candidates[index]
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

    /** Tìm node có thể cuộn (scrollable) rồi cuộn xuống 1 nấc; best-effort, không báo lỗi nếu không tìm thấy. */
    private fun scrollDown(root: AccessibilityNodeInfo) {
        val scrollable = findScrollableNode(root) ?: return
        @Suppress("DEPRECATION")
        scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
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
            return
        }
        // Không lấy được toạ độ hợp lệ (node ẩn/0px) -> fallback về ACTION_CLICK như cũ,
        // leo lên node cha gần nhất clickable rồi mới bấm.
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
     * Dò riêng cho tab "Hồ sơ/Tôi" ở thanh dưới cùng - CHỈ khớp theo text HIỂN THỊ THẬT
     * (node.text), KHÔNG dùng contentDescription như findNodeByText thông thường. Lý do:
     * avatar (ảnh đại diện) làm icon cho tab này cũng thường có contentDescription trùng
     * tên tab (vd "Hồ sơ") để hỗ trợ đọc màn hình, khiến findNodeByText có thể vô tình trả
     * về node ẢNH thay vì node CHỮ - bấm vào node ảnh có thể không mở đúng tab (hoặc mở
     * preview avatar) thay vì chuyển sang trang Hồ sơ. Text thật thì chỉ có ở TextView nhãn
     * tab, không có ở ImageView avatar, nên tránh được nhầm lẫn này.
     */
    private fun findProfileTabNode(
        node: AccessibilityNodeInfo,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 40) return null
        val text = node.text?.toString()?.trim()?.lowercase()
        if (!text.isNullOrBlank()) {
            val match = PROFILE_TAB_LABELS.any { text == it || (it.length >= 4 && text.contains(it)) }
            if (match) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findProfileTabNode(child, depth + 1)
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
}
