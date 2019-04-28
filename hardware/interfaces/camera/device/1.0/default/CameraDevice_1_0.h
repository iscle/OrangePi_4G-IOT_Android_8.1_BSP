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

#ifndef ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_CAMERADEVICE_H
#define ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_CAMERADEVICE_H

#include <unordered_map>
#include "utils/Mutex.h"
#include "utils/SortedVector.h"
#include "CameraModule.h"
#include "HandleImporter.h"

#include <android/hardware/camera/device/1.0/ICameraDevice.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <android/hidl/memory/1.0/IMemory.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V1_0 {
namespace implementation {

using ::android::hardware::camera::common::V1_0::CameraResourceCost;
using ::android::hardware::camera::common::V1_0::Status;
using ::android::hardware::camera::common::V1_0::TorchMode;
using ::android::hardware::camera::common::V1_0::helper::CameraModule;
using ::android::hardware::camera::common::V1_0::helper::HandleImporter;
using ::android::hardware::camera::device::V1_0::CameraInfo;
using ::android::hardware::camera::device::V1_0::CommandType;
using ::android::hardware::camera::device::V1_0::ICameraDevice;
using ::android::hardware::camera::device::V1_0::ICameraDeviceCallback;
using ::android::hardware::camera::device::V1_0::ICameraDevicePreviewCallback;
using ::android::hardware::camera::device::V1_0::MemoryId;
using ::android::hidl::allocator::V1_0::IAllocator;
using ::android::hidl::base::V1_0::IBase;
using ::android::hidl::memory::V1_0::IMemory;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct CameraDevice : public ICameraDevice {

    // Called by provider HAL. Provider HAL must ensure the uniqueness of
    // CameraDevice object per cameraId, or there could be multiple CameraDevice
    // trying to access the same physical camera.
    // Also, provider will have to keep track of all CameraDevice objects in
    // order to notify CameraDevice when the underlying camera is detached
    CameraDevice(sp<CameraModule> module,
                 const std::string& cameraId,
                 const SortedVector<std::pair<std::string, std::string>>& cameraDeviceNames);
    ~CameraDevice();

    // Caller must use this method to check if CameraDevice ctor failed
    bool isInitFailed() { return mInitFail; }
    // Used by provider HAL to signal external camera disconnected
    void setConnectionStatus(bool connected);

    // Methods from ::android::hardware::camera::device::V1_0::ICameraDevice follow.
    Return<void> getResourceCost(getResourceCost_cb _hidl_cb) override;
    Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) override;
    Return<Status> setTorchMode(TorchMode mode) override;
    Return<Status> dumpState(const hidl_handle& fd) override;
    Return<Status> open(const sp<ICameraDeviceCallback>& callback) override;
    Return<Status> setPreviewWindow(const sp<ICameraDevicePreviewCallback>& window) override;
    Return<void> enableMsgType(uint32_t msgType) override;
    Return<void> disableMsgType(uint32_t msgType) override;
    Return<bool> msgTypeEnabled(uint32_t msgType) override;
    Return<Status> startPreview() override;
    Return<void> stopPreview() override;
    Return<bool> previewEnabled() override;
    Return<Status> storeMetaDataInBuffers(bool enable) override;
    Return<Status> startRecording() override;
    Return<void> stopRecording() override;
    Return<bool> recordingEnabled() override;
    Return<void> releaseRecordingFrame(uint32_t memId, uint32_t bufferIndex) override;
    Return<void> releaseRecordingFrameHandle(
            uint32_t memId, uint32_t bufferIndex, const hidl_handle& frame) override;
    Return<void> releaseRecordingFrameHandleBatch(
            const hidl_vec<VideoFrameMessage>&) override;
    Return<Status> autoFocus() override;
    Return<Status> cancelAutoFocus() override;
    Return<Status> takePicture() override;
    Return<Status> cancelPicture() override;
    Return<Status> setParameters(const hidl_string& params) override;
    Return<void> getParameters(getParameters_cb _hidl_cb) override;
    Return<Status> sendCommand(CommandType cmd, int32_t arg1, int32_t arg2) override;
    Return<void> close() override;

private:
    struct CameraMemory : public camera_memory_t {
        MemoryId mId;
        CameraDevice* mDevice;
    };

    class CameraHeapMemory : public RefBase {
    public:
        CameraHeapMemory(int fd, size_t buf_size, uint_t num_buffers = 1);
        explicit CameraHeapMemory(
            sp<IAllocator> ashmemAllocator, size_t buf_size, uint_t num_buffers = 1);
        void commonInitialization();
        virtual ~CameraHeapMemory();

