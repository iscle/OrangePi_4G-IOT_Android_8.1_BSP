/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
#define LOG_TAG "MtkMPEG2TSExtractor"

#include <inttypes.h>
#include <utils/Log.h>

#include "include/MtkMPEG2TSExtractor.h"
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

#include "mpeg2ts/AnotherPacketSource.h"
#include "mpeg2ts/ATSParser.h"

#include <hidl/HybridInterface.h>
#include <android/hardware/cas/1.0/ICas.h>
// add for support M2TS file playback
#define SUPPORT_M2TS

namespace android {

using hardware::cas::V1_0::ICas;

static const size_t kTSPacketSize = 188;
// add for mtk
#ifdef SUPPORT_M2TS
static const size_t kM2TSPacketSize = 192;
// if one day no need to support M2TS, all code include in SUPPORT_M2TS can be removed.
// The variable kFillPacketSize can be all instead by kTSPacketSize, and it can be removed too.

// kFillPacketSize can not modified as a global variable, when slide video be opened,
// switching .ts and .m2ts will cause this global variable does not meet expectations.
// static size_t kFillPacketSize = 188; //for ALPS02615242
#endif
#ifdef MTK_SEEK_AND_DURATION
// add for mtk duration calculate method, handle find Duration for ANR
const static int64_t kMaxPTSTimeOutUs = 3000000LL;
#endif
// end of add for mtk
static const int kMaxDurationReadSize = 250000LL;
static const int kMaxDurationRetry = 6;

struct MtkMPEG2TSSource : public MediaSource {
    MtkMPEG2TSSource(
            const sp<MtkMPEG2TSExtractor> &extractor,
            const sp<AnotherPacketSource> &impl,
            bool doesSeek);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

private:
    sp<MtkMPEG2TSExtractor> mExtractor;
    sp<AnotherPacketSource> mImpl;

    // If there are both audio and video streams, only the video stream
    // will signal seek on the extractor; otherwise the single stream will seek.
    bool mDoesSeek;
#ifdef MTK_SEEK_AND_DURATION
    bool mIsVideo;
#endif

    DISALLOW_EVIL_CONSTRUCTORS(MtkMPEG2TSSource);
};

MtkMPEG2TSSource::MtkMPEG2TSSource(
        const sp<MtkMPEG2TSExtractor> &extractor,
        const sp<AnotherPacketSource> &impl,
        bool doesSeek)
    : mExtractor(extractor),
      mImpl(impl),
      mDoesSeek(doesSeek) {
#ifdef MTK_SEEK_AND_DURATION
      mIsVideo = true;
#endif
}

status_t MtkMPEG2TSSource::start(MetaData *params) {
    return mImpl->start(params);
}

status_t MtkMPEG2TSSource::stop() {
#ifdef MTK_SEEK_AND_DURATION
    // add for video short than audio, video stop then audio can do seek
    ALOGD("Stop Video=%d track", mIsVideo);
    if (mIsVideo == true)
        mExtractor->setVideoState(true);
#endif
    return mImpl->stop();
}

sp<MetaData> MtkMPEG2TSSource::getFormat() {
#ifdef MTK_SEEK_AND_DURATION
    if (mImpl == NULL) {
        return NULL;
    }

    sp<MetaData> meta = mImpl->getFormat();
    if (meta == NULL)
        return NULL;

    // add for mtk duration calculate method
    int64_t durationUs;
    if (!meta->findInt64(kKeyDuration, &durationUs)) {
        meta->setInt64(kKeyDuration, mExtractor->getDurationUs());
    }

    // add for mtk seek
    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));
    if (strncasecmp("video/", mime, 6))
        mIsVideo = false;

    return meta;
#else
    return mImpl->getFormat();
#endif
}

