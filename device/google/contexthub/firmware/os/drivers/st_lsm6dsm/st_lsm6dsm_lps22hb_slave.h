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

#ifndef __LSM6DSM_I2C_MASTER_LPS22HB__
#define __LSM6DSM_I2C_MASTER_LPS22HB__

#ifdef LSM6DSM_I2C_MASTER_LPS22HB
#ifndef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
#define LSM6DSM_I2C_MASTER_BAROMETER_ENABLED            1
#else /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
#error "Another barometer is already selected! One baro per time can be used."
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

#if !defined(LSM6DSM_LPS22HB_I2C_ADDRESS)
#error "LPS22HB i2c address macro not defined. Please check README file"
#endif /* LSM6DSM_LPS22HB_I2C_ADDRESS */

#else /* LSM6DSM_I2C_MASTER_LPS22HB */
#undef LSM6DSM_LPS22HB_I2C_ADDRESS
#define LSM6DSM_LPS22HB_I2C_ADDRESS                     1
#endif /* LSM6DSM_I2C_MASTER_LPS22HB */

#define LPS22HB_PRESS_KSCALE                            0.000244140625f    /* PRESS scale @1260hPa in hPa/LSB */
#define LPS22HB_TEMP_KSCALE                             0.01f              /* TEMP scale in *C/LSB */
#define LPS22HB_I2C_ADDRESS                             (LSM6DSM_LPS22HB_I2C_ADDRESS)

/* LPS22HB registers */
#define LPS22HB_WAI_ADDR                                (0x0f)
#define LPS22HB_CTRL1_ADDR                              (0x10)
#define LPS22HB_CTRL2_ADDR                              (0x11)
#define LPS22HB_OUTDATA_ADDR                            (0x28)

#define LPS22HB_SW_RESET                                (0x04)
#define LPS22HB_POWER_ON_VALUE                          (0x00)
#define LPS22HB_POWER_OFF_VALUE                         (0x00)
#define LPS22HB_OUTDATA_LEN                             (0x05)
#define LPS22HB_OUTDATA_PRESS_BYTE                      3
#define LPS22HB_OUTDATA_TEMP_BYTE                       2


/* LPS22HB default base registers status */
/* LPS22HB_CTRL1_BASE: control register 1 default settings */
#define LPS22HB_CTRL1_BASE                             ((0 << 7) |    /* (0) */ \
                                                        (0 << 6) |    /* ODR2 */ \
                                                        (0 << 5) |    /* ODR1 */ \
                                                        (0 << 4) |    /* ODR0 */ \
                                                        (0 << 3) |    /* EN_LPFP */ \
                                                        (0 << 2) |    /* LPF_CFG */ \
                                                        (1 << 1) |    /* BDU */ \
                                                        (0 << 0))     /* SIM */

/* LPS22HB_CTRL2_BASE: control register 2 default settings */
#define LPS22HB_CTRL2_BASE                             ((0 << 7) |    /* BOOT */ \
                                                        (0 << 6) |    /* FIFO_EN */ \
                                                        (0 << 5) |    /* STOP_ON_FTH */ \
                                                        (1 << 4) |    /* IF_ADD_INC */ \
                                                        (0 << 3) |    /* I2C_DIS */ \
                                                        (0 << 2) |    /* SWRESET */ \
                                                        (0 << 1) |    /* (0) */ \
                                                        (0 << 0))     /* ONE_SHOT */

#ifdef LSM6DSM_I2C_MASTER_LPS22HB
/* MUST BE SAME LENGTH OF LSM6DSMMagnRates */
static uint8_t LPS22HBBaroRatesRegValue[] = {
    0x20, /* Expected 0.8125Hz, ODR = 10Hz */
    0x20, /* Expected 1.625Hz, ODR = 10Hz */
    0x20, /* Expected 3.25Hz, ODR = 10Hz */
    0x20, /* Expected 6.5Hz, ODR = 10Hz */
    0x30, /* Expected 12.5Hz, ODR = 25Hz */
    0x40, /* Expected 26Hz, ODR = 50Hz */
    0x50, /* Expected 52Hz, ODR = 75Hz */
    0x50, /* Expected 104Hz, ODR = 75Hz */
};
#endif /* LSM6DSM_I2C_MASTER_LPS22HB */

#endif /* __LSM6DSM_I2C_MASTER_LPS22HB__ */
