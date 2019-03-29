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

#include <algos/time_sync.h>
#include <atomic.h>
#include <cpu/cpuMath.h>
#include <errno.h>
#include <gpio.h>
#include <heap.h>
#include <halIntf.h>
#include <hostIntf.h>
#include <i2c.h>
#include <isr.h>
#include <nanohub_math.h>
#include <nanohubPacket.h>
#include <printf.h>
#include <plat/exti.h>
#include <plat/gpio.h>
#include <plat/syscfg.h>
#include <plat/rtc.h>
#include <sensors.h>
#include <seos.h>
#include <slab.h>
#include <spi.h>
#include <timer.h>
#include <variant/sensType.h>
#include <variant/variant.h>

#ifdef MAG_SLAVE_PRESENT
#include <calibration/magnetometer/mag_cal.h>
#endif

#ifdef ACCEL_CAL_ENABLED
#include <calibration/accelerometer/accel_cal.h>
#endif

#if defined(OVERTEMPCAL_ENABLED) && !defined(GYRO_CAL_ENABLED)
#undef OVERTEMPCAL_ENABLED
#endif

#if defined(GYRO_CAL_DBG_ENABLED) && !defined(GYRO_CAL_ENABLED)
#undef GYRO_CAL_DBG_ENABLED
#endif

#if defined(OVERTEMPCAL_DBG_ENABLED) && !defined(OVERTEMPCAL_ENABLED)
#undef OVERTEMPCAL_DBG_ENABLED
#endif

#ifdef GYRO_CAL_ENABLED
#include <calibration/gyroscope/gyro_cal.h>
#include <common/math/macros.h>
#endif  // GYRO_CAL_ENABLED

#if defined(GYRO_CAL_DBG_ENABLED) || defined(OVERTEMPCAL_DBG_ENABLED)
#include <calibration/util/cal_log.h>
#endif  // GYRO_CAL_DBG_ENABLED || OVERTEMPCAL_DBG_ENABLED

#ifdef OVERTEMPCAL_ENABLED
#include <calibration/over_temp/over_temp_cal.h>
#endif  // OVERTEMPCAL_ENABLED

#include <limits.h>
#include <stdlib.h>
#include <string.h>

#define VERBOSE_PRINT(fmt, ...) do { \
        osLog(LOG_VERBOSE, "%s " fmt, "[BMI160]", ##__VA_ARGS__); \
    } while (0);

#define INFO_PRINT(fmt, ...) do { \
        osLog(LOG_INFO, "%s " fmt, "[BMI160]", ##__VA_ARGS__); \
    } while (0);

