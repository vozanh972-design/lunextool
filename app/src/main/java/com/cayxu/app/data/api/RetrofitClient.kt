package com.cayxu.app.data.api

import com.cayxu.app.util.decodeText
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Base URL KHÔNG được lưu dạng chữ trực tiếp trong code, để tránh việc
    // ai đó chỉ cần decompile APK rồi grep chuỗi là thấy ngay địa chỉ server.
    // Chuỗi được mã hoá XOR đơn giản và chỉ giải mã lúc chạy (runtime).
    // Lưu ý: đây chỉ chống được kiểu "tìm chuỗi tĩnh" như Dex Editor/jadx;
    // không chống được việc bắt gói tin (proxy/Frida) khi app đang chạy thật,
    // vì bản chất app luôn phải gửi request thật tới đúng domain này.
    private val OBFUSCATED_BASE_URL = intArrayOf(
        50, 46, 46, 42, 41, 96, 117, 117, 54, 47, 52, 63, 34, 116, 51, 53, 116, 44, 52, 117
    )
    private const val XOR_KEY = 0x5A

    private fun resolveBaseUrl(): String {
        val chars = CharArray(OBFUSCATED_BASE_URL.size)
        for (i in OBFUSCATED_BASE_URL.indices) {
            chars[i] = (OBFUSCATED_BASE_URL[i] xor XOR_KEY).toChar()
        }
        return String(chars)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Chỉ log chi tiết body/header (chứa key người dùng) khi app đang debug.
        // Bản release KHÔNG log body để tránh lộ key qua Logcat trên máy đã root.
        level = if (com.cayxu.app.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // Ghim chứng chỉ (certificate pinning) - chặn việc bắt gói tin qua Charles/Fiddler/
    // mitmproxy/Frida-unpin ngay cả khi máy đã cài CA giả làm root trust (rất phổ biến khi
    // ai đó cố dò API bằng proxy trên máy họ tự root). App sẽ TỪ CHỐI kết nối nếu chứng chỉ
    // server không khớp đúng các hash đã ghim dưới đây, bất kể máy có tin CA nào khác.
    //
    // ⚠️ BẮT BUỘC thay 2 hash placeholder dưới đây bằng hash THẬT trước khi build bản phát
    // hành - xem hướng dẫn lấy hash thật ở cuối file. Ghim ÍT NHẤT 2 hash (chứng chỉ hiện tại
    // + 1 hash dự phòng) để tránh app ngừng hoạt động khi Cloudflare tự động đổi chứng chỉ.
    // Domain cũng được giải mã lúc chạy (giống resolveBaseUrl ở trên) thay vì để
    // dạng chữ trực tiếp, tránh grep/jadx thấy ngay tên miền server trong .add(...).
    private val pinnedDomain =
        decodeText(54, 47, 52, 63, 34, 116, 51, 53, 116, 44, 52)

    private val certificatePinner = CertificatePinner.Builder()
        .add(pinnedDomain, "sha256/ZJC5IGL/O/c6TSM+rsSyheuIh/Akc/GmM+dyizIpUGA=")
        .add(pinnedDomain, "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .certificatePinner(certificatePinner)
        // Đã bỏ SecureApiInterceptor (lớp mã hoá dành cho Cloudflare Worker) vì không còn
        // dùng Worker trung gian nữa. Request gửi thẳng dạng form-urlencoded gốc
        // (key=...&device_id=...) tới verify_key.php trên server chính.
        .addInterceptor(loggingInterceptor)
        .build()

    // Path của endpoint cũng được giải mã lúc chạy, không để "verify_key.php" ghi
    // cứng trong @POST(...) của ApiService (annotation bắt buộc hằng số biên dịch nên
    // không decode được ngay tại đó) - AuthRepository sẽ truyền chuỗi này vào @Url.
    val VERIFY_KEY_PATH =
        decodeText(59, 42, 51, 117, 44, 63, 40, 51, 60, 35, 5, 49, 63, 35, 116, 42, 50, 42)

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(resolveBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

/*
================================================================================================
 HƯỚNG DẪN LẤY HASH CHỨNG CHỈ THẬT (certificate pin) CHO lunex.io.vn:

 Chạy lệnh này trên máy tính (Linux/Mac, hoặc WSL/Git Bash trên Windows):

     openssl s_client -connect lunex.io.vn:443 -servername lunex.io.vn </dev/null 2>/dev/null \
       | openssl x509 -pubkey -noout \
       | openssl pkey -pubin -outform der \
       | openssl dgst -sha256 -binary \
       | openssl enc -base64

 Kết quả in ra là 1 chuỗi base64 - đó chính là giá trị điền vào sau "sha256/" ở pin THỨ NHẤT.

 Vì Cloudflare tự động xoay vòng chứng chỉ theo thời gian, bạn nên ghim THÊM 1 pin dự phòng là
 public key của chứng chỉ TRUNG GIAN (intermediate CA) mà Cloudflare đang dùng cho domain này -
 pin này ổn định lâu hơn pin của chứng chỉ lá (leaf), giảm rủi ro app ngừng hoạt động đột ngột
 khi Cloudflare đổi chứng chỉ lá định kỳ. Cloudflare Dashboard > SSL/TLS > Edge Certificates sẽ
 cho bạn xem chuỗi chứng chỉ hiện tại để lấy đúng CA trung gian đang áp dụng.

 ⚠️ LƯU Ý QUAN TRỌNG: certificate pinning là con dao 2 lưỡi - nếu bạn quên cập nhật pin khi
 Cloudflare đổi chứng chỉ, TOÀN BỘ app sẽ mất kết nối server (không phải bug, mà pinning đang
 làm đúng việc của nó là từ chối chứng chỉ lạ). Nên theo dõi hạn chứng chỉ hiện tại và có kế
 hoạch cập nhật pin trước khi hết hạn.
================================================================================================
*/
