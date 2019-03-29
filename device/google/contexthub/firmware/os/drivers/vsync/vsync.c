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

#include <stdlib.h>
#include <string.h>
#include <float.h>

#include <eventnums.h>
#include <gpio.h>
#include <heap.h>
#include <hostIntf.h>
#include <isr.h>
#include <nanohubPacket.h>
#include <sensors.h>
#include <seos.h>
#include <slab.h>
#include <timer.h>
#include <plat/gpio.h>
#include <plat/exti.h>
#include <plat/syscfg.h>
#include <variant/variant.h>

#define VSYNC_APP_ID      APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 7)
#define VSYNC_APP_VERSION 2

// This defines how many vsync events we could handle being backed up in the
// queue. Use this to size our slab
#define MAX_VSYNC_EVENTS        4
#define MAX_VSYNC_INT_LATENCY   1000 /* in ns */

#ifndef VSYNC_PIN
#error "VSYNC_PIN is not defined; please define in variant.h"
#endif

#ifndef VSYNC_IRQ
#error "VSYNC_IRQ is not defined; please define in variant.h"
#endif

#define VERBOSE_PRINT(fmt, ...) do { \
        osLog(LOG_VERBOSE, "%s " fmt, "[VSYNC]", ##__VA_ARGS__); \
    } while (0);

#define INFO_PRINT(fmt, ...) do { \
        osLog(LOG_INFO, "%s " fmt, "[VSYNC]", ##__VA_ARGS__); \
    } while (0);

#define ERROR_PRINT(fmt, ...) INFO_PRINT("%s" fmt, "ERROR: ", ##__VA_ARGS__); \

#define DEBUG_PRINT(fmt, ...) do { \
        if (enable_debug) {  \
            INFO_PRINT(fmt, ##__VA_ARGS__); \
        } \
    } while (0);

static const bool __attribute__((unused)) enable_debug = 0;

static struct SensorTask
{
    struct Gpio *pin;
    struct ChainedIsr isr;
    struct SlabAllocator *evtSlab;


    uint32_t id;
    uint32_t sensorHandle;

    bool on;
} mTask;

static bool vsyncAllocateEvt(struct SingleAxisDataEvent **evPtr, uint64_t time)
{
    struct SingleAxisDataEvent *ev;

    *evPtr = slabAllocatorAlloc(mTask.evtSlab);

    ev = *evPtr;
    if (!ev) {
        ERROR_PRINT("slabAllocatorAlloc() failed\n");
        return false;
    }

    memset(&ev->samples[0].firstSample, 0x00, sizeof(struct SensorFirstSample));
    ev->referenceTime = time;
    ev->samples[0].firstSample.numSamples = 1;
    ev->samples[0].idata = 1;

    return true;
}

static void vsyncFreeEvt(void *ptr)
{
    slabAllocatorFree(mTask.evtSlab, ptr);
}

static bool vsyncIsr(struct ChainedIsr *localIsr)
{
    struct SensorTask *data = container_of(localIsr, struct SensorTask, isr);
    struct SingleAxisDataEvent *ev;

    if (!extiIsPendingGpio(data->pin)) {
        return false;
    }

    if (data->on) {
        if (vsyncAllocateEvt(&ev, sensorGetTime())) {
            if (!osEnqueueEvtOrFree(sensorGetMyEventType(SENS_TYPE_VSYNC), ev, vsyncFreeEvt)) {
                ERROR_PRINT("osEnqueueEvtOrFree() failed\n");
            }
        }
    }

    extiClearPendingGpio(data->pin);
    return true;
}

static bool enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_FALLING);
    extiChainIsr(VSYNC_IRQ, isr);
    return true;
}

static bool disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiUnchainIsr(VSYNC_IRQ, isr);
    extiDisableIntGpio(pin);
    return true;
}

static const struct SensorInfo mSensorInfo =
{
    .sensorName = "Camera Vsync",
    .sensorType = SENS_TYPE_VSYNC,
    .numAxis = NUM_AXIS_ONE,
    .interrupt = NANOHUB_INT_NONWAKEUP,
    .minSamples = 20,
};

static bool vsyncPower(bool on, void *cookie)
{
    VERBOSE_PRINT("power %d\n", on);

    if (on) {
        extiClearPendingGpio(mTask.pin);
        enableInterrupt(mTask.pin, &mTask.isr);
    } else {
        disableInterrupt(mTask.pin, &mTask.isr);
        extiClearPendingGpio(mTask.pin);
    }

    mTask.on = on;
    sensorSignalInternalEvt(mTask.sensorHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);
    return true;
}

static bool vsyncFirmwareUpload(void *cookie)
{
    return sensorSignalInternalEvt(mTask.sensorHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool vsyncSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    VERBOSE_PRINT("setRate\n");
    return sensorSignalInternalEvt(mTask.sensorHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool vsyncFlush(void *cookie)
{
    VERBOSE_PRINT("flush\n");
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_VSYNC), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static const struct SensorOps mSensorOps =
{
    .sensorPower = vsyncPower,
    .sensorFirmwareUpload = vsyncFirmwareUpload,
    .sensorSetRate = vsyncSetRate,
    .sensorFlush = vsyncFlush,
};

static void handleEvent(uint32_t evtType, const void* evtData)
{
}

static bool startTask(uint32_t taskId)
{
    mTask.id = taskId;
    mTask.sensorHandle = sensorRegister(&mSensorInfo, &mSensorOps, NULL, true);
    mTask.pin = gpioRequest(VSYNC_PIN);
    mTask.isr.func = vsyncIsr;
    mTask.isr.maxLatencyNs = MAX_VSYNC_INT_LATENCY;

    mTask.evtSlab = slabAllocatorNew(sizeof(struct SingleAxisDataEvent) + sizeof(struct SingleAxisDataPoint), 4, MAX_VSYNC_EVENTS);
    if (!mTask.evtSlab) {
        ERROR_PRINT("slabAllocatorNew() failed\n");
        return false;
    }

    return true;
}

static void endTask(void)
{
    disableInterrupt(mTask.pin, &mTask.isr);
    extiUnchainIsr(VSYNC_IRQ, &mTask.isr);
    extiClearPendingGpio(mTask.pin);
    gpioRelease(mTask.pin);
    sensorUnregister(mTask.sensorHandle);
}

INTERNAL_APP_INIT(VSYNC_APP_ID, VSYNC_APP_VERSION, startTask, endTask, handleEvent);
