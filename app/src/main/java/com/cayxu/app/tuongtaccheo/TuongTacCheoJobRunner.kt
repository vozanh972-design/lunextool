package com.cayxu.app.tuongtaccheo

class TuongTacCheoJobRunner(
    private val apiClient: TuongTacCheoApiClient,
    private val delayBetweenJobsSeconds: Int = 5,
    private val onStatusUpdate: ((String) -> Unit)? = null
) {
    private var isRunning = false

    fun startAutoJob(
        jobType: TTCJobType,
        targetNickUid: String,
        executorAction: (TTCJob) -> Boolean
    ) {
        isRunning = true
        onStatusUpdate?.invoke("Đang cấu hình đặt nick/page [$targetNickUid]...")
        try {
            val configResult = apiClient.autoPrepareAndSetNick(targetNickUid, if (jobType.apiType.startsWith("tiktok")) "tiktok" else "fb")
            if (!configResult.isSuccess) {
                onStatusUpdate?.invoke("❌ ${configResult.message}")
                return
            }
            onStatusUpdate?.invoke("✔️ Đặt nick thành công! Đang lấy nhiệm vụ...")
            while (isRunning) {
                val jobs = apiClient.getJobs(jobType)
                if (jobs.isEmpty()) {
                    onStatusUpdate?.invoke("Tạm hết job ${jobType.displayName}. Chờ 10s...")
                    Thread.sleep(10000)
                    continue
                }
                for (job in jobs) {
                    if (!isRunning) break
                    onStatusUpdate?.invoke("Đang làm nhiệm vụ ID: ${job.id}...")
                    val ok = executorAction(job)
                    if (ok) {
                        val res = apiClient.claimReward(job.id, jobType)
                        if (res.isSuccess) {
                            onStatusUpdate?.invoke("✔️ +${res.xuThem} xu | Số dư: ${res.sodu} xu")
                        } else {
                            onStatusUpdate?.invoke("❌ Nhận xu thất bại: ${res.message}")
                        }
                    } else {
                        onStatusUpdate?.invoke("❌ Thực hiện tương tác thất bại!")
                    }
                    Thread.sleep((delayBetweenJobsSeconds * 1000).toLong())
                }
            }
        } catch (e: Exception) {
            onStatusUpdate?.invoke("Lỗi: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        onStatusUpdate?.invoke("Đã dừng chạy.")
    }
}
