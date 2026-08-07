package com.cayxu.app.ui.overlay.golike

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lớp nổi hiện khi bấm "Chạy" để làm nhiệm vụ (job) - vd follow/like trên 1 nền tảng nào đó
 * (Instagram, TikTok...) để kiếm tiền trong CayXu.
 *
 * QUAN TRỌNG: Service này CHỈ LO PHẦN GIAO DIỆN (đúng bố cục ảnh mẫu người dùng cung cấp).
 * KHÔNG có logic tự chạy job/gọi API lấy job/kiểm tra hoàn thành job ở đây - vì hệ thống đó
 * (API lấy job kế tiếp, API "kiểm tra bạn chưa làm việc", cách thao tác trên từng nền tảng...)
 * CHƯA tồn tại trong project. Mọi nội dung hiển thị đều nhận qua Intent extras (KHÔNG hardcode
 * dữ liệu mẫu như "lyly57892", "20 N.vụ", link Instagram... - đó chỉ là ví dụ trong ảnh mẫu).
 * Khi có hệ thống job thật, chỉ cần start Service này với đúng extras + cập nhật trạng thái
 * qua GolikeJobStatusBridge.update(...) là hiển thị đúng như ảnh mẫu, không cần sửa UI.
 */
class GolikeJobRunnerOverlayService : Service() {

