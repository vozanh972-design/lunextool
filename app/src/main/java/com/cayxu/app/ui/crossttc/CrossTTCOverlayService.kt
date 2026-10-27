package com.cayxu.app.ui.crossttc

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
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.automation.tiktok.XsmmTaskActionResult
import com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service quản lý màn hình nổi (Overlay) và tự động thực hiện nhiệm vụ TikTok TTC.
 * Hoàn toàn độc lập trong package com.cayxu.app.ui.crossttc.
 */
class CrossTTCOverlayService : Service() {

    companion object {
        const val EXTRA_ACCOUNT_HANDLES = "extra_account_handles"
        const val EXTRA_SESSION_COOKIE = "extra_session_cookie"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"

        @Volatile
        var instance: CrossTTCOverlayService? = null
            private set
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private lateinit var windowManager: WindowManager
    private var overlayComposeView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private val overlayUiState = MutableStateFlow(
        RunningOverlayUiState(
            mode = "TikTok TTC",
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
    private var sessionCookie: String = ""
    private var accessToken: String = ""

    private var totalCompleted = 0
    private var totalEarnedPoints = 0L
    private var isPaused = false
    private var lastBubbleX = -1
    private var lastBubbleY = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        sessionCookie = intent?.getStringExtra(EXTRA_SESSION_COOKIE).orEmpty()
        accessToken = intent?.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
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
            mode = "TikTok TTC",
            accountLabel = accSummary,
            doneTasks = totalCompleted,
            earnedCoins = totalEarnedPoints,
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

    /**
     * Mở trực tiếp trang cá nhân trên app TikTok qua deep link nội bộ,
     * tuyệt đối không mở qua trình duyệt ngoài.
     */
    private fun openTikTokDirectDeepLink(context: Context, rawTarget: String, uid: String): Boolean {
        val cleanHandle = rawTarget.trim()
            .removePrefix("@")
            .removePrefix("https://www.tiktok.com/@")
            .removePrefix("http://www.tiktok.com/@")
            .trim('/')

        val deepLinks = mutableListOf<String>()

        // 1. Ưu tiên schema nội bộ theo UID nếu có (nhanh và chính xác nhất)
        val cleanUid = uid.trim()
        if (cleanUid.isNotBlank() && cleanUid.all { it.isDigit() }) {
            deepLinks.add("snssdk1233://user/profile/$cleanUid")
            deepLinks.add("snssdk1180://user/profile/$cleanUid")
        }

        // 2. Schema nội bộ theo username / unique_id
        if (cleanHandle.isNotBlank()) {
            deepLinks.add("snssdk1233://user/profile?unique_id=$cleanHandle")
            deepLinks.add("snssdk1180://user/profile?unique_id=$cleanHandle")
            deepLinks.add("tiktok://user/profile?unique_id=$cleanHandle")
            deepLinks.add("https://www.tiktok.com/@$cleanHandle")
        }

        // Danh sách các package chính thức của TikTok
        val tikTokPackages = listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go"
        )

        // Cố gắng mở bằng từng package đích danh của TikTok để chặn hoàn toàn trình duyệt ngoài
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

        // Fallback: Thử mở các custom schema (snssdk/tiktok://) mà không gán package
        // (Chỉ app TikTok mới đăng ký scheme này, trình duyệt như Chrome sẽ không mở được)
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
            if (!com.cayxu.app.ui.f4f.F4fNativeBridge.ensureSession(applicationContext)) {
                setStatus("Lỗi: Bản quyền không hợp lệ hoặc đã hết hạn")
                delay(2000L)
                stopSelf()
                return@launch
            }
            if (accountHandles.isEmpty()) {
                setStatus("Lỗi: Chưa chọn tài khoản nào")
                return@launch
            }

            val config = CrossTTCRunConfigStore.get(applicationContext)
            setStatus("Bắt đầu chạy TikTok TTC...")

            while (isActive) {
                for (handle in accountHandles) {
                    if (!isActive) break

                    // Kiểm tra mục tiêu số lượng
                    if (config.goalTaskCount > 0 && totalCompleted >= config.goalTaskCount) {
                        setStatus("Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!")
                        return@launch
                    }

                    val cleanHandle = handle.trim().removePrefix("@").lowercase()
                    launch(Dispatchers.Main) {
                        overlayUiState.value = overlayUiState.value.copy(
                            accountLabel = "@$cleanHandle"
                        )
                    }

                    checkPauseWait()

                    var currentSessionCookie = sessionCookie
                    val pendingBatchJobIds = mutableListOf<String>()

                    suspend fun flushBatchClaim(jobType: String): Boolean {
                        if (pendingBatchJobIds.isEmpty()) return true
                        val batchCount = pendingBatchJobIds.size
                        val typeName = if (jobType.contains("vip", ignoreCase = true)) "Follow VIP" else "Follow Thường"
                        setStatus("Gom nhận xu $batchCount NV $typeName...")
                        val claimRes = CrossTTCApi.claimTikTokJobReward(currentSessionCookie, accessToken, jobType, pendingBatchJobIds.toList())
                        if (claimRes.isSuccess) {
                            totalCompleted += batchCount
                            val pts = claimRes.earnedCoins.takeIf { it > 0 } ?: (batchCount * 500L)
                            totalEarnedPoints += pts
                            launch(Dispatchers.Main) { updateProgressDisplay() }
                            setStatus("+$pts xu (Đã nhận $batchCount NV, Tổng: $totalCompleted)")
                            pendingBatchJobIds.clear()
                            delay(1200L)
                            return true
                        } else {
                            setStatus("Lỗi nhận xu: ${claimRes.message}")
                            delay(1500L)
                            return false
                        }
                    }

                    // BƯỚC 1: ĐẶT NICK CHẠY MẶC ĐỊNH TRÊN TTC WEB (POST /cauhinh/datnick.php iddat[]=uid&loai=tt)
                    setStatus("Đặt @$cleanHandle làm nick chạy TTC...")
                    var ttcAccounts = CrossTTCApi.getTtcTikTokAccounts(currentSessionCookie)
                    if (ttcAccounts.isEmpty() && accessToken.isNotBlank()) {
                        val reLogin = CrossTTCApi.loginWithToken(accessToken)
                        if (reLogin.isSuccess && reLogin.sessionCookie.isNotBlank()) {
                            currentSessionCookie = reLogin.sessionCookie
                            ttcAccounts = CrossTTCApi.getTtcTikTokAccounts(currentSessionCookie)
                        }
                    }
                    val matchedTtc = ttcAccounts.firstOrNull {
                        it.username.trim().removePrefix("@").equals(cleanHandle, ignoreCase = true)
                    }
                    val ttcUid = matchedTtc?.id.orEmpty().ifBlank { cleanHandle }

                    val setDefaultRes = CrossTTCApi.setDefaultTikTokAccount(currentSessionCookie, ttcUid)
                    if (!setDefaultRes.isSuccess) {
                        setStatus("Lỗi đặt nick: ${setDefaultRes.message} -> Thử nick khác...")
                        delay(2500L)
                        continue
                    }
                    setStatus("Đã đặt @$cleanHandle làm nick chạy mặc định")
                    delay(1200L)

                    // BƯỚC 2: KIỂM TRA & CHUYỂN TÀI KHOẢN TRÊN APP TIKTOK
                    launch(Dispatchers.Main) {
                        if (!isPaused) showMiniBubble()
                    }
                    setStatus("Mở TikTok kiểm tra nick @$cleanHandle...")
                    TikTokAppLauncher.launch(applicationContext, TikTokAppVariant.STANDARD)
                    delay(1200L)

                    val verifyActionId = XsmmTaskAutomationBridge.triggerVerifyAccount(
                        targetHandle = cleanHandle,
                        variant = TikTokAppVariant.STANDARD,
                        platform = "ttc"
                    )
                    val verifyStartTime = System.currentTimeMillis()
                    val maxVerifyWait = 60000L
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

                    // Danh sách các loại nhiệm vụ người dùng đã chọn
                    val enabledTypes = mutableListOf<String>()
                    if (config.jobTypeFollowNormal) enabledTypes.add("subcheo")
                    if (config.jobTypeFollowVip) enabledTypes.add("subcheovip")
                    if (enabledTypes.isEmpty()) enabledTypes.add("subcheo")

                    // Ngẫu nhiên chọn loại nhiệm vụ bắt đầu nếu chọn cả hai
                    var currentJobType = enabledTypes.random()

                    // BƯỚC 3: VÒNG LẶP LÀM NHIỆM VỤ CHO TÀI KHOẢN NÀY
                    var failedJobsThisAccount = 0
                    while (isActive) {
                        checkPauseWait()
                        if (config.goalTaskCount > 0 && totalCompleted >= config.goalTaskCount) {
                            flushBatchClaim(currentJobType)
                            setStatus("Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!")
                            return@launch
                        }

                        if (config.autoSwapOnError && failedJobsThisAccount >= config.errorSwapCount) {
                            flushBatchClaim(currentJobType)
                            setStatus("Nick @$cleanHandle bị lỗi $failedJobsThisAccount lần -> Đổi nick...")
                            delay(2000L)
                            break
                        }

                        val typeName = if (currentJobType == "subcheovip") "Follow VIP" else "Follow Thường"

                        // 1. Đếm ngược từng giây trước khi lấy đợt nhiệm vụ mới
                        val waitGetJob = (config.getJobMinSeconds..config.getJobMaxSeconds).random().coerceAtLeast(1)
                        countdownWait(waitGetJob) { s -> "Đợi lấy NV $typeName sau ${s}s..." }
                        checkPauseWait()

                        // 2. Gọi API TTC
                        setStatus("Đang tìm kiếm nhiệm vụ $typeName...")
                        var jobs = CrossTTCApi.getTikTokJobs(currentSessionCookie, accessToken, currentJobType)
                        if (jobs.isEmpty() && accessToken.isNotBlank()) {
                            val reLogin = CrossTTCApi.loginWithToken(accessToken)
                            if (reLogin.isSuccess && reLogin.sessionCookie.isNotBlank()) {
                                currentSessionCookie = reLogin.sessionCookie
                                jobs = CrossTTCApi.getTikTokJobs(currentSessionCookie, accessToken, currentJobType)
                            }
                        }

                        if (jobs.isEmpty()) {
                            flushBatchClaim(currentJobType)
                            countdownWait(8) { s -> "Tạm hết NV $typeName, đang chờ (${s}s)..." }
                            // Nếu chọn cả 2: lần sau chuyển sang loại kia
                            if (enabledTypes.size > 1) {
                                currentJobType = if (currentJobType == "subcheo") "subcheovip" else "subcheo"
                            }
                            continue
                        }

                        // 3. Thông báo số lượng job tìm thấy theo đúng số lượng thực tế
                        val totalJobs = jobs.size
                        setStatus("Đã tìm thấy $totalJobs NV $typeName!")
                        delay(1200L)

                        // 4. Thực hiện từng nhiệm vụ trong danh sách đã tải về
                        for ((index, job) in jobs.withIndex()) {
                            checkPauseWait()
                            if (!isActive) break
                            if (config.goalTaskCount > 0 && totalCompleted >= config.goalTaskCount) break

                            val jobIndex = index + 1
                            val shortTarget = if (job.link.length > 25) job.link.take(22) + "..." else job.link
                            setStatus("Đang làm job [$jobIndex/$totalJobs]: Follow @$shortTarget")

                            // Mở trực tiếp bằng Deep link vào app TikTok (chặn hoàn toàn trình duyệt ngoài)
                            val opened = openTikTokDirectDeepLink(applicationContext, job.link, job.uid)
                            if (!opened) {
                                TikTokAppLauncher.launch(applicationContext, TikTokAppVariant.STANDARD)
                            }
                            delay(1400L) // Chờ TikTok chuyển màn hình

                            // Kích hoạt Accessibility tự động bấm Follow và lướt bảng tin
                            val doJobDuration = (config.doJobMinSeconds..config.doJobMaxSeconds).random().coerceAtLeast(2)
                            val actionId = XsmmTaskAutomationBridge.triggerTask(
                                taskType = "follow",
                                swipeBefore = config.swipeBeforeGetJob,
                                returnHomeAndSwipe = config.swipeAfterFollow,
                                durationSeconds = doJobDuration
                            )

                            var taskSuccess = true
                            val actionStartTime = System.currentTimeMillis()
                            val maxWaitTime = (doJobDuration + 18) * 1000L
                            while (isActive && (System.currentTimeMillis() - actionStartTime) < maxWaitTime) {
                                val res = XsmmTaskAutomationBridge.result.value
                                when (res) {
                                    is XsmmTaskActionResult.InProgress -> {
                                        setStatus("[$jobIndex/$totalJobs] ${res.message}")
                                    }
                                    is XsmmTaskActionResult.Completed -> {
                                        if (res.actionId == actionId) {
                                            taskSuccess = res.success
                                            break
                                        }
                                    }
                                    else -> Unit
                                }
                                delay(400L)
                            }

                            if (taskSuccess) {
                                pendingBatchJobIds.add(job.id)
                                failedJobsThisAccount = 0
                                val curBatch = pendingBatchJobIds.size
                                setStatus("Đã làm xong job [$jobIndex/$totalJobs] [Đã gom $curBatch/8]")
                                delay(1000L)

                                // Đủ 8 nhiệm vụ thì đếm ngược và gom nhận xu 1 lần theo cơ chế web TTC
                                if (curBatch >= 8) {
                                    val waitComplete = (config.completeJobMinSeconds..config.completeJobMaxSeconds).random().coerceAtLeast(2)
                                    countdownWait(waitComplete) { s -> "Đủ 8 NV! Đợi nhận xu (${s}s)..." }
                                    checkPauseWait()
                                    val claimed = flushBatchClaim(currentJobType)
                                    if (!claimed) {
                                        failedJobsThisAccount++
                                    }
                                }
                            } else {
                                failedJobsThisAccount++
                                setStatus("Job [$jobIndex/$totalJobs] lỗi ($failedJobsThisAccount/${config.errorSwapCount})")
                            }

                            if (config.autoSwapOnError && failedJobsThisAccount >= config.errorSwapCount) {
                                break
                            }

                            // 5. Lướt bảng tin & nghỉ ngơi có đếm ngược từng giây trước khi làm job tiếp theo
                            if (jobIndex < totalJobs) {
                                val breakTime = (config.breakMinSeconds..config.breakMaxSeconds).random().coerceAtLeast(1)
                                countdownWait(breakTime) { s -> "Lướt bảng tin & chờ job tiếp theo (${s}s)..." }
                            }
                        }

                        // Khi làm hết sạch toàn bộ totalJobs của đợt này
                        setStatus("Đã hoàn thành toàn bộ $totalJobs NV $typeName!")
                        delay(1200L)

                        // Nếu còn sót job chưa gom nhận xu, nhận nốt
                        if (pendingBatchJobIds.isNotEmpty()) {
                            flushBatchClaim(currentJobType)
                        }

                        // Nếu chọn cả 2 loại: lần tiếp theo chuyển sang loại kia
                        if (enabledTypes.size > 1) {
                            currentJobType = if (currentJobType == "subcheo") "subcheovip" else "subcheo"
                        }
                    }
                    // Đổi sang nick khác: gom nốt xu nếu còn
                    flushBatchClaim(currentJobType)
                }
                delay(2000L)
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
    } catch (_: PackageManager.NameNotFoundException) { "" }

    private fun initOverlayCompose() {
        if (overlayComposeView != null) return

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
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
            wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "cayxu:crossttc_wakelock")
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

    /**
     * Tự động kiểm tra và di chuyển bong bóng nổi nếu đang che khuất vùng [targetBounds] của nút/chữ.
     * Trả về true nếu đã phát hiện che và di chuyển sang vị trí an toàn khác.
     */
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

            // Nếu trục Y vẫn đè lên nút, nhảy lên trên hoặc xuống dưới 120dp
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
        releaseWakeLock()
        runnerJob?.cancel()
        serviceScope.cancel()
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
