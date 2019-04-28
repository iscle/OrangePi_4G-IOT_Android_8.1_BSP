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
#define LOG_TAG "MtkMediaPuller"
#include <utils/Log.h>

#include "MtkMediaPuller.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include "DataPathTrace.h"
#include <cutils/properties.h>

namespace android {

MtkMediaPuller::MtkMediaPuller(
        const sp<MediaSource> &source, const sp<AMessage> &notify)
    : mSource(source),
      mNotify(notify),
      mPullGeneration(0),
      mIsAudio(false),
      mPaused(false) {
    sp<MetaData> meta = source->getFormat();
    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    mIsAudio = !strncasecmp(mime, "audio/", 6);

    mFirstDeltaMs = -1;

    mWFDFrameLog = false;
    char log_en[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.WFDFrameLog", log_en, NULL)) {
        if (!strcmp(log_en, "1")) {
            mWFDFrameLog = true;
        }
    }
}

MtkMediaPuller::~MtkMediaPuller() {
}

status_t MtkMediaPuller::postSynchronouslyAndReturnError(
        const sp<AMessage> &msg) {
    sp<AMessage> response;
    status_t err = msg->postAndAwaitResponse(&response);

    if (err != OK) {
        return err;
    }

    if (!response->findInt32("err", &err)) {
        err = OK;
    }

    return err;
}

status_t MtkMediaPuller::start() {
    return postSynchronouslyAndReturnError(new AMessage(kWhatStart, this));
}

void MtkMediaPuller::stopAsync(const sp<AMessage> &notify) {
    sp<AMessage> msg = new AMessage(kWhatStop, this);
    msg->setMessage("notify", notify);
    msg->post();
}

void MtkMediaPuller::pause() {
    (new AMessage(kWhatPause, this))->post();
}

void MtkMediaPuller::resume() {
    (new AMessage(kWhatResume, this))->post();
}

void MtkMediaPuller::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatStart:
        {
            status_t err;
            if (mIsAudio) {
                // This atrocity causes AudioSource to deliver absolute
                // systemTime() based timestamps (off by 1 us).
                sp<MetaData> params = new MetaData;
                params->setInt64(kKeyTime, 1ll);
                err = mSource->start(params.get());
            } else {
                err = mSource->start();
                if (err != OK) {
                    ALOGE("source failed to start w/ err %d", err);
                }
            }

            if (err == OK) {
                schedulePull();
            }

            sp<AMessage> response = new AMessage;
            response->setInt32("err", err);

            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));
            response->postReply(replyID);
            break;
        }

        case kWhatStop:
        {
            sp<MetaData> meta = mSource->getFormat();
            const char *tmp;
            CHECK(meta->findCString(kKeyMIMEType, &tmp));
            AString mime = tmp;

            ALOGI("MtkMediaPuller(%s) stopping.", mime.c_str());
            mSource->stop();
            ALOGI("MtkMediaPuller(%s) stopped.", mime.c_str());
            ++mPullGeneration;

            sp<AMessage> notify;
            CHECK(msg->findMessage("notify", &notify));
            notify->post();
            break;
        }

        case kWhatPull:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));

            if (generation != mPullGeneration) {
                break;
            }

            MediaBuffer *mbuf;
            status_t err = mSource->read(&mbuf);

            if (mPaused) {
                if (err == OK) {
                    int64_t timeUs;
                    CHECK(mbuf->meta_data()->findInt64(kKeyTime, &timeUs));
                    sp<WfdDebugInfo> debugInfo = defaultWfdDebugInfo();
                    debugInfo->removeTimeInfoByKey(1, timeUs);
                    mbuf->release();
                    mbuf = NULL;
                }

                schedulePull();
                break;
            }

            if (err != OK) {
                if (err == 1) {
                    //  ALOGI("VP Repeat Buffer, do not pass it to encoder");
                    schedulePull();
                    break;
                }
                if (err == ERROR_END_OF_STREAM) {
                    ALOGI("stream ended.");
                } else {
                    ALOGE("error %d reading stream.", err);
                }

                sp<AMessage> notify = mNotify->dup();
                notify->setInt32("what", kWhatEOS);
                notify->post();
            } else {
                int64_t timeUs;
                CHECK(mbuf->meta_data()->findInt64(kKeyTime, &timeUs));

                sp<ABuffer> accessUnit = new ABuffer(mbuf->range_length());

                memcpy(accessUnit->data(),
                       (const uint8_t *)mbuf->data() + mbuf->range_offset(),
                       mbuf->range_length());

                accessUnit->meta()->setInt64("timeUs", timeUs);

                if(mWFDFrameLog == true){
                    ALOGD("puller get source mIsAudio=%d ts=%lld", mIsAudio, (long long)timeUs);
                }

                if (mIsAudio) {
                    static int64_t lasttime = 0;
                    int64_t ntime = ALooper::GetNowUs();
                    if ((ntime - lasttime) > 50000)
                        ALOGE("#### now lasttime=%lld time=%lld  timeUs=%lld\n",
                             (long long)lasttime, (long long)ntime, (long long)timeUs);
                    lasttime = ntime;
                }
                read_pro(!mIsAudio, timeUs, mbuf, accessUnit);
                if (mIsAudio) {
                    mbuf->release();
                    mbuf = NULL;
                } else {
                    // video encoder will release MediaBuffer when done
                    // with underlying data.
                    accessUnit->setMediaBufferBase(mbuf);
                }

                sp<AMessage> notify = mNotify->dup();

                notify->setInt32("what", kWhatAccessUnit);
                notify->setBuffer("accessUnit", accessUnit);
                notify->post();

                if (mbuf != NULL) {
                    ALOGV("posted mbuf %p", mbuf);
                }

                schedulePull();
            }
            break;
        }

        case kWhatPause:
        {
            mPaused = true;
            break;
        }

        case kWhatResume:
        {
            mPaused = false;
            break;
        }

        default:
            TRESPASS();
    }
}

