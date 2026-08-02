package com.cayxu.app.data.repository

import com.cayxu.app.data.api.GolikeRetrofitClient
import com.google.gson.JsonObject

data class GolikeUserInfo(
    val name: String,
    val handle: String,
    val email: String,
    val coin: String,
    val tasksToday: String,
    val rewardToday: String
)

sealed class GolikeLoginResult {
    data class Success(val info: GolikeUserInfo) : GolikeLoginResult()
    data class Error(val message: String) : GolikeLoginResult()
}

/**
 * Gọi THẬT GET https://gateway.golike.net/api/users/me với token Bearer người dùng tự
 * dán vào màn Đăng nhập Golike - HOÀN TOÀN ĐỘC LẬP với AuthRepository (đó là license key
 * riêng của app CayXu, không liên quan gì tới Golike).
 *
 * Vì không có tài liệu chính thức về đúng cấu trúc JSON trả về, hàm đọc dữ liệu bên dưới
 * thử NHIỀU tên field phổ biến (name/full_name, username/handle/nickname, email,
 * money/coin/balance/xu, tasks_today, reward_today...) và tự bóc lớp "data" bọc ngoài nếu
 * có. "NV hôm nay"/"Thưởng hôm nay" mặc định "0" nếu API không trả field tương ứng - CẦN
 * xem JSON thật trả về để chỉnh đúng tên field nếu 2 số này hiện sai.
 */
object GolikeAuthRepository {

    suspend fun fetchMe(rawToken: String): GolikeLoginResult {
        val token = rawToken.trim()
        if (token.isBlank()) return GolikeLoginResult.Error("Vui lòng dán token")

        val authHeader = if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

        return try {
            val response = GolikeRetrofitClient.api.getMe(authHeader)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val data = unwrapData(body)
                val name = firstNonBlank(data, listOf("name", "full_name", "fullname", "display_name"))
                    ?: "Tài khoản Golike"
                val handle = firstNonBlank(data, listOf("username", "handle", "nickname")).orEmpty()
                val email = firstNonBlank(data, listOf("email")).orEmpty()
                val coin = firstNonBlank(data, listOf("money", "coin", "coins", "balance", "xu", "wallet"))
                    ?: "0"
                val tasksToday = firstNonBlank(data, listOf("tasks_today", "task_today", "today_tasks", "nv_hom_nay"))
                    ?: "0"
                val rewardToday = firstNonBlank(data, listOf("reward_today", "bonus_today", "today_reward", "thuong_hom_nay"))
                    ?: "0"
                GolikeLoginResult.Success(GolikeUserInfo(name, handle, email, coin, tasksToday, rewardToday))
            } else {
                val message = when (response.code()) {
                    401, 403 -> "Token không hợp lệ hoặc đã hết hạn"
                    else -> "Không thể đăng nhập (mã lỗi HTTP: ${response.code()})"
                }
                GolikeLoginResult.Error(message)
            }
        } catch (e: Exception) {
            GolikeLoginResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** Nhiều API bọc dữ liệu thật trong "data", số khác trả thẳng ở gốc - thử cả 2 kiểu. */
    private fun unwrapData(root: JsonObject): JsonObject {
        val dataEl = root.get("data")
        return if (dataEl != null && dataEl.isJsonObject) dataEl.asJsonObject else root
    }

    private fun firstNonBlank(obj: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val el = obj.get(key) ?: continue
            if (el.isJsonNull) continue
            val text = if (el.isJsonPrimitive) el.asString else el.toString()
            if (text.isNotBlank()) return text
        }
        return null
    }
}
