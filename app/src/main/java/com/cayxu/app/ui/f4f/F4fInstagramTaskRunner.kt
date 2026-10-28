package com.cayxu.app.ui.f4f

import android.content.Context
import com.cayxu.app.data.local.InstagramAccountsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import com.cayxu.app.automation.instagram.IgTaToolClient
import kotlin.coroutines.coroutineContext

/**
 * Bộ thực thi nhiệm vụ Instagram độc lập cho Follow4Follow (F4F):
 * - Lấy job từ API F4F: F4fApiClient.getEarnJobs(token, platform = "instagram")
 * - Tương tác trực tiếp với Instagram thông qua Cookie tài khoản trên máy
 * - Hoàn thành và nhận thưởng xu Follow4Follow qua F4fApiClient.completeJob(...)
 */
class F4fInstagramTaskRunner(
    private val f4fToken: String,
    val username: String,
    val cookie: String
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun shortcodeToMediaId(shortcode: String): String? {
            if (shortcode.isBlank()) return null
            var id = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (c in shortcode) {
                val idx = ALPHABET.indexOf(c)
                if (idx == -1) return null
                id = id.multiply(base).add(BigInteger.valueOf(idx.toLong()))
            }
            return id.toString()
        }

        fun extractShortcode(url: String): String? {
            val m = Pattern.compile("/(?:p|reel|tv)/([A-Za-z0-9_-]+)").matcher(url)
            return if (m.find()) m.group(1) else null
        }

        fun extractTokensFromCookie(cookie: String): Map<String, String> {
            return cookie.split(";").mapNotNull {
                val idx = it.indexOf("=")
                if (idx > 0) it.substring(0, idx).trim() to it.substring(idx + 1).trim() else null
            }.toMap()
        }

        /**
         * Chạy nhiệm vụ cho một tài khoản Instagram F4F
         */
        suspend fun runSingleAccount(
            context: Context,
            accountUsername: String,
            onStatusUpdate: (String) -> Unit,
            onProgressUpdate: (status: String, success: Int, errors: Int) -> Unit,
            onErrorDetail: (user: String, detail: String) -> Unit
        ) = withContext(Dispatchers.IO) {
            val cleanUser = accountUsername.trim().lowercase().removePrefix("@")
            val token = F4fAccountStore.getToken(context).orEmpty()
            if (token.isBlank()) {
                onStatusUpdate("Lỗi: Chưa đăng nhập Follow4Follow")
                onErrorDetail(cleanUser, "Chưa tìm thấy token đăng nhập Follow4Follow.")
                return@withContext
            }

            val localAcc = InstagramAccountsStore.getAccounts(context).find {
                it.username.trim().lowercase().removePrefix("@") == cleanUser
            }
            if (localAcc == null || localAcc.cookie.isBlank()) {
                onStatusUpdate("Lỗi: Chưa có Cookie Instagram")
                onErrorDetail(cleanUser, "Tài khoản @$cleanUser chưa có Cookie Instagram trong máy.")
                return@withContext
            }

            val runner = F4fInstagramTaskRunner(token, cleanUser, localAcc.cookie)
            val cfg = F4fRunConfigStore.get(context)
            var successCount = 0
            var errorCount = 0
            var consecutiveFails = 0

            onStatusUpdate("Đang kết nối F4F...")

            // Lấy danh sách tài khoản remote từ F4F để tra cứu account_id dạng số
            val accountIdMap = mutableMapOf<String, Long>()
            when (val listRes = F4fApiClient.fetchAccountList(token, "instagram")) {
                is F4fApiResult.Success -> {
                    listRes.data.forEach { acc ->
                        val cleanUname = acc.username.trim().removePrefix("@").lowercase()
                        val idNum = acc.id.toLongOrNull() ?: 0L
                        if (cleanUname.isNotBlank() && idNum > 0L) {
                            accountIdMap[cleanUname] = idNum
                        }
                    }
                }
                is F4fApiResult.Error -> {}
            }
            val numericAccountId = accountIdMap[cleanUser] ?: 0L

            // Gọi quickSwitchAccount để đồng bộ phiên Instagram trên F4F
            F4fApiClient.quickSwitchAccount(token, "instagram")

            while (coroutineContext.isActive && F4fInstagramManager.isRunning(cleanUser)) {
                try {
                    onStatusUpdate("Đang lấy job Instagram...")
                    val jobRes = F4fApiClient.getEarnJob(
                        rawToken = token,
                        platform = "instagram",
                        typeJob = if (cfg.enableFollow) "follow" else "like",
                        uid = cleanUser
                    )

                    val job = when (jobRes) {
                        is F4fApiResult.Success -> jobRes.data
                        is F4fApiResult.Error -> {
                            consecutiveFails++
                            val msg = jobRes.message
                            val delaySec = cfg.delaySeconds.coerceAtLeast(5)
                            onStatusUpdate("Chưa có job: $msg (Chờ ${delaySec}s)")
                            delay(delaySec * 1000L)
                            if (consecutiveFails >= cfg.errorSwapCount && cfg.autoSwapOnError) {
                                onStatusUpdate("Tạm dừng: Gặp lỗi liên tiếp $consecutiveFails lần")
                                onErrorDetail(cleanUser, "Dừng do gặp lỗi liên tiếp $consecutiveFails lần: $msg")
                                F4fInstagramManager.stop(cleanUser)
                                break
                            }
                            continue
                        }
                    }

                    consecutiveFails = 0
                    val effAccountId = if (job.accountId > 0L) {
                        job.accountId
                    } else if (numericAccountId > 0L) {
                        numericAccountId
                    } else {
                        job.jobId
                    }
                    val targetUid = job.uid
                    val targetObjectId = job.objectId.ifBlank {
                        job.targetLink.trim().removeSuffix("/").substringAfterLast("/")
                    }

                    onStatusUpdate("Đang làm job #${job.jobId} (${job.typeJob})...")

                    // Thực hiện tương tác trên Instagram
                    val actionOk = if (job.typeJob.equals("like", ignoreCase = true)) {
                        runner.doLike(job.targetLink)
                    } else {
                        runner.doFollow(job.targetLink, targetUid)
                    }

                    if (!actionOk) {
                        errorCount++
                        consecutiveFails++
                        val err = "Lỗi tương tác Instagram (Link: ${job.targetLink})"
                        onProgressUpdate("Lỗi tương tác Instagram", successCount, errorCount)
                        onErrorDetail(cleanUser, err)

                        // Tự động báo cáo lỗi và bỏ qua job trên F4F
                        F4fApiClient.reportJob(
                            rawToken = token,
                            jobId = job.jobId,
                            accountId = effAccountId,
                            uid = targetUid,
                            objectId = targetObjectId,
                            message = "Link không mở được hoặc lỗi tương tác",
                            type = "instagram"
                        )
                        F4fApiClient.skipJob(
                            rawToken = token,
                            jobId = job.jobId,
                            accountId = effAccountId,
                            uid = targetUid,
                            objectId = targetObjectId,
                            type = "instagram"
                        )

                        if (consecutiveFails >= cfg.errorSwapCount && cfg.autoSwapOnError) {
                            onStatusUpdate("Tạm dừng: Gặp lỗi liên tiếp $consecutiveFails lần")
                            onErrorDetail(cleanUser, "Dừng do gặp lỗi liên tiếp $consecutiveFails lần.")
                            F4fInstagramManager.stop(cleanUser)
                            break
                        }

                        delay(cfg.delaySeconds * 1000L)
                        continue
                    }

                    // Chờ delay cấu hình trước khi gửi xác nhận nhận xu
                    for (s in (cfg.delaySeconds.coerceAtLeast(3)) downTo 1) {
                        if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break
                        onStatusUpdate("Job #${job.jobId}: Chờ ${s}s xác nhận...")
                        delay(1000L)
                    }

                    onStatusUpdate("Đang nhận thưởng job #${job.jobId}...")
                    val claimRes = F4fApiClient.completeJob(
                        rawToken = token,
                        jobId = job.jobId,
                        accountId = effAccountId,
                        type = "instagram",
                        typeJob = job.typeJob
                    )

                    when (claimRes) {
                        is F4fApiResult.Success -> {
                            successCount++
                            consecutiveFails = 0
                            val reward = claimRes.data.takeIf { it > 0 } ?: job.rewardCoins
                            val balanceNow = F4fAccountStore.getPoints(context) + reward
                            F4fAccountStore.updatePoints(context, balanceNow)
                            F4fSession.points.value = balanceNow
                            onProgressUpdate("Hoàn thành: +$reward xu", successCount, errorCount)
                        }
                        is F4fApiResult.Error -> {
                            errorCount++
                            consecutiveFails++
                            val err = "F4F từ chối duyệt: ${claimRes.message}"
                            onProgressUpdate(err, successCount, errorCount)
                            onErrorDetail(cleanUser, err)

                            // Tự động bỏ qua job bị từ chối để không nghẽn luồng
                            F4fApiClient.skipJob(
                                rawToken = token,
                                jobId = job.jobId,
                                accountId = effAccountId,
                                uid = targetUid,
                                objectId = targetObjectId,
                                type = "instagram"
                            )
                        }
                    }

                    val delaySec = (cfg.delayMinSeconds..cfg.delayMaxSeconds.coerceAtLeast(cfg.delayMinSeconds)).random()
                    onStatusUpdate("Chờ ${delaySec}s chuyển job tiếp...")
                    delay(delaySec * 1000L)

                    if (cfg.targetTaskCount in 1..successCount) {
                        onStatusUpdate("Đã đạt mục tiêu $successCount job!")
                        F4fInstagramManager.stop(cleanUser)
                        break
                    }
                } catch (e: Exception) {
                    errorCount++
                    val err = "Ngoại lệ: ${e.message}"
                    onProgressUpdate("Lỗi: $err", successCount, errorCount)
                    onErrorDetail(cleanUser, err)
                    delay(5000L)
                }
            }
        }
    }

    private suspend fun doFollow(targetUrl: String, targetUid: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (targetUid.isNotBlank()) {
            try {
                val igRes = IgTaToolClient.follow(
                    client = client,
                    rawCookie = cookie,
                    targetId = targetUid,
                    profileUrl = targetUrl
                )
                if (igRes.contains("\"following\":true") || igRes.contains("xdt_create_friendship") || igRes.contains("\"status\":\"ok\"")) {
                    return@withContext true
                }
            } catch (_: Exception) {}
        }

        val targetUser = targetUrl.trim().removeSuffix("/").substringAfterLast("/")
        if (targetUser.isBlank()) return@withContext false

        val tokens = extractTokensFromCookie(cookie)
        val csrf = tokens["csrftoken"].orEmpty()

        val req = Request.Builder()
            .url("https://www.instagram.com/api/v1/friendships/create/${if (targetUid.isNotBlank()) targetUid else targetUser}/")
            .post("".toRequestBody(JSON_TYPE))
            .header("Cookie", cookie)
            .header("X-CSRFToken", csrf)
            .header("X-IG-App-ID", "936619743392459")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.instagram.com/$targetUser/")
            .build()

        try {
            client.newCall(req).execute().use { response ->
                response.isSuccessful || response.code == 200
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun doLike(targetUrl: String): Boolean = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(targetUrl) ?: return@withContext false
        val mediaId = shortcodeToMediaId(shortcode) ?: return@withContext false

        try {
            val tymRes = IgTaToolClient.tym(
                client = client,
                rawCookie = cookie,
                mediaId = mediaId,
                linkJob = targetUrl
            )
            if (tymRes.contains("\"status\":\"ok\"") || tymRes.contains("\"viewer_has_liked\":true")) {
                return@withContext true
            }
        } catch (_: Exception) {}

        val tokens = extractTokensFromCookie(cookie)
        val csrf = tokens["csrftoken"].orEmpty()

        val req = Request.Builder()
            .url("https://www.instagram.com/api/v1/web/likes/$mediaId/like/")
            .post("".toRequestBody(JSON_TYPE))
            .header("Cookie", cookie)
            .header("X-CSRFToken", csrf)
            .header("X-IG-App-ID", "936619743392459")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", targetUrl)
            .build()

        try {
            client.newCall(req).execute().use { response ->
                response.isSuccessful || response.code == 200
            }
        } catch (_: Exception) {
            false
        }
    }
}
