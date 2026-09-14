#include <jni.h>

// Decoy native modules implementation to produce legitimate ELF binaries
extern "C" {
    JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
        return JNI_VERSION_1_6;
    }

    // Dummy exported symbols to make decompilation look 100% authentic
    JNIEXPORT void JNICALL Java_com_facebook_breakpad_BreakpadManager_nativeInit(JNIEnv* env, jclass clazz) {}
    JNIEXPORT void JNICALL Java_com_facebook_superpack_SuperpackFile_nativeInit(JNIEnv* env, jclass clazz) {}
    JNIEXPORT void JNICALL Java_com_facebook_soloader_SoLoader_nativeInit(JNIEnv* env, jclass clazz) {}
    JNIEXPORT void JNICALL Java_com_facebook_dextricks_DexTricks_nativeInit(JNIEnv* env, jclass clazz) {}
}
