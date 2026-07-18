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

    /**
     * Đánh dấu app bị khoá VĨNH VIỄN (phát hiện bị patch/bypass, hoặc key bị
     * server thu hồi khi re-check định kỳ). Một khi đã set, app sẽ luôn mở
     * thẳng vào màn "Đã bị khoá" mỗi lần mở app, không cách nào quay lại luồng
     * bình thường ngoài việc gỡ cài đặt và cài lại bản gốc chưa bị sửa.
     */
    fun setPermanentlyBlocked() {
        prefs.edit().putBoolean(KEY_BLOCKED, true).apply()
    }

    fun isPermanentlyBlocked(): Boolean = prefs.getBoolean(KEY_BLOCKED, false)

    companion object {
        private const val KEY_LOGIN_KEY = "login_key"
        private const val KEY_BLOCKED = "permanently_blocked"
    }
}
