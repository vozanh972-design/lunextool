package com.cayxu.app.ui.crossttc

import android.content.Context
import android.content.SharedPreferences

/*
 * LƯU TRỮ CẤU HÌNH NHIỆM VỤ TIKTOK TƯƠNG TÁC CHÉO (TIKTOK TTC)
 * Độc lập 100%, lưu vào SharedPreferences riêng "cross_ttc_run_config".
 */

data class CrossTTCRunConfig(
    val jobTypeFollowNormal: Boolean = true,
    val jobTypeFollowVip: Boolean = true,
    val getJobMinSeconds: Int = 5,
    val getJobMaxSeconds: Int = 12,
    val swipeBeforeGetJob: Boolean = true,
    val doJobMinSeconds: Int = 8,
    val doJobMaxSeconds: Int = 15,
    val swipeAfterFollow: Boolean = true,
    val completeJobMinSeconds: Int = 20,
    val completeJobMaxSeconds: Int = 40,
    val breakMinSeconds: Int = 8,
    val breakMaxSeconds: Int = 15,
    val goalTaskCount: Int = 0,
    val autoSwapOnError: Boolean = true,
    val errorSwapCount: Int = 5,
)

object CrossTTCRunConfigStore {
    private const val PREFS_NAME = "cross_ttc_run_config"

    private const val KEY_JOB_TYPE_FOLLOW_NORMAL = "job_type_follow_normal"
    private const val KEY_JOB_TYPE_FOLLOW_VIP = "job_type_follow_vip"
    private const val KEY_GET_JOB_MIN = "get_job_min"
    private const val KEY_GET_JOB_MAX = "get_job_max"
    private const val KEY_SWIPE_BEFORE_GET_JOB = "swipe_before_get_job"
    private const val KEY_DO_JOB_MIN = "do_job_min"
    private const val KEY_DO_JOB_MAX = "do_job_max"
    private const val KEY_SWIPE_AFTER_FOLLOW = "swipe_after_follow"
    private const val KEY_COMPLETE_JOB_MIN = "complete_job_min"
    private const val KEY_COMPLETE_JOB_MAX = "complete_job_max"
    private const val KEY_BREAK_MIN = "break_min"
    private const val KEY_BREAK_MAX = "break_max"
    private const val KEY_GOAL_TASK_COUNT = "goal_task_count"
    private const val KEY_AUTO_SWAP_ON_ERROR = "auto_swap_on_error"
    private const val KEY_ERROR_SWAP_COUNT = "error_swap_count"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context): CrossTTCRunConfig {
        val p = getPrefs(context)
        return CrossTTCRunConfig(
            jobTypeFollowNormal = p.getBoolean(KEY_JOB_TYPE_FOLLOW_NORMAL, true),
            jobTypeFollowVip = p.getBoolean(KEY_JOB_TYPE_FOLLOW_VIP, true),
            getJobMinSeconds = p.getInt(KEY_GET_JOB_MIN, 5),
            getJobMaxSeconds = p.getInt(KEY_GET_JOB_MAX, 12),
            swipeBeforeGetJob = p.getBoolean(KEY_SWIPE_BEFORE_GET_JOB, true),
            doJobMinSeconds = p.getInt(KEY_DO_JOB_MIN, 8),
            doJobMaxSeconds = p.getInt(KEY_DO_JOB_MAX, 15),
            swipeAfterFollow = p.getBoolean(KEY_SWIPE_AFTER_FOLLOW, true),
            completeJobMinSeconds = p.getInt(KEY_COMPLETE_JOB_MIN, 20),
            completeJobMaxSeconds = p.getInt(KEY_COMPLETE_JOB_MAX, 40),
            breakMinSeconds = p.getInt(KEY_BREAK_MIN, 8),
            breakMaxSeconds = p.getInt(KEY_BREAK_MAX, 15),
            goalTaskCount = p.getInt(KEY_GOAL_TASK_COUNT, 0),
            autoSwapOnError = p.getBoolean(KEY_AUTO_SWAP_ON_ERROR, true),
            errorSwapCount = p.getInt(KEY_ERROR_SWAP_COUNT, 5),
        )
    }

    fun save(context: Context, config: CrossTTCRunConfig) {
        getPrefs(context).edit()
            .putBoolean(KEY_JOB_TYPE_FOLLOW_NORMAL, config.jobTypeFollowNormal)
            .putBoolean(KEY_JOB_TYPE_FOLLOW_VIP, config.jobTypeFollowVip)
            .putInt(KEY_GET_JOB_MIN, config.getJobMinSeconds)
            .putInt(KEY_GET_JOB_MAX, config.getJobMaxSeconds)
            .putBoolean(KEY_SWIPE_BEFORE_GET_JOB, config.swipeBeforeGetJob)
            .putInt(KEY_DO_JOB_MIN, config.doJobMinSeconds)
            .putInt(KEY_DO_JOB_MAX, config.doJobMaxSeconds)
            .putBoolean(KEY_SWIPE_AFTER_FOLLOW, config.swipeAfterFollow)
            .putInt(KEY_COMPLETE_JOB_MIN, config.completeJobMinSeconds)
            .putInt(KEY_COMPLETE_JOB_MAX, config.completeJobMaxSeconds)
            .putInt(KEY_BREAK_MIN, config.breakMinSeconds)
            .putInt(KEY_BREAK_MAX, config.breakMaxSeconds)
            .putInt(KEY_GOAL_TASK_COUNT, config.goalTaskCount)
            .putBoolean(KEY_AUTO_SWAP_ON_ERROR, config.autoSwapOnError)
            .putInt(KEY_ERROR_SWAP_COUNT, config.errorSwapCount)
            .apply()
    }
}
