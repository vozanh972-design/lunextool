package com.cayxu.app.ui.xsmm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/*
 * TẤM "CHI TIẾT TÀI KHOẢN" CỦA XSMM (Facebook) — CHỈ LÀ LỚP GIAO DIỆN, không đụng logic.
 * Dựng đúng theo canvas XsmmAccountDetail.dc.html: tấm trượt lên từ dưới (không phải toàn màn hình,
 * giống GolikeTaskConfigSheet.kt), gồm ảnh bìa gradient xanh + nút "Đổi bìa", avatar đè lên mép bìa +
 * nút đổi ảnh, tên + pill trạng thái, rồi khối "Chi tiết tài khoản" với các field có thể ẩn/hiện +
 * sao chép, cuối cùng là nút "Đóng".
 *
 * Không gọi mạng, không tự đổi ảnh bìa/avatar, không tự sao chép vào clipboard — mọi giá trị hiển thị
 * (kể cả trạng thái ẩn/hiện từng field) truyền qua [XsmmAccountDetailUiState], mọi thao tác chỉ báo ra
 * ngoài qua callback, bạn tự nối vào logic/ViewModel XSMM thật đang có.
 *
 * Avatar dùng ẢNH thật qua Coil (AsyncImage) — cần dependency `io.coil-kt:coil-compose` nếu project
 * chưa có (đã dùng ở CrossTTCAccountsScreen.kt).
 */

private val XsmmBlue = Color(0xFF1F4BD8)

private val Camera = listOf("M4 7h3l2-3h6l2 3h3a1 1 0 011 1v11a1 1 0 01-1 1H4a1 1 0 01-1-1V8a1 1 0 011-1z")
private val Eye = listOf("M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z")
private val EyeOff = listOf("M17.9 17.9A10.4 10.4 0 0112 19.5c-7 0-11-7.5-11-7.5a19.3 19.3 0 015.1-5.7M9.9 4.6A10.6 10.6 0 0112 4.5c7 0 11 7.5 11 7.5a19.4 19.4 0 01-3.2 4.3M14.1 14.1a3 3 0 10-4.2-4.2", "M1 1l22 22")

/* ───────────────────────────── Trạng thái ───────────────────────────── */

/** Một field chi tiết tài khoản — field "dài" (Cookie, Access Token…) bọc trong thẻ nền xám, field thường (UID, Email) để trần. */
data class XsmmAccountDetailField(
    val id: String,
    val label: String,
    val maskedValue: String,
    val revealedValue: String,
    val revealed: Boolean = false,
    val boxed: Boolean = false,
)

data class XsmmAccountDetailUiState(
    val coverPhotoUrl: String? = null,     // null = dùng gradient xanh mặc định như canvas
    val avatarUrl: String,
    val name: String,
    val statusLabel: String,               // vd "Die / Checkpoint", "Live"
    val statusColor: Color = Color(0xFFC9363B),
    val statusBg: Color = Color(0xFFFDECEC),
    val fields: List<XsmmAccountDetailField> = emptyList(),
    val hasPage: Boolean = false,
    val pageSummary: String = "",          // chỉ hiện khi hasPage = true, vd "2 page/profile liên kết"
)

/* ───────────────────────────── Tấm chi tiết ───────────────────────────── */

/**
 * Mở bằng [Dialog] toàn màn hình (giống GolikeTaskConfigSheet) nên tự vẽ nền mờ + tấm trượt lên.
 * Gọi `XsmmAccountDetailSheet(state = ..., onDismiss = ...)` khi bấm vào một dòng tài khoản Facebook
 * ở `XsmmAccountsScreen`.
 */
@Composable
fun XsmmAccountDetailSheet(
    state: XsmmAccountDetailUiState,
    onDismiss: () -> Unit,
    onChangeCover: () -> Unit,
    onChangeAvatar: () -> Unit,
    onToggleFieldReveal: (XsmmAccountDetailField) -> Unit,
    onCopyField: (XsmmAccountDetailField) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "xsmmDetailScrim")
        val noRipple = remember { MutableInteractionSource() }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color(0x800F172A))
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
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 40.dp, height = 4.dp).background(Color(0xFFD5DBE6), RoundedCornerShape(2.dp)))

                    CoverPhoto(state.coverPhotoUrl, onChangeCover, Modifier.offset(y = (-12).dp))

                    Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                        AvatarOverlap(state.avatarUrl, onChangeAvatar, Modifier.offset(y = (-38).dp))

                        BasicText(
                            state.name,
                            Modifier.offset(y = (-24).dp),
                            style = type(20f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.offset(y = (-17).dp).background(state.statusBg, RoundedCornerShape(999.dp))
                                .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(6.dp).background(state.statusColor, CircleShape))
                            BasicText(state.statusLabel, style = type(12f, FontWeight.ExtraBold, state.statusColor))
                        }

                        Box(Modifier.offset(y = (-10).dp).fillMaxWidth().height(1.dp).background(Color(0xFFEEF1F6)))

                        BasicText("Chi tiết tài khoản", Modifier.offset(y = (-4).dp), style = type(15f, FontWeight.ExtraBold, Alx.Ink))

                        state.fields.forEach { f ->
                            if (f.boxed) {
                                BoxedField(f, onToggleFieldReveal, onCopyField, Modifier.padding(top = 16.dp))
                            } else {
                                PlainField(f, onToggleFieldReveal, onCopyField, Modifier.padding(top = 16.dp))
                            }
                        }

                        BasicText(
                            if (state.hasPage) state.pageSummary else "Tài khoản không có Page",
                            Modifier.padding(top = 16.dp),
                            style = type(12.5f, FontWeight.Medium, Alx.Muted),
                        )

                        PressBox(
                            onClick = onDismiss,
                            modifier = Modifier.padding(top = 22.dp).fillMaxWidth().height(56.dp)
                                .background(XsmmBlue, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) { BasicText("Đóng", style = type(16f, FontWeight.Bold, Alx.White)) }
                    }
                }
            }
        }
    }
}

