/*
 * Copyright (C) 2016-2017 STMicroelectronics
 *
 * Author: Denis Ciocca <denis.ciocca@st.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdlib.h>
#include <string.h>
#include <sensors.h>
#include <slab.h>
#include <heap.h>
#include <spi.h>
#include <gpio.h>
#include <atomic.h>
#include <timer.h>
#include <printf.h>
#include <isr.h>
#include <hostIntf.h>
#include <nanohubPacket.h>
#include <cpu/cpuMath.h>
#include <variant/sensType.h>
#include <plat/gpio.h>
#include <plat/syscfg.h>
#include <plat/exti.h>
#include <plat/rtc.h>
#include <calibration/accelerometer/accel_cal.h>
#include <calibration/gyroscope/gyro_cal.h>
#include <calibration/magnetometer/mag_cal.h>
#include <calibration/over_temp/over_temp_cal.h>
#include <algos/time_sync.h>

#include "st_lsm6dsm_lis3mdl_slave.h"
#include "st_lsm6dsm_lsm303agr_slave.h"
#include "st_lsm6dsm_ak09916_slave.h"
#include "st_lsm6dsm_lps22hb_slave.h"

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) || defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#define LSM6DSM_I2C_MASTER_ENABLED                      1
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

#if defined(LSM6DSM_MAGN_CALIB_ENABLED) && !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
#pragma message("LSM6DSM_MAGN_CALIB_ENABLED can not be used if no magnetometer sensors are enabled on I2C master. Disabling it!")
#undef LSM6DSM_MAGN_CALIB_ENABLED
#endif /* LSM6DSM_MAGN_CALIB_ENABLED, LSM6DSM_I2C_MASTER_ENABLED */

#if defined(LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP) && !defined(LSM6DSM_I2C_MASTER_ENABLED)
#pragma message("LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP has no meaning if no sensors are enabled on I2C master. Discarding it!")
#endif /* LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP, LSM6DSM_I2C_MASTER_ENABLED */

#if defined(LSM6DSM_OVERTEMP_CALIB_ENABLED) && !defined(LSM6DSM_GYRO_CALIB_ENABLED)
#pragma message("LSM6DSM_OVERTEMP_CALIB_ENABLED has no meaning if gyro calibration is not enabled. Discarding it!")
#undef LSM6DSM_OVERTEMP_CALIB_ENABLED
#endif /* LSM6DSM_OVERTEMP_CALIB_ENABLED, LSM6DSM_GYRO_CALIB_ENABLED */

#if !defined(LSM6DSM_SPI_SLAVE_BUS_ID) || !defined(LSM6DSM_SPI_SLAVE_FREQUENCY_HZ) || !defined(LSM6DSM_SPI_SLAVE_CS_GPIO)
#error "SPI macros not fully defined. Please check README file"
#endif /* LSM6DSM_SPI_SLAVE_BUS_ID, LSM6DSM_SPI_SLAVE_FREQUENCY_HZ, LSM6DSM_SPI_SLAVE_CS_GPIO */

#if !defined(LSM6DSM_INT_IRQ) || !defined(LSM6DSM_INT1_GPIO)
#error "Interrupts macros not fully defined. Please check README file"
#endif /* LSM6DSM_INT_IRQ, LSM6DSM_INT1_GPIO */

#if !defined(LSM6DSM_ACCEL_GYRO_ROT_MATRIX)
#error "Accel/gyro rotation matrix macro not defined. Please check README file"
#endif /* LSM6DSM_ACCEL_GYRO_ROT_MATRIX */

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
#if !defined(LSM6DSM_MAGN_ROT_MATRIX)
#error "Magn rotation matrix macro not defined. Please check README file"
#endif /* LSM6DSM_MAGN_ROT_MATRIX */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#define LSM6DSM_APP_ID                                  APP_ID_MAKE(NANOHUB_VENDOR_STMICRO, 0)

#define LSM6DSM_WAI_VALUE                               (0x6a)
#define LSM6DSM_RETRY_CNT_WAI                           5               /* Retry #n times if WAI value is wrong. Maybe HW is not ready after power on */
#define LSM6DSM_ACCEL_KSCALE                            0.00239364f     /* Accel scale @8g in (m/s^2)/LSB */
#define LSM6DSM_GYRO_KSCALE                             0.00122173f     /* Gyro scale @2000dps in (rad/sec)/LSB */
#define LSM6DSM_ONE_SAMPLE_BYTE                         6               /* One sample of triaxial sensor is expressed on 6 byte */
#define LSM6DSM_TEMP_SAMPLE_BYTE                        2               /* One sample of temperature sensor is expressed on 2 byte */
#define LSM6DSM_TIMESTAMP_SAMPLE_BYTE                   3               /* One sample of timestamp is expressed on 3 byte */
#define LSM6DSM_TEMP_OFFSET                             (25.0f)
#define LSM6DSM_SC_DELTA_TIME_PERIOD_SEC                (1.6384f)       /* Step counter deltatime resolution */
#define LSM6DSM_MAX_NUM_COMMS_EVENT_SAMPLE              15
#define LSM6DSM_MAX_WATERMARK_VALUE                     600             /* 4096byte = 682 samples, use 600 to avoid overflow */
#define LSM6DSM_TIME_RESOLUTION                         25000UL         /* 25us [ns] */
#define LSM6DSM_MASK_24BIT_TIMESTAMP                    0x00ffffff      /* mask to select 24bit data from 32bit storage data type */
#define LSM6DSM_TIMEDIFF_OVERFLOW_LSB                   8388608LL       /* If deltatime is bigger than 2^23 it means timer is overflowed */
#define LSM6DSM_SYNC_DELTA_INTERVAL                     100000000ULL    /* Sensor timestamp is synced with MCU every #n deltatime [ns] */
#define LSM6DSM_TRIAXIAL_NUM_AXIS                       3

/* SPI buffers */
#define LSM6DSM_SPI_PACKET_SIZE                         75
#define LSM6DSM_SPI_FIFO_SIZE                           1024
#define LSM6DSM_BUF_MARGIN                              100
#define SPI_BUF_SIZE                                    (LSM6DSM_SPI_FIFO_SIZE + LSM6DSM_BUF_MARGIN)

/* LSM6DSM status check registers */
#define LSM6DSM_FUNC_SRC_STEP_DETECTED                  (0x10)
#define LSM6DSM_FUNC_SRC_STEP_COUNT_DELTA_IA            (0x80)
#define LSM6DSM_FUNC_SRC_SIGN_MOTION                    (0x40)
#define LSM6DSM_FIFO_STATUS2_FIFO_EMPTY                 (0x10)
#define LSM6DSM_FIFO_STATUS2_FIFO_FULL_SMART            (0x20)
#define LSM6DSM_FIFO_STATUS2_FIFO_FULL_OVERRUN          (0x40)
#define LSM6DSM_FIFO_STATUS2_FIFO_ERROR                 (LSM6DSM_FIFO_STATUS2_FIFO_EMPTY | \
                                                         LSM6DSM_FIFO_STATUS2_FIFO_FULL_SMART | \
                                                         LSM6DSM_FIFO_STATUS2_FIFO_FULL_OVERRUN)

/* LSM6DSM ODR related */
#define LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON              80000
#define LSM6DSM_ODR_12HZ_ACCEL_STD                      1
#define LSM6DSM_ODR_26HZ_ACCEL_STD                      1
#define LSM6DSM_ODR_52HZ_ACCEL_STD                      1
#define LSM6DSM_ODR_104HZ_ACCEL_STD                     1
#define LSM6DSM_ODR_208HZ_ACCEL_STD                     1
#define LSM6DSM_ODR_416HZ_ACCEL_STD                     1
#define LSM6DSM_ODR_12HZ_GYRO_STD                       2
#define LSM6DSM_ODR_26HZ_GYRO_STD                       3
#define LSM6DSM_ODR_52HZ_GYRO_STD                       3
#define LSM6DSM_ODR_104HZ_GYRO_STD                      3
#define LSM6DSM_ODR_208HZ_GYRO_STD                      3
#define LSM6DSM_ODR_416HZ_GYRO_STD                      3

#define LSM6DSM_ODR_12HZ_REG_VALUE                      (0x10)
#define LSM6DSM_ODR_26HZ_REG_VALUE                      (0x20)
#define LSM6DSM_ODR_52HZ_REG_VALUE                      (0x30)
#define LSM6DSM_ODR_104HZ_REG_VALUE                     (0x40)
#define LSM6DSM_ODR_208HZ_REG_VALUE                     (0x50)
#define LSM6DSM_ODR_416HZ_REG_VALUE                     (0x60)

#define LSM6DSM_INT_FIFO_FTH_ENABLE_REG_VALUE           (0x08)
#define LSM6DSM_INT_STEP_DETECTOR_ENABLE_REG_VALUE      (0x80)
#define LSM6DSM_INT_STEP_COUNTER_ENABLE_REG_VALUE       (0x80)
#define LSM6DSM_INT_SIGN_MOTION_ENABLE_REG_VALUE        (0x40)

/* LSM6DSM registers */
#define LSM6DSM_FUNC_CFG_ACCESS_ADDR                    (0x01)
#define LSM6DSM_FIFO_CTRL1_ADDR                         (0x06)
#define LSM6DSM_FIFO_CTRL5_ADDR                         (0x0a)
#define LSM6DSM_DRDY_PULSE_CFG_ADDR                     (0x0b)
#define LSM6DSM_INT1_CTRL_ADDR                          (0x0d)
#define LSM6DSM_INT2_CTRL_ADDR                          (0x0e)
#define LSM6DSM_WAI_ADDR                                (0x0f)
#define LSM6DSM_CTRL1_XL_ADDR                           (0x10)
#define LSM6DSM_CTRL2_G_ADDR                            (0x11)
#define LSM6DSM_CTRL3_C_ADDR                            (0x12)
#define LSM6DSM_CTRL4_C_ADDR                            (0x13)
#define LSM6DSM_CTRL5_C_ADDR                            (0x14)
#define LSM6DSM_EBD_STEP_COUNT_DELTA_ADDR               (0x15)
#define LSM6DSM_CTRL10_C_ADDR                           (0x19)
#define LSM6DSM_MASTER_CONFIG_ADDR                      (0x1a)
#define LSM6DSM_STATUS_REG_ADDR                         (0x1e)
#define LSM6DSM_OUT_TEMP_L_ADDR                         (0x20)
#define LSM6DSM_OUTX_L_G_ADDR                           (0x22)
#define LSM6DSM_OUTX_L_XL_ADDR                          (0x28)
#define LSM6DSM_OUT_SENSORHUB1_ADDR                     (0x2e)
#define LSM6DSM_FIFO_STATUS1_ADDR                       (0x3a)
#define LSM6DSM_FIFO_DATA_OUT_L_ADDR                    (0x3e)
#define LSM6DSM_TIMESTAMP0_REG_ADDR                     (0x40)
#define LSM6DSM_TIMESTAMP2_REG_ADDR                     (0x42)
#define LSM6DSM_STEP_COUNTER_L_ADDR                     (0x4b)
#define LSM6DSM_FUNC_SRC_ADDR                           (0x53)
#define LSM6DSM_WAKE_UP_DUR_ADDR                        (0x5c)
#define LSM6DSM_X_OFS_USR_ADDR                          (0x73)

#define LSM6DSM_SW_RESET                                (0x01)
#define LSM6DSM_RESET_PEDOMETER                         (0x02)
#define LSM6DSM_ENABLE_FUNC_CFG_ACCESS                  (0x80)
#define LSM6DSM_ENABLE_DIGITAL_FUNC                     (0x04)
#define LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC           (0x10)
#define LSM6DSM_ENABLE_SIGN_MOTION_DIGITAL_FUNC         (0x01)
#define LSM6DSM_MASTER_CONFIG_PULL_UP_EN                (0x08)
#define LSM6DSM_MASTER_CONFIG_MASTER_ON                 (0x01)
#define LSM6DSM_ENABLE_FIFO_TIMESTAMP                   (0x80)
#define LSM6DSM_TIMESTAMP2_REG_RESET_TIMESTAMP          (0xaa)

/* LSM6DSM fifo modes */
#define LSM6DSM_FIFO_BYPASS_MODE                        (0x00)
#define LSM6DSM_FIFO_CONTINUOS_MODE                     (0x36)
#define LSM6DSM_FIFO_CTRL2_FTH_MASK                     (0x07)

/* LSM6DSM fifo decimators */
#define LSM6DSM_FIFO_SAMPLE_NOT_IN_FIFO                 (0x00)
#define LSM6DSM_FIFO_NO_DECIMATION                      (0x01)
#define LSM6DSM_FIFO_DECIMATION_FACTOR_2                (0x02)
#define LSM6DSM_FIFO_DECIMATION_FACTOR_3                (0x03)
#define LSM6DSM_FIFO_DECIMATION_FACTOR_4                (0x04)
#define LSM6DSM_FIFO_DECIMATION_FACTOR_8                (0x05)
#define LSM6DSM_FIFO_DECIMATION_FACTOR_16               (0x06)
#define LSM6DSM_FIFO_DECIMATION_FACTOR_32               (0x07)

/* LSM6DSM selftest related */
#define LSM6DSM_NUM_AVERAGE_SELFTEST                    5
#define LSM6DSM_NUM_AVERAGE_SELFTEST_SLOW               30
#define LSM6DSM_ACCEL_SELFTEST_PS                       (0x01)
#define LSM6DSM_GYRO_SELFTEST_PS                        (0x04)
#define LSM6DSM_ACCEL_SELFTEST_NS                       (0x02)
#define LSM6DSM_GYRO_SELFTEST_NS                        (0x0c)
#define LSM6DSM_ACCEL_SELFTEST_HIGH_THR_LSB             6967            /* 1700mg @8g in LSB */
#define LSM6DSM_ACCEL_SELFTEST_LOW_THR_LSB              368             /* 90mg @8g in LSB */
#define LSM6DSM_GYRO_SELFTEST_HIGH_THR_LSB              10000           /* 700dps @2000dps in LSB */
#define LSM6DSM_GYRO_SELFTEST_LOW_THR_LSB               2142            /* 150dps @2000dps in LSB */

/* LSM6DSM calibration related */
#define LSM6DSM_NUM_AVERAGE_CALIBRATION                 10
#define LSM6DSM_1G_IN_LSB_CALIBRATION                   4098            /* 1000mg @8g in LSB */
#define LSM6DSM_ACCEL_MAX_CALIBRATION_THR_LSB           127             /* 8-bit available */
#define LSM6DSM_ACCEL_LSB_TO_OFFSET_DIGIT_SCALE         0.2501f         /* @8g */

/* LSM6DSM embedded registers */
#define LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR                 (0x02)
#define LSM6DSM_EMBEDDED_SLV0_SUBADDR_ADDR              (0x03)
#define LSM6DSM_EMBEDDED_SLV0_CONFIG_ADDR               (0x04)
#define LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR                 (0x05)
#define LSM6DSM_EMBEDDED_SLV1_SUBADDR_ADDR              (0x06)
#define LSM6DSM_EMBEDDED_SLV1_CONFIG_ADDR               (0x07)
#define LSM6DSM_EMBEDDED_SLV2_ADDR_ADDR                 (0x08)
#define LSM6DSM_EMBEDDED_SLV2_SUBADDR_ADDR              (0x09)
#define LSM6DSM_EMBEDDED_SLV2_CONFIG_ADDR               (0x0a)
#define LSM6DSM_EMBEDDED_SLV3_ADDR_ADDR                 (0x0b)
#define LSM6DSM_EMBEDDED_SLV3_SUBADDR_ADDR              (0x0c)
#define LSM6DSM_EMBEDDED_SLV3_CONFIG_ADDR               (0x0d)
#define LSM6DSM_EMBEDDED_DATAWRITE_SLV0_ADDR            (0x0e)
#define LSM6DSM_EMBEDDED_STEP_COUNT_DELTA_ADDR          (0x15)

#define LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB             (0x01)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONLY_WRITE     (0x00)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONE_SENSOR     (0x10)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_TWO_SENSOR     (0x20)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_THREE_SENSOR   (0x30)
#define LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE         (0x20)
#define LSM6DSM_EMBEDDED_SLV0_WRITE_ADDR_SLEEP          (0x07)

/* LSM6DSM I2C master - slave devices */
#ifdef LSM6DSM_I2C_MASTER_LIS3MDL
#define LSM6DSM_MAGN_KSCALE                             LIS3MDL_KSCALE
#define LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT         LIS3MDL_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_MAGN_DUMMY_REG_ADDR        LIS3MDL_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR            LIS3MDL_CTRL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE           LIS3MDL_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR            LIS3MDL_CTRL3_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE            LIS3MDL_CTRL3_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE        LIS3MDL_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE       LIS3MDL_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR              LIS3MDL_CTRL1_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE              LIS3MDL_CTRL1_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR          LIS3MDL_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN           LIS3MDL_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i)    LIS3MDLMagnRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_LIS3MDL */

#ifdef LSM6DSM_I2C_MASTER_AK09916
#define LSM6DSM_MAGN_KSCALE                             AK09916_KSCALE
#define LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT         AK09916_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_MAGN_DUMMY_REG_ADDR        AK09916_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR            AK09916_CNTL3_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE           AK09916_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR            AK09916_CNTL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE            AK09916_CNTL2_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE        AK09916_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE       AK09916_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR              AK09916_CNTL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE              AK09916_CNTL2_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR          AK09916_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN           AK09916_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i)    AK09916MagnRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_AK09916 */

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
#define LSM6DSM_MAGN_KSCALE                             LSM303AGR_KSCALE
#define LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT         LSM303AGR_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_MAGN_DUMMY_REG_ADDR        LSM303AGR_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR            LSM303AGR_CFG_REG_A_M_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE           LSM303AGR_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR            LSM303AGR_CFG_REG_A_M_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE            LSM303AGR_CFG_REG_A_M_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE        LSM303AGR_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE       LSM303AGR_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR              LSM303AGR_CFG_REG_A_M_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE              LSM303AGR_CFG_REG_A_M_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR          LSM303AGR_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN           LSM303AGR_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i)    LSM303AGRMagnRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

#ifdef LSM6DSM_I2C_MASTER_LPS22HB
#define LSM6DSM_PRESS_KSCALE                            LPS22HB_PRESS_KSCALE
#define LSM6DSM_TEMP_KSCALE                             LPS22HB_TEMP_KSCALE
#define LSM6DSM_PRESS_OUTDATA_LEN                       LPS22HB_OUTDATA_PRESS_BYTE
#define LSM6DSM_TEMP_OUTDATA_LEN                        LPS22HB_OUTDATA_TEMP_BYTE
#define LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT         LPS22HB_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_BARO_DUMMY_REG_ADDR        LPS22HB_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_RESET_ADDR            LPS22HB_CTRL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_RESET_VALUE           LPS22HB_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_ADDR            LPS22HB_CTRL1_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_BASE            LPS22HB_CTRL1_BASE
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_ON_VALUE        LPS22HB_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE       LPS22HB_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_BARO_ODR_ADDR              LPS22HB_CTRL1_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_ODR_BASE              LPS22HB_CTRL1_BASE
#define LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_ADDR          LPS22HB_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN           LPS22HB_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i)    LPS22HBBaroRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_LPS22HB */

#ifndef LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN           0
#endif /* LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN */
#ifndef LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN           0
#endif /* LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN */

/* Magn only enabled */
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && !defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#ifdef LSM6DSM_I2C_MASTER_AK09916
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE           LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_TWO_SENSOR
#else /* LSM6DSM_I2C_MASTER_AK09916 */
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE           LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONE_SENSOR
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

/* Baro only enabled */
#if !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE           LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONE_SENSOR
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

/* Magn & Baro both enabled */
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#ifdef LSM6DSM_I2C_MASTER_AK09916
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE           LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_THREE_SENSOR
#else /* LSM6DSM_I2C_MASTER_AK09916 */
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE           LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_TWO_SENSOR
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */


/* LSM6DSM default base registers status */
/* LSM6DSM_FUNC_CFG_ACCESS_BASE: enable embedded functions register */
#define LSM6DSM_FUNC_CFG_ACCESS_BASE                    (0x00)

/* LSM6DSM_DRDY_PULSE_CFG_BASE: enable pulsed interrupt register */
#define LSM6DSM_DRDY_PULSE_CFG_BASE                     (0x00)

/* LSM6DSM_INT1_CTRL_BASE: interrupt 1 control register default settings */
#define LSM6DSM_INT1_CTRL_BASE                          ((0 << 7) |    /* INT1_STEP_DETECTOR */ \
                                                         (0 << 6) |    /* INT1_SIGN_MOT */ \
                                                         (1 << 5) |    /* INT1_FULL_FLAG */ \
                                                         (1 << 4) |    /* INT1_FIFO_OVR */ \
                                                         (1 << 3) |    /* INT1_FTH */ \
                                                         (0 << 2) |    /* INT1_BOOT */ \
                                                         (0 << 1) |    /* INT1_DRDY_G */ \
                                                         (0 << 0))     /* INT1_DRDY_XL */

/* LSM6DSM_INT2_CTRL_BASE: interrupt 2 control register default settings */
#define LSM6DSM_INT2_CTRL_BASE                          ((0 << 7) |    /* INT2_STEP_DELTA */ \
                                                         (0 << 6) |    /* INT2_STEP_OV */ \
                                                         (0 << 5) |    /* INT2_FULL_FLAG */ \
                                                         (0 << 4) |    /* INT2_FIFO_OVR */ \
                                                         (0 << 3) |    /* INT2_FTH */ \
                                                         (0 << 2) |    /* INT2_DRDY_TEMP */ \
                                                         (0 << 1) |    /* INT2_DRDY_G */ \
                                                         (0 << 0))     /* INT2_DRDY_XL */

/* LSM6DSM_CTRL1_XL_BASE: accelerometer sensor register default settings */
#define LSM6DSM_CTRL1_XL_BASE                           ((0 << 7) |    /* ODR_XL3 */ \
                                                         (0 << 6) |    /* ODR_XL2 */ \
                                                         (0 << 5) |    /* ODR_XL1 */ \
                                                         (0 << 4) |    /* ODR_XL0 */ \
                                                         (1 << 3) |    /* FS_XL1 */ \
                                                         (1 << 2) |    /* FS_XL0 */ \
                                                         (0 << 1) |    /* LPF1_BW_SEL */ \
                                                         (0 << 0))     /* (0) */

/* LSM6DSM_CTRL2_G_BASE: gyroscope sensor register default settings */
#define LSM6DSM_CTRL2_G_BASE                            ((0 << 7) |    /* ODR_G3 */ \
                                                         (0 << 6) |    /* ODR_G2 */ \
                                                         (0 << 5) |    /* ODR_G1 */ \
                                                         (0 << 4) |    /* ODR_G0 */ \
                                                         (1 << 3) |    /* FS_G1 */ \
                                                         (1 << 2) |    /* FS_G0 */ \
                                                         (0 << 1) |    /* FS_125 */ \
                                                         (0 << 0))     /* (0) */

/* LSM6DSM_CTRL3_C_BASE: control register 3 default settings */
#define LSM6DSM_CTRL3_C_BASE                            ((0 << 7) |    /* BOOT */ \
                                                         (1 << 6) |    /* BDU */ \
                                                         (0 << 5) |    /* H_LACTIVE */ \
                                                         (0 << 4) |    /* PP_OD */ \
                                                         (0 << 3) |    /* SIM */ \
                                                         (1 << 2) |    /* IF_INC */ \
                                                         (0 << 1) |    /* BLE */ \
                                                         (0 << 0))     /* SW_RESET */

/* LSM6DSM_CTRL4_C_BASE: control register 4 default settings */
#define LSM6DSM_CTRL4_C_BASE                            ((0 << 7) |    /* DEN_XL_EN */ \
                                                         (0 << 6) |    /* SLEEP */ \
                                                         (1 << 5) |    /* INT2_on_INT1 */ \
                                                         (0 << 4) |    /* DEN_DRDY_MASK */ \
                                                         (0 << 3) |    /* DRDY_MASK */ \
                                                         (1 << 2) |    /* I2C_disable */ \
                                                         (0 << 1) |    /* LPF1_SEL_G */ \
                                                         (0 << 0))     /* (0) */

/* LSM6DSM_CTRL5_C_BASE: control register 5 default settings */
#define LSM6DSM_CTRL5_C_BASE                            (0x00)

/* LSM6DSM_CTRL10_C_BASE: control register 10 default settings */
#define LSM6DSM_CTRL10_C_BASE                           ((0 << 7) |    /* (WRIST_TILT_EN) */ \
                                                         (0 << 6) |    /* (0) */ \
                                                         (1 << 5) |    /* TIMER_EN */ \
                                                         (0 << 4) |    /* PEDO_EN */ \
                                                         (0 << 3) |    /* TILT_EN */ \
                                                         (1 << 2) |    /* FUNC_EN */ \
                                                         (0 << 1) |    /* PEDO_RST_STEP */ \
                                                         (0 << 0))     /* SIGN_MOTION_EN */

/* LSM6DSM_MASTER_CONFIG_BASE: I2C master configuration register default value */
#ifdef LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP
#define LSM6DSM_MASTER_CONFIG_BASE                      (LSM6DSM_MASTER_CONFIG_PULL_UP_EN)
#else /* LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP */
#define LSM6DSM_MASTER_CONFIG_BASE                      (0x00)
#endif /* LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP */

/* LSM6DSM_WAKE_UP_DUR_BASE: control register WK default settings */
#define LSM6DSM_WAKE_UP_DUR_BASE                        (0x10)         /* TIMER_HR */

#define LSM6DSM_X_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                        ((r11 == 1 ? x : (r11 == -1 ? -x : 0)) + \
                                                        (r21 == 1 ? y : (r21 == -1 ? -y : 0)) + \
                                                        (r31 == 1 ? z : (r31 == -1 ? -z : 0)))

#define LSM6DSM_Y_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                        ((r12 == 1 ? x : (r12 == -1 ? -x : 0)) + \
                                                        (r22 == 1 ? y : (r22 == -1 ? -y : 0)) + \
                                                        (r32 == 1 ? z : (r32 == -1 ? -z : 0)))

