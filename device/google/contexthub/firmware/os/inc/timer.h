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

#ifndef _TIMER_H_
#define _TIMER_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include <stdint.h>


#define MAX_TIMERS  16  /* we *REALLY* do not want these proliferating endlessly, hence a small limit */

struct TimerEvent {
    uint32_t timerId;
    void *data;
};


typedef void (*TimTimerCbkF)(uint32_t timerId, void* data);



uint64_t timGetTime(void);   /* Time since some stable reference point in nanoseconds */

uint32_t timTimerSet(uint64_t length, uint32_t jitterPpm, uint32_t driftPpm, TimTimerCbkF cbk, void* data, bool oneShot); /* return timer id or 0 if failed */
uint32_t timTimerSetAsApp(uint64_t length, uint32_t jitterPpm, uint32_t driftPpm, uint32_t tid, void* data, bool oneShot); /* return timer id or 0 if failed */
uint32_t timTimerSetNew(uint64_t length, const void* data, bool oneShot);
bool timTimerCancel(uint32_t timerId);
bool timTimerCancelEx(uint32_t timerId, bool cancelPending);
int timTimerCancelAll(uint32_t tid);


//called by interrupt routine. ->true if any timers were fired
bool timIntHandler(void);


//init subsystem
void timInit(void);




#ifdef __cplusplus
}
#endif

#endif
