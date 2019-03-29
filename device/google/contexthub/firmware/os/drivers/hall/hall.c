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
#include <timer.h>
#include <plat/gpio.h>
#include <plat/exti.h>
#include <plat/syscfg.h>
#include <variant/variant.h>

#define APP_VERSION 2

#define HALL_REPORT_OPENED_VALUE  0
#define HALL_REPORT_CLOSED_VALUE  1
#define HALL_DEBOUNCE_TIMER_DELAY 25000000ULL // 25 milliseconds

#ifndef HALL_PIN
#error "HALL_PIN is not defined; please define in variant.h"
#endif

#ifndef HALL_IRQ
#error "HALL_IRQ is not defined; please define in variant.h"
#endif


static struct SensorTask
{
    struct Gpio *pin;
    struct ChainedIsr isr;

    uint32_t id;
    uint32_t sensorHandle;
    uint32_t debounceTimerHandle;

    int32_t prevReportedValue;

    bool on;
} mTask;

static void debounceTimerCallback(uint32_t timerId, void *cookie)
{
    union EmbeddedDataPoint sample;
    bool prevPinState = (bool)cookie;
    bool pinState = gpioGet(mTask.pin);

    if (mTask.on) {
        if (pinState == prevPinState) {
            sample.idata = pinState ? HALL_REPORT_OPENED_VALUE :
                HALL_REPORT_CLOSED_VALUE;

            if (sample.idata != mTask.prevReportedValue) {
                mTask.prevReportedValue = sample.idata;
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HALL), sample.vptr, NULL);
            }
        }
    }
}

static bool hallIsr(struct ChainedIsr *localIsr)
{
    struct SensorTask *data = container_of(localIsr, struct SensorTask, isr);
    bool pinState = gpioGet(data->pin);

    if (!extiIsPendingGpio(data->pin)) {
        return false;
    }

    if (data->on) {
        if (mTask.debounceTimerHandle)
            timTimerCancel(mTask.debounceTimerHandle);

        mTask.debounceTimerHandle = timTimerSet(HALL_DEBOUNCE_TIMER_DELAY, 0, 50, debounceTimerCallback, (void*)pinState, true /* oneShot */);
    }


    extiClearPendingGpio(data->pin);
    return true;
}

static bool enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_BOTH);
    extiChainIsr(HALL_IRQ, isr);
    return true;
}

static bool disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiUnchainIsr(HALL_IRQ, isr);
    extiDisableIntGpio(pin);
    return true;
}

static const uint32_t supportedRates[] =
{
    SENSOR_RATE_ONCHANGE,
    0
};

static const struct SensorInfo mSensorInfo =
{
    .sensorName = "Hall",
    .supportedRates = supportedRates,
    .sensorType = SENS_TYPE_HALL,
    .numAxis = NUM_AXIS_EMBEDDED,
    .interrupt = NANOHUB_INT_WAKEUP,
    .minSamples = 20
};

static bool hallPower(bool on, void *cookie)
{
    if (on) {
        extiClearPendingGpio(mTask.pin);
        enableInterrupt(mTask.pin, &mTask.isr);
    } else {
        disableInterrupt(mTask.pin, &mTask.isr);
        extiClearPendingGpio(mTask.pin);
    }

    mTask.on = on;
    mTask.prevReportedValue = -1;

    if (mTask.debounceTimerHandle) {
        timTimerCancel(mTask.debounceTimerHandle);
        mTask.debounceTimerHandle = 0;
    }

    return sensorSignalInternalEvt(mTask.sensorHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);
}

static bool hallFirmwareUpload(void *cookie)
{
    return sensorSignalInternalEvt(mTask.sensorHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool hallSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    // report initial state of hall interrupt pin
    if (mTask.on) {
        union EmbeddedDataPoint sample;
        bool pinState = gpioGet(mTask.pin);
        sample.idata = pinState ? HALL_REPORT_OPENED_VALUE :
            HALL_REPORT_CLOSED_VALUE;
        osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HALL), sample.vptr, NULL);
    }

    return sensorSignalInternalEvt(mTask.sensorHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool hallFlush(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HALL), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool hallSendLastSample(void *cookie, uint32_t tid)
{
    union EmbeddedDataPoint sample;
    bool result = true;

    if (mTask.prevReportedValue != -1) {
        sample.idata = mTask.prevReportedValue;
        result = osEnqueuePrivateEvt(sensorGetMyEventType(SENS_TYPE_HALL), sample.vptr, NULL, tid);
    }

    return result;
}

static const struct SensorOps mSensorOps =
{
    .sensorPower = hallPower,
    .sensorFirmwareUpload = hallFirmwareUpload,
    .sensorSetRate = hallSetRate,
    .sensorFlush = hallFlush,
    .sensorSendOneDirectEvt = hallSendLastSample
};

static void handleEvent(uint32_t evtType, const void* evtData)
{
}

static bool startTask(uint32_t taskId)
{
    mTask.id = taskId;
    mTask.sensorHandle = sensorRegister(&mSensorInfo, &mSensorOps, NULL, true);
    mTask.prevReportedValue = -1;
    mTask.pin = gpioRequest(HALL_PIN);
    mTask.isr.func = hallIsr;

    return true;
}

static void endTask(void)
{
    disableInterrupt(mTask.pin, &mTask.isr);
    extiUnchainIsr(HALL_IRQ, &mTask.isr);
    extiClearPendingGpio(mTask.pin);
    gpioRelease(mTask.pin);
    sensorUnregister(mTask.sensorHandle);
}

INTERNAL_APP_INIT(APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 6), APP_VERSION, startTask, endTask, handleEvent);