    companion object {
        /** Tên chế độ hiện tại, vd "Làm NV" (hiện màu xanh, bên phải dòng "Chế độ"). */
        const val EXTRA_MODE_LABEL = "extra_mode_label"

        /** @handle của tài khoản đang dùng để chạy job, vd "lyly57892" (không cần dấu @, tự thêm). */
        const val EXTRA_ACCOUNT_HANDLE = "extra_account_handle"

        /** Tổng số nhiệm vụ acc này đã/đang làm, vd 20 -> hiện "20 N.vụ". */
        const val EXTRA_ACCOUNT_TASK_COUNT = "extra_account_task_count"

        /** ID phụ hiện dưới dòng Tài khoản (vd device id/session id) - để trống nếu không có. */
        const val EXTRA_ACCOUNT_SECONDARY_ID = "extra_account_secondary_id"

        /** Mã job đang làm, vd "622999" -> hiện "#622999". */
        const val EXTRA_JOB_ID = "extra_job_id"

        /** Loại job, vd "follow", "like"... */
        const val EXTRA_JOB_TYPE = "extra_job_type"

        /** Giá tiền/job, vd "25đ". */
        const val EXTRA_JOB_PRICE = "extra_job_price"

        /** Số job đã làm THÀNH CÔNG trong phiên chạy này (hiện màu xanh cạnh dấu ✓). */
        const val EXTRA_JOB_SUCCESS_COUNT = "extra_job_success_count"

        /** Số job làm THẤT BẠI trong phiên chạy này (hiện màu đỏ cạnh dấu ✗). */
        const val EXTRA_JOB_FAIL_COUNT = "extra_job_fail_count"

        /** Tổng tiền đã kiếm được trong phiên chạy này, vd "250đ" (hiện màu xanh lá). */
        const val EXTRA_JOB_EARNED = "extra_job_earned"

        /** Link job đang làm (vd link bài Instagram cần follow) - bấm vào sẽ mở link này. */
        const val EXTRA_JOB_LINK = "extra_job_link"

        /** Trạng thái/lỗi ban đầu (có thể để trống, cập nhật sau qua GolikeJobStatusBridge). */
        const val EXTRA_INITIAL_STATUS = "extra_initial_status"
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
            val modeLabel = intent?.getStringExtra(EXTRA_MODE_LABEL).orEmpty().ifBlank { "Làm NV" }
            val accountHandle = intent?.getStringExtra(EXTRA_ACCOUNT_HANDLE).orEmpty()
            val accountTaskCount = intent?.getIntExtra(EXTRA_ACCOUNT_TASK_COUNT, 0) ?: 0
            val accountSecondaryId = intent?.getStringExtra(EXTRA_ACCOUNT_SECONDARY_ID).orEmpty()
            val jobId = intent?.getStringExtra(EXTRA_JOB_ID).orEmpty()
            val jobType = intent?.getStringExtra(EXTRA_JOB_TYPE).orEmpty()
            val jobPrice = intent?.getStringExtra(EXTRA_JOB_PRICE).orEmpty()
            val jobSuccessCount = intent?.getIntExtra(EXTRA_JOB_SUCCESS_COUNT, 0) ?: 0
            val jobFailCount = intent?.getIntExtra(EXTRA_JOB_FAIL_COUNT, 0) ?: 0
            val jobEarned = intent?.getStringExtra(EXTRA_JOB_EARNED).orEmpty()
            val jobLink = intent?.getStringExtra(EXTRA_JOB_LINK).orEmpty()
            val initialStatus = intent?.getStringExtra(EXTRA_INITIAL_STATUS).orEmpty()

            GolikeJobStatusBridge.update(initialStatus)

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            showFullPanel(
                modeLabel, accountHandle, accountTaskCount, accountSecondaryId,
                jobId, jobType, jobPrice, jobSuccessCount, jobFailCount, jobEarned, jobLink
            )

            // Hiện LIVE trạng thái/lỗi ngay tại dòng dưới link job - cùng cơ chế
            // GolikeFollowStatusBridge đã dùng cho màn nổi "Thêm".
            serviceScope.launch {
                GolikeJobStatusBridge.status.collect { text ->
                    statusValueView?.text = text
                    (statusValueView?.parent as? View)?.visibility =
                        if (text.isBlank()) View.GONE else View.VISIBLE
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** Đọc ĐÚNG version thật của app đang cài (KHÔNG hardcode "v1.4" như ảnh mẫu - ảnh mẫu
     *  chỉ là ví dụ, version thật phải luôn khớp với bản app đang chạy). */
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

    private fun showFullPanel(
        modeLabel: String,
        accountHandle: String,
        accountTaskCount: Int,
        accountSecondaryId: String,
        jobId: String,
        jobType: String,
        jobPrice: String,
        jobSuccessCount: Int,
        jobFailCount: Int,
        jobEarned: String,
        jobLink: String
    ) {
        miniBubble?.let { runCatching { windowManager.removeView(it) } }
        miniBubble = null

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

        // ---- Header: logo + tên app + version thật (KHÔNG có badge "FREE") ----
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

        headerRow.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        headerRow.addView(circleIconButton("\u21A9", Color.parseColor("#2E4C8DFF"), Color.parseColor("#4C8DFF")) {
            TikTokAppLauncher.bringToolToFront(applicationContext)
        })
        headerRow.addView(spacerDp(8))
        headerRow.addView(circleIconButton("\u2013", Color.parseColor("#2E2E38"), Color.parseColor("#C7CBD4")) {
            showMiniBubble(
                modeLabel, accountHandle, accountTaskCount, accountSecondaryId,
                jobId, jobType, jobPrice, jobSuccessCount, jobFailCount, jobEarned, jobLink
            )
        })
        headerRow.addView(spacerDp(8))
        headerRow.addView(circleIconButton("\u2715", Color.parseColor("#3DFF5252"), Color.parseColor("#FF6B6B")) {
            exitToolCompletely()
        })

        root.addView(headerRow)
        root.addView(dividerView())

        // ---- Chế độ ----
        root.addView(infoRow("Chế độ", modeLabel, Color.parseColor("#4C8DFF")))

        // ---- Tài khoản (+ id phụ ở dòng dưới, nếu có) ----
        val taskCountText = if (accountTaskCount > 0) " · $accountTaskCount N.vụ" else ""
        root.addView(infoRow("Tài khoản", "@$accountHandle$taskCountText", Color.WHITE))
        if (accountSecondaryId.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = accountSecondaryId
                setTextColor(Color.parseColor("#5B6272"))
                textSize = 11f
                setPadding(0, 0, 0, dp(4))
            })
        }

        // ---- #job · loại · giá (nếu có) ----
        val jobMetaParts = listOfNotNull(
            jobId.takeIf { it.isNotBlank() }?.let { "#$it" },
            jobType.takeIf { it.isNotBlank() },
            jobPrice.takeIf { it.isNotBlank() }
        )
        if (jobMetaParts.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = jobMetaParts.joinToString(" · ")
                setTextColor(Color.parseColor("#C7CBD4"))
                textSize = 12f
                gravity = Gravity.END
                setPadding(0, 0, 0, dp(8))
            })
        }

