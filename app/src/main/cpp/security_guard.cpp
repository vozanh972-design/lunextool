#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/ptrace.h>
#include <sys/system_properties.h>
#include <fcntl.h>
#include <android/log.h>

#define LOG_TAG "SecurityGuard"

// ==========================================
// SHA-256 Implementation in Pure C++
// ==========================================
namespace sha256_native {
    typedef unsigned int uint32;
    typedef unsigned char uint8;

    #define ROTLEFT(a,b) (((a) << (b)) | ((a) >> (32-(b))))
    #define ROTRIGHT(a,b) (((a) >> (b)) | ((a) << (32-(b))))
    #define CH(x,y,z) (((x) & (y)) ^ (~(x) & (z)))
    #define MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
    #define EP0(x) (ROTRIGHT(x,2) ^ ROTRIGHT(x,13) ^ ROTRIGHT(x,22))
    #define EP1(x) (ROTRIGHT(x,6) ^ ROTRIGHT(x,11) ^ ROTRIGHT(x,25))
    #define SIG0(x) (ROTRIGHT(x,7) ^ ROTRIGHT(x,18) ^ ((x) >> 3))
    #define SIG1(x) (ROTRIGHT(x,17) ^ ROTRIGHT(x,19) ^ ((x) >> 10))

    struct SHA256_CTX {
        uint8 data[64];
        uint32 datalen;
        unsigned long long bitlen;
        uint32 state[8];
    };

    static const uint32 k[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };

    void transform(SHA256_CTX *ctx, const uint8 data[]) {
        uint32 a, b, c, d, e, f, g, h, i, j, t1, t2, m[64];
        for (i = 0, j = 0; i < 16; ++i, j += 4)
            m[i] = (data[j] << 24) | (data[j + 1] << 16) | (data[j + 2] << 8) | (data[j + 3]);
        for ( ; i < 64; ++i)
            m[i] = SIG1(m[i - 2]) + m[i - 7] + SIG0(m[i - 15]) + m[i - 16];

        a = ctx->state[0]; b = ctx->state[1]; c = ctx->state[2]; d = ctx->state[3];
        e = ctx->state[4]; f = ctx->state[5]; g = ctx->state[6]; h = ctx->state[7];

        for (i = 0; i < 64; ++i) {
            t1 = h + EP1(e) + CH(e,f,g) + k[i] + m[i];
            t2 = EP0(a) + MAJ(a,b,c);
            h = g; g = f; f = e; e = d + t1;
            d = c; c = b; b = a; a = t1 + t2;
        }

        ctx->state[0] += a; ctx->state[1] += b; ctx->state[2] += c; ctx->state[3] += d;
        ctx->state[4] += e; ctx->state[5] += f; ctx->state[6] += g; ctx->state[7] += h;
    }

    void init(SHA256_CTX *ctx) {
        ctx->datalen = 0;
        ctx->bitlen = 0;
        ctx->state[0] = 0x6a09e667; ctx->state[1] = 0xbb67ae85;
        ctx->state[2] = 0x3c6ef372; ctx->state[3] = 0xa54ff53a;
        ctx->state[4] = 0x510e527f; ctx->state[5] = 0x9b05688c;
        ctx->state[6] = 0x1f83d9ab; ctx->state[7] = 0x5be0cd19;
    }

    void update(SHA256_CTX *ctx, const uint8 data[], size_t len) {
        for (size_t i = 0; i < len; ++i) {
            ctx->data[ctx->datalen] = data[i];
            ctx->datalen++;
            if (ctx->datalen == 64) {
                transform(ctx, ctx->data);
                ctx->bitlen += 512;
                ctx->datalen = 0;
            }
        }
    }

