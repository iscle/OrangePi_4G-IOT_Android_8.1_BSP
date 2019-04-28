/*
 * Copyright (C) 2008 The Android Open Source Project
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

#define LOG_TAG "CameraService"
#define ATRACE_TAG ATRACE_TAG_CAMERA
//#define LOG_NDEBUG 0

#include <algorithm>
#include <climits>
#include <stdio.h>
#include <cstring>
#include <ctime>
#include <string>
#include <sys/types.h>
#include <inttypes.h>
#include <pthread.h>

#include <android/hardware/ICamera.h>
#include <android/hardware/ICameraClient.h>

#include <android-base/macros.h>
#include <android-base/parseint.h>
#include <binder/AppOpsManager.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <binder/ProcessInfoService.h>
#include <cutils/atomic.h>
#include <cutils/properties.h>
#include <gui/Surface.h>
#include <hardware/hardware.h>
#include <memunreachable/memunreachable.h>
#include <media/AudioSystem.h>
#include <media/IMediaHTTPService.h>
#include <media/mediaplayer.h>
#include <mediautils/BatteryNotifier.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/String16.h>
#include <utils/Trace.h>
#include <private/android_filesystem_config.h>
#include <system/camera_vendor_tags.h>
#include <system/camera_metadata.h>

#include <system/camera.h>

#include "CameraService.h"
#include "api1/CameraClient.h"
#include "api1/Camera2Client.h"
#include "api2/CameraDeviceClient.h"
#include "utils/CameraTraces.h"

namespace {
    const char* kPermissionServiceName = "permission";
}; // namespace anonymous

namespace android {

using binder::Status;
using hardware::ICamera;
using hardware::ICameraClient;
using hardware::ICameraServiceProxy;
using hardware::ICameraServiceListener;
using hardware::camera::common::V1_0::CameraDeviceStatus;
using hardware::camera::common::V1_0::TorchModeStatus;

// ----------------------------------------------------------------------------
// Logging support -- this is for debugging only
// Use "adb shell dumpsys media.camera -v 1" to change it.
//!++ For MTK debug use
#ifdef MTKCAM_TARGET_BUILD_USER
volatile int32_t gLogLevel = 0;
#else
volatile int32_t gLogLevel = 2;
#endif
//!--

#define LOG1(...) ALOGD_IF(gLogLevel >= 1, __VA_ARGS__);
#define LOG2(...) ALOGD_IF(gLogLevel >= 2, __VA_ARGS__);

static void setLogLevel(int level) {
    android_atomic_write(level, &gLogLevel);
}

// Convenience methods for constructing binder::Status objects for error returns

#define STATUS_ERROR(errorCode, errorString) \
    binder::Status::fromServiceSpecificError(errorCode, \
            String8::format("%s:%d: %s", __FUNCTION__, __LINE__, errorString))

#define STATUS_ERROR_FMT(errorCode, errorString, ...) \
    binder::Status::fromServiceSpecificError(errorCode, \
            String8::format("%s:%d: " errorString, __FUNCTION__, __LINE__, \
                    __VA_ARGS__))

// ----------------------------------------------------------------------------

extern "C" {
static void camera_device_status_change(
        const struct camera_module_callbacks* callbacks,
        int camera_id,
        int new_status) {
    sp<CameraService> cs = const_cast<CameraService*>(
            static_cast<const CameraService*>(callbacks));
    String8 id = String8::format("%d", camera_id);

    CameraDeviceStatus newStatus{CameraDeviceStatus::NOT_PRESENT};
    switch (new_status) {
        case CAMERA_DEVICE_STATUS_NOT_PRESENT:
            newStatus = CameraDeviceStatus::NOT_PRESENT;
            break;
        case CAMERA_DEVICE_STATUS_PRESENT:
            newStatus = CameraDeviceStatus::PRESENT;
            break;
        case CAMERA_DEVICE_STATUS_ENUMERATING:
            newStatus = CameraDeviceStatus::ENUMERATING;
            break;
        default:
            ALOGW("Unknown device status change to %d", new_status);
            break;
    }
    cs->onDeviceStatusChanged(id, newStatus);
}

static void torch_mode_status_change(
        const struct camera_module_callbacks* callbacks,
        const char* camera_id,
        int new_status) {
    if (!callbacks || !camera_id) {
        ALOGE("%s invalid parameters. callbacks %p, camera_id %p", __FUNCTION__,
                callbacks, camera_id);
    }
    sp<CameraService> cs = const_cast<CameraService*>(
                                static_cast<const CameraService*>(callbacks));

    TorchModeStatus status;
    switch (new_status) {
        case TORCH_MODE_STATUS_NOT_AVAILABLE:
            status = TorchModeStatus::NOT_AVAILABLE;
            break;
        case TORCH_MODE_STATUS_AVAILABLE_OFF:
            status = TorchModeStatus::AVAILABLE_OFF;
            break;
        case TORCH_MODE_STATUS_AVAILABLE_ON:
            status = TorchModeStatus::AVAILABLE_ON;
            break;
        default:
            ALOGE("Unknown torch status %d", new_status);
            return;
    }

    cs->onTorchStatusChanged(
        String8(camera_id),
        status);
}
} // extern "C"

// ----------------------------------------------------------------------------

CameraService::CameraService() :
        mEventLog(DEFAULT_EVENT_LOG_LENGTH),
        mNumberOfCameras(0), mNumberOfNormalCameras(0),
        mSoundRef(0), mInitialized(false) {
    ALOGI("CameraService started (pid=%d)", getpid());

    this->camera_device_status_change = android::camera_device_status_change;
    this->torch_mode_status_change = android::torch_mode_status_change;

    mServiceLockWrapper = std::make_shared<WaitableMutexWrapper>(&mServiceLock);
}

void CameraService::onFirstRef()
{
    ALOGI("CameraService process starting");

    BnCameraService::onFirstRef();

    // Update battery life tracking if service is restarting
    BatteryNotifier& notifier(BatteryNotifier::getInstance());
    notifier.noteResetCamera();
    notifier.noteResetFlashlight();

    status_t res = INVALID_OPERATION;

    res = enumerateProviders();
    if (res == OK) {
        mInitialized = true;
    }

    CameraService::pingCameraServiceProxy();
}

status_t CameraService::enumerateProviders() {
    status_t res;
    Mutex::Autolock l(mServiceLock);

    if (nullptr == mCameraProviderManager.get()) {
        mCameraProviderManager = new CameraProviderManager();
        res = mCameraProviderManager->initialize(this);
        if (res != OK) {
            ALOGE("%s: Unable to initialize camera provider manager: %s (%d)",
                    __FUNCTION__, strerror(-res), res);
            return res;
        }
    }

    mNumberOfCameras = mCameraProviderManager->getCameraCount();
    mNumberOfNormalCameras =
            mCameraProviderManager->getAPI1CompatibleCameraCount();

    // Setup vendor tags before we call get_camera_info the first time
    // because HAL might need to setup static vendor keys in get_camera_info
    // TODO: maybe put this into CameraProviderManager::initialize()?
    mCameraProviderManager->setUpVendorTags();

    if (nullptr == mFlashlight.get()) {
        mFlashlight = new CameraFlashlight(mCameraProviderManager, this);
    }

    res = mFlashlight->findFlashUnits();
    if (res != OK) {
        ALOGE("Failed to enumerate flash units: %s (%d)", strerror(-res), res);
    }

    for (auto& cameraId : mCameraProviderManager->getCameraDeviceIds()) {
        String8 id8 = String8(cameraId.c_str());
        bool cameraFound = false;
        {

            Mutex::Autolock lock(mCameraStatesLock);
            auto iter = mCameraStates.find(id8);
            if (iter != mCameraStates.end()) {
                cameraFound = true;
            }
        }

        if (!cameraFound) {
            hardware::camera::common::V1_0::CameraResourceCost cost;
            res = mCameraProviderManager->getResourceCost(cameraId, &cost);
            if (res != OK) {
                ALOGE("Failed to query device resource cost: %s (%d)", strerror(-res), res);
                continue;
            }
            std::set<String8> conflicting;
            for (size_t i = 0; i < cost.conflictingDevices.size(); i++) {
                conflicting.emplace(String8(cost.conflictingDevices[i].c_str()));
            }

            {
                Mutex::Autolock lock(mCameraStatesLock);
                mCameraStates.emplace(id8,
                    std::make_shared<CameraState>(id8, cost.resourceCost, conflicting));
            }
        }

        onDeviceStatusChanged(id8, CameraDeviceStatus::PRESENT);

        if (mFlashlight->hasFlashUnit(id8)) {
            mTorchStatusMap.add(id8, TorchModeStatus::AVAILABLE_OFF);
        }
    }

    return OK;
}

sp<ICameraServiceProxy> CameraService::getCameraServiceProxy() {
    sp<ICameraServiceProxy> proxyBinder = nullptr;
#ifndef __BRILLO__
    sp<IServiceManager> sm = defaultServiceManager();
    // Use checkService because cameraserver normally starts before the
    // system server and the proxy service. So the long timeout that getService
    // has before giving up is inappropriate.
    sp<IBinder> binder = sm->checkService(String16("media.camera.proxy"));
    if (binder != nullptr) {
        proxyBinder = interface_cast<ICameraServiceProxy>(binder);
    }
#endif
    return proxyBinder;
}

void CameraService::pingCameraServiceProxy() {
    sp<ICameraServiceProxy> proxyBinder = getCameraServiceProxy();
    if (proxyBinder == nullptr) return;
    proxyBinder->pingForUserUpdate();
}

CameraService::~CameraService() {
    VendorTagDescriptor::clearGlobalVendorTagDescriptor();
}

void CameraService::onNewProviderRegistered() {
    enumerateProviders();
}

void CameraService::onDeviceStatusChanged(const String8& id,
        CameraDeviceStatus newHalStatus) {
    ALOGI("%s: Status changed for cameraId=%s, newStatus=%d", __FUNCTION__,
            id.string(), newHalStatus);

    StatusInternal newStatus = mapToInternal(newHalStatus);

    std::shared_ptr<CameraState> state = getCameraState(id);

    if (state == nullptr) {
        if (newStatus == StatusInternal::PRESENT) {
            ALOGW("%s: Unknown camera ID %s, probably newly registered?",
                    __FUNCTION__, id.string());
        } else {
            ALOGE("%s: Bad camera ID %s", __FUNCTION__, id.string());
        }
        return;
    }

    StatusInternal oldStatus = state->getStatus();

    if (oldStatus == newStatus) {
        ALOGE("%s: State transition to the same status %#x not allowed", __FUNCTION__, newStatus);
        return;
    }

    if (newStatus == StatusInternal::NOT_PRESENT) {
        logDeviceRemoved(id, String8::format("Device status changed from %d to %d", oldStatus,
                newStatus));
        sp<BasicClient> clientToDisconnect;
        {
            // Don't do this in updateStatus to avoid deadlock over mServiceLock
            Mutex::Autolock lock(mServiceLock);

            // Set the device status to NOT_PRESENT, clients will no longer be able to connect
            // to this device until the status changes
            updateStatus(StatusInternal::NOT_PRESENT, id);

            // Remove cached shim parameters
            state->setShimParams(CameraParameters());

            // Remove the client from the list of active clients, if there is one
            clientToDisconnect = removeClientLocked(id);
        }

        // Disconnect client
        if (clientToDisconnect.get() != nullptr) {
            ALOGI("%s: Client for camera ID %s evicted due to device status change from HAL",
                    __FUNCTION__, id.string());
            // Notify the client of disconnection
            clientToDisconnect->notifyError(
                    hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_DISCONNECTED,
                    CaptureResultExtras{});
            // Ensure not in binder RPC so client disconnect PID checks work correctly
            LOG_ALWAYS_FATAL_IF(getCallingPid() != getpid(),
                    "onDeviceStatusChanged must be called from the camera service process!");
            clientToDisconnect->disconnect();
        }

    } else {
        if (oldStatus == StatusInternal::NOT_PRESENT) {
            logDeviceAdded(id, String8::format("Device status changed from %d to %d", oldStatus,
                    newStatus));
        }
        updateStatus(newStatus, id);
    }

}

void CameraService::onTorchStatusChanged(const String8& cameraId,
        TorchModeStatus newStatus) {
    Mutex::Autolock al(mTorchStatusMutex);
    onTorchStatusChangedLocked(cameraId, newStatus);
}

void CameraService::onTorchStatusChangedLocked(const String8& cameraId,
        TorchModeStatus newStatus) {
    ALOGI("%s: Torch status changed for cameraId=%s, newStatus=%d",
            __FUNCTION__, cameraId.string(), newStatus);

    TorchModeStatus status;
    status_t res = getTorchStatusLocked(cameraId, &status);
    if (res) {
        ALOGE("%s: cannot get torch status of camera %s: %s (%d)",
                __FUNCTION__, cameraId.string(), strerror(-res), res);
        return;
    }
    if (status == newStatus) {
        return;
    }

    res = setTorchStatusLocked(cameraId, newStatus);
    if (res) {
        ALOGE("%s: Failed to set the torch status to %d: %s (%d)", __FUNCTION__,
                (uint32_t)newStatus, strerror(-res), res);
        return;
    }

    {
        // Update battery life logging for flashlight
        Mutex::Autolock al(mTorchUidMapMutex);
        auto iter = mTorchUidMap.find(cameraId);
        if (iter != mTorchUidMap.end()) {
            int oldUid = iter->second.second;
            int newUid = iter->second.first;
            BatteryNotifier& notifier(BatteryNotifier::getInstance());
            if (oldUid != newUid) {
                // If the UID has changed, log the status and update current UID in mTorchUidMap
                if (status == TorchModeStatus::AVAILABLE_ON) {
                    notifier.noteFlashlightOff(cameraId, oldUid);
                }
                if (newStatus == TorchModeStatus::AVAILABLE_ON) {
                    notifier.noteFlashlightOn(cameraId, newUid);
                }
                iter->second.second = newUid;
            } else {
                // If the UID has not changed, log the status
                if (newStatus == TorchModeStatus::AVAILABLE_ON) {
                    notifier.noteFlashlightOn(cameraId, oldUid);
                } else {
                    notifier.noteFlashlightOff(cameraId, oldUid);
                }
            }
        }
    }

    {
        Mutex::Autolock lock(mStatusListenerLock);
        for (auto& i : mListenerList) {
            i->onTorchStatusChanged(mapToInterface(newStatus), String16{cameraId});
        }
    }
}

Status CameraService::getNumberOfCameras(int32_t type, int32_t* numCameras) {
    ATRACE_CALL();
    Mutex::Autolock l(mServiceLock);
    switch (type) {
        case CAMERA_TYPE_BACKWARD_COMPATIBLE:
            *numCameras = mNumberOfNormalCameras;
            break;
        case CAMERA_TYPE_ALL:
            *numCameras = mNumberOfCameras;
            break;
        default:
            ALOGW("%s: Unknown camera type %d",
                    __FUNCTION__, type);
            return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                    "Unknown camera type %d", type);
    }
    return Status::ok();
}

Status CameraService::getCameraInfo(int cameraId,
        CameraInfo* cameraInfo) {
    ATRACE_CALL();
    Mutex::Autolock l(mServiceLock);

    if (!mInitialized) {
        return STATUS_ERROR(ERROR_DISCONNECTED,
                "Camera subsystem is not available");
    }

    if (cameraId < 0 || cameraId >= mNumberOfCameras) {
        return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT,
                "CameraId is not valid");
    }

    Status ret = Status::ok();
    status_t err = mCameraProviderManager->getCameraInfo(std::to_string(cameraId), cameraInfo);
    if (err != OK) {
        ret = STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                "Error retrieving camera info from device %d: %s (%d)", cameraId,
                strerror(-err), err);
    }

    return ret;
}

int CameraService::cameraIdToInt(const String8& cameraId) {
    int id;
    bool success = base::ParseInt(cameraId.string(), &id, 0);
    if (!success) {
        return -1;
    }
    return id;
}

Status CameraService::getCameraCharacteristics(const String16& cameraId,
        CameraMetadata* cameraInfo) {
    ATRACE_CALL();
    if (!cameraInfo) {
        ALOGE("%s: cameraInfo is NULL", __FUNCTION__);
        return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, "cameraInfo is NULL");
    }

    if (!mInitialized) {
        ALOGE("%s: Camera HAL couldn't be initialized", __FUNCTION__);
        return STATUS_ERROR(ERROR_DISCONNECTED,
                "Camera subsystem is not available");;
    }

    Status ret{};

    status_t res = mCameraProviderManager->getCameraCharacteristics(
            String8(cameraId).string(), cameraInfo);
    if (res != OK) {
        return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION, "Unable to retrieve camera "
                "characteristics for device %s: %s (%d)", String8(cameraId).string(),
                strerror(-res), res);
    }

    return ret;
}

int CameraService::getCallingPid() {
    return IPCThreadState::self()->getCallingPid();
}

int CameraService::getCallingUid() {
    return IPCThreadState::self()->getCallingUid();
}

String8 CameraService::getFormattedCurrentTime() {
    time_t now = time(nullptr);
    char formattedTime[64];
    strftime(formattedTime, sizeof(formattedTime), "%m-%d %H:%M:%S", localtime(&now));
    return String8(formattedTime);
}

Status CameraService::getCameraVendorTagDescriptor(
        /*out*/
        hardware::camera2::params::VendorTagDescriptor* desc) {
    ATRACE_CALL();
    if (!mInitialized) {
        ALOGE("%s: Camera HAL couldn't be initialized", __FUNCTION__);
        return STATUS_ERROR(ERROR_DISCONNECTED, "Camera subsystem not available");
    }
    sp<VendorTagDescriptor> globalDescriptor = VendorTagDescriptor::getGlobalVendorTagDescriptor();
    if (globalDescriptor != nullptr) {
        *desc = *(globalDescriptor.get());
    }
    return Status::ok();
}

