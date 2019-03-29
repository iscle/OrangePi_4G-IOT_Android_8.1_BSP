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

#ifndef __LSM6DSM_I2C_MASTER_LSM303AGR__
#define __LSM6DSM_I2C_MASTER_LSM303AGR__

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
#ifndef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
#define LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED         1
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#error "Another magnetometer is already selected! One magn per time can be used."
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

#define LSM303AGR_KSCALE                                0.15f         /* MAGN scale in uT/LSB */
#define LSM303AGR_I2C_ADDRESS                           (0x1e)

/* LSM303AGR registers */
#define LSM303AGR_WAI_ADDR                              (0x4f)
#define LSM303AGR_CFG_REG_A_M_ADDR                      (0x60)
#define LSM303AGR_CFG_REG_B_M_ADDR                      (0x61)
#define LSM303AGR_CFG_REG_C_M_ADDR                      (0x62)
#define LSM303AGR_OUTDATA_ADDR                          (0x68)

#define LSM303AGR_SW_RESET                              (0x20)
#define LSM303AGR_POWER_ON_VALUE                        (0x00)
#define LSM303AGR_POWER_OFF_VALUE                       (0x03)
#define LSM303AGR_OUTDATA_LEN                           (0x06)
#define LSM303AGR_OFFSET_CANCELLATION                   (0x02)
#define LSM303AGR_ENABLE_SELFTEST                       (0x02)

/* Selftest related */
#define LSM303AGR_SELFTEST_HIGH_THR_LSB                 333
#define LSM303AGR_SELFTEST_LOW_THR_LSB                  10


/* LSM303AGR default base registers status */
/* LSM303AGR_CFG_REG_A_M_BASE: configuration register 1 default settings */
#define LSM303AGR_CFG_REG_A_M_BASE                     ((1 << 7) |    /* COMP_TEMP_EN */ \
                                                        (0 << 6) |    /* REBOOT */ \
                                                        (0 << 5) |    /* SOFT_RST */ \
                                                        (0 << 4) |    /* LP */ \
                                                        (0 << 3) |    /* ODR1 */ \
                                                        (0 << 2) |    /* ODR0 */ \
                                                        (0 << 1) |    /* MD1 */ \
                                                        (0 << 0))     /* MD0 */

/* LSM303AGR_CFC_REG_C_M_BASE: configuration register 3 default settings */
#define LSM303AGR_CFG_REG_C_M_BASE                     ((0 << 7) |    /* (0) */ \
                                                        (0 << 6) |    /* INT_MAG_PIN */ \
                                                        (0 << 5) |    /* I2C_DIS */ \
                                                        (1 << 4) |    /* BDU */ \
                                                        (0 << 3) |    /* BLE */ \
                                                        (0 << 2) |    /* (0) */ \
                                                        (0 << 1) |    /* SELFT_TEST */ \
                                                        (0 << 0))     /* INT_MAG */

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
/* MUST BE SAME LENGTH OF LSM6DSMMagnRates */
static uint8_t LSM303AGRMagnRatesRegValue[] = {
    0x00, /* Expected 0.8125Hz, ODR = 10Hz */
    0x00, /* Expected 1.625Hz, ODR = 10Hz */
    0x00, /* Expected 3.25Hz, ODR = 10Hz */
    0x00, /* Expected 6.5Hz, ODR = 10Hz */
    0x04, /* Expected 12.5Hz, ODR = 20Hz */
    0x0c, /* Expected 26Hz, ODR = 100Hz */
    0x0c, /* Expected 52Hz, ODR = 100Hz */
    0x0c, /* Expected 104Hz, ODR = 100Hz */
};
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

#endif /* __LSM6DSM_I2C_MASTER_LSM303AGR__ */
