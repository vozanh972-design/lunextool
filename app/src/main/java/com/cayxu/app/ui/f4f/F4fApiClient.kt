package com.cayxu.app.ui.f4f

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class F4fUserInfo(
    val id: Long = 0L,
    val username: String = "",
    val balanceText: String = "0 coin",
    val balancePoints: Long = 0L,
    val token: String = "",
    val levelName: String = "Thành viên",
)

data class F4fRemoteAccount(
    val id: String,
    val username: String,
    val status: String,
    val isLive: Boolean,
    val avatarUrl: String = "",
    val type: String = "",
)

sealed class F4fApiResult<out T> {
    data class Success<T>(val data: T) : F4fApiResult<T>()
    data class Error(val message: String) : F4fApiResult<Nothing>()
}

object F4fApiClient {
    private const val BASE_URL = "https://api.follow4follow.net"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-A556B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun parseCoins(balanceStr: String?): Long {
        if (balanceStr.isNullOrBlank()) return 0L
        val clean = balanceStr.replace("coin", "", ignoreCase = true)
            .replace(",", "")
            .replace(".", "")
            .trim()
        return clean.toLongOrNull() ?: 0L
    }

    /**
     * Lấy thông tin người dùng từ Bearer Token (chuỗi token lưu trong localStorage của web: 42870|...)
     */
    suspend fun fetchMe(rawToken: String): F4fApiResult<F4fUserInfo> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa nhập token")

        val path = "/user/auth/me"
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $cleanToken")
            .header("Origin", "https://follow4follow.net")
            .header("Referer", "https://follow4follow.net/")
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi không hợp lệ từ máy chủ")

                val statusCode = json.get("status_code")?.asInt ?: response.code
                val message = json.get("message")?.takeIf { !it.isJsonNull }?.asString

                if (statusCode != 200 || !response.isSuccessful) {
                    return@withContext F4fApiResult.Error(message ?: "Token không hợp lệ hoặc đã hết hạn")
                }

                val data = json.getAsJsonObject("data")
                    ?: return@withContext F4fApiResult.Error("Dữ liệu trống")

                val username = data.get("username")?.asString.orEmpty()
                val balanceStr = data.get("balance")?.asString ?: "0 coin"
                val id = data.get("id")?.asLong ?: 0L
                val levelName = data.getAsJsonObject("level")?.get("name")?.asString ?: "Thành viên"

                F4fApiResult.Success(
                    F4fUserInfo(
                        id = id,
                        username = username,
                        balanceText = balanceStr,
                        balancePoints = parseCoins(balanceStr),
                        token = cleanToken,
                        levelName = levelName
                    )
                )
            }
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối tới Follow4Follow")
        }
    }

    /**
     * Lấy danh sách tài khoản MXH từ Follow4Follow (type: "tiktok", "facebook", "instagram")
     */
    suspend fun fetchAccountList(rawToken: String, type: String): F4fApiResult<List<F4fRemoteAccount>> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Success(emptyList())

        val path = "/user/account/list"
        val fullUrl = "$BASE_URL$path?type=$type&page=1"

        val request = Request.Builder()
            .url(fullUrl)
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $cleanToken")
            .header("Origin", "https://follow4follow.net")
            .header("Referer", "https://follow4follow.net/")
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi danh sách tài khoản không hợp lệ")

                val dataObj = json.getAsJsonObject("data") ?: return@withContext F4fApiResult.Success(emptyList())
                val dataArr = dataObj.getAsJsonArray("data") ?: return@withContext F4fApiResult.Success(emptyList())

                val resultList = mutableListOf<F4fRemoteAccount>()
                for (elem in dataArr) {
                    if (elem.isJsonObject) {
                        val item = elem.asJsonObject
                        val id = item.get("id")?.asString
                            ?: item.get("account_id")?.asString
                            ?: item.get("link")?.asString.orEmpty()
                        val uName = item.get("link")?.asString
                            ?: item.get("username")?.asString
                            ?: item.get("name")?.asString
                            ?: item.get("account_id")?.asString.orEmpty()
                        val status = item.get("status")?.asString ?: "Hoạt động"
                        val isLive = item.get("is_active")?.asBoolean
                            ?: (status.equals("active", ignoreCase = true) || status.equals("Hoạt động", ignoreCase = true))
                        val avatar = item.get("avatar")?.asString.orEmpty()
                        resultList.add(
                            F4fRemoteAccount(
                                id = id,
                                username = uName,
                                status = status,
                                isLive = isLive,
                                avatarUrl = avatar,
                                type = type
                            )
                        )
                    }
                }
                F4fApiResult.Success(resultList)
            }
        } catch (e: java.net.UnknownHostException) {
            F4fApiResult.Error("Lỗi kết nối: Vui lòng kiểm tra lại mạng WiFi/4G")
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi tải danh sách tài khoản F4F")
        }
    }

    /**
     * Thêm/liên kết tài khoản MXH vào Follow4Follow:
     * POST /user/account/add
     * Body JSON: {"link": "<handle>", "g_recaptcha_response": "", "type": "<type>"}
     */
    suspend fun addAccount(rawToken: String, handle: String, type: String = "tiktok"): F4fApiResult<String> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa có token đăng nhập")
        val cleanHandle = handle.trim().removePrefix("@").trim()
        if (cleanHandle.isBlank()) return@withContext F4fApiResult.Error("Tên tài khoản không hợp lệ")

        val path = "/user/account/add"
        val jsonBody = JsonObject().apply {
            addProperty("link", cleanHandle)
            addProperty("g_recaptcha_response", "")
            addProperty("type", type)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(requestBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $cleanToken")
            .header("Origin", "https://follow4follow.net")
            .header("Referer", "https://follow4follow.net/")
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi không hợp lệ từ máy chủ")

                val statusCode = json.get("status_code")?.asInt ?: response.code
                val message = json.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

                if (statusCode == 200 && response.isSuccessful) {
                    F4fApiResult.Success(if (message.isNotBlank()) message else "Liên kết tài khoản thành công")
                } else {
                    F4fApiResult.Error(if (message.isNotBlank()) message else "Lỗi liên kết tài khoản (Mã $statusCode)")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            F4fApiResult.Error("Lỗi kết nối: Vui lòng kiểm tra lại mạng WiFi/4G")
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối khi liên kết tài khoản F4F")
        }
    }
}
