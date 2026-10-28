package com.cayxu.app.ui.f4f

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Keep
data class F4fUserInfo(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("username") val username: String = "",
    @SerializedName("balanceText") val balanceText: String = "0 coin",
    @SerializedName("balancePoints") val balancePoints: Long = 0L,
    @SerializedName("token") val token: String = "",
    @SerializedName("levelName") val levelName: String = "Thành viên",
)

@Keep
data class F4fRemoteAccount(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("status") val status: String,
    @SerializedName("isLive") val isLive: Boolean,
    @SerializedName("avatarUrl") val avatarUrl: String = "",
    @SerializedName("type") val type: String = "",
)

@Keep
data class F4fJobInfo(
    @SerializedName("jobId") val jobId: Long,
    @SerializedName("targetLink") val targetLink: String,
    @SerializedName("type") val type: String = "tiktok",
    @SerializedName("typeJob") val typeJob: String = "follow",
    @SerializedName("rewardCoins") val rewardCoins: Long = 0L,
    @SerializedName("accountId") val accountId: Long = 0L,
    @SerializedName("originalData") val originalData: String = "",
    @SerializedName("uid") val uid: String = "",
    @SerializedName("objectId") val objectId: String = "",
)

sealed class F4fApiResult<out T> {
    data class Success<T>(val data: T) : F4fApiResult<T>()
    data class Error(val message: String) : F4fApiResult<Nothing>()
}

@Keep
object F4fApiClient {
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun parseCoins(balanceStr: String?): Long {
        if (balanceStr.isNullOrBlank()) return 0L
        val clean = balanceStr.replace("coin", "", ignoreCase = true)
            .replace(",", "")
            .replace(".", "")
            .trim()
        return clean.toLongOrNull() ?: 0L
    }

    /**
     * Tạo Request.Builder chuẩn với các header F4F được giải mã qua Native C++
     */
    private fun buildF4fRequest(url: String, cleanToken: String): Request.Builder {
        val b = Request.Builder()
            .url(url)
            .header("Accept", F4fNativeBridge.fetchAcceptJson())
            .header("Origin", F4fNativeBridge.fetchOrigin())
            .header("Referer", F4fNativeBridge.fetchReferer())
            .header("User-Agent", F4fNativeBridge.fetchUserAgent())
        if (cleanToken.isNotBlank()) {
            b.header("Authorization", "Bearer $cleanToken")
        }
        return b
    }