Status CameraService::getCameraVendorTagCache(
        /*out*/ hardware::camera2::params::VendorTagDescriptorCache* cache) {
    ATRACE_CALL();
    if (!mInitialized) {
        ALOGE("%s: Camera HAL couldn't be initialized", __FUNCTION__);
        return STATUS_ERROR(ERROR_DISCONNECTED,
                "Camera subsystem not available");
    }
    sp<VendorTagDescriptorCache> globalCache =
            VendorTagDescriptorCache::getGlobalVendorTagCache();
    if (globalCache != nullptr) {
        *cache = *(globalCache.get());
    }
    return Status::ok();
}

int CameraService::getDeviceVersion(const String8& cameraId, int* facing) {
    ATRACE_CALL();

    int deviceVersion = 0;

    status_t res;
    hardware::hidl_version maxVersion{0,0};
    res = mCameraProviderManager->getHighestSupportedVersion(cameraId.string(),
            &maxVersion);
    if (res != OK) return -1;
    deviceVersion = HARDWARE_DEVICE_API_VERSION(maxVersion.get_major(), maxVersion.get_minor());

    hardware::CameraInfo info;
    if (facing) {
        res = mCameraProviderManager->getCameraInfo(cameraId.string(), &info);
        if (res != OK) return -1;
        *facing = info.facing;
    }

    return deviceVersion;
}

Status CameraService::filterGetInfoErrorCode(status_t err) {
    switch(err) {
        case NO_ERROR:
            return Status::ok();
        case BAD_VALUE:
            return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT,
                    "CameraId is not valid for HAL module");
        case NO_INIT:
            return STATUS_ERROR(ERROR_DISCONNECTED,
                    "Camera device not available");
        default:
            return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                    "Camera HAL encountered error %d: %s",
                    err, strerror(-err));
    }
}

Status CameraService::makeClient(const sp<CameraService>& cameraService,
        const sp<IInterface>& cameraCb, const String16& packageName, const String8& cameraId,
        int facing, int clientPid, uid_t clientUid, int servicePid, bool legacyMode,
        int halVersion, int deviceVersion, apiLevel effectiveApiLevel,
        /*out*/sp<BasicClient>* client) {

    if (halVersion < 0 || halVersion == deviceVersion) {
        // Default path: HAL version is unspecified by caller, create CameraClient
        // based on device version reported by the HAL.
        switch(deviceVersion) {
          case CAMERA_DEVICE_API_VERSION_1_0:
            if (effectiveApiLevel == API_1) {  // Camera1 API route
                sp<ICameraClient> tmp = static_cast<ICameraClient*>(cameraCb.get());
                *client = new CameraClient(cameraService, tmp, packageName, cameraIdToInt(cameraId),
                        facing, clientPid, clientUid, getpid(), legacyMode);
            } else { // Camera2 API route
                ALOGW("Camera using old HAL version: %d", deviceVersion);
                return STATUS_ERROR_FMT(ERROR_DEPRECATED_HAL,
                        "Camera device \"%s\" HAL version %d does not support camera2 API",
                        cameraId.string(), deviceVersion);
            }
            break;
          case CAMERA_DEVICE_API_VERSION_3_0:
          case CAMERA_DEVICE_API_VERSION_3_1:
          case CAMERA_DEVICE_API_VERSION_3_2:
          case CAMERA_DEVICE_API_VERSION_3_3:
          case CAMERA_DEVICE_API_VERSION_3_4:
            if (effectiveApiLevel == API_1) { // Camera1 API route
                sp<ICameraClient> tmp = static_cast<ICameraClient*>(cameraCb.get());
                *client = new Camera2Client(cameraService, tmp, packageName, cameraIdToInt(cameraId),
                        facing, clientPid, clientUid, servicePid, legacyMode);
            } else { // Camera2 API route
                sp<hardware::camera2::ICameraDeviceCallbacks> tmp =
                        static_cast<hardware::camera2::ICameraDeviceCallbacks*>(cameraCb.get());
                *client = new CameraDeviceClient(cameraService, tmp, packageName, cameraId,
                        facing, clientPid, clientUid, servicePid);
            }
            break;
          default:
            // Should not be reachable
            ALOGE("Unknown camera device HAL version: %d", deviceVersion);
            return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                    "Camera device \"%s\" has unknown HAL version %d",
                    cameraId.string(), deviceVersion);
        }
    } else {
        // A particular HAL version is requested by caller. Create CameraClient
        // based on the requested HAL version.
        if (deviceVersion > CAMERA_DEVICE_API_VERSION_1_0 &&
            halVersion == CAMERA_DEVICE_API_VERSION_1_0) {
            // Only support higher HAL version device opened as HAL1.0 device.
            sp<ICameraClient> tmp = static_cast<ICameraClient*>(cameraCb.get());
            *client = new CameraClient(cameraService, tmp, packageName, cameraIdToInt(cameraId),
                    facing, clientPid, clientUid, servicePid, legacyMode);
        } else {
            // Other combinations (e.g. HAL3.x open as HAL2.x) are not supported yet.
            ALOGE("Invalid camera HAL version %x: HAL %x device can only be"
                    " opened as HAL %x device", halVersion, deviceVersion,
                    CAMERA_DEVICE_API_VERSION_1_0);
            return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                    "Camera device \"%s\" (HAL version %d) cannot be opened as HAL version %d",
                    cameraId.string(), deviceVersion, halVersion);
        }
    }
    return Status::ok();
}

String8 CameraService::toString(std::set<userid_t> intSet) {
    String8 s("");
    bool first = true;
    for (userid_t i : intSet) {
        if (first) {
            s.appendFormat("%d", i);
            first = false;
        } else {
            s.appendFormat(", %d", i);
        }
    }
    return s;
}

int32_t CameraService::mapToInterface(TorchModeStatus status) {
    int32_t serviceStatus = ICameraServiceListener::TORCH_STATUS_NOT_AVAILABLE;
    switch (status) {
        case TorchModeStatus::NOT_AVAILABLE:
            serviceStatus = ICameraServiceListener::TORCH_STATUS_NOT_AVAILABLE;
            break;
        case TorchModeStatus::AVAILABLE_OFF:
            serviceStatus = ICameraServiceListener::TORCH_STATUS_AVAILABLE_OFF;
            break;
        case TorchModeStatus::AVAILABLE_ON:
            serviceStatus = ICameraServiceListener::TORCH_STATUS_AVAILABLE_ON;
            break;
        default:
            ALOGW("Unknown new flash status: %d", status);
    }
    return serviceStatus;
}

CameraService::StatusInternal CameraService::mapToInternal(CameraDeviceStatus status) {
    StatusInternal serviceStatus = StatusInternal::NOT_PRESENT;
    switch (status) {
        case CameraDeviceStatus::NOT_PRESENT:
            serviceStatus = StatusInternal::NOT_PRESENT;
            break;
        case CameraDeviceStatus::PRESENT:
            serviceStatus = StatusInternal::PRESENT;
            break;
        case CameraDeviceStatus::ENUMERATING:
            serviceStatus = StatusInternal::ENUMERATING;
            break;
        default:
            ALOGW("Unknown new HAL device status: %d", status);
    }
    return serviceStatus;
}

