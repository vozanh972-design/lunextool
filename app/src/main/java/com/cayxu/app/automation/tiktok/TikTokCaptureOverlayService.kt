package com.cayxu.app.automation.tiktok

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.util.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lớp nổi (floating bubble) - CHỈ dùng cho luồng thêm tài khoản TikTok.
 *
 * Toàn bộ việc bấm tab "Tôi" và đọc @handle đều do TikTokAccessibilityService tự làm.
 * Lớp nổi CHỈ hiển thị trạng thái đang làm gì - không còn nút "Lưu @" (đường tự động
 * đã chạy ổn), chỉ còn nút "Huỷ" để người dùng dừng nếu muốn.
 *
 * Việc đóng lớp nổi + đưa tool lên trước KHÔNG còn dựa vào việc "nghe" trạng thái Captured
 * ở đây nữa (dễ bị lỡ mất do màn hình tool cũng nghe cùng lúc rồi reset trạng thái gần như
 * ngay lập tức) - TikTokAccessibilityService sẽ gọi thẳng stopService()/bringToolToFront()
 * ngay tại chỗ tìm thấy @, đảm bảo chắc chắn chạy, không phụ thuộc StateFlow nữa.
 */
class TikTokCaptureOverlayService : Service() {

    companion object {
        const val EXTRA_VARIANT = "extra_variant"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var statusText: TextView? = null
    private var keyInfoText: TextView? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubbleView == null) {
            showBubble()
            observeProgress()
            loadKeyInfo()
        }
        return START_NOT_STICKY
    }

    /** Hiện hạn key ngay trên lớp nổi để biết còn dùng được không hay phải mua/gia hạn key. */
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
                        days != null && days <= 0 -> "⚠ Key đã hết hạn — cần mua key mới"
                        days != null -> "Key còn $days ngày (hết hạn ${result.data.expiresAt ?: ""})"
                        else -> "Key hết hạn: ${result.data.expiresAt ?: "?"}"
                    }
                }
                is AuthResult.ApiError -> keyInfoText?.text = "⚠ ${result.message}"
                is AuthResult.NetworkError -> keyInfoText?.text = "Không kiểm tra được hạn key (mất mạng)"
            }
        }
    }

    /** Chỉ hiển thị tiến trình từng bước - việc đóng lớp nổi do service accessibility tự gọi thẳng. */
    private fun observeProgress() {
        watchJob?.cancel()
        watchJob = scope.launch {
            TikTokCaptureBridge.progress.collect { msg ->
                if (msg.isNotBlank()) statusText?.text = msg
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
            text = "Đang đợi TikTok tải xong..."
            setTextColor(Color.parseColor("#E6FFFFFF"))
            textSize = 12f
            setPadding(0, 6, 0, 6)
        }
        statusText = status

        val keyInfo = TextView(this).apply {
            text = "Đang kiểm tra hạn key..."
            setTextColor(Color.parseColor("#FFF3D4"))
            textSize = 11f
            setPadding(0, 0, 0, 10)
        }
        keyInfoText = keyInfo

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
        container.addView(keyInfo)
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
