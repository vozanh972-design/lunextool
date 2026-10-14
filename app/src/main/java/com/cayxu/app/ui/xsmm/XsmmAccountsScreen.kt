package com.cayxu.app.ui.xsmm

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/*
 * Màn "XSMM" (danh sách tài khoản sau khi đăng nhập) — CHỈ là lớp giao diện, giống XsmmLoginScreen.
 * Không gọi mạng, không tự tính số dư/trạng thái tài khoản: màn này chỉ hiển thị [XsmmAccountsUiState]
 * do bạn truyền vào và báo lại thao tác người dùng qua callback. Xem README để nối dữ liệu thật.
 *
 * MỘT file này vẽ cả 3 tab TikTok / Facebook / Instagram (đổi qua [state.selectedPlatform]) vì mỗi tab
 * có bố cục khác nhau một chút, khớp đúng thiết kế gốc:
 * - TikTok: có hàng chip lọc loại tài khoản; trạng thái rỗng là thẻ trắng viền + icon; thanh dưới có "Chạy".
 * - Facebook/Instagram: KHÔNG có chip loại; trạng thái rỗng là thẻ tô nền xanh nhạt, bấm CẢ THẺ để đăng
 *   nhập luôn; thanh dưới KHÔNG có "Chạy" mà thay bằng checkbox "Tất cả" + nút (+).
 * - Màu nhận diện theo nền tảng: TikTok/Facebook xanh dương, Instagram hồng-magenta — tô cho tab đang chọn,
 *   nút (+), và nút "Cấu hình" (chỉ đổi màu khi đang ở tab Instagram).
 */

/* ───────────────────────────── Dữ liệu ───────────────────────────── */

enum class XsmmPlatform(val label: String) { TikTok("TikTok"), Facebook("Facebook"), Instagram("Instagram") }

/** Màu nhận diện theo nền tảng: TikTok/Facebook dùng xanh dương sẵn có của app; Instagram dùng hồng-magenta
 *  (màu này app đã dùng cho mục Tương tác chéo, không phải màu mới). Tô cho tab đang chọn, nút (+), v.v. */
private fun XsmmPlatform.accent(): Color = if (this == XsmmPlatform.Instagram) Color(0xFFC2255C) else Alx.Blue
private fun XsmmPlatform.soft(): Color = if (this == XsmmPlatform.Instagram) Color(0xFFFDEAF2) else Alx.BlueSoft

/** Loại tài khoản lọc theo platform, ví dụ TikTok: "TikTok" / "TikTok Lite" / "TikTok Studio". */
data class XsmmAccountTypeFilter(val id: String, val label: String)

/** Danh sách loại mặc định theo từng platform — sửa lại nếu bộ loại thật của bạn khác. */
val DefaultXsmmAccountTypes: Map<XsmmPlatform, List<XsmmAccountTypeFilter>> = mapOf(
    XsmmPlatform.TikTok to listOf(
        XsmmAccountTypeFilter("tiktok", "TikTok"),
        XsmmAccountTypeFilter("tiktok_lite", "TikTok Lite"),
        XsmmAccountTypeFilter("tiktok_studio", "TikTok Studio"),
    ),
    XsmmPlatform.Facebook to listOf(XsmmAccountTypeFilter("facebook", "Facebook")),
    XsmmPlatform.Instagram to listOf(XsmmAccountTypeFilter("instagram", "Instagram")),
)

/** Một tài khoản trong danh sách. [statusLabel]/[statusOk] để tô màu trạng thái (vd "Hoạt động" / "Lỗi"). */
data class XsmmAccount(
    val id: String,
    val name: String,
    val typeId: String,
    val statusLabel: String = "Hoạt động",
    val statusOk: Boolean = true,
)

data class XsmmAccountsUiState(
    val username: String,
    val coinBalance: Long,
    val platformCounts: Map<XsmmPlatform, Int> = emptyMap(),
    val selectedPlatform: XsmmPlatform = XsmmPlatform.TikTok,
    val selectedTypeId: String? = null,
    val accounts: List<XsmmAccount> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
    val refreshingBalance: Boolean = false,
)

