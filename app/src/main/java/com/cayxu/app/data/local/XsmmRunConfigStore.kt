package com.cayxu.app.data.local

import android.content.Context

/** Cấu hình "Chạy" cho XSMM - lưu SharedPreferences riêng (cayxu_xsmm_run_config). */
data class XsmmRunConfig(
    /** Loại nhiệm vụ (mặc định: tiktok_follow) */
    val taskType: String = "tiktok_follow",
    /** Thời gian giữa các lần lấy nhiệm vụ (giây). */
    val fetchTaskIntervalSeconds: Int = 10,
    /** Thời gian "làm" 1 nhiệm vụ trước khi báo hoàn thành (giây). */
    val doTaskDurationSeconds: Int = 10,
    /** Số nhiệm vụ muốn làm trong phiên chạy này (0 = không giới hạn). */
    val taskCountTarget: Int = 0,
    /** Số lần liên tiếp "hết nhiệm vụ" (không lấy được job nào) thì tự dừng. */
    val stopAfterNoTaskCount: Int = 100,
    /** Số lần hoàn thành nhiệm vụ thì tự dừng. */
    val stopAfterCompletedCount: Int = 100,
    /** Lướt (vuốt) một chút trước khi làm nhiệm vụ. */
    val swipeBeforeTask: Boolean = false,
    /** Trở về Home rồi lướt sau khi làm xong (giữa các nhiệm vụ). */
    val returnHomeAndSwipe: Boolean = false
)

object XsmmRunConfigStore {
    private const val PREFS_NAME = "cayxu_xsmm_run_config"
    private const val KEY_TASK_TYPE = "task_type"
    private const val KEY_FETCH_INTERVAL = "fetch_task_interval_seconds"
    private const val KEY_DO_DURATION = "do_task_duration_seconds"
    private const val KEY_TASK_COUNT_TARGET = "task_count_target"
    private const val KEY_STOP_AFTER_NO_TASK = "stop_after_no_task_count"
    private const val KEY_STOP_AFTER_COMPLETED = "stop_after_completed_count"
    private const val KEY_SWIPE_BEFORE = "swipe_before_task"
    private const val KEY_RETURN_HOME_SWIPE = "return_home_and_swipe"

    val supportedTaskTypes = listOf(
        "tiktok_follow" to "TikTok Follow",
        "tiktok_like" to "TikTok Like (Thả tim)",
        "tiktok_comment" to "TikTok Comment",
        "facebook_like" to "Facebook Like",
        "facebook_follow" to "Facebook Follow",
        "facebook_comment" to "Facebook Comment",
        "facebook_share" to "Facebook Share",
        "facebook_likepage" to "Facebook Like Page",
        "facebook_member" to "Facebook Tham gia nhóm",
        "facebook_likecmt" to "Facebook Like Comment",
        "facebook_review" to "Facebook Đánh giá",
        "instagram_follow" to "Instagram Follow",
        "instagram_like" to "Instagram Like",
        "instagram_comment" to "Instagram Comment",
        "thread_follow" to "Threads Follow",
        "thread_like" to "Threads Like",
        "youtube_follow" to "YouTube Subscribe",
        "youtube_comment" to "YouTube Comment",
        "google_review" to "Google Review"
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context): XsmmRunConfig {
        val p = prefs(context)
        return XsmmRunConfig(
            taskType = p.getString(KEY_TASK_TYPE, "tiktok_follow") ?: "tiktok_follow",
            fetchTaskIntervalSeconds = p.getInt(KEY_FETCH_INTERVAL, 10),
            doTaskDurationSeconds = p.getInt(KEY_DO_DURATION, 10),
            taskCountTarget = p.getInt(KEY_TASK_COUNT_TARGET, 0),
            stopAfterNoTaskCount = p.getInt(KEY_STOP_AFTER_NO_TASK, 100),
            stopAfterCompletedCount = p.getInt(KEY_STOP_AFTER_COMPLETED, 100),
            swipeBeforeTask = p.getBoolean(KEY_SWIPE_BEFORE, false),
            returnHomeAndSwipe = p.getBoolean(KEY_RETURN_HOME_SWIPE, false)
        )
    }

    fun save(context: Context, config: XsmmRunConfig) {
        prefs(context).edit()
            .putString(KEY_TASK_TYPE, config.taskType)
            .putInt(KEY_FETCH_INTERVAL, config.fetchTaskIntervalSeconds)
            .putInt(KEY_DO_DURATION, config.doTaskDurationSeconds)
            .putInt(KEY_TASK_COUNT_TARGET, config.taskCountTarget)
            .putInt(KEY_STOP_AFTER_NO_TASK, config.stopAfterNoTaskCount)
            .putInt(KEY_STOP_AFTER_COMPLETED, config.stopAfterCompletedCount)
            .putBoolean(KEY_SWIPE_BEFORE, config.swipeBeforeTask)
            .putBoolean(KEY_RETURN_HOME_SWIPE, config.returnHomeAndSwipe)
            .apply()
    }
}