#define ERROR_PRINT(fmt, ...) do { \
        osLog(LOG_ERROR, "%s " fmt, "[BMI160] ERROR:", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) do { \
        if (DBG_ENABLE) {  \
            osLog(LOG_DEBUG, "%s " fmt, "[BMI160]", ##__VA_ARGS__); \
        } \
    } while (0);

#define DEBUG_PRINT_IF(cond, fmt, ...) do { \
        if ((cond) && DBG_ENABLE) {  \
            osLog(LOG_DEBUG, "%s " fmt, "[BMI160]", ##__VA_ARGS__); \
        } \
    } while (0);

#define DBG_ENABLE                0
#define DBG_CHUNKED               0
#define DBG_INT                   0
#define DBG_SHALLOW_PARSE         0
#define DBG_STATE                 0
#define DBG_WM_CALC               0
#define TIMESTAMP_DBG             0

#define BMI160_APP_VERSION 17

// fixme: to list required definitions for a slave mag
#ifdef USE_BMM150
#include "bosch_bmm150_slave.h"
#elif USE_AK09915
#include "akm_ak09915_slave.h"
#endif

#define BMI160_APP_ID APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 2)

#ifdef BMI160_I2C_BUS_ID
#define BMI160_USE_I2C

#ifndef BMI160_I2C_SPEED
#define BMI160_I2C_SPEED          400000
#endif
#ifndef BMI160_I2C_ADDR
#define BMI160_I2C_ADDR           0x68
#endif
#endif

#define BMI160_SPI_WRITE          0x00
#define BMI160_SPI_READ           0x80

#define BMI160_SPI_BUS_ID         1
#define BMI160_SPI_SPEED_HZ       8000000
#define BMI160_SPI_MODE           3

#ifndef BMI160_INT1_IRQ
#define BMI160_INT1_IRQ           EXTI9_5_IRQn
#endif
#ifndef BMI160_INT1_PIN
#define BMI160_INT1_PIN           GPIO_PB(6)
#endif
#ifndef BMI160_INT2_IRQ
#define BMI160_INT2_IRQ           EXTI9_5_IRQn
#endif
#ifndef BMI160_INT2_PIN
#define BMI160_INT2_PIN           GPIO_PB(7)
#endif

#define BMI160_ID                 0xd1

#define BMI160_REG_ID             0x00
#define BMI160_REG_ERR            0x02
#define BMI160_REG_PMU_STATUS     0x03
#define BMI160_REG_DATA_0         0x04
#define BMI160_REG_DATA_1         0x05
#define BMI160_REG_DATA_14        0x12
#define BMI160_REG_SENSORTIME_0   0x18
#define BMI160_REG_STATUS         0x1b
#define BMI160_REG_INT_STATUS_0   0x1c
#define BMI160_REG_INT_STATUS_1   0x1d
#define BMI160_REG_TEMPERATURE_0  0x20
#define BMI160_REG_TEMPERATURE_1  0x21
#define BMI160_REG_FIFO_LENGTH_0  0x22
#define BMI160_REG_FIFO_DATA      0x24
#define BMI160_REG_ACC_CONF       0x40
#define BMI160_REG_ACC_RANGE      0x41
#define BMI160_REG_GYR_CONF       0x42
#define BMI160_REG_GYR_RANGE      0x43
#define BMI160_REG_MAG_CONF       0x44
#define BMI160_REG_FIFO_DOWNS     0x45
#define BMI160_REG_FIFO_CONFIG_0  0x46
#define BMI160_REG_FIFO_CONFIG_1  0x47
#define BMI160_REG_MAG_IF_0       0x4b
#define BMI160_REG_MAG_IF_1       0x4c
#define BMI160_REG_MAG_IF_2       0x4d
#define BMI160_REG_MAG_IF_3       0x4e
#define BMI160_REG_MAG_IF_4       0x4f
#define BMI160_REG_INT_EN_0       0x50
#define BMI160_REG_INT_EN_1       0x51
#define BMI160_REG_INT_EN_2       0x52
#define BMI160_REG_INT_OUT_CTRL   0x53
#define BMI160_REG_INT_LATCH      0x54
#define BMI160_REG_INT_MAP_0      0x55
#define BMI160_REG_INT_MAP_1      0x56
#define BMI160_REG_INT_MAP_2      0x57
#define BMI160_REG_INT_DATA_0     0x58
#define BMI160_REG_INT_MOTION_0   0x5f
#define BMI160_REG_INT_MOTION_1   0x60
#define BMI160_REG_INT_MOTION_2   0x61
#define BMI160_REG_INT_MOTION_3   0x62
#define BMI160_REG_INT_TAP_0      0x63
#define BMI160_REG_INT_TAP_1      0x64
#define BMI160_REG_INT_FLAT_0     0x67
#define BMI160_REG_INT_FLAT_1     0x68
#define BMI160_REG_PMU_TRIGGER    0x6C
#define BMI160_REG_FOC_CONF       0x69
#define BMI160_REG_CONF           0x6a
#define BMI160_REG_IF_CONF        0x6b
#define BMI160_REG_SELF_TEST      0x6d
#define BMI160_REG_OFFSET_0       0x71
#define BMI160_REG_OFFSET_3       0x74
#define BMI160_REG_OFFSET_6       0x77
#define BMI160_REG_STEP_CNT_0     0x78
#define BMI160_REG_STEP_CONF_0    0x7a
#define BMI160_REG_STEP_CONF_1    0x7b
#define BMI160_REG_CMD            0x7e
#define BMI160_REG_MAGIC          0x7f

#define INT_STEP        0x01
#define INT_ANY_MOTION  0x04
#define INT_DOUBLE_TAP  0x10
#define INT_SINGLE_TAP  0x20
#define INT_ORIENT      0x40
#define INT_FLAT        0x80
#define INT_HIGH_G_Z    0x04
#define INT_LOW_G       0x08
#define INT_DATA_RDY    0x10
#define INT_FIFO_FULL   0x20
#define INT_FIFO_WM     0x40
#define INT_NO_MOTION   0x80

#define BMI160_FRAME_HEADER_INVALID  0x80   // mark the end of valid data
#define BMI160_FRAME_HEADER_SKIP     0x81   // not defined by hw, used for skip a byte in buffer

#define WATERMARK_MIN                1
#define WATERMARK_MAX                200    // must <= 255 (0xff)

#define WATERMARK_MAX_SENSOR_RATE    400    // Accel and gyro are 400 Hz max
#define WATERMARK_TIME_UNIT_NS       (1000000000ULL/(WATERMARK_MAX_SENSOR_RATE))

#define gSPI    BMI160_SPI_BUS_ID

#define ACCL_INT_LINE EXTI_LINE_P6
#define GYR_INT_LINE EXTI_LINE_P7

#define SPI_WRITE_0(addr, data) spiQueueWrite(addr, data, 2)
#define SPI_WRITE_1(addr, data, delay) spiQueueWrite(addr, data, delay)
#define GET_SPI_WRITE_MACRO(_1,_2,_3,NAME,...) NAME
#define SPI_WRITE(...) GET_SPI_WRITE_MACRO(__VA_ARGS__, SPI_WRITE_1, SPI_WRITE_0)(__VA_ARGS__)

#define SPI_READ_0(addr, size, buf) spiQueueRead(addr, size, buf, 0)
#define SPI_READ_1(addr, size, buf, delay) spiQueueRead(addr, size, buf, delay)
#define GET_SPI_READ_MACRO(_1,_2,_3,_4,NAME,...) NAME
#define SPI_READ(...) GET_SPI_READ_MACRO(__VA_ARGS__, SPI_READ_1, SPI_READ_0)(__VA_ARGS__)

#define EVT_SENSOR_ACC_DATA_RDY sensorGetMyEventType(SENS_TYPE_ACCEL)
#define EVT_SENSOR_GYR_DATA_RDY sensorGetMyEventType(SENS_TYPE_GYRO)
#define EVT_SENSOR_MAG_DATA_RDY sensorGetMyEventType(SENS_TYPE_MAG)
#define EVT_SENSOR_STEP sensorGetMyEventType(SENS_TYPE_STEP_DETECT)
#define EVT_SENSOR_NO_MOTION sensorGetMyEventType(SENS_TYPE_NO_MOTION)
#define EVT_SENSOR_ANY_MOTION sensorGetMyEventType(SENS_TYPE_ANY_MOTION)
#define EVT_SENSOR_FLAT sensorGetMyEventType(SENS_TYPE_FLAT)
#define EVT_SENSOR_DOUBLE_TAP sensorGetMyEventType(SENS_TYPE_DOUBLE_TAP)
#define EVT_SENSOR_STEP_COUNTER sensorGetMyEventType(SENS_TYPE_STEP_COUNT)

#define MAX_NUM_COMMS_EVENT_SAMPLES 15

// Default accel range is 8g
#ifndef BMI160_ACC_RANGE_G
#define BMI160_ACC_RANGE_G 8
#endif

#if BMI160_ACC_RANGE_G == 16
#define ACC_RANGE_SETTING 0x0c
#elif BMI160_ACC_RANGE_G == 8
#define ACC_RANGE_SETTING 0x08
#else
#error "Invalid BMI160_ACC_RANGE_G setting: valid values are 8, 16"
#endif

#define kScale_acc    (9.81f * BMI160_ACC_RANGE_G / 32768.0f)
#define kScale_gyr    0.00053263221f  // GYR_range * M_PI / (180.0f * 32768.0f);
#define kScale_temp   0.001953125f    // temperature in deg C
#define kTempInvalid  -1000.0f

#define kTimeSyncPeriodNs        100000000ull // sync sensor and RTC time every 100ms
#define kSensorTimerIntervalUs   39ull        // bmi160 clock increaments every 39000ns

#define kMinRTCTimeIncrementNs   1250000ull // forced min rtc time increment, 1.25ms for 400Hz
#define kMinSensorTimeIncrement  64         // forced min sensortime increment,
                                            // 64 = 2.5 msec for 400Hz

#define ACC_MIN_RATE    5
#define GYR_MIN_RATE    6
#define ACC_MAX_RATE    12
#define GYR_MAX_RATE    13
#define MAG_MAX_RATE    11
#define ACC_MAX_OSR     3
#define GYR_MAX_OSR     4
#define ODR_100HZ       8
#define ODR_200HZ       9

#define MOTION_ODR         7

#define RETRY_CNT_CALIBRATION 10
#define RETRY_CNT_ID 5
#define RETRY_CNT_MAG 30

#define SPI_PACKET_SIZE 30
#define FIFO_READ_SIZE  (1024+4)
#define CHUNKED_READ_SIZE (64)
#define BUF_MARGIN 32   // some extra buffer for additional reg RW when a FIFO read happens
#define SPI_BUF_SIZE (FIFO_READ_SIZE + CHUNKED_READ_SIZE + BUF_MARGIN)

#ifndef ABS
#define ABS(x) (((x) > 0) ? (x) : -(x))
#endif

enum SensorIndex {
    FIRST_CONT_SENSOR = 0,
    ACC = FIRST_CONT_SENSOR,
    GYR,
#ifdef MAG_SLAVE_PRESENT
    MAG,
#endif
    NUM_CONT_SENSOR,
    FIRST_ONESHOT_SENSOR = NUM_CONT_SENSOR,
    STEP = FIRST_ONESHOT_SENSOR,
    DTAP,
    FLAT,
    ANYMO,
    NOMO,
    STEPCNT,
    NUM_OF_SENSOR,
};

enum SensorEvents {
    NO_EVT = -1,
    EVT_SPI_DONE = EVT_APP_START + 1,
    EVT_SENSOR_INTERRUPT_1,
    EVT_SENSOR_INTERRUPT_2,
    EVT_TIME_SYNC,
};

enum InitState {
    RESET_BMI160,
    INIT_BMI160,
    INIT_MAG,
    INIT_ON_CHANGE_SENSORS,
    INIT_DONE,
};

enum CalibrationState {
    CALIBRATION_START,
    CALIBRATION_FOC,
    CALIBRATION_WAIT_FOC_DONE,
    CALIBRATION_SET_OFFSET,
    CALIBRATION_DONE,
    CALIBRATION_TIMEOUT,
};

enum AccTestState {
    ACC_TEST_START,
    ACC_TEST_CONFIG,
    ACC_TEST_RUN_0,
    ACC_TEST_RUN_1,
    ACC_TEST_VERIFY,
    ACC_TEST_DONE
};

enum GyroTestState {
    GYRO_TEST_START,
    GYRO_TEST_RUN,
    GYRO_TEST_VERIFY,
    GYRO_TEST_DONE
};

enum SensorState {
    // keep this in sync with getStateName
    SENSOR_BOOT,
    SENSOR_VERIFY_ID,
    SENSOR_INITIALIZING,
    SENSOR_IDLE,
    SENSOR_POWERING_UP,
    SENSOR_POWERING_DOWN,
    SENSOR_CONFIG_CHANGING,
    SENSOR_INT_1_HANDLING,
    SENSOR_INT_2_HANDLING,
    SENSOR_CALIBRATING,
    SENSOR_TESTING,
    SENSOR_STEP_CNT,
    SENSOR_TIME_SYNC,
    SENSOR_SAVE_CALIBRATION,
    SENSOR_NUM_OF_STATE
};
#if DBG_STATE
#define PRI_STATE "s"
static const char * getStateName(int32_t s) {
    // keep this in sync with SensorState
    static const char* const l[] = {"BOOT", "VERIFY_ID", "INIT", "IDLE", "PWR_UP",
            "PWR-DN", "CFG_CHANGE", "INT1", "INT2", "CALIB", "STEP_CNT", "SYNC", "SAVE_CALIB"};
    if (s >= 0 && s < SENSOR_NUM_OF_STATE) {
        return l[s];
    }
    return "???";
#else
#define PRI_STATE PRIi32
static int32_t getStateName(int32_t s) {
    return s;
#endif
}

enum MagConfigState {
    MAG_SET_START,
    MAG_SET_IF,

    // BMM150 only
    MAG_SET_REPXY,
    MAG_SET_REPZ,
    MAG_GET_DIG_X,
    MAG_GET_DIG_Y,
    MAG_GET_DIG_Z,
    MAG_SET_SAVE_DIG,

    MAG_SET_FORCE,
    MAG_SET_ADDR,
    MAG_SET_DATA,
    MAG_SET_DONE,

    MAG_INIT_FAILED
};

struct ConfigStat {
    uint64_t latency;
    uint32_t rate;
    bool enable;
};

struct CalibrationData {
    struct HostHubRawPacket header;
    struct SensorAppEventHeader data_header;
    int32_t xBias;
    int32_t yBias;
    int32_t zBias;
} __attribute__((packed));

struct TestResultData {
    struct HostHubRawPacket header;
    struct SensorAppEventHeader data_header;
} __attribute__((packed));

struct BMI160Sensor {
    struct ConfigStat pConfig; // pending config status request
    struct TripleAxisDataEvent *data_evt;
    uint32_t handle;
    uint32_t rate;
    uint64_t latency;
    uint64_t prev_rtc_time;
    uint32_t offset[3];
    bool powered; // activate status
    bool configed; // configure status
    bool offset_enable;
    uint8_t flush;
    enum SensorIndex idx;
};

struct OtcGyroUpdateBuffer {
    struct AppToSensorHalDataBuffer head;
    struct GyroOtcData data;
    volatile uint8_t lock; // lock for static object
    bool sendToHostRequest;
} __attribute__((packed));

struct BMI160Task {
    uint32_t tid;
    struct BMI160Sensor sensors[NUM_OF_SENSOR];

#ifdef GYRO_CAL_ENABLED
    // Gyro Cal -- Declaration.
    struct GyroCal gyro_cal;
#endif  //  GYRO_CAL_ENABLED

#ifdef OVERTEMPCAL_ENABLED
    // Over-temp gyro calibration object.
    struct OverTempCal over_temp_gyro_cal;
    struct OtcGyroUpdateBuffer otcGyroUpdateBuffer;
#endif  //  OVERTEMPCAL_ENABLED

    // time keeping.
    uint64_t last_sensortime;
    uint64_t frame_sensortime;
    uint64_t prev_frame_time[NUM_CONT_SENSOR];
    uint64_t time_delta[NUM_CONT_SENSOR];
    uint64_t next_delta[NUM_CONT_SENSOR];
    uint64_t tempTime;
    uint64_t timesync_rtc_time;

    // spi and interrupt
    spi_cs_t cs;
    struct SpiMode mode;
    struct SpiPacket packets[SPI_PACKET_SIZE];
    struct SpiDevice *spiDev;
    struct Gpio *Int1;
    struct Gpio *Int2;
    IRQn_Type Irq1;
    IRQn_Type Irq2;
    struct ChainedIsr Isr1;
    struct ChainedIsr Isr2;
#ifdef ACCEL_CAL_ENABLED
    struct AccelCal acc;
#endif
#ifdef MAG_SLAVE_PRESENT
    struct MagCal moc;
#endif
    time_sync_t gSensorTime2RTC;

    float tempCelsius;
    float last_charging_bias_x;
    uint32_t total_step_cnt;
    uint32_t last_step_cnt;
    uint32_t poll_generation;
    uint32_t active_poll_generation;
    uint8_t active_oneshot_sensor_cnt;
    uint8_t interrupt_enable_0;
    uint8_t interrupt_enable_2;
    uint8_t acc_downsample;
    uint8_t gyr_downsample;
    bool magBiasPosted;
    bool magBiasCurrent;
    bool fifo_enabled[NUM_CONT_SENSOR];

    // for step count
    uint32_t stepCntSamplingTimerHandle;
    bool step_cnt_changed;

    // spi buffers
    int xferCnt;
    uint8_t *dataBuffer;
    uint8_t *statusBuffer;
    uint8_t *sensorTimeBuffer;
    uint8_t *temperatureBuffer;
    uint8_t txrxBuffer[SPI_BUF_SIZE];

    // states
    volatile uint8_t state;  //task state, type enum SensorState, do NOT change this directly
    enum InitState init_state;
    enum MagConfigState mag_state;
    enum CalibrationState calibration_state;
    enum AccTestState acc_test_state;
    enum GyroTestState gyro_test_state;

    // for self-test
    int16_t accTestX, accTestY, accTestZ;

    // pending configs
    bool pending_int[2];
    bool pending_step_cnt;
    bool pending_config[NUM_OF_SENSOR];
    bool pending_calibration_save;
    bool pending_time_sync;
    bool pending_delta[NUM_CONT_SENSOR];
    bool pending_dispatch;
    bool frame_sensortime_valid;

    // FIFO setting
    uint16_t chunkReadSize;
    uint8_t  watermark;

    // spi rw
    struct SlabAllocator *mDataSlab;
    uint16_t mWbufCnt;
    uint8_t mRegCnt;
#ifdef BMI160_USE_I2C
    uint8_t cReg;
    SpiCbkF sCallback;
#endif

    uint8_t mRetryLeft;
    bool spiInUse;
};

static uint32_t AccRates[] = {
    SENSOR_HZ(25.0f/8.0f),
    SENSOR_HZ(25.0f/4.0f),
    SENSOR_HZ(25.0f/2.0f),
    SENSOR_HZ(25.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    SENSOR_HZ(200.0f),
    SENSOR_HZ(400.0f),
    0,
};

static uint32_t GyrRates[] = {
    SENSOR_HZ(25.0f/8.0f),
    SENSOR_HZ(25.0f/4.0f),
    SENSOR_HZ(25.0f/2.0f),
    SENSOR_HZ(25.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    SENSOR_HZ(200.0f),
    SENSOR_HZ(400.0f),
    0,
};

#ifdef MAG_SLAVE_PRESENT
static uint32_t MagRates[] = {
    SENSOR_HZ(25.0f/8.0f),
    SENSOR_HZ(25.0f/4.0f),
    SENSOR_HZ(25.0f/2.0f),
    SENSOR_HZ(25.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    0,
};
#endif

static uint32_t StepCntRates[] = {
    SENSOR_HZ(1.0f/300.0f),
    SENSOR_HZ(1.0f/240.0f),
    SENSOR_HZ(1.0f/180.0f),
    SENSOR_HZ(1.0f/120.0f),
    SENSOR_HZ(1.0f/90.0f),
    SENSOR_HZ(1.0f/60.0f),
    SENSOR_HZ(1.0f/45.0f),
    SENSOR_HZ(1.0f/30.0f),
    SENSOR_HZ(1.0f/15.0f),
    SENSOR_HZ(1.0f/10.0f),
    SENSOR_HZ(1.0f/5.0f),
    SENSOR_RATE_ONCHANGE,
    0
};

static const uint64_t stepCntRateTimerVals[] = // should match StepCntRates and be the timer length for that rate in nanosecs
{
    300 * 1000000000ULL,
    240 * 1000000000ULL,
    180 * 1000000000ULL,
    120 * 1000000000ULL,
    90 * 1000000000ULL,
    60 * 1000000000ULL,
    45 * 1000000000ULL,
    30 * 1000000000ULL,
    15 * 1000000000ULL,
    10 * 1000000000ULL,
    5 * 1000000000ULL,
};

static struct BMI160Task mTask;

#ifdef MAG_SLAVE_PRESENT
static struct MagTask magTask;
#endif

#define MAG_WRITE(addr, data)                                   \
    do {                                                        \
        SPI_WRITE(BMI160_REG_MAG_IF_4, data);                   \
        SPI_WRITE(BMI160_REG_MAG_IF_3, addr);                   \
    } while (0)

#define MAG_READ(addr, size)                                    \
    do {                                                        \
        SPI_WRITE(BMI160_REG_MAG_IF_2, addr, 5000);             \
        SPI_READ(BMI160_REG_DATA_0, size, &mTask.dataBuffer);   \
    } while (0)

#define DEC_INFO(name, type, axis, inter, samples) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples

#define DEC_INFO_RATE(name, rates, type, axis, inter, samples) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates

#define DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_RAW, \
    .rawType = raw, \
    .rawScale = scale

#define DEC_INFO_RATE_BIAS(name, rates, type, axis, inter, samples, bias) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

#define DEC_INFO_RATE_RAW_BIAS(name, rates, type, axis, inter, samples, raw, scale, bias) \
    DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale), \
    .flags1 = SENSOR_INFO_FLAGS1_RAW | SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

typedef struct BMI160Task _Task;
#define TASK  _Task* const _task

// To get rid of static variables all task functions should have a task structure pointer input.
// This is an intermediate step.
#define TDECL()  TASK = &mTask; (void)_task

// Access task variables without explicitly specify the task structure pointer.
#define T(v)  (_task->v)

// Atomic get state
#define GET_STATE() (atomicReadByte(&(_task->state)))

// Atomic set state, this set the state to arbitrary value, use with caution
#define SET_STATE(s) do{\
        DEBUG_PRINT_IF(DBG_STATE, "set state %" PRI_STATE "\n", getStateName(s));\
        atomicWriteByte(&(_task->state), (s));\
    }while(0)

// Atomic switch state from IDLE to desired state.
static bool trySwitchState_(TASK, enum SensorState newState) {
#if DBG_STATE
    bool ret = atomicCmpXchgByte(&T(state), SENSOR_IDLE, newState);
    uint8_t prevState = ret ? SENSOR_IDLE : GET_STATE();
    DEBUG_PRINT("switch state %" PRI_STATE "->%" PRI_STATE ", %s\n",
            getStateName(prevState), getStateName(newState), ret ? "ok" : "failed");
    return ret;
#else
    return atomicCmpXchgByte(&T(state), SENSOR_IDLE, newState);
#endif
}
// Short-hand
#define trySwitchState(s) trySwitchState_(_task, (s))

// Chunked FIFO read functions
static void chunkedReadInit_(TASK, int index, int size);
#define chunkedReadInit(a,b) chunkedReadInit_(_task, (a), (b))
static void chunkedReadSpiCallback(void *cookie, int error);
static void initiateFifoRead_(TASK, bool isInterruptContext);
#define initiateFifoRead(a) initiateFifoRead_(_task, (a))
static uint8_t* shallowParseFrame(uint8_t * buf, int size);

#ifdef OVERTEMPCAL_ENABLED
// otc gyro cal save restore functions
static void handleOtcGyroConfig_(TASK, const struct AppToSensorHalDataPayload *data);
#define handleOtcGyroConfig(a) handleOtcGyroConfig_(_task, (a))
static bool sendOtcGyroUpdate_();
#define sendOtcGyroUpdate() sendOtcGyroUpdate_(_task)
static void unlockOtcGyroUpdateBuffer();
#endif  // OVERTEMPCAL_ENABLED

// Binary dump to osLog
static void dumpBinary(void* buf, unsigned int address, size_t size);

// Watermark calculation
static uint8_t calcWatermark2_(TASK);
#define calcWatermark2() calcWatermark2_(_task)

static const struct SensorInfo mSensorInfo[NUM_OF_SENSOR] =
{
#ifdef ACCEL_CAL_ENABLED
    { DEC_INFO_RATE_RAW_BIAS("Accelerometer", AccRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 3000, SENS_TYPE_ACCEL_RAW, 1.0/kScale_acc,
            SENS_TYPE_ACCEL_BIAS) },
#else
    { DEC_INFO_RATE_RAW("Accelerometer", AccRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 3000, SENS_TYPE_ACCEL_RAW, 1.0/kScale_acc) },
#endif
    { DEC_INFO_RATE_BIAS("Gyroscope", GyrRates, SENS_TYPE_GYRO, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 20, SENS_TYPE_GYRO_BIAS) },
#ifdef MAG_SLAVE_PRESENT
    { DEC_INFO_RATE_RAW_BIAS("Magnetometer", MagRates, SENS_TYPE_MAG, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 600, SENS_TYPE_MAG_RAW, 1.0/kScale_mag,
            SENS_TYPE_MAG_BIAS) },
#endif
    { DEC_INFO("Step Detector", SENS_TYPE_STEP_DETECT, NUM_AXIS_EMBEDDED,
            NANOHUB_INT_NONWAKEUP, 100) },
    { DEC_INFO("Double Tap", SENS_TYPE_DOUBLE_TAP, NUM_AXIS_EMBEDDED,
            NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO("Flat", SENS_TYPE_FLAT, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO("Any Motion", SENS_TYPE_ANY_MOTION, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO("No Motion", SENS_TYPE_NO_MOTION, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO_RATE("Step Counter", StepCntRates, SENS_TYPE_STEP_COUNT, NUM_AXIS_EMBEDDED,
            NANOHUB_INT_NONWAKEUP, 20) },
};

static void time_init(void) {
    time_sync_init(&mTask.gSensorTime2RTC);
}

static bool sensortime_to_rtc_time(uint64_t sensor_time, uint64_t *rtc_time_ns) {
// fixme: nsec?
    return time_sync_estimate_time1(
            &mTask.gSensorTime2RTC, sensor_time * 39ull, rtc_time_ns);
}

static void map_sensortime_to_rtc_time(uint64_t sensor_time, uint64_t rtc_time_ns) {
// fixme: nsec?
    time_sync_add(&mTask.gSensorTime2RTC, rtc_time_ns, sensor_time * 39ull);
}

static void invalidate_sensortime_to_rtc_time(void) {
    time_sync_reset(&mTask.gSensorTime2RTC);
}

static void minimize_sensortime_history(void) {
    // truncate datapoints to the latest two to maintain valid sensortime to rtc
    // mapping and minimize the inflence of the past mapping
    time_sync_truncate(&mTask.gSensorTime2RTC, 2);

    // drop the oldest datapoint when a new one arrives for two times to
    // completely shift out the influence of the past mapping
    time_sync_hold(&mTask.gSensorTime2RTC, 2);
}

static void dataEvtFree(void *ptr)
{
    TDECL();
    struct TripleAxisDataEvent *ev = (struct TripleAxisDataEvent *)ptr;
    slabAllocatorFree(T(mDataSlab), ev);
}

static void spiQueueWrite(uint8_t addr, uint8_t data, uint32_t delay)
{
    TDECL();
    if (T(spiInUse)) {
        ERROR_PRINT("SPI in use, cannot queue write\n");
        return;
    }
    T(packets[T(mRegCnt)]).size = 2;
    T(packets[T(mRegCnt)]).txBuf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).rxBuf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).delay = delay * 1000;
    T(txrxBuffer[T(mWbufCnt++)]) = BMI160_SPI_WRITE | addr;
    T(txrxBuffer[T(mWbufCnt++)]) = data;
    T(mRegCnt)++;
}

/*
 * need to be sure size of buf is larger than read size
 */
static void spiQueueRead(uint8_t addr, size_t size, uint8_t **buf, uint32_t delay)
{
    TDECL();
    if (T(spiInUse)) {
        ERROR_PRINT("SPI in use, cannot queue read %d %d\n", (int)addr, (int)size);
        return;
    }

    *buf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).size = size + 1; // first byte will not contain valid data
    T(packets[T(mRegCnt)]).txBuf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).rxBuf = *buf;
    T(packets[T(mRegCnt)]).delay = delay * 1000;
    T(txrxBuffer[T(mWbufCnt)++]) = BMI160_SPI_READ | addr;
    T(mWbufCnt) += size;
    T(mRegCnt)++;
}

#ifdef BMI160_USE_I2C
static void i2cBatchTxRx(void *evtData, int err);
#endif

static void spiBatchTxRx(struct SpiMode *mode,
        SpiCbkF callback, void *cookie, const char * src)
{
    TDECL();
    if (T(mWbufCnt) > SPI_BUF_SIZE) {
        ERROR_PRINT("NO enough SPI buffer space, dropping transaction.\n");
        return;
    }
    if (T(mRegCnt) > SPI_PACKET_SIZE) {
        ERROR_PRINT("spiBatchTxRx too many packets!\n");
        return;
    }

    T(spiInUse) = true;
    T(mWbufCnt) = 0;

#ifdef BMI160_USE_I2C
    T(cReg) = 0;
    T(sCallback) = callback;
    i2cBatchTxRx(cookie, 0);
#else
    // Reset variables before issuing SPI transaction.
    // SPI may finish before spiMasterRxTx finish
    uint8_t regCount = T(mRegCnt);
    T(mRegCnt) = 0;

    if (spiMasterRxTx(T(spiDev), T(cs), T(packets), regCount, mode, callback, cookie) < 0) {
        ERROR_PRINT("spiMasterRxTx failed!\n");
    }
#endif
}


static bool bmi160Isr1(struct ChainedIsr *isr)
{
    TASK = container_of(isr, struct BMI160Task, Isr1);

    if (!extiIsPendingGpio(T(Int1))) {
        return false;
    }
    DEBUG_PRINT_IF(DBG_INT, "i1\n");
    initiateFifoRead(true /*isInterruptContext*/);
    extiClearPendingGpio(T(Int1));
    return true;
}


static bool bmi160Isr2(struct ChainedIsr *isr)
{
    TASK = container_of(isr, struct BMI160Task, Isr2);

    if (!extiIsPendingGpio(T(Int2)))
        return false;

    DEBUG_PRINT_IF(DBG_INT, "i2\n");
    if (!osEnqueuePrivateEvt(EVT_SENSOR_INTERRUPT_2, _task, NULL, T(tid)))
        ERROR_PRINT("bmi160Isr2: osEnqueuePrivateEvt() failed\n");
    extiClearPendingGpio(T(Int2));
    return true;
}

static void sensorSpiCallback(void *cookie, int err)
{
    mTask.spiInUse = false;

    if (!osEnqueuePrivateEvt(EVT_SPI_DONE, cookie, NULL, mTask.tid))
        ERROR_PRINT("sensorSpiCallback: osEnqueuePrivateEvt() failed\n");
}

static void sensorTimerCallback(uint32_t timerId, void *data)
{
    if (!osEnqueuePrivateEvt(EVT_SPI_DONE, data, NULL, mTask.tid))
        ERROR_PRINT("sensorTimerCallback: osEnqueuePrivateEvt() failed\n")
}

static void timeSyncCallback(uint32_t timerId, void *data)
{
    if (!osEnqueuePrivateEvt(EVT_TIME_SYNC, data, NULL, mTask.tid))
        ERROR_PRINT("timeSyncCallback: osEnqueuePrivateEvt() failed\n");
}

static void stepCntSamplingCallback(uint32_t timerId, void *data)
{
    union EmbeddedDataPoint step_cnt;

    if (mTask.sensors[STEPCNT].powered && mTask.step_cnt_changed) {
        mTask.step_cnt_changed = false;
        step_cnt.idata = mTask.total_step_cnt;
        osEnqueueEvt(EVT_SENSOR_STEP_COUNTER, step_cnt.vptr, NULL);
    }
}

static bool accFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[ACC].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool gyrFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[GYR].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

#ifdef MAG_SLAVE_PRESENT
static bool magFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[MAG].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}
#endif

static bool stepFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[STEP].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool doubleTapFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[DTAP].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool noMotionFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[NOMO].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool anyMotionFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[ANYMO].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool flatFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[FLAT].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool stepCntFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.sensors[STEPCNT].handle,
            SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool enableInterrupt(struct Gpio *pin, IRQn_Type irq, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_RISING);
    extiChainIsr(irq, isr);
    return true;
}

static bool disableInterrupt(struct Gpio *pin, IRQn_Type irq, struct ChainedIsr *isr)
{
    extiUnchainIsr(irq, isr);
    extiDisableIntGpio(pin);
    return true;
}

static void magConfigMagic(void)
{
    // set the MAG power to NORMAL mode
    SPI_WRITE(BMI160_REG_CMD, 0x19, 10000);

    // Magic register sequence to shift register page table to access hidden
    // register
    SPI_WRITE(BMI160_REG_CMD, 0x37);
    SPI_WRITE(BMI160_REG_CMD, 0x9a);
    SPI_WRITE(BMI160_REG_CMD, 0xc0);
    SPI_WRITE(BMI160_REG_MAGIC, 0x90);
    SPI_READ(BMI160_REG_DATA_1, 1, &mTask.dataBuffer);
}

static void magConfigIf(void)
{
    // Set the on-chip I2C pull-up register settings and shift the register
    // table back down (magic)
    SPI_WRITE(BMI160_REG_DATA_1, mTask.dataBuffer[1] | 0x30);
    SPI_WRITE(BMI160_REG_MAGIC, 0x80);

    // Config the MAG I2C device address
#ifdef MAG_SLAVE_PRESENT
    SPI_WRITE(BMI160_REG_MAG_IF_0, (MAG_I2C_ADDR << 1));
#endif

    // set mag_manual_enable, mag_offset=0, mag_rd_burst='8 bytes'
    SPI_WRITE(BMI160_REG_MAG_IF_1, 0x83);

    // primary interface: autoconfig, secondary: magnetometer.
    SPI_WRITE(BMI160_REG_IF_CONF, 0x20);

    // fixme: move to mag-specific function
#ifdef USE_BMM150
    // set mag to SLEEP mode
    MAG_WRITE(BMM150_REG_CTRL_1, 0x01);
#elif USE_AK09915
    // Disable Noise Suppression Filter (NSF) settings
    MAG_WRITE(AKM_AK09915_REG_CNTL1, 0x00);
#endif
}

// fixme: break this up to master/slave-specific, so it'll be eventually slave-agnostic,
// and slave provides its own stateless config function
// fixme: not all async_elem_t is supported
static void magConfig(void)
{
    switch (mTask.mag_state) {
    case MAG_SET_START:
        magConfigMagic();
        mTask.mag_state = MAG_SET_IF;
        break;
    case MAG_SET_IF:
        magConfigIf();
#ifdef USE_AK09915
        mTask.mag_state = MAG_SET_FORCE;
#elif USE_BMM150
        mTask.mag_state = MAG_SET_REPXY;
#endif
        break;

#ifdef USE_BMM150
    case MAG_SET_REPXY:
        // MAG_SET_REPXY and MAG_SET_REPZ case set:
        // regular preset, f_max,ODR ~ 102 Hz
        MAG_WRITE(BMM150_REG_REPXY, 9);
        mTask.mag_state = MAG_SET_REPZ;
        break;
    case MAG_SET_REPZ:
        MAG_WRITE(BMM150_REG_REPZ, 15);
        mTask.mag_state = MAG_GET_DIG_X;
        break;
    case MAG_GET_DIG_X:
        // MAG_GET_DIG_X, MAG_GET_DIG_Y and MAG_GET_DIG_Z cases:
        // save parameters for temperature compensation.
        MAG_READ(BMM150_REG_DIG_X1, 8);
        mTask.mag_state = MAG_GET_DIG_Y;
        break;
    case MAG_GET_DIG_Y:
        bmm150SaveDigData(&magTask, &mTask.dataBuffer[1], 0);
        MAG_READ(BMM150_REG_DIG_X1 + 8, 8);
        mTask.mag_state = MAG_GET_DIG_Z;
        break;
    case MAG_GET_DIG_Z:
        bmm150SaveDigData(&magTask, &mTask.dataBuffer[1], 8);
        MAG_READ(BMM150_REG_DIG_X1 + 16, 8);
        mTask.mag_state = MAG_SET_SAVE_DIG;
        break;
    case MAG_SET_SAVE_DIG:
        bmm150SaveDigData(&magTask, &mTask.dataBuffer[1], 16);
        // fall through, no break;
        mTask.mag_state = MAG_SET_FORCE;
#endif

    case MAG_SET_FORCE:
        // set MAG mode to "forced". ready to pull data
#ifdef USE_AK09915
        MAG_WRITE(AKM_AK09915_REG_CNTL2, 0x01);
#elif USE_BMM150
        MAG_WRITE(BMM150_REG_CTRL_2, 0x02);
#endif
        mTask.mag_state = MAG_SET_ADDR;
        break;
    case MAG_SET_ADDR:
        // config MAG read data address to the first data register
#ifdef MAG_SLAVE_PRESENT
        SPI_WRITE(BMI160_REG_MAG_IF_2, MAG_REG_DATA);
#endif
        mTask.mag_state = MAG_SET_DATA;
        break;
    case MAG_SET_DATA:
        // clear mag_manual_en.
        SPI_WRITE(BMI160_REG_MAG_IF_1, 0x03, 1000);
        // set the MAG power to SUSPEND mode
        SPI_WRITE(BMI160_REG_CMD, 0x18, 10000);
        mTask.mag_state = MAG_SET_DONE;
        mTask.init_state = INIT_ON_CHANGE_SENSORS;
        break;
    default:
        break;
    }
    SPI_READ(BMI160_REG_STATUS, 1, &mTask.statusBuffer, 1000);
}

static bool flushData(struct BMI160Sensor *sensor, uint32_t eventId)
{
    bool success = false;

    if (sensor->data_evt) {
        success = osEnqueueEvtOrFree(eventId, sensor->data_evt, dataEvtFree);
        sensor->data_evt = NULL;
    }

    return success;
}

static void flushAllData(void)
{
    int i;
    for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; i++) {
        flushData(&mTask.sensors[i],
                EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSensorInfo[i].sensorType));
    }
}

static bool allocateDataEvt(struct BMI160Sensor *mSensor, uint64_t rtc_time)
{
    TDECL();
    mSensor->data_evt = slabAllocatorAlloc(T(mDataSlab));
    if (mSensor->data_evt == NULL) {
        // slab allocation failed
        ERROR_PRINT("slabAllocatorAlloc() failed\n");
        return false;
    }

    // delta time for the first sample is sample count
    memset(&mSensor->data_evt->samples[0].firstSample, 0x00, sizeof(struct SensorFirstSample));
    mSensor->data_evt->referenceTime = rtc_time;
    mSensor->prev_rtc_time = rtc_time;

    return true;
}

