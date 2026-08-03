package com.cayxu.app.data.repository

import com.cayxu.app.data.api.GolikeRetrofitClient
import com.google.gson.JsonObject

/** Thu nhập theo 1 nền tảng cụ thể (facebook/instagram/tiktok/...). */
data class GolikePlatformStat(
    val platform: String,
    val pendingCoin: Long,
    val holdCoin: Long
)

data class GolikeStatisticsReport(
    val currentCoin: Long,
    val coinToday: Long,
    val platformStats: List<GolikePlatformStat>
) {
    /** Tổng thu nhập hôm nay = tổng pending_coin của TẤT CẢ nền tảng - đây là số thật
     *  dùng cho "Thu nhập hôm nay", vì field "coin" ở gốc JSON có vẻ không phản ánh
     *  đúng tổng này (ví dụ thật: coin=0 nhưng facebook.pending_coin=1529). */
    val totalPendingToday: Long get() = platformStats.sumOf { it.pendingCoin }
}

sealed class GolikeStatisticsResult {
    data class Success(val report: GolikeStatisticsReport) : GolikeStatisticsResult()
    data class Error(val message: String) : GolikeStatisticsResult()
}

/**
 * Gọi THẬT GET https://gateway.golike.net/api/statistics/report bằng token Bearer đã đăng
 * nhập - lấy thu nhập hôm nay theo TỪNG nền tảng. Dùng cho "Thu nhập hôm nay" (Home) và
 * "Lịch sử làm nhiệm vụ"/biểu đồ phân bổ theo nền tảng (Wallet) - dữ liệu THẬT, không còn
 * số mẫu/biểu đồ giả nữa.
 *
 * JSON mẫu thật (rút gọn):
 * { "current_coin": 22084, "coin": 0,
 *   "facebook": {"pending_coin":1529,"hold_coin":0,"time":27,"lasted":"03-08-2026 11:08:51"},
 *   "tiktok": {"pending_coin":50,"hold_coin":0}, "instagram": {...}, "pinterest": {...}, ... }
 */
object GolikeStatisticsRepository {
    // Đúng các key nền tảng GoLike trả về trong response - tách riêng khỏi các field chung
    // khác (current_coin, coin, data, status, server...).
    private val PLATFORM_KEYS = listOf(
        "facebook", "instagram", "tiktok", "shopee", "twitter", "lazada", "youtube",
        "traffic", "review", "threads", "linkedin", "snapchat", "pinterest", "bluesky",
        "tumblr", "soundcloud"
    )

    suspend fun fetchReport(rawToken: String): GolikeStatisticsResult {
        val token = rawToken.trim()
        if (token.isBlank()) return GolikeStatisticsResult.Error("Chưa có token")
        val authHeader = if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

        return try {
            val response = GolikeRetrofitClient.api.getStatisticsReport(authHeader)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val currentCoin = body.get("current_coin")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                val coinToday = body.get("coin")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                val platformStats = PLATFORM_KEYS.mapNotNull { key ->
                    val el = body.get(key) ?: return@mapNotNull null
                    if (!el.isJsonObject) return@mapNotNull null
                    val obj = el.asJsonObject
                    val pending = obj.get("pending_coin")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                    val hold = obj.get("hold_coin")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                    GolikePlatformStat(key, pending, hold)
                }
                GolikeStatisticsResult.Success(GolikeStatisticsReport(currentCoin, coinToday, platformStats))
            } else {
                val message = when (response.code()) {
                    401, 403 -> "Token không hợp lệ hoặc đã hết hạn"
                    else -> "Không lấy được thống kê (mã lỗi HTTP: ${response.code()})"
                }
                GolikeStatisticsResult.Error(message)
            }
        } catch (e: Exception) {
            GolikeStatisticsResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }
}
