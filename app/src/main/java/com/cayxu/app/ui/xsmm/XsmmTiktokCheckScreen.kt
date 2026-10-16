package com.cayxu.app.ui.xsmm

/*

 * MÀN "KIỂM TRA TÀI KHOẢN" CỦA XSMM — NHÁNH TIKTOK.

 *

 * LƯU Ý QUAN TRỌNG: đây là màn GỐC của XSMM TikTok (yêu cầu quyền Hiển thị trên ứng dụng khác +

 * Dịch vụ Trợ năng, rồi chọn app TikTok/TikTok Lite/TikTok Studio để mở và kiểm tra @username).

 * Màn "Golike: kiểm tra tài khoản" trong app hiện tại chỉ là bản COPY lại gần như y nguyên từ

 * chính màn XSMM TikTok này (cùng bố cục, cùng 2 dòng quyền, cùng lưới chọn app) — nên file này

 * code cho XSMM TikTok, không phải cho Golike. Nếu cần dùng lại cho Golike thì copy/wiring logic

 * tương ứng bên Golike, không chỉnh sửa gì ở đây.

 *

 * CHỈ THAY GIAO DIỆN — KHÔNG ĐỤNG LOGIC BÊN TRONG. Toàn bộ hành vi thật (xin quyền overlay, xin

 * quyền Trợ năng, mở app đã chọn, tự động bấm tab "Tôi", đọc @username, lưu tài khoản...) vẫn do

 * bạn tự nối qua [XsmmTiktokCheckUiState] và các callback bên dưới — không có logic nghiệp vụ nào

 * được viết sẵn ở đây.

 */

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

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

import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.BasicText

import androidx.compose.runtime.Composable

import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.alpha

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.unit.dp

import com.cayxu.app.ui.design.Alx

import com.cayxu.app.ui.design.PrimaryButton

import com.cayxu.app.ui.design.SvgIcon

import com.cayxu.app.ui.design.type

/* ───────────────────────────── Trạng thái ───────────────────────────── */

/** Các app TikTok có thể chọn để kiểm tra. [installed] chỉ phản ánh app đã cài trên máy hay chưa — do logic thật xác định. */

enum class TiktokAppVariant(val title: String) {

    TikTok("TikTok"),

    TikTokLite("TikTok Lite"),

    TikTokStudio("TikTok Studio"),

}

data class XsmmTiktokCheckUiState(

    /** Quyền "Hiển thị trên ứng dụng khác" — ở thiết kế gốc luôn hiện sẵn "Đã cấp", nhưng vẫn để true/false cho đúng thực tế thiết bị. */

    val overlayGranted: Boolean = true,

    /** Quyền Dịch vụ Trợ năng — bắt buộc phải bật mới cho bấm "Kiểm tra tài khoản". */

    val accessibilityGranted: Boolean = false,

    val selectedApp: TiktokAppVariant = TiktokAppVariant.TikTok,

    val installedApps: Set<TiktokAppVariant> = setOf(TiktokAppVariant.TikTok),

)

/* ───────────────────────────── Composable chính ───────────────────────────── */

@Composable

