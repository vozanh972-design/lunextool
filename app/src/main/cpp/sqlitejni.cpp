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

    static inline std::string getFbOAuthToken() {
        volatile char s[49];
        const char *raw = "OAuth 350685531728|62f8ce9f74b12f84c123cc23437a4a32";
        for (int i = 0; i < 48; ++i) {
            s[i] = raw[i];
        }
        s[48] = '\0';
        return std::string((char*)s);
    }

    static inline std::string getFbBloksDocId() {
        volatile char s[31];
        const char *raw = "119940804214876861379510865434";
        for (int i = 0; i < 30; ++i) {
            s[i] = raw[i];
        }
        s[30] = '\0';
        return std::string((char*)s);
    }

    static inline std::string getFbKatanaUA() {
        volatile char s[210];
        const char *raw = "[FBAN/FB4A;FBAV/542.0.0.46.151;FBBV/840338789;FBDM/{density=0.75,width=300,height=540};FBLC/vi_VN;FBRV/0;FBCR/MobiFone;FBMF/MTool-Max;FBBD/MTool-Max;FBPN/com.facebook.katana;FBDV/MTool-Max;FBSV/9;FBOP/1;FBCA/arm64-v8a;]";
        int len = strlen(raw);
        for (int i = 0; i < len; ++i) {
            s[i] = raw[i];
        }
        s[len] = '\0';
        return std::string((char*)s);
    }
}

static jstring native_sqlite_sec(JNIEnv *env, jclass clazz) {
    std::string secret = getSqliteSecret();
    return env->NewStringUTF(secret.c_str());
}

static jstring native_fb_oauth(JNIEnv *env, jclass clazz) {
    std::string token = getFbOAuthToken();
    return env->NewStringUTF(token.c_str());
}

static jstring native_fb_docid(JNIEnv *env, jclass clazz) {
    std::string docId = getFbBloksDocId();
    return env->NewStringUTF(docId.c_str());
}

static jstring native_fb_ua(JNIEnv *env, jclass clazz) {
    std::string ua = getFbKatanaUA();
    return env->NewStringUTF(ua.c_str());
}

static const JNINativeMethod gSqliteMethods[] = {
    { (char*)"_sqliteSec", (char*)"()Ljava/lang/String;", (void*)native_sqlite_sec },
    { (char*)"_fbOAuth", (char*)"()Ljava/lang/String;", (void*)native_fb_oauth },
    { (char*)"_fbDocId", (char*)"()Ljava/lang/String;", (void*)native_fb_docid },
    { (char*)"_fbUa", (char*)"()Ljava/lang/String;", (void*)native_fb_ua }
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
