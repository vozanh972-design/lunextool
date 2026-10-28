package com.cayxu.app.ui.screens.golike

/*
 * MÀN "GOLIKE — SAU KHI ĐĂNG NHẬP" (GolikeAccountsScreen.kt).
 *
 * Màn hình quản lý danh sách tài khoản TikTok, Facebook, Instagram của Golike.
 * Mỗi tài khoản có 2 trạng thái: "Chưa liên kết" (cam) / "Đã liên kết" (xanh lá).
 * Nút bên phải mỗi dòng là nút liên kết. Số dư hiển thị bằng đ.
 * CHỈ GIAO DIỆN — toàn bộ logic kết nối API, quét tài khoản, lưu trữ giữ nguyên.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import java.text.NumberFormat
import java.util.Locale

/* ───────────────────────────── Màu nhận diện Golike ───────────────────────────── */

val GolikeOrange = Color(0xFFF59E0B)
val GolikeOrangeSoft = Color(0xFFFFF7ED)

private val LiveGreen = Color(0xFF16A34A)
private val IgMagenta = Color(0xFFC2255C)
private val IgMagentaSoft = Color(0xFFFDEAF2)

/* ───────────────────────────── Model dữ liệu hiển thị ───────────────────────────── */

enum class GolikePlatform(val label: String, val id: String) {
    TikTok("TikTok", "tiktok"),
    Facebook("Facebook", "facebook"),
    Instagram("Instagram", "instagram");

    fun accent(): Color = if (this == Instagram) IgMagenta else GolikeOrange
    fun soft(): Color = if (this == Instagram) IgMagentaSoft else GolikeOrangeSoft

    companion object {
        fun fromId(id: String): GolikePlatform = when (id.lowercase()) {
            "facebook" -> Facebook
            "instagram" -> Instagram
            else -> TikTok
        }
    }
}

data class GolikeAccountTypeFilter(val id: String, val label: String)

val DefaultGolikeAccountTypes: Map<GolikePlatform, List<GolikeAccountTypeFilter>> = mapOf(
    GolikePlatform.TikTok to listOf(
        GolikeAccountTypeFilter("standard", "TikTok"),
        GolikeAccountTypeFilter("lite", "TikTok Lite"),
        GolikeAccountTypeFilter("studio", "TikTok Studio"),
    ),
    GolikePlatform.Facebook to listOf(GolikeAccountTypeFilter("facebook", "Facebook")),
    GolikePlatform.Instagram to listOf(
        GolikeAccountTypeFilter("ready", "Đã có"),
        GolikeAccountTypeFilter("unlinked", "Chưa có"),
    ),
)

data class GolikeAccountsUiState(
    val username: String = "",
    val balanceDong: Long = 0L,
    val platformCounts: Map<GolikePlatform, Int> = emptyMap(),
    val selectedPlatform: GolikePlatform = GolikePlatform.TikTok,
    val selectedTypeId: String? = null,
    val accounts: List<GolikeAccount> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
    val refreshingBalance: Boolean = false,
    val toast: String? = null,
    val linkingAccountId: String? = null,
)

