/*
 * Copyright (C) 2012 The Android Open Source Project
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
#define LOG_TAG "GenericSource"

#include "GenericSource.h"
#include "NuPlayerDrm.h"

#include "AnotherPacketSource.h"
#include <binder/IServiceManager.h>
#include <cutils/properties.h>
#include <media/IMediaExtractorService.h>
#include <media/IMediaHTTPService.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include "../../libstagefright/include/NuCachedSource2.h"
#include "../../libstagefright/include/HTTPBase.h"

#ifdef MTK_DRM_APP
#include <drmutils/drm_utils_mtk.h>
#endif
#include <media/MtkMMLog.h>

namespace android {

static const int kLowWaterMarkMs          = 2000;  // 2secs
static const int kHighWaterMarkMs         = 5000;  // 5secs
static const int kHighWaterMarkRebufferMs = 15000;  // 15secs

static const int kLowWaterMarkKB  = 40;
static const int kHighWaterMarkKB = 200;

NuPlayer::GenericSource::GenericSource(
        const sp<AMessage> &notify,
        bool uidValid,
        uid_t uid)
    : Source(notify),
      mAudioTimeUs(0),
      mAudioLastDequeueTimeUs(0),
      mVideoTimeUs(0),
      mVideoLastDequeueTimeUs(0),
      mFetchSubtitleDataGeneration(0),
      mFetchTimedTextDataGeneration(0),
      mDurationUs(-1ll),
      mAudioIsVorbis(false),
      mIsSecure(false),
      mIsStreaming(false),
      mUIDValid(uidValid),
      mUID(uid),
      mFd(-1),
      mBitrate(-1ll),
      mPendingReadBufferTypes(0) {
    ALOGV("GenericSource");

    mBufferingMonitor = new BufferingMonitor(notify);
    resetDataSource();
//  M Add for support OMA DRM playback
    mDecryptHandle = NULL;
    mDrmManagerClient = NULL;
    mIsCurrentComplete = false;

    //init extra variant
    init();
}

void NuPlayer::GenericSource::resetDataSource() {
    ALOGV("resetDataSource");

    mHTTPService.clear();
    mHttpSource.clear();
    mUri.clear();
    mUriHeaders.clear();
    if (mFd >= 0) {
        close(mFd);
        mFd = -1;
    }
    mOffset = 0;
    mLength = 0;
    mStarted = false;
    mStopRead = true;

    if (mBufferingMonitorLooper != NULL) {
        mBufferingMonitorLooper->unregisterHandler(mBufferingMonitor->id());
        mBufferingMonitorLooper->stop();
        mBufferingMonitorLooper = NULL;
    }
    mBufferingMonitor->stop();

    mIsDrmProtected = false;
    mIsDrmReleased = false;
    mIsSecure = false;
    mMimes.clear();
#ifdef MTK_DRM_APP
    setDrmPlaybackStatusIfNeeded(Playback::STOP, 0);
    mDecryptHandle = NULL;
    mDrmManagerClient = NULL;
    mIsCurrentComplete = false;
#endif
}

status_t NuPlayer::GenericSource::setDataSource(
        const sp<IMediaHTTPService> &httpService,
        const char *url,
        const KeyedVector<String8, String8> *headers) {
    ALOGV("setDataSource url: %s", url);

    resetDataSource();

    mHTTPService = httpService;
    mUri = url;

    if (headers) {
        mUriHeaders = *headers;
    }

    // delay data source creation to prepareAsync() to avoid blocking
    // the calling thread in setDataSource for any significant time.
    return OK;
}

status_t NuPlayer::GenericSource::setDataSource(
        int fd, int64_t offset, int64_t length) {
    ALOGV("setDataSource %d/%lld/%lld", fd, (long long)offset, (long long)length);

    resetDataSource();

    mFd = dup(fd);
    mOffset = offset;
    mLength = length;

    // delay data source creation to prepareAsync() to avoid blocking
    // the calling thread in setDataSource for any significant time.
    return OK;
}

status_t NuPlayer::GenericSource::setDataSource(const sp<DataSource>& source) {
    ALOGV("setDataSource (source: %p)", source.get());

    resetDataSource();
    mDataSource = source;
    return OK;
}

sp<MetaData> NuPlayer::GenericSource::getFileFormatMeta() const {
    return mFileMeta;
}

status_t NuPlayer::GenericSource::initFromDataSource() {
    sp<IMediaExtractor> extractor;
    CHECK(mDataSource != NULL);

    extractor = MediaExtractor::Create(mDataSource, NULL);

    if (extractor == NULL) {
        ALOGE("initFromDataSource, cannot create extractor!");
        return UNKNOWN_ERROR;
    }

#ifdef MTK_DRM_APP
    mDataSource->getDrmInfo(mDecryptHandle, &mDrmManagerClient);
    if (mDecryptHandle.get() != NULL && DecryptApiType::CONTAINER_BASED == mDecryptHandle->decryptApiType
        && RightsStatus::RIGHTS_VALID != mDecryptHandle->status) {
            sp<AMessage> msg = dupNotify();
            msg->setInt32("what", kWhatDrmNoLicense);
            msg->post();
    }
#endif
    mFileMeta = extractor->getMetaData();
    if (mFileMeta != NULL) {
        int64_t duration;
        if (mFileMeta->findInt64(kKeyDuration, &duration)) {
            mDurationUs = duration;
        }
    }

    int32_t totalBitrate = 0;

    size_t numtracks = extractor->countTracks();
    if (numtracks == 0) {
        ALOGE("initFromDataSource, source has no track!");
        return UNKNOWN_ERROR;
    }

    mMimes.clear();

    for (size_t i = 0; i < numtracks; ++i) {
        //mtkadd+ tricky to set flag before gettrack
        if (mIsMtkMusic) {
            sp<MetaData> mp3Meta = extractor->getTrackMetaData(i, MediaExtractor::kIncludeMp3LowPowerInfo);
            ALOGD("set mp3 low power");
        }

        sp<IMediaSource> track = extractor->getTrack(i);
        if (track == NULL) {
            continue;
        }

        sp<MetaData> meta = extractor->getTrackMetaData(i);
        if (meta == NULL) {
            ALOGE("no metadata for track %zu", i);
            return UNKNOWN_ERROR;
        }

        const char *mime;
        CHECK(meta->findCString(kKeyMIMEType, &mime));

        ALOGV("initFromDataSource track[%zu]: %s", i, mime);

        // Do the string compare immediately with "mime",
        // we can't assume "mime" would stay valid after another
        // extractor operation, some extractors might modify meta
        // during getTrack() and make it invalid.
        if (!strncasecmp(mime, "audio/", 6)) {
            if (mAudioTrack.mSource == NULL) {
                mAudioTrack.mIndex = i;
                mAudioTrack.mSource = track;
                mAudioTrack.mPackets =
                    new AnotherPacketSource(mAudioTrack.mSource->getFormat());

                if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_VORBIS)) {
                    mAudioIsVorbis = true;
                } else {
                    mAudioIsVorbis = false;
                }

                mMimes.add(String8(mime));
            }
        } else if (!strncasecmp(mime, "video/", 6)) {
            if (mVideoTrack.mSource == NULL) {
                mVideoTrack.mIndex = i;
                mVideoTrack.mSource = track;
                mVideoTrack.mPackets =
                    new AnotherPacketSource(mVideoTrack.mSource->getFormat());

                // video always at the beginning
                mMimes.insertAt(String8(mime), 0);
            }
        }

        mSources.push(track);
        int64_t durationUs;
        if (meta->findInt64(kKeyDuration, &durationUs)) {
            if (durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }

        int32_t bitrate;
        if (totalBitrate >= 0 && meta->findInt32(kKeyBitRate, &bitrate)) {
            totalBitrate += bitrate;
        } else {
            totalBitrate = -1;
        }
    }

    ALOGV("initFromDataSource mSources.size(): %zu  mIsSecure: %d  mime[0]: %s", mSources.size(),
            mIsSecure, (mMimes.isEmpty() ? "NONE" : mMimes[0].string()));

    if (mSources.size() == 0) {
        ALOGE("b/23705695");
        return UNKNOWN_ERROR;
    }

    // Modular DRM: The return value doesn't affect source initialization.
    (void)checkDrmInfo();

    mBitrate = totalBitrate;

    return OK;
}

status_t NuPlayer::GenericSource::getDefaultBufferingSettings(
        BufferingSettings* buffering /* nonnull */) {
    mBufferingMonitor->getDefaultBufferingSettings(buffering);
    return OK;
}

status_t NuPlayer::GenericSource::setBufferingSettings(const BufferingSettings& buffering) {
    return mBufferingMonitor->setBufferingSettings(buffering);
}

status_t NuPlayer::GenericSource::startSources() {
    // Start the selected A/V tracks now before we start buffering.
    // Widevine sources might re-initialize crypto when starting, if we delay
    // this to start(), all data buffered during prepare would be wasted.
    // (We don't actually start reading until start().)
    //
    // TODO: this logic may no longer be relevant after the removal of widevine
    // support
    if (mAudioTrack.mSource != NULL && mAudioTrack.mSource->start() != OK) {
        ALOGE("failed to start audio track!");
        return UNKNOWN_ERROR;
    }

    if (mVideoTrack.mSource != NULL && mVideoTrack.mSource->start() != OK) {
        ALOGE("failed to start video track!");
        return UNKNOWN_ERROR;
    }

    return OK;
}

