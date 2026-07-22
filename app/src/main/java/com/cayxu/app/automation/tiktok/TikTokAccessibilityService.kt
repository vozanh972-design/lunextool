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
        private val PROFILE_TAB_LABELS = setOf("tôi", "me", "hồ sơ", "profile")
        private const val POLL_INTERVAL_MS = 700L
        // Không có mốc cố định vì máy nhanh/chậm khác nhau - cho dò tới ~5 phút rồi mới
        // báo lỗi (chỉ để tránh treo dịch vụ mãi mãi, không phải để giới hạn thời gian chờ
        // TikTok load thật sự).
        private const val MAX_POLL_ATTEMPTS = 420 // ~5 phút

        // Tiêu đề sheet "Chuyển đổi tài khoản" mà TikTok bản chuẩn hiển thị khi bấm mũi tên
        // cạnh tên ở trang "Tôi". Chỉ dùng để XÁC NHẬN sheet đã mở, không dùng để bấm gì.
        private val SWITCH_SHEET_TITLE = setOf("chuyển đổi tài khoản", "switch account", "switch accounts")
        // Nhãn không phải là 1 dòng tài khoản trong sheet - loại các dòng này ra khi quét.
        private val SWITCH_SHEET_IGNORE_LABELS = setOf(
            "chuyển đổi tài khoản", "switch account", "switch accounts",
            "thêm tài khoản", "add account", "quản lý tài khoản", "manage accounts"
        )
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
     * RIÊNG cho TikTok bản chuẩn: sau khi vào tab "Tôi", tự bấm vào khu vực tên/@ (có mũi tên)
     * để mở sheet "Chuyển đổi tài khoản" - đây là sheet CÓ SẴN của chính app TikTok, liệt kê
     * các tài khoản NGƯỜI DÙNG ĐÃ TỰ ĐĂNG NHẬP trên máy này (giống hệt việc người dùng bấm tay
     * để xem, tool chỉ đọc lại chữ đang hiển thị). Sau khi sheet mở, quét toàn bộ các dòng tên
     * trong đó rồi lưu hết một lượt, thay vì phải lặp lại thao tác cho từng acc.
     */
    private fun startPollingSwitchAccountList(variant: TikTokAppVariant) {
        pollingJob?.cancel()
        var hasTappedProfileTab = false
        var hasTappedSwitcher = false
        var switcherTapAttempts = 0
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

                // Bước 3: nếu sheet "Chuyển đổi tài khoản" đã mở (do bước bấm mũi tên bên dưới),
                // quét toàn bộ danh sách trong đó.
                if (hasTappedSwitcher) {
                    val sheetTitleNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                    if (sheetTitleNode != null) {
                        TikTokCaptureBridge.updateProgress("Đã mở danh sách tài khoản, đang quét...")
                        val entries = collectSwitchAccountEntries(root)
                        if (entries.isNotEmpty()) {
                            TikTokCaptureBridge.updateProgress("Đã quét ${entries.size} tài khoản, đang lưu...")
                            TikTokCaptureBridge.onCapturedBatch(entries, variant)
                            stopService(Intent(applicationContext, TikTokCaptureOverlayService::class.java))
                            TikTokAppLauncher.bringToolToFront(applicationContext)
                            return@launch
                        }
                        // Sheet mở nhưng chưa kịp render danh sách bên trong - dò tiếp.
                        TikTokCaptureBridge.updateProgress("Đang chờ danh sách tài khoản hiện ra...")
                    } else {
                        // Có thể lần bấm trước bị trượt (chưa đúng vị trí mũi tên) - thử bấm lại
                        // vài lần trước khi báo lỗi.
                        if (switcherTapAttempts < 5) {
                            val arrowNode = findSwitchAccountArrow(root)
                            if (arrowNode != null) {
                                TikTokCaptureBridge.updateProgress("Đang mở danh sách tài khoản...")
                                clickNode(arrowNode)
                                switcherTapAttempts++
                            }
                        } else {
                            TikTokCaptureBridge.updateProgress("Đang chờ danh sách tài khoản hiện ra...")
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 2: đã ở trang "Tôi" (thấy @handle) - bấm vào khu vực tên để mở sheet.
                if (hasTappedProfileTab) {
                    val handleNode = findHandleNode(root)
                    if (handleNode != null) {
                        val arrowNode = findSwitchAccountArrow(root) ?: handleNode
                        TikTokCaptureBridge.updateProgress("Đã thấy @, đang mở danh sách tài khoản...")
                        clickNode(arrowNode)
                        hasTappedSwitcher = true
                        switcherTapAttempts = 1
                    } else {
                        TikTokCaptureBridge.updateProgress("Đang chờ trang \"Tôi\" hiện @...")
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Bước 1: bấm tab "Tôi" ở thanh dưới cùng.
                val tabNode = findNodeByText(root, PROFILE_TAB_LABELS, exact = false)
                if (tabNode != null) {
                    TikTokCaptureBridge.updateProgress("Đã thấy tab \"Tôi\", đang bấm...")
                    clickNode(tabNode)
                    hasTappedProfileTab = true
                } else {
                    TikTokCaptureBridge.updateProgress("Đang tìm tab \"Tôi\" ở thanh dưới cùng...")
                }
                delay(POLL_INTERVAL_MS)
            }
            if (TikTokCaptureBridge.state.value is TikTokCaptureState.Waiting) {
                TikTokCaptureBridge.onFailed("Không tự mở được danh sách tài khoản sau nhiều lần thử, hãy mở lại và thử lại")
            }
        }
    }

    /**
     * Mũi tên/dropdown cạnh tên ở đầu trang "Tôi" thường không có text riêng - nó là node
     * clickable NHỎ nhất bao quanh (hoặc ngay cạnh) node tên hiển thị/@ ở khu vực đầu trang
     * (không phải trong thanh tab dưới cùng). Nếu không tìm được node clickable tách riêng,
     * fallback: bấm thẳng vào node tên/@ (thường cả khối tên+mũi tên dùng chung 1 click target).
     */
    private fun findSwitchAccountArrow(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val handleNode = findHandleNode(root) ?: return null
        // Tên hiển thị (không bắt đầu bằng @) thường nằm cùng khối với mũi tên chuyển đổi,
        // và nằm PHÍA TRÊN @handle. Ưu tiên node clickable bao quanh tên hiển thị đó.
        var node: AccessibilityNodeInfo? = handleNode.parent
        var depth = 0
        while (node != null && depth < 6) {
            if (node.isClickable) return node
            node = node.parent
            depth++
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