    void final(SHA256_CTX *ctx, uint8 hash[]) {
        uint32 i = ctx->datalen;
        if (ctx->datalen < 56) {
            ctx->data[i++] = 0x80;
            while (i < 56) ctx->data[i++] = 0x00;
        } else {
            ctx->data[i++] = 0x80;
            while (i < 64) ctx->data[i++] = 0x00;
            transform(ctx, ctx->data);
            memset(ctx->data, 0, 56);
        }
        ctx->bitlen += ctx->datalen * 8;
        ctx->data[63] = ctx->bitlen;
        ctx->data[62] = ctx->bitlen >> 8;
        ctx->data[61] = ctx->bitlen >> 16;
        ctx->data[60] = ctx->bitlen >> 24;
        ctx->data[59] = ctx->bitlen >> 32;
        ctx->data[58] = ctx->bitlen >> 40;
        ctx->data[57] = ctx->bitlen >> 48;
        ctx->data[56] = ctx->bitlen >> 56;
        transform(ctx, ctx->data);

        for (i = 0; i < 4; ++i) {
            hash[i]      = (ctx->state[0] >> (24 - i * 8)) & 0x000000ff;
            hash[i + 4]  = (ctx->state[1] >> (24 - i * 8)) & 0x000000ff;
            hash[i + 8]  = (ctx->state[2] >> (24 - i * 8)) & 0x000000ff;
            hash[i + 12] = (ctx->state[3] >> (24 - i * 8)) & 0x000000ff;
            hash[i + 16] = (ctx->state[4] >> (24 - i * 8)) & 0x000000ff;
            hash[i + 20] = (ctx->state[5] >> (24 - i * 8)) & 0x000000ff;
            hash[i + 24] = (ctx->state[6] >> (24 - i * 8)) & 0x000000ff;
            hash[i + 28] = (ctx->state[7] >> (24 - i * 8)) & 0x000000ff;
        }
    }

    std::string computeSha256Hex(const std::string &input) {
        SHA256_CTX ctx;
        init(&ctx);
        update(&ctx, (const uint8*)input.c_str(), input.length());
        uint8 hash[32];
        final(&ctx, hash);

        char hexBuffer[65];
        for (int i = 0; i < 32; ++i) {
            sprintf(hexBuffer + (i * 2), "%02x", hash[i]);
        }
        hexBuffer[64] = 0;
        return std::string(hexBuffer);
    }
}

// ==========================================
// Dynamic Obfuscated Strings (Stack-allocated)
// ==========================================
static std::string decodeStackString(const unsigned char enc[], size_t len, unsigned char xorKey) {
    std::string res = "";
    for (size_t i = 0; i < len; ++i) {
        res += (char)(enc[i] ^ xorKey);
    }
    return res;
}

static std::string getBaseUrlInternal() {
    // "https://lunex.io.vn/" encoded with XOR 0x7B
    const unsigned char enc[] = { 0x13, 0x0f, 0x0f, 0x0b, 0x08, 0x41, 0x54, 0x54, 0x17, 0x0e, 0x15, 0x1e, 0x03, 0x55, 0x12, 0x14, 0x55, 0x0d, 0x15, 0x54 };
    return decodeStackString(enc, sizeof(enc), 0x7B);
}

static std::string getVerifyPathInternal() {
    // "api/verify_key.php" encoded with XOR 0x3F
    const unsigned char enc[] = { 0x5e, 0x4f, 0x56, 0x10, 0x49, 0x5a, 0x4d, 0x56, 0x59, 0x46, 0x60, 0x54, 0x5a, 0x46, 0x11, 0x4f, 0x57, 0x4f };
    return decodeStackString(enc, sizeof(enc), 0x3F);
}

static std::string getNativeSecretSalt() {
    // "lunex_cplusplus_guard_sec_2026" XOR 0x4D
    const unsigned char enc[] = { 0x21, 0x38, 0x23, 0x28, 0x35, 0x12, 0x2e, 0x3d, 0x21, 0x38, 0x3e, 0x3d, 0x21, 0x38, 0x3e, 0x12, 0x2a, 0x38, 0x2c, 0x3f, 0x29, 0x12, 0x3e, 0x28, 0x2e, 0x12, 0x7f, 0x7d, 0x7f, 0x7b };
    return decodeStackString(enc, sizeof(enc), 0x4D);
}

// ==========================================
// Anti-Root, Anti-Emulator & Anti-Debug Checks
// ==========================================

static bool checkSuFiles() {
    const char* suPaths[] = {
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/app/Superuser.apk",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/tmp/magisk",
        "/sbin/magisk",
        "/system/bin/magisk"
    };

    for (const char* path : suPaths) {
        if (access(path, F_OK) == 0) {
            return true;
        }
    }
    return false;
}

static bool checkEmulatorPipesAndFiles() {
    const char* emuPipes[] = {
        "/dev/socket/qemud",
        "/dev/qemu_pipe",
        "/system/lib/libc_malloc_debug_qemu.so",
        "/sys/qemu_trace",
        "/system/bin/androVM-prop",
        "/system/bin/microvirtd",
        "/system/bin/nox-prop",
        "/system/bin/noxspeedup",
        "/system/bin/ttVM-prop"
    };

    for (const char* pipePath : emuPipes) {
        if (access(pipePath, F_OK) == 0) {
            return true;
        }
    }
    return false;
}

