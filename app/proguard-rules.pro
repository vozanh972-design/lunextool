# ============================================================================
# ProGuard / R8 Full Obfuscation & Anti-Decompilation Configuration
# Che giấu triệt để tên file nguồn thật, cấu trúc package và class trong classes.dex
# ============================================================================

-obfuscationdictionary dict_random.txt
-classobfuscationdictionary dict_random.txt
-packageobfuscationdictionary dict_random.txt

-dontusemixedcaseclassnames
-allowaccessmodification
-overloadaggressively
-repackageclasses ''
-optimizationpasses 5

# Ẩn/xóa hoàn toàn tên file nguồn thật (.kt / .java) khỏi toàn bộ DEX
-renamesourcefileattribute ''

# Loại bỏ các chuỗi kiểm tra nội bộ Kotlin có chứa tên tham số / file
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}

# Loại bỏ Log ở bản release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============================================================================
# Bảo toàn an toàn 100% Logic ứng dụng (Reflection, Serialization, JNI, Models)
# ============================================================================

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Giữ nguyên các Model dữ liệu & SerializedName để JSON parse chính xác 100%
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.cayxu.app.data.model.** { *; }
-keep class com.cayxu.app.data.local.** { *; }
-keep class com.cayxu.app.facebook.** { *; }

# Giữ nguyên cầu nối Native JNI cho C++ FindClass liên kết thành công
-keepclassmembers class * {
    native <methods>;
}
-keep class com.cayxu.app.util.NativeSecurity {
    native <methods>;
    *;
}

# WorkManager & Android Components
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# Thư viện mạng & mã hóa
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