static inline bool anyFifoEnabled(void)
{
    bool anyFifoEnabled = mTask.fifo_enabled[ACC] || mTask.fifo_enabled[GYR];
#ifdef MAG_SLAVE_PRESENT
    anyFifoEnabled = anyFifoEnabled || mTask.fifo_enabled[MAG];
#endif
    return anyFifoEnabled;
}

static void configFifo(void)
{
    TDECL();
    int i;
    uint8_t val = 0x12;
    bool any_fifo_enabled_prev = anyFifoEnabled();
#ifdef ACCEL_CAL_ENABLED
    struct BMI160Sensor *mSensorAcc;
    bool accelCalNewBiasAvailable;
    struct TripleAxisDataPoint *sample;
    float accelCalBiasX, accelCalBiasY, accelCalBiasZ;
    bool fallThrough;
#endif

    // if ACC is configed, enable ACC bit in fifo_config reg.
    if (mTask.sensors[ACC].configed && mTask.sensors[ACC].latency != SENSOR_LATENCY_NODATA) {
        val |= 0x40;
        mTask.fifo_enabled[ACC] = true;
    } else {
        mTask.fifo_enabled[ACC] = false;
#ifdef ACCEL_CAL_ENABLED
        // https://source.android.com/devices/sensors/sensor-types.html
        // "The bias and scale calibration must only be updated while the sensor is deactivated,
        // so as to avoid causing jumps in values during streaming."
        accelCalNewBiasAvailable = accelCalUpdateBias(&mTask.acc, &accelCalBiasX, &accelCalBiasY, &accelCalBiasZ);

        mSensorAcc = &mTask.sensors[ACC];
        // notify HAL about new accel bias calibration
        if (accelCalNewBiasAvailable) {
            fallThrough = true;
            if (mSensorAcc->data_evt->samples[0].firstSample.numSamples > 0) {
                // flush existing samples so the bias appears after them
                flushData(mSensorAcc,
                        EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSensorInfo[ACC].sensorType));

                // try to allocate another data event and break if unsuccessful
                if (!allocateDataEvt(mSensorAcc, sensorGetTime())) {
                    fallThrough = false;
                }
            }

            if (fallThrough) {
                mSensorAcc->data_evt->samples[0].firstSample.biasCurrent = true;
                mSensorAcc->data_evt->samples[0].firstSample.biasPresent = 1;
                mSensorAcc->data_evt->samples[0].firstSample.biasSample =
                        mSensorAcc->data_evt->samples[0].firstSample.numSamples;
                sample = &mSensorAcc->data_evt->samples[mSensorAcc->data_evt->samples[0].firstSample.numSamples++];
                sample->x = accelCalBiasX;
                sample->y = accelCalBiasY;
                sample->z = accelCalBiasZ;
                flushData(mSensorAcc, sensorGetMyEventType(mSensorInfo[ACC].biasType));

                allocateDataEvt(mSensorAcc, sensorGetTime());
            }
        }
#endif
    }

    // if GYR is configed, enable GYR bit in fifo_config reg.
    if (mTask.sensors[GYR].configed && mTask.sensors[GYR].latency != SENSOR_LATENCY_NODATA) {
        val |= 0x80;
        mTask.fifo_enabled[GYR] = true;
    } else {
        mTask.fifo_enabled[GYR] = false;
    }

#ifdef MAG_SLAVE_PRESENT
    // if MAG is configed, enable MAG bit in fifo_config reg.
    if (mTask.sensors[MAG].configed && mTask.sensors[MAG].latency != SENSOR_LATENCY_NODATA) {
        val |= 0x20;
        mTask.fifo_enabled[MAG] = true;
    } else {
        mTask.fifo_enabled[MAG] = false;
    }
#endif

    // if this is the first data sensor fifo to enable, start to
    // sync the sensor time and rtc time
    if (!any_fifo_enabled_prev && anyFifoEnabled()) {
        invalidate_sensortime_to_rtc_time();

        // start a new poll generation and attach the generation number to event
        if (!osEnqueuePrivateEvt(EVT_TIME_SYNC, (void *)mTask.poll_generation, NULL, mTask.tid))
            ERROR_PRINT("configFifo: osEnqueuePrivateEvt() failed\n");
    }

    // cancel current poll generation
    if (any_fifo_enabled_prev && !anyFifoEnabled()) {
        ++mTask.poll_generation;
    }

    // if this is not the first fifo enabled or last fifo disabled, flush all fifo data;
    if (any_fifo_enabled_prev && anyFifoEnabled()) {
        mTask.pending_dispatch = true;
        mTask.xferCnt = FIFO_READ_SIZE;
        SPI_READ(BMI160_REG_FIFO_DATA, mTask.xferCnt, &mTask.dataBuffer);
    }

    // calculate the new watermark level
    if (anyFifoEnabled()) {
        mTask.watermark = calcWatermark2_(_task);
        DEBUG_PRINT("wm=%d", mTask.watermark);
        SPI_WRITE(BMI160_REG_FIFO_CONFIG_0, mTask.watermark);
    }

    // config the fifo register
    SPI_WRITE(BMI160_REG_FIFO_CONFIG_1, val);

    // if no more fifo enabled, we need to cleanup the fifo and invalidate time
    if (!anyFifoEnabled()) {
        SPI_WRITE(BMI160_REG_CMD, 0xb0);
        mTask.frame_sensortime_valid = false;
        for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; i++) {
            mTask.pending_delta[i] = false;
            mTask.prev_frame_time[i] = ULONG_LONG_MAX;
        }
    }
}

static bool accPower(bool on, void *cookie)
{
    TDECL();

    VERBOSE_PRINT("accPower: on=%d, state=%" PRI_STATE "\n", on, getStateName(GET_STATE()));
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            // set ACC power mode to NORMAL
            SPI_WRITE(BMI160_REG_CMD, 0x11, 50000);
        } else {
            // set ACC power mode to SUSPEND
            mTask.sensors[ACC].configed = false;
            configFifo();
            SPI_WRITE(BMI160_REG_CMD, 0x10, 5000);
        }
        mTask.sensors[ACC].powered = on;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
    } else {
        mTask.pending_config[ACC] = true;
        mTask.sensors[ACC].pConfig.enable = on;
    }
    return true;
}

static bool gyrPower(bool on, void *cookie)
{
    TDECL();
    VERBOSE_PRINT("gyrPower: on=%d, state=%" PRI_STATE "\n", on, getStateName(GET_STATE()));

    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            // set GYR power mode to NORMAL
            SPI_WRITE(BMI160_REG_CMD, 0x15, 50000);
        } else {
            // set GYR power mode to SUSPEND
            mTask.sensors[GYR].configed = false;
            configFifo();
            SPI_WRITE(BMI160_REG_CMD, 0x14, 5000);
        }

        if (anyFifoEnabled() && on != mTask.sensors[GYR].powered) {
#if TIMESTAMP_DBG
            DEBUG_PRINT("minimize_sensortime_history()\n");
#endif
            minimize_sensortime_history();
        }

        mTask.sensors[GYR].powered = on;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
    } else {
        mTask.pending_config[GYR] = true;
        mTask.sensors[GYR].pConfig.enable = on;
    }
    return true;
}

#ifdef MAG_SLAVE_PRESENT
static bool magPower(bool on, void *cookie)
{
    TDECL();
    VERBOSE_PRINT("magPower: on=%d, state=%" PRI_STATE "\n", on, getStateName(GET_STATE()));
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            // set MAG power mode to NORMAL
            SPI_WRITE(BMI160_REG_CMD, 0x19, 10000);
        } else {
            // set MAG power mode to SUSPEND
            mTask.sensors[MAG].configed = false;
            configFifo();
            SPI_WRITE(BMI160_REG_CMD, 0x18, 5000);
        }
        mTask.sensors[MAG].powered = on;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[MAG], __FUNCTION__);
    } else {
        mTask.pending_config[MAG] = true;
        mTask.sensors[MAG].pConfig.enable = on;
    }
    return true;
}
#endif

static bool stepPower(bool on, void *cookie)
{
    TDECL();
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        // if step counter is powered, no need to change actual config of step
        // detector.
        // But we choose to perform one SPI_WRITE anyway to go down the code path
        // to state SENSOR_POWERING_UP/DOWN to update sensor manager.
        if (on) {
            mTask.interrupt_enable_2 |= 0x08;
        } else {
            if (!mTask.sensors[STEPCNT].powered)
                mTask.interrupt_enable_2 &= ~0x08;
            mTask.sensors[STEP].configed = false;
        }
        mTask.sensors[STEP].powered = on;
        SPI_WRITE(BMI160_REG_INT_EN_2, mTask.interrupt_enable_2, 450);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[STEP], __FUNCTION__);
    } else {
        mTask.pending_config[STEP] = true;
        mTask.sensors[STEP].pConfig.enable = on;
    }
    return true;
}

static bool flatPower(bool on, void *cookie)
{
    TDECL();
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            mTask.interrupt_enable_0 |= 0x80;
        } else {
            mTask.interrupt_enable_0 &= ~0x80;
            mTask.sensors[FLAT].configed = false;
        }
        mTask.sensors[FLAT].powered = on;
        SPI_WRITE(BMI160_REG_INT_EN_0, mTask.interrupt_enable_0, 450);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[FLAT], __FUNCTION__);
    } else {
        mTask.pending_config[FLAT] = true;
        mTask.sensors[FLAT].pConfig.enable = on;
    }
    return true;
}

static bool doubleTapPower(bool on, void *cookie)
{
    TDECL();
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            mTask.interrupt_enable_0 |= 0x10;
        } else {
            mTask.interrupt_enable_0 &= ~0x10;
            mTask.sensors[DTAP].configed = false;
        }
        mTask.sensors[DTAP].powered = on;
        SPI_WRITE(BMI160_REG_INT_EN_0, mTask.interrupt_enable_0, 450);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[DTAP], __FUNCTION__);
    } else {
        mTask.pending_config[DTAP] = true;
        mTask.sensors[DTAP].pConfig.enable = on;
    }
    return true;
}

static bool anyMotionPower(bool on, void *cookie)
{
    TDECL();
    DEBUG_PRINT("anyMotionPower: on=%d, oneshot_cnt %d, state=%" PRI_STATE "\n",
            on, mTask.active_oneshot_sensor_cnt, getStateName(GET_STATE()));

    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            mTask.interrupt_enable_0 |= 0x07;
        } else {
            mTask.interrupt_enable_0 &= ~0x07;
            mTask.sensors[ANYMO].configed = false;
        }
        mTask.sensors[ANYMO].powered = on;
        SPI_WRITE(BMI160_REG_INT_EN_0, mTask.interrupt_enable_0, 450);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ANYMO], __FUNCTION__);
    } else {
        mTask.pending_config[ANYMO] = true;
        mTask.sensors[ANYMO].pConfig.enable = on;
    }
    return true;
}

static bool noMotionPower(bool on, void *cookie)
{
    TDECL();
    DEBUG_PRINT("noMotionPower: on=%d, oneshot_cnt %d, state=%" PRI_STATE "\n",
            on, mTask.active_oneshot_sensor_cnt, getStateName(GET_STATE()));
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            mTask.interrupt_enable_2 |= 0x07;
        } else {
            mTask.interrupt_enable_2 &= ~0x07;
            mTask.sensors[NOMO].configed = false;
        }
        mTask.sensors[NOMO].powered = on;
        SPI_WRITE(BMI160_REG_INT_EN_2, mTask.interrupt_enable_2, 450);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[NOMO], __FUNCTION__);
    } else {
        mTask.pending_config[NOMO] = true;
        mTask.sensors[NOMO].pConfig.enable = on;
    }
    return true;
}

static bool stepCntPower(bool on, void *cookie)
{
    TDECL();
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        if (on) {
            if (!mTask.sensors[STEP].powered) {
                mTask.interrupt_enable_2 |= 0x08;
                SPI_WRITE(BMI160_REG_INT_EN_2, mTask.interrupt_enable_2, 450);
            }
            // set step_cnt_en bit
            SPI_WRITE(BMI160_REG_STEP_CONF_1, 0x08 | 0x03, 1000);
        } else {
            if (mTask.stepCntSamplingTimerHandle) {
                timTimerCancel(mTask.stepCntSamplingTimerHandle);
                mTask.stepCntSamplingTimerHandle = 0;
            }
            if (!mTask.sensors[STEP].powered) {
                mTask.interrupt_enable_2 &= ~0x08;
                SPI_WRITE(BMI160_REG_INT_EN_2, mTask.interrupt_enable_2);
            }
            // unset step_cnt_en bit
            SPI_WRITE(BMI160_REG_STEP_CONF_1, 0x03);
            mTask.last_step_cnt = 0;
            mTask.sensors[STEPCNT].configed = false;
        }
        mTask.sensors[STEPCNT].powered = on;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[STEPCNT], __FUNCTION__);
    } else {
        mTask.pending_config[STEPCNT] = true;
        mTask.sensors[STEPCNT].pConfig.enable = on;
    }
    return true;
}

static void updateTimeDelta(uint8_t idx, uint8_t odr)
{
    if (mTask.fifo_enabled[idx]) {
        // wait till control frame to update, if not disabled
        mTask.next_delta[idx] = 1ull << (16 - odr);
        mTask.pending_delta[idx] = true;
    } else {
        mTask.time_delta[idx] = 1ull << (16 - odr);
    }
}

// compute the register value from sensor rate.
static uint8_t computeOdr(uint32_t rate)
{
    uint8_t odr = 0x00;
    switch (rate) {
    // fall through intended to get the correct register value
    case SENSOR_HZ(3200): odr ++;
    case SENSOR_HZ(1600): odr ++;
    case SENSOR_HZ(800): odr ++;
    case SENSOR_HZ(400): odr ++;
    case SENSOR_HZ(200): odr ++;
    case SENSOR_HZ(100): odr ++;
    case SENSOR_HZ(50): odr ++;
    case SENSOR_HZ(25): odr ++;
    case SENSOR_HZ(25.0f/2.0f): odr ++;
    case SENSOR_HZ(25.0f/4.0f): odr ++;
    case SENSOR_HZ(25.0f/8.0f): odr ++;
    case SENSOR_HZ(25.0f/16.0f): odr ++;
    case SENSOR_HZ(25.0f/32.0f): odr ++;
    default:
        return odr;
    }
}

static void configMotion(uint8_t odr) {
#if BMI160_ACC_RANGE_G == 16
    // motion threshold is element * 31.25mg (for 16g range)
    static const uint8_t motion_thresholds[ACC_MAX_RATE+1] =
        {3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 1, 1, 1};
#elif BMI160_ACC_RANGE_G == 8
    // motion threshold is element * 15.63mg (for 8g range)
    static const uint8_t motion_thresholds[ACC_MAX_RATE+1] =
        {5, 5, 5, 5, 5, 5, 5, 5, 4, 3, 2, 2, 2};
#endif

    // set any_motion duration to 1 point
    // set no_motion duration to (3+1)*1.28sec=5.12sec
    SPI_WRITE(BMI160_REG_INT_MOTION_0, 0x03 << 2, 450);

    // set any_motion threshold
    SPI_WRITE(BMI160_REG_INT_MOTION_1, motion_thresholds[odr], 450);

    // set no_motion threshold
    SPI_WRITE(BMI160_REG_INT_MOTION_2, motion_thresholds[odr], 450);
}

static bool accSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    int odr, osr = 0;
    int osr_mode = 2; // normal

    // change this to DEBUG_PRINT as there will be frequent (un)subscribings
    // to accel with different rate/latency requirements.
    DEBUG_PRINT("accSetRate: rate=%ld, latency=%lld, state=%" PRI_STATE "\n",
                rate, latency, getStateName(GET_STATE()));

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        odr = computeOdr(rate);
        if (!odr) {
            ERROR_PRINT("invalid acc rate\n");
            return false;
        }

        updateTimeDelta(ACC, odr);

        // minimum supported rate for ACCEL is 12.5Hz.
        // Anything lower than that shall be acheived by downsampling.
        if (odr < ACC_MIN_RATE) {
            osr = ACC_MIN_RATE - odr;
            odr = ACC_MIN_RATE;
        }

        // for high odrs, oversample to reduce hw latency and downsample
        // to get desired odr
        if (odr > ODR_100HZ) {
            // 200Hz osr4, >= 400Hz osr2
            if (odr == ODR_200HZ) {
                osr_mode = 0; // OSR4
            } else {
                osr_mode = 1; // OSR2
            }
            osr = (ACC_MAX_OSR + odr) > ACC_MAX_RATE ? (ACC_MAX_RATE - odr) : ACC_MAX_OSR;
            odr += osr;
        }

        mTask.sensors[ACC].rate = rate;
        mTask.sensors[ACC].latency = latency;
        mTask.sensors[ACC].configed = true;
        mTask.acc_downsample = osr;

        // configure ANY_MOTION and NO_MOTION based on odr
        configMotion(odr);

        // set ACC bandwidth parameter to 2 (bits[4:6])
        // set the rate (bits[0:3])
        SPI_WRITE(BMI160_REG_ACC_CONF, (osr_mode << 4) | odr);

        // configure down sampling ratio, 0x88 is to specify we are using
        // filtered samples
        SPI_WRITE(BMI160_REG_FIFO_DOWNS, (mTask.acc_downsample << 4) | mTask.gyr_downsample | 0x88);

        // flush the data and configure the fifo
        configFifo();

        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
    } else {
        mTask.pending_config[ACC] = true;
        mTask.sensors[ACC].pConfig.enable = 1;
        mTask.sensors[ACC].pConfig.rate = rate;
        mTask.sensors[ACC].pConfig.latency = latency;
    }
    return true;
}

static bool gyrSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    int odr, osr = 0;
    int osr_mode = 2; // normal
    VERBOSE_PRINT("gyrSetRate: rate=%ld, latency=%lld, state=%" PRI_STATE "\n",
               rate, latency, getStateName(GET_STATE()));

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        odr = computeOdr(rate);
        if (!odr) {
            ERROR_PRINT("invalid gyr rate\n");
            return false;
        }

        updateTimeDelta(GYR, odr);

        // minimum supported rate for GYRO is 25.0Hz.
        // Anything lower than that shall be acheived by downsampling.
        if (odr < GYR_MIN_RATE) {
            osr = GYR_MIN_RATE - odr;
            odr = GYR_MIN_RATE;
        }

        // for high odrs, oversample to reduce hw latency and downsample
        // to get desired odr
        if (odr > ODR_100HZ) {
            // 200Hz osr4, >= 400Hz osr2
            if (odr == ODR_200HZ) {
                osr_mode = 0; // OSR4
            } else {
                osr_mode = 1; // OSR2
            }
            osr = (GYR_MAX_OSR + odr) > GYR_MAX_RATE ? (GYR_MAX_RATE - odr) : GYR_MAX_OSR;
            odr += osr;
        }

        mTask.sensors[GYR].rate = rate;
        mTask.sensors[GYR].latency = latency;
        mTask.sensors[GYR].configed = true;
        mTask.gyr_downsample = osr;

        // set GYR bandwidth parameter to 2 (bits[4:6])
        // set the rate (bits[0:3])
        SPI_WRITE(BMI160_REG_GYR_CONF, (osr_mode << 4) | odr);

        // configure down sampling ratio, 0x88 is to specify we are using
        // filtered samples
        SPI_WRITE(BMI160_REG_FIFO_DOWNS, (mTask.acc_downsample << 4) | mTask.gyr_downsample | 0x88);

        // flush the data and configure the fifo
        configFifo();

        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
    } else {
        mTask.pending_config[GYR] = true;
        mTask.sensors[GYR].pConfig.enable = 1;
        mTask.sensors[GYR].pConfig.rate = rate;
        mTask.sensors[GYR].pConfig.latency = latency;
    }
    return true;
}

#ifdef MAG_SLAVE_PRESENT
static bool magSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    int odr;

    if (rate == SENSOR_RATE_ONCHANGE)
        rate = SENSOR_HZ(100);

    VERBOSE_PRINT("magSetRate: rate=%ld, latency=%lld, state=%" PRI_STATE "\n",
               rate, latency, getStateName(GET_STATE()));

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        mTask.sensors[MAG].rate = rate;
        mTask.sensors[MAG].latency = latency;
        mTask.sensors[MAG].configed = true;

        odr = computeOdr(rate);
        if (!odr) {
            ERROR_PRINT("invalid mag rate\n");
            return false;
        }

        updateTimeDelta(MAG, odr);

        odr = odr > MAG_MAX_RATE ? MAG_MAX_RATE : odr;

        // set the rate for MAG
        SPI_WRITE(BMI160_REG_MAG_CONF, odr);

        // flush the data and configure the fifo
        configFifo();

        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[MAG], __FUNCTION__);
    } else {
        mTask.pending_config[MAG] = true;
        mTask.sensors[MAG].pConfig.enable = 1;
        mTask.sensors[MAG].pConfig.rate = rate;
        mTask.sensors[MAG].pConfig.latency = latency;
    }
    return true;
}
#endif

