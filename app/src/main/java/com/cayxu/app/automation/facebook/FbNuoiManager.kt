package com.cayxu.app.automation.facebook

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.FbNuoiConfigStore
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

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
    fun start(context: Context, account: FacebookAccount) {
        val uid = account.uid
        if (uid.isBlank() || isRunning(uid)) return

        runningAccounts.add(uid)
        statusMap[uid] = "Đang chuẩn bị chạy nuôi nick..."
        successCountMap[uid] = 0
        errorCountMap[uid] = 0
        lastErrorDetail.remove(uid)

        val job = scope.launch {
            try {
                val config = FbNuoiConfigStore.getConfig(context)
                val runner = FbNuoiTaskRunner(
                    context = context,
                    account = account,
                    config = config,
                    onStatus = { msg ->
                        withContext(Dispatchers.Main) {
                            statusMap[uid] = msg
                        }
                    },
                    onSuccess = {
                        withContext(Dispatchers.Main) {
                            val cur = successCountMap[uid] ?: 0
                            successCountMap[uid] = cur + 1
                        }
                    },
                    onError = { err ->
                        withContext(Dispatchers.Main) {
                            val cur = errorCountMap[uid] ?: 0
                            errorCountMap[uid] = cur + 1
                            lastErrorDetail[uid] = err
                        }
                    }
                )
                runner.run()
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    statusMap[uid] = "Đã dừng nuôi nick"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = e.message ?: "Lỗi không xác định"
                    statusMap[uid] = "Lỗi: $msg"
                    lastErrorDetail[uid] = msg
                    val cur = errorCountMap[uid] ?: 0
                    errorCountMap[uid] = cur + 1
                }
            } finally {
                withContext(Dispatchers.Main) {
                    runningAccounts.remove(uid)
                    activeJobs.remove(uid)
                }
            }
        }

        activeJobs[uid] = job
    }

    @Synchronized
    fun start(context: Context, targetUid: String) {
        val clean = targetUid.trim()
        if (clean.isBlank() || isRunning(clean)) return

        val allAccounts = FacebookAccountsStore.getAccounts(context)
        val mainAcc = allAccounts.firstOrNull { it.uid == clean }
        if (mainAcc != null) {
            start(context, mainAcc)
            return
        }

        for (parent in allAccounts) {
            val p = parent.pages.firstOrNull {
                it.additionalProfileId == clean || it.displayUid == clean || it.pageId == clean
            }
            if (p != null) {
                val effectiveToken = p.pageToken.ifBlank { parent.bio }
                val pageAccount = parent.copy(
                    uid = clean,
                    name = p.pageName.ifBlank { parent.name },
                    bio = effectiveToken
                )
                start(context, pageAccount)
                return
            }
        }
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
