package com.cayxu.app.tuongtaccheo

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class TuongTacCheoApiClient(
    var tokenTTC: String = "",
    var sessionCookie: String = "",
    proxyStr: String? = null
) {
    companion object {
        const val BASE_URL = "https://tuongtaccheo.com"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mi 9T Pro) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/12.1 Chrome/79.0.3945.136 Mobile Safari/537.36"
    }

    private var httpClient: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
        if (!proxyStr.isNullOrEmpty()) {
            setupProxy(builder, proxyStr)
        }
        httpClient = builder.build()
    }

    private fun setupProxy(builder: OkHttpClient.Builder, proxyStr: String) {
        try {
            val parts = proxyStr.trim().split(":")
            if (parts.size >= 2) {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: return
                builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                if (parts.size >= 4) {
                    val user = parts[2].trim()
                    val pass = parts[3].trim()
                    builder.proxyAuthenticator { _, response ->
                        val credential = Credentials.basic(user, pass)
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildHeaders(): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Accept", "application/json, text/javascript, */*; q=0.01")
            .add("Accept-Language", "vi")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Host", "tuongtaccheo.com")
            .add("Origin", BASE_URL)
            .add("Referer", "$BASE_URL/home.php")
        if (sessionCookie.isNotEmpty()) {
            builder.add("Cookie", sessionCookie)
        }
        return builder.build()
    }

    /**
     * 1. Đăng nhập bằng Token & Lấy Session Cookie
     */
    @Throws(Exception::class)
    fun loginWithToken(token: String): TuongTacCheoAccount {
        this.tokenTTC = token
        val formBody = FormBody.Builder().add("access_token", token).build()
        val request = Request.Builder().url("$BASE_URL/logintoken.php").headers(buildHeaders()).post(formBody).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw IllegalStateException("Lỗi HTTP: ${response.code}")
            val cookies = response.headers("Set-Cookie")
            val cookieSb = StringBuilder()
            for (c in cookies) cookieSb.append(c.split(";")[0]).append("; ")
            if (cookieSb.isNotEmpty()) sessionCookie = cookieSb.toString().trim()
            val json = if (body.trim().startsWith("{")) JSONObject(body) else JSONObject()
            if (json.optString("status") != "success" && !body.contains("user")) {
                throw IllegalStateException(json.optString("mess", "Không thể login TTC"))
            }
            val user = json.optString("user", "Unknown")
            val soduStr = json.optString("sodu", "0").replace(",", "").replace(".", "")
            return TuongTacCheoAccount(user, token, sessionCookie, soduStr.toLongOrNull() ?: 0L)
        }
    }

    /**
     * 2. Thêm Nick/Page vào hệ thống TTC (nhapnick.php)
     */
    @Throws(Exception::class)
    fun themNick(linkOrUid: String, loainick: String = "fb", recaptcha: String = ""): TTCThemNickResult {
        if (sessionCookie.isBlank() && tokenTTC.isNotBlank()) {
            try {
                loginWithToken(tokenTTC)
            } catch (_: Exception) {}
        }
        val formBody = FormBody.Builder()
            .add("link", linkOrUid)
            .add("loainick", loainick)
            .add("recaptcha", recaptcha)
            .build()
        val request = Request.Builder()
            .url("$BASE_URL/cauhinh/nhapnick.php")
            .headers(buildHeaders())
            .post(formBody)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val isSuccess = response.isSuccessful && (body.contains("\"status\":1") || body.contains("Thành công") || body.contains("thành công") || body.trim() == "1")
            var msg = body
            try {
                if (body.trim().startsWith("{")) {
                    val json = JSONObject(body)
                    msg = json.optString("mess", json.optString("message", body))
                }
            } catch (_: Exception) {}
            return TTCThemNickResult(isSuccess, msg, body)
        }
    }

    /**
     * 3. Cấu hình Đặt Nick chạy (cauhinh/datnick.php)
     */
    @Throws(Exception::class)
    fun setNickRun(uid: String, loai: String = "fb"): TTCDatNickResult {
        if (sessionCookie.isBlank() && tokenTTC.isNotBlank()) {
            try {
                loginWithToken(tokenTTC)
            } catch (_: Exception) {}
        }
        val formBody = FormBody.Builder()
            .add("iddat[]", uid)
            .add("loai", loai)
            .build()
        val request = Request.Builder()
            .url("$BASE_URL/cauhinh/datnick.php")
            .headers(buildHeaders())
            .post(formBody)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val trimmed = body.trim()
            val isSuccess = response.isSuccessful && (trimmed == "1" || trimmed.contains("\"status\":1") || trimmed.contains("Cấu hình thành công"))
            val code = when {
                isSuccess -> 1
                trimmed == "2" || trimmed.contains("\"status\":2") -> 2
                else -> 0
            }
            val msg = when (code) {
                1 -> "Cấu hình đặt nick thành công!"
                2 -> "Nick/Page chưa được thêm vào hệ thống TTC!"
                else -> "Lỗi đặt nick TTC: $trimmed"
            }
            return TTCDatNickResult(isSuccess, code, msg, body)
        }
    }

    /**
     * 4. Helper tự động kiểm tra và thêm nick nếu chưa có rồi mới đặt nick
     */
    @Throws(Exception::class)
    fun autoPrepareAndSetNick(
        uid: String,
        loai: String = "fb",
        onStepUpdate: ((String) -> Unit)? = null
    ): TTCDatNickResult {
        onStepUpdate?.invoke("Đang đặt nick/page làm nick chạy...")
        var datResult = setNickRun(uid, loai)
        if (datResult.isSuccess) return datResult
        // Nếu mã lỗi 2 (chưa có trên web), tự động gọi themNick rồi thử lại
        if (datResult.code == 2) {
            onStepUpdate?.invoke("Đang thêm nick/page [$uid] vào hệ thống TTC...")
            themNick(uid, loai)
            try { Thread.sleep(1000) } catch (_: Exception) {}
            onStepUpdate?.invoke("Đang đặt nick/page làm nick chạy...")
            datResult = setNickRun(uid, loai)
        }
        return datResult
    }

    /**
     * 5. Lấy danh sách nhiệm vụ (kiemtien/getpost.php)
     */
    @Throws(Exception::class)
    fun getJobs(jobType: TTCJobType): List<TTCJob> {
        val request = Request.Builder().url("$BASE_URL/kiemtien/getpost.php?type=${jobType.apiType}").headers(buildHeaders()).get().build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (body.contains("Hết Job") || body.contains("countdown") || body.contains("\"error\"")) return emptyList()
            val jobList = mutableListOf<TTCJob>()
            if (body.trim().startsWith("[")) {
                val jsonArr = JSONArray(body)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    jobList.add(
                        TTCJob(
                            id = obj.optString("id", obj.optString("idpost", "")),
                            idfb = obj.optString("idfb", null),
                            idpost = obj.optString("idpost", null),
                            link = obj.optString("link", null),
                            loaicx = obj.optString("loaicx", null),
                            cmt = obj.optString("nd", null),
                            uid = obj.optString("uid", null)
                        )
                    )
                }
            }
            return jobList
        }
    }

    /**
     * 6. Nhận xu sau khi hoàn thành job (kiemtien/nhantien.php)
     */
    @Throws(Exception::class)
    fun claimReward(jobId: String, jobType: TTCJobType): TTCNhanTienResult {
        val formBody = FormBody.Builder().add("id", jobId).add("loai", jobType.apiType).build()
        val request = Request.Builder().url("$BASE_URL/kiemtien/nhantien.php").headers(buildHeaders()).post(formBody).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val isSuccess = response.isSuccessful && (body.contains("\"success\"") || body.contains("\"status\":\"success\"") || body.contains("Thành công"))
            val json = if (body.trim().startsWith("{")) JSONObject(body) else JSONObject()
            val soduStr = json.optString("sodu", "0").replace(",", "").replace(".", "")
            return TTCNhanTienResult(
                isSuccess = isSuccess,
                sodu = soduStr.toLongOrNull() ?: 0L,
                xuThem = json.optLong("xu", json.optLong("xu_them", 0L)),
                message = json.optString("mess", if (isSuccess) "Nhận xu thành công!" else "Lỗi: $body")
            )
        }
    }
}
