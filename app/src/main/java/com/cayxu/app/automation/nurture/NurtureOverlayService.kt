package com.cayxu.app.automation.nurture

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.util.DeviceUtils
import com.cayxu.app.util.decodeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lớp nổi (floating bubble) RIÊNG cho tính năng "Nuôi tài khoản" - không đụng tới lớp nổi
 * của luồng thêm tài khoản TikTok (TikTokCaptureOverlayService).
 *
 * Đọc thời lượng nuôi mỗi lần từ NurtureConfig (vd 15 phút) -> đếm ngược ngay trên lớp nổi,
 * bên dưới hiện hạn key, dưới nữa là dòng "Mua key tại lunex.io.vn", cuối cùng có nút "Dừng"
 * - bấm Dừng thì đóng lớp nổi và đưa tool trở lại đúng màn hình Nuôi tài khoản.
 */
class NurtureOverlayService : Service() {

    companion object {
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_VARIANT = "extra_variant"
        const val EXTRA_AUTO_WATCH = "extra_auto_watch"
        const val EXTRA_VIEW_COMMENTS = "extra_view_comments"
        const val EXTRA_COPY_LINK = "extra_copy_link"
        const val EXTRA_REPOST = "extra_repost"
        // Không còn const val chữ trực tiếp - giải mã lúc chạy để tránh lộ domain
        // khi decompile APK (đồng bộ với cách làm ở RetrofitClient/LoginScreen).
        val BUY_KEY_URL = decodeText(54, 47, 52, 63, 34, 116, 51, 53, 116, 44, 52)
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var timerText: TextView? = null
    private var keyInfoText: TextView? = null
    private var countDownTimer: CountDownTimer? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 15) ?: 15
        val variant = intent?.getStringExtra(EXTRA_VARIANT)
            ?.let { runCatching { TikTokAppVariant.valueOf(it) }.getOrNull() }
            ?: TikTokAppVariant.STANDARD
        val autoWatch = intent?.getBooleanExtra(EXTRA_AUTO_WATCH, true) ?: true
        val viewComments = intent?.getBooleanExtra(EXTRA_VIEW_COMMENTS, false) ?: false
        val copyLink = intent?.getBooleanExtra(EXTRA_COPY_LINK, false) ?: false
        val repost = intent?.getBooleanExtra(EXTRA_REPOST, false) ?: false

        if (bubbleView == null) {
            showBubble()
            loadKeyInfo()
            startCountdown(durationMinutes)
            // Báo cho TikTokAccessibilityService biết ĐÚNG những hành động nào cần tự chạy -
            // chỉ chạy cái nào cấu hình đã bật, không tự thêm hành động ngoài ý muốn.
            NurtureBridge.start(variant, autoWatch, viewComments, copyLink, repost, durationMinutes)
        }
        return START_NOT_STICKY
    }

    private fun startCountdown(durationMinutes: Int) {
        countDownTimer?.cancel()
        val totalMs = durationMinutes.coerceAtLeast(1) * 60_000L
        countDownTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(msLeft: Long) {
                val totalSec = (msLeft / 1000L).toInt()
                val mm = totalSec / 60
                val ss = totalSec % 60
                timerText?.text = String.format("%02d:%02d", mm, ss)
            }

            override fun onFinish() {
                timerText?.text = "Đã hoàn tất phiên nuôi"
                stopAndReturn()
            }
        }.start()
    }

    /** Hiện hạn key ngay trên lớp nổi + nhắc mua key nếu cần, để biết còn dùng được không. */
    private fun loadKeyInfo() {
        scope.launch {
            val key = SecurePrefs(applicationContext).getKey()
            if (key.isNullOrBlank()) {
                keyInfoText?.text = "⚠ Chưa đăng nhập key"
                return@launch
            }
            when (val result = AuthRepository().verifyKey(key, DeviceUtils.getAndroidId(applicationContext))) {
                is AuthResult.Success -> {
                    val days = result.data.daysLeft
                    keyInfoText?.text = when {
                        days != null && days <= 0 -> "⚠ Key đã hết hạn"
                        days != null -> "Key còn $days ngày"
                        else -> "Key hết hạn: ${result.data.expiresAt ?: "?"}"
                    }
                }
                is AuthResult.ApiError -> keyInfoText?.text = "⚠ ${result.message}"
                is AuthResult.NetworkError -> keyInfoText?.text = "Không kiểm tra được hạn key (mất mạng)"
            }
        }
    }

    private fun stopAndReturn() {
        // Dừng vòng lặp tự động (lướt video/xem bình luận/...) trong TikTokAccessibilityService.
        NurtureBridge.stop()
        // Đưa tool trở lại đúng màn hình Nuôi tài khoản (đang nằm sẵn trên back stack vì
        // người dùng bấm "Nuôi tài khoản" từ chính màn đó, chỉ cần đưa app lên trước).
        TikTokAppLauncher.bringToolToFront(applicationContext)
        stopSelf()
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
                setColor(Color.parseColor("#F0424242"))
            }
        }

        val title = TextView(this).apply {
            text = "CayXu • Đang nuôi tài khoản"
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val timer = TextView(this).apply {
            text = "--:--"
            setTextColor(Color.WHITE)
            textSize = 28f
            setPadding(0, 8, 0, 4)
        }
        timerText = timer

        val keyInfo = TextView(this).apply {
            text = "Đang kiểm tra hạn key..."
            setTextColor(Color.parseColor("#FFF3D4"))
            textSize = 11f
            setPadding(0, 0, 0, 2)
        }
        keyInfoText = keyInfo

        val buyKey = TextView(this).apply {
            text = "Mua key tại $BUY_KEY_URL"
            setTextColor(Color.parseColor("#E6FFFFFF"))
            textSize = 11f
            setPadding(0, 0, 0, 12)
        }

        val stopBtn = TextView(this).apply {
            text = "  Dừng  "
            setTextColor(Color.parseColor("#424242"))
            textSize = 13f
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.WHITE)
            }
            setPadding(24, 14, 24, 14)
            setOnClickListener { stopAndReturn() }
        }

        container.addView(title)
        container.addView(timer)
        container.addView(keyInfo)
        container.addView(buyKey)
        container.addView(stopBtn)

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
        countDownTimer?.cancel()
        NurtureBridge.stop()
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
        }
        bubbleView = null
    }
}
