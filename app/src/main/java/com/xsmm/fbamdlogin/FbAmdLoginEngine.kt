package com.xsmm.fbamdlogin

import android.content.Context
import androidx.annotation.Keep
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * FbAmdLoginEngine
 * Extracted from FacebookAMD v1.4.7 DEX analysis
 *
 * Nguồn:
 *   - classes2.dex  → auth/login endpoint, machine_id, app_token
 *   - classes12.dex → CAA flow, checkpoint, 2FA handler
 *   - classes16.dex → AUTH_CREDENTIAL_COOKIE_SET
 *   - classes10.dex → try_num, SecuredAction, AutoConfStart
 *
 * ⚠️ LỆNH CẤM: CẤM ĐỤNG Instagram, TikTok, Comment, Follow, NhiemVuCheo, TuongTacCheo
 */

// ──────────────────────────────────────────────────────────────
// 1. HẰNG SỐ – lấy từ classes2.dex dòng L382
// ──────────────────────────────────────────────────────────────
@Keep
object FbAmdConstants {

    /** App Access Token nhúng cứng trong Katana – classes2.dex L382 */
    const val APP_TOKEN = "432827354065804|cb9c2da18237a3bb72878cc3a28019ad"

    /** App ID */
    const val APP_ID = "432827354065804"

    /** Endpoint chính – classes2.dex L340 */
    const val AUTH_LOGIN_URL = "https://b-api.facebook.com/method/auth.login"

    /** Bloks Graph – classes2.dex L41 */
    const val B_GRAPH_URL = "https://b-graph.facebook.com"

    /** Checkpoint resolve URL – classes12.dex L663 */
    const val CHECKPOINT_URL = "https://b-api.facebook.com/restserver.php"

    /** User-Agent Katana */
    const val USER_AGENT =
        "Facebook/548.1.0.51.64 Android/28 (samsung; SM-G975F; samsung; arm64-v8a; en_US)"

    /** Timeout ms */
    const val TIMEOUT_MS = 15_000

    /** SharedPreferences key */
    const val PREFS_NAME = "fb_amd_prefs"
    const val KEY_MACHINE_ID = "machine_id"
}

// ──────────────────────────────────────────────────────────────
// 2. DATA CLASSES – kết quả login
// ──────────────────────────────────────────────────────────────

/**
 * Kết quả login thành công
 * @param uid        UID Facebook
 * @param token      Access Token EAAA...
 * @param machineId  machine_id để dùng cho lần login tiếp theo (bỏ qua thiết bị)
 * @param cookies    Cookie string (c_user=...; xs=...; datr=...; sb=...)
 * @param name       Tên Facebook sau khi verify (nếu có)
 */
@Keep
data class FbLoginResult(
    val uid: String,
    val token: String,
    val machineId: String,
    val cookies: String,
    var name: String = ""
)

/**
 * Trạng thái login
 */
@Keep
sealed class FbLoginState {
    data class Success(val result: FbLoginResult) : FbLoginState()
    data class CheckpointSms(val checkpointSession: String, val uid: String) : FbLoginState()
    data class CheckpointEmail(val checkpointSession: String, val uid: String) : FbLoginState()
    data class CheckpointTwoFa(val checkpointSession: String, val uid: String) : FbLoginState()
    data class Error(val code: Int, val message: String) : FbLoginState()
}

// ──────────────────────────────────────────────────────────────
// 3. MAIN ENGINE
// ──────────────────────────────────────────────────────────────

@Keep
class FbAmdLoginEngine {

