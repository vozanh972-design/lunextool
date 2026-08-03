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
import kotlin.random.Random

class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        private val PROFILE_TAB_LABELS = setOf("hồ sơ", "tôi", "me", "profile")
        private const val POLL_INTERVAL_MS = 700L
        private const val MAX_POLL_ATTEMPTS = 420

        private val SETTINGS_PRIVACY_LABELS = setOf("cài đặt và quyền riêng tư", "settings and privacy", "settings")

        private val SWITCH_SHEET_TITLE = setOf("chuyển đổi tài khoản", "switch account", "switch accounts")
        private val ADD_ACCOUNT_LABELS = setOf("thêm tài khoản", "add account")
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
        private val MENU_ICON_HINTS = listOf("menu", "more", "tùy chọn", "cài đặt")

        private val COMMENT_LABELS = setOf("bình luận", "comment", "comments")
        private val SHARE_LABELS = setOf("chia sẻ", "share")
        private val COPY_LINK_LABELS = setOf("sao chép liên kết", "copy link")
        private val REPOST_LABELS = setOf("đăng lại", "repost")
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollingJob: Job? = null
    private var nurtureJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            TikTokCaptureBridge.state.collect { state ->
                if (state is TikTokCaptureState.Waiting) {
                    startPolling(state.variant)
                } else {
                    pollingJob?.cancel()
                }
            }
        }
        scope.launch {
            NurtureBridge.state.collect { state ->
                if (state is NurtureState.Running) {
                    startNurtureLoop(state)
                } else {
                    nurtureJob?.cancel()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        nurtureJob?.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

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
                    stopService(Intent(applicationContext, TikTokCaptureOverlayService::class.java))
                    TikTokAppLauncher.bringToolToFront(applicationContext)
                    return@launch
                }

                if (!hasTappedProfileTab) {
                    val tabNode = findProfileTabNode(root)
                    if (tabNode != null) {
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
            if (TikTokCaptureBridge.state.value is TikTokCaptureState.Waiting) {
                TikTokCaptureBridge.onFailed("Không tự tìm thấy @ sau nhiều lần thử, hãy mở lại và thử lại")
            }
        }
    }

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
                try {
                val expectedPkg = TikTokAppLauncher.packageNameOf(variant)
                val root = findRootForPackage(expectedPkg)

                if (root == null) {
                    TikTokCaptureBridge.updateProgress("Đang đợi TikTok tải xong...")
                    delay(POLL_INTERVAL_MS)
                    continue
                }

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
                        TikTokCaptureBridge.updateProgress("Đang mở danh sách tài khoản...")
                    } else {
                        val switchRowNode = findNodeByText(root, SWITCH_SHEET_TITLE, exact = false)
                        if (switchRowNode != null) {
                            TikTokCaptureBridge.updateProgress("Đang mở danh sách tài khoản...")
                            clickNode(switchRowNode)
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

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

                if (hasTappedProfileTab) {
                    val handleNode = findHandleNode(root)
                    if (handleNode != null) {
                        val menuNode = findMenuIcon(root, menuTapAttempts)
                        if (menuNode != null) {
                            TikTokCaptureBridge.updateProgress("Đã thấy @, đang mở menu...")
                            clickNode(menuNode)
                            menuTapAttempts++
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

                val tabNode = findProfileTabNode(root)
                if (tabNode != null) {
                    TikTokCaptureBridge.updateProgress("Đã thấy tab \"Hồ sơ\", đang bấm...")
                    clickNode(tabNode)
                    hasTappedProfileTab = true
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

    private fun findMenuIcon(root: AccessibilityNodeInfo, attemptIndex: Int): AccessibilityNodeInfo? {
        val rootBounds = android.graphics.Rect()
        root.getBoundsInScreen(rootBounds)
        if (rootBounds.height() <= 0 || rootBounds.width() <= 0) return null
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
        val index = attemptIndex % candidates.size
        return candidates[index]
    }

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

    private fun findClickableRowsWithText(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int = 0
    ) {
        if (depth > 40) return
        if (node.isClickable && firstMeaningfulText(node) != null) {
            out.add(node)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableRowsWithText(child, out, depth + 1)
        }
    }

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

    private fun clickNode(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            tapAt(bounds.exactCenterX(), bounds.exactCenterY())
            return
        }
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && !target.isClickable && depth < 10) {
            target = target.parent
            depth++
        }
        (target ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatchGesture(gesture, null, null)
    }

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
