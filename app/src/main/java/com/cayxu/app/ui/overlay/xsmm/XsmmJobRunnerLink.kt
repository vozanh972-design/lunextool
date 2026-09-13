package com.cayxu.app.ui.overlay.xsmm

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.data.local.TikTokAppVariant

fun startXsmmJobRunnerOverlay(context: Context, accountHandles: List<String>) {
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
    context.startService(Intent(context, XsmmJobRunnerOverlayService::class.java).apply {
        putExtra(XsmmJobRunnerOverlayService.EXTRA_ACCOUNT_HANDLES, accountHandles.joinToString(","))
    })
}
