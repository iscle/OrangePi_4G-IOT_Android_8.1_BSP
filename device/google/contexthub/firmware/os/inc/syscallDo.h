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

#ifndef _SYSCALL_DO_H_
#define _SYSCALL_DO_H_


#ifdef __cplusplus
extern "C" {
#endif

#ifdef _OS_BUILD_
    #error "Syscalls should not be called from OS code"
#endif

#include <cpu/syscallDo.h>
#include <sensors.h>
#include <syscall.h>
#include <stdarg.h>
#include <gpio.h>
#include <osApi.h>
#include <seos.h>
#include <util.h>

#include <syscall_defs.h>


//system syscalls live here
static inline bool eOsEventSubscribe(uint32_t tid, uint32_t evtType)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_EVENTQ, SYSCALL_OS_MAIN_EVTQ_SUBCRIBE), tid, evtType);
}

static inline bool eOsEventUnsubscribe(uint32_t tid, uint32_t evtType)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_EVENTQ, SYSCALL_OS_MAIN_EVTQ_UNSUBCRIBE), tid, evtType);
}

static inline bool eOsEnqueueEvt(uint32_t evtType, void *evtData, uint32_t tidOfWhoWillFreeThisEvent) // tidOfWhoWillFreeThisEvent is likely your TID
{
    return syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_EVENTQ, SYSCALL_OS_MAIN_EVTQ_ENQUEUE), evtType, evtData, tidOfWhoWillFreeThisEvent);
}

static inline bool eOsEnqueueEvtOrFree(uint32_t evtType, void *evtData, EventFreeF evtFreeF, uint32_t tidOfWhoWillFreeThisEvent) // tidOfWhoWillFreeThisEvent is likely your TID
{
    bool success = eOsEnqueueEvt(evtType, evtData, tidOfWhoWillFreeThisEvent);
    if (!success && evtFreeF)
        evtFreeF(evtData);
    return success;
}

static inline bool eOsEnqueuePrivateEvt(uint32_t evtType, void *evtData, uint32_t tidOfWhoWillFreeThisEvent, uint32_t toTid)
{
    return syscallDo4P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_EVENTQ, SYSCALL_OS_MAIN_EVTQ_ENQUEUE_PRIVATE), evtType, evtData, tidOfWhoWillFreeThisEvent, toTid);
}

static inline bool eOsRetainCurrentEvent(TaggedPtr *evtFreeingInfoP)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_EVENTQ, SYSCALL_OS_MAIN_EVTQ_RETAIN_EVT), evtFreeingInfoP);
}

static inline bool eOsFreeRetainedEvent(uint32_t evtType, void *evtData, TaggedPtr *evtFreeingInfoP)
{
    return syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_EVENTQ, SYSCALL_OS_MAIN_EVTQ_FREE_RETAINED), evtType, evtData, evtFreeingInfoP);
}

static inline void eOsLogvInternal(enum LogLevel level, const char *str, uintptr_t args_list)
{
    (void)syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_LOGGING, SYSCALL_OS_MAIN_LOG_LOGV), level, str, args_list);
}

static inline void eOsLogv(enum LogLevel level, const char *str, va_list vl)
{
    eOsLogvInternal(level, str, VA_LIST_TO_INTEGER(vl));
}

static inline void eOsLog(enum LogLevel level, const char *str, ...)
{
    va_list vl;

    va_start(vl, str);
    eOsLogvInternal(level, str, VA_LIST_TO_INTEGER(vl));
    va_end(vl);
}

static inline const struct SensorInfo* eOsSensorSignalInternalEvt(uint32_t handle, uint32_t intEvtNum, uint32_t value1, uint64_t value2)
{
    uint32_t value2_lo = value2;
    uint32_t value2_hi = value2 >> 32;

    return (const struct SensorInfo*)syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_SIGNAL), handle, intEvtNum, value1, value2_lo, value2_hi);
}

static inline uint32_t eOsSensorRegister(const struct SensorInfo *si, uint32_t tid, void *cookie, bool initComplete)
{
    return syscallDo4P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_REG), si, tid, cookie, (int)initComplete);
}

static inline bool eOsSensorUnregister(uint32_t handle)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_UNREG), handle);
}

