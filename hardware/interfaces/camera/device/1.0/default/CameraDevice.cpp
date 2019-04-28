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

#define LOG_TAG "CamDev@1.0-impl"
#include <hardware/camera.h>
#include <hardware/gralloc1.h>
#include <hidlmemory/mapping.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <media/hardware/HardwareAPI.h> // For VideoNativeHandleMetadata
#include "CameraDevice_1_0.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V1_0 {
namespace implementation {

using ::android::hardware::graphics::common::V1_0::BufferUsage;
using ::android::hardware::graphics::common::V1_0::PixelFormat;

HandleImporter CameraDevice::sHandleImporter;

Status CameraDevice::getHidlStatus(const int& status) {
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

status_t CameraDevice::getStatusT(const Status& s)  {
    switch(s) {
        case Status::OK:
            return OK;
        case Status::ILLEGAL_ARGUMENT:
            return BAD_VALUE;
        case Status::CAMERA_IN_USE:
            return -EBUSY;
        case Status::MAX_CAMERAS_IN_USE:
            return -EUSERS;
        case Status::METHOD_NOT_SUPPORTED:
            return UNKNOWN_TRANSACTION;
        case Status::OPERATION_NOT_SUPPORTED:
            return INVALID_OPERATION;
        case Status::CAMERA_DISCONNECTED:
            return DEAD_OBJECT;
        case Status::INTERNAL_ERROR:
            return INVALID_OPERATION;
    }
    ALOGW("Unexpected HAL status code %d", s);
    return INVALID_OPERATION;
}

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
    if (mDeviceVersion != CAMERA_DEVICE_API_VERSION_1_0 && !mModule->isOpenLegacyDefined()) {
        ALOGI("%s: Camera id %s does not support HAL1.0",
                __FUNCTION__, mCameraId.c_str());
        mInitFail = true;
    }

    mAshmemAllocator = IAllocator::getService("ashmem");
    if (mAshmemAllocator == nullptr) {
        ALOGI("%s: cannot get ashmemAllocator", __FUNCTION__);
        mInitFail = true;
    }
}

CameraDevice::~CameraDevice() {
    Mutex::Autolock _l(mLock);
    if (mDevice != nullptr) {
        ALOGW("%s: camera %s is deleted while open", __FUNCTION__, mCameraId.c_str());
        closeLocked();
    }
    mHalPreviewWindow.cleanUpCirculatingBuffers();
}


void CameraDevice::setConnectionStatus(bool connected) {
    Mutex::Autolock _l(mLock);
    mDisconnected = !connected;
    if (mDevice == nullptr) {
        return;
    }
    if (!connected) {
        ALOGW("%s: camera %s is disconneted. Closing", __FUNCTION__, mCameraId.c_str());
        closeLocked();
    }
    return;
}

void CameraDevice::CameraPreviewWindow::cleanUpCirculatingBuffers() {
    Mutex::Autolock _l(mLock);
    for (auto pair : mCirculatingBuffers) {
        sHandleImporter.freeBuffer(pair.second);
    }
    mCirculatingBuffers.clear();
    mBufferIdMap.clear();
}

int CameraDevice::sDequeueBuffer(struct preview_stream_ops* w,
                                   buffer_handle_t** buffer, int *stride) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    if (buffer == nullptr || stride == nullptr) {
        ALOGE("%s: buffer (%p) and stride (%p) must not be null!", __FUNCTION__, buffer, stride);
        return BAD_VALUE;
    }

    Status s;
    object->mPreviewCallback->dequeueBuffer(
        [&](auto status, uint64_t bufferId, const auto& buf, uint32_t strd) {
            s = status;
            if (s == Status::OK) {
                Mutex::Autolock _l(object->mLock);
                if (object->mCirculatingBuffers.count(bufferId) == 0) {
                    buffer_handle_t importedBuf = buf.getNativeHandle();
                    sHandleImporter.importBuffer(importedBuf);
                    if (importedBuf == nullptr) {
                        ALOGE("%s: preview buffer import failed!", __FUNCTION__);
                        s = Status::INTERNAL_ERROR;
                        return;
                    } else {
                        object->mCirculatingBuffers[bufferId] = importedBuf;
                        object->mBufferIdMap[&(object->mCirculatingBuffers[bufferId])] = bufferId;
                    }
                }
                *buffer = &(object->mCirculatingBuffers[bufferId]);
                *stride = strd;
            }
        });
    return getStatusT(s);
}

