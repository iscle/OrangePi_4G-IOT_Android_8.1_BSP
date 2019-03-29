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

#include <plat/taggedPtr.h>
#include <plat/rtc.h>
#include <syscall.h>
#include <sensors.h>
#include <errno.h>
#include <osApi.h>
#include <timer.h>
#include <gpio.h>
#include <util.h>
#include <seos.h>
#include <slab.h>
#include <heap.h>
#include <i2c.h>
#include <nanohubCommand.h>

static struct SlabAllocator *mSlabAllocator;


static void osExpApiEvtqSubscribe(uintptr_t *retValP, va_list args)
{
    (void)va_arg(args, uint32_t); // tid
    uint32_t evtType = va_arg(args, uint32_t);

    *retValP = osEventSubscribe(0, evtType);
}

static void osExpApiEvtqUnsubscribe(uintptr_t *retValP, va_list args)
{
    (void)va_arg(args, uint32_t); // tid
    uint32_t evtType = va_arg(args, uint32_t);

    *retValP = osEventUnsubscribe(0, evtType);
}

static void osExpApiEvtqEnqueue(uintptr_t *retValP, va_list args)
{
    uint32_t evtType = va_arg(args, uint32_t);
    void *evtData = va_arg(args, void*);
    uint32_t tid = va_arg(args, uint32_t);

    *retValP = osEnqueueEvtAsApp(evtType, evtData, tid ? true : false);
}

static void osExpApiEvtqEnqueuePrivate(uintptr_t *retValP, va_list args)
{
    uint32_t evtType = va_arg(args, uint32_t);
    void *evtData = va_arg(args, void*);
    (void)va_arg(args, uint32_t); // tid
    uint32_t toTid = va_arg(args, uint32_t);

    *retValP = osEnqueuePrivateEvtAsApp(evtType, evtData, toTid);
}

static void osExpApiEvtqRetainEvt(uintptr_t *retValP, va_list args)
{
    TaggedPtr *evtFreeingInfoP = va_arg(args, TaggedPtr*);

    *retValP = osRetainCurrentEvent(evtFreeingInfoP);
}

static void osExpApiEvtqFreeRetained(uintptr_t *retValP, va_list args)
{
    uint32_t evtType = va_arg(args, uint32_t);
    void *evtData = va_arg(args, void*);
    TaggedPtr *evtFreeingInfoP = va_arg(args, TaggedPtr*);

    osFreeRetainedEvent(evtType, evtData, evtFreeingInfoP);
}

static void osExpApiLogLogv(uintptr_t *retValP, va_list args)
{
    enum LogLevel level = va_arg(args, int /* enums promoted to ints in va_args in C */);
    const char *str = va_arg(args, const char*);
    va_list innerArgs;
    va_copy(innerArgs, INTEGER_TO_VA_LIST(va_arg(args, uintptr_t)));
    osLogv((char)level, 0, str, innerArgs);
    va_end(innerArgs);
}

static void osExpApiSensorSignal(uintptr_t *retValP, va_list args)
{
    uint32_t handle = va_arg(args, uint32_t);
    uint32_t intEvtNum = va_arg(args, uint32_t);
    uint32_t value1 = va_arg(args, uint32_t);
    uint32_t value2_lo = va_arg(args, uint32_t);
    uint32_t value2_hi = va_arg(args, uint32_t);
    uint64_t value2 = (((uint64_t)value2_hi) << 32) + value2_lo;

    *retValP = (uintptr_t)sensorSignalInternalEvt(handle, intEvtNum, value1, value2);
}

static void osExpApiSensorReg(uintptr_t *retValP, va_list args)
{
    const struct SensorInfo *si = va_arg(args, const struct SensorInfo*);
    (void)va_arg(args, uint32_t); // tid
    void *cookie = va_arg(args, void *);
    bool initComplete = va_arg(args, int);

    *retValP = (uintptr_t)sensorRegisterAsApp(si, 0, cookie, initComplete);
}

static void osExpApiSensorUnreg(uintptr_t *retValP, va_list args)
{
    uint32_t handle = va_arg(args, uint32_t);

    *retValP = (uintptr_t)sensorUnregister(handle);
}

static void osExpApiSensorRegInitComp(uintptr_t *retValP, va_list args)
{
    uint32_t handle = va_arg(args, uint32_t);

    *retValP = (uintptr_t)sensorRegisterInitComplete(handle);
}

