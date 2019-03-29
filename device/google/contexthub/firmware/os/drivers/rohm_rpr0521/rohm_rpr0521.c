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
#include <timer.h>
#include <sensors.h>
#include <heap.h>
#include <hostIntf.h>
#include <isr.h>
#include <i2c.h>
#include <nanohubPacket.h>
#include <sensors.h>
#include <seos.h>

#include <plat/exti.h>
#include <plat/gpio.h>
#include <plat/syscfg.h>
#include <variant/variant.h>

#ifndef PROX_INT_PIN
#error "PROX_INT_PIN is not defined; please define in variant.h"
#endif

#ifndef PROX_IRQ
#error "PROX_IRQ is not defined; please define in variant.h"
#endif

#ifndef PROX_I2C_BUS_ID
#define PROX_I2C_BUS_ID     0
#endif

#define RPR0521_APP_VERSION 3

#define I2C_BUS_ID                              PROX_I2C_BUS_ID
#define I2C_SPEED                               400000
#define I2C_ADDR                                0x38

#define ROHM_RPR0521_REG_ID                     0x92
#define ROHM_RPR0521_REG_SYSTEM_CONTROL         0x40
#define ROHM_RPR0521_REG_MODE_CONTROL           0x41
#define ROHM_RPR0521_REG_ALS_PS_CONTROL         0x42
#define ROHM_RPR0521_REG_PS_CONTROL             0x43
#define ROHM_RPR0521_REG_PS_DATA_LSB            0x44
#define ROHM_RPR0521_REG_ALS_DATA0_LSB          0x46
#define ROHM_RPR0521_REG_INTERRUPT              0x4a
#define ROHM_RPR0521_REG_PS_TH_LSB              0x4b
#define ROHM_RPR0521_REG_PS_TH_MSB              0x4c
#define ROHM_RPR0521_REG_PS_TL_LSB              0x4d
#define ROHM_RPR0521_REG_PS_TL_MSB              0x4e
#define ROHM_RPR0521_REG_ALS_DATA0_TH_LSB       0x4f
#define ROHM_RPR0521_REG_ALS_DATA0_TL_LSB       0x51
#define ROHM_RPR0521_REG_PS_OFFSET_LSB          0x53
#define ROHM_RPR0521_REG_PS_OFFSET_MSB          0x54

#define ROHM_RPR0521_ID                         0xe0

#define ROHM_RPR0521_DEFAULT_RATE               SENSOR_HZ(5)

enum {
    ALS_GAIN_X1         = 0,
    ALS_GAIN_X2         = 1,
    ALS_GAIN_X64        = 2,
    ALS_GAIN_X128       = 3,
};
#define ROHM_RPR0521_GAIN_ALS0          ALS_GAIN_X1
#define ROHM_RPR0521_GAIN_ALS1          ALS_GAIN_X1

enum {
    LED_CURRENT_25MA    = 0,
    LED_CURRENT_50MA    = 1,
    LED_CURRENT_100MA   = 2,
    LED_CURRENT_200MA   = 3,
};
#define ROHM_RPR0521_LED_CURRENT        LED_CURRENT_100MA

/* ROHM_RPR0521_REG_SYSTEM_CONTROL */
#define SW_RESET_BIT                            (1 << 7)
#define INT_RESET_BIT                           (1 << 6)

/* ROHM_RPR0521_REG_MODE_CONTROL */
#define ALS_EN_BIT                              (1 << 7)
#define PS_EN_BIT                               (1 << 6)

/* ROHM_RPR0521_REG_PS_CONTROL */
enum {
    PS_GAIN_X1          = 0,
    PS_GAIN_X2          = 1,
    PS_GAIN_X4          = 2,
};
enum {
    PS_PERSISTENCE_ACTIVE_AT_EACH_MEASUREMENT_END         = 0,
    PS_PERSISTENCE_STATUS_UPDATED_AT_EACH_MEASUREMENT_END = 1,
};
#define ROHM_RPR0521_GAIN_PS            PS_GAIN_X1


