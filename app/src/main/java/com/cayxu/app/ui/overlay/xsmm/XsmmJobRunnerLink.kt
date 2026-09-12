package com.cayxu.app.ui.overlay.xsmm

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import com.cayxu.app.automation.tiktok.TikTokAccessibilityService

fun startXsmmJobRunnerOverlay(context: Context, accountHandles: List<String>) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Cần cấp quyền hiển thị trên ứng dụng khác để dùng tính năng này", Toast.LENGTH_LONG).show()
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Máy không hỗ trợ mở màn cấp quyền này", Toast.LENGTH_SHORT).show()
        }
        return
    }
    if (!isAccessibilityEnabledForXsmm(context)) {
        Toast.makeText(context, "Cần bật quyền Trợ năng (Accessibility) cho CayXu để chạy nhiệm vụ", Toast.LENGTH_LONG).show()
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Máy không hỗ trợ mở màn cấp quyền này", Toast.LENGTH_SHORT).show()
        }
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

private fun isAccessibilityEnabledForXsmm(context: Context): Boolean {
    val expected = ComponentName(context, TikTokAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) { if (splitter.next().equals(expected, ignoreCase = true)) return true }
    return false
}
