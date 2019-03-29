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

#include <seos.h>
#include <i2c.h>
#include <timer.h>
#include <sensors.h>
#include <heap.h>
#include <hostIntf.h>
#include <nanohubPacket.h>
#include <eventnums.h>
#include <util.h>

#define AMS_TMD2772_APP_VERSION 3

#define DRIVER_NAME                            "AMS: "

#define I2C_BUS_ID                             0
#define I2C_SPEED                              400000
#define I2C_ADDR                               0x39

#define AMS_TMD2772_ID                         0x39

#define AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT    0xa0

#define AMS_TMD2772_REG_ENABLE                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x00)
#define AMS_TMD2772_REG_ATIME                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x01)
#define AMS_TMD2772_REG_PTIME                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x02)
#define AMS_TMD2772_REG_WTIME                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x03)
#define AMS_TMD2772_REG_AILTL                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x04)
#define AMS_TMD2772_REG_AILTH                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x05)
#define AMS_TMD2772_REG_AIHTL                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x06)
#define AMS_TMD2772_REG_AIHTH                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x07)
#define AMS_TMD2772_REG_PILTL                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x08)
#define AMS_TMD2772_REG_PILTH                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x09)
#define AMS_TMD2772_REG_PIHTL                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x0a)
#define AMS_TMD2772_REG_PIHTH                  (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x0b)
#define AMS_TMD2772_REG_PERS                   (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x0c)
#define AMS_TMD2772_REG_CONFIG                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x0d)
#define AMS_TMD2772_REG_PPULSE                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x0e)
#define AMS_TMD2772_REG_CONTROL                (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x0f)
#define AMS_TMD2772_REG_ID                     (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x12)
#define AMS_TMD2772_REG_STATUS                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x13)
#define AMS_TMD2772_REG_C0DATA                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x14)
#define AMS_TMD2772_REG_C0DATAH                (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x15)
#define AMS_TMD2772_REG_C1DATA                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x16)
#define AMS_TMD2772_REG_C1DATAH                (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x17)
#define AMS_TMD2772_REG_PDATAL                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x18)
#define AMS_TMD2772_REG_PDATAH                 (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x19)
#define AMS_TMD2772_REG_POFFSET                (AMS_TMD2772_CMD_TYPE_AUTO_INCREMENT | 0x1E)

#define AMS_TMD2772_ATIME_SETTING              0xdb
#define AMS_TMD2772_ATIME_MS                   ((256 - AMS_TMD2772_ATIME_SETTING) * 2.73) // in milliseconds
#define AMS_TMD2772_PTIME_SETTING              0xff
#define AMS_TMD2772_PTIME_MS                   ((256 - AMS_TMD2772_PTIME_SETTING) * 2.73) // in milliseconds
#define AMS_TMD2772_WTIME_SETTING_ALS_ON       0xdd // (256 - 221) * 2.73 ms = 95.55 ms
#define AMS_TMD2772_WTIME_SETTING_ALS_OFF      0xb8 // (256 - 184) * 2.73 ms = 196.56 ms
#define AMS_TMD2772_PPULSE_SETTING             8

#define AMS_TMD2772_CAL_DEFAULT_OFFSET         0
#define AMS_TMD2772_CAL_MAX_OFFSET             500

/* AMS_TMD2772_REG_ENABLE */
#define POWER_ON_BIT                           (1 << 0)
#define ALS_ENABLE_BIT                         (1 << 1)
#define PROX_ENABLE_BIT                        (1 << 2)
#define WAIT_ENABLE_BIT                        (1 << 3)

/* AMS_TMD2772_REG_STATUS */
#define PROX_INT_BIT                           (1 << 5)
#define ALS_INT_BIT                            (1 << 4)
#define PROX_VALID_BIT                         (1 << 1)
#define ALS_VALID_BIT                          (1 << 0)

#define AMS_TMD2772_REPORT_NEAR_VALUE          0.0f // centimeters
#define AMS_TMD2772_REPORT_FAR_VALUE           5.0f // centimeters

#define AMS_TMD2772_THRESHOLD_ASSERT_NEAR      300   // in PS units
#define AMS_TMD2772_THRESHOLD_DEASSERT_NEAR    150   // in PS units

