package com.cayxu.app

import android.app.Application
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.util.IntegrityGuard
import com.cayxu.app.worker.KeyRecheckWorker

class CayXuApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val prefs = SecurePrefs(this)

        // Kiểm tra tính toàn vẹn ngay khi app khởi động: nếu phát hiện app đã bị
        // patch/ký lại (chữ ký APK sai) hoặc đang bị debug/hook -> khoá vĩnh viễn.
        if (IntegrityGuard.isTampered(this)) {
            prefs.setPermanentlyBlocked()
        }

        // Bắt đầu chuỗi tự re-check key định kỳ (ngẫu nhiên 3-10 tiếng/lần) với
        // server thật - hoạt động độc lập với UI nên không bị ảnh hưởng nếu màn
        // Login bị bypass.
        KeyRecheckWorker.scheduleNext(this)
    }
}