fun XsmmTiktokCheckScreen(

    state: XsmmTiktokCheckUiState,

    onDismiss: () -> Unit,

    onGrantOverlay: () -> Unit,

    onGrantAccessibility: () -> Unit,

    onSelectApp: (TiktokAppVariant) -> Unit,

    onCheckAccount: () -> Unit,

    modifier: Modifier = Modifier,

) {

    Box(modifier = modifier.fillMaxSize()) {

        // Nền mờ — chạm để đóng, quay lại màn trước đó.

        Box(

            modifier = Modifier

                .fillMaxSize()

                .background(Color(0x730F172A))

                .clickable(

                    interactionSource = remember { MutableInteractionSource() },

                    indication = null,

                    onClick = onDismiss,

                )

        )

        Column(

            modifier = Modifier

                .align(Alignment.BottomCenter)

                .fillMaxWidth()

                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))

                .background(Alx.White)

                .navigationBarsPadding()

                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp)

        ) {

            Box(

                modifier = Modifier

                    .align(Alignment.CenterHorizontally)

                    .size(width = 40.dp, height = 4.dp)

                    .clip(RoundedCornerShape(2.dp))

                    .background(Alx.InputBorder)

            )

            Spacer(Modifier.height(18.dp))

            BasicText("Kiểm tra tài khoản TikTok", style = type(size = 20f, weight = FontWeight.ExtraBold, color = Alx.Ink))

            Spacer(Modifier.height(6.dp))

            BasicText(

                "Màn hình nổi và Trợ năng cần được cấp quyền để tự động mở TikTok và kiểm tra trạng thái nick.",

                style = type(size = 13.5f, weight = FontWeight.Medium, color = Alx.Muted, lineHeight = 1.45f),

            )

            Spacer(Modifier.height(16.dp))

            PermissionRow(

                iconPaths = IconOverlayWindow,

                title = "Hiển thị trên ứng dụng khác",

                subtitle = "Để hiện màn nổi kiểm tra và điều khiển trên TikTok",

                granted = state.overlayGranted,

                grantedLabel = "Đã cấp",

                requestLabel = "Cấp quyền",

                onGrant = onGrantOverlay,

            )

            Spacer(Modifier.height(10.dp))

            PermissionRow(

                iconPaths = IconAccessibility,

                title = "Dịch vụ Trợ năng",

                subtitle = "Để tự động bấm tab \"Tôi\" và kiểm tra @username TikTok",

                granted = state.accessibilityGranted,

                grantedLabel = "Đã cấp",

                requestLabel = "Cấp quyền",

                onGrant = onGrantAccessibility,

            )

            Spacer(Modifier.height(20.dp))

            BasicText(

                "CHỌN ỨNG DỤNG CẦN KIỂM TRA",

                style = type(size = 11f, weight = FontWeight.ExtraBold, color = Alx.Muted, spacing = 0.12f),

            )

            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(

                columns = GridCells.Fixed(3),

                horizontalArrangement = Arrangement.spacedBy(8.dp),

                verticalArrangement = Arrangement.spacedBy(8.dp),

                modifier = Modifier.height(64.dp),

            ) {

                items(TiktokAppVariant.entries) { app ->

                    AppVariantCard(

                        title = app.title,

                        installed = state.installedApps.contains(app),

                        selected = state.selectedApp == app,

                        onClick = { onSelectApp(app) },

                    )

                }

            }

            Spacer(Modifier.height(18.dp))

            val checkEnabled = state.accessibilityGranted

            PrimaryButton(

                label = "Kiểm tra tài khoản",

                onClick = onCheckAccount,

                enabled = checkEnabled,

            )

            if (!checkEnabled) {

                Spacer(Modifier.height(8.dp))

                BasicText(

                    "Cấp quyền Trợ năng để tiếp tục",

                    style = type(size = 12f, weight = FontWeight.Medium, color = Alx.Muted),

                    modifier = Modifier.align(Alignment.CenterHorizontally),

                )

            }

        }

    }

}

/* ───────────────────────────── Thành phần con ───────────────────────────── */

@Composable

private fun PermissionRow(

    iconPaths: List<String>,

    title: String,

    subtitle: String,

    granted: Boolean,

    grantedLabel: String,

    requestLabel: String,

    onGrant: () -> Unit,

) {

    Row(

        modifier = Modifier

            .fillMaxWidth()

            .clip(RoundedCornerShape(18.dp))

            .background(Alx.Bg)

            .padding(horizontal = 14.dp, vertical = 12.dp),

        verticalAlignment = Alignment.CenterVertically,

    ) {

        Box(

            modifier = Modifier

                .size(40.dp)

                .clip(RoundedCornerShape(12.dp))

                .background(Alx.BlueSoft),

            contentAlignment = Alignment.Center,

        ) { SvgIcon(paths = iconPaths, size = 19.dp, color = Alx.Blue, strokeWidth = 1.8f) }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            BasicText(title, style = type(size = 14.5f, weight = FontWeight.Bold, color = Alx.Ink))

            Spacer(Modifier.height(2.dp))

            BasicText(subtitle, style = type(size = 12f, weight = FontWeight.Medium, color = Alx.Muted, lineHeight = 1.4f))

        }

        Spacer(Modifier.width(12.dp))

        if (granted) {

            Row(

                modifier = Modifier

                    .height(30.dp)

                    .clip(RoundedCornerShape(50))

                    .background(Alx.BlueSoft)

                    .padding(horizontal = 11.dp),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.spacedBy(4.dp),

            ) {

                SvgIcon(paths = listOf(IconCheckMark), size = 13.dp, color = Alx.Blue, strokeWidth = 3f)

                BasicText(grantedLabel, style = type(size = 12.5f, weight = FontWeight.ExtraBold, color = Alx.Blue))

            }

        } else {

            PressBox(onClick = onGrant) { _ ->

                Box(

                    modifier = Modifier

                        .height(36.dp)

                        .clip(RoundedCornerShape(12.dp))

                        .background(Alx.Blue)

                        .padding(horizontal = 14.dp),

                    contentAlignment = Alignment.Center,

                ) {

                    BasicText(requestLabel, style = type(size = 13f, weight = FontWeight.Bold, color = Alx.White))

                }

            }

        }

    }

}

