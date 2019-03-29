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

#include <apInt.h>
#include <gpio.h>
#include <variant/variant.h>
#include <plat/gpio.h>
#include <seos.h>
#include <platform.h>
#include <plat/plat.h>

static struct Gpio *apIntWkup;
#ifdef AP_INT_NONWAKEUP
static struct Gpio *apIntNonWkup;
#endif

void apIntInit()
{
    apIntWkup = gpioRequest(AP_INT_WAKEUP);
    gpioConfigOutput(apIntWkup, GPIO_SPEED_LOW, GPIO_PULL_NONE, GPIO_OUT_PUSH_PULL, 1);

#ifdef AP_INT_NONWAKEUP
    apIntNonWkup = gpioRequest(AP_INT_NONWAKEUP);
    gpioConfigOutput(apIntNonWkup, GPIO_SPEED_LOW, GPIO_PULL_NONE, GPIO_OUT_PUSH_PULL, 1);
#endif
}

void apIntSet(bool wakeup)
{
    if (wakeup) {
        platRequestDevInSleepMode(Stm32sleepWakeup, 12);
        gpioSet(apIntWkup, 0);
    }
#ifdef AP_INT_NONWAKEUP
    else
        gpioSet(apIntNonWkup, 0);
#endif
}

void apIntClear(bool wakeup)
{
    if (wakeup) {
        platReleaseDevInSleepMode(Stm32sleepWakeup);
        gpioSet(apIntWkup, 1);
    }
#ifdef AP_INT_NONWAKEUP
    else
        gpioSet(apIntNonWkup, 1);
#endif
}
