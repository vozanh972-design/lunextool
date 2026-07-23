package com.cayxu.app.ui.screens.nurture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cayxu.app.data.local.NurtureConfig
import com.cayxu.app.data.local.NurtureConfigStore
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.navigation.goHome
import com.cayxu.app.ui.theme.*

private fun TikTokAppVariant.displayLabel(): String = when (this) {
    TikTokAppVariant.STANDARD -> "TikTok"
    TikTokAppVariant.LITE -> "TikTok Lite"
    TikTokAppVariant.STUDIO -> "TikTok Studio"
}

@Composable
fun NurtureSetupScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedVariant by remember { mutableStateOf(TikTokAppVariant.STANDARD) }
    var accounts by remember { mutableStateOf(listOf<TikTokAccount>()) }
    val selectedUids = remember { mutableStateListOf<String>() }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf(NurtureConfig()) }

    LaunchedEffect(selectedVariant) {
        accounts = TikTokAccountsStore.getAccounts(context).filter { it.variant == selectedVariant }
        selectedUids.clear()
    }
    LaunchedEffect(Unit) {
        config = NurtureConfigStore.getConfig(context)
    }

    val allSelected = accounts.isNotEmpty() && selectedUids.size == accounts.size

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.goHome() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Về trang chủ", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("Nuôi tài khoản", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // Chọn nền tảng
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TikTokAppVariant.entries.forEach { variant ->
                val selected = variant == selectedVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Primary else CardWhite)
                        .clickable { selectedVariant = variant }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        variant.displayLabel(),
                        color = if (selected) Color.White else TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Chọn tất cả
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable {
                    if (allSelected) selectedUids.clear() else {
                        selectedUids.clear()
                        selectedUids.addAll(accounts.map { it.uid })
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (allSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (allSelected) Primary else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Chọn tất cả (${accounts.size} tài khoản)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(8.dp))

        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Chưa có tài khoản ${selectedVariant.displayLabel()} nào - hãy liên kết tài khoản trước.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.uid }) { account ->
                    val checked = selectedUids.contains(account.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardWhite)
                            .clickable {
                                if (checked) selectedUids.remove(account.uid) else selectedUids.add(account.uid)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (checked) Primary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                account.displayName.ifBlank { account.handle.ifBlank { "TikTok" } },
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (account.handle.isNotBlank()) {
                                Text(account.handle, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }

        // Nút cấu hình + bắt đầu - CỐ ĐỊNH ở đáy màn hình (Surface riêng có elevation để tách
        // biệt rõ với danh sách acc cuộn ở trên, không bị trôi theo khi cuộn danh sách).
        Surface(tonalElevation = 0.dp, shadowElevation = 8.dp, color = CardWhite) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showConfigDialog = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Cấu hình nuôi") }

                Button(
                    onClick = {
                        if (!com.cayxu.app.automation.tiktok.TikTokAppLauncher.isOverlayPermissionGranted(context)) {
                            showOverlayPermissionDialog = true
                            return@Button
                        }
                        startNurture(context, selectedVariant, config.durationMinutes)
                    },
                    enabled = selectedUids.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Nuôi tài khoản") }
            }
        }
    }

    if (showConfigDialog) {
        NurtureConfigDialog(
            initial = config,
            onDismiss = { showConfigDialog = false },
            onSave = {
                config = it
                NurtureConfigStore.saveConfig(context, it)
                showConfigDialog = false
            }
        )
    }

    if (showOverlayPermissionDialog) {
        OverlayPermissionDialog(
            onDismiss = { showOverlayPermissionDialog = false },
            onGranted = {
                showOverlayPermissionDialog = false
                startNurture(context, selectedVariant, config.durationMinutes)
            }
        )
    }
}

/**
 * Xin quyền "Hiển thị trên ứng dụng khác" TRƯỚC khi mở lớp nổi Nuôi tài khoản. Tự động đóng
 * dialog và chạy tiếp ngay khi quay lại app mà đã thấy quyền được cấp - không cần bấm thêm.
 */
