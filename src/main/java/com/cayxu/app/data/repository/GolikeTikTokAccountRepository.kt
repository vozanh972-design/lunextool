package com.cayxu.app.data.repository

import com.cayxu.app.data.api.GolikeRetrofitClient
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

sealed class GolikeTikTokAccountsResult {
    data class Success(val handles: Set<String>) : GolikeTikTokAccountsResult()
    data class Error(val message: String) : GolikeTikTokAccountsResult()
}

/**
 * Gọi THẬT GET https://gateway.golike.net/api/tiktok-account bằng token Bearer đã đăng
 * nhập (KHÔNG hỏi lại token) - lấy danh sách acc TikTok đã có sẵn trong GoLike, để so
 * khớp với acc TikTok trong máy: acc nào KHÔNG có trong danh sách này thì hiện nút
 * "+ Thêm" bên cạnh (xem TikTokAccountCard trong GolikeTikTokScreen.kt).
 *
 * Vì không có tài liệu chính thức về cấu trúc JSON trả về, hàm bên dưới thử NHIỀU kiểu
 * bọc phổ biến (mảng thẳng ở gốc / bọc trong "data" / bọc kiểu phân trang Laravel
 * "data.data") và thử NHIỀU tên field định danh acc (username/unique_id/nickname/handle...).
 * Nếu so khớp sai, cần xem JSON thật trả về để chỉnh lại tên field.
 */
object GolikeTikTokAccountRepository {

    suspend fun fetchLinkedHandles(rawToken: String): GolikeTikTokAccountsResult {
        val token = rawToken.trim()
        if (token.isBlank()) return GolikeTikTokAccountsResult.Error("Chưa có token")

        val authHeader = if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

        return try {
            val response = GolikeRetrofitClient.api.getTikTokAccounts(authHeader)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val items = unwrapArray(body)
                val handles = items.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val obj = el.asJsonObject
                    firstNonBlank(obj, listOf("username", "unique_id", "handle", "nickname", "tiktok_username"))
                        ?.removePrefix("@")
                        ?.lowercase()
                }.toSet()
                GolikeTikTokAccountsResult.Success(handles)
            } else {
                val message = when (response.code()) {
                    401, 403 -> "Token không hợp lệ hoặc đã hết hạn"
                    else -> "Không lấy được danh sách (mã lỗi HTTP: ${response.code()})"
                }
                GolikeTikTokAccountsResult.Error(message)
            }
        } catch (e: Exception) {
            GolikeTikTokAccountsResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** Thử nhiều kiểu bọc phổ biến để tìm ra mảng dữ liệu thật bên trong response. */
    private fun unwrapArray(root: JsonElement): JsonArray {
        if (root.isJsonArray) return root.asJsonArray
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val data = obj.get("data")
            if (data != null) {
                if (data.isJsonArray) return data.asJsonArray
                if (data.isJsonObject) {
                    val nested = data.asJsonObject.get("data")
                    if (nested != null && nested.isJsonArray) return nested.asJsonArray
                }
            }
        }
        return JsonArray()
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