int64_t NuPlayer::GenericSource::getLastReadPosition() {
    if (mAudioTrack.mSource != NULL) {
        return mAudioTimeUs;
    } else if (mVideoTrack.mSource != NULL) {
        return mVideoTimeUs;
    } else {
        return 0;
    }
}

status_t NuPlayer::GenericSource::setBuffers(
        bool audio, Vector<MediaBuffer *> &buffers) {
    if (mIsSecure && !audio && mVideoTrack.mSource != NULL) {
        return mVideoTrack.mSource->setBuffers(buffers);
    }
    return INVALID_OPERATION;
}

bool NuPlayer::GenericSource::isStreaming() const {
    return mIsStreaming;
}

void NuPlayer::GenericSource::setOffloadAudio(bool offload) {
    mBufferingMonitor->setOffloadAudio(offload);
}

NuPlayer::GenericSource::~GenericSource() {
    ALOGV("~GenericSource");
    if (mLooper != NULL) {
        mLooper->unregisterHandler(id());
        mLooper->stop();
    }
    resetDataSource();
}

void NuPlayer::GenericSource::prepareAsync() {
    ALOGV("prepareAsync: (looper: %d)", (mLooper != NULL));

    if (mLooper == NULL) {
        mLooper = new ALooper;
        mLooper->setName("generic");
        mLooper->start();

        mLooper->registerHandler(this);
    }

    sp<AMessage> msg = new AMessage(kWhatPrepareAsync, this);
    msg->post();
}

void NuPlayer::GenericSource::onPrepareAsync() {
    ALOGV("onPrepareAsync: mDataSource: %d", (mDataSource != NULL));

    // delayed data source creation
    if (mDataSource == NULL) {
        // set to false first, if the extractor
        // comes back as secure, set it to true then.
        mIsSecure = false;

        if (!mUri.empty()) {
            const char* uri = mUri.c_str();
            String8 contentType;

            if (!strncasecmp("http://", uri, 7) || !strncasecmp("https://", uri, 8)) {
                mHttpSource = DataSource::CreateMediaHTTP(mHTTPService);
                if (mHttpSource == NULL) {
                    ALOGE("Failed to create http source!");
                    notifyPreparedAndCleanup(UNKNOWN_ERROR);
                    return;
                }
            }

            mDataSource = DataSource::CreateFromURI(
                   mHTTPService, uri, &mUriHeaders, &contentType,
                   static_cast<HTTPBase *>(mHttpSource.get()));
        } else {
            if (property_get_bool("media.stagefright.extractremote", true) &&
                    !FileSource::requiresDrm(mFd, mOffset, mLength, nullptr /* mime */)) {
                sp<IBinder> binder =
                        defaultServiceManager()->getService(String16("media.extractor"));
                if (binder != nullptr) {
                    ALOGD("FileSource remote");
                    sp<IMediaExtractorService> mediaExService(
                            interface_cast<IMediaExtractorService>(binder));
                    sp<IDataSource> source =
                            mediaExService->makeIDataSource(mFd, mOffset, mLength);
                    ALOGV("IDataSource(FileSource): %p %d %lld %lld",
                            source.get(), mFd, (long long)mOffset, (long long)mLength);
                    if (source.get() != nullptr) {
                        mDataSource = DataSource::CreateFromIDataSource(source);
                        if (mDataSource != nullptr) {
                            // Close the local file descriptor as it is not needed anymore.
                            close(mFd);
                            mFd = -1;
                        }
                    } else {
                        ALOGW("extractor service cannot make data source");
                    }
                } else {
                    ALOGW("extractor service not running");
                }
            }
            if (mDataSource == nullptr) {
                ALOGD("FileSource local");
                mDataSource = new FileSource(mFd, mOffset, mLength);
            }
            // TODO: close should always be done on mFd, see the lines following
            // DataSource::CreateFromIDataSource above,
            // and the FileSource constructor should dup the mFd argument as needed.
            mFd = -1;
        }

        if (mDataSource == NULL) {
            ALOGE("Failed to create data source!");
            notifyPreparedAndCleanup(UNKNOWN_ERROR);
            return;
        }
    }

    if (mDataSource->flags() & DataSource::kIsCachingDataSource) {
        mCachedSource = static_cast<NuCachedSource2 *>(mDataSource.get());
    }

    // For cached streaming cases, we need to wait for enough
    // buffering before reporting prepared.
    mIsStreaming = (mCachedSource != NULL);

    // init extractor from data source
    status_t err = initFromDataSource();

    if (err != OK) {
        ALOGE("Failed to init from data source!");
        notifyPreparedAndCleanup(err);
        return;
    }

    if (mVideoTrack.mSource != NULL) {
        sp<MetaData> meta = doGetFormatMeta(false /* audio */);
        sp<AMessage> msg = new AMessage;
        err = convertMetaDataToMessage(meta, &msg);
        if(err != OK) {
            notifyPreparedAndCleanup(err);
            return;
        }
        notifyVideoSizeChanged(msg);
    }

#ifdef MTK_DRM_APP  // OMA DRM v1 implementation: consume rights.
        mIsCurrentComplete = false;
        ConsumeRight(mDecryptHandle, mDrmManagerClient, mDrmProcName);
#endif
    notifyFlagsChanged(
            // FLAG_SECURE will be known if/when prepareDrm is called by the app
            // FLAG_PROTECTED will be known if/when prepareDrm is called by the app
            FLAG_CAN_PAUSE |
            FLAG_CAN_SEEK_BACKWARD |
            FLAG_CAN_SEEK_FORWARD |
            FLAG_CAN_SEEK);

    finishPrepareAsync();

    ALOGV("onPrepareAsync: Done");
}

void NuPlayer::GenericSource::finishPrepareAsync() {
    ALOGV("finishPrepareAsync");

    status_t err = startSources();
    if (err != OK) {
        ALOGE("Failed to init start data source!");
        notifyPreparedAndCleanup(err);
        return;
    }

    if (mIsStreaming) {
        if (mBufferingMonitorLooper == NULL) {
            mBufferingMonitor->prepare(mCachedSource, mDurationUs, mBitrate,
                    mIsStreaming);

            mBufferingMonitorLooper = new ALooper;
            mBufferingMonitorLooper->setName("GSBMonitor");
            mBufferingMonitorLooper->start();
            mBufferingMonitorLooper->registerHandler(mBufferingMonitor);
        }

        mBufferingMonitor->ensureCacheIsFetching();
        mBufferingMonitor->restartPollBuffering();
    } else {
        notifyPrepared();
    }
}

void NuPlayer::GenericSource::notifyPreparedAndCleanup(status_t err) {
    if (err != OK) {
        {
            sp<DataSource> dataSource = mDataSource;
            sp<NuCachedSource2> cachedSource = mCachedSource;
            sp<DataSource> httpSource = mHttpSource;
            {
                Mutex::Autolock _l(mDisconnectLock);
                mDataSource.clear();
                mCachedSource.clear();
                mHttpSource.clear();
#ifdef MTK_DRM_APP
                mDecryptHandle = NULL;
                mDrmManagerClient = NULL;
#endif
            }
        }
        mBitrate = -1;

        mBufferingMonitor->cancelPollBuffering();
    }
    notifyPrepared(err);
}

void NuPlayer::GenericSource::start() {
    ALOGI("start");

    mStopRead = false;
    if (mAudioTrack.mSource != NULL) {
        postReadBuffer(MEDIA_TRACK_TYPE_AUDIO);
    }

    if (mVideoTrack.mSource != NULL) {
        postReadBuffer(MEDIA_TRACK_TYPE_VIDEO);
    }

    mStarted = true;
#ifdef MTK_DRM_APP
    setDrmPlaybackStatusIfNeeded(Playback::START, getLastReadPosition() / 1000);
    if (mIsCurrentComplete) {    // single recursive mode
        ConsumeRight(mDecryptHandle, mDrmManagerClient, mDrmProcName);
        mIsCurrentComplete = false;
    }
#endif

    (new AMessage(kWhatStart, this))->post();
}

void NuPlayer::GenericSource::stop() {
#ifdef MTK_DRM_APP
    setDrmPlaybackStatusIfNeeded(Playback::STOP, 0);
    mIsCurrentComplete = true;
#endif
    mStarted = false;
}

void NuPlayer::GenericSource::pause() {
#ifdef MTK_DRM_APP
    setDrmPlaybackStatusIfNeeded(Playback::PAUSE, 0);
#endif
    mStarted = false;
}

void NuPlayer::GenericSource::resume() {
#ifdef MTK_DRM_APP
    setDrmPlaybackStatusIfNeeded(Playback::START, getLastReadPosition() / 1000);
#endif
    mStarted = true;

    (new AMessage(kWhatResume, this))->post();
}

void NuPlayer::GenericSource::disconnect() {
    sp<DataSource> dataSource, httpSource;
    {
        Mutex::Autolock _l(mDisconnectLock);
        dataSource = mDataSource;
        httpSource = mHttpSource;
    }

    if (dataSource != NULL) {
        // disconnect data source
        if (dataSource->flags() & DataSource::kIsCachingDataSource) {
            static_cast<NuCachedSource2 *>(dataSource.get())->disconnect();
        }
    } else if (httpSource != NULL) {
        static_cast<HTTPBase *>(httpSource.get())->disconnect();
    }
}

