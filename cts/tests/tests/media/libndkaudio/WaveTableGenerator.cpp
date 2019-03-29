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

#include <math.h>

#include <android/log.h>

#include "WaveTableGenerator.h"

static const float kPI = 3.14159265359;	// close enough

//static const char* const TAG = "WaveTableGenerator";

namespace ndkaudio {

float* WaveTableGenerator::genSinWave(int size, float maxValue) {
    return genSinWave(size, maxValue, new float[size]);
}

float* WaveTableGenerator::genSinWave(int size, float maxValue, float* tbl) {
    float incr = (kPI * 2.0f) / (float) size;
    float val = 0.0f;
    for (int index = 0; index < size; index++) {
        tbl[index] = (float) sin(val) * maxValue;
        val += incr;
    }

    return tbl;
}

} // namespace ndkaudio