    companion object {
        /**
         * Lưu machine_id vào SharedPreferences
         */
        fun saveMachineId(context: Context, machineId: String) {
            if (machineId.isNotBlank()) {
                val prefs = context.applicationContext.getSharedPreferences(
                    FbAmdConstants.PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                prefs.edit().putString(FbAmdConstants.KEY_MACHINE_ID, machineId).apply()
            }
        }

        /**
         * Lấy machine_id đã lưu từ SharedPreferences
         */
        fun getSavedMachineId(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences(
                FbAmdConstants.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            return prefs.getString(FbAmdConstants.KEY_MACHINE_ID, "") ?: ""
        }

        /**
         * Hỗ trợ tách định dạng dòng nhập liệu uid|pass|2fa|cookie
         */
        fun parseInputLine(line: String): Triple<String, String, String> {
            val parts = line.trim().split("|")
            return when {
                parts.size >= 4 -> Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
                parts.size == 3 -> Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
                parts.size == 2 -> Triple(parts[0].trim(), parts[1].trim(), "")
                else -> Triple(line.trim(), "", "")
            }
        }

        /**
         * Định dạng chuỗi hiển thị chuẩn theo yêu cầu
         */
        fun formatResult(r: FbLoginResult): String = buildString {
            appendLine("UID: ${r.uid}")
            appendLine("TOKEN EAAA: ${r.token}")
            appendLine("COOKIE: ${r.cookies}")
        }
    }

    /**
     * BƯỚC 1 – Login bằng email/phone + password
     *
     * Phân tích từ:
     *   - classes2.dex: auth/login, machine_id, auth_device_based_login_credentials
     *   - classes12.dex: login_source, login_request_try_num, cross_session_login
     *   - classes10.dex: try_num
     *
     * @param emailOrPhone  Email hoặc SĐT
     * @param password      Mật khẩu
     * @param machineId     machine_id cũ (nếu đã có từ lần trước, để tránh checkpoint)
     * @param tryNum        Số lần thử (mặc định 1)
     * @return FbLoginState
     */
    fun login(
        emailOrPhone: String,
        password: String,
        machineId: String = "",
        tryNum: Int = 1
    ): FbLoginState {
        val params = mutableMapOf(
            // ── Core params từ classes2.dex auth/login ──
            "adid" to generateFakeAdid(),
            "email" to emailOrPhone.trim(),
            "password" to password,
            "format" to "json",
            "generate_session_cookies" to "1",
            "generate_analytics_claim" to "1",
            "currently_logged_in_userid" to "0",
            "irisSeqID" to "1",
            "locale" to "en_US",
            "client_country_code" to "US",
            "fb_api_req_friendly_name" to "authenticate",
            "fb_api_caller_class" to "AuthOperations",
            // ── machine_id từ classes2.dex L352 ──
            "machine_id" to machineId.trim(),
            // ── try_num từ classes10.dex L648 ──
            "try_num" to tryNum.toString(),
            // ── login_source từ classes12.dex L761 ──
            "login_source" to "DEFAULT",
            // ── App token từ classes2.dex L382 ──
            "access_token" to FbAmdConstants.APP_TOKEN
        )

        return executePost(FbAmdConstants.AUTH_LOGIN_URL, params, "")
    }

    /**
     * BƯỚC 2A – Gửi mã SMS/Email 2FA để giải checkpoint
     *
     * Phân tích từ:
     *   - classes12.dex: CONF_SMS_CODE, email_code, Checkpoints/TwoFA error payload
     *   - classes12.dex L663: Checkpoint login redirect expected uid but got
     *
     * @param checkpointSession  Session checkpoint từ bước 1
     * @param uid                UID từ bước 1
     * @param code               Mã OTP (SMS hoặc Email)
     * @param codeType           "sms_code" hoặc "email_code"
     */
    fun submitCheckpointCode(
        checkpointSession: String,
        uid: String,
        code: String,
        codeType: String = "sms_code"
    ): FbLoginState {
        val params = mutableMapOf(
            "uid" to uid.trim(),
            "action" to "CONFIRM",
            codeType to code.trim(),
            "format" to "json",
            "generate_session_cookies" to "1",
            "checkpoint_session" to checkpointSession.trim(),
            "fb_api_req_friendly_name" to "checkpoint",
            "access_token" to FbAmdConstants.APP_TOKEN
        )

        return executePost(FbAmdConstants.CHECKPOINT_URL, params, "")
    }

    /**
     * BƯỚC 2B – Gửi mã 2FA từ app Authenticator
     *
     * Phân tích từ:
     *   - classes12.dex: Checkpoints/TwoFA error payload is null
     *   - classes9.dex: 2Fa (string pool)
     *
     * @param checkpointSession  Session checkpoint
     * @param uid                UID
     * @param twoFaCode          Mã 6 chữ số từ Authenticator
     */
    fun submitTwoFaCode(
        checkpointSession: String,
        uid: String,
        twoFaCode: String
    ): FbLoginState {
        val params = mutableMapOf(
            "uid" to uid.trim(),
            "action" to "CONFIRM",
            "twofactor_code" to twoFaCode.trim(),
            "format" to "json",
            "generate_session_cookies" to "1",
            "checkpoint_session" to checkpointSession.trim(),
            "fb_api_req_friendly_name" to "checkpoint_twofa",
            "access_token" to FbAmdConstants.APP_TOKEN
        )

        return executePost(FbAmdConstants.CHECKPOINT_URL, params, "")
    }

    /**
     * BƯỚC VERIFY – Kiểm tra Access Token thu được bằng Graph API
     * GET https://graph.facebook.com/me?fields=id,name&access_token={token}
     *
     * Trả về Pair(isPass, message)
     */
    fun verifyToken(token: String): Pair<Boolean, String> {
        if (token.isBlank()) return Pair(false, "Token rỗng")
        return try {
            val url = URL("https://graph.facebook.com/me?fields=id,name&access_token=${URLEncoder.encode(token, "UTF-8")}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", FbAmdConstants.USER_AGENT)
            }
            val respCode = conn.responseCode
            val stream = if (respCode == 200) conn.inputStream else conn.errorStream
            val respBody = stream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (respCode == 200) {
                val json = JSONObject(respBody)
                val id = json.optString("id", "")
                val name = json.optString("name", "")
                if (id.isNotEmpty()) {
                    Pair(true, "PASS: $name ($id)")
                } else {
                    Pair(false, "Không tìm thấy UID trong response: $respBody")
                }
            } else {
                Pair(false, "HTTP $respCode: $respBody")
            }
        } catch (e: Exception) {
            Pair(false, "Lỗi kiểm tra token: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────────────────
    // INTERNAL: HTTP POST
    // ──────────────────────────────────────────────────────────
    private fun executePost(
        urlStr: String,
        params: Map<String, String>,
        existingCookie: String
    ): FbLoginState {
        return try {
            val body = params
                .filter { it.value.isNotEmpty() }
                .entries
                .joinToString("&") {
                    "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
                }
                .toByteArray(Charsets.UTF_8)

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = FbAmdConstants.TIMEOUT_MS
                readTimeout = FbAmdConstants.TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", FbAmdConstants.USER_AGENT)
                setRequestProperty("Authorization", "Bearer ${FbAmdConstants.APP_TOKEN}")
                setRequestProperty("X-FB-HTTP-Engine", "Liger")
                setRequestProperty("X-FB-Client-IP", "True")
                setRequestProperty("X-FB-Server-Cluster", "True")
                setRequestProperty("Accept-Language", "en_US")
                if (existingCookie.isNotEmpty()) {
                    setRequestProperty("Cookie", existingCookie)
                }
            }

            conn.outputStream.use { it.write(body) }

            val respCode = conn.responseCode
            val stream = if (respCode == 200) conn.inputStream else conn.errorStream
            val rawJson = stream?.bufferedReader()?.readText() ?: ""

            // ── Collect cookies từ headers (AUTH_CREDENTIAL_COOKIE_SET – classes16.dex L810) ──
            val setCookieHeaders = conn.headerFields["Set-Cookie"] ?: emptyList()
            val cookieStr = buildCookieString(setCookieHeaders)

            conn.disconnect()
            parseResponse(rawJson, cookieStr)

        } catch (e: Exception) {
            FbLoginState.Error(-1, e.message ?: "Lỗi kết nối mạng")
        }
    }

    // ──────────────────────────────────────────────────────────
    // INTERNAL: Parse JSON response
    //
    // Phân tích từ classes12.dex L696:
    //   "JSONException in authentication response"
    // Và classes12.dex L720:
    //   auth_token, authenticate, authentication_ticket_id
    // ──────────────────────────────────────────────────────────
    private fun parseResponse(rawJson: String, cookieStr: String): FbLoginState {
        return try {
            val json = JSONObject(rawJson)

            // Bóc tách error_data (nếu có)
            var errorDataJson: JSONObject? = null
            if (json.has("error_data")) {
                val edStr = json.optString("error_data", "")
                if (edStr.isNotEmpty()) {
                    try {
                        errorDataJson = JSONObject(edStr)
                    } catch (_: Exception) {}
                }
            }

            val cpSession = json.optString("checkpoint_session",
                errorDataJson?.optString("checkpoint_session", "") ?: "")
            val cpUid = json.optString("uid",
                errorDataJson?.optString("uid", "") ?: "")
            val options = json.optJSONObject("checkpoint_options")
                ?: errorDataJson?.optJSONObject("checkpoint_options")

            val errorCode = json.optInt("error_code",
                json.optJSONObject("error")?.optInt("code") ?: -1)
            val errorMsg = json.optString("error_msg",
                json.optJSONObject("error")?.optString("message") ?: "")

            // ── Checkpoint (2FA / SMS / Email) ──
            // classes12.dex: "Checkpoint login redirect expected uid but got"
            val isCheckpoint = options != null ||
                    json.optString("login_status") == "CHECKPOINT" ||
                    errorCode == 405 ||
                    cpSession.isNotEmpty()

            if (isCheckpoint) {
                return when {
                    options?.has("twofactor_code") == true ->
                        FbLoginState.CheckpointTwoFa(cpSession, cpUid)
                    options?.has("sms_code") == true ->
                        FbLoginState.CheckpointSms(cpSession, cpUid)
                    options?.has("email_code") == true ->
                        FbLoginState.CheckpointEmail(cpSession, cpUid)
                    else ->
                        FbLoginState.CheckpointTwoFa(cpSession, cpUid)
                }
            }

            // ── Lỗi API khác ──
            if (errorCode > 0 || errorMsg.isNotEmpty()) {
                val userFriendlyMsg = when (errorCode) {
                    406 -> "Email/Số điện thoại hoặc mật khẩu không chính xác (Mã 406)"
                    401 -> "App Token Facebook đã hết hạn hoặc không hợp lệ (Mã 401)"
                    368 -> "Tài khoản bị tạm khóa hoặc giới hạn đăng nhập từ Meta (Mã 368)"
                    else -> errorMsg.ifEmpty { "Mã lỗi: $errorCode" }
                }
                return FbLoginState.Error(errorCode, userFriendlyMsg)
            }

            // ── Thành công ──
            val token = json.optString("access_token", "")
            val uid = json.optString("uid", "")
            val machineId = json.optString("machine_id", "")

            if (token.isNotEmpty() && uid.isNotEmpty()) {
                FbLoginState.Success(
                    FbLoginResult(
                        uid = uid,
                        token = token,
                        machineId = machineId,
                        cookies = cookieStr
                    )
                )
            } else {
                FbLoginState.Error(-2, "Không tìm thấy token hoặc UID trong response: $rawJson")
            }

        } catch (e: Exception) {
            // "JSONException in authentication response" – classes12.dex L696
            FbLoginState.Error(-3, "JSONException: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────────────────
    // INTERNAL: Build cookie string
    // AUTH_CREDENTIAL_COOKIE_SET – classes16.dex L810
    // Cookie keys: c_user, xs, datr, sb, fr
    // ──────────────────────────────────────────────────────────
    private fun buildCookieString(setCookieHeaders: List<String>): String {
        val cookieMap = mutableMapOf<String, String>()
        val relevantKeys = setOf("c_user", "xs", "datr", "sb", "fr", "wd", "locale")

        for (header in setCookieHeaders) {
            val parts = header.split(";").map { it.trim() }
            if (parts.isNotEmpty()) {
                val kv = parts[0].split("=", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].trim()
                    val v = kv[1].trim()
                    if (k in relevantKeys) {
                        cookieMap[k] = v
                    }
                }
            }
        }
        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    // ──────────────────────────────────────────────────────────────
    // INTERNAL: Fake ADID (advertising ID)
    // ──────────────────────────────────────────────────────────────
    private fun generateFakeAdid(): String {
        val chars = ('a'..'f') + ('0'..'9')
        fun seg(n: Int) = (1..n).map { chars.random() }.joinToString("")
        return "${seg(8)}-${seg(4)}-${seg(4)}-${seg(4)}-${seg(12)}"
    }
}