static inline bool eOsSensorRegisterInitComplete(uint32_t handle)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_REG_INIT_COMP), handle);
}

static inline const struct SensorInfo* eOsSensorFind(uint32_t sensorType, uint32_t idx, uint32_t *handleP)
{
    return (const struct SensorInfo*)syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_FIND), sensorType, idx, handleP);
}

static inline bool eOsSensorRequest(uint32_t clientId, uint32_t sensorHandle, uint32_t rate, uint64_t latency)
{
    uint32_t latency_lo = latency;
    uint32_t latency_hi = latency >> 32;

    return syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_REQUEST), clientId, sensorHandle, rate, latency_lo, latency_hi);
}

static inline bool eOsSensorRequestRateChange(uint32_t clientId, uint32_t sensorHandle, uint32_t newRate, uint64_t newLatency)
{
    uint32_t newLatency_lo = newLatency;
    uint32_t newLatency_hi = newLatency >> 32;

    return syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_RATE_CHG), clientId, sensorHandle, newRate, newLatency_lo, newLatency_hi);
}

static inline bool eOsSensorRelease(uint32_t clientId, uint32_t sensorHandle)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_RELEASE), clientId, sensorHandle);
}

static inline bool eOsSensorTriggerOndemand(uint32_t clientId, uint32_t sensorHandle)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_TRIGGER), clientId, sensorHandle);
}

static inline uint32_t eOsSensorGetCurRate(uint32_t sensorHandle)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_GET_CUR_RATE), sensorHandle);
}

static inline uint64_t eOsSensorGetTime(void)
{
    uint64_t timeNanos;
    syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_GET_TIME), &timeNanos);
    return timeNanos;
}

static inline uint32_t eOsSensorGetReqRate(uint32_t sensorHandle)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SENSOR, SYSCALL_OS_MAIN_SENSOR_GET_REQ_RATE), sensorHandle);
}

static inline uint64_t eOsTimGetTime(void)
{
    uint64_t timeNanos;
    syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_TIME, SYSCALL_OS_MAIN_TIME_GET_TIME), &timeNanos);
    return timeNanos;
}

static inline uint32_t eOsTimTimerSet(uint64_t length, uint32_t jitterPpm, uint32_t driftPpm, uint32_t tid, void* cookie, bool oneShot)
{
    uint32_t lengthLo = length;
    uint32_t lengthHi = length >> 32;

    return syscallDoGeneric(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_TIME, SYSCALL_OS_MAIN_TIME_SET_TIMER), lengthLo, lengthHi, jitterPpm, driftPpm, tid, cookie, (int)oneShot);
}

static inline bool eOsTimTimerCancel(uint32_t timerId)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_TIME, SYSCALL_OS_MAIN_TIME_CANCEL_TIMER), timerId);
}

static inline void* eOsHeapAlloc(uint32_t sz)
{
    return (void*)syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_HEAP, SYSCALL_OS_MAIN_HEAP_ALLOC), sz);
}

static inline void eOsHeapFree(void* ptr)
{
    (void)syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_HEAP, SYSCALL_OS_MAIN_HEAP_FREE), ptr);
}

static inline struct SlabAllocator* eOsSlabAllocatorNew(uint32_t itemSz, uint32_t itemAlign, uint32_t numItems)
{
    return (struct SlabAllocator*)syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SLAB, SYSCALL_OS_MAIN_SLAB_NEW), itemSz, itemAlign, numItems);
}

static inline void eOsSlabAllocatorDestroy(struct SlabAllocator* allocator)
{
    (void)syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SLAB, SYSCALL_OS_MAIN_SLAB_DESTROY), allocator);
}

static inline void* eOsSlabAllocatorAlloc(struct SlabAllocator* allocator)
{
    return (void *)syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SLAB, SYSCALL_OS_MAIN_SLAB_ALLOC), allocator);
}

static inline void eOsSlabAllocatorFree(struct SlabAllocator* allocator, void* ptrP)
{
    (void)syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_SLAB, SYSCALL_OS_MAIN_SLAB_FREE), allocator, ptrP);
}

static inline uint64_t eOsHostGetTime(void)
{
    uint64_t timeNanos;
    syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_HOST, SYSCALL_OS_MAIN_HOST_GET_TIME), &timeNanos);
    return timeNanos;
}