static void osExpApiSensorFind(uintptr_t *retValP, va_list args)
{
    uint32_t sensorType = va_arg(args, uint32_t);
    uint32_t idx = va_arg(args, uint32_t);
    uint32_t *handleP = va_arg(args, uint32_t*);

    *retValP = (uintptr_t)sensorFind(sensorType, idx, handleP);
}

static void osExpApiSensorReq(uintptr_t *retValP, va_list args)
{
    (void)va_arg(args, uint32_t); // clientId == tid
    uint32_t sensorHandle = va_arg(args, uint32_t);
    uint32_t rate = va_arg(args, uint32_t);
    uint32_t latency_lo = va_arg(args, uint32_t);
    uint32_t latency_hi = va_arg(args, uint32_t);
    uint64_t latency = (((uint64_t)latency_hi) << 32) + latency_lo;

    *retValP = sensorRequest(0, sensorHandle, rate, latency);
}

static void osExpApiSensorRateChg(uintptr_t *retValP, va_list args)
{
    (void)va_arg(args, uint32_t); // clientId == tid
    uint32_t sensorHandle = va_arg(args, uint32_t);
    uint32_t newRate = va_arg(args, uint32_t);
    uint32_t newLatency_lo = va_arg(args, uint32_t);
    uint32_t newLatency_hi = va_arg(args, uint32_t);
    uint64_t newLatency = (((uint64_t)newLatency_hi) << 32) + newLatency_lo;

    *retValP = sensorRequestRateChange(0, sensorHandle, newRate, newLatency);
}

static void osExpApiSensorRel(uintptr_t *retValP, va_list args)
{
    (void)va_arg(args, uint32_t); // clientId == tid
    uint32_t sensorHandle = va_arg(args, uint32_t);

    *retValP = sensorRelease(0, sensorHandle);
}

static void osExpApiSensorTrigger(uintptr_t *retValP, va_list args)
{
    (void)va_arg(args, uint32_t); // clientId == tid
    uint32_t sensorHandle = va_arg(args, uint32_t);

    *retValP = sensorTriggerOndemand(0, sensorHandle);
}

static void osExpApiSensorGetCurRate(uintptr_t *retValP, va_list args)
{
    uint32_t sensorHandle = va_arg(args, uint32_t);

    *retValP = sensorGetCurRate(sensorHandle);
}

static void osExpApiSensorGetTime(uintptr_t *retValP, va_list args)
{
    uint64_t *timeNanos = va_arg(args, uint64_t *);
    *timeNanos = sensorGetTime();
}

static void osExpApiSensorGetReqRate(uintptr_t *retValP, va_list args)
{
    uint32_t sensorHandle = va_arg(args, uint32_t);

    *retValP = sensorGetReqRate(sensorHandle);
}

static void osExpApiTimGetTime(uintptr_t *retValP, va_list args)
{
    uint64_t *timeNanos = va_arg(args, uint64_t *);
    *timeNanos = timGetTime();
}

static void osExpApiTimSetTimer(uintptr_t *retValP, va_list args)
{
    uint32_t length_lo = va_arg(args, uint32_t);
    uint32_t length_hi = va_arg(args, uint32_t);
    uint32_t jitterPpm = va_arg(args, uint32_t);
    uint32_t driftPpm = va_arg(args, uint32_t);
    (void)va_arg(args, uint32_t); // tid
    void *cookie = va_arg(args, void *);
    bool oneshot = va_arg(args, int);
    uint64_t length = (((uint64_t)length_hi) << 32) + length_lo;

    *retValP = timTimerSetAsApp(length, jitterPpm, driftPpm, 0, cookie, oneshot);
}

static void osExpApiTimCancelTimer(uintptr_t *retValP, va_list args)
{
    uint32_t timerId = va_arg(args, uint32_t);

    *retValP = timTimerCancel(timerId);
}

static void osExpApiHeapAlloc(uintptr_t *retValP, va_list args)
{
    uint32_t sz = va_arg(args, uint32_t);

    *retValP = (uintptr_t)heapAlloc(sz);
}

static void osExpApiHeapFree(uintptr_t *retValP, va_list args)
{
    void *mem = va_arg(args, void *);

    heapFree(mem);
}

static void osExpApiSlabNew(uintptr_t *retValP, va_list args)
{
    uint32_t itemSz = va_arg(args, uint32_t);
    uint32_t itemAlign = va_arg(args, uint32_t);
    uint32_t numItems = va_arg(args, uint32_t);

    *retValP = (uintptr_t)slabAllocatorNew(itemSz, itemAlign, numItems);
}

