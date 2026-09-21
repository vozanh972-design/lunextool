package com.cayxu.app.data.local

import android.content.Context

data class TtcRunConfig(
    val taskTypes: List<String> = listOf("likevip", "like", "cxvip", "cx", "cmt", "sub", "subvip", "page"),
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
        "likevip" to "Like chéo VIP",
        "like" to "Like chéo",
        "cxvip" to "Cảm xúc chéo VIP",
        "cx" to "Cảm xúc chéo thường",
        "cxcmt" to "Cảm xúc chéo bình luận",
        "cmt" to "Bình luận chéo",
        "sub" to "Theo dõi chéo",
        "subvip" to "Theo dõi chéo vip",
        "share" to "Share chéo",
        "sharend" to "Share chéo kèm nội dung",
        "page" to "Like page chéo",
        "member" to "Tham gia nhóm chéo",
        "danhgia" to "Đánh giá page chéo"
    )

    fun getConfig(context: Context): TtcRunConfig {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val typesStr = sp.getString(KEY_TASK_TYPES, null)
        val types = if (typesStr != null) {
            var rawList = typesStr.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            // Tự động map các key cũ sang key mới chuẩn TTC
            rawList = rawList.map { k ->
                when (k) {
                    "follow" -> "sub"
                    "comment" -> "cmt"
                    "cmtvip" -> "cmt"
                    else -> k
                }
            }.distinct()
            val validKeys = fbTaskTypes.map { it.first }
            rawList.filter { it in validKeys }.ifEmpty {
                listOf("likevip", "like", "cxvip", "cx", "cmt", "sub", "subvip", "page")
            }
        } else {
            listOf("likevip", "like", "cxvip", "cx", "cmt", "sub", "subvip", "page")
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
