package com.cayxu.app.ui.overlay.golike

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
import com.cayxu.app.ui.screens.golike.openTikTokProfile
import kotlin.math.min

/**
 * Lớp nổi hiện khi bấm "Thêm" ở acc TikTok chưa có trong GoLike.
 *
 * CHỈ hiển thị thông tin (chế độ/tài khoản/URL) + tự mở link trang cá nhân TikTok lên, sau
 * đó tự vuốt xuống 1 lần để ép tải lại (workaround máy bị lag/đứng hình không tự load) -
 * KHÔNG dùng Accessibility Service để bấm Follow hay bất kỳ thao tác tương tác nào khác
 * thay người dùng. Người dùng tự tay bấm Follow nếu muốn, y hệt việc họ tự mở link đó.
 * Bấm "DỪNG" chỉ đóng lớp nổi lại, không có tiến trình tự động nào đang chạy để dừng.
 *
 * Kích thước lớp nổi tự tính theo % chiều rộng màn hình thật của máy (giới hạn 1 mức tối
 * đa) để máy nhỏ hay to đều hiển thị cân đối, không dùng số px cứng.
 */
class GolikeAddAccountOverlayService : Service() {

    companion object {
        const val EXTRA_HANDLE = "extra_handle"
        const val EXTRA_CREATED_MONTHS_AGO = "extra_created_months_ago"
        const val EXTRA_TARGET_USERNAME = "extra_target_username"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private lateinit var windowManager: WindowManager
    private var fullPanel: View? = null
    private var miniBubble: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (fullPanel == null && miniBubble == null) {
            val handle = intent?.getStringExtra(EXTRA_HANDLE).orEmpty()
            val monthsAgo = intent?.getIntExtra(EXTRA_CREATED_MONTHS_AGO, 0) ?: 0
            val targetUsername = intent?.getStringExtra(EXTRA_TARGET_USERNAME).orEmpty()
            val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            showFullPanel(handle, monthsAgo, targetUsername)

            // Chỉ MỞ trang lên - không thao tác gì thêm bên trong TikTok.
            openTikTokProfile(applicationContext, targetUsername, packageName)

            // Đợi 1 nhịp cho TikTok kịp mở/tải, rồi yêu cầu vuốt xuống 1 lần để ép tải lại -
            // một số máy sau khi mở link bị lag/đứng hình, không tự load được gì, phải tự
            // vuốt tay mới tải lại. Chỉ có tác dụng nếu người dùng đã bật Accessibility Service
            // của tool; nếu chưa bật thì không có gì xảy ra, không lỗi gì cả.
            android.os.Handler(mainLooper).postDelayed({
                com.cayxu.app.automation.tiktok.GolikeReloadBridge.requestReload()
            }, 2500L)
        }
        return START_NOT_STICKY
    }

