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

#include <eventnums.h>
#include <heap.h>
#include <hostIntf.h>
#include <i2c.h>
#include <leds_gpio.h>
#include <nanohubPacket.h>
#include <sensors.h>
#include <seos.h>
#include <timer.h>
#include <util.h>
#include <variant/variant.h>

#define LP3943_LEDS_APP_ID              APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 21)
#define LP3943_LEDS_APP_VERSION         1

#ifdef LP3943_I2C_BUS_ID
#define I2C_BUS_ID                      LP3943_I2C_BUS_ID
#else
#define I2C_BUS_ID                      0
#endif

#define I2C_SPEED                       400000
#ifdef LP3943_I2C_ADDR
#define I2C_ADDR                        LP3943_I2C_ADDR
#else
#define I2C_ADDR                        0x60
#endif

#define LP3943_REG_PSC0                 0x02
#define LP3943_REG_PWM0                 0x03
#define LP3943_REG_PSC1                 0x04
#define LP3943_REG_PWM1                 0x05
#define LP3943_REG_LS0                  0x06
#define LP3943_REG_LS1                  0x07
#define LP3943_REG_LS2                  0x08
#define LP3943_REG_LS3                  0x09

#define LP3943_MAX_PENDING_I2C_REQUESTS 4
#define LP3943_MAX_I2C_TRANSFER_SIZE    2
#define LP3943_MAX_LED_NUM              16
#define LP3943_MAX_LED_SECTION          4

#ifndef LP3943_DBG_ENABLE
#define LP3943_DBG_ENABLE               0
#endif
#define LP3943_DBG_VALUE                0x55

enum LP3943SensorEvents
{
    EVT_SENSOR_I2C = EVT_APP_START + 1,
    EVT_SENSOR_LEDS_TIMER,
    EVT_TEST,
};

enum LP3943TaskState
{
    STATE_RESET,
    STATE_CLEAN_LS1,
    STATE_CLEAN_LS2,
    STATE_FINISH_INIT,
    STATE_LED,
};

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;
    uint8_t txrxBuf[LP3943_MAX_I2C_TRANSFER_SIZE];
    uint8_t state;
    bool inUse;
};

static struct LP3943Task
{
    uint32_t id;
    uint32_t sHandle;
    uint32_t num;
    bool     ledsOn;
    bool     blink;
    uint32_t ledsTimerHandle;
    uint8_t  led[LP3943_MAX_LED_SECTION];

    struct I2cTransfer transfers[LP3943_MAX_PENDING_I2C_REQUESTS];
} mTask;

/* sensor callbacks from nanohub */
static void i2cCallback(void *cookie, size_t tx, size_t rx, int err)
{
    struct I2cTransfer *xfer = cookie;

    xfer->tx = tx;
    xfer->rx = rx;
    xfer->err = err;

    osEnqueuePrivateEvt(EVT_SENSOR_I2C, cookie, NULL, mTask.id);
    if (err != 0)
        osLog(LOG_INFO, "[LP3943] i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
}

static void sensorLP3943TimerCallback(uint32_t timerId, void *data)
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

// Allocate a buffer and mark it as in use with the given state, or return NULL
// if no buffers available. Must *not* be called from interrupt context.
static struct I2cTransfer *allocXfer(uint8_t state)
{
    size_t i;

    for (i = 0; i < ARRAY_SIZE(mTask.transfers); i++) {
        if (!mTask.transfers[i].inUse) {
            mTask.transfers[i].inUse = true;
            mTask.transfers[i].state = state;
            return &mTask.transfers[i];
        }
    }

    osLog(LOG_ERROR, "[LP3943]: Ran out of i2c buffers!");
    return NULL;
}

// Helper function to release I2cTranfer structure.
static inline void releaseXfer(struct I2cTransfer *xfer)
{
    xfer->inUse = false;
}

// Helper function to write a one byte register. Returns true if we got a
// successful return value from i2cMasterTx().
static bool writeRegister(uint8_t reg, uint8_t value, uint8_t state)
{
    struct I2cTransfer *xfer = allocXfer(state);
    int ret = -1;

    if (xfer != NULL) {
        xfer->txrxBuf[0] = reg;
        xfer->txrxBuf[1] = value;
        ret = i2cMasterTx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf, 2, i2cCallback, xfer);
        if (ret)
            releaseXfer(xfer);
    }

    return (ret == 0);
}

/* Sensor Operations */
static bool sensorLP3943Power(bool on, void *cookie)
{
    if (mTask.ledsTimerHandle) {
        timTimerCancel(mTask.ledsTimerHandle);
        mTask.ledsTimerHandle = 0;
    }
    mTask.ledsOn = on;
    return sensorSignalInternalEvt(mTask.sHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);
}