int32_t CameraService::mapToInterface(StatusInternal status) {
    int32_t serviceStatus = ICameraServiceListener::STATUS_NOT_PRESENT;
    switch (status) {
        case StatusInternal::NOT_PRESENT:
            serviceStatus = ICameraServiceListener::STATUS_NOT_PRESENT;
            break;
        case StatusInternal::PRESENT:
            serviceStatus = ICameraServiceListener::STATUS_PRESENT;
            break;
        case StatusInternal::ENUMERATING:
            serviceStatus = ICameraServiceListener::STATUS_ENUMERATING;
            break;
        case StatusInternal::NOT_AVAILABLE:
            serviceStatus = ICameraServiceListener::STATUS_NOT_AVAILABLE;
            break;
        case StatusInternal::UNKNOWN:
            serviceStatus = ICameraServiceListener::STATUS_UNKNOWN;
            break;
        default:
            ALOGW("Unknown new internal device status: %d", status);
    }
    return serviceStatus;
}

Status CameraService::initializeShimMetadata(int cameraId) {
    int uid = getCallingUid();

    String16 internalPackageName("cameraserver");
    String8 id = String8::format("%d", cameraId);
    Status ret = Status::ok();
    sp<Client> tmp = nullptr;
    if (!(ret = connectHelper<ICameraClient,Client>(
            sp<ICameraClient>{nullptr}, id, static_cast<int>(CAMERA_HAL_API_VERSION_UNSPECIFIED),
            internalPackageName, uid, USE_CALLING_PID,
            API_1, /*legacyMode*/ false, /*shimUpdateOnly*/ true,
            /*out*/ tmp)
            ).isOk()) {
        ALOGE("%s: Error initializing shim metadata: %s", __FUNCTION__, ret.toString8().string());
    }
    return ret;
}

Status CameraService::getLegacyParametersLazy(int cameraId,
        /*out*/
        CameraParameters* parameters) {

    ALOGV("%s: for cameraId: %d", __FUNCTION__, cameraId);

    Status ret = Status::ok();

    if (parameters == NULL) {
        ALOGE("%s: parameters must not be null", __FUNCTION__);
        return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, "Parameters must not be null");
    }

    String8 id = String8::format("%d", cameraId);

    // Check if we already have parameters
    {
        // Scope for service lock
        Mutex::Autolock lock(mServiceLock);
        auto cameraState = getCameraState(id);
        if (cameraState == nullptr) {
            ALOGE("%s: Invalid camera ID: %s", __FUNCTION__, id.string());
            return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                    "Invalid camera ID: %s", id.string());
        }
        CameraParameters p = cameraState->getShimParams();
        if (!p.isEmpty()) {
            *parameters = p;
            return ret;
        }
    }

    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    ret = initializeShimMetadata(cameraId);
    IPCThreadState::self()->restoreCallingIdentity(token);
    if (!ret.isOk()) {
        // Error already logged by callee
        return ret;
    }

    // Check for parameters again
    {
        // Scope for service lock
        Mutex::Autolock lock(mServiceLock);
        auto cameraState = getCameraState(id);
        if (cameraState == nullptr) {
            ALOGE("%s: Invalid camera ID: %s", __FUNCTION__, id.string());
            return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                    "Invalid camera ID: %s", id.string());
        }
        CameraParameters p = cameraState->getShimParams();
        if (!p.isEmpty()) {
            *parameters = p;
            return ret;
        }
    }

    ALOGE("%s: Parameters were not initialized, or were empty.  Device may not be present.",
            __FUNCTION__);
    return STATUS_ERROR(ERROR_INVALID_OPERATION, "Unable to initialize legacy parameters");
}

// Can camera service trust the caller based on the calling UID?
static bool isTrustedCallingUid(uid_t uid) {
    switch (uid) {
        case AID_MEDIA:        // mediaserver
        case AID_CAMERASERVER: // cameraserver
        case AID_RADIO:        // telephony
            return true;
        default:
            return false;
    }
}

Status CameraService::validateConnectLocked(const String8& cameraId,
        const String8& clientName8, /*inout*/int& clientUid, /*inout*/int& clientPid,
        /*out*/int& originalClientPid) const {

#ifdef __BRILLO__
    UNUSED(clientName8);
    UNUSED(clientUid);
    UNUSED(clientPid);
    UNUSED(originalClientPid);
#else
    Status allowed = validateClientPermissionsLocked(cameraId, clientName8, clientUid, clientPid,
            originalClientPid);
    if (!allowed.isOk()) {
        return allowed;
    }
#endif  // __BRILLO__

    int callingPid = getCallingPid();

    if (!mInitialized) {
        ALOGE("CameraService::connect X (PID %d) rejected (camera HAL module not loaded)",
                callingPid);
        return STATUS_ERROR_FMT(ERROR_DISCONNECTED,
                "No camera HAL module available to open camera device \"%s\"", cameraId.string());
    }

    if (getCameraState(cameraId) == nullptr) {
        ALOGE("CameraService::connect X (PID %d) rejected (invalid camera ID %s)", callingPid,
                cameraId.string());
        return STATUS_ERROR_FMT(ERROR_DISCONNECTED,
                "No camera device with ID \"%s\" available", cameraId.string());
    }

    status_t err = checkIfDeviceIsUsable(cameraId);
    if (err != NO_ERROR) {
        switch(err) {
            case -ENODEV:
            case -EBUSY:
                return STATUS_ERROR_FMT(ERROR_DISCONNECTED,
                        "No camera device with ID \"%s\" currently available", cameraId.string());
            default:
                return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                        "Unknown error connecting to ID \"%s\"", cameraId.string());
        }
    }
    return Status::ok();
}

Status CameraService::validateClientPermissionsLocked(const String8& cameraId,
        const String8& clientName8, int& clientUid, int& clientPid,
        /*out*/int& originalClientPid) const {
    int callingPid = getCallingPid();
    int callingUid = getCallingUid();

    // Check if we can trust clientUid
    if (clientUid == USE_CALLING_UID) {
        clientUid = callingUid;
    } else if (!isTrustedCallingUid(callingUid)) {
        ALOGE("CameraService::connect X (calling PID %d, calling UID %d) rejected "
                "(don't trust clientUid %d)", callingPid, callingUid, clientUid);
        return STATUS_ERROR_FMT(ERROR_PERMISSION_DENIED,
                "Untrusted caller (calling PID %d, UID %d) trying to "
                "forward camera access to camera %s for client %s (PID %d, UID %d)",
                callingPid, callingUid, cameraId.string(),
                clientName8.string(), clientUid, clientPid);
    }

    // Check if we can trust clientPid
    if (clientPid == USE_CALLING_PID) {
        clientPid = callingPid;
    } else if (!isTrustedCallingUid(callingUid)) {
        ALOGE("CameraService::connect X (calling PID %d, calling UID %d) rejected "
                "(don't trust clientPid %d)", callingPid, callingUid, clientPid);
        return STATUS_ERROR_FMT(ERROR_PERMISSION_DENIED,
                "Untrusted caller (calling PID %d, UID %d) trying to "
                "forward camera access to camera %s for client %s (PID %d, UID %d)",
                callingPid, callingUid, cameraId.string(),
                clientName8.string(), clientUid, clientPid);
    }

    // If it's not calling from cameraserver, check the permission.
    if (callingPid != getpid() &&
            !checkPermission(String16("android.permission.CAMERA"), clientPid, clientUid)) {
        ALOGE("Permission Denial: can't use the camera pid=%d, uid=%d", clientPid, clientUid);
        return STATUS_ERROR_FMT(ERROR_PERMISSION_DENIED,
                "Caller \"%s\" (PID %d, UID %d) cannot open camera \"%s\" without camera permission",
                clientName8.string(), clientUid, clientPid, cameraId.string());
    }

    // Only use passed in clientPid to check permission. Use calling PID as the client PID that's
    // connected to camera service directly.
    originalClientPid = clientPid;
    clientPid = callingPid;

    userid_t clientUserId = multiuser_get_user_id(clientUid);

    // Only allow clients who are being used by the current foreground device user, unless calling
    // from our own process.
    if (callingPid != getpid() && (mAllowedUsers.find(clientUserId) == mAllowedUsers.end())) {
        ALOGE("CameraService::connect X (PID %d) rejected (cannot connect from "
                "device user %d, currently allowed device users: %s)", callingPid, clientUserId,
                toString(mAllowedUsers).string());
        return STATUS_ERROR_FMT(ERROR_PERMISSION_DENIED,
                "Callers from device user %d are not currently allowed to connect to camera \"%s\"",
                clientUserId, cameraId.string());
    }

    return Status::ok();
}

status_t CameraService::checkIfDeviceIsUsable(const String8& cameraId) const {
    auto cameraState = getCameraState(cameraId);
    int callingPid = getCallingPid();
    if (cameraState == nullptr) {
        ALOGE("CameraService::connect X (PID %d) rejected (invalid camera ID %s)", callingPid,
                cameraId.string());
        return -ENODEV;
    }

    StatusInternal currentStatus = cameraState->getStatus();
    if (currentStatus == StatusInternal::NOT_PRESENT) {
        ALOGE("CameraService::connect X (PID %d) rejected (camera %s is not connected)",
                callingPid, cameraId.string());
        return -ENODEV;
    } else if (currentStatus == StatusInternal::ENUMERATING) {
        ALOGE("CameraService::connect X (PID %d) rejected, (camera %s is initializing)",
                callingPid, cameraId.string());
        return -EBUSY;
    }

    return NO_ERROR;
}

void CameraService::finishConnectLocked(const sp<BasicClient>& client,
        const CameraService::DescriptorPtr& desc) {

    // Make a descriptor for the incoming client
    auto clientDescriptor = CameraService::CameraClientManager::makeClientDescriptor(client, desc);
    auto evicted = mActiveClientManager.addAndEvict(clientDescriptor);

    logConnected(desc->getKey(), static_cast<int>(desc->getOwnerId()),
            String8(client->getPackageName()));

    if (evicted.size() > 0) {
        // This should never happen - clients should already have been removed in disconnect
        for (auto& i : evicted) {
            ALOGE("%s: Invalid state: Client for camera %s was not removed in disconnect",
                    __FUNCTION__, i->getKey().string());
        }

        LOG_ALWAYS_FATAL("%s: Invalid state for CameraService, clients not evicted properly",
                __FUNCTION__);
    }

    // And register a death notification for the client callback. Do
    // this last to avoid Binder policy where a nested Binder
    // transaction might be pre-empted to service the client death
    // notification if the client process dies before linkToDeath is
    // invoked.
    sp<IBinder> remoteCallback = client->getRemote();
    if (remoteCallback != nullptr) {
        remoteCallback->linkToDeath(this);
    }
}