int CameraDevice::sLockBuffer(struct preview_stream_ops*, buffer_handle_t*) {
    return 0;
}

int CameraDevice::sEnqueueBuffer(struct preview_stream_ops* w, buffer_handle_t* buffer) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }
    uint64_t bufferId = object->mBufferIdMap.at(buffer);
    return getStatusT(object->mPreviewCallback->enqueueBuffer(bufferId));
}

int CameraDevice::sCancelBuffer(struct preview_stream_ops* w, buffer_handle_t* buffer) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }
    uint64_t bufferId = object->mBufferIdMap.at(buffer);
    return getStatusT(object->mPreviewCallback->cancelBuffer(bufferId));
}

int CameraDevice::sSetBufferCount(struct preview_stream_ops* w, int count) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    object->cleanUpCirculatingBuffers();
    return getStatusT(object->mPreviewCallback->setBufferCount(count));
}

int CameraDevice::sSetBuffersGeometry(struct preview_stream_ops* w,
                                         int width, int height, int format) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    object->cleanUpCirculatingBuffers();
    return getStatusT(
            object->mPreviewCallback->setBuffersGeometry(width, height, (PixelFormat) format));
}

int CameraDevice::sSetCrop(struct preview_stream_ops *w,
                             int left, int top, int right, int bottom) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    return getStatusT(object->mPreviewCallback->setCrop(left, top, right, bottom));
}

int CameraDevice::sSetTimestamp(struct preview_stream_ops *w, int64_t timestamp) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    return getStatusT(object->mPreviewCallback->setTimestamp(timestamp));
}

int CameraDevice::sSetUsage(struct preview_stream_ops* w, int usage) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    object->cleanUpCirculatingBuffers();
    return getStatusT(object->mPreviewCallback->setUsage((BufferUsage)usage));
}

int CameraDevice::sSetSwapInterval(struct preview_stream_ops *w, int interval) {
    CameraPreviewWindow* object = static_cast<CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    return getStatusT(object->mPreviewCallback->setSwapInterval(interval));
}

int CameraDevice::sGetMinUndequeuedBufferCount(
                  const struct preview_stream_ops *w,
                  int *count) {
    const CameraPreviewWindow* object =  static_cast<const CameraPreviewWindow*>(w);
    if (object->mPreviewCallback == nullptr) {
        ALOGE("%s: camera HAL calling preview ops while there is no preview window!", __FUNCTION__);
        return INVALID_OPERATION;
    }
    if (count == nullptr) {
        ALOGE("%s: count is null!", __FUNCTION__);
        return BAD_VALUE;
    }

    Status s;
    object->mPreviewCallback->getMinUndequeuedBufferCount(
        [&](auto status, uint32_t cnt) {
            s = status;
            if (s == Status::OK) {
                *count = cnt;
            }
        });
    return getStatusT(s);
}

CameraDevice::CameraHeapMemory::CameraHeapMemory(
    int fd, size_t buf_size, uint_t num_buffers) :
        mBufSize(buf_size),
        mNumBufs(num_buffers) {
    mHidlHandle = native_handle_create(1,0);
    mHidlHandle->data[0] = fcntl(fd, F_DUPFD_CLOEXEC, 0);
    const size_t pagesize = getpagesize();
    size_t size = ((buf_size * num_buffers + pagesize-1) & ~(pagesize-1));
    mHidlHeap = hidl_memory("ashmem", mHidlHandle, size);
    commonInitialization();
}

CameraDevice::CameraHeapMemory::CameraHeapMemory(
    sp<IAllocator> ashmemAllocator,
    size_t buf_size, uint_t num_buffers) :
        mBufSize(buf_size),
        mNumBufs(num_buffers) {
    const size_t pagesize = getpagesize();
    size_t size = ((buf_size * num_buffers + pagesize-1) & ~(pagesize-1));
    ashmemAllocator->allocate(size,
        [&](bool success, const hidl_memory& mem) {
            if (!success) {
                ALOGE("%s: allocating ashmem of %zu bytes failed!",
                        __FUNCTION__, buf_size * num_buffers);
                return;
            }
            mHidlHandle = native_handle_clone(mem.handle());
            mHidlHeap = hidl_memory("ashmem", mHidlHandle, size);
        });

    commonInitialization();
}

