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

#ifndef MTK_WIFI_DISPLAY_SOURCE_H_

#define MTK_WIFI_DISPLAY_SOURCE_H_

#include "MtkVideoFormats.h"

#include <media/stagefright/foundation/AHandler.h>
#include "MtkANetworkSession.h"

#include <netinet/in.h>


#include <utils/String16.h>


#include "uibc/UibcServerHandler.h"
#include <gui/SurfaceComposerClient.h>
#include <binder/IPCThreadState.h>

namespace android {

struct AReplyToken;
struct IHDCP;
class IRemoteDisplayClient;
struct ParsedMessage;

// Represents the RTSP server acting as a wifi display source.
// Manages incoming connections, sets up Playback sessions as necessary.
struct MtkWifiDisplaySource : public AHandler {
    static const unsigned kWifiDisplayDefaultPort = 7236;

    MtkWifiDisplaySource (
            const String16 &opPackageName,
            const sp<MtkANetworkSession> &netSession,
            const sp<IRemoteDisplayClient> &client,
            const char *path = NULL);

    MtkWifiDisplaySource(
            const String16 &opPackageName,
            const sp<MtkANetworkSession> &netSession,
            const sp<IRemoteDisplayClient> &client,
            const uint32_t  wfdFlags,
            const char *path = NULL);
    status_t start(const char *iface);
    status_t stop();

    status_t pause();
    status_t resume();


   ///M: add for rtsp generic message@{
   status_t sendGenericMsg(int cmd);
   status_t setBitrateControl(int bitrate);
   int      getWfdParam(int paramType);
   ///@}

   ///M:
    static const int32_t kFastSetupFlag     = 0x01;
    static const int32_t kTestModeFlag      = 0x02;
    static const int32_t kUibcEnableFlag    = 0x04;
    static const int32_t kFastRtpFlag       = 0x08;
    static const int32_t kSigmaTest         = 0x10;

    static const int32_t kExpectedBitRate   = 0;
    static const int32_t kCuurentBitRate   = 1;
    static const int32_t kSkipRate   = 2;
/// @}



protected:
    virtual ~MtkWifiDisplaySource();
    virtual void onMessageReceived(const sp<AMessage> &msg);

private:
    struct MtkPlaybackSession;
    struct HDCPObserver;

    enum State {
        INITIALIZED,
        AWAITING_CLIENT_CONNECTION,
        AWAITING_CLIENT_SETUP,
        AWAITING_CLIENT_PLAY,
        ABOUT_TO_PLAY,
        PLAYING,
        PLAYING_TO_PAUSED,
        PAUSED,
        PAUSED_TO_PLAYING,
        AWAITING_CLIENT_TEARDOWN,
        STOPPING,
        STOPPED,
    };

    enum {
        kWhatStart,
        kWhatRTSPNotify,
        kWhatStop,
        kWhatPause,
        kWhatResume,
        kWhatReapDeadClients,
        kWhatPlaybackSessionNotify,
        kWhatKeepAlive,
        kWhatHDCPNotify,
        kWhatFinishStop2,
        kWhatTeardownTriggerTimedOut,
        ///M: Add by MTK
        kWhatTestNotify,
        kWhatSendGenericMsg,
        kWhatRtpNotify
        ///@}
    };

    struct ResponseID {
        int32_t mSessionID;
        int32_t mCSeq;

        bool operator<(const ResponseID &other) const {
            return mSessionID < other.mSessionID
                || (mSessionID == other.mSessionID
                        && mCSeq < other.mCSeq);
        }
    };

