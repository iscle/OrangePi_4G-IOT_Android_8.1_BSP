/*
 * Copyright (C) 2010 The Android Open Source Project
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
#define LOG_TAG "MPEG2TSExtractor"

#include <inttypes.h>
#include <utils/Log.h>

#include "include/MPEG2TSExtractor.h"
#include "include/NuCachedSource2.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>
#include <media/stagefright/foundation/AUtils.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/IStreamSource.h>
#include <utils/String8.h>

#include "AnotherPacketSource.h"
#include "ATSParser.h"

#include <hidl/HybridInterface.h>
#include <android/hardware/cas/1.0/ICas.h>

namespace android {

using hardware::cas::V1_0::ICas;

static const size_t kTSPacketSize = 188;
static const int kMaxDurationReadSize = 250000LL;
static const int kMaxDurationRetry = 6;

struct MPEG2TSSource : public MediaSource {
    MPEG2TSSource(
            const sp<MPEG2TSExtractor> &extractor,
            const sp<AnotherPacketSource> &impl,
            bool doesSeek);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

private:
    sp<MPEG2TSExtractor> mExtractor;
    sp<AnotherPacketSource> mImpl;

    // If there are both audio and video streams, only the video stream
    // will signal seek on the extractor; otherwise the single stream will seek.
    bool mDoesSeek;

    DISALLOW_EVIL_CONSTRUCTORS(MPEG2TSSource);
};

MPEG2TSSource::MPEG2TSSource(
        const sp<MPEG2TSExtractor> &extractor,
        const sp<AnotherPacketSource> &impl,
        bool doesSeek)
    : mExtractor(extractor),
      mImpl(impl),
      mDoesSeek(doesSeek) {
}

status_t MPEG2TSSource::start(MetaData *params) {
    return mImpl->start(params);
}

status_t MPEG2TSSource::stop() {
    return mImpl->stop();
}

sp<MetaData> MPEG2TSSource::getFormat() {
    return mImpl->getFormat();
}

status_t MPEG2TSSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode seekMode;
    if (mDoesSeek && options && options->getSeekTo(&seekTimeUs, &seekMode)) {
        // seek is needed
        status_t err = mExtractor->seek(seekTimeUs, seekMode);
        if (err != OK) {
            return err;
        }
    }

    if (mExtractor->feedUntilBufferAvailable(mImpl) != OK) {
        return ERROR_END_OF_STREAM;
    }

    return mImpl->read(out, options);
}

////////////////////////////////////////////////////////////////////////////////

MPEG2TSExtractor::MPEG2TSExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mParser(new ATSParser),
      mLastSyncEvent(0),
      mOffset(0) {
    init();
}

size_t MPEG2TSExtractor::countTracks() {
    return mSourceImpls.size();
}

sp<IMediaSource> MPEG2TSExtractor::getTrack(size_t index) {
    if (index >= mSourceImpls.size()) {
        return NULL;
    }

    // The seek reference track (video if present; audio otherwise) performs
    // seek requests, while other tracks ignore requests.
    return new MPEG2TSSource(this, mSourceImpls.editItemAt(index),
            (mSeekSyncPoints == &mSyncPoints.editItemAt(index)));
}

sp<MetaData> MPEG2TSExtractor::getTrackMetaData(
        size_t index, uint32_t /* flags */) {
    return index < mSourceImpls.size()
        ? mSourceImpls.editItemAt(index)->getFormat() : NULL;
}

sp<MetaData> MPEG2TSExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_MPEG2TS);

    return meta;
}

//static
bool MPEG2TSExtractor::isScrambledFormat(const sp<MetaData> &format) {
    const char *mime;
    return format->findCString(kKeyMIMEType, &mime)
            && (!strcasecmp(MEDIA_MIMETYPE_VIDEO_SCRAMBLED, mime)
                    || !strcasecmp(MEDIA_MIMETYPE_AUDIO_SCRAMBLED, mime));
}

status_t MPEG2TSExtractor::setMediaCas(const HInterfaceToken &casToken) {
    HalToken halToken;
    halToken.setToExternal((uint8_t*)casToken.data(), casToken.size());
    sp<ICas> cas = ICas::castFrom(retrieveHalInterface(halToken));
    ALOGD("setMediaCas: %p", cas.get());

    status_t err = mParser->setMediaCas(cas);
    if (err == OK) {
        ALOGI("All tracks now have descramblers");
        init();
    }
    return err;
}

