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

#ifndef __LSM6DSM_I2C_MASTER_LIS3MDL__
#define __LSM6DSM_I2C_MASTER_LIS3MDL__

#ifdef LSM6DSM_I2C_MASTER_LIS3MDL
#ifndef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
#define LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED         1
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#error "Another magnetometer is already selected! One magn per time can be used."
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#if !defined(LSM6DSM_LIS3MDL_I2C_ADDRESS)
#error "LIS3MDL i2c address macro not defined. Please check README file"
#endif /* LSM6DSM_LIS3MDL_I2C_ADDRESS */

#else /* LSM6DSM_I2C_MASTER_LIS3MDL */
#undef LSM6DSM_LIS3MDL_I2C_ADDRESS
#define LSM6DSM_LIS3MDL_I2C_ADDRESS                     1
#endif /* LSM6DSM_I2C_MASTER_LIS3MDL */

#define LIS3MDL_KSCALE                                  0.043840420868f    /* MAGN scale @12G in uT/LSB */
#define LIS3MDL_I2C_ADDRESS                             (LSM6DSM_LIS3MDL_I2C_ADDRESS)

/* LIS3MDL registers */
#define LIS3MDL_WAI_ADDR                                (0x0f)
#define LIS3MDL_CTRL1_ADDR                              (0x20)
#define LIS3MDL_CTRL2_ADDR                              (0x21)
#define LIS3MDL_CTRL3_ADDR                              (0x22)
#define LIS3MDL_CTRL4_ADDR                              (0x23)
#define LIS3MDL_CTRL5_ADDR                              (0x24)
#define LIS3MDL_OUTDATA_ADDR                            (0x28)

#define LIS3MDL_SW_RESET                                (0x04)
#define LIS3MDL_POWER_ON_VALUE                          (0x00)
#define LIS3MDL_POWER_OFF_VALUE                         (0x03)
#define LIS3MDL_OUTDATA_LEN                             (0x06)
#define LIS3MDL_ENABLE_SELFTEST                         (0x01)

/* Selftest related */
#define LIS3MDL_SELFTEST_HIGH_THR_XY_LSB                6843
#define LIS3MDL_SELFTEST_HIGH_THR_Z_LSB                 2281
#define LIS3MDL_SELFTEST_LOW_THR_XY_LSB                 2281
#define LIS3MDL_SELFTEST_LOW_THR_Z_LSB                  228


/* LIS3MDL default base registers status */
/* LIS3MDL_CTRL1_BASE: control register 1 default settings */
#define LIS3MDL_CTRL1_BASE                             ((0 << 7) |    /* TEMP_EN */ \
                                                        (1 << 6) |    /* OM1 */ \
                                                        (1 << 5) |    /* OM0 */ \
                                                        (0 << 4) |    /* DO2 */ \
                                                        (0 << 3) |    /* DO1 */ \
                                                        (0 << 2) |    /* DO0 */ \
                                                        (0 << 1) |    /* FAST_ODR */ \
                                                        (0 << 0))     /* ST */

/* LIS3MDL_CTRL2_BASE: control register 2 default settings */
#define LIS3MDL_CTRL2_BASE                             ((0 << 7) |    /* (0) */ \
                                                        (1 << 6) |    /* FS1 */ \
                                                        (0 << 5) |    /* FS0 */ \
                                                        (0 << 4) |    /* (0) */ \
                                                        (0 << 3) |    /* REBOOT */ \
                                                        (0 << 2) |    /* SOFT_RST */ \
                                                        (0 << 1) |    /* (0) */ \
                                                        (0 << 0))     /* (0) */

/* LIS3MDL_CTRL3_BASE: control register 3 default settings */
#define LIS3MDL_CTRL3_BASE                              (0x00)

/* LIS3MDL_CTRL4_BASE: control register 4 default settings */
#define LIS3MDL_CTRL4_BASE                             ((0 << 7) |    /* (0) */ \
                                                        (0 << 6) |    /* (0) */ \
                                                        (0 << 5) |    /* (0) */ \
                                                        (0 << 4) |    /* (0) */ \
                                                        (1 << 3) |    /* OMZ1 */ \
                                                        (1 << 2) |    /* OMZ0 */ \
                                                        (0 << 1) |    /* BLE */ \
                                                        (0 << 0))     /* (0) */

/* LIS3MDL_CTRL5_BASE: control register 5 default settings */
#define LIS3MDL_CTRL5_BASE                             ((0 << 7) |    /* FAST_READ */ \
                                                        (1 << 6) |    /* BDU */ \
                                                        (0 << 5) |    /* (0) */ \
                                                        (0 << 4) |    /* (0) */ \
                                                        (0 << 3) |    /* (0) */ \
                                                        (0 << 2) |    /* (0) */ \
                                                        (0 << 1) |    /* (0) */ \
                                                        (0 << 0))     /* (0) */

#ifdef LSM6DSM_I2C_MASTER_LIS3MDL
/* MUST BE SAME LENGTH OF LSM6DSMMagnRates */
static uint8_t LIS3MDLMagnRatesRegValue[] = {
    0x0c, /* Expected 0.8125Hz, ODR = 5Hz */
    0x0c, /* Expected 1.625Hz, ODR = 5Hz */
    0x0c, /* Expected 3.25Hz, ODR = 5Hz */
    0x10, /* Expected 6.5Hz, ODR = 10Hz */
    0x14, /* Expected 12.5Hz, ODR = 20Hz */
    0x18, /* Expected 26Hz, ODR = 40Hz */
    0x1c, /* Expected 52Hz, ODR = 80Hz */
    0x1c, /* Expected 104Hz, ODR = 80Hz */
};
#endif /* LSM6DSM_I2C_MASTER_LIS3MDL */

#endif /* __LSM6DSM_I2C_MASTER_LIS3MDL__ */
