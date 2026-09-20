// fb_vault.cpp
// Obfuscation tầng native: constexpr XOR tại compile-time
// Compiler tự encode → .rodata chỉ chứa XOR bytes, không có plain text
// Không cần tính thủ công → không sai sót

#include <jni.h>
#include <string>
#include <android/log.h>

namespace {
    static constexpr unsigned char K = 0x5C;

    // Compile-time XOR encode một ký tự
    static constexpr unsigned char e(char c) { return (unsigned char)((unsigned char)c ^ K); }

    // Runtime decode: XOR ngược lại để lấy string gốc
    template<size_t N>
    static std::string xd(const unsigned char (&arr)[N]) {
        std::string s;
        s.reserve(N);
        for (size_t i = 0; i < N; ++i) s += (char)(arr[i] ^ K);
        return s;
    }

    // ── Các chuỗi được encode tại compile-time ──────────────────────────────
    // Compiler thấy ký tự gốc nhưng binary .so chỉ chứa XOR bytes

    // "https://graph.facebook.com/v21.0"
    static constexpr unsigned char k_GRAPH_API[] = {
        e('h'),e('t'),e('t'),e('p'),e('s'),e(':'),e('/'),e('/'),
        e('g'),e('r'),e('a'),e('p'),e('h'),e('.'),e('f'),e('a'),
        e('c'),e('e'),e('b'),e('o'),e('o'),e('k'),e('.'),e('c'),
        e('o'),e('m'),e('/'),e('v'),e('2'),e('1'),e('.'),e('0')
    };

    // "https://graph.facebook.com/graphql"
    static constexpr unsigned char k_GRAPHQL[] = {
        e('h'),e('t'),e('t'),e('p'),e('s'),e(':'),e('/'),e('/'),
        e('g'),e('r'),e('a'),e('p'),e('h'),e('.'),e('f'),e('a'),
        e('c'),e('e'),e('b'),e('o'),e('o'),e('k'),e('.'),e('c'),
        e('o'),e('m'),e('/'),e('g'),e('r'),e('a'),e('p'),e('h'),
        e('q'),e('l')
    };

    // "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
    static constexpr unsigned char k_UA[] = {
        e('['),e('F'),e('B'),e('A'),e('N'),e('/'),e('F'),e('B'),e('4'),e('A'),
        e(';'),e('F'),e('B'),e('A'),e('V'),e('/'),e('5'),e('4'),e('8'),e('.'),
        e('1'),e('.'),e('0'),e('.'),e('5'),e('1'),e('.'),e('6'),e('4'),e(';'),
        e('F'),e('B'),e('B'),e('V'),e('/'),e('4'),e('7'),e('4'),e('6'),e('1'),
        e('8'),e('9'),e('2'),e('9'),e(';'),e('F'),e('B'),e('D'),e('M'),e('/'),
        e('{'),e('d'),e('e'),e('n'),e('s'),e('i'),e('t'),e('y'),e('='),e('3'),
        e('.'),e('0'),e(','),e('w'),e('i'),e('d'),e('t'),e('h'),e('='),e('1'),
        e('0'),e('8'),e('0'),e(','),e('h'),e('e'),e('i'),e('g'),e('h'),e('t'),
        e('='),e('2'),e('3'),e('4'),e('0'),e('}'),e(';'),e('F'),e('B'),e('L'),
        e('C'),e('/'),e('v'),e('i'),e('_'),e('V'),e('N'),e(';'),e('F'),e('B'),
        e('R'),e('V'),e('/'),e('0'),e(';'),e('F'),e('B'),e('C'),e('R'),e('/'),
        e('V'),e('i'),e('e'),e('t'),e('t'),e('e'),e('l'),e(';'),e('F'),e('B'),
        e('M'),e('F'),e('/'),e('s'),e('a'),e('m'),e('s'),e('u'),e('n'),e('g'),
        e(';'),e('F'),e('B'),e('B'),e('D'),e('/'),e('s'),e('a'),e('m'),e('s'),
        e('u'),e('n'),e('g'),e(';'),e('F'),e('B'),e('P'),e('N'),e('/'),e('c'),
        e('o'),e('m'),e('.'),e('f'),e('a'),e('c'),e('e'),e('b'),e('o'),e('o'),
        e('k'),e('.'),e('k'),e('a'),e('t'),e('a'),e('n'),e('a'),e(';'),e('F'),
        e('B'),e('D'),e('V'),e('/'),e('S'),e('M'),e('-'),e('S'),e('9'),e('2'),
        e('8'),e('B'),e(';'),e('F'),e('B'),e('S'),e('V'),e('/'),e('1'),e('4'),
        e(';'),e('F'),e('B'),e('O'),e('P'),e('/'),e('1'),e(';'),e('F'),e('B'),
        e('C'),e('A'),e('/'),e('a'),e('r'),e('m'),e('6'),e('4'),e('-'),e('v'),
        e('8'),e('a'),e(';'),e(']')
    };

