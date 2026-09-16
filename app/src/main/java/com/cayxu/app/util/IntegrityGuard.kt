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
    private const val EXPECTED_SIGNATURE_SHA256 = "ec7bed8d3bd42922674710fd99339c424ab1b8ab3b480ceb902abd49d5d19203"

    fun isTampered(context: Context): Boolean {
        return isSignatureInvalid(context) || isDebuggerAttached() || NativeSecurity.isDeviceCompromised(context)
    }

    /** Kiểm tra thiết bị có bị Root không */
    fun isRooted(context: Context): Boolean {
        return NativeSecurity.checkSecurityEnvironment(context) == 1
    }

    /** Kiểm tra thiết bị có phải Máy ảo (Emulator) không */
    fun isEmulator(context: Context): Boolean {
        return NativeSecurity.checkSecurityEnvironment(context) == 2
    }

    /**
     * Băm ràng buộc key với máy (device_id) và chữ ký APK trực tiếp bằng C++ Native:
     * Toàn bộ salt và thuật toán được giấu kín trong file .so
     */
    private fun computeKeyFingerprint(context: Context, key: String): String {
        val deviceId = com.cayxu.app.util.DeviceUtils.getAndroidId(context)
        val sigHash = currentSignatureSha256(context)
        return NativeSecurity.computeNativeKeyHash(key, deviceId, sigHash)
    }

    /** Gọi ngay sau khi lưu key thành công (login server trả về hợp lệ) để ràng buộc vào máy. */
    fun bindKeyToDevice(context: Context, key: String) {
        val fingerprint = computeKeyFingerprint(context, key)
        com.cayxu.app.data.local.SecurePrefs(context).saveKeyFingerprint(fingerprint)
    }

    /**
     * Chỉ xét khi ĐÃ CÓ key lưu sẵn trên máy (người dùng đã đăng nhập trước đó). Máy mới cài,
     * chưa từng đăng nhập -> trả về false (không phải bất thường, đó là luồng Login bình
     * thường), tránh chặn nhầm người dùng hợp lệ mới cài app lần đầu.
     */
    private fun isKeyFingerprintInvalid(context: Context): Boolean {
        val prefs = com.cayxu.app.data.local.SecurePrefs(context)
        val key = prefs.getKey()
        if (key.isNullOrBlank()) return false
        val storedFingerprint = prefs.getKeyFingerprint() ?: return true
        val expectedFingerprint = computeKeyFingerprint(context, key)
        return !expectedFingerprint.equals(storedFingerprint, ignoreCase = true)
    }

    /**
     * Kiểm tra CỨNG, chỉ cục bộ (không gọi verify_key.php hay request mạng nào): CHỈ xét
     * chữ ký APK + debugger/hook. Nếu sai thì ném exception ngay để app dừng/crash tại chỗ,
     * không hiện màn hình giải thích gì. Cố tình gọi ở NHIỀU điểm khác nhau trong app (xem
     * CayXuApp và HomeScreen) thay vì chỉ 1 chỗ duy nhất, để việc patch/xoá 1 điểm gọi không
     * đủ để vô hiệu hoá toàn bộ - đây là lớp gây khó/tốn thời gian, KHÔNG PHẢI chống crack
     * tuyệt đối.
     *
     * LƯU Ý QUAN TRỌNG: KHÔNG còn xét fingerprint key ở đây nữa. Trước đây fingerprint sai
     * (đổi máy/reset máy) cũng bị ném exception giống hệt tampered, khiến app crash ngay từ
     * CayXuApp.onCreate() - kể cả khi user đổi máy HOÀN TOÀN HỢP LỆ (không phải bẻ khoá gì
     * cả). Việc xác nhận "key có còn đúng với máy hiện tại không" giờ để server quyết định
     * qua verify_key.php (xem LoginViewModel.checkSavedKey và KeyRecheckWorker) - nếu server
     * không cho phép đổi máy, luồng đó tự đưa user quay lại màn Login một cách êm ái, không
     * crash cứng. Fingerprint cục bộ vẫn được lưu/so sánh (xem isKeyFingerprintInvalid) chỉ
     * để tham khảo, không dùng để chặn hay crash app nữa.
     */
    fun assertValidOrCrash(context: Context) {
        if (isTampered(context)) {
            throw IllegalStateException()
        }
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
                val signers = info.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
                    ?: info.signingInfo?.signingCertificateHistory
                signers?.firstOrNull()?.let { sha256(it.toByteArray()) }
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

    private fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    /**
     * Trả về SHA-256 chữ ký thật của APK đang chạy trên máy này - dùng để hiển
     * thị trong app (màn Tài khoản) cho bạn tự đối chiếu bằng mắt với giá trị
     * đã điền trong EXPECTED_SIGNATURE_SHA256, không cần dùng ADB hay công cụ gì.
     */
    @Suppress("DEPRECATION")
    fun currentSignatureSha256(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signers = info.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
                    ?: info.signingInfo?.signingCertificateHistory
                signers?.firstOrNull()?.let { sha256(it.toByteArray()) } ?: "?"
            } else {
                val info = context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNATURES
                )
                info.signatures?.firstOrNull()?.let { sha256(it.toByteArray()) } ?: "?"
            }
        } catch (e: Exception) {
            "?"
        }
    }
}
