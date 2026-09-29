package com.cayxu.app.ui.screens.golike

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Model tài khoản chạy nhiệm vụ Golike (hỗ trợ TikTok, Facebook, Instagram).
 */
data class GolikeAccount(
    val id: String, // UID hoặc @handle
    val platform: String, // "tiktok", "facebook", "instagram"
    val username: String,
    val avatar: String = "",
    val isLive: Boolean = true,
    val isGolikeLinked: Boolean = false,
    val golikeAccountId: String = "",
    val proxy: String = "",
    val lastStatus: String = "Sẵn sàng",
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val lastErrorDetail: String? = null
)

/**
 * Lưu trữ độc lập toàn bộ dữ liệu tài khoản và phiên đăng nhập của Golike.
 * Sử dụng SharedPreferences riêng: "golike_accounts_pref".
 * Thao tác xóa hoặc cập nhật sử dụng .commit() để ghi đĩa tức thì, vĩnh viễn.
 */
object GolikeAccountsStore {
    private const val PREFS_NAME = "golike_accounts_pref"
    private const val KEY_TOKEN = "golike_token"
    private const val KEY_USERNAME = "golike_username"
    private const val KEY_BALANCE = "golike_balance"
    private const val KEY_T_TOKEN = "golike_t_token"
    private const val KEY_DEVICE_ID = "golike_device_id"
    private const val KEY_G_AUTH = "golike_g_auth"
    private const val KEY_PREFIX_ACCOUNTS = "golike_accounts_"

    private val gson = Gson()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== PHIÊN ĐĂNG NHẬP GOLIKE =====

    fun isLoggedIn(context: Context): Boolean =
        !prefs(context).getString(KEY_TOKEN, null).isNullOrBlank()

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getUsername(context: Context): String =
        prefs(context).getString(KEY_USERNAME, "").orEmpty()

    fun getBalance(context: Context): Long =
        prefs(context).getLong(KEY_BALANCE, 0L)

    fun getTToken(context: Context): String =
        prefs(context).getString(KEY_T_TOKEN, "").orEmpty()

    fun getDeviceId(context: Context): String =
        prefs(context).getString(KEY_DEVICE_ID, "").orEmpty()

    fun getGAuth(context: Context): String =
        prefs(context).getString(KEY_G_AUTH, "").orEmpty()

    fun saveLogin(
        context: Context,
        token: String,
        username: String,
        balance: Long,
        tToken: String = "",
        deviceId: String = "",
        gAuth: String = ""
    ) {
        val editor = prefs(context).edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_USERNAME, username.trim())
            .putLong(KEY_BALANCE, balance)
        if (tToken.isNotBlank()) editor.putString(KEY_T_TOKEN, tToken.trim())
        if (deviceId.isNotBlank()) editor.putString(KEY_DEVICE_ID, deviceId.trim())
        if (gAuth.isNotBlank()) editor.putString(KEY_G_AUTH, gAuth.trim())
        editor.commit()
    }

    fun updateBalance(context: Context, balance: Long) {
        prefs(context).edit()
            .putLong(KEY_BALANCE, balance)
            .commit()
    }

    fun clearSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_BALANCE)
            .remove(KEY_T_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_G_AUTH)
            .commit()
    }

    fun getApiClient(context: Context): GolikeApiClient {
        return GolikeApiClient(
            authToken = getToken(context),
            tToken = getTToken(context),
            deviceId = getDeviceId(context),
            username = getUsername(context),
            gAuth = getGAuth(context)
        )
    }

    // ===== QUẢN LÝ DANH SÁCH TÀI KHOẢN THEO NỀN TẢNG =====

    fun getAccounts(context: Context, platform: String): List<GolikeAccount> {
        val key = "${KEY_PREFIX_ACCOUNTS}${platform.lowercase()}"
        val json = prefs(context).getString(key, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<GolikeAccount>>() {}.type
            gson.fromJson<List<GolikeAccount>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun saveAccounts(context: Context, platform: String, list: List<GolikeAccount>) {
        val key = "${KEY_PREFIX_ACCOUNTS}${platform.lowercase()}"
        prefs(context).edit()
            .putString(key, gson.toJson(list))
            .commit()
    }

    fun addOrUpdateAccount(context: Context, account: GolikeAccount) {
        val currentList = getAccounts(context, account.platform).toMutableList()
        val index = currentList.indexOfFirst { it.id.equals(account.id, ignoreCase = true) }
        if (index >= 0) {
            currentList[index] = account
        } else {
            currentList.add(account)
        }
        saveAccounts(context, account.platform, currentList)
    }

    /** Xóa vĩnh viễn danh sách tài khoản theo ID (sử dụng .commit()) */
    fun removeAccounts(context: Context, platform: String, idsToRemove: Collection<String>) {
        if (idsToRemove.isEmpty()) return
        val currentList = getAccounts(context, platform)
        val cleanSet = idsToRemove.map { it.trim().lowercase() }.toSet()
        val updated = currentList.filterNot { cleanSet.contains(it.id.trim().lowercase()) }
        saveAccounts(context, platform, updated)
    }

    /** Cập nhật trạng thái liên kết Golike */
    fun setGolikeLinked(context: Context, platform: String, id: String, linked: Boolean, golikeAccId: String = "") {
        val currentList = getAccounts(context, platform).toMutableList()
        val index = currentList.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (index >= 0) {
            val current = currentList[index]
            currentList[index] = current.copy(
                isGolikeLinked = linked,
                golikeAccountId = if (golikeAccId.isNotBlank()) golikeAccId else current.golikeAccountId
            )
            saveAccounts(context, platform, currentList)
        }
    }

    /** Cập nhật tiến trình/kết quả thực thi của nick */
    fun updateAccountProgress(
        context: Context,
        platform: String,
        id: String,
        status: String,
        isSuccess: Boolean? = null,
        errorDetail: String? = null
    ) {
        val currentList = getAccounts(context, platform).toMutableList()
        val index = currentList.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (index >= 0) {
            val current = currentList[index]
            val newSuccess = if (isSuccess == true) current.successCount + 1 else current.successCount
            val newError = if (isSuccess == false) current.errorCount + 1 else current.errorCount
            currentList[index] = current.copy(
                lastStatus = status,
                successCount = newSuccess,
                errorCount = newError,
                lastErrorDetail = errorDetail ?: current.lastErrorDetail
            )
            saveAccounts(context, platform, currentList)
        }
    }
}
