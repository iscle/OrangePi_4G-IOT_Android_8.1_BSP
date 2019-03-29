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
#include <stdlib.h>
#include <crt_priv.h>

static void callVectors(const void *from_addr, const void *to_addr)
{
    typedef void (* const callVect)(void);
    callVect *start = (callVect *)from_addr;
    callVect *end = (callVect *)to_addr;
    const int32_t step = from_addr < to_addr ? 1 : -1;
    const int32_t count = step > 0 ? end - start : start - end;

    // basic sanity check
    if (&start[step * count] != end)
        return;

    for (; start != end; start += step) {
        callVect vec = *start;
        if (vec != NULL)
            vec();
    }
}

void __crt_init(void)
{
    extern uint32_t __init_array_start[];
    extern uint32_t __init_array_end[];

    callVectors(__init_array_start, __init_array_end);
}

void __crt_exit(void)
{
    extern uint32_t __fini_array_start[];
    extern uint32_t __fini_array_end[];
    extern uint32_t __bss_end[];
    extern uint32_t __got_start[];

    // call global destructors
    callVectors(__fini_array_start, __fini_array_end);
    if (&__fini_array_end[1] <= __got_start) {
        // call registered static destructors
        callVectors(__bss_end + __fini_array_end[0] * (sizeof(uint32_t)), __bss_end);
    }
}
