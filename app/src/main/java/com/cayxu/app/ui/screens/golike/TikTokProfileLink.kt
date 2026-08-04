package com.cayxu.app.ui.screens.golike

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.navigation.NavController
import com.cayxu.app.automation.tiktok.TikTokAccessibilityService
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.overlay.golike.GolikeAddAccountOverlayService
import java.util.concurrent.TimeUnit

/** Tên TikTok GoLike yêu cầu liên kết/theo dõi để hoàn tất thêm tài khoản. */
private const val GOLIKE_LINK_TARGET_USERNAME = "gosen.vietnam"

/**
 * Bấm "Thêm" ở acc chưa có trong GoLike -> BẮT BUỘC đã đăng nhập GoLike trước (chưa đăng
 * nhập thì KHÔNG làm gì thêm, tự chuyển sang màn Đăng nhập GoLike luôn) -> rồi kiểm tra ĐỦ
 * 2 quyền: (1) "Hiển thị trên ứng dụng khác" và (2) "Trợ năng" (Accessibility) cho tool -
 * THIẾU 1 TRONG 2 sẽ KHÔNG mở lớp nổi, mà tự mở đúng màn Cài đặt tương ứng để người dùng
 * bật, rồi họ bấm "Thêm" lại sau khi bật xong. Đủ điều kiện mới khởi chạy lớp nổi
 * (GolikeAddAccountOverlayService): hiển thị thông tin + tự mở link trang cá nhân TikTok,
 * tự đợi ~5 giây, tải lại rồi tự bấm Follow.
 */
fun startAddToGolikeOverlay(context: Context, navController: NavController, account: TikTokAccount) {
    if (!GolikeSession.isLoggedIn.value) {
        Toast.makeText(context, "Cần đăng nhập GoLike trước khi thêm tài khoản", Toast.LENGTH_LONG).show()
        navController.navigate(Routes.GOLIKE_LOGIN)
        return
    }

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

    if (!isAccessibilityServiceEnabled(context)) {
        Toast.makeText(
            context,
            "Cần bật quyền Trợ năng (Accessibility) cho CayXu để tự bấm Follow - hãy tìm và bật CayXu trong danh sách",
            Toast.LENGTH_LONG
        ).show()
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

/** Kiểm tra Accessibility Service của tool (TikTokAccessibilityService) đã được người dùng
 *  bật trong Cài đặt máy chưa - đọc danh sách "enabled_accessibility_services" của hệ thống
 *  (cách chuẩn của Android, không có API kiểu isEnabled() trực tiếp). */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, TikTokAccessibilityService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
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