void CameraDevice::CameraHeapMemory::commonInitialization() {
    mHidlHeapMemory = mapMemory(mHidlHeap);
    if (mHidlHeapMemory == nullptr) {
        ALOGE("%s: memory map failed!", __FUNCTION__);
        native_handle_close(mHidlHandle); // close FD for the shared memory
        native_handle_delete(mHidlHandle);
        mHidlHeap = hidl_memory();
        mHidlHandle = nullptr;
        return;
    }
    mHidlHeapMemData = mHidlHeapMemory->getPointer();
    handle.data = mHidlHeapMemData;
    handle.size = mBufSize * mNumBufs;
    handle.handle = this;
    handle.release = sPutMemory;
}

CameraDevice::CameraHeapMemory::~CameraHeapMemory() {
    if (mHidlHeapMemory != nullptr) {
        mHidlHeapMemData = nullptr;
        mHidlHeapMemory.clear(); // The destructor will trigger munmap
    }

    if (mHidlHandle) {
        native_handle_close(mHidlHandle); // close FD for the shared memory
        native_handle_delete(mHidlHandle);
    }
}

// shared memory methods
camera_memory_t* CameraDevice::sGetMemory(int fd, size_t buf_size, uint_t num_bufs, void *user) {
    ALOGV("%s", __FUNCTION__);
    CameraDevice* object = static_cast<CameraDevice*>(user);
    if (object->mDeviceCallback == nullptr) {
        ALOGE("%s: camera HAL request memory while camera is not opened!", __FUNCTION__);
        return nullptr;
    }

    CameraHeapMemory* mem;
    if (fd < 0) {
        mem = new CameraHeapMemory(object->mAshmemAllocator, buf_size, num_bufs);
    } else {
        mem = new CameraHeapMemory(fd, buf_size, num_bufs);
    }
    mem->incStrong(mem);
    hidl_handle hidlHandle = mem->mHidlHandle;
    MemoryId id = object->mDeviceCallback->registerMemory(hidlHandle, buf_size, num_bufs);
    mem->handle.mId = id;

    {
        Mutex::Autolock _l(object->mMemoryMapLock);
        if (object->mMemoryMap.count(id) != 0) {
            ALOGE("%s: duplicate MemoryId %d returned by client!", __FUNCTION__, id);
        }
        object->mMemoryMap[id] = mem;
    }
    mem->handle.mDevice = object;
    return &mem->handle;
}

void CameraDevice::sPutMemory(camera_memory_t *data) {
    if (!data)
        return;

    CameraHeapMemory* mem = static_cast<CameraHeapMemory *>(data->handle);
    CameraDevice* device = mem->handle.mDevice;
    if (device == nullptr) {
        ALOGE("%s: camera HAL return memory for a null device!", __FUNCTION__);
    }
    if (device->mDeviceCallback == nullptr) {
        ALOGE("%s: camera HAL return memory while camera is not opened!", __FUNCTION__);
    }
    device->mDeviceCallback->unregisterMemory(mem->handle.mId);
    {
        Mutex::Autolock _l(device->mMemoryMapLock);
        device->mMemoryMap.erase(mem->handle.mId);
    }
    mem->decStrong(mem);
}

// Callback forwarding methods
void CameraDevice::sNotifyCb(int32_t msg_type, int32_t ext1, int32_t ext2, void *user) {
    ALOGV("%s", __FUNCTION__);
    CameraDevice* object = static_cast<CameraDevice*>(user);
    if (object->mDeviceCallback != nullptr) {
        object->mDeviceCallback->notifyCallback((NotifyCallbackMsg) msg_type, ext1, ext2);
    }
}

