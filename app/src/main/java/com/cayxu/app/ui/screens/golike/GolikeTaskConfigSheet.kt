package com.cayxu.app.ui.screens.golike

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.PrimaryButton
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

private val GolikeCfgBlue = Color(0xFF1F4BD8)
private val GolikeCfgBlueSoft = Color(0xFFE8EEFD)

private val Close = listOf("M6 6l12 12M18 6L6 18")
private val Settings = listOf(
    "M12 15a3 3 0 100-6 3 3 0 000 6z",
    "M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 11-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 110-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 114 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06-.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 110 4h-.09a1.65 1.65 0 00-1.51 1z"
)

/* ───────────────────────────── Trạng thái ───────────────────────────── */

data class GolikeTaskConfigUiState(
    val platformLabel: String = "TikTok",
    val getJobMinSeconds: String = "5",
    val getJobMaxSeconds: String = "12",
    val swipeBeforeGetJob: Boolean = true,
    val doJobMinSeconds: String = "8",
    val doJobMaxSeconds: String = "15",
    val swipeAfterFollow: Boolean = true,
    val completeJobMinSeconds: String = "20",
    val completeJobMaxSeconds: String = "40",
    val breakMinSeconds: String = "8",
    val breakMaxSeconds: String = "15",
    val goalTaskCount: String = "0",
    val autoSwapOnError: Boolean = true,
    val errorSwapCount: String = "5"
)

/* ───────────────────────────── Tấm cấu hình ───────────────────────────── */

