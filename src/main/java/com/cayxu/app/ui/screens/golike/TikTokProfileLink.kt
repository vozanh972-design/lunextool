package com.cayxu.app.ui.screens.golike

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.ui.overlay.golike.GolikeAddAccountOverlayService
import java.util.concurrent.TimeUnit

/** Tên TikTok GoLike yêu cầu liên kết/theo dõi để hoàn tất thêm tài khoản. */
private const val GOLIKE_LINK_TARGET_USERNAME = "gosen.vietnam"

/**
 * Bấm "Thêm" ở acc chưa có trong GoLike -> xin quyền "Hiển thị trên ứng dụng khác" (nếu
 * chưa có) rồi khởi chạy lớp nổi (GolikeAddAccountOverlayService). Lớp nổi CHỈ hiển thị
 * thông tin + tự mở link trang cá nhân TikTok - KHÔNG dùng Accessibility Service, KHÔNG tự
 * bấm Follow hay bất kỳ thao tác nào thay người dùng.
 */
fun startAddToGolikeOverlay(context: Context, account: TikTokAccount) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Cần cấp quyền hiển thị trên ứng dụng khác để dùng tính năng này", Toast.LENGTH_LONG).show()
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Máy không hỗ trợ mở màn cấp quyền này", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val monthsAgo = (TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - account.createdAt) / 30).toInt()

    val intent = Intent(context, GolikeAddAccountOverlayService::class.java).apply {
        putExtra(GolikeAddAccountOverlayService.EXTRA_HANDLE, account.handle)
        putExtra(GolikeAddAccountOverlayService.EXTRA_CREATED_MONTHS_AGO, monthsAgo)
        putExtra(GolikeAddAccountOverlayService.EXTRA_TARGET_USERNAME, GOLIKE_LINK_TARGET_USERNAME)
        putExtra(GolikeAddAccountOverlayService.EXTRA_PACKAGE_NAME, account.variant.packageName)
    }
    context.startService(intent)
}

/**
 * Mở thẳng trang cá nhân TikTok của 1 username bằng Intent ACTION_VIEW (deep link chuẩn
 * https://www.tiktok.com/@username).
 *
 * CHỦ Ý: hàm này CHỈ điều hướng tới đúng trang - KHÔNG dùng Accessibility Service, KHÔNG
 * tự động thao tác gì bên trong TikTok (không tự bấm Follow thay người dùng). Người dùng
 * vẫn là người tự tay bấm Follow nếu muốn, giống hệt việc họ tự mở link đó trên điện thoại.
 *
 * [preferredPackage] (không bắt buộc) là package của đúng app TikTok/Lite/Studio ứng với
 * tài khoản đang thao tác - ưu tiên mở bằng đúng app đó nếu có cài; nếu không mở được (app
 * đó chưa cài, hoặc không xử lý được link dạng này) thì để hệ thống tự chọn ứng dụng khác
 * phù hợp (trình duyệt hoặc TikTok bất kỳ đã cài).
 */
fun openTikTokProfile(context: Context, username: String, preferredPackage: String? = null) {
    val uri = Uri.parse("https://www.tiktok.com/@${username.removePrefix("@")}")

    if (preferredPackage != null) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(preferredPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // App đó không xử lý được link này (có thể chưa cài) - thử lại không ép package.
        }
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Không tìm thấy ứng dụng để mở link này", Toast.LENGTH_SHORT).show()
    }
}
