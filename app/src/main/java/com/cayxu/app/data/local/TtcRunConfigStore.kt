package com.cayxu.app.data.local

import android.content.Context

data class TtcRunConfig(
    val taskTypes: List<String> = listOf("like", "follow", "comment", "page"),
    val delaySeconds: Int = 10,
    val taskCountTarget: Int = 50,
    val failJobCountLimit: Int = 5
)

object TtcRunConfigStore {
    private const val PREFS_NAME = "cayxu_ttc_run_config"
    private const val KEY_TASK_TYPES = "task_types"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_TASK_COUNT_TARGET = "task_count_target"
    private const val KEY_FAIL_LIMIT = "fail_limit"

    val fbTaskTypes = listOf(
        "like" to "Cảm xúc (Like / Love)",
        "follow" to "Theo dõi (Follow)",
        "comment" to "Bình luận (Comment)",
        "page" to "Like Page",
        "member" to "Tham gia nhóm"
    )

    fun getConfig(context: Context): TtcRunConfig {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val typesStr = sp.getString(KEY_TASK_TYPES, null)
        val types = if (typesStr != null) {
            typesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf("like", "follow", "comment", "page")
        }
        val delay = sp.getInt(KEY_DELAY_SECONDS, 10)
        val target = sp.getInt(KEY_TASK_COUNT_TARGET, 50)
        val failLimit = sp.getInt(KEY_FAIL_LIMIT, 5)
        return TtcRunConfig(
            taskTypes = types,
            delaySeconds = delay,
            taskCountTarget = target,
            failJobCountLimit = failLimit
        )
    }

    fun saveConfig(context: Context, config: TtcRunConfig) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_TASK_TYPES, config.taskTypes.joinToString(","))
            .putInt(KEY_DELAY_SECONDS, config.delaySeconds)
            .putInt(KEY_TASK_COUNT_TARGET, config.taskCountTarget)
            .putInt(KEY_FAIL_LIMIT, config.failJobCountLimit)
            .apply()
    }
}
