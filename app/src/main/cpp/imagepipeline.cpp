#include <jni.h>
#include <vector>
#include <algorithm>

// ============================================================================
// Module: imagepipeline (libimagepipeline.so)
// ============================================================================

namespace {
    static void applyFastBlur(int *pix, int w, int h, int radius) {
        if (radius < 1) return;
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        std::vector<int> r(wh), g(wh), b(wh);
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        std::vector<int> vmin(std::max(w, h));

        yw = yi = 0;

        for (y = 0; y < h; y++) {
            rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + std::min(wm, std::max(i, 0))];
                rsum += (p >> 16) & 0xff;
                gsum += (p >> 8) & 0xff;
                bsum += p & 0xff;
            }
            for (x = 0; x < w; x++) {
                r[yi] = rsum / div;
                g[yi] = gsum / div;
                b[yi] = bsum / div;

                if (y == 0) {
                    vmin[x] = std::min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];
                int p2 = pix[yw + std::max(x - radius, 0)];

                rsum += ((p >> 16) & 0xff) - ((p2 >> 16) & 0xff);
                gsum += ((p >> 8) & 0xff) - ((p2 >> 8) & 0xff);
                bsum += (p & 0xff) - (p2 & 0xff);
                yi++;
            }
            yw += w;
        }

        for (x = 0; x < w; x++) {
            rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = std::max(0, yp) + x;
                rsum += r[yi];
                gsum += g[yi];
                bsum += b[yi];
                yp += w;
            }
            yi = x;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000) | ((rsum / div) << 16) | ((gsum / div) << 8) | (bsum / div);
                if (x == 0) {
                    vmin[y] = std::min(y + radius + 1, hm) * w;
                }
                p = x + vmin[y];
                int p2 = x + std::max(y - radius, 0) * w;

                rsum += r[p] - r[p2];
                gsum += g[p] - g[p2];
                bsum += b[p] - b[p2];
                yi += w;
            }
        }
    }

    static void adjustContrast(int *pix, int count, float contrast) {
        float factor = (259.0f * (contrast + 255.0f)) / (255.0f * (259.0f - contrast));
        for (int i = 0; i < count; i++) {
            int p = pix[i];
            int a = (p >> 24) & 0xff;
            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;

            r = std::min(255, std::max(0, (int)(factor * (r - 128) + 128)));
            g = std::min(255, std::max(0, (int)(factor * (g - 128) + 128)));
            b = std::min(255, std::max(0, (int)(factor * (b - 128) + 128)));

            pix[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}

static jboolean native_img_blr(JNIEnv *env, jclass clazz, jintArray pixelsArray, jint width, jint height, jint radius) {
    if (!pixelsArray || width <= 0 || height <= 0 || radius <= 0) return JNI_FALSE;
    jint *pixels = env->GetIntArrayElements(pixelsArray, nullptr);
    if (!pixels) return JNI_FALSE;

    applyFastBlur(reinterpret_cast<int*>(pixels), width, height, radius);

    env->ReleaseIntArrayElements(pixelsArray, pixels, 0);
    return JNI_TRUE;
}

static jboolean native_img_cts(JNIEnv *env, jclass clazz, jintArray pixelsArray, jint width, jint height, jfloat contrast) {
    if (!pixelsArray || width <= 0 || height <= 0) return JNI_FALSE;
    jint *pixels = env->GetIntArrayElements(pixelsArray, nullptr);
    if (!pixels) return JNI_FALSE;

    adjustContrast(reinterpret_cast<int*>(pixels), width * height, contrast);

    env->ReleaseIntArrayElements(pixelsArray, pixels, 0);
    return JNI_TRUE;
}

static const JNINativeMethod gImgMethods[] = {
    { (char*)"_imgBlr", (char*)"([IIII)Z", (void*)native_img_blr },
    { (char*)"_imgCts", (char*)"([IIIF)Z", (void*)native_img_cts }
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/cayxu/app/util/NativeSecurity");
    if (!clazz) return JNI_ERR;

    if (env->RegisterNatives(clazz, gImgMethods, sizeof(gImgMethods) / sizeof(gImgMethods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
