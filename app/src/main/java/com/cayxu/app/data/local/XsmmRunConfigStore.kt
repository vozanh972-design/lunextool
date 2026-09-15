package com.cayxu.app.data.local

import android.content.Context

/** Cấu hình "Chạy" cho XSMM - lưu SharedPreferences riêng (cayxu_xsmm_run_config). */
data class XsmmRunConfig(
    /** Nền tảng (mặc định: tiktok) */
    val platform: String = "tiktok",
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
    private const val KEY_PLATFORM = "platform"
    private const val KEY_TASK_TYPE = "task_type"
    private const val KEY_FETCH_INTERVAL = "fetch_task_interval_seconds"
    private const val KEY_DO_DURATION = "do_task_duration_seconds"
    private const val KEY_TASK_COUNT_TARGET = "task_count_target"
    private const val KEY_STOP_AFTER_NO_TASK = "stop_after_no_task_count"
    private const val KEY_STOP_AFTER_COMPLETED = "stop_after_completed_count"
    private const val KEY_SWIPE_BEFORE = "swipe_before_task"
    private const val KEY_RETURN_HOME_SWIPE = "return_home_and_swipe"

    val supportedPlatforms = listOf(
        "tiktok" to "TikTok",
        "facebook" to "Facebook",
        "instagram" to "Instagram"
    )

    val tiktokTaskTypes = listOf(
        "tiktok_follow" to "TikTok Follow (Theo dõi)",
        "tiktok_like" to "TikTok Like (Thả tim)",
        "tiktok_comment" to "TikTok Comment (Bình luận)"
    )

    val facebookTaskTypes = listOf(
        "facebook_follow" to "Facebook Follow (Theo dõi)",
        "facebook_like" to "Facebook Like (Thích bài viết)",
        "facebook_comment" to "Facebook Comment (Bình luận)",
        "facebook_share" to "Facebook Share (Chia sẻ)",
        "facebook_likepage" to "Facebook Like Fanpage",
        "facebook_member" to "Facebook Tham gia nhóm",
        "facebook_likecmt" to "Facebook Like Comment",
        "facebook_review" to "Facebook Đánh giá"
    )

    val instagramTaskTypes = listOf(
        "instagram_random" to "Ngẫu nhiên (Ưu tiên Follow, hết job chuyển Like)",
        "instagram_follow" to "Chỉ Follow (Theo dõi)",
        "instagram_like" to "Chỉ Like (Thích bài viết)"
    )

    fun taskTypesFor(platform: String): List<Pair<String, String>> = when (platform.lowercase()) {
        "facebook" -> facebookTaskTypes
        "instagram" -> instagramTaskTypes
        else -> tiktokTaskTypes
    }

    val supportedTaskTypes: List<Pair<String, String>>
        get() = tiktokTaskTypes

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setActivePlatform(context: Context, platform: String) {
        prefs(context).edit().putString(KEY_PLATFORM, platform).apply()
    }

    fun get(context: Context, platform: String? = null): XsmmRunConfig {
        val p = prefs(context)
        val selectedPlatform = (platform ?: p.getString(KEY_PLATFORM, "tiktok") ?: "tiktok").lowercase()
        val prefix = if (selectedPlatform != "tiktok") "${selectedPlatform}_" else ""
        return XsmmRunConfig(
            platform = selectedPlatform,
            taskType = p.getString("${prefix}${KEY_TASK_TYPE}", if (selectedPlatform == "instagram") "instagram_random" else "tiktok_follow") ?: "tiktok_follow",
            fetchTaskIntervalSeconds = p.getInt("${prefix}${KEY_FETCH_INTERVAL}", p.getInt(KEY_FETCH_INTERVAL, 10)),
            doTaskDurationSeconds = p.getInt("${prefix}${KEY_DO_DURATION}", p.getInt(KEY_DO_DURATION, 10)),
            taskCountTarget = p.getInt("${prefix}${KEY_TASK_COUNT_TARGET}", p.getInt(KEY_TASK_COUNT_TARGET, 0)),
            stopAfterNoTaskCount = p.getInt("${prefix}${KEY_STOP_AFTER_NO_TASK}", p.getInt(KEY_STOP_AFTER_NO_TASK, 100)),
            stopAfterCompletedCount = p.getInt("${prefix}${KEY_STOP_AFTER_COMPLETED}", p.getInt(KEY_STOP_AFTER_COMPLETED, 100)),
            swipeBeforeTask = p.getBoolean("${prefix}${KEY_SWIPE_BEFORE}", p.getBoolean(KEY_SWIPE_BEFORE, false)),
            returnHomeAndSwipe = p.getBoolean("${prefix}${KEY_RETURN_HOME_SWIPE}", p.getBoolean(KEY_RETURN_HOME_SWIPE, false))
        )
    }

    fun save(context: Context, config: XsmmRunConfig) {
        val p = prefs(context)
        val selectedPlatform = config.platform.lowercase()
        val prefix = if (selectedPlatform != "tiktok") "${selectedPlatform}_" else ""
        p.edit()
            .putString(KEY_PLATFORM, selectedPlatform)
            .putString("${prefix}${KEY_TASK_TYPE}", config.taskType)
            .putInt("${prefix}${KEY_FETCH_INTERVAL}", config.fetchTaskIntervalSeconds)
            .putInt("${prefix}${KEY_DO_DURATION}", config.doTaskDurationSeconds)
            .putInt("${prefix}${KEY_TASK_COUNT_TARGET}", config.taskCountTarget)
            .putInt("${prefix}${KEY_STOP_AFTER_NO_TASK}", config.stopAfterNoTaskCount)
            .putInt("${prefix}${KEY_STOP_AFTER_COMPLETED}", config.stopAfterCompletedCount)
            .putBoolean("${prefix}${KEY_SWIPE_BEFORE}", config.swipeBeforeTask)
            .putBoolean("${prefix}${KEY_RETURN_HOME_SWIPE}", config.returnHomeAndSwipe)
            .apply()
    }
}
