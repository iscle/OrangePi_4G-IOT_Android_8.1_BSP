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

#ifndef _SEOS_H_
#define _SEOS_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <plat/taggedPtr.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdarg.h>
#include <stddef.h>
#include <eventQ.h>
#include <plat/app.h>
#include <eventnums.h>
#include <variant/variant.h>
#include "toolchain.h"

#include <nanohub/nanohub.h>

//#define SEGMENT_CRC_SUPPORT

#ifndef MAX_TASKS
/* Default to 16 tasks, override may come from variant.h */
#define MAX_TASKS                        16
#endif

#define MAX_EMBEDDED_EVT_SUBS             6 /* tradeoff, no wrong answer */
#define TASK_IDX_BITS                     8 /* should be big enough to hold MAX_TASKS, but still fit in TaskIndex */

typedef uint8_t TaskIndex;

struct AppFuncs { /* do not rearrange */
    /* lifescycle */
    bool (*init)(uint32_t yourTid);   //simple init only - no ints on at this time
    void (*end)(void);                //die quickly please
    /* events */
    void (*handle)(uint32_t evtType, const void* evtData);
};

/* NOTE: [TASK ID]
 * TID is designed to be 16-bit; there is no reason for TID to become bigger than that on a system
 * with typical RAM size of 64kB. However, in NO CASE TID values should overlap with TaggedPtr TAG mask,
 * which is currently defined as 0x80000000.
 */

#define TASK_TID_BITS 16

#define TASK_TID_MASK ((1 << TASK_TID_BITS) - 1)
#define TASK_TID_INCREMENT (1 << TASK_IDX_BITS)
#define TASK_TID_IDX_MASK ((1 << TASK_IDX_BITS) - 1)
#define TASK_TID_COUNTER_MASK ((1 << TASK_TID_BITS) - TASK_TID_INCREMENT)

#if MAX_TASKS > TASK_TID_IDX_MASK
#error MAX_TASKS does not fit in TASK_TID_BITS
#endif

#define OS_SYSTEM_TID                    0
#define OS_VER                           0x0000

// FIXME: compatibility: keep key ID 1 until key update is functional
//#define ENCR_KEY_GOOGLE_PREPOPULATED     0x041F010000000001
#define ENCR_KEY_GOOGLE_PREPOPULATED     1 // our key ID is 1

#define APP_HDR_MAGIC              NANOAPP_FW_MAGIC
#define APP_HDR_VER_CUR            1

#define FL_APP_HDR_INTERNAL        0x0001 // to be able to fork behavior at run time for internal apps
#define FL_APP_HDR_APPLICATION     0x0002 // image has AppHdr; otherwise is has AppInfo header
#define FL_APP_HDR_SECURE          0x0004 // secure content, needs to be zero-filled when discarded
#define FL_APP_HDR_VOLATILE        0x0008 // volatile content, segment shall be deleted after operation is complete
#define FL_APP_HDR_CHRE            0x0010 // app is CHRE API compatible
#define FL_KEY_HDR_DELETE          0x8000 // key-specific flag: if set key id refers to existing key which has to be deleted

/* app ids are split into vendor and app parts. vendor parts are assigned by google. App parts are free for each vendor to assign at will */
#define KEY_ID_MAKE(vendor, key)   ((((uint64_t)(vendor)) << 24) | ((key) & KEY_SEQ_ID_ANY))
#define HW_ID_MAKE(vendor, ver)    ((((uint64_t)(vendor)) << 24) | (PLATFORM_ID(ver) & HW_ID_ANY))
#define KEY_SEQ_ID_ANY             UINT64_C(0xFFFFFF)
#define HW_ID_ANY                  UINT64_C(0xFFFFFF)
#define PLATFORM_ID(ver)           ((((PLATFORM_HW_TYPE) & 0xFFFF) << 8) | (ver & 0xFF))

#define APP_INFO_CMD_ADD_KEY 1
#define APP_INFO_CMD_REMOVE_KEY 2
#define APP_INFO_CMD_OS_UPDATE 3

#define SEG_STATE_INVALID UINT32_C(0xFFFFFFFF)
#define SEG_SIZE_MAX      UINT32_C(0x00FFFFFF)
#define SEG_SIZE_INVALID  (-1)
#define SEG_ST(arg) (((arg) << 4) | (arg))

#define SEG_ID_EMPTY    0xF
#define SEG_ID_RESERVED 0x7 // upload in progress
#define SEG_ID_VALID    0x3 // CRC-32 valid
#define SEG_ID_ERASED   0x0 // segment erased

