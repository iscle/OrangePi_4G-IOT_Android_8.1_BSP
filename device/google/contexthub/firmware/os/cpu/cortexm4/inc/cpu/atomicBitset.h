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

#ifndef _CM4F_ATOMIC_BITSET_H_
#define _CM4F_ATOMIC_BITSET_H_

#include <stdint.h>
#include <stdbool.h>
#include "toolchain.h"

struct AtomicBitset {
    uint32_t numBits;
    uint32_t words[];
};

#define ATOMIC_BITSET_NUM_WORDS(numbits) (((numbits) + 31) / 32)
#define ATOMIC_BITSET_SZ(numbits) (sizeof(struct AtomicBitset) + sizeof(uint32_t) * ATOMIC_BITSET_NUM_WORDS(numbits))
#define ATOMIC_BITSET_DECL(nam, numbits, extra_keyword)    DECLARE_OS_ALIGNMENT(nam, ATOMIC_BITSET_SZ(numbits), extra_keyword, struct AtomicBitset)

void atomicBitsetInit(struct AtomicBitset *set, uint32_t numBits);
uint32_t atomicBitsetGetNumBits(const struct AtomicBitset *set);
bool atomicBitsetGetBit(const struct AtomicBitset *set, uint32_t num);
void atomicBitsetClearBit(struct AtomicBitset *set, uint32_t num);
int32_t atomicBitsetFindClearAndSet(struct AtomicBitset *set);

#endif


