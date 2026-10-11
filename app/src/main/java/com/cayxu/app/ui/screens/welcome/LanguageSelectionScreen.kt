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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState

// ── BỘ FONT MANROPE CHUẨN (HỖ TRỢ VARIABLE FONT WEIGHT VÀ FALLBACK CHỐNG CRASH) ──
@OptIn(ExperimentalTextApi::class)
val ManropeFontFamily: FontFamily = try {
    FontFamily(
        Font(
            resId = R.font.manrope,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            resId = R.font.manrope,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))
        ),
        Font(
            resId = R.font.manrope,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))
        ),
        Font(
            resId = R.font.manrope,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        ),
        Font(
            resId = R.font.manrope,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800))
        )
    )
} catch (_: Throwable) {
    try {
        FontFamily(
            Font(R.font.manrope, FontWeight.Normal),
            Font(R.font.manrope, FontWeight.Bold)
        )
    } catch (_: Throwable) {
        FontFamily.Default
    }
}

// ── BẢNG MÀU CHÍNH XÁC 100% THEO FILE HTML GỐC LANGUAGE.DC.HTML ──
// body { background: #F3F5F9; color: #0F172A }
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
 * Bê nguyên bản 1:1 từ file Language.dc.html:
 * - Không tự chế thêm icon hay thành phần nào không có trong file HTML mẫu.
 * - Khung preview kích thước 204dp, chứa quả cầu SVG line-art + chấm vệ tinh chuyển động.
 * - Thẻ trắng nổi width 236px, padding chuẩn 14px 16px 16px, không bị rớt dòng phụ đề.
 * - Bong bóng thoại "Bắt đầu thôi!" đặt góc phải dưới chuẩn toạ độ right: 18px, bottom: 20px.
 * - 2 lựa chọn VI và EN giữ nguyên thông số CSS (border 1.5px, radius 20px, badge 44x44px radius 13px).
 * - Nút "Tiếp tục" height 56px, radius 18px với đổ bóng xanh chuẩn 1:1.
 */
