/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
#define LOG_TAG "MtkWifiDisplaySource"
#include <utils/Log.h>

#include "MtkWifiDisplaySource.h"
#include "MtkPlaybackSession.h"
#include "MtkParameters.h"
#include "rtp/MtkRTPSender.h"

#include <binder/IServiceManager.h>
#include <gui/IGraphicBufferProducer.h>
#include <media/IHDCP.h>
#include <media/IMediaPlayerService.h>
#include <media/IRemoteDisplayClient.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/ParsedMessage.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/Utils.h>

#include <arpa/inet.h>
#include <cutils/properties.h>

#include <ctype.h>


///M: Support Miracast Testing @{
#include <media/stagefright/foundation/hexdump.h>
#include <string.h>
#include <unistd.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/ISurfaceComposer.h>
#include <ui/DisplayInfo.h>
/// @}

///M: add for rtsp generic message{@
#include <media/IRemoteDisplay.h>
///@}


///M: @{
#include <system/audio.h>
#include <system/audio_policy.h>
#include <media/AudioSystem.h>
#include <media/IAudioPolicyService.h>
#include "DataPathTrace.h"
/// @}


#include <fcntl.h>
#include <dlfcn.h>
#include <stdlib.h>

namespace android {


static const char *hdcp_notify_str[] =
{
    "HDCP_INITIALIZATION_COMPLETE",
    "HDCP_INITIALIZATION_FAILED",
    "HDCP_SHUTDOWN_COMPLETE",
    "HDCP_SHUTDOWN_FAILED"
};

#define HDCP_ENABLE_CONTROL   1

// static
const int64_t MtkWifiDisplaySource::kReaperIntervalUs;
const int64_t MtkWifiDisplaySource::kTeardownTriggerTimeouSecs;
const int64_t MtkWifiDisplaySource::kPlaybackSessionTimeoutSecs;
const int64_t MtkWifiDisplaySource::kPlaybackSessionTimeoutUs;
const AString MtkWifiDisplaySource::sUserAgent = MakeUserAgent();

MtkWifiDisplaySource::MtkWifiDisplaySource(
        const String16 &opPackageName,
        const sp<MtkANetworkSession> &netSession,
        const sp<IRemoteDisplayClient> &client,
        const char *path)
    : mOpPackageName(opPackageName),
      mState(INITIALIZED),
      mNetSession(netSession),
      mClient(client),
      mSessionID(0),
      mStopReplyID(NULL),
      mChosenRTPPort(-1),
      mSinkSupportsVideo(false),
      mChosenVideoResolutionType(MtkVideoFormats::RESOLUTION_CEA),
      mChosenVideoResolutionIndex(0),
      mChosenVideoProfile(MtkVideoFormats::PROFILE_CBP),
      mChosenVideoLevel(MtkVideoFormats::LEVEL_31),
      mSinkSupportsAudio(false),
      mUsingPCMAudio(false),
      mClientSessionID(0),
///M: @{
      mPlaybackSessionIniting(false), ///M: Add by MTK Start
      mPlaybackSessionDestroyDeferred(false),
      mPlayRequestReceived(false),
      mTestSessionID(0),
      mTestClientSessionID(0),
      mWfdFlags(0),
      mTestSessionStopped(false),
///M: Add by MTK End
///@}

      mReaperPending(false),
      mNextCSeq(1),
      mUsingHDCP(false),
      mIsHDCP2_0(false),
      mHDCPPort(0),
      mHDCPInitializationComplete(false),
      mSetupTriggerDeferred(false),
      mPlaybackSessionEstablished(false),
      mPromoted(false)
{
    if (path != NULL) {
        mMediaPath.setTo(path);
    }

    mSupportedSourceVideoFormats.disableAll();

    //Mandatory support resolution
    mSupportedSourceVideoFormats.setNativeResolution(
            MtkVideoFormats::RESOLUTION_CEA, 0);
    mSupportedSourceVideoFormats.setNativeResolution(
            MtkVideoFormats::RESOLUTION_CEA, 5);

    // Enable all resolutions up to 1280x720p30
    mSupportedSourceVideoFormats.enableResolutionUpto(
            MtkVideoFormats::RESOLUTION_CEA, 5,
            MtkVideoFormats::PROFILE_CHP,  // Constrained High Profile
            MtkVideoFormats::LEVEL_32);    // Level 3.2

///M: export default configuration for WFD
    char val[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.video-format", val, NULL)) {
        ALOGI("media.wfd.video-format:%s", val);
        int videoFormat = atoi(val);
        if(videoFormat >=0 && videoFormat <= 16){
            mSupportedSourceVideoFormats.setNativeResolution(MtkVideoFormats::RESOLUTION_CEA, videoFormat);
            mSupportedSourceVideoFormats.setProfileLevel(
                MtkVideoFormats::RESOLUTION_CEA, videoFormat, MtkVideoFormats::PROFILE_CHP, MtkVideoFormats::LEVEL_32);
        }
    }

    ///M: Support portrait-resolution
    char portrait[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.portrait", portrait, NULL)) {
        int value = atoi(portrait);
        if(value == 1){
            ALOGI("media.wfd.portrait:%s", portrait);
            if (mSupportedSourceVideoFormats.isResolutionEnabled(
                MtkVideoFormats::RESOLUTION_CEA, 5)){
                mSupportedSourceVideoFormats.setNativeResolution(
                    MtkVideoFormats::RESOLUTION_CEA, 17);
            }

            if (mSupportedSourceVideoFormats.isResolutionEnabled(
                MtkVideoFormats::RESOLUTION_CEA, 7)){
                mSupportedSourceVideoFormats.setNativeResolution(
                    MtkVideoFormats::RESOLUTION_CEA, 18);
            }
        }
    }
    mClientUid = IPCThreadState::self()->getCallingUid();
    mClientPid = IPCThreadState::self()->getCallingPid();
    ALOGD("uid %d, pid %d", mClientUid,mClientPid);
    mInterfaceAddr.s_addr = 0;
}

///M: @{
MtkWifiDisplaySource::MtkWifiDisplaySource(
        const String16 &opPackageName,
        const sp<MtkANetworkSession> &netSession,
        const sp<IRemoteDisplayClient> &client,
        uint32_t  wfdFlags,
        const char *path
        )
    : mOpPackageName(opPackageName),
      mState(INITIALIZED),
      mNetSession(netSession),
      mClient(client),
      mSessionID(0),
      mStopReplyID(NULL),
      mChosenRTPPort(-1),
      mSinkSupportsVideo(false),
      mChosenVideoResolutionType(MtkVideoFormats::RESOLUTION_CEA),
      mChosenVideoResolutionIndex(0),
      mChosenVideoProfile(MtkVideoFormats::PROFILE_CBP),
      mChosenVideoLevel(MtkVideoFormats::LEVEL_31),
      mSinkSupportsAudio(false),
      mUsingPCMAudio(false),
      mClientSessionID(0),
///M: @{
      mPlaybackSessionIniting(false), ///M: Add by MTK Start
      mPlaybackSessionDestroyDeferred(false),
      mPlayRequestReceived(false),
      mTestSessionID(0),
      mTestClientSessionID(0),
      mWfdFlags(wfdFlags),
      mTestSessionStopped(false),
///M: Add by MTK End
///@}