static bool stepSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    mTask.sensors[STEP].rate = rate;
    mTask.sensors[STEP].latency = latency;
    mTask.sensors[STEP].configed = true;

    sensorSignalInternalEvt(mTask.sensors[STEP].handle,
            SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
    return true;
}

static bool flatSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    mTask.sensors[FLAT].rate = rate;
    mTask.sensors[FLAT].latency = latency;
    mTask.sensors[FLAT].configed = true;

    sensorSignalInternalEvt(mTask.sensors[FLAT].handle,
            SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
    return true;
}

static bool doubleTapSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    mTask.sensors[DTAP].rate = rate;
    mTask.sensors[DTAP].latency = latency;
    mTask.sensors[DTAP].configed = true;

    sensorSignalInternalEvt(mTask.sensors[DTAP].handle,
            SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
    return true;
}

static bool anyMotionSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    mTask.sensors[ANYMO].rate = rate;
    mTask.sensors[ANYMO].latency = latency;
    mTask.sensors[ANYMO].configed = true;

    sensorSignalInternalEvt(mTask.sensors[ANYMO].handle,
            SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

static bool noMotionSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    mTask.sensors[NOMO].rate = rate;
    mTask.sensors[NOMO].latency = latency;
    mTask.sensors[NOMO].configed = true;

    sensorSignalInternalEvt(mTask.sensors[NOMO].handle,
            SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
    return true;
}

static bool stepCntSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    mTask.sensors[STEPCNT].rate = rate;
    mTask.sensors[STEPCNT].latency = latency;
    mTask.sensors[STEPCNT].configed = true;

    if (rate == SENSOR_RATE_ONCHANGE && mTask.stepCntSamplingTimerHandle) {
        timTimerCancel(mTask.stepCntSamplingTimerHandle);
        mTask.stepCntSamplingTimerHandle = 0;
    } else if (rate != SENSOR_RATE_ONCHANGE) {
        if (mTask.stepCntSamplingTimerHandle) {
            timTimerCancel(mTask.stepCntSamplingTimerHandle);
        }
        mTask.stepCntSamplingTimerHandle = timTimerSet(sensorTimerLookupCommon(StepCntRates, stepCntRateTimerVals, rate),
                                                       0, 50, stepCntSamplingCallback, NULL, false);
        if (!mTask.stepCntSamplingTimerHandle)
            ERROR_PRINT("Couldn't get a timer for step counter\n");

    }

    sensorSignalInternalEvt(mTask.sensors[STEPCNT].handle,
            SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);
    return true;
}

static void sendFlushEvt(void)
{
    while (mTask.sensors[ACC].flush > 0) {
        osEnqueueEvt(EVT_SENSOR_ACC_DATA_RDY, SENSOR_DATA_EVENT_FLUSH, NULL);
        mTask.sensors[ACC].flush--;
    }
    while (mTask.sensors[GYR].flush > 0) {
        osEnqueueEvt(EVT_SENSOR_GYR_DATA_RDY, SENSOR_DATA_EVENT_FLUSH, NULL);
        mTask.sensors[GYR].flush--;
    }
#ifdef MAG_SLAVE_PRESENT
    while (mTask.sensors[MAG].flush > 0) {
        osEnqueueEvt(EVT_SENSOR_MAG_DATA_RDY, SENSOR_DATA_EVENT_FLUSH, NULL);
        mTask.sensors[MAG].flush--;
    }
#endif
}

static bool accFlush(void *cookie)
{
    TDECL();
    mTask.sensors[ACC].flush++;
    initiateFifoRead(false /*isInterruptContext*/);
    return true;
}

static bool gyrFlush(void *cookie)
{
    TDECL();
    mTask.sensors[GYR].flush++;
    initiateFifoRead(false /*isInterruptContext*/);
    return true;
}

#ifdef MAG_SLAVE_PRESENT
static bool magFlush(void *cookie)
{
    TDECL();
    mTask.sensors[MAG].flush++;
    initiateFifoRead(false /*isInterruptContext*/);
    return true;
}
#endif

