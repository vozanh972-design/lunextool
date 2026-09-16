package com.cayxu.app.instagram.xsmm

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * FULL LOGIC XSMM INSTAGRAM (API XSMM.net TaskAPI v2 + Tự động làm job Follow/Like & Nhận xu).
 * Đồng bộ chuẩn 100% với endpoint /api/taskapi/accounts2, /api/taskapi/tasks2, /api/taskapi/tasks2/complete.
 */
class XsmmInstagramService(
    private val xsmmToken: String,
    private val igClient: InstagramClient
) {
    companion object {
        const val BASE_URL = "https://xsmm.net/api/taskapi"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var isRunning = false

    private fun buildAuthorizedRequest(url: String): Request.Builder {
        val token = xsmmToken.trim()
        val authHeader = if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"
        return Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
    }

    /**
     * 1. Lấy thông tin tài khoản & số dư xu trên XSMM.NET (/api/taskapi/user)
     */
    @Throws(Exception::class)
    fun getUserProfile(): XsmmUserProfile {
        val url = "$BASE_URL/user"
        val request = buildAuthorizedRequest(url).get().build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IllegalStateException("Lỗi tải thông tin XSMM: HTTP ${response.code} - $body")
            }

            val json = JSONObject(body)
            val user = json.getJSONObject("user")
            return XsmmUserProfile(
                username = user.getString("username"),
                points = user.optLong("points", 0L),
                token = xsmmToken
            )
        }
    }

    /**
     * 2. Lấy danh sách tài khoản Instagram đã liên kết trên XSMM (/api/taskapi/accounts2)
     */
    @Throws(Exception::class)
    fun getInstagramAccounts(): List<XsmmAccountItem> {
        val url = "$BASE_URL/accounts2?account_type=instagram"
        val request = buildAuthorizedRequest(url).get().build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val list = mutableListOf<XsmmAccountItem>()

            if (body.startsWith("{")) {
                val obj = JSONObject(body)
                val arr = obj.optJSONArray("accounts") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val acc = arr.getJSONObject(i)
                    list.add(
                        XsmmAccountItem(
                            id = acc.optLong("id", i.toLong() + 1),
                            accountId = acc.optString("account_id", acc.optString("name", "")),
                            name = acc.optString("name", acc.optString("link_account", "")),
                            isActive = acc.optBoolean("is_active", acc.optInt("is_active", 0) == 1),
                            type = acc.optString("type", "instagram")
                        )
                    )
                }
            } else if (body.startsWith("[")) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        XsmmAccountItem(
                            id = obj.optLong("id", i.toLong() + 1),
                            accountId = obj.optString("account_id", obj.optString("name", "")),
                            name = obj.optString("name", ""),
                            isActive = obj.optBoolean("is_active", obj.optInt("is_active", 0) == 1),
                            type = obj.optString("type", "instagram")
                        )
                    )
                }
            }
            return list
        }
    }

    /**
     * 3. Thêm / Liên kết nick Instagram vào XSMM (/api/taskapi/accounts2)
     */
    @Throws(Exception::class)
    fun addInstagramAccount(instagramUsername: String, setActive: Boolean = true): Boolean {
        val url = "$BASE_URL/accounts2"
        val cleanName = instagramUsername.trim().removePrefix("@")
        val payload = JSONObject().apply {
            put("type", "instagram")
            put("link_account", "https://www.instagram.com/$cleanName/")
            put("active", setActive)
        }

        val request = buildAuthorizedRequest(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            return response.isSuccessful || body.contains("Liên kết tài khoản thành công") || body.contains("already exists") || body.contains("Thành công")
        }
    }

    /**
     * 4. Đặt nick Instagram làm nick chạy chính (Set Active /api/taskapi/accounts/{id}/set-active)
     */
    fun setActiveAccount(accountIdOnXsmm: String): Boolean {
        val url = "$BASE_URL/accounts/$accountIdOnXsmm/set-active"
        val requestPut = buildAuthorizedRequest(url)
            .put("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            httpClient.newCall(requestPut).execute().use { resp ->
                if (resp.isSuccessful) return true
            }
            val requestPost = buildAuthorizedRequest(url)
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(requestPost).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /**
     * 5. Nhận danh sách nhiệm vụ Instagram từ XSMM (/api/taskapi/tasks2)
     */
    @Throws(Exception::class)
    fun getInstagramTasks(accountUid: String, taskType: String = "instagram_follow"): List<XsmmTaskItem> {
        val url = "$BASE_URL/tasks2?type=$taskType&uid=$accountUid&typejob=normal,better,best"
        val request = buildAuthorizedRequest(url).get().build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val taskList = mutableListOf<XsmmTaskItem>()

            if (body.isEmpty() || body.contains("Không có job nào") || body == "[]") {
                return taskList
            }

            if (body.startsWith("[")) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    taskList.add(
                        XsmmTaskItem(
                            taskId = obj.getString("id"),
                            type = obj.optString("type", taskType),
                            targetId = obj.optString("target_id", obj.optString("target_id2", "")),
                            idOrLink = obj.optString("idorlink", obj.optString("target_url", "")),
                            points = obj.optInt("points", 0),
                            commentText = obj.optString("comment", obj.optString("comment_text", null))
                        )
                    )
                }
            }
            return taskList
        }
    }

    /**
     * 6. Báo cáo hoàn thành nhiệm vụ và Nhận Điểm / Xu (/api/taskapi/tasks2/complete)
     */
    @Throws(Exception::class)
    fun completeTasks(taskIds: List<String>, accountUid: String, taskType: String = "instagram_follow", cookieCheck: String? = null): XsmmCompleteResult {
        val url = "$BASE_URL/tasks2/complete"
        val taskIdArray = JSONArray()
        for (id in taskIds) {
            taskIdArray.put(id)
        }

        val payload = JSONObject().apply {
            put("type", taskType)
            put("task_id", taskIdArray)
            put("uid", accountUid)
            if (!cookieCheck.isNullOrBlank()) {
                put("cookie_check", cookieCheck)
            }
        }

        val request = buildAuthorizedRequest(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val json = if (body.startsWith("{")) JSONObject(body) else JSONObject()

            val isSuccess = response.isSuccessful && (body.contains("Thành công") || json.has("points") || json.optInt("points", 0) > 0)
            val points = json.optInt("points", 0)
            val successCount = json.optInt("success_count", if (isSuccess) taskIds.size else 0)
            val msg = json.optString("message", if (isSuccess) "Hoàn thành nhiệm vụ thành công!" else body)
            val countdown = json.optInt("countdown", 5)

            return XsmmCompleteResult(
                isSuccess = isSuccess,
                pointsEarned = points,
                successCount = successCount,
                message = msg,
                countdownSeconds = countdown
            )
        }
    }

    /**
     * 7. VÒNG LẶP TỰ ĐỘNG CHẠY JOB XSMM INSTAGRAM
     */
    fun startAutoTaskLoop(
        igAccount: InstagramProfile,
        targetJobs: Int = 50,
        delaySeconds: Int = 10,
        onProgress: (String) -> Unit
    ) {
        isRunning = true
        var completedCount = 0
        var totalPointsEarned = 0
        var consecutiveErrors = 0
        val pendingFollowTaskIds = mutableListOf<String>()

        log("🚀 [XSMM Instagram] Bắt đầu chạy nhiệm vụ cho nick @${igAccount.username}...")

        // Tự động liên kết / kích hoạt tài khoản
        var xsmmUid = igAccount.userId
        try {
            val accounts = getInstagramAccounts()
            val matched = accounts.find { 
                it.name.contains(igAccount.username, ignoreCase = true) || 
                it.accountId.equals(igAccount.username, ignoreCase = true) ||
                it.accountId.equals(igAccount.userId, ignoreCase = true)
            }

            if (matched == null) {
                log("⚡ Chưa có nick @${igAccount.username} trên XSMM, đang tự động liên kết...")
                addInstagramAccount(igAccount.username, setActive = true)
            } else {
                xsmmUid = matched.accountId.ifBlank { matched.id.toString() }
                if (!matched.isActive) {
                    setActiveAccount(matched.id.toString())
                }
            }
        } catch (e: Exception) {
            log("⚠️ Cảnh báo kiểm tra nick trên XSMM: ${e.message}")
        }

        while (isRunning && completedCount < targetJobs) {
            try {
                // Ưu tiên Follow, nếu hết job Follow thì thử Like
                val taskTypes = listOf("instagram_follow", "instagram_like")
                var foundTasks = false

                for (type in taskTypes) {
                    if (!isRunning || completedCount >= targetJobs) break

                    log("Đang lấy job $type cho @${igAccount.username}...")
                    val tasks = getInstagramTasks(xsmmUid, taskType = type)

                    if (tasks.isNotEmpty()) {
                        foundTasks = true
                        for (task in tasks) {
                            if (!isRunning || completedCount >= targetJobs) break

                            log("Nhận job: ${task.type} | Target: ${task.idOrLink.ifBlank { task.targetId }} (+${task.points} Xu)")

                            var isActionSuccess = false
                            if (task.type.contains("follow", ignoreCase = true)) {
                                val result = igClient.followUser(task.idOrLink.ifBlank { task.targetId }, igAccount)
                                isActionSuccess = result.isSuccess
                                log("Thực hiện Follow target: ${result.message}")
                            } else {
                                val result = igClient.likeMedia(task.idOrLink.ifBlank { task.targetId }, igAccount)
                                isActionSuccess = result.isSuccess
                                log("Thực hiện Like target: ${result.message}")
                            }

                            if (isActionSuccess) {
                                consecutiveErrors = 0
                                if (task.type.contains("follow", ignoreCase = true)) {
                                    pendingFollowTaskIds.add(task.taskId)
                                    completedCount++
                                    log("Đã Follow (${pendingFollowTaskIds.size}/12) - Đủ 12 job sẽ gửi nhận xu...")

                                    if (pendingFollowTaskIds.size >= 12) {
                                        val batch = pendingFollowTaskIds.take(12)
                                        log("Gửi nhận xu cho 12 job Follow...")
                                        val compRes = completeTasks(batch, xsmmUid, "instagram_follow", igAccount.cookie)
                                        if (compRes.isSuccess) {
                                            totalPointsEarned += compRes.pointsEarned
                                            log("✅ Nhận thành công +${compRes.pointsEarned} Xu! (Tổng: $totalPointsEarned Xu | $completedCount/$targetJobs jobs)")
                                            pendingFollowTaskIds.removeAll(batch)
                                        } else {
                                            log("❌ Lỗi nhận xu: ${compRes.message}")
                                            pendingFollowTaskIds.removeAll(batch)
                                        }
                                    }
                                } else {
                                    // Like hoàn thành từng job
                                    Thread.sleep(3000)
                                    log("Gửi báo cáo hoàn thành Like...")
                                    val compRes = completeTasks(listOf(task.taskId), xsmmUid, "instagram_like", igAccount.cookie)
                                    if (compRes.isSuccess) {
                                        completedCount++
                                        totalPointsEarned += (if (compRes.pointsEarned > 0) compRes.pointsEarned else task.points)
                                        log("✅ Hoàn thành Like! +${compRes.pointsEarned} Xu (Tổng: $totalPointsEarned Xu | $completedCount/$targetJobs jobs)")
                                    } else {
                                        log("❌ Lỗi báo cáo Like: ${compRes.message}")
                                    }
                                }
                            } else {
                                consecutiveErrors++
                                log("❌ Thao tác trên Instagram thất bại!")

                                if (consecutiveErrors >= 3) {
                                    log("⚠️ Lỗi liên tiếp 3 lần. Đang kiểm tra lại trạng thái Cookie...")
                                    try {
                                        igClient.verifyCookieAndGetProfile(igAccount.cookie ?: "", igAccount.username)
                                    } catch (e: Exception) {
                                        log("❌ Cookie Die! Dừng tài khoản @${igAccount.username}")
                                        isRunning = false
                                        break
                                    }
                                }
                            }

                            log("Chờ ${delaySeconds}s an toàn...")
                            Thread.sleep((delaySeconds * 1000).toLong())
                        }
                    }
                }

                if (!foundTasks) {
                    log("Tạm hết job trên XSMM. Nghỉ 15 giây trước khi quét lại...")
                    Thread.sleep(15000)
                }
            } catch (e: Exception) {
                log("❌ Lỗi ngoại lệ trong vòng lặp XSMM: ${e.message}")
                Thread.sleep(10000)
            }
        }

        // Nhận xu nốt các job Follow còn đọng
        if (pendingFollowTaskIds.isNotEmpty()) {
            val remaining = pendingFollowTaskIds.toList()
            log("Đang gửi nhận xu cho ${remaining.size} job Follow còn lại...")
            val compRes = completeTasks(remaining, xsmmUid, "instagram_follow", igAccount.cookie)
            if (compRes.isSuccess) {
                totalPointsEarned += compRes.pointsEarned
            }
            pendingFollowTaskIds.clear()
        }

        log("🏁 Kết thúc phiên chạy XSMM Instagram cho @${igAccount.username}! Hoàn thành: $completedCount jobs | Kiếm được: +$totalPointsEarned Xu.")
    }

    fun stop() {
        isRunning = false
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        println("[$time] [XsmmRunner] $msg")
    }
}

