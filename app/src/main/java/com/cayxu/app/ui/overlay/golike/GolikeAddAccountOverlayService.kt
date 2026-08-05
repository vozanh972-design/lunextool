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
import android.widget.Toast
import com.cayxu.app.R
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.ui.screens.golike.openTikTokProfile
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * Lớp nổi hiện khi bấm "Thêm" ở acc TikTok chưa có trong GoLike.
 *
 * Hiển thị thông tin (chế độ/tài khoản/URL) + tự mở link trang cá nhân TikTok lên, sau đó
 * yêu cầu TikTokAccessibilityService (qua GolikeFollowBridge) tự đợi ~5 giây, kéo xuống tải
 * lại, rồi tìm và bấm nút Follow - CHỈ có tác dụng nếu người dùng đã bật Accessibility
 * Service của tool; nếu chưa bật thì không có gì xảy ra, người dùng vẫn có thể tự tay bấm
 * Follow như bình thường. Bấm "DỪNG" chỉ đóng lớp nổi lại, không huỷ luồng tự bấm Follow
 * đang chờ trong Accessibility Service (vì đó là 1 lần chạy ngắn, tự dừng sau khi xong).
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
        const val EXTRA_VARIANT = "extra_variant"

        /** Timeout AN TOÀN chờ kết quả thật từ GolikeFollowResultBridge - không phải khoảng
         *  đợi cố định, chỉ là giới hạn tối đa phòng khi Accessibility Service không phản
         *  hồi gì (ví dụ người dùng chưa bật quyền). Với bản STANDARD phải đi qua thêm các
         *  bước Hồ sơ -> menu -> Cài đặt -> Chuyển đổi tài khoản trước khi tới bước follow,
         *  nên để dư dả 60s (thay vì 25s như luồng deep link thẳng trước đây). */
        private const val FOLLOW_RESULT_TIMEOUT_MS = 60000L
    }

    private lateinit var windowManager: WindowManager
    private var fullPanel: View? = null
    private var miniBubble: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var statusValueView: TextView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (fullPanel == null && miniBubble == null) {
            val handle = intent?.getStringExtra(EXTRA_HANDLE).orEmpty()
            val monthsAgo = intent?.getIntExtra(EXTRA_CREATED_MONTHS_AGO, 0) ?: 0
            val targetUsername = intent?.getStringExtra(EXTRA_TARGET_USERNAME).orEmpty()
            val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
            val variant = intent?.getStringExtra(EXTRA_VARIANT)
                ?.let { runCatching { com.cayxu.app.data.local.TikTokAppVariant.valueOf(it) }.getOrNull() }

            com.cayxu.app.automation.tiktok.GolikeFollowStatusBridge.clear()

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            showFullPanel(handle, monthsAgo, targetUsername)

            // Hiện LIVE từng bước của luồng tự follow ngay tại chỗ trước đây hiện URL tĩnh -
            // "Đang vuốt tải lại (2/3)...", "Đã bấm Follow, đang xác nhận..." v.v.
            serviceScope.launch {
                com.cayxu.app.automation.tiktok.GolikeFollowStatusBridge.status.collect { text ->
                    statusValueView?.text = text.ifBlank { "tiktok.com/@$targetUsername" }
                }
            }

            // Cho người dùng thấy thông tin đầy đủ 1 chút, rồi TỰ THU NHỎ thành bong bóng
            // nhỏ ở góc trái - bong bóng nhỏ (52dp) nằm sát mép trái không che tab "Hồ sơ"
            // ở thanh điều hướng dưới cùng (mình sắp cần bấm tab đó), cũng không che vùng
            // GIỮA màn hình mà cử chỉ vuốt xuống (reload) sắp thực hiện sau này - khác với
            // trước đây kéo cả lớp nổi lớn xuống đáy, vô tình đè luôn lên tab Hồ sơ.
            fullPanel?.postDelayed({
                showMiniBubble(handle, monthsAgo, targetUsername)
            }, 1200L)

            // Bản TikTok chuẩn (STANDARD) hỗ trợ "Chuyển đổi tài khoản" trong app - nên mở
            // TikTok BÌNH THƯỜNG (không deep link thẳng), rồi tự đi: Hồ sơ -> menu -> Cài đặt
            // và quyền riêng tư -> Chuyển đổi tài khoản -> chọn ĐÚNG acc @$handle -> mới mở
            // deep link để follow (tái sử dụng nguyên luồng dò UI của "check acc tiktok").
            // Các bản khác (Lite/Studio) không có màn này -> giữ hành vi cũ: mở deep link
            // thẳng rồi tự bấm Follow luôn.
            if (variant == com.cayxu.app.data.local.TikTokAppVariant.STANDARD && !packageName.isNullOrBlank()) {
                TikTokAppLauncher.launch(applicationContext, variant)
                com.cayxu.app.automation.tiktok.GolikeSwitchAccountBridge.requestSwitch(
                    targetHandle = handle,
                    followTargetUsername = targetUsername,
                    packageName = packageName,
                    variant = variant
                )
            } else {
                // Mở trang lên, rồi yêu cầu tự bấm Follow (đợi ~5 giây, vuốt xuống tải lại vài
                // lần, tìm và bấm nút Follow). Chỉ có tác dụng nếu người dùng đã bật Accessibility
                // Service của tool; nếu chưa bật thì không có gì xảy ra, không lỗi gì cả.
                openTikTokProfile(applicationContext, targetUsername, packageName)

                if (!packageName.isNullOrBlank()) {
                    com.cayxu.app.automation.tiktok.GolikeFollowBridge.requestFollow(
                        targetUsername = targetUsername,
                        packageName = packageName
                    )
                }
            }

            // Đợi KẾT QUẢ THẬT của luồng tự follow (không phải đợi 1 khoảng cố định đoán
            // chừng) - "đã follow sẵn từ trước" (thấy nút Nhắn tin) hoặc "vừa bấm Follow
            // xong" đều coi là xong, gọi ngay API xác nhận với GoLike. Có timeout an toàn
            // phòng khi Accessibility Service không phản hồi gì (vẫn thử gọi verify, vì
            // GoLike tự kiểm tra thật trên TikTok, không phụ thuộc vào việc mình có tự tin
            // phát hiện được nút hay không).
            serviceScope.launch {
                withTimeoutOrNull(FOLLOW_RESULT_TIMEOUT_MS) {
                    com.cayxu.app.automation.tiktok.GolikeFollowResultBridge.result
                        .filter { it !is com.cayxu.app.automation.tiktok.GolikeFollowResult.Idle }
                        .first()
                }
                com.cayxu.app.automation.tiktok.GolikeFollowResultBridge.clear()

                val token = com.cayxu.app.data.local.GolikeAccountStore.getToken(applicationContext)
                if (token.isNullOrBlank()) return@launch
                val result = com.cayxu.app.data.repository.GolikeVerifyAccountRepository
                    .verifyAccountId(token, handle)
                val message = when (result) {
                    is com.cayxu.app.data.repository.GolikeVerifyAccountResult.Success -> {
                        val extra = listOfNotNull(
                            result.uniqueUsername?.let { "@$it" },
                            result.nickname
                        ).joinToString(" - ")
                        if (extra.isBlank()) result.message else "${result.message} ($extra)"
                    }
                    is com.cayxu.app.data.repository.GolikeVerifyAccountResult.Error -> result.message
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                com.cayxu.app.automation.tiktok.GolikeFollowStatusBridge.update(message)

                if (result is com.cayxu.app.data.repository.GolikeVerifyAccountResult.Success) {
                    // Đã thêm thành công - tự quay lại tool luôn, không cần người dùng tự
                    // bấm nút "↩". Màn danh sách acc TikTok sẽ tự làm mới và ẩn nút "Thêm".
                    TikTokAppLauncher.bringToolToFront(applicationContext)
                    stopSelf()
                }
            }
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

        val statusLabel = TextView(this).apply {
            text = "Trạng thái"
            setTextColor(Color.parseColor("#8A93A6"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(statusLabel)
        val statusValue = TextView(this).apply {
            text = com.cayxu.app.automation.tiktok.GolikeFollowStatusBridge.status.value
                .ifBlank { "tiktok.com/@$targetUsername" }
            setTextColor(Color.parseColor("#C7CBD4"))
            textSize = 12f
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(statusValue)
        statusValueView = statusValue

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
        serviceScope.cancel()
        com.cayxu.app.automation.tiktok.GolikeFollowStatusBridge.clear()
        com.cayxu.app.automation.tiktok.GolikeSwitchAccountBridge.clear()
        fullPanel?.let { runCatching { windowManager.removeView(it) } }
        miniBubble?.let { runCatching { windowManager.removeView(it) } }
        fullPanel = null
        miniBubble = null
    }
}
