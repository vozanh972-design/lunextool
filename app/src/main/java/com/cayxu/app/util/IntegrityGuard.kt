package com.cayxu.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.security.MessageDigest

/**
 * Kiểm tra tính toàn vẹn của app: phát hiện APK đã bị patch rồi ký lại bằng
 * chữ ký khác (kiểu crack "bypass hoàn toàn" mà bạn mô tả - sửa code luôn trả
 * về nhánh true), hoặc đang bị gắn debugger/hook (Frida, Xposed...).
 *
 * QUAN TRỌNG: đây là lớp phòng thủ THÊM VÀO, không phải duy nhất và không phải
 * bất khả xâm phạm. Ai đủ kỹ năng vẫn có thể patch được chính đoạn kiểm tra
 * này. Lớp phòng thủ chắc chắn nhất là KeyRecheckWorker (server tự xác nhận
 * lại định kỳ) vì server là nơi kẻ tấn công không sửa trực tiếp được.
 */
object IntegrityGuard {

    // TODO (bắt buộc trước khi phát hành thật): build APK release đã ký xong,
    // lấy đúng SHA-256 chữ ký thật rồi điền vào đây. Cách lấy:
    //   keytool -printcert -jarfile app-release.apk
    // (hoặc dùng `apksigner verify --print-certs app-release.apk`)
    // rồi copy giá trị SHA-256 (bỏ dấu ":") vào EXPECTED_SIGNATURE_SHA256.
    // Để trống "" = TẠM THỜI bỏ qua kiểm tra chữ ký (dùng khi test bản debug,
    // vì bản debug ký bằng debug-key khác release-key nên sẽ luôn "invalid"
    // nếu bật kiểm tra này quá sớm).
    private const val EXPECTED_SIGNATURE_SHA256 = ""

    fun isTampered(context: Context): Boolean {
        return isSignatureInvalid(context) || isDebuggerAttached()
    }

    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    @Suppress("DEPRECATION")
    private fun isSignatureInvalid(context: Context): Boolean {
        if (EXPECTED_SIGNATURE_SHA256.isBlank()) return false
        return try {
            val actualHash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.let { sha256(it.toByteArray()) }
            } else {
                val info = context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNATURES
                )
                info.signatures?.firstOrNull()?.let { sha256(it.toByteArray()) }
            }
            actualHash != null && !actualHash.equals(EXPECTED_SIGNATURE_SHA256, ignoreCase = true)
        } catch (e: Exception) {
            // Không xác định được chữ ký (lỗi hệ thống) -> không tự ý khoá app vì
            // false positive sẽ khoá nhầm người dùng hợp lệ.
            false
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
