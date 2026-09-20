package com.cayxu.app.data.repository

import com.cayxu.app.data.api.XsmmRetrofitClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaTypeOrNull

data class XsmmAccount(
    val id: String,
    val type: String,
    val accountId: String,
    val name: String,
    val linkAccount: String,
    val isActive: Boolean
)

sealed class XsmmAccountsResult {
    data class Success(val accounts: List<XsmmAccount>, val totalPages: Int) : XsmmAccountsResult()
    data class Error(val message: String) : XsmmAccountsResult()
}

sealed class XsmmAddAccountResult {
    data class Success(val account: XsmmAccount) : XsmmAddAccountResult()
    data class Error(val message: String) : XsmmAddAccountResult()
}

data class XsmmSyncAccountResult(
    val isSuccess: Boolean,
    val uid: String,
    val internalId: String,
    val message: String
)

/**
 * Gọi THẬT các API "GET/POST /api/taskapi/accounts" của XSMM - lấy danh sách acc đã thêm
 * (để biết acc TikTok nào ĐÃ có trên XSMM, tự ẩn nút "Thêm"), và thêm acc mới (bấm "Thêm").
 */
object XsmmAccountsRepository {

    private fun authHeader(rawToken: String): String {
        val token = rawToken.trim()
        return if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"
    }

    private fun readError(errorBody: String?, fallback: String): String {
        if (errorBody.isNullOrBlank()) return fallback
        return runCatching {
            val jsonElement = JsonParser.parseString(errorBody)
            if (jsonElement.isJsonObject) {
                val obj = jsonElement.asJsonObject
                val err = obj.get("error")
                if (err != null && err.isJsonPrimitive) return@runCatching err.asString
                if (err != null && err.isJsonObject) {
                    val subMsg = err.asJsonObject.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    if (!subMsg.isNullOrBlank()) return@runCatching subMsg
                }
                val msg = obj.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                if (!msg.isNullOrBlank()) return@runCatching msg
                val detail = obj.get("detail")?.takeIf { it.isJsonPrimitive }?.asString
                if (!detail.isNullOrBlank()) return@runCatching detail
                val m = obj.get("msg")?.takeIf { it.isJsonPrimitive }?.asString
                if (!m.isNullOrBlank()) return@runCatching m
            }
            errorBody.take(150)
        }.getOrNull() ?: fallback
    }

    private fun parseAccount(obj: JsonObject): XsmmAccount {
        val rawAccId = obj.get("account_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val rawId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            .ifBlank { obj.get("_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty() }
        val finalAccountId = rawAccId.ifBlank { rawId }
        return XsmmAccount(
            id = rawId,
            type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            accountId = finalAccountId,
            name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            linkAccount = obj.get("link_account")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            isActive = obj.get("is_active")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        )
    }

