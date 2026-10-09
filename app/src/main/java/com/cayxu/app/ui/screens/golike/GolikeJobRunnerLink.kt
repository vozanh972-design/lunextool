package com.cayxu.app.ui.screens.golike

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.overlay.xsmm.XsmmJobRunnerOverlayService

/**
 * Khởi chạy màn nổi overlay riêng biệt cho module Golike TikTok.
 * Đảm bảo truyền EXTRA_PLATFORM = "golike" và EXTRA_MODE rõ ràng,
 * hiển thị màu cam (#FF7A00), đơn vị "đ", điều phối trực tiếp GolikeTikTokTaskRunner.
 */
fun startGolikeVerifyTikTokAccount(
    context: Context,
    variant: TikTokAppVariant,
    handle: String = ""
) {
    if (!TikTokAppLauncher.isOverlayPermissionGranted(context)) {
        Toast.makeText(context, "Cần cấp quyền hiển thị trên ứng dụng khác để mở lớp nổi", Toast.LENGTH_LONG).show()
        TikTokAppLauncher.openOverlayPermissionSettings(context)
        return
    }
    if (!TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
        Toast.makeText(context, "Cần bật quyền Trợ năng (Accessibility) cho CayXu để tự động kiểm tra", Toast.LENGTH_LONG).show()
        TikTokAppLauncher.openAccessibilitySettings(context)
        return
    }
    context.startService(Intent(context, XsmmJobRunnerOverlayService::class.java).apply {
        putExtra("extra_platform", "golike")
        putExtra("extra_mode", "verify_only")
        putExtra(XsmmJobRunnerOverlayService.EXTRA_PLATFORM, XsmmJobRunnerOverlayService.PLATFORM_GOLIKE)
        putExtra(XsmmJobRunnerOverlayService.EXTRA_MODE, XsmmJobRunnerOverlayService.MODE_VERIFY_ONLY)
        putExtra(XsmmJobRunnerOverlayService.EXTRA_VARIANT, variant.name)
        putExtra(XsmmJobRunnerOverlayService.EXTRA_ACCOUNT_HANDLES, handle)
    })
}

fun startGolikeJobRunnerOverlay(
    context: Context,
    accountHandles: List<String>,
    variant: TikTokAppVariant = TikTokAppVariant.STANDARD
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
    context.startService(Intent(context, XsmmJobRunnerOverlayService::class.java).apply {
        putExtra("extra_platform", "golike")
        putExtra("extra_mode", "golike")
        putExtra(XsmmJobRunnerOverlayService.EXTRA_PLATFORM, XsmmJobRunnerOverlayService.PLATFORM_GOLIKE)
        putExtra(XsmmJobRunnerOverlayService.EXTRA_MODE, XsmmJobRunnerOverlayService.MODE_RUN_JOBS)
        putExtra(XsmmJobRunnerOverlayService.EXTRA_VARIANT, variant.name)
        putExtra(XsmmJobRunnerOverlayService.EXTRA_ACCOUNT_HANDLES, accountHandles.joinToString(","))
    })
}
