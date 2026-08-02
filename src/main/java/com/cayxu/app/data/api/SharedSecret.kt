package com.cayxu.app.data.api

/**
 * Secret DÙNG CHUNG với Cloudflare Worker (không liên quan gì tới key/device_id của người
 * dùng) để ký (HMAC) và mã hoá (AES-GCM) toàn bộ request/response giữa app và Worker. Không
 * lưu dạng chữ rõ trong code (tránh grep chuỗi tĩnh qua jadx/Dex Editor) - mã hoá XOR y hệt
 * cách BASE_URL đang dùng ở RetrofitClient, chỉ giải mã lúc chạy.
 *
 * ⚠️ BẮT BUỘC: secret giải mã ra ở đây phải GIỐNG HỆT giá trị bạn đặt cho Worker bằng lệnh:
 *     wrangler secret put API_SHARED_SECRET
 * Xem hướng dẫn tạo mảng OBFUSCATED ở cuối file.
 */
internal object SharedSecret {

    // ⚠️ THAY mảng này bằng secret THẬT của bạn - placeholder dưới đây chỉ để code build được,
    // KHÔNG dùng nguyên trong production vì nó không bí mật (đã đưa vào tin nhắn này).
    private val OBFUSCATED = intArrayOf(
        107, 105, 111, 63, 62, 57, 105, 110, 107, 59, 110, 109, 57, 111, 106, 63, 59, 63, 104,
        109, 59, 57, 109, 63, 108, 108, 59, 107, 98, 62, 110, 98, 59, 106, 104, 105, 98, 60, 57,
        56, 60, 105, 99, 109, 111, 105, 98, 104, 98, 108, 56, 57, 105, 59, 62, 99, 99, 105, 109,
        109, 111, 111, 106, 110
    )
    private const val XOR_KEY = 0x5A

    fun value(): String {
        val chars = CharArray(OBFUSCATED.size)
        for (i in OBFUSCATED.indices) {
            chars[i] = (OBFUSCATED[i] xor XOR_KEY).toChar()
        }
        return String(chars)
    }
}

/*
================================================================================================
 HƯỚNG DẪN THAY SECRET THẬT (bắt buộc phải làm trước khi phát hành bản build này):

 1) Tạo 1 secret ngẫu nhiên đủ mạnh (chạy trên máy tính của bạn, KHÔNG chạy trong app):
        openssl rand -hex 32

    Ví dụ kết quả: 9f3a7c1e0b6d4a2f8e5c... (64 ký tự hex)

 2) Đặt CHÍNH XÁC secret đó cho Cloudflare Worker:
        wrangler secret put API_SHARED_SECRET
    (dán nguyên chuỗi vừa tạo ở bước 1 khi được hỏi)

 3) Tạo mảng OBFUSCATED tương ứng cho secret đó bằng đoạn Python nhỏ này (chạy 1 lần trên máy
    tính, không đưa đoạn script này vào app):

        secret = "9f3a7c1e0b6d4a2f8e5c..."   # đúng secret ở bước 1, dán full vào đây
        xor_key = 0x5A
        print(", ".join(str(ord(c) ^ xor_key) for c in secret))

 4) Copy kết quả (1 dòng số cách nhau dấu phẩy) thay thế TOÀN BỘ nội dung bên trong
    intArrayOf(...) ở OBFUSCATED phía trên.

 Nếu không đổi, secret placeholder hiện tại coi như ĐÃ BỊ LỘ (vì nó nằm trong lịch sử chat
 này) và không còn tác dụng bảo mật - nhớ đổi trước khi build bản phát hành thật.
================================================================================================
*/
