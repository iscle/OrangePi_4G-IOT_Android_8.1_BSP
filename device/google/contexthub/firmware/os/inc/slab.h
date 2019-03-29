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

#ifndef _SLAB_H_
#define _SLAB_H_

#include <stdint.h>

struct SlabAllocator;



//thread/interrupt safe. allocations will not fail if space exists. even in interrupts.
//itemAlign over 4 will not be guaranteed since the heap does not hand out chunks with that kind of alignment
struct SlabAllocator* slabAllocatorNew(uint32_t itemSz, uint32_t itemAlign, uint32_t numItems);
void slabAllocatorDestroy(struct SlabAllocator *allocator);
void* slabAllocatorAlloc(struct SlabAllocator *allocator);
void slabAllocatorFree(struct SlabAllocator *allocator, void *ptr);

void* slabAllocatorGetNth(struct SlabAllocator *allocator, uint32_t idx); // -> pointer or NULL if that slot is empty   may be not int-safe. YMMV
uint32_t slabAllocatorGetIndex(struct SlabAllocator *allocator, void *ptr); // -> index or -1 if invalid pointer
uint32_t slabAllocatorGetNumItems(struct SlabAllocator *allocator); // simply say hwo many items it can hold max (numItems passed to constructor)

#endif

