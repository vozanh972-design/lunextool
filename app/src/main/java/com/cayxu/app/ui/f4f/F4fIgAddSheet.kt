package com.cayxu.app.ui.f4f

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val Close = listOf("M6 6l12 12M18 6L6 18")
private val Plus = listOf("M12 5v14M5 12h14")
private val Warning = listOf(
    "M12 9v4",
    "M12 17h.01",
    "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
)

private val IgPink = Color(0xFFC2255C)
private val IgPinkSoft = Color(0xFFFDEAF2)

/**
 * Tấm "Thêm tài khoản Instagram" riêng của Follow4Follow.
 * [onSubmit] nhận chuỗi text đã dán (mỗi dòng 1 nick: Cookie hoặc Cookie|Proxy).
 */
@Composable
fun F4fIgAddSheet(
    onDismiss: () -> Unit,
    onSubmit: (data: String) -> Unit,
) {
    var data by remember { mutableStateOf("") }

    F4fAddAccountDialog(onDismiss = onDismiss) {
        F4fSheetHeader(
            iconTint = IgPink,
            iconSoft = IgPinkSoft,
            icon = Plus,
            title = "Thêm tài khoản Instagram F4F",
            subtitle = "Định dạng Cookie hoặc Cookie|Proxy",
            onClose = onDismiss,
        )

        F4fSectionLabel("DANH SÁCH TÀI KHOẢN (MỖI DÒNG 1 NICK)", Modifier.padding(top = 22.dp))
        F4fMultilineField(
            value = data,
            onValueChange = { data = it },
            accent = IgPink,
            minHeight = 190.dp,
            placeholder = "Định dạng: Cookie hoặc Cookie|Proxy\nVí dụ:\nsessionid=...; ds_user_id=123...; csrftoken=...\nsessionid=...; ds_user_id=456...; csrftoken=...|103.152.118.23:8080:user:pass",
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            F4fCancelButton(onDismiss, Modifier.weight(1f))
            F4fPrimaryActionButton(
                label = "Thêm tài khoản",
                icon = Plus,
                accent = IgPink,
                onClick = { onSubmit(data) },
                modifier = Modifier.weight(1.25f),
                iconSize = 16.dp,
                iconStroke = 2.4f,
            )
        }
    }
}

/**
 * BottomSheet hiển thị chi tiết lỗi của tài khoản F4F.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun F4fErrorDetailBottomSheet(
    accountName: String,
    errorMessage: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Alx.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFDECEC)),
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon(Warning, 18.dp, Color(0xFFC9363B), 2f)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    BasicText(
                        "Chi tiết lỗi",
                        style = type(17f, FontWeight.Bold, Alx.Ink)
                    )
                    BasicText(
                        "Tài khoản: $accountName",
                        style = type(12.5f, FontWeight.Medium, Alx.Muted)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            BasicText("Chi tiết nguyên nhân phản hồi:", style = type(13f, FontWeight.SemiBold, Alx.Ink))
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEF2F2))
                    .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                val displayError = if (errorMessage.length > 500) errorMessage.take(500) + "..." else errorMessage
                BasicText(
                    displayError.ifBlank { "Không có nội dung lỗi chi tiết." },
                    style = type(13f, FontWeight.Medium, Color(0xFF991B1B), lineHeight = 1.4f)
                )
            }

            Spacer(Modifier.height(20.dp))

            F4fPressBox(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFC9363B)),
                contentAlignment = Alignment.Center
            ) {
                BasicText("Đã hiểu & Đóng", style = type(15f, FontWeight.Bold, Alx.White))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun F4fAddAccountDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "f4fAddScrim")
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
private fun F4fSheetHeader(iconTint: Color, iconSoft: Color, icon: List<String>, title: String, subtitle: String, onClose: () -> Unit) {
    Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(iconSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            SvgIcon(icon, 21.dp, iconTint, 2.2f)
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = type(19f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f, lineHeight = 1.2f))
            BasicText(subtitle, Modifier.padding(top = 2.dp), style = type(12.5f, FontWeight.Medium, Alx.Muted, lineHeight = 1.4f))
        }
        F4fPressBox(
            onClick = onClose,
            modifier = Modifier.size(40.dp).background(Alx.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Close, 18.dp, Alx.Ink2, 2.2f) }
    }
}

@Composable
private fun F4fSectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(horizontal = 4.dp), style = type(11f, FontWeight.ExtraBold, Alx.Muted, spacing = 0.12f))
}

@Composable
private fun F4fMultilineField(
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
private fun F4fCancelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    F4fPressBox(
        onClick = onClick,
        modifier = modifier.height(56.dp)
            .background(Alx.White, RoundedCornerShape(18.dp))
            .border(1.5.dp, Alx.InputBorder, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) { BasicText("Huỷ", style = type(15f, FontWeight.Bold, Alx.Ink)) }
}

@Composable
private fun F4fPrimaryActionButton(
    label: String,
    icon: List<String>,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    iconStroke: Float = 2f,
) {
    val shape = RoundedCornerShape(18.dp)
    F4fPressBox(
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
private fun F4fPressBox(
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
