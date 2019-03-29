/*
 * Copyright (C) 2017 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeMediaEnc"

#include <stddef.h>
#include <inttypes.h>
#include <log/log.h>

#include <assert.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <semaphore.h>
#include <list>
#include <memory>
#include <string>

#include <android/native_window_jni.h>

#include "media/NdkMediaFormat.h"
#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaCrypto.h"
#include "media/NdkMediaFormat.h"
#include "media/NdkMediaMuxer.h"

#include "native_media_source.h"
using namespace Utils;

class NativeEncoder : Thread {
public:

    NativeEncoder(const std::string&);
    NativeEncoder(const NativeEncoder&) = delete;
    ~NativeEncoder();
    static std::shared_ptr<ANativeWindow> getPersistentSurface();
    std::shared_ptr<ANativeWindow> getSurface() const;

    Status prepare(std::unique_ptr<RunConfig> config, std::shared_ptr<ANativeWindow> anw = nullptr);
    Status start();
    Status waitForCompletion();
    Status validate();

    Status reset();

protected:
    void run() override;

private:
    std::shared_ptr<AMediaCodec> mEnc;
    std::shared_ptr<ANativeWindow> mLocalSurface; // the one created by createInputSurface()
    std::string mOutFileName;
    bool mStarted;

    Stats mStats;
    std::unique_ptr<RunConfig> mRunConfig;

};

NativeEncoder::NativeEncoder(const std::string& outFileName)
    : mEnc(nullptr),
      mLocalSurface(nullptr),
      mOutFileName(outFileName),
      mStarted(false) {
    mRunConfig = nullptr;
}

NativeEncoder::~NativeEncoder() {
    mEnc = nullptr;
    mLocalSurface = nullptr;
    mRunConfig = nullptr;
}

//static
std::shared_ptr<ANativeWindow> NativeEncoder::getPersistentSurface() {
    ANativeWindow *ps;
    media_status_t ret = AMediaCodec_createPersistentInputSurface(&ps);
    if (ret != AMEDIA_OK) {
        ALOGE("Failed to create persistent surface !");
        return nullptr;
    }
    ALOGI("Encoder: created persistent surface %p", ps);
    return std::shared_ptr<ANativeWindow>(ps, deleter_ANativeWindow);
}

std::shared_ptr<ANativeWindow> NativeEncoder::getSurface() const {
    return mLocalSurface;
}

Status NativeEncoder::prepare(
        std::unique_ptr<RunConfig> runConfig, std::shared_ptr<ANativeWindow> surface) {
    assert(runConfig != nullptr);
    assert(runConfig->format() != nullptr);

    ALOGI("NativeEncoder::prepare");
    mRunConfig = std::move(runConfig);

    AMediaFormat *config = mRunConfig->format();
    ALOGI("Encoder format: %s", AMediaFormat_toString(config));

    const char *mime;
    AMediaFormat_getString(config, AMEDIAFORMAT_KEY_MIME, &mime);

    AMediaCodec *enc = AMediaCodec_createEncoderByType(mime);
    mEnc = std::shared_ptr<AMediaCodec>(enc, deleter_AMediaCodec);

    media_status_t status = AMediaCodec_configure(
            mEnc.get(), config, NULL, NULL /* crypto */, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    if (status != AMEDIA_OK) {
        ALOGE("failed to configure encoder");
        return FAIL;
    }

    if (surface == nullptr) {
        ANativeWindow *anw;
        status = AMediaCodec_createInputSurface(mEnc.get(), &anw);
        mLocalSurface = std::shared_ptr<ANativeWindow>(anw, deleter_ANativeWindow);
        ALOGI("created input surface = %p", mLocalSurface.get());
    } else {
        ALOGI("setting persistent input surface %p", surface.get());
        status = AMediaCodec_setInputSurface(mEnc.get(), surface.get());
    }

    return status == AMEDIA_OK ? OK : FAIL;
}

Status NativeEncoder::start() {
    ALOGI("starting encoder..");

    media_status_t status = AMediaCodec_start(mEnc.get());
    if (status != AMEDIA_OK) {
        ALOGE("failed to start decoder");
        return FAIL;
    }
    if (startThread() != OK) {
        return FAIL;
    }
    mStarted = true;
    return OK;
}

Status NativeEncoder::waitForCompletion() {
    joinThread();
    ALOGI("encoder done..");
    return OK;
}

