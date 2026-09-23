package com.example.facebooktoken

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Port 100% từ PHP sang Kotlin.
 * - Giữ nguyên luồng xử lý, tham số, URL, app token, sig.
 * - SSL verification tắt (tương đương SSL_VERIFYPEER = false).
 * - Nên gọi từ background thread.
 */
object FacebookToken {

    // ---------- CONSTANTS ----------
    private const val PWD_KEY_FETCH_URL = "https://b-graph.facebook.com/pwd_key_fetch"
    private const val LOGIN_URL        = "https://b-graph.facebook.com/auth/login"
    private const val CONVERT_URL      = "https://api.facebook.com/method/auth.getSessionforApp"
    private const val COOKIE_URL       = "https://api.facebook.com/method/auth.getSessionForApp"

    private const val APP_TOKEN  = "350685531728|62f8ce9f74b12f84c123cc23437a4a32"
    private const val API_KEY    = "882a8490361da98702bf97a021ddc14d"
    private const val SIG        = "214049b9f17c38bd767de53752b53946"
    private const val TARGET_APP_ID = "350685531728"

    private const val FB_PWD_ACCESS_TOKEN =
        "438142079694454|fc0a7caa49b192f64f6f5a6d9643bb28"

    private val random = SecureRandom()
    private val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    // ---------- DATA CLASSES ----------
    data class LoginResult(
        val status: Int,
        val msg: String? = null,
        val token: String? = null,
        val eaaaaToken: String? = null,
        val cookie: String? = null,
        val uid: String? = null
    )

    data class Entry(
        val type: String,           // "login" | "cookie"
        val uid: String? = null,
        val pass: String? = null,
        val twofa: String? = null,
        val datr: String? = null,
        val cookie: String? = null,
        val raw: String
    )

    data class ProcessedResult(
        val account: String,
        val success: Boolean,
        val token: String? = null,
        val cookie: String? = null,
        val uid: String? = null,
        val error: String? = null
    )

    init {
        disableSslVerification()
    }

    // ---------- SSL DISABLE (match PHP SSL_VERIFYPEER=false) ----------
    private fun disableSslVerification() {
        try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAll, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }

    // ---------- HTTP HELPERS ----------
    private fun buildQuery(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    private fun httpGet(url: String, cookie: String? = null): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        cookie?.let { conn.setRequestProperty("Cookie", it) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to body
    }

    private fun httpPost(
        url: String,
        data: String,
        cookie: String? = null,
        contentType: String = "application/x-www-form-urlencoded"
    ): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", contentType)
        cookie?.let { conn.setRequestProperty("Cookie", it) }
        conn.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to body
    }

    // ---------- ENCRYPT PASSWORD ----------
    fun encryptPassword(password: String): String? {
        return try {
            val params = mapOf(
                "version" to "2",
                "flow" to "CONTROLLER_INITIALIZATION",
                "method" to "GET",
                "fb_api_req_friendly_name" to "pwdKeyFetch",
                "fb_api_caller_class" to "com.facebook.auth.login.AuthOperations",
                "access_token" to FB_PWD_ACCESS_TOKEN
            )
            val (code, body) = httpGet(PWD_KEY_FETCH_URL + "?" + buildQuery(params))
            if (code != 200) return null

            val json = JSONObject(body)
            if (!json.has("public_key")) return null

            val publicKeyPem = json.getString("public_key")
            val keyId = if (json.has("key_id")) json.getInt("key_id") else 25

            val aesKey = ByteArray(32).also { random.nextBytes(it) }
            val iv = ByteArray(12).also { random.nextBytes(it) }

            // Parse PEM public key (giống openssl_pkey_get_public)
            val pubKeyClean = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace(Regex("\\s"), "")
            val pubKeyBytes = Base64.getDecoder().decode(pubKeyClean)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(pubKeyBytes))

            // RSA encrypt AES key với PKCS#1 padding (giống OPENSSL_PKCS1_PADDING)
            val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedAesKey = rsaCipher.doFinal(aesKey)

            // AES-256-GCM encrypt password, AAD = currentTime string
            val currentTime = System.currentTimeMillis() / 1000
            val aad = currentTime.toString().toByteArray(Charsets.UTF_8)

            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            aesCipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(128, iv)      // 128-bit = 16-byte tag
            )
            aesCipher.updateAAD(aad)
            val cipherWithTag = aesCipher.doFinal(password.toByteArray(Charsets.UTF_8))

