package com.cayxu.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object DeviceUtils {

    /**
     * Lấy device_id bằng Settings.Secure.ANDROID_ID theo đúng yêu cầu.
     */
    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }
}