      mReaperPending(false),
      mNextCSeq(1),
      mUsingHDCP(false),
      mIsHDCP2_0(false),
      mHDCPPort(0),
      mHDCPInitializationComplete(false),
      mSetupTriggerDeferred(false),
      mPlaybackSessionEstablished(false),
      mPromoted(false)
{
    if (path != NULL) {
        mMediaPath.setTo(path);
    }

    mSupportedSourceVideoFormats.disableAll();

    //Mandatory support resolution
    mSupportedSourceVideoFormats.setNativeResolution(
            MtkVideoFormats::RESOLUTION_CEA, 0);
    mSupportedSourceVideoFormats.setNativeResolution(
            MtkVideoFormats::RESOLUTION_CEA, 5);

    // Enable all resolutions up to 1280x720p30
    mSupportedSourceVideoFormats.enableResolutionUpto(
            MtkVideoFormats::RESOLUTION_CEA, 5,
            MtkVideoFormats::PROFILE_CHP,  // Constrained High Profile
            MtkVideoFormats::LEVEL_32);    // Level 3.2

///M: export default configuration for WFD
    char val[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.video-format", val, NULL)) {
        ALOGI("media.wfd.video-format:%s", val);
        int videoFormat = atoi(val);
        if(videoFormat >=0 && videoFormat <= 16){
            mSupportedSourceVideoFormats.setNativeResolution(MtkVideoFormats::RESOLUTION_CEA, videoFormat);
        }
    }

    ///M: Support portrait-resolution
    char portrait[PROPERTY_VALUE_MAX];
    if (property_get("media.wfd.portrait", portrait, NULL)) {
        int value = atoi(portrait);
        if(value == 1){
            ALOGI("media.wfd.portrait:%s", portrait);
            if (mSupportedSourceVideoFormats.isResolutionEnabled(
                MtkVideoFormats::RESOLUTION_CEA, 5)){
                mSupportedSourceVideoFormats.setNativeResolution(
                    MtkVideoFormats::RESOLUTION_CEA, 17);
            }

            if (mSupportedSourceVideoFormats.isResolutionEnabled(
                MtkVideoFormats::RESOLUTION_CEA, 7)){
                mSupportedSourceVideoFormats.setNativeResolution(
                    MtkVideoFormats::RESOLUTION_CEA, 18);
            }
        }
    }
    // Get UID and PID here for permission checking
    mClientUid = IPCThreadState::self()->getCallingUid();
    mClientPid = IPCThreadState::self()->getCallingPid();
    ALOGD("uid %d, pid %d", mClientUid,mClientPid);
    mInterfaceAddr.s_addr = 0;
}


MtkWifiDisplaySource::~MtkWifiDisplaySource() {
}

static status_t PostAndAwaitResponse(
        const sp<AMessage> &msg, sp<AMessage> *response) {
    status_t err = msg->postAndAwaitResponse(response);

    if (err != OK) {
        return err;
    }

    if (response == NULL || !(*response)->findInt32("err", &err)) {
        err = OK;
    }

    return err;
}

status_t MtkWifiDisplaySource::start(const char *iface) {
    CHECK_EQ(mState, INITIALIZED);

    sp<AMessage> msg = new AMessage(kWhatStart, this);
    msg->setString("iface", iface);

///M: @{

    if((mWfdFlags & kTestModeFlag) == kTestModeFlag){
        ALOGI("Run test mode and init audio device");
        setAudioPath(true);
        if(mComposerClient == NULL){
            mComposerClient = new SurfaceComposerClient;
            CHECK_EQ(mComposerClient->initCheck(), (status_t)OK);
        }
    }

///@}

///M: Avoid wifi throttle@{
    notifyThermal(true);
///@}

// M: Notify GPU driver@{
    notifyGPUDriver(true);
// @}

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MtkWifiDisplaySource::stop() {
    sp<AMessage> msg = new AMessage(kWhatStop, this);

    ALOGI("MtkWifiDisplaySource::stop kWhatStop=%d",kWhatStop);
    sp<AMessage> response;


    ///M: Notify Audio driver @{
    if((mWfdFlags & kTestModeFlag) == kTestModeFlag){
        ALOGI("un-init audio device");
        setAudioPath(false);
    }
    ///M: @}

///M: Avoid wifi throttle@{
    notifyThermal(false);
///@}

// M: Notify GPU driver@{
    notifyGPUDriver(false);
// @}

    return PostAndAwaitResponse(msg, &response);
}

status_t MtkWifiDisplaySource::pause() {
    sp<AMessage> msg = new AMessage(kWhatPause, this);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MtkWifiDisplaySource::resume() {
    sp<AMessage> msg = new AMessage(kWhatResume, this);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

void MtkWifiDisplaySource::onMessageReceived(const sp<AMessage> &msg) {
    ///M: Add debug inform. @{
    if(msg->what() != kWhatReapDeadClients){
        ALOGI("what:%d", msg->what());
    }
    ///@}


   if(!mPromoted)
   {
       struct sched_param sched_p;
       mPromoted = true;
       // Change the scheduling policy to SCHED_RR
       sched_getparam(0, &sched_p);
       sched_p.sched_priority = 1;

       if (0 != sched_setscheduler(0, SCHED_RR, &sched_p)) {
           ALOGE("@@[WFD_PROPERTY]sched_setscheduler fail...");
       }
       else {
           sched_p.sched_priority = 0;
           sched_getparam(0, &sched_p);
           ALOGD("@@[WFD_PROPERTY]sched_setscheduler ok..., priority:%d", sched_p.sched_priority);
       }
   }


    switch (msg->what()) {
        case kWhatStart:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            AString iface;
            CHECK(msg->findString("iface", &iface));

            status_t err = OK;

            ssize_t colonPos = iface.find(":");

            unsigned long port;

            if (colonPos >= 0) {
                const char *s = iface.c_str() + colonPos + 1;

                char *end;
                port = strtoul(s, &end, 10);

                if (end == s || *end != '\0' || port > 65535) {
                    err = -EINVAL;
                } else {
                    iface.erase(colonPos, iface.size() - colonPos);
                }
            } else {
                port = kWifiDisplayDefaultPort;
            }

            ALOGI("Bind to IP-port==>%s:%d", iface.c_str(), (int) port);


            ///M: Add debug inform. @{
            if((mWfdFlags & kFastRtpFlag) == kFastRtpFlag){
                ALOGI("Run Fast RTP");
                startRtpClient(iface.c_str(), port);
                return;
            }
            ///@}

            if (err == OK) {
                if (inet_aton(iface.c_str(), &mInterfaceAddr) != 0) {
                    sp<AMessage> notify = new AMessage(kWhatRTSPNotify, this);

                    err = mNetSession->createRTSPServer(
                            mInterfaceAddr, port, notify, &mSessionID);


                    //Enable for Mircast testing
                    sp<AMessage> testNotify = new AMessage(kWhatTestNotify, this);
                        mNetSession->createTCPTextDataSession(
                            mInterfaceAddr, WFD_TESTMODE_PORT, testNotify, &mTestSessionID);

                } else {
                    ALOGE("Error in inet_aton:%d", errno);
                    err = -EINVAL;
                }

            }else{
                ALOGE("Parse Error in iface: %d", err);
            }

            mState = AWAITING_CLIENT_CONNECTION;

            sp<AMessage> response = new AMessage;
            response->setInt32("err", err);
            response->postReply(replyID);
            break;
        }

        case kWhatRTSPNotify:
        {
            int32_t reason;
            CHECK(msg->findInt32("reason", &reason));
            ALOGI("reason:%d", reason);

            switch (reason) {
                case MtkANetworkSession::kWhatError:
                {
                    int32_t sessionID;
                    CHECK(msg->findInt32("sessionID", &sessionID));

                    int32_t err;
                    CHECK(msg->findInt32("err", &err));

                    AString detail;
                    CHECK(msg->findString("detail", &detail));

                    ALOGE("An error occurred in session %d (%d, '%s/%s').",
                          sessionID,
                          err,
                          detail.c_str(),
                          strerror(-err));

                    mNetSession->destroySession(sessionID);

                    if (sessionID == mClientSessionID) {
                        mClientSessionID = 0;

                        ///M: Don't report error event for test program
                        if(mClient != NULL){
                            if (mState < STOPPING){
                                mClient->onDisplayError(
                                    IRemoteDisplayClient::kDisplayErrorUnknown);
                            }
                        }else{ //test mode
                            stopTestSession();
                        }
                    }
                    break;
                }

                case MtkANetworkSession::kWhatClientConnected:
                {
                    int32_t sessionID;
                    CHECK(msg->findInt32("sessionID", &sessionID));

                    if (mClientSessionID > 0) {
                        ALOGW("A client tried to connect, but we already "
                              "have one.");

                        mNetSession->destroySession(sessionID);
                        break;
                    }

                    ///M: Don't check the RTSP state here.
                    //CHECK_EQ(mState, AWAITING_CLIENT_CONNECTION);
///M:@{
                    if (mState != AWAITING_CLIENT_CONNECTION &&
                        mState != STOPPED) {
                        ALOGI("A client tried to connect, but the state is wrong. mState: %d", mState);


                        mNetSession->destroySession(sessionID);
                        break;
                    }
///@}

                    CHECK(msg->findString("client-ip", &mClientInfo.mRemoteIP));
                    CHECK(msg->findString("server-ip", &mClientInfo.mLocalIP));

                    ///M: Disable below local IP checking for testing
#if 0
                    if (mClientInfo.mRemoteIP == mClientInfo.mLocalIP) {
                        // Disallow connections from the local interface
                        // for security reasons.
                        mNetSession->destroySession(sessionID);
                        break;
                    }
#endif

                    CHECK(msg->findInt32(
                                "server-port", &mClientInfo.mLocalPort));
                    mClientInfo.mPlaybackSessionID = -1;

                    mClientSessionID = sessionID;

                    ALOGI("We now have a client (%d) connected.", sessionID);

                    mState = AWAITING_CLIENT_SETUP;

///M:@{
                    ALOGD("Run fast setup:%d", mWfdFlags);
                    if((mWfdFlags & kFastSetupFlag) == 0){
                        status_t err = sendM1(sessionID);
                        CHECK_EQ(err, (status_t)OK);
                    }
///@}

                    break;
                }

                case MtkANetworkSession::kWhatData:
                {
                    status_t err = onReceiveClientData(msg);

                    if (err != OK) {
                        if(mClient != NULL){
                               mClient->onDisplayError(
                                       IRemoteDisplayClient::kDisplayErrorUnknown);
                        }else{
                              stopTestSession();
                           }
                    }

 #if 0
                    // testing only.
                    char val[PROPERTY_VALUE_MAX];
                    if (property_get("media.wfd.trigger", val, NULL)) {
                        if (!strcasecmp(val, "pause") && mState == PLAYING) {
                            mState = PLAYING_TO_PAUSED;
                            sendTrigger(mClientSessionID, TRIGGER_PAUSE);
                        } else if (!strcasecmp(val, "play")
                                    && mState == PAUSED) {
                            mState = PAUSED_TO_PLAYING;
                            sendTrigger(mClientSessionID, TRIGGER_PLAY);
                        }
                    }
#endif
                    break;
                }

                case MtkANetworkSession::kWhatNetworkStall:
                {
                    break;
                }

                default:
                    TRESPASS();
            }
            break;
        }

        case kWhatStop:
        {
            CHECK(msg->senderAwaitsResponse(&mStopReplyID));

            if(mState >= AWAITING_CLIENT_TEARDOWN){
                ALOGD("Already in stop procedure");
                return;
            }

            CHECK_LT(mState, AWAITING_CLIENT_TEARDOWN);

            if (mState >= AWAITING_CLIENT_PLAY) {
                // We have a session, i.e. a previous SETUP succeeded.

                status_t err = sendTrigger(
                        mClientSessionID, TRIGGER_TEARDOWN);

                if (err == OK) {
                    mState = AWAITING_CLIENT_TEARDOWN;

                    (new AMessage(kWhatTeardownTriggerTimedOut, this))->post(
                            kTeardownTriggerTimeouSecs * 1000000ll);

                    break;
                }

                // fall through.
            }

            finishStop();
            break;
        }

        case kWhatPause:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            status_t err = OK;

            if (mState != PLAYING) {
                err = INVALID_OPERATION;
            } else {
                mState = PLAYING_TO_PAUSED;
                sendTrigger(mClientSessionID, TRIGGER_PAUSE);
            }

            sp<AMessage> response = new AMessage;
            response->setInt32("err", err);
            response->postReply(replyID);
            break;
        }

        case kWhatResume:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            status_t err = OK;

            if (mState != PAUSED) {
                err = INVALID_OPERATION;
            } else {
                mState = PAUSED_TO_PLAYING;
                sendTrigger(mClientSessionID, TRIGGER_PLAY);
            }

            sp<AMessage> response = new AMessage;
            response->setInt32("err", err);
            response->postReply(replyID);
            break;
        }

        case kWhatReapDeadClients:
        {
            mReaperPending = false;

            if (mClientSessionID == 0
                    || mClientInfo.mPlaybackSession == NULL) {
                break;
            }

            ///For Miracast testing issue
            if(mState == PAUSED){
               scheduleReaper();
               break;
            }

            if (mClientInfo.mPlaybackSession->getLastLifesignUs()
                    + kPlaybackSessionTimeoutUs < ALooper::GetNowUs()) {
                ALOGI("playback session timed out, reaping.");

                mNetSession->destroySession(mClientSessionID);
                mClientSessionID = 0;

                    if(mClient != NULL){
                        mClient->onDisplayError(
                                IRemoteDisplayClient::kDisplayErrorUnknown);
                    }else{
                        stopTestSession();
                    }
            } else {
                scheduleReaper();
            }
            break;
        }

        case kWhatPlaybackSessionNotify:
        {
            int32_t playbackSessionID;
            CHECK(msg->findInt32("playbackSessionID", &playbackSessionID));

            int32_t what;
            CHECK(msg->findInt32("what", &what));

            if (what == MtkPlaybackSession::kWhatSessionDead) {
                ALOGI("playback session wants to quit.");

                // M: {{{ To avoid playbacksessoin init() is interrupted by destroyAsync()
                mPlaybackSessionIniting = false;

                if (mPlaybackSessionDestroyDeferred) {
                    mPlaybackSessionDestroyDeferred = false;

                    ALOGI("Do Deferred Destroy PlaybackSession ");
                    mClientInfo.mPlaybackSession->destroyAsync();
                    return;
                }
                // M: }}}


                if(mClient != NULL){
                    if (mState < STOPPING){
                        mClient->onDisplayError(
                                IRemoteDisplayClient::kDisplayErrorUnknown);
                    }
                }else{
                    stopTestSession();
                }
            } else if (what == MtkPlaybackSession::kWhatSessionEstablished) {
                ALOGI("playback session established");

                mPlaybackSessionEstablished = true;

                // M: {{{ To avoid playbacksessoin init() is interrupted by destroyAsync()
                mPlaybackSessionIniting = false;

                if (mPlaybackSessionDestroyDeferred) {
                    mPlaybackSessionDestroyDeferred = false;

                    ALOGI("Do Deferred Destroy PlaybackSession ");
                    mClientInfo.mPlaybackSession->destroyAsync();
                    return;
                }
                // M: }}}

                if (mClient != NULL) {
                    ALOGD("PlaybackSession::kWhatSessionEstablished HDCP = %d\n", mUsingHDCP);
                    if (!mSinkSupportsVideo) {
                        mClient->onDisplayConnected(
                                NULL,  // SurfaceTexture
                                0, // width,
                                0, // height,
                                mUsingHDCP
                                    ? IRemoteDisplayClient::kDisplayFlagSecure
                                    : 0,
                                0);
                    } else {
                        size_t width, height;

                        CHECK(MtkVideoFormats::GetConfiguration(
                                    mChosenVideoResolutionType,
                                    mChosenVideoResolutionIndex,
                                    &width,
                                    &height,
                                    NULL /* framesPerSecond */,
                                    NULL /* interlaced */));

                        mClient->onDisplayConnected(
                                mClientInfo.mPlaybackSession
                                    ->getSurfaceTexture(),
                                width,
                                height,
                                mUsingHDCP
                                    ? IRemoteDisplayClient::kDisplayFlagSecure
                                    : 0,
                                playbackSessionID);
                    }
                }
#if 1
                else{
                    DisplayInfo displayInfo;
                    uint32_t physWidth = 1280;
                    uint32_t physHeight = 720;
                    int layerWidth = 1280;
                    int layerHeight = 720;
                    size_t displayRectWidth, displayRectHeight;
                    CHECK(MtkVideoFormats::GetConfiguration(
                                    mChosenVideoResolutionType,
                                    mChosenVideoResolutionIndex,
                                    &displayRectWidth,
                                    &displayRectHeight,
                                    NULL /* framesPerSecond */,
                                    NULL /* interlaced */));

                    ALOGI("[test] onDisplayConnected width=%u, height=%u, flags = 0x%08x",
                            (uint32_t)displayRectWidth, (uint32_t)displayRectHeight, mUsingHDCP);

                    sp<IBinder> dtoken(SurfaceComposerClient::getBuiltInDisplay(ISurfaceComposer::eDisplayIdMain));

                    if (SurfaceComposerClient::getDisplayInfo(dtoken, &displayInfo) < 0) {
                        ALOGE("Can't get display info");
                        displayInfo.w = 1280;
                        displayInfo.h = 720;
                        displayInfo.orientation = DISPLAY_ORIENTATION_0;
                    }else{
                        ALOGI("Built-in display info: %d:%d:%d", displayInfo.w, displayInfo.h, displayInfo.orientation);
                    }

                    if(displayInfo.orientation == DISPLAY_ORIENTATION_90 || displayInfo.orientation == DISPLAY_ORIENTATION_270){
                         layerWidth = displayInfo.h;
                         layerHeight = displayInfo.w;
                    }else{
                         layerWidth = displayInfo.w;
                         layerHeight = displayInfo.h;
                    }

                    physWidth = displayRectWidth;
                    physHeight = displayRectHeight;

                    //The built-in display resolution
                    Rect layerStackRect(0, 0, layerWidth, layerHeight);  // XXX fix this.
                    ALOGI("layerStackRect: %d:%d:%d", layerWidth, layerHeight, displayInfo.orientation);

                    mBufferProducer = mClientInfo.mPlaybackSession->getSurfaceTexture();
                    mDisplayBinder = mComposerClient->createDisplay(
                            String8("foo"), mUsingHDCP /*true*/ /* secure */);

                    if (physWidth * layerHeight
                                < physHeight * layerWidth) {
                        // Letter box.
                        displayRectWidth = physWidth;
                        displayRectHeight = layerHeight * physWidth / layerWidth;
                    } else {
                        // Pillar box.
                        displayRectWidth = layerWidth * physHeight / layerHeight;
                        displayRectHeight = physHeight;
                    }

                    int displayRectTop = (physHeight - displayRectHeight) / 2;
                    int displayRectLeft = (physWidth - displayRectWidth) / 2;

                    Rect displayRect(displayRectLeft, displayRectTop,
                            displayRectLeft + displayRectWidth, displayRectTop + displayRectHeight);

                    ALOGI("displayRect: %d:%d:%d:%d", displayRectLeft, displayRectTop,
                            displayRectLeft + (int)displayRectWidth, displayRectTop + (int)displayRectHeight);

                    //Rect displayRect(0, 60, 640, 60 + 360);
                    //Rect displayRect(185, 0, 185 + 270, 480);

                    SurfaceComposerClient::openGlobalTransaction();
                    mComposerClient->setDisplaySurface(mDisplayBinder, mBufferProducer);
                    mComposerClient->setDisplayProjection(
                            mDisplayBinder, 0 /* 0 degree rotation */,
                            layerStackRect,
                            displayRect);
                    SurfaceComposerClient::setDisplayLayerStack(mDisplayBinder, 0);    // default stack
                    SurfaceComposerClient::closeGlobalTransaction();
                }
#endif

                ///M:@{
                if ((mWfdFlags & kSigmaTest) == kSigmaTest) {
                    if (mPlayRequestReceived) {
                        finishPlay();
                        if (mState == ABOUT_TO_PLAY) {
                            mState = PLAYING;
                        }
                    } else {
                        ALOGI("[For Miracast MTK test bed]deferring finishPlay() until play request");
                    }
                ///}}}
                } else {
                    finishPlay();
                    if (mState == ABOUT_TO_PLAY) {
                        mState = PLAYING;
                    }
                }


            } else if (what == MtkPlaybackSession::kWhatSessionDestroyed) {
                disconnectClient2();
            } else {
                CHECK_EQ(what, MtkPlaybackSession::kWhatBinaryData);

                int32_t channel;
                CHECK(msg->findInt32("channel", &channel));

                sp<ABuffer> data;
                CHECK(msg->findBuffer("data", &data));

                CHECK_LE((size_t)channel, 0xffu);
                CHECK_LE(data->size(), 0xffffu);

                int32_t sessionID;
                CHECK(msg->findInt32("sessionID", &sessionID));

                char header[4];
                header[0] = '$';
                header[1] = channel;
                header[2] = data->size() >> 8;
                header[3] = data->size() & 0xff;

                mNetSession->sendRequest(
                        sessionID, header, sizeof(header));

                mNetSession->sendRequest(
                        sessionID, data->data(), data->size());
            }
            break;
        }

        case kWhatKeepAlive:
        {
            int32_t sessionID;
            CHECK(msg->findInt32("sessionID", &sessionID));

            if (mClientSessionID != sessionID) {
                // Obsolete event, client is already gone.
                break;
            }

            sendM16(sessionID);
            break;
        }

        case kWhatTeardownTriggerTimedOut:
        {
            if (mState == AWAITING_CLIENT_TEARDOWN) {
                ALOGI("TEARDOWN trigger timed out, forcing disconnection.");

                CHECK(mStopReplyID != NULL);
                finishStop();
                break;
            }
            break;
        }

        case kWhatHDCPNotify:
        {
            int32_t msgCode, ext1, ext2;
            CHECK(msg->findInt32("msg", &msgCode));
            CHECK(msg->findInt32("ext1", &ext1));
            CHECK(msg->findInt32("ext2", &ext2));

            ALOGD("Saw HDCP notification code %d=[%s], ext1 %d, ext2 %d",
                    msgCode, hdcp_notify_str[msgCode], ext1, ext2);

            switch (msgCode) {
                case HDCPModule::HDCP_INITIALIZATION_COMPLETE:
                {
                    mHDCPInitializationComplete = true;

                    if (mSetupTriggerDeferred) {
                        mSetupTriggerDeferred = false;

                        sendTrigger(mClientSessionID, TRIGGER_SETUP);
                    }
                    break;
                }

                // if HDCP authentication failed, disable HDCP

                case HDCPModule::HDCP_INITIALIZATION_FAILED:
                {
                    mHDCPInitializationComplete = true;
                    mUsingHDCP = false;

                    ALOGE("HDCP init failure, disable HDCP.");

                    if (mSetupTriggerDeferred) {
                        mSetupTriggerDeferred = false;

                        ALOGI("sendTrigger");

                        sendTrigger(mClientSessionID, TRIGGER_SETUP);
                    }
                    break;
                }

                case HDCPModule::HDCP_SHUTDOWN_COMPLETE:
                case HDCPModule::HDCP_SHUTDOWN_FAILED:
                {
                    // Ugly hack to make sure that the call to
                    // HDCPObserver::notify is completely handled before
                    // we clear the HDCP instance and unload the shared
                    // library :(
                    (new AMessage(kWhatFinishStop2, this))->post(300000ll);
                    break;
                }

                default:
                {
                    ALOGE("HDCP failure, shutting down.");

                    if(mClient != NULL){
                        mClient->onDisplayError(
                                IRemoteDisplayClient::kDisplayErrorUnknown);
                    }else{
                        stopTestSession();
                    }
                    break;
                }
            }
            break;
        }

        case kWhatFinishStop2:
        {
            finishStop2();
            break;
        }

        ///M: Add by MTK @{
        case kWhatTestNotify:
        {
            int32_t reason;
            CHECK(msg->findInt32("reason", &reason));

            ALOGI("reason:%d", reason);

            switch (reason) {
                case MtkANetworkSession::kWhatClientConnected:
                {
                     int32_t sessionID;
                     CHECK(msg->findInt32("sessionID", &sessionID));

                     if (mTestClientSessionID > 0) {
                         ALOGW("A test client tried to connect, but we already "
                               "have one.");

                         mNetSession->destroySession(sessionID);
                         break;
                     }

                     mTestClientSessionID = sessionID;

                     ALOGI("We now have a test client (%d) connected.", sessionID);
                     break;
                }
                case MtkANetworkSession::kWhatError:
                {
                     int32_t sessionID;
                     CHECK(msg->findInt32("sessionID", &sessionID));
                     int32_t err;
                     CHECK(msg->findInt32("err", &err));

                     AString detail;
                     CHECK(msg->findString("detail", &detail));

                     ALOGE("An error occurred in test session %d (%d, '%s/%s').",
                       sessionID,
                       err,
                       detail.c_str(),
                       strerror(-err));

                     mNetSession->destroySession(sessionID);
                     if (sessionID == mTestClientSessionID) {
                         mTestClientSessionID = 0;
                     }
                     break;
                }
                case MtkANetworkSession::kWhatTextData:
                {
                     onReceiveTestData(msg);
                     break;
                }
                default:
                     TRESPASS();
                     break;
            }
            break;
        }

        case kWhatRtpNotify:
        {
            ALOGI("Fast RTP is running");
            break;
        }
        case kWhatSendGenericMsg:
        {
            int32_t cmd;
            CHECK(msg->findInt32("cmd", &cmd));
            ALOGI("cmd:%d", cmd);

            status_t err = OK;

            if(mClientInfo.mPlaybackSessionID == -1){
                ALOGE("No Client");
                return;
            }

            switch (cmd){
                case IRemoteDisplay::kGenericMessagePlay:
                {
                    ALOGI("IRemoteDisplay::kGenericMessagePlay");
                    if(mState == ABOUT_TO_PLAY){
                        sendGenericMsgByMethod (TRIGGER_PLAY);
                    }else{
                        ALOGE("Wrong state in PLAY");
                    }
                    break;
                }
                case IRemoteDisplay::kGenericMessagePause:
                {
                    ALOGI("IRemoteDisplay::kGenericMessagePause");
                    if(mState == PLAYING){
                        sendGenericMsgByMethod (TRIGGER_PAUSE);
                    }else{
                        ALOGE("Wrong state in PAUSE");
                    }
                    break;
                }
                case IRemoteDisplay::kGenericMessageTeardown:
                {
                    ALOGI("IRemoteDisplay::kGenericMessageTeardown");
                    sendGenericMsgByMethod (TRIGGER_TEARDOWN);
                    break;
                }
                default:
                {
                    err = BAD_VALUE;
                }
            };
            break;
        }
        ///@}
        default:
            TRESPASS();
    }
}

void MtkWifiDisplaySource::registerResponseHandler(
        int32_t sessionID, int32_t cseq, HandleRTSPResponseFunc func) {
    ResponseID id;
    id.mSessionID = sessionID;
    id.mCSeq = cseq;
    mResponseHandlers.add(id, func);
}

status_t MtkWifiDisplaySource::sendM1(int32_t sessionID) {
    AString request = "OPTIONS * RTSP/1.0\r\n";
    ///M: mircast testing
    mNextCSeq = 1;

    AppendCommonResponse(&request, mNextCSeq);

    request.append(
            "Require: org.wfa.wfd1.0\r\n"
            "\r\n");

    status_t err =
        mNetSession->sendRequest(sessionID, request.c_str(), request.size());

    if (err != OK) {
        return err;
    }

    registerResponseHandler(
            sessionID, mNextCSeq, &MtkWifiDisplaySource::onReceiveM1Response);

    ++mNextCSeq;

    return OK;
}

status_t MtkWifiDisplaySource::sendM3(int32_t sessionID) {
    AString body =
        "wfd_content_protection\r\n"
        "wfd_video_formats\r\n"
        "wfd_audio_codecs\r\n"
        "wfd_client_rtp_ports\r\n";

    ALOGI("sendM3():%s", body.c_str());

    AString request = "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\n";
    AppendCommonResponse(&request, mNextCSeq);

    request.append("Content-Type: text/parameters\r\n");
    request.append(AStringPrintf("Content-Length: %d\r\n", body.size()));

    request.append("\r\n");
    request.append(body);

    status_t err =
        mNetSession->sendRequest(sessionID, request.c_str(), request.size());

    if (err != OK) {
        return err;
    }

    registerResponseHandler(
            sessionID, mNextCSeq, &MtkWifiDisplaySource::onReceiveM3Response);

    ++mNextCSeq;

    return OK;
}

status_t MtkWifiDisplaySource::sendM4(int32_t sessionID) {
    CHECK_EQ(sessionID, mClientSessionID);

    AString body;

    if (mSinkSupportsVideo) {
        body.append("wfd_video_formats: ");

        MtkVideoFormats chosenVideoFormat;
        chosenVideoFormat.disableAll();
        chosenVideoFormat.setNativeResolution(
                mChosenVideoResolutionType, mChosenVideoResolutionIndex);
        chosenVideoFormat.setProfileLevel(
                mChosenVideoResolutionType, mChosenVideoResolutionIndex,
                mChosenVideoProfile, mChosenVideoLevel);

        if((mWfdFlags & kSigmaTest) == kSigmaTest){
           ALOGI("test video codec");
           body.append("00 00 01 01 00000001 00000000 00000000 00 0000 0000 01 none none");
        }else{
            body.append(chosenVideoFormat.getFormatSpec(true /* forM4Message */));
        }

        body.append("\r\n");
        ALOGI("videoFormatString:%s", body.c_str());
    }



    if (mSinkSupportsAudio) {
        body.append(
                AStringPrintf("wfd_audio_codecs: %s\r\n",
                             (mUsingPCMAudio
                                ? "LPCM 00000002 00" // 2 ch PCM 48kHz
                                : "AAC 00000001 00")));  // 2 ch AAC 48kHz
    }

    body.append(
            AStringPrintf(
                "wfd_presentation_URL: rtsp://%s/wfd1.0/streamid=0 none\r\n",
                mClientInfo.mLocalIP.c_str()));

    body.append(
            AStringPrintf(
                "wfd_client_rtp_ports: %s\r\n", mWfdClientRtpPorts.c_str()));

    AString request = "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\n";
    AppendCommonResponse(&request, mNextCSeq);

    request.append("Content-Type: text/parameters\r\n");
    request.append(AStringPrintf("Content-Length: %d\r\n", body.size()));
    request.append("\r\n");
    request.append(body);

    status_t err =
        mNetSession->sendRequest(sessionID, request.c_str(), request.size());

    if (err != OK) {
        return err;
    }

    registerResponseHandler(
            sessionID, mNextCSeq, &MtkWifiDisplaySource::onReceiveM4Response);

    ++mNextCSeq;

    return OK;
}

status_t MtkWifiDisplaySource::sendTrigger(
        int32_t sessionID, TriggerType triggerType) {
    AString body = "wfd_trigger_method: ";
    switch (triggerType) {
        case TRIGGER_SETUP:
            body.append("SETUP");
            break;
        case TRIGGER_TEARDOWN:
        ALOGI("Sending TEARDOWN trigger.");
        body.append("TEARDOWN");
            break;
        case TRIGGER_PAUSE:
            body.append("PAUSE");
            break;
        case TRIGGER_PLAY:
            body.append("PLAY");
            break;
        default:
            TRESPASS();
    }

    body.append("\r\n");

    AString request = "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\n";
    AppendCommonResponse(&request, mNextCSeq);

    request.append("Content-Type: text/parameters\r\n");
    request.append(AStringPrintf("Content-Length: %d\r\n", body.size()));
    request.append("\r\n");
    request.append(body);

    status_t err =
        mNetSession->sendRequest(sessionID, request.c_str(), request.size());

    if (err != OK) {
        return err;
    }

    registerResponseHandler(
            sessionID, mNextCSeq, &MtkWifiDisplaySource::onReceiveM5Response);

    ++mNextCSeq;

    return OK;
}

status_t MtkWifiDisplaySource::sendM16(int32_t sessionID) {
    AString request = "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\n";
    AppendCommonResponse(&request, mNextCSeq);

    CHECK_EQ(sessionID, mClientSessionID);
    request.append(
            AStringPrintf("Session: %d\r\n", mClientInfo.mPlaybackSessionID));
    request.append("\r\n");  // Empty body

    status_t err =
        mNetSession->sendRequest(sessionID, request.c_str(), request.size());

    ///M: Increase timeout value for Keep alive message @{
    if (mClientInfo.mPlaybackSession != NULL) {
        mClientInfo.mPlaybackSession->updateLiveness();
    }
    ///@}


    if (err != OK) {
        return err;
    }

    registerResponseHandler(
            sessionID, mNextCSeq, &MtkWifiDisplaySource::onReceiveM16Response);

    ++mNextCSeq;

    scheduleKeepAlive(sessionID);

    return OK;
}

status_t MtkWifiDisplaySource::onReceiveM1Response(
        int32_t /* sessionID */, const sp<ParsedMessage> &msg) {
    int32_t statusCode;
    if (!msg->getStatusCode(&statusCode)) {
        return ERROR_MALFORMED;
    }

    if (statusCode != 200) {
        return ERROR_UNSUPPORTED;
    }

    return OK;
}

// sink_audio_list := ("LPCM"|"AAC"|"AC3" HEXDIGIT*8 HEXDIGIT*2)
//                       (", " sink_audio_list)*
static void GetAudioModes(const char *s, const char *prefix, uint32_t *modes) {
    *modes = 0;

    size_t prefixLen = strlen(prefix);

    while (*s != '0') {
        if (!strncmp(s, prefix, prefixLen) && s[prefixLen] == ' ') {
            unsigned latency;
            if (sscanf(&s[prefixLen + 1], "%08x %02x", modes, &latency) != 2) {
                *modes = 0;
            }

            return;
        }

        const char *commaPos = strchr(s, ',');
        if (commaPos != NULL) {
            s = commaPos + 1;

            while (isspace(*s)) {
                ++s;
            }
        } else {
            break;
        }
    }
}

status_t MtkWifiDisplaySource::onReceiveM3Response(
        int32_t sessionID, const sp<ParsedMessage> &msg) {
    int32_t statusCode;
    if (!msg->getStatusCode(&statusCode)) {
        return ERROR_MALFORMED;
    }

    if (statusCode != 200) {
        return ERROR_UNSUPPORTED;
    }

    sp<MtkParameters> params =
        MtkParameters::Parse(msg->getContent(), strlen(msg->getContent()));

    if (params == NULL) {
        ALOGE("no content");
        return ERROR_MALFORMED;
    }

    AString value;
    if (!params->findParameter("wfd_client_rtp_ports", &value)) {
        ALOGE("Sink doesn't report its choice of wfd_client_rtp_ports.");
        return ERROR_MALFORMED;
    }

    unsigned port0 = 0, port1 = 0;
    if (sscanf(value.c_str(),
               "RTP/AVP/UDP;unicast %u %u mode=play",
               &port0,
               &port1) == 2
        || sscanf(value.c_str(),
               "RTP/AVP/TCP;unicast %u %u mode=play",
               &port0,
               &port1) == 2) {
            if (port0 == 0 || port0 > 65535 || port1 != 0) {
                ALOGE("Sink chose its wfd_client_rtp_ports poorly (%s)",
                      value.c_str());

                return ERROR_MALFORMED;
            }
    } else if (strcmp(value.c_str(), "RTP/AVP/TCP;interleaved mode=play")) {
        ALOGE("Unsupported value for wfd_client_rtp_ports (%s)",
              value.c_str());

        return ERROR_UNSUPPORTED;
    }

    mWfdClientRtpPorts = value;
    mChosenRTPPort = port0;

    if (!params->findParameter("wfd_video_formats", &value)) {
        ALOGE("Sink doesn't report its choice of wfd_video_formats.");
        return ERROR_MALFORMED;
    }

    mSinkSupportsVideo = false;
    if (!(value == "none")) {
        ALOGI("Parse String:%s", value.c_str());
        mSinkSupportsVideo = true;
        if (!mSupportedSinkVideoFormats.parseFormatSpec(value.c_str())) {
             ALOGE("Failed to parse sink provided wfd_video_formats (%s)",
                   value.c_str());

             return ERROR_MALFORMED;
       }

        ///Configure for test mode
        if((mWfdFlags & kSigmaTest) == kSigmaTest){
            mChosenVideoResolutionType = MtkVideoFormats::RESOLUTION_CEA;
            mChosenVideoResolutionIndex = 0;
            mChosenVideoProfile = MtkVideoFormats::PROFILE_CBP;
            mChosenVideoLevel = MtkVideoFormats::LEVEL_31;

        } else {
            if (!MtkVideoFormats::PickBestFormat(
                        mSupportedSinkVideoFormats,
                        mSupportedSourceVideoFormats,
                        &mChosenVideoResolutionType,
                        &mChosenVideoResolutionIndex,
                        &mChosenVideoProfile,
                        &mChosenVideoLevel)) {
                ALOGE("Sink and source share no commonly supported video "
                      "formats.");

                return ERROR_UNSUPPORTED;
            }
        }

       size_t width, height, framesPerSecond;
       bool interlaced;
       CHECK(MtkVideoFormats::GetConfiguration(
                        mChosenVideoResolutionType,
                        mChosenVideoResolutionIndex,
                        &width,
                        &height,
                        &framesPerSecond,
                        &interlaced));


       ALOGI("Picked video resolution %zu x %zu %c%zu",
              width, height, interlaced ? 'i' : 'p', framesPerSecond);

       char val[PROPERTY_VALUE_MAX];
       if (property_get("ro.mtk_hybrid_encode_support", val, NULL)
               && (!strcmp(val, "true") )) {
           mChosenVideoProfile = MtkVideoFormats::PROFILE_CBP;
           mChosenVideoLevel = MtkVideoFormats::LEVEL_31;
           ALOGD("hybrid encode support");
       }

       ALOGI("Picked AVC profile %d, level %d",
              mChosenVideoProfile, mChosenVideoLevel);
    } else {
        ALOGI("Sink doesn't support video at all.");
    }

    if (!params->findParameter("wfd_audio_codecs", &value)) {
        ALOGE("Sink doesn't report its choice of wfd_audio_codecs.");
        return ERROR_MALFORMED;
    }

    mSinkSupportsAudio = false;

    if  (!(value == "none")) {
        mSinkSupportsAudio = true;

        uint32_t modes;
        GetAudioModes(value.c_str(), "AAC", &modes);

        bool supportsAAC = (modes & 1) != 0;  // AAC 2ch 48kHz

        GetAudioModes(value.c_str(), "LPCM", &modes);

        bool supportsPCM = (modes & 2) != 0;  // LPCM 2ch 48kHz

        char val[PROPERTY_VALUE_MAX];
        if (supportsPCM
                && property_get("media.wfd.use-pcm-audio", val, NULL)
                && (!strcasecmp("true", val) || !strcmp("1", val))) {
            ALOGI("Using PCM audio.");
            mUsingPCMAudio = true;
        } else if (supportsAAC) {
            ALOGI("Using AAC audio.");
            mUsingPCMAudio = false;
        } else if (supportsPCM) {
            ALOGI("Using PCM audio.");
            mUsingPCMAudio = true;
        } else {
            ALOGI("Sink doesn't support an audio format we do.");
            return ERROR_UNSUPPORTED;
        }
    } else {
        ALOGI("Sink doesn't support audio at all.");
    }

    if (!mSinkSupportsVideo && !mSinkSupportsAudio) {
        ALOGE("Sink supports neither video nor audio...");
        return ERROR_UNSUPPORTED;
    }

    mUsingHDCP = false;
    if (!params->findParameter("wfd_content_protection", &value)) {
        ALOGI("Sink doesn't appear to support content protection.");
    } else if (value == "none") {
        ALOGI("Sink does not support content protection.");
    } else {

#if HDCP_ENABLE_CONTROL
        char v[PROPERTY_VALUE_MAX];
        if (property_get("media.stagefright_wfd.hdcp.off", v, NULL)
                && (!strcmp(v, "1") ))
        {
            ALOGD("turn off HDCP !");
        }
        else
#endif
        {
            mUsingHDCP = true;

            bool isHDCP2_0 = false;
            if (value.startsWith("HDCP2.0 ")) {
                isHDCP2_0 = true;
            }

            else if ((value.startsWith("HDCP2.1 ")) || (value.startsWith("HDCP2.2 "))) {
                isHDCP2_0 = true;
            } else {
               return ERROR_MALFORMED;
            }

            int32_t hdcpPort;
            if (!ParsedMessage::GetInt32Attribute(
                        value.c_str() + 8, "port", &hdcpPort)
                    || hdcpPort < 1 || hdcpPort > 65535) {
                return ERROR_MALFORMED;
            }

            mIsHDCP2_0 = isHDCP2_0;
            mHDCPPort = hdcpPort;

            status_t err = makeHDCP();
            if (err != OK) {
                ALOGE("Unable to instantiate HDCP component. "
                      "Not using HDCP after all.");

                mUsingHDCP = false;
            }
        }
    }

    return sendM4(sessionID);
}

status_t MtkWifiDisplaySource::onReceiveM4Response(
        int32_t sessionID, const sp<ParsedMessage> &msg) {
    int32_t statusCode;
    if (!msg->getStatusCode(&statusCode)) {
        return ERROR_MALFORMED;
    }

    if (statusCode != 200) {
        return ERROR_UNSUPPORTED;
    }

    ALOGI("onReceiveM4Response(), mUsingHDCP:%d, mHDCPInitializationComplete:%d", mUsingHDCP, mHDCPInitializationComplete);

    if (mUsingHDCP && !mHDCPInitializationComplete) {
        ALOGI("Deferring SETUP trigger until HDCP initialization completes.");

        mSetupTriggerDeferred = true;
        return OK;
    }

    return sendTrigger(sessionID, TRIGGER_SETUP);
}

status_t MtkWifiDisplaySource::onReceiveM5Response(
        int32_t /* sessionID */, const sp<ParsedMessage> &msg) {
    int32_t statusCode;
    if (!msg->getStatusCode(&statusCode)) {
        return ERROR_MALFORMED;
    }

    if (statusCode != 200) {
        return ERROR_UNSUPPORTED;
    }

    return OK;
}

status_t MtkWifiDisplaySource::onReceiveM16Response(
        int32_t sessionID, const sp<ParsedMessage> & /* msg */) {
    // If only the response was required to include a "Session:" header...

    CHECK_EQ(sessionID, mClientSessionID);

    if (mClientInfo.mPlaybackSession != NULL) {
        mClientInfo.mPlaybackSession->updateLiveness();
    }

    return OK;
}

void MtkWifiDisplaySource::scheduleReaper() {
    if (mReaperPending) {
        return;
    }

    mReaperPending = true;
    (new AMessage(kWhatReapDeadClients, this))->post(kReaperIntervalUs);
}

void MtkWifiDisplaySource::scheduleKeepAlive(int32_t sessionID) {
    // We need to send updates at least 5 secs before the timeout is set to
    // expire, make sure the timeout is greater than 5 secs to begin with.
    CHECK_GT(kPlaybackSessionTimeoutUs, 5000000ll);

    sp<AMessage> msg = new AMessage(kWhatKeepAlive, this);
    msg->setInt32("sessionID", sessionID);
    msg->post(kPlaybackSessionTimeoutUs - 5000000ll);
}

status_t MtkWifiDisplaySource::onReceiveClientData(const sp<AMessage> &msg) {
    int32_t sessionID;
    CHECK(msg->findInt32("sessionID", &sessionID));

    sp<RefBase> obj;
    CHECK(msg->findObject("data", &obj));

    sp<ParsedMessage> data =
        static_cast<ParsedMessage *>(obj.get());

    ALOGI("session %d received '%s'",
          sessionID, data->debugString().c_str());

    AString method;
    AString uri;
    data->getRequestField(0, &method);

    int32_t cseq;
    if (!data->findInt32("cseq", &cseq)) {
        sendErrorResponse(sessionID, "400 Bad Request", -1 /* cseq */);
        return ERROR_MALFORMED;
    }
    //M: Add error handling for the client session is disconnected from other thread @{
//    if( mClientSessionID <= 0){
//        ALOGE("The client session is disconnected. Ingore the incoming session data");
//        return;
//    }
    // @}

    if (method.startsWith("RTSP/")) {
        // This is a response.

        ResponseID id;
        id.mSessionID = sessionID;
        id.mCSeq = cseq;

        ssize_t index = mResponseHandlers.indexOfKey(id);

        if (index < 0) {
            ALOGW("Received unsolicited server response, cseq %d", cseq);
            return ERROR_MALFORMED;
        }

        HandleRTSPResponseFunc func = mResponseHandlers.valueAt(index);
        mResponseHandlers.removeItemsAt(index);

        status_t err = (this->*func)(sessionID, data);

        if (err != OK) {
            ALOGW("Response handler for session %d, cseq %d returned "
                  "err %d (%s)",
                  sessionID, cseq, err, strerror(-err));

            return err;
        }

        return OK;
    }

    AString version;
    data->getRequestField(2, &version);
    if (!(version == AString("RTSP/1.0"))) {
        sendErrorResponse(sessionID, "505 RTSP Version not supported", cseq);
        return ERROR_UNSUPPORTED;
    }

    status_t err;
    if (method == "OPTIONS") {
        err = onOptionsRequest(sessionID, cseq, data);
    } else if (method == "SETUP") {
        err = onSetupRequest(sessionID, cseq, data);
    } else if (method == "PLAY") {
        err = onPlayRequest(sessionID, cseq, data);
    } else if (method == "PAUSE") {
            ALOGI(" received a request method = PAUSE");
        err = onPauseRequest(sessionID, cseq, data);
    } else if (method == "TEARDOWN") {
            ALOGI(" received a request method = TEARDOWN");
        err = onTeardownRequest(sessionID, cseq, data);
    } else if (method == "GET_PARAMETER") {
            ALOGI(" received a request method = GET_PARAMETER");
        err = onGetParameterRequest(sessionID, cseq, data);
    } else if (method == "SET_PARAMETER") {
            ALOGI(" received a request method = SET_PARAMETER");
        err = onSetParameterRequest(sessionID, cseq, data);
    } else {
        sendErrorResponse(sessionID, "405 Method Not Allowed", cseq);

        err = ERROR_UNSUPPORTED;
    }

    return err;
}

status_t MtkWifiDisplaySource::onOptionsRequest(
        int32_t sessionID,
        int32_t cseq,
        const sp<ParsedMessage> &data) {
    int32_t playbackSessionID;
    sp<MtkPlaybackSession> playbackSession =
        findPlaybackSession(data, &playbackSessionID);

    if (playbackSession != NULL) {
        playbackSession->updateLiveness();
    }

    AString response = "RTSP/1.0 200 OK\r\n";
    AppendCommonResponse(&response, cseq);

    response.append(
            "Public: org.wfa.wfd1.0, SETUP, TEARDOWN, PLAY, PAUSE, "
            "GET_PARAMETER, SET_PARAMETER\r\n");

    response.append("\r\n");

    status_t err = mNetSession->sendDirectRequest(sessionID, response.c_str(), response.size());

    if (err == OK) {
        // For mediatek test bed, can not send M3 message immediately
        if((mWfdFlags & kTestModeFlag) == kTestModeFlag){
            ALOGI("sleep 100ms for mediatek miracast test bed");
            usleep(100 * 1000);
        }

        err = sendM3(sessionID);
    }

    return err;
}

status_t MtkWifiDisplaySource::onSetupRequest(
        int32_t sessionID,
        int32_t cseq,
        const sp<ParsedMessage> &data) {
    CHECK_EQ(sessionID, mClientSessionID);
    if (mClientInfo.mPlaybackSessionID != -1) {
        // We only support a single playback session per client.
        // This is due to the reversed keep-alive design in the wfd specs...
        sendErrorResponse(sessionID, "400 Bad Request", cseq);
        return ERROR_MALFORMED;
    }

    AString transport;
    if (!data->findString("transport", &transport)) {
        sendErrorResponse(sessionID, "400 Bad Request", cseq);
        return ERROR_MALFORMED;
    }

    MtkRTPSender::TransportMode rtpMode = MtkRTPSender::TRANSPORT_UDP;

    int clientRtp, clientRtcp;
    if (transport.startsWith("RTP/AVP/TCP;")) {
        AString interleaved;
        if (ParsedMessage::GetAttribute(
                    transport.c_str(), "interleaved", &interleaved)
                && sscanf(interleaved.c_str(), "%d-%d",
                          &clientRtp, &clientRtcp) == 2) {
            rtpMode = MtkRTPSender::TRANSPORT_TCP_INTERLEAVED;
        } else {
            bool badRequest = false;

            AString clientPort;
            if (!ParsedMessage::GetAttribute(
                        transport.c_str(), "client_port", &clientPort)) {
                badRequest = true;
            } else if (sscanf(clientPort.c_str(), "%d-%d",
                              &clientRtp, &clientRtcp) == 2) {
            } else if (sscanf(clientPort.c_str(), "%d", &clientRtp) == 1) {
                // No RTCP.
                clientRtcp = -1;
            } else {
                badRequest = true;
            }

            if (badRequest) {
                sendErrorResponse(sessionID, "400 Bad Request", cseq);
                return ERROR_MALFORMED;
            }

            rtpMode = MtkRTPSender::TRANSPORT_TCP;
        }
    } else if (transport.startsWith("RTP/AVP;unicast;")
            || transport.startsWith("RTP/AVP/UDP;unicast;")
///M:@{
            || transport.startsWith("RTP/AVP/UDP;unicast ")) {
///@}
        bool badRequest = false;

        AString clientPort;
///M:@{
        if(transport.startsWith("RTP/AVP/UDP;unicast ")){
           transport.erase(0, strlen("RTP/AVP/UDP;unicast "));
        }
///@}
        if (!ParsedMessage::GetAttribute(
                    transport.c_str(), "client_port", &clientPort)) {
            badRequest = true;
        } else if (sscanf(clientPort.c_str(), "%d-%d",
                          &clientRtp, &clientRtcp) == 2) {
        } else if (sscanf(clientPort.c_str(), "%d", &clientRtp) == 1) {
            // No RTCP.
            clientRtcp = -1;
        } else {
            badRequest = true;
        }

        if (badRequest) {
            sendErrorResponse(sessionID, "400 Bad Request", cseq);
            return ERROR_MALFORMED;
        }
#if 1
    // The older LG dongles doesn't specify client_port=xxx apparently.
    } else if (transport == "RTP/AVP/UDP;unicast") {
        clientRtp = 19000;
        clientRtcp = -1;
#endif
    } else {
        sendErrorResponse(sessionID, "461 Unsupported Transport", cseq);
        return ERROR_UNSUPPORTED;
    }

    int32_t playbackSessionID = makeUniquePlaybackSessionID();

    sp<AMessage> notify = new AMessage(kWhatPlaybackSessionNotify, this);
    notify->setInt32("playbackSessionID", playbackSessionID);
    notify->setInt32("sessionID", sessionID);

    // mtk00634 for HDCP authenticaion fail error handling, set NULL pointer to playbackSession to disable HDCP encrypt

    sp<MtkPlaybackSession> playbackSession;

    if (mUsingHDCP)
    {
       ALOGI("new playbackSession w/ HDCP");
       playbackSession =
            new MtkPlaybackSession(
                    mOpPackageName, mNetSession, notify, mInterfaceAddr, mHDCP, mClientUid,
                    mClientPid, mMediaPath.c_str());
    }
    else
    {
       ALOGI("new playbackSession w/o HDCP");
       playbackSession =
            new MtkPlaybackSession(
                    mOpPackageName, mNetSession, notify, mInterfaceAddr, NULL, mClientUid,
                    mClientPid, mMediaPath.c_str());
    }

    //M: To avoid playbacksessoin init() is interrupted by destroyAsync()
    mPlaybackSessionIniting = false;



    looper()->registerHandler(playbackSession);

    AString uri;
    data->getRequestField(1, &uri);

    if (strncasecmp("rtsp://", uri.c_str(), 7)) {
        sendErrorResponse(sessionID, "400 Bad Request", cseq);
        return ERROR_MALFORMED;
    }

    if (!(uri.startsWith("rtsp://") && uri.endsWith("/wfd1.0/streamid=0"))) {
        sendErrorResponse(sessionID, "404 Not found", cseq);
        return ERROR_MALFORMED;
    }

    MtkRTPSender::TransportMode rtcpMode = MtkRTPSender::TRANSPORT_UDP;
    if (clientRtcp < 0) {
        rtcpMode = MtkRTPSender::TRANSPORT_NONE;
    }

    status_t err;
    ALOGI("Init playbackSession");
    ///Configure for test mode
    if((mWfdFlags & kSigmaTest) == kSigmaTest){
        ALOGI("Configure video capability for test bed");

        err = playbackSession->init(
            mClientInfo.mRemoteIP.c_str(),
            clientRtp,
            rtpMode,
            clientRtcp,
            rtcpMode,
            mSinkSupportsAudio,
            mUsingPCMAudio,
            mSinkSupportsVideo,
            MtkVideoFormats::RESOLUTION_CEA,  //640x480, 60fps
            0,
            mChosenVideoProfile,
            mChosenVideoLevel);


        ALOGI("[Workaround] pause playbackSession");

        playbackSession->pause();
        // Notify it's miracast certification mode for dropping dummy nalu
        playbackSession->setMiracastMode(true);
    }else{
        err = playbackSession->init(
            mClientInfo.mRemoteIP.c_str(),
            clientRtp,
            rtpMode,
            clientRtcp,
            rtcpMode,
            mSinkSupportsAudio,
            mUsingPCMAudio,
            mSinkSupportsVideo,
            mChosenVideoResolutionType,
            mChosenVideoResolutionIndex,
            mChosenVideoProfile,
            mChosenVideoLevel);
    }

    //For HE dongle, its RTP port is 1010
    if(clientRtp == 1010 || clientRtp == 24030){
        //ALOGI("Enable slide mode for HE dongle");
        //playbackSession->setSliceMode(1);
    }



    if (err != OK) {
        looper()->unregisterHandler(playbackSession->id());
        playbackSession.clear();
    }

    switch (err) {
        case OK:

            //M: To avoid playbacksessoin init() is interrupted by destroyAsync()
            mPlaybackSessionIniting = true;
            break;
        case -ENOENT:
            sendErrorResponse(sessionID, "404 Not Found", cseq);
            return err;
        default:
            sendErrorResponse(sessionID, "403 Forbidden", cseq);
            return err;
    }

    mClientInfo.mPlaybackSessionID = playbackSessionID;
    mClientInfo.mPlaybackSession = playbackSession;

    AString response = "RTSP/1.0 200 OK\r\n";
    AppendCommonResponse(&response, cseq, playbackSessionID);

    if (rtpMode == MtkRTPSender::TRANSPORT_TCP_INTERLEAVED) {
        response.append(
                AStringPrintf(
                    "Transport: RTP/AVP/TCP;interleaved=%d-%d;",
                    clientRtp, clientRtcp));
    } else {
///M: @{
#if 0
    /* disable RTP port in server side */
        AString transportString = "UDP";
        if (transportMode == Sender::TRANSPORT_TCP) {
            transportString = "TCP";
        }

        if (clientRtcp >= 0) {
            response.append(
                    AStringPrintf(
                        "Transport: RTP/AVP/%s;unicast;client_port=%d-%d;server_port=0-0\r\n",
                        transportString.c_str(),
                        clientRtp, clientRtcp));
        } else {
            response.append(
                    AStringPrintf(
                        "Transport: RTP/AVP/%s;unicast;client_port=%d;server_port=0\r\n",
                        transportString.c_str(),
                        clientRtp));
        }
#endif
///@}

        int32_t serverRtp = playbackSession->getRTPPort();

        ///M: @{ For MET integration
#ifdef DEBUG_BUILD
        char buffer [16];
        snprintf(buffer, sizeof(buffer), "%d", serverRtp);
        ALOGI("serverRtp port=%s", buffer);
        property_set("media.wfd.met_port", buffer);
#endif
        ///@}

        AString transportString = "UDP";
        if (rtpMode == MtkRTPSender::TRANSPORT_TCP) {
            transportString = "TCP";
        }

        if (clientRtcp >= 0) {
            response.append(
                    AStringPrintf(
                        "Transport: RTP/AVP/%s;unicast;client_port=%d-%d;"
                        "server_port=%d-%d\r\n",
                        transportString.c_str(),
                        clientRtp, clientRtcp, serverRtp, serverRtp + 1));
        } else {
            response.append(
                    AStringPrintf(
                        "Transport: RTP/AVP/%s;unicast;client_port=%d;"
                        "server_port=%d\r\n",
                        transportString.c_str(),
                        clientRtp, serverRtp));
        }
    }

    response.append("\r\n");

    err = mNetSession->sendRequest(sessionID, response.c_str());

    if (err != OK) {
        return err;
    }

    mState = AWAITING_CLIENT_PLAY;

    scheduleReaper();
    scheduleKeepAlive(sessionID);

    return OK;
}

status_t MtkWifiDisplaySource::onPlayRequest(
        int32_t sessionID,
        int32_t cseq,
        const sp<ParsedMessage> &data) {
    ///M:
    mPlayRequestReceived = true;
    /// @}

    int32_t playbackSessionID;
    sp<MtkPlaybackSession> playbackSession =
        findPlaybackSession(data, &playbackSessionID);

    if (playbackSession == NULL) {
        sendErrorResponse(sessionID, "454 Session Not Found", cseq);
        return ERROR_MALFORMED;
    }

    ALOGI("Received PLAY request :%d", mState);

///M: Check mState for PLAY request
    if(mState >= AWAITING_CLIENT_TEARDOWN){
       ALOGE("Wrong state for PLAY request");
       return OK;
    }
/// @}
#if 0
    if (mState != AWAITING_CLIENT_PLAY
     && mState != PAUSED_TO_PLAYING
     && mState != PAUSED) {
        ALOGW("Received PLAY request but we're in state %d", mState);

        sendErrorResponse(
                sessionID, "455 Method Not Valid in This State", cseq);

        return INVALID_OPERATION;
    }
#endif

    if (mPlaybackSessionEstablished) {
        finishPlay();
    } else {
        ALOGI("deferring PLAY request until session established.");
    }

    AString response = "RTSP/1.0 200 OK\r\n";
    AppendCommonResponse(&response, cseq, playbackSessionID);
    response.append("Range: npt=now-\r\n");
    response.append("\r\n");

    status_t err = mNetSession->sendRequest(sessionID, response.c_str());

    if (err != OK) {
        return err;
    }


    if(mState == PAUSED_TO_PLAYING || mState == PAUSED
       || mPlaybackSessionEstablished){
      ///For miracast testing issue
      playbackSession->updateLiveness();
      scheduleKeepAlive(sessionID);
      scheduleReaper();

      mState = PLAYING;
      return OK;
    }else if(mState == PLAYING){

        ALOGE("Wrong state for PLAY request:%d", mState);
        sendErrorResponse(sessionID, "406 not acceptable", cseq);
        return OK;

    }

    CHECK_EQ(mState, AWAITING_CLIENT_PLAY);
    mState = ABOUT_TO_PLAY;

    return OK;
}

void MtkWifiDisplaySource::finishPlay() {
    const sp<MtkPlaybackSession> &playbackSession =
        mClientInfo.mPlaybackSession;

    ALOGI("call playbackSession play()");
    status_t err = playbackSession->play();
    CHECK_EQ(err, (status_t)OK);
}

status_t MtkWifiDisplaySource::onPauseRequest(
        int32_t sessionID,
        int32_t cseq,
        const sp<ParsedMessage> &data) {
    int32_t playbackSessionID;
    sp<MtkPlaybackSession> playbackSession =
        findPlaybackSession(data, &playbackSessionID);

    if (playbackSession == NULL) {
        sendErrorResponse(sessionID, "454 Session Not Found", cseq);
        return ERROR_MALFORMED;
    }

    ALOGI("Received PAUSE request.");

    if (mState != PLAYING_TO_PAUSED && mState != PLAYING) {
        return INVALID_OPERATION;
    }

    //if (mState == PAUSED) {
//#ifdef MTK_AOSP_ENHANCEMENT
//        ALOGE("Wrong state for PAUSE request:%d", mState);
//        sendErrorResponse(sessionID, "406 not acceptable", cseq);
//#endif
//        return OK;
//   }

    status_t err = playbackSession->pause();
    CHECK_EQ(err, (status_t)OK);


    AString response = "RTSP/1.0 200 OK\r\n";
    AppendCommonResponse(&response, cseq, playbackSessionID);
    response.append("\r\n");

    err = mNetSession->sendRequest(sessionID, response.c_str());

    if (err != OK) {
        return err;
    }

    mState = PAUSED;

    return err;
}

status_t MtkWifiDisplaySource::onTeardownRequest(
        int32_t sessionID,
        int32_t cseq,
        const sp<ParsedMessage> &data) {
    ALOGI("Received TEARDOWN request:%d", mState);

    int32_t playbackSessionID;
    sp<MtkPlaybackSession> playbackSession =
        findPlaybackSession(data, &playbackSessionID);

    if (playbackSession == NULL) {
        sendErrorResponse(sessionID, "454 Session Not Found", cseq);
        return ERROR_MALFORMED;
    }

    AString response = "RTSP/1.0 200 OK\r\n";
    AppendCommonResponse(&response, cseq, playbackSessionID);
    response.append("Connection: close\r\n");
    response.append("\r\n");

    mNetSession->sendRequest(sessionID, response.c_str());

    ///M: @{ For MET integration
#ifdef DEBUG_BUILD
    property_set("media.wfd.met_port", "-1");
#endif
    ///@}

    if (mState == AWAITING_CLIENT_TEARDOWN) {
        CHECK(mStopReplyID != NULL);
        finishStop();
    } else {
        if(mClient != NULL){
            mClient->onDisplayError(IRemoteDisplayClient::kDisplayErrorUnknown);
        }else{
            stopTestSession();
        }
    }

    return OK;
}

void MtkWifiDisplaySource::finishStop() {
    ALOGI("finishStop");

    mState = STOPPING;

    disconnectClientAsync();
}

void MtkWifiDisplaySource::finishStopAfterDisconnectingClient() {
    ALOGI("finishStopAfterDisconnectingClient");

    if (mHDCP != NULL) {
        ALOGI("Initiating HDCP shutdown.");

        //Deinitialize for Miracast
        ALOGI("Reset mHDCPInitializationComplete.");
        mHDCPInitializationComplete = false;

        mHDCP->shutdownAsync();
        return;
    }

    finishStop2();
}

void MtkWifiDisplaySource::finishStop2() {
    ALOGI("finishStop2");

    if (mHDCP != NULL) {
        mHDCP->setObserver(NULL);
        mHDCPObserver.clear();
        mHDCP.clear();
    }

    ALOGI("We're stopped.");
    mState = STOPPED;

    status_t err = OK;

    if (mTestSessionStopped) {
        ALOGD("in test mode, don't reply any message and stop RTSP server");
        return;
    }

    //This is a server session; Don't stop under test mode
    if (mSessionID != 0) {
        mNetSession->destroySession(mSessionID);
        mSessionID = 0;
    }




    sp<AMessage> response = new AMessage;
    response->setInt32("err", err);
    response->postReply(mStopReplyID);
}

status_t MtkWifiDisplaySource::onGetParameterRequest(
        int32_t sessionID,
        int32_t cseq,
        const sp<ParsedMessage> &data) {
    int32_t playbackSessionID;
    sp<MtkPlaybackSession> playbackSession =
        findPlaybackSession(data, &playbackSessionID);

    if (playbackSession == NULL) {
        sendErrorResponse(sessionID, "454 Session Not Found", cseq);
        return ERROR_MALFORMED;
    }

    playbackSession->updateLiveness();

    AString response = "RTSP/1.0 200 OK\r\n";
    AppendCommonResponse(&response, cseq, playbackSessionID);
    response.append("\r\n");

    status_t err = mNetSession->sendRequest(sessionID, response.c_str());
    return err;
}

status_t MtkWifiDisplaySource::onSetParameterRequest(
        int32_t sessionID,
        int32_t cseq,
        const sp<ParsedMessage> &data) {
    int32_t playbackSessionID;
    sp<MtkPlaybackSession> playbackSession =
        findPlaybackSession(data, &playbackSessionID);

    if (playbackSession == NULL) {
        sendErrorResponse(sessionID, "454 Session Not Found", cseq);

        // To fix Xiaomi Box IOT issue
        // return ERROR_MALFORMED;
        return OK;
    }

    if (strstr(data->getContent(), "wfd_idr_request\r\n")) {
        playbackSession->requestIDRFrame();
    }

    playbackSession->updateLiveness();

    AString response = "RTSP/1.0 200 OK\r\n";
    AppendCommonResponse(&response, cseq, playbackSessionID);
    response.append("\r\n");

    status_t err = mNetSession->sendRequest(sessionID, response.c_str());
    return err;
}

// static
void MtkWifiDisplaySource::AppendCommonResponse(
        AString *response, int32_t cseq, int32_t playbackSessionID) {
    time_t now = time(NULL);
    struct tm *now2 = gmtime(&now);
    char buf[128];
    strftime(buf, sizeof(buf), "%a, %d %b %Y %H:%M:%S %z", now2);

    response->append("Date: ");
    response->append(buf);
    response->append("\r\n");

    response->append(AStringPrintf("Server: %s\r\n", sUserAgent.c_str()));

    if (cseq >= 0) {
        response->append(AStringPrintf("CSeq: %d\r\n", cseq));
    }

    if (playbackSessionID >= 0ll) {
        response->append(
                AStringPrintf(
                    "Session: %d;timeout=%lld\r\n",
                    playbackSessionID, kPlaybackSessionTimeoutSecs));
    }
}

void MtkWifiDisplaySource::sendErrorResponse(
        int32_t sessionID,
        const char *errorDetail,
        int32_t cseq) {
    AString response;
    response.append("RTSP/1.0 ");
    response.append(errorDetail);
    response.append("\r\n");

    AppendCommonResponse(&response, cseq);

    response.append("\r\n");

    mNetSession->sendRequest(sessionID, response.c_str());
}

int32_t MtkWifiDisplaySource::makeUniquePlaybackSessionID() const {
    srand(time(NULL));
    return rand();
}

sp<MtkWifiDisplaySource::MtkPlaybackSession> MtkWifiDisplaySource::findPlaybackSession(
        const sp<ParsedMessage> &data, int32_t *playbackSessionID) const {
    if (!data->findInt32("session", playbackSessionID)) {
        // XXX the older dongles do not always include a "Session:" header.
        *playbackSessionID = mClientInfo.mPlaybackSessionID;
        return mClientInfo.mPlaybackSession;
    }

    if (*playbackSessionID != mClientInfo.mPlaybackSessionID) {
        return NULL;
    }

    return mClientInfo.mPlaybackSession;
}

void MtkWifiDisplaySource::disconnectClientAsync() {
    ALOGI("disconnectClient");

    if (mClientInfo.mPlaybackSession == NULL) {
        disconnectClient2();
        return;
    }

    if (mClientInfo.mPlaybackSession != NULL) {
        //M: To avoid playbacksessoin init() is interrupted by destroyAsync()
        if (mPlaybackSessionIniting) {
            ALOGI("Deferring destroy playbacksession until playbacksession initialization completes.");

            mPlaybackSessionDestroyDeferred = true;
        }
        else {
            ALOGI("Destroying PlaybackSession");
            mClientInfo.mPlaybackSession->destroyAsync();
        }
    }
}

void MtkWifiDisplaySource::disconnectClient2() {
    ALOGI("disconnectClient2");

    if (mClientInfo.mPlaybackSession != NULL) {
        looper()->unregisterHandler(mClientInfo.mPlaybackSession->id());
        mClientInfo.mPlaybackSession.clear();
    }

    if (mClientSessionID != 0) {
        mNetSession->destroySession(mClientSessionID);
        mClientSessionID = 0;
    }

    ///M:@{
    if (mTestClientSessionID > 0) {
        mNetSession->destroySession(mTestClientSessionID);
        mTestClientSessionID = 0;
    }
    ///@}

    if(mClient != NULL){
        mClient->onDisplayDisconnected();
    }

    finishStopAfterDisconnectingClient();
}

struct MtkWifiDisplaySource::HDCPObserver : public BnHDCPObserver {
    HDCPObserver(const sp<AMessage> &notify);

