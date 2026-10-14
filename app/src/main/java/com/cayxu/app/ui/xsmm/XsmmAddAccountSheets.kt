package com.cayxu.app.ui.xsmm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/*
 * 2 tấm "Thêm tài khoản" của XSMM — CHỈ là lớp giao diện, giống XsmmLoginScreen/XsmmAccountsScreen.
 * Không gọi mạng, không tự kiểm tra Live/UID, không tự lưu tài khoản: tấm chỉ gom dữ liệu người dùng
 * dán vào rồi báo ra qua [onSubmit]; bạn tự xử lý (gọi API, parse từng dòng, v.v.) ở nơi gọi.
 * Mở bằng Dialog toàn màn hình (giống SupportSheet) nên tự vẽ nền mờ + tấm trượt lên, không cần NavHost riêng.
 */

private val Close = listOf("M6 6l12 12M18 6L6 18")
private val Plus = listOf("M12 5v14M5 12h14")
private val Login = listOf("M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-4M10 17l5-5-5-5M15 12H3")
private val Info = listOf("M12 8v5M12 16h.01")

/* ───────────────────────────── Facebook ───────────────────────────── */

/** 3 định dạng tài khoản FB mà tấm cho chọn (tuỳ chọn — bỏ trống thì hệ thống tự nhận diện). */
enum class XsmmFbFormat(val label: String) { Token("Token"), Uid2fa("UID|PASS|2FA"), UidCookie("UID|PASS|COOKIE") }

/**
 * Tấm "Đăng nhập Facebook". [onSubmit] nhận định dạng đang chọn (null = tự nhận diện) + toàn bộ text đã dán
 * (bạn tự `split("\n")` để lấy từng dòng 1 tài khoản).
 */
@Composable
fun XsmmFbLoginSheet(
    onDismiss: () -> Unit,
    onSubmit: (format: XsmmFbFormat?, data: String) -> Unit,
) {
    var format by remember { mutableStateOf<XsmmFbFormat?>(null) }
    var data by remember { mutableStateOf("") }

    AddAccountDialog(onDismiss = onDismiss) {
        SheetHeader(
            iconTint = Alx.Blue, iconSoft = Alx.BlueSoft, icon = Plus,
            title = "Đăng nhập Facebook",
            subtitle = "Chọn định dạng và dán tài khoản (hỗ trợ Token, UID|Pass|2FA, Cookie)",
            onClose = onDismiss,
        )

        SectionLabel("ĐỊNH DẠNG TÀI KHOẢN (TÙY CHỌN)", Modifier.padding(top = 22.dp))
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XsmmFbFormat.entries.forEach { f ->
                FormatChip(f.label, selected = format == f, modifier = Modifier.weight(1f)) {
                    format = if (format == f) null else f
                }
            }
        }

        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth()
                .background(Alx.Bg, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.padding(top = 1.dp).size(20.dp).background(Alx.BlueSoft, CircleShape), contentAlignment = Alignment.Center) {
                SvgIcon(Info, 11.dp, Alx.Blue, 2.6f)
            }
            Column {
                BasicText(
                    format?.let { "Định dạng: ${it.label}" } ?: "Chưa chọn định dạng (Tự động nhận diện)",
                    style = type(13.5f, FontWeight.ExtraBold, Alx.Blue, lineHeight = 1.3f),
                )
                BasicText(
                    format?.let { "Mỗi dòng dán đúng theo định dạng ${it.label}." }
                        ?: "Bấm chọn 1 định dạng phía trên nếu muốn cố định, hoặc dán trực tiếp vào ô dưới (hệ thống sẽ tự nhận diện).",
                    Modifier.padding(top = 2.dp),
                    style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f),
                )
            }
        }

        SectionLabel("DỮ LIỆU TÀI KHOẢN (MỖI DÒNG 1 NICK)", Modifier.padding(top = 18.dp))
        MultilineField(
            value = data, onValueChange = { data = it }, accent = Alx.Blue, minHeight = 150.dp,
            placeholder = "Dán Token (EAA...), Cookie hoặc UID|PASS|... (mỗi dòng 1 nick)",
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(Modifier.padding(top = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CancelButton(onDismiss, Modifier.weight(1f))
            PrimaryActionButton(
                label = "Đăng nhập", icon = Login, accent = Alx.Blue,
                onClick = { onSubmit(format, data) },
                modifier = Modifier.weight(1.25f),
            )
        }
    }
}

/* ───────────────────────────── Instagram ───────────────────────────── */

private val IgPink = Color(0xFFC2255C)
private val IgPinkSoft = Color(0xFFFDEAF2)

