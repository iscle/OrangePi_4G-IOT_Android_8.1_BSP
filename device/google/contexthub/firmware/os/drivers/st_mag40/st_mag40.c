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
#include <isr.h>
#include <nanohubPacket.h>
#include <plat/exti.h>
#include <plat/gpio.h>
#include <platform.h>
#include <plat/syscfg.h>
#include <plat/rtc.h>
#include <sensors.h>
#include <seos.h>
#include <slab.h>
#include <heap.h>
#include <i2c.h>
#include <timer.h>
#include <variant/sensType.h>
#include <cpu/cpuMath.h>
#include <calibration/magnetometer/mag_cal.h>
#include <floatRt.h>

#include <stdlib.h>
#include <string.h>
#include <variant/variant.h>

#define ST_MAG40_APP_ID            APP_ID_MAKE(NANOHUB_VENDOR_STMICRO, 3)

/* Sensor registers */
#define ST_MAG40_WAI_REG_ADDR      0x4F
#define ST_MAG40_WAI_REG_VAL       0x40

#define ST_MAG40_CFG_A_REG_ADDR    0x60
#define ST_MAG40_TEMP_COMP_EN      0x80
#define ST_MAG40_SOFT_RESET_BIT    0x20
#define ST_MAG40_ODR_10_HZ         0x00
#define ST_MAG40_ODR_20_HZ         0x04
#define ST_MAG40_ODR_50_HZ         0x08
#define ST_MAG40_ODR_100_HZ        0x0C
#define ST_MAG40_POWER_ON          0x00
#define ST_MAG40_POWER_IDLE        0x03

#define ST_MAG40_CFG_B_REG_ADDR    0x61
#define ST_MAG40_OFF_CANC          0x02

#define ST_MAG40_CFG_C_REG_ADDR    0x62
#define ST_MAG40_I2C_DIS           0x20
#define ST_MAG40_BDU_ON            0x10
#define ST_MAG40_SELFTEST_EN       0x02
#define ST_MAG40_INT_MAG           0x01

#define ST_MAG40_OUTXL_REG_ADDR    0x68

/* Enable auto-increment of the I2C subaddress (to allow I2C multiple ops) */
#define ST_MAG40_I2C_AUTO_INCR     0x80

enum st_mag40_SensorEvents
{
    EVT_COMM_DONE = EVT_APP_START + 1,
    EVT_SENSOR_INTERRUPT,
};

enum st_mag40_TestState {
    MAG_SELFTEST_INIT,
    MAG_SELFTEST_RUN_ST_OFF,
    MAG_SELFTEST_INIT_ST_EN,
    MAG_SELFTEST_RUN_ST_ON,
    MAG_SELFTEST_VERIFY,
    MAG_SELFTEST_DONE,
};

enum st_mag40_SensorState {
    SENSOR_BOOT,
    SENSOR_VERIFY_ID,
    SENSOR_INITIALIZATION,
    SENSOR_IDLE,
    SENSOR_MAG_CONFIGURATION,
    SENSOR_READ_SAMPLES,
    SENSOR_SELF_TEST,
};

enum st_mag40_subState {
    NO_SUBSTATE = 0,

    INIT_START,
    INIT_ENABLE_DRDY,
    INIT_I2C_DISABLE_ACCEL,
    INIT_DONE,

    CONFIG_POWER_UP,
    CONFIG_POWER_UP_2,

    CONFIG_POWER_DOWN,
    CONFIG_POWER_DOWN_2,

    CONFIG_SET_RATE,
    CONFIG_SET_RATE_2,

    CONFIG_DONE,
};

struct TestResultData {
    struct HostHubRawPacket header;
    struct SensorAppEventHeader data_header;
} __attribute__((packed));

#ifndef ST_MAG40_I2C_BUS_ID
#error "ST_MAG40_I2C_BUS_ID is not defined; please define in variant.h"
#endif

#ifndef ST_MAG40_I2C_SPEED
#error "ST_MAG40_I2C_SPEED is not defined; please define in variant.h"
#endif

#ifndef ST_MAG40_I2C_ADDR
#error "ST_MAG40_I2C_ADDR is not defined; please define in variant.h"
#endif

#ifndef ST_MAG40_INT_PIN
#error "ST_MAG40_INT_PIN is not defined; please define in variant.h"
#endif

#ifndef ST_MAG40_INT_IRQ
#error "ST_MAG40_INT_IRQ is not defined; please define in variant.h"
#endif

