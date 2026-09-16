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
    val totalPoints: Long? = null,
    val successCount: Int = 0,
    val countdown: Int = 0,
    val retry: Boolean = false
)

object XsmmTasksRepository {

    private fun auth(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("Bearer", ignoreCase = true)) t else "Bearer $t"
    }

    private fun errMsg(body: String?, fallback: String): String =
        body?.let { runCatching { JsonParser.parseString(it).asJsonObject.get("error")?.takeIf { e -> e.isJsonPrimitive }?.asString }.getOrNull() } ?: fallback

    /**
     * GET /api/taskapi/tasks2 - lấy danh sách nhiệm vụ theo loại + uid TikTok/Instagram.
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
     * POST /api/taskapi/tasks2/complete - báo hoàn thành nhiệm vụ và nhận xu.
     */
    suspend fun completeTasks2(
        rawToken: String,
        type: String,
        taskIds: List<String>,
        uid: String,
        cookieCheck: String? = null
    ): XsmmCompleteTask2Result {
        if (taskIds.isEmpty()) return XsmmCompleteTask2Result(false, "Không có nhiệm vụ nào", 0, null, 0, 0, false)
        
        val body = JsonObject().apply {
            addProperty("type", type)
            val arr = com.google.gson.JsonArray()
            taskIds.forEach { arr.add(it) }
            add("task_id", arr)
            if (taskIds.size == 1) {
                addProperty("id", taskIds.first())
            }
            addProperty("uid", uid)
            if (type.contains("instagram", ignoreCase = true) || type.startsWith("ig", ignoreCase = true)) {
                addProperty("ig", uid)
                addProperty("platform", "ig")
            }
            if (!cookieCheck.isNullOrBlank()) {
                addProperty("cookie_check", cookieCheck)
            }
        }

        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val response = XsmmRetrofitClient.api.completeTasks2(auth(rawToken), body)
                if (!response.isSuccessful) {
                    val msg = errMsg(response.errorBody()?.string(), "Lỗi hoàn thành NV (mã HTTP: ${response.code()})")
                    return XsmmCompleteTask2Result(false, msg, 0, null, 0, 0, false)
                }
                val json = response.body() ?: return XsmmCompleteTask2Result(false, "Phản hồi rỗng từ server XSMM", 0, null, 0, 0, false)

                val hasExplicitError = json.has("error") && !json.get("error").isJsonNull
                val errorMsg = if (hasExplicitError) {
                    val errEl = json.get("error")
                    if (errEl.isJsonPrimitive) errEl.asString else errEl.toString()
                } else null

                val statusStr = json.get("status")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val statusCode = json.get("status")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
                val isSuccessFlag = json.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == true ||
                    statusStr.equals("success", ignoreCase = true) ||
                    statusCode == 200

                if (!errorMsg.isNullOrBlank() && !isSuccessFlag && json.get("points") == null) {
                    return XsmmCompleteTask2Result(false, errorMsg, 0, null, 0, 0, false)
                }

                val points = json.get("points")?.takeIf { it.isJsonPrimitive }?.asInt
                    ?: json.get("earned_points")?.takeIf { it.isJsonPrimitive }?.asInt
                    ?: json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("points")?.takeIf { it.isJsonPrimitive }?.asInt
                    ?: json.get("bonus")?.takeIf { it.isJsonPrimitive }?.asInt
                    ?: 0

                val totalPoints = json.get("total_points")?.takeIf { it.isJsonPrimitive }?.asLong
                    ?: json.get("user")?.takeIf { it.isJsonObject }?.asJsonObject?.get("points")?.takeIf { it.isJsonPrimitive }?.asLong
                    ?: json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject?.get("points")?.takeIf { it.isJsonPrimitive }?.asLong

                val message = json.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: json.get("msg")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: errorMsg
                    ?: ""

                val successCount = json.get("success_count")?.takeIf { it.isJsonPrimitive }?.asInt ?: taskIds.size
                val countdown = json.get("countdown")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                val retry = json.get("retry")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

                val isSuccess = isSuccessFlag || points > 0 || errorMsg.isNullOrBlank()

                return XsmmCompleteTask2Result(
                    success = isSuccess,
                    message = message,
                    points = points,
                    totalPoints = totalPoints,
                    successCount = successCount,
                    countdown = countdown,
                    retry = retry
                )
            } catch (e: Exception) {
                lastException = e
                if (attempt < 3) {
                    kotlinx.coroutines.delay(1500L * attempt)
                }
            }
        }
        val errText = lastException?.message ?: "Lỗi kết nối server XSMM (Timeout)"
        return XsmmCompleteTask2Result(false, errText, 0, null, 0, 0, false)
    }
}
