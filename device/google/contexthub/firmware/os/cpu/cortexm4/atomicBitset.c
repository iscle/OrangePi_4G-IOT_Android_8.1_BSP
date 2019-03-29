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

#include <stdint.h>
#include <string.h>
#include <atomicBitset.h>
#include <atomic.h>


void atomicBitsetInit(struct AtomicBitset *set, uint32_t numBits)
{
    set->numBits = numBits;
    memset(set->words, 0, sizeof(uint32_t) * ATOMIC_BITSET_NUM_WORDS(numBits));
    if (numBits & 31) //mark all high bits so that atomicBitsetFindClearAndSet() is simpler
        set->words[numBits / 32] = ((uint32_t)((int32_t)-1LL)) << (numBits & 31);
}

uint32_t atomicBitsetGetNumBits(const struct AtomicBitset *set)
{
    return set->numBits;
}

bool atomicBitsetGetBit(const struct AtomicBitset *set, uint32_t num)
{
    if (num >= set->numBits) /* any value is as good as the next */
        return false;

    return !!((set->words[num / 32]) & (1UL << (num & 31)));
}

void atomicBitsetClearBit(struct AtomicBitset *set, uint32_t num)
{
    uint32_t idx = num / 32, mask = 1UL << (num & 31), status, tmp;
    uint32_t *wordPtr = set->words + idx;

    if (num >= set->numBits)
        return;

    do {
        asm volatile(
            "    ldrex %0, [%2]       \n"
            "    bics  %0, %3         \n"
            "    strex %1, %0, [%2]   \n"
            :"=r"(tmp), "=r"(status), "=r"(wordPtr), "=r"(mask)
            :"2"(wordPtr), "3"(mask)
            :"cc","memory"
        );
    } while (status);
}

void atomicBitsetSetBit(struct AtomicBitset *set, uint32_t num)
{
    uint32_t idx = num / 32, mask = 1UL << (num & 31), status, tmp;
    uint32_t *wordPtr = set->words + idx;

    if (num >= set->numBits)
        return;

    do {
        asm volatile(
            "    ldrex %0, [%2]       \n"
            "    orrs  %0, %3         \n"
            "    strex %1, %0, [%2]   \n"
            :"=r"(tmp), "=r"(status), "=r"(wordPtr), "=r"(mask)
            :"2"(wordPtr), "3"(mask)
            :"cc","memory"
        );
    } while (status);
}

int32_t atomicBitsetFindClearAndSet(struct AtomicBitset *set)
{
    uint32_t idx, numWords = ATOMIC_BITSET_NUM_WORDS(set->numBits);
    uint32_t scratch1, scratch2, scratch3, bit = 32;
    uint32_t *wordPtr = set->words;

    for (idx = 0; idx < numWords; idx++, wordPtr++) {
        asm volatile(
            "1:                       \n"
            "    ldrex %0, [%4]       \n"
            "    mvns  %3, %0         \n"
            "    beq   1f             \n"
            "    clz   %1, %3         \n"
            "    rsb   %1, #31        \n"
            "    lsl   %3, %2, %1     \n"
            "    orrs  %0, %3         \n"
            "    strex %3, %0, [%4]   \n"
            "    cbz   %3, 1f         \n"
            "    movs  %1, #32        \n"
            "    b     1b             \n"
            "1:                       \n"
            :"=r"(scratch1), "=r"(bit), "=r"(scratch2), "=l"(scratch3), "=r"(wordPtr)
            :"1"(32), "2"(1), "4"(wordPtr)
            :"cc", "memory"
        );

        if (bit != 32)
            return (idx * 32) + bit;
    }

    return -1;
}

bool atomicBitsetXchg(struct AtomicBitset *atomicallyAccessedSet, struct AtomicBitset *otherSet)
{
    uint32_t idx, numWords = ATOMIC_BITSET_NUM_WORDS(atomicallyAccessedSet->numBits);

    if (atomicallyAccessedSet->numBits != otherSet->numBits)
        return false;

    for (idx = 0; idx < numWords; idx++)
        otherSet->words[idx] = atomicXchg32bits(&atomicallyAccessedSet->words[idx], otherSet->words[idx]);

    return true;
}

bool atomicBitsetBulkRead(struct AtomicBitset *set, uint32_t *dest, uint32_t numBits)
{
    uint32_t idx, numWords = ATOMIC_BITSET_NUM_WORDS(set->numBits);

    if (set->numBits != numBits)
        return false;

    for (idx = 0; idx < numWords; idx++)
        dest[idx] = atomicRead32bits(&set->words[idx]);

    return true;
}
