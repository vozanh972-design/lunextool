package com.cayxu.app.ui.screens.settings

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.cayxu.app.BuildConfig
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.DangerRed
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.SuccessGreen
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }

    // "Giữ màn hình sáng" là công tắc thật, tác động trực tiếp lên cửa sổ Activity hiện tại
    // (không phải mock) - bật/tắt FLAG_KEEP_SCREEN_ON ngay khi gạt.
    var keepScreenOn by remember { mutableStateOf(false) }

    // TODO: các mục dưới đây (thông báo đẩy, email, ngôn ngữ...) hiện chưa có hệ thống
    // backend/tuỳ chỉnh tương ứng, chỉ là công tắc UI cục bộ để hoàn thiện màn hình theo
    // ảnh mẫu, chưa lưu lại hay tác động thật. Riêng "Chế độ tối" bên dưới là công tắc THẬT,
    // đổi màu toàn app ngay lập tức (xem ThemeState.kt).
    var pushNotifications by remember { mutableStateOf(true) }
    var emailNotifications by remember { mutableStateOf(true) }

    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Cài đặt",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SettingsSectionLabel("Thông báo")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    iconColor = Primary,
                    title = "Thông báo đẩy",
                    checked = pushNotifications,
                    onCheckedChange = { pushNotifications = it }
                )
                SettingsSwitchRow(
                    icon = Icons.Filled.Email,
                    iconColor = Primary,
                    title = "Email",
                    checked = emailNotifications,
                    onCheckedChange = { emailNotifications = it }
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionLabel("Giao diện")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Filled.LightMode,
                    iconColor = Color(0xFFF59E0B),
                    title = "Giữ màn hình sáng",
                    checked = keepScreenOn,
                    onCheckedChange = { checked ->
                        keepScreenOn = checked
                        val activity = context as? Activity
                        if (checked) {
                            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                )
                SettingsSwitchRow(
                    icon = Icons.Filled.DarkMode,
                    iconColor = TextSecondary,
                    title = "Chế độ tối",
                    checked = com.cayxu.app.ui.theme.ThemeState.isDarkMode,
                    onCheckedChange = { checked ->
                        com.cayxu.app.ui.theme.ThemeState.setDarkMode(context, checked)
                    }
                )
                SettingsRow(
                    icon = Icons.Filled.Language,
                    iconColor = Primary,
                    title = "Ngôn ngữ",
                    trailingText = "Tiếng Việt",
                    onClick = { }
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionLabel("Hỗ trợ")
            SettingsGroup {
                SettingsRow(icon = Icons.Filled.HelpOutline, iconColor = Color(0xFF0EA5E9), title = "Trung tâm hỗ trợ", onClick = { })
                SettingsRow(icon = Icons.Filled.Description, iconColor = Color(0xFF7C3AED), title = "Điều khoản sử dụng", onClick = { })
                SettingsRow(icon = Icons.Filled.Shield, iconColor = SuccessGreen, title = "Chính sách bảo mật", onClick = { })
                SettingsRow(
                    icon = Icons.Filled.Info,
                    iconColor = TextSecondary,
                    title = "Phiên bản ứng dụng",
                    trailingText = BuildConfig.VERSION_NAME,
                    onClick = { }
                )
            }

            Spacer(Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().clickable { showLogoutConfirm = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng xuất", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Đăng xuất") },
            text = { Text("Bạn có chắc muốn đăng xuất? Key đã lưu trên máy sẽ bị xoá, bạn cần nhập lại key ở lần mở app sau.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    // Xoá key đăng nhập đã lưu (EncryptedSharedPreferences) rồi quay về màn nhập key,
                    // xoá sạch back stack để không thể bấm Back quay lại các màn cần đăng nhập.
                    securePrefs.clearKey()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text("Đăng xuất", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Huỷ")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(trailingText, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CardWhite, checkedTrackColor = Primary)
        )
    }
}
