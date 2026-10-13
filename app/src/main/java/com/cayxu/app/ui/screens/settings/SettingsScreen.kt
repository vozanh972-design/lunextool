package com.cayxu.app.ui.screens.settings

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.cayxu.app.BuildConfig
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.worker.AppAlertNotifier
import com.cayxu.app.worker.AppBackgroundService

import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.locale.Str
import com.cayxu.app.ui.theme.*

private val FigmaBg: Color @Composable get() = AppBackground
private val FigmaCardBg: Color @Composable get() = CardWhite
private val FigmaBorder: Color @Composable get() = BorderLight
private val FigmaDivider: Color @Composable get() = BorderLight
private val FigmaTextPrimary: Color @Composable get() = TextPrimary
private val FigmaTextHeader: Color @Composable get() = TextPrimary
private val FigmaTextSection: Color @Composable get() = TextSecondary
private val FigmaTextMuted: Color @Composable get() = TextSecondary
private val FigmaBlue: Color @Composable get() = Primary
private val FigmaIconBg: Color @Composable get() = InfoBlueBg
private val FigmaDanger: Color @Composable get() = DangerRed

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }

    var keepScreenOn by remember { mutableStateOf(false) }
    var pushNotifications by remember { mutableStateOf(AppAlertNotifier.isPushEnabled(context)) }
    var backgroundServiceEnabled by remember { mutableStateOf(AppBackgroundService.isEnabled(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            AppBackgroundService.start(context)
        }
    }

    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FigmaBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // 📌 1. TOP BAR TIÊU ĐỀ
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FigmaCardBg)
                    .border(1.dp, FigmaBorder, RoundedCornerShape(12.dp))
                    .clickable { navController.popBackStack() }
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = Str.settingsBack,
                    tint = FigmaTextHeader,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = Str.settingsTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = FigmaTextHeader,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))

            // 📌 2. NHÓM THÔNG BÁO
            SettingsSectionLabel(Str.settingsSectionNotifications)
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Notifications,
                    title = Str.settingsPushNotifications,
                    checked = pushNotifications,
                    onCheckedChange = { checked ->
                        pushNotifications = checked
                        AppAlertNotifier.setPushEnabled(context, checked)
                        if (checked) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasPerm) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            AppAlertNotifier.createChannel(context)
                            Toast.makeText(context, Str.settingsPushEnabledToast, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, Str.settingsPushDisabledToast, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HorizontalDivider(color = FigmaDivider, thickness = 0.5.dp)
                SettingsSwitchRow(
                    icon = Icons.Outlined.Sync,
                    title = Str.settingsBackgroundService,
                    checked = backgroundServiceEnabled,
                    onCheckedChange = { checked ->
                        backgroundServiceEnabled = checked
                        if (checked) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasPerm) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            AppBackgroundService.start(context)
                            Toast.makeText(context, Str.settingsBackgroundEnabledToast, Toast.LENGTH_SHORT).show()
                        } else {
                            AppBackgroundService.stop(context)
                            Toast.makeText(context, Str.settingsBackgroundDisabledToast, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // 📌 3. NHÓM GIAO DIỆN
            SettingsSectionLabel(Str.settingsSectionAppearance)
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Outlined.LightMode,
                    title = Str.settingsKeepScreenOn,
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
                HorizontalDivider(color = FigmaDivider, thickness = 0.5.dp)
                SettingsRow(
                    icon = Icons.Outlined.LightMode,
                    title = Str.settingsTheme,
                    trailingText = Str.settingsThemeValue,
                    onClick = { Toast.makeText(context, Str.settingsThemeToast, Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = FigmaDivider, thickness = 0.5.dp)
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = Str.settingsLanguage,
                    trailingText = if (LanguageState.language == AppLanguage.EN) "English" else "Tiếng Việt",
                    onClick = {
                        val next = if (LanguageState.language == AppLanguage.EN) AppLanguage.VI else AppLanguage.EN
                        LanguageState.setLanguage(context, next)
                        Toast.makeText(
                            context,
                            if (next == AppLanguage.EN) "Language changed to English" else "Đã chuyển sang Tiếng Việt",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // 📌 4. NHÓM HỖ TRỢ & HỆ THỐNG
            SettingsSectionLabel(Str.settingsSectionSupport)
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.HelpOutline,
                    title = Str.settingsSupportCenter,
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://t.me/thanhgmn")
                        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider(color = FigmaDivider, thickness = 0.5.dp)
                SettingsRow(
                    icon = Icons.Outlined.Description,
                    title = Str.settingsTermsOfUse,
                    onClick = { Toast.makeText(context, Str.profileTermsUpdating, Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = FigmaDivider, thickness = 0.5.dp)
                SettingsRow(
                    icon = Icons.Outlined.Shield,
                    title = Str.settingsPrivacyPolicy,
                    onClick = { Toast.makeText(context, Str.profileTermsUpdating, Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = FigmaDivider, thickness = 0.5.dp)
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = Str.settingsAppVersion,
                    trailingText = BuildConfig.VERSION_NAME,
                    onClick = { Toast.makeText(context, "v${BuildConfig.VERSION_NAME}", Toast.LENGTH_SHORT).show() }
                )
            }

            Spacer(Modifier.height(24.dp))

            // 📌 5. NÚT ĐĂNG XUẤT Ở ĐÁY
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutConfirm = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Logout,
                        contentDescription = null,
                        tint = FigmaDanger,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Str.settingsLogout,
                        color = FigmaDanger,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = {
                Text(
                    text = Str.settingsLogoutConfirmTitle,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextHeader
                )
            },
            text = {
                Text(
                    text = Str.settingsLogoutConfirmMsg,
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        securePrefs.clearKey()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FigmaDanger)
                ) {
                    Text(Str.settingsLogoutConfirmBtn, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutConfirm = false }) {
                    Text(Str.settingsCancel, color = Color(0xFF6B7280))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = FigmaTextSection,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
        border = BorderStroke(1.dp, FigmaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FigmaIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FigmaBlue,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = FigmaTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = FigmaTextMuted,
                fontSize = 13.5.sp
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = FigmaTextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FigmaIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FigmaBlue,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = FigmaTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FigmaBlue,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE2E8F0),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
