package com.cayxu.app.ui.crossttc

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import kotlinx.coroutines.delay

/*
 * MÀN "TƯƠNG TÁC CHÉO" (danh sách tài khoản TikTok sau khi đăng nhập) — CHỈ là lớp giao diện, dựng
 * theo đúng cấu trúc `com.cayxu.app.ui.xsmm.XsmmAccountsScreen` (thẻ tài khoản + tab + danh sách +
 * thanh dưới) nhưng CHỈ MỘT nền tảng TikTok — không có Facebook/Instagram, đúng yêu cầu "tiktok ttc là
 * màn độc lập, chỉ có tiktok thôi". Không gọi mạng, không tự tính followers/trạng thái liên kết: mọi dữ
 * liệu hiển thị truyền qua [CrossTTCAccountsUiState], thao tác người dùng báo lại qua callback.
 *
 * Màu nhận diện: Đen TikTok `TtcDark`/`TtcDarkSoft` (#0F172A/#F1F5F9).
 * Avatar dùng ẢNH thật qua Coil (AsyncImage).
 */

private val TtcDark = Color(0xFF0F172A)
private val TtcDarkSoft = Color(0xFFF1F5F9)
private val TtcGreen = Color(0xFF15803D)
private val TtcGreenSoft = Color(0xFFE7F8EE)

/** Một tài khoản TikTok trong danh sách trao đổi chéo. */
data class CrossTTCAccount(
    val id: String,
    val handle: String,
    val avatarUrl: String,
    val statLine: String,     // vd "11.2K followers · 2.0K tim"
    val createdAt: String,    // vd "03/05/2026"
    val linked: Boolean = true,
)

data class CrossTTCAccountsUiState(
    val username: String = "",
    val coins: Long = 0L,
    val accounts: List<CrossTTCAccount> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
)

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val Refresh = listOf("M21 12a9 9 0 11-3-6.7M21 4v5h-5")
private val Logout = listOf("M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9")
private val Plus = listOf("M12 5v14M5 12h14")
private val CrossSwap = listOf("M17 3l4 4-4 4M21 7H8M7 21l-4-4 4-4M3 17h13")
private val Check = listOf("M5 12.5l4.5 4.5L19 7.5")
private val Gear = listOf(
    "M12 15a3 3 0 100-6 3 3 0 000 6z",
    "M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 11-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 110-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 114 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 110 4h-.09a1.65 1.65 0 00-1.51 1z",
)
private val Play = listOf("M7 4.5v15l12-7.5z")
private val EmptyPeople = listOf("M17 3l4 4-4 4", "M21 7H8", "M7 21l-4-4 4-4", "M3 17h13")

/**
 * @param state             dữ liệu hiển thị hiện tại (tên tài khoản TTC, danh sách tài khoản TikTok…).
 * @param onBack            bấm nút quay lại ở header.
 * @param onRefreshSummary  bấm nút làm mới ở thẻ tài khoản trên cùng.
 * @param onLogout          bấm icon đăng xuất — xoá phiên Tương tác chéo và về CrossTTCLoginScreen.
 * @param onToggleSelectAll bấm checkbox "Tất cả".
 * @param onToggleAccount   bấm checkbox một dòng tài khoản.
 * @param onRefreshAccount  bấm nút làm mới riêng một dòng tài khoản.
 * @param onAddAccount      bấm nút (+) hoặc thẻ rỗng — mở CrossTTCLoginScreen để thêm tài khoản TikTok.
 * @param onOpenConfig      bấm "Cấu hình" ở thanh dưới.
 * @param onRun             bấm "Chạy" — chạy trao đổi chéo cho các tài khoản đã chọn.
 */
