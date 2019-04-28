/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MtkConverter"
#include <utils/Log.h>

#include "MtkConverter.h"

#include "MtkMediaPuller.h"
#include "include/avc_utils.h"

#include <cutils/properties.h>
#include <gui/Surface.h>
#include <media/ICrypto.h>
#include <media/MediaCodecBuffer.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>

#include <arpa/inet.h>

#include <OMX_Video.h>

#include <dlfcn.h>
#include "DataPathTrace.h"
#include "MtkWifiDisplaySource.h"
#define MAX_VIDEO_QUEUE_BUFFER (3)
#define MAX_AUDIO_QUEUE_BUFFER (5)
#define WFD_LOGI(fmt, arg...) ALOGI(fmt, ##arg)


namespace android {

MtkConverter::MtkConverter(
        const sp<AMessage> &notify,
        const sp<ALooper> &codecLooper,
        const sp<AMessage> &outputFormat,
        uint32_t flags)
    : mNotify(notify),
      mCodecLooper(codecLooper),
      mOutputFormat(outputFormat),
      mFlags(flags),
      mIsVideo(false),
      mIsH264(false),
      mIsPCMAudio(false),
      mNeedToManuallyPrependSPSPPS(false),
      mDoMoreWorkPending(false)
#if ENABLE_SILENCE_DETECTION
      ,mFirstSilentFrameUs(-1ll)
      ,mInSilentMode(false)
#endif
      ,mPrevVideoBitrate(-1)
      ,mNumFramesToDrop(0)
      ,mEncodingSuspended(false)
    {
    AString mime;
    CHECK(mOutputFormat->findString("mime", &mime));

    if (!strncasecmp("video/", mime.c_str(), 6)) {
        mIsVideo = true;

        mIsH264 = !strcasecmp(mime.c_str(), MEDIA_MIMETYPE_VIDEO_AVC);
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_RAW, mime.c_str())) {
        mIsPCMAudio = true;
    }


    mWFDFrameLog = false;
    char log_en[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.WFDFrameLog", log_en, NULL)) {
        if (!strcmp(log_en, "1")) {
            mWFDFrameLog = true;
        }
    }
}

void MtkConverter::releaseEncoder() {
    if (mEncoder == NULL) {
        return;
    }

    mEncoder->release();
    mEncoder.clear();

    mInputBufferQueue.clear();
    mEncoderInputBuffers.clear();
    mEncoderOutputBuffers.clear();
}

MtkConverter::~MtkConverter() {
    releaseEncoder();  //  release mEncoder
    CHECK(mEncoder == NULL);
}

void MtkConverter::shutdownAsync() {
    ALOGV("shutdown");
    (new AMessage(kWhatShutdown, this))->post();
}

status_t MtkConverter::init() {
    status_t err = initEncoder();

    if (err != OK) {
        releaseEncoder();
    }

    return err;
}

sp<IGraphicBufferProducer> MtkConverter::getGraphicBufferProducer() {
    CHECK(mFlags & FLAG_USE_SURFACE_INPUT);
    return mGraphicBufferProducer;
}

size_t MtkConverter::getInputBufferCount() const {
    return mEncoderInputBuffers.size();
}

sp<AMessage> MtkConverter::getOutputFormat() const {
    return mOutputFormat;
}

bool MtkConverter::needToManuallyPrependSPSPPS() const {
    return mNeedToManuallyPrependSPSPPS;
}

// static
int32_t MtkConverter::GetInt32Property(
        const char *propName, int32_t defaultValue) {
    char val[PROPERTY_VALUE_MAX];
    if (property_get(propName, val, NULL)) {
        char *end;
        unsigned long x = strtoul(val, &end, 10);

        if (*end == '\0' && end > val && x > 0) {
            return x;
        }
    }

    return defaultValue;
}

