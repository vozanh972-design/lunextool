package com.cayxu.app.automation.tiktok

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        private const val MAX_MENU_TAP_ATTEMPTS = 5
        private const val MAX_SETTINGS_TAP_ATTEMPTS = 5
        private const val MAX_SCROLL_ATTEMPTS = 14
        private const val MAX_SWITCH_ROW_TAP_ATTEMPTS = 5

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
            "điều khoản và chính sách", "đăng xuất", "đăng nhập"
        )
        // Gợi ý nhận diện icon menu (☰) ở đầu trang Hồ sơ, khi không có contentDescription rõ ràng.
        private val MENU_ICON_HINTS = listOf("menu", "more", "tùy chọn", "cài đặt")
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var pollingJob: Job? = null

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
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }

    // Không cần xử lý gì ở đây - toàn bộ logic tự động nằm ở vòng lặp polling để không phụ
    // thuộc việc event có bắn đúng lúc chuyển tab hay không.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

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
                    val tabNode = findNodeByText(root, PROFILE_TAB_LABELS, exact = false)
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
        var settingsTapAttempts = 0
        var scrollAttempts = 0
        var hasTappedSwitchRow = false
        var switchRowTapAttempts = 0
        pollingJob = scope.launch {
            var attempt = 0
            while (attempt < MAX_POLL_ATTEMPTS) {
                attempt++
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
                    } else if (switchRowTapAttempts < MAX_SWITCH_ROW_TAP_ATTEMPTS) {
                        // Chưa thấy gì cả - có thể bấm bị trượt, thử tìm lại đúng dòng và bấm lại.
                        val switchRowNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                        if (switchRowNode != null) {
                            TikTokCaptureBridge.updateProgress("Đang mở danh sách tài khoản...")
                            clickNode(switchRowNode)
                            switchRowTapAttempts++
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 4: đã ở màn Cài đặt (đã bấm "Cài đặt và quyền riêng tư") - cuộn xuống
                // tới khi thấy dòng "Chuyển đổi tài khoản" rồi bấm vào.
                if (hasTappedSettingsRow) {
                    val switchRowNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                    if (switchRowNode != null) {
                        TikTokCaptureBridge.updateProgress("Đã thấy \"Chuyển đổi tài khoản\", đang bấm...")
                        clickNode(switchRowNode)
                        hasTappedSwitchRow = true
                        switchRowTapAttempts = 1
                    } else if (scrollAttempts < MAX_SCROLL_ATTEMPTS) {
                        TikTokCaptureBridge.updateProgress("Đang cuộn xuống tìm \"Chuyển đổi tài khoản\"...")
                        scrollDown(root)
                        scrollAttempts++
                    } else {
                        TikTokCaptureBridge.updateProgress("Không tìm thấy \"Chuyển đổi tài khoản\", đang thử lại...")
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 3: menu (☰) đã mở - tìm và bấm "Cài đặt và quyền riêng tư".
                if (hasTappedMenuIcon) {
                    val settingsRowNode = findNodeByText(root, SETTINGS_PRIVACY_LABELS, exact = false)
                    if (settingsRowNode != null) {
                        TikTokCaptureBridge.updateProgress("Đã thấy \"Cài đặt và quyền riêng tư\", đang bấm...")
                        clickNode(settingsRowNode)
                        hasTappedSettingsRow = true
                        settingsTapAttempts = 1
                    } else if (settingsTapAttempts < MAX_SETTINGS_TAP_ATTEMPTS) {
                        TikTokCaptureBridge.updateProgress("Đang mở menu...")
                        settingsTapAttempts++
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang chờ menu hiện \"Cài đặt và quyền riêng tư\"...")
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 2: đã ở trang Hồ sơ (thấy @handle) - bấm icon menu (☰) góc trên bên phải.
                if (hasTappedProfileTab) {
                    val handleNode = findHandleNode(root)
                    if (handleNode != null) {
                        if (menuTapAttempts < MAX_MENU_TAP_ATTEMPTS) {
                            val menuNode = findMenuIcon(root, menuTapAttempts)
                            if (menuNode != null) {
                                TikTokCaptureBridge.updateProgress("Đã thấy @, đang mở menu...")
                                clickNode(menuNode)
                                menuTapAttempts++
                                // Bấm xong coi như đã thử mở menu - bước sau tự kiểm tra menu
                                // có thật sự mở hay chưa (tìm "Cài đặt và quyền riêng tư"); nếu
                                // chưa thấy, quay lại bước này thử candidate khác.
                                hasTappedMenuIcon = true
                            }
                        } else {
                            TikTokCaptureBridge.updateProgress("Không tự mở được menu, đang thử lại...")
                        }
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang chờ trang Hồ sơ hiện @...")
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 1: bấm tab "Hồ sơ" ở thanh dưới cùng.
                val tabNode = findNodeByText(root, PROFILE_TAB_LABELS, exact = false)
                if (tabNode != null) {
                    TikTokCaptureBridge.updateProgress("Đã thấy tab \"Hồ sơ\", đang bấm...")
                    clickNode(tabNode)
                    hasTappedProfileTab = true
                } else {
                    TikTokCaptureBridge.updateProgress("Đang tìm tab \"Hồ sơ\" ở thanh dưới cùng...")
                }
                delay(POLL_INTERVAL_MS)
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
        if (rootBounds.height() <= 0) return null
        val headerBottomLimit = rootBounds.top + (rootBounds.height() * 0.10f).toInt()

        val byHint = findClickableIconInRegion(root, headerBottomLimit)
        if (byHint != null) return byHint

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectHeaderIconCandidates(root, headerBottomLimit, candidates)
        if (candidates.isEmpty()) return null
        candidates.sortByDescending { node ->
            val b = android.graphics.Rect()
            node.getBoundsInScreen(b)
            b.right
        }
        val index = attemptIndex.coerceIn(0, candidates.size - 1)
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

    /** Gom các node clickable, KHÔNG CÓ text riêng (icon thuần), nằm trong vùng đầu màn hình. */
    private fun collectHeaderIconCandidates(
        node: AccessibilityNodeInfo,
        headerBottomLimit: Int,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int = 0
    ) {
        if (depth > 40) return
        if (node.isClickable) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.bottom in 1..headerBottomLimit && node.text.isNullOrBlank()) {
                out.add(node)
                return
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectHeaderIconCandidates(child, headerBottomLimit, out, depth + 1)
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
    private fun clickNode(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && !target.isClickable && depth < 10) {
            target = target.parent
            depth++
        }
        (target ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