#ifdef MTK_DRM_APP
void NuPlayer::GenericSource::setDrmPlaybackStatusIfNeeded(int playbackStatus, int64_t position) {
    if (mDecryptHandle != NULL) {
        mDrmManagerClient->setPlaybackStatus(mDecryptHandle, playbackStatus, position);
    }
}
#endif
status_t NuPlayer::GenericSource::feedMoreTSData() {
    return OK;
}

void NuPlayer::GenericSource::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
      case kWhatPrepareAsync:
      {
          onPrepareAsync();
          break;
      }
      case kWhatFetchSubtitleData:
      {
          fetchTextData(kWhatSendSubtitleData, MEDIA_TRACK_TYPE_SUBTITLE,
                  mFetchSubtitleDataGeneration, mSubtitleTrack.mPackets, msg);
          break;
      }

      case kWhatFetchTimedTextData:
      {
          fetchTextData(kWhatSendTimedTextData, MEDIA_TRACK_TYPE_TIMEDTEXT,
                  mFetchTimedTextDataGeneration, mTimedTextTrack.mPackets, msg);
          break;
      }

      case kWhatSendSubtitleData:
      {
          sendTextData(kWhatSubtitleData, MEDIA_TRACK_TYPE_SUBTITLE,
                  mFetchSubtitleDataGeneration, mSubtitleTrack.mPackets, msg);
          break;
      }

      case kWhatSendGlobalTimedTextData:
      {
          sendGlobalTextData(kWhatTimedTextData, mFetchTimedTextDataGeneration, msg);
          break;
      }
      case kWhatSendTimedTextData:
      {
          sendTextData(kWhatTimedTextData, MEDIA_TRACK_TYPE_TIMEDTEXT,
                  mFetchTimedTextDataGeneration, mTimedTextTrack.mPackets, msg);
          break;
      }

      case kWhatChangeAVSource:
      {
          int32_t trackIndex;
          CHECK(msg->findInt32("trackIndex", &trackIndex));
          const sp<IMediaSource> source = mSources.itemAt(trackIndex);

          Track* track;
          const char *mime;
          media_track_type trackType, counterpartType;
          sp<MetaData> meta = source->getFormat();
          meta->findCString(kKeyMIMEType, &mime);
          if (!strncasecmp(mime, "audio/", 6)) {
              track = &mAudioTrack;
              trackType = MEDIA_TRACK_TYPE_AUDIO;
              counterpartType = MEDIA_TRACK_TYPE_VIDEO;;
          } else {
              CHECK(!strncasecmp(mime, "video/", 6));
              track = &mVideoTrack;
              trackType = MEDIA_TRACK_TYPE_VIDEO;
              counterpartType = MEDIA_TRACK_TYPE_AUDIO;;
          }


          if (track->mSource != NULL) {
              track->mSource->stop();
          }
          track->mSource = source;
          track->mSource->start();
          track->mIndex = trackIndex;

          int64_t timeUs, actualTimeUs;
          const bool formatChange = true;
          if (trackType == MEDIA_TRACK_TYPE_AUDIO) {
              timeUs = mAudioLastDequeueTimeUs;
          } else {
              timeUs = mVideoLastDequeueTimeUs;
          }
          readBuffer(trackType, timeUs, MediaPlayerSeekMode::SEEK_PREVIOUS_SYNC /* mode */,
                  &actualTimeUs, formatChange);
          readBuffer(counterpartType, -1, MediaPlayerSeekMode::SEEK_PREVIOUS_SYNC /* mode */,
                  NULL, !formatChange);
          ALOGV("timeUs %lld actualTimeUs %lld", (long long)timeUs, (long long)actualTimeUs);

          break;
      }

      case kWhatStart:
      case kWhatResume:
      {
          mBufferingMonitor->restartPollBuffering();
          break;
      }

      case kWhatGetFormat:
      {
          onGetFormatMeta(msg);
          break;
      }

      case kWhatGetSelectedTrack:
      {
          onGetSelectedTrack(msg);
          break;
      }

      case kWhatGetTrackInfo:
      {
          onGetTrackInfo(msg);
          break;
      }

      case kWhatSelectTrack:
      {
          onSelectTrack(msg);
          break;
      }

      case kWhatSeek:
      {
          onSeek(msg);
          break;
      }

      case kWhatReadBuffer:
      {
          onReadBuffer(msg);
          break;
      }

      case kWhatPrepareDrm:
      {
          status_t status = onPrepareDrm(msg);
          sp<AMessage> response = new AMessage;
          response->setInt32("status", status);
          sp<AReplyToken> replyID;
          CHECK(msg->senderAwaitsResponse(&replyID));
          response->postReply(replyID);
          break;
      }

      case kWhatReleaseDrm:
      {
          status_t status = onReleaseDrm();
          sp<AMessage> response = new AMessage;
          response->setInt32("status", status);
          sp<AReplyToken> replyID;
          CHECK(msg->senderAwaitsResponse(&replyID));
          response->postReply(replyID);
          break;
      }

      default:
          Source::onMessageReceived(msg);
          break;
    }
}

void NuPlayer::GenericSource::fetchTextData(
        uint32_t sendWhat,
        media_track_type type,
        int32_t curGen,
        const sp<AnotherPacketSource>& packets,
        const sp<AMessage>& msg) {
    int32_t msgGeneration;
    CHECK(msg->findInt32("generation", &msgGeneration));
    if (msgGeneration != curGen) {
        // stale
        return;
    }

    int32_t avail;
    if (packets->hasBufferAvailable(&avail)) {
        return;
    }

    int64_t timeUs;
    CHECK(msg->findInt64("timeUs", &timeUs));

    int64_t subTimeUs;
    readBuffer(type, timeUs, MediaPlayerSeekMode::SEEK_PREVIOUS_SYNC /* mode */, &subTimeUs);

    int64_t delayUs = subTimeUs - timeUs;
    if (msg->what() == kWhatFetchSubtitleData) {
        const int64_t oneSecUs = 1000000ll;
        delayUs -= oneSecUs;
    }
    sp<AMessage> msg2 = new AMessage(sendWhat, this);
    msg2->setInt32("generation", msgGeneration);
    msg2->post(delayUs < 0 ? 0 : delayUs);
}

void NuPlayer::GenericSource::sendTextData(
        uint32_t what,
        media_track_type type,
        int32_t curGen,
        const sp<AnotherPacketSource>& packets,
        const sp<AMessage>& msg) {
    int32_t msgGeneration;
    CHECK(msg->findInt32("generation", &msgGeneration));
    if (msgGeneration != curGen) {
        // stale
        return;
    }

    int64_t subTimeUs;
    if (packets->nextBufferTime(&subTimeUs) != OK) {
        return;
    }

    int64_t nextSubTimeUs;
    readBuffer(type, -1, MediaPlayerSeekMode::SEEK_PREVIOUS_SYNC /* mode */, &nextSubTimeUs);

    sp<ABuffer> buffer;
    status_t dequeueStatus = packets->dequeueAccessUnit(&buffer);
    if (dequeueStatus == OK) {
        sp<AMessage> notify = dupNotify();
        notify->setInt32("what", what);
        notify->setBuffer("buffer", buffer);
        notify->post();

        const int64_t delayUs = nextSubTimeUs - subTimeUs;
        msg->post(delayUs < 0 ? 0 : delayUs);
    }
}

void NuPlayer::GenericSource::sendGlobalTextData(
        uint32_t what,
        int32_t curGen,
        sp<AMessage> msg) {
    int32_t msgGeneration;
    CHECK(msg->findInt32("generation", &msgGeneration));
    if (msgGeneration != curGen) {
        // stale
        return;
    }

    uint32_t textType;
    const void *data;
    size_t size = 0;
    if (mTimedTextTrack.mSource->getFormat()->findData(
                    kKeyTextFormatData, &textType, &data, &size)) {
        mGlobalTimedText = new ABuffer(size);
        if (mGlobalTimedText->data()) {
            memcpy(mGlobalTimedText->data(), data, size);
            sp<AMessage> globalMeta = mGlobalTimedText->meta();
            globalMeta->setInt64("timeUs", 0);
            globalMeta->setString("mime", MEDIA_MIMETYPE_TEXT_3GPP);
            globalMeta->setInt32("global", 1);
            sp<AMessage> notify = dupNotify();
            notify->setInt32("what", what);
            notify->setBuffer("buffer", mGlobalTimedText);
            notify->post();
        }
    }
}

sp<MetaData> NuPlayer::GenericSource::getFormatMeta(bool audio) {
    sp<AMessage> msg = new AMessage(kWhatGetFormat, this);
    msg->setInt32("audio", audio);

    sp<AMessage> response;
    sp<RefBase> format;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findObject("format", &format));
        return static_cast<MetaData*>(format.get());
    } else {
        return NULL;
    }
}

