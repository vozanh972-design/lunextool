package com.cayxu.app.automation.facebook

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.FbNuoiConfigStore
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

val FacebookAccount.cookie: String
    get() = note.ifBlank { password }

val FacebookAccount.token: String
    get() = bio.takeIf { it.isNotBlank() && (it.startsWith("EAA") || it.startsWith("EAAB")) }
        ?: bio.takeIf { it.isNotBlank() }
        ?: ""

object FbNuoiManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    val runningAccounts = mutableStateListOf<String>()
    val statusMap = mutableStateMapOf<String, String>()
    val successCountMap = mutableStateMapOf<String, Int>()
    val errorCountMap = mutableStateMapOf<String, Int>()
    val lastErrorDetail = mutableStateMapOf<String, String>()

    fun isRunning(uid: String): Boolean = uid in runningAccounts
    fun isAnyRunning(): Boolean = runningAccounts.isNotEmpty()

    @Synchronized
    fun start(context: Context, account: FacebookAccount, targetPageUid: String? = null) {
        val execUid = targetPageUid?.takeIf { it.isNotBlank() } ?: account.uid
        startInternal(context, targetAccount = account, targetPageUid = targetPageUid, executionUid = execUid)
    }

    @Synchronized
    fun start(context: Context, targetUid: String) {
        val clean = targetUid.trim()
        if (clean.isBlank() || isRunning(clean)) return

        val allAccounts = FacebookAccountsStore.getAccounts(context)
        // 1. Kiểm tra xem có phải Profile cá nhân không
        val mainAcc = allAccounts.firstOrNull { it.uid == clean }
        if (mainAcc != null) {
            startInternal(context, targetAccount = mainAcc, targetPageUid = null, executionUid = clean)
            return
        }

        // 2. Kiểm tra xem có phải Page Profile+ không
        for (parent in allAccounts) {
            val p = parent.pages.firstOrNull {
                it.additionalProfileId == clean || it.displayUid == clean || it.pageId == clean
            }
            if (p != null) {
                val pageUid = p.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                    ?: p.displayUid.takeIf { it.isNotBlank() && it.startsWith("615") }
                    ?: p.additionalProfileId.takeIf { it.isNotBlank() }
                    ?: p.pageId
                startInternal(context, targetAccount = parent, targetPageUid = pageUid, executionUid = clean)
                return
            }
        }
    }

    private fun startInternal(
        context: Context,
        targetAccount: FacebookAccount,
        targetPageUid: String?,
        executionUid: String
    ) {
        val uid = executionUid
        if (uid.isBlank() || isRunning(uid)) return

        val cookie = targetAccount.cookie
        var token = if (!targetPageUid.isNullOrBlank()) {
            targetAccount.pages.firstOrNull {
                it.additionalProfileId == targetPageUid || it.displayUid == targetPageUid || it.pageId == targetPageUid
            }?.pageToken?.takeIf { it.isNotBlank() }
                ?: targetAccount.token.takeIf { it.isNotBlank() }
                ?: ""
        } else {
            targetAccount.token.takeIf { it.isNotBlank() } ?: ""
        }

        // Nếu token chưa có mà có Cookie, tự động lấy token từ Cookie
        if (token.isBlank() && cookie.contains("c_user=")) {
            try {
                val mgr = com.cayxu.app.facebook.FacebookAccountManager()
                val acc = mgr.getTokenFromCookie(cookie, targetAccount.phone.ifBlank { null })
                if (acc != null && acc.bio.isNotBlank()) {
                    token = acc.bio
                }
            } catch (_: Exception) {}
        }

        if (cookie.isBlank() && token.isBlank()) {
            scope.launch(Dispatchers.Main) {
                statusMap[uid] = "Lỗi: Tài khoản chưa có Cookie/Token"
                lastErrorDetail[uid] = "Không tìm thấy Cookie hoặc Token của tài khoản [$uid]. Hãy thêm Cookie/Token để nuôi nick."
                val cur = errorCountMap[uid] ?: 0
                errorCountMap[uid] = cur + 1
            }
            return
        }

        runningAccounts.add(uid)
        statusMap[uid] = "Đang khởi động nuôi nick..."
        successCountMap[uid] = 0
        errorCountMap[uid] = 0
        lastErrorDetail.remove(uid)

        val job = scope.launch {
            try {
                val currentNuoiConfig = FbNuoiConfigStore.getConfig(context)
                val client = com.cayxu.app.automation.facebook.nuoi.FbNuoiTaskRunner.buildClient(targetAccount.phone)
                val runner = com.cayxu.app.automation.facebook.nuoi.FbNuoiTaskRunner(
                    cookie = cookie,
                    token = token,
                    targetActorId = targetPageUid, // Truyền UID của Page nếu là Page Profile+, null nếu là Profile
                    myUid = targetPageUid ?: targetAccount.uid,
                    myName = targetAccount.name,
                    config = currentNuoiConfig,
                    client = client
                )

                runner.run { progress ->
                    scope.launch(Dispatchers.Main) {
                        statusMap[uid] = progress.status
                        successCountMap[uid] = progress.successReactions + progress.successComments + progress.successFriends
                        errorCountMap[uid] = progress.totalErrors
                        if (progress.errorMessage != null) {
                            lastErrorDetail[uid] = progress.errorMessage
                        }
                    }
                }
            } catch (e: CancellationException) {
                scope.launch(Dispatchers.Main) {
                    statusMap[uid] = "Đã dừng nuôi nick"
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) {
                    val msg = e.message ?: "Lỗi không xác định"
                    statusMap[uid] = "Lỗi: $msg"
                    lastErrorDetail[uid] = msg
                    val cur = errorCountMap[uid] ?: 0
                    errorCountMap[uid] = cur + 1
                }
            } finally {
                scope.launch(Dispatchers.Main) {
                    runningAccounts.remove(uid)
                    activeJobs.remove(uid)
                }
            }
        }

        activeJobs[uid] = job
    }

    @Synchronized
    fun startAccounts(context: Context, uids: List<String>) {
        uids.forEach { uid ->
            start(context, uid)
        }
    }

    @Synchronized
    fun stop(uid: String) {
        activeJobs[uid]?.cancel()
        activeJobs.remove(uid)
        runningAccounts.remove(uid)
        statusMap[uid] = "Đã dừng"
    }

    @Synchronized
    fun stopAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        runningAccounts.clear()
    }
}
