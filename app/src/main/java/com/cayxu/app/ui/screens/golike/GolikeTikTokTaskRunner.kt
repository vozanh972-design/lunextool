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

/**
 * TaskRunner chuyên trách làm nhiệm vụ và tự động cấu hình nick TikTok trên Golike chuẩn GoMax.
 * Tái sử dụng 100% engine follow & verify của XSMM TikTok, kết nối Gateway API Golike.
 */
object GolikeTikTokTaskRunner {

    // ─────────────────────────────────────────────────────────────────────────
    // Kiểm tra session đủ 5 trường bắt buộc của Golike Gateway.
    // Trả về null nếu đủ, trả về thông báo lỗi cụ thể nếu thiếu.
    // ─────────────────────────────────────────────────────────────────────────
    private fun checkSession(client: GolikeApiClient): String? {
        if (client.authToken.isNullOrBlank()) {
            return "Lỗi: Chưa có token đăng nhập Golike (Vào app để đăng nhập)"
        }
        if (client.username.isNullOrBlank()) {
            return "Lỗi: Chưa có thông tin tài khoản Golike"
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
            val dataArray = res?.optJSONArray("data")
                ?: res?.optJSONObject("data")?.optJSONArray("data")
                ?: return@withContext false
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

        val verifyActionId = XsmmTaskAutomationBridge.triggerVerifyAccount(cleanTarget, variant, platform = "golike")
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
    /**
     * Tìm ID tài khoản TikTok đã có sẵn trên hệ thống Golike (qua GET /api/tiktok-account?limit=200).
     * Hỗ trợ cả 2 định dạng response: data là List trực tiếp [ ... ] hoặc Laravel Pagination data.data.
     */
    fun findExistingTikTokAccountId(client: GolikeApiClient, cleanUsername: String): String? {
        try {
            val listRes = client.getTikTokAccounts()
            android.util.Log.d("GolikeVerify", "GET /api/tiktok-account response: $listRes")
            val listData = listRes?.optJSONArray("data")
                ?: listRes?.optJSONObject("data")?.optJSONArray("data")

            if (listData != null) {
                val golikeLoginUsername = client.username?.trim()?.removePrefix("@")?.lowercase().orEmpty()
                val targetClean = cleanUsername.trim().removePrefix("@").lowercase()
                val targetAlphanum = targetClean.replace(Regex("[._\\s-]"), "")

                for (i in 0 until listData.length()) {
                    val item = listData.optJSONObject(i) ?: continue
                    val sUnique = item.optString("unique_username").trim().removePrefix("@").lowercase()
                    val sUniqueId = item.optString("unique_id").trim().removePrefix("@").lowercase()
                    val sNick = item.optString("nickname").trim().removePrefix("@").lowercase()
                    val sAccount = item.optString("tiktok_account").trim().removePrefix("@").lowercase()
                    val rawUser = item.optString("username").trim().removePrefix("@").lowercase()
                    val sUser = if (rawUser == golikeLoginUsername) "" else rawUser
                    val sName = item.optString("name").trim().removePrefix("@").lowercase()

                    val isMatch = sUnique == targetClean || sUniqueId == targetClean ||
                            sNick == targetClean || sAccount == targetClean ||
                            (sUser.isNotBlank() && sUser == targetClean) ||
                            (sName.isNotBlank() && sName == targetClean) ||
                            (targetAlphanum.isNotBlank() && (
                                sUnique.replace(Regex("[._\\s-]"), "") == targetAlphanum ||
                                sNick.replace(Regex("[._\\s-]"), "") == targetAlphanum
                            )) ||
                            (sUnique.isNotBlank() && (sUnique.contains(targetClean) || targetClean.contains(sUnique)))

                    if (isMatch) {
                        val rawId = item.opt("id") ?: item.opt("account_id")
                        val idStr = when (rawId) {
                            is Number -> rawId.toLong().toString()
                            is String -> rawId.trim()
                            else -> item.optString("id").ifBlank { item.optString("account_id") }
                        }
                        if (idStr.isNotBlank()) {
                            android.util.Log.d("GolikeVerify", "Khớp nick @$cleanUsername -> Golike account_id: $idStr")
                            return idStr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GolikeVerify", "Lỗi kiểm tra danh sách TikTok Golike: ${e.message}")
        }
        return null
    }

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

        // ── Nạp đầy đủ 100% session và headers phiên bản từ SharedPreferences vào client trước khi gọi API ──
        val token = GolikeAccountsStore.getToken(context).orEmpty()
        client.updateSession(
            authToken = token,
            tToken = GolikeAccountsStore.getTToken(context),
            deviceId = GolikeAccountsStore.getDeviceId(context),
            username = GolikeAccountsStore.getUsername(context),
            gAuth = GolikeAccountsStore.getGAuth(context),
            version = GolikeAccountsStore.getVersionApp(context),
            client = GolikeAccountsStore.getClient(context),
            scheme = GolikeAccountsStore.getScheme(context),
            webVersion = GolikeAccountsStore.getWebVersion(context),
            webVersionText = GolikeAccountsStore.getWebVersionText(context),
            webData = GolikeAccountsStore.getWebData(context),
            signingKey = GolikeAccountsStore.getSigningKey(context),
            userId = GolikeAccountsStore.getUserId(context).toLongOrNull(),
            context = context
        )

        // ── Guard: Kiểm tra đủ 5 trường session bắt buộc của Golike Gateway ──
        val sessionErr = checkSession(client)
        if (sessionErr != null) {
            onProgress(sessionErr)
            XsmmJobStatusBridge.update(sessionErr)
            return@withContext Result.failure(Exception(sessionErr))
        }

        // ── 0. Kiểm tra cache golike_tiktok_map trước ──
        var accountId = GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanUsername).orEmpty()

        // ── 1. BƯỚC 1: KIỂM TRA TÀI KHOẢN ĐÃ TỒN TẠI TRƯỚC KHI BẮT ĐẦU CẤU HÌNH ──
        if (accountId.isBlank()) {
            onProgress("Kiểm tra nick @$cleanUsername trên Golike...")
            XsmmJobStatusBridge.update("Kiểm tra nick @$cleanUsername trên Golike...")
            val existingId = findExistingTikTokAccountId(client, cleanUsername)
            if (!existingId.isNullOrBlank()) {
                accountId = existingId
                GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, accountId)
            }
        }

        // ── NẾU ĐÃ CÓ: BỎ QUA HOÀN TOÀN BƯỚC FOLLOW VÀ BƯỚC XÁC THỰC! ──
        if (accountId.isNotBlank()) {
            val readyMsg = "Tài khoản đã liên kết Golike. Đang lấy nhiệm vụ..."
            onProgress(readyMsg)
            XsmmJobStatusBridge.update(readyMsg)
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
            delay(800L)
            return@withContext Result.success(existingAcc)
        }

        // ── 2. Khai báo nick lên hệ thống Golike nếu chưa có accountId ──
        onProgress("Khai báo @$cleanUsername lên hệ thống Golike...")
        val declareRes = client.declareTikTokAccount(cleanUsername)
        android.util.Log.d("GolikeVerify", "declareTikTokAccount response: $declareRes")

        // Kiểm tra lỗi session từ declare response
        val declareMsg = declareRes?.optString("message").orEmpty()
        if (declareMsg.contains("tải lại trang", ignoreCase = true) ||
            declareMsg.contains("phiên bản mới nhất", ignoreCase = true)
        ) {
            val errMsg = "Golike: Nick @$cleanUsername chưa liên kết trên Golike (Vào app.golike.net để liên kết nick trước)"
            onProgress(errMsg)
            XsmmJobStatusBridge.update(errMsg)
            return@withContext Result.failure(Exception(errMsg))
        }

        // Nếu thông báo là tài khoản đã tồn tại / đã liên kết từ declare response:
        if (declareMsg.contains("đã tồn tại", ignoreCase = true) ||
            declareMsg.contains("đã liên kết", ignoreCase = true) ||
            declareMsg.contains("đã được liên kết", ignoreCase = true)
        ) {
            val existingId = findExistingTikTokAccountId(client, cleanUsername)
            if (!existingId.isNullOrBlank()) {
                accountId = existingId
                GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, accountId)
                val readyMsg = "Tài khoản đã liên kết Golike. Đang lấy nhiệm vụ..."
                onProgress(readyMsg)
                XsmmJobStatusBridge.update(readyMsg)
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
                delay(800L)
                return@withContext Result.success(existingAcc)
            } else if (declareMsg.contains("đã được liên kết với", ignoreCase = true) ||
                declareMsg.contains("đã liên kết với", ignoreCase = true)
            ) {
                val errMsg = "Golike: $declareMsg"
                onProgress(errMsg)
                XsmmJobStatusBridge.update(errMsg)
                return@withContext Result.failure(Exception(errMsg))
            }
        }

        val dataObj = declareRes?.optJSONObject("data")
        accountId = dataObj?.optString("id")?.takeIf { it.isNotBlank() }
            ?: dataObj?.optString("account_id")?.takeIf { it.isNotBlank() }
            ?: declareRes?.optString("id")?.takeIf { it.isNotBlank() }
            ?: declareRes?.optString("account_id").orEmpty()

        if (accountId.isBlank()) {
            val existingId = findExistingTikTokAccountId(client, cleanUsername)
            if (!existingId.isNullOrBlank()) {
                accountId = existingId
            }
        }

        val targetAccountId = accountId.ifBlank { cleanUsername }

        // ── 3. Lấy thông tin nick chỉ định cấu hình từ Golike (mặc định @gosen.vietnam) ──
        onProgress("Lấy nick chỉ định cấu hình từ Golike...")
        var targetUser = ""
        try {
            val targetRes = client.getTikTokVerifyTarget(targetAccountId, cleanUsername)
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
        delay(1200L)

        if (TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
            val actionId = XsmmTaskAutomationBridge.triggerTask(
                taskType = "golike_config_follow",
                swipeBefore = false,
                returnHomeAndSwipe = false,
                durationSeconds = 3
            )
            val startWait = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - startWait) < 12000L) {
                val res = XsmmTaskAutomationBridge.result.value
                if (res is XsmmTaskActionResult.InProgress) {
                    onProgress(res.message)
                    XsmmJobStatusBridge.update(res.message)
                    // Nếu đã follow rồi → không cần bấm lại, thoát luôn
                    if (res.message.contains("Đã follow", ignoreCase = true) ||
                        res.message.contains("Following", ignoreCase = true) ||
                        res.message.contains("đã theo dõi", ignoreCase = true) ||
                        res.message.contains("nhắn tin", ignoreCase = true)
                    ) {
                        break
                    }
                } else if (res is XsmmTaskActionResult.Completed && (res.actionId == actionId || res.actionId == 0L || res.success)) {
                    onProgress(res.message)
                    XsmmJobStatusBridge.update(res.message)
                    break
                }
                delay(300L)
            }
        } else {
            for (sec in 3 downTo 1) {
                XsmmJobStatusBridge.update("Chờ bấm Follow @$targetFollow (${sec}s)...")
                delay(1000L)
            }
        }

        val verifyInitMsg = "Tài khoản đã Follow @$targetFollow. Đang gửi xác thực lên Golike..."
        onProgress(verifyInitMsg)
        XsmmJobStatusBridge.update(verifyInitMsg)
        delay(1000L)

        // ── 5. Gửi xác nhận hoàn tất lên Golike: POST /api/tiktok-account/verify-account-id ──
        var retryCount = 0
        val maxRetries = 3
        var verifySuccess = false
        var verifiedAccountId = ""

        while (retryCount < maxRetries && !verifySuccess) {
            retryCount++
            val waitNotice = "Đang gửi xác thực lên Golike (lần $retryCount/$maxRetries)..."
            onProgress(waitNotice)
            XsmmJobStatusBridge.update(waitNotice)

            var response = client.verifyTikTokAccountId(targetAccountId, cleanUsername)
            android.util.Log.d("GolikeVerify", "Verify Response (lần $retryCount): $response")

            val httpCode = response?.optInt("http_code", 0) ?: 0
            var statusCode = response?.optInt("status", httpCode) ?: httpCode
            var isSuccess = response?.optBoolean("success", false) == true

            // Lấy message nguyên văn từ server Golike:
            var serverMessage = when {
                !response?.optString("message").isNullOrBlank() -> response?.optString("message").orEmpty()
                !response?.optString("error").isNullOrBlank() -> response?.optString("error").orEmpty()
                !response?.optJSONObject("data")?.optString("message").isNullOrBlank() -> response?.optJSONObject("data")?.optString("message").orEmpty()
                !response?.optJSONObject("error")?.optString("message").isNullOrBlank() -> response?.optJSONObject("error")?.optString("message").orEmpty()
                else -> ""
            }

            if (statusCode != 200 && !isSuccess && !serverMessage.contains("thành công", ignoreCase = true) && !serverMessage.contains("đã xác nhận", ignoreCase = true)) {
                val fallbackRes = client.verifyTikTokAccount(targetAccountId, cleanUsername)
                if (fallbackRes != null) {
                    val fallbackHttp = fallbackRes.optInt("http_code", 0)
                    val fallbackStatus = fallbackRes.optInt("status", fallbackHttp)
                    val fallbackSuccess = fallbackRes.optBoolean("success", false) == true
                    val fallbackMessage = when {
                        !fallbackRes.optString("message").isNullOrBlank() -> fallbackRes.optString("message").orEmpty()
                        !fallbackRes.optString("error").isNullOrBlank() -> fallbackRes.optString("error").orEmpty()
                        !fallbackRes.optJSONObject("data")?.optString("message").isNullOrBlank() -> fallbackRes.optJSONObject("data")?.optString("message").orEmpty()
                        !fallbackRes.optJSONObject("error")?.optString("message").isNullOrBlank() -> fallbackRes.optJSONObject("error")?.optString("message").orEmpty()
                        else -> ""
                    }
                    if (fallbackStatus == 200 || fallbackSuccess || fallbackMessage.contains("thành công", ignoreCase = true)) {
                        statusCode = fallbackStatus
                        isSuccess = fallbackSuccess
                        serverMessage = fallbackMessage
                        response = fallbackRes
                    } else if (serverMessage.isBlank() && fallbackMessage.isNotBlank()) {
                        serverMessage = fallbackMessage
                    }
                }
            }

            // XỬ LÝ 3 TRƯỜNG HỢP:
            val isNormalSuccess = (statusCode == 200 || isSuccess) ||
                    serverMessage.contains("thành công", ignoreCase = true) ||
                    serverMessage.contains("đã xác nhận", ignoreCase = true)

            if (isNormalSuccess) {
                // Trường hợp 1: Thành công
                verifySuccess = true
                verifiedAccountId = response?.optJSONObject("data")?.optString("id", "").orEmpty()
                if (verifiedAccountId.isBlank()) {
                    verifiedAccountId = findExistingTikTokAccountId(client, cleanUsername) ?: targetAccountId
                }
                val succMsg = "Xác thực thành công! Đang lấy nhiệm vụ..."
                onProgress(succMsg)
                XsmmJobStatusBridge.update(succMsg)
                break
            } else {
                // Kiểm tra xem có phải trạng thái đang xử lý / chờ duyệt (hoặc rỗng) hay không:
                val isWaitingApproval = serverMessage.contains("đang thử lại", ignoreCase = true) ||
                        serverMessage.contains("chờ duyệt", ignoreCase = true) ||
                        serverMessage.contains("chờ xác thực", ignoreCase = true) ||
                        serverMessage.isBlank()

                if (isWaitingApproval) {
                    // Trường hợp 3: Server báo trạng thái đang xử lý / chờ duyệt (CHỈ RETRY TỐI ĐA 3 LẦN)
                    if (retryCount < maxRetries) {
                        val waitMsg = "Golike: ${serverMessage.ifBlank { "Đang chờ duyệt xác thực" }}. Thử lại sau 3s..."
                        onProgress(waitMsg)
                        XsmmJobStatusBridge.update(waitMsg)
                        delay(3000L)
                    } else {
                        val errMsg = "Golike: ${serverMessage.ifBlank { "Chờ duyệt chưa hoàn tất" }} (Vui lòng thử lại sau)"
                        onProgress(errMsg)
                        XsmmJobStatusBridge.update(errMsg)
                        return@withContext Result.failure(Exception(errMsg))
                    }
                } else {
                    // Trường hợp 2: Lỗi từ server (Ví dụ: "Tài khoản này đã được liên kết với tài khoản ...", "Tài khoản chưa follow", "Không tìm thấy", ...)
                    // HIỂN THỊ NGUYÊN VĂN THÔNG BÁO LỖI LÊN MÀN HÌNH NỔI (OVERLAY) VÀ DỪNG NGAY
                    val errMsg = "Golike: $serverMessage"
                    onProgress(errMsg)
                    XsmmJobStatusBridge.update(errMsg)
                    return@withContext Result.failure(Exception(errMsg))
                }
            }
        }

        // SAU KHI THOÁT KHỎI VÒNG LẶP:
        if (verifySuccess) {
            val finalId = verifiedAccountId.ifBlank { targetAccountId }
            val successMsg = "Xác thực thành công! Đang lấy nhiệm vụ..."
            onProgress(successMsg)
            XsmmJobStatusBridge.update(successMsg)
            GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, finalId)

            val finalAcc = GolikeAccount(
                id = cleanUsername,
                platform = "tiktok",
                username = cleanUsername,
                avatar = "",
                isLive = true,
                isGolikeLinked = true,
                golikeAccountId = finalId,
                lastStatus = "Đã liên kết Golike • Sẵn sàng"
            )
            GolikeAccountsStore.addOrUpdateAccount(context, finalAcc)
            delay(1000L)
            return@withContext Result.success(finalAcc)
        } else {
            val err = "Golike: Không thể xác thực tài khoản."
            XsmmJobStatusBridge.update(err)
            onProgress(err)
            return@withContext Result.failure(Exception(err))
        }
    }