        size_t mBufSize;
        uint_t mNumBufs;

        // Shared memory related members
        hidl_memory      mHidlHeap;
        native_handle_t* mHidlHandle; // contains one shared memory FD
        void*            mHidlHeapMemData;
        sp<IMemory>      mHidlHeapMemory; // munmap happens in ~IMemory()

        CameraMemory handle;
    };
    sp<IAllocator> mAshmemAllocator;

    const sp<CameraModule> mModule;
    const std::string mCameraId;
    // const after ctor
    int   mCameraIdInt;
    int   mDeviceVersion;

    camera_device_t* mDevice = nullptr;

    void initHalPreviewWindow();
    struct CameraPreviewWindow : public preview_stream_ops {
        // Called when we expect buffer will be re-allocated
        void cleanUpCirculatingBuffers();

        Mutex mLock;
        sp<ICameraDevicePreviewCallback> mPreviewCallback = nullptr;
        std::unordered_map<uint64_t, buffer_handle_t> mCirculatingBuffers;
        std::unordered_map<buffer_handle_t*, uint64_t> mBufferIdMap;
    } mHalPreviewWindow;

    // gating access to mDevice, mInitFail, mDisconnected
    mutable Mutex mLock;

    bool  mInitFail = false;
    // Set by provider (when external camera is connected/disconnected)
    bool  mDisconnected;

    static HandleImporter sHandleImporter;

    const SortedVector<std::pair<std::string, std::string>>& mCameraDeviceNames;

    sp<ICameraDeviceCallback> mDeviceCallback = nullptr;

    mutable Mutex mMemoryMapLock; // gating access to mMemoryMap
                                  // must not hold mLock after this lock is acquired
    std::unordered_map<MemoryId, CameraHeapMemory*> mMemoryMap;

    bool mMetadataMode = false;

    mutable Mutex mBatchLock;
    // Start of protection scope for mBatchLock
    uint32_t mBatchSize = 0; // 0 for non-batch mode, set to other value to start batching
    int32_t mBatchMsgType;   // Maybe only allow DataCallbackMsg::VIDEO_FRAME?
    std::vector<HandleTimestampMessage> mInflightBatch;
    // End of protection scope for mBatchLock

    void handleCallbackTimestamp(
            nsecs_t timestamp, int32_t msg_type,
            MemoryId memId , unsigned index, native_handle_t* handle);
    void releaseRecordingFrameLocked(uint32_t memId, uint32_t bufferIndex, const native_handle_t*);

    // shared memory methods
    static camera_memory_t* sGetMemory(int fd, size_t buf_size, uint_t num_bufs, void *user);
    static void sPutMemory(camera_memory_t *data);

    // Device callback forwarding methods
    static void sNotifyCb(int32_t msg_type, int32_t ext1, int32_t ext2, void *user);
    static void sDataCb(int32_t msg_type, const camera_memory_t *data, unsigned int index,
                        camera_frame_metadata_t *metadata, void *user);
    static void sDataCbTimestamp(nsecs_t timestamp, int32_t msg_type,
                                    const camera_memory_t *data, unsigned index, void *user);

    // Preview window callback forwarding methods
    static int sDequeueBuffer(struct preview_stream_ops* w,
                              buffer_handle_t** buffer, int *stride);

    static int sLockBuffer(struct preview_stream_ops* w, buffer_handle_t* buffer);

    static int sEnqueueBuffer(struct preview_stream_ops* w, buffer_handle_t* buffer);

    static int sCancelBuffer(struct preview_stream_ops* w, buffer_handle_t* buffer);

    static int sSetBufferCount(struct preview_stream_ops* w, int count);

    static int sSetBuffersGeometry(struct preview_stream_ops* w,
                                   int width, int height, int format);

    static int sSetCrop(struct preview_stream_ops *w, int left, int top, int right, int bottom);

    static int sSetTimestamp(struct preview_stream_ops *w, int64_t timestamp);

    static int sSetUsage(struct preview_stream_ops* w, int usage);

    static int sSetSwapInterval(struct preview_stream_ops *w, int interval);

    static int sGetMinUndequeuedBufferCount(const struct preview_stream_ops *w, int *count);

    // convert conventional HAL status to HIDL Status
    static Status getHidlStatus(const int&);
    static status_t getStatusT(const Status& s);

    Status initStatus() const;
    void closeLocked();
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_CAMERADEVICE_H
