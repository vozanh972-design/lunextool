package com.cayxu.app.ui.screens.golike

import android.content.Context

/**
 * Cấu hình chạy nhiệm vụ Golike (Delay, số lượng job, tự đổi nick khi lỗi).
 */
data class GolikeRunConfig(
    val platform: String = "tiktok",
    val delayMinSeconds: Int = 8,
    val delayMaxSeconds: Int = 15,
    val taskCountTarget: Int = 0, // 0 = Không giới hạn
    val failJobCountToSwitchAccount: Int = 5,
    val autoSwitchOnFail: Boolean = true
)

object GolikeRunConfigStore {
    private const val PREFS_NAME = "golike_run_config_pref"
    private const val KEY_PLATFORM = "golike_platform"
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
            .putInt("${prefix}${KEY_DELAY_MIN}", config.delayMinSeconds)
            .putInt("${prefix}${KEY_DELAY_MAX}", config.delayMaxSeconds)
            .putInt("${prefix}${KEY_TASK_TARGET}", config.taskCountTarget)
            .putInt("${prefix}${KEY_FAIL_SWITCH}", config.failJobCountToSwitchAccount)
            .putBoolean("${prefix}${KEY_AUTO_SWITCH}", config.autoSwitchOnFail)
            .commit()
    }
}
