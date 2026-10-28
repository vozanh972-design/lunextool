package com.cayxu.app.ui.f4f

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
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

enum class F4fIgTaskMode(val title: String, val subtitle: String?) {
    Random("Ngẫu nhiên", "Tym / Follow"),
    FollowOnly("Chỉ Follow", null),
    TymOnly("Chỉ Tym", null),
}

data class F4fRunConfigInstagramUiState(
    val mode: F4fIgTaskMode = F4fIgTaskMode.FollowOnly,
    val getTaskWaitSeconds: String = "5",
    val workSeconds: String = "10",
    val taskCount: String = "",
    val stopAfterEmptyTimes: String = "3",
    val errorSwapAccountCount: String = "5",
    val stopAfterDoneTimes: String = "",
    val browseBeforeTask: Boolean = true,
    val backHomeThenBrowse: Boolean = false,
)

@Composable
fun F4fRunConfigInstagramScreen(
    state: F4fRunConfigInstagramUiState,
    onBack: () -> Unit,
    onModeChange: (F4fIgTaskMode) -> Unit,
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

            CardSection(title = "CHẾ ĐỘ NHIỆM VỤ") {
                ModeRow(mode = F4fIgTaskMode.FollowOnly, selected = true, onClick = { onModeChange(F4fIgTaskMode.FollowOnly) })
            }

            CardSection(title = "THỜI GIAN CHỜ") {
                NumberInputRow(
                    label = "Giãn cách lấy nhiệm vụ (giây)",
                    value = state.getTaskWaitSeconds,
                    onValueChange = onGetTaskWaitChange,
                    placeholder = "5",
                )
                DividerLine()
                NumberInputRow(
                    label = "Thời gian làm nhiệm vụ (giây)",
                    value = state.workSeconds,
                    onValueChange = onWorkSecondsChange,
                    placeholder = "10",
                )
            }

            CardSection(title = "GIỚI HẠN NHIỆM VỤ") {
                NumberInputRow(
                    label = "Số nhiệm vụ mỗi tài khoản",
                    value = state.taskCount,
                    onValueChange = onTaskCountChange,
                    placeholder = "Không giới hạn",
                )
                DividerLine()
                NumberInputRow(
                    label = "Dừng khi hết nhiệm vụ (lần)",
                    value = state.stopAfterEmptyTimes,
                    onValueChange = onStopAfterEmptyChange,
                    placeholder = "3",
                )
                DividerLine()
                NumberInputRow(
                    label = "Đổi nick khi gặp lỗi (lần)",
                    value = state.errorSwapAccountCount,
                    onValueChange = onErrorSwapChange,
                    placeholder = "5",
                )
            }

            CardSection(title = "TÙY CHỌN NÂNG CAO") {
                ToggleRow(
                    label = "Lướt xem trước khi làm nhiệm vụ",
                    checked = state.browseBeforeTask,
                    onCheckedChange = onBrowseBeforeTaskChange,
                )
                DividerLine()
                ToggleRow(
                    label = "Về trang chủ rồi mới lướt",
                    checked = state.backHomeThenBrowse,
                    onCheckedChange = onBackHomeThenBrowseChange,
                )
            }
        }

        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Alx.Bg)
                .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFC2255C))
                    .clickable(role = Role.Button, onClick = onSave),
                contentAlignment = Alignment.Center
            ) {
                BasicText("Lưu cấu hình", style = type(16f, FontWeight.Bold, Alx.White))
            }
        }
    }
}

private val ArrowLeft = listOf("M19 12H5M12 19l-7-7 7-7")

@Composable
private fun ConfigHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(ArrowLeft, 20.dp, Alx.Ink, 2f) }
        BasicText("Cấu hình chạy Follow4Follow", style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
    }
}

@Composable
private fun PlatformBadge(iconRes: Int, name: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFDEAF2), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painter = painterResource(iconRes), contentDescription = name, modifier = Modifier.size(28.dp))
        Column {
            BasicText("Nền tảng", style = type(11f, FontWeight.SemiBold, Alx.Muted))
            BasicText(name, style = type(15f, FontWeight.Bold, Color(0xFFC2255C)))
        }
    }
}

@Composable
private fun CardSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(title, Modifier.padding(start = 4.dp), style = type(11.5f, FontWeight.ExtraBold, Alx.Muted, spacing = 0.04f))
        Column(
            Modifier.fillMaxWidth().background(Alx.White, RoundedCornerShape(20.dp)).border(1.dp, Alx.Line, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) { content() }
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Alx.Line))
}

@Composable
private fun ModeRow(mode: F4fIgTaskMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).background(if (selected) Color(0xFFC2255C) else Alx.White, CircleShape)
                .border(2.dp, if (selected) Color(0xFFC2255C) else Alx.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).background(Alx.White, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            BasicText(mode.title, style = type(14.5f, if (selected) FontWeight.Bold else FontWeight.SemiBold, Alx.Ink))
            if (mode.subtitle != null) {
                BasicText(mode.subtitle, style = type(12f, FontWeight.Medium, Alx.Muted))
            }
        }
    }
}

@Composable
private fun NumberInputRow(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(label, Modifier.weight(1f), style = type(14f, FontWeight.SemiBold, Alx.Ink))
        Box(
            Modifier.width(110.dp).height(38.dp).background(Alx.Bg, RoundedCornerShape(10.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (value.isEmpty()) {
                BasicText(placeholder, style = type(13.5f, FontWeight.Medium, Alx.Muted, align = TextAlign.End))
            }
            BasicTextField(
                value = value,
                onValueChange = { s -> onValueChange(s.filter { it.isDigit() }) },
                textStyle = type(14f, FontWeight.Bold, Alx.Ink, align = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(Color(0xFFC2255C)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(label, Modifier.weight(1f), style = type(14f, FontWeight.SemiBold, Alx.Ink))
        CupertinoSwitch(checked = checked, onCheckedChange = onCheckedChange, activeColor = Color(0xFFC2255C))
    }
}

@Composable
private fun CupertinoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, activeColor: Color) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val thumbSize = 27.dp
    val thumbPadding = 2.dp
    val maxOffset = trackWidth - thumbSize - thumbPadding * 2

    val bg by animateColorAsState(if (checked) activeColor else Color(0xFFE2E8F0), tween(200), label = "switchBg")
    val thumbX: Dp by animateDpAsState(if (checked) maxOffset else 0.dp, tween(200), label = "switchThumb")

    Box(
        Modifier.size(trackWidth, trackHeight).clip(RoundedCornerShape(trackHeight / 2)).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
            .padding(thumbPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = thumbX).size(thumbSize).shadow(2.dp, CircleShape, clip = false)
                .background(Alx.White, CircleShape)
        )
    }
}
