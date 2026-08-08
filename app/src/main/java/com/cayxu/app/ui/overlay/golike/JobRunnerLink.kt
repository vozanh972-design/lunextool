package com.cayxu.app.ui.overlay.golike

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.navigation.NavController
import com.cayxu.app.automation.tiktok.GolikeSwitchAccountBridge
import com.cayxu.app.automation.tiktok.TikTokAccessibilityService
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.screens.golike.GolikeSession

/**
 * Toàn bộ dữ liệu hiển thị trên màn nổi "Làm NV" - KHÔNG có giá trị mặc định kiểu dữ liệu
 * mẫu, phải truyền đúng dữ liệu THẬT từ nơi gọi (khi đã có hệ thống job thật).
 */
data class JobRunData(
    val modeLabel: String,
    val accountHandle: String,
    val accountTaskCount: Int = 0,
    val accountSecondaryId: String = "",
    val jobId: String = "",
    val jobType: String = "",
    val jobPrice: String = "",
    val jobSuccessCount: Int = 0,
    val jobFailCount: Int = 0,
    val jobEarned: String = "",
    val jobLink: String = "",
    val initialStatus: String = ""
)

/** 1 tài khoản trong hàng đợi "Chạy" (khi tick chọn nhiều acc cùng lúc). */
data class JobRunAccount(
    val handle: String,
    val packageName: String,
    val variant: TikTokAppVariant,
    val taskCount: Int = 0,
    /** ID NỘI BỘ của GoLike (field "id" trong GET /api/tiktok-account) - dùng để gọi API
     *  lấy job (?account_id=...). 0 nghĩa là chưa xác định được (không gọi API job được). */
    val golikeAccountId: Long = 0L
)

/**
 * Bấm "Chạy" để làm NV -> kiểm tra ĐỦ 3 điều kiện y hệt màn "Thêm" (xem
 * TikTokProfileLink.startAddToGolikeOverlay): (1) đã đăng nhập GoLike, (2) "Hiển thị trên
 * ứng dụng khác", (3) "Trợ năng" - THIẾU cái nào sẽ KHÔNG mở màn nổi, tự chuyển sang đúng màn
 * cần bật. Đủ điều kiện mới khởi chạy GolikeJobRunnerOverlayService.
 *
 * Nếu [accountsQueue] có acc và acc đầu tiên là TikTokAppVariant.STANDARD (bản hỗ trợ
 * "Chuyển đổi tài khoản" trong app), sẽ TÁI SỬ DỤNG luồng chuyển acc của "Thêm"
 * (GolikeSwitchAccountBridge, KHÔNG đụng code luồng "Thêm") để tự mở TikTok -> Hồ sơ -> menu
 * -> Cài đặt và quyền riêng tư -> Chuyển đổi tài khoản -> chọn ĐÚNG acc đó, nhưng KHÔNG mở
 * deep link/follow (skipFollow = true) - vì "Làm NV" không phải "Thêm".
 *
 * CHƯA xử lý tự động chuyển sang acc TIẾP THEO trong hàng đợi khi acc hiện tại "xong việc" -
 * vì chưa có hệ thống job thật để biết khi nào 1 acc coi là xong.
 */
fun startJobRunnerOverlay(
    context: Context,
    navController: NavController,
    data: JobRunData,
    accountsQueue: List<JobRunAccount> = emptyList()
) {
    if (!GolikeSession.isLoggedIn.value) {
        Toast.makeText(context, "Cần đăng nhập GoLike trước khi chạy", Toast.LENGTH_LONG).show()
        navController.navigate(Routes.GOLIKE_LOGIN)
        return
    }

    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Cần cấp quyền hiển thị trên ứng dụng khác để dùng tính năng này", Toast.LENGTH_LONG).show()
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Máy không hỗ trợ mở màn cấp quyền này", Toast.LENGTH_SHORT).show()
        }
        return
    }

    if (!isAccessibilityServiceEnabledForJobRunner(context)) {
        Toast.makeText(
            context,
            "Cần bật quyền Trợ năng (Accessibility) cho CayXu để chạy nhiệm vụ",
            Toast.LENGTH_LONG
        ).show()
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Máy không hỗ trợ mở màn cấp quyền này", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val intent = Intent(context, GolikeJobRunnerOverlayService::class.java).apply {
        putExtra(GolikeJobRunnerOverlayService.EXTRA_MODE_LABEL, data.modeLabel)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_ACCOUNT_HANDLE, data.accountHandle)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_ACCOUNT_TASK_COUNT, data.accountTaskCount)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_ACCOUNT_SECONDARY_ID, data.accountSecondaryId)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_JOB_ID, data.jobId)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_JOB_TYPE, data.jobType)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_JOB_PRICE, data.jobPrice)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_JOB_SUCCESS_COUNT, data.jobSuccessCount)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_JOB_FAIL_COUNT, data.jobFailCount)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_JOB_EARNED, data.jobEarned)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_JOB_LINK, data.jobLink)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_INITIAL_STATUS, data.initialStatus)
        putExtra(GolikeJobRunnerOverlayService.EXTRA_ACCOUNT_PACKAGE_NAME, accountsQueue.firstOrNull()?.packageName.orEmpty())
        putExtra(GolikeJobRunnerOverlayService.EXTRA_ACCOUNT_GOLIKE_ID, accountsQueue.firstOrNull()?.golikeAccountId ?: 0L)
    }
    context.startService(intent)

    val firstAccount = accountsQueue.firstOrNull()
    if (firstAccount != null && firstAccount.variant == TikTokAppVariant.STANDARD) {
        // Tái sử dụng NGUYÊN luồng chuyển acc của "Thêm" (không đụng code luồng đó) - chỉ
        // khác ở chỗ skipFollow = true: chuyển đúng acc xong là dừng, không mở deep
        // link/follow gì cả.
        TikTokAppLauncher.launch(context, firstAccount.variant)
        GolikeSwitchAccountBridge.requestSwitch(
            targetHandle = firstAccount.handle,
            followTargetUsername = "",
            packageName = firstAccount.packageName,
            variant = firstAccount.variant,
            skipFollow = true
        )
    } else if (firstAccount != null) {
        // Bản Lite/Studio không có màn "Chuyển đổi tài khoản" - chỉ mở TikTok bình thường.
        TikTokAppLauncher.launch(context, firstAccount.variant)
    }
}

/** Bản sao độc lập của cùng logic kiểm tra Accessibility Service ở TikTokProfileLink.kt -
 *  tách riêng để KHÔNG đụng vào file đó (giữ nguyên luồng "Thêm" như đã yêu cầu trước đây). */
private fun isAccessibilityServiceEnabledForJobRunner(context: Context): Boolean {
    val expected = ComponentName(context, TikTokAccessibilityService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}