    /**
     * Mở đối tượng TikTok (profile hoặc video bài viết) bằng URL THẬT do Golike cấp,
     * tự động gắn đích danh package TikTok để mở ngay trong app thật, không nhảy ra trình duyệt.
     */
    private fun openTikTokTarget(context: Context, realUrl: String, variant: TikTokAppVariant): Boolean {
        val cleanUrl = if (!realUrl.startsWith("http://") && !realUrl.startsWith("https://")) "https://$realUrl" else realUrl
        val uri = Uri.parse(cleanUrl)
        val candidates = TikTokAppLauncher.candidatePackages(variant)

        // 1. Mở đích danh bằng package của TikTok (chuẩn nhất cho cả video, profile và vt.tiktok)
        for (pkg in candidates) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        // 2. Fallback: Mở Intent ACTION_VIEW mặc định
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
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
        try {
            val cfg = GolikeRunConfigStore.get(context, "tiktok")
            val cleanUsername = account.id.ifBlank { account.username }.trim().removePrefix("@").lowercase()

        // ── Nạp đầy đủ session từ SharedPreferences vào client trước khi gọi API ──
        val token = GolikeAccountsStore.getToken(context).orEmpty()
        client.updateSession(
            authToken = token,
            tToken = GolikeAccountsStore.getTToken(context),
            deviceId = GolikeAccountsStore.getDeviceId(context),
            username = GolikeAccountsStore.getUsername(context),
            gAuth = GolikeAccountsStore.getGAuth(context),
            version = GolikeAccountsStore.getVersionApp(context),
            client = GolikeAccountsStore.getClient(context),
            scheme = GolikeAccountsStore.getScheme(context),
            webVersion = GolikeAccountsStore.getWebVersion(context),
            webVersionText = GolikeAccountsStore.getWebVersionText(context),
            webData = GolikeAccountsStore.getWebData(context),
            signingKey = GolikeAccountsStore.getSigningKey(context),
            userId = GolikeAccountsStore.getUserId(context).toLongOrNull(),
            context = context
        )
        GolikeWasmSigner.init(context)

        // ── Guard 0: Đồng bộ tính toàn vẹn phiên làm việc Golike ──
        val sessionSyncErr = GolikeNativeBridge.checkSessionIntegrity(context)
        if (sessionSyncErr != null) {
            onStatusChange(sessionSyncErr)
            XsmmJobStatusBridge.update(sessionSyncErr)
            onJobFailed(sessionSyncErr)
            GolikeRunningManager.runningAccounts[account.id] = false
            GolikeRunningManager.runningAccounts[cleanUsername] = false
            return@withContext
        }

        // ── Guard: Kiểm tra đăng nhập Golike ──
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
        var golikeAccountId = account.golikeAccountId.ifBlank {
            GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanUsername).orEmpty()
        }

        // BƯỚC 1: KIỂM TRA TÀI KHOẢN ĐÃ TỒN TẠI TRƯỚC KHI BẮT ĐẦU CẤU HÌNH
        if (golikeAccountId.isBlank()) {
            onStatusChange("Kiểm tra nick @$cleanUsername trên Golike...")
            XsmmJobStatusBridge.update("Kiểm tra nick @$cleanUsername trên Golike...")
            val existingId = findExistingTikTokAccountId(client, cleanUsername)
            if (!existingId.isNullOrBlank()) {
                golikeAccountId = existingId
                GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, golikeAccountId)
            }
        }

