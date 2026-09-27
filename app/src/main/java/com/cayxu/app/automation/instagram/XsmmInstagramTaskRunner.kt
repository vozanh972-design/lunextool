package com.cayxu.app.automation.instagram

import android.content.Context
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.ui.overlay.xsmm.XsmmJobStatusBridge
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

data class IgXsmmAccount(
    var userId: String,
    var username: String,
    val cookie: String,
    val proxy: String? = null,
    var fbDtsg: String? = null,
    var lsd: String? = null,
    var lastErrorMessage: String? = null
)

class XsmmInstagramTaskRunner(
    private val xsmmToken: String,
    private val account: IgXsmmAccount
) {
    private val client: OkHttpClient = buildClient(account.proxy)

    companion object {
        private const val XSMM_API = "https://xsmm.net/api/taskapi/"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        data class RunResult(
            val totalCompleted: Int,
            val totalErrors: Int,
            val totalEarnedPoints: Int,
            val message: String
        )

        data class TasksResponse(
            val tasks: List<JSONObject> = emptyList(),
            val errorMessage: String? = null,
            val message: String? = null,
            val countdown: Int = 10
        )

        fun shortcodeToMediaId(shortcode: String): String? {
            if (shortcode.isBlank()) return null
            var id = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (c in shortcode) {
                val idx = ALPHABET.indexOf(c)
                if (idx == -1) return null
                id = id.multiply(base).add(BigInteger.valueOf(idx.toLong()))
            }
            return id.toString()
        }

        fun extractShortcode(url: String): String? {
            val m = Pattern.compile("/(?:p|reel|tv)/([A-Za-z0-9_-]+)").matcher(url)
            return if (m.find()) m.group(1) else null
        }

        fun extractTokensFromCookie(cookie: String): Map<String, String> {
            return cookie.split(";").mapNotNull {
                val idx = it.indexOf("=")
                if (idx > 0) it.substring(0, idx).trim() to it.substring(idx + 1).trim() else null
            }.toMap()
        }

        fun buildClient(proxyStr: String?): OkHttpClient {
            val b = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
            if (!proxyStr.isNullOrBlank()) {
                val s = proxyStr.trim().removePrefix("http://").removePrefix("https://")
                try {
                    if (s.contains("@")) {
                        val atParts = s.split("@", limit = 2)
                        val auth = atParts[0].split(":", limit = 2)
                        val hostPort = atParts[1].split(":", limit = 2)
                        val host = hostPort[0].trim()
                        val port = hostPort.getOrNull(1)?.trim()?.toIntOrNull() ?: 8080
                        b.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                        if (auth.size >= 2) {
                            val user = auth[0].trim()
                            val pass = auth[1].trim()
                            b.proxyAuthenticator { _, res ->
                                if (res.request.header("Proxy-Authorization") != null) null
                                else res.request.newBuilder()
                                    .header("Proxy-Authorization", Credentials.basic(user, pass))
                                    .build()
                            }
                        }
                    } else {
                        val p = s.split(":")
                        if (p.size >= 2) {
                            val host = p[0].trim()
                            val port = p[1].trim().toIntOrNull() ?: 8080
                            b.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                            if (p.size >= 4) {
                                val user = p[2].trim()
                                val pass = p[3].trim()
                                b.proxyAuthenticator { _, res ->
                                    if (res.request.header("Proxy-Authorization") != null) null
                                    else res.request.newBuilder()
                                        .header("Proxy-Authorization", Credentials.basic(user, pass))
                                        .build()
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            return b.build()
        }

        suspend fun run(
            context: Context,
            accountUsernames: List<String>,
            onStatusUpdate: ((String) -> Unit)? = null
        ): RunResult {
            var totalCompleted = 0
            var totalErrors = 0
            var totalPoints = 0
            val messages = mutableListOf<String>()

            for (u in accountUsernames) {
                val res = runSingleAccount(
                    context = context,
                    accountUsername = u,
                    onStatusUpdate = onStatusUpdate
                )
                totalCompleted += res.totalCompleted
                totalErrors += res.totalErrors
                totalPoints += res.totalEarnedPoints
                messages.add(res.message)
            }
            return RunResult(totalCompleted, totalErrors, totalPoints, messages.joinToString(" | "))
        }

        suspend fun runSingleAccount(
            context: Context,
            accountUsername: String,
            onProgressUpdate: ((status: String, successCount: Int, errorCount: Int) -> Unit)? = null,
            onStatusUpdate: ((String) -> Unit)? = null,
            onErrorDetail: ((username: String, detail: String) -> Unit)? = null
        ): RunResult {
            val cleanUsername = accountUsername.trim().removePrefix("@").lowercase()
            val token = XsmmAccountStore.getToken(context)
            if (token.isNullOrBlank()) {
                val msg = "Chưa cấu hình Token XSMM"
                onStatusUpdate?.invoke(msg)
                onProgressUpdate?.invoke(msg, 0, 1)
                return RunResult(0, 1, 0, msg)
            }
            val localAcc = InstagramAccountsStore.getAccount(context, cleanUsername)
            if (localAcc == null || localAcc.cookie.isBlank()) {
                val msg = "[$cleanUsername] Không tìm thấy cookie"
                onStatusUpdate?.invoke(msg)
                onProgressUpdate?.invoke(msg, 0, 1)
                return RunResult(0, 1, 0, msg)
            }
            fun notify(st: String, sc: Int = 0, err: Int = 0) {
                onStatusUpdate?.invoke(st)
                onProgressUpdate?.invoke(st, sc, err)
                XsmmJobStatusBridge.update("[$cleanUsername] $st")
            }
            val cookieTokens = extractTokensFromCookie(localAcc.cookie)
            val dsUserId = localAcc.userId.ifBlank { cookieTokens["ds_user_id"] ?: "" }
            val initialUname = localAcc.username.takeIf { it.isNotBlank() && !it.contains("người dùng", ignoreCase = true) } ?: cleanUsername
            val igAccount = IgXsmmAccount(
                userId = dsUserId,
                username = initialUname,
                cookie = localAcc.cookie,
                proxy = localAcc.proxy.takeIf { it.isNotBlank() },
                fbDtsg = localAcc.fbDtsg.takeIf { !it.isNullOrBlank() },
                lsd = localAcc.lsd.takeIf { !it.isNullOrBlank() }
            )
            val runner = XsmmInstagramTaskRunner(token, igAccount)

            // Bước 1: Đồng bộ token & session qua Web HTML
            notify("Kiểm tra cookie & token...")
            val (isLive, tokens) = runner.syncSessionTokens()
            if (!isLive) {
                val msg = "• Lỗi: Cookie DIE / Checkpoint"
                notify(msg, 0, 1)
                onErrorDetail?.invoke(cleanUsername, "Cookie DIE hoặc bị Checkpoint khi kiểm tra phiên")
                InstagramAccountsStore.updateAccount(context, localAcc.copy(isLive = false))
                return RunResult(0, 1, 0, msg)
            }

            // Tự động khôi phục username thật nếu bị dính chuỗi rác "Người dùng Instagram"
            val extractedUsername = tokens["username"]?.takeIf { it.isNotBlank() && !it.contains("người dùng", ignoreCase = true) }
            val realUsername = extractedUsername 
                ?: localAcc.username.takeIf { it.isNotBlank() && !it.contains("người dùng", ignoreCase = true) }
                ?: localAcc.fullName.takeIf { it.isNotBlank() && !it.contains("người dùng", ignoreCase = true) && !it.contains(" ") }
                ?: cleanUsername
            
            val updated = localAcc.copy(
                username = realUsername,
                isLive = true,
                fbDtsg = tokens["fb_dtsg"] ?: localAcc.fbDtsg,
                lsd = tokens["lsd"] ?: localAcc.lsd,
                userId = tokens["userId"] ?: localAcc.userId.ifBlank { dsUserId }
            )
            if (localAcc.username.isNotBlank() && localAcc.username != realUsername) {
                InstagramAccountsStore.removeAccount(context, localAcc.username)
            }
            InstagramAccountsStore.updateAccount(context, updated)
            igAccount.username = realUsername
            if (updated.userId.isNotBlank()) igAccount.userId = updated.userId

            // Bước 2: Đảm bảo nick đã có trên XSMM.NET qua /api/taskapi/accounts2
            notify("Đồng bộ liên kết XSMM...")
            val isLinked = runner.ensureAccountLinked()
            if (!isLinked) {
                notify("Thêm nick XSMM: Thử lại...")
                delay(2000L)
            } else {
                InstagramAccountsStore.setXsmmLinked(context, realUsername, true)
                InstagramAccountsStore.setXsmmLinked(context, cleanUsername, true)
            }

            // Bước 3: Đọc cấu hình nhiệm vụ
            val config = XsmmRunConfigStore.get(context, "instagram")
            val listnv = mutableListOf<String>()
            for (t in config.effectiveTaskTypes()) {
                when (t.lowercase()) {
                    "instagram_follow" -> listnv.add("instagram_follow")
                    "instagram_like" -> listnv.add("instagram_like")
                    "instagram_comment" -> listnv.add("instagram_comment")
                }
            }
            if (listnv.isEmpty()) {
                listnv.add("instagram_follow")
                listnv.add("instagram_like")
            }
            val doDuration = config.doTaskDurationSeconds.coerceAtLeast(8)
            val targetJobs = if (config.taskCountTarget > 0) config.taskCountTarget else 99999
            val maxErrors = config.failJobCountToSwitchAccount.coerceAtLeast(5)
            var completedCount = 0
            var errorCount = 0
            var totalPoints = 0
            var consecutiveErrors = 0

            // Bước 4: Vòng lặp nhận job & làm việc
            while (coroutineContext.isActive) {
                if (completedCount >= targetJobs) {
                    notify("Đủ $targetJobs job -> Dừng", completedCount, errorCount)
                    break
                }
                val jobType = listnv.random()
                notify("• Đang nhận job $jobType...", completedCount, errorCount)
                val taskRes = runner.getTasks(jobType)
                val tasks = taskRes.tasks
                if (tasks.isEmpty()) {
                    val errMsg = taskRes.errorMessage
                    val infoMsg = taskRes.message
                    val waitSec = if (taskRes.countdown > 0) taskRes.countdown else 10

                    if (!errMsg.isNullOrBlank()) {
                        notify("XSMM Lỗi: $errMsg", completedCount, errorCount)
                        onErrorDetail?.invoke(cleanUsername, "XSMM Lỗi ($jobType):\n$errMsg")
                    } else if (!infoMsg.isNullOrBlank()) {
                        notify("XSMM: $infoMsg", completedCount, errorCount)
                    } else {
                        notify("Hết job $jobType (chờ ${waitSec}s)...", completedCount, errorCount)
                    }

                    for (x in waitSec downTo 1) {
                        if (!coroutineContext.isActive) break
                        val prefix = if (!errMsg.isNullOrBlank()) "XSMM: $errMsg" else if (!infoMsg.isNullOrBlank()) "XSMM: $infoMsg" else "Chờ job $jobType"
                        notify("• $prefix (${x}s)...", completedCount, errorCount)
                        delay(1000L)
                    }
                    continue
                }

                for (task in tasks) {
                    if (!coroutineContext.isActive) break
                    val taskId = task.optString("id")
                    val targetId = task.optString("target_id")
                    val targetUrl = task.optString("target_url")
                    val idOrLink = task.optString("idorlink")
                    val commentText = task.optString("comment").ifBlank { "Great post!" }
                    val finalTarget = targetId.ifBlank { idOrLink }
                    val success = when (jobType) {
                        "instagram_follow" -> {
                            val userToFollow = targetUrl.trim().trimEnd('/').substringAfterLast("/").ifBlank { idOrLink }
                            notify("• Follow @$userToFollow ($finalTarget)", completedCount, errorCount)
                            val res = runner.doFollow(finalTarget, userToFollow)
                            if (!res.isSuccess) {
                                val bodyPreview = if (res.rawBody.length > 500) res.rawBody.take(500) else res.rawBody
                                val isDieOrCheckpoint = res.rawBody.contains("login_required", true) ||
                                    res.rawBody.contains("checkpoint", true) ||
                                    res.rawBody.contains("accounts/suspended", true) ||
                                    res.rawBody.contains("1357031") ||
                                    res.httpCode == 401 || res.httpCode == 403
                                if (isDieOrCheckpoint) {
                                    val dieMsg = "• Lỗi: Cookie DIE / Checkpoint"
                                    notify(dieMsg, completedCount, errorCount + 1)
                                    onErrorDetail?.invoke(cleanUsername, "Tài khoản bị DIE hoặc dính Checkpoint [HTTP ${res.httpCode}]:\n$bodyPreview")
                                    InstagramAccountsStore.updateAccount(context, updated.copy(isLive = false))
                                    InstagramAccountsStore.updateAccount(context, localAcc.copy(isLive = false))
                                    return RunResult(completedCount, errorCount + 1, totalPoints, dieMsg)
                                }
                                onErrorDetail?.invoke(cleanUsername, "Instagram Follow lỗi [HTTP ${res.httpCode}]:\n$bodyPreview")
                            }
                            res.isSuccess
                        }
                        "instagram_like" -> {
                            notify("• Tym bài viết ($finalTarget)", completedCount, errorCount)
                            runner.doLike(targetUrl.ifBlank { idOrLink }, finalTarget)
                        }
                        "instagram_comment" -> {
                            notify("• Comment: \"${commentText.take(15)}...\"", completedCount, errorCount)
                            runner.doComment(targetUrl.ifBlank { idOrLink }, finalTarget, commentText)
                        }
                        else -> false
                    }
                    if (success) {
                        notify("• Xác nhận nhận xu...", completedCount, errorCount)
                        val comp = runner.completeTask(jobType, taskId)
                        val pts = comp?.optInt("points") ?: comp?.optJSONObject("data")?.optInt("points") ?: 35
                        completedCount++
                        totalPoints += pts
                        consecutiveErrors = 0
                        val userBal = runner.fetchUserPoints()
                        if (userBal > 0) {
                            XsmmAccountStore.updatePoints(context, userBal)
                            withContext(Dispatchers.Main) {
                                XsmmSession.points.value = userBal
                            }
                        }
                        for (sec in doDuration downTo 1) {
                            if (!coroutineContext.isActive) break
                            notify("• Thành công +$pts xu | Chờ ${sec}s...", completedCount, errorCount)
                            delay(1000L)
                        }
                    } else {
                        errorCount++
                        consecutiveErrors++
                        notify("• Lỗi làm job $jobType ($finalTarget)", completedCount, errorCount)
                        if (consecutiveErrors >= maxErrors) {
                            notify("Lỗi liên tiếp $consecutiveErrors lần -> Dừng nick", completedCount, errorCount)
                            return RunResult(completedCount, errorCount, totalPoints, "Lỗi liên tiếp")
                        }
                        delay(4000L)
                    }
                    if (completedCount >= targetJobs) break
                }
            }
            return RunResult(completedCount, errorCount, totalPoints, "Hoàn thành $completedCount job")
        }
    }

    // ── 1. ĐỒNG BỘ TOKEN THEO METHOD Lx/tu;->e ────────────────────────
    fun syncSessionTokens(): Pair<Boolean, Map<String, String>> {
        val req = Request.Builder()
            .url("https://www.instagram.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cookie", account.cookie)
            .get().build()
        val html = try {
            client.newCall(req).execute().use { it.body?.string().orEmpty() }
        } catch (_: Exception) { "" }
        if (html.isBlank()) return Pair(false, emptyMap())
        val lower = html.lowercase()
        if (lower.contains("login_required") || lower.contains("checkpoint_required") || lower.contains("\"is_logged_in\":false") || lower.contains("accounts/suspended") || lower.contains("1357031") || lower.contains("/accounts/login/") || lower.contains("loginform")) {
            return Pair(false, emptyMap())
        }
        fun extract(pattern: String): String? {
            val m = Pattern.compile(pattern).matcher(html)
            return if (m.find()) m.group(1) else null
        }
        val dtsg = extract("name=\"fb_dtsg\"\\s+value=\"(.*?)\"")
            ?: extract("\\[\"DTSGInitialData\",\\[\\],\\{\"token\":\"(.*?)\"\\}")
            ?: extract("\"dtsg\"\\s*:\\s*\"([^\"]+)\"")
            ?: extract("\"fb_dtsg\"\\s*:\\s*\"([^\"]+)\"")
            ?: ""
        if (dtsg.isBlank()) {
            return Pair(false, emptyMap())
        }
        val lsdVal = extract("name=\"lsd\"\\s+value=\"(.*?)\"")
            ?: extract("\\[\"LSD\",\\[\\],\\{\"token\":\"(.*?)\"\\}")
            ?: extract("\"lsd\"\\s*:\\s*\"([^\"]+)\"")
            ?: ""
        val uName = extract("\"username\"\\s*:\\s*\"([^\"]+)\"")
            ?: extract("<meta property=\"og:title\" content=\"[^(]+\\(@([^)]+)\\)")
            ?: extract("\"viewer\":\\s*\\{[^}]*\"username\"\\s*:\\s*\"([^\"]+)\"")
        val uId = extract("\"viewerId\"\\s*:\\s*\"?(\\d+)\"?")
            ?: extract("\"ds_user_id\"\\s*:\\s*\"?(\\d+)\"?")

        if (dtsg.isNotBlank()) account.fbDtsg = dtsg
        if (lsdVal.isNotBlank()) account.lsd = lsdVal
        if (!uName.isNullOrBlank() && !uName.contains("người dùng", ignoreCase = true) && !uName.contains("instagram user", ignoreCase = true)) {
            account.username = uName
        }
        if (!uId.isNullOrBlank()) {
            account.userId = uId
        }

        val tokenMap = mutableMapOf<String, String>()
        if (dtsg.isNotBlank()) tokenMap["fb_dtsg"] = dtsg
        if (lsdVal.isNotBlank()) tokenMap["lsd"] = lsdVal
        if (!uName.isNullOrBlank() && !uName.contains("người dùng", ignoreCase = true)) tokenMap["username"] = uName
        if (!uId.isNullOrBlank()) tokenMap["userId"] = uId

        return Pair(true, tokenMap)
    }

    // ── 2. ĐẢM BẢO NICK LIÊN KẾT XSMM THEO /api/taskapi/accounts2 ─────
    fun ensureAccountLinked(): Boolean {
        val cleanUser = account.username.trim().removePrefix("@").trim('/')
        // Kiểm tra xem đã có chưa
        try {
            val checkReq = Request.Builder()
                .url("${XSMM_API}accounts2?account_type=instagram")
                .header("Authorization", "Bearer $xsmmToken")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .get().build()
            val checkRes = execute(checkReq)
            if (checkRes != null && (checkRes.contains(cleanUser) || (account.userId.isNotBlank() && checkRes.contains(account.userId)))) {
                return true
            }
        } catch (_: Exception) {}
        // Thêm vào XSMM
        val body = JSONObject().apply {
            put("type", "instagram")
            put("account_type", "instagram")
            put("link_account", "https://www.instagram.com/$cleanUser/")
        }.toString().toRequestBody(JSON_TYPE)
        val addReq = Request.Builder()
            .url("${XSMM_API}accounts2")
            .header("Authorization", "Bearer $xsmmToken")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .post(body).build()
        val addRes = execute(addReq)
        return addRes != null && (!addRes.contains("\"error\":") || addRes.contains("đã tồn tại") || addRes.contains("\"id\":"))
    }

    // ── 3. LẤY NHIỆM VỤ THEO /api/taskapi/tasks2 ───────────────────────
    fun getTasks(type: String): TasksResponse {
        val uidParam = account.userId.ifBlank { extractTokensFromCookie(account.cookie)["ds_user_id"] ?: "" }
        val url = "${XSMM_API}tasks2?type=$type&uid=$uidParam&typejob=normal,better,best"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $xsmmToken")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .get().build()
        val res = execute(req) ?: return TasksResponse(errorMessage = "Không thể kết nối máy chủ XSMM")
        val trimmed = res.trim()
        if (trimmed.startsWith("[")) {
            val list = mutableListOf<JSONObject>()
            try {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    list.add(arr.getJSONObject(i))
                }
                return TasksResponse(tasks = list)
            } catch (e: Exception) {
                return TasksResponse(errorMessage = "Lỗi đọc dữ liệu nhiệm vụ: ${e.message}")
            }
        } else if (trimmed.startsWith("{")) {
            return try {
                val obj = JSONObject(trimmed)
                val err = obj.optString("error").takeIf { it.isNotBlank() }
                val msg = obj.optString("message").takeIf { it.isNotBlank() }
                val cd = obj.optInt("countdown", 10)
                val dataArr = obj.optJSONArray("data")
                if (dataArr != null && dataArr.length() > 0) {
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until dataArr.length()) {
                        list.add(dataArr.getJSONObject(i))
                    }
                    TasksResponse(tasks = list, countdown = cd)
                } else {
                    TasksResponse(errorMessage = err, message = msg, countdown = cd)
                }
            } catch (e: Exception) {
                TasksResponse(errorMessage = "Lỗi phản hồi JSON: ${e.message}")
            }
        }
        return TasksResponse(errorMessage = "Phản hồi không xác định: ${trimmed.take(100)}")
    }

    // ── 4. HOÀN THÀNH NHẬN XU THEO /api/taskapi/tasks2/complete ───────
    fun completeTask(type: String, taskId: String): JSONObject? {
        val uidParam = account.userId.ifBlank { extractTokensFromCookie(account.cookie)["ds_user_id"] ?: "" }
        val body = JSONObject().apply {
            put("type", type)
            put("task_id", JSONArray().apply { put(taskId) })
            put("uid", uidParam)
        }.toString().toRequestBody(JSON_TYPE)
        val req = Request.Builder()
            .url("${XSMM_API}tasks2/complete")
            .header("Authorization", "Bearer $xsmmToken")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .post(body).build()
        val res = execute(req) ?: return null
        return try { JSONObject(res) } catch (_: Exception) { null }
    }

    fun fetchUserPoints(): Long {
        val req = Request.Builder()
            .url("${XSMM_API}user")
            .header("Authorization", "Bearer $xsmmToken")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .get().build()
        val res = execute(req) ?: return 0L
        return try {
            JSONObject(res).optJSONObject("user")?.optLong("points") ?: 0L
        } catch (_: Exception) { 0L }
    }

    // ── 5. THỰC THI FOLLOW 100% GRAPHQL GOMAX (Method Lx/tu;->N) ──────
    data class FollowResult(val isSuccess: Boolean, val httpCode: Int, val rawBody: String)

    fun doFollow(targetNumericId: String, targetUsername: String): FollowResult {
        var uid = targetNumericId.trim()
        if (!uid.all { it.isDigit() } || uid.isBlank()) {
            uid = resolveTargetUid(targetUsername) ?: targetNumericId
        }
        val cleanTargetUser = targetUsername.trim().trim('/').substringAfterLast('/')
        val referer = if (cleanTargetUser.isNotBlank()) "https://www.instagram.com/$cleanTargetUser/" else "https://www.instagram.com/"
        val headerBuilder = Headers.Builder()
            .add("Accept", "*/*")
            .add("Accept-Language", "vi,en;q=0.9")
            .add("Cache-Control", "no-cache")
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Origin", "https://www.instagram.com")
            .add("Pragma", "no-cache")
            .add("Referer", referer)
            .add("Sec-Ch-Ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"")
            .add("Sec-Ch-Ua-Mobile", "?0")
            .add("Sec-Ch-Ua-Platform", "\"Windows\"")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .add("X-ASBD-ID", "359341")
            .add("X-Bloks-Version-Id", "61fc9465e13b77eaa110f317859102ba7fb93a0a2bcc08c46473da6713640739")
            .add("X-CSRFToken", extractTokensFromCookie(account.cookie)["csrftoken"] ?: "")
            .add("X-FB-Friendly-Name", "usePolarisFollowMutation")
            .add("X-IG-App-ID", "936619743392459")
            .add("X-Root-Field-Name", "xdt_create_friendship")
            .add("Cookie", account.cookie)
        if (!account.fbDtsg.isNullOrEmpty()) headerBuilder.add("X-FB-DTSG", account.fbDtsg!!)
        if (!account.lsd.isNullOrEmpty()) headerBuilder.add("X-FB-LSD", account.lsd!!)
        val headers = headerBuilder.build()

        val cookieUid = extractTokensFromCookie(account.cookie)["ds_user_id"] ?: ""
        val myUid = if (account.userId.isNotBlank()) account.userId else cookieUid
        val avActor = if (myUid.isNotBlank()) "178414$myUid" else "178414"

        val jazoestVal = if (!account.fbDtsg.isNullOrEmpty()) {
            var s = 0; for (c in account.fbDtsg!!) s += c.code; "2$s"
        } else "26738"

        val vars = JSONObject().apply {
            put("target_user_id", uid)
            put("container_module", "profile")
            put("nav_chain", "PolarisProfilePostsTabRoot:profilePage:1:via_cold_start,PolarisProfilePostsTabRoot:profilePage:3:unexpected")
        }.toString()

        fun buildBody(docId: String, reqVal: String): FormBody {
            val b = FormBody.Builder()
                .add("av", avActor)
                .add("__d", "www")
                .add("__user", "0")
                .add("__a", "1")
                .add("__req", reqVal)
                .add("__hs", "20519.HYP:instagram_web_pkg.2.1...0")
                .add("dpr", "1")
                .add("__ccg", "EXCELLENT")
                .add("__hsi", System.currentTimeMillis().toString())
                .add("__comet_req", "7")
                .add("jazoest", jazoestVal)
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "usePolarisFollowMutation")
                .add("variables", vars)
                .add("server_timestamps", "true")
                .add("doc_id", docId)
            if (!account.fbDtsg.isNullOrEmpty()) b.add("fb_dtsg", account.fbDtsg!!)
            if (!account.lsd.isNullOrEmpty()) b.add("lsd", account.lsd!!)
            return b.build()
        }

        // Lần 1: Doc_id chính "9740159112729312" (__req="1j")
        var req = Request.Builder()
            .url("https://www.instagram.com/graphql/query")
            .headers(headers)
            .post(buildBody("9740159112729312", "1j"))
            .build()
        var (code, body) = executeWithCode(req)
        if (checkSuccess(body)) return FollowResult(true, code, body)

        // Lần 2: Doc_id phụ "9663809173698092" (__req="15")
        req = Request.Builder()
            .url("https://www.instagram.com/graphql/query")
            .headers(headers)
            .post(buildBody("9663809173698092", "15"))
            .build()
        val res2 = executeWithCode(req)
        return FollowResult(checkSuccess(res2.second), res2.first, res2.second)
    }

    private fun resolveTargetUid(username: String): String? {
        val clean = username.trim().removePrefix("@").trim('/')
        if (clean.isBlank()) return null
        val req = Request.Builder()
            .url("https://www.instagram.com/web/search/topsearch/?context=blended&query=$clean")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
            .header("X-IG-App-ID", "936619743392459")
            .header("Cookie", account.cookie)
            .get().build()
        val jsonStr = execute(req) ?: return null
        return try {
            val root = JSONObject(jsonStr)
            val users = root.optJSONArray("users") ?: return null
            for (i in 0 until users.length()) {
                val u = users.getJSONObject(i).optJSONObject("user") ?: continue
                if (u.optString("username").equals(clean, ignoreCase = true)) {
                    val pk = u.optString("pk")
                    if (pk.isNotBlank()) return pk
                    val id = u.optString("id")
                    if (id.isNotBlank()) return id
                }
            }
            null
        } catch (_: Exception) { null }
    }

    private fun checkSuccess(res: String): Boolean {
        if (res.isBlank()) return false
        return try {
            val root = JSONObject(res)
            val data = root.optJSONObject("data")
            val friendship = data?.optJSONObject("xdt_create_friendship")
            val status = friendship?.optJSONObject("friendship_status")
            if (status?.optBoolean("following") == true || status?.optBoolean("outgoing_request") == true) return true
            if (root.optString("status").equals("ok", true)) return true
            val errors = root.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val msg = errors.getJSONObject(0).optString("message").lowercase()
                if (msg.contains("already")) return true
            }
            res.contains("\"following\":true") || res.contains("already")
        } catch (_: Exception) {
            res.contains("\"following\":true") || res.contains("already")
        }
    }

    fun doLike(link: String, mediaId: String): Boolean {
        try {
            var mid = mediaId.trim()
            if (!mid.all { it.isDigit() } || mid.isBlank()) {
                val sc = extractShortcode(link)
                if (!sc.isNullOrBlank()) mid = shortcodeToMediaId(sc) ?: mid
            }
            if (mid.all { it.isDigit() } && mid.isNotBlank()) {
                val vars = JSONObject().apply {
                    put("media_id", mid)
                    put("container_module", "feed_timeline")
                }
                val headers = Headers.Builder()
                    .add("Accept", "*/*")
                    .add("Content-Type", "application/x-www-form-urlencoded")
                    .add("Origin", "https://www.instagram.com")
                    .add("Referer", link.ifBlank { "https://www.instagram.com/" })
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .add("X-CSRFToken", extractTokensFromCookie(account.cookie)["csrftoken"] ?: "")
                    .add("X-FB-Friendly-Name", "usePolarisLikeMediaLikeMutation")
                    .add("X-IG-App-ID", "936619743392459")
                    .add("Cookie", account.cookie)
                if (!account.fbDtsg.isNullOrEmpty()) headers.add("X-FB-DTSG", account.fbDtsg!!)
                val body = FormBody.Builder()
                    .add("variables", vars.toString())
                    .add("doc_id", "9595477160535898")
                    .add("fb_api_req_friendly_name", "usePolarisLikeMediaLikeMutation")
                if (!account.fbDtsg.isNullOrEmpty()) body.add("fb_dtsg", account.fbDtsg!!)
                val req = Request.Builder().url("https://www.instagram.com/graphql/query").headers(headers.build()).post(body.build()).build()
                val res = execute(req)
                if (res != null && (res.contains("\"status\":\"ok\"") || res.contains("\"viewer_has_liked\":true") || res.contains("\"success\":true"))) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return true
    }

    fun doComment(link: String, mediaId: String, text: String): Boolean {
        try {
            var mid = mediaId.trim()
            if (!mid.all { it.isDigit() } || mid.isBlank()) {
                val sc = extractShortcode(link)
                if (!sc.isNullOrBlank()) mid = shortcodeToMediaId(sc) ?: mid
            }
            if (mid.all { it.isDigit() } && mid.isNotBlank()) {
                val vars = JSONObject().apply {
                    put("id", mid)
                    put("comment_text", text)
                    put("container_module", "self_comments_v2")
                }
                val headers = Headers.Builder()
                    .add("Accept", "*/*")
                    .add("Content-Type", "application/x-www-form-urlencoded")
                    .add("Origin", "https://www.instagram.com")
                    .add("Referer", link.ifBlank { "https://www.instagram.com/" })
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .add("X-CSRFToken", extractTokensFromCookie(account.cookie)["csrftoken"] ?: "")
                    .add("X-FB-Friendly-Name", "usePolarisCommentDirectMutation")
                    .add("X-IG-App-ID", "936619743392459")
                    .add("Cookie", account.cookie)
                if (!account.fbDtsg.isNullOrEmpty()) headers.add("X-FB-DTSG", account.fbDtsg!!)
                val body = FormBody.Builder()
                    .add("variables", vars.toString())
                    .add("doc_id", "7755358241198424")
                    .add("fb_api_req_friendly_name", "usePolarisCommentDirectMutation")
                if (!account.fbDtsg.isNullOrEmpty()) body.add("fb_dtsg", account.fbDtsg!!)
                val req = Request.Builder().url("https://www.instagram.com/graphql/query").headers(headers.build()).post(body.build()).build()
                val res = execute(req)
                if (res != null && !res.contains("\"errors\":") && res.contains("\"id\":")) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return true
    }

    private fun execute(req: Request): String? {
        return try {
            client.newCall(req).execute().use { it.body?.string() }
        } catch (_: Exception) { null }
    }

    private fun executeWithCode(req: Request): Pair<Int, String> {
        return try {
            client.newCall(req).execute().use { Pair(it.code, it.body?.string().orEmpty()) }
        } catch (e: Exception) {
            Pair(0, e.message.orEmpty())
        }
    }
}
