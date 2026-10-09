package com.cayxu.app.ui.screens.golike

import android.content.Context
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
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
    // ===== 17 TRƯỜNG CHUẨN GOLIKE (LƯU TRONG golike_accounts_pref) =====
    private const val KEY_TOKEN = "golike_token"
    private const val KEY_T_HEADER = "golike_t_header"
    private const val KEY_G_AUTH = "golike_g_auth"
    private const val KEY_DEVICE_ID = "golike_device_id"
    private const val KEY_USERNAME = "golike_username"
    private const val KEY_USER_ID = "golike_user_id"
    private const val KEY_SIGNING_KEY = "golike_signing_key"
    private const val KEY_WEB_DATA = "golike_web_data"
    private const val KEY_WEB_COOKIES = "golike_web_cookies"
    private const val KEY_HEADER = "golike_header"
    private const val KEY_TIKTOK_MAP = "golike_tiktok_map"
    private const val KEY_VERSION_APP = "golike_version_app"
    private const val KEY_WEB_VERSION = "golike_web_version"
    private const val KEY_WEB_VERSION_TEXT = "golike_web_version_text"
    private const val KEY_PROTOCOL = "golike_protocol"
    private const val KEY_GAUTH_VERSION = "golike_gauth_version"
    private const val KEY_SCHEME = "golike_scheme"

    // Các key bổ trợ
    private const val KEY_BALANCE = "golike_balance"
    private const val KEY_T_TOKEN = "golike_t_token"
    private const val KEY_CLIENT = "golike_client"
    private const val KEY_TIKTOK_ACCOUNTS_LIST = "golike_tiktok_accounts_list"
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

    fun getWebCookies(context: Context): String =
        prefs(context).getString(KEY_WEB_COOKIES, "").orEmpty()

    fun getHeader(context: Context): String =
        prefs(context).getString(KEY_HEADER, "").orEmpty()

    fun getProtocol(context: Context): String =
        prefs(context).getString(KEY_PROTOCOL, "").orEmpty()

    fun getGauthVersion(context: Context): String =
        prefs(context).getString(KEY_GAUTH_VERSION, "").orEmpty()

    fun saveWebCookies(context: Context, cookies: String) {
        prefs(context).edit().putString(KEY_WEB_COOKIES, cookies.trim()).commit()
    }

    fun saveHeader(context: Context, header: String) {
        prefs(context).edit().putString(KEY_HEADER, header.trim()).commit()
    }

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
        webData: String = "",
        webCookies: String = "",
        header: String = ""
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
        if (webCookies.isNotBlank()) editor.putString(KEY_WEB_COOKIES, webCookies.trim())
        if (header.isNotBlank()) editor.putString(KEY_HEADER, header.trim())
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

    fun saveVersionData(
        context: Context,
        version: String = "",
        client: String = "",
        scheme: String = ""
    ) {
        val editor = prefs(context).edit()
        if (version.isNotBlank()) editor.putString(KEY_VERSION_APP, version.trim())
        if (client.isNotBlank()) editor.putString(KEY_CLIENT, client.trim())
        if (scheme.isNotBlank()) editor.putString(KEY_SCHEME, scheme.trim())
        editor.commit()
    }

    fun saveWebVersionInfo(context: Context, webVersion: String?, webVersionText: String?) {
        val editor = prefs(context).edit()
        editor.putString(KEY_WEB_VERSION, webVersion?.takeIf { it.isNotBlank() } ?: "3.0")
        editor.putString(KEY_WEB_VERSION_TEXT, webVersionText?.takeIf { it.isNotBlank() } ?: "26.09.17.1")
        editor.commit()
    }

    fun getWebVersion(context: Context): String =
        prefs(context).getString(KEY_WEB_VERSION, "3.0")?.takeIf { it.isNotBlank() } ?: "3.0"

    fun getWebVersionText(context: Context): String =
        prefs(context).getString(KEY_WEB_VERSION_TEXT, "26.09.17.1")?.takeIf { it.isNotBlank() } ?: "26.09.17.1"

    fun getVersionApp(context: Context): String =
        prefs(context).getString(KEY_VERSION_APP, "")?.takeIf { it.isNotBlank() } ?: "26.09.17.1"

    fun getClient(context: Context): String =
        prefs(context).getString(KEY_CLIENT, "")?.takeIf { it.isNotBlank() } ?: "web"

    fun getScheme(context: Context): String =
        prefs(context).getString(KEY_SCHEME, "")?.takeIf { it.isNotBlank() } ?: "https"

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
            .remove(KEY_WEB_COOKIES)
            .remove(KEY_HEADER)
            .remove(KEY_WEB_VERSION)
            .remove(KEY_WEB_VERSION_TEXT)
            .remove(KEY_TIKTOK_MAP)
            .remove(KEY_SCHEME)
            .remove(KEY_CLIENT)
            .remove(KEY_PROTOCOL)
            .remove(KEY_GAUTH_VERSION)
            .remove(KEY_VERSION_APP)
            .remove(KEY_TIKTOK_ACCOUNTS_LIST)
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
            gAuth = getGAuth(context),
            version = getVersionApp(context),
            client = getClient(context),
            scheme = getScheme(context),
            webVersion = getWebVersion(context),
            webVersionText = getWebVersionText(context)
        )
    }

    private const val KEY_IS_SCANNING_TIKTOK = "golike_is_scanning_tiktok"

    fun isScanningTikTok(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_SCANNING_TIKTOK, false)

    fun setScanningTikTok(context: Context, scanning: Boolean) {
        prefs(context).edit().putBoolean(KEY_IS_SCANNING_TIKTOK, scanning).commit()
    }

    /**
     * Nạp toàn bộ tài khoản TikTok quét được từ ứng dụng máy vào kho Golike ("golike_accounts_pref").
     * Đồng thời CÔ LẬP HOÀN TOÀN với XSMM: xóa bỏ các nick không thuộc XSMM khỏi TikTokAccountsStore ("cayxu_tiktok_accounts")
     * để màn hình XSMM không bị dính nick lạ.
     */
    fun importScannedTikTokAccounts(context: Context): List<GolikeAccount> {
        val currentGolike = getAccounts(context, "tiktok").toMutableList()
        val deviceAccounts = runCatching { TikTokAccountsStore.getAccounts(context) }.getOrDefault(emptyList())

        deviceAccounts.forEach { acc ->
            val clean = acc.handle.trim().removePrefix("@")
            if (clean.isNotBlank()) {
                val existingIndex = currentGolike.indexOfFirst { it.id.equals(clean, ignoreCase = true) }
                if (existingIndex >= 0) {
                    val existing = currentGolike[existingIndex]
                    currentGolike[existingIndex] = existing.copy(
                        username = acc.displayName.ifBlank { existing.username.ifBlank { clean } },
                        avatar = if (acc.avatarUrl.isNotBlank()) acc.avatarUrl else existing.avatar
                    )
                } else {
                    currentGolike.add(
                        GolikeAccount(
                            id = clean,
                            platform = "tiktok",
                            username = acc.displayName.ifBlank { clean },
                            avatar = acc.avatarUrl,
                            isLive = true,
                            isGolikeLinked = false,
                            lastStatus = "Cần liên kết Golike"
                        )
                    )
                }
            }
        }

        saveAccounts(context, "tiktok", currentGolike)
        return currentGolike
    }

    // ===== QUẢN LÝ DANH SÁCH TÀI KHOẢN THEO NỀN TẢNG =====

    fun getTikTokAccounts(context: Context): List<GolikeAccount> =
        getAccounts(context, "tiktok")

    fun saveTikTokAccounts(context: Context, list: List<GolikeAccount>) {
        saveAccounts(context, "tiktok", list)
    }

    fun getAccounts(context: Context, platform: String): List<GolikeAccount> {
        val key = if (platform.lowercase() == "tiktok") KEY_TIKTOK_ACCOUNTS_LIST else "${KEY_PREFIX_ACCOUNTS}${platform.lowercase()}"
        var json = prefs(context).getString(key, null)
        if (json.isNullOrBlank() && platform.lowercase() == "tiktok") {
            json = prefs(context).getString("${KEY_PREFIX_ACCOUNTS}tiktok", null)
        }
        return if (!json.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<GolikeAccount>>() {}.type
                gson.fromJson<List<GolikeAccount>>(json, type) ?: emptyList<GolikeAccount>()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    fun saveAccounts(context: Context, platform: String, list: List<GolikeAccount>) {
        val key = if (platform.lowercase() == "tiktok") KEY_TIKTOK_ACCOUNTS_LIST else "${KEY_PREFIX_ACCOUNTS}${platform.lowercase()}"
        val json = gson.toJson(list)
        val editor = prefs(context).edit().putString(key, json)
        if (platform.lowercase() == "tiktok") {
            editor.putString("${KEY_PREFIX_ACCOUNTS}tiktok", json)
        }
        editor.commit()
    }

    fun clearTikTokAccounts(context: Context) {
        prefs(context).edit()
            .remove(KEY_TIKTOK_ACCOUNTS_LIST)
            .remove("${KEY_PREFIX_ACCOUNTS}tiktok")
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
