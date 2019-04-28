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

#ifndef MTK_A_NETWORK_SESSION_H_

#define MTK_A_NETWORK_SESSION_H_

#include <media/stagefright/foundation/ABase.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/Thread.h>

#include <netinet/in.h>
#include <utils/List.h>
#include <media/stagefright/foundation/ABuffer.h>

namespace android {

struct AMessage;

// Helper class to manage a number of live sockets (datagram and stream-based)
// on a single thread. Clients are notified about activity through AMessages.
struct MtkANetworkSession : public RefBase {
    MtkANetworkSession();
    status_t start();
    status_t stop();

    status_t createRTSPClient(
            const char *host, unsigned port, const sp<AMessage> &notify,
            int32_t *sessionID);

    status_t createRTSPServer(
            const struct in_addr &addr, unsigned port,
            const sp<AMessage> &notify, int32_t *sessionID);

    status_t createUDPSession(
            unsigned localPort,
            const sp<AMessage> &notify,
            int32_t *sessionID);

    status_t createUDPSession(
            unsigned localPort,
            const char *remoteHost,
            unsigned remotePort,
            const sp<AMessage> &notify,
            int32_t *sessionID);

    status_t connectUDPSession(
            int32_t sessionID, const char *remoteHost, unsigned remotePort);

    // passive
    status_t createTCPDatagramSession(
            const struct in_addr &addr, unsigned port,
            const sp<AMessage> &notify, int32_t *sessionID);

    // active
    status_t createTCPDatagramSession(
            unsigned localPort,
            const char *remoteHost,
            unsigned remotePort,
            const sp<AMessage> &notify,
            int32_t *sessionID);

    status_t destroySession(int32_t sessionID);

    int getSessionCount();

    status_t sendRequest(
            int32_t sessionID, const void *data, ssize_t size = -1,
            bool timeValid = false, int64_t timeUs = -1ll);

    ///Add by MTK @{
    status_t createTCPTextDataSession(
            const struct in_addr &addr, unsigned port,
            const sp<AMessage> &notify, int32_t *sessionID);

    status_t createUIBCClient(
            const char *host, unsigned port, const sp<AMessage> &notify,
            int32_t *sessionID);

    status_t createUIBCServer(
            const struct in_addr &addr, unsigned port,
            const sp<AMessage> &notify, int32_t *sessionID);

    status_t sendDirectRequest(
            int32_t sessionID, const void *data, ssize_t size);

    status_t createTCPBinaryDataSessionActive(
            unsigned localPort,
            const char *remoteHost,
            unsigned remotePort,
            const sp<AMessage> &notify,
            int32_t *sessionID);

    status_t switchToWebSocketMode(int32_t sessionID);
    ///M: Add for RTP data control @{
    status_t mtkRTPRecvPause(int32_t sessionID);
    status_t mtkRTPRecvResume(int32_t sessionID);
    status_t setNetworkSessionTestMode();
    int64_t getRTPRecvNum(int32_t sessionID);
    status_t resetRTPRecvNum(int32_t sessionID);
    ///@}

    enum NotificationReason {
        kWhatError,
        kWhatConnected,
        kWhatClientConnected,
        kWhatData,
        kWhatDatagram,
        kWhatBinaryData,
        kWhatWebSocketMessage,
        kWhatNetworkStall,
        kWhatTextData,
        kWhatUibcData
    };

protected:
    virtual ~MtkANetworkSession();

private:
    struct NetworkThread;
    struct Session;

    Mutex mLock;
    sp<Thread> mThread;

    int32_t mNextSessionID;

    int mPipeFd[2];

    KeyedVector<int32_t, sp<Session> > mSessions;

    enum Mode {
        kModeCreateUDPSession,
        kModeCreateTCPDatagramSessionPassive,           // RTP over TCP
        kModeCreateTCPDatagramSessionActive,            // RTP over TCP
        kModeCreateRTSPServer,
        kModeCreateRTSPClient,
        ///Add by MTK @{
        kModeCreateTCPTextDataSessionPassive,
        kModeCreateUIBCServer,
        kModeCreateUIBCClient,
        kModeCreateTCPBinaryDataSessionActive,          // TCP binary data
        /// @}
    };
    status_t createClientOrServer(
            Mode mode,
            const struct in_addr *addr,
            unsigned port,
            const char *remoteHost,
            unsigned remotePort,
            const sp<AMessage> &notify,
            int32_t *sessionID);

    void threadLoop();
    void interrupt();
    bool mTestMode;
    static status_t MakeSocketNonBlocking(int s);
    static status_t MakeSocketBlocking(int s);

    int findListeningSock(int port);

    DISALLOW_EVIL_CONSTRUCTORS(MtkANetworkSession);

public:
    status_t sendWFDRequest(
            int32_t sessionID, List<sp<ABuffer> > &packets, int64_t timeUs = -1ll);


};

}  // namespace android

#endif  // A_NETWORK_SESSION_H_
