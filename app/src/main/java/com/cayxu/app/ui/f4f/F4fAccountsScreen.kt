package com.cayxu.app.ui.f4f

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/* ───────────────────────────── Dữ liệu Follow4Follow ───────────────────────────── */

enum class F4fPlatform(val label: String) { TikTok("TikTok"), Facebook("Facebook"), Instagram("Instagram") }

private val F4fViolet = Color(0xFF7C3AED)
private val F4fVioletSoft = Color(0xFFF5F3FF)

private fun F4fPlatform.accent(): Color = when (this) {
    F4fPlatform.Instagram -> Color(0xFFC2255C)
    F4fPlatform.Facebook -> Color(0xFF1877F2)
    F4fPlatform.TikTok -> F4fViolet
}

private fun F4fPlatform.soft(): Color = when (this) {
    F4fPlatform.Instagram -> Color(0xFFFDEAF2)
    F4fPlatform.Facebook -> Color(0xFFE7F3FF)
    F4fPlatform.TikTok -> F4fVioletSoft
}

data class F4fAccountTypeFilter(val id: String, val label: String)

val DefaultF4fAccountTypes: Map<F4fPlatform, List<F4fAccountTypeFilter>> = mapOf(
    F4fPlatform.TikTok to listOf(
        F4fAccountTypeFilter("tiktok", "TikTok"),
        F4fAccountTypeFilter("tiktok_lite", "TikTok Lite"),
        F4fAccountTypeFilter("tiktok_studio", "TikTok Studio"),
    ),
    F4fPlatform.Facebook to listOf(F4fAccountTypeFilter("facebook", "Facebook")),
    F4fPlatform.Instagram to listOf(F4fAccountTypeFilter("instagram", "Instagram")),
)

data class F4fPageItem(
    val pageId: String,
    val pageName: String,
    val displayUid: String = "",
    val avatarUrl: String = "",
    val isLive: Boolean = true,
    val isLinked: Boolean = false,
    val isRunning: Boolean = false,
    val isReloading: Boolean = false,
)

data class F4fAccount(
    val id: String,
    val name: String,
    val typeId: String,
    val statusLabel: String = "Hoạt động",
    val statusOk: Boolean = true,
    val avatarUrl: String = "",
    val handle: String = "",
    val followerCount: Long = 0L,
    val heartCount: Long = 0L,
    val createDateFormatted: String = "",
    val isLive: Boolean = true,
    val isReloading: Boolean = false,
    val isLinked: Boolean = true,
    val isLinking: Boolean = false,
    val isRunning: Boolean = false,
    val pages: List<F4fPageItem> = emptyList(),
)

data class F4fAccountsUiState(
    val username: String = "",
    val coinBalance: Long = 0L,
    val platformCounts: Map<F4fPlatform, Int> = emptyMap(),
    val selectedPlatform: F4fPlatform = F4fPlatform.TikTok,
    val selectedTypeId: String? = null,
    val accounts: List<F4fAccount> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
    val refreshingBalance: Boolean = false,
    val isAnyRunning: Boolean = false,
)

private fun F4fAccountsUiState.visibleAccounts(): List<F4fAccount> =
    accounts.filter { selectedTypeId == null || it.typeId == selectedTypeId }

/* ───────────────────────────── Icon (24x24) ───────────────────────────── */

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
private val Info = listOf(
    circle(12f, 12f, 9f),
    "M12 8v.01",
    "M12 11v5"
)

/* ───────────────────────────── Màn hình Follow4Follow ───────────────────────────── */