void NuPlayer::GenericSource::onGetFormatMeta(const sp<AMessage>& msg) const {
    int32_t audio;
    CHECK(msg->findInt32("audio", &audio));

    sp<AMessage> response = new AMessage;
    sp<MetaData> format = doGetFormatMeta(audio);
    response->setObject("format", format);

    sp<AReplyToken> replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

sp<MetaData> NuPlayer::GenericSource::doGetFormatMeta(bool audio) const {
    sp<IMediaSource> source = audio ? mAudioTrack.mSource : mVideoTrack.mSource;

    if (source == NULL) {
        return NULL;
    }

    return source->getFormat();
}

status_t NuPlayer::GenericSource::dequeueAccessUnit(
        bool audio, sp<ABuffer> *accessUnit) {
    if (audio && !mStarted) {
        return -EWOULDBLOCK;
    }

    // If has gone through stop/releaseDrm sequence, we no longer send down any buffer b/c
    // the codec's crypto object has gone away (b/37960096).
    // Note: This will be unnecessary when stop() changes behavior and releases codec (b/35248283).
    if (!mStarted && mIsDrmReleased) {
        return -EWOULDBLOCK;
    }

    Track *track = audio ? &mAudioTrack : &mVideoTrack;

    if (track->mSource == NULL) {
        return -EWOULDBLOCK;
    }

    status_t finalResult;
    if (!track->mPackets->hasBufferAvailable(&finalResult)) {
        if (finalResult == OK) {
            postReadBuffer(
                    audio ? MEDIA_TRACK_TYPE_AUDIO : MEDIA_TRACK_TYPE_VIDEO);
            return -EWOULDBLOCK;
        }
        return finalResult;
    }

    status_t result = track->mPackets->dequeueAccessUnit(accessUnit);

    // start pulling in more buffers if we only have one (or no) buffer left
    // so that decoder has less chance of being starved
    if (track->mPackets->getAvailableBufferCount(&finalResult) < 2) {
        postReadBuffer(audio? MEDIA_TRACK_TYPE_AUDIO : MEDIA_TRACK_TYPE_VIDEO);
    }

    if (result != OK) {
        if (mSubtitleTrack.mSource != NULL) {
            mSubtitleTrack.mPackets->clear();
            mFetchSubtitleDataGeneration++;
        }
        if (mTimedTextTrack.mSource != NULL) {
            mTimedTextTrack.mPackets->clear();
            mFetchTimedTextDataGeneration++;
        }
        return result;
    }

    int64_t timeUs;
    status_t eosResult; // ignored
    CHECK((*accessUnit)->meta()->findInt64("timeUs", &timeUs));
    if (audio) {
        mAudioLastDequeueTimeUs = timeUs;
        mBufferingMonitor->updateDequeuedBufferTime(timeUs);
    } else {
        mVideoLastDequeueTimeUs = timeUs;
    }

    if (mSubtitleTrack.mSource != NULL
            && !mSubtitleTrack.mPackets->hasBufferAvailable(&eosResult)) {
        sp<AMessage> msg = new AMessage(kWhatFetchSubtitleData, this);
        msg->setInt64("timeUs", timeUs);
        msg->setInt32("generation", mFetchSubtitleDataGeneration);
        msg->post();
    }

    if (mTimedTextTrack.mSource != NULL
            && !mTimedTextTrack.mPackets->hasBufferAvailable(&eosResult)) {
        sp<AMessage> msg = new AMessage(kWhatFetchTimedTextData, this);
        msg->setInt64("timeUs", timeUs);
        msg->setInt32("generation", mFetchTimedTextDataGeneration);
        msg->post();
    }

    return result;
}

status_t NuPlayer::GenericSource::getDuration(int64_t *durationUs) {
    *durationUs = mDurationUs;
    return OK;
}

size_t NuPlayer::GenericSource::getTrackCount() const {
    return mSources.size();
}

sp<AMessage> NuPlayer::GenericSource::getTrackInfo(size_t trackIndex) const {
    sp<AMessage> msg = new AMessage(kWhatGetTrackInfo, this);
    msg->setSize("trackIndex", trackIndex);

    sp<AMessage> response;
    sp<RefBase> format;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findObject("format", &format));
        return static_cast<AMessage*>(format.get());
    } else {
        return NULL;
    }
}

void NuPlayer::GenericSource::onGetTrackInfo(const sp<AMessage>& msg) const {
    size_t trackIndex;
    CHECK(msg->findSize("trackIndex", &trackIndex));

    sp<AMessage> response = new AMessage;
    sp<AMessage> format = doGetTrackInfo(trackIndex);
    response->setObject("format", format);

    sp<AReplyToken> replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

sp<AMessage> NuPlayer::GenericSource::doGetTrackInfo(size_t trackIndex) const {
    size_t trackCount = mSources.size();
    if (trackIndex >= trackCount) {
        return NULL;
    }

    sp<AMessage> format = new AMessage();
    sp<MetaData> meta = mSources.itemAt(trackIndex)->getFormat();
    if (meta == NULL) {
        ALOGE("no metadata for track %zu", trackIndex);
        return NULL;
    }

    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));
    format->setString("mime", mime);

    int32_t trackType;
    if (!strncasecmp(mime, "video/", 6)) {
        trackType = MEDIA_TRACK_TYPE_VIDEO;
    } else if (!strncasecmp(mime, "audio/", 6)) {
        trackType = MEDIA_TRACK_TYPE_AUDIO;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP)) {
        trackType = MEDIA_TRACK_TYPE_TIMEDTEXT;
    } else {
        trackType = MEDIA_TRACK_TYPE_UNKNOWN;
    }
    format->setInt32("type", trackType);

    const char *lang;
    if (!meta->findCString(kKeyMediaLanguage, &lang)) {
        lang = "und";
    }
    format->setString("language", lang);

    if (trackType == MEDIA_TRACK_TYPE_SUBTITLE) {
        int32_t isAutoselect = 1, isDefault = 0, isForced = 0;
        meta->findInt32(kKeyTrackIsAutoselect, &isAutoselect);
        meta->findInt32(kKeyTrackIsDefault, &isDefault);
        meta->findInt32(kKeyTrackIsForced, &isForced);

        format->setInt32("auto", !!isAutoselect);
        format->setInt32("default", !!isDefault);
        format->setInt32("forced", !!isForced);
    }

    return format;
}

ssize_t NuPlayer::GenericSource::getSelectedTrack(media_track_type type) const {
    sp<AMessage> msg = new AMessage(kWhatGetSelectedTrack, this);
    msg->setInt32("type", type);

    sp<AMessage> response;
    int32_t index;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findInt32("index", &index));
        return index;
    } else {
        return -1;
    }
}

void NuPlayer::GenericSource::onGetSelectedTrack(const sp<AMessage>& msg) const {
    int32_t tmpType;
    CHECK(msg->findInt32("type", &tmpType));
    media_track_type type = (media_track_type)tmpType;

    sp<AMessage> response = new AMessage;
    ssize_t index = doGetSelectedTrack(type);
    response->setInt32("index", index);

    sp<AReplyToken> replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

ssize_t NuPlayer::GenericSource::doGetSelectedTrack(media_track_type type) const {
    const Track *track = NULL;
    switch (type) {
    case MEDIA_TRACK_TYPE_VIDEO:
        track = &mVideoTrack;
        break;
    case MEDIA_TRACK_TYPE_AUDIO:
        track = &mAudioTrack;
        break;
    case MEDIA_TRACK_TYPE_TIMEDTEXT:
        track = &mTimedTextTrack;
        break;
    case MEDIA_TRACK_TYPE_SUBTITLE:
        track = &mSubtitleTrack;
        break;
    default:
        break;
    }

    if (track != NULL && track->mSource != NULL) {
        return track->mIndex;
    }

    return -1;
}

status_t NuPlayer::GenericSource::selectTrack(size_t trackIndex, bool select, int64_t timeUs) {
    ALOGV("%s track: %zu", select ? "select" : "deselect", trackIndex);
    sp<AMessage> msg = new AMessage(kWhatSelectTrack, this);
    msg->setInt32("trackIndex", trackIndex);
    msg->setInt32("select", select);
    msg->setInt64("timeUs", timeUs);

    sp<AMessage> response;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findInt32("err", &err));
    }

    return err;
}