/* ROHM_RPR0521_REG_INTERRUPT */
#define INTERRUPT_LATCH_BIT                     (1 << 2)
enum {
    INTERRUPT_MODE_PS_TH_H_ONLY      = 0,
    INTERRUPT_MODE_PS_HYSTERESIS     = 1,
    INTERRUPT_MODE_PS_OUTSIDE_DETECT = 2
};
enum {
    INTERRUPT_TRIGGER_INACTIVE = 0,
    INTERRUPT_TRIGGER_PS       = 1,
    INTERRUPT_TRIGGER_ALS      = 2,
    INTERRUPT_TRIGGER_BOTH     = 3
};


#define ROHM_RPR0521_REPORT_NEAR_VALUE          0.0f // centimeters
#define ROHM_RPR0521_REPORT_FAR_VALUE           5.0f // centimeters
#define ROHM_RPR0521_THRESHOLD_ASSERT_NEAR      12   // value in PS_DATA
#define ROHM_RPR0521_THRESHOLD_DEASSERT_NEAR    7    // value in PS_DATA

#define ROHM_RPR0521_ALS_INVALID                UINT32_MAX

#define ROHM_RPR0521_ALS_TIMER_DELAY            200000000ULL

#define ROHM_RPR0521_MAX_PENDING_I2C_REQUESTS   4
#define ROHM_RPR0521_MAX_I2C_TRANSFER_SIZE      16

#define VERBOSE_PRINT(fmt, ...) do { \
        osLog(LOG_VERBOSE, "[Rohm RPR-0521] " fmt, ##__VA_ARGS__); \
    } while (0);

#define INFO_PRINT(fmt, ...) do { \
        osLog(LOG_INFO, "[Rohm RPR-0521] " fmt, ##__VA_ARGS__); \
    } while (0);

#define ERROR_PRINT(fmt, ...) do { \
        osLog(LOG_ERROR, "[Rohm RPR-0521] " fmt, ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) do { \
        if (enable_debug) {  \
            osLog(LOG_INFO, "[Rohm RPR-0521] " fmt, ##__VA_ARGS__); \
        } \
    } while (0);

static const bool enable_debug = 0;

/* Private driver events */
enum SensorEvents
{
    EVT_SENSOR_I2C = EVT_APP_START + 1,
    EVT_SENSOR_ALS_TIMER,
    EVT_SENSOR_PROX_INTERRUPT,
};

/* I2C state machine */
enum SensorState
{
    SENSOR_STATE_RESET,
    SENSOR_STATE_VERIFY_ID,
    SENSOR_STATE_INIT_GAINS,
    SENSOR_STATE_INIT_THRESHOLDS,
    SENSOR_STATE_INIT_OFFSETS,
    SENSOR_STATE_FINISH_INIT,
    SENSOR_STATE_ENABLING_ALS,
    SENSOR_STATE_ENABLING_PROX,
    SENSOR_STATE_DISABLING_ALS,
    SENSOR_STATE_DISABLING_PROX,
    SENSOR_STATE_DISABLING_PROX_2,
    SENSOR_STATE_DISABLING_PROX_3,
    SENSOR_STATE_ALS_SAMPLING,
    SENSOR_STATE_PROX_SAMPLING,
    SENSOR_STATE_IDLE,
};

enum ProxState
{
    PROX_STATE_INIT,
    PROX_STATE_NEAR,
    PROX_STATE_FAR,
};

enum MeasurementTime {
    MEASUREMENT_TIME_ALS_STANDBY_PS_STANDBY     = 0,
    MEASUREMENT_TIME_ALS_STANDBY_PS_10          = 1,
    MEASUREMENT_TIME_ALS_STANDBY_PS_40          = 2,
    MEASUREMENT_TIME_ALS_STANDBY_PS_100         = 3,
    MEASUREMENT_TIME_ALS_STANDBY_PS_400         = 4,
    MEASUREMENT_TIME_ALS_100_PS_50              = 5,
    MEASUREMENT_TIME_ALS_100_PS_100             = 6,
    MEASUREMENT_TIME_ALS_100_PS_400             = 7,
    MEASUREMENT_TIME_ALS_400_PS_50              = 8,
    MEASUREMENT_TIME_ALS_400_PS_100             = 9,
    MEASUREMENT_TIME_ALS_400_PS_STANDBY         = 10,
    MEASUREMENT_TIME_ALS_400_PS_400             = 11,
    MEASUREMENT_TIME_ALS_50_PS_50               = 12,
};

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;
    uint8_t txrxBuf[ROHM_RPR0521_MAX_I2C_TRANSFER_SIZE];
    uint8_t state;
    bool inUse;
};

