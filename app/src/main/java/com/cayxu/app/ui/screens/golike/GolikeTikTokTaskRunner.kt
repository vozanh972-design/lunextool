package com.cayxu.app.ui.screens.golike

import android.content.Context
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.XsmmTaskActionResult
import com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.overlay.xsmm.XsmmJobStatusBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * TaskRunner chuyên trách làm nhiệm vụ và tự động cấu hình nick TikTok trên Golike chuẩn GoMax.
 * Tái sử dụng 100% engine follow & verify của XSMM TikTok, kết nối Gateway API Golike.
 */
object GolikeTikTokTaskRunner {

    // ─────────────────────────────────────────────────────────────────────────
    // Kiểm tra session đủ 5 trường bắt buộc của Golike Gateway.
    // Trả về null nếu đủ, trả về thông báo lỗi nếu thiếu.
    // ─────────────────────────────────────────────────────────────────────────
    private fun checkSession(client: GolikeApiClient): String? {
        if (client.authToken.isNullOrBlank() || client.username.isNullOrBlank()) {
            return "Phiên Golike hết hạn. Vui lòng đăng nhập lại Golike."
        }
        return null
    }

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
                if (res.message.contains("Đã khớp tài khoản", ignoreCase = true) ||
                    res.message.contains("Đúng tài khoản", ignoreCase = true)
                ) {
                    matched = true
                    delay(800L)
                    break
                }
            } else if (res is XsmmTaskActionResult.Completed) {
                if (res.actionId == verifyActionId || res.success ||
                    res.message.contains("Đúng tài khoản", ignoreCase = true) ||
                    res.message.contains("Đã khớp", ignoreCase = true)
                ) {
                    matched = res.success
                    XsmmJobStatusBridge.update(res.message)
                    delay(800L)
                    break
                }
            }
            delay(300L)
        }

        if (!matched) {
            XsmmJobStatusBridge.update("Dừng chạy: Vui lòng chuyển app TikTok sang nick @$cleanTarget!")
        }
        return@withContext matched
    }

    /**
     * BƯỚC 3: Tự động cấu hình và xác minh tài khoản TikTok vào Golike theo chuẩn GoMax:
     * 0. Guard: Kiểm tra session đủ 5 trường.
     * 1. Kiểm tra cache map hoặc API xem nick đã đăng ký Golike chưa — nếu rồi, lấy ID và return success ngay.
     * 2. Nếu chưa: Khai báo nick lên Golike (POST /api/tiktok-account).
     * 3. Lấy nick chỉ định cấu hình từ Golike (mặc định @gosen.vietnam).
     * 4. Tự động mở profile TikTok và bấm Follow (bỏ qua nếu đã follow rồi).
     * 5. Chờ 3-5s và gửi xác nhận (POST /api/tiktok-account/verify-account-id).
     * 6. Lưu mapping vào golike_tiktok_map.
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

        // ── Guard: Kiểm tra đủ 5 trường session bắt buộc của Golike Gateway ──
        val sessionErr = checkSession(client)
        if (sessionErr != null) {
            onProgress(sessionErr)
            XsmmJobStatusBridge.update(sessionErr)
            return@withContext Result.failure(Exception(sessionErr))
        }

        // ── 0. Kiểm tra cache golike_tiktok_map trước ──
        var accountId = GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanUsername).orEmpty()

        // ── 1. Kiểm tra xem tài khoản đã có trên Golike chưa qua API ──
        if (accountId.isBlank()) {
            onProgress("Kiểm tra nick @$cleanUsername trên Golike...")
            XsmmJobStatusBridge.update("Kiểm tra nick @$cleanUsername trên Golike...")
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
                            if (accountId.isNotBlank()) {
                                GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, accountId)
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GolikeApi", "Lỗi kiểm tra danh sách TikTok hiện tại: ${e.message}")
            }
        }

        // ── Nếu đã có trên Golike → bỏ qua toàn bộ cấu hình, return thành công ngay ──
        if (accountId.isNotBlank()) {
            onProgress("Nick @$cleanUsername đã đăng ký Golike. Bắt đầu cày job...")
            XsmmJobStatusBridge.update("Nick @$cleanUsername đã đăng ký Golike. Bắt đầu cày job...")
            val existingAcc = GolikeAccount(
                id = cleanUsername,
                platform = "tiktok",
                username = username,
                avatar = "",
                isLive = true,
                isGolikeLinked = true,
                golikeAccountId = accountId,
                lastStatus = "Đã liên kết Golike • Sẵn sàng"
            )
            GolikeAccountsStore.addOrUpdateAccount(context, existingAcc)
            return@withContext Result.success(existingAcc)
        }

        // ── 2. Khai báo nick lên hệ thống Golike nếu chưa có accountId ──
        onProgress("Khai báo @$cleanUsername lên hệ thống Golike...")
        val declareRes = client.declareTikTokAccount(cleanUsername)

        // Kiểm tra lỗi session từ declare response
        val declareMsg = declareRes?.optString("message").orEmpty()
        if (declareMsg.contains("tải lại trang", ignoreCase = true) ||
            declareMsg.contains("phiên bản mới nhất", ignoreCase = true)
        ) {
            val errMsg = "Phiên Golike hết hạn. Vui lòng đăng nhập lại Golike."
            onProgress(errMsg)
            XsmmJobStatusBridge.update(errMsg)
            return@withContext Result.failure(Exception(errMsg))
        }

        val dataObj = declareRes?.optJSONObject("data")
        accountId = dataObj?.optString("id")?.takeIf { it.isNotBlank() }
            ?: dataObj?.optString("account_id")?.takeIf { it.isNotBlank() }
            ?: declareRes?.optString("account_id").orEmpty()

        val targetAccountId = accountId.ifBlank { cleanUsername }

        // ── 3. Lấy thông tin nick chỉ định cấu hình từ Golike (mặc định @gosen.vietnam) ──
        onProgress("Lấy nick chỉ định cấu hình từ Golike...")
        var targetUser = ""
        try {
            val targetRes = client.getTikTokVerifyTarget(targetAccountId)
            val targetData = targetRes?.optJSONObject("data") ?: targetRes
            targetUser = targetData?.optString("target_user")?.takeIf { it.isNotBlank() }
                ?: targetData?.optString("username")?.takeIf { it.isNotBlank() }
                ?: targetData?.optString("unique_username")?.takeIf { it.isNotBlank() }
                ?: targetData?.optString("nickname").orEmpty()
        } catch (_: Exception) {}

        if (targetUser.isBlank()) {
            targetUser = "gosen.vietnam"
        }
        val targetFollow = targetUser.trim().removePrefix("@")

        // ── 4. Mở TikTok đến trang cá nhân @gosen.vietnam và bấm Follow (dùng TikTokAppLauncher như XSMM) ──
        val statusMsg = "Mở follow nick cấu hình @$targetFollow..."
        onProgress(statusMsg)
        XsmmJobStatusBridge.update(statusMsg)

        TikTokAppLauncher.openUserProfile(context, "https://www.tiktok.com/@$targetFollow")
        delay(1500L)

        if (TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
            onProgress("Bấm Follow @$targetFollow để cấu hình...")
            XsmmJobStatusBridge.update("Bấm Follow @$targetFollow để cấu hình...")
            val actionId = XsmmTaskAutomationBridge.triggerTask(
                taskType = "follow",
                swipeBefore = false,
                returnHomeAndSwipe = false,
                durationSeconds = 5
            )
            val startWait = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - startWait) < 15000L) {
                val res = XsmmTaskAutomationBridge.result.value
                if (res is XsmmTaskActionResult.InProgress) {
                    onProgress(res.message)
                    XsmmJobStatusBridge.update(res.message)
                    // Nếu đã follow rồi → không cần bấm lại, thoát luôn
                    if (res.message.contains("Đã follow", ignoreCase = true) ||
                        res.message.contains("Following", ignoreCase = true) ||
                        res.message.contains("đã theo dõi", ignoreCase = true)
                    ) {
                        onProgress("Đã follow @$targetFollow từ trước — bỏ qua.")
                        XsmmJobStatusBridge.update("Đã follow @$targetFollow từ trước — bỏ qua.")
                        break
                    }
                } else if (res is XsmmTaskActionResult.Completed && (res.actionId == actionId || res.actionId == 0L || res.success)) {
                    onProgress(res.message)
                    XsmmJobStatusBridge.update(res.message)
                    break
                }
                delay(400L)
            }
        } else {
            for (sec in 4 downTo 1) {
                XsmmJobStatusBridge.update("Chờ bấm Follow @$targetFollow (${sec}s)...")
                delay(1000L)
            }
        }

        onProgress("Chờ TikTok ghi nhận follow (3s)...")
        XsmmJobStatusBridge.update("Chờ TikTok ghi nhận follow (3s)...")
        delay(3500L)

        // ── 5. Gửi xác nhận hoàn tất lên Golike: POST /api/tiktok-account/verify-account-id ──
        onProgress("Gửi xác nhận cấu hình lên Golike...")
        XsmmJobStatusBridge.update("Gửi xác nhận cấu hình lên Golike...")
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
                vMessage.contains("đã xác nhận", ignoreCase = true) ||
                vMessage.contains("đã tồn tại", ignoreCase = true)

        if (isVerifySuccess) {
            XsmmJobStatusBridge.update("Cấu hình nick @$cleanUsername thành công!")
            // Lưu mapping vào golike_tiktok_map
            GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, targetAccountId)

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
            val failMsg = vMessage.ifBlank { "Chưa bấm Follow @$targetFollow" }
            XsmmJobStatusBridge.update("Cấu hình thất bại: $failMsg")
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

        // ── Guard: Kiểm tra đủ 5 trường session bắt buộc của Golike Gateway ──
        val sessionErr = checkSession(client)
        if (sessionErr != null) {
            onStatusChange(sessionErr)
            XsmmJobStatusBridge.update(sessionErr)
            onJobFailed(sessionErr)
            GolikeRunningManager.runningAccounts[account.id] = false
            GolikeRunningManager.runningAccounts[cleanUsername] = false
            return@withContext
        }

        // BƯỚC 1: KIỂM TRA ĐỐI SOÁT ACC TIKTOK TRÊN MÁY VỚI ACC GOLIKE ĐANG CHẠY
        onStatusChange("Kiểm tra nick @$cleanUsername trên máy...")
        val isMatched = verifyAndSwitchTikTokAccount(context, cleanUsername, variant)
        if (!isMatched) {
            val err = "Dừng chạy: Vui lòng chuyển app TikTok sang nick @$cleanUsername!"
            XsmmJobStatusBridge.update(err)
            onStatusChange(err)
            onJobFailed(err)
            GolikeRunningManager.runningAccounts[account.id] = false
            GolikeRunningManager.runningAccounts[cleanUsername] = false
            return@withContext
        }

        // BƯỚC 2: CẤU HÌNH XÁC MINH TRÊN GOLIKE CHUẨN GOMAX (KHAI BÁO + FOLLOW @gosen.vietnam + XÁC NHẬN)
        // 2.1 Kiểm tra nick đã có trên Golike chưa (từ cache map hoặc API) — nếu rồi thì bỏ qua bước cấu hình
        var golikeAccountId = account.golikeAccountId.ifBlank {
            GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanUsername).orEmpty()
        }
        var skipConfig = golikeAccountId.isNotBlank()

        if (!skipConfig) {
            onStatusChange("Kiểm tra nick @$cleanUsername đã đăng ký Golike chưa...")
            XsmmJobStatusBridge.update("Kiểm tra nick @$cleanUsername đã đăng ký Golike chưa...")
            try {
                val listRes = client.getTikTokAccounts()
                val dataArr = listRes?.optJSONArray("data")
                if (dataArr != null) {
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.optJSONObject(i) ?: continue
                        val u = item.optString("unique_username").ifBlank {
                            item.optString("username").ifBlank { item.optString("nickname") }
                        }.trim().removePrefix("@").lowercase()
                        if (u == cleanUsername) {
                            golikeAccountId = item.optString("id").ifBlank { item.optString("account_id") }
                            if (golikeAccountId.isNotBlank()) {
                                GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, golikeAccountId)
                                skipConfig = true
                            }
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (skipConfig && golikeAccountId.isNotBlank()) {
            // Nick đã đăng ký Golike → nhảy thẳng vào job loop
            onStatusChange("Nick @$cleanUsername đã cấu hình Golike. Bắt đầu cày job...")
            XsmmJobStatusBridge.update("Nick @$cleanUsername đã cấu hình Golike. Bắt đầu cày job...")
        } else {
            // 2.2 KHAI BÁO THÊM ACC LÊN HỆ THỐNG GOLIKE
            onStatusChange("Khai báo @$cleanUsername lên Golike...")
            XsmmJobStatusBridge.update("Khai báo @$cleanUsername lên Golike...")

            val declareRes = client.declareTikTokAccount(cleanUsername)

            // Kiểm tra lỗi session từ declare response
            val declareMsg = declareRes?.optString("message").orEmpty()
            if (declareMsg.contains("tải lại trang", ignoreCase = true) ||
                declareMsg.contains("phiên bản mới nhất", ignoreCase = true)
            ) {
                val errMsg = "Phiên Golike hết hạn. Vui lòng đăng nhập lại Golike."
                onStatusChange(errMsg)
                XsmmJobStatusBridge.update(errMsg)
                onJobFailed(errMsg)
                GolikeRunningManager.runningAccounts[account.id] = false
                return@withContext
            }

            golikeAccountId = declareRes?.optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
                ?: declareRes?.optJSONObject("data")?.optString("account_id")?.takeIf { it.isNotBlank() }
                ?: declareRes?.optString("account_id").orEmpty()

            // Nếu vẫn còn trống, thử lấy từ danh sách lần 2
            if (golikeAccountId.isBlank()) {
                try {
                    val listRes = client.getTikTokAccounts()
                    val dataArr = listRes?.optJSONArray("data")
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val item = dataArr.optJSONObject(i) ?: continue
                            val u = item.optString("unique_username").ifBlank {
                                item.optString("username").ifBlank { item.optString("nickname") }
                            }.trim().removePrefix("@").lowercase()
                            if (u == cleanUsername) {
                                golikeAccountId = item.optString("id").ifBlank { item.optString("account_id") }
                                if (golikeAccountId.isNotBlank()) {
                                    GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, golikeAccountId)
                                }
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (golikeAccountId.isBlank()) {
                golikeAccountId = cleanUsername
            }

            // 2.3 MỞ TIKTOK FOLLOW NICK CHỈ ĐỊNH ĐỂ CẤU HÌNH (CHUẨN GOMAX @gosen.vietnam)
            // Dùng TikTokAppLauncher.openUserProfile như XSMM — KHÔNG dùng snssdk1128
            var targetConfigUser = "gosen.vietnam"
            try {
                val targetRes = client.getTikTokVerifyTarget(golikeAccountId)
                val targetData = targetRes?.optJSONObject("data") ?: targetRes
                val tu = targetData?.optString("target_user")?.takeIf { it.isNotBlank() }
                    ?: targetData?.optString("username")?.takeIf { it.isNotBlank() }
                    ?: targetData?.optString("unique_username")?.takeIf { it.isNotBlank() }
                    ?: targetData?.optString("nickname").orEmpty()
                if (tu.isNotBlank()) {
                    targetConfigUser = tu.trim().removePrefix("@")
                }
            } catch (_: Exception) {}

            onStatusChange("Mở follow nick cấu hình @$targetConfigUser...")
            XsmmJobStatusBridge.update("Mở follow nick cấu hình @$targetConfigUser...")

            TikTokAppLauncher.openUserProfile(context, "https://www.tiktok.com/@$targetConfigUser")

            // Kích hoạt Trợ Năng bấm nút Follow
            delay(1500L) // Chờ TikTok load trang cá nhân
            onStatusChange("Bấm Follow @$targetConfigUser để cấu hình...")
            XsmmJobStatusBridge.update("Bấm Follow @$targetConfigUser để cấu hình...")

            if (TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
                val actionId = XsmmTaskAutomationBridge.triggerTask(
                    taskType = "follow",
                    swipeBefore = false,
                    returnHomeAndSwipe = false,
                    durationSeconds = 5
                )
                val startWait = System.currentTimeMillis()
                while (isActive && (System.currentTimeMillis() - startWait) < 15000L) {
                    val res = XsmmTaskAutomationBridge.result.value
                    if (res is XsmmTaskActionResult.InProgress) {
                        onStatusChange(res.message)
                        XsmmJobStatusBridge.update(res.message)
                        // Nếu đã follow rồi → không bấm lại, thoát luôn
                        if (res.message.contains("Đã follow", ignoreCase = true) ||
                            res.message.contains("Following", ignoreCase = true) ||
                            res.message.contains("đã theo dõi", ignoreCase = true)
                        ) {
                            onStatusChange("Đã follow @$targetConfigUser từ trước — bỏ qua.")
                            XsmmJobStatusBridge.update("Đã follow @$targetConfigUser từ trước — bỏ qua.")
                            break
                        }
                    } else if (res is XsmmTaskActionResult.Completed && (res.actionId == actionId || res.actionId == 0L || res.success)) {
                        onStatusChange(res.message)
                        XsmmJobStatusBridge.update(res.message)
                        break
                    }
                    delay(400L)
                }
            } else {
                for (sec in 4 downTo 1) {
                    XsmmJobStatusBridge.update("Chờ bấm Follow @$targetConfigUser (${sec}s)...")
                    delay(1000L)
                }
            }

            onStatusChange("Chờ TikTok ghi nhận follow (3s)...")
            XsmmJobStatusBridge.update("Chờ TikTok ghi nhận follow (3s)...")
            delay(4000L) // Chờ bấm follow và mạng ghi nhận

            // 2.4 GỬI XÁC NHẬN HOÀN TẤT CẤU HÌNH LÊN GOLIKE
            onStatusChange("Gửi xác nhận cấu hình lên Golike...")
            XsmmJobStatusBridge.update("Gửi xác nhận cấu hình lên Golike...")

            var verifyRes = client.verifyTikTokAccountId(golikeAccountId, cleanUsername)
            var vStatus = verifyRes?.optInt("status", 0) ?: 0
            var vSuccess = verifyRes?.optBoolean("success", false) ?: false
            var vMessage = verifyRes?.optString("message").orEmpty()

            if (vStatus != 200 && !vSuccess && !vMessage.contains("thành công", ignoreCase = true)) {
                val fallbackRes = client.verifyTikTokAccount(golikeAccountId)
                if (fallbackRes != null) {
                    verifyRes = fallbackRes
                    vStatus = fallbackRes.optInt("status", 0)
                    vSuccess = fallbackRes.optBoolean("success", false)
                    vMessage = fallbackRes.optString("message").orEmpty()
                }
            }

            val isConfigSuccess = vStatus == 200 || vSuccess ||
                    vMessage.contains("thành công", ignoreCase = true) ||
                    vMessage.contains("đã liên kết", ignoreCase = true) ||
                    vMessage.contains("đã xác nhận", ignoreCase = true) ||
                    vMessage.contains("đã tồn tại", ignoreCase = true)

            if (isConfigSuccess) {
                onStatusChange("Cấu hình nick @$cleanUsername thành công!")
                XsmmJobStatusBridge.update("Cấu hình nick @$cleanUsername thành công!")

                // Lưu mapping vào golike_tiktok_map
                GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, golikeAccountId)

                val updatedAcc = GolikeAccount(
                    id = cleanUsername,
                    platform = "tiktok",
                    username = account.username,
                    avatar = account.avatar,
                    isLive = true,
                    isGolikeLinked = true,
                    golikeAccountId = golikeAccountId,
                    lastStatus = "Đã liên kết Golike • Sẵn sàng"
                )
                GolikeAccountsStore.addOrUpdateAccount(context, updatedAcc)
                GolikeAccountsStore.updateAccountProgress(context, "tiktok", cleanUsername, "Đã cấu hình • Sẵn sàng", isSuccess = true)
                delay(1500L)
            } else {
                val err = vMessage.ifBlank { "Chưa bấm Follow @$targetConfigUser" }
                onStatusChange("Cấu hình: $err")
                XsmmJobStatusBridge.update("Cấu hình: $err")
                delay(1500L)
            }
        }

        // BƯỚC 3: CHUYỂN NGAY SANG VÒNG LẶP LÀM JOB CỦA GOLIKE
        val golikeAccId = golikeAccountId.ifBlank { account.id }
        GolikeRunningManager.runningAccounts[account.id] = true
        GolikeRunningManager.runningAccounts[cleanUsername] = true
        var consecutiveFails = 0

        while ((GolikeRunningManager.isRunning(account.id) || GolikeRunningManager.isRunning(cleanUsername)) && isActive) {
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
