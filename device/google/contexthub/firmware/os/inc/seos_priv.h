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

#ifndef __SEOS_PRIV_H__
#define __SEOS_PRIV_H__

#include <inttypes.h>
#include <seos.h>

#define NO_NODE (TaskIndex)(-1)
#define for_each_task(listHead, task) for (task = osTaskByIdx((listHead)->next); task; task = osTaskByIdx(task->list.next))

#define TID_TO_TASK_IDX(tid) (tid & TASK_TID_IDX_MASK)

#define FL_TASK_STOPPED 1

#define EVT_SUBSCRIBE_TO_EVT         0x00000000
#define EVT_UNSUBSCRIBE_TO_EVT       0x00000001
#define EVT_DEFERRED_CALLBACK        0x00000002
#define EVT_PRIVATE_EVT              0x00000003

#define EVT_PRIVATE_CLASS_CHRE       0x00000001

#define EVENT_WITH_ORIGIN(evt, origin)       (((evt) & EVT_MASK) | ((origin) << (32 - TASK_TID_BITS)))
#define EVENT_GET_ORIGIN(evt) ((evt) >> (32 - TASK_TID_BITS))
#define EVENT_GET_EVENT(evt) ((evt) & (EVT_MASK & ~EVENT_TYPE_BIT_DISCARDABLE))

#define MAX_EVT_SUB_CNT              6

SET_PACKED_STRUCT_MODE_ON
struct TaskList {
    TaskIndex prev;
    TaskIndex next;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

struct Task {
    /* App entry points */
    const struct AppHdr *app;

    /* per-platform app info */
    struct PlatAppInfo platInfo;

    /* for some basic number of subbed events, the array is stored directly here. after that, a heap chunk is used */
    uint32_t subbedEventsInt[MAX_EMBEDDED_EVT_SUBS];
    uint32_t *subbedEvents; /* NULL for invalid tasks */

    struct TaskList list;

    /* task pointer will not change throughout task lifetime,
     * however same task pointer may be reused for a new task; to eliminate the ambiguity,
     * TID is maintained for each task such that new tasks will be guaranteed to receive different TID */
    uint16_t tid;

    uint8_t  subbedEvtCount;
    uint8_t  subbedEvtListSz;
    uint8_t  flags;
    uint8_t  ioCount;

};

struct I2cEventData {
    void *cookie;
    uint32_t tx;
    uint32_t rx;
    int err;
};

union OsApiSlabItem {
    struct I2cEventData i2cAppCbkEvt;
    struct {
        uint32_t toTid;
        void *cookie;
    } i2cAppCbkInfo;
};

/* this is a system slab allocator internal data type */
union SeosInternalSlabData {
    struct {
        uint16_t tid;
        uint8_t numEvts;
        uint16_t evts[MAX_EVT_SUB_CNT];
    } evtSub;
    struct {
        OsDeferCbkF callback;
        void *cookie;
    } deferred;
    struct {
        uint32_t evtType;
        void *evtData;
        TaggedPtr evtFreeInfo;
        uint16_t fromTid;
        uint16_t toTid;
    } privateEvt;
    union OsApiSlabItem osApiItem;
};

uint8_t osTaskIndex(struct Task *task);
struct Task *osGetCurrentTask();
struct Task *osSetCurrentTask(struct Task *task);
struct Task *osTaskFindByTid(uint32_t tid);
void osTaskAbort(struct Task *task);
void osTaskInvokeMessageFreeCallback(struct Task *task, void (*freeCallback)(void *, size_t), void *message, uint32_t messageSize);
void osTaskInvokeEventFreeCallback(struct Task *task, void (*freeCallback)(uint16_t, void *), uint16_t event, void *data);
void osChreTaskHandle(struct Task *task, uint32_t evtType, const void *evtData);

static inline bool osTaskIsChre(const struct Task *task)
{
    return task->app && (task->app->hdr.fwFlags & FL_APP_HDR_CHRE) != 0;
}

static inline void osTaskMakeNewTid(struct Task *task)
{
    task->tid = ((task->tid + TASK_TID_INCREMENT) & TASK_TID_COUNTER_MASK) |
                (osTaskIndex(task) & TASK_TID_IDX_MASK);
}

#endif // __SEOS_PRIV_H__