    // ---- Helper quy đổi dp -> px để hiển thị đúng tỉ lệ trên mọi máy ----
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
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
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun showFullPanel(handle: String, monthsAgo: Int, targetUsername: String) {
        miniBubble?.let { runCatching { windowManager.removeView(it) } }
        miniBubble = null

        // Rộng theo % màn hình thật (86%), tối đa 360dp - máy nhỏ hay to đều cân đối,
        // không quá to cũng không quá nhỏ.
        val screenWidthPx = resources.displayMetrics.widthPixels
        val panelWidthPx = min((screenWidthPx * 0.86f).toInt(), dp(360))

        val params = WindowManager.LayoutParams(
            panelWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((resources.displayMetrics.widthPixels - panelWidthPx) / 2).coerceAtLeast(0)
            y = dp(120)
        }
        panelParams = params

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F00E0E16"))
                setStroke(dp(1.5f), Color.parseColor("#4C8DFF"))
            }
        }

        // ---- Hàng tiêu đề: chấm xanh + tên app + version thật + 3 nút icon ----
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4C8DFF"))
            }
        }
        headerRow.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(8) })

        val appName = TextView(this).apply {
            text = "CayXu"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerRow.addView(appName)

        val version = TextView(this).apply {
            val v = appVersionName()
            text = if (v.isNotBlank()) "v$v" else ""
            setTextColor(Color.parseColor("#8A93A6"))
            textSize = 12f
            setPadding(dp(8), 0, 0, 0)
        }
        headerRow.addView(version)

        val headerSpacer = View(this)
        headerRow.addView(headerSpacer, LinearLayout.LayoutParams(0, 0, 1f))

        headerRow.addView(circleIconButton("\u21A9", Color.parseColor("#2E4C8DFF"), Color.parseColor("#4C8DFF")) {
            TikTokAppLauncher.bringToolToFront(applicationContext)
        })
        headerRow.addView(spacerDp(8))
        headerRow.addView(circleIconButton("\u2013", Color.parseColor("#2E2E38"), Color.parseColor("#C7CBD4")) {
            showMiniBubble(handle, monthsAgo, targetUsername)
        })
        headerRow.addView(spacerDp(8))
        headerRow.addView(circleIconButton("\u2715", Color.parseColor("#3DFF5252"), Color.parseColor("#FF6B6B")) {
            exitToolCompletely()
        })

        root.addView(headerRow)
        root.addView(dividerView())

        root.addView(infoRow("Chế độ", "Liên kết", Color.parseColor("#4C8DFF")))
        val ageText = "tạo $monthsAgo tháng"
        root.addView(infoRow("Tài khoản", "@$handle ($ageText)", Color.WHITE))

        root.addView(dividerView())

        val urlLabel = TextView(this).apply {
            text = "URL"
            setTextColor(Color.parseColor("#8A93A6"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(urlLabel)
        val urlValue = TextView(this).apply {
            text = "tiktok.com/@$targetUsername"
            setTextColor(Color.parseColor("#C7CBD4"))
            textSize = 12f
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(urlValue)

        val stopBtn = TextView(this).apply {
            text = "\u25A0  DỪNG"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F2534A"))
            }
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { stopSelf() }
        }
        root.addView(stopBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        makeDraggable(root, params)

        windowManager.addView(root, params)
        fullPanel = root
    }

    /** Thu nhỏ lại thành 1 bong bóng tròn nhỏ, bấm vào để mở lại panel đầy đủ. */
    private fun showMiniBubble(handle: String, monthsAgo: Int, targetUsername: String) {
        fullPanel?.let { runCatching { windowManager.removeView(it) } }
        fullPanel = null

        val size = dp(52)
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(160)
        }
        bubbleParams = params

        val bubble = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F00E0E16"))
                setStroke(dp(1.5f), Color.parseColor("#4C8DFF"))
            }
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
        }
        bubble.addView(
            logo,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                val inset = dp(6)
                setMargins(inset, inset, inset, inset)
            }
        )

        var isDrag = false
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDrag = false
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) isDrag = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) showFullPanel(handle, monthsAgo, targetUsername)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        miniBubble = bubble
    }

    /** Nút X: đóng HẲN tool (không chỉ đóng lớp nổi, không quay lại tool) - khác với nút
     *  mũi tên (quay lại tool) và nút trừ (chỉ ẩn thành icon). */
    private fun exitToolCompletely() {
        fullPanel?.let { runCatching { windowManager.removeView(it) } }
        miniBubble?.let { runCatching { windowManager.removeView(it) } }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun circleIconButton(symbol: String, bgColor: Int, fgColor: Int, onClick: () -> Unit): View {
        val size = dp(28)
        return TextView(this).apply {
            text = symbol
            setTextColor(fgColor)
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }

    private fun spacerDp(value: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(value), 0)
    }

    private fun dividerView(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#22FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
    }

    private fun infoRow(label: String, value: String, valueColor: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#8A93A6"))
            textSize = 13f
        }
        row.addView(labelView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = TextView(this).apply {
            text = value
            setTextColor(valueColor)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        row.addView(valueView)
        return row
    }

    override fun onDestroy() {
        super.onDestroy()
        fullPanel?.let { runCatching { windowManager.removeView(it) } }
        miniBubble?.let { runCatching { windowManager.removeView(it) } }
        fullPanel = null
        miniBubble = null
    }
}