    virtual void notify(
            int msg, int ext1, int ext2, const Parcel *obj);

private:
    sp<AMessage> mNotify;

    DISALLOW_EVIL_CONSTRUCTORS(HDCPObserver);
};

MtkWifiDisplaySource::HDCPObserver::HDCPObserver(
        const sp<AMessage> &notify)
    : mNotify(notify) {
}

void MtkWifiDisplaySource::HDCPObserver::notify(
        int msg, int ext1, int ext2, const Parcel * /* obj */) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("msg", msg);
    notify->setInt32("ext1", ext1);
    notify->setInt32("ext2", ext2);
    notify->post();
}

status_t MtkWifiDisplaySource::makeHDCP() {
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder = sm->getService(String16("media.player"));

    sp<IMediaPlayerService> service =
        interface_cast<IMediaPlayerService>(binder);

    CHECK(service != NULL);

    mHDCP = service->makeHDCP(true /* createEncryptionModule */);

    if (mHDCP == NULL) {
        return ERROR_UNSUPPORTED;
    }

    sp<AMessage> notify = new AMessage(kWhatHDCPNotify, this);
    mHDCPObserver = new HDCPObserver(notify);

    status_t err = mHDCP->setObserver(mHDCPObserver);

    if (err != OK) {
        ALOGE("Failed to set HDCP observer.");

        mHDCPObserver.clear();
        mHDCP.clear();

        return err;
    }

    ALOGI("Initiating HDCP negotiation w/ host %s:%d",
            mClientInfo.mRemoteIP.c_str(), mHDCPPort);

    err = mHDCP->initAsync(mClientInfo.mRemoteIP.c_str(), mHDCPPort);

    if (err != OK) {
        return err;
    }

    return OK;
}



