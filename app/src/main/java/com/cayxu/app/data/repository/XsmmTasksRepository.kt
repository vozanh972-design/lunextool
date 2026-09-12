package com.cayxu.app.data.repository

import com.cayxu.app.data.api.XsmmRetrofitClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class XsmmTask2(
    val id: String,
    val type: String,
    val targetUrl: String,
    val idorlink: String,
    val points: Int
)

sealed class XsmmTasks2Result {
    data class Success(val tasks: List<XsmmTask2>) : XsmmTasks2Result()
    data class Error(val message: String) : XsmmTasks2Result()
}

data class XsmmCompleteTask2Result(
    val success: Boolean,
    val message: String,
    val points: Int,
    val successCount: Int,
    val countdown: Int,
    val retry: Boolean
)

object XsmmTasksRepository {

    private fun auth(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("Bearer", ignoreCase = true)) t else "Bearer $t"
    }

    private fun errMsg(body: String?, fallback: String): String =
        body?.let { runCatching { JsonParser.parseString(it).asJsonObject.get("error")?.takeIf { e -> e.isJsonPrimitive }?.asString }.getOrNull() } ?: fallback

    /**
     * GET /api/taskapi/tasks2 - lấy danh sách nhiệm vụ theo loại + uid TikTok.
     * [type]: vd "tiktok_follow", [uid]: account_id TikTok đang chạy (account_id trong acc XSMM).
     */
    suspend fun getTasks2(rawToken: String, type: String, uid: String, typejob: String? = null): XsmmTasks2Result {
        return try {
            val response = XsmmRetrofitClient.api.getTasks2(auth(rawToken), type, uid, typejob)
            if (!response.isSuccessful) {
                return XsmmTasks2Result.Error(errMsg(response.errorBody()?.string(), "Lỗi lấy nhiệm vụ (mã HTTP: ${response.code()})"))
            }
            val arr = response.body() ?: return XsmmTasks2Result.Error("Không có dữ liệu trả về")
            val tasks = arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                XsmmTask2(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    targetUrl = obj.get("target_url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    idorlink = obj.get("idorlink")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    points = obj.get("points")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                )
            }
            XsmmTasks2Result.Success(tasks)
        } catch (e: Exception) {
            XsmmTasks2Result.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /**
     * POST /api/taskapi/tasks2/complete - báo hoàn thành nhiệm vụ.
     * [uid]: account_id TikTok đang chạy (bắt buộc theo tài liệu API mới).
     */
    suspend fun completeTasks2(rawToken: String, type: String, taskIds: List<String>, uid: String): XsmmCompleteTask2Result {
        if (taskIds.isEmpty()) return XsmmCompleteTask2Result(false, "Không có nhiệm vụ nào", 0, 0, 0, false)
        val body = JsonObject().apply {
            addProperty("type", type)
            add("task_id", com.google.gson.JsonArray().apply { taskIds.forEach { add(it) } })
            addProperty("uid", uid)
        }
        return try {
            val response = XsmmRetrofitClient.api.completeTasks2(auth(rawToken), body)
            if (!response.isSuccessful) {
                val msg = errMsg(response.errorBody()?.string(), "Lỗi hoàn thành NV (mã HTTP: ${response.code()})")
                return XsmmCompleteTask2Result(false, msg, 0, 0, 0, false)
            }
            val json = response.body()
            val err = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!err.isNullOrBlank()) return XsmmCompleteTask2Result(false, err, 0, 0, 0, false)
            XsmmCompleteTask2Result(
                success = true,
                message = json?.get("message")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                points = json?.get("points")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                successCount = json?.get("success_count")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                countdown = json?.get("countdown")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                retry = json?.get("retry")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            )
        } catch (e: Exception) {
            XsmmCompleteTask2Result(false, e.message ?: "Lỗi kết nối mạng", 0, 0, 0, false)
        }
    }
}