status_t MtkConverter::initEncoder() {
    AString outputMIME;
    CHECK(mOutputFormat->findString("mime", &outputMIME));

    bool isAudio = !strncasecmp(outputMIME.c_str(), "audio/", 6);

    if (!mIsPCMAudio) {
        mEncoder = MediaCodec::CreateByType(
                mCodecLooper, outputMIME.c_str(), true /* encoder */);

        if (mEncoder == NULL) {
            return ERROR_UNSUPPORTED;
        }
    }

    if (mIsPCMAudio) {
        return OK;
    }

    int32_t audioBitrate = GetInt32Property("media.wfd.audio-bitrate", 128000);
    int32_t videoBitrate = GetInt32Property("media.wfd.video-bitrate", 5000000);
    mPrevVideoBitrate = videoBitrate;

    ALOGI("using audio bitrate of %d bps, video bitrate of %d bps",
          audioBitrate, videoBitrate);

    if (isAudio) {
        mOutputFormat->setInt32("bitrate", audioBitrate);
        mOutputFormat->setInt32("isWfdMode", 1);
    } else {
        mOutputFormat->setInt32("bitrate", videoBitrate);
        mOutputFormat->setInt32("bitrate-mode", OMX_Video_ControlRateConstant);

        mOutputFormat->setInt32("i-frame-interval", 15);  // Iframes every 15 secs

        // Configure encoder to use intra macroblock refresh mode
        mOutputFormat->setInt32("intra-refresh-mode", OMX_VIDEO_IntraRefreshCyclic);

        int width, height, mbs;
        if (!mOutputFormat->findInt32("width", &width)
                || !mOutputFormat->findInt32("height", &height)) {
            return ERROR_UNSUPPORTED;
        }

        // Update macroblocks in a cyclic fashion with 10% of all MBs within
        // frame gets updated at one time. It takes about 10 frames to
        // completely update a whole video frame. If the frame rate is 30,
        // it takes about 333 ms in the best case (if next frame is not an IDR)
        // to recover from a lost/corrupted packet.
        mbs = (((width + 15) / 16) * ((height + 15) / 16) * 10) / 100;
        mOutputFormat->setInt32("intra-refresh-CIR-mbs", mbs);
        initEncoder_ext();

    }

    ALOGV("output format is '%s'", mOutputFormat->debugString(0).c_str());

    mNeedToManuallyPrependSPSPPS = false;

    status_t err = NO_INIT;

    if (!isAudio) {
        sp<AMessage> tmp = mOutputFormat->dup();
        tmp->setInt32("prepend-sps-pps-to-idr-frames", 1);

        err = mEncoder->configure(
                tmp,
                NULL /* nativeWindow */,
                NULL /* crypto */,
                MediaCodec::CONFIGURE_FLAG_ENCODE);

        if (err == OK) {
            // Encoder supported prepending SPS/PPS, we don't need to emulate
            // it.
            mOutputFormat = tmp;
        } else {
            mNeedToManuallyPrependSPSPPS = true;
            int32_t store_metadata_in_buffers_output = 0;
            if (mOutputFormat->findInt32("store-metadata-in-buffers-output", &store_metadata_in_buffers_output)
                     && store_metadata_in_buffers_output ==1) {
                ALOGE("!!![error]Codec not support prepend-sps-pps-to-idr-frames while store-metadata-in-buffers-output!!!");
                mNeedToManuallyPrependSPSPPS = false;
            }

            ALOGI("We going to manually prepend SPS and PPS to IDR frames.");
        }
    }

    if (err != OK) {
        // We'll get here for audio or if we failed to configure the encoder
        // to automatically prepend SPS/PPS in the case of video.

        err = mEncoder->configure(
                    mOutputFormat,
                    NULL /* nativeWindow */,
                    NULL /* crypto */,
                    MediaCodec::CONFIGURE_FLAG_ENCODE);
    }

    if (err != OK) {
        return err;
    }

    if (mFlags & FLAG_USE_SURFACE_INPUT) {
        CHECK(mIsVideo);

        err = mEncoder->createInputSurface(&mGraphicBufferProducer);

        if (err != OK) {
            return err;
        }
    }

    err = mEncoder->start();

    if (err != OK) {
        return err;
    }

    err = mEncoder->getInputBuffers(&mEncoderInputBuffers);

    if (err != OK) {
        return err;
    }

    err = mEncoder->getOutputBuffers(&mEncoderOutputBuffers);

    if (err != OK) {
        return err;
    }

    if (mFlags & FLAG_USE_SURFACE_INPUT) {
        scheduleDoMoreWork();
    }

    return OK;
}

