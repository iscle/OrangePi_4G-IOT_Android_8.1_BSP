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

#ifndef ANDROID_HARDWARE_CAMERA_DEVICE_V3_2_CAMERADEVICE3SESSION_H
#define ANDROID_HARDWARE_CAMERA_DEVICE_V3_2_CAMERADEVICE3SESSION_H

#include <android/hardware/camera/device/3.2/ICameraDevice.h>
#include <android/hardware/camera/device/3.2/ICameraDeviceSession.h>
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
namespace V3_2 {
namespace implementation {

using ::android::hardware::camera::device::V3_2::CaptureRequest;
using ::android::hardware::camera::device::V3_2::HalStreamConfiguration;
using ::android::hardware::camera::device::V3_2::StreamConfiguration;
using ::android::hardware::camera::device::V3_2::ICameraDeviceSession;
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

struct Camera3Stream;

/**
 * Function pointer types with C calling convention to
 * use for HAL callback functions.
 */
extern "C" {
    typedef void (callbacks_process_capture_result_t)(
        const struct camera3_callback_ops *,
        const camera3_capture_result_t *);

    typedef void (callbacks_notify_t)(
        const struct camera3_callback_ops *,
        const camera3_notify_msg_t *);
}

struct CameraDeviceSession : public virtual RefBase, protected camera3_callback_ops  {

    CameraDeviceSession(camera3_device_t*,
                        const camera_metadata_t* deviceInfo,
                        const sp<ICameraDeviceCallback>&);
    virtual ~CameraDeviceSession();
    // Call by CameraDevice to dump active device states
    void dumpState(const native_handle_t* fd);
    // Caller must use this method to check if CameraDeviceSession ctor failed
    bool isInitFailed() { return mInitFail; }
    // Used by CameraDevice to signal external camera disconnected
    void disconnect();
    bool isClosed();

    // Retrieve the HIDL interface, split into its own class to avoid inheritance issues when
    // dealing with minor version revs and simultaneous implementation and interface inheritance
    virtual sp<ICameraDeviceSession> getInterface() {
        return new TrampolineSessionInterface_3_2(this);
    }

protected:

    // Methods from ::android::hardware::camera::device::V3_2::ICameraDeviceSession follow

    Return<void> constructDefaultRequestSettings(
            RequestTemplate type,
            ICameraDeviceSession::constructDefaultRequestSettings_cb _hidl_cb);
    Return<void> configureStreams(
            const StreamConfiguration& requestedConfiguration,
            ICameraDeviceSession::configureStreams_cb _hidl_cb);
    Return<void> getCaptureRequestMetadataQueue(
        ICameraDeviceSession::getCaptureRequestMetadataQueue_cb _hidl_cb);
    Return<void> getCaptureResultMetadataQueue(
        ICameraDeviceSession::getCaptureResultMetadataQueue_cb _hidl_cb);
    Return<void> processCaptureRequest(
            const hidl_vec<CaptureRequest>& requests,
            const hidl_vec<BufferCache>& cachesToRemove,
            ICameraDeviceSession::processCaptureRequest_cb _hidl_cb);
    Return<Status> flush();
    Return<void> close();

protected:

    // protecting mClosed/mDisconnected/mInitFail
    mutable Mutex mStateLock;
    // device is closed either
    //    - closed by user
    //    - init failed
    //    - camera disconnected
    bool mClosed = false;

    // Set by CameraDevice (when external camera is disconnected)
    bool mDisconnected = false;

    struct AETriggerCancelOverride {
        bool applyAeLock;
        uint8_t aeLock;
        bool applyAePrecaptureTrigger;
        uint8_t aePrecaptureTrigger;
    };

    camera3_device_t* mDevice;
    uint32_t mDeviceVersion;
    bool mIsAELockAvailable;
    bool mDerivePostRawSensKey;
    uint32_t mNumPartialResults;
    // Stream ID -> Camera3Stream cache
    std::map<int, Camera3Stream> mStreamMap;

    mutable Mutex mInflightLock; // protecting mInflightBuffers and mCirculatingBuffers
    // (streamID, frameNumber) -> inflight buffer cache
    std::map<std::pair<int, uint32_t>, camera3_stream_buffer_t>  mInflightBuffers;

    // (frameNumber, AETriggerOverride) -> inflight request AETriggerOverrides
    std::map<uint32_t, AETriggerCancelOverride> mInflightAETriggerOverrides;
    ::android::hardware::camera::common::V1_0::helper::CameraMetadata mOverridenResult;
    std::map<uint32_t, bool> mInflightRawBoostPresent;
    ::android::hardware::camera::common::V1_0::helper::CameraMetadata mOverridenRequest;

