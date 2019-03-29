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

#include <inttypes.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>

#include <plat/eeData.h>
#include <plat/plat.h>
#include <plat/wdt.h>

#include <apInt.h>
#include <atomic.h>
#include <bl.h>
#include <cpu.h>
#include <crc.h>
#include <eventQ.h>
#include <heap.h>
#include <hostIntf.h>
#include <mpu.h>
#include <nanohubPacket.h>
#include <osApi.h>
#include <platform.h>
#include <printf.h>
#include <sensors.h>
#include <seos.h>
#include <seos_priv.h>
#include <slab.h>
#include <syscall.h>
#include <timer.h>
#include <util.h>

#include <nanohub/nanohub.h>

#include <chreApi.h>

struct TaskPool {
    struct Task data[MAX_TASKS];
};

static struct TaskPool mTaskPool;
static struct EvtQueue *mEvtsInternal;
static struct SlabAllocator* mMiscInternalThingsSlab;
static struct TaskList mFreeTasks;
static struct TaskList mTasks;
static struct Task *mCurrentTask;
static struct Task *mSystemTask;
static TaggedPtr *mCurEvtEventFreeingInfo = NULL; //used as flag for retaining. NULL when none or already retained

static inline void list_init(struct TaskList *l)
{
    l->prev = l->next = NO_NODE;
}

struct Task *osGetCurrentTask()
{
    return mCurrentTask;
}

struct Task *osSetCurrentTask(struct Task *task)
{
    struct Task *old = mCurrentTask;
    while (true) {
        old = mCurrentTask;
        if (atomicCmpXchgPtr((uintptr_t*)&mCurrentTask, (uintptr_t)old, (uintptr_t)task)) {
            break;
        }
    }
    return old;
}

// beyond this point, noone shall access mCurrentTask directly

static inline bool osTaskTestFlags(struct Task *task, uint32_t mask)
{
    return (atomicReadByte(&task->flags) & mask) != 0;
}

bool osAppIsChre(uint16_t tid)
{
    struct Task *task = osTaskFindByTid(tid);

    return task && osTaskIsChre(task);
}

static inline uint32_t osTaskClrSetFlags(struct Task *task, uint32_t clrMask, uint32_t setMask)
{
    while (true) {
        uint8_t flags = atomicReadByte(&task->flags);
        uint8_t newFlags = (flags & ~clrMask) | setMask;
        if (atomicCmpXchgByte(&task->flags, flags, newFlags))
            return newFlags;
    }
}

static inline uint32_t osTaskAddIoCount(struct Task *task, int32_t delta)
{
    uint8_t count = atomicAddByte(&task->ioCount, delta);

    count += delta; // old value is returned, so we add it again

    return count;
}

static inline uint32_t osTaskGetIoCount(struct Task *task)
{
    return atomicReadByte(&task->ioCount);
}

uint8_t osTaskIndex(struct Task *task)
{
    // we don't need signed diff here: this way we simplify boundary check
    size_t idx = task - &mTaskPool.data[0];
    return idx >= MAX_TASKS || &mTaskPool.data[idx] != task ? NO_NODE : idx;
}

static inline struct Task *osTaskByIdx(size_t idx)
{
    return idx >= MAX_TASKS ? NULL : &mTaskPool.data[idx];
}

uint32_t osGetCurrentTid()
{
    struct Task *task = osGetCurrentTask();
    if (task == NULL) {
        return UINT32_MAX;
    }
    return task->tid;
}

uint32_t osSetCurrentTid(uint32_t tid)
{
    struct Task *task = osTaskByIdx(TID_TO_TASK_IDX(tid));

    if (task && task->tid == tid) {
        struct Task *preempted = osSetCurrentTask(task);
        return preempted->tid;
    }

    return osGetCurrentTid();
}

static inline struct Task *osTaskListPeekHead(struct TaskList *listHead)
{
    TaskIndex idx = listHead->next;
    return idx == NO_NODE ? NULL : &mTaskPool.data[idx];
}

#ifdef DEBUG
static void dumpListItems(const char *p, struct TaskList *listHead)
{
    int i = 0;
    struct Task *task;

    osLog(LOG_ERROR, "List: %s (%p) [%u;%u]\n",
          p,
          listHead,
          listHead ? listHead->prev : NO_NODE,
          listHead ? listHead->next : NO_NODE
    );
    if (!listHead)
        return;

    for_each_task(listHead, task) {
        osLog(LOG_ERROR, "  item %d: task=%p TID=%04X [%u;%u;%u]\n",
              i,
              task,
              task->tid,
              task->list.prev,
              osTaskIndex(task),
              task->list.next
        );
        ++i;
    }
}

static void dumpTaskList(const char *f, struct Task *task, struct TaskList *listHead)
{
    osLog(LOG_ERROR, "%s: pool: %p; task=%p [%u;%u;%u]; listHead=%p [%u;%u]\n",
          f,
          &mTaskPool,
          task,
          task ? task->list.prev : NO_NODE,
          osTaskIndex(task),
          task ? task->list.next : NO_NODE,
          listHead,
          listHead ? listHead->prev : NO_NODE,
          listHead ? listHead->next : NO_NODE
    );
    dumpListItems("Tasks", &mTasks);
    dumpListItems("Free Tasks", &mFreeTasks);
}
#else
#define dumpTaskList(a,b,c)
#endif

static inline void osTaskListRemoveTask(struct TaskList *listHead, struct Task *task)
{
    if (task && listHead) {
        struct TaskList *cur = &task->list;
        TaskIndex left_idx = cur->prev;
        TaskIndex right_idx = cur->next;
        struct TaskList *left =  left_idx == NO_NODE ? listHead : &mTaskPool.data[left_idx].list;
        struct TaskList *right = right_idx == NO_NODE ? listHead : &mTaskPool.data[right_idx].list;
        cur->prev = cur->next = NO_NODE;
        left->next = right_idx;
        right->prev = left_idx;
    } else {
        dumpTaskList(__func__, task, listHead);
    }
}

static inline void osTaskListAddTail(struct TaskList *listHead, struct Task *task)
{
    if (task && listHead) {
        struct TaskList *cur = &task->list;
        TaskIndex last_idx = listHead->prev;
        TaskIndex new_idx = osTaskIndex(task);
        struct TaskList *last = last_idx == NO_NODE ? listHead : &mTaskPool.data[last_idx].list;
        cur->prev = last_idx;
        cur->next = NO_NODE;
        last->next = new_idx;
        listHead->prev = new_idx;
    } else {
        dumpTaskList(__func__, task, listHead);
    }
}

static struct Task *osAllocTask()
{
    struct Task *task = osTaskListPeekHead(&mFreeTasks);

