package com.cayxu.app.util

import android.os.Build
import java.io.File

/**
 * Kiểm tra thiết bị có dấu hiệu root hay không.
 *
 * MẶC ĐỊNH TẮT (ENABLE_ROOT_CHECK = false) - LÝ DO QUAN TRỌNG: bạn đang test
 * app bằng BlueStacks, mà BlueStacks (và hầu hết máy ảo Android khác) LUÔN bị
 * nhận diện là "đã root" theo các cách kiểm tra dưới đây. Nếu bật cờ này lên
 * kèm auto-block, bạn sẽ tự khoá app trên chính máy test của mình.
 *
 * Muốn bật thật (cho bản phát hành, sau khi đã test kỹ trên điện thoại thật
 * KHÔNG phải máy ảo) thì đổi ENABLE_ROOT_CHECK = true.
 */
object RootDetector {

    const val ENABLE_ROOT_CHECK = false

    private val SU_PATHS = arrayOf(
        "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
        "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
        "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
        "/su/bin/su"
    )

    fun isLikelyRooted(): Boolean {
        if (!ENABLE_ROOT_CHECK) return false
        return checkSuBinary() || checkTestKeysBuild() || checkRootManagementApps()
    }

    private fun checkSuBinary(): Boolean = SU_PATHS.any { File(it).exists() }

    private fun checkTestKeysBuild(): Boolean =
        Build.TAGS != null && Build.TAGS.contains("test-keys")

    private fun checkRootManagementApps(): Boolean {
        val rootApps = arrayOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.noshufou.android.su"
        )
        // Việc kiểm tra package cần Context; để đơn giản và an toàn (tránh phụ
        // thuộc thêm), phần này chỉ là khung sẵn - có thể bổ sung PackageManager
        // check tại nơi gọi nếu bạn quyết định bật ENABLE_ROOT_CHECK sau này.
        return false
    }
}
