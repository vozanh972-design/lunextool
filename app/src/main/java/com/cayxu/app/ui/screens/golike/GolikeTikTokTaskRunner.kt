package com.cayxu.app.ui.screens.golike

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.XsmmTaskActionResult
import com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.overlay.xsmm.XsmmJobStatusBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * TaskRunner chuyên trách làm nhiệm vụ và tự động cấu hình nick TikTok trên Golike chuẩn GoMax.
 * Tái sử dụng 100% engine follow & verify của XSMM TikTok, kết nối Gateway API Golike.
 */
object GolikeTikTokTaskRunner {

    /**
     * Kiểm tra trạng thái xác minh thực sự của tài khoản trên Golike Server qua API.
     */
    suspend fun checkIfAccountVerifiedOnGolike(client: GolikeApiClient, username: String): Boolean = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@").lowercase()
        try {
            val res = client.getTikTokAccounts()
            val dataArray = res?.optJSONArray("data") ?: return@withContext false
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val u = item.optString("unique_username").ifBlank {
                    item.optString("username").ifBlank { item.optString("nickname") }
                }.trim().removePrefix("@").lowercase()
                if (u == clean) {
                    val isVerified = item.optBoolean("is_verified", false) ||
                            item.optInt("is_verified", 0) == 1 ||
                            item.optString("status").contains("active", ignoreCase = true) ||
                            item.optString("status") == "1"
                    return@withContext isVerified
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * BƯỚC 2: Kiểm tra đối soát tài khoản TikTok trên máy với tài khoản Golike cần chạy.
     * Mở TikTok -> Quét @handle bằng Accessibility UI -> Chuyển tài khoản nếu bị lệch.
     */
    suspend fun verifyAndSwitchTikTokAccount(
        context: Context,
        targetUsername: String,
        variant: TikTokAppVariant = TikTokAppVariant.STANDARD
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanTarget = targetUsername.trim().removePrefix("@").lowercase()
        if (cleanTarget.isBlank()) return@withContext false

        val msg = "Kiểm tra nick TikTok @$cleanTarget trên máy..."
        XsmmJobStatusBridge.update(msg)

        if (!TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
            XsmmJobStatusBridge.update("Lỗi: Chưa bật quyền Trợ năng cho CayXu!")
            return@withContext false
        }

        // Mở app TikTok về tab Hồ sơ
        TikTokAppLauncher.launch(context, variant)
        delay(1500L)

        val verifyActionId = XsmmTaskAutomationBridge.triggerVerifyAccount(cleanTarget, variant)
        val startTime = System.currentTimeMillis()
        val maxWaitTime = 60000L // Chờ tối đa 60s
        var matched = false

        while (isActive && (System.currentTimeMillis() - startTime) < maxWaitTime) {
            val res = XsmmTaskAutomationBridge.result.value
            if (res is XsmmTaskActionResult.InProgress) {
                XsmmJobStatusBridge.update(res.message)
            } else if (res is XsmmTaskActionResult.Completed && res.actionId == verifyActionId) {
                matched = res.success
                XsmmJobStatusBridge.update(res.message)
                delay(1000L)
                break
            }
            delay(400L)
        }

        if (!matched) {
            XsmmJobStatusBridge.update("Dừng chạy: Vui lòng chuyển app TikTok sang nick @$cleanTarget!")
        }
        return@withContext matched
    }

    /**
     * BƯỚC 3: Tự động cấu hình và xác minh tài khoản TikTok vào Golike theo chuẩn GoMax:
     * 1. Khai báo nick: POST /api/tiktok-account {"unique_username": "..."}
     * 2. Lấy nick chỉ định cấu hình: GET /api/tiktok-account/verify-account-id (mặc định @gosen.vietnam).
     * 3. Tự động mở profile TikTok và bấm Follow qua Accessibility.
     * 4. Chờ 3-5s và gửi xác nhận: POST /api/tiktok-account/verify-account-id
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
        XsmmJobStatusBridge.update("Kiểm tra nick @$cleanUsername trên Golike...")

        var accountId = ""

        // 1. Kiểm tra xem tài khoản đã có trên Golike chưa
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
                        val isVerified = item.optBoolean("is_verified", false) || item.optInt("is_verified", 0) == 1
                        if (isVerified) {
                            android.util.Log.d("GolikeApi", "Tài khoản @$cleanUsername đã xác minh trên Golike với ID: $accId")
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
                        } else {
                            // Nick đã có trên Golike nhưng CHƯA verify -> Lấy ID để chạy tiếp bước Follow cấu hình
                            accountId = accId
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GolikeApi", "Lỗi kiểm tra danh sách TikTok hiện tại: ${e.message}")
        }

        // 2. Khai báo nick lên hệ thống Golike nếu chưa có accountId
        if (accountId.isBlank()) {
            onProgress("Khai báo @$cleanUsername lên hệ thống Golike...")
            val declareRes = client.declareTikTokAccount(cleanUsername)
            val dataObj = declareRes?.optJSONObject("data")
            val status = declareRes?.optInt("status", 0) ?: 0
            val isActuallyVerified = (status == 200 && dataObj?.optBoolean("is_verified", false) == true) || (dataObj?.optInt("is_verified", 0) == 1)

            accountId = dataObj?.optString("id")?.takeIf { it.isNotBlank() }
                ?: dataObj?.optString("account_id")?.takeIf { it.isNotBlank() }
                ?: declareRes?.optString("account_id").orEmpty()

            if (isActuallyVerified && accountId.isNotBlank()) {
                val verifiedAcc = GolikeAccount(
                    id = cleanUsername,
                    platform = "tiktok",
                    username = username,
                    avatar = dataObj?.optString("avatar").orEmpty(),
                    isLive = true,
                    isGolikeLinked = true,
                    golikeAccountId = accountId,
                    lastStatus = "Đã liên kết Golike • Sẵn sàng"
                )
                GolikeAccountsStore.addOrUpdateAccount(context, verifiedAcc)
                return@withContext Result.success(verifiedAcc)
            }
        }

        // Quét lại danh sách để lấy accountId nếu sau declare vẫn rỗng
        if (accountId.isBlank()) {
            try {
                val listRes = client.getTikTokAccounts()
                val listData = listRes?.optJSONArray("data")
                if (listData != null) {
                    for (i in 0 until listData.length()) {
                        val item = listData.optJSONObject(i) ?: continue
                        val sUname = item.optString("unique_username").ifBlank {
                            item.optString("username").ifBlank { item.optString("nickname") }
                        }.trim().removePrefix("@").lowercase()
                        if (sUname == cleanUsername) {
                            accountId = item.optString("id").ifBlank { item.optString("account_id") }
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val targetAccountId = accountId.ifBlank { cleanUsername }

        // 3. Lấy thông tin nick chỉ định cấu hình từ Golike: GET /api/tiktok-account/verify-account-id?account_id={accountId}
        onProgress("Lấy nick chỉ định cấu hình từ Golike...")
        var targetUser = ""
        var targetLink = ""
        try {
            val targetRes = client.getTikTokVerifyTarget(targetAccountId)
            val targetData = targetRes?.optJSONObject("data") ?: targetRes

            targetLink = targetData?.optString("link").takeIf { !it.isNullOrBlank() }
                ?: targetData?.optString("target_link").takeIf { !it.isNullOrBlank() }
                ?: targetData?.optString("target_url").orEmpty()

            targetUser = targetData?.optString("target_user").takeIf { !it.isNullOrBlank() }
                ?: targetData?.optString("username").takeIf { !it.isNullOrBlank() }
                ?: targetData?.optString("unique_username").takeIf { !it.isNullOrBlank() }
                ?: targetData?.optString("nickname").orEmpty()
        } catch (_: Exception) {}

        // Chuẩn GoMax: Nếu API trả về rỗng, mặc định mục tiêu chỉ định chuẩn của GoMax là @gosen.vietnam
        if (targetUser.isBlank()) {
            targetUser = "gosen.vietnam"
        }
        if (targetLink.isBlank()) {
            targetLink = "https://www.tiktok.com/@gosen.vietnam"
        }

        val targetFollow = targetUser.trim().removePrefix("@")

        // 4. Mở TikTok trực tiếp đến trang cá nhân @gosen.vietnam và bấm Follow
        val statusMsg = "Đang follow nick cấu hình @$targetFollow..."
        onProgress(statusMsg)
        XsmmJobStatusBridge.update(statusMsg)

        val uri = if (targetFollow.all { it.isDigit() }) {
            Uri.parse("snssdk1128://user/profile/$targetFollow")
        } else {
            Uri.parse("snssdk1128://user/profile?unique_id=$targetFollow")
        }
        val tiktokIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        var opened = false
        try {
            context.startActivity(tiktokIntent)
            opened = true
        } catch (_: Exception) {}

        if (!opened) {
            val webUrl = targetLink.ifBlank { "https://www.tiktok.com/@$targetFollow" }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {
                TikTokAppLauncher.openUserProfile(context, webUrl)
            }
        }
        delay(1500L)

        if (TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
            onProgress("Tự động bấm nút Follow @$targetFollow...")
            XsmmJobStatusBridge.update("Đang bấm Follow @$targetFollow...")
            val actionId = XsmmTaskAutomationBridge.triggerTask(
                taskType = "follow",
                swipeBefore = false,
                returnHomeAndSwipe = false,
                durationSeconds = 5
            )
            val startWait = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - startWait) < 20000L) {
                val res = XsmmTaskAutomationBridge.result.value
                if (res is XsmmTaskActionResult.InProgress) {
                    onProgress(res.message)
                    XsmmJobStatusBridge.update(res.message)
                } else if (res is XsmmTaskActionResult.Completed && res.actionId == actionId) {
                    onProgress(res.message)
                    XsmmJobStatusBridge.update(res.message)
                    break
                }
                delay(400L)
            }
        } else {
            onProgress("Đang chờ Follow trên TikTok...")
            for (sec in 5 downTo 1) {
                XsmmJobStatusBridge.update("Chờ Follow @$targetFollow (${sec}s)...")
                delay(1000L)
            }
        }

        // Đợi 3-5 giây để TikTok ghi nhận lượt theo dõi chuẩn GoMax
        onProgress("Chờ TikTok ghi nhận follow (3s)...")
        XsmmJobStatusBridge.update("Chờ TikTok ghi nhận follow (3s)...")
        delay(3500L)

        // 5. Gửi xác nhận hoàn tất lên Golike: POST /api/tiktok-account/verify-account-id
        onProgress("Xác nhận hoàn tất cấu hình lên Golike...")
        XsmmJobStatusBridge.update("Xác nhận cấu hình lên Golike...")
        var verifyRes = client.verifyTikTokAccountId(targetAccountId, cleanUsername)
        var vStatus = verifyRes?.optInt("status", 0) ?: 0
        var vSuccess = verifyRes?.optBoolean("success", false) ?: false
        var vMessage = verifyRes?.optString("message").orEmpty()

        if (vStatus != 200 && !vSuccess && !vMessage.contains("thành công", ignoreCase = true)) {
            val fallbackRes = client.verifyTikTokAccount(targetAccountId)
            if (fallbackRes != null) {
                verifyRes = fallbackRes
                vStatus = fallbackRes.optInt("status", 0)
                vSuccess = fallbackRes.optBoolean("success", false)
                vMessage = fallbackRes.optString("message").orEmpty()
            }
        }

        val isVerifySuccess = vStatus == 200 || vSuccess ||
                vMessage.contains("thành công", ignoreCase = true) ||
                vMessage.contains("đã liên kết", ignoreCase = true) ||
                vMessage.contains("đã xác nhận", ignoreCase = true)

        if (isVerifySuccess) {
            XsmmJobStatusBridge.update("Cấu hình thành công @$cleanUsername!")
            val finalAcc = GolikeAccount(
                id = cleanUsername,
                platform = "tiktok",
                username = username,
                avatar = "",
                isLive = true,
                isGolikeLinked = true,
                golikeAccountId = targetAccountId,
                lastStatus = "Đã liên kết Golike • Sẵn sàng"
            )
            GolikeAccountsStore.addOrUpdateAccount(context, finalAcc)
            return@withContext Result.success(finalAcc)
        } else {
            val failMsg = "Cấu hình nick thất bại. Hãy chắc chắn đã bấm Follow @$targetFollow!"
            XsmmJobStatusBridge.update(failMsg)
            return@withContext Result.failure(Exception(failMsg))
        }
    }

    /**
     * Vòng lặp nhận và thực thi nhiệm vụ TikTok độc lập cho một tài khoản.
     * TUYỆT ĐỐI KHÔNG ĐƯỢC LẤY JOB KHI CHƯA ĐÚNG NICK HOẶC CHƯA CẤU HÌNH THÀNH CÔNG.
     */
    suspend fun runTikTokTaskLoop(
        context: Context,
        account: GolikeAccount,
        client: GolikeApiClient,
        variant: TikTokAppVariant = TikTokAppVariant.STANDARD,
        onStatusChange: (String) -> Unit,
        onJobSuccess: (earned: Int) -> Unit,
        onJobFailed: (reason: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val cfg = GolikeRunConfigStore.get(context, "tiktok")
        val cleanUsername = account.username.trim().removePrefix("@").lowercase()

        // BƯỚC 1: KIỂM TRA ĐỐI SOÁT ACC TIKTOK TRÊN MÁY VỚI ACC GOLIKE ĐANG CHẠY
        onStatusChange("Kiểm tra nick @$cleanUsername trên máy...")
        val isMatched = verifyAndSwitchTikTokAccount(context, cleanUsername, variant)
        if (!isMatched) {
            val err = "Dừng chạy: Vui lòng chuyển app TikTok sang nick @$cleanUsername!"
            XsmmJobStatusBridge.update(err)
            onStatusChange(err)
            onJobFailed(err)
            GolikeRunningManager.runningAccounts[account.id] = false
            return@withContext
        }

        // BƯỚC 2: KIỂM TRA VÀ CẤU HÌNH XÁC MINH TRÊN GOLIKE TRƯỚC KHI LÀM JOB
        var currentAcc = account
        val isVerified = checkIfAccountVerifiedOnGolike(client, cleanUsername)
        if (!isVerified || !currentAcc.isGolikeLinked || currentAcc.golikeAccountId.isBlank()) {
            onStatusChange("Đang cấu hình nick @$cleanUsername vào Golike...")
            val linkRes = verifyAndLinkTikTokAccount(context, client, cleanUsername) { step ->
                onStatusChange(step)
            }
            if (linkRes.isSuccess) {
                currentAcc = linkRes.getOrThrow()
                onStatusChange("Cấu hình thành công @$cleanUsername!")
                delay(1200L)
            } else {
                val failReason = linkRes.exceptionOrNull()?.message ?: "Cấu hình nick thất bại. Hãy chắc chắn đã bấm Follow @gosen.vietnam!"
                XsmmJobStatusBridge.update(failReason)
                onStatusChange(failReason)
                onJobFailed(failReason)
                GolikeRunningManager.runningAccounts[account.id] = false
                // DỪNG LẠI NGAY LẬP TỨC - CẤM ĐƯỢC GỌI API LẤY JOB!
                return@withContext
            }
        }

        // BƯỚC 3: CHỈ KHI THỎA MÃN CẢ 2 ĐIỀU KIỆN MỚI LẤY JOB VÀ LÀM NHIỆM VỤ
        val golikeAccId = currentAcc.golikeAccountId.ifBlank { currentAcc.id }
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