void NuPlayer::GenericSource::onSelectTrack(const sp<AMessage>& msg) {
    int32_t trackIndex, select;
    int64_t timeUs;
    CHECK(msg->findInt32("trackIndex", &trackIndex));
    CHECK(msg->findInt32("select", &select));
    CHECK(msg->findInt64("timeUs", &timeUs));

    sp<AMessage> response = new AMessage;
    status_t err = doSelectTrack(trackIndex, select, timeUs);
    response->setInt32("err", err);

    sp<AReplyToken> replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

status_t NuPlayer::GenericSource::doSelectTrack(size_t trackIndex, bool select, int64_t timeUs) {
    if (trackIndex >= mSources.size()) {
        return BAD_INDEX;
    }

    if (!select) {
        Track* track = NULL;
        if (mSubtitleTrack.mSource != NULL && trackIndex == mSubtitleTrack.mIndex) {
            track = &mSubtitleTrack;
            mFetchSubtitleDataGeneration++;
        } else if (mTimedTextTrack.mSource != NULL && trackIndex == mTimedTextTrack.mIndex) {
            track = &mTimedTextTrack;
            mFetchTimedTextDataGeneration++;
        }
        if (track == NULL) {
            return INVALID_OPERATION;
        }
        track->mSource->stop();
        track->mSource = NULL;
        track->mPackets->clear();
        return OK;
    }

    const sp<IMediaSource> source = mSources.itemAt(trackIndex);
    sp<MetaData> meta = source->getFormat();
    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));
    if (!strncasecmp(mime, "text/", 5)) {
        bool isSubtitle = strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP);
        Track *track = isSubtitle ? &mSubtitleTrack : &mTimedTextTrack;
        if (track->mSource != NULL && track->mIndex == trackIndex) {
            return OK;
        }
        track->mIndex = trackIndex;
        if (track->mSource != NULL) {
            track->mSource->stop();
        }
        track->mSource = mSources.itemAt(trackIndex);
        track->mSource->start();
        if (track->mPackets == NULL) {
            track->mPackets = new AnotherPacketSource(track->mSource->getFormat());
        } else {
            track->mPackets->clear();
            track->mPackets->setFormat(track->mSource->getFormat());

        }

        if (isSubtitle) {
            mFetchSubtitleDataGeneration++;
        } else {
            mFetchTimedTextDataGeneration++;
        }

        status_t eosResult; // ignored
        if (mSubtitleTrack.mSource != NULL
                && !mSubtitleTrack.mPackets->hasBufferAvailable(&eosResult)) {
            sp<AMessage> msg = new AMessage(kWhatFetchSubtitleData, this);
            msg->setInt64("timeUs", timeUs);
            msg->setInt32("generation", mFetchSubtitleDataGeneration);
            msg->post();
        }

        sp<AMessage> msg2 = new AMessage(kWhatSendGlobalTimedTextData, this);
        msg2->setInt32("generation", mFetchTimedTextDataGeneration);
        msg2->post();

        if (mTimedTextTrack.mSource != NULL
                && !mTimedTextTrack.mPackets->hasBufferAvailable(&eosResult)) {
            sp<AMessage> msg = new AMessage(kWhatFetchTimedTextData, this);
            msg->setInt64("timeUs", timeUs);
            msg->setInt32("generation", mFetchTimedTextDataGeneration);
            msg->post();
        }

        return OK;
    } else if (!strncasecmp(mime, "audio/", 6) || !strncasecmp(mime, "video/", 6)) {
        bool audio = !strncasecmp(mime, "audio/", 6);
        Track *track = audio ? &mAudioTrack : &mVideoTrack;
        if (track->mSource != NULL && track->mIndex == trackIndex) {
            return OK;
        }

        sp<AMessage> msg = new AMessage(kWhatChangeAVSource, this);
        msg->setInt32("trackIndex", trackIndex);
        msg->post();
        return OK;
    }

    return INVALID_OPERATION;
}

status_t NuPlayer::GenericSource::seekTo(int64_t seekTimeUs, MediaPlayerSeekMode mode) {
    sp<AMessage> msg = new AMessage(kWhatSeek, this);
    msg->setInt64("seekTimeUs", seekTimeUs);
    msg->setInt32("mode", mode);

    sp<AMessage> response;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findInt32("err", &err));
    }

    return err;
}

