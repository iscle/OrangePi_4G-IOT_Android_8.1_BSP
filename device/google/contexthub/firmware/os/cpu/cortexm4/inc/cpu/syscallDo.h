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

#ifndef _CM4F_SYSCALL_DO_H_
#define _CM4F_SYSCALL_DO_H_


#ifdef __cplusplus
extern "C" {
#endif

#ifdef _OS_BUILD_
    #error "Syscalls should not be called from OS code"
#endif


#include <stdint.h>
#include <stdarg.h>

#if defined(__ICCARM__)

#pragma swi_number=0
__swi uintptr_t cpuSyscallDo(uint32_t syscallNo, uint32_t vl);

#elif defined(__GNUC__)

#define cpuSyscallDo(syscallNo, vaListPtr)                     \
    ({                                                         \
        register uint32_t _r0 asm("r0") = syscallNo;           \
        register uint32_t _r1 asm("r1") = (uint32_t)vaListPtr; \
        asm volatile (                                         \
            "swi 0      \n"                                    \
            :"=r"(_r0), "=r"(_r1)                              \
            :"0"(_r0), "1"(_r1)                                \
            :"memory"                                          \
        );                                                     \
        _r0;                                                   \
    })

/* these are specific to arm-eabi-32-le-gcc ONLY. YMMV otherwise */

#define cpuSyscallDo0P(syscallNo)                    \
    ({                                               \
        register uint32_t _r0 asm("r0") = syscallNo; \
        asm volatile (                               \
            "swi 0      \n"                          \
            :"=r"(_r0)                               \
            :"0"(_r0)                                \
            :"memory"                                \
        );                                           \
        _r0;                                         \
    })


#define cpuSyscallDo1P(syscallNo, p1)                  \
    ({                                                 \
        register uint32_t _r0 asm("r0") = syscallNo;   \
        register uint32_t _r1 asm("r1") = (uint32_t)p1;\
        asm volatile (                                 \
            "swi 1      \n"                            \
            :"=r"(_r0), "=r"(_r1)                      \
            :"0"(_r0), "1"(_r1)                        \
            :"memory"                                  \
        );                                             \
        _r0;                                           \
    })



#define cpuSyscallDo2P(syscallNo, p1, p2)              \
    ({                                                 \
        register uint32_t _r0 asm("r0") = syscallNo;   \
        register uint32_t _r1 asm("r1") = (uint32_t)p1;\
        register uint32_t _r2 asm("r2") = (uint32_t)p2;\
        asm volatile (                                 \
            "swi 1      \n"                            \
            :"=r"(_r0), "=r"(_r1), "=r"(_r2)           \
            :"0"(_r0), "1"(_r1), "2"(_r2)              \
            :"memory"                                  \
        );                                             \
        _r0;                                           \
    })

#define cpuSyscallDo3P(syscallNo, p1, p2, p3)          \
    ({                                                 \
        register uint32_t _r0 asm("r0") = syscallNo;   \
        register uint32_t _r1 asm("r1") = (uint32_t)p1;\
        register uint32_t _r2 asm("r2") = (uint32_t)p2;\
        register uint32_t _r3 asm("r3") = (uint32_t)p3;\
        asm volatile (                                 \
            "swi 1      \n"                            \
            :"=r"(_r0), "=r"(_r1), "=r"(_r2), "=r"(_r3)\
            :"0"(_r0), "1"(_r1), "2"(_r2), "3"(_r3)    \
            :"memory"                                  \
        );                                             \
        _r0;                                           \
    })

#define cpuSyscallDo4P(syscallNo, p1, p2, p3, p4)                   \
    ({                                                              \
        register uint32_t _r0 asm("r0") = syscallNo;                \
        register uint32_t _r1 asm("r1") = (uint32_t)p1;             \
        register uint32_t _r2 asm("r2") = (uint32_t)p2;             \
        register uint32_t _r3 asm("r3") = (uint32_t)p3;             \
        register uint32_t _r12 asm("r12") = (uint32_t)p4;           \
        asm volatile (                                              \
            "swi 1      \n"                                         \
            :"=r"(_r0), "=r"(_r1), "=r"(_r2), "=r"(_r3), "=r"(_r12) \
            :"0"(_r0), "1"(_r1), "2"(_r2), "3"(_r3), "4"(_r12)      \
            :"memory"                                               \
        );                                                          \
        _r0;                                                        \
    })

#endif


#ifdef __cplusplus
}
#endif

#endif