    // "4715426135182900"
    static constexpr unsigned char k_DOC_PAGE[] = {
        e('4'),e('7'),e('1'),e('5'),e('4'),e('2'),e('6'),e('1'),
        e('3'),e('5'),e('1'),e('8'),e('2'),e('9'),e('0'),e('0')
    };

    // "5411782298894101"
    static constexpr unsigned char k_DOC_PROFILE[] = {
        e('5'),e('4'),e('1'),e('1'),e('7'),e('8'),e('2'),e('2'),
        e('9'),e('8'),e('8'),e('9'),e('4'),e('1'),e('0'),e('1')
    };

    // "/reactions"
    static constexpr unsigned char k_REACTIONS[] = {
        e('/'),e('r'),e('e'),e('a'),e('c'),e('t'),e('i'),e('o'),e('n'),e('s')
    };

    // "/comments"
    static constexpr unsigned char k_COMMENTS[] = {
        e('/'),e('c'),e('o'),e('m'),e('m'),e('e'),e('n'),e('t'),e('s')
    };

    // "/subscribers"
    static constexpr unsigned char k_SUBSCRIBERS[] = {
        e('/'),e('s'),e('u'),e('b'),e('s'),e('c'),e('r'),e('i'),e('b'),e('e'),e('r'),e('s')
    };

    // "/likes"
    static constexpr unsigned char k_LIKES[] = {
        e('/'),e('l'),e('i'),e('k'),e('e'),e('s')
    };

    // "access_token"
    static constexpr unsigned char k_ACCESS_TOKEN[] = {
        e('a'),e('c'),e('c'),e('e'),e('s'),e('s'),e('_'),e('t'),e('o'),e('k'),e('e'),e('n')
    };

    // "message"
    static constexpr unsigned char k_MESSAGE[] = {
        e('m'),e('e'),e('s'),e('s'),e('a'),e('g'),e('e')
    };

    // "attachment_id"
    static constexpr unsigned char k_ATTACHMENT_ID[] = {
        e('a'),e('t'),e('t'),e('a'),e('c'),e('h'),e('m'),e('e'),e('n'),e('t'),e('_'),e('i'),e('d')
    };

    // "type"
    static constexpr unsigned char k_TYPE[] = {
        e('t'),e('y'),e('p'),e('e')
    };

    // "variables"
    static constexpr unsigned char k_VARIABLES[] = {
        e('v'),e('a'),e('r'),e('i'),e('a'),e('b'),e('l'),e('e'),e('s')
    };

    // "doc_id"
    static constexpr unsigned char k_DOC_ID[] = {
        e('d'),e('o'),e('c'),e('_'),e('i'),e('d')
    };

    // "com/cayxu/app/facebook/FbVault"
    static constexpr unsigned char k_CLASS[] = {
        e('c'),e('o'),e('m'),e('/'),e('c'),e('a'),e('y'),e('x'),e('u'),e('/'),
        e('a'),e('p'),e('p'),e('/'),e('f'),e('a'),e('c'),e('e'),e('b'),e('o'),
        e('o'),e('k'),e('/'),e('F'),e('b'),e('V'),e('a'),e('u'),e('l'),e('t')
    };
}

