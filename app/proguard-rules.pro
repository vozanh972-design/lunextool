# Retrofit / OkHttp / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.cayxu.app.data.model.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Google Tink (dùng bởi androidx.security.crypto / EncryptedSharedPreferences)
# tham chiếu tới các annotation của errorprone chỉ dùng lúc biên dịch, không có
# mặt lúc runtime -> chỉ cần bỏ qua cảnh báo, không ảnh hưởng gì tới hoạt động
# thật của app.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ==== Tăng cường chống crack ở mức TIÊU CHUẨN NGÀNH (không phải kỹ thuật né tránh
# phân tích hành vi - chỉ làm code khó đọc/khó sao chép hơn với người thường) ====

# Đóng gói lại TẤT CẢ class đã đổi tên vào 1 package rỗng duy nhất (thay vì giữ
# nguyên cấu trúc package com.cayxu.app.ui.xxx.yyy dễ đoán) - làm khó việc dò
# theo cấu trúc thư mục khi mở file .smali/.class đã dịch ngược.
-repackageclasses ''
-allowaccessmodification

# R8 mặc định đã tối ưu (inline hàm nhỏ, gộp nhánh trùng...) - chỉ định rõ số
# vòng lặp tối ưu hoá tối đa để ép chạy hết mức có thể.
-optimizationpasses 5

# Loại bỏ log Log.d/Log.v/Log.i ở bản release (biên dịch thẳng ra khỏi bytecode,
# không chỉ ẩn ở UI) - giảm rò rỉ chi tiết luồng chạy qua Logcat.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Không giữ lại số dòng gốc trong crash log ẩn danh (đổi thành "SourceFile" chung
# chung) - vẫn nhận được crash report từ Play Console/Firebase (dùng file mapping.txt
# để dịch ngược riêng cho mình), nhưng người khác cầm APK không tự map lại được.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
