package com.cayxu.app.ui.screens.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState

// ── BỘ FONT MANROPE CHUẨN (BUNDLE TRỰC TIẾP TỪ RES/FONT) ──
val ManropeFontFamily = runCatching {
    FontFamily(
        Font(R.font.manrope, FontWeight.Normal),
        Font(R.font.manrope, FontWeight.Medium),
        Font(R.font.manrope, FontWeight.SemiBold),
        Font(R.font.manrope, FontWeight.Bold),
        Font(R.font.manrope, FontWeight.ExtraBold)
    )
}.getOrDefault(FontFamily.Default)

// ── BẢNG MÀU CHÍNH XÁC 100% TỪ LANGUAGE.DC.HTML ──
private val ColorPageBg = Color(0xFFF3F5F9)
private val ColorPrimaryBlue = Color(0xFF1F4BD8)
private val ColorTextMain = Color(0xFF0F172A)
private val ColorTextMuted = Color(0xFF64748B)
private val ColorTextDesc = Color(0xFF475569)
private val ColorTextDarkSlate = Color(0xFF334155)
private val ColorBorderLight = Color(0xFFE4E8F0)
private val ColorBorderActive = Color(0xFF1F4BD8)
private val ColorCardActiveBg = Color(0xFFF5F8FF)
private val ColorGlobeStroke = Color(0xFFB9CBF3)
private val ColorPreviewBoxBg = Color(0xFFE8EEFD)
private val ColorPreviewBoxBorder = Color(0xFFDCE5FA)
private val ColorPillBg = Color(0xFFF1F5F9)
private val ColorRadioUncheckedBorder = Color(0xFFCBD2DF)

/**
 * MÀN HÌNH CHỌN NGÔN NGỮ (LANGUAGE SELECTION SCREEN)
 * Khớp từng pixel với file Language.dc.html mới và ảnh mẫu thiết kế.
 */
