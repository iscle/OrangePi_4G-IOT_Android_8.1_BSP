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

#include <simpleQ.h>
#include <stddef.h>
#include <string.h>
#include <stdio.h>
#include <heap.h>


struct SimpleQueueEntry {
    uint32_t nextIdx     : 31;
    uint32_t discardable :  1;
    uint8_t data[];
};

struct SimpleQueue {
    SimpleQueueForciblyDiscardCbkF discardCbk;
    uint32_t head, tail, num, freeHead, entrySz;
    uint8_t data[];
};

#define SIMPLE_QUEUE_IDX_NONE (SIMPLE_QUEUE_MAX_ELEMENTS + 1)

//no bounds checking
static inline struct SimpleQueueEntry *simpleQueueGetNth(struct SimpleQueue* sq, uint32_t n)
{
    return (struct SimpleQueueEntry*)(sq->data + n * sq->entrySz);
}

static inline uint32_t simpleQueueGetIdx(struct SimpleQueue* sq, const struct SimpleQueueEntry *e)
{
    return (((const uint8_t*)e) - sq->data) / sq->entrySz;
}

struct SimpleQueue* simpleQueueAlloc(uint32_t numEntries, uint32_t entrySz, SimpleQueueForciblyDiscardCbkF forceDiscardCbk)
{
    uint32_t i, sz = sizeof(struct SimpleQueue) + (sizeof(struct SimpleQueueEntry) + entrySz) * numEntries;
    struct SimpleQueue *sq;

    if (numEntries > SIMPLE_QUEUE_MAX_ELEMENTS)
        return NULL;

    sq = heapAlloc(sz);
    if (!sq)
        return NULL;

    memset(sq, 0, sz);

    sq->discardCbk = forceDiscardCbk;
    sq->head = SIMPLE_QUEUE_IDX_NONE;
    sq->tail = SIMPLE_QUEUE_IDX_NONE;
    sq->entrySz = entrySz + sizeof(struct SimpleQueueEntry);
    sq->freeHead = 0;
    sq->num = numEntries;

    //init the freelist
    for (i = 0; i < numEntries - 1; i++)
        simpleQueueGetNth(sq, i)->nextIdx = i + 1;

    simpleQueueGetNth(sq, numEntries - 1)->nextIdx = SIMPLE_QUEUE_IDX_NONE;

    return sq;
}

void simpleQueueDestroy(struct SimpleQueue* sq)
{
    SimpleQueueForciblyDiscardCbkF discard = sq->discardCbk;
    struct SimpleQueueEntry *cur;
    uint32_t i;

    for (i = sq->head; i != SIMPLE_QUEUE_IDX_NONE; i = cur->nextIdx) {
        cur = simpleQueueGetNth(sq, i);
        discard(cur->data, true);
    }

    heapFree(sq);
}

bool simpleQueueDequeue(struct SimpleQueue* sq, void *data)
{
    struct SimpleQueueEntry *e;
    uint32_t head;

    if (!sq || sq->head == SIMPLE_QUEUE_IDX_NONE)
        return false;

    head = sq->head;
    e = simpleQueueGetNth(sq, head);

    sq->head = e->nextIdx;
    if (sq->tail == head)
        sq->tail = SIMPLE_QUEUE_IDX_NONE;

    memcpy(data, e->data, sq->entrySz - sizeof(struct SimpleQueueEntry));

    e->nextIdx = sq->freeHead;
    sq->freeHead = simpleQueueGetIdx(sq, e);

    return true;
}

//if this is called, we need to discard at least one entry. we prefer to discard the oldest item
static struct SimpleQueueEntry* simpleQueueAllocWithDiscard(struct SimpleQueue* sq)
{
    struct SimpleQueueEntry* cur = NULL;
    uint32_t idx, prev = SIMPLE_QUEUE_IDX_NONE;

    for (idx = sq->head; idx != SIMPLE_QUEUE_IDX_NONE; prev=idx, idx = cur->nextIdx) {
        cur = simpleQueueGetNth(sq, idx);

        if (!cur->discardable)
            continue;

        //try to discard it
        if (sq->discardCbk(cur->data, false)) {

            //unlink
            if (prev == SIMPLE_QUEUE_IDX_NONE)
                sq->head = cur->nextIdx;
            else
                simpleQueueGetNth(sq, prev)->nextIdx = cur->nextIdx;
            if (sq->tail == idx)
                sq->tail = prev;

            return cur;
        }
    }

    return NULL;
}

bool simpleQueueEnqueue(struct SimpleQueue* sq, const void *data, int length, bool possiblyDiscardable)
{
    struct SimpleQueueEntry *e = NULL;

    if (length > sq->entrySz - sizeof(struct SimpleQueueEntry))
        return false;

    //first try a simple alloc
    if (sq->freeHead != SIMPLE_QUEUE_IDX_NONE) {
        e = simpleQueueGetNth(sq, sq->freeHead);
        sq->freeHead = e->nextIdx;
    }

    //if no luck, it gets complicated
    if (!e)
        e = simpleQueueAllocWithDiscard(sq);

    //and we may have to give up
    if (!e)
        return false;

    //link it in
    e->nextIdx = SIMPLE_QUEUE_IDX_NONE;
    if (sq->head == SIMPLE_QUEUE_IDX_NONE) // head = none implies tail = none
        sq->head = simpleQueueGetIdx(sq, e);
    else
        simpleQueueGetNth(sq, sq->tail)->nextIdx = simpleQueueGetIdx(sq, e);
    sq->tail = simpleQueueGetIdx(sq, e);

    //fill in the data
    memcpy(e->data, data, length);
    e->discardable = possiblyDiscardable ? 1 : 0;

    return true;
}
