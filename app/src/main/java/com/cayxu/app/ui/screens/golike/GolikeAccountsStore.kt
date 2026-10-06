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
    private const val KEY_T_HEADER = "golike_t_header"
    private const val KEY_T_TOKEN = "golike_t_token"
    private const val KEY_DEVICE_ID = "golike_device_id"
    private const val KEY_G_AUTH = "golike_g_auth"
    private const val KEY_SIGNING_KEY = "golike_signing_key"
    private const val KEY_USER_ID = "golike_user_id"
    private const val KEY_WEB_DATA = "golike_web_data"
    private const val KEY_WEB_VERSION = "golike_web_version"
    private const val KEY_WEB_VERSION_TEXT = "golike_web_version_text"
    private const val KEY_TIKTOK_MAP = "golike_tiktok_map"
    private const val KEY_SCHEME = "golike_scheme"
    private const val KEY_PROTOCOL = "golike_protocol"
    private const val KEY_GAUTH_VERSION = "golike_gauth_version"
    private const val KEY_VERSION_APP = "golike_version_app"
    private const val KEY_PREFIX_ACCOUNTS = "golike_accounts_"

    private val gson = Gson()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== PHIÊN ĐĂNG NHẬP GOLIKE (11 KEYS CHUẨN GOMAX) =====

    fun isLoggedIn(context: Context): Boolean =
        !prefs(context).getString(KEY_TOKEN, null).isNullOrBlank()

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getUsername(context: Context): String =
        prefs(context).getString(KEY_USERNAME, "").orEmpty()

    fun getBalance(context: Context): Long =
        prefs(context).getLong(KEY_BALANCE, 0L)

    fun getTToken(context: Context): String =
        prefs(context).getString(KEY_T_HEADER, "").orEmpty().ifBlank {
            prefs(context).getString(KEY_T_TOKEN, "").orEmpty()
        }

    fun getDeviceId(context: Context): String =
        prefs(context).getString(KEY_DEVICE_ID, "").orEmpty()

    fun getGAuth(context: Context): String =
        prefs(context).getString(KEY_G_AUTH, "").orEmpty()

    fun getSigningKey(context: Context): String =
        prefs(context).getString(KEY_SIGNING_KEY, "").orEmpty()

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "").orEmpty()

    fun getWebData(context: Context): String =
        prefs(context).getString(KEY_WEB_DATA, "").orEmpty()

    fun saveLogin(
        context: Context,
        token: String,
        username: String,
        balance: Long,
        tToken: String = "",
        deviceId: String = "",
        gAuth: String = "",
        signingKey: String = "",
        userId: String = "",
        webData: String = ""
    ) {
        val editor = prefs(context).edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_USERNAME, username.trim())
            .putLong(KEY_BALANCE, balance)
        if (tToken.isNotBlank()) {
            editor.putString(KEY_T_HEADER, tToken.trim())
            editor.putString(KEY_T_TOKEN, tToken.trim())
        }
        if (deviceId.isNotBlank()) editor.putString(KEY_DEVICE_ID, deviceId.trim())
        if (gAuth.isNotBlank()) editor.putString(KEY_G_AUTH, gAuth.trim())
        if (signingKey.isNotBlank()) editor.putString(KEY_SIGNING_KEY, signingKey.trim())
        if (userId.isNotBlank()) editor.putString(KEY_USER_ID, userId.trim())
        if (webData.isNotBlank()) editor.putString(KEY_WEB_DATA, webData.trim())
        editor.commit()
    }

    fun saveProtocolData(
        context: Context,
        scheme: String = "",
        protocol: String = "",
        gauthVersion: String = "",
        versionApp: String = ""
    ) {
        val editor = prefs(context).edit()
        if (scheme.isNotBlank()) editor.putString(KEY_SCHEME, scheme.trim())
        if (protocol.isNotBlank()) editor.putString(KEY_PROTOCOL, protocol.trim())
        if (gauthVersion.isNotBlank()) editor.putString(KEY_GAUTH_VERSION, gauthVersion.trim())
        if (versionApp.isNotBlank()) editor.putString(KEY_VERSION_APP, versionApp.trim())
        editor.commit()
    }

    fun saveTikTokMapping(context: Context, username: String, golikeAccountId: String) {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank() || golikeAccountId.isBlank()) return
        val currentJson = prefs(context).getString(KEY_TIKTOK_MAP, "{}") ?: "{}"
        try {
            val mapObj = org.json.JSONObject(currentJson)
            mapObj.put(clean, golikeAccountId.trim())
            prefs(context).edit().putString(KEY_TIKTOK_MAP, mapObj.toString()).commit()
        } catch (_: Exception) {}
    }

    fun getTikTokAccountIdFromMap(context: Context, username: String): String? {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return null
        val currentJson = prefs(context).getString(KEY_TIKTOK_MAP, "{}") ?: "{}"
        return try {
            val mapObj = org.json.JSONObject(currentJson)
            mapObj.optString(clean).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
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
            .remove(KEY_T_HEADER)
            .remove(KEY_T_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_G_AUTH)
            .remove(KEY_SIGNING_KEY)
            .remove(KEY_USER_ID)
            .remove(KEY_WEB_DATA)
            .remove(KEY_TIKTOK_MAP)
            .remove(KEY_SCHEME)
            .remove(KEY_PROTOCOL)
            .remove(KEY_GAUTH_VERSION)
            .remove(KEY_VERSION_APP)
            .remove("${KEY_PREFIX_ACCOUNTS}tiktok")
            .remove("${KEY_PREFIX_ACCOUNTS}facebook")
            .remove("${KEY_PREFIX_ACCOUNTS}instagram")
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
        if (!isLoggedIn(context)) {
            return emptyList()
        }
        val key = "${KEY_PREFIX_ACCOUNTS}${platform.lowercase()}"
        val json = prefs(context).getString(key, null)
        val savedList: List<GolikeAccount> = if (!json.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<GolikeAccount>>() {}.type
                gson.fromJson<List<GolikeAccount>>(json, type) ?: emptyList<GolikeAccount>()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        if (platform.lowercase() == "tiktok") {
            // Đọc trực tiếp từ Kho tài khoản TikTok chung của thiết bị (TikTokAccountsStore)
            val sharedAccounts = com.cayxu.app.data.local.TikTokAccountsStore.getAccounts(context)
            if (sharedAccounts.isNotEmpty()) {
                val savedMap = savedList.associateBy { it.id.trim().removePrefix("@").lowercase() }
                val merged = mutableListOf<GolikeAccount>()
                val seen = mutableSetOf<String>()

                for (shared in sharedAccounts) {
                    val cleanHandle = shared.handle.trim().removePrefix("@").lowercase()
                    if (cleanHandle.isBlank() || !seen.add(cleanHandle)) continue
                    val existing = savedMap[cleanHandle]
                    merged.add(
                        existing?.copy(
                            username = shared.handle,
                            avatar = existing.avatar.ifBlank { shared.avatarUrl },
                            isLive = shared.isLive,
                            proxy = existing.proxy.ifBlank { shared.proxy }
                        ) ?: GolikeAccount(
                            id = cleanHandle,
                            platform = "tiktok",
                            username = shared.handle,
                            avatar = shared.avatarUrl,
                            isLive = shared.isLive,
                            isGolikeLinked = false,
                            golikeAccountId = "",
                            proxy = shared.proxy,
                            lastStatus = "Chưa liên kết Golike"
                        )
                    )
                }
                for (saved in savedList) {
                    val cleanId = saved.id.trim().removePrefix("@").lowercase()
                    if (cleanId.isNotBlank() && seen.add(cleanId)) {
                        merged.add(saved)
                    }
                }
                return merged
            }
        }

        return savedList
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
        if (account.platform.lowercase() == "tiktok") {
            try {
                com.cayxu.app.data.local.TikTokAccountsStore.addFromCapture(
                    context = context,
                    handle = account.username.ifBlank { account.id },
                    displayName = account.username,
                    avatarUrl = account.avatar,
                    variant = com.cayxu.app.data.local.TikTokAppVariant.STANDARD,
                    proxy = account.proxy
                )
            } catch (_: Exception) {}
        }
    }

    /** Xóa vĩnh viễn danh sách tài khoản theo ID (sử dụng .commit()) */
    fun removeAccounts(context: Context, platform: String, idsToRemove: Collection<String>) {
        if (idsToRemove.isEmpty()) return
        val currentList = getAccounts(context, platform)
        val cleanSet = idsToRemove.map { it.trim().lowercase() }.toSet()
        val updated = currentList.filterNot { cleanSet.contains(it.id.trim().lowercase()) }
        saveAccounts(context, platform, updated)
        if (platform.lowercase() == "tiktok") {
            try {
                val localTiktok = com.cayxu.app.data.local.TikTokAccountsStore.getAccounts(context)
                val uidsToRemove = localTiktok.filter {
                    cleanSet.contains(it.handle.trim().removePrefix("@").lowercase()) || cleanSet.contains(it.uid.lowercase())
                }.map { it.uid }
                if (uidsToRemove.isNotEmpty()) {
                    com.cayxu.app.data.local.TikTokAccountsStore.removeAccounts(context, uidsToRemove)
                }
            } catch (_: Exception) {}
        }
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
