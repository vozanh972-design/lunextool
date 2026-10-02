package com.cayxu.app.ui.screens.golike

import android.content.Context
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * TaskRunner chuyên trách làm nhiệm vụ và tự động cấu hình nick TikTok trên Golike.
 * Sao chép chuẩn xác cơ chế tương tác và mở TikTok từ XSMM nhưng gọi Gateway API Golike.
 */
object GolikeTikTokTaskRunner {

    /**
     * Tự động cấu hình và xác minh tài khoản TikTok vào Golike theo chuẩn GoMax:
     * 1. Khai báo nick: POST /api/tiktok-account {"unique_username": "..."}
     * 2. Lấy thông tin tài khoản quản trị viên Golike yêu cầu (target_user).
     * 3. Tự động mở profile TikTok và bấm Follow target_user (qua Accessibility hoặc Intent).
     * 4. Gửi xác nhận: POST /api/tiktok-account/verify-account-id
     */
    suspend fun verifyAndLinkTikTokAccount(
        context: Context,
        client: GolikeApiClient,
        username: String,
        onProgress: (String) -> Unit
    ): Result<GolikeAccount> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().removePrefix("@").lowercase()
        if (cleanUsername.isBlank()) {
            return@withContext Result.failure(Exception("Tên người dùng TikTok không hợp lệ"))
        }

        onProgress("Kiểm tra nick @$cleanUsername trên Golike...")

