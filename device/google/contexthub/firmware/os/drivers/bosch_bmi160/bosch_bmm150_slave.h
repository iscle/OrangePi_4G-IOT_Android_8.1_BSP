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

#ifndef BOSCH_BMM150_H_

#define BOSCH_BMM150_H_

#include <stdio.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define kScale_mag 0.0625f         // 1.0f / 16.0f;

#define BMM150_REG_DATA           0x42
#define BMM150_REG_CTRL_1         0x4b
#define BMM150_REG_CTRL_2         0x4c
#define BMM150_REG_REPXY          0x51
#define BMM150_REG_REPZ           0x52
#define BMM150_REG_DIG_X1         0x5d
#define BMM150_REG_DIG_Y1         0x5e
#define BMM150_REG_DIG_Z4_LSB     0x62
#define BMM150_REG_DIG_Z4_MSB     0x63
#define BMM150_REG_DIG_X2         0x64
#define BMM150_REG_DIG_Y2         0x65
#define BMM150_REG_DIG_Z2_LSB     0x68
#define BMM150_REG_DIG_Z2_MSB     0x69
#define BMM150_REG_DIG_Z1_LSB     0x6a
#define BMM150_REG_DIG_Z1_MSB     0x6b
#define BMM150_REG_DIG_XYZ1_LSB   0x6c
#define BMM150_REG_DIG_XYZ1_MSB   0x6d
#define BMM150_REG_DIG_Z3_LSB     0x6e
#define BMM150_REG_DIG_Z3_MSB     0x6f
#define BMM150_REG_DIG_XY2        0x70
#define BMM150_REG_DIG_XY1        0x71

#define BMM150_MAG_FLIP_OVERFLOW_ADCVAL     ((int16_t)-4096)
#define BMM150_MAG_HALL_OVERFLOW_ADCVAL     ((int16_t)-16384)
#define BMM150_MAG_OVERFLOW_OUTPUT          ((int16_t)-32768)
#define BMM150_CALIB_HEX_LACKS              0x100000
#define BMM150_MAG_OVERFLOW_OUTPUT_S32      ((int32_t)(-2147483647-1))

struct MagTask {
    uint16_t dig_z1;
    int16_t dig_z2, dig_z3, dig_z4;
    uint16_t dig_xyz1;
    uint8_t raw_dig_data[24];
    int8_t dig_x1, dig_y1, dig_x2, dig_y2;
    uint8_t dig_xy1;
    int8_t dig_xy2;
};

#define MAG_I2C_ADDR 0x10
#define MAG_REG_DATA BMM150_REG_DATA

void bmm150SaveDigData(struct MagTask *magTask, uint8_t *data, size_t offset);
void parseMagData(struct MagTask *magTask, uint8_t *buf, float *x, float *y, float *z);

#ifdef __cplusplus
}
#endif

#endif  // BOSCH_BMM150_H_
