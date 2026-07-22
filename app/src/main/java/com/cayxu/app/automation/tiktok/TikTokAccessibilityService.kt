package com.cayxu.app.automation.tiktok

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Dịch vụ hỗ trợ - lo TOÀN BỘ việc tự động lấy tài khoản TikTok, KHÔNG cần người dùng
 * bấm tay: ngay khi TikTokCaptureBridge chuyển sang "Waiting" (do người dùng bấm
 * "Tiếp tục" trong tool), service tự dò liên tục (polling) cho tới khi:
 *   - Tìm thấy app TikTok/Lite/Studio đang ở foreground
 *   - Tự bấm tab "Tôi/Me/Profile" nếu chưa ở đó
 *   - Tự tìm text "@handle" trên màn hình rồi tự báo về TikTokCaptureBridge để lưu
 *
 * Dùng polling (dò lặp lại mỗi ~700ms) thay vì chỉ dựa vào onAccessibilityEvent, vì trên
 * nhiều máy/emulator sự kiện đổi nội dung màn hình khi chuyển tab không bắn đủ để bắt kịp -
 * polling đảm bảo vẫn tự chạy được dù event có tới hay không.
 *
 * Nút "Lưu @" trên lớp nổi vẫn giữ làm phương án dự phòng (quét ngay lập tức theo yêu cầu),
 * không thay thế luồng tự động này.
 */
class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        private var instanceRef: WeakReference<TikTokAccessibilityService>? = null

        private val PROFILE_TAB_LABELS = setOf("tôi", "me", "hồ sơ", "profile")
        private const val POLL_INTERVAL_MS = 700L
        // Không có mốc cố định vì máy nhanh/chậm khác nhau - cho dò tới ~5 phút rồi mới
        // báo lỗi (chỉ để tránh treo dịch vụ mãi mãi, không phải để giới hạn thời gian chờ
        // TikTok load thật sự).
        private const val MAX_POLL_ATTEMPTS = 420 // ~5 phút

        /** Gọi từ lớp nổi khi người dùng bấm "Lưu @" - quét lại NGAY, không chờ polling. */
        fun requestCapture(variant: TikTokAppVariant) {
            val service = instanceRef?.get()
            if (service == null) {
                TikTokCaptureBridge.onFailed("Chưa bật quyền Trợ năng cho ứng dụng")
                return
            }
            service.performManualCapture(variant)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var pollingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)

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
        instanceRef = null
    }

    // Không cần xử lý gì ở đây - toàn bộ logic tự động nằm ở vòng lặp polling để không phụ
    // thuộc việc event có bắn đúng lúc chuyển tab hay không.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun startPolling(variant: TikTokAppVariant) {
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
            // Dò rất lâu (vài phút) mà vẫn chưa xong mới báo lỗi - lúc đó bấm "Lưu @" thủ công.
            if (TikTokCaptureBridge.state.value is TikTokCaptureState.Waiting) {
                TikTokCaptureBridge.onFailed("Chưa tự tìm thấy @, hãy bấm tab \"Tôi\" trong TikTok rồi bấm \"Lưu @\"")
            }
        }
    }

    /** Quét thủ công theo yêu cầu từ lớp nổi (không phụ thuộc vòng lặp polling). */
    private fun performManualCapture(variant: TikTokAppVariant) {
        val expectedPkg = TikTokAppLauncher.packageNameOf(variant)
        val root = findRootForPackage(expectedPkg)
        if (root == null) {
            TikTokCaptureBridge.onFailed("Chưa ở đúng màn hình TikTok, hãy mở app rồi thử lại")
            return
        }

        val handleNode = findHandleNode(root)
        if (handleNode == null) {
            TikTokCaptureBridge.onFailed("Chưa tìm thấy @ trên màn hình. Hãy bấm vào tab \"Tôi\" rồi bấm Lưu @ lại")
            return
        }

        val handleText = handleNode.text?.toString()?.trim().orEmpty()
        val displayName = findDisplayNameNear(handleNode)
        TikTokCaptureBridge.onCaptured(
            handle = handleText,
            displayName = displayName,
            avatarUrl = "",
            variant = variant
        )
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
            // BUG cũ: nhánh "không cần khớp chính xác" lại so == y hệt exact, khiến tab
            // "Tôi" có kèm badge/khoảng trắng thừa không bao giờ khớp được. Giờ dùng contains,
            // nhưng CHỈ cho nhãn đủ dài (>=4 ký tự) để tránh khớp nhầm - vd nhãn "me" ngắn mà
            // cho contains thì chữ "Home" (chứa "me") sẽ bị khớp nhầm.
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
