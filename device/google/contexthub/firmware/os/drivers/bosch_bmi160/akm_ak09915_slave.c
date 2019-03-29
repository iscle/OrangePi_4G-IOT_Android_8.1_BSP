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
#include "akm_ak09915_slave.h"

void parseMagData(struct MagTask *magTask, uint8_t *buf, float *x, float *y, float *z) {
    int32_t raw_x = (*(int16_t *)&buf[0]);
    int32_t raw_y = (*(int16_t *)&buf[2]);
    int32_t raw_z = (*(int16_t *)&buf[4]);

    *x = (float)raw_x * kScale_mag;
    *y = (float)raw_y * kScale_mag;
    *z = (float)raw_z * kScale_mag;
}
