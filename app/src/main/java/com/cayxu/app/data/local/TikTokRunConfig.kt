package com.cayxu.app.data.local

import android.content.Context

/**
 * Cấu hình hoạt động (màn "Cấu hình hoạt động" mở từ nút "Cấu hình chạy" ở Golike
 * TikTok) - lưu RIÊNG theo từng loại app (TikTok/TikTok Lite/TikTok Studio), vì mỗi
 * loại có thể cấu hình khác nhau.
 */
data class TikTokRunConfig(
    val timeBetweenActionsMin: Int = 5,
    val timeBetweenActionsMax: Int = 15,
    val timeBetweenTasksMin: Int = 10,
    val timeBetweenTasksMax: Int = 20,
    val timeNoTaskMin: Int = 20,
    val timeNoTaskMax: Int = 40,

    val randomTapBeforeAction: Boolean = true,
    val randomViewContent: Boolean = true,
    val randomSwipe: Boolean = true,
    val occasionallyBackHome: Boolean = true,
    val waitBeforeBackHome: Int = 5,
    val backHomeAfterFinish: Boolean = true,

    val showNotifyNewContent: Boolean = true,
    val periodicContentCheck: Boolean = true,
    val reloadUiOnUpdate: Boolean = true,
    val waitBeforeReload: Int = 5,
    val backHomeAfterComplete: Boolean = true,
    val waitBeforeBackHomeComplete: Int = 3,
    val repeatBackHomeComplete: Int = 1,

    val randomPauseEnabled: Boolean = true,
    val randomPauseMin: Int = 40,
    val randomPauseMax: Int = 60,
    val rotateAccountsEnabled: Boolean = true,
    val rotateAfterCount: Int = 50,
    val rotateRestMinutes: Int = 5,
    val reduceSystemLoadEnabled: Boolean = true,
    val reduceSystemLoadAfterCount: Int = 10,
    val stopOnErrorEnabled: Boolean = true,
    val stopOnErrorAfterCount: Int = 10,
    val stopOnTasksDoneEnabled: Boolean = true,
    val stopOnTasksDoneAfterCount: Int = 100
)

object TikTokRunConfigStore {
    private const val PREFS_NAME = "cayxu_tiktok_run_config"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Đọc cấu hình đã lưu cho [variant] (STANDARD/LITE/STUDIO). Chưa lưu lần nào thì trả về mặc định. */
    fun getConfig(context: Context, variant: String): TikTokRunConfig {
        val p = prefs(context)
        fun i(key: String, default: Int) = p.getInt("${variant}_$key", default)
        fun b(key: String, default: Boolean) = p.getBoolean("${variant}_$key", default)
        val default = TikTokRunConfig()

        return TikTokRunConfig(
            timeBetweenActionsMin = i("timeBetweenActionsMin", default.timeBetweenActionsMin),
            timeBetweenActionsMax = i("timeBetweenActionsMax", default.timeBetweenActionsMax),
            timeBetweenTasksMin = i("timeBetweenTasksMin", default.timeBetweenTasksMin),
            timeBetweenTasksMax = i("timeBetweenTasksMax", default.timeBetweenTasksMax),
            timeNoTaskMin = i("timeNoTaskMin", default.timeNoTaskMin),
            timeNoTaskMax = i("timeNoTaskMax", default.timeNoTaskMax),

            randomTapBeforeAction = b("randomTapBeforeAction", default.randomTapBeforeAction),
            randomViewContent = b("randomViewContent", default.randomViewContent),
            randomSwipe = b("randomSwipe", default.randomSwipe),
            occasionallyBackHome = b("occasionallyBackHome", default.occasionallyBackHome),
            waitBeforeBackHome = i("waitBeforeBackHome", default.waitBeforeBackHome),
            backHomeAfterFinish = b("backHomeAfterFinish", default.backHomeAfterFinish),

            showNotifyNewContent = b("showNotifyNewContent", default.showNotifyNewContent),
            periodicContentCheck = b("periodicContentCheck", default.periodicContentCheck),
            reloadUiOnUpdate = b("reloadUiOnUpdate", default.reloadUiOnUpdate),
            waitBeforeReload = i("waitBeforeReload", default.waitBeforeReload),
            backHomeAfterComplete = b("backHomeAfterComplete", default.backHomeAfterComplete),
            waitBeforeBackHomeComplete = i("waitBeforeBackHomeComplete", default.waitBeforeBackHomeComplete),
            repeatBackHomeComplete = i("repeatBackHomeComplete", default.repeatBackHomeComplete),

            randomPauseEnabled = b("randomPauseEnabled", default.randomPauseEnabled),
            randomPauseMin = i("randomPauseMin", default.randomPauseMin),
            randomPauseMax = i("randomPauseMax", default.randomPauseMax),
            rotateAccountsEnabled = b("rotateAccountsEnabled", default.rotateAccountsEnabled),
            rotateAfterCount = i("rotateAfterCount", default.rotateAfterCount),
            rotateRestMinutes = i("rotateRestMinutes", default.rotateRestMinutes),
            reduceSystemLoadEnabled = b("reduceSystemLoadEnabled", default.reduceSystemLoadEnabled),
            reduceSystemLoadAfterCount = i("reduceSystemLoadAfterCount", default.reduceSystemLoadAfterCount),
            stopOnErrorEnabled = b("stopOnErrorEnabled", default.stopOnErrorEnabled),
            stopOnErrorAfterCount = i("stopOnErrorAfterCount", default.stopOnErrorAfterCount),
            stopOnTasksDoneEnabled = b("stopOnTasksDoneEnabled", default.stopOnTasksDoneEnabled),
            stopOnTasksDoneAfterCount = i("stopOnTasksDoneAfterCount", default.stopOnTasksDoneAfterCount)
        )
    }