/** Tài khoản của platform + loại đang được lọc hiện tại — dùng để quyết định hiện danh sách hay trạng thái rỗng. */
private fun XsmmAccountsUiState.visibleAccounts(): List<XsmmAccount> =
    accounts.filter { selectedTypeId == null || it.typeId == selectedTypeId }

/* ───────────────────────────── Icon (khung 24×24) ───────────────────────────── */

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

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * @param state           dữ liệu hiển thị hiện tại (số dư, platform/loại đang chọn, danh sách tài khoản…).
 * @param onBack          bấm nút quay lại ở header.
 * @param onRefreshBalance bấm nút làm mới số dư xu — gọi API lấy số dư mới rồi cập nhật [state.coinBalance].
 * @param onLogout        bấm icon đăng xuất — xoá token XSMM và điều hướng về màn đăng nhập.
 * @param onSelectPlatform bấm 1 trong 3 tab TikTok/Facebook/Instagram.
 * @param onSelectType     bấm 1 chip loại tài khoản (null = "tất cả loại" nếu bạn thêm chip đó).
 * @param onToggleSelectAll bấm checkbox "Tất cả" ở đầu danh sách.
 * @param onToggleAccount  bấm chọn/bỏ chọn một dòng tài khoản (chỉ dùng nếu bạn thêm checkbox từng dòng).
 * @param onAddAccount     bấm nút (+) thêm tài khoản.
 * @param onOpenConfig     bấm "Cấu hình" ở thanh dưới.
 * @param onRun            bấm "Chạy" ở thanh dưới.
 */
