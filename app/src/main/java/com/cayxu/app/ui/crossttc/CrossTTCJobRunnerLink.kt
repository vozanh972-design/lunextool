package com.cayxu.app.ui.crossttc

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.cayxu.app.automation.tiktok.TikTokAppLauncher

/**
 * Quản lý khởi động màn nổi (Overlay) và kiểm tra quyền vẽ đè + trợ năng cho TikTok TTC.
 * Hoàn toàn độc lập trong package crossttc, không can thiệp hay phụ thuộc nền tảng khác.
 */
object CrossTTCJobRunnerLink {

    fun startTTCJobRunner(
        context: Context,
        accountHandles: List<String>,
        sessionCookie: String,
        accessToken: String,
    ) {
        if (!TikTokAppLauncher.isOverlayPermissionGranted(context)) {
            Toast.makeText(context, "Cần cấp quyền hiển thị trên ứng dụng khác để mở lớp nổi", Toast.LENGTH_LONG).show()
            TikTokAppLauncher.openOverlayPermissionSettings(context)
            return
        }
        if (!TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
            Toast.makeText(context, "Cần bật quyền Trợ năng (Accessibility) cho CayXu để tự động chạy nhiệm vụ", Toast.LENGTH_LONG).show()
            TikTokAppLauncher.openAccessibilitySettings(context)
            return
        }
        if (accountHandles.isEmpty()) {
            Toast.makeText(context, "Hãy tick chọn ít nhất 1 tài khoản để chạy", Toast.LENGTH_SHORT).show()
            return
        }

        context.startService(Intent(context, CrossTTCOverlayService::class.java).apply {
            putExtra(CrossTTCOverlayService.EXTRA_ACCOUNT_HANDLES, accountHandles.joinToString(","))
            putExtra(CrossTTCOverlayService.EXTRA_SESSION_COOKIE, sessionCookie)
            putExtra(CrossTTCOverlayService.EXTRA_ACCESS_TOKEN, accessToken)
        })
    }
}
