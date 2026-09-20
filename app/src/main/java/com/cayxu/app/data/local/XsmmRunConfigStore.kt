package com.cayxu.app.data.local

import android.content.Context

/** Cấu hình "Chạy" cho XSMM - lưu SharedPreferences riêng (cayxu_xsmm_run_config). */
data class XsmmRunConfig(
    /** Nền tảng (mặc định: tiktok) */
    val platform: String = "tiktok",
    /** Loại nhiệm vụ đơn (mặc định: tiktok_follow) — giữ để tương thích ngược */
    val taskType: String = "tiktok_follow",
    /** Danh sách loại nhiệm vụ được chọn (multi-select). Nếu trống thì dùng taskType. */
    val taskTypes: List<String> = emptyList(),
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
    /** Số job lỗi hoặc bị nhả sẽ tự động đổi sang nick tiếp theo (mặc định: 50). */
    val failJobCountToSwitchAccount: Int = 50,
    /** Lướt (vuốt) một chút trước khi làm nhiệm vụ. */
    val swipeBeforeTask: Boolean = false,
    /** Trở về Home rồi lướt sau khi làm xong (giữa các nhiệm vụ). */
    val returnHomeAndSwipe: Boolean = false
) {
    /** Trả về danh sách loại nhiệm vụ hiệu lực (ưu tiên taskTypes, fallback taskType). */
    fun effectiveTaskTypes(): List<String> =
        taskTypes.filter { it.isNotBlank() }.ifEmpty { listOf(taskType).filter { it.isNotBlank() } }
}

object XsmmRunConfigStore {
    private const val PREFS_NAME = "cayxu_xsmm_run_config"
    private const val KEY_PLATFORM = "platform"
    private const val KEY_TASK_TYPE = "task_type"
    private const val KEY_TASK_TYPES = "task_types"   // comma-separated
    private const val KEY_FETCH_INTERVAL = "fetch_task_interval_seconds"
    private const val KEY_DO_DURATION = "do_task_duration_seconds"
    private const val KEY_TASK_COUNT_TARGET = "task_count_target"
    private const val KEY_STOP_AFTER_NO_TASK = "stop_after_no_task_count"
    private const val KEY_STOP_AFTER_COMPLETED = "stop_after_completed_count"
    private const val KEY_FAIL_JOB_COUNT_TO_SWITCH = "fail_job_count_to_switch"
    private const val KEY_SWIPE_BEFORE = "swipe_before_task"
    private const val KEY_RETURN_HOME_SWIPE = "return_home_and_swipe"

    val supportedPlatforms = listOf(
        "tiktok"    to "TikTok",
        "facebook"  to "Facebook",
        "instagram" to "Instagram"
    )

    val tiktokTaskTypes = listOf(
        "tiktok_follow"  to "TikTok Follow (Theo dõi)",
        "tiktok_like"    to "TikTok Like (Thả tim)",
        "tiktok_comment" to "TikTok Comment (Bình luận)"
    )

    val facebookTaskTypes = listOf(
        "facebook_like"     to "Cảm xúc Facebook (Like / Love / Care / Haha / Wow / Sad / Angry)",
        "facebook_follow"   to "Theo dõi Facebook",
        "facebook_comment"  to "Comment Facebook (Bình luận)",
        "facebook_share"    to "Share Facebook (Chia sẻ)",
        "facebook_likepage" to "Like Page Facebook",
        "facebook_member"   to "Tham gia nhóm Facebook",
        "facebook_likecmt"  to "Cảm xúc comment Facebook",
        "facebook_review"   to "Đánh giá Facebook"
    )

    val instagramTaskTypes = listOf(
        "instagram_random"  to "Ngẫu nhiên (Tym / Follow / Comment)",
        "instagram_follow"  to "Chỉ Follow (Theo dõi)",
        "instagram_like"    to "Chỉ Tym / Like (Thích bài viết)",
        "instagram_comment" to "Chỉ Comment (Bình luận)"
    )

    fun taskTypesFor(platform: String): List<Pair<String, String>> = when (platform.lowercase()) {
        "facebook"  -> facebookTaskTypes
        "instagram" -> instagramTaskTypes
        else        -> tiktokTaskTypes
    }

    fun defaultTaskTypeFor(platform: String): String = when (platform.lowercase()) {
        "instagram" -> "instagram_random"
        "facebook"  -> "facebook_like"
        else        -> "tiktok_follow"
    }

    private fun prefixFor(platform: String): String = when (platform.lowercase()) {
        "instagram" -> "instagram_"
        "facebook"  -> "facebook_"
        else        -> "tiktok_"
    }

