package com.cayxu.app.ui.screens.golike

import androidx.compose.runtime.mutableStateOf

/**
 * Trạng thái đăng nhập Golike - CHỈ lưu tạm trong bộ nhớ (không phải đăng nhập thật, chưa có
 * API Golike). Dùng chung 1 nguồn duy nhất để card trạng thái hiển thị NHẤT QUÁN ở mọi màn
 * (GolikeScreen, GolikePlatformScreen...) thay vì mỗi màn tự vẽ lại card riêng.
 */
object GolikeSession {
    val isLoggedIn = mutableStateOf(false)
}