        if (golikeAccountId.isNotBlank()) {
            // NẾU ĐÃ CÓ: BỎ QUA HOÀN TOÀN BƯỚC FOLLOW VÀ BƯỚC XÁC THỰC!
            val readyMsg = "Tài khoản đã liên kết Golike. Đang lấy nhiệm vụ..."
            onStatusChange(readyMsg)
            XsmmJobStatusBridge.update(readyMsg)
            GolikeAccountsStore.updateAccountProgress(context, "tiktok", cleanUsername, "Đã liên kết • Sẵn sàng", isSuccess = true)
        } else {
            // 2.2 KHAI BÁO THÊM ACC LÊN HỆ THỐNG GOLIKE
            onStatusChange("Khai báo @$cleanUsername lên Golike...")
            XsmmJobStatusBridge.update("Khai báo @$cleanUsername lên Golike...")

            val declareRes = client.declareTikTokAccount(cleanUsername)
            android.util.Log.d("GolikeVerify", "declareTikTokAccount in loop response: $declareRes")

            // Kiểm tra lỗi session từ declare response
            val declareMsg = declareRes?.optString("message").orEmpty()
            if (declareMsg.contains("tải lại trang", ignoreCase = true) ||
                declareMsg.contains("phiên bản mới nhất", ignoreCase = true)
            ) {
                val errMsg = "Golike: Nick @$cleanUsername chưa liên kết trên Golike (Vào app.golike.net để liên kết trước)"
                onStatusChange(errMsg)
                XsmmJobStatusBridge.update(errMsg)
                onJobFailed(errMsg)
                GolikeRunningManager.runningAccounts[account.id] = false
                GolikeRunningManager.runningAccounts[cleanUsername] = false
                return@withContext
            }

            // Nếu khai báo báo là đã tồn tại / đã liên kết:
            if (declareMsg.contains("đã tồn tại", ignoreCase = true) ||
                declareMsg.contains("đã liên kết", ignoreCase = true) ||
                declareMsg.contains("đã được liên kết", ignoreCase = true)
            ) {
                val existingId = findExistingTikTokAccountId(client, cleanUsername)
                if (!existingId.isNullOrBlank()) {
                    golikeAccountId = existingId
                    GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, golikeAccountId)
                } else if (declareMsg.contains("đã được liên kết với", ignoreCase = true) ||
                    declareMsg.contains("đã liên kết với", ignoreCase = true)
                ) {
                    val errMsg = "Golike: $declareMsg"
                    onStatusChange(errMsg)
                    XsmmJobStatusBridge.update(errMsg)
                    onJobFailed(errMsg)
                    GolikeRunningManager.runningAccounts[account.id] = false
                    GolikeRunningManager.runningAccounts[cleanUsername] = false
                    return@withContext
                }
            }

