package com.cayxu.app.ui.screens.golike

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokRunConfig
import com.cayxu.app.data.local.TikTokRunConfigStore
import com.cayxu.app.ui.theme.*

/**
 * Màn "Cấu hình hoạt động" mở ra khi bấm nút "Cấu hình chạy" ở Golike TikTok.
 *
 * Đã bỏ hẳn phần "Chọn loại nền tảng" (loại TikTok chọn từ trước ở màn Golike TikTok
 * rồi, không chọn lại ở đây nữa - header vẫn hiện tên loại đang cấu hình).
 *
 * Mọi giá trị số (giây/phút/nv/lần) đều bấm vào để mở popup chỉnh lại - xem
 * RangeEditDialog (chỉnh khoảng min-max) và SingleEditDialog (chỉnh 1 số).
 * Các nút Bật/Tắt (badge) vẫn bấm là gạt bật/tắt ngay, KHÔNG mở popup.
 *
 * Nút "LƯU CẤU HÌNH" đã có logic THẬT: lưu toàn bộ giá trị xuống
 * TikTokRunConfigStore (SharedPreferences, riêng theo từng loại TikTok/Lite/
 * Studio), báo Toast xác nhận rồi quay lại màn trước. Mở lại màn này sẽ đọc
 * đúng cấu hình đã lưu lần trước, không bị mất khi tắt/mở lại app.
 */
private fun variantLabel(variant: String): String = when (variant) {
    "LITE" -> "TikTok Lite"
    "STUDIO" -> "TikTok Studio"
    else -> "TikTok"
}

private data class RangeValue(val min: Int, val max: Int)

/** Mục tiêu đang được chỉnh trong popup - dispatch theo [id] khi lưu. */
private sealed class EditTarget {
    data class RangeTarget(val id: String, val title: String, val unit: String, val value: RangeValue) : EditTarget()
    data class SingleTarget(val id: String, val title: String, val unit: String, val value: Int) : EditTarget()
}

