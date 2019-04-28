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

#ifndef GENERIC_SOURCE_H_

#define GENERIC_SOURCE_H_

#include "NuPlayer.h"
#include "NuPlayerSource.h"

#include "ATSParser.h"

#include <media/mediaplayer.h>

namespace android {

class DecryptHandle;
class DrmManagerClient;
struct AnotherPacketSource;
struct ARTSPController;
class DataSource;
class IDataSource;
struct IMediaHTTPService;
struct MediaSource;
class MediaBuffer;
struct NuCachedSource2;

struct NuPlayer::GenericSource : public NuPlayer::Source,
                                 public MediaBufferObserver // Modular DRM
{
    GenericSource(const sp<AMessage> &notify, bool uidValid, uid_t uid);

    status_t setDataSource(
            const sp<IMediaHTTPService> &httpService,
            const char *url,
            const KeyedVector<String8, String8> *headers);

    status_t setDataSource(int fd, int64_t offset, int64_t length);

    status_t setDataSource(const sp<DataSource>& dataSource);

    virtual status_t getDefaultBufferingSettings(
            BufferingSettings* buffering /* nonnull */) override;
    virtual status_t setBufferingSettings(const BufferingSettings& buffering) override;

    virtual void prepareAsync();

    virtual void start();
    virtual void stop();
    virtual void pause();
    virtual void resume();

    virtual void disconnect();

    virtual status_t feedMoreTSData();

    virtual sp<MetaData> getFileFormatMeta() const;

    virtual status_t dequeueAccessUnit(bool audio, sp<ABuffer> *accessUnit);

    virtual status_t getDuration(int64_t *durationUs);
    virtual size_t getTrackCount() const;
    virtual sp<AMessage> getTrackInfo(size_t trackIndex) const;
    virtual ssize_t getSelectedTrack(media_track_type type) const;
    virtual status_t selectTrack(size_t trackIndex, bool select, int64_t timeUs);
    virtual status_t seekTo(
        int64_t seekTimeUs,
        MediaPlayerSeekMode mode = MediaPlayerSeekMode::SEEK_PREVIOUS_SYNC) override;

    virtual status_t setBuffers(bool audio, Vector<MediaBuffer *> &buffers);

    virtual bool isStreaming() const;

    virtual void setOffloadAudio(bool offload);

    // Modular DRM
    virtual void signalBufferReturned(MediaBuffer *buffer);

    virtual status_t prepareDrm(
            const uint8_t uuid[16], const Vector<uint8_t> &drmSessionId, sp<ICrypto> *crypto);

    virtual status_t releaseDrm();

    virtual status_t setVendorMeta(bool audio, const sp<MetaData> & meta);

protected:
    virtual ~GenericSource();

    virtual void onMessageReceived(const sp<AMessage> &msg);

    virtual sp<MetaData> getFormatMeta(bool audio);

private:
    enum {
        kWhatPrepareAsync,
        kWhatFetchSubtitleData,
        kWhatFetchTimedTextData,
        kWhatSendSubtitleData,
        kWhatSendGlobalTimedTextData,
        kWhatSendTimedTextData,
        kWhatChangeAVSource,
        kWhatPollBuffering,
        kWhatGetFormat,
        kWhatGetSelectedTrack,
        kWhatSelectTrack,
        kWhatSeek,
        kWhatReadBuffer,
        kWhatStart,
        kWhatResume,
        kWhatSecureDecodersInstantiated,
        // Modular DRM
        kWhatPrepareDrm,
        kWhatReleaseDrm,
    };

    struct Track {
        size_t mIndex;
        sp<IMediaSource> mSource;
        sp<AnotherPacketSource> mPackets;
    };

    // Helper to monitor buffering status. The polling happens every second.
    // When necessary, it will send out buffering events to the player.
    struct BufferingMonitor : public AHandler {
    public:
        explicit BufferingMonitor(const sp<AMessage> &notify);

        void getDefaultBufferingSettings(BufferingSettings *buffering /* nonnull */);
        status_t setBufferingSettings(const BufferingSettings &buffering);

        // Set up state.
        void prepare(const sp<NuCachedSource2> &cachedSource,
                int64_t durationUs,
                int64_t bitrate,
                bool isStreaming);
        // Stop and reset buffering monitor.
        void stop();
        // Cancel the current monitor task.
        void cancelPollBuffering();
        // Restart the monitor task.
        void restartPollBuffering();
        // Stop buffering task and send out corresponding events.
        void stopBufferingIfNecessary();
        // Make sure data source is getting data.
        void ensureCacheIsFetching();
        // Update media time of just extracted buffer from data source.
        void updateQueuedTime(bool isAudio, int64_t timeUs);

        // Set the offload mode.
        void setOffloadAudio(bool offload);
        // Update media time of last dequeued buffer which is sent to the decoder.
        void updateDequeuedBufferTime(int64_t mediaUs);

    protected:
        virtual ~BufferingMonitor();
        virtual void onMessageReceived(const sp<AMessage> &msg);

    private:
        enum {
            kWhatPollBuffering,
        };

        sp<AMessage> mNotify;

        sp<NuCachedSource2> mCachedSource;
        int64_t mDurationUs;
        int64_t mBitrate;
        bool mIsStreaming;

        int64_t mAudioTimeUs;
        int64_t mVideoTimeUs;
        int32_t mPollBufferingGeneration;
        bool mPrepareBuffering;
        bool mBuffering;
        int32_t mPrevBufferPercentage;

        mutable Mutex mLock;

        BufferingSettings mSettings;
        bool mOffloadAudio;
        int64_t mFirstDequeuedBufferRealUs;
        int64_t mFirstDequeuedBufferMediaUs;
        int64_t mlastDequeuedBufferMediaUs;

        void prepare_l(const sp<NuCachedSource2> &cachedSource,
                int64_t durationUs,
                int64_t bitrate,
                bool isStreaming);
        void cancelPollBuffering_l();
        void notifyBufferingUpdate_l(int32_t percentage);
        void startBufferingIfNecessary_l();
        void stopBufferingIfNecessary_l();
        void sendCacheStats_l();
        void ensureCacheIsFetching_l();
        int64_t getLastReadPosition_l();
        void onPollBuffering_l();
        void schedulePollBuffering_l();
    };

    Vector<sp<IMediaSource> > mSources;
    Track mAudioTrack;
    int64_t mAudioTimeUs;
    int64_t mAudioLastDequeueTimeUs;
    Track mVideoTrack;
    int64_t mVideoTimeUs;
    int64_t mVideoLastDequeueTimeUs;
    Track mSubtitleTrack;
    Track mTimedTextTrack;

    int32_t mFetchSubtitleDataGeneration;
    int32_t mFetchTimedTextDataGeneration;
    int64_t mDurationUs;
    bool mAudioIsVorbis;
    // Secure codec is required.
    bool mIsSecure;
    bool mIsStreaming;
    bool mUIDValid;
    uid_t mUID;
    sp<IMediaHTTPService> mHTTPService;
    AString mUri;
    KeyedVector<String8, String8> mUriHeaders;
    int mFd;
    int64_t mOffset;
    int64_t mLength;

    sp<DataSource> mDataSource;
    sp<NuCachedSource2> mCachedSource;
    sp<DataSource> mHttpSource;
    sp<MetaData> mFileMeta;
    bool mStarted;
    bool mStopRead;
    int64_t mBitrate;
    sp<BufferingMonitor> mBufferingMonitor;
    uint32_t mPendingReadBufferTypes;
    sp<ABuffer> mGlobalTimedText;

    mutable Mutex mReadBufferLock;
    mutable Mutex mDisconnectLock;

    sp<ALooper> mLooper;
    sp<ALooper> mBufferingMonitorLooper;

    void resetDataSource();

    status_t initFromDataSource();
    int64_t getLastReadPosition();

    void notifyPreparedAndCleanup(status_t err);
    void onSecureDecodersInstantiated(status_t err);
    void finishPrepareAsync();
    status_t startSources();

    void onGetFormatMeta(const sp<AMessage>& msg) const;
    sp<MetaData> doGetFormatMeta(bool audio) const;

    void onGetTrackInfo(const sp<AMessage>& msg) const;
    sp<AMessage> doGetTrackInfo(size_t trackIndex) const;

    void onGetSelectedTrack(const sp<AMessage>& msg) const;
    ssize_t doGetSelectedTrack(media_track_type type) const;

    void onSelectTrack(const sp<AMessage>& msg);
    status_t doSelectTrack(size_t trackIndex, bool select, int64_t timeUs);

    void onSeek(const sp<AMessage>& msg);
    status_t doSeek(int64_t seekTimeUs, MediaPlayerSeekMode mode);

    void onPrepareAsync();

    void fetchTextData(
            uint32_t what, media_track_type type,
            int32_t curGen, const sp<AnotherPacketSource>& packets, const sp<AMessage>& msg);

    void sendGlobalTextData(
            uint32_t what,
            int32_t curGen, sp<AMessage> msg);

    void sendTextData(
            uint32_t what, media_track_type type,
            int32_t curGen, const sp<AnotherPacketSource>& packets, const sp<AMessage>& msg);

    sp<ABuffer> mediaBufferToABuffer(
            MediaBuffer *mbuf,
            media_track_type trackType);

    void postReadBuffer(media_track_type trackType);
    void onReadBuffer(const sp<AMessage>& msg);
    // When |mode| is MediaPlayerSeekMode::SEEK_CLOSEST, the buffer read shall
    // include an item indicating skipping rendering all buffers with timestamp
    // earlier than |seekTimeUs|.
    // For other modes, the buffer read will not include the item as above in order
    // to facilitate fast seek operation.
    void readBuffer(
            media_track_type trackType,
            int64_t seekTimeUs = -1ll,
            MediaPlayerSeekMode mode = MediaPlayerSeekMode::SEEK_PREVIOUS_SYNC,
            int64_t *actualTimeUs = NULL, bool formatChange = false);

    void queueDiscontinuityIfNeeded(
            bool seeking, bool formatChange, media_track_type trackType, Track *track);

    // Modular DRM
    // The source is DRM protected and is prepared for DRM.
    bool mIsDrmProtected;
    // releaseDrm has been processed.
    bool mIsDrmReleased;
    Vector<String8> mMimes;

    status_t checkDrmInfo();
    status_t onPrepareDrm(const sp<AMessage> &msg);
    status_t onReleaseDrm();

    DISALLOW_EVIL_CONSTRUCTORS(GenericSource);

// add for mtk
public:
    bool mIsCurrentComplete;   // OMA DRM v1 implementation
    String8 mDrmProcName;
    void setDRMClientInfo(const Parcel *request){mDrmProcName = request->readString8();};
private:
    // add for OMA DRM v1 implementation
    DrmManagerClient *mDrmManagerClient;
    sp<DecryptHandle> mDecryptHandle;
//    void checkDrmStatus(const sp<DataSource>& dataSource);
    void setDrmPlaybackStatusIfNeeded(int playbackStatus, int64_t position);
    // add for TS
    bool isTS();
// end of add for mtk

//mtkadd+ for mp3 lowpower
public:
    virtual void setParams(const sp<MetaData>& meta);
private:
    int mIsMtkMusic;
    void init();
};

}  // namespace android

#endif  // GENERIC_SOURCE_H_