/* ───────────────────────────── Icon SVG ───────────────────────────── */

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val Refresh = listOf(
    "M21 12a9 9 0 1 1 -9 -9c2.52 0 4.93 1 6.74 2.74L21 8",
    "M21 3v5h-5"
)
private val Logout = listOf("M9 21H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2h4M16 17l5-5-5-5M21 12H9")
private val Plus = listOf("M12 5v14M5 12h14")
private val EmptyPeople = listOf(
    "M16 21v-2a4 4 0 0 0 -4 -4H6a4 4 0 0 0 -4 4v2",
    circle(9f, 7f, 4f),
    "M19 8v6M22 11h-6"
)
private val Gear = listOf(
    circle(12f, 12f, 3f),
    "M19.4 15 a1.65 1.65 0 0 0 0.33 1.82 l0.06 0.06 a2 2 0 0 1 0 2.83 a2 2 0 0 1 -2.83 0 l-0.06 -0.06 a1.65 1.65 0 0 0 -1.82 -0.33 a1.65 1.65 0 0 0 -1 1.51 V21 a2 2 0 0 1 -2 2 a2 2 0 0 1 -2 -2 v-0.09 a1.65 1.65 0 0 0 -1 -1.51 a1.65 1.65 0 0 0 -1.82 0.33 l-0.06 0.06 a2 2 0 0 1 -2.83 0 a2 2 0 0 1 0 -2.83 l0.06 -0.06 a1.65 1.65 0 0 0 0.33 -1.82 a1.65 1.65 0 0 0 -1.51 -1 H3 a2 2 0 0 1 -2 -2 a2 2 0 0 1 2 -2 h0.09 a1.65 1.65 0 0 0 1.51 -1 a1.65 1.65 0 0 0 -0.33 -1.82 l-0.06 -0.06 a2 2 0 0 1 0 -2.83 a2 2 0 0 1 2.83 0 l0.06 0.06 a1.65 1.65 0 0 0 1.82 0.33 H9 a1.65 1.65 0 0 0 1 -1.51 V3 a2 2 0 0 1 2 -2 a2 2 0 0 1 2 2 v0.09 a1.65 1.65 0 0 0 1 1.51 a1.65 1.65 0 0 0 1.82 -0.33 l0.06 -0.06 a2 2 0 0 1 2.83 0 a2 2 0 0 1 0 2.83 l-0.06 0.06 a1.65 1.65 0 0 0 -0.33 1.82 V9 a1.65 1.65 0 0 0 1.51 1 H21 a2 2 0 0 1 2 2 a2 2 0 0 1 -2 2 h-0.09 a1.65 1.65 0 0 0 -1.51 1 z"
)
private val Play = listOf("M7 4.5v15l12-7.5z")
private val Trash = listOf(
    "M3 6h18",
    "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
    "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    "M10 11v6",
    "M14 11v6"
)
private val Check = listOf("M5 13l4 4L19 7")
private val LinkIcon = listOf(
    "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
    "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
)
private val AlertCircle = listOf(
    circle(12f, 12f, 9f),
    "M12 8v4",
    "M12 16h.01"
)

/* ───────────────────────────── Composable chính ───────────────────────────── */

