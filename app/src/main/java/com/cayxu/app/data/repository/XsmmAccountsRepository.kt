package com.cayxu.app.data.repository

import com.cayxu.app.data.api.XsmmRetrofitClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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
        return try {
            // Với Facebook: gọi thẳng API web /api/accounts theo đúng web XSMM
            val response = if (accountType.equals("facebook", ignoreCase = true)) {
                XsmmRetrofitClient.api.getAccountsWeb(authHeader(rawToken), search, page ?: 1, "facebook")
            } else {
                XsmmRetrofitClient.api.getAccounts(authHeader(rawToken), search, page, accountType)
            }
            if (!response.isSuccessful) {
                return XsmmAccountsResult.Error(readError(response.errorBody()?.string(), "Lỗi lấy danh sách (mã HTTP: ${response.code()})"))
            }
            val json = response.body()
            val errorField = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) return XsmmAccountsResult.Error(errorField)

            val accountsArray = json?.get("accounts")?.takeIf { it.isJsonArray }?.asJsonArray
            val accounts = accountsArray?.mapNotNull { el ->
                if (el.isJsonObject) parseAccount(el.asJsonObject) else null
            }.orEmpty()
            val filteredAccounts = if (!accountType.isNullOrBlank()) {
                accounts.filter { it.type.equals(accountType, ignoreCase = true) }
            } else accounts
            val totalPages = json?.get("total_pages")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1

            XsmmAccountsResult.Success(filteredAccounts, totalPages)
        } catch (e: Exception) {
            XsmmAccountsResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** Tìm kiếm tài khoản Facebook trên XSMM theo UID (dùng endpoint search của API accounts2).
     *  Trả về [XsmmAccount] nếu tìm thấy, hoặc null nếu không tồn tại trên XSMM. */
    suspend fun searchFacebookAccount(rawToken: String, uid: String): XsmmAccount? {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return null
        val result = getAccounts(rawToken, accountType = "facebook", search = cleanUid)
        val accounts = (result as? XsmmAccountsResult.Success)?.accounts.orEmpty()
        return accounts.firstOrNull { it.accountId.trim() == cleanUid }
    }

    /** Kiểm tra nhanh 1 UID Facebook (acc chính hoặc Page) đã có trên XSMM chưa qua search. */
    suspend fun isFacebookUidLinked(rawToken: String, uid: String): Boolean {
        return searchFacebookAccount(rawToken, uid) != null
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
     * Kiểm tra tài khoản Facebook (theo UID) đã có trên XSMM chưa.
     */
    suspend fun isFacebookAccountLinked(rawToken: String, uid: String): Boolean {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return false
        // Dùng search=uid để lọc server-side (giống website XSMM)
        val result = getAccounts(rawToken, accountType = "facebook", search = cleanUid)
        val accounts = (result as? XsmmAccountsResult.Success)?.accounts.orEmpty()
            .filter { it.type.equals("facebook", ignoreCase = true) }
        // Chỉ check account_id - API docs: account_id là Facebook UID
        // KHÔNG check link_account vì link_account là URL gốc gửi lên, không phải UID thật
        return accounts.any { acc -> acc.accountId.trim() == cleanUid }
    }

    /**
     * Thêm tài khoản Facebook mới vào XSMM theo UID hoặc link_account.
     * Body: {"type": "facebook", "link_account": "https://facebook.com/username"}
     * TUYỆT ĐỐI KHÔNG thêm ảo: chỉ trả Success khi XSMM trả về account có id/account_id hợp lệ,
     * hoặc nick thực sự xuất hiện trong danh sách accounts của XSMM sau khi gọi.
     */
    suspend fun addFacebookAccount(
        rawToken: String,
        uid: String,
        linkAccount: String? = null
    ): XsmmAddAccountResult {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return XsmmAddAccountResult.Error("Thiếu UID Facebook để thêm")

        // API docs: link_account phải là URL dạng https://facebook.com/username
        // Với numeric UID → dùng profile.php?id=
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

            // API docs: thành công trả full account object - parse luôn
            val account = parseAccount(json)
            if (account.id.isBlank() || account.accountId.isBlank()) {
                return XsmmAddAccountResult.Error("Server không trả về tài khoản hợp lệ")
            }
            XsmmAddAccountResult.Success(account)

        } catch (e: Exception) {
            XsmmAddAccountResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }

    /** Đặt 1 acc (theo id) làm "nick chạy". */
    suspend fun setActiveAccount(rawToken: String, accountId: String): Boolean {
        return try {
            val response = XsmmRetrofitClient.api.setActiveAccount(authHeader(rawToken), accountId)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
