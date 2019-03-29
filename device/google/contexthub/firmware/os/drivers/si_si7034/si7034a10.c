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

#include <atomic.h>
#include <gpio.h>
#include <nanohubPacket.h>
#include <plat/exti.h>
#include <plat/gpio.h>
#include <platform.h>
#include <plat/syscfg.h>
#include <sensors.h>
#include <seos.h>
#include <slab.h>
#include <i2c.h>
#include <timer.h>
#include <stdlib.h>
#include <string.h>
#include <variant/variant.h>

#define SI7034A10_APP_ID                APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 22)

/* Sensor defs */
#define SI7034_ID_SAMPLE                0xFF
#define SI7034_ID_PROD                  0x22

#define SI7034_RESET_CMD                0xFE
#define SI7034_READID_0_CMD             0xFC
#define SI7034_READID_1_CMD             0xC9
#define SI7034_READDATA_0_CMD           0x7C
#define SI7034_READDATA_1_CMD           0xA2

#define SI7034_HUMIGRADES(humi_val)     ((humi_val * 12500) >> 13)
#define SI7034_CENTIGRADES(temp_val)    (((temp_val * 21875) >> 13) - 45000)

#define INFO_PRINT(fmt, ...) \
    do { \
        osLog(LOG_INFO, "%s " fmt, "[SI7034]", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) \
    do { \
        if (SI7034_DBG_ENABLED) { \
            osLog(LOG_DEBUG, "%s " fmt, "[SI7034]", ##__VA_ARGS__); \
        } \
    } while (0);

#define ERROR_PRINT(fmt, ...) \
    do { \
        osLog(LOG_ERROR, "%s " fmt, "[SI7034]", ##__VA_ARGS__); \
    } while (0);

/* DO NOT MODIFY, just to avoid compiler error if not defined using FLAGS */
#ifndef SI7034_DBG_ENABLED
#define SI7034_DBG_ENABLED              0
#endif /* SI7034_DBG_ENABLED */

enum si7034SensorEvents
{
    EVT_SENSOR_I2C = EVT_APP_START + 1,
    EVT_SENSOR_HUMIDITY_TIMER,
    EVT_SENSOR_TEMP_TIMER,
    EVT_TEST,
};

enum si7034SensorState {
    SENSOR_BOOT,
    SENSOR_VERIFY_ID,
    SENSOR_READ_SAMPLES,
};

#ifndef SI7034A10_I2C_BUS_ID
#error "SI7034A10_I2C_BUS_ID is not defined; please define in variant.h"
#endif

#ifndef SI7034A10_I2C_SPEED
#define SI7034A10_I2C_SPEED     400000
#endif

#ifndef SI7034A10_I2C_ADDR
#define SI7034A10_I2C_ADDR      0x70
#endif

enum si7034SensorIndex {
    HUMIDITY = 0,
    TEMP,
    NUM_OF_SENSOR,
};

struct si7034Sensor {
    uint32_t handle;
};

#define SI7034_MAX_PENDING_I2C_REQUESTS   4
#define SI7034_MAX_I2C_TRANSFER_SIZE      6

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;
    uint8_t txrxBuf[SI7034_MAX_I2C_TRANSFER_SIZE];
    uint8_t state;
    bool inUse;
};

/* Task structure */
struct si7034Task {
    uint32_t tid;

    /* timer */
    uint32_t humiTimerHandle;
    uint32_t tempTimerHandle;

    /* sensor flags */
    bool humiOn;
    bool humiReading;
    bool tempOn;
    bool tempReading;

    struct I2cTransfer transfers[SI7034_MAX_PENDING_I2C_REQUESTS];

    /* sensors */
    struct si7034Sensor sensors[NUM_OF_SENSOR];
};

static struct si7034Task mTask;

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

    ERROR_PRINT("Ran out of i2c buffers!");
    return NULL;
}

static inline void releaseXfer(struct I2cTransfer *xfer)
{
    xfer->inUse = false;
}

static void i2cCallback(void *cookie, size_t tx, size_t rx, int err)
{
    struct I2cTransfer *xfer = cookie;

    xfer->tx = tx;
    xfer->rx = rx;
    xfer->err = err;

    osEnqueuePrivateEvt(EVT_SENSOR_I2C, cookie, NULL, mTask.tid);
    if (err != 0)
        ERROR_PRINT("i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
}

static bool si7034_i2c_read(uint8_t addr0, uint8_t addr1, uint8_t state)
{
    struct I2cTransfer *xfer = allocXfer(state);
    int ret = -1;

    if (xfer != NULL) {
        xfer->txrxBuf[0] = addr0;
        xfer->txrxBuf[1] = addr1;
        ret = i2cMasterTxRx(SI7034A10_I2C_BUS_ID, SI7034A10_I2C_ADDR,
                  xfer->txrxBuf, 2, xfer->txrxBuf, 6, i2cCallback, xfer);
        if (ret) {
            releaseXfer(xfer);
            return false;
        }
    }

    return (ret == -1) ? false : true;
}

static bool si7034_i2c_write(uint8_t data, uint8_t state)
{
    struct I2cTransfer *xfer = allocXfer(state);
    int ret = -1;

    if (xfer != NULL) {
        xfer->txrxBuf[0] = data;
        ret = i2cMasterTx(SI7034A10_I2C_BUS_ID, SI7034A10_I2C_ADDR,
                  xfer->txrxBuf, 1, i2cCallback, xfer);
        if (ret) {
            releaseXfer(xfer);
            return false;
        }
    }

    return (ret == -1) ? false : true;
}

/* Sensor Info */
static void sensorHumiTimerCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_SENSOR_HUMIDITY_TIMER, data, NULL, mTask.tid);
}

