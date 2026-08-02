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
