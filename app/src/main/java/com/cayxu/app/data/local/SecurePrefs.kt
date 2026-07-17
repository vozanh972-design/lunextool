package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Lưu key đăng nhập bằng EncryptedSharedPreferences theo đúng yêu cầu.
 */
class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "cayxu_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_LOGIN_KEY, key).apply()
    }

    fun getKey(): String? = prefs.getString(KEY_LOGIN_KEY, null)

    fun clearKey() {
        prefs.edit().remove(KEY_LOGIN_KEY).apply()
    }

    companion object {
        private const val KEY_LOGIN_KEY = "login_key"
    }
}