void MPEG2TSExtractor::addSource(const sp<AnotherPacketSource> &impl) {
    bool found = false;
    for (size_t i = 0; i < mSourceImpls.size(); i++) {
        if (mSourceImpls[i] == impl) {
            found = true;
            break;
        }
    }
    if (!found) {
        mSourceImpls.push(impl);
    }
}

void MPEG2TSExtractor::init() {
    bool haveAudio = false;
    bool haveVideo = false;
    int64_t startTime = ALooper::GetNowUs();

    status_t err;
    while ((err = feedMore(true /* isInit */)) == OK
            || err == ERROR_DRM_DECRYPT_UNIT_NOT_INITIALIZED) {
        if (haveAudio && haveVideo) {
            addSyncPoint_l(mLastSyncEvent);
            mLastSyncEvent.reset();
            break;
        }
        if (!haveVideo) {
            sp<AnotherPacketSource> impl =
                (AnotherPacketSource *)mParser->getSource(
                        ATSParser::VIDEO).get();

            if (impl != NULL) {
                sp<MetaData> format = impl->getFormat();
                if (format != NULL) {
                    haveVideo = true;
                    addSource(impl);
                    if (!isScrambledFormat(format)) {
                        mSyncPoints.push();
                        mSeekSyncPoints = &mSyncPoints.editTop();
                    }
                }
            }
        }

        if (!haveAudio) {
            sp<AnotherPacketSource> impl =
                (AnotherPacketSource *)mParser->getSource(
                        ATSParser::AUDIO).get();

            if (impl != NULL) {
                sp<MetaData> format = impl->getFormat();
                if (format != NULL) {
                    haveAudio = true;
                    addSource(impl);
                    if (!isScrambledFormat(format)) {
                        mSyncPoints.push();
                        if (!haveVideo) {
                            mSeekSyncPoints = &mSyncPoints.editTop();
                        }
                    }
                }
            }
        }

        addSyncPoint_l(mLastSyncEvent);
        mLastSyncEvent.reset();

        // ERROR_DRM_DECRYPT_UNIT_NOT_INITIALIZED is returned when the mpeg2ts
        // is scrambled but we don't have a MediaCas object set. The extraction
        // will only continue when setMediaCas() is called successfully.
        if (err == ERROR_DRM_DECRYPT_UNIT_NOT_INITIALIZED) {
            ALOGI("stopped parsing scrambled content, "
                  "haveAudio=%d, haveVideo=%d, elaspedTime=%" PRId64,
                    haveAudio, haveVideo, ALooper::GetNowUs() - startTime);
            return;
        }

        // Wait only for 2 seconds to detect audio/video streams.
        if (ALooper::GetNowUs() - startTime > 2000000ll) {
            break;
        }
    }

    off64_t size;
    if (mDataSource->getSize(&size) == OK && (haveAudio || haveVideo)) {
        sp<AnotherPacketSource> impl = haveVideo
                ? (AnotherPacketSource *)mParser->getSource(
                        ATSParser::VIDEO).get()
                : (AnotherPacketSource *)mParser->getSource(
                        ATSParser::AUDIO).get();
        size_t prevSyncSize = 1;
        int64_t durationUs = -1;
        List<int64_t> durations;
        // Estimate duration --- stabilize until you get <500ms deviation.
        while (feedMore() == OK
                && ALooper::GetNowUs() - startTime <= 2000000ll) {
            if (mSeekSyncPoints->size() > prevSyncSize) {
                prevSyncSize = mSeekSyncPoints->size();
                int64_t diffUs = mSeekSyncPoints->keyAt(prevSyncSize - 1)
                        - mSeekSyncPoints->keyAt(0);
                off64_t diffOffset = mSeekSyncPoints->valueAt(prevSyncSize - 1)
                        - mSeekSyncPoints->valueAt(0);
                int64_t currentDurationUs = size * diffUs / diffOffset;
                durations.push_back(currentDurationUs);
                if (durations.size() > 5) {
                    durations.erase(durations.begin());
                    int64_t min = *durations.begin();
                    int64_t max = *durations.begin();
                    for (auto duration : durations) {
                        if (min > duration) {
                            min = duration;
                        }
                        if (max < duration) {
                            max = duration;
                        }
                    }
                    if (max - min < 500 * 1000) {
                        durationUs = currentDurationUs;
                        break;
                    }
                }
            }
        }
        status_t err;
        int64_t bufferedDurationUs;
        bufferedDurationUs = impl->getBufferedDurationUs(&err);
        if (err == ERROR_END_OF_STREAM) {
            durationUs = bufferedDurationUs;
        }
        if (durationUs > 0) {
            const sp<MetaData> meta = impl->getFormat();
            meta->setInt64(kKeyDuration, durationUs);
            impl->setFormat(meta);
        } else {
            estimateDurationsFromTimesUsAtEnd();
        }
    }

    ALOGI("haveAudio=%d, haveVideo=%d, elaspedTime=%" PRId64,
            haveAudio, haveVideo, ALooper::GetNowUs() - startTime);
}

