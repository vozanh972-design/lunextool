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
        if (cookie.isBlank()) {
            scope.launch(Dispatchers.Main) {
                statusMap[uid] = "Lỗi: Tài khoản chưa có Cookie"
                lastErrorDetail[uid] = "Không tìm thấy Cookie của tài khoản [$uid]. Hãy thêm Cookie để nuôi nick."
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
                    targetActorId = targetPageUid, // Truyền UID của Page nếu là Page Profile+, null nếu là Profile
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
