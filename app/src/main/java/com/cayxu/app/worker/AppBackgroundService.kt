package com.cayxu.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cayxu.app.MainActivity
import com.cayxu.app.R

/**
 * Foreground Service chạy ngầm.
 * Khi bật "Chạy ngầm" trong Cài đặt → service này được khởi động.
 * Service giữ cho app không bị kill khi người dùng vuốt thoát, và hiển thị
 * notification persistent ở thanh thông báo (vuốt màn hình xuống sẽ thấy).
 *
 * Cách dùng:
 *   AppBackgroundService.start(context)   // bật
 *   AppBackgroundService.stop(context)    // tắt
 *   AppBackgroundService.isEnabled(context) // kiểm tra trạng thái đã lưu
 */
class AppBackgroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "cayxu_background_channel"
        private const val NOTIFICATION_ID = 9001
        private const val PREF_NAME = "app_background_pref"
        private const val KEY_ENABLED = "background_service_enabled"

        fun start(context: Context) {
            saveEnabled(context, true)
            val intent = Intent(context, AppBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            saveEnabled(context, false)
            val intent = Intent(context, AppBackgroundService::class.java)
            context.stopService(intent)
        }

        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ENABLED, false)
        }

        private fun saveEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Cập nhật notification mới nhất
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
        // START_STICKY: Android sẽ tự khởi động lại service nếu bị kill
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    // ------- Private -------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chạy ngầm",
                NotificationManager.IMPORTANCE_LOW   // IMPORTANCE_LOW = không phát âm thanh
            ).apply {
                description = "App đang chạy nền để duy trì tác vụ tự động"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Tap vào notification → mở lại MainActivity
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appName = try {
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) { "CayXu" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)          // icon nhỏ góc trên
            .setContentTitle(appName)
            .setContentText("Đang chạy nền – chạm để mở lại ứng dụng")
            .setSubText("Chạy ngầm đang bật")
            .setContentIntent(openIntent)
            .setOngoing(true)                            // không thể vuốt xóa
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
