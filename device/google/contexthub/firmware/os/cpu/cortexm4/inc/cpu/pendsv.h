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

#ifndef _CM4F_PENDSV_H_
#define _CM4F_PENDSV_H_

#include <stdbool.h>
#include <stdint.h>

#define MAX_PENDSV_SUBSCRIBERS	4

struct PendsvRegsLow {
    uint32_t r0, r1, r2, r3;
    uint32_t r12, lr, pc, cpsr;
};

struct PendsvRegsHi {
    uint32_t r4, r5, r6, r7;
    uint32_t r8, r9, r10, r11;
};

typedef void (*PendsvCallbackF)(struct PendsvRegsLow *loRegs, struct PendsvRegsHi *hiRegs);

bool pendsvSubscribe(PendsvCallbackF cbk);	//may not be interrupt safe if reentered
bool pendsvUnsubscribe(PendsvCallbackF cbk);
void pendsvTrigger(void);
void pendsvClear(void);
bool pendsvIsPending(void);


#endif

