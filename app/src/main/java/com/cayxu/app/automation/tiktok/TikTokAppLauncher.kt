package com.cayxu.app.automation.tiktok

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.cayxu.app.data.local.TikTokAppVariant

/**
 * Chỉ phục vụ luồng "thêm tài khoản TikTok bằng cách check trong app thật" - RIÊNG cho TikTok,
 * không đụng tới luồng nhập UID thủ công của các nền tảng khác.
 */
object TikTokAppLauncher {

    // Package/Activity của từng bản TikTok.
    private const val PKG_STANDARD = "com.ss.android.ugc.trill"
    private const val ACTIVITY_STANDARD = "com.ss.android.ugc.aweme.splash.SplashActivity"
    private const val PKG_LITE = "com.zhiliaoapp.musically.go"
    private const val ACTIVITY_LITE = "com.zhiliaoapp.musically.go.mini.MainActivity"
    private const val PKG_STUDIO = "com.ss.android.tt.creator"
    private const val ACTIVITY_STUDIO = "com.ss.android.ugc.aweme.splash.SplashActivity"

    fun packageNameOf(variant: TikTokAppVariant): String = when (variant) {
        TikTokAppVariant.STANDARD -> PKG_STANDARD
        TikTokAppVariant.LITE -> PKG_LITE
        TikTokAppVariant.STUDIO -> PKG_STUDIO
    }

    fun isInstalled(context: Context, variant: TikTokAppVariant): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageNameOf(variant), 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Mở app TikTok tương ứng. Trả về false nếu chưa cài / không mở được. */
    fun launch(context: Context, variant: TikTokAppVariant): Boolean {
        val explicitComponent = when (variant) {
            TikTokAppVariant.STANDARD -> ComponentName(PKG_STANDARD, ACTIVITY_STANDARD)
            TikTokAppVariant.LITE -> ComponentName(PKG_LITE, ACTIVITY_LITE)
            TikTokAppVariant.STUDIO -> ComponentName(PKG_STUDIO, ACTIVITY_STUDIO)
        }
        val intent = Intent().apply {
            component = explicitComponent
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback: nếu activity tường minh bị đổi tên/khác ở máy này (khác OEM/phiên bản
            // app), thử mở bằng launch intent mặc định của package.
            val fallback = context.packageManager.getLaunchIntentForPackage(packageNameOf(variant))
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            if (fallback != null) {
                try {
                    context.startActivity(fallback)
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
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
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
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