status_t CameraService::handleEvictionsLocked(const String8& cameraId, int clientPid,
        apiLevel effectiveApiLevel, const sp<IBinder>& remoteCallback, const String8& packageName,
        /*out*/
        sp<BasicClient>* client,
        std::shared_ptr<resource_policy::ClientDescriptor<String8, sp<BasicClient>>>* partial) {
    ATRACE_CALL();
    status_t ret = NO_ERROR;
    std::vector<DescriptorPtr> evictedClients;
    DescriptorPtr clientDescriptor;
    {
        if (effectiveApiLevel == API_1) {
            // If we are using API1, any existing client for this camera ID with the same remote
            // should be returned rather than evicted to allow MediaRecorder to work properly.

            auto current = mActiveClientManager.get(cameraId);
            if (current != nullptr) {
                auto clientSp = current->getValue();
                if (clientSp.get() != nullptr) { // should never be needed
                    if (!clientSp->canCastToApiClient(effectiveApiLevel)) {
                        ALOGW("CameraService connect called from same client, but with a different"
                                " API level, evicting prior client...");
                    } else if (clientSp->getRemote() == remoteCallback) {
                        ALOGI("CameraService::connect X (PID %d) (second call from same"
                                " app binder, returning the same client)", clientPid);
                        *client = clientSp;
                        return NO_ERROR;
                    }
                }
            }
        }

        // Get current active client PIDs
        std::vector<int> ownerPids(mActiveClientManager.getAllOwners());
        ownerPids.push_back(clientPid);

        std::vector<int> priorityScores(ownerPids.size());
        std::vector<int> states(ownerPids.size());

        // Get priority scores of all active PIDs
        status_t err = ProcessInfoService::getProcessStatesScoresFromPids(
                ownerPids.size(), &ownerPids[0], /*out*/&states[0],
                /*out*/&priorityScores[0]);
        if (err != OK) {
            ALOGE("%s: Priority score query failed: %d",
                  __FUNCTION__, err);
            return err;
        }

        // Update all active clients' priorities
        std::map<int,resource_policy::ClientPriority> pidToPriorityMap;
        for (size_t i = 0; i < ownerPids.size() - 1; i++) {
            pidToPriorityMap.emplace(ownerPids[i],
                    resource_policy::ClientPriority(priorityScores[i], states[i]));
        }
        mActiveClientManager.updatePriorities(pidToPriorityMap);

        // Get state for the given cameraId
        auto state = getCameraState(cameraId);
        if (state == nullptr) {
            ALOGE("CameraService::connect X (PID %d) rejected (no camera device with ID %s)",
                clientPid, cameraId.string());
            // Should never get here because validateConnectLocked should have errored out
            return BAD_VALUE;
        }

        // Make descriptor for incoming client
        clientDescriptor = CameraClientManager::makeClientDescriptor(cameraId,
                sp<BasicClient>{nullptr}, static_cast<int32_t>(state->getCost()),
                state->getConflicting(),
                priorityScores[priorityScores.size() - 1],
                clientPid,
                states[states.size() - 1]);

        // Find clients that would be evicted
        auto evicted = mActiveClientManager.wouldEvict(clientDescriptor);

        // If the incoming client was 'evicted,' higher priority clients have the camera in the
        // background, so we cannot do evictions
        if (std::find(evicted.begin(), evicted.end(), clientDescriptor) != evicted.end()) {
            ALOGE("CameraService::connect X (PID %d) rejected (existing client(s) with higher"
                    " priority).", clientPid);

            sp<BasicClient> clientSp = clientDescriptor->getValue();
            String8 curTime = getFormattedCurrentTime();
            auto incompatibleClients =
                    mActiveClientManager.getIncompatibleClients(clientDescriptor);

            String8 msg = String8::format("%s : DENIED connect device %s client for package %s "
                    "(PID %d, score %d state %d) due to eviction policy", curTime.string(),
                    cameraId.string(), packageName.string(), clientPid,
                    priorityScores[priorityScores.size() - 1],
                    states[states.size() - 1]);

            for (auto& i : incompatibleClients) {
                msg.appendFormat("\n   - Blocked by existing device %s client for package %s"
                        "(PID %" PRId32 ", score %" PRId32 ", state %" PRId32 ")",
                        i->getKey().string(),
                        String8{i->getValue()->getPackageName()}.string(),
                        i->getOwnerId(), i->getPriority().getScore(),
                        i->getPriority().getState());
                ALOGE("   Conflicts with: Device %s, client package %s (PID %"
                        PRId32 ", score %" PRId32 ", state %" PRId32 ")", i->getKey().string(),
                        String8{i->getValue()->getPackageName()}.string(), i->getOwnerId(),
                        i->getPriority().getScore(), i->getPriority().getState());
            }

            // Log the client's attempt
            Mutex::Autolock l(mLogLock);
            mEventLog.add(msg);

            return -EBUSY;
        }

        for (auto& i : evicted) {
            sp<BasicClient> clientSp = i->getValue();
            if (clientSp.get() == nullptr) {
                ALOGE("%s: Invalid state: Null client in active client list.", __FUNCTION__);

                // TODO: Remove this
                LOG_ALWAYS_FATAL("%s: Invalid state for CameraService, null client in active list",
                        __FUNCTION__);
                mActiveClientManager.remove(i);
                continue;
            }

            ALOGE("CameraService::connect evicting conflicting client for camera ID %s",
                    i->getKey().string());
            evictedClients.push_back(i);

            // Log the clients evicted
            logEvent(String8::format("EVICT device %s client held by package %s (PID"
                    " %" PRId32 ", score %" PRId32 ", state %" PRId32 ")\n - Evicted by device %s client for"
                    " package %s (PID %d, score %" PRId32 ", state %" PRId32 ")",
                    i->getKey().string(), String8{clientSp->getPackageName()}.string(),
                    i->getOwnerId(), i->getPriority().getScore(),
                    i->getPriority().getState(), cameraId.string(),
                    packageName.string(), clientPid,
                    priorityScores[priorityScores.size() - 1],
                    states[states.size() - 1]));

            // Notify the client of disconnection
            clientSp->notifyError(hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_DISCONNECTED,
                    CaptureResultExtras());
        }
    }

    // Do not hold mServiceLock while disconnecting clients, but retain the condition blocking
    // other clients from connecting in mServiceLockWrapper if held
    mServiceLock.unlock();

    // Clear caller identity temporarily so client disconnect PID checks work correctly
    int64_t token = IPCThreadState::self()->clearCallingIdentity();

    // Destroy evicted clients
    for (auto& i : evictedClients) {
        // Disconnect is blocking, and should only have returned when HAL has cleaned up
        i->getValue()->disconnect(); // Clients will remove themselves from the active client list
    }

    IPCThreadState::self()->restoreCallingIdentity(token);

    for (const auto& i : evictedClients) {
        ALOGV("%s: Waiting for disconnect to complete for client for device %s (PID %" PRId32 ")",
                __FUNCTION__, i->getKey().string(), i->getOwnerId());
        ret = mActiveClientManager.waitUntilRemoved(i, DEFAULT_DISCONNECT_TIMEOUT_NS);
        if (ret == TIMED_OUT) {
            ALOGE("%s: Timed out waiting for client for device %s to disconnect, "
                    "current clients:\n%s", __FUNCTION__, i->getKey().string(),
                    mActiveClientManager.toString().string());
            return -EBUSY;
        }
        if (ret != NO_ERROR) {
            ALOGE("%s: Received error waiting for client for device %s to disconnect: %s (%d), "
                    "current clients:\n%s", __FUNCTION__, i->getKey().string(), strerror(-ret),
                    ret, mActiveClientManager.toString().string());
            return ret;
        }
    }

    evictedClients.clear();

    // Once clients have been disconnected, relock
    mServiceLock.lock();

    // Check again if the device was unplugged or something while we weren't holding mServiceLock
    if ((ret = checkIfDeviceIsUsable(cameraId)) != NO_ERROR) {
        return ret;
    }

    *partial = clientDescriptor;
    return NO_ERROR;
}

Status CameraService::connect(
        const sp<ICameraClient>& cameraClient,
        int cameraId,
        const String16& clientPackageName,
        int clientUid,
        int clientPid,
        /*out*/
        sp<ICamera>* device) {

    ATRACE_CALL();
    Status ret = Status::ok();
    String8 id = String8::format("%d", cameraId);
    sp<Client> client = nullptr;
    ret = connectHelper<ICameraClient,Client>(cameraClient, id,
            CAMERA_HAL_API_VERSION_UNSPECIFIED, clientPackageName, clientUid, clientPid, API_1,
            /*legacyMode*/ false, /*shimUpdateOnly*/ false,
            /*out*/client);

    if(!ret.isOk()) {
        logRejected(id, getCallingPid(), String8(clientPackageName),
                ret.toString8());
        return ret;
    }

    *device = client;
    return ret;
}

Status CameraService::connectLegacy(
        const sp<ICameraClient>& cameraClient,
        int cameraId, int halVersion,
        const String16& clientPackageName,
        int clientUid,
        /*out*/
        sp<ICamera>* device) {

    ATRACE_CALL();
    String8 id = String8::format("%d", cameraId);

    Status ret = Status::ok();
    sp<Client> client = nullptr;
    ret = connectHelper<ICameraClient,Client>(cameraClient, id, halVersion,
            clientPackageName, clientUid, USE_CALLING_PID, API_1,
            /*legacyMode*/ true, /*shimUpdateOnly*/ false,
            /*out*/client);

    if(!ret.isOk()) {
        logRejected(id, getCallingPid(), String8(clientPackageName),
                ret.toString8());
        return ret;
    }

    *device = client;
    return ret;
}

Status CameraService::connectDevice(
        const sp<hardware::camera2::ICameraDeviceCallbacks>& cameraCb,
        const String16& cameraId,
        const String16& clientPackageName,
        int clientUid,
        /*out*/
        sp<hardware::camera2::ICameraDeviceUser>* device) {

    ATRACE_CALL();
    Status ret = Status::ok();
    String8 id = String8(cameraId);
    sp<CameraDeviceClient> client = nullptr;
    ret = connectHelper<hardware::camera2::ICameraDeviceCallbacks,CameraDeviceClient>(cameraCb, id,
            CAMERA_HAL_API_VERSION_UNSPECIFIED, clientPackageName,
            clientUid, USE_CALLING_PID, API_2,
            /*legacyMode*/ false, /*shimUpdateOnly*/ false,
            /*out*/client);

    if(!ret.isOk()) {
        logRejected(id, getCallingPid(), String8(clientPackageName),
                ret.toString8());
        return ret;
    }

    *device = client;
    return ret;
}

template<class CALLBACK, class CLIENT>
Status CameraService::connectHelper(const sp<CALLBACK>& cameraCb, const String8& cameraId,
        int halVersion, const String16& clientPackageName, int clientUid, int clientPid,
        apiLevel effectiveApiLevel, bool legacyMode, bool shimUpdateOnly,
        /*out*/sp<CLIENT>& device) {
    binder::Status ret = binder::Status::ok();

    String8 clientName8(clientPackageName);

    int originalClientPid = 0;

    ALOGI("CameraService::connect call (PID %d \"%s\", camera ID %s) for HAL version %s and "
            "Camera API version %d", clientPid, clientName8.string(), cameraId.string(),
            (halVersion == -1) ? "default" : std::to_string(halVersion).c_str(),
            static_cast<int>(effectiveApiLevel));

    sp<CLIENT> client = nullptr;
    {
        // Acquire mServiceLock and prevent other clients from connecting
        std::unique_ptr<AutoConditionLock> lock =
                AutoConditionLock::waitAndAcquire(mServiceLockWrapper, DEFAULT_CONNECT_TIMEOUT_NS);

        if (lock == nullptr) {
            ALOGE("CameraService::connect (PID %d) rejected (too many other clients connecting)."
                    , clientPid);
            return STATUS_ERROR_FMT(ERROR_MAX_CAMERAS_IN_USE,
                    "Cannot open camera %s for \"%s\" (PID %d): Too many other clients connecting",
                    cameraId.string(), clientName8.string(), clientPid);
        }

        // Enforce client permissions and do basic sanity checks
        if(!(ret = validateConnectLocked(cameraId, clientName8,
                /*inout*/clientUid, /*inout*/clientPid, /*out*/originalClientPid)).isOk()) {
            return ret;
        }

        // Check the shim parameters after acquiring lock, if they have already been updated and
        // we were doing a shim update, return immediately
        if (shimUpdateOnly) {
            auto cameraState = getCameraState(cameraId);
            if (cameraState != nullptr) {
                if (!cameraState->getShimParams().isEmpty()) return ret;
            }
        }

        status_t err;

        sp<BasicClient> clientTmp = nullptr;
        std::shared_ptr<resource_policy::ClientDescriptor<String8, sp<BasicClient>>> partial;
        if ((err = handleEvictionsLocked(cameraId, originalClientPid, effectiveApiLevel,
                IInterface::asBinder(cameraCb), clientName8, /*out*/&clientTmp,
                /*out*/&partial)) != NO_ERROR) {
            switch (err) {
                case -ENODEV:
                    return STATUS_ERROR_FMT(ERROR_DISCONNECTED,
                            "No camera device with ID \"%s\" currently available",
                            cameraId.string());
                case -EBUSY:
                    return STATUS_ERROR_FMT(ERROR_CAMERA_IN_USE,
                            "Higher-priority client using camera, ID \"%s\" currently unavailable",
                            cameraId.string());
                default:
                    return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                            "Unexpected error %s (%d) opening camera \"%s\"",
                            strerror(-err), err, cameraId.string());
            }
        }

        if (clientTmp.get() != nullptr) {
            // Handle special case for API1 MediaRecorder where the existing client is returned
            device = static_cast<CLIENT*>(clientTmp.get());
            return ret;
        }

        // give flashlight a chance to close devices if necessary.
        mFlashlight->prepareDeviceOpen(cameraId);

        int facing = -1;
        int deviceVersion = getDeviceVersion(cameraId, /*out*/&facing);
        if (facing == -1) {
            ALOGE("%s: Unable to get camera device \"%s\"  facing", __FUNCTION__, cameraId.string());
            return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                    "Unable to get camera device \"%s\" facing", cameraId.string());
        }

        sp<BasicClient> tmp = nullptr;
        if(!(ret = makeClient(this, cameraCb, clientPackageName, cameraId, facing, clientPid,
                clientUid, getpid(), legacyMode, halVersion, deviceVersion, effectiveApiLevel,
                /*out*/&tmp)).isOk()) {
            return ret;
        }
        client = static_cast<CLIENT*>(tmp.get());

        LOG_ALWAYS_FATAL_IF(client.get() == nullptr, "%s: CameraService in invalid state",
                __FUNCTION__);

        err = client->initialize(mCameraProviderManager);
        if (err != OK) {
            ALOGE("%s: Could not initialize client from HAL.", __FUNCTION__);
            // Errors could be from the HAL module open call or from AppOpsManager
            switch(err) {
                case BAD_VALUE:
                    return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                            "Illegal argument to HAL module for camera \"%s\"", cameraId.string());
                case -EBUSY:
                    return STATUS_ERROR_FMT(ERROR_CAMERA_IN_USE,
                            "Camera \"%s\" is already open", cameraId.string());
                case -EUSERS:
                    return STATUS_ERROR_FMT(ERROR_MAX_CAMERAS_IN_USE,
                            "Too many cameras already open, cannot open camera \"%s\"",
                            cameraId.string());
                case PERMISSION_DENIED:
                    return STATUS_ERROR_FMT(ERROR_PERMISSION_DENIED,
                            "No permission to open camera \"%s\"", cameraId.string());
                case -EACCES:
                    return STATUS_ERROR_FMT(ERROR_DISABLED,
                            "Camera \"%s\" disabled by policy", cameraId.string());
                case -ENODEV:
                default:
                    return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                            "Failed to initialize camera \"%s\": %s (%d)", cameraId.string(),
                            strerror(-err), err);
            }
        }

        // Update shim paremeters for legacy clients
        if (effectiveApiLevel == API_1) {
            // Assume we have always received a Client subclass for API1
            sp<Client> shimClient = reinterpret_cast<Client*>(client.get());
            String8 rawParams = shimClient->getParameters();
            CameraParameters params(rawParams);

            auto cameraState = getCameraState(cameraId);
            if (cameraState != nullptr) {
                cameraState->setShimParams(params);
            } else {
                ALOGE("%s: Cannot update shim parameters for camera %s, no such device exists.",
                        __FUNCTION__, cameraId.string());
            }
        }

        if (shimUpdateOnly) {
            // If only updating legacy shim parameters, immediately disconnect client
            mServiceLock.unlock();
            client->disconnect();
            mServiceLock.lock();
        } else {
            // Otherwise, add client to active clients list
            finishConnectLocked(client, partial);
        }
    } // lock is destroyed, allow further connect calls

    // Important: release the mutex here so the client can call back into the service from its
    // destructor (can be at the end of the call)
    device = client;
    return ret;
}

