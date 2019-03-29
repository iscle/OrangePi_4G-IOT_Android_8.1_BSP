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

#ifndef _FLOAT_RT_H_
#define _FLOAT_RT_H_

#include <stdint.h>

#ifdef USE_NANOHUB_FLOAT_RUNTIME

// Under this define, we want to use these custom methods rather than trust
// our built-in float runtime.

uint64_t floatToUint64(float f);
int64_t floatToInt64(float f);
float floatFromUint64(uint64_t v);
float floatFromInt64(int64_t v);


#else // USE_NANOHUB_FLOAT_RUNTIME

static inline uint64_t floatToUint64(float f)
{
    return f;
}

static inline int64_t floatToInt64(float f)
{
    return f;
}

static inline float floatFromUint64(uint64_t v)
{
    return v;
}

static inline float floatFromInt64(int64_t v)
{
    return v;
}

#endif // USE_NANOHUB_FLOAT_RUNTIME

#endif