struct SensorData
{
    struct Gpio *pin;
    struct ChainedIsr isr;

    uint32_t tid;

    uint32_t alsHandle;
    uint32_t proxHandle;
    uint32_t alsTimerHandle;

    union EmbeddedDataPoint lastAlsSample;

    struct I2cTransfer transfers[ROHM_RPR0521_MAX_PENDING_I2C_REQUESTS];

    uint8_t proxState; // enum ProxState

    bool alsOn;
    bool proxOn;
};

static struct SensorData mTask;

static const uint32_t supportedRates[] =
{
    SENSOR_HZ(5),
    SENSOR_RATE_ONCHANGE,
    0,
};

/*
 * Helper functions
 */
static bool proxIsr(struct ChainedIsr *localIsr)
{
    struct SensorData *data = container_of(localIsr, struct SensorData, isr);
    bool firstProxSample = (data->proxState == PROX_STATE_INIT);
    uint8_t lastProxState = data->proxState;
    bool pinState;
    union EmbeddedDataPoint sample;

    if (!extiIsPendingGpio(data->pin)) {
        return false;
    }

    if (data->proxOn) {
        pinState = gpioGet(data->pin);

        if (firstProxSample && !pinState) {
            osEnqueuePrivateEvt(EVT_SENSOR_PROX_INTERRUPT, NULL, NULL, mTask.tid);
        } else if (!firstProxSample) {
            sample.fdata = (pinState) ? ROHM_RPR0521_REPORT_FAR_VALUE : ROHM_RPR0521_REPORT_NEAR_VALUE;
            data->proxState = (pinState) ? PROX_STATE_FAR : PROX_STATE_NEAR;
            if (data->proxState != lastProxState)
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_PROX), sample.vptr, NULL);
        }
    }

    extiClearPendingGpio(data->pin);
    return true;
}

static bool enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiEnableIntGpio(pin, EXTI_TRIGGER_BOTH);
    extiChainIsr(PROX_IRQ, isr);
    return true;
}

static bool disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiUnchainIsr(PROX_IRQ, isr);
    extiDisableIntGpio(pin);
    return true;
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

static void alsTimerCallback(uint32_t timerId, void *cookie)
{
    osEnqueuePrivateEvt(EVT_SENSOR_ALS_TIMER, cookie, NULL, mTask.tid);
}

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

    osLog(LOG_ERROR, "[BMP280]: Ran out of i2c buffers!");
    return NULL;
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
    }

    return (ret == 0);
}

static inline float getLuxFromAlsData(uint16_t als0, uint16_t als1)
{
    static const float invGain[] = {1.0f, 0.5f, 1.0f / 64.0f, 1.0f / 128.0f};
    float d0 = (float)als0 * invGain[ROHM_RPR0521_GAIN_ALS0];
    float d1 = (float)als1 * invGain[ROHM_RPR0521_GAIN_ALS1];
    float ratio = d1 / d0;
    float c1;
    float c2;

    if (ratio < 1.221f) {
        c1 = 6.323f;
        c2 = -3.917f;
    } else if (ratio < 1.432f) {
        c1 = 5.350f;
        c2 = -3.121f;
    } else if (ratio < 1.710f) {
        c1 = 2.449f;
        c2 = -1.096f;
    } else if (ratio < 3.393f) {
        c1 = 1.155f;
        c2 = -0.340f;
    } else {
        c1 = c2 = 0.0f;
    }

    return c1 * d0 + c2 * d1;
}