@Composable
fun GolikeTikTokConfigScreen(navController: NavController, variant: String) {
    val context = LocalContext.current
    // Đọc lại cấu hình đã lưu trước đó cho đúng loại (TikTok/Lite/Studio) này -
    // chưa lưu lần nào thì TikTokRunConfigStore tự trả về giá trị mặc định.
    val savedConfig = remember(variant) { TikTokRunConfigStore.getConfig(context, variant) }

    // ---- Thời gian ngẫu nhiên (khoảng min-max, đơn vị giây) ----
    var timeBetweenActions by remember { mutableStateOf(RangeValue(savedConfig.timeBetweenActionsMin, savedConfig.timeBetweenActionsMax)) }
    var timeBetweenTasks by remember { mutableStateOf(RangeValue(savedConfig.timeBetweenTasksMin, savedConfig.timeBetweenTasksMax)) }
    var timeNoTask by remember { mutableStateOf(RangeValue(savedConfig.timeNoTaskMin, savedConfig.timeNoTaskMax)) }
    var randomPauseRange by remember { mutableStateOf(RangeValue(savedConfig.randomPauseMin, savedConfig.randomPauseMax)) }

    // ---- Toggle hành vi ----
    var randomTapBeforeAction by remember { mutableStateOf(savedConfig.randomTapBeforeAction) }
    var randomViewContent by remember { mutableStateOf(savedConfig.randomViewContent) }
    var randomSwipe by remember { mutableStateOf(savedConfig.randomSwipe) }
    var occasionallyBackHome by remember { mutableStateOf(savedConfig.occasionallyBackHome) }
    var waitBeforeBackHome by remember { mutableStateOf(savedConfig.waitBeforeBackHome) }
    var backHomeAfterFinish by remember { mutableStateOf(savedConfig.backHomeAfterFinish) }

    // ---- Thông báo & làm mới ----
    var showNotifyNewContent by remember { mutableStateOf(savedConfig.showNotifyNewContent) }
    var periodicContentCheck by remember { mutableStateOf(savedConfig.periodicContentCheck) }
    var reloadUiOnUpdate by remember { mutableStateOf(savedConfig.reloadUiOnUpdate) }
    var waitBeforeReload by remember { mutableStateOf(savedConfig.waitBeforeReload) }
    var backHomeAfterComplete by remember { mutableStateOf(savedConfig.backHomeAfterComplete) }
    var waitBeforeBackHomeComplete by remember { mutableStateOf(savedConfig.waitBeforeBackHomeComplete) }
    var repeatBackHomeComplete by remember { mutableStateOf(savedConfig.repeatBackHomeComplete) }

    // ---- Khác & tuỳ chọn ----
    var randomPauseEnabled by remember { mutableStateOf(savedConfig.randomPauseEnabled) }
    var rotateAccountsEnabled by remember { mutableStateOf(savedConfig.rotateAccountsEnabled) }
    var rotateAfterCount by remember { mutableStateOf(savedConfig.rotateAfterCount) }
    var rotateRestMinutes by remember { mutableStateOf(savedConfig.rotateRestMinutes) }

    // 3 mục trước là "Tắt" - giờ mặc định BẬT, kèm "Sau 10 nv".
    var reduceSystemLoadEnabled by remember { mutableStateOf(savedConfig.reduceSystemLoadEnabled) }
    var reduceSystemLoadAfterCount by remember { mutableStateOf(savedConfig.reduceSystemLoadAfterCount) }
    var stopOnErrorEnabled by remember { mutableStateOf(savedConfig.stopOnErrorEnabled) }
    var stopOnErrorAfterCount by remember { mutableStateOf(savedConfig.stopOnErrorAfterCount) }
    var stopOnTasksDoneEnabled by remember { mutableStateOf(savedConfig.stopOnTasksDoneEnabled) }
    var stopOnTasksDoneAfterCount by remember { mutableStateOf(savedConfig.stopOnTasksDoneAfterCount) }

    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

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
                    value = timeBetweenActions,
                    onClick = {
                        editTarget = EditTarget.RangeTarget("actions", "Thời gian giữa các thao tác", "giây", timeBetweenActions)
                    }
                )
                RowDivider()
                TimeRangeRow(
                    title = "Thời gian giữa 2 nhiệm vụ",
                    subtitle = "Khoảng thời gian giữa các nhiệm vụ liên tiếp",
                    value = timeBetweenTasks,
                    onClick = {
                        editTarget = EditTarget.RangeTarget("tasks", "Thời gian giữa 2 nhiệm vụ", "giây", timeBetweenTasks)
                    }
                )
                RowDivider()
                TimeRangeRow(
                    title = "Thời gian khi không có nhiệm vụ",
                    subtitle = "Thời gian chờ trước khi tìm nhiệm vụ mới",
                    value = timeNoTask,
                    onClick = {
                        editTarget = EditTarget.RangeTarget("noTask", "Thời gian khi không có nhiệm vụ", "giây", timeNoTask)
                    }
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
                    WaitHintRow(
                        prefix = "Chờ",
                        valueText = "${waitBeforeBackHome}s",
                        suffix = "rồi quay lại",
                        onValueClick = {
                            editTarget = EditTarget.SingleTarget("waitBackHome", "Chờ bao lâu rồi quay lại", "giây", waitBeforeBackHome)
                        }
                    )
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
                    WaitHintRow(
                        prefix = "Chờ",
                        valueText = "${waitBeforeReload}s",
                        suffix = "rồi làm mới",
                        onValueClick = {
                            editTarget = EditTarget.SingleTarget("waitReload", "Chờ bao lâu rồi làm mới", "giây", waitBeforeReload)
                        }
                    )
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
                        EditableValuePill("${waitBeforeBackHomeComplete}s") {
                            editTarget = EditTarget.SingleTarget("waitBackHomeComplete", "Chờ bao lâu trước khi quay lại", "giây", waitBeforeBackHomeComplete)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("· sau đó", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        EditableValuePill("$repeatBackHomeComplete lần") {
                            editTarget = EditTarget.SingleTarget("repeatBackHomeComplete", "Lặp lại bao nhiêu lần", "lần", repeatBackHomeComplete)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Khác & Tùy chọn", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            SectionCard(paddingBetweenRows = true) {
                // Chữ "Sau 40 - 60 giây" giờ nằm cạnh mô tả (dưới subtitle), không còn
                // nằm cạnh công tắc nữa - đúng yêu cầu.
                ToggleRowWithBelowValue(
                    title = "Tạm dừng ngẫu nhiên giữa các hành động",
                    subtitle = "Giúp hoạt động tự nhiên hơn",
                    checked = randomPauseEnabled,
                    onCheckedChange = { randomPauseEnabled = it }
                ) {
                    Text("Sau", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    EditableValuePill("${randomPauseRange.min} - ${randomPauseRange.max} giây") {
                        editTarget = EditTarget.RangeTarget("randomPause", "Tạm dừng ngẫu nhiên giữa các hành động", "giây", randomPauseRange)
                    }
                }
                RowDivider()
                // "Sau 50 nv nghỉ 5 phút" - cả 2 số đều bấm sửa riêng được.
                ToggleRowWithBelowValue(
                    title = "Luân phiên tài khoản",
                    subtitle = "Tự động đổi tài khoản trong quá trình chạy",
                    checked = rotateAccountsEnabled,
                    onCheckedChange = { rotateAccountsEnabled = it }
                ) {
                    Text("Sau", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    EditableValuePill("$rotateAfterCount nv") {
                        editTarget = EditTarget.SingleTarget("rotateCount", "Luân phiên sau bao nhiêu nhiệm vụ", "nv", rotateAfterCount)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("nghỉ", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    EditableValuePill("$rotateRestMinutes phút") {
                        editTarget = EditTarget.SingleTarget("rotateMinutes", "Nghỉ bao nhiêu phút", "phút", rotateRestMinutes)
                    }
                }
                RowDivider()
                // 3 mục trước "Tắt" - giờ mặc định Bật, kèm "Sau 10 nv" bấm sửa được;
                // nút Bật/Tắt vẫn chỉ gạt bật/tắt, không mở popup.
                ToggleBadgeRowWithBelowValue(
                    title = "Giảm tải tài nguyên hệ thống",
                    subtitle = "Giảm mức sử dụng CPU & RAM",
                    isOn = reduceSystemLoadEnabled,
                    onToggle = { reduceSystemLoadEnabled = !reduceSystemLoadEnabled }
                ) {
                    Text("Sau", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    EditableValuePill("$reduceSystemLoadAfterCount nv") {
                        editTarget = EditTarget.SingleTarget("reduceLoadCount", "Có hiệu lực sau bao nhiêu nhiệm vụ", "nv", reduceSystemLoadAfterCount)
                    }
                }
                RowDivider()
                ToggleBadgeRowWithBelowValue(
                    title = "Dừng khi gặp lỗi",
                    subtitle = "Tự động dừng khi gặp sự cố trong quá trình chạy",
                    isOn = stopOnErrorEnabled,
                    onToggle = { stopOnErrorEnabled = !stopOnErrorEnabled }
                ) {
                    Text("Sau", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    EditableValuePill("$stopOnErrorAfterCount nv") {
                        editTarget = EditTarget.SingleTarget("stopOnErrorCount", "Có hiệu lực sau bao nhiêu nhiệm vụ", "nv", stopOnErrorAfterCount)
                    }
                }
                RowDivider()
                ToggleBadgeRowWithBelowValue(
                    title = "Dừng khi hoàn thành tác vụ",
                    subtitle = "Tự động dừng khi tất cả tác vụ đã xong",
                    isOn = stopOnTasksDoneEnabled,
                    onToggle = { stopOnTasksDoneEnabled = !stopOnTasksDoneEnabled }
                ) {
                    Text("Sau", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    EditableValuePill("$stopOnTasksDoneAfterCount nv") {
                        editTarget = EditTarget.SingleTarget("stopTasksDoneCount", "Có hiệu lực sau bao nhiêu nhiệm vụ", "nv", stopOnTasksDoneAfterCount)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val config = TikTokRunConfig(
                        timeBetweenActionsMin = timeBetweenActions.min,
                        timeBetweenActionsMax = timeBetweenActions.max,
                        timeBetweenTasksMin = timeBetweenTasks.min,
                        timeBetweenTasksMax = timeBetweenTasks.max,
                        timeNoTaskMin = timeNoTask.min,
                        timeNoTaskMax = timeNoTask.max,
                        randomTapBeforeAction = randomTapBeforeAction,
                        randomViewContent = randomViewContent,
                        randomSwipe = randomSwipe,
                        occasionallyBackHome = occasionallyBackHome,
                        waitBeforeBackHome = waitBeforeBackHome,
                        backHomeAfterFinish = backHomeAfterFinish,
                        showNotifyNewContent = showNotifyNewContent,
                        periodicContentCheck = periodicContentCheck,
                        reloadUiOnUpdate = reloadUiOnUpdate,
                        waitBeforeReload = waitBeforeReload,
                        backHomeAfterComplete = backHomeAfterComplete,
                        waitBeforeBackHomeComplete = waitBeforeBackHomeComplete,
                        repeatBackHomeComplete = repeatBackHomeComplete,
                        randomPauseEnabled = randomPauseEnabled,
                        randomPauseMin = randomPauseRange.min,
                        randomPauseMax = randomPauseRange.max,
                        rotateAccountsEnabled = rotateAccountsEnabled,
                        rotateAfterCount = rotateAfterCount,
                        rotateRestMinutes = rotateRestMinutes,
                        reduceSystemLoadEnabled = reduceSystemLoadEnabled,
                        reduceSystemLoadAfterCount = reduceSystemLoadAfterCount,
                        stopOnErrorEnabled = stopOnErrorEnabled,
                        stopOnErrorAfterCount = stopOnErrorAfterCount,
                        stopOnTasksDoneEnabled = stopOnTasksDoneEnabled,
                        stopOnTasksDoneAfterCount = stopOnTasksDoneAfterCount
                    )
                    TikTokRunConfigStore.saveConfig(context, variant, config)
                    Toast.makeText(context, "Đã lưu cấu hình ${variantLabel(variant)}", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("LƯU CẤU HÌNH", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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

    // ---- Popup chỉnh số - dùng chung cho MỌI giá trị bấm-để-sửa ở trên ----
    when (val target = editTarget) {
        is EditTarget.RangeTarget -> {
            RangeEditDialog(
                title = target.title,
                unit = target.unit,
                initial = target.value,
                onDismiss = { editTarget = null },
                onConfirm = { newValue ->
                    when (target.id) {
                        "actions" -> timeBetweenActions = newValue
                        "tasks" -> timeBetweenTasks = newValue
                        "noTask" -> timeNoTask = newValue
                        "randomPause" -> randomPauseRange = newValue
                    }
                    editTarget = null
                }
            )
        }
        is EditTarget.SingleTarget -> {
            SingleEditDialog(
                title = target.title,
                unit = target.unit,
                initial = target.value,
                onDismiss = { editTarget = null },
                onConfirm = { newValue ->
                    when (target.id) {
                        "waitBackHome" -> waitBeforeBackHome = newValue
                        "waitReload" -> waitBeforeReload = newValue
                        "waitBackHomeComplete" -> waitBeforeBackHomeComplete = newValue
                        "repeatBackHomeComplete" -> repeatBackHomeComplete = newValue
                        "rotateCount" -> rotateAfterCount = newValue
                        "rotateMinutes" -> rotateRestMinutes = newValue
                        "reduceLoadCount" -> reduceSystemLoadAfterCount = newValue
                        "stopOnErrorCount" -> stopOnErrorAfterCount = newValue
                        "stopTasksDoneCount" -> stopOnTasksDoneAfterCount = newValue
                    }
                    editTarget = null
                }
            )
        }
        null -> Unit
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
private fun EditableValuePill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(SuccessGreen.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeRangeRow(title: String, subtitle: String, value: RangeValue, onClick: () -> Unit) {
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
        EditableValuePill("${value.min}–${value.max}s", onClick)
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

/** Toggle (Switch) với dòng giá trị hiển thị NGAY DƯỚI mô tả, thay vì cạnh switch. */
@Composable
private fun ToggleRowWithBelowValue(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    valueRow: @Composable RowScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            valueRow()
        }
    }
}

/** Badge Bật/Tắt (bấm là gạt ngay, KHÔNG mở popup) với dòng giá trị bên dưới mô tả. */
@Composable
private fun ToggleBadgeRowWithBelowValue(
    title: String,
    subtitle: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    valueRow: @Composable RowScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            valueRow()
        }
    }
}

@Composable
private fun WaitHintRow(prefix: String, valueText: String, suffix: String, onValueClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(prefix, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        EditableValuePill(valueText, onValueClick)
        Spacer(Modifier.width(6.dp))
        Text(suffix, color = TextSecondary, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------
// Popup chỉnh số - dùng chung cho toàn bộ giá trị bấm-để-sửa trong màn này.
// ---------------------------------------------------------------------------

@Composable
private fun RangeEditDialog(
    title: String,
    unit: String,
    initial: RangeValue,
    onDismiss: () -> Unit,
    onConfirm: (RangeValue) -> Unit
) {
    var minText by remember(title) { mutableStateOf(initial.min.toString()) }
    var maxText by remember(title) { mutableStateOf(initial.max.toString()) }

    EditDialogShell(
        title = title,
        onDismiss = onDismiss,
        onConfirm = {
            val min = minText.toIntOrNull() ?: initial.min
            val max = maxText.toIntOrNull() ?: initial.max
            onConfirm(RangeValue(min.coerceAtLeast(0), max.coerceAtLeast(min)))
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = minText,
                onValueChange = { minText = it.filter { c -> c.isDigit() } },
                label = { Text("Từ ($unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it.filter { c -> c.isDigit() } },
                label = { Text("Đến ($unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SingleEditDialog(
    title: String,
    unit: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var valueText by remember(title) { mutableStateOf(initial.toString()) }

    EditDialogShell(
        title = title,
        onDismiss = onDismiss,
        onConfirm = {
            val value = valueText.toIntOrNull() ?: initial
            onConfirm(value.coerceAtLeast(0))
        }
    ) {
        OutlinedTextField(
            value = valueText,
            onValueChange = { valueText = it.filter { c -> c.isDigit() } },
            label = { Text("Giá trị ($unit)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EditDialogShell(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                content()
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Huỷ", color = TextSecondary) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Text("Lưu", color = Color.White)
                    }
                }
            }
        }
    }
}