    /** [accountType]: "facebook"/"tiktok"/"instagram"/"thread"/"youtube"/"google". */
    suspend fun getAccounts(
        rawToken: String,
        accountType: String? = null,
        search: String? = null,
        page: Int? = null
    ): XsmmAccountsResult {
        try {
            val response = XsmmRetrofitClient.api.getAccounts(authHeader(rawToken), search, page, accountType)
            if (response.isSuccessful) {
                val json = response.body()
                val errorField = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                if (errorField.isNullOrBlank()) {
                    val accountsArray = json?.get("accounts")?.takeIf { it.isJsonArray }?.asJsonArray
                    val accounts = accountsArray?.mapNotNull { el ->
                        if (el.isJsonObject) parseAccount(el.asJsonObject) else null
                    }.orEmpty()
                    if (accounts.isNotEmpty()) {
                        val filteredAccounts = if (!accountType.isNullOrBlank()) {
                            accounts.filter { it.type.equals(accountType, ignoreCase = true) }
                        } else accounts
                        val totalPages = json?.get("total_pages")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
                        return XsmmAccountsResult.Success(filteredAccounts, totalPages)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback trực tiếp sang endpoint web chuẩn: https://xsmm.net/api/accounts
        return try {
            val urlBuilder = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("xsmm.net")
                .addPathSegments("api/accounts")
            if (page != null) urlBuilder.addQueryParameter("page", page.toString())
            if (!accountType.isNullOrBlank()) urlBuilder.addQueryParameter("account_type", accountType)
            if (!search.isNullOrBlank()) urlBuilder.addQueryParameter("search", search)

            val req = okhttp3.Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", authHeader(rawToken))
                .get()
                .build()

            XsmmRetrofitClient.okHttpClient.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                val accountsArray = json?.get("accounts")?.takeIf { it.isJsonArray }?.asJsonArray
                val accounts = accountsArray?.mapNotNull { el ->
                    if (el.isJsonObject) parseAccount(el.asJsonObject) else null
                }.orEmpty()
                val filtered = if (!accountType.isNullOrBlank()) {
                    accounts.filter { it.type.equals(accountType, ignoreCase = true) }
                } else accounts
                val totalPages = json?.get("total_pages")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
                XsmmAccountsResult.Success(filtered, totalPages)
            }
        } catch (e: Exception) {
            XsmmAccountsResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** Lấy toàn bộ danh sách tài khoản của [accountType] trên XSMM, tự động duyệt tất cả các trang. */
    suspend fun getAllAccounts(rawToken: String, accountType: String? = null): List<XsmmAccount> {
        val allList = mutableListOf<XsmmAccount>()
        var currentPage = 1
        var totalPages = 1
        while (currentPage <= totalPages && currentPage <= 20) {
            val res = getAccounts(rawToken, accountType = accountType, page = currentPage)
            if (res is XsmmAccountsResult.Success) {
                allList.addAll(res.accounts)
                totalPages = res.totalPages.coerceAtLeast(1)
                currentPage++
            } else {
                break
            }
        }
        return allList
    }

    /** Lấy toàn bộ danh sách tài khoản Facebook từ XSMM (GET /api/taskapi/accounts?account_type=facebook), tự động duyệt tất cả các trang. */
    suspend fun getFacebookAccounts(rawToken: String): List<XsmmAccount> {
        return getAllAccounts(rawToken, accountType = "facebook")
    }

    /**
     * Đồng bộ và kích hoạt nick Facebook theo đúng 4 API chuẩn của XSMM:
     * 1. GET /api/taskapi/accounts/active -> nếu đúng UID đang active thì dùng luôn.
     * 2. GET /api/taskapi/accounts?account_type=facebook -> lấy danh sách tài khoản theo trang.
     * 3. Nếu đã có trong danh sách -> PUT /api/taskapi/accounts/{id}/set-active để đặt làm nick chạy.
     * 4. Nếu chưa có -> POST /api/taskapi/accounts (active: true) để thêm và đặt làm nick chạy luôn.
     */
    suspend fun syncAndActivateFacebookAccount(rawToken: String, targetUid: String): XsmmSyncAccountResult {
        val cleanUid = targetUid.trim()
        if (cleanUid.isBlank()) return XsmmSyncAccountResult(false, "", "", "UID Facebook trống")

        // 1. Kiểm tra tài khoản đang active trên XSMM (GET /api/taskapi/accounts/active)
        val currentActive = getActiveAccount(rawToken)
        if (currentActive != null && (currentActive.accountId.trim() == cleanUid || currentActive.linkAccount.contains(cleanUid))) {
            return XsmmSyncAccountResult(
                isSuccess = true,
                uid = currentActive.accountId.ifBlank { cleanUid },
                internalId = currentActive.id,
                message = "Đang active trên XSMM"
            )
        }

        // 2. Lấy danh sách tài khoản Facebook từ XSMM (GET /api/taskapi/accounts?account_type=facebook)
        val allFbAccounts = getFacebookAccounts(rawToken)
        val matchedAcc = allFbAccounts.firstOrNull {
            it.accountId.trim() == cleanUid || it.linkAccount.contains(cleanUid)
        }

        if (matchedAcc != null) {
            // 3. Đã có trong danh sách -> PUT /api/taskapi/accounts/{id}/set-active
            val setOk = setActiveAccount(rawToken, matchedAcc.id)
            return XsmmSyncAccountResult(
                isSuccess = setOk,
                uid = matchedAcc.accountId.ifBlank { cleanUid },
                internalId = matchedAcc.id,
                message = if (setOk) "Đã kích hoạt nick làm mặc định" else "Lỗi kích hoạt nick trên XSMM"
            )
        } else {
            // 4. Chưa có trong danh sách -> POST /api/taskapi/accounts kèm "active": true
            val addRes = addFacebookAccount(rawToken, cleanUid, setActive = true)
            return when (addRes) {
                is XsmmAddAccountResult.Success -> {
                    if (addRes.account.id.isNotBlank()) {
                        setActiveAccount(rawToken, addRes.account.id)
                    }
                    XsmmSyncAccountResult(
                        isSuccess = true,
                        uid = addRes.account.accountId.ifBlank { cleanUid },
                        internalId = addRes.account.id,
                        message = "Đã thêm và kích hoạt nick mới trên XSMM"
                    )
                }
                is XsmmAddAccountResult.Error -> {
                    // Nếu lỗi báo đã tồn tại, quét lại danh sách để lấy id và kích hoạt
                    val reloaded = getFacebookAccounts(rawToken)
                    val found = reloaded.firstOrNull { it.accountId.trim() == cleanUid || it.linkAccount.contains(cleanUid) }
                    if (found != null) {
                        val setOk = setActiveAccount(rawToken, found.id)
                        XsmmSyncAccountResult(
                            isSuccess = setOk,
                            uid = found.accountId.ifBlank { cleanUid },
                            internalId = found.id,
                            message = if (setOk) "Đã kích hoạt nick làm mặc định" else "Lỗi kích hoạt nick trên XSMM"
                        )
                    } else {
                        XsmmSyncAccountResult(
                            isSuccess = false,
                            uid = cleanUid,
                            internalId = "",
                            message = addRes.message
                        )
                    }
                }
            }
        }
    }

    /** Kiểm tra 1 @handle TikTok đã có trong danh sách acc XSMM chưa (dùng search để lọc
     *  gọn phía server, rồi so khớp CHÍNH XÁC @handle trong link_account để chắc chắn). */
    suspend fun isTikTokHandleLinked(rawToken: String, handle: String): Boolean {
        val normalizedHandle = handle.trim().removePrefix("@").lowercase()
        if (normalizedHandle.isBlank()) return false
        val result = getAccounts(rawToken, accountType = "tiktok", search = normalizedHandle)
        val accounts = (result as? XsmmAccountsResult.Success)?.accounts.orEmpty()
        return accounts.any { acc ->
            acc.linkAccount.substringAfterLast("@").trim('/').lowercase() == normalizedHandle
        }
    }

    /** Thêm acc TikTok mới vào XSMM theo @handle - [setActive] = true nghĩa là đặt luôn làm
     *  "nick chạy" ngay sau khi thêm (theo đúng tài liệu API: field "active": true). */
    suspend fun addTikTokAccount(rawToken: String, handle: String, setActive: Boolean = true): XsmmAddAccountResult {
        val normalizedHandle = handle.trim().removePrefix("@")
        if (normalizedHandle.isBlank()) return XsmmAddAccountResult.Error("Thiếu @handle để thêm")

        val body = JsonObject().apply {
            addProperty("type", "tiktok")
            addProperty("link_account", "https://www.tiktok.com/@$normalizedHandle")
            addProperty("active", setActive)
        }

        return try {
            val response = XsmmRetrofitClient.api.addAccount(authHeader(rawToken), body)
            if (!response.isSuccessful) {
                return XsmmAddAccountResult.Error(readError(response.errorBody()?.string(), "Lỗi thêm tài khoản (mã HTTP: ${response.code()})"))
            }
            val json = response.body()
            val errorField = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) return XsmmAddAccountResult.Error(errorField)

            // Response thêm mới có thể trả thẳng object account, hoặc bọc trong "account" -
            // thử cả 2 kiểu cho chắc.
            val accountObj = json?.takeIf { it.has("id") }
                ?: json?.get("account")?.takeIf { it.isJsonObject }?.asJsonObject

            if (accountObj != null) {
                XsmmAddAccountResult.Success(parseAccount(accountObj))
            } else {
                // Không rõ cấu trúc response nhưng không có lỗi -> coi như thành công, dựng
                // tạm 1 account object từ chính dữ liệu vừa gửi lên.
                XsmmAddAccountResult.Success(
                    XsmmAccount(
                        id = "",
                        type = "tiktok",
                        accountId = "",
                        name = normalizedHandle,
                        linkAccount = "https://www.tiktok.com/@$normalizedHandle",
                        isActive = setActive
                    )
                )
            }
        } catch (e: Exception) {
            XsmmAddAccountResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** Thêm acc Instagram mới vào XSMM theo username/handle */
    suspend fun addInstagramAccount(rawToken: String, username: String): XsmmAddAccountResult {
        val cleanName = username.trim().removePrefix("@").trim('/')
        if (cleanName.isBlank()) return XsmmAddAccountResult.Error("Thiếu username Instagram để thêm")

        val body = JsonObject().apply {
            addProperty("type", "instagram")
            addProperty("link_account", "https://www.instagram.com/$cleanName")
        }

        return try {
            val response = XsmmRetrofitClient.api.addAccount(authHeader(rawToken), body)
            if (!response.isSuccessful) {
                return XsmmAddAccountResult.Error(readError(response.errorBody()?.string(), "Lỗi thêm tài khoản Instagram (mã HTTP: ${response.code()})"))
            }
            val json = response.body()
            val errorField = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) return XsmmAddAccountResult.Error(errorField)

            val accountObj = json?.takeIf { it.has("id") }
                ?: json?.get("account")?.takeIf { it.isJsonObject }?.asJsonObject

            val hasValidId = accountObj?.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true ||
                accountObj?.get("account_id")?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true

            if (accountObj != null && hasValidId) {
                XsmmAddAccountResult.Success(parseAccount(accountObj))
            } else {
                val err = errorField
                    ?: json?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: if (json != null) json.toString() else "XSMM không trả về ID tài khoản"
                XsmmAddAccountResult.Error(err)
            }
        } catch (e: Exception) {
            XsmmAddAccountResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /**
     * Thêm tài khoản Facebook mới vào XSMM theo UID hoặc link_account.
     * Body: {"type": "facebook", "link_account": "https://facebook.com/username", "active": true}
     */
    suspend fun addFacebookAccount(
        rawToken: String,
        uid: String,
        linkAccount: String? = null,
        setActive: Boolean = true
    ): XsmmAddAccountResult {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return XsmmAddAccountResult.Error("Thiếu UID Facebook để thêm")

        val targetUrl = if (cleanUid.all { it.isDigit() }) {
            "https://www.facebook.com/profile.php?id=$cleanUid"
        } else {
            val raw = linkAccount?.trim().takeIf { !it.isNullOrBlank() } ?: cleanUid
            if (raw.startsWith("http", ignoreCase = true)) raw
            else "https://www.facebook.com/$raw"
        }

        val body = JsonObject().apply {
            addProperty("type", "facebook")
            addProperty("link_account", targetUrl)
            addProperty("active", setActive)
        }

        return try {
            val response = XsmmRetrofitClient.api.addAccount(authHeader(rawToken), body)

            // HTTP lỗi → đọc error body
            if (!response.isSuccessful) {
                return XsmmAddAccountResult.Error(
                    readError(response.errorBody()?.string(), "Lỗi ${response.code()}")
                )
            }

            val json = response.body() ?: return XsmmAddAccountResult.Error("Phản hồi rỗng từ XSMM")

            // API docs: lỗi trả {"error": "Chi tiết lỗi"}
            val errorField = json.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) return XsmmAddAccountResult.Error(errorField)

            // API docs: thành công trả full account object hoặc bọc trong account
            val accountObj = json.takeIf { it.has("id") || it.has("account_id") }
                ?: json.get("account")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: json
            val account = parseAccount(accountObj)
            val finalAccount = if (account.accountId.isBlank()) account.copy(accountId = cleanUid) else account
            XsmmAddAccountResult.Success(finalAccount)

        } catch (e: Exception) {
            XsmmAddAccountResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** Lấy tài khoản đang active trên XSMM (GET /api/taskapi/accounts/active). */
    suspend fun getActiveAccount(rawToken: String): XsmmAccount? {
        return try {
            val response = XsmmRetrofitClient.api.getActiveAccount(authHeader(rawToken))
            if (response.isSuccessful) {
                val json = response.body() ?: return null
                val accObj = if (json.has("account") && json.get("account").isJsonObject) {
                    json.getAsJsonObject("account")
                } else json
                if (accObj.has("id") || accObj.has("account_id") || accObj.has("_id")) {
                    parseAccount(accObj)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** Đặt 1 acc (theo id nội bộ trên XSMM) làm "nick chạy" (PUT /api/taskapi/accounts/{id}/set-active). */
    suspend fun setActiveAccount(rawToken: String, accountId: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (accountId.isBlank()) return@withContext false
        try {
            val response = XsmmRetrofitClient.api.setActiveAccount(authHeader(rawToken), accountId, JsonObject())
            if (response.isSuccessful) return@withContext true
        } catch (_: Exception) {}

        // Fallback trực tiếp bằng OkHttp với cả 2 URL (/api/accounts và /api/taskapi/accounts)
        try {
            val mediaType = "application/json".toMediaTypeOrNull()
            val urls = listOf(
                "https://xsmm.net/api/accounts/$accountId/set-active",
                "https://xsmm.net/api/taskapi/accounts/$accountId/set-active"
            )
            for (targetUrl in urls) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(targetUrl)
                        .header("Authorization", authHeader(rawToken))
                        .put(okhttp3.RequestBody.create(mediaType, "{}"))
                        .build()
                    val success = XsmmRetrofitClient.okHttpClient.newCall(req).execute().use { it.isSuccessful }
                    if (success) return@withContext true
                } catch (_: Exception) {}
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
