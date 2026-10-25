package com.cayxu.app.ui.f4f

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
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
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.XsmmTaskActionResult
import com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.overlay.OverlaySide
import com.cayxu.app.ui.overlay.RunningOverlayUiState
import com.cayxu.app.ui.overlay.RunningOverlayWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service quản lý màn hình nổi (Overlay) và tự động thực hiện nhiệm vụ TikTok Follow4Follow (F4F).
 * Tách biệt 100% trong package com.cayxu.app.ui.f4f, không đụng chạm đến XSMM hay bên khác.
 */
class F4fJobRunnerOverlayService : Service() {

    companion object {
        const val EXTRA_ACCOUNT_HANDLES = "extra_account_handles"
        const val EXTRA_VARIANT = "extra_variant"

        @Volatile
        var instance: F4fJobRunnerOverlayService? = null
            private set

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private lateinit var windowManager: WindowManager
    private var overlayComposeView: ComposeView? = null
    private var overlayLifecycleOwner: F4fOverlayLifecycleOwner? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private val overlayUiState = MutableStateFlow(
        RunningOverlayUiState(
            mode = "Follow4Follow",
            accountLabel = null,
            doneTasks = 0,
            earnedCoins = 0L,
            statusText = "Đang khởi động...",
            paused = false,
            expanded = false,
            dockedSide = OverlaySide.Right,
            language = LanguageState.language,
            appVersion = "v1.0.6"
        )
    )

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var runnerJob: Job? = null
    private var accountHandles: List<String> = emptyList()
    private var selectedVariant: TikTokAppVariant = TikTokAppVariant.STANDARD

    private var totalCompleted = 0
    private var totalEarnedCoins = 0L
    private var isPaused = false
    private var lastBubbleX = -1
    private var lastBubbleY = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        _isRunning.value = true

        val variantStr = intent?.getStringExtra(EXTRA_VARIANT).orEmpty()
        selectedVariant = runCatching { TikTokAppVariant.valueOf(variantStr) }.getOrDefault(TikTokAppVariant.STANDARD)

        accountHandles = intent?.getStringExtra(EXTRA_ACCOUNT_HANDLES)
            ?.split(",")
            ?.map { it.trim().removePrefix("@") }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        if (overlayComposeView != null) {
            runnerJob?.cancel()
            overlayComposeView?.let { runCatching { windowManager.removeView(it) } }
            overlayComposeView = null
            overlayLifecycleOwner?.onDestroy()
            overlayLifecycleOwner = null
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val accSummary = when {
            accountHandles.isEmpty() -> null
            accountHandles.size == 1 -> "@${accountHandles.first()}"
            else -> "@${accountHandles.first()} + ${accountHandles.size - 1} acc"
        }
        val appV = appVersionName()

        overlayUiState.value = RunningOverlayUiState(
            mode = "Follow4Follow",
            accountLabel = accSummary,
            doneTasks = totalCompleted,
            earnedCoins = totalEarnedCoins,
            statusText = "Đang chuẩn bị...",
            paused = isPaused,
            expanded = false,
            dockedSide = OverlaySide.Right,
            language = LanguageState.language,
            appVersion = if (appV.isNotBlank()) "v$appV" else "v1.0.6"
        )
        initOverlayCompose()
        startAutomationRunner()

        return START_NOT_STICKY
    }

    private fun setStatus(text: String) {
        overlayUiState.value = overlayUiState.value.copy(statusText = text)
    }

    private suspend fun checkPauseWait() {
        if (isPaused) {
            val curText = overlayUiState.value.statusText
            if (!curText.startsWith("Lỗi", ignoreCase = true)) {
                setStatus("Đã tạm dừng (nhấn Tiếp tục để chạy lại)")
            }
            while (isPaused && serviceScope.isActive) {
                delay(400L)
            }
        }
    }

    private fun openTikTokDirectDeepLink(context: Context, rawTarget: String, uid: String): Boolean {
        val cleanTarget = rawTarget.trim()
        val deepLinks = mutableListOf<String>()

        if (cleanTarget.contains("/video/")) {
            // Link video TikTok (dùng cho job Like / Thả tim)
            val videoId = cleanTarget.substringAfter("/video/").substringBefore("?").trim('/')
            if (videoId.isNotBlank() && videoId.all { it.isDigit() }) {
                deepLinks.add("snssdk1233://aweme/detail/$videoId")
                deepLinks.add("snssdk1180://aweme/detail/$videoId")
            }
            deepLinks.add(cleanTarget)
        } else {
            // Link profile người dùng (dùng cho job Follow)
            val cleanHandle = cleanTarget
                .removePrefix("@")
                .removePrefix("https://www.tiktok.com/@")
                .removePrefix("http://www.tiktok.com/@")
                .trim('/')

            val cleanUid = uid.trim()
            if (cleanUid.isNotBlank() && cleanUid.all { it.isDigit() }) {
                deepLinks.add("snssdk1233://user/profile/$cleanUid")
                deepLinks.add("snssdk1180://user/profile/$cleanUid")
            }

            if (cleanHandle.isNotBlank()) {
                deepLinks.add("snssdk1233://user/profile?unique_id=$cleanHandle")
                deepLinks.add("snssdk1180://user/profile?unique_id=$cleanHandle")
                deepLinks.add("tiktok://user/profile?unique_id=$cleanHandle")
                deepLinks.add("https://www.tiktok.com/@$cleanHandle")
            }
        }

        val tikTokPackages = when (selectedVariant) {
            TikTokAppVariant.LITE -> listOf("com.zhiliaoapp.musically.go")
            else -> listOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.zhiliaoapp.musically.go"
            )
        }

        for (pkg in tikTokPackages) {
            for (uriStr in deepLinks) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                        setPackage(pkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {}
            }
        }

        for (uriStr in deepLinks.filter { it.startsWith("snssdk") || it.startsWith("tiktok://") }) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        return false
    }