status_t MPEG2TSExtractor::feedMore(bool isInit) {
    Mutex::Autolock autoLock(mLock);

    uint8_t packet[kTSPacketSize];
    ssize_t n = mDataSource->readAt(mOffset, packet, kTSPacketSize);

    if (n < (ssize_t)kTSPacketSize) {
        if (n >= 0) {
            mParser->signalEOS(ERROR_END_OF_STREAM);
        }
        return (n < 0) ? (status_t)n : ERROR_END_OF_STREAM;
    }

    ATSParser::SyncEvent event(mOffset);
    mOffset += n;
    status_t err = mParser->feedTSPacket(packet, kTSPacketSize, &event);
    if (event.hasReturnedData()) {
        if (isInit) {
            mLastSyncEvent = event;
        } else {
            addSyncPoint_l(event);
        }
    }
    return err;
}

void MPEG2TSExtractor::addSyncPoint_l(const ATSParser::SyncEvent &event) {
    if (!event.hasReturnedData()) {
        return;
    }

    for (size_t i = 0; i < mSourceImpls.size(); ++i) {
        if (mSourceImpls[i].get() == event.getMediaSource().get()) {
            KeyedVector<int64_t, off64_t> *syncPoints = &mSyncPoints.editItemAt(i);
            syncPoints->add(event.getTimeUs(), event.getOffset());
            // We're keeping the size of the sync points at most 5mb per a track.
            size_t size = syncPoints->size();
            if (size >= 327680) {
                int64_t firstTimeUs = syncPoints->keyAt(0);
                int64_t lastTimeUs = syncPoints->keyAt(size - 1);
                if (event.getTimeUs() - firstTimeUs > lastTimeUs - event.getTimeUs()) {
                    syncPoints->removeItemsAt(0, 4096);
                } else {
                    syncPoints->removeItemsAt(size - 4096, 4096);
                }
            }
            break;
        }
    }
}

