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

// ============================================================================
// Module: androidx.graphics.path (libandroidx.graphics.path.so)
// ============================================================================

namespace {
    static bool isDeviceRooted() {
        const char* rootPaths[] = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu",
            "/system/app/Superuser.apk",
            "/system/app/Magisk.apk",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/data/adb/magisk"
        };
        for (const char* path : rootPaths) {
            struct stat sb;
            if (stat(path, &sb) == 0) return true;
        }
        return false;
    }

    static bool isRunningOnEmulator() {
        const char* qemuPipes[] = {
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/dev/goldfish_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace"
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
        int fd = open("/proc/self/status", O_RDONLY);
        if (fd >= 0) {
            char buffer[1024];
            int bytesRead = read(fd, buffer, sizeof(buffer) - 1);
            close(fd);
            if (bytesRead > 0) {
                buffer[bytesRead] = '\0';
                char *tracerPid = strstr(buffer, "TracerPid:");
                if (tracerPid) {
                    tracerPid += 10;
                    while (*tracerPid == ' ' || *tracerPid == '\t') tracerPid++;
                    if (*tracerPid != '0' && *tracerPid != '\n' && *tracerPid != '\r') {
                        return true;
                    }
                }
            }
        }

        int mapFd = open("/proc/self/maps", O_RDONLY);
        if (mapFd >= 0) {
            char mapBuf[4096];
            int mapRead = read(mapFd, mapBuf, sizeof(mapBuf) - 1);
            close(mapFd);
            if (mapRead > 0) {
                mapBuf[mapRead] = '\0';
                if (strstr(mapBuf, "frida") != nullptr ||
                    strstr(mapBuf, "xposed") != nullptr ||
                    strstr(mapBuf, "sandhook") != nullptr ||
                    strstr(mapBuf, "edxposed") != nullptr ||
                    strstr(mapBuf, "lsposed") != nullptr ||
                    strstr(mapBuf, "substrate") != nullptr ||
                    strstr(mapBuf, "dobby") != nullptr ||
                    strstr(mapBuf, "shadowhook") != nullptr ||
                    strstr(mapBuf, "pine") != nullptr ||
                    strstr(mapBuf, "sslunpinning") != nullptr ||
                    strstr(mapBuf, "justtrustme") != nullptr ||
                    strstr(mapBuf, "httptoolkit") != nullptr ||
                    strstr(mapBuf, "gadget") != nullptr) {
                    return true;
                }
            }
        }
        return false;
    }

    static inline std::string getNativeUrlFragment() {
        volatile char s[21];
        s[0]='h'; s[1]='t'; s[2]='t'; s[3]='p'; s[4]='s'; s[5]=':';
        s[6]='/'; s[7]='/'; s[8]='l'; s[9]='u'; s[10]='n'; s[11]='e';
        s[12]='x'; s[13]='.'; s[14]='i'; s[15]='o'; s[16]='.'; s[17]='v';
        s[18]='n'; s[19]='/'; s[20]='\0';
        return std::string((char*)s);
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
