package com.cayxu.app.ui.screens.linkaccount.tiktok

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.TikTokCaptureBridge
import com.cayxu.app.automation.tiktok.TikTokCaptureOverlayService
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.theme.*

private data class TikTokTypeOption(
    val variant: TikTokAppVariant,
    val title: String,
    val subtitle: String
)

private val tikTokTypeOptions = listOf(
    TikTokTypeOption(TikTokAppVariant.STANDARD, "TikTok", "Phiên bản tiêu chuẩn"),
    TikTokTypeOption(TikTokAppVariant.LITE, "TikTok Lite", "Phiên bản rút gọn, nhẹ hơn"),
    TikTokTypeOption(TikTokAppVariant.STUDIO, "TikTok Studio", "Dành cho nhà sáng tạo nội dung")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTokAddAccountSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<TikTokAppVariant?>(null) }
    var showPermissionStep by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardWhite) {
        if (!showPermissionStep) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Chọn loại TikTok", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Vui lòng chọn phiên bản muốn thêm tài khoản",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))

                tikTokTypeOptions.forEach { option ->
                    TikTokTypeRow(
                        option = option,
                        isSelected = selected == option.variant,
                        onClick = { selected = option.variant }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        val variant = selected
                        if (variant == null) {
                            Toast.makeText(context, "Vui lòng chọn một loại TikTok", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!TikTokAppLauncher.isInstalled(context, variant)) {
                            Toast.makeText(
                                context,
                                "Chưa cài đặt ${optionTitle(variant)} trên máy này",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        showPermissionStep = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("+  Thêm tài khoản", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
        } else {
            val variant = selected ?: TikTokAppVariant.LITE
            TikTokPermissionStep(
                variant = variant,
                onBack = { showPermissionStep = false },
                onGranted = {
                    TikTokCaptureBridge.startWaiting(variant)
                    context.startService(
                        Intent(context, TikTokCaptureOverlayService::class.java)
                            .putExtra(TikTokCaptureOverlayService.EXTRA_VARIANT, variant.name)
                    )
                    val launched = TikTokAppLauncher.launch(context, variant)
                    if (!launched) {
                        Toast.makeText(context, "Không mở được ${optionTitle(variant)}", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            )
        }
    }
}

private fun optionTitle(variant: TikTokAppVariant): String =
    tikTokTypeOptions.first { it.variant == variant }.title

@Composable
private fun TikTokTypeRow(option: TikTokTypeOption, isSelected: Boolean, onClick: () -> Unit) {
    val icon = when (option.variant) {
        TikTokAppVariant.STANDARD -> Icons.Filled.MusicNote
        TikTokAppVariant.LITE -> Icons.Filled.Bolt
        TikTokAppVariant.STUDIO -> Icons.Filled.Layers
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Primary.copy(alpha = 0.08f) else AppBackground)
            .then(Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(CardWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) Primary else TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(option.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(option.subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        if (isSelected) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = CardWhite, modifier = Modifier.size(14.dp))
            }
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

/**
 * Bước xin quyền TRƯỚC khi mở app TikTok: quyền hiển thị lớp nổi (để hiện trạng thái)
 * và quyền Trợ năng (để tool tự bấm tab "Tôi" và tự đọc @handle - không cần người
 * dùng thao tác tay). Ngay khi cả 2 quyền đã được cấp, tự động chạy tiếp luôn.
 */
@Composable
private fun TikTokPermissionStep(variant: TikTokAppVariant, onBack: () -> Unit, onGranted: () -> Unit) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(TikTokAppLauncher.isOverlayPermissionGranted(context)) }
    var accessibilityGranted by remember { mutableStateOf(TikTokAppLauncher.isAccessibilityServiceEnabled(context)) }
    var autoStarted by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = TikTokAppLauncher.isOverlayPermissionGranted(context)
                accessibilityGranted = TikTokAppLauncher.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tự động chạy tiếp ngay khi đủ 2 quyền, không cần người dùng bấm nút nào cả.
    LaunchedEffect(overlayGranted, accessibilityGranted) {
        if (overlayGranted && accessibilityGranted && !autoStarted) {
            autoStarted = true
            onGranted()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Cấp quyền cho ${optionTitle(variant)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cần 2 quyền sau để tool tự động mở ${optionTitle(variant)}, tự bấm tab \"Tôi\" và tự đọc @ - bạn không cần thao tác gì thêm.",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))

        PermissionRow(
            title = "Hiển thị trên ứng dụng khác",
            desc = "Để hiện trạng thái đang xử lý trên app TikTok",
            granted = overlayGranted,
            onClick = { TikTokAppLauncher.openOverlayPermissionSettings(context) }
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            title = "Trợ năng (Accessibility)",
            desc = "Để tool tự bấm tab \"Tôi\" và tự đọc @ giúp bạn",
            granted = accessibilityGranted,
            onClick = { TikTokAppLauncher.openAccessibilitySettings(context) }
        )

        Spacer(Modifier.height(18.dp))
        if (overlayGranted && accessibilityGranted) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Đã đủ quyền, đang tự mở ${optionTitle(variant)}...", fontSize = 12.sp, color = TextSecondary)
            }
        } else {
            Text("Cấp đủ 2 quyền ở trên, tool sẽ tự chạy tiếp ngay, không cần bấm gì thêm.", fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Quay lại", color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppBackground)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = TextSecondary)
        }
        if (granted) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(SuccessGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Đã cấp", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Cấp quyền", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
