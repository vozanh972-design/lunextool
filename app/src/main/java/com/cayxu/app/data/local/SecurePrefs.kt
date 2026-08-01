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

    /** Fingerprint cục bộ ràng buộc key với máy+chữ ký APK - xem IntegrityGuard.bindKeyToDevice. */
    fun saveKeyFingerprint(fingerprint: String) {
        prefs.edit().putString(KEY_FINGERPRINT, fingerprint).apply()
    }

    fun getKeyFingerprint(): String? = prefs.getString(KEY_FINGERPRINT, null)

    fun clearKey() {
        prefs.edit().remove(KEY_LOGIN_KEY).remove(KEY_FINGERPRINT).apply()
    }

    /**
     * Đánh dấu app bị khoá VĨNH VIỄN - CHỈ dùng khi phát hiện bị patch/bypass
     * (xem IntegrityGuard). Một khi đã set, app sẽ luôn mở thẳng vào màn "Đã bị
     * khoá" mỗi lần mở app, không cách nào quay lại luồng bình thường ngoài việc
     * gỡ cài đặt và cài lại bản gốc chưa bị sửa.
     *
     * LƯU Ý: trường hợp key hết hạn/bị server thu hồi (re-check định kỳ) KHÔNG
     * gọi hàm này nữa - xem KeyRecheckWorker/AppLockState.markKeyRevoked, trường
     * hợp đó chỉ xoá key và đưa về màn nhập Key, không khoá chết.
     */
    fun setPermanentlyBlocked() {
        prefs.edit().putBoolean(KEY_BLOCKED, true).apply()
    }

    fun isPermanentlyBlocked(): Boolean = prefs.getBoolean(KEY_BLOCKED, false)

    /**
     * Trả về ID hiển thị của tài khoản (8 chữ số, sinh ngẫu nhiên 1 lần duy nhất
     * rồi lưu lại) - dùng thay cho username thật để hiển thị ở màn Tài khoản.
     */
    fun getOrCreateAccountId(): String {
        val existing = prefs.getString(KEY_ACCOUNT_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = (10_000_000..99_999_999).random().toString()
        prefs.edit().putString(KEY_ACCOUNT_ID, generated).apply()
        return generated
    }

    /**
     * Lưu/đọc URI ảnh đại diện do người dùng tự chọn. Nếu chưa chọn (null),
     * màn Tài khoản sẽ hiển thị ảnh đại diện mặc định.
     */
    fun saveAvatarUri(uri: String?) {
        prefs.edit().putString(KEY_AVATAR_URI, uri).apply()
    }

    fun getAvatarUri(): String? = prefs.getString(KEY_AVATAR_URI, null)

    /**
     * Đánh dấu người dùng đã xem qua màn "Chào mừng" (welcome) lần đầu tiên. Chỉ hiện màn
     * chào mừng ở lần mở app ĐẦU TIÊN sau khi cài; những lần sau (kể cả khi key hết hạn/đổi
     * máy phải nhập lại key) sẽ vào thẳng màn nhập Key, không hiện lại màn chào mừng nữa.
     */
    fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_SEEN_WELCOME, false)

    fun setSeenWelcome() {
        prefs.edit().putBoolean(KEY_SEEN_WELCOME, true).apply()
    }

    companion object {
        private const val KEY_LOGIN_KEY = "login_key"
        private const val KEY_FINGERPRINT = "login_key_fingerprint"
        private const val KEY_BLOCKED = "permanently_blocked"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_SEEN_WELCOME = "has_seen_welcome"
    }
}