// ── JNI implementations ───────────────────────────────────────────────────
static jstring jni_graphApiUrl      (JNIEnv* env, jclass) { auto s=xd(k_GRAPH_API);    return env->NewStringUTF(s.c_str()); }
static jstring jni_graphqlUrl       (JNIEnv* env, jclass) { auto s=xd(k_GRAPHQL);      return env->NewStringUTF(s.c_str()); }
static jstring jni_userAgent        (JNIEnv* env, jclass) { auto s=xd(k_UA);           return env->NewStringUTF(s.c_str()); }
static jstring jni_docIdPageReact   (JNIEnv* env, jclass) { auto s=xd(k_DOC_PAGE);     return env->NewStringUTF(s.c_str()); }
static jstring jni_docIdProfileReact(JNIEnv* env, jclass) { auto s=xd(k_DOC_PROFILE);  return env->NewStringUTF(s.c_str()); }
static jstring jni_pathReactions    (JNIEnv* env, jclass) { auto s=xd(k_REACTIONS);    return env->NewStringUTF(s.c_str()); }
static jstring jni_pathComments     (JNIEnv* env, jclass) { auto s=xd(k_COMMENTS);     return env->NewStringUTF(s.c_str()); }
static jstring jni_pathSubscribers  (JNIEnv* env, jclass) { auto s=xd(k_SUBSCRIBERS);  return env->NewStringUTF(s.c_str()); }
static jstring jni_pathLikes        (JNIEnv* env, jclass) { auto s=xd(k_LIKES);        return env->NewStringUTF(s.c_str()); }
static jstring jni_fieldAccessToken (JNIEnv* env, jclass) { auto s=xd(k_ACCESS_TOKEN); return env->NewStringUTF(s.c_str()); }
static jstring jni_fieldMessage     (JNIEnv* env, jclass) { auto s=xd(k_MESSAGE);      return env->NewStringUTF(s.c_str()); }
static jstring jni_fieldAttachmentId(JNIEnv* env, jclass) { auto s=xd(k_ATTACHMENT_ID);return env->NewStringUTF(s.c_str()); }
static jstring jni_fieldType        (JNIEnv* env, jclass) { auto s=xd(k_TYPE);         return env->NewStringUTF(s.c_str()); }
static jstring jni_fieldVariables   (JNIEnv* env, jclass) { auto s=xd(k_VARIABLES);    return env->NewStringUTF(s.c_str()); }
static jstring jni_fieldDocId       (JNIEnv* env, jclass) { auto s=xd(k_DOC_ID);       return env->NewStringUTF(s.c_str()); }

static const JNINativeMethod gFbMethods[] = {
    {(char*)"nativeGraphApiUrl",       (char*)"()Ljava/lang/String;", (void*)jni_graphApiUrl      },
    {(char*)"nativeGraphqlUrl",        (char*)"()Ljava/lang/String;", (void*)jni_graphqlUrl       },
    {(char*)"nativeUserAgent",         (char*)"()Ljava/lang/String;", (void*)jni_userAgent        },
    {(char*)"nativeDocIdPageReact",    (char*)"()Ljava/lang/String;", (void*)jni_docIdPageReact   },
    {(char*)"nativeDocIdProfileReact", (char*)"()Ljava/lang/String;", (void*)jni_docIdProfileReact},
    {(char*)"nativePathReactions",     (char*)"()Ljava/lang/String;", (void*)jni_pathReactions    },
    {(char*)"nativePathComments",      (char*)"()Ljava/lang/String;", (void*)jni_pathComments     },
    {(char*)"nativePathSubscribers",   (char*)"()Ljava/lang/String;", (void*)jni_pathSubscribers  },
    {(char*)"nativePathLikes",         (char*)"()Ljava/lang/String;", (void*)jni_pathLikes        },
    {(char*)"nativeFieldAccessToken",  (char*)"()Ljava/lang/String;", (void*)jni_fieldAccessToken },
    {(char*)"nativeFieldMessage",      (char*)"()Ljava/lang/String;", (void*)jni_fieldMessage     },
    {(char*)"nativeFieldAttachmentId", (char*)"()Ljava/lang/String;", (void*)jni_fieldAttachmentId},
    {(char*)"nativeFieldType",         (char*)"()Ljava/lang/String;", (void*)jni_fieldType        },
    {(char*)"nativeFieldVariables",    (char*)"()Ljava/lang/String;", (void*)jni_fieldVariables   },
    {(char*)"nativeFieldDocId",        (char*)"()Ljava/lang/String;", (void*)jni_fieldDocId       },
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    auto cn = xd(k_CLASS);
    jclass clazz = env->FindClass(cn.c_str());
    if (!clazz) return JNI_ERR;
    if (env->RegisterNatives(clazz, gFbMethods, sizeof(gFbMethods)/sizeof(gFbMethods[0])) < 0) return JNI_ERR;
    return JNI_VERSION_1_6;
}
