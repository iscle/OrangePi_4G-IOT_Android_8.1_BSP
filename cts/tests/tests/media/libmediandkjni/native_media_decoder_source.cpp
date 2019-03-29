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
#define LOG_TAG "NativeMediaEnc-Source"
#include <log/log.h>

#include "native_media_source.h"

using namespace Utils;

class DecoderSource : public Thread, public Source {
public:
    DecoderSource(
        int32_t w, int32_t h, int32_t colorFormat, float fps, bool looping, bool regulate);
    ~DecoderSource();
    DecoderSource(const DecoderSource& ) = delete;

    Status setDataSourceFd(int sourceFileFd, off64_t sourceFileOffset, off64_t sourceFileSize);
    Status prepare(std::shared_ptr<Listener> l, std::shared_ptr<ANativeWindow> n) override;
    Status start() override;
    Status stop() override;

protected:
    void run() override;

private:
    // seek the extractor back to beginning
    void rewindExtractor();

    // When setting dynamic params, if the source is faster than the encoder,
    // there is a possibility of param set via setParameters() will get delayed.
    // Simulate a real-time source by slowing down the feeding rate (up to configured fps)
    bool mRegulateFramerate;

    std::shared_ptr<AMediaExtractor> mEx;
    std::shared_ptr<AMediaCodec> mDec;
    std::shared_ptr<AMediaFormat> mFormat;
    std::string mMime;
    int mVideoTrackIndex;
    int mFrameCount;
    bool mStopRequest;
    bool mStarted;
};

std::shared_ptr<Source> createDecoderSource(
        int32_t w, int32_t h, int32_t colorFormat, float fps, bool looping,
        bool regulateFeedingRate, /* WA for dynamic settings */
        int sourceFileFd, off64_t sourceFileOffset, off64_t sourceFileSize) {
    DecoderSource *d = new DecoderSource(w, h, colorFormat, fps, looping, regulateFeedingRate);
    d->setDataSourceFd(sourceFileFd, sourceFileOffset, sourceFileSize);
    std::shared_ptr<Source> src(d);
    return src;
}

DecoderSource::DecoderSource(
        int32_t w, int32_t h, int32_t colorFormat, float fps, bool looping, bool regulate)
    : Source(w, h, colorFormat, fps, looping),
      mRegulateFramerate(regulate),
      mEx(nullptr),
      mDec(nullptr),
      mFormat(nullptr),
      mMime(""),
      mVideoTrackIndex(-1),
      mFrameCount(0),
      mStopRequest(false),
      mStarted(false) {
}

Status DecoderSource::setDataSourceFd(
        int sourceFileFd, off64_t sourceFileOffset, off64_t sourceFileSize) {

    mEx = std::shared_ptr<AMediaExtractor>(AMediaExtractor_new(), deleter_AMediExtractor);
    int err = AMediaExtractor_setDataSourceFd(mEx.get(), sourceFileFd, sourceFileOffset, sourceFileSize);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return FAIL;
    }

    const char *mime;
    mVideoTrackIndex = -1;
    int numtracks = AMediaExtractor_getTrackCount(mEx.get());
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(mEx.get(), i);
        const char *s = AMediaFormat_toString(format);
        ALOGV("track %d format: %s", i, s);
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            mEx = nullptr;
            AMediaFormat_delete(format);
            return FAIL;
        } else if (!strncmp(mime, "video/", 6)) {
            mVideoTrackIndex = i;
            mFormat = std::shared_ptr<AMediaFormat>(format, deleter_AMediaFormat);
            mMime = mime;
            break;
        } else {
            ALOGE("expected video mime type, got %s", mime);
            mEx = nullptr;
        }
        AMediaFormat_delete(format);
    }
    return mVideoTrackIndex == -1 ? FAIL : OK;
}

DecoderSource::~DecoderSource() {
    mDec = nullptr;
    mEx = nullptr;
    mFormat = nullptr;
}

