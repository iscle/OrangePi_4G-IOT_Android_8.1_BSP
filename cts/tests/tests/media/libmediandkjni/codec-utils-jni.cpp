/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Original code copied from NDK Native-media sample code */

//#define LOG_NDEBUG 0
#define TAG "CodecUtilsJNI"
#include <log/log.h>

#include <stdint.h>
#include <sys/types.h>
#include <jni.h>

#include <ScopedLocalRef.h>
#include <JNIHelp.h>

#include <math.h>

#include "md5_utils.h"

typedef ssize_t offs_t;

struct NativeImage {
    struct crop {
        int left;
        int top;
        int right;
        int bottom;
    } crop;
    struct plane {
        const uint8_t *buffer;
        size_t size;
        ssize_t colInc;
        ssize_t rowInc;
        offs_t cropOffs;
        size_t cropWidth;
        size_t cropHeight;
    } plane[3];
    int width;
    int height;
    int format;
    long timestamp;
    size_t numPlanes;
};

struct ChecksumAlg {
    virtual void init() = 0;
    virtual void update(uint8_t c) = 0;
    virtual uint32_t checksum() = 0;
    virtual size_t length() = 0;
protected:
    virtual ~ChecksumAlg() {}
};

struct Adler32 : ChecksumAlg {
    Adler32() {
        init();
    }
    void init() {
        a = 1;
        len = b = 0;
    }
    void update(uint8_t c) {
        a += c;
        b += a;
        ++len;
    }
    uint32_t checksum() {
        return (a % 65521) + ((b % 65521) << 16);
    }
    size_t length() {
        return len;
    }
private:
    uint32_t a, b;
    size_t len;
};

static struct ImageFieldsAndMethods {
    // android.graphics.ImageFormat
    int YUV_420_888;
    // android.media.Image
    jmethodID methodWidth;
    jmethodID methodHeight;
    jmethodID methodFormat;
    jmethodID methodTimestamp;
    jmethodID methodPlanes;
    jmethodID methodCrop;
    // android.media.Image.Plane
    jmethodID methodBuffer;
    jmethodID methodPixelStride;
    jmethodID methodRowStride;
    // android.graphics.Rect
    jfieldID fieldLeft;
    jfieldID fieldTop;
    jfieldID fieldRight;
    jfieldID fieldBottom;
} gFields;
static bool gFieldsInitialized = false;

void initializeGlobalFields(JNIEnv *env) {
    if (gFieldsInitialized) {
        return;
    }
    {   // ImageFormat
        jclass imageFormatClazz = env->FindClass("android/graphics/ImageFormat");
        const jfieldID fieldYUV420888 = env->GetStaticFieldID(imageFormatClazz, "YUV_420_888", "I");
        gFields.YUV_420_888 = env->GetStaticIntField(imageFormatClazz, fieldYUV420888);
        env->DeleteLocalRef(imageFormatClazz);
        imageFormatClazz = NULL;
    }

    {   // Image
        jclass imageClazz = env->FindClass("android/media/cts/CodecImage");
        gFields.methodWidth  = env->GetMethodID(imageClazz, "getWidth", "()I");
        gFields.methodHeight = env->GetMethodID(imageClazz, "getHeight", "()I");
        gFields.methodFormat = env->GetMethodID(imageClazz, "getFormat", "()I");
        gFields.methodTimestamp = env->GetMethodID(imageClazz, "getTimestamp", "()J");
        gFields.methodPlanes = env->GetMethodID(
                imageClazz, "getPlanes", "()[Landroid/media/cts/CodecImage$Plane;");
        gFields.methodCrop   = env->GetMethodID(
                imageClazz, "getCropRect", "()Landroid/graphics/Rect;");
        env->DeleteLocalRef(imageClazz);
        imageClazz = NULL;
    }

    {   // Image.Plane
        jclass planeClazz = env->FindClass("android/media/cts/CodecImage$Plane");
        gFields.methodBuffer = env->GetMethodID(planeClazz, "getBuffer", "()Ljava/nio/ByteBuffer;");
        gFields.methodPixelStride = env->GetMethodID(planeClazz, "getPixelStride", "()I");
        gFields.methodRowStride = env->GetMethodID(planeClazz, "getRowStride", "()I");
        env->DeleteLocalRef(planeClazz);
        planeClazz = NULL;
    }

    {   // Rect
        jclass rectClazz = env->FindClass("android/graphics/Rect");
        gFields.fieldLeft   = env->GetFieldID(rectClazz, "left", "I");
        gFields.fieldTop    = env->GetFieldID(rectClazz, "top", "I");
        gFields.fieldRight  = env->GetFieldID(rectClazz, "right", "I");
        gFields.fieldBottom = env->GetFieldID(rectClazz, "bottom", "I");
        env->DeleteLocalRef(rectClazz);
        rectClazz = NULL;
    }
    gFieldsInitialized = true;
}