        // ---- Job: ✓ thành công (xanh) · ✗ thất bại (đỏ) ..... tiền kiếm được (xanh lá) ----
        val jobLabel = TextView(this).apply {
            text = "Job"
            setTextColor(Color.parseColor("#8A93A6"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(jobLabel)

        val jobCountsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        jobCountsRow.addView(TextView(this).apply {
            text = "\u2713 $jobSuccessCount"
            setTextColor(Color.parseColor("#4C8DFF"))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        jobCountsRow.addView(spacerDp(14))
        jobCountsRow.addView(TextView(this).apply {
            text = "\u2717 $jobFailCount"
            setTextColor(Color.parseColor("#FF6B6B"))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        jobCountsRow.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        if (jobEarned.isNotBlank()) {
            jobCountsRow.addView(TextView(this).apply {
                text = jobEarned
                setTextColor(Color.parseColor("#4ADE80"))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        root.addView(
            jobCountsRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
        )

        root.addView(dividerView())

        // ---- Link job đang làm (bấm vào mở link thật) ----
        if (jobLink.isNotBlank()) {
            val linkRow = TextView(this).apply {
                text = "\u2197  $jobLink"
                setTextColor(Color.parseColor("#4C8DFF"))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(jobLink))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            root.addView(linkRow)
            root.addView(dividerView())
        }

        // ---- Dòng trạng thái/lỗi LIVE (ẩn nếu rỗng) - xem GolikeJobStatusBridge ----
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        val statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4C8DFF"))
            }
        }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(7), dp(7)).apply {
            topMargin = dp(5)
            rightMargin = dp(8)
        })
        val statusText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#C7CBD4"))
            textSize = 12.5f
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusValueView = statusText
        root.addView(
            statusRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
                topMargin = dp(6)
            }
        )
        // Trạng thái ban đầu (nếu có) - cập nhật ngay không cần chờ collector chạy xong.
        val current = GolikeJobStatusBridge.status.value
        statusText.text = current
        statusRow.visibility = if (current.isBlank()) View.GONE else View.VISIBLE

        // ---- Nút TẠM DỪNG ----
        val pauseBtn = TextView(this).apply {
            text = "\u25A0  TẠM DỪNG"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F2534A"))
            }
            setPadding(0, dp(14), 0, dp(14))
            // Chưa có hệ thống job thật để "tạm dừng rồi tiếp tục" - hiện tại bấm vào sẽ
            // dừng hẳn màn nổi này (giống nút DỪNG ở màn "Thêm"). Khi có hệ thống job thật,
            // đổi hàm này để chỉ tạm dừng vòng lặp job, không đóng màn nổi.
            setOnClickListener { stopSelf() }
        }
        root.addView(
            pauseBtn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        )

        makeDraggable(root, params)
        windowManager.addView(root, params)
        fullPanel = root
    }

    private fun showMiniBubble(
        modeLabel: String,
        accountHandle: String,
        accountTaskCount: Int,
        accountSecondaryId: String,
        jobId: String,
        jobType: String,
        jobPrice: String,
        jobSuccessCount: Int,
        jobFailCount: Int,
        jobEarned: String,
        jobLink: String
    ) {
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
                    if (!isDrag) {
                        showFullPanel(
                            modeLabel, accountHandle, accountTaskCount, accountSecondaryId,
                            jobId, jobType, jobPrice, jobSuccessCount, jobFailCount, jobEarned, jobLink
                        )
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        miniBubble = bubble
    }

    private fun exitToolCompletely() {
        stopSelf()
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
        GolikeJobStatusBridge.clear()
        fullPanel?.let { runCatching { windowManager.removeView(it) } }
        miniBubble?.let { runCatching { windowManager.removeView(it) } }
        fullPanel = null
        miniBubble = null
    }
}
