package com.cayxu.app.ui.screens.xsmm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Add

private val XsmmAccent = Color(0xFF16A34A)

/**
 * Màn "Cấu hình chạy" cho XSMM.
 * Loại nhiệm vụ: toggle switch bật/tắt từng loại (multi-select).
 * Logic chạy: xoay vòng qua các loại đã bật; hết NV → đợi 10s rồi chuyển loại tiếp.
 */
@Composable
fun XsmmRunConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val activePlatform = remember { XsmmRunConfigStore.getActivePlatform(context) }
    val saved = remember(activePlatform) { XsmmRunConfigStore.get(context, activePlatform) }

    var platform by remember { mutableStateOf(saved.platform) }
    // Multi-select: set các taskType đang bật
    var selectedTaskTypes by remember {
        mutableStateOf(
            saved.effectiveTaskTypes().toSet().ifEmpty {
                setOf(saved.taskType)
            }
        )
    }
    var fetchTaskInterval by remember { mutableStateOf(saved.fetchTaskIntervalSeconds.toString()) }
    var doTaskDuration by remember { mutableStateOf(saved.doTaskDurationSeconds.toString()) }
    var taskCountTarget by remember { mutableStateOf(if (saved.taskCountTarget > 0) saved.taskCountTarget.toString() else "") }
    var stopAfterNoTask by remember { mutableStateOf(saved.stopAfterNoTaskCount.toString()) }
    var stopAfterCompleted by remember { mutableStateOf(saved.stopAfterCompletedCount.toString()) }
    var failJobCountToSwitch by remember { mutableStateOf(saved.failJobCountToSwitchAccount.toString()) }
    var swipeBeforeTask by remember { mutableStateOf(saved.swipeBeforeTask) }
    var returnHomeAndSwipe by remember { mutableStateOf(saved.returnHomeAndSwipe) }
    var page615ReactionMethod by remember { mutableStateOf(saved.page615ReactionMethod) }

    var showInstagramCookieSheet by remember { mutableStateOf(false) }
    var showFacebookLoginSheet by remember { mutableStateOf(false) }

    fun saveAndBack() {
        val types = selectedTaskTypes.toList()
        XsmmRunConfigStore.save(
            context,
            XsmmRunConfig(
                platform = platform,
                taskType = types.firstOrNull() ?: XsmmRunConfigStore.defaultTaskTypeFor(platform),
                taskTypes = types,
                fetchTaskIntervalSeconds = fetchTaskInterval.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                doTaskDurationSeconds = doTaskDuration.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                taskCountTarget = taskCountTarget.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                stopAfterNoTaskCount = stopAfterNoTask.toIntOrNull()?.coerceAtLeast(1) ?: 100,
                stopAfterCompletedCount = stopAfterCompleted.toIntOrNull()?.coerceAtLeast(1) ?: 100,
                failJobCountToSwitchAccount = failJobCountToSwitch.toIntOrNull()?.coerceAtLeast(1) ?: 50,
                swipeBeforeTask = swipeBeforeTask,
                returnHomeAndSwipe = returnHomeAndSwipe,
                page615ReactionMethod = page615ReactionMethod
            )
        )
        navController.popBackStack()
    }

    if (showInstagramCookieSheet) {
        InstagramCookieBottomSheet(onDismiss = { showInstagramCookieSheet = false })
    }
    if (showFacebookLoginSheet) {
        FacebookLoginBottomSheet(onDismiss = { showFacebookLoginSheet = false })
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
            Text("Cấu hình chạy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chọn nền tảng
            ConfigPlatformSelector(
                selectedPlatform = platform,
                onSelectPlatform = { newPlat ->
                    platform = newPlat
                    val platConfig = XsmmRunConfigStore.get(context, newPlat)
                    selectedTaskTypes = platConfig.effectiveTaskTypes().toSet().ifEmpty { setOf(platConfig.taskType) }
                    fetchTaskInterval = platConfig.fetchTaskIntervalSeconds.toString()
                    doTaskDuration = platConfig.doTaskDurationSeconds.toString()
                    taskCountTarget = if (platConfig.taskCountTarget > 0) platConfig.taskCountTarget.toString() else ""
                    stopAfterNoTask = platConfig.stopAfterNoTaskCount.toString()
                    stopAfterCompleted = platConfig.stopAfterCompletedCount.toString()
                    failJobCountToSwitch = platConfig.failJobCountToSwitchAccount.toString()
                    swipeBeforeTask = platConfig.swipeBeforeTask
                    returnHomeAndSwipe = platConfig.returnHomeAndSwipe
                    page615ReactionMethod = platConfig.page615ReactionMethod
                }
            )

            // Loại nhiệm vụ — toggle switches
            ConfigTaskTypeToggleGroup(
                platform = platform,
                selectedTypes = selectedTaskTypes,
                onToggle = { typeKey, isOn ->
                    selectedTaskTypes = if (isOn) selectedTaskTypes + typeKey
                               else selectedTaskTypes - typeKey
                }
            )


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
                label = "Số job lỗi / nhả sẽ đổi acc",
                suffix = "job",
                value = failJobCountToSwitch,
                onValueChange = { failJobCountToSwitch = it },
                placeholder = "Mặc định = 50 job"
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

/** Card chứa toggle switch cho từng loại nhiệm vụ của platform. */
@Composable
private fun ConfigTaskTypeToggleGroup(
    platform: String,
    selectedTypes: Set<String>,
    onToggle: (typeKey: String, isOn: Boolean) -> Unit
) {
    val options = XsmmRunConfigStore.taskTypesFor(platform)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Loại nhiệm vụ",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Bật nhiều loại → xoay vòng; hết NV đợi 10s rồi sang loại kế",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            options.forEachIndexed { index, (typeKey, label) ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color(0xFFF0F0F0),
                        thickness = 0.5.dp
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = typeKey in selectedTypes,
                        onCheckedChange = { isOn -> onToggle(typeKey, isOn) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = XsmmAccent,
                            uncheckedTrackColor = Color(0xFFE0E0E0),
                            uncheckedThumbColor = Color.White
                        )
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigPlatformSelector(
    selectedPlatform: String,
    onSelectPlatform: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val platforms = XsmmRunConfigStore.supportedPlatforms
    val currentLabel = platforms.firstOrNull { it.first == selectedPlatform }?.second ?: selectedPlatform

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Chọn nền tảng chạy", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = XsmmAccent,
                        cursorColor = XsmmAccent
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    platforms.forEach { (platKey, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (platKey == selectedPlatform) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { onSelectPlatform(platKey); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

