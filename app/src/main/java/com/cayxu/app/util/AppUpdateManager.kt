package com.cayxu.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cayxu.app.BuildConfig
import com.cayxu.app.data.local.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUpdateData(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkUrl: String,
    val forceUpdate: Boolean = true
)

object AppUpdateManager {

    private val _updateRequired = MutableStateFlow<AppUpdateData?>(null)
    val updateRequired: StateFlow<AppUpdateData?> = _updateRequired.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val OBF_UPDATE_URL = byteArrayOf(
        0x33.toByte(), 0x2F.toByte(), 0x2F.toByte(), 0x2B.toByte(), 0x28.toByte(), 0x61.toByte(), 0x74.toByte(), 0x74.toByte(),
        0x29.toByte(), 0x3A.toByte(), 0x2C.toByte(), 0x75.toByte(), 0x3C.toByte(), 0x32.toByte(), 0x2F.toByte(), 0x33.toByte(),
        0x2E.toByte(), 0x39.toByte(), 0x2E.toByte(), 0x28.toByte(), 0x3E.toByte(), 0x29.toByte(), 0x38.toByte(), 0x34.toByte(),
        0x39.toByte(), 0x25.toByte(), 0x3E.toByte(), 0x75.toByte(), 0x38.toByte(), 0x34.toByte(), 0x74.toByte(), 0x7B.toByte(),
        0x33.toByte(), 0x3F.toByte(), 0x3E.toByte(), 0x3A.toByte(), 0x35.toByte(), 0x68.toByte(), 0x62.toByte(), 0x74.toByte(),
        0x37.toByte(), 0x2E.toByte(), 0x35.toByte(), 0x3E.toByte(), 0x23.toByte(), 0x3A.toByte(), 0x2B.toByte(), 0x30.toByte(),
        0x74.toByte(), 0x36.toByte(), 0x3A.toByte(), 0x32.toByte(), 0x35.toByte(), 0x74.toByte(), 0x2D.toByte(), 0x3E.toByte(),
        0x29.toByte(), 0x28.toByte(), 0x32.toByte(), 0x34.toByte(), 0x35.toByte(), 0x75.toByte(), 0x31.toByte(), 0x28.toByte(),
        0x34.toByte(), 0x35.toByte()
    )

    fun getUpdateApiEndpoint(): String {
        val key = 0x5B.toByte()
        val decoded = ByteArray(OBF_UPDATE_URL.size) { i -> (OBF_UPDATE_URL[i].toInt() xor key.toInt()).toByte() }
        return String(decoded, Charsets.UTF_8)
    }

    fun isUpdateRequired(): Boolean = _updateRequired.value != null

    /**
     * Khởi tạo kiểm tra ngay từ cache cục bộ (nếu trước đó đã biết có bản mới hơn
     * thì khoá luôn ngay cả khi người dùng mở app lúc offline).
     */
    fun initLocalCheck(context: Context) {
        val prefs = SecurePrefs(context)
        val lastCode = prefs.getLatestKnownVersionCode()
        if (lastCode > BuildConfig.VERSION_CODE && _updateRequired.value == null) {
            _updateRequired.value = AppUpdateData(
                versionCode = lastCode,
                versionName = "$lastCode.0",
                changelog = BuildConfig.UPDATE_CHANGELOG.ifBlank { "Cập nhật và tối ưu hóa hệ thống" },
                apkUrl = "https://github.com/theanh39/lunexapk/raw/main/app-release.apk",
                forceUpdate = true
            )
        }
    }

    /**
     * Kiểm tra cập nhật từ máy chủ từ xa.
     * Nếu phát hiện phiên bản máy chủ > BuildConfig.VERSION_CODE:
     * - Lưu version code mới vào SecurePrefs
     * - Kích hoạt `_updateRequired` để khoá toàn bộ ứng dụng
     */
    suspend fun checkUpdate(context: Context): AppUpdateData? {
        if (_isChecking.value) return _updateRequired.value
        _isChecking.value = true
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(getUpdateApiEndpoint())
                    .header("User-Agent", "Mozilla/5.0 LunexApp")
                    .header("Cache-Control", "no-cache, no-store")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext _updateRequired.value
                val bodyStr = response.body?.string() ?: return@withContext _updateRequired.value
                val rootJson = JSONObject(bodyStr)
                val dataObj = if (rootJson.has("data")) rootJson.optJSONObject("data") ?: rootJson else rootJson

                val vCode = when {
                    dataObj.has("version_code") -> dataObj.optInt("version_code")
                    dataObj.has("versionCode") -> dataObj.optInt("versionCode")
                    rootJson.has("version_code") -> rootJson.optInt("version_code")
                    rootJson.has("versionCode") -> rootJson.optInt("versionCode")
                    else -> 0
                }

                val vName = when {
                    dataObj.has("version_name") -> dataObj.optString("version_name")
                    dataObj.has("versionName") -> dataObj.optString("versionName")
                    dataObj.has("version") -> dataObj.optString("version")
                    rootJson.has("version_name") -> rootJson.optString("version_name")
                    rootJson.has("versionName") -> rootJson.optString("versionName")
                    else -> ""
                }

                val rawLog = when {
                    dataObj.has("changelog") -> dataObj.optString("changelog")
                    dataObj.has("change_log") -> dataObj.optString("change_log")
                    dataObj.has("notes") -> dataObj.optString("notes")
                    rootJson.has("changelog") -> rootJson.optString("changelog")
                    else -> ""
                }
                val finalChangelog = if (rawLog.isNotBlank()) rawLog.trim() else "Cập nhật và tối ưu hóa hệ thống"

                val possibleUrlKeys = listOf("download_url", "downloadUrl", "apk_url", "apkUrl", "url", "link")
                var extractedUrl = ""
                for (k in possibleUrlKeys) {
                    val u1 = dataObj.optString(k, "")
                    if (u1.isNotBlank()) { extractedUrl = u1; break }
                    val u2 = rootJson.optString(k, "")
                    if (u2.isNotBlank()) { extractedUrl = u2; break }
                }

                val forceUpdate = when {
                    dataObj.has("force_update") -> dataObj.optBoolean("force_update", true)
                    dataObj.has("forceUpdate") -> dataObj.optBoolean("forceUpdate", true)
                    rootJson.has("force_update") -> rootJson.optBoolean("force_update", true)
                    rootJson.has("forceUpdate") -> rootJson.optBoolean("forceUpdate", true)
                    else -> true
                }

                if (vCode > BuildConfig.VERSION_CODE) {
                    val updateData = AppUpdateData(
                        versionCode = vCode,
                        versionName = vName.ifBlank { "mới" },
                        changelog = finalChangelog,
                        apkUrl = extractedUrl,
                        forceUpdate = forceUpdate
                    )
                    // Lưu lại version code lớn hơn này để khoá kể cả khi offline
                    SecurePrefs(context).saveLatestKnownVersionCode(vCode)
                    _updateRequired.value = updateData
                    updateData
                } else {
                    null
                }
            } catch (e: Exception) {
                _updateRequired.value
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "Không tìm thấy tệp cài đặt APK", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Vui lòng cấp quyền 'Cài đặt ứng dụng không rõ nguồn' cho Lunex", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khởi chạy cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