/// M: Add by MTK @{
status_t MtkWifiDisplaySource::sendGenericMsgByMethod(int32_t methodID) {
    AString method;

    if(mClientSessionID <= 0){
        ALOGE("No active client session for WFD client");
        return 0;
    }

    switch(methodID){
        case TRIGGER_PLAY:
            mState = PAUSED_TO_PLAYING;
            method = "PLAY\r\n";
            break;
        case TRIGGER_PAUSE:
            mState = PLAYING_TO_PAUSED;
            method = "PAUSE\r\n";
            break;
        case TRIGGER_TEARDOWN:
            method = "TEARDOWN\r\n";
            break;
        default:
            ALOGE("Unknown methodID:%d", methodID);
            return BAD_VALUE;
            break;
    }

    AString body = "wfd_trigger_method: ";
    body.append(method);

    AString request = "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\n";
    AppendCommonResponse(&request, mNextCSeq);

    request.append("Content-Type: text/parameters\r\n");
    request.append(AStringPrintf("Content-Length: %d\r\n", body.size()));
    request.append("\r\n");
    request.append(body);

    status_t err =
        mNetSession->sendRequest(mClientSessionID, request.c_str(), request.size());

    if (err != OK) {
        return err;
    }
    ALOGI("mNextCSeq=%d method =%d",mNextCSeq,methodID);

    registerResponseHandler(
            mClientSessionID, mNextCSeq, &MtkWifiDisplaySource::onReceiveGenericResponse);

    ++mNextCSeq;

    return OK;
}

