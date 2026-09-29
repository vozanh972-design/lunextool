package com.cayxu.app.ui.screens.golike

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

private val GolikeBrandOrange = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolikeConfigBottomSheet(
    platform: String,
    onDismiss: () -> Unit,
    onConfigSaved: (GolikeRunConfig) -> Unit
) {
    val context = LocalContext.current
    val currentConfig = remember(platform) { GolikeRunConfigStore.get(context, platform) }

    var delayMin by remember { mutableStateOf(currentConfig.delayMinSeconds.toString()) }
    var delayMax by remember { mutableStateOf(currentConfig.delayMaxSeconds.toString()) }
    var targetCount by remember { mutableStateOf(currentConfig.taskCountTarget.toString()) }
    var failSwitchCount by remember { mutableStateOf(currentConfig.failJobCountToSwitchAccount.toString()) }
    var autoSwitch by remember { mutableStateOf(currentConfig.autoSwitchOnFail) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GolikeBrandOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = GolikeBrandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Cấu hình làm nhiệm vụ Golike",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Áp dụng cho ${platform.replaceFirstChar { it.uppercase() }}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(18.dp))

            // 1. Thời gian nghỉ giữa các nhiệm vụ (Delay Min - Max)
            Text(
                text = "Thời gian nghỉ giữa các Job (giây)",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = delayMin,
                    onValueChange = { if (it.all { c -> c.isDigit() }) delayMin = it },
                    label = { Text("Tối thiểu (Min)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = delayMax,
                    onValueChange = { if (it.all { c -> c.isDigit() }) delayMax = it },
                    label = { Text("Tối đa (Max)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // 2. Số lượng nhiệm vụ tối đa
            Text(
                text = "Số lượng nhiệm vụ tối đa (0 = Không giới hạn)",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = targetCount,
                onValueChange = { if (it.all { c -> c.isDigit() }) targetCount = it },
                label = { Text("Số job mục tiêu") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // 3. Tự động chuyển nick khi gặp lỗi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tự động đổi nick khi lỗi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tự động nhảy sang tài khoản tiếp theo nếu gặp lỗi liên tiếp",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = autoSwitch,
                    onCheckedChange = { autoSwitch = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GolikeBrandOrange)
                )
            }

            if (autoSwitch) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = failSwitchCount,
                    onValueChange = { if (it.all { c -> c.isDigit() }) failSwitchCount = it },
                    label = { Text("Số lần lỗi liên tiếp thì đổi nick") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            // Nút Lưu cấu hình
            Button(
                onClick = {
                    val dMin = delayMin.toIntOrNull() ?: 8
                    val dMax = delayMax.toIntOrNull() ?: 15
                    val tTarget = targetCount.toIntOrNull() ?: 0
                    val fSwitch = failSwitchCount.toIntOrNull() ?: 5

                    val newConfig = GolikeRunConfig(
                        platform = platform.lowercase(),
                        delayMinSeconds = dMin.coerceAtLeast(1),
                        delayMaxSeconds = dMax.coerceAtLeast(dMin),
                        taskCountTarget = tTarget,
                        failJobCountToSwitchAccount = fSwitch.coerceAtLeast(1),
                        autoSwitchOnFail = autoSwitch
                    )

                    GolikeRunConfigStore.save(context, newConfig)
                    Toast.makeText(context, "Đã lưu cấu hình làm nhiệm vụ Golike!", Toast.LENGTH_SHORT).show()
                    onConfigSaved(newConfig)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GolikeBrandOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lưu cấu hình",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
