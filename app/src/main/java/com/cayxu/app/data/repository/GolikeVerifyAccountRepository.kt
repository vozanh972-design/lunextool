package com.cayxu.app.data.repository

import com.cayxu.app.data.api.GolikeRetrofitClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed class GolikeVerifyAccountResult {
    data class Success(
        val message: String,
        val nickname: String? = null,
        val uniqueUsername: String? = null
    ) : GolikeVerifyAccountResult()
    data class Error(val message: String) : GolikeVerifyAccountResult()
}

/**
 * Gọi THẬT POST https://gateway.golike.net/api/tiktok-account/verify-account-id để hỏi
 * GoLike xem 1 tài khoản TikTok (theo username) đã follow đúng kênh chỉ định
 * (https://www.tiktok.com/@gosen.vietnam) hay chưa - gọi SAU KHI đã tự bấm Follow xong
 * (xem GolikeAddAccountOverlayService), để xác nhận với server trước khi coi acc đủ điều
 * kiện thêm vào GoLike.
 *
 * ĐÃ XÁC NHẬN QUA TEST THỰC TẾ: tên field đúng là "unique_id" (không phải "account_id" như
 * suy đoán ban đầu theo tên URL), và giá trị gửi lên KHÔNG có tiền tố "@" (vd handle
 * "@aduasd2423" thì gửi "aduasd2423" - gửi kèm "@" bị lỗi "trường unique_id không được bỏ
 * trống").
 *
 * QUAN TRỌNG - LỖI (400 và các mã lỗi khác) PHẢI ĐỌC "errorBody()", KHÔNG PHẢI "body()":
 * Retrofit chỉ điền "body()" khi request THÀNH CÔNG (2xx) - với response lỗi như 400, nội
 * dung JSON thật (vd { "message": "Account này đã được thêm với tài khoản losslo*** !" })
 * nằm trong "errorBody()", "body()" lúc đó LUÔN LÀ NULL. Trước đây code chỉ đọc "body()" nên
 * gặp lỗi 400 là không lấy được message thật, rơi vào 1 câu chung chung không rõ ràng. Giờ
 * đọc đúng "errorBody()" khi response không thành công để luôn hiện đúng message thật từ
 * GoLike, không còn tình trạng "không biết nên làm gì" nữa - lỗi gì hiện đúng lỗi đó.
 */
object GolikeVerifyAccountRepository {

    /** ĐÃ XÁC NHẬN QUA TEST THỰC TẾ - tên field đúng là "unique_id". */
    private const val KEY_ACCOUNT_ID = "unique_id"

    suspend fun verifyAccountId(rawToken: String, username: String): GolikeVerifyAccountResult {
        val token = rawToken.trim()
        if (token.isBlank()) return GolikeVerifyAccountResult.Error("Chưa có token GoLike")
        if (username.isBlank()) return GolikeVerifyAccountResult.Error("Thiếu username để xác minh")

        val authHeader = if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

        val body = JsonObject().apply {
            addProperty(KEY_ACCOUNT_ID, buildAccountIdValue(username))
        }

        return try {
            val response = GolikeRetrofitClient.api.verifyTikTokAccountId(authHeader, body)

            if (response.isSuccessful) {
                val json = response.body()
                val message = json?.get("message")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                val success = json?.get("success")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asBoolean == true

                if (success) {
                    // Ví dụ response thật lúc thành công (200):
                    // { "success": true, "message": "Thành công !",
                    //   "data": { "nickname": "gmn", "unique_username": "theanhgmn", ... } }
                    val data = json?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    val nickname = data?.get("nickname")?.takeIf { it.isJsonPrimitive }?.asString
                    val uniqueUsername = data?.get("unique_username")?.takeIf { it.isJsonPrimitive }?.asString
                    GolikeVerifyAccountResult.Success(
                        message = message ?: "Xác minh follow thành công",
                        nickname = nickname,
                        uniqueUsername = uniqueUsername
                    )
                } else {
                    // HTTP 200 nhưng "success": false - vẫn hiện đúng message thật nếu có.
                    GolikeVerifyAccountResult.Error(message ?: "Xác minh thất bại (không rõ lý do)")
                }
            } else {
                // response lỗi (400/401/500...) - PHẢI đọc errorBody(), không phải body()
                // (body() luôn null ở đây). Ví dụ thật (400):
                // { "success": false, "message": "Account này đã được thêm với tài khoản
                //   losslo*** !", "data": [], "error": [] }
                val errorText = response.errorBody()?.string()
                val message = errorText?.let { text ->
                    runCatching {
                        JsonParser.parseString(text).asJsonObject
                            .get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    }.getOrNull()
                }
                val fallback = "Xác minh thất bại (mã lỗi HTTP: ${response.code()})"
                GolikeVerifyAccountResult.Error(message ?: fallback)
            }
        } catch (e: Exception) {
            GolikeVerifyAccountResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** "@aduasd2423" hoặc "aduasd2423" -> "aduasd2423". API cần username KHÔNG có "@" (xác
     *  nhận qua test thực tế: gửi kèm "@" bị lỗi "trường unique_id không được bỏ trống"). */
    private fun buildAccountIdValue(username: String): String =
        username.trim().removePrefix("@")
}
