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

#ifndef __SENSORS_PRIV_H__
#define __SENSORS_PRIV_H__

#include <inttypes.h>
#include <seos.h>

struct Sensor {
    const struct SensorInfo *si;
    uint32_t handle;         /* here 0 means invalid */
    uint64_t currentLatency; /* here 0 means no batching */
    uint32_t currentRate;    /* here 0 means off */
    TaggedPtr callInfo;      /* pointer to ops struct or app tid */
    void *callData;
    uint32_t initComplete:1; /* sensor finished initializing */
    uint32_t hasOnchange :1; /* sensor supports onchange and wants to be notified to send new clients current state */
    uint32_t hasOndemand :1; /* sensor supports ondemand and wants to get triggers */
};

struct SensorsInternalEvent {
    union {
        struct {
            uint32_t handle;
            uint32_t value1;
            uint64_t value2;
        };
        struct SensorRateChangeEvent rateChangeEvt;
        struct SensorPowerEvent externalPowerEvt;
        struct SensorSetRateEvent externalSetRateEvt;
        struct SensorCfgDataEvent externalCfgDataEvt;
        struct SensorSendDirectEventEvent externalSendDirectEvt;
        struct SensorMarshallUserEventEvent externalMarshallEvt;
    };
};

struct SensorsClientRequest {
    uint32_t handle;
    uint32_t clientTid;
    uint64_t latency;
    uint32_t rate;
};

#define MAX_INTERNAL_EVENTS       32 //also used for external app sensors' setRate() calls
#define MAX_CLI_SENS_MATRIX_SZ    64 /* MAX(numClients * numSensors) */

#define SENSOR_RATE_OFF           UINT32_C(0x00000000) /* used in sensor state machine */
#define SENSOR_RATE_POWERING_ON   UINT32_C(0xFFFFFFF0) /* used in sensor state machine */
#define SENSOR_RATE_POWERING_OFF  UINT32_C(0xFFFFFFF1) /* used in sensor state machine */
#define SENSOR_RATE_FW_UPLOADING  UINT32_C(0xFFFFFFF2) /* used in sensor state machine */
#define SENSOR_RATE_IMPOSSIBLE    UINT32_C(0xFFFFFFF3) /* used in rate calc to indicate impossible combinations */
#define SENSOR_LATENCY_INVALID    UINT64_C(0xFFFFFFFFFFFFFFFF)

#define HANDLE_TO_TID(handle) (((handle) >> (32 - TASK_TID_BITS)) & TASK_TID_MASK)
#define EXT_APP_TID(s) HANDLE_TO_TID(s->handle)
#define LOCAL_APP_OPS(s) ((const struct SensorOps*)taggedPtrToPtr(s->callInfo))
#define IS_LOCAL_APP(s) (taggedPtrIsPtr(s->callInfo))

struct Sensor* sensorFindByHandle(uint32_t handle);

#endif // __SENSORS_PRIV_H__
