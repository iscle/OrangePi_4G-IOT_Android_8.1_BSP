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

#include <string.h>
#include "bosch_bmm150_slave.h"

void bmm150SaveDigData(struct MagTask *magTask, uint8_t *data, size_t offset)
{
    // magnetometer temperature calibration data is read in 3 bursts of 8 byte
    // length each.
    memcpy(&magTask->raw_dig_data[offset], data, 8);

    if (offset == 16) {
        // we have all the raw data.

        static const size_t first_reg = BMM150_REG_DIG_X1;
        magTask->dig_x1 = magTask->raw_dig_data[BMM150_REG_DIG_X1 - first_reg];
        magTask->dig_y1 = magTask->raw_dig_data[BMM150_REG_DIG_Y1 - first_reg];
        magTask->dig_x2 = magTask->raw_dig_data[BMM150_REG_DIG_X2 - first_reg];
        magTask->dig_y2 = magTask->raw_dig_data[BMM150_REG_DIG_Y2 - first_reg];
        magTask->dig_xy2 = magTask->raw_dig_data[BMM150_REG_DIG_XY2 - first_reg];
        magTask->dig_xy1 = magTask->raw_dig_data[BMM150_REG_DIG_XY1 - first_reg];

        magTask->dig_z1 = *(uint16_t *)(&magTask->raw_dig_data[BMM150_REG_DIG_Z1_LSB - first_reg]);
        magTask->dig_z2 = *(int16_t *)(&magTask->raw_dig_data[BMM150_REG_DIG_Z2_LSB - first_reg]);
        magTask->dig_z3 = *(int16_t *)(&magTask->raw_dig_data[BMM150_REG_DIG_Z3_LSB - first_reg]);
        magTask->dig_z4 = *(int16_t *)(&magTask->raw_dig_data[BMM150_REG_DIG_Z4_LSB - first_reg]);

        magTask->dig_xyz1 = *(uint16_t *)(&magTask->raw_dig_data[BMM150_REG_DIG_XYZ1_LSB - first_reg]);
    }
}

static int32_t bmm150TempCompensateX(struct MagTask *magTask, int16_t mag_x, uint16_t rhall)
{
    int32_t inter_retval = 0;

    // some temp var to made the long calculation easier to read
    int32_t temp_1, temp_2, temp_3, temp_4;

    // no overflow
    if (mag_x != BMM150_MAG_FLIP_OVERFLOW_ADCVAL) {
        if ((rhall != 0) && (magTask->dig_xyz1 != 0)) {

            inter_retval = ((int32_t)(((uint16_t) ((((int32_t)magTask->dig_xyz1) << 14)
                / (rhall != 0 ?  rhall : magTask->dig_xyz1))) - ((uint16_t)0x4000)));

        } else {
            inter_retval = BMM150_MAG_OVERFLOW_OUTPUT;
            return inter_retval;
        }

        temp_1 = ((int32_t)magTask->dig_xy2) * ((((int32_t)inter_retval) * ((int32_t)inter_retval)) >> 7);
        temp_2 = ((int32_t)inter_retval) * ((int32_t)(((int16_t)magTask->dig_xy1) << 7));
        temp_3 = ((temp_1 + temp_2) >> 9) + ((int32_t)BMM150_CALIB_HEX_LACKS);
        temp_4 = ((int32_t)mag_x) * ((temp_3 * ((int32_t)(((int16_t)magTask->dig_x2) + ((int16_t)0xa0)))) >> 12);

        inter_retval = ((int32_t)(temp_4 >> 13)) + (((int16_t)magTask->dig_x1) << 3);

        // check the overflow output
        if (inter_retval == (int32_t)BMM150_MAG_OVERFLOW_OUTPUT)
            inter_retval = BMM150_MAG_OVERFLOW_OUTPUT_S32;
    } else {
        // overflow
        inter_retval = BMM150_MAG_OVERFLOW_OUTPUT;
    }
    return inter_retval;
}

static int32_t bmm150TempCompensateY(struct MagTask *magTask, int16_t mag_y, uint16_t rhall)
{
    int32_t inter_retval = 0;

    // some temp var to made the long calculation easier to read
    int32_t temp_1, temp_2, temp_3, temp_4;

    // no overflow
    if (mag_y != BMM150_MAG_FLIP_OVERFLOW_ADCVAL) {
        if ((rhall != 0) && (magTask->dig_xyz1 != 0)) {

            inter_retval = ((int32_t)(((uint16_t)((( (int32_t)magTask->dig_xyz1) << 14)
                / (rhall != 0 ?  rhall : magTask->dig_xyz1))) - ((uint16_t)0x4000)));

        } else {
            inter_retval = BMM150_MAG_OVERFLOW_OUTPUT;
            return inter_retval;
        }

        temp_1 = ((int32_t)magTask->dig_xy2) * ((((int32_t) inter_retval) * ((int32_t)inter_retval)) >> 7);
        temp_2 = ((int32_t)inter_retval) * ((int32_t)(((int16_t)magTask->dig_xy1) << 7));
        temp_3 = ((temp_1 + temp_2) >> 9) + ((int32_t)BMM150_CALIB_HEX_LACKS);
        temp_4 = ((int32_t)mag_y) * ((temp_3 * ((int32_t)(((int16_t)magTask->dig_y2) + ((int16_t)0xa0)))) >> 12);

        inter_retval = ((int32_t)(temp_4 >> 13)) + (((int16_t)magTask->dig_y1) << 3);

        // check the overflow output
        if (inter_retval == (int32_t)BMM150_MAG_OVERFLOW_OUTPUT)
            inter_retval = BMM150_MAG_OVERFLOW_OUTPUT_S32;
    } else {
        // overflow
        inter_retval = BMM150_MAG_OVERFLOW_OUTPUT;
    }
    return inter_retval;
}

static int32_t bmm150TempCompensateZ(struct MagTask *magTask, int16_t mag_z, uint16_t rhall)
{
    int32_t retval = 0;
    if (mag_z != BMM150_MAG_HALL_OVERFLOW_ADCVAL) {
        if ((rhall != 0) && (magTask->dig_z2 != 0) && (magTask->dig_z1 != 0)) {

            retval = ((((int32_t)(mag_z - magTask->dig_z4)) << 15)
                    - ((((int32_t)magTask->dig_z3) * ((int32_t)(((int16_t)rhall) - ((int16_t)magTask->dig_xyz1)))) >> 2));

            retval /= (magTask->dig_z2
                    + ((int16_t)(((((int32_t)magTask->dig_z1) * ((((int16_t)rhall) << 1))) + (1 << 15)) >> 16)));
        }
    } else {
        retval = BMM150_MAG_OVERFLOW_OUTPUT;
    }
    return retval;
}

void parseMagData(struct MagTask *magTask, uint8_t *buf, float *x, float *y, float *z) {
    int32_t mag_x = (*(int16_t *)&buf[0]) >> 3;
    int32_t mag_y = (*(int16_t *)&buf[2]) >> 3;
    int32_t mag_z = (*(int16_t *)&buf[4]) >> 1;
    uint32_t mag_rhall = (*(uint16_t *)&buf[6]) >> 2;

    int32_t raw_x = bmm150TempCompensateX(magTask, mag_x, mag_rhall);
    int32_t raw_y = bmm150TempCompensateY(magTask, mag_y, mag_rhall);
    int32_t raw_z = bmm150TempCompensateZ(magTask, mag_z, mag_rhall);

    *x = (float)raw_x * kScale_mag;
    *y = (float)raw_y * kScale_mag;
    *z = (float)raw_z * kScale_mag;
}
