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

#include <atomic.h>
#include <gpio.h>
#include <nanohubPacket.h>
#include <plat/exti.h>
#include <plat/gpio.h>
#include <platform.h>
#include <plat/syscfg.h>
#include <sensors.h>
#include <seos.h>
#include <i2c.h>
#include <timer.h>
#include <stdlib.h>
#include <string.h>
#include <variant/variant.h>
#include <variant/sensType.h>

#define HTS221_APP_ID              APP_ID_MAKE(NANOHUB_VENDOR_STMICRO, 2)

/* Sensor defs */
#define HTS221_WAI_REG_ADDR    0x0F
#define HTS221_WAI_REG_VAL     0xBC

#define HTS221_AV_CONF         0x10

#define HTS221_CTRL_REG1       0x20
#define HTS221_POWER_ON        0x80
#define HTS221_POWER_OFF       0x00
#define HTS221_BDU_ON          0x04
#define HTS221_ODR_ONE_SHOT    0x00
#define HTS221_ODR_1_HZ        0x01
#define HTS221_ODR_7_HZ        0x02
#define HTS221_ODR_12_5_HZ     0x03

#define HTS221_CTRL_REG2       0x21
#define HTS221_REBOOT          0x80

#define HTS221_CTRL_REG3       0x22
#define HTS221_STATUS_REG      0x27

#define HTS221_HUMIDITY_OUTL_REG_ADDR    0x28
#define HTS221_TEMP_OUTL_REG_ADDR        0x2A

#define HTS221_CALIB_DATA      0x30
#define HTS221_CALIB_DATA_LEN  16

struct hts221_calib_data {
    uint8_t  h0_x2;
    uint8_t  h1_x2;
    uint8_t  unused[4];
    uint8_t  h0_t0_l;
    uint8_t  h0_t0_h;
    uint8_t  unused_2[2];
    uint8_t  h1_t0_l;
    uint8_t  h1_t0_h;
    uint8_t  unused_3[4];
};