    if (task) {
        osTaskListRemoveTask(&mFreeTasks, task);
        uint16_t tid = task->tid;
        memset(task, 0, sizeof(*task));
        task->tid = tid;
    }

    return task;
}

static void osFreeTask(struct Task *task)
{
    if (task) {
        task->flags = 0;
        task->ioCount = 0;
        osTaskListAddTail(&mFreeTasks, task);
    }
}

static void osRemoveTask(struct Task *task)
{
    osTaskListRemoveTask(&mTasks, task);
}

static void osAddTask(struct Task *task)
{
    osTaskListAddTail(&mTasks, task);
}

struct Task* osTaskFindByTid(uint32_t tid)
{
    TaskIndex idx = TID_TO_TASK_IDX(tid);

    return idx < MAX_TASKS ? &mTaskPool.data[idx] : NULL;
}

static inline bool osTaskInit(struct Task *task)
{
    struct Task *preempted = osSetCurrentTask(task);
    bool done = cpuAppInit(task->app, &task->platInfo, task->tid);
    osSetCurrentTask(preempted);
    return done;
}

static void osTaskRelease(struct Task *task)
{
    uint16_t tid = task->tid;

    osSetCurrentTask(mSystemTask);

    platFreeResources(tid); // HW resources cleanup (IRQ, DMA etc)
    sensorFreeAll(tid);
    timTimerCancelAll(tid);
    heapFreeAll(tid);
}

static inline void osTaskEnd(struct Task *task)
{
    struct Task *preempted = osSetCurrentTask(task);

    cpuAppEnd(task->app, &task->platInfo);

    // task was supposed to release it's resources,
    // but we do our cleanup anyway
    osTaskRelease(task);
    // NOTE: we don't need to unsubscribe from events
    osSetCurrentTask(preempted);
}

static inline void osTaskHandle(struct Task *task, uint16_t evtType, uint16_t fromTid, const void* evtData)
{
    struct Task *preempted = osSetCurrentTask(task);
    cpuAppHandle(task->app, &task->platInfo,
                 EVENT_WITH_ORIGIN(evtType, osTaskIsChre(task) ? fromTid : 0),
                 evtData);
    osSetCurrentTask(preempted);
}

void osTaskInvokeMessageFreeCallback(struct Task *task, void (*freeCallback)(void *, size_t), void *message, uint32_t messageSize)
{
    if (!task || !freeCallback)
        return;
    cpuAppInvoke(task->app, &task->platInfo, (void (*)(uintptr_t,uintptr_t))freeCallback, (uintptr_t)message, (uintptr_t)messageSize);
}

void osTaskInvokeEventFreeCallback(struct Task *task, void (*freeCallback)(uint16_t, void *), uint16_t event, void *data)
{
    if (!task || !freeCallback)
        return;
    cpuAppInvoke(task->app, &task->platInfo,
                 (void (*)(uintptr_t,uintptr_t))freeCallback,
                 (uintptr_t)event, (uintptr_t)data);
}

static void osPrivateEvtFreeF(void *event)
{
    union SeosInternalSlabData *act = event;
    uint16_t fromTid = act->privateEvt.fromTid;
    struct Task *srcTask = osTaskFindByTid(fromTid);
    TaggedPtr evtFreeInfo = act->privateEvt.evtFreeInfo;
    uint32_t evtType = act->privateEvt.evtType;
    void *evtData = act->privateEvt.evtData;

    slabAllocatorFree(mMiscInternalThingsSlab, event);

    if (!srcTask) {
        osLog(LOG_ERROR, "ERROR: Failed to find task to free event: evtType=%08" PRIX32 "\n", evtType);
        return;
    }

    if (taggedPtrIsPtr(evtFreeInfo) && taggedPtrToPtr(evtFreeInfo)) {
        if (osTaskIsChre(srcTask) && (evtType >> 16) == EVT_PRIVATE_CLASS_CHRE) {
            osChreFreeEvent(fromTid,
                            (void (*)(uint16_t, void *))taggedPtrToPtr(evtFreeInfo),
                            evtType & EVT_MASK, evtData);
        } else {
            // this is for internal non-CHRE tasks, and CHRE tasks
            // System may schedule non-CHRE events on behalf of CHRE app;
            // this is the place we release them
            struct Task *preempted = osSetCurrentTask(srcTask);
            ((EventFreeF)taggedPtrToPtr(evtFreeInfo))(evtData);
            osSetCurrentTask(preempted);
        }
    } else if (taggedPtrIsUint(evtFreeInfo)) {
        // this is for external non-CHRE tasks
        struct AppEventFreeData fd = {.evtType = evtType, .evtData = evtData};
        osTaskHandle(srcTask, EVT_APP_FREE_EVT_DATA, OS_SYSTEM_TID, &fd);
    }

    osTaskAddIoCount(srcTask, -1);
}

static void handleEventFreeing(uint32_t evtType, void *evtData, TaggedPtr evtFreeData) // watch out, this is synchronous
{
    struct Task *srcTask = osTaskFindByTid(EVENT_GET_ORIGIN(evtType));

    if (!srcTask) {
        osLog(LOG_ERROR, "ERROR: Failed to find task to free event: evtType=%08" PRIX32 "\n", evtType);
        return;
    }

    // release non-CHRE event; we can't determine if this is CHRE or non-CHRE event, but
    // this method is only called to release non-CHRE events, so we make use of that fact

    if (taggedPtrIsPtr(evtFreeData) && taggedPtrToPtr(evtFreeData)) {
        // this is for internal non-CHRE tasks, and CHRE tasks
        // System may schedule non-CHRE events on behalf of CHRE app;
        // this is the place we release them
        struct Task *preempted = osSetCurrentTask(srcTask);
        ((EventFreeF)taggedPtrToPtr(evtFreeData))(evtData);
        osSetCurrentTask(preempted);
    } else if (taggedPtrIsUint(evtFreeData)) {
        // this is for external non-CHRE tasks
        struct AppEventFreeData fd = {.evtType = EVENT_GET_EVENT(evtType), .evtData = evtData};
        osTaskHandle(srcTask, EVT_APP_FREE_EVT_DATA, OS_SYSTEM_TID, &fd);
    }

    osTaskAddIoCount(srcTask, -1);
}

static void osInit(void)
{
    heapInit();
    platInitialize();

    osLog(LOG_INFO, "SEOS Initializing\n");
    cpuInitLate();

    /* create the queues */
    if (!(mEvtsInternal = evtQueueAlloc(512, handleEventFreeing))) {
        osLog(LOG_INFO, "events failed to init\n");
        return;
    }

    mMiscInternalThingsSlab = slabAllocatorNew(sizeof(union SeosInternalSlabData), alignof(union SeosInternalSlabData), 64 /* for now? */);
    if (!mMiscInternalThingsSlab) {
        osLog(LOG_INFO, "deferred actions list failed to init\n");
        return;
    }
}

