package com.cayxu.app.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SỔ XU: nguồn dữ liệu THẬT cho "Lịch sử xu". Không có dữ liệu mẫu nào trong màn hình.
 *
 * Mỗi lần một nền tảng trong app (XSMM, GoLike, Tuongtaccheo, TikTok TTC, Nhiemvucheo, hoặc đổi thưởng…)
 * trả xu hoặc trừ xu, nơi xử lý nhiệm vụ gọi [CoinLedger.record] đúng một lần. Hồ sơ và Lịch sử chỉ ĐỌC từ đây.
 */
enum class CoinEntryKind { Follow, Like, Comment, Share, Watch, Redeem, Other }

/**
 * Một dòng sổ xu.
 * @param coins số xu có dấu: cộng khi kiếm, ÂM khi đổi thưởng/trừ xu (vd +40, -1000).
 * @param title chữ hiển thị, tạo lúc ghi, vd "Theo dõi @linh.daily", "Đổi thẻ điện thoại".
 * @param atMillis thời điểm xảy ra (epoch millis).
 * @param source id nền tảng sinh ra dòng này ("xsmm", "golike", "ttc", "tiktok", "nvc", "redeem"). Chỉ để thống kê/đối soát, UI không hiện.
 * @param externalId id nhiệm vụ do nền tảng trả về, dùng để KHÔNG ghi trùng khi app gọi lại (null nếu nền tảng không có).
 */
data class CoinEntry(
    val id: Long = 0,
    val kind: CoinEntryKind,
    val title: String,
    val coins: Long,
    val atMillis: Long,
    val source: String,
    val externalId: String? = null,
)

interface CoinLedger {
    /** Ghi một dòng. Nếu [CoinEntry.externalId] đã có cùng [CoinEntry.source] thì bỏ qua (chống trùng). */
    suspend fun record(entry: CoinEntry)

    /** Các dòng trong đúng ngày [day] theo múi giờ [zone], mới nhất trước. Tự phát lại khi có dòng mới. */
    fun observeDay(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<CoinEntry>>

    /** [limit] dòng mới nhất bất kể ngày, cho mục "Lịch sử xu" ở Hồ sơ. */
    fun observeRecent(limit: Int): Flow<List<CoinEntry>>
}

/* ───────────────────────────── Định dạng hiển thị ───────────────────────────── */

private val Hm = DateTimeFormatter.ofPattern("HH:mm")
private val Dm = DateTimeFormatter.ofPattern("dd/MM")
private val WeekdayNames = arrayOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật")

/** "+40", "−1.000" (dấu trừ thật U+2212, phân cách nghìn bằng dấu chấm). 0 trả "0". */
fun formatCoins(coins: Long): String {
    val digits = kotlin.math.abs(coins).toString().reversed().chunked(3).joinToString(".").reversed()
    return when {
        coins > 0 -> "+$digits"
        coins < 0 -> "−$digits"
        else -> "0"
    }
}

fun timeOfDay(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime().format(Hm)

private val WeekdayNamesEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "Hôm nay, 10:24" / "Today, 10:24": dùng ở Hồ sơ nơi không có tiêu đề ngày. */
fun relativeTime(
    atMillis: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    isEn: Boolean = com.cayxu.app.ui.locale.LanguageState.language == com.cayxu.app.ui.locale.AppLanguage.EN
): String {
    val at = Instant.ofEpochMilli(atMillis).atZone(zone)
    val day = at.toLocalDate()
    val lead = when (day) {
        today -> if (isEn) "Today" else "Hôm nay"
        today.minusDays(1) -> if (isEn) "Yesterday" else "Hôm qua"
        else -> day.format(Dm)
    }
    return "$lead, ${at.toLocalTime().format(Hm)}"
}

/** "Hôm nay, 13/10" / "Today, 13/10" / "Chủ nhật, 11/10": nhãn giữa thanh chọn ngày, luôn tính từ ngày thật. */
fun dayLabel(
    day: LocalDate,
    today: LocalDate,
    isEn: Boolean = com.cayxu.app.ui.locale.LanguageState.language == com.cayxu.app.ui.locale.AppLanguage.EN
): String {
    val lead = when (day) {
        today -> if (isEn) "Today" else "Hôm nay"
        today.minusDays(1) -> if (isEn) "Yesterday" else "Hôm qua"
        else -> if (isEn) WeekdayNamesEn[day.dayOfWeek.value - 1] else WeekdayNames[day.dayOfWeek.value - 1]
    }
    return "$lead, ${day.format(Dm)}"
}