status_t MPEG2TSExtractor::estimateDurationsFromTimesUsAtEnd()  {
    if (!(mDataSource->flags() & DataSource::kIsLocalFileSource)) {
        return ERROR_UNSUPPORTED;
    }

    off64_t size = 0;
    status_t err = mDataSource->getSize(&size);
    if (err != OK) {
        return err;
    }

    uint8_t packet[kTSPacketSize];
    const off64_t zero = 0;
    off64_t offset = max(zero, size - kMaxDurationReadSize);
    if (mDataSource->readAt(offset, &packet, 0) < 0) {
        return ERROR_IO;
    }

    int retry = 0;
    bool allDurationsFound = false;
    int64_t timeAnchorUs = mParser->getFirstPTSTimeUs();
    do {
        int bytesRead = 0;
        sp<ATSParser> parser = new ATSParser(ATSParser::TS_TIMESTAMPS_ARE_ABSOLUTE);
        ATSParser::SyncEvent ev(0);
        offset = max(zero, size - (kMaxDurationReadSize << retry));
        offset = (offset / kTSPacketSize) * kTSPacketSize;
        for (;;) {
            if (bytesRead >= kMaxDurationReadSize << max(0, retry - 1)) {
                break;
            }

            ssize_t n = mDataSource->readAt(offset, packet, kTSPacketSize);
            if (n < 0) {
                return n;
            } else if (n < (ssize_t)kTSPacketSize) {
                break;
            }

            offset += kTSPacketSize;
            bytesRead += kTSPacketSize;
            err = parser->feedTSPacket(packet, kTSPacketSize, &ev);
            if (err != OK) {
                return err;
            }

            if (ev.hasReturnedData()) {
                int64_t durationUs = ev.getTimeUs();
                ATSParser::SourceType type = ev.getType();
                ev.reset();

                int64_t firstTimeUs;
                sp<AnotherPacketSource> src =
                    (AnotherPacketSource *)mParser->getSource(type).get();
                if (src == NULL || src->nextBufferTime(&firstTimeUs) != OK) {
                    continue;
                }
                durationUs += src->getEstimatedBufferDurationUs();
                durationUs -= timeAnchorUs;
                durationUs -= firstTimeUs;
                if (durationUs > 0) {
                    int64_t origDurationUs, lastDurationUs;
                    const sp<MetaData> meta = src->getFormat();
                    const uint32_t kKeyLastDuration = 'ldur';
                    // Require two consecutive duration calculations to be within 1 sec before
                    // updating; use MetaData to store previous duration estimate in per-stream
                    // context.
                    if (!meta->findInt64(kKeyDuration, &origDurationUs)
                            || !meta->findInt64(kKeyLastDuration, &lastDurationUs)
                            || (origDurationUs < durationUs
                             && abs(durationUs - lastDurationUs) < 60000000)) {
                        meta->setInt64(kKeyDuration, durationUs);
                    }
                    meta->setInt64(kKeyLastDuration, durationUs);
                }
            }
        }

        if (!allDurationsFound) {
            allDurationsFound = true;
            for (auto t: {ATSParser::VIDEO, ATSParser::AUDIO}) {
                sp<AnotherPacketSource> src = (AnotherPacketSource *)mParser->getSource(t).get();
                if (src == NULL) {
                    continue;
                }
                int64_t durationUs;
                const sp<MetaData> meta = src->getFormat();
                if (!meta->findInt64(kKeyDuration, &durationUs)) {
                    allDurationsFound = false;
                    break;
                }
            }
        }

        ++retry;
    } while(!allDurationsFound && offset > 0 && retry <= kMaxDurationRetry);

    return allDurationsFound? OK : ERROR_UNSUPPORTED;
}

uint32_t MPEG2TSExtractor::flags() const {
    return CAN_PAUSE | CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD;
}

status_t MPEG2TSExtractor::seek(int64_t seekTimeUs,
        const MediaSource::ReadOptions::SeekMode &seekMode) {
    if (mSeekSyncPoints == NULL || mSeekSyncPoints->isEmpty()) {
        ALOGW("No sync point to seek to.");
        // ... and therefore we have nothing useful to do here.
        return OK;
    }

    // Determine whether we're seeking beyond the known area.
    bool shouldSeekBeyond =
            (seekTimeUs > mSeekSyncPoints->keyAt(mSeekSyncPoints->size() - 1));

    // Determine the sync point to seek.
    size_t index = 0;
    for (; index < mSeekSyncPoints->size(); ++index) {
        int64_t timeUs = mSeekSyncPoints->keyAt(index);
        if (timeUs > seekTimeUs) {
            break;
        }
    }

    switch (seekMode) {
        case MediaSource::ReadOptions::SEEK_NEXT_SYNC:
            if (index == mSeekSyncPoints->size()) {
                ALOGW("Next sync not found; starting from the latest sync.");
                --index;
            }
            break;
        case MediaSource::ReadOptions::SEEK_CLOSEST_SYNC:
        case MediaSource::ReadOptions::SEEK_CLOSEST:
            ALOGW("seekMode not supported: %d; falling back to PREVIOUS_SYNC",
                    seekMode);
            // fall-through
        case MediaSource::ReadOptions::SEEK_PREVIOUS_SYNC:
            if (index == 0) {
                ALOGW("Previous sync not found; starting from the earliest "
                        "sync.");
            } else {
                --index;
            }
            break;
    }
    if (!shouldSeekBeyond || mOffset <= mSeekSyncPoints->valueAt(index)) {
        int64_t actualSeekTimeUs = mSeekSyncPoints->keyAt(index);
        mOffset = mSeekSyncPoints->valueAt(index);
        status_t err = queueDiscontinuityForSeek(actualSeekTimeUs);
        if (err != OK) {
            return err;
        }
    }

    if (shouldSeekBeyond) {
        status_t err = seekBeyond(seekTimeUs);
        if (err != OK) {
            return err;
        }
    }

    // Fast-forward to sync frame.
    for (size_t i = 0; i < mSourceImpls.size(); ++i) {
        const sp<AnotherPacketSource> &impl = mSourceImpls[i];
        status_t err;
        feedUntilBufferAvailable(impl);
        while (impl->hasBufferAvailable(&err)) {
            sp<AMessage> meta = impl->getMetaAfterLastDequeued(0);
            sp<ABuffer> buffer;
            if (meta == NULL) {
                return UNKNOWN_ERROR;
            }
            int32_t sync;
            if (meta->findInt32("isSync", &sync) && sync) {
                break;
            }
            err = impl->dequeueAccessUnit(&buffer);
            if (err != OK) {
                return err;
            }
            feedUntilBufferAvailable(impl);
        }
    }

    return OK;
}

