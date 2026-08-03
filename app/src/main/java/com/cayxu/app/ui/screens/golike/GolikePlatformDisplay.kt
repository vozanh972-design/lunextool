package com.cayxu.app.ui.screens.golike

import androidx.compose.ui.graphics.Color

/** Tên hiển thị đẹp cho từng key nền tảng GoLike trả về (facebook/instagram/tiktok/...). */
fun golikePlatformDisplayName(key: String): String = when (key) {
    "facebook" -> "Facebook"
    "instagram" -> "Instagram"
    "tiktok" -> "TikTok"
    "shopee" -> "Shopee"
    "twitter" -> "Twitter/X"
    "lazada" -> "Lazada"
    "youtube" -> "YouTube"
    "traffic" -> "Traffic"
    "review" -> "Review"
    "threads" -> "Threads"
    "linkedin" -> "LinkedIn"
    "snapchat" -> "Snapchat"
    "pinterest" -> "Pinterest"
    "bluesky" -> "Bluesky"
    "tumblr" -> "Tumblr"
    "soundcloud" -> "SoundCloud"
    else -> key.replaceFirstChar { it.uppercase() }
}

/** Màu đại diện cho từng nền tảng - dùng cho icon tròn/thanh biểu đồ. */
fun golikePlatformColor(key: String): Color = when (key) {
    "facebook" -> Color(0xFF1877F2)
    "instagram" -> Color(0xFFE1306C)
    "tiktok" -> Color(0xFF25F4EE)
    "shopee" -> Color(0xFFEE4D2D)
    "twitter" -> Color(0xFF1D9BF0)
    "lazada" -> Color(0xFF0F146D)
    "youtube" -> Color(0xFFFF0000)
    "traffic" -> Color(0xFF16A34A)
    "review" -> Color(0xFFF59E0B)
    "threads" -> Color(0xFF000000)
    "linkedin" -> Color(0xFF0A66C2)
    "snapchat" -> Color(0xFFFFFC00)
    "pinterest" -> Color(0xFFE60023)
    "bluesky" -> Color(0xFF1185FE)
    "tumblr" -> Color(0xFF34526F)
    "soundcloud" -> Color(0xFFFF5500)
    else -> Color(0xFF7C3AED)
}
