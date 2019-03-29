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

#ifndef __HOSTINTF_H
#define __HOSTINTF_H

#include <stdint.h>
#include <atomicBitset.h>
#include <sensors.h>
#include "toolchain.h"

/**
 * System-facing hostIntf API
 */

#define HOSTINTF_MAX_INTERRUPTS     256
#define HOSTINTF_SENSOR_DATA_MAX    240

enum HostIntfDataType
{
    HOSTINTF_DATA_TYPE_LOG,
    HOSTINTF_DATA_TYPE_APP_TO_HOST,
    HOSTINTF_DATA_TYPE_RESET_REASON,
    HOSTINTF_DATA_TYPE_APP_TO_SENSOR_HAL,         // for config data upload
};

SET_PACKED_STRUCT_MODE_ON
struct HostIntfDataBuffer
{
    union
    {
        struct
        {
            uint8_t sensType;
            uint8_t length;
            uint8_t dataType;
            uint8_t interrupt;
        };
        uint32_t evtType;
    };
    union
    {
        struct
        {
            uint64_t referenceTime;
            union
            {
                struct SensorFirstSample firstSample;
                struct SingleAxisDataPoint single[HOSTINTF_SENSOR_DATA_MAX / sizeof(struct SingleAxisDataPoint)];
                struct TripleAxisDataPoint triple[HOSTINTF_SENSOR_DATA_MAX / sizeof(struct TripleAxisDataPoint)];
                struct RawTripleAxisDataPoint rawTriple[HOSTINTF_SENSOR_DATA_MAX / sizeof(struct RawTripleAxisDataPoint)];
            };
        };
        uint8_t buffer[sizeof(uint64_t) + HOSTINTF_SENSOR_DATA_MAX];
    };
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

void hostIntfCopyInterrupts(void *dst, uint32_t numBits);
void hostIntfClearInterrupts();
void hostIntfSetInterrupt(uint32_t bit);
bool hostIntfGetInterrupt(uint32_t bit);
void hostIntfClearInterrupt(uint32_t bit);
void hostIntfSetInterruptMask(uint32_t bit);
bool hostIntfGetInterruptMask(uint32_t bit);
void hostIntfClearInterruptMask(uint32_t bit);
void hostIntfPacketFree(void *ptr);
bool hostIntfPacketDequeue(void *ptr, uint32_t *wakeup, uint32_t *nonwakeup);
void hostIntfSetBusy(bool busy);
void hostIntfRxPacket(bool wakeupActive);
void hostIntfTxAck(void *buffer, uint8_t len);

#endif /* __HOSTINTF_H */
