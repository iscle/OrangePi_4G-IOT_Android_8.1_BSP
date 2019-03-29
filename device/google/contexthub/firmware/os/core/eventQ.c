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

#include <platform.h>
#include <eventQ.h>
#include <stddef.h>
#include <timer.h>
#include <stdio.h>
#include <heap.h>
#include <slab.h>
#include <cpu.h>
#include <util.h>
#include <plat/plat.h>
#include <plat/taggedPtr.h>

#define for_each_item_safe(head, pos, tmp) \
    for (pos = (head)->next; tmp = (pos)->next, (pos) != (head); pos = (tmp))

struct EvtList
{
    struct EvtList *next;
    struct EvtList *prev;
};

struct EvtRecord {
    struct EvtList item;
    uint32_t evtType;
    void* evtData;
    TaggedPtr evtFreeData;
};

struct EvtQueue {
    struct EvtList head;
    struct SlabAllocator *evtsSlab;
    EvtQueueForciblyDiscardEvtCbkF forceDiscardCbk;
};

static inline void __evtListDel(struct EvtList *prev, struct EvtList *next)
{
    next->prev = prev;
    prev->next = next;
}

static inline void evtListDel(struct EvtList *entry)
{
    __evtListDel(entry->prev, entry->next);
    entry->next = entry->prev = NULL;
}

struct EvtQueue* evtQueueAlloc(uint32_t size, EvtQueueForciblyDiscardEvtCbkF forceDiscardCbk)
{
    struct EvtQueue *q = heapAlloc(sizeof(struct EvtQueue));
    struct SlabAllocator *slab = slabAllocatorNew(sizeof(struct EvtRecord),
                                                  alignof(struct EvtRecord), size);

    if (q && slab) {
        q->forceDiscardCbk = forceDiscardCbk;
        q->evtsSlab = slab;
        q->head.next = &q->head;
        q->head.prev = &q->head;
        return q;
    }

    if (q)
        heapFree(q);
    if (slab)
        slabAllocatorDestroy(slab);

    return NULL;
}

void evtQueueFree(struct EvtQueue* q)
{
    struct EvtList *pos, *tmp;

    for_each_item_safe (&q->head, pos, tmp) {
        struct EvtRecord * rec = container_of(pos, struct EvtRecord, item);

        q->forceDiscardCbk(rec->evtType, rec->evtData, rec->evtFreeData);
        slabAllocatorFree(q->evtsSlab, rec);
    }

    slabAllocatorDestroy(q->evtsSlab);
    heapFree(q);
}

bool evtQueueEnqueue(struct EvtQueue* q, uint32_t evtType, void *evtData,
                    TaggedPtr evtFreeData, bool atFront)
{
    struct EvtRecord *rec;
    uint64_t intSta;
    struct EvtList *item = NULL, *a, *b;

    if (!q)
        return false;

    rec = slabAllocatorAlloc(q->evtsSlab);
    if (!rec) {
        struct EvtList *pos;

        intSta = cpuIntsOff();
        //find a victim for discarding
        for (pos = q->head.next; pos != &q->head; pos = pos->next) {
            rec = container_of(pos, struct EvtRecord, item);
            if (!(rec->evtType & EVENT_TYPE_BIT_DISCARDABLE))
                continue;
            q->forceDiscardCbk(rec->evtType, rec->evtData, rec->evtFreeData);
            evtListDel(pos);
            item = pos;
        }
        cpuIntsRestore (intSta);
    } else {
        item = &rec->item;
    }

    if (!item)
        return false;

    item->prev = item->next = NULL;

    rec->evtType = evtType;
    rec->evtData = evtData;
    rec->evtFreeData = evtFreeData;

    intSta = cpuIntsOff();

    if (unlikely(atFront)) {
        b = q->head.next;
        a = b->prev;
    } else {
        a = q->head.prev;
        b = a->next;
    }

    a->next = item;
    item->prev = a;
    b->prev = item;
    item->next = b;

    cpuIntsRestore(intSta);
    platWake();
    return true;
}

void evtQueueRemoveAllMatching(struct EvtQueue* q,
                               bool (*match)(uint32_t evtType, const void *data, void *context),
                               void *context)
{
    uint64_t intSta = cpuIntsOff();
    struct EvtList *pos, *tmp;

    for_each_item_safe (&q->head, pos, tmp) {
        struct EvtRecord * rec = container_of(pos, struct EvtRecord, item);

        if (match(rec->evtType, rec->evtData, context)) {
            q->forceDiscardCbk(rec->evtType, rec->evtData, rec->evtFreeData);
            evtListDel(pos);
            slabAllocatorFree(q->evtsSlab, rec);
        }
    }
    cpuIntsRestore(intSta);
}

bool evtQueueDequeue(struct EvtQueue* q, uint32_t *evtTypeP, void **evtDataP,
                     TaggedPtr *evtFreeDataP, bool sleepIfNone)
{
    struct EvtRecord *rec = NULL;
    uint64_t intSta;

    while(1) {
        struct EvtList *pos;
        intSta = cpuIntsOff();

        pos = q->head.next;
        if (pos != &q->head) {
            rec = container_of(pos, struct EvtRecord, item);
            evtListDel(pos);
            break;
        }
        else if (!sleepIfNone)
            break;
        else if (!timIntHandler()) {
            // check for timers
            // if any fire, do not sleep (since by the time callbacks run, more might be due)
            platSleep();
            //first thing when awake: check timers again
            timIntHandler();
        }
        cpuIntsRestore(intSta);
    }

    cpuIntsRestore(intSta);

    if (!rec)
        return false;

    *evtTypeP = rec->evtType;
    *evtDataP = rec->evtData;
    *evtFreeDataP = rec->evtFreeData;
    slabAllocatorFree(q->evtsSlab, rec);

    return true;
}