static struct Task* osTaskFindByAppID(uint64_t appID)
{
    struct Task *task;

    for_each_task(&mTasks, task) {
        if (task->app && task->app->hdr.appId == appID)
            return task;
    }

    return NULL;
}

void osSegmentIteratorInit(struct SegmentIterator *it)
{
    uint32_t sz;
    uint8_t *start = platGetSharedAreaInfo(&sz);

    it->shared    = (const struct Segment *)(start);
    it->sharedEnd = (const struct Segment *)(start + sz);
    it->seg       = NULL;
}

bool osAppSegmentSetState(const struct AppHdr *app, uint32_t segState)
{
    bool done;
    struct Segment *seg = osGetSegment(app);
    uint8_t state = segState;

    if (!seg)
        return false;

    mpuAllowRamExecution(true);
    mpuAllowRomWrite(true);
    done = BL.blProgramShared(&seg->state, &state, sizeof(state), BL_FLASH_KEY1, BL_FLASH_KEY2);
    mpuAllowRomWrite(false);
    mpuAllowRamExecution(false);

    return done;
}

bool osSegmentSetSize(struct Segment *seg, uint32_t size)
{
    bool ret = true;

    if (!seg)
        return false;

    if (size > SEG_SIZE_MAX) {
        seg->state = SEG_ST_ERASED;
        size = SEG_SIZE_MAX;
        ret = false;
    }
    seg->size[0] = size;
    seg->size[1] = size >> 8;
    seg->size[2] = size >> 16;

    return ret;
}

struct Segment *osSegmentGetEnd()
{
    uint32_t size;
    uint8_t *start = platGetSharedAreaInfo(&size);
    return (struct Segment *)(start + size);
}

struct Segment *osGetSegment(const struct AppHdr *app)
{
    uint32_t size;
    uint8_t *start = platGetSharedAreaInfo(&size);

    return (struct Segment *)((uint8_t*)app &&
                              (uint8_t*)app >= start &&
                              (uint8_t*)app < (start + size) ?
                              (uint8_t*)app - sizeof(struct Segment) : NULL);
}

bool osEraseShared()
{
    wdtDisableClk();
    mpuAllowRamExecution(true);
    mpuAllowRomWrite(true);
    (void)BL.blEraseShared(BL_FLASH_KEY1, BL_FLASH_KEY2);
    mpuAllowRomWrite(false);
    mpuAllowRamExecution(false);
    wdtEnableClk();
    return true;
}

bool osWriteShared(void *dest, const void *src, uint32_t len)
{
    bool ret;

    mpuAllowRamExecution(true);
    mpuAllowRomWrite(true);
    ret = BL.blProgramShared(dest, src, len, BL_FLASH_KEY1, BL_FLASH_KEY2);
    mpuAllowRomWrite(false);
    mpuAllowRamExecution(false);

    if (!ret)
        osLog(LOG_ERROR, "osWriteShared: blProgramShared return false\n");

    return ret;
}

struct AppHdr *osAppSegmentCreate(uint32_t size)
{
    struct SegmentIterator it;
    const struct Segment *storageSeg = NULL;
    struct AppHdr *app;

    osSegmentIteratorInit(&it);
    while (osSegmentIteratorNext(&it)) {
        if (osSegmentGetState(it.seg) == SEG_ST_EMPTY) {
            storageSeg = it.seg;
            break;
        }
    }
    if (!storageSeg || osSegmentSizeGetNext(storageSeg, size) > it.sharedEnd)
        return NULL;

    app = osSegmentGetData(storageSeg);
    osAppSegmentSetState(app, SEG_ST_RESERVED);

    return app;
}

bool osAppSegmentClose(struct AppHdr *app, uint32_t segDataSize, uint32_t segState)
{
    struct Segment seg;

    // this is enough for holding padding to uint32_t and the footer
    uint8_t footer[sizeof(uint32_t) + FOOTER_SIZE];
    int footerLen;
    bool ret;
    uint32_t totalSize;
    uint8_t *start = platGetSharedAreaInfo(&totalSize);
    uint8_t *end = start + totalSize;
    int32_t fullSize = segDataSize + sizeof(seg); // without footer or padding
    struct Segment *storageSeg = osGetSegment(app);

    // sanity check
    if (segDataSize >= SEG_SIZE_MAX)
        return false;

    // physical limits check
    if (osSegmentSizeAlignedWithFooter(segDataSize) + sizeof(struct Segment) > totalSize)
        return false;

    // available space check: we could truncate size, instead of disallowing it,
    // but we know that we performed validation on the size before, in *Create call,
    // and it was fine, so this must be a programming error, and so we fail.
    // on a side note: size may grow or shrink compared to original estimate.
    // typically it shrinks, since we skip some header info and padding, as well
    // as signature blocks, but it is possible that at some point we may produce
    // more data for some reason. At that time the logic here may need to change
    if (osSegmentSizeGetNext(storageSeg, segDataSize) > (struct Segment*)end)
        return false;

    seg.state = segState;
    osSegmentSetSize(&seg, segDataSize);

    ret = osWriteShared((uint8_t*)storageSeg, (uint8_t*)&seg, sizeof(seg));

    footerLen = (-fullSize) & 3;
    memset(footer, 0x00, footerLen);

#ifdef SEGMENT_CRC_SUPPORT
    struct SegmentFooter segFooter {
        .crc = ~crc32(storageSeg, fullSize, ~0),
    };
    memcpy(&footer[footerLen], &segFooter, sizeof(segFooter));
    footerLen += sizeof(segFooter);
#endif

    if (ret && footerLen)
        ret = osWriteShared((uint8_t*)storageSeg + fullSize, footer, footerLen);

    return ret;
}

bool osAppWipeData(struct AppHdr *app)
{
    struct Segment *seg = osGetSegment(app);
    int32_t size = osSegmentGetSize(seg);
    uint8_t *p = (uint8_t*)app;
    uint32_t state = osSegmentGetState(seg);
    uint8_t buf[256];
    bool done = true;

    if (!seg || size == SEG_SIZE_INVALID || state == SEG_ST_EMPTY) {
        osLog(LOG_ERROR, "%s: can't erase segment: app=%p; seg=%p"
                         "; size=%" PRIu32
                         "; state=%" PRIu32
                         "\n",
                         __func__, app, seg, size, state);
        return false;
    }

    size = osSegmentSizeAlignedWithFooter(size);

    memset(buf, 0, sizeof(buf));
    while (size > 0) {
        uint32_t flashSz = size > sizeof(buf) ? sizeof(buf) : size;
        // keep trying to zero-out stuff even in case of intermittent failures.
        // flash write may occasionally fail on some byte, but it is not good enough
        // reason to not rewrite other bytes
        bool res = osWriteShared(p, buf, flashSz);
        done = done && res;
        size -= flashSz;
        p += flashSz;
    }

    return done;
}

