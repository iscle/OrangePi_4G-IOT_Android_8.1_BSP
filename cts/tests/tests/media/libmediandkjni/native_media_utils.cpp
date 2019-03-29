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
#define LOG_TAG "NativeMedia"
#include <log/log.h>

#include <stdlib.h>
#include <math.h>
#include <string>
#include <algorithm>
#include <iterator>
#include "native_media_utils.h"

namespace Utils {

const char * TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME = "request-sync";
const char * TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE = "video-bitrate";

const char * TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE = "bitrate-mode";

Status Thread::startThread() {
    assert(mHandle == 0);
    if (pthread_create(&mHandle, nullptr, Thread::thread_wrapper, this) != 0) {
        ALOGE("Failed to create thread");
        return FAIL;
    }
    return OK;
}

Status Thread::joinThread() {
    assert(mHandle != 0);
    void *ret;
    pthread_join(mHandle, &ret);
    return OK;
}

//static
void* Thread::thread_wrapper(void *obj) {
    assert(obj != nullptr);
    Thread *t = reinterpret_cast<Thread *>(obj);
    t->run();
    return nullptr;
}

int32_t RunConfig::dynamicParamsOfKind(
        const char *key, std::vector<DParamRef>& paramsList) const {
    paramsList.clear();
    for (const DParamRef& d : mParams) {
        assert(d->param() != nullptr);

        if (!strncmp(key, TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME,
                strlen(TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME))) {
            int32_t tmp;
            if (AMediaFormat_getInt32(d->param(), TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME, &tmp)) {
                paramsList.push_back(d);
            }

        } else if (!strncmp(key, TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE,
                strlen(TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE))) {
            int32_t tmp;
            if (AMediaFormat_getInt32(d->param(), TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE, &tmp)) {
                paramsList.push_back(d);
            }
        }
    }
    return (int32_t)paramsList.size();
}

static bool comparePTS(const AMediaCodecBufferInfo& l, const AMediaCodecBufferInfo& r) {
    return l.presentationTimeUs < r.presentationTimeUs;
}

int32_t Stats::getBitrateAverage(int32_t frameNumFrom, int32_t frameNumTo) const {
    int64_t sum = 0;
    assert(frameNumFrom >= 0 && frameNumTo < mInfos.size());
    for (int i = frameNumFrom; i < frameNumTo; ++i) {
        sum += mInfos[i].size;
    }
    sum *= 8; // kB -> kb

    auto from = mInfos.begin() + frameNumFrom;
    auto to = mInfos.begin() + frameNumTo;
    int64_t duration = (*std::max_element(from, to, comparePTS)).presentationTimeUs
            - (*std::min_element(from, to, comparePTS)).presentationTimeUs;
    if (duration <= 0) {
        return 0;
    }

    int64_t avg = (sum * 1e6) / duration;
    return (int32_t)avg;
}

int32_t Stats::getBitratePeak(
        int32_t frameNumFrom, int32_t frameNumTo, int32_t windowSize) const {
    int64_t sum = 0;
    int64_t maxSum = 0;
    assert(frameNumFrom >= 0 && frameNumTo < mInfos.size());
    assert(windowSize < (frameNumTo - frameNumFrom));

    for (int i = frameNumFrom; i < frameNumTo; ++i) {
        sum += mInfos[i].size;
        if (i >= windowSize) {
            sum -= mInfos[i - windowSize].size;
        }
        maxSum = sum > maxSum ? sum : maxSum;
    }
    maxSum *= 8; // kB -> kb
    int64_t duration = mInfos[frameNumTo].presentationTimeUs -
            mInfos[frameNumFrom].presentationTimeUs;
    if (duration <= 0) {
        return 0;
    }

    int64_t peak = (maxSum * 1e6) / duration;
    return (int32_t)peak;
}

int32_t Stats::getSyncFrameNext(int32_t frameNumWhence) const {
    assert(frameNumWhence >= 0 && frameNumWhence < mInfos.size());
    int i = frameNumWhence;
    for (; i < (int)mInfos.size(); ++i) {
        if (mInfos[i].flags & TBD_AMEDIACODEC_BUFFER_FLAG_KEY_FRAME) {
            return i;
        }
    }
    return -1;
}

Status Validator::checkOverallBitrate(const Stats &stats, const RunConfig& config) {
    // skip this check if bitrate was updated dynamically
    ALOGV("DEBUG: checkOverallBitrate");
    std::vector<DParamRef> tmp;
    if (config.dynamicParamsOfKind(TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE, tmp) > 0) {
        ALOGV("DEBUG: checkOverallBitrate: dynamic bitrate enabled");
        return OK;
    }

    int32_t bitrate = 0;
    if (!AMediaFormat_getInt32(config.format(), AMEDIAFORMAT_KEY_BIT_RATE, &bitrate)) {
        // should not happen
        ALOGV("DEBUG: checkOverallBitrate: bitrate was not configured !");
        return FAIL;
    }
    assert(bitrate > 0);

    int32_t avgBitrate = stats.getBitrateAverage(0, config.frameCount() - 1);
    float deviation = (avgBitrate - bitrate) * 100 / bitrate;
    ALOGI("RESULT: Bitrate expected=%d Achieved=%d Deviation=%.2g%%",
            bitrate, avgBitrate, deviation);

    if (fabs(deviation) > kBitrateDeviationPercentMax) {
        ALOGI("RESULT: ERROR: bitrate deviation(%.2g%%) exceeds threshold (+/-%.2g%%)",
                deviation, kBitrateDeviationPercentMax);
        return FAIL;
    }

    // TODO
    // if bitrate mode was set to CBR, check for peak-bitrate deviation (+/-20%?)
    return OK;
}

Status Validator::checkFramerate(const Stats&, const RunConfig&) {
    // TODO - tricky if frames are reordered
    return OK;
}

Status Validator::checkIntraPeriod(const Stats& stats, const RunConfig& config) {
    float framerate;
    if (!AMediaFormat_getFloat(config.format(), AMEDIAFORMAT_KEY_FRAME_RATE, &framerate)) {
        // should not happen
        ALOGV("DEBUG: checkIntraPeriod: framerate was not configured ! : %s",
                AMediaFormat_toString(config.format()));
        return OK;
    }

    int32_t intraPeriod;
    if (!AMediaFormat_getInt32(config.format(), AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, &intraPeriod)) {
        // should not happen
        ALOGV("DEBUG: checkIntraPeriod: I-period was not configured !");
        return OK;
    }

    // TODO: handle special cases
    // intraPeriod = 0  => all I
    // intraPeriod < 0  => infinite GOP
    if (intraPeriod <= 0) {
        return OK;
    }

    int32_t iInterval = framerate * intraPeriod;

    if (iInterval >= stats.frameCount()) {
        ALOGV("RESULT: Intra-period %d exceeds frame-count %d ..skipping",
                iInterval, stats.frameCount());
        return OK;
    }

    int32_t numGopFound = 0;
    int32_t sumGopDistance = 0;
    int32_t lastKeyLocation = stats.getSyncFrameNext(0);
    for (;;) {
        int32_t nextKeyLocation = stats.getSyncFrameNext(lastKeyLocation + iInterval - kSyncFrameDeviationFramesMax);
        if (nextKeyLocation < 0) {
            break;
        }
        if (abs(nextKeyLocation - lastKeyLocation - iInterval) > kSyncFrameDeviationFramesMax) {
            ALOGE("RESULT: ERROR: Intra period at frame %d is %d (expected %d +/-%d)",
                    lastKeyLocation, nextKeyLocation - lastKeyLocation, iInterval,
                    kSyncFrameDeviationFramesMax);
            return FAIL;
        }
        ++numGopFound;
        sumGopDistance += (nextKeyLocation - lastKeyLocation);
        lastKeyLocation = nextKeyLocation;
    }

    if (numGopFound) {
        ALOGI("RESULT: Intra-period: configured=%d frames (%d sec). Actual=%d frames",
                iInterval, intraPeriod, sumGopDistance / numGopFound);
    }

    return OK;
}

Status Validator::checkDynamicKeyFrames(const Stats& stats, const RunConfig& config) {
    ALOGV("DEBUG: checkDynamicKeyFrames");
    std::vector<DParamRef> keyRequests;
    if (config.dynamicParamsOfKind(TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME, keyRequests) <= 0) {
        ALOGV("DEBUG: dynamic key-frames were not requested");
        return OK;
    }

    std::string debugStr = "";
    bool fail = false;
    for (DParamRef &d : keyRequests) {
        int32_t generatedKeyLocation = stats.getSyncFrameNext(d->frameNum());
        if (generatedKeyLocation - d->frameNum() > kSyncFrameDeviationFramesMax) {
            ALOGI("RESULT: ERROR: Dynamic sync-frame requested at frame=%d, got at frame=%d",
                    d->frameNum(), generatedKeyLocation);
            fail = true;
        }
        char tmp[128];
        snprintf(tmp, 128, " %d/%d,", generatedKeyLocation, d->frameNum());
        debugStr = debugStr + std::string(tmp);
    }
    ALOGI("RESULT: Dynamic Key-frame locations - actual/requested :");
    ALOGI("RESULT:         %s", debugStr.c_str());

    return fail ? FAIL : OK;
}

Status Validator::checkDynamicBitrate(const Stats& stats, const RunConfig& config) {
    // Checking bitrate convergence between two updates makes sense if requested along with CBR
    // check if CBR mode has been set. If not, simply pass
    int32_t bitrateMode;
    if (!AMediaFormat_getInt32(config.format(), TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE,
            &bitrateMode) || bitrateMode != kBitrateModeConstant) {
        ALOGV("DEBUG: checkDynamicBitrate: skipping since CBR not requested");
        return OK; //skip
    }

    // check if dynamic bitrates were requested
    std::vector<DParamRef> bitrateUpdates;
    if (config.dynamicParamsOfKind(TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE, bitrateUpdates) <= 0) {
        ALOGV("DEBUG: checkDynamicBitrate: dynamic bitrates not requested !");
        return OK; //skip
    }
    int32_t bitrate = 0;
    if (!AMediaFormat_getInt32(config.format(), AMEDIAFORMAT_KEY_BIT_RATE, &bitrate)) {
        // should not happen
        ALOGV("DEBUG: checkDynamicBitrate: bitrate was not configured !");
        return OK; //skip
    }
    assert(bitrate > 0);

    std::string debugStr = "";
    int32_t lastBitrateUpdateFrameNum = 0;
    int32_t lastBitrate = bitrate;
    bool fail = false;

    for (DParamRef &d : bitrateUpdates) {
        int32_t updatedBitrate = 0;
        if (!AMediaFormat_getInt32(
                d->param(), TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE, &updatedBitrate)) {
            ALOGE("BUG: expected dynamic bitrate");
            continue;
        }
        assert(updatedBitrate > 0);

        int32_t lastAverage = stats.getBitrateAverage(lastBitrateUpdateFrameNum,  d->frameNum() - 1);
        float deviation = (lastAverage - lastBitrate) * 100 / lastBitrate;

        if (fabs(deviation) > kBitrateDeviationPercentMax) {
            ALOGI("RESULT: ERROR: dynamic bitrate deviation(%.2g%%) exceeds threshold (+/-%.2g%%)",
                    deviation, kBitrateDeviationPercentMax);
            fail |= true;
        }

        char tmp[128];
        snprintf(tmp, 128, "  [%d - %d] %d/%d,",
                lastBitrateUpdateFrameNum, d->frameNum() - 1, lastAverage, lastBitrate);
        debugStr = debugStr + std::string(tmp);
        lastBitrate = updatedBitrate;
        lastBitrateUpdateFrameNum = d->frameNum();
    }

    ALOGI("RESULT: Dynamic Bitrates : [from-frame  -  to-frame] actual/expected :");
    ALOGI("RESULT:        %s", debugStr.c_str());

    return fail ? FAIL : OK;
}


}; // namespace Utils