Status CameraService::setTorchMode(const String16& cameraId, bool enabled,
        const sp<IBinder>& clientBinder) {
    Mutex::Autolock lock(mServiceLock);

    ATRACE_CALL();
    if (enabled && clientBinder == nullptr) {
        ALOGE("%s: torch client binder is NULL", __FUNCTION__);
        return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT,
                "Torch client Binder is null");
    }

    String8 id = String8(cameraId.string());
    int uid = getCallingUid();

    // verify id is valid.
    auto state = getCameraState(id);
    if (state == nullptr) {
        ALOGE("%s: camera id is invalid %s", __FUNCTION__, id.string());
        return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                "Camera ID \"%s\" is a not valid camera ID", id.string());
    }

    StatusInternal cameraStatus = state->getStatus();
    if (cameraStatus != StatusInternal::PRESENT &&
            cameraStatus != StatusInternal::NOT_AVAILABLE) {
        ALOGE("%s: camera id is invalid %s, status %d", __FUNCTION__, id.string(), (int)cameraStatus);
        return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                "Camera ID \"%s\" is a not valid camera ID", id.string());
    }

    {
        Mutex::Autolock al(mTorchStatusMutex);
        TorchModeStatus status;
        status_t err = getTorchStatusLocked(id, &status);
        if (err != OK) {
            if (err == NAME_NOT_FOUND) {
                return STATUS_ERROR_FMT(ERROR_ILLEGAL_ARGUMENT,
                        "Camera \"%s\" does not have a flash unit", id.string());
            }
            ALOGE("%s: getting current torch status failed for camera %s",
                    __FUNCTION__, id.string());
            return STATUS_ERROR_FMT(ERROR_INVALID_OPERATION,
                    "Error updating torch status for camera \"%s\": %s (%d)", id.string(),
                    strerror(-err), err);
        }

        if (status == TorchModeStatus::NOT_AVAILABLE) {
            if (cameraStatus == StatusInternal::NOT_AVAILABLE) {
                ALOGE("%s: torch mode of camera %s is not available because "
                        "camera is in use", __FUNCTION__, id.string());
                return STATUS_ERROR_FMT(ERROR_CAMERA_IN_USE,
                        "Torch for camera \"%s\" is not available due to an existing camera user",
                        id.string());
            } else {
                ALOGE("%s: torch mode of camera %s is not available due to "
                        "insufficient resources", __FUNCTION__, id.string());
                return STATUS_ERROR_FMT(ERROR_MAX_CAMERAS_IN_USE,
                        "Torch for camera \"%s\" is not available due to insufficient resources",
                        id.string());
            }
        }
    }

    {
        // Update UID map - this is used in the torch status changed callbacks, so must be done
        // before setTorchMode
        Mutex::Autolock al(mTorchUidMapMutex);
        if (mTorchUidMap.find(id) == mTorchUidMap.end()) {
            mTorchUidMap[id].first = uid;
            mTorchUidMap[id].second = uid;
        } else {
            // Set the pending UID
            mTorchUidMap[id].first = uid;
        }
    }

    status_t err = mFlashlight->setTorchMode(id, enabled);

    if (err != OK) {
        int32_t errorCode;
        String8 msg;
        switch (err) {
            case -ENOSYS:
                msg = String8::format("Camera \"%s\" has no flashlight",
                    id.string());
                errorCode = ERROR_ILLEGAL_ARGUMENT;
                break;
            default:
                msg = String8::format(
                    "Setting torch mode of camera \"%s\" to %d failed: %s (%d)",
                    id.string(), enabled, strerror(-err), err);
                errorCode = ERROR_INVALID_OPERATION;
        }
        ALOGE("%s: %s", __FUNCTION__, msg.string());
        return STATUS_ERROR(errorCode, msg.string());
    }

    {
        // update the link to client's death
        Mutex::Autolock al(mTorchClientMapMutex);
        ssize_t index = mTorchClientMap.indexOfKey(id);
        if (enabled) {
            if (index == NAME_NOT_FOUND) {
                mTorchClientMap.add(id, clientBinder);
            } else {
                mTorchClientMap.valueAt(index)->unlinkToDeath(this);
                mTorchClientMap.replaceValueAt(index, clientBinder);
            }
            clientBinder->linkToDeath(this);
        } else if (index != NAME_NOT_FOUND) {
            mTorchClientMap.valueAt(index)->unlinkToDeath(this);
        }
    }

    return Status::ok();
}

Status CameraService::notifySystemEvent(int32_t eventId,
        const std::vector<int32_t>& args) {
    ATRACE_CALL();

    switch(eventId) {
        case ICameraService::EVENT_USER_SWITCHED: {
            doUserSwitch(/*newUserIds*/ args);
            break;
        }
        case ICameraService::EVENT_NONE:
        default: {
            ALOGW("%s: Received invalid system event from system_server: %d", __FUNCTION__,
                    eventId);
            break;
        }
    }
    return Status::ok();
}

Status CameraService::addListener(const sp<ICameraServiceListener>& listener,
        /*out*/
        std::vector<hardware::CameraStatus> *cameraStatuses) {
    ATRACE_CALL();

    ALOGV("%s: Add listener %p", __FUNCTION__, listener.get());

    if (listener == nullptr) {
        ALOGE("%s: Listener must not be null", __FUNCTION__);
        return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, "Null listener given to addListener");
    }

    Mutex::Autolock lock(mServiceLock);

    {
        Mutex::Autolock lock(mStatusListenerLock);
        for (auto& it : mListenerList) {
            if (IInterface::asBinder(it) == IInterface::asBinder(listener)) {
                ALOGW("%s: Tried to add listener %p which was already subscribed",
                      __FUNCTION__, listener.get());
                return STATUS_ERROR(ERROR_ALREADY_EXISTS, "Listener already registered");
            }
        }

        mListenerList.push_back(listener);
    }

    /* Collect current devices and status */
    {
        Mutex::Autolock lock(mCameraStatesLock);
        for (auto& i : mCameraStates) {
            cameraStatuses->emplace_back(i.first, mapToInterface(i.second->getStatus()));
        }
    }

    /*
     * Immediately signal current torch status to this listener only
     * This may be a subset of all the devices, so don't include it in the response directly
     */
    {
        Mutex::Autolock al(mTorchStatusMutex);
        for (size_t i = 0; i < mTorchStatusMap.size(); i++ ) {
            String16 id = String16(mTorchStatusMap.keyAt(i).string());
            listener->onTorchStatusChanged(mapToInterface(mTorchStatusMap.valueAt(i)), id);
        }
    }

    return Status::ok();
}

Status CameraService::removeListener(const sp<ICameraServiceListener>& listener) {
    ATRACE_CALL();

    ALOGV("%s: Remove listener %p", __FUNCTION__, listener.get());

    if (listener == 0) {
        ALOGE("%s: Listener must not be null", __FUNCTION__);
        return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, "Null listener given to removeListener");
    }

    Mutex::Autolock lock(mServiceLock);

    {
        Mutex::Autolock lock(mStatusListenerLock);
        for (auto it = mListenerList.begin(); it != mListenerList.end(); it++) {
            if (IInterface::asBinder(*it) == IInterface::asBinder(listener)) {
                mListenerList.erase(it);
                return Status::ok();
            }
        }
    }

    ALOGW("%s: Tried to remove a listener %p which was not subscribed",
          __FUNCTION__, listener.get());

    return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, "Unregistered listener given to removeListener");
}

Status CameraService::getLegacyParameters(int cameraId, /*out*/String16* parameters) {

    ATRACE_CALL();
    ALOGV("%s: for camera ID = %d", __FUNCTION__, cameraId);

    if (parameters == NULL) {
        ALOGE("%s: parameters must not be null", __FUNCTION__);
        return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, "Parameters must not be null");
    }

    Status ret = Status::ok();

    CameraParameters shimParams;
    if (!(ret = getLegacyParametersLazy(cameraId, /*out*/&shimParams)).isOk()) {
        // Error logged by caller
        return ret;
    }

    String8 shimParamsString8 = shimParams.flatten();
    String16 shimParamsString16 = String16(shimParamsString8);

    *parameters = shimParamsString16;

    return ret;
}

Status CameraService::supportsCameraApi(const String16& cameraId, int apiVersion,
        /*out*/ bool *isSupported) {
    ATRACE_CALL();

    const String8 id = String8(cameraId);

    ALOGV("%s: for camera ID = %s", __FUNCTION__, id.string());

    switch (apiVersion) {
        case API_VERSION_1:
        case API_VERSION_2:
            break;
        default:
            String8 msg = String8::format("Unknown API version %d", apiVersion);
            ALOGE("%s: %s", __FUNCTION__, msg.string());
            return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, msg.string());
    }

    int deviceVersion = getDeviceVersion(id);
    switch(deviceVersion) {
        case CAMERA_DEVICE_API_VERSION_1_0:
        case CAMERA_DEVICE_API_VERSION_3_0:
        case CAMERA_DEVICE_API_VERSION_3_1:
            if (apiVersion == API_VERSION_2) {
                ALOGV("%s: Camera id %s uses HAL version %d <3.2, doesn't support api2 without shim",
                        __FUNCTION__, id.string(), deviceVersion);
                *isSupported = false;
            } else { // if (apiVersion == API_VERSION_1) {
                ALOGV("%s: Camera id %s uses older HAL before 3.2, but api1 is always supported",
                        __FUNCTION__, id.string());
                *isSupported = true;
            }
            break;
        case CAMERA_DEVICE_API_VERSION_3_2:
        case CAMERA_DEVICE_API_VERSION_3_3:
        case CAMERA_DEVICE_API_VERSION_3_4:
            ALOGV("%s: Camera id %s uses HAL3.2 or newer, supports api1/api2 directly",
                    __FUNCTION__, id.string());
            *isSupported = true;
            break;
        case -1: {
            String8 msg = String8::format("Unknown camera ID %s", id.string());
            ALOGE("%s: %s", __FUNCTION__, msg.string());
            return STATUS_ERROR(ERROR_ILLEGAL_ARGUMENT, msg.string());
        }
        default: {
            String8 msg = String8::format("Unknown device version %x for device %s",
                    deviceVersion, id.string());
            ALOGE("%s: %s", __FUNCTION__, msg.string());
            return STATUS_ERROR(ERROR_INVALID_OPERATION, msg.string());
        }
    }

    return Status::ok();
}

