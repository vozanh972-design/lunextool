package com.cayxu.app.ui.f4f

import android.content.Context
import com.google.gson.Gson

/**
 * Cấu hình chạy nhiệm vụ riêng biệt của Follow4Follow.
 */
data class F4fRunConfig(
    val delaySeconds: Int = 5,
    val delayMinSeconds: Int = 5,
    val delayMaxSeconds: Int = 10,
    val targetTaskCount: Int = 50,
    val enableFollow: Boolean = true,
    val enableLike: Boolean = true,
    val enableComment: Boolean = false,
    val autoSwapOnError: Boolean = true,
    val errorSwapCount: Int = 3,
    val swipeBeforeGetJob: Boolean = false,
)

object F4fRunConfigStore {
    private const val PREFS_NAME = "cayxu_f4f_run_config"
    private const val KEY_CONFIG = "run_config"
    private val gson = Gson()

    fun get(context: Context): F4fRunConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null) ?: return F4fRunConfig()
        return runCatching {
            gson.fromJson(json, F4fRunConfig::class.java) ?: F4fRunConfig()
        }.getOrDefault(F4fRunConfig())
    }

    fun save(context: Context, config: F4fRunConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG, gson.toJson(config)).apply()
    }
}