void MtkConverter::notifyError(status_t err) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatError);
    notify->setInt32("err", err);
    notify->post();
}

// static
bool MtkConverter::IsSilence(const sp<ABuffer> &accessUnit) {
    const uint8_t *ptr = accessUnit->data();
    const uint8_t *end = ptr + accessUnit->size();
    while (ptr < end) {
        if (*ptr != 0) {
            return false;
        }
        ++ptr;
    }

    return true;
}

void MtkConverter::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatMediaPullerNotify:
        {
            int32_t what;
            CHECK(msg->findInt32("what", &what));

            if (!mIsPCMAudio && mEncoder == NULL) {
                ALOGV("got msg '%s' after encoder shutdown.",
                      msg->debugString().c_str());

                if (what == MtkMediaPuller::kWhatAccessUnit) {
                    sp<ABuffer> accessUnit;
                    CHECK(msg->findBuffer("accessUnit", &accessUnit));

                    accessUnit->setMediaBufferBase(NULL);
                }
                break;
            }

            if (what == MtkMediaPuller::kWhatEOS) {
                mInputBufferQueue.push_back(NULL);

                feedEncoderInputBuffers();

                scheduleDoMoreWork();
            } else {
                CHECK_EQ(what, MtkMediaPuller::kWhatAccessUnit);

                sp<ABuffer> accessUnit;
                CHECK(msg->findBuffer("accessUnit", &accessUnit));

                if (mNumFramesToDrop > 0 || mEncodingSuspended) {
                    if (mNumFramesToDrop > 0) {
                        --mNumFramesToDrop;
                        ALOGI("dropping frame.");
                    }

                    accessUnit->setMediaBufferBase(NULL);
                    break;
                }

#if 0
                MediaBuffer *mbuf =
                    (MediaBuffer *)(accessUnit->getMediaBufferBase());
                if (mbuf != NULL) {
                    ALOGI("queueing mbuf %p", mbuf);
                    mbuf->release();
                }
#endif

#if ENABLE_SILENCE_DETECTION
                if (!mIsVideo) {
                    if (IsSilence(accessUnit)) {
                        if (mInSilentMode) {
                            break;
                        }

                        int64_t nowUs = ALooper::GetNowUs();

                        if (mFirstSilentFrameUs < 0ll) {
                            mFirstSilentFrameUs = nowUs;
                        } else if (nowUs >= mFirstSilentFrameUs + 10000000ll) {
                            mInSilentMode = true;
                            ALOGI("audio in silent mode now.");
                            break;
                        }
                    } else {
                        if (mInSilentMode) {
                            ALOGI("audio no longer in silent mode.");
                        }
                        mInSilentMode = false;
                        mFirstSilentFrameUs = -1ll;
                    }
                }
#endif

                uint32_t maxSize = mIsVideo ? MAX_VIDEO_QUEUE_BUFFER : MAX_AUDIO_QUEUE_BUFFER;
                if (mInputBufferQueue.size() >= maxSize) {  //  for rsss warning as pull new buffer
                    sp<ABuffer> tmpbuffer = *mInputBufferQueue.begin();
                    tmpbuffer->setMediaBufferBase(NULL);
                    if (!mIsVideo)
                        ALOGI("[audio buffer]audio queuebuffer >= %d release oldest buffer", maxSize);
                    else
                        ALOGI("[vedio buffer]audio queuebuffer >= %d release oldest buffer", maxSize);

                    mInputBufferQueue.erase(mInputBufferQueue.begin());
                }

                mInputBufferQueue.push_back(accessUnit);

                feedEncoderInputBuffers();

                scheduleDoMoreWork();
            }
            break;
        }

        case kWhatEncoderActivity:
        {
#if 0
            int64_t whenUs;
            if (msg->findInt64("whenUs", &whenUs)) {
                int64_t nowUs = ALooper::GetNowUs();
                ALOGI("[%s] kWhatEncoderActivity after %lld us",
                      mIsVideo ? "video" : "audio", nowUs - whenUs);
            }
#endif

            mDoMoreWorkPending = false;

            if (mEncoder == NULL) {
                break;
            }

            status_t err = doMoreWork();

            if (err != OK) {
                notifyError(err);
            } else {
                scheduleDoMoreWork();
            }
            break;
        }

        case kWhatRequestIDRFrame:
        {
            if (mEncoder == NULL) {
                break;
            }

            if (mIsVideo) {

                ALOGI("requesting IDR frame");

                mEncoder->requestIDRFrame();
            }
            break;
        }

        case kWhatShutdown:
        {
            ALOGI("shutting down %s encoder", mIsVideo ? "video" : "audio");

            releaseEncoder();

            AString mime;
            CHECK(mOutputFormat->findString("mime", &mime));
            ALOGI("encoder (%s) shut down.", mime.c_str());

            sp<AMessage> notify = mNotify->dup();
            notify->setInt32("what", kWhatShutdownCompleted);
            notify->post();
            break;
        }

        case kWhatDropAFrame:
        {
            ++mNumFramesToDrop;
            break;
        }

        case kWhatReleaseOutputBuffer:
        {
            if (mEncoder != NULL) {
                size_t bufferIndex;
                CHECK(msg->findInt32("bufferIndex", (int32_t*)&bufferIndex));
                CHECK(bufferIndex < mEncoderOutputBuffers.size());
                mEncoder->releaseOutputBuffer(bufferIndex);
            }
            break;
        }

        case kWhatSuspendEncoding:
        {
            int32_t suspend;
            CHECK(msg->findInt32("suspend", &suspend));

            mEncodingSuspended = suspend;

            if (mFlags & FLAG_USE_SURFACE_INPUT) {
                sp<AMessage> params = new AMessage;
                params->setInt32("drop-input-frames",suspend);
                mEncoder->setParameters(params);
            }
            break;
        }

        default:
            TRESPASS();
    }
}