@Composable
private fun OverlayPermissionDialog(onDismiss: () -> Unit, onGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                com.cayxu.app.automation.tiktok.TikTokAppLauncher.isOverlayPermissionGranted(context)
            ) {
                onGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cần quyền hiển thị lớp nổi") },
        text = {
            Text(
                "Nuôi tài khoản cần quyền \"Hiển thị trên ứng dụng khác\" để hiện đồng hồ đếm ngược, " +
                    "hạn key và nút Dừng ngay trên màn hình TikTok. Cấp quyền xong quay lại app là tool tự chạy tiếp."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                com.cayxu.app.automation.tiktok.TikTokAppLauncher.openOverlayPermissionSettings(context)
            }) { Text("Cấp quyền") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        }
    )
}

/** Mở app TikTok theo phiên bản đang chọn rồi bật lớp nổi đếm giờ nuôi tài khoản. */
private fun startNurture(context: android.content.Context, variant: TikTokAppVariant, durationMinutes: Int) {
    com.cayxu.app.automation.tiktok.TikTokAppLauncher.launch(context, variant)
    context.startService(
        android.content.Intent(context, com.cayxu.app.automation.nurture.NurtureOverlayService::class.java)
            .putExtra(com.cayxu.app.automation.nurture.NurtureOverlayService.EXTRA_DURATION_MINUTES, durationMinutes)
    )
}

private val DURATION_PRESETS = listOf(15, 30, 60)

@Composable
private fun NurtureConfigDialog(
    initial: NurtureConfig,
    onDismiss: () -> Unit,
    onSave: (NurtureConfig) -> Unit
) {
    var autoWatch by remember { mutableStateOf(initial.autoWatch) }
    var viewComments by remember { mutableStateOf(initial.viewComments) }
    var copyLink by remember { mutableStateOf(initial.copyLink) }
    var repost by remember { mutableStateOf(initial.repost) }

    val initialIsPreset = initial.durationMinutes in DURATION_PRESETS
    var selectedPreset by remember { mutableStateOf(if (initialIsPreset) initial.durationMinutes else null) }
    var customMinutesText by remember { mutableStateOf(if (initialIsPreset) "" else initial.durationMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cấu hình nuôi tài khoản") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ConfigSwitchRow("Tự động xem video", autoWatch, null) { autoWatch = it }
                ConfigSwitchRow(
                    "Xem bình luận",
                    viewComments,
                    "Cứ vài video lại mở phần bình luận đọc thử rồi đóng"
                ) { viewComments = it }
                ConfigSwitchRow(
                    "Sao chép liên kết",
                    copyLink,
                    "Thi thoảng mở chia sẻ rồi sao chép liên kết"
                ) { copyLink = it }
                ConfigSwitchRow(
                    "Đăng lại",
                    repost,
                    "Thi thoảng đăng lại"
                ) { repost = it }

                Text("Thời gian nuôi mỗi lần", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DURATION_PRESETS.forEach { minutes ->
                        val selected = selectedPreset == minutes
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Primary else AppBackground)
                                .clickable {
                                    selectedPreset = minutes
                                    customMinutesText = ""
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$minutes phút",
                                color = if (selected) Color.White else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    val isCustomSelected = selectedPreset == null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCustomSelected) Primary else AppBackground)
                            .clickable { selectedPreset = null }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Khác",
                            color = if (isCustomSelected) Color.White else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (selectedPreset == null) {
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { v -> customMinutesText = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Nhập số phút") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = selectedPreset ?: (customMinutesText.toIntOrNull()?.coerceIn(1, 999) ?: 15)
                onSave(
                    NurtureConfig(
                        autoWatch = autoWatch,
                        viewComments = viewComments,
                        copyLink = copyLink,
                        repost = repost,
                        durationMinutes = minutes
                    )
                )
            }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        }
    )
}

@Composable
private fun ConfigSwitchRow(label: String, checked: Boolean, description: String?, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(description, fontSize = 11.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
