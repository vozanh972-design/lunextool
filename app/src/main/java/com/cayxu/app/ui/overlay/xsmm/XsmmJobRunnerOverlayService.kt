package com.cayxu.app.ui.overlay.xsmm

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.cayxu.app.R
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.data.repository.XsmmAccountsRepository
import com.cayxu.app.data.repository.XsmmAccountsResult
import com.cayxu.app.data.repository.XsmmAddAccountResult
import com.cayxu.app.data.repository.XsmmTasks2Result
import com.cayxu.app.data.repository.XsmmTasksRepository
import com.cayxu.app.ui.screens.golike.GolikeAccount
import com.cayxu.app.ui.screens.golike.GolikeAccountsStore
import com.cayxu.app.ui.screens.golike.GolikeRunningManager
import com.cayxu.app.ui.screens.golike.GolikeRunConfigStore
import com.cayxu.app.ui.screens.golike.GolikeSession
import com.cayxu.app.ui.screens.golike.GolikeTikTokTaskRunner
import kotlin.math.min
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.overlay.OverlaySide
import com.cayxu.app.ui.overlay.RunningOverlayUiState
import com.cayxu.app.ui.overlay.RunningOverlayWindow
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class XsmmJobRunnerOverlayService : Service() {
    companion object {
        const val EXTRA_ACCOUNT_HANDLES = "extra_account_handles"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_VARIANT = "extra_variant"
        const val EXTRA_PLATFORM = "extra_platform"
        const val MODE_RUN_JOBS = "run_jobs"
        const val MODE_VERIFY_ONLY = "verify_only"
        const val PLATFORM_XSMM = "xsmm"
        const val PLATFORM_GOLIKE = "golike"
    }

    private lateinit var windowManager: WindowManager
    private var overlayComposeView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val overlayUiState = MutableStateFlow(
        RunningOverlayUiState(
            mode = "XSMM",
            accountLabel = null,
            doneTasks = 0,
            earnedCoins = 0L,
            statusText = "Đang khởi động...",
            paused = false,
            expanded = true,
            dockedSide = OverlaySide.Right,
            language = LanguageState.language,
            appVersion = "v1.0.6"
        )
    )

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var runnerJob: Job? = null
    private var accountHandles: List<String> = emptyList()

    private var totalCompleted = 0
    private var totalEarnedPoints = 0L

    private var isPaused = false
    private var lastBubbleX = -1
    private var lastBubbleY = -1

    private var currentPlatform: String = PLATFORM_XSMM

    private val themeColor: Int
        get() = if (currentPlatform == PLATFORM_GOLIKE) Color.parseColor("#FF7A00") else Color.parseColor("#16A34A")

    private val themeLightBg: Int
        get() = if (currentPlatform == PLATFORM_GOLIKE) Color.parseColor("#2EFF7A00") else Color.parseColor("#2E16A34A")

    private val themeDivider: Int
        get() = if (currentPlatform == PLATFORM_GOLIKE) Color.parseColor("#22FF7A00") else Color.parseColor("#2216A34A")

    private val currencyUnit: String
        get() = if (currentPlatform == PLATFORM_GOLIKE) "đ" else "xu"

    private val platformTitle: String
        get() = if (currentPlatform == PLATFORM_GOLIKE) "Golike" else "XSMM"

    private suspend fun checkPauseWait() {
        if (isPaused) {
            val curText = overlayUiState.value.statusText
            if (!curText.startsWith("Lỗi", ignoreCase = true) && !curText.startsWith("Golike:", ignoreCase = true)) {
                XsmmJobStatusBridge.update("Đã tạm dừng (nhấn Tiếp tục để chạy lại)")
            }
            while (isPaused && serviceScope.isActive) {
                delay(400L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val platformExtra = intent?.getStringExtra(EXTRA_PLATFORM)
            ?: intent?.getStringExtra("extra_platform")
        if (!platformExtra.isNullOrBlank()) {
            currentPlatform = platformExtra.lowercase()
        }

        val mode = intent?.getStringExtra(EXTRA_MODE)
            ?: intent?.getStringExtra("extra_mode")
            ?: MODE_RUN_JOBS

        if (overlayComposeView != null) {
            runnerJob?.cancel()
            overlayComposeView?.let { runCatching { windowManager.removeView(it) } }
            overlayComposeView = null
            overlayLifecycleOwner?.onDestroy()
            overlayLifecycleOwner = null
        }

        val variantStr = intent?.getStringExtra(EXTRA_VARIANT)
        val variant = variantStr?.let {
            try { TikTokAppVariant.valueOf(it) } catch (_: Exception) { null }
        } ?: TikTokAppVariant.STANDARD

        accountHandles = intent?.getStringExtra(EXTRA_ACCOUNT_HANDLES)
            ?.split(",")
            ?.map { it.trim().removePrefix("@") }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val accSummary = when {
            accountHandles.isEmpty() -> null
            accountHandles.size == 1 -> "@${accountHandles.first()}"
            else -> "@${accountHandles.first()} + ${accountHandles.size - 1} acc"
        }
        val appV = appVersionName()

        overlayUiState.value = RunningOverlayUiState(
            mode = if (currentPlatform == PLATFORM_GOLIKE) "Golike" else "XSMM TikTok",
            accountLabel = accSummary,
            doneTasks = totalCompleted,
            earnedCoins = totalEarnedPoints,
            statusText = "Đang khởi động...",
            paused = isPaused,
            expanded = true,
            dockedSide = OverlaySide.Right,
            language = LanguageState.language,
            appVersion = if (appV.isNotBlank()) "v$appV" else "v1.0.6"
        )
        initOverlayCompose()

        serviceScope.launch {
            XsmmJobStatusBridge.status.collect { text ->
                overlayUiState.value = overlayUiState.value.copy(statusText = text)
            }
        }

        if (mode == MODE_VERIFY_ONLY || mode == "verify_only") {
            startVerifyOnlyRunner(variant, accountHandles.firstOrNull().orEmpty())
        } else if (currentPlatform == PLATFORM_GOLIKE || mode == "golike") {
            startGolikeAutomationRunner(variant)
        } else {
            startAutomationRunner()
        }
        return START_NOT_STICKY
    }

    private fun startVerifyOnlyRunner(variant: TikTokAppVariant, handle: String) {
        runnerJob?.cancel()
        runnerJob = serviceScope.launch(Dispatchers.IO) {
            val cleanHandle = handle.trim().removePrefix("@").lowercase()
            XsmmJobStatusBridge.update("Mở TikTok (${variant.name}) kiểm tra nick...")

            // Thu nhỏ sang mini bubble để không che khuất màn hình TikTok
            launch(Dispatchers.Main) {
                showMiniBubble()
            }

            TikTokAppLauncher.launch(applicationContext, variant, forceStopFirst = true)
            delay(1500L)

            val verifyActionId = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.triggerVerifyAccount(
                targetHandle = cleanHandle,
                variant = variant,
                platform = currentPlatform
            )
            val verifyStartTime = System.currentTimeMillis()
            val maxVerifyWait = 90000L // 90s để máy yếu mở app và tải chậm thoải mái

            var verifySuccess = false
            var verifyMsg = ""
            while (isActive && (System.currentTimeMillis() - verifyStartTime) < maxVerifyWait) {
                val res = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.result.value
                if (res is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.InProgress) {
                    XsmmJobStatusBridge.update(res.message)
                } else if (res is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.Completed && res.actionId == verifyActionId) {
                    verifySuccess = res.success
                    verifyMsg = res.message
                    XsmmJobStatusBridge.update(res.message)
                    delay(1500L)
                    break
                }
                delay(400L)
            }

            if (verifySuccess) {
                XsmmJobStatusBridge.update("Hoàn tất kiểm tra!")
                if (currentPlatform == PLATFORM_GOLIKE) {
                    com.cayxu.app.ui.screens.golike.GolikeAccountsStore.importScannedTikTokAccounts(applicationContext)
                }
                delay(1200L)
            } else {
                XsmmJobStatusBridge.update(verifyMsg.ifBlank { "Kiểm tra thất bại (hết thời gian)" })
                delay(2500L)
            }
            TikTokAppLauncher.bringToolToFront(applicationContext)
            stopSelf()
        }
    }

    private fun startAutomationRunner() {
        runnerJob?.cancel()
        runnerJob = serviceScope.launch(Dispatchers.IO) {
            val token = XsmmAccountStore.getToken(applicationContext)
            if (token.isNullOrBlank()) {
                XsmmJobStatusBridge.update("Lỗi: Chưa đăng nhập XSMM")
                return@launch
            }

            val config = XsmmRunConfigStore.get(applicationContext, "tiktok")
            var noTaskConsecutiveCount = 0

            // Ensure we have account IDs for all handles using accounts2 (ĐA LUỒNG 100%)
            val accountIdMap = XsmmAccountStore.getAccountIdMap(applicationContext).toMutableMap()
            val missingHandles = accountHandles.filter { !accountIdMap.containsKey(it.lowercase()) }
            if (missingHandles.isNotEmpty()) {
                XsmmJobStatusBridge.update("Đang đồng bộ danh sách tài khoản đa luồng...")
                when (val res = XsmmAccountsRepository.getAccounts2(token, accountType = "tiktok")) {
                    is XsmmAccountsResult.Success -> {
                        res.accounts.forEach { acc ->
                            val h = acc.linkAccount.substringAfterLast("@").trim('/').lowercase()
                            val accId = acc.accountId.ifBlank { acc.id }
                            if (h.isNotBlank() && accId.isNotBlank()) {
                                accountIdMap[h] = accId
                            }
                        }
                        XsmmAccountStore.saveAccountIdMap(applicationContext, accountIdMap)
                    }
                    is XsmmAccountsResult.Error -> Unit
                }
            }

            val activeList = accountHandles.ifEmpty { listOf("") }
            XsmmJobStatusBridge.update("Bắt đầu chạy nhiệm vụ ${config.taskType}...")

            while (isActive) {
                for (handle in activeList) {
                    if (!isActive) break

                    // Check stop condition: target task count
                    if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                        XsmmJobStatusBridge.update("Đã đạt mục tiêu $totalCompleted/${config.taskCountTarget} nhiệm vụ. Hoàn thành!")
                        return@launch
                    }
                    if (config.stopAfterCompletedCount > 0 && totalCompleted >= config.stopAfterCompletedCount) {
                        XsmmJobStatusBridge.update("Đã đạt giới hạn $totalCompleted nhiệm vụ. Hoàn thành!")
                        return@launch
                    }

                    val cleanHandle = handle.trim().removePrefix("@").lowercase()
                    var uid = accountIdMap[cleanHandle].orEmpty()

                    // Nếu chưa có UID đa luồng -> Tự động thêm vào POST /api/taskapi/accounts2
                    if (uid.isBlank()) {
                        XsmmJobStatusBridge.update("Đồng bộ @$cleanHandle lên XSMM đa luồng...")
                        val addRes = XsmmAccountsRepository.addTikTokAccount2(token, cleanHandle)
                        if (addRes is XsmmAddAccountResult.Success) {
                            val resolvedId = addRes.account.accountId.ifBlank { addRes.account.id }
                            if (resolvedId.isNotBlank()) {
                                uid = resolvedId
                                accountIdMap[cleanHandle] = uid
                                XsmmAccountStore.saveAccountIdMap(applicationContext, accountIdMap)
                            }
                        }
                    }
                    if (uid.isBlank()) uid = cleanHandle

                    var failedJobsThisAccount = 0

                    launch(Dispatchers.Main) {
                        updateProgressDisplay()
                    }

                    checkPauseWait()

                    // Kiểm tra và chuyển đúng tài khoản TikTok cần chạy như khi bấm kiểm tra tài khoản
                    if (cleanHandle.isNotBlank()) {
                        // Chủ động thu nhỏ để không che màn hình khi thao tác
                        launch(Dispatchers.Main) {
                            if (!isPaused) showMiniBubble()
                        }
                        val allAccounts = com.cayxu.app.data.local.TikTokAccountsStore.getAccounts(applicationContext)
                        val matchedAccount = allAccounts.firstOrNull { it.handle.trim().removePrefix("@").equals(cleanHandle, ignoreCase = true) }
                        val variant = matchedAccount?.variant ?: TikTokAppVariant.STANDARD

                        XsmmJobStatusBridge.update("Mở TikTok (${variant.name}) kiểm tra nick...")
                        TikTokAppLauncher.launch(applicationContext, variant)
                        delay(1200L)
                        val verifyActionId = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.triggerVerifyAccount(cleanHandle, variant)
                        val verifyStartTime = System.currentTimeMillis()
                        val maxVerifyWait = 90000L // 90s để máy yếu mở app và tải chậm thoải mái
                        while (isActive && (System.currentTimeMillis() - verifyStartTime) < maxVerifyWait) {
                            val res = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.result.value
                            if (res is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.InProgress) {
                                XsmmJobStatusBridge.update(res.message)
                            } else if (res is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.Completed && res.actionId == verifyActionId) {
                                XsmmJobStatusBridge.update(res.message)
                                delay(1200L)
                                break
                            }
                            delay(400L)
                        }
                    }

                    checkPauseWait()
                    XsmmJobStatusBridge.update("Lấy nhiệm vụ @$cleanHandle...")
                    var taskResult = XsmmTasksRepository.getTasks2(token, config.taskType, uid)

                    // Nếu server báo cần thêm tài khoản -> Tự động thêm vào POST /api/taskapi/accounts2 và lấy lại job
                    if (taskResult is XsmmTasks2Result.Error && (
                        taskResult.message.contains("cần thêm tài khoản", ignoreCase = true) ||
                        taskResult.message.contains("chưa thêm", ignoreCase = true)
                    )) {
                        XsmmJobStatusBridge.update("Thêm @$cleanHandle vào hệ thống đa luồng...")
                        val addRes = XsmmAccountsRepository.addTikTokAccount2(token, cleanHandle)
                        if (addRes is XsmmAddAccountResult.Success) {
                            val newUid = addRes.account.accountId.ifBlank { addRes.account.id }
                            if (newUid.isNotBlank()) {
                                uid = newUid
                                accountIdMap[cleanHandle] = uid
                                XsmmAccountStore.saveAccountIdMap(applicationContext, accountIdMap)
                            }
                            delay(1200L)
                            XsmmJobStatusBridge.update("Lấy lại nhiệm vụ @$cleanHandle...")
                            taskResult = XsmmTasksRepository.getTasks2(token, config.taskType, uid)
                        }
                    }

                    when (taskResult) {
                        is XsmmTasks2Result.Error -> {
                            noTaskConsecutiveCount++
                            XsmmJobStatusBridge.update("Không lấy được NV (${taskResult.message}). Thử lại sau ${config.fetchTaskIntervalSeconds}s...")
                            if (config.stopAfterNoTaskCount > 0 && noTaskConsecutiveCount >= config.stopAfterNoTaskCount) {
                                XsmmJobStatusBridge.update("Hết nhiệm vụ liên tiếp $noTaskConsecutiveCount lần. Tự động dừng.")
                                return@launch
                            }
                            delay(config.fetchTaskIntervalSeconds * 1000L)
                        }
                        is XsmmTasks2Result.Success -> {
                            if (taskResult.tasks.isEmpty()) {
                                noTaskConsecutiveCount++
                                XsmmJobStatusBridge.update("Hết nhiệm vụ (${config.taskType}). Đang chờ ${config.fetchTaskIntervalSeconds}s...")
                                if (config.stopAfterNoTaskCount > 0 && noTaskConsecutiveCount >= config.stopAfterNoTaskCount) {
                                    XsmmJobStatusBridge.update("Hết nhiệm vụ liên tiếp $noTaskConsecutiveCount lần. Tự động dừng.")
                                    return@launch
                                }
                                delay(config.fetchTaskIntervalSeconds * 1000L)
                            } else {
                                noTaskConsecutiveCount = 0
                                val pendingBatchTaskIds = mutableListOf<String>()

                                // Làm từng job một trong danh sách vừa lấy về, gom đủ 10 job nhận xu 1 lần
                                for ((index, task) in taskResult.tasks.withIndex()) {
                                    if (!isActive) break

                                    // Kiểm tra điều kiện dừng mục tiêu
                                    if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                                        XsmmJobStatusBridge.update("Đã đạt mục tiêu $totalCompleted NV. Hoàn thành!")
                                        break
                                    }
                                    if (config.stopAfterCompletedCount > 0 && totalCompleted >= config.stopAfterCompletedCount) {
                                        XsmmJobStatusBridge.update("Đã đạt giới hạn $totalCompleted NV. Hoàn thành!")
                                        break
                                    }

                                    checkPauseWait()

                                    // Chủ động thu nhỏ thành bong bóng khi chạy job tránh chạm nhầm nút
                                    launch(Dispatchers.Main) {
                                        if (!isPaused) showMiniBubble()
                                    }

                                    val target = task.idorlink.ifBlank { task.targetUrl }
                                    val shortTarget = if (target.length > 22) target.take(19) + "..." else target
                                    val shortType = when {
                                        task.type.contains("follow", ignoreCase = true) -> "Follow"
                                        task.type.contains("like", ignoreCase = true) -> "Like"
                                        task.type.contains("comment", ignoreCase = true) -> "Comment"
                                        else -> task.type.removePrefix("tiktok_")
                                    }
                                    val jobPosText = "[${index + 1}/${taskResult.tasks.size}]"
                                    XsmmJobStatusBridge.update("$jobPosText Mở $shortType: $shortTarget")

                                    if (task.targetUrl.isNotBlank()) {
                                        TikTokAppLauncher.openUserProfile(applicationContext, task.targetUrl)
                                        delay(1200L) // Chờ TikTok chuyển sang trang cá nhân ổn định
                                    }

                                    // Kích hoạt Accessibility Service tự động bấm Follow/Like tuần tự, không nhảy lung tung
                                    val actionId = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.triggerTask(
                                        taskType = task.type.ifBlank { config.taskType },
                                        swipeBefore = config.swipeBeforeTask,
                                        returnHomeAndSwipe = config.returnHomeAndSwipe,
                                        durationSeconds = config.doTaskDurationSeconds
                                    )

                                    var taskActionSuccess = true
                                    val actionStartTime = System.currentTimeMillis()
                                    val maxWaitTime = (config.doTaskDurationSeconds + 20) * 1000L
                                    while (isActive && (System.currentTimeMillis() - actionStartTime) < maxWaitTime) {
                                        val res = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.result.value
                                        when (res) {
                                            is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.InProgress -> {
                                                XsmmJobStatusBridge.update(res.message)
                                            }
                                            is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.Completed -> {
                                                if (res.actionId == actionId) {
                                                    taskActionSuccess = res.success
                                                    XsmmJobStatusBridge.update(res.message)
                                                    break
                                                }
                                            }
                                            else -> Unit
                                        }
                                        delay(400L)
                                    }

                                    if (taskActionSuccess) {
                                        pendingBatchTaskIds.add(task.id)
                                        totalCompleted++
                                        launch(Dispatchers.Main) { updateProgressDisplay() }
                                        XsmmJobStatusBridge.update("Xong $shortType $jobPosText (Đã gom ${pendingBatchTaskIds.size}/10)")
                                    } else {
                                        failedJobsThisAccount++
                                        XsmmJobStatusBridge.update("Job lỗi (Lỗi $failedJobsThisAccount/${config.failJobCountToSwitchAccount})")
                                        if (config.failJobCountToSwitchAccount > 0 && failedJobsThisAccount >= config.failJobCountToSwitchAccount) {
                                            XsmmJobStatusBridge.update("Nick @$cleanHandle lỗi $failedJobsThisAccount job -> Đổi nick...")
                                            delay(2000L)
                                            break
                                        }
                                    }

                                    val isLastInList = (index == taskResult.tasks.size - 1)
                                    // Gom đủ 10 job HOẶC hết danh sách nhiệm vụ mẻ này -> Gửi nhận xu 1 lần!
                                    if (pendingBatchTaskIds.size >= 10 || (isLastInList && pendingBatchTaskIds.isNotEmpty())) {
                                        checkPauseWait()
                                        val batchSize = pendingBatchTaskIds.size
                                        XsmmJobStatusBridge.update("Gửi nhận xu $batchSize job...")

                                        val compRes = XsmmTasksRepository.completeTasks2(
                                            token,
                                            task.type.ifBlank { config.taskType },
                                            pendingBatchTaskIds.toList(),
                                            uid
                                        )

                                        val pts = if (compRes.points > 0) compRes.points else 0
                                        if (pts > 0) {
                                            totalEarnedPoints += pts
                                            launch(Dispatchers.Main) {
                                                val currentPts = XsmmAccountStore.getPoints(applicationContext) + pts
                                                XsmmAccountStore.updatePoints(applicationContext, currentPts)
                                                XsmmSession.points.value = currentPts
                                                updateProgressDisplay()
                                            }
                                        }

                                        if (compRes.success && pts > 0) {
                                            val sc = if (compRes.successCount > 0) compRes.successCount else batchSize
                                            XsmmJobStatusBridge.update("+$pts xu ($sc/$batchSize job) (Xong $totalCompleted NV)")
                                            if (compRes.countdown > 0) {
                                                delay(compRes.countdown * 1000L)
                                            }
                                        } else {
                                            val failedBatch = (batchSize - compRes.successCount).coerceAtLeast(1)
                                            failedJobsThisAccount += failedBatch
                                            XsmmJobStatusBridge.update("Nhận xu lỗi/nhả ($failedJobsThisAccount/${config.failJobCountToSwitchAccount})")
                                            if (config.failJobCountToSwitchAccount > 0 && failedJobsThisAccount >= config.failJobCountToSwitchAccount) {
                                                XsmmJobStatusBridge.update("Nick @$cleanHandle bị $failedJobsThisAccount job lỗi/nhả -> Đổi nick...")
                                                pendingBatchTaskIds.clear()
                                                delay(2000L)
                                                break
                                            }
                                        }
                                        pendingBatchTaskIds.clear()
                                    }

                                    delay(1500L)
                                }

                                // Gửi nốt nếu còn sót trong batch (phòng trường hợp break)
                                if (pendingBatchTaskIds.isNotEmpty() && isActive) {
                                    val batchSize = pendingBatchTaskIds.size
                                    XsmmJobStatusBridge.update("Gửi nhận xu nốt $batchSize job...")
                                    val compRes = XsmmTasksRepository.completeTasks2(
                                        token,
                                        config.taskType,
                                        pendingBatchTaskIds.toList(),
                                        uid
                                    )
                                    val pts = if (compRes.points > 0) compRes.points else 0
                                    if (pts > 0) {
                                        totalEarnedPoints += pts
                                        launch(Dispatchers.Main) {
                                            val currentPts = XsmmAccountStore.getPoints(applicationContext) + pts
                                            XsmmAccountStore.updatePoints(applicationContext, currentPts)
                                            XsmmSession.points.value = currentPts
                                            updateProgressDisplay()
                                        }
                                        XsmmJobStatusBridge.update("+$pts xu (Xong $totalCompleted NV)")
                                    }
                                    pendingBatchTaskIds.clear()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startGolikeAutomationRunner(variant: TikTokAppVariant) {
        runnerJob?.cancel()
        runnerJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val token = GolikeAccountsStore.getToken(applicationContext)
                if (token.isNullOrBlank() || !GolikeAccountsStore.isLoggedIn(applicationContext)) {
                    XsmmJobStatusBridge.update("Lỗi: Chưa đăng nhập Golike")
                    return@launch
                }
                val client = GolikeAccountsStore.getApiClient(applicationContext)

                val activeList = accountHandles.ifEmpty { listOf("") }
                val config = GolikeRunConfigStore.get(applicationContext, "tiktok")

                activeList.forEach { handle ->
                    val clean = handle.trim().removePrefix("@").lowercase()
                    GolikeRunningManager.runningAccounts[clean] = true
                }

                XsmmJobStatusBridge.update("Bắt đầu chạy Golike TikTok...")

                while (isActive) {
                    for (handle in activeList) {
                        if (!isActive) break

                        // Kiểm tra giới hạn số lượng hoàn thành
                        if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                            XsmmJobStatusBridge.update("Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!")
                            return@launch
                        }

                        checkPauseWait()

                        val cleanHandle = handle.trim().removePrefix("@").lowercase()
                        val currentAccounts = GolikeAccountsStore.getAccounts(applicationContext, "tiktok")
                        var matchedAcc = currentAccounts.firstOrNull {
                            it.username.trim().removePrefix("@").equals(cleanHandle, ignoreCase = true) || it.id.equals(cleanHandle, ignoreCase = true)
                        } ?: GolikeAccount(
                            id = cleanHandle,
                            platform = "tiktok",
                            username = cleanHandle,
                            avatar = "",
                            isLive = true,
                            isGolikeLinked = false,
                            lastStatus = "Sẵn sàng"
                        )

                        // Tự động kiểm tra và gắn golikeAccountId từ cache hoặc server Golike
                        var resolvedId = GolikeAccountsStore.getTikTokAccountIdFromMap(applicationContext, cleanHandle).orEmpty()
                        if (resolvedId.isBlank() && matchedAcc.golikeAccountId.isNotBlank()) {
                            resolvedId = matchedAcc.golikeAccountId
                        }
                        if (resolvedId.isBlank()) {
                            val serverId = GolikeTikTokTaskRunner.findExistingTikTokAccountId(client, cleanHandle)
                            if (!serverId.isNullOrBlank()) {
                                resolvedId = serverId
                                GolikeAccountsStore.saveTikTokMapping(applicationContext, cleanHandle, resolvedId)
                            }
                        }
                        if (resolvedId.isNotBlank()) {
                            matchedAcc = matchedAcc.copy(golikeAccountId = resolvedId, isGolikeLinked = true)
                        }

                        GolikeRunningManager.runningAccounts[matchedAcc.id] = true
                        launch(Dispatchers.Main) {
                            updateProgressDisplay()
                        }

                        // Tự động thu nhỏ màn nổi khi bắt đầu chạy để không che màn hình
                        launch(Dispatchers.Main) {
                            if (!isPaused) showMiniBubble()
                        }

                        GolikeTikTokTaskRunner.runTikTokTaskLoop(
                            context = applicationContext,
                            account = matchedAcc,
                            client = client,
                            variant = variant,
                            onStatusChange = { newStatus ->
                                XsmmJobStatusBridge.update(newStatus)
                                GolikeRunningManager.statusMap[matchedAcc.id] = newStatus
                                GolikeAccountsStore.updateAccountProgress(applicationContext, "tiktok", matchedAcc.id, newStatus)
                            },
                            onJobSuccess = { earned ->
                                totalCompleted++
                                totalEarnedPoints += earned
                                val curSucc = (GolikeRunningManager.successCountMap[matchedAcc.id] ?: matchedAcc.successCount) + 1
                                GolikeRunningManager.successCountMap[matchedAcc.id] = curSucc

                                val newBal = GolikeAccountsStore.getBalance(applicationContext) + earned
                                GolikeAccountsStore.updateBalance(applicationContext, newBal)
                                GolikeSession.balance.value = newBal

                                GolikeAccountsStore.updateAccountProgress(applicationContext, "tiktok", matchedAcc.id, "Thành công +$earned đ", isSuccess = true)
                                launch(Dispatchers.Main) {
                                    updateProgressDisplay()
                                }
                                XsmmJobStatusBridge.update("+$earned đ (Xong $totalCompleted NV)")
                            },
                            onJobFailed = { reason ->
                                val curErr = (GolikeRunningManager.errorCountMap[matchedAcc.id] ?: matchedAcc.errorCount) + 1
                                GolikeRunningManager.errorCountMap[matchedAcc.id] = curErr
                                GolikeRunningManager.lastErrorDetailMap[matchedAcc.id] = reason
                                GolikeAccountsStore.updateAccountProgress(applicationContext, "tiktok", matchedAcc.id, "Lỗi: $reason", isSuccess = false, errorDetail = reason)
                                XsmmJobStatusBridge.update("Lỗi: $reason")
                            }
                        )

                        checkPauseWait()
                    }

                    delay(2000L)
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("GolikeRunner", "Lỗi trong startGolikeAutomationRunner", t)
                    XsmmJobStatusBridge.update("Golike: ${t.message ?: "Đã dừng"}")
                }
            }
        }
    }

    private fun updateProgressDisplay() {
        overlayUiState.value = overlayUiState.value.copy(
            doneTasks = totalCompleted,
            earnedCoins = totalEarnedPoints
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun appVersionName() = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) { "" }

    private fun initOverlayCompose() {
        if (overlayComposeView != null) return

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        overlayLifecycleOwner = lifecycleOwner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(460),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        overlayParams = params

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val uiState by overlayUiState.collectAsState()
                RunningOverlayWindow(
                    state = uiState,
                    onReposition = { TikTokAppLauncher.bringToolToFront(applicationContext) },
                    onCollapse = { showMiniBubble() },
                    onExpand = { showFullPanel() },
                    onClose = { stopSelf() },
                    onPauseResume = { togglePause() },
                    onDockSideChange = { side ->
                        overlayUiState.value = overlayUiState.value.copy(dockedSide = side)
                    },
                    onBubbleDragDelta = { dx, dy ->
                        val view = overlayComposeView ?: return@RunningOverlayWindow
                        val params = overlayParams ?: return@RunningOverlayWindow
                        val dm = resources.displayMetrics
                        val screenW = dm.widthPixels
                        val screenH = dm.heightPixels
                        // Chuyển tạm về START khi kéo tự do
                        params.gravity = Gravity.TOP or Gravity.START
                        params.x = (params.x + dx.toInt()).coerceIn(0, (screenW - dp(50)).coerceAtLeast(0))
                        params.y = (params.y + dy.toInt()).coerceIn(0, (screenH - dp(50)).coerceAtLeast(0))
                        lastBubbleX = params.x
                        lastBubbleY = params.y
                        runCatching { windowManager.updateViewLayout(view, params) }
                    },
                    onBubbleDragEnd = {
                        val view = overlayComposeView ?: return@RunningOverlayWindow
                        val params = overlayParams ?: return@RunningOverlayWindow
                        val dm = resources.displayMetrics
                        val screenW = dm.widthPixels
                        val viewW = view.width.takeIf { it > 0 } ?: dp(180)
                        val snapRight = (params.x + viewW / 2) > (screenW / 2)
                        if (snapRight) {
                            params.gravity = Gravity.TOP or Gravity.END
                            params.x = 0
                            overlayUiState.value = overlayUiState.value.copy(dockedSide = OverlaySide.Right)
                        } else {
                            params.gravity = Gravity.TOP or Gravity.START
                            params.x = 0
                            overlayUiState.value = overlayUiState.value.copy(dockedSide = OverlaySide.Left)
                        }
                        lastBubbleX = params.x
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                )
            }
        }

        windowManager.addView(composeView, params)
        overlayComposeView = composeView
    }

    private fun togglePause() {
        isPaused = !isPaused
        overlayUiState.value = overlayUiState.value.copy(paused = isPaused)
        if (isPaused) {
            val curText = overlayUiState.value.statusText
            if (!curText.startsWith("Lỗi", ignoreCase = true) && !curText.startsWith("Golike:", ignoreCase = true)) {
                XsmmJobStatusBridge.update("Đã tạm dừng (nhấn Tiếp tục để chạy lại)")
            }
        } else {
            XsmmJobStatusBridge.update("Đang tiếp tục chạy...")
        }
    }

    private fun showMiniBubble() {
        overlayUiState.value = overlayUiState.value.copy(expanded = false)
        serviceScope.launch(Dispatchers.Main) {
            val view = overlayComposeView ?: return@launch
            val params = overlayParams ?: return@launch
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            val isRight = overlayUiState.value.dockedSide == OverlaySide.Right
            if (isRight) {
                params.gravity = Gravity.TOP or Gravity.END
                params.x = 0
            } else {
                params.gravity = Gravity.TOP or Gravity.START
                params.x = 0
            }
            if (lastBubbleY < 0) {
                lastBubbleY = dp(150)
            }
            params.y = lastBubbleY
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun showFullPanel() {
        serviceScope.launch(Dispatchers.Main) {
            val view = overlayComposeView ?: return@launch
            val params = overlayParams ?: return@launch
            params.gravity = Gravity.TOP or Gravity.START
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = 0
            params.y = dp(120)
            runCatching { windowManager.updateViewLayout(view, params) }
            overlayUiState.value = overlayUiState.value.copy(expanded = true)
        }
    }

    private fun updateOverlayWindowSize(expanded: Boolean) {
        if (expanded) showFullPanel() else showMiniBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        runnerJob?.cancel()
        serviceScope.cancel()
        XsmmJobStatusBridge.clear()
        if (currentPlatform == PLATFORM_GOLIKE) {
            GolikeRunningManager.runningAccounts.keys.forEach { k ->
                GolikeRunningManager.runningAccounts[k] = false
            }
        }
        overlayComposeView?.let { runCatching { windowManager.removeView(it) } }
        overlayComposeView = null
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