    private suspend fun countdownWait(seconds: Int, message: (Int) -> String) {
        val safeSeconds = seconds.coerceAtLeast(1)
        for (sec in safeSeconds downTo 1) {
            checkPauseWait()
            if (!serviceScope.isActive) break
            setStatus(message(sec))
            delay(1000L)
        }
    }

    private fun startAutomationRunner() {
        runnerJob?.cancel()
        runnerJob = serviceScope.launch(Dispatchers.IO) {
            val token = F4fAccountStore.getToken(applicationContext).orEmpty()
            if (token.isBlank()) {
                setStatus("Lỗi: Chưa đăng nhập Follow4Follow")
                return@launch
            }

            if (accountHandles.isEmpty()) {
                setStatus("Lỗi: Chưa chọn tài khoản nào")
                return@launch
            }

            val config = F4fRunConfigStore.get(applicationContext)
            setStatus("Bắt đầu chạy Follow4Follow TikTok...")

            // Lấy map tài khoản remote từ F4F để có numeric account_id
            val accountIdMap = mutableMapOf<String, Long>()
            when (val listRes = F4fApiClient.fetchAccountList(token, "tiktok")) {
                is F4fApiResult.Success -> {
                    listRes.data.forEach { acc ->
                        val cleanUname = acc.username.trim().removePrefix("@").lowercase()
                        val idNum = acc.id.toLongOrNull() ?: 0L
                        if (cleanUname.isNotBlank() && idNum > 0L) {
                            accountIdMap[cleanUname] = idNum
                        }
                    }
                }
                is F4fApiResult.Error -> {}
            }

            val cleanHandles = accountHandles.map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }

            while (isActive) {
                for (handle in cleanHandles) {
                    if (!isActive) break

                    if (config.targetTaskCount > 0 && totalCompleted >= config.targetTaskCount) {
                        setStatus("Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!")
                        return@launch
                    }

                    checkPauseWait()

                    overlayUiState.value = overlayUiState.value.copy(accountLabel = "@$handle")
                    val numericAccountId = accountIdMap[handle.lowercase()] ?: 0L

                    // 1. Chuyển / kiểm tra nick TikTok trên ứng dụng TikTok
                    launch(Dispatchers.Main) {
                        if (!isPaused) showMiniBubble()
                    }
                    setStatus("Mở TikTok kiểm tra nick @$handle...")
                    TikTokAppLauncher.launch(applicationContext, selectedVariant)
                    delay(1500L)

                    val verifyActionId = XsmmTaskAutomationBridge.triggerVerifyAccount(
                        targetHandle = handle,
                        variant = selectedVariant,
                        platform = "f4f"
                    )
                    val verifyStartTime = System.currentTimeMillis()
                    val maxVerifyWait = 45000L
                    while (isActive && (System.currentTimeMillis() - verifyStartTime) < maxVerifyWait) {
                        val res = XsmmTaskAutomationBridge.result.value
                        if (res is XsmmTaskActionResult.InProgress) {
                            setStatus(res.message)
                        } else if (res is XsmmTaskActionResult.Completed && res.actionId == verifyActionId) {
                            setStatus(res.message)
                            delay(1000L)
                            break
                        }
                        delay(400L)
                    }

                    // 2. Vòng lặp nhận job và làm nhiệm vụ cho nick này
                    val jobTypesToTry = buildList {
                        if (config.enableFollow) add("follow")
                        if (config.enableLike) add("like")
                    }.ifEmpty { listOf("follow") }

                    var consecutiveFails = 0
                    val maxJobsPerTurn = 15
                    var jobsInCurrentTurn = 0
                    val jobQueue = mutableListOf<F4fJobInfo>()
                    var currentBatchTotal = 0
                    var currentBatchIndex = 0

                    while (isActive && jobsInCurrentTurn < maxJobsPerTurn) {
                        checkPauseWait()

                        if (config.targetTaskCount > 0 && totalCompleted >= config.targetTaskCount) {
                            setStatus("Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!")
                            return@launch
                        }

                        // Nếu hàng đợi rỗng, lấy đợt job mới từ server F4F
                        if (jobQueue.isEmpty()) {
                            for (jType in jobTypesToTry) {
                                if (!isActive) break
                                val typeLabel = if (jType == "like") "Like (thả tim)" else "Follow"
                                setStatus("@$handle: Đang tìm job $typeLabel...")
                                val batchRes = F4fApiClient.getEarnJobs(token, platform = "tiktok", typeJob = jType, uid = handle)
                                if (batchRes is F4fApiResult.Success && batchRes.data.isNotEmpty()) {
                                    val validJobs = batchRes.data.filter { it.typeJob.lowercase() in jobTypesToTry }
                                    if (validJobs.isNotEmpty()) {
                                        jobQueue.clear()
                                        jobQueue.addAll(validJobs)
                                        currentBatchTotal = validJobs.size
                                        currentBatchIndex = 0
                                        setStatus("@$handle: Đã tìm thấy $currentBatchTotal job $typeLabel! Chuẩn bị làm...")
                                        delay(1000L)
                                        break
                                    }
                                }
                                delay(600L)
                            }
                        }

                        if (jobQueue.isEmpty()) {
                            consecutiveFails++
                            val maxConsecutiveFails = if (config.autoSwapOnError) config.errorSwapCount.coerceAtLeast(1) else 3
                            if (consecutiveFails >= maxConsecutiveFails) {
                                setStatus("@$handle: Tạm hết job $consecutiveFails lần -> Chuyển nick tiếp theo...")
                                delay(2000L)
                                break
                            }
                            countdownWait(8) { s -> "@$handle: Hết nhiệm vụ, đợi ${s}s..." }
                            continue
                        }

                        consecutiveFails = 0
                        currentBatchIndex++
                        val currentJob = jobQueue.removeAt(0)
                        jobsInCurrentTurn++

                        val isLikeJob = currentJob.typeJob.equals("like", ignoreCase = true)
                        val jobLabel = if (isLikeJob) "Like" else "Follow"
                        val effAccountId = if (currentJob.accountId > 0L) {
                            currentJob.accountId
                        } else if (numericAccountId > 0L) {
                            numericAccountId
                        } else {
                            currentJob.jobId
                        }

                        // Nếu là job Like (tym), gọi mark-start theo đúng chuẩn cURL web
                        if (isLikeJob) {
                            setStatus("@$handle: Job $currentBatchIndex/$currentBatchTotal: Đánh dấu Like #${currentJob.jobId}...")
                            F4fApiClient.markStartTiktokLike(token, currentJob.jobId, effAccountId)
                            delay(400L)
                        }

                        // Nếu cấu hình bật "Lướt video trước khi làm"
                        if (config.swipeBeforeGetJob) {
                            XsmmTaskAutomationBridge.triggerTask(
                                taskType = "swipe",
                                swipeBefore = false,
                                returnHomeAndSwipe = false,
                                durationSeconds = 1
                            )
                            delay(1000L)
                        }

                        // Mở profile/video trên TikTok
                        setStatus("@$handle: Đang làm job $currentBatchIndex/$currentBatchTotal (#${currentJob.jobId} $jobLabel)...")
                        val opened = openTikTokDirectDeepLink(applicationContext, currentJob.targetLink, "")
                        if (!opened) {
                            TikTokAppLauncher.openUserProfile(applicationContext, currentJob.targetLink)
                        }
                        delay(2000L)

                        // Thời gian làm job ngẫu nhiên trên profile/video theo cấu hình trước khi bấm nút
                        val doJobSec = if (config.doJobMaxSeconds > config.doJobMinSeconds) {
                            (config.doJobMinSeconds..config.doJobMaxSeconds).random()
                        } else {
                            config.doJobMinSeconds.coerceAtLeast(1)
                        }
                        countdownWait(doJobSec) { s -> "Job $currentBatchIndex/$currentBatchTotal: Đang xem $jobLabel (chờ ${s}s)..." }

                        // Bấm nút qua Accessibility
                        val actionId = XsmmTaskAutomationBridge.triggerTask(
                            taskType = if (isLikeJob) "like" else "follow",
                            swipeBefore = false,
                            returnHomeAndSwipe = false,
                            durationSeconds = 3
                        )

                        val startActionTime = System.currentTimeMillis()
                        while (isActive && (System.currentTimeMillis() - startActionTime) < 15000L) {
                            val res = XsmmTaskAutomationBridge.result.value
                            if (res is XsmmTaskActionResult.Completed && res.actionId == actionId) {
                                break
                            }
                            delay(400L)
                        }

                        // Giãn cách chờ xác nhận (ngẫu nhiên giữa min và max)
                        val delaySec = if (config.delayMaxSeconds > config.delayMinSeconds) {
                            (config.delayMinSeconds..config.delayMaxSeconds).random()
                        } else {
                            config.delaySeconds.coerceAtLeast(3)
                        }
                        countdownWait(delaySec) { s -> "Job $currentBatchIndex/$currentBatchTotal: Chờ ${s}s nhận coin..." }

                        // Gửi xác nhận hoàn thành
                        when (val completeRes = F4fApiClient.completeJob(
                            rawToken = token,
                            jobId = currentJob.jobId,
                            accountId = effAccountId,
                            type = "tiktok",
                            typeJob = currentJob.typeJob
                        )) {
                            is F4fApiResult.Success -> {
                                val reward = completeRes.data.takeIf { it > 0 } ?: currentJob.rewardCoins
                                totalCompleted++
                                totalEarnedCoins += reward
                                F4fSession.points.value = F4fSession.points.value + reward
                                updateProgressDisplay()
                                setStatus("+$reward coin (Job $currentBatchIndex/$currentBatchTotal) | Xong $totalCompleted NV")

                                // Kiểm tra mục tiêu số lượng sau khi hoàn thành job
                                if (config.targetTaskCount > 0 && totalCompleted >= config.targetTaskCount) {
                                    setStatus("Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!")
                                    delay(2000L)
                                    stopSelf()
                                    return@launch
                                }
                            }
                            is F4fApiResult.Error -> {
                                setStatus("Job $currentBatchIndex/$currentBatchTotal lỗi: ${completeRes.message}")
                                consecutiveFails++
                                val maxConsecutiveFails = if (config.autoSwapOnError) config.errorSwapCount.coerceAtLeast(1) else 3
                                if (config.autoSwapOnError && consecutiveFails >= maxConsecutiveFails) {
                                    delay(1200L)
                                    setStatus("@$handle: Lỗi liên tiếp $consecutiveFails lần -> Đổi nick...")
                                    delay(2000L)
                                    break
                                }
                            }
                        }

                        delay(1500L)
                    }
                }
            }
        }
    }

    private fun updateProgressDisplay() {
        overlayUiState.value = overlayUiState.value.copy(
            doneTasks = totalCompleted,
            earnedCoins = totalEarnedCoins
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun appVersionName() = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) { "" }

    private fun initOverlayCompose() {
        if (overlayComposeView != null) return

        val lifecycleOwner = F4fOverlayLifecycleOwner().apply { onCreate() }
        overlayLifecycleOwner = lifecycleOwner

        val keepAwake = runCatching {
            com.cayxu.app.data.AppSettingsStore(applicationContext).settings.value.keepAwake
        }.getOrDefault(false)

        var initialFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (keepAwake) {
            initialFlags = initialFlags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = dp(150)
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
                        val p = overlayParams ?: return@RunningOverlayWindow
                        val dm = resources.displayMetrics
                        val screenW = dm.widthPixels
                        val screenH = dm.heightPixels
                        p.gravity = Gravity.TOP or Gravity.START
                        p.x = (p.x + dx.toInt()).coerceIn(0, (screenW - dp(50)).coerceAtLeast(0))
                        p.y = (p.y + dy.toInt()).coerceIn(0, (screenH - dp(50)).coerceAtLeast(0))
                        lastBubbleX = p.x
                        lastBubbleY = p.y
                        runCatching { windowManager.updateViewLayout(view, p) }
                    },
                    onBubbleDragEnd = {
                        val view = overlayComposeView ?: return@RunningOverlayWindow
                        val p = overlayParams ?: return@RunningOverlayWindow
                        val dm = resources.displayMetrics
                        val screenW = dm.widthPixels
                        val viewW = view.width.takeIf { it > 0 } ?: dp(180)
                        val snapRight = (p.x + viewW / 2) > (screenW / 2)
                        if (snapRight) {
                            p.gravity = Gravity.TOP or Gravity.END
                            p.x = 0
                            overlayUiState.value = overlayUiState.value.copy(dockedSide = OverlaySide.Right)
                        } else {
                            p.gravity = Gravity.TOP or Gravity.START
                            p.x = 0
                            overlayUiState.value = overlayUiState.value.copy(dockedSide = OverlaySide.Left)
                        }
                        lastBubbleX = p.x
                        runCatching { windowManager.updateViewLayout(view, p) }
                    }
                )
            }
        }

        windowManager.addView(composeView, params)
        overlayComposeView = composeView
        setupKeepScreenOnObserver()
    }

    private fun setupKeepScreenOnObserver() {
        val settingsStore = com.cayxu.app.data.AppSettingsStore(applicationContext)
        serviceScope.launch {
            settingsStore.settings.collect { settings ->
                val keep = settings.keepAwake
                val p = overlayParams
                val view = overlayComposeView
                if (p != null && view != null) {
                    if (keep) {
                        p.flags = p.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    } else {
                        p.flags = p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
                    }
                    runCatching { windowManager.updateViewLayout(view, p) }
                }
                if (keep) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "cayxu:f4f_wakelock")
        }
        if (wakeLock?.isHeld == false) {
            runCatching { wakeLock?.acquire(24 * 60 * 60 * 1000L) }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    fun checkAndAvoidCollision(targetBounds: Rect): Boolean {
        val view = overlayComposeView ?: return false
        val p = overlayParams ?: return false
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val bubbleBounds = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)

        val expandedTarget = Rect(targetBounds)
        expandedTarget.inset(-dp(10), -dp(10))

        if (Rect.intersects(bubbleBounds, expandedTarget)) {
            val currentDock = overlayUiState.value.dockedSide
            val nextDock = if (currentDock == OverlaySide.Right) OverlaySide.Left else OverlaySide.Right
            val dm = resources.displayMetrics

            if (nextDock == OverlaySide.Left) {
                p.gravity = Gravity.TOP or Gravity.START
                p.x = 0
            } else {
                p.gravity = Gravity.TOP or Gravity.END
                p.x = 0
            }

            if (Math.abs(p.y - targetBounds.centerY()) < dp(80)) {
                if (p.y > dm.heightPixels / 2) {
                    p.y = (p.y - dp(120)).coerceAtLeast(dp(60))
                } else {
                    p.y = (p.y + dp(120)).coerceAtMost(dm.heightPixels - dp(150))
                }
                lastBubbleY = p.y
            }

            overlayUiState.value = overlayUiState.value.copy(dockedSide = nextDock)
            runCatching { windowManager.updateViewLayout(view, p) }
            return true
        }
        return false
    }

    private fun togglePause() {
        isPaused = !isPaused
        overlayUiState.value = overlayUiState.value.copy(paused = isPaused)
        if (isPaused) {
            setStatus("Đã tạm dừng (nhấn Tiếp tục để chạy lại)")
        } else {
            setStatus("Đang tiếp tục chạy...")
        }
    }

    private fun showMiniBubble() {
        overlayUiState.value = overlayUiState.value.copy(expanded = false)
        serviceScope.launch(Dispatchers.Main) {
            val view = overlayComposeView ?: return@launch
            val p = overlayParams ?: return@launch
            p.width = WindowManager.LayoutParams.WRAP_CONTENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            val isRight = overlayUiState.value.dockedSide == OverlaySide.Right
            if (isRight) {
                p.gravity = Gravity.TOP or Gravity.END
                p.x = 0
            } else {
                p.gravity = Gravity.TOP or Gravity.START
                p.x = 0
            }
            if (lastBubbleY < 0) {
                lastBubbleY = dp(150)
            }
            p.y = lastBubbleY
            runCatching { windowManager.updateViewLayout(view, p) }
        }
    }

    private fun showFullPanel() {
        serviceScope.launch(Dispatchers.Main) {
            val view = overlayComposeView ?: return@launch
            val p = overlayParams ?: return@launch
            p.gravity = Gravity.TOP or Gravity.START
            p.width = WindowManager.LayoutParams.MATCH_PARENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            p.x = 0
            p.y = dp(120)
            runCatching { windowManager.updateViewLayout(view, p) }
            overlayUiState.value = overlayUiState.value.copy(expanded = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isRunning.value = false
        releaseWakeLock()
        runnerJob?.cancel()
        serviceScope.cancel()
        overlayComposeView?.let { runCatching { windowManager.removeView(it) } }
        overlayComposeView = null
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
    }
}

private class F4fOverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
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