#ifndef ST_MAG40_ROT_MATRIX
#error "ST_MAG40_ROT_MATRIX is not defined; please define in variant.h"
#endif

#define ST_MAG40_X_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                      ((r11 == 1 ? x : (r11 == -1 ? -x : 0)) + \
                                                       (r12 == 1 ? y : (r12 == -1 ? -y : 0)) + \
                                                       (r13 == 1 ? z : (r13 == -1 ? -z : 0)))

#define ST_MAG40_Y_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                      ((r21 == 1 ? x : (r21 == -1 ? -x : 0)) + \
                                                       (r22 == 1 ? y : (r22 == -1 ? -y : 0)) + \
                                                       (r23 == 1 ? z : (r23 == -1 ? -z : 0)))

#define ST_MAG40_Z_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                      ((r31 == 1 ? x : (r31 == -1 ? -x : 0)) + \
                                                       (r32 == 1 ? y : (r32 == -1 ? -y : 0)) + \
                                                       (r33 == 1 ? z : (r33 == -1 ? -z : 0)))

#define ST_MAG40_REMAP_X_DATA(...)                     ST_MAG40_X_MAP(__VA_ARGS__)
#define ST_MAG40_REMAP_Y_DATA(...)                     ST_MAG40_Y_MAP(__VA_ARGS__)
#define ST_MAG40_REMAP_Z_DATA(...)                     ST_MAG40_Z_MAP(__VA_ARGS__)

/* Self Test macros */
#define ST_MAG40_ST_NUM_OF_SAMPLES        50
#define ST_MAG40_ST_MIN_THRESHOLD         10 /* 15 mGa */
#define ST_MAG40_ST_MAX_THRESHOLD        333 /* 500 mGa */

