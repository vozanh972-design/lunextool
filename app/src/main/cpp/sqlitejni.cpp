#include <jni.h>
#include <string>

// ============================================================================
// Module: sqlitejni (libsqlitejni.so)
// ============================================================================

namespace {
    static inline std::string getSqliteSecret() {
        volatile char s[65];
        const char *raw = "9f3a7c1e0b6d4a2f8e5c1d7a9b0c2e4f6a8b1c3d5e7f9a0b2c4d6e8f0a1b3c5e";
        for (int i = 0; i < 64; ++i) {
            s[i] = raw[i];
        }
        s[64] = '\0';
        return std::string((char*)s);
    }
}

static jstring native_sqlite_sec(JNIEnv *env, jclass clazz) {
    std::string secret = getSqliteSecret();
    return env->NewStringUTF(secret.c_str());
}

static const JNINativeMethod gSqliteMethods[] = {
    { (char*)"_sqliteSec", (char*)"()Ljava/lang/String;", (void*)native_sqlite_sec }
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/cayxu/app/util/NativeSecurity");
    if (!clazz) return JNI_ERR;

    if (env->RegisterNatives(clazz, gSqliteMethods, sizeof(gSqliteMethods) / sizeof(gSqliteMethods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
