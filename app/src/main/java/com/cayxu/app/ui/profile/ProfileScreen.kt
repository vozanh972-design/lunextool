package com.cayxu.app.ui.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cayxu.app.data.CoinEntry
import com.cayxu.app.data.CoinLedgerProvider
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.relativeTime
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.history.CoinEntryCard
import com.cayxu.app.ui.history.CoinEntryRow
import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.locale.Str
import com.cayxu.app.ui.main.DockClearance
import com.cayxu.app.ui.main.MainShell
import com.cayxu.app.ui.main.MainTab
import com.cayxu.app.ui.navigation.Routes

/* ───────────────────────────── Dữ liệu ───────────────────────────── */

/**
 * Các dòng đi tới màn khác. "Ngôn ngữ" và "Liên hệ hỗ trợ" KHÔNG nằm ở đây vì mở popup ngay trong màn Hồ sơ.
 */
enum class ProfileAction { Settings, Terms, Logout }

/** Kênh liên hệ hỗ trợ trong popup. [url] là link thật, KHÔNG hiện lên màn hình, chạm vào mới mở. */
enum class ContactKind { Telegram, Zalo }
data class ContactChannel(val kind: ContactKind, val name: String, val subtitle: String, val url: String)

val DefaultSupportContacts: List<ContactChannel> = listOf(
    ContactChannel(ContactKind.Telegram, "Telegram", "Nhắn tin qua Telegram", "https://t.me/thanhgmn"),
    ContactChannel(ContactKind.Zalo, "Zalo", "Nhắn tin qua Zalo", "https://zalo.me/0931006827"),
)

/** Cấu hình kênh hỗ trợ đa ngôn ngữ. */
@Composable
fun defaultSupportContacts(): List<ContactChannel> = listOf(
    ContactChannel(ContactKind.Telegram, "Telegram", Str.profileMsgTelegram, "https://t.me/thanhgmn"),
    ContactChannel(ContactKind.Zalo, "Zalo", Str.profileMsgZalo, "https://zalo.me/0931006827"),
)

/**
 * [membershipProgress] là phần thanh đã dùng/còn lại, 0..1 (thiết kế: 0,58). Tính ở nơi giữ dữ liệu
 * (ví dụ daysLeft / tổng số ngày của gói) rồi truyền vào.
 */
data class ProfileUiState(
    val username: String,
    val handle: String,
    val planBadge: String,          // "" thì ẩn nhãn
    val planName: String,
    val daysLeft: Int,
    val expiryDate: String,         // đã định dạng, vd "05/11/2026"
    val membershipProgress: Float,
    val language: AppLanguage,
    /** Vài dòng mới nhất của sổ xu (thường 2), lấy từ CoinLedger.observeRecent(2). */
    val recent: List<CoinEntry> = emptyList(),
)