#define AMS_TMD2772_ALS_MAX_CHANNEL_COUNT      37888 // in raw data
#define AMS_TMD2772_ALS_MAX_REPORT_VALUE       10000 // in lux

#define AMS_TMD2772_ALS_INVALID                UINT32_MAX

/* Used when SENSOR_RATE_ONCHANGE is requested */
#define AMS_TMD2772_DEFAULT_RATE               SENSOR_HZ(5)

#define AMS_TMD2772_MAX_PENDING_I2C_REQUESTS   4
#define AMS_TMD2772_MAX_I2C_TRANSFER_SIZE      16

/* Private driver events */
enum SensorEvents
{
    EVT_SENSOR_I2C = EVT_APP_START + 1,
    EVT_SENSOR_ALS_TIMER,
    EVT_SENSOR_PROX_TIMER,
};

/* I2C state machine */
enum SensorState
{
    SENSOR_STATE_VERIFY_ID,
    SENSOR_STATE_INIT,

    SENSOR_STATE_CALIBRATE_RESET,
    SENSOR_STATE_CALIBRATE_START,
    SENSOR_STATE_CALIBRATE_ENABLING,
    SENSOR_STATE_CALIBRATE_POLLING_STATUS,
    SENSOR_STATE_CALIBRATE_AWAITING_SAMPLE,
    SENSOR_STATE_CALIBRATE_DISABLING,

    SENSOR_STATE_ENABLING_ALS,
    SENSOR_STATE_ENABLING_PROX,
    SENSOR_STATE_DISABLING_ALS,
    SENSOR_STATE_DISABLING_PROX,

    SENSOR_STATE_IDLE,
    SENSOR_STATE_SAMPLING,
};

enum ProxState
{
    PROX_STATE_INIT,
    PROX_STATE_NEAR,
    PROX_STATE_FAR,
};

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;

    union {
        uint8_t bytes[AMS_TMD2772_MAX_I2C_TRANSFER_SIZE];
        struct {
            uint8_t status;
            uint16_t als[2];
            uint16_t prox;
        } __attribute__((packed)) sample;
        struct {
            uint16_t prox;
        } calibration;
    } txrxBuf;

    uint8_t state;
    bool inUse;
};


struct SensorData
{
    uint32_t tid;

    uint32_t alsHandle;
    uint32_t proxHandle;
    uint32_t alsTimerHandle;
    uint32_t proxTimerHandle;
    uint32_t calibrationSampleTotal;

    struct I2cTransfer transfers[AMS_TMD2772_MAX_PENDING_I2C_REQUESTS];

    union EmbeddedDataPoint lastAlsSample;

    uint8_t calibrationSampleCount;
    uint8_t proxState; // enum ProxState

    bool alsOn;
    bool alsReading;
    bool proxOn;
    bool proxReading;
};

static struct SensorData mData;

/* TODO: check rates are supported */
static const uint32_t supportedRates[] =
{
    SENSOR_HZ(0.1),
    SENSOR_HZ(1),
    SENSOR_HZ(4),
    SENSOR_HZ(5),
    SENSOR_RATE_ONCHANGE,
    0
};

static const uint64_t rateTimerVals[] = //should match "supported rates in length" and be the timer length for that rate in nanosecs
{
    10 * 1000000000ULL,
     1 * 1000000000ULL,
    1000000000ULL / 4,
    1000000000ULL / 5,
};

/*
 * Helper functions
 */

