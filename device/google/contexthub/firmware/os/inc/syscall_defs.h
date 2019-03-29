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

#ifndef _INC_SYSCALL_DEFS_H_
#define _INC_SYSCALL_DEFS_H_

#include <cpu/syscallDo.h>

/* it is always safe to use this, but using syscallDo0P .. syscallDo4P macros may produce faster code for free */
static inline uintptr_t syscallDoGeneric(uint32_t syscallNo, ...)
{
    uintptr_t ret;
    va_list vl;

    va_start(vl, syscallNo);
    #ifdef SYSCALL_PARAMS_PASSED_AS_PTRS
        ret = cpuSyscallDo(syscallNo, &vl);
    #else
        ret = cpuSyscallDo(syscallNo, *(uint32_t*)&vl);
    #endif
    va_end(vl);

    return ret;
}

#ifdef cpuSyscallDo0P
    #define syscallDo0P(syscallNo) cpuSyscallDo0P(syscallNo)
#else
    #define syscallDo0P(syscallNo) syscallDoGeneric(syscallNo)
#endif

#ifdef cpuSyscallDo1P
    #define syscallDo1P(syscallNo,p1) cpuSyscallDo1P(syscallNo,p1)
#else
    #define syscallDo1P(syscallNo,p1) syscallDoGeneric(syscallNo,p1)
#endif

#ifdef cpuSyscallDo2P
    #define syscallDo2P(syscallNo,p1,p2) cpuSyscallDo2P(syscallNo,p1,p2)
#else
    #define syscallDo2P(syscallNo,p1,p2) syscallDoGeneric(syscallNo,p1,p2)
#endif

#ifdef cpuSyscallDo3P
    #define syscallDo3P(syscallNo,p1,p2,p3) cpuSyscallDo3P(syscallNo,p1,p2,p3)
#else
    #define syscallDo3P(syscallNo,p1,p2,p3) syscallDoGeneric(syscallNo,p1,p2,p3)
#endif

#ifdef cpuSyscallDo4P
    #define syscallDo4P(syscallNo,p1,p2,p3,p4) cpuSyscallDo4P(syscallNo,p1,p2,p3,p4)
#else
    #define syscallDo4P(syscallNo,p1,p2,p3,p4) syscallDoGeneric(syscallNo,p1,p2,p3,p4)
#endif

#ifdef cpuSyscallDo5P
    #define syscallDo5P(syscallNo,p1,p2,p3,p4,p5) cpuSyscallDo5P(syscallNo,p1,p2,p3,p4,p5)
#else
    #define syscallDo5P(syscallNo,p1,p2,p3,p4,p5) syscallDoGeneric(syscallNo,p1,p2,p3,p4,p5)
#endif

#ifdef cpuSyscallDo6P
    #define syscallDo6P(syscallNo,p1,p2,p3,p4,p5,p6) cpuSyscallDo5P(syscallNo,p1,p2,p3,p4,p5,p6)
#else
    #define syscallDo6P(syscallNo,p1,p2,p3,p4,p5,p6) syscallDoGeneric(syscallNo,p1,p2,p3,p4,p5,p6)
#endif

#endif /* _INC_SYSCALL_DEFS_H_ */