#define INFO_PRINT(fmt, ...) \
    do { \
        osLog(LOG_INFO, "%s " fmt, "[HTS221]", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) \
    do { \
        if (HTS221_DBG_ENABLED) { \
            osLog(LOG_DEBUG, "%s " fmt, "[HTS221]", ##__VA_ARGS__); \
        } \
    } while (0);

#define ERROR_PRINT(fmt, ...) \
    do { \
        osLog(LOG_ERROR, "%s " fmt, "[HTS221]", ##__VA_ARGS__); \
    } while (0);

/* DO NOT MODIFY, just to avoid compiler error if not defined using FLAGS */
#ifndef HTS221_DBG_ENABLED
#define HTS221_DBG_ENABLED                           0
#endif /* HTS221_DBG_ENABLED */

enum hts221SensorEvents
{
    EVT_COMM_DONE = EVT_APP_START + 1,
    EVT_INT1_RAISED,
    EVT_SENSOR_HUMIDITY_TIMER,
};

enum hts221SensorState {
    SENSOR_BOOT,
    SENSOR_VERIFY_ID,
    SENSOR_INIT,
    SENSOR_HUMIDITY_POWER_UP,
    SENSOR_HUMIDITY_POWER_DOWN,
    SENSOR_READ_SAMPLES,
};

#ifndef HTS221_I2C_BUS_ID
#error "HTS221_I2C_BUS_ID is not defined; please define in variant.h"
#endif

#ifndef HTS221_I2C_SPEED
#error "HTS221_I2C_SPEED is not defined; please define in variant.h"
#endif

#ifndef HTS221_I2C_ADDR
#error "HTS221_I2C_ADDR is not defined; please define in variant.h"
#endif

enum hts221SensorIndex {
    HUMIDITY = 0,
    NUM_OF_SENSOR,
};

struct hts221Sensor {
    uint32_t handle;
};

#define HTS221_MAX_PENDING_I2C_REQUESTS   4
#define HTS221_MAX_I2C_TRANSFER_SIZE      HTS221_CALIB_DATA_LEN

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;
    uint8_t txrxBuf[HTS221_MAX_I2C_TRANSFER_SIZE];
    uint8_t state;
    bool inUse;
};

/* Task structure */
struct hts221Task {
    uint32_t tid;

    /* timer */
    uint32_t humidityTimerHandle;

    /* sensor flags */
    bool humidityOn;
    bool humidityReading;
    bool humidityWantRead;

    /* calib data */
    int8_t y0_H;
    int8_t y1_H;
    int16_t x0_H;
    int16_t x1_H;

    struct I2cTransfer transfers[HTS221_MAX_PENDING_I2C_REQUESTS];

    /* Communication functions */
    bool (*comm_tx)(uint8_t addr, uint8_t data, uint32_t delay, uint8_t state);
    bool (*comm_rx)(uint8_t addr, uint16_t len, uint32_t delay, uint8_t state);

    /* sensors */
    struct hts221Sensor sensors[NUM_OF_SENSOR];
};

static struct hts221Task mTask;

static inline float hts221_humidity_percent(int16_t hum)
{
    float percentage = (float) ((mTask.y1_H - mTask.y0_H) * hum + \
                               ((mTask.x1_H * mTask.y0_H) - (mTask.x0_H * mTask.y1_H))) / \
                                  (mTask.x1_H - mTask.x0_H);

    return((percentage > 100) ? 100 : percentage);
}

/*
 * Allocate a buffer and mark it as in use with the given state, or return NULL
 * if no buffers available. Must *not* be called from interrupt context.
 */
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

    osEnqueuePrivateEvt(EVT_COMM_DONE, cookie, NULL, mTask.tid);
    if (err != 0)
        ERROR_PRINT("i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
}

static bool i2c_read(uint8_t addr, uint16_t len, uint32_t delay, uint8_t state)
{
    struct I2cTransfer *xfer = allocXfer(state);
    int ret = -1;

    if (xfer != NULL) {
        if (len > HTS221_MAX_I2C_TRANSFER_SIZE) {
            DEBUG_PRINT("i2c_read: len too big (len: %d)\n", len);
            releaseXfer(xfer);
            return false;
        }

        xfer->txrxBuf[0] = 0x80 | addr;
        if ((ret = i2cMasterTxRx(HTS221_I2C_BUS_ID, HTS221_I2C_ADDR,
                    xfer->txrxBuf, 1, xfer->txrxBuf, len, i2cCallback, xfer)) < 0) {
            DEBUG_PRINT("i2c_read: i2cMasterTxRx operation failed (ret: %d)\n", ret);
            releaseXfer(xfer);
            return false;
        }
    }

    return (ret == -1) ? false : true;
}

static bool i2c_write(uint8_t addr, uint8_t data, uint32_t delay, uint8_t state)
{
    struct I2cTransfer *xfer = allocXfer(state);
    int ret = -1;

    if (xfer != NULL) {
        xfer->txrxBuf[0] = addr;
        xfer->txrxBuf[1] = data;
        if ((ret = i2cMasterTx(HTS221_I2C_BUS_ID, HTS221_I2C_ADDR, xfer->txrxBuf, 2, i2cCallback, xfer)) < 0) {
            releaseXfer(xfer);
            DEBUG_PRINT("i2c_write: i2cMasterTx operation failed (ret: %d)\n", ret);
            return false;
        }
    }

    return (ret == -1) ? false : true;
}

/* Sensor Info */
static void sensorHumidityTimerCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_SENSOR_HUMIDITY_TIMER, data, NULL, mTask.tid);
}

#define DEC_INFO(name, type, axis, inter, samples, rates) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples, \
    .supportedRates = rates

static uint32_t hts221Rates[] = {
    SENSOR_HZ(1.0f),
    SENSOR_HZ(7.0f),
    SENSOR_HZ(12.5f),
    0
};

/* should match "supported rates in length" and be the timer length for that rate in nanosecs */
static const uint64_t hts221RatesRateVals[] =
{
    1 * 1000000000ULL,    /* 1 Hz */
    1000000000ULL / 7,    /* 7 Hz */
    2000000000ULL / 25,   /* 12.5 Hz */
};


static const struct SensorInfo hts221SensorInfo[NUM_OF_SENSOR] =
{
    { DEC_INFO("Humidity", SENS_TYPE_HUMIDITY, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP,
        300, hts221Rates) },
};

/* Sensor Operations */
static bool humidityPower(bool on, void *cookie)
{
    bool oldMode = mTask.humidityOn;
    bool newMode = on;
    uint32_t state = on ? SENSOR_HUMIDITY_POWER_UP : SENSOR_HUMIDITY_POWER_DOWN;
    bool ret = true;

    INFO_PRINT("humidityPower %s\n", on ? "enable" : "disable");

    if (!on && mTask.humidityTimerHandle) {
        timTimerCancel(mTask.humidityTimerHandle);
        mTask.humidityTimerHandle = 0;
        mTask.humidityReading = false;
    }

    if (oldMode != newMode) {
        if (on)
            ret = mTask.comm_tx(HTS221_CTRL_REG1, HTS221_POWER_ON | HTS221_ODR_12_5_HZ, 0, state);
        else
            ret = mTask.comm_tx(HTS221_CTRL_REG1, HTS221_POWER_OFF, 0, state);
    } else
        sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle,
                    SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);

    if (!ret) {
        DEBUG_PRINT("humidityPower comm_tx failed\n");
        return(false);
    }

    mTask.humidityReading = false;
    mTask.humidityOn = on;
    return true;
}

static bool humidityFwUpload(void *cookie)
{
    return sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool humiditySetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    INFO_PRINT("humiditySetRate %lu Hz - %llu ns\n", rate, latency);

    if (mTask.humidityTimerHandle)
        timTimerCancel(mTask.humidityTimerHandle);

    mTask.humidityTimerHandle = timTimerSet(sensorTimerLookupCommon(hts221Rates,
                hts221RatesRateVals, rate), 0, 50, sensorHumidityTimerCallback, NULL, false);

    return sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle,
                SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool humidityFlush(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HUMIDITY), SENSOR_DATA_EVENT_FLUSH, NULL);
}

#define DEC_OPS(power, firmware, rate, flush, cal, cfg) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush, \
    .sensorCalibrate = cal, \
    .sensorCfgData = cfg

static const struct SensorOps hts221SensorOps[NUM_OF_SENSOR] =
{
    { DEC_OPS(humidityPower, humidityFwUpload, humiditySetRate, humidityFlush, NULL, NULL) },
};

static void hts221_save_calib_data(uint8_t *buf)
{
    struct hts221_calib_data *calib = (struct hts221_calib_data *) buf;

    mTask.y0_H = (int8_t) (calib->h0_x2 / 2);
    mTask.y1_H = (int8_t) (calib->h1_x2 / 2);
    mTask.x0_H = (int16_t) (calib->h0_t0_h << 8) |
                           calib->h0_t0_l;
    mTask.x1_H = (int16_t) (calib->h1_t0_h << 8) |
                           calib->h1_t0_l;
    DEBUG_PRINT("y0_H: %d - y1_H: %d\n", mTask.y0_H, mTask.y1_H);
    DEBUG_PRINT("x0_H: %d - x1_H: %d\n", mTask.x0_H, mTask.x1_H);
}

static uint8_t *humidity_samples;
static int handleCommDoneEvt(const void* evtData)
{
    uint8_t i;
    int16_t humidity_val;
    union EmbeddedDataPoint sample;
    struct I2cTransfer *xfer = (struct I2cTransfer *)evtData;

    switch (xfer->state) {
    case SENSOR_BOOT:
        hts221_save_calib_data(xfer->txrxBuf);
        if (!mTask.comm_rx(HTS221_WAI_REG_ADDR, 1, 1, SENSOR_VERIFY_ID)) {
            DEBUG_PRINT("Not able to read WAI\n");
            return -1;
        }
        break;

    case SENSOR_VERIFY_ID:
        /* Check the sensor ID */
        if (xfer->err != 0 || xfer->txrxBuf[0] != HTS221_WAI_REG_VAL) {
            DEBUG_PRINT("WAI returned is: %02x\n", xfer->txrxBuf[0]);
            break;
        }

        INFO_PRINT( "Device ID is correct! (%02x)\n", xfer->txrxBuf[0]);
        for (i = 0; i < NUM_OF_SENSOR; i++)
            sensorRegisterInitComplete(mTask.sensors[i].handle);

        break;

    case SENSOR_INIT:
        for (i = 0; i < NUM_OF_SENSOR; i++)
            sensorRegisterInitComplete(mTask.sensors[i].handle);
        break;

    case SENSOR_HUMIDITY_POWER_UP:
        sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle,
                    SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case SENSOR_HUMIDITY_POWER_DOWN:
        sensorSignalInternalEvt(mTask.sensors[HUMIDITY].handle,
                    SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case SENSOR_READ_SAMPLES:
        if (mTask.humidityOn && mTask.humidityWantRead) {
            mTask.humidityWantRead = false;
            humidity_samples = xfer->txrxBuf;

            humidity_val = (int16_t)(((humidity_samples[1] << 8) & 0xff00) | humidity_samples[0]);
            DEBUG_PRINT("humidity raw data %d\n", humidity_val);

            mTask.humidityReading = false;
            sample.fdata = hts221_humidity_percent(humidity_val);
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_HUMIDITY), sample.vptr, NULL);
        }

        break;

    default:
        break;
    }

    releaseXfer(xfer);
    return (0);
}

static void handleEvent(uint32_t evtType, const void* evtData)
{
    switch (evtType) {
    case EVT_APP_START:
        INFO_PRINT( "EVT_APP_START\n");
        osEventUnsubscribe(mTask.tid, EVT_APP_START);

        mTask.comm_rx(HTS221_CALIB_DATA, sizeof(struct hts221_calib_data), 0, SENSOR_BOOT);
        break;

    case EVT_COMM_DONE:
        handleCommDoneEvt(evtData);
        break;

    case EVT_SENSOR_HUMIDITY_TIMER:
        mTask.humidityWantRead = true;

        /* Start sampling for a value */
        if (!mTask.humidityReading) {
            mTask.humidityReading = true;

            mTask.comm_rx(HTS221_HUMIDITY_OUTL_REG_ADDR, 2, 1, SENSOR_READ_SAMPLES);
        }
        break;

    default:
        break;
    }

}

static bool startTask(uint32_t task_id)
{
    uint8_t i;

    mTask.tid = task_id;

    INFO_PRINT( "started\n");

    mTask.humidityOn = false;
    mTask.humidityReading = false;

    /* Init the communication part */
    i2cMasterRequest(HTS221_I2C_BUS_ID, HTS221_I2C_SPEED);

    mTask.comm_tx = i2c_write;
    mTask.comm_rx = i2c_read;

    for (i = 0; i < NUM_OF_SENSOR; i++) {
        mTask.sensors[i].handle =
            sensorRegister(&hts221SensorInfo[i], &hts221SensorOps[i], NULL, false);
    }

    osEventSubscribe(mTask.tid, EVT_APP_START);

    return true;
}

static void endTask(void)
{
    INFO_PRINT( "ended\n");
}

INTERNAL_APP_INIT(HTS221_APP_ID, 0, startTask, endTask, handleEvent);