static void osExpApiSlabDestroy(uintptr_t *retValP, va_list args)
{
    struct SlabAllocator *allocator = va_arg(args, struct SlabAllocator *);

    slabAllocatorDestroy(allocator);
}

static void osExpApiSlabAlloc(uintptr_t *retValP, va_list args)
{
    struct SlabAllocator *allocator = va_arg(args, struct SlabAllocator *);

    *retValP = (uintptr_t)slabAllocatorAlloc(allocator);
}

static void osExpApiSlabFree(uintptr_t *retValP, va_list args)
{
    struct SlabAllocator *allocator = va_arg(args, struct SlabAllocator *);
    void *mem = va_arg(args, void *);

    slabAllocatorFree(allocator, mem);
}

static void osExpApiHostGetTime(uintptr_t *retValP, va_list args)
{
    uint64_t *timeNanos = va_arg(args, uint64_t *);
    *timeNanos = hostGetTime();
}

static void osExpApiRtcGetTime(uintptr_t *retValP, va_list args)
{
    uint64_t *timeNanos = va_arg(args, uint64_t *);
    *timeNanos = rtcGetTime();
}

static union OsApiSlabItem* osExpApiI2cCbkInfoAlloc(void *cookie)
{
    union OsApiSlabItem *thing = slabAllocatorAlloc(mSlabAllocator);

    if (thing) {
        thing->i2cAppCbkInfo.toTid = osGetCurrentTid();
        thing->i2cAppCbkInfo.cookie = cookie;
    }

    return thing;
}

static void osExpApiI2cInternalEvtFreeF(void *evt)
{
    slabAllocatorFree(mSlabAllocator, evt);
}

static void osExpApiI2cInternalCbk(void *cookie, size_t tx, size_t rx, int err)
{
    union OsApiSlabItem *thing = (union OsApiSlabItem*)cookie;
    uint32_t tid;

    tid = thing->i2cAppCbkInfo.toTid;
    cookie = thing->i2cAppCbkInfo.cookie;

    //we reuse the same slab element to send the event now
    thing->i2cAppCbkEvt.cookie = cookie;
    thing->i2cAppCbkEvt.tx = tx;
    thing->i2cAppCbkEvt.rx = rx;
    thing->i2cAppCbkEvt.err = err;

    if (!osEnqueuePrivateEvt(EVT_APP_I2C_CBK, &thing->i2cAppCbkEvt, osExpApiI2cInternalEvtFreeF, tid)) {
        osLog(LOG_WARN, "Failed to send I2C evt to app. This might end badly for the app...");
        osExpApiI2cInternalEvtFreeF(thing);
        // TODO: terminate app here: memory pressure is severe
    }
}

static void osExpApiGpioReq(uintptr_t *retValP, va_list args)
{
    uint32_t gpioNum = va_arg(args, uint32_t);

    *retValP = (uintptr_t)gpioRequest(gpioNum);
}

static void osExpApiGpioRel(uintptr_t *retValP, va_list args)
{
    struct Gpio* gpio = va_arg(args, struct Gpio*);

    gpioRelease(gpio);
}

static void osExpApiGpioCfgIn(uintptr_t *retValP, va_list args)
{
    struct Gpio* gpio = va_arg(args, struct Gpio*);
    int32_t speed = va_arg(args, int32_t);
    enum GpioPullMode pullMode = va_arg(args, int);

    gpioConfigInput(gpio, speed, pullMode);
}

static void osExpApiGpioCfgOut(uintptr_t *retValP, va_list args)
{
    struct Gpio* gpio = va_arg(args, struct Gpio*);
    int32_t speed = va_arg(args, int32_t);
    enum GpioPullMode pullMode = va_arg(args, int);
    enum GpioOpenDrainMode odrMode = va_arg(args, int);
    bool value = !!va_arg(args, int);

    gpioConfigOutput(gpio, speed, pullMode, odrMode, value);
}

static void osExpApiGpioCfgAlt(uintptr_t *retValP, va_list args)
{
    struct Gpio* gpio = va_arg(args, struct Gpio*);
    int32_t speed = va_arg(args, int32_t);
    enum GpioPullMode pullMode = va_arg(args, int);
    enum GpioOpenDrainMode odrMode = va_arg(args, int);
    uint32_t altFunc = va_arg(args, uint32_t);

    gpioConfigAlt(gpio, speed, pullMode, odrMode, altFunc);
}

