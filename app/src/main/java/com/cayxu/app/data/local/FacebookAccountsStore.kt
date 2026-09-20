package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import org.json.JSONArray
import org.json.JSONObject

@Keep
data class FacebookPageItem(
    @SerializedName("pageId") val pageId: String = "",
    @SerializedName("pageName") val pageName: String = "",
    @SerializedName("pageToken") val pageToken: String = "",
    @SerializedName("additionalProfileId") val additionalProfileId: String = "",
    @SerializedName("avatar") val avatar: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("isLive") val isLive: Boolean = true
) {
    val displayUid: String
        get() = if (additionalProfileId.isNotBlank() && additionalProfileId.startsWith("615")) {
            additionalProfileId
        } else if (pageId.startsWith("615")) {
            pageId
        } else if (additionalProfileId.isNotBlank() && !additionalProfileId.equals(pageId, ignoreCase = true)) {
            additionalProfileId
        } else {
            "" // Ép không hiện ID page thông thường làm UID
        }
}

@Keep
data class FacebookAccount(
    @SerializedName("uid") val uid: String = "",
    @SerializedName("name") val name: String = "",      // Password hoặc Tên hiển thị
    @SerializedName("link") val link: String = "",      // 2FA
    @SerializedName("note") val note: String = "",      // Cookie
    @SerializedName("phone") val phone: String = "",     // Proxy
    @SerializedName("bio") val bio: String = "",       // Token
    @SerializedName("isLive") val isLive: Boolean = false,
    @SerializedName("avatar") val avatar: String = "",    // URL avatar
    @SerializedName("cover") val cover: String = "",     // URL ảnh bìa
    @SerializedName("email") val email: String = "",
    @SerializedName("pages") val pages: List<FacebookPageItem> = emptyList(),
    @SerializedName("password") val password: String = ""
)

object FacebookAccountsStore {
    private const val PREFS_NAME = "cayxu_facebook_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private const val ENTRY_SEPARATOR = "\u0001"
    private const val FIELD_SEPARATOR = "\u0002"
    private var memoryCache: MutableList<FacebookAccount>? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getAccounts(context: Context, forceReload: Boolean = false): List<FacebookAccount> {
        if (!forceReload) {
            memoryCache?.let { return it.toList() }
        }
        val raw = prefs(context).getString(KEY_ACCOUNTS, null)
        if (raw.isNullOrBlank()) {
            memoryCache = mutableListOf()
            return emptyList()
        }
        // 1. Phân tích định dạng JSON không phụ thuộc Reflection (chống 100% lỗi ProGuard/R8)
        if (raw.trimStart().startsWith("[")) {
            try {
                val jsonArr = JSONArray(raw)
                val list = mutableListOf<FacebookAccount>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val pagesList = mutableListOf<FacebookPageItem>()
                    if (obj.has("pages")) {
                        val pagesArr = obj.optJSONArray("pages")
                        if (pagesArr != null) {
                            for (j in 0 until pagesArr.length()) {
                                val pObj = pagesArr.getJSONObject(j)
                                pagesList.add(
                                    FacebookPageItem(
                                        pageId = pObj.optString("pageId", ""),
                                        pageName = pObj.optString("pageName", ""),
                                        pageToken = pObj.optString("pageToken", ""),
                                        additionalProfileId = pObj.optString("additionalProfileId", ""),
                                        avatar = pObj.optString("avatar", ""),
                                        cover = pObj.optString("cover", ""),
                                        isLive = pObj.optBoolean("isLive", true)
                                    )
                                )
                            }
                        }
                    }

                    list.add(
                        FacebookAccount(
                            uid = obj.optString("uid", ""),
                            name = obj.optString("name", ""),
                            link = obj.optString("link", ""),
                            note = obj.optString("note", ""),
                            phone = obj.optString("phone", ""),
                            bio = obj.optString("bio", ""),
                            isLive = obj.optBoolean("isLive", false),
                            avatar = obj.optString("avatar", ""),
                            cover = obj.optString("cover", ""),
                            email = obj.optString("email", ""),
                            pages = pagesList,
                            password = obj.optString("password", "")
                        )
                    )
                }
                val validList = list.filter { it.uid.isNotBlank() }
                memoryCache = validList.toMutableList()
                return validList
            } catch (_: Exception) {}
        }