void CameraService::removeByClient(const BasicClient* client) {
    Mutex::Autolock lock(mServiceLock);
    for (auto& i : mActiveClientManager.getAll()) {
        auto clientSp = i->getValue();
        if (clientSp.get() == client) {
            mActiveClientManager.remove(i);
        }
    }
}

bool CameraService::evictClientIdByRemote(const wp<IBinder>& remote) {
    const int callingPid = getCallingPid();
    const int servicePid = getpid();
    bool ret = false;
    {
        // Acquire mServiceLock and prevent other clients from connecting
        std::unique_ptr<AutoConditionLock> lock =
                AutoConditionLock::waitAndAcquire(mServiceLockWrapper);


        std::vector<sp<BasicClient>> evicted;
        for (auto& i : mActiveClientManager.getAll()) {
            auto clientSp = i->getValue();
            if (clientSp.get() == nullptr) {
                ALOGE("%s: Dead client still in mActiveClientManager.", __FUNCTION__);
                mActiveClientManager.remove(i);
                continue;
            }
            if (remote == clientSp->getRemote() && (callingPid == servicePid ||
                    callingPid == clientSp->getClientPid())) {
                mActiveClientManager.remove(i);
                evicted.push_back(clientSp);

                // Notify the client of disconnection
                clientSp->notifyError(
                        hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_DISCONNECTED,
                        CaptureResultExtras());
            }
        }

        // Do not hold mServiceLock while disconnecting clients, but retain the condition blocking
        // other clients from connecting in mServiceLockWrapper if held
        mServiceLock.unlock();

        // Do not clear caller identity, remote caller should be client proccess

        for (auto& i : evicted) {
            if (i.get() != nullptr) {
                i->disconnect();
                ret = true;
            }
        }

        // Reacquire mServiceLock
        mServiceLock.lock();

    } // lock is destroyed, allow further connect calls

    return ret;
}

std::shared_ptr<CameraService::CameraState> CameraService::getCameraState(
        const String8& cameraId) const {
    std::shared_ptr<CameraState> state;
    {
        Mutex::Autolock lock(mCameraStatesLock);
        auto iter = mCameraStates.find(cameraId);
        if (iter != mCameraStates.end()) {
            state = iter->second;
        }
    }
    return state;
}

sp<CameraService::BasicClient> CameraService::removeClientLocked(const String8& cameraId) {
    // Remove from active clients list
    auto clientDescriptorPtr = mActiveClientManager.remove(cameraId);
    if (clientDescriptorPtr == nullptr) {
        ALOGW("%s: Could not evict client, no client for camera ID %s", __FUNCTION__,
                cameraId.string());
        return sp<BasicClient>{nullptr};
    }

    return clientDescriptorPtr->getValue();
}

void CameraService::doUserSwitch(const std::vector<int32_t>& newUserIds) {
    // Acquire mServiceLock and prevent other clients from connecting
    std::unique_ptr<AutoConditionLock> lock =
            AutoConditionLock::waitAndAcquire(mServiceLockWrapper);

    std::set<userid_t> newAllowedUsers;
    for (size_t i = 0; i < newUserIds.size(); i++) {
        if (newUserIds[i] < 0) {
            ALOGE("%s: Bad user ID %d given during user switch, ignoring.",
                    __FUNCTION__, newUserIds[i]);
            return;
        }
        newAllowedUsers.insert(static_cast<userid_t>(newUserIds[i]));
    }


    if (newAllowedUsers == mAllowedUsers) {
        ALOGW("%s: Received notification of user switch with no updated user IDs.", __FUNCTION__);
        return;
    }

    logUserSwitch(mAllowedUsers, newAllowedUsers);

    mAllowedUsers = std::move(newAllowedUsers);

    // Current user has switched, evict all current clients.
    std::vector<sp<BasicClient>> evicted;
    for (auto& i : mActiveClientManager.getAll()) {
        auto clientSp = i->getValue();

        if (clientSp.get() == nullptr) {
            ALOGE("%s: Dead client still in mActiveClientManager.", __FUNCTION__);
            continue;
        }

        // Don't evict clients that are still allowed.
        uid_t clientUid = clientSp->getClientUid();
        userid_t clientUserId = multiuser_get_user_id(clientUid);
        if (mAllowedUsers.find(clientUserId) != mAllowedUsers.end()) {
            continue;
        }

        evicted.push_back(clientSp);

        String8 curTime = getFormattedCurrentTime();

        ALOGE("Evicting conflicting client for camera ID %s due to user change",
                i->getKey().string());

        // Log the clients evicted
        logEvent(String8::format("EVICT device %s client held by package %s (PID %"
                PRId32 ", score %" PRId32 ", state %" PRId32 ")\n   - Evicted due"
                " to user switch.", i->getKey().string(),
                String8{clientSp->getPackageName()}.string(),
                i->getOwnerId(), i->getPriority().getScore(),
                i->getPriority().getState()));

    }

    // Do not hold mServiceLock while disconnecting clients, but retain the condition
    // blocking other clients from connecting in mServiceLockWrapper if held.
    mServiceLock.unlock();

    // Clear caller identity temporarily so client disconnect PID checks work correctly
    int64_t token = IPCThreadState::self()->clearCallingIdentity();

    for (auto& i : evicted) {
        i->disconnect();
    }

    IPCThreadState::self()->restoreCallingIdentity(token);

    // Reacquire mServiceLock
    mServiceLock.lock();
}

void CameraService::logEvent(const char* event) {
    String8 curTime = getFormattedCurrentTime();
    Mutex::Autolock l(mLogLock);
    mEventLog.add(String8::format("%s : %s", curTime.string(), event));
}

void CameraService::logDisconnected(const char* cameraId, int clientPid,
        const char* clientPackage) {
    // Log the clients evicted
    logEvent(String8::format("DISCONNECT device %s client for package %s (PID %d)", cameraId,
            clientPackage, clientPid));
}

void CameraService::logConnected(const char* cameraId, int clientPid,
        const char* clientPackage) {
    // Log the clients evicted
    logEvent(String8::format("CONNECT device %s client for package %s (PID %d)", cameraId,
            clientPackage, clientPid));
}

void CameraService::logRejected(const char* cameraId, int clientPid,
        const char* clientPackage, const char* reason) {
    // Log the client rejected
    logEvent(String8::format("REJECT device %s client for package %s (PID %d), reason: (%s)",
            cameraId, clientPackage, clientPid, reason));
}

void CameraService::logUserSwitch(const std::set<userid_t>& oldUserIds,
        const std::set<userid_t>& newUserIds) {
    String8 newUsers = toString(newUserIds);
    String8 oldUsers = toString(oldUserIds);
    if (oldUsers.size() == 0) {
        oldUsers = "<None>";
    }
    // Log the new and old users
    logEvent(String8::format("USER_SWITCH previous allowed user IDs: %s, current allowed user IDs: %s",
            oldUsers.string(), newUsers.string()));
}

void CameraService::logDeviceRemoved(const char* cameraId, const char* reason) {
    // Log the device removal
    logEvent(String8::format("REMOVE device %s, reason: (%s)", cameraId, reason));
}

void CameraService::logDeviceAdded(const char* cameraId, const char* reason) {
    // Log the device removal
    logEvent(String8::format("ADD device %s, reason: (%s)", cameraId, reason));
}

void CameraService::logClientDied(int clientPid, const char* reason) {
    // Log the device removal
    logEvent(String8::format("DIED client(s) with PID %d, reason: (%s)", clientPid, reason));
}

void CameraService::logServiceError(const char* msg, int errorCode) {
    String8 curTime = getFormattedCurrentTime();
    logEvent(String8::format("SERVICE ERROR: %s : %d (%s)", msg, errorCode, strerror(-errorCode)));
}

status_t CameraService::onTransact(uint32_t code, const Parcel& data, Parcel* reply,
        uint32_t flags) {

    const int pid = getCallingPid();
    const int selfPid = getpid();

    // Permission checks
    switch (code) {
        case BnCameraService::NOTIFYSYSTEMEVENT: {
            if (pid != selfPid) {
                // Ensure we're being called by system_server, or similar process with
                // permissions to notify the camera service about system events
                if (!checkCallingPermission(
                        String16("android.permission.CAMERA_SEND_SYSTEM_EVENTS"))) {
                    const int uid = getCallingUid();
                    ALOGE("Permission Denial: cannot send updates to camera service about system"
                            " events from pid=%d, uid=%d", pid, uid);
                    return PERMISSION_DENIED;
                }
            }
            break;
        }
    }

    return BnCameraService::onTransact(code, data, reply, flags);
}

// We share the media players for shutter and recording sound for all clients.
// A reference count is kept to determine when we will actually release the
// media players.

MediaPlayer* CameraService::newMediaPlayer(const char *file) {
    MediaPlayer* mp = new MediaPlayer();
    if (mp->setDataSource(NULL /* httpService */, file, NULL) == NO_ERROR) {
        mp->setAudioStreamType(AUDIO_STREAM_ENFORCED_AUDIBLE);
        mp->prepare();
    } else {
        ALOGE("Failed to load CameraService sounds: %s", file);
        return NULL;
    }
    return mp;
}

void CameraService::loadSound() {
    ATRACE_CALL();

    Mutex::Autolock lock(mSoundLock);
    LOG1("CameraService::loadSound ref=%d", mSoundRef);
    if (mSoundRef++) return;
    //!++
    #if 0
    mSoundPlayer[SOUND_SHUTTER] = newMediaPlayer("/system/media/audio/ui/camera_click.ogg");
    mSoundPlayer[SOUND_RECORDING_START] = newMediaPlayer("/system/media/audio/ui/VideoRecord.ogg");
    mSoundPlayer[SOUND_RECORDING_STOP] = newMediaPlayer("/system/media/audio/ui/VideoStop.ogg");
    #else
    if( pthread_create(&mloadSoundTThreadHandle, NULL, loadSoundThread, this) != 0 )
    {
        ALOGE("loadSound pthread create failed");
    }
    #endif
    //!--
}

//!++
void CameraService::loadSoundImp() {
    LOG1("[CameraService::loadSoundImp] E");
    mSoundPlayer[SOUND_SHUTTER] = newMediaPlayer("/system/media/audio/ui/camera_click.ogg");
    mSoundPlayer[SOUND_RECORDING_START] = newMediaPlayer("/system/media/audio/ui/VideoRecord.ogg");
    mSoundPlayer[SOUND_RECORDING_STOP] = newMediaPlayer("/system/media/audio/ui/VideoStop.ogg");
    LOG1("[CameraService::loadSoundImp] X");
}

bool CameraService::waitloadSoundDone() {
    if(mloadSoundTThreadHandle != 0)
    {
        LOG1("CameraService::waitloadSoundDone E");
        int s = pthread_join(mloadSoundTThreadHandle, NULL);
        mloadSoundTThreadHandle = 0;
        LOG1("CameraService::waitloadSoundDone X");
        if( s != 0 )
        {
            ALOGE("loadSound pthread join error: %d", s);
            return false;
        }
    }
    return true;
}

void* CameraService::loadSoundThread(void* arg) {
    LOG1("[CameraService::loadSoundThread]");
    CameraService* pCameraService = (CameraService*)arg;
    pCameraService->loadSoundImp();
    pthread_exit(NULL);
    return NULL;
}
//!--

void CameraService::releaseSound() {
    Mutex::Autolock lock(mSoundLock);
    LOG1("CameraService::releaseSound ref=%d", mSoundRef);
    if (--mSoundRef) return;

    //!++
    waitloadSoundDone();
    //!--
    for (int i = 0; i < NUM_SOUNDS; i++) {
        if (mSoundPlayer[i] != 0) {
            mSoundPlayer[i]->disconnect();
            mSoundPlayer[i].clear();
        }
    }
}

void CameraService::playSound(sound_kind kind) {
    ATRACE_CALL();

    LOG1("playSound(%d)", kind);
    Mutex::Autolock lock(mSoundLock);
    //!++
    waitloadSoundDone();
    //!--
    sp<MediaPlayer> player = mSoundPlayer[kind];
    if (player != 0) {
        player->seekTo(0);
        player->start();
    }
}

// ----------------------------------------------------------------------------

