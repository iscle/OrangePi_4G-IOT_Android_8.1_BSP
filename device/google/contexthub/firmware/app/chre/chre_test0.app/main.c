/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <stdint.h>
#include <inttypes.h>
#include <chre.h>

#define CHRE_APP_TAG "CHRE App 0: "

/* chre.h does not define printf format attribute for chreLog() */
void chreLog(enum chreLogLevel level, const char *str, ...) __attribute__ ((__format__ (__printf__, 2, 3)));

#define EVT_LOCAL_SETUP CHRE_EVENT_FIRST_USER_VALUE

struct MyTimer {
    uint32_t timerId;
};

struct ExtMsg
{
    uint8_t msg;
    uint32_t val;
} __attribute__((packed));

static const uint64_t kOneSecond = UINT64_C(1000000000); // in nanoseconds

static uint32_t mMyTid;
static uint64_t mMyAppId;
static int cnt;
static struct MyTimer mTimer;

static void nanoappFreeEvent(uint16_t eventType, void *data)
{
    chreLog(CHRE_LOG_INFO, CHRE_APP_TAG "event callback invoked: eventType=%04" PRIX16
            "; data=%p\n", eventType, data);
}

// Default implementation for message free
static void nanoappFreeMessage(void *msg, size_t size)
{
    chreLog(CHRE_LOG_INFO, CHRE_APP_TAG "message callback invoked: msg=%p; size=%08zu\n",
            msg, size);
    chreHeapFree(msg);
}

bool nanoappStart(void)
{
    mMyAppId = chreGetAppId();
    mMyTid = chreGetInstanceId();
    cnt = 3;
    chreSendEvent(EVT_LOCAL_SETUP, (void*)0x87654321, nanoappFreeEvent, mMyTid);
    chreLog(CHRE_LOG_INFO, CHRE_APP_TAG "init\n");
    return true;
}

void nanoappEnd(void)
{
}

void nanoappHandleEvent(uint32_t srcTid, uint16_t evtType, const void* evtData)
{
    switch (evtType) {
    case  EVT_LOCAL_SETUP:
        mTimer.timerId = chreTimerSet(kOneSecond, &mTimer, false);
        chreLog(CHRE_LOG_INFO, CHRE_APP_TAG "started with tid %04" PRIX32
                               " timerid %" PRIu32
                               "\n", mMyTid, mTimer.timerId);
        break;
    case CHRE_EVENT_TIMER:
    {
        const struct MyTimer *t = (const struct MyTimer *)evtData;
        struct ExtMsg *extMsg = chreHeapAlloc(sizeof(*extMsg));

        chreLog(CHRE_LOG_INFO, CHRE_APP_TAG "received timer %" PRIu32
                               " (TIME: %" PRIu64
                               ") cnt: %d\n", t->timerId, chreGetTime(), cnt);
        extMsg->msg = 0x01;
        extMsg->val = cnt;
        chreSendMessageToHost(extMsg, sizeof(*extMsg), 0, nanoappFreeMessage);
        if (cnt-- <= 0)
            chreTimerCancel(t->timerId);
        break;
    }
    case CHRE_EVENT_MESSAGE_FROM_HOST:
    {
        const struct chreMessageFromHostData *msg = (const struct chreMessageFromHostData *)evtData;
        const uint8_t *data = (const uint8_t *)msg->message;
        const size_t size = msg->messageSize;
        chreLog(CHRE_LOG_INFO, CHRE_APP_TAG "message=%p; code=%d; size=%zu\n",
                data, (data && size) ? data[0] : 0, size);
        break;
    }
    }
}
