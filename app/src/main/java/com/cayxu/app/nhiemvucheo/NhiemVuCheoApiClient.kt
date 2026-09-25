package com.cayxu.app.nhiemvucheo

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

data class NvcUser(
    val id: Long = 0,
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val role: String = "member",
    val status: String = "active",
    val coinBalance: String = "0",
    val lockedCoinBalance: String = "0"
)

sealed class NvcProfileResult {
    data class Success(val user: NvcUser) : NvcProfileResult()
    data class Error(val code: String, val message: String) : NvcProfileResult()
}

data class NvcTaskAssignment(
    val id: String = "",
    val actionFamily: String = "reaction", // reaction, comment, follow
    val reaction: String? = null,          // LIKE, LOVE, CARE, HAHA, WOW, SAD, ANGRY
    val commentText: String? = null,
    val targetUrl: String = "",
    val targetId: String = "",
    val rewardCoin: Long = 0L,
    val version: Int = 1,
    val remainingSeconds: Int = 120
)

sealed class NvcClaimResult {
    data class Success(val assignments: List<NvcTaskAssignment>) : NvcClaimResult()
    data class Empty(val message: String) : NvcClaimResult()
    data class Error(val code: String, val message: String) : NvcClaimResult()
}

sealed class NvcSubmitResult {
    data class Success(val earnedCoins: Long, val message: String, val newBalance: String? = null) : NvcSubmitResult()
    data class RetryWait(val code: String, val message: String) : NvcSubmitResult()
    data class Error(val code: String, val message: String) : NvcSubmitResult()
}

sealed class NvcSkipResult {
    data class Success(val message: String) : NvcSkipResult()
    data class Error(val code: String, val message: String) : NvcSkipResult()
}

/**
 * Client kết nối NhiemVuCheo Member Tool API (OpenAPI 3.0.3).
 * Base URL: https://nhiemvucheo.com/api/v1
 * Auth: Authorization: Bearer <nvc_sk_...>
 */