static bool stepFlush(void *cookie)
{
    return osEnqueueEvt(EVT_SENSOR_STEP, SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool flatFlush(void *cookie)
{
    return osEnqueueEvt(EVT_SENSOR_FLAT, SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool doubleTapFlush(void *cookie)
{
    return osEnqueueEvt(EVT_SENSOR_DOUBLE_TAP, SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool anyMotionFlush(void *cookie)
{
    return osEnqueueEvt(EVT_SENSOR_ANY_MOTION, SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool noMotionFlush(void *cookie)
{
    return osEnqueueEvt(EVT_SENSOR_NO_MOTION, SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool stepCntFlushGetData()
{
    TDECL();
    if (trySwitchState(SENSOR_STEP_CNT)) {
        SPI_READ(BMI160_REG_STEP_CNT_0, 2, &mTask.dataBuffer);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[STEPCNT], __FUNCTION__);
        return true;
    }
    return false;
}

static bool stepCntFlush(void *cookie)
{
    mTask.sensors[STEPCNT].flush++;
    stepCntFlushGetData();
    return true;
}

static void sendStepCnt()
{
    union EmbeddedDataPoint step_cnt;
    uint32_t cur_step_cnt;
    cur_step_cnt = (int)(mTask.dataBuffer[1] | (mTask.dataBuffer[2] << 8));

    if (cur_step_cnt != mTask.last_step_cnt) {
        // Check for possible overflow
        if (cur_step_cnt < mTask.last_step_cnt) {
            mTask.total_step_cnt += cur_step_cnt + (0xFFFF - mTask.last_step_cnt);
        } else {
            mTask.total_step_cnt += (cur_step_cnt - mTask.last_step_cnt);
        }
        mTask.last_step_cnt = cur_step_cnt;

        // Send the event if the current rate is ONCHANGE or we need to flush;
        // otherwise, wait until step count sampling timer expires
        if (mTask.sensors[STEPCNT].rate == SENSOR_RATE_ONCHANGE || mTask.sensors[STEPCNT].flush) {
            step_cnt.idata = mTask.total_step_cnt;
            osEnqueueEvt(EVT_SENSOR_STEP_COUNTER, step_cnt.vptr, NULL);
        } else {
            mTask.step_cnt_changed = true;
        }
    }

    while (mTask.sensors[STEPCNT].flush) {
        osEnqueueEvt(EVT_SENSOR_STEP_COUNTER, SENSOR_DATA_EVENT_FLUSH, NULL);
        mTask.sensors[STEPCNT].flush--;
    }
}

static bool stepCntSendLastData(void *cookie, uint32_t tid)
{
    // If this comes in and we don't have data yet, there's no harm in reporting step_cnt = 0
    if (!osEnqueuePrivateEvt(EVT_SENSOR_STEP_COUNTER, (void *) mTask.total_step_cnt, NULL, tid)) {
        ERROR_PRINT("stepCntSendLastData: osEnqueuePrivateEvt() failed\n");
        return false;
    }

    return true;
}

static uint64_t parseSensortime(uint32_t sensor_time24)
{
    uint32_t prev_time24;
    uint32_t kHalf = 1ul << 23;
    uint64_t full;

    prev_time24 = (uint32_t)mTask.last_sensortime & 0xffffff;

    if (mTask.last_sensortime == 0) {
        mTask.last_sensortime = (uint64_t)sensor_time24;
        return (uint64_t)(sensor_time24);
    }

    if (sensor_time24 == prev_time24) {
        return (uint64_t)(mTask.last_sensortime);
    }

    full = (mTask.last_sensortime & ~0xffffffull) | sensor_time24;

    if (((prev_time24 < sensor_time24) && (sensor_time24 - prev_time24) < kHalf)
            || ((prev_time24 > sensor_time24) && (prev_time24 - sensor_time24) > kHalf)) {
        if (full < mTask.last_sensortime) {
            full += 0x1000000ull;
        }
        mTask.last_sensortime = full;
        return mTask.last_sensortime;
    }

    if (full < mTask.last_sensortime) {
        return full;
    }

    return (full -  0x1000000ull);
}

static void parseRawData(struct BMI160Sensor *mSensor, uint8_t *buf, float kScale, uint64_t sensorTime)
{
    TDECL();
    struct TripleAxisDataPoint *sample;
    uint64_t rtc_time, cur_time;
    uint32_t delta_time;
    float x, y, z;
    int16_t raw_x, raw_y, raw_z;
#ifdef MAG_SLAVE_PRESENT
    bool newMagBias = false;
#endif

    if (!sensortime_to_rtc_time(sensorTime, &rtc_time)) {
        return;
    }

    cur_time = sensorGetTime();
    if (rtc_time > cur_time + kMinRTCTimeIncrementNs) { // + tolerance to prevent frequent tripping
        INFO_PRINT("Future ts %s: rtc_time = %llu, cur_time = %llu",
                mSensorInfo[mSensor->idx].sensorName, rtc_time, cur_time);
        // clamp to current time
        rtc_time = cur_time + kMinRTCTimeIncrementNs;
    }

    if (rtc_time < mSensor->prev_rtc_time + kMinRTCTimeIncrementNs) {
#if TIMESTAMP_DBG
        DEBUG_PRINT("%s prev rtc 0x%08x %08x, curr 0x%08x %08x, delta %d usec\n",
                mSensorInfo[mSensor->idx].sensorName,
                (unsigned int)((mSensor->prev_rtc_time >> 32) & 0xffffffff),
                (unsigned int)(mSensor->prev_rtc_time & 0xffffffff),
                (unsigned int)((rtc_time >> 32) & 0xffffffff),
                (unsigned int)(rtc_time & 0xffffffff),
                (int)(rtc_time - mSensor->prev_rtc_time) / 1000);
#endif
        rtc_time = mSensor->prev_rtc_time + kMinRTCTimeIncrementNs;
    }

#ifdef MAG_SLAVE_PRESENT
    if (mSensor->idx == MAG) {
        parseMagData(&magTask, &buf[0], &x, &y, &z);
        BMM150_TO_ANDROID_COORDINATE(x, y, z);

        float xi, yi, zi;
        magCalRemoveSoftiron(&mTask.moc, x, y, z, &xi, &yi, &zi);

        newMagBias |= magCalUpdate(&mTask.moc, sensorTime * kSensorTimerIntervalUs, xi, yi, zi);

        magCalRemoveBias(&mTask.moc, xi, yi, zi, &x, &y, &z);

#ifdef GYRO_CAL_ENABLED
        // Gyro Cal -- Add magnetometer sample.
        gyroCalUpdateMag(&mTask.gyro_cal,
                         rtc_time,  // nsec
                         x, y, z);
#endif  // GYRO_CAL_ENABLED
    } else
#endif  // MAG_SLAVE_PRESENT
    {
        raw_x = (buf[0] | buf[1] << 8);
        raw_y = (buf[2] | buf[3] << 8);
        raw_z = (buf[4] | buf[5] << 8);

        x = (float)raw_x * kScale;
        y = (float)raw_y * kScale;
        z = (float)raw_z * kScale;

        BMI160_TO_ANDROID_COORDINATE(x, y, z);

        if (mSensor->idx == ACC) {

#ifdef ACCEL_CAL_ENABLED
          accelCalRun(&mTask.acc, rtc_time,
                      x, y, z, mTask.tempCelsius);

          accelCalBiasRemove(&mTask.acc, &x, &y, &z);
#endif

#ifdef GYRO_CAL_ENABLED
          // Gyro Cal -- Add accelerometer sample.
          gyroCalUpdateAccel(&mTask.gyro_cal,
                             rtc_time,  // nsec
                             x, y, z);
#endif  // GYRO_CAL_ENABLED
        } else if (mSensor->idx == GYR) {
#ifdef GYRO_CAL_ENABLED
          // Gyro Cal -- Add gyroscope and temperature sample.
          gyroCalUpdateGyro(&mTask.gyro_cal,
                            rtc_time,  // nsec
                            x, y, z, mTask.tempCelsius);

#ifdef OVERTEMPCAL_ENABLED
          // Over-Temp Gyro Cal -- Update measured temperature.
          overTempCalSetTemperature(&mTask.over_temp_gyro_cal, rtc_time,
                                    mTask.tempCelsius);

          // Over-Temp Gyro Cal -- Apply over-temp calibration correction.
          overTempCalRemoveOffset(&mTask.over_temp_gyro_cal, rtc_time,
                                  x, y, z,    /* input values */
                                  &x, &y, &z  /* calibrated output */);
#else  // OVERTEMPCAL_ENABLED
          // Gyro Cal -- Apply calibration correction.
          gyroCalRemoveBias(&mTask.gyro_cal,
                            x, y, z,    /* input values */
                            &x, &y, &z  /* calibrated output */);
#endif  // OVERTEMPCAL_ENABLED

#if defined(GYRO_CAL_DBG_ENABLED) || defined(OVERTEMPCAL_DBG_ENABLED)
          // This flag keeps GyroCal and OverTempCal from printing back-to-back.
          // If they do, then sometimes important print log data gets dropped.
          static size_t print_flag = 0;

          if (print_flag > 0) {
#ifdef GYRO_CAL_DBG_ENABLED
            // Gyro Cal -- Read out Debug data.
            gyroCalDebugPrint(&mTask.gyro_cal, rtc_time);
#endif  // GYRO_CAL_DBG_ENABLED
            print_flag = 0;
          } else {
#ifdef OVERTEMPCAL_ENABLED
#ifdef OVERTEMPCAL_DBG_ENABLED
            // Over-Temp Gyro Cal -- Read out Debug data.
            overTempCalDebugPrint(&mTask.over_temp_gyro_cal, rtc_time);
#endif  // OVERTEMPCAL_DBG_ENABLED
#endif  // OVERTEMPCAL_ENABLED
            print_flag = 1;
          }
#endif  // GYRO_CAL_DBG_ENABLED || OVERTEMPCAL_DBG_ENABLED
#endif  // GYRO_CAL_ENABLED
        }
    }

    if (mSensor->data_evt == NULL) {
        if (!allocateDataEvt(mSensor, rtc_time)) {
            return;
        }
    }

    if (mSensor->data_evt->samples[0].firstSample.numSamples >= MAX_NUM_COMMS_EVENT_SAMPLES) {
        ERROR_PRINT("BAD INDEX\n");
        return;
    }

#ifdef MAG_SLAVE_PRESENT
    if (mSensor->idx == MAG && (newMagBias || !mTask.magBiasPosted)) {
        if (mSensor->data_evt->samples[0].firstSample.numSamples > 0) {
            // flush existing samples so the bias appears after them
            flushData(mSensor,
                    EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSensorInfo[MAG].sensorType));
            if (!allocateDataEvt(mSensor, rtc_time)) {
                return;
            }
        }
        if (newMagBias) {
            mTask.magBiasCurrent = true;
        }
        mSensor->data_evt->samples[0].firstSample.biasCurrent = mTask.magBiasCurrent;
        mSensor->data_evt->samples[0].firstSample.biasPresent = 1;
        mSensor->data_evt->samples[0].firstSample.biasSample =
                mSensor->data_evt->samples[0].firstSample.numSamples;
        sample = &mSensor->data_evt->samples[mSensor->data_evt->samples[0].firstSample.numSamples++];
        magCalGetBias(&mTask.moc, &sample->x, &sample->y, &sample->z);
        // bias is non-discardable, if we fail to enqueue, don't clear new_mag_bias
        if (flushData(mSensor, sensorGetMyEventType(mSensorInfo[MAG].biasType))) {
            mTask.magBiasPosted = true;
        }

        if (!allocateDataEvt(mSensor, rtc_time)) {
            return;
        }
    }
#endif
#ifdef GYRO_CAL_ENABLED
    if (mSensor->idx == GYR) {
      // GyroCal -- Checks for a new offset estimate update.
      float gyro_offset[3] = {0.0f, 0.0f, 0.0f};
      float gyro_offset_temperature_celsius = 0.0f;
      bool new_gyrocal_offset_update = gyroCalNewBiasAvailable(&mTask.gyro_cal);
      if (new_gyrocal_offset_update) {
        // GyroCal -- Gets the GyroCal offset estimate.
        gyroCalGetBias(&mTask.gyro_cal, &gyro_offset[0], &gyro_offset[1],
                       &gyro_offset[2], &gyro_offset_temperature_celsius);

#ifdef OVERTEMPCAL_ENABLED
        // OTC-Gyro Cal -- Sends a new GyroCal estimate to the OTC-Gyro.
        overTempCalUpdateSensorEstimate(&mTask.over_temp_gyro_cal, rtc_time,
                                        gyro_offset,
                                        gyro_offset_temperature_celsius);
#endif  // OVERTEMPCAL_ENABLED
      }

#ifdef OVERTEMPCAL_ENABLED
      // OTC-Gyro Cal --  Gets the latest OTC-Gyro temperature compensated
      // offset estimate.
      bool new_otc_offset_update =
          overTempCalNewOffsetAvailable(&mTask.over_temp_gyro_cal);
      overTempCalGetOffset(&mTask.over_temp_gyro_cal,
                           &gyro_offset_temperature_celsius, gyro_offset);

      // OTC-Gyro Cal --  Checks for a model update.
      bool new_otc_model_update =
          overTempCalNewModelUpdateAvailable(&mTask.over_temp_gyro_cal);

      if (new_otc_offset_update) {
#else   // OVERTEMPCAL_ENABLED
      if (new_gyrocal_offset_update) {
#endif  // OVERTEMPCAL_ENABLED
        if (mSensor->data_evt->samples[0].firstSample.numSamples > 0) {
          // flush existing samples so the bias appears after them.
          flushData(mSensor,
                    EVENT_TYPE_BIT_DISCARDABLE |
                        sensorGetMyEventType(mSensorInfo[GYR].sensorType));
          if (!allocateDataEvt(mSensor, rtc_time)) {
            return;
          }
        }
        mSensor->data_evt->samples[0].firstSample.biasCurrent = true;
        mSensor->data_evt->samples[0].firstSample.biasPresent = 1;
        mSensor->data_evt->samples[0].firstSample.biasSample =
            mSensor->data_evt->samples[0].firstSample.numSamples;
        sample = &mSensor->data_evt->samples[mSensor->data_evt->samples[0]
                                                 .firstSample.numSamples++];
        // Updates the gyro offset in HAL.
        sample->x = gyro_offset[0];
        sample->y = gyro_offset[1];
        sample->z = gyro_offset[2];

#if defined(GYRO_CAL_DBG_ENABLED) || defined(OVERTEMPCAL_DBG_ENABLED)
        CAL_DEBUG_LOG("[GYRO_OFFSET:STORED]",
                      "Offset|Temp|Time: %s%d.%06d, %s%d.%06d, %s%d.%06d | "
                      "%s%d.%06d | %llu",
                      CAL_ENCODE_FLOAT(sample->x, 6),
                      CAL_ENCODE_FLOAT(sample->y, 6),
                      CAL_ENCODE_FLOAT(sample->z, 6),
                      CAL_ENCODE_FLOAT(gyro_offset_temperature_celsius, 6),
                      (unsigned long long int)rtc_time);
#endif  // GYRO_CAL_DBG_ENABLED || OVERTEMPCAL_DBG_ENABLED

        flushData(mSensor, sensorGetMyEventType(mSensorInfo[GYR].biasType));
        if (!allocateDataEvt(mSensor, rtc_time)) {
          return;
        }
      }
#ifdef OVERTEMPCAL_ENABLED
      if (new_otc_model_update || new_otc_offset_update) {
        // Notify HAL to store new gyro OTC-Gyro data.
        T(otcGyroUpdateBuffer).sendToHostRequest = true;
      }
#endif  // OVERTEMPCAL_ENABLED
    }
#endif  // GYRO_CAL_ENABLED

    sample = &mSensor->data_evt->samples[mSensor->data_evt->samples[0].firstSample.numSamples++];

    // the first deltatime is for sample size
    if (mSensor->data_evt->samples[0].firstSample.numSamples > 1) {
        delta_time = rtc_time - mSensor->prev_rtc_time;
        delta_time = delta_time < 0 ? 0 : delta_time;
        sample->deltaTime = delta_time;
        mSensor->prev_rtc_time = rtc_time;
    }

    sample->x = x;
    sample->y = y;
    sample->z = z;

    //DEBUG_PRINT("bmi160: x: %d, y: %d, z: %d\n", (int)(1000*x), (int)(1000*y), (int)(1000*z));

    //TODO: This was added to prevent to much data of the same type accumulate in internal buffer.
    //      It might no longer be necessary and can be removed.
    if (mSensor->data_evt->samples[0].firstSample.numSamples == MAX_NUM_COMMS_EVENT_SAMPLES) {
        flushAllData();
    }
}

static void dispatchData(void)
{
    size_t i = 1, j;
    size_t size = mTask.xferCnt;
    int fh_mode, fh_param;
    uint8_t *buf = mTask.dataBuffer;

    uint64_t min_delta = ULONG_LONG_MAX;
    uint32_t sensor_time24;
    uint64_t full_sensor_time;
    uint64_t frame_sensor_time = mTask.frame_sensortime;
    bool observed[NUM_CONT_SENSOR];
    uint64_t tmp_frame_time, tmp_time[NUM_CONT_SENSOR];
    bool frame_sensor_time_valid = mTask.frame_sensortime_valid;
    bool saved_pending_delta[NUM_CONT_SENSOR];
    uint64_t saved_time_delta[NUM_CONT_SENSOR];
#if TIMESTAMP_DBG
    int frame_num = -1;
#endif

    for (j = FIRST_CONT_SENSOR; j < NUM_CONT_SENSOR; j++)
        observed[j] = false;

    if (!mTask.frame_sensortime_valid) {
        // This is the first FIFO delivery after any sensor is enabled in
        // bmi160. Sensor time reference is not establised until end of this
        // FIFO frame. Assume time start from zero and do a dry run to estimate
        // the time and then go through this FIFO again.
        frame_sensor_time = 0ull;

        // Save these states for future recovery by the end of dry run.
        for (j = FIRST_CONT_SENSOR; j < NUM_CONT_SENSOR; j++) {
            saved_pending_delta[j] = mTask.pending_delta[j];
            saved_time_delta[j] = mTask.time_delta[j];
        }
    }

    while (size > 0) {
        if (buf[i] == BMI160_FRAME_HEADER_INVALID) {
            // reaching invalid header means no more data
            break;
        } else if (buf[i] == BMI160_FRAME_HEADER_SKIP) {
            // manually injected skip header
            DEBUG_PRINT_IF(DBG_CHUNKED, "skip nop header");
            i++;
            size--;
            continue;
        }

        fh_mode = buf[i] >> 6;
        fh_param = (buf[i] >> 2) & 0xf;

        i++;
        size--;
#if TIMESTAMP_DBG
        ++frame_num;
#endif

        if (fh_mode == 1) {
            // control frame.
            if (fh_param == 0) {
                // skip frame, we skip it
                if (size >= 1) {
                    i++;
                    size--;
                } else {
                    size = 0;
                }
            } else if (fh_param == 1) {
                // sensortime frame
                if (size >= 3) {
                    // The active sensor with the highest odr/lowest delta is the one that
                    // determines the sensor time increments.
                    for (j = FIRST_CONT_SENSOR; j < NUM_CONT_SENSOR; j++) {
                        if (mTask.sensors[j].configed &&
                                mTask.sensors[j].latency != SENSOR_LATENCY_NODATA) {
                            min_delta = min_delta < mTask.time_delta[j] ? min_delta :
                                    mTask.time_delta[j];
                        }
                    }
                    sensor_time24 = buf[i + 2] << 16 | buf[i + 1] << 8 | buf[i];

                    // clear lower bits that measure time from taking the sample to reading the
                    // FIFO, something we're not interested in.
                    sensor_time24 &= ~(min_delta - 1);

                    full_sensor_time = parseSensortime(sensor_time24);

#if TIMESTAMP_DBG
                    if (frame_sensor_time == full_sensor_time) {
                        //DEBUG_PRINT("frame %d FrameTime 0x%08x\n",
                        //        frame_num - 1,
                        //        (unsigned int)frame_sensor_time);
                    } else if (frame_sensor_time_valid) {
                        DEBUG_PRINT("frame %d FrameTime 0x%08x != SensorTime 0x%08x, jumped %d msec\n",
                                frame_num - 1,
                                (unsigned int)frame_sensor_time,
                                (unsigned int)full_sensor_time,
                                (int)(5 * ((int64_t)(full_sensor_time - frame_sensor_time) >> 7)));
                    }
#endif


                    if (frame_sensor_time_valid) {
                        mTask.frame_sensortime = full_sensor_time;
                    } else {
                        // Dry run if frame_sensortime_valid == false,
                        // no sample is added this round.
                        // So let's time travel back to beginning of frame.
                        mTask.frame_sensortime_valid = true;
                        mTask.frame_sensortime = full_sensor_time - frame_sensor_time;

                        // recover states
                        for (j = FIRST_CONT_SENSOR; j < NUM_CONT_SENSOR; j++) {
                            // reset all prev_frame_time to invalid values
                            // they should be so anyway at the first FIFO
                            mTask.prev_frame_time[j] = ULONG_LONG_MAX;

                            // recover saved time_delta and pending_delta values
                            mTask.pending_delta[j] = saved_pending_delta[j];
                            mTask.time_delta[j] = saved_time_delta[j];
                        }

                        DEBUG_PRINT_IF(TIMESTAMP_DBG,
                                "sensortime invalid: full, frame, task = %llu, %llu, %llu\n",
                                full_sensor_time,
                                frame_sensor_time,
                                mTask.frame_sensortime);

                        // Parse again with known valid timing.
                        // This time the sensor events will be committed into event buffer.
                        return dispatchData();
                    }

                    // Invalidate sensor timestamp that didn't get corrected by full_sensor_time,
                    // so it can't be used as a reference at next FIFO read.
                    // Use (ULONG_LONG_MAX - 1) to indicate this.
                    for (j = FIRST_CONT_SENSOR; j < NUM_CONT_SENSOR; j++) {
                        mTask.prev_frame_time[j] = observed[j] ? full_sensor_time : (ULONG_LONG_MAX - 1);

                        // sensor can be disabled in the middle of the FIFO, but wait till the FIFO
                        // end to invalidate prev_frame_time since it's still needed for parsing.
                        // Also invalidate pending delta just to be safe.
                        if (!mTask.sensors[j].configed ||
                                mTask.sensors[j].latency == SENSOR_LATENCY_NODATA) {
                            mTask.prev_frame_time[j] = ULONG_LONG_MAX;
                            mTask.pending_delta[j] = false;
                        }
                    }
                    i += 3;
                    size -= 3;
                } else {
                    size = 0;
                }
            } else if (fh_param == 2) {
                // fifo_input config frame
#if TIMESTAMP_DBG
                DEBUG_PRINT("frame %d config change 0x%02x\n", frame_num, buf[i]);
#endif
                if (size >= 1) {
                    for (j = FIRST_CONT_SENSOR; j < NUM_CONT_SENSOR; j++) {
                        if (buf[i] & (0x01 << (j << 1)) && mTask.pending_delta[j]) {
                            mTask.pending_delta[j] = false;
                            mTask.time_delta[j] = mTask.next_delta[j];
#if TIMESTAMP_DBG
                            DEBUG_PRINT("%s new delta %u\n", mSensorInfo[j].sensorName,
                                    (unsigned int)mTask.time_delta[j]);
#endif
                        }
                    }
                    i++;
                    size--;
                } else {
                    size = 0;
                }
            } else {
                size = 0; // drop this batch
                ERROR_PRINT("Invalid fh_param in control frame\n");
            }
        } else if (fh_mode == 2) {
            // Calcutate candidate frame time (tmp_frame_time):
            // 1) When sensor is first enabled, reference from other sensors if possible.
            // Otherwise, add the smallest increment to the previous data frame time.
            // 2) The newly enabled sensor could only underestimate its
            // frame time without reference from other sensors.
            // 3) The underestimated frame time of a newly enabled sensor will be corrected
            // as soon as it shows up in the same frame with another sensor.
            // 4) (prev_frame_time == ULONG_LONG_MAX) means the sensor wasn't enabled.
            // 5) (prev_frame_time == ULONG_LONG_MAX -1) means the sensor didn't appear in the last
            // data frame of the previous fifo read.  So it won't be used as a frame time reference.

            tmp_frame_time = 0;
            for (j = FIRST_CONT_SENSOR; j < NUM_CONT_SENSOR; j++) {
                observed[j] = false; // reset at each data frame
                tmp_time[j] = 0;
                if ((mTask.prev_frame_time[j] < ULONG_LONG_MAX - 1) && (fh_param & (1 << j))) {
                    tmp_time[j] = mTask.prev_frame_time[j] + mTask.time_delta[j];
                    tmp_frame_time = (tmp_time[j] > tmp_frame_time) ? tmp_time[j] : tmp_frame_time;
                }
            }
            tmp_frame_time = (frame_sensor_time + kMinSensorTimeIncrement > tmp_frame_time)
                ? (frame_sensor_time + kMinSensorTimeIncrement) : tmp_frame_time;

            // regular frame, dispatch data to each sensor's own fifo
#ifdef MAG_SLAVE_PRESENT
            if (fh_param & 4) { // have mag data
                if (size >= 8) {
                    if (frame_sensor_time_valid) {
                        // scale not used
                        parseRawData(&mTask.sensors[MAG], &buf[i], 0, tmp_frame_time);
#if TIMESTAMP_DBG
                        if (mTask.prev_frame_time[MAG] == ULONG_LONG_MAX) {
                            DEBUG_PRINT("mag enabled: frame %d time 0x%08x\n",
                                    frame_num, (unsigned int)tmp_frame_time);
                        } else if ((tmp_frame_time != tmp_time[MAG]) && (tmp_time[MAG] != 0)) {
                            DEBUG_PRINT("frame %d mag time: 0x%08x -> 0x%08x, jumped %d msec\n",
                                    frame_num,
                                    (unsigned int)tmp_time[MAG],
                                    (unsigned int)tmp_frame_time,
                                    (int)(5 * ((int64_t)(tmp_frame_time - tmp_time[MAG]) >> 7)));
                        }
#endif
                    }
                    mTask.prev_frame_time[MAG] = tmp_frame_time;
                    i += 8;
                    size -= 8;
                    observed[MAG] = true;
                } else {
                    size = 0;
                }
            }
#endif
            if (fh_param & 2) { // have gyro data
                if (size >= 6) {
                    if (frame_sensor_time_valid) {
                        parseRawData(&mTask.sensors[GYR], &buf[i], kScale_gyr, tmp_frame_time);
#if TIMESTAMP_DBG
                        if (mTask.prev_frame_time[GYR] == ULONG_LONG_MAX) {
                            DEBUG_PRINT("gyr enabled: frame %d time 0x%08x\n",
                                    frame_num, (unsigned int)tmp_frame_time);
                        } else if ((tmp_frame_time != tmp_time[GYR]) && (tmp_time[GYR] != 0)) {
                            DEBUG_PRINT("frame %d gyr time: 0x%08x -> 0x%08x, jumped %d msec\n",
                                    frame_num,
                                    (unsigned int)tmp_time[GYR],
                                    (unsigned int)tmp_frame_time,
                                    (int)(5 * ((int64_t)(tmp_frame_time - tmp_time[GYR]) >> 7)));
                        }
#endif
                    }
                    mTask.prev_frame_time[GYR] = tmp_frame_time;
                    i += 6;
                    size -= 6;
                    observed[GYR] = true;
                } else {
                    size = 0;
                }
            }
            if (fh_param & 1) { // have accel data
                if (size >= 6) {
                    if (frame_sensor_time_valid) {
                        parseRawData(&mTask.sensors[ACC], &buf[i], kScale_acc, tmp_frame_time);
#if TIMESTAMP_DBG
                        if (mTask.prev_frame_time[ACC] == ULONG_LONG_MAX) {
                            DEBUG_PRINT("acc enabled: frame %d time 0x%08x\n",
                                    frame_num, (unsigned int)tmp_frame_time);
                        } else if ((tmp_frame_time != tmp_time[ACC]) && (tmp_time[ACC] != 0)) {
                            DEBUG_PRINT("frame %d gyr time: 0x%08x -> 0x%08x, jumped %d msec\n",
                                    frame_num,
                                    (unsigned int)tmp_time[ACC],
                                    (unsigned int)tmp_frame_time,
                                    (int)(5 * ((int64_t)(tmp_frame_time - tmp_time[ACC]) >> 7)));
                        }
#endif
                    }
                    mTask.prev_frame_time[ACC] = tmp_frame_time;
                    i += 6;
                    size -= 6;
                    observed[ACC] = true;
                } else {
                    size = 0;
                }
            }

            if (observed[ACC] || observed[GYR])
                frame_sensor_time = tmp_frame_time;
#ifdef MAG_SLAVE_PRESENT
            else if (observed[MAG])
                frame_sensor_time = tmp_frame_time;
#endif
        } else {
            size = 0; // drop this batch
            ERROR_PRINT("Invalid fh_mode %d at 0x%x, data dump:\n", fh_mode, i);
            // dump (a) bytes back and (b) bytes forward.
            int a = i < 0x80 ? 0 : (i - 0x80) & ~0x0F;
            int b = ((i + 0x80 > mTask.xferCnt ? mTask.xferCnt : i + 0x80) + 0x0F) & ~0x0F;
            dumpBinary(mTask.dataBuffer, a, b - a);
        }
    }

    //flush data events.
    flushAllData();
}

/*
 * Read the interrupt type and send corresponding event
 * If it's anymo or double tap, also send a single uint32 to indicate which axies
 * is this interrupt triggered.
 * If it's flat, also send a bit to indicate flat/non-flat position.
 * If it's step detector, check if we need to send the total step count.
 */
static void int2Handling(void)
{
    TDECL();
    union EmbeddedDataPoint trigger_axies;
    uint8_t int_status_0 = mTask.statusBuffer[1];
    uint8_t int_status_1 = mTask.statusBuffer[2];
    if (int_status_0 & INT_STEP) {
        if (mTask.sensors[STEP].powered) {
            DEBUG_PRINT("Detected step\n");
            osEnqueueEvt(EVT_SENSOR_STEP, NULL, NULL);
        }
        if (mTask.sensors[STEPCNT].powered) {
            T(pending_step_cnt) = true;
        }
    }
    if ((int_status_0 & INT_ANY_MOTION) && mTask.sensors[ANYMO].powered) {
        // bit [0:2] of INT_STATUS[2] is set when anymo is triggered by x, y or
        // z axies respectively. bit [3] indicates the slope.
        trigger_axies.idata = (mTask.statusBuffer[3] & 0x0f);
        DEBUG_PRINT("Detected any motion\n");
        osEnqueueEvt(EVT_SENSOR_ANY_MOTION, trigger_axies.vptr, NULL);
    }
    if ((int_status_0 & INT_DOUBLE_TAP) && mTask.sensors[DTAP].powered) {
        // bit [4:6] of INT_STATUS[2] is set when double tap is triggered by
        // x, y or z axies respectively. bit [7] indicates the slope.
        trigger_axies.idata = ((mTask.statusBuffer[3] & 0xf0) >> 4);
        DEBUG_PRINT("Detected double tap\n");
        osEnqueueEvt(EVT_SENSOR_DOUBLE_TAP, trigger_axies.vptr, NULL);
    }
    if ((int_status_0 & INT_FLAT) && mTask.sensors[FLAT].powered) {
        // bit [7] of INT_STATUS[3] indicates flat/non-flat position
        trigger_axies.idata = ((mTask.statusBuffer[4] & 0x80) >> 7);
        DEBUG_PRINT("Detected flat\n");
        osEnqueueEvt(EVT_SENSOR_FLAT, trigger_axies.vptr, NULL);
    }
    if ((int_status_1 & INT_NO_MOTION) && mTask.sensors[NOMO].powered) {
        DEBUG_PRINT("Detected no motion\n");
        osEnqueueEvt(EVT_SENSOR_NO_MOTION, NULL, NULL);
    }
    return;
}

static void int2Evt(void)
{
    TDECL();
    if (trySwitchState(SENSOR_INT_2_HANDLING)) {
        // Read the interrupt reg value to determine what interrupts
        SPI_READ(BMI160_REG_INT_STATUS_0, 4, &mTask.statusBuffer);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask, __FUNCTION__);
    } else {
        // even if we are still in SENSOR_INT_2_HANDLING, the SPI may already finished and we need
        // to issue another SPI read to get the latest status
        mTask.pending_int[1] = true;
    }
}

// bits[6:7] in OFFSET[6] to enable/disable gyro/accel offset.
// bits[0:5] in OFFSET[6] stores the most significant 2 bits of gyro offset at
// its x, y, z axies.
// Calculate the stored gyro offset and compose it with the intended
// enable/disable mode for gyro/accel offset to determine the value for
// OFFSET[6].
static uint8_t offset6Mode(void)
{
    uint8_t mode = 0;
    if (mTask.sensors[GYR].offset_enable)
        mode |= 0x01 << 7;
    if (mTask.sensors[ACC].offset_enable)
        mode |= 0x01 << 6;
    mode |= (mTask.sensors[GYR].offset[2] & 0x0300) >> 4;
    mode |= (mTask.sensors[GYR].offset[1] & 0x0300) >> 6;
    mode |= (mTask.sensors[GYR].offset[0] & 0x0300) >> 8;
    DEBUG_PRINT("OFFSET_6_MODE is: %02x\n", mode);
    return mode;
}

static bool saveCalibration()
{
    TDECL();
    if (trySwitchState(SENSOR_SAVE_CALIBRATION)) {
        if (mTask.sensors[ACC].offset_enable) {
            SPI_WRITE(BMI160_REG_OFFSET_0, mTask.sensors[ACC].offset[0] & 0xFF, 450);
            SPI_WRITE(BMI160_REG_OFFSET_0 + 1, mTask.sensors[ACC].offset[1] & 0xFF, 450);
            SPI_WRITE(BMI160_REG_OFFSET_0 + 2, mTask.sensors[ACC].offset[2] & 0xFF, 450);
        }
        if (mTask.sensors[GYR].offset_enable) {
            SPI_WRITE(BMI160_REG_OFFSET_3, mTask.sensors[GYR].offset[0] & 0xFF, 450);
            SPI_WRITE(BMI160_REG_OFFSET_3 + 1, mTask.sensors[GYR].offset[1] & 0xFF, 450);
            SPI_WRITE(BMI160_REG_OFFSET_3 + 2, mTask.sensors[GYR].offset[2] & 0xFF, 450);
        }
        SPI_WRITE(BMI160_REG_OFFSET_6, offset6Mode(), 450);
        SPI_READ(BMI160_REG_OFFSET_0, 7, &mTask.dataBuffer);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, NULL, __FUNCTION__);
        return true;
    } else {
        DEBUG_PRINT("%s, state != IDLE", __FUNCTION__);
        return false;
    }
}

static void sendCalibrationResult(uint8_t status, uint8_t sensorType,
        int32_t xBias, int32_t yBias, int32_t zBias) {
    struct CalibrationData *data = heapAlloc(sizeof(struct CalibrationData));
    if (!data) {
        osLog(LOG_WARN, "Couldn't alloc cal result pkt");
        return;
    }

    data->header.appId = BMI160_APP_ID;
    data->header.dataLen = (sizeof(struct CalibrationData) - sizeof(struct HostHubRawPacket));
    data->data_header.msgId = SENSOR_APP_MSG_ID_CAL_RESULT;
    data->data_header.sensorType = sensorType;
    data->data_header.status = status;

    data->xBias = xBias;
    data->yBias = yBias;
    data->zBias = zBias;

    if (!osEnqueueEvtOrFree(EVT_APP_TO_HOST, data, heapFree))
        osLog(LOG_WARN, "Couldn't send cal result evt");
}

static void accCalibrationHandling(void)
{
    TDECL();
    switch (mTask.calibration_state) {
    case CALIBRATION_START:
        T(mRetryLeft) = RETRY_CNT_CALIBRATION;

        // turn ACC to NORMAL mode
        SPI_WRITE(BMI160_REG_CMD, 0x11, 50000);

        mTask.calibration_state = CALIBRATION_FOC;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;
    case CALIBRATION_FOC:

        // set accel range
        SPI_WRITE(BMI160_REG_ACC_RANGE, ACC_RANGE_SETTING);

        // enable accel fast offset compensation,
        // x: 0g, y: 0g, z: 1g
        SPI_WRITE(BMI160_REG_FOC_CONF, ACC_FOC_CONFIG);

        // start calibration
        SPI_WRITE(BMI160_REG_CMD, 0x03, 100000);

        // poll the status reg until the calibration finishes.
        SPI_READ(BMI160_REG_STATUS, 1, &mTask.statusBuffer, 50000);

        mTask.calibration_state = CALIBRATION_WAIT_FOC_DONE;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;
    case CALIBRATION_WAIT_FOC_DONE:
        // if the STATUS REG has bit 3 set, it means calbration is done.
        // otherwise, check back in 50ms later.
        if (mTask.statusBuffer[1] & 0x08) {

            //disable FOC
            SPI_WRITE(BMI160_REG_FOC_CONF, 0x00);

            //read the offset value for accel
            SPI_READ(BMI160_REG_OFFSET_0, 3, &mTask.dataBuffer);
            mTask.calibration_state = CALIBRATION_SET_OFFSET;
            DEBUG_PRINT("FOC set FINISHED!\n");
        } else {

            // calibration hasn't finished yet, go back to wait for 50ms.
            SPI_READ(BMI160_REG_STATUS, 1, &mTask.statusBuffer, 50000);
            mTask.calibration_state = CALIBRATION_WAIT_FOC_DONE;
            T(mRetryLeft)--;
        }
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);

        // if calbration hasn't finished after 10 polling on the STATUS reg,
        // declare timeout.
        if (T(mRetryLeft) == 0) {
            mTask.calibration_state = CALIBRATION_TIMEOUT;
        }
        break;
    case CALIBRATION_SET_OFFSET:
        mTask.sensors[ACC].offset[0] = mTask.dataBuffer[1];
        mTask.sensors[ACC].offset[1] = mTask.dataBuffer[2];
        mTask.sensors[ACC].offset[2] = mTask.dataBuffer[3];
        // sign extend values
        if (mTask.sensors[ACC].offset[0] & 0x80)
            mTask.sensors[ACC].offset[0] |= 0xFFFFFF00;
        if (mTask.sensors[ACC].offset[1] & 0x80)
            mTask.sensors[ACC].offset[1] |= 0xFFFFFF00;
        if (mTask.sensors[ACC].offset[2] & 0x80)
            mTask.sensors[ACC].offset[2] |= 0xFFFFFF00;

        mTask.sensors[ACC].offset_enable = true;
        DEBUG_PRINT("ACCELERATION OFFSET is %02x  %02x  %02x\n",
                (unsigned int)mTask.sensors[ACC].offset[0],
                (unsigned int)mTask.sensors[ACC].offset[1],
                (unsigned int)mTask.sensors[ACC].offset[2]);

        sendCalibrationResult(SENSOR_APP_EVT_STATUS_SUCCESS, SENS_TYPE_ACCEL,
                mTask.sensors[ACC].offset[0], mTask.sensors[ACC].offset[1],
                mTask.sensors[ACC].offset[2]);

        // Enable offset compensation for accel
        uint8_t mode = offset6Mode();
        SPI_WRITE(BMI160_REG_OFFSET_6, mode);

        // turn ACC to SUSPEND mode
        SPI_WRITE(BMI160_REG_CMD, 0x10, 5000);

        mTask.calibration_state = CALIBRATION_DONE;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;
    default:
        ERROR_PRINT("Invalid calibration state\n");
        break;
    }
}

static bool accCalibration(void *cookie)
{
    TDECL();
    if (!mTask.sensors[ACC].powered && trySwitchState(SENSOR_CALIBRATING)) {
        mTask.calibration_state = CALIBRATION_START;
        accCalibrationHandling();
        return true;
    } else {
        ERROR_PRINT("cannot calibrate accel because sensor is busy\n");
        sendCalibrationResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_ACCEL, 0, 0, 0);
        return false;
    }
}

static bool accCfgData(void *data, void *cookie)
{
    struct CfgData {
        int32_t hw[3];
        float sw[3];
    };
    struct CfgData *values = data;

    mTask.sensors[ACC].offset[0] = values->hw[0];
    mTask.sensors[ACC].offset[1] = values->hw[1];
    mTask.sensors[ACC].offset[2] = values->hw[2];
    mTask.sensors[ACC].offset_enable = true;

#ifdef ACCEL_CAL_ENABLED
    accelCalBiasSet(&mTask.acc, values->sw[0], values->sw[1], values->sw[2]);
#endif

    INFO_PRINT("accCfgData: data=%02lx, %02lx, %02lx\n",
            values->hw[0] & 0xFF, values->hw[1] & 0xFF, values->hw[2] & 0xFF);

    if (!saveCalibration()) {
        mTask.pending_calibration_save = true;
    }

    return true;
}

static void sendTestResult(uint8_t status, uint8_t sensorType) {
    struct TestResultData *data = heapAlloc(sizeof(struct TestResultData));
    if (!data) {
        osLog(LOG_WARN, "Couldn't alloc test result packet");
        return;
    }

    data->header.appId = BMI160_APP_ID;
    data->header.dataLen = (sizeof(struct TestResultData) - sizeof(struct HostHubRawPacket));
    data->data_header.msgId = SENSOR_APP_MSG_ID_TEST_RESULT;
    data->data_header.sensorType = sensorType;
    data->data_header.status = status;

    if (!osEnqueueEvtOrFree(EVT_APP_TO_HOST, data, heapFree))
        osLog(LOG_WARN, "Couldn't send test result packet");
}

static void accTestHandling(void)
{
    // the minimum absolute differences, according to BMI160 datasheet section
    // 2.8.1, are 800 mg for the x and y axes and 400 mg for the z axis
    static const int32_t kMinDifferenceXY = (800 * 32767) / 8000;
    static const int32_t kMinDifferenceZ = (400 * 32767) / 8000;

    int32_t tempTestX, tempTestY, tempTestZ;
    int32_t absDiffX, absDiffY, absDiffZ;

    TDECL();

    switch (mTask.acc_test_state) {
    case ACC_TEST_START:
        // turn ACC to NORMAL mode
        SPI_WRITE(BMI160_REG_CMD, 0x11, 50000);

        mTask.acc_test_state = ACC_TEST_CONFIG;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;

    case ACC_TEST_CONFIG:
        // set accel conf
        SPI_WRITE(BMI160_REG_ACC_CONF, 0x2c);

        // set accel range
        SPI_WRITE(BMI160_REG_ACC_RANGE, ACC_RANGE_SETTING);

        // read stale accel data
        SPI_READ(BMI160_REG_DATA_14, 6, &mTask.dataBuffer);

        mTask.acc_test_state = ACC_TEST_RUN_0;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;

    case ACC_TEST_RUN_0:
        // configure acc_self_test_amp=1, acc_self_test_sign=0, acc_self_test_enable=b01
        // wait 50ms for data to be available
        SPI_WRITE(BMI160_REG_SELF_TEST, 0x09, 50000);

        // read accel data
        SPI_READ(BMI160_REG_DATA_14, 6, &mTask.dataBuffer);

        mTask.acc_test_state = ACC_TEST_RUN_1;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;

    case ACC_TEST_RUN_1:
        // save accel data
        mTask.accTestX = *(int16_t*)(mTask.dataBuffer+1);
        mTask.accTestY = *(int16_t*)(mTask.dataBuffer+3);
        mTask.accTestZ = *(int16_t*)(mTask.dataBuffer+5);

        // configure acc_self_test_amp=1, acc_self_test_sign=1, acc_self_test_enable=b01
        // wait 50ms for data to be available
        SPI_WRITE(BMI160_REG_SELF_TEST, 0x0d, 50000);

        // read accel data
        SPI_READ(BMI160_REG_DATA_14, 6, &mTask.dataBuffer);

        mTask.acc_test_state = ACC_TEST_VERIFY;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;

    case ACC_TEST_VERIFY:
        // save accel data
        tempTestX = *(int16_t*)(mTask.dataBuffer+1);
        tempTestY = *(int16_t*)(mTask.dataBuffer+3);
        tempTestZ = *(int16_t*)(mTask.dataBuffer+5);

        // calculate the differences between run 0 and run 1
        absDiffX = ABS((int32_t)mTask.accTestX - tempTestX);
        absDiffY = ABS((int32_t)mTask.accTestY - tempTestY);
        absDiffZ = ABS((int32_t)mTask.accTestZ - tempTestZ);

        DEBUG_PRINT("accSelfTest diffs: X %d, Y %d, Z %d\n", (int)absDiffX, (int)absDiffY, (int)absDiffZ);

        // verify that the differences between run 0 and run 1 are within spec
        if (absDiffX >= kMinDifferenceXY && absDiffY >= kMinDifferenceXY && absDiffZ >= kMinDifferenceZ) {
            sendTestResult(SENSOR_APP_EVT_STATUS_SUCCESS, SENS_TYPE_ACCEL);
        } else {
            sendTestResult(SENSOR_APP_EVT_STATUS_ERROR, SENS_TYPE_ACCEL);
        }

        // turn ACC to SUSPEND mode
        SPI_WRITE(BMI160_REG_CMD, 0x10, 5000);

        mTask.acc_test_state = ACC_TEST_DONE;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[ACC], __FUNCTION__);
        break;

    default:
        ERROR_PRINT("Invalid accel test state\n");
        break;
    }
}

static bool accSelfTest(void *cookie)
{
    TDECL();
    INFO_PRINT("accSelfTest\n");

    if (!mTask.sensors[ACC].powered && trySwitchState(SENSOR_TESTING)) {
        mTask.acc_test_state = ACC_TEST_START;
        accTestHandling();
        return true;
    } else {
        ERROR_PRINT("cannot test accel because sensor is busy\n");
        sendTestResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_ACCEL);
        return false;
    }
}

static void gyrCalibrationHandling(void)
{
    TDECL();
    switch (mTask.calibration_state) {
    case CALIBRATION_START:
        T(mRetryLeft) = RETRY_CNT_CALIBRATION;

        // turn GYR to NORMAL mode
        SPI_WRITE(BMI160_REG_CMD, 0x15, 50000);

        mTask.calibration_state = CALIBRATION_FOC;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
        break;
    case CALIBRATION_FOC:

        // set gyro range to +-1000 deg/sec
        SPI_WRITE(BMI160_REG_GYR_RANGE, 0x01);

        // enable gyro fast offset compensation
        SPI_WRITE(BMI160_REG_FOC_CONF, 0x40);

        // start FOC
        SPI_WRITE(BMI160_REG_CMD, 0x03, 100000);

        // poll the status reg until the calibration finishes.
        SPI_READ(BMI160_REG_STATUS, 1, &mTask.statusBuffer, 50000);

        mTask.calibration_state = CALIBRATION_WAIT_FOC_DONE;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
        break;
    case CALIBRATION_WAIT_FOC_DONE:

        // if the STATUS REG has bit 3 set, it means calbration is done.
        // otherwise, check back in 50ms later.
        if (mTask.statusBuffer[1] & 0x08) {

            // disable gyro fast offset compensation
            SPI_WRITE(BMI160_REG_FOC_CONF, 0x00);

            //read the offset value for gyro
            SPI_READ(BMI160_REG_OFFSET_3, 4, &mTask.dataBuffer);
            mTask.calibration_state = CALIBRATION_SET_OFFSET;
            DEBUG_PRINT("FOC set FINISHED!\n");
        } else {

            // calibration hasn't finished yet, go back to wait for 50ms.
            SPI_READ(BMI160_REG_STATUS, 1, &mTask.statusBuffer, 50000);
            mTask.calibration_state = CALIBRATION_WAIT_FOC_DONE;
            T(mRetryLeft)--;
        }
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);

        // if calbration hasn't finished after 10 polling on the STATUS reg,
        // declare timeout.
        if (T(mRetryLeft) == 0) {
            mTask.calibration_state = CALIBRATION_TIMEOUT;
        }
        break;
    case CALIBRATION_SET_OFFSET:
        mTask.sensors[GYR].offset[0] = ((mTask.dataBuffer[4] & 0x03) << 8) | mTask.dataBuffer[1];
        mTask.sensors[GYR].offset[1] = ((mTask.dataBuffer[4] & 0x0C) << 6) | mTask.dataBuffer[2];
        mTask.sensors[GYR].offset[2] = ((mTask.dataBuffer[4] & 0x30) << 4) | mTask.dataBuffer[3];
        // sign extend values
        if (mTask.sensors[GYR].offset[0] & 0x200)
            mTask.sensors[GYR].offset[0] |= 0xFFFFFC00;
        if (mTask.sensors[GYR].offset[1] & 0x200)
            mTask.sensors[GYR].offset[1] |= 0xFFFFFC00;
        if (mTask.sensors[GYR].offset[2] & 0x200)
            mTask.sensors[GYR].offset[2] |= 0xFFFFFC00;

        mTask.sensors[GYR].offset_enable = true;
        DEBUG_PRINT("GYRO OFFSET is %02x  %02x  %02x\n",
                (unsigned int)mTask.sensors[GYR].offset[0],
                (unsigned int)mTask.sensors[GYR].offset[1],
                (unsigned int)mTask.sensors[GYR].offset[2]);

        sendCalibrationResult(SENSOR_APP_EVT_STATUS_SUCCESS, SENS_TYPE_GYRO,
                mTask.sensors[GYR].offset[0], mTask.sensors[GYR].offset[1],
                mTask.sensors[GYR].offset[2]);

        // Enable offset compensation for gyro
        uint8_t mode = offset6Mode();
        SPI_WRITE(BMI160_REG_OFFSET_6, mode);

        // turn GYR to SUSPEND mode
        SPI_WRITE(BMI160_REG_CMD, 0x14, 1000);

        mTask.calibration_state = CALIBRATION_DONE;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
        break;
    default:
        ERROR_PRINT("Invalid calibration state\n");
        break;
    }
}

static bool gyrCalibration(void *cookie)
{
    TDECL();
    if (!mTask.sensors[GYR].powered && trySwitchState(SENSOR_CALIBRATING)) {
        mTask.calibration_state = CALIBRATION_START;
        gyrCalibrationHandling();
        return true;
    } else {
        ERROR_PRINT("cannot calibrate gyro because sensor is busy\n");
        sendCalibrationResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_GYRO, 0, 0, 0);
        return false;
    }
}

static bool gyrCfgData(void *data, void *cookie)
{
    TDECL();
    const struct AppToSensorHalDataPayload *p = data;
    if (p->type == HALINTF_TYPE_GYRO_CAL_BIAS && p->size == sizeof(struct GyroCalBias)) {
        const struct GyroCalBias *bias = p->gyroCalBias;
        mTask.sensors[GYR].offset[0] = bias->hardwareBias[0];
        mTask.sensors[GYR].offset[1] = bias->hardwareBias[1];
        mTask.sensors[GYR].offset[2] = bias->hardwareBias[2];
        mTask.sensors[GYR].offset_enable = true;
        INFO_PRINT("gyrCfgData hw bias: data=%02lx, %02lx, %02lx\n",
                bias->hardwareBias[0] & 0xFF,
                bias->hardwareBias[1] & 0xFF,
                bias->hardwareBias[2] & 0xFF);

#ifdef GYRO_CAL_ENABLED
        gyroCalSetBias(&T(gyro_cal), bias->softwareBias[0], bias->softwareBias[1],
                       bias->softwareBias[2], sensorGetTime());
#endif  // GYRO_CAL_ENABLED
        if (!saveCalibration()) {
            T(pending_calibration_save) = true;
        }
#if OVERTEMPCAL_ENABLED
    } else if (p->type == HALINTF_TYPE_GYRO_OTC_DATA && p->size == sizeof(struct GyroOtcData)) {
        handleOtcGyroConfig(data);
#endif // OVERTEMPCAL_ENABLED
    } else {
        ERROR_PRINT("Unknown gyro config data type 0x%04x, size %d\n", p->type, p->size);
    }
    return true;
}

static void gyroTestHandling(void)
{
    TDECL();

    switch (mTask.gyro_test_state) {
    case GYRO_TEST_START:
        // turn GYR to NORMAL mode
        SPI_WRITE(BMI160_REG_CMD, 0x15, 50000);

        mTask.gyro_test_state = GYRO_TEST_RUN;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
        break;

    case GYRO_TEST_RUN:
        // set gyr_self_test_enable
        // wait 50ms to check test status
        SPI_WRITE(BMI160_REG_SELF_TEST, 0x10, 50000);

        // check gyro self-test result in status register
        SPI_READ(BMI160_REG_STATUS, 1, &mTask.statusBuffer);

        mTask.gyro_test_state = GYRO_TEST_VERIFY;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
        break;

    case GYRO_TEST_VERIFY:
        // gyr_self_test_ok is bit 1
        if (mTask.statusBuffer[1] & 0x2) {
            sendTestResult(SENSOR_APP_EVT_STATUS_SUCCESS, SENS_TYPE_GYRO);
        } else {
            sendTestResult(SENSOR_APP_EVT_STATUS_ERROR, SENS_TYPE_GYRO);
        }

        // turn GYR to SUSPEND mode
        SPI_WRITE(BMI160_REG_CMD, 0x14, 1000);

        mTask.gyro_test_state = GYRO_TEST_DONE;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask.sensors[GYR], __FUNCTION__);
        break;

    default:
        ERROR_PRINT("Invalid gyro test state\n");
        break;
    }
}

static bool gyrSelfTest(void *cookie)
{
    TDECL();
    INFO_PRINT("gyrSelfTest\n");

    if (!mTask.sensors[GYR].powered && trySwitchState(SENSOR_TESTING)) {
        mTask.gyro_test_state = GYRO_TEST_START;
        gyroTestHandling();
        return true;
    } else {
        ERROR_PRINT("cannot test gyro because sensor is busy\n");
        sendTestResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_GYRO);
        return false;
    }
}

#ifdef MAG_SLAVE_PRESENT
static bool magCfgData(void *data, void *cookie)
{
    const struct AppToSensorHalDataPayload *p = data;
    if (p->type == HALINTF_TYPE_MAG_CAL_BIAS && p->size == sizeof(struct MagCalBias)) {
        const struct MagCalBias *d = p->magCalBias;
        INFO_PRINT("magCfgData: calibration %ldnT, %ldnT, %ldnT\n",
                (int32_t)(d->bias[0] * 1000),
                (int32_t)(d->bias[1] * 1000),
                (int32_t)(d->bias[2] * 1000));

        mTask.moc.x_bias = d->bias[0];
        mTask.moc.y_bias = d->bias[1];
        mTask.moc.z_bias = d->bias[2];
        mTask.magBiasPosted = false;
    } else if (p->type == HALINTF_TYPE_MAG_LOCAL_FIELD && p->size == sizeof(struct MagLocalField)) {
        const struct MagLocalField *d = p->magLocalField;
        INFO_PRINT("magCfgData: local field strength %dnT, dec %ddeg, inc %ddeg\n",
                (int)(d->strength * 1000),
                (int)(d->declination * 180 / M_PI + 0.5f),
                (int)(d->inclination * 180 / M_PI + 0.5f));

        // Passing local field information to mag calibration routine
#ifdef DIVERSITY_CHECK_ENABLED
        diversityCheckerLocalFieldUpdate(&mTask.moc.diversity_checker, d->strength);
#endif
        // TODO: pass local field information to rotation vector sensor.
    } else {
        ERROR_PRINT("magCfgData: unknown type 0x%04x, size %d", p->type, p->size);
    }
    return true;
}
#endif

#define DEC_OPS(power, firmware, rate, flush) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush

#define DEC_OPS_SEND(power, firmware, rate, flush, send) \
    DEC_OPS(power, firmware, rate, flush), \
    .sensorSendOneDirectEvt = send

#define DEC_OPS_CAL_CFG_TEST(power, firmware, rate, flush, cal, cfg, test) \
    DEC_OPS(power, firmware, rate, flush), \
    .sensorCalibrate = cal, \
    .sensorCfgData = cfg, \
    .sensorSelfTest = test,

#define DEC_OPS_CFG(power, firmware, rate, flush, cfg) \
    DEC_OPS(power, firmware, rate, flush), \
    .sensorCfgData = cfg

static const struct SensorOps mSensorOps[NUM_OF_SENSOR] =
{
    { DEC_OPS_CAL_CFG_TEST(accPower, accFirmwareUpload, accSetRate, accFlush, accCalibration,
            accCfgData, accSelfTest) },
    { DEC_OPS_CAL_CFG_TEST(gyrPower, gyrFirmwareUpload, gyrSetRate, gyrFlush, gyrCalibration,
            gyrCfgData, gyrSelfTest) },
#ifdef MAG_SLAVE_PRESENT
    { DEC_OPS_CFG(magPower, magFirmwareUpload, magSetRate, magFlush, magCfgData) },
#endif
    { DEC_OPS(stepPower, stepFirmwareUpload, stepSetRate, stepFlush) },
    { DEC_OPS(doubleTapPower, doubleTapFirmwareUpload, doubleTapSetRate, doubleTapFlush) },
    { DEC_OPS(flatPower, flatFirmwareUpload, flatSetRate, flatFlush) },
    { DEC_OPS(anyMotionPower, anyMotionFirmwareUpload, anyMotionSetRate, anyMotionFlush) },
    { DEC_OPS(noMotionPower, noMotionFirmwareUpload, noMotionSetRate, noMotionFlush) },
    { DEC_OPS_SEND(stepCntPower, stepCntFirmwareUpload, stepCntSetRate, stepCntFlush,
            stepCntSendLastData) },
};

static void configEvent(struct BMI160Sensor *mSensor, struct ConfigStat *ConfigData)
{
    int i;

    for (i = 0; &mTask.sensors[i] != mSensor; i++) ;

    if (ConfigData->enable == 0 && mSensor->powered)
        mSensorOps[i].sensorPower(false, (void *)i);
    else if (ConfigData->enable == 1 && !mSensor->powered)
        mSensorOps[i].sensorPower(true, (void *)i);
    else
        mSensorOps[i].sensorSetRate(ConfigData->rate, ConfigData->latency, (void *)i);
}

static void timeSyncEvt(uint32_t evtGeneration, bool evtDataValid)
{
    TDECL();
    // not processing pending events
    if (evtDataValid) {
        // stale event
        if (evtGeneration != mTask.poll_generation)
            return;

        mTask.active_poll_generation = mTask.poll_generation;
    }

    if (trySwitchState(SENSOR_TIME_SYNC)) {
        SPI_READ(BMI160_REG_SENSORTIME_0, 3, &mTask.sensorTimeBuffer);
        SPI_READ(BMI160_REG_TEMPERATURE_0, 2, &mTask.temperatureBuffer);
        // sensorSpiCallback schedules a private event, which can be delayed
        // by other long-running tasks.
        // Take the rtc time now so it matches the current sensorTime register
        // reading.
        mTask.timesync_rtc_time = sensorGetTime();
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask, __FUNCTION__);
    } else {
        mTask.pending_time_sync = true;
    }
}

static void processPendingEvt(void)
{
    TDECL();
    enum SensorIndex i;
    if (mTask.pending_int[0]) {
        mTask.pending_int[0] = false;
        initiateFifoRead(false /*isInterruptContext*/);
        return;
    }
    if (mTask.pending_int[1]) {
        mTask.pending_int[1] = false;
        int2Evt();
        return;
    }
    if (mTask.pending_time_sync) {
        mTask.pending_time_sync = false;
        timeSyncEvt(0, false);
        return;
    }
    for (i = FIRST_CONT_SENSOR; i < NUM_OF_SENSOR; i++) {
        if (mTask.pending_config[i]) {
            mTask.pending_config[i] = false;
            configEvent(&mTask.sensors[i], &mTask.sensors[i].pConfig);
            return;
        }
    }
    if (mTask.sensors[STEPCNT].flush > 0 || T(pending_step_cnt)) {
        T(pending_step_cnt) = T(pending_step_cnt) && !stepCntFlushGetData();
        return;
    }
    if (mTask.pending_calibration_save) {
        mTask.pending_calibration_save = !saveCalibration();
        return;
    }

#ifdef OVERTEMPCAL_ENABLED
    // tasks that do not initiate SPI transaction
    if (T(otcGyroUpdateBuffer).sendToHostRequest) {
        sendOtcGyroUpdate();
    }
#endif
}

static void sensorInit(void)
{
    TDECL();
    switch (mTask.init_state) {
    case RESET_BMI160:
        DEBUG_PRINT("Performing soft reset\n");
        // perform soft reset and wait for 100ms
        SPI_WRITE(BMI160_REG_CMD, 0xb6, 100000);
        // dummy reads after soft reset, wait 100us
        SPI_READ(BMI160_REG_MAGIC, 1, &mTask.dataBuffer, 100);

        mTask.init_state = INIT_BMI160;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask, "sensorInit RESET" );
        break;

    case INIT_BMI160:
        // Read any pending interrupts to reset them
        SPI_READ(BMI160_REG_INT_STATUS_0, 4, &mTask.statusBuffer);

        // disable accel, gyro and mag data in FIFO, enable header, enable time.
        SPI_WRITE(BMI160_REG_FIFO_CONFIG_1, 0x12, 450);

        // set the watermark to 24 byte
        SPI_WRITE(BMI160_REG_FIFO_CONFIG_0, 0x06, 450);

        // FIFO watermark and fifo_full interrupt enabled
        SPI_WRITE(BMI160_REG_INT_EN_0, 0x00, 450);
        SPI_WRITE(BMI160_REG_INT_EN_1, 0x60, 450);
        SPI_WRITE(BMI160_REG_INT_EN_2, 0x00, 450);

        // INT1, INT2 enabled, high-edge (push-pull) triggered.
        SPI_WRITE(BMI160_REG_INT_OUT_CTRL, 0xbb, 450);

        // INT1, INT2 input disabled, interrupt mode: non-latched
        SPI_WRITE(BMI160_REG_INT_LATCH, 0x00, 450);

        // Map data interrupts (e.g., FIFO) to INT1 and physical
        // interrupts (e.g., any motion) to INT2
        SPI_WRITE(BMI160_REG_INT_MAP_0, 0x00, 450);
        SPI_WRITE(BMI160_REG_INT_MAP_1, 0xE1, 450);
        SPI_WRITE(BMI160_REG_INT_MAP_2, 0xFF, 450);

        // Use pre-filtered data for tap interrupt
        SPI_WRITE(BMI160_REG_INT_DATA_0, 0x08);

        // Disable PMU_TRIGGER
        SPI_WRITE(BMI160_REG_PMU_TRIGGER, 0x00, 450);

        // tell gyro and accel to NOT use the FOC offset.
        mTask.sensors[ACC].offset_enable = false;
        mTask.sensors[GYR].offset_enable = false;
        SPI_WRITE(BMI160_REG_OFFSET_6, offset6Mode(), 450);

        // initial range for accel and gyro (+-1000 degree).
        SPI_WRITE(BMI160_REG_ACC_RANGE, ACC_RANGE_SETTING, 450);
        SPI_WRITE(BMI160_REG_GYR_RANGE, 0x01, 450);

        // Reset step counter
        SPI_WRITE(BMI160_REG_CMD, 0xB2, 10000);
        // Reset interrupt
        SPI_WRITE(BMI160_REG_CMD, 0xB1, 10000);
        // Reset fifo
        SPI_WRITE(BMI160_REG_CMD, 0xB0, 10000);

#ifdef MAG_SLAVE_PRESENT
        mTask.init_state = INIT_MAG;
        mTask.mag_state = MAG_SET_START;
#else
        // no mag connected to secondary interface
        mTask.init_state = INIT_ON_CHANGE_SENSORS;
#endif
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask, "sensorInit INIT");
        break;

    case INIT_MAG:
        // Don't check statusBuffer if we are just starting mag config
        if (mTask.mag_state == MAG_SET_START) {
            T(mRetryLeft) = RETRY_CNT_MAG;
            magConfig();
        } else if (mTask.mag_state < MAG_SET_DATA && mTask.statusBuffer[1] & 0x04) {
            // fixme: poll_until to reduce states
            // fixme: check should be done before SPI_READ in MAG_READ
            SPI_READ(BMI160_REG_STATUS, 1, &mTask.statusBuffer, 1000);
            if (--T(mRetryLeft) == 0) {
                ERROR_PRINT("INIT_MAG failed\n");
                // fixme: duplicate suspend mag here
                mTask.mag_state = MAG_INIT_FAILED;
                mTask.init_state = INIT_ON_CHANGE_SENSORS;
            }
        } else {
            T(mRetryLeft) = RETRY_CNT_MAG;
            magConfig();
        }

        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask, "sensorInit INIT_MAG");
        break;

    case INIT_ON_CHANGE_SENSORS:
        // configure any_motion and no_motion for 50Hz accel samples
        configMotion(MOTION_ODR);

        // select no_motion over slow_motion
        // select any_motion over significant motion
        SPI_WRITE(BMI160_REG_INT_MOTION_3, 0x15, 450);

        // int_tap_quiet=30ms, int_tap_shock=75ms, int_tap_dur=150ms
        SPI_WRITE(BMI160_REG_INT_TAP_0, 0x42, 450);

        // int_tap_th = 7 * 250 mg (8-g range)
        SPI_WRITE(BMI160_REG_INT_TAP_1, TAP_THRESHOLD, 450);

        // config step detector
#ifdef BMI160_STEP_COUNT_MODE_SENSITIVE
        SPI_WRITE(BMI160_REG_STEP_CONF_0, 0x2D, 450);
        SPI_WRITE(BMI160_REG_STEP_CONF_1, 0x02, 450);
#else
        SPI_WRITE(BMI160_REG_STEP_CONF_0, 0x15, 450);
        SPI_WRITE(BMI160_REG_STEP_CONF_1, 0x03, 450);
#endif

        // int_flat_theta = 44.8 deg * (16/64) = 11.2 deg
        SPI_WRITE(BMI160_REG_INT_FLAT_0, 0x10, 450);

        // int_flat_hold_time = (640 msec)
        // int_flat_hy = 44.8 * 4 / 64 = 2.8 deg
        SPI_WRITE(BMI160_REG_INT_FLAT_1, 0x14, 450);

        mTask.init_state = INIT_DONE;
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask, "sensorInit INIT_ONC");
        break;

    default:
        INFO_PRINT("Invalid init_state.\n");
    }
}

static void handleSpiDoneEvt(const void* evtData)
{
    TDECL();
    struct BMI160Sensor *mSensor;
    uint64_t SensorTime;
    int16_t temperature16;
    int i;
    bool returnIdle = false;

    switch (GET_STATE()) {
    case SENSOR_BOOT:
        SET_STATE(SENSOR_VERIFY_ID);
        // dummy reads after boot, wait 100us
        SPI_READ(BMI160_REG_MAGIC, 1, &mTask.statusBuffer, 100);
        // read the device ID for bmi160
        SPI_READ(BMI160_REG_ID, 1, &mTask.dataBuffer);
        spiBatchTxRx(&mTask.mode, sensorSpiCallback, &mTask, "spiDone SENSOR_BOOT");
        break;
    case SENSOR_VERIFY_ID:
        if (mTask.dataBuffer[1] != BMI160_ID) {
            T(mRetryLeft) --;
            ERROR_PRINT("failed id match: %02x\n", mTask.dataBuffer[1]);
            if (T(mRetryLeft) == 0)
                break;
            // For some reason the first ID read will fail to get the
            // correct value. need to retry a few times.
            SET_STATE(SENSOR_BOOT);
            if (timTimerSet(100000000, 100, 100, sensorTimerCallback, NULL, true) == 0)
                ERROR_PRINT("Couldn't get a timer to verify ID\n");
            break;
        } else {
            INFO_PRINT("detected\n");
            SET_STATE(SENSOR_INITIALIZING);
            mTask.init_state = RESET_BMI160;
            sensorInit();
            break;
        }
    case SENSOR_INITIALIZING:
        if (mTask.init_state == INIT_DONE) {
            DEBUG_PRINT("Done initialzing, system IDLE\n");
            for (i=0; i<NUM_OF_SENSOR; i++)
                sensorRegisterInitComplete(mTask.sensors[i].handle);
            // In case other tasks have already requested us before we finish booting up.
            returnIdle = true;
        } else {
            sensorInit();
        }
        break;
    case SENSOR_POWERING_UP:
        mSensor = (struct BMI160Sensor *)evtData;
        if (mSensor->idx >= FIRST_ONESHOT_SENSOR && ++mTask.active_oneshot_sensor_cnt == 1) {
            // if this is the first one-shot sensor to enable, we need
            // to request the accel at 50Hz.
            sensorRequest(mTask.tid, mTask.sensors[ACC].handle, SENSOR_HZ(50), SENSOR_LATENCY_NODATA);
            //DEBUG_PRINT("oneshot on\n");
        }
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 1, 0);
        returnIdle = true;
        break;
    case SENSOR_POWERING_DOWN:
        mSensor = (struct BMI160Sensor *)evtData;
        if (mSensor->idx >= FIRST_ONESHOT_SENSOR && --mTask.active_oneshot_sensor_cnt == 0) {
            // if this is the last one-shot sensor to disable, we need to
            // release the accel.
            sensorRelease(mTask.tid, mTask.sensors[ACC].handle);
            //DEBUG_PRINT("oneshot off\n");
        }
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 0, 0);

        if (mTask.pending_dispatch) {
            mTask.pending_dispatch = false;
            dispatchData();
        }
        returnIdle = true;
        break;
    case SENSOR_INT_1_HANDLING:
        dispatchData();
        sendFlushEvt();
        returnIdle = true;
        break;
    case SENSOR_INT_2_HANDLING:
        int2Handling();
        returnIdle = true;
        break;
    case SENSOR_CONFIG_CHANGING:
        mSensor = (struct BMI160Sensor *)evtData;
        sensorSignalInternalEvt(mSensor->handle,
                SENSOR_INTERNAL_EVT_RATE_CHG, mSensor->rate, mSensor->latency);

        if (mTask.pending_dispatch) {
            mTask.pending_dispatch = false;
            dispatchData();
        }

        returnIdle = true;
        break;
    case SENSOR_CALIBRATING:
        mSensor = (struct BMI160Sensor *)evtData;
        if (mTask.calibration_state == CALIBRATION_DONE) {
            DEBUG_PRINT("DONE calibration\n");
            returnIdle = true;
        } else if (mTask.calibration_state == CALIBRATION_TIMEOUT) {
            DEBUG_PRINT("Calibration TIMED OUT\n");
            sendCalibrationResult(SENSOR_APP_EVT_STATUS_ERROR,
                    (mSensor->idx == ACC) ? SENS_TYPE_ACCEL : SENS_TYPE_GYRO, 0, 0, 0);
            returnIdle = true;
        } else if (mSensor->idx == ACC) {
            accCalibrationHandling();
        } else if (mSensor->idx == GYR) {
            gyrCalibrationHandling();
        }
        break;
    case SENSOR_TESTING:
        mSensor = (struct BMI160Sensor *)evtData;
        if (mSensor->idx == ACC) {
            if (mTask.acc_test_state == ACC_TEST_DONE) {
                returnIdle = true;
            } else {
                accTestHandling();
            }
        } else if (mSensor->idx == GYR) {
            if (mTask.gyro_test_state == GYRO_TEST_DONE) {
                returnIdle = true;
            } else {
                gyroTestHandling();
            }
        }
        break;
    case SENSOR_STEP_CNT:
        sendStepCnt();
        returnIdle = true;
        break;
    case SENSOR_TIME_SYNC:
        SensorTime = parseSensortime(mTask.sensorTimeBuffer[1] |
                (mTask.sensorTimeBuffer[2] << 8) | (mTask.sensorTimeBuffer[3] << 16));
        map_sensortime_to_rtc_time(SensorTime, mTask.timesync_rtc_time);

        temperature16 = (mTask.temperatureBuffer[1] | (mTask.temperatureBuffer[2] << 8));
        if (temperature16 == 0x8000) {
            mTask.tempCelsius = kTempInvalid;
        } else {
            mTask.tempCelsius = 23.0f + temperature16 * kScale_temp;
            mTask.tempTime = sensorGetTime();
        }

        if (mTask.active_poll_generation == mTask.poll_generation) {
            // attach the generation number to event
            if (timTimerSet(kTimeSyncPeriodNs, 100, 100, timeSyncCallback,
                    (void *)mTask.poll_generation, true) == 0)
                ERROR_PRINT("Couldn't get a timer for time sync\n");
        }

        returnIdle = true;
        break;
    case SENSOR_SAVE_CALIBRATION:
        DEBUG_PRINT("SENSOR_SAVE_CALIBRATION: %02x %02x %02x %02x %02x %02x %02x\n",
                mTask.dataBuffer[1], mTask.dataBuffer[2], mTask.dataBuffer[3], mTask.dataBuffer[4],
                mTask.dataBuffer[5], mTask.dataBuffer[6], mTask.dataBuffer[7]);
        returnIdle = true;
        break;
    default:
        break;
    }

    if (returnIdle) {
        SET_STATE(SENSOR_IDLE);
        processPendingEvt();
    }
}

#ifdef BMI160_USE_I2C
static void i2cCallback(void *cookie, size_t tx, size_t rx, int err);

/* delayed callback */
static void i2cDelayCallback(uint32_t timerId, void *data)
{
    i2cCallback(data, 0, 0, 0);
}

