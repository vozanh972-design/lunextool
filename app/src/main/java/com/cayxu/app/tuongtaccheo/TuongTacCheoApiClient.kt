package com.cayxu.app.tuongtaccheo

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
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
        const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"
    }

    private var httpClient: OkHttpClient
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false) // Tắt redirect để không bị nuốt mã HTML
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

        if (!proxyStr.isNullOrEmpty()) {
            val parts = proxyStr.split(":")
            if (parts.size >= 2) {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull()
                if (port != null) {
                    builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                    if (parts.size >= 4) {
                        val user = parts[2].trim()
                        val pass = parts[3].trim()
                        val credential = Credentials.basic(user, pass)
                        builder.proxyAuthenticator { _, res ->
                            res.request.newBuilder().header("Proxy-Authorization", credential).build()
                        }
                    }
                }
            }
        }
        httpClient = builder.build()
    }

    private fun buildHeaders(): Headers {
        val b = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Accept", "application/json, text/javascript, */*; q=0.01")
            .add("Accept-Language", "vi")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Host", "tuongtaccheo.com")
            .add("Origin", BASE_URL)
        if (sessionCookie.isNotEmpty()) b.add("Cookie", sessionCookie)
        return b.build()
    }

    // 1. Đăng nhập bằng Token & Lưu Cookie
    @Throws(Exception::class)
    fun loginWithToken(token: String): TuongTacCheoAccount {
        this.tokenTTC = token
        val body = FormBody.Builder().add("access_token", token).build()
        val req = Request.Builder().url("$BASE_URL/logintoken.php").headers(buildHeaders()).post(body).build()
        httpClient.newCall(req).execute().use { res ->
            val resStr = res.body?.string() ?: ""
            val cookies = res.headers("Set-Cookie")
            val sb = StringBuilder()
            for (c in cookies) sb.append(c.split(";")[0]).append("; ")
            if (sb.isNotEmpty()) sessionCookie = sb.toString().trim()

            val json = if (resStr.trim().startsWith("{")) JSONObject(resStr) else JSONObject()
            if (json.optString("status") != "success" && !resStr.contains("user")) {
                throw IllegalStateException(json.optString("mess", "Không thể login TTC"))
            }
            val user = json.optString("user", "Unknown")
            val sodu = json.optString("sodu", "0").replace(",", "").replace(".", "").toLongOrNull() ?: 0L
            return TuongTacCheoAccount(user, token, sessionCookie, sodu)
        }
    }

    // 2. Thêm Nick / Page (Chuẩn 100% từ cURL)
    @Throws(Exception::class)
    fun themNick(linkOrUid: String, loainick: String = "fb"): TTCThemNickResult {
        if (sessionCookie.isBlank() && tokenTTC.isNotBlank()) {
            try { loginWithToken(tokenTTC) } catch (_: Exception) {}
        }

        val refererUrl = if (loainick == "fb") "$BASE_URL/cauhinh/facebook.php" else "$BASE_URL/cauhinh/tiktok.php"
        val formBody = FormBody.Builder()
            .add("link", linkOrUid)
            .add("loainick", loainick)
            .add("recaptcha", "1") // BẮT BUỘC "1"
            .build()

        val req = Request.Builder()
            .url("$BASE_URL/cauhinh/nhapnick.php")
            .headers(buildHeaders())
            .header("Referer", refererUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        httpClient.newCall(req).execute().use { res ->
            val resStr = res.body?.string() ?: ""
            val isSuccess = res.isSuccessful && (resStr.contains("\"status\":1") || resStr.contains("Thành công") || resStr.contains("Thêm thành công"))
            var msg = resStr
            try {
                if (resStr.trim().startsWith("{")) {
                    val j = JSONObject(resStr)
                    msg = j.optString("mess", j.optString("message", resStr))
                }
            } catch (_: Exception) {}
            return TTCThemNickResult(isSuccess, msg, resStr)
        }
    }

    // 3. Đặt Nick chạy Job (cauhinh/datnick.php)
    @Throws(Exception::class)
    fun setNickRun(uid: String, loai: String = "fb"): TTCDatNickResult {
        if (sessionCookie.isBlank() && tokenTTC.isNotBlank()) {
            try { loginWithToken(tokenTTC) } catch (_: Exception) {}
        }

        val formBody = FormBody.Builder()
            .add("iddat[]", uid)
            .add("loai", loai)
            .build()
        val refererUrl = if (loai == "fb") "$BASE_URL/cauhinh/facebook.php" else "$BASE_URL/cauhinh/tiktok.php"
        val req = Request.Builder()
            .url("$BASE_URL/cauhinh/datnick.php")
            .headers(buildHeaders())
            .header("Referer", refererUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        httpClient.newCall(req).execute().use { res ->
            val resStr = res.body?.string() ?: ""
            val trimmed = resStr.trim()
            val isSuccess = res.isSuccessful && (trimmed == "1" || trimmed.contains("\"status\":1") || trimmed.contains("Cấu hình thành công"))
            val code = when {
                isSuccess -> 1
                trimmed == "2" || trimmed.contains("\"status\":2") || trimmed.contains("chưa thêm") || trimmed.contains("chưa được thêm") -> 2
                trimmed == "-1" || trimmed.contains("chậm lại") -> -1
                else -> 0
            }
            val msg = when (code) {
                1 -> "Cấu hình đặt nick thành công!"
                2 -> "Nick/Page chưa được thêm vào hệ thống TTC!"
                -1 -> "Vui lòng thao tác chậm lại"
                else -> "Lỗi đặt nick TTC: $trimmed"
            }
            return TTCDatNickResult(isSuccess, code, msg, resStr)
        }
    }

    // 4. Helper tự động thêm nick nếu chưa có & tự động delay nếu gặp rate-limit
    @Throws(Exception::class)
    fun autoPrepareAndSetNick(uid: String, loai: String = "fb", onLog: ((String) -> Unit)? = null): TTCDatNickResult {
        var res = setNickRun(uid, loai)
        if (res.isSuccess) return res

        // Nếu mã 2: Chưa thêm nick -> Tự thêm nick vào TTC rồi đặt lại
        if (res.code == 2 || res.message.contains("chưa", ignoreCase = true)) {
            onLog?.invoke("Nick chưa thêm trên web, đang tự động thêm vào TTC...")
            themNick(uid, loai)
            try { Thread.sleep(2000) } catch (_: Exception) {}
            res = setNickRun(uid, loai)
        }

        // Nếu mã -1: Rate limit thao tác chậm -> Tự động chờ đếm ngược 7s rồi thử lại
        if (res.code == -1 || res.message.contains("chậm lại", ignoreCase = true) || res.rawResponse.contains("chậm lại", ignoreCase = true)) {
            for (sec in 7 downTo 1) {
                onLog?.invoke("TTC yêu cầu thao tác chậm, tự động chờ ${sec}s rồi thử lại...")
                try { Thread.sleep(1000) } catch (_: Exception) {}
            }
            onLog?.invoke("Đang đặt nick/page làm nick chạy...")
            res = setNickRun(uid, loai)
        }
        return res
    }

    // 5. Lấy danh sách nhiệm vụ (Chuẩn 100% theo path cURL)
    @Throws(Exception::class)
    fun getJobs(jobType: TTCJobType): List<TTCJob> {
        val url = "$BASE_URL/kiemtien/${jobType.path}/getpost.php"
        val referer = "$BASE_URL/kiemtien/${jobType.path}/"

        val req = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .get()
            .build()

        httpClient.newCall(req).execute().use { res ->
            val resStr = res.body?.string() ?: ""
            if (resStr.contains("Hết Job") || resStr.contains("countdown") || resStr.contains("\"error\"")) return emptyList()
            val list = mutableListOf<TTCJob>()
            if (resStr.trim().startsWith("[")) {
                val arr = JSONArray(resStr)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(TTCJob(
                        id = o.optString("id", o.optString("idpost", "")),
                        idfb = o.optString("idfb", null),
                        idpost = o.optString("idpost", null),
                        link = o.optString("link", null),
                        loaicx = o.optString("loaicx", null),
                        cmt = o.optString("nd", null),
                        uid = o.optString("uid", null)
                    ))
                }
            } else if (resStr.trim().startsWith("{")) {
                val j = JSONObject(resStr)
                val arr = j.optJSONArray("data") ?: j.optJSONArray("posts")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(TTCJob(
                            id = o.optString("id", o.optString("idpost", "")),
                            idfb = o.optString("idfb", null),
                            idpost = o.optString("idpost", null),
                            link = o.optString("link", null),
                            loaicx = o.optString("loaicx", null),
                            cmt = o.optString("nd", null),
                            uid = o.optString("uid", null)
                        ))
                    }
                }
            }
            return list
        }
    }

    // 6. Nhận xu sau khi hoàn thành job (Chuẩn 100% theo path cURL)
    @Throws(Exception::class)
    fun claimReward(jobId: String, jobType: TTCJobType): TTCNhanTienResult {
        val url = "$BASE_URL/kiemtien/${jobType.path}/nhantien.php"
        val referer = "$BASE_URL/kiemtien/${jobType.path}/"
        val body = FormBody.Builder().add("id", jobId).build()

        val req = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post(body)
            .build()

        httpClient.newCall(req).execute().use { res ->
            val resStr = res.body?.string() ?: ""
            val isSuccess = res.isSuccessful && (resStr.contains("\"success\"") || resStr.contains("\"status\":\"success\"") || resStr.contains("Thành công"))
            val j = if (resStr.trim().startsWith("{")) JSONObject(resStr) else JSONObject()
            val sodu = j.optString("sodu", "0").replace(",", "").replace(".", "").toLongOrNull() ?: 0L
            return TTCNhanTienResult(
                isSuccess = isSuccess,
                sodu = sodu,
                xuThem = j.optLong("xu", j.optLong("xu_them", 0L)),
                message = j.optString("mess", if (isSuccess) "Nhận xu thành công!" else "Lỗi nhận xu: $resStr")
            )
        }
    }
}
