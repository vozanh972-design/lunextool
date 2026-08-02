package com.cayxu.app.ui.screens.golike

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

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
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(preferredPackage))
            return
        } catch (e: ActivityNotFoundException) {
            // App đó không xử lý được link này (có thể chưa cài) - thử lại không ép package.
        }
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Không tìm thấy ứng dụng để mở link này", Toast.LENGTH_SHORT).show()
    }
}