static inline bool osAppIsValid(const struct AppHdr *app)
{
    return app->hdr.magic == APP_HDR_MAGIC &&
           app->hdr.fwVer == APP_HDR_VER_CUR &&
           (app->hdr.fwFlags & FL_APP_HDR_APPLICATION) != 0 &&
           app->hdr.payInfoType == LAYOUT_APP;
}

static bool osExtAppIsValid(const struct AppHdr *app, uint32_t len)
{
    //TODO: when CRC support is ready, add CRC check here
    return  osAppIsValid(app) &&
            len >= sizeof(*app) &&
            osAppSegmentGetState(app) == SEG_ST_VALID &&
            !(app->hdr.fwFlags & FL_APP_HDR_INTERNAL);
}

static bool osIntAppIsValid(const struct AppHdr *app)
{
    return  osAppIsValid(app) &&
            osAppSegmentGetState(app) == SEG_STATE_INVALID &&
            (app->hdr.fwFlags & FL_APP_HDR_INTERNAL) != 0;
}

static inline bool osExtAppErase(const struct AppHdr *app)
{
    return osAppSegmentSetState(app, SEG_ST_ERASED);
}

static struct Task *osLoadApp(const struct AppHdr *app) {
    struct Task *task;

    task = osAllocTask();
    if (!task) {
        osLog(LOG_WARN, "External app id %016" PRIX64 " @ %p cannot be used as too many apps already exist.\n", app->hdr.appId, app);
        return NULL;
    }
    task->app = app;
    bool done = (app->hdr.fwFlags & FL_APP_HDR_INTERNAL) ?
                cpuInternalAppLoad(task->app, &task->platInfo) :
                cpuAppLoad(task->app, &task->platInfo);

    if (!done) {
        osLog(LOG_WARN, "App @ %p ID %016" PRIX64 " failed to load\n", app, app->hdr.appId);
        osFreeTask(task);
        task = NULL;
    }

    return task;
}

static void osUnloadApp(struct Task *task)
{
    // this is called on task that has stopped running, or had never run
    cpuAppUnload(task->app, &task->platInfo);
    osFreeTask(task);
}

static bool osStartApp(const struct AppHdr *app)
{
    bool done = false;
    struct Task *task;

    if ((task = osLoadApp(app)) != NULL) {
        task->subbedEvtListSz = MAX_EMBEDDED_EVT_SUBS;
        task->subbedEvents = task->subbedEventsInt;
        osTaskMakeNewTid(task);

        // print external NanoApp info to facilitate NanoApp debugging
        if (!(task->app->hdr.fwFlags & FL_APP_HDR_INTERNAL))
            osLog(LOG_INFO, "loaded app ID 0x%llx at flash base 0x%08x ram base 0x%08x; TID %04X\n",
                  task->app->hdr.appId, (uintptr_t) task->app, (uintptr_t) task->platInfo.data, task->tid);

        done = osTaskInit(task);

        if (!done) {
            osLog(LOG_WARN, "App @ %p ID %016" PRIX64 " failed to init\n", task->app, task->app->hdr.appId);
            osUnloadApp(task);
        } else {
            osAddTask(task);
        }
    }

    return done;
}

static bool osStopTask(struct Task *task)
{
    if (!task)
        return false;

    osRemoveTask(task);

    if (osTaskGetIoCount(task))
    {
        osTaskHandle(task, EVT_APP_STOP, OS_SYSTEM_TID, NULL);
        osEnqueueEvt(EVT_APP_END, task, NULL);
    } else {
        osTaskEnd(task); // calls app END() and Release()
        osUnloadApp(task);
    }

    osTaskClrSetFlags(task, 0, FL_TASK_STOPPED);
    return true;
}

void osTaskAbort(struct Task *task)
{
    if (!task)
        return;

    osRemoveTask(task); // remove from active task list
    // do not call app END()
    osTaskRelease(task); // release all system resources
    osUnloadApp(task); // destroy platform app object in RAM
}

static bool osExtAppFind(struct SegmentIterator *it, uint64_t appId)
{
    uint64_t vendor = APP_ID_GET_VENDOR(appId);
    uint64_t seqId = APP_ID_GET_SEQ_ID(appId);
    uint64_t curAppId;
    const struct AppHdr *app;
    const struct Segment *seg;

    while (osSegmentIteratorNext(it)) {
        seg = it->seg;
        if (!seg)
            break;
        if (seg->state == SEG_ST_EMPTY)
            break;
        if (seg->state != SEG_ST_VALID)
            continue;
        app = osSegmentGetData(seg);
        curAppId = app->hdr.appId;

        if ((vendor == APP_VENDOR_ANY || vendor == APP_ID_GET_VENDOR(curAppId)) &&
            (seqId == APP_SEQ_ID_ANY || seqId == APP_ID_GET_SEQ_ID(curAppId)))
            return true;
    }

    return false;
}

static uint32_t osExtAppStopEraseApps(uint64_t appId, bool doErase)
{
    const struct AppHdr *app;
    int32_t len;
    struct SegmentIterator it;
    uint32_t stopCount = 0;
    uint32_t eraseCount = 0;
    uint32_t appCount = 0;
    uint32_t taskCount = 0;
    struct MgmtStatus stat = { .value = 0 };
    struct Task *task;

    osSegmentIteratorInit(&it);
    while (osExtAppFind(&it, appId)) {
        app = osSegmentGetData(it.seg);
        len = osSegmentGetSize(it.seg);
        if (!osExtAppIsValid(app, len))
            continue;
        appCount++;
        /* it is safe to erase a running app;
         * erase merely sets a flag in the header,
         * and app keeps running until it is stopped */
        if (doErase && osExtAppErase(app))
            eraseCount++;
        task = osTaskFindByAppID(app->hdr.appId);
        if (task) {
            taskCount++;
            if (osStopTask(task))
               stopCount++;
        }
    }
    SET_COUNTER(stat.app,   appCount);
    SET_COUNTER(stat.task,  taskCount);
    SET_COUNTER(stat.op,    stopCount);
    SET_COUNTER(stat.erase, eraseCount);

    return stat.value;
}

uint32_t osExtAppStopApps(uint64_t appId)
{
    return osExtAppStopEraseApps(appId, false);
}

