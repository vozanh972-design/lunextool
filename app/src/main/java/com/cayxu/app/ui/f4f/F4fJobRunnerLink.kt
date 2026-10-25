package com.cayxu.app.ui.f4f

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.data.local.TikTokAppVariant

/**
 * Cầu nối kích hoạt Màn nổi độc lập của Follow4Follow.
 */
fun startF4fJobRunnerOverlay(
    context: Context,
    accountHandles: List<String>,
    variant: TikTokAppVariant = TikTokAppVariant.STANDARD
) {
    if (!TikTokAppLauncher.isOverlayPermissionGranted(context)) {
        Toast.makeText(context, "Cần cấp quyền hiển thị trên ứng dụng khác để mở màn nổi", Toast.LENGTH_LONG).show()
        TikTokAppLauncher.openOverlayPermissionSettings(context)
        return
    }
    if (!TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
        Toast.makeText(context, "Cần bật quyền Trợ năng (Accessibility) để tự động làm việc", Toast.LENGTH_LONG).show()
        TikTokAppLauncher.openAccessibilitySettings(context)
        return
    }
    if (accountHandles.isEmpty()) {
        Toast.makeText(context, "Hãy tick chọn ít nhất 1 tài khoản để chạy", Toast.LENGTH_SHORT).show()
        return
    }
    context.startService(Intent(context, F4fJobRunnerOverlayService::class.java).apply {
        putExtra(F4fJobRunnerOverlayService.EXTRA_VARIANT, variant.name)
        putExtra(F4fJobRunnerOverlayService.EXTRA_ACCOUNT_HANDLES, accountHandles.joinToString(","))
    })
}

fun stopF4fJobRunnerOverlay(context: Context) {
    context.stopService(Intent(context, F4fJobRunnerOverlayService::class.java))
}
