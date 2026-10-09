package com.cayxu.app.ui.screens.golike

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
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
import com.cayxu.app.automation.tiktok.TikTokCaptureState
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GolikeBrandOrange = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolikeAddAccountBottomSheet(
    platform: String,
    onDismiss: () -> Unit,
    onAccountAdded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var usernameOrUid by remember { mutableStateOf("") }
    var proxyInput by remember { mutableStateOf("") }
    var isScanningTikTok by remember { mutableStateOf(false) }
    var selectedVariant by remember {
        mutableStateOf(
            when {
                TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STANDARD) -> TikTokAppVariant.STANDARD
                TikTokAppLauncher.isInstalled(context, TikTokAppVariant.LITE) -> TikTokAppVariant.LITE
                TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STUDIO) -> TikTokAppVariant.STUDIO
                else -> TikTokAppVariant.STANDARD
            }
        )
    }

    var overlayGranted by remember {
        mutableStateOf(TikTokAppLauncher.isOverlayPermissionGranted(context))
    }
    var accessibilityGranted by remember {
        mutableStateOf(TikTokAppLauncher.isAccessibilityServiceEnabled(context))
    }

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

    LaunchedEffect(Unit) {
        TikTokCaptureBridge.state.collect { state ->
            when (state) {
                is TikTokCaptureState.Captured -> {
                    isScanningTikTok = false
                    val clean = state.handle.trim().removePrefix("@")
                    usernameOrUid = clean
                    try {
                        TikTokAccountsStore.addFromCapture(
                            context = context,
                            handle = clean,
                            displayName = state.displayName.ifBlank { clean },
                            avatarUrl = state.avatarUrl,
                            variant = state.variant
                        )
                    } catch (_: Exception) {}
                    if (platform.lowercase() == "tiktok" && clean.isNotBlank()) {
                        val gAcc = GolikeAccount(
                            id = clean,
                            platform = "tiktok",
                            username = state.displayName.ifBlank { clean },
                            avatar = state.avatarUrl,
                            isLive = true,
                            isGolikeLinked = false,
                            lastStatus = "Cần liên kết Golike"
                        )
                        GolikeAccountsStore.addOrUpdateAccount(context, gAcc)
                    }
                    TikTokCaptureBridge.reset()
                    Toast.makeText(context, "Đã quét thành công nick: @$clean", Toast.LENGTH_SHORT).show()
                    onAccountAdded()
                    onDismiss()
                }
                is TikTokCaptureState.CapturedBatch -> {
                    isScanningTikTok = false
                    val active = state.accounts.firstOrNull { it.isActive } ?: state.accounts.firstOrNull()
                    val clean = active?.handle?.ifBlank { active.displayName }?.trim()?.removePrefix("@").orEmpty()
                    if (clean.isNotBlank()) {
                        usernameOrUid = clean
                        state.accounts.forEach { entry ->
                            val h = entry.handle.ifBlank { entry.displayName }.trim().removePrefix("@")
                            if (h.isNotBlank()) {
                                try {
                                    TikTokAccountsStore.addFromCapture(
                                        context = context,
                                        handle = h,
                                        displayName = entry.displayName,
                                        variant = state.variant
                                    )
                                } catch (_: Exception) {}
                                if (platform.lowercase() == "tiktok") {
                                    val gAcc = GolikeAccount(
                                        id = h,
                                        platform = "tiktok",
                                        username = entry.displayName.ifBlank { h },
                                        avatar = entry.avatarUrl,
                                        isLive = true,
                                        isGolikeLinked = false,
                                        lastStatus = "Cần liên kết Golike"
                                    )
                                    GolikeAccountsStore.addOrUpdateAccount(context, gAcc)
                                }
                            }
                        }
                    }
                    TikTokCaptureBridge.reset()
                    Toast.makeText(context, "Đã quét thành công nick: @$usernameOrUid", Toast.LENGTH_SHORT).show()
                    onAccountAdded()
                    onDismiss()
                }
                is TikTokCaptureState.Failed -> {
                    isScanningTikTok = false
                    Toast.makeText(context, state.reason, Toast.LENGTH_LONG).show()
                    TikTokCaptureBridge.reset()
                }
                else -> Unit
            }
        }
    }

    fun startScanTikTok() {
        val variant = selectedVariant
        if (!TikTokAppLauncher.isInstalled(context, variant)) {
            val variantTitle = when (variant) {
                TikTokAppVariant.STANDARD -> "TikTok"
                TikTokAppVariant.LITE -> "TikTok Lite"
                TikTokAppVariant.STUDIO -> "TikTok Studio"
            }
            Toast.makeText(context, "Chưa cài đặt $variantTitle trên thiết bị này", Toast.LENGTH_SHORT).show()
            return
        }

        if (!overlayGranted) {
            Toast.makeText(context, "Vui lòng cấp quyền Hiển thị trên ứng dụng khác trước khi kiểm tra", Toast.LENGTH_LONG).show()
            TikTokAppLauncher.openOverlayPermissionSettings(context)
            return
        }

        if (!accessibilityGranted) {
            Toast.makeText(context, "Vui lòng bật quyền Trợ năng (Accessibility) trước khi kiểm tra", Toast.LENGTH_LONG).show()
            TikTokAppLauncher.openAccessibilitySettings(context)
            return
        }

        // Tái sử dụng nguyên bản Màn nổi kiểm tra TikTok của XSMM
        com.cayxu.app.ui.overlay.xsmm.startXsmmVerifyTikTokAccount(context, variant)
        onDismiss()
    }

    val platformTitle = when (platform.lowercase()) {
        "facebook" -> "Facebook"
        "instagram" -> "Instagram"
        else -> "TikTok"
    }

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
            if (platform.lowercase() == "tiktok") {
                // 1. Header Tiêu Đề theo chuẩn Ảnh 2
                Text(
                    text = "Kiểm tra tài khoản TikTok",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Màn hình nổi & Trợ năng cần được cấp quyền để tự động mở TikTok và kiểm tra trạng thái nick.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(18.dp))

                // 2. Hai Thẻ Kiểm Tra Quyền Hệ Thống (Permission Cards)
                // Thẻ 1: Quyền Hiển thị trên ứng dụng khác (Overlay)
                GolikePermissionCard(
                    title = "Hiển thị trên ứng dụng khác",
                    desc = "Để hiện màn nổi (overlay) kiểm tra và điều khiển trên TikTok",
                    granted = overlayGranted,
                    onClick = { TikTokAppLauncher.openOverlayPermissionSettings(context) }
                )

                Spacer(Modifier.height(10.dp))

                // Thẻ 2: Dịch vụ Trợ năng (Accessibility)
                GolikePermissionCard(
                    title = "Dịch vụ Trợ năng (Accessibility)",
                    desc = "Để tự động bấm tab \"Tôi\" và kiểm tra @username TikTok",
                    granted = accessibilityGranted,
                    onClick = { TikTokAppLauncher.openAccessibilitySettings(context) }
                )

                Spacer(Modifier.height(20.dp))

                // 3. Khu Vực Chọn Phiên Bản TikTok
                Text(
                    text = "Chọn ứng dụng cần kiểm tra:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Pair(TikTokAppVariant.STANDARD, "TikTok"),
                        Pair(TikTokAppVariant.LITE, "TikTok Lite"),
                        Pair(TikTokAppVariant.STUDIO, "TikTok Studio")
                    ).forEach { (variant, label) ->
                        val isSelected = selectedVariant == variant
                        val isInstalled = TikTokAppLauncher.isInstalled(context, variant)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0xFF1A1D20)
                                    else Color(0xFFF8F9FA)
                                )
                                .clickable { selectedVariant = variant }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = if (isInstalled) "Đã cài" else "Chưa cài",
                                    fontSize = 10.5.sp,
                                    color = if (isSelected) Color(0xFF94A3B8) else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 4. Nút Hành Động Chính (Main Action Button)
                Button(
                    onClick = { startScanTikTok() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF111827),
                        disabledContainerColor = Color(0xFF374151)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kiểm tra tài khoản",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(16.dp))
            } else {
                // Header cho Facebook / Instagram
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
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = GolikeBrandOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Thêm tài khoản $platformTitle",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Quản lý & làm nhiệm vụ cho Golike",
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

                // Tên tài khoản / UID
                Text(
                    text = "Tên đăng nhập / UID / @handle $platformTitle",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = usernameOrUid,
                    onValueChange = { usernameOrUid = it },
                    placeholder = {
                        Text(
                            when (platform.lowercase()) {
                                "facebook" -> "Nhập UID Facebook hoặc link trang"
                                "instagram" -> "Nhập username Instagram (ví dụ: nguyen_van_a)"
                                else -> "Nhập UID hoặc tên đăng nhập"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // Proxy (tùy chọn)
                Text(
                    text = "Proxy gắn riêng cho nick (Tùy chọn: host:port hoặc host:port:user:pass)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = proxyInput,
                    onValueChange = { proxyInput = it },
                    placeholder = { Text("127.0.0.1:8080:user:pass") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Nút Thêm tài khoản Facebook / Instagram
                Button(
                    onClick = {
                        val rawInput = usernameOrUid.trim()
                        if (rawInput.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập tên tài khoản hoặc UID", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val cleanId = rawInput.removePrefix("@").trim()
                        val newAccount = GolikeAccount(
                            id = cleanId,
                            platform = platform.lowercase(),
                            username = rawInput,
                            isLive = true,
                            isGolikeLinked = true,
                            proxy = proxyInput.trim(),
                            lastStatus = "Đã thêm vào Golike • Sẵn sàng"
                        )
                        GolikeAccountsStore.addOrUpdateAccount(context, newAccount)
                        Toast.makeText(context, "Đã thêm tài khoản $cleanId vào Golike!", Toast.LENGTH_SHORT).show()
                        onAccountAdded()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GolikeBrandOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Lưu tài khoản vào danh sách",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GolikePermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8F9FA))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        if (granted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFE6F4EA))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Đã cấp",
                    color = Color(0xFF137333),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "Cấp quyền",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
