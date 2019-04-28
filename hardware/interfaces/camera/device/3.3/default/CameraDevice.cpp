/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "CamDev@3.3-impl"
#include <log/log.h>

#include <utils/Vector.h>
#include <utils/Trace.h>
#include "CameraDevice_3_3.h"
#include <include/convert.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_3 {
namespace implementation {

using ::android::hardware::camera::common::V1_0::Status;
using namespace ::android::hardware::camera::device;

CameraDevice::CameraDevice(
    sp<CameraModule> module, const std::string& cameraId,
    const SortedVector<std::pair<std::string, std::string>>& cameraDeviceNames) :
        V3_2::implementation::CameraDevice(module, cameraId, cameraDeviceNames) {
}

CameraDevice::~CameraDevice() {
}

sp<V3_2::implementation::CameraDeviceSession> CameraDevice::createSession(camera3_device_t* device,
        const camera_metadata_t* deviceInfo,
        const sp<V3_2::ICameraDeviceCallback>& callback) {
    sp<CameraDeviceSession> session = new CameraDeviceSession(device, deviceInfo, callback);
    IF_ALOGV() {
        session->getInterface()->interfaceChain([](
            ::android::hardware::hidl_vec<::android::hardware::hidl_string> interfaceChain) {
                ALOGV("Session interface chain:");
                for (auto iface : interfaceChain) {
                    ALOGV("  %s", iface.c_str());
                }
            });
    }
    return session;
}

// End of methods from ::android::hardware::camera::device::V3_2::ICameraDevice.

} // namespace implementation
}  // namespace V3_3
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