status_t MPEG2TSExtractor::queueDiscontinuityForSeek(int64_t actualSeekTimeUs) {
    // Signal discontinuity
    sp<AMessage> extra(new AMessage);
    extra->setInt64(IStreamListener::kKeyMediaTimeUs, actualSeekTimeUs);
    mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, extra);

    // After discontinuity, impl should only have discontinuities
    // with the last being what we queued. Dequeue them all here.
    for (size_t i = 0; i < mSourceImpls.size(); ++i) {
        const sp<AnotherPacketSource> &impl = mSourceImpls.itemAt(i);
        sp<ABuffer> buffer;
        status_t err;
        while (impl->hasBufferAvailable(&err)) {
            if (err != OK) {
                return err;
            }
            err = impl->dequeueAccessUnit(&buffer);
            // If the source contains anything but discontinuity, that's
            // a programming mistake.
            CHECK(err == INFO_DISCONTINUITY);
        }
    }

    // Feed until we have a buffer for each source.
    for (size_t i = 0; i < mSourceImpls.size(); ++i) {
        const sp<AnotherPacketSource> &impl = mSourceImpls.itemAt(i);
        sp<ABuffer> buffer;
        status_t err = feedUntilBufferAvailable(impl);
        if (err != OK) {
            return err;
        }
    }

    return OK;
}

status_t MPEG2TSExtractor::seekBeyond(int64_t seekTimeUs) {
    // If we're seeking beyond where we know --- read until we reach there.
    size_t syncPointsSize = mSeekSyncPoints->size();

    while (seekTimeUs > mSeekSyncPoints->keyAt(
            mSeekSyncPoints->size() - 1)) {
        status_t err;
        if (syncPointsSize < mSeekSyncPoints->size()) {
            syncPointsSize = mSeekSyncPoints->size();
            int64_t syncTimeUs = mSeekSyncPoints->keyAt(syncPointsSize - 1);
            // Dequeue buffers before sync point in order to avoid too much
            // cache building up.
            sp<ABuffer> buffer;
            for (size_t i = 0; i < mSourceImpls.size(); ++i) {
                const sp<AnotherPacketSource> &impl = mSourceImpls[i];
                int64_t timeUs;
                while ((err = impl->nextBufferTime(&timeUs)) == OK) {
                    if (timeUs < syncTimeUs) {
                        impl->dequeueAccessUnit(&buffer);
                    } else {
                        break;
                    }
                }
                if (err != OK && err != -EWOULDBLOCK) {
                    return err;
                }
            }
        }
        if (feedMore() != OK) {
            return ERROR_END_OF_STREAM;
        }
    }

    return OK;
}

status_t MPEG2TSExtractor::feedUntilBufferAvailable(
        const sp<AnotherPacketSource> &impl) {
    status_t finalResult;
    while (!impl->hasBufferAvailable(&finalResult)) {
        if (finalResult != OK) {
            return finalResult;
        }

        status_t err = feedMore();
        if (err != OK) {
            impl->signalEOS(err);
        }
    }
    return OK;
}

////////////////////////////////////////////////////////////////////////////////

bool SniffMPEG2TS(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    for (int i = 0; i < 5; ++i) {
        char header;
        if (source->readAt(kTSPacketSize * i, &header, 1) != 1
                || header != 0x47) {
            return false;
        }
    }

    *confidence = 0.1f;
    mimeType->setTo(MEDIA_MIMETYPE_CONTAINER_MPEG2TS);

    return true;
}

}  // namespace android
