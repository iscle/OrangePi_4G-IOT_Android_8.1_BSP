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

#include <atomic.h>

uint32_t atomicAdd32bits(volatile uint32_t *val, uint32_t addend)
{
    uint32_t old;

    do {
        old = *val;
    } while (!atomicCmpXchg32bits(val, old, old + addend));

    return old;
}

uint32_t atomicAddByte(volatile uint8_t *val, uint32_t addend)
{
    uint8_t old;

    do {
        old = *val;
    } while (!atomicCmpXchgByte(val, old, old + addend));

    return old;
}

uint32_t atomicXchgByte(volatile uint8_t *byte, uint32_t newVal)
{
    return __atomic_exchange_n(byte, newVal, __ATOMIC_ACQ_REL);
/*
    uint32_t oldVal;

    asm volatile(
        "    xchgb %1, %0     \n"
        :"=r"(oldVal), "+m"(*byte)
        :"0"(newVal)
        :"memory"
       );

    return oldVal;
*/
}

uint32_t atomicXchg32bits(volatile uint32_t *word, uint32_t newVal)
{
    return __atomic_exchange_n(word, newVal, __ATOMIC_ACQ_REL);
/*
    uint32_t oldVal;

    asm volatile(
        "    xchgl %1, %0     \n"
        :"=r"(oldVal), "+m"(*byte)
        :"0"(newVal)
        :"memory"
       );

    return oldVal;
*/
}

bool atomicCmpXchgByte(volatile uint8_t *byte, uint32_t prevVal, uint32_t newVal)
{
    return __sync_bool_compare_and_swap (byte, prevVal, newVal);
/*
    uint32_t ret;

    asm volatile(
        "    lock cmpxchgb %2, %1     \n"
        :"=a"(ret), "+m"(*byte)
        :"r"(newVal), "0"(prevVal)
        :"cc", "memory"
    );

    return ret == prevVal;
*/
}

bool atomicCmpXchg32bits(volatile uint32_t *word, uint32_t prevVal, uint32_t newVal)
{
    return __sync_bool_compare_and_swap (word, prevVal, newVal);
/*
    uint32_t ret;

    asm volatile(
        "    lock cmpxchgl %2, %1     \n"
        :"=a"(ret), "+m"(*word)
        :"r"(newVal), "0"(prevVal)
        :"cc", "memory"
    );

    return ret == prevVal;
*/
}
