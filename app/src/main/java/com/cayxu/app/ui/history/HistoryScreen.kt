package com.cayxu.app.ui.history

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.cayxu.app.data.CoinEntry
import com.cayxu.app.data.CoinEntryKind
import com.cayxu.app.data.CoinLedger
import com.cayxu.app.data.CoinLedgerProvider
import com.cayxu.app.data.dayLabel
import com.cayxu.app.data.formatCoins
import com.cayxu.app.data.timeOfDay
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import java.time.LocalDate
import java.time.YearMonth

/* Icon: lưới 24×24, cờ cung luôn viết cách nhau vì PathParser không đọc dạng dính. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val UserPlus = listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", circle(9f, 7f, 4f), "M19 8v6M22 11h-6")
private val Heart = listOf("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z")
private val Comment = listOf("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")
private val Share = listOf("M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7M16 6l-4-4-4 4M12 2v13")
private val Play = listOf(circle(12f, 12f, 9f), "M10 8.5l5 3.5-5 3.5z")
private val Gift = listOf(
    "M5 8h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z",
    "M12 8v13M3 12h18",
    "M12 8c-2 0-4-1-4-3a2 2 0 0 1 4-.5A2 2 0 0 1 16 5c0 2-2 3-4 3z",
)
private val Dots = listOf("M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01")
private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val ChevronL = listOf("M15 6l-6 6 6 6")
private val ChevronR = listOf("M9 6l6 6-6 6")
private val Calendar = listOf("M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M3 10h18M8 3v4M16 3v4")

private val RowLine = Color(0xFFEEF1F6)
private val DangerSoft = Color(0xFFFDECEC)

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Màn Lịch sử xu (mở từ "Xem tất cả" ở Hồ sơ). Khung 390 × 844dp, nội dung cuộn.
 * Đây là màn con toàn màn hình: KHÔNG có dock, hiển thị bên ngoài MainRoute.
 * Dữ liệu 100% từ [state] (do [HistoryViewModel] lấy từ sổ xu thật), không có dữ liệu mẫu.
 *
 * @param onPick chọn ngày bất kỳ từ lịch tháng (chạm giữa thanh chọn ngày).
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PressBox(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).size(44.dp)
                    .background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
            BasicText(com.cayxu.app.ui.locale.Str.historyTitle, style = type(20f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
        }

        DateNavigator(
            label = dayLabel(state.day, state.today),
            canGoNext = !state.isToday,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenCalendar = { picking = true },
            modifier = Modifier.padding(top = 20.dp),
        )

        when {
            state.loading -> Unit
            state.entries.isEmpty() -> EmptyDay(Modifier.padding(top = 16.dp))
            else -> CoinEntryCard(Modifier.padding(top = 16.dp)) {
                state.entries.forEachIndexed { i, e -> CoinEntryRow(e, timeOfDay(e.atMillis), divider = i > 0) }
            }
        }

        Spacer(Modifier.height(40.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    if (picking) {
        DatePickerDialog(
            selected = state.day,
            today = state.today,
            onPick = { picking = false; onPick(it) },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Overload kết nối NavController và tự động khởi tạo ViewModel từ CoinLedgerProvider.
 */
@Composable
fun HistoryScreen(
    navController: NavController,
    ledger: CoinLedger = CoinLedgerProvider.get(LocalContext.current),
) {
    val vm: HistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { HistoryViewModel(ledger) } }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler { navController.popBackStack() }

    HistoryScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onPrevious = vm::previous,
        onNext = vm::next,
        onPick = vm::select,
    )
}

@Composable
private fun DateNavigator(
    label: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().height(56.dp).background(Alx.White, shape).border(1.dp, Alx.Line, shape).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(ChevronL, enabled = true, description = "Ngày trước", onClick = onPrevious)
        PressBox(
            onClick = onOpenCalendar,
            modifier = Modifier.weight(1f).height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SvgIcon(Calendar, 18.dp, Alx.Blue, 1.9f)
                BasicText(label, style = type(15f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f), maxLines = 1)
            }
        }
        StepButton(ChevronR, enabled = canGoNext, description = "Ngày sau", onClick = onNext)
    }
}

@Composable
private fun StepButton(icon: List<String>, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(Modifier.alpha(if (enabled) 1f else 0.3f)) {
        PressBox(
            onClick = { if (enabled) onClick() },
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(icon, 20.dp, Alx.Ink, 2f) }
    }
}

@Composable
private fun EmptyDay(modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).padding(horizontal = 16.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            com.cayxu.app.ui.locale.Str.historyEmptyDay,
            style = type(13.5f, FontWeight.Medium, Alx.Muted, align = TextAlign.Center),
        )
    }
}

/* ───────────────────────────── Thành phần dùng chung với Hồ sơ ───────────────────────────── */