void MtkConverter::scheduleDoMoreWork() {
    if (mIsPCMAudio) {
        // There's no encoder involved in this case.
        return;
    }

    if (mDoMoreWorkPending) {
        return;
    }

    mDoMoreWorkPending = true;

#if 1
    if (mEncoderActivityNotify == NULL) {
        mEncoderActivityNotify = new AMessage(kWhatEncoderActivity, this);
    }
    mEncoder->requestActivityNotification(mEncoderActivityNotify->dup());
#else
    sp<AMessage> notify = new AMessage(kWhatEncoderActivity, this);
    notify->setInt64("whenUs", ALooper::GetNowUs());
    mEncoder->requestActivityNotification(notify);
#endif
}

status_t MtkConverter::feedRawAudioInputBuffers() {
    // Split incoming PCM audio into buffers of 6 AUs of 80 audio frames each
    // and add a 4 byte header according to the wifi display specs.

    while (!mInputBufferQueue.empty()) {
        sp<ABuffer> buffer = *mInputBufferQueue.begin();
        mInputBufferQueue.erase(mInputBufferQueue.begin());

        int16_t *ptr = (int16_t *)buffer->data();
        int16_t *stop = (int16_t *)(buffer->data() + buffer->size());
        while (ptr < stop) {
            *ptr = htons(*ptr);
            ++ptr;
        }

        static const size_t kFrameSize = 2 * sizeof(int16_t);  // stereo
        static const size_t kFramesPerAU = 80;
        static const size_t kNumAUsPerPESPacket = 6;

        if (mPartialAudioAU != NULL) {
            size_t bytesMissingForFullAU =
                kNumAUsPerPESPacket * kFramesPerAU * kFrameSize
                - mPartialAudioAU->size() + 4;

            size_t copy = buffer->size();
            if(copy > bytesMissingForFullAU) {
                copy = bytesMissingForFullAU;
            }

            memcpy(mPartialAudioAU->data() + mPartialAudioAU->size(),
                   buffer->data(),
                   copy);

            mPartialAudioAU->setRange(0, mPartialAudioAU->size() + copy);

            buffer->setRange(buffer->offset() + copy, buffer->size() - copy);

            int64_t timeUs;
            CHECK(buffer->meta()->findInt64("timeUs", &timeUs));

            int64_t copyUs = (int64_t)((copy / kFrameSize) * 1E6 / 48000.0);
            timeUs += copyUs;
            buffer->meta()->setInt64("timeUs", timeUs);

            if (bytesMissingForFullAU == copy) {
                sp<AMessage> notify = mNotify->dup();
                notify->setInt32("what", kWhatAccessUnit);
                notify->setBuffer("accessUnit", mPartialAudioAU);
                notify->post();

                mPartialAudioAU.clear();
            }
        }

        while (buffer->size() > 0) {
            sp<ABuffer> partialAudioAU =
                new ABuffer(
                        4
                        + kNumAUsPerPESPacket * kFrameSize * kFramesPerAU);

            uint8_t *ptr = partialAudioAU->data();
            ptr[0] = 0xa0;  // 10100000b
            ptr[1] = kNumAUsPerPESPacket;
            ptr[2] = 0;  // reserved, audio _emphasis_flag = 0

            static const unsigned kQuantizationWordLength = 0;  // 16-bit
            static const unsigned kAudioSamplingFrequency = 2;  // 48Khz
            static const unsigned kNumberOfAudioChannels = 1;  // stereo

            ptr[3] = (kQuantizationWordLength << 6)
                    | (kAudioSamplingFrequency << 3)
                    | kNumberOfAudioChannels;

            size_t copy = buffer->size();
            if (copy > partialAudioAU->size() - 4) {
                copy = partialAudioAU->size() - 4;
            }

            memcpy(&ptr[4], buffer->data(), copy);

            partialAudioAU->setRange(0, 4 + copy);
            buffer->setRange(buffer->offset() + copy, buffer->size() - copy);

            int64_t timeUs;
            CHECK(buffer->meta()->findInt64("timeUs", &timeUs));

            partialAudioAU->meta()->setInt64("timeUs", timeUs);

            int64_t copyUs = (int64_t)((copy / kFrameSize) * 1E6 / 48000.0);
            timeUs += copyUs;
            buffer->meta()->setInt64("timeUs", timeUs);

            if (copy == partialAudioAU->capacity() - 4) {
                sp<AMessage> notify = mNotify->dup();
                notify->setInt32("what", kWhatAccessUnit);
                notify->setBuffer("accessUnit", partialAudioAU);
                notify->post();

                partialAudioAU.clear();
                continue;
            }

            mPartialAudioAU = partialAudioAU;
        }
    }

    return OK;
}

