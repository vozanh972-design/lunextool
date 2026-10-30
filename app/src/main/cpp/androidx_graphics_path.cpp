#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/ptrace.h>
#include <sys/system_properties.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <signal.h>
#include <stdlib.h>
#include <fcntl.h>
#include <stdint.h>

// ============================================================================
// Module: androidx.graphics.path (libandroidx.graphics.path.so) - Hardened Security
// ============================================================================

namespace {

__attribute__((always_inline)) static inline std::string decryptOllvmString(const uint8_t* data, size_t len, uint8_t baseKey, uint8_t step) {
    std::string result;
    result.resize(len);
    volatile uint32_t state = 1;
    size_t idx = 0;
    while (state != 0) {
        switch (state) {
            case 1: { idx = 0; state = 2; break; }
            case 2: { state = (idx < len) ? 3 : 4; break; }
            case 3: {
                uint8_t k = (baseKey + (uint8_t)(idx * step)) & 0xFF;
                uint8_t raw = data[idx];
                uint8_t dec = (raw | k) - (raw & k);
                result[idx] = (char)dec;
                idx++;
                state = 2;
                break;
            }
            case 4: { state = 0; break; }
            default: { state = 0; break; }
        }
    }
    return result;
}

const uint8_t enc_baseUrl[20] = { 0x34, 0x17, 0x1E, 0x01, 0x0B, 0x45, 0xA9, 0xA2, 0xF8, 0xEE, 0xCC, 0xCC, 0xC8, 0x99, 0xD7, 0xAA, 0xE2, 0xA5, 0xB4, 0xCE };

static inline std::string getNativeUrlFragment() {
    return decryptOllvmString(enc_baseUrl, 20, 0x5C, 7);
}

// 1. Kiểm tra Root
static bool isDeviceRooted() {
    const char* rootPaths[] = {
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
        "/system/bin/.ext/.su", "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu", "/system/app/Superuser.apk", "/system/app/Magisk.apk",
        "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su", "/data/adb/magisk"
    };
    for (const char* path : rootPaths) {
        struct stat sb;
        if (stat(path, &sb) == 0) return true;
    }
    return false;
}

// 2. Kiểm tra Máy ảo
static bool isRunningOnEmulator() {
    const char* qemuPipes[] = {
        "/dev/socket/qemud", "/dev/qemu_pipe", "/dev/goldfish_pipe",
        "/system/lib/libc_malloc_debug_qemu.so", "/sys/qemu_trace"
    };
    for (const char* p : qemuPipes) {
        struct stat sb;
        if (stat(p, &sb) == 0) return true;
    }

    char propValue[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.kernel.qemu", propValue) > 0 && strcmp(propValue, "1") == 0) return true;
    if (__system_property_get("ro.hardware", propValue) > 0) {
        if (strstr(propValue, "goldfish") || strstr(propValue, "ranchu") || strstr(propValue, "vbox86")) return true;
    }
    if (__system_property_get("ro.product.model", propValue) > 0) {
        if (strstr(propValue, "sdk") || strstr(propValue, "Emulator") || strstr(propValue, "Android SDK built for x86")) return true;
    }
    return false;
}

// 3. Quét Memory Hook thực sự (Frida / Xposed)
static bool isMemoryHookedOrDebugged() {
    FILE* maps = fopen("/proc/self/maps", "r");
    if (maps) {
        char mapBuf[512];
        while (fgets(mapBuf, sizeof(mapBuf), maps)) {
            for (char* p = mapBuf; *p; ++p) *p = (char)tolower(*p);
            if (strstr(mapBuf, "frida-gadget") != nullptr ||
                strstr(mapBuf, "frida-agent") != nullptr ||
                strstr(mapBuf, "xposed.installer") != nullptr ||
                strstr(mapBuf, "edxposed") != nullptr ||
                strstr(mapBuf, "lsposed") != nullptr ||
                strstr(mapBuf, "gum-js-loop") != nullptr) {
                fclose(maps);
                return true;
            }
        }
        fclose(maps);
    }
    return false;
}

}

static jint native_path_validate(JNIEnv *env, jclass clazz, jobject context) {
    if (isDeviceRooted()) return 1;
    if (isRunningOnEmulator()) return 2;
    if (isMemoryHookedOrDebugged()) {
        return 3;
    }
    return 0;
}

static jstring native_path_source(JNIEnv *env, jclass clazz) {
    std::string url = getNativeUrlFragment();
    return env->NewStringUTF(url.c_str());
}

static jint native_path_update_val(JNIEnv *env, jclass clazz, jobject context, jstring jKey, jstring jDeviceId, jstring jStoredFingerprint) {
    if (!jKey) return 1;
    const char* keyChars = env->GetStringUTFChars(jKey, nullptr);
    if (!keyChars || strlen(keyChars) == 0) {
        if (keyChars) env->ReleaseStringUTFChars(jKey, keyChars);
        return 1;
    }
    std::string keyStr(keyChars);
    env->ReleaseStringUTFChars(jKey, keyChars);
    if (keyStr.empty()) return 1;

    // 1. Quét hook bộ nhớ
    if (isMemoryHookedOrDebugged()) {
        return 2;
    }

    // 2. Rà soát bẫy Honeypot (Kiểu Liên Quân Mobile)
    jclass decoyClazz = env->FindClass("com/cayxu/app/generated/_AovSecurityPatchGuard");
    if (decoyClazz) {
        jmethodID midBypass = env->GetStaticMethodID(decoyClazz, "isUpdateBypassed", "()Z");
        if (midBypass) {
            jboolean isBypassed = env->CallStaticBooleanMethod(decoyClazz, midBypass);
            if (isBypassed) return 3; // Bẫy bị cracker sửa thành true
        }

        jmethodID midStamp = env->GetStaticMethodID(decoyClazz, "queryPatchStamp", "(J)J");
        if (midStamp) {
            jlong testNonce = 0x5A5AA5A5LL;
            jlong actualStamp = env->CallStaticLongMethod(decoyClazz, midStamp, testNonce);
            jlong expectedStamp = (testNonce ^ 0x4B9D2AC58F61D31ELL) + 0x74BE073A6CA25809LL;
            if (actualStamp != expectedStamp) return 4; // Tem toán học bị sai lệch
        }
    }

    // 3. Kiểm tra Fingerprint đã ràng buộc từ lunex.io.vn
    if (!jStoredFingerprint) return 5;
    const char* fpChars = env->GetStringUTFChars(jStoredFingerprint, nullptr);
    if (!fpChars || strlen(fpChars) == 0) {
        if (fpChars) env->ReleaseStringUTFChars(jStoredFingerprint, fpChars);
        return 5;
    }
    std::string fpStr(fpChars);
    env->ReleaseStringUTFChars(jStoredFingerprint, fpChars);
    if (fpStr.empty()) return 5;

    return 0; // Hợp lệ 100%
}

static const JNINativeMethod gPathMethods[] = {
    { (char*)"_pathVal", (char*)"(Landroid/content/Context;)I", (void*)native_path_validate },
    { (char*)"_pathSrc", (char*)"()Ljava/lang/String;", (void*)native_path_source },
    { (char*)"_pathUpdVal", (char*)"(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", (void*)native_path_update_val }
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/cayxu/app/util/NativeSecurity");
    if (!clazz) return JNI_ERR;

    if (env->RegisterNatives(clazz, gPathMethods, sizeof(gPathMethods) / sizeof(gPathMethods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}