@Composable
fun GolikeAccountsScreen(
    state: GolikeAccountsUiState,
    onBack: () -> Unit,
    onRefreshBalance: () -> Unit,
    onLogout: () -> Unit,
    onSelectPlatform: (GolikePlatform) -> Unit,
    onSelectType: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleAccount: (GolikeAccount) -> Unit,
    onLinkAccount: (GolikeAccount) -> Unit,
    onAddAccount: () -> Unit,
    onOpenConfig: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    accountTypes: Map<GolikePlatform, List<GolikeAccountTypeFilter>> = DefaultGolikeAccountTypes,
    onDeleteSelected: (() -> Unit)? = null,
    onTogglePlayAccount: ((GolikeAccount) -> Unit)? = null,
    onReloadAccount: ((GolikeAccount) -> Unit)? = null,
    onErrorAccount: ((GolikeAccount) -> Unit)? = null,
) {
    val types = accountTypes[state.selectedPlatform].orEmpty()
    val visible = if (state.selectedPlatform == GolikePlatform.Instagram) {
        val filterId = state.selectedTypeId ?: "ready"
        if (filterId == "unlinked") {
            state.accounts.filter { !it.isGolikeLinked || it.cookie.isBlank() }
        } else {
            state.accounts.filter { it.isGolikeLinked && it.cookie.isNotBlank() }
        }
    } else {
        state.accounts
    }
    val allSelected = visible.isNotEmpty() && visible.all { it.id in state.selectedAccountIds }

    Box(modifier = modifier.fillMaxSize().background(Alx.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        ) {
            // Header: nút quay lại + tiêu đề "Golike"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PressBox(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
                BasicText("Golike", style = type(24f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
            }

            // Thẻ tài khoản: avatar cam, username, số dư bằng đ, nút refresh xoay, nút đăng xuất đỏ
            GolikeAccountCard(state, onRefreshBalance, onLogout, Modifier.padding(top = 20.dp))

            // Tab chọn nền tảng TikTok / Facebook / Instagram
            GolikePlatformTabs(state.selectedPlatform, state.platformCounts, onSelectPlatform, Modifier.padding(top = 16.dp))

            // 2 ô nhỏ phân loại lọc (Đã có / Chưa có với IG, hoặc TikTok / Lite / Studio) nằm ở giữa thẻ card và tiêu đề mục
            if (types.size > 1) {
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    types.forEach { t ->
                        val selected = state.selectedTypeId == t.id || (state.selectedTypeId == null && t == types.first())
                        GolikeTypeChip(t.label, selected = selected) { onSelectType(t.id) }
                    }
                }
            }

            // Tiêu đề mục: "Tài khoản TikTok" + Checkbox "Tất cả" + Nút xóa / Nút (+)
            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerTitle = if (state.selectedPlatform == GolikePlatform.Instagram && visible.isNotEmpty()) {
                    "Tài khoản ${state.selectedPlatform.label} (${visible.size})"
                } else {
                    "Tài khoản ${state.selectedPlatform.label}"
                }
                BasicText(
                    headerTitle,
                    Modifier.weight(1f),
                    style = type(18f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.selectedPlatform != GolikePlatform.Instagram) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PressBox(onClick = onToggleSelectAll, contentAlignment = Alignment.CenterStart) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GolikeCheckbox(allSelected)
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
                            modifier = Modifier.size(40.dp).background(state.selectedPlatform.accent(), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { SvgIcon(Plus, 18.dp, Alx.White, 2.4f) }
                    }
                } else {
                    if (state.selectedAccountIds.isNotEmpty() && onDeleteSelected != null) {
                        PressBox(
                            onClick = onDeleteSelected,
                            modifier = Modifier.size(40.dp).background(Alx.DangerBg, CircleShape).border(1.dp, Alx.Danger.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { SvgIcon(Trash, 18.dp, Alx.Danger, 2f) }
                    }
                }
            }

            // Danh sách tài khoản hoặc EmptyState
            if (visible.isEmpty()) {
                GolikeEmptyState(state.selectedPlatform, onAddAccount, Modifier.padding(top = 16.dp))
            } else {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visible.forEach { acc ->
                        if (state.selectedPlatform == GolikePlatform.Instagram) {
                            GolikeInstagramAccountRow(
                                account = acc,
                                selected = acc.id in state.selectedAccountIds,
                                isLinking = state.linkingAccountId == acc.id,
                                onLink = { onLinkAccount(acc) },
                                onClick = { onToggleAccount(acc) },
                                onTogglePlay = { onTogglePlayAccount?.invoke(acc) ?: onToggleAccount(acc) },
                                onReload = { onReloadAccount?.invoke(acc) ?: onLinkAccount(acc) },
                                onErrorClick = { onErrorAccount?.invoke(acc) }
                            )
                        } else {
                            GolikeAccountRow(
                                account = acc,
                                selected = acc.id in state.selectedAccountIds,
                                isLinking = state.linkingAccountId == acc.id,
                                onLink = { onLinkAccount(acc) },
                                onClick = { onToggleAccount(acc) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
        }

        // Toast nổi ở đáy thông báo liên kết thành công
        AnimatedVisibility(
            visible = !state.toast.isNullOrBlank(),
            enter = slideInVertically(tween(250, easing = EaseOutSoft)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200, easing = EaseOutSoft)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Alx.Ink)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BasicText(
                    text = state.toast.orEmpty(),
                    style = type(13.5f, FontWeight.Bold, Alx.White)
                )
            }
        }

        // Thanh hành động dưới cùng: "Cấu hình" + "Chạy" / Cấu hình + Tất cả + [▶] + [+]
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
            val configAccent = if (state.selectedPlatform == GolikePlatform.Instagram) IgMagenta else Alx.Ink
            val configBorder = if (state.selectedPlatform == GolikePlatform.Instagram) IgMagenta else Alx.InputBorder

            PressBox(
                onClick = onOpenConfig,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(Alx.White, RoundedCornerShape(18.dp))
                    .border(1.5.dp, configBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvgIcon(Gear, 18.dp, configAccent, 1.9f)
                    BasicText("Cấu hình", style = type(15f, FontWeight.Bold, configAccent))
                }
            }

            if (state.selectedPlatform == GolikePlatform.Instagram) {
                Row(
                    Modifier.weight(1.25f).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    PressBox(onClick = onToggleSelectAll, contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GolikeCheckbox(allSelected, checkedColor = IgMagenta)
                            BasicText("Tất cả", style = type(13f, FontWeight.SemiBold, Alx.Ink2))
                        }
                    }

                    if (state.accounts.isNotEmpty()) {
                        val isAnyRunning = state.accounts.any { com.cayxu.app.ui.screens.golike.GolikeRunningManager.isRunning(it.id) || com.cayxu.app.ui.screens.golike.GolikeRunningManager.isRunning(it.username) }
                        PressBox(
                            onClick = onRun,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(44.dp)
                                .background(if (isAnyRunning) Alx.Danger else IgMagenta, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isAnyRunning) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Alx.White))
                            } else {
                                SvgIcon(Play, 16.dp, Alx.White, 2f, fill = true)
                            }
                        }
                    }

                    PressBox(
                        onClick = onAddAccount,
                        modifier = Modifier.padding(start = 12.dp).size(44.dp).background(IgMagenta, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { SvgIcon(Plus, 18.dp, Alx.White, 2.4f) }
                }
            } else {
                PressBox(
                    onClick = onRun,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(56.dp)
                        .background(Alx.Ink, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SvgIcon(Play, 16.dp, Alx.White, 2f, fill = true)
                        BasicText("Chạy", style = type(15f, FontWeight.Bold, Alx.White))
                    }
                }
            }
        }
    }
}

/* ───────────────────────────── Các thành phần con UI ───────────────────────────── */

private fun formatDong(amount: Long): String {
    return try {
        NumberFormat.getInstance(Locale("vi", "VN")).format(amount)
    } catch (_: Exception) {
        amount.toString()
    }
}

@Composable
private fun GolikeAccountCard(
    state: GolikeAccountsUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "golikeRefresh")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "golikeRefreshAngle",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Alx.White, RoundedCornerShape(22.dp))
            .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar chữ cái đầu màu cam
        Box(
            Modifier.size(48.dp).background(GolikeOrangeSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                state.username.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "G",
                style = type(18f, FontWeight.ExtraBold, GolikeOrange),
            )
        }

        Column(Modifier.weight(1f)) {
            BasicText(
                state.username.ifBlank { "Tài khoản Golike" },
                style = type(15f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.25f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "${formatDong(state.balanceDong)} đ",
                Modifier.padding(top = 2.dp),
                style = type(13.5f, FontWeight.ExtraBold, GolikeOrange, lineHeight = 1.3f),
            )
        }

        // Nút làm mới số dư
        PressBox(
            onClick = onRefresh,
            modifier = Modifier.size(40.dp).background(Alx.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SvgIcon(
                Refresh, 18.dp, GolikeOrange, 2f,
                modifier = if (state.refreshingBalance) Modifier.graphicsLayer { rotationZ = angle } else Modifier,
            )
        }

        // Nút đăng xuất đỏ nhạt
        PressBox(
            onClick = onLogout,
            modifier = Modifier.size(40.dp).background(Color(0xFFFDECEC), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SvgIcon(Logout, 18.dp, Color(0xFFDC2626), 2f)
        }
    }
}

@Composable
private fun GolikePlatformTabs(
    selected: GolikePlatform,
    counts: Map<GolikePlatform, Int>,
    onSelect: (GolikePlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE6EBF3), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GolikePlatform.entries.forEach { p ->
            val on = p == selected
            val shape = RoundedCornerShape(12.dp)
            PressBox(
                onClick = { onSelect(p) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .then(if (on) Modifier.background(p.soft(), shape) else Modifier)
                    .clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "${p.label} (${counts[p] ?: 0})",
                    style = type(13f, if (on) FontWeight.ExtraBold else FontWeight.Bold, if (on) p.accent() else Alx.Muted),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GolikeTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    PressBox(
        onClick = onClick,
        modifier = Modifier
            .height(38.dp)
            .then(
                if (selected) Modifier.background(Alx.Ink, shape)
                else Modifier.background(Alx.White, shape).border(1.dp, Alx.Line, shape),
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = type(13f, if (selected) FontWeight.Bold else FontWeight.SemiBold, if (selected) Alx.White else Alx.Ink2))
    }
}

@Composable
private fun GolikeEmptyState(platform: GolikePlatform, onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    if (platform == GolikePlatform.TikTok) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(22.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).background(GolikeOrangeSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(EmptyPeople, 26.dp, GolikeOrange, 1.8f) }
            BasicText(
                "Chưa có tài khoản nào được thêm",
                Modifier.padding(top = 8.dp),
                style = type(15f, FontWeight.Bold, Alx.Ink),
            )
            BasicText(
                "Nhấn dấu (+) ở trên để quét tài khoản TikTok từ máy.",
                Modifier.padding(top = 4.dp),
                style = type(13f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f, align = TextAlign.Center),
            )
        }
    } else {
        val label = platform.label
        PressBox(
            onClick = onAddAccount,
            modifier = modifier
                .fillMaxWidth()
                .background(platform.soft(), RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    "Chưa có tài khoản $label nào.",
                    style = type(14f, FontWeight.SemiBold, Alx.Muted, lineHeight = 1.6f, align = TextAlign.Center)
                )
                BasicText(
                    "Bấm vào đây hoặc nút dấu + để thêm tài khoản $label.",
                    Modifier.padding(top = 2.dp),
                    style = type(14f, FontWeight.ExtraBold, platform.accent(), lineHeight = 1.6f, align = TextAlign.Center),
                )
            }
        }
    }
}

/**
 * Mỗi dòng tài khoản:
 * - Checkbox
 * - Avatar tròn 42dp
 * - Tên + badge "Chưa liên kết" (cam) / "Đã liên kết" (xanh lá)
 * - Cảnh báo "⚠ Cần liên kết Golike" (cam) nếu chưa liên kết
 * - @handle xám bên dưới
 * - Nút liên kết tròn bên phải
 */
@Composable
private fun GolikeAccountRow(
    account: GolikeAccount,
    selected: Boolean,
    isLinking: Boolean = false,
    onLink: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    PressBox(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .background(Alx.White, shape)
            .border(1.dp, if (selected) GolikeOrange else Alx.Line, shape)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GolikeCheckbox(selected)

            // Avatar tròn 42dp
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Alx.Chip),
                contentAlignment = Alignment.Center
            ) {
                if (account.avatar.isNotBlank()) {
                    AsyncImage(
                        model = account.avatar,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BasicText(
                        account.username.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?",
                        style = type(15f, FontWeight.ExtraBold, Alx.ChipText)
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BasicText(
                        account.username,
                        style = type(14.5f, FontWeight.Bold, Alx.Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Badge: "Đã liên kết" (xanh lá) / "Chưa liên kết" (cam)
                    if (account.isGolikeLinked) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LiveGreen.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(LiveGreen))
                            Spacer(Modifier.width(4.dp))
                            BasicText("Đã liên kết", style = type(10f, FontWeight.ExtraBold, LiveGreen))
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GolikeOrange.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(GolikeOrange))
                            Spacer(Modifier.width(4.dp))
                            BasicText("Chưa liên kết", style = type(10f, FontWeight.ExtraBold, GolikeOrange))
                        }
                    }
                }

                // Cảnh báo "⚠ Cần liên kết Golike" nếu tài khoản chưa được liên kết
                if (!account.isGolikeLinked) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        "⚠ Cần liên kết Golike",
                        style = type(11.5f, FontWeight.Bold, GolikeOrange)
                    )
                }

                // Dòng @handle xám nhỏ bên dưới
                Spacer(Modifier.height(2.dp))
                BasicText(
                    if (account.id.startsWith("@")) account.id else "@${account.id}",
                    style = type(11.5f, FontWeight.Medium, Alx.Muted)
                )
            }

            // Nút liên kết tròn bên phải
            PressBox(
                onClick = onLink,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (account.isGolikeLinked) Alx.Bg else GolikeOrangeSoft),
                contentAlignment = Alignment.Center
            ) {
                if (isLinking) {
                    CircularProgressIndicator(
                        color = GolikeOrange,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    SvgIcon(
                        paths = Refresh,
                        size = 17.dp,
                        color = if (account.isGolikeLinked) Alx.Muted else GolikeOrange,
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

@Composable
private fun GolikeInstagramAccountRow(
    account: GolikeAccount,
    selected: Boolean,
    isLinking: Boolean = false,
    onLink: () -> Unit,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onReload: () -> Unit,
    onErrorClick: () -> Unit = {}
) {
    val isRunning = com.cayxu.app.ui.screens.golike.GolikeRunningManager.isRunning(account.id) || com.cayxu.app.ui.screens.golike.GolikeRunningManager.isRunning(account.username)
    val igAccent = Color(0xFFC2255C)
    val igSoft = Color(0xFFFDEAF2)
    val liveColor = if (account.isLive) Color(0xFF15803D) else Color(0xFFC9363B)
    val liveSoft = if (account.isLive) Color(0xFFE7F8EE) else Color(0xFFFDECEC)
    val cardShape = RoundedCornerShape(20.dp)

    PressBox(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .background(Alx.White, cardShape)
            .border(1.dp, if (selected) igAccent else Color(0xFFE4E8F0), cardShape)
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hàng 1 (Top Row): Checkbox + Avatar (với chấm xanh góc dưới) + Column(Tên + Badge liên kết) + [⟳] + [▶ / ⏹]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                GolikeCheckbox(selected, checkedColor = igAccent)

                // Avatar 42.dp với chấm xanh góc dưới bên phải
                Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(igSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        if (account.avatar.isNotBlank()) {
                            AsyncImage(
                                model = account.avatar,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            BasicText(
                                account.username.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "A",
                                style = type(15f, FontWeight.ExtraBold, igAccent)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Alx.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(liveColor)
                    )
                }

                // Cột giữa: Username + Badge liên kết Golike
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        val displayName = if (account.username.startsWith("@")) account.username else "@${account.username}"
                        BasicText(
                            displayName,
                            style = type(14.5f, FontWeight.ExtraBold, Alx.Ink),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!account.isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(liveSoft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                BasicText("Die", style = type(10.5f, FontWeight.ExtraBold, liveColor))
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    if (account.cookie.isBlank()) {
                        PressBox(
                            onClick = onLink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFD97706)))
                                BasicText(
                                    "Chưa có Cookie trong tool",
                                    style = type(10.5f, FontWeight.ExtraBold, Color(0xFFB45309))
                                )
                            }
                        }
                    } else if (account.isGolikeLinked) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFE7F8EE))
                                .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SvgIcon(Check, 10.dp, Color(0xFF15803D), 2.5f)
                            BasicText(
                                "Đã liên kết Golike",
                                style = type(10.5f, FontWeight.ExtraBold, Color(0xFF15803D))
                            )
                        }
                    } else {
                        PressBox(
                            onClick = onLink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF1F4BD8).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isLinking) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF1F4BD8),
                                        strokeWidth = 1.5.dp,
                                        modifier = Modifier.size(9.dp)
                                    )
                                } else {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF1F4BD8)))
                                }
                                BasicText(
                                    if (isLinking) "Đang liên kết..." else "Chưa liên kết",
                                    style = type(10.5f, FontWeight.ExtraBold, Color(0xFF1F4BD8))
                                )
                            }
                        }
                    }
                }

                // Cụm nút bên phải: Reload [⟳] (xám) & Play/Stop [▶ / ⏹] (hồng / đỏ)
                PressBox(
                    onClick = onReload,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF3F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon(Refresh, 16.dp, Alx.Muted, 2f)
                }

                PressBox(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isRunning) Color(0xFFC9363B) else igAccent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRunning) {
                        Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.5.dp)).background(Alx.White))
                    } else {
                        SvgIcon(Play, 14.dp, Alx.White, 2f, fill = true)
                    }
                }
            }

            // Hàng 2 (Middle Row): Text kết quả / trạng thái làm việc
            val liveStatus = com.cayxu.app.ui.screens.golike.GolikeRunningManager.statusMap[account.id]
                ?: com.cayxu.app.ui.screens.golike.GolikeRunningManager.statusMap[account.username]
                ?: account.lastStatus
            if (liveStatus.isNotBlank()) {
                BasicText(
                    liveStatus,
                    style = type(12.5f, FontWeight.Medium, if (liveStatus.contains("Lỗi", ignoreCase = true)) Color(0xFFDC2626) else Alx.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val successCount = com.cayxu.app.ui.screens.golike.GolikeRunningManager.successCountMap[account.id]
                ?: com.cayxu.app.ui.screens.golike.GolikeRunningManager.successCountMap[account.username]
                ?: account.successCount
            val errorCount = com.cayxu.app.ui.screens.golike.GolikeRunningManager.errorCountMap[account.id]
                ?: com.cayxu.app.ui.screens.golike.GolikeRunningManager.errorCountMap[account.username]
                ?: account.errorCount

            // Hàng 3 (Bottom Row): 2 Badge viên thuốc Hoàn thành (xanh) và Lỗi (đỏ)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge Hoàn thành (xanh)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFE7F8EE))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    BasicText(
                        "Hoàn thành: $successCount",
                        style = type(11.5f, FontWeight.ExtraBold, Color(0xFF15803D))
                    )
                }

                // Badge Lỗi (đỏ)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFDECEC))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    BasicText(
                        "Lỗi: $errorCount",
                        style = type(11.5f, FontWeight.ExtraBold, Color(0xFFC9363B))
                    )
                }

                Spacer(Modifier.weight(1f))

                // Nút chấm than đỏ (!) cùng hàng ở phía bên phải
                PressBox(
                    onClick = onErrorClick,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFDECEC))
                        .border(1.dp, Color(0xFFFCA5A5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon(
                        paths = AlertCircle,
                        size = 16.dp,
                        color = Color(0xFFDC2626),
                        strokeWidth = 2.2f
                    )
                }
            }
        }
    }
}

