/*
 * Copyright (C) 2014 The Android Open Source Project
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
#define TAG "NativeMedia"
#include <log/log.h>

#include <assert.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <semaphore.h>

#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>

#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaCrypto.h"
#include "media/NdkMediaFormat.h"
#include "media/NdkMediaMuxer.h"

template <class T>
class simplevector {
    T *storage;
    int capacity;
    int numfilled;
public:
    simplevector() {
        capacity = 16;
        numfilled = 0;
        storage = new T[capacity];
    }
    ~simplevector() {
        delete[] storage;
    }

    void add(T item) {
        if (numfilled == capacity) {
            T *old = storage;
            capacity *= 2;
            storage = new T[capacity];
            for (int i = 0; i < numfilled; i++) {
                storage[i] = old[i];
            }
            delete[] old;
        }
        storage[numfilled] = item;
        numfilled++;
    }

    int size() {
        return numfilled;
    }

    T* data() {
        return storage;
    }
};



jobject testExtractor(AMediaExtractor *ex, JNIEnv *env) {

    simplevector<int> sizes;
    int numtracks = AMediaExtractor_getTrackCount(ex);
    sizes.add(numtracks);
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
        const char *s = AMediaFormat_toString(format);
        ALOGI("track %d format: %s", i, s);
        const char *mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            return NULL;
        } else if (!strncmp(mime, "audio/", 6)) {
            sizes.add(0);
            int32_t val32;
            int64_t val64;
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &val32);
            sizes.add(val32);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &val32);
            sizes.add(val32);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &val64);
            sizes.add(val64);
        } else if (!strncmp(mime, "video/", 6)) {
            sizes.add(1);
            int32_t val32;
            int64_t val64;
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &val32);
            sizes.add(val32);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &val32);
            sizes.add(val32);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &val64);
            sizes.add(val64);
        } else {
            ALOGE("expected audio or video mime type, got %s", mime);
        }
        AMediaFormat_delete(format);
        AMediaExtractor_selectTrack(ex, i);
    }
    int bufsize = 1024*1024;
    uint8_t *buf = new uint8_t[bufsize];
    while(true) {
        int n = AMediaExtractor_readSampleData(ex, buf, bufsize);
        if (n < 0) {
            break;
        }
        sizes.add(n);
        sizes.add(AMediaExtractor_getSampleTrackIndex(ex));
        sizes.add(AMediaExtractor_getSampleFlags(ex));
        sizes.add(AMediaExtractor_getSampleTime(ex));
        AMediaExtractor_advance(ex);
    }

    // allocate java int array for result and return it
    int *data = sizes.data();
    int numsamples = sizes.size();
    jintArray ret = env->NewIntArray(numsamples);
    jboolean isCopy;
    jint *dst = env->GetIntArrayElements(ret, &isCopy);
    for (int i = 0; i < numsamples; ++i) {
        dst[i] = data[i];
    }
    env->ReleaseIntArrayElements(ret, dst, 0);

    delete[] buf;
    AMediaExtractor_delete(ex);
    return ret;
}


// get the sample sizes for the file
extern "C" jobject Java_android_media_cts_NativeDecoderTest_getSampleSizesNative(JNIEnv *env,
        jclass /*clazz*/, int fd, jlong offset, jlong size)
{
    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return NULL;
    }
    return testExtractor(ex, env);
}

// get the sample sizes for the path
extern "C" jobject Java_android_media_cts_NativeDecoderTest_getSampleSizesNativePath(JNIEnv *env,
        jclass /*clazz*/, jstring jpath)
{
    AMediaExtractor *ex = AMediaExtractor_new();

    const char *tmp = env->GetStringUTFChars(jpath, NULL);
    if (tmp == NULL) {  // Out of memory
        return NULL;
    }

    int err = AMediaExtractor_setDataSource(ex, tmp);

    env->ReleaseStringUTFChars(jpath, tmp);

    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return NULL;
    }
    return testExtractor(ex, env);
}

static int adler32(const uint8_t *input, int len) {

    int a = 1;
    int b = 0;
    for (int i = 0; i < len; i++) {
        a += input[i];
        b += a;
    }
    a = a % 65521;
    b = b % 65521;
    int ret = b * 65536 + a;
    ALOGV("adler %d/%d", len, ret);
    return ret;
}