@Composable
fun XsmmAccountsScreen(
    state: XsmmAccountsUiState,
    onBack: () -> Unit,
    onRefreshBalance: () -> Unit,
    onLogout: () -> Unit,
    onSelectPlatform: (XsmmPlatform) -> Unit,
    onSelectType: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleAccount: (XsmmAccount) -> Unit,
    onAddAccount: () -> Unit,
    onOpenConfig: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    accountTypes: Map<XsmmPlatform, List<XsmmAccountTypeFilter>> = DefaultXsmmAccountTypes,
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
                BasicText("XSMM", style = type(24f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PressBox(onClick = onToggleSelectAll, contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(allSelected)
                            BasicText("Tất cả", style = type(13f, FontWeight.SemiBold, Alx.Muted))
                        }
                    }
                    PressBox(
                        onClick = onAddAccount,
                        modifier = Modifier.size(40.dp).background(state.selectedPlatform.accent(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { SvgIcon(Plus, 18.dp, Alx.White, 2.4f) }
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
                    visible.forEach { acc -> AccountRow(acc, acc.id in state.selectedAccountIds) { onToggleAccount(acc) } }
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
            // "Cấu hình": viền/chữ trung tính, trừ Instagram thì tô theo màu nhận diện (giống thiết kế gốc).
            val configAccent = if (state.selectedPlatform == XsmmPlatform.Instagram) XsmmPlatform.Instagram.accent() else Alx.Ink
            val configBorder = if (state.selectedPlatform == XsmmPlatform.Instagram) XsmmPlatform.Instagram.accent() else Alx.InputBorder
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
            // TikTok: nút "Chạy". Facebook/Instagram: KHÔNG có "Chạy" (chưa có gì để chạy) — thay bằng
            // checkbox "Tất cả" + nút (+), giống đúng bố trí màn gốc, chỉ đổi màu theo nền tảng.
            if (state.selectedPlatform == XsmmPlatform.TikTok) {
                PressBox(
                    onClick = onRun,
                    modifier = Modifier.weight(1.25f).height(56.dp).background(Alx.Ink, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SvgIcon(Play, 16.dp, Alx.White, 2f, fill = true)
                        BasicText("Chạy", style = type(15f, FontWeight.Bold, Alx.White))
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
                    PressBox(
                        onClick = onAddAccount,
                        modifier = Modifier.padding(start = 14.dp).size(40.dp).background(state.selectedPlatform.accent(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { SvgIcon(Plus, 18.dp, Alx.White, 2.4f) }
                }
            }
        }
    }
}

/* ───────────────────────────── Thành phần ───────────────────────────── */

private val XsmmRed = Color(0xFFC9363B)
private val XsmmRedSoft = Color(0xFFFDECEC)

@Composable
private fun AccountCard(
    state: XsmmAccountsUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "xsmmRefresh")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "xsmmRefreshAngle",
    )
    Row(
        modifier.fillMaxWidth()
            .background(Alx.White, RoundedCornerShape(22.dp))
            .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).background(XsmmRedSoft, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(
                state.username.firstOrNull()?.uppercaseChar()?.toString() ?: "T",
                style = type(18f, FontWeight.ExtraBold, XsmmRed),
            )
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                state.username, style = type(15f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.25f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "${formatXsmmCoins(state.coinBalance)} xu",
                Modifier.padding(top = 2.dp),
                style = type(13f, FontWeight.ExtraBold, Alx.Blue, lineHeight = 1.3f),
            )
        }
        PressBox(
            onClick = onRefresh,
            modifier = Modifier.size(40.dp).background(Alx.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SvgIcon(
                Refresh, 18.dp, Alx.Blue, 2f,
                modifier = if (state.refreshingBalance) Modifier.graphicsLayer { rotationZ = angle } else Modifier,
            )
        }
        PressBox(
            onClick = onLogout,
            modifier = Modifier.size(40.dp).background(XsmmRedSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Logout, 18.dp, XsmmRed, 2f) }
    }
}

@Composable
private fun PlatformTabs(
    selected: XsmmPlatform,
    counts: Map<XsmmPlatform, Int>,
    onSelect: (XsmmPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(Color(0xFFE6EBF3), RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        XsmmPlatform.entries.forEach { p ->
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

/**
 * Trạng thái rỗng — KHÁC NHAU theo nền tảng, đúng theo thiết kế gốc:
 * - TikTok: thẻ trắng viền + icon, chỉ để xem (tài khoản TikTok phải thêm ở màn Quản lý tài khoản riêng).
 * - Facebook/Instagram: thẻ tô nền xanh nhạt, KHÔNG icon, bấm được CẢ THẺ để đăng nhập luôn (không chỉ nút +).
 *   Dòng 2 tô theo màu nhận diện nền tảng (xanh cho Facebook, hồng cho Instagram).
 */
@Composable
private fun EmptyState(platform: XsmmPlatform, onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    if (platform == XsmmPlatform.TikTok) {
        Column(
            modifier.fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(22.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).background(Alx.BlueSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(EmptyPeople, 26.dp, Alx.Blue, 1.8f) }
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
        val (line1, line2) = if (platform == XsmmPlatform.Facebook)
            "Chưa có tài khoản $label nào." to "Bấm vào đây hoặc nút dấu + để đăng nhập tài khoản $label."
        else
            "Chưa có tài khoản $label nào." to "Bấm dấu + để đăng nhập tài khoản $label."
        PressBox(
            onClick = onAddAccount,
            modifier = modifier.fillMaxWidth()
                .background(Alx.BlueSoft, RoundedCornerShape(22.dp))
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

/**
 * Dòng tài khoản khi danh sách KHÔNG rỗng. Thiết kế gốc chỉ có trạng thái rỗng, nên dòng này là bố cục
 * hợp lý theo cùng phong cách (checkbox + avatar chữ cái đầu + tên + trạng thái) — bạn có thể đổi tự do.
 */
@Composable
private fun AccountRow(account: XsmmAccount, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    PressBox(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .background(Alx.White, shape)
            .border(1.dp, if (selected) Alx.Blue else Alx.Line, shape)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(selected)
            Box(Modifier.size(40.dp).background(Alx.Chip, CircleShape), contentAlignment = Alignment.Center) {
                BasicText(account.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", style = type(14f, FontWeight.ExtraBold, Alx.ChipText))
            }
            Column(Modifier.weight(1f)) {
                BasicText(account.name, style = type(14.5f, FontWeight.Bold, Alx.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    account.statusLabel,
                    Modifier.padding(top = 2.dp),
                    style = type(12f, FontWeight.SemiBold, if (account.statusOk) Alx.Blue else Alx.Danger),
                )
            }
        }
    }
}

@Composable
private fun Checkbox(checked: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(22.dp)
            .then(if (checked) Modifier.background(Alx.Blue, shape) else Modifier.background(Alx.White, shape).border(1.8.dp, Color(0xFF94A3B8), shape)),
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

/** "2688" → "2.688" (dấu chấm ngăn cách nghìn, kiểu Việt Nam) — giống cách Trang chủ/Hồ sơ đang hiển thị xu. */
private fun formatXsmmCoins(v: Long): String {
    val s = v.toString()
    val sb = StringBuilder()
    for (i in s.indices) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(s[i])
    }
    return sb.toString()
}

/* ───────────────────────────── Preview (chỉ để xem bố cục) ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun XsmmAccountsScreenEmptyPreview() {
    XsmmAccountsScreen(
        state = XsmmAccountsUiState(
            username = "theanhz900",
            coinBalance = 2688,
            platformCounts = mapOf(XsmmPlatform.TikTok to 0, XsmmPlatform.Facebook to 0, XsmmPlatform.Instagram to 0),
        ),
        onBack = {}, onRefreshBalance = {}, onLogout = {}, onSelectPlatform = {}, onSelectType = {},
        onToggleSelectAll = {}, onToggleAccount = {}, onAddAccount = {}, onOpenConfig = {}, onRun = {},
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun XsmmAccountsScreenFacebookPreview() {
    XsmmAccountsScreen(
        state = XsmmAccountsUiState(
            username = "theanhz900",
            coinBalance = 2688,
            platformCounts = mapOf(XsmmPlatform.TikTok to 0, XsmmPlatform.Facebook to 0, XsmmPlatform.Instagram to 0),
            selectedPlatform = XsmmPlatform.Facebook,
        ),
        onBack = {}, onRefreshBalance = {}, onLogout = {}, onSelectPlatform = {}, onSelectType = {},
        onToggleSelectAll = {}, onToggleAccount = {}, onAddAccount = {}, onOpenConfig = {}, onRun = {},
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun XsmmAccountsScreenInstagramPreview() {
    XsmmAccountsScreen(
        state = XsmmAccountsUiState(
            username = "theanhz900",
            coinBalance = 2688,
            platformCounts = mapOf(XsmmPlatform.TikTok to 0, XsmmPlatform.Facebook to 0, XsmmPlatform.Instagram to 0),
            selectedPlatform = XsmmPlatform.Instagram,
        ),
        onBack = {}, onRefreshBalance = {}, onLogout = {}, onSelectPlatform = {}, onSelectType = {},
        onToggleSelectAll = {}, onToggleAccount = {}, onAddAccount = {}, onOpenConfig = {}, onRun = {},
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun XsmmAccountsScreenFilledPreview() {
    XsmmAccountsScreen(
        state = XsmmAccountsUiState(
            username = "theanhz900",
            coinBalance = 2688,
            platformCounts = mapOf(XsmmPlatform.TikTok to 2, XsmmPlatform.Facebook to 0, XsmmPlatform.Instagram to 0),
            selectedTypeId = "tiktok",
            accounts = listOf(
                XsmmAccount("1", "tiktoker_abc", "tiktok"),
                XsmmAccount("2", "tiktoker_xyz", "tiktok", statusLabel = "Lỗi đăng nhập", statusOk = false),
            ),
        ),
        onBack = {}, onRefreshBalance = {}, onLogout = {}, onSelectPlatform = {}, onSelectType = {},
        onToggleSelectAll = {}, onToggleAccount = {}, onAddAccount = {}, onOpenConfig = {}, onRun = {},
    )
}