void MtkMediaPuller::schedulePull() {
    sp<AMessage> msg = new AMessage(kWhatPull, this);
    msg->setInt32("generation", mPullGeneration);
    msg->post();
}


void MtkMediaPuller::read_pro(bool /*isVideo*/, int64_t timeUs, MediaBuffer *mbuf, const sp<ABuffer>& Abuf) {
    int32_t latencyToken = 0;
    if (mbuf->meta_data()->findInt32(kKeyWFDLatency, &latencyToken)) {
        Abuf->meta()->setInt32("LatencyToken", latencyToken);
    }

    sp<WfdDebugInfo> debugInfo = defaultWfdDebugInfo();
    int64_t MpMs = ALooper::GetNowUs();
    if (mIsAudio)
        debugInfo->addTimeInfoByKey(!mIsAudio , timeUs, "MpIn", MpMs/1000);

    int64_t NowMpDelta = 0;

    NowMpDelta = (MpMs - timeUs)/1000;

    if (mFirstDeltaMs == -1) {
        mFirstDeltaMs = NowMpDelta;
        ALOGE("[check Input ts and nowUs delta][%s],timestamp=%lld ms,[1th delta]=%lld ms",
        mIsAudio?"audio":"video", (long long)(timeUs/1000), (long long)NowMpDelta);
    }
    NowMpDelta = NowMpDelta - mFirstDeltaMs;

    if (mIsAudio) {
        if (NowMpDelta > 60ll || NowMpDelta < -60ll) {
            ALOGE("[check Input ts and nowUs delta][%s] ,timestamp=%lld ms,[delta]=%lld ms",
            mIsAudio?"audio":"video", (long long)(timeUs/1000), (long long)NowMpDelta);
        }
    } else {
        if (NowMpDelta > 30ll || NowMpDelta < -30ll) {
            ALOGE("[check Input ts and nowUs delta][%s] ,timestamp=%lld ms,[delta]=%lld ms",
            mIsAudio?"audio":"video", (long long)(timeUs/1000), (long long)NowMpDelta);
        }
    }
}

}  // namespace android