status_t MtkMPEG2TSSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode seekMode;
#ifdef MTK_SEEK_AND_DURATION
    // add for mtk seek method
    if (options && options->getSeekTo(&seekTimeUs, &seekMode)) {
        if (mExtractor->getVideoState() && mExtractor->IsLocalSource() && !mIsVideo && !mDoesSeek) {
            mDoesSeek = true;
            ALOGE("Audio can seek now");
        }
        if (mDoesSeek) {
            if (mExtractor->IsLocalSource()) {  // loacal seek use mtk
                mExtractor->seekTo(seekTimeUs);
                mImpl->queueDiscontinuity(ATSParser::DISCONTINUITY_NONE, NULL, false);
            } else {
                // http streaming seek use google default
                status_t err = mExtractor->seek(seekTimeUs, seekMode);
                if (err != OK) {
                    return err;
                }
            }
        }
    }
#else
    if (mDoesSeek && options && options->getSeekTo(&seekTimeUs, &seekMode)) {
        // seek is needed
        status_t err = mExtractor->seek(seekTimeUs, seekMode);
        if (err != OK) {
            return err;
        }
    }
#endif

    if (mExtractor->feedUntilBufferAvailable(mImpl) != OK) {
        return ERROR_END_OF_STREAM;
    }
#ifdef MTK_SEEK_AND_DURATION
    // add for mtk seek method
    // only seek when has both video and audio, need drop audio frames queued in anotherpacketsource before seektime.
    // This seektime is video I frame been found after seek. But audio been queued from seektime set by ap.
    if (options && options->getSeekTo(&seekTimeUs, &seekMode)
        && !(mExtractor->getVideoState()) && mExtractor->IsLocalSource() && !mIsVideo) {
        ALOGV("Audio first frame after seek, time should > %lld", (long long)seekTimeUs);
        status_t err = OK;
        while ((err = mImpl->read(out, options)) == OK) {
            int64_t timeUs;
            MediaBuffer *mbuf = *out;
            if (mbuf->meta_data()->findInt64(kKeyTime, &timeUs)) {
                if (timeUs < seekTimeUs) {
                    ALOGV("buffer time (%lld) < seektime (%lld)", (long long)timeUs, (long long)seekTimeUs);
                    mbuf->release();
                    mbuf = NULL;
                    if (mExtractor->feedUntilBufferAvailable(mImpl) != OK) {
                        return ERROR_END_OF_STREAM;
                    }
                } else {
                    ALOGD("find audio first buffer(%lld) after seek time(%lld)",
                            (long long)timeUs, (long long)seekTimeUs);
                    *out = mbuf;
                    return OK;
                }
            }
        }
        if (err != OK)
            return err;
    }
#endif

    return mImpl->read(out, options);
}

////////////////////////////////////////////////////////////////////////////////

MtkMPEG2TSExtractor::MtkMPEG2TSExtractor(const sp<DataSource> &source)
    : mDataSource(source),
#ifndef MTK_SEEK_AND_DURATION
      mParser(new ATSParser),
#endif
      mLastSyncEvent(0),
      mOffset(0) {
// add for mtk
    kFillPacketSize = kTSPacketSize;
#ifdef SUPPORT_M2TS
    char header;
    if (source->readAt(0, &header, 1) == 1 && header == 0x47) {
        ALOGD("MtkMPEG2TSExtractor:this is ts file\n");
        kFillPacketSize = kTSPacketSize;
    } else {
        ALOGD("MtkMPEG2TSExtractor:this is m2ts file\n");
        kFillPacketSize = kM2TSPacketSize;
    }
#endif
#ifdef MTK_SEEK_AND_DURATION
    if (mDataSource->flags() & DataSource::kIsCachingDataSource) {
        mParser = new ATSParser();  // http streaming is needed
    } else {
        mParser = new ATSParser(0x40000000);  // local
    }

    // add for mtk duration calculate method and local seek
    mDurationUs = 0;
    mFileSize = 0;
    // add for mtk duration calculate method
    mFindingMaxPTS = false;
    mOffsetPAT = 0;
    // add for mtk seek method
    mSeekTimeUs = 0;
    mSeeking = false;
    mSeekingOffset = 0;
    mMinOffset = 0;
    mMaxOffset = 0;
    mMaxcount = 0;
    mVideoUnSupportedByDecoder = false;
    End_OF_FILE = false;

    // add for mtk duration calculate method
    // http streaming calculate duration use google default method
    if (!(mDataSource->flags() & DataSource::kIsCachingDataSource)) {
        status_t err = parseMaxPTS();
        if (err != OK) {
            // for ALPS03097043, if get duration fail, return will cause can't play.
            // clear data queue, and setDequeueState true as it was set to false when parseMaxPTS.
            mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
            mParser->setDequeueState(true);
        }
    }
    ALOGD("MtkMPEG2TSExtractor: after parseMaxPTS  mOffset=%lld", (long long)mOffset);
#endif
// end of add for mtk
    init();
}

