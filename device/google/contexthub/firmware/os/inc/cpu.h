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

#ifndef _CPU_H_
#define _CPU_H_

#ifdef __cplusplus
extern "C" {
#endif


#include <seos.h>
#include <stdint.h>
#include <plat/app.h>


void cpuInit(void);
void cpuInitLate(void);  //console guaranted to be up by now

uint64_t cpuIntsOff(void);
uint64_t cpuIntsOn(void);
void cpuIntsRestore(uint64_t state);

/* app loading, unloading & calling */
bool cpuInternalAppLoad(const struct AppHdr *appHdr, struct PlatAppInfo *platInfo);
bool cpuAppLoad(const struct AppHdr *appHdr, struct PlatAppInfo *platInfo);
void cpuAppUnload(const struct AppHdr *appHdr, struct PlatAppInfo *platInfo);
bool cpuAppInit(const struct AppHdr *appHdr, struct PlatAppInfo *platInfo, uint32_t tid);
void cpuAppEnd(const struct AppHdr *appHdr, struct PlatAppInfo *platInfo);
void cpuAppHandle(const struct AppHdr *appHdr, struct PlatAppInfo *platInfo, uint32_t evtType, const void* evtData);
void cpuAppInvoke(const struct AppHdr *app, struct PlatAppInfo *platInfo,
                  void (*method)(uintptr_t, uintptr_t), uintptr_t arg1, uintptr_t arg2);

/* these default to false, there is CPU_NUM_PERSISTENT_RAM_BITS of them */
bool cpuRamPersistentBitGet(uint32_t which);
void cpuRamPersistentBitSet(uint32_t which, bool on);


#ifdef __cplusplus
}
#endif

#endif