void CameraDevice::sDataCb(int32_t msg_type, const camera_memory_t *data, unsigned int index,
        camera_frame_metadata_t *metadata, void *user) {
    ALOGV("%s", __FUNCTION__);
    CameraDevice* object = static_cast<CameraDevice*>(user);
    sp<CameraHeapMemory> mem(static_cast<CameraHeapMemory*>(data->handle));
    if (index >= mem->mNumBufs) {
        ALOGE("%s: invalid buffer index %d, max allowed is %d", __FUNCTION__,
             index, mem->mNumBufs);
        return;
    }
    if (object->mDeviceCallback != nullptr) {
        CameraFrameMetadata hidlMetadata;
        if (metadata) {
            hidlMetadata.faces.resize(metadata->number_of_faces);
            for (size_t i = 0; i < hidlMetadata.faces.size(); i++) {
                hidlMetadata.faces[i].score = metadata->faces[i].score;
                hidlMetadata.faces[i].id = metadata->faces[i].id;
                for (int k = 0; k < 4; k++) {
                    hidlMetadata.faces[i].rect[k] = metadata->faces[i].rect[k];
                }
                for (int k = 0; k < 2; k++) {
                    hidlMetadata.faces[i].leftEye[k] = metadata->faces[i].left_eye[k];
                }
                for (int k = 0; k < 2; k++) {
                    hidlMetadata.faces[i].rightEye[k] = metadata->faces[i].right_eye[k];
                }
                for (int k = 0; k < 2; k++) {
                    hidlMetadata.faces[i].mouth[k] = metadata->faces[i].mouth[k];
                }
            }
        }
        CameraHeapMemory* mem = static_cast<CameraHeapMemory *>(data->handle);
        object->mDeviceCallback->dataCallback(
                (DataCallbackMsg) msg_type, mem->handle.mId, index, hidlMetadata);
    }
}

void CameraDevice::handleCallbackTimestamp(
        nsecs_t timestamp, int32_t msg_type,
        MemoryId memId , unsigned index, native_handle_t* handle) {
    uint32_t batchSize = 0;
    {
        Mutex::Autolock _l(mBatchLock);
        batchSize = mBatchSize;
    }

    if (batchSize == 0) { // non-batch mode
        mDeviceCallback->handleCallbackTimestamp(
                (DataCallbackMsg) msg_type, handle, memId, index, timestamp);
    } else { // batch mode
        Mutex::Autolock _l(mBatchLock);
        size_t inflightSize = mInflightBatch.size();
        if (inflightSize == 0) {
            mBatchMsgType = msg_type;
        } else if (mBatchMsgType != msg_type) {
            ALOGE("%s: msg_type change (from %d to %d) is not supported!",
                    __FUNCTION__, mBatchMsgType, msg_type);
            return;
        }
        mInflightBatch.push_back({handle, memId, index, timestamp});

        // Send batched frames to camera framework
        if (mInflightBatch.size() >= batchSize) {
            mDeviceCallback->handleCallbackTimestampBatch(
                    (DataCallbackMsg) mBatchMsgType, mInflightBatch);
            mInflightBatch.clear();
        }
    }
}

void CameraDevice::sDataCbTimestamp(nsecs_t timestamp, int32_t msg_type,
        const camera_memory_t *data, unsigned index, void *user) {
    ALOGV("%s", __FUNCTION__);
    CameraDevice* object = static_cast<CameraDevice*>(user);
    // Start refcounting the heap object from here on.  When the clients
    // drop all references, it will be destroyed (as well as the enclosed
    // MemoryHeapBase.
    sp<CameraHeapMemory> mem(static_cast<CameraHeapMemory*>(data->handle));
    if (index >= mem->mNumBufs) {
        ALOGE("%s: invalid buffer index %d, max allowed is %d", __FUNCTION__,
             index, mem->mNumBufs);
        return;
    }

    native_handle_t* handle = nullptr;
    if (object->mMetadataMode) {
        if (mem->mBufSize == sizeof(VideoNativeHandleMetadata)) {
            VideoNativeHandleMetadata* md = (VideoNativeHandleMetadata*)
                    ((uint8_t*) mem->mHidlHeapMemData + index * mem->mBufSize);
            if (md->eType == kMetadataBufferTypeNativeHandleSource) {
                handle = md->pHandle;
            }
        }
    }

    if (object->mDeviceCallback != nullptr) {
        if (handle == nullptr) {
            object->mDeviceCallback->dataCallbackTimestamp(
                    (DataCallbackMsg) msg_type, mem->handle.mId, index, timestamp);
        } else {
            object->handleCallbackTimestamp(timestamp, msg_type, mem->handle.mId, index, handle);
        }
    }
}