/** Thẻ trắng bo 20dp chứa các [CoinEntryRow]. Hồ sơ và Lịch sử dùng chung nên hai nơi luôn giống nhau. */
@Composable
internal fun CoinEntryCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape).padding(horizontal = 16.dp),
    ) { content() }
}

/** Một dòng sổ xu. [timeText] do nơi gọi chọn: "10:24" (Lịch sử, đã có tiêu đề ngày) hoặc "Hôm nay, 10:24" (Hồ sơ). */
@Composable
internal fun CoinEntryRow(entry: CoinEntry, timeText: String, divider: Boolean) {
    val (paths, color, tint) = when (entry.kind) {
        CoinEntryKind.Follow -> Triple(UserPlus, Alx.Blue, Alx.BlueSoft)
        CoinEntryKind.Like -> Triple(Heart, Alx.Blue, Alx.BlueSoft)
        CoinEntryKind.Comment -> Triple(Comment, Alx.Blue, Alx.BlueSoft)
        CoinEntryKind.Share -> Triple(Share, Alx.Blue, Alx.BlueSoft)
        CoinEntryKind.Watch -> Triple(Play, Alx.Blue, Alx.BlueSoft)
        CoinEntryKind.Redeem -> Triple(Gift, Alx.Danger, DangerSoft)
        CoinEntryKind.Other -> Triple(Dots, Alx.Blue, Alx.BlueSoft)
    }
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                SvgIcon(paths, 19.dp, color, 1.8f)
            }
            Column(Modifier.weight(1f)) {
                BasicText(entry.title, style = type(14.5f, FontWeight.Bold, Alx.Ink), maxLines = 1)
                BasicText(timeText, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted), maxLines = 1)
            }
            BasicText(
                formatCoins(entry.coins),
                style = type(14.5f, FontWeight.ExtraBold, if (entry.coins < 0) Alx.Danger else Alx.Blue),
            )
        }
    }
}

/* ───────────────────────────── Lịch tháng chọn ngày ───────────────────────────── */

/** Hộp chọn ngày: lịch tháng, tuần bắt đầu thứ Hai, không cho chọn ngày sau hôm nay. */
@Composable
private fun DatePickerDialog(selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val lastMonth = YearMonth.from(today)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StepButton(ChevronL, enabled = true, description = "Tháng trước") { month = month.minusMonths(1) }
                BasicText(
                    "Tháng ${month.monthValue}, ${month.year}",
                    Modifier.weight(1f),
                    style = type(15f, FontWeight.ExtraBold, Alx.Ink, align = TextAlign.Center),
                )
                StepButton(ChevronR, enabled = month.isBefore(lastMonth), description = "Tháng sau") { month = month.plusMonths(1) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val weekdays = if (com.cayxu.app.ui.locale.LanguageState.language == com.cayxu.app.ui.locale.AppLanguage.EN)
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                else
                    listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                weekdays.forEach {
                    BasicText(it, Modifier.weight(1f), style = type(11f, FontWeight.Bold, Alx.Muted, align = TextAlign.Center))
                }
            }
            val lead = month.atDay(1).dayOfWeek.value - 1
            val length = month.lengthOfMonth()
            val rows = (lead + length + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    for (c in 0 until 7) {
                        val n = r * 7 + c - lead + 1
                        if (n in 1..length) {
                            val date = month.atDay(n)
                            DayCell(n, date == selected, date == today, enabled = !date.isAfter(today), Modifier.weight(1f)) { onPick(date) }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(n: Int, selected: Boolean, isToday: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(40.dp), contentAlignment = Alignment.Center) {
        PressBox(
            onClick = { if (enabled) onClick() },
            modifier = Modifier.size(36.dp).then(if (selected) Modifier.background(Alx.Blue, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                n.toString(),
                style = type(
                    14f,
                    if (selected || isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                    when {
                        selected -> Color.White
                        !enabled -> Color(0xFFCBD2DF)
                        isToday -> Alx.Blue
                        else -> Alx.Ink
                    },
                    align = TextAlign.Center,
                ),
            )
        }
    }
}

/** Vùng bấm có hiệu ứng nhún 0.985 / 120 ms, không gợn sóng. */
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

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    val today = LocalDate.of(2026, 10, 13)
    val at = today.atTime(10, 24).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    HistoryScreen(
        HistoryUiState(
            day = today, today = today, loading = false,
            entries = listOf(
                CoinEntry(1, CoinEntryKind.Follow, "Theo dõi @linh.daily", 40, at, "preview"),
                CoinEntry(2, CoinEntryKind.Redeem, "Đổi thẻ điện thoại", -1000, at - 3_600_000, "preview"),
            ),
        ),
        onBack = {}, onPrevious = {}, onNext = {}, onPick = {},
    )
}