static void setMode(bool alsOn, bool proxOn, uint8_t state)
{
    uint8_t ctrl;

    static const uint8_t measurementTime[] = {
        MEASUREMENT_TIME_ALS_STANDBY_PS_STANDBY, /* als disabled, prox disabled */
        MEASUREMENT_TIME_ALS_100_PS_100,         /* als enabled, prox disabled */
        MEASUREMENT_TIME_ALS_STANDBY_PS_100,     /* als disabled, prox enabled  */
        MEASUREMENT_TIME_ALS_100_PS_100,         /* als enabled, prox enabled */
    };

    ctrl = measurementTime[alsOn ? 1 : 0 + proxOn ? 2 : 0] | (alsOn ? ALS_EN_BIT : 0) | (proxOn ? PS_EN_BIT : 0);
    writeRegister(ROHM_RPR0521_REG_MODE_CONTROL, ctrl, state);
}

static bool sensorPowerAls(bool on, void *cookie)
{
    VERBOSE_PRINT("sensorPowerAls: %d\n", on);

    if (on && !mTask.alsTimerHandle) {
        mTask.alsTimerHandle = timTimerSet(ROHM_RPR0521_ALS_TIMER_DELAY, 0, 50, alsTimerCallback, NULL, false);
    } else if (!on && mTask.alsTimerHandle) {
        timTimerCancel(mTask.alsTimerHandle);
        mTask.alsTimerHandle = 0;
    }

    mTask.lastAlsSample.idata = ROHM_RPR0521_ALS_INVALID;
    mTask.alsOn = on;

    setMode(on, mTask.proxOn, (on ? SENSOR_STATE_ENABLING_ALS : SENSOR_STATE_DISABLING_ALS));
    return true;
}

