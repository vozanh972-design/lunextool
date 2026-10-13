package com.cayxu.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cayxu.app.data.CoinEntry
import com.cayxu.app.data.CoinLedger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class HistoryUiState(
    val day: LocalDate,
    val today: LocalDate,
    val entries: List<CoinEntry>,
    val loading: Boolean,
) {
    val isToday get() = day == today
}

/**
 * Dữ liệu màn Lịch sử xu. Ngày lấy từ đồng hồ máy ([LocalDate.now]), danh sách lấy từ [CoinLedger]:
 * đổi ngày thì tự truy vấn đúng ngày đó, có dòng mới ghi vào sổ thì danh sách tự cập nhật.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val ledger: CoinLedger,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(zone))
    private val day = MutableStateFlow(today.value)

    val state: StateFlow<HistoryUiState> =
        combine(day, today) { d, t -> d to t }
            .flatMapLatest { (d, t) ->
                ledger.observeDay(d, zone)
                    .map { HistoryUiState(d, t, it, loading = false) }
                    .onStart { emit(HistoryUiState(d, t, emptyList(), loading = true)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(day.value, today.value, emptyList(), true))

    /** Chọn ngày bất kỳ; ngày tương lai bị đưa về hôm nay. */
    fun select(d: LocalDate) { day.value = if (d.isAfter(today.value)) today.value else d }
    fun previous() = select(day.value.minusDays(1))
    fun next() = select(day.value.plusDays(1))

    /** Gọi khi màn quay lại (ON_RESUME): nếu app mở xuyên qua nửa đêm thì "hôm nay" được cập nhật. */
    fun refreshToday() {
        val now = LocalDate.now(zone)
        if (now != today.value) today.value = now
    }
}