@Composable
fun LanguageSelectionScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    // Mặc định chọn Tiếng Việt (VI)
    var selectedLang by remember { mutableStateOf(AppLanguage.VI) }
    val isVi = selectedLang == AppLanguage.VI

    // Hiệu ứng quỹ đạo vệ tinh của quả cầu
    val infiniteTransition = rememberInfiniteTransition(label = "globeOrbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorPageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── 1. BRAND HEADER (KHỚP 100% ẢNH MẪU: DUY NHẤT TEXT AUTOLUNEX) ──
                Text(
                    text = "AUTOLUNEX",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    letterSpacing = 1.6.sp,
                    color = ColorTextMain
                )

                Spacer(modifier = Modifier.height(22.dp))

                // ── 2. LIVE PREVIEW CONTAINER (KHUNG MINH HỌA QUẢ CẦU + THẺ TRẮNG NỔI) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(204.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(ColorPreviewBoxBg)
                        .border(1.dp, ColorPreviewBoxBorder, RoundedCornerShape(26.dp))
                ) {
                    // Quả cầu line-art SVG được vẽ chính xác từng nét
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val density = this.density
                        val strokePx = 1.4f * density
                        val stroke = Stroke(width = strokePx)

                        // Tâm quả cầu: cx = 292, cy = 108
                        val cx = 292f * density
                        val cy = 108f * density
                        val radius = 106f * density

                        // Circle r = 106
                        drawCircle(
                            color = ColorGlobeStroke,
                            radius = radius,
                            center = Offset(cx, cy),
                            style = stroke
                        )

                        // Ellipse rx = 10, ry = 106
                        drawOval(
                            color = ColorGlobeStroke,
                            topLeft = Offset(cx - (10f * density), cy - radius),
                            size = Size(20f * density, radius * 2),
                            style = stroke
                        )

                        // Ellipse rx = 54, ry = 106
                        drawOval(
                            color = ColorGlobeStroke,
                            topLeft = Offset(cx - (54f * density), cy - radius),
                            size = Size(108f * density, radius * 2),
                            style = stroke
                        )

                        // Ellipse rx = 100, ry = 106
                        drawOval(
                            color = ColorGlobeStroke,
                            topLeft = Offset(cx - (100f * density), cy - radius),
                            size = Size(200f * density, radius * 2),
                            style = stroke
                        )

                        // Đường ngang vĩ tuyến: M186 108h212
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(186f * density, 108f * density),
                            end = Offset((186f + 212f) * density, 108f * density),
                            strokeWidth = strokePx
                        )
                        // M204 60h176
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(204f * density, 60f * density),
                            end = Offset((204f + 176f) * density, 60f * density),
                            strokeWidth = strokePx
                        )
                        // M204 156h176
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(204f * density, 156f * density),
                            end = Offset((204f + 176f) * density, 156f * density),
                            strokeWidth = strokePx
                        )
                        // M226 26h132
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(226f * density, 26f * density),
                            end = Offset((226f + 132f) * density, 26f * density),
                            strokeWidth = strokePx
                        )
                        // M226 190h132
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(226f * density, 190f * density),
                            end = Offset((226f + 132f) * density, 190f * density),
                            strokeWidth = strokePx
                        )

                        // Vệ tinh xanh quỹ đạo (Orbiting Satellite Dot)
                        val rad = Math.toRadians(orbitAngle.toDouble())
                        val dotX = cx + (radius * Math.cos(rad)).toFloat()
                        val dotY = cy + (radius * Math.sin(rad)).toFloat()

                        drawCircle(
                            color = ColorPrimaryBlue.copy(alpha = 0.15f),
                            radius = 9f * density,
                            center = Offset(dotX, dotY)
                        )
                        drawCircle(
                            color = ColorPrimaryBlue,
                            radius = 4.5f * density,
                            center = Offset(dotX, dotY)
                        )
                    }

                    // THẺ TRẮNG NỔI (PREVIEW GIAO DIỆN)
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 20.dp)
                            .width(236.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = Color(0x330F172A),
                                spotColor = Color(0x4D0F172A)
                            )
                            .background(Color.White, RoundedCornerShape(20.dp))
                            .border(1.dp, ColorBorderLight, RoundedCornerShape(20.dp))
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
                    ) {
                        Column {
                            // Tag label
                            Text(
                                text = if (isVi) "NGÔN NGỮ GIAO DIỆN" else "INTERFACE LANGUAGE",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ManropeFontFamily,
                                letterSpacing = 1.05.sp,
                                color = ColorPrimaryBlue
                            )

                            // Greeting
                            Text(
                                text = if (isVi) "Xin chào" else "Hello",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ManropeFontFamily,
                                letterSpacing = (-0.9).sp,
                                lineHeight = 33.sp,
                                color = ColorTextMain,
                                modifier = Modifier.padding(top = 6.dp)
                            )

                            // Welcome subtitle
                            Text(
                                text = if (isVi) "Chào mừng đến với AUTOLUNEX" else "Welcome to AUTOLUNEX",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = ManropeFontFamily,
                                color = ColorTextMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            // 3 Tab Pills
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                PreviewPill(text = if (isVi) "Trang chủ" else "Home")
                                PreviewPill(text = if (isVi) "Nhiệm vụ" else "Tasks")
                                PreviewPill(text = if (isVi) "Tiện ích" else "Tools")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // ── 3. TIÊU ĐỀ & MÔ TẢ CHÍNH ──
                Text(
                    text = if (isVi) "Chọn ngôn ngữ" else "Choose language",
                    fontSize = 30.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    letterSpacing = (-0.9).sp,
                    color = ColorTextMain
                )

                Text(
                    text = if (isVi) 
                        "Vui lòng chọn ngôn ngữ hiển thị bạn muốn sử dụng." 
                    else 
                        "Please select the display language you want to use.",
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextDesc,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ── 4. DANH SÁCH 2 THẺ CHỌN NGÔN NGỮ (VI & EN) ──
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Thẻ Tiếng Việt
                    LanguageSelectionItem(
                        code = "VI",
                        title = "Tiếng Việt",
                        subtitle = "Giao diện Tiếng Việt",
                        isSelected = isVi,
                        onClick = {
                            selectedLang = AppLanguage.VI
                            LanguageState.setLanguage(context, AppLanguage.VI)
                        }
                    )

                    // Thẻ English
                    LanguageSelectionItem(
                        code = "EN",
                        title = "English",
                        subtitle = "English interface",
                        isSelected = !isVi,
                        onClick = {
                            selectedLang = AppLanguage.EN
                            LanguageState.setLanguage(context, AppLanguage.EN)
                        }
                    )
                }
            }

            // ── 5. ĐÁY MÀN HÌNH (GHI CHÚ HỒ SƠ + NÚT TIẾP TỤC) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isVi) 
                        "Bạn có thể đổi lại trong Hồ sơ › Cài đặt tài khoản" 
                    else 
                        "You can change it later in Profile › Account settings",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextMuted,
                    textAlign = TextAlign.Center
                )

                // Nút Tiếp tục (Chuyển sang màn nhập License Key)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = Color(0x661F4BD8),
                            spotColor = Color(0xB21F4BD8)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(ColorPrimaryBlue)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            LanguageState.setLanguage(context, selectedLang)
                            onFinished()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isVi) "Tiếp tục" else "Continue",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ManropeFontFamily
                    )
                }
            }
        }
    }
}

/**
 * Thẻ Tab nhỏ bên trong thẻ Preview (Trang chủ, Nhiệm vụ, Tiện ích).
 */
@Composable
private fun PreviewPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ColorPillBg)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ManropeFontFamily,
            color = ColorTextDarkSlate
        )
    }
}

/**
 * Thẻ chọn ngôn ngữ (VI hoặc EN) chuẩn số đo theo Language.dc.html.
 */
@Composable
private fun LanguageSelectionItem(
    code: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ColorBorderActive else ColorBorderLight,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ColorCardActiveBg else Color.White,
        animationSpec = tween(durationMillis = 200),
        label = "bgColor"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon Badge vuông bo góc (VI / EN)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (isSelected) ColorPrimaryBlue else ColorPreviewBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    letterSpacing = 0.3.sp,
                    color = if (isSelected) Color.White else ColorPrimaryBlue
                )
            }

            // Tên ngôn ngữ & Mô tả
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextMain
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextMuted
                )
            }

            // Radio Button Indicator (Dấu check tròn xanh hoặc vòng tròn viền xám)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ColorPrimaryBlue else Color.White)
                    .border(
                        width = if (isSelected) 0.dp else 1.5.dp,
                        color = if (isSelected) Color.Transparent else ColorRadioUncheckedBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        // SVG check: M5 12.5l4.5 4.5L19 7.5 (ViewBox 24x24)
                        val scaleX = size.width / 24f
                        val scaleY = size.height / 24f
                        val checkPath = Path().apply {
                            moveTo(5f * scaleX, 12.5f * scaleY)
                            lineTo(9.5f * scaleX, 17f * scaleY)
                            lineTo(19f * scaleX, 7.5f * scaleY)
                        }
                        drawPath(
                            path = checkPath,
                            color = Color.White,
                            style = Stroke(
                                width = 3.2f * scaleX,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}