NativeImage *getNativeImage(JNIEnv *env, jobject image, jobject area = NULL) {
    if (image == NULL) {
        jniThrowNullPointerException(env, "image is null");
        return NULL;
    }

    initializeGlobalFields(env);

    NativeImage *img = new NativeImage;
    img->format = env->CallIntMethod(image, gFields.methodFormat);
    img->width  = env->CallIntMethod(image, gFields.methodWidth);
    img->height = env->CallIntMethod(image, gFields.methodHeight);
    img->timestamp = env->CallLongMethod(image, gFields.methodTimestamp);

    jobject cropRect = NULL;
    if (area == NULL) {
        cropRect = env->CallObjectMethod(image, gFields.methodCrop);
        area = cropRect;
    }

    img->crop.left   = env->GetIntField(area, gFields.fieldLeft);
    img->crop.top    = env->GetIntField(area, gFields.fieldTop);
    img->crop.right  = env->GetIntField(area, gFields.fieldRight);
    img->crop.bottom = env->GetIntField(area, gFields.fieldBottom);
    if (img->crop.right == 0 && img->crop.bottom == 0) {
        img->crop.right  = img->width;
        img->crop.bottom = img->height;
    }

    if (cropRect != NULL) {
        env->DeleteLocalRef(cropRect);
        cropRect = NULL;
    }

    if (img->format != gFields.YUV_420_888) {
        jniThrowException(
                env, "java/lang/UnsupportedOperationException",
                "only support YUV_420_888 images");
        delete img;
        img = NULL;
        return NULL;
    }
    img->numPlanes = 3;

    ScopedLocalRef<jobjectArray> planesArray(
            env, (jobjectArray)env->CallObjectMethod(image, gFields.methodPlanes));
    int xDecim = 0;
    int yDecim = 0;
    for (size_t ix = 0; ix < img->numPlanes; ++ix) {
        ScopedLocalRef<jobject> plane(
                env, env->GetObjectArrayElement(planesArray.get(), (jsize)ix));
        img->plane[ix].colInc = env->CallIntMethod(plane.get(), gFields.methodPixelStride);
        img->plane[ix].rowInc = env->CallIntMethod(plane.get(), gFields.methodRowStride);
        ScopedLocalRef<jobject> buffer(
                env, env->CallObjectMethod(plane.get(), gFields.methodBuffer));

        img->plane[ix].buffer = (const uint8_t *)env->GetDirectBufferAddress(buffer.get());
        img->plane[ix].size = env->GetDirectBufferCapacity(buffer.get());

        img->plane[ix].cropOffs =
            (img->crop.left >> xDecim) * img->plane[ix].colInc
                    + (img->crop.top >> yDecim) * img->plane[ix].rowInc;
        img->plane[ix].cropHeight =
            ((img->crop.bottom + (1 << yDecim) - 1) >> yDecim) - (img->crop.top >> yDecim);
        img->plane[ix].cropWidth =
            ((img->crop.right + (1 << xDecim) - 1) >> xDecim) - (img->crop.left >> xDecim);

        // sanity check on increments
        ssize_t widthOffs =
            (((img->width + (1 << xDecim) - 1) >> xDecim) - 1) * img->plane[ix].colInc;
        ssize_t heightOffs =
            (((img->height + (1 << yDecim) - 1) >> yDecim) - 1) * img->plane[ix].rowInc;
        if (widthOffs < 0 || heightOffs < 0
                || widthOffs + heightOffs >= (ssize_t)img->plane[ix].size) {
            jniThrowException(
                    env, "java/lang/IndexOutOfBoundsException", "plane exceeds bytearray");
            delete img;
            img = NULL;
            return NULL;
        }
        xDecim = yDecim = 1;
    }
    return img;
}