    /** Lưu THẬT cấu hình cho [variant] xuống SharedPreferences - còn nguyên sau khi tắt/mở lại app. */
    fun saveConfig(context: Context, variant: String, config: TikTokRunConfig) {
        prefs(context).edit().apply {
            putInt("${variant}_timeBetweenActionsMin", config.timeBetweenActionsMin)
            putInt("${variant}_timeBetweenActionsMax", config.timeBetweenActionsMax)
            putInt("${variant}_timeBetweenTasksMin", config.timeBetweenTasksMin)
            putInt("${variant}_timeBetweenTasksMax", config.timeBetweenTasksMax)
            putInt("${variant}_timeNoTaskMin", config.timeNoTaskMin)
            putInt("${variant}_timeNoTaskMax", config.timeNoTaskMax)

            putBoolean("${variant}_randomTapBeforeAction", config.randomTapBeforeAction)
            putBoolean("${variant}_randomViewContent", config.randomViewContent)
            putBoolean("${variant}_randomSwipe", config.randomSwipe)
            putBoolean("${variant}_occasionallyBackHome", config.occasionallyBackHome)
            putInt("${variant}_waitBeforeBackHome", config.waitBeforeBackHome)
            putBoolean("${variant}_backHomeAfterFinish", config.backHomeAfterFinish)

            putBoolean("${variant}_showNotifyNewContent", config.showNotifyNewContent)
            putBoolean("${variant}_periodicContentCheck", config.periodicContentCheck)
            putBoolean("${variant}_reloadUiOnUpdate", config.reloadUiOnUpdate)
            putInt("${variant}_waitBeforeReload", config.waitBeforeReload)
            putBoolean("${variant}_backHomeAfterComplete", config.backHomeAfterComplete)
            putInt("${variant}_waitBeforeBackHomeComplete", config.waitBeforeBackHomeComplete)
            putInt("${variant}_repeatBackHomeComplete", config.repeatBackHomeComplete)

            putBoolean("${variant}_randomPauseEnabled", config.randomPauseEnabled)
            putInt("${variant}_randomPauseMin", config.randomPauseMin)
            putInt("${variant}_randomPauseMax", config.randomPauseMax)
            putBoolean("${variant}_rotateAccountsEnabled", config.rotateAccountsEnabled)
            putInt("${variant}_rotateAfterCount", config.rotateAfterCount)
            putInt("${variant}_rotateRestMinutes", config.rotateRestMinutes)
            putBoolean("${variant}_reduceSystemLoadEnabled", config.reduceSystemLoadEnabled)
            putInt("${variant}_reduceSystemLoadAfterCount", config.reduceSystemLoadAfterCount)
            putBoolean("${variant}_stopOnErrorEnabled", config.stopOnErrorEnabled)
            putInt("${variant}_stopOnErrorAfterCount", config.stopOnErrorAfterCount)
            putBoolean("${variant}_stopOnTasksDoneEnabled", config.stopOnTasksDoneEnabled)
            putInt("${variant}_stopOnTasksDoneAfterCount", config.stopOnTasksDoneAfterCount)

            apply()
        }
    }
}