status_t MtkWifiDisplaySource::onReceiveGenericResponse(
        int32_t sessionID, const sp<ParsedMessage> &msg) {
    int32_t statusCode;

    CHECK_EQ(sessionID, mClientSessionID);

    if (!msg->getStatusCode(&statusCode)) {
        return ERROR_MALFORMED;
    }

    if (statusCode != 200) {
        return ERROR_UNSUPPORTED;
    }

    return OK;
}

void MtkWifiDisplaySource::onReceiveTestData(const sp<AMessage> &msg) {
    status_t err = OK;
    int32_t sessionID;
    AString response = "";
    CHECK(msg->findInt32("sessionID", &sessionID));

    AString data;
    CHECK(msg->findString("data", &data));

    mWfdFlags |= kSigmaTest;

    ALOGI("test session %d received '%s'",
          sessionID, data.c_str());

    if(data.startsWith("reset") || data.startsWith("wfd_cmd wfd_reset")){
        resetRtspClient();
        response = "OK,\r\n";
    }else if(data.startsWith("rtsp_get sessionids")){
        if(mClientInfo.mPlaybackSessionID > 0){
            response = AStringPrintf("OK,%d,0\r\n", mClientInfo.mPlaybackSessionID);
        }else{
            response = "FAIL,0,0\r\n";
        }
    }else if(data.startsWith("rtsp_trigger PLAY")){
        err = sendGenericMsgByMethod(TRIGGER_PLAY);
        response = "OK,PLAY\r\n";
    }else if(data.startsWith("rtsp_trigger PAUSE")){
        err = sendGenericMsgByMethod(TRIGGER_PAUSE);
        response = "OK,PAUSE\r\n";
    }else if(data.startsWith("rtsp_trigger TEARDOWN")){
        err = sendGenericMsgByMethod(TRIGGER_TEARDOWN);
        response = "OK,TEARDOWN\r\n";
    }else if(data.startsWith("rtsp_set")){
        response = "OK,\r\n";
    }
    // For HDCP
     else if(data.startsWith("preset wfdHdcp")){
        response = "OK,\r\n";
    }else if(data.startsWith("preset disableOption")){
        response = "OK,\r\n";
    }else if(data.startsWith("preset inputContentType 1") ||
             data.startsWith("preset inputContentType 3")){
        response = "OK,\r\n";
        //Do nothing for PRESET
    }else if(data.startsWith("preset inputContentType 2")){
        response = "OK,\r\n";
        //Do nothing for PRESET
    }else if(data.startsWith("event inputContentType 1") ||
             data.startsWith("event inputContentType 3")){
        response = "OK,\r\n";
        ALOGI("mUsingHDCP:%d", mUsingHDCP);
        if (!mUsingHDCP){
            if (mClientInfo.mPlaybackSession != NULL) {

                ALOGI("call forceBlackScreen(%d)", 1);
                mClientInfo.mPlaybackSession->forceBlackScreen(true);

            }
        }
    }else if(data.startsWith("event inputContentType 2")){
        response = "OK,\r\n";
        ALOGI("mUsingHDCP:%d", mUsingHDCP);
        if (!mUsingHDCP){
            if (mClientInfo.mPlaybackSession != NULL) {
            #ifdef MTK_AOSP_ENHANCEMENT
                ALOGI("call forceBlackScreen(%d)", 0);
                mClientInfo.mPlaybackSession->forceBlackScreen(false);
            #endif
            }
        }
    }
    else {
        ALOGD("No match test command");
    }

    if(response.size() > 0){
        ALOGD("test response:[%s]", response.c_str());
        mNetSession->sendDirectRequest(sessionID, response.c_str(), response.size());
    }
}