static void sensorTempTimerCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_SENSOR_TEMP_TIMER, data, NULL, mTask.tid);
}

#define DEC_INFO(name, type, axis, inter, samples, rates) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples, \
    .supportedRates = rates

static uint32_t si7034Rates[] = {
    SENSOR_HZ(0.1),
    SENSOR_HZ(1.0f),
    SENSOR_HZ(5.0f),
    SENSOR_HZ(10.0f),
    SENSOR_HZ(25.0f),
    0
};

// should match "supported rates in length" and be the timer length for that rate in nanosecs
static const uint64_t si7034RatesRateVals[] =
{
    10 * 1000000000ULL,
    1 * 1000000000ULL,
    1000000000ULL / 5,
    1000000000ULL / 10,
    1000000000ULL / 25,
};


static const struct SensorInfo si7034SensorInfo[NUM_OF_SENSOR] =
{
    { DEC_INFO("Humidity", SENS_TYPE_HUMIDITY, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP,
        300, si7034Rates) },
    { DEC_INFO("Temperature", SENS_TYPE_TEMP, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP,
        20, si7034Rates) },
};

/* Sensor Operations */
static bool humiPower(bool on, void *cookie)
{
    DEBUG_PRINT("%s: %d\n", __func__, on);

    if (mTask.humiTimerHandle) {
        timTimerCancel(mTask.humiTimerHandle);
        mTask.humiTimerHandle = 0;
        mTask.humiReading = false;
    }
    mTask.humiOn = on;
    return sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle,
                SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);
}

