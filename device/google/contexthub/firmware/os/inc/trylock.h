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

#ifndef _CM4F_TRYLOCK_H_
#define _CM4F_TRYLOCK_H_

#include <stdint.h>
#include <stdbool.h>

#ifdef PLATFORM_HAS_OWN_TRYLOCK
#include <plat/trylock.h>
#else

struct TryLock {
    volatile uint8_t lock;
};

#define TRYLOCK_DECL_STATIC(name)   struct TryLock name
#define TRYLOCK_INIT_STATIC()       {0}

void trylockInit(struct TryLock *lock);
void trylockRelease(struct TryLock *lock);
bool trylockTryTake(struct TryLock *lock); //true if we took it

/* DON'T YOU EVER DARE TO TRY AND IMPLEMENT A BLOCKING "TAKE" ON THIS TYPE OF LOCK!   -dmitrygr@ */

#endif //PLATFORM_HAS_OWN_TRYLOCK

#endif