size_t MtkMPEG2TSExtractor::countTracks() {
    return mSourceImpls.size();
}

sp<IMediaSource> MtkMPEG2TSExtractor::getTrack(size_t index) {
    if (index >= mSourceImpls.size()) {
        return NULL;
    }

#ifdef MTK_SEEK_AND_DURATION
    // add for mtk seek method
    // http streaming seek use google default method, local use mtk seek
    if (!(mDataSource->flags() & DataSource::kIsCachingDataSource)) {
        bool doesSeek = true;
        if (mSourceImpls.size() > 1) {
            CHECK_EQ(mSourceImpls.size(), 2u);
            sp<MetaData> meta = mSourceImpls.editItemAt(index)->getFormat();
            const char *mime;
            CHECK(meta->findCString(kKeyMIMEType, &mime));

            if (!strncasecmp("audio/", mime, 6)) {
                doesSeek = false;
            }
        }

        return new MtkMPEG2TSSource(this, mSourceImpls.editItemAt(index), doesSeek);
    } else
#endif
    // The seek reference track (video if present; audio otherwise) performs
    // seek requests, while other tracks ignore requests.
    return new MtkMPEG2TSSource(this, mSourceImpls.editItemAt(index),
            (mSeekSyncPoints == &mSyncPoints.editItemAt(index)));
}

sp<MetaData> MtkMPEG2TSExtractor::getTrackMetaData(
        size_t index, uint32_t /* flags */) {
#ifdef MTK_SEEK_AND_DURATION
    if (index >= mSourceImpls.size())
        return NULL;

    sp<MetaData> meta = mSourceImpls.editItemAt(index)->getFormat();

    if (meta != NULL && meta.get() != NULL) {
        // add for mtk duration calculate method
        int64_t durationUs;
        if ((!meta->findInt64(kKeyDuration, &durationUs)) && mDurationUs)
            meta->setInt64(kKeyDuration, mDurationUs);
        return meta;
    }
    return NULL;
#else
    return index < mSourceImpls.size()
        ? mSourceImpls.editItemAt(index)->getFormat() : NULL;
#endif
}

sp<MetaData> MtkMPEG2TSExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_MPEG2TS);

    return meta;
}

//static
bool MtkMPEG2TSExtractor::isScrambledFormat(const sp<MetaData> &format) {
    const char *mime;
    return format->findCString(kKeyMIMEType, &mime)
            && (!strcasecmp(MEDIA_MIMETYPE_VIDEO_SCRAMBLED, mime)
                    || !strcasecmp(MEDIA_MIMETYPE_AUDIO_SCRAMBLED, mime));
}