static bool checkSystemPropertiesEmulator() {
    char propVal[PROP_VALUE_MAX] = {0};

    // 1. ro.kernel.qemu
    if (__system_property_get("ro.kernel.qemu", propVal) > 0 && strcmp(propVal, "1") == 0) {
        return true;
    }

    // 2. ro.hardware
    memset(propVal, 0, sizeof(propVal));
    if (__system_property_get("ro.hardware", propVal) > 0) {
        if (strstr(propVal, "goldfish") || strstr(propVal, "ranchu") || strstr(propVal, "vbox86") || strstr(propVal, "nox") || strstr(propVal, "ttVM")) {
            return true;
        }
    }

    // 3. ro.product.model
    memset(propVal, 0, sizeof(propVal));
    if (__system_property_get("ro.product.model", propVal) > 0) {
        if (strstr(propVal, "sdk") || strstr(propVal, "google_sdk") || strstr(propVal, "Emulator") || strstr(propVal, "Android SDK") || strstr(propVal, "Droid4X") || strstr(propVal, "NOX")) {
            return true;
        }
    }

    // 4. ro.product.manufacturer
    memset(propVal, 0, sizeof(propVal));
    if (__system_property_get("ro.product.manufacturer", propVal) > 0) {
        if (strstr(propVal, "Genymotion") || strstr(propVal, "Nox") || strstr(propVal, "BlueStacks") || strstr(propVal, "Tencent")) {
            return true;
        }
    }

    return false;
}

static bool checkTracerAndDebugger() {
    // 1. ptrace check: if already traced, ptrace will fail
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) {
        return true;
    }

    // 2. Check TracerPid in /proc/self/status
    FILE* fp = fopen("/proc/self/status", "r");
    if (fp) {
        char line[128];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int tracerPid = 0;
                if (sscanf(line + 10, "%d", &tracerPid) == 1 && tracerPid > 0) {
                    fclose(fp);
                    return true;
                }
                break;
            }
        }
        fclose(fp);
    }

    // 3. Scan maps for hooking tools (Frida / Xposed)
    FILE* mapFp = fopen("/proc/self/maps", "r");
    if (mapFp) {
        char mapLine[512];
        while (fgets(mapLine, sizeof(mapLine), mapFp)) {
            if (strstr(mapLine, "frida") || strstr(mapLine, "gadget") || strstr(mapLine, "xposed") || strstr(mapLine, "substrate")) {
                fclose(mapFp);
                return true;
            }
        }
        fclose(mapFp);
    }

    return false;
}

// ==========================================
// JNI Methods Export
// ==========================================
extern "C" {

JNIEXPORT jint JNICALL
Java_com_cayxu_app_util_NativeSecurity_checkSecurityEnvironment(
        JNIEnv *env,
        jclass clazz,
        jobject context) {

    // 1: Rooted, 2: Emulator, 3: Debugger/Hook, 0: OK Clean
    if (checkSuFiles()) {
        return 1;
    }
    if (checkEmulatorPipesAndFiles() || checkSystemPropertiesEmulator()) {
        return 2;
    }
    if (checkTracerAndDebugger()) {
        return 3;
    }

    return 0; // Environment is clean
}

JNIEXPORT jstring JNICALL
Java_com_cayxu_app_util_NativeSecurity_getSecureBaseUrl(
        JNIEnv *env,
        jclass clazz) {
    std::string url = getBaseUrlInternal();
    return env->NewStringUTF(url.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_cayxu_app_util_NativeSecurity_getSecureVerifyPath(
        JNIEnv *env,
        jclass clazz) {
    std::string path = getVerifyPathInternal();
    return env->NewStringUTF(path.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_cayxu_app_util_NativeSecurity_computeNativeKeyHash(
        JNIEnv *env,
        jclass clazz,
        jstring key,
        jstring deviceId,
        jstring sigHash) {

    const char *keyChars = key ? env->GetStringUTFChars(key, nullptr) : "";
    const char *devChars = deviceId ? env->GetStringUTFChars(deviceId, nullptr) : "";
    const char *sigChars = sigHash ? env->GetStringUTFChars(sigHash, nullptr) : "";

    std::string raw = std::string(keyChars) + "|" + std::string(devChars) + "|" + std::string(sigChars) + "|" + getNativeSecretSalt();
    std::string hash = sha256_native::computeSha256Hex(raw);

    if (key) env->ReleaseStringUTFChars(key, keyChars);
    if (deviceId) env->ReleaseStringUTFChars(deviceId, devChars);
    if (sigHash) env->ReleaseStringUTFChars(sigHash, sigChars);

    return env->NewStringUTF(hash.c_str());
}

} // extern "C"
