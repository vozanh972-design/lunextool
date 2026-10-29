package com.cayxu.app.ui.f4f

import android.content.Context
import com.cayxu.app.automation.instagram.IgTaToolClient
import com.cayxu.app.data.local.InstagramAccountsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

data class IgActionResult(
    val isSuccess: Boolean,
    val httpCode: Int,
    val rawBody: String,
    val errorMessage: String? = null
)

/**
 * Bộ thực thi nhiệm vụ Instagram độc lập cho Follow4Follow (F4F):
 * - Dùng 100% logic TA Tool (IgTaToolClient, extractTargetIdFromUrl, parseIgResult)
 * - Truy cập trực tiếp link profile nhiệm vụ của F4F để lấy numeric ID, không tìm kiếm topsearch
 * - Đếm ngược thời gian thực cho: Thời gian làm nhiệm vụ, Thời gian báo cáo hoàn thành, Thời gian giãn cách
 * - Báo cáo kết quả và nhận xu qua F4fApiClient
 */
class F4fInstagramTaskRunner(
    private val f4fToken: String,
    val username: String,
    val cookie: String,
    var fbDtsg: String? = null,
    var lsd: String? = null,
    var userId: String? = null
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

            val tokens = extractTokensFromCookie(localAcc.cookie)
            val resolvedUserId = localAcc.userId.ifBlank { tokens["ds_user_id"].orEmpty() }

            onStatusUpdate("Đang trích xuất token Instagram...")
            val pageTokens = try {
                IgTaToolClient.extractPageTokens(
                    client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build(),
                    cookie = localAcc.cookie,
                    targetUrl = "https://www.instagram.com/",
                    cachedDtsg = localAcc.fbDtsg,
                    cachedLsd = localAcc.lsd
                )
            } catch (_: Exception) {
                null
            }
            val effDtsg = pageTokens?.dtsg?.takeIf { it.isNotBlank() } ?: localAcc.fbDtsg
            val effLsd = pageTokens?.lsd?.takeIf { it.isNotBlank() } ?: localAcc.lsd

            val runner = F4fInstagramTaskRunner(
                f4fToken = token,
                username = cleanUser,
                cookie = localAcc.cookie,
                fbDtsg = effDtsg,
                lsd = effLsd,
                userId = resolvedUserId
            )
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

            // Đồng bộ phiên Instagram trên F4F
            F4fApiClient.quickSwitchAccount(token, "instagram")

            while (coroutineContext.isActive && F4fInstagramManager.isRunning(cleanUser)) {
                try {
                    // Kiểm tra mục tiêu số lượng hoàn thành
                    if (cfg.targetTaskCount in 1..successCount) {
                        onStatusUpdate("Đã hoàn thành mục tiêu $successCount job!")
                        F4fInstagramManager.stop(cleanUser)
                        break
                    }

                    onStatusUpdate("Đang lấy nhiệm vụ Follow...")
                    val jobRes = F4fApiClient.getEarnJob(
                        rawToken = token,
                        platform = "instagram",
                        typeJob = "follow",
                        uid = cleanUser
                    )

                    val job = when (jobRes) {
                        is F4fApiResult.Success -> jobRes.data
                        is F4fApiResult.Error -> {
                            consecutiveFails++
                            val msg = jobRes.message
                            val delaySec = cfg.getTaskWaitSeconds.coerceAtLeast(cfg.delaySeconds).coerceAtLeast(3)
                            for (s in delaySec downTo 1) {
                                if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break
                                onStatusUpdate("Chưa có job: $msg (Thử lại sau ${s}s...)")
                                delay(1000L)
                            }
                            if (consecutiveFails >= cfg.errorSwapCount && cfg.autoSwapOnError) {
                                onStatusUpdate("Tạm dừng: Lỗi liên tiếp $consecutiveFails lần")
                                onErrorDetail(cleanUser, "Dừng do không có nhiệm vụ hoặc lỗi liên tiếp $consecutiveFails lần: $msg")
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
                    val targetNumericId = when {
                        job.objectId.isNotBlank() && job.objectId.all { it.isDigit() } -> job.objectId
                        job.uid.isNotBlank() && job.uid.all { it.isDigit() } -> job.uid
                        else -> ""
                    }
                    val targetUsername = job.targetLink.trim().removeSuffix("/").substringAfterLast("/")
                    val displayTarget = if (targetUsername.isNotBlank()) "@$targetUsername" else "nhiệm vụ"

                    // Bước 1: Mở thẳng profile URL của nhiệm vụ và thực hiện Follow bằng 100% logic TA Tool V2
                    onStatusUpdate("Đang mở Follow $displayTarget...")
                    val actionResult = if (job.typeJob.equals("like", ignoreCase = true)) {
                        runner.doLike(job.targetLink)
                    } else {
                        runner.doFollow(
                            targetNumericId = targetNumericId,
                            targetUsername = targetUsername,
                            targetUrl = job.targetLink
                        )
                    }

                    if (!actionResult.isSuccess) {
                        val isPrivateAccount = actionResult.errorMessage?.contains("Riêng tư (Private)") == true
                        errorCount++
                        if (!isPrivateAccount) {
                            consecutiveFails++
                        }
                        val errTitle = actionResult.errorMessage ?: "Lỗi tương tác Instagram (Link: ${job.targetLink})"
                        val fullDetail = buildString {
                            append(errTitle)
                            if (actionResult.rawBody.isNotBlank()) {
                                append("\n\n--- [Phản hồi gốc từ Instagram] ---\n")
                                append(actionResult.rawBody.trim().take(2000))
                            }
                        }
                        onProgressUpdate(errTitle, successCount, errorCount)
                        onErrorDetail(cleanUser, fullDetail)

                        // Báo cáo lỗi và bỏ qua nhiệm vụ trên F4F
                        F4fApiClient.reportJob(
                            rawToken = token,
                            jobId = job.jobId,
                            accountId = effAccountId,
                            uid = targetUid,
                            objectId = targetObjectId,
                            message = errTitle,
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
                            onErrorDetail(cleanUser, "Dừng do gặp lỗi liên tiếp $consecutiveFails lần: $errTitle")
                            F4fInstagramManager.stop(cleanUser)
                            break
                        }

                        val waitSec = cfg.getTaskWaitSeconds.coerceAtLeast(3)
                        for (s in waitSec downTo 1) {
                            if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break
                            onStatusUpdate("Nghỉ chờ sau lỗi: ${s}s...")
                            delay(1000L)
                        }
                        continue
                    }

                    // Bước 2: Đếm ngược thời gian làm nhiệm vụ theo cấu hình (workSeconds)
                    val workSec = cfg.workSeconds.coerceAtLeast(1)
                    for (s in workSec downTo 1) {
                        if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break
                        onStatusUpdate("Đang làm $displayTarget (Chờ ${s}s...)")
                        delay(1000L)
                    }

                    if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break

                    // Bước 3: Đếm ngược thời gian chờ báo cáo hoàn thành theo cấu hình (reportWaitSeconds)
                    val reportSec = cfg.reportWaitSeconds.coerceAtLeast(1)
                    for (s in reportSec downTo 1) {
                        if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break
                        onStatusUpdate("Chờ báo cáo hoàn thành: ${s}s...")
                        delay(1000L)
                    }

                    if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break

                    // Bước 4: Báo cáo hoàn thành để nhận xu
                    onStatusUpdate("Đang báo cáo hoàn thành nhận xu...")
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
                            onProgressUpdate("Hoàn thành: +$reward xu (Job $successCount)", successCount, errorCount)
                        }
                        is F4fApiResult.Error -> {
                            errorCount++
                            consecutiveFails++
                            val err = "F4F từ chối duyệt: ${claimRes.message}"
                            onProgressUpdate(err, successCount, errorCount)
                            onErrorDetail(cleanUser, err)

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

                    // Bước 5: Đếm ngược thời gian nghỉ giãn cách trước khi lấy job tiếp theo
                    val restSec = cfg.getTaskWaitSeconds.coerceAtLeast(1)
                    for (s in restSec downTo 1) {
                        if (!coroutineContext.isActive || !F4fInstagramManager.isRunning(cleanUser)) break
                        onStatusUpdate("Nghỉ giãn cách: ${s}s...")
                        delay(1000L)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    onStatusUpdate("Đã tạm dừng")
                    throw e
                } catch (e: Exception) {
                    errorCount++
                    val err = "Ngoại lệ: ${e.message}"
                    onProgressUpdate("Lỗi: $err", successCount, errorCount)
                    onErrorDetail(cleanUser, err)
                    delay(3000L)
                }
            }
        }
    }

    /**
     * Phân tích kết quả Instagram từ chối hay thành công chuẩn 100% TA Tool.
     */
    private fun parseIgResult(rawBody: String, defaultActionName: String): IgActionResult {
        if (rawBody.isBlank()) {
            return IgActionResult(false, 0, "", "Phản hồi rỗng từ Instagram")
        }
        var cleanBody = rawBody.trim()
        if (cleanBody.startsWith("for (;;);")) {
            cleanBody = cleanBody.removePrefix("for (;;);").trim()
        }
        try {
            val root = JSONObject(cleanBody)

            // 1. Kiểm tra lỗi chặn tính năng (Feedback required)
            val message = root.optString("message", "")
            val statusStr = root.optString("status", "")
            val errorType = root.optString("error_type", "")
            val feedbackTitle = root.optString("feedback_title", "")
            val feedbackMsg = root.optString("feedback_message", "")
            if (message.contains("feedback_required", ignoreCase = true) || root.optBoolean("feedback_required", false)) {
                val detail = if (feedbackMsg.isNotBlank()) feedbackMsg else if (feedbackTitle.isNotBlank()) feedbackTitle else "Tài khoản bị Instagram chặn tạm thời tính năng này"
                return IgActionResult(false, 429, rawBody, "Chặn tính năng: $detail")
            }
            if (message.contains("checkpoint_required", ignoreCase = true) || root.has("checkpoint_url")) {
                return IgActionResult(false, 403, rawBody, "Tài khoản dính Checkpoint Instagram")
            }
            if (message.contains("login_required", ignoreCase = true)) {
                return IgActionResult(false, 401, rawBody, "Cookie DIE / Yêu cầu đăng nhập lại")
            }

            // 2. Kiểm tra lỗi GraphQL của Meta
            val errors = root.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val errObj = errors.optJSONObject(0)
                val errMsg = errObj?.optString("message", "") ?: ""
                val errCode = errObj?.optInt("code", 0) ?: 0
                val friendlyMsg = when (errCode) {
                    1357004 -> "Chặn tính năng tương tác (Mã 1357004)"
                    1357001 -> "Cookie hết hạn / Lỗi phiên (Mã 1357001)"
                    else -> if (errMsg.isNotBlank()) errMsg else "Lỗi GraphQL Meta ($errCode)"
                }
                return IgActionResult(false, 400, rawBody, friendlyMsg)
            }

            // 3. Kiểm tra thành công cụ thể
            val data = root.optJSONObject("data")
            val friendship = data?.optJSONObject("xdt_create_friendship")
            val status = friendship?.optJSONObject("friendship_status")
            if (status?.optBoolean("following") == true || status?.optBoolean("outgoing_request") == true) {
                return IgActionResult(true, 200, rawBody, null)
            }
            val likeMedia = data?.optJSONObject("xdt_like_media")
            if (likeMedia != null && (likeMedia.optString("status").equals("ok", true) || likeMedia.has("client_mutation_id"))) {
                return IgActionResult(true, 200, rawBody, null)
            }
            if (root.optString("status").equals("ok", true) || root.optBoolean("viewer_has_liked", false)) {
                return IgActionResult(true, 200, rawBody, null)
            }
            // 4. Bóc tách thông báo lỗi cụ thể từ server nếu có
            if (message.isNotBlank()) {
                val prefix = if (errorType.isNotBlank()) "$errorType: " else ""
                return IgActionResult(false, 400, rawBody, "Instagram phản hồi: $prefix$message")
            }
            if (statusStr.isNotBlank() && !statusStr.equals("ok", true)) {
                return IgActionResult(false, 400, rawBody, "Instagram phản hồi status: $statusStr")
            }
        } catch (_: Exception) {}

        // Fallback kiểm tra chuỗi (Chỉ khi không dính lỗi fail)
        val hasFail = cleanBody.contains("\"status\":\"fail\"") || cleanBody.contains("\"status\": \"fail\"")
        if (!hasFail && (cleanBody.contains("\"following\":true") || cleanBody.contains("\"viewer_has_liked\":true") || cleanBody.contains("\"status\":\"ok\"") || cleanBody.contains("\"status\": \"ok\""))) {
            return IgActionResult(true, 200, rawBody, null)
        }
        if (cleanBody.contains("feedback_required", ignoreCase = true)) {
            return IgActionResult(false, 429, rawBody, "Chặn tính năng (feedback_required)")
        }
        if (cleanBody.contains("checkpoint", ignoreCase = true)) {
            return IgActionResult(false, 403, rawBody, "Dính Checkpoint Instagram")
        }
        if (cleanBody.contains("login_required", ignoreCase = true)) {
            return IgActionResult(false, 401, rawBody, "Cookie DIE / Yêu cầu đăng nhập")
        }

        val rawExcerpt = if (rawBody.length > 150) rawBody.take(150) + "..." else rawBody
        return IgActionResult(false, 400, rawBody, "Instagram từ chối ($defaultActionName): ${rawExcerpt.ifBlank { "Phản hồi rỗng" }}")
    }

    /**
     * Follow 100% logic chuẩn Python v5.2 (Mở profile URL bằng IgTaToolClient.extractTargetIdFromUrl -> IgTaToolClient.follow)
     */
    fun doFollow(targetNumericId: String, targetUsername: String, targetUrl: String = ""): IgActionResult {
        val cleanTargetUser = targetUsername.trim().removePrefix("@").trim('/').substringAfterLast('/')
        val profileUrl = if (targetUrl.isNotBlank() && targetUrl.startsWith("http")) {
            targetUrl
        } else if (cleanTargetUser.isNotBlank()) {
            "https://www.instagram.com/$cleanTargetUser/"
        } else {
            "https://www.instagram.com/"
        }

        var uid = targetNumericId.trim()
        if (!uid.all { it.isDigit() } || uid.isBlank() || uid == userId) {
            // Mở thẳng URL trang profile để lấy UID số bằng 100% chuẩn v5.2
            val extracted = if (profileUrl.isNotBlank()) IgTaToolClient.extractTargetIdFromUrl(client, cookie, profileUrl) else null
            uid = extracted?.takeIf { it != userId } ?: ""
        }

        if (uid.isBlank() || !uid.all { it.isDigit() }) {
            return IgActionResult(false, 400, "", "Không lấy được thông tin tài khoản @$cleanTargetUser từ link nhiệm vụ")
        }

        // Thực thi Follow 100% logic chuẩn V2
        val resBody = IgTaToolClient.follow(
            client = client,
            cookie = cookie,
            targetId = uid,
            profileUrl = profileUrl,
            cachedDtsg = fbDtsg,
            cachedLsd = lsd,
            userId = userId
        )
        val parseRes = IgTaToolClient.parseFollowResponseV2(resBody)
        return if (parseRes.isSuccess) {
            IgActionResult(true, 200, resBody, null)
        } else {
            IgActionResult(false, 400, resBody, parseRes.message)
        }
    }

    /**
     * Like 100% logic TA Tool V2
     */
    fun doLike(targetUrl: String): IgActionResult {
        val shortcode = extractShortcode(targetUrl) ?: return IgActionResult(false, 400, "", "Không trích xuất được Shortcode từ link")
        val mediaId = shortcodeToMediaId(shortcode) ?: return IgActionResult(false, 400, "", "Không đổi được Shortcode sang MediaId")
        val resBody = IgTaToolClient.tym(
            client = client,
            cookie = cookie,
            mediaId = mediaId,
            linkJob = targetUrl,
            cachedDtsg = fbDtsg,
            cachedLsd = lsd,
            userId = userId
        )
        val parseRes = IgTaToolClient.parseTymResponseV2(resBody)
        return if (parseRes.isSuccess) {
            IgActionResult(true, 200, resBody, null)
        } else {
            IgActionResult(false, 400, resBody, parseRes.message)
        }
    }
}