CameraService::Client::Client(const sp<CameraService>& cameraService,
        const sp<ICameraClient>& cameraClient,
        const String16& clientPackageName,
        const String8& cameraIdStr, int cameraFacing,
        int clientPid, uid_t clientUid,
        int servicePid) :
        CameraService::BasicClient(cameraService,
                IInterface::asBinder(cameraClient),
                clientPackageName,
                cameraIdStr, cameraFacing,
                clientPid, clientUid,
                servicePid),
        mCameraId(CameraService::cameraIdToInt(cameraIdStr))
{
    int callingPid = getCallingPid();
    LOG1("Client::Client E (pid %d, id %d)", callingPid, mCameraId);

    mRemoteCallback = cameraClient;

    cameraService->loadSound();

    LOG1("Client::Client X (pid %d, id %d)", callingPid, mCameraId);
}

// tear down the client
CameraService::Client::~Client() {
    ALOGV("~Client");
    mDestructionStarted = true;

    sCameraService->releaseSound();
    // unconditionally disconnect. function is idempotent
    Client::disconnect();
}

sp<CameraService> CameraService::BasicClient::BasicClient::sCameraService;

CameraService::BasicClient::BasicClient(const sp<CameraService>& cameraService,
        const sp<IBinder>& remoteCallback,
        const String16& clientPackageName,
        const String8& cameraIdStr, int cameraFacing,
        int clientPid, uid_t clientUid,
        int servicePid):
        mCameraIdStr(cameraIdStr), mCameraFacing(cameraFacing),
        mClientPackageName(clientPackageName), mClientPid(clientPid), mClientUid(clientUid),
        mServicePid(servicePid),
        mDisconnected(false),
        mRemoteBinder(remoteCallback)
{
    if (sCameraService == nullptr) {
        sCameraService = cameraService;
    }
    mOpsActive = false;
    mDestructionStarted = false;

    // In some cases the calling code has no access to the package it runs under.
    // For example, NDK camera API.
    // In this case we will get the packages for the calling UID and pick the first one
    // for attributing the app op. This will work correctly for runtime permissions
    // as for legacy apps we will toggle the app op for all packages in the UID.
    // The caveat is that the operation may be attributed to the wrong package and
    // stats based on app ops may be slightly off.
    if (mClientPackageName.size() <= 0) {
        sp<IServiceManager> sm = defaultServiceManager();
        sp<IBinder> binder = sm->getService(String16(kPermissionServiceName));
        if (binder == 0) {
            ALOGE("Cannot get permission service");
            // Leave mClientPackageName unchanged (empty) and the further interaction
            // with camera will fail in BasicClient::startCameraOps
            return;
        }

        sp<IPermissionController> permCtrl = interface_cast<IPermissionController>(binder);
        Vector<String16> packages;

        permCtrl->getPackagesForUid(mClientUid, packages);

        if (packages.isEmpty()) {
            ALOGE("No packages for calling UID");
            // Leave mClientPackageName unchanged (empty) and the further interaction
            // with camera will fail in BasicClient::startCameraOps
            return;
        }
        mClientPackageName = packages[0];
    }
}

CameraService::BasicClient::~BasicClient() {
    ALOGV("~BasicClient");
    mDestructionStarted = true;
}

binder::Status CameraService::BasicClient::disconnect() {
    binder::Status res = Status::ok();
    if (mDisconnected) {
        return res;
    }
    mDisconnected = true;

    sCameraService->removeByClient(this);
    sCameraService->logDisconnected(mCameraIdStr, mClientPid,
            String8(mClientPackageName));

    sp<IBinder> remote = getRemote();
    if (remote != nullptr) {
        remote->unlinkToDeath(sCameraService);
    }

    finishCameraOps();
    // Notify flashlight that a camera device is closed.
    sCameraService->mFlashlight->deviceClosed(mCameraIdStr);
    ALOGI("%s: Disconnected client for camera %s for PID %d", __FUNCTION__, mCameraIdStr.string(),
            mClientPid);

    // client shouldn't be able to call into us anymore
    mClientPid = 0;

    return res;
}

status_t CameraService::BasicClient::dump(int, const Vector<String16>&) {
    // No dumping of clients directly over Binder,
    // must go through CameraService::dump
    android_errorWriteWithInfoLog(SN_EVENT_LOG_ID, "26265403",
            IPCThreadState::self()->getCallingUid(), NULL, 0);
    return OK;
}

String16 CameraService::BasicClient::getPackageName() const {
    return mClientPackageName;
}


int CameraService::BasicClient::getClientPid() const {
    return mClientPid;
}

uid_t CameraService::BasicClient::getClientUid() const {
    return mClientUid;
}

bool CameraService::BasicClient::canCastToApiClient(apiLevel level) const {
    // Defaults to API2.
    return level == API_2;
}

status_t CameraService::BasicClient::startCameraOps() {
    ATRACE_CALL();

    int32_t res;
    // Notify app ops that the camera is not available
    mOpsCallback = new OpsCallback(this);

    {
        ALOGV("%s: Start camera ops, package name = %s, client UID = %d",
              __FUNCTION__, String8(mClientPackageName).string(), mClientUid);
    }

    mAppOpsManager.startWatchingMode(AppOpsManager::OP_CAMERA,
            mClientPackageName, mOpsCallback);
    res = mAppOpsManager.startOp(AppOpsManager::OP_CAMERA,
            mClientUid, mClientPackageName);

    if (res == AppOpsManager::MODE_ERRORED) {
        ALOGI("Camera %s: Access for \"%s\" has been revoked",
                mCameraIdStr.string(), String8(mClientPackageName).string());
        return PERMISSION_DENIED;
    }

    if (res == AppOpsManager::MODE_IGNORED) {
        ALOGI("Camera %s: Access for \"%s\" has been restricted",
                mCameraIdStr.string(), String8(mClientPackageName).string());
        // Return the same error as for device policy manager rejection
        return -EACCES;
    }

    mOpsActive = true;

    // Transition device availability listeners from PRESENT -> NOT_AVAILABLE
    sCameraService->updateStatus(StatusInternal::NOT_AVAILABLE, mCameraIdStr);

    // Transition device state to OPEN
    sCameraService->updateProxyDeviceState(ICameraServiceProxy::CAMERA_STATE_OPEN,
            mCameraIdStr, mCameraFacing, mClientPackageName);

    return OK;
}

status_t CameraService::BasicClient::finishCameraOps() {
    ATRACE_CALL();

    // Check if startCameraOps succeeded, and if so, finish the camera op
    if (mOpsActive) {
        // Notify app ops that the camera is available again
        mAppOpsManager.finishOp(AppOpsManager::OP_CAMERA, mClientUid,
                mClientPackageName);
        mOpsActive = false;

        std::initializer_list<StatusInternal> rejected = {StatusInternal::PRESENT,
                StatusInternal::ENUMERATING};

        // Transition to PRESENT if the camera is not in either of the rejected states
        sCameraService->updateStatus(StatusInternal::PRESENT,
                mCameraIdStr, rejected);

        // Transition device state to CLOSED
        sCameraService->updateProxyDeviceState(ICameraServiceProxy::CAMERA_STATE_CLOSED,
                mCameraIdStr, mCameraFacing, mClientPackageName);
    }
    // Always stop watching, even if no camera op is active
    if (mOpsCallback != NULL) {
        mAppOpsManager.stopWatchingMode(mOpsCallback);
    }
    mOpsCallback.clear();

    return OK;
}

void CameraService::BasicClient::opChanged(int32_t op, const String16& packageName) {
    ATRACE_CALL();

    String8 name(packageName);
    String8 myName(mClientPackageName);

    if (op != AppOpsManager::OP_CAMERA) {
        ALOGW("Unexpected app ops notification received: %d", op);
        return;
    }

    int32_t res;
    res = mAppOpsManager.checkOp(AppOpsManager::OP_CAMERA,
            mClientUid, mClientPackageName);
    ALOGV("checkOp returns: %d, %s ", res,
            res == AppOpsManager::MODE_ALLOWED ? "ALLOWED" :
            res == AppOpsManager::MODE_IGNORED ? "IGNORED" :
            res == AppOpsManager::MODE_ERRORED ? "ERRORED" :
            "UNKNOWN");

    if (res != AppOpsManager::MODE_ALLOWED) {
        ALOGI("Camera %s: Access for \"%s\" revoked", mCameraIdStr.string(),
                myName.string());
        // Reset the client PID to allow server-initiated disconnect,
        // and to prevent further calls by client.
        mClientPid = getCallingPid();
        CaptureResultExtras resultExtras; // a dummy result (invalid)
        notifyError(hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_SERVICE, resultExtras);
        disconnect();
    }
}

// ----------------------------------------------------------------------------

void CameraService::Client::notifyError(int32_t errorCode,
        const CaptureResultExtras& resultExtras) {
    (void) errorCode;
    (void) resultExtras;
    if (mRemoteCallback != NULL) {
        mRemoteCallback->notifyCallback(CAMERA_MSG_ERROR, CAMERA_ERROR_RELEASED, 0);
    } else {
        ALOGE("mRemoteCallback is NULL!!");
    }
}

// NOTE: function is idempotent
binder::Status CameraService::Client::disconnect() {
    ALOGV("Client::disconnect");
    return BasicClient::disconnect();
}

bool CameraService::Client::canCastToApiClient(apiLevel level) const {
    return level == API_1;
}

CameraService::Client::OpsCallback::OpsCallback(wp<BasicClient> client):
        mClient(client) {
}

void CameraService::Client::OpsCallback::opChanged(int32_t op,
        const String16& packageName) {
    sp<BasicClient> client = mClient.promote();
    if (client != NULL) {
        client->opChanged(op, packageName);
    }
}

// ----------------------------------------------------------------------------
//                  CameraState
// ----------------------------------------------------------------------------

CameraService::CameraState::CameraState(const String8& id, int cost,
        const std::set<String8>& conflicting) : mId(id),
        mStatus(StatusInternal::PRESENT), mCost(cost), mConflicting(conflicting) {}

CameraService::CameraState::~CameraState() {}

CameraService::StatusInternal CameraService::CameraState::getStatus() const {
    Mutex::Autolock lock(mStatusLock);
    return mStatus;
}

CameraParameters CameraService::CameraState::getShimParams() const {
    return mShimParams;
}

void CameraService::CameraState::setShimParams(const CameraParameters& params) {
    mShimParams = params;
}

int CameraService::CameraState::getCost() const {
    return mCost;
}

std::set<String8> CameraService::CameraState::getConflicting() const {
    return mConflicting;
}

String8 CameraService::CameraState::getId() const {
    return mId;
}

// ----------------------------------------------------------------------------
//                  ClientEventListener
// ----------------------------------------------------------------------------

void CameraService::ClientEventListener::onClientAdded(
        const resource_policy::ClientDescriptor<String8,
        sp<CameraService::BasicClient>>& descriptor) {
    const auto& basicClient = descriptor.getValue();
    if (basicClient.get() != nullptr) {
        BatteryNotifier& notifier(BatteryNotifier::getInstance());
        notifier.noteStartCamera(descriptor.getKey(),
                static_cast<int>(basicClient->getClientUid()));
    }
}

void CameraService::ClientEventListener::onClientRemoved(
        const resource_policy::ClientDescriptor<String8,
        sp<CameraService::BasicClient>>& descriptor) {
    const auto& basicClient = descriptor.getValue();
    if (basicClient.get() != nullptr) {
        BatteryNotifier& notifier(BatteryNotifier::getInstance());
        notifier.noteStopCamera(descriptor.getKey(),
                static_cast<int>(basicClient->getClientUid()));
    }
}


// ----------------------------------------------------------------------------
//                  CameraClientManager
// ----------------------------------------------------------------------------

CameraService::CameraClientManager::CameraClientManager() {
    setListener(std::make_shared<ClientEventListener>());
}

CameraService::CameraClientManager::~CameraClientManager() {}

sp<CameraService::BasicClient> CameraService::CameraClientManager::getCameraClient(
        const String8& id) const {
    auto descriptor = get(id);
    if (descriptor == nullptr) {
        return sp<BasicClient>{nullptr};
    }
    return descriptor->getValue();
}

String8 CameraService::CameraClientManager::toString() const {
    auto all = getAll();
    String8 ret("[");
    bool hasAny = false;
    for (auto& i : all) {
        hasAny = true;
        String8 key = i->getKey();
        int32_t cost = i->getCost();
        int32_t pid = i->getOwnerId();
        int32_t score = i->getPriority().getScore();
        int32_t state = i->getPriority().getState();
        auto conflicting = i->getConflicting();
        auto clientSp = i->getValue();
        String8 packageName;
        userid_t clientUserId = 0;
        if (clientSp.get() != nullptr) {
            packageName = String8{clientSp->getPackageName()};
            uid_t clientUid = clientSp->getClientUid();
            clientUserId = multiuser_get_user_id(clientUid);
        }
        ret.appendFormat("\n(Camera ID: %s, Cost: %" PRId32 ", PID: %" PRId32 ", Score: %"
                PRId32 ", State: %" PRId32, key.string(), cost, pid, score, state);

        if (clientSp.get() != nullptr) {
            ret.appendFormat("User Id: %d, ", clientUserId);
        }
        if (packageName.size() != 0) {
            ret.appendFormat("Client Package Name: %s", packageName.string());
        }

        ret.append(", Conflicting Client Devices: {");
        for (auto& j : conflicting) {
            ret.appendFormat("%s, ", j.string());
        }
        ret.append("})");
    }
    if (hasAny) ret.append("\n");
    ret.append("]\n");
    return ret;
}