extern "C" jint Java_android_media_cts_CodecUtils_getImageChecksumAlder32(JNIEnv *env,
        jclass /*clazz*/, jobject image)
{
    NativeImage *img = getNativeImage(env, image);
    if (img == NULL) {
        return 0;
    }

    Adler32 adler;
    for (size_t ix = 0; ix < img->numPlanes; ++ix) {
        const uint8_t *row = img->plane[ix].buffer + img->plane[ix].cropOffs;
        for (size_t y = img->plane[ix].cropHeight; y > 0; --y) {
            const uint8_t *col = row;
            ssize_t colInc = img->plane[ix].colInc;
            for (size_t x = img->plane[ix].cropWidth; x > 0; --x) {
                adler.update(*col);
                col += colInc;
            }
            row += img->plane[ix].rowInc;
        }
    }
    ALOGV("adler %zu/%u", adler.length(), adler.checksum());
    return adler.checksum();
}

extern "C" jstring Java_android_media_cts_CodecUtils_getImageChecksumMD5(JNIEnv *env,
        jclass /*clazz*/, jobject image)
{
    NativeImage *img = getNativeImage(env, image);
    if (img == NULL) {
        return 0;
    }

    MD5Context md5;
    char res[33];
    MD5Init(&md5);

    for (size_t ix = 0; ix < img->numPlanes; ++ix) {
        const uint8_t *row = img->plane[ix].buffer + img->plane[ix].cropOffs;
        for (size_t y = img->plane[ix].cropHeight; y > 0; --y) {
            const uint8_t *col = row;
            ssize_t colInc = img->plane[ix].colInc;
            for (size_t x = img->plane[ix].cropWidth; x > 0; --x) {
                MD5Update(&md5, col, 1);
                col += colInc;
            }
            row += img->plane[ix].rowInc;
        }
    }

    static const char hex[16] = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    };
    uint8_t tmp[16];

    MD5Final(tmp, &md5);
    for (int i = 0; i < 16; i++) {
        res[i * 2 + 0] = hex[tmp[i] >> 4];
        res[i * 2 + 1] = hex[tmp[i] & 0xf];
    }
    res[32] = 0;

    return env->NewStringUTF(res);
}

/* tiled copy that loops around source image boundary */
extern "C" void Java_android_media_cts_CodecUtils_copyFlexYUVImage(JNIEnv *env,
        jclass /*clazz*/, jobject target, jobject source)
{
    NativeImage *tgt = getNativeImage(env, target);
    NativeImage *src = getNativeImage(env, source);
    if (tgt != NULL && src != NULL) {
        ALOGV("copyFlexYUVImage %dx%d (%d,%d..%d,%d) (%zux%zu) %+zd%+zd %+zd%+zd %+zd%+zd <= "
                "%dx%d (%d, %d..%d, %d) (%zux%zu) %+zd%+zd %+zd%+zd %+zd%+zd",
                tgt->width, tgt->height,
                tgt->crop.left, tgt->crop.top, tgt->crop.right, tgt->crop.bottom,
                tgt->plane[0].cropWidth, tgt->plane[0].cropHeight,
                tgt->plane[0].rowInc, tgt->plane[0].colInc,
                tgt->plane[1].rowInc, tgt->plane[1].colInc,
                tgt->plane[2].rowInc, tgt->plane[2].colInc,
                src->width, src->height,
                src->crop.left, src->crop.top, src->crop.right, src->crop.bottom,
                src->plane[0].cropWidth, src->plane[0].cropHeight,
                src->plane[0].rowInc, src->plane[0].colInc,
                src->plane[1].rowInc, src->plane[1].colInc,
                src->plane[2].rowInc, src->plane[2].colInc);
        for (size_t ix = 0; ix < tgt->numPlanes; ++ix) {
            uint8_t *row = const_cast<uint8_t *>(tgt->plane[ix].buffer) + tgt->plane[ix].cropOffs;
            for (size_t y = 0; y < tgt->plane[ix].cropHeight; ++y) {
                uint8_t *col = row;
                ssize_t colInc = tgt->plane[ix].colInc;
                const uint8_t *srcRow = (src->plane[ix].buffer + src->plane[ix].cropOffs
                        + src->plane[ix].rowInc * (y % src->plane[ix].cropHeight));
                for (size_t x = 0; x < tgt->plane[ix].cropWidth; ++x) {
                    *col = srcRow[src->plane[ix].colInc * (x % src->plane[ix].cropWidth)];
                    col += colInc;
                }
                row += tgt->plane[ix].rowInc;
            }
        }
    }
}