uint32_t osExtAppEraseApps(uint64_t appId)
{
    return osExtAppStopEraseApps(appId, true);
}

static void osScanExternal()
{
    struct SegmentIterator it;
    osSegmentIteratorInit(&it);
    while (osSegmentIteratorNext(&it)) {
        switch (osSegmentGetState(it.seg)) {
        case SEG_ST_EMPTY:
            // everything looks good
            osLog(LOG_INFO, "External area is good\n");
            return;
        case SEG_ST_ERASED:
        case SEG_ST_VALID:
            // this is valid stuff, ignore
            break;
        case SEG_ST_RESERVED:
        default:
            // something is wrong: erase everything
            osLog(LOG_ERROR, "External area is damaged. Erasing\n");
            osEraseShared();
            return;
        }
    }
}

uint32_t osExtAppStartApps(uint64_t appId)
{
    const struct AppHdr *app;
    int32_t len;
    struct SegmentIterator it;
    struct SegmentIterator checkIt;
    uint32_t startCount = 0;
    uint32_t eraseCount = 0;
    uint32_t appCount = 0;
    uint32_t taskCount = 0;
    struct MgmtStatus stat = { .value = 0 };

    osScanExternal();

    osSegmentIteratorInit(&it);
    while (osExtAppFind(&it, appId)) {
        app = osSegmentGetData(it.seg);
        len = osSegmentGetSize(it.seg);

        // skip erased or malformed apps
        if (!osExtAppIsValid(app, len))
            continue;

        appCount++;
        checkIt = it;
        // find the most recent copy
        while (osExtAppFind(&checkIt, app->hdr.appId)) {
            if (osExtAppErase(app)) // erase the old one, so we skip it next time
                eraseCount++;
            app = osSegmentGetData(checkIt.seg);
        }

        if (osTaskFindByAppID(app->hdr.appId)) {
            // this either the most recent external app with the same ID,
            // or internal app with the same id; in both cases we do nothing
            taskCount++;
            continue;
        }

        if (osStartApp(app))
            startCount++;
    }
    SET_COUNTER(stat.app,   appCount);
    SET_COUNTER(stat.task,  taskCount);
    SET_COUNTER(stat.op,    startCount);
    SET_COUNTER(stat.erase, eraseCount);

    return stat.value;
}

static void osStartTasks(void)
{
    const struct AppHdr *app;
    uint32_t i, nApps;
    struct Task* task;
    uint32_t status = 0;
    uint32_t taskCnt = 0;

    osLog(LOG_DEBUG, "Initializing task pool...\n");
    list_init(&mTasks);
    list_init(&mFreeTasks);
    for (i = 0; i < MAX_TASKS; ++i) {
        task = &mTaskPool.data[i];
        list_init(&task->list);
        osFreeTask(task);
    }

    mSystemTask = osAllocTask(); // this is a dummy task; holder of TID 0; all system code will run with TID 0
    osSetCurrentTask(mSystemTask);
    osLog(LOG_DEBUG, "System task is: %p\n", mSystemTask);

    /* first enum all internal apps, making sure to check for dupes */
    osLog(LOG_DEBUG, "Starting internal apps...\n");
    for (i = 0, app = platGetInternalAppList(&nApps); i < nApps; i++, app++) {
        if (!osIntAppIsValid(app)) {
            osLog(LOG_WARN, "Invalid internal app @ %p ID %016" PRIX64
                            "header version: %" PRIu16
                            "\n",
                            app, app->hdr.appId, app->hdr.fwVer);
            continue;
        }

        if (!(app->hdr.fwFlags & FL_APP_HDR_INTERNAL)) {
            osLog(LOG_WARN, "Internal app is not marked: [%p]: flags: 0x%04" PRIX16
                            "; ID: %016" PRIX64
                            "; ignored\n",
                            app, app->hdr.fwFlags, app->hdr.appId);
            continue;
        }
        if ((task = osTaskFindByAppID(app->hdr.appId))) {
            osLog(LOG_WARN, "Internal app ID %016" PRIX64
                            "@ %p attempting to update internal app @ %p; app @%p ignored.\n",
                            app->hdr.appId, app, task->app, app);
            continue;
        }
        if (osStartApp(app))
            taskCnt++;
    }

    osLog(LOG_DEBUG, "Starting external apps...\n");
    status = osExtAppStartApps(APP_ID_ANY);
    osLog(LOG_DEBUG, "Started %" PRIu32 " internal apps; EXT status: %08" PRIX32 "\n", taskCnt, status);
}

static void osInternalEvtHandle(uint32_t evtType, void *evtData)
{
    union SeosInternalSlabData *da = (union SeosInternalSlabData*)evtData;
    struct Task *task;
    uint32_t i, j;
    uint16_t tid = EVENT_GET_ORIGIN(evtType);
    uint16_t evt = EVENT_GET_EVENT(evtType);
    struct Task *srcTask = osTaskFindByTid(tid);
    struct Task *preempted = osSetCurrentTask(srcTask);

    switch (evt) {
    case EVT_SUBSCRIBE_TO_EVT:
    case EVT_UNSUBSCRIBE_TO_EVT:
        /* get task */
        task = osTaskFindByTid(da->evtSub.tid);
        if (!task)
            break;

        for (j = 0; j < da->evtSub.numEvts; j++) {
            /* find if subscribed to this evt */
            for (i = 0; i < task->subbedEvtCount && task->subbedEvents[i] != da->evtSub.evts[j]; i++);

            /* if unsub & found -> unsub */
            if (evt == EVT_UNSUBSCRIBE_TO_EVT && i != task->subbedEvtCount)
                task->subbedEvents[i] = task->subbedEvents[--task->subbedEvtCount];
            /* if sub & not found -> sub */
            else if (evt == EVT_SUBSCRIBE_TO_EVT && i == task->subbedEvtCount) {
                if (task->subbedEvtListSz == task->subbedEvtCount) { /* enlarge the list */
                    uint32_t newSz = (task->subbedEvtListSz * 3 + 1) / 2;
                    uint32_t *newList = heapAlloc(sizeof(uint32_t[newSz])); /* grow by 50% */
                    if (newList) {
                        memcpy(newList, task->subbedEvents, sizeof(uint32_t[task->subbedEvtListSz]));
                        if (task->subbedEvents != task->subbedEventsInt)
                            heapFree(task->subbedEvents);
                        task->subbedEvents = newList;
                        task->subbedEvtListSz = newSz;
                    }
                }
                if (task->subbedEvtListSz > task->subbedEvtCount) { /* have space ? */
                    task->subbedEvents[task->subbedEvtCount++] = da->evtSub.evts[j];
                }
            }
        }
        break;

    case EVT_APP_END:
        task = evtData;
        osTaskEnd(task);
        osUnloadApp(task);
        break;

    case EVT_DEFERRED_CALLBACK:
        da->deferred.callback(da->deferred.cookie);
        break;

    case EVT_PRIVATE_EVT:
        task = osTaskFindByTid(da->privateEvt.toTid);
        evtType = da->privateEvt.evtType & EVT_MASK;
        evtData = da->privateEvt.evtData;
        if (task) {
            //private events cannot be retained
            TaggedPtr *tmp = mCurEvtEventFreeingInfo;
            mCurEvtEventFreeingInfo = NULL;
            osTaskHandle(task, evtType, da->privateEvt.fromTid, da->privateEvt.evtData);
            mCurEvtEventFreeingInfo = tmp;
        }
        break;
    }
    osSetCurrentTask(preempted);
}

