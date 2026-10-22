package com.cayxu.app.ui.update

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/*
 * MÀN "CẬP NHẬT HỆ THỐNG" — mở từ thẻ "Có bản cập nhật mới" ở Hồ sơ (xem ProfileScreen.kt,
 * ProfileAction.OpenUpdate). CHỈ GIAO DIỆN: không tự kiểm tra bản mới, không tự tải, không tự cài —
 * nội dung (tên bản, dung lượng, build, danh sách thay đổi) truyền hết qua [SystemUpdateUiState],
 * bấm "Cài đặt ngay" chỉ gọi [onInstallNow], bạn tự nối vào logic tải/cài thật.
 */

data class UpdateNote(val title: String, val body: String)

data class SystemUpdateUiState(
    val appName: String = "Autolunex",
    val versionLabel: String = "",     // vd "Phiên bản 1.0.6 · 18 MB"
    val buildLabel: String = "",       // vd "Build ALX_1.0.6.V7"
    val notes: List<UpdateNote> = emptyList(),
)

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val UpdateIcon = listOf("M12 3v12M7 10l5 5 5-5", "M5 21h14")
private val InfoIcon = listOf("M12 8v5M12 16h.01", "M12 3a9 9 0 100 18 9 9 0 000-18z")

@Composable
fun SystemUpdateScreen(
    state: SystemUpdateUiState,
    onBack: () -> Unit,
    onInstallNow: () -> Unit,
    downloadProgress: Int? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Alx.Bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        ) {
            Header(onBack)

            VersionCard(state, Modifier.padding(top = 20.dp))

            BasicText(
                "NỘI DUNG CẬP NHẬT",
                Modifier.padding(top = 22.dp, start = 4.dp),
                style = type(11f, FontWeight.ExtraBold, Alx.Muted, spacing = 0.12f),
            )
            state.notes.forEachIndexed { i, note ->
                NoteCard(note, Modifier.padding(top = if (i == 0) 10.dp else 10.dp))
            }

            Row(Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SvgIcon(InfoIcon, 16.dp, Alx.Muted, 2f, Modifier.padding(top = 2.dp))
                BasicText(
                    "Giữ máy đủ pin và kết nối Wi-Fi ổn định trong lúc cài đặt. Máy sẽ tự khởi động lại sau khi cập nhật xong.",
                    style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.55f),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
                .navigationBarsPadding(),
        ) {
            val shape = RoundedCornerShape(18.dp)
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "btn")
            val isDownloading = downloadProgress != null
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(Alx.Blue, shape)
                    .clip(shape)
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        enabled = !isDownloading,
                        onClick = onInstallNow,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isDownloading) {
                        BasicText("Đang tải xuống $downloadProgress%", style = type(16f, FontWeight.Bold, Color.White))
                    } else {
                        SvgIcon(UpdateIcon, 20.dp, Color.White, 2.2f)
                        BasicText("Cài đặt ngay", style = type(16f, FontWeight.Bold, Color.White))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PressBox(
            onClick = onBack,
            modifier = Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
        BasicText("Cập nhật hệ thống", style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
    }
}

@Composable
private fun VersionCard(state: SystemUpdateUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).background(Alx.BlueSoft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            SvgIcon(UpdateIcon, 24.dp, Alx.Blue, 2f)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(state.appName, style = type(16f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
                BasicText(
                    "MỚI",
                    Modifier.background(Alx.Blue, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                    style = type(10f, FontWeight.ExtraBold, Color.White, spacing = 0.06f, lineHeight = 1.2f),
                )
            }
            BasicText(state.versionLabel, Modifier.padding(top = 3.dp), style = type(12.5f, FontWeight.SemiBold, Alx.Muted))
            BasicText(state.buildLabel, Modifier.padding(top = 1.dp), style = type(11f, FontWeight.Medium, Color(0xFF94A3B8)))
        }
    }
}

@Composable
private fun NoteCard(note: UpdateNote, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).padding(horizontal = 16.dp, vertical = 14.dp)) {
        BasicText(note.title, style = type(14.5f, FontWeight.ExtraBold, Alx.Ink))
        BasicText(note.body, Modifier.padding(top = 6.dp), style = type(13f, FontWeight.Medium, Alx.Muted, lineHeight = 1.55f))
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
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content() }
}

/* ───────────────────────────── Dữ liệu mẫu + Preview ───────────────────────────── */

val SampleUpdateNotes = listOf(
    UpdateNote("Facebook", "Sửa lỗi đăng nhập."),
    UpdateNote("Instagram", "Sửa lỗi đăng nhập."),
    UpdateNote("TikTok", "Thêm tuongtaccheo tiktok."),
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SystemUpdateScreenPreview() {
    SystemUpdateScreen(
        state = SystemUpdateUiState(
            versionLabel = "Phiên bản 1.0.6 · 18 MB",
            buildLabel = "Build ALX_1.0.6.V7",
            notes = SampleUpdateNotes,
        ),
        onBack = {},
        onInstallNow = {},
    )
}