static int checksum(const uint8_t *in, int len, AMediaFormat *format) {
    int width, stride, height;
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &width)) {
        width = len;
    }
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_STRIDE, &stride)) {
        stride = width;
    }
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &height)) {
        height = 1;
    }
    uint8_t *bb = new uint8_t[width * height];
    for (int i = 0; i < height; i++) {
        memcpy(bb + i * width, in + i * stride, width);
    }
    // bb is filled with data
    int sum = adler32(bb, width * height);
    delete[] bb;
    return sum;
}

extern "C" jobject Java_android_media_cts_NativeDecoderTest_getDecodedDataNative(JNIEnv *env,
        jclass /*clazz*/, int fd, jlong offset, jlong size) {
    ALOGV("getDecodedDataNative");

    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return NULL;
    }

    int numtracks = AMediaExtractor_getTrackCount(ex);

    AMediaCodec **codec = new AMediaCodec*[numtracks];
    AMediaFormat **format = new AMediaFormat*[numtracks];
    bool *sawInputEOS = new bool[numtracks];
    bool *sawOutputEOS = new bool[numtracks];
    simplevector<int> *sizes = new simplevector<int>[numtracks];

    ALOGV("input has %d tracks", numtracks);
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
        const char *s = AMediaFormat_toString(format);
        ALOGI("track %d format: %s", i, s);
        const char *mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            return NULL;
        } else if (!strncmp(mime, "audio/", 6) || !strncmp(mime, "video/", 6)) {
            codec[i] = AMediaCodec_createDecoderByType(mime);
            AMediaCodec_configure(codec[i], format, NULL /* surface */, NULL /* crypto */, 0);
            AMediaCodec_start(codec[i]);
            sawInputEOS[i] = false;
            sawOutputEOS[i] = false;
        } else {
            ALOGE("expected audio or video mime type, got %s", mime);
            return NULL;
        }
        AMediaFormat_delete(format);
        AMediaExtractor_selectTrack(ex, i);
    }
    int eosCount = 0;
    while(eosCount < numtracks) {
        int t = AMediaExtractor_getSampleTrackIndex(ex);
        if (t >=0) {
            ssize_t bufidx = AMediaCodec_dequeueInputBuffer(codec[t], 5000);
            ALOGV("track %d, input buffer %zd", t, bufidx);
            if (bufidx >= 0) {
                size_t bufsize;
                uint8_t *buf = AMediaCodec_getInputBuffer(codec[t], bufidx, &bufsize);
                int sampleSize = AMediaExtractor_readSampleData(ex, buf, bufsize);
                ALOGV("read %d", sampleSize);
                if (sampleSize < 0) {
                    sampleSize = 0;
                    sawInputEOS[t] = true;
                    ALOGV("EOS");
                    //break;
                }
                int64_t presentationTimeUs = AMediaExtractor_getSampleTime(ex);

                AMediaCodec_queueInputBuffer(codec[t], bufidx, 0, sampleSize, presentationTimeUs,
                        sawInputEOS[t] ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
                AMediaExtractor_advance(ex);
            }
        } else {
            ALOGV("@@@@ no more input samples");
            for (int tt = 0; tt < numtracks; tt++) {
                if (!sawInputEOS[tt]) {
                    // we ran out of samples without ever signaling EOS to the codec,
                    // so do that now
                    int bufidx = AMediaCodec_dequeueInputBuffer(codec[tt], 5000);
                    if (bufidx >= 0) {
                        AMediaCodec_queueInputBuffer(codec[tt], bufidx, 0, 0, 0,
                                AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS[tt] = true;
                    }
                }
            }
        }

        // check all codecs for available data
        AMediaCodecBufferInfo info;
        for (int tt = 0; tt < numtracks; tt++) {
            if (!sawOutputEOS[tt]) {
                int status = AMediaCodec_dequeueOutputBuffer(codec[tt], &info, 1);
                ALOGV("dequeueoutput on track %d: %d", tt, status);
                if (status >= 0) {
                    if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                        ALOGV("EOS on track %d", tt);
                        sawOutputEOS[tt] = true;
                        eosCount++;
                    }
                    ALOGV("got decoded buffer for track %d, size %d", tt, info.size);
                    if (info.size > 0) {
                        size_t bufsize;
                        uint8_t *buf = AMediaCodec_getOutputBuffer(codec[tt], status, &bufsize);
                        int adler = checksum(buf, info.size, format[tt]);
                        sizes[tt].add(adler);
                    }
                    AMediaCodec_releaseOutputBuffer(codec[tt], status, false);
                } else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                    ALOGV("output buffers changed for track %d", tt);
                } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                    format[tt] = AMediaCodec_getOutputFormat(codec[tt]);
                    ALOGV("format changed for track %d: %s", tt, AMediaFormat_toString(format[tt]));
                } else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                    ALOGV("no output buffer right now for track %d", tt);
                } else {
                    ALOGV("unexpected info code for track %d : %d", tt, status);
                }
            } else {
                ALOGV("already at EOS on track %d", tt);
            }
        }
    }
    ALOGV("decoding loop done");

    // allocate java int array for result and return it
    int numsamples = 0;
    for (int i = 0; i < numtracks; i++) {
        numsamples += sizes[i].size();
    }
    ALOGV("checksums: %d", numsamples);
    jintArray ret = env->NewIntArray(numsamples);
    jboolean isCopy;
    jint *org = env->GetIntArrayElements(ret, &isCopy);
    jint *dst = org;
    for (int i = 0; i < numtracks; i++) {
        int *data = sizes[i].data();
        int len = sizes[i].size();
        ALOGV("copying %d", len);
        for (int j = 0; j < len; j++) {
            *dst++ = data[j];
        }
    }
    env->ReleaseIntArrayElements(ret, org, 0);

    delete[] sizes;
    delete[] sawOutputEOS;
    delete[] sawInputEOS;
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat_delete(format[i]);
        AMediaCodec_stop(codec[i]);
        AMediaCodec_delete(codec[i]);
    }
    delete[] format;
    delete[] codec;
    AMediaExtractor_delete(ex);
    return ret;
}

extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testPlaybackNative(JNIEnv *env,
        jclass /*clazz*/, jobject surface, int fd, jlong offset, jlong size) {

    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    ALOGI("@@@@ native window: %p", window);

    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return false;
    }

    int numtracks = AMediaExtractor_getTrackCount(ex);

    AMediaCodec *codec = NULL;
    AMediaFormat *format = NULL;
    bool sawInputEOS = false;
    bool sawOutputEOS = false;

    ALOGV("input has %d tracks", numtracks);
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
        const char *s = AMediaFormat_toString(format);
        ALOGI("track %d format: %s", i, s);
        const char *mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            return false;
        } else if (!strncmp(mime, "video/", 6)) {
            codec = AMediaCodec_createDecoderByType(mime);
            AMediaCodec_configure(codec, format, window, NULL, 0);
            AMediaCodec_start(codec);
            AMediaExtractor_selectTrack(ex, i);
        }
        AMediaFormat_delete(format);
    }

    while (!sawOutputEOS) {
        ssize_t bufidx = AMediaCodec_dequeueInputBuffer(codec, 5000);
        ALOGV("input buffer %zd", bufidx);
        if (bufidx >= 0) {
            size_t bufsize;
            uint8_t *buf = AMediaCodec_getInputBuffer(codec, bufidx, &bufsize);
            int sampleSize = AMediaExtractor_readSampleData(ex, buf, bufsize);
            ALOGV("read %d", sampleSize);
            if (sampleSize < 0) {
                sampleSize = 0;
                sawInputEOS = true;
                ALOGV("EOS");
            }
            int64_t presentationTimeUs = AMediaExtractor_getSampleTime(ex);

            AMediaCodec_queueInputBuffer(codec, bufidx, 0, sampleSize, presentationTimeUs,
                    sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
            AMediaExtractor_advance(ex);
        }

        AMediaCodecBufferInfo info;
        int status = AMediaCodec_dequeueOutputBuffer(codec, &info, 1);
        ALOGV("dequeueoutput returned: %d", status);
        if (status >= 0) {
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                ALOGV("output EOS");
                sawOutputEOS = true;
            }
            ALOGV("got decoded buffer size %d", info.size);
            AMediaCodec_releaseOutputBuffer(codec, status, true);
            usleep(20000);
        } else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            ALOGV("output buffers changed");
        } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            format = AMediaCodec_getOutputFormat(codec);
            ALOGV("format changed to: %s", AMediaFormat_toString(format));
        } else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            ALOGV("no output buffer right now");
        } else {
            ALOGV("unexpected info code: %d", status);
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaExtractor_delete(ex);
    return true;
}

extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testMuxerNative(JNIEnv */*env*/,
        jclass /*clazz*/, int infd, jlong inoffset, jlong insize, int outfd, jboolean webm) {


    AMediaMuxer *muxer = AMediaMuxer_new(outfd,
            webm ? AMEDIAMUXER_OUTPUT_FORMAT_WEBM : AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);

    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, infd, inoffset, insize);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return false;
    }

    int numtracks = AMediaExtractor_getTrackCount(ex);
    ALOGI("input tracks: %d", numtracks);
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
        const char *s = AMediaFormat_toString(format);
        ALOGI("track %d format: %s", i, s);
        const char *mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            return false;
        } else if (!strncmp(mime, "audio/", 6) || !strncmp(mime, "video/", 6)) {
            ssize_t tidx = AMediaMuxer_addTrack(muxer, format);
            ALOGI("track %d -> %zd format %s", i, tidx, s);
            AMediaExtractor_selectTrack(ex, i);
        } else {
            ALOGE("expected audio or video mime type, got %s", mime);
            return false;
        }
        AMediaFormat_delete(format);
        AMediaExtractor_selectTrack(ex, i);
    }
    AMediaMuxer_start(muxer);

    int bufsize = 1024*1024;
    uint8_t *buf = new uint8_t[bufsize];
    AMediaCodecBufferInfo info;
    while(true) {
        int n = AMediaExtractor_readSampleData(ex, buf, bufsize);
        if (n < 0) {
            break;
        }
        info.offset = 0;
        info.size = n;
        info.presentationTimeUs = AMediaExtractor_getSampleTime(ex);
        info.flags = AMediaExtractor_getSampleFlags(ex);

        size_t idx = (size_t) AMediaExtractor_getSampleTrackIndex(ex);
        AMediaMuxer_writeSampleData(muxer, idx, buf, &info);

        AMediaExtractor_advance(ex);
    }

    AMediaExtractor_delete(ex);
    AMediaMuxer_stop(muxer);
    AMediaMuxer_delete(muxer);
    return true;

}

extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testFormatNative(JNIEnv * /*env*/,
        jclass /*clazz*/) {
    AMediaFormat* format = AMediaFormat_new();
    if (!format) {
        return false;
    }

    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, 8000);
    int32_t bitrate = 0;
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, &bitrate) || bitrate != 8000) {
        ALOGE("AMediaFormat_getInt32 fail: %d", bitrate);
        return false;
    }

    AMediaFormat_setInt64(format, AMEDIAFORMAT_KEY_DURATION, 123456789123456789ll);
    int64_t duration = 0;
    if (!AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &duration)
            || duration != 123456789123456789ll) {
        ALOGE("AMediaFormat_getInt64 fail: %lld", (long long) duration);
        return false;
    }

    AMediaFormat_setFloat(format, AMEDIAFORMAT_KEY_FRAME_RATE, 25.0f);
    float framerate = 0.0f;
    if (!AMediaFormat_getFloat(format, AMEDIAFORMAT_KEY_FRAME_RATE, &framerate)
            || framerate != 25.0f) {
        ALOGE("AMediaFormat_getFloat fail: %f", framerate);
        return false;
    }

    const char* value = "audio/mpeg";
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, value);
    const char* readback = NULL;
    if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &readback)
            || strcmp(value, readback) || value == readback) {
        ALOGE("AMediaFormat_getString fail");
        return false;
    }

    uint32_t foo = 0xdeadbeef;
    AMediaFormat_setBuffer(format, "csd-0", &foo, sizeof(foo));
    foo = 0xabadcafe;
    void *bytes;
    size_t bytesize = 0;
    if(!AMediaFormat_getBuffer(format, "csd-0", &bytes, &bytesize)
            || bytesize != sizeof(foo) || *((uint32_t*)bytes) != 0xdeadbeef) {
        ALOGE("AMediaFormat_getBuffer fail");
        return false;
    }

    return true;
}


extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testPsshNative(JNIEnv * /*env*/,
        jclass /*clazz*/, int fd, jlong offset, jlong size) {

    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return false;
    }

    PsshInfo* info = AMediaExtractor_getPsshInfo(ex);
    if (info == NULL) {
        ALOGI("null pssh");
        return false;
    }

    ALOGI("pssh has %zd entries", info->numentries);
    if (info->numentries != 2) {
        return false;
    }

    for (size_t i = 0; i < info->numentries; i++) {
        PsshEntry *entry = &info->entries[i];
        ALOGI("entry uuid %02x%02x..%02x%02x, data size %zd",
                entry->uuid[0],
                entry->uuid[1],
                entry->uuid[14],
                entry->uuid[15],
                entry->datalen);

        AMediaCrypto *crypto = AMediaCrypto_new(entry->uuid, entry->data, entry->datalen);
        if (crypto) {
            ALOGI("got crypto");
            AMediaCrypto_delete(crypto);
        } else {
            ALOGI("no crypto");
        }
    }
    return true;
}

extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testCryptoInfoNative(JNIEnv * /*env*/,
        jclass /*clazz*/) {

    size_t numsubsamples = 4;
    uint8_t key[16] = { 1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4 };
    uint8_t iv[16] = { 4,3,2,1,4,3,2,1,4,3,2,1,4,3,2,1 };
    size_t clearbytes[4] = { 5, 6, 7, 8 };
    size_t encryptedbytes[4] = { 8, 7, 6, 5 };

    AMediaCodecCryptoInfo *ci =
            AMediaCodecCryptoInfo_new(numsubsamples, key, iv, AMEDIACODECRYPTOINFO_MODE_CLEAR, clearbytes, encryptedbytes);

    if (AMediaCodecCryptoInfo_getNumSubSamples(ci) != 4) {
        ALOGE("numsubsamples mismatch");
        return false;
    }
    uint8_t bytes[16];
    AMediaCodecCryptoInfo_getKey(ci, bytes);
    if (memcmp(key, bytes, 16) != 0) {
        ALOGE("key mismatch");
        return false;
    }
    AMediaCodecCryptoInfo_getIV(ci, bytes);
    if (memcmp(iv, bytes, 16) != 0) {
        ALOGE("IV mismatch");
        return false;
    }
    if (AMediaCodecCryptoInfo_getMode(ci) != AMEDIACODECRYPTOINFO_MODE_CLEAR) {
        ALOGE("mode mismatch");
        return false;
    }
    size_t sizes[numsubsamples];
    AMediaCodecCryptoInfo_getClearBytes(ci, sizes);
    if (memcmp(clearbytes, sizes, sizeof(size_t) * numsubsamples)) {
        ALOGE("clear size mismatch");
        return false;
    }
    AMediaCodecCryptoInfo_getEncryptedBytes(ci, sizes);
    if (memcmp(encryptedbytes, sizes, sizeof(size_t) * numsubsamples)) {
        ALOGE("encrypted size mismatch");
        return false;
    }
    return true;
}

