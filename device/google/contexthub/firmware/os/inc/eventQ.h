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

#ifndef _EVENT_Q_H_
#define _EVENT_Q_H_


#include <stdbool.h>
#include <stdint.h>
#include <plat/taggedPtr.h>


#define EVENT_TYPE_BIT_DISCARDABLE_COMPAT    0x80000000 /* some external apps are using this one */
#define EVENT_TYPE_BIT_DISCARDABLE               0x8000 /* set for events we can afford to lose */

struct EvtQueue;

typedef void (*EvtQueueForciblyDiscardEvtCbkF)(uint32_t evtType, void *evtData, TaggedPtr evtFreeData);

//multi-producer, SINGLE consumer queue

struct EvtQueue* evtQueueAlloc(uint32_t size, EvtQueueForciblyDiscardEvtCbkF forceDiscardCbk);
void evtQueueFree(struct EvtQueue* q);
bool evtQueueEnqueue(struct EvtQueue* q, uint32_t evtType, void *evtData, TaggedPtr evtFreeData, bool atFront /* do not set this unless you know the repercussions. read: never set this in new code */);
bool evtQueueDequeue(struct EvtQueue* q, uint32_t *evtTypeP, void **evtDataP, TaggedPtr *evtFreeDataP, bool sleepIfNone);
void evtQueueRemoveAllMatching(struct EvtQueue* q,  bool (*match)(uint32_t evtType, const void *data, void *context), void *context);

#endif