status_t MtkMPEG2TSExtractor::setMediaCas(const HInterfaceToken &casToken) {
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

void MtkMPEG2TSExtractor::addSource(const sp<AnotherPacketSource> &impl) {
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

void MtkMPEG2TSExtractor::init() {
    bool haveAudio = false;
    bool haveVideo = false;
    int64_t startTime = ALooper::GetNowUs();
#ifdef MTK_SEEK_AND_DURATION
    mOffset = 0;
    End_OF_FILE = false;
#endif

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
#ifdef MTK_SEEK_AND_DURATION
    // http streaming calculate duration use google default method
    if (mDataSource->flags() & DataSource::kIsCachingDataSource)
#endif
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

status_t MtkMPEG2TSExtractor::feedMore(bool isInit) {
    Mutex::Autolock autoLock(mLock);
#ifdef MTK_SEEK_AND_DURATION
    // add for mtk seek method
    if (mSeeking) {
        int64_t pts = mParser->getMaxPTS();  // [qian] get the max pts in the had read data
        // to solve the problem that when seek to End_OF_FILE then seek to another place ,instantly. and can not play
        if (End_OF_FILE && pts == 0) {
            ALOGE("seek to End_OF_FILE last time");
            mOffset = (off64_t)((((mMinOffset + mMaxOffset) / 2) / kFillPacketSize) * kFillPacketSize);

            mSeekingOffset = mOffset;
            End_OF_FILE = false;
        }

        if (pts > 0) {
            mMaxcount++;
            if ((pts - mSeekTimeUs < 50000 && pts - mSeekTimeUs > -50000)
                || mMinOffset == mMaxOffset || mMaxcount > 13) {
                ALOGE("seekdone pts=%lld,mSeekTimeUs=%lld,mMaxcount=%lld,mMinOffset=%lld,mMaxOffset=%lld moffset:%lld",
                    (long long)pts/1000, (long long)mSeekTimeUs/1000, (long long)mMaxcount,
                    (long long)mMinOffset, (long long)mMaxOffset, (long long)mOffset);
                mSeeking = false;
                mParser->setDequeueState(true);
            } else {
                mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
                if (pts < mSeekTimeUs) {
                    mMinOffset = mSeekingOffset;    // [qian], 1 enter this will begin with the mid of file

                } else {
                    mMaxOffset = mSeekingOffset;
                }
                mSeekingOffset = (off64_t)((((mMinOffset + mMaxOffset) / 2) / kFillPacketSize) * kFillPacketSize);

                mOffset = mSeekingOffset;
            }
            ALOGE("pts=%lld,mSeekTimeUs=%lld,mMaxcount=%lld,mOffset=%lld,mMinOffset=%lld,mMaxOffset=%lld",
                 (long long)pts / 1000, (long long)mSeekTimeUs / 1000, (long long)mMaxcount, (long long)mOffset,
                 (long long)mMinOffset, (long long)mMaxOffset);
        }
    }
#endif
    uint8_t packet[kFillPacketSize];
    ssize_t n = mDataSource->readAt(mOffset, packet, kFillPacketSize);

    if (n < (ssize_t)kFillPacketSize) {
#ifdef MTK_SEEK_AND_DURATION
        // add for mtk duration calculate method and local seek
        ALOGE(" mOffset=%lld,n =%zd", (long long)mOffset, n);
        End_OF_FILE = true;
        if ((n >= 0) && (!mFindingMaxPTS) && (!mSeeking)) {
            mParser->signalEOS(ERROR_END_OF_STREAM);
        }
        if (mSeeking) {
            mSeeking = false;
            mParser->setDequeueState(true);
            ALOGE("seek to end of file, stop seeking");
        }
#else
        if (n >= 0) {
            mParser->signalEOS(ERROR_END_OF_STREAM);
        }
#endif
        return (n < 0) ? (status_t)n : ERROR_END_OF_STREAM;
    }

    ATSParser::SyncEvent event(mOffset);
    mOffset += n;

    status_t err = OK;
#ifdef SUPPORT_M2TS
    if (kFillPacketSize == kM2TSPacketSize) {
        err = mParser->feedTSPacket(packet + 4, kFillPacketSize - 4, &event);
    } else
#endif
    err = mParser->feedTSPacket(packet, kTSPacketSize, &event);
    if (event.hasReturnedData()) {
        if (isInit) {
            mLastSyncEvent = event;
        } else {
            addSyncPoint_l(event);
        }
    }
#ifdef MTK_SEEK_AND_DURATION
    // add to avoid find sync word err when seek.
    if (err == BAD_VALUE) {
        int32_t syncOff = 0;
#ifdef SUPPORT_M2TS
        if (kFillPacketSize == kM2TSPacketSize) {
            syncOff = findSyncCode(packet + 4, kFillPacketSize - 4);
        } else
#endif
        syncOff = findSyncCode(packet, kTSPacketSize);
        if (syncOff >= 0) {
            mOffset -= n;
            mOffset += syncOff;
        }
        ALOGE("[TS_ERROR]correction once offset mOffset=%lld", (long long)mOffset);
        return OK;
    }
#endif
    return err;
}

void MtkMPEG2TSExtractor::addSyncPoint_l(const ATSParser::SyncEvent &event) {
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

status_t MtkMPEG2TSExtractor::estimateDurationsFromTimesUsAtEnd()  {
    if (!(mDataSource->flags() & DataSource::kIsLocalFileSource)) {
        return ERROR_UNSUPPORTED;
    }

    off64_t size = 0;
    status_t err = mDataSource->getSize(&size);
    if (err != OK) {
        return err;
    }

    uint8_t packet[kFillPacketSize];
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
        offset = (offset / kFillPacketSize) * kFillPacketSize;
        for (;;) {
            if (bytesRead >= kMaxDurationReadSize << max(0, retry - 1)) {
                break;
            }

            ssize_t n = mDataSource->readAt(offset, packet, kFillPacketSize);
            if (n < 0) {
                return n;
            } else if (n < (ssize_t)kFillPacketSize) {
                break;
            }

            offset += kFillPacketSize;
            bytesRead += kFillPacketSize;
#ifdef SUPPORT_M2TS
            if (kFillPacketSize == kM2TSPacketSize) {
                err = mParser->feedTSPacket(packet + 4, kFillPacketSize - 4, &ev);
            } else
#endif
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

uint32_t MtkMPEG2TSExtractor::flags() const {
    return CAN_PAUSE | CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD;
}

status_t MtkMPEG2TSExtractor::seek(int64_t seekTimeUs,
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

status_t MtkMPEG2TSExtractor::queueDiscontinuityForSeek(int64_t actualSeekTimeUs) {
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

status_t MtkMPEG2TSExtractor::seekBeyond(int64_t seekTimeUs) {
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

status_t MtkMPEG2TSExtractor::feedUntilBufferAvailable(
        const sp<AnotherPacketSource> &impl) {
    status_t finalResult;
#ifdef MTK_SEEK_AND_DURATION
    // add for mtk seek method
    while (!impl->hasBufferAvailable(&finalResult) || getSeeking())
#else
    while (!impl->hasBufferAvailable(&finalResult))
#endif
    {
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

bool MtkSniffMPEG2TS(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
#ifdef SUPPORT_M2TS
    bool isM2ts = false;
#endif
    for (int i = 0; i < 5; ++i) {
        char header;
        if (source->readAt(kTSPacketSize * i, &header, 1) != 1
                || header != 0x47) {
#ifdef SUPPORT_M2TS
            // not ts file, check if m2ts file
            for (int j = 0; j < 5; ++j) {
                char headers[5];
                if (source->readAt(kM2TSPacketSize * j, &headers, 5) != 5
                    || headers[4] != 0x47) {
                    // not m2ts file too, return
                    return false;
                }
            }
            ALOGD("this is m2ts file\n");
            isM2ts = true;
            break;
#else
            return false;
#endif
        }
    }
#ifdef SUPPORT_M2TS
    if (!isM2ts)
#endif
    ALOGD("this is ts file\n");

    *confidence = 0.1f;
    mimeType->setTo(MEDIA_MIMETYPE_CONTAINER_MPEG2TS);

    return true;
}

// add for mtk, mtk defined interfaces
#ifdef MTK_SEEK_AND_DURATION
    // add for mtk duration calculate method
bool MtkMPEG2TSExtractor::findSyncWord(const sp<DataSource> &source, off64_t StartOffset,
                    uint64_t size, size_t PacketSize, off64_t &NewOffset) {
    uint8_t packet[PacketSize];
    off64_t Offset = StartOffset;

    source->readAt(Offset, packet, PacketSize);
    ALOGD("findSyncWord mOffset= %lld  packet=0x%x,0x%x,0x%x,0x%x,0x%x",
        (long long)Offset, packet[0], packet[1], packet[2], packet[3], packet[4]);
#ifdef SUPPORT_M2TS
    if (((PacketSize == kTSPacketSize) && packet[0] != 0x47) ||
        ((PacketSize == kM2TSPacketSize) && packet[4] != 0x47))
#else
    if ((PacketSize == kTSPacketSize) && packet[0] != 0x47)
#endif
    {
        uint8_t packetTempS[PacketSize * 3];
        int32_t index = 0;
        for (; Offset < (off64_t)(StartOffset + size - 3 * PacketSize);) {
            Offset = Offset + PacketSize;
            source->readAt(Offset, packetTempS, PacketSize * 3);

            for (index = 0; index < (int32_t)PacketSize; index++) {
                if ((packetTempS[index] == 0x47) && (packetTempS[index+ PacketSize] == 0x47)
                    && (packetTempS[index+ PacketSize * 2] == 0x47)) {
                    break;
                }
            }

            if (index < (int32_t)PacketSize) {
#ifdef SUPPORT_M2TS
                if (PacketSize == kM2TSPacketSize) {
                    NewOffset = Offset + index - 4 + 2 * PacketSize;
                } else
#else
                    NewOffset = Offset + index + 2 * PacketSize;
#endif
                ALOGD("findSyncWord mOffset= %lld  kFillPacketSize:%zu packet=0x%x,0x%x,0x%x,0x%x,0x%x",
                    (long long)NewOffset, PacketSize, packetTempS[index], packetTempS[index+1],
                    packetTempS[index+2], packetTempS[index+3], packetTempS[index+4]);
                return true;
            }
        }
        ALOGE("findSyncWord: can not find sync word");
        return false;
    } else {
        return true;
    }
}

status_t MtkMPEG2TSExtractor::parseMaxPTS() {
    mFindingMaxPTS = true;
    status_t err = mDataSource->getSize(&mFileSize);
    if (err != OK) {
        return err;
    }
    ALOGE("mFileSize:%lld", (long long)mFileSize);

    off64_t counts = mFileSize / kFillPacketSize;
    int32_t numPacketsParsed = 0;
    int64_t maxPTSStart = systemTime() / 1000;

    // set false, when parse the ts pakect, will not exec the  main function of onPayloadData
    // only parse the PAT, PMT,PES header, save parse time
    mParser->setDequeueState(false);

    // get first pts(pts in in PES packet)
    bool foundFirstPTS = false;
    while (feedMore() == OK) {
        if (mParser->getFirstPTSTimeUs() >= 0) {
            ALOGD("parseMaxPTS:firstPTSIsValid, mOffset %lld", (long long)mOffset);
            foundFirstPTS = true;
            break;
        }
        if (++numPacketsParsed > 30000) {
            break;
        }
    }
    if (!foundFirstPTS) {
        ALOGI("not found first PTS numPacketsParsed %d", numPacketsParsed);
        return UNKNOWN_ERROR;
    }

    mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);  // clear

    // get duration
    mOffsetPAT = mFileSize;
    for (off64_t i = 1; i <= counts; i++) {
        int64_t maxPTSDuration = systemTime() / 1000 - maxPTSStart;
        if (maxPTSDuration > kMaxPTSTimeOutUs) {
            ALOGD("TimeOut find PTS, start time=%lld, duration=%lld",
                  (long long)maxPTSStart, (long long)maxPTSDuration);
            return UNKNOWN_ERROR;
        }

        if (mOffsetPAT > (off64_t)(2500 * i * i * kFillPacketSize)) {
            mOffsetPAT = (off64_t)(mOffsetPAT - 2500 * i * i * kFillPacketSize);
        } else {
            mOffsetPAT = 0;
        }

        mOffset = mOffsetPAT;

        if (findSyncWord(mDataSource, mOffsetPAT, 1000 * (off64_t)kFillPacketSize, kFillPacketSize, mOffset)) {
            // find last PAT, start searching from the last PAT
            ALOGD("parseMaxPTS:findSyncWord done, mOffset=%lld", (long long)mOffset);
            mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
            while (feedMore() == OK) {
                int64_t maxPTSfeedmoreDuration = systemTime() / 1000 - maxPTSStart;
                if (maxPTSfeedmoreDuration > kMaxPTSTimeOutUs) {
                    ALOGD("TimeOut find PTS, start time=%lld, maxPTSfeedmoreduration=%lld",
                          (long long)maxPTSStart, (long long)maxPTSfeedmoreDuration);
                    return UNKNOWN_ERROR;
                }

                if (((mOffset - mOffsetPAT) > (10000 * (off64_t) kFillPacketSize))
                    && (mParser->getMaxPTS() == 0)) {
                    ALOGD("stop feedmore (no PES) mOffset=%lld  mOffsetPAT=%lld",
                        (long long)mOffset, (long long)mOffsetPAT);
                    break;
                }
            }
            mDurationUs = mParser->getMaxPTS();
            if (mDurationUs) {
                mFindingMaxPTS = false;
                break;
            }
        }
    }
    // clear data queue
    mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
    mParser->setDequeueState(true);
    ALOGD("getMaxPTS->mDurationUs:%lld", (long long)mDurationUs);

    return OK;
}

uint64_t MtkMPEG2TSExtractor::getDurationUs() {
        return mDurationUs;
}

// add for mtk seek method
    // add to avoid find sync word err when seek.
int32_t MtkMPEG2TSExtractor::findSyncCode(const void *data, size_t size) {
    uint32_t i = 0;
    for (i = 0; i < size; i++) {
        if (((uint8_t *) data)[i] == 0x47u)
            return i;
    }
    return -1;
}

void MtkMPEG2TSExtractor::seekTo(int64_t seekTimeUs) {
    Mutex::Autolock autoLock(mLock);

    ALOGE("seekTo:mDurationMs =%lld,seekTimeMs= %lld, mOffset:%lld",
          (long long)(mDurationUs / 1000), (long long)(seekTimeUs / 1000), (long long)mOffset);
    if (seekTimeUs == 0) {
        mOffset = 0;
        mSeeking = false;
        // clear MaxPTS
        mParser->setDequeueState(false);
        mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
        // clear buffer queue
        mParser->setDequeueState(true);
        mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
    } else if ((mDurationUs - seekTimeUs) < 10000) {  // seek to end
        mOffset = mFileSize;
        mSeeking = false;
        // set ATSParser MaxTimeUs to mDurationUs
        mParser->setDequeueState(false);
        sp<AMessage> maxTimeUsMsg = new AMessage;
        maxTimeUsMsg->setInt64("MaxtimeUs", mDurationUs);
        mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, maxTimeUsMsg);
        // clear buffer queue
        mParser->setDequeueState(true);
        mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
    } else {
        mParser->signalDiscontinuity(ATSParser::DISCONTINUITY_TIME, NULL);
        mSeekingOffset = mOffset;
        mSeekTimeUs = seekTimeUs;
        mMinOffset = 0;
        mMaxOffset = mFileSize;
        mMaxcount = 0;
        mParser->setDequeueState(false);    // will start search mode, not read data mode
        mSeeking = true;
    }
    return;
}

bool MtkMPEG2TSExtractor::getSeeking() {
    return mSeeking;
}

bool MtkMPEG2TSExtractor::IsLocalSource() {  // http streaming seek is needed
    if (!(mDataSource->flags() & DataSource::kIsCachingDataSource)) {
       return true;
    }
    return false;
}

void MtkMPEG2TSExtractor::setVideoState(bool state) {
    mVideoUnSupportedByDecoder = state;
    ALOGE("setVideoState  mVideoUnSupportedByDecoder=%d",
            mVideoUnSupportedByDecoder);
}

bool MtkMPEG2TSExtractor::getVideoState(void) {
    ALOGE("getVideoState  mVideoUnSupportedByDecoder=%d",
          mVideoUnSupportedByDecoder);
    return mVideoUnSupportedByDecoder;
}
#endif
// end of add for mtk
}  // namespace android
