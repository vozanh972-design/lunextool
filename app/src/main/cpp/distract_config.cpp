#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdio>
#include <stdint.h>

// ============================================================================
// Module: distract-config (libdistract-config.so) - OLLVM Hardened
// Control Flow Flattening (CFF) + Instruction Substitution (MBA) + XOR String Encryption
// ============================================================================

namespace {
    typedef unsigned int uint32;
    typedef unsigned char uint8;

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

    const uint8_t enc_verifyEp[18] = { 0x3D, 0x13, 0x03, 0x5E, 0x0E, 0x1A, 0xF4, 0xE4, 0xF2, 0xE2, 0xFD, 0xC2, 0xD5, 0xCE, 0x90, 0xB5, 0xA4, 0xA3 };
    const uint8_t enc_distractSalt[36] = { 0x38, 0x0A, 0x19, 0x05, 0x0A, 0x1E, 0xE5, 0xF9, 0xCB, 0xE8, 0xC3, 0xC5, 0xC4, 0xE8, 0xD8, 0xB7, 0xAD, 0xB4, 0x85, 0xD3, 0xD8, 0xDD, 0xC0, 0xA2, 0x6F, 0x6E, 0x6B, 0x46, 0x56, 0x1F, 0x0F, 0x75, 0x1F, 0x67, 0x6F, 0x0F };

    static inline std::string getNativeEndpoint() {
        return decryptOllvmString(enc_verifyEp, 18, 0x5C, 7);
    }

    static inline std::string getDistractSalt() {
        return decryptOllvmString(enc_distractSalt, 36, 0x5C, 7);
    }

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

    static void sha256_transform(SHA256_CTX *ctx, const uint8 data[]) {
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

    static void sha256_init(SHA256_CTX *ctx) {
        ctx->datalen = 0;
        ctx->bitlen = 0;
        ctx->state[0] = 0x6a09e667; ctx->state[1] = 0xbb67ae85;
        ctx->state[2] = 0x3c6ef372; ctx->state[3] = 0xa54ff53a;
        ctx->state[4] = 0x510e527f; ctx->state[5] = 0x9b05688c;
        ctx->state[6] = 0x1f83d9ab; ctx->state[7] = 0x5be0cd19;
    }

    static void sha256_update(SHA256_CTX *ctx, const uint8 data[], size_t len) {
        for (size_t i = 0; i < len; ++i) {
            ctx->data[ctx->datalen] = data[i];
            ctx->datalen++;
            if (ctx->datalen == 64) {
                sha256_transform(ctx, ctx->data);
                ctx->bitlen += 512;
                ctx->datalen = 0;
            }
        }
    }

    static void sha256_final(SHA256_CTX *ctx, uint8 hash[]) {
        uint32 i = ctx->datalen;
        if (ctx->datalen < 56) {
            ctx->data[i++] = 0x80;
            while (i < 56) ctx->data[i++] = 0x00;
        } else {
            ctx->data[i++] = 0x80;
            while (i < 64) ctx->data[i++] = 0x00;
            sha256_transform(ctx, ctx->data);
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
        sha256_transform(ctx, ctx->data);

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

    static std::string calculateSha256Hex(const std::string& input) {
        SHA256_CTX ctx;
        sha256_init(&ctx);
        sha256_update(&ctx, (const uint8*)input.c_str(), input.length());
        uint8 hash[32];
        sha256_final(&ctx, hash);

        char hexBuffer[65];
        for (int i = 0; i < 32; ++i) {
            sprintf(hexBuffer + (i * 2), "%02x", hash[i]);
        }
        hexBuffer[64] = 0;
        return std::string(hexBuffer);
    }
}

static jstring native_distract_ep(JNIEnv *env, jclass clazz) {
    std::string path = getNativeEndpoint();
    return env->NewStringUTF(path.c_str());
}

static jstring native_distract_sig(JNIEnv *env, jclass clazz, jstring seed, jstring bufferId, jstring frameStamp) {
    const char *k = seed ? env->GetStringUTFChars(seed, nullptr) : "";
    const char *d = bufferId ? env->GetStringUTFChars(bufferId, nullptr) : "";
    const char *s = frameStamp ? env->GetStringUTFChars(frameStamp, nullptr) : "";

    std::string raw = std::string(k) + "|" + std::string(d) + "|" + std::string(s) + "|" + getDistractSalt();
    std::string hash = calculateSha256Hex(raw);

    if (seed) env->ReleaseStringUTFChars(seed, k);
    if (bufferId) env->ReleaseStringUTFChars(bufferId, d);
    if (frameStamp) env->ReleaseStringUTFChars(frameStamp, s);

    return env->NewStringUTF(hash.c_str());
}

static const JNINativeMethod gDistractMethods[] = {
    { (char*)"_distractEp", (char*)"()Ljava/lang/String;", (void*)native_distract_ep },
    { (char*)"_distractSig", (char*)"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", (void*)native_distract_sig }
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/cayxu/app/util/NativeSecurity");
    if (!clazz) return JNI_ERR;

    if (env->RegisterNatives(clazz, gDistractMethods, sizeof(gDistractMethods) / sizeof(gDistractMethods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}