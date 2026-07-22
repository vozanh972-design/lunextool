package com.cayxu.app.automation.tiktok

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

/**
 * Dịch vụ hỗ trợ - CHỈ dùng để tự động (KHÔNG cần người dùng bấm tay):
 *  1) Khi TikTokCaptureBridge đang ở trạng thái "Waiting" và người dùng đang ở đúng app
 *     TikTok/TikTok Lite/TikTok Studio, tự bấm vào tab "Tôi/Me/Profile" (best-effort).
 *  2) Tự quét cây node của màn hình để tìm text dạng "@handle" (định danh công khai,
 *     KHÔNG phải mật khẩu) và tên hiển thị gần đó, rồi tự báo về TikTokCaptureBridge -
 *     không cần người dùng bấm nút gì thêm.
 *
 * Chỉ xử lý khi cửa sổ đang active thuộc đúng gói TikTok/TikTok Lite/TikTok Studio và chỉ khi
 * đang trong phiên "Waiting" do người dùng chủ động bắt đầu (chọn loại TikTok trong tool).
 * Không đọc/gửi bất kỳ dữ liệu nào khác.
 */
class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        private var instanceRef: WeakReference<TikTokAccessibilityService>? = null

        private val PROFILE_TAB_LABELS = setOf("tôi", "me", "hồ sơ", "profile")
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
        // Chỉ xử lý khi tool đang thực sự chờ lấy tài khoản (người dùng vừa chọn loại
        // TikTok trong bottom sheet) - tránh đọc màn hình ngoài lúc cần.
        val waitingState = TikTokCaptureBridge.state.value as? TikTokCaptureState.Waiting ?: return
        val expectedPkg = TikTokAppLauncher.packageNameOf(waitingState.variant)

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != expectedPkg) return

        // Best-effort: tự bấm sang tab "Tôi" một lần khi vừa vào app.
        if (!hasTappedProfileTabThisSession) {
            val tabNode = findNodeByText(root, PROFILE_TAB_LABELS, exact = false)
            if (tabNode != null) {
                tabNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                hasTappedProfileTabThisSession = true
                // Đợi màn hình "Tôi" render xong ở sự kiện kế tiếp rồi mới quét @, không quét ngay.
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
