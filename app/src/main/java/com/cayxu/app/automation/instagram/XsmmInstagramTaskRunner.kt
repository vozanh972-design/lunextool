package com.cayxu.app.automation.instagram

import android.content.Context
import com.cayxu.app.data.local.InstagramAccount
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.instagram.InstagramApiClient
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
    val userId: String,
    val username: String,
    val cookie: String,
    val proxy: String? = null
)

/**
 * Runner chạy 100% API chuẩn XSMM.NET (https://xsmm.net/api/taskapi/) cho Instagram:
 * - Bỏ hẳn mọi thứ liên quan đến gateway GoLike / GoMax server.
 * - Tự động đồng bộ tài khoản qua POST /api/taskapi/accounts2.
 * - Lấy nhiệm vụ trực tiếp qua GET /api/taskapi/tasks2?type={type}&uid={ds_user_id}.
 * - Tương tác thẳng với Instagram qua HTTP client nội bộ có proxy riêng.
 * - Báo cáo nhận xu trực tiếp qua POST /api/taskapi/tasks2/complete.
 * - Cập nhật số dư xu qua GET /api/taskapi/user.
 */
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

        private fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").mapNotNull {
                val idx = it.indexOf("=")
                if (idx > 0) it.substring(0, idx).trim() to it.substring(idx + 1).trim() else null
            }.toMap()
        }

        fun extractUserId(cookie: String): String {
            return parseCookieMap(cookie)["ds_user_id"] ?: ""
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
                val msg = "Chưa đăng nhập XSMM"
                onStatusUpdate?.invoke(msg)
                onProgressUpdate?.invoke(msg, 0, 1)
                return RunResult(0, 1, 0, msg)
            }

            val account = InstagramAccountsStore.getAccount(context, cleanUsername)
            if (account == null || account.cookie.isBlank()) {
                val msg = "[$cleanUsername] Không tìm thấy cookie tài khoản"
                onStatusUpdate?.invoke(msg)
                onProgressUpdate?.invoke(msg, 0, 1)
                return RunResult(0, 1, 0, msg)
            }

            val config = XsmmRunConfigStore.get(context, "instagram")
            var totalCompleted = 0
            var totalErrors = 0
            var totalEarnedPoints = 0

            fun notify(status: String) {
                onStatusUpdate?.invoke(status)
                onProgressUpdate?.invoke(status, totalCompleted, totalErrors)
                XsmmJobStatusBridge.update("[$cleanUsername] $status")
            }

            fun reportError(user: String, detail: String) {
                onErrorDetail?.invoke(user, detail)
            }

            // 1. Kiểm tra cookie và trích xuất UID
            val userId = account.userId.ifBlank { extractUserId(account.cookie) }
            val igAccount = IgXsmmAccount(
                userId = userId,
                username = account.username.ifBlank { cleanUsername },
                cookie = account.cookie,
                proxy = account.proxy.takeIf { it.isNotBlank() }
            )

            val runner = XsmmInstagramTaskRunner(token, igAccount)

            notify("Kiểm tra acc XSMM...")
            val added = runner.ensureAccountAdded()
            if (!added) {
                notify("Thêm nick lên XSMM: Đang thử lại...")
                delay(2000L)
            }

            // 2. Cấu hình loại nhiệm vụ
            val listnv = mutableListOf<String>()
            val effectiveTypes = config.effectiveTaskTypes()
            for (t in effectiveTypes) {
                when (t.lowercase()) {
                    "instagram_like" -> listnv.add("instagram_like")
                    "instagram_follow" -> listnv.add("instagram_follow")
                    "instagram_comment" -> listnv.add("instagram_comment")
                    "instagram_likecmt" -> listnv.add("instagram_likecmt")
                }
            }
            if (listnv.isEmpty()) {
                listnv.add("instagram_like")
                listnv.add("instagram_follow")
                listnv.add("instagram_comment")
            }

            val doDuration = config.doTaskDurationSeconds.coerceAtLeast(10)
            val fetchInterval = config.fetchTaskIntervalSeconds.coerceAtLeast(10)
            val doi = if (config.taskCountTarget > 0) config.taskCountTarget else 99999
            val maxStopAfterNoTask = config.stopAfterNoTaskCount.coerceAtLeast(10)
            val maxErrors = config.failJobCountToSwitchAccount.coerceAtLeast(5)

            var maxJob = 0
            var consecutiveNoTasks = 0
            var consecutiveErrors = 0

            suspend fun updatePointsUi(pts: Long) {
                if (pts > 0) {
                    synchronized(XsmmAccountStore) {
                        XsmmAccountStore.updatePoints(context, pts)
                    }
                    withContext(Dispatchers.Main) {
                        XsmmSession.points.value = pts
                    }
                }
            }

            // 3. Vòng lặp nhận và làm nhiệm vụ 100% XSMM.NET
            while (coroutineContext.isActive) {
                if (maxJob >= doi) {
                    notify("Đủ $maxJob job -> Đổi nick")
                    break
                }

                val randType = listnv.random()
                val readableName = when (randType) {
                    "instagram_like" -> "Tym"
                    "instagram_follow" -> "Follow"
                    "instagram_comment" -> "Comment"
                    "instagram_likecmt" -> "Like Comment"
                    else -> randType
                }

                notify("Nhận việc: $readableName")
                val tasks = runner.getTasks(randType)

                if (tasks.isEmpty()) {
                    consecutiveNoTasks++
                    notify("Hết job $readableName (${consecutiveNoTasks}/$maxStopAfterNoTask)")
                    if (consecutiveNoTasks >= maxStopAfterNoTask) {
                        notify("Hết job liên tục -> Dừng")
                        break
                    }
                    for (x in fetchInterval downTo 1) {
                        if (!coroutineContext.isActive) break
                        notify("Chờ job (${x}s)...")
                        delay(1000L)
                    }
                    continue
                }

                consecutiveNoTasks = 0

                for (task in tasks) {
                    if (!coroutineContext.isActive) break
                    val taskId = task.optString("id")
                    val targetId = task.optString("target_id")
                    val targetUrl = task.optString("target_url")
                    val idOrLink = task.optString("idorlink")
                    val commentText = task.optString("comment").ifBlank { "Tuyệt vời!" }

                    notify("Làm $readableName: ${targetId.ifBlank { idOrLink }}")

                    val success = when (randType) {
                        "instagram_follow" -> {
                            val userToFollow = targetUrl.trim().trimEnd('/').substringAfterLast("/").ifBlank { idOrLink }
                            runner.doFollow(targetId.ifBlank { idOrLink }, userToFollow)
                        }
                        "instagram_like" -> {
                            runner.doLike(targetUrl.ifBlank { idOrLink }, targetId.ifBlank { idOrLink })
                        }
                        "instagram_comment" -> {
                            runner.doComment(targetUrl.ifBlank { idOrLink }, targetId.ifBlank { idOrLink }, commentText)
                        }
                        "instagram_likecmt" -> {
                            runner.doLike(targetUrl.ifBlank { idOrLink }, targetId.ifBlank { idOrLink })
                        }
                        else -> false
                    }

                    maxJob++

                    if (success) {
                        notify("$readableName xong -> Nhận xu...")
                        val compRes = runner.completeTask(randType, taskId)
                        val points = compRes?.optInt("points") ?: compRes?.optJSONObject("data")?.optInt("points") ?: 35
                        val totalPts = compRes?.optLong("total_points") ?: compRes?.optJSONObject("data")?.optLong("total_points") ?: 0L

                        totalCompleted++
                        consecutiveErrors = 0
                        totalEarnedPoints += points

                        if (totalPts > 0) {
                            updatePointsUi(totalPts)
                        } else {
                            val cur = XsmmAccountStore.getPoints(context) + points
                            updatePointsUi(cur)
                        }

                        notify("$readableName: +$points xu (Xong: $totalCompleted)")

                        for (x in doDuration downTo 1) {
                            if (!coroutineContext.isActive) break
                            notify("Nghỉ ${x}s...")
                            delay(1000L)
                        }
                    } else {
                        totalErrors++
                        consecutiveErrors++
                        notify("$readableName lỗi: ${targetId.ifBlank { idOrLink }}")
                        reportError(cleanUsername, "$readableName lỗi: ${targetId.ifBlank { idOrLink }}")

                        if (consecutiveErrors >= maxErrors) {
                            notify("Lỗi liên tiếp $consecutiveErrors lần -> Đổi nick")
                            break
                        }
                        delay(3000L)
                    }

                    if (maxJob >= doi) break
                }
            }

            // Đồng bộ lại số dư xu mới nhất từ server XSMM
            val latestPoints = runner.fetchUserPoints()
            if (latestPoints != null && latestPoints > 0) {
                updatePointsUi(latestPoints)
            }

            val finalMsg = "Hoàn tất: $totalCompleted thành công, $totalErrors lỗi (+${totalEarnedPoints} xu)"
            notify(finalMsg)
            return RunResult(totalCompleted, totalErrors, totalEarnedPoints, finalMsg)
        }
    }

    private fun buildClient(proxyStr: String?): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (!proxyStr.isNullOrBlank()) {
            var s = proxyStr.trim()
            if (s.contains("://")) {
                s = s.substringAfter("://")
            }
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

    // ── 1. ĐẢM BẢO TÀI KHOẢN ĐÃ CÓ TRÊN XSMM.NET ─────────────────────
    fun ensureAccountAdded(): Boolean {
        // Kiểm tra tài khoản đã tồn tại chưa
        try {
            val checkReq = Request.Builder()
                .url("${XSMM_API}accounts2?search=${account.userId}&account_type=instagram")
                .header("Authorization", "Bearer $xsmmToken")
                .get().build()
            val checkRes = execute(checkReq)
            if (checkRes != null && (checkRes.contains(account.userId) || (account.username.isNotBlank() && checkRes.contains(account.username)))) {
                return true
            }
        } catch (_: Exception) {}

        // Nếu chưa, thêm vào XSMM
        val body = JSONObject().apply {
            put("type", "instagram")
            put("link_account", "https://www.instagram.com/${account.username}")
        }.toString().toRequestBody(JSON_TYPE)
        val addReq = Request.Builder()
            .url("${XSMM_API}accounts2")
            .header("Authorization", "Bearer $xsmmToken")
            .post(body).build()
        val addRes = execute(addReq)
        return addRes != null && (!addRes.contains("\"error\":") || addRes.contains("đã tồn tại"))
    }

    // ── 2. LẤY NHIỆM VỤ TỪ XSMM.NET ─────────────────────────────────
    fun getTasks(type: String): List<JSONObject> {
        val url = "${XSMM_API}tasks2?type=$type&uid=${account.userId}&typejob=normal,better"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $xsmmToken")
            .get().build()
        val res = execute(req) ?: return emptyList()
        val list = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(res)
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}
        return list
    }

    // ── 3. THỰC HIỆN TƯƠNG TÁC INSTAGRAM TRỰC TIẾP ──────────────────
    fun doFollow(targetUid: String, targetUsername: String): Boolean {
        var uid = targetUid.trim()
        if ((!uid.all { it.isDigit() } || uid.isBlank()) && targetUsername.isNotBlank()) {
            val resolved = InstagramApiClient.resolveTargetUserId(targetUsername, account.proxy)
            if (!resolved.isNullOrBlank()) {
                uid = resolved
            }
        }
        val vars = JSONObject().apply {
            put("target_user_id", uid)
            put("container_module", "profile")
            put("nav_chain", "PolarisProfilePostsTabRoot:profilePage:1:via_cold_start,PolarisProfilePostsTabRoot:profilePage:3:unexpected")
        }
        val referer = if (targetUsername.isNotBlank()) {
            if (targetUsername.startsWith("http")) targetUsername else "https://www.instagram.com/$targetUsername/"
        } else "https://www.instagram.com/"

        val headers = igHeaders(account.cookie, referer).newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisFollowMutation")
            .add("X-Root-Field-Name", "xdt_create_friendship")
            .build()
        val body = igFormBody("9740159112729312", "usePolarisFollowMutation", vars.toString())
        val req = Request.Builder().url("https://www.instagram.com/graphql/query").headers(headers).post(body).build()
        val res = execute(req) ?: return false
        return res.contains("\"following\":true") || res.contains("\"status\":\"ok\"") || res.contains("already")
    }

    fun doLike(targetUrl: String, targetId: String): Boolean {
        val shortcode = extractShortcode(targetUrl)
        var mediaId = shortcode?.let { shortcodeToMediaId(it) } ?: targetId.trim()
        if (!mediaId.all { it.isDigit() } || mediaId.isBlank()) {
            val resolved = InstagramApiClient.resolveMediaId(targetUrl.ifBlank { targetId }, account.proxy)
            if (!resolved.isNullOrBlank()) {
                mediaId = resolved
            }
        }
        if (mediaId.isBlank()) return false

        val vars = JSONObject().apply {
            put("media_id", mediaId)
            put("container_module", "feed_timeline")
        }
        val headers = igHeaders(account.cookie, "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisLikeMediaLikeMutation")
            .build()
        val body = igFormBody("9595477160535898", "usePolarisLikeMediaLikeMutation", vars.toString())
        val req = Request.Builder().url("https://www.instagram.com/graphql/query").headers(headers).post(body).build()
        val res = execute(req)
        if (res != null && (res.contains("\"status\":\"ok\"") || res.contains("\"viewer_has_liked\":true") || res.contains("\"success\":true"))) {
            return true
        }

        // Fallback REST
        val restReq = Request.Builder()
            .url("https://www.instagram.com/api/v1/web/likes/$mediaId/like/")
            .header("X-CSRFToken", getCsrf(account.cookie))
            .header("X-Instagram-AJAX", "1006309104")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-IG-App-ID", "936619743392459")
            .header("Cookie", account.cookie)
            .post(FormBody.Builder().build())
            .build()
        val restRes = execute(restReq)
        return restRes != null && (restRes.contains("\"status\":\"ok\"") || restRes.contains("\"success\":true"))
    }

    fun doComment(targetUrl: String, targetId: String, text: String): Boolean {
        val shortcode = extractShortcode(targetUrl)
        var mediaId = shortcode?.let { shortcodeToMediaId(it) } ?: targetId.trim()
        if (!mediaId.all { it.isDigit() } || mediaId.isBlank()) {
            val resolved = InstagramApiClient.resolveMediaId(targetUrl.ifBlank { targetId }, account.proxy)
            if (!resolved.isNullOrBlank()) {
                mediaId = resolved
            }
        }
        if (mediaId.isBlank()) return false

        val vars = JSONObject().apply {
            put("id", mediaId)
            put("comment_text", text)
            put("container_module", "self_comments_v2")
        }
        val headers = igHeaders(account.cookie, "https://www.instagram.com/").newBuilder()
            .add("X-FB-Friendly-Name", "usePolarisCommentDirectMutation")
            .build()
        val body = igFormBody("7755358241198424", "usePolarisCommentDirectMutation", vars.toString())
        val req = Request.Builder().url("https://www.instagram.com/graphql/query").headers(headers).post(body).build()
        val res = execute(req)
        return res != null && !res.contains("\"errors\":") && res.contains("\"id\":")
    }

    // ── 4. HOÀN THÀNH NHIỆM VỤ TRÊN XSMM.NET ─────────────────────────
    fun completeTask(type: String, taskId: String): JSONObject? {
        val body = JSONObject().apply {
            put("type", type)
            put("task_id", JSONArray().apply { put(taskId) })
            put("uid", account.userId)
        }.toString().toRequestBody(JSON_TYPE)
        val req = Request.Builder()
            .url("${XSMM_API}tasks2/complete")
            .header("Authorization", "Bearer $xsmmToken")
            .post(body).build()
        val res = execute(req) ?: return null
        return try { JSONObject(res) } catch (_: Exception) { null }
    }

    fun completeTasks(type: String, taskIds: List<String>): JSONObject? {
        val body = JSONObject().apply {
            put("type", type)
            val arr = JSONArray()
            taskIds.forEach { arr.put(it) }
            put("task_id", arr)
            put("uid", account.userId)
        }.toString().toRequestBody(JSON_TYPE)
        val req = Request.Builder()
            .url("${XSMM_API}tasks2/complete")
            .header("Authorization", "Bearer $xsmmToken")
            .post(body).build()
        val res = execute(req) ?: return null
        return try { JSONObject(res) } catch (_: Exception) { null }
    }

    // ── 5. CẬP NHẬT SỐ DƯ XU ─────────────────────────────────────────
    fun fetchUserPoints(): Long? {
        val req = Request.Builder()
            .url("${XSMM_API}user")
            .header("Authorization", "Bearer $xsmmToken")
            .get().build()
        val res = execute(req) ?: return null
        return try {
            val obj = JSONObject(res)
            val user = obj.optJSONObject("user") ?: obj.optJSONObject("data")
            user?.optLong("points") ?: obj.optLong("points")
        } catch (_: Exception) { null }
    }

    // ── HELPERS ──────────────────────────────────────────────────────
    private fun igHeaders(cookie: String, referer: String) = Headers.Builder()
        .add("Accept", "*/*")
        .add("Accept-Language", "vi,en;q=0.9")
        .add("Content-Type", "application/x-www-form-urlencoded")
        .add("Origin", "https://www.instagram.com")
        .add("Referer", referer)
        .add("Sec-Ch-Ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"")
        .add("Sec-Ch-Ua-Mobile", "?0")
        .add("Sec-Ch-Ua-Platform", "\"Windows\"")
        .add("Sec-Fetch-Dest", "empty")
        .add("Sec-Fetch-Mode", "cors")
        .add("Sec-Fetch-Site", "same-origin")
        .add("X-ASBD-ID", "359341")
        .add("X-Bloks-Version-Id", "61fc9465e13b77eaa110f317859102ba7fb93a0a2bcc08c46473da6713640739")
        .add("X-CSRFToken", getCsrf(cookie))
        .add("X-IG-App-ID", "936619743392459")
        .add("Cookie", cookie)
        .build()

    private fun igFormBody(docId: String, name: String, vars: String) = FormBody.Builder()
        .add("av", "178414")
        .add("__d", "www")
        .add("__user", "0")
        .add("__a", "1")
        .add("__req", "1j")
        .add("__hs", "20519.HYP:instagram_web_pkg.2.1...0")
        .add("dpr", "1")
        .add("__ccg", "EXCELLENT")
        .add("__comet_req", "7")
        .add("jazoest", "26738")
        .add("fb_api_caller_class", "RelayModern")
        .add("fb_api_req_friendly_name", name)
        .add("variables", vars)
        .add("server_timestamps", "true")
        .add("doc_id", docId)
        .build()

    private fun execute(req: Request): String? = try {
        client.newCall(req).execute().use { it.body?.string() }
    } catch (_: Exception) { null }

    private fun getCsrf(cookie: String): String {
        return cookie.split(";").mapNotNull {
            val idx = it.indexOf("=")
            if (idx > 0 && it.substring(0, idx).trim() == "csrftoken") it.substring(idx + 1).trim() else null
        }.firstOrNull() ?: ""
    }
}