void abort(void)
{
    /* this is necessary for va_* funcs... */
    osLog(LOG_ERROR, "Abort called");
    while(1);
}

bool osRetainCurrentEvent(TaggedPtr *evtFreeingInfoP)
{
    if (!mCurEvtEventFreeingInfo)
        return false;

    *evtFreeingInfoP = *mCurEvtEventFreeingInfo;
    mCurEvtEventFreeingInfo = NULL;
    return true;
}

void osFreeRetainedEvent(uint32_t evtType, void *evtData, TaggedPtr *evtFreeingInfoP)
{
    //TODO: figure the way to calculate src tid here to pass to handleEventFreeing
    handleEventFreeing(evtType, evtData, *evtFreeingInfoP);
}

void osMainInit(void)
{
    cpuInit();
    cpuIntsOff();
    osInit();
    timInit();
    sensorsInit();
    syscallInit();
    osApiExport(mMiscInternalThingsSlab);
    osChreApiExport();
    apIntInit();
    cpuIntsOn();
    wdtInit();
    osStartTasks();

    //broadcast app start to all already-loaded apps
    (void)osEnqueueEvt(EVT_APP_START, NULL, NULL);
}

void osMainDequeueLoop(void)
{
    TaggedPtr evtFreeingInfo;
    uint32_t evtType, j;
    void *evtData;
    struct Task *task;
    uint16_t tid, evt;

    /* get an event */
    if (!evtQueueDequeue(mEvtsInternal, &evtType, &evtData, &evtFreeingInfo, true))
        return;

    /* by default we free them when we're done with them */
    mCurEvtEventFreeingInfo = &evtFreeingInfo;
    tid = EVENT_GET_ORIGIN(evtType);
    evt = EVENT_GET_EVENT(evtType);

    if (evt < EVT_NO_FIRST_USER_EVENT) {
        /* handle deferred actions and other reserved events here */
        osInternalEvtHandle(evtType, evtData);
    } else {
        /* send this event to all tasks who want it */
        for_each_task(&mTasks, task) {
            for (j = 0; j < task->subbedEvtCount; j++) {
                if (task->subbedEvents[j] == evt) {
                    osTaskHandle(task, evt, tid, evtData);
                    break;
                }
            }
        }
    }

    /* free it */
    if (mCurEvtEventFreeingInfo)
        handleEventFreeing(evtType, evtData, evtFreeingInfo);

    /* avoid some possible errors */
    mCurEvtEventFreeingInfo = NULL;
}

void __attribute__((noreturn)) osMain(void)
{
    osMainInit();

    while (true)
    {
        osMainDequeueLoop();
        platPeriodic();
    }
}

static void osDeferredActionFreeF(void* event)
{
    slabAllocatorFree(mMiscInternalThingsSlab, event);
}

static bool osEventsSubscribeUnsubscribeV(bool sub, uint32_t numEvts, va_list ap)
{
    union SeosInternalSlabData *act = slabAllocatorAlloc(mMiscInternalThingsSlab);
    int i;

    if (!act || numEvts > MAX_EVT_SUB_CNT)
        return false;

    act->evtSub.tid = osGetCurrentTid();
    act->evtSub.numEvts = numEvts;
    for (i = 0; i < numEvts; i++)
        act->evtSub.evts[i] = va_arg(ap, uint32_t);

    return osEnqueueEvtOrFree(sub ? EVT_SUBSCRIBE_TO_EVT : EVT_UNSUBSCRIBE_TO_EVT, act, osDeferredActionFreeF);
}

static bool osEventsSubscribeUnsubscribe(bool sub, uint32_t numEvts, ...)
{
    bool ret;
    va_list ap;

    va_start(ap, numEvts);
    ret = osEventsSubscribeUnsubscribeV(sub, numEvts, ap);
    va_end(ap);

    return ret;
}

bool osEventSubscribe(uint32_t tid, uint32_t evtType)
{
    (void)tid;
    return osEventsSubscribeUnsubscribe(true, 1, evtType);
}

bool osEventUnsubscribe(uint32_t tid, uint32_t evtType)
{
    (void)tid;
    return osEventsSubscribeUnsubscribe(false, 1, evtType);
}

bool osEventsSubscribe(uint32_t numEvts, ...)
{
    bool ret;
    va_list ap;

    va_start(ap, numEvts);
    ret = osEventsSubscribeUnsubscribeV(true, numEvts, ap);
    va_end(ap);

    return ret;
}

bool osEventsUnsubscribe(uint32_t numEvts, ...)
{
    bool ret;
    va_list ap;

    va_start(ap, numEvts);
    ret = osEventsSubscribeUnsubscribeV(false, numEvts, ap);
    va_end(ap);

    return ret;
}

static bool osEnqueueEvtCommon(uint32_t evt, void *evtData, TaggedPtr evtFreeInfo, bool urgent)
{
    struct Task *task = osGetCurrentTask();
    uint32_t evtType = EVENT_WITH_ORIGIN(evt, osGetCurrentTid());

    osTaskAddIoCount(task, 1);

    if (osTaskTestFlags(task, FL_TASK_STOPPED)) {
        handleEventFreeing(evtType, evtData, evtFreeInfo);
        return true;
    }

    if (!evtQueueEnqueue(mEvtsInternal, evtType, evtData, evtFreeInfo, urgent)) {
        osTaskAddIoCount(task, -1);
        return false;
    }

    return true;
}

void osRemovePendingEvents(bool (*match)(uint32_t evtType, const void *evtData, void *context), void *context)
{
    evtQueueRemoveAllMatching(mEvtsInternal, match, context);
}

bool osEnqueueEvt(uint32_t evtType, void *evtData, EventFreeF evtFreeF)
{
    return osEnqueueEvtCommon(evtType, evtData, taggedPtrMakeFromPtr(evtFreeF), false);
}

