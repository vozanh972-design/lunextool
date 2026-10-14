package com.cayxu.app.ui.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.locale.Str
import com.cayxu.app.ui.profile.ContactChannel
import com.cayxu.app.ui.profile.ContactKind

/*
 * Tấm "Liên hệ hỗ trợ" dùng cho Trang chủ (nút tai nghe). Cùng giao diện với tấm ở Hồ sơ, nhưng đứng riêng để Trang chủ
 * không phụ thuộc ProfileScreen. Chỉ trình bày: link nằm trong [ContactChannel.url], không hiện ra màn hình.
 */

private val Plane = listOf("M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z")
private val Chevron = listOf("M9 6l6 6-6 6")

val DefaultSupportContacts = listOf(
    ContactChannel(ContactKind.Telegram, "Telegram", "Nhắn tin qua Telegram", "https://t.me/thanhgmn"),
    ContactChannel(ContactKind.Zalo, "Zalo", "Nhắn tin qua Zalo", "https://zalo.me/0931006827"),
)

/**
 * Tấm trượt từ đáy trong Dialog toàn màn hình nên phủ cả thanh dock. Chạm nền mờ hoặc bấm Back để đóng; không có nút "Đóng".
 * Chạm một kênh thì mở link bằng [LocalUriHandler] (Telegram / Zalo).
 */
@Composable
fun SupportSheet(
    onDismiss: () -> Unit,
    contacts: List<ContactChannel> = DefaultSupportContacts,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)   // tự vẽ nền mờ
        val uri = LocalUriHandler.current
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "scrim")
        val noRipple = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color(0x730F172A))
                    .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
            )
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(280, easing = EaseOutSoft)) { it / 4 } + fadeIn(tween(280)),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Alx.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(Color(0xFFD5DBE6), RoundedCornerShape(2.dp)))
                    BasicText(Str.profileContactSheetTitle, Modifier.padding(top = 18.dp), style = type(20f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
                    BasicText(Str.profileContactSheetSubtitle, Modifier.padding(top = 4.dp), style = type(13.5f, FontWeight.Medium, Alx.Muted))
                    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        contacts.forEach { c -> ContactRow(c) { uri.openUri(c.url) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(c: ContactChannel, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Box(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Alx.Bg, shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (c.kind) {
                ContactKind.Telegram -> Box(Modifier.size(44.dp).background(Color(0xFFE3F2FB), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    SvgIcon(Plane, 21.dp, Color(0xFF1D8FCF), 1.8f)
                }
                ContactKind.Zalo -> Box(Modifier.size(44.dp).background(Alx.BlueSoft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    BasicText("Zalo", style = type(13.5f, FontWeight.ExtraBold, Color(0xFF0F5BD8), spacing = -0.01f))
                }
            }
            Column(Modifier.weight(1f)) {
                BasicText(c.name, style = type(15f, FontWeight.Bold, Alx.Ink))
                BasicText(c.subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
            }
            SvgIcon(Chevron, 18.dp, Color(0xFF94A3B8), 2f)
        }
    }
}
