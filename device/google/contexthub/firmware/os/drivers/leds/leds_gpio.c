/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <eventnums.h>
#include <gpio.h>
#include <hostIntf.h>
#include <leds_gpio.h>
#include <nanohubPacket.h>
#include <sensors.h>
#include <seos.h>
#include <timer.h>
#include <plat/gpio.h>
#include <plat/exti.h>
#include <plat/syscfg.h>
#include <variant/variant.h>

#define LEDS_GPIO_APP_ID        APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 20)
#define LEDS_GPIO_APP_VERSION   1

#ifndef LEDS_DBG_ENABLE
#define LEDS_DBG_ENABLE         0
#endif

enum sensorLedsEvents
{
    EVT_SENSOR_LEDS_TIMER = EVT_APP_START + 1,
    EVT_TEST,
};

struct LedsVal {
    struct Gpio *ledid;
    uint8_t val;
};

static struct LedsTask
{
    struct LedsVal leds[LEDS_GPIO_MAX];
    uint32_t num;

    uint32_t id;
    uint32_t sHandle;
    uint32_t ledsTimerHandle;
    bool     ledsOn;
} mTask;

static void sensorLedsTimerCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_SENSOR_LEDS_TIMER, data, NULL, mTask.id);
}

static uint32_t ledsRates[] = {
    SENSOR_HZ(0.1),
    SENSOR_HZ(0.5),
    SENSOR_HZ(1.0f),
    SENSOR_HZ(2.0f),
    0
};

// should match "supported rates in length"
static const uint64_t ledsRatesRateVals[] =
{
    10 * 1000000000ULL,
    2 * 1000000000ULL,
    1 * 1000000000ULL,
    1000000000ULL / 2,
};

/* Sensor Operations */
static bool sensorLedsPower(bool on, void *cookie)
{
    if (mTask.ledsTimerHandle) {
        timTimerCancel(mTask.ledsTimerHandle);
        mTask.ledsTimerHandle = 0;
    }

    return sensorSignalInternalEvt(mTask.sHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);
}

static bool sensorLedsFwUpload(void *cookie)
{
    return sensorSignalInternalEvt(mTask.sHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool sensorLedsSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    if (mTask.ledsTimerHandle)
        timTimerCancel(mTask.ledsTimerHandle);

    mTask.ledsTimerHandle = timTimerSet(sensorTimerLookupCommon(ledsRates,
                ledsRatesRateVals, rate), 0, 50, sensorLedsTimerCallback, NULL, false);

    return sensorSignalInternalEvt(mTask.sHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool sensorConfigLedsGpio(void *cfgData, void *buf)
{
    struct LedsCfg *lcfg = (struct LedsCfg *)cfgData;

    if (lcfg->led_num >= mTask.num) {
        osLog(LOG_INFO, "Wrong led number %"PRIu32"\n", lcfg->led_num);
        return false;
    }
    mTask.leds[lcfg->led_num].val = lcfg->value ? 1 : 0;
    gpioSet(mTask.leds[lcfg->led_num].ledid, mTask.leds[lcfg->led_num].val);

    osLog(LOG_INFO, "Set led[%"PRIu32"]=%"PRIu32"\n", lcfg->led_num, lcfg->value);
    return true;
}

static void sensorLedsOnOff(bool flag)
{
    uint32_t i;

    for (i=0; i < mTask.num; i++)
        gpioSet(mTask.leds[i].ledid, flag ? mTask.leds[i].val : 0);
}

static const struct SensorInfo sensorInfoLedsGpio = {
    .sensorName = "Leds-Gpio",
    .sensorType = SENS_TYPE_LEDS,
    .supportedRates = ledsRates,
};

static const struct SensorOps sensorOpsLedsGpio = {
    .sensorPower    = sensorLedsPower,
    .sensorFirmwareUpload = sensorLedsFwUpload,
    .sensorSetRate  = sensorLedsSetRate,
    .sensorCfgData  = sensorConfigLedsGpio,
};

static void handleEvent(uint32_t evtType, const void *evtData)
{
    switch (evtType) {
    case EVT_APP_START:
        osEventUnsubscribe(mTask.id, EVT_APP_START);
        /* Test leds */
        if (LEDS_DBG_ENABLE) {
            mTask.leds[0].val = 1;
            osEnqueuePrivateEvt(EVT_TEST, NULL, NULL, mTask.id);
        }
        osLog(LOG_INFO, "[Leds-Gpio] detected\n");
        break;
    case EVT_SENSOR_LEDS_TIMER:
        mTask.ledsOn = !mTask.ledsOn;
        sensorLedsOnOff(mTask.ledsOn);
        break;
    case EVT_TEST:
        sensorLedsSetRate(SENSOR_HZ(1), 0, NULL);
        break;
    default:
        break;
    }
}

static bool startTask(uint32_t taskId)
{
    const struct LedsGpio *leds;
    struct Gpio *led;
    uint32_t i;

    mTask.id = taskId;
    mTask.num = 0;
    mTask.ledsOn = false;

    leds = ledsGpioBoardCfg();
    for (i=0; i < leds->num && i < LEDS_GPIO_MAX; i++) {
        led = gpioRequest(leds->leds_array[i]);
        if (!led)
            continue;
        mTask.leds[mTask.num].val = 0;
        mTask.leds[mTask.num++].ledid = led;
        gpioConfigOutput(led, GPIO_SPEED_LOW, GPIO_PULL_NONE, GPIO_OUT_PUSH_PULL, 0);
    }
    if (mTask.num == 0)
        return false;
    mTask.sHandle = sensorRegister(&sensorInfoLedsGpio, &sensorOpsLedsGpio, NULL, true);
    osEventSubscribe(taskId, EVT_APP_START);
    return true;
}

static void endTask(void)
{
    uint32_t i;

    sensorUnregister(mTask.sHandle);
    for (i=0; i < mTask.num; i++) {
        gpioSet(mTask.leds[i].ledid, 0);
        gpioRelease(mTask.leds[i].ledid);
    }
}

INTERNAL_APP_INIT(LEDS_GPIO_APP_ID, LEDS_GPIO_APP_VERSION, startTask, endTask, handleEvent);