void CameraDevice::initHalPreviewWindow()
{
    mHalPreviewWindow.cancel_buffer = sCancelBuffer;
    mHalPreviewWindow.lock_buffer = sLockBuffer;
    mHalPreviewWindow.dequeue_buffer = sDequeueBuffer;
    mHalPreviewWindow.enqueue_buffer = sEnqueueBuffer;
    mHalPreviewWindow.set_buffer_count = sSetBufferCount;
    mHalPreviewWindow.set_buffers_geometry = sSetBuffersGeometry;
    mHalPreviewWindow.set_crop = sSetCrop;
    mHalPreviewWindow.set_timestamp = sSetTimestamp;
    mHalPreviewWindow.set_usage = sSetUsage;
    mHalPreviewWindow.set_swap_interval = sSetSwapInterval;

    mHalPreviewWindow.get_min_undequeued_buffer_count =
            sGetMinUndequeuedBufferCount;
}

// Methods from ::android::hardware::camera::device::V1_0::ICameraDevice follow.
Return<void> CameraDevice::getResourceCost(getResourceCost_cb _hidl_cb) {
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

Return<void> CameraDevice::getCameraInfo(getCameraInfo_cb _hidl_cb) {
    Status status = initStatus();
    CameraInfo cameraInfo;
    if (status == Status::OK) {
        struct camera_info info;
        int ret = mModule->getCameraInfo(mCameraIdInt, &info);
        if (ret == OK) {
            cameraInfo.facing = (CameraFacing) info.facing;
            // Device 1.0 does not support external camera facing.
            // The closest approximation would be front camera.
            if (cameraInfo.facing == CameraFacing::EXTERNAL) {
                cameraInfo.facing = CameraFacing::FRONT;
            }
            cameraInfo.orientation = info.orientation;
        } else {
            ALOGE("%s: get camera info failed!", __FUNCTION__);
            status = Status::INTERNAL_ERROR;
        }
    }
    _hidl_cb(status, cameraInfo);
    return Void();
}

Return<Status> CameraDevice::setTorchMode(TorchMode mode) {
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

Return<Status> CameraDevice::dumpState(const hidl_handle& handle) {
    Mutex::Autolock _l(mLock);
    if (handle.getNativeHandle() == nullptr) {
        ALOGE("%s: handle must not be null", __FUNCTION__);
        return Status::ILLEGAL_ARGUMENT;
    }
    if (handle->numFds != 1 || handle->numInts != 0) {
        ALOGE("%s: handle must contain 1 FD and 0 integers! Got %d FDs and %d ints",
                __FUNCTION__, handle->numFds, handle->numInts);
        return Status::ILLEGAL_ARGUMENT;
    }
    int fd = handle->data[0];

    if (mDevice != nullptr) {
        if (mDevice->ops->dump) { // It's fine if the HAL doesn't implement dump()
            return getHidlStatus(mDevice->ops->dump(mDevice, fd));
        }
    }
    return Status::OK;
}

Return<Status> CameraDevice::open(const sp<ICameraDeviceCallback>& callback) {
    ALOGI("Opening camera %s", mCameraId.c_str());
    Mutex::Autolock _l(mLock);

    camera_info info;
    status_t res = mModule->getCameraInfo(mCameraIdInt, &info);
    if (res != OK) {
        ALOGE("Could not get camera info: %s: %d", mCameraId.c_str(), res);
        return getHidlStatus(res);
    }

    int rc = OK;
    if (mModule->getModuleApiVersion() >= CAMERA_MODULE_API_VERSION_2_3 &&
        info.device_version > CAMERA_DEVICE_API_VERSION_1_0) {
        // Open higher version camera device as HAL1.0 device.
        rc = mModule->openLegacy(mCameraId.c_str(),
                                 CAMERA_DEVICE_API_VERSION_1_0,
                                 (hw_device_t **)&mDevice);
    } else {
        rc = mModule->open(mCameraId.c_str(), (hw_device_t **)&mDevice);
    }
    if (rc != OK) {
        mDevice = nullptr;
        ALOGE("Could not open camera %s: %d", mCameraId.c_str(), rc);
        return getHidlStatus(rc);
    }

    initHalPreviewWindow();
    mDeviceCallback = callback;

    if (mDevice->ops->set_callbacks) {
        mDevice->ops->set_callbacks(mDevice,
                sNotifyCb, sDataCb, sDataCbTimestamp, sGetMemory, this);
    }

    return getHidlStatus(rc);
}

Return<Status> CameraDevice::setPreviewWindow(const sp<ICameraDevicePreviewCallback>& window) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }

    mHalPreviewWindow.mPreviewCallback = window;
    if (mDevice->ops->set_preview_window) {
        return getHidlStatus(mDevice->ops->set_preview_window(mDevice,
                (window == nullptr) ? nullptr : &mHalPreviewWindow));
    }
    return Status::INTERNAL_ERROR; // HAL should provide set_preview_window
}