    // buffers currently ciculating between HAL and camera service
    // key: bufferId sent via HIDL interface
    // value: imported buffer_handle_t
    // Buffer will be imported during process_capture_request and will be freed
    // when the its stream is deleted or camera device session is closed
    typedef std::unordered_map<uint64_t, buffer_handle_t> CirculatingBuffers;
    // Stream ID -> circulating buffers map
    std::map<int, CirculatingBuffers> mCirculatingBuffers;

    static HandleImporter sHandleImporter;

    bool mInitFail;
    bool mFirstRequest = false;

    common::V1_0::helper::CameraMetadata mDeviceInfo;

    using RequestMetadataQueue = MessageQueue<uint8_t, kSynchronizedReadWrite>;
    std::unique_ptr<RequestMetadataQueue> mRequestMetadataQueue;
    using ResultMetadataQueue = MessageQueue<uint8_t, kSynchronizedReadWrite>;
    std::shared_ptr<ResultMetadataQueue> mResultMetadataQueue;

    class ResultBatcher {
    public:
        ResultBatcher(const sp<ICameraDeviceCallback>& callback);
        void setNumPartialResults(uint32_t n);
        void setBatchedStreams(const std::vector<int>& streamsToBatch);
        void setResultMetadataQueue(std::shared_ptr<ResultMetadataQueue> q);

        void registerBatch(const hidl_vec<CaptureRequest>& requests);
        void notify(NotifyMsg& msg);
        void processCaptureResult(CaptureResult& result);

    private:
        struct InflightBatch {
            // Protect access to entire struct. Acquire this lock before read/write any data or
            // calling any methods. processCaptureResult and notify will compete for this lock
            // HIDL IPCs might be issued while the lock is held
            Mutex mLock;

            bool allDelivered() const;

            uint32_t mFirstFrame;
            uint32_t mLastFrame;
            uint32_t mBatchSize;

            bool mShutterDelivered = false;
            std::vector<NotifyMsg> mShutterMsgs;

            struct BufferBatch {
                BufferBatch(uint32_t batchSize) {
                    mBuffers.reserve(batchSize);
                }
                bool mDelivered = false;
                // This currently assumes every batched request will output to the batched stream
                // and since HAL must always send buffers in order, no frameNumber tracking is
                // needed
                std::vector<StreamBuffer> mBuffers;
            };
            // Stream ID -> VideoBatch
            std::unordered_map<int, BufferBatch> mBatchBufs;

            struct MetadataBatch {
                //                   (frameNumber, metadata)
                std::vector<std::pair<uint32_t, CameraMetadata>> mMds;
            };
            // Partial result IDs that has been delivered to framework
            uint32_t mNumPartialResults;
            uint32_t mPartialResultProgress = 0;
            // partialResult -> MetadataBatch
            std::map<uint32_t, MetadataBatch> mResultMds;

            // Set to true when batch is removed from mInflightBatches
            // processCaptureResult and notify must check this flag after acquiring mLock to make
            // sure this batch isn't removed while waiting for mLock
            bool mRemoved = false;
        };

        static const int NOT_BATCHED = -1;

        // Get the batch index and pointer to InflightBatch (nullptrt if the frame is not batched)
        // Caller must acquire the InflightBatch::mLock before accessing the InflightBatch
        // It's possible that the InflightBatch is removed from mInflightBatches before the
        // InflightBatch::mLock is acquired (most likely caused by an error notification), so
        // caller must check InflightBatch::mRemoved flag after the lock is acquried.
        // This method will hold ResultBatcher::mLock briefly
        std::pair<int, std::shared_ptr<InflightBatch>> getBatch(uint32_t frameNumber);

        // Check if the first batch in mInflightBatches is ready to be removed, and remove it if so
        // This method will hold ResultBatcher::mLock briefly
        void checkAndRemoveFirstBatch();

        // The following sendXXXX methods must be called while the InflightBatch::mLock is locked
        // HIDL IPC methods will be called during these methods.
        void sendBatchShutterCbsLocked(std::shared_ptr<InflightBatch> batch);
        // send buffers for all batched streams
        void sendBatchBuffersLocked(std::shared_ptr<InflightBatch> batch);
        // send buffers for specified streams
        void sendBatchBuffersLocked(
                std::shared_ptr<InflightBatch> batch, const std::vector<int>& streams);
        void sendBatchMetadataLocked(
                std::shared_ptr<InflightBatch> batch, uint32_t lastPartialResultIdx);
        // End of sendXXXX methods