static inline uint64_t eOsRtcGetTime(void)
{
    uint64_t timeNanos;
    syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_MAIN, SYSCALL_OS_MAIN_RTC, SYSCALL_OS_MAIN_RTC_GET_TIME), &timeNanos);
    return timeNanos;
}

static inline struct Gpio* eOsGpioRequest(uint32_t gpioNum)
{
    return (struct Gpio*)syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_GPIO, SYSCALL_OS_DRV_GPIO_REQ), gpioNum);
}

static inline void eOsGpioRelease(struct Gpio* __restrict gpio)
{
    syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_GPIO, SYSCALL_OS_DRV_GPIO_REL), gpio);
}

static inline void eOsGpioConfigInput(const struct Gpio* __restrict gpio, int32_t gpioSpeed, enum GpioPullMode pull)
{
    syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_GPIO, SYSCALL_OS_DRV_GPIO_CFG_IN), gpio, gpioSpeed, pull);
}

static inline void eOsGpioConfigOutput(const struct Gpio* __restrict gpio, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode odrMode, bool value)
{
    syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_GPIO, SYSCALL_OS_DRV_GPIO_CFG_OUT), gpio, gpioSpeed, pull, odrMode, value);
}

static inline void eOsGpioConfigAlt(const struct Gpio* __restrict gpio, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode odrMode, uint32_t altFunc)
{
    syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_GPIO, SYSCALL_OS_DRV_GPIO_CFG_ALT), gpio, gpioSpeed, pull, odrMode, altFunc);
}

static inline bool eOsGpioGet(const struct Gpio* __restrict gpio)
{
    return !!syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_GPIO, SYSCALL_OS_DRV_GPIO_GET), gpio);
}

static inline void eOsGpioSet(const struct Gpio* __restrict gpio, bool value)
{
    syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_GPIO, SYSCALL_OS_DRV_GPIO_SET), gpio, value);
}

static inline int eOsI2cMasterRequest(uint32_t busId, uint32_t speedInHz)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_MASTER, SYSCALL_OS_DRV_I2CM_REQ), busId, speedInHz);
}

static inline int eOsI2cMasterRelease(uint32_t busId)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_MASTER, SYSCALL_OS_DRV_I2CM_REL), busId);
}

static inline int eOsI2cMasterTxRx(uint32_t busId, uint32_t addr, const void *txBuf, size_t txSize, void *rxBuf, size_t rxSize, uint32_t cbkTid, void *cookie)
{
    return syscallDoGeneric(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_MASTER, SYSCALL_OS_DRV_I2CM_TXRX), busId, addr, txBuf, txSize, rxBuf, rxSize, cbkTid, cookie);
}

static inline int eOsI2cSlaveRequest(uint32_t busId, uint32_t addr)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_SLAVE, SYSCALL_OS_DRV_I2CS_REQ), busId, addr);
}

static inline int eOsI2cSlaveRelease(uint32_t busId)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_SLAVE, SYSCALL_OS_DRV_I2CS_REL), busId);
}

static inline void eOsI2cSlaveEnableRx(uint32_t busId, void *rxBuf, size_t rxSize, uint32_t cbkTid, void *cookie)
{
    syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_SLAVE, SYSCALL_OS_DRV_I2CS_RX_EN), busId, rxBuf, rxSize, cbkTid, cookie);
}

static inline int eOsI2cSlaveTxPreamble(uint32_t busId, uint8_t byte, uint32_t cbkTid, void *cookie)
{
    return syscallDo4P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_SLAVE, SYSCALL_OS_DRV_I2CS_TX_PRE), busId, byte, cbkTid, cookie);
}

static inline int eOsI2cSlaveTxPacket(uint32_t busId, const void *txBuf, size_t txSize, uint32_t cbkTid, void *cookie)
{
    return syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_OS, SYSCALL_OS_DRIVERS, SYSCALL_OS_DRV_I2C_SLAVE, SYSCALL_OS_DRV_I2CS_TX_PKT), busId, txBuf, txSize, cbkTid, cookie);
}

#ifdef __cplusplus
}
#endif

#endif

