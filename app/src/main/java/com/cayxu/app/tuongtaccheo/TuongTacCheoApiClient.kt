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

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private var httpClient: OkHttpClient

    init {
        // Nạp cookie khởi tạo nếu có
        if (sessionCookie.isNotBlank()) {
            val list = cookieStore.getOrPut("tuongtaccheo.com") { mutableListOf() }
            val parts = sessionCookie.split(";")
            for (p in parts) {
                val kv = p.trim().split("=", limit = 2)
                if (kv.size == 2) {
                    try {
                        val c = Cookie.Builder()
                            .domain("tuongtaccheo.com")
                            .path("/")
                            .name(kv[0].trim())
                            .value(kv[1].trim())
                            .build()
                        list.removeAll { it.name == c.name }
                        list.add(c)
                    } catch (_: Exception) {}
                }
            }
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                    for (cookie in cookies) {
                        list.removeAll { it.name == cookie.name }
                        list.add(cookie)
                    }
                    val sb = StringBuilder()
                    for (c in list) {
                        sb.append(c.name).append("=").append(c.value).append("; ")
                    }
                    if (sb.isNotEmpty()) {
                        sessionCookie = sb.toString().trim()
                    }
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .followRedirects(false) // TẮT tự động redirect để không bị nuốt mã HTML trang chủ
            .followSslRedirects(false)

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

    private fun isHtml(raw: String): Boolean {
        val b = raw.trim().lowercase()
        return b.startsWith("<!doctype") || b.startsWith("<html") ||
                b.contains("<title>") || b.contains("tăng like tương tác chéo")
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
            if (!response.isSuccessful && response.code !in 200..399) throw IllegalStateException("Lỗi HTTP: ${response.code}")
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
    fun themNick(linkOrUid: String, loainick: String = "fb"): TTCThemNickResult {
        if (sessionCookie.isBlank() && tokenTTC.isNotBlank()) {
            try { loginWithToken(tokenTTC) } catch (_: Exception) {}
        }

        val refererUrl = if (loainick == "fb") {
            "$BASE_URL/cauhinh/facebook.php"
        } else {
            "$BASE_URL/cauhinh/tiktok.php"
        }

        // Form data chuẩn xác 100% từ cURL trình duyệt
        val formBody = FormBody.Builder()
            .add("link", linkOrUid)
            .add("loainick", loainick)
            .add("recaptcha", "1") // BẮT BUỘC PHẢI LÀ "1" (TTC YÊU CẦU ĐỂ CHẤP NHẬN THÊM NICK)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/cauhinh/nhapnick.php")
            .headers(buildHeaders())
            .header("Referer", refererUrl) // Header Referer bắt buộc
            .header("Origin", BASE_URL)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        fun doThem(): TTCThemNickResult {
            return httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val isSuccess = response.isSuccessful && (
                    body.contains("\"status\":1") || 
                    body.contains("Thành công") || 
                    body.contains("thành công") ||
                    body.contains("Thêm thành công")
                )
                
                var msg = body
                try {
                    if (body.trim().startsWith("{")) {
                        val json = JSONObject(body)
                        msg = json.optString("mess", json.optString("message", body))
                    }
                } catch (_: Exception) {}

                TTCThemNickResult(
                    isSuccess = isSuccess,
                    message = msg,
                    rawResponse = body
                )
            }
        }

        var res = doThem()
        if (!res.isSuccess && (isHtml(res.rawResponse) || res.message.contains("hết hạn")) && tokenTTC.isNotBlank()) {
            try {
                loginWithToken(tokenTTC)
                res = doThem()
            } catch (_: Exception) {}
        }
        return res
    }

    /**
     * 3. Cấu hình Đặt Nick chạy (cauhinh/datnick.php)
     */
    @Throws(Exception::class)
    fun setNickRun(uid: String, loai: String = "fb"): TTCDatNickResult {
        if (sessionCookie.isBlank() && tokenTTC.isNotBlank()) {
            try { loginWithToken(tokenTTC) } catch (_: Exception) {}
        }

        fun doSet(): TTCDatNickResult {
            val formBody = FormBody.Builder()
                .add("iddat[]", uid)
                .add("loai", loai)
                .build()
            val request = Request.Builder()
                .url("$BASE_URL/cauhinh/datnick.php")
                .headers(buildHeaders())
                .header("Referer", "$BASE_URL/cauhinh/facebook.php")
                .header("Origin", BASE_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBody)
                .build()
            return httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val trimmed = body.trim()

                // Kiểm tra nếu bị redirect hoặc trả về HTML
                if (response.code in 300..399 || isHtml(trimmed)) {
                    return@use TTCDatNickResult(
                        isSuccess = false,
                        code = -1,
                        message = "Phiên đăng nhập TTC hết hạn hoặc sai Cookie!",
                        rawResponse = trimmed
                    )
                }

                val isSuccess = response.isSuccessful && (trimmed == "1" || trimmed.contains("\"status\":1") || trimmed.contains("Cấu hình thành công") || trimmed.contains("Thành công"))
                val code = when {
                    isSuccess -> 1
                    trimmed == "2" || trimmed.contains("\"status\":2") || trimmed.contains("chưa thêm") || trimmed.contains("chưa được thêm") -> 2
                    else -> 0
                }
                val msg = when (code) {
                    1 -> "Cấu hình đặt nick thành công!"
                    2 -> "Nick/Page chưa được thêm vào hệ thống TTC!"
                    else -> {
                        var parsedMsg = trimmed
                        try {
                            if (trimmed.startsWith("{")) {
                                val json = JSONObject(trimmed)
                                parsedMsg = json.optString("mess", json.optString("message", trimmed))
                            }
                        } catch (_: Exception) {}
                        "Lỗi đặt nick TTC: $parsedMsg"
                    }
                }
                TTCDatNickResult(isSuccess, code, msg, body)
            }
        }

        var res = doSet()
        // Nếu bị hết hạn cookie (-1), tự động login token refresh rồi thử lại 1 lần
        if (res.code == -1 && tokenTTC.isNotBlank()) {
            try {
                loginWithToken(tokenTTC)
                res = doSet()
            } catch (_: Exception) {}
        }
        return res
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

        // Xử lý lỗi Rate-limit TTC: "Vui lòng thao tác chậm lại"
        if (datResult.message.contains("thao tác chậm lại", ignoreCase = true) || datResult.rawResponse.contains("thao tác chậm lại", ignoreCase = true)) {
            for (sec in 10 downTo 1) {
                onStepUpdate?.invoke("TTC yêu cầu thao tác chậm lại, đang chờ ${sec}s rồi thử lại...")
                try { Thread.sleep(1000) } catch (_: Exception) {}
            }
            onStepUpdate?.invoke("Đang đặt nick/page làm nick chạy...")
            datResult = setNickRun(uid, loai)
            if (datResult.isSuccess) return datResult
        }

        // Nếu mã lỗi 2 (chưa có trên web), tự động gọi themNick rồi thử lại
        val isNotAdded = datResult.code == 2 || 
            datResult.message.contains("chưa được thêm", ignoreCase = true) || 
            datResult.message.contains("chưa thêm", ignoreCase = true) ||
            datResult.rawResponse.contains("chưa", ignoreCase = true)

        if (isNotAdded) {
            onStepUpdate?.invoke("Đang thêm nick/page [$uid] vào hệ thống TTC...")
            val themRes = themNick(uid, loai)
            if (themRes.message.contains("thao tác chậm lại", ignoreCase = true) || themRes.rawResponse.contains("thao tác chậm lại", ignoreCase = true)) {
                for (sec in 10 downTo 1) {
                    onStepUpdate?.invoke("TTC yêu cầu thao tác chậm lại, đang chờ ${sec}s rồi thử lại...")
                    try { Thread.sleep(1000) } catch (_: Exception) {}
                }
            } else {
                try { Thread.sleep(2000) } catch (_: Exception) {}
            }
            onStepUpdate?.invoke("Đang đặt nick/page làm nick chạy...")
            datResult = setNickRun(uid, loai)

            // Kiểm tra rate-limit sau khi themNick và thử đặt lại
            while (!datResult.isSuccess && (datResult.message.contains("thao tác chậm lại", ignoreCase = true) || datResult.rawResponse.contains("thao tác chậm lại", ignoreCase = true))) {
                for (sec in 10 downTo 1) {
                    onStepUpdate?.invoke("TTC yêu cầu thao tác chậm lại, đang chờ ${sec}s rồi thử lại...")
                    try { Thread.sleep(1000) } catch (_: Exception) {}
                }
                onStepUpdate?.invoke("Đang đặt nick/page làm nick chạy...")
                datResult = setNickRun(uid, loai)
                if (datResult.isSuccess) break
            }
        }
        return datResult
    }

    /**
     * 5. Lấy danh sách nhiệm vụ (kiemtien/getpost.php)
     */
    @Throws(Exception::class)
    fun getJobs(jobType: TTCJobType): List<TTCJob> {
        val (url, referer) = when (jobType) {
            TTCJobType.FB_LIKE -> Pair(
                "$BASE_URL/kiemtien/likepostvipre/getpost.php",
                "$BASE_URL/kiemtien/likepostvipre/"
            )
            TTCJobType.FB_CX -> Pair(
                "$BASE_URL/kiemtien/camxucvipre/getpost.php",
                "$BASE_URL/kiemtien/camxucvipre/"
            )
            else -> Pair(
                "$BASE_URL/kiemtien/getpost.php?type=${jobType.apiType}",
                "$BASE_URL/home.php"
            )
        }

        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.code in 300..399 || isHtml(body) || body.contains("Hết Job") || body.contains("countdown") || body.contains("\"error\"")) return emptyList()
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
            } else if (body.trim().startsWith("{")) {
                val json = JSONObject(body)
                val jsonArr = json.optJSONArray("data") ?: json.optJSONArray("posts")
                if (jsonArr != null) {
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
            }
            return jobList
        }
    }

    /**
     * 6. Nhận xu sau khi hoàn thành job (kiemtien/nhantien.php)
     */
    @Throws(Exception::class)
    fun claimReward(jobId: String, jobType: TTCJobType): TTCNhanTienResult {
        val (nhanTienUrl, referer) = when (jobType) {
            TTCJobType.FB_LIKE -> Pair(
                "$BASE_URL/kiemtien/likepostvipre/nhantien.php",
                "$BASE_URL/kiemtien/likepostvipre/"
            )
            TTCJobType.FB_CX -> Pair(
                "$BASE_URL/kiemtien/camxucvipre/nhantien.php",
                "$BASE_URL/kiemtien/camxucvipre/"
            )
            else -> Pair(
                "$BASE_URL/kiemtien/nhantien.php",
                "$BASE_URL/home.php"
            )
        }
        val formBody = FormBody.Builder()
            .add("id", jobId)
            .add("loai", jobType.apiType)
            .build()

        fun doClaim(targetUrl: String): TTCNhanTienResult {
            val request = Request.Builder()
                .url(targetUrl)
                .headers(buildHeaders())
                .header("Referer", referer)
                .header("Origin", BASE_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .post(formBody)
                .build()
            return httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val isSuccess = response.isSuccessful && (
                    body.contains("\"success\"") || 
                    body.contains("\"status\":\"success\"") || 
                    body.contains("\"status\":1") || 
                    body.contains("Thành công") || 
                    body.contains("thành công")
                )
                val json = if (body.trim().startsWith("{")) JSONObject(body) else JSONObject()
                val soduStr = json.optString("sodu", "0").replace(",", "").replace(".", "")
                TTCNhanTienResult(
                    isSuccess = isSuccess,
                    sodu = soduStr.toLongOrNull() ?: 0L,
                    xuThem = json.optLong("xu", json.optLong("xu_them", 0L)),
                    message = json.optString("mess", if (isSuccess) "Nhận xu thành công!" else "Lỗi: $body")
                )
            }
        }

        var res = doClaim(nhanTienUrl)
        if (!res.isSuccess && nhanTienUrl != "$BASE_URL/kiemtien/nhantien.php" && (isHtml(res.message) || res.message.contains("404"))) {
            res = doClaim("$BASE_URL/kiemtien/nhantien.php")
        }
        return res
    }
}
