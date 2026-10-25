package com.cayxu.app.ui.f4f

import android.content.Context
import android.widget.Toast
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.XsmmTaskActionResult
import com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.overlay.xsmm.XsmmJobStatusBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Bộ chạy nhiệm vụ TikTok tự động cho Follow4Follow (F4F):
 * - Nhận danh sách các tài khoản TikTok đã chọn
 * - Lấy nhiệm vụ kiếm xu từ Follow4Follow API (Follow, Like)
 * - Tự động mở TikTok và tương tác qua Dịch vụ Trợ năng
 * - Gửi báo cáo hoàn thành job lên server để nhận coin
 */
object F4fTikTokTaskRunner {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var runnerJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    var totalCompleted = 0
        private set
    var totalCoinsEarned = 0L
        private set

    fun isRunning(): Boolean = _isRunning.value

    fun start(
        context: Context,
        selectedHandles: List<String>,
        variant: TikTokAppVariant = TikTokAppVariant.STANDARD,
        delaySeconds: Int = 5,
        targetTaskCount: Int = 0,
        enableFollow: Boolean = true,
        enableLike: Boolean = true,
        onStatusChange: (String) -> Unit = {},
        onProgressUpdate: (completed: Int, coins: Long) -> Unit = { _, _ -> }
    ) {
        if (_isRunning.value) return

        val token = F4fAccountStore.getToken(context).orEmpty()
        if (token.isBlank()) {
            Toast.makeText(context, "Chưa đăng nhập Follow4Follow", Toast.LENGTH_SHORT).show()
            return
        }

        if (!TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
            Toast.makeText(context, "Cần cấp quyền Trợ năng để tự động làm nhiệm vụ", Toast.LENGTH_LONG).show()
            TikTokAppLauncher.openAccessibilitySettings(context)
            return
        }

        if (selectedHandles.isEmpty()) {
            Toast.makeText(context, "Vui lòng chọn ít nhất 1 tài khoản để chạy", Toast.LENGTH_SHORT).show()
            return
        }

        _isRunning.value = true
        totalCompleted = 0
        totalCoinsEarned = 0L

        runnerJob?.cancel()
        runnerJob = scope.launch {
            try {
                XsmmJobStatusBridge.update("Bắt đầu chạy Follow4Follow TikTok...")
                onStatusChange("Đang khởi tạo danh sách tài khoản...")

                // Tải danh sách tài khoản remote từ F4F để lấy account_id dạng số
                val accountIdMap = mutableMapOf<String, Long>()
                when (val listRes = F4fApiClient.fetchAccountList(token, "tiktok")) {
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

                val cleanHandles = selectedHandles.map { it.trim().removePrefix("@").lowercase() }.filter { it.isNotBlank() }

                while (isActive && _isRunning.value) {
                    for (handle in cleanHandles) {
                        if (!isActive || !_isRunning.value) break

                        if (targetTaskCount in 1..totalCompleted) {
                            val doneMsg = "Đã đạt mục tiêu $totalCompleted nhiệm vụ. Hoàn thành!"
                            XsmmJobStatusBridge.update(doneMsg)
                            onStatusChange(doneMsg)
                            stop()
                            return@launch
                        }

                        val numericAccountId = accountIdMap[handle] ?: 0L
                        val statusNick = "Chạy nick @$handle (ID: $numericAccountId)..."
                        XsmmJobStatusBridge.update(statusNick)
                        onStatusChange(statusNick)

                        // 1. Nhận Job từ F4F (ưu tiên Follow, sau đó Like)
                        var jobInfo: F4fJobInfo? = null
                        val jobTypesToTry = buildList {
                            if (enableFollow) add("follow")
                            if (enableLike) add("like")
                        }.ifEmpty { listOf("follow") }

                        for (jobType in jobTypesToTry) {
                            if (!isActive || !_isRunning.value) break
                            XsmmJobStatusBridge.update("@$handle: Đang tìm job $jobType...")
                            val jobRes = F4fApiClient.getEarnJob(token, platform = "tiktok", typeJob = jobType, uid = handle)
                            if (jobRes is F4fApiResult.Success) {
                                jobInfo = jobRes.data
                                break
                            }
                            delay(1000L)
                        }

                        if (jobInfo == null) {
                            val waitMsg = "@$handle: Tạm hết nhiệm vụ, chờ 8 giây..."
                            XsmmJobStatusBridge.update(waitMsg)
                            onStatusChange(waitMsg)
                            delay(8000L)
                            continue
                        }

                        // 2. Mở TikTok đến profile/video của job
                        val isLikeJob = jobInfo.typeJob.equals("like", ignoreCase = true)
                        val jobLabel = if (isLikeJob) "Like (thả tim)" else "Follow"
                        val effAccountId = if (jobInfo.accountId > 0L) {
                            jobInfo.accountId
                        } else if (numericAccountId > 0L) {
                            numericAccountId
                        } else {
                            jobInfo.jobId
                        }

                        if (isLikeJob) {
                            XsmmJobStatusBridge.update("@$handle: Đánh dấu bắt đầu Like #${jobInfo.jobId}...")
                            F4fApiClient.markStartTiktokLike(token, jobInfo.jobId, effAccountId)
                            delay(500L)
                        }

                        val taskMsg = "@$handle: Làm job #${jobInfo.jobId} ($jobLabel)..."
                        XsmmJobStatusBridge.update(taskMsg)
                        onStatusChange(taskMsg)

                        val targetUrl = jobInfo.targetLink.let { link ->
                            if (link.startsWith("http://") || link.startsWith("https://")) {
                                link
                            } else {
                                "https://www.tiktok.com/@${link.trim().removePrefix("@")}"
                            }
                        }

                        TikTokAppLauncher.openUserProfile(context, targetUrl)
                        delay(2000L)

                        // 3. Kích hoạt Trợ năng bấm nút tương tác
                        val actionId = XsmmTaskAutomationBridge.triggerTask(
                            taskType = if (isLikeJob) "like" else "follow",
                            swipeBefore = false,
                            returnHomeAndSwipe = false,
                            durationSeconds = 3
                        )

                        // Chờ thao tác hoàn thành
                        val startActionTime = System.currentTimeMillis()
                        var actionSuccess = false
                        while (isActive && (System.currentTimeMillis() - startActionTime) < 15000L) {
                            val res = XsmmTaskAutomationBridge.result.value
                            if (res is XsmmTaskActionResult.Completed && res.actionId == actionId) {
                                actionSuccess = res.success
                                break
                            }
                            delay(400L)
                        }

                        // 4. Nghỉ delay theo cấu hình trước khi xác nhận
                        val waitDelay = (delaySeconds.coerceAtLeast(3)) * 1000L
                        XsmmJobStatusBridge.update("Chờ ${delaySeconds}s xác nhận...")
                        delay(waitDelay)

                        // 5. Xác nhận hoàn thành job lên server F4F
                        when (val completeRes = F4fApiClient.completeJob(
                            rawToken = token,
                            jobId = jobInfo.jobId,
                            accountId = effAccountId,
                            type = "tiktok",
                            typeJob = jobInfo.typeJob
                        )) {
                            is F4fApiResult.Success -> {
                                val reward = completeRes.data.takeIf { it > 0 } ?: jobInfo.rewardCoins
                                totalCompleted++
                                totalCoinsEarned += reward
                                F4fSession.points.value = F4fSession.points.value + reward

                                val succMsg = "+$reward coin (#${jobInfo.jobId} $jobLabel) | Xong $totalCompleted NV"
                                XsmmJobStatusBridge.update(succMsg)
                                onStatusChange(succMsg)
                                onProgressUpdate(totalCompleted, totalCoinsEarned)
                            }
                            is F4fApiResult.Error -> {
                                val errMsg = "Lỗi duyệt job: ${completeRes.message}"
                                XsmmJobStatusBridge.update(errMsg)
                                onStatusChange(errMsg)
                            }
                        }

                        delay(2000L)
                    }
                }
            } catch (e: Exception) {
                val err = "Lỗi chạy F4F: ${e.message}"
                XsmmJobStatusBridge.update(err)
                onStatusChange(err)
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        runnerJob?.cancel()
        runnerJob = null
        XsmmJobStatusBridge.update("Đã dừng chạy Follow4Follow")
    }
}
