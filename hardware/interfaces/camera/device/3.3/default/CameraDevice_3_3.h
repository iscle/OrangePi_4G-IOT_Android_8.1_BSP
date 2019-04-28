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

#ifndef ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_CAMERADEVICE_H
#define ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_CAMERADEVICE_H

#include "utils/Mutex.h"
#include "CameraModule.h"
#include "CameraMetadata.h"
#include "CameraDeviceSession.h"
#include <../../3.2/default/CameraDevice_3_2.h>

#include <android/hardware/camera/device/3.2/ICameraDevice.h>
#include <hidl/Status.h>
#include <hidl/MQDescriptor.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_3 {
namespace implementation {

using namespace ::android::hardware::camera::device;
using ::android::hardware::camera::common::V1_0::helper::CameraModule;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

/*
 * The camera device HAL implementation is opened lazily (via the open call)
 */
struct CameraDevice : public V3_2::implementation::CameraDevice {

    // Called by provider HAL.
    // Provider HAL must ensure the uniqueness of CameraDevice object per cameraId, or there could
    // be multiple CameraDevice trying to access the same physical camera.  Also, provider will have
    // to keep track of all CameraDevice objects in order to notify CameraDevice when the underlying
    // camera is detached.
    // Delegates nearly all work to CameraDevice_3_2
    CameraDevice(sp<CameraModule> module,
                 const std::string& cameraId,
                 const SortedVector<std::pair<std::string, std::string>>& cameraDeviceNames);
    ~CameraDevice();

protected:
    virtual sp<V3_2::implementation::CameraDeviceSession> createSession(camera3_device_t*,
            const camera_metadata_t* deviceInfo,
            const sp<V3_2::ICameraDeviceCallback>&) override;

};

}  // namespace implementation
}  // namespace V3_3
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_CAMERADEVICE_H