@Composable
fun CrossTTCAccountsScreen(
    state: CrossTTCAccountsUiState,
    onBack: () -> Unit,
    onRefreshSummary: () -> Unit,
    onLogout: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleAccount: (CrossTTCAccount) -> Unit,
    onRefreshAccount: (CrossTTCAccount) -> Unit,
    onAddAccount: () -> Unit,
    onOpenConfig: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    toastText: String? = null,
) {
    val allSelected = state.accounts.isNotEmpty() && state.accounts.all { it.id in state.selectedAccountIds }
    val selectedCount = state.accounts.count { it.id in state.selectedAccountIds }

    Box(modifier.fillMaxSize().background(Alx.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PressBox(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
                BasicText("Tương tác chéo", style = type(24f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
            }

            AccountSummaryCard(state, onRefreshSummary, onLogout, Modifier.padding(top = 20.dp))

            // Section header
            Row(
                Modifier.padding(top = 22.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "Tài khoản TikTok",
                    Modifier.weight(1f),
                    style = type(18f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PressBox(onClick = onToggleSelectAll, contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(allSelected)
                            BasicText("Tất cả", style = type(13f, FontWeight.SemiBold, Alx.Muted))
                        }
                    }
                    PressBox(
                        onClick = onAddAccount,
                        modifier = Modifier.size(40.dp).background(TtcDark, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { SvgIcon(Plus, 18.dp, Alx.White, 2.4f) }
                }
            }

            if (state.accounts.isEmpty()) {
                EmptyState(Modifier.padding(top = 16.dp))
            } else {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.accounts.forEach { acc ->
                        AccountRow(
                            account = acc,
                            selected = acc.id in state.selectedAccountIds,
                            onToggle = { onToggleAccount(acc) },
                            onRefresh = { onRefreshAccount(acc) },
                        )
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
        }

        Toast(toastText, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))

        // Action bar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Alx.White)
                .drawBehind {
                    drawLine(Alx.Line, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PressBox(
                onClick = onOpenConfig,
                modifier = Modifier.weight(1f).size(width = 0.dp, height = 56.dp).fillMaxWidth(1f)
                    .background(Alx.White, RoundedCornerShape(18.dp))
                    .border(1.5.dp, Alx.InputBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvgIcon(Gear, 18.dp, Alx.Ink, 1.9f)
                    BasicText("Cấu hình", style = type(15f, FontWeight.Bold, Alx.Ink))
                }
            }
            PressBox(
                onClick = onRun,
                modifier = Modifier.weight(1.25f).size(width = 0.dp, height = 56.dp).fillMaxWidth(1f)
                    .background(Alx.Ink, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvgIcon(Play, 16.dp, Alx.White, 2f, fill = true)
                    BasicText("Chạy ($selectedCount)", style = type(15f, FontWeight.Bold, Alx.White))
                }
            }
        }
    }
}

/* ───────────────────────────── Thành phần ───────────────────────────── */

@Composable
private fun AccountSummaryCard(
    state: CrossTTCAccountsUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .background(Alx.White, RoundedCornerShape(22.dp))
            .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val firstLetter = state.username.firstOrNull()?.uppercaseChar()?.toString() ?: "T"
        Box(Modifier.size(48.dp).background(TtcDarkSoft, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(firstLetter, style = type(18f, FontWeight.ExtraBold, TtcDark))
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                state.username.ifBlank { "Tài khoản TTC" },
                style = type(15f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.25f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val coinsFormatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(state.coins)
            BasicText(
                "$coinsFormatted xu",
                Modifier.padding(top = 2.dp),
                style = type(13f, FontWeight.ExtraBold, TtcDark, lineHeight = 1.3f),
            )
        }
        PressBox(
            onClick = onRefresh,
            modifier = Modifier.size(40.dp).background(Alx.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Refresh, 18.dp, TtcDark, 2f) }
        PressBox(
            onClick = onLogout,
            modifier = Modifier.size(40.dp).background(Color(0xFFFDECEC), CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Logout, 18.dp, Color(0xFFC9363B), 2f) }
    }
}

/** Trạng thái rỗng — thẻ trắng viền + icon, giống đúng thiết kế gốc (không bấm được cả thẻ, dùng nút (+) ở trên). */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(Alx.White, RoundedCornerShape(22.dp))
            .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).background(TtcDarkSoft, CircleShape), contentAlignment = Alignment.Center) {
            SvgIcon(CrossSwap, 26.dp, TtcDark, 1.8f)
        }
        BasicText(
            "Chưa có tài khoản TikTok nào",
            Modifier.padding(top = 6.dp),
            style = type(15f, FontWeight.Bold, Alx.Ink),
        )
        BasicText(
            "Thêm tài khoản TikTok để bắt đầu trao đổi like, sub chéo với người dùng khác.",
            Modifier.padding(top = 4.dp),
            style = type(13f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f, align = TextAlign.Center),
        )
    }
}

/** Dòng tài khoản: checkbox + avatar ẢNH thật + handle in đậm + pill "Đã liên kết" + dòng thống kê + ngày tạo + nút làm mới riêng. */
@Composable
private fun AccountRow(
    account: CrossTTCAccount,
    selected: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(Alx.White, shape)
            .border(1.dp, Alx.Line, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        PressBox(onClick = onToggle) { Checkbox(selected) }

        AsyncImage(
            model = account.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(CircleShape).background(TtcDarkSoft),
        )

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BasicText(
                    account.handle,
                    style = type(14.5f, FontWeight.ExtraBold, Alx.Ink),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (account.linked) {
                    Row(
                        Modifier.background(TtcGreenSoft, RoundedCornerShape(999.dp)).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SvgIcon(Check, 9.dp, TtcGreen, 3.6f)
                        BasicText("Đã liên kết", style = type(10.5f, FontWeight.ExtraBold, TtcGreen))
                    }
                }
            }
            BasicText(account.statLine, Modifier.padding(top = 4.dp), style = type(12.5f, FontWeight.ExtraBold, TtcDark))
            BasicText("Tạo: ${account.createdAt}", Modifier.padding(top = 2.dp), style = type(11.5f, FontWeight.SemiBold, Color(0xFF94A3B8)))
        }

        PressBox(
            onClick = onRefresh,
            modifier = Modifier.size(36.dp).background(Alx.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Refresh, 16.dp, Alx.Muted, 2f) }
    }
}

/** Toast nổi phía dưới — tự ẩn sau ~2.2s giống bản canvas; truyền `text = null` để không hiện gì. */
@Composable
private fun Toast(text: String?, modifier: Modifier = Modifier) {
    var visible by remember(text) { mutableStateOf(text != null) }
    LaunchedEffect(text) {
        if (text != null) {
            visible = true
            delay(2200)
            visible = false
        }
    }
    if (visible && text != null) {
        Box(
            modifier.background(Alx.Ink, RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 11.dp),
        ) { BasicText(text, style = type(13f, FontWeight.Bold, Alx.White)) }
    }
}

@Composable
private fun Checkbox(checked: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(22.dp)
            .then(if (checked) Modifier.background(TtcDark, shape) else Modifier.background(Alx.White, shape).border(1.8.dp, Color(0xFF94A3B8), shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) SvgIcon(Check, 12.dp, Alx.White, 3.4f)
    }
}

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
private fun CrossTTCAccountsScreenPreview() {
    CrossTTCAccountsScreen(
        state = CrossTTCAccountsUiState(
            username = "mimi10001",
            coins = 41905L,
            accounts = emptyList(),
            selectedAccountIds = emptySet(),
        ),
        onBack = {}, onRefreshSummary = {}, onLogout = {}, onToggleSelectAll = {},
        onToggleAccount = {}, onRefreshAccount = {}, onAddAccount = {}, onOpenConfig = {}, onRun = {},
    )
}

