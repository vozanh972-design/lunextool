package com.cayxu.app.ui.screens.xsmm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.XsmmRunConfig
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.ui.theme.*

private val XsmmAccent = Color(0xFF16A34A)

/**
 * Màn "Cấu hình chạy" cho XSMM - 7 mục:
 *   1. Thời gian lấy nhiệm vụ (giây) - mặc định 10
 *   2. Thời gian làm nhiệm vụ (giây) - mặc định 10
 *   3. Số nhiệm vụ muốn làm (0 = không giới hạn)
 *   4. Số lần hết NV sẽ dừng - mặc định 100
 *   5. Số lần hoàn thành NV sẽ dừng - mặc định 100
 *   6. Lướt trước khi làm - bật/tắt
 *   7. Trở về Home rồi lướt - bật/tắt
 * Lưu vào XsmmRunConfigStore, đọc lại đúng giá trị đã lưu mỗi lần mở màn.
 */
@Composable
fun XsmmRunConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val saved = remember { XsmmRunConfigStore.get(context) }

    var fetchTaskInterval by remember { mutableStateOf(saved.fetchTaskIntervalSeconds.toString()) }
    var doTaskDuration by remember { mutableStateOf(saved.doTaskDurationSeconds.toString()) }
    var taskCountTarget by remember { mutableStateOf(if (saved.taskCountTarget > 0) saved.taskCountTarget.toString() else "") }
    var stopAfterNoTask by remember { mutableStateOf(saved.stopAfterNoTaskCount.toString()) }
    var stopAfterCompleted by remember { mutableStateOf(saved.stopAfterCompletedCount.toString()) }
    var swipeBeforeTask by remember { mutableStateOf(saved.swipeBeforeTask) }
    var returnHomeAndSwipe by remember { mutableStateOf(saved.returnHomeAndSwipe) }

    fun saveAndBack() {
        XsmmRunConfigStore.save(
            context,
            XsmmRunConfig(
                fetchTaskIntervalSeconds = fetchTaskInterval.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                doTaskDurationSeconds = doTaskDuration.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                taskCountTarget = taskCountTarget.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                stopAfterNoTaskCount = stopAfterNoTask.toIntOrNull()?.coerceAtLeast(1) ?: 100,
                stopAfterCompletedCount = stopAfterCompleted.toIntOrNull()?.coerceAtLeast(1) ?: 100,
                swipeBeforeTask = swipeBeforeTask,
                returnHomeAndSwipe = returnHomeAndSwipe
            )
        )
        navController.popBackStack()
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("Cấu hình chạy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConfigNumberField(
                label = "Thời gian lấy nhiệm vụ",
                suffix = "giây",
                value = fetchTaskInterval,
                onValueChange = { fetchTaskInterval = it }
            )
            ConfigNumberField(
                label = "Thời gian làm nhiệm vụ",
                suffix = "giây",
                value = doTaskDuration,
                onValueChange = { doTaskDuration = it }
            )
            ConfigNumberField(
                label = "Số nhiệm vụ muốn làm",
                suffix = "NV",
                value = taskCountTarget,
                onValueChange = { taskCountTarget = it },
                placeholder = "Để trống = không giới hạn"
            )
            ConfigNumberField(
                label = "Số lần hết NV sẽ dừng",
                suffix = "lần",
                value = stopAfterNoTask,
                onValueChange = { stopAfterNoTask = it }
            )
            ConfigNumberField(
                label = "Số lần hoàn thành NV sẽ dừng",
                suffix = "lần",
                value = stopAfterCompleted,
                onValueChange = { stopAfterCompleted = it }
            )
            ConfigSwitchRow(
                label = "Lướt trước khi làm",
                checked = swipeBeforeTask,
                onCheckedChange = { swipeBeforeTask = it }
            )
            ConfigSwitchRow(
                label = "Trở về Home rồi lướt",
                checked = returnHomeAndSwipe,
                onCheckedChange = { returnHomeAndSwipe = it }
            )
            Spacer(Modifier.height(90.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { saveAndBack() },
                colors = ButtonDefaults.buttonColors(containerColor = XsmmAccent),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Lưu cấu hình", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConfigNumberField(
    label: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { new -> onValueChange(new.filter { it.isDigit() }) },
                placeholder = { if (placeholder != null) Text(placeholder, fontSize = 12.sp) },
                suffix = { Text(suffix, color = TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = XsmmAccent,
                    cursorColor = XsmmAccent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = XsmmAccent)
            )
        }
    }
}