/* Icon: lưới 24×24, cờ cung luôn viết cách nhau vì PathParser không đọc dạng dính. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val Shield = listOf("M12 22s8-4.5 8-11V5l-8-3-8 3v6c0 6.5 8 11 8 11z", "M8.5 12l2.5 2.5 4.5-5")
private val Sliders = listOf("M4 7h10M18 7h2M4 17h2M10 17h10", circle(16f, 7f, 2.2f), circle(8f, 17f, 2.2f))
private val Help = listOf(circle(12f, 12f, 9f), "M9.5 9.5a2.5 2.5 0 1 1 4 2c-.9.6-1.5 1-1.5 2M12 17h.01")
private val Logout = listOf("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9")
private val Chevron = listOf("M9 6l6 6-6 6")
private val Globe = listOf(circle(12f, 12f, 9f), "M3 12h18M12 3c2.5 2.7 3.7 5.7 3.7 9S14.5 18.3 12 21c-2.5-2.7-3.7-5.7-3.7-9S9.5 5.7 12 3z")
private val Doc = listOf("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5M9 13h6M9 17h6")
private val Plane = listOf("M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z")
private val Tick = listOf("M5 12.5l4.5 4.5L19 7.5")

private val RowLine = Color(0xFFEEF1F6)

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Màn Hồ sơ. Khung 390 × 844dp, nội dung cuộn. KHÔNG vẽ thanh dock: dock dùng chung nằm ở MainShell,
 * màn chỉ chừa [DockClearance] ở đáy. Không có animation riêng ngoài hiệu ứng chạm nhún 0.985 / 120 ms.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    contacts: List<ContactChannel> = defaultSupportContacts(),
    onAction: (ProfileAction) -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    onSeeAllHistory: () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf<ProfileSheet?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp),
    ) {
        Identity(state)
        Membership(state, Modifier.padding(top = 20.dp))

        // Tiêu đề Lịch sử xu và nút Xem tất cả luôn hiển thị
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(Str.profileCoinHistory, Modifier.weight(1f), style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
            SeeAll(onSeeAllHistory)
        }
        if (state.recent.isNotEmpty()) {
            CoinEntryCard(Modifier.padding(top = 12.dp)) {
                val today = remember { java.time.LocalDate.now() }
                state.recent.forEachIndexed { i, e -> CoinEntryRow(e, relativeTime(e.atMillis, today), divider = i > 0) }
            }
        } else {
            Card(Modifier.padding(top = 12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(Str.profileNoCoinHistory, style = type(13.5f, FontWeight.Medium, Alx.Muted))
                }
            }
        }

        SectionTitle(Str.profileAccountSupport)
        Card(Modifier.padding(top = 12.dp)) {
            SettingRow(Str.profileSettings, Sliders, divider = false) { onAction(ProfileAction.Settings) }
            SettingRow(Str.profileLanguage, Globe, divider = true, value = state.language.nativeName) { sheet = ProfileSheet.Language }
            SettingRow(Str.profileContactSupport, Help, divider = true) { sheet = ProfileSheet.Contact }
            SettingRow(Str.profileTermsPolicies, Doc, divider = true) { onAction(ProfileAction.Terms) }
            SettingRow(Str.profileLogout, Logout, divider = true, danger = true) { onAction(ProfileAction.Logout) }
        }

        Spacer(Modifier.height(DockClearance))
        Spacer(Modifier.navigationBarsPadding())
    }

    when (sheet) {
        ProfileSheet.Language -> BottomSheetDialog(onDismiss = { sheet = null }) {
            SheetHeader(Str.profileLanguageSheetTitle, Str.profileLanguageSheetSubtitle)
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLanguage.values().forEach { lang ->
                    LanguageOption(lang, selected = lang == state.language) {
                        onLanguageChange(lang)
                        sheet = null
                    }
                }
            }
        }
        ProfileSheet.Contact -> BottomSheetDialog(onDismiss = { sheet = null }) {
            val uri = LocalUriHandler.current
            SheetHeader(Str.profileContactSheetTitle, Str.profileContactSheetSubtitle)
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                contacts.forEach { c -> ContactRow(c) { uri.openUri(c.url) } }
            }
        }
        null -> Unit
    }
}

/**
 * Overload kết nối NavController, CoinLedger thực tế, ngôn ngữ toàn app và thông tin bản quyền từ SecurePrefs.
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    onAction: ((ProfileAction) -> Unit)? = null,
) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val ledger = remember { CoinLedgerProvider.get(context) }
    val recentCoins by ledger.observeRecent(2).collectAsStateWithLifecycle(initialValue = emptyList())
    var showLogoutDialog by remember { mutableStateOf(false) }

    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val daysLeft = remember { securePrefs.getDaysLeft() }
    val rawExpiresAt = remember { securePrefs.getExpiresAt() }

    val username = remember(buyerUsername) {
        if (!buyerUsername.isNullOrBlank()) buyerUsername.trim() else "username"
    }
    val handle = remember(username) { "@$username" }

    val expiryDate = remember(rawExpiresAt) {
        if (!rawExpiresAt.isNullOrBlank()) {
            rawExpiresAt.split(" ").firstOrNull() ?: rawExpiresAt
        } else {
            "05/11/2026"
        }
    }

    val progress = remember(daysLeft) {
        (daysLeft.toFloat() / 30f).coerceIn(0f, 1f)
    }

    val currentLang = LanguageState.language
    val planTitle = Str.profileMemberPro

    val state = remember(username, handle, daysLeft, expiryDate, progress, currentLang, recentCoins, planTitle) {
        ProfileUiState(
            username = username,
            handle = handle,
            planBadge = "PRO",
            planName = planTitle,
            daysLeft = daysLeft,
            expiryDate = expiryDate,
            membershipProgress = progress,
            language = currentLang,
            recent = recentCoins,
        )
    }

    ProfileScreen(
        state = state,
        onAction = onAction ?: { action ->
            when (action) {
                ProfileAction.Settings -> {
                    navController.navigate(Routes.SETTINGS)
                }
                ProfileAction.Terms -> {
                    Toast.makeText(context, Str.profileTermsUpdating, Toast.LENGTH_SHORT).show()
                }
                ProfileAction.Logout -> {
                    showLogoutDialog = true
                }
            }
        },
        onLanguageChange = { lang ->
            LanguageState.setLanguage(context, lang)
        },
        onSeeAllHistory = {
            navController.navigate(Routes.COIN_HISTORY)
        }
    )

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                BasicText(Str.profileLogoutConfirmTitle, style = type(18f, FontWeight.Bold, Alx.Ink))
            },
            text = {
                BasicText(Str.profileLogoutConfirmMsg, style = type(14f, FontWeight.Medium, Alx.Ink))
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .background(Alx.Danger, RoundedCornerShape(12.dp))
                        .clickable {
                            showLogoutDialog = false
                            securePrefs.clearKey()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicText(Str.profileLogout, style = type(14f, FontWeight.Bold, Color.White))
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable { showLogoutDialog = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicText(Str.profileCancel, style = type(14f, FontWeight.SemiBold, Alx.Muted))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
        )
    }
}

private enum class ProfileSheet { Language, Contact }

private val AppLanguage.nativeName get() = when (this) {
    AppLanguage.VI -> "Tiếng Việt"
    AppLanguage.EN -> "English"
}

@Composable
private fun Identity(s: ProfileUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(68.dp).background(Alx.BlueSoft, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(
                s.username.take(1).uppercase(),
                style = type(28f, FontWeight.ExtraBold, Alx.Blue),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(s.username, Modifier.weight(1f, fill = false), style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f), maxLines = 1)
                if (s.planBadge.isNotEmpty()) {
                    BasicText(
                        s.planBadge,
                        Modifier.background(Alx.Blue, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        style = type(10f, FontWeight.ExtraBold, Color.White, spacing = 0.08f, lineHeight = 1.2f),
                    )
                }
            }
            BasicText(s.handle, style = type(13.5f, FontWeight.Medium, Alx.Muted), maxLines = 1)
        }
    }
}

@Composable
private fun Membership(s: ProfileUiState, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Shield, Alx.Blue, Alx.BlueSoft, 40, 12, 20)
            Column(Modifier.weight(1f)) {
                BasicText(s.planName, style = type(14.5f, FontWeight.Bold, Alx.Ink))
                BasicText(
                    Str.profileDaysLeft(s.daysLeft, s.expiryDate),
                    Modifier.padding(top = 1.dp),
                    style = type(12f, FontWeight.Medium, Alx.Muted),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(Alx.BlueSoft, RoundedCornerShape(3.dp))) {
            Box(
                Modifier.fillMaxWidth(s.membershipProgress.coerceIn(0f, 1f)).height(5.dp)
                    .background(Alx.Blue, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(text, Modifier.padding(top = 24.dp), style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
}

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape)
            .padding(horizontal = 16.dp),
    ) { content() }
}

@Composable
private fun IconTile(paths: List<String>, color: Color, tint: Color, tile: Int, radius: Int, icon: Int) {
    Box(Modifier.size(tile.dp).background(tint, RoundedCornerShape(radius.dp)), contentAlignment = Alignment.Center) {
        SvgIcon(paths, icon.dp, color, 1.8f)
    }
}

/** "Xem tất cả ›" xanh, cạnh tiêu đề Lịch sử xu. Vùng chạm cao 44dp. */
@Composable
private fun SeeAll(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Row(
        Modifier.height(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(Str.profileSeeAll, style = type(13.5f, FontWeight.Bold, Alx.Blue))
        SvgIcon(Chevron, 16.dp, Alx.Blue, 2.2f)
    }
}

@Composable
private fun SettingRow(
    label: String,
    icon: List<String>,
    divider: Boolean,
    danger: Boolean = false,
    value: String? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine))
        Row(
            Modifier.fillMaxWidth().height(54.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SvgIcon(icon, 20.dp, if (danger) Alx.Danger else Alx.Blue, 1.8f)
            BasicText(
                label,
                Modifier.weight(1f),
                style = type(14.5f, if (danger) FontWeight.Bold else FontWeight.SemiBold, if (danger) Alx.Danger else Alx.Ink),
            )
            if (value != null) BasicText(value, style = type(13.5f, FontWeight.SemiBold, Alx.Muted))
            if (!danger) SvgIcon(Chevron, 16.dp, Color(0xFF94A3B8), 2f)
        }
    }
}

