package com.cayxu.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Màn "Nhiệm vụ" dùng CHUNG cho 3 dịch vụ: Trao đổi Sub, Tương tác chéo, XSMM - chỉ hỗ trợ
 * Facebook + TikTok (không có đủ các nền tảng như GoLike trước đây). CHƯA có API/backend
 * riêng cho từng dịch vụ này (khác GoLike đã bị gỡ bỏ hoàn toàn) - nên màn này CHỈ hiển thị
 * danh sách tài khoản đã lưu sẵn trong máy (dùng lại TikTokAccountsStore/FacebookAccountsStore
 * đã có), nút "Chạy" hiện tại chỉ là placeholder (báo chưa có API), sẵn sàng nối vào khi có
 * backend thật cho dịch vụ tương ứng.
 */
@Composable
fun SimpleTaskPlatformScreen(navController: NavController, service: String) {
    val context = LocalContext.current
    val accent = accentColorFor(service)
    val displayName = displayNameFor(service)

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Facebook, 1 = TikTok
    var selectedAccountUid by remember(selectedTabIndex) { mutableStateOf<String?>(null) }

    val facebookAccounts = remember { FacebookAccountsStore.getAccounts(context) }
    val tikTokAccounts = remember { TikTokAccountsStore.getAccounts(context).filter { it.enabled } }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // ---- Tab chọn nền tảng: CHỈ Facebook + TikTok ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlatformTabChip(
                label = "Facebook",
                icon = Icons.Filled.Facebook,
                count = facebookAccounts.size,
                isSelected = selectedTabIndex == 0,
                accent = accent,
                onClick = { selectedTabIndex = 0 }
            )
            PlatformTabChip(
                label = "TikTok",
                icon = Icons.Filled.MusicNote,
                count = tikTokAccounts.size,
                isSelected = selectedTabIndex == 1,
                accent = accent,
                onClick = { selectedTabIndex = 1 }
            )
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedTabIndex == 0) {
                if (facebookAccounts.isEmpty()) {
                    EmptyAccountsHint("Chưa có tài khoản Facebook nào - thêm ở phần Quản lý tài khoản Facebook trước.")
                } else {
                    facebookAccounts.forEach { acc ->
                        SimpleAccountCard(
                            title = acc.name.ifBlank { "Chưa đặt tên" },
                            subtitle = acc.link.ifBlank { "Chưa có link" },
                            isSelected = acc.uid == selectedAccountUid,
                            accent = accent,
                            onClick = { selectedAccountUid = acc.uid }
                        )
                    }
                }
            } else {
                if (tikTokAccounts.isEmpty()) {
                    EmptyAccountsHint("Chưa có tài khoản TikTok nào - thêm ở phần Quản lý tài khoản TikTok trước.")
                } else {
                    tikTokAccounts.forEach { acc ->
                        SimpleAccountCard(
                            title = "@${acc.handle.ifBlank { "chưa_rõ" }}",
                            subtitle = acc.displayName.ifBlank { "Chưa có tên hiển thị" },
                            isSelected = acc.uid == selectedAccountUid,
                            accent = accent,
                            onClick = { selectedAccountUid = acc.uid }
                        )
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }

        // ---- Nút Chạy - CHƯA nối API thật (chưa có backend cho dịch vụ này) ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    android.widget.Toast.makeText(
                        context,
                        "$displayName chưa có API - tính năng đang chờ backend",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chạy")
            }
        }
    }
}

private fun accentColorFor(service: String): Color = when (service) {
    "Traodoisub" -> Color(0xFF2563EB)
    "Tuongtaccheo" -> Color(0xFFEC4899)
    "XSMM" -> Color(0xFF16A34A)
    else -> Color(0xFF2563EB)
}

private fun displayNameFor(service: String): String = when (service) {
    "Traodoisub" -> "Trao đổi Sub"
    "Tuongtaccheo" -> "Tương tác chéo"
    "XSMM" -> "XSMM"
    else -> service
}

@Composable
private fun PlatformTabChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (isSelected) accent.copy(alpha = 0.12f) else CardWhite, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent else Color(0xFFEEF1F5),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) accent else TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label ($count)",
            color = if (isSelected) accent else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SimpleAccountCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) accent.copy(alpha = 0.06f) else CardWhite),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accent else Color(0xFFEEF1F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyAccountsHint(text: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, color = TextSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