@Composable

private fun AppVariantCard(

    title: String,

    installed: Boolean,

    selected: Boolean,

    onClick: () -> Unit,

) {

    PressBox(onClick = onClick, enabled = installed) { pressed ->

        Box(

            modifier = Modifier

                .fillMaxWidth()

                .height(64.dp)

                .clip(RoundedCornerShape(18.dp))

                .background(if (selected) Alx.Ink else Alx.White)

                .then(

                    if (selected) Modifier else Modifier.border(1.dp, Alx.Line, RoundedCornerShape(18.dp))

                ),

            contentAlignment = Alignment.Center,

        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                BasicText(

                    title,

                    style = type(

                        size = 14f,

                        weight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,

                        color = if (selected) Alx.White else Alx.Ink2,

                        align = TextAlign.Center,

                    ),

                )

                Spacer(Modifier.height(2.dp))

                BasicText(

                    if (installed) "Đã cài" else "Chưa cài",

                    style = type(

                        size = 11.5f,

                        weight = FontWeight.SemiBold,

                        color = if (selected) Color(0xFFB6C0D2) else Alx.Muted,

                    ),

                )

            }

        }

    }

}

/** Nút bấm có hiệu ứng scale-down nhẹ khi đè — dùng chung kiểu press-animation như các màn khác trong app. */

@Composable

private fun PressBox(

    onClick: () -> Unit,

    enabled: Boolean = true,

    content: @Composable (pressed: Boolean) -> Unit,

) {

    val interaction = remember { MutableInteractionSource() }

    val pressed by interaction.collectIsPressedAsState()

    Box(

        modifier = Modifier

            .clickable(

                interactionSource = interaction,

                indication = null,

                enabled = enabled,

                onClick = onClick,

            )

            .alpha(if (enabled) 1f else .45f)

    ) { content(pressed) }

}

private val IconOverlayWindow = listOf("M4 4h12v12H4z", "M8 20h12V8")

private val IconAccessibility = listOf(

    "M12 12m-9 0a9 9 0 1018 0 9 9 0 10-18 0",

    "M12 7.5h.01",

    "M8 10.5l4 .8 4-.8",

    "M12 11.3V15",

    "M10 19l2-4 2 4",

)

private const val IconCheckMark = "M5 12.5l4.5 4.5L19 7.5"

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(showBackground = true, widthDp = 390, heightDp = 844)

@Composable

private fun XsmmTiktokCheckScreenPreview() {

    var ui by remember {

        mutableStateOf(

            XsmmTiktokCheckUiState(

                overlayGranted = true,

                accessibilityGranted = false,

                selectedApp = TiktokAppVariant.TikTok,

                installedApps = setOf(TiktokAppVariant.TikTok),

            )

        )

    }

    XsmmTiktokCheckScreen(

        state = ui,

        onDismiss = {},

        onGrantOverlay = { ui = ui.copy(overlayGranted = true) },

        onGrantAccessibility = { ui = ui.copy(accessibilityGranted = true) },

        onSelectApp = { app -> if (ui.installedApps.contains(app)) ui = ui.copy(selectedApp = app) },

        onCheckAccount = {},

    )

}
