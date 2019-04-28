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

#ifndef ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_CAMERADEVICE3SESSION_H
#define ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_CAMERADEVICE3SESSION_H

#include <android/hardware/camera/device/3.2/ICameraDevice.h>
#include <android/hardware/camera/device/3.3/ICameraDeviceSession.h>
#include <../../3.2/default/CameraDeviceSession.h>
#include <fmq/MessageQueue.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <include/convert.h>
#include <deque>
#include <map>
#include <unordered_map>
#include "CameraMetadata.h"
#include "HandleImporter.h"
#include "hardware/camera3.h"
#include "hardware/camera_common.h"
#include "utils/Mutex.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_3 {
namespace implementation {

using namespace ::android::hardware::camera::device;
using ::android::hardware::camera::device::V3_2::CaptureRequest;
using ::android::hardware::camera::device::V3_2::StreamConfiguration;
using ::android::hardware::camera::device::V3_3::HalStreamConfiguration;
using ::android::hardware::camera::device::V3_3::ICameraDeviceSession;
using ::android::hardware::camera::common::V1_0::Status;
using ::android::hardware::camera::common::V1_0::helper::HandleImporter;
using ::android::hardware::kSynchronizedReadWrite;
using ::android::hardware::MessageQueue;
using ::android::hardware::MQDescriptorSync;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;
using ::android::Mutex;

struct CameraDeviceSession : public V3_2::implementation::CameraDeviceSession {

    CameraDeviceSession(camera3_device_t*,
            const camera_metadata_t* deviceInfo,
            const sp<V3_2::ICameraDeviceCallback>&);
    virtual ~CameraDeviceSession();

    virtual sp<V3_2::ICameraDeviceSession> getInterface() override {
        return new TrampolineSessionInterface_3_3(this);
    }

protected:
    // Methods from v3.2 and earlier will trampoline to inherited implementation

    // New methods for v3.3

    Return<void> configureStreams_3_3(
            const StreamConfiguration& requestedConfiguration,
            ICameraDeviceSession::configureStreams_3_3_cb _hidl_cb);
private:

    struct TrampolineSessionInterface_3_3 : public ICameraDeviceSession {
        TrampolineSessionInterface_3_3(sp<CameraDeviceSession> parent) :
                mParent(parent) {}

        virtual Return<void> constructDefaultRequestSettings(
                V3_2::RequestTemplate type,
                V3_3::ICameraDeviceSession::constructDefaultRequestSettings_cb _hidl_cb) override {
            return mParent->constructDefaultRequestSettings(type, _hidl_cb);
        }

        virtual Return<void> configureStreams(
                const V3_2::StreamConfiguration& requestedConfiguration,
                V3_3::ICameraDeviceSession::configureStreams_cb _hidl_cb) override {
            return mParent->configureStreams(requestedConfiguration, _hidl_cb);
        }

        virtual Return<void> processCaptureRequest(const hidl_vec<V3_2::CaptureRequest>& requests,
                const hidl_vec<V3_2::BufferCache>& cachesToRemove,
                V3_3::ICameraDeviceSession::processCaptureRequest_cb _hidl_cb) override {
            return mParent->processCaptureRequest(requests, cachesToRemove, _hidl_cb);
        }

        virtual Return<void> getCaptureRequestMetadataQueue(
                V3_3::ICameraDeviceSession::getCaptureRequestMetadataQueue_cb _hidl_cb) override  {
            return mParent->getCaptureRequestMetadataQueue(_hidl_cb);
        }

        virtual Return<void> getCaptureResultMetadataQueue(
                V3_3::ICameraDeviceSession::getCaptureResultMetadataQueue_cb _hidl_cb) override  {
            return mParent->getCaptureResultMetadataQueue(_hidl_cb);
        }

        virtual Return<Status> flush() override {
            return mParent->flush();
        }

        virtual Return<void> close() override {
            return mParent->close();
        }

        virtual Return<void> configureStreams_3_3(
                const StreamConfiguration& requestedConfiguration, configureStreams_3_3_cb _hidl_cb) override {
            return mParent->configureStreams_3_3(requestedConfiguration, _hidl_cb);
        }

    private:
        sp<CameraDeviceSession> mParent;
    };
};

}  // namespace implementation
}  // namespace V3_3
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_CAMERADEVICE3SESSION_H