static void i2cCallback(void *cookie, size_t tx, size_t rx, int err)
{
    struct I2cTransfer *xfer = cookie;

    xfer->tx = tx;
    xfer->rx = rx;
    xfer->err = err;

    osEnqueuePrivateEvt(EVT_SENSOR_I2C, cookie, NULL, mData.tid);
    if (err != 0)
        osLog(LOG_INFO, DRIVER_NAME "i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
}

// Allocate a buffer and mark it as in use with the given state, or return NULL
// if no buffers available. Must *not* be called from interrupt context.
static struct I2cTransfer *allocXfer(uint8_t state)
{
    size_t i;

    for (i = 0; i < ARRAY_SIZE(mData.transfers); i++) {
        if (!mData.transfers[i].inUse) {
            mData.transfers[i].inUse = true;
            mData.transfers[i].state = state;
            return &mData.transfers[i];
        }
    }

    osLog(LOG_ERROR, DRIVER_NAME "Ran out of i2c buffers!");
    return NULL;
}

// Helper function to write a one byte register. Returns true if we got a
// successful return value from i2cMasterTx().
static bool writeRegister(uint8_t reg, uint8_t value, uint8_t state)
{
    struct I2cTransfer *xfer = allocXfer(state);
    int ret = -1;

    if (xfer != NULL) {
        xfer->txrxBuf.bytes[0] = reg;
        xfer->txrxBuf.bytes[1] = value;
        ret = i2cMasterTx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf.bytes, 2, i2cCallback, xfer);
    }

    return (ret == 0);
}

static void alsTimerCallback(uint32_t timerId, void *cookie)
{
    osEnqueuePrivateEvt(EVT_SENSOR_ALS_TIMER, cookie, NULL, mData.tid);
}

static void proxTimerCallback(uint32_t timerId, void *cookie)
{
    osEnqueuePrivateEvt(EVT_SENSOR_PROX_TIMER, cookie, NULL, mData.tid);
}

static inline float getLuxFromAlsData(uint16_t als0, uint16_t als1)
{
    float cpl = 1.0f / AMS_TMD2772_ATIME_MS;
    float GA;

    if ((als0 * 10) < (als1 * 21)) {
        // A light
        GA = 0.274f;
    } else if (((als0 * 10) >= (als1 * 21)) && ((als0 * 10) <= (als1 * 43)) && (als0 > 300)) {
        // D65
        GA = 0.592f;
    } else {
        // cool white
        GA = 1.97f;
    }

    float lux1 = GA * 207 * (als0 - (1.799 * als1)) * cpl;
    float lux2 = GA * 207 * ((0.188f * als0) - (0.303 * als1)) * cpl;

    if ((als0 >= AMS_TMD2772_ALS_MAX_CHANNEL_COUNT) ||
        (als1 >= AMS_TMD2772_ALS_MAX_CHANNEL_COUNT)) {
        return AMS_TMD2772_ALS_MAX_REPORT_VALUE;
    } else if ((lux1 > lux2) && (lux1 > 0.0f)) {
        return lux1 > AMS_TMD2772_ALS_MAX_REPORT_VALUE ? AMS_TMD2772_ALS_MAX_REPORT_VALUE : lux1;
    } else if (lux2 > 0.0f) {
        return lux2 > AMS_TMD2772_ALS_MAX_REPORT_VALUE ? AMS_TMD2772_ALS_MAX_REPORT_VALUE : lux2;
    } else {
        return 0.0f;
    }
}

static void setMode(bool alsOn, bool proxOn, uint8_t state)
{
    struct I2cTransfer *xfer;

    xfer = allocXfer(state);
    if (xfer != NULL) {
        xfer->txrxBuf.bytes[0] = AMS_TMD2772_REG_ENABLE;
        xfer->txrxBuf.bytes[1] = POWER_ON_BIT | WAIT_ENABLE_BIT |
                                 (alsOn ? ALS_ENABLE_BIT : 0) | (proxOn ? PROX_ENABLE_BIT : 0);
        xfer->txrxBuf.bytes[2] = AMS_TMD2772_ATIME_SETTING;
        xfer->txrxBuf.bytes[3] = AMS_TMD2772_PTIME_SETTING;
        xfer->txrxBuf.bytes[4] = alsOn ? AMS_TMD2772_WTIME_SETTING_ALS_ON : AMS_TMD2772_WTIME_SETTING_ALS_OFF;
        i2cMasterTx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf.bytes, 5, i2cCallback, xfer);
    }
}