            if (golikeAccountId.isBlank()) {
                golikeAccountId = declareRes?.optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: declareRes?.optJSONObject("data")?.optString("account_id")?.takeIf { it.isNotBlank() }
                    ?: declareRes?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: declareRes?.optString("account_id").orEmpty()
            }

            if (golikeAccountId.isBlank()) {
                val existingId = findExistingTikTokAccountId(client, cleanUsername)
                if (!existingId.isNullOrBlank()) {
                    golikeAccountId = existingId
                }
            }

            // Nếu sau khi kiểm tra lại đã có ID (tài khoản đã có trên Golike) -> BỎ QUA HOÀN TOÀN BƯỚC FOLLOW VÀ VERIFY!
            if (golikeAccountId.isNotBlank()) {
                val readyMsg = "Tài khoản đã liên kết Golike. Đang lấy nhiệm vụ..."
                onStatusChange(readyMsg)
                XsmmJobStatusBridge.update(readyMsg)
                GolikeAccountsStore.updateAccountProgress(context, "tiktok", cleanUsername, "Đã liên kết • Sẵn sàng", isSuccess = true)
            } else {
                val targetConfigAccountId = golikeAccountId.ifBlank { cleanUsername }

                // 2.3 MỞ TIKTOK FOLLOW NICK CHỈ ĐỊNH ĐỂ CẤU HÌNH (CHUẨN GOMAX @gosen.vietnam)
                var targetConfigUser = "gosen.vietnam"
                try {
                    val targetRes = client.getTikTokVerifyTarget(targetConfigAccountId, cleanUsername)
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

                openTikTokTarget(context, "https://www.tiktok.com/@$targetConfigUser", variant)
                delay(1200L) // Chờ TikTok load trang cá nhân

                if (TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
                    val actionId = XsmmTaskAutomationBridge.triggerTask(
                        taskType = "golike_config_follow",
                        swipeBefore = false,
                        returnHomeAndSwipe = false,
                        durationSeconds = 3
                    )
                    val startWait = System.currentTimeMillis()
                    while (isActive && (System.currentTimeMillis() - startWait) < 12000L) {
                        val res = XsmmTaskAutomationBridge.result.value
                        if (res is XsmmTaskActionResult.InProgress) {
                            onStatusChange(res.message)
                            XsmmJobStatusBridge.update(res.message)
                            // Nếu đã follow rồi → không bấm lại, thoát luôn
                            if (res.message.contains("Đã follow", ignoreCase = true) ||
                                res.message.contains("Following", ignoreCase = true) ||
                                res.message.contains("đã theo dõi", ignoreCase = true) ||
                                res.message.contains("nhắn tin", ignoreCase = true)
                            ) {
                                break
                            }
                        } else if (res is XsmmTaskActionResult.Completed && (res.actionId == actionId || res.actionId == 0L || res.success)) {
                            onStatusChange(res.message)
                            XsmmJobStatusBridge.update(res.message)
                            break
                        }
                        delay(300L)
                    }
                } else {
                    for (sec in 3 downTo 1) {
                        XsmmJobStatusBridge.update("Chờ bấm Follow @$targetConfigUser (${sec}s)...")
                        delay(1000L)
                    }
                }

                val verifyInitMsg = "Tài khoản đã Follow @$targetConfigUser. Đang gửi xác thực lên Golike..."
                onStatusChange(verifyInitMsg)
                XsmmJobStatusBridge.update(verifyInitMsg)
                delay(1000L)

                // 2.4 GỬI XÁC NHẬN HOÀN TẤT CẤU HÌNH LÊN GOLIKE
                var retryCount = 0
                val maxConfigAttempts = 3
                var isConfigSuccess = false
                var verifiedAccountId = ""

                while (retryCount < maxConfigAttempts && !isConfigSuccess) {
                    retryCount++
                    val waitNotice = "Đang gửi xác thực lên Golike (lần $retryCount/$maxConfigAttempts)..."
                    onStatusChange(waitNotice)
                    XsmmJobStatusBridge.update(waitNotice)

                    var verifyRes = client.verifyTikTokAccountId(targetConfigAccountId, cleanUsername)
                    android.util.Log.d("GolikeVerify", "verifyTikTokAccountId loop (lần $retryCount): $verifyRes")

                    val httpCode = verifyRes?.optInt("http_code", 0) ?: 0
                    var vStatus = verifyRes?.optInt("status", httpCode) ?: httpCode
                    var vSuccess = verifyRes?.optBoolean("success", false) == true

                    // Lấy message nguyên văn từ server Golike:
                    var serverMessage = when {
                        !verifyRes?.optString("message").isNullOrBlank() -> verifyRes?.optString("message").orEmpty()
                        !verifyRes?.optString("error").isNullOrBlank() -> verifyRes?.optString("error").orEmpty()
                        !verifyRes?.optJSONObject("data")?.optString("message").isNullOrBlank() -> verifyRes?.optJSONObject("data")?.optString("message").orEmpty()
                        !verifyRes?.optJSONObject("error")?.optString("message").isNullOrBlank() -> verifyRes?.optJSONObject("error")?.optString("message").orEmpty()
                        else -> ""
                    }

                    if (vStatus != 200 && !vSuccess && !serverMessage.contains("thành công", ignoreCase = true) && !serverMessage.contains("đã xác nhận", ignoreCase = true)) {
                        val fallbackRes = client.verifyTikTokAccount(targetConfigAccountId, cleanUsername)
                        android.util.Log.d("GolikeVerify", "verifyTikTokAccount fallback loop response: $fallbackRes")
                        if (fallbackRes != null) {
                            val fallbackHttpCode = fallbackRes.optInt("http_code", 0)
                            val fallbackStatus = fallbackRes.optInt("status", fallbackHttpCode)
                            val fallbackSuccess = fallbackRes.optBoolean("success", false) == true
                            val fallbackMessage = when {
                                !fallbackRes.optString("message").isNullOrBlank() -> fallbackRes.optString("message").orEmpty()
                                !fallbackRes.optString("error").isNullOrBlank() -> fallbackRes.optString("error").orEmpty()
                                !fallbackRes.optJSONObject("data")?.optString("message").isNullOrBlank() -> fallbackRes.optJSONObject("data")?.optString("message").orEmpty()
                                !fallbackRes.optJSONObject("error")?.optString("message").isNullOrBlank() -> fallbackRes.optJSONObject("error")?.optString("message").orEmpty()
                                else -> ""
                            }
                            if (fallbackStatus == 200 || fallbackSuccess || fallbackMessage.contains("thành công", ignoreCase = true)) {
                                vStatus = fallbackStatus
                                vSuccess = fallbackSuccess
                                serverMessage = fallbackMessage
                                verifyRes = fallbackRes
                            } else if (serverMessage.isBlank() && fallbackMessage.isNotBlank()) {
                                serverMessage = fallbackMessage
                            }
                        }
                    }

                    // XỬ LÝ 3 TRƯỜNG HỢP:
                    val isNormalSuccess = (vStatus == 200 || vSuccess) ||
                            serverMessage.contains("thành công", ignoreCase = true) ||
                            serverMessage.contains("đã xác nhận", ignoreCase = true)

                    if (isNormalSuccess) {
                        // Trường hợp 1: Thành công
                        isConfigSuccess = true
                        verifiedAccountId = verifyRes?.optJSONObject("data")?.optString("id", "").orEmpty()
                        if (verifiedAccountId.isBlank()) {
                            verifiedAccountId = findExistingTikTokAccountId(client, cleanUsername) ?: targetConfigAccountId
                        }
                        golikeAccountId = verifiedAccountId
                        val succMsg = "Xác thực thành công! Đang lấy nhiệm vụ..."
                        onStatusChange(succMsg)
                        XsmmJobStatusBridge.update(succMsg)
                        break
                    } else {
                        // Kiểm tra xem có phải trạng thái đang xử lý / chờ duyệt (hoặc rỗng) hay không:
                        val isWaitingApproval = serverMessage.contains("đang thử lại", ignoreCase = true) ||
                                serverMessage.contains("chờ duyệt", ignoreCase = true) ||
                                serverMessage.contains("chờ xác thực", ignoreCase = true) ||
                                serverMessage.isBlank()

                        if (isWaitingApproval) {
                            // Trường hợp 3: Server báo trạng thái đang xử lý / chờ duyệt (CHỈ RETRY TỐI ĐA 3 LẦN)
                            if (retryCount < maxConfigAttempts) {
                                val waitMsg = "Golike: ${serverMessage.ifBlank { "Đang chờ duyệt xác thực" }}. Thử lại sau 3s..."
                                onStatusChange(waitMsg)
                                XsmmJobStatusBridge.update(waitMsg)
                                delay(3000L)
                            } else {
                                val errMsg = "Golike: ${serverMessage.ifBlank { "Chờ duyệt chưa hoàn tất" }} (Vui lòng thử lại sau)"
                                onStatusChange(errMsg)
                                XsmmJobStatusBridge.update(errMsg)
                                onJobFailed(errMsg)
                                GolikeRunningManager.runningAccounts[account.id] = false
                                GolikeRunningManager.runningAccounts[cleanUsername] = false
                                return@withContext
                            }
                        } else {
                            // Trường hợp 2: Lỗi từ server (Ví dụ: "Tài khoản này đã được liên kết với tài khoản ...", "Tài khoản chưa follow", "Không tìm thấy", ...)
                            // HIỂN THỊ NGUYÊN VĂN THÔNG BÁO LỖI LÊN MÀN HÌNH NỔI (OVERLAY) VÀ DỪNG NGAY
                            val errMsg = "Golike: $serverMessage"
                            onStatusChange(errMsg)
                            XsmmJobStatusBridge.update(errMsg)
                            onJobFailed(errMsg)
                            GolikeRunningManager.runningAccounts[account.id] = false
                            GolikeRunningManager.runningAccounts[cleanUsername] = false
                            return@withContext
                        }
                    }
                }

                if (isConfigSuccess) {
                    val finalId = golikeAccountId.ifBlank { targetConfigAccountId }
                    val successMsg = "Xác thực thành công! Đang lấy nhiệm vụ..."
                    onStatusChange(successMsg)
                    XsmmJobStatusBridge.update(successMsg)

                    // Lưu mapping vào golike_tiktok_map
                    GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, finalId)

                    val updatedAcc = GolikeAccount(
                        id = cleanUsername,
                        platform = "tiktok",
                        username = account.username,
                        avatar = account.avatar,
                        isLive = true,
                        isGolikeLinked = true,
                        golikeAccountId = finalId,
                        lastStatus = "Đã liên kết Golike • Sẵn sàng"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, updatedAcc)
                    GolikeAccountsStore.updateAccountProgress(context, "tiktok", cleanUsername, "Đã cấu hình • Sẵn sàng", isSuccess = true)
                    delay(1200L)
                } else {
                    val err = "Golike: Không thể xác thực tài khoản."
                    onStatusChange(err)
                    XsmmJobStatusBridge.update(err)
                    onJobFailed(err)
                    GolikeRunningManager.runningAccounts[account.id] = false
                    GolikeRunningManager.runningAccounts[cleanUsername] = false
                    delay(1500L)
                    return@withContext
                }
            }
        }

        // BƯỚC 3: CHUYỂN NGAY SANG VÒNG LẶP LÀM JOB CỦA GOLIKE
        var finalNumericId = golikeAccountId.ifBlank {
            GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanUsername).orEmpty()
        }
        if (finalNumericId.toLongOrNull() == null) {
            val found = findExistingTikTokAccountId(client, cleanUsername)
            if (!found.isNullOrBlank()) {
                finalNumericId = found
                GolikeAccountsStore.saveTikTokMapping(context, cleanUsername, finalNumericId)
            }
        }

        // BẢO VỆ CHẶT CHẼ: BẮT BUỘC account_id PHẢI LÀ ID SỐ CỦA GOLIKE (KHÔNG ĐƯỢC LÀ USERNAME DẠNG CHỮ)
        val numericId = finalNumericId.toLongOrNull()
        if (numericId == null) {
            val errNotLinked = "Golike: Nick @$cleanUsername chưa có ID số trên Golike. Vui lòng vào app.golike.net liên kết nick trước!"
            onStatusChange(errNotLinked)
            XsmmJobStatusBridge.update(errNotLinked)
            onJobFailed(errNotLinked)
            GolikeRunningManager.runningAccounts[account.id] = false
            GolikeRunningManager.runningAccounts[cleanUsername] = false
            return@withContext
        }
        val golikeAccId = numericId.toString()

        // Khởi tạo security session lấy signing_key & userId chuẩn bị cho việc nhận và hoàn thành job
        try {
            onStatusChange("Khởi tạo phiên bảo mật Golike...")
            XsmmJobStatusBridge.update("Khởi tạo phiên bảo mật Golike...")
            val secRes = client.createSecuritySession()
            val secKey = secRes?.optString("signing_key").orEmpty()
            if (secKey.isNotBlank()) {
                client.signingKey = secKey
                GolikeAccountsStore.saveSigningKey(context, secKey)
            } else {
                val httpCode = secRes?.optInt("http_code", 0) ?: 0
                val msg = secRes?.optString("message")?.ifBlank { "Không có signing_key" }
                val warnMsg = "Cảnh báo bảo mật: HTTP $httpCode - $msg"
                onStatusChange(warnMsg)
                XsmmJobStatusBridge.update(warnMsg)
            }
        } catch (e: Exception) {
            android.util.Log.w("GolikeTaskRunner", "Lỗi tạo security session: ${e.message}")
            onStatusChange("Lỗi kết nối bảo mật: ${e.message}")
        }

        // Đảm bảo client luôn có userId hợp lệ trước khi vào vòng lặp
        if ((client.userId ?: 0L) <= 0L) {
            try {
                val me = client.getMe()
                val uid = me?.optJSONObject("data")?.optLong("id", 0L)
                    ?: me?.optLong("id", 0L) ?: 0L
                if (uid > 0L) {
                    client.userId = uid
                    GolikeAccountsStore.saveUserId(context, uid.toString())
                }
            } catch (_: Exception) {}
        }

        GolikeRunningManager.runningAccounts[account.id] = true
        GolikeRunningManager.runningAccounts[cleanUsername] = true
        var consecutiveFails = 0
        var totalCompletedTasks = 0

        // Khởi động WebView mini nội bộ riêng cho Golike
        try {
            withContext(Dispatchers.Main) {
                GolikeWebJobBridge.getOrCreateMiniWebView(context)
            }
        } catch (_: Exception) {}

        while ((GolikeRunningManager.isRunning(account.id) || GolikeRunningManager.isRunning(cleanUsername)) && isActive) {
            try {
                // Kiểm tra mục tiêu số lượng nhiệm vụ tối đa
                if (cfg.taskCountTarget > 0 && totalCompletedTasks >= cfg.taskCountTarget) {
                    val finishMsg = "Đã hoàn thành đủ mục tiêu ${cfg.taskCountTarget} nhiệm vụ. Dừng chạy."
                    onStatusChange(finishMsg)
                    XsmmJobStatusBridge.update(finishMsg)
                    GolikeRunningManager.runningAccounts[account.id] = false
                    GolikeRunningManager.runningAccounts[cleanUsername] = false
                    break
                }

                // ─────────────────────────────────────────────────────────────
                // BƯỚC 1: THỜI GIAN LẤY JOB (GIÂY) & LƯỚT TRƯỚC KHI LÀM
                // ─────────────────────────────────────────────────────────────
                val getJobDelay = if (cfg.getJobMaxSeconds >= cfg.getJobMinSeconds && cfg.getJobMinSeconds > 0) {
                    (cfg.getJobMinSeconds..cfg.getJobMaxSeconds).random()
                } else {
                    5
                }

                if (cfg.swipeBeforeGetJob && TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
                    // Nếu bật lướt trước khi làm: Kích hoạt lướt video TikTok trong thời gian chờ lấy job
                    XsmmTaskAutomationBridge.triggerTask(
                        taskType = "swipe_feed",
                        swipeBefore = false,
                        returnHomeAndSwipe = true,
                        durationSeconds = getJobDelay
                    )
                    val getJobStartTime = System.currentTimeMillis()
                    val getJobEndTime = getJobStartTime + getJobDelay * 1000L
                    while (System.currentTimeMillis() < getJobEndTime && isActive && GolikeRunningManager.isRunning(account.id)) {
                        val secLeft = ((getJobEndTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(1)
                        val msg = "Lướt TikTok chờ lấy job (${secLeft}s)..."
                        onStatusChange(msg)
                        XsmmJobStatusBridge.update(msg)
                        delay(1000L)
                    }
                } else {
                    for (sec in getJobDelay downTo 1) {
                        if (!GolikeRunningManager.isRunning(account.id) || !isActive) break
                        val msg = "Chờ lấy nhiệm vụ TikTok (${sec}s)..."
                        onStatusChange(msg)
                        XsmmJobStatusBridge.update(msg)
                        delay(1000L)
                    }
                }

                // 100% Lấy nhiệm vụ trực tiếp qua Web Context của Golike (tránh triệt để lỗi 403 sig)
                val currentWebData = GolikeAccountsStore.getWebData(context)
                val jobRes = try {
                    GolikeWebJobBridge.getJobViaWeb(golikeAccId, currentWebData)
                } catch (_: Exception) { null }
                if (jobRes == null || !jobRes.optBoolean("success", false)) {
                    consecutiveFails++
                    val httpCode = jobRes?.optInt("http_code", 0)?.takeIf { it > 0 }
                        ?: jobRes?.optInt("status", 0)?.takeIf { it > 0 } ?: 0
                    val rawMsg = jobRes?.optString("message").orEmpty()
                    val rawErr = jobRes?.optString("error").orEmpty()
                    val errMsg = when {
                        rawMsg.isNotBlank() && httpCode > 0 && !rawMsg.contains("HTTP") -> "[$httpCode] $rawMsg"
                        rawMsg.isNotBlank() -> rawMsg
                        rawErr.isNotBlank() -> rawErr
                        else -> "Chưa có nhiệm vụ lúc này"
                    }
                    val displayError = "Golike: $errMsg (Chờ ${cfg.delayMinSeconds}s)"
                    onStatusChange(displayError)
                    XsmmJobStatusBridge.update(displayError)
                    onJobFailed(errMsg)

                    if (cfg.autoSwitchOnFail && consecutiveFails >= cfg.failJobCountToSwitchAccount) {
                        val stopMsg = "Tạm dừng: Gặp lỗi liên tiếp $consecutiveFails lần"
                        onStatusChange(stopMsg)
                        XsmmJobStatusBridge.update(stopMsg)
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

                val rawAdsId = jobData.optString("ads_id").ifBlank { jobData.optString("id") }
                val adsId = rawAdsId.ifBlank { jobData.optLong("ads_id", jobData.optLong("id", 0L)).toString() }
                val rawType = jobData.optString("type", "follow").lowercase()
                val link = jobData.optString("link").trim()
                val objectId = jobData.optString("object_id").trim()

                val isFollowJob = rawType.contains("follow") || rawType.contains("sub")
                val isLikeJob = rawType.contains("like") || rawType.contains("tym") || rawType.contains("tim") || rawType.contains("love")
                val normalizedJobType = if (isLikeJob) "like" else "follow"
                val shortType = if (isLikeJob) "Tym" else "Follow"

                // 1. URL THẬT do Golike cấp (dùng để mở thẳng trong ứng dụng TikTok, không gán ID số vào URL profile)
                val realUrl = when {
                    link.isNotBlank() -> link
                    isFollowJob && !objectId.all { it.isDigit() } && objectId.isNotBlank() -> "https://www.tiktok.com/@${objectId.removePrefix("@")}"
                    isLikeJob && objectId.isNotBlank() -> "https://www.tiktok.com/@user/video/$objectId"
                    else -> link.ifBlank { "https://www.tiktok.com" }
                }

                // 2. Tên hiển thị chuẩn xác và thân thiện trên Màn nổi (Overlay)
                val displayTarget = when {
                    isFollowJob -> {
                        if (link.contains("@")) {
                            "@" + link.substringAfter("@").substringBefore("/").substringBefore("?").trim()
                        } else if (!objectId.all { it.isDigit() } && objectId.isNotBlank()) {
                            "@" + objectId.removePrefix("@")
                        } else {
                            "nick mục tiêu"
                        }
                    }
                    isLikeJob -> {
                        val vid = if (objectId.all { it.isDigit() } && objectId.isNotBlank()) {
                            objectId
                        } else if (link.contains("/video/")) {
                            link.substringAfter("/video/").substringBefore("?").substringBefore("/")
                        } else {
                            objectId.takeLast(10)
                        }
                        "Video #$vid"
                    }
                    else -> objectId.ifBlank { "nhiệm vụ" }
                }

                // ─────────────────────────────────────────────────────────────
                // BƯỚC 2: THỜI GIAN LÀM JOB (GIÂY) & THỰC HIỆN FOLLOW / TYM
                // ─────────────────────────────────────────────────────────────
                val doJobDelay = if (cfg.doJobMaxSeconds >= cfg.doJobMinSeconds && cfg.doJobMinSeconds > 0) {
                    (cfg.doJobMinSeconds..cfg.doJobMaxSeconds).random()
                } else {
                    8
                }

                val openMsg = "Mở $shortType: $displayTarget"
                onStatusChange(openMsg)
                XsmmJobStatusBridge.update(openMsg)

                // Mở trực tiếp bằng URL thật do Golike cấp qua package TikTok đích danh (chuẩn XSMM)
                TikTokAppLauncher.openUserProfile(context, realUrl, variant)
                delay(1200L)

                // Bấm tương tác (Follow hoặc Thả tim liên tục) chuẩn XSMM
                var taskActionSuccess = false
                val isA11yEnabled = TikTokAppLauncher.isAccessibilityServiceEnabled(context)
                if (isA11yEnabled) {
                    val actionId = XsmmTaskAutomationBridge.triggerTask(
                        taskType = normalizedJobType,
                        swipeBefore = false,
                        returnHomeAndSwipe = false,
                        durationSeconds = doJobDelay
                    )

                    val actionStartTime = System.currentTimeMillis()
                    val maxWaitTime = (doJobDelay + 15) * 1000L
                    while ((GolikeRunningManager.isRunning(account.id) || GolikeRunningManager.isRunning(cleanUsername)) &&
                           isActive && (System.currentTimeMillis() - actionStartTime) < maxWaitTime) {
                        val res = XsmmTaskAutomationBridge.result.value
                        when (res) {
                            is XsmmTaskActionResult.InProgress -> {
                                onStatusChange(res.message)
                                XsmmJobStatusBridge.update(res.message)
                            }
                            is XsmmTaskActionResult.Completed -> {
                                if (res.actionId == actionId) {
                                    taskActionSuccess = res.success
                                    val doneClickMsg = if (taskActionSuccess) "Đã bấm $shortType thành công!" else res.message
                                    onStatusChange(doneClickMsg)
                                    XsmmJobStatusBridge.update(doneClickMsg)
                                    break
                                }
                            }
                            else -> Unit
                        }
                        delay(400L)
                    }
                } else {
                    val errA11y = "Lỗi: Chưa bật quyền Trợ năng (Accessibility) cho app!"
                    onStatusChange(errA11y)
                    XsmmJobStatusBridge.update(errA11y)
                    taskActionSuccess = false
                }

                if (!GolikeRunningManager.isRunning(account.id) || !isActive) break

                // ─────────────────────────────────────────────────────────────
                // BƯỚC 3 & 4: FOLLOW XONG LƯỚT TIN & THỜI GIAN HOÀN THÀNH JOB
                // ─────────────────────────────────────────────────────────────
                if (taskActionSuccess) {
                    val completeJobDelay = if (cfg.completeJobMaxSeconds >= cfg.completeJobMinSeconds && cfg.completeJobMinSeconds > 0) {
                        (cfg.completeJobMinSeconds..cfg.completeJobMaxSeconds).random()
                    } else {
                        20
                    }

                    // Nếu người dùng BẬT "Follow xong lướt tin": quay lại Home và lướt tin video
                    if (cfg.swipeAfterFollow && isA11yEnabled) {
                        XsmmTaskAutomationBridge.triggerTask(
                            taskType = "swipe_feed",
                            swipeBefore = false,
                            returnHomeAndSwipe = true,
                            durationSeconds = completeJobDelay
                        )
                        val compStartTime = System.currentTimeMillis()
                        val compEndTime = compStartTime + completeJobDelay * 1000L
                        while (System.currentTimeMillis() < compEndTime && isActive && GolikeRunningManager.isRunning(account.id)) {
                            val secLeft = ((compEndTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(1)
                            val compMsg = "Đã $shortType $displayTarget | Lướt tin chờ báo cáo (${secLeft}s)..."
                            onStatusChange(compMsg)
                            XsmmJobStatusBridge.update(compMsg)
                            delay(1000L)
                        }
                    } else {
                        // Nếu TẮT: đứng yên đếm ngược đủ thời gian hoàn thành
                        for (sec in completeJobDelay downTo 1) {
                            if (!GolikeRunningManager.isRunning(account.id) || !isActive) break
                            val compMsg = "Đã $shortType $displayTarget | Chờ hoàn tất (${sec}s)..."
                            onStatusChange(compMsg)
                            XsmmJobStatusBridge.update(compMsg)
                            delay(1000L)
                        }
                    }

                    if (!GolikeRunningManager.isRunning(account.id) || !isActive) break

                    val reportMsg = "Đang gửi báo cáo hoàn thành..."
                    onStatusChange(reportMsg)
                    XsmmJobStatusBridge.update(reportMsg)

                    // 100% Báo cáo hoàn thành nhiệm vụ qua Web Context của Golike
                    val compRes = try {
                        GolikeWebJobBridge.completeJobViaWeb(golikeAccId, adsId, objectId, "tiktok")
                    } catch (_: Exception) { null }
                    if (compRes != null && compRes.optBoolean("success", false)) {
                        totalCompletedTasks++
                        val price = compRes.optJSONObject("data")?.optInt("prices", 35) ?: 35
                        val succMsg = "Hoàn thành #$totalCompletedTasks (+$price đ) | Nghỉ ${cfg.delayMinSeconds}s"
                        onStatusChange(succMsg)
                        XsmmJobStatusBridge.update(succMsg)
                        onJobSuccess(price)
                    } else {
                        val failMsg = compRes?.optString("message")?.takeIf { it.isNotBlank() } ?: "Lỗi hoàn thành nhiệm vụ"
                        onStatusChange("Lỗi: $failMsg")
                        XsmmJobStatusBridge.update("Lỗi: $failMsg")
                        try { client.sendReport(adsId, golikeAccId, description = failMsg) } catch (_: Exception) {}
                        client.skipJob(adsId, objectId, golikeAccId, normalizedJobType)
                        onJobFailed(failMsg)
                    }
                } else {
                    // Nếu không bấm được Follow/Tym (ví dụ nick bị chặn hoặc không tìm thấy nút) -> BỎ QUA NHIỆM VỤ
                    val skipMsg = "Không bấm được $shortType -> Bỏ qua nhiệm vụ"
                    onStatusChange(skipMsg)
                    XsmmJobStatusBridge.update(skipMsg)
                    try { client.sendReport(adsId, golikeAccId, description = skipMsg) } catch (_: Exception) {}
                    client.skipJob(adsId, objectId, golikeAccId, normalizedJobType)
                    onJobFailed(skipMsg)
                }

                // ─────────────────────────────────────────────────────────────
                // BƯỚC 5: NGHỈ GIỮA CÁC JOB (GIÂY)
                // ─────────────────────────────────────────────────────────────
                val randomDelay = (cfg.delayMinSeconds..cfg.delayMaxSeconds).random()
                for (sec in randomDelay downTo 1) {
                    if (!GolikeRunningManager.isRunning(account.id) || !isActive) break
                    val restMsg = "Nghỉ giữa các job (${sec}s)..."
                    onStatusChange(restMsg)
                    XsmmJobStatusBridge.update(restMsg)
                    delay(1000L)
                }

            } catch (e: Exception) {
                val err = "Lỗi: ${e.message}"
                onStatusChange(err)
                XsmmJobStatusBridge.update(err)
                delay(5000L)
            }
        }
        } catch (t: Throwable) {
            if (t !is kotlinx.coroutines.CancellationException) {
                android.util.Log.e("GolikeTaskRunner", "Lỗi ngoại lệ trong runTikTokTaskLoop: ${t.message}", t)
                val errMsg = "Golike: ${t.message ?: "Đã dừng nhiệm vụ"}"
                onStatusChange(errMsg)
                XsmmJobStatusBridge.update(errMsg)
                onJobFailed(errMsg)
                GolikeRunningManager.runningAccounts[account.id] = false
            }
        }
    }
}