Return<void> CameraDevice::enableMsgType(uint32_t msgType) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Void();
    }
    if (mDevice->ops->enable_msg_type) {
        mDevice->ops->enable_msg_type(mDevice, msgType);
    }
    return Void();
}

Return<void> CameraDevice::disableMsgType(uint32_t msgType) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Void();
    }
    if (mDevice->ops->disable_msg_type) {
        mDevice->ops->disable_msg_type(mDevice, msgType);
    }
    return Void();
}

Return<bool> CameraDevice::msgTypeEnabled(uint32_t msgType) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return false;
    }
    if (mDevice->ops->msg_type_enabled) {
        return mDevice->ops->msg_type_enabled(mDevice, msgType);
    }
    return false;
}

Return<Status> CameraDevice::startPreview() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->start_preview) {
        return getHidlStatus(mDevice->ops->start_preview(mDevice));
    }
    return Status::INTERNAL_ERROR; // HAL should provide start_preview
}

Return<void> CameraDevice::stopPreview() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Void();
    }
    if (mDevice->ops->stop_preview) {
        mDevice->ops->stop_preview(mDevice);
    }
    return Void();
}

Return<bool> CameraDevice::previewEnabled() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return false;
    }
    if (mDevice->ops->preview_enabled) {
        return mDevice->ops->preview_enabled(mDevice);
    }
    return false;
}

Return<Status> CameraDevice::storeMetaDataInBuffers(bool enable) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->store_meta_data_in_buffers) {
        status_t s = mDevice->ops->store_meta_data_in_buffers(mDevice, enable);
        if (s == OK && enable) {
            mMetadataMode = true;
        }
        return getHidlStatus(s);
    }
    return enable ? Status::ILLEGAL_ARGUMENT : Status::OK;
}

Return<Status> CameraDevice::startRecording() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->start_recording) {
        return getHidlStatus(mDevice->ops->start_recording(mDevice));
    }
    return Status::ILLEGAL_ARGUMENT;
}

Return<void> CameraDevice::stopRecording() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Void();
    }
    if (mDevice->ops->stop_recording) {
        mDevice->ops->stop_recording(mDevice);
    }
    return Void();
}

Return<bool> CameraDevice::recordingEnabled() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return false;
    }
    if (mDevice->ops->recording_enabled) {
        return mDevice->ops->recording_enabled(mDevice);
    }
    return false;
}

void CameraDevice::releaseRecordingFrameLocked(
        uint32_t memId, uint32_t bufferIndex, const native_handle_t* handle) {
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return;
    }
    if (mDevice->ops->release_recording_frame) {
        CameraHeapMemory* camMemory;
        {
            Mutex::Autolock _l(mMemoryMapLock);
            auto it = mMemoryMap.find(memId);
            if (it == mMemoryMap.end() || it->second == nullptr) {
                ALOGE("%s unknown memoryId %d", __FUNCTION__, memId);
                return;
            }
            camMemory = it->second;
        }
        if (bufferIndex >= camMemory->mNumBufs) {
            ALOGE("%s: bufferIndex %d exceeds number of buffers %d",
                    __FUNCTION__, bufferIndex, camMemory->mNumBufs);
            return;
        }
        void *data = ((uint8_t *) camMemory->mHidlHeapMemData) + bufferIndex * camMemory->mBufSize;
        if (handle) {
            VideoNativeHandleMetadata* md = (VideoNativeHandleMetadata*) data;
            if (md->eType == kMetadataBufferTypeNativeHandleSource) {
                // Input handle will be closed by HIDL transport later, so clone it
                // HAL implementation is responsible to close/delete the clone
                native_handle_t* clone = native_handle_clone(handle);
                if (!clone) {
                    ALOGE("%s: failed to clone buffer %p", __FUNCTION__, handle);
                    return;
                }
                md->pHandle = clone;
            } else {
                ALOGE("%s:Malform VideoNativeHandleMetadata at memId %d, bufferId %d",
                        __FUNCTION__, memId, bufferIndex);
                return;
            }
        }
        mDevice->ops->release_recording_frame(mDevice, data);
    }
}