    val supportedTaskTypes: List<Pair<String, String>>
        get() = tiktokTaskTypes

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActivePlatform(context: Context): String =
        prefs(context).getString(KEY_PLATFORM, "tiktok") ?: "tiktok"

    fun setActivePlatform(context: Context, platform: String) {
        prefs(context).edit().putString(KEY_PLATFORM, platform).apply()
    }

    fun get(context: Context, platform: String? = null): XsmmRunConfig {
        val p = prefs(context)
        val selectedPlatform = (platform ?: p.getString(KEY_PLATFORM, "tiktok") ?: "tiktok").lowercase()
        val prefix = prefixFor(selectedPlatform)

        val validTypes = taskTypesFor(selectedPlatform).map { it.first }
        val rawTaskType = p.getString("${prefix}${KEY_TASK_TYPE}", null)
            ?: if (selectedPlatform == "tiktok") p.getString(KEY_TASK_TYPE, null) else null
        val safeTaskType = if (rawTaskType != null && rawTaskType in validTypes) rawTaskType
                           else defaultTaskTypeFor(selectedPlatform)

        // Đọc taskTypes (comma-separated)
        val rawTaskTypes = p.getString("${prefix}${KEY_TASK_TYPES}", "")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it in validTypes }
            ?: emptyList()

        return XsmmRunConfig(
            platform = selectedPlatform,
            taskType = safeTaskType,
            taskTypes = rawTaskTypes,
            fetchTaskIntervalSeconds = p.getInt("${prefix}${KEY_FETCH_INTERVAL}", p.getInt(KEY_FETCH_INTERVAL, 10)),
            doTaskDurationSeconds = p.getInt("${prefix}${KEY_DO_DURATION}", p.getInt(KEY_DO_DURATION, 10)),
            taskCountTarget = p.getInt("${prefix}${KEY_TASK_COUNT_TARGET}", p.getInt(KEY_TASK_COUNT_TARGET, 0)),
            stopAfterNoTaskCount = p.getInt("${prefix}${KEY_STOP_AFTER_NO_TASK}", p.getInt(KEY_STOP_AFTER_NO_TASK, 100)),
            stopAfterCompletedCount = p.getInt("${prefix}${KEY_STOP_AFTER_COMPLETED}", p.getInt(KEY_STOP_AFTER_COMPLETED, 100)),
            failJobCountToSwitchAccount = p.getInt("${prefix}${KEY_FAIL_JOB_COUNT_TO_SWITCH}", p.getInt(KEY_FAIL_JOB_COUNT_TO_SWITCH, 50)),
            swipeBeforeTask = p.getBoolean("${prefix}${KEY_SWIPE_BEFORE}", p.getBoolean(KEY_SWIPE_BEFORE, false)),
            returnHomeAndSwipe = p.getBoolean("${prefix}${KEY_RETURN_HOME_SWIPE}", p.getBoolean(KEY_RETURN_HOME_SWIPE, false))
        )
    }

    fun save(context: Context, config: XsmmRunConfig) {
        val p = prefs(context)
        val selectedPlatform = config.platform.lowercase()
        val prefix = prefixFor(selectedPlatform)
        val validTypes = taskTypesFor(selectedPlatform).map { it.first }
        val safeTaskType = if (config.taskType in validTypes) config.taskType else defaultTaskTypeFor(selectedPlatform)
        val safeTaskTypes = config.taskTypes.filter { it in validTypes }

        p.edit()
            .putString(KEY_PLATFORM, selectedPlatform)
            .putString("${prefix}${KEY_TASK_TYPE}", safeTaskType)
            .putString("${prefix}${KEY_TASK_TYPES}", safeTaskTypes.joinToString(","))
            .putInt("${prefix}${KEY_FETCH_INTERVAL}", config.fetchTaskIntervalSeconds)
            .putInt("${prefix}${KEY_DO_DURATION}", config.doTaskDurationSeconds)
            .putInt("${prefix}${KEY_TASK_COUNT_TARGET}", config.taskCountTarget)
            .putInt("${prefix}${KEY_STOP_AFTER_NO_TASK}", config.stopAfterNoTaskCount)
            .putInt("${prefix}${KEY_STOP_AFTER_COMPLETED}", config.stopAfterCompletedCount)
            .putInt("${prefix}${KEY_FAIL_JOB_COUNT_TO_SWITCH}", config.failJobCountToSwitchAccount)
            .putBoolean("${prefix}${KEY_SWIPE_BEFORE}", config.swipeBeforeTask)
            .putBoolean("${prefix}${KEY_RETURN_HOME_SWIPE}", config.returnHomeAndSwipe)
            .apply()
    }
}
