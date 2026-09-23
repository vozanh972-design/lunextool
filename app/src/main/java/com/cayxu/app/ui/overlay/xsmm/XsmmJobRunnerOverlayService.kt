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
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class XsmmJobRunnerOverlayService : Service() {
    companion object {
        const val EXTRA_ACCOUNT_HANDLES = "extra_account_handles"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_VARIANT = "extra_variant"
        const val MODE_RUN_JOBS = "run_jobs"
        const val MODE_VERIFY_ONLY = "verify_only"
    }

    private lateinit var windowManager: WindowManager
    private var fullPanel: View? = null
    private var miniBubble: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var statusValueView: TextView? = null
    private var taskInfoView: TextView? = null
    private var progressInfoView: TextView? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var runnerJob: Job? = null
    private var accountHandles: List<String> = emptyList()

    private var totalCompleted = 0
    private var totalEarnedPoints = 0L

    private var isPaused = false
    private var pauseButtonView: TextView? = null
    private var lastBubbleX = -1
    private var lastBubbleY = -1

    private suspend fun checkPauseWait() {
        if (isPaused) {
            XsmmJobStatusBridge.update("Đã tạm dừng (nhấn Tiếp tục để chạy lại)")
            while (isPaused && serviceScope.isActive) {
                delay(400L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (fullPanel == null && miniBubble == null) {
            val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RUN_JOBS
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
            showFullPanel()

            serviceScope.launch {
                XsmmJobStatusBridge.status.collect { text ->
                    statusValueView?.text = text
                    (statusValueView?.parent as? View)?.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
                }
            }

            if (mode == MODE_VERIFY_ONLY) {
                startVerifyOnlyRunner(variant, accountHandles.firstOrNull().orEmpty())
            } else {
                startAutomationRunner()
            }
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
                if (fullPanel != null) showMiniBubble()
            }

            TikTokAppLauncher.launch(applicationContext, variant, forceStopFirst = true)
            delay(1500L)

            val verifyActionId = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.triggerVerifyAccount(cleanHandle, variant)
            val verifyStartTime = System.currentTimeMillis()
            val maxVerifyWait = 90000L // 90s để máy yếu mở app và tải chậm thoải mái

            while (isActive && (System.currentTimeMillis() - verifyStartTime) < maxVerifyWait) {
                val res = com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge.result.value
                if (res is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.InProgress) {
                    XsmmJobStatusBridge.update(res.message)
                } else if (res is com.cayxu.app.automation.tiktok.XsmmTaskActionResult.Completed && res.actionId == verifyActionId) {
                    XsmmJobStatusBridge.update(res.message)
                    delay(1500L)
                    break
                }
                delay(400L)
            }

            XsmmJobStatusBridge.update("Hoàn tất kiểm tra!")
            delay(1200L)
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
                            if (!isPaused && fullPanel != null) showMiniBubble()
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
                                        if (!isPaused && fullPanel != null) showMiniBubble()
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

    private fun updateProgressDisplay() {
        progressInfoView?.text = "Đã làm: $totalCompleted NV  |  +$totalEarnedPoints xu"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun appVersionName() = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) { "" }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; true }
                MotionEvent.ACTION_MOVE -> { params.x = ix + (e.rawX - tx).toInt(); params.y = iy + (e.rawY - ty).toInt(); windowManager.updateViewLayout(view, params); true }
                else -> false
            }
        }
    }

    private fun showFullPanel() {
        fullPanel?.let { runCatching { windowManager.removeView(it) } }; fullPanel = null
        miniBubble?.let { runCatching { windowManager.removeView(it) } }; miniBubble = null

        val panelW = min((resources.displayMetrics.widthPixels * 0.86f).toInt(), dp(360))
        val params = WindowManager.LayoutParams(
            panelW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((resources.displayMetrics.widthPixels - panelW) / 2).coerceAtLeast(0)
            y = dp(120)
        }
        panelParams = params

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F00E1611"))
                setStroke(dp(1.5f), Color.parseColor("#16A34A"))
            }
        }

        // Header
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#16A34A")) } }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(8) })
        header.addView(TextView(this).apply { text = "CayXu"; setTextColor(Color.WHITE); textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        header.addView(TextView(this).apply { val v = appVersionName(); text = if (v.isNotBlank()) "v$v" else ""; setTextColor(Color.parseColor("#8A93A6")); textSize = 12f; setPadding(dp(8), 0, 0, 0) })
        header.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        header.addView(circleBtn("\u21A9", Color.parseColor("#2E16A34A"), Color.parseColor("#16A34A")) { TikTokAppLauncher.bringToolToFront(applicationContext) })
        header.addView(spacer(8))
        header.addView(circleBtn("\u2013", Color.parseColor("#2E2E38"), Color.parseColor("#C7CBD4")) { showMiniBubble() })
        header.addView(spacer(8))
        header.addView(circleBtn("\u2715", Color.parseColor("#3DFF5252"), Color.parseColor("#FF6B6B")) { stopSelf() })
        root.addView(header)
        root.addView(divider())

        root.addView(infoRow("Chế độ", "XSMM", Color.parseColor("#16A34A")))
        val accSummary = when {
            accountHandles.isEmpty() -> "Chưa chọn tài khoản"
            accountHandles.size == 1 -> "@${accountHandles.first()}"
            else -> "@${accountHandles.first()} + ${accountHandles.size - 1} acc khác"
        }
        root.addView(infoRow("Tài khoản", accSummary, Color.WHITE))

        val progressTv = TextView(this).apply {
            text = "Đã làm: $totalCompleted NV  |  +$totalEarnedPoints xu"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(4))
        }
        progressInfoView = progressTv
        root.addView(progressTv)

        root.addView(divider())

        // Đẩy trạng thái thao tác lên vị trí NV HIỆN TẠI, rút gọn nội dung
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        statusRow.addView(
            View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#16A34A")) }
            },
            LinearLayout.LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(8) }
        )
        val statusTv = TextView(this).apply {
            val cur = XsmmJobStatusBridge.status.value
            text = if (cur.isNotBlank()) cur else "Đang lấy nhiệm vụ..."
            setTextColor(Color.parseColor("#C7CBD4"))
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        statusRow.addView(statusTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusValueView = statusTv
        root.addView(statusRow)

        val pauseBtn = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(14))
            updatePauseButtonState(this)
            setOnClickListener {
                togglePause()
            }
        }
        pauseButtonView = pauseBtn
        root.addView(pauseBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        makeDraggable(root, params)
        windowManager.addView(root, params)
        fullPanel = root
    }

    private fun updatePauseButtonState(btn: TextView) {
        if (isPaused) {
            btn.text = "▶  TIẾP TỤC"
            btn.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#16A34A")) // Xanh lá tiếp tục
            }
        } else {
            btn.text = "\u25A0  TẠM DỪNG"
            btn.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F2534A")) // Đỏ tạm dừng
            }
        }
    }

    private fun togglePause() {
        isPaused = !isPaused
        pauseButtonView?.let { updatePauseButtonState(it) }
        if (isPaused) {
            XsmmJobStatusBridge.update("Đã tạm dừng (nhấn Tiếp tục để chạy lại)")
        } else {
            XsmmJobStatusBridge.update("Đang tiếp tục chạy...")
        }
    }

    private fun showMiniBubble() {
        fullPanel?.let { runCatching { windowManager.removeView(it) } }; fullPanel = null
        pauseButtonView = null
        val size = dp(52)
        val initialX = if (lastBubbleX >= 0) lastBubbleX else dp(16)
        val initialY = if (lastBubbleY >= 0) lastBubbleY else dp(160)
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = initialX; y = initialY }
        bubbleParams = params
        val bubble = FrameLayout(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#F00E1611")); setStroke(dp(1.5f), Color.parseColor("#16A34A")) } }
        bubble.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher_round) }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { val i = dp(6); setMargins(i, i, i, i) })
        var drag = false; var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { drag = false; ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) drag = true
                    params.x = ix + dx
                    params.y = iy + dy
                    lastBubbleX = params.x
                    lastBubbleY = params.y
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> { if (!drag) showFullPanel(); true }
                else -> false
            }
        }
        windowManager.addView(bubble, params); miniBubble = bubble
    }

    private fun circleBtn(sym: String, bg: Int, fg: Int, onClick: () -> Unit): View = TextView(this).apply {
        text = sym; setTextColor(fg); textSize = 14f; gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)); setOnClickListener { onClick() }
    }
    private fun spacer(v: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(v), 0) }
    private fun divider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#2216A34A"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(10) }
    }
    private fun infoRow(label: String, value: String, valueColor: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
        row.addView(TextView(this).apply { text = label; setTextColor(Color.parseColor("#8A93A6")); textSize = 13f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply { text = value; setTextColor(valueColor); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        return row
    }

    override fun onDestroy() {
        super.onDestroy()
        runnerJob?.cancel()
        serviceScope.cancel()
        XsmmJobStatusBridge.clear()
        fullPanel?.let { runCatching { windowManager.removeView(it) } }
        miniBubble?.let { runCatching { windowManager.removeView(it) } }
        fullPanel = null
        miniBubble = null
    }
}
