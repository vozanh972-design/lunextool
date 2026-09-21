package com.cayxu.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cayxu.app.MainActivity
import com.cayxu.app.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Quản lý gửi thông báo cảnh báo (Alert Notification) lên thanh trạng thái điện thoại
 * khi tài khoản chạy nhiều nhiệm vụ bị lỗi liên tiếp (>= 3 lỗi hoặc bị dừng do lỗi).
 *
 * Người dùng vuốt màn hình xuống sẽ thấy ngay tài khoản nào đang lỗi,
 * lỗi gì và bao nhiêu lần để kịp thời sửa (đổi cookie, gỡ checkpoint, kiểm tra mạng...).
 *
 * Có thể bật/tắt trong Cài đặt > "Thông báo đẩy".
 */
object AppAlertNotifier {

    const val CHANNEL_ID = "cayxu_account_error_alerts"
    private const val PREF_NAME = "app_alert_prefs"
    private const val KEY_PUSH_ENABLED = "push_notifications_enabled"

    // Chống spam thông báo: lưu thời điểm thông báo gần nhất cho từng tài khoản
    // key: "${platform}_${accountUid}" -> timestamp
    private val lastAlertTimes = ConcurrentHashMap<String, Long>()
    private val lastAlertCounts = ConcurrentHashMap<String, Int>()

    fun isPushEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PUSH_ENABLED, true)
    }

    fun setPushEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PUSH_ENABLED, enabled)
            .apply()
    }

    /**
     * Tạo NotificationChannel có độ ưu tiên cao (IMPORTANCE_HIGH) để:
     * - Phát âm thanh / rung
     * - Hiện banner thông báo nổi lên màn hình
     * - Giữ trong thanh thông báo khi vuốt xuống
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cảnh báo lỗi tài khoản",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi tài khoản chạy bị lỗi liên tiếp nhiều nhiệm vụ"
                enableVibration(true)
                setShowBadge(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Gửi thông báo cảnh báo lỗi tài khoản lên thanh thông báo.
     *
     * @param context Context ứng dụng
     * @param platform Tên nền tảng (VD: "Facebook", "Instagram", "TTC")
     * @param accountName Tên hiển thị của tài khoản
     * @param accountUid UID hoặc username của tài khoản
     * @param consecutiveErrors Số lỗi liên tiếp
     * @param errorDetail Nội dung chi tiết lỗi
     */
    fun notifyAccountError(
        context: Context,
        platform: String,
        accountName: String,
        accountUid: String,
        consecutiveErrors: Int,
        errorDetail: String
    ) {
        if (!isPushEnabled(context)) return
        if (consecutiveErrors < 3) return // Chỉ báo khi bị lỗi từ 3 job liên tiếp trở lên

        val cleanKey = "${platform.trim().lowercase()}_${accountUid.trim().lowercase()}"
        val now = System.currentTimeMillis()
        val lastTime = lastAlertTimes[cleanKey] ?: 0L
        val lastCount = lastAlertCounts[cleanKey] ?: 0

        // Chống spam: nếu cùng số lỗi và trong vòng 60 giây thì không gửi lại
        if (consecutiveErrors == lastCount && (now - lastTime) < 60_000L) {
            return
        }
        // Giãn cách tối thiểu giữa 2 lần báo cho cùng 1 acc là 20 giây
        if ((now - lastTime) < 20_000L) {
            return
        }

        lastAlertTimes[cleanKey] = now
        lastAlertCounts[cleanKey] = consecutiveErrors

        createChannel(context)

        // Bấm vào thông báo -> Mở ứng dụng
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            cleanKey.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = accountName.ifBlank { accountUid }
        val title = "⚠️ Cảnh báo: $platform [$displayName] lỗi liên tiếp $consecutiveErrors job"

        // Rút gọn chi tiết lỗi cho thông báo ngắn
        val shortError = errorDetail.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("•") }
            ?: errorDetail.lines().firstOrNull { it.isNotBlank() }
            ?: "Thao tác nhiệm vụ thất bại"

        val summaryText = "Đã lỗi liên tiếp $consecutiveErrors job: $shortError. Chạm để mở app kiểm tra!"

        val bigText = buildString {
            append("• Tài khoản: $displayName\n")
            if (accountUid != displayName && accountUid.isNotBlank()) {
                append("• UID: $accountUid\n")
            }
            append("• Nền tảng: $platform\n")
            append("• Số job lỗi liên tiếp: $consecutiveErrors\n")
            append("• Chi tiết lỗi: $shortError\n\n")
            append("👉 Vui lòng kiểm tra lại cookie, token hoặc trạng thái tài khoản để tiếp tục chạy!")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Mỗi tài khoản có 1 ID thông báo riêng để không ghi đè thông báo của tài khoản khác
        val notificationId = 20000 + (cleanKey.hashCode() and 0x7FFFFFFF % 100000)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(notificationId, notification)
        } catch (_: Exception) {}
    }
}
