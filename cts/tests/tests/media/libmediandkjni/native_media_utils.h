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

#ifndef _NATIVE_MEDIA_UTILS_H_
#define _NATIVE_MEDIA_UTILS_H_

#include <pthread.h>
#include <sys/cdefs.h>
#include <stddef.h>
#include <assert.h>
#include <vector>

#include <android/native_window_jni.h>

#include "media/NdkMediaFormat.h"
#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaMuxer.h"

namespace Utils {

// constants not defined in NDK api
extern const char * TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME;
extern const char * TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE;
static const uint32_t TBD_AMEDIACODEC_BUFFER_FLAG_KEY_FRAME = 0x1;

extern const char * TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE;
static const int32_t kBitrateModeConstant = 2;
static const int32_t kColorFormatSurface = 0x7f000789;

// tolerances
static const float kBitrateDeviationPercentMax = 10.0;
static const int32_t kSyncFrameDeviationFramesMax = 5;

enum Status : int32_t {
    FAIL = -1,
    OK = 0,
};

class Thread {
public:
    Thread()
        : mHandle(0) {
    }
    virtual ~Thread() {
        assert(mExited);
        mHandle = 0;
    }
    Thread(const Thread& ) = delete;
    Status startThread();
    Status joinThread();

protected:
    virtual void run() = 0;

private:
    static void* thread_wrapper(void *);
    pthread_t mHandle;
};

static inline void deleter_AMediExtractor(AMediaExtractor *_a) {
    AMediaExtractor_delete(_a);
}

static inline void deleter_AMediaCodec(AMediaCodec *_a) {
    AMediaCodec_delete(_a);
}

static inline void deleter_AMediaFormat(AMediaFormat *_a) {
    AMediaFormat_delete(_a);
}

static inline void deleter_AMediaMuxer(AMediaMuxer *_a) {
    AMediaMuxer_delete(_a);
}

static inline void deleter_ANativeWindow(ANativeWindow *_a) {
    ANativeWindow_release(_a);
}

/*
 * Dynamic paramater that will be applied via AMediaCodec_setParamater(..)
 *  during the encoding process, at the given frame number
 */
struct DynamicParam {
    DynamicParam() = delete;
    DynamicParam(const DynamicParam&) = delete;
    ~DynamicParam() = default;

    static std::shared_ptr<DynamicParam> newBitRate(int atFrame, int32_t bitrate) {
        DynamicParam *d = new DynamicParam(atFrame);
        AMediaFormat_setInt32(d->param(), TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE, bitrate);
        return std::shared_ptr<DynamicParam>(d);
    }
    static std::shared_ptr<DynamicParam> newRequestSync(int atFrame) {
        DynamicParam *d = new DynamicParam(atFrame);
        AMediaFormat_setInt32(d->param(), TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME, 0 /*ignore*/);
        return std::shared_ptr<DynamicParam>(d);
    }

    inline int frameNum() const {
        return mFrameNum;
    }
    inline AMediaFormat *param() const {
        return mParam.get();
    }

private:
    DynamicParam(int _at)
        : mFrameNum(_at) {
        mParam = std::shared_ptr<AMediaFormat>(AMediaFormat_new(), deleter_AMediaFormat);
    }

    int mFrameNum;
    std::shared_ptr<AMediaFormat> mParam;
};

using DParamRef = std::shared_ptr<DynamicParam>;

/*
 * Configuration to the encoder (static + dynamic)
 */
struct RunConfig {
    RunConfig(const RunConfig&) = delete;
    RunConfig(int32_t numFramesToEncode, std::shared_ptr<AMediaFormat> staticParams)
        : mNumFramesToEncode (numFramesToEncode),
          mStaticParams(staticParams) {
    }
    void add(const DParamRef& p) {
        mParams.push_back(p);
    }

    AMediaFormat* format() const {
        return mStaticParams.get();
    }
    const std::vector<DParamRef>& dynamicParams() const {
        return mParams;
    }
    int32_t frameCount() const {
        return mNumFramesToEncode;
    }
    int32_t dynamicParamsOfKind(
        const char *key, std::vector<DParamRef>& ) const;

private:
    int32_t mNumFramesToEncode;
    std::vector<DParamRef> mParams;
    std::shared_ptr<AMediaFormat> mStaticParams;
};

/*
 * Encoded output statistics
 * provides helpers to compute windowed average of bitrate and search for I-frames
 */
struct Stats {
    Stats() = default;
    Stats(const Stats&) = delete;
    void add(const AMediaCodecBufferInfo &info) {
        mInfos.push_back(info);
    }
    void setOutputFormat(std::shared_ptr<AMediaFormat> fmt) {
        mOutputFormat = fmt;
    }
    int32_t frameCount() const {
        return (int32_t)mInfos.size();
    }
    const std::vector<AMediaCodecBufferInfo>& infos() const {
        return mInfos;
    }

    int32_t getBitrateAverage(int32_t frameNumFrom, int32_t frameNumTo) const;
    int32_t getBitratePeak(int32_t frameNumFrom, int32_t frameNumTo, int32_t windowSize) const;
    int32_t getSyncFrameNext(int32_t frameNumWhence) const;

private:
    std::vector<AMediaCodecBufferInfo> mInfos;
    std::shared_ptr<AMediaFormat> mOutputFormat;
};

/*
 * Helpers to validate output (Stats) based on expected settings (RunConfig)
 * Check for validity of both static and dynamic settings
 */
struct Validator {
    static Status checkOverallBitrate(const Stats&, const RunConfig&);
    static Status checkFramerate(const Stats&, const RunConfig&);
    static Status checkIntraPeriod(const Stats&, const RunConfig&);
    static Status checkDynamicKeyFrames(const Stats&, const RunConfig&);
    static Status checkDynamicBitrate(const Stats&, const RunConfig&);
};

}; //namespace Utils

#endif // _NATIVE_MEDIA_UTILS_H_
