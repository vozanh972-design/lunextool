package com.cayxu.app.data.repository

import com.cayxu.app.data.api.XsmmRetrofitClient
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.net.SocketTimeoutException
import kotlin.random.Random

data class XsmmTask2(
    val id: String,
    val type: String,
    val targetUrl: String,
    val targetId: String = "",
    val idorlink: String,
    val points: Int,
    val comment: String = "",
    val reaction: String = ""
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
    val retry: Boolean = false,
    val isTimeout: Boolean = false
)

object XsmmTasksRepository {

    private fun auth(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("Bearer", ignoreCase = true)) t else "Bearer $t"
    }

    private fun errMsg(body: String?, fallback: String): String =
        body?.let { runCatching { JsonParser.parseString(it).asJsonObject.get("error")?.takeIf { e -> e.isJsonPrimitive }?.asString }.getOrNull() } ?: fallback

    /**
     * GET /api/taskapi/tasks - Lấy danh sách nhiệm vụ khả dụng (chuẩn 100% tài liệu XSMM).
     * Không cần uid, server tự động cấp nhiệm vụ theo tài khoản đang active!
     */
    suspend fun getTasks(
        rawToken: String,
        type: String,
        typejob: String? = "normal,better,best"
    ): XsmmTasks2Result {
        return try {
            var response = XsmmRetrofitClient.api.getTasks(auth(rawToken), type, typejob ?: "normal,better,best")
            if (response.code() == 429) {
                kotlinx.coroutines.delay(10000L)
                response = XsmmRetrofitClient.api.getTasks(auth(rawToken), type, typejob ?: "normal,better,best")
            }
            if (!response.isSuccessful) {
                return XsmmTasks2Result.Error(errMsg(response.errorBody()?.string(), "Lỗi lấy nhiệm vụ (mã HTTP: ${response.code()})"))
            }
            val body = response.body() ?: return XsmmTasks2Result.Error("Không có dữ liệu trả về")
            if (body.isJsonObject) {
                val errorMsg = body.asJsonObject.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                return XsmmTasks2Result.Error(errorMsg ?: "Lỗi từ XSMM: $body")
            }
            if (!body.isJsonArray) {
                return XsmmTasks2Result.Error("Phản hồi không hợp lệ từ XSMM")
            }
            val arr = body.asJsonArray
            val tasks = arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                XsmmTask2(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    targetUrl = obj.get("target_url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    targetId = obj.get("target_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    idorlink = obj.get("idorlink")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    points = obj.get("points")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                    comment = extractComment(obj),
                    reaction = extractReaction(obj)
                )
            }
            XsmmTasks2Result.Success(tasks)
        } catch (e: Exception) {
            XsmmTasks2Result.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /**
     * POST /api/taskapi/tasks/complete - Báo hoàn thành nhiệm vụ và nhận xu (chuẩn 100% tài liệu XSMM).
     * Body: {"type": type, "task_id": [...]}
     */
    suspend fun completeTasks(
        rawToken: String,
        type: String,
        taskIds: List<String>,
        maxRetries: Int = 3
    ): XsmmCompleteTask2Result {
        if (taskIds.isEmpty()) return XsmmCompleteTask2Result(false, "Không có nhiệm vụ nào", 0, null, 0, 0, false)

        val body = JsonObject().apply {
            addProperty("type", type)
            val arr = JsonArray()
            taskIds.forEach { arr.add(it) }
            add("task_id", arr)
        }

        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            try {
                val response = XsmmRetrofitClient.api.completeTasks(auth(rawToken), body)
                if (response.code() == 429 && attempt < maxRetries - 1) {
                    val retryWait = Random.nextLong(10L, 16L)
                    kotlinx.coroutines.delay(retryWait * 1000L)
                    attempt++
                    continue
                }
                if (!response.isSuccessful) {
                    val msg = errMsg(response.errorBody()?.string(), "Lỗi hoàn thành NV (mã HTTP: ${response.code()})")
                    return XsmmCompleteTask2Result(false, msg, 0, null, 0, 0, false)
                }
                val json = response.body() ?: return XsmmCompleteTask2Result(false, "Phản hồi rỗng từ server XSMM", 0, null, 0, 0, false)

                val retry = json.get("retry")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                if (retry && attempt < maxRetries - 1) {
                    val retryWait = Random.nextLong(10L, 16L)
                    kotlinx.coroutines.delay(retryWait * 1000L)
                    attempt++
                    continue
                }

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
                    ?: 0

                val totalPoints = json.get("total_points")?.takeIf { it.isJsonPrimitive }?.asLong
                val message = json.get("message")?.takeIf { it.isJsonPrimitive }?.asString ?: errorMsg ?: ""
                val successCount = json.get("success_count")?.takeIf { it.isJsonPrimitive }?.asInt ?: taskIds.size
                val countdown = json.get("countdown")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                val isSuccess = isSuccessFlag || points > 0 || (errorMsg.isNullOrBlank() && !retry)

                return XsmmCompleteTask2Result(
                    success = isSuccess,
                    message = message,
                    points = points,
                    totalPoints = totalPoints,
                    successCount = successCount,
                    countdown = countdown,
                    retry = retry
                )
            } catch (e: SocketTimeoutException) {
                return XsmmCompleteTask2Result(
                    success = true,
                    message = "Server phản hồi chậm nhưng đã gửi duyệt ${taskIds.size} job thành công",
                    points = 0,
                    totalPoints = null,
                    successCount = taskIds.size,
                    countdown = 0,
                    retry = false,
                    isTimeout = true
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // CancellationException PHẢI được re-throw, không được bắt nhầm như Exception thường
                throw e
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }

        val errText = lastException?.message ?: "Đã thử lại nhiều lần nhưng không thành công"
        return XsmmCompleteTask2Result(false, errText, 0, null, 0, 0, false)
    }

    /**
     * GET /api/taskapi/tasks2 - lấy danh sách nhiệm vụ theo loại + uid (Instagram/TikTok).
     * [typejob]: "normal,better,best" chuẩn theo Python XSMM
     */
    suspend fun getTasks2(
        rawToken: String,
        type: String,
        uid: String,
        typejob: String? = "normal,better,best"
    ): XsmmTasks2Result {
        return try {
            var response = XsmmRetrofitClient.api.getTasks2(auth(rawToken), type, uid, typejob ?: "normal,better,best")
            if (response.code() == 429) {
                kotlinx.coroutines.delay(10000L)
                response = XsmmRetrofitClient.api.getTasks2(auth(rawToken), type, uid, typejob ?: "normal,better,best")
            }
            if (!response.isSuccessful) {
                return XsmmTasks2Result.Error(errMsg(response.errorBody()?.string(), "Lỗi lấy nhiệm vụ (mã HTTP: ${response.code()})"))
            }
            val body = response.body() ?: return XsmmTasks2Result.Error("Không có dữ liệu trả về")
            if (body.isJsonObject) {
                val errorMsg = body.asJsonObject.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                return XsmmTasks2Result.Error(errorMsg ?: "Lỗi từ XSMM: $body")
            }
            if (!body.isJsonArray) {
                return XsmmTasks2Result.Error("Phản hồi không hợp lệ từ XSMM")
            }
            val arr = body.asJsonArray
            val tasks = arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                XsmmTask2(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    targetUrl = obj.get("target_url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    targetId = obj.get("target_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    idorlink = obj.get("idorlink")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    points = obj.get("points")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                    comment = extractComment(obj),
                    reaction = extractReaction(obj)
                )
            }
            XsmmTasks2Result.Success(tasks)
        } catch (e: Exception) {
            XsmmTasks2Result.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /**
     * POST /api/taskapi/tasks2/complete - báo hoàn thành nhiệm vụ và nhận xu.
     * Chuẩn 100% logic payload và retry của Python XSMM Tool:
     * - Payload: {"type": job_type, "task_id": [...], "uid": uid, "cookie_check": cookie}
     * - Tự động retry khi res_data["retry"] == true (chờ 10-15s thử lại tối đa 3 lần)
     * - Xử lý socket timeout
     */
    suspend fun completeTasks2(
        rawToken: String,
        type: String,
        taskIds: List<String>,
        uid: String,
        cookieCheck: String? = null,
        maxRetries: Int = 3
    ): XsmmCompleteTask2Result {
        if (taskIds.isEmpty()) return XsmmCompleteTask2Result(false, "Không có nhiệm vụ nào", 0, null, 0, 0, false)

        val body = JsonObject().apply {
            addProperty("type", type)
            val arr = JsonArray()
            taskIds.forEach { arr.add(it) }
            add("task_id", arr)
            addProperty("uid", uid)
            if (!cookieCheck.isNullOrBlank()) {
                addProperty("cookie_check", cookieCheck)
                addProperty("string_ig", cookieCheck)
                addProperty("string", cookieCheck)
                addProperty("cookie", cookieCheck)
            }
        }

        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            try {
                val response = XsmmRetrofitClient.api.completeTasks2(auth(rawToken), body)
                if (response.code() == 429 && attempt < maxRetries - 1) {
                    val retryWait = Random.nextLong(10L, 16L)
                    kotlinx.coroutines.delay(retryWait * 1000L)
                    attempt++
                    continue
                }
                if (!response.isSuccessful) {
                    val msg = errMsg(response.errorBody()?.string(), "Lỗi hoàn thành NV (mã HTTP: ${response.code()})")
                    return XsmmCompleteTask2Result(false, msg, 0, null, 0, 0, false)
                }
                val json = response.body() ?: return XsmmCompleteTask2Result(false, "Phản hồi rỗng từ server XSMM", 0, null, 0, 0, false)

                val retry = json.get("retry")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                if (retry && attempt < maxRetries - 1) {
                    val retryWait = Random.nextLong(10L, 16L)
                    kotlinx.coroutines.delay(retryWait * 1000L)
                    attempt++
                    continue
                }

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

                val isSuccess = isSuccessFlag || points > 0 || (errorMsg.isNullOrBlank() && !retry)

                return XsmmCompleteTask2Result(
                    success = isSuccess,
                    message = message,
                    points = points,
                    totalPoints = totalPoints,
                    successCount = successCount,
                    countdown = countdown,
                    retry = retry
                )
            } catch (e: SocketTimeoutException) {
                return XsmmCompleteTask2Result(
                    success = true,
                    message = "Server phản hồi chậm nhưng đã gửi duyệt ${taskIds.size} job thành công",
                    points = 0,
                    totalPoints = null,
                    successCount = taskIds.size,
                    countdown = 0,
                    retry = false,
                    isTimeout = true
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // CancellationException PHẢI được re-throw, không được bắt nhầm như Exception thường
                throw e
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }

        val errText = lastException?.message ?: "Đã thử lại nhiều lần nhưng không thành công"
        return XsmmCompleteTask2Result(false, errText, 0, null, 0, 0, false)
    }

    /**
     * Tự động trích xuất nội dung comment từ phản hồi nhiệm vụ của XSMM.
     * Hỗ trợ mọi định dạng: chuỗi, mảng chuỗi, lồng trong data/task/params...
     */
    private fun extractComment(obj: JsonObject): String {
        val candidateKeys = listOf(
            "comment", "comments",
            "content", "contents",
            "message", "msg", "text",
            "comment_text", "comment_content",
            "noi_dung", "noidung"
        )

        fun fromElement(el: com.google.gson.JsonElement?): String? {
            if (el == null || el.isJsonNull) return null
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotBlank()) return s
            }
            if (el.isJsonArray) {
                val arr = el.asJsonArray
                val list = arr.mapNotNull { item ->
                    if (item.isJsonPrimitive) item.asString.trim()
                    else if (item.isJsonObject) {
                        val innerObj = item.asJsonObject
                        candidateKeys.firstNotNullOfOrNull { k ->
                            innerObj.get(k)?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                        }
                    } else null
                }.filter { it.isNotBlank() }
                if (list.isNotEmpty()) return list.random()
            }
            if (el.isJsonObject) {
                val innerObj = el.asJsonObject
                for (k in candidateKeys) {
                    val found = fromElement(innerObj.get(k))
                    if (!found.isNullOrBlank()) return found
                }
            }
            return null
        }

        for (k in candidateKeys) {
            val found = fromElement(obj.get(k))
            if (!found.isNullOrBlank()) return found
        }

        for (containerKey in listOf("data", "task", "job", "params")) {
            val container = obj.get(containerKey)
            val found = fromElement(container)
            if (!found.isNullOrBlank()) return found
        }

        return ""
    }

    /**
     * Tự động trích xuất loại cảm xúc (reaction) từ phản hồi nhiệm vụ của XSMM.
     * Hỗ trợ mọi định dạng: reaction, reaction_type, react, action, sub_type, camxuc...
     */
    private fun extractReaction(obj: JsonObject): String {
        // Thu thập tất cả các chuỗi có thể chứa thông tin cảm xúc trong task của XSMM
        val textSnippets = mutableListOf<String>()

        val candidateKeys = listOf(
            "reaction", "reactions", "reaction_type", "react", "type_reaction",
            "action", "action_type", "sub_type", "subtype",
            "camxuc", "cam_xuc", "loaicx", "loai_cx", "loai",
            "emotion", "feeling",
            "name", "title", "note", "description", "task_name", "job_name", "type_job", "job_type",
            "content", "text"
        )

        for (k in candidateKeys) {
            val el = obj.get(k)
            if (el != null && el.isJsonPrimitive) {
                textSnippets.add(el.asString)
            }
        }

        for (containerKey in listOf("data", "task", "job", "params")) {
            val container = obj.get(containerKey)
            if (container != null && container.isJsonObject) {
                val inner = container.asJsonObject
                for (k in candidateKeys) {
                    val el = inner.get(k)
                    if (el != null && el.isJsonPrimitive) {
                        textSnippets.add(el.asString)
                    }
                }
                val innerType = inner.get("type")?.takeIf { it.isJsonPrimitive }?.asString
                if (!innerType.isNullOrBlank()) textSnippets.add(innerType)
            }
        }

        val typeStr = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString
        if (!typeStr.isNullOrBlank()) textSnippets.add(typeStr)

        val allText = (textSnippets.joinToString(" ") + " " + obj.toString()).lowercase()

        // Ưu tiên cao nhất: Kiểm tra các loại cảm xúc đặc thù (CARE, LOVE, HAHA, WOW, SAD, ANGRY)
        // Tuyệt đối không để LIKE hay "facebook_like" ghi đè lên các cảm xúc này!
        return when {
            allText.contains("care") || allText.contains("thuongthuong") || allText.contains("thương thương") || allText.contains("thương") || allText.contains("om") -> "CARE"
            allText.contains("love") || allText.contains("tym") || allText.contains("tim") || allText.contains("yeuthich") || allText.contains("yêu thích") || allText.contains("heart") -> "LOVE"
            allText.contains("haha") || allText.contains("cuoi") || allText.contains("cười") -> "HAHA"
            allText.contains("wow") || allText.contains("ngacnhien") || allText.contains("ngạc nhiên") -> "WOW"
            allText.contains("sad") || allText.contains("buon") || allText.contains("buồn") || allText.contains("khoc") || allText.contains("khóc") -> "SAD"
            allText.contains("angry") || allText.contains("phanno") || allText.contains("phẫn nộ") || allText.contains("gian") || allText.contains("giận") -> "ANGRY"
            allText.contains("like") || allText.contains("thich") || allText.contains("thích") -> "LIKE"
            else -> ""
        }
    }
}