static void osExpApiGpioGet(uintptr_t *retValP, va_list args)
{
    struct Gpio* gpio = va_arg(args, struct Gpio*);

    *retValP = gpioGet(gpio);
}

static void osExpApiGpioSet(uintptr_t *retValP, va_list args)
{
    struct Gpio* gpio = va_arg(args, struct Gpio*);
    bool value = !!va_arg(args, int);

    gpioSet(gpio, value);
}

static void osExpApiI2cMstReq(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);
    uint32_t speed = va_arg(args, uint32_t);

    *retValP = i2cMasterRequest(busId, speed);
}

static void osExpApiI2cMstRel(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);

    *retValP = i2cMasterRelease(busId);
}

static void osExpApiI2cMstTxRx(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);
    uint32_t addr = va_arg(args, uint32_t);
    const void *txBuf = va_arg(args, const void*);
    size_t txSize = va_arg(args, size_t);
    void *rxBuf = va_arg(args, void*);
    size_t rxSize = va_arg(args, size_t);
    (void)va_arg(args, uint32_t); // tid
    void *cookie = va_arg(args, void *);
    union OsApiSlabItem *cbkInfo = osExpApiI2cCbkInfoAlloc(cookie);

    if (!cbkInfo)
        *retValP =  -ENOMEM;

    *retValP = i2cMasterTxRx(busId, addr, txBuf, txSize, rxBuf, rxSize, osExpApiI2cInternalCbk, cbkInfo);

    if (*retValP)
        slabAllocatorFree(mSlabAllocator, cbkInfo);
}

static void osExpApiI2cSlvReq(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);
    uint32_t addr = va_arg(args, uint32_t);

    *retValP = i2cSlaveRequest(busId, addr);
}

static void osExpApiI2cSlvRel(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);

    *retValP = i2cSlaveRelease(busId);
}

static void osExpApiI2cSlvRxEn(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);
    void *rxBuf = va_arg(args, void*);
    size_t rxSize = va_arg(args, size_t);
    (void)va_arg(args, uint32_t); // tid
    void *cookie = va_arg(args, void *);
    union OsApiSlabItem *cbkInfo = osExpApiI2cCbkInfoAlloc(cookie);

    if (!cbkInfo)
        *retValP =  -ENOMEM;

    i2cSlaveEnableRx(busId, rxBuf, rxSize, osExpApiI2cInternalCbk, cbkInfo);

    if (*retValP)
        slabAllocatorFree(mSlabAllocator, cbkInfo);
}

static void osExpApiI2cSlvTxPre(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);
    uint8_t byte = va_arg(args, int);
    (void)va_arg(args, uint32_t); // tid
    void *cookie = va_arg(args, void *);
    union OsApiSlabItem *cbkInfo = osExpApiI2cCbkInfoAlloc(cookie);

    if (!cbkInfo)
        *retValP =  -ENOMEM;

    *retValP = i2cSlaveTxPreamble(busId, byte, osExpApiI2cInternalCbk, cbkInfo);

    if (*retValP)
        slabAllocatorFree(mSlabAllocator, cbkInfo);
}

static void osExpApiI2cSlvTxPkt(uintptr_t *retValP, va_list args)
{
    uint32_t busId = va_arg(args, uint32_t);
    const void *txBuf = va_arg(args, const void*);
    size_t txSize = va_arg(args, size_t);
    (void)va_arg(args, uint32_t); // tid
    void *cookie = va_arg(args, void *);
    union OsApiSlabItem *cbkInfo = osExpApiI2cCbkInfoAlloc(cookie);

    if (!cbkInfo)
        *retValP =  -ENOMEM;

    *retValP = i2cSlaveTxPacket(busId, txBuf, txSize, osExpApiI2cInternalCbk, cbkInfo);

    if (*retValP)
        slabAllocatorFree(mSlabAllocator, cbkInfo);
}