#define LSM6DSM_Z_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                        ((r13 == 1 ? x : (r13 == -1 ? -x : 0)) + \
                                                        (r23 == 1 ? y : (r23 == -1 ? -y : 0)) + \
                                                        (r33 == 1 ? z : (r33 == -1 ? -z : 0)))

#define LSM6DSM_REMAP_X_DATA(...)                       LSM6DSM_X_MAP(__VA_ARGS__)
#define LSM6DSM_REMAP_Y_DATA(...)                       LSM6DSM_Y_MAP(__VA_ARGS__)
#define LSM6DSM_REMAP_Z_DATA(...)                       LSM6DSM_Z_MAP(__VA_ARGS__)

enum SensorIndex {
    GYRO = 0,
    ACCEL,
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    MAGN,
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    PRESS,
    TEMP,
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    STEP_DETECTOR,
    STEP_COUNTER,
    SIGN_MOTION,
    NUM_SENSORS,
    EMBEDDED_TIMESTAMP
};

enum SensorFifoIndex {
    FIFO_GYRO,
    FIFO_ACCEL,
    FIFO_DS3,
    FIFO_DS4,
    FIFO_NUM
};

enum InitState {
    RESET_LSM6DSM = 0,
    INIT_LSM6DSM,
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    INIT_I2C_MASTER_REGS_CONF,
    INIT_I2C_MASTER_SENSOR_RESET,
    INIT_I2C_MASTER_MAGN_SENSOR,
    INIT_I2C_MASTER_BARO_SENSOR,
    INIT_I2C_MASTER_SENSOR_END,
#endif /* LSM6DSM_I2C_MASTER_ENABLED */
    INIT_DONE,
};

enum SelfTestState {
    SELFTEST_INITIALIZATION = 0,
    SELFTEST_READ_EST_DATA,
    SELFTEST_SECOND_STEP_INITIALIZATION,
    SELFTEST_READ_NST_DATA,
    SELFTEST_VERIFICATION,
    SELFTEST_COMPLETED
};

enum CalibrationState {
    CALIBRATION_INITIALIZATION = 0,
    CALIBRATION_READ_DATA,
    CALIBRATION_VERIFICATION,
    CALIBRATION_COMPLETED
};

enum SensorEvents {
    NO_EVT = -1,
    EVT_SPI_DONE = EVT_APP_START + 1,
    EVT_START_ACCEL_TIME_CALIB,
    EVT_SENSOR_INTERRUPT_1,
    EVT_SENSOR_POWERING_UP,
    EVT_SENSOR_POWERING_DOWN,
    EVT_SENSOR_CONFIG_CHANGING,
    EVT_SENSOR_RESTORE_IDLE,
    EVT_TIME_SYNC
};

enum SensorState {
    SENSOR_BOOT = 0,
    SENSOR_VERIFY_WAI,
    SENSOR_INITIALIZATION,
    SENSOR_IDLE,
    SENSOR_POWERING_UP,
    SENSOR_POWERING_DOWN,
    SENSOR_CONFIG_CHANGING,
    SENSOR_CONFIG_WATERMARK_CHANGING,
    SENSOR_CALIBRATION,
    SENSOR_STORE_CALIBRATION_DATA,
    SENSOR_SELFTEST,
    SENSOR_INT1_STATUS_REG_HANDLING,
    SENSOR_INT1_OUTPUT_DATA_HANDLING,
    SENSOR_TIME_SYNC,
    SENSOR_BARO_READ_DATA,
    SENSOR_INVALID_STATE
};

static void lsm6dsm_spiQueueRead(uint8_t addr, size_t size, uint8_t **buf, uint32_t delay);
static void lsm6dsm_spiQueueWrite(uint8_t addr, uint8_t data, uint32_t delay);
static void lsm6dsm_spiQueueMultiwrite(uint8_t addr, uint8_t *data, size_t size, uint32_t delay);

#define SPI_MULTIWRITE_0(addr, data, size)                          lsm6dsm_spiQueueMultiwrite(addr, data, size, 2)
#define SPI_MULTIWRITE_1(addr, data, size, delay)                   lsm6dsm_spiQueueMultiwrite(addr, data, size, delay)
#define GET_SPI_MULTIWRITE_MACRO(_1, _2, _3, _4, NAME, ...)         NAME
#define SPI_MULTIWRITE(...)                                         GET_SPI_MULTIWRITE_MACRO(__VA_ARGS__, SPI_MULTIWRITE_1, SPI_MULTIWRITE_0)(__VA_ARGS__)

#define SPI_WRITE_0(addr, data)                                     lsm6dsm_spiQueueWrite(addr, data, 2)
#define SPI_WRITE_1(addr, data, delay)                              lsm6dsm_spiQueueWrite(addr, data, delay)
#define GET_SPI_WRITE_MACRO(_1, _2, _3, NAME, ...)                  NAME
#define SPI_WRITE(...)                                              GET_SPI_WRITE_MACRO(__VA_ARGS__, SPI_WRITE_1, SPI_WRITE_0)(__VA_ARGS__)

#define SPI_READ_0(addr, size, buf)                                 lsm6dsm_spiQueueRead(addr, size, buf, 0)
#define SPI_READ_1(addr, size, buf, delay)                          lsm6dsm_spiQueueRead(addr, size, buf, delay)
#define GET_SPI_READ_MACRO(_1, _2, _3, _4, NAME, ...)               NAME
#define SPI_READ(...)                                               GET_SPI_READ_MACRO(__VA_ARGS__, SPI_READ_1, SPI_READ_0)(__VA_ARGS__)

#ifdef LSM6DSM_I2C_MASTER_ENABLED
static void lsm6dsm_writeSlaveRegister(uint8_t addr, uint8_t value, uint32_t accelRate, uint32_t delay, enum SensorIndex si);

#define SPI_WRITE_SS_REGISTER_0(addr, value, accelRate, si)         lsm6dsm_writeSlaveRegister(addr, value, accelRate, 0, si)
#define SPI_WRITE_SS_REGISTER_1(addr, value, accelRate, si, delay)  lsm6dsm_writeSlaveRegister(addr, value, accelRate, delay, si)
#define GET_SPI_WRITE_SS_MACRO(_1, _2, _3, _4, _5, NAME, ...)       NAME
#define SPI_WRITE_SLAVE_SENSOR_REGISTER(...)                        GET_SPI_WRITE_SS_MACRO(__VA_ARGS__, SPI_WRITE_SS_REGISTER_1, \
                                                                        SPI_WRITE_SS_REGISTER_0)(__VA_ARGS__)
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

#define INFO_PRINT(fmt, ...) \
    do { \
        osLog(LOG_INFO, "%s " fmt, "[LSM6DSM]", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) \
    do { \
        if (LSM6DSM_DBG_ENABLED) { \
            osLog(LOG_DEBUG, "%s " fmt, "[LSM6DSM]", ##__VA_ARGS__); \
        } \
    } while (0);

#define ERROR_PRINT(fmt, ...) \
    do { \
        osLog(LOG_ERROR, "%s " fmt, "[LSM6DSM]", ##__VA_ARGS__); \
    } while (0);

/* DO NOT MODIFY, just to avoid compiler error if not defined using FLAGS */
#ifndef LSM6DSM_DBG_ENABLED
#define LSM6DSM_DBG_ENABLED                             0
#endif /* LSM6DSM_DBG_ENABLED */


/*
 * struct LSM6DSMSPISlaveInterface: SPI slave data interface
 * @packets: spi packets needed to perform read/write operations.
 * @txrxBuffer: spi data buffer.
 * @spiDev: spi device info.
 * @mode: spi mode info (frequency, polarity, etc).
 * @mWbufCnt: counter of total data in spi buffer.
 * @cs: chip select used by SPI slave.
 * @funcSrcBuffer: pointer of txrxBuffer to access func source register data.
 * @tmpDataBuffer: pointer of txrxBuffer to access sporadic temp read.
 * @fifoDataBuffer: pointer of txrxBuffer to access fifo data.
 * @fifoStatusRegBuffer: pointer of txrxBuffer to access fifo status registers.
 * @stepCounterDataBuffer: pointer of txrxBuffer to access step counter data.
 * @tempDataBuffer: pointer of txrxBuffer to access sensor temperature data needed by calibration algos.
 * @timestampDataBuffer: pointer of txrxBuffer to access sensor timestamp data in order to syncronize time.
 * @timestampDataBufferBaro: pointer of txrxBuffer to access sensor timestamp data for barometer when not in FIFO.
 * @baroDataBuffer: pointer of txrx to access barometer data from DSM when not in FIFO.
 * @mRegCnt: spi packet num counter.
 * @spiInUse: flag used to check if SPI is currently busy.
 */
struct LSM6DSMSPISlaveInterface {
    struct SpiPacket packets[LSM6DSM_SPI_PACKET_SIZE];
    uint8_t txrxBuffer[SPI_BUF_SIZE];
    struct SpiDevice *spiDev;
    struct SpiMode mode;

    uint16_t mWbufCnt;

    spi_cs_t cs;

    uint8_t *funcSrcBuffer;
    uint8_t *tmpDataBuffer;
    uint8_t *fifoDataBuffer;
    uint8_t *fifoStatusRegBuffer;
    uint8_t *stepCounterDataBuffer;
#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    uint8_t *tempDataBuffer;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */
    uint8_t *timestampDataBuffer;
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    uint8_t *timestampDataBufferBaro;
    uint8_t *baroDataBuffer;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    uint8_t mRegCnt;

    bool spiInUse;
};

/*
 * struct LSM6DSMConfigStatus: temporary data of pending events
 * @latency: value to be used in next setRate operation [ns].
 * @rate: value to be used in next setRate operation [Hz * 1024].
 * @enable: value to be used in next setEnable.
 */
struct LSM6DSMConfigStatus {
    uint64_t latency;
    uint32_t rate;
    bool enable;
};

/*
 * struct LSM6DSMSensor: sensor status data
 * @pConfig: temporary data of pending events.
 * @tADataEvt: three axis sensor data to send to nanohub.
 * @sADataEvt: one axis sensor data to send to nanohub.
 * @latency: current value of latency [n].
 * @pushedTimestamp: latest sample timestamp pusshed to nanohub.
 * @handle: sensor handle obtained by sensorRegister.
 * @rate: current value of rates based on dependecies [Hz * 1024].
 * @hwRate: current value of physical rate [Hz * 1024].
 * @idx: enum SensorIndex.
 * @samplesToDiscard: samples to discard after enable or odr switch.
 * @samplesDecimator: sw decimator factor to achieve lower odr that cannot be achieved only by FIFO decimator. For example accel is used by dependecies.
 * @samplesDecimatorCounter: samples counter working together with samplesDecimator.
 * @samplesFifoDecimator: sw decimator factor to achieve lower odr that cannot be achived by FIFO decimator.
 * @samplesFifoDecimatorCounter: samples counter working together with sampleFifoDecimator.
 * @dependenciesRequireData: mask used to verify if dependencies needs data or not. For example accel is used for internal algos.
 * enabled: current status of sensor.
 */
struct LSM6DSMSensor {
    struct LSM6DSMConfigStatus pConfig;

    union {
        struct TripleAxisDataEvent *tADataEvt;
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        struct SingleAxisDataEvent *sADataEvt;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    };

    uint64_t latency;
    uint64_t pushedTimestamp;
    uint32_t handle;
    uint32_t rate[NUM_SENSORS];
    uint32_t hwRate;
    enum SensorIndex idx;
    uint8_t samplesToDiscard;
    uint8_t samplesDecimator;
    uint8_t samplesDecimatorCounter;
    uint8_t samplesFifoDecimator;
    uint8_t samplesFifoDecimatorCounter;
    bool dependenciesRequireData[NUM_SENSORS];
    bool enabled;
};

/*
 * struct LSM6DSMFifoCntl: fifo control data
 * @decimatorsIdx: give who is the sensor that store data in that FIFO slot.
 * @triggerRate: frequency of FIFO [Hz * 1024].
 * @watermark: watermark value in #num of samples.
 * @decimators: fifo decimators value.
 * @minDecimator: min value of decimators.
 * @maxDecimator: max value of decimators.
 * @maxMinDecimator: maxDecimator devided by minDecimator.
 * @totalSip: total number of samples in one pattern.
 * @timestampPosition: since timestamp in FIFO is the latest sensor, we need to know where is located during FIFO parsing.
 */
struct LSM6DSMFifoCntl {
    enum SensorIndex decimatorsIdx[FIFO_NUM];
    uint32_t triggerRate;
    uint16_t watermark;
    uint8_t decimators[FIFO_NUM];
    uint8_t minDecimator;
    uint8_t maxDecimator;
    uint8_t maxMinDecimator;
    uint8_t totalSip;
    uint8_t timestampPosition[32];
};

/*
 * struct LSM6DSMTimeCalibrationWithoutTimer: data used when time calibration is performed during FIFO read.
 *      If latency is smaller than LSM6DSM_SYNC_DELTA_INTERVAL no need to use a timer but we can read timestamp before read FIFO data.
 * @lastTimestampDataAvlRtcTime: last time we perform a timestamp read from LSM6DSM based on RTC time.
 * @newTimestampDataAvl: when deltatime is enough we can read again timestamp from LSM6DSM.
 */
struct LSM6DSMTimeCalibrationWithoutTimer {
    uint64_t lastTimestampDataAvlRtcTime;
    bool newTimestampDataAvl;
};

enum LSM6DSMTimeCalibrationStatus {
    TIME_SYNC_DISABLED,
    TIME_SYNC_TIMER,
    TIME_SYNC_DURING_FIFO_READ
};

/*
 * struct LSM6DSMTimeCalibration: time calibration task data
 * @sensorTimeToRtcData: timeSync algo data.
 * @noTimer: if timer is not used to perform time sync, those data will be used.
 * @lastSampleTimestamp: last sample timestamp from FIFO. Already coverted to RTC time.
 * @timeSyncRtcTime: Rtc time while performing timestamp read from LSM6DSM.
 * @sampleTimestampFromFifoLSB: current timestamp from FIFO in LSB. Needs to be stored becasue of overflow.
 * @timestampSyncTaskLSB: when timer is used to sync time, this is the last timestamp read from LSM6DSM in LSB. Needs to be stored becasue of overflow.
 * @deltaTimeMarginLSB: is it used to verify if timestamp from FIFO is valid, this is max jitter that timestamp can have from FIFO.
 * @timestampBaroLSB: if magn and baro are both enabled, barometer data are read with a timer because no slots are available in FIFO. This is the timestamp of baro data.
 * @theoreticalDeltaTimeLSB: theoretical value of timestamp based on sensor frequency.
 * @timestampIsValid: flag that indicate if current timestamp parsing FIFO is valid.
 */
struct LSM6DSMTimeCalibration {
    time_sync_t sensorTimeToRtcData;
    struct LSM6DSMTimeCalibrationWithoutTimer noTimer;
    uint64_t lastSampleTimestamp;
    uint64_t timeSyncRtcTime;
    enum LSM6DSMTimeCalibrationStatus status;
    uint32_t sampleTimestampFromFifoLSB;
    uint32_t timestampSyncTaskLSB;
    uint32_t deltaTimeMarginLSB;
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    uint32_t timestampBaroLSB;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    uint32_t theoreticalDeltaTimeLSB;
    bool timestampIsValid;
};

/*
 * struct LSM6DSMSelfTestResultPkt: self-test packet result data
 * @header: describe packet size and application ID of packet.
 * @dataHeader: payload of message.
 */
struct LSM6DSMSelfTestResultPkt {
    struct HostHubRawPacket header;
    struct SensorAppEventHeader dataHeader;
} __attribute__((packed));

/*
 * struct LSM6DSMCalibrationResultPkt: calibration packet result data
 * @header: describe packet size and application ID of packet.
 * @dataHeader: payload of header message.
 * @xBias: raw offset value X axis.
 * @yBias: raw offset value Y axis.
 * @zBias: raw offset value Z axis.
 */
struct LSM6DSMCalibrationResultPkt {
    struct HostHubRawPacket header;
    struct SensorAppEventHeader dataHeader;
    int32_t xBias;
    int32_t yBias;
    int32_t zBias;
} __attribute__((packed));

/*
 * struct LSM6DSMAccelGyroCfgData: configuration packet data
 * @hw: chip level calibration data.
 * @sw: software level calibration data (algos).
 */
struct LSM6DSMAccelGyroCfgData {
    int32_t hw[LSM6DSM_TRIAXIAL_NUM_AXIS];
    float sw[LSM6DSM_TRIAXIAL_NUM_AXIS];
};

/*
 * struct LSM6DSMTask: driver task data
 * @sensors: sensor status data list.
 * @slaveConn: slave interface / communication data.
 * @accelCal: accelerometer calibration algo data.
 * @gyroCal: gyroscope calibration algo data.
 * @overTempCal: gyroscope over temperature calibration algo data.
 * @magnCal: magnetometer calibration algo data.
 * @int1: int1 gpio data.
 * @isr1: isr1 data.
 * @mDataSlabThreeAxis: memory used to store three axis sensors data.
 * @mDataSlabOneAxis: memory used to store one axis sensors data.
 * @fifoCntl: fifo control data.
 * @time: time calibration data.
 * @currentTemperature: sensor temperature data value used by gyroscope/accelerometer bias calibration libs.
 * @lastFifoReadTimestamp: store when last time FIFO was read.
 * @initState: initialization is done in several steps (enum InitState).
 * @selftestState: self-test is performed in several steps (enum SelfTestState).
 * @calibrationState: sensor calibration is done in several steps (enum CalibrationState).
 * @tid: task id.
 * @totalNumSteps: total number of steps of step counter sensor.
 * @fifoDataToRead: number of byte to read in current FIFO read.
 * @fifoDataToReadPending: in order to reduce txrxBuffer, FIFO read is performed in several read. This value tell how many data still need to read from FIFO.
 * @baroTimerId: barometer task timer id.
 * @dataSelftestEnabled: sensor data read during GapSelfTestProgram while self-test bit is set.
 * @dataSelftestNotEnabled: sensor data read during GapSelfTestProgram while self-test bit is not set.
 * @dataCalibration: sensor data read during calibration program.
 * @accelCalibrationData: accelerometer offset value (hw) to store into sensor.
 * @gyroCalibrationData: gyroscope offset value (hw) applied to each sample (by software).
 * @state: task state, driver manage operations using a state machine (enum SensorState).
 * @numSamplesSelftest: temp variable storing number of samples read by self-test program.
 * @numSamplesCalibration: temp variable storing number of samples read by calibration program.
 * @mRetryLeft: counter used to retry operations #n times before return a failure.
 * @pedometerDependencies: dependencies mask of sensors that are using embedded functions.
 * @masterConfigDependencies: dependencies mask of sensors that are using I2C master.
 * @int1Register: interrupt 1 register content (addr: 0x0d).
 * @int2Register: interrupt 2 register content (addr: 0x0e).
 * @embeddedFunctionsRegister: embedded register content (addr: 0x19).
 * @pendingFlush: number of flush requested for each sensor.
 * @masterConfigRegister: i2c master register content (addr: 0x1a).
 * @readSteps: flag used to indicate if interrupt task need to read number of steps.
 * @sendFlushEvt: if flush is requested, send it out after FIFO read is completed.
 * @pendingEnableConfig: pending setEnable operations to be executed.
 * @pendingRateConfig: pending setRate operations to be executed.
 * @pendingInt: pending interrupt task to be executed.
 * @pendingTimeSyncTask: pending time sync task to be executed.
 * @pendingBaroTimerTask: pending baro read data task to be executed.
 * @pendingStoreAccelCalibData: pending calibration data store task to be executed.
 */
typedef struct LSM6DSMTask {
    struct LSM6DSMSensor sensors[NUM_SENSORS];
    struct LSM6DSMSPISlaveInterface slaveConn;

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    struct AccelCal accelCal;
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    struct GyroCal gyroCal;
#ifdef LSM6DSM_OVERTEMP_CALIB_ENABLED
    struct OverTempCal overTempCal;
#endif /* LSM6DSM_OVERTEMP_CALIB_ENABLED */
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    struct MagCal magnCal;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

    struct Gpio *int1;
    struct ChainedIsr isr1;
    struct SlabAllocator *mDataSlabThreeAxis;
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    struct SlabAllocator *mDataSlabOneAxis;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    struct LSM6DSMFifoCntl fifoCntl;
    struct LSM6DSMTimeCalibration time;

#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    float currentTemperature;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */

    uint64_t lastFifoReadTimestamp;

    enum InitState initState;
    enum SelfTestState selftestState;
    enum CalibrationState calibrationState;

    uint32_t tid;
    uint32_t totalNumSteps;
    uint32_t fifoDataToRead;
    uint32_t fifoDataToReadPending;
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    uint32_t baroTimerId;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    int32_t dataSelftestEnabled[LSM6DSM_TRIAXIAL_NUM_AXIS];
    int32_t dataSelftestNotEnabled[LSM6DSM_TRIAXIAL_NUM_AXIS];
    int32_t dataCalibration[LSM6DSM_TRIAXIAL_NUM_AXIS];
    int32_t accelCalibrationData[LSM6DSM_TRIAXIAL_NUM_AXIS];
    int32_t gyroCalibrationData[LSM6DSM_TRIAXIAL_NUM_AXIS];

    volatile uint8_t state;

    uint8_t numSamplesSelftest;
    uint8_t numSamplesCalibration;
    uint8_t mRetryLeft;
    uint8_t pedometerDependencies;
    uint8_t masterConfigDependencies;
    uint8_t int1Register;
    uint8_t int2Register;
    uint8_t embeddedFunctionsRegister;
    uint8_t pendingFlush[NUM_SENSORS];
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    uint8_t masterConfigRegister;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

    bool readSteps;
    bool sendFlushEvt[NUM_SENSORS];
    bool pendingEnableConfig[NUM_SENSORS];
    bool pendingRateConfig[NUM_SENSORS];
    bool pendingInt;
    bool pendingTimeSyncTask;
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    bool pendingBaroTimerTask;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    bool pendingStoreAccelCalibData;
} LSM6DSMTask;

static LSM6DSMTask mTask;

#define TASK                                            LSM6DSMTask* const _task
#define TDECL()                                         TASK = &mTask; (void)_task
#define T(v)                                            (_task->v)
#define T_SLAVE_INTERFACE(v)                            (_task->slaveConn.v)

#define BIT(x)                                          (0x01 << x)
#define SENSOR_HZ_RATE_TO_US(x)                         (1024000000UL / x)
#define NS_TO_US(ns)                                    cpuMathU64DivByU16(ns, 1000)

/* Atomic get state */
#define GET_STATE()                                     (atomicReadByte(&(_task->state)))

/* Atomic set state, this set the state to arbitrary value, use with caution */
#define SET_STATE(s) \
    do { \
        atomicWriteByte(&(_task->state), (s)); \
    } while (0)

static bool trySwitchState_(TASK, enum SensorState newState)
{
    return atomicCmpXchgByte(&T(state), SENSOR_IDLE, newState);
}
#define trySwitchState(s) trySwitchState_(_task, (s))

static void lsm6dsm_readStatusReg_(TASK, bool isInterruptContext);
#define lsm6dsm_readStatusReg(a)                        lsm6dsm_readStatusReg_(_task, (a))

#define DEC_INFO(name, type, axis, inter, samples) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples

#define DEC_INFO_RATE(name, rates, type, axis, inter, samples) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates

#define DEC_INFO_RATE_BIAS(name, rates, type, axis, inter, samples, bias) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

#define DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_RAW, \
    .rawType = raw, \
    .rawScale = scale

#define DEC_INFO_RATE_RAW_BIAS(name, rates, type, axis, inter, samples, raw, scale, bias) \
    DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale), \
    .flags1 = SENSOR_INFO_FLAGS1_RAW | SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

/*
 * LSM6DSMImuRates: supported frequencies by accelerometer and gyroscope sensors
 * LSM6DSMImuRatesRegValue, LSM6DSMRatesSamplesToDiscardGyroPowerOn, LSM6DSMAccelRatesSamplesToDiscard,
 *     LSM6DSMGyroRatesSamplesToDiscard must have same length.
 */
static uint32_t LSM6DSMImuRates[] = {
    SENSOR_HZ(26.0f / 32.0f),       /* 0.8125Hz */
    SENSOR_HZ(26.0f / 16.0f),       /* 1.625Hz */
    SENSOR_HZ(26.0f / 8.0f),        /* 3.25Hz */
    SENSOR_HZ(26.0f / 4.0f),        /* 6.5Hz */
    SENSOR_HZ(26.0f / 2.0f),        /* 12.5Hz */
    SENSOR_HZ(26.0f),               /* 26Hz */
    SENSOR_HZ(52.0f),               /* 52Hz */
    SENSOR_HZ(104.0f),              /* 104Hz */
    SENSOR_HZ(208.0f),              /* 208Hz */
    SENSOR_HZ(416.0f),              /* 416Hz */
    0,
};

static uint32_t LSM6DSMImuRatesInNs[] = {
    1230769230,                     /* 0.8125Hz */
    615384615,                      /* 1.625Hz */
    307692308,                      /* 3.25Hz */
    153846154,                      /* 6.5Hz */
    80000000,                       /* 12.5Hz */
    38461538,                       /* 26Hz */
    19230769,                       /* 52Hz */
    9615385,                        /* 104Hz */
    4807692,                        /* 208Hz */
    2403846,                        /* 416Hz */
    0,
};

static uint8_t LSM6DSMImuRatesRegValue[] = {
    LSM6DSM_ODR_12HZ_REG_VALUE,     /* 0.8125Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_REG_VALUE,     /* 1.625Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_REG_VALUE,     /* 3.25Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_REG_VALUE,     /* 6.5Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_REG_VALUE,     /* 12.5Hz */
    LSM6DSM_ODR_26HZ_REG_VALUE,     /* 26Hz */
    LSM6DSM_ODR_52HZ_REG_VALUE,     /* 52Hz */
    LSM6DSM_ODR_104HZ_REG_VALUE,    /* 104Hz */
    LSM6DSM_ODR_208HZ_REG_VALUE,    /* 208Hz */
    LSM6DSM_ODR_416HZ_REG_VALUE,    /* 416Hz */
};

/* When sensors switch status from power-down, constant boottime must be considered, some samples should be discarded */
static uint8_t LSM6DSMRatesSamplesToDiscardGyroPowerOn[] = {
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 0.8125Hz - do not exist, use 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 1.625Hz - do not exist, use 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 3.25Hz - do not exist, use 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 6.5Hz - do not exist, use 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 38461, /* 26Hz = 38461us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 19230, /* 52Hz = 19230s */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 9615,  /* 104Hz = 9615us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 4807,  /* 208Hz = 4807us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 2403,  /* 416Hz = 2403us */
};

/* When accelerometer change odr but sensor is already on, few samples should be discarded */
static uint8_t LSM6DSMAccelRatesSamplesToDiscard[] = {
    LSM6DSM_ODR_12HZ_ACCEL_STD,     /* 0.8125Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_ACCEL_STD,     /* 1.625Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_ACCEL_STD,     /* 3.25Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_ACCEL_STD,     /* 6.5Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_ACCEL_STD,     /* 12.5Hz */
    LSM6DSM_ODR_26HZ_ACCEL_STD,     /* 26Hz */
    LSM6DSM_ODR_52HZ_ACCEL_STD,     /* 52Hz */
    LSM6DSM_ODR_104HZ_ACCEL_STD,    /* 104Hz */
    LSM6DSM_ODR_208HZ_ACCEL_STD,    /* 208Hz */
    LSM6DSM_ODR_416HZ_ACCEL_STD,    /* 416Hz */
};

/* When gyroscope change odr but sensor is already on, few samples should be discarded */
static uint8_t LSM6DSMGyroRatesSamplesToDiscard[] = {
    LSM6DSM_ODR_12HZ_GYRO_STD,      /* 0.8125Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_GYRO_STD,      /* 1.625Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_GYRO_STD,      /* 3.25Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_GYRO_STD,      /* 6.5Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_GYRO_STD,      /* 12.5Hz */
    LSM6DSM_ODR_26HZ_GYRO_STD,      /* 26Hz */
    LSM6DSM_ODR_52HZ_GYRO_STD,      /* 52Hz */
    LSM6DSM_ODR_104HZ_GYRO_STD,     /* 104Hz */
    LSM6DSM_ODR_208HZ_GYRO_STD,     /* 208Hz */
    LSM6DSM_ODR_416HZ_GYRO_STD,     /* 416Hz */
};

#ifdef LSM6DSM_I2C_MASTER_ENABLED
static uint32_t LSM6DSMSHRates[] = {
    SENSOR_HZ(26.0f / 32.0f),       /* 0.8125Hz */
    SENSOR_HZ(26.0f / 16.0f),       /* 1.625Hz */
    SENSOR_HZ(26.0f / 8.0f),        /* 3.25Hz */
    SENSOR_HZ(26.0f / 4.0f),        /* 6.5Hz */
    SENSOR_HZ(26.0f / 2.0f),        /* 12.5Hz */
    SENSOR_HZ(26.0f),               /* 26Hz */
    SENSOR_HZ(52.0f),               /* 52Hz */
    SENSOR_HZ(104.0f),              /* 104Hz */
    0,
};
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

static uint32_t LSM6DSMStepCounterRates[] = {
    SENSOR_HZ(1.0f / (128 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)), /* 209.715 sec */
    SENSOR_HZ(1.0f / (64 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),  /* 104.857 sec */
    SENSOR_HZ(1.0f / (32 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),  /* 52.4288 sec */
    SENSOR_HZ(1.0f / (16 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),  /* 26.1574 sec */
    SENSOR_HZ(1.0f / (8 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 13.0787 sec */
    SENSOR_HZ(1.0f / (4 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 6.53936 sec */
    SENSOR_HZ(1.0f / (2 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 3.26968 sec */
    SENSOR_HZ(1.0f / (1 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 1.63840 sec */
    SENSOR_RATE_ONCHANGE,
    0,
};

static const struct SensorInfo LSM6DSMSensorInfo[NUM_SENSORS] = {
    {
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
        DEC_INFO_RATE_BIAS("Gyroscope", LSM6DSMImuRates, SENS_TYPE_GYRO, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 20, SENS_TYPE_GYRO_BIAS)
#else /* LSM6DSM_GYRO_CALIB_ENABLED */
        DEC_INFO_RATE("Gyroscope", LSM6DSMImuRates, SENS_TYPE_GYRO, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 20)
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
    },
    {
#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
        DEC_INFO_RATE_RAW_BIAS("Accelerometer", LSM6DSMImuRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 3000,
            SENS_TYPE_ACCEL_RAW, 1.0f / LSM6DSM_ACCEL_KSCALE, SENS_TYPE_ACCEL_BIAS)
#else /* LSM6DSM_ACCEL_CALIB_ENABLED */
        DEC_INFO_RATE_RAW("Accelerometer", LSM6DSMImuRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 3000,
            SENS_TYPE_ACCEL_RAW, 1.0f / LSM6DSM_ACCEL_KSCALE)
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
    },
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    {
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
        DEC_INFO_RATE_BIAS("Magnetometer", LSM6DSMSHRates, SENS_TYPE_MAG, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 600, SENS_TYPE_MAG_BIAS)
#else /* LSM6DSM_MAGN_CALIB_ENABLED */
        DEC_INFO_RATE("Magnetometer", LSM6DSMSHRates, SENS_TYPE_MAG, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 600)
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
    },
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    {
        DEC_INFO_RATE("Pressure", LSM6DSMSHRates, SENS_TYPE_BARO, NUM_AXIS_ONE, NANOHUB_INT_NONWAKEUP, 300)
    },
    {
        DEC_INFO_RATE("Temperature", LSM6DSMSHRates, SENS_TYPE_TEMP, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 20)
    },
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    {
        DEC_INFO("Step Detector", SENS_TYPE_STEP_DETECT, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 100)
    },
    {
        DEC_INFO_RATE("Step Counter", LSM6DSMStepCounterRates, SENS_TYPE_STEP_COUNT, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 20)
    },
    {
        DEC_INFO("Significant Motion", SENS_TYPE_SIG_MOTION, NUM_AXIS_EMBEDDED, NANOHUB_INT_WAKEUP, 1)
    },
};

#define DEC_OPS(power, firmware, rate, flush) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush

#define DEC_OPS_SEND(power, firmware, rate, flush, send) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush, \
    .sensorSendOneDirectEvt = send

#define DEC_OPS_CFG_SELFTEST(power, firmware, rate, flush, cfgData, selftest) \
    DEC_OPS(power, firmware, rate, flush), \
    .sensorCfgData = cfgData, \
    .sensorSelfTest = selftest

#define DEC_OPS_CAL_CFG_SELFTEST(power, firmware, rate, flush, cal, cfgData, selftest) \
    DEC_OPS(power, firmware, rate, flush), \
    .sensorCalibrate = cal, \
    .sensorCfgData = cfgData, \
    .sensorSelfTest = selftest

static bool lsm6dsm_setAccelPower(bool on, void *cookie);
static bool lsm6dsm_setGyroPower(bool on, void *cookie);
static bool lsm6dsm_setStepDetectorPower(bool on, void *cookie);
static bool lsm6dsm_setStepCounterPower(bool on, void *cookie);
static bool lsm6dsm_setSignMotionPower(bool on, void *cookie);
static bool lsm6dsm_accelFirmwareUpload(void *cookie);
static bool lsm6dsm_gyroFirmwareUpload(void *cookie);
static bool lsm6dsm_stepDetectorFirmwareUpload(void *cookie);
static bool lsm6dsm_stepCounterFirmwareUpload(void *cookie);
static bool lsm6dsm_signMotionFirmwareUpload(void *cookie);
static bool lsm6dsm_setAccelRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setGyroRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setStepDetectorRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setStepCounterRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setSignMotionRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_accelFlush(void *cookie);
static bool lsm6dsm_gyroFlush(void *cookie);
static bool lsm6dsm_stepDetectorFlush(void *cookie);
static bool lsm6dsm_stepCounterFlush(void *cookie);
static bool lsm6dsm_signMotionFlush(void *cookie);
static bool lsm6dsm_stepCounterSendLastData(void *cookie, uint32_t tid);
static bool lsm6dsm_runAccelSelfTest(void *cookie);
static bool lsm6dsm_runGyroSelfTest(void *cookie);
static bool lsm6dsm_runAccelCalibration(void *cookie);
static bool lsm6dsm_runGyroCalibration(void *cookie);
static bool lsm6dsm_accelCfgData(void *data, void *cookie);
static bool lsm6dsm_gyroCfgData(void *data, void *cookie);

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
static bool lsm6dsm_setMagnPower(bool on, void *cookie);
static bool lsm6dsm_magnFirmwareUpload(void *cookie);
static bool lsm6dsm_setMagnRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_magnFlush(void *cookie);
static bool lsm6dsm_runMagnSelfTest(void *cookie);
static bool lsm6dsm_magnCfgData(void *data, void *cookie);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
static bool lsm6dsm_setPressPower(bool on, void *cookie);
static bool lsm6dsm_pressFirmwareUpload(void *cookie);
static bool lsm6dsm_setPressRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_pressFlush(void *cookie);
static bool lsm6dsm_setTempPower(bool on, void *cookie);
static bool lsm6dsm_tempFirmwareUpload(void *cookie);
static bool lsm6dsm_setTempRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_tempFlush(void *cookie);
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

static const struct SensorOps LSM6DSMSensorOps[NUM_SENSORS] = {
    { DEC_OPS_CAL_CFG_SELFTEST(lsm6dsm_setGyroPower, lsm6dsm_gyroFirmwareUpload, lsm6dsm_setGyroRate,
                                lsm6dsm_gyroFlush, lsm6dsm_runGyroCalibration, lsm6dsm_gyroCfgData, lsm6dsm_runGyroSelfTest) },
    { DEC_OPS_CAL_CFG_SELFTEST(lsm6dsm_setAccelPower, lsm6dsm_accelFirmwareUpload, lsm6dsm_setAccelRate,
                                lsm6dsm_accelFlush, lsm6dsm_runAccelCalibration, lsm6dsm_accelCfgData, lsm6dsm_runAccelSelfTest) },
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    { DEC_OPS_CFG_SELFTEST(lsm6dsm_setMagnPower, lsm6dsm_magnFirmwareUpload, lsm6dsm_setMagnRate,
                                lsm6dsm_magnFlush, lsm6dsm_magnCfgData, lsm6dsm_runMagnSelfTest) },
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    { DEC_OPS(lsm6dsm_setPressPower, lsm6dsm_pressFirmwareUpload, lsm6dsm_setPressRate, lsm6dsm_pressFlush) },
    { DEC_OPS(lsm6dsm_setTempPower, lsm6dsm_tempFirmwareUpload, lsm6dsm_setTempRate, lsm6dsm_tempFlush) },
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    { DEC_OPS(lsm6dsm_setStepDetectorPower, lsm6dsm_stepDetectorFirmwareUpload, lsm6dsm_setStepDetectorRate, lsm6dsm_stepDetectorFlush) },
    { DEC_OPS_SEND(lsm6dsm_setStepCounterPower, lsm6dsm_stepCounterFirmwareUpload, lsm6dsm_setStepCounterRate,
                                lsm6dsm_stepCounterFlush, lsm6dsm_stepCounterSendLastData) },
    { DEC_OPS(lsm6dsm_setSignMotionPower, lsm6dsm_signMotionFirmwareUpload, lsm6dsm_setSignMotionRate, lsm6dsm_signMotionFlush) },
};

static void lsm6dsm_processPendingEvt(void);

/*
 * lsm6dsm_spiQueueRead: enqueue a new SPI read that will be performed after lsm6dsm_spiBatchTxRx function is called
 * @addr: start reading from this register address.
 * @size: number of byte to read.
 * @buf: address of pointer where store data.
 * @delay: wait `delay time' after read is completed. [us]
 */
static void lsm6dsm_spiQueueRead(uint8_t addr, size_t size, uint8_t **buf, uint32_t delay)
{
    TDECL();

    if (T_SLAVE_INTERFACE(spiInUse)) {
        ERROR_PRINT("spiQueueRead: SPI in use, cannot queue read (addr=%x len=%d)\n", addr, (int)size);
        return;
    }

    *buf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);

    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).size = size + 1;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).txBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).rxBuf = *buf;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).delay = delay * 1000;

    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = addr | 0x80;
    T_SLAVE_INTERFACE(mWbufCnt) += size;
    T_SLAVE_INTERFACE(mRegCnt)++;
}

/*
 * lsm6dsm_spiQueueWrite: enqueue a new SPI 1-byte write that will be performed after lsm6dsm_spiBatchTxRx function is called
 * @addr: write byte to this register address.
 * @data: value to write.
 * @delay: wait `delay time' after write is completed. [us]
 */
static void lsm6dsm_spiQueueWrite(uint8_t addr, uint8_t data, uint32_t delay)
{
    TDECL();

    if (T_SLAVE_INTERFACE(spiInUse)) {
        ERROR_PRINT("spiQueueWrite: SPI in use, cannot queue 1-byte write (addr=%x data=%x)\n", addr, data);
        return;
    }

    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).size = 2;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).txBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).rxBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).delay = delay * 1000;

    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = addr;
    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = data;
    T_SLAVE_INTERFACE(mRegCnt)++;
}

/*
 * lsm6dsm_spiQueueMultiwrite: enqueue a new SPI n-byte write that will be performed after lsm6dsm_spiBatchTxRx function is called
 * @addr: start writing from this register address.
 * @data: array data to write.
 * @size: number of byte to write.
 * @delay: wait `delay time' after write is completed. [us]
 */
static void lsm6dsm_spiQueueMultiwrite(uint8_t addr, uint8_t *data, size_t size, uint32_t delay)
{
    TDECL();
    uint8_t i;

    if (T_SLAVE_INTERFACE(spiInUse)) {
        ERROR_PRINT("spiQueueMultiwrite: SPI in use, cannot queue multiwrite (addr=%x size=%d)\n", addr, (int)size);
        return;
    }

    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).size = 1 + size;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).txBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).rxBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).delay = delay * 1000;

    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = addr;

    for (i = 0; i < size; i++)
        T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = data[i];

    T_SLAVE_INTERFACE(mRegCnt)++;
}