status_t MtkConverter::feedEncoderInputBuffers() {
    if (mIsPCMAudio) {
        return feedRawAudioInputBuffers();
    }

    while (!mInputBufferQueue.empty()
            && !mAvailEncoderInputIndices.empty()) {
        sp<ABuffer> buffer = *mInputBufferQueue.begin();
        mInputBufferQueue.erase(mInputBufferQueue.begin());

        size_t bufferIndex = *mAvailEncoderInputIndices.begin();
        mAvailEncoderInputIndices.erase(mAvailEncoderInputIndices.begin());

        int64_t timeUs = 0ll;
        uint32_t flags = 0;

        if (buffer != NULL) {
            CHECK(buffer->meta()->findInt64("timeUs", &timeUs));

            memcpy(mEncoderInputBuffers.itemAt(bufferIndex)->data(),
                   buffer->data(),
                   buffer->size());

            MediaBuffer *mediaBuffer =
                (MediaBuffer *)(buffer->getMediaBufferBase());
            if (mediaBuffer != NULL) {
                mEncoderInputBuffers.itemAt(bufferIndex)->setMediaBufferBase(
                        mediaBuffer);

                buffer->setMediaBufferBase(NULL);
            }
        } else {
            flags = MediaCodec::BUFFER_FLAG_EOS;
        }

        if(mWFDFrameLog == true){
            ALOGD("converter queueInputBuffer mIsVideo=%d ts=%lld", mIsVideo, (long long)timeUs);
        }

        status_t err = mEncoder->queueInputBuffer(
                bufferIndex, 0, (buffer == NULL) ? 0 : buffer->size(),
                timeUs, flags);

        if (err != OK) {
            return err;
        }
    }

    return OK;
}