object NhiemVuCheoApiClient {
    const val BASE_URL = "https://nhiemvucheo.com/api/v1"
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 1. Lấy thông tin tài khoản và số dư coin.
     * GET /account/profile
     */
    suspend fun fetchProfile(token: String): NvcProfileResult = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext NvcProfileResult.Error("EMPTY_TOKEN", "Vui lòng nhập API Token")
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/profile")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")
                .header("User-Agent", "CayXu-Tool/1.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext NvcProfileResult.Error(
                        "EMPTY_RESPONSE",
                        "Máy chủ phản hồi rỗng (HTTP ${response.code})"
                    )
                }

                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (_: Exception) {
                    return@withContext NvcProfileResult.Error(
                        "PARSE_ERROR",
                        "Không thể đọc dữ liệu phản hồi từ máy chủ"
                    )
                }

                val isSuccess = json.has("success") && json.get("success").asBoolean
                if (isSuccess && json.has("data")) {
                    val dataObj = json.getAsJsonObject("data")
                    if (dataObj.has("user")) {
                        val userObj = dataObj.getAsJsonObject("user")
                        val user = NvcUser(
                            id = userObj.get("id")?.asLong ?: 0L,
                            username = userObj.get("username")?.asString.orEmpty(),
                            displayName = userObj.get("displayName")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                            avatarUrl = userObj.get("avatarUrl")?.takeIf { !it.isJsonNull }?.asString,
                            role = userObj.get("role")?.takeIf { !it.isJsonNull }?.asString ?: "member",
                            status = userObj.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "active",
                            coinBalance = userObj.get("coinBalance")?.asString ?: "0",
                            lockedCoinBalance = userObj.get("lockedCoinBalance")?.asString ?: "0"
                        )
                        return@withContext NvcProfileResult.Success(user)
                    }
                }

                // Error response
                var errCode = "UNKNOWN_ERROR"
                var errMsg = "Đăng nhập thất bại (HTTP ${response.code})"
                if (json.has("error") && json.get("error").isJsonObject) {
                    val errObj = json.getAsJsonObject("error")
                    errCode = errObj.get("code")?.asString ?: errCode
                    errMsg = errObj.get("message")?.asString ?: errMsg
                }

                return@withContext NvcProfileResult.Error(errCode, errMsg)
            }
        } catch (e: Exception) {
            return@withContext NvcProfileResult.Error(
                "NETWORK_ERROR",
                "Lỗi kết nối mạng: ${e.message ?: "Không xác định"}"
            )
        }
    }

    /**
     * 2. Claim (nhận) nhiệm vụ cho tài khoản/Page Facebook.
     * POST /account/tasks/claim
     * Header: Idempotency-Key: <UUID>
     */
    suspend fun claimTasks(
        token: String,
        uid: String,
        category: String,
        limit: Int = 1,
        quality: String = "all"
    ): NvcClaimResult = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext NvcClaimResult.Error("EMPTY_TOKEN", "Chưa có API Token NVC")
        }

        val idempotencyKey = UUID.randomUUID().toString()
        val bodyObj = JsonObject().apply {
            put("uid", uid.trim())
            put("category", category.trim())
            put("limit", limit.coerceIn(1, 15))
            put("quality", quality.trim())
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/tasks/claim")
                .header("Authorization", "Bearer $cleanToken")
                .header("Idempotency-Key", idempotencyKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "CayXu-Tool/1.0")
                .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext NvcClaimResult.Error(
                        "EMPTY_RESPONSE",
                        "Máy chủ phản hồi rỗng (HTTP ${response.code})"
                    )
                }

                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (_: Exception) {
                    return@withContext NvcClaimResult.Error(
                        "PARSE_ERROR",
                        "Không thể đọc phản hồi JSON (HTTP ${response.code})"
                    )
                }

                val isSuccess = json.has("success") && json.get("success").asBoolean
                if (isSuccess && json.has("data")) {
                    val dataObj = json.getAsJsonObject("data")
                    val assignmentsArr = dataObj.getAsJsonArray("assignments") ?: dataObj.getAsJsonArray("tasks")
                    if (assignmentsArr != null && assignmentsArr.size() > 0) {
                        val list = mutableListOf<NvcTaskAssignment>()
                        for (el in assignmentsArr) {
                            if (el.isJsonObject) {
                                val item = el.asJsonObject
                                val id = item.get("id")?.asString ?: item.get("taskId")?.asString ?: ""
                                val actionFamily = item.get("actionFamily")?.asString ?: category
                                val reaction = item.get("reaction")?.takeIf { !it.isJsonNull }?.asString?.uppercase()
                                val commentText = item.get("commentText")?.takeIf { !it.isJsonNull }?.asString
                                val targetUrl = item.get("targetUrl")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                                val targetId = item.get("targetId")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                                val rewardCoin = item.get("rewardCoin")?.asLong ?: 0L
                                val version = item.get("version")?.asInt ?: 1
                                val remainingSeconds = item.get("remainingSeconds")?.asInt ?: 120

                                list.add(
                                    NvcTaskAssignment(
                                        id = id,
                                        actionFamily = actionFamily,
                                        reaction = reaction,
                                        commentText = commentText,
                                        targetUrl = targetUrl,
                                        targetId = targetId,
                                        rewardCoin = rewardCoin,
                                        version = version,
                                        remainingSeconds = remainingSeconds
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) {
                            return@withContext NvcClaimResult.Success(list)
                        }
                    }
                    return@withContext NvcClaimResult.Empty("Hiện tại chưa có nhiệm vụ mới cho $category")
                }

                // Error response
                var errCode = "CLAIM_FAILED"
                var errMsg = "Không thể lấy nhiệm vụ (HTTP ${response.code})"
                if (json.has("error") && json.get("error").isJsonObject) {
                    val errObj = json.getAsJsonObject("error")
                    errCode = errObj.get("code")?.asString ?: errCode
                    errMsg = errObj.get("message")?.asString ?: errMsg
                }

                return@withContext NvcClaimResult.Error(errCode, errMsg)
            }
        } catch (e: Exception) {
            return@withContext NvcClaimResult.Error(
                "NETWORK_ERROR",
                "Lỗi kết nối khi nhận nhiệm vụ: ${e.message ?: "Không xác định"}"
            )
        }
    }

    /**
     * 3. Báo hoàn thành nhiệm vụ (Submit).
     * POST /account/tasks/{assignmentId}/submit
     * Header: Idempotency-Key: <UUID>
     */
    suspend fun submitTask(
        token: String,
        assignmentId: String,
        expectedVersion: Int
    ): NvcSubmitResult = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext NvcSubmitResult.Error("EMPTY_TOKEN", "Chưa có API Token NVC")
        }

        val idempotencyKey = UUID.randomUUID().toString()
        val bodyObj = JsonObject().apply {
            put("expectedVersion", expectedVersion)
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/tasks/$assignmentId/submit")
                .header("Authorization", "Bearer $cleanToken")
                .header("Idempotency-Key", idempotencyKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "CayXu-Tool/1.0")
                .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (_: Exception) {
                    null
                }

                if (response.code == 422) {
                    val errCode = json?.getAsJsonObject("error")?.get("code")?.asString.orEmpty()
                    val errMsg = json?.getAsJsonObject("error")?.get("message")?.asString.orEmpty()
                    if (errCode == "POINT_VERIFY_UID_MISSING") {
                        return@withContext NvcSubmitResult.RetryWait(
                            code = errCode,
                            message = errMsg.ifBlank { "Hệ thống đang quét xác minh, cần thử lại" }
                        )
                    }
                }

                val isSuccess = response.isSuccessful && (json?.get("success")?.asBoolean == true)
                if (isSuccess && json != null) {
                    val dataObj = json.getAsJsonObject("data")
                    val rewardCoin = dataObj?.get("rewardCoin")?.asLong
                        ?: dataObj?.get("earnedCoin")?.asLong
                        ?: dataObj?.get("coins")?.asLong
                        ?: 0L

                    val newBalance = dataObj?.get("coinBalance")?.asString
                        ?: dataObj?.getAsJsonObject("user")?.get("coinBalance")?.asString

                    return@withContext NvcSubmitResult.Success(
                        earnedCoins = rewardCoin,
                        message = "Hoàn thành nhiệm vụ thành công",
                        newBalance = newBalance
                    )
                }

                var errCode = "SUBMIT_FAILED"
                var errMsg = "Gửi hoàn thành thất bại (HTTP ${response.code})"
                if (json != null && json.has("error") && json.get("error").isJsonObject) {
                    val errObj = json.getAsJsonObject("error")
                    errCode = errObj.get("code")?.asString ?: errCode
                    errMsg = errObj.get("message")?.asString ?: errMsg
                }

                return@withContext NvcSubmitResult.Error(errCode, errMsg)
            }
        } catch (e: Exception) {
            return@withContext NvcSubmitResult.Error(
                "NETWORK_ERROR",
                "Lỗi kết nối khi gửi hoàn thành: ${e.message ?: "Không xác định"}"
            )
        }
    }

    /**
     * 4. Bỏ qua nhiệm vụ khi lỗi hoặc link bài viết die (Skip).
     * POST /account/tasks/{assignmentId}/skip
     * Header: Idempotency-Key: <UUID>
     */
    suspend fun skipTask(
        token: String,
        assignmentId: String,
        expectedVersion: Int,
        reason: String = "link_die"
    ): NvcSkipResult = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext NvcSkipResult.Error("EMPTY_TOKEN", "Chưa có API Token NVC")
        }

        val idempotencyKey = UUID.randomUUID().toString()
        val bodyObj = JsonObject().apply {
            put("expectedVersion", expectedVersion)
            put("reason", reason)
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/tasks/$assignmentId/skip")
                .header("Authorization", "Bearer $cleanToken")
                .header("Idempotency-Key", idempotencyKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "CayXu-Tool/1.0")
                .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (_: Exception) {
                    null
                }

                if (response.isSuccessful) {
                    return@withContext NvcSkipResult.Success("Đã bỏ qua nhiệm vụ")
                }

                var errCode = "SKIP_FAILED"
                var errMsg = "Bỏ qua nhiệm vụ thất bại (HTTP ${response.code})"
                if (json != null && json.has("error") && json.get("error").isJsonObject) {
                    val errObj = json.getAsJsonObject("error")
                    errCode = errObj.get("code")?.asString ?: errCode
                    errMsg = errObj.get("message")?.asString ?: errMsg
                }

                return@withContext NvcSkipResult.Error(errCode, errMsg)
            }
        } catch (e: Exception) {
            return@withContext NvcSkipResult.Error(
                "NETWORK_ERROR",
                "Lỗi kết nối khi bỏ qua nhiệm vụ: ${e.message ?: "Không xác định"}"
            )
        }
    }
}

private fun JsonObject.put(key: String, value: String) = addProperty(key, value)
private fun JsonObject.put(key: String, value: Number) = addProperty(key, value)