/*
 * lsm6dsm_spiBatchTxRx: perform SPI read and/or write enqueued before
 * @mode: SPI configuration data.
 * @callback: callback function triggered when all transactions are terminated.
 * @cookie: private data delivered to callback function.
 * @src: function name and/or custom string used during print to trace the callstack.
 */
static void lsm6dsm_spiBatchTxRx(struct SpiMode *mode, SpiCbkF callback, void *cookie, const char *src)
{
    TDECL();
    uint8_t regCount;

    if (T_SLAVE_INTERFACE(mWbufCnt) > SPI_BUF_SIZE) {
        ERROR_PRINT("spiBatchTxRx: not enough SPI buffer space, dropping transaction. Ref. %s\n", src);
        return;
    }

    if (T_SLAVE_INTERFACE(mRegCnt) > LSM6DSM_SPI_PACKET_SIZE) {
        ERROR_PRINT("spiBatchTxRx: too many packets! Ref. %s\n", src);
        return;
    }

    /* Reset variables before issuing SPI transaction.
       SPI may finish before spiMasterRxTx finish */
    regCount = T_SLAVE_INTERFACE(mRegCnt);
    T_SLAVE_INTERFACE(spiInUse) = true;
    T_SLAVE_INTERFACE(mRegCnt) = 0;
    T_SLAVE_INTERFACE(mWbufCnt) = 0;

    if (spiMasterRxTx(T_SLAVE_INTERFACE(spiDev), T_SLAVE_INTERFACE(cs), T_SLAVE_INTERFACE(packets), regCount, mode, callback, cookie)) {
        ERROR_PRINT("spiBatchTxRx: transaction failed!\n");
    }
}

/*
 * lsm6dsm_timerCallback: timer callback routine used to retry WAI read
 * @timerId: timer identificator.
 * @data: private data delivered to private event handler.
 */
static void lsm6dsm_timerCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_SPI_DONE, data, NULL, mTask.tid);
}

/*
 * lsm6dsm_timerSyncCallback: time syncronization timer callback routine
 * @timerId: timer identificator.
 * @data: private data delivered to private event handler.
 */
static void lsm6dsm_timerSyncCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_TIME_SYNC, data, NULL, mTask.tid);
}

/*
 * lsm6dsm_spiCallback: SPI callback function
 * @cookie: private data from lsm6dsm_spiBatchTxRx function.
 * @err: error code from SPI transfer.
 */
static void lsm6dsm_spiCallback(void *cookie, int err)
{
    TDECL();

    T_SLAVE_INTERFACE(spiInUse) = false;
    osEnqueuePrivateEvt(EVT_SPI_DONE, cookie, NULL, mTask.tid);
}

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
/*
 * lsm6dsm_baroTimerTask: baro read data task
 */
static void lsm6dsm_baroTimerTask(void)
{
    TDECL();

    if (trySwitchState(SENSOR_BARO_READ_DATA)) {
        SPI_READ(LSM6DSM_TIMESTAMP0_REG_ADDR, LSM6DSM_TIMESTAMP_SAMPLE_BYTE, &T_SLAVE_INTERFACE(timestampDataBufferBaro));
        SPI_READ(LSM6DSM_OUT_SENSORHUB1_ADDR + LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN,
                LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN, &T_SLAVE_INTERFACE(baroDataBuffer));

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else
        T(pendingBaroTimerTask) = true;

    return;
}

/*
 * lsm6dsm_baroTimerCallback: baro timer callback routine
 * @timerId: timer identificator.
 * @data: private data delivered to private event handler.
 */
static void lsm6dsm_baroTimerCallback(uint32_t timerId, void *data)
{
    lsm6dsm_baroTimerTask();
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_timeSyncTask: time syncronization task by timer
 */
static void lsm6dsm_timeSyncTask(void)
{
    TDECL();

    if (T(time).status != TIME_SYNC_TIMER)
        return;

    if (trySwitchState(SENSOR_TIME_SYNC)) {
        SPI_READ(LSM6DSM_TIMESTAMP0_REG_ADDR, LSM6DSM_TIMESTAMP_SAMPLE_BYTE, &T_SLAVE_INTERFACE(timestampDataBuffer));
#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
        SPI_READ(LSM6DSM_OUT_TEMP_L_ADDR, LSM6DSM_TEMP_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tempDataBuffer));
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */

        T(time).timeSyncRtcTime = sensorGetTime();

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else
        T(pendingTimeSyncTask) = true;
}

/*
 * lsm6dsm_readStatusReg_: read status registers (interrupt arrived)
 * @TASK: task id.
 * @isInterruptContext: function is called directly by ISR.
 */
static void lsm6dsm_readStatusReg_(TASK, bool isInterruptContext)
{
    if (trySwitchState(SENSOR_INT1_STATUS_REG_HANDLING)) {
        if (T(sensors[STEP_DETECTOR]).enabled || T(sensors[STEP_COUNTER]).enabled || T(sensors[SIGN_MOTION]).enabled)
            SPI_READ(LSM6DSM_FUNC_SRC_ADDR, 1, &T_SLAVE_INTERFACE(funcSrcBuffer));

        SPI_READ(LSM6DSM_FIFO_STATUS1_ADDR, 2, &T_SLAVE_INTERFACE(fifoStatusRegBuffer));

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else {
        if (isInterruptContext)
            osEnqueuePrivateEvt(EVT_SENSOR_INTERRUPT_1, _task, NULL, T(tid));
        else
            T(pendingInt) = true;
    }
}

/*
 * lsm6dsm_isr1: INT-1 line service routine
 * @isr: isr data.
 */
static bool lsm6dsm_isr1(struct ChainedIsr *isr)
{
    TDECL();

    if (!extiIsPendingGpio(T(int1)))
        return false;

    lsm6dsm_readStatusReg(true);

    extiClearPendingGpio(T(int1));

    return true;
}

/*
 * lsm6dsm_enableInterrupt: enable driver interrupt capability
 * @pin: gpio data.
 * @isr: isr data.
 */
static void lsm6dsm_enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_RISING);
    extiChainIsr(LSM6DSM_INT_IRQ, isr);
}

/*
 * lsm6dsm_disableInterrupt: disable driver interrupt capability
 * @pin: gpio data.
 * @isr: isr data.
 */
static void lsm6dsm_disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiUnchainIsr(LSM6DSM_INT_IRQ, isr);
    extiDisableIntGpio(pin);
}

/*
 * lsm6dsm_sendSelfTestResult: send to nanohub result of self-test
 * @sensorType: android sensor type.
 * @result: status message to send (PASS/ERROR).
 */
static void lsm6dsm_sendSelfTestResult(uint8_t sensorType, uint8_t result)
{
    struct LSM6DSMSelfTestResultPkt *data;

    data = heapAlloc(sizeof(struct LSM6DSMSelfTestResultPkt));
    if (!data) {
        ERROR_PRINT("sendSelfTestResult: cannot allocate self-test result packet\n");
        return;
    }

    data->header.appId = LSM6DSM_APP_ID;
    data->header.dataLen = (sizeof(struct LSM6DSMSelfTestResultPkt) - sizeof(struct HostHubRawPacket));

    data->dataHeader.msgId = SENSOR_APP_MSG_ID_TEST_RESULT;
    data->dataHeader.sensorType = sensorType;
    data->dataHeader.status = result;

    if (!osEnqueueEvtOrFree(EVT_APP_TO_HOST, data, heapFree)) {
        ERROR_PRINT("sendSelfTestResult: failed to enqueue self-test result packet\n");
    }
}

/*
 * lsm6dsm_sendCalibrationResult: send to nanohub result of calibration
 * @sensorType: android sensor type.
 * @result: status message to send (VALID/ERROR).
 * @xBias: raw offset value X axis.
 * @yBias: raw offset value Y axis.
 * @zBias: raw offset value Z axis.
 */
static void lsm6dsm_sendCalibrationResult(uint8_t sensorType, uint8_t result, int32_t xBias, int32_t yBias, int32_t zBias)
{
    struct LSM6DSMCalibrationResultPkt *data;

    data = heapAlloc(sizeof(struct LSM6DSMCalibrationResultPkt));
    if (!data) {
        ERROR_PRINT("sendCalibrationResult: cannot allocate calibration result packet\n");
        return;
    }

    data->header.appId = LSM6DSM_APP_ID;
    data->header.dataLen = (sizeof(struct LSM6DSMCalibrationResultPkt) - sizeof(struct HostHubRawPacket));

    data->dataHeader.msgId = SENSOR_APP_MSG_ID_CAL_RESULT;
    data->dataHeader.sensorType = sensorType;
    data->dataHeader.status = result;

    data->xBias = xBias;
    data->yBias = yBias;
    data->zBias = zBias;

    if (!osEnqueueEvtOrFree(EVT_APP_TO_HOST, data, heapFree)) {
        ERROR_PRINT("sendCalibrationResult: failed to enqueue calibration result packet\n");
    }
}

/*
 * lsm6dsm_runGapSelfTestProgram: state machine that is executing self-test verifying data gap
 * @idx: sensor driver index.
 */
static void lsm6dsm_runGapSelfTestProgram(enum SensorIndex idx)
{
    TDECL();
    uint8_t *sensorData, numberOfAverage;

    numberOfAverage = LSM6DSM_NUM_AVERAGE_SELFTEST;

    switch (T(selftestState)) {
    case SELFTEST_INITIALIZATION:
        DEBUG_PRINT("runGapSelfTestProgram: initialization\n");

        T(numSamplesSelftest) = 0;
        memset(T(dataSelftestEnabled), 0, LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));
        memset(T(dataSelftestNotEnabled), 0, LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));

        /* Enable self-test & power on sensor */
        switch (idx) {
        case ACCEL:
            SPI_WRITE(LSM6DSM_CTRL5_C_ADDR, LSM6DSM_CTRL5_C_BASE | LSM6DSM_ACCEL_SELFTEST_PS);
            SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | LSM6DSM_ODR_104HZ_REG_VALUE, 30000);
            break;
        case GYRO:
            SPI_WRITE(LSM6DSM_CTRL5_C_ADDR, LSM6DSM_CTRL5_C_BASE | LSM6DSM_GYRO_SELFTEST_PS);
            SPI_WRITE(LSM6DSM_CTRL2_G_ADDR, LSM6DSM_CTRL2_G_BASE | LSM6DSM_ODR_104HZ_REG_VALUE, 30000);
            break;
#if defined(LSM6DSM_I2C_MASTER_LSM303AGR) || defined(LSM6DSM_I2C_MASTER_LIS3MDL)
        case MAGN:
            /* Enable accelerometer and sensor-hub */
            SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | LSM6DSM_ODR_104HZ_REG_VALUE);
            SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE | LSM6DSM_MASTER_CONFIG_MASTER_ON, 10000);
            T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

            uint8_t rateIndex = ARRAY_SIZE(LSM6DSMSHRates) - 2;

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_B_M_ADDR, LSM303AGR_OFFSET_CANCELLATION, SENSOR_HZ(104.0f), MAGN);
            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_C_M_ADDR,
                    LSM303AGR_CFG_REG_C_M_BASE | LSM303AGR_ENABLE_SELFTEST, SENSOR_HZ(104.0f), MAGN);

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR,
                    LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE |
                    LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(rateIndex), SENSOR_HZ(104.0f), MAGN, 200000);
#else /* LSM6DSM_I2C_MASTER_LSM303AGR */
            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR,
                    LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE, SENSOR_HZ(104.0f), MAGN);
            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR,
                    LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(rateIndex) | LIS3MDL_ENABLE_SELFTEST,
                    SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */
            break;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR, LSM6DSM_I2C_MASTER_LIS3MDL */
        default:
            return;
        }

        T(selftestState) = SELFTEST_READ_EST_DATA;
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
        break;

    case SELFTEST_READ_EST_DATA:
#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
        if (idx == MAGN)
            numberOfAverage = LSM6DSM_NUM_AVERAGE_SELFTEST_SLOW;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

        if (T(numSamplesSelftest) > 0) {
            sensorData = &T_SLAVE_INTERFACE(tmpDataBuffer[1]);
            T(dataSelftestEnabled[0]) += (int16_t)*((uint16_t *)&sensorData[0]);
            T(dataSelftestEnabled[1]) += (int16_t)*((uint16_t *)&sensorData[2]);
            T(dataSelftestEnabled[2]) += (int16_t)*((uint16_t *)&sensorData[4]);
        }
        T(numSamplesSelftest)++;

        if (T(numSamplesSelftest) <= numberOfAverage) {
            DEBUG_PRINT("runGapSelfTestProgram: reading output data while self-test is enabled\n");

            switch (idx) {
            case ACCEL:
                SPI_READ(LSM6DSM_OUTX_L_XL_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 10000);
                break;
            case GYRO:
                SPI_READ(LSM6DSM_OUTX_L_G_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 10000);
                break;
#if defined(LSM6DSM_I2C_MASTER_LSM303AGR) || defined(LSM6DSM_I2C_MASTER_LIS3MDL)
            case MAGN:
                SPI_READ(LSM6DSM_OUT_SENSORHUB1_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 20000);
                break;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR, LSM6DSM_I2C_MASTER_LIS3MDL */
            default:
                return;
            }
            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
            break;
        }

        T(dataSelftestEnabled[0]) /= numberOfAverage;
        T(dataSelftestEnabled[1]) /= numberOfAverage;
        T(dataSelftestEnabled[2]) /= numberOfAverage;
        T(selftestState) = SELFTEST_SECOND_STEP_INITIALIZATION;

    case SELFTEST_SECOND_STEP_INITIALIZATION:
        DEBUG_PRINT("runGapSelfTestProgram: second step initialization\n");

        T(numSamplesSelftest) = 0;

        /* Disable self-test */
        switch (idx) {
        case ACCEL:
        case GYRO:
            SPI_WRITE(LSM6DSM_CTRL5_C_ADDR, LSM6DSM_CTRL5_C_BASE, 30000);
            break;
#if defined(LSM6DSM_I2C_MASTER_LSM303AGR) || defined(LSM6DSM_I2C_MASTER_LIS3MDL)
        case MAGN: ;
#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_C_M_ADDR, LSM303AGR_CFG_REG_C_M_BASE, SENSOR_HZ(104.0f), MAGN, 200000);
#else /* LSM6DSM_I2C_MASTER_LSM303AGR */
            uint8_t rateIndex = ARRAY_SIZE(LSM6DSMSHRates) - 2;

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR,
                    LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(rateIndex),
                    SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */
            break;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR, LSM6DSM_I2C_MASTER_LIS3MDL */
        default:
            return;
        }

        T(selftestState) = SELFTEST_READ_NST_DATA;
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
        break;

    case SELFTEST_READ_NST_DATA:
#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
        if (idx == MAGN)
            numberOfAverage = LSM6DSM_NUM_AVERAGE_SELFTEST_SLOW;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

        if (T(numSamplesSelftest) > 0) {
            sensorData = &T_SLAVE_INTERFACE(tmpDataBuffer[1]);
            T(dataSelftestNotEnabled[0]) += (int16_t)*((uint16_t *)&sensorData[0]);
            T(dataSelftestNotEnabled[1]) += (int16_t)*((uint16_t *)&sensorData[2]);
            T(dataSelftestNotEnabled[2]) += (int16_t)*((uint16_t *)&sensorData[4]);
        }
        T(numSamplesSelftest)++;

        if (T(numSamplesSelftest) <= numberOfAverage) {
            DEBUG_PRINT("runGapSelfTestProgram: reading output data while self-test is disabled\n");

            switch (idx) {
            case ACCEL:
                SPI_READ(LSM6DSM_OUTX_L_XL_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 10000);
                break;
            case GYRO:
                SPI_READ(LSM6DSM_OUTX_L_G_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 10000);
                break;
#if defined(LSM6DSM_I2C_MASTER_LSM303AGR) || defined(LSM6DSM_I2C_MASTER_LIS3MDL)
            case MAGN:
                SPI_READ(LSM6DSM_OUT_SENSORHUB1_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 20000);
                break;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR, LSM6DSM_I2C_MASTER_LIS3MDL */
            default:
                return;
            }
            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
            break;
        }

        T(dataSelftestNotEnabled[0]) /= numberOfAverage;
        T(dataSelftestNotEnabled[1]) /= numberOfAverage;
        T(dataSelftestNotEnabled[2]) /= numberOfAverage;
        T(selftestState) = SELFTEST_VERIFICATION;

    case SELFTEST_VERIFICATION: ;
        uint8_t i, sType;
        int32_t dataGap[3];
        bool testPassed = true;
        int32_t lower_threshold[3], higher_threshold[3];

        dataGap[0] = abs(T(dataSelftestEnabled[0]) - T(dataSelftestNotEnabled[0]));
        dataGap[1] = abs(T(dataSelftestEnabled[1]) - T(dataSelftestNotEnabled[1]));
        dataGap[2] = abs(T(dataSelftestEnabled[2]) - T(dataSelftestNotEnabled[2]));

        switch (idx) {
        case ACCEL:
            sType = SENS_TYPE_ACCEL;
            lower_threshold[0] = lower_threshold[1] = lower_threshold[2] = LSM6DSM_ACCEL_SELFTEST_LOW_THR_LSB;
            higher_threshold[0] = higher_threshold[1] = higher_threshold[2] = LSM6DSM_ACCEL_SELFTEST_HIGH_THR_LSB;

            /* Power off sensor */
            SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE);
            break;
        case GYRO:
            sType = SENS_TYPE_GYRO;
            lower_threshold[0] = lower_threshold[1] = lower_threshold[2] = LSM6DSM_GYRO_SELFTEST_LOW_THR_LSB;
            higher_threshold[0] = higher_threshold[1] = higher_threshold[2] = LSM6DSM_GYRO_SELFTEST_HIGH_THR_LSB;

            /* Power off sensor */
            SPI_WRITE(LSM6DSM_CTRL2_G_ADDR, LSM6DSM_CTRL2_G_BASE);
            break;
#if defined(LSM6DSM_I2C_MASTER_LSM303AGR) || defined(LSM6DSM_I2C_MASTER_LIS3MDL)
        case MAGN:
            sType = SENS_TYPE_MAG;

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
            lower_threshold[0] = lower_threshold[1] = lower_threshold[2] = LSM303AGR_SELFTEST_LOW_THR_LSB;
            higher_threshold[0] = higher_threshold[1] = higher_threshold[2] = LSM303AGR_SELFTEST_HIGH_THR_LSB;

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_B_M_ADDR, 0x00, SENSOR_HZ(104.0f), MAGN);
#else /* LSM6DSM_I2C_MASTER_LSM303AGR */
            lower_threshold[0] = lower_threshold[1] = LIS3MDL_SELFTEST_LOW_THR_XY_LSB;
            higher_threshold[0] = higher_threshold[1] = LIS3MDL_SELFTEST_HIGH_THR_XY_LSB;
            lower_threshold[2] = LIS3MDL_SELFTEST_LOW_THR_Z_LSB;
            higher_threshold[2] = LIS3MDL_SELFTEST_HIGH_THR_Z_LSB;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

            /* Power off sensor */
            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR,
                    LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE, SENSOR_HZ(104.0f), MAGN);

            /* Disable accelerometer and sensor-hub */
            SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE);
            SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE);
            T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
            break;
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR, LSM6DSM_I2C_MASTER_LIS3MDL */
        default:
            return;
        }

        for (i = 0; i < 3; i++) {
            if ((dataGap[i] < lower_threshold[i]) || (dataGap[i] > higher_threshold[i])) {
                testPassed = false;
                ERROR_PRINT("runGapSelfTestProgram: axis-%d out of spec! test-enabled: %ldLSB ** test-disabled: %ldLSB, ** delta: %ldLSB\n",
                            i, T(dataSelftestEnabled[i]), T(dataSelftestNotEnabled[i]), dataGap[i]);
            }
        }
        INFO_PRINT("runGapSelfTestProgram: completed. Test result: %s\n", testPassed ? "pass" : "fail");

        if (testPassed)
            lsm6dsm_sendSelfTestResult(sType, SENSOR_APP_EVT_STATUS_SUCCESS);
        else
            lsm6dsm_sendSelfTestResult(sType, SENSOR_APP_EVT_STATUS_ERROR);

        T(selftestState) = SELFTEST_COMPLETED;
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
        break;

    default:
        break;
    }
}

/*
 * lsm6dsm_convertAccelOffsetValue: convert accel LSB value to offset digit
 * @val: LSB axis offset value.
 */
static uint8_t lsm6dsm_convertAccelOffsetValue(int32_t val)
{
    float temp;

    temp = val * LSM6DSM_ACCEL_LSB_TO_OFFSET_DIGIT_SCALE;
    if (temp > LSM6DSM_ACCEL_MAX_CALIBRATION_THR_LSB)
        temp = LSM6DSM_ACCEL_MAX_CALIBRATION_THR_LSB;

    if (temp < -LSM6DSM_ACCEL_MAX_CALIBRATION_THR_LSB)
        temp = -LSM6DSM_ACCEL_MAX_CALIBRATION_THR_LSB;

    return (uint8_t)((int8_t)temp);
}

/*
 * lsm6dsm_runCalibrationProgram: state machine that is executing calibration
 * @idx: sensor driver index.
 */
static void lsm6dsm_runCalibrationProgram(enum SensorIndex idx)
{
    TDECL();
    uint8_t *sensorData, numberOfAverage;
    uint8_t buffer[LSM6DSM_TRIAXIAL_NUM_AXIS] = { 0 };

    numberOfAverage = LSM6DSM_NUM_AVERAGE_CALIBRATION;

    switch (T(calibrationState)) {
    case CALIBRATION_INITIALIZATION:
        DEBUG_PRINT("runCalibrationProgram: initialization\n");

        T(numSamplesCalibration) = 0;
        memset(T(dataCalibration), 0, LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));

        /* Power on sensor */
        switch (idx) {
        case ACCEL:
            SPI_MULTIWRITE(LSM6DSM_X_OFS_USR_ADDR, buffer, LSM6DSM_TRIAXIAL_NUM_AXIS, 500);
            SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | LSM6DSM_ODR_104HZ_REG_VALUE, 30000);
            break;
        case GYRO:
            SPI_WRITE(LSM6DSM_CTRL2_G_ADDR, LSM6DSM_CTRL2_G_BASE | LSM6DSM_ODR_104HZ_REG_VALUE, 100000);
            break;
        default:
            return;
        }

        T(calibrationState) = CALIBRATION_READ_DATA;
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
        break;

    case CALIBRATION_READ_DATA:
        if (T(numSamplesCalibration) > 0) {
            sensorData = &T_SLAVE_INTERFACE(tmpDataBuffer[1]);
            T(dataCalibration[0]) += (int16_t)*((uint16_t *)&sensorData[0]);
            T(dataCalibration[1]) += (int16_t)*((uint16_t *)&sensorData[2]);
            T(dataCalibration[2]) += (int16_t)*((uint16_t *)&sensorData[4]);
        }
        T(numSamplesCalibration)++;

        if (T(numSamplesCalibration) <= numberOfAverage) {
            DEBUG_PRINT("runCalibrationProgram: reading output data\n");

            switch (idx) {
            case ACCEL:
                SPI_READ(LSM6DSM_OUTX_L_XL_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 10000);
                break;
            case GYRO:
                SPI_READ(LSM6DSM_OUTX_L_G_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 10000);
                break;
            default:
                return;
            }
            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
            break;
        }

        T(dataCalibration[0]) /= numberOfAverage;
        T(dataCalibration[1]) /= numberOfAverage;
        T(dataCalibration[2]) /= numberOfAverage;
        T(calibrationState) = CALIBRATION_VERIFICATION;

    case CALIBRATION_VERIFICATION: ;
        uint8_t sType;

        switch (idx) {
        case ACCEL:
            sType = SENS_TYPE_ACCEL;

            /* Power off sensor */
            SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE);

            /* Supposed 0,0,1g (Android coordinate system) */
            T(dataCalibration[0]) = -T(dataCalibration[0]);
            T(dataCalibration[1]) = -T(dataCalibration[1]);
            T(dataCalibration[2]) = T(dataCalibration[2]) - LSM6DSM_1G_IN_LSB_CALIBRATION;

            for (int8_t i = 0; i < LSM6DSM_TRIAXIAL_NUM_AXIS; i++)
                buffer[i] = lsm6dsm_convertAccelOffsetValue(T(dataCalibration[i]));

            SPI_MULTIWRITE(LSM6DSM_X_OFS_USR_ADDR, buffer, LSM6DSM_TRIAXIAL_NUM_AXIS);
            break;
        case GYRO:
            sType = SENS_TYPE_GYRO;

            /* Power off sensor */
            SPI_WRITE(LSM6DSM_CTRL2_G_ADDR, LSM6DSM_CTRL2_G_BASE);

            memcpy(T(gyroCalibrationData), T(dataCalibration), LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));
            break;
        default:
            return;
        }

        INFO_PRINT("runCalibrationProgram: completed. offset [LSB]: %ld %ld %ld\n", T(dataCalibration[0]), T(dataCalibration[1]), T(dataCalibration[2]));
        lsm6dsm_sendCalibrationResult(sType, SENSOR_APP_EVT_STATUS_SUCCESS, T(dataCalibration[0]), T(dataCalibration[1]), T(dataCalibration[2]));

        T(calibrationState) = CALIBRATION_COMPLETED;
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[idx]), __FUNCTION__);
        break;

    default:
        break;
    }
}


#ifdef LSM6DSM_I2C_MASTER_AK09916
/*
 * lsm6dsm_runAbsoluteSelfTestProgram: state machine that is executing self-test verifying absolute value
 */
static void lsm6dsm_runAbsoluteSelfTestProgram(void)
{
    TDECL();
    uint8_t *sensorData;

    switch (T(selftestState)) {
    case SELFTEST_INITIALIZATION: ;
        DEBUG_PRINT("runAbsoluteSelfTestProgram: initialization\n");

        T(numSamplesSelftest) = 0;
        memset(T(dataSelftestEnabled), 0, LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));

        /* Enable accelerometer and sensor-hub */
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | LSM6DSM_ODR_104HZ_REG_VALUE);
        SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE | LSM6DSM_MASTER_CONFIG_MASTER_ON, 20000);
        T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

        SPI_WRITE_SLAVE_SENSOR_REGISTER(AK09916_CNTL2_ADDR, AK09916_ENABLE_SELFTEST_MODE, SENSOR_HZ(104.0f), MAGN, 20000);

        T(selftestState) = SELFTEST_READ_EST_DATA;
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
        break;

    case SELFTEST_READ_EST_DATA:
        if (T(numSamplesSelftest) > 0) {
            sensorData = &T_SLAVE_INTERFACE(tmpDataBuffer[1]);
            T(dataSelftestEnabled[0]) += (int16_t)*((uint16_t *)&sensorData[0]);
            T(dataSelftestEnabled[1]) += (int16_t)*((uint16_t *)&sensorData[2]);
            T(dataSelftestEnabled[2]) += (int16_t)*((uint16_t *)&sensorData[4]);
        }
        T(numSamplesSelftest)++;

        if (T(numSamplesSelftest) <= LSM6DSM_NUM_AVERAGE_SELFTEST) {
            DEBUG_PRINT("runAbsoluteSelfTestProgram: reading output data while self-test is enabled\n");

            SPI_READ(LSM6DSM_OUT_SENSORHUB1_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tmpDataBuffer), 20000);
            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
            break;
        }

        T(dataSelftestEnabled[0]) /= LSM6DSM_NUM_AVERAGE_SELFTEST;
        T(dataSelftestEnabled[1]) /= LSM6DSM_NUM_AVERAGE_SELFTEST;
        T(dataSelftestEnabled[2]) /= LSM6DSM_NUM_AVERAGE_SELFTEST;
        T(selftestState) = SELFTEST_VERIFICATION;

    case SELFTEST_VERIFICATION: ;
        bool testPassed = true;

        if ((T(dataSelftestEnabled[0]) < AK09916_SELFTEST_LOW_THR_XY_LSB) ||
                (T(dataSelftestEnabled[0]) > AK09916_SELFTEST_HIGH_THR_XYZ_LSB)) {
            testPassed = false;
            ERROR_PRINT("runAbsoluteSelfTestProgram: axis-0 out of spec! Read: %ldLSB\n", T(dataSelftestEnabled[0]));
        }
        if ((T(dataSelftestEnabled[1]) < AK09916_SELFTEST_LOW_THR_XY_LSB) ||
                (T(dataSelftestEnabled[1]) > AK09916_SELFTEST_HIGH_THR_XYZ_LSB)) {
            testPassed = false;
            ERROR_PRINT("runAbsoluteSelfTestProgram: axis-1 out of spec! Read: %ldLSB\n", T(dataSelftestEnabled[1]));
        }
        if ((T(dataSelftestEnabled[2]) < AK09916_SELFTEST_LOW_THR_Z_LSB) ||
                (T(dataSelftestEnabled[2]) > AK09916_SELFTEST_HIGH_THR_XYZ_LSB)) {
            testPassed = false;
            ERROR_PRINT("runAbsoluteSelfTestProgram: axis-2 out of spec! Read: %ldLSB\n", T(dataSelftestEnabled[2]));
        }

        INFO_PRINT("runAbsoluteSelfTestProgram: completed. Test result: %s\n", testPassed ? "pass" : "fail");

        if (testPassed)
            lsm6dsm_sendSelfTestResult(SENS_TYPE_MAG, SENSOR_APP_EVT_STATUS_SUCCESS);
        else
            lsm6dsm_sendSelfTestResult(SENS_TYPE_MAG, SENSOR_APP_EVT_STATUS_ERROR);

        /* Disable accelerometer and sensor-hub */
        SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE);
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE);
        T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;

        T(selftestState) = SELFTEST_COMPLETED;
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
        break;

    default:
        break;
    }
}
#endif /* LSM6DSM_I2C_MASTER_AK09916 */

/*
 * lsm6dsm_writeEmbeddedRegister: write embedded register
 * @addr: address of register to be written.
 * @value: value to write.
 */
static void lsm6dsm_writeEmbeddedRegister(uint8_t addr, uint8_t value)
{
    TDECL();

#ifdef LSM6DSM_I2C_MASTER_ENABLED
    SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE);
#endif /* LSM6DSM_I2C_MASTER_ENABLED */
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister) & ~LSM6DSM_ENABLE_DIGITAL_FUNC, 3000);
    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

    SPI_WRITE(addr, value);

    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
#endif /* LSM6DSM_I2C_MASTER_ENABLED */
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
}

#ifdef LSM6DSM_I2C_MASTER_ENABLED
/*
 * lsm6dsm_writeSlaveRegister: write I2C slave register using sensor-hub feature
 * @addr: address of register to be written.
 * @value: value to write.
 * @accelRate: sensor-hub is using accel odr as trigger. This is current accel odr value.
 * @delay: perform a delay after write is completed.
 * @si: which slave sensor needs to be written.
 */
static void lsm6dsm_writeSlaveRegister(uint8_t addr, uint8_t value, uint32_t accelRate, uint32_t delay, enum SensorIndex si)
{
    TDECL();
    uint8_t slave_addr, buffer[3];
    uint32_t SHOpCompleteTime;

    switch (si) {
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    case MAGN:
        slave_addr = LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT;
        break;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    case PRESS:
    case TEMP:
        slave_addr = LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT;
        break;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    default:
        return;
    }

    if (accelRate > SENSOR_HZ(104.0f))
        SHOpCompleteTime = SENSOR_HZ_RATE_TO_US(SENSOR_HZ(104.0f));
    else
        SHOpCompleteTime = SENSOR_HZ_RATE_TO_US(accelRate);

    /* Perform write to slave sensor and wait write is done (1 accel ODR) */
    SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE);
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister) & ~LSM6DSM_ENABLE_DIGITAL_FUNC, 3000);
    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

    buffer[0] = slave_addr << 1;                                     /* LSM6DSM_EMBEDDED_SLV0_ADDR */
    buffer[1] = addr;                                                /* LSM6DSM_EMBEDDED_SLV0_SUBADDR */
    buffer[2] = LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONLY_WRITE;         /* LSM6DSM_EMBEDDED_SLV0_CONFIG */
    SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR, buffer, 3);
    SPI_WRITE(LSM6DSM_EMBEDDED_DATAWRITE_SLV0_ADDR, value);

    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
    SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister), (3 * SHOpCompleteTime) / 2);

    /* After write is completed slave 0 must be set to sleep mode */
    SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE);
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister) & ~LSM6DSM_ENABLE_DIGITAL_FUNC, 3000);
    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

    buffer[0] = LSM6DSM_EMBEDDED_SLV0_WRITE_ADDR_SLEEP;              /* LSM6DSM_EMBEDDED_SLV0_ADDR */
    buffer[1] = addr;                                                /* LSM6DSM_EMBEDDED_SLV0_SUBADDR */
    buffer[2] = LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE;               /* LSM6DSM_EMBEDDED_SLV0_CONFIG */
    SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR, buffer, 3);

    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
    SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
}
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

/*
 * lsm6dsm_computeOdr: get index of LSM6DSMImuRates array based on selected rate
 * @rate: ODR value expressed in SENSOR_HZ(x).
 */
static uint8_t lsm6dsm_computeOdr(uint32_t rate)
{
    int i;

    for (i = 0; i < (ARRAY_SIZE(LSM6DSMImuRates) - 1); i++) {
        if (LSM6DSMImuRates[i] == rate)
            break;
    }
    if (i == (ARRAY_SIZE(LSM6DSMImuRates) - 1)) {
        ERROR_PRINT("computeOdr: ODR not valid! Selected smallest ODR available\n");
        i = 0;
    }

    return i;
}

/*
 * lsm6dsm_sensorHzToNs: return delta time of specifi sensor rate
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 */
static uint32_t lsm6dsm_sensorHzToNs(uint32_t rate)
{
    int i;

    for (i = 0; i < (ARRAY_SIZE(LSM6DSMImuRates) - 1); i++) {
        if (LSM6DSMImuRates[i] == rate)
            break;
    }
    if (i == (ARRAY_SIZE(LSM6DSMImuRates) - 1)) {
        ERROR_PRINT("sensorHzToNs: rate not available. Selected smaller rate\n");
        i = 0;
    }

    return LSM6DSMImuRatesInNs[i];
}

/*
 * lsm6dsm_decimatorToFifoDecimatorReg: get decimator reg value based on decimation factor
 * @dec: FIFO sample decimation factor.
 */
static uint8_t lsm6dsm_decimatorToFifoDecimatorReg(uint8_t dec)
{
    uint8_t regValue;

    switch (dec) {
    case 1:
        regValue = LSM6DSM_FIFO_NO_DECIMATION;
        break;
    case 2:
        regValue = LSM6DSM_FIFO_DECIMATION_FACTOR_2;
        break;
    case 3:
        regValue = LSM6DSM_FIFO_DECIMATION_FACTOR_3;
        break;
    case 4:
        regValue = LSM6DSM_FIFO_DECIMATION_FACTOR_4;
        break;
    case 8:
        regValue = LSM6DSM_FIFO_DECIMATION_FACTOR_8;
        break;
    case 16:
        regValue = LSM6DSM_FIFO_DECIMATION_FACTOR_16;
        break;
    case 32:
        regValue = LSM6DSM_FIFO_DECIMATION_FACTOR_32;
        break;
    default:
        regValue = LSM6DSM_FIFO_SAMPLE_NOT_IN_FIFO;
        break;
    }

    return regValue;
}

/*
 * lsm6dsm_calculateFifoDecimators: calculate fifo decimators
 * @RequestedRate: list of ODRs requested by driver for each sensor in FIFO.
 * @minLatency: the function will set the min latency based on all sensors enabled in FIFO.
 */