static bool sensorPowerAls(bool on, void *cookie)
{
    osLog(LOG_INFO, DRIVER_NAME "sensorPowerAls: %d\n", on);

    if (mData.alsTimerHandle) {
        timTimerCancel(mData.alsTimerHandle);
        mData.alsTimerHandle = 0;
        mData.alsReading = false;
    }

    mData.lastAlsSample.idata = AMS_TMD2772_ALS_INVALID;
    mData.alsOn = on;
    setMode(on, mData.proxOn, (on ? SENSOR_STATE_ENABLING_ALS : SENSOR_STATE_DISABLING_ALS));

    return true;
}

static bool sensorFirmwareAls(void *cookie)
{
    sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool sensorRateAls(uint32_t rate, uint64_t latency, void *cookie)
{
    if (rate == SENSOR_RATE_ONCHANGE) {
        rate = AMS_TMD2772_DEFAULT_RATE;
    }
    osLog(LOG_INFO, DRIVER_NAME "sensorRateAls: %ld/%lld\n", rate, latency);

    if (mData.alsTimerHandle)
        timTimerCancel(mData.alsTimerHandle);
    mData.alsTimerHandle = timTimerSet(sensorTimerLookupCommon(supportedRates, rateTimerVals, rate), 0, 50, alsTimerCallback, NULL, false);
    osEnqueuePrivateEvt(EVT_SENSOR_ALS_TIMER, NULL, NULL, mData.tid);
    sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

static bool sensorFlushAls(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ALS), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool sendLastSampleAls(void *cookie, uint32_t tid) {
    bool result = true;

    // If we don't end up doing anything here, the expectation is that we are powering up/haven't got the
    // first sample yet, so a broadcast event will go out soon with the first sample
    if (mData.lastAlsSample.idata != AMS_TMD2772_ALS_INVALID) {
        result = osEnqueuePrivateEvt(sensorGetMyEventType(SENS_TYPE_ALS), mData.lastAlsSample.vptr, NULL, tid);
    }
    return result;
}

static bool sensorPowerProx(bool on, void *cookie)
{
    osLog(LOG_INFO, DRIVER_NAME "sensorPowerProx: %d\n", on);

    if (mData.proxTimerHandle) {
        timTimerCancel(mData.proxTimerHandle);
        mData.proxTimerHandle = 0;
        mData.proxReading = false;
    }

    mData.proxState = PROX_STATE_INIT;
    mData.proxOn = on;
    setMode(mData.alsOn, on, (on ? SENSOR_STATE_ENABLING_PROX : SENSOR_STATE_DISABLING_PROX));

    return true;
}

static bool sensorFirmwareProx(void *cookie)
{
    sensorSignalInternalEvt(mData.proxHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool sensorRateProx(uint32_t rate, uint64_t latency, void *cookie)
{
    if (rate == SENSOR_RATE_ONCHANGE) {
        rate = AMS_TMD2772_DEFAULT_RATE;
    }
    osLog(LOG_INFO, DRIVER_NAME "sensorRateProx: %ld/%lld\n", rate, latency);

    if (mData.proxTimerHandle)
        timTimerCancel(mData.proxTimerHandle);
    mData.proxTimerHandle = timTimerSet(sensorTimerLookupCommon(supportedRates, rateTimerVals, rate), 0, 50, proxTimerCallback, NULL, false);
    osEnqueuePrivateEvt(EVT_SENSOR_PROX_TIMER, NULL, NULL, mData.tid);
    sensorSignalInternalEvt(mData.proxHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

static bool sensorFlushProx(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_PROX), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool sendLastSampleProx(void *cookie, uint32_t tid) {
    union EmbeddedDataPoint sample;
    bool result = true;

    // See note in sendLastSampleAls
    if (mData.proxState != PROX_STATE_INIT) {
        sample.fdata = (mData.proxState == PROX_STATE_NEAR) ?
            AMS_TMD2772_REPORT_NEAR_VALUE : AMS_TMD2772_REPORT_FAR_VALUE;
        result = osEnqueuePrivateEvt(sensorGetMyEventType(SENS_TYPE_PROX), sample.vptr, NULL, tid);
    }
    return result;
}

static const struct SensorInfo sensorInfoAls =
{
    .sensorName = "ALS",
    .supportedRates = supportedRates,
    .sensorType = SENS_TYPE_ALS,
    .numAxis = NUM_AXIS_EMBEDDED,
    .interrupt = NANOHUB_INT_NONWAKEUP,
    .minSamples = 20
};

static const struct SensorOps sensorOpsAls =
{
    .sensorPower = sensorPowerAls,
    .sensorFirmwareUpload = sensorFirmwareAls,
    .sensorSetRate = sensorRateAls,
    .sensorFlush = sensorFlushAls,
    .sensorTriggerOndemand = NULL,
    .sensorCalibrate = NULL,
    .sensorSendOneDirectEvt = sendLastSampleAls
};

static const struct SensorInfo sensorInfoProx =
{
    .sensorName = "Proximity",
    .supportedRates = supportedRates,
    .sensorType = SENS_TYPE_PROX,
    .numAxis = NUM_AXIS_EMBEDDED,
    .interrupt = NANOHUB_INT_WAKEUP,
    .minSamples = 300
};

static const struct SensorOps sensorOpsProx =
{
    .sensorPower = sensorPowerProx,
    .sensorFirmwareUpload = sensorFirmwareProx,
    .sensorSetRate = sensorRateProx,
    .sensorFlush = sensorFlushProx,
    .sensorTriggerOndemand = NULL,
    .sensorCalibrate = NULL,
    .sensorSendOneDirectEvt = sendLastSampleProx
};

/*
 * Sensor i2c state machine
 */

static void handle_calibration_event(struct I2cTransfer *xfer) {
    struct I2cTransfer *nextXfer;

    switch (xfer->state) {
    case SENSOR_STATE_CALIBRATE_RESET:
        mData.calibrationSampleCount = 0;
        mData.calibrationSampleTotal = 0;
        /* Intentional fall-through */

    case SENSOR_STATE_CALIBRATE_START:
        writeRegister(AMS_TMD2772_REG_ENABLE, POWER_ON_BIT | PROX_ENABLE_BIT, SENSOR_STATE_CALIBRATE_ENABLING);
        break;

    case SENSOR_STATE_CALIBRATE_ENABLING:
        nextXfer = allocXfer(SENSOR_STATE_CALIBRATE_POLLING_STATUS);
        if (nextXfer != NULL) {
            nextXfer->txrxBuf.bytes[0] = AMS_TMD2772_REG_STATUS;
            i2cMasterTxRx(I2C_BUS_ID, I2C_ADDR, nextXfer->txrxBuf.bytes, 1, nextXfer->txrxBuf.bytes, 1, i2cCallback, nextXfer);
        }
        break;

    case SENSOR_STATE_CALIBRATE_POLLING_STATUS:
        if (xfer->txrxBuf.bytes[0] & PROX_INT_BIT) {
            /* Done */
            nextXfer = allocXfer(SENSOR_STATE_CALIBRATE_AWAITING_SAMPLE);
            if (nextXfer != NULL) {
                nextXfer->txrxBuf.bytes[0] = AMS_TMD2772_REG_PDATAL;
                i2cMasterTxRx(I2C_BUS_ID, I2C_ADDR, nextXfer->txrxBuf.bytes, 1, nextXfer->txrxBuf.bytes, 2, i2cCallback, nextXfer);
            }
        } else {
            /* Poll again; go back to previous state */
            xfer->state = SENSOR_STATE_CALIBRATE_ENABLING;
            handle_calibration_event(xfer);
        }
        break;

    case SENSOR_STATE_CALIBRATE_AWAITING_SAMPLE:
        mData.calibrationSampleCount++;
        mData.calibrationSampleTotal += xfer->txrxBuf.calibration.prox;

        writeRegister(AMS_TMD2772_REG_ENABLE, 0x00, SENSOR_STATE_CALIBRATE_DISABLING);
        break;

    case SENSOR_STATE_CALIBRATE_DISABLING:
        if (mData.calibrationSampleCount >= 20) {
            /* Done, calculate calibration */
            uint16_t average = mData.calibrationSampleTotal / mData.calibrationSampleCount;
            uint16_t crosstalk = (average > 0x7f) ? 0x7f : average;

            writeRegister(AMS_TMD2772_REG_POFFSET, crosstalk, SENSOR_STATE_IDLE);
        } else {
            /* Get another sample; go back to earlier state */
            xfer->state = SENSOR_STATE_CALIBRATE_START;
            handle_calibration_event(xfer);
        }
        break;

    default:
        break;
    }
}

static void handle_i2c_event(struct I2cTransfer *xfer)
{
    union EmbeddedDataPoint sample;
    bool sendData;
    struct I2cTransfer *nextXfer;

    switch (xfer->state) {
    case SENSOR_STATE_VERIFY_ID:
        /* Check the sensor ID */
        if (xfer->err != 0 || xfer->txrxBuf.bytes[0] != AMS_TMD2772_ID) {
            osLog(LOG_INFO, DRIVER_NAME "not detected\n");
            sensorUnregister(mData.alsHandle);
            sensorUnregister(mData.proxHandle);
            break;
        }

        nextXfer = allocXfer(SENSOR_STATE_INIT);
        if (nextXfer != NULL) {
            /* Start address */
            nextXfer->txrxBuf.bytes[0] = AMS_TMD2772_REG_ENABLE;
            /* ENABLE */
            nextXfer->txrxBuf.bytes[1] = 0x00;
            /* ATIME */
            nextXfer->txrxBuf.bytes[2] = AMS_TMD2772_ATIME_SETTING;
            /* PTIME */
            nextXfer->txrxBuf.bytes[3] = AMS_TMD2772_PTIME_SETTING;
            /* WTIME */
            nextXfer->txrxBuf.bytes[4] = 0xFF;
            i2cMasterTx(I2C_BUS_ID, I2C_ADDR, nextXfer->txrxBuf.bytes, 5, i2cCallback, nextXfer);
        }
        break;

    case SENSOR_STATE_INIT:
        nextXfer = allocXfer(SENSOR_STATE_IDLE);
        if (nextXfer != NULL) {
            /* Start address */
            nextXfer->txrxBuf.bytes[0] = AMS_TMD2772_REG_PERS;
            /* PERS */
            nextXfer->txrxBuf.bytes[1] = 0x00;
            /* CONFIG */
            nextXfer->txrxBuf.bytes[2] = 0x00;
            /* PPULSE */
            nextXfer->txrxBuf.bytes[3] = AMS_TMD2772_PPULSE_SETTING;
            /* CONTROL */
            nextXfer->txrxBuf.bytes[4] = 0x20;
            i2cMasterTx(I2C_BUS_ID, I2C_ADDR, nextXfer->txrxBuf.bytes, 5, i2cCallback, nextXfer);
        }
        break;

    case SENSOR_STATE_IDLE:
        sensorRegisterInitComplete(mData.alsHandle);
        sensorRegisterInitComplete(mData.proxHandle);
        break;

    case SENSOR_STATE_ENABLING_ALS:
        sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case SENSOR_STATE_ENABLING_PROX:
        sensorSignalInternalEvt(mData.proxHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case SENSOR_STATE_DISABLING_ALS:
        sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case SENSOR_STATE_DISABLING_PROX:
        sensorSignalInternalEvt(mData.proxHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case SENSOR_STATE_SAMPLING:
#if 0
        osLog(LOG_INFO, DRIVER_NAME "sample ready: status=%02x prox=%u als0=%u als1=%u\n",
              xfer->txrxBuf.sample.status, xfer->txrxBuf.sample.prox,
              xfer->txrxBuf.sample.als[0], xfer->txrxBuf.sample.als[1]);
#endif

        if (mData.alsOn && mData.alsReading &&
            (xfer->txrxBuf.sample.status & ALS_VALID_BIT)) {
            /* Create event */
            sample.fdata = getLuxFromAlsData(xfer->txrxBuf.sample.als[0],
                                             xfer->txrxBuf.sample.als[1]);
            if (mData.lastAlsSample.idata != sample.idata) {
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ALS), sample.vptr, NULL);
                mData.lastAlsSample.fdata = sample.fdata;
            }
        }

        if (mData.proxOn && mData.proxReading &&
            (xfer->txrxBuf.sample.status & PROX_VALID_BIT)) {
            /* Create event */
            sendData = true;
            if (mData.proxState == PROX_STATE_INIT) {
                if (xfer->txrxBuf.sample.prox > AMS_TMD2772_THRESHOLD_ASSERT_NEAR) {
                    sample.fdata = AMS_TMD2772_REPORT_NEAR_VALUE;
                    mData.proxState = PROX_STATE_NEAR;
                } else {
                    sample.fdata = AMS_TMD2772_REPORT_FAR_VALUE;
                    mData.proxState = PROX_STATE_FAR;
                }
            } else {
                if (mData.proxState == PROX_STATE_NEAR &&
                    xfer->txrxBuf.sample.prox < AMS_TMD2772_THRESHOLD_DEASSERT_NEAR) {
                    sample.fdata = AMS_TMD2772_REPORT_FAR_VALUE;
                    mData.proxState = PROX_STATE_FAR;
                } else if (mData.proxState == PROX_STATE_FAR &&
                    xfer->txrxBuf.sample.prox > AMS_TMD2772_THRESHOLD_ASSERT_NEAR) {
                    sample.fdata = AMS_TMD2772_REPORT_NEAR_VALUE;
                    mData.proxState = PROX_STATE_NEAR;
                } else {
                    sendData = false;
                }
            }

            if (sendData)
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_PROX), sample.vptr, NULL);
        }

        mData.alsReading = false;
        mData.proxReading = false;
        break;

    default:
        handle_calibration_event(xfer);
        break;
    }

    xfer->inUse = false;
}

/*
 * Main driver entry points
 */

static bool init_app(uint32_t myTid)
{
    /* Set up driver private data */
    mData.tid = myTid;
    mData.alsOn = false;
    mData.alsReading = false;
    mData.proxOn = false;
    mData.proxReading = false;
    mData.lastAlsSample.idata = AMS_TMD2772_ALS_INVALID;
    mData.proxState = PROX_STATE_INIT;

    /* Register sensors */
    mData.alsHandle = sensorRegister(&sensorInfoAls, &sensorOpsAls, NULL, false);
    mData.proxHandle = sensorRegister(&sensorInfoProx, &sensorOpsProx, NULL, false);

    osEventSubscribe(myTid, EVT_APP_START);

    return true;
}

static void end_app(void)
{
    sensorUnregister(mData.alsHandle);
    sensorUnregister(mData.proxHandle);

    i2cMasterRelease(I2C_BUS_ID);
}

static void handle_event(uint32_t evtType, const void* evtData)
{
    struct I2cTransfer *xfer;

    switch (evtType) {
    case EVT_APP_START:
        osEventUnsubscribe(mData.tid, EVT_APP_START);
        i2cMasterRequest(I2C_BUS_ID, I2C_SPEED);

        /* TODO: reset chip first */
        xfer = allocXfer(SENSOR_STATE_VERIFY_ID);
        if (xfer != NULL) {
            xfer->txrxBuf.bytes[0] = AMS_TMD2772_REG_ID;
            i2cMasterTxRx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf.bytes, 1, xfer->txrxBuf.bytes, 1, i2cCallback, xfer);
        }
        break;

    case EVT_SENSOR_I2C:
        handle_i2c_event((struct I2cTransfer *)evtData);
        break;

    case EVT_SENSOR_ALS_TIMER:
    case EVT_SENSOR_PROX_TIMER:
        /* Start sampling for a value */
        if (!mData.alsReading && !mData.proxReading) {
            xfer = allocXfer(SENSOR_STATE_SAMPLING);
            if (xfer != NULL) {
                xfer->txrxBuf.bytes[0] = AMS_TMD2772_REG_STATUS;
                i2cMasterTxRx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf.bytes, 1, xfer->txrxBuf.bytes, 7, i2cCallback, xfer);
            }
        }

        if (evtType == EVT_SENSOR_ALS_TIMER)
            mData.alsReading = true;
        else
            mData.proxReading = true;
        break;
    }
}

INTERNAL_APP_INIT(APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 9), AMS_TMD2772_APP_VERSION, init_app, end_app, handle_event);
