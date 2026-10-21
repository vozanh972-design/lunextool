package com.cayxu.app.ui.crossttc

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

/*
 * TẤM CẤU HÌNH NHIỆM VỤ TIKTOK TƯƠNG TÁC CHÉO (TIKTOK TTC)
 * Bản sao độc lập lấy từ cấu hình nhiệm vụ Golike TikTok gồm đủ 6 mục chuẩn:
 * 1. Thời gian lấy job + Lướt trước khi làm
 * 2. Thời gian làm job + Follow xong lướt tin
 * 3. Thời gian hoàn thành job
 * 4. Thời gian nghỉ giữa các job
 * 5. Số lượng nhiệm vụ tối đa
 * 6. Tự động đổi nick khi lỗi
 */

private val TtcCfgDark = Color(0xFF0F172A)
private val TtcCfgDarkSoft = Color(0xFFF1F5F9)

private val Close = listOf("M6 6l12 12M18 6L6 18")
private val Settings = listOf(
    "M12 15a3 3 0 100-6 3 3 0 000 6z",
    "M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 11-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 110-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 114 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 110 4h-.09a1.65 1.65 0 00-1.51 1z"
)

data class CrossTTCTaskConfigUiState(
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
    val errorSwapCount: String = "5",
)

@Composable
fun CrossTTCTaskConfigSheet(
    state: CrossTTCTaskConfigUiState,
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
    onSave: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "ttcCfgScrim")
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
                enter = slideInVertically(tween(280, easing = EaseOutSoft)) { it / 4 } + fadeIn(tween(280)),
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

                    // Thân cuộn toàn bộ 6 khối cấu hình
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
                        SubLabel("Thời gian chờ ngẫu nhiên trước khi gọi lấy job mới từ Tương tác chéo")

                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            title = "Lướt trước khi làm",
                            subtitle = "Lướt video TikTok trong thời gian chờ lấy job",
                            checked = state.swipeBeforeGetJob,
                            onCheckedChange = onSwipeBeforeGetJobChange,
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
                            onCheckedChange = onSwipeAfterFollowChange,
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
                                style = type(12f, FontWeight.Medium, Alx.Muted),
                            )
                        }

                        // ── 6. TỰ ĐỘNG ĐỔI NICK KHI LỖI ──
                        SectionLabel("6. TỰ ĐỘNG ĐỔI NICK KHI LỖI", Modifier.padding(top = 22.dp))
                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            title = "Tự động đổi nick khi lỗi",
                            subtitle = "Tự nhảy sang tài khoản tiếp theo nếu gặp lỗi liên tiếp",
                            checked = state.autoSwapOnError,
                            onCheckedChange = onAutoSwapOnErrorChange,
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
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        PrimaryButton(
                            label = "Lưu cấu hình",
                            onClick = onSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(TtcCfgDarkSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SvgIcon(Settings, 20.dp, TtcCfgDark, 1.8f)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                BasicText(
                    "Cấu hình nhiệm vụ",
                    style = type(18f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f),
                )
                BasicText(
                    "Tùy chỉnh luồng chạy tự động $platformLabel TTC",
                    style = type(12.5f, FontWeight.Medium, Alx.Muted),
                )
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .background(Alx.Bg, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            SvgIcon(Close, 16.dp, Alx.Ink, 2f)
        }
    }
}

@Composable
private fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    BasicText(
        text = title,
        modifier = modifier.padding(bottom = 6.dp),
        style = type(12.5f, FontWeight.ExtraBold, Alx.Ink, spacing = 0.05f),
    )
}

@Composable
private fun SubLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.35f),
    )
}

@Composable
private fun FieldLabel(title: String) {
    BasicText(
        text = title,
        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
        style = type(12.5f, FontWeight.Bold, Alx.Ink),
    )
}

@Composable
private fun MinMaxRow(
    minVal: String,
    onMinChange: (String) -> Unit,
    maxVal: String,
    onMaxChange: (String) -> Unit,
) {
    Row(
        Modifier.padding(top = 6.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = { input ->
            if (input.all { it.isDigit() }) onValueChange(input)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = type(15f, FontWeight.Bold, Alx.Ink, align = TextAlign.Center),
        cursorBrush = SolidColor(Alx.Ink),
        modifier = modifier
            .height(46.dp)
            .background(Alx.Bg, RoundedCornerShape(14.dp))
            .border(1.dp, Alx.InputBorder, RoundedCornerShape(14.dp)),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                innerTextField()
            }
        },
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Alx.Bg, RoundedCornerShape(16.dp))
            .border(1.dp, Alx.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(title, style = type(14f, FontWeight.Bold, Alx.Ink))
            BasicText(subtitle, Modifier.padding(top = 2.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
        }
        CupertinoSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CupertinoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val bgColor by animateColorAsState(if (checked) TtcCfgDark else Color(0xFFE2E8F0), label = "switchBg")
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 2.dp, label = "switchThumb")

    Box(
        Modifier
            .width(46.dp)
            .height(28.dp)
            .background(bgColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(2.dp, CircleShape)
                .background(Alx.White, CircleShape),
        )
    }
}
