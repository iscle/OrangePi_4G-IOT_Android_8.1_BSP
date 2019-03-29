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

#ifndef __UTIL_H
#define __UTIL_H
#include <plat/plat.h>
#include "toolchain.h"
#include <limits.h>
#include <stdbool.h>
#include <stddef.h>

#define ARRAY_SIZE(a)   (sizeof((a)) / sizeof((a)[0]))

#define UNUSED_PARAM(param) (void) (param)

#ifndef alignof
#define alignof(type) offsetof(struct { char x; type field; }, field)
#endif
/*
 * this macro does not consume bytes from any segment; if condition is true it is a zero-sized array;
 * if some compilers do try to allocate space for them we create a special section for those checks
 * and discard this section at link time
 */
#define C_STATIC_ASSERT(name, condition) \
    static const char __attribute__((used, section(".static_assert"))) \
    static_assert_check_ ## name [(condition) ? 0 : -1]

#define unlikely(x) (x)
#define likely(x) (x)

#define container_of(addr, struct_name, field_name) \
    ((struct_name *)((char *)(addr) - offsetof(struct_name, field_name)))

static inline bool IS_POWER_OF_TWO(unsigned int n)
{
    return !(n & (n - 1));
}

static inline int LOG2_FLOOR(unsigned int n)
{
    if (UNLIKELY(n == 0))
        return INT_MIN;

    // floor(log2(n)) = MSB(n) = (# of bits) - (# of leading zeros) - 1
    return 8 * sizeof(n) - CLZ(n) - 1;
}

static inline int LOG2_CEIL(unsigned int n)
{
    return IS_POWER_OF_TWO(n) ? LOG2_FLOOR(n) : LOG2_FLOOR(n) + 1;
}

#ifdef SYSCALL_VARARGS_PARAMS_PASSED_AS_PTRS
  #define VA_LIST_TO_INTEGER(__vl)  ((uintptr_t)(&__vl))
  #define INTEGER_TO_VA_LIST(__ptr)  (*(va_list*)(__ptr))
#else
  #define VA_LIST_TO_INTEGER(__vl)  (*(uintptr_t*)(&__vl))
  #define INTEGER_TO_VA_LIST(__ptr)  ({uintptr_t tmp = __ptr; (*(va_list*)(&tmp)); })
#endif

#endif /* __UTIL_H */