extern "C" void Java_android_media_cts_CodecUtils_fillImageRectWithYUV(JNIEnv *env,
        jclass /*clazz*/, jobject image, jobject area, jint y, jint u, jint v)
{
    NativeImage *img = getNativeImage(env, image, area);
    if (img == NULL) {
        return;
    }

    for (size_t ix = 0; ix < img->numPlanes; ++ix) {
        const uint8_t *row = img->plane[ix].buffer + img->plane[ix].cropOffs;
        uint8_t val = ix == 0 ? y : ix == 1 ? u : v;
        for (size_t y = img->plane[ix].cropHeight; y > 0; --y) {
            uint8_t *col = (uint8_t *)row;
            ssize_t colInc = img->plane[ix].colInc;
            for (size_t x = img->plane[ix].cropWidth; x > 0; --x) {
                *col = val;
                col += colInc;
            }
            row += img->plane[ix].rowInc;
        }
    }
}

void getRawStats(NativeImage *img, jlong rawStats[10])
{
    // this works best if crop area is even

    uint64_t sum_x[3]  = { 0, 0, 0 }; // Y, U, V
    uint64_t sum_xx[3] = { 0, 0, 0 }; // YY, UU, VV
    uint64_t sum_xy[3] = { 0, 0, 0 }; // YU, YV, UV

    const uint8_t *yrow = img->plane[0].buffer + img->plane[0].cropOffs;
    const uint8_t *urow = img->plane[1].buffer + img->plane[1].cropOffs;
    const uint8_t *vrow = img->plane[2].buffer + img->plane[2].cropOffs;

    ssize_t ycolInc = img->plane[0].colInc;
    ssize_t ucolInc = img->plane[1].colInc;
    ssize_t vcolInc = img->plane[2].colInc;

    ssize_t yrowInc = img->plane[0].rowInc;
    ssize_t urowInc = img->plane[1].rowInc;
    ssize_t vrowInc = img->plane[2].rowInc;

    size_t rightOdd = img->crop.right & 1;
    size_t bottomOdd = img->crop.bottom & 1;

    for (size_t y = img->plane[0].cropHeight; y; --y) {
        uint8_t *ycol = (uint8_t *)yrow;
        uint8_t *ucol = (uint8_t *)urow;
        uint8_t *vcol = (uint8_t *)vrow;

        for (size_t x = img->plane[0].cropWidth; x; --x) {
            uint64_t Y = *ycol;
            uint64_t U = *ucol;
            uint64_t V = *vcol;

            sum_x[0] += Y;
            sum_x[1] += U;
            sum_x[2] += V;
            sum_xx[0] += Y * Y;
            sum_xx[1] += U * U;
            sum_xx[2] += V * V;
            sum_xy[0] += Y * U;
            sum_xy[1] += Y * V;
            sum_xy[2] += U * V;

            ycol += ycolInc;
            if (rightOdd ^ (x & 1)) {
                ucol += ucolInc;
                vcol += vcolInc;
            }
        }

        yrow += yrowInc;
        if (bottomOdd ^ (y & 1)) {
            urow += urowInc;
            vrow += vrowInc;
        }
    }

    rawStats[0] = img->plane[0].cropWidth * (uint64_t)img->plane[0].cropHeight;
    for (size_t i = 0; i < 3; i++) {
        rawStats[i + 1] = sum_x[i];
        rawStats[i + 4] = sum_xx[i];
        rawStats[i + 7] = sum_xy[i];
    }
}

