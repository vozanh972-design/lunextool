package com.cayxu.app.data.api

import com.cayxu.app.util.NativeSecurity

/**
 * Secret dùng cho API / Cloudflare Worker:
 * Được mã hóa trong các thanh ghi máy C++ (libqcx.so) dạng Stack String,
 * không lưu chuỗi tĩnh trong DEX hay mã bytecode Java.
 */
internal object SharedSecret {

    fun value(): String {
        return NativeSecurity.getApiSharedSecret()
    }
}
