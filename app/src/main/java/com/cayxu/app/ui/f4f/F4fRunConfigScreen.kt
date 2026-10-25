package com.cayxu.app.ui.f4f

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

@Composable
fun F4fRunConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val initialConfig = remember { F4fRunConfigStore.get(context) }

    var followTask by remember { mutableStateOf(initialConfig.enableFollow) }
    var likeTask by remember { mutableStateOf(initialConfig.enableLike) }
    var commentTask by remember { mutableStateOf(initialConfig.enableComment) }

    var doJobMin by remember { mutableStateOf(initialConfig.doJobMinSeconds.toString()) }
    var doJobMax by remember { mutableStateOf(initialConfig.doJobMaxSeconds.toString()) }

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
            verticalAlignment = Alignment.CenterVertically
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

            // ── 2. THỜI GIAN LÀM JOB (GIÂY) ──
            SectionLabel("2. THỜI GIAN LÀM JOB (GIÂY)", Modifier.padding(top = 26.dp))
            MinMaxRow(
                minVal = doJobMin,
                onMinChange = { doJobMin = it },
                maxVal = doJobMax,
                onMaxChange = { doJobMax = it }
            )
            SubLabel("Thời gian đếm ngược ngẫu nhiên trên profile/video trước khi bấm Follow/Like")

            // ── 3. THỜI GIAN NGHỈ GIỮA CÁC JOB (GIÂY) ──
            SectionLabel("3. THỜI GIAN NGHỈ GIỮA CÁC JOB (GIÂY)", Modifier.padding(top = 26.dp))
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

            // ── 4. SỐ LƯỢNG NHIỆM VỤ MỤC TIÊU ──
            SectionLabel("4. SỐ LƯỢNG NHIỆM VỤ MỤC TIÊU", Modifier.padding(top = 26.dp))
            Column(Modifier.padding(top = 8.dp)) {
                FieldLabel("Số job mục tiêu")
                NumberField(taskCount, { taskCount = it }, Modifier.fillMaxWidth())
                SubLabel("Nhập 0 để chạy không giới hạn số lượng (Tool sẽ tự động dừng khi đạt đủ mục tiêu)")
            }

            // ── 5. TỰ ĐỘNG ĐỔI NICK KHI LỖI HOẶC HẾT JOB ──
            SectionLabel("5. TỰ ĐỘNG ĐỔI NICK KHI LỖI HOẶC HẾT JOB", Modifier.padding(top = 26.dp))
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
                        val djMin = doJobMin.toIntOrNull()?.coerceAtLeast(1) ?: 5
                        val djMax = doJobMax.toIntOrNull()?.coerceAtLeast(djMin) ?: (djMin + 5)
                        val dMin = delayMin.toIntOrNull()?.coerceAtLeast(1) ?: 5
                        val dMax = delayMax.toIntOrNull()?.coerceAtLeast(dMin) ?: (dMin + 5)
                        val config = F4fRunConfig(
                            delaySeconds = dMin,
                            delayMinSeconds = dMin,
                            delayMaxSeconds = dMax,
                            doJobMinSeconds = djMin,
                            doJobMaxSeconds = djMax,
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
                BasicText(
                    "Lưu cấu hình",
                    style = type(15.5f, FontWeight.SemiBold, Alx.White)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    BasicText(
        text = title,
        modifier = modifier,
        style = type(12.5f, FontWeight.Bold, Color(0xFF6B7280))
    )
}

@Composable
private fun FieldLabel(title: String) {
    BasicText(
        text = title,
        modifier = Modifier.padding(bottom = 6.dp),
        style = type(13.5f, FontWeight.SemiBold, Color(0xFF374151))
    )
}

@Composable
private fun SubLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(top = 5.dp),
        style = type(12f, FontWeight.Normal, Color(0xFF9CA3AF))
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF9FAFB))
            .border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(title, style = type(14.5f, FontWeight.SemiBold, Color(0xFF1F2937)))
            BasicText(
                subtitle,
                Modifier.padding(top = 2.dp),
                style = type(12f, FontWeight.Normal, Color(0xFF6B7280))
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Alx.White,
                checkedTrackColor = F4fViolet,
                uncheckedThumbColor = Alx.White,
                uncheckedTrackColor = Color(0xFFE5E7EB),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
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
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            FieldLabel("Tối thiểu (giây)")
            NumberField(minVal, onMinChange, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f)) {
            FieldLabel("Tối đa (giây)")
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
    val numericVal = value.toIntOrNull() ?: 0

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF9FAFB))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nút trừ
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                .clickable {
                    val next = (numericVal - 1).coerceAtLeast(0)
                    onValueChange(next.toString())
                },
            contentAlignment = Alignment.Center
        ) {
            BasicText("-", style = type(16f, FontWeight.Bold, Color(0xFF4B5563)))
        }

        // Ô nhập số ở giữa căn giữa tuyệt đối
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                onValueChange = { newVal ->
                    val filtered = newVal.filter { it.isDigit() }
                    if (filtered.length <= 5) {
                        onValueChange(filtered)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = type(15f, FontWeight.Bold, Color(0xFF111827)).copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(F4fViolet),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) {
                            BasicText(
                                "0",
                                style = type(15f, FontWeight.Bold, Color(0xFF9CA3AF)).copy(textAlign = TextAlign.Center)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Nút cộng
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                .clickable {
                    val next = numericVal + 1
                    onValueChange(next.toString())
                },
            contentAlignment = Alignment.Center
        ) {
            BasicText("+", style = type(16f, FontWeight.Bold, Color(0xFF4B5563)))
        }
    }
}
