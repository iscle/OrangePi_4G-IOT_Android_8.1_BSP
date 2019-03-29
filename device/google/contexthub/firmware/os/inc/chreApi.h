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

#ifndef _CHRE_API_H_
#define _CHRE_API_H_

#include <stdarg.h>
#include <stdint.h>

/* if va_list is passed by value it must fit in 32-bit register */
#if !defined(SYSCALL_PARAMS_PASSED_AS_PTRS) || !defined(SYSCALL_VARARGS_PARAMS_PASSED_AS_PTRS)
C_STATIC_ASSERT(va_list_size, sizeof(va_list) == sizeof(uint32_t));
#endif

/*
 *  many syscalls rely on the property that uintptr_t can hold uint32_t without data loss
 * This is enforced by static assertion below.
 */
C_STATIC_ASSERT(uintptr_size, sizeof(uintptr_t) >= sizeof(uint32_t));

//EXTERNAL API
#define SYSCALL_CHRE_MAIN                 0
#define SYSCALL_CHRE_LAST                 1

#define SYSCALL_CHRE_MAIN_API             0
#define SYSCALL_CHRE_MAIN_LAST            1

#define SYSCALL_CHRE_MAIN_API_GET_APP_ID             0 // (void) -> uint64_t
#define SYSCALL_CHRE_MAIN_API_GET_INST_ID            1 // (void) -> uint32_t
#define SYSCALL_CHRE_MAIN_API_LOG_OLD                2 // (enum LogLevel, const char *, uintptr_t) -> void
#define SYSCALL_CHRE_MAIN_API_GET_TIME               3 // (void) -> uint64_t
#define SYSCALL_CHRE_MAIN_API_TIMER_SET              4 // (uint64_t, const void *, bool) -> uint32_t
#define SYSCALL_CHRE_MAIN_API_TIMER_CANCEL           5 // (uint32_t) -> bool
#define SYSCALL_CHRE_MAIN_API_ABORT                  6 // (uint32_t) -> void
#define SYSCALL_CHRE_MAIN_API_HEAP_ALLOC             7 // (uint32_t) -> void *
#define SYSCALL_CHRE_MAIN_API_HEAP_FREE              8 // (void *) -> void
#define SYSCALL_CHRE_MAIN_API_SEND_EVENT             9 // (uint32_t, void *, chreEventCompleteFunction*, uint32_t) -> bool
#define SYSCALL_CHRE_MAIN_API_SEND_MSG              10 // (void *, uint32_t, uint32_t, chreMessageFreeFunction *) -> bool
#define SYSCALL_CHRE_MAIN_API_SENSOR_FIND_DEFAULT   11 //
#define SYSCALL_CHRE_MAIN_API_SENSOR_GET_INFO       12 //
#define SYSCALL_CHRE_MAIN_API_SENSOR_GET_STATUS     13 //
#define SYSCALL_CHRE_MAIN_API_SENSOR_CONFIG         14 //
#define SYSCALL_CHRE_MAIN_API_GET_OS_API_VERSION    15 //
#define SYSCALL_CHRE_MAIN_API_GET_OS_VERSION        16 //
#define SYSCALL_CHRE_MAIN_API_GET_PLATFORM_ID       17 //
#define SYSCALL_CHRE_MAIN_API_LOG                   18 // (enum LogLevel, const char *, uintptr_t) -> void
#define SYSCALL_CHRE_MAIN_API_LAST                  19 // always last. holes are allowed, but not immediately before this

//called by os entry point to export the api
void osChreApiExport(void);
// release CHRE event and optionally call completion callback
void osChreFreeEvent(uint32_t tid, void (*free_info)(uint16_t, void *), uint32_t evtType, void * evtData);

#endif