    /**
     * Lấy thông tin người dùng từ Bearer Token (chuỗi token lưu trong localStorage của web: 42870|...)
     */
    suspend fun fetchMe(rawToken: String): F4fApiResult<F4fUserInfo> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa nhập token")

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}${F4fNativeBridge.fetchPathAuthMe()}"
        val request = buildF4fRequest(fullUrl, cleanToken)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi không hợp lệ từ máy chủ")

                val statusCode = json.get("status_code")?.asInt ?: response.code
                val message = json.get("message")?.takeIf { !it.isJsonNull }?.asString

                if (statusCode != 200 || !response.isSuccessful) {
                    return@withContext F4fApiResult.Error(message ?: "Token không hợp lệ hoặc đã hết hạn")
                }

                val data = json.getAsJsonObject("data")
                    ?: return@withContext F4fApiResult.Error("Dữ liệu trống")

                val username = data.get("username")?.asString.orEmpty()
                val balanceStr = data.get("balance")?.asString ?: "0 coin"
                val id = data.get("id")?.asLong ?: 0L
                val levelName = data.getAsJsonObject("level")?.get("name")?.asString ?: "Thành viên"

                F4fApiResult.Success(
                    F4fUserInfo(
                        id = id,
                        username = username,
                        balanceText = balanceStr,
                        balancePoints = parseCoins(balanceStr),
                        token = cleanToken,
                        levelName = levelName
                    )
                )
            }
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối tới Follow4Follow")
        }
    }

    /**
     * Lấy danh sách tài khoản MXH từ Follow4Follow (type: "tiktok", "facebook", "instagram")
     */
    suspend fun fetchAccountList(rawToken: String, type: String): F4fApiResult<List<F4fRemoteAccount>> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Success(emptyList())

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}${F4fNativeBridge.fetchPathAccountList()}?type=$type&page=1"
        val request = buildF4fRequest(fullUrl, cleanToken)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi danh sách tài khoản không hợp lệ")

                val dataObj = json.getAsJsonObject("data") ?: return@withContext F4fApiResult.Success(emptyList())
                val dataArr = dataObj.getAsJsonArray("data") ?: return@withContext F4fApiResult.Success(emptyList())

                val resultList = mutableListOf<F4fRemoteAccount>()
                for (elem in dataArr) {
                    if (elem.isJsonObject) {
                        val item = elem.asJsonObject
                        val id = item.get("id")?.asString
                            ?: item.get("account_id")?.asString
                            ?: item.get("link")?.asString.orEmpty()
                        val uName = item.get("link")?.asString
                            ?: item.get("username")?.asString
                            ?: item.get("name")?.asString
                            ?: item.get("account_id")?.asString.orEmpty()
                        val status = item.get("status")?.asString ?: "Hoạt động"
                        val isLive = item.get("is_active")?.asBoolean
                            ?: (status.equals("active", ignoreCase = true) || status.equals("Hoạt động", ignoreCase = true))
                        val avatar = item.get("avatar")?.asString.orEmpty()
                        resultList.add(
                            F4fRemoteAccount(
                                id = id,
                                username = uName,
                                status = status,
                                isLive = isLive,
                                avatarUrl = avatar,
                                type = type
                            )
                        )
                    }
                }
                F4fApiResult.Success(resultList)
            }
        } catch (e: java.net.UnknownHostException) {
            F4fApiResult.Error("Lỗi kết nối: Vui lòng kiểm tra lại mạng WiFi/4G")
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi tải danh sách tài khoản F4F")
        }
    }

    /**
     * Thêm/liên kết tài khoản MXH vào Follow4Follow:
     * POST /user/account/add
     * Body JSON: {"link": "<handle>", "g_recaptcha_response": "", "type": "<type>"}
     */
    suspend fun addAccount(rawToken: String, handle: String, type: String = "tiktok"): F4fApiResult<String> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")
        val cleanHandle = handle.trim().removePrefix("@").trim()
        if (cleanHandle.isBlank()) return@withContext F4fApiResult.Error("Tên tài khoản không hợp lệ")

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}${F4fNativeBridge.fetchPathAccountAdd()}"
        val jsonBody = JsonObject().apply {
            addProperty("link", cleanHandle)
            addProperty("g_recaptcha_response", "")
            addProperty("type", type)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = buildF4fRequest(fullUrl, cleanToken)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi không hợp lệ từ máy chủ")

                val statusCode = json.get("status_code")?.asInt ?: response.code
                val message = json.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

                if (statusCode == 200 && response.isSuccessful) {
                    F4fApiResult.Success(if (message.isNotBlank()) message else "Liên kết tài khoản thành công")
                } else {
                    F4fApiResult.Error(if (message.isNotBlank()) message else "Lỗi liên kết tài khoản (Mã $statusCode)")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            F4fApiResult.Error("Lỗi kết nối: Vui lòng kiểm tra lại mạng WiFi/4G")
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối khi liên kết tài khoản F4F")
        }
    }

    /**
     * Lấy danh sách nhiệm vụ kiếm xu từ Follow4Follow:
     * GET /user/earn-coin?platform=tiktok&type=follow&refresh=1&uid=<handle>
     */
    suspend fun getEarnJobs(
        rawToken: String,
        platform: String = "tiktok",
        typeJob: String = "follow",
        uid: String = ""
    ): F4fApiResult<List<F4fJobInfo>> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")

        val cleanUid = uid.trim().removePrefix("@").trim()
        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}${F4fNativeBridge.fetchPathEarnCoin()}?platform=$platform&type=$typeJob&refresh=1&uid=$cleanUid"

        val request = buildF4fRequest(fullUrl, cleanToken)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi nhận job không hợp lệ")

                val statusCode = json.get("status_code")?.asInt ?: response.code
                val message = json.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

                if (statusCode != 200 || !response.isSuccessful) {
                    return@withContext F4fApiResult.Error(if (message.isNotBlank()) message else "Không có nhiệm vụ khả dụng (Mã $statusCode)")
                }

                val dataObj = if (json.has("data") && json.get("data").isJsonObject) {
                    json.getAsJsonObject("data")
                } else null

                var activeAccountId = 0L
                if (dataObj != null && dataObj.has("accountActive") && dataObj.get("accountActive").isJsonObject) {
                    activeAccountId = dataObj.getAsJsonObject("accountActive").get("id")?.asLong ?: 0L
                }

                val jobsArray = when {
                    dataObj != null && dataObj.has("jobs") && dataObj.get("jobs").isJsonArray -> {
                        dataObj.getAsJsonArray("jobs")
                    }
                    json.has("data") && json.get("data").isJsonArray -> {
                        json.getAsJsonArray("data")
                    }
                    else -> null
                }

                val jobList = mutableListOf<F4fJobInfo>()

                if (jobsArray != null && jobsArray.size() > 0) {
                    for (i in 0 until jobsArray.size()) {
                        val jobObj = jobsArray.get(i).asJsonObject
                        val jobId = jobObj.get("id")?.asLong
                            ?: jobObj.get("job_id")?.asLong
                            ?: 0L

                        val link = jobObj.get("link")?.asString
                            ?: jobObj.get("url")?.asString
                            ?: jobObj.get("original_link")?.asString
                            ?: jobObj.get("target_url")?.asString
                            ?: jobObj.get("username")?.asString.orEmpty()

                        val coin = jobObj.get("reward_amount")?.asString?.toLongOrNull()
                            ?: jobObj.get("coin")?.asLong
                            ?: jobObj.get("reward")?.asLong
                            ?: jobObj.get("point")?.asLong
                            ?: 0L

                        val actualTypeJob = jobObj.get("type_job")?.asString ?: typeJob
                        val itemAccountId = jobObj.get("account_id")?.asLong ?: activeAccountId
                        val itemUid = jobObj.get("uid")?.asString.orEmpty()
                        val itemObjectId = jobObj.get("object_id")?.asString.orEmpty()

                        if (jobId > 0L && link.isNotBlank()) {
                            jobList.add(
                                F4fJobInfo(
                                    jobId = jobId,
                                    targetLink = link,
                                    type = platform,
                                    typeJob = actualTypeJob,
                                    rewardCoins = coin,
                                    accountId = itemAccountId,
                                    originalData = jobObj.toString(),
                                    uid = itemUid,
                                    objectId = itemObjectId
                                )
                            )
                        }
                    }
                } else if (dataObj != null && dataObj.has("id")) {
                    val jobId = dataObj.get("id")?.asLong ?: 0L
                    val link = dataObj.get("link")?.asString ?: dataObj.get("url")?.asString.orEmpty()
                    val coin = dataObj.get("reward_amount")?.asString?.toLongOrNull() ?: dataObj.get("coin")?.asLong ?: 0L
                    val itemAccountId = dataObj.get("account_id")?.asLong ?: activeAccountId
                    val itemUid = dataObj.get("uid")?.asString.orEmpty()
                    val itemObjectId = dataObj.get("object_id")?.asString.orEmpty()
                    if (jobId > 0L && link.isNotBlank()) {
                        jobList.add(
                            F4fJobInfo(
                                jobId = jobId,
                                targetLink = link,
                                type = platform,
                                typeJob = typeJob,
                                rewardCoins = coin,
                                accountId = itemAccountId,
                                originalData = dataObj.toString(),
                                uid = itemUid,
                                objectId = itemObjectId
                            )
                        )
                    }
                }

                if (jobList.isEmpty()) {
                    return@withContext F4fApiResult.Error("Hết nhiệm vụ hoặc không có job khả dụng")
                }

                F4fApiResult.Success(jobList)
            }
        } catch (e: java.net.UnknownHostException) {
            F4fApiResult.Error("Lỗi kết nối: Vui lòng kiểm tra lại mạng WiFi/4G")
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi lấy nhiệm vụ F4F")
        }
    }

    /**
     * Lấy 1 nhiệm vụ kiếm xu từ Follow4Follow:
     */
    suspend fun getEarnJob(
        rawToken: String,
        platform: String = "tiktok",
        typeJob: String = "follow",
        uid: String = ""
    ): F4fApiResult<F4fJobInfo> = withContext(Dispatchers.IO) {
        when (val batchRes = getEarnJobs(rawToken, platform, typeJob, uid)) {
            is F4fApiResult.Success -> {
                val first = batchRes.data.firstOrNull()
                if (first != null) F4fApiResult.Success(first)
                else F4fApiResult.Error("Hết nhiệm vụ hoặc không có job khả dụng")
            }
            is F4fApiResult.Error -> F4fApiResult.Error(batchRes.message)
        }
    }

    /**
     * Đánh dấu bắt đầu làm nhiệm vụ Like TikTok:
     * POST /user/earn-coin/tiktok-like/mark-start
     * Body JSON: {"job_id": <id>, "account_id": <id>}
     */
    suspend fun markStartTiktokLike(
        rawToken: String,
        jobId: Long,
        accountId: Long
    ): F4fApiResult<Boolean> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}${F4fNativeBridge.fetchPathMarkStart()}"
        val jsonBody = JsonObject().apply {
            addProperty("job_id", jobId)
            addProperty("account_id", accountId)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = buildF4fRequest(fullUrl, cleanToken)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                val statusCode = json?.get("status_code")?.asInt ?: response.code
                if (response.isSuccessful && statusCode == 200) {
                    F4fApiResult.Success(true)
                } else {
                    val msg = json?.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    F4fApiResult.Error(if (msg.isNotBlank()) msg else "Lỗi đánh dấu bắt đầu like (Mã $statusCode)")
                }
            }
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối khi đánh dấu like")
        }
    }

    /**
     * Xác nhận hoàn thành nhiệm vụ và nhận xu:
     * POST /user/earn-coin/complete-job
     * Body JSON: {"job_id": <id>, "account_id": <id>, "type": "<type>", "type_job": "<type_job>"}
     */
    suspend fun completeJob(
        rawToken: String,
        jobId: Long,
        accountId: Long,
        type: String = "tiktok",
        typeJob: String = "follow"
    ): F4fApiResult<Long> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}${F4fNativeBridge.fetchPathCompleteJob()}"
        val jsonBody = JsonObject().apply {
            addProperty("job_id", jobId)
            addProperty("account_id", accountId)
            addProperty("type", type)
            addProperty("type_job", typeJob)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = buildF4fRequest(fullUrl, cleanToken)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi hoàn thành job không hợp lệ")

                val statusCode = json.get("status_code")?.asInt ?: response.code
                val message = json.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

                if (statusCode == 200 && response.isSuccessful) {
                    val reward = json.getAsJsonObject("data")?.get("coin")?.asLong
                        ?: json.getAsJsonObject("data")?.get("reward")?.asLong
                        ?: 0L
                    F4fApiResult.Success(reward)
                } else {
                    F4fApiResult.Error(if (message.isNotBlank()) message else "Lỗi duyệt job (Mã $statusCode)")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            F4fApiResult.Error("Lỗi kết nối: Vui lòng kiểm tra lại mạng WiFi/4G")
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi xác nhận hoàn thành job F4F")
        }
    }

    /**
     * Kích hoạt chuyển đổi tài khoản chạy trên Follow4Follow:
     * GET /user/account/quick-switch?type=<type>
     */
    suspend fun quickSwitchAccount(
        rawToken: String,
        type: String = "instagram"
    ): F4fApiResult<Boolean> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}/user/account/quick-switch?type=$type"
        val request = buildF4fRequest(fullUrl, cleanToken)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                F4fApiResult.Success(response.isSuccessful)
            }
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi chuyển đổi tài khoản F4F")
        }
    }

    /**
     * Bỏ qua nhiệm vụ:
     * POST /user/earn-coin/skip-job
     * Body JSON: {"job_id": <id>, "account_id": <id>, "uid": "<target_uid>", "object_id": "<target_handle>", "type": "<type>"}
     */
    suspend fun skipJob(
        rawToken: String,
        jobId: Long,
        accountId: Long,
        uid: String = "",
        objectId: String = "",
        type: String = "instagram"
    ): F4fApiResult<Boolean> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}/user/earn-coin/skip-job"
        val jsonBody = JsonObject().apply {
            addProperty("job_id", jobId)
            addProperty("account_id", accountId)
            addProperty("uid", uid)
            addProperty("object_id", objectId)
            addProperty("type", type)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = buildF4fRequest(fullUrl, cleanToken)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                val statusCode = json?.get("status_code")?.asInt ?: response.code
                if (response.isSuccessful && statusCode == 200) {
                    F4fApiResult.Success(true)
                } else {
                    val msg = json?.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    F4fApiResult.Error(if (msg.isNotBlank()) msg else "Lỗi bỏ qua job (Mã $statusCode)")
                }
            }
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối khi bỏ qua job")
        }
    }

    /**
     * Báo cáo nhiệm vụ lỗi (link die, tài khoản đích bị khóa, lỗi tương tác):
     * POST /user/earn-coin/report-job
     * Body JSON: {"job_id": <id>, "account_id": <id>, "uid": "<target_uid>", "object_id": "<target_handle>", "type": "<type>", "message": "<msg>"}
     */
    suspend fun reportJob(
        rawToken: String,
        jobId: Long,
        accountId: Long,
        uid: String = "",
        objectId: String = "",
        message: String = "Link không mở được",
        type: String = "instagram"
    ): F4fApiResult<Boolean> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")

        val fullUrl = "${F4fNativeBridge.fetchBaseUrl()}/user/earn-coin/report-job"
        val jsonBody = JsonObject().apply {
            addProperty("job_id", jobId)
            addProperty("account_id", accountId)
            addProperty("uid", uid)
            addProperty("object_id", objectId)
            addProperty("type", type)
            addProperty("message", message)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = buildF4fRequest(fullUrl, cleanToken)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                val statusCode = json?.get("status_code")?.asInt ?: response.code
                if (response.isSuccessful && statusCode == 200) {
                    F4fApiResult.Success(true)
                } else {
                    val msg = json?.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    F4fApiResult.Error(if (msg.isNotBlank()) msg else "Lỗi báo cáo job (Mã $statusCode)")
                }
            }
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối khi báo cáo job")
        }
    }
}