status_t MtkWifiDisplaySource::sendM14(int32_t sessionID) {
    ALOGD("unused sessionID %d",sessionID);
    return OK;
}

status_t MtkWifiDisplaySource::onReceiveM14Response(
        int32_t /*sessionID*/, const sp<ParsedMessage> &msg) {
    int32_t statusCode;
    if (!msg->getStatusCode(&statusCode)) {
        return ERROR_MALFORMED;
    }

    if (statusCode != 200) {
        return ERROR_UNSUPPORTED;
    }

    return OK;
}

void MtkWifiDisplaySource::resetRtspClient() {
    ALOGI("resetRtspClient()");

    if (mClientSessionID != 0) {
        if (mClientInfo.mPlaybackSession != NULL) {
            looper()->unregisterHandler(mClientInfo.mPlaybackSession->id());
            ALOGE("mPlaybackSession clear");
            mClientInfo.mPlaybackSession->destroyAsync();
            mClientInfo.mPlaybackSession.clear();
        }
        mNetSession->destroySession(mClientSessionID);
        mClientSessionID = 0;
    }

    mClientInfo.mPlaybackSessionID = -1;
    mTestSessionStopped = false;

    //Sync with incoming TCP connetion checking
    mState = AWAITING_CLIENT_CONNECTION;
    if(mSessionID == 0){
        sp<AMessage> notify = new AMessage(kWhatRTSPNotify, this);
        ALOGD("Create RTSP server for WFD Sigma");
        mNetSession->createRTSPServer(mInterfaceAddr, kWifiDisplayDefaultPort, notify, &mSessionID);
    }

    setAudioPath(true);

    //Deinitialize for Miracast
    ALOGI("Reset mHDCPInitializationComplete.");
    mHDCPInitializationComplete = false;
}