sp<ABuffer> MtkConverter::prependCSD(const sp<ABuffer> &accessUnit) const {
    CHECK(mCSD0 != NULL);

    sp<ABuffer> dup = new ABuffer(accessUnit->size() + mCSD0->size());
    memcpy(dup->data(), mCSD0->data(), mCSD0->size());
    memcpy(dup->data() + mCSD0->size(), accessUnit->data(), accessUnit->size());

    int64_t timeUs;
    CHECK(accessUnit->meta()->findInt64("timeUs", &timeUs));

    dup->meta()->setInt64("timeUs", timeUs);

    return dup;
}

status_t MtkConverter::doMoreWork() {
    status_t err;

    if (!(mFlags & FLAG_USE_SURFACE_INPUT)) {
        for (;;) {
            size_t bufferIndex;
            err = mEncoder->dequeueInputBuffer(&bufferIndex);

            if (err != OK) {
                break;
            }

            mAvailEncoderInputIndices.push_back(bufferIndex);
        }

        feedEncoderInputBuffers();
    }

    for (;;) {
        size_t bufferIndex;
        size_t offset;
        size_t size;
        int64_t timeUs;
        uint32_t flags;
        native_handle_t* handle = NULL;
        err = mEncoder->dequeueOutputBuffer(
                &bufferIndex, &offset, &size, &timeUs, &flags);

        if(err == OK && mWFDFrameLog == true ){
            ALOGD("converter dequeueOutputBuffer mIsVideo=%d ts=%lld size=%zu flag=%d",
                   mIsVideo, (long long)timeUs, size, flags);
        }

        if (err != OK) {
            if (err == INFO_FORMAT_CHANGED) {
                continue;
            } else if (err == INFO_OUTPUT_BUFFERS_CHANGED) {
                mEncoder->getOutputBuffers(&mEncoderOutputBuffers);
                continue;
            }

            if (err == -EAGAIN) {
                err = OK;
            }
            break;
        }

        if (flags & MediaCodec::BUFFER_FLAG_EOS) {
            sp<AMessage> notify = mNotify->dup();
            notify->setInt32("what", kWhatEOS);
            notify->post();
        } else {
#if 0
            if (mIsVideo) {
                int32_t videoBitrate = GetInt32Property(
                        "media.wfd.video-bitrate", 5000000);

                setVideoBitrate(videoBitrate);
            }
#endif

            sp<ABuffer> buffer;
            sp<MediaCodecBuffer> outbuf = mEncoderOutputBuffers.itemAt(bufferIndex);

            if (outbuf->meta()->findPointer("handle", (void**)&handle) &&
                    handle != NULL) {
                int32_t rangeLength, rangeOffset;
                CHECK(outbuf->meta()->findInt32("rangeOffset", &rangeOffset));
                CHECK(outbuf->meta()->findInt32("rangeLength", &rangeLength));
                outbuf->meta()->setPointer("handle", NULL);

                // MediaSender will post the following message when HDCP
                // is done, to release the output buffer back to encoder.
                sp<AMessage> notify(new AMessage(kWhatReleaseOutputBuffer, this));
                notify->setInt32("bufferIndex", bufferIndex);

                buffer = new ABuffer(
                        rangeLength > (int32_t)size ? rangeLength : size);
                buffer->meta()->setPointer("handle", handle);
                buffer->meta()->setInt32("rangeOffset", rangeOffset);
                buffer->meta()->setInt32("rangeLength", rangeLength);
                buffer->meta()->setMessage("notify", notify);
            } else {
                buffer = new ABuffer(size);
                memcpy(buffer->data(), outbuf->base() + offset, size);
            }

            buffer->meta()->setInt64("timeUs", timeUs);

            ALOGV("[%s] time %lld us (%.2f secs)",
                    mIsVideo ? "video" : "audio", (long long)timeUs, timeUs / 1E6);

            doMoreWork_p(buffer, outbuf, timeUs, flags);

            if (flags & MediaCodec::BUFFER_FLAG_CODECCONFIG) {
                if (!handle) {
                    if (mIsH264) {
                        mCSD0 = buffer;
                    }
                    mOutputFormat->setBuffer("csd-0", buffer);
                }
            } else {
                if (mNeedToManuallyPrependSPSPPS
                        && mIsH264
                        && (mFlags & FLAG_PREPEND_CSD_IF_NECESSARY)
                        && IsIDR(buffer)) {
                    buffer = prependCSD(buffer);
                }

                sp<AMessage> notify = mNotify->dup();
                notify->setInt32("what", kWhatAccessUnit);
                notify->setBuffer("accessUnit", buffer);
                notify->post();
            }
        }

        if (!handle) {
            mEncoder->releaseOutputBuffer(bufferIndex);
        }

        if (flags & MediaCodec::BUFFER_FLAG_EOS) {
            break;
        }
    }

    return err;
}

