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

#include <atomicBitset.h>
#include <stdio.h>
#include <heap.h>
#include <slab.h>

struct SlabAllocator {

    uint32_t itemSz;
    uint8_t *dataChunks;
    struct AtomicBitset bitset[0];
};

struct SlabAllocator* slabAllocatorNew(uint32_t itemSz, uint32_t itemAlign, uint32_t numItems)
{
    struct SlabAllocator *allocator;
    uint32_t bitsetSz, dataSz;

    /* calcualte size */
    bitsetSz = ATOMIC_BITSET_SZ(numItems);
    bitsetSz = ((bitsetSz + itemAlign - 1) / itemAlign) * itemAlign;

    itemSz = ((itemSz + itemAlign - 1) / itemAlign) * itemAlign;
    dataSz = itemSz * numItems;

    /* allocate & init*/
    allocator = (struct SlabAllocator*)heapAlloc(sizeof(struct SlabAllocator) + bitsetSz + dataSz);
    if (allocator) {
        allocator->itemSz = itemSz;
        allocator->dataChunks = ((uint8_t*)allocator->bitset) + bitsetSz;
        atomicBitsetInit(allocator->bitset, numItems);
    }

    return allocator;
}

void slabAllocatorDestroy(struct SlabAllocator *allocator)
{
    heapFree(allocator);
}

void* slabAllocatorAlloc(struct SlabAllocator *allocator)
{
    int32_t itemIdx = atomicBitsetFindClearAndSet(allocator->bitset);

    if (itemIdx < 0)
        return NULL;

    return allocator->dataChunks + allocator->itemSz * itemIdx;
}

void slabAllocatorFree(struct SlabAllocator *allocator, void* ptrP)
{
    uint8_t *ptr = (uint8_t*)ptrP;
    uint32_t itemOffset = ptr - allocator->dataChunks;
    uint32_t itemIdx = itemOffset / allocator->itemSz;

    //check for invalid inputs
    if ((itemOffset % allocator->itemSz) || (itemIdx >= atomicBitsetGetNumBits(allocator->bitset)) || !atomicBitsetGetBit(allocator->bitset, itemIdx))
        return;

    atomicBitsetClearBit(allocator->bitset, itemIdx);
}

void* slabAllocatorGetNth(struct SlabAllocator *allocator, uint32_t idx)
{
    if (!atomicBitsetGetBit(allocator->bitset, idx))
        return NULL;

    return allocator->dataChunks + allocator->itemSz * idx;
}

uint32_t slabAllocatorGetIndex(struct SlabAllocator *allocator, void* ptrP)
{
    uint8_t *ptr = (uint8_t*)ptrP;
    uint32_t itemOffset = ptr - allocator->dataChunks;
    uint32_t itemIdx = itemOffset / allocator->itemSz;

    if ((itemOffset % allocator->itemSz) || (itemIdx >= atomicBitsetGetNumBits(allocator->bitset)) || !atomicBitsetGetBit(allocator->bitset, itemIdx))
        return -1;

    return itemIdx;
}

uint32_t slabAllocatorGetNumItems(struct SlabAllocator *allocator)
{
    return atomicBitsetGetNumBits(allocator->bitset);
}