/* ───────────────────────────── Popup (tấm trượt từ đáy) ───────────────────────────── */

/**
 * Tấm trượt từ đáy, vẽ trong Dialog toàn màn hình nên phủ cả thanh dock. Nền mờ hiện dần 200ms, tấm trượt lên
 * và hiện dần 280ms. Chạm nền mờ hoặc bấm Back để đóng; KHÔNG có nút "Đóng". Đóng là tắt ngay, không có animation đóng.
 */
@Composable
private fun BottomSheetDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)   // tự vẽ nền mờ, bỏ nền mờ mặc định
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "scrim")
        val noRipple = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color(0x730F172A))
                    .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
            )
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(280, easing = EaseOutSoft)) { it / 4 } + fadeIn(tween(280)),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Alx.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})  // chạm trong tấm không đóng
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(Color(0xFFD5DBE6), RoundedCornerShape(2.dp)))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    BasicText(title, Modifier.padding(top = 18.dp), style = type(20f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
    BasicText(subtitle, Modifier.padding(top = 4.dp), style = type(13.5f, FontWeight.Medium, Alx.Muted))
}

/** Một lựa chọn ngôn ngữ, cùng kiểu với màn chọn ngôn ngữ đầu tiên: đang chọn có viền xanh, nền xanh nhạt, dấu tích. */
@Composable
private fun LanguageOption(lang: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    PressBox(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) Color(0xFFF5F8FF) else Alx.White, shape)
            .border(1.5.dp, if (selected) Alx.Blue else Alx.Line, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).background(if (selected) Alx.Blue else Alx.BlueSoft, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(lang.name, style = type(14f, FontWeight.ExtraBold, if (selected) Color.White else Alx.Blue, spacing = 0.02f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText(lang.nativeName, style = type(16f, FontWeight.Bold, Alx.Ink))
                BasicText(
                    if (lang == AppLanguage.VI) "Giao diện Tiếng Việt" else "English interface",
                    style = type(12.5f, FontWeight.Medium, Alx.Muted),
                )
            }
            Box(
                Modifier.size(24.dp).then(
                    if (selected) Modifier.background(Alx.Blue, CircleShape)
                    else Modifier.background(Alx.White, CircleShape).border(1.5.dp, Color(0xFFCBD2DF), CircleShape),
                ),
                contentAlignment = Alignment.Center,
            ) { if (selected) SvgIcon(Tick, 14.dp, Color.White, 3.2f) }
        }
    }
}

