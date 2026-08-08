package com.cayxu.app.data.repository

import com.cayxu.app.data.api.GolikeRetrofitClient

/** 1 job (nhiệm vụ) TikTok lấy về từ GoLike - CHỈ map đúng field đã thấy trong response
 *  thật, KHÔNG suy đoán thêm field khác chưa xác nhận. */
data class GolikeTikTokJob(
    val jobId: String,
    val link: String,
    val type: String,
    val quantity: Int,
    val fixCoinJob: Int,
    val pricePerAfterCost: Int
)

sealed class GolikeTikTokJobResult {
    data class Success(val job: GolikeTikTokJob) : GolikeTikTokJobResult()
    /** "success": true nhưng "data" rỗng/null - hiện KHÔNG CÒN job nào cho acc này lúc này. */
    object NoJobAvailable : GolikeTikTokJobResult()
    data class Error(val message: String) : GolikeTikTokJobResult()
}

/**
 * Gọi THẬT GET https://gateway.golike.net/api/advertising/publishers/tiktok/jobs để lấy job
 * TikTok tiếp theo cho 1 acc - gọi SAU KHI đã chuyển đúng acc trong TikTok (xem
 * GolikeJobRunnerOverlayService), dùng ID NỘI BỘ GoLike của acc đó (không phải @handle).
 *
 * LƯU Ý: request thật quan sát được còn có 1 header tên "sig" (chuỗi ký tự dài, có vẻ là
 * chữ ký/hash để chống giả mạo request) mà mình CHƯA rõ cách tính ra - hàm này hiện KHÔNG
 * gửi header đó. Nếu API trả lỗi kiểu "chữ ký không hợp lệ"/"thiếu sig" thì đây chính là lý
 * do - cần bạn cho biết thêm cách web app.golike.net tính ra giá trị "sig" đó (có thể phải
 * xem code JS của web) thì mới gắn được.
 */
object GolikeTikTokJobRepository {

    suspend fun fetchNextJob(rawToken: String, golikeAccountId: Long): GolikeTikTokJobResult {
        val token = rawToken.trim()
        if (token.isBlank()) return GolikeTikTokJobResult.Error("Chưa có token GoLike")

        val authHeader = if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

        return try {
            val response = GolikeRetrofitClient.api.getNextTikTokJob(authHeader, golikeAccountId)
            val json = response.body()

            if (!response.isSuccessful) {
                val errorText = response.errorBody()?.string()
                val message = errorText?.let { text ->
                    runCatching {
                        com.google.gson.JsonParser.parseString(text).asJsonObject
                            .get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    }.getOrNull()
                }
                return GolikeTikTokJobResult.Error(message ?: "Lỗi lấy job (mã HTTP: ${response.code()})")
            }

            val success = json?.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            val data = json?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

            if (!success || data == null) {
                val message = json?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                return if (message.isNullOrBlank()) {
                    GolikeTikTokJobResult.NoJobAvailable
                } else {
                    GolikeTikTokJobResult.Error(message)
                }
            }

            val job = GolikeTikTokJob(
                jobId = data.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                link = data.get("link")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                type = data.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                quantity = data.get("quantity")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                fixCoinJob = data.get("fix_coin_job")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                pricePerAfterCost = data.get("price_per_after_cost")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            )
            GolikeTikTokJobResult.Success(job)
        } catch (e: Exception) {
            GolikeTikTokJobResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }
}