bool osEnqueueEvtOrFree(uint32_t evtType, void *evtData, EventFreeF evtFreeF)
{
    bool success = osEnqueueEvt(evtType, evtData, evtFreeF);

    if (!success && evtFreeF)
        evtFreeF(evtData);

    return success;
}

bool osEnqueueEvtAsApp(uint32_t evtType, void *evtData, bool freeData)
{
    // compatibility with existing external apps
    if (evtType & EVENT_TYPE_BIT_DISCARDABLE_COMPAT)
        evtType |= EVENT_TYPE_BIT_DISCARDABLE;

    return osEnqueueEvtCommon(evtType, evtData, freeData ? taggedPtrMakeFromUint(osGetCurrentTid()) : taggedPtrMakeFromPtr(NULL), false);
}

bool osDefer(OsDeferCbkF callback, void *cookie, bool urgent)
{
    union SeosInternalSlabData *act = slabAllocatorAlloc(mMiscInternalThingsSlab);
    if (!act)
            return false;

    act->deferred.callback = callback;
    act->deferred.cookie = cookie;

    if (osEnqueueEvtCommon(EVT_DEFERRED_CALLBACK, act, taggedPtrMakeFromPtr(osDeferredActionFreeF), urgent))
        return true;

    slabAllocatorFree(mMiscInternalThingsSlab, act);
    return false;
}

static bool osEnqueuePrivateEvtEx(uint32_t evtType, void *evtData, TaggedPtr evtFreeInfo, uint32_t toTid)
{
    union SeosInternalSlabData *act = slabAllocatorAlloc(mMiscInternalThingsSlab);
    bool result;

    if (!act) {
        osLog(LOG_ERROR, "[seos] ERROR: osEnqueuePrivateEvtEx: call to slabAllocatorAlloc() failed\n");
        return false;
    }
    struct Task *task = osGetCurrentTask();
    osTaskAddIoCount(task, 1);

    act->privateEvt.evtType = evtType;
    act->privateEvt.evtData = evtData;
    act->privateEvt.evtFreeInfo = evtFreeInfo;
    act->privateEvt.fromTid = task->tid;
    act->privateEvt.toTid = toTid;

    osSetCurrentTask(mSystemTask);
    result = osEnqueueEvtOrFree(EVT_PRIVATE_EVT, act, osPrivateEvtFreeF);
    osSetCurrentTask(task);
    return result;
}

// only called to send events for CHRE apps
bool osEnqueuePrivateEvtNew(uint16_t evtType, void *evtData,
                                   void (*evtFreeCallback)(uint16_t evtType, void *evtData),
                                   uint32_t toTid)
{
    if (!osEnqueuePrivateEvtEx(evtType | (EVT_PRIVATE_CLASS_CHRE << 16), evtData,
                               taggedPtrMakeFromPtr(evtFreeCallback), toTid)) {
        osChreFreeEvent(osGetCurrentTid(), evtFreeCallback, evtType, evtData);
        return false;
    }
    return true;
}

bool osEnqueuePrivateEvt(uint32_t evtType, void *evtData, EventFreeF evtFreeF, uint32_t toTid)
{
    return osEnqueuePrivateEvtEx(evtType & EVT_MASK, evtData, taggedPtrMakeFromPtr(evtFreeF), toTid);
}

bool osEnqueuePrivateEvtAsApp(uint32_t evtType, void *evtData, uint32_t toTid)
{
    return osEnqueuePrivateEvtEx(evtType & EVT_MASK, evtData, taggedPtrMakeFromUint(osGetCurrentTid()), toTid);
}

bool osTidById(uint64_t *appId, uint32_t *tid)
{
    struct Task *task;

    for_each_task(&mTasks, task) {
        if (task->app && !memcmp(&task->app->hdr.appId, appId, sizeof(*appId))) {
            *tid = task->tid;
            return true;
        }
    }

    return false;
}

bool osAppInfoById(uint64_t appId, uint32_t *appIdx, uint32_t *appVer, uint32_t *appSize)
{
    uint32_t i = 0;
    struct Task *task;

    for_each_task(&mTasks, task) {
        const struct AppHdr *app = task->app;
        if (app && app->hdr.appId == appId) {
            *appIdx = i;
            *appVer = app->hdr.appVer;
            *appSize = app->sect.rel_end;
            return true;
        }
        i++;
    }

    return false;
}

bool osAppInfoByIndex(uint32_t appIdx, uint64_t *appId, uint32_t *appVer, uint32_t *appSize)
{
    struct Task *task;
    int i = 0;

    for_each_task(&mTasks, task) {
        if (i != appIdx) {
            ++i;
        } else {
            const struct AppHdr *app = task->app;
            *appId = app->hdr.appId;
            *appVer = app->hdr.appVer;
            *appSize = app->sect.rel_end;
            return true;
        }
    }

    return false;
}

bool osExtAppInfoByIndex(uint32_t appIdx, uint64_t *appId, uint32_t *appVer, uint32_t *appSize)
{
    struct Task *task;
    int i = 0;

    for_each_task(&mTasks, task) {
        const struct AppHdr *app = task->app;
        if (!(app->hdr.fwFlags & FL_APP_HDR_INTERNAL)) {
            if (i != appIdx) {
                ++i;
            } else {
                *appId = app->hdr.appId;
                *appVer = app->hdr.appVer;
                *appSize = app->sect.rel_end;
                return true;
            }
        }
    }

    return false;
}

void osLogv(char clevel, uint32_t flags, const char *str, va_list vl)
{
    void *userData = platLogAllocUserData();

    platLogPutcharF(userData, clevel);
    cvprintf(platLogPutcharF, flags, userData, str, vl);

    platLogFlush(userData);
}

void osLog(enum LogLevel level, const char *str, ...)
{
    va_list vl;

    va_start(vl, str);
    osLogv((char)level, 0, str, vl);
    va_end(vl);
}