void MtkWifiDisplaySource::startRtpClient(const char* /*remoteIP*/, int32_t /*clientRtp*/) {
#if 0
    int32_t playbackSessionID = makeUniquePlaybackSessionID();

    sp<AMessage> notify = new AMessage(kWhatRtpNotify, this);
    notify->setInt32("playbackSessionID", playbackSessionID);
    notify->setInt32("sessionID", 0);

    sp<PlaybackSession> playbackSession =
        new PlaybackSession(
                mNetSession, notify, mInterfaceAddr, mHDCP);

    looper()->registerHandler(playbackSession);

    ALOGI("Run startRtpClient");

    status_t err = playbackSession->init(
            remoteIP,
            clientRtp,
            -1,
            RTPSender::TRANSPORT_UDP,
            true); //Use PCM

    if (err != OK) {
        looper()->unregisterHandler(playbackSession->id());
        playbackSession.clear();
    }

    mClientInfo.mPlaybackSessionID = playbackSessionID;
    mClientInfo.mPlaybackSession = playbackSession;

    ALOGI("Prepare to Play");

    err = playbackSession->play();
    CHECK_EQ(err, (status_t)OK);

    playbackSession->finishPlay();
    CHECK_EQ(err, (status_t)OK);
#endif
}

///M: add for rtsp generic message @{
status_t MtkWifiDisplaySource::sendGenericMsg(int cmd) {
    ALOGI("MtkWifiDisplaySource sendGenericMsg");

    if(mClientSessionID == 0){
        ALOGE("No Client WFD session");
        return OK;
    }

    if(mSessionID == 0){
        ALOGE("No WFD session");
        return OK;
    }

    sp<AMessage> msg = new AMessage(kWhatSendGenericMsg, this);
    msg->setInt32("cmd",cmd);
    msg->post();

    return OK;
}