/* ───────────────────────────── Thành phần dùng trong tấm ───────────────────────────── */

@Composable
private fun CoverPhoto(photoUrl: String?, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(
        modifier.fillMaxWidth().height(150.dp).clip(shape)
            .then(
                if (photoUrl != null) Modifier else Modifier.background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4F86FF), Color(0xFF1F4BD8), Color(0xFF1239A8)),
                        start = Offset(0f, 0f), end = Offset(1000f, 1000f),
                    ),
                ),
            ),
    ) {
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        PressBox(
            onClick = onChange,
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).height(32.dp)
                .background(Color(0x8C0F172A), RoundedCornerShape(999.dp))
                .padding(start = 10.dp, end = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SvgIcon(Camera, 14.dp, Alx.White, 2.2f)
                BasicText("Đổi bìa", style = type(12f, FontWeight.Bold, Alx.White))
            }
        }
    }
}

@Composable
private fun AvatarOverlap(avatarUrl: String, onChange: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.width(84.dp)) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(84.dp).clip(CircleShape).border(4.dp, Alx.White, CircleShape).background(Alx.BlueSoft),
        )
        PressBox(
            onClick = onChange,
            modifier = Modifier.align(Alignment.BottomEnd).size(30.dp)
                .background(XsmmBlue, CircleShape).border(3.dp, Alx.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Camera, 13.dp, Alx.White, 2.4f) }
    }
}

@Composable
private fun PlainField(
    field: XsmmAccountDetailField,
    onToggle: (XsmmAccountDetailField) -> Unit,
    onCopy: (XsmmAccountDetailField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BasicText(field.label, style = type(12.5f, FontWeight.Bold, Alx.Muted))
        Row(Modifier.padding(top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                if (field.revealed) field.revealedValue else field.maskedValue,
                Modifier.weight(1f),
                style = type(14.5f, FontWeight.Bold, Alx.Ink, spacing = 0.04f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            RevealButton(field, onToggle, Alx.Bg)
            CopyButton(field, onCopy, Alx.Bg)
        }
    }
}

@Composable
private fun BoxedField(
    field: XsmmAccountDetailField,
    onToggle: (XsmmAccountDetailField) -> Unit,
    onCopy: (XsmmAccountDetailField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().background(Color(0xFFF7F9FC), shape).border(1.dp, Alx.Line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicText(field.label, style = type(12.5f, FontWeight.Bold, Alx.Muted))
        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                if (field.revealed) field.revealedValue else field.maskedValue,
                Modifier.weight(1f),
                style = type(13.5f, FontWeight.Bold, Alx.Ink, spacing = 0.03f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            RevealButton(field, onToggle, Alx.White)
            CopyButton(field, onCopy, Alx.White)
        }
    }
}

@Composable
private fun RevealButton(field: XsmmAccountDetailField, onToggle: (XsmmAccountDetailField) -> Unit, bg: Color) {
    PressBox(
        onClick = { onToggle(field) },
        modifier = Modifier.size(32.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) { SvgIcon(if (field.revealed) Eye else EyeOff, 16.dp, Color(0xFF475569), 2f) }
}

private val CopyIconPaths = listOf("M9 9h12v12H9z", "M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1")

@Composable
private fun CopyButton(field: XsmmAccountDetailField, onCopy: (XsmmAccountDetailField) -> Unit, bg: Color) {
    PressBox(
        onClick = { onCopy(field) },
        modifier = Modifier.size(32.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) { SvgIcon(CopyIconPaths, 15.dp, XsmmBlue, 2f) }
}

@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content() }
}

/* ───────────────────────────── Dữ liệu mẫu + Preview ───────────────────────────── */

fun sampleXsmmAccountDetailFields() = listOf(
    XsmmAccountDetailField("uid", "UID Facebook", "••••••••••••", "61592384910…", boxed = false),
    XsmmAccountDetailField("email", "Email", "••••••••••••", "jong.tran83@gmail.com", boxed = false),
    XsmmAccountDetailField("cookie", "Cookie", "••••••••••••••••", "c_user=61592384910…; xs=47%3Aab12Qp…", boxed = true),
    XsmmAccountDetailField("token", "Access Token", "••••••••••••••••", "EAAG9m0pQ2vYBA...kZDZD8f91A2b", boxed = true),
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun XsmmAccountDetailSheetPreview() {
    var fields by remember { mutableStateOf(sampleXsmmAccountDetailFields()) }
    XsmmAccountDetailSheet(
        state = XsmmAccountDetailUiState(
            avatarUrl = "https://i.pravatar.cc/120?img=25",
            name = "Jong Tran",
            statusLabel = "Die / Checkpoint",
            fields = fields,
            hasPage = false,
        ),
        onDismiss = {},
        onChangeCover = {},
        onChangeAvatar = {},
        onToggleFieldReveal = { f -> fields = fields.map { if (it.id == f.id) it.copy(revealed = !it.revealed) else it } },
        onCopyField = {},
    )
}
