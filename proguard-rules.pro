# ============================================================================
# ProGuard / R8 Full Arabic Script Obfuscation Dictionaries (7000+ Unique Tokens)
# Ép 100% Class, Package, Method, Field dùng toàn bộ từ điển tiếng Ả Rập
# ============================================================================
-obfuscationdictionary dict_arabic.txt
-classobfuscationdictionary dict_arabic.txt
-packageobfuscationdictionary dict_arabic.txt

-dontusemixedcaseclassnames
-useuniqueclassmembernames

# Retrofit / OkHttp / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.cayxu.app.data.model.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Google Tink (dùng bởi androidx.security.crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# WorkManager Worker
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }

# Giữ nguyên tên class cầu nối Native JNI để C++ FindClass liên kết thành công
-keep class com.cayxu.app.util.NativeSecurity {
    native <methods>;
    *;
}

# ============================================================================
# Tối ưu hóa và làm rối cấp cao
# ============================================================================
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# Loại bỏ log ở bản release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Ẩn tên file nguồn gốc
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