#define INFO_PRINT(fmt, ...) \
    do { \
        osLog(LOG_INFO, "%s " fmt, "[ST_MAG40]", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) \
    do { \
        if (ST_MAG40_DBG_ENABLED) { \
            osLog(LOG_DEBUG, "%s " fmt, "[ST_MAG40]", ##__VA_ARGS__); \
        } \
    } while (0);

#define ERROR_PRINT(fmt, ...) \
    do { \
        osLog(LOG_ERROR, "%s " fmt, "[ST_MAG40]", ##__VA_ARGS__); \
    } while (0);

/* DO NOT MODIFY, just to avoid compiler error if not defined using FLAGS */
#ifndef ST_MAG40_DBG_ENABLED
#define ST_MAG40_DBG_ENABLED                           0
#endif /* ST_MAG40_DBG_ENABLED */

#define ST_MAG40_MAX_PENDING_I2C_REQUESTS   4
#define ST_MAG40_MAX_I2C_TRANSFER_SIZE      6
#define ST_MAG40_MAX_MAG_EVENTS             20

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;
    uint8_t txrxBuf[ST_MAG40_MAX_I2C_TRANSFER_SIZE];
    bool last;
    bool inUse;
    uint32_t delay;
};

/* Task structure */
struct st_mag40_Task {
    uint32_t tid;

    struct SlabAllocator *magDataSlab;

    uint64_t timestampInt;

    volatile uint8_t state; //task state, type enum st_mag40_SensorState, do NOT change this directly
    uint8_t subState;

    /* sensor flags */
    uint8_t samplesToDiscard;
    uint32_t rate;
    uint64_t latency;
    bool magOn;
    bool pendingInt;
    uint8_t pendingSubState;

    uint8_t currentODR;

#if defined(ST_MAG40_CAL_ENABLED)
    struct MagCal moc;
#endif

    unsigned char       sens_buf[7];

    struct I2cTransfer transfers[ST_MAG40_MAX_PENDING_I2C_REQUESTS];

    /* Communication functions */
    void (*comm_tx)(uint8_t addr, uint8_t data, uint32_t delay, bool last);
    void (*comm_rx)(uint8_t addr, uint16_t len, uint32_t delay, bool last);

    /* irq */
    struct Gpio *Int1;
    struct ChainedIsr Isr1;

    /* Self Test */
    enum st_mag40_TestState mag_test_state;
    uint32_t mag_selftest_num;
    int32_t dataST[3];
    int32_t dataNOST[3];

    /* sensors */
    uint32_t magHandle;
};

static struct st_mag40_Task mTask;

static void sensorMagConfig(void);

#define PRI_STATE PRIi32
static int32_t getStateName(int32_t s) {
    return s;
}

// Atomic get state
#define GET_STATE() (atomicReadByte(&mTask.state))

// Atomic set state, this set the state to arbitrary value, use with caution
#define SET_STATE(s) do{\
        DEBUG_PRINT("set state %" PRI_STATE "\n", getStateName(s));\
        atomicWriteByte(&mTask.state, (s));\
    }while(0)

// Atomic switch state from IDLE to desired state.
static bool trySwitchState(enum st_mag40_SensorState newState) {
#if DBG_STATE
    bool ret = atomicCmpXchgByte(&mTask.state, SENSOR_IDLE, newState);
    uint8_t prevState = ret ? SENSOR_IDLE : GET_STATE();
    DEBUG_PRINT("switch state %" PRI_STATE "->%" PRI_STATE ", %s\n",
            getStateName(prevState), getStateName(newState), ret ? "ok" : "failed");
    return ret;
#else
    return atomicCmpXchgByte(&mTask.state, SENSOR_IDLE, newState);
#endif
}

static bool magAllocateEvt(struct TripleAxisDataEvent **evPtr)
{
    struct TripleAxisDataEvent *ev;

    ev = *evPtr = slabAllocatorAlloc(mTask.magDataSlab);
    if (!ev) {
        ERROR_PRINT("Failed to allocate mag event memory");
        return false;
    }

    memset(&ev->samples[0].firstSample, 0x00, sizeof(struct SensorFirstSample));
    return true;
}

static void magFreeEvt(void *ptr)
{
    slabAllocatorFree(mTask.magDataSlab, ptr);
}

// Allocate a buffer and mark it as in use with the given state, or return NULL
// if no buffers available. Must *not* be called from interrupt context.
static struct I2cTransfer *allocXfer(void)
{
    size_t i;

    for (i = 0; i < ARRAY_SIZE(mTask.transfers); i++) {
        if (!mTask.transfers[i].inUse) {
            mTask.transfers[i].inUse = true;
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

static void i2cCallback(void *cookie, size_t tx, size_t rx, int err);

/* delayed callback */
static void i2cDelayCallback(uint32_t timerId, void *data)
{
    struct I2cTransfer *xfer = data;

    i2cCallback((void *)xfer, xfer->tx, xfer->rx, xfer->err);
}

static void i2cCallback(void *cookie, size_t tx, size_t rx, int err)
{
    struct I2cTransfer *xfer = cookie;

    /* Do not run callback if not the last one in a set of i2c transfers */
    if (xfer && !xfer->last) {
        releaseXfer(xfer);
        return;
    }

    /* delay callback if it is the case */
    if (xfer->delay > 0) {
        xfer->tx = tx;
        xfer->rx = rx;
        xfer->err = err;

        if (!timTimerSet(xfer->delay * 1000, 0, 50, i2cDelayCallback, xfer, true)) {
            ERROR_PRINT("Cannot do delayed i2cCallback\n");
            goto handle_now;
        }

        xfer->delay = 0;
        return;
    }

handle_now:
    xfer->tx = tx;
    xfer->rx = rx;
    xfer->err = err;

    osEnqueuePrivateEvt(EVT_COMM_DONE, cookie, NULL, mTask.tid);
    if (err != 0)
        ERROR_PRINT("i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
}

static void i2c_read(uint8_t addr, uint16_t len, uint32_t delay, bool last)
{
    struct I2cTransfer *xfer = allocXfer();

    if (xfer != NULL) {
        xfer->delay = delay;
        xfer->last = last;
        xfer->txrxBuf[0] = ST_MAG40_I2C_AUTO_INCR | addr;
        i2cMasterTxRx(ST_MAG40_I2C_BUS_ID, ST_MAG40_I2C_ADDR, xfer->txrxBuf, 1, xfer->txrxBuf, len, i2cCallback, xfer);
    }
}

static void i2c_write(uint8_t addr, uint8_t data, uint32_t delay, bool last)
{
    struct I2cTransfer *xfer = allocXfer();

    if (xfer != NULL) {
        xfer->delay = delay;
        xfer->last = last;
        xfer->txrxBuf[0] = addr;
        xfer->txrxBuf[1] = data;
        i2cMasterTx(ST_MAG40_I2C_BUS_ID, ST_MAG40_I2C_ADDR, xfer->txrxBuf, 2, i2cCallback, xfer);
    }
}

#define DEC_INFO_BIAS(name, type, axis, inter, samples, rates, raw, scale, bias) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples, \
    .supportedRates = rates, \
    .rawType = raw, \
    .rawScale = scale, \
    .biasType = bias

#define DEC_INFO(name, type, axis, inter, samples, rates, raw, scale) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples, \
    .supportedRates = rates, \
    .rawType = raw, \
    .rawScale = scale,

static uint32_t st_mag40_Rates[] = {
    SENSOR_HZ(10.0f),
    SENSOR_HZ(20.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    0
};

static uint32_t st_mag40_regVal[] = {
    ST_MAG40_ODR_10_HZ,
    ST_MAG40_ODR_20_HZ,
    ST_MAG40_ODR_50_HZ,
    ST_MAG40_ODR_100_HZ,
};

static uint8_t st_mag40_computeOdr(uint32_t rate)
{
    int i;

    for (i = 0; i < (ARRAY_SIZE(st_mag40_Rates) - 1); i++) {
        if (st_mag40_Rates[i] == rate)
            break;
    }
    if (i == (ARRAY_SIZE(st_mag40_Rates) -1 )) {
        ERROR_PRINT("ODR not valid! Choosed smallest ODR available\n");
        i = 0;
    }

    return i;
}


static const struct SensorInfo st_mag40_SensorInfo =
{
#if defined(ST_MAG40_CAL_ENABLED)
    DEC_INFO_BIAS("Magnetometer", SENS_TYPE_MAG, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP,
        600, st_mag40_Rates, 0, 0, SENS_TYPE_MAG_BIAS)
#else
    DEC_INFO("Magnetometer", SENS_TYPE_MAG, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP,
        600, st_mag40_Rates, 0, 0)
#endif
};

/* Sensor Operations */
static bool magPower(bool on, void *cookie)
{
    INFO_PRINT("magPower %s\n", on ? "on" : "off");
    if (trySwitchState(SENSOR_MAG_CONFIGURATION)) {
        mTask.subState = on ? CONFIG_POWER_UP : CONFIG_POWER_DOWN;
        sensorMagConfig();
    } else {
        mTask.pendingSubState = on ? CONFIG_POWER_UP : CONFIG_POWER_DOWN;
    }

    return true;
}

static bool magFwUpload(void *cookie)
{
    return sensorSignalInternalEvt(mTask.magHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool magSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    uint8_t num = 0;

    INFO_PRINT("magSetRate %lu Hz - %llu ns\n", rate, latency);

    num = st_mag40_computeOdr(rate);
    mTask.currentODR = st_mag40_regVal[num];
    mTask.rate = rate;
    mTask.latency = latency;
    mTask.samplesToDiscard = 2;

    if (trySwitchState(SENSOR_MAG_CONFIGURATION)) {
        mTask.subState = CONFIG_SET_RATE;
        sensorMagConfig();
    } else {
        mTask.pendingSubState = CONFIG_SET_RATE;
    }

    return true;
}

static bool magFlush(void *cookie)
{
    INFO_PRINT("magFlush\n");
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_MAG), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool magCfgData(void *data, void *cookie)
{
#if defined(ST_MAG40_CAL_ENABLED)
    float *values = data;

    INFO_PRINT("magCfgData: (values in uT * 1000) %ld, %ld, %ld\n",
            (int32_t)(values[0] * 1000), (int32_t)(values[1] * 1000), (int32_t)(values[2] * 1000));

    magCalAddBias(&mTask.moc, values[0], values[1], values[2]);
#endif

    return true;
}

static void sendTestResult(uint8_t status, uint8_t sensorType)
{
    struct TestResultData *data = heapAlloc(sizeof(struct TestResultData));
    if (!data) {
        ERROR_PRINT("Couldn't alloc test result packet");
        return;
    }

    data->header.appId = ST_MAG40_APP_ID;
    data->header.dataLen = (sizeof(struct TestResultData) - sizeof(struct HostHubRawPacket));
    data->data_header.msgId = SENSOR_APP_MSG_ID_TEST_RESULT;
    data->data_header.sensorType = sensorType;
    data->data_header.status = status;

    if (!osEnqueueEvtOrFree(EVT_APP_TO_HOST, data, heapFree))
        ERROR_PRINT("Couldn't send test result packet");
}

static void magTestHandling(struct I2cTransfer *xfer)
{
    int32_t dataGap[3];

    switch(mTask.mag_test_state) {
    case MAG_SELFTEST_INIT:
        mTask.mag_selftest_num = 0;
        memset(mTask.dataNOST, 0, 3 * sizeof(int32_t));

        mTask.mag_test_state = MAG_SELFTEST_RUN_ST_OFF;
        mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR, ST_MAG40_ODR_100_HZ, 0, false);
        mTask.comm_tx(ST_MAG40_CFG_B_REG_ADDR, ST_MAG40_OFF_CANC, 0, false);
        mTask.comm_tx(ST_MAG40_CFG_C_REG_ADDR, ST_MAG40_BDU_ON, 0, true);
        break;

    case MAG_SELFTEST_RUN_ST_OFF:
        if (mTask.mag_selftest_num++ > 0) {
            uint8_t *raw = &xfer->txrxBuf[0];

            mTask.dataNOST[0] += (*(int16_t *)&raw[0]);
            mTask.dataNOST[1] += (*(int16_t *)&raw[2]);
            mTask.dataNOST[2] += (*(int16_t *)&raw[4]);
        }

        if (mTask.mag_selftest_num <= ST_MAG40_ST_NUM_OF_SAMPLES) {
            mTask.comm_rx(ST_MAG40_OUTXL_REG_ADDR, 6, 10000, true);

            break;
        }

        mTask.dataNOST[0] /= ST_MAG40_ST_NUM_OF_SAMPLES;
        mTask.dataNOST[1] /= ST_MAG40_ST_NUM_OF_SAMPLES;
        mTask.dataNOST[2] /= ST_MAG40_ST_NUM_OF_SAMPLES;
        mTask.mag_test_state = MAG_SELFTEST_INIT_ST_EN;
        /* fall through */

    case MAG_SELFTEST_INIT_ST_EN:
        mTask.mag_selftest_num = 0;
        memset(mTask.dataST, 0, 3 * sizeof(int32_t));

        mTask.mag_test_state = MAG_SELFTEST_RUN_ST_ON;
        mTask.comm_tx(ST_MAG40_CFG_C_REG_ADDR, ST_MAG40_BDU_ON | ST_MAG40_SELFTEST_EN, 0, true);
        break;

    case MAG_SELFTEST_RUN_ST_ON:
        if (mTask.mag_selftest_num++ > 0) {
            uint8_t *raw = &xfer->txrxBuf[0];

            mTask.dataST[0] += (*(int16_t *)&raw[0]);
            mTask.dataST[1] += (*(int16_t *)&raw[2]);
            mTask.dataST[2] += (*(int16_t *)&raw[4]);
        }

        if (mTask.mag_selftest_num <= ST_MAG40_ST_NUM_OF_SAMPLES) {
            mTask.comm_rx(ST_MAG40_OUTXL_REG_ADDR, 6, 10000, true);

            break;
        }

        mTask.dataST[0] /= ST_MAG40_ST_NUM_OF_SAMPLES;
        mTask.dataST[1] /= ST_MAG40_ST_NUM_OF_SAMPLES;
        mTask.dataST[2] /= ST_MAG40_ST_NUM_OF_SAMPLES;
        mTask.mag_test_state = MAG_SELFTEST_VERIFY;

        /* fall through */

    case MAG_SELFTEST_VERIFY:
        dataGap[0] = abs(mTask.dataST[0] - mTask.dataNOST[0]);
        dataGap[1] = abs(mTask.dataST[1] - mTask.dataNOST[1]);
        dataGap[2] = abs(mTask.dataST[2] - mTask.dataNOST[2]);

        if (dataGap[0] >= ST_MAG40_ST_MIN_THRESHOLD &&
            dataGap[0] <= ST_MAG40_ST_MAX_THRESHOLD &&
            dataGap[1] >= ST_MAG40_ST_MIN_THRESHOLD &&
            dataGap[1] <= ST_MAG40_ST_MAX_THRESHOLD &&
            dataGap[2] >= ST_MAG40_ST_MIN_THRESHOLD &&
            dataGap[2] <= ST_MAG40_ST_MAX_THRESHOLD)
                sendTestResult(SENSOR_APP_EVT_STATUS_SUCCESS, SENS_TYPE_MAG);
        else
                sendTestResult(SENSOR_APP_EVT_STATUS_ERROR, SENS_TYPE_MAG);

        mTask.mag_test_state = MAG_SELFTEST_DONE;
        mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR, ST_MAG40_TEMP_COMP_EN | ST_MAG40_POWER_IDLE, 0, false);
        mTask.comm_tx(ST_MAG40_CFG_C_REG_ADDR, ST_MAG40_BDU_ON | ST_MAG40_INT_MAG, 0, true);
        break;

    case MAG_SELFTEST_DONE:
        break;
    }
}

static bool magSelfTest(void *cookie)
{
    INFO_PRINT("magSelfTest\n");

    if (!mTask.magOn && trySwitchState(SENSOR_SELF_TEST)) {
        mTask.mag_test_state = MAG_SELFTEST_INIT;
        magTestHandling(NULL);
        return true;
    } else {
        ERROR_PRINT("cannot test mag because sensor is busy\n");
        sendTestResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_MAG);
        return false;
    }
}

#define DEC_OPS(power, firmware, rate, flush, test, cal, cfg) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush, \
    .sensorCalibrate = cal, \
    .sensorSelfTest = test, \
    .sensorCfgData = cfg

static const struct SensorOps st_mag40_SensorOps =
{
    DEC_OPS(magPower, magFwUpload, magSetRate, magFlush, magSelfTest, NULL, magCfgData),
};

static void enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_RISING);
    extiChainIsr(ST_MAG40_INT_IRQ, isr);
}

static void disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiUnchainIsr(ST_MAG40_INT_IRQ, isr);
    extiDisableIntGpio(pin);
}

static bool st_mag40_int1_isr(struct ChainedIsr *isr)
{
    if (!extiIsPendingGpio(mTask.Int1))
        return false;

    mTask.timestampInt = rtcGetTime();

    /* Start sampling for a value */
    if (!osEnqueuePrivateEvt(EVT_SENSOR_INTERRUPT, NULL, NULL, mTask.tid))
        ERROR_PRINT("st_mag40_int1_isr: osEnqueuePrivateEvt() failed\n");

    extiClearPendingGpio(mTask.Int1);
    return true;
}

#define TIME_NS_TO_US(ns)    cpuMathU64DivByU16(ns, 1000)
#define kScale_mag      0.15f /* in uT - (1.5f / 10) */

static void parseRawData(uint8_t *raw)
{
    struct TripleAxisDataEvent *magSample;

    int32_t raw_x = (*(int16_t *)&raw[0]);
    int32_t raw_y = (*(int16_t *)&raw[2]);
    int32_t raw_z = (*(int16_t *)&raw[4]);
    float x, y, z;
    float xs, ys, zs;
    bool newMagnCalibData;
#if defined(ST_MAG40_CAL_ENABLED)
    float xi, yi, zi;
#endif

	/* Discard samples generated during sensor turn-on time */
    if (mTask.samplesToDiscard > 0) {
        mTask.samplesToDiscard--;
        return;
    }

    /* in uT */
    xs = (float)raw_x * kScale_mag;
    ys = (float)raw_y * kScale_mag;
    zs = (float)raw_z * kScale_mag;

    /* rotate axes */
    x = ST_MAG40_REMAP_X_DATA(xs, ys, zs, ST_MAG40_ROT_MATRIX);
    y = ST_MAG40_REMAP_Y_DATA(xs, ys, zs, ST_MAG40_ROT_MATRIX);
    z = ST_MAG40_REMAP_Z_DATA(xs, ys, zs, ST_MAG40_ROT_MATRIX);

#if defined(ST_MAG40_CAL_ENABLED)
    magCalRemoveSoftiron(&mTask.moc, x, y, z, &xi, &yi, &zi);

    newMagnCalibData = magCalUpdate(&mTask.moc, TIME_NS_TO_US(mTask.timestampInt), xi, yi, zi);

    magCalRemoveBias(&mTask.moc, xi, yi, zi, &x, &y, &z);
#endif

    if (magAllocateEvt(&magSample) == false)
        return;

    magSample->referenceTime = mTask.timestampInt;
    magSample->samples[0].deltaTime = 0;
    magSample->samples[0].firstSample.numSamples = 1;
    magSample->samples[0].x = x;
    magSample->samples[0].y = y;
    magSample->samples[0].z = z;

#if defined(ST_MAG40_CAL_ENABLED)
    if (newMagnCalibData) {
        magSample->samples[1].deltaTime = 0;
        magCalGetBias(&mTask.moc,
                     &magSample->samples[1].x,
                     &magSample->samples[1].y,
                     &magSample->samples[1].z);

        magSample->referenceTime = mTask.timestampInt;
        magSample->samples[0].firstSample.numSamples = 2;
        magSample->samples[0].firstSample.biasCurrent = true;
        magSample->samples[0].firstSample.biasPresent = 1;
        magSample->samples[0].firstSample.biasSample = 1;
    }
#endif

    osEnqueueEvtOrFree(sensorGetMyEventType(SENS_TYPE_MAG), magSample, magFreeEvt);
}

static uint8_t *wai;

static void int2Evt(void)
{
    if (trySwitchState(SENSOR_READ_SAMPLES)) {
        mTask.comm_rx(ST_MAG40_OUTXL_REG_ADDR, 6, 0, true);
    } else {
        mTask.pendingInt = true;
    }
}

static void processPendingEvt(void)
{
    if (mTask.pendingInt) {
        mTask.pendingInt = false;
        int2Evt();
        return;
    }

    if (mTask.pendingSubState != NO_SUBSTATE) {
        if (trySwitchState(SENSOR_MAG_CONFIGURATION)) {
            mTask.subState = mTask.pendingSubState;
            mTask.pendingSubState = NO_SUBSTATE;
            sensorMagConfig();
        }
    }
}

static void sensorMagConfig(void)
{
    uint8_t tmp;

    switch (mTask.subState) {
    case CONFIG_POWER_UP:
        mTask.subState = CONFIG_POWER_UP_2;
        mTask.comm_tx(ST_MAG40_CFG_B_REG_ADDR, ST_MAG40_OFF_CANC, 0, false);
        mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR,
                      ST_MAG40_TEMP_COMP_EN | ST_MAG40_POWER_ON | mTask.currentODR, 0, true);
        break;

    case CONFIG_POWER_UP_2:
        mTask.subState = CONFIG_DONE;
        mTask.magOn = true;
        sensorSignalInternalEvt(mTask.magHandle,
            SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case CONFIG_POWER_DOWN:
        mTask.subState = CONFIG_POWER_DOWN_2;
        mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR,
                      ST_MAG40_TEMP_COMP_EN | ST_MAG40_POWER_IDLE | mTask.currentODR, 0, true);
        break;

    case CONFIG_POWER_DOWN_2:
        mTask.subState = CONFIG_DONE;
        mTask.magOn = false;
        sensorSignalInternalEvt(mTask.magHandle,
            SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case CONFIG_SET_RATE:
        mTask.subState = CONFIG_SET_RATE_2;
        tmp = mTask.magOn ? ST_MAG40_POWER_ON : ST_MAG40_POWER_IDLE;
        tmp |= mTask.currentODR;
        mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR, ST_MAG40_TEMP_COMP_EN | tmp, 0, true);
        break;

    case CONFIG_SET_RATE_2:
        mTask.subState = CONFIG_DONE;
        sensorSignalInternalEvt(mTask.magHandle,
                SENSOR_INTERNAL_EVT_RATE_CHG, mTask.rate, mTask.latency);
        break;

    default:
        /* Something weird happened */
        ERROR_PRINT("sensorMagConfig() subState=%d\n", mTask.subState);
        mTask.subState = CONFIG_DONE;
        break;
    }
}

