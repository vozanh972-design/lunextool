package com.cayxu.app.automation.tiktok

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cayxu.app.data.local.TikTokAppVariant
import java.lang.ref.WeakReference

/**
 * Dịch vụ hỗ trợ - CHỈ dùng để:
 *  1) Best-effort tự bấm vào tab "Tôi/Me/Profile" khi vừa mở app TikTok/Lite/Studio.
 *  2) Khi người dùng bấm nút trên lớp nổi ("Lưu @"), quét cây node của màn hình hiện tại
 *     để tìm text dạng "@handle" và tên hiển thị, rồi báo về TikTokCaptureBridge.
 *
 * Không đọc/gửi bất kỳ dữ liệu nào khác, không tự động thao tác gì ngoài phạm vi trên,
 * và chỉ hoạt động khi cửa sổ đang active thuộc gói TikTok/TikTok Lite/TikTok Studio.
 */
class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        private var instanceRef: WeakReference<TikTokAccessibilityService>? = null

        private val TIKTOK_PACKAGES = setOf(
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.tt.creator"
        )

        private val PROFILE_TAB_LABELS = setOf("tôi", "me", "hồ sơ", "profile")

        /** Gọi từ lớp nổi khi người dùng bấm nút "Lưu @". */
        fun requestCapture(variant: TikTokAppVariant) {
            instanceRef?.get()?.performCapture(variant)
                ?: TikTokCaptureBridge.onFailed("Chưa bật quyền Trợ năng cho ứng dụng")
        }

        /** Gọi từ lớp nổi để thử tự bấm sang tab "Tôi" (best-effort, có thể bỏ qua nếu không tìm thấy). */
        fun requestGoToProfileTab() {
            instanceRef?.get()?.tapProfileTabIfFound()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef = null
    }

    // Không cần xử lý theo sự kiện real-time - việc quét chỉ chạy khi requestCapture() được gọi,
    // để tránh đọc màn hình liên tục không cần thiết.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun tapProfileTabIfFound() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in TIKTOK_PACKAGES) return
        val node = findNodeByText(root, PROFILE_TAB_LABELS, exact = false)
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun performCapture(variant: TikTokAppVariant) {
        val root = rootInActiveWindow
        val activePkg = root?.packageName?.toString()
        val expectedPkg = TikTokAppLauncher.packageNameOf(variant)

        if (root == null || activePkg != expectedPkg) {
            TikTokCaptureBridge.onFailed("Chưa ở đúng màn hình của TikTok, hãy mở app TikTok rồi thử lại")
            return
        }

        val handleNode = findHandleNode(root)
        if (handleNode == null) {
            TikTokCaptureBridge.onFailed("Chưa tìm thấy @ trên màn hình. Hãy bấm vào tab \"Tôi\" rồi thử lại")
            return
        }

        val handleText = handleNode.text?.toString()?.trim().orEmpty()
        val displayName = findDisplayNameNear(root, handleNode)

        TikTokCaptureBridge.onCaptured(
            handle = handleText,
            displayName = displayName,
            avatarUrl = "",
            variant = variant
        )
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

    /** Best-effort: tên hiển thị thường là dòng text gần nhất phía trên/gần node @handle. */
    private fun findDisplayNameNear(root: AccessibilityNodeInfo, handleNode: AccessibilityNodeInfo): String {
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
