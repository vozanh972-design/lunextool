package com.cayxu.app.data.repository

import com.cayxu.app.data.api.XsmmRetrofitClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class XsmmTask(
    val taskId: String,
    val raw: JsonObject
)

sealed class XsmmTasksResult {
    data class Success(val tasks: List<XsmmTask>) : XsmmTasksResult()
    data class Error(val message: String) : XsmmTasksResult()
}

sealed class XsmmCompleteTaskResult {
    data class Success(val message: String) : XsmmCompleteTaskResult()
    data class Error(val message: String) : XsmmCompleteTaskResult()
}

/**
 * Gọi THẬT "GET/POST /api/taskapi/tasks..." của XSMM - lấy nhiệm vụ khả dụng theo loại (vd
 * "tiktok_follow") và báo hoàn thành nhiệm vụ.
 */
object XsmmTasksRepository {

    private fun authHeader(rawToken: String): String {
        val token = rawToken.trim()
        return if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"
    }

    private fun readError(errorBody: String?, fallback: String): String {
        val message = errorBody?.let { text ->
            runCatching {
                JsonParser.parseString(text).asJsonObject
                    .get("error")?.takeIf { it.isJsonPrimitive }?.asString
            }.getOrNull()
        }
        return message ?: fallback
    }

    /** [type]: vd "tiktok_follow", "tiktok_like", "tiktok_comment"...
     *  [typejob]: lọc theo hạng, vd "normal,better,best" (cách nhau dấu phẩy), để trống = tất cả. */
    suspend fun getTasks(rawToken: String, type: String, typejob: String? = null): XsmmTasksResult {
        return try {
            val response = XsmmRetrofitClient.api.getTasks(authHeader(rawToken), type, typejob)
            if (!response.isSuccessful) {
                return XsmmTasksResult.Error(readError(response.errorBody()?.string(), "Lỗi lấy nhiệm vụ (mã HTTP: ${response.code()})"))
            }
            val json = response.body()
            val errorField = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) return XsmmTasksResult.Error(errorField)

            // Chưa rõ 100% tên field mảng nhiệm vụ trong response thật (tài liệu không có ví
            // dụ cụ thể) - thử lần lượt các tên phổ biến nhất.
            val tasksArray = json?.get("tasks")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: json?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray

            val tasks = tasksArray?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                val id = obj.get("task_id")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: return@mapNotNull null
                XsmmTask(taskId = id, raw = obj)
            }.orEmpty()

            XsmmTasksResult.Success(tasks)
        } catch (e: Exception) {
            XsmmTasksResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** [type]: LOẠI nhiệm vụ, vd "facebook_like" (đúng tên loại của các task_id đang gửi lên,
     *  không được trộn nhiều loại khác nhau trong 1 lần gọi theo tài liệu API). */
    suspend fun completeTasks(rawToken: String, type: String, taskIds: List<String>): XsmmCompleteTaskResult {
        if (taskIds.isEmpty()) return XsmmCompleteTaskResult.Error("Không có nhiệm vụ nào để hoàn thành")

        val body = JsonObject().apply {
            addProperty("type", type)
            add("task_id", com.google.gson.JsonArray().apply { taskIds.forEach { add(it) } })
        }

        return try {
            val response = XsmmRetrofitClient.api.completeTasks(authHeader(rawToken), body)
            if (!response.isSuccessful) {
                return XsmmCompleteTaskResult.Error(readError(response.errorBody()?.string(), "Lỗi hoàn thành nhiệm vụ (mã HTTP: ${response.code()})"))
            }
            val json = response.body()
            val errorField = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) return XsmmCompleteTaskResult.Error(errorField)

            val message = json?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
            XsmmCompleteTaskResult.Success(message ?: "Đã hoàn thành nhiệm vụ")
        } catch (e: Exception) {
            XsmmCompleteTaskResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }
}
