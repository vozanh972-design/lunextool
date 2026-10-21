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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator
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
    val isLinking: Boolean = false,
    val ttcId: String = "",
)

data class CrossTTCAccountsUiState(
    val username: String = "",
    val coins: Long = 0L,
    val accounts: List<CrossTTCAccount> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
)

/* Cờ cung (arc flags) luôn viết cách nhau vì PathParser của Android không đọc dạng dính */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val Refresh = listOf(
    "M21 12a9 9 0 1 1 -9 -9c2.52 0 4.93 1 6.74 2.74L21 8",
    "M21 3v5h-5"
)
private val Logout = listOf("M9 21H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2h4M16 17l5-5-5-5M21 12H9")
private val Plus = listOf("M12 5v14M5 12h14")
private val CrossSwap = listOf("M17 3l4 4-4 4M21 7H8M7 21l-4-4 4-4M3 17h13")
private val Check = listOf("M5 12.5l4.5 4.5L19 7.5")
private val Gear = listOf(
    circle(12f, 12f, 3f),
    "M19.4 15 a1.65 1.65 0 0 0 0.33 1.82 l0.06 0.06 a2 2 0 0 1 0 2.83 a2 2 0 0 1 -2.83 0 l-0.06 -0.06 a1.65 1.65 0 0 0 -1.82 -0.33 a1.65 1.65 0 0 0 -1 1.51 V21 a2 2 0 0 1 -2 2 a2 2 0 0 1 -2 -2 v-0.09 a1.65 1.65 0 0 0 -1 -1.51 a1.65 1.65 0 0 0 -1.82 0.33 l-0.06 0.06 a2 2 0 0 1 -2.83 0 a2 2 0 0 1 0 -2.83 l0.06 -0.06 a1.65 1.65 0 0 0 0.33 -1.82 a1.65 1.65 0 0 0 -1.51 -1 H3 a2 2 0 0 1 -2 -2 a2 2 0 0 1 2 -2 h0.09 a1.65 1.65 0 0 0 1.51 -1 a1.65 1.65 0 0 0 -0.33 -1.82 l-0.06 -0.06 a2 2 0 0 1 0 -2.83 a2 2 0 0 1 2.83 0 l0.06 0.06 a1.65 1.65 0 0 0 1.82 0.33 H9 a1.65 1.65 0 0 0 1 -1.51 V3 a2 2 0 0 1 2 -2 a2 2 0 0 1 2 2 v0.09 a1.65 1.65 0 0 0 1 1.51 a1.65 1.65 0 0 0 1.82 -0.33 l0.06 -0.06 a2 2 0 0 1 2.83 0 a2 2 0 0 1 0 2.83 l-0.06 0.06 a1.65 1.65 0 0 0 -0.33 1.82 V9 a1.65 1.65 0 0 0 1.51 1 H21 a2 2 0 0 1 2 2 a2 2 0 0 1 -2 2 h-0.09 a1.65 1.65 0 0 0 -1.51 1 z"
)
private val Play = listOf("M7 4.5v15l12-7.5z")
private val EmptyPeople = listOf("M17 3l4 4-4 4", "M21 7H8", "M7 21l-4-4 4-4", "M3 17h13")
private val Trash = listOf(
    "M3 6h18",
    "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
    "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    "M10 11v6",
    "M14 11v6"
)

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
 * @param onDeleteSelected  bấm icon thùng rác — xoá các tài khoản TikTok đã chọn.
 * @param onLinkAccount     bấm vào badge "Chưa liên kết" — gọi liên kết tài khoản vào TTC.
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
    onDeleteSelected: (() -> Unit)? = null,
    onLinkAccount: ((CrossTTCAccount) -> Unit)? = null,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PressBox(onClick = onToggleSelectAll, contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(allSelected)
                            BasicText("Tất cả", style = type(13f, FontWeight.SemiBold, Alx.Muted))
                        }
                    }
                    if (state.selectedAccountIds.isNotEmpty() && onDeleteSelected != null) {
                        PressBox(
                            onClick = onDeleteSelected,
                            modifier = Modifier.size(40.dp).background(Alx.DangerBg, CircleShape).border(1.dp, Alx.Danger.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { SvgIcon(Trash, 18.dp, Alx.Danger, 2f) }
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
                            onLinkAccount = onLinkAccount?.let { { it(acc) } },
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

/** Dòng tài khoản: checkbox + avatar HD hoặc ký tự đầu + handle in đậm (weight fill=false chống rớt dòng badge) + pill "Đã liên kết" / "Chưa liên kết" + dòng thống kê + ngày tạo + nút làm mới riêng. */
@Composable
private fun AccountRow(
    account: CrossTTCAccount,
    selected: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onLinkAccount: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    PressBox(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
            .background(Alx.White, shape)
            .border(1.dp, if (selected) Alx.Blue else Alx.Line, shape)
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(selected)

            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Alx.Chip),
                contentAlignment = Alignment.Center,
            ) {
                if (account.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BasicText(
                        account.handle.removePrefix("@").firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "T",
                        style = type(15f, FontWeight.ExtraBold, Alx.ChipText),
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(
                        account.handle,
                        style = type(14.5f, FontWeight.Bold, Alx.Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (account.linked) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF16A34A).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                            Spacer(Modifier.width(4.dp))
                            BasicText(
                                "Đã liên kết",
                                style = type(10f, FontWeight.ExtraBold, Color(0xFF16A34A)),
                            )
                        }
                    } else {
                        PressBox(
                            onClick = { onLinkAccount?.invoke() },
                            enabled = !account.isLinking,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Alx.Blue.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (account.isLinking) {
                                    CircularProgressIndicator(
                                        color = Alx.Blue,
                                        strokeWidth = 1.5.dp,
                                        modifier = Modifier.size(9.dp),
                                    )
                                } else {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Alx.Blue))
                                }
                                BasicText(
                                    if (account.isLinking) "Đang liên kết..." else "Chưa liên kết",
                                    style = type(10f, FontWeight.ExtraBold, Alx.Blue),
                                )
                            }
                        }
                    }
                }

                if (account.statLine.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        account.statLine,
                        style = type(11.5f, FontWeight.SemiBold, Color(0xFFE1306C)),
                    )
                }

                if (account.createdAt.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        "Tạo: ${account.createdAt}",
                        style = type(11f, FontWeight.Medium, Alx.Muted),
                    )
                }
            }

            PressBox(
                onClick = onRefresh,
                modifier = Modifier.size(36.dp).background(Alx.Bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(Refresh, 16.dp, Alx.Muted, 2f) }
        }
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
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.985f else 1f, tween(120), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
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

