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
#ifndef ANDROID_HARDWARE_VR_V1_0_VR_H
#define ANDROID_HARDWARE_VR_V1_0_VR_H

#include <android/hardware/vr/1.0/IVr.h>
#include <hardware/vr.h>
#include <hidl/MQDescriptor.h>

namespace android {
namespace hardware {
namespace vr {
namespace V1_0 {
namespace implementation {

using ::android::hardware::vr::V1_0::IVr;
using ::android::hardware::Return;

struct Vr : public IVr {
    Vr(vr_module_t *device);

    // Methods from ::android::hardware::vr::V1_0::IVr follow.
    Return<void> init()  override;
    Return<void> setVrMode(bool enabled)  override;

  private:
    vr_module_t    *mDevice;
};

extern "C" IVr* HIDL_FETCH_IVr(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace vr
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_VR_V1_0_VR_H
