package com.cayxu.app.worker

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cayxu.app.MainActivity
import com.cayxu.app.R

/**
 * Foreground Service chạy ngầm — CỰC KỲ BỀN, KHÔNG VĂNG APP.
 *
 * Chỉ có thể dừng bằng 1 trong 2 cách:
 *   1. Tắt toggle "Chạy ngầm" trong Cài đặt  (gọi stop() → saveEnabled(false))
 *   2. Buộc dừng (Force Stop) trong Cài đặt hệ thống
 *
 * Các cơ chế bảo vệ:
 *   [A] START_STICKY         → Android tự restart khi bị kill do thiếu RAM
 *   [B] onTaskRemoved()      → Tự khởi động lại ngay khi user vuốt app ra khỏi Recent
 *   [C] AlarmManager watchdog→ 5 giây sau khi destroy, AlarmManager ping lại để restart
 *   [D] BootReceiver         → Tự bật lại sau khi điện thoại khởi động lại
 *   [E] Foreground priority  → Ưu tiên cao nhất, Android ít kill nhất có thể
 */
class AppBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID    = "cayxu_background_channel"
        const val NOTIFICATION_ID = 9001
        const val PREF_NAME     = "app_background_pref"
        const val KEY_ENABLED   = "background_service_enabled"

        // Intent action dùng cho AlarmManager watchdog restart
        private const val ACTION_WATCHDOG_RESTART = "com.cayxu.app.ACTION_WATCHDOG_RESTART"

        fun start(context: Context) {
            saveEnabled(context, true)
            launchService(context)
        }

        fun stop(context: Context) {
            saveEnabled(context, false)
            cancelWatchdog(context)
            try {
                context.stopService(Intent(context, AppBackgroundService::class.java))
            } catch (_: Exception) {}
        }

        fun isEnabled(context: Context): Boolean {
            return try {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENABLED, false)
            } catch (_: Exception) {
                false
            }
        }

        fun saveEnabled(context: Context, enabled: Boolean) {
            try {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, enabled).apply()
            } catch (_: Exception) {}
        }

        fun launchService(context: Context) {
            try {
                val intent = Intent(context, AppBackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Fallback nếu startForegroundService bị hạn chế bởi hệ điều hành
                try {
                    val intent = Intent(context, AppBackgroundService::class.java)
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        // Đặt AlarmManager sẽ restart service sau delayMs milliseconds
        fun scheduleWatchdog(context: Context, delayMs: Long = 5_000L) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val pi = getWatchdogPendingIntent(context) ?: return
                val triggerAt = SystemClock.elapsedRealtime() + delayMs

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Trên Android 12+, kiểm tra quyền exact alarm trước để tránh SecurityException
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            } catch (_: Exception) {
                try {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    val pi = getWatchdogPendingIntent(context) ?: return
                    alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
                } catch (_: Exception) {}
            }
        }

        private fun cancelWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                getWatchdogPendingIntent(context)?.let { alarmManager?.cancel(it) }
            } catch (_: Exception) {}
        }

        private fun getWatchdogPendingIntent(context: Context): PendingIntent? {
            return try {
                val intent = Intent(context, AppRestartReceiver::class.java).apply {
                    action = ACTION_WATCHDOG_RESTART
                }
                PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    // -------- Lifecycle --------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback an toàn tuyệt đối
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {}
        // [A] START_STICKY: Android tự restart sau khi kill
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // [B] User vuốt app ra khỏi Recent → lên lịch restart ngay sau 3 giây
        if (isEnabled(this)) {
            scheduleWatchdog(this, delayMs = 3_000L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // [C] Bị destroy vì bất cứ lý do gì (trừ stop() thủ công) → đặt alarm restart
        if (isEnabled(this)) {
            scheduleWatchdog(this, delayMs = 5_000L)
        }
        // Xóa notification khi stop() được gọi thủ công (isEnabled=false)
        if (!isEnabled(this)) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }

    // -------- Notification --------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Chạy ngầm",
                    NotificationManager.IMPORTANCE_LOW   // không phát âm thanh
                ).apply {
                    description = "App đang chạy nền để duy trì tác vụ tự động"
                    setShowBadge(false)
                }
                (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.createNotificationChannel(channel)
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appName = try {
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) { "CayXu" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)      // Vector drawable chuẩn, không bị crash do adaptive-icon
            .setContentTitle(appName)
            .setContentText("Đang chạy nền – chạm để mở lại ứng dụng")
            .setSubText("Chạy ngầm đang bật")
            .setContentIntent(openIntent)
            .setOngoing(true)           // không thể vuốt xóa notification
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