static void i2cCallback(void *cookie, size_t tx, size_t rx, int err)
{
    TDECL();
    uint8_t reg = T(cReg) - 1;
    uint32_t delay;

    if (err != 0) {
        ERROR_PRINT("i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
    } else { /* delay callback if it is the case */
        delay = T(packets[reg]).delay;
        T(packets[reg]).delay = 0;
        if (delay > 0) {
            if (timTimerSet(delay, 0, 50, i2cDelayCallback, cookie, true))
                return;
            ERROR_PRINT("Cannot do delayed i2cCallback\n");
            err = -ENOMEM;
        }
    }
    i2cBatchTxRx(cookie, err);
}

static void i2cBatchTxRx(void *evtData, int err)
{
    TDECL();
    uint8_t *txBuf;
    uint8_t *rxBuf;
    uint16_t size;
    uint8_t reg;

    reg = T(cReg)++;
    if (err || (reg >= T(mRegCnt))) // No more packets
        goto i2c_batch_end;

    // Setup i2c op for next packet
    txBuf = (uint8_t *)T(packets[reg]).txBuf;
    size = T(packets[reg]).size;
    if (txBuf[0] & BMI160_SPI_READ) { // Read op
        rxBuf = (uint8_t *)T(packets[reg]).rxBuf + 1;
        size--;
        err = i2cMasterTxRx(BMI160_I2C_BUS_ID, BMI160_I2C_ADDR, txBuf, 1, rxBuf, size, i2cCallback, evtData);
    } else { // Write op
        err = i2cMasterTx(BMI160_I2C_BUS_ID, BMI160_I2C_ADDR, txBuf, size, i2cCallback, evtData);
    }
    if (!err)
        return;
    ERROR_PRINT("%s: [0x%x] (err: %d)\n", __func__, txBuf[0], err);

i2c_batch_end:
    T(mRegCnt) = 0;
    if (T(sCallback))
        T(sCallback)((void *)evtData, err);
}
#endif

static void handleEvent(uint32_t evtType, const void* evtData)
{
    TDECL();
    uint64_t currTime;
    uint8_t *packet;
    float newMagBias;

    switch (evtType) {
    case EVT_APP_START:
        SET_STATE(SENSOR_BOOT);
        T(mRetryLeft) = RETRY_CNT_ID;
        osEventUnsubscribe(mTask.tid, EVT_APP_START);

        // wait 100ms for sensor to boot
        currTime = timGetTime();
        if (currTime < 100000000ULL) {
            if (timTimerSet(100000000 - currTime, 100, 100, sensorTimerCallback, NULL, true) == 0)
                ERROR_PRINT("Couldn't get a timer for boot delay\n");
            break;
        }
        /* We have already been powered on long enough - fall through */
    case EVT_SPI_DONE:
        handleSpiDoneEvt(evtData);
        break;

    case EVT_APP_FROM_HOST:
        packet = (uint8_t*)evtData;
        if (packet[0] == sizeof(float)) {
            memcpy(&newMagBias, packet+1, sizeof(float));
#ifdef MAG_SLAVE_PRESENT
            magCalAddBias(&mTask.moc, (mTask.last_charging_bias_x - newMagBias), 0.0, 0.0);
#endif
            mTask.last_charging_bias_x = newMagBias;
            mTask.magBiasPosted = false;
        }
        break;

    case EVT_SENSOR_INTERRUPT_1:
        initiateFifoRead(false /*isInterruptContext*/);
        break;
    case EVT_SENSOR_INTERRUPT_2:
        int2Evt();
        break;
    case EVT_TIME_SYNC:
        timeSyncEvt((uint32_t)evtData, true);
    default:
        break;
    }
}

static void initSensorStruct(struct BMI160Sensor *sensor, enum SensorIndex idx)
{
    sensor->idx = idx;
    sensor->powered = false;
    sensor->configed = false;
    sensor->rate = 0;
    sensor->offset[0] = 0;
    sensor->offset[1] = 0;
    sensor->offset[2] = 0;
    sensor->latency = 0;
    sensor->data_evt = NULL;
    sensor->flush = 0;
    sensor->prev_rtc_time = 0;
}

static bool startTask(uint32_t task_id)
{
    TDECL();
    enum SensorIndex i;
    size_t slabSize;

    time_init();

    T(tid) = task_id;

    T(Int1) = gpioRequest(BMI160_INT1_PIN);
    T(Irq1) = BMI160_INT1_IRQ;
    T(Isr1).func = bmi160Isr1;
    T(Int2) = gpioRequest(BMI160_INT2_PIN);
    T(Irq2) = BMI160_INT2_IRQ;
    T(Isr2).func = bmi160Isr2;
    T(pending_int[0]) = false;
    T(pending_int[1]) = false;
    T(pending_step_cnt) = false;
    T(pending_dispatch) = false;
    T(frame_sensortime_valid) = false;
    T(poll_generation) = 0;
    T(tempCelsius) = kTempInvalid;
    T(tempTime) = 0;

    T(mode).speed = BMI160_SPI_SPEED_HZ;
    T(mode).bitsPerWord = 8;
    T(mode).cpol = SPI_CPOL_IDLE_HI;
    T(mode).cpha = SPI_CPHA_TRAILING_EDGE;
    T(mode).nssChange = true;
    T(mode).format = SPI_FORMAT_MSB_FIRST;
    T(cs) = GPIO_PB(12);

    T(watermark) = 0;

#ifdef BMI160_USE_I2C
    i2cMasterRequest(BMI160_I2C_BUS_ID, BMI160_I2C_SPEED);
#else
    spiMasterRequest(BMI160_SPI_BUS_ID, &T(spiDev));
#endif

    for (i = FIRST_CONT_SENSOR; i < NUM_OF_SENSOR; i++) {
        initSensorStruct(&T(sensors[i]), i);
        T(sensors[i]).handle = sensorRegister(&mSensorInfo[i], &mSensorOps[i], NULL, false);
        T(pending_config[i]) = false;
    }

    osEventSubscribe(mTask.tid, EVT_APP_START);

#ifdef ACCEL_CAL_ENABLED
    // Init Accel Cal
    accelCalInit(&mTask.acc,
                 800000000, /* Stillness Time in ns (0.8s) */
                 5,         /* Minimum Sample Number */
                 0.00025,   /* Threshold */
                 15,        /* nx bucket count */
                 15,        /* nxb bucket count */
                 15,        /* ny bucket count */
                 15,        /* nyb bucket count */
                 15,        /* nz bucket count */
                 15,        /* nzb bucket count */
                 15);       /* nle bucket count */
#endif

#ifdef GYRO_CAL_ENABLED
    // Gyro Cal -- Initialization.
    gyroCalInit(&mTask.gyro_cal,
                SEC_TO_NANOS(5.0f),   // Min stillness period = 5.0 seconds
                SEC_TO_NANOS(5.9f),   // Max stillness period = 6.0 seconds (NOTE 1)
                0, 0, 0,              // Initial bias offset calibration
                0,                    // Time stamp of initial bias calibration
                SEC_TO_NANOS(1.5f),   // Analysis window length = 1.5 seconds
                7.5e-5f,              // Gyroscope variance threshold [rad/sec]^2
                1.5e-5f,              // Gyroscope confidence delta [rad/sec]^2
                4.5e-3f,              // Accelerometer variance threshold [m/sec^2]^2
                9.0e-4f,              // Accelerometer confidence delta [m/sec^2]^2
                5.0f,                 // Magnetometer variance threshold [uT]^2
                1.0f,                 // Magnetometer confidence delta [uT]^2
                0.95f,                // Stillness threshold [0,1]
                40.0f * MDEG_TO_RAD,  // Stillness mean variation limit [rad/sec]
                1.5f,                 // Max temperature delta during stillness [C]
                true);                // Gyro calibration enable
    // NOTE 1: This parameter is set to 5.9 seconds to achieve a max stillness
    // period of 6.0 seconds and avoid buffer boundary conditions that could push
    // the max stillness to the next multiple of the analysis window length
    // (i.e., 7.5 seconds).

#ifdef OVERTEMPCAL_ENABLED
    // Initialize over-temp calibration.
    overTempCalInit(&mTask.over_temp_gyro_cal,
                    5,                     // Min num of points to enable model update
                    SEC_TO_NANOS(0.5f),    // Min temperature update interval [nsec]
                    0.75f,                 // Temperature span of bin method [C]
                    40.0f * MDEG_TO_RAD,   // Jump tolerance [rad/sec]
                    50.0f * MDEG_TO_RAD,   // Outlier rejection tolerance [rad/sec]
                    DAYS_TO_NANOS(2),      // Model data point age limit [nsec]
                    80.0f * MDEG_TO_RAD,   // Limit for temp. sensitivity [rad/sec/C]
                    3.0e3f * MDEG_TO_RAD,  // Limit for model intercept parameter [rad/sec]
                    0.1f * MDEG_TO_RAD,    // Significant offset change [rad/sec]
                    true);                 // Over-temp compensation enable
#endif  // OVERTEMPCAL_ENABLED
#endif  // GYRO_CAL_ENABLED

#ifdef MAG_SLAVE_PRESENT
#ifdef DIVERSITY_CHECK_ENABLED
    initMagCal(&mTask.moc,
               0.0f, 0.0f, 0.0f,   // bias x, y, z
               1.0f, 0.0f, 0.0f,   // c00, c01, c02
               0.0f, 1.0f, 0.0f,   // c10, c11, c12
               0.0f, 0.0f, 1.0f,   // c20, c21, c22
               3000000,            // min_batch_window_in_micros
               8,                  // min_num_diverse_vectors
               1,                  // max_num_max_distance
               6.0f,               // var_threshold
               10.0f,              // max_min_threshold
               48.f,               // local_field
               0.5f,               // threshold_tuning_param
               2.552f);            // max_distance_tuning_param
#else
    initMagCal(&mTask.moc,
               0.0f, 0.0f, 0.0f,   // bias x, y, z
               1.0f, 0.0f, 0.0f,   // c00, c01, c02
               0.0f, 1.0f, 0.0f,   // c10, c11, c12
               0.0f, 0.0f, 1.0f,   // c20, c21, c22
               3000000);           // min_batch_window_in_micros
#endif
#endif

    slabSize = sizeof(struct TripleAxisDataEvent) +
               MAX_NUM_COMMS_EVENT_SAMPLES * sizeof(struct TripleAxisDataPoint);

    // each event has 15 samples, with 7 bytes per sample from the fifo.
    // the fifo size is 1K.
    // 20 slabs because some slabs may only hold 1-2 samples.
    // XXX: this consumes too much memeory, need to optimize
    T(mDataSlab) = slabAllocatorNew(slabSize, 4, 20);
    if (!T(mDataSlab)) {
        ERROR_PRINT("slabAllocatorNew() failed\n");
        return false;
    }
    T(mWbufCnt) = 0;
    T(mRegCnt) = 0;
#ifdef BMI160_USE_I2C
    T(cReg) = 0;
#endif
    T(spiInUse) = false;

    T(interrupt_enable_0) = 0x00;
    T(interrupt_enable_2) = 0x00;

    // initialize the last bmi160 time to be ULONG_MAX, so that we know it's
    // not valid yet.
    T(last_sensortime) = 0;
    T(frame_sensortime) = ULONG_LONG_MAX;

    // it's ok to leave interrupt open all the time.
    enableInterrupt(T(Int1), T(Irq1), &T(Isr1));
    enableInterrupt(T(Int2), T(Irq2), &T(Isr2));

    return true;
}

static void endTask(void)
{
    TDECL();
#ifdef MAG_SLAVE_PRESENT
    magCalDestroy(&mTask.moc);
#endif
#ifdef ACCEL_CAL_ENABLED
    accelCalDestroy(&mTask.acc);
#endif
    slabAllocatorDestroy(T(mDataSlab));
#ifndef BMI160_USE_I2C
    spiMasterRelease(mTask.spiDev);
#endif

    // disable and release interrupt.
    disableInterrupt(mTask.Int1, mTask.Irq1, &mTask.Isr1);
    disableInterrupt(mTask.Int2, mTask.Irq2, &mTask.Isr2);
    gpioRelease(mTask.Int1);
    gpioRelease(mTask.Int2);
}

/**
 * Parse BMI160 FIFO frame without side effect.
 *
 * The major purpose of this function is to determine if FIFO content is received completely (start
 * to see invalid headers). If not, return the pointer to the beginning last incomplete frame so
 * additional read can use this pointer as start of read buffer.
 *
 * @param buf  buffer location
 * @param size size of data to be parsed
 *
 * @return NULL if the FIFO is received completely; or pointer to the beginning of last incomplete
 * frame for additional read.
 */
static uint8_t* shallowParseFrame(uint8_t * buf, int size) {
    int i = 0;
    int iLastFrame = 0; // last valid frame header index

    DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "spf start %p: %x %x %x\n", buf, buf[0], buf[1], buf[2]);
    while (size > 0) {
        int fh_mode, fh_param;
        iLastFrame = i;

        if (buf[i] == BMI160_FRAME_HEADER_INVALID) {
            // no more data
            DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "spf:at%d=0x80\n", iLastFrame);
            return NULL;
        } else if (buf[i] == BMI160_FRAME_HEADER_SKIP) {
            // artifically added nop frame header, skip
            DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "at %d, skip header\n", i);
            i++;
            size--;
            continue;
        }

        //++frame_num;

        fh_mode = buf[i] >> 6;
        fh_param = (buf[i] >> 2) & 0xf;

        i++;
        size--;

        if (fh_mode == 1) {
            // control frame.
            if (fh_param == 0) {
                // skip frame, we skip it (1 byte)
                i++;
                size--;
                DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "at %d, a skip frame\n", iLastFrame);
            } else if (fh_param == 1) {
                // sensortime frame  (3 bytes)
                i += 3;
                size -= 3;
                DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "at %d, a sensor_time frame\n", iLastFrame);
            } else if (fh_param == 2) {
                // fifo_input config frame (1byte)
                i++;
                size--;
                DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "at %d, a fifo cfg frame\n", iLastFrame);
            } else {
                size = 0; // drop this batch
                DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "Invalid fh_param in control frame!!\n");
                // mark invalid
                buf[iLastFrame] = BMI160_FRAME_HEADER_INVALID;
                return NULL;
            }
        } else if (fh_mode == 2) {
            // regular frame, dispatch data to each sensor's own fifo
            if (fh_param & 4) { // have mag data
                i += 8;
                size -= 8;
            }
            if (fh_param & 2) { // have gyro data
                i += 6;
                size -= 6;
            }
            if (fh_param & 1) { // have accel data
                i += 6;
                size -= 6;
            }
            DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "at %d, a reg frame acc %d, gyro %d, mag %d\n",
                       iLastFrame, fh_param &1 ? 1:0, fh_param&2?1:0, fh_param&4?1:0);
        } else {
            size = 0; // drop the rest of batch
            DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "spf: Invalid fh_mode %d!!\n", fh_mode);
            //mark invalid
            buf[iLastFrame] = BMI160_FRAME_HEADER_INVALID;
            return NULL;
        }
    }

    // there is a partial frame, return where to write next chunck of data
    DEBUG_PRINT_IF(DBG_SHALLOW_PARSE, "partial frame ends %p\n", buf + iLastFrame);
    return buf + iLastFrame;
}

/**
 * Intialize the first read of chunked SPI read sequence.
 *
 * @param index starting index of the txrxBuffer in which the data will be write into.
 */
static void chunkedReadInit_(TASK, int index, int size) {

    if (GET_STATE() != SENSOR_INT_1_HANDLING) {
        ERROR_PRINT("chunkedReadInit in wrong mode");
        return;
    }

    if (T(mRegCnt)) {
        //chunked read are always executed as a single command. This should never happen.
        ERROR_PRINT("SPI queue not empty at chunkedReadInit, regcnt = %d", T(mRegCnt));
        // In case it did happen, we do not want to write crap to BMI160.
        T(mRegCnt) = 0;
    }

    T(mWbufCnt) = index;
    if (T(mWbufCnt) > FIFO_READ_SIZE) {
        // drop data to prevent bigger issue
        T(mWbufCnt) = 0;
    }
    T(chunkReadSize) = size > CHUNKED_READ_SIZE ? size : CHUNKED_READ_SIZE;

    DEBUG_PRINT_IF(DBG_CHUNKED, "crd %d>>%d\n", T(chunkReadSize), index);
    SPI_READ(BMI160_REG_FIFO_DATA, T(chunkReadSize), &T(dataBuffer));
    spiBatchTxRx(&T(mode), chunkedReadSpiCallback, _task, __FUNCTION__);
}

/**
 * Chunked SPI read callback.
 *
 * Handles the chunked read logic: issue additional read if necessary, or calls sensorSpiCallback()
 * if the entire FIFO is read.
 *
 * @param cookie extra data
 * @param err    error
 *
 * @see sensorSpiCallback()
 */
static void chunkedReadSpiCallback(void *cookie, int err) {
    TASK = (_Task*) cookie;

    T(spiInUse) = false;
    DEBUG_PRINT_IF(err !=0 || GET_STATE() != SENSOR_INT_1_HANDLING,
            "crcb,e:%d,s:%d", err, (int)GET_STATE());
    bool int1 = gpioGet(T(Int1));
    if (err != 0) {
        DEBUG_PRINT_IF(DBG_CHUNKED, "spi err, crd retry");
        // read full fifo length to be safe
        chunkedReadInit(0, FIFO_READ_SIZE);
        return;
    }

    *T(dataBuffer) = BMI160_FRAME_HEADER_SKIP; // fill the 0x00/0xff hole at the first byte
    uint8_t* end = shallowParseFrame(T(dataBuffer), T(chunkReadSize));

    if (end == NULL) {
        // if interrupt is still set after read for some reason, set the pending interrupt
        // to handle it immediately after data is handled.
        T(pending_int[0]) = T(pending_int[0]) || int1;

        // recover the buffer and valid data size to make it looks like a single read so that
        // real frame parse works properly
        T(dataBuffer) = T(txrxBuffer);
        T(xferCnt) = FIFO_READ_SIZE;
        sensorSpiCallback(cookie, err);
    } else {
        DEBUG_PRINT_IF(DBG_CHUNKED, "crd cont");
        chunkedReadInit(end - T(txrxBuffer), CHUNKED_READ_SIZE);
    }
}

/**
 * Initiate read of sensor fifo.
 *
 * If task is in idle state, init chunked FIFO read; otherwise, submit an interrupt message or mark
 * the read pending depending if it is called in interrupt context.
 *
 * @param isInterruptContext true if called from interrupt context; false otherwise.
 *
 */
static void initiateFifoRead_(TASK, bool isInterruptContext) {
    if (trySwitchState(SENSOR_INT_1_HANDLING)) {
        // estimate first read size to be watermark + 1 more sample + some extra
        int firstReadSize = T(watermark) * 4 + 32; // 1+6+6+8+1+3 + extra = 25 + extra = 32
        if (firstReadSize < CHUNKED_READ_SIZE) {
            firstReadSize = CHUNKED_READ_SIZE;
        }
        chunkedReadInit(0, firstReadSize);
    } else {
        if (isInterruptContext) {
            // called from interrupt context, queue event
            if (!osEnqueuePrivateEvt(EVT_SENSOR_INTERRUPT_1, _task, NULL, T(tid)))
                ERROR_PRINT("initiateFifoRead_: osEnqueuePrivateEvt() failed\n");
        } else {
            // non-interrupt context, set pending flag, so next time it will be picked up after
            // switching back to idle.
            // Note: even if we are still in SENSOR_INT_1_HANDLING, the SPI may already finished and
            // we need to issue another SPI read to get the latest status.
            T(pending_int[0]) = true;
        }
    }
}

/**
 * Calculate fifo size using normalized input.
 *
 * @param iPeriod normalized period vector
 * @param iLatency normalized latency vector
 * @param factor vector that contains size factor for each sensor
 * @param n size of the vectors
 *
 * @return max size of FIFO to guarantee latency requirements of all sensors or SIZE_MAX if no
 * sensor is active.
 */
static size_t calcFifoSize(const int* iPeriod, const int* iLatency, const int* factor, int n) {
    int i;

    int minLatency = INT_MAX;
    for (i = 0; i < n; i++) {
        if (iLatency[i] > 0) {
            minLatency = iLatency[i] < minLatency ? iLatency[i] : minLatency;
        }
    }
    DEBUG_PRINT_IF(DBG_WM_CALC, "cfifo: min latency %d unit", minLatency);

    bool anyActive = false;
    size_t s = 0;
    size_t head = 0;
    for (i = 0; i < n; i++) {
        if (iPeriod[i] > 0) {
            anyActive = true;
            size_t t = minLatency / iPeriod[i];
            head = t > head ? t : head;
            s += t * factor[i];
            DEBUG_PRINT_IF(DBG_WM_CALC, "cfifo %d: s += %d * %d, head = %d", i, t, factor[i], head);
        }
    }

    return anyActive ? head + s : SIZE_MAX;
}

/**
 * Calculate the watermark setting from sensor registration information
 *
 * It is assumed that all sensor periods share a common denominator (true for BMI160) and the
 * latency of sensor will be lower bounded by its sampling period.
 *
 * @return watermark register setting
 */
static uint8_t calcWatermark2_(TASK) {
    int period[] = {-1, -1, -1};
    int latency[] = {-1, -1, -1};
    const int factor[] = {6, 6, 8};
    int i;

    for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; ++i) {
        if (T(sensors[i]).configed && T(sensors[i]).latency != SENSOR_LATENCY_NODATA) {
            period[i - ACC] = SENSOR_HZ((float)WATERMARK_MAX_SENSOR_RATE) / T(sensors[i]).rate;
            latency[i - ACC] = U64_DIV_BY_U64_CONSTANT(
                    T(sensors[i]).latency + WATERMARK_TIME_UNIT_NS/2, WATERMARK_TIME_UNIT_NS);
            DEBUG_PRINT_IF(DBG_WM_CALC, "cwm2 %d: f %dHz, l %dus => T %d unit, L %d unit",
                    i, (int) T(sensors[i]).rate/1024,
                    (int) U64_DIV_BY_U64_CONSTANT(T(sensors[i]).latency, 1000),
                    period[i-ACC], latency[i-ACC]);
        }
    }


    size_t watermark = calcFifoSize(period, latency, factor, NUM_CONT_SENSOR) / 4;
    DEBUG_PRINT_IF(DBG_WM_CALC, "cwm2: wm = %d", watermark);
    watermark = watermark < WATERMARK_MIN ? WATERMARK_MIN : watermark;
    watermark = watermark > WATERMARK_MAX ? WATERMARK_MAX : watermark;

    return watermark;
}

static bool dumpBinaryPutC(void* p, char c) {
    *(*(char**)p)++ = c;
    return true;
}

static uint32_t cvprintf_ellipsis(printf_write_c writeF, void* writeD, const char* fmtStr, ...) {
    va_list vl;
    uint32_t ret;

    va_start(vl, fmtStr);
    ret = cvprintf(writeF, 0, writeD, fmtStr, vl);
    va_end(vl);

    return ret;
}

static void dumpBinary(void* buf, unsigned int address, size_t size) {
    size_t i, j;
    char buffer[5+16*3+1+2]; //5: address, 3:each byte+space, 1: middle space, 1: \n and \0
    char* p;

    for (i = 0; i < size; ) {
        p = buffer;
        cvprintf_ellipsis(dumpBinaryPutC, &p, "%08x:", address);
        for (j = 0; j < 0x10 && i < size; ++i, ++j) {
            if (j == 0x8) {
                *p++ = ' ';
            }
            cvprintf_ellipsis(dumpBinaryPutC, &p, " %02x", ((unsigned char *)buf)[i]);
        }
        *p = '\0';

        osLog(LOG_INFO, "%s\n", buffer);
        address += 0x10;
    }
}

#ifdef OVERTEMPCAL_ENABLED
static void handleOtcGyroConfig_(TASK, const struct AppToSensorHalDataPayload *data) {
    const struct GyroOtcData *d = data->gyroOtcData;

    INFO_PRINT("gyrCfgData otc-data: off %d %d %d, t %d, s %d %d %d, i %d %d %d",
            (int)(d->lastOffset[0]), (int)(d->lastOffset[1]), (int)(d->lastOffset[2]),
            (int)(d->lastTemperature),
            (int)(d->sensitivity[0]), (int)(d->sensitivity[1]), (int)(d->sensitivity[2]),
            (int)(d->intercept[0]), (int)(d->intercept[1]), (int)(d->intercept[2]));

    overTempCalSetModel(&T(over_temp_gyro_cal), d->lastOffset, d->lastTemperature,
                        sensorGetTime(), d->sensitivity, d->intercept, true /*jumpstart*/);
}

static bool sendOtcGyroUpdate_(TASK) {
    int step = 0;
    if (atomicCmpXchgByte(&T(otcGyroUpdateBuffer).lock, false, true)) {
        ++step;
        //fill HostIntfDataBuffer header
        struct HostIntfDataBuffer *p = (struct HostIntfDataBuffer *)(&T(otcGyroUpdateBuffer));
        p->sensType = SENS_TYPE_INVALID;
        p->length = sizeof(struct AppToSensorHalDataPayload) + sizeof(struct GyroOtcData);
        p->dataType = HOSTINTF_DATA_TYPE_APP_TO_SENSOR_HAL;
        p->interrupt = NANOHUB_INT_NONWAKEUP;

        //fill AppToSensorHalDataPayload header
        struct AppToSensorHalDataBuffer *q = (struct AppToSensorHalDataBuffer *)p;
        q->payload.size = sizeof(struct GyroOtcData);
        q->payload.type = HALINTF_TYPE_GYRO_OTC_DATA; // bit-or EVENT_TYPE_BIT_DISCARDABLE
                                                      // to make it discardable

        // fill payload data
        struct GyroOtcData *data = q->payload.gyroOtcData;
        uint64_t timestamp;
        overTempCalGetModel(&T(over_temp_gyro_cal), data->lastOffset, &data->lastTemperature,
                            &timestamp, data->sensitivity, data->intercept);
        if (osEnqueueEvtOrFree(EVT_APP_TO_SENSOR_HAL_DATA, // bit-or EVENT_TYPE_BIT_DISCARDABLE
                                                          // to make event discardable
                               p, unlockOtcGyroUpdateBuffer)) {
            T(otcGyroUpdateBuffer).sendToHostRequest = false;
            ++step;
        }
    }
    DEBUG_PRINT("otc gyro update, finished at step %d", step);
    return step == 2;
}

static void unlockOtcGyroUpdateBuffer(void *event) {
    atomicXchgByte(&(((struct OtcGyroUpdateBuffer*)(event))->lock), false);
}
#endif // OVERTEMPCAL_ENABLED

INTERNAL_APP_INIT(BMI160_APP_ID, BMI160_APP_VERSION, startTask, endTask, handleEvent);
