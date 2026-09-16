#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/ptrace.h>
#include <sys/system_properties.h>
#include <sys/types.h>
#include <signal.h>
#include <stdlib.h>
#include <fcntl.h>
#include <stdint.h>

// ============================================================================
// Module: androidx.graphics.path (libandroidx.graphics.path.so) - OLLVM Hardened
// Control Flow Flattening (CFF) + Instruction Substitution (MBA) + XOR String Encryption
// ============================================================================

namespace {

__attribute__((always_inline)) static inline std::string decryptOllvmString(const uint8_t* data, size_t len, uint8_t baseKey, uint8_t step) {
    std::string result;
    result.resize(len);
    volatile uint32_t state = 0xA1B2C3D4;
    size_t idx = 0;
    while (state != 0) {
        switch (state) {
            case 0xA1B2C3D4: { idx = 0; state = 0x5E6F7A8B; break; }
            case 0x5E6F7A8B: { state = (idx < len) ? 0x9C8D7E6F : 0x11223344; break; }
            case 0x9C8D7E6F: {
                uint8_t k = (baseKey + (uint8_t)(idx * step)) & 0xFF;
                uint8_t raw = data[idx];
                uint8_t dec = (raw | k) - (raw & k);
                result[idx] = (char)dec;
                idx++;
                state = 0x5E6F7A8B;
                break;
            }
            case 0x11223344: { state = 0; break; }
            default: { state = 0; break; }
        }
    }
    return result;
}

const uint8_t enc_baseUrl[20] = { 0x34, 0x17, 0x1E, 0x01, 0x0B, 0x45, 0xA9, 0xA2, 0xF8, 0xEE, 0xCC, 0xCC, 0xC8, 0x99, 0xD7, 0xAA, 0xE2, 0xA5, 0xB4, 0xCE };

static inline std::string getNativeUrlFragment() {
    return decryptOllvmString(enc_baseUrl, 20, 0x5C, 7);
}

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

static bool isMemoryHookedOrDebugged() {
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) return true;

    FILE* maps = fopen("/proc/self/maps", "r");
    if (maps) {
        char mapBuf[512];
        while (fgets(mapBuf, sizeof(mapBuf), maps)) {
            for (char* p = mapBuf; *p; ++p) *p = (char)tolower(*p);
            if (strstr(mapBuf, "frida") != nullptr ||
                strstr(mapBuf, "xposed") != nullptr ||
                strstr(mapBuf, "substrate") != nullptr ||
                strstr(mapBuf, "edxposed") != nullptr ||
                strstr(mapBuf, "lsposed") != nullptr ||
                strstr(mapBuf, "sslunpinning") != nullptr ||
                strstr(mapBuf, "justtrustme") != nullptr ||
                strstr(mapBuf, "httptoolkit") != nullptr ||
                strstr(mapBuf, "gadget") != nullptr) {
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
        raise(SIGKILL);
        return 3;
    }
    return 0;
}

static jstring native_path_source(JNIEnv *env, jclass clazz) {
    std::string url = getNativeUrlFragment();
    return env->NewStringUTF(url.c_str());
}

static const JNINativeMethod gPathMethods[] = {
    { (char*)"_pathVal", (char*)"(Landroid/content/Context;)I", (void*)native_path_validate },
    { (char*)"_pathSrc", (char*)"()Ljava/lang/String;", (void*)native_path_source }
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    ptrace(PTRACE_TRACEME, 0, 1, 0);

    jclass clazz = env->FindClass("com/cayxu/app/util/NativeSecurity");
    if (!clazz) return JNI_ERR;

    if (env->RegisterNatives(clazz, gPathMethods, sizeof(gPathMethods) / sizeof(gPathMethods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}