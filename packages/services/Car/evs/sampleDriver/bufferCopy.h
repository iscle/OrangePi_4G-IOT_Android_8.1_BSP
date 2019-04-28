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

#ifndef ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BUFFERCOPY_H
#define ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BUFFERCOPY_H

#include <android/hardware/automotive/evs/1.0/types.h>


namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


void fillNV21FromNV21(const BufferDesc& tgtBuff, uint8_t* tgt,
                      void* imgData, unsigned imgStride);

void fillNV21FromYUYV(const BufferDesc& tgtBuff, uint8_t* tgt,
                      void* imgData, unsigned imgStride);

void fillRGBAFromYUYV(const BufferDesc& tgtBuff, uint8_t* tgt,
                      void* imgData, unsigned imgStride);

void fillYUYVFromYUYV(const BufferDesc& tgtBuff, uint8_t* tgt,
                      void* imgData, unsigned imgStride);

void fillYUYVFromUYVY(const BufferDesc& tgtBuff, uint8_t* tgt,
                      void* imgData, unsigned imgStride);

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace hardware
} // namespace android

#endif  // ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BUFFERCOPY_H
