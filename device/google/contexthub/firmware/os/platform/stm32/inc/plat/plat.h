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

#ifndef _STM32_PLAT_H_
#define _STM32_PLAT_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stdbool.h>
#include <seos.h>

enum PlatSleepDevID
{
    Stm32sleepDevTim2, /* we use this for short sleeps in WFI mode */
    Stm32sleepDevTim4, /* input capture uses this, potentially */
    Stm32sleepDevTim5, /* input capture uses this, potentially */
    Stm32sleepDevTim9, /* input capture uses this, potentially */
    Stm32sleepWakeup,  /* we use this to wakeup from AP */
    Stm32sleepDevSpi2, /* we use this to prevent stop mode during spi2 xfers */
    Stm32sleepDevSpi3, /* we use this to prevent stop mode during spi3 xfers */
    Stm32sleepDevI2c1, /* we use this to prevent stop mode during i2c1 xfers */
    Stm32sleepDevI2c2, /* we use this to prevent stop mode during i2c2 xfers */
    Stm32sleepDevI2c3, /* we use this to prevent stop mode during i2c3 xfers */
    Stm32sleepDevExti, /* we use this for max external interrupt latency */

    Stm32sleepDevNum,  //must be last always, and must be <= PLAT_MAX_SLEEP_DEVS
};

static inline const struct AppHdr* platGetInternalAppList(uint32_t *numAppsP)
{
    extern const struct AppHdr __internal_app_start, __internal_app_end;

    *numAppsP = &__internal_app_end - &__internal_app_start;
    return &__internal_app_start;
}

static inline uint8_t* platGetSharedAreaInfo(uint32_t *areaSzP)
{
    extern uint8_t __shared_start[];
    extern uint8_t __shared_end[];

    *areaSzP = __shared_end - __shared_start;
    return __shared_start;
}

//used for dropbox
void* platGetPersistentRamStore(uint32_t *bytes);

static inline void platWake(void)
{
}

// GCC aligns 64-bit types on an 8-byte boundary, but Cortex-M4 only requires
// 4-byte alignment for these types. So limit the return value, as we're
// interested in the minimum alignment requirement.
#if defined(__GNUC__) && !defined(alignof)
#define alignof(type) ((__alignof__(type) > 4) ? 4 : __alignof__(type))
#endif

#ifdef __cplusplus
}
#endif

#endif