@Composable
private fun GolikeCheckbox(checked: Boolean, checkedColor: Color = GolikeOrange) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(22.dp)
            .then(if (checked) Modifier.background(checkedColor, shape) else Modifier.background(Alx.White, shape).border(1.8.dp, Color(0xFF94A3B8), shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) SvgIcon(listOf("M5 13l4 4L19 7"), 13.dp, Alx.White, 2.6f)
    }
}

@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = if (pressed) 0.985f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
    ) { content() }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GolikeAccountsScreenPreview() {
    GolikeAccountsScreen(
        state = GolikeAccountsUiState(
            username = "thanhgmn",
            balanceDong = 50574,
            platformCounts = mapOf(GolikePlatform.TikTok to 2),
            selectedPlatform = GolikePlatform.TikTok,
            accounts = listOf(
                GolikeAccount(id = "thanhgmn", platform = "tiktok", username = "Thành Phạm", isGolikeLinked = true),
                GolikeAccount(id = "user123", platform = "tiktok", username = "User 123", isGolikeLinked = false),
            )
        ),
        onBack = {},
        onRefreshBalance = {},
        onLogout = {},
        onSelectPlatform = {},
        onSelectType = {},
        onToggleSelectAll = {},
        onToggleAccount = {},
        onLinkAccount = {},
        onAddAccount = {},
        onOpenConfig = {},
        onRun = {},
    )
}