bool Raw2YUVStats(jlong rawStats[10], jfloat stats[9]) {
    int64_t sum_x[3], sum_xx[3]; // Y, U, V
    int64_t sum_xy[3];           // YU, YV, UV

    int64_t num = rawStats[0];   // #Y,U,V
    for (size_t i = 0; i < 3; i++) {
        sum_x[i] = rawStats[i + 1];
        sum_xx[i] = rawStats[i + 4];
        sum_xy[i] = rawStats[i + 7];
    }

    if (num > 0) {
        stats[0] = sum_x[0] / (float)num;  // y average
        stats[1] = sum_x[1] / (float)num;  // u average
        stats[2] = sum_x[2] / (float)num;  // v average

        // 60 bits for 4Mpixel image
        // adding 1 to avoid degenerate case when deviation is 0
        stats[3] = sqrtf((sum_xx[0] + 1) * num - sum_x[0] * sum_x[0]) / num; // y stdev
        stats[4] = sqrtf((sum_xx[1] + 1) * num - sum_x[1] * sum_x[1]) / num; // u stdev
        stats[5] = sqrtf((sum_xx[2] + 1) * num - sum_x[2] * sum_x[2]) / num; // v stdev

        // yu covar
        stats[6] = (float)(sum_xy[0] + 1 - sum_x[0] * sum_x[1] / num) / num / stats[3] / stats[4];
        // yv covar
        stats[7] = (float)(sum_xy[1] + 1 - sum_x[0] * sum_x[2] / num) / num / stats[3] / stats[5];
        // uv covar
        stats[8] = (float)(sum_xy[2] + 1 - sum_x[1] * sum_x[2] / num) / num / stats[4] / stats[5];
        return true;
    } else {
        return false;
    }
}

extern "C" jobject Java_android_media_cts_CodecUtils_getRawStats(JNIEnv *env,
        jclass /*clazz*/, jobject image, jobject area)
{
    NativeImage *img = getNativeImage(env, image, area);
    if (img == NULL) {
        return NULL;
    }

    jlong rawStats[10];
    getRawStats(img, rawStats);
    jlongArray jstats = env->NewLongArray(10);
    if (jstats != NULL) {
        env->SetLongArrayRegion(jstats, 0, 10, rawStats);
    }
    return jstats;
}

extern "C" jobject Java_android_media_cts_CodecUtils_getYUVStats(JNIEnv *env,
        jclass /*clazz*/, jobject image, jobject area)
{
    NativeImage *img = getNativeImage(env, image, area);
    if (img == NULL) {
        return NULL;
    }

    jlong rawStats[10];
    getRawStats(img, rawStats);
    jfloat stats[9];
    jfloatArray jstats = NULL;
    if (Raw2YUVStats(rawStats, stats)) {
        jstats = env->NewFloatArray(9);
        if (jstats != NULL) {
            env->SetFloatArrayRegion(jstats, 0, 9, stats);
        }
    } else {
        jniThrowRuntimeException(env, "empty area");
    }

    return jstats;
}

extern "C" jobject Java_android_media_cts_CodecUtils_Raw2YUVStats(JNIEnv *env,
        jclass /*clazz*/, jobject jrawStats)
{
    jfloatArray jstats = NULL;
    jlong rawStats[10];
    env->GetLongArrayRegion((jlongArray)jrawStats, 0, 10, rawStats);
    if (!env->ExceptionCheck()) {
        jfloat stats[9];
        if (Raw2YUVStats(rawStats, stats)) {
            jstats = env->NewFloatArray(9);
            if (jstats != NULL) {
                env->SetFloatArrayRegion(jstats, 0, 9, stats);
            }
        } else {
            jniThrowRuntimeException(env, "no raw statistics");
        }
    }
    return jstats;
}
