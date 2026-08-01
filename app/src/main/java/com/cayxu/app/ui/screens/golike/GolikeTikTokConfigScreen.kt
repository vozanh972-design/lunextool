package com.cayxu.app.ui.screens.golike

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.*

/**
 * Màn "Cấu hình hoạt động" mở ra khi bấm nút "Cấu hình chạy" ở Golike TikTok.
 * Bố cục và nội dung theo đúng 2 ảnh mẫu (ảnh 1 = phần trên, ảnh 2 = nối tiếp
 * bên dưới của CÙNG 1 màn cuộn dọc).
 *
 * CHỈ là giao diện: toàn bộ giá trị/toggle dùng state cục bộ trong Compose, CHƯA
 * gắn logic chạy/lưu cấu hình thật - nút "Bắt đầu hoạt động" chưa có logic.
 * Không đụng tới màn Golike TikTok hay các phần khác ngoài route mới này.
 */
private fun variantLabel(variant: String): String = when (variant) {
    "LITE" -> "TikTok Lite"
    "STUDIO" -> "TikTok Studio"
    else -> "TikTok"
}

@Composable
fun GolikeTikTokConfigScreen(navController: NavController, variant: String) {
    var selectedPlatform by remember { mutableStateOf(variantLabel(variant)) }

    // Toggle hành vi
    var randomTapBeforeAction by remember { mutableStateOf(true) }
    var randomViewContent by remember { mutableStateOf(true) }
    var randomSwipe by remember { mutableStateOf(true) }
    var occasionallyBackHome by remember { mutableStateOf(true) }
    var backHomeAfterFinish by remember { mutableStateOf(true) }

    // Thông báo & làm mới
    var showNotifyNewContent by remember { mutableStateOf(true) }
    var periodicContentCheck by remember { mutableStateOf(true) }
    var reloadUiOnUpdate by remember { mutableStateOf(true) }
    var backHomeAfterComplete by remember { mutableStateOf(true) }

    // Khác & tuỳ chọn
    var randomPause by remember { mutableStateOf(true) }
    var rotateAccounts by remember { mutableStateOf(true) }
    var reduceSystemLoad by remember { mutableStateOf(false) }
    var stopOnNoNetwork by remember { mutableStateOf(false) }
    var stopOnTasksDone by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Cấu hình hoạt động", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "${variantLabel(variant)} · Bước 3/3 · Thiết lập",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Text("3/3", fontSize = 13.sp, color = TextSecondary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- Chọn loại nền tảng ----
            SectionCard {
                Text("Chọn loại nền tảng", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                Text(
                    "Lựa chọn nền tảng để thiết lập quy trình hoạt động",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("TikTok", "TikTok Lite", "TikTok Studio").forEach { label ->
                        val isSelected = label == selectedPlatform
                        PlatformPillButton(
                            label = label,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedPlatform = label }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Thiết lập thời gian hoạt động · ngẫu nhiên",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            SectionCard(paddingBetweenRows = true) {
                TimeRangeRow(
                    title = "Thời gian giữa các thao tác",
                    subtitle = "Khoảng thời gian giữa mỗi hành động",
                    rangeText = "5–15s"
                )
                RowDivider()
                TimeRangeRow(
                    title = "Thời gian giữa 2 nhiệm vụ",
                    subtitle = "Khoảng thời gian giữa các nhiệm vụ liên tiếp",
                    rangeText = "10–20s"
                )
                RowDivider()
                TimeRangeRow(
                    title = "Thời gian khi không có nhiệm vụ",
                    subtitle = "Thời gian chờ trước khi tìm nhiệm vụ mới",
                    rangeText = "20–40s"
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Tùy chọn hành vi", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            SectionCard(paddingBetweenRows = true) {
                ToggleRow(
                    title = "Gõ nhẹ ngẫu nhiên trước mỗi thao tác",
                    subtitle = "Mô phỏng hành vi tương tác thực tế",
                    checked = randomTapBeforeAction,
                    onCheckedChange = { randomTapBeforeAction = it }
                )
                RowDivider()
                ToggleRow(
                    title = "Xem nội dung ngẫu nhiên",
                    subtitle = "Xem từ 3–8 giây trước khi tương tác",
                    checked = randomViewContent,
                    onCheckedChange = { randomViewContent = it }
                )
                RowDivider()
                ToggleRow(
                    title = "Vuốt ngẫu nhiên trong quá trình hoạt động",
                    subtitle = "Vuốt lên/xuống trái/phải một cách tự nhiên",
                    checked = randomSwipe,
                    onCheckedChange = { randomSwipe = it }
                )
                RowDivider()
                ToggleRow(
                    title = "Thỉnh thoảng quay lại trang chủ",
                    subtitle = "Quay về trang chính sau một số lần thao tác",
                    checked = occasionallyBackHome,
                    onCheckedChange = { occasionallyBackHome = it }
                )
                if (occasionallyBackHome) {
                    WaitHintRow(prefix = "Chờ", valueText = "5s", suffix = "rồi quay lại")
                }
                RowDivider()
                ToggleRow(
                    title = "Quay lại trang chủ sau khi kết thúc",
                    subtitle = "Trở về trang chính khi hoàn tất nhiệm vụ",
                    checked = backHomeAfterFinish,
                    onCheckedChange = { backHomeAfterFinish = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionCard(paddingBetweenRows = true) {
                ToggleRow(
                    title = "Hiển thị thông báo khi có nội dung mới",
                    subtitle = "Thông báo trong ứng dụng",
                    checked = showNotifyNewContent,
                    onCheckedChange = { showNotifyNewContent = it }
                )
                RowDivider()
                ToggleRow(
                    title = "Kiểm tra nội dung định kỳ",
                    subtitle = "Kiểm tra sau khoảng thời gian nhất định",
                    checked = periodicContentCheck,
                    onCheckedChange = { periodicContentCheck = it }
                )
                RowDivider()
                ToggleRow(
                    title = "Tải lại giao diện khi có cập nhật",
                    subtitle = "Tự động làm mới giao diện",
                    checked = reloadUiOnUpdate,
                    onCheckedChange = { reloadUiOnUpdate = it }
                )
                if (reloadUiOnUpdate) {
                    WaitHintRow(prefix = "Chờ", valueText = "5s", suffix = "rồi làm mới")
                }
                RowDivider()
                ToggleRow(
                    title = "Quay lại trang chính sau khi hoàn tất",
                    subtitle = "Tự động quay về màn hình chính",
                    checked = backHomeAfterComplete,
                    onCheckedChange = { backHomeAfterComplete = it }
                )
                if (backHomeAfterComplete) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Chờ", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        ValuePill("3s")
                        Spacer(Modifier.width(6.dp))
                        Text("· sau đó", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        ValuePill("1 lần")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Khác & Tùy chọn", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            SectionCard(paddingBetweenRows = true) {
                ToggleWithValueRow(
                    title = "Tạm dừng ngẫu nhiên giữa các hành động",
                    subtitle = "Giúp hoạt động tự nhiên hơn",
                    valueText = "Sau 40 - 60 giây",
                    checked = randomPause,
                    onCheckedChange = { randomPause = it }
                )
                RowDivider()
                ToggleWithValueRow(
                    title = "Luân phiên tài khoản",
                    subtitle = "Tự động đổi tài khoản trong quá trình chạy",
                    valueText = "Sau 5 phút",
                    checked = rotateAccounts,
                    onCheckedChange = { rotateAccounts = it }
                )
                RowDivider()
                OffOnBadgeRow(
                    title = "Giảm tải tài nguyên hệ thống",
                    subtitle = "Giảm mức sử dụng CPU & RAM",
                    isOn = reduceSystemLoad,
                    onToggle = { reduceSystemLoad = !reduceSystemLoad }
                )
                RowDivider()
                OffOnBadgeRow(
                    title = "Dừng khi mất kết nối mạng",
                    subtitle = "Tự động dừng khi không có mạng",
                    isOn = stopOnNoNetwork,
                    onToggle = { stopOnNoNetwork = !stopOnNoNetwork }
                )
                RowDivider()
                OffOnBadgeRow(
                    title = "Dừng khi hoàn thành tác vụ",
                    subtitle = "Tự động dừng khi tất cả tác vụ đã xong",
                    isOn = stopOnTasksDone,
                    onToggle = { stopOnTasksDone = !stopOnTasksDone }
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { /* Chưa gắn logic - chỉ hiển thị nút */ },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("BẮT ĐẦU HOẠT ĐỘNG", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("1 tài khoản", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Tính năng hoạt động an toàn và ổn định",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Không lưu mật khẩu · Không thu thập dữ liệu cá nhân",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(paddingBetweenRows: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = if (paddingBetweenRows) Arrangement.spacedBy(2.dp) else Arrangement.Top
        ) {
            content()
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = TextSecondary.copy(alpha = 0.12f))
}

@Composable
private fun PlatformPillButton(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) SuccessGreen.copy(alpha = 0.18f) else AppBackground,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) SuccessGreen else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TimeRangeRow(title: String, subtitle: String, rangeText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .background(TextSecondary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text("GIÂY", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        ValuePill(rangeText)
    }
}

@Composable
private fun ValuePill(text: String) {
    Box(
        modifier = Modifier
            .background(SuccessGreen.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CardWhite, checkedTrackColor = SuccessGreen)
        )
    }
}

@Composable
private fun ToggleWithValueRow(
    title: String,
    subtitle: String,
    valueText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Text(valueText, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CardWhite, checkedTrackColor = SuccessGreen)
        )
    }
}

@Composable
private fun OffOnBadgeRow(title: String, subtitle: String, isOn: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .background(
                    if (isOn) SuccessGreen.copy(alpha = 0.18f) else TextSecondary.copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                if (isOn) "Bật" else "Tắt",
                color = if (isOn) SuccessGreen else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WaitHintRow(prefix: String, valueText: String, suffix: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(prefix, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        ValuePill(valueText)
        Spacer(Modifier.width(6.dp))
        Text(suffix, color = TextSecondary, fontSize = 12.sp)
    }
}