@Composable
fun F4fAccountsScreen(
    state: F4fAccountsUiState,
    onBack: () -> Unit,
    onRefreshBalance: () -> Unit,
    onLogout: () -> Unit,
    onSelectPlatform: (F4fPlatform) -> Unit,
    onSelectType: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleAccount: (F4fAccount) -> Unit,
    onAddAccount: () -> Unit,
    onOpenConfig: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    accountTypes: Map<F4fPlatform, List<F4fAccountTypeFilter>> = DefaultF4fAccountTypes,
    onReloadAccount: ((F4fAccount) -> Unit)? = null,
    onDeleteSelected: (() -> Unit)? = null,
    onLinkAccount: ((F4fAccount) -> Unit)? = null,
    onTogglePlayAccount: ((F4fAccount) -> Unit)? = null,
    onTogglePage: ((parentAcc: F4fAccount, page: F4fPageItem) -> Unit)? = null,
    onTogglePlayPage: ((parentAcc: F4fAccount, page: F4fPageItem) -> Unit)? = null,
    onReloadPage: ((parentAcc: F4fAccount, page: F4fPageItem) -> Unit)? = null,
    onOpenInfoAccount: ((F4fAccount) -> Unit)? = null,
    onOpenInfoPage: ((parentAcc: F4fAccount, page: F4fPageItem) -> Unit)? = null,
) {
    val types = accountTypes[state.selectedPlatform].orEmpty()
    val visible = state.visibleAccounts()
    val allSelected = visible.isNotEmpty() && visible.all { it.id in state.selectedAccountIds }

    Box(modifier.fillMaxSize().background(Alx.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PressBox(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
                BasicText("Follow4Follow", style = type(24f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
            }

            AccountCard(state, onRefreshBalance, onLogout, Modifier.padding(top = 20.dp))

            PlatformTabs(state.selectedPlatform, state.platformCounts, onSelectPlatform, Modifier.padding(top = 16.dp))

            // Section header
            Row(
                Modifier.padding(top = 22.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "Tài khoản ${state.selectedPlatform.label}",
                    Modifier.weight(1f),
                    style = type(18f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (state.selectedPlatform == F4fPlatform.TikTok) {
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

            if (types.size > 1) {
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    types.forEach { t -> TypeChip(t.label, selected = state.selectedTypeId == t.id || (state.selectedTypeId == null && t == types.first())) { onSelectType(t.id) } }
                }
            }

            if (visible.isEmpty()) {
                EmptyState(state.selectedPlatform, onAddAccount, Modifier.padding(top = 16.dp))
            } else {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visible.forEach { acc ->
                        AccountRow(
                            account = acc,
                            selected = acc.id in state.selectedAccountIds,
                            platform = state.selectedPlatform,
                            selectedPageIds = state.selectedAccountIds,
                            onReload = onReloadAccount?.let { { it(acc) } },
                            onLinkAccount = onLinkAccount?.let { { it(acc) } },
                            onTogglePlay = onTogglePlayAccount?.let { { it(acc) } },
                            onTogglePage = onTogglePage?.let { cb -> { p -> cb(acc, p) } },
                            onTogglePlayPage = onTogglePlayPage?.let { cb -> { p -> cb(acc, p) } },
                            onReloadPage = onReloadPage?.let { cb -> { p -> cb(acc, p) } },
                            onOpenInfoAccount = onOpenInfoAccount?.let { { it(acc) } },
                            onOpenInfoPage = onOpenInfoPage?.let { cb -> { p -> cb(acc, p) } },
                            onClick = { onToggleAccount(acc) }
                        )
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
        }

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
            val configAccent = if (state.selectedPlatform == F4fPlatform.Instagram) F4fPlatform.Instagram.accent() else Alx.Ink
            val configBorder = if (state.selectedPlatform == F4fPlatform.Instagram) F4fPlatform.Instagram.accent() else Alx.InputBorder
            PressBox(
                onClick = onOpenConfig,
                modifier = Modifier.weight(1f).height(56.dp)
                    .background(Alx.White, RoundedCornerShape(18.dp))
                    .border(1.5.dp, configBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvgIcon(Gear, 18.dp, configAccent, 1.9f)
                    BasicText("Cấu hình", style = type(15f, FontWeight.Bold, configAccent))
                }
            }

            if (state.selectedPlatform == F4fPlatform.TikTok) {
                val isRunning = state.isAnyRunning
                val runBg = if (isRunning) Alx.Danger else state.selectedPlatform.accent()
                val runCount = state.selectedAccountIds.size
                val runText = if (isRunning) "Dừng lại" else if (runCount > 1) "Chạy ($runCount)" else "Chạy"

                PressBox(
                    onClick = onRun,
                    modifier = Modifier.weight(1.25f).height(56.dp).background(runBg, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isRunning) {
                            Box(Modifier.size(13.dp).clip(RoundedCornerShape(2.5.dp)).background(Alx.White))
                        } else {
                            SvgIcon(Play, 16.dp, Alx.White, 2f, fill = true)
                        }
                        BasicText(runText, style = type(15f, FontWeight.Bold, Alx.White))
                    }
                }
            } else {
                Row(
                    Modifier.weight(1.25f).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    PressBox(onClick = onToggleSelectAll, contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(allSelected)
                            BasicText("Tất cả", style = type(13f, FontWeight.SemiBold, Alx.Ink2))
                        }
                    }

                    if (state.accounts.isNotEmpty()) {
                        val isRunning = state.isAnyRunning
                        PressBox(
                            onClick = onRun,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(44.dp)
                                .background(if (isRunning) Alx.Danger else state.selectedPlatform.accent(), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isRunning) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Alx.White))
                            } else {
                                SvgIcon(Play, 16.dp, Alx.White, 2f, fill = true)
                            }
                        }
                    }

                    PressBox(
                        onClick = onAddAccount,
                        modifier = Modifier.padding(start = 12.dp).size(44.dp).background(state.selectedPlatform.accent(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { SvgIcon(Plus, 18.dp, Alx.White, 2.4f) }
                }
            }
        }
    }
}

/* ───────────────────────────── Thành phần con ───────────────────────────── */

@Composable
private fun AccountCard(
    state: F4fAccountsUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "f4fRefresh")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "f4fRefreshAngle",
    )
    Row(
        modifier.fillMaxWidth()
            .background(Alx.White, RoundedCornerShape(22.dp))
            .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).background(F4fVioletSoft, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(
                state.username.firstOrNull()?.uppercaseChar()?.toString() ?: "F",
                style = type(18f, FontWeight.ExtraBold, F4fViolet),
            )
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                state.username.ifBlank { "Follow4Follow" },
                style = type(15f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.25f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "${formatF4fCoins(state.coinBalance)} xu",
                Modifier.padding(top = 2.dp),
                style = type(13f, FontWeight.ExtraBold, F4fViolet, lineHeight = 1.3f),
            )
        }
        PressBox(
            onClick = onRefresh,
            modifier = Modifier.size(40.dp).background(Alx.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SvgIcon(
                Refresh, 18.dp, F4fViolet, 2f,
                modifier = if (state.refreshingBalance) Modifier.graphicsLayer { rotationZ = angle } else Modifier,
            )
        }
        PressBox(
            onClick = onLogout,
            modifier = Modifier.size(40.dp).background(F4fVioletSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Logout, 18.dp, F4fViolet, 2f) }
    }
}

@Composable
private fun PlatformTabs(
    selected: F4fPlatform,
    counts: Map<F4fPlatform, Int>,
    onSelect: (F4fPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(Color(0xFFE6EBF3), RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        F4fPlatform.entries.forEach { p ->
            val on = p == selected
            val shape = RoundedCornerShape(12.dp)
            PressBox(
                onClick = { onSelect(p) },
                modifier = Modifier.weight(1f).height(42.dp)
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
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    PressBox(
        onClick = onClick,
        modifier = Modifier.height(38.dp)
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
private fun EmptyState(platform: F4fPlatform, onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    if (platform == F4fPlatform.TikTok) {
        Column(
            modifier.fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(22.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).background(F4fVioletSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(EmptyPeople, 26.dp, F4fViolet, 1.8f) }
            BasicText(
                "Chưa có tài khoản nào ở loại này",
                Modifier.padding(top = 6.dp),
                style = type(15f, FontWeight.Bold, Alx.Ink),
            )
            BasicText(
                "Thêm tài khoản ở phần Quản lý tài khoản TikTok trước, rồi quay lại đây để chạy.",
                Modifier.padding(top = 4.dp),
                style = type(13f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f, align = TextAlign.Center),
            )
        }
    } else {
        val label = platform.label
        val (line1, line2) = if (platform == F4fPlatform.Facebook)
            "Chưa có tài khoản $label nào." to "Bấm vào đây hoặc nút dấu + để đăng nhập tài khoản $label."
        else
            "Chưa có tài khoản $label nào." to "Bấm dấu + để đăng nhập tài khoản $label."
        PressBox(
            onClick = onAddAccount,
            modifier = modifier.fillMaxWidth()
                .background(platform.soft(), RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(line1, style = type(14f, FontWeight.SemiBold, Alx.Muted, lineHeight = 1.6f, align = TextAlign.Center))
                BasicText(
                    line2,
                    Modifier.padding(top = 0.dp),
                    style = type(14f, FontWeight.ExtraBold, platform.accent(), lineHeight = 1.6f, align = TextAlign.Center),
                )
            }
        }
    }
}

private val LiveGreen = Color(0xFF16A34A)

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
private fun AccountRow(
    account: F4fAccount,
    selected: Boolean,
    platform: F4fPlatform,
    selectedPageIds: Set<String>,
    onReload: (() -> Unit)? = null,
    onLinkAccount: (() -> Unit)? = null,
    onTogglePlay: (() -> Unit)? = null,
    onTogglePage: ((F4fPageItem) -> Unit)? = null,
    onTogglePlayPage: ((F4fPageItem) -> Unit)? = null,
    onReloadPage: ((F4fPageItem) -> Unit)? = null,
    onOpenInfoAccount: (() -> Unit)? = null,
    onOpenInfoPage: ((F4fPageItem) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val isTiktok = platform == F4fPlatform.TikTok
    val isFacebook = platform == F4fPlatform.Facebook
    val isInstagram = platform == F4fPlatform.Instagram

    Column(
        Modifier
            .fillMaxWidth()
            .background(Alx.White, RoundedCornerShape(22.dp))
            .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox, onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(selected)

            Box(Modifier.size(46.dp)) {
                if (account.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(Alx.Bg),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(platform.soft(), CircleShape), contentAlignment = Alignment.Center) {
                        BasicText(
                            account.name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                            style = type(16f, FontWeight.ExtraBold, platform.accent()),
                        )
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        account.name,
                        style = type(14.5f, FontWeight.Bold, Alx.Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isFacebook || isInstagram) {
                        Box(
                            Modifier
                                .background(if (account.isLive) Color(0xFFDCFCE7) else Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            BasicText(
                                if (account.isLive) "Live" else "Die",
                                style = type(10.5f, FontWeight.Bold, if (account.isLive) LiveGreen else Alx.Danger)
                            )
                        }
                    }
                }

                if (account.handle.isNotBlank()) {
                    BasicText(
                        account.handle,
                        Modifier.padding(top = 1.dp),
                        style = type(12.5f, FontWeight.Medium, Alx.Muted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isTiktok && (account.followerCount > 0 || account.heartCount > 0)) {
                    Row(
                        Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            "${formatCount(account.followerCount)} Follower",
                            style = type(11.5f, FontWeight.SemiBold, Alx.Ink2)
                        )
                        BasicText("•", style = type(11.5f, FontWeight.Bold, Alx.Muted))
                        BasicText(
                            "${formatCount(account.heartCount)} Tim",
                            style = type(11.5f, FontWeight.SemiBold, Alx.Ink2)
                        )
                    }
                }

                if (account.createDateFormatted.isNotBlank()) {
                    BasicText(
                        "Tạo ngày: ${account.createDateFormatted}",
                        Modifier.padding(top = 2.dp),
                        style = type(11f, FontWeight.Medium, Alx.Muted)
                    )
                }
            }

            // Reload button
            if (onReload != null) {
                PressBox(
                    onClick = onReload,
                    modifier = Modifier.size(36.dp).background(Alx.Bg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (account.isReloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = platform.accent())
                    } else {
                        SvgIcon(Refresh, 16.dp, Alx.Ink2, 2f)
                    }
                }
            }
        }
    }
}

@Composable
private fun Checkbox(checked: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(22.dp)
            .then(if (checked) Modifier.background(F4fViolet, shape) else Modifier.background(Alx.White, shape).border(1.8.dp, Color(0xFF94A3B8), shape)),
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

private fun formatF4fCoins(v: Long): String {
    val s = v.toString()
    val sb = StringBuilder()
    for (i in s.indices) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(s[i])
    }
    return sb.toString()
}
