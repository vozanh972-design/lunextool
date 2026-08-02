package com.cayxu.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.util.DeviceUtils
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Định kỳ tự gọi lại verify_key.php để xác nhận key vẫn còn hợp lệ với server
 * thật - đây là lớp phòng thủ quan trọng nhất chống crack kiểu "bypass hoàn
 * toàn" (patch code luôn trả về đã đăng nhập mà không gọi API), vì worker này
 * chạy độc lập, không phụ thuộc vào màn hình Login có bị bypass hay không.
 *
 * Nếu server trả về lỗi (key hết hạn/bị thu hồi/dùng sai thiết bị...), app sẽ
 * XOÁ key và đưa NGAY người dùng về màn nhập Key để họ tự nhập key mới/gia hạn
 * - đây KHÔNG phải khoá vĩnh viễn, khác với trường hợp phát hiện app bị patch/
 * bypass (xem IntegrityGuard/CayXuApp.onCreate, trường hợp đó mới khoá chết).
 *
 * Mỗi lần chạy xong tự đặt lịch lần kế tiếp với độ trễ NGẪU NHIÊN 3-10 tiếng
 * (random riêng theo từng máy, không đồng bộ) để hàng loạt máy không cùng gọi
 * server 1 lúc gây nghẽn.
 */
class KeyRecheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePrefs(applicationContext)
        val key = prefs.getKey()

        // Nếu chưa đăng nhập (chưa có key lưu) hoặc app đã bị khoá vĩnh viễn (crack/
        // patch) thì không có gì để kiểm tra nữa.
        if (!key.isNullOrBlank() && !prefs.isPermanentlyBlocked()) {
            val deviceId = DeviceUtils.getAndroidId(applicationContext)
            val repository = AuthRepository()
            when (repository.verifyKey(key, deviceId)) {
                is AuthResult.Success -> {
                    // Vẫn hợp lệ -> không làm gì thêm.
                }
                is AuthResult.ApiError -> {
                    // Server xác nhận key không còn hợp lệ (hết hạn / bị thu hồi /
                    // đang dùng ở thiết bị khác...) -> xoá key NGAY, không giữ lại
                    // trong tool nữa. KHÔNG khoá vĩnh viễn (không phải phát hiện
                    // crack) - báo cho UI đưa người dùng về màn nhập Key ngay lập
                    // tức để họ tự nhập key mới/gia hạn.
                    prefs.clearKey()
                    com.cayxu.app.data.local.AppLockState.markKeyRevoked()
                }
                is AuthResult.NetworkError -> {
                    // Lỗi mạng tạm thời (mất mạng, server bảo trì...) - KHÔNG khoá
                    // app vì đây có thể chỉ là sự cố tạm thời, không phải key sai.
                    // Worker sẽ tự thử lại ở lần chạy kế tiếp.
                }
            }
        }

        scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "key_recheck_worker"

        /** Random 3–10 tiếng (tính bằng phút) cho lần chạy tiếp theo. */
        private fun randomDelayMinutes(): Long = Random.nextLong(3 * 60L, 10 * 60L + 1)

        /**
         * Gọi hàm này 1 lần khi app khởi động (CayXuApp.onCreate) để bắt đầu chuỗi
         * kiểm tra định kỳ. ExistingWorkPolicy.REPLACE đảm bảo không bị chạy trùng
         * nhiều worker cùng lúc nếu app được mở lại nhiều lần.
         */
        fun scheduleNext(context: Context) {
            val request = OneTimeWorkRequestBuilder<KeyRecheckWorker>()
                .setInitialDelay(randomDelayMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
