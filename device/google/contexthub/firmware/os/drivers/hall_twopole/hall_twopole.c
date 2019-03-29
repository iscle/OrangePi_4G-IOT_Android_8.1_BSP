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

#define APP_ID      APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 11)
#define APP_VERSION 2

#define HALL_REPORT_OPENED_VALUE  0
#define HALL_REPORT_CLOSED_VALUE  1
#define HALL_DEBOUNCE_TIMER_DELAY 25000000ULL // 25 milliseconds

#ifndef HALL_S_PIN
#error "HALL_S_PIN is not defined; please define in variant.h"
#endif
#ifndef HALL_S_IRQ
#error "HALL_S_IRQ is not defined; please define in variant.h"
#endif
#ifndef HALL_N_PIN
#error "HALL_N_PIN is not defined; please define in variant.h"
#endif
#ifndef HALL_N_IRQ
#error "HALL_N_IRQ is not defined; please define in variant.h"
#endif

#define MAKE_TYPE(sPin,nPin) (sPin ? HALL_REPORT_OPENED_VALUE : HALL_REPORT_CLOSED_VALUE) + \
    ((nPin ? HALL_REPORT_OPENED_VALUE : HALL_REPORT_CLOSED_VALUE) << 1)

static struct SensorTask
{
    struct Gpio *sPin;
    struct Gpio *nPin;
    struct ChainedIsr sIsr;
    struct ChainedIsr nIsr;

    uint32_t id;
    uint32_t sensorHandle;
    uint32_t debounceTimerHandle;

    int32_t prevReportedState;

    bool on;
} mTask;

static void hallReportState(int32_t pinState)
{
    union EmbeddedDataPoint sample;
    if (pinState != mTask.prevReportedState) {
        mTask.prevReportedState = pinState;
        sample.idata = pinState;
        osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HALL), sample.vptr, NULL);
    }
}

static void debounceTimerCallback(uint32_t timerId, void *cookie)
{
    int32_t prevPinState = (int32_t)cookie;
    int32_t currPinState = MAKE_TYPE(gpioGet(mTask.sPin), gpioGet(mTask.nPin));

    if (mTask.on && (currPinState == prevPinState)) {
        hallReportState(currPinState);
    }
}

static void startDebounceTimer(struct SensorTask *data)
{
    int32_t currPinState = MAKE_TYPE(gpioGet(data->sPin), gpioGet(data->nPin));
    if (data->debounceTimerHandle)
        timTimerCancel(data->debounceTimerHandle);

    data->debounceTimerHandle = timTimerSet(HALL_DEBOUNCE_TIMER_DELAY, 0, 50, debounceTimerCallback, (void*)currPinState, true /* oneShot */);
}

static bool hallSouthIsr(struct ChainedIsr *localIsr)
{
    struct SensorTask *data = container_of(localIsr, struct SensorTask, sIsr);
    if (data->on)
        startDebounceTimer(data);
    extiClearPendingGpio(data->sPin);
    return true;
}

static bool hallNorthIsr(struct ChainedIsr *localIsr)
{
    struct SensorTask *data = container_of(localIsr, struct SensorTask, nIsr);
    if (data->on)
        startDebounceTimer(data);
    extiClearPendingGpio(data->nPin);
    return true;
}

static bool enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr, IRQn_Type irqn)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_BOTH);
    extiChainIsr(irqn, isr);
    return true;
}

static bool disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr, IRQn_Type irqn)
{
    extiUnchainIsr(irqn, isr);
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
        extiClearPendingGpio(mTask.sPin);
        extiClearPendingGpio(mTask.nPin);
        enableInterrupt(mTask.sPin, &mTask.sIsr, HALL_S_IRQ);
        enableInterrupt(mTask.nPin, &mTask.nIsr, HALL_N_IRQ);
    } else {
        disableInterrupt(mTask.sPin, &mTask.sIsr, HALL_S_IRQ);
        disableInterrupt(mTask.nPin, &mTask.nIsr, HALL_N_IRQ);
        extiClearPendingGpio(mTask.sPin);
        extiClearPendingGpio(mTask.nPin);
    }

    mTask.on = on;
    mTask.prevReportedState = -1;

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
    if (mTask.on)
        hallReportState(MAKE_TYPE(gpioGet(mTask.sPin), gpioGet(mTask.nPin)));

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

    if (mTask.prevReportedState != -1) {
        sample.idata = mTask.prevReportedState;
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
    mTask.prevReportedState = -1;
    mTask.sPin = gpioRequest(HALL_S_PIN);
    mTask.nPin = gpioRequest(HALL_N_PIN);
    mTask.sIsr.func = hallSouthIsr;
    mTask.nIsr.func = hallNorthIsr;

    return true;
}

static void endTask(void)
{
    disableInterrupt(mTask.sPin, &mTask.sIsr, HALL_S_IRQ);
    disableInterrupt(mTask.nPin, &mTask.nIsr, HALL_N_IRQ);
    extiUnchainIsr(HALL_S_IRQ, &mTask.sIsr);
    extiUnchainIsr(HALL_N_IRQ, &mTask.nIsr);
    extiClearPendingGpio(mTask.sPin);
    extiClearPendingGpio(mTask.nPin);
    gpioRelease(mTask.sPin);
    gpioRelease(mTask.nPin);
    sensorUnregister(mTask.sensorHandle);
    memset(&mTask, 0, sizeof(struct SensorTask));
}

INTERNAL_APP_INIT(APP_ID, APP_VERSION, startTask, endTask, handleEvent);