CameraService::DescriptorPtr CameraService::CameraClientManager::makeClientDescriptor(
        const String8& key, const sp<BasicClient>& value, int32_t cost,
        const std::set<String8>& conflictingKeys, int32_t score, int32_t ownerId,
        int32_t state) {

    return std::make_shared<resource_policy::ClientDescriptor<String8, sp<BasicClient>>>(
            key, value, cost, conflictingKeys, score, ownerId, state);
}

CameraService::DescriptorPtr CameraService::CameraClientManager::makeClientDescriptor(
        const sp<BasicClient>& value, const CameraService::DescriptorPtr& partial) {
    return makeClientDescriptor(partial->getKey(), value, partial->getCost(),
            partial->getConflicting(), partial->getPriority().getScore(),
            partial->getOwnerId(), partial->getPriority().getState());
}

// ----------------------------------------------------------------------------

static const int kDumpLockRetries = 50;
static const int kDumpLockSleep = 60000;

static bool tryLock(Mutex& mutex)
{
    bool locked = false;
    for (int i = 0; i < kDumpLockRetries; ++i) {
        if (mutex.tryLock() == NO_ERROR) {
            locked = true;
            break;
        }
        usleep(kDumpLockSleep);
    }
    return locked;
}

status_t CameraService::dump(int fd, const Vector<String16>& args) {
    ATRACE_CALL();

    if (checkCallingPermission(String16("android.permission.DUMP")) == false) {
        dprintf(fd, "Permission Denial: can't dump CameraService from pid=%d, uid=%d\n",
                getCallingPid(),
                getCallingUid());
        return NO_ERROR;
    }
    bool locked = tryLock(mServiceLock);
    // failed to lock - CameraService is probably deadlocked
    if (!locked) {
        dprintf(fd, "!! CameraService may be deadlocked !!\n");
    }

    if (!mInitialized) {
        dprintf(fd, "!! No camera HAL available !!\n");

        // Dump event log for error information
        dumpEventLog(fd);

        if (locked) mServiceLock.unlock();
        return NO_ERROR;
    }
    dprintf(fd, "\n== Service global info: ==\n\n");
    dprintf(fd, "Number of camera devices: %d\n", mNumberOfCameras);
    dprintf(fd, "Number of normal camera devices: %d\n", mNumberOfNormalCameras);
    String8 activeClientString = mActiveClientManager.toString();
    dprintf(fd, "Active Camera Clients:\n%s", activeClientString.string());
    dprintf(fd, "Allowed user IDs: %s\n", toString(mAllowedUsers).string());

    dumpEventLog(fd);

    bool stateLocked = tryLock(mCameraStatesLock);
    if (!stateLocked) {
        dprintf(fd, "CameraStates in use, may be deadlocked\n");
    }

    for (auto& state : mCameraStates) {
        String8 cameraId = state.first;

        dprintf(fd, "== Camera device %s dynamic info: ==\n", cameraId.string());

        CameraParameters p = state.second->getShimParams();
        if (!p.isEmpty()) {
            dprintf(fd, "  Camera1 API shim is using parameters:\n        ");
            p.dump(fd, args);
        }

        auto clientDescriptor = mActiveClientManager.get(cameraId);
        if (clientDescriptor != nullptr) {
            dprintf(fd, "  Device %s is open. Client instance dump:\n",
                    cameraId.string());
            dprintf(fd, "    Client priority score: %d state: %d\n",
                    clientDescriptor->getPriority().getScore(),
                    clientDescriptor->getPriority().getState());
            dprintf(fd, "    Client PID: %d\n", clientDescriptor->getOwnerId());

            auto client = clientDescriptor->getValue();
            dprintf(fd, "    Client package: %s\n",
                    String8(client->getPackageName()).string());

            client->dumpClient(fd, args);
        } else {
            dprintf(fd, "  Device %s is closed, no client instance\n",
                    cameraId.string());
        }

    }

    if (stateLocked) mCameraStatesLock.unlock();

    if (locked) mServiceLock.unlock();

    mCameraProviderManager->dump(fd, args);

    dprintf(fd, "\n== Vendor tags: ==\n\n");

    sp<VendorTagDescriptor> desc = VendorTagDescriptor::getGlobalVendorTagDescriptor();
    if (desc == NULL) {
        sp<VendorTagDescriptorCache> cache =
                VendorTagDescriptorCache::getGlobalVendorTagCache();
        if (cache == NULL) {
            dprintf(fd, "No vendor tags.\n");
        } else {
            cache->dump(fd, /*verbosity*/2, /*indentation*/2);
        }
    } else {
        desc->dump(fd, /*verbosity*/2, /*indentation*/2);
    }

    // Dump camera traces if there were any
    dprintf(fd, "\n");
    camera3::CameraTraces::dump(fd, args);

    // Process dump arguments, if any
    int n = args.size();
    String16 verboseOption("-v");
    String16 unreachableOption("--unreachable");
    for (int i = 0; i < n; i++) {
        if (args[i] == verboseOption) {
            // change logging level
            if (i + 1 >= n) continue;
            String8 levelStr(args[i+1]);
            int level = atoi(levelStr.string());
            dprintf(fd, "\nSetting log level to %d.\n", level);
            setLogLevel(level);
        } else if (args[i] == unreachableOption) {
            // Dump memory analysis
            // TODO - should limit be an argument parameter?
            UnreachableMemoryInfo info;
            bool success = GetUnreachableMemory(info, /*limit*/ 10000);
            if (!success) {
                dprintf(fd, "\n== Unable to dump unreachable memory. "
                        "Try disabling SELinux enforcement. ==\n");
            } else {
                dprintf(fd, "\n== Dumping unreachable memory: ==\n");
                std::string s = info.ToString(/*log_contents*/ true);
                write(fd, s.c_str(), s.size());
            }
        }
    }
    return NO_ERROR;
}

void CameraService::dumpEventLog(int fd) {
    dprintf(fd, "\n== Camera service events log (most recent at top): ==\n");

    Mutex::Autolock l(mLogLock);
    for (const auto& msg : mEventLog) {
        dprintf(fd, "  %s\n", msg.string());
    }

    if (mEventLog.size() == DEFAULT_EVENT_LOG_LENGTH) {
        dprintf(fd, "  ...\n");
    } else if (mEventLog.size() == 0) {
        dprintf(fd, "  [no events yet]\n");
    }
    dprintf(fd, "\n");
}

void CameraService::handleTorchClientBinderDied(const wp<IBinder> &who) {
    Mutex::Autolock al(mTorchClientMapMutex);
    for (size_t i = 0; i < mTorchClientMap.size(); i++) {
        if (mTorchClientMap[i] == who) {
            // turn off the torch mode that was turned on by dead client
            String8 cameraId = mTorchClientMap.keyAt(i);
            status_t res = mFlashlight->setTorchMode(cameraId, false);
            if (res) {
                ALOGE("%s: torch client died but couldn't turn off torch: "
                    "%s (%d)", __FUNCTION__, strerror(-res), res);
                return;
            }
            mTorchClientMap.removeItemsAt(i);
            break;
        }
    }
}

/*virtual*/void CameraService::binderDied(const wp<IBinder> &who) {

    /**
      * While tempting to promote the wp<IBinder> into a sp, it's actually not supported by the
      * binder driver
      */

    logClientDied(getCallingPid(), String8("Binder died unexpectedly"));

    // check torch client
    handleTorchClientBinderDied(who);

    // check camera device client
    if(!evictClientIdByRemote(who)) {
        ALOGV("%s: Java client's binder death already cleaned up (normal case)", __FUNCTION__);
        return;
    }

    ALOGE("%s: Java client's binder died, removing it from the list of active clients",
            __FUNCTION__);
}

void CameraService::updateStatus(StatusInternal status, const String8& cameraId) {
    updateStatus(status, cameraId, {});
}

void CameraService::updateStatus(StatusInternal status, const String8& cameraId,
        std::initializer_list<StatusInternal> rejectSourceStates) {
    // Do not lock mServiceLock here or can get into a deadlock from
    // connect() -> disconnect -> updateStatus

    auto state = getCameraState(cameraId);

    if (state == nullptr) {
        ALOGW("%s: Could not update the status for %s, no such device exists", __FUNCTION__,
                cameraId.string());
        return;
    }

    // Update the status for this camera state, then send the onStatusChangedCallbacks to each
    // of the listeners with both the mStatusStatus and mStatusListenerLock held
    state->updateStatus(status, cameraId, rejectSourceStates, [this]
            (const String8& cameraId, StatusInternal status) {

            if (status != StatusInternal::ENUMERATING) {
                // Update torch status if it has a flash unit.
                Mutex::Autolock al(mTorchStatusMutex);
                TorchModeStatus torchStatus;
                if (getTorchStatusLocked(cameraId, &torchStatus) !=
                        NAME_NOT_FOUND) {
                    TorchModeStatus newTorchStatus =
                            status == StatusInternal::PRESENT ?
                            TorchModeStatus::AVAILABLE_OFF :
                            TorchModeStatus::NOT_AVAILABLE;
                    if (torchStatus != newTorchStatus) {
                        onTorchStatusChangedLocked(cameraId, newTorchStatus);
                    }
                }
            }

            Mutex::Autolock lock(mStatusListenerLock);

            for (auto& listener : mListenerList) {
                listener->onStatusChanged(mapToInterface(status), String16(cameraId));
            }
        });
}

template<class Func>
void CameraService::CameraState::updateStatus(StatusInternal status,
        const String8& cameraId,
        std::initializer_list<StatusInternal> rejectSourceStates,
        Func onStatusUpdatedLocked) {
    Mutex::Autolock lock(mStatusLock);
    StatusInternal oldStatus = mStatus;
    mStatus = status;

    if (oldStatus == status) {
        return;
    }

    ALOGV("%s: Status has changed for camera ID %s from %#x to %#x", __FUNCTION__,
            cameraId.string(), oldStatus, status);

    if (oldStatus == StatusInternal::NOT_PRESENT &&
            (status != StatusInternal::PRESENT &&
             status != StatusInternal::ENUMERATING)) {

        ALOGW("%s: From NOT_PRESENT can only transition into PRESENT or ENUMERATING",
                __FUNCTION__);
        mStatus = oldStatus;
        return;
    }

    /**
     * Sometimes we want to conditionally do a transition.
     * For example if a client disconnects, we want to go to PRESENT
     * only if we weren't already in NOT_PRESENT or ENUMERATING.
     */
    for (auto& rejectStatus : rejectSourceStates) {
        if (oldStatus == rejectStatus) {
            ALOGV("%s: Rejecting status transition for Camera ID %s,  since the source "
                    "state was was in one of the bad states.", __FUNCTION__, cameraId.string());
            mStatus = oldStatus;
            return;
        }
    }

    onStatusUpdatedLocked(cameraId, status);
}

void CameraService::updateProxyDeviceState(int newState,
        const String8& cameraId, int facing, const String16& clientName) {
    sp<ICameraServiceProxy> proxyBinder = getCameraServiceProxy();
    if (proxyBinder == nullptr) return;
    String16 id(cameraId);
    proxyBinder->notifyCameraState(id, newState, facing, clientName);
}

status_t CameraService::getTorchStatusLocked(
        const String8& cameraId,
        TorchModeStatus *status) const {
    if (!status) {
        return BAD_VALUE;
    }
    ssize_t index = mTorchStatusMap.indexOfKey(cameraId);
    if (index == NAME_NOT_FOUND) {
        // invalid camera ID or the camera doesn't have a flash unit
        return NAME_NOT_FOUND;
    }

    *status = mTorchStatusMap.valueAt(index);
    return OK;
}

status_t CameraService::setTorchStatusLocked(const String8& cameraId,
        TorchModeStatus status) {
    ssize_t index = mTorchStatusMap.indexOfKey(cameraId);
    if (index == NAME_NOT_FOUND) {
        return BAD_VALUE;
    }
    mTorchStatusMap.editValueAt(index) = status;

    return OK;
}

}; // namespace android