void osApiExport(struct SlabAllocator *mainSlubAllocator)
{
    static const struct SyscallTable osMainEvtqTable = {
        .numEntries = SYSCALL_OS_MAIN_EVTQ_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_EVTQ_SUBCRIBE]        = { .func = osExpApiEvtqSubscribe,      },
            [SYSCALL_OS_MAIN_EVTQ_UNSUBCRIBE]      = { .func = osExpApiEvtqUnsubscribe,    },
            [SYSCALL_OS_MAIN_EVTQ_ENQUEUE]         = { .func = osExpApiEvtqEnqueue,        },
            [SYSCALL_OS_MAIN_EVTQ_ENQUEUE_PRIVATE] = { .func = osExpApiEvtqEnqueuePrivate, },
            [SYSCALL_OS_MAIN_EVTQ_RETAIN_EVT]      = { .func = osExpApiEvtqRetainEvt,      },
            [SYSCALL_OS_MAIN_EVTQ_FREE_RETAINED]   = { .func = osExpApiEvtqFreeRetained,   },
        },
    };

    static const struct SyscallTable osMainLogTable = {
        .numEntries = SYSCALL_OS_MAIN_LOG_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_LOG_LOGV]   = { .func = osExpApiLogLogv,   },
        },
    };

    static const struct SyscallTable osMainSensorsTable = {
        .numEntries = SYSCALL_OS_MAIN_SENSOR_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_SENSOR_SIGNAL]        = { .func = osExpApiSensorSignal,     },
            [SYSCALL_OS_MAIN_SENSOR_REG]           = { .func = osExpApiSensorReg,        },
            [SYSCALL_OS_MAIN_SENSOR_UNREG]         = { .func = osExpApiSensorUnreg,      },
            [SYSCALL_OS_MAIN_SENSOR_REG_INIT_COMP] = { .func = osExpApiSensorRegInitComp },
            [SYSCALL_OS_MAIN_SENSOR_FIND]          = { .func = osExpApiSensorFind,       },
            [SYSCALL_OS_MAIN_SENSOR_REQUEST]       = { .func = osExpApiSensorReq,        },
            [SYSCALL_OS_MAIN_SENSOR_RATE_CHG]      = { .func = osExpApiSensorRateChg,    },
            [SYSCALL_OS_MAIN_SENSOR_RELEASE]       = { .func = osExpApiSensorRel,        },
            [SYSCALL_OS_MAIN_SENSOR_TRIGGER]       = { .func = osExpApiSensorTrigger,    },
            [SYSCALL_OS_MAIN_SENSOR_GET_CUR_RATE]  = { .func = osExpApiSensorGetCurRate, },
            [SYSCALL_OS_MAIN_SENSOR_GET_TIME]      = { .func = osExpApiSensorGetTime,    },
            [SYSCALL_OS_MAIN_SENSOR_GET_REQ_RATE]  = { .func = osExpApiSensorGetReqRate, },

        },
    };

    static const struct SyscallTable osMainTimerTable = {
        .numEntries = SYSCALL_OS_MAIN_TIME_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_TIME_GET_TIME]     = { .func = osExpApiTimGetTime,     },
            [SYSCALL_OS_MAIN_TIME_SET_TIMER]    = { .func = osExpApiTimSetTimer,    },
            [SYSCALL_OS_MAIN_TIME_CANCEL_TIMER] = { .func = osExpApiTimCancelTimer, },
        },
    };

    static const struct SyscallTable osMainHeapTable = {
        .numEntries = SYSCALL_OS_MAIN_HEAP_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_HEAP_ALLOC] = { .func = osExpApiHeapAlloc },
            [SYSCALL_OS_MAIN_HEAP_FREE]  = { .func = osExpApiHeapFree  },
        },
    };

    static const struct SyscallTable osMainSlabTable = {
        .numEntries = SYSCALL_OS_MAIN_SLAB_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_SLAB_NEW]     = { .func = osExpApiSlabNew     },
            [SYSCALL_OS_MAIN_SLAB_DESTROY] = { .func = osExpApiSlabDestroy },
            [SYSCALL_OS_MAIN_SLAB_ALLOC]   = { .func = osExpApiSlabAlloc   },
            [SYSCALL_OS_MAIN_SLAB_FREE]    = { .func = osExpApiSlabFree    },
        },
    };

    static const struct SyscallTable osMainHostTable = {
        .numEntries = SYSCALL_OS_MAIN_HOST_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_HOST_GET_TIME] = { .func = osExpApiHostGetTime },
        },
    };

    static const struct SyscallTable osMainRtcTable = {
        .numEntries = SYSCALL_OS_MAIN_RTC_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_RTC_GET_TIME] = { .func = osExpApiRtcGetTime },
        },
    };

    static const struct SyscallTable osMainTable = {
        .numEntries = SYSCALL_OS_MAIN_LAST,
        .entry = {
            [SYSCALL_OS_MAIN_EVENTQ]  = { .subtable = (struct SyscallTable*)&osMainEvtqTable,    },
            [SYSCALL_OS_MAIN_LOGGING] = { .subtable = (struct SyscallTable*)&osMainLogTable,     },
            [SYSCALL_OS_MAIN_SENSOR]  = { .subtable = (struct SyscallTable*)&osMainSensorsTable, },
            [SYSCALL_OS_MAIN_TIME]    = { .subtable = (struct SyscallTable*)&osMainTimerTable,   },
            [SYSCALL_OS_MAIN_HEAP]    = { .subtable = (struct SyscallTable*)&osMainHeapTable,    },
            [SYSCALL_OS_MAIN_SLAB]    = { .subtable = (struct SyscallTable*)&osMainSlabTable,    },
            [SYSCALL_OS_MAIN_HOST]    = { .subtable = (struct SyscallTable*)&osMainHostTable,    },
            [SYSCALL_OS_MAIN_RTC]     = { .subtable = (struct SyscallTable*)&osMainRtcTable,     },
        },
    };

    static const struct SyscallTable osDrvGpioTable = {
        .numEntries = SYSCALL_OS_DRV_GPIO_LAST,
        .entry = {
            [SYSCALL_OS_DRV_GPIO_REQ]     = { .func = osExpApiGpioReq,    },
            [SYSCALL_OS_DRV_GPIO_REL]     = { .func = osExpApiGpioRel,    },
            [SYSCALL_OS_DRV_GPIO_CFG_IN]  = { .func = osExpApiGpioCfgIn,  },
            [SYSCALL_OS_DRV_GPIO_CFG_OUT] = { .func = osExpApiGpioCfgOut, },
            [SYSCALL_OS_DRV_GPIO_CFG_ALT] = { .func = osExpApiGpioCfgAlt, },
            [SYSCALL_OS_DRV_GPIO_GET]     = { .func = osExpApiGpioGet,    },
            [SYSCALL_OS_DRV_GPIO_SET]     = { .func = osExpApiGpioSet,    },
        },
    };

    static const struct SyscallTable osGrvI2cMstTable = {
        .numEntries = SYSCALL_OS_DRV_I2CM_LAST,
        .entry = {
            [SYSCALL_OS_DRV_I2CM_REQ]  = { .func = osExpApiI2cMstReq,  },
            [SYSCALL_OS_DRV_I2CM_REL]  = { .func = osExpApiI2cMstRel,  },
            [SYSCALL_OS_DRV_I2CM_TXRX] = { .func = osExpApiI2cMstTxRx, },
        },
    };

    static const struct SyscallTable osGrvI2cSlvTable = {
        .numEntries = SYSCALL_OS_DRV_I2CS_LAST,
        .entry = {
            [ SYSCALL_OS_DRV_I2CS_REQ]    = { .func = osExpApiI2cSlvReq,   },
            [ SYSCALL_OS_DRV_I2CS_REL]    = { .func = osExpApiI2cSlvRel,   },
            [ SYSCALL_OS_DRV_I2CS_RX_EN]  = { .func = osExpApiI2cSlvRxEn,  },
            [ SYSCALL_OS_DRV_I2CS_TX_PRE] = { .func = osExpApiI2cSlvTxPre, },
            [ SYSCALL_OS_DRV_I2CS_TX_PKT] = { .func = osExpApiI2cSlvTxPkt, },
        },
    };

    static const struct SyscallTable osDriversTable = {
        .numEntries = SYSCALL_OS_DRV_LAST,
        .entry = {
            [SYSCALL_OS_DRV_GPIO]       = { .subtable = (struct SyscallTable*)&osDrvGpioTable,   },
            [SYSCALL_OS_DRV_I2C_MASTER] = { .subtable = (struct SyscallTable*)&osGrvI2cMstTable, },
            [SYSCALL_OS_DRV_I2C_SLAVE]  = { .subtable = (struct SyscallTable*)&osGrvI2cSlvTable, },
        },
    };

    static const struct SyscallTable osTable = {
        .numEntries = SYSCALL_OS_LAST,
        .entry = {
            [SYSCALL_OS_MAIN]    = { .subtable = (struct SyscallTable*)&osMainTable,    },
            [SYSCALL_OS_DRIVERS] = { .subtable = (struct SyscallTable*)&osDriversTable, },
        },
    };

    if (!syscallAddTable(SYSCALL_NO(SYSCALL_DOMAIN_OS,0,0,0), 1, (struct SyscallTable*)&osTable))
        osLog(LOG_ERROR, "Failed to export OS base API");
}