        // helper methods
        void freeReleaseFences(hidl_vec<CaptureResult>&);
        void notifySingleMsg(NotifyMsg& msg);
        void processOneCaptureResult(CaptureResult& result);
        void invokeProcessCaptureResultCallback(hidl_vec<CaptureResult> &results, bool tryWriteFmq);

        // move/push function avoids "hidl_handle& operator=(hidl_handle&)", which clones native
        // handle
        void moveStreamBuffer(StreamBuffer&& src, StreamBuffer& dst);
        void pushStreamBuffer(StreamBuffer&& src, std::vector<StreamBuffer>& dst);

        // Protect access to mInflightBatches, mNumPartialResults and mStreamsToBatch
        // processCaptureRequest, processCaptureResult, notify will compete for this lock
        // Do NOT issue HIDL IPCs while holding this lock (except when HAL reports error)
        mutable Mutex mLock;
        std::deque<std::shared_ptr<InflightBatch>> mInflightBatches;
        uint32_t mNumPartialResults;
        std::vector<int> mStreamsToBatch;
        const sp<ICameraDeviceCallback> mCallback;
        std::shared_ptr<ResultMetadataQueue> mResultMetadataQueue;

        // Protect against invokeProcessCaptureResultCallback()
        Mutex mProcessCaptureResultLock;

    } mResultBatcher;

    std::vector<int> mVideoStreamIds;

    bool initialize();

    Status initStatus() const;

    // Validate and import request's input buffer and acquire fence
    Status importRequest(
            const CaptureRequest& request,
            hidl_vec<buffer_handle_t*>& allBufPtrs,
            hidl_vec<int>& allFences);

    static void cleanupInflightFences(
            hidl_vec<int>& allFences, size_t numFences);

    void cleanupBuffersLocked(int id);

    void updateBufferCaches(const hidl_vec<BufferCache>& cachesToRemove);

    android_dataspace mapToLegacyDataspace(
            android_dataspace dataSpace) const;

    bool handleAePrecaptureCancelRequestLocked(
            const camera3_capture_request_t &halRequest,
            android::hardware::camera::common::V1_0::helper::CameraMetadata *settings /*out*/,
            AETriggerCancelOverride *override /*out*/);

    void overrideResultForPrecaptureCancelLocked(
            const AETriggerCancelOverride &aeTriggerCancelOverride,
            ::android::hardware::camera::common::V1_0::helper::CameraMetadata *settings /*out*/);

    Status processOneCaptureRequest(const CaptureRequest& request);
    /**
     * Static callback forwarding methods from HAL to instance
     */
    static callbacks_process_capture_result_t sProcessCaptureResult;
    static callbacks_notify_t sNotify;

private:

    struct TrampolineSessionInterface_3_2 : public ICameraDeviceSession {
        TrampolineSessionInterface_3_2(sp<CameraDeviceSession> parent) :
                mParent(parent) {}

        virtual Return<void> constructDefaultRequestSettings(
                V3_2::RequestTemplate type,
                V3_2::ICameraDeviceSession::constructDefaultRequestSettings_cb _hidl_cb) override {
            return mParent->constructDefaultRequestSettings(type, _hidl_cb);
        }

        virtual Return<void> configureStreams(
                const V3_2::StreamConfiguration& requestedConfiguration,
                V3_2::ICameraDeviceSession::configureStreams_cb _hidl_cb) override {
            return mParent->configureStreams(requestedConfiguration, _hidl_cb);
        }

        virtual Return<void> processCaptureRequest(const hidl_vec<V3_2::CaptureRequest>& requests,
                const hidl_vec<V3_2::BufferCache>& cachesToRemove,
                V3_2::ICameraDeviceSession::processCaptureRequest_cb _hidl_cb) override {
            return mParent->processCaptureRequest(requests, cachesToRemove, _hidl_cb);
        }

        virtual Return<void> getCaptureRequestMetadataQueue(
                V3_2::ICameraDeviceSession::getCaptureRequestMetadataQueue_cb _hidl_cb) override  {
            return mParent->getCaptureRequestMetadataQueue(_hidl_cb);
        }

        virtual Return<void> getCaptureResultMetadataQueue(
                V3_2::ICameraDeviceSession::getCaptureResultMetadataQueue_cb _hidl_cb) override  {
            return mParent->getCaptureResultMetadataQueue(_hidl_cb);
        }

        virtual Return<Status> flush() override {
            return mParent->flush();
        }

        virtual Return<void> close() override {
            return mParent->close();
        }

    private:
        sp<CameraDeviceSession> mParent;
    };
};

}  // namespace implementation
}  // namespace V3_2
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CAMERA_DEVICE_V3_2_CAMERADEVICE3SESSION_H
