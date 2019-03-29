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

#include <plat/cmsis.h>
#include <cpu/pendsv.h>
#include <stdio.h>


static PendsvCallbackF mSubscribers[MAX_PENDSV_SUBSCRIBERS] = {0,};

bool pendsvSubscribe(PendsvCallbackF cbk)
{
    int32_t i, free = -1;

    //check for dupes and also look fro a free slot
    for (i = 0; i < MAX_PENDSV_SUBSCRIBERS; i++) {
        if (!mSubscribers[i])
            free = i;
        if (mSubscribers[i] == cbk)
            return false;
    }

    //make sure we found a slot
    if (free < 0)
        return false;

    mSubscribers[free] = cbk;
    return true;
}

bool pendsvUnsubscribe(PendsvCallbackF cbk)
{
    uint32_t i;

    for (i = 0; i < MAX_PENDSV_SUBSCRIBERS; i++) {
        if (mSubscribers[i] == cbk) {
            mSubscribers[i] = NULL;
            return true;
        }
    }

    return false;
}

void pendsvTrigger(void)
{
    SCB->ICSR = 1UL << 28;
}

void pendsvClear(void)
{
    SCB->ICSR = 1UL << 27;
}

bool pendsvIsPending(void)
{
    return !!(SCB->ICSR & (1UL << 28));
}

static void __attribute__((used)) pendSvHandleC(struct PendsvRegsLow *loRegs, struct PendsvRegsHi *hiRegs)
{
    uint32_t i;

    for (i = 0; i < MAX_PENDSV_SUBSCRIBERS; i++) {
        if (mSubscribers[i])
            mSubscribers[i](loRegs, hiRegs);
    }
}

void PendSV_Handler(void);
void PendSV_Handler(void)
{
    asm volatile(
        "tst lr, #4         \n"
        "ite eq             \n"
        "mrseq r0, msp      \n"
        "mrsne r0, psp      \n"
        "push  {r4-r11}     \n"
        "mov   r1, sp       \n"
        "b     pendSvHandleC\n"
    );
}