//Google's public key for Google's apps' signing
const uint8_t __attribute__ ((section (".pubkeys"))) _RSA_KEY_GOOGLE[] = {
    0xd9, 0xcd, 0x83, 0xae, 0xb5, 0x9e, 0xe4, 0x63, 0xf1, 0x4c, 0x26, 0x6a, 0x1c, 0xeb, 0x4c, 0x12,
    0x5b, 0xa6, 0x71, 0x7f, 0xa2, 0x4e, 0x7b, 0xa2, 0xee, 0x02, 0x86, 0xfc, 0x0d, 0x31, 0x26, 0x74,
    0x1e, 0x9c, 0x41, 0x43, 0xba, 0x16, 0xe9, 0x23, 0x4d, 0xfc, 0xc4, 0xca, 0xcc, 0xd5, 0x27, 0x2f,
    0x16, 0x4c, 0xe2, 0x85, 0x39, 0xb3, 0x0b, 0xcb, 0x73, 0xb6, 0x56, 0xc2, 0x98, 0x83, 0xf6, 0xfa,
    0x7a, 0x6e, 0xa0, 0x9a, 0xcc, 0x83, 0x97, 0x9d, 0xde, 0x89, 0xb2, 0xa3, 0x05, 0x46, 0x0c, 0x12,
    0xae, 0x01, 0xf8, 0x0c, 0xf5, 0x39, 0x32, 0xe5, 0x94, 0xb9, 0xa0, 0x8f, 0x19, 0xe4, 0x39, 0x54,
    0xad, 0xdb, 0x81, 0x60, 0x74, 0x63, 0xd5, 0x80, 0x3b, 0xd2, 0x88, 0xf4, 0xcb, 0x6b, 0x47, 0x28,
    0x80, 0xb0, 0xd1, 0x89, 0x6d, 0xd9, 0x62, 0x88, 0x81, 0xd6, 0xc0, 0x13, 0x88, 0x91, 0xfb, 0x7d,
    0xa3, 0x7f, 0xa5, 0x40, 0x12, 0xfb, 0x77, 0x77, 0x4c, 0x98, 0xe4, 0xd3, 0x62, 0x39, 0xcc, 0x63,
    0x34, 0x76, 0xb9, 0x12, 0x67, 0xfe, 0x83, 0x23, 0x5d, 0x40, 0x6b, 0x77, 0x93, 0xd6, 0xc0, 0x86,
    0x6c, 0x03, 0x14, 0xdf, 0x78, 0x2d, 0xe0, 0x9b, 0x5e, 0x05, 0xf0, 0x93, 0xbd, 0x03, 0x1d, 0x17,
    0x56, 0x88, 0x58, 0x25, 0xa6, 0xae, 0x63, 0xd2, 0x01, 0x43, 0xbb, 0x7e, 0x7a, 0xa5, 0x62, 0xdf,
    0x8a, 0x31, 0xbd, 0x24, 0x1b, 0x1b, 0xeb, 0xfe, 0xdf, 0xd1, 0x31, 0x61, 0x4a, 0xfa, 0xdd, 0x6e,
    0x62, 0x0c, 0xa9, 0xcd, 0x08, 0x0c, 0xa1, 0x1b, 0xe7, 0xf2, 0xed, 0x36, 0x22, 0xd0, 0x5d, 0x80,
    0x78, 0xeb, 0x6f, 0x5a, 0x58, 0x18, 0xb5, 0xaf, 0x82, 0x77, 0x4c, 0x95, 0xce, 0xc6, 0x4d, 0xda,
    0xca, 0xef, 0x68, 0xa6, 0x6d, 0x71, 0x4d, 0xf1, 0x14, 0xaf, 0x68, 0x25, 0xb8, 0xf3, 0xff, 0xbe,
};


#ifdef DEBUG

//debug key whose privatekey is checked in as misc/debug.privkey
const uint8_t __attribute__ ((section (".pubkeys"))) _RSA_KEY_GOOGLE_DEBUG[] = {
    0x2d, 0xff, 0xa6, 0xb5, 0x65, 0x87, 0xbe, 0x61, 0xd1, 0xe1, 0x67, 0x10, 0xa1, 0x9b, 0xc6, 0xca,
    0xc8, 0xb1, 0xf0, 0xaa, 0x88, 0x60, 0x9f, 0xa1, 0x00, 0xa1, 0x41, 0x9a, 0xd8, 0xb4, 0xd1, 0x74,
    0x9f, 0x23, 0x28, 0x0d, 0xc2, 0xc4, 0x37, 0x15, 0xb1, 0x4a, 0x80, 0xca, 0xab, 0xb9, 0xba, 0x09,
    0x7d, 0xf8, 0x44, 0xd6, 0xa2, 0x72, 0x28, 0x12, 0x91, 0xf6, 0xa5, 0xea, 0xbd, 0xf8, 0x81, 0x6b,
    0xd2, 0x3c, 0x50, 0xa2, 0xc6, 0x19, 0x54, 0x48, 0x45, 0x8d, 0x92, 0xac, 0x01, 0xda, 0x14, 0x32,
    0xdb, 0x05, 0x82, 0x06, 0x30, 0x25, 0x09, 0x7f, 0x5a, 0xbb, 0x86, 0x64, 0x70, 0x98, 0x64, 0x1e,
    0xe6, 0xca, 0x1d, 0xc1, 0xcb, 0xb6, 0x23, 0xd2, 0x62, 0x00, 0x46, 0x97, 0xd5, 0xcc, 0xe6, 0x36,
    0x72, 0xec, 0x2e, 0x43, 0x1f, 0x0a, 0xaf, 0xf2, 0x51, 0xe1, 0xcd, 0xd2, 0x98, 0x5d, 0x7b, 0x64,
    0xeb, 0xd1, 0x35, 0x4d, 0x59, 0x13, 0x82, 0x6c, 0xbd, 0xc4, 0xa2, 0xfc, 0xad, 0x64, 0x73, 0xe2,
    0x71, 0xb5, 0xf4, 0x45, 0x53, 0x6b, 0xc3, 0x56, 0xb9, 0x8b, 0x3d, 0xeb, 0x00, 0x48, 0x6e, 0x29,
    0xb1, 0xb4, 0x8e, 0x2e, 0x43, 0x39, 0xef, 0x45, 0xa0, 0xb8, 0x8b, 0x5f, 0x80, 0xb5, 0x0c, 0xc3,
    0x03, 0xe3, 0xda, 0x51, 0xdc, 0xec, 0x80, 0x2c, 0x0c, 0xdc, 0xe2, 0x71, 0x0a, 0x14, 0x4f, 0x2c,
    0x22, 0x2b, 0x0e, 0xd1, 0x8b, 0x8f, 0x93, 0xd2, 0xf3, 0xec, 0x3a, 0x5a, 0x1c, 0xba, 0x80, 0x54,
    0x23, 0x7f, 0xb0, 0x54, 0x8b, 0xe3, 0x98, 0x22, 0xbb, 0x4b, 0xd0, 0x29, 0x5f, 0xce, 0xf2, 0xaa,
    0x99, 0x89, 0xf2, 0xb7, 0x5d, 0x8d, 0xb2, 0x72, 0x0b, 0x52, 0x02, 0xb8, 0xa4, 0x37, 0xa0, 0x3b,
    0xfe, 0x0a, 0xbc, 0xb3, 0xb3, 0xed, 0x8f, 0x8c, 0x42, 0x59, 0xbe, 0x4e, 0x31, 0xed, 0x11, 0x9b,
};

#endif
