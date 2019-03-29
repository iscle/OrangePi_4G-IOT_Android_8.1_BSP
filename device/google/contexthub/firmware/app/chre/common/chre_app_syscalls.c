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

#include <stdarg.h>

#include <gpio.h>
#include <osApi.h>
#include <sensors.h>
#include <seos.h>
#include <util.h>

/* CHRE syscalls */
#include <chre.h>
#include <chreApi.h>
#include <syscall.h>
#include <syscall_defs.h>

#define SYSCALL_CHRE_API(name) \
    SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_API, SYSCALL_CHRE_MAIN_API_ ## name)

uint64_t chreGetAppId(void)
{
    uint64_t appId = 0;
    (void)syscallDo1P(SYSCALL_CHRE_API(GET_APP_ID), &appId);
    return appId;
}

uint32_t chreGetInstanceId(void)
{
    return syscallDo0P(SYSCALL_CHRE_API(GET_INST_ID));
}

uint64_t chreGetTime(void) {
    uint64_t time_ns = 0;
    (void)syscallDo1P(SYSCALL_CHRE_API(GET_TIME), &time_ns);
    return time_ns;
}

void chreLog(enum chreLogLevel level, const char *str, ...)
{
    va_list vl;

    va_start(vl, str);
    (void)syscallDo3P(SYSCALL_CHRE_API(LOG), level, str, VA_LIST_TO_INTEGER(vl));
    va_end(vl);
}

uint32_t chreTimerSet(uint64_t duration, const void* cookie, bool oneShot)
{
    uint32_t dur_lo = duration;
    uint32_t dur_hi = duration >> 32;
    return syscallDo4P(SYSCALL_CHRE_API(TIMER_SET), dur_lo, dur_hi, cookie, oneShot);
}

bool chreTimerCancel(uint32_t timerId)
{
    return syscallDo1P(SYSCALL_CHRE_API(TIMER_CANCEL), timerId);
}

void chreAbort(uint32_t abortCode)
{
    (void)syscallDo1P(SYSCALL_CHRE_API(ABORT), abortCode);
}

void* chreHeapAlloc(uint32_t bytes)
{
    return (void *)syscallDo1P(SYSCALL_CHRE_API(HEAP_ALLOC), bytes);
}

void chreHeapFree(void* ptr)
{
    (void)syscallDo1P(SYSCALL_CHRE_API(HEAP_FREE), ptr);
}

bool chreSensorFindDefault(uint8_t sensorType, uint32_t *handle)
{
    return syscallDo2P(SYSCALL_CHRE_API(SENSOR_FIND_DEFAULT), sensorType, handle);
}

bool chreGetSensorInfo(uint32_t sensorHandle, struct chreSensorInfo *info)
{
    return syscallDo2P(SYSCALL_CHRE_API(SENSOR_GET_INFO), sensorHandle, info);
}

bool chreGetSensorSamplingStatus(uint32_t sensorHandle,
                                 struct chreSensorSamplingStatus *status)
{
    return syscallDo2P(SYSCALL_CHRE_API(SENSOR_GET_STATUS), sensorHandle, status);
}

bool chreSensorConfigure(uint32_t sensorHandle,
                         enum chreSensorConfigureMode mode,
                         uint64_t interval, uint64_t latency)
{
    uint32_t interval_lo = interval;
    uint32_t interval_hi = interval >> 32;
    uint32_t latency_lo = latency;
    uint32_t latency_hi = latency >> 32;
    return syscallDo6P(SYSCALL_CHRE_API(SENSOR_CONFIG), sensorHandle, mode,
                       interval_lo, interval_hi, latency_lo, latency_hi);
}

bool chreSendEvent(uint16_t eventType, void *eventData,
                   chreEventCompleteFunction *freeCallback,
                   uint32_t targetInstanceId)
{
    return syscallDo4P(SYSCALL_CHRE_API(SEND_EVENT), eventType, eventData, freeCallback, targetInstanceId);
}

bool chreSendMessageToHost(void *message, uint32_t messageSize,
                           uint32_t reservedMessageType,
                           chreMessageFreeFunction *freeCallback)
{
    return syscallDo4P(SYSCALL_CHRE_API(SEND_MSG), message, messageSize, reservedMessageType, freeCallback);
}

uint32_t chreGetApiVersion(void)
{
    return syscallDo0P(SYSCALL_CHRE_API(GET_OS_API_VERSION));
}

uint32_t chreGetVersion(void)
{
    return syscallDo0P(SYSCALL_CHRE_API(GET_OS_VERSION));
}

uint64_t chreGetPlatformId(void)
{
    uint64_t plat = 0;
    (void)syscallDo1P(SYSCALL_CHRE_API(GET_PLATFORM_ID), &plat);
    return plat;
}