Status NativeEncoder::validate() {
    const char *s = AMediaFormat_toString(mRunConfig->format());
    ALOGI("RESULT: Encoder Output Format: %s", s);

    {
        int32_t encodedFrames = mStats.frameCount();
        int32_t inputFrames = mRunConfig->frameCount();
        ALOGI("RESULT: input frames = %d, Encoded frames = %d",
                inputFrames, encodedFrames);
        if (encodedFrames != inputFrames) {
            ALOGE("RESULT: ERROR: output frame count does not match input");
            return FAIL;
        }
    }

    if (Validator::checkOverallBitrate(mStats, *mRunConfig) != OK) {
        ALOGE("Overall bitrate check failed!");
        return FAIL;
    }
    if (Validator::checkIntraPeriod(mStats, *mRunConfig) != OK) {
        ALOGE("I-period check failed!");
        return FAIL;
    }
    if (Validator::checkDynamicKeyFrames(mStats, *mRunConfig) != OK) {
        ALOGE("Dynamic-I-frame-request check failed!");
        return FAIL;
    }
    if (Validator::checkDynamicBitrate(mStats, *mRunConfig) != OK) {
        ALOGE("Dynamic-bitrate-update check failed!");
        return FAIL;
    }

    return OK;
}

Status NativeEncoder::reset() {

    mEnc = nullptr;
    return OK;
}

void NativeEncoder::run() {

    assert(mRunConfig != nullptr);

    int32_t framesToEncode = mRunConfig->frameCount();
    auto dynamicParams = mRunConfig->dynamicParams();
    auto paramItr = dynamicParams.begin();
    int32_t nFrameCount = 0;

    while (nFrameCount < framesToEncode) {

        // apply frame-specific settings
        for (;paramItr != dynamicParams.end()
                && (*paramItr)->frameNum() <= nFrameCount; ++paramItr) {
            DParamRef& p = *paramItr;
            if (p->frameNum() == nFrameCount) {
                assert(p->param() != nullptr);
                const char *s = AMediaFormat_toString(p->param());
                ALOGI("Encoder DynamicParam @frame[%d] - applying setting : %s",
                        nFrameCount, s);
                AMediaCodec_setParameters(mEnc.get(), p->param());
            }
        }

        AMediaCodecBufferInfo info;
        int status = AMediaCodec_dequeueOutputBuffer(mEnc.get(), &info, 5000);
        if (status >= 0) {
            ALOGV("got encoded buffer[%d] of size=%d @%lld us flags=%x",
                    nFrameCount, info.size, (long long)info.presentationTimeUs, info.flags);
            mStats.add(info);
            AMediaCodec_releaseOutputBuffer(mEnc.get(), status, false);
            ++nFrameCount;

            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                ALOGV("saw EOS");
                break;
            }

        } else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
        } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            std::shared_ptr<AMediaFormat> format = std::shared_ptr<AMediaFormat>(
                    AMediaCodec_getOutputFormat(mEnc.get()), deleter_AMediaFormat);
            mStats.setOutputFormat(format);
            ALOGV("format changed: %s", AMediaFormat_toString(format.get()));
        } else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
        } else {
            ALOGV("Invalid status : %d", status);
        }
    }

    ALOGV("Encoder exited !");
    AMediaCodec_stop(mEnc.get());
}

static std::shared_ptr<AMediaFormat> createMediaFormat(
        std::string mime,
        int32_t w, int32_t h, int32_t colorFormat,
        int32_t bitrate, float framerate,
        int32_t i_interval) {

    std::shared_ptr<AMediaFormat> config(AMediaFormat_new(), deleter_AMediaFormat);

    AMediaFormat_setString(config.get(), AMEDIAFORMAT_KEY_MIME, mime.c_str());
    AMediaFormat_setInt32(config.get(), AMEDIAFORMAT_KEY_WIDTH, w);
    AMediaFormat_setInt32(config.get(), AMEDIAFORMAT_KEY_HEIGHT, h);
    AMediaFormat_setFloat(config.get(), AMEDIAFORMAT_KEY_FRAME_RATE, framerate);
    AMediaFormat_setInt32(config.get(), AMEDIAFORMAT_KEY_BIT_RATE, bitrate);
    AMediaFormat_setInt32(config.get(), AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, i_interval);
    AMediaFormat_setInt32(config.get(), AMEDIAFORMAT_KEY_COLOR_FORMAT, colorFormat);

    return config;
}

static int32_t getOptimalBitrate(int w, int h) {
    return (w * h <= 640 * 480) ? 1000000 :
            (w * h <= 1280 * 720) ? 2000000 :
            (w * h <= 1920 * 1080) ? 6000000 :
            10000000;
}