// === NdkMediaCodec

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreateCodecByName(
        JNIEnv *env, jclass /*clazz*/, jstring name) {

    if (name == NULL) {
        return 0;
    }

    const char *tmp = env->GetStringUTFChars(name, NULL);
    if (tmp == NULL) {
        return 0;
    }

    AMediaCodec *codec = AMediaCodec_createCodecByName(tmp);
    if (codec == NULL) {
        env->ReleaseStringUTFChars(name, tmp);
        return 0;
    }

    env->ReleaseStringUTFChars(name, tmp);
    return reinterpret_cast<jlong>(codec);

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecDelete(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_delete(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecStart(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_start(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecStop(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_stop(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecConfigure(
        JNIEnv *env,
        jclass /*clazz*/,
        jlong codec,
        jstring mime,
        jint width,
        jint height,
        jint colorFormat,
        jint bitRate,
        jint frameRate,
        jint iFrameInterval,
        jobject csd,
        jint flags) {

    AMediaFormat* format = AMediaFormat_new();
    if (format == NULL) {
        return false;
    }

    const char *tmp = env->GetStringUTFChars(mime, NULL);
    if (tmp == NULL) {
        AMediaFormat_delete(format);
        return false;
    }

    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, tmp);
    env->ReleaseStringUTFChars(mime, tmp);

    const char *keys[] = {
            AMEDIAFORMAT_KEY_WIDTH,
            AMEDIAFORMAT_KEY_HEIGHT,
            AMEDIAFORMAT_KEY_COLOR_FORMAT,
            AMEDIAFORMAT_KEY_BIT_RATE,
            AMEDIAFORMAT_KEY_FRAME_RATE,
            AMEDIAFORMAT_KEY_I_FRAME_INTERVAL
    };

    jint values[] = {width, height, colorFormat, bitRate, frameRate, iFrameInterval};
    for (size_t i = 0; i < sizeof(values) / sizeof(values[0]); i++) {
        if (values[i] >= 0) {
            AMediaFormat_setInt32(format, keys[i], values[i]);
        }
    }

    if (csd != NULL) {
        void *csdPtr = env->GetDirectBufferAddress(csd);
        jlong csdSize = env->GetDirectBufferCapacity(csd);
        AMediaFormat_setBuffer(format, "csd-0", csdPtr, csdSize);
    }

    media_status_t err = AMediaCodec_configure(
            reinterpret_cast<AMediaCodec *>(codec),
            format,
            NULL,
            NULL,
            flags);

    AMediaFormat_delete(format);
    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetInputSurface(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jobject surface) {

    media_status_t err = AMediaCodec_setInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            ANativeWindow_fromSurface(env, surface));

    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetNativeInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jlong nativeWindow) {

    media_status_t err = AMediaCodec_setInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            reinterpret_cast<ANativeWindow *>(nativeWindow));

    return err == AMEDIA_OK;

}

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreateInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec) {

    ANativeWindow *nativeWindow;
    media_status_t err = AMediaCodec_createInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            &nativeWindow);

     if (err == AMEDIA_OK) {
         return reinterpret_cast<jlong>(nativeWindow);
     }

     return 0;

}

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreatePersistentInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/) {

    ANativeWindow *nativeWindow;
    media_status_t err = AMediaCodec_createPersistentInputSurface(&nativeWindow);

     if (err == AMEDIA_OK) {
         return reinterpret_cast<jlong>(nativeWindow);
     }

     return 0;

}

extern "C" jstring Java_android_media_cts_NdkMediaCodec_AMediaCodecGetOutputFormatString(
        JNIEnv* env, jclass /*clazz*/, jlong codec) {

    AMediaFormat *format = AMediaCodec_getOutputFormat(reinterpret_cast<AMediaCodec *>(codec));
    const char *str = AMediaFormat_toString(format);
    jstring jstr = env->NewStringUTF(str);
    AMediaFormat_delete(format);
    return jstr;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSignalEndOfInputStream(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec) {

    media_status_t err = AMediaCodec_signalEndOfInputStream(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecReleaseOutputBuffer(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jint index, jboolean render) {

    media_status_t err = AMediaCodec_releaseOutputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            render);

    return err == AMEDIA_OK;

}

static jobject AMediaCodecGetBuffer(
        JNIEnv* env,
        jlong codec,
        jint index,
        uint8_t *(*getBuffer)(AMediaCodec*, size_t, size_t*)) {

    size_t bufsize;
    uint8_t *buf = getBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            &bufsize);

    return env->NewDirectByteBuffer(buf, bufsize);

}

extern "C" jobject Java_android_media_cts_NdkMediaCodec_AMediaCodecGetOutputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jint index) {

    return AMediaCodecGetBuffer(env, codec, index, AMediaCodec_getOutputBuffer);

}

extern "C" jlongArray Java_android_media_cts_NdkMediaCodec_AMediaCodecDequeueOutputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jlong timeoutUs) {

    AMediaCodecBufferInfo info;
    memset(&info, 0, sizeof(info));
    int status = AMediaCodec_dequeueOutputBuffer(
        reinterpret_cast<AMediaCodec *>(codec),
        &info,
        timeoutUs);

    jlong ret[5] = {0};
    ret[0] = status;
    ret[1] = 0; // NdkMediaCodec calls ABuffer::data, which already adds offset
    ret[2] = info.size;
    ret[3] = info.presentationTimeUs;
    ret[4] = info.flags;

    jlongArray jret = env->NewLongArray(5);
    env->SetLongArrayRegion(jret, 0, 5, ret);
    return jret;

}

extern "C" jobject Java_android_media_cts_NdkMediaCodec_AMediaCodecGetInputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jint index) {

    return AMediaCodecGetBuffer(env, codec, index, AMediaCodec_getInputBuffer);

}

