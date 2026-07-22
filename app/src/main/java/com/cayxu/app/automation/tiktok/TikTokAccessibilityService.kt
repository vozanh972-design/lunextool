package com.cayxu.app.automation.tiktok

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

/**
 * Dịch vụ hỗ trợ cho luồng lấy tài khoản TikTok. Có 2 đường:
 *  1) TỰ ĐỘNG (best-effort): mỗi khi có sự kiện màn hình đổi trong app TikTok/Lite/Studio
 *     lúc TikTokCaptureBridge đang "Waiting", tự thử bấm tab "Tôi" rồi tự quét @.
 *     Trên một số máy/emulator (vd BlueStacks) accessibility event có thể không bắn đủ
 *     hoặc node tree không đọc được ngay, nên đường tự động có thể không ăn.
 *  2) THỦ CÔNG (đáng tin cậy hơn): người dùng tự bấm tab "Tôi" trong app TikTok, sau đó
 *     bấm nút "Lưu @" trên lớp nổi -> gọi requestCapture() -> quét lại NGAY LÚC ĐÓ,
 *     không phụ thuộc việc event có bắn hay không.
 *
 * Chỉ đọc @handle (định danh công khai) và tên hiển thị, không đọc dữ liệu nào khác,
 * và chỉ hoạt động trong đúng gói TikTok/TikTok Lite/TikTok Studio.
 */
class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        private var instanceRef: WeakReference<TikTokAccessibilityService>? = null

        private val PROFILE_TAB_LABELS = setOf("tôi", "me", "hồ sơ", "profile")

        /** Gọi từ lớp nổi khi người dùng bấm "Lưu @" - quét lại NGAY, không chờ event. */
        fun requestCapture(variant: com.cayxu.app.data.local.TikTokAppVariant) {
            val service = instanceRef?.get()
            if (service == null) {
                TikTokCaptureBridge.onFailed("Chưa bật quyền Trợ năng cho ứng dụng")
                return
            }
            service.performManualCapture(variant)
        }
    }

    private var hasTappedProfileTabThisSession = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Chỉ tự động xử lý khi tool đang thực sự chờ lấy tài khoản.
        val waitingState = TikTokCaptureBridge.state.value as? TikTokCaptureState.Waiting ?: return
        val expectedPkg = TikTokAppLauncher.packageNameOf(waitingState.variant)

        val root = findRootForPackage(expectedPkg) ?: return

        // Best-effort: tự bấm sang tab "Tôi" một lần khi vừa vào app.
        if (!hasTappedProfileTabThisSession) {
            val tabNode = findNodeByText(root, PROFILE_TAB_LABELS, exact = false)
            if (tabNode != null) {
                tabNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                hasTappedProfileTabThisSession = true
                return
            }
        }

        val handleNode = findHandleNode(root) ?: return
        val handleText = handleNode.text?.toString()?.trim().orEmpty()
        if (handleText.length <= 1) return

        val displayName = findDisplayNameNear(handleNode)
        hasTappedProfileTabThisSession = false
        TikTokCaptureBridge.onCaptured(
            handle = handleText,
            displayName = displayName,
            avatarUrl = "",
            variant = waitingState.variant
        )
    }

    override fun onInterrupt() {}

    /** Quét thủ công theo yêu cầu từ lớp nổi (không phụ thuộc accessibility event). */
    private fun performManualCapture(variant: com.cayxu.app.data.local.TikTokAppVariant) {
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

    /** Ưu tiên cửa sổ đang active; nếu không đúng gói, dò qua danh sách windows() để tìm đúng gói. */
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

    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        labels: Set<String>,
        exact: Boolean,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 40) return null
        val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()?.lowercase()
        if (text != null) {
            val match = if (exact) text in labels else labels.any { text == it }
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
