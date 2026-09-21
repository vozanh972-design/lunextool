package com.cayxu.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver xử lý 2 sự kiện:
 *
 *  1. BOOT_COMPLETED — [D] điện thoại khởi động lại → tự bật lại service nếu đã bật trước đó
 *  2. ACTION_WATCHDOG_RESTART — AlarmManager gọi lại → [B][C] restart service bị kill/vuốt
 *
 * Đăng ký trong AndroidManifest với 2 intent-filter tương ứng.
 */
class AppRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        // Chỉ restart nếu người dùng đã bật toggle "Chạy ngầm"
        // (tránh tự động chạy khi người dùng chưa từng bật)
        if (!AppBackgroundService.isEnabled(context)) return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",   // HTC/Asus variant
            "com.htc.intent.action.QUICKBOOT_POWERON",   // HTC variant
            "com.cayxu.app.ACTION_WATCHDOG_RESTART" -> {
                AppBackgroundService.launchService(context)
            }
        }
    }
}