static bool lsm6dsm_calculateFifoDecimators(uint32_t RequestedRate[FIFO_NUM], uint64_t *minLatency)
{
    TDECL();
    uint8_t i, n, tempDec, decimators[FIFO_NUM] = { 0 }, minDec = UINT8_MAX, maxDec = 0;
    enum SensorIndex sidx;
    bool changed = false;

    T(fifoCntl).triggerRate = T(sensors[ACCEL]).hwRate;
    if (T(sensors[GYRO]).hwRate > T(fifoCntl).triggerRate)
        T(fifoCntl).triggerRate = T(sensors[GYRO]).hwRate;

    for (i = 0; i < FIFO_NUM; i++) {
        sidx = T(fifoCntl).decimatorsIdx[i];
        if (sidx >= NUM_SENSORS)
            continue;

        if (T(sensors[i]).latency < *minLatency)
            *minLatency = T(sensors[i]).latency;
    }

    for (i = 0; i < FIFO_NUM; i++) {
        sidx = T(fifoCntl).decimatorsIdx[i];
        if (sidx >= NUM_SENSORS)
            continue;

        if (RequestedRate[i]) {
            decimators[i] = (T(fifoCntl).triggerRate / RequestedRate[i]) <= 32 ? (T(fifoCntl).triggerRate / RequestedRate[i]) : 32;

            tempDec = decimators[i];
            while (decimators[i] > 1) {
                if (((uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * decimators[i]) > *minLatency)
                    decimators[i] /= 2;
                else
                    break;
            }
            T(sensors[sidx]).samplesFifoDecimator = tempDec / decimators[i];
            T(sensors[sidx]).samplesFifoDecimatorCounter = T(sensors[sidx]).samplesFifoDecimator - 1;

            if (decimators[i] < minDec)
                minDec = decimators[i];

            if (decimators[i] > maxDec)
                maxDec = decimators[i];
        }

        DEBUG_PRINT("calculateFifoDecimators: sensorIndex=%d, fifo decimator=%d, software decimation=%d\n", sidx, decimators[i], T(sensors[sidx]).samplesFifoDecimator);

        if (T(fifoCntl).decimators[i] != decimators[i]) {
            T(fifoCntl).decimators[i] = decimators[i];
            changed = true;
        }
    }

    /* Embedded timestamp slot */
    T(fifoCntl).decimators[FIFO_DS4] = minDec;

    T(fifoCntl).minDecimator = minDec;
    T(fifoCntl).maxDecimator = maxDec;
    T(fifoCntl).maxMinDecimator = maxDec / minDec;
    T(fifoCntl).totalSip = 0;

    if (maxDec > 0) {
        T(time).theoreticalDeltaTimeLSB = cpuMathU64DivByU16((uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * T(fifoCntl).minDecimator, LSM6DSM_TIME_RESOLUTION);
        T(time).deltaTimeMarginLSB = ((T(time).theoreticalDeltaTimeLSB) * 10) / 100;

        for (i = 0; i < FIFO_NUM; i++) {
            if (T(fifoCntl).decimators[i] > 0)
                T(fifoCntl).totalSip += (maxDec / T(fifoCntl).decimators[i]);
        }
    }

    DEBUG_PRINT("calculateFifoDecimators: samples in pattern=%d\n", T(fifoCntl).totalSip);

    for (i = 0; i < T(fifoCntl).maxMinDecimator; i++) {
        T(fifoCntl).timestampPosition[i] = 0;
        for (n = 0; n < FIFO_NUM - 1; n++) {
            if ((T(fifoCntl).decimators[n] > 0) && ((i % (T(fifoCntl).decimators[n] / T(fifoCntl).minDecimator)) == 0))
                T(fifoCntl).timestampPosition[i] += LSM6DSM_ONE_SAMPLE_BYTE;
        }
    }

    return changed;
}

/*
 * lsm6dsm_calculateWatermark: calculate fifo watermark level
 * @minLatency: min latency requested by system based on all sensors in FIFO.
 */
static bool lsm6dsm_calculateWatermark(uint64_t *minLatency)
{
    TDECL();
    uint64_t patternRate, tempLatency;
    uint16_t watermark;
    uint16_t i = 1;

    if (T(fifoCntl).totalSip > 0) {
        patternRate = (uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * T(fifoCntl).maxDecimator;

        do {
            tempLatency = patternRate * (++i);
        } while ((tempLatency < *minLatency) && (i <= LSM6DSM_MAX_WATERMARK_VALUE));

        watermark = (i - 1) * T(fifoCntl).totalSip;

        while (watermark > LSM6DSM_MAX_WATERMARK_VALUE) {
            watermark /= 2;
            watermark = watermark - (watermark % T(fifoCntl).totalSip);
        }

        DEBUG_PRINT("calculateWatermark: level=#%d, min latency=%lldns\n", watermark, *minLatency);

        if (T(fifoCntl).watermark != watermark) {
            T(fifoCntl).watermark = watermark;
            return true;
        }
    }

   return false;
}

/*
 * lsm6dsm_resetTimestampSync: reset all variables used by sync timestamp task
 */
static inline void lsm6dsm_resetTimestampSync(void)
{
    TDECL();

    T(lastFifoReadTimestamp) = 0;

    T(time).sampleTimestampFromFifoLSB = 0;
    T(time).timestampIsValid = false;
    T(time).lastSampleTimestamp = 0;
    T(time).noTimer.lastTimestampDataAvlRtcTime = 0;
    T(time).noTimer.newTimestampDataAvl = false;
    T(time).timestampSyncTaskLSB = 0;

    time_sync_reset(&T(time).sensorTimeToRtcData);
}

/*
 * lsm6dsm_updateSyncTaskMode: set the best way to sync timestamp
 * @minLatency: min latency of sensors using FIFO.
 */
static inline void lsm6dsm_updateSyncTaskMode(uint64_t *minLatency)
{
    TDECL();

    /* If minLatency is `small` do not use timer to read timestamp and
        temperature but read it during FIFO read. */
    if (*minLatency < LSM6DSM_SYNC_DELTA_INTERVAL) {
        T(time).status = TIME_SYNC_DURING_FIFO_READ;
    } else {
        T(time).status = TIME_SYNC_TIMER;

        if (!osEnqueuePrivateEvt(EVT_TIME_SYNC, 0, NULL, mTask.tid)) {
            T(pendingTimeSyncTask) = true;
            ERROR_PRINT("updateSyncTaskMode: failed to enqueue time sync event\n");
        }
    }
}

/*
 * lsm6dsm_updateOdrs: update ODRs based on rates
 */
static bool lsm6dsm_updateOdrs(void)
{
    TDECL();
    bool accelOdrChanged = false, gyroOdrChanged = false, decChanged, watermarkChanged, gyroFirstEnable = false;
    uint32_t maxRate, maxPushDataRate[FIFO_NUM] = { 0 };
    uint64_t minLatency = UINT64_MAX;
    uint8_t i, regValue, buffer[5];
    uint16_t watermarkReg;

    maxRate = 0;

    /* Verify accel odr */
    for (i = 0; i < NUM_SENSORS; i++) {
        if (T(sensors[ACCEL]).rate[i] > maxRate)
            maxRate = T(sensors[ACCEL]).rate[i] < SENSOR_HZ(26.0f / 2.0f) ? SENSOR_HZ(26.0f / 2.0f) : T(sensors[ACCEL]).rate[i];

        if ((T(sensors[ACCEL]).rate[i] > maxPushDataRate[FIFO_ACCEL]) && T(sensors[ACCEL]).dependenciesRequireData[i])
            maxPushDataRate[FIFO_ACCEL] = T(sensors[ACCEL]).rate[i];
    }
    if (T(sensors[ACCEL]).hwRate != maxRate) {
        T(sensors[ACCEL]).hwRate = maxRate;
        accelOdrChanged = true;
    }

    maxRate = 0;

    /* Verify gyro odr */
    for (i = 0; i < NUM_SENSORS; i++) {
        if (T(sensors[GYRO]).rate[i] > maxRate)
            maxRate = T(sensors[GYRO]).rate[i] < SENSOR_HZ(26.0f / 2.0f) ? SENSOR_HZ(26.0f / 2.0f) : T(sensors[GYRO]).rate[i];

        if (T(sensors[GYRO]).rate[i] > maxPushDataRate[FIFO_GYRO])
            maxPushDataRate[FIFO_GYRO] = T(sensors[GYRO]).rate[i];
    }
    if (T(sensors[GYRO]).hwRate != maxRate) {
        /* If gyro is enabled from PowerDown more samples needs to be discarded */
        if (T(sensors[GYRO]).hwRate == 0)
            gyroFirstEnable = true;

        T(sensors[GYRO]).hwRate = maxRate;
        gyroOdrChanged = true;
    }

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    /* If magnetometer is enabled, FIFO is used for it */
    maxPushDataRate[FIFO_DS3] = T(sensors[MAGN]).rate[MAGN];
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#if defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) && !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
    /* If magnetometer is not available FIFO can be used to store barometer sensor data */
    maxPushDataRate[FIFO_DS3] = T(sensors[PRESS]).rate[PRESS] > T(sensors[TEMP]).rate[TEMP] ? T(sensors[PRESS]).rate[PRESS] : T(sensors[TEMP]).rate[TEMP];
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED, LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

    decChanged = lsm6dsm_calculateFifoDecimators(maxPushDataRate, &minLatency);
    watermarkChanged = lsm6dsm_calculateWatermark(&minLatency);
    watermarkReg = T(fifoCntl).watermark * 3;

    if (accelOdrChanged || gyroOdrChanged || decChanged) {
        /* read all FIFO content and disable it */
        DEBUG_PRINT("updateOdrs: disabling FIFO\n");
        T(time).status = TIME_SYNC_DISABLED;

        SPI_WRITE(LSM6DSM_TIMESTAMP2_REG_ADDR, LSM6DSM_TIMESTAMP2_REG_RESET_TIMESTAMP);
        SPI_WRITE(LSM6DSM_FIFO_CTRL5_ADDR, LSM6DSM_FIFO_BYPASS_MODE, 25);
    }

    if (accelOdrChanged) {
        if (T(sensors[ACCEL]).hwRate == 0) {
            DEBUG_PRINT("updateOdrs: no one is using accel, disabling it\n");
            regValue = 0;
        } else {
            DEBUG_PRINT("updateOdrs: accel in use, updating odr to %dHz\n", (int)(T(sensors[ACCEL]).hwRate / 1024));
            i = lsm6dsm_computeOdr(T(sensors[ACCEL]).hwRate);
            regValue = LSM6DSMImuRatesRegValue[i];
            T(sensors[ACCEL]).samplesToDiscard = LSM6DSMAccelRatesSamplesToDiscard[i] /
                                                    (T(sensors[ACCEL]).hwRate / (T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_ACCEL]));

            if (T(sensors[ACCEL]).samplesToDiscard == 0)
                T(sensors[ACCEL]).samplesToDiscard = 1;

            T(sensors[ACCEL]).samplesDecimator = ((T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_ACCEL]) /
                                                    T(sensors[ACCEL]).samplesFifoDecimator) / T(sensors[ACCEL]).rate[ACCEL];
            T(sensors[ACCEL]).samplesDecimatorCounter = T(sensors[ACCEL]).samplesDecimator - 1;
        }
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | regValue, 30);
    }

    if (gyroOdrChanged) {
        if (T(sensors[GYRO]).hwRate == 0) {
            DEBUG_PRINT("updateOdrs: no one is using gyro, disabling it\n");
            regValue = 0;
        } else {
            DEBUG_PRINT("updateOdrs: gyro in use, updating odr to %dHz\n", (int)(T(sensors[GYRO]).hwRate / 1024));
            i = lsm6dsm_computeOdr(T(sensors[GYRO]).hwRate);
            regValue = LSM6DSMImuRatesRegValue[i];
            T(sensors[GYRO]).samplesToDiscard = LSM6DSMGyroRatesSamplesToDiscard[i];

            if (gyroFirstEnable)
                T(sensors[GYRO]).samplesToDiscard += LSM6DSMRatesSamplesToDiscardGyroPowerOn[i];

            T(sensors[GYRO]).samplesToDiscard /= (T(sensors[GYRO]).hwRate / (T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_GYRO]));

            if (T(sensors[GYRO]).samplesToDiscard == 0)
                T(sensors[GYRO]).samplesToDiscard = 1;

            T(sensors[GYRO]).samplesDecimator = ((T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_GYRO]) /
                                                    T(sensors[GYRO]).samplesFifoDecimator) / T(sensors[GYRO]).rate[GYRO];
            T(sensors[GYRO]).samplesDecimatorCounter = T(sensors[GYRO]).samplesDecimator - 1;
        }
        SPI_WRITE(LSM6DSM_CTRL2_G_ADDR, LSM6DSM_CTRL2_G_BASE | regValue, 30);
    }

    /* Program Fifo and enable or disable it */
    if (accelOdrChanged || gyroOdrChanged || decChanged) {
        buffer[0] = *((uint8_t *)&watermarkReg);
        buffer[1] = (*((uint8_t *)&watermarkReg + 1) & LSM6DSM_FIFO_CTRL2_FTH_MASK) | LSM6DSM_ENABLE_FIFO_TIMESTAMP;
        buffer[2] = (lsm6dsm_decimatorToFifoDecimatorReg(T(fifoCntl).decimators[FIFO_GYRO]) << 3) |
                    lsm6dsm_decimatorToFifoDecimatorReg(T(fifoCntl).decimators[FIFO_ACCEL]);
        buffer[3] = (lsm6dsm_decimatorToFifoDecimatorReg(T(fifoCntl).decimators[FIFO_DS4]) << 3) |
                    lsm6dsm_decimatorToFifoDecimatorReg(T(fifoCntl).decimators[FIFO_DS3]);

        for (i = 0; i < FIFO_NUM - 1; i++) {
            if (T(fifoCntl).decimators[i] > 0)
                break;
        }
        if (i < (FIFO_NUM - 1)) {
            /* Someone want to use FIFO */
            DEBUG_PRINT("updateOdrs: enabling FIFO in continuos mode\n");
            buffer[4] = LSM6DSM_FIFO_CONTINUOS_MODE;

            lsm6dsm_resetTimestampSync();
            lsm6dsm_updateSyncTaskMode(&minLatency);
        } else {
            /* No one is using FIFO */
            buffer[4] = LSM6DSM_FIFO_BYPASS_MODE;

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
            if ((T(sensors[PRESS]).rate[PRESS] > 0) || (T(sensors[TEMP]).rate[TEMP] > 0)) {
                uint64_t latencyOnlyBaro = LSM6DSM_SYNC_DELTA_INTERVAL;
                lsm6dsm_resetTimestampSync();
                lsm6dsm_updateSyncTaskMode(&latencyOnlyBaro);
            }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
        }

        SPI_MULTIWRITE(LSM6DSM_FIFO_CTRL1_ADDR, buffer, 5);
    } else {
        if (watermarkChanged) {
            lsm6dsm_updateSyncTaskMode(&minLatency);

            buffer[0] = *((uint8_t *)&watermarkReg);
            buffer[1] = (*((uint8_t *)&watermarkReg + 1) & LSM6DSM_FIFO_CTRL2_FTH_MASK) | LSM6DSM_ENABLE_FIFO_TIMESTAMP;
            SPI_MULTIWRITE(LSM6DSM_FIFO_CTRL1_ADDR, buffer, 2);
        }
    }

    if (accelOdrChanged || gyroOdrChanged || decChanged || watermarkChanged)
        return true;

    return false;
}

/*
 * lsm6dsm_setAccelPower: enable/disable accelerometer sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setAccelPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setAccelPower: %s\n", on ? "enable" : "disable");

        if (on)
            osEnqueuePrivateEvt(EVT_SENSOR_POWERING_UP, &T(sensors[ACCEL]), NULL, mTask.tid);
        else {
            T(sensors[ACCEL]).rate[ACCEL] = 0;
            T(sensors[ACCEL]).latency = UINT64_MAX;

            if (lsm6dsm_updateOdrs())
                lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[ACCEL]), __FUNCTION__);
            else
                osEnqueuePrivateEvt(EVT_SENSOR_POWERING_DOWN, &T(sensors[ACCEL]), NULL, mTask.tid);
        }
    } else {
        T(pendingEnableConfig[ACCEL]) = true;
        T(sensors[ACCEL]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setGyroPower: enable/disable gyroscope sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setGyroPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setGyroPower: %s\n", on ? "enable" : "disable");

        if (on)
            osEnqueuePrivateEvt(EVT_SENSOR_POWERING_UP, &T(sensors[GYRO]), NULL, mTask.tid);
        else {
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
            T(sensors[ACCEL]).rate[GYRO] = 0;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
            T(sensors[GYRO]).rate[GYRO] = 0;
            T(sensors[GYRO]).latency = UINT64_MAX;

            if (lsm6dsm_updateOdrs()) {
                lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[GYRO]), __FUNCTION__);
            } else
                osEnqueuePrivateEvt(EVT_SENSOR_POWERING_DOWN, &T(sensors[GYRO]), NULL, mTask.tid);
        }
    } else {
        T(pendingEnableConfig[GYRO]) = true;
        T(sensors[GYRO]).pConfig.enable = on;
    }

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_setMagnPower: enable/disable magnetometer sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setMagnPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setMagnPower: %s\n", on ? "enable" : "disable");

        if (on) {
            if (T(masterConfigDependencies) != 0) {
                T(masterConfigDependencies) |= BIT(MAGN);

                osEnqueuePrivateEvt(EVT_SENSOR_POWERING_UP, &T(sensors[MAGN]), NULL, mTask.tid);
            } else {
                T(masterConfigDependencies) |= BIT(MAGN);
                T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));

                lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
            }
        } else {
            T(masterConfigDependencies) &= ~BIT(MAGN);

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR,
                    LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE,
                    T(sensors[ACCEL]).hwRate, MAGN);

            if (T(masterConfigDependencies) == 0) {
                DEBUG_PRINT("setMagnPower: no sensors enabled on i2c master, disabling it\n");
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }

            T(sensors[ACCEL]).rate[MAGN] = 0;
            T(sensors[MAGN]).rate[MAGN] = 0;
            T(sensors[MAGN]).latency = UINT64_MAX;
            T(sensors[MAGN]).hwRate = 0;

            lsm6dsm_updateOdrs();

            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
        }
    } else {
        T(pendingEnableConfig[MAGN]) = true;
        T(sensors[MAGN]).pConfig.enable = on;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_setPressPower: enable/disable pressure sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setPressPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setPressPower: %s\n", on ? "enable" : "disable");

        if (on) {
            if (T(masterConfigDependencies) != 0) {
                T(masterConfigDependencies) |= BIT(PRESS);

                osEnqueuePrivateEvt(EVT_SENSOR_POWERING_UP, &T(sensors[PRESS]), NULL, mTask.tid);
            } else {
                T(masterConfigDependencies) |= BIT(PRESS);
                T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));

                lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[PRESS]), __FUNCTION__);
            }
        } else {
            uint8_t i, reg_value = LSM6DSM_SENSOR_SLAVE_BARO_POWER_BASE;

            T(masterConfigDependencies) &= ~BIT(PRESS);

            if (T(sensors[TEMP]).enabled) {
                i = lsm6dsm_computeOdr(T(sensors[TEMP]).rate[TEMP]);
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i);
            } else
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE;

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_POWER_ADDR, reg_value,
                    T(sensors[ACCEL]).hwRate, PRESS);

            if (T(masterConfigDependencies) == 0) {
                DEBUG_PRINT("setPressPower: no sensors enabled on i2c master, disabling it\n");
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
            if (T(baroTimerId)) {
                timTimerCancel(T(baroTimerId));
                T(baroTimerId) = 0;

                T(pendingBaroTimerTask) = false;
                T(time).timestampBaroLSB = 0;

                if (T(sensors[TEMP]).enabled)
                    T(baroTimerId) = timTimerSet(lsm6dsm_sensorHzToNs(T(sensors[TEMP]).rate[TEMP]), 0, 50, lsm6dsm_baroTimerCallback, NULL, false);
            }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

            T(sensors[ACCEL]).rate[PRESS] = 0;
            T(sensors[PRESS]).rate[PRESS] = 0;
            T(sensors[PRESS]).latency = UINT64_MAX;
            T(sensors[PRESS]).hwRate = 0;

            lsm6dsm_updateOdrs();

            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[PRESS]), __FUNCTION__);
        }
    } else {
        T(pendingEnableConfig[PRESS]) = true;
        T(sensors[PRESS]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setTempPower: enable/disable temperature sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setTempPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setTempPower: %s\n", on ? "enable" : "disable");

        if (on) {
            if (T(masterConfigDependencies) != 0) {
                T(masterConfigDependencies) |= BIT(TEMP);

                osEnqueuePrivateEvt(EVT_SENSOR_POWERING_UP, &T(sensors[TEMP]), NULL, mTask.tid);
            } else {
                T(masterConfigDependencies) |= BIT(TEMP);
                T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));

                lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[TEMP]), __FUNCTION__);
            }
        } else {
            uint8_t i, reg_value = LSM6DSM_SENSOR_SLAVE_BARO_POWER_BASE;

            T(masterConfigDependencies) &= ~BIT(TEMP);

            if (T(sensors[PRESS]).enabled) {
                i = lsm6dsm_computeOdr(T(sensors[PRESS]).rate[PRESS]);
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i);
            } else
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE;

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_POWER_ADDR, reg_value,
                    T(sensors[ACCEL]).hwRate, TEMP);

            if (T(masterConfigDependencies) == 0) {
                DEBUG_PRINT("setTempPower: no sensors enabled on i2c master, disabling it\n");
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
            if (T(baroTimerId)) {
                timTimerCancel(T(baroTimerId));
                T(baroTimerId) = 0;

                T(pendingBaroTimerTask) = false;
                T(time).timestampBaroLSB = 0;

                if (T(sensors[PRESS]).enabled)
                    T(baroTimerId) = timTimerSet(lsm6dsm_sensorHzToNs(T(sensors[PRESS]).rate[PRESS]), 0, 50, lsm6dsm_baroTimerCallback, NULL, false);
            }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

            T(sensors[ACCEL]).rate[TEMP] = 0;
            T(sensors[TEMP]).rate[TEMP] = 0;
            T(sensors[TEMP]).latency = UINT64_MAX;
            T(sensors[TEMP]).hwRate = 0;

            lsm6dsm_updateOdrs();

            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[TEMP]), __FUNCTION__);
        }
    } else {
        T(pendingEnableConfig[TEMP]) = true;
        T(sensors[TEMP]).pConfig.enable = on;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_setStepDetectorPower: enable/disable step detector sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setStepDetectorPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setStepDetectorPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(pedometerDependencies) |= BIT(STEP_DETECTOR);
            T(embeddedFunctionsRegister) |= LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC;
            T(int1Register) |= LSM6DSM_INT_STEP_DETECTOR_ENABLE_REG_VALUE;

            T(sensors[ACCEL]).rate[STEP_DETECTOR] = SENSOR_HZ(26.0f);
            lsm6dsm_updateOdrs();

            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
        } else {
            T(pedometerDependencies) &= ~BIT(STEP_DETECTOR);
            T(int1Register) &= ~LSM6DSM_INT_STEP_DETECTOR_ENABLE_REG_VALUE;

            if ((T(pedometerDependencies) & (BIT(STEP_COUNTER) | BIT(SIGN_MOTION))) == 0) {
                DEBUG_PRINT("setStepDetectorPower: no more need pedometer algo, disabling it\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC;
            }

            T(sensors[ACCEL]).rate[STEP_DETECTOR] = 0;
            lsm6dsm_updateOdrs();

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
        }

        /* If enable, set INT bit enable and enable accelerometer sensor @26Hz if disabled. If disable, disable INT bit and disable accelerometer if no one need it */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[STEP_DETECTOR]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[STEP_DETECTOR]) = true;
        T(sensors[STEP_DETECTOR]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setStepCounterPower: enable/disable step counter sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setStepCounterPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setStepCounterPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(readSteps) = false;
            T(pedometerDependencies) |= BIT(STEP_COUNTER);
            T(embeddedFunctionsRegister) |= LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC;
            T(int2Register) |= LSM6DSM_INT_STEP_COUNTER_ENABLE_REG_VALUE;

            T(sensors[ACCEL]).rate[STEP_COUNTER] = SENSOR_HZ(26.0f);
            lsm6dsm_updateOdrs();

            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            SPI_WRITE(LSM6DSM_INT2_CTRL_ADDR, T(int2Register));
        } else {
            T(pedometerDependencies) &= ~BIT(STEP_COUNTER);
            T(int2Register) &= ~LSM6DSM_INT_STEP_COUNTER_ENABLE_REG_VALUE;

            if ((T(pedometerDependencies) & (BIT(STEP_DETECTOR) | BIT(SIGN_MOTION))) == 0) {
                DEBUG_PRINT("setStepCounterPower: no more need pedometer algo, disabling it\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC;
            }

            T(sensors[ACCEL]).rate[STEP_COUNTER] = 0;
            lsm6dsm_updateOdrs();

            SPI_WRITE(LSM6DSM_INT2_CTRL_ADDR, T(int2Register));
            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
        }

        /* If enable, set INT bit enable and enable accelerometer sensor @26Hz if disabled. If disable, disable INT bit and disable accelerometer if no one need it */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[STEP_COUNTER]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[STEP_COUNTER]) = true;
        T(sensors[STEP_COUNTER]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setSignMotionPower: enable/disable significant motion sensor
 * @on: enable or disable sensor.
 * @cookie: private data.
 */
static bool lsm6dsm_setSignMotionPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setSignMotionPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(pedometerDependencies) |= BIT(SIGN_MOTION);
            T(embeddedFunctionsRegister) |= (LSM6DSM_ENABLE_SIGN_MOTION_DIGITAL_FUNC | LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC);
            T(int1Register) |= LSM6DSM_INT_SIGN_MOTION_ENABLE_REG_VALUE;

            T(sensors[ACCEL]).rate[SIGN_MOTION] = SENSOR_HZ(26.0f);
            lsm6dsm_updateOdrs();

            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
        } else {
            T(pedometerDependencies) &= ~BIT(SIGN_MOTION);
            T(int1Register) &= ~LSM6DSM_INT_SIGN_MOTION_ENABLE_REG_VALUE;

            if ((T(pedometerDependencies) & (BIT(STEP_DETECTOR) | BIT(STEP_COUNTER))) == 0) {
                DEBUG_PRINT("setSignMotionPower: no more need pedometer algo, disabling it\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_SIGN_MOTION_DIGITAL_FUNC;
            }

            T(sensors[ACCEL]).rate[SIGN_MOTION] = 0;
            lsm6dsm_updateOdrs();

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register), 50000);
            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
        }

        /* If enable, set INT bit enable and enable accelerometer sensor @26Hz if disabled. If disable, disable INT bit and disable accelerometer if no one need it */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[SIGN_MOTION]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[SIGN_MOTION]) = true;
        T(sensors[SIGN_MOTION]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_accelFirmwareUpload: upload accelerometer firmware
 * @cookie: private data.
 */
static bool lsm6dsm_accelFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[ACCEL]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_gyroFirmwareUpload: upload gyroscope firmware
 * @cookie: private data.
 */
static bool lsm6dsm_gyroFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[GYRO]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_magnFirmwareUpload: upload magnetometer firmware
 * @cookie: private data.
 */
static bool lsm6dsm_magnFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[MAGN]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_pressFirmwareUpload: upload pressure firmware
 * @cookie: private data.
 */
static bool lsm6dsm_pressFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[PRESS]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_tempFirmwareUpload: upload pressure firmware
 * @cookie: private data.
 */
static bool lsm6dsm_tempFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[TEMP]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_stepDetectorFirmwareUpload: upload step detector firmware
 * @cookie: private data.
 */
static bool lsm6dsm_stepDetectorFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[STEP_DETECTOR]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_stepCounterFirmwareUpload: upload step counter firmware
 * @cookie: private data.
 */
static bool lsm6dsm_stepCounterFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[STEP_COUNTER]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_signMotionFirmwareUpload: upload significant motion firmware
 * @cookie: private data.
 */
static bool lsm6dsm_signMotionFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[SIGN_MOTION]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_setAccelRate: set accelerometer ODR and report latency (FIFO watermark related)
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setAccelRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setAccelRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(sensors[ACCEL]).rate[ACCEL] = rate;
        T(sensors[ACCEL]).latency = latency;

        if (lsm6dsm_updateOdrs())
            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[ACCEL]), __FUNCTION__);
        else
            osEnqueuePrivateEvt(EVT_SENSOR_CONFIG_CHANGING, &T(sensors[ACCEL]), NULL, mTask.tid);
    } else {
        T(pendingRateConfig[ACCEL]) = true;
        T(sensors[ACCEL].pConfig.rate) = rate;
        T(sensors[ACCEL]).pConfig.latency = latency;
    }

    return true;
}