            // Java trả về ciphertext + tag liền nhau → tách 16 byte cuối làm tag
            val tag = cipherWithTag.copyOfRange(cipherWithTag.size - 16, cipherWithTag.size)
            val encryptedPass = cipherWithTag.copyOfRange(0, cipherWithTag.size - 16)

            // Build buffer:
            // [1 byte 0x01][1 byte keyId][12 bytes iv]
            // [2 bytes LE len(encAesKey)][encAesKey][16 bytes tag][ciphertext]
            val buf = ByteArrayOutputStream()
            buf.write(1)
            buf.write(keyId)
            buf.write(iv)
            val len = encryptedAesKey.size
            buf.write(len and 0xFF)          // pack('v') low byte
            buf.write((len shr 8) and 0xFF)  // pack('v') high byte
            buf.write(encryptedAesKey)
            buf.write(tag)
            buf.write(encryptedPass)

            Base64.getEncoder().encodeToString(buf.toByteArray())
        } catch (e: Exception) {
            null
        }
    }

    // ---------- TOTP ----------
    @Throws(Exception::class)
    fun generateTOTP(secret: String): String {
        val normalized = secret.uppercase().replace(" ", "").trimEnd('=')
        val binary = ByteArrayOutputStream()
        var buffer = 0L
        var bits = 0

        for (c in normalized) {
            val idx = base32Chars.indexOf(c)
            if (idx == -1) throw Exception("Invalid base32 char")
            buffer = (buffer shl 5) or idx.toLong()
            bits += 5
            if (bits >= 8) {
                binary.write(((buffer shr (bits - 8)) and 0xFF).toInt())
                bits -= 8
            }
        }

        val binaryBytes = binary.toByteArray()
        val counter = System.currentTimeMillis() / 1000 / 30

        // PHP: pack('N*', 0, 0, 0, counter) → 16 byte big-endian
        val counterBytes = ByteBuffer.allocate(16)
            .putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(counter.toInt())
            .array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(binaryBytes, "HmacSHA1"))
        val hmac = mac.doFinal(counterBytes)

        val offset = hmac[hmac.size - 1].toInt() and 0x0F
        val code = ((hmac[offset].toInt() and 0x7F) shl 24) or
                   ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                   ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                   (hmac[offset + 3].toInt() and 0xFF)

        return (code % 1_000_000).toString().padStart(6, '0')
    }

    // ---------- CONVERT TOKEN ----------
    fun convertToken(accessToken: String, targetAppId: String): String? {
        return try {
            val data = buildQuery(mapOf(
                "access_token" to accessToken,
                "format" to "json",
                "new_app_id" to targetAppId,
                "generate_session_cookies" to "1"
            ))
            val (_, body) = httpPost(CONVERT_URL, data)
            val json = JSONObject(body)
            if (json.has("access_token")) json.getString("access_token") else null
        } catch (e: Exception) {
            null
        }
    }

    // ---------- FACEBOOK LOGIN ----------
    fun facebookLogin(
        email: String,
        password: String,
        auth2fa: String? = null,
        datr: String? = null
    ): LoginResult {
        // Tạo UUID-like device ID
        val deviceId = String.format(
            "%04x%04x-%04x-%04x-%04x-%04x%04x%04x",
            random.nextInt(0x10000), random.nextInt(0x10000),
            random.nextInt(0x10000),
            (random.nextInt(0x1000) or 0x4000),
            (random.nextInt(0x4000) or 0x8000),
            random.nextInt(0x10000), random.nextInt(0x10000), random.nextInt(0x10000)
        )
        val adid = deviceId
        val jazoest = ('0'..'9').shuffled().take(5).joinToString("")

        val machineId = if (datr != null && datr.length >= 24) {
            datr.substring(0, 24)
        } else {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            chars.repeat(3).toList().shuffled().take(24).joinToString("")
        }

        val encPass = encryptPassword(password)
        val passwords = mutableListOf<String>()
        encPass?.let { passwords.add(it) }
        passwords.add(password) // fallback plaintext

        val cookieJar = mutableListOf<String>()
        datr?.let { cookieJar.add("datr=$it") }

        for ((index, pwd) in passwords.withIndex()) {
            val params = mapOf(
                "email" to email,
                "password" to pwd,
                "generate_session_cookies" to "1",
                "locale" to "vi_VN",
                "client_country_code" to "VN",
                "access_token" to APP_TOKEN,
                "api_key" to API_KEY,
                "adid" to adid,
                "machine_id" to machineId,
                "jazoest" to jazoest,
                "fb_api_req_friendly_name" to "authenticate",
                "sig" to SIG
            )
            val cookie = if (cookieJar.isNotEmpty()) cookieJar.joinToString("; ") else null
            val (_, body) = httpPost(LOGIN_URL, buildQuery(params), cookie)
            var resultJson = try { JSONObject(body) } catch (e: Exception) { continue }

            // Xử lý 2FA (giống PHP isset error_data.login_first_factor + uid)
            val errorData = resultJson.optJSONObject("error")?.optJSONObject("error_data")
            if (errorData != null &&
                errorData.has("login_first_factor") &&
                errorData.has("uid")
            ) {
                val factor = errorData.getString("login_first_factor")
                val uidFromError = errorData.getString("uid")

                val twofactorCode = if (auth2fa != null) {
                    try { generateTOTP(auth2fa) }
                    catch (e: Exception) {
                        return LoginResult(402, "Lỗi tạo mã 2FA: ${e.message}")
                    }
                } else {
                    return LoginResult(402, "Yêu cầu nhập mã 2FA thủ công (thiếu secret)")
                }

                val data2fa = mapOf(
                    "email" to email,
                    "access_token" to APP_TOKEN,
                    "twofactor_code" to twofactorCode,
                    "password" to pwd,
                    "userid" to uidFromError,
                    "machine_id" to factor,
                    "generate_session_cookies" to "1"
                )
                val (_, body2) = httpPost(LOGIN_URL, buildQuery(data2fa), cookie)
                resultJson = try { JSONObject(body2) } catch (e: Exception) { continue }
            }

            if (resultJson.has("access_token")) {
                val accessToken = resultJson.getString("access_token")
                val eaaaaToken = convertToken(accessToken, TARGET_APP_ID) ?: accessToken

                val sessionCookies = resultJson.optJSONArray("session_cookies")
                val cookieParts = mutableListOf<String>()
                var uid: String? = null

                if (sessionCookies != null) {
                    for (i in 0 until sessionCookies.length()) {
                        val c = sessionCookies.getJSONObject(i)
                        val name = c.getString("name")
                        val value = c.getString("value")
                        cookieParts.add("$name=$value")
                        if (name == "c_user") uid = value
                    }
                }

                return LoginResult(
                    status = 200,
                    token = accessToken,
                    eaaaaToken = eaaaaToken,
                    cookie = cookieParts.joinToString("; "),
                    uid = uid
                )
            }

            val errorMsg = resultJson.optJSONObject("error")
                ?.optString("message") ?: "Login failed"
            if (errorMsg.contains("Invalid username or password") &&
                index < passwords.size - 1
            ) {
                continue
            }
            return LoginResult(401, errorMsg)
        }

        return LoginResult(401, "Login failed after trying all password variants")
    }

    // ---------- GET TOKEN FROM COOKIE ----------
    fun getTokenFromCookie(cookie: String): LoginResult {
        val cUserMatch = Regex("c_user=([^;]+)").find(cookie)
        val xsMatch = Regex("xs=([^;]+)").find(cookie)

        if (cUserMatch == null && xsMatch == null) {
            return LoginResult(400, "Cookie không có c_user và xs. Vui lòng cung cấp cookie đầy đủ.")
        }
        if (cUserMatch == null) {
            return LoginResult(400, "Cookie thiếu c_user. Không thể xác thực.")
        }
        if (xsMatch == null) {
            return LoginResult(400, "Cookie thiếu xs. Không thể xác thực.")
        }

        val cUser = cUserMatch.groupValues[1]

        val data = buildQuery(mapOf(
            "format" to "json",
            "generate_session_cookies" to "1"
        ))
        val (code, body) = httpPost(COOKIE_URL, data, cookie)

        if (code != 200) {
            return LoginResult(401, "Không thể kết nối tới Facebook API (HTTP $code)")
        }

        val json = try { JSONObject(body) } catch (e: Exception) {
            return LoginResult(401, "Không thể lấy token từ cookie")
        }

        if (json.has("access_token")) {
            val accessToken = json.getString("access_token")
            val eaaaaToken = convertToken(accessToken, TARGET_APP_ID) ?: accessToken

            val sessionCookies = json.optJSONArray("session_cookies")
            val cookieParts = mutableListOf<String>()
            var uid: String? = null

            if (sessionCookies != null) {
                for (i in 0 until sessionCookies.length()) {
                    val c = sessionCookies.getJSONObject(i)
                    val name = c.getString("name")
                    val value = c.getString("value")
                    cookieParts.add("$name=$value")
                    if (name == "c_user") uid = value
                }
            }
            if (cookieParts.isEmpty()) cookieParts.add(cookie)

            return LoginResult(
                status = 200,
                token = accessToken,
                eaaaaToken = eaaaaToken,
                cookie = cookieParts.joinToString("; "),
                uid = uid ?: cUser
            )
        }

        val errorMsg = json.optJSONObject("error")
            ?.optString("message") ?: "Không thể lấy token từ cookie"
        return LoginResult(401, errorMsg)
    }

    // ---------- PARSE INPUT ----------
    fun parseEntries(raw: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        for (line in raw.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("|")) {
                val parts = trimmed.split("|").map { it.trim() }
                val uid = parts.getOrNull(0) ?: ""
                val pwd = parts.getOrNull(1) ?: ""
                var twofa: String? = null
                var datr: String? = null

                for (part in parts) {
                    if (part.startsWith("datr=")) datr = part.substring(5)
                }
                if (parts.size > 2 && !parts[2].startsWith("datr=")) {
                    twofa = parts[2]
                }

                entries.add(Entry(
                    type = "login",
                    uid = uid,
                    pass = pwd,
                    twofa = twofa,
                    datr = datr,
                    raw = trimmed
                ))
            } else {
                entries.add(Entry(type = "cookie", cookie = trimmed, raw = trimmed))
            }
        }
        return entries
    }

    // ---------- PROCESS ALL ----------
    fun process(raw: String): List<ProcessedResult> {
        val results = mutableListOf<ProcessedResult>()
        for (entry in parseEntries(raw)) {
            if (entry.type == "cookie") {
                val r = getTokenFromCookie(entry.cookie ?: "")
                if (r.status == 200) {
                    results.add(ProcessedResult(
                        account = "Cookie (UID: ${r.uid ?: "N/A"})",
                        success = true,
                        token = r.eaaaaToken,
                        cookie = r.cookie,
                        uid = r.uid
                    ))
                } else {
                    results.add(ProcessedResult(
                        account = "Cookie",
                        success = false,
                        error = r.msg ?: "Unknown error"
                    ))
                }
            } else {
                val r = facebookLogin(entry.uid ?: "", entry.pass ?: "", entry.twofa, entry.datr)
                if (r.status == 200) {
                    results.add(ProcessedResult(
                        account = entry.uid ?: "N/A",
                        success = true,
                        token = r.eaaaaToken,
                        cookie = r.cookie,
                        uid = r.uid
                    ))
                } else {
                    results.add(ProcessedResult(
                        account = entry.uid ?: "N/A",
                        success = false,
                        error = r.msg ?: "Unknown error"
                    ))
                }
            }
        }
        return results
    }
}
