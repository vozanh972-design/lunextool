package com.cayxu.app.tuongtaccheo

import android.content.Context
import com.cayxu.app.data.local.TtcRunConfigStore

class TuongTacCheoJobRunner(
    private val apiClient: TuongTacCheoApiClient,
    var delayBetweenJobsSeconds: Int = 5,
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
        onStatusUpdate = onStatusUpdate
    )

    private var isRunning = false

    // Đếm ngược nghỉ delay chuẩn xác từng giây hiển thị lên UI
    private fun countdownDelay(seconds: Int) {
        val sec = seconds.coerceAtLeast(1)
        for (s in sec downTo 1) {
            if (!isRunning) break
            onStatusUpdate?.invoke("Nghỉ delay ${s}s trước job tiếp theo...")
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
        executorAction: (TTCJob) -> Boolean
    ) {
        if (delaySeconds != null && delaySeconds > 0) {
            this.delayBetweenJobsSeconds = delaySeconds
        }
        isRunning = true
        onStatusUpdate?.invoke("Đang cấu hình đặt nick/page [$targetNickUid]...")

        try {
            val configRes = apiClient.autoPrepareAndSetNick(targetNickUid, "fb") { log ->
                onStatusUpdate?.invoke(log)
            }
            if (!configRes.isSuccess) {
                onStatusUpdate?.invoke("❌ ${configRes.message}")
                return
            }
            onStatusUpdate?.invoke("✔️ Đặt nick thành công! Đang lấy job ${jobType.displayName}...")

            while (isRunning) {
                val jobs = apiClient.getJobs(jobType)
                if (jobs.isEmpty()) {
                    onStatusUpdate?.invoke("Tạm hết job ${jobType.displayName}. Chờ 10s...")
                    for (s in 10 downTo 1) {
                        if (!isRunning) break
                        try { Thread.sleep(1000) } catch (_: Exception) {}
                    }
                    continue
                }

                for (job in jobs) {
                    if (!isRunning) break

                    val targetId = job.idpost ?: job.idfb ?: job.link ?: job.id
                    onStatusUpdate?.invoke("🎯 Nhận job: ID [${job.id}] - Mục tiêu: [$targetId]")
                    try { Thread.sleep(1000) } catch (_: Exception) {}

                    // BƯỚC 1: GỌI HÀM TƯƠNG TÁC FACEBOOK (LIKE / CẢM XÚC)
                    onStatusUpdate?.invoke("⏳ Đang thực hiện tương tác Facebook cho [$targetId]...")

                    val isFbSuccess = try {
                        executorAction(job) // Thực hiện like bài viết Facebook
                    } catch (e: Exception) {
                        onStatusUpdate?.invoke("❌ Lỗi crash khi Like FB: ${e.message}")
                        false
                    }

                    // BƯỚC 2: KIỂM TRA KẾT QUẢ LIKE FACEBOOK
                    if (!isFbSuccess) {
                        onStatusUpdate?.invoke("❌ Like Facebook THẤT BẠI cho [$targetId]! Bỏ qua nhận xu.")
                        countdownDelay(delayBetweenJobsSeconds)
                        continue
                    }

                    onStatusUpdate?.invoke("✔️ Like Facebook THÀNH CÔNG! Đang gửi nhận xu lên TTC...")
                    try { Thread.sleep(1500) } catch (_: Exception) {} // Nghỉ 1.5s để TTC kịp cập nhật tương tác

                    // BƯỚC 3: GỬI LỆNH NHẬN XU LÊN SERVER TTC
                    val claimResult = apiClient.claimReward(job.id, jobType)
                    if (claimResult.isSuccess) {
                        onStatusUpdate?.invoke("💰 Thành công! +${claimResult.xuThem} xu | Số dư mới: ${claimResult.sodu} xu")
                    } else {
                        onStatusUpdate?.invoke("⚠️ Nhận xu thất bại từ TTC: ${claimResult.message}")
                    }

                    // BƯỚC 4: NGHỈ DELAY THEO CẤU HÌNH
                    onStatusUpdate?.invoke("⏳ Nghỉ delay ${delayBetweenJobsSeconds}s...")
                    countdownDelay(delayBetweenJobsSeconds)
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