/* initial sensor configuration */
static void sensorInit(void)
{
    switch (mTask.subState) {
    case INIT_START:
        mTask.subState = INIT_ENABLE_DRDY;
        mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR,
                    ST_MAG40_SOFT_RESET_BIT, 0, true);
        break;

    case INIT_ENABLE_DRDY:
        mTask.subState = INIT_DONE;
        mTask.comm_rx(ST_MAG40_OUTXL_REG_ADDR, 6, 0, false);
        mTask.comm_tx(ST_MAG40_CFG_C_REG_ADDR,
                    ST_MAG40_BDU_ON | ST_MAG40_INT_MAG, 0, true);
        break;

    default:
        /* Something weird happened */
        ERROR_PRINT("sensorInit() subState=%d\n", mTask.subState);
        mTask.subState = INIT_DONE;
        break;
    }
}

static void handleCommDoneEvt(const void* evtData)
{
    bool returnIdle = false;
    struct I2cTransfer *xfer = (struct I2cTransfer *)evtData;

    switch (GET_STATE()) {
    case SENSOR_BOOT:
        SET_STATE(SENSOR_VERIFY_ID);

        mTask.comm_rx(ST_MAG40_WAI_REG_ADDR, 1, 0, true);
        break;

    case SENSOR_VERIFY_ID:
        /* Check the sensor ID */
        wai = &xfer->txrxBuf[0];

        if (ST_MAG40_WAI_REG_VAL != wai[0]) {
            DEBUG_PRINT("WAI returned is: %02x\n\n", *wai);
            SET_STATE(SENSOR_BOOT);
            mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR,
                        ST_MAG40_SOFT_RESET_BIT, 0, true);
            break;
        }

        INFO_PRINT( "Device ID is correct! (%02x)\n", *wai);
        SET_STATE(SENSOR_INITIALIZATION);
        mTask.subState = INIT_START;
        sensorInit();

        break;

    case SENSOR_INITIALIZATION:
        if (mTask.subState == INIT_DONE) {
            INFO_PRINT( "Initialization completed\n");
            returnIdle = true;
            sensorRegisterInitComplete(mTask.magHandle);
        } else {
            sensorInit();
        }

        break;

    case SENSOR_MAG_CONFIGURATION:
        if (mTask.subState != CONFIG_DONE)
            sensorMagConfig();
        if (mTask.subState == CONFIG_DONE)
            returnIdle = true;
        break;

    case SENSOR_READ_SAMPLES:
        returnIdle = true;

        if (gpioGet(mTask.Int1)) {
            ERROR_PRINT("error read sensor, retry!\n");
        }
        if (mTask.magOn)
            parseRawData(&xfer->txrxBuf[0]);
        break;

    case SENSOR_SELF_TEST:
        if (mTask.mag_test_state == MAG_SELFTEST_DONE)
            returnIdle = true;
        else
            magTestHandling(xfer);

        break;

    case SENSOR_IDLE:
    default:
        break;
    }

    releaseXfer(xfer);

    if (returnIdle) {
        SET_STATE(SENSOR_IDLE);
        processPendingEvt();
    }
}

