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
    val account: IgXsmmAccount
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
                val msg = "• Lỗi: Kháng nghị / Checkpoint / DIE"
                notify(msg, 0, 1)
                val detailMsg = runner.account.lastErrorMessage ?: "Tài khoản bị Kháng nghị / Checkpoint / DIE khi kiểm tra phiên"
                onErrorDetail?.invoke(cleanUsername, detailMsg)
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
                if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException("User stopped")
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
                        if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException("User stopped")
                        val prefix = if (!errMsg.isNullOrBlank()) "XSMM: $errMsg" else if (!infoMsg.isNullOrBlank()) "XSMM: $infoMsg" else "Chờ job $jobType"
                        notify("• $prefix (${x}s)...", completedCount, errorCount)
                        delay(1000L)
                        if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException("User stopped")
                    }
                    continue
                }

                val pendingFollowTaskIds = mutableListOf<String>()
                for (task in tasks) {
                    if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException("User stopped")
                    val taskId = task.optString("id")
                    val targetId = task.optString("target_id")
                    val targetUrl = task.optString("target_url")
                    val idOrLink = task.optString("idorlink")
                    val commentText = task.optString("comment").ifBlank { "Great post!" }
                    val finalTarget = targetId.ifBlank { idOrLink }

                    val actionResult = when (jobType) {
                        "instagram_follow" -> {
                            val userToFollow = targetUrl.trim().trimEnd('/').substringAfterLast("/").ifBlank { idOrLink }
                            notify("• Follow @$userToFollow ($finalTarget)", completedCount, errorCount)
                            runner.doFollow(finalTarget, userToFollow, targetUrl)
                        }
                        "instagram_like" -> {
                            notify("• Tym bài viết ($finalTarget)", completedCount, errorCount)
                            runner.doLike(targetUrl.ifBlank { idOrLink }, finalTarget)
                        }
                        "instagram_comment" -> {
                            notify("• Comment: \"${commentText.take(15)}...\"", completedCount, errorCount)
                            runner.doComment(targetUrl.ifBlank { idOrLink }, finalTarget, commentText)
                        }
                        else -> IgActionResult(false, 400, "", "Loại job không hợp lệ")
                    }

                    if (!actionResult.isSuccess) {
                        errorCount++
                        consecutiveErrors++
                        val errReason = actionResult.errorMessage ?: "Lỗi tương tác Instagram"
                        val isDieOrCheckpoint = actionResult.rawBody.contains("login_required", true) ||
                                actionResult.rawBody.contains("checkpoint", true) ||
                                actionResult.rawBody.contains("accounts/suspended", true) ||
                                actionResult.rawBody.contains("1357031") ||
                                actionResult.httpCode == 401 || actionResult.httpCode == 403

                        if (isDieOrCheckpoint) {
                            val dieMsg = "• Lỗi: Cookie DIE / Checkpoint"
                            notify(dieMsg, completedCount, errorCount)
                            onErrorDetail?.invoke(cleanUsername, "Tài khoản bị DIE hoặc dính Checkpoint [HTTP ${actionResult.httpCode}]:\n$errReason\n${actionResult.rawBody.take(300)}")
                            InstagramAccountsStore.updateAccount(context, updated.copy(isLive = false))
                            InstagramAccountsStore.updateAccount(context, localAcc.copy(isLive = false))
                            return RunResult(completedCount, errorCount, totalPoints, dieMsg)
                        }

                        notify("• Lỗi: $errReason", completedCount, errorCount)
                        onErrorDetail?.invoke(cleanUsername, "Instagram $jobType lỗi [Mã ${actionResult.httpCode}]:\n$errReason\n${actionResult.rawBody.take(300)}")

                        if (consecutiveErrors >= maxErrors) {
                            notify("Lỗi liên tiếp $consecutiveErrors lần -> Dừng nick", completedCount, errorCount)
                            return RunResult(completedCount, errorCount, totalPoints, "Lỗi liên tiếp")
                        }
                        delay(4000L)
                    } else {
                        // Thao tác IG thành công -> Báo XSMM
                        consecutiveErrors = 0
                        if (jobType == "instagram_follow") {
                            pendingFollowTaskIds.add(taskId)
                            completedCount++
                            notify("• Follow thành công ($completedCount)", completedCount, errorCount)
                            if (pendingFollowTaskIds.size >= 10) {
                                notify("• Gửi xác nhận 10 follow...", completedCount, errorCount)
                                val comp = runner.completeTasks(jobType, pendingFollowTaskIds)
                                val points = comp?.optInt("points") ?: comp?.optJSONObject("data")?.optInt("points") ?: comp?.optInt("earned_points") ?: 0
                                val isSuccess = points > 0 || comp?.optBoolean("success", false) == true || comp?.optString("status").equals("success", true)
                                if (isSuccess) {
                                    val pts = if (points > 0) points else (pendingFollowTaskIds.size * 35)
                                    totalPoints += pts
                                    notify("• +$pts xu (10 follow)", completedCount, errorCount)
                                } else {
                                    val xsmmErr = comp?.optString("error")?.takeIf { it.isNotBlank() } ?: comp?.optString("message")?.takeIf { it.isNotBlank() } ?: "XSMM từ chối cộng xu"
                                    notify("• Lỗi XSMM: $xsmmErr", completedCount, errorCount)
                                    onErrorDetail?.invoke(cleanUsername, "XSMM không cộng xu 10 follow:\n$xsmmErr\nPhản hồi: ${comp?.toString()?.take(200)}")
                                }
                                pendingFollowTaskIds.clear()
                                val userBal = runner.fetchUserPoints()
                                if (userBal > 0) {
                                    XsmmAccountStore.updatePoints(context, userBal)
                                    withContext(Dispatchers.Main) {
                                        XsmmSession.points.value = userBal
                                    }
                                }
                            }
                        } else {
                            notify("• Xác nhận nhận xu...", completedCount, errorCount)
                            val comp = runner.completeTask(jobType, taskId)
                            val points = comp?.optInt("points") ?: comp?.optJSONObject("data")?.optInt("points") ?: comp?.optInt("earned_points") ?: 0
                            val isSuccess = points > 0 || comp?.optBoolean("success", false) == true || comp?.optString("status").equals("success", true)
                            if (isSuccess) {
                                val pts = if (points > 0) points else 35
                                completedCount++
                                totalPoints += pts
                                notify("• +$pts xu (Thành công)", completedCount, errorCount)
                            } else {
                                errorCount++
                                consecutiveErrors++
                                val xsmmErr = comp?.optString("error")?.takeIf { it.isNotBlank() } ?: comp?.optString("message")?.takeIf { it.isNotBlank() } ?: "XSMM từ chối cộng xu"
                                notify("• Lỗi XSMM: $xsmmErr", completedCount, errorCount)
                                onErrorDetail?.invoke(cleanUsername, "XSMM không cộng xu ($jobType):\n$xsmmErr\nPhản hồi: ${comp?.toString()?.take(200)}")
                            }
                            val userBal = runner.fetchUserPoints()
                            if (userBal > 0) {
                                XsmmAccountStore.updatePoints(context, userBal)
                                withContext(Dispatchers.Main) {
                                    XsmmSession.points.value = userBal
                                }
                            }
                        }

                        for (sec in doDuration downTo 1) {
                            if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException("User stopped")
                            notify("• Thành công | Chờ ${sec}s...", completedCount, errorCount)
                            delay(1000L)
                            if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException("User stopped")
                        }
                    }
                    if (completedCount >= targetJobs) break
                }

                if (pendingFollowTaskIds.isNotEmpty()) {
                    notify("• Gửi xác nhận ${pendingFollowTaskIds.size} follow...", completedCount, errorCount)
                    val comp = runner.completeTasks("instagram_follow", pendingFollowTaskIds)
                    val points = comp?.optInt("points") ?: comp?.optJSONObject("data")?.optInt("points") ?: comp?.optInt("earned_points") ?: 0
                    val isSuccess = points > 0 || comp?.optBoolean("success", false) == true || comp?.optString("status").equals("success", true)
                    if (isSuccess) {
                        val pts = if (points > 0) points else (pendingFollowTaskIds.size * 35)
                        totalPoints += pts
                        notify("• +$pts xu (${pendingFollowTaskIds.size} follow)", completedCount, errorCount)
                    }
                    pendingFollowTaskIds.clear()
                    val userBal = runner.fetchUserPoints()
                    if (userBal > 0) {
                        XsmmAccountStore.updatePoints(context, userBal)
                        withContext(Dispatchers.Main) {
                            XsmmSession.points.value = userBal
                        }
                    }
                }
            }
            return RunResult(completedCount, errorCount, totalPoints, "Hoàn thành $completedCount job")
        }
    }

    // ── 1. ĐỒNG BỘ TOKEN THEO IgTaToolClient (Bỏ hoàn toàn quét regex HTML) ────
    fun syncSessionTokens(): Pair<Boolean, Map<String, String>> {
        val info = IgTaToolClient.checkCookieIg(client, account.cookie)
        if (!info.isLive) {
            account.lastErrorMessage = "Tài khoản bị Kháng nghị / Checkpoint / DIE"
            return Pair(false, emptyMap())
        }

        val realUsername = info.username.ifBlank { account.username }
        val realUserId = info.userId.ifBlank { account.userId }
        account.username = realUsername
        account.userId = realUserId

        val tokenMap = mutableMapOf<String, String>()
        tokenMap["username"] = realUsername
        tokenMap["userId"] = realUserId

        try {
            val cookieTokens = extractTokensFromCookie(account.cookie)
            val csrftoken = cookieTokens["csrftoken"] ?: ""
            if (csrftoken.isNotBlank()) tokenMap["csrftoken"] = csrftoken
        } catch (_: Exception) {}

        return Pair(true, tokenMap)
    }

    // ── 2. ĐẢM BẢO NICK LIÊN KẾT XSMM THEO /api/taskapi/accounts2 ─────
    fun ensureAccountLinked(): Boolean {
        val cleanUser = account.username.trim().removePrefix("@").trim('/')
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
        return completeTasks(type, listOf(taskId))
    }

    fun completeTasks(type: String, taskIds: List<String>): JSONObject? {
        if (taskIds.isEmpty()) return null
        val uidParam = account.userId.takeIf { it.isNotBlank() && it != "0" }
            ?: extractTokensFromCookie(account.cookie)["ds_user_id"]
            ?: account.username
        val body = JSONObject().apply {
            put("type", type)
            put("task_id", JSONArray().apply { taskIds.forEach { put(it) } })
            put("uid", uidParam)
            put("cookie_check", account.cookie)
            put("string_ig", account.cookie)
            put("string", account.cookie)
            put("cookie", account.cookie)
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
            val root = JSONObject(res)
            val user = root.optJSONObject("user") ?: root.optJSONObject("data")?.optJSONObject("user")
            user?.optLong("points") ?: root.optLong("points", 0L)
        } catch (_: Exception) { 0L }
    }

    // ── 5. THỰC THI NHIỆM VỤ INSTAGRAM BẰNG IgTaToolClient (GRAPHQL CHUẨN PYTHON TA TOOL) ──
    data class IgActionResult(
        val isSuccess: Boolean,
        val httpCode: Int,
        val rawBody: String,
        val errorMessage: String? = null
    )
    typealias FollowResult = IgActionResult

    private fun parseIgResult(rawBody: String, defaultActionName: String): IgActionResult {
        if (rawBody.isBlank()) {
            return IgActionResult(false, 0, "", "Phản hồi rỗng từ Instagram")
        }
        try {
            val root = JSONObject(rawBody)
            // 1. Kiểm tra thành công
            val data = root.optJSONObject("data")
            val friendship = data?.optJSONObject("xdt_create_friendship")
            val status = friendship?.optJSONObject("friendship_status")
            if (status?.optBoolean("following") == true || status?.optBoolean("outgoing_request") == true) {
                return IgActionResult(true, 200, rawBody, null)
            }
            val likeMedia = data?.optJSONObject("xdt_like_media")
            if (likeMedia != null && (likeMedia.optString("status").equals("ok", true) || likeMedia.has("client_mutation_id"))) {
                return IgActionResult(true, 200, rawBody, null)
            }
            val commentData = data?.optJSONObject("comment") ?: data?.optJSONObject("xdt_comment")
            if (commentData != null || root.has("comment") || (root.has("id") && root.has("text"))) {
                return IgActionResult(true, 200, rawBody, null)
            }
            if (root.optString("status").equals("ok", true)) {
                return IgActionResult(true, 200, rawBody, null)
            }

            // 2. Kiểm tra mảng lỗi errors
            val errorsArr = root.optJSONArray("errors")
            if (errorsArr != null && errorsArr.length() > 0) {
                val firstErr = errorsArr.getJSONObject(0)
                val msg = firstErr.optString("message").takeIf { it.isNotBlank() }
                if (msg != null && msg.contains("already", ignoreCase = true)) {
                    return IgActionResult(true, 200, rawBody, null)
                }
                val summary = firstErr.optString("summary").takeIf { it.isNotBlank() }
                val desc = firstErr.optString("description").takeIf { it.isNotBlank() }
                val errorText = listOfNotNull(summary, desc, msg).distinct().joinToString(": ")
                return IgActionResult(false, 400, rawBody, errorText.ifBlank { "Lỗi GraphQL Instagram" })
            }

            // 3. Kiểm tra các mã lỗi nghiệp vụ
            val msg = root.optString("message").takeIf { it.isNotBlank() }
            val statusStr = root.optString("status").takeIf { it.isNotBlank() }
            val spam = root.optBoolean("spam", false)
            val feedbackTitle = root.optString("feedback_title").takeIf { it.isNotBlank() }
            val feedbackMessage = root.optString("feedback_message").takeIf { it.isNotBlank() }

            if (msg != null || statusStr.equals("fail", ignoreCase = true) || spam) {
                val friendlyMsg = when {
                    msg.equals("feedback_required", ignoreCase = true) || spam -> {
                        feedbackMessage ?: feedbackTitle ?: "Chặn tính năng (feedback_required / spam)"
                    }
                    msg.equals("checkpoint_required", ignoreCase = true) -> "Dính Checkpoint xác minh tài khoản"
                    msg.equals("login_required", ignoreCase = true) -> "Hết phiên đăng nhập (Cookie DIE)"
                    msg.equals("rate_limit_exceeded", ignoreCase = true) -> "Quá giới hạn thao tác Instagram (Rate limit)"
                    !feedbackMessage.isNullOrBlank() -> feedbackMessage
                    !feedbackTitle.isNullOrBlank() -> feedbackTitle
                    !msg.isNullOrBlank() -> msg
                    else -> "Thao tác thất bại ($defaultActionName)"
                }
                return IgActionResult(false, if (spam) 429 else 400, rawBody, friendlyMsg)
            }
        } catch (_: Exception) {}

        // Fallback kiểm tra chuỗi
        if (rawBody.contains("\"following\":true") || rawBody.contains("\"viewer_has_liked\":true") || rawBody.contains("\"status\":\"ok\"") || rawBody.contains("\"status\": \"ok\"")) {
            return IgActionResult(true, 200, rawBody, null)
        }
        if (rawBody.contains("feedback_required", ignoreCase = true)) {
            return IgActionResult(false, 429, rawBody, "Chặn tính năng (feedback_required)")
        }
        if (rawBody.contains("checkpoint", ignoreCase = true)) {
            return IgActionResult(false, 403, rawBody, "Dính Checkpoint Instagram")
        }
        if (rawBody.contains("login_required", ignoreCase = true)) {
            return IgActionResult(false, 401, rawBody, "Cookie DIE / Yêu cầu đăng nhập")
        }

        return IgActionResult(false, 400, rawBody, "Instagram từ chối ($defaultActionName)")
    }

    fun doFollow(targetNumericId: String, targetUsername: String, targetUrl: String = ""): IgActionResult {
        var uid = targetNumericId.trim()
        if (!uid.all { it.isDigit() } || uid.isBlank()) {
            val extracted = if (targetUrl.isNotBlank()) IgTaToolClient.extractTargetIdFromUrl(client, account.cookie, targetUrl) else null
            uid = extracted ?: resolveTargetUid(targetUsername) ?: targetNumericId
        }
        val cleanTargetUser = targetUsername.trim().trim('/').substringAfterLast('/')
        val profileUrl = if (targetUrl.isNotBlank()) targetUrl else if (cleanTargetUser.isNotBlank()) "https://www.instagram.com/$cleanTargetUser/" else "https://www.instagram.com/"
        val resBody = IgTaToolClient.follow(client, account.cookie, uid, profileUrl)
        return parseIgResult(resBody, "Follow")
    }

    private fun resolveTargetUid(username: String): String? {
        val clean = username.trim().removePrefix("@").trim('/')
        if (clean.isBlank()) return null
        val req = Request.Builder()
            .url("https://www.instagram.com/web/search/topsearch/?context=blended&query=$clean")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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

    fun doLike(link: String, mediaId: String): IgActionResult {
        try {
            var mid = mediaId.trim()
            if (!mid.all { it.isDigit() } || mid.isBlank()) {
                val sc = extractShortcode(link) ?: extractShortcode(mediaId)
                if (!sc.isNullOrBlank()) mid = shortcodeToMediaId(sc) ?: mid
            }
            if (mid.isBlank() || !mid.all { it.isDigit() }) {
                val targetLink = link.ifBlank { if (mediaId.startsWith("http")) mediaId else "https://www.instagram.com/p/$mediaId/" }
                val extractedId = extractMediaIdFromPage(targetLink)
                if (!extractedId.isNullOrBlank()) mid = extractedId
            }
            if (mid.isBlank()) return IgActionResult(false, 400, "", "Không tìm thấy Media ID để Like")
            val res = IgTaToolClient.tym(client, account.cookie, mid, link)
            return parseIgResult(res, "Tym")
        } catch (e: Exception) {
            return IgActionResult(false, 500, "", e.message ?: "Lỗi kết nối khi Tym")
        }
    }

    fun doComment(link: String, mediaId: String, text: String): IgActionResult {
        try {
            var mid = mediaId.trim()
            if (!mid.all { it.isDigit() } || mid.isBlank()) {
                val sc = extractShortcode(link) ?: extractShortcode(mediaId)
                if (!sc.isNullOrBlank()) mid = shortcodeToMediaId(sc) ?: mid
            }
            if (mid.isBlank() || !mid.all { it.isDigit() }) {
                val targetLink = link.ifBlank { if (mediaId.startsWith("http")) mediaId else "https://www.instagram.com/p/$mediaId/" }
                val extractedId = extractMediaIdFromPage(targetLink)
                if (!extractedId.isNullOrBlank()) mid = extractedId
            }
            if (mid.isBlank()) return IgActionResult(false, 400, "", "Không tìm thấy Media ID để Comment")
            val res = IgTaToolClient.cmt(client, account.cookie, mid, text, link)
            return parseIgResult(res, "Comment")
        } catch (e: Exception) {
            return IgActionResult(false, 500, "", e.message ?: "Lỗi kết nối khi Comment")
        }
    }

    private fun extractMediaIdFromPage(url: String): String? {
        if (url.isBlank()) return null
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Cookie", account.cookie)
            .get().build()
        val html = execute(req) ?: return null
        val m = Pattern.compile("\"media_id\":\"(\\d+)\"").matcher(html)
        if (m.find()) return m.group(1)
        val m2 = Pattern.compile("\"pk\":\"(\\d+)\"").matcher(html)
        if (m2.find()) return m2.group(1)
        return null
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