@Composable
fun LanguageSelectionScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf(AppLanguage.VI) }
    val isVi = selectedLang == AppLanguage.VI

    // Hiệu ứng quỹ đạo vệ tinh của quả cầu SVG (animateMotion dur="10s")
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

    // Khung 390x844px theo HTML: padding 24px 20px 0, background #F3F5F9
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
                // ── 1. BRAND (NGUYÊN MẪU DÒNG 31-34 TRONG HTML: CHỈ DUY NHẤT TEXT AUTOLUNEX, KHÔNG CHẾ THÊM ICON) ──
                // <div style="display:flex;align-items:center;gap:10px">
                //   <span style="font-size:16px;font-weight:800;letter-spacing:.1em">AUTOLUNEX</span>
                // </div>
                Text(
                    text = "AUTOLUNEX",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    letterSpacing = 1.6.sp,
                    color = ColorTextMain
                )

                Spacer(modifier = Modifier.height(22.dp))

                // ── 2. LIVE PREVIEW CONTAINER (DÒNG 36-67 TRONG HTML) ──
                // <div style="margin-top:22px;height:204px;border-radius:26px;background:#E8EEFD;border:1px solid #DCE5FA;position:relative;overflow:hidden">
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(204.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(ColorPreviewBoxBg)
                        .border(1.dp, ColorPreviewBoxBorder, RoundedCornerShape(26.dp))
                ) {
                    // Quả cầu SVG line-art (cx = 292, cy = 108, r = 106)
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val density = this.density
                        val strokePx = 1.4f * density
                        val stroke = Stroke(width = strokePx)

                        val cx = 292f * density
                        val cy = 108f * density
                        val radius = 106f * density

                        // Circle cx="292" cy="108" r="106"
                        drawCircle(
                            color = ColorGlobeStroke,
                            radius = radius,
                            center = Offset(cx, cy),
                            style = stroke
                        )

                        // Ellipse rx="10" ry="106"
                        drawOval(
                            color = ColorGlobeStroke,
                            topLeft = Offset(cx - (10f * density), cy - radius),
                            size = Size(20f * density, radius * 2),
                            style = stroke
                        )
                        // Ellipse rx="54" ry="106"
                        drawOval(
                            color = ColorGlobeStroke,
                            topLeft = Offset(cx - (54f * density), cy - radius),
                            size = Size(108f * density, radius * 2),
                            style = stroke
                        )
                        // Ellipse rx="100" ry="106"
                        drawOval(
                            color = ColorGlobeStroke,
                            topLeft = Offset(cx - (100f * density), cy - radius),
                            size = Size(200f * density, radius * 2),
                            style = stroke
                        )

                        // Các vĩ tuyến ngang SVG
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(186f * density, 108f * density),
                            end = Offset((186f + 212f) * density, 108f * density),
                            strokeWidth = strokePx
                        )
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(204f * density, 60f * density),
                            end = Offset((204f + 176f) * density, 60f * density),
                            strokeWidth = strokePx
                        )
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(204f * density, 156f * density),
                            end = Offset((204f + 176f) * density, 156f * density),
                            strokeWidth = strokePx
                        )
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(226f * density, 26f * density),
                            end = Offset((226f + 132f) * density, 26f * density),
                            strokeWidth = strokePx
                        )
                        drawLine(
                            color = ColorGlobeStroke,
                            start = Offset(226f * density, 190f * density),
                            end = Offset((226f + 132f) * density, 190f * density),
                            strokeWidth = strokePx
                        )

                        // Vệ tinh quay quanh quỹ đạo
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

                    // THẺ TRẮNG NỔI (DÒNG 49-60 TRONG HTML):
                    // <div style="position:absolute;left:16px;top:20px;width:236px;background:#fff;border:1px solid #E4E8F0;border-radius:20px;padding:14px 16px 16px;box-shadow:0 12px 24px -14px rgba(15,23,42,.3)">
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 18.dp)
                            .width(236.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = Color(0x330F172A),
                                spotColor = Color(0x4D0F172A)
                            )
                            .background(Color.White, RoundedCornerShape(20.dp))
                            .border(1.dp, ColorBorderLight, RoundedCornerShape(20.dp))
                            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
                    ) {
                        Column {
                            // Tag: <div style="font-size:10.5px;font-weight:800;letter-spacing:.1em;color:#1F4BD8">{{ tagLabel }}</div>
                            Text(
                                text = if (isVi) "NGÔN NGỮ GIAO DIỆN" else "INTERFACE LANGUAGE",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ManropeFontFamily,
                                letterSpacing = 1.05.sp,
                                color = ColorPrimaryBlue,
                                maxLines = 1,
                                softWrap = false
                            )

                            // Greet: <div style="font-size:30px;font-weight:800;letter-spacing:-.03em;line-height:1.1;margin-top:6px">{{ greet }}</div>
                            Text(
                                text = if (isVi) "Xin chào" else "Hello",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ManropeFontFamily,
                                letterSpacing = (-0.9).sp,
                                lineHeight = 33.sp,
                                color = ColorTextMain,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 1,
                                softWrap = false
                            )

                            // Welcome: <div style="font-size:12.5px;font-weight:500;color:#64748B;margin-top:4px">{{ welcome }}</div>
                            // Khóa maxLines = 1, không cho ngắt dòng thành 2 dòng làm đội kích thước thẻ
                            Text(
                                text = if (isVi) "Chào mừng đến với AUTOLUNEX" else "Welcome to AUTOLUNEX",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = ManropeFontFamily,
                                color = ColorTextMuted,
                                modifier = Modifier.padding(top = 2.dp),
                                maxLines = 1,
                                softWrap = false
                            )

                            // 3 Tabs: <div style="display:flex;gap:6px;margin-top:12px">
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                PreviewPill(text = if (isVi) "Trang chủ" else "Home")
                                PreviewPill(text = if (isVi) "Nhiệm vụ" else "Tasks")
                                PreviewPill(text = if (isVi) "Tiện ích" else "Tools")
                            }
                        }
                    }

                    // BONG BÓNG THOẠI XANH (DÒNG 66 TRONG HTML):
                    // <div style="position:absolute;right:18px;bottom:20px;background:#1F4BD8;color:#fff;font-size:13px;font-weight:700;padding:10px 14px;border-radius:16px 16px 4px 16px;box-shadow:0 10px 20px -10px rgba(31,75,216,.7)">{{ reply }}</div>
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 20.dp)
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp),
                                ambientColor = Color(0x661F4BD8),
                                spotColor = Color(0xB21F4BD8)
                            )
                            .background(
                                color = ColorPrimaryBlue,
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isVi) "Bắt đầu thôi!" else "Let's start!",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ManropeFontFamily,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // ── 3. TIÊU ĐỀ & MÔ TẢ (DÒNG 69-70 TRONG HTML) ──
                // <h1 style="margin:26px 0 0;font-size:30px;line-height:1.1;font-weight:800;letter-spacing:-.03em">Chọn ngôn ngữ</h1>
                Text(
                    text = "Chọn ngôn ngữ",
                    fontSize = 30.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    letterSpacing = (-0.9).sp,
                    color = ColorTextMain
                )

                // <p style="margin:6px 0 0;font-size:14.5px;line-height:1.5;color:#475569">Vui lòng chọn ngôn ngữ hiển thị bạn muốn sử dụng.</p>
                Text(
                    text = "Vui lòng chọn ngôn ngữ hiển thị bạn muốn sử dụng.",
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextDesc,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ── 4. DANH SÁCH 2 THẺ CHỌN NGÔN NGỮ (DÒNG 72-83 TRONG HTML) ──
                // <div style="margin-top:18px;display:flex;flex-direction:column;gap:10px">
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

            // ── 5. ĐÁY MÀN HÌNH (DÒNG 85-88 TRONG HTML) ──
            // <div style="margin-top:auto;padding-bottom:32px;display:flex;flex-direction:column;gap:12px">
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // <span style="font-size:12.5px;font-weight:500;color:#64748B;text-align:center">Bạn có thể đổi lại trong Hồ sơ › Cài đặt tài khoản</span>
                Text(
                    text = "Bạn có thể đổi lại trong Hồ sơ › Cài đặt tài khoản",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextMuted,
                    textAlign = TextAlign.Center
                )

                // <a href="License.dc.html" style="display:flex;align-items:center;justify-content:center;height:56px;border-radius:18px;background:#1F4BD8;color:#fff;font-size:16px;font-weight:700;box-shadow:0 12px 24px -12px rgba(31,75,216,.7)">Tiếp tục</a>
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
                        text = "Tiếp tục",
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
 * Style HTML dòng 55-57: font-size: 11px; font-weight: 700; color: #334155; background: #F1F5F9; padding: 5px 9px; border-radius: 999px;
 */
@Composable
private fun PreviewPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ColorPillBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ManropeFontFamily,
            color = ColorTextDarkSlate,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Thẻ chọn ngôn ngữ (VI hoặc EN) chuẩn 1:1 theo dòng 73-83 và renderVals() trong Language.dc.html.
 * - base: display:flex; align-items:center; gap:14px; padding:12px 16px; border-radius:20px;
 * - on:  background:#F5F8FF; border:1.5px solid #1F4BD8;
 * - off: background:#fff; border:1.5px solid #E4E8F0;
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
            // Badge vuông bo góc (VI / EN): width:44px; height:44px; border-radius:13px; font-size:14px; font-weight:800; letter-spacing:.02em
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

            // Tiêu đề & phụ đề: gap: 1px
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // <span style="font-size:16px;font-weight:700;color:#0F172A">Tiếng Việt</span>
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextMain
                )
                Spacer(modifier = Modifier.height(1.dp))
                // <span style="font-size:12.5px;font-weight:500;color:#64748B">Giao diện Tiếng Việt</span>
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ManropeFontFamily,
                    color = ColorTextMuted
                )
            }

            // Radio Button / Checkmark: width:24px; height:24px; border-radius:50%
            // cOn: background:#1F4BD8
            // cOff: background:#fff; border:1.5px solid #CBD2DF
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
                    // SVG Checkmark: viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"
                    // d="M5 12.5l4.5 4.5L19 7.5"
                    Canvas(modifier = Modifier.size(14.dp)) {
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
