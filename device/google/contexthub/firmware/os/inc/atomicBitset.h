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

#ifndef _ATOMIC_BITSET_H_
#define _ATOMIC_BITSET_H_

#include <stdint.h>
#include <stdbool.h>
#include <cpu/atomicBitset.h>

struct AtomicBitset;

//static size calc:
//	ATOMIC_BITSET_SZ(numbits)
//static alloc
//      ATOMIC_BITSET_DECL(nam, numbits, [static]);
//dynamic init:
//	uint32_t sz = atomicBitsetSize(uint32_t numBits);
//	struct AtomicBitset *set = (struct AtomicBitset*)heapAlloc(sz);
//	atomicBitsetInit(set, numBits);


void atomicBitsetInit(struct AtomicBitset *set, uint32_t numBits); //inited state is all zeroes
uint32_t atomicBitsetGetNumBits(const struct AtomicBitset *set);
bool atomicBitsetGetBit(const struct AtomicBitset *set, uint32_t num);
void atomicBitsetClearBit(struct AtomicBitset *set, uint32_t num);
void atomicBitsetSetBit(struct AtomicBitset *set, uint32_t num);

//find a clear bit and set it atomically.
// returns bit number or negative if none.
// only one pass is attempted so if index 0 is cleared after we've looked at it, too bad
int32_t atomicBitsetFindClearAndSet(struct AtomicBitset *set);

//swap the bitsets in atomicallyAccessedSet and otherSet
// returns false if the size of the bitsets are different.
// otherwise atomically copies the bitset in otherSet into atomicallyAccessedSet
// and returns the previous value of atomicallyAccessedSet in otherSet
// NOTE: the copy back to otherSet is not atomic
bool atomicBitsetXchg(struct AtomicBitset *atomicallyAccessedSet, struct AtomicBitset *otherSet);

//read the bits in set into an array of uint32_t's
// returns false if the number of bits in set is not numBits
// otherwise atomically read a uint32_t at a time from the bitset in set into
// dest
// NOTE: if bitset is not a multiple of 32, the remaining bits up to 32 in the
// last uint32_t will contain undefined values
bool atomicBitsetBulkRead(struct AtomicBitset *set, uint32_t *dest, uint32_t numBits);

#endif

