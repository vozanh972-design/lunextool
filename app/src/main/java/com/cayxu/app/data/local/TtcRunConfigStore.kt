package com.cayxu.app.data.local

import android.content.Context

data class TtcRunConfig(
    val taskTypes: List<String> = listOf("like", "follow", "comment", "page", "vip"),
    val delaySeconds: Int = 10,
    val taskCountTarget: Int = 50,
    val failJobCountLimit: Int = 5,
    val pairModeEnabled: Boolean = true,
    val pairTargetType: String = "page" // "page" hoặc "profile"
)

object TtcRunConfigStore {
    private const val PREFS_NAME = "cayxu_ttc_run_config"
    private const val KEY_TASK_TYPES = "task_types"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_TASK_COUNT_TARGET = "task_count_target"
    private const val KEY_FAIL_LIMIT = "fail_limit"
    private const val KEY_PAIR_MODE_ENABLED = "pair_mode_enabled"
    private const val KEY_PAIR_TARGET_TYPE = "pair_target_type"

    val fbTaskTypes = listOf(
        "like" to "Cảm xúc (Like / Love)",
        "follow" to "Theo dõi (Follow)",
        "comment" to "Bình luận (Comment)",
        "page" to "Like Page",
        "member" to "Tham gia nhóm",
        "vip" to "Nhiệm vụ VIP"
    )

    fun getConfig(context: Context): TtcRunConfig {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val typesStr = sp.getString(KEY_TASK_TYPES, null)
        val types = if (typesStr != null) {
            typesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf("like", "follow", "comment", "page", "vip")
        }
        val delay = sp.getInt(KEY_DELAY_SECONDS, 10)
        val target = sp.getInt(KEY_TASK_COUNT_TARGET, 50)
        val failLimit = sp.getInt(KEY_FAIL_LIMIT, 5)
        val pairEnabled = sp.getBoolean(KEY_PAIR_MODE_ENABLED, true)
        val pairType = sp.getString(KEY_PAIR_TARGET_TYPE, "page") ?: "page"

        return TtcRunConfig(
            taskTypes = types,
            delaySeconds = delay,
            taskCountTarget = target,
            failJobCountLimit = failLimit,
            pairModeEnabled = pairEnabled,
            pairTargetType = pairType
        )
    }

    fun saveConfig(context: Context, config: TtcRunConfig) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_TASK_TYPES, config.taskTypes.joinToString(","))
            .putInt(KEY_DELAY_SECONDS, config.delaySeconds)
            .putInt(KEY_TASK_COUNT_TARGET, config.taskCountTarget)
            .putInt(KEY_FAIL_LIMIT, config.failJobCountLimit)
            .putBoolean(KEY_PAIR_MODE_ENABLED, config.pairModeEnabled)
            .putString(KEY_PAIR_TARGET_TYPE, config.pairTargetType)
            .apply()
    }
}
