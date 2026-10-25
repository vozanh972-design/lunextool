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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    private const val SIGN_KEY = "follow4follow_key"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-A556B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun generateSignature(method: String, path: String, timestamp: Long): String {
        val payload = "$timestamp:${method.uppercase()}:$path"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(SIGN_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val bytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseCoins(balanceStr: String?): Long {
        if (balanceStr.isNullOrBlank()) return 0L
        val clean = balanceStr.replace("coin", "", ignoreCase = true)
            .replace(",", "")
            .replace(".", "")
            .trim()
        return clean.toLongOrNull() ?: 0L
    }

    /**
     * Đăng nhập bằng Username & Password của Follow4Follow
     */
    suspend fun loginWithCredentials(user: String, pass: String): F4fApiResult<F4fUserInfo> = withContext(Dispatchers.IO) {
        val path = "/user/auth/login"
        val ts = System.currentTimeMillis() / 1000
        val sign = generateSignature("POST", path, ts)

        val bodyJson = JsonObject().apply {
            addProperty("username", user.trim())
            addProperty("password", pass.trim())
        }
        val requestBody = bodyJson.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(requestBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Origin", "https://follow4follow.net")
            .header("Referer", "https://follow4follow.net/")
            .header("User-Agent", USER_AGENT)
            .header("x-ts", ts.toString())
            .header("x-sign", sign)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
                    ?: return@withContext F4fApiResult.Error("Phản hồi không hợp lệ từ máy chủ")

                val statusCode = json.get("status_code")?.asInt ?: response.code
                val message = json.get("message")?.takeIf { !it.isJsonNull }?.asString

                if (statusCode != 200 || !response.isSuccessful) {
                    return@withContext F4fApiResult.Error(message ?: "Đăng nhập thất bại (Mã lỗi: $statusCode)")
                }

                val data = json.getAsJsonObject("data")
                    ?: return@withContext F4fApiResult.Error("Không tìm thấy dữ liệu trả về")

                val token = data.get("access_token")?.asString.orEmpty()
                val userObj = data.getAsJsonObject("user")
                val username = userObj?.get("username")?.asString ?: user
                val balanceStr = userObj?.get("balance")?.asString ?: "0 coin"
                val id = userObj?.get("id")?.asLong ?: 0L
                val levelName = userObj?.getAsJsonObject("level")?.get("name")?.asString ?: "Thành viên"

                F4fApiResult.Success(
                    F4fUserInfo(
                        id = id,
                        username = username,
                        balanceText = balanceStr,
                        balancePoints = parseCoins(balanceStr),
                        token = token,
                        levelName = levelName
                    )
                )
            }
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi kết nối tới Follow4Follow")
        }
    }

    /**
     * Lấy thông tin người dùng từ Bearer Token
     */
    suspend fun fetchMe(rawToken: String): F4fApiResult<F4fUserInfo> = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return@withContext F4fApiResult.Error("Chưa nhập token")

        val path = "/user/auth/me"
        val ts = System.currentTimeMillis() / 1000
        val sign = generateSignature("GET", path, ts)

        val request = Request.Builder()
            .url("$BASE_URL$path")
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $cleanToken")
            .header("Origin", "https://follow4follow.net")
            .header("Referer", "https://follow4follow.net/")
            .header("User-Agent", USER_AGENT)
            .header("x-ts", ts.toString())
            .header("x-sign", sign)
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
        val path = "/user/account/list"
        val fullUrl = "$BASE_URL$path?type=$type&page=1"
        val ts = System.currentTimeMillis() / 1000
        val sign = generateSignature("GET", path, ts)

        val request = Request.Builder()
            .url(fullUrl)
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $cleanToken")
            .header("Origin", "https://follow4follow.net")
            .header("Referer", "https://follow4follow.net/")
            .header("User-Agent", USER_AGENT)
            .header("x-ts", ts.toString())
            .header("x-sign", sign)
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
                        val id = item.get("id")?.asString ?: item.get("account_id")?.asString.orEmpty()
                        val uName = item.get("username")?.asString ?: item.get("name")?.asString.orEmpty()
                        val status = item.get("status")?.asString ?: "Hoạt động"
                        val isLive = item.get("is_active")?.asBoolean ?: (status.equals("active", ignoreCase = true) || status.equals("Hoạt động", ignoreCase = true))
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
        } catch (e: Exception) {
            F4fApiResult.Error(e.message ?: "Lỗi tải danh sách tài khoản F4F")
        }
    }
}