void NuPlayer::GenericSource::onSeek(const sp<AMessage>& msg) {
    int64_t seekTimeUs;
    int32_t mode;
    CHECK(msg->findInt64("seekTimeUs", &seekTimeUs));
    CHECK(msg->findInt32("mode", &mode));

    sp<AMessage> response = new AMessage;
    status_t err = doSeek(seekTimeUs, (MediaPlayerSeekMode)mode);
    response->setInt32("err", err);

    sp<AReplyToken> replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

status_t NuPlayer::GenericSource::doSeek(int64_t seekTimeUs, MediaPlayerSeekMode mode) {
    mBufferingMonitor->updateDequeuedBufferTime(-1ll);

    // If the Widevine source is stopped, do not attempt to read any
    // more buffers.
    //
    // TODO: revisit after widevine is removed.  May be able to
    // combine mStopRead with mStarted.
    if (mStopRead) {
        return INVALID_OPERATION;
    }
    if (mVideoTrack.mSource != NULL) {
        int64_t actualTimeUs;
        readBuffer(MEDIA_TRACK_TYPE_VIDEO, seekTimeUs, mode, &actualTimeUs);

        if (mode != MediaPlayerSeekMode::SEEK_CLOSEST) {
            seekTimeUs = actualTimeUs;
        }
        mVideoLastDequeueTimeUs = actualTimeUs;
    }

    if (mAudioTrack.mSource != NULL) {
        readBuffer(MEDIA_TRACK_TYPE_AUDIO, seekTimeUs);
        mAudioLastDequeueTimeUs = seekTimeUs;
    }
#ifdef MTK_DRM_APP
    setDrmPlaybackStatusIfNeeded(Playback::START, seekTimeUs / 1000);
    if (!mStarted) {
        setDrmPlaybackStatusIfNeeded(Playback::PAUSE, 0);
    }
#endif

    if (mSubtitleTrack.mSource != NULL) {
        mSubtitleTrack.mPackets->clear();
        mFetchSubtitleDataGeneration++;
    }

    if (mTimedTextTrack.mSource != NULL) {
        mTimedTextTrack.mPackets->clear();
        mFetchTimedTextDataGeneration++;
    }

    // If currently buffering, post kWhatBufferingEnd first, so that
    // NuPlayer resumes. Otherwise, if cache hits high watermark
    // before new polling happens, no one will resume the playback.
    mBufferingMonitor->stopBufferingIfNecessary();
    mBufferingMonitor->restartPollBuffering();

    return OK;
}

sp<ABuffer> NuPlayer::GenericSource::mediaBufferToABuffer(
        MediaBuffer* mb,
        media_track_type trackType) {
    bool audio = trackType == MEDIA_TRACK_TYPE_AUDIO;
    size_t outLength = mb->range_length();

    if (audio && mAudioIsVorbis) {
        outLength += sizeof(int32_t);
    }

    sp<ABuffer> ab;

    if (mIsDrmProtected)   {
        // Modular DRM
        // Enabled for both video/audio so 1) media buffer is reused without extra copying
        // 2) meta data can be retrieved in onInputBufferFetched for calling queueSecureInputBuffer.

        // data is already provided in the buffer
        ab = new ABuffer(NULL, mb->range_length());
        mb->add_ref();
        ab->setMediaBufferBase(mb);

        // Modular DRM: Required b/c of the above add_ref.
        // If ref>0, there must be an observer, or it'll crash at release().
        // TODO: MediaBuffer might need to be revised to ease such need.
        mb->setObserver(this);
        // setMediaBufferBase() interestingly doesn't increment the ref count on its own.
        // Extra increment (since we want to keep mb alive and attached to ab beyond this function
        // call. This is to counter the effect of mb->release() towards the end.
        mb->add_ref();

    } else {
        ab = new ABuffer(outLength);
        memcpy(ab->data(),
               (const uint8_t *)mb->data() + mb->range_offset(),
               mb->range_length());
    }

    if (audio && mAudioIsVorbis) {
        int32_t numPageSamples;
        if (!mb->meta_data()->findInt32(kKeyValidSamples, &numPageSamples)) {
            numPageSamples = -1;
        }

        uint8_t* abEnd = ab->data() + mb->range_length();
        memcpy(abEnd, &numPageSamples, sizeof(numPageSamples));
    }

    sp<AMessage> meta = ab->meta();

    int64_t timeUs;
    CHECK(mb->meta_data()->findInt64(kKeyTime, &timeUs));
    meta->setInt64("timeUs", timeUs);

    if (trackType == MEDIA_TRACK_TYPE_VIDEO) {
        int32_t layerId;
        if (mb->meta_data()->findInt32(kKeyTemporalLayerId, &layerId)) {
            meta->setInt32("temporal-layer-id", layerId);
        }
    }

    if (trackType == MEDIA_TRACK_TYPE_TIMEDTEXT) {
        const char *mime;
        CHECK(mTimedTextTrack.mSource != NULL
                && mTimedTextTrack.mSource->getFormat()->findCString(kKeyMIMEType, &mime));
        meta->setString("mime", mime);
    }

    int64_t durationUs;
    if (mb->meta_data()->findInt64(kKeyDuration, &durationUs)) {
        meta->setInt64("durationUs", durationUs);
    }

    if (trackType == MEDIA_TRACK_TYPE_SUBTITLE) {
        meta->setInt32("trackIndex", mSubtitleTrack.mIndex);
    }

    uint32_t dataType; // unused
    const void *seiData;
    size_t seiLength;
    if (mb->meta_data()->findData(kKeySEI, &dataType, &seiData, &seiLength)) {
        sp<ABuffer> sei = ABuffer::CreateAsCopy(seiData, seiLength);;
        meta->setBuffer("sei", sei);
    }

    const void *mpegUserDataPointer;
    size_t mpegUserDataLength;
    if (mb->meta_data()->findData(
            kKeyMpegUserData, &dataType, &mpegUserDataPointer, &mpegUserDataLength)) {
        sp<ABuffer> mpegUserData = ABuffer::CreateAsCopy(mpegUserDataPointer, mpegUserDataLength);
        meta->setBuffer("mpegUserData", mpegUserData);
    }

    mb->release();
    mb = NULL;

    return ab;
}

void NuPlayer::GenericSource::postReadBuffer(media_track_type trackType) {
    Mutex::Autolock _l(mReadBufferLock);

    if ((mPendingReadBufferTypes & (1 << trackType)) == 0) {
        mPendingReadBufferTypes |= (1 << trackType);
        sp<AMessage> msg = new AMessage(kWhatReadBuffer, this);
        msg->setInt32("trackType", trackType);
        msg->post();
    }
}

void NuPlayer::GenericSource::onReadBuffer(const sp<AMessage>& msg) {
    int32_t tmpType;
    CHECK(msg->findInt32("trackType", &tmpType));
    media_track_type trackType = (media_track_type)tmpType;
    readBuffer(trackType);
    {
        // only protect the variable change, as readBuffer may
        // take considerable time.
        Mutex::Autolock _l(mReadBufferLock);
        mPendingReadBufferTypes &= ~(1 << trackType);
    }
}

void NuPlayer::GenericSource::readBuffer(
        media_track_type trackType, int64_t seekTimeUs, MediaPlayerSeekMode mode,
        int64_t *actualTimeUs, bool formatChange) {
    // Do not read data if Widevine source is stopped
    //
    // TODO: revisit after widevine is removed.  May be able to
    // combine mStopRead with mStarted.
    if (mStopRead) {
        return;
    }
    Track *track;
    size_t maxBuffers = 1;
    switch (trackType) {
        case MEDIA_TRACK_TYPE_VIDEO:
            track = &mVideoTrack;
            maxBuffers = 8;  // too large of a number may influence seeks
            break;
        case MEDIA_TRACK_TYPE_AUDIO:
            track = &mAudioTrack;
            maxBuffers = 64;
// add for mtk
            // add for TS, resolve 4k video play not smooth, too many buffers will cause parse audio too long time.
            // it will block video parse, as TS video and audio is interleave.
            if (isTS()) {
                maxBuffers = 8;
            }
// end of add for mtk
            break;
        case MEDIA_TRACK_TYPE_SUBTITLE:
            track = &mSubtitleTrack;
            break;
        case MEDIA_TRACK_TYPE_TIMEDTEXT:
            track = &mTimedTextTrack;
            break;
        default:
            TRESPASS();
    }

    if (track->mSource == NULL) {
        return;
    }

    if (actualTimeUs) {
        *actualTimeUs = seekTimeUs;
    }

    MediaSource::ReadOptions options;

    bool seeking = false;
    if (seekTimeUs >= 0) {
        options.setSeekTo(seekTimeUs, mode);
        seeking = true;
    }

    const bool couldReadMultiple = (track->mSource->supportReadMultiple());

    if (couldReadMultiple) {
        options.setNonBlocking();
    }

    for (size_t numBuffers = 0; numBuffers < maxBuffers; ) {
        Vector<MediaBuffer *> mediaBuffers;
        status_t err = NO_ERROR;

        if (couldReadMultiple) {
            err = track->mSource->readMultiple(
                    &mediaBuffers, maxBuffers - numBuffers, &options);
        } else {
            MediaBuffer *mbuf = NULL;
            err = track->mSource->read(&mbuf, &options);
            if (err == OK && mbuf != NULL) {
                mediaBuffers.push_back(mbuf);
            }
        }

        options.clearNonPersistent();

        size_t id = 0;
        size_t count = mediaBuffers.size();
        for (; id < count; ++id) {
            int64_t timeUs;
            MediaBuffer *mbuf = mediaBuffers[id];
            if (!mbuf->meta_data()->findInt64(kKeyTime, &timeUs)) {
                mbuf->meta_data()->dumpToLog();
                track->mPackets->signalEOS(ERROR_MALFORMED);
                break;
            }
            if (trackType == MEDIA_TRACK_TYPE_AUDIO) {
                mAudioTimeUs = timeUs;
                mBufferingMonitor->updateQueuedTime(true /* isAudio */, timeUs);
            } else if (trackType == MEDIA_TRACK_TYPE_VIDEO) {
                mVideoTimeUs = timeUs;
                mBufferingMonitor->updateQueuedTime(false /* isAudio */, timeUs);
            }

            queueDiscontinuityIfNeeded(seeking, formatChange, trackType, track);

#ifdef MTK_AUDIO_APE_SUPPORT
            //for ape seek
            int32_t newframe = 0;
            int32_t firstbyte = 0;
            if (mbuf->meta_data()->findInt32(kKeyNemFrame, &newframe))
            {
                ALOGI("see nwfrm:%d", (int)newframe);
            }
            if (mbuf->meta_data()->findInt32(kKeySeekByte, &firstbyte))
            {
                ALOGI("see sekbyte:%d", (int)firstbyte);
            }
#endif

            sp<ABuffer> buffer = mediaBufferToABuffer(mbuf, trackType);
            if (numBuffers == 0 && actualTimeUs != nullptr) {
                *actualTimeUs = timeUs;
            }
            if (seeking && buffer != nullptr) {
                sp<AMessage> meta = buffer->meta();
                if (meta != nullptr && mode == MediaPlayerSeekMode::SEEK_CLOSEST
                        && seekTimeUs > timeUs) {
                    sp<AMessage> extra = new AMessage;
                    extra->setInt64("resume-at-mediaTimeUs", seekTimeUs);
                    //mtk add seekmode for ALPS03567323 AND ALPS03607769
                    // should set decode-seekTime to decoder to skip B frame when seek
                    // case 1: seek-preroll is open
                    extra->setInt64("decode-seekTime", seekTimeUs);
#ifdef MTK_AUDIO_APE_SUPPORT
                    //for ape seek
                    if (newframe != 0 || firstbyte != 0){
                        extra->setInt32("nwfrm", newframe);
                        extra->setInt32("sekbyte", firstbyte);
                    }
#endif
                    meta->setMessage("extra", extra);
                }
                else if (meta != nullptr) {
                    // case 2: seek-preroll is off
                    sp<AMessage> extra = new AMessage;
                    extra->setInt64("decode-seekTime", timeUs);
#ifdef MTK_AUDIO_APE_SUPPORT               
                    //for ape seek
                    if (newframe != 0 || firstbyte != 0){
                        extra->setInt64("resume-at-mediaTimeUs", seekTimeUs);
                        extra->setInt32("nwfrm", newframe);
                        extra->setInt32("sekbyte", firstbyte);
                    }                   
#endif            
                    meta->setMessage("extra", extra);    
                }

            }

            track->mPackets->queueAccessUnit(buffer);
            formatChange = false;
            seeking = false;
            ++numBuffers;
        }
        if (id < count) {
            // Error, some mediaBuffer doesn't have kKeyTime.
            for (; id < count; ++id) {
                mediaBuffers[id]->release();
            }
            break;
        }

        if (err == WOULD_BLOCK) {
            break;
        } else if (err == INFO_FORMAT_CHANGED) {
#if 0
            track->mPackets->queueDiscontinuity(
                    ATSParser::DISCONTINUITY_FORMATCHANGE,
                    NULL,
                    false /* discard */);
#endif
        } else if (err != OK) {
            queueDiscontinuityIfNeeded(seeking, formatChange, trackType, track);
            track->mPackets->signalEOS(err);
            break;
        }
    }
}

void NuPlayer::GenericSource::queueDiscontinuityIfNeeded(
        bool seeking, bool formatChange, media_track_type trackType, Track *track) {
    // formatChange && seeking: track whose source is changed during selection
    // formatChange && !seeking: track whose source is not changed during selection
    // !formatChange: normal seek
    if ((seeking || formatChange)
            && (trackType == MEDIA_TRACK_TYPE_AUDIO
            || trackType == MEDIA_TRACK_TYPE_VIDEO)) {
        ATSParser::DiscontinuityType type = (formatChange && seeking)
                ? ATSParser::DISCONTINUITY_FORMATCHANGE
                : ATSParser::DISCONTINUITY_NONE;
        track->mPackets->queueDiscontinuity(type, NULL /* extra */, true /* discard */);
    }
}

NuPlayer::GenericSource::BufferingMonitor::BufferingMonitor(const sp<AMessage> &notify)
    : mNotify(notify),
      mDurationUs(-1ll),
      mBitrate(-1ll),
      mIsStreaming(false),
      mAudioTimeUs(0),
      mVideoTimeUs(0),
      mPollBufferingGeneration(0),
      mPrepareBuffering(false),
      mBuffering(false),
      mPrevBufferPercentage(-1),
      mOffloadAudio(false),
      mFirstDequeuedBufferRealUs(-1ll),
      mFirstDequeuedBufferMediaUs(-1ll),
      mlastDequeuedBufferMediaUs(-1ll) {
      getDefaultBufferingSettings(&mSettings);
}

NuPlayer::GenericSource::BufferingMonitor::~BufferingMonitor() {
}

void NuPlayer::GenericSource::BufferingMonitor::getDefaultBufferingSettings(
        BufferingSettings *buffering /* nonnull */) {
    buffering->mInitialBufferingMode = BUFFERING_MODE_TIME_ONLY;
    buffering->mRebufferingMode = BUFFERING_MODE_TIME_THEN_SIZE;
    buffering->mInitialWatermarkMs = kHighWaterMarkMs;
    buffering->mRebufferingWatermarkLowMs = kLowWaterMarkMs;
    buffering->mRebufferingWatermarkHighMs = kHighWaterMarkRebufferMs;
    buffering->mRebufferingWatermarkLowKB = kLowWaterMarkKB;
    buffering->mRebufferingWatermarkHighKB = kHighWaterMarkKB;

    ALOGV("BufferingMonitor::getDefaultBufferingSettings{%s}",
            buffering->toString().string());
}

status_t NuPlayer::GenericSource::BufferingMonitor::setBufferingSettings(
        const BufferingSettings &buffering) {
    ALOGV("BufferingMonitor::setBufferingSettings{%s}",
            buffering.toString().string());

    Mutex::Autolock _l(mLock);
    if (buffering.IsSizeBasedBufferingMode(buffering.mInitialBufferingMode)
            || (buffering.IsTimeBasedBufferingMode(buffering.mRebufferingMode)
                && buffering.mRebufferingWatermarkLowMs > buffering.mRebufferingWatermarkHighMs)
            || (buffering.IsSizeBasedBufferingMode(buffering.mRebufferingMode)
                && buffering.mRebufferingWatermarkLowKB > buffering.mRebufferingWatermarkHighKB)) {
        return BAD_VALUE;
    }
    mSettings = buffering;
    if (mSettings.mInitialBufferingMode == BUFFERING_MODE_NONE) {
        mSettings.mInitialWatermarkMs = BufferingSettings::kNoWatermark;
    }
    if (!mSettings.IsTimeBasedBufferingMode(mSettings.mRebufferingMode)) {
        mSettings.mRebufferingWatermarkLowMs = BufferingSettings::kNoWatermark;
        mSettings.mRebufferingWatermarkHighMs = INT32_MAX;
    }
    if (!mSettings.IsSizeBasedBufferingMode(mSettings.mRebufferingMode)) {
        mSettings.mRebufferingWatermarkLowKB = BufferingSettings::kNoWatermark;
        mSettings.mRebufferingWatermarkHighKB = INT32_MAX;
    }
    return OK;
}

void NuPlayer::GenericSource::BufferingMonitor::prepare(
        const sp<NuCachedSource2> &cachedSource,
        int64_t durationUs,
        int64_t bitrate,
        bool isStreaming) {
    Mutex::Autolock _l(mLock);
    prepare_l(cachedSource, durationUs, bitrate, isStreaming);
}

void NuPlayer::GenericSource::BufferingMonitor::stop() {
    Mutex::Autolock _l(mLock);
    prepare_l(NULL /* cachedSource */, -1 /* durationUs */,
            -1 /* bitrate */, false /* isStreaming */);
}

void NuPlayer::GenericSource::BufferingMonitor::cancelPollBuffering() {
    Mutex::Autolock _l(mLock);
    cancelPollBuffering_l();
}

void NuPlayer::GenericSource::BufferingMonitor::restartPollBuffering() {
    Mutex::Autolock _l(mLock);
    if (mIsStreaming) {
        cancelPollBuffering_l();
        onPollBuffering_l();
    }
}

void NuPlayer::GenericSource::BufferingMonitor::stopBufferingIfNecessary() {
    Mutex::Autolock _l(mLock);
    stopBufferingIfNecessary_l();
}

void NuPlayer::GenericSource::BufferingMonitor::ensureCacheIsFetching() {
    Mutex::Autolock _l(mLock);
    ensureCacheIsFetching_l();
}

void NuPlayer::GenericSource::BufferingMonitor::updateQueuedTime(bool isAudio, int64_t timeUs) {
    Mutex::Autolock _l(mLock);
    if (isAudio) {
        mAudioTimeUs = timeUs;
    } else {
        mVideoTimeUs = timeUs;
    }
}

void NuPlayer::GenericSource::BufferingMonitor::setOffloadAudio(bool offload) {
    Mutex::Autolock _l(mLock);
    mOffloadAudio = offload;
}

void NuPlayer::GenericSource::BufferingMonitor::updateDequeuedBufferTime(int64_t mediaUs) {
    Mutex::Autolock _l(mLock);
    if (mediaUs < 0) {
        mFirstDequeuedBufferRealUs = -1ll;
        mFirstDequeuedBufferMediaUs = -1ll;
    } else if (mFirstDequeuedBufferRealUs < 0) {
        mFirstDequeuedBufferRealUs = ALooper::GetNowUs();
        mFirstDequeuedBufferMediaUs = mediaUs;
    }
    mlastDequeuedBufferMediaUs = mediaUs;
}

void NuPlayer::GenericSource::BufferingMonitor::prepare_l(
        const sp<NuCachedSource2> &cachedSource,
        int64_t durationUs,
        int64_t bitrate,
        bool isStreaming) {

    mCachedSource = cachedSource;
    mDurationUs = durationUs;
    mBitrate = bitrate;
    mIsStreaming = isStreaming;
    mAudioTimeUs = 0;
    mVideoTimeUs = 0;
    mPrepareBuffering = (cachedSource != NULL);
    cancelPollBuffering_l();
    mOffloadAudio = false;
    mFirstDequeuedBufferRealUs = -1ll;
    mFirstDequeuedBufferMediaUs = -1ll;
    mlastDequeuedBufferMediaUs = -1ll;
}

void NuPlayer::GenericSource::BufferingMonitor::cancelPollBuffering_l() {
    mBuffering = false;
    ++mPollBufferingGeneration;
    mPrevBufferPercentage = -1;
}

void NuPlayer::GenericSource::BufferingMonitor::notifyBufferingUpdate_l(int32_t percentage) {
    // Buffering percent could go backward as it's estimated from remaining
    // data and last access time. This could cause the buffering position
    // drawn on media control to jitter slightly. Remember previously reported
    // percentage and don't allow it to go backward.
    if (percentage < mPrevBufferPercentage) {
        percentage = mPrevBufferPercentage;
    } else if (percentage > 100) {
        percentage = 100;
    }

    mPrevBufferPercentage = percentage;

    ALOGV("notifyBufferingUpdate_l: buffering %d%%", percentage);

    sp<AMessage> msg = mNotify->dup();
    msg->setInt32("what", kWhatBufferingUpdate);
    msg->setInt32("percentage", percentage);
    msg->post();
}

void NuPlayer::GenericSource::BufferingMonitor::startBufferingIfNecessary_l() {
    if (mPrepareBuffering) {
        return;
    }

    if (!mBuffering) {
        ALOGD("startBufferingIfNecessary_l");

        mBuffering = true;

        ensureCacheIsFetching_l();
        sendCacheStats_l();

        sp<AMessage> notify = mNotify->dup();
        notify->setInt32("what", kWhatPauseOnBufferingStart);
        notify->post();
    }
}

void NuPlayer::GenericSource::BufferingMonitor::stopBufferingIfNecessary_l() {
    if (mPrepareBuffering) {
        ALOGD("stopBufferingIfNecessary_l, mBuffering=%d", mBuffering);

        mPrepareBuffering = false;

        sp<AMessage> notify = mNotify->dup();
        notify->setInt32("what", kWhatPrepared);
        notify->setInt32("err", OK);
        notify->post();

        return;
    }

    if (mBuffering) {
        ALOGD("stopBufferingIfNecessary_l");
        mBuffering = false;

        sendCacheStats_l();

        sp<AMessage> notify = mNotify->dup();
        notify->setInt32("what", kWhatResumeOnBufferingEnd);
        notify->post();
    }
}

void NuPlayer::GenericSource::BufferingMonitor::sendCacheStats_l() {
    int32_t kbps = 0;
    status_t err = UNKNOWN_ERROR;

    if (mCachedSource != NULL) {
        err = mCachedSource->getEstimatedBandwidthKbps(&kbps);
    }

    if (err == OK) {
        sp<AMessage> notify = mNotify->dup();
        notify->setInt32("what", kWhatCacheStats);
        notify->setInt32("bandwidth", kbps);
        notify->post();
    }
}

void NuPlayer::GenericSource::BufferingMonitor::ensureCacheIsFetching_l() {
    if (mCachedSource != NULL) {
        mCachedSource->resumeFetchingIfNecessary();
    }
}

void NuPlayer::GenericSource::BufferingMonitor::schedulePollBuffering_l() {
    sp<AMessage> msg = new AMessage(kWhatPollBuffering, this);
    msg->setInt32("generation", mPollBufferingGeneration);
    // Enquires buffering status every second.
    msg->post(1000000ll);
}

int64_t NuPlayer::GenericSource::BufferingMonitor::getLastReadPosition_l() {
    if (mAudioTimeUs > 0) {
        return mAudioTimeUs;
    } else if (mVideoTimeUs > 0) {
        return mVideoTimeUs;
    } else {
        return 0;
    }
}

void NuPlayer::GenericSource::BufferingMonitor::onPollBuffering_l() {
    status_t finalStatus = UNKNOWN_ERROR;
    int64_t cachedDurationUs = -1ll;
    ssize_t cachedDataRemaining = -1;

    if (mCachedSource != NULL) {
        cachedDataRemaining =
                mCachedSource->approxDataRemaining(&finalStatus);

        if (finalStatus == OK) {
            off64_t size;
            int64_t bitrate = 0ll;
            if (mDurationUs > 0 && mCachedSource->getSize(&size) == OK) {
                // |bitrate| uses bits/second unit, while size is number of bytes.
                bitrate = size * 8000000ll / mDurationUs;
            } else if (mBitrate > 0) {
                bitrate = mBitrate;
            }
            if (bitrate > 0) {
                cachedDurationUs = cachedDataRemaining * 8000000ll / bitrate;
            }
        }
    }

    if (finalStatus != OK) {
        ALOGV("onPollBuffering_l: EOS (finalStatus = %d)", finalStatus);

        if (finalStatus == ERROR_END_OF_STREAM) {
            notifyBufferingUpdate_l(100);
        }

        stopBufferingIfNecessary_l();
        return;
    }

    if (cachedDurationUs >= 0ll) {
        if (mDurationUs > 0ll) {
            int64_t cachedPosUs = getLastReadPosition_l() + cachedDurationUs;
            int percentage = 100.0 * cachedPosUs / mDurationUs;
            if (percentage > 100) {
                percentage = 100;
            }

            notifyBufferingUpdate_l(percentage);
        }

        ALOGV("onPollBuffering_l: cachedDurationUs %.1f sec", cachedDurationUs / 1000000.0f);

        if (mPrepareBuffering) {
            if (cachedDurationUs > mSettings.mInitialWatermarkMs * 1000) {
                stopBufferingIfNecessary_l();
            }
        } else if (mSettings.IsTimeBasedBufferingMode(mSettings.mRebufferingMode)) {
            if (cachedDurationUs < mSettings.mRebufferingWatermarkLowMs * 1000) {
                // Take into account the data cached in downstream components to try to avoid
                // unnecessary pause.
                if (mOffloadAudio && mFirstDequeuedBufferRealUs >= 0) {
                    int64_t downStreamCacheUs =
                        mlastDequeuedBufferMediaUs - mFirstDequeuedBufferMediaUs
                            - (ALooper::GetNowUs() - mFirstDequeuedBufferRealUs);
                    if (downStreamCacheUs > 0) {
                        cachedDurationUs += downStreamCacheUs;
                    }
                }

                if (cachedDurationUs < mSettings.mRebufferingWatermarkLowMs * 1000) {
                    startBufferingIfNecessary_l();
                }
            } else if (cachedDurationUs > mSettings.mRebufferingWatermarkHighMs * 1000) {
                stopBufferingIfNecessary_l();
            }
        }
    } else if (cachedDataRemaining >= 0
            && mSettings.IsSizeBasedBufferingMode(mSettings.mRebufferingMode)) {
        ALOGV("onPollBuffering_l: cachedDataRemaining %zd bytes",
                cachedDataRemaining);

        if (cachedDataRemaining < (mSettings.mRebufferingWatermarkLowKB << 10)) {
            startBufferingIfNecessary_l();
        } else if (cachedDataRemaining > (mSettings.mRebufferingWatermarkHighKB << 10)) {
            stopBufferingIfNecessary_l();
        }
    }

    schedulePollBuffering_l();
}

void NuPlayer::GenericSource::BufferingMonitor::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
      case kWhatPollBuffering:
      {
          int32_t generation;
          CHECK(msg->findInt32("generation", &generation));
          Mutex::Autolock _l(mLock);
          if (generation == mPollBufferingGeneration) {
              onPollBuffering_l();
          }
          break;
      }
      default:
          TRESPASS();
          break;
    }
}

