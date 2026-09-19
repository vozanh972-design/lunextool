package com.cayxu.app.automation.tiktok

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.util.NativeSecurity

/**
 * Chỉ phục vụ luồng "thêm tài khoản TikTok bằng cách check trong app thật" - RIÊNG cho TikTok,
 * không đụng tới luồng nhập UID thủ công của các nền tảng khác.
 */
object TikTokAppLauncher {

    // Package/Activity của từng bản TikTok (lấy động từ NativeSecurity C++ OLLVM).
    private val CANDIDATES_STANDARD: List<String> get() = NativeSecurity.getTtCandidatesStandard()
    private val CANDIDATES_LITE: List<String> get() = NativeSecurity.getTtCandidatesLite()
    private val CANDIDATES_STUDIO: List<String> get() = NativeSecurity.getTtCandidatesStudio()

    fun candidatePackages(variant: TikTokAppVariant): List<String> = when (variant) {
        TikTokAppVariant.STANDARD -> CANDIDATES_STANDARD
        TikTokAppVariant.LITE -> CANDIDATES_LITE
        TikTokAppVariant.STUDIO -> CANDIDATES_STUDIO
    }

    fun packageNameOf(variant: TikTokAppVariant): String = when (variant) {
        TikTokAppVariant.STANDARD -> CANDIDATES_STANDARD.first()
        TikTokAppVariant.LITE -> CANDIDATES_LITE.first()
        TikTokAppVariant.STUDIO -> CANDIDATES_STUDIO.first()
    }

    fun isInstalled(context: Context, variant: TikTokAppVariant): Boolean {
        val candidates = candidatePackages(variant)
        for (pkg in candidates) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    /** Buộc dừng app TikTok (kill background processes) */
    fun forceStop(context: Context, variant: TikTokAppVariant) {
        val candidates = candidatePackages(variant)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        for (pkg in candidates) {
            try {
                am?.killBackgroundProcesses(pkg)
            } catch (_: Exception) {}
        }
    }

    /** Mở app TikTok tương ứng (buộc dừng trước nếu cần). Trả về false nếu chưa cài / không mở được. */
    fun launch(context: Context, variant: TikTokAppVariant, forceStopFirst: Boolean = false): Boolean {
        if (forceStopFirst) {
            forceStop(context, variant)
            try {
                Thread.sleep(400)
            } catch (_: Exception) {}
        }
        val candidates = candidatePackages(variant)
        for (pkg in candidates) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {
                }
            }
        }

        // Fallback tường minh nếu launch intent không ra
        val explicitComponent = when (variant) {
            TikTokAppVariant.STANDARD -> ComponentName(NativeSecurity.getTtPkgStandardFirst(), NativeSecurity.getTtSplashStandard())
            TikTokAppVariant.LITE -> ComponentName(NativeSecurity.getTtPkgLite(), NativeSecurity.getTtSplashLite())
            TikTokAppVariant.STUDIO -> ComponentName(NativeSecurity.getTtPkgStudio(), NativeSecurity.getTtSplashStudio())
        }
        val intent = Intent().apply {
            component = explicitComponent
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Đưa app CayXu quay lại màn hình (dùng sau khi lớp nổi lưu xong @handle). */
    fun bringToolToFront(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /** Mở link profile / video trên TikTok hoặc trình duyệt */
    fun openUserProfile(context: Context, url: String): Boolean {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, TikTokAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (!enabled.isNullOrBlank()) {
            val splitter = android.text.TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val next = splitter.next()
                if (next.equals(expected, ignoreCase = true) || next.contains(context.packageName, ignoreCase = true)) {
                    return true
                }
            }
        }
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
