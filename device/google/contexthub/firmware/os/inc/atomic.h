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

#ifndef _ATOMIC_H_
#define _ATOMIC_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stdbool.h>

/* almost all platforms support byte and 32-bit operations of this sort. please do not add other sizes here */
uint32_t atomicXchgByte(volatile uint8_t *byte, uint32_t newVal);
uint32_t atomicXchg32bits(volatile uint32_t *word, uint32_t newVal);
bool atomicCmpXchgByte(volatile uint8_t *byte, uint32_t prevVal, uint32_t newVal);
bool atomicCmpXchg32bits(volatile uint32_t *word, uint32_t prevVal, uint32_t newVal);

//returns old value
uint32_t atomicAddByte(volatile uint8_t *byte, uint32_t addend);
uint32_t atomicAdd32bits(volatile uint32_t *word, uint32_t addend);

// pull in inline cpu-specific implementations, if any
#include <cpu/atomic.h>
#include <cpu/barrier.h>

//writes with barriers
static inline uint32_t atomicReadByte(volatile uint8_t *byte)
{
    mem_reorder_barrier();
    return *byte;
}

static inline uint32_t atomicRead32bits(volatile uint32_t *word)
{
    mem_reorder_barrier();
    return *word;
}

static inline void atomicWriteByte(volatile uint8_t *byte, uint32_t val)
{
    *byte = val;
    mem_reorder_barrier();
}

static inline void atomicWrite32bits(volatile uint32_t *word, uint32_t val)
{
    *word = val;
    mem_reorder_barrier();
}

#ifdef __cplusplus
}
#endif

#endif
