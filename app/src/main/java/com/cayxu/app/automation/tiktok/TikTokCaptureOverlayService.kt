package com.cayxu.app.automation.tiktok

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lớp nổi (floating bubble) - CHỈ dùng cho luồng thêm tài khoản TikTok bằng cách check
 * trực tiếp trong app TikTok/TikTok Lite/TikTok Studio. Không đụng tới luồng của
 * các nền tảng khác trong app.
 *
 * Toàn bộ việc bấm tab "Tôi" và đọc @handle đều do TikTokAccessibilityService tự làm
 * theo sự kiện (event-driven) - lớp nổi này CHỈ hiển thị trạng thái đang làm gì, người
 * dùng không cần bấm nút nào để "lưu" cả.
 */
class TikTokCaptureOverlayService : Service() {

    companion object {
        const val EXTRA_VARIANT = "extra_variant"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var statusText: TextView? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val variant = (intent?.getStringExtra(EXTRA_VARIANT))
            ?.let { runCatching { TikTokAppVariant.valueOf(it) }.getOrNull() }
            ?: TikTokAppVariant.LITE

        if (bubbleView == null) {
            showBubble()
            observeBridge()
        }
        return START_NOT_STICKY
    }

    private fun observeBridge() {
        watchJob?.cancel()
        watchJob = scope.launch {
            TikTokCaptureBridge.state.collect { state ->
                when (state) {
                    is TikTokCaptureState.Captured -> {
                        statusText?.text = "Đã tự lấy được ${state.handle} — đang lưu..."
                        Handler(Looper.getMainLooper()).postDelayed({
                            TikTokAppLauncher.bringToolToFront(applicationContext)
                            stopSelf()
                        }, 700)
                    }
                    is TikTokCaptureState.Failed -> {
                        statusText?.text = state.reason
                    }
                    is TikTokCaptureState.Waiting -> {
                        statusText?.text = "Đang tự động mở TikTok và quét @, vui lòng chờ..."
                    }
                    TikTokCaptureState.Idle -> Unit
                }
            }
        }
    }

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 22, 28, 22)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#F02F5BFF"))
            }
        }

        val title = TextView(this).apply {
            text = "CâyXu • Đang tự động lấy tài khoản TikTok"
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val status = TextView(this).apply {
            text = "Đang tự động mở TikTok và quét @, vui lòng chờ..."
            setTextColor(Color.parseColor("#E6FFFFFF"))
            textSize = 12f
            setPadding(0, 6, 0, 14)
        }
        statusText = status

        val closeBtn = TextView(this).apply {
            text = "  Huỷ  "
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setPadding(24, 14, 24, 14)
            setOnClickListener {
                TikTokCaptureBridge.reset()
                stopSelf()
            }
        }

        container.addView(title)
        container.addView(status)
        container.addView(closeBtn)

        // Cho phép kéo lớp nổi đi chỗ khác trên màn hình.
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        bubbleView = container
    }

    override fun onDestroy() {
        super.onDestroy()
        watchJob?.cancel()
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
        }
        bubbleView = null
    }
}