// Modular DRM
status_t NuPlayer::GenericSource::prepareDrm(
        const uint8_t uuid[16], const Vector<uint8_t> &drmSessionId, sp<ICrypto> *crypto)
{
    ALOGV("prepareDrm");

    sp<AMessage> msg = new AMessage(kWhatPrepareDrm, this);
    // synchronous call so just passing the address but with local copies of "const" args
    uint8_t UUID[16];
    memcpy(UUID, uuid, sizeof(UUID));
    Vector<uint8_t> sessionId = drmSessionId;
    msg->setPointer("uuid", (void*)UUID);
    msg->setPointer("drmSessionId", (void*)&sessionId);
    msg->setPointer("crypto", (void*)crypto);

    sp<AMessage> response;
    status_t status = msg->postAndAwaitResponse(&response);

    if (status == OK && response != NULL) {
        CHECK(response->findInt32("status", &status));
        ALOGV_IF(status == OK, "prepareDrm: mCrypto: %p (%d)", crypto->get(),
                (*crypto != NULL ? (*crypto)->getStrongCount() : 0));
        ALOGD("prepareDrm ret: %d ", status);
    } else {
        ALOGE("prepareDrm err: %d", status);
    }

    return status;
}

status_t NuPlayer::GenericSource::releaseDrm()
{
    ALOGV("releaseDrm");

    sp<AMessage> msg = new AMessage(kWhatReleaseDrm, this);

    // synchronous call to update the source states before the player proceedes with crypto cleanup
    sp<AMessage> response;
    status_t status = msg->postAndAwaitResponse(&response);

    if (status == OK && response != NULL) {
        ALOGD("releaseDrm ret: OK ");
    } else {
        ALOGE("releaseDrm err: %d", status);
    }

    return status;
}