/** Dòng kênh liên hệ: ô icon 44dp, tên, mô tả, mũi tên. Link thật nằm trong [ContactChannel.url], không hiện ra. */
@Composable
private fun ContactRow(c: ContactChannel, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    PressBox(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().background(Alx.Bg, shape).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (c.kind) {
                ContactKind.Telegram -> Box(Modifier.size(44.dp).background(Color(0xFFE3F2FB), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    SvgIcon(Plane, 21.dp, Color(0xFF1D8FCF), 1.8f)
                }
                ContactKind.Zalo -> Box(Modifier.size(44.dp).background(Alx.BlueSoft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    BasicText("Zalo", style = type(13.5f, FontWeight.ExtraBold, Color(0xFF0F5BD8), spacing = -0.01f))
                }
            }
            Column(Modifier.weight(1f)) {
                BasicText(c.name, style = type(15f, FontWeight.Bold, Alx.Ink))
                BasicText(c.subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
            }
            SvgIcon(Chevron, 18.dp, Color(0xFF94A3B8), 2f)
        }
    }
}

/** Vùng bấm có hiệu ứng nhún 0.985 / 120 ms, không gợn sóng. */
@Composable
private fun PressBox(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
    ) { content() }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    val now = System.currentTimeMillis()
    MainShell(selected = MainTab.Profile, onSelect = {}) {
        ProfileScreen(
            ProfileUiState(
                username = "username", handle = "@username", planBadge = "PRO", planName = "Thành viên PRO",
                daysLeft = 25, expiryDate = "05/11/2026", membershipProgress = 0.58f, language = AppLanguage.VI,
                recent = listOf(
                    CoinEntry(1, com.cayxu.app.data.CoinEntryKind.Follow, "Theo dõi @linh.daily", 40, now, "preview"),
                    CoinEntry(2, com.cayxu.app.data.CoinEntryKind.Redeem, "Đổi thẻ điện thoại", -1000, now - 86_400_000, "preview"),
                ),
            ),
        )
    }
}