static bool humiFwUpload(void *cookie)
{
    DEBUG_PRINT("%s\n", __func__);

    return sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle,
                SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool humiSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    DEBUG_PRINT("%s %ld (%lld)\n", __func__, rate, latency);

    if (mTask.humiTimerHandle)
        timTimerCancel(mTask.humiTimerHandle);

    mTask.humiTimerHandle = timTimerSet(sensorTimerLookupCommon(si7034Rates,
                si7034RatesRateVals, rate), 0, 50, sensorHumiTimerCallback, NULL, false);

    return sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle,
                SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool humiFlush(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HUMIDITY), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool tempPower(bool on, void *cookie)
{
    DEBUG_PRINT("%s: %d\n", __func__, on);

    if (mTask.tempTimerHandle) {
        timTimerCancel(mTask.tempTimerHandle);
        mTask.tempTimerHandle = 0;
        mTask.tempReading = false;
    }
    mTask.tempOn = on;
    return sensorSignalInternalEvt(mTask.sensors[TEMP].handle,
                SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);
}

static bool tempFwUpload(void *cookie)
{
    DEBUG_PRINT("%s\n", __func__);

    return sensorSignalInternalEvt(mTask.sensors[TEMP].handle,
                SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool tempSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    DEBUG_PRINT("%s %ld (%lld)\n", __func__, rate, latency);

    if (mTask.tempTimerHandle)
        timTimerCancel(mTask.tempTimerHandle);

    mTask.tempTimerHandle = timTimerSet(sensorTimerLookupCommon(si7034Rates,
                si7034RatesRateVals, rate), 0, 50, sensorTempTimerCallback, NULL, false);

    return sensorSignalInternalEvt(mTask.sensors[TEMP].handle,
                SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool tempFlush(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TEMP), SENSOR_DATA_EVENT_FLUSH, NULL);
}

#define DEC_OPS(power, firmware, rate, flush, cal, cfg) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush, \
    .sensorCalibrate = cal, \
    .sensorCfgData = cfg

static const struct SensorOps si7034SensorOps[NUM_OF_SENSOR] =
{
    { DEC_OPS(humiPower, humiFwUpload, humiSetRate, humiFlush, NULL, NULL) },
    { DEC_OPS(tempPower, tempFwUpload, tempSetRate, tempFlush, NULL, NULL) },
};

static void handleI2cEvent(const void *evtData)
{
    struct I2cTransfer *xfer = (struct I2cTransfer *)evtData;
    union EmbeddedDataPoint sample;
    uint32_t value;
    uint8_t i;

    switch (xfer->state) {
    case SENSOR_BOOT:
        if (!si7034_i2c_read(SI7034_READID_0_CMD, SI7034_READID_1_CMD, SENSOR_VERIFY_ID)) {
            DEBUG_PRINT("Not able to read ID\n");
            return;
        }
        break;

    case SENSOR_VERIFY_ID:
        /* Check the sensor ID */
        if (xfer->err != 0)
            return;
        INFO_PRINT("Device ID = (%02x)\n", xfer->txrxBuf[0]);
        if ((xfer->txrxBuf[0] != SI7034_ID_SAMPLE) &&
            (xfer->txrxBuf[0] != SI7034_ID_PROD))
            break;
        INFO_PRINT("detected\n");
        for (i = 0; i < NUM_OF_SENSOR; i++)
            sensorRegisterInitComplete(mTask.sensors[i].handle);

        /* TEST the environment in standalone mode */
        if (SI7034_DBG_ENABLED) {
            mTask.humiOn = mTask.tempOn = true;
            osEnqueuePrivateEvt(EVT_TEST, NULL, NULL, mTask.tid);
        }
        break;

    case SENSOR_READ_SAMPLES:
        if (mTask.humiOn && mTask.humiReading) {
            value = ((uint32_t)(xfer->txrxBuf[3]) << 8) | xfer->txrxBuf[4];
            value = SI7034_HUMIGRADES(value);
            value = (value > 100000) ? 100000 : value;
            DEBUG_PRINT("Humidity = %u\n", (unsigned)value);
            sample.fdata = (float)value / 1000.0f;

            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HUMIDITY), sample.vptr, NULL);
        }

        if (mTask.tempOn && mTask.tempReading) {
            value = ((uint32_t)(xfer->txrxBuf[0]) << 8) | xfer->txrxBuf[1];
            value = SI7034_CENTIGRADES(value);
            DEBUG_PRINT("Temp = %u\n", (unsigned)value);
            sample.fdata = (float)value / 1000.0f;

            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TEMP), sample.vptr, NULL);
        }

        mTask.humiReading = mTask.tempReading = false;
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
        osEventUnsubscribe(mTask.tid, EVT_APP_START);
        si7034_i2c_write(SI7034_RESET_CMD, SENSOR_BOOT);
        break;

    case EVT_SENSOR_I2C:
        handleI2cEvent(evtData);
        break;

    case EVT_SENSOR_HUMIDITY_TIMER:
        DEBUG_PRINT("EVT_SENSOR_HUMIDITY_TIMER\n");

        if (!mTask.humiOn)
            break;
        /* Start sampling for a value */
        if (!mTask.humiReading && !mTask.tempReading)
            si7034_i2c_read(SI7034_READDATA_0_CMD, SI7034_READDATA_1_CMD, SENSOR_READ_SAMPLES);
        mTask.humiReading = true;
        break;

    case EVT_SENSOR_TEMP_TIMER:
        DEBUG_PRINT("EVT_SENSOR_TEMP_TIMER\n");

        if (!mTask.tempOn)
            break;
        /* Start sampling for a value */
        if (!mTask.humiReading && !mTask.tempReading)
            si7034_i2c_read(SI7034_READDATA_0_CMD, SI7034_READDATA_1_CMD, SENSOR_READ_SAMPLES);
        mTask.tempReading = true;
        break;

    case EVT_TEST:
        DEBUG_PRINT("EVT_TEST\n");

        humiSetRate(SENSOR_HZ(1), 0, NULL);
        tempSetRate(SENSOR_HZ(1), 0, NULL);
        break;

    default:
        break;
    }
}

static bool startTask(uint32_t task_id)
{
    uint8_t i;

    mTask.tid = task_id;

    DEBUG_PRINT("task started\n");

    mTask.humiOn = mTask.humiReading = false;
    mTask.tempOn = mTask.tempReading = false;

    /* Init the communication part */
    i2cMasterRequest(SI7034A10_I2C_BUS_ID, SI7034A10_I2C_SPEED);

    for (i = 0; i < NUM_OF_SENSOR; i++) {
        mTask.sensors[i].handle =
            sensorRegister(&si7034SensorInfo[i], &si7034SensorOps[i], NULL, false);
    }

    osEventSubscribe(mTask.tid, EVT_APP_START);

    return true;
}

static void endTask(void)
{
    uint8_t i;

    DEBUG_PRINT("task ended\n");

    for (i = 0; i < NUM_OF_SENSOR; i++) {
        sensorUnregister(mTask.sensors[i].handle);
    }
}

INTERNAL_APP_INIT(SI7034A10_APP_ID, 0, startTask, endTask, handleEvent);