        // BƯỚC 1: Kiểm tra xem tài khoản đã được liên kết sẵn trên Golike chưa
        try {
            val listRes = client.getTikTokAccounts()
            val listData = listRes?.optJSONArray("data")
            if (listData != null) {
                for (i in 0 until listData.length()) {
                    val item = listData.optJSONObject(i) ?: continue
                    val sUname = item.optString("unique_username").ifBlank {
                        item.optString("username").ifBlank {
                            item.optString("nickname")
                        }
                    }.trim().removePrefix("@").lowercase()

                    if (sUname == cleanUsername) {
                        val accId = item.optString("id").ifBlank { item.optString("account_id") }
                        android.util.Log.d("GolikeApi", "Tài khoản @$cleanUsername đã có sẵn trên Golike với ID: $accId")
                        val verifiedAcc = GolikeAccount(
                            id = cleanUsername,
                            platform = "tiktok",
                            username = username,
                            avatar = item.optString("avatar"),
                            isLive = true,
                            isGolikeLinked = true,
                            golikeAccountId = accId,
                            lastStatus = "Đã liên kết Golike • Sẵn sàng"
                        )
                        GolikeAccountsStore.addOrUpdateAccount(context, verifiedAcc)
                        return@withContext Result.success(verifiedAcc)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GolikeApi", "Lỗi kiểm tra danh sách TikTok hiện tại: ${e.message}")
        }

        // BƯỚC 2: Khai báo nick lên hệ thống Golike
        onProgress("Khai báo @$cleanUsername lên hệ thống Golike...")
        val declareRes = client.declareTikTokAccount(cleanUsername)
        if (declareRes == null) {
            return@withContext Result.failure(Exception("Không thể kết nối API Golike. Vui lòng kiểm tra mạng hoặc đăng nhập lại."))
        }

        val status = declareRes.optInt("status", 0)
        val success = declareRes.optBoolean("success", false)
        val dataObj = declareRes.optJSONObject("data")
        val message = declareRes.optString("message")

        // Trường hợp tài khoản đã tồn tại hoặc đã liên kết từ trước
        if (message.contains("đã tồn tại", ignoreCase = true) ||
            message.contains("đã liên kết", ignoreCase = true) ||
            message.contains("thành công", ignoreCase = true) ||
            (status == 200 && dataObj?.optBoolean("is_verified", false) == true)
        ) {
            // Tải lại danh sách từ Golike để lấy accountId
            var foundAccId = dataObj?.optString("id")?.takeIf { it.isNotBlank() } ?: dataObj?.optString("account_id").orEmpty()
            if (foundAccId.isBlank()) {
                val listRes = client.getTikTokAccounts()
                val listData = listRes?.optJSONArray("data")
                if (listData != null) {
                    for (i in 0 until listData.length()) {
                        val item = listData.optJSONObject(i) ?: continue
                        val sUname = item.optString("unique_username").ifBlank {
                            item.optString("username").ifBlank { item.optString("nickname") }
                        }.trim().removePrefix("@").lowercase()
                        if (sUname == cleanUsername) {
                            foundAccId = item.optString("id").ifBlank { item.optString("account_id") }
                            break
                        }
                    }
                }
            }
            if (foundAccId.isBlank()) foundAccId = cleanUsername

            val verifiedAcc = GolikeAccount(
                id = cleanUsername,
                platform = "tiktok",
                username = username,
                avatar = dataObj?.optString("avatar").orEmpty(),
                isLive = true,
                isGolikeLinked = true,
                golikeAccountId = foundAccId,
                lastStatus = "Đã liên kết Golike • Sẵn sàng"
            )
            GolikeAccountsStore.addOrUpdateAccount(context, verifiedAcc)
            return@withContext Result.success(verifiedAcc)
        }

        // Lấy target_user cần follow từ server Golike
        var targetUser = dataObj?.optString("target_user").orEmpty()
        if (targetUser.isBlank()) {
            targetUser = dataObj?.optJSONObject("target_user")?.optString("username").orEmpty()
        }
        if (targetUser.isBlank()) {
            targetUser = dataObj?.optString("unique_username").orEmpty()
        }
        if (targetUser.isBlank()) {
            targetUser = dataObj?.optString("username").orEmpty()
        }

        val accountId = dataObj?.optString("id")?.takeIf { it.isNotBlank() }
            ?: dataObj?.optString("account_id")?.takeIf { it.isNotBlank() }
            ?: declareRes.optString("account_id").orEmpty()

        if (accountId.isBlank() && targetUser.isBlank() && status != 200 && !success) {
            val err = message.ifBlank { "Không lấy được thông tin xác minh tài khoản từ Golike" }
            return@withContext Result.failure(Exception(err))
        }

        // BƯỚC 3: Mở profile TikTok follow target_user của Golike nếu có
        if (targetUser.isNotBlank()) {
            onProgress("Mở TikTok follow nick cấu hình @$targetUser...")
            val targetUrl = "https://www.tiktok.com/@${targetUser.removePrefix("@")}"
            TikTokAppLauncher.openUserProfile(context, targetUrl)
            delay(1500L)

            if (TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
                onProgress("Tự động bấm nút Follow @$targetUser...")
                XsmmTaskAutomationBridge.triggerTask(
                    taskType = "follow",
                    swipeBefore = false,
                    returnHomeAndSwipe = false,
                    durationSeconds = 4
                )
                delay(4500L)
            } else {
                onProgress("Đang chờ Follow @$targetUser trên TikTok...")
                delay(5000L)
            }
        } else {
            delay(1500L)
        }

        // BƯỚC 4: Gửi xác nhận liên kết (Verify Account ID)
        onProgress("Đang gửi xác minh liên kết lên Golike...")
        val verifyRes = client.verifyTikTokAccountId(accountId.ifBlank { cleanUsername }, cleanUsername)
        val vStatus = verifyRes?.optInt("status", 0) ?: 0
        val vSuccess = verifyRes?.optBoolean("success", false) ?: false
        val vMessage = verifyRes?.optString("message").orEmpty()

        if (vStatus == 200 || vSuccess || vMessage.contains("thành công", ignoreCase = true) || vMessage.contains("đã tồn tại", ignoreCase = true)) {
            val finalAcc = GolikeAccount(
                id = cleanUsername,
                platform = "tiktok",
                username = username,
                avatar = dataObj?.optString("avatar").orEmpty(),
                isLive = true,
                isGolikeLinked = true,
                golikeAccountId = accountId.ifBlank { cleanUsername },
                lastStatus = "Đã liên kết Golike • Sẵn sàng"
            )
            GolikeAccountsStore.addOrUpdateAccount(context, finalAcc)
            return@withContext Result.success(finalAcc)
        } else {
            val failMsg = vMessage.ifBlank { "Xác minh thất bại. Hãy chắc chắn bạn đã bấm Follow @$targetUser." }
            return@withContext Result.failure(Exception(failMsg))
        }
    }

    /**
     * Vòng lặp nhận và thực thi nhiệm vụ TikTok độc lập cho một tài khoản.
     */
    suspend fun runTikTokTaskLoop(
        context: Context,
        account: GolikeAccount,
        client: GolikeApiClient,
        onStatusChange: (String) -> Unit,
        onJobSuccess: (earned: Int) -> Unit,
        onJobFailed: (reason: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val cfg = GolikeRunConfigStore.get(context, "tiktok")
        val golikeAccId = account.golikeAccountId.ifBlank { account.id }
        var consecutiveFails = 0

        while (GolikeRunningManager.isRunning(account.id) && isActive) {
            try {
                onStatusChange("Đang lấy nhiệm vụ TikTok...")

                // 1. Lấy nhiệm vụ (Get Job)
                val jobRes = client.getTikTokJob(golikeAccId)
                if (jobRes == null || !jobRes.optBoolean("success", false)) {
                    consecutiveFails++
                    val errMsg = jobRes?.optString("message")?.takeIf { it.isNotBlank() } ?: "Chưa có nhiệm vụ phù hợp lúc này"
                    onStatusChange("Chưa có job: $errMsg (Chờ ${cfg.delayMinSeconds}s...)")
                    onJobFailed(errMsg)

                    if (cfg.autoSwitchOnFail && consecutiveFails >= cfg.failJobCountToSwitchAccount) {
                        onStatusChange("Tạm dừng: Gặp lỗi liên tiếp $consecutiveFails lần")
                        GolikeRunningManager.runningAccounts[account.id] = false
                        break
                    }

                    delay(cfg.delayMinSeconds * 1000L)
                    continue
                }

                consecutiveFails = 0
                val jobData = jobRes.optJSONObject("data")
                if (jobData == null) {
                    delay(cfg.delayMinSeconds * 1000L)
                    continue
                }

                val adsId = jobData.optLong("ads_id", jobData.optLong("id", 0L))
                val jobType = jobData.optString("type", "follow")
                val link = jobData.optString("link")
                val objectId = jobData.optString("object_id")
                val countdown = jobRes.optInt("countdown", cfg.delayMinSeconds).coerceAtLeast(3)

                // 2. Thực thi tương tác TikTok
                val targetUrl = when {
                    link.isNotBlank() -> link
                    jobType.contains("follow", ignoreCase = true) -> "https://www.tiktok.com/@${objectId.removePrefix("@")}"
                    else -> "https://www.tiktok.com/@user/video/$objectId"
                }

                val shortType = if (jobType.contains("follow", ignoreCase = true)) "Follow" else "Tym"
                onStatusChange("Mở $shortType: ${objectId.ifBlank { targetUrl.takeLast(15) }}")

                TikTokAppLauncher.openUserProfile(context, targetUrl)
                delay(1200L)

                // Kích hoạt Accessibility bấm tương tác nếu có quyền
                if (TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
                    XsmmTaskAutomationBridge.triggerTask(
                        taskType = jobType,
                        swipeBefore = false,
                        returnHomeAndSwipe = true,
                        durationSeconds = countdown
                    )
                }

                // Chờ đếm ngược an toàn
                for (sec in countdown downTo 1) {
                    if (!GolikeRunningManager.isRunning(account.id)) break
                    onStatusChange("Làm job $shortType | Chờ nhận xu sau ${sec}s...")
                    delay(1000L)
                }
                if (!GolikeRunningManager.isRunning(account.id)) break

                // 3. Báo hoàn thành (Complete Job)
                onStatusChange("Đang gửi báo cáo hoàn thành...")
                val compRes = client.completeTikTokJob(adsId, golikeAccId)

                if (compRes != null && compRes.optBoolean("success", false)) {
                    val price = compRes.optJSONObject("data")?.optInt("prices", 35) ?: 35
                    onStatusChange("Hoàn thành +$price đ | Nghỉ ${cfg.delayMinSeconds}s")
                    onJobSuccess(price)
                } else {
                    val failMsg = compRes?.optString("message")?.takeIf { it.isNotBlank() } ?: "Lỗi hoàn thành nhiệm vụ"
                    onStatusChange("Lỗi: $failMsg")

                    // Bỏ qua nhiệm vụ (Skip Job)
                    client.skipJob(adsId, objectId, golikeAccId, jobType)
                    onJobFailed(failMsg)
                }

                // Delay ngẫu nhiên giữa các nhiệm vụ
                val randomDelay = (cfg.delayMinSeconds..cfg.delayMaxSeconds).random()
                delay(randomDelay * 1000L)

            } catch (e: Exception) {
                onStatusChange("Lỗi: ${e.message}")
                delay(5000L)
            }
        }
    }
}
