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
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.data.repository.XsmmAccountsRepository
import com.cayxu.app.data.repository.XsmmAccountsResult
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (fullPanel == null && miniBubble == null) {
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

            startAutomationRunner()
        }
        return START_NOT_STICKY
    }

    private fun startAutomationRunner() {
        runnerJob?.cancel()
        runnerJob = serviceScope.launch(Dispatchers.IO) {
            val token = XsmmAccountStore.getToken(applicationContext)
            if (token.isNullOrBlank()) {
                XsmmJobStatusBridge.update("Lỗi: Chưa đăng nhập XSMM")
                return@launch
            }

            val config = XsmmRunConfigStore.get(applicationContext)
            var noTaskConsecutiveCount = 0

            // Ensure we have account IDs for all handles
            val accountIdMap = XsmmAccountStore.getAccountIdMap(applicationContext).toMutableMap()
            val missingHandles = accountHandles.filter { !accountIdMap.containsKey(it.lowercase()) }
            if (missingHandles.isNotEmpty()) {
                XsmmJobStatusBridge.update("Đang đồng bộ danh sách tài khoản...")
                when (val res = XsmmAccountsRepository.getAccounts(token, "tiktok")) {
                    is XsmmAccountsResult.Success -> {
                        res.accounts.forEach { acc ->
                            val h = acc.linkAccount.substringAfterLast("@").trim('/').lowercase()
                            if (h.isNotBlank() && !acc.accountId.isNullOrBlank()) {
                                accountIdMap[h] = acc.accountId
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
                    val uid = accountIdMap[cleanHandle] ?: cleanHandle

                    launch(Dispatchers.Main) {
                        updateProgressDisplay()
                    }

                    XsmmJobStatusBridge.update("Đang lấy nhiệm vụ cho @$cleanHandle...")
                    val taskResult = XsmmTasksRepository.getTasks2(token, config.taskType, uid)

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
                                for (task in taskResult.tasks) {
                                    if (!isActive) break

                                    val target = task.idorlink.ifBlank { task.targetUrl }
                                    launch(Dispatchers.Main) {
                                        taskInfoView?.text = "${task.type}: $target"
                                    }

                                    XsmmJobStatusBridge.update("Đang mở nhiệm vụ: $target")
                                    if (task.targetUrl.isNotBlank()) {
                                        TikTokAppLauncher.openUserProfile(applicationContext, task.targetUrl)
                                    }

                                    val duration = config.doTaskDurationSeconds.coerceAtLeast(1)
                                    for (s in duration downTo 1) {
                                        if (!isActive) break
                                        XsmmJobStatusBridge.update("Đang làm nhiệm vụ... còn ${s}s")
                                        delay(1000L)
                                    }

                                    XsmmJobStatusBridge.update("Đang gửi xác nhận hoàn thành...")
                                    val compRes = XsmmTasksRepository.completeTasks2(
                                        token,
                                        task.type.ifBlank { config.taskType },
                                        listOf(task.id),
                                        uid
                                    )

                                    if (compRes.success) {
                                        totalCompleted++
                                        val pts = if (compRes.points > 0) compRes.points else task.points
                                        totalEarnedPoints += pts

                                        launch(Dispatchers.Main) {
                                            val currentPts = XsmmAccountStore.getPoints(applicationContext) + pts
                                            XsmmAccountStore.updatePoints(applicationContext, currentPts)
                                            XsmmSession.points.value = currentPts
                                            updateProgressDisplay()
                                        }

                                        XsmmJobStatusBridge.update("Thành công +$pts xu! (Đã làm $totalCompleted NV)")

                                        if (compRes.countdown > 0) {
                                            delay(compRes.countdown * 1000L)
                                        }
                                    } else {
                                        XsmmJobStatusBridge.update("Chưa hoàn thành: ${compRes.message}")
                                    }

                                    if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                                        XsmmJobStatusBridge.update("Hoàn thành mục tiêu $totalCompleted NV!")
                                        return@launch
                                    }

                                    delay(config.fetchTaskIntervalSeconds * 1000L)
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

        root.addView(TextView(this).apply { text = "NV HIỆN TẠI"; setTextColor(Color.parseColor("#8A93A6")); textSize = 11.5f; setPadding(0, dp(2), 0, dp(2)) })
        val taskTv = TextView(this).apply {
            text = "Đang kết nối hệ thống lấy nhiệm vụ..."
            setTextColor(Color.parseColor("#C7CBD4"))
            textSize = 12.5f
            setPadding(0, 0, 0, dp(4))
        }
        taskInfoView = taskTv
        root.addView(taskTv)
        root.addView(divider())

        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        statusRow.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#16A34A")) } }, LinearLayout.LayoutParams(dp(7), dp(7)).apply { topMargin = dp(5); rightMargin = dp(8) })
        val statusTv = TextView(this).apply { text = ""; setTextColor(Color.parseColor("#C7CBD4")); textSize = 12.5f; setLineSpacing(dp(2).toFloat(), 1f) }
        statusRow.addView(statusTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusValueView = statusTv
        root.addView(statusRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6); bottomMargin = dp(14) })
        val cur = XsmmJobStatusBridge.status.value
        statusTv.text = cur; statusRow.visibility = if (cur.isBlank()) View.GONE else View.VISIBLE

        root.addView(TextView(this).apply {
            text = "\u25A0  TẠM DỪNG"; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F2534A")) }
            setPadding(0, dp(14), 0, dp(14)); setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        makeDraggable(root, params)
        windowManager.addView(root, params)
        fullPanel = root
    }

    private fun showMiniBubble() {
        fullPanel?.let { runCatching { windowManager.removeView(it) } }; fullPanel = null
        val size = dp(52)
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(16); y = dp(160) }
        bubbleParams = params
        val bubble = FrameLayout(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#F00E1611")); setStroke(dp(1.5f), Color.parseColor("#16A34A")) } }
        bubble.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher_round) }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { val i = dp(6); setMargins(i, i, i, i) })
        var drag = false; var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { drag = false; ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; true }
                MotionEvent.ACTION_MOVE -> { val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt(); if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) drag = true; params.x = ix + dx; params.y = iy + dy; windowManager.updateViewLayout(bubble, params); true }
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