#define SEG_ST_EMPTY    SEG_ST(SEG_ID_EMPTY)
#define SEG_ST_RESERVED SEG_ST(SEG_ID_RESERVED)
#define SEG_ST_VALID    SEG_ST(SEG_ID_VALID)
#define SEG_ST_ERASED   SEG_ST(SEG_ID_ERASED)

struct Segment {
    uint8_t  state;   // 0xFF: empty; bit7=0: segment present; bit6=0: size valid; bit5=0: CRC-32 valid; bit4=0:segment erased;
                      // bits 3-0 replicate bits7-4;
    uint8_t  size[3]; // actual stored size in flash, initially filled with 0xFF
                      // updated after flash operation is completed (successfully or not)
};

struct AppEventFreeData { //goes with EVT_APP_FREE_EVT_DATA
    uint32_t evtType;
    void* evtData;
};

typedef void (*OsDeferCbkF)(void *);

typedef void (*EventFreeF)(void* event);

SET_PACKED_STRUCT_MODE_ON
struct SeosEedataEncrKeyData {
    uint64_t keyID;
    uint8_t key[32];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

/* ==== ABOUT THE "urgent" FLAG ====
 *
 * Do not set "urgent" unless you understand all the repercussions! What repercussions you might ask?
 * Setting this flag will place your defer request at the front of the queue. This is useful for enqueueing work
 * from interrupt context that needs to be done "very very soon"(tm). Doing this will delay all other work requests
 * that have heretofore been peacefully queueing in full faith and with complete belief in fairness of our "FIFO"-ness.
 * Please be appreciative of this fact and do not abuse this! Example: if you are setting "urgent" flag outside of interrupt
 * context, you're very very likely wrong. That is not to say that being in interrupt context is a free pass to set this!
 */

// osMainInit is exposed for testing only, it must never be called for any reason at all by anyone
void osMainInit(void);
// osMainDequeueLoop is exposed for testing only, it must never be called for any reason at all by anyone
void osMainDequeueLoop(void);
void osMain(void);

bool osEventSubscribe(uint32_t tid, uint32_t evtType); /* async */
bool osEventUnsubscribe(uint32_t tid, uint32_t evtType);  /* async */
bool osEventsSubscribe(uint32_t numEvts, ...); /* async */
bool osEventsUnsubscribe(uint32_t numEvts, ...); /* async */

bool osEnqueuePrivateEvt(uint32_t evtType, void *evtData, EventFreeF evtFreeF, uint32_t toTid);
bool osEnqueuePrivateEvtAsApp(uint32_t evtType, void *evtData, uint32_t toTid);
bool osEnqueuePrivateEvtNew(uint16_t evtType, void *evtData,
                                   void (*evtFreeCallback)(uint16_t eventType, void *eventData),
                                   uint32_t toTid);

bool osEnqueueEvt(uint32_t evtType, void *evtData, EventFreeF evtFreeF);
bool osEnqueueEvtOrFree(uint32_t evtType, void *evtData, EventFreeF evtFreeF);
bool osEnqueueEvtAsApp(uint32_t evtType, void *evtData, bool freeData);
void osRemovePendingEvents(bool (*match)(uint32_t evtType, const void *evtData, void *context), void *context);

bool osDefer(OsDeferCbkF callback, void *cookie, bool urgent);

bool osTidById(uint64_t *appId, uint32_t *tid);
bool osAppInfoById(uint64_t appId, uint32_t *appIdx, uint32_t *appVer, uint32_t *appSize);
bool osAppInfoByIndex(uint32_t appIdx, uint64_t *appId, uint32_t *appVer, uint32_t *appSize);
bool osExtAppInfoByIndex(uint32_t appIdx, uint64_t *appId, uint32_t *appVer, uint32_t *appSize);
uint32_t osGetCurrentTid();
uint32_t osSetCurrentTid(uint32_t);

struct AppHdr *osAppSegmentCreate(uint32_t size);
bool osAppSegmentClose(struct AppHdr *app, uint32_t segSize, uint32_t segState);
bool osAppSegmentSetState(const struct AppHdr *app, uint32_t segState);
bool osSegmentSetSize(struct Segment *seg, uint32_t size);
bool osAppWipeData(struct AppHdr *app);
struct Segment *osGetSegment(const struct AppHdr *app);
struct Segment *osSegmentGetEnd();

static inline int32_t osSegmentGetSize(const struct Segment *seg)
{
    return seg ? seg->size[0] | (seg->size[1] << 8) | (seg->size[2] << 16) : SEG_SIZE_INVALID;
}

static inline uint32_t osSegmentGetState(const struct Segment *seg)
{
    return seg ? seg->state : SEG_STATE_INVALID;
}

static inline struct AppHdr *osSegmentGetData(const struct Segment *seg)
{
    return (struct AppHdr*)(&seg[1]);
}

#ifdef SEGMENT_CRC_SUPPORT

struct SegmentFooter
{
    uint32_t crc;
};

#define FOOTER_SIZE sizeof(struct SegmentFooter)
#else
#define FOOTER_SIZE 0
#endif

static inline uint32_t osSegmentSizeAlignedWithFooter(uint32_t size)
{
    return ((size + 3) & ~3) + FOOTER_SIZE;
}

static inline const struct Segment *osSegmentSizeGetNext(const struct Segment *seg, uint32_t size)
{
    struct Segment *next = (struct Segment *)(((uint8_t*)seg) +
                                              osSegmentSizeAlignedWithFooter(size) +
                                              sizeof(*seg)
                                              );
    return seg ? next : NULL;
}

static inline const struct Segment *osSegmentGetNext(const struct Segment *seg)
{
    return osSegmentSizeGetNext(seg, osSegmentGetSize(seg));
}

static inline uint32_t osAppSegmentGetState(const struct AppHdr *app)
{
    return osSegmentGetState(osGetSegment(app));
}

struct SegmentIterator {
    const struct Segment *shared;
    const struct Segment *sharedEnd;
    const struct Segment *seg;
};

void osSegmentIteratorInit(struct SegmentIterator *it);

static inline bool osSegmentIteratorNext(struct SegmentIterator *it)
{
    const struct Segment *seg = it->shared;
    const struct Segment *next = seg < it->sharedEnd ? osSegmentGetNext(seg) : it->sharedEnd;

    it->shared = next;
    it->seg = seg;

    return seg < it->sharedEnd;
}

bool osWriteShared(void *dest, const void *src, uint32_t len);
bool osEraseShared();

//event retaining support
bool osRetainCurrentEvent(TaggedPtr *evtFreeingInfoP); //called from any apps' event handling to retain current event. Only valid for first app that tries. evtFreeingInfoP filled by call and used to free evt later
void osFreeRetainedEvent(uint32_t evtType, void *evtData, TaggedPtr *evtFreeingInfoP);

uint32_t osExtAppStopApps(uint64_t appId);
uint32_t osExtAppEraseApps(uint64_t appId);
uint32_t osExtAppStartApps(uint64_t appId);

bool osAppIsChre(uint16_t tid);

/* Logging */
enum LogLevel {
    LOG_ERROR   = 'E',
    LOG_WARN    = 'W',
    LOG_INFO    = 'I',
    LOG_DEBUG   = 'D',
    LOG_VERBOSE = 'V',
};

void osLogv(char clevel, uint32_t flags, const char *str, va_list vl);
void osLog(enum LogLevel level, const char *str, ...) PRINTF_ATTRIBUTE(2, 3);

#ifndef INTERNAL_APP_INIT
#define INTERNAL_APP_INIT(_id, _ver, _init, _end, _event)                               \
SET_INTERNAL_LOCATION(location, ".internal_app_init")static const struct AppHdr         \
SET_INTERNAL_LOCATION_ATTRIBUTES(used, section (".internal_app_init")) mAppHdr = {      \
    .hdr.magic   = APP_HDR_MAGIC,                                                       \
    .hdr.fwVer   = APP_HDR_VER_CUR,                                                     \
    .hdr.fwFlags = FL_APP_HDR_INTERNAL | FL_APP_HDR_APPLICATION,                        \
    .hdr.appId   = (_id),                                                               \
    .hdr.appVer  = (_ver),                                                              \
    .hdr.payInfoType = LAYOUT_APP,                                                      \
    .vec.init    = (uint32_t)(_init),                                                   \
    .vec.end     = (uint32_t)(_end),                                                    \
    .vec.handle  = (uint32_t)(_event)                                                   \
}
#endif

#ifndef APP_INIT
#define APP_INIT(_ver, _init, _end, _event)                                             \
extern const struct AppFuncs _mAppFuncs;                                                \
const struct AppFuncs SET_EXTERNAL_APP_ATTRIBUTES(used, section (".app_init"),          \
visibility("default")) _mAppFuncs = {                                                   \
    .init   = (_init),                                                                  \
    .end    = (_end),                                                                   \
    .handle = (_event)                                                                  \
};                                                                                      \
const uint32_t SET_EXTERNAL_APP_VERSION(used, section (".app_version"),                 \
visibility("default")) _mAppVer = _ver
#endif


#ifdef __cplusplus
}
#endif

#endif
