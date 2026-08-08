package com.cayxu.app.data.repository

import com.cayxu.app.data.api.GolikeRetrofitClient
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

sealed class GolikeTikTokAccountsResult {
    /** [idsByHandle]: map @handle (lowercase, không @) -> ID NỘI BỘ của GoLike (field "id"
     *  trong response, KHÁC với unique_id của TikTok) - cần để gọi API lấy job
     *  (?account_id=...). */
    data class Success(val handles: Set<String>, val idsByHandle: Map<String, Long>) : GolikeTikTokAccountsResult()
    data class Error(val message: String) : GolikeTikTokAccountsResult()
}

/**
 * Gọi THẬT GET https://gateway.golike.net/api/tiktok-account bằng token Bearer đã đăng
 * nhập (KHÔNG hỏi lại token) - lấy danh sách acc TikTok đã có sẵn trong GoLike, để so
 * khớp với acc TikTok trong máy: acc nào KHÔNG có trong danh sách này thì hiện nút
 * "+ Thêm" bên cạnh (xem TikTokAccountCard trong GolikeTikTokScreen.kt).
 *
 * ĐÃ XÁC NHẬN QUA RESPONSE THẬT: field đúng để so khớp @handle TikTok là "unique_username"
 * (vd "thu.ha4217", "kjkio8"). Field "username" trong response này KHÔNG PHẢI @handle TikTok
 * - đó là TÊN ĐĂNG NHẬP GOLIKE, giống hệt nhau ở MỌI dòng (vd "losslow12" lặp lại ở tất cả
 * acc) - nếu match theo field đó sẽ luôn sai/không bao giờ khớp đúng acc nào.
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
                val handles = mutableSetOf<String>()
                val idsByHandle = mutableMapOf<String, Long>()
                items.forEach { el ->
                    if (!el.isJsonObject) return@forEach
                    val obj = el.asJsonObject
                    // "unique_username" là field ĐÚNG (đã xác nhận qua response thật) - các
                    // field còn lại chỉ là dự phòng cho trường hợp API đổi cấu trúc sau này.
                    // KHÔNG dùng "username" (đó là tên đăng nhập GoLike, không phải TikTok).
                    val handle = firstNonBlank(obj, listOf("unique_username", "handle", "tiktok_username"))
                        ?.removePrefix("@")
                        ?.lowercase()
                        ?: return@forEach
                    handles.add(handle)
                    val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asLong
                    if (id != null) idsByHandle[handle] = id
                }
                GolikeTikTokAccountsResult.Success(handles, idsByHandle)
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
