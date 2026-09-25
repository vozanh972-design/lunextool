package com.cayxu.app.nhiemvucheo

import android.content.Context

/**
 * Cấu hình thực thi cho module Nhiệm Vụ Chéo (NVC).
 * Lưu SharedPreferences riêng biệt (cayxu_nvc_run_config).
 */
data class NvcRunConfig(
    val enabledCategories: List<String> = listOf("reaction"), // reaction, comment, follow
    val selectedReactions: List<String> = listOf("LIKE", "LOVE", "CARE"),
    val claimLimit: Int = 1,
    val delaySeconds: Int = 8,
    val quality: String = "all", // all, normal, good, high
    val taskCountTarget: Int = 0, // 0 = không giới hạn
    val failJobLimit: Int = 5 // dừng nick nếu lỗi liên tiếp
)

object NvcRunConfigStore {
    private const val PREFS_NAME = "cayxu_nvc_run_config"
    private const val KEY_CATEGORIES = "enabled_categories"
    private const val KEY_REACTIONS = "selected_reactions"
    private const val KEY_CLAIM_LIMIT = "claim_limit"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_QUALITY = "quality"
    private const val KEY_TASK_TARGET = "task_count_target"
    private const val KEY_FAIL_LIMIT = "fail_job_limit"

    val supportedCategories = listOf(
        "reaction" to "Thả cảm xúc (Reaction)",
        "comment"  to "Bình luận (Comment)",
        "follow"   to "Theo dõi (Follow)"
    )

    val supportedReactions = listOf(
        "LIKE"  to "Like (Thích)",
        "LOVE"  to "Love (Yêu thích)",
        "CARE"  to "Care (Thương thương)",
        "HAHA"  to "Haha (Cười)",
        "WOW"   to "Wow (Bất ngờ)",
        "SAD"   to "Sad (Buồn)",
        "ANGRY" to "Angry (Phẫn nộ)"
    )

    val supportedQualities = listOf(
        "all"    to "Tự động (all)",
        "normal" to "Thường (normal)",
        "good"   to "Tốt (good)",
        "high"   to "Cao (high)"
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context): NvcRunConfig {
        val p = prefs(context)
        val catStr = p.getString(KEY_CATEGORIES, "reaction") ?: "reaction"
        val reactStr = p.getString(KEY_REACTIONS, "LIKE,LOVE,CARE") ?: "LIKE,LOVE,CARE"
        return NvcRunConfig(
            enabledCategories = catStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("reaction") },
            selectedReactions = reactStr.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }.ifEmpty { listOf("LIKE", "LOVE", "CARE") },
            claimLimit = p.getInt(KEY_CLAIM_LIMIT, 1).coerceIn(1, 5),
            delaySeconds = p.getInt(KEY_DELAY_SECONDS, 8).coerceIn(5, 60),
            quality = p.getString(KEY_QUALITY, "all") ?: "all",
            taskCountTarget = p.getInt(KEY_TASK_TARGET, 0).coerceAtLeast(0),
            failJobLimit = p.getInt(KEY_FAIL_LIMIT, 5).coerceAtLeast(1)
        )
    }

    fun save(context: Context, config: NvcRunConfig) {
        prefs(context).edit()
            .putString(KEY_CATEGORIES, config.enabledCategories.joinToString(","))
            .putString(KEY_REACTIONS, config.selectedReactions.joinToString(","))
            .putInt(KEY_CLAIM_LIMIT, config.claimLimit.coerceIn(1, 5))
            .putInt(KEY_DELAY_SECONDS, config.delaySeconds.coerceIn(5, 60))
            .putString(KEY_QUALITY, config.quality)
            .putInt(KEY_TASK_TARGET, config.taskCountTarget.coerceAtLeast(0))
            .putInt(KEY_FAIL_LIMIT, config.failJobLimit.coerceAtLeast(1))
            .apply()
    }
}
