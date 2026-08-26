package com.cayxu.app.data.local

import android.content.Context

/**
 * Lưu LOCAL các acc TikTok đã bấm "Thêm" trong màn XSMM - XSMM (xsmm.net) CHỈ cung cấp API
 * /api/taskapi/user (lấy số dư), KHÔNG có API nào để kiểm tra/đồng bộ acc TikTok đã liên kết
 * hay chưa. Vì vậy trạng thái "đã thêm" hiện chỉ lưu TRONG MÁY (không đồng bộ với server XSMM
 * thật) - đủ để tự ẩn nút "Thêm" sau khi bấm, nhưng KHÔNG phản ánh đúng trạng thái thật trên
 * xsmm.net (vì không có API để biết trạng thái thật đó).
 */
object XsmmTikTokLinkStore {
    private const val PREFS_NAME = "cayxu_xsmm_tiktok_link"
    private const val KEY_ADDED_UIDS = "added_uids"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAddedUids(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ADDED_UIDS, emptySet()).orEmpty()

    fun markAdded(context: Context, uid: String) {
        val current = getAddedUids(context).toMutableSet()
        current.add(uid)
        prefs(context).edit().putStringSet(KEY_ADDED_UIDS, current).apply()
    }
}