    typedef status_t (MtkWifiDisplaySource::*HandleRTSPResponseFunc)(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    static const int64_t kReaperIntervalUs = 1000000ll;

    // We request that the dongle send us a "TEARDOWN" in order to
    // perform an orderly shutdown. We're willing to wait up to 2 secs
    // for this message to arrive, after that we'll force a disconnect
    // instead.
    static const int64_t kTeardownTriggerTimeouSecs = 1;

    static const int64_t kPlaybackSessionTimeoutSecs = 60;

    static const int64_t kPlaybackSessionTimeoutUs =
        kPlaybackSessionTimeoutSecs * 1000000ll;

    static const AString sUserAgent;

    String16 mOpPackageName;

    State mState;
    MtkVideoFormats mSupportedSourceVideoFormats;
    sp<MtkANetworkSession> mNetSession;
    sp<IRemoteDisplayClient> mClient;
    AString mMediaPath;
    struct in_addr mInterfaceAddr;
    int32_t mSessionID;

    sp<AReplyToken> mStopReplyID;

    AString mWfdClientRtpPorts;
    int32_t mChosenRTPPort;  // extracted from "wfd_client_rtp_ports"

    bool mSinkSupportsVideo;
    MtkVideoFormats mSupportedSinkVideoFormats;

    MtkVideoFormats::ResolutionType mChosenVideoResolutionType;
    size_t mChosenVideoResolutionIndex;
    MtkVideoFormats::ProfileType mChosenVideoProfile;
    MtkVideoFormats::LevelType mChosenVideoLevel;

    bool mSinkSupportsAudio;

    bool mUsingPCMAudio;
    int32_t mClientSessionID;

    //M: To avoid playbacksessoin init() is interrupted by destroyAsync()
    bool mPlaybackSessionIniting;
    bool mPlaybackSessionDestroyDeferred;

    bool mPlayRequestReceived;

    ///M: Support Mircast Testing @{
    int32_t mTestSessionID;
    int32_t mTestClientSessionID;

    uint32_t    mWfdFlags;
    bool mTestSessionStopped;

    //Support for test mode for Surface Media Source
    sp<SurfaceComposerClient> mComposerClient;
    sp<IGraphicBufferProducer> mBufferProducer;
    sp<IBinder> mDisplayBinder;
    /// @}


    struct ClientInfo {
        AString mRemoteIP;
        AString mLocalIP;
        int32_t mLocalPort;
        int32_t mPlaybackSessionID;
        int32_t mRemoteRtpPort;
        bool    mUibcSupported;
        sp<MtkPlaybackSession> mPlaybackSession;
    };
    ClientInfo mClientInfo;

    bool mReaperPending;

    int32_t mNextCSeq;

    KeyedVector<ResponseID, HandleRTSPResponseFunc> mResponseHandlers;

    // HDCP specific section >>>>
    bool mUsingHDCP;
    bool mIsHDCP2_0;
    int32_t mHDCPPort;
    sp<IHDCP> mHDCP;
    sp<HDCPObserver> mHDCPObserver;

    bool mHDCPInitializationComplete;
    bool mSetupTriggerDeferred;

    bool mPlaybackSessionEstablished;

    bool mPromoted;

    uid_t mClientUid;
    pid_t mClientPid;
    status_t makeHDCP();
    // <<<< HDCP specific section

    status_t sendM1(int32_t sessionID);
    status_t sendM3(int32_t sessionID);
    status_t sendM4(int32_t sessionID);

    enum TriggerType {
        TRIGGER_SETUP,
        TRIGGER_TEARDOWN,
        TRIGGER_PAUSE,
        TRIGGER_PLAY,
    };

    // M5
    status_t sendTrigger(int32_t sessionID, TriggerType triggerType);

    status_t sendM16(int32_t sessionID);

    status_t onReceiveM1Response(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    status_t onReceiveM3Response(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    status_t onReceiveM4Response(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    status_t onReceiveM5Response(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    status_t onReceiveM16Response(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    void registerResponseHandler(
            int32_t sessionID, int32_t cseq, HandleRTSPResponseFunc func);

    status_t onReceiveClientData(const sp<AMessage> &msg);

    status_t onOptionsRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    status_t onSetupRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    status_t onPlayRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    status_t onPauseRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    status_t onTeardownRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    status_t onGetParameterRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    status_t onSetParameterRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    void sendErrorResponse(
            int32_t sessionID,
            const char *errorDetail,
            int32_t cseq);

    static void AppendCommonResponse(
            AString *response, int32_t cseq, int32_t playbackSessionID = -1ll);

    void scheduleReaper();
    void scheduleKeepAlive(int32_t sessionID);

    int32_t makeUniquePlaybackSessionID() const;

    sp<MtkPlaybackSession> findPlaybackSession(
            const sp<ParsedMessage> &data, int32_t *playbackSessionID) const;

    void finishStop();
    void disconnectClientAsync();
    void disconnectClient2();
    void finishStopAfterDisconnectingClient();
    void finishStop2();

    void finishPlay();

    DISALLOW_EVIL_CONSTRUCTORS(MtkWifiDisplaySource);
    ///M: Add by MTK @{
    status_t sendGenericMsgByMethod(int32_t methodID);
    status_t onReceiveGenericResponse(int32_t sessionID, const sp<ParsedMessage> &msg);
    void     onReceiveTestData(const sp<AMessage> &msg);
    status_t onReceiveM14Response(int32_t sessionID, const sp<ParsedMessage> &msg);
    status_t sendM14(int32_t sessionID);
    void     resetRtspClient();
    void     startRtpClient(const char* remoteIP, int32_t clientRtp);
    void     setAudioPath(bool on);
    void     stopTestSession();
    void     notifyThermal(bool start);
    void     notifyGPUDriver(bool start);
    /// @}
};

}  // namespace android

#endif  // WIFI_DISPLAY_SOURCE_H_
