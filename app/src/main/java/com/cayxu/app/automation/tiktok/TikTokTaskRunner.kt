package com.cayxu.app.automation.tiktok

import android.app.ActivityManager
import android.content.Context
import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Chạy tác vụ cho MỘT tài khoản TikTok cụ thể - RIÊNG cho TikTok, không đụng tới
 * luồng của các nền tảng khác. Khi bấm "Chạy":
 *  1) Buộc dừng app TikTok tương ứng trước (best-effort - xem ghi chú bên dưới).
 *  2) Mở lại app.
 *  3) Cập nhật trạng thái đang làm gì để hiển thị ngay dưới từng tài khoản trong danh sách.
 *
 * Lưu ý kỹ thuật quan trọng: trên Android, một app thường (không phải app hệ thống/không
 * root) KHÔNG có quyền force-stop app khác giống như trong Cài đặt > Ứng dụng. Cách gần
 * nhất được phép là ActivityManager.killBackgroundProcesses(package) - chỉ dừng được các
 * tiến trình nền đã bị hệ thống đưa vào cache, không đảm bảo dừng được app đang ở foreground.
 * Nếu máy có root/Shizuku, có thể thay hàm forceStopBestEffort() bằng lệnh
 * `am force-stop <package>` để chắc chắn hơn.
 */
object TikTokTaskRunner {
    sealed class RunStatus(val label: String) {
        data object Idle : RunStatus("")
        data object StoppingApp : RunStatus("Đang buộc dừng TikTok...")
        data object Launching : RunStatus("Đang mở lại TikTok...")
        data object RunningTask : RunStatus("Đang chạy nhiệm vụ...")
        data object Done : RunStatus("Hoàn tất")
        data class Error(val reason: String) : RunStatus(reason)
    }

    private val _statusByUid = MutableStateFlow<Map<String, RunStatus>>(emptyMap())
    val statusByUid: StateFlow<Map<String, RunStatus>> = _statusByUid.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    fun statusOf(uid: String): RunStatus = _statusByUid.value[uid] ?: RunStatus.Idle

    fun run(context: Context, uid: String, variant: TikTokAppVariant) {
        // Tránh bấm chạy trùng khi tài khoản đang chạy dở.
        val current = statusOf(uid)
        if (current is RunStatus.StoppingApp || current is RunStatus.Launching || current is RunStatus.RunningTask) return

        scope.launch {
            setStatus(uid, RunStatus.StoppingApp)
            forceStopBestEffort(context, variant)
            delay(600)

            setStatus(uid, RunStatus.Launching)
            val launched = TikTokAppLauncher.launch(context, variant)
            if (!launched) {
                setStatus(uid, RunStatus.Error("Không mở được app, kiểm tra lại đã cài đặt chưa"))
                delay(2500)
                setStatus(uid, RunStatus.Idle)
                return@launch
            }
            delay(800)

            setStatus(uid, RunStatus.RunningTask)
            // TODO: gắn logic nhiệm vụ thật (nếu có) tại đây - hiện tool chỉ đảm bảo
            // buộc dừng -> mở lại -> báo trạng thái theo đúng yêu cầu.
            delay(1500)

            setStatus(uid, RunStatus.Done)
            delay(1500)
            setStatus(uid, RunStatus.Idle)
        }
    }

    private fun forceStopBestEffort(context: Context, variant: TikTokAppVariant) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(TikTokAppLauncher.packageNameOf(variant))
        } catch (_: Exception) {
            // Bỏ qua - đây chỉ là best-effort, không chặn luồng mở lại app phía sau.
        }
    }

    private fun setStatus(uid: String, status: RunStatus) {
        _statusByUid.update { it.toMutableMap().apply { put(uid, status) } }
    }
}