//-----------------------------------------------------------------------------
// Tests
//-----------------------------------------------------------------------------
static bool runNativeEncoderTest(
        JNIEnv *env, int fd, jlong offset, jlong fileSize,
        jstring jmime, int w, int h,
        const std::vector<DParamRef>& dynParams,
        int32_t numFrames,
        bool usePersistentSurface) {

    // If dynamic I-frame is requested, set large-enough i-period
    // so that auto I-frames do not interfere with the ones explicitly requested,
    // and hence simplify validation.
    bool hasDynamicSyncRequest = false;

    // If dynamic bitrate updates are requested, set bitrate mode to CBR to
    // ensure bitrate within 'window of two updates' remains constant
    bool hasDynamicBitrateChanges = false;

    for (const DParamRef &d : dynParams) {
        int32_t temp;
        if (AMediaFormat_getInt32(d->param(), TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME, &temp)) {
            hasDynamicSyncRequest = true;
        } else if (AMediaFormat_getInt32(d->param(), TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE, &temp)) {
            hasDynamicBitrateChanges = true;
        }
    }

    const char* cmime = env->GetStringUTFChars(jmime, nullptr);
    std::string mime = cmime;
    env->ReleaseStringUTFChars(jmime, cmime);

    float fps = 30.0f;
    std::shared_ptr<AMediaFormat> config = createMediaFormat(
            mime, w, h, kColorFormatSurface,
            getOptimalBitrate(w, h),
            fps,
            hasDynamicSyncRequest ? numFrames / fps : 1 /*sec*/);

    if (hasDynamicBitrateChanges) {
        AMediaFormat_setInt32(config.get(), TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE, kBitrateModeConstant);
    }

    std::shared_ptr<Source> src = createDecoderSource(
            w, h, kColorFormatSurface, fps,
            true /*looping*/,
            hasDynamicSyncRequest | hasDynamicBitrateChanges, /*regulate feeding rate*/
            fd, offset, fileSize);

    std::unique_ptr<RunConfig> runConfig = std::make_unique<RunConfig>(numFrames, config);
    for (const DParamRef &d : dynParams) {
        runConfig->add(d);
    }

    std::string debugOutputFileName = "";
    std::shared_ptr<NativeEncoder> enc(new NativeEncoder(debugOutputFileName));

    if (usePersistentSurface) {
        std::shared_ptr<ANativeWindow> persistentSurface = enc->getPersistentSurface();
        enc->prepare(std::move(runConfig), persistentSurface);
        src->prepare(nullptr /*bufferListener*/, persistentSurface);
    } else {
        enc->prepare(std::move(runConfig));
        src->prepare(nullptr /*bufferListener*/, enc->getSurface());
    }

    src->start();
    enc->start();

    enc->waitForCompletion();

    Status status = enc->validate();

    src->stop();
    enc->reset();

    return status == OK;
}

extern "C" jboolean Java_android_media_cts_NativeEncoderTest_testEncodeSurfaceNative(
        JNIEnv *env, jclass /*clazz*/, int fd, jlong offset, jlong fileSize,
        jstring jmime, int w, int h) {

    std::vector<DParamRef> dynParams;
    return runNativeEncoderTest(env, fd, offset, fileSize, jmime, w, h,
            dynParams, 300, false /*usePersistentSurface*/);

}

extern "C" jboolean Java_android_media_cts_NativeEncoderTest_testEncodePersistentSurfaceNative(
        JNIEnv *env, jclass /*clazz*/, int fd, jlong offset, jlong fileSize,
        jstring jmime, int w, int h) {

    std::vector<DParamRef> dynParams;
    return runNativeEncoderTest(env, fd, offset, fileSize, jmime, w, h,
            dynParams, 300, true /*usePersistentSurface*/);
}

extern "C" jboolean Java_android_media_cts_NativeEncoderTest_testEncodeSurfaceDynamicSyncFrameNative(
        JNIEnv *env, jclass /*clazz*/, int fd, jlong offset, jlong fileSize,
        jstring jmime, int w, int h) {

    std::vector<DParamRef> dynParams;
    for (int32_t frameNum : {40, 75, 160, 180, 250}) {
        dynParams.push_back(DynamicParam::newRequestSync(frameNum));
    }

    return runNativeEncoderTest(env, fd, offset, fileSize, jmime, w, h,
            dynParams, 300, false /*usePersistentSurface*/);
}

extern "C" jboolean Java_android_media_cts_NativeEncoderTest_testEncodeSurfaceDynamicBitrateNative(
        JNIEnv *env, jclass /*clazz*/, int fd, jlong offset, jlong fileSize,
        jstring jmime, int w, int h) {

    int32_t bitrate = getOptimalBitrate(w, h);
    std::vector<DParamRef> dynParams;

    dynParams.push_back(DynamicParam::newBitRate(100,  bitrate/2));
    dynParams.push_back(DynamicParam::newBitRate(200,  3*bitrate/4));
    dynParams.push_back(DynamicParam::newBitRate(300,  bitrate));

    return runNativeEncoderTest(env, fd, offset, fileSize, jmime, w, h,
            dynParams, 400, false /*usePersistentSurface*/);
}