/*
 * lsm6dsm_setGyroRate: set gyroscope ODR and report latency (FIFO watermark related)
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setGyroRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setGyroRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
        T(sensors[ACCEL]).rate[GYRO] = rate;
        T(sensors[ACCEL]).dependenciesRequireData[GYRO] = true;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
        T(sensors[GYRO]).rate[GYRO] = rate;
        T(sensors[GYRO]).latency = latency;

        if (lsm6dsm_updateOdrs())
            lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[GYRO]), __FUNCTION__);
        else
            osEnqueuePrivateEvt(EVT_SENSOR_CONFIG_CHANGING, &T(sensors[GYRO]), NULL, mTask.tid);
    } else {
        T(pendingRateConfig[GYRO]) = true;
        T(sensors[GYRO]).pConfig.rate = rate;
        T(sensors[GYRO]).pConfig.latency = latency;
    }

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_setMagnRate: set magnetometer ODR and report latency (FIFO watermark related)
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setMagnRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i;

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setMagnRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(sensors[ACCEL]).rate[MAGN] = rate;
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
        T(sensors[ACCEL]).dependenciesRequireData[MAGN] = true;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
        T(sensors[MAGN]).rate[MAGN] = rate;
        T(sensors[MAGN]).latency = latency;

        lsm6dsm_updateOdrs();

        /* This call return index of LSM6DSMImuRates struct element */
        i = lsm6dsm_computeOdr(rate);
        T(sensors[MAGN]).hwRate = LSM6DSMSHRates[i];
        T(sensors[MAGN]).samplesToDiscard = 3;

        T(sensors[MAGN]).samplesDecimator = ((T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_DS3]) / T(sensors[MAGN]).samplesFifoDecimator) / T(sensors[MAGN]).rate[MAGN];
        T(sensors[MAGN]).samplesDecimatorCounter = T(sensors[MAGN]).samplesDecimator - 1;

#ifdef LSM6DSM_I2C_MASTER_LIS3MDL
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR,
                LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE,
                T(sensors[ACCEL]).hwRate, MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, MAGN);
#else /* LSM6DSM_I2C_MASTER_LIS3MDL */
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE | LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, MAGN);
#endif /* LSM6DSM_I2C_MASTER_LIS3MDL */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
    } else {
        T(pendingRateConfig[MAGN]) = true;
        T(sensors[MAGN]).pConfig.rate = rate;
        T(sensors[MAGN]).pConfig.latency = latency;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_setPressRate: set pressure ODR and report latency (FIFO watermark related)
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setPressRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i;

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setPressRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(sensors[ACCEL]).rate[PRESS] = rate;
        T(sensors[PRESS]).rate[PRESS] = rate;
        T(sensors[PRESS]).latency = latency;

        lsm6dsm_updateOdrs();

        if (T(sensors[TEMP]).enabled) {
            if (rate < T(sensors[TEMP]).rate[TEMP])
                rate = T(sensors[TEMP]).rate[TEMP];
        }

        /* This call return index of LSM6DSMImuRates struct element */
        i = lsm6dsm_computeOdr(rate);
        T(sensors[PRESS]).hwRate = LSM6DSMSHRates[i];
        T(sensors[PRESS]).samplesToDiscard = 3;

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
        if (T(baroTimerId)) {
            timTimerCancel(T(baroTimerId));
            T(baroTimerId) = 0;
            T(pendingBaroTimerTask) = false;
        }

        T(sensors[PRESS]).samplesDecimator = rate / T(sensors[PRESS]).rate[PRESS];
        T(sensors[TEMP]).samplesDecimator = rate / T(sensors[TEMP]).rate[TEMP];
        T(time).timestampBaroLSB = 0;

        T(baroTimerId) = timTimerSet(lsm6dsm_sensorHzToNs(rate), 0, 50, lsm6dsm_baroTimerCallback, NULL, false);
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
        T(sensors[PRESS]).samplesDecimator = ((T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_DS3]) / T(sensors[PRESS]).samplesFifoDecimator) / T(sensors[PRESS]).rate[PRESS];
        T(sensors[TEMP]).samplesDecimator = ((T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_DS3]) / T(sensors[PRESS]).samplesFifoDecimator) / T(sensors[TEMP]).rate[TEMP];
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

        T(sensors[PRESS]).samplesDecimatorCounter = T(sensors[PRESS]).samplesDecimator - 1;
        T(sensors[TEMP]).samplesDecimatorCounter = T(sensors[TEMP]).samplesDecimator - 1;

        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_BARO_ODR_BASE | LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, PRESS);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[PRESS]), __FUNCTION__);
    } else {
        T(pendingRateConfig[PRESS]) = true;
        T(sensors[PRESS]).pConfig.rate = rate;
        T(sensors[PRESS]).pConfig.latency = latency;
    }

    return true;
}

/*
 * lsm6dsm_setTempRate: set temperature ODR and report latency (FIFO watermark related)
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setTempRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i;

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setTempRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(sensors[ACCEL]).rate[TEMP] = rate;
        T(sensors[TEMP]).rate[TEMP] = rate;
        T(sensors[TEMP]).latency = latency;

        lsm6dsm_updateOdrs();

        if (T(sensors[PRESS]).enabled) {
            if (rate < T(sensors[PRESS]).rate[PRESS])
                rate = T(sensors[PRESS]).rate[PRESS];
        }

        /* This call return index of LSM6DSMImuRates struct element */
        i = lsm6dsm_computeOdr(rate);
        T(sensors[TEMP]).hwRate = LSM6DSMSHRates[i];
        T(sensors[TEMP]).samplesToDiscard = 3;

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
        if (T(baroTimerId)) {
            timTimerCancel(T(baroTimerId));
            T(baroTimerId) = 0;
            T(pendingBaroTimerTask) = false;
        }

        T(sensors[TEMP]).samplesDecimator = rate / T(sensors[PRESS]).rate[PRESS];
        T(sensors[PRESS]).samplesDecimator = rate / T(sensors[PRESS]).rate[PRESS];
        T(time).timestampBaroLSB = 0;

        T(baroTimerId) = timTimerSet(lsm6dsm_sensorHzToNs(rate), 0, 50, lsm6dsm_baroTimerCallback, NULL, false);
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
        T(sensors[TEMP]).samplesDecimator = ((T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_DS3]) / T(sensors[PRESS]).samplesFifoDecimator) / T(sensors[TEMP]).rate[TEMP];
        T(sensors[PRESS]).samplesDecimator = ((T(fifoCntl).triggerRate / T(fifoCntl).decimators[FIFO_DS3]) / T(sensors[PRESS]).samplesFifoDecimator) / T(sensors[PRESS]).rate[TEMP];
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

        T(sensors[TEMP]).samplesDecimatorCounter = T(sensors[TEMP]).samplesDecimator - 1;
        T(sensors[PRESS]).samplesDecimatorCounter = T(sensors[PRESS]).samplesDecimator - 1;

        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_BARO_ODR_BASE | LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, TEMP);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[TEMP]), __FUNCTION__);
    } else {
        T(pendingRateConfig[TEMP]) = true;
        T(sensors[TEMP]).pConfig.rate = rate;
        T(sensors[TEMP]).pConfig.latency = latency;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_setStepDetectorRate: set step detector report latency
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setStepDetectorRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();

    INFO_PRINT("setStepDetectorRate: latency=%lldns\n", latency);

    T(sensors[STEP_DETECTOR]).hwRate = rate;
    T(sensors[STEP_DETECTOR]).latency = latency;

    sensorSignalInternalEvt(T(sensors[STEP_DETECTOR]).handle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

/*
 * lsm6dsm_setStepCounterRate: set step counter report latency
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setStepCounterRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i, regValue;

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        if (rate == SENSOR_RATE_ONCHANGE) {
            INFO_PRINT("setStepCounterRate: delivery-rate=on_change, latency=%lldns\n", latency);
        } else
            INFO_PRINT("setStepCounterRate: delivery_rate=%dms, latency=%lldns\n", (int)((1024.0f / rate) * 1000.0f), latency);

        T(sensors[STEP_COUNTER]).hwRate = rate;
        T(sensors[STEP_COUNTER]).latency = latency;

        if (rate != SENSOR_RATE_ONCHANGE) {
        for (i = 0; i < ARRAY_SIZE(LSM6DSMStepCounterRates); i++) {
            if (rate == LSM6DSMStepCounterRates[i])
                break;
        }
        if (i >= (ARRAY_SIZE(LSM6DSMStepCounterRates) - 2))
            regValue = 0;
        else
            regValue = (128 >> i);
        } else
            regValue = 0;

        lsm6dsm_writeEmbeddedRegister(LSM6DSM_EMBEDDED_STEP_COUNT_DELTA_ADDR, regValue);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[GYRO]), __FUNCTION__);
    } else {
        T(pendingRateConfig[STEP_COUNTER]) = true;
        T(sensors[STEP_COUNTER]).pConfig.rate = rate;
        T(sensors[STEP_COUNTER]).pConfig.latency = latency;
    }

    return true;
}

/*
 * lsm6dsm_setSignMotionRate: set significant motion report latency
 * @rate: sensor rate expressed in SENSOR_HZ(x).
 * @latency: max latency valud in ns.
 * @cookie: private data.
 */
static bool lsm6dsm_setSignMotionRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();

    DEBUG_PRINT("setSignMotionRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

    T(sensors[SIGN_MOTION]).rate[SIGN_MOTION] = rate;
    T(sensors[SIGN_MOTION]).latency = latency;

    sensorSignalInternalEvt(T(sensors[SIGN_MOTION]).handle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

/*
 * lsm6dsm_accelFlush: send accelerometer flush event
 * @cookie: private data.
 */
static bool lsm6dsm_accelFlush(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_INT1_STATUS_REG_HANDLING)) {
        INFO_PRINT("accelFlush: flush accelerometer data\n");

        if (sensorGetTime() <= (T(lastFifoReadTimestamp) + ((uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * T(fifoCntl).maxDecimator))) {
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ACCEL), SENSOR_DATA_EVENT_FLUSH, NULL);
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
            return true;
        }

        T(sendFlushEvt[ACCEL]) = true;

        SPI_READ(LSM6DSM_FIFO_STATUS1_ADDR, 2, &T_SLAVE_INTERFACE(fifoStatusRegBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else
        T(pendingFlush[ACCEL])++;

    return true;
}

/*
 * lsm6dsm_gyroFlush: send gyroscope flush event
 * @cookie: private data.
 */
static bool lsm6dsm_gyroFlush(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_INT1_STATUS_REG_HANDLING)) {
        INFO_PRINT("gyroFlush: flush gyroscope data\n");

        if (sensorGetTime() <= (T(lastFifoReadTimestamp) + ((uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * T(fifoCntl).maxDecimator))) {
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_GYRO), SENSOR_DATA_EVENT_FLUSH, NULL);
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
            return true;
        }

        T(sendFlushEvt[GYRO]) = true;

        SPI_READ(LSM6DSM_FIFO_STATUS1_ADDR, 2, &T_SLAVE_INTERFACE(fifoStatusRegBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else
        T(pendingFlush[GYRO])++;

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_magnFlush: send magnetometer flush event
 * @cookie: private data.
 */
static bool lsm6dsm_magnFlush(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_INT1_STATUS_REG_HANDLING)) {
        INFO_PRINT("magnFlush: flush magnetometer data\n");

        if (sensorGetTime() <= (T(lastFifoReadTimestamp) + ((uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * T(fifoCntl).maxDecimator))) {
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_MAG), SENSOR_DATA_EVENT_FLUSH, NULL);
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
            return true;
        }

        T(sendFlushEvt[MAGN]) = true;

        SPI_READ(LSM6DSM_FIFO_STATUS1_ADDR, 2, &T_SLAVE_INTERFACE(fifoStatusRegBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else
        T(pendingFlush[MAGN])++;

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_pressFlush: send pressure flush event
 * @cookie: private data.
 */
static bool lsm6dsm_pressFlush(void *cookie)
{
    TDECL();

#if !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
    if (trySwitchState(SENSOR_INT1_STATUS_REG_HANDLING)) {
        INFO_PRINT("pressFlush: flush pressure data\n");

        if (sensorGetTime() <= (T(lastFifoReadTimestamp) + ((uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * T(fifoCntl).maxDecimator))) {
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_BARO), SENSOR_DATA_EVENT_FLUSH, NULL);
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
            return true;
        }

        T(sendFlushEvt[PRESS]) = true;

        SPI_READ(LSM6DSM_FIFO_STATUS1_ADDR, 2, &T_SLAVE_INTERFACE(fifoStatusRegBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else
        T(pendingFlush[PRESS])++;
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
    INFO_PRINT("pressFlush: flush pressure data\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_BARO), SENSOR_DATA_EVENT_FLUSH, NULL);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

    return true;
}

/*
 * lsm6dsm_tempFlush: send temperature flush event
 * @cookie: private data.
 */
static bool lsm6dsm_tempFlush(void *cookie)
{
    TDECL();

#if !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
    if (trySwitchState(SENSOR_INT1_STATUS_REG_HANDLING)) {
        INFO_PRINT("tempFlush: flush temperature data\n");

        if (sensorGetTime() <= (T(lastFifoReadTimestamp) + ((uint64_t)lsm6dsm_sensorHzToNs(T(fifoCntl).triggerRate) * T(fifoCntl).maxDecimator))) {
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TEMP), SENSOR_DATA_EVENT_FLUSH, NULL);
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
            return true;
        }

        T(sendFlushEvt[TEMP]) = true;

        SPI_READ(LSM6DSM_FIFO_STATUS1_ADDR, 2, &T_SLAVE_INTERFACE(fifoStatusRegBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else
        T(pendingFlush[TEMP])++;
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
    INFO_PRINT("tempFlush: flush temperature data\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TEMP), SENSOR_DATA_EVENT_FLUSH, NULL);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_stepDetectorFlush: send step detector flush event
 * @cookie: private data.
 */
static bool lsm6dsm_stepDetectorFlush(void *cookie)
{
    TDECL();

    INFO_PRINT("stepDetectorFlush: flush step detector data\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_DETECT), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

/*
 * lsm6dsm_stepCounterFlush: send step counter flush event
 * @cookie: private data.
 */
static bool lsm6dsm_stepCounterFlush(void *cookie)
{
    TDECL();

    INFO_PRINT("stepCounterFlush: flush step counter data\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_COUNT), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

/*
 * lsm6dsm_signMotionFlush: send significant motion flush event
 * @cookie: private data.
 */
static bool lsm6dsm_signMotionFlush(void *cookie)
{
    TDECL();

    INFO_PRINT("signMotionFlush: flush significant motion data\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_SIG_MOTION), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

/*
 * lsm6dsm_stepCounterSendLastData: send last number of steps
 * @cookie: private data.
 * @tid: task id.
 */
static bool lsm6dsm_stepCounterSendLastData(void *cookie, uint32_t tid)
{
    TDECL();

    INFO_PRINT("stepCounterSendLastData: %lu steps\n", T(totalNumSteps));

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_COUNT), &T(totalNumSteps), NULL);

    return true;
}

/*
 * lsm6dsm_runAccelSelfTest: execute accelerometer self-test
 * @cookie: private data.
 */
static bool lsm6dsm_runAccelSelfTest(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_SELFTEST)) {
        if (!T(sensors[ACCEL]).enabled && (T(sensors[ACCEL]).hwRate == 0) && (T(sensors[GYRO]).hwRate == 0)) {
            INFO_PRINT("runAccelSelfTest: executing accelerometer selftest\n");
            T(selftestState) = SELFTEST_INITIALIZATION;
            lsm6dsm_runGapSelfTestProgram(ACCEL);
            return true;
        } else
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
    }

    ERROR_PRINT("runAccelSelfTest: cannot run selftest because sensor is busy!\n");
    lsm6dsm_sendSelfTestResult(SENS_TYPE_ACCEL, SENSOR_APP_EVT_STATUS_BUSY);

    return false;
}

/*
 * lsm6dsm_runGyroSelfTest: execute gyroscope self-test
 * @cookie: private data.
 */
static bool lsm6dsm_runGyroSelfTest(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_SELFTEST)) {
        if (!T(sensors[GYRO]).enabled && (T(sensors[GYRO]).hwRate == 0) && (T(sensors[ACCEL]).hwRate == 0)) {
            INFO_PRINT("runGyroSelfTest: executing gyroscope selftest\n");
            T(selftestState) = SELFTEST_INITIALIZATION;
            lsm6dsm_runGapSelfTestProgram(GYRO);
            return true;
        } else
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
    }

    ERROR_PRINT("runGyroSelfTest: cannot run selftest because sensor is busy!\n");
    lsm6dsm_sendSelfTestResult(SENS_TYPE_GYRO, SENSOR_APP_EVT_STATUS_BUSY);

    return false;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_runMagnSelfTest: execute magnetometer self-test
 * @cookie: private data.
 */
static bool lsm6dsm_runMagnSelfTest(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_SELFTEST)) {
        if (!T(sensors[MAGN]).enabled && (T(sensors[MAGN]).hwRate == 0) && (T(sensors[GYRO]).hwRate == 0) && (T(sensors[ACCEL]).hwRate == 0)) {
            INFO_PRINT("runMagnSelfTest: executing magnetometer selftest\n");
            T(selftestState) = SELFTEST_INITIALIZATION;
#ifdef LSM6DSM_I2C_MASTER_AK09916
            lsm6dsm_runAbsoluteSelfTestProgram();
#else
            lsm6dsm_runGapSelfTestProgram(MAGN);
#endif
            return true;
        } else
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
    }

    ERROR_PRINT("runMagnSelfTest: cannot run selftest because sensor is busy!\n");
    lsm6dsm_sendSelfTestResult(SENS_TYPE_MAG, SENSOR_APP_EVT_STATUS_BUSY);

    return false;
}

/*
 * lsm6dsm_magnCfgData: set sw magnetometer calibration values
 * @data: calibration data struct.
 * @cookie: private data.
 */
static bool lsm6dsm_magnCfgData(void *data, void *cookie)
{
    TDECL();
    float *values = data;

    DEBUG_PRINT("Magn sw bias data [uT * 1000]: %ld %ld %ld\n", (int32_t)(values[0] * 1000), (int32_t)(values[1] * 1000), (int32_t)(values[2] * 1000));

    T(magnCal).x_bias = values[0];
    T(magnCal).y_bias = values[1];
    T(magnCal).z_bias = values[2];

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

/*
 * lsm6dsm_runAccelCalibration: execute accelerometer calibration
 * @cookie: private data.
 */
static bool lsm6dsm_runAccelCalibration(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_CALIBRATION)) {
        if (!T(sensors[ACCEL]).enabled && (T(sensors[ACCEL]).hwRate == 0) && (T(sensors[GYRO]).hwRate == 0)) {
            INFO_PRINT("runAccelCalibration: executing accelerometer calibration\n");
            T(calibrationState) = CALIBRATION_INITIALIZATION;
            lsm6dsm_runCalibrationProgram(ACCEL);
            return true;
        } else
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
    }

    ERROR_PRINT("runAccelCalibration: cannot run selftest because sensor is busy!\n");
    lsm6dsm_sendCalibrationResult(SENS_TYPE_ACCEL, SENSOR_APP_EVT_STATUS_BUSY, 0, 0, 0);

    return true;
}

/*
 * lsm6dsm_runGyroCalibration: execute gyroscope calibration
 * @cookie: private data.
 */
static bool lsm6dsm_runGyroCalibration(void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_CALIBRATION)) {
        if (!T(sensors[GYRO]).enabled && (T(sensors[GYRO]).hwRate == 0) && (T(sensors[ACCEL]).hwRate == 0)) {
            INFO_PRINT("runGyroCalibration: executing gyroscope calibration\n");
            T(calibrationState) = CALIBRATION_INITIALIZATION;
            lsm6dsm_runCalibrationProgram(GYRO);
            return true;
        } else
            osEnqueuePrivateEvt(EVT_SENSOR_RESTORE_IDLE, cookie, NULL, mTask.tid);
    }

    ERROR_PRINT("runGyroCalibration: cannot run selftest because sensor is busy!\n");
    lsm6dsm_sendCalibrationResult(SENS_TYPE_GYRO, SENSOR_APP_EVT_STATUS_BUSY, 0, 0, 0);

    return true;
}

/*
 * lsm6dsm_storeAccelCalibrationData: store hw calibration into sensor
 */
static bool lsm6dsm_storeAccelCalibrationData(void)
{
    TDECL();
    uint8_t buffer[LSM6DSM_TRIAXIAL_NUM_AXIS];

    if (trySwitchState(SENSOR_STORE_CALIBRATION_DATA)) {
        for (uint8_t i = 0; i < LSM6DSM_TRIAXIAL_NUM_AXIS; i++)
            buffer[i] = lsm6dsm_convertAccelOffsetValue(T(accelCalibrationData[i]));

        SPI_MULTIWRITE(LSM6DSM_X_OFS_USR_ADDR, buffer, LSM6DSM_TRIAXIAL_NUM_AXIS);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, NULL, __FUNCTION__);
    } else
        return false;

    return true;
}

/*
 * lsm6dsm_accelCfgData: set hw and sw accelerometer calibration values
 * @data: calibration data struct.
 * @cookie: private data.
 */
static bool lsm6dsm_accelCfgData(void *data, void *cookie)
{
    TDECL();
    struct LSM6DSMAccelGyroCfgData *cfgData = data;

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    accelCalBiasSet(&T(accelCal) , cfgData->sw[0], cfgData->sw[1], cfgData->sw[2]);
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */

    DEBUG_PRINT("Accel hw bias data [LSB]: %ld %ld %ld\n", cfgData->hw[0], cfgData->hw[1], cfgData->hw[2]);

    memcpy(T(accelCalibrationData), cfgData->hw, LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));

    if (!lsm6dsm_storeAccelCalibrationData())
        T(pendingStoreAccelCalibData) = true;

    return true;
}

/*
 * lsm6dsm_gyroCfgData: set hw and sw gyroscope calibration values
 * @data: calibration data struct.
 * @cookie: private data.
 */
static bool lsm6dsm_gyroCfgData(void *data, void *cookie)
{
    TDECL();
    struct LSM6DSMAccelGyroCfgData *cfgData = data;

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    gyroCalSetBias(&T(gyroCal), cfgData->sw[0], cfgData->sw[1], cfgData->sw[2], sensorGetTime());
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

    DEBUG_PRINT("Gyro hw bias data [LSB]: %ld %ld %ld\n", cfgData->hw[0], cfgData->hw[1], cfgData->hw[2]);

    memcpy(T(gyroCalibrationData), cfgData->hw, LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));

    return true;
}

/*
 * lsm6dsm_sensorInit: initial sensors configuration
 */
static void lsm6dsm_sensorInit(void)
{
    TDECL();
    uint8_t buffer[5];

    switch (T(initState)) {
    case RESET_LSM6DSM:
        INFO_PRINT("Performing soft-reset\n");

        T(initState) = INIT_LSM6DSM;

        /* Sensor SW-reset */
        SPI_WRITE(LSM6DSM_CTRL3_C_ADDR, LSM6DSM_SW_RESET, 20000);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_LSM6DSM:
        INFO_PRINT("Initial registers configuration\n");

        /* During init, reset all configurable registers to default values */
        SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
        SPI_WRITE(LSM6DSM_DRDY_PULSE_CFG_ADDR, LSM6DSM_DRDY_PULSE_CFG_BASE);

        buffer[0] = LSM6DSM_CTRL1_XL_BASE;                           /* LSM6DSM_CTRL1_XL */
        buffer[1] = LSM6DSM_CTRL2_G_BASE;                            /* LSM6DSM_CTRL2_G */
        buffer[2] = LSM6DSM_CTRL3_C_BASE;                            /* LSM6DSM_CTRL3_C */
        buffer[3] = LSM6DSM_CTRL4_C_BASE;                            /* LSM6DSM_CTRL4_C */
        buffer[4] = LSM6DSM_CTRL5_C_BASE;                            /* LSM6DSM_CTRL4_C */
        SPI_MULTIWRITE(LSM6DSM_CTRL1_XL_ADDR, buffer, 5);

        buffer[0] = LSM6DSM_CTRL10_C_BASE | LSM6DSM_RESET_PEDOMETER; /* LSM6DSM_CTRL10_C */
        buffer[1] = LSM6DSM_MASTER_CONFIG_BASE;                      /* LSM6DSM_MASTER_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_CTRL10_C_ADDR, buffer, 2);

        SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, LSM6DSM_INT1_CTRL_BASE);
        SPI_WRITE(LSM6DSM_WAKE_UP_DUR_ADDR, LSM6DSM_WAKE_UP_DUR_BASE);

#ifdef LSM6DSM_I2C_MASTER_ENABLED
        T(initState) = INIT_I2C_MASTER_REGS_CONF;
#else /* LSM6DSM_I2C_MASTER_ENABLED */
        INFO_PRINT("Initialization completed successfully!\n");
        T(initState) = INIT_DONE;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

#ifdef LSM6DSM_I2C_MASTER_ENABLED
    case INIT_I2C_MASTER_REGS_CONF:
        INFO_PRINT("Initial I2C master registers configuration\n");

        /* Enable access for embedded registers */
        SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

        /* I2C-0 configuration */
        buffer[0] = LSM6DSM_EMBEDDED_SLV0_WRITE_ADDR_SLEEP;                                               /* LSM6DSM_EMBEDDED_SLV0_ADDR */
        buffer[1] = 0x00;                                                                                 /* LSM6DSM_EMBEDDED_SLV0_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE;                                                /* LSM6DSM_EMBEDDED_SLV0_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR, buffer, 3);

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)     /* Magn & Baro both enabled */
        /* I2C-1 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV1_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV1_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE | LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN;      /* LSM6DSM_EMBEDDED_SLV1_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR, buffer, 3);

        /* I2C-2 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV2_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV2_SUBADDR */
        buffer[2] = LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN;                                                /* LSM6DSM_EMBEDDED_SLV2_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV2_ADDR_ADDR, buffer, 3);

#ifdef LSM6DSM_I2C_MASTER_AK09916
        /* I2C-3 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV3_ADDR */
        buffer[1] = AK09916_STATUS_DATA_ADDR;                                                             /* LSM6DSM_EMBEDDED_SLV3_SUBADDR */
        buffer[2] = 1;                                                                                    /* LSM6DSM_EMBEDDED_SLV3_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV3_ADDR_ADDR, buffer, 3);
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && !defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)    /* Magn only enabled */
        /* I2C-1 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV1_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV1_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE | LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN;      /* LSM6DSM_EMBEDDED_SLV1_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR, buffer, 3);

#ifdef LSM6DSM_I2C_MASTER_AK09916
        /* I2C-2 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV2_ADDR */
        buffer[1] = AK09916_STATUS_DATA_ADDR;                                                             /* LSM6DSM_EMBEDDED_SLV2_SUBADDR */
        buffer[2] = 0x01;                                                                                 /* LSM6DSM_EMBEDDED_SLV2_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV2_ADDR_ADDR, buffer, 3);
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

#if !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)    /* Baro only enabled */
        /* I2C-1 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV1_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV1_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE | LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN;      /* LSM6DSM_EMBEDDED_SLV1_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR, buffer, 3);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

        /* Disable access for embedded registers */
        SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);

        T(initState) = INIT_I2C_MASTER_SENSOR_RESET;

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_SENSOR_RESET:
        INFO_PRINT("Performing soft-reset slave sensors\n");
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
        T(initState) = INIT_I2C_MASTER_MAGN_SENSOR;
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
        T(initState) = INIT_I2C_MASTER_BARO_SENSOR;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

        /* Enable accelerometer and sensor-hub to initialize slave sensor */
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | LSM6DSM_ODR_104HZ_REG_VALUE);
        SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE | LSM6DSM_MASTER_CONFIG_MASTER_ON);
        T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR, LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE, SENSOR_HZ(104.0f), MAGN, 20000);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_RESET_ADDR, LSM6DSM_SENSOR_SLAVE_BARO_RESET_VALUE, SENSOR_HZ(104.0f), PRESS, 20000);
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_MAGN_SENSOR:
        INFO_PRINT("Initial slave magnetometer sensor registers configuration\n");
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        T(initState) = INIT_I2C_MASTER_BARO_SENSOR;
#else /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
        T(initState) = INIT_I2C_MASTER_SENSOR_END;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_LIS3MDL
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL1_ADDR, LIS3MDL_CTRL1_BASE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL2_ADDR, LIS3MDL_CTRL2_BASE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL3_ADDR, LIS3MDL_CTRL3_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL4_ADDR, LIS3MDL_CTRL4_BASE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL5_ADDR, LIS3MDL_CTRL5_BASE, SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_LIS3MDL */

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_A_M_ADDR, LSM303AGR_CFG_REG_A_M_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_C_M_ADDR, LSM303AGR_CFG_REG_C_M_BASE, SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

#ifdef LSM6DSM_I2C_MASTER_AK09916
        SPI_WRITE_SLAVE_SENSOR_REGISTER(AK09916_CNTL2_ADDR, AK09916_CNTL2_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE, SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_AK09916 */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_BARO_SENSOR:
        INFO_PRINT("Initial slave barometer sensor registers configuration\n");
        T(initState) = INIT_I2C_MASTER_SENSOR_END;

#ifdef LSM6DSM_I2C_MASTER_LPS22HB
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LPS22HB_CTRL1_ADDR, LPS22HB_CTRL1_BASE | LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE, SENSOR_HZ(104.0f), PRESS);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LPS22HB_CTRL2_ADDR, LPS22HB_CTRL2_BASE, SENSOR_HZ(104.0f), PRESS);
#endif /* LSM6DSM_I2C_MASTER_LPS22HB */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_SENSOR_END:
        INFO_PRINT("Initialization completed successfully!\n");
        T(initState) = INIT_DONE;

        /* Disable accelerometer and sensor-hub */
        SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE);
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE);
        T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

    default:
        break;
    }
}