static bool sensorLP3943FwUpload(void *cookie)
{
    return sensorSignalInternalEvt(mTask.sHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool sensorLP3943SetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    if (mTask.ledsTimerHandle)
        timTimerCancel(mTask.ledsTimerHandle);

    mTask.ledsTimerHandle = timTimerSet(sensorTimerLookupCommon(ledsRates,
                ledsRatesRateVals, rate), 0, 50, sensorLP3943TimerCallback, NULL, false);

    return sensorSignalInternalEvt(mTask.sHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool sensorCfgDataLedsLP3943(void *cfg, void *cookie)
{
    struct LedsCfg *lcfg = (struct LedsCfg *)cfg;
    uint8_t laddr = LP3943_REG_LS0;
    uint8_t lval;
    uint8_t index;
    uint8_t lnum;

    if (lcfg->led_num >= mTask.num) {
        osLog(LOG_INFO, "Wrong led number %"PRIu32"\n", lcfg->led_num);
        return false;
    }
    index = lcfg->led_num >> 2;
    lnum = (lcfg->led_num & 0x3) << 1;
    lval = mTask.led[index];
    laddr += index;
    if (lcfg->value) {
        lval |= (1 << lnum);
    } else {
        lval &= ~(1 << lnum);
    }

    writeRegister(laddr, lval, STATE_LED);
    mTask.led[index] = lval;
    osLog(LOG_INFO, "Set led[%"PRIu32"]=%"PRIu32"\n", lcfg->led_num, lcfg->value);
    return true;
}

static void sensorLedsOnOff(bool flag)
{
    uint8_t laddr = LP3943_REG_LS0;
    uint8_t lval;
    uint8_t index;

    for (index=0; index < LP3943_MAX_LED_SECTION; index++) {
        lval = flag ? mTask.led[index] : 0;
        writeRegister(laddr + index, lval, STATE_LED);
    }
}

static const struct SensorInfo sensorInfoLedsLP3943 = {
    .sensorName = "Leds-LP3943",
    .sensorType = SENS_TYPE_LEDS_I2C,
    .supportedRates = ledsRates,
};

static const struct SensorOps sensorOpsLedsLP3943 = {
    .sensorPower   = sensorLP3943Power,
    .sensorFirmwareUpload = sensorLP3943FwUpload,
    .sensorSetRate  = sensorLP3943SetRate,
    .sensorCfgData = sensorCfgDataLedsLP3943,
};

static void handleI2cEvent(struct I2cTransfer *xfer)
{
    switch (xfer->state) {
        case STATE_RESET:
            writeRegister(LP3943_REG_LS1, 0, STATE_CLEAN_LS1);
            break;

        case STATE_CLEAN_LS1:
            writeRegister(LP3943_REG_LS2, 0, STATE_FINISH_INIT);
            break;

        case STATE_CLEAN_LS2:
            writeRegister(LP3943_REG_LS3, 0, STATE_FINISH_INIT);
            break;

        case STATE_FINISH_INIT:
            if (xfer->err != 0) {
                osLog(LOG_INFO, "[LP3943] not detected\n");
            } else {
                osLog(LOG_INFO, "[LP3943] detected\n");
                sensorRegisterInitComplete(mTask.sHandle);
                if (LP3943_DBG_ENABLE) {
                    mTask.ledsOn = true;
                    mTask.led[0] = LP3943_DBG_VALUE;
                    osEnqueuePrivateEvt(EVT_TEST, NULL, NULL, mTask.id);
                }
            }
            break;

        case STATE_LED:
            break;

        default:
            break;
    }

    releaseXfer(xfer);
}

static void handleEvent(uint32_t evtType, const void* evtData)
{
    switch (evtType) {
    case EVT_APP_START:
        osEventUnsubscribe(mTask.id, EVT_APP_START);
        i2cMasterRequest(I2C_BUS_ID, I2C_SPEED);

        /* Reset Leds */
        writeRegister(LP3943_REG_LS0, 0, STATE_RESET);
        break;

    case EVT_SENSOR_I2C:
        handleI2cEvent((struct I2cTransfer *)evtData);
        break;

    case EVT_SENSOR_LEDS_TIMER:
        if (!mTask.ledsOn)
            break;
        mTask.blink = !mTask.blink;
        sensorLedsOnOff(mTask.blink);
        break;

    case EVT_TEST:
        sensorLP3943SetRate(SENSOR_HZ(1), 0, NULL);
        break;

    default:
        break;
    }
}

static bool startTask(uint32_t taskId)
{
    mTask.id = taskId;
    mTask.num = LP3943_MAX_LED_NUM;
    memset(mTask.led, 0x00, LP3943_MAX_LED_SECTION);
    mTask.ledsOn = mTask.blink = false;

    /* Register sensors */
    mTask.sHandle = sensorRegister(&sensorInfoLedsLP3943, &sensorOpsLedsLP3943, NULL, false);

    osEventSubscribe(taskId, EVT_APP_START);

    return true;
}

static void endTask(void)
{
    sensorUnregister(mTask.sHandle);
}

INTERNAL_APP_INIT(LP3943_LEDS_APP_ID, LP3943_LEDS_APP_VERSION, startTask, endTask, handleEvent);
