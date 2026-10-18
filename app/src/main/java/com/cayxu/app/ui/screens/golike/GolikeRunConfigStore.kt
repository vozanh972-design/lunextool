package com.cayxu.app.ui.screens.golike

import android.content.Context

/**
 * Cấu hình chạy nhiệm vụ Golike chi tiết:
 * 1. getJobMinSeconds, getJobMaxSeconds: Thời gian lấy job (giây)
 *    swipeBeforeGetJob: Lướt trước khi làm (bật/tắt)
 * 2. doJobMinSeconds, doJobMaxSeconds: Thời gian làm job (giây)
 *    swipeAfterFollow: Follow xong lướt tin (bật/tắt)
 * 3. completeJobMinSeconds, completeJobMaxSeconds: Thời gian hoàn thành job (giây)
 * 4. delayMinSeconds, delayMaxSeconds: Thời gian nghỉ giữa các job (giây)
 * 5. taskCountTarget: Số lượng nhiệm vụ tối đa (0 = không giới hạn)
 * 6. autoSwitchOnFail, failJobCountToSwitchAccount: Tự động đổi nick khi lỗi
 */
data class GolikeRunConfig(
    val platform: String = "tiktok",
    val getJobMinSeconds: Int = 5,
    val getJobMaxSeconds: Int = 12,
    val swipeBeforeGetJob: Boolean = true,
    val doJobMinSeconds: Int = 8,
    val doJobMaxSeconds: Int = 15,
    val swipeAfterFollow: Boolean = true,
    val completeJobMinSeconds: Int = 20,
    val completeJobMaxSeconds: Int = 40,
    val delayMinSeconds: Int = 8,
    val delayMaxSeconds: Int = 15,
    val taskCountTarget: Int = 0, // 0 = Không giới hạn
    val failJobCountToSwitchAccount: Int = 5,
    val autoSwitchOnFail: Boolean = true
)

object GolikeRunConfigStore {
    private const val PREFS_NAME = "golike_run_config_pref"
    private const val KEY_PLATFORM = "golike_platform"
    private const val KEY_GET_JOB_MIN = "golike_get_job_min"
    private const val KEY_GET_JOB_MAX = "golike_get_job_max"
    private const val KEY_SWIPE_BEFORE_GET = "golike_swipe_before_get"
    private const val KEY_DO_JOB_MIN = "golike_do_job_min"
    private const val KEY_DO_JOB_MAX = "golike_do_job_max"
    private const val KEY_SWIPE_AFTER_FOLLOW = "golike_swipe_after_follow"
    private const val KEY_COMPLETE_JOB_MIN = "golike_complete_job_min"
    private const val KEY_COMPLETE_JOB_MAX = "golike_complete_job_max"
    private const val KEY_DELAY_MIN = "golike_delay_min"
    private const val KEY_DELAY_MAX = "golike_delay_max"
    private const val KEY_TASK_TARGET = "golike_task_target"
    private const val KEY_FAIL_SWITCH = "golike_fail_switch"
    private const val KEY_AUTO_SWITCH = "golike_auto_switch"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context, platform: String = "tiktok"): GolikeRunConfig {
        val p = prefs(context)
        val prefix = "${platform.lowercase()}_"
        return GolikeRunConfig(
            platform = platform.lowercase(),
            getJobMinSeconds = p.getInt("${prefix}${KEY_GET_JOB_MIN}", p.getInt(KEY_GET_JOB_MIN, 5)),
            getJobMaxSeconds = p.getInt("${prefix}${KEY_GET_JOB_MAX}", p.getInt(KEY_GET_JOB_MAX, 12)),
            swipeBeforeGetJob = p.getBoolean("${prefix}${KEY_SWIPE_BEFORE_GET}", p.getBoolean(KEY_SWIPE_BEFORE_GET, true)),
            doJobMinSeconds = p.getInt("${prefix}${KEY_DO_JOB_MIN}", p.getInt(KEY_DO_JOB_MIN, 8)),
            doJobMaxSeconds = p.getInt("${prefix}${KEY_DO_JOB_MAX}", p.getInt(KEY_DO_JOB_MAX, 15)),
            swipeAfterFollow = p.getBoolean("${prefix}${KEY_SWIPE_AFTER_FOLLOW}", p.getBoolean(KEY_SWIPE_AFTER_FOLLOW, true)),
            completeJobMinSeconds = p.getInt("${prefix}${KEY_COMPLETE_JOB_MIN}", p.getInt(KEY_COMPLETE_JOB_MIN, 20)),
            completeJobMaxSeconds = p.getInt("${prefix}${KEY_COMPLETE_JOB_MAX}", p.getInt(KEY_COMPLETE_JOB_MAX, 40)),
            delayMinSeconds = p.getInt("${prefix}${KEY_DELAY_MIN}", p.getInt(KEY_DELAY_MIN, 8)),
            delayMaxSeconds = p.getInt("${prefix}${KEY_DELAY_MAX}", p.getInt(KEY_DELAY_MAX, 15)),
            taskCountTarget = p.getInt("${prefix}${KEY_TASK_TARGET}", p.getInt(KEY_TASK_TARGET, 0)),
            failJobCountToSwitchAccount = p.getInt("${prefix}${KEY_FAIL_SWITCH}", p.getInt(KEY_FAIL_SWITCH, 5)),
            autoSwitchOnFail = p.getBoolean("${prefix}${KEY_AUTO_SWITCH}", p.getBoolean(KEY_AUTO_SWITCH, true))
        )
    }

    fun save(context: Context, config: GolikeRunConfig) {
        val p = prefs(context)
        val prefix = "${config.platform.lowercase()}_"
        p.edit()
            .putString(KEY_PLATFORM, config.platform.lowercase())
            .putInt("${prefix}${KEY_GET_JOB_MIN}", config.getJobMinSeconds)
            .putInt("${prefix}${KEY_GET_JOB_MAX}", config.getJobMaxSeconds)
            .putBoolean("${prefix}${KEY_SWIPE_BEFORE_GET}", config.swipeBeforeGetJob)
            .putInt("${prefix}${KEY_DO_JOB_MIN}", config.doJobMinSeconds)
            .putInt("${prefix}${KEY_DO_JOB_MAX}", config.doJobMaxSeconds)
            .putBoolean("${prefix}${KEY_SWIPE_AFTER_FOLLOW}", config.swipeAfterFollow)
            .putInt("${prefix}${KEY_COMPLETE_JOB_MIN}", config.completeJobMinSeconds)
            .putInt("${prefix}${KEY_COMPLETE_JOB_MAX}", config.completeJobMaxSeconds)
            .putInt("${prefix}${KEY_DELAY_MIN}", config.delayMinSeconds)
            .putInt("${prefix}${KEY_DELAY_MAX}", config.delayMaxSeconds)
            .putInt("${prefix}${KEY_TASK_TARGET}", config.taskCountTarget)
            .putInt("${prefix}${KEY_FAIL_SWITCH}", config.failJobCountToSwitchAccount)
            .putBoolean("${prefix}${KEY_AUTO_SWITCH}", config.autoSwitchOnFail)
            .apply()
    }
}