status_t NuPlayer::GenericSource::onPrepareDrm(const sp<AMessage> &msg)
{
    ALOGV("onPrepareDrm ");

    mIsDrmProtected = false;
    mIsDrmReleased = false;
    mIsSecure = false;

    uint8_t *uuid;
    Vector<uint8_t> *drmSessionId;
    sp<ICrypto> *outCrypto;
    CHECK(msg->findPointer("uuid", (void**)&uuid));
    CHECK(msg->findPointer("drmSessionId", (void**)&drmSessionId));
    CHECK(msg->findPointer("crypto", (void**)&outCrypto));

    status_t status = OK;
    sp<ICrypto> crypto = NuPlayerDrm::createCryptoAndPlugin(uuid, *drmSessionId, status);
    if (crypto == NULL) {
        ALOGE("onPrepareDrm: createCrypto failed. status: %d", status);
        return status;
    }
    ALOGV("onPrepareDrm: createCryptoAndPlugin succeeded for uuid: %s",
            DrmUUID::toHexString(uuid).string());

    *outCrypto = crypto;
    // as long a there is an active crypto
    mIsDrmProtected = true;

    if (mMimes.size() == 0) {
        status = UNKNOWN_ERROR;
        ALOGE("onPrepareDrm: Unexpected. Must have at least one track. status: %d", status);
        return status;
    }

    // first mime in this list is either the video track, or the first audio track
    const char *mime = mMimes[0].string();
    mIsSecure = crypto->requiresSecureDecoderComponent(mime);
    ALOGV("onPrepareDrm: requiresSecureDecoderComponent mime: %s  isSecure: %d",
            mime, mIsSecure);

    // Checking the member flags while in the looper to send out the notification.
    // The legacy mDecryptHandle!=NULL check (for FLAG_PROTECTED) is equivalent to mIsDrmProtected.
    notifyFlagsChanged(
            (mIsSecure ? FLAG_SECURE : 0) |
            // Setting "protected screen" only for L1: b/38390836
            (mIsSecure ? FLAG_PROTECTED : 0) |
            FLAG_CAN_PAUSE |
            FLAG_CAN_SEEK_BACKWARD |
            FLAG_CAN_SEEK_FORWARD |
            FLAG_CAN_SEEK);

    return status;
}

status_t NuPlayer::GenericSource::onReleaseDrm()
{
    if (mIsDrmProtected) {
        mIsDrmProtected = false;
        // to prevent returning any more buffer after stop/releaseDrm (b/37960096)
        mIsDrmReleased = true;
        ALOGV("onReleaseDrm: mIsDrmProtected is reset.");
    } else {
        ALOGE("onReleaseDrm: mIsDrmProtected is already false.");
    }

    return OK;
}

status_t NuPlayer::GenericSource::checkDrmInfo()
{
    // clearing the flag at prepare in case the player is reused after stop/releaseDrm with the
    // same source without being reset (called by prepareAsync/initFromDataSource)
    mIsDrmReleased = false;

    if (mFileMeta == NULL) {
        ALOGI("checkDrmInfo: No metadata");
        return OK; // letting the caller responds accordingly
    }

    uint32_t type;
    const void *pssh;
    size_t psshsize;

    if (!mFileMeta->findData(kKeyPssh, &type, &pssh, &psshsize)) {
        ALOGV("checkDrmInfo: No PSSH");
        return OK; // source without DRM info
    }

    Parcel parcel;
    NuPlayerDrm::retrieveDrmInfo(pssh, psshsize, &parcel);
    ALOGV("checkDrmInfo: MEDIA_DRM_INFO PSSH size: %d  Parcel size: %d  objects#: %d",
          (int)psshsize, (int)parcel.dataSize(), (int)parcel.objectsCount());

    if (parcel.dataSize() == 0) {
        ALOGE("checkDrmInfo: Unexpected parcel size: 0");
        return UNKNOWN_ERROR;
    }

    // Can't pass parcel as a message to the player. Converting Parcel->ABuffer to pass it
    // to the Player's onSourceNotify then back to Parcel for calling driver's notifyListener.
    sp<ABuffer> drmInfoBuffer = ABuffer::CreateAsCopy(parcel.data(), parcel.dataSize());
    notifyDrmInfo(drmInfoBuffer);

    return OK;
}

void NuPlayer::GenericSource::signalBufferReturned(MediaBuffer *buffer)
{
    //ALOGV("signalBufferReturned %p  refCount: %d", buffer, buffer->localRefcount());

    buffer->setObserver(NULL);
    buffer->release(); // this leads to delete since that there is no observor
}

// add for mtk
bool NuPlayer::GenericSource::isTS() {
    const char *mime = NULL;
    if (mFileMeta != NULL && mFileMeta->findCString(kKeyMIMEType, &mime)
            && !strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG2TS)) {
        return true;
    }
    return false;
}
// end of add for mtk

//mtkadd+
void NuPlayer::GenericSource::init() {
    mIsMtkMusic = 0;
}

void NuPlayer::GenericSource::setParams(const sp<MetaData>& meta) {
    mIsMtkMusic = 0;
    if (meta->findInt32(kKeyMp3MultiRead, &mIsMtkMusic) && (mIsMtkMusic == 1)) {
        ALOGD("It's MTK Music");
    }
}

status_t NuPlayer::GenericSource::setVendorMeta(bool audio, const sp<MetaData> & meta)
{
    sp<IMediaSource> source = audio ? mAudioTrack.mSource : mVideoTrack.mSource;
    if (source == NULL || meta == NULL) {
        return UNKNOWN_ERROR;
    }
    source->setVendorMeta(meta);
    return OK;
}


}  // namespace android