static void handleEvent(uint32_t evtType, const void* evtData)
{
    switch (evtType) {
    case EVT_APP_START:
        INFO_PRINT("EVT_APP_START\n");
        osEventUnsubscribe(mTask.tid, EVT_APP_START);

        SET_STATE(SENSOR_BOOT);
        mTask.comm_tx(ST_MAG40_CFG_A_REG_ADDR,
                        ST_MAG40_SOFT_RESET_BIT, 0, true);

        break;

    case EVT_COMM_DONE:
        handleCommDoneEvt(evtData);
        break;

    case EVT_SENSOR_INTERRUPT:
        int2Evt();
        break;

    default:
        break;
    }

}

static bool startTask(uint32_t task_id)
{
    size_t slabSize;

    mTask.tid = task_id;

    INFO_PRINT("I2C DRIVER started\n");

    mTask.magOn = false;
    mTask.pendingInt = false;
    mTask.pendingSubState = NO_SUBSTATE;

    mTask.currentODR = ST_MAG40_ODR_10_HZ;
    mTask.timestampInt = 0;

    slabSize = sizeof(struct TripleAxisDataEvent) + sizeof(struct TripleAxisDataPoint);
#if defined(ST_MAG40_CAL_ENABLED)
    slabSize += sizeof(struct TripleAxisDataPoint);
#endif

    mTask.magDataSlab = slabAllocatorNew(slabSize, 4, ST_MAG40_MAX_MAG_EVENTS);
    if (!mTask.magDataSlab) {
        ERROR_PRINT("Failed to allocate magDataSlab memory\n");
        return false;
    }

    /* Init the communication part */
    i2cMasterRequest(ST_MAG40_I2C_BUS_ID, ST_MAG40_I2C_SPEED);

    mTask.comm_tx = i2c_write;
    mTask.comm_rx = i2c_read;

    /* irq */
    mTask.Int1 = gpioRequest(ST_MAG40_INT_PIN);
    gpioConfigInput(mTask.Int1, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    mTask.Isr1.func = st_mag40_int1_isr;
    enableInterrupt(mTask.Int1, &mTask.Isr1);

#if defined(ST_MAG40_CAL_ENABLED)
    initMagCal(&mTask.moc,
            0.0f, 0.0f, 0.0f,      // bias x, y, z
            1.0f, 0.0f, 0.0f,      // c00, c01, c02
            0.0f, 1.0f, 0.0f,      // c10, c11, c12
            0.0f, 0.0f, 1.0f);     // c20, c21, c22
#endif

    mTask.magHandle =
            sensorRegister(&st_mag40_SensorInfo, &st_mag40_SensorOps, NULL, false);

    osEventSubscribe(mTask.tid, EVT_APP_START);

    return true;
}

static void endTask(void)
{
    INFO_PRINT("ended\n");
    slabAllocatorDestroy(mTask.magDataSlab);
    disableInterrupt(mTask.Int1, &mTask.Isr1);
}

INTERNAL_APP_INIT(ST_MAG40_APP_ID, 0, startTask, endTask, handleEvent);
