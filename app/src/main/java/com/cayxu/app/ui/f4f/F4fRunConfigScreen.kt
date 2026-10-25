package com.cayxu.app.ui.f4f

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

private val F4fViolet = Color(0xFF7C3AED)
private val F4fVioletSoft = Color(0xFFF5F3FF)

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val SettingsIcon = listOf(
    "M12 15a3 3 0 100-6 3 3 0 000 6z",
    "M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 11-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 110-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 114 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06-.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 110 4h-.09a1.65 1.65 0 00-1.51 1z"
)

@Composable
fun F4fRunConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val initialConfig = remember { F4fRunConfigStore.get(context) }

    var followTask by remember { mutableStateOf(initialConfig.enableFollow) }
    var likeTask by remember { mutableStateOf(initialConfig.enableLike) }
    var commentTask by remember { mutableStateOf(initialConfig.enableComment) }

    var delayMin by remember { mutableStateOf(initialConfig.delayMinSeconds.toString()) }
    var delayMax by remember { mutableStateOf(initialConfig.delayMaxSeconds.toString()) }
    var swipeBeforeGetJob by remember { mutableStateOf(initialConfig.swipeBeforeGetJob) }

    var taskCount by remember { mutableStateOf(initialConfig.targetTaskCount.toString()) }

    var autoSwapOnError by remember { mutableStateOf(initialConfig.autoSwapOnError) }
    var errorSwapCount by remember { mutableStateOf(initialConfig.errorSwapCount.toString()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Alx.White)
            .statusBarsPadding()
    ) {
        // ── HEADER ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(F4fVioletSoft, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { navController.popBackStack() },
                    contentAlignment = Alignment.Center,
                ) { SvgIcon(Back, 20.dp, F4fViolet, 2.2f) }

                Column {
                    BasicText("Cấu hình làm nhiệm vụ", style = type(18f, FontWeight.Bold, Alx.Ink))
                    BasicText(
                        "Áp dụng cho Follow4Follow TikTok",
                        Modifier.padding(top = 2.dp),
                        style = type(12.5f, FontWeight.Medium, Alx.Muted)
                    )
                }
            }

            Box(
                Modifier
                    .size(40.dp)
                    .background(F4fVioletSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SvgIcon(paths = SettingsIcon, size = 20.dp, color = F4fViolet)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Alx.Line)
        )

        // ── BODY CUỘN NỘI DUNG ──
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── 1. LOẠI NHIỆM VỤ FOLLOW4FOLLOW ──
            SectionLabel("1. LOẠI NHIỆM VỤ FOLLOW4FOLLOW")
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = "Follow / Theo dõi",
                subtitle = "Tự động mở profile TikTok và bấm Follow nhận coin",
                checked = followTask,
                onCheckedChange = { followTask = it }
            )
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = "Like / Thả tim",
                subtitle = "Tự động mở video TikTok và thả tim nhận coin",
                checked = likeTask,
                onCheckedChange = { likeTask = it }
            )
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = "Comment / Bình luận",
                subtitle = "Tự động bình luận theo nội dung nhiệm vụ",
                checked = commentTask,
                onCheckedChange = { commentTask = it }
            )

            // ── 2. THỜI GIAN GIÃN CÁCH GIỮA CÁC TASK (GIÂY) ──
            SectionLabel("2. THỜI GIAN GIÃN CÁCH GIỮA CÁC TASK (GIÂY)", Modifier.padding(top = 26.dp))
            MinMaxRow(
                minVal = delayMin,
                onMinChange = { delayMin = it },
                maxVal = delayMax,
                onMaxChange = { delayMax = it }
            )
            SubLabel("Thời gian chờ ngẫu nhiên giữa 2 mốc trước khi xác nhận hoàn thành nhiệm vụ")

            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Lướt video trước khi làm",
                subtitle = "Lướt nhẹ video TikTok trong thời gian chờ để tăng độ tin cậy của tài khoản",
                checked = swipeBeforeGetJob,
                onCheckedChange = { swipeBeforeGetJob = it }
            )

            // ── 3. SỐ LƯỢNG NHIỆM VỤ MỤC TIÊU ──
            SectionLabel("3. SỐ LƯỢNG NHIỆM VỤ MỤC TIÊU", Modifier.padding(top = 26.dp))
            Column(Modifier.padding(top = 8.dp)) {
                FieldLabel("Số job mục tiêu")
                NumberField(taskCount, { taskCount = it }, Modifier.fillMaxWidth())
                SubLabel("Nhập 0 để chạy không giới hạn số lượng (Tool sẽ tự động dừng khi đạt đủ mục tiêu)")
            }

            // ── 4. TỰ ĐỘNG ĐỔI NICK KHI LỖI HOẶC HẾT JOB ──
            SectionLabel("4. TỰ ĐỘNG ĐỔI NICK KHI LỖI HOẶC HẾT JOB", Modifier.padding(top = 26.dp))
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = "Tự động đổi nick khi lỗi",
                subtitle = "Tự nhảy sang tài khoản tiếp theo nếu gặp lỗi liên tiếp hoặc tạm hết job",
                checked = autoSwapOnError,
                onCheckedChange = { autoSwapOnError = it }
            )

            AnimatedVisibility(visible = autoSwapOnError) {
                Column(Modifier.padding(top = 12.dp)) {
                    FieldLabel("Số lần lỗi liên tiếp thì đổi nick")
                    NumberField(errorSwapCount, { errorSwapCount = it }, Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(30.dp))
        }

        // ── NÚT LƯU CẤU HÌNH CỐ ĐỊNH Ở ĐÁY MÀN HÌNH ──
        Box(
            Modifier
                .fillMaxWidth()
                .background(Alx.White)
                .border(1.dp, Alx.Line)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(F4fViolet)
                    .clickable {
                        val dMin = delayMin.toIntOrNull()?.coerceAtLeast(3) ?: 5
                        val dMax = delayMax.toIntOrNull()?.coerceAtLeast(dMin) ?: (dMin + 5)
                        val config = F4fRunConfig(
                            delaySeconds = dMin,
                            delayMinSeconds = dMin,
                            delayMaxSeconds = dMax,
                            targetTaskCount = taskCount.toIntOrNull()?.coerceAtLeast(0) ?: 50,
                            enableFollow = followTask,
                            enableLike = likeTask,
                            enableComment = commentTask,
                            autoSwapOnError = autoSwapOnError,
                            errorSwapCount = errorSwapCount.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                            swipeBeforeGetJob = swipeBeforeGetJob,
                        )
                        F4fRunConfigStore.save(context, config)
                        Toast.makeText(context, "Đã lưu cấu hình Follow4Follow", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText("Lưu cấu hình", style = type(16f, FontWeight.Bold, Alx.White))
            }
        }
    }
}

/* ───────────────────────────── Các thành phần UI phụ trợ ───────────────────────────── */

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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        cursorBrush = SolidColor(F4fViolet),
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
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            BasicText(title, style = type(14f, FontWeight.Bold, Alx.Ink))
            BasicText(
                subtitle,
                Modifier.padding(top = 2.dp),
                style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.35f)
            )
        }
        F4fSwitch(checked)
    }
}

@Composable
private fun F4fSwitch(checked: Boolean) {
    val track by animateColorAsState(if (checked) F4fViolet else Color(0xFFCBD2DF), tween(150), label = "f4fTrack")
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, tween(150), label = "f4fKnob")

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
