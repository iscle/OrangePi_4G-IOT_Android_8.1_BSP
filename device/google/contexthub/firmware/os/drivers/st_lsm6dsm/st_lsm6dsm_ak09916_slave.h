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

#ifndef __LSM6DSM_I2C_MASTER_AK09916__
#define __LSM6DSM_I2C_MASTER_AK09916__

#ifdef LSM6DSM_I2C_MASTER_AK09916
#ifndef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
#define LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED         1
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#error "Another magnetometer is already selected! One magn per time can be used."
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#endif /* LSM6DSM_I2C_MASTER_AK09916 */

#define AK09916_KSCALE                                  0.15          /* MAGN scale in uT/LSB */
#define AK09916_I2C_ADDRESS                             (0x0c)

/* AK09916 registers */
#define AK09916_WAI_ADDR                                (0x00)
#define AK09916_CNTL2_ADDR                              (0x31)
#define AK09916_CNTL3_ADDR                              (0x32)
#define AK09916_OUTDATA_ADDR                            (0x11)
#define AK09916_STATUS_DATA_ADDR                        (0x18)

#define AK09916_SW_RESET                                (0x01)
#define AK09916_POWER_ON_VALUE                          (0x00)
#define AK09916_POWER_OFF_VALUE                         (0x00)
#define AK09916_OUTDATA_LEN                             (0x06)
#define AK09916_ENABLE_SELFTEST_MODE                    (0x10)

/* Selftest related */
#define AK09916_SELFTEST_HIGH_THR_XYZ_LSB               200
#define AK09916_SELFTEST_LOW_THR_XY_LSB                 -200
#define AK09916_SELFTEST_LOW_THR_Z_LSB                  -1000


/* AK09916 default base registers status */
/* AK09916_CNTL2_BASE: control register 2 default settings */
#define AK09916_CNTL2_BASE                             ((0 << 7) |    /* (0) */ \
                                                        (0 << 6) |    /* (0) */ \
                                                        (0 << 5) |    /* (0) */ \
                                                        (0 << 4) |    /* MODE4 */ \
                                                        (0 << 3) |    /* MODE3 */ \
                                                        (0 << 2) |    /* MODE2 */ \
                                                        (0 << 1) |    /* MODE1 */ \
                                                        (0 << 0))     /* MODE0 */

#ifdef LSM6DSM_I2C_MASTER_AK09916
/* MUST BE SAME LENGTH OF LSM6DSMMagnRates */
static uint8_t AK09916MagnRatesRegValue[] = {
    0x02, /* Expected 0.8125Hz, ODR = 10Hz */
    0x02, /* Expected 1.625Hz, ODR = 10Hz */
    0x02, /* Expected 3.25Hz, ODR = 10Hz */
    0x02, /* Expected 6.5Hz, ODR = 10Hz */
    0x04, /* Expected 12.5Hz, ODR = 20Hz */
    0x06, /* Expected 26Hz, ODR = 50Hz */
    0x08, /* Expected 52Hz, ODR = 100Hz */
    0x08, /* Expected 104Hz, ODR = 100Hz */
};
#endif /* LSM6DSM_I2C_MASTER_AK09916 */

#endif /* __LSM6DSM_I2C_MASTER_AK09916__ */