/** Tấm "Thêm tài khoản Instagram". [onSubmit] nhận toàn bộ text đã dán (mỗi dòng 1 tài khoản, định dạng Cookie/Cookie\|Proxy). */
@Composable
fun XsmmIgAddSheet(
    onDismiss: () -> Unit,
    onSubmit: (data: String) -> Unit,
) {
    var data by remember { mutableStateOf("") }

    AddAccountDialog(onDismiss = onDismiss) {
        SheetHeader(
            iconTint = IgPink, iconSoft = IgPinkSoft, icon = Plus,
            title = "Thêm tài khoản Instagram",
            subtitle = "Định dạng Cookie hoặc Cookie|Proxy",
            onClose = onDismiss,
        )

        SectionLabel("DANH SÁCH TÀI KHOẢN (MỖI DÒNG 1 NICK)", Modifier.padding(top = 22.dp))
        MultilineField(
            value = data, onValueChange = { data = it }, accent = IgPink, minHeight = 190.dp,
            placeholder = "Định dạng: Cookie hoặc Cookie|Proxy\nVí dụ:\nsessionid=...; ds_user_id=123...; csrftoken=...\nsessionid=...; ds_user_id=456...; csrftoken=...|103.152.118.23:8080:user:pass\nsessionid=...; ds_user_id=789...; csrftoken=...|103.152.118.24:8080",
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(Modifier.padding(top = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CancelButton(onDismiss, Modifier.weight(1f))
            PrimaryActionButton(
                label = "Thêm tài khoản", icon = Plus, accent = IgPink,
                onClick = { onSubmit(data) },
                modifier = Modifier.weight(1.25f),
                iconSize = 16.dp, iconStroke = 2.4f,
            )
        }
    }
}

/* ───────────────────────────── Khung tấm dùng chung ───────────────────────────── */

/** Dialog toàn màn hình tự vẽ nền mờ + trượt tấm lên từ đáy — giống [com.cayxu.app.ui.support.SupportSheet]. */
@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "xsmmAddScrim")
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
                    content()
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(iconTint: Color, iconSoft: Color, icon: List<String>, title: String, subtitle: String, onClose: () -> Unit) {
    Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(iconSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            SvgIcon(icon, 21.dp, iconTint, 2.2f)
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = type(19f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f, lineHeight = 1.2f))
            BasicText(subtitle, Modifier.padding(top = 2.dp), style = type(12.5f, FontWeight.Medium, Alx.Muted, lineHeight = 1.4f))
        }
        PressBox(
            onClick = onClose,
            modifier = Modifier.size(40.dp).background(Alx.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Close, 18.dp, Alx.Ink2, 2.2f) }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(horizontal = 4.dp), style = type(11f, FontWeight.ExtraBold, Alx.Muted, spacing = 0.12f))
}

@Composable
private fun FormatChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    PressBox(
        onClick = onClick,
        modifier = modifier.height(44.dp)
            .then(
                if (selected) Modifier.background(Alx.Ink, shape)
                else Modifier.background(Alx.White, shape).border(1.dp, Alx.Line, shape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = type(12.5f, if (selected) FontWeight.ExtraBold else FontWeight.Bold, if (selected) Alx.White else Alx.Ink2))
    }
}

@Composable
private fun MultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    accent: Color,
    minHeight: Dp,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        interactionSource = source,
        textStyle = type(13.5f, FontWeight.SemiBold, Alx.Ink, lineHeight = 1.5f),
        cursorBrush = SolidColor(accent),
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight)
            .background(Alx.White, shape)
            .border(if (focused) 2.dp else 1.5.dp, if (focused) accent else Alx.InputBorder, shape)
            .padding(14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                BasicText(placeholder, style = type(13f, FontWeight.Medium, Alx.Placeholder, lineHeight = 1.6f))
            }
            inner()
        },
    )
}

@Composable
private fun CancelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressBox(
        onClick = onClick,
        modifier = modifier.height(56.dp)
            .background(Alx.White, RoundedCornerShape(18.dp))
            .border(1.5.dp, Alx.InputBorder, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) { BasicText("Huỷ", style = type(15f, FontWeight.Bold, Alx.Ink)) }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: List<String>,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    iconStroke: Float = 2f,
) {
    val shape = RoundedCornerShape(18.dp)
    PressBox(
        onClick = onClick,
        modifier = modifier.height(56.dp).background(accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SvgIcon(icon, iconSize, Alx.White, iconStroke)
            BasicText(label, style = type(15f, FontWeight.Bold, Alx.White))
        }
    }
}

@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content() }
}