/*
 * lsm6dsm_processPendingEvt: process pending events
 */
static void lsm6dsm_processPendingEvt(void)
{
    TDECL();
    enum SensorIndex i;

    SET_STATE(SENSOR_IDLE);

    for (i = 0; i < NUM_SENSORS; i++) {
        if (T(pendingEnableConfig[i])) {
            T(pendingEnableConfig[i]) = false;
            LSM6DSMSensorOps[i].sensorPower(T(sensors[i]).pConfig.enable, (void *)i);
            return;
        }

        if (T(pendingRateConfig[i])) {
            T(pendingRateConfig[i]) = false;
            LSM6DSMSensorOps[i].sensorSetRate(T(sensors[i]).pConfig.rate, T(sensors[i]).pConfig.latency, (void *)i);
            return;
        }

        if (T(pendingFlush[i]) > 0) {
            T(pendingFlush[i])--;
            LSM6DSMSensorOps[i].sensorFlush((void *)i);
            return;
        }
    }

    if (T(pendingTimeSyncTask)) {
        T(pendingTimeSyncTask) = false;
        lsm6dsm_timeSyncTask();
        return;
    }

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    if (T(pendingBaroTimerTask)) {
        T(pendingBaroTimerTask) = false;
        lsm6dsm_baroTimerTask();
        return;
    }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

    if (T(pendingStoreAccelCalibData)) {
        T(pendingStoreAccelCalibData) = lsm6dsm_storeAccelCalibrationData();
        return;
    }

    if (T(pendingInt)) {
        T(pendingInt) = false;
        lsm6dsm_readStatusReg(false);
        return;
    }

    if (gpioGet(T(int1)))
        lsm6dsm_readStatusReg(false);
}

/*
 * lsm6dsm_allocateThreeAxisDataEvt: allocate slab for three axis sensor data
 * @mSensor: sensor info.
 * @rtcTime: time of first sample in this block.
 */
static bool lsm6dsm_allocateThreeAxisDataEvt(struct LSM6DSMSensor *mSensor, uint64_t rtcTime)
{
    TDECL();

    mSensor->tADataEvt = slabAllocatorAlloc(T(mDataSlabThreeAxis));
    if (!mSensor->tADataEvt) {
        ERROR_PRINT("Failed to allocate memory!\n");
        return false;
    }

    memset(&mSensor->tADataEvt->samples[0].firstSample, 0, sizeof(struct SensorFirstSample));
    mSensor->tADataEvt->referenceTime = rtcTime;
    mSensor->pushedTimestamp = rtcTime;

    return true;
}

/*
 * lsm6dsm_threeAxisDataEvtFree: deallocate slab of three axis sensor.
 * @ptr: sensor data pointer.
 */
static void lsm6dsm_threeAxisDataEvtFree(void *ptr)
{
    TDECL();

    slabAllocatorFree(T(mDataSlabThreeAxis), (struct TripleAxisDataEvent *)ptr);
}

#if defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
/*
 * lsm6dsm_allocateOneAxisDataEvt: allocate slab for one axis sensor data
 * @mSensor: sensor info.
 * @rtcTime: time of first sample in this block.
 */
static bool lsm6dsm_allocateOneAxisDataEvt(struct LSM6DSMSensor *mSensor, uint64_t rtcTime)
{
    TDECL();

    mSensor->sADataEvt = slabAllocatorAlloc(T(mDataSlabOneAxis));
    if (!mSensor->sADataEvt) {
        ERROR_PRINT("Failed to allocate memory!\n");
        return false;
    }

    memset(&mSensor->sADataEvt->samples[0].firstSample, 0, sizeof(struct SensorFirstSample));
    mSensor->sADataEvt->referenceTime = rtcTime;
    mSensor->pushedTimestamp = rtcTime;

    return true;
}

/*
 * lsm6dsm_oneAxisDataEvtFree: deallocate slab of one axis sensor
 * @ptr: sensor data pointer.
 */
static void lsm6dsm_oneAxisDataEvtFree(void *ptr)
{
    TDECL();

    slabAllocatorFree(T(mDataSlabOneAxis), (struct SingleAxisDataEvent *)ptr);
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_processSensorThreeAxisData: process three axis sensors data
 * @mSensor: sensor info.
 * @data: sensor data.
 * @sampleNum: number of samples in the current slab.
 * @timestamp: current sample timestamp;
 */
static bool lsm6dsm_processSensorThreeAxisData(struct LSM6DSMSensor *mSensor, uint8_t *data, uint16_t *sampleNum, uint64_t *timestamp)
{
    TDECL();
    int16_t x, y, z;
    float x_remap, y_remap, z_remap;
    struct TripleAxisDataPoint *samples;

    if (*timestamp == 0)
        return false;

    if (mSensor->tADataEvt == NULL) {
        if (!lsm6dsm_allocateThreeAxisDataEvt(mSensor, *timestamp))
            return false;
    }
    samples = mSensor->tADataEvt->samples;

    x = (int16_t)(data[1] << 8) | data[0];
    y = (int16_t)(data[3] << 8) | data[2];
    z = (int16_t)(data[5] << 8) | data[4];

    switch (mSensor->idx) {
    case ACCEL:
        x_remap = LSM6DSM_REMAP_X_DATA(x, y, z, LSM6DSM_ACCEL_GYRO_ROT_MATRIX) * LSM6DSM_ACCEL_KSCALE;
        y_remap = LSM6DSM_REMAP_Y_DATA(x, y, z, LSM6DSM_ACCEL_GYRO_ROT_MATRIX) * LSM6DSM_ACCEL_KSCALE;
        z_remap = LSM6DSM_REMAP_Z_DATA(x, y, z, LSM6DSM_ACCEL_GYRO_ROT_MATRIX) * LSM6DSM_ACCEL_KSCALE;

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
        accelCalRun(&T(accelCal), *timestamp, x_remap, y_remap, z_remap, T(currentTemperature));
        accelCalBiasRemove(&T(accelCal), &x_remap, &y_remap, &z_remap);

        if (accelCalUpdateBias(&T(accelCal), &samples[*sampleNum].x, &samples[*sampleNum].y, &samples[*sampleNum].z)) {
            if (!samples->firstSample.biasCurrent) {
                samples->firstSample.biasCurrent = true;
                samples->firstSample.biasPresent = 1;
                samples->firstSample.biasSample = *sampleNum;

                if (*sampleNum > 0)
                    samples[*sampleNum].deltaTime = 0;

                *sampleNum += 1;
            }
        }
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
        if (T(sensors[GYRO].enabled))
            gyroCalUpdateAccel(&T(gyroCal), *timestamp, x_remap, y_remap, z_remap);
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

        break;

    case GYRO:
        x -= (int16_t)T(gyroCalibrationData)[0];
        y -= (int16_t)T(gyroCalibrationData)[1];
        z -= (int16_t)T(gyroCalibrationData)[2];

        x_remap = LSM6DSM_REMAP_X_DATA(x, y, z, LSM6DSM_ACCEL_GYRO_ROT_MATRIX) * LSM6DSM_GYRO_KSCALE;
        y_remap = LSM6DSM_REMAP_Y_DATA(x, y, z, LSM6DSM_ACCEL_GYRO_ROT_MATRIX) * LSM6DSM_GYRO_KSCALE;
        z_remap = LSM6DSM_REMAP_Z_DATA(x, y, z, LSM6DSM_ACCEL_GYRO_ROT_MATRIX) * LSM6DSM_GYRO_KSCALE;

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
        gyroCalUpdateGyro(&T(gyroCal), *timestamp, x_remap, y_remap, z_remap, T(currentTemperature));

#ifdef LSM6DSM_OVERTEMP_CALIB_ENABLED
        overTempCalSetTemperature(&T(overTempCal), *timestamp, T(currentTemperature));
#else /* LSM6DSM_OVERTEMP_CALIB_ENABLED */
        gyroCalRemoveBias(&T(gyroCal), x_remap, y_remap, z_remap, &x_remap, &y_remap, &z_remap);
#endif /* LSM6DSM_OVERTEMP_CALIB_ENABLED */

        if (gyroCalNewBiasAvailable(&T(gyroCal))) {
            float biasTemperature, gyroOffset[3] = { 0.0f, 0.0f, 0.0f };

            gyroCalGetBias(&T(gyroCal), &gyroOffset[0], &gyroOffset[1], &gyroOffset[2], &biasTemperature);

            if (!samples->firstSample.biasCurrent) {
                samples->firstSample.biasCurrent = true;
                samples->firstSample.biasPresent = 1;
                samples->firstSample.biasSample = *sampleNum;

                if (*sampleNum > 0)
                    samples[*sampleNum].deltaTime = 0;

                samples[*sampleNum].x = gyroOffset[0];
                samples[*sampleNum].y = gyroOffset[1];
                samples[*sampleNum].z = gyroOffset[2];

                *sampleNum += 1;
            }

#ifdef LSM6DSM_OVERTEMP_CALIB_ENABLED
            overTempCalUpdateSensorEstimate(&T(overTempCal), *timestamp, gyroOffset, biasTemperature);
            overTempCalRemoveOffset(&T(overTempCal), *timestamp, x_remap, y_remap, z_remap, &x_remap, &y_remap, &z_remap);
#endif /* LSM6DSM_OVERTEMP_CALIB_ENABLED */
        } else {
#ifdef LSM6DSM_OVERTEMP_CALIB_ENABLED
            overTempCalRemoveOffset(&T(overTempCal), *timestamp, x_remap, y_remap, z_remap, &x_remap, &y_remap, &z_remap);
#endif /* LSM6DSM_OVERTEMP_CALIB_ENABLED */
        }
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
        break;

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    case MAGN: ;
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
        bool newMagnCalibData;
        float magnOffX, magnOffY, magnOffZ;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

        x_remap = LSM6DSM_REMAP_X_DATA(x, y, z, LSM6DSM_MAGN_ROT_MATRIX) * LSM6DSM_MAGN_KSCALE;
        y_remap = LSM6DSM_REMAP_Y_DATA(x, y, z, LSM6DSM_MAGN_ROT_MATRIX) * LSM6DSM_MAGN_KSCALE;
        z_remap = LSM6DSM_REMAP_Z_DATA(x, y, z, LSM6DSM_MAGN_ROT_MATRIX) * LSM6DSM_MAGN_KSCALE;

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
        magCalRemoveSoftiron(&T(magnCal), x_remap, y_remap, z_remap, &magnOffX, &magnOffY, &magnOffZ);
        newMagnCalibData = magCalUpdate(&T(magnCal), NS_TO_US(*timestamp), magnOffX, magnOffY, magnOffZ);
        magCalRemoveBias(&T(magnCal), magnOffX, magnOffY, magnOffZ, &x_remap, &y_remap, &z_remap);

        if (newMagnCalibData && !samples->firstSample.biasCurrent) {
            samples->firstSample.biasCurrent = true;
            samples->firstSample.biasPresent = 1;
            samples->firstSample.biasSample = *sampleNum;

            if (*sampleNum > 0)
                samples[*sampleNum].deltaTime = 0;

            magCalGetBias(&T(magnCal), &samples[*sampleNum].x, &samples[*sampleNum].y, &samples[*sampleNum].z);

            *sampleNum += 1;
        }
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

        break;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

    default:
        return false;
    }

    if (++mSensor->samplesDecimatorCounter >= mSensor->samplesDecimator) {
        samples[*sampleNum].x = x_remap;
        samples[*sampleNum].y = y_remap;
        samples[*sampleNum].z = z_remap;

        if (*sampleNum > 0) {
            samples[*sampleNum].deltaTime = *timestamp - mSensor->pushedTimestamp;
            mSensor->pushedTimestamp = *timestamp;
        }

        *sampleNum += 1;

        mSensor->samplesDecimatorCounter = 0;
    }

    return true;
}

#if defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
/*
 * lsm6dsm_processSensorOneAxisData: process single axis sensors data
 * @mSensor: sensor info.
 * @data: sensor data.
 * @sampleNum: number of samples in the current slab.
 * @timestamp: current sample timestamp;
 */
static bool lsm6dsm_processSensorOneAxisData(struct LSM6DSMSensor *mSensor, uint8_t *data, uint16_t *sampleNum, uint64_t *timestamp)
{
    TDECL();

    if (*timestamp == 0)
        return false;

    if (++mSensor->samplesDecimatorCounter >= mSensor->samplesDecimator) {
        if (mSensor->sADataEvt == NULL) {
            if (!lsm6dsm_allocateOneAxisDataEvt(mSensor, *timestamp))
                return false;
        }

        switch (mSensor->idx) {
        case PRESS:
            mSensor->sADataEvt->samples[*sampleNum].fdata = ((data[2] << 16) | (data[1] << 8) | data[0]) * LSM6DSM_PRESS_KSCALE;
            break;
        default:
            return false;
        }

        if (*sampleNum > 0) {
            mSensor->sADataEvt->samples[*sampleNum].deltaTime = *timestamp - mSensor->pushedTimestamp;
            mSensor->pushedTimestamp = *timestamp;
        }

        *sampleNum += 1;

        mSensor->samplesDecimatorCounter = 0;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_pushData: push slab to nanohub
 * @sidx: sensor index.
 * @numSamples: number of samples in the slab.
 */
static void lsm6dsm_pushData(enum SensorIndex sidx, uint16_t *numSamples)
{
    TDECL();
    bool triaxial = true;

#if defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    if (sidx == PRESS)
        triaxial = false;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

    if (triaxial) {
        T(sensors[sidx]).tADataEvt->samples[0].firstSample.numSamples = *numSamples;
        osEnqueueEvtOrFree(sensorGetMyEventType(LSM6DSMSensorInfo[sidx].sensorType), T(sensors[sidx]).tADataEvt, lsm6dsm_threeAxisDataEvtFree);
        T(sensors[sidx]).tADataEvt = NULL;
    } else {
#if defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
        T(sensors[sidx]).sADataEvt->samples[0].firstSample.numSamples = *numSamples;
        osEnqueueEvtOrFree(sensorGetMyEventType(LSM6DSMSensorInfo[sidx].sensorType), T(sensors[sidx]).sADataEvt, lsm6dsm_oneAxisDataEvtFree);
        T(sensors[sidx]).sADataEvt = NULL;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    }

    *numSamples = 0;
}

/*
 * lsm6dsm_parseFifoData: processing FIFO data.
 * @data: FIFO data.
 * @numPattern: number of pattern inside data.
 */
static void lsm6dsm_parseFifoData(uint8_t *data, uint16_t numPattern)
{
    TDECL();
    uint16_t j, fifoCounter = 0, samplesCounter[FIFO_NUM] = { 0 };
    struct LSM6DSMSensor *sensor;
    uint32_t sampleTimestamp;
    int32_t timestampDiffLSB;
    uint64_t timestamp = 0;
    enum SensorIndex sidx;
    uint8_t i, n;

    for (j = 0; j < numPattern; j++) {
        for (i = 0; i < T(fifoCntl).maxMinDecimator; i++) {
            sampleTimestamp = ((data[fifoCounter + T(fifoCntl).timestampPosition[i] + 1] << 16) |
                            (data[fifoCounter + T(fifoCntl).timestampPosition[i]] << 8) |
                            data[fifoCounter + T(fifoCntl).timestampPosition[i] + 3]);

            if (T(time).sampleTimestampFromFifoLSB > 0) {
                timestampDiffLSB = (int32_t)sampleTimestamp - (int32_t)(T(time).sampleTimestampFromFifoLSB & LSM6DSM_MASK_24BIT_TIMESTAMP);

                if ((timestampDiffLSB < 0) || (timestampDiffLSB > (T(time).theoreticalDeltaTimeLSB + T(time).deltaTimeMarginLSB))) {
                    if (timestampDiffLSB < -LSM6DSM_TIMEDIFF_OVERFLOW_LSB) {
                        T(time).sampleTimestampFromFifoLSB += (UINT32_MAX >> 8) + 1;
                    } else {
                        if (T(time).timestampIsValid)
                            sampleTimestamp = (T(time).sampleTimestampFromFifoLSB & LSM6DSM_MASK_24BIT_TIMESTAMP) + T(time).theoreticalDeltaTimeLSB;
                        else
                            sampleTimestamp = 0;
                    }
                } else
                    T(time).timestampIsValid = true;
            }

            T(time).sampleTimestampFromFifoLSB = (T(time).sampleTimestampFromFifoLSB & ~LSM6DSM_MASK_24BIT_TIMESTAMP) + sampleTimestamp;

            if (T(time).timestampIsValid) {
                if (!time_sync_estimate_time1(&T(time).sensorTimeToRtcData, (uint64_t)T(time).sampleTimestampFromFifoLSB * LSM6DSM_TIME_RESOLUTION, &timestamp)) {
                    timestamp = 0;
                } else {
                    if (T(time).lastSampleTimestamp > 0) {
                        if ((int64_t)timestamp <= (int64_t)T(time).lastSampleTimestamp)
                            timestamp = 0;
                    }

                    T(time).lastSampleTimestamp = timestamp > 0 ? timestamp : T(time).lastSampleTimestamp;

                }
            }

            for (n = 0; n < FIFO_NUM; n++) {
                if ((T(fifoCntl).decimators[n] > 0) && ((i % (T(fifoCntl).decimators[n] / T(fifoCntl).minDecimator)) == 0)) {
                    sidx = T(fifoCntl).decimatorsIdx[n];
                    if (sidx != EMBEDDED_TIMESTAMP) {
                        sensor = &T(sensors[sidx]);

                        if (sensor->samplesToDiscard == 0) {
                            if (++sensor->samplesFifoDecimatorCounter >= sensor->samplesFifoDecimator) {
                                switch (sidx) {
                                case GYRO:
                                case ACCEL:
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
                                case MAGN:
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
                                    lsm6dsm_processSensorThreeAxisData(sensor, &data[fifoCounter], &samplesCounter[n], &timestamp);
                                    break;

#if defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) && !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
                                case PRESS:
                                    if (T(sensors[PRESS]).enabled)
                                        lsm6dsm_processSensorOneAxisData(sensor, &data[fifoCounter], &samplesCounter[n], &timestamp);

                                    if (T(sensors[TEMP]).enabled) {
                                        union EmbeddedDataPoint tempData;

                                        tempData.fdata = ((int16_t)(data[fifoCounter + LSM6DSM_PRESS_OUTDATA_LEN + 1] << 8) |
                                                    data[fifoCounter + LSM6DSM_PRESS_OUTDATA_LEN]) * LSM6DSM_TEMP_KSCALE;

                                        osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TEMP), tempData.vptr, NULL);
                                    }

                                    break;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED, LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

                                default:
                                    break;
                                }

                                sensor->samplesFifoDecimatorCounter = 0;

                                if (samplesCounter[n] >= (LSM6DSM_MAX_NUM_COMMS_EVENT_SAMPLE - 1))
                                    lsm6dsm_pushData(sidx, &samplesCounter[n]);
                            }
                        } else
                            sensor->samplesToDiscard--;
                    } else {
                        if (T(sensors[STEP_COUNTER].enabled) && !T(readSteps)) {
                            uint16_t steps = data[fifoCounter + 4] | (data[fifoCounter + 5] << 8);

                            if (steps != T(totalNumSteps)) {
                                union EmbeddedDataPoint stepCntData;

                                stepCntData.idata = steps;
                                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_COUNT), stepCntData.vptr, NULL);
                                DEBUG_PRINT("Step Counter update: %ld steps\n", stepCntData.idata);
                                T(totalNumSteps) = stepCntData.idata;
                            }
                        }
                    }

                    fifoCounter += LSM6DSM_ONE_SAMPLE_BYTE;
                }
            }
        }
    }

    for (n = 0; n < FIFO_NUM; n++) {
        if (samplesCounter[n])
            lsm6dsm_pushData(T(fifoCntl).decimatorsIdx[n], &samplesCounter[n]);
    }
}

/*
 * lsm6dsm_updateSyncTaskValues: read timestamp used for time calibration and temperature
 */
static inline void lsm6dsm_updateSyncTaskValues(void)
{
    TDECL();
    uint32_t sensorTimestamp;

    sensorTimestamp = ((T_SLAVE_INTERFACE(timestampDataBuffer[1]) << 0) |
                    (T_SLAVE_INTERFACE(timestampDataBuffer[2]) << 8) |
                    (T_SLAVE_INTERFACE(timestampDataBuffer[3]) << 16));

    if (T(time).timestampSyncTaskLSB > 0) {
        if (((int32_t)sensorTimestamp - (int32_t)(T(time).timestampSyncTaskLSB & LSM6DSM_MASK_24BIT_TIMESTAMP)) < -LSM6DSM_TIMEDIFF_OVERFLOW_LSB)
            T(time).timestampSyncTaskLSB += (UINT32_MAX >> 8) + 1;
    }

    T(time).timestampSyncTaskLSB = (T(time).timestampSyncTaskLSB & ~LSM6DSM_MASK_24BIT_TIMESTAMP) + sensorTimestamp;

    time_sync_add(&T(time).sensorTimeToRtcData, T(time).timeSyncRtcTime, (uint64_t)T(time).timestampSyncTaskLSB * LSM6DSM_TIME_RESOLUTION);

#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    T(currentTemperature) = LSM6DSM_TEMP_OFFSET +
            (float)((int16_t)((T_SLAVE_INTERFACE(tempDataBuffer[2]) << 8) | T_SLAVE_INTERFACE(tempDataBuffer[1]))) / 256.0f;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */
}

