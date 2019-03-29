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
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_QUAT_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_QUAT_H_

#include "common/math/mat.h"
#include "common/math/vec.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct Vec4 Quat;

void initQuat(Quat *q, const struct Mat33 *R);
void quatToMatrix(struct Mat33 *R, const Quat *q);
void quatNormalize(Quat *q);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_QUAT_H_