Return<void> CameraDevice::releaseRecordingFrame(uint32_t memId, uint32_t bufferIndex) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    releaseRecordingFrameLocked(memId, bufferIndex, nullptr);
    return Void();
}

Return<void> CameraDevice::releaseRecordingFrameHandle(
        uint32_t memId, uint32_t bufferIndex, const hidl_handle& frame) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    releaseRecordingFrameLocked(
            memId, bufferIndex, frame.getNativeHandle());
    return Void();
}

Return<void> CameraDevice::releaseRecordingFrameHandleBatch(
        const hidl_vec<VideoFrameMessage>& msgs) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    for (auto& msg : msgs) {
        releaseRecordingFrameLocked(
                msg.data, msg.bufferIndex, msg.frameData.getNativeHandle());
    }
    return Void();
}

Return<Status> CameraDevice::autoFocus() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->auto_focus) {
        return getHidlStatus(mDevice->ops->auto_focus(mDevice));
    }
    return Status::ILLEGAL_ARGUMENT;
}

Return<Status> CameraDevice::cancelAutoFocus() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->cancel_auto_focus) {
        return getHidlStatus(mDevice->ops->cancel_auto_focus(mDevice));
    }
    return Status::ILLEGAL_ARGUMENT;
}

Return<Status> CameraDevice::takePicture() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->take_picture) {
        return getHidlStatus(mDevice->ops->take_picture(mDevice));
    }
    return Status::ILLEGAL_ARGUMENT;
}

Return<Status> CameraDevice::cancelPicture() {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->cancel_picture) {
        return getHidlStatus(mDevice->ops->cancel_picture(mDevice));
    }
    return Status::ILLEGAL_ARGUMENT;
}

Return<Status> CameraDevice::setParameters(const hidl_string& params) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->set_parameters) {
        return getHidlStatus(mDevice->ops->set_parameters(mDevice, params.c_str()));
    }
    return Status::ILLEGAL_ARGUMENT;
}

Return<void> CameraDevice::getParameters(getParameters_cb _hidl_cb) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    hidl_string outStr;
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        _hidl_cb(outStr);
        return Void();
    }
    if (mDevice->ops->get_parameters) {
        char *temp = mDevice->ops->get_parameters(mDevice);
        outStr = temp;
        if (mDevice->ops->put_parameters) {
            mDevice->ops->put_parameters(mDevice, temp);
        } else {
            free(temp);
        }
    }
    _hidl_cb(outStr);
    return Void();
}

Return<Status> CameraDevice::sendCommand(CommandType cmd, int32_t arg1, int32_t arg2) {
    ALOGV("%s(%s)", __FUNCTION__, mCameraId.c_str());
    Mutex::Autolock _l(mLock);
    if (!mDevice) {
        ALOGE("%s called while camera is not opened", __FUNCTION__);
        return Status::OPERATION_NOT_SUPPORTED;
    }
    if (mDevice->ops->send_command) {
        return getHidlStatus(mDevice->ops->send_command(mDevice, (int32_t) cmd, arg1, arg2));
    }
    return Status::ILLEGAL_ARGUMENT;
}

Return<void> CameraDevice::close() {
    Mutex::Autolock _l(mLock);
    closeLocked();
    return Void();
}

void CameraDevice::closeLocked() {
    ALOGI("Closing camera %s", mCameraId.c_str());
    if(mDevice) {
        int rc = mDevice->common.close(&mDevice->common);
        if (rc != OK) {
            ALOGE("Could not close camera %s: %d", mCameraId.c_str(), rc);
        }
        mDevice = nullptr;
    }
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