status_t MtkWifiDisplaySource::setBitrateControl(int bitrate) {

    mClientInfo.mPlaybackSession->setBitrateControl(bitrate);

    return OK;
}

int MtkWifiDisplaySource::getWfdParam(int paramType) {
    int paramValue = 0;

    // 0: expected bit rate, 1: current bit rate, 2: fluency rate
    if (paramType == 0 || paramType == 1 || paramType == 2)
    {
        // Remove and use AOSP bitrate control mechanism instead
        paramValue = 0;
    }
    // 3: if audio format is LPCM
    else if (paramType == 3) {
        paramValue = mUsingPCMAudio ? 1: 0;
    }
    // 4: if video profile is CBP
    else if (paramType == 4) {
        paramValue = (mChosenVideoProfile == MtkVideoFormats::PROFILE_CBP) ? 1 : 0;
    }

    // 5: average latency, 6: sink fps
    else if (paramType == 5 || paramType == 6) {
        sp<WfdDebugInfo> debugInfo= defaultWfdDebugInfo();
        int64_t value = 0, dummy;
    int32_t fps;

        if (paramType == 5){
            debugInfo->getStatistics(1, &value, &dummy, &fps, &dummy);
            paramValue = (int) value;
        }else if (paramType == 6){
            debugInfo->getStatistics(1, &dummy, &dummy, &fps ,&dummy);
            paramValue = (int) fps;
        }
    }

    else if (paramType == 7) {
        paramValue = mUsingHDCP ? 1 : 0;
    }
    return paramValue;
}

void MtkWifiDisplaySource::setAudioPath(bool on) {
    ALOGI("setAudioPath:%d", on);
    if (on) {
        AudioSystem::setDeviceConnectionState(
            AUDIO_DEVICE_OUT_REMOTE_SUBMIX, AUDIO_POLICY_DEVICE_STATE_AVAILABLE, 0, NULL);
        AudioSystem::setDeviceConnectionState(
            AUDIO_DEVICE_IN_REMOTE_SUBMIX, AUDIO_POLICY_DEVICE_STATE_AVAILABLE, 0, NULL);
    }else{
        AudioSystem::setDeviceConnectionState(
            AUDIO_DEVICE_OUT_REMOTE_SUBMIX, AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE, 0, NULL);
        AudioSystem::setDeviceConnectionState(
            AUDIO_DEVICE_IN_REMOTE_SUBMIX, AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE, 0, NULL);
    }
}

void MtkWifiDisplaySource::stopTestSession() {
     ALOGI("stop RTSP session for test mode: %d", mState);

     if(mState != STOPPING){
       mTestSessionStopped = true;
       finishStop();
     }
     setAudioPath(false);
}



void MtkWifiDisplaySource::notifyThermal(bool start){
    FILE *fp = fopen("/proc/driver/thermal/clwmt_wfdstat", "w");
    if(fp != NULL)
    {
        if (start) {
            fprintf(fp, "2");
        } else {
            fprintf(fp, "0");
        }

        fclose(fp);
    }
    else
    {
        ALOGI("Error: Open clwmt_wfdstat fail, error: %d", errno);
    }

    // Notify thermal to apply WFD policy: 52 degrees
    ALOGI("Set thermal policy: %d", start);

    void *handle = dlopen("/system/vendor/lib/libmtcloader.so", RTLD_NOW);
    if (NULL == handle) {
        ALOGI("%s, can't load thermal library: %s", __FUNCTION__, dlerror());
        return;
    }

    void *func = dlsym(handle, "change_policy");

    if (func != NULL) {
        typedef int (*load_change_policy)(char *, int);
        char file_path[] = "thermal_policy_00";

        load_change_policy change_policy =
            reinterpret_cast<load_change_policy>(func);

        if (change_policy != NULL) {
            if (start) {
                change_policy(file_path, 1);
            } else {
                change_policy(file_path, 0);
            }
        } else {
            ALOGI("reinterpret_cast: change_policy fail");
        }
    } else {
        ALOGI("dlsym: change_policy fail");
    }

    dlclose(handle);
}



void MtkWifiDisplaySource::notifyGPUDriver(bool start) {
    FILE *fp= fopen("/d/ged/hal/media_event", "w");
    if(fp != NULL) {
        if (start) {
            fprintf(fp, "enable_WFD 1");
        } else {
            fprintf(fp, "enable_WFD 0");
        }
        fclose(fp);
    } else {
        ALOGI("Error: Open /d/ged/hal/media_event fail, error: %d", errno);
    }
    // Notify GPU Driver
    ALOGI("Notify GPU Driver: %d", start);
}
///@}


/// M: @}
}  // namespace android
