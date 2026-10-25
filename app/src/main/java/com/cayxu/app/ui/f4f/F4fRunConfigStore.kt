package com.cayxu.app.ui.f4f

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Cấu hình chạy nhiệm vụ riêng biệt của Follow4Follow.
 * Có @Keep và @SerializedName đảm bảo tương thích 100% với R8/ProGuard không bao giờ gãy logic.
 */
@Keep
data class F4fRunConfig(
    @SerializedName("delaySeconds") val delaySeconds: Int = 5,
    @SerializedName("delayMinSeconds") val delayMinSeconds: Int = 5,
    @SerializedName("delayMaxSeconds") val delayMaxSeconds: Int = 10,
    @SerializedName("doJobMinSeconds") val doJobMinSeconds: Int = 5,
    @SerializedName("doJobMaxSeconds") val doJobMaxSeconds: Int = 10,
    @SerializedName("targetTaskCount") val targetTaskCount: Int = 50,
    @SerializedName("enableFollow") val enableFollow: Boolean = true,
    @SerializedName("enableLike") val enableLike: Boolean = true,
    @SerializedName("enableComment") val enableComment: Boolean = false,
    @SerializedName("autoSwapOnError") val autoSwapOnError: Boolean = true,
    @SerializedName("errorSwapCount") val errorSwapCount: Int = 3,
    @SerializedName("swipeBeforeGetJob") val swipeBeforeGetJob: Boolean = false,
)

@Keep
object F4fRunConfigStore {
    private val gson = Gson()

    fun get(context: Context): F4fRunConfig {
        val prefs = context.applicationContext.getSharedPreferences(F4fNativeBridge.fetchPrefsRunConfig(), Context.MODE_PRIVATE)
        val json = prefs.getString(F4fNativeBridge.fetchKeyRunConfig(), null) ?: return F4fRunConfig()
        return runCatching {
            gson.fromJson(json, F4fRunConfig::class.java) ?: F4fRunConfig()
        }.getOrDefault(F4fRunConfig())
    }

    fun save(context: Context, config: F4fRunConfig) {
        val prefs = context.applicationContext.getSharedPreferences(F4fNativeBridge.fetchPrefsRunConfig(), Context.MODE_PRIVATE)
        prefs.edit().putString(F4fNativeBridge.fetchKeyRunConfig(), gson.toJson(config)).apply()
    }
}
