package com.cayxu.app.tuongtaccheo

import android.content.Context
import com.cayxu.app.data.local.TtcRunConfigStore

class TuongTacCheoJobRunner(
    private val apiClient: TuongTacCheoApiClient,
    var delayBetweenJobsSeconds: Int = 10,
    var doJobDelaySeconds: Int = 5,
    private val context: Context? = null,
    var jobTypeProvider: (() -> TTCJobType)? = null,
    private val onStatusUpdate: ((String) -> Unit)? = null
) {
    // Constructor nạp delay trực tiếp từ Bảng Cấu hình nếu có Context
    constructor(
        context: Context,
        apiClient: TuongTacCheoApiClient,
        onStatusUpdate: ((String) -> Unit)? = null
    ) : this(
        apiClient = apiClient,
        delayBetweenJobsSeconds = TtcRunConfigStore.getConfig(context).delaySeconds,
        doJobDelaySeconds = TtcRunConfigStore.getConfig(context).doJobDelaySeconds,
        context = context,
        jobTypeProvider = {
            val cfg = TtcRunConfigStore.getConfig(context)
            cfg.taskTypes.firstOrNull()?.let { TTCJobType.fromKey(it) } ?: TTCJobType.FB_LIKE
        },
        onStatusUpdate = onStatusUpdate
    )

    constructor(
        context: Context,
        apiClient: TuongTacCheoApiClient,
        jobTypeProvider: (() -> TTCJobType)?,
        onStatusUpdate: ((String) -> Unit)? = null
    ) : this(
        apiClient = apiClient,
        delayBetweenJobsSeconds = TtcRunConfigStore.getConfig(context).delaySeconds,
        doJobDelaySeconds = TtcRunConfigStore.getConfig(context).doJobDelaySeconds,
        context = context,
        jobTypeProvider = jobTypeProvider,
        onStatusUpdate = onStatusUpdate
    )

    private var isRunning = false

    fun getCurrentJobType(fallback: TTCJobType = TTCJobType.FB_LIKE): TTCJobType {
        return jobTypeProvider?.invoke()
            ?: context?.let { ctx ->
                val cfg = TtcRunConfigStore.getConfig(ctx)
                cfg.taskTypes.firstOrNull()?.let { TTCJobType.fromKey(it) }
            }
            ?: fallback
    }

    // Đếm ngược nghỉ delay chuẩn xác từng giây hiển thị lên UI
    private fun countdownDelay(seconds: Int) {
        val sec = seconds.coerceAtLeast(1)
        for (s in sec downTo 1) {
            if (!isRunning) break
            onStatusUpdate?.invoke("Nghỉ ${s}s...")
            try {
                Thread.sleep(1000)
            } catch (_: Exception) {}
        }
    }

    @JvmOverloads
    fun startAutoJob(
        jobType: TTCJobType,
        targetNickUid: String,
        delaySeconds: Int? = null,
        doJobDelaySec: Int? = null,
        jobTypeProvider: (() -> TTCJobType)? = null,
        executorAction: (TTCJob) -> Boolean
    ) {
        if (delaySeconds != null && delaySeconds > 0) {
            this.delayBetweenJobsSeconds = delaySeconds
        }
        if (doJobDelaySec != null && doJobDelaySec > 0) {
            this.doJobDelaySeconds = doJobDelaySec
        }
        if (jobTypeProvider != null) {
            this.jobTypeProvider = jobTypeProvider
        }
        isRunning = true
        onStatusUpdate?.invoke("[TTC] Đang đặt nick [$targetNickUid]...")

        try {
            val configRes = apiClient.autoPrepareAndSetNick(targetNickUid, "fb") { log ->
                onStatusUpdate?.invoke(log)
            }
            if (!configRes.isSuccess) {
                onStatusUpdate?.invoke("[TTC] Lỗi: ${configRes.message}")
                return
            }

            var currentJobType = getCurrentJobType(jobType)
            onStatusUpdate?.invoke("[TTC] Đặt nick thành công! Đang lấy job [${currentJobType.displayName}]...")

            while (isRunning) {
                // Đọc loại job theo thời gian thực (Hot-switching)
                currentJobType = getCurrentJobType(currentJobType)

                val jobs = apiClient.getJobs(currentJobType)
                if (jobs.isEmpty()) {
                    onStatusUpdate?.invoke("[TTC] Tạm hết job [${currentJobType.displayName}]. Chờ 10s...")
                    for (s in 10 downTo 1) {
                        if (!isRunning) break
                        try { Thread.sleep(1000) } catch (_: Exception) {}
                    }
                    continue
                }

                onStatusUpdate?.invoke("[TTC] Nhận ${jobs.size} job [${currentJobType.displayName}]")

                for (job in jobs) {
                    if (!isRunning) break

                    // Kiểm tra chuyển loại job tức thì ngay trong đợt job
                    val liveType = getCurrentJobType(currentJobType)
                    if (liveType != currentJobType) {
                        currentJobType = liveType
                        onStatusUpdate?.invoke("[TTC] Chuyển sang job [${currentJobType.displayName}]...")
                        break // Ngắt để lấy job của loại mới
                    }

                    val targetId = job.idpost ?: job.idfb ?: job.link ?: job.id
                    val shortTarget = if (targetId.length > 15) targetId.take(12) + "..." else targetId

                    // BƯỚC 0: NGHỈ DELAY LÀM JOB (TRÁNH QUÉT SPAM FB)
                    val waitSec = doJobDelaySeconds.coerceIn(3, 10)
                    for (s in waitSec downTo 1) {
                        if (!isRunning) break
                        onStatusUpdate?.invoke("⏳ Chờ làm ${s}s...")
                        try { Thread.sleep(1000) } catch (_: Exception) {}
                    }
                    if (!isRunning) break

                    // BƯỚC 1: GỌI HÀM TƯƠNG TÁC FACEBOOK (LIKE / CẢM XÚC / FOLLOW / COMMENT)
                    val isFbSuccess = try {
                        executorAction(job) // Thực hiện tương tác bài viết Facebook
                    } catch (e: Exception) {
                        onStatusUpdate?.invoke("[TTC] Lỗi: ${e.message}")
                        false
                    }

                    onStatusUpdate?.invoke("[TTC] Làm ID: [$shortTarget] -> ${if (isFbSuccess) "Thành công" else "Thất bại"}")

                    // BƯỚC 2: KIỂM TRA KẾT QUẢ TƯƠNG TÁC FACEBOOK
                    if (!isFbSuccess) {
                        countdownDelay(delayBetweenJobsSeconds)
                        continue
                    }

                    // Nếu job là Comment: cố định delay 60 giây để TTC duyệt
                    val isCommentJob = currentJobType == TTCJobType.FB_COMMENT || currentJobType.apiType == "cmtcheo"
                    if (isCommentJob) {
                        onStatusUpdate?.invoke("[TTC] Đã cmt xong. Chờ 60s để TTC duyệt...")
                        for (s in 60 downTo 1) {
                            if (!isRunning) break
                            try { Thread.sleep(1000) } catch (_: Exception) {}
                        }
                    } else {
                        try { Thread.sleep(2000) } catch (_: Exception) {}
                    }
                    if (!isRunning) break

                    // BƯỚC 3: GỬI LỆNH NHẬN XU LÊN SERVER TTC
                    val claimResult = apiClient.claimReward(job.id, currentJobType)
                    if (claimResult.isSuccess) {
                        onStatusUpdate?.invoke("[TTC] +${claimResult.xuThem} xu | Dư: ${claimResult.sodu} xu")
                    } else {
                        onStatusUpdate?.invoke("[TTC] Lỗi: ${claimResult.message}")
                    }

                    // BƯỚC 4: NGHỈ DELAY THEO CẤU HÌNH
                    countdownDelay(delayBetweenJobsSeconds)
                }
            }
        } catch (e: Exception) {
            onStatusUpdate?.invoke("[TTC] Lỗi: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        onStatusUpdate?.invoke("[TTC] Đã dừng chạy.")
    }
}
