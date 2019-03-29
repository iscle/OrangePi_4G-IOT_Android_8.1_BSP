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

#ifndef _SIMPLE_Q_H_
#define _SIMPLE_Q_H_


#include <stdbool.h>
#include <stdint.h>


#define SIMPLE_QUEUE_MAX_ELEMENTS 0x0FFFFFFE

typedef bool (*SimpleQueueForciblyDiscardCbkF)(void *data, bool onDelete); //return false to reject

//SINGLE producer, SINGLE consumer queue. data is copied INTO/OUT of the queue by simpleQueueEnqueue/simpleQueueDequeue

struct SimpleQueue* simpleQueueAlloc(uint32_t numEntries, uint32_t entrySz, SimpleQueueForciblyDiscardCbkF forceDiscardCbk);
void simpleQueueDestroy(struct SimpleQueue* sq); //will call discard, but in no particular order!
bool simpleQueueEnqueue(struct SimpleQueue* sq, const void *data, int length, bool possiblyDiscardable);
bool simpleQueueDequeue(struct SimpleQueue* sq, void *dataVal);


#endif

