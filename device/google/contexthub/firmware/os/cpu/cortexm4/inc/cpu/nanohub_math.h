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

#ifndef _CPU_NANOHUB_MATH_H_
#define _CPU_NANOHUB_MATH_H_

#include <cpu/cmsis/arm_math.h>

#define asinf  arm_asinf
#define sinf   arm_sin_f32
#define cosf   arm_cos_f32
#define sqrtf  arm_sqrtf



static inline float arm_sqrtf(float val)
{
    float ret;

    asm(
        "vsqrt.f32 %0, %1"
        : "=w" (ret)
        :"w" (val)
    );

    return ret;
}

float atan2f(float, float);

static inline float arm_asinf(float x)
{
    return atan2f(x, sqrtf(1.0f - x * x));
}

#endif
