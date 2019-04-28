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

#define LOG_TAG "CamDev@3.2-impl"
#include <log/log.h>

#include <utils/Vector.h>
#include <utils/Trace.h>
#include "CameraDevice_3_2.h"
#include <include/convert.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_2 {
namespace implementation {

using ::android::hardware::camera::common::V1_0::Status;

CameraDevice::CameraDevice(
    sp<CameraModule> module, const std::string& cameraId,
    const SortedVector<std::pair<std::string, std::string>>& cameraDeviceNames) :
        mModule(module),
        mCameraId(cameraId),
        mDisconnected(false),
        mCameraDeviceNames(cameraDeviceNames) {
    mCameraIdInt = atoi(mCameraId.c_str());
    // Should not reach here as provider also validate ID
    if (mCameraIdInt < 0 || mCameraIdInt >= module->getNumberOfCameras()) {
        ALOGE("%s: Invalid camera id: %s", __FUNCTION__, mCameraId.c_str());
        mInitFail = true;
    }

    mDeviceVersion = mModule->getDeviceVersion(mCameraIdInt);
    if (mDeviceVersion < CAMERA_DEVICE_API_VERSION_3_2) {
        ALOGE("%s: Camera id %s does not support HAL3.2+",
                __FUNCTION__, mCameraId.c_str());
        mInitFail = true;
    }
}

CameraDevice::~CameraDevice() {}

Status CameraDevice::initStatus() const {
    Mutex::Autolock _l(mLock);
    Status status = Status::OK;
    if (mInitFail) {
        status = Status::INTERNAL_ERROR;
    } else if (mDisconnected) {
        status = Status::CAMERA_DISCONNECTED;
    }
    return status;
}

void CameraDevice::setConnectionStatus(bool connected) {
    Mutex::Autolock _l(mLock);
    mDisconnected = !connected;
    if (mSession == nullptr) {
        return;
    }
    sp<CameraDeviceSession> session = mSession.promote();
    if (session == nullptr) {
        return;
    }
    // Only notify active session disconnect events.
    // Users will need to re-open camera after disconnect event
    if (!connected) {
        session->disconnect();
    }
    return;
}

Status CameraDevice::getHidlStatus(int status) {
    switch (status) {
        case 0: return Status::OK;
        case -ENOSYS: return Status::OPERATION_NOT_SUPPORTED;
        case -EBUSY : return Status::CAMERA_IN_USE;
        case -EUSERS: return Status::MAX_CAMERAS_IN_USE;
        case -ENODEV: return Status::INTERNAL_ERROR;
        case -EINVAL: return Status::ILLEGAL_ARGUMENT;
        default:
            ALOGE("%s: unknown HAL status code %d", __FUNCTION__, status);
            return Status::INTERNAL_ERROR;
    }
}

// Methods from ::android::hardware::camera::device::V3_2::ICameraDevice follow.
Return<void> CameraDevice::getResourceCost(getResourceCost_cb _hidl_cb)  {
    Status status = initStatus();
    CameraResourceCost resCost;
    if (status == Status::OK) {
        int cost = 100;
        std::vector<std::string> conflicting_devices;
        struct camera_info info;

        // If using post-2.4 module version, query the cost + conflicting devices from the HAL
        if (mModule->getModuleApiVersion() >= CAMERA_MODULE_API_VERSION_2_4) {
            int ret = mModule->getCameraInfo(mCameraIdInt, &info);
            if (ret == OK) {
                cost = info.resource_cost;
                for (size_t i = 0; i < info.conflicting_devices_length; i++) {
                    std::string cameraId(info.conflicting_devices[i]);
                    for (const auto& pair : mCameraDeviceNames) {
                        if (cameraId == pair.first) {
                            conflicting_devices.push_back(pair.second);
                        }
                    }
                }
            } else {
                status = Status::INTERNAL_ERROR;
            }
        }

        if (status == Status::OK) {
            resCost.resourceCost = cost;
            resCost.conflictingDevices.resize(conflicting_devices.size());
            for (size_t i = 0; i < conflicting_devices.size(); i++) {
                resCost.conflictingDevices[i] = conflicting_devices[i];
                ALOGV("CamDevice %s is conflicting with camDevice %s",
                        mCameraId.c_str(), resCost.conflictingDevices[i].c_str());
            }
        }
    }
    _hidl_cb(status, resCost);
    return Void();
}

Return<void> CameraDevice::getCameraCharacteristics(getCameraCharacteristics_cb _hidl_cb)  {
    Status status = initStatus();
    CameraMetadata cameraCharacteristics;
    if (status == Status::OK) {
        //Module 2.1+ codepath.
        struct camera_info info;
        int ret = mModule->getCameraInfo(mCameraIdInt, &info);
        if (ret == OK) {
            convertToHidl(info.static_camera_characteristics, &cameraCharacteristics);
        } else {
            ALOGE("%s: get camera info failed!", __FUNCTION__);
            status = Status::INTERNAL_ERROR;
        }
    }
    _hidl_cb(status, cameraCharacteristics);
    return Void();
}

Return<Status> CameraDevice::setTorchMode(TorchMode mode)  {
    if (!mModule->isSetTorchModeSupported()) {
        return Status::METHOD_NOT_SUPPORTED;
    }

    Status status = initStatus();
    if (status == Status::OK) {
        bool enable = (mode == TorchMode::ON) ? true : false;
        status = getHidlStatus(mModule->setTorchMode(mCameraId.c_str(), enable));
    }
    return status;
}

Return<void> CameraDevice::open(const sp<ICameraDeviceCallback>& callback, open_cb _hidl_cb)  {
    Status status = initStatus();
    sp<CameraDeviceSession> session = nullptr;

    if (callback == nullptr) {
        ALOGE("%s: cannot open camera %s. callback is null!",
                __FUNCTION__, mCameraId.c_str());
        _hidl_cb(Status::ILLEGAL_ARGUMENT, nullptr);
        return Void();
    }

    if (status != Status::OK) {
        // Provider will never pass initFailed device to client, so
        // this must be a disconnected camera
        ALOGE("%s: cannot open camera %s. camera is disconnected!",
                __FUNCTION__, mCameraId.c_str());
        _hidl_cb(Status::CAMERA_DISCONNECTED, nullptr);
        return Void();
    } else {
        mLock.lock();

        ALOGV("%s: Initializing device for camera %d", __FUNCTION__, mCameraIdInt);
        session = mSession.promote();
        if (session != nullptr && !session->isClosed()) {
            ALOGE("%s: cannot open an already opened camera!", __FUNCTION__);
            mLock.unlock();
            _hidl_cb(Status::CAMERA_IN_USE, nullptr);
            return Void();
        }

        /** Open HAL device */
        status_t res;
        camera3_device_t *device;

        ATRACE_BEGIN("camera3->open");
        res = mModule->open(mCameraId.c_str(),
                reinterpret_cast<hw_device_t**>(&device));
        ATRACE_END();

        if (res != OK) {
            ALOGE("%s: cannot open camera %s!", __FUNCTION__, mCameraId.c_str());
            mLock.unlock();
            _hidl_cb(getHidlStatus(res), nullptr);
            return Void();
        }

        /** Cross-check device version */
        if (device->common.version < CAMERA_DEVICE_API_VERSION_3_2) {
            ALOGE("%s: Could not open camera: "
                    "Camera device should be at least %x, reports %x instead",
                    __FUNCTION__,
                    CAMERA_DEVICE_API_VERSION_3_2,
                    device->common.version);
            device->common.close(&device->common);
            mLock.unlock();
            _hidl_cb(Status::ILLEGAL_ARGUMENT, nullptr);
            return Void();
        }

        struct camera_info info;
        res = mModule->getCameraInfo(mCameraIdInt, &info);
        if (res != OK) {
            ALOGE("%s: Could not open camera: getCameraInfo failed", __FUNCTION__);
            device->common.close(&device->common);
            mLock.unlock();
            _hidl_cb(Status::ILLEGAL_ARGUMENT, nullptr);
            return Void();
        }

        session = createSession(
                device, info.static_camera_characteristics, callback);
        if (session == nullptr) {
            ALOGE("%s: camera device session allocation failed", __FUNCTION__);
            mLock.unlock();
            _hidl_cb(Status::INTERNAL_ERROR, nullptr);
            return Void();
        }
        if (session->isInitFailed()) {
            ALOGE("%s: camera device session init failed", __FUNCTION__);
            session = nullptr;
            mLock.unlock();
            _hidl_cb(Status::INTERNAL_ERROR, nullptr);
            return Void();
        }
        mSession = session;

        IF_ALOGV() {
            session->getInterface()->interfaceChain([](
                ::android::hardware::hidl_vec<::android::hardware::hidl_string> interfaceChain) {
                    ALOGV("Session interface chain:");
                    for (auto iface : interfaceChain) {
                        ALOGV("  %s", iface.c_str());
                    }
                });
        }
        mLock.unlock();
    }
    _hidl_cb(status, session->getInterface());
    return Void();
}

Return<void> CameraDevice::dumpState(const ::android::hardware::hidl_handle& handle)  {
    Mutex::Autolock _l(mLock);
    if (handle.getNativeHandle() == nullptr) {
        ALOGE("%s: handle must not be null", __FUNCTION__);
        return Void();
    }
    if (handle->numFds != 1 || handle->numInts != 0) {
        ALOGE("%s: handle must contain 1 FD and 0 integers! Got %d FDs and %d ints",
                __FUNCTION__, handle->numFds, handle->numInts);
        return Void();
    }
    int fd = handle->data[0];
    if (mSession == nullptr) {
        dprintf(fd, "No active camera device session instance\n");
        return Void();
    }
    sp<CameraDeviceSession> session = mSession.promote();
    if (session == nullptr) {
        dprintf(fd, "No active camera device session instance\n");
        return Void();
    }
    // Call into active session to dump states
    session->dumpState(handle);
    return Void();
}

sp<CameraDeviceSession> CameraDevice::createSession(camera3_device_t* device,
        const camera_metadata_t* deviceInfo,
        const sp<ICameraDeviceCallback>& callback) {
    return new CameraDeviceSession(device, deviceInfo, callback);
}

// End of methods from ::android::hardware::camera::device::V3_2::ICameraDevice.

} // namespace implementation
}  // namespace V3_2
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
