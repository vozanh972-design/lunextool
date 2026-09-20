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

    private fun parseAccount(obj: JsonObject): XsmmAccount = XsmmAccount(
        id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        accountId = obj.get("account_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        linkAccount = obj.get("link_account")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        isActive = obj.get("is_active")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
    )

    /** [accountType]: "facebook"/"tiktok"/"instagram"/"thread"/"youtube"/"google". */
    suspend fun getAccounts(
        rawToken: String,
        accountType: String? = null,
        search: String? = null,
        page: Int? = null
    ): XsmmAccountsResult {
        return try {
            val response = XsmmRetrofitClient.api.getAccounts(authHeader(rawToken), search, page, accountType)
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
        val result = getAccounts(rawToken, accountType = "facebook", search = cleanUid)
        val accounts = (result as? XsmmAccountsResult.Success)?.accounts.orEmpty()
            .filter { it.type.equals("facebook", ignoreCase = true) }
        return accounts.any { acc ->
            acc.accountId == cleanUid || acc.linkAccount.contains(cleanUid)
        }
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

        val targetLink = when {
            !linkAccount.isNullOrBlank() -> linkAccount.trim()
            cleanUid.startsWith("http://", ignoreCase = true) || cleanUid.startsWith("https://", ignoreCase = true) -> cleanUid
            cleanUid.contains("facebook.com") -> "https://$cleanUid"
            else -> "https://facebook.com/$cleanUid"
        }

        val body = JsonObject().apply {
            addProperty("type", "facebook")
            addProperty("link_account", targetLink)
        }

        return try {
            val response = XsmmRetrofitClient.api.addAccount(authHeader(rawToken), body)
            if (!response.isSuccessful) {
                return XsmmAddAccountResult.Error(readError(response.errorBody()?.string(), "Lỗi thêm tài khoản Facebook (mã HTTP: ${response.code()})"))
            }
            val json = response.body() ?: return XsmmAddAccountResult.Error("Phản hồi rỗng từ máy chủ XSMM")

            // Kiểm tra lỗi rõ ràng từ server trả về
            val errorField = json.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) return XsmmAddAccountResult.Error(errorField)

            val isFailedStatus = (json.get("status")?.takeIf { it.isJsonPrimitive }?.asString == "error" ||
                json.get("status")?.takeIf { it.isJsonPrimitive }?.asString == "fail" ||
                json.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == false)
            val statusMsg = (json.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                ?: json.get("msg")?.takeIf { it.isJsonPrimitive }?.asString)
            if (isFailedStatus && !statusMsg.isNullOrBlank()) {
                return XsmmAddAccountResult.Error(statusMsg)
            }

            val accountObj = when {
                json.has("id") && json.get("id").isJsonPrimitive && json.get("id").asString.isNotBlank() -> json
                json.has("account_id") && json.get("account_id").isJsonPrimitive && json.get("account_id").asString.isNotBlank() -> json
                json.get("account")?.isJsonObject == true -> json.getAsJsonObject("account")
                json.get("data")?.isJsonObject == true -> json.getAsJsonObject("data")
                else -> null
            }

            val validId = accountObj?.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true ||
                accountObj?.get("account_id")?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true

            if (accountObj != null && validId) {
                XsmmAddAccountResult.Success(parseAccount(accountObj))
            } else {
                // Kiểm tra lại danh sách thực tế từ XSMM để xác thực chắc chắn
                val verifyRes = getAccounts(rawToken, accountType = "facebook", search = cleanUid)
                if (verifyRes is XsmmAccountsResult.Success) {
                    val found = verifyRes.accounts.firstOrNull { acc ->
                        acc.type.equals("facebook", ignoreCase = true) &&
                            (acc.accountId == cleanUid || acc.linkAccount.contains(cleanUid))
                    }
                    if (found != null) {
                        return XsmmAddAccountResult.Success(found)
                    }
                }

                val failureReason = statusMsg
                    ?: json.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: json.get("msg")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: json.get("detail")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: "XSMM không trả về thông tin tài khoản hợp lệ"
                XsmmAddAccountResult.Error(failureReason)
            }
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
