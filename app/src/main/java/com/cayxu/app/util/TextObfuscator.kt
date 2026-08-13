package com.cayxu.app.util

private const val TEXT_XOR_KEY = 0x5A

/**
 * Giải mã 1 chuỗi text UI đã được mã hoá XOR lúc build, chỉ giải mã lúc app
 * đang chạy (runtime). Mục đích: tránh việc mở APK bằng công cụ decompile
 * (Dex Editor, jadx...) rồi tìm/grep thấy thẳng nội dung chữ tiếng Việt gốc.
 *
 * Lưu ý giới hạn: cách này chỉ chống được kiểu "tìm chuỗi tĩnh trong file".
 * Nó KHÔNG giấu được logic xử lý (onClick, luồng gọi API...) vì phần đó nằm
 * trong code chứ không phải trong nội dung chữ hiển thị.
 */
fun decodeText(vararg codes: Int): String {
    val chars = CharArray(codes.size)
    for (i in codes.indices) {
        chars[i] = (codes[i] xor TEXT_XOR_KEY).toChar()
    }
    return String(chars)
}