@Composable
fun GolikeTaskConfigSheet(
    state: GolikeTaskConfigUiState,
    onDismiss: () -> Unit,
    onGetJobMinChange: (String) -> Unit,
    onGetJobMaxChange: (String) -> Unit,
    onSwipeBeforeGetJobChange: (Boolean) -> Unit,
    onDoJobMinChange: (String) -> Unit,
    onDoJobMaxChange: (String) -> Unit,
    onSwipeAfterFollowChange: (Boolean) -> Unit,
    onCompleteJobMinChange: (String) -> Unit,
    onCompleteJobMaxChange: (String) -> Unit,
    onBreakMinChange: (String) -> Unit,
    onBreakMaxChange: (String) -> Unit,
    onGoalTaskCountChange: (String) -> Unit,
    onAutoSwapOnErrorChange: (Boolean) -> Unit,
    onErrorSwapCountChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "golikeCfgScrim")
        val noRipple = remember { MutableInteractionSource() }

        Box(Modifier.fillMaxSize()) {
            // Nền tối mờ phía sau
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(Color(0x730F172A))
                    .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
            )

            // Sheet trượt lên từ dưới
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(280, easing = EaseOutSoft)) { it / 4 } + fadeIn(tween(280))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f)
                        .background(Alx.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .navigationBarsPadding()
                        .padding(top = 10.dp)
                ) {
                    // Thanh gạt phía trên
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 4.dp)
                            .background(Color(0xFFD5DBE6), RoundedCornerShape(2.dp))
                    )

                    // Tiêu đề cố định trên cùng
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        SheetHeader(state.platformLabel, onDismiss)
                    }

                    // Thân cuộn toàn bộ các khối cấu hình theo đúng thứ tự 1 ➔ 6
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        // ── 1. THỜI GIAN LẤY JOB (GIÂY) ──
                        SectionLabel("1. THỜI GIAN LẤY JOB (GIÂY)")
                        MinMaxRow(state.getJobMinSeconds, onGetJobMinChange, state.getJobMaxSeconds, onGetJobMaxChange)
                        SubLabel("Thời gian chờ ngẫu nhiên trước khi gọi lấy job mới từ Golike")

                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            title = "Lướt trước khi làm",
                            subtitle = "Lướt video TikTok trong thời gian chờ lấy job",
                            checked = state.swipeBeforeGetJob,
                            onCheckedChange = onSwipeBeforeGetJobChange
                        )

                        // ── 2. THỜI GIAN LÀM JOB (GIÂY) ──
                        SectionLabel("2. THỜI GIAN LÀM JOB (GIÂY)", Modifier.padding(top = 22.dp))
                        MinMaxRow(state.doJobMinSeconds, onDoJobMinChange, state.doJobMaxSeconds, onDoJobMaxChange)
                        SubLabel("Thời gian đếm ngược ngẫu nhiên trên trang cá nhân rồi bấm Follow")

                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            title = "Follow xong lướt tin",
                            subtitle = "Bấm Follow xong tự quay lại Home lướt tin video",
                            checked = state.swipeAfterFollow,
                            onCheckedChange = onSwipeAfterFollowChange
                        )

                        // ── 3. THỜI GIAN HOÀN THÀNH JOB (GIÂY) ──
                        SectionLabel("3. THỜI GIAN HOÀN THÀNH JOB (GIÂY)", Modifier.padding(top = 22.dp))
                        MinMaxRow(state.completeJobMinSeconds, onCompleteJobMinChange, state.completeJobMaxSeconds, onCompleteJobMaxChange)
                        SubLabel("Thời gian chờ để hoàn thành một job trước khi gửi báo cáo xong")

                        // ── 4. THỜI GIAN NGHỈ GIỮA CÁC JOB (GIÂY) ──
                        SectionLabel("4. THỜI GIAN NGHỈ GIỮA CÁC JOB (GIÂY)", Modifier.padding(top = 22.dp))
                        MinMaxRow(state.breakMinSeconds, onBreakMinChange, state.breakMaxSeconds, onBreakMaxChange)
                        SubLabel("Thời gian nghỉ giãn cách giữa 2 nhiệm vụ liên tiếp")

                        // ── 5. SỐ LƯỢNG NHIỆM VỤ TỐI ĐA ──
                        SectionLabel("5. SỐ LƯỢNG NHIỆM VỤ TỐI ĐA", Modifier.padding(top = 22.dp))
                        Column(Modifier.padding(top = 8.dp)) {
                            FieldLabel("Số job mục tiêu")
                            NumberField(state.goalTaskCount, onGoalTaskCountChange, Modifier.fillMaxWidth())
                            BasicText(
                                "Nhập 0 để không giới hạn",
                                Modifier.padding(top = 6.dp, start = 4.dp),
                                style = type(12f, FontWeight.Medium, Alx.Muted)
                            )
                        }

                        // ── 6. TỰ ĐỘNG ĐỔI NICK KHI LỖI ──
                        SectionLabel("6. TỰ ĐỘNG ĐỔI NICK KHI LỖI", Modifier.padding(top = 22.dp))
                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            title = "Tự động đổi nick khi lỗi",
                            subtitle = "Tự nhảy sang tài khoản tiếp theo nếu gặp lỗi liên tiếp",
                            checked = state.autoSwapOnError,
                            onCheckedChange = onAutoSwapOnErrorChange
                        )

                        AnimatedVisibility(visible = state.autoSwapOnError) {
                            Column(Modifier.padding(top = 12.dp)) {
                                FieldLabel("Số lần lỗi liên tiếp thì đổi nick")
                                NumberField(state.errorSwapCount, onErrorSwapCountChange, Modifier.fillMaxWidth())
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }

                    // ── NÚT LƯU CẤU HÌNH CỐ ĐỊNH Ở ĐÁY SHEET ──
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Alx.White)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        PrimaryButton(
                            label = "Lưu cấu hình",
                            onClick = onSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ───────────────────────────── Các thành phần UI phụ trợ ───────────────────────────── */

@Composable
private fun SheetHeader(platformLabel: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(GolikeCfgBlueSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(paths = Settings, size = 20.dp, color = GolikeCfgBlue)
            }
            Column(Modifier.padding(start = 12.dp)) {
                BasicText("Cấu hình làm nhiệm vụ", style = type(18f, FontWeight.Bold, Alx.Ink))
                BasicText(
                    "Áp dụng cho $platformLabel",
                    Modifier.padding(top = 2.dp),
                    style = type(13f, FontWeight.Medium, Alx.Muted)
                )
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .background(Color(0xFFF1F5F9), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            SvgIcon(paths = Close, size = 16.dp, color = Alx.Ink2)
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier.padding(start = 2.dp),
        style = type(12.5f, FontWeight.ExtraBold, Color(0xFF475569), spacing = 0.05f)
    )
}

@Composable
private fun SubLabel(text: String) {
    BasicText(
        text,
        Modifier.padding(top = 6.dp, start = 4.dp),
        style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.35f)
    )
}

@Composable
private fun FieldLabel(text: String) {
    BasicText(
        text,
        Modifier.padding(bottom = 6.dp, start = 2.dp),
        style = type(13f, FontWeight.SemiBold, Alx.Ink2)
    )
}

@Composable
private fun MinMaxRow(
    minVal: String,
    onMinChange: (String) -> Unit,
    maxVal: String,
    onMaxChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            FieldLabel("Tối thiểu")
            NumberField(minVal, onMinChange, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f)) {
            FieldLabel("Tối đa")
            NumberField(maxVal, onMaxChange, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = { s -> onValueChange(s.filter { it.isDigit() }) },
        modifier = modifier
            .height(48.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(14.dp))
            .border(1.dp, Alx.InputBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = type(15f, FontWeight.SemiBold, Alx.Ink, align = TextAlign.Start),
        cursorBrush = SolidColor(GolikeCfgBlue),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                innerTextField()
            }
        }
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
            .border(1.dp, Alx.Divider, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(title, style = type(14f, FontWeight.Bold, Alx.Ink))
            BasicText(subtitle, Modifier.padding(top = 2.dp), style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.35f))
        }
        Switch(checked)
    }
}

@Composable
private fun Switch(checked: Boolean) {
    val track by animateColorAsState(if (checked) GolikeCfgBlue else Color(0xFFCBD2DF), tween(150), label = "track")
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, tween(150), label = "knob")

    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .background(track, RoundedCornerShape(16.dp))
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(26.dp)
                .shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}