extern "C" jint Java_android_media_cts_NdkMediaCodec_AMediaCodecDequeueInputBuffer(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jlong timeoutUs) {

    return AMediaCodec_dequeueInputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            timeoutUs);

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecQueueInputBuffer(
        JNIEnv* /*env*/,
        jclass /*clazz*/,
        jlong codec,
        jint index,
        jint offset,
        jint size,
        jlong presentationTimeUs,
        jint flags) {

    media_status_t err = AMediaCodec_queueInputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            offset,
            size,
            presentationTimeUs,
            flags);

    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetParameter(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jstring jkey, jint value) {

    AMediaFormat* params = AMediaFormat_new();
    if (params == NULL) {
        return false;
    }

    const char *key = env->GetStringUTFChars(jkey, NULL);
    if (key == NULL) {
        AMediaFormat_delete(params);
        return false;
    }

    AMediaFormat_setInt32(params, key, value);
    media_status_t err = AMediaCodec_setParameters(
            reinterpret_cast<AMediaCodec *>(codec),
            params);
    env->ReleaseStringUTFChars(jkey, key);
    AMediaFormat_delete(params);
    return err == AMEDIA_OK;

}

// === NdkInputSurface

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglGetDisplay(JNIEnv * /*env*/, jclass /*clazz*/) {

    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        return 0;
    }

    EGLint major, minor;
    if (!eglInitialize(eglDisplay, &major, &minor)) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglDisplay);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglChooseConfig(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay) {

    // Configure EGL for recordable and OpenGL ES 2.0.  We want enough RGB bits
    // to minimize artifacts from possible YUV conversion.
    EGLint attribList[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL_NONE
    };

    EGLConfig configs[1];
    EGLint numConfigs[1];
    if (!eglChooseConfig(reinterpret_cast<EGLDisplay>(eglDisplay), attribList, configs, 1, numConfigs)) {
        return 0;
    }
    return reinterpret_cast<jlong>(configs[0]);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglCreateContext(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglConfig) {

    // Configure context for OpenGL ES 2.0.
    int attrib_list[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };

    EGLConfig eglContext = eglCreateContext(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLConfig>(eglConfig),
            EGL_NO_CONTEXT,
            attrib_list);

    if (eglGetError() != EGL_SUCCESS) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglContext);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_createEGLSurface(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglConfig, jlong nativeWindow) {

    int surfaceAttribs[] = {EGL_NONE};
    EGLSurface eglSurface = eglCreateWindowSurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLConfig>(eglConfig),
            reinterpret_cast<EGLNativeWindowType>(nativeWindow),
            surfaceAttribs);

    if (eglGetError() != EGL_SUCCESS) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglSurface);

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglMakeCurrent(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong eglContext) {

    return eglMakeCurrent(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLContext>(eglContext));

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglSwapBuffers(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    return eglSwapBuffers(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface));

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglPresentationTimeANDROID(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong nsecs) {

    return eglPresentationTimeANDROID(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLnsecsANDROID>(nsecs));

}

extern "C" jint Java_android_media_cts_NdkInputSurface_eglGetWidth(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    EGLint width;
    eglQuerySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            EGL_WIDTH,
            &width);

    return width;

}

extern "C" jint Java_android_media_cts_NdkInputSurface_eglGetHeight(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    EGLint height;
    eglQuerySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            EGL_HEIGHT,
            &height);

    return height;

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglDestroySurface(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    return eglDestroySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface));

}

extern "C" void Java_android_media_cts_NdkInputSurface_nativeRelease(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong eglContext, jlong nativeWindow) {

    if (eglDisplay != 0) {

        EGLDisplay _eglDisplay = reinterpret_cast<EGLDisplay>(eglDisplay);
        EGLSurface _eglSurface = reinterpret_cast<EGLSurface>(eglSurface);
        EGLContext _eglContext = reinterpret_cast<EGLContext>(eglContext);

        eglDestroySurface(_eglDisplay, _eglSurface);
        eglDestroyContext(_eglDisplay, _eglContext);
        eglReleaseThread();
        eglTerminate(_eglDisplay);

    }

    ANativeWindow_release(reinterpret_cast<ANativeWindow *>(nativeWindow));

}