static bool sensorFirmwareAls(void *cookie)
{
    return sensorSignalInternalEvt(mTask.alsHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool sensorRateAls(uint32_t rate, uint64_t latency, void *cookie)
{
    if (rate == SENSOR_RATE_ONCHANGE)
        rate = ROHM_RPR0521_DEFAULT_RATE;

    VERBOSE_PRINT("sensorRateAls: rate=%ld Hz latency=%lld ns\n", rate/1024, latency);

    return sensorSignalInternalEvt(mTask.alsHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool sensorFlushAls(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ALS), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool sendLastSampleAls(void *cookie, uint32_t tid) {
    bool result = true;

    // If we don't end up doing anything here, the expectation is that we are powering up/haven't got the
    // first sample yet, so the client will get a broadcast event soon
    if (mTask.lastAlsSample.idata != ROHM_RPR0521_ALS_INVALID) {
        result = osEnqueuePrivateEvt(sensorGetMyEventType(SENS_TYPE_ALS), mTask.lastAlsSample.vptr, NULL, tid);
    }
    return result;
}

static bool sensorPowerProx(bool on, void *cookie)
{
    VERBOSE_PRINT("sensorPowerProx: %d\n", on);

    if (on) {
        extiClearPendingGpio(mTask.pin);
        enableInterrupt(mTask.pin, &mTask.isr);
    } else {
        disableInterrupt(mTask.pin, &mTask.isr);
        extiClearPendingGpio(mTask.pin);
    }

    mTask.proxState = PROX_STATE_INIT;
    mTask.proxOn = on;

    setMode(mTask.alsOn, on, (on ? SENSOR_STATE_ENABLING_PROX : SENSOR_STATE_DISABLING_PROX));
    return true;
}

static bool sensorFirmwareProx(void *cookie)
{
    return sensorSignalInternalEvt(mTask.proxHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool sensorRateProx(uint32_t rate, uint64_t latency, void *cookie)
{
    if (rate == SENSOR_RATE_ONCHANGE)
        rate = ROHM_RPR0521_DEFAULT_RATE;

    VERBOSE_PRINT("sensorRateProx: rate=%ld Hz latency=%lld ns\n", rate/1024, latency);

    return sensorSignalInternalEvt(mTask.proxHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
}

static bool sensorFlushProx(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_PROX), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool sensorCfgDataProx(void *data, void *cookie)
{
    struct I2cTransfer *xfer;

    int32_t offset = *(int32_t*)data;

    INFO_PRINT("Received cfg data: %d\n", (int)offset);

    xfer = allocXfer(SENSOR_STATE_IDLE);
    if (xfer != NULL) {
        xfer->txrxBuf[0] = ROHM_RPR0521_REG_PS_OFFSET_LSB;
        xfer->txrxBuf[1] = offset & 0xFF;
        xfer->txrxBuf[2] = (offset >> 8) & 0x3;
        i2cMasterTx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf, 3, i2cCallback, xfer);

        return true;
    }

    return false;
}

static bool sendLastSampleProx(void *cookie, uint32_t tid) {
    union EmbeddedDataPoint sample;
    bool result = true;

    // See note in sendLastSampleAls
    if (mTask.proxState != PROX_STATE_INIT) {
        sample.fdata = (mTask.proxState == PROX_STATE_NEAR) ? ROHM_RPR0521_REPORT_NEAR_VALUE : ROHM_RPR0521_REPORT_FAR_VALUE;
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
    .sensorCfgData = sensorCfgDataProx,
    .sensorSendOneDirectEvt = sendLastSampleProx
};

/*
 * Sensor i2c state machine
 */

static void __attribute__((unused)) sensorAlsFree(void *ptr)
{
}

static void __attribute__((unused)) sensorProxFree(void *ptr)
{
}

static void handle_i2c_event(struct I2cTransfer *xfer)
{
    union EmbeddedDataPoint sample;
    uint16_t als0, als1, ps;
    uint8_t lastProxState;
    struct I2cTransfer *newXfer;
    uint8_t regData;

    switch (xfer->state) {
    case SENSOR_STATE_RESET:
        newXfer = allocXfer(SENSOR_STATE_VERIFY_ID);
        if (newXfer != NULL) {
            newXfer->txrxBuf[0] = ROHM_RPR0521_REG_ID;
            i2cMasterTxRx(I2C_BUS_ID, I2C_ADDR, newXfer->txrxBuf, 1, newXfer->txrxBuf, 1, i2cCallback, newXfer);
        }
        break;

    case SENSOR_STATE_VERIFY_ID:
        /* Check the sensor ID */
        if (xfer->err != 0 || xfer->txrxBuf[0] != ROHM_RPR0521_ID) {
            INFO_PRINT("not detected\n");
            sensorUnregister(mTask.alsHandle);
            sensorUnregister(mTask.proxHandle);
            break;
        }

        newXfer = allocXfer(SENSOR_STATE_INIT_GAINS);
        if (newXfer != NULL) {
            newXfer->txrxBuf[0] = ROHM_RPR0521_REG_ALS_PS_CONTROL;
            newXfer->txrxBuf[1] = (ROHM_RPR0521_GAIN_ALS0 << 4) | (ROHM_RPR0521_GAIN_ALS1 << 2) | ROHM_RPR0521_LED_CURRENT;
            newXfer->txrxBuf[2] = (ROHM_RPR0521_GAIN_PS << 4) | PS_PERSISTENCE_ACTIVE_AT_EACH_MEASUREMENT_END;
            i2cMasterTx(I2C_BUS_ID, I2C_ADDR, newXfer->txrxBuf, 3, i2cCallback, newXfer);
        }
        break;

    case SENSOR_STATE_INIT_GAINS:
        /* Offset register */
        newXfer = allocXfer(SENSOR_STATE_INIT_OFFSETS);
        if (newXfer != NULL) {
            newXfer->txrxBuf[0] = ROHM_RPR0521_REG_PS_OFFSET_LSB;
            newXfer->txrxBuf[1] = 0;
            newXfer->txrxBuf[2] = 0;
            i2cMasterTx(I2C_BUS_ID, I2C_ADDR, newXfer->txrxBuf, 3, i2cCallback, newXfer);
        }
        break;

    case SENSOR_STATE_INIT_OFFSETS:
        /* PS Threshold register */
        newXfer = allocXfer(SENSOR_STATE_INIT_THRESHOLDS);
        if (newXfer != NULL) {
            newXfer->txrxBuf[0] = ROHM_RPR0521_REG_PS_TH_LSB;
            newXfer->txrxBuf[1] = (ROHM_RPR0521_THRESHOLD_ASSERT_NEAR & 0xFF);
            newXfer->txrxBuf[2] = (ROHM_RPR0521_THRESHOLD_ASSERT_NEAR & 0xFF00) >> 8;
            newXfer->txrxBuf[3] = (ROHM_RPR0521_THRESHOLD_DEASSERT_NEAR & 0xFF);
            newXfer->txrxBuf[4] = (ROHM_RPR0521_THRESHOLD_DEASSERT_NEAR & 0xFF00) >> 8;
            i2cMasterTx(I2C_BUS_ID, I2C_ADDR, newXfer->txrxBuf, 5, i2cCallback, newXfer);
        }
        break;

    case SENSOR_STATE_INIT_THRESHOLDS:
        /* Interrupt register */
        regData = (INTERRUPT_MODE_PS_HYSTERESIS << 4) | INTERRUPT_LATCH_BIT | INTERRUPT_TRIGGER_PS;
        writeRegister(ROHM_RPR0521_REG_INTERRUPT, regData, SENSOR_STATE_FINISH_INIT);
        break;

    case SENSOR_STATE_FINISH_INIT:
        sensorRegisterInitComplete(mTask.alsHandle);
        sensorRegisterInitComplete(mTask.proxHandle);
        break;

    case SENSOR_STATE_ENABLING_ALS:
        sensorSignalInternalEvt(mTask.alsHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case SENSOR_STATE_ENABLING_PROX:
        sensorSignalInternalEvt(mTask.proxHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case SENSOR_STATE_DISABLING_ALS:
        sensorSignalInternalEvt(mTask.alsHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case SENSOR_STATE_DISABLING_PROX:
        // Clear persistence setting
        regData = (ROHM_RPR0521_GAIN_PS << 4) | PS_PERSISTENCE_ACTIVE_AT_EACH_MEASUREMENT_END;
        writeRegister(ROHM_RPR0521_REG_PS_CONTROL, regData, SENSOR_STATE_DISABLING_PROX_2);
        break;

    case SENSOR_STATE_DISABLING_PROX_2:
        // Reset interrupt
        writeRegister(ROHM_RPR0521_REG_SYSTEM_CONTROL, INT_RESET_BIT, SENSOR_STATE_DISABLING_PROX_3);
        break;

    case SENSOR_STATE_DISABLING_PROX_3:
        sensorSignalInternalEvt(mTask.proxHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case SENSOR_STATE_ALS_SAMPLING:
        als0 = *(uint16_t*)(xfer->txrxBuf);
        als1 = *(uint16_t*)(xfer->txrxBuf+2);

        DEBUG_PRINT("als sample ready: als0=%u als1=%u\n", als0, als1);

        if (mTask.alsOn) {
            sample.fdata = getLuxFromAlsData(als0, als1);
            if (mTask.lastAlsSample.idata != sample.idata) {
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ALS), sample.vptr, NULL);
                mTask.lastAlsSample.fdata = sample.fdata;
            }
        }

        break;

    case SENSOR_STATE_PROX_SAMPLING:
        ps = *(uint16_t*)(xfer->txrxBuf);
        lastProxState = mTask.proxState;

        DEBUG_PRINT("prox sample ready: prox=%u\n", ps);

        if (mTask.proxOn) {
            if (ps > ROHM_RPR0521_THRESHOLD_ASSERT_NEAR) {
                sample.fdata = ROHM_RPR0521_REPORT_NEAR_VALUE;
                mTask.proxState = PROX_STATE_NEAR;
            } else {
                sample.fdata = ROHM_RPR0521_REPORT_FAR_VALUE;
                mTask.proxState = PROX_STATE_FAR;
            }

            if (mTask.proxState != lastProxState)
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_PROX), sample.vptr, NULL);

            // After the first prox sample, change the persistance setting to assert
            // interrupt on-change, rather than after every sample
            regData = (ROHM_RPR0521_GAIN_PS << 4) | PS_PERSISTENCE_STATUS_UPDATED_AT_EACH_MEASUREMENT_END;
            writeRegister(ROHM_RPR0521_REG_PS_CONTROL, regData, SENSOR_STATE_IDLE);
        }

        break;

    default:
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
    mTask.tid = myTid;
    mTask.alsOn = false;
    mTask.proxOn = false;
    mTask.lastAlsSample.idata = ROHM_RPR0521_ALS_INVALID;
    mTask.proxState = PROX_STATE_INIT;

    mTask.pin = gpioRequest(PROX_INT_PIN);
    gpioConfigInput(mTask.pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(mTask.pin);
    mTask.isr.func = proxIsr;

    /* Register sensors */
    mTask.alsHandle = sensorRegister(&sensorInfoAls, &sensorOpsAls, NULL, false);
    mTask.proxHandle = sensorRegister(&sensorInfoProx, &sensorOpsProx, NULL, false);

    osEventSubscribe(myTid, EVT_APP_START);

    return true;
}

static void end_app(void)
{
    disableInterrupt(mTask.pin, &mTask.isr);
    extiUnchainIsr(PROX_IRQ, &mTask.isr);
    extiClearPendingGpio(mTask.pin);
    gpioRelease(mTask.pin);

    sensorUnregister(mTask.alsHandle);
    sensorUnregister(mTask.proxHandle);

    i2cMasterRelease(I2C_BUS_ID);
}

static void handle_event(uint32_t evtType, const void* evtData)
{
    struct I2cTransfer *xfer;

    switch (evtType) {
    case EVT_APP_START:
        i2cMasterRequest(I2C_BUS_ID, I2C_SPEED);

        /* Reset chip */
        writeRegister(ROHM_RPR0521_REG_SYSTEM_CONTROL, SW_RESET_BIT, SENSOR_STATE_RESET);
        break;

    case EVT_SENSOR_I2C:
        handle_i2c_event((struct I2cTransfer*)evtData);
        break;

    case EVT_SENSOR_ALS_TIMER:
        xfer = allocXfer(SENSOR_STATE_ALS_SAMPLING);
        if (xfer != NULL) {
            xfer->txrxBuf[0] = ROHM_RPR0521_REG_ALS_DATA0_LSB;
            i2cMasterTxRx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf, 1, xfer->txrxBuf, 4, i2cCallback, xfer);
        }
        break;

    case EVT_SENSOR_PROX_INTERRUPT:
        // Over-read to read the INTERRUPT register to clear the interrupt
        xfer = allocXfer(SENSOR_STATE_PROX_SAMPLING);
        if (xfer != NULL) {
            xfer->txrxBuf[0] = ROHM_RPR0521_REG_PS_DATA_LSB;
            i2cMasterTxRx(I2C_BUS_ID, I2C_ADDR, xfer->txrxBuf, 1, xfer->txrxBuf, 7, i2cCallback, xfer);
        }
        break;

    }
}

INTERNAL_APP_INIT(APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 10), RPR0521_APP_VERSION, init_app, end_app, handle_event);
