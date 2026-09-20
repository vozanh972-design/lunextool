package com.cayxu.app.tuongtaccheo

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runner tự động nhận và hoàn thành job TTC.
 */
class TuongTacCheoJobRunner(
    private val apiClient: TuongTacCheoApiClient,
    private val delayBetweenJobsSeconds: Int = 3
) {
    private var isRunning = false

    fun startAutoJob(
        jobType: TTCJobType,
        targetNickUid: String,
        executorAction: (TTCJob) -> Boolean
    ) {
        isRunning = true
        log("Bắt đầu cấu hình nick $targetNickUid chạy job ${jobType.displayName}...")

        try {
            val configured = apiClient.setNickRun(targetNickUid, if (jobType.apiType.startsWith("tiktok")) "tiktok" else "fb")
            if (!configured) {
                log("Cấu hình nick $targetNickUid thất bại! Kiểm tra lại nick trên TTC.")
                return
            }
            log("Cấu hình nick $targetNickUid thành công!")

            while (isRunning) {
                log("Đang lấy danh sách nhiệm vụ ${jobType.displayName}...")
                val jobs = apiClient.getJobs(jobType)

                if (jobs.isEmpty()) {
                    log("Tạm thời hết job hoặc đang cooldown. Chờ 10 giây...")
                    Thread.sleep(10000)
                    continue
                }

                log("Đã tải được ${jobs.size} nhiệm vụ!")
                for (job in jobs) {
                    if (!isRunning) break

                    log("Đang làm nhiệm vụ ID: ${job.id} (Link: ${job.link ?: job.idpost})...")
                    val executed = executorAction(job)

                    if (executed) {
                        log("Làm xong nhiệm vụ, đang nhận tiền...")
                        val result = apiClient.claimReward(job.id, jobType)
                        if (result.isSuccess) {
                            log("✔️ Nhận xu thành công! +${result.xuThem} xu | Số dư hiện tại: ${result.sodu} xu")
                        } else {
                            log("❌ Nhận xu thất bại: ${result.message}")
                        }
                    } else {
                        log("❌ Thực hiện nhiệm vụ thất bại!")
                    }

                    Thread.sleep((delayBetweenJobsSeconds * 1000).toLong())
                }
            }
        } catch (e: Exception) {
            log("Lỗi trong quá trình chạy auto: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        println("[$time] [TTC] $message")
    }
}
