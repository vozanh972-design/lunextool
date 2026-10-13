package com.cayxu.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.cayxu.app.data.AppSettingsStore
import com.cayxu.app.data.appVersionName
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.locale.Str
import com.cayxu.app.worker.AppAlertNotifier

/* ───────────────────────────── Dữ liệu ───────────────────────────── */

/**
 * Trạng thái màn Cài đặt. Không có giá trị mặc định: [push], [background], [keepAwake] lấy từ
 * [com.cayxu.app.data.AppSettingsStore], [versionName] từ [com.cayxu.app.data.appVersionName].
 */
data class SettingsUiState(
    val push: Boolean,
    val background: Boolean,
    val keepAwake: Boolean,
    val appearanceName: String,     // vd "Sáng cao cấp"
    val versionName: String,
)

/** Các dòng đi tới màn khác (tự nối đích đến). */
enum class SettingsAction { Appearance, HelpCenter, Terms, Privacy }

/* Icon: lưới 24×24, cờ cung luôn viết cách nhau vì PathParser không đọc dạng dính. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val Bell = listOf("M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0")
private val Monitor = listOf("M5 4h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M8 20h8M12 16v4")
private val Sun = listOf(circle(12f, 12f, 4f), "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
private val Palette = listOf(
    circle(13.5f, 6.5f, 1.5f), circle(17.5f, 10.5f, 1.5f), circle(8.5f, 7.5f, 1.5f), circle(6.5f, 12.5f, 1.5f),
    "M12 2a10 10 0 0 0 0 20c1.1 0 2-.9 2-2 0-.5-.2-1-.5-1.3-.3-.4-.5-.8-.5-1.3 0-1.1.9-2 2-2h2.3c3 0 5.2-2.2 5.2-5.2C22 6 17.5 2 12 2z",
)
private val Headset = listOf("M3 18v-6a9 9 0 0 1 18 0v6", "M21 19a2 2 0 0 1-2 2h-1v-7h3zM3 19a2 2 0 0 0 2 2h1v-7H3z")
private val Doc = listOf("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h6")
private val ShieldCheck = listOf("M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z", "M9 12l2 2 4-4")
private val Info = listOf(circle(12f, 12f, 9f), "M12 11v5M12 8h.01")
private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val Chevron = listOf("M9 6l6 6-6 6")

private val RowLine = Color(0xFFEEF1F6)
private val Slate = Color(0xFF334155)
private val SlateSoft = Color(0xFFEDF0F5)

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Cổng vào kết nối với NavController và AppSettingsStore thật trên máy.
 */
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember { AppSettingsStore(context) }
    val settings by store.settings.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        store.setPush(granted)
        if (granted) {
            AppAlertNotifier.createChannel(context)
            Toast.makeText(context, Str.settingsPushEnabledToast, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, Str.settingsPushDisabledToast, Toast.LENGTH_SHORT).show()
        }
    }

    val state = SettingsUiState(
        push = settings.push,
        background = settings.background,
        keepAwake = settings.keepAwake,
        appearanceName = Str.settingsThemeValue,
        versionName = appVersionName(context).ifEmpty { "1.0.5" }
    )

    SettingsScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onPushChange = { on ->
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    store.setPush(true)
                    AppAlertNotifier.createChannel(context)
                    Toast.makeText(context, Str.settingsPushEnabledToast, Toast.LENGTH_SHORT).show()
                }
            } else {
                store.setPush(on)
                Toast.makeText(
                    context,
                    if (on) Str.settingsPushEnabledToast else Str.settingsPushDisabledToast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onBackgroundChange = { on ->
            store.setBackground(on)
            Toast.makeText(
                context,
                if (on) Str.settingsBackgroundEnabledToast else Str.settingsBackgroundDisabledToast,
                Toast.LENGTH_SHORT
            ).show()
        },
        onKeepAwakeChange = { on ->
            store.setKeepAwake(on)
        },
        onAction = { action ->
            when (action) {
                SettingsAction.Appearance -> {
                    Toast.makeText(context, Str.settingsThemeToast, Toast.LENGTH_SHORT).show()
                }
                SettingsAction.HelpCenter -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/thanhgmn")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                SettingsAction.Terms -> {
                    Toast.makeText(context, Str.profileTermsUpdating, Toast.LENGTH_SHORT).show()
                }
                SettingsAction.Privacy -> {
                    Toast.makeText(context, Str.profileTermsUpdating, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

/**
 * Màn Cài đặt (mở từ dòng "Cài đặt" ở Hồ sơ). Khung 390 × 844dp, nội dung cuộn.
 * Là màn con toàn màn hình: KHÔNG có dock, hiển thị bên ngoài MainRoute.
 * Công tắc đổi là gọi callback ngay; [AppSettingsStore] ghi lại rồi truyền state mới xuống.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onPushChange: (Boolean) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onKeepAwakeChange: (Boolean) -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PressBox(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .background(Alx.White, CircleShape)
                    .border(1.dp, Alx.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
            BasicText(Str.settingsTitle, style = type(20f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
        }

        GroupLabel(Str.settingsSectionNotifications)
        GroupCard {
            ToggleRow(
                Bell, Alx.Blue, Alx.BlueSoft,
                Str.settingsPushNotifications, Str.settingsPushSubtitle,
                state.push, onPushChange, divider = false
            )
            ToggleRow(
                Monitor, Slate, SlateSoft,
                Str.settingsBackgroundService, Str.settingsBackgroundSubtitle,
                state.background, onBackgroundChange, divider = true
            )
        }

        GroupLabel(Str.settingsSectionAppearance)
        GroupCard {
            ToggleRow(
                Sun, Alx.Blue, Alx.BlueSoft,
                Str.settingsKeepScreenOn, null,
                state.keepAwake, onKeepAwakeChange, divider = false
            )
            LinkRow(
                label = Str.settingsTheme, value = state.appearanceName, divider = true, bold = true, minHeight = 60,
                leading = { IconTile(Palette, Alx.Blue, Alx.BlueSoft) },
            ) { onAction(SettingsAction.Appearance) }
        }

        GroupLabel(Str.settingsSectionSupport)
        GroupCard {
            LinkRow(
                Str.settingsSupportCenter, null, divider = false,
                leading = { SvgIcon(Headset, 20.dp, Alx.Blue, 1.8f) }
            ) { onAction(SettingsAction.HelpCenter) }
            LinkRow(
                Str.settingsTermsOfUse, null, divider = true,
                leading = { SvgIcon(Doc, 20.dp, Alx.Blue, 1.8f) }
            ) { onAction(SettingsAction.Terms) }
            LinkRow(
                Str.settingsPrivacyPolicy, null, divider = true,
                leading = { SvgIcon(ShieldCheck, 20.dp, Alx.Blue, 1.8f) }
            ) { onAction(SettingsAction.Privacy) }
            InfoRow(Str.settingsAppVersion, state.versionName, divider = true)
        }

        Spacer(Modifier.height(40.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * Áp dụng "Giữ màn hình sáng" cho cả app: đặt MỘT lần ở gốc nội dung của Activity, vd
 * `KeepScreenOn(settings.keepAwake)` ngay trong `setContent { … }`, để bật là có hiệu lực mọi màn.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/* ───────────────────────────── Thành phần ───────────────────────────── */

@Composable
private fun GroupLabel(text: String) {
    BasicText(
        text,
        Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp),
        style = type(11f, FontWeight.ExtraBold, Alx.Muted, spacing = 0.12f),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(Alx.White, shape)
            .border(1.dp, Alx.Line, shape)
            .clip(shape)
            .padding(horizontal = 16.dp),
    ) { content() }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine))

@Composable
private fun IconTile(paths: List<String>, color: Color, tint: Color) {
    Box(Modifier.size(38.dp).background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        SvgIcon(paths, 19.dp, color, 1.8f)
    }
}

/** Dòng có công tắc: ô icon 38dp, tiêu đề (+ mô tả nếu có), công tắc 52×32dp bên phải. */
@Composable
private fun ToggleRow(
    icon: List<String>, color: Color, tint: Color,
    title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean,
) {
    Column {
        if (divider) Divider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(icon, color, tint)
            Column(Modifier.weight(1f)) {
                BasicText(title, style = type(14.5f, FontWeight.Bold, Alx.Ink))
                if (subtitle != null) BasicText(subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
            }
            Switch(checked)
        }
    }
}

/** Công tắc 52×32dp: nền xanh khi bật / #CBD2DF khi tắt, nút tròn trắng 26dp, trượt 150 ms. */
@Composable
private fun Switch(checked: Boolean) {
    val track by animateColorAsState(if (checked) Alx.Blue else Color(0xFFCBD2DF), tween(150), label = "track")
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, tween(150), label = "knob")
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .background(track, RoundedCornerShape(16.dp))
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(26.dp)
                .shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** Dòng bấm để đi tới màn khác: [leading] (ô icon hoặc icon trần), nhãn, giá trị phụ bên phải (nếu có), mũi tên. */
@Composable
private fun LinkRow(
    label: String,
    value: String?,
    divider: Boolean,
    bold: Boolean = false,
    minHeight: Int = 56,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column {
        if (divider) Divider()
        PressBox(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                leading()
                BasicText(label, Modifier.weight(1f), style = type(14.5f, if (bold) FontWeight.Bold else FontWeight.SemiBold, Alx.Ink))
                if (value != null) BasicText(value, style = type(13.5f, FontWeight.SemiBold, Alx.Muted))
                SvgIcon(Chevron, 18.dp, Color(0xFF94A3B8), 2f)
            }
        }
    }
}

/** Dòng chỉ hiển thị, không bấm được (phiên bản ứng dụng). */
@Composable
private fun InfoRow(label: String, value: String, divider: Boolean) {
    Column {
        if (divider) Divider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SvgIcon(Info, 20.dp, Alx.Blue, 1.8f)
            BasicText(label, Modifier.weight(1f), style = type(14.5f, FontWeight.SemiBold, Alx.Ink))
            BasicText(value, style = type(13.5f, FontWeight.Bold, Alx.Muted))
        }
    }
}

/** Vùng bấm có hiệu ứng nhún 0.985 / 120 ms, không gợn sóng. */
@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content() }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        state = SettingsUiState(push = true, background = false, keepAwake = false, appearanceName = "Sáng cao cấp", versionName = "1.0.5"),
        onBack = {}, onPushChange = {}, onBackgroundChange = {}, onKeepAwakeChange = {}, onAction = {},
    )
}