/*
 * lsm6dsm_handleSpiDoneEvt: all SPI operation fall back here
 * @evtData: event data.
 */
static void lsm6dsm_handleSpiDoneEvt(const void *evtData)
{
    TDECL();
    bool returnIdle = false, resetFIFO = false;
    struct LSM6DSMSensor *mSensor;
    int i;

    switch (GET_STATE()) {
    case SENSOR_BOOT:
        SET_STATE(SENSOR_VERIFY_WAI);

        SPI_READ(LSM6DSM_WAI_ADDR, 1, &T_SLAVE_INTERFACE(tmpDataBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case SENSOR_VERIFY_WAI:
        if (T_SLAVE_INTERFACE(tmpDataBuffer[1]) != LSM6DSM_WAI_VALUE) {
            T(mRetryLeft)--;
            if (T(mRetryLeft) == 0)
                break;

            ERROR_PRINT("`Who-Am-I` register value not valid: %x\n", T_SLAVE_INTERFACE(tmpDataBuffer[1]));
            SET_STATE(SENSOR_BOOT);
            timTimerSet(100000000, 100, 100, lsm6dsm_timerCallback, NULL, true);
        } else {
            SET_STATE(SENSOR_INITIALIZATION);
            T(initState) = RESET_LSM6DSM;
            lsm6dsm_sensorInit();
        }

        break;

    case SENSOR_INITIALIZATION:
        if (T(initState) == INIT_DONE) {
            for (i = 0; i < NUM_SENSORS; i++) {
                sensorRegisterInitComplete(T(sensors[i]).handle);
            }

                returnIdle = true;
            } else
                lsm6dsm_sensorInit();

        break;

    case SENSOR_POWERING_UP:
        mSensor = (struct LSM6DSMSensor *)evtData;

        mSensor->enabled = true;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 1, 0);
        returnIdle = true;
        break;

    case SENSOR_POWERING_DOWN:
        mSensor = (struct LSM6DSMSensor *)evtData;

        mSensor->enabled = false;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 0, 0);
        returnIdle = true;
        break;

    case SENSOR_CONFIG_CHANGING:
        mSensor = (struct LSM6DSMSensor *)evtData;

        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_RATE_CHG, mSensor->rate[mSensor->idx], mSensor->latency);
        returnIdle = true;
        break;

    case SENSOR_CONFIG_WATERMARK_CHANGING:
        returnIdle = true;
        break;

    case SENSOR_CALIBRATION:
        mSensor = (struct LSM6DSMSensor *)evtData;

        if (T(calibrationState == CALIBRATION_COMPLETED)) {
            returnIdle = true;
        } else {
            lsm6dsm_runCalibrationProgram(mSensor->idx);
        }
        break;

    case SENSOR_STORE_CALIBRATION_DATA:
        returnIdle = true;
        break;

    case SENSOR_SELFTEST:
        mSensor = (struct LSM6DSMSensor *)evtData;

        if (T(selftestState == SELFTEST_COMPLETED)) {
            returnIdle = true;
        } else {
#ifdef LSM6DSM_I2C_MASTER_AK09916
            if (mSensor->idx == MAGN) {
                lsm6dsm_runAbsoluteSelfTestProgram();
            } else {
                lsm6dsm_runGapSelfTestProgram(mSensor->idx);
            }
#else /* LSM6DSM_I2C_MASTER_AK09916 */
            lsm6dsm_runGapSelfTestProgram(mSensor->idx);
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
        }

        break;

    case SENSOR_INT1_STATUS_REG_HANDLING:
        if (T(sensors[STEP_DETECTOR].enabled) && (T_SLAVE_INTERFACE(funcSrcBuffer[1]) & LSM6DSM_FUNC_SRC_STEP_DETECTED)) {
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_DETECT), NULL, NULL);
            DEBUG_PRINT("Step Detected!\n");
        }

        if (T(sensors[SIGN_MOTION].enabled) && (T_SLAVE_INTERFACE(funcSrcBuffer[1]) & LSM6DSM_FUNC_SRC_SIGN_MOTION)) {
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_SIG_MOTION), NULL, NULL);
            DEBUG_PRINT("Significant Motion event!\n");
        }

        if ((T_SLAVE_INTERFACE(fifoStatusRegBuffer[2]) & LSM6DSM_FIFO_STATUS2_FIFO_ERROR) == 0) {
            T(fifoDataToRead) = (((T_SLAVE_INTERFACE(fifoStatusRegBuffer[2]) & LSM6DSM_FIFO_CTRL2_FTH_MASK) << 8) | T_SLAVE_INTERFACE(fifoStatusRegBuffer[1])) * 2;

            if (T(fifoDataToRead) > LSM6DSM_SPI_FIFO_SIZE) {
                T(fifoDataToReadPending) = T(fifoDataToRead);
                T(fifoDataToRead) = LSM6DSM_SPI_FIFO_SIZE - (LSM6DSM_SPI_FIFO_SIZE % (T(fifoCntl).totalSip * LSM6DSM_ONE_SAMPLE_BYTE));
                T(fifoDataToReadPending) -= T(fifoDataToRead);
            } else {
                T(fifoDataToReadPending) = 0;

                if (T(fifoDataToRead) >= (T(fifoCntl).totalSip * LSM6DSM_ONE_SAMPLE_BYTE))
                    T(fifoDataToRead) -= T(fifoDataToRead) % (T(fifoCntl).totalSip * LSM6DSM_ONE_SAMPLE_BYTE);
                else
                    T(fifoDataToRead) = 0;
            }

            if (T(fifoDataToRead) > 0) {
                if (T(time).status == TIME_SYNC_DURING_FIFO_READ) {
                    uint64_t time = sensorGetTime();
                    if ((time - T(time).noTimer.lastTimestampDataAvlRtcTime) > LSM6DSM_SYNC_DELTA_INTERVAL) {
                        T(time).noTimer.newTimestampDataAvl = true;
                        T(time).noTimer.lastTimestampDataAvlRtcTime = time;

                        SPI_READ(LSM6DSM_TIMESTAMP0_REG_ADDR, LSM6DSM_TIMESTAMP_SAMPLE_BYTE, &T_SLAVE_INTERFACE(timestampDataBuffer));
#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
                        SPI_READ(LSM6DSM_OUT_TEMP_L_ADDR, LSM6DSM_TEMP_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tempDataBuffer));
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */
                    }
                }

                SPI_READ(LSM6DSM_FIFO_DATA_OUT_L_ADDR, T(fifoDataToRead), &T_SLAVE_INTERFACE(fifoDataBuffer));
            }
        } else {
            T(fifoDataToRead) = 0;

            if ((T_SLAVE_INTERFACE(fifoStatusRegBuffer[2]) & LSM6DSM_FIFO_STATUS2_FIFO_FULL_SMART) ||
                                    (T_SLAVE_INTERFACE(fifoStatusRegBuffer[2]) & LSM6DSM_FIFO_STATUS2_FIFO_FULL_OVERRUN)) {
                resetFIFO = true;
                SPI_WRITE(LSM6DSM_FIFO_CTRL5_ADDR, LSM6DSM_FIFO_BYPASS_MODE, 25);
                SPI_WRITE(LSM6DSM_FIFO_CTRL5_ADDR, LSM6DSM_FIFO_CONTINUOS_MODE);
            }

            if (T(sensors[STEP_COUNTER].enabled) && (T_SLAVE_INTERFACE(funcSrcBuffer[1]) & LSM6DSM_FUNC_SRC_STEP_COUNT_DELTA_IA)) {
                T(readSteps) = true;
                SPI_READ(LSM6DSM_STEP_COUNTER_L_ADDR, 2, &T_SLAVE_INTERFACE(stepCounterDataBuffer));
            }
        }

        if (!T(readSteps) && (T(fifoDataToRead) == 0)) {
            for (i = 0; i < NUM_SENSORS; i++) {
                if (T(sendFlushEvt[i])) {
                    osEnqueueEvt(sensorGetMyEventType(LSM6DSMSensorInfo[i].sensorType), SENSOR_DATA_EVENT_FLUSH, NULL);
                    T(sendFlushEvt[i]) = false;
                }
            }

            if (resetFIFO) {
                SET_STATE(SENSOR_INVALID_STATE);
                lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
            } else
                returnIdle = true;

            break;
        }

        SET_STATE(SENSOR_INT1_OUTPUT_DATA_HANDLING);

        if (T(fifoDataToRead) > 0) {
            T(lastFifoReadTimestamp) = sensorGetTime();

            if (T(time).noTimer.newTimestampDataAvl)
                T(time).timeSyncRtcTime = T(lastFifoReadTimestamp);
        }

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);

        break;

    case SENSOR_INT1_OUTPUT_DATA_HANDLING:
        if (T(fifoDataToRead) > 0) {
            if (T(time).noTimer.newTimestampDataAvl) {
                T(time).noTimer.newTimestampDataAvl = false;
                lsm6dsm_updateSyncTaskValues();
            }

            lsm6dsm_parseFifoData(&T_SLAVE_INTERFACE(fifoDataBuffer[1]), (T(fifoDataToRead) / 6) / T(fifoCntl).totalSip);

            if (T(fifoDataToReadPending) > 0) {
                T(fifoDataToRead) = T(fifoDataToReadPending);

                if (T(fifoDataToRead) > LSM6DSM_SPI_FIFO_SIZE) {
                    T(fifoDataToReadPending) = T(fifoDataToRead);
                    T(fifoDataToRead) = LSM6DSM_SPI_FIFO_SIZE - (LSM6DSM_SPI_FIFO_SIZE % (T(fifoCntl).totalSip * LSM6DSM_ONE_SAMPLE_BYTE));
                    T(fifoDataToReadPending) -= T(fifoDataToRead);
                } else {
                    T(fifoDataToReadPending) = 0;

                    if (T(fifoDataToRead) >= (T(fifoCntl).totalSip * LSM6DSM_ONE_SAMPLE_BYTE))
                        T(fifoDataToRead) -= T(fifoDataToRead) % (T(fifoCntl).totalSip * LSM6DSM_ONE_SAMPLE_BYTE);
                    else
                        T(fifoDataToRead) = 0;
                }

                if (T(fifoDataToRead) > 0) {
                    SPI_READ(LSM6DSM_FIFO_DATA_OUT_L_ADDR, T(fifoDataToRead), &T_SLAVE_INTERFACE(fifoDataBuffer));
                    lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
                    return;
                }
            } else
                T(fifoDataToRead) = 0;
        }

        for (i = 0; i < NUM_SENSORS; i++) {
            if (T(sendFlushEvt[i])) {
                osEnqueueEvt(sensorGetMyEventType(LSM6DSMSensorInfo[i].sensorType), SENSOR_DATA_EVENT_FLUSH, NULL);
                T(sendFlushEvt[i]) = false;
            }
        }

        if (T(readSteps)) {
            union EmbeddedDataPoint stepCntData;

            stepCntData.idata = T_SLAVE_INTERFACE(stepCounterDataBuffer[1]) | (T_SLAVE_INTERFACE(stepCounterDataBuffer[2]) << 8);
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_COUNT), stepCntData.vptr, NULL);
            DEBUG_PRINT("Step Counter update: %ld steps\n", stepCntData.idata);
            T(totalNumSteps) = stepCntData.idata;
            T(readSteps) = false;
        }

        returnIdle = true;
        break;

    case SENSOR_TIME_SYNC: ;
        lsm6dsm_updateSyncTaskValues();

        if (T(time).status == TIME_SYNC_TIMER) {
            if (timTimerSet(LSM6DSM_SYNC_DELTA_INTERVAL, 100, 100, lsm6dsm_timerSyncCallback, NULL, true) == 0)
                ERROR_PRINT("Failed to set a timer for time sync\n");
        }

        returnIdle = true;
        break;

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    case SENSOR_BARO_READ_DATA: ;
        uint16_t samplesCounter = 0;
        uint32_t sensorTimestamp;
        uint64_t timestamp;

        sensorTimestamp = ((T_SLAVE_INTERFACE(timestampDataBufferBaro[1]) << 0) |
                        (T_SLAVE_INTERFACE(timestampDataBufferBaro[2]) << 8) |
                        (T_SLAVE_INTERFACE(timestampDataBufferBaro[3]) << 16));

        if (T(time).timestampBaroLSB > 0) {
            if (((int32_t)sensorTimestamp - (int32_t)(T(time).timestampBaroLSB & LSM6DSM_MASK_24BIT_TIMESTAMP)) < -LSM6DSM_TIMEDIFF_OVERFLOW_LSB)
                T(time).timestampBaroLSB += (UINT32_MAX >> 8) + 1;
        }

        T(time).timestampBaroLSB = (T(time).timestampBaroLSB & ~LSM6DSM_MASK_24BIT_TIMESTAMP) + sensorTimestamp;

        if (time_sync_estimate_time1(&T(time).sensorTimeToRtcData, (uint64_t)T(time).timestampBaroLSB * LSM6DSM_TIME_RESOLUTION, &timestamp)) {
            if (T(sensors[PRESS]).enabled) {
                lsm6dsm_processSensorOneAxisData(&T(sensors[PRESS]), &T_SLAVE_INTERFACE(baroDataBuffer[1]), &samplesCounter, &timestamp);
                lsm6dsm_pushData(PRESS, &samplesCounter);
            }
        }

        if (T(sensors[TEMP]).enabled) {
            union EmbeddedDataPoint tempData;

            tempData.fdata = ((int16_t)(T_SLAVE_INTERFACE(baroDataBuffer[LSM6DSM_PRESS_OUTDATA_LEN + 2]) << 8) |
                            T_SLAVE_INTERFACE(baroDataBuffer[LSM6DSM_PRESS_OUTDATA_LEN + 1])) * LSM6DSM_TEMP_KSCALE;

            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TEMP), tempData.vptr, NULL);
        }

        returnIdle = true;
        break;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

    default:
        returnIdle = true;
        break;
    }

    if (returnIdle)
        lsm6dsm_processPendingEvt();
}

/*
 * lsm6dsm_handleEvent: handle driver events
 * @evtType: event type.
 * @evtData: event data.
 */
static void lsm6dsm_handleEvent(uint32_t evtType, const void *evtData)
{
    TDECL();
    struct LSM6DSMSensor *mSensor;

    switch (evtType) {
    case EVT_APP_START: ;
        uint64_t currTime;

        T(mRetryLeft) = LSM6DSM_RETRY_CNT_WAI;
        SET_STATE(SENSOR_BOOT);
        osEventUnsubscribe(T(tid), EVT_APP_START);

        /* Sensor need 100ms to boot, use a timer callback to continue */
        currTime = timGetTime();
        if (currTime < 100000000ULL) {
            timTimerSet(100000000 - currTime, 100, 100, lsm6dsm_timerCallback, NULL, true);
            break;
        }

        /* If 100ms already passed just fall through next step */
    case EVT_SPI_DONE:
        lsm6dsm_handleSpiDoneEvt(evtData);
        break;

    case EVT_SENSOR_INTERRUPT_1:
        lsm6dsm_readStatusReg(false);
        break;

    case EVT_SENSOR_POWERING_UP:
        mSensor = (struct LSM6DSMSensor *)evtData;

        mSensor->enabled = true;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 1, 0);
        lsm6dsm_processPendingEvt();
        break;

    case EVT_SENSOR_POWERING_DOWN:
        mSensor = (struct LSM6DSMSensor *)evtData;

        mSensor->enabled = false;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 0, 0);
        lsm6dsm_processPendingEvt();
        break;

    case EVT_SENSOR_CONFIG_CHANGING:
        mSensor = (struct LSM6DSMSensor *)evtData;

        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_RATE_CHG, mSensor->rate[mSensor->idx], mSensor->latency);
        lsm6dsm_processPendingEvt();
        break;

    case EVT_APP_FROM_HOST:
        break;

    case EVT_SENSOR_RESTORE_IDLE:
        lsm6dsm_processPendingEvt();
        break;

    case EVT_TIME_SYNC:
        lsm6dsm_timeSyncTask();
        break;

    default:
        break;
    }
}

/*
 * lsm6dsm_initSensorStruct: initialize sensor struct variable
 * @sensor: sensor info.
 * @idx: sensor index.
 */
static void lsm6dsm_initSensorStruct(struct LSM6DSMSensor *sensor, enum SensorIndex idx)
{
    TDECL();
    uint8_t i;

    for (i = 0; i < NUM_SENSORS; i++) {
        if (i == idx)
            sensor->dependenciesRequireData[i] = true;
        else
            sensor->dependenciesRequireData[i] = false;

        sensor->rate[i] = 0;
    }

    sensor->idx = idx;
    sensor->hwRate = 0;
    sensor->latency = UINT64_MAX;
    sensor->enabled = false;
    sensor->samplesToDiscard = 0;
    sensor->samplesDecimator = 1;
    sensor->samplesDecimatorCounter = 0;
    sensor->samplesFifoDecimator = 1;
    sensor->samplesFifoDecimatorCounter = 0;
    sensor->tADataEvt = NULL;
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    sensor->sADataEvt = NULL;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
}

/*
 * lsm6dsm_startTask: first function executed when App start
 * @taskId: task id.
 */
static bool lsm6dsm_startTask(uint32_t taskId)
{
    TDECL();
    enum SensorIndex i;
    size_t slabSize;
    int err;

    DEBUG_PRINT("IMU: %lu\n", taskId);

    T(tid) = taskId;
    T(int1) = gpioRequest(LSM6DSM_INT1_GPIO);
    T(isr1).func = lsm6dsm_isr1;

    T_SLAVE_INTERFACE(mode).speed = LSM6DSM_SPI_SLAVE_FREQUENCY_HZ;
    T_SLAVE_INTERFACE(mode).bitsPerWord = 8;
    T_SLAVE_INTERFACE(mode).cpol = SPI_CPOL_IDLE_HI;
    T_SLAVE_INTERFACE(mode).cpha = SPI_CPHA_TRAILING_EDGE;
    T_SLAVE_INTERFACE(mode).nssChange = true;
    T_SLAVE_INTERFACE(mode).format = SPI_FORMAT_MSB_FIRST;
    T_SLAVE_INTERFACE(cs) = LSM6DSM_SPI_SLAVE_CS_GPIO;

    DEBUG_PRINT("Requested SPI on bus #%d @%dHz, int1 on gpio#%d\n",
            LSM6DSM_SPI_SLAVE_BUS_ID, LSM6DSM_SPI_SLAVE_FREQUENCY_HZ, LSM6DSM_INT1_GPIO);

    err = spiMasterRequest(LSM6DSM_SPI_SLAVE_BUS_ID, &T_SLAVE_INTERFACE(spiDev));
    if (err < 0) {
        ERROR_PRINT("Failed to request SPI on this bus: #%d\n", LSM6DSM_SPI_SLAVE_BUS_ID);
        return false;
    }

    T(int1Register) = LSM6DSM_INT1_CTRL_BASE;
    T(int2Register) = LSM6DSM_INT2_CTRL_BASE;
    T(embeddedFunctionsRegister) = LSM6DSM_CTRL10_C_BASE;
    T(pedometerDependencies) = 0;
    T(pendingInt) = false;
    T(pendingTimeSyncTask) = false;
    T(lastFifoReadTimestamp) = 0;
    T(totalNumSteps) = 0;
    T(time).status = TIME_SYNC_DISABLED;
#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    T(currentTemperature) = 0;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    T(masterConfigRegister) = LSM6DSM_MASTER_CONFIG_BASE;
    T(masterConfigDependencies) = 0;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
    T(baroTimerId) = 0;
    T(pendingBaroTimerTask) = false;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    memset(T(gyroCalibrationData), 0, LSM6DSM_TRIAXIAL_NUM_AXIS * sizeof(int32_t));

    slabSize = sizeof(struct TripleAxisDataEvent) + (LSM6DSM_MAX_NUM_COMMS_EVENT_SAMPLE * sizeof(struct TripleAxisDataPoint));

    T(mDataSlabThreeAxis) = slabAllocatorNew(slabSize, 4, 20);
    if (!T(mDataSlabThreeAxis)) {
        ERROR_PRINT("Failed to allocate mDataSlabThreeAxis memory\n");
        spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
        return false;
    }

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    slabSize = sizeof(struct SingleAxisDataEvent) + (LSM6DSM_MAX_NUM_COMMS_EVENT_SAMPLE * sizeof(struct SingleAxisDataPoint));

    T(mDataSlabOneAxis) = slabAllocatorNew(slabSize, 4, 20);
    if (!T(mDataSlabOneAxis)) {
        ERROR_PRINT("Failed to allocate mDataSlabOneAxis memory\n");
        slabAllocatorDestroy(T(mDataSlabThreeAxis));
        spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
        return false;
    }
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

    for (i = 0; i < NUM_SENSORS; i++) {
        T(pendingEnableConfig[i]) = false;
        T(pendingRateConfig[i]) = false;
        T(pendingFlush[i]) = 0;
        T(sendFlushEvt[i]) = false;
        lsm6dsm_initSensorStruct(&T(sensors[i]), i);
        T(sensors[i]).handle = sensorRegister(&LSM6DSMSensorInfo[i], &LSM6DSMSensorOps[i], NULL, false);
    }

    T(fifoCntl).decimatorsIdx[FIFO_GYRO] = GYRO;
    T(fifoCntl).decimatorsIdx[FIFO_ACCEL] = ACCEL;
    T(fifoCntl).decimatorsIdx[FIFO_DS3] = NUM_SENSORS;
    T(fifoCntl).decimatorsIdx[FIFO_DS4] = EMBEDDED_TIMESTAMP;

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    T(fifoCntl).decimatorsIdx[FIFO_DS3] = MAGN;
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    T(fifoCntl).decimatorsIdx[FIFO_DS3] = PRESS;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    accelCalInit(&T(accelCal),
            800000000,                  /* stillness period [ns] */
            5,                          /* minimum sample number */
            0.00025,                    /* threshold */
            15,                         /* nx bucket count */
            15,                         /* nxb bucket count */
            15,                         /* ny bucket count */
            15,                         /* nyb bucket count */
            15,                         /* nz bucket count */
            15,                         /* nzb bucket count */
            15);                        /* nle bucket count */
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    gyroCalInit(&T(gyroCal),
            5e9,                        /* min stillness period [ns] */
            6e9,                        /* max stillness period [ns] */
            0, 0, 0,                    /* initial bias offset calibration values [rad/sec] */
            0,                          /* timestamp of initial bias calibration [ns] */
            1.5e9,                      /* analysis window length = 1.5 seconds [ns] */
            5e-5f,                      /* gyroscope variance threshold [(rad/sec)^2] */
            1e-5f,                      /* gyroscope confidence delta [(rad/sec)^2] */
            8e-3f,                      /* accelerometer variance threshold [(m/sec^2)^2] */
            1.6e-3f,                    /* accelerometer confidence delta [(m/sec^2)^2] */
            1.4f,                       /* magnetometer variance threshold [uT^2] */
            0.25,                       /* magnetometer confidence delta [uT^2] */
            0.95f,                      /* stillness threshold [0, 1] */
            40.0e-3f * M_PI / 180.0f,   /* stillness mean variation limit [rad/sec] */
            1.5f,                       /* maximum temperature deviation during stillness */
            true);                      /* gyro calibration enable */
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

#ifdef LSM6DSM_OVERTEMP_CALIB_ENABLED
    overTempCalInit(&T(overTempCal),
            5,                          /* min num of points to enable model update */
            5000000000,                 /* min model update interval [ns] */
            0.75f,                      /* temperature span of bin method [C] */
            50.0e-3f * M_PI / 180.0f,   /* model fit tolerance [rad/sec/] */
            172800000000000,            /* model data point age limit [ns] */
            50.0e-3f * M_PI / 180.0f,   /* limit for temperature sensitivity [(rad/sec)/C] */
            3.0f * M_PI / 180.0f,       /* limit for model intercept parameter [rad/sec] */
            true);                      /* over temperature compensation enable */
#endif /* LSM6DSM_OVERTEMP_CALIB_ENABLED */

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    initMagCal(&T(magnCal),
            0.0f, 0.0f, 0.0f,           /* magn offset x - y - z */
            1.0f, 0.0f, 0.0f,           /* magn scale matrix c00 - c01 - c02 */
            0.0f, 1.0f, 0.0f,           /* magn scale matrix c10 - c11 - c12 */
            0.0f, 0.0f, 1.0f);          /* magn scale matrix c20 - c21 - c22 */
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

    /* Initialize index used to fill/get data from buffer */
    T_SLAVE_INTERFACE(mWbufCnt) = 0;
    T_SLAVE_INTERFACE(mRegCnt) = 0;

    time_sync_init(&T(time).sensorTimeToRtcData);

    osEventSubscribe(T(tid), EVT_APP_START);

    DEBUG_PRINT("Enabling gpio#%d connected to int1\n", LSM6DSM_INT1_GPIO);
    lsm6dsm_enableInterrupt(T(int1), &T(isr1));

    return true;
}

/*
 * lsm6dsm_endTask: last function executed when App end
 */
static void lsm6dsm_endTask(void)
{
    TDECL();
    enum SensorIndex i;

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    accelCalDestroy(&T(accelCal));
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    magCalDestroy(&T(magnCal));
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    gyroCalDestroy(&T(gyroCal));
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

    lsm6dsm_disableInterrupt(T(int1), &T(isr1));
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    slabAllocatorDestroy(T(mDataSlabOneAxis));
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    slabAllocatorDestroy(T(mDataSlabThreeAxis));
    spiMasterRelease(T_SLAVE_INTERFACE(spiDev));

    for (i = 0; i < NUM_SENSORS; i++)
        sensorUnregister(T(sensors[i]).handle);

    gpioRelease(T(int1));
}

INTERNAL_APP_INIT(LSM6DSM_APP_ID, 0, lsm6dsm_startTask, lsm6dsm_endTask, lsm6dsm_handleEvent);
