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

#include <stdlib.h>
#include <string.h>

#include <seos.h>
#include <timer.h>
#include <syscallDo.h>

static uint32_t mMyTid;
static int cnt;

struct ExtMsg
{
    struct HostHubRawPacket hdr;
    uint8_t msg;
    uint32_t val;
} __attribute__((packed));

static bool start_task(uint32_t myTid)
{
    mMyTid = myTid;
    cnt = 100;

    return eOsEventSubscribe(myTid, EVT_APP_START);
}

static void end_task(void)
{
    eOsLog(LOG_DEBUG, "App 0 terminating");
}

static void handle_event(uint32_t evtType, const void* evtData)
{
    const struct TimerEvent *te;
    const struct AppEventFreeData *aefd;
    uint32_t timerId;
    struct ExtMsg *extMsg;

    if (evtType == EVT_APP_START) {
        timerId = eOsTimTimerSet(1000000000ULL, 50, 50, mMyTid, (void *)&cnt, false);
        eOsLog(LOG_INFO, "App 0 started with tid %u timerid %u\n", mMyTid, timerId);
    } else if (evtType == EVT_APP_TIMER) {
        te = evtData;
        eOsLog(LOG_INFO, "App 0 received timer %u callback: %d (TIM: %lld, RTC: %lld, SENSOR: %lld, HOST: %lld)\n", te->timerId, *(int *)te->data, eOsTimGetTime(), eOsRtcGetTime(), eOsSensorGetTime(), eOsHostGetTime());
        extMsg = eOsHeapAlloc(sizeof(*extMsg));
        extMsg->hdr.appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0x548000);
        extMsg->hdr.dataLen = 5;
        extMsg->msg = 0x01;
        extMsg->val = *(int *)te->data;
        if (!(eOsEnqueueEvt(EVT_APP_TO_HOST, extMsg, mMyTid)))
            eOsHeapFree(extMsg);
        if (cnt-- <= 0)
            eOsTimTimerCancel(te->timerId);
    } else if (evtType == EVT_APP_FREE_EVT_DATA) {
        aefd = evtData;
        if (aefd->evtType == EVT_APP_TO_HOST)
            eOsHeapFree(aefd->evtData);
    }
}

APP_INIT(0, start_task, end_task, handle_event);