void DecoderSource::run() {
    while(!mStopRequest) {
        int t = AMediaExtractor_getSampleTrackIndex(mEx.get());
        if (t < 0) {
            if (mLooping) {
                rewindExtractor();
                continue;
            } else {
                ALOGV("no more samples");
                break;
            }
        } else if (t != mVideoTrackIndex) {
            continue;
        }

        ssize_t bufidx = AMediaCodec_dequeueInputBuffer(mDec.get(), 5000);
        if (bufidx >= 0) {
            size_t bufsize;
            uint8_t *buf = AMediaCodec_getInputBuffer(mDec.get(), bufidx, &bufsize);
            int sampleSize = AMediaExtractor_readSampleData(mEx.get(), buf, bufsize);
            int32_t flags = 0;
            if (sampleSize < 0) {
                if (mLooping) {
                    rewindExtractor();
                    continue;
                } else {
                    flags = AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
                }
            }
            // synthesize timestamps based on required fps
            int64_t timeStampUs = 1e6 / mFps * mFrameCount;
            AMediaCodec_queueInputBuffer(mDec.get(), bufidx, 0, sampleSize, timeStampUs, flags);

            AMediaExtractor_advance(mEx.get());
            ++mFrameCount;
        }

        AMediaCodecBufferInfo info;
        int status = AMediaCodec_dequeueOutputBuffer(mDec.get(), &info, 1000);
        if (status >= 0) {
            ALOGV("got decoded buffer of size=%d @%lld us",
                    info.size, (long long)info.presentationTimeUs);
            bool render = info.size > 0;
            if (mBufListener != nullptr) {
                //TBD
                //mBufListener->onBufferAvailable(..);
            }

            // WA: if decoder runs free, dynamic settings applied by
            //     MediaCodec.setParameters() are off
            if (mRegulateFramerate) {
                usleep(1e6/mFps);
            }

            AMediaCodec_releaseOutputBuffer(mDec.get(), status, render);

            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                ALOGV("saw EOS");
                break;
            }

        } else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
        } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            mFormat = std::shared_ptr<AMediaFormat>(
                    AMediaCodec_getOutputFormat(mDec.get()), deleter_AMediaFormat);
            ALOGV("format changed: %s", AMediaFormat_toString(mFormat.get()));
        } else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
        } else {
            ALOGV("Invalid status : %d", status);
        }
    }
}

Status DecoderSource::prepare(
        std::shared_ptr<Listener> l, std::shared_ptr<ANativeWindow> n) {

    mBufListener = l;
    mSurface = n;

    if (mVideoTrackIndex < 0) {
        ALOGE("Video track not found !");
        return FAIL;
    }

    assert(mEx.get() != nullptr);
    AMediaExtractor_selectTrack(mEx.get(), mVideoTrackIndex);

    AMediaCodec *dec = AMediaCodec_createDecoderByType(mMime.c_str());
    mDec = std::shared_ptr<AMediaCodec>(dec, deleter_AMediaCodec);

    ALOGI("configure decoder. surface = %p", mSurface.get());
    media_status_t status = AMediaCodec_configure(
            mDec.get(), mFormat.get(), mSurface.get(), NULL /* crypto */, 0);
    if (status != AMEDIA_OK) {
        ALOGE("failed to configure decoder");
        return FAIL;
    }
    return OK;
}

Status DecoderSource::start() {
    ALOGV("start");
    media_status_t status = AMediaCodec_start(mDec.get());
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

Status DecoderSource::stop() {
    if (!mStarted) {
        return FAIL;
    }

    ALOGV("Stopping decoder source..");
    mStopRequest = true;
    joinThread();

    media_status_t status = AMediaCodec_stop(mDec.get());
    if (status != AMEDIA_OK) {
        ALOGE("failed to stop decoder");
    }

    mDec = nullptr;
    mEx = nullptr;
    mFormat = nullptr;
    return OK;
}

void DecoderSource::rewindExtractor() {
    assert(mEx.get() != nullptr);
    media_status_t status = AMediaExtractor_seekTo(mEx.get(), 0, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
    if (status != AMEDIA_OK) {
        ALOGE("failed to seek Extractor to 0");
    }
}

