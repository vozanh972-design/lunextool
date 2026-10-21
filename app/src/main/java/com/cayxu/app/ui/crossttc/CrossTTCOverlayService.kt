package com.cayxu.app.ui.crossttc

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
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
    }

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
            expanded = true,
            dockedSide = OverlaySide.Right,
            language = LanguageState.language,
            appVersion = "v1.0.5"
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
            expanded = true,
            dockedSide = OverlaySide.Right,
            language = LanguageState.language,
            appVersion = if (appV.isNotBlank()) "v$appV" else "v1.0.5"
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

    private fun startAutomationRunner() {
        runnerJob?.cancel()
        runnerJob = serviceScope.launch(Dispatchers.IO) {
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

                    suspend fun flushBatchClaim(): Boolean {
                        if (pendingBatchJobIds.isEmpty()) return true
                        val batchCount = pendingBatchJobIds.size
                        setStatus("Gom nhận xu cho $batchCount nhiệm vụ đã làm...")
                        val claimRes = CrossTTCApi.claimTikTokJobReward(currentSessionCookie, accessToken, "subcheo", pendingBatchJobIds.toList())
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

                    // BƯỚC 3: VÒNG LẶP LÀM NHIỆM VỤ CHO TÀI KHOẢN NÀY
                    var failedJobsThisAccount = 0
                    while (isActive) {
                        checkPauseWait()
                        if (config.goalTaskCount > 0 && totalCompleted >= config.goalTaskCount) {
                            flushBatchClaim()
                            setStatus("Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!")
                            return@launch
                        }

                        if (config.autoSwapOnError && failedJobsThisAccount >= config.errorSwapCount) {
                            flushBatchClaim()
                            setStatus("Nick @$cleanHandle bị lỗi $failedJobsThisAccount lần -> Đổi nick...")
                            delay(2000L)
                            break
                        }

                        // Delay trước khi lấy nhiệm vụ
                        val waitGetJob = (config.getJobMinSeconds..config.getJobMaxSeconds).random().coerceAtLeast(1)
                        setStatus("Đợi ${waitGetJob}s lấy nhiệm vụ...")
                        delay(waitGetJob * 1000L)
                        checkPauseWait()

                        setStatus("Lấy nhiệm vụ TikTok TTC...")
                        var jobs = CrossTTCApi.getTikTokJobs(currentSessionCookie, accessToken, "subcheo")
                        if (jobs.isEmpty() && accessToken.isNotBlank()) {
                            val reLogin = CrossTTCApi.loginWithToken(accessToken)
                            if (reLogin.isSuccess && reLogin.sessionCookie.isNotBlank()) {
                                currentSessionCookie = reLogin.sessionCookie
                                jobs = CrossTTCApi.getTikTokJobs(currentSessionCookie, accessToken, "subcheo")
                            }
                        }

                        if (jobs.isEmpty()) {
                            flushBatchClaim()
                            setStatus("Tạm thời hết nhiệm vụ, đang chờ (8s)...")
                            delay(8000L)
                            continue
                        }

                        // Thực hiện từng nhiệm vụ trong danh sách
                        for ((index, job) in jobs.withIndex()) {
                            checkPauseWait()
                            if (!isActive) break
                            if (config.goalTaskCount > 0 && totalCompleted >= config.goalTaskCount) break

                            val jobPosText = "[${index + 1}/${jobs.size}]"
                            val shortTarget = if (job.link.length > 25) job.link.take(22) + "..." else job.link
                            setStatus("$jobPosText Mở Follow: $shortTarget")

                            // Mở trang cá nhân trên TikTok
                            TikTokAppLauncher.openUserProfile(applicationContext, job.link)
                            delay(1400L) // Chờ TikTok chuyển màn hình

                            // Kích hoạt Accessibility tự động bấm Follow
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
                                    is XsmmTaskActionResult.InProgress -> setStatus(res.message)
                                    is XsmmTaskActionResult.Completed -> {
                                        if (res.actionId == actionId) {
                                            taskSuccess = res.success
                                            setStatus(res.message)
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
                                setStatus("Xong $jobPosText [Đã gom $curBatch/8 NV]")

                                // Đủ 8 nhiệm vụ thì gom nhận xu 1 lần theo cơ chế web TTC
                                if (curBatch >= 8) {
                                    val waitComplete = (config.completeJobMinSeconds..config.completeJobMaxSeconds).random().coerceAtLeast(2)
                                    setStatus("Đủ 8 NV! Đợi ${waitComplete}s gom nhận xu...")
                                    delay(waitComplete * 1000L)
                                    checkPauseWait()
                                    val claimed = flushBatchClaim()
                                    if (!claimed) {
                                        failedJobsThisAccount++
                                    }
                                }
                            } else {
                                failedJobsThisAccount++
                                setStatus("Nhiệm vụ lỗi ($failedJobsThisAccount/${config.errorSwapCount})")
                            }

                            if (config.autoSwapOnError && failedJobsThisAccount >= config.errorSwapCount) {
                                break
                            }

                            // Nghỉ ngẫu nhiên giữa các nhiệm vụ
                            val breakTime = (config.breakMinSeconds..config.breakMaxSeconds).random().coerceAtLeast(1)
                            delay(breakTime * 1000L)
                        }

                        // Nếu hết đợt job này mà còn job chưa gom nhận xu, nhận nốt
                        if (pendingBatchJobIds.isNotEmpty()) {
                            flushBatchClaim()
                        }
                    }
                    // Đổi sang nick khác: gom nốt xu nếu còn
                    flushBatchClaim()
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