        // 2. Fallback định dạng phân tách cũ
        val list = raw.split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 7) return@mapNotNull null
                try {
                    FacebookAccount(
                        uid = parts[0],
                        name = parts.getOrElse(1) { "" },
                        link = parts.getOrElse(2) { "" },
                        note = parts.getOrElse(3) { "" },
                        phone = parts.getOrElse(4) { "" },
                        bio = parts.getOrElse(5) { "" },
                        isLive = parts.getOrElse(6) { "die" } != "die",
                        avatar = parts.getOrElse(7) { "" },
                        email = parts.getOrElse(8) { "" },
                        password = parts.getOrElse(9) { "" }
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .filter { it.uid.isNotBlank() }
        memoryCache = list.toMutableList()
        return list
    }

    @Synchronized
    fun addAccount(context: Context, account: FacebookAccount) {
        addAccounts(context, listOf(account))
    }

    @Synchronized
    fun addAccount(
        context: Context,
        uid: String,
        name: String = "",
        link: String = "",
        note: String = "",
        phone: String = "",
        bio: String = "",
        isLive: Boolean = false,
        avatar: String = "",
        email: String = "",
        pages: List<FacebookPageItem> = emptyList()
    ) {
        val account = FacebookAccount(
            uid = uid,
            name = name,
            link = link,
            note = note,
            phone = phone,
            bio = bio,
            isLive = isLive,
            avatar = avatar,
            email = email,
            pages = pages
        )
        addAccounts(context, listOf(account))
    }

    @Synchronized
    fun addAccounts(context: Context, entries: List<FacebookAccount>) {
        val trimmedNew = entries
            .map {
                it.copy(
                    uid = it.uid.trim(),
                    name = it.name.trim(),
                    link = it.link.trim(),
                    note = it.note.trim(),
                    phone = it.phone.trim(),
                    bio = it.bio.trim(),
                    email = it.email.trim(),
                    password = it.password.trim()
                )
            }
            .filter { it.uid.isNotEmpty() }
        if (trimmedNew.isEmpty()) return

        val current = getAccounts(context, forceReload = true).toMutableList()
        trimmedNew.forEach { entry ->
            val existingIdx = current.indexOfFirst { it.uid == entry.uid }
            if (existingIdx >= 0) {
                current[existingIdx] = entry
            } else {
                current.add(entry)
            }
        }
        save(context, current)
    }

    @Synchronized
    fun updateAccount(context: Context, account: FacebookAccount) {
        val current = getAccounts(context, forceReload = true).toMutableList()
        val index = current.indexOfFirst { it.uid == account.uid }
        if (index >= 0) {
            current[index] = account
        } else {
            current.add(account)
        }
        save(context, current)
    }

    @Synchronized
    fun updatePageMedia(
        context: Context,
        parentUid: String,
        pageId: String,
        avatar: String? = null,
        cover: String? = null,
        additionalProfileId: String? = null
    ) {
        val current = getAccounts(context, forceReload = true).toMutableList()
        val parentIdx = current.indexOfFirst { it.uid == parentUid }
        if (parentIdx >= 0) {
            val parent = current[parentIdx]
            val updatedPages = parent.pages.map { p ->
                if (p.pageId == pageId || (p.additionalProfileId.isNotBlank() && p.additionalProfileId == pageId)) {
                    p.copy(
                        avatar = if (!avatar.isNullOrBlank()) avatar else p.avatar,
                        cover = if (!cover.isNullOrBlank()) cover else p.cover,
                        additionalProfileId = if (!additionalProfileId.isNullOrBlank()) additionalProfileId else p.additionalProfileId
                    )
                } else p
            }
            current[parentIdx] = parent.copy(pages = updatedPages)
            save(context, current)
        }
    }

    @Synchronized
    fun updatePageUid(
        context: Context,
        parentUid: String,
        pageId: String,
        uid615: String
    ) {
        if (uid615.isBlank()) return
        val current = getAccounts(context, forceReload = true).toMutableList()
        val parentIdx = current.indexOfFirst { it.uid == parentUid }
        if (parentIdx >= 0) {
            val parent = current[parentIdx]
            val updatedPages = parent.pages.map { p ->
                if (p.pageId == pageId) {
                    p.copy(additionalProfileId = uid615)
                } else p
            }
            current[parentIdx] = parent.copy(pages = updatedPages)
            save(context, current)
        }
    }

    @Synchronized
    fun removeAccount(context: Context, uid: String) {
        removeAccounts(context, listOf(uid))
    }

    @Synchronized
    fun removeAccounts(context: Context, uids: List<String>) {
        val current = getAccounts(context, forceReload = true).toMutableList()
        current.removeAll { it.uid in uids }
        save(context, current)
    }

    @Synchronized
    fun markLive(context: Context, uids: List<String>) {
        val current = getAccounts(context, forceReload = true).map { acc ->
            if (acc.uid in uids) acc.copy(isLive = true) else acc
        }
        save(context, current)
    }

    @Synchronized
    fun markDie(context: Context, uids: List<String>) {
        val current = getAccounts(context, forceReload = true).map { acc ->
            if (acc.uid in uids) acc.copy(isLive = false) else acc
        }
        save(context, current)
    }

    @Synchronized
    fun markLiveWithAvatar(context: Context, uids: List<String>, avatar: String) {
        val current = getAccounts(context, forceReload = true).map { acc ->
            if (acc.uid in uids) acc.copy(isLive = true, avatar = avatar) else acc
        }
        save(context, current)
    }

    @Synchronized
    fun updateLiveStatus(
        context: Context,
        uid: String,
        isLive: Boolean,
        avatar: String?,
        name: String?,
        email: String? = null,
        pages: List<FacebookPageItem>? = null
    ) {
        val current = getAccounts(context, forceReload = true).map { acc ->
            if (acc.uid == uid) {
                acc.copy(
                    isLive = isLive,
                    avatar = avatar ?: acc.avatar,
                    name = name ?: acc.name,
                    email = email ?: acc.email,
                    pages = pages ?: acc.pages
                )
            } else acc
        }
        save(context, current)
    }

    @Synchronized
    private fun save(context: Context, accounts: List<FacebookAccount>) {
        memoryCache = accounts.toMutableList()
        try {
            val jsonArr = JSONArray()
            accounts.forEach { acc ->
                val obj = JSONObject()
                obj.put("uid", acc.uid)
                obj.put("name", acc.name)
                obj.put("link", acc.link)
                obj.put("note", acc.note)
                obj.put("phone", acc.phone)
                obj.put("bio", acc.bio)
                obj.put("isLive", acc.isLive)
                obj.put("avatar", acc.avatar)
                obj.put("cover", acc.cover)
                obj.put("email", acc.email)
                obj.put("password", acc.password)

                if (acc.pages.isNotEmpty()) {
                    val pagesArr = JSONArray()
                    acc.pages.forEach { p ->
                        val pObj = JSONObject()
                        pObj.put("pageId", p.pageId)
                        pObj.put("pageName", p.pageName)
                        pObj.put("pageToken", p.pageToken)
                        pObj.put("additionalProfileId", p.additionalProfileId)
                        pObj.put("avatar", p.avatar)
                        pObj.put("cover", p.cover)
                        pObj.put("isLive", p.isLive)
                        pagesArr.put(pObj)
                    }
                    obj.put("pages", pagesArr)
                }
                jsonArr.put(obj)
            }
            prefs(context).edit().putString(KEY_ACCOUNTS, jsonArr.toString()).commit()
        } catch (_: Exception) {
            val raw = accounts.joinToString(ENTRY_SEPARATOR) { acc ->
                listOf(
                    acc.uid,
                    acc.name,
                    acc.link,
                    acc.note,
                    acc.phone,
                    acc.bio,
                    if (acc.isLive) "live" else "die",
                    acc.avatar,
                    acc.email,
                    acc.password
                ).joinToString(FIELD_SEPARATOR)
            }
            prefs(context).edit().putString(KEY_ACCOUNTS, raw).commit()
        }
    }
}
