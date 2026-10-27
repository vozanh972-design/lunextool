package com.cayxu.app.ui.screens.golike

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cayxu.app.R
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.PrimaryButton
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/* ───────────────────────────── Trạng thái ───────────────────────────── */

enum class GolikeIgTaskMode(val title: String, val subtitle: String?) {
    Random("Ngẫu nhiên", "Tym / Follow"),
    FollowOnly("Chỉ Follow", null),
    TymOnly("Chỉ Tym", null),
}

data class GolikeRunConfigInstagramUiState(
    val mode: GolikeIgTaskMode = GolikeIgTaskMode.Random,
    val getTaskWaitSeconds: String = "10",
    val workSeconds: String = "10",
    val taskCount: String = "",
    val stopAfterEmptyTimes: String = "100",
    val errorSwapAccountCount: String = "50",
    val stopAfterDoneTimes: String = "100",
    val browseBeforeTask: Boolean = true,
    val backHomeThenBrowse: Boolean = false,
)

/* ───────────────────────────── Màn hình Cấu hình GoLike IG ───────────────────────────── */

@Composable
fun GolikeRunConfigInstagramScreen(
    state: GolikeRunConfigInstagramUiState,
    onBack: () -> Unit,
    onModeChange: (GolikeIgTaskMode) -> Unit,
    onGetTaskWaitChange: (String) -> Unit,
    onWorkSecondsChange: (String) -> Unit,
    onTaskCountChange: (String) -> Unit,
    onStopAfterEmptyChange: (String) -> Unit,
    onErrorSwapChange: (String) -> Unit,
    onStopAfterDoneChange: (String) -> Unit,
    onBrowseBeforeTaskChange: (Boolean) -> Unit,
    onBackHomeThenBrowseChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Alx.Bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConfigHeader(onBack)
            PlatformBadge(R.drawable.ic_platform_instagram, "Instagram")

            SectionCard {
                BasicText("Loại nhiệm vụ", style = type(15f, FontWeight.ExtraBold, Alx.Ink))
                BasicText(
                    "Bật nhiều loại → xoay vòng; hết NV đợi 10s rồi sang loại kế",
                    Modifier.padding(top = 3.dp), style = type(12.5f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f),
                )
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GolikeIgTaskMode.entries.forEach { m -> ModeRow(m, selected = state.mode == m, onClick = { onModeChange(m) }) }
                }
            }

            SectionCard(innerPadding = 0.dp) {
                NumberRow("Thời gian lấy nhiệm vụ", state.getTaskWaitSeconds, onGetTaskWaitChange, unit = "s", divider = false)
                NumberRow("Thời gian làm nhiệm vụ", state.workSeconds, onWorkSecondsChange, unit = "s", divider = true)
                NumberRow("Số nhiệm vụ muốn làm", state.taskCount, onTaskCountChange, placeholder = "Để trống = không giới hạn", wide = true, divider = true)
                NumberRow("Số lần hết NV sẽ dừng", state.stopAfterEmptyTimes, onStopAfterEmptyChange, divider = true)
                NumberRow("Số job lỗi / nhả sẽ đổi acc", state.errorSwapAccountCount, onErrorSwapChange, divider = true)
                NumberRow("Số lần hoàn thành NV sẽ dừng", state.stopAfterDoneTimes, onStopAfterDoneChange, placeholder = "Để trống = không giới hạn", wide = true, divider = true)
            }

            SectionCard(innerPadding = 0.dp) {
                BehaviorToggleRow("Lướt trước khi làm", "Cuộn feed một lúc trước khi thực hiện NV", state.browseBeforeTask, onBrowseBeforeTaskChange, divider = false)
                BehaviorToggleRow("Trở về Home rồi lướt", "Quay về trang chủ trước khi cuộn", state.backHomeThenBrowse, onBackHomeThenBrowseChange, divider = true)
            }
        }

        PrimaryButton(
            "Lưu cấu hình", onSave,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 20.dp).padding(bottom = 26.dp).navigationBarsPadding(),
        )
    }
}

/* ───────────────────────────── Khối con giao diện ───────────────────────────── */

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val InstagramPink = Color(0xFFC2255C)
private val InstagramSoft = Color(0xFFFDEAF2)

@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = if (pressed) 0.985f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
    ) { content() }
}

@Composable
private fun ConfigHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PressBox(
            onClick = onBack,
            modifier = Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
        BasicText("Cấu hình chạy", style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
    }
}

@Composable
private fun PlatformBadge(icon: Int, label: String) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Alx.White, RoundedCornerShape(18.dp))
            .border(1.5.dp, Alx.InputBorder, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(icon), label, Modifier.size(28.dp).clip(CircleShape))
        BasicText(label, style = type(15f, FontWeight.ExtraBold, Alx.Ink))
    }
}

@Composable
private fun SectionCard(innerPadding: Dp = 16.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape).padding(innerPadding)) {
        content()
    }
}

@Composable
private fun ModeRow(mode: GolikeIgTaskMode, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) InstagramSoft else Color.Transparent, tween(120), label = "modeBg")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(mode.title, style = type(14f, FontWeight.Bold, Alx.Ink))
            if (mode.subtitle != null) BasicText(mode.subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
        }
        RadioDot(selected)
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier.size(20.dp).border(1.5.dp, if (selected) InstagramPink else Alx.InputBorder, CircleShape)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).background(InstagramPink, CircleShape))
    }
}

@Composable
private fun BehaviorToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0xFFEEF1F6)))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = type(14f, FontWeight.Bold, Alx.Ink))
                BasicText(subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
            }
            Switch(checked)
        }
    }
}

@Composable
private fun NumberRow(
    label: String, value: String, onChange: (String) -> Unit,
    divider: Boolean, unit: String? = null, placeholder: String? = null, wide: Boolean = false,
) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0xFFEEF1F6)))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(label, Modifier.weight(1f), style = type(14f, FontWeight.Bold, Alx.Ink))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier.then(if (wide) Modifier.width(160.dp) else Modifier.width(68.dp)).height(38.dp)
                        .background(Alx.White, RoundedCornerShape(12.dp)).border(1.dp, Alx.InputBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        BasicText(placeholder, style = type(11f, FontWeight.Medium, Alx.Placeholder, align = TextAlign.Center))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = type(15f, FontWeight.ExtraBold, Alx.Ink, align = TextAlign.Center),
                        cursorBrush = SolidColor(InstagramPink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (unit != null) BasicText(unit, style = type(13f, FontWeight.SemiBold, Alx.Muted))
            }
        }
    }
}

@Composable
private fun Switch(checked: Boolean) {
    val bg by animateColorAsState(if (checked) InstagramPink else Color(0xFFCBD5E1), tween(160), label = "swBg")
    Box(
        Modifier.size(width = 46.dp, height = 26.dp).clip(CircleShape).background(bg)
            .padding(2.5.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(21.dp).clip(CircleShape).background(Color.White))
    }
}