void MtkConverter::requestIDRFrame() {
    (new AMessage(kWhatRequestIDRFrame, this))->post();
}

void MtkConverter::dropAFrame() {
    // Unsupported in surface input mode.
    CHECK(!(mFlags & FLAG_USE_SURFACE_INPUT));

    (new AMessage(kWhatDropAFrame, this))->post();
}

void MtkConverter::suspendEncoding(bool suspend) {
    sp<AMessage> msg = new AMessage(kWhatSuspendEncoding, this);
    msg->setInt32("suspend", suspend);
    msg->post();
}

int32_t MtkConverter::getVideoBitrate() const {
    return mPrevVideoBitrate;
}

void MtkConverter::setVideoBitrate(int32_t bitRate) {
    if (mIsVideo && mEncoder != NULL && bitRate != mPrevVideoBitrate) {
        sp<AMessage> params = new AMessage;
        params->setInt32("video-bitrate", bitRate);

        mEncoder->setParameters(params);

        mPrevVideoBitrate = bitRate;
    }
}


void MtkConverter::doMoreWork_p(const sp<ABuffer> &buffer, const sp<MediaCodecBuffer> &outbuf,
     const int64_t &timeUs,const uint32_t &flags) {
    sp<WfdDebugInfo> debugInfo = defaultWfdDebugInfo();
    int64_t latencyB = debugInfo->getTimeInfoByKey(mIsVideo, timeUs, mIsVideo?"RpIn":"MpIn");
    if (latencyB > 0) {
         if (!mIsVideo) {
             debugInfo->removeTimeInfoByKey(mIsVideo, timeUs);
         }
         buffer->meta()->setInt64("latencyB", latencyB);
    }
    if (flags & MediaCodec::BUFFER_FLAG_DUMMY) {
        buffer->meta()->setInt32("dummy-nal", 1);

        int64_t LatencyToken = -1;
        int32_t tmpToken = -1;
        if (outbuf->meta()->findInt32("LatencyToken", &tmpToken) && tmpToken  >= 0) {
            LatencyToken = static_cast<int64_t>(tmpToken);
        } else {
            LatencyToken = debugInfo->getTimeInfoByKey(mIsVideo, timeUs, mIsVideo?"LatencyToken":"No");
        }

        debugInfo->removeTimeInfoByKey(mIsVideo, timeUs);
        buffer->meta()->setInt64("LatencyToken", LatencyToken);
    }
}
void  MtkConverter::initEncoder_ext() {
    //  framerate
    int32_t frameRate = 0;
    if (mOutputFormat->findInt32("frame-rate", &frameRate) && frameRate > 0) {
        ALOGI("user set frameRate=%d", frameRate);
    } else {
        mOutputFormat->setInt32("frame-rate", 30);
        frameRate = 30;
    }
    mOutputFormat->setInt32("frame-rate", frameRate);

    //  I interval
    mOutputFormat->setInt32("i-frame-interval", 4);  // Iframes every 15 secs

    //  bitrate,W-H
    int width = 0;
    int height = 0;
    mOutputFormat->findInt32("width", &width);
    mOutputFormat->findInt32("height", &height);

    int32_t video_bitrate = 4800000;
    if ( width >= 1920 && height >= 1080 ) {
        video_bitrate = 11000000;
        ALOGI("change video_bitrate to %d as 1080P ", video_bitrate);
    }
    mOutputFormat->setInt32("bitrate", video_bitrate);

    //  property set
    char video_bitrate_char[PROPERTY_VALUE_MAX];

    if (property_get("media.wfd.video-bitrate", video_bitrate_char, NULL)) {
        int32_t temp_video_bitrate = atoi(video_bitrate_char);
        if (temp_video_bitrate > 0) {
            video_bitrate = temp_video_bitrate;
        }
    }
    mOutputFormat->setInt32("bitrate", video_bitrate);

    char frameRate_char[PROPERTY_VALUE_MAX];

    if (property_get("media.wfd.frame-rate", frameRate_char, NULL)) {
        int32_t temp_fps = atoi(frameRate_char);
        if ( temp_fps > 0 ) {
             mOutputFormat->setInt32("frame-rate", temp_fps);
        }
    }


    int32_t useSliceMode = 0;

    if (mOutputFormat->findInt32("slice-mode", &useSliceMode) && useSliceMode ==1) {
        ALOGI("useSliceMode =%d", useSliceMode);
        int32_t buffersize = 60*1024;

        char buffersize_value[PROPERTY_VALUE_MAX];
        if (property_get("wfd.slice.size", buffersize_value, NULL)) {
            buffersize = atoi(buffersize_value)*1024;
        }
        mOutputFormat->setInt32("outputbuffersize", buffersize);  // bs buffer size is 15k for slice mode
        ALOGI("slice mode: output buffer size=%d KB", buffersize/1024);
    }

    mOutputFormat->setInt32("inputbuffercnt", 4);  // yuv buffer count is 4
    mOutputFormat->setInt32("bitrate-mode", 0x7F000001);  //  OMX_Video_ControlRateMtkWFD
}


void  MtkConverter::forceBlackScreen(bool blackNow) {
    ALOGI("force black now blackNow=%d", blackNow);

    sp<AMessage> params = new AMessage;
    params->setInt32("drawBlack", (blackNow) ? 1 : 0);

    mEncoder->setParameters(params);
}


}  // namespace android
