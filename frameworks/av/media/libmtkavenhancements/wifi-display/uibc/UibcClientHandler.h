
#ifndef UIBC_CLIENT_HANDLER_H
#define UIBC_CLIENT_HANDLER_H

#include "UibcHandler.h"
#include "UibcMessage.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AHandler.h>
#include "MtkANetworkSession.h"

#include <utils/RefBase.h>
#include <utils/Thread.h>

#include <linux/input.h>
#include "UibcMessage.h"

namespace android {

struct UibcClientHandler : public RefBase, public UibcHandler  {
    UibcClientHandler(   sp<MtkANetworkSession> netSession);
    status_t init(bool testMode);
    status_t destroy();

    void setSessionID(int32_t SessionID);
    int32_t getSessionID();

    status_t sendUibcMessage(sp<MtkANetworkSession> netSession,
                             UibcMessage::MessageType type,
                             const char *eventDesc) ;

    status_t startListenHidc();
    status_t stopListenHidc();
    static int isListenHidcStop();

protected:
    virtual ~UibcClientHandler();

private:
    int32_t mSessionID;

    static int mStopListenHidc;
    static pthread_mutex_t mStopHidcMutex;
    sp<MtkANetworkSession> mNetSession;

};

}

#endif
