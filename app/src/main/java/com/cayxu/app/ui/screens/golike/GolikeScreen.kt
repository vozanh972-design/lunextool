package com.cayxu.app.ui.screens.golike

/*
 * MÀN "GOLIKE — CHƯA ĐĂNG NHẬP".
 *
 * Trạng thái rỗng khi mở mục Golike mà chưa đăng nhập: icon mũi tên trong vòng tròn xanh nhạt +
 * tiêu đề "Chưa đăng nhập Golike" + mô tả ngắn + 1 nút chính "Đăng nhập Golike" dẫn qua màn đăng
 * nhập Golike thật. CHỈ GIAO DIỆN — không có logic đăng nhập thật nào được viết ở đây; bạn tự nối
 * [onLogin] qua flow đăng nhập Golike thật của app.
 */

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/* ───────────────────────────── Composable chính ───────────────────────────── */

@Composable
fun GolikeScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        // Header: nút quay lại + tiêu đề, giống mọi màn con khác trong app.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PressBox(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Alx.White, CircleShape)
                    .border(1.dp, Alx.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(IconBack, 20.dp, Alx.Ink, 2f) }
            BasicText("Golike", style = type(size = 24f, weight = FontWeight.ExtraBold, color = Alx.Ink, spacing = -0.02f))
        }

        // Nội dung rỗng, canh giữa phần còn lại của màn hình.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(50))
                    .background(GolikeOrangeSoft),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(IconLoginArrow, 32.dp, GolikeOrange, 1.9f) }

            Spacer(Modifier.height(20.dp))
            BasicText(
                "Chưa đăng nhập Golike",
                style = type(size = 19f, weight = FontWeight.ExtraBold, color = Alx.Ink, align = TextAlign.Center),
            )

            Spacer(Modifier.height(6.dp))
            BasicText(
                "Đăng nhập để quét và quản lý tài khoản\nTikTok, Facebook, Instagram qua Golike.",
                style = type(size = 13.5f, weight = FontWeight.Medium, color = Alx.Muted, lineHeight = 1.5f, align = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(24.dp))
            LoginButton(
                label = "Đăng nhập Golike",
                onClick = onLogin,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

/* ───────────────────────────── Khối dùng chung trong file này ───────────────────────────── */
// GolikeOrange và GolikeOrangeSoft dùng chung từ GolikeAccountsScreen.kt trong cùng package

private val IconBack = listOf("M19 12H5M11 6l-6 6 6 6")
private val IconLoginArrow = listOf("M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-4M10 17l5-5-5-5M15 12H3")

/**
 * Nút chính có icon + nhãn, cùng phong cách PrimaryButton (pill cam,
 * đổ bóng, nhún nhẹ khi đè) nhưng thêm icon bên trái.
 */
@Composable
private fun LoginButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "golike-login-btn")
    Row(
        modifier = modifier
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(12.dp, shape, ambientColor = Color(0x4DF59E0B), spotColor = Color(0xB3F59E0B))
            .background(GolikeOrange, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SvgIcon(IconLoginArrow, 18.dp, Alx.White, 2f)
        Spacer(Modifier.width(8.dp))
        BasicText(label, style = type(size = 16f, weight = FontWeight.Bold, color = Alx.White))
    }
}

/** Nút bấm trong suốt, không hiệu ứng ripple — dùng cho nút tròn quay lại ở header. */
@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
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
private fun GolikeScreenPreview() {
    GolikeScreen(onBack = {}, onLogin = {})
}
