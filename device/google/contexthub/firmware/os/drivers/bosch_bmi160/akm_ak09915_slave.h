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

#ifndef AKM_AK09915_H_

#define AKM_AK09915_H_

#include <stdio.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define kScale_mag 0.15f

#define AKM_AK09915_DEVICE_ID     0x1048
#define AKM_AK09915_REG_WIA1      0x00
#define AKM_AK09915_REG_DATA      0x11
#define AKM_AK09915_REG_CNTL1     0x30
#define AKM_AK09915_REG_CNTL2     0x31

struct MagTask {
    int32_t dummy;
};

#define MAG_I2C_ADDR 0x0C
#define MAG_REG_DATA AKM_AK09915_REG_DATA

void parseMagData(struct MagTask *magTask, uint8_t *buf, float *x, float *y, float *z);

#ifdef __cplusplus
}
#endif

#endif  // AKM_AK09915_H_
