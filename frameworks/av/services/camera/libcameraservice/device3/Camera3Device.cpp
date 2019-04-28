/*
 * Copyright (C) 2013 The Android Open Source Project
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

#define LOG_TAG "Camera3-Device"
#define ATRACE_TAG ATRACE_TAG_CAMERA
//#define LOG_NDEBUG 0
//#define LOG_NNDEBUG 0  // Per-frame verbose logging

#ifdef LOG_NNDEBUG
#define ALOGVV(...) ALOGV(__VA_ARGS__)
#else
#define ALOGVV(...) ((void)0)
#endif

// Convenience macro for transient errors
#define CLOGE(fmt, ...) ALOGE("Camera %s: %s: " fmt, mId.string(), __FUNCTION__, \
            ##__VA_ARGS__)

// Convenience macros for transitioning to the error state
#define SET_ERR(fmt, ...) setErrorState(   \
    "%s: " fmt, __FUNCTION__,              \
    ##__VA_ARGS__)
#define SET_ERR_L(fmt, ...) setErrorStateLocked( \
    "%s: " fmt, __FUNCTION__,                    \
    ##__VA_ARGS__)

#include <inttypes.h>

#include <utils/Log.h>
#include <utils/Trace.h>
#include <utils/Timers.h>
#include <cutils/properties.h>

#include <android/hardware/camera2/ICameraDeviceUser.h>

#include "utils/CameraTraces.h"
#include "mediautils/SchedulingPolicyService.h"
#include "device3/Camera3Device.h"
#include "device3/Camera3OutputStream.h"
#include "device3/Camera3InputStream.h"
#include "device3/Camera3DummyStream.h"
#include "device3/Camera3SharedOutputStream.h"
#include "CameraService.h"

using namespace android::camera3;
using namespace android::hardware::camera;
using namespace android::hardware::camera::device::V3_2;

namespace android {

Camera3Device::Camera3Device(const String8 &id):
        mId(id),
        mOperatingMode(NO_MODE),
        mIsConstrainedHighSpeedConfiguration(false),
        mStatus(STATUS_UNINITIALIZED),
        mStatusWaiters(0),
        mUsePartialResult(false),
        mNumPartialResults(1),
        mTimestampOffset(0),
        mNextResultFrameNumber(0),
        mNextReprocessResultFrameNumber(0),
        mNextShutterFrameNumber(0),
        mNextReprocessShutterFrameNumber(0),
        mListener(NULL),
        mVendorTagId(CAMERA_METADATA_INVALID_VENDOR_ID)
{
    ATRACE_CALL();
    camera3_callback_ops::notify = &sNotify;
    camera3_callback_ops::process_capture_result = &sProcessCaptureResult;
    ALOGV("%s: Created device for camera %s", __FUNCTION__, mId.string());
}

Camera3Device::~Camera3Device()
{
    ATRACE_CALL();
    ALOGV("%s: Tearing down for camera id %s", __FUNCTION__, mId.string());
    disconnect();
}

const String8& Camera3Device::getId() const {
    return mId;
}

status_t Camera3Device::initialize(sp<CameraProviderManager> manager) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    ALOGV("%s: Initializing HIDL device for camera %s", __FUNCTION__, mId.string());
    if (mStatus != STATUS_UNINITIALIZED) {
        CLOGE("Already initialized!");
        return INVALID_OPERATION;
    }
    if (manager == nullptr) return INVALID_OPERATION;

    sp<ICameraDeviceSession> session;
    ATRACE_BEGIN("CameraHal::openSession");
    status_t res = manager->openSession(mId.string(), this,
            /*out*/ &session);
    ATRACE_END();
    if (res != OK) {
        SET_ERR_L("Could not open camera session: %s (%d)", strerror(-res), res);
        return res;
    }

    res = manager->getCameraCharacteristics(mId.string(), &mDeviceInfo);
    if (res != OK) {
        SET_ERR_L("Could not retrive camera characteristics: %s (%d)", strerror(-res), res);
        session->close();
        return res;
    }

    std::shared_ptr<RequestMetadataQueue> queue;
    auto requestQueueRet = session->getCaptureRequestMetadataQueue(
        [&queue](const auto& descriptor) {
            queue = std::make_shared<RequestMetadataQueue>(descriptor);
            if (!queue->isValid() || queue->availableToWrite() <= 0) {
                ALOGE("HAL returns empty request metadata fmq, not use it");
                queue = nullptr;
                // don't use the queue onwards.
            }
        });
    if (!requestQueueRet.isOk()) {
        ALOGE("Transaction error when getting request metadata fmq: %s, not use it",
                requestQueueRet.description().c_str());
        return DEAD_OBJECT;
    }

    std::unique_ptr<ResultMetadataQueue>& resQueue = mResultMetadataQueue;
    auto resultQueueRet = session->getCaptureResultMetadataQueue(
        [&resQueue](const auto& descriptor) {
            resQueue = std::make_unique<ResultMetadataQueue>(descriptor);
            if (!resQueue->isValid() || resQueue->availableToWrite() <= 0) {
                ALOGE("HAL returns empty result metadata fmq, not use it");
                resQueue = nullptr;
                // Don't use the resQueue onwards.
            }
        });
    if (!resultQueueRet.isOk()) {
        ALOGE("Transaction error when getting result metadata queue from camera session: %s",
                resultQueueRet.description().c_str());
        return DEAD_OBJECT;
    }
    IF_ALOGV() {
        session->interfaceChain([](
            ::android::hardware::hidl_vec<::android::hardware::hidl_string> interfaceChain) {
                ALOGV("Session interface chain:");
                for (auto iface : interfaceChain) {
                    ALOGV("  %s", iface.c_str());
                }
            });
    }

    mInterface = new HalInterface(session, queue);
    std::string providerType;
    mVendorTagId = manager->getProviderTagIdLocked(mId.string());

    return initializeCommonLocked();
}

status_t Camera3Device::initializeCommonLocked() {

    /** Start up status tracker thread */
    mStatusTracker = new StatusTracker(this);
    status_t res = mStatusTracker->run(String8::format("C3Dev-%s-Status", mId.string()).string());
    if (res != OK) {
        SET_ERR_L("Unable to start status tracking thread: %s (%d)",
                strerror(-res), res);
        mInterface->close();
        mStatusTracker.clear();
        return res;
    }

    /** Register in-flight map to the status tracker */
    mInFlightStatusId = mStatusTracker->addComponent();

    /** Create buffer manager */
    mBufferManager = new Camera3BufferManager();

    mTagMonitor.initialize(mVendorTagId);

    /** Start up request queue thread */
    mRequestThread = new RequestThread(this, mStatusTracker, mInterface);
    res = mRequestThread->run(String8::format("C3Dev-%s-ReqQueue", mId.string()).string());
    if (res != OK) {
        SET_ERR_L("Unable to start request queue thread: %s (%d)",
                strerror(-res), res);
        mInterface->close();
        mRequestThread.clear();
        return res;
    }

    mPreparerThread = new PreparerThread();

    internalUpdateStatusLocked(STATUS_UNCONFIGURED);
    mNextStreamId = 0;
    mDummyStreamId = NO_STREAM;
    mNeedConfig = true;
    mPauseStateNotify = false;

    // Measure the clock domain offset between camera and video/hw_composer
    camera_metadata_entry timestampSource =
            mDeviceInfo.find(ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE);
    if (timestampSource.count > 0 && timestampSource.data.u8[0] ==
            ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
        mTimestampOffset = getMonoToBoottimeOffset();
    }

    // Will the HAL be sending in early partial result metadata?
    camera_metadata_entry partialResultsCount =
            mDeviceInfo.find(ANDROID_REQUEST_PARTIAL_RESULT_COUNT);
    if (partialResultsCount.count > 0) {
        mNumPartialResults = partialResultsCount.data.i32[0];
        mUsePartialResult = (mNumPartialResults > 1);
    }

    camera_metadata_entry configs =
            mDeviceInfo.find(ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS);
    for (uint32_t i = 0; i < configs.count; i += 4) {
        if (configs.data.i32[i] == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED &&
                configs.data.i32[i + 3] ==
                ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT) {
            mSupportedOpaqueInputSizes.add(Size(configs.data.i32[i + 1],
                    configs.data.i32[i + 2]));
        }
    }

    return OK;
}

status_t Camera3Device::disconnect() {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);

    ALOGI("%s: E", __FUNCTION__);

    status_t res = OK;
    std::vector<wp<Camera3StreamInterface>> streams;
    nsecs_t maxExpectedDuration = getExpectedInFlightDuration();
    {
        Mutex::Autolock l(mLock);
        if (mStatus == STATUS_UNINITIALIZED) return res;

        if (mStatus == STATUS_ACTIVE ||
                (mStatus == STATUS_ERROR && mRequestThread != NULL)) {
            res = mRequestThread->clearRepeatingRequests();
            if (res != OK) {
                SET_ERR_L("Can't stop streaming");
                // Continue to close device even in case of error
            } else {
                res = waitUntilStateThenRelock(/*active*/ false, maxExpectedDuration);
                if (res != OK) {
                    SET_ERR_L("Timeout waiting for HAL to drain (% " PRIi64 " ns)",
                            maxExpectedDuration);
                    // Continue to close device even in case of error
                }
            }
        }

        if (mStatus == STATUS_ERROR) {
            CLOGE("Shutting down in an error state");
        }

        if (mStatusTracker != NULL) {
            mStatusTracker->requestExit();
        }

        if (mRequestThread != NULL) {
            mRequestThread->requestExit();
        }

        streams.reserve(mOutputStreams.size() + (mInputStream != nullptr ? 1 : 0));
        for (size_t i = 0; i < mOutputStreams.size(); i++) {
            streams.push_back(mOutputStreams[i]);
        }
        if (mInputStream != nullptr) {
            streams.push_back(mInputStream);
        }
    }

    // Joining done without holding mLock, otherwise deadlocks may ensue
    // as the threads try to access parent state
    if (mRequestThread != NULL && mStatus != STATUS_ERROR) {
        // HAL may be in a bad state, so waiting for request thread
        // (which may be stuck in the HAL processCaptureRequest call)
        // could be dangerous.
        mRequestThread->join();
    }

    if (mStatusTracker != NULL) {
        mStatusTracker->join();
    }

    HalInterface* interface;
    {
        Mutex::Autolock l(mLock);
        mRequestThread.clear();
        mStatusTracker.clear();
        interface = mInterface.get();
    }

    // Call close without internal mutex held, as the HAL close may need to
    // wait on assorted callbacks,etc, to complete before it can return.
    interface->close();

    flushInflightRequests();

    {
        Mutex::Autolock l(mLock);
        mInterface->clear();
        mOutputStreams.clear();
        mInputStream.clear();
        mDeletedStreams.clear();
        mBufferManager.clear();
        internalUpdateStatusLocked(STATUS_UNINITIALIZED);
    }

    for (auto& weakStream : streams) {
        sp<Camera3StreamInterface> stream = weakStream.promote();
        if (stream != nullptr) {
            ALOGE("%s: Stream %d leaked! strong reference (%d)!",
                    __FUNCTION__, stream->getId(), stream->getStrongCount() - 1);
        }
    }

    ALOGI("%s: X", __FUNCTION__);
    return res;
}

// For dumping/debugging only -
// try to acquire a lock a few times, eventually give up to proceed with
// debug/dump operations
bool Camera3Device::tryLockSpinRightRound(Mutex& lock) {
    bool gotLock = false;
    for (size_t i = 0; i < kDumpLockAttempts; ++i) {
        if (lock.tryLock() == NO_ERROR) {
            gotLock = true;
            break;
        } else {
            usleep(kDumpSleepDuration);
        }
    }
    return gotLock;
}

Camera3Device::Size Camera3Device::getMaxJpegResolution() const {
    int32_t maxJpegWidth = 0, maxJpegHeight = 0;
    const int STREAM_CONFIGURATION_SIZE = 4;
    const int STREAM_FORMAT_OFFSET = 0;
    const int STREAM_WIDTH_OFFSET = 1;
    const int STREAM_HEIGHT_OFFSET = 2;
    const int STREAM_IS_INPUT_OFFSET = 3;
    camera_metadata_ro_entry_t availableStreamConfigs =
            mDeviceInfo.find(ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS);
    if (availableStreamConfigs.count == 0 ||
            availableStreamConfigs.count % STREAM_CONFIGURATION_SIZE != 0) {
        return Size(0, 0);
    }

    // Get max jpeg size (area-wise).
    for (size_t i=0; i < availableStreamConfigs.count; i+= STREAM_CONFIGURATION_SIZE) {
        int32_t format = availableStreamConfigs.data.i32[i + STREAM_FORMAT_OFFSET];
        int32_t width = availableStreamConfigs.data.i32[i + STREAM_WIDTH_OFFSET];
        int32_t height = availableStreamConfigs.data.i32[i + STREAM_HEIGHT_OFFSET];
        int32_t isInput = availableStreamConfigs.data.i32[i + STREAM_IS_INPUT_OFFSET];
        if (isInput == ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT
                && format == HAL_PIXEL_FORMAT_BLOB &&
                (width * height > maxJpegWidth * maxJpegHeight)) {
            maxJpegWidth = width;
            maxJpegHeight = height;
        }
    }

    return Size(maxJpegWidth, maxJpegHeight);
}

nsecs_t Camera3Device::getMonoToBoottimeOffset() {
    // try three times to get the clock offset, choose the one
    // with the minimum gap in measurements.
    const int tries = 3;
    nsecs_t bestGap, measured;
    for (int i = 0; i < tries; ++i) {
        const nsecs_t tmono = systemTime(SYSTEM_TIME_MONOTONIC);
        const nsecs_t tbase = systemTime(SYSTEM_TIME_BOOTTIME);
        const nsecs_t tmono2 = systemTime(SYSTEM_TIME_MONOTONIC);
        const nsecs_t gap = tmono2 - tmono;
        if (i == 0 || gap < bestGap) {
            bestGap = gap;
            measured = tbase - ((tmono + tmono2) >> 1);
        }
    }
    return measured;
}

hardware::graphics::common::V1_0::PixelFormat Camera3Device::mapToPixelFormat(
        int frameworkFormat) {
    return (hardware::graphics::common::V1_0::PixelFormat) frameworkFormat;
}

DataspaceFlags Camera3Device::mapToHidlDataspace(
        android_dataspace dataSpace) {
    return dataSpace;
}

BufferUsageFlags Camera3Device::mapToConsumerUsage(
        uint64_t usage) {
    return usage;
}

StreamRotation Camera3Device::mapToStreamRotation(camera3_stream_rotation_t rotation) {
    switch (rotation) {
        case CAMERA3_STREAM_ROTATION_0:
            return StreamRotation::ROTATION_0;
        case CAMERA3_STREAM_ROTATION_90:
            return StreamRotation::ROTATION_90;
        case CAMERA3_STREAM_ROTATION_180:
            return StreamRotation::ROTATION_180;
        case CAMERA3_STREAM_ROTATION_270:
            return StreamRotation::ROTATION_270;
    }
    ALOGE("%s: Unknown stream rotation %d", __FUNCTION__, rotation);
    return StreamRotation::ROTATION_0;
}

status_t Camera3Device::mapToStreamConfigurationMode(
        camera3_stream_configuration_mode_t operationMode, StreamConfigurationMode *mode) {
    if (mode == nullptr) return BAD_VALUE;
    if (operationMode < CAMERA3_VENDOR_STREAM_CONFIGURATION_MODE_START) {
        switch(operationMode) {
            case CAMERA3_STREAM_CONFIGURATION_NORMAL_MODE:
                *mode = StreamConfigurationMode::NORMAL_MODE;
                break;
            case CAMERA3_STREAM_CONFIGURATION_CONSTRAINED_HIGH_SPEED_MODE:
                *mode = StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE;
                break;
            default:
                ALOGE("%s: Unknown stream configuration mode %d", __FUNCTION__, operationMode);
                return BAD_VALUE;
        }
    } else {
        *mode = static_cast<StreamConfigurationMode>(operationMode);
    }
    return OK;
}

camera3_buffer_status_t Camera3Device::mapHidlBufferStatus(BufferStatus status) {
    switch (status) {
        case BufferStatus::OK: return CAMERA3_BUFFER_STATUS_OK;
        case BufferStatus::ERROR: return CAMERA3_BUFFER_STATUS_ERROR;
    }
    return CAMERA3_BUFFER_STATUS_ERROR;
}

int Camera3Device::mapToFrameworkFormat(
        hardware::graphics::common::V1_0::PixelFormat pixelFormat) {
    return static_cast<uint32_t>(pixelFormat);
}

android_dataspace Camera3Device::mapToFrameworkDataspace(
        DataspaceFlags dataSpace) {
    return static_cast<android_dataspace>(dataSpace);
}

uint64_t Camera3Device::mapConsumerToFrameworkUsage(
        BufferUsageFlags usage) {
    return usage;
}

uint64_t Camera3Device::mapProducerToFrameworkUsage(
        BufferUsageFlags usage) {
    return usage;
}

ssize_t Camera3Device::getJpegBufferSize(uint32_t width, uint32_t height) const {
    // Get max jpeg size (area-wise).
    Size maxJpegResolution = getMaxJpegResolution();
    if (maxJpegResolution.width == 0) {
        ALOGE("%s: Camera %s: Can't find valid available jpeg sizes in static metadata!",
                __FUNCTION__, mId.string());
        return BAD_VALUE;
    }

    // Get max jpeg buffer size
    ssize_t maxJpegBufferSize = 0;
    camera_metadata_ro_entry jpegBufMaxSize = mDeviceInfo.find(ANDROID_JPEG_MAX_SIZE);
    if (jpegBufMaxSize.count == 0) {
        ALOGE("%s: Camera %s: Can't find maximum JPEG size in static metadata!", __FUNCTION__,
                mId.string());
        return BAD_VALUE;
    }
    maxJpegBufferSize = jpegBufMaxSize.data.i32[0];
    assert(kMinJpegBufferSize < maxJpegBufferSize);

    // Calculate final jpeg buffer size for the given resolution.
    float scaleFactor = ((float) (width * height)) /
            (maxJpegResolution.width * maxJpegResolution.height);
    ssize_t jpegBufferSize = scaleFactor * (maxJpegBufferSize - kMinJpegBufferSize) +
            kMinJpegBufferSize;
    if (jpegBufferSize > maxJpegBufferSize) {
        jpegBufferSize = maxJpegBufferSize;
    }

    return jpegBufferSize;
}

ssize_t Camera3Device::getPointCloudBufferSize() const {
    const int FLOATS_PER_POINT=4;
    camera_metadata_ro_entry maxPointCount = mDeviceInfo.find(ANDROID_DEPTH_MAX_DEPTH_SAMPLES);
    if (maxPointCount.count == 0) {
        ALOGE("%s: Camera %s: Can't find maximum depth point cloud size in static metadata!",
                __FUNCTION__, mId.string());
        return BAD_VALUE;
    }
    ssize_t maxBytesForPointCloud = sizeof(android_depth_points) +
            maxPointCount.data.i32[0] * sizeof(float) * FLOATS_PER_POINT;
    return maxBytesForPointCloud;
}

ssize_t Camera3Device::getRawOpaqueBufferSize(int32_t width, int32_t height) const {
    const int PER_CONFIGURATION_SIZE = 3;
    const int WIDTH_OFFSET = 0;
    const int HEIGHT_OFFSET = 1;
    const int SIZE_OFFSET = 2;
    camera_metadata_ro_entry rawOpaqueSizes =
        mDeviceInfo.find(ANDROID_SENSOR_OPAQUE_RAW_SIZE);
    size_t count = rawOpaqueSizes.count;
    if (count == 0 || (count % PER_CONFIGURATION_SIZE)) {
        ALOGE("%s: Camera %s: bad opaque RAW size static metadata length(%zu)!",
                __FUNCTION__, mId.string(), count);
        return BAD_VALUE;
    }

    for (size_t i = 0; i < count; i += PER_CONFIGURATION_SIZE) {
        if (width == rawOpaqueSizes.data.i32[i + WIDTH_OFFSET] &&
                height == rawOpaqueSizes.data.i32[i + HEIGHT_OFFSET]) {
            return rawOpaqueSizes.data.i32[i + SIZE_OFFSET];
        }
    }

    ALOGE("%s: Camera %s: cannot find size for %dx%d opaque RAW image!",
            __FUNCTION__, mId.string(), width, height);
    return BAD_VALUE;
}

status_t Camera3Device::dump(int fd, const Vector<String16> &args) {
    ATRACE_CALL();
    (void)args;

    // Try to lock, but continue in case of failure (to avoid blocking in
    // deadlocks)
    bool gotInterfaceLock = tryLockSpinRightRound(mInterfaceLock);
    bool gotLock = tryLockSpinRightRound(mLock);

    ALOGW_IF(!gotInterfaceLock,
            "Camera %s: %s: Unable to lock interface lock, proceeding anyway",
            mId.string(), __FUNCTION__);
    ALOGW_IF(!gotLock,
            "Camera %s: %s: Unable to lock main lock, proceeding anyway",
            mId.string(), __FUNCTION__);

    bool dumpTemplates = false;

    String16 templatesOption("-t");
    String16 monitorOption("-m");
    int n = args.size();
    for (int i = 0; i < n; i++) {
        if (args[i] == templatesOption) {
            dumpTemplates = true;
        }
        if (args[i] == monitorOption) {
            if (i + 1 < n) {
                String8 monitorTags = String8(args[i + 1]);
                if (monitorTags == "off") {
                    mTagMonitor.disableMonitoring();
                } else {
                    mTagMonitor.parseTagsToMonitor(monitorTags);
                }
            } else {
                mTagMonitor.disableMonitoring();
            }
        }
    }

    String8 lines;

    const char *status =
            mStatus == STATUS_ERROR         ? "ERROR" :
            mStatus == STATUS_UNINITIALIZED ? "UNINITIALIZED" :
            mStatus == STATUS_UNCONFIGURED  ? "UNCONFIGURED" :
            mStatus == STATUS_CONFIGURED    ? "CONFIGURED" :
            mStatus == STATUS_ACTIVE        ? "ACTIVE" :
            "Unknown";

    lines.appendFormat("    Device status: %s\n", status);
    if (mStatus == STATUS_ERROR) {
        lines.appendFormat("    Error cause: %s\n", mErrorCause.string());
    }
    lines.appendFormat("    Stream configuration:\n");
    const char *mode =
            mOperatingMode == static_cast<int>(StreamConfigurationMode::NORMAL_MODE) ? "NORMAL" :
            mOperatingMode == static_cast<int>(
                StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE) ? "CONSTRAINED_HIGH_SPEED" :
            "CUSTOM";
    lines.appendFormat("    Operation mode: %s (%d) \n", mode, mOperatingMode);

    if (mInputStream != NULL) {
        write(fd, lines.string(), lines.size());
        mInputStream->dump(fd, args);
    } else {
        lines.appendFormat("      No input stream.\n");
        write(fd, lines.string(), lines.size());
    }
    for (size_t i = 0; i < mOutputStreams.size(); i++) {
        mOutputStreams[i]->dump(fd,args);
    }

    if (mBufferManager != NULL) {
        lines = String8("    Camera3 Buffer Manager:\n");
        write(fd, lines.string(), lines.size());
        mBufferManager->dump(fd, args);
    }

    lines = String8("    In-flight requests:\n");
    if (mInFlightMap.size() == 0) {
        lines.append("      None\n");
    } else {
        for (size_t i = 0; i < mInFlightMap.size(); i++) {
            InFlightRequest r = mInFlightMap.valueAt(i);
            lines.appendFormat("      Frame %d |  Timestamp: %" PRId64 ", metadata"
                    " arrived: %s, buffers left: %d\n", mInFlightMap.keyAt(i),
                    r.shutterTimestamp, r.haveResultMetadata ? "true" : "false",
                    r.numBuffersLeft);
        }
    }
    write(fd, lines.string(), lines.size());

    if (mRequestThread != NULL) {
        mRequestThread->dumpCaptureRequestLatency(fd,
                "    ProcessCaptureRequest latency histogram:");
    }

    {
        lines = String8("    Last request sent:\n");
        write(fd, lines.string(), lines.size());

        CameraMetadata lastRequest = getLatestRequestLocked();
        lastRequest.dump(fd, /*verbosity*/2, /*indentation*/6);
    }

    if (dumpTemplates) {
        const char *templateNames[] = {
            "TEMPLATE_PREVIEW",
            "TEMPLATE_STILL_CAPTURE",
            "TEMPLATE_VIDEO_RECORD",
            "TEMPLATE_VIDEO_SNAPSHOT",
            "TEMPLATE_ZERO_SHUTTER_LAG",
            "TEMPLATE_MANUAL"
        };

        for (int i = 1; i < CAMERA3_TEMPLATE_COUNT; i++) {
            camera_metadata_t *templateRequest = nullptr;
            mInterface->constructDefaultRequestSettings(
                    (camera3_request_template_t) i, &templateRequest);
            lines = String8::format("    HAL Request %s:\n", templateNames[i-1]);
            if (templateRequest == nullptr) {
                lines.append("       Not supported\n");
                write(fd, lines.string(), lines.size());
            } else {
                write(fd, lines.string(), lines.size());
                dump_indented_camera_metadata(templateRequest,
                        fd, /*verbosity*/2, /*indentation*/8);
            }
            free_camera_metadata(templateRequest);
        }
    }

    mTagMonitor.dumpMonitoredMetadata(fd);

    if (mInterface->valid()) {
        lines = String8("     HAL device dump:\n");
        write(fd, lines.string(), lines.size());
        mInterface->dump(fd);
    }

    if (gotLock) mLock.unlock();
    if (gotInterfaceLock) mInterfaceLock.unlock();

    return OK;
}

const CameraMetadata& Camera3Device::info() const {
    ALOGVV("%s: E", __FUNCTION__);
    if (CC_UNLIKELY(mStatus == STATUS_UNINITIALIZED ||
                    mStatus == STATUS_ERROR)) {
        ALOGW("%s: Access to static info %s!", __FUNCTION__,
                mStatus == STATUS_ERROR ?
                "when in error state" : "before init");
    }
    return mDeviceInfo;
}

status_t Camera3Device::checkStatusOkToCaptureLocked() {
    switch (mStatus) {
        case STATUS_ERROR:
            CLOGE("Device has encountered a serious error");
            return INVALID_OPERATION;
        case STATUS_UNINITIALIZED:
            CLOGE("Device not initialized");
            return INVALID_OPERATION;
        case STATUS_UNCONFIGURED:
        case STATUS_CONFIGURED:
        case STATUS_ACTIVE:
            // OK
            break;
        default:
            SET_ERR_L("Unexpected status: %d", mStatus);
            return INVALID_OPERATION;
    }
    return OK;
}

status_t Camera3Device::convertMetadataListToRequestListLocked(
        const List<const CameraMetadata> &metadataList,
        const std::list<const SurfaceMap> &surfaceMaps,
        bool repeating,
        RequestList *requestList) {
    if (requestList == NULL) {
        CLOGE("requestList cannot be NULL.");
        return BAD_VALUE;
    }

    int32_t burstId = 0;
    List<const CameraMetadata>::const_iterator metadataIt = metadataList.begin();
    std::list<const SurfaceMap>::const_iterator surfaceMapIt = surfaceMaps.begin();
    for (; metadataIt != metadataList.end() && surfaceMapIt != surfaceMaps.end();
            ++metadataIt, ++surfaceMapIt) {
        sp<CaptureRequest> newRequest = setUpRequestLocked(*metadataIt, *surfaceMapIt);
        if (newRequest == 0) {
            CLOGE("Can't create capture request");
            return BAD_VALUE;
        }

        newRequest->mRepeating = repeating;

        // Setup burst Id and request Id
        newRequest->mResultExtras.burstId = burstId++;
        if (metadataIt->exists(ANDROID_REQUEST_ID)) {
            if (metadataIt->find(ANDROID_REQUEST_ID).count == 0) {
                CLOGE("RequestID entry exists; but must not be empty in metadata");
                return BAD_VALUE;
            }
            newRequest->mResultExtras.requestId = metadataIt->find(ANDROID_REQUEST_ID).data.i32[0];
        } else {
            CLOGE("RequestID does not exist in metadata");
            return BAD_VALUE;
        }

        requestList->push_back(newRequest);

        ALOGV("%s: requestId = %" PRId32, __FUNCTION__, newRequest->mResultExtras.requestId);
    }
    if (metadataIt != metadataList.end() || surfaceMapIt != surfaceMaps.end()) {
        ALOGE("%s: metadataList and surfaceMaps are not the same size!", __FUNCTION__);
        return BAD_VALUE;
    }

    // Setup batch size if this is a high speed video recording request.
    if (mIsConstrainedHighSpeedConfiguration && requestList->size() > 0) {
        auto firstRequest = requestList->begin();
        for (auto& outputStream : (*firstRequest)->mOutputStreams) {
            if (outputStream->isVideoStream()) {
                (*firstRequest)->mBatchSize = requestList->size();
                break;
            }
        }
    }

    return OK;
}

status_t Camera3Device::capture(CameraMetadata &request, int64_t* /*lastFrameNumber*/) {
    ATRACE_CALL();

    List<const CameraMetadata> requests;
    std::list<const SurfaceMap> surfaceMaps;
    convertToRequestList(requests, surfaceMaps, request);

    return captureList(requests, surfaceMaps, /*lastFrameNumber*/NULL);
}

void Camera3Device::convertToRequestList(List<const CameraMetadata>& requests,
        std::list<const SurfaceMap>& surfaceMaps,
        const CameraMetadata& request) {
    requests.push_back(request);

    SurfaceMap surfaceMap;
    camera_metadata_ro_entry streams = request.find(ANDROID_REQUEST_OUTPUT_STREAMS);
    // With no surface list passed in, stream and surface will have 1-to-1
    // mapping. So the surface index is 0 for each stream in the surfaceMap.
    for (size_t i = 0; i < streams.count; i++) {
        surfaceMap[streams.data.i32[i]].push_back(0);
    }
    surfaceMaps.push_back(surfaceMap);
}

status_t Camera3Device::submitRequestsHelper(
        const List<const CameraMetadata> &requests,
        const std::list<const SurfaceMap> &surfaceMaps,
        bool repeating,
        /*out*/
        int64_t *lastFrameNumber) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    status_t res = checkStatusOkToCaptureLocked();
    if (res != OK) {
        // error logged by previous call
        return res;
    }

    RequestList requestList;

    res = convertMetadataListToRequestListLocked(requests, surfaceMaps,
            repeating, /*out*/&requestList);
    if (res != OK) {
        // error logged by previous call
        return res;
    }

    if (repeating) {
        res = mRequestThread->setRepeatingRequests(requestList, lastFrameNumber);
    } else {
        res = mRequestThread->queueRequestList(requestList, lastFrameNumber);
    }

    if (res == OK) {
        waitUntilStateThenRelock(/*active*/true, kActiveTimeout);
        if (res != OK) {
            SET_ERR_L("Can't transition to active in %f seconds!",
                    kActiveTimeout/1e9);
        }
        ALOGV("Camera %s: Capture request %" PRId32 " enqueued", mId.string(),
              (*(requestList.begin()))->mResultExtras.requestId);
    } else {
        CLOGE("Cannot queue request. Impossible.");
        return BAD_VALUE;
    }

    return res;
}

// Only one processCaptureResult should be called at a time, so
// the locks won't block. The locks are present here simply to enforce this.
hardware::Return<void> Camera3Device::processCaptureResult(
        const hardware::hidl_vec<
                hardware::camera::device::V3_2::CaptureResult>& results) {
    // Ideally we should grab mLock, but that can lead to deadlock, and
    // it's not super important to get up to date value of mStatus for this
    // warning print, hence skipping the lock here
    if (mStatus == STATUS_ERROR) {
        // Per API contract, HAL should act as closed after device error
        // But mStatus can be set to error by framework as well, so just log
        // a warning here.
        ALOGW("%s: received capture result in error state.", __FUNCTION__);
    }

    if (mProcessCaptureResultLock.tryLock() != OK) {
        // This should never happen; it indicates a wrong client implementation
        // that doesn't follow the contract. But, we can be tolerant here.
        ALOGE("%s: callback overlapped! waiting 1s...",
                __FUNCTION__);
        if (mProcessCaptureResultLock.timedLock(1000000000 /* 1s */) != OK) {
            ALOGE("%s: cannot acquire lock in 1s, dropping results",
                    __FUNCTION__);
            // really don't know what to do, so bail out.
            return hardware::Void();
        }
    }
    for (const auto& result : results) {
        processOneCaptureResultLocked(result);
    }
    mProcessCaptureResultLock.unlock();
    return hardware::Void();
}

void Camera3Device::processOneCaptureResultLocked(
        const hardware::camera::device::V3_2::CaptureResult& result) {
    camera3_capture_result r;
    status_t res;
    r.frame_number = result.frameNumber;

    hardware::camera::device::V3_2::CameraMetadata resultMetadata;
    if (result.fmqResultSize > 0) {
        resultMetadata.resize(result.fmqResultSize);
        if (mResultMetadataQueue == nullptr) {
            return; // logged in initialize()
        }
        if (!mResultMetadataQueue->read(resultMetadata.data(), result.fmqResultSize)) {
            ALOGE("%s: Frame %d: Cannot read camera metadata from fmq, size = %" PRIu64,
                    __FUNCTION__, result.frameNumber, result.fmqResultSize);
            return;
        }
    } else {
        resultMetadata.setToExternal(const_cast<uint8_t *>(result.result.data()),
                result.result.size());
    }

    if (resultMetadata.size() != 0) {
        r.result = reinterpret_cast<const camera_metadata_t*>(resultMetadata.data());
        size_t expected_metadata_size = resultMetadata.size();
        if ((res = validate_camera_metadata_structure(r.result, &expected_metadata_size)) != OK) {
            ALOGE("%s: Frame %d: Invalid camera metadata received by camera service from HAL: %s (%d)",
                    __FUNCTION__, result.frameNumber, strerror(-res), res);
            return;
        }
    } else {
        r.result = nullptr;
    }

    std::vector<camera3_stream_buffer_t> outputBuffers(result.outputBuffers.size());
    std::vector<buffer_handle_t> outputBufferHandles(result.outputBuffers.size());
    for (size_t i = 0; i < result.outputBuffers.size(); i++) {
        auto& bDst = outputBuffers[i];
        const StreamBuffer &bSrc = result.outputBuffers[i];

        ssize_t idx = mOutputStreams.indexOfKey(bSrc.streamId);
        if (idx == NAME_NOT_FOUND) {
            ALOGE("%s: Frame %d: Buffer %zu: Invalid output stream id %d",
                    __FUNCTION__, result.frameNumber, i, bSrc.streamId);
            return;
        }
        bDst.stream = mOutputStreams.valueAt(idx)->asHalStream();

        buffer_handle_t *buffer;
        res = mInterface->popInflightBuffer(result.frameNumber, bSrc.streamId, &buffer);
        if (res != OK) {
            ALOGE("%s: Frame %d: Buffer %zu: No in-flight buffer for stream %d",
                    __FUNCTION__, result.frameNumber, i, bSrc.streamId);
            return;
        }
        bDst.buffer = buffer;
        bDst.status = mapHidlBufferStatus(bSrc.status);
        bDst.acquire_fence = -1;
        if (bSrc.releaseFence == nullptr) {
            bDst.release_fence = -1;
        } else if (bSrc.releaseFence->numFds == 1) {
            bDst.release_fence = dup(bSrc.releaseFence->data[0]);
        } else {
            ALOGE("%s: Frame %d: Invalid release fence for buffer %zu, fd count is %d, not 1",
                    __FUNCTION__, result.frameNumber, i, bSrc.releaseFence->numFds);
            return;
        }
    }
    r.num_output_buffers = outputBuffers.size();
    r.output_buffers = outputBuffers.data();

    camera3_stream_buffer_t inputBuffer;
    if (result.inputBuffer.streamId == -1) {
        r.input_buffer = nullptr;
    } else {
        if (mInputStream->getId() != result.inputBuffer.streamId) {
            ALOGE("%s: Frame %d: Invalid input stream id %d", __FUNCTION__,
                    result.frameNumber, result.inputBuffer.streamId);
            return;
        }
        inputBuffer.stream = mInputStream->asHalStream();
        buffer_handle_t *buffer;
        res = mInterface->popInflightBuffer(result.frameNumber, result.inputBuffer.streamId,
                &buffer);
        if (res != OK) {
            ALOGE("%s: Frame %d: Input buffer: No in-flight buffer for stream %d",
                    __FUNCTION__, result.frameNumber, result.inputBuffer.streamId);
            return;
        }
        inputBuffer.buffer = buffer;
        inputBuffer.status = mapHidlBufferStatus(result.inputBuffer.status);
        inputBuffer.acquire_fence = -1;
        if (result.inputBuffer.releaseFence == nullptr) {
            inputBuffer.release_fence = -1;
        } else if (result.inputBuffer.releaseFence->numFds == 1) {
            inputBuffer.release_fence = dup(result.inputBuffer.releaseFence->data[0]);
        } else {
            ALOGE("%s: Frame %d: Invalid release fence for input buffer, fd count is %d, not 1",
                    __FUNCTION__, result.frameNumber, result.inputBuffer.releaseFence->numFds);
            return;
        }
        r.input_buffer = &inputBuffer;
    }

    r.partial_result = result.partialResult;

    processCaptureResult(&r);
}

hardware::Return<void> Camera3Device::notify(
        const hardware::hidl_vec<hardware::camera::device::V3_2::NotifyMsg>& msgs) {
    // Ideally we should grab mLock, but that can lead to deadlock, and
    // it's not super important to get up to date value of mStatus for this
    // warning print, hence skipping the lock here
    if (mStatus == STATUS_ERROR) {
        // Per API contract, HAL should act as closed after device error
        // But mStatus can be set to error by framework as well, so just log
        // a warning here.
        ALOGW("%s: received notify message in error state.", __FUNCTION__);
    }

    for (const auto& msg : msgs) {
        notify(msg);
    }
    return hardware::Void();
}

void Camera3Device::notify(
        const hardware::camera::device::V3_2::NotifyMsg& msg) {

    camera3_notify_msg m;
    switch (msg.type) {
        case MsgType::ERROR:
            m.type = CAMERA3_MSG_ERROR;
            m.message.error.frame_number = msg.msg.error.frameNumber;
            if (msg.msg.error.errorStreamId >= 0) {
                ssize_t idx = mOutputStreams.indexOfKey(msg.msg.error.errorStreamId);
                if (idx == NAME_NOT_FOUND) {
                    ALOGE("%s: Frame %d: Invalid error stream id %d",
                            __FUNCTION__, m.message.error.frame_number, msg.msg.error.errorStreamId);
                    return;
                }
                m.message.error.error_stream = mOutputStreams.valueAt(idx)->asHalStream();
            } else {
                m.message.error.error_stream = nullptr;
            }
            switch (msg.msg.error.errorCode) {
                case ErrorCode::ERROR_DEVICE:
                    m.message.error.error_code = CAMERA3_MSG_ERROR_DEVICE;
                    break;
                case ErrorCode::ERROR_REQUEST:
                    m.message.error.error_code = CAMERA3_MSG_ERROR_REQUEST;
                    break;
                case ErrorCode::ERROR_RESULT:
                    m.message.error.error_code = CAMERA3_MSG_ERROR_RESULT;
                    break;
                case ErrorCode::ERROR_BUFFER:
                    m.message.error.error_code = CAMERA3_MSG_ERROR_BUFFER;
                    break;
            }
            break;
        case MsgType::SHUTTER:
            m.type = CAMERA3_MSG_SHUTTER;
            m.message.shutter.frame_number = msg.msg.shutter.frameNumber;
            m.message.shutter.timestamp = msg.msg.shutter.timestamp;
            break;
    }
    notify(&m);
}

status_t Camera3Device::captureList(const List<const CameraMetadata> &requests,
                                    const std::list<const SurfaceMap> &surfaceMaps,
                                    int64_t *lastFrameNumber) {
    ATRACE_CALL();

    return submitRequestsHelper(requests, surfaceMaps, /*repeating*/false, lastFrameNumber);
}

status_t Camera3Device::setStreamingRequest(const CameraMetadata &request,
                                            int64_t* /*lastFrameNumber*/) {
    ATRACE_CALL();

    List<const CameraMetadata> requests;
    std::list<const SurfaceMap> surfaceMaps;
    convertToRequestList(requests, surfaceMaps, request);

    return setStreamingRequestList(requests, /*surfaceMap*/surfaceMaps,
                                   /*lastFrameNumber*/NULL);
}

status_t Camera3Device::setStreamingRequestList(const List<const CameraMetadata> &requests,
                                                const std::list<const SurfaceMap> &surfaceMaps,
                                                int64_t *lastFrameNumber) {
    ATRACE_CALL();

    return submitRequestsHelper(requests, surfaceMaps, /*repeating*/true, lastFrameNumber);
}

sp<Camera3Device::CaptureRequest> Camera3Device::setUpRequestLocked(
        const CameraMetadata &request, const SurfaceMap &surfaceMap) {
    status_t res;

    if (mStatus == STATUS_UNCONFIGURED || mNeedConfig) {
        // This point should only be reached via API1 (API2 must explicitly call configureStreams)
        // so unilaterally select normal operating mode.
        res = configureStreamsLocked(CAMERA3_STREAM_CONFIGURATION_NORMAL_MODE);
        // Stream configuration failed. Client might try other configuraitons.
        if (res != OK) {
            CLOGE("Can't set up streams: %s (%d)", strerror(-res), res);
            return NULL;
        } else if (mStatus == STATUS_UNCONFIGURED) {
            // Stream configuration successfully configure to empty stream configuration.
            CLOGE("No streams configured");
            return NULL;
        }
    }

    sp<CaptureRequest> newRequest = createCaptureRequest(request, surfaceMap);
    return newRequest;
}

status_t Camera3Device::clearStreamingRequest(int64_t *lastFrameNumber) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    switch (mStatus) {
        case STATUS_ERROR:
            CLOGE("Device has encountered a serious error");
            return INVALID_OPERATION;
        case STATUS_UNINITIALIZED:
            CLOGE("Device not initialized");
            return INVALID_OPERATION;
        case STATUS_UNCONFIGURED:
        case STATUS_CONFIGURED:
        case STATUS_ACTIVE:
            // OK
            break;
        default:
            SET_ERR_L("Unexpected status: %d", mStatus);
            return INVALID_OPERATION;
    }
    ALOGV("Camera %s: Clearing repeating request", mId.string());

    return mRequestThread->clearRepeatingRequests(lastFrameNumber);
}

status_t Camera3Device::waitUntilRequestReceived(int32_t requestId, nsecs_t timeout) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);

    return mRequestThread->waitUntilRequestProcessed(requestId, timeout);
}

status_t Camera3Device::createInputStream(
        uint32_t width, uint32_t height, int format, int *id) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    nsecs_t maxExpectedDuration = getExpectedInFlightDuration();
    Mutex::Autolock l(mLock);
    ALOGV("Camera %s: Creating new input stream %d: %d x %d, format %d",
            mId.string(), mNextStreamId, width, height, format);

    status_t res;
    bool wasActive = false;

    switch (mStatus) {
        case STATUS_ERROR:
            ALOGE("%s: Device has encountered a serious error", __FUNCTION__);
            return INVALID_OPERATION;
        case STATUS_UNINITIALIZED:
            ALOGE("%s: Device not initialized", __FUNCTION__);
            return INVALID_OPERATION;
        case STATUS_UNCONFIGURED:
        case STATUS_CONFIGURED:
            // OK
            break;
        case STATUS_ACTIVE:
            ALOGV("%s: Stopping activity to reconfigure streams", __FUNCTION__);
            res = internalPauseAndWaitLocked(maxExpectedDuration);
            if (res != OK) {
                SET_ERR_L("Can't pause captures to reconfigure streams!");
                return res;
            }
            wasActive = true;
            break;
        default:
            SET_ERR_L("%s: Unexpected status: %d", mStatus);
            return INVALID_OPERATION;
    }
    assert(mStatus != STATUS_ACTIVE);

    if (mInputStream != 0) {
        ALOGE("%s: Cannot create more than 1 input stream", __FUNCTION__);
        return INVALID_OPERATION;
    }

    sp<Camera3InputStream> newStream = new Camera3InputStream(mNextStreamId,
                width, height, format);
    newStream->setStatusTracker(mStatusTracker);

    mInputStream = newStream;

    *id = mNextStreamId++;

    // Continue captures if active at start
    if (wasActive) {
        ALOGV("%s: Restarting activity to reconfigure streams", __FUNCTION__);
        // Reuse current operating mode for new stream config
        res = configureStreamsLocked(mOperatingMode);
        if (res != OK) {
            ALOGE("%s: Can't reconfigure device for new stream %d: %s (%d)",
                    __FUNCTION__, mNextStreamId, strerror(-res), res);
            return res;
        }
        internalResumeLocked();
    }

    ALOGV("Camera %s: Created input stream", mId.string());
    return OK;
}

status_t Camera3Device::createStream(sp<Surface> consumer,
            uint32_t width, uint32_t height, int format,
            android_dataspace dataSpace, camera3_stream_rotation_t rotation, int *id,
            int streamSetId, bool isShared, uint64_t consumerUsage) {
    ATRACE_CALL();

    if (consumer == nullptr) {
        ALOGE("%s: consumer must not be null", __FUNCTION__);
        return BAD_VALUE;
    }

    std::vector<sp<Surface>> consumers;
    consumers.push_back(consumer);

    return createStream(consumers, /*hasDeferredConsumer*/ false, width, height,
            format, dataSpace, rotation, id, streamSetId, isShared, consumerUsage);
}

status_t Camera3Device::createStream(const std::vector<sp<Surface>>& consumers,
        bool hasDeferredConsumer, uint32_t width, uint32_t height, int format,
        android_dataspace dataSpace, camera3_stream_rotation_t rotation, int *id,
        int streamSetId, bool isShared, uint64_t consumerUsage) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    nsecs_t maxExpectedDuration = getExpectedInFlightDuration();
    Mutex::Autolock l(mLock);
    ALOGV("Camera %s: Creating new stream %d: %d x %d, format %d, dataspace %d rotation %d"
            " consumer usage %" PRIu64 ", isShared %d", mId.string(), mNextStreamId, width, height, format,
            dataSpace, rotation, consumerUsage, isShared);

    status_t res;
    bool wasActive = false;

    switch (mStatus) {
        case STATUS_ERROR:
            CLOGE("Device has encountered a serious error");
            return INVALID_OPERATION;
        case STATUS_UNINITIALIZED:
            CLOGE("Device not initialized");
            return INVALID_OPERATION;
        case STATUS_UNCONFIGURED:
        case STATUS_CONFIGURED:
            // OK
            break;
        case STATUS_ACTIVE:
            ALOGV("%s: Stopping activity to reconfigure streams", __FUNCTION__);
            res = internalPauseAndWaitLocked(maxExpectedDuration);
            if (res != OK) {
                SET_ERR_L("Can't pause captures to reconfigure streams!");
                return res;
            }
            wasActive = true;
            break;
        default:
            SET_ERR_L("Unexpected status: %d", mStatus);
            return INVALID_OPERATION;
    }
    assert(mStatus != STATUS_ACTIVE);

    sp<Camera3OutputStream> newStream;

    if (consumers.size() == 0 && !hasDeferredConsumer) {
        ALOGE("%s: Number of consumers cannot be smaller than 1", __FUNCTION__);
        return BAD_VALUE;
    }

    if (hasDeferredConsumer && format != HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
        ALOGE("Deferred consumer stream creation only support IMPLEMENTATION_DEFINED format");
        return BAD_VALUE;
    }

    if (format == HAL_PIXEL_FORMAT_BLOB) {
        ssize_t blobBufferSize;
        if (dataSpace != HAL_DATASPACE_DEPTH) {
            blobBufferSize = getJpegBufferSize(width, height);
            if (blobBufferSize <= 0) {
                SET_ERR_L("Invalid jpeg buffer size %zd", blobBufferSize);
                return BAD_VALUE;
            }
        } else {
            blobBufferSize = getPointCloudBufferSize();
            if (blobBufferSize <= 0) {
                SET_ERR_L("Invalid point cloud buffer size %zd", blobBufferSize);
                return BAD_VALUE;
            }
        }
        newStream = new Camera3OutputStream(mNextStreamId, consumers[0],
                width, height, blobBufferSize, format, dataSpace, rotation,
                mTimestampOffset, streamSetId);
    } else if (format == HAL_PIXEL_FORMAT_RAW_OPAQUE) {
        ssize_t rawOpaqueBufferSize = getRawOpaqueBufferSize(width, height);
        if (rawOpaqueBufferSize <= 0) {
            SET_ERR_L("Invalid RAW opaque buffer size %zd", rawOpaqueBufferSize);
            return BAD_VALUE;
        }
        newStream = new Camera3OutputStream(mNextStreamId, consumers[0],
                width, height, rawOpaqueBufferSize, format, dataSpace, rotation,
                mTimestampOffset, streamSetId);
    } else if (isShared) {
        newStream = new Camera3SharedOutputStream(mNextStreamId, consumers,
                width, height, format, consumerUsage, dataSpace, rotation,
                mTimestampOffset, streamSetId);
    } else if (consumers.size() == 0 && hasDeferredConsumer) {
        newStream = new Camera3OutputStream(mNextStreamId,
                width, height, format, consumerUsage, dataSpace, rotation,
                mTimestampOffset, streamSetId);
    } else {
        newStream = new Camera3OutputStream(mNextStreamId, consumers[0],
                width, height, format, dataSpace, rotation,
                mTimestampOffset, streamSetId);
    }
    newStream->setStatusTracker(mStatusTracker);

    newStream->setBufferManager(mBufferManager);

    res = mOutputStreams.add(mNextStreamId, newStream);
    if (res < 0) {
        SET_ERR_L("Can't add new stream to set: %s (%d)", strerror(-res), res);
        return res;
    }

    *id = mNextStreamId++;
    mNeedConfig = true;

    // Continue captures if active at start
    if (wasActive) {
        ALOGV("%s: Restarting activity to reconfigure streams", __FUNCTION__);
        // Reuse current operating mode for new stream config
        res = configureStreamsLocked(mOperatingMode);
        if (res != OK) {
            CLOGE("Can't reconfigure device for new stream %d: %s (%d)",
                    mNextStreamId, strerror(-res), res);
            return res;
        }
        internalResumeLocked();
    }
    ALOGV("Camera %s: Created new stream", mId.string());
    return OK;
}

status_t Camera3Device::getStreamInfo(int id, StreamInfo *streamInfo) {
    ATRACE_CALL();
    if (nullptr == streamInfo) {
        return BAD_VALUE;
    }
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    switch (mStatus) {
        case STATUS_ERROR:
            CLOGE("Device has encountered a serious error");
            return INVALID_OPERATION;
        case STATUS_UNINITIALIZED:
            CLOGE("Device not initialized!");
            return INVALID_OPERATION;
        case STATUS_UNCONFIGURED:
        case STATUS_CONFIGURED:
        case STATUS_ACTIVE:
            // OK
            break;
        default:
            SET_ERR_L("Unexpected status: %d", mStatus);
            return INVALID_OPERATION;
    }

    ssize_t idx = mOutputStreams.indexOfKey(id);
    if (idx == NAME_NOT_FOUND) {
        CLOGE("Stream %d is unknown", id);
        return idx;
    }

    streamInfo->width  = mOutputStreams[idx]->getWidth();
    streamInfo->height = mOutputStreams[idx]->getHeight();
    streamInfo->format = mOutputStreams[idx]->getFormat();
    streamInfo->dataSpace = mOutputStreams[idx]->getDataSpace();
    streamInfo->formatOverridden = mOutputStreams[idx]->isFormatOverridden();
    streamInfo->originalFormat = mOutputStreams[idx]->getOriginalFormat();
    streamInfo->dataSpaceOverridden = mOutputStreams[idx]->isDataSpaceOverridden();
    streamInfo->originalDataSpace = mOutputStreams[idx]->getOriginalDataSpace();
    return OK;
}

status_t Camera3Device::setStreamTransform(int id,
        int transform) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    switch (mStatus) {
        case STATUS_ERROR:
            CLOGE("Device has encountered a serious error");
            return INVALID_OPERATION;
        case STATUS_UNINITIALIZED:
            CLOGE("Device not initialized");
            return INVALID_OPERATION;
        case STATUS_UNCONFIGURED:
        case STATUS_CONFIGURED:
        case STATUS_ACTIVE:
            // OK
            break;
        default:
            SET_ERR_L("Unexpected status: %d", mStatus);
            return INVALID_OPERATION;
    }

    ssize_t idx = mOutputStreams.indexOfKey(id);
    if (idx == NAME_NOT_FOUND) {
        CLOGE("Stream %d does not exist",
                id);
        return BAD_VALUE;
    }

    return mOutputStreams.editValueAt(idx)->setTransform(transform);
}

status_t Camera3Device::deleteStream(int id) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);
    status_t res;

    ALOGV("%s: Camera %s: Deleting stream %d", __FUNCTION__, mId.string(), id);

    // CameraDevice semantics require device to already be idle before
    // deleteStream is called, unlike for createStream.
    if (mStatus == STATUS_ACTIVE) {
        ALOGV("%s: Camera %s: Device not idle", __FUNCTION__, mId.string());
        return -EBUSY;
    }

    if (mStatus == STATUS_ERROR) {
        ALOGW("%s: Camera %s: deleteStream not allowed in ERROR state",
                __FUNCTION__, mId.string());
        return -EBUSY;
    }

    sp<Camera3StreamInterface> deletedStream;
    ssize_t outputStreamIdx = mOutputStreams.indexOfKey(id);
    if (mInputStream != NULL && id == mInputStream->getId()) {
        deletedStream = mInputStream;
        mInputStream.clear();
    } else {
        if (outputStreamIdx == NAME_NOT_FOUND) {
            CLOGE("Stream %d does not exist", id);
            return BAD_VALUE;
        }
    }

    // Delete output stream or the output part of a bi-directional stream.
    if (outputStreamIdx != NAME_NOT_FOUND) {
        deletedStream = mOutputStreams.editValueAt(outputStreamIdx);
        mOutputStreams.removeItem(id);
    }

    // Free up the stream endpoint so that it can be used by some other stream
    res = deletedStream->disconnect();
    if (res != OK) {
        SET_ERR_L("Can't disconnect deleted stream %d", id);
        // fall through since we want to still list the stream as deleted.
    }
    mDeletedStreams.add(deletedStream);
    mNeedConfig = true;

    return res;
}

status_t Camera3Device::configureStreams(int operatingMode) {
    ATRACE_CALL();
    ALOGV("%s: E", __FUNCTION__);

    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    return configureStreamsLocked(operatingMode);
}

status_t Camera3Device::getInputBufferProducer(
        sp<IGraphicBufferProducer> *producer) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    if (producer == NULL) {
        return BAD_VALUE;
    } else if (mInputStream == NULL) {
        return INVALID_OPERATION;
    }

    return mInputStream->getInputBufferProducer(producer);
}

status_t Camera3Device::createDefaultRequest(int templateId,
        CameraMetadata *request) {
    ATRACE_CALL();
    ALOGV("%s: for template %d", __FUNCTION__, templateId);

    if (templateId <= 0 || templateId >= CAMERA3_TEMPLATE_COUNT) {
        android_errorWriteWithInfoLog(CameraService::SN_EVENT_LOG_ID, "26866110",
                IPCThreadState::self()->getCallingUid(), nullptr, 0);
        return BAD_VALUE;
    }

    Mutex::Autolock il(mInterfaceLock);

    {
        Mutex::Autolock l(mLock);
        switch (mStatus) {
            case STATUS_ERROR:
                CLOGE("Device has encountered a serious error");
                return INVALID_OPERATION;
            case STATUS_UNINITIALIZED:
                CLOGE("Device is not initialized!");
                return INVALID_OPERATION;
            case STATUS_UNCONFIGURED:
            case STATUS_CONFIGURED:
            case STATUS_ACTIVE:
                // OK
                break;
            default:
                SET_ERR_L("Unexpected status: %d", mStatus);
                return INVALID_OPERATION;
        }

        if (!mRequestTemplateCache[templateId].isEmpty()) {
            *request = mRequestTemplateCache[templateId];
            return OK;
        }
    }

    camera_metadata_t *rawRequest;
    status_t res = mInterface->constructDefaultRequestSettings(
            (camera3_request_template_t) templateId, &rawRequest);

    {
        Mutex::Autolock l(mLock);
        if (res == BAD_VALUE) {
            ALOGI("%s: template %d is not supported on this camera device",
                  __FUNCTION__, templateId);
            return res;
        } else if (res != OK) {
            CLOGE("Unable to construct request template %d: %s (%d)",
                    templateId, strerror(-res), res);
            return res;
        }

        set_camera_metadata_vendor_id(rawRequest, mVendorTagId);
        mRequestTemplateCache[templateId].acquire(rawRequest);

        *request = mRequestTemplateCache[templateId];
    }
    return OK;
}

status_t Camera3Device::waitUntilDrained() {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);
    nsecs_t maxExpectedDuration = getExpectedInFlightDuration();
    Mutex::Autolock l(mLock);

    return waitUntilDrainedLocked(maxExpectedDuration);
}

status_t Camera3Device::waitUntilDrainedLocked(nsecs_t maxExpectedDuration) {
    switch (mStatus) {
        case STATUS_UNINITIALIZED:
        case STATUS_UNCONFIGURED:
            ALOGV("%s: Already idle", __FUNCTION__);
            return OK;
        case STATUS_CONFIGURED:
            // To avoid race conditions, check with tracker to be sure
        case STATUS_ERROR:
        case STATUS_ACTIVE:
            // Need to verify shut down
            break;
        default:
            SET_ERR_L("Unexpected status: %d",mStatus);
            return INVALID_OPERATION;
    }
    ALOGV("%s: Camera %s: Waiting until idle (%" PRIi64 "ns)", __FUNCTION__, mId.string(),
            maxExpectedDuration);
    status_t res = waitUntilStateThenRelock(/*active*/ false, maxExpectedDuration);
    if (res != OK) {
        SET_ERR_L("Error waiting for HAL to drain: %s (%d)", strerror(-res),
                res);
    }
    return res;
}


void Camera3Device::internalUpdateStatusLocked(Status status) {
    mStatus = status;
    mRecentStatusUpdates.add(mStatus);
    mStatusChanged.broadcast();
}

// Pause to reconfigure
status_t Camera3Device::internalPauseAndWaitLocked(nsecs_t maxExpectedDuration) {
    mRequestThread->setPaused(true);
    mPauseStateNotify = true;

    ALOGV("%s: Camera %s: Internal wait until idle (% " PRIi64 " ns)", __FUNCTION__, mId.string(),
          maxExpectedDuration);
    status_t res = waitUntilStateThenRelock(/*active*/ false, maxExpectedDuration);
    if (res != OK) {
        SET_ERR_L("Can't idle device in %f seconds!",
                maxExpectedDuration/1e9);
    }

    return res;
}

// Resume after internalPauseAndWaitLocked
status_t Camera3Device::internalResumeLocked() {
    status_t res;

    mRequestThread->setPaused(false);

    res = waitUntilStateThenRelock(/*active*/ true, kActiveTimeout);
    if (res != OK) {
        SET_ERR_L("Can't transition to active in %f seconds!",
                kActiveTimeout/1e9);
    }
    mPauseStateNotify = false;
    return OK;
}

status_t Camera3Device::waitUntilStateThenRelock(bool active, nsecs_t timeout) {
    status_t res = OK;

    size_t startIndex = 0;
    if (mStatusWaiters == 0) {
        // Clear the list of recent statuses if there are no existing threads waiting on updates to
        // this status list
        mRecentStatusUpdates.clear();
    } else {
        // If other threads are waiting on updates to this status list, set the position of the
        // first element that this list will check rather than clearing the list.
        startIndex = mRecentStatusUpdates.size();
    }

    mStatusWaiters++;

    bool stateSeen = false;
    do {
        if (active == (mStatus == STATUS_ACTIVE)) {
            // Desired state is current
            break;
        }

        res = mStatusChanged.waitRelative(mLock, timeout);
        if (res != OK) break;

        // This is impossible, but if not, could result in subtle deadlocks and invalid state
        // transitions.
        LOG_ALWAYS_FATAL_IF(startIndex > mRecentStatusUpdates.size(),
                "%s: Skipping status updates in Camera3Device, may result in deadlock.",
                __FUNCTION__);

        // Encountered desired state since we began waiting
        for (size_t i = startIndex; i < mRecentStatusUpdates.size(); i++) {
            if (active == (mRecentStatusUpdates[i] == STATUS_ACTIVE) ) {
                stateSeen = true;
                break;
            }
        }
    } while (!stateSeen);

    mStatusWaiters--;

    return res;
}


status_t Camera3Device::setNotifyCallback(wp<NotificationListener> listener) {
    ATRACE_CALL();
    Mutex::Autolock l(mOutputLock);

    if (listener != NULL && mListener != NULL) {
        ALOGW("%s: Replacing old callback listener", __FUNCTION__);
    }
    mListener = listener;
    mRequestThread->setNotificationListener(listener);
    mPreparerThread->setNotificationListener(listener);

    return OK;
}

bool Camera3Device::willNotify3A() {
    return false;
}

status_t Camera3Device::waitForNextFrame(nsecs_t timeout) {
    ATRACE_CALL();
    status_t res;
    Mutex::Autolock l(mOutputLock);

    while (mResultQueue.empty()) {
        res = mResultSignal.waitRelative(mOutputLock, timeout);
        if (res == TIMED_OUT) {
            return res;
        } else if (res != OK) {
            ALOGW("%s: Camera %s: No frame in %" PRId64 " ns: %s (%d)",
                    __FUNCTION__, mId.string(), timeout, strerror(-res), res);
            return res;
        }
    }
    return OK;
}

status_t Camera3Device::getNextResult(CaptureResult *frame) {
    ATRACE_CALL();
    Mutex::Autolock l(mOutputLock);

    if (mResultQueue.empty()) {
        return NOT_ENOUGH_DATA;
    }

    if (frame == NULL) {
        ALOGE("%s: argument cannot be NULL", __FUNCTION__);
        return BAD_VALUE;
    }

    CaptureResult &result = *(mResultQueue.begin());
    frame->mResultExtras = result.mResultExtras;
    frame->mMetadata.acquire(result.mMetadata);
    mResultQueue.erase(mResultQueue.begin());

    return OK;
}

status_t Camera3Device::triggerAutofocus(uint32_t id) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);

    ALOGV("%s: Triggering autofocus, id %d", __FUNCTION__, id);
    // Mix-in this trigger into the next request and only the next request.
    RequestTrigger trigger[] = {
        {
            ANDROID_CONTROL_AF_TRIGGER,
            ANDROID_CONTROL_AF_TRIGGER_START
        },
        {
            ANDROID_CONTROL_AF_TRIGGER_ID,
            static_cast<int32_t>(id)
        }
    };

    return mRequestThread->queueTrigger(trigger,
                                        sizeof(trigger)/sizeof(trigger[0]));
}

status_t Camera3Device::triggerCancelAutofocus(uint32_t id) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);

    ALOGV("%s: Triggering cancel autofocus, id %d", __FUNCTION__, id);
    // Mix-in this trigger into the next request and only the next request.
    RequestTrigger trigger[] = {
        {
            ANDROID_CONTROL_AF_TRIGGER,
            ANDROID_CONTROL_AF_TRIGGER_CANCEL
        },
        {
            ANDROID_CONTROL_AF_TRIGGER_ID,
            static_cast<int32_t>(id)
        }
    };

    return mRequestThread->queueTrigger(trigger,
                                        sizeof(trigger)/sizeof(trigger[0]));
}

status_t Camera3Device::triggerPrecaptureMetering(uint32_t id) {
    ATRACE_CALL();
    Mutex::Autolock il(mInterfaceLock);

    ALOGV("%s: Triggering precapture metering, id %d", __FUNCTION__, id);
    // Mix-in this trigger into the next request and only the next request.
    RequestTrigger trigger[] = {
        {
            ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER,
            ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START
        },
        {
            ANDROID_CONTROL_AE_PRECAPTURE_ID,
            static_cast<int32_t>(id)
        }
    };

    return mRequestThread->queueTrigger(trigger,
                                        sizeof(trigger)/sizeof(trigger[0]));
}

status_t Camera3Device::flush(int64_t *frameNumber) {
    ATRACE_CALL();
    ALOGV("%s: Camera %s: Flushing all requests", __FUNCTION__, mId.string());
    Mutex::Autolock il(mInterfaceLock);

    {
        Mutex::Autolock l(mLock);
        mRequestThread->clear(/*out*/frameNumber);
    }

    return mRequestThread->flush();
}

status_t Camera3Device::prepare(int streamId) {
    return prepare(camera3::Camera3StreamInterface::ALLOCATE_PIPELINE_MAX, streamId);
}

status_t Camera3Device::prepare(int maxCount, int streamId) {
    ATRACE_CALL();
    ALOGV("%s: Camera %s: Preparing stream %d", __FUNCTION__, mId.string(), streamId);
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    sp<Camera3StreamInterface> stream;
    ssize_t outputStreamIdx = mOutputStreams.indexOfKey(streamId);
    if (outputStreamIdx == NAME_NOT_FOUND) {
        CLOGE("Stream %d does not exist", streamId);
        return BAD_VALUE;
    }

    stream = mOutputStreams.editValueAt(outputStreamIdx);

    if (stream->isUnpreparable() || stream->hasOutstandingBuffers() ) {
        CLOGE("Stream %d has already been a request target", streamId);
        return BAD_VALUE;
    }

    if (mRequestThread->isStreamPending(stream)) {
        CLOGE("Stream %d is already a target in a pending request", streamId);
        return BAD_VALUE;
    }

    return mPreparerThread->prepare(maxCount, stream);
}

status_t Camera3Device::tearDown(int streamId) {
    ATRACE_CALL();
    ALOGV("%s: Camera %s: Tearing down stream %d", __FUNCTION__, mId.string(), streamId);
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    sp<Camera3StreamInterface> stream;
    ssize_t outputStreamIdx = mOutputStreams.indexOfKey(streamId);
    if (outputStreamIdx == NAME_NOT_FOUND) {
        CLOGE("Stream %d does not exist", streamId);
        return BAD_VALUE;
    }

    stream = mOutputStreams.editValueAt(outputStreamIdx);

    if (stream->hasOutstandingBuffers() || mRequestThread->isStreamPending(stream)) {
        CLOGE("Stream %d is a target of a in-progress request", streamId);
        return BAD_VALUE;
    }

    return stream->tearDown();
}

status_t Camera3Device::addBufferListenerForStream(int streamId,
        wp<Camera3StreamBufferListener> listener) {
    ATRACE_CALL();
    ALOGV("%s: Camera %s: Adding buffer listener for stream %d", __FUNCTION__, mId.string(), streamId);
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    sp<Camera3StreamInterface> stream;
    ssize_t outputStreamIdx = mOutputStreams.indexOfKey(streamId);
    if (outputStreamIdx == NAME_NOT_FOUND) {
        CLOGE("Stream %d does not exist", streamId);
        return BAD_VALUE;
    }

    stream = mOutputStreams.editValueAt(outputStreamIdx);
    stream->addBufferListener(listener);

    return OK;
}

/**
 * Methods called by subclasses
 */

void Camera3Device::notifyStatus(bool idle) {
    ATRACE_CALL();
    {
        // Need mLock to safely update state and synchronize to current
        // state of methods in flight.
        Mutex::Autolock l(mLock);
        // We can get various system-idle notices from the status tracker
        // while starting up. Only care about them if we've actually sent
        // in some requests recently.
        if (mStatus != STATUS_ACTIVE && mStatus != STATUS_CONFIGURED) {
            return;
        }
        ALOGV("%s: Camera %s: Now %s", __FUNCTION__, mId.string(),
                idle ? "idle" : "active");
        internalUpdateStatusLocked(idle ? STATUS_CONFIGURED : STATUS_ACTIVE);

        // Skip notifying listener if we're doing some user-transparent
        // state changes
        if (mPauseStateNotify) return;
    }

    sp<NotificationListener> listener;
    {
        Mutex::Autolock l(mOutputLock);
        listener = mListener.promote();
    }
    if (idle && listener != NULL) {
        listener->notifyIdle();
    }
}

status_t Camera3Device::setConsumerSurfaces(int streamId,
        const std::vector<sp<Surface>>& consumers) {
    ATRACE_CALL();
    ALOGV("%s: Camera %s: set consumer surface for stream %d",
            __FUNCTION__, mId.string(), streamId);
    Mutex::Autolock il(mInterfaceLock);
    Mutex::Autolock l(mLock);

    if (consumers.size() == 0) {
        CLOGE("No consumer is passed!");
        return BAD_VALUE;
    }

    ssize_t idx = mOutputStreams.indexOfKey(streamId);
    if (idx == NAME_NOT_FOUND) {
        CLOGE("Stream %d is unknown", streamId);
        return idx;
    }
    sp<Camera3OutputStreamInterface> stream = mOutputStreams[idx];
    status_t res = stream->setConsumers(consumers);
    if (res != OK) {
        CLOGE("Stream %d set consumer failed (error %d %s) ", streamId, res, strerror(-res));
        return res;
    }

    if (stream->isConsumerConfigurationDeferred()) {
        if (!stream->isConfiguring()) {
            CLOGE("Stream %d was already fully configured.", streamId);
            return INVALID_OPERATION;
        }

        res = stream->finishConfiguration();
        if (res != OK) {
            SET_ERR_L("Can't finish configuring output stream %d: %s (%d)",
                   stream->getId(), strerror(-res), res);
            return res;
        }
    }

    return OK;
}

/**
 * Camera3Device private methods
 */

sp<Camera3Device::CaptureRequest> Camera3Device::createCaptureRequest(
        const CameraMetadata &request, const SurfaceMap &surfaceMap) {
    ATRACE_CALL();
    status_t res;

    sp<CaptureRequest> newRequest = new CaptureRequest;
    newRequest->mSettings = request;

    camera_metadata_entry_t inputStreams =
            newRequest->mSettings.find(ANDROID_REQUEST_INPUT_STREAMS);
    if (inputStreams.count > 0) {
        if (mInputStream == NULL ||
                mInputStream->getId() != inputStreams.data.i32[0]) {
            CLOGE("Request references unknown input stream %d",
                    inputStreams.data.u8[0]);
            return NULL;
        }
        // Lazy completion of stream configuration (allocation/registration)
        // on first use
        if (mInputStream->isConfiguring()) {
            res = mInputStream->finishConfiguration();
            if (res != OK) {
                SET_ERR_L("Unable to finish configuring input stream %d:"
                        " %s (%d)",
                        mInputStream->getId(), strerror(-res), res);
                return NULL;
            }
        }
        // Check if stream is being prepared
        if (mInputStream->isPreparing()) {
            CLOGE("Request references an input stream that's being prepared!");
            return NULL;
        }

        newRequest->mInputStream = mInputStream;
        newRequest->mSettings.erase(ANDROID_REQUEST_INPUT_STREAMS);
    }

    camera_metadata_entry_t streams =
            newRequest->mSettings.find(ANDROID_REQUEST_OUTPUT_STREAMS);
    if (streams.count == 0) {
        CLOGE("Zero output streams specified!");
        return NULL;
    }

    for (size_t i = 0; i < streams.count; i++) {
        int idx = mOutputStreams.indexOfKey(streams.data.i32[i]);
        if (idx == NAME_NOT_FOUND) {
            CLOGE("Request references unknown stream %d",
                    streams.data.u8[i]);
            return NULL;
        }
        sp<Camera3OutputStreamInterface> stream =
                mOutputStreams.editValueAt(idx);

        // It is illegal to include a deferred consumer output stream into a request
        auto iter = surfaceMap.find(streams.data.i32[i]);
        if (iter != surfaceMap.end()) {
            const std::vector<size_t>& surfaces = iter->second;
            for (const auto& surface : surfaces) {
                if (stream->isConsumerConfigurationDeferred(surface)) {
                    CLOGE("Stream %d surface %zu hasn't finished configuration yet "
                          "due to deferred consumer", stream->getId(), surface);
                    return NULL;
                }
            }
            newRequest->mOutputSurfaces[i] = surfaces;
        }

        // Lazy completion of stream configuration (allocation/registration)
        // on first use
        if (stream->isConfiguring()) {
            res = stream->finishConfiguration();
            if (res != OK) {
                SET_ERR_L("Unable to finish configuring stream %d: %s (%d)",
                        stream->getId(), strerror(-res), res);
                return NULL;
            }
        }
        // Check if stream is being prepared
        if (stream->isPreparing()) {
            CLOGE("Request references an output stream that's being prepared!");
            return NULL;
        }

        newRequest->mOutputStreams.push(stream);
    }
    newRequest->mSettings.erase(ANDROID_REQUEST_OUTPUT_STREAMS);
    newRequest->mBatchSize = 1;

    return newRequest;
}

bool Camera3Device::isOpaqueInputSizeSupported(uint32_t width, uint32_t height) {
    for (uint32_t i = 0; i < mSupportedOpaqueInputSizes.size(); i++) {
        Size size = mSupportedOpaqueInputSizes[i];
        if (size.width == width && size.height == height) {
            return true;
        }
    }

    return false;
}

void Camera3Device::cancelStreamsConfigurationLocked() {
    int res = OK;
    if (mInputStream != NULL && mInputStream->isConfiguring()) {
        res = mInputStream->cancelConfiguration();
        if (res != OK) {
            CLOGE("Can't cancel configuring input stream %d: %s (%d)",
                    mInputStream->getId(), strerror(-res), res);
        }
    }

    for (size_t i = 0; i < mOutputStreams.size(); i++) {
        sp<Camera3OutputStreamInterface> outputStream = mOutputStreams.editValueAt(i);
        if (outputStream->isConfiguring()) {
            res = outputStream->cancelConfiguration();
            if (res != OK) {
                CLOGE("Can't cancel configuring output stream %d: %s (%d)",
                        outputStream->getId(), strerror(-res), res);
            }
        }
    }

    // Return state to that at start of call, so that future configures
    // properly clean things up
    internalUpdateStatusLocked(STATUS_UNCONFIGURED);
    mNeedConfig = true;
}

status_t Camera3Device::configureStreamsLocked(int operatingMode) {
    ATRACE_CALL();
    status_t res;

    if (mStatus != STATUS_UNCONFIGURED && mStatus != STATUS_CONFIGURED) {
        CLOGE("Not idle");
        return INVALID_OPERATION;
    }

    if (operatingMode < 0) {
        CLOGE("Invalid operating mode: %d", operatingMode);
        return BAD_VALUE;
    }

    bool isConstrainedHighSpeed =
            static_cast<int>(StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE) ==
            operatingMode;

    if (mOperatingMode != operatingMode) {
        mNeedConfig = true;
        mIsConstrainedHighSpeedConfiguration = isConstrainedHighSpeed;
        mOperatingMode = operatingMode;
    }

    if (!mNeedConfig) {
        ALOGV("%s: Skipping config, no stream changes", __FUNCTION__);
        return OK;
    }

    // Workaround for device HALv3.2 or older spec bug - zero streams requires
    // adding a dummy stream instead.
    // TODO: Bug: 17321404 for fixing the HAL spec and removing this workaround.
    if (mOutputStreams.size() == 0) {
        addDummyStreamLocked();
    } else {
        tryRemoveDummyStreamLocked();
    }

    // Start configuring the streams
    ALOGV("%s: Camera %s: Starting stream configuration", __FUNCTION__, mId.string());

    camera3_stream_configuration config;
    config.operation_mode = mOperatingMode;
    config.num_streams = (mInputStream != NULL) + mOutputStreams.size();

    Vector<camera3_stream_t*> streams;
    streams.setCapacity(config.num_streams);

    if (mInputStream != NULL) {
        camera3_stream_t *inputStream;
        inputStream = mInputStream->startConfiguration();
        if (inputStream == NULL) {
            CLOGE("Can't start input stream configuration");
            cancelStreamsConfigurationLocked();
            return INVALID_OPERATION;
        }
        streams.add(inputStream);
    }

    for (size_t i = 0; i < mOutputStreams.size(); i++) {

        // Don't configure bidi streams twice, nor add them twice to the list
        if (mOutputStreams[i].get() ==
            static_cast<Camera3StreamInterface*>(mInputStream.get())) {

            config.num_streams--;
            continue;
        }

        camera3_stream_t *outputStream;
        outputStream = mOutputStreams.editValueAt(i)->startConfiguration();
        if (outputStream == NULL) {
            CLOGE("Can't start output stream configuration");
            cancelStreamsConfigurationLocked();
            return INVALID_OPERATION;
        }
        streams.add(outputStream);
    }

    config.streams = streams.editArray();

    // Do the HAL configuration; will potentially touch stream
    // max_buffers, usage, priv fields.

    res = mInterface->configureStreams(&config);

    if (res == BAD_VALUE) {
        // HAL rejected this set of streams as unsupported, clean up config
        // attempt and return to unconfigured state
        CLOGE("Set of requested inputs/outputs not supported by HAL");
        cancelStreamsConfigurationLocked();
        return BAD_VALUE;
    } else if (res != OK) {
        // Some other kind of error from configure_streams - this is not
        // expected
        SET_ERR_L("Unable to configure streams with HAL: %s (%d)",
                strerror(-res), res);
        return res;
    }

    // Finish all stream configuration immediately.
    // TODO: Try to relax this later back to lazy completion, which should be
    // faster

    if (mInputStream != NULL && mInputStream->isConfiguring()) {
        res = mInputStream->finishConfiguration();
        if (res != OK) {
            CLOGE("Can't finish configuring input stream %d: %s (%d)",
                    mInputStream->getId(), strerror(-res), res);
            cancelStreamsConfigurationLocked();
            return BAD_VALUE;
        }
    }

    for (size_t i = 0; i < mOutputStreams.size(); i++) {
        sp<Camera3OutputStreamInterface> outputStream =
            mOutputStreams.editValueAt(i);
        if (outputStream->isConfiguring() && !outputStream->isConsumerConfigurationDeferred()) {
            res = outputStream->finishConfiguration();
            if (res != OK) {
                CLOGE("Can't finish configuring output stream %d: %s (%d)",
                        outputStream->getId(), strerror(-res), res);
                cancelStreamsConfigurationLocked();
                return BAD_VALUE;
            }
        }
    }

    // Request thread needs to know to avoid using repeat-last-settings protocol
    // across configure_streams() calls
    mRequestThread->configurationComplete(mIsConstrainedHighSpeedConfiguration);

    char value[PROPERTY_VALUE_MAX];
    property_get("camera.fifo.disable", value, "0");
    int32_t disableFifo = atoi(value);
    if (disableFifo != 1) {
        // Boost priority of request thread to SCHED_FIFO.
        pid_t requestThreadTid = mRequestThread->getTid();
        res = requestPriority(getpid(), requestThreadTid,
                kRequestThreadPriority, /*isForApp*/ false, /*asynchronous*/ false);
        if (res != OK) {
            ALOGW("Can't set realtime priority for request processing thread: %s (%d)",
                    strerror(-res), res);
        } else {
            ALOGD("Set real time priority for request queue thread (tid %d)", requestThreadTid);
        }
    }

    // Update device state

    mNeedConfig = false;

    internalUpdateStatusLocked((mDummyStreamId == NO_STREAM) ?
            STATUS_CONFIGURED : STATUS_UNCONFIGURED);

    ALOGV("%s: Camera %s: Stream configuration complete", __FUNCTION__, mId.string());

    // tear down the deleted streams after configure streams.
    mDeletedStreams.clear();

    return OK;
}

status_t Camera3Device::addDummyStreamLocked() {
    ATRACE_CALL();
    status_t res;

    if (mDummyStreamId != NO_STREAM) {
        // Should never be adding a second dummy stream when one is already
        // active
        SET_ERR_L("%s: Camera %s: A dummy stream already exists!",
                __FUNCTION__, mId.string());
        return INVALID_OPERATION;
    }

    ALOGV("%s: Camera %s: Adding a dummy stream", __FUNCTION__, mId.string());

    sp<Camera3OutputStreamInterface> dummyStream =
            new Camera3DummyStream(mNextStreamId);

    res = mOutputStreams.add(mNextStreamId, dummyStream);
    if (res < 0) {
        SET_ERR_L("Can't add dummy stream to set: %s (%d)", strerror(-res), res);
        return res;
    }

    mDummyStreamId = mNextStreamId;
    mNextStreamId++;

    return OK;
}

status_t Camera3Device::tryRemoveDummyStreamLocked() {
    ATRACE_CALL();
    status_t res;

    if (mDummyStreamId == NO_STREAM) return OK;
    if (mOutputStreams.size() == 1) return OK;

    ALOGV("%s: Camera %s: Removing the dummy stream", __FUNCTION__, mId.string());

    // Ok, have a dummy stream and there's at least one other output stream,
    // so remove the dummy

    sp<Camera3StreamInterface> deletedStream;
    ssize_t outputStreamIdx = mOutputStreams.indexOfKey(mDummyStreamId);
    if (outputStreamIdx == NAME_NOT_FOUND) {
        SET_ERR_L("Dummy stream %d does not appear to exist", mDummyStreamId);
        return INVALID_OPERATION;
    }

    deletedStream = mOutputStreams.editValueAt(outputStreamIdx);
    mOutputStreams.removeItemsAt(outputStreamIdx);

    // Free up the stream endpoint so that it can be used by some other stream
    res = deletedStream->disconnect();
    if (res != OK) {
        SET_ERR_L("Can't disconnect deleted dummy stream %d", mDummyStreamId);
        // fall through since we want to still list the stream as deleted.
    }
    mDeletedStreams.add(deletedStream);
    mDummyStreamId = NO_STREAM;

    return res;
}

void Camera3Device::setErrorState(const char *fmt, ...) {
    ATRACE_CALL();
    Mutex::Autolock l(mLock);
    va_list args;
    va_start(args, fmt);

    setErrorStateLockedV(fmt, args);

    va_end(args);
}

void Camera3Device::setErrorStateV(const char *fmt, va_list args) {
    ATRACE_CALL();
    Mutex::Autolock l(mLock);
    setErrorStateLockedV(fmt, args);
}

void Camera3Device::setErrorStateLocked(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);

    setErrorStateLockedV(fmt, args);

    va_end(args);
}

void Camera3Device::setErrorStateLockedV(const char *fmt, va_list args) {
    // Print out all error messages to log
    String8 errorCause = String8::formatV(fmt, args);
    ALOGE("Camera %s: %s", mId.string(), errorCause.string());

    // But only do error state transition steps for the first error
    if (mStatus == STATUS_ERROR || mStatus == STATUS_UNINITIALIZED) return;

    mErrorCause = errorCause;

    if (mRequestThread != nullptr) {
        mRequestThread->setPaused(true);
    }
    internalUpdateStatusLocked(STATUS_ERROR);

    // Notify upstream about a device error
    sp<NotificationListener> listener = mListener.promote();
    if (listener != NULL) {
        listener->notifyError(hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_DEVICE,
                CaptureResultExtras());
    }

    // Save stack trace. View by dumping it later.
    CameraTraces::saveTrace();
    // TODO: consider adding errorCause and client pid/procname
}

/**
 * In-flight request management
 */

status_t Camera3Device::registerInFlight(uint32_t frameNumber,
        int32_t numBuffers, CaptureResultExtras resultExtras, bool hasInput,
        bool hasAppCallback, nsecs_t maxExpectedDuration) {
    ATRACE_CALL();
    Mutex::Autolock l(mInFlightLock);

    ssize_t res;
    res = mInFlightMap.add(frameNumber, InFlightRequest(numBuffers, resultExtras, hasInput,
            hasAppCallback, maxExpectedDuration));
    if (res < 0) return res;

    if (mInFlightMap.size() == 1) {
        // hold mLock to prevent race with disconnect
        Mutex::Autolock l(mLock);
        if (mStatusTracker != nullptr) {
            mStatusTracker->markComponentActive(mInFlightStatusId);
        }
    }

    mExpectedInflightDuration += maxExpectedDuration;
    return OK;
}

void Camera3Device::returnOutputBuffers(
        const camera3_stream_buffer_t *outputBuffers, size_t numBuffers,
        nsecs_t timestamp) {
    for (size_t i = 0; i < numBuffers; i++)
    {
        Camera3Stream *stream = Camera3Stream::cast(outputBuffers[i].stream);
        status_t res = stream->returnBuffer(outputBuffers[i], timestamp);
        // Note: stream may be deallocated at this point, if this buffer was
        // the last reference to it.
        if (res != OK) {
            ALOGE("Can't return buffer to its stream: %s (%d)",
                strerror(-res), res);
        }
    }
}

void Camera3Device::removeInFlightMapEntryLocked(int idx) {
    ATRACE_CALL();
    nsecs_t duration = mInFlightMap.valueAt(idx).maxExpectedDuration;
    mInFlightMap.removeItemsAt(idx, 1);

    // Indicate idle inFlightMap to the status tracker
    if (mInFlightMap.size() == 0) {
        // hold mLock to prevent race with disconnect
        Mutex::Autolock l(mLock);
        if (mStatusTracker != nullptr) {
            mStatusTracker->markComponentIdle(mInFlightStatusId, Fence::NO_FENCE);
        }
    }
    mExpectedInflightDuration -= duration;
}

void Camera3Device::removeInFlightRequestIfReadyLocked(int idx) {

    const InFlightRequest &request = mInFlightMap.valueAt(idx);
    const uint32_t frameNumber = mInFlightMap.keyAt(idx);

    nsecs_t sensorTimestamp = request.sensorTimestamp;
    nsecs_t shutterTimestamp = request.shutterTimestamp;

    // Check if it's okay to remove the request from InFlightMap:
    // In the case of a successful request:
    //      all input and output buffers, all result metadata, shutter callback
    //      arrived.
    // In the case of a unsuccessful request:
    //      all input and output buffers arrived.
    if (request.numBuffersLeft == 0 &&
            (request.skipResultMetadata ||
            (request.haveResultMetadata && shutterTimestamp != 0))) {
        ATRACE_ASYNC_END("frame capture", frameNumber);

        // Sanity check - if sensor timestamp matches shutter timestamp in the
        // case of request having callback.
        if (request.hasCallback && request.requestStatus == OK &&
                sensorTimestamp != shutterTimestamp) {
            SET_ERR("sensor timestamp (%" PRId64
                ") for frame %d doesn't match shutter timestamp (%" PRId64 ")",
                sensorTimestamp, frameNumber, shutterTimestamp);
        }

        // for an unsuccessful request, it may have pending output buffers to
        // return.
        assert(request.requestStatus != OK ||
               request.pendingOutputBuffers.size() == 0);
        returnOutputBuffers(request.pendingOutputBuffers.array(),
            request.pendingOutputBuffers.size(), 0);

        removeInFlightMapEntryLocked(idx);
        ALOGVV("%s: removed frame %d from InFlightMap", __FUNCTION__, frameNumber);
     }

    // Sanity check - if we have too many in-flight frames, something has
    // likely gone wrong
    if (!mIsConstrainedHighSpeedConfiguration && mInFlightMap.size() > kInFlightWarnLimit) {
        CLOGE("In-flight list too large: %zu", mInFlightMap.size());
    } else if (mIsConstrainedHighSpeedConfiguration && mInFlightMap.size() >
            kInFlightWarnLimitHighSpeed) {
        CLOGE("In-flight list too large for high speed configuration: %zu",
                mInFlightMap.size());
    }
}

void Camera3Device::flushInflightRequests() {
    ATRACE_CALL();
    { // First return buffers cached in mInFlightMap
        Mutex::Autolock l(mInFlightLock);
        for (size_t idx = 0; idx < mInFlightMap.size(); idx++) {
            const InFlightRequest &request = mInFlightMap.valueAt(idx);
            returnOutputBuffers(request.pendingOutputBuffers.array(),
                request.pendingOutputBuffers.size(), 0);
        }
        mInFlightMap.clear();
        mExpectedInflightDuration = 0;
    }

    // Then return all inflight buffers not returned by HAL
    std::vector<std::pair<int32_t, int32_t>> inflightKeys;
    mInterface->getInflightBufferKeys(&inflightKeys);

    int32_t inputStreamId = (mInputStream != nullptr) ? mInputStream->getId() : -1;
    for (auto& pair : inflightKeys) {
        int32_t frameNumber = pair.first;
        int32_t streamId = pair.second;
        buffer_handle_t* buffer;
        status_t res = mInterface->popInflightBuffer(frameNumber, streamId, &buffer);
        if (res != OK) {
            ALOGE("%s: Frame %d: No in-flight buffer for stream %d",
                    __FUNCTION__, frameNumber, streamId);
            continue;
        }

        camera3_stream_buffer_t streamBuffer;
        streamBuffer.buffer = buffer;
        streamBuffer.status = CAMERA3_BUFFER_STATUS_ERROR;
        streamBuffer.acquire_fence = -1;
        streamBuffer.release_fence = -1;

        // First check if the buffer belongs to deleted stream
        bool streamDeleted = false;
        for (auto& stream : mDeletedStreams) {
            if (streamId == stream->getId()) {
                streamDeleted = true;
                // Return buffer to deleted stream
                camera3_stream* halStream = stream->asHalStream();
                streamBuffer.stream = halStream;
                switch (halStream->stream_type) {
                    case CAMERA3_STREAM_OUTPUT:
                        res = stream->returnBuffer(streamBuffer, /*timestamp*/ 0);
                        if (res != OK) {
                            ALOGE("%s: Can't return output buffer for frame %d to"
                                  " stream %d: %s (%d)",  __FUNCTION__,
                                  frameNumber, streamId, strerror(-res), res);
                        }
                        break;
                    case CAMERA3_STREAM_INPUT:
                        res = stream->returnInputBuffer(streamBuffer);
                        if (res != OK) {
                            ALOGE("%s: Can't return input buffer for frame %d to"
                                  " stream %d: %s (%d)",  __FUNCTION__,
                                  frameNumber, streamId, strerror(-res), res);
                        }
                        break;
                    default: // Bi-direcitonal stream is deprecated
                        ALOGE("%s: stream %d has unknown stream type %d",
                                __FUNCTION__, streamId, halStream->stream_type);
                        break;
                }
                break;
            }
        }
        if (streamDeleted) {
            continue;
        }

        // Then check against configured streams
        if (streamId == inputStreamId) {
            streamBuffer.stream = mInputStream->asHalStream();
            res = mInputStream->returnInputBuffer(streamBuffer);
            if (res != OK) {
                ALOGE("%s: Can't return input buffer for frame %d to"
                      " stream %d: %s (%d)",  __FUNCTION__,
                      frameNumber, streamId, strerror(-res), res);
            }
        } else {
            ssize_t idx = mOutputStreams.indexOfKey(streamId);
            if (idx == NAME_NOT_FOUND) {
                ALOGE("%s: Output stream id %d not found!", __FUNCTION__, streamId);
                continue;
            }
            streamBuffer.stream = mOutputStreams.valueAt(idx)->asHalStream();
            returnOutputBuffers(&streamBuffer, /*size*/1, /*timestamp*/ 0);
        }
    }
}

void Camera3Device::insertResultLocked(CaptureResult *result,
        uint32_t frameNumber) {
    if (result == nullptr) return;

    camera_metadata_t *meta = const_cast<camera_metadata_t *>(
            result->mMetadata.getAndLock());
    set_camera_metadata_vendor_id(meta, mVendorTagId);
    result->mMetadata.unlock(meta);

    if (result->mMetadata.update(ANDROID_REQUEST_FRAME_COUNT,
            (int32_t*)&frameNumber, 1) != OK) {
        SET_ERR("Failed to set frame number %d in metadata", frameNumber);
        return;
    }

    if (result->mMetadata.update(ANDROID_REQUEST_ID, &result->mResultExtras.requestId, 1) != OK) {
        SET_ERR("Failed to set request ID in metadata for frame %d", frameNumber);
        return;
    }

    // Valid result, insert into queue
    List<CaptureResult>::iterator queuedResult =
            mResultQueue.insert(mResultQueue.end(), CaptureResult(*result));
    ALOGVV("%s: result requestId = %" PRId32 ", frameNumber = %" PRId64
           ", burstId = %" PRId32, __FUNCTION__,
           queuedResult->mResultExtras.requestId,
           queuedResult->mResultExtras.frameNumber,
           queuedResult->mResultExtras.burstId);

    mResultSignal.signal();
}


void Camera3Device::sendPartialCaptureResult(const camera_metadata_t * partialResult,
        const CaptureResultExtras &resultExtras, uint32_t frameNumber) {
    ATRACE_CALL();
    Mutex::Autolock l(mOutputLock);

    CaptureResult captureResult;
    captureResult.mResultExtras = resultExtras;
    captureResult.mMetadata = partialResult;

    insertResultLocked(&captureResult, frameNumber);
}


void Camera3Device::sendCaptureResult(CameraMetadata &pendingMetadata,
        CaptureResultExtras &resultExtras,
        CameraMetadata &collectedPartialResult,
        uint32_t frameNumber,
        bool reprocess) {
    ATRACE_CALL();
    if (pendingMetadata.isEmpty())
        return;

    Mutex::Autolock l(mOutputLock);

    // TODO: need to track errors for tighter bounds on expected frame number
    if (reprocess) {
        if (frameNumber < mNextReprocessResultFrameNumber) {
            SET_ERR("Out-of-order reprocess capture result metadata submitted! "
                "(got frame number %d, expecting %d)",
                frameNumber, mNextReprocessResultFrameNumber);
            return;
        }
        mNextReprocessResultFrameNumber = frameNumber + 1;
    } else {
        if (frameNumber < mNextResultFrameNumber) {
            SET_ERR("Out-of-order capture result metadata submitted! "
                    "(got frame number %d, expecting %d)",
                    frameNumber, mNextResultFrameNumber);
            return;
        }
        mNextResultFrameNumber = frameNumber + 1;
    }

    CaptureResult captureResult;
    captureResult.mResultExtras = resultExtras;
    captureResult.mMetadata = pendingMetadata;

    // Append any previous partials to form a complete result
    if (mUsePartialResult && !collectedPartialResult.isEmpty()) {
        captureResult.mMetadata.append(collectedPartialResult);
    }

    captureResult.mMetadata.sort();

    // Check that there's a timestamp in the result metadata
    camera_metadata_entry timestamp = captureResult.mMetadata.find(ANDROID_SENSOR_TIMESTAMP);
    if (timestamp.count == 0) {
        SET_ERR("No timestamp provided by HAL for frame %d!",
                frameNumber);
        return;
    }

    mTagMonitor.monitorMetadata(TagMonitor::RESULT,
            frameNumber, timestamp.data.i64[0], captureResult.mMetadata);

    insertResultLocked(&captureResult, frameNumber);
}

/**
 * Camera HAL device callback methods
 */

void Camera3Device::processCaptureResult(const camera3_capture_result *result) {
    ATRACE_CALL();

    status_t res;

    uint32_t frameNumber = result->frame_number;
    if (result->result == NULL && result->num_output_buffers == 0 &&
            result->input_buffer == NULL) {
        SET_ERR("No result data provided by HAL for frame %d",
                frameNumber);
        return;
    }

    if (!mUsePartialResult &&
            result->result != NULL &&
            result->partial_result != 1) {
        SET_ERR("Result is malformed for frame %d: partial_result %u must be 1"
                " if partial result is not supported",
                frameNumber, result->partial_result);
        return;
    }

    bool isPartialResult = false;
    CameraMetadata collectedPartialResult;
    CaptureResultExtras resultExtras;
    bool hasInputBufferInRequest = false;

    // Get shutter timestamp and resultExtras from list of in-flight requests,
    // where it was added by the shutter notification for this frame. If the
    // shutter timestamp isn't received yet, append the output buffers to the
    // in-flight request and they will be returned when the shutter timestamp
    // arrives. Update the in-flight status and remove the in-flight entry if
    // all result data and shutter timestamp have been received.
    nsecs_t shutterTimestamp = 0;

    {
        Mutex::Autolock l(mInFlightLock);
        ssize_t idx = mInFlightMap.indexOfKey(frameNumber);
        if (idx == NAME_NOT_FOUND) {
            SET_ERR("Unknown frame number for capture result: %d",
                    frameNumber);
            return;
        }
        InFlightRequest &request = mInFlightMap.editValueAt(idx);
        ALOGVV("%s: got InFlightRequest requestId = %" PRId32
                ", frameNumber = %" PRId64 ", burstId = %" PRId32
                ", partialResultCount = %d, hasCallback = %d",
                __FUNCTION__, request.resultExtras.requestId,
                request.resultExtras.frameNumber, request.resultExtras.burstId,
                result->partial_result, request.hasCallback);
        // Always update the partial count to the latest one if it's not 0
        // (buffers only). When framework aggregates adjacent partial results
        // into one, the latest partial count will be used.
        if (result->partial_result != 0)
            request.resultExtras.partialResultCount = result->partial_result;

        // Check if this result carries only partial metadata
        if (mUsePartialResult && result->result != NULL) {
            if (result->partial_result > mNumPartialResults || result->partial_result < 1) {
                SET_ERR("Result is malformed for frame %d: partial_result %u must be  in"
                        " the range of [1, %d] when metadata is included in the result",
                        frameNumber, result->partial_result, mNumPartialResults);
                return;
            }
            isPartialResult = (result->partial_result < mNumPartialResults);
            if (isPartialResult) {
                request.collectedPartialResult.append(result->result);
            }

            if (isPartialResult && request.hasCallback) {
                // Send partial capture result
                sendPartialCaptureResult(result->result, request.resultExtras,
                        frameNumber);
            }
        }

        shutterTimestamp = request.shutterTimestamp;
        hasInputBufferInRequest = request.hasInputBuffer;

        // Did we get the (final) result metadata for this capture?
        if (result->result != NULL && !isPartialResult) {
            if (request.haveResultMetadata) {
                SET_ERR("Called multiple times with metadata for frame %d",
                        frameNumber);
                return;
            }
            if (mUsePartialResult &&
                    !request.collectedPartialResult.isEmpty()) {
                collectedPartialResult.acquire(
                    request.collectedPartialResult);
            }
            request.haveResultMetadata = true;
        }

        uint32_t numBuffersReturned = result->num_output_buffers;
        if (result->input_buffer != NULL) {
            if (hasInputBufferInRequest) {
                numBuffersReturned += 1;
            } else {
                ALOGW("%s: Input buffer should be NULL if there is no input"
                        " buffer sent in the request",
                        __FUNCTION__);
            }
        }
        request.numBuffersLeft -= numBuffersReturned;
        if (request.numBuffersLeft < 0) {
            SET_ERR("Too many buffers returned for frame %d",
                    frameNumber);
            return;
        }

        camera_metadata_ro_entry_t entry;
        res = find_camera_metadata_ro_entry(result->result,
                ANDROID_SENSOR_TIMESTAMP, &entry);
        if (res == OK && entry.count == 1) {
            request.sensorTimestamp = entry.data.i64[0];
        }

        // If shutter event isn't received yet, append the output buffers to
        // the in-flight request. Otherwise, return the output buffers to
        // streams.
        if (shutterTimestamp == 0) {
            request.pendingOutputBuffers.appendArray(result->output_buffers,
                result->num_output_buffers);
        } else {
            returnOutputBuffers(result->output_buffers,
                result->num_output_buffers, shutterTimestamp);
        }

        if (result->result != NULL && !isPartialResult) {
            if (shutterTimestamp == 0) {
                request.pendingMetadata = result->result;
                request.collectedPartialResult = collectedPartialResult;
            } else if (request.hasCallback) {
                CameraMetadata metadata;
                metadata = result->result;
                sendCaptureResult(metadata, request.resultExtras,
                    collectedPartialResult, frameNumber,
                    hasInputBufferInRequest);
            }
        }

        removeInFlightRequestIfReadyLocked(idx);
    } // scope for mInFlightLock

    if (result->input_buffer != NULL) {
        if (hasInputBufferInRequest) {
            Camera3Stream *stream =
                Camera3Stream::cast(result->input_buffer->stream);
            res = stream->returnInputBuffer(*(result->input_buffer));
            // Note: stream may be deallocated at this point, if this buffer was the
            // last reference to it.
            if (res != OK) {
                ALOGE("%s: RequestThread: Can't return input buffer for frame %d to"
                      "  its stream:%s (%d)",  __FUNCTION__,
                      frameNumber, strerror(-res), res);
            }
        } else {
            ALOGW("%s: Input buffer should be NULL if there is no input"
                    " buffer sent in the request, skipping input buffer return.",
                    __FUNCTION__);
        }
    }
}

void Camera3Device::notify(const camera3_notify_msg *msg) {
    ATRACE_CALL();
    sp<NotificationListener> listener;
    {
        Mutex::Autolock l(mOutputLock);
        listener = mListener.promote();
    }

    if (msg == NULL) {
        SET_ERR("HAL sent NULL notify message!");
        return;
    }

    switch (msg->type) {
        case CAMERA3_MSG_ERROR: {
            notifyError(msg->message.error, listener);
            break;
        }
        case CAMERA3_MSG_SHUTTER: {
            notifyShutter(msg->message.shutter, listener);
            break;
        }
        default:
            SET_ERR("Unknown notify message from HAL: %d",
                    msg->type);
    }
}

void Camera3Device::notifyError(const camera3_error_msg_t &msg,
        sp<NotificationListener> listener) {
    ATRACE_CALL();
    // Map camera HAL error codes to ICameraDeviceCallback error codes
    // Index into this with the HAL error code
    static const int32_t halErrorMap[CAMERA3_MSG_NUM_ERRORS] = {
        // 0 = Unused error code
        hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_INVALID_ERROR,
        // 1 = CAMERA3_MSG_ERROR_DEVICE
        hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_DEVICE,
        // 2 = CAMERA3_MSG_ERROR_REQUEST
        hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_REQUEST,
        // 3 = CAMERA3_MSG_ERROR_RESULT
        hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_RESULT,
        // 4 = CAMERA3_MSG_ERROR_BUFFER
        hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_BUFFER
    };

    int32_t errorCode =
            ((msg.error_code >= 0) &&
                    (msg.error_code < CAMERA3_MSG_NUM_ERRORS)) ?
            halErrorMap[msg.error_code] :
            hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_INVALID_ERROR;

    int streamId = 0;
    if (msg.error_stream != NULL) {
        Camera3Stream *stream =
                Camera3Stream::cast(msg.error_stream);
        streamId = stream->getId();
    }
    ALOGV("Camera %s: %s: HAL error, frame %d, stream %d: %d",
            mId.string(), __FUNCTION__, msg.frame_number,
            streamId, msg.error_code);

    CaptureResultExtras resultExtras;
    switch (errorCode) {
        case hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_DEVICE:
            // SET_ERR calls notifyError
            SET_ERR("Camera HAL reported serious device error");
            break;
        case hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_REQUEST:
        case hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_RESULT:
        case hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_BUFFER:
            {
                Mutex::Autolock l(mInFlightLock);
                ssize_t idx = mInFlightMap.indexOfKey(msg.frame_number);
                if (idx >= 0) {
                    InFlightRequest &r = mInFlightMap.editValueAt(idx);
                    r.requestStatus = msg.error_code;
                    resultExtras = r.resultExtras;
                    if (hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_RESULT == errorCode
                            ||  hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_REQUEST ==
                            errorCode) {
                        r.skipResultMetadata = true;
                    }
                    if (hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_RESULT ==
                            errorCode) {
                        // In case of missing result check whether the buffers
                        // returned. If they returned, then remove inflight
                        // request.
                        removeInFlightRequestIfReadyLocked(idx);
                    }
                } else {
                    resultExtras.frameNumber = msg.frame_number;
                    ALOGE("Camera %s: %s: cannot find in-flight request on "
                            "frame %" PRId64 " error", mId.string(), __FUNCTION__,
                            resultExtras.frameNumber);
                }
            }
            resultExtras.errorStreamId = streamId;
            if (listener != NULL) {
                listener->notifyError(errorCode, resultExtras);
            } else {
                ALOGE("Camera %s: %s: no listener available", mId.string(), __FUNCTION__);
            }
            break;
        default:
            // SET_ERR calls notifyError
            SET_ERR("Unknown error message from HAL: %d", msg.error_code);
            break;
    }
}

void Camera3Device::notifyShutter(const camera3_shutter_msg_t &msg,
        sp<NotificationListener> listener) {
    ATRACE_CALL();
    ssize_t idx;

    // Set timestamp for the request in the in-flight tracking
    // and get the request ID to send upstream
    {
        Mutex::Autolock l(mInFlightLock);
        idx = mInFlightMap.indexOfKey(msg.frame_number);
        if (idx >= 0) {
            InFlightRequest &r = mInFlightMap.editValueAt(idx);

            // Verify ordering of shutter notifications
            {
                Mutex::Autolock l(mOutputLock);
                // TODO: need to track errors for tighter bounds on expected frame number.
                if (r.hasInputBuffer) {
                    if (msg.frame_number < mNextReprocessShutterFrameNumber) {
                        SET_ERR("Shutter notification out-of-order. Expected "
                                "notification for frame %d, got frame %d",
                                mNextReprocessShutterFrameNumber, msg.frame_number);
                        return;
                    }
                    mNextReprocessShutterFrameNumber = msg.frame_number + 1;
                } else {
                    if (msg.frame_number < mNextShutterFrameNumber) {
                        SET_ERR("Shutter notification out-of-order. Expected "
                                "notification for frame %d, got frame %d",
                                mNextShutterFrameNumber, msg.frame_number);
                        return;
                    }
                    mNextShutterFrameNumber = msg.frame_number + 1;
                }
            }

            r.shutterTimestamp = msg.timestamp;
            if (r.hasCallback) {
                ALOGVV("Camera %s: %s: Shutter fired for frame %d (id %d) at %" PRId64,
                    mId.string(), __FUNCTION__,
                    msg.frame_number, r.resultExtras.requestId, msg.timestamp);
                // Call listener, if any
                if (listener != NULL) {
                    listener->notifyShutter(r.resultExtras, msg.timestamp);
                }
                // send pending result and buffers
                sendCaptureResult(r.pendingMetadata, r.resultExtras,
                    r.collectedPartialResult, msg.frame_number,
                    r.hasInputBuffer);
            }
            returnOutputBuffers(r.pendingOutputBuffers.array(),
                r.pendingOutputBuffers.size(), r.shutterTimestamp);
            r.pendingOutputBuffers.clear();

            removeInFlightRequestIfReadyLocked(idx);
        }
    }
    if (idx < 0) {
        SET_ERR("Shutter notification for non-existent frame number %d",
                msg.frame_number);
    }
}


CameraMetadata Camera3Device::getLatestRequestLocked() {
    ALOGV("%s", __FUNCTION__);

    CameraMetadata retVal;

    if (mRequestThread != NULL) {
        retVal = mRequestThread->getLatestRequest();
    }

    return retVal;
}


void Camera3Device::monitorMetadata(TagMonitor::eventSource source,
        int64_t frameNumber, nsecs_t timestamp, const CameraMetadata& metadata) {
    mTagMonitor.monitorMetadata(source, frameNumber, timestamp, metadata);
}

/**
 * HalInterface inner class methods
 */

Camera3Device::HalInterface::HalInterface(
            sp<ICameraDeviceSession> &session,
            std::shared_ptr<RequestMetadataQueue> queue) :
        mHidlSession(session),
        mRequestMetadataQueue(queue) {}

Camera3Device::HalInterface::HalInterface() {}

Camera3Device::HalInterface::HalInterface(const HalInterface& other) :
        mHidlSession(other.mHidlSession),
        mRequestMetadataQueue(other.mRequestMetadataQueue) {}

bool Camera3Device::HalInterface::valid() {
    return (mHidlSession != nullptr);
}

void Camera3Device::HalInterface::clear() {
    mHidlSession.clear();
}

bool Camera3Device::HalInterface::supportBatchRequest() {
    return mHidlSession != nullptr;
}

status_t Camera3Device::HalInterface::constructDefaultRequestSettings(
        camera3_request_template_t templateId,
        /*out*/ camera_metadata_t **requestTemplate) {
    ATRACE_NAME("CameraHal::constructDefaultRequestSettings");
    if (!valid()) return INVALID_OPERATION;
    status_t res = OK;

    common::V1_0::Status status;
    RequestTemplate id;
    switch (templateId) {
        case CAMERA3_TEMPLATE_PREVIEW:
            id = RequestTemplate::PREVIEW;
            break;
        case CAMERA3_TEMPLATE_STILL_CAPTURE:
            id = RequestTemplate::STILL_CAPTURE;
            break;
        case CAMERA3_TEMPLATE_VIDEO_RECORD:
            id = RequestTemplate::VIDEO_RECORD;
            break;
        case CAMERA3_TEMPLATE_VIDEO_SNAPSHOT:
            id = RequestTemplate::VIDEO_SNAPSHOT;
            break;
        case CAMERA3_TEMPLATE_ZERO_SHUTTER_LAG:
            id = RequestTemplate::ZERO_SHUTTER_LAG;
            break;
        case CAMERA3_TEMPLATE_MANUAL:
            id = RequestTemplate::MANUAL;
            break;
        default:
            // Unknown template ID
            return BAD_VALUE;
    }
    auto err = mHidlSession->constructDefaultRequestSettings(id,
            [&status, &requestTemplate]
            (common::V1_0::Status s, const device::V3_2::CameraMetadata& request) {
                status = s;
                if (status == common::V1_0::Status::OK) {
                    const camera_metadata *r =
                            reinterpret_cast<const camera_metadata_t*>(request.data());
                    size_t expectedSize = request.size();
                    int ret = validate_camera_metadata_structure(r, &expectedSize);
                    if (ret == OK || ret == CAMERA_METADATA_VALIDATION_SHIFTED) {
                        *requestTemplate = clone_camera_metadata(r);
                        if (*requestTemplate == nullptr) {
                            ALOGE("%s: Unable to clone camera metadata received from HAL",
                                    __FUNCTION__);
                            status = common::V1_0::Status::INTERNAL_ERROR;
                        }
                    } else {
                        ALOGE("%s: Malformed camera metadata received from HAL", __FUNCTION__);
                        status = common::V1_0::Status::INTERNAL_ERROR;
                    }
                }
            });
    if (!err.isOk()) {
        ALOGE("%s: Transaction error: %s", __FUNCTION__, err.description().c_str());
        res = DEAD_OBJECT;
    } else {
        res = CameraProviderManager::mapToStatusT(status);
    }

    return res;
}

status_t Camera3Device::HalInterface::configureStreams(camera3_stream_configuration *config) {
    ATRACE_NAME("CameraHal::configureStreams");
    if (!valid()) return INVALID_OPERATION;
    status_t res = OK;

    // Convert stream config to HIDL
    std::set<int> activeStreams;
    StreamConfiguration requestedConfiguration;
    requestedConfiguration.streams.resize(config->num_streams);
    for (size_t i = 0; i < config->num_streams; i++) {
        Stream &dst = requestedConfiguration.streams[i];
        camera3_stream_t *src = config->streams[i];

        Camera3Stream* cam3stream = Camera3Stream::cast(src);
        cam3stream->setBufferFreedListener(this);
        int streamId = cam3stream->getId();
        StreamType streamType;
        switch (src->stream_type) {
            case CAMERA3_STREAM_OUTPUT:
                streamType = StreamType::OUTPUT;
                break;
            case CAMERA3_STREAM_INPUT:
                streamType = StreamType::INPUT;
                break;
            default:
                ALOGE("%s: Stream %d: Unsupported stream type %d",
                        __FUNCTION__, streamId, config->streams[i]->stream_type);
                return BAD_VALUE;
        }
        dst.id = streamId;
        dst.streamType = streamType;
        dst.width = src->width;
        dst.height = src->height;
        dst.format = mapToPixelFormat(src->format);
        dst.usage = mapToConsumerUsage(cam3stream->getUsage());
        dst.dataSpace = mapToHidlDataspace(src->data_space);
        dst.rotation = mapToStreamRotation((camera3_stream_rotation_t) src->rotation);

        activeStreams.insert(streamId);
        // Create Buffer ID map if necessary
        if (mBufferIdMaps.count(streamId) == 0) {
            mBufferIdMaps.emplace(streamId, BufferIdMap{});
        }
    }
    // remove BufferIdMap for deleted streams
    for(auto it = mBufferIdMaps.begin(); it != mBufferIdMaps.end();) {
        int streamId = it->first;
        bool active = activeStreams.count(streamId) > 0;
        if (!active) {
            it = mBufferIdMaps.erase(it);
        } else {
            ++it;
        }
    }

    res = mapToStreamConfigurationMode(
            (camera3_stream_configuration_mode_t) config->operation_mode,
            /*out*/ &requestedConfiguration.operationMode);
    if (res != OK) {
        return res;
    }

    // Invoke configureStreams

    device::V3_3::HalStreamConfiguration finalConfiguration;
    common::V1_0::Status status;

    // See if we have v3.3 HAL
    sp<device::V3_3::ICameraDeviceSession> hidlSession_3_3;
    auto castResult = device::V3_3::ICameraDeviceSession::castFrom(mHidlSession);
    if (castResult.isOk()) {
        hidlSession_3_3 = castResult;
    } else {
        ALOGE("%s: Transaction error when casting ICameraDeviceSession: %s", __FUNCTION__,
                castResult.description().c_str());
    }
    if (hidlSession_3_3 != nullptr) {
        // We do; use v3.3 for the call
        ALOGV("%s: v3.3 device found", __FUNCTION__);
        auto err = hidlSession_3_3->configureStreams_3_3(requestedConfiguration,
            [&status, &finalConfiguration]
            (common::V1_0::Status s, const device::V3_3::HalStreamConfiguration& halConfiguration) {
                finalConfiguration = halConfiguration;
                status = s;
            });
        if (!err.isOk()) {
            ALOGE("%s: Transaction error: %s", __FUNCTION__, err.description().c_str());
            return DEAD_OBJECT;
        }
    } else {
        // We don't; use v3.2 call and construct a v3.3 HalStreamConfiguration
        ALOGV("%s: v3.2 device found", __FUNCTION__);
        HalStreamConfiguration finalConfiguration_3_2;
        auto err = mHidlSession->configureStreams(requestedConfiguration,
                [&status, &finalConfiguration_3_2]
                (common::V1_0::Status s, const HalStreamConfiguration& halConfiguration) {
                    finalConfiguration_3_2 = halConfiguration;
                    status = s;
                });
        if (!err.isOk()) {
            ALOGE("%s: Transaction error: %s", __FUNCTION__, err.description().c_str());
            return DEAD_OBJECT;
        }
        finalConfiguration.streams.resize(finalConfiguration_3_2.streams.size());
        for (size_t i = 0; i < finalConfiguration_3_2.streams.size(); i++) {
            finalConfiguration.streams[i].v3_2 = finalConfiguration_3_2.streams[i];
            finalConfiguration.streams[i].overrideDataSpace =
                    requestedConfiguration.streams[i].dataSpace;
        }
    }

    if (status != common::V1_0::Status::OK ) {
        return CameraProviderManager::mapToStatusT(status);
    }

    // And convert output stream configuration from HIDL

    for (size_t i = 0; i < config->num_streams; i++) {
        camera3_stream_t *dst = config->streams[i];
        int streamId = Camera3Stream::cast(dst)->getId();

        // Start scan at i, with the assumption that the stream order matches
        size_t realIdx = i;
        bool found = false;
        for (size_t idx = 0; idx < finalConfiguration.streams.size(); idx++) {
            if (finalConfiguration.streams[realIdx].v3_2.id == streamId) {
                found = true;
                break;
            }
            realIdx = (realIdx >= finalConfiguration.streams.size()) ? 0 : realIdx + 1;
        }
        if (!found) {
            ALOGE("%s: Stream %d not found in stream configuration response from HAL",
                    __FUNCTION__, streamId);
            return INVALID_OPERATION;
        }
        device::V3_3::HalStream &src = finalConfiguration.streams[realIdx];

        Camera3Stream* dstStream = Camera3Stream::cast(dst);
        dstStream->setFormatOverride(false);
        dstStream->setDataSpaceOverride(false);
        int overrideFormat = mapToFrameworkFormat(src.v3_2.overrideFormat);
        android_dataspace overrideDataSpace = mapToFrameworkDataspace(src.overrideDataSpace);

        if (dst->format != HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
            if (dst->format != overrideFormat) {
                ALOGE("%s: Stream %d: Format override not allowed for format 0x%x", __FUNCTION__,
                        streamId, dst->format);
            }
            if (dst->data_space != overrideDataSpace) {
                ALOGE("%s: Stream %d: DataSpace override not allowed for format 0x%x", __FUNCTION__,
                        streamId, dst->format);
            }
        } else {
            dstStream->setFormatOverride((dst->format != overrideFormat) ? true : false);
            dstStream->setDataSpaceOverride((dst->data_space != overrideDataSpace) ? true : false);

            // Override allowed with IMPLEMENTATION_DEFINED
            dst->format = overrideFormat;
            dst->data_space = overrideDataSpace;
        }

        if (dst->stream_type == CAMERA3_STREAM_INPUT) {
            if (src.v3_2.producerUsage != 0) {
                ALOGE("%s: Stream %d: INPUT streams must have 0 for producer usage",
                        __FUNCTION__, streamId);
                return INVALID_OPERATION;
            }
            dstStream->setUsage(
                    mapConsumerToFrameworkUsage(src.v3_2.consumerUsage));
        } else {
            // OUTPUT
            if (src.v3_2.consumerUsage != 0) {
                ALOGE("%s: Stream %d: OUTPUT streams must have 0 for consumer usage",
                        __FUNCTION__, streamId);
                return INVALID_OPERATION;
            }
            dstStream->setUsage(
                    mapProducerToFrameworkUsage(src.v3_2.producerUsage));
        }
        dst->max_buffers = src.v3_2.maxBuffers;
    }

    return res;
}

void Camera3Device::HalInterface::wrapAsHidlRequest(camera3_capture_request_t* request,
        /*out*/device::V3_2::CaptureRequest* captureRequest,
        /*out*/std::vector<native_handle_t*>* handlesCreated) {
    ATRACE_CALL();
    if (captureRequest == nullptr || handlesCreated == nullptr) {
        ALOGE("%s: captureRequest (%p) and handlesCreated (%p) must not be null",
                __FUNCTION__, captureRequest, handlesCreated);
        return;
    }

    captureRequest->frameNumber = request->frame_number;

    captureRequest->fmqSettingsSize = 0;

    {
        std::lock_guard<std::mutex> lock(mInflightLock);
        if (request->input_buffer != nullptr) {
            int32_t streamId = Camera3Stream::cast(request->input_buffer->stream)->getId();
            buffer_handle_t buf = *(request->input_buffer->buffer);
            auto pair = getBufferId(buf, streamId);
            bool isNewBuffer = pair.first;
            uint64_t bufferId = pair.second;
            captureRequest->inputBuffer.streamId = streamId;
            captureRequest->inputBuffer.bufferId = bufferId;
            captureRequest->inputBuffer.buffer = (isNewBuffer) ? buf : nullptr;
            captureRequest->inputBuffer.status = BufferStatus::OK;
            native_handle_t *acquireFence = nullptr;
            if (request->input_buffer->acquire_fence != -1) {
                acquireFence = native_handle_create(1,0);
                acquireFence->data[0] = request->input_buffer->acquire_fence;
                handlesCreated->push_back(acquireFence);
            }
            captureRequest->inputBuffer.acquireFence = acquireFence;
            captureRequest->inputBuffer.releaseFence = nullptr;

            pushInflightBufferLocked(captureRequest->frameNumber, streamId,
                    request->input_buffer->buffer,
                    request->input_buffer->acquire_fence);
        } else {
            captureRequest->inputBuffer.streamId = -1;
            captureRequest->inputBuffer.bufferId = BUFFER_ID_NO_BUFFER;
        }

        captureRequest->outputBuffers.resize(request->num_output_buffers);
        for (size_t i = 0; i < request->num_output_buffers; i++) {
            const camera3_stream_buffer_t *src = request->output_buffers + i;
            StreamBuffer &dst = captureRequest->outputBuffers[i];
            int32_t streamId = Camera3Stream::cast(src->stream)->getId();
            buffer_handle_t buf = *(src->buffer);
            auto pair = getBufferId(buf, streamId);
            bool isNewBuffer = pair.first;
            dst.streamId = streamId;
            dst.bufferId = pair.second;
            dst.buffer = isNewBuffer ? buf : nullptr;
            dst.status = BufferStatus::OK;
            native_handle_t *acquireFence = nullptr;
            if (src->acquire_fence != -1) {
                acquireFence = native_handle_create(1,0);
                acquireFence->data[0] = src->acquire_fence;
                handlesCreated->push_back(acquireFence);
            }
            dst.acquireFence = acquireFence;
            dst.releaseFence = nullptr;

            pushInflightBufferLocked(captureRequest->frameNumber, streamId,
                    src->buffer, src->acquire_fence);
        }
    }
}

status_t Camera3Device::HalInterface::processBatchCaptureRequests(
        std::vector<camera3_capture_request_t*>& requests,/*out*/uint32_t* numRequestProcessed) {
    ATRACE_NAME("CameraHal::processBatchCaptureRequests");
    if (!valid()) return INVALID_OPERATION;

    hardware::hidl_vec<device::V3_2::CaptureRequest> captureRequests;
    size_t batchSize = requests.size();
    captureRequests.resize(batchSize);
    std::vector<native_handle_t*> handlesCreated;

    for (size_t i = 0; i < batchSize; i++) {
        wrapAsHidlRequest(requests[i], /*out*/&captureRequests[i], /*out*/&handlesCreated);
    }

    std::vector<device::V3_2::BufferCache> cachesToRemove;
    {
        std::lock_guard<std::mutex> lock(mBufferIdMapLock);
        for (auto& pair : mFreedBuffers) {
            // The stream might have been removed since onBufferFreed
            if (mBufferIdMaps.find(pair.first) != mBufferIdMaps.end()) {
                cachesToRemove.push_back({pair.first, pair.second});
            }
        }
        mFreedBuffers.clear();
    }

    common::V1_0::Status status = common::V1_0::Status::INTERNAL_ERROR;
    *numRequestProcessed = 0;

    // Write metadata to FMQ.
    for (size_t i = 0; i < batchSize; i++) {
        camera3_capture_request_t* request = requests[i];
        device::V3_2::CaptureRequest* captureRequest = &captureRequests[i];

        if (request->settings != nullptr) {
            size_t settingsSize = get_camera_metadata_size(request->settings);
            if (mRequestMetadataQueue != nullptr && mRequestMetadataQueue->write(
                    reinterpret_cast<const uint8_t*>(request->settings), settingsSize)) {
                captureRequest->settings.resize(0);
                captureRequest->fmqSettingsSize = settingsSize;
            } else {
                if (mRequestMetadataQueue != nullptr) {
                    ALOGW("%s: couldn't utilize fmq, fallback to hwbinder", __FUNCTION__);
                }
                captureRequest->settings.setToExternal(
                        reinterpret_cast<uint8_t*>(const_cast<camera_metadata_t*>(request->settings)),
                        get_camera_metadata_size(request->settings));
                captureRequest->fmqSettingsSize = 0u;
            }
        } else {
            // A null request settings maps to a size-0 CameraMetadata
            captureRequest->settings.resize(0);
            captureRequest->fmqSettingsSize = 0u;
        }
    }
    auto err = mHidlSession->processCaptureRequest(captureRequests, cachesToRemove,
            [&status, &numRequestProcessed] (auto s, uint32_t n) {
                status = s;
                *numRequestProcessed = n;
            });
    if (!err.isOk()) {
        ALOGE("%s: Transaction error: %s", __FUNCTION__, err.description().c_str());
        return DEAD_OBJECT;
    }
    if (status == common::V1_0::Status::OK && *numRequestProcessed != batchSize) {
        ALOGE("%s: processCaptureRequest returns OK but processed %d/%zu requests",
                __FUNCTION__, *numRequestProcessed, batchSize);
        status = common::V1_0::Status::INTERNAL_ERROR;
    }

    for (auto& handle : handlesCreated) {
        native_handle_delete(handle);
    }
    return CameraProviderManager::mapToStatusT(status);
}

status_t Camera3Device::HalInterface::processCaptureRequest(
        camera3_capture_request_t *request) {
    ATRACE_NAME("CameraHal::processCaptureRequest");
    if (!valid()) return INVALID_OPERATION;
    status_t res = OK;

    uint32_t numRequestProcessed = 0;
    std::vector<camera3_capture_request_t*> requests(1);
    requests[0] = request;
    res = processBatchCaptureRequests(requests, &numRequestProcessed);

    return res;
}

status_t Camera3Device::HalInterface::flush() {
    ATRACE_NAME("CameraHal::flush");
    if (!valid()) return INVALID_OPERATION;
    status_t res = OK;

    auto err = mHidlSession->flush();
    if (!err.isOk()) {
        ALOGE("%s: Transaction error: %s", __FUNCTION__, err.description().c_str());
        res = DEAD_OBJECT;
    } else {
        res = CameraProviderManager::mapToStatusT(err);
    }

    return res;
}

status_t Camera3Device::HalInterface::dump(int /*fd*/) {
    ATRACE_NAME("CameraHal::dump");
    if (!valid()) return INVALID_OPERATION;

    // Handled by CameraProviderManager::dump

    return OK;
}

status_t Camera3Device::HalInterface::close() {
    ATRACE_NAME("CameraHal::close()");
    if (!valid()) return INVALID_OPERATION;
    status_t res = OK;

    auto err = mHidlSession->close();
    // Interface will be dead shortly anyway, so don't log errors
    if (!err.isOk()) {
        res = DEAD_OBJECT;
    }

    return res;
}

void Camera3Device::HalInterface::getInflightBufferKeys(
        std::vector<std::pair<int32_t, int32_t>>* out) {
    std::lock_guard<std::mutex> lock(mInflightLock);
    out->clear();
    out->reserve(mInflightBufferMap.size());
    for (auto& pair : mInflightBufferMap) {
        uint64_t key = pair.first;
        int32_t streamId = key & 0xFFFFFFFF;
        int32_t frameNumber = (key >> 32) & 0xFFFFFFFF;
        out->push_back(std::make_pair(frameNumber, streamId));
    }
    return;
}

status_t Camera3Device::HalInterface::pushInflightBufferLocked(
        int32_t frameNumber, int32_t streamId, buffer_handle_t *buffer, int acquireFence) {
    uint64_t key = static_cast<uint64_t>(frameNumber) << 32 | static_cast<uint64_t>(streamId);
    auto pair = std::make_pair(buffer, acquireFence);
    mInflightBufferMap[key] = pair;
    return OK;
}

status_t Camera3Device::HalInterface::popInflightBuffer(
        int32_t frameNumber, int32_t streamId,
        /*out*/ buffer_handle_t **buffer) {
    std::lock_guard<std::mutex> lock(mInflightLock);

    uint64_t key = static_cast<uint64_t>(frameNumber) << 32 | static_cast<uint64_t>(streamId);
    auto it = mInflightBufferMap.find(key);
    if (it == mInflightBufferMap.end()) return NAME_NOT_FOUND;
    auto pair = it->second;
    *buffer = pair.first;
    int acquireFence = pair.second;
    if (acquireFence > 0) {
        ::close(acquireFence);
    }
    mInflightBufferMap.erase(it);
    return OK;
}

std::pair<bool, uint64_t> Camera3Device::HalInterface::getBufferId(
        const buffer_handle_t& buf, int streamId) {
    std::lock_guard<std::mutex> lock(mBufferIdMapLock);

    BufferIdMap& bIdMap = mBufferIdMaps.at(streamId);
    auto it = bIdMap.find(buf);
    if (it == bIdMap.end()) {
        bIdMap[buf] = mNextBufferId++;
        ALOGV("stream %d now have %zu buffer caches, buf %p",
                streamId, bIdMap.size(), buf);
        return std::make_pair(true, mNextBufferId - 1);
    } else {
        return std::make_pair(false, it->second);
    }
}

void Camera3Device::HalInterface::onBufferFreed(
        int streamId, const native_handle_t* handle) {
    std::lock_guard<std::mutex> lock(mBufferIdMapLock);
    uint64_t bufferId = BUFFER_ID_NO_BUFFER;
    auto mapIt = mBufferIdMaps.find(streamId);
    if (mapIt == mBufferIdMaps.end()) {
        // streamId might be from a deleted stream here
        ALOGI("%s: stream %d has been removed",
                __FUNCTION__, streamId);
        return;
    }
    BufferIdMap& bIdMap = mapIt->second;
    auto it = bIdMap.find(handle);
    if (it == bIdMap.end()) {
        ALOGW("%s: cannot find buffer %p in stream %d",
                __FUNCTION__, handle, streamId);
        return;
    } else {
        bufferId =  it->second;
        bIdMap.erase(it);
        ALOGV("%s: stream %d now have %zu buffer caches after removing buf %p",
                __FUNCTION__, streamId, bIdMap.size(), handle);
    }
    mFreedBuffers.push_back(std::make_pair(streamId, bufferId));
}

/**
 * RequestThread inner class methods
 */

Camera3Device::RequestThread::RequestThread(wp<Camera3Device> parent,
        sp<StatusTracker> statusTracker,
        sp<HalInterface> interface) :
        Thread(/*canCallJava*/false),
        mParent(parent),
        mStatusTracker(statusTracker),
        mInterface(interface),
        mListener(nullptr),
        mId(getId(parent)),
        mReconfigured(false),
        mDoPause(false),
        mPaused(true),
        mFrameNumber(0),
        mLatestRequestId(NAME_NOT_FOUND),
        mCurrentAfTriggerId(0),
        mCurrentPreCaptureTriggerId(0),
        mRepeatingLastFrameNumber(
            hardware::camera2::ICameraDeviceUser::NO_IN_FLIGHT_REPEATING_FRAMES),
        mPrepareVideoStream(false),
        mRequestLatency(kRequestLatencyBinSize) {
    mStatusId = statusTracker->addComponent();
}

Camera3Device::RequestThread::~RequestThread() {}

void Camera3Device::RequestThread::setNotificationListener(
        wp<NotificationListener> listener) {
    ATRACE_CALL();
    Mutex::Autolock l(mRequestLock);
    mListener = listener;
}

void Camera3Device::RequestThread::configurationComplete(bool isConstrainedHighSpeed) {
    ATRACE_CALL();
    Mutex::Autolock l(mRequestLock);
    mReconfigured = true;
    // Prepare video stream for high speed recording.
    mPrepareVideoStream = isConstrainedHighSpeed;
}

status_t Camera3Device::RequestThread::queueRequestList(
        List<sp<CaptureRequest> > &requests,
        /*out*/
        int64_t *lastFrameNumber) {
    ATRACE_CALL();
    Mutex::Autolock l(mRequestLock);
    for (List<sp<CaptureRequest> >::iterator it = requests.begin(); it != requests.end();
            ++it) {
        mRequestQueue.push_back(*it);
    }

    if (lastFrameNumber != NULL) {
        *lastFrameNumber = mFrameNumber + mRequestQueue.size() - 1;
        ALOGV("%s: requestId %d, mFrameNumber %" PRId32 ", lastFrameNumber %" PRId64 ".",
              __FUNCTION__, (*(requests.begin()))->mResultExtras.requestId, mFrameNumber,
              *lastFrameNumber);
    }

    unpauseForNewRequests();

    return OK;
}


status_t Camera3Device::RequestThread::queueTrigger(
        RequestTrigger trigger[],
        size_t count) {
    ATRACE_CALL();
    Mutex::Autolock l(mTriggerMutex);
    status_t ret;

    for (size_t i = 0; i < count; ++i) {
        ret = queueTriggerLocked(trigger[i]);

        if (ret != OK) {
            return ret;
        }
    }

    return OK;
}

const String8& Camera3Device::RequestThread::getId(const wp<Camera3Device> &device) {
    static String8 deadId("<DeadDevice>");
    sp<Camera3Device> d = device.promote();
    if (d != nullptr) return d->mId;
    return deadId;
}

status_t Camera3Device::RequestThread::queueTriggerLocked(
        RequestTrigger trigger) {

    uint32_t tag = trigger.metadataTag;
    ssize_t index = mTriggerMap.indexOfKey(tag);

    switch (trigger.getTagType()) {
        case TYPE_BYTE:
        // fall-through
        case TYPE_INT32:
            break;
        default:
            ALOGE("%s: Type not supported: 0x%x", __FUNCTION__,
                    trigger.getTagType());
            return INVALID_OPERATION;
    }

    /**
     * Collect only the latest trigger, since we only have 1 field
     * in the request settings per trigger tag, and can't send more than 1
     * trigger per request.
     */
    if (index != NAME_NOT_FOUND) {
        mTriggerMap.editValueAt(index) = trigger;
    } else {
        mTriggerMap.add(tag, trigger);
    }

    return OK;
}

status_t Camera3Device::RequestThread::setRepeatingRequests(
        const RequestList &requests,
        /*out*/
        int64_t *lastFrameNumber) {
    ATRACE_CALL();
    Mutex::Autolock l(mRequestLock);
    if (lastFrameNumber != NULL) {
        *lastFrameNumber = mRepeatingLastFrameNumber;
    }
    mRepeatingRequests.clear();
    mRepeatingRequests.insert(mRepeatingRequests.begin(),
            requests.begin(), requests.end());

    unpauseForNewRequests();

    mRepeatingLastFrameNumber = hardware::camera2::ICameraDeviceUser::NO_IN_FLIGHT_REPEATING_FRAMES;
    return OK;
}

bool Camera3Device::RequestThread::isRepeatingRequestLocked(const sp<CaptureRequest>& requestIn) {
    if (mRepeatingRequests.empty()) {
        return false;
    }
    int32_t requestId = requestIn->mResultExtras.requestId;
    const RequestList &repeatRequests = mRepeatingRequests;
    // All repeating requests are guaranteed to have same id so only check first quest
    const sp<CaptureRequest> firstRequest = *repeatRequests.begin();
    return (firstRequest->mResultExtras.requestId == requestId);
}

status_t Camera3Device::RequestThread::clearRepeatingRequests(/*out*/int64_t *lastFrameNumber) {
    ATRACE_CALL();
    Mutex::Autolock l(mRequestLock);
    return clearRepeatingRequestsLocked(lastFrameNumber);

}

status_t Camera3Device::RequestThread::clearRepeatingRequestsLocked(/*out*/int64_t *lastFrameNumber) {
    mRepeatingRequests.clear();
    if (lastFrameNumber != NULL) {
        *lastFrameNumber = mRepeatingLastFrameNumber;
    }
    mRepeatingLastFrameNumber = hardware::camera2::ICameraDeviceUser::NO_IN_FLIGHT_REPEATING_FRAMES;
    return OK;
}

status_t Camera3Device::RequestThread::clear(
        /*out*/int64_t *lastFrameNumber) {
    ATRACE_CALL();
    Mutex::Autolock l(mRequestLock);
    ALOGV("RequestThread::%s:", __FUNCTION__);

    mRepeatingRequests.clear();

    // Send errors for all requests pending in the request queue, including
    // pending repeating requests
    sp<NotificationListener> listener = mListener.promote();
    if (listener != NULL) {
        for (RequestList::iterator it = mRequestQueue.begin();
                 it != mRequestQueue.end(); ++it) {
            // Abort the input buffers for reprocess requests.
            if ((*it)->mInputStream != NULL) {
                camera3_stream_buffer_t inputBuffer;
                status_t res = (*it)->mInputStream->getInputBuffer(&inputBuffer,
                        /*respectHalLimit*/ false);
                if (res != OK) {
                    ALOGW("%s: %d: couldn't get input buffer while clearing the request "
                            "list: %s (%d)", __FUNCTION__, __LINE__, strerror(-res), res);
                } else {
                    res = (*it)->mInputStream->returnInputBuffer(inputBuffer);
                    if (res != OK) {
                        ALOGE("%s: %d: couldn't return input buffer while clearing the request "
                                "list: %s (%d)", __FUNCTION__, __LINE__, strerror(-res), res);
                    }
                }
            }
            // Set the frame number this request would have had, if it
            // had been submitted; this frame number will not be reused.
            // The requestId and burstId fields were set when the request was
            // submitted originally (in convertMetadataListToRequestListLocked)
            (*it)->mResultExtras.frameNumber = mFrameNumber++;
            listener->notifyError(hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_REQUEST,
                    (*it)->mResultExtras);
        }
    }
    mRequestQueue.clear();

    Mutex::Autolock al(mTriggerMutex);
    mTriggerMap.clear();
    if (lastFrameNumber != NULL) {
        *lastFrameNumber = mRepeatingLastFrameNumber;
    }
    mRepeatingLastFrameNumber = hardware::camera2::ICameraDeviceUser::NO_IN_FLIGHT_REPEATING_FRAMES;
    return OK;
}

status_t Camera3Device::RequestThread::flush() {
    ATRACE_CALL();
    Mutex::Autolock l(mFlushLock);

    return mInterface->flush();
}

void Camera3Device::RequestThread::setPaused(bool paused) {
    ATRACE_CALL();
    Mutex::Autolock l(mPauseLock);
    mDoPause = paused;
    mDoPauseSignal.signal();
}

status_t Camera3Device::RequestThread::waitUntilRequestProcessed(
        int32_t requestId, nsecs_t timeout) {
    ATRACE_CALL();
    Mutex::Autolock l(mLatestRequestMutex);
    status_t res;
    while (mLatestRequestId != requestId) {
        nsecs_t startTime = systemTime();

        res = mLatestRequestSignal.waitRelative(mLatestRequestMutex, timeout);
        if (res != OK) return res;

        timeout -= (systemTime() - startTime);
    }

    return OK;
}

void Camera3Device::RequestThread::requestExit() {
    // Call parent to set up shutdown
    Thread::requestExit();
    // The exit from any possible waits
    mDoPauseSignal.signal();
    mRequestSignal.signal();

    mRequestLatency.log("ProcessCaptureRequest latency histogram");
    mRequestLatency.reset();
}

void Camera3Device::RequestThread::checkAndStopRepeatingRequest() {
    ATRACE_CALL();
    bool surfaceAbandoned = false;
    int64_t lastFrameNumber = 0;
    sp<NotificationListener> listener;
    {
        Mutex::Autolock l(mRequestLock);
        // Check all streams needed by repeating requests are still valid. Otherwise, stop
        // repeating requests.
        for (const auto& request : mRepeatingRequests) {
            for (const auto& s : request->mOutputStreams) {
                if (s->isAbandoned()) {
                    surfaceAbandoned = true;
                    clearRepeatingRequestsLocked(&lastFrameNumber);
                    break;
                }
            }
            if (surfaceAbandoned) {
                break;
            }
        }
        listener = mListener.promote();
    }

    if (listener != NULL && surfaceAbandoned) {
        listener->notifyRepeatingRequestError(lastFrameNumber);
    }
}

bool Camera3Device::RequestThread::sendRequestsBatch() {
    ATRACE_CALL();
    status_t res;
    size_t batchSize = mNextRequests.size();
    std::vector<camera3_capture_request_t*> requests(batchSize);
    uint32_t numRequestProcessed = 0;
    for (size_t i = 0; i < batchSize; i++) {
        requests[i] = &mNextRequests.editItemAt(i).halRequest;
    }

    ATRACE_ASYNC_BEGIN("batch frame capture", mNextRequests[0].halRequest.frame_number);
    res = mInterface->processBatchCaptureRequests(requests, &numRequestProcessed);

    bool triggerRemoveFailed = false;
    NextRequest& triggerFailedRequest = mNextRequests.editItemAt(0);
    for (size_t i = 0; i < numRequestProcessed; i++) {
        NextRequest& nextRequest = mNextRequests.editItemAt(i);
        nextRequest.submitted = true;


        // Update the latest request sent to HAL
        if (nextRequest.halRequest.settings != NULL) { // Don't update if they were unchanged
            Mutex::Autolock al(mLatestRequestMutex);

            camera_metadata_t* cloned = clone_camera_metadata(nextRequest.halRequest.settings);
            mLatestRequest.acquire(cloned);

            sp<Camera3Device> parent = mParent.promote();
            if (parent != NULL) {
                parent->monitorMetadata(TagMonitor::REQUEST,
                        nextRequest.halRequest.frame_number,
                        0, mLatestRequest);
            }
        }

        if (nextRequest.halRequest.settings != NULL) {
            nextRequest.captureRequest->mSettings.unlock(nextRequest.halRequest.settings);
        }

        if (!triggerRemoveFailed) {
            // Remove any previously queued triggers (after unlock)
            status_t removeTriggerRes = removeTriggers(mPrevRequest);
            if (removeTriggerRes != OK) {
                triggerRemoveFailed = true;
                triggerFailedRequest = nextRequest;
            }
        }
    }

    if (triggerRemoveFailed) {
        SET_ERR("RequestThread: Unable to remove triggers "
              "(capture request %d, HAL device: %s (%d)",
              triggerFailedRequest.halRequest.frame_number, strerror(-res), res);
        cleanUpFailedRequests(/*sendRequestError*/ false);
        return false;
    }

    if (res != OK) {
        // Should only get a failure here for malformed requests or device-level
        // errors, so consider all errors fatal.  Bad metadata failures should
        // come through notify.
        SET_ERR("RequestThread: Unable to submit capture request %d to HAL device: %s (%d)",
                mNextRequests[numRequestProcessed].halRequest.frame_number,
                strerror(-res), res);
        cleanUpFailedRequests(/*sendRequestError*/ false);
        return false;
    }
    return true;
}

bool Camera3Device::RequestThread::sendRequestsOneByOne() {
    status_t res;

    for (auto& nextRequest : mNextRequests) {
        // Submit request and block until ready for next one
        ATRACE_ASYNC_BEGIN("frame capture", nextRequest.halRequest.frame_number);
        res = mInterface->processCaptureRequest(&nextRequest.halRequest);

        if (res != OK) {
            // Should only get a failure here for malformed requests or device-level
            // errors, so consider all errors fatal.  Bad metadata failures should
            // come through notify.
            SET_ERR("RequestThread: Unable to submit capture request %d to HAL"
                    " device: %s (%d)", nextRequest.halRequest.frame_number, strerror(-res),
                    res);
            cleanUpFailedRequests(/*sendRequestError*/ false);
            return false;
        }

        // Mark that the request has be submitted successfully.
        nextRequest.submitted = true;

        // Update the latest request sent to HAL
        if (nextRequest.halRequest.settings != NULL) { // Don't update if they were unchanged
            Mutex::Autolock al(mLatestRequestMutex);

            camera_metadata_t* cloned = clone_camera_metadata(nextRequest.halRequest.settings);
            mLatestRequest.acquire(cloned);

            sp<Camera3Device> parent = mParent.promote();
            if (parent != NULL) {
                parent->monitorMetadata(TagMonitor::REQUEST, nextRequest.halRequest.frame_number,
                        0, mLatestRequest);
            }
        }

        if (nextRequest.halRequest.settings != NULL) {
            nextRequest.captureRequest->mSettings.unlock(nextRequest.halRequest.settings);
        }

        // Remove any previously queued triggers (after unlock)
        res = removeTriggers(mPrevRequest);
        if (res != OK) {
            SET_ERR("RequestThread: Unable to remove triggers "
                  "(capture request %d, HAL device: %s (%d)",
                  nextRequest.halRequest.frame_number, strerror(-res), res);
            cleanUpFailedRequests(/*sendRequestError*/ false);
            return false;
        }
    }
    return true;
}

nsecs_t Camera3Device::RequestThread::calculateMaxExpectedDuration(const camera_metadata_t *request) {
    nsecs_t maxExpectedDuration = kDefaultExpectedDuration;
    camera_metadata_ro_entry_t e = camera_metadata_ro_entry_t();
    find_camera_metadata_ro_entry(request,
            ANDROID_CONTROL_AE_MODE,
            &e);
    if (e.count == 0) return maxExpectedDuration;

    switch (e.data.u8[0]) {
        case ANDROID_CONTROL_AE_MODE_OFF:
            find_camera_metadata_ro_entry(request,
                    ANDROID_SENSOR_EXPOSURE_TIME,
                    &e);
            if (e.count > 0) {
                maxExpectedDuration = e.data.i64[0];
            }
            find_camera_metadata_ro_entry(request,
                    ANDROID_SENSOR_FRAME_DURATION,
                    &e);
            if (e.count > 0) {
                maxExpectedDuration = std::max(e.data.i64[0], maxExpectedDuration);
            }
            break;
        default:
            find_camera_metadata_ro_entry(request,
                    ANDROID_CONTROL_AE_TARGET_FPS_RANGE,
                    &e);
            if (e.count > 1) {
                maxExpectedDuration = 1e9 / e.data.u8[0];
            }
            break;
    }

    return maxExpectedDuration;
}

bool Camera3Device::RequestThread::threadLoop() {
    ATRACE_CALL();
    status_t res;

    // Handle paused state.
    if (waitIfPaused()) {
        return true;
    }

    // Wait for the next batch of requests.
    waitForNextRequestBatch();
    if (mNextRequests.size() == 0) {
        return true;
    }

    // Get the latest request ID, if any
    int latestRequestId;
    camera_metadata_entry_t requestIdEntry = mNextRequests[mNextRequests.size() - 1].
            captureRequest->mSettings.find(ANDROID_REQUEST_ID);
    if (requestIdEntry.count > 0) {
        latestRequestId = requestIdEntry.data.i32[0];
    } else {
        ALOGW("%s: Did not have android.request.id set in the request.", __FUNCTION__);
        latestRequestId = NAME_NOT_FOUND;
    }

    // Prepare a batch of HAL requests and output buffers.
    res = prepareHalRequests();
    if (res == TIMED_OUT) {
        // Not a fatal error if getting output buffers time out.
        cleanUpFailedRequests(/*sendRequestError*/ true);
        // Check if any stream is abandoned.
        checkAndStopRepeatingRequest();
        return true;
    } else if (res != OK) {
        cleanUpFailedRequests(/*sendRequestError*/ false);
        return false;
    }

    // Inform waitUntilRequestProcessed thread of a new request ID
    {
        Mutex::Autolock al(mLatestRequestMutex);

        mLatestRequestId = latestRequestId;
        mLatestRequestSignal.signal();
    }

    // Submit a batch of requests to HAL.
    // Use flush lock only when submitting multilple requests in a batch.
    // TODO: The problem with flush lock is flush() will be blocked by process_capture_request()
    // which may take a long time to finish so synchronizing flush() and
    // process_capture_request() defeats the purpose of cancelling requests ASAP with flush().
    // For now, only synchronize for high speed recording and we should figure something out for
    // removing the synchronization.
    bool useFlushLock = mNextRequests.size() > 1;

    if (useFlushLock) {
        mFlushLock.lock();
    }

    ALOGVV("%s: %d: submitting %zu requests in a batch.", __FUNCTION__, __LINE__,
            mNextRequests.size());

    bool submitRequestSuccess = false;
    nsecs_t tRequestStart = systemTime(SYSTEM_TIME_MONOTONIC);
    if (mInterface->supportBatchRequest()) {
        submitRequestSuccess = sendRequestsBatch();
    } else {
        submitRequestSuccess = sendRequestsOneByOne();
    }
    nsecs_t tRequestEnd = systemTime(SYSTEM_TIME_MONOTONIC);
    mRequestLatency.add(tRequestStart, tRequestEnd);

    if (useFlushLock) {
        mFlushLock.unlock();
    }

    // Unset as current request
    {
        Mutex::Autolock l(mRequestLock);
        mNextRequests.clear();
    }

    return submitRequestSuccess;
}

status_t Camera3Device::RequestThread::prepareHalRequests() {
    ATRACE_CALL();

    for (size_t i = 0; i < mNextRequests.size(); i++) {
        auto& nextRequest = mNextRequests.editItemAt(i);
        sp<CaptureRequest> captureRequest = nextRequest.captureRequest;
        camera3_capture_request_t* halRequest = &nextRequest.halRequest;
        Vector<camera3_stream_buffer_t>* outputBuffers = &nextRequest.outputBuffers;

        // Prepare a request to HAL
        halRequest->frame_number = captureRequest->mResultExtras.frameNumber;

        // Insert any queued triggers (before metadata is locked)
        status_t res = insertTriggers(captureRequest);

        if (res < 0) {
            SET_ERR("RequestThread: Unable to insert triggers "
                    "(capture request %d, HAL device: %s (%d)",
                    halRequest->frame_number, strerror(-res), res);
            return INVALID_OPERATION;
        }
        int triggerCount = res;
        bool triggersMixedIn = (triggerCount > 0 || mPrevTriggers > 0);
        mPrevTriggers = triggerCount;

        // If the request is the same as last, or we had triggers last time
        if (mPrevRequest != captureRequest || triggersMixedIn) {
            /**
             * HAL workaround:
             * Insert a dummy trigger ID if a trigger is set but no trigger ID is
             */
            res = addDummyTriggerIds(captureRequest);
            if (res != OK) {
                SET_ERR("RequestThread: Unable to insert dummy trigger IDs "
                        "(capture request %d, HAL device: %s (%d)",
                        halRequest->frame_number, strerror(-res), res);
                return INVALID_OPERATION;
            }

            /**
             * The request should be presorted so accesses in HAL
             *   are O(logn). Sidenote, sorting a sorted metadata is nop.
             */
            captureRequest->mSettings.sort();
            halRequest->settings = captureRequest->mSettings.getAndLock();
            mPrevRequest = captureRequest;
            ALOGVV("%s: Request settings are NEW", __FUNCTION__);

            IF_ALOGV() {
                camera_metadata_ro_entry_t e = camera_metadata_ro_entry_t();
                find_camera_metadata_ro_entry(
                        halRequest->settings,
                        ANDROID_CONTROL_AF_TRIGGER,
                        &e
                );
                if (e.count > 0) {
                    ALOGV("%s: Request (frame num %d) had AF trigger 0x%x",
                          __FUNCTION__,
                          halRequest->frame_number,
                          e.data.u8[0]);
                }
            }
        } else {
            // leave request.settings NULL to indicate 'reuse latest given'
            ALOGVV("%s: Request settings are REUSED",
                   __FUNCTION__);
        }

        uint32_t totalNumBuffers = 0;

        // Fill in buffers
        if (captureRequest->mInputStream != NULL) {
            halRequest->input_buffer = &captureRequest->mInputBuffer;
            totalNumBuffers += 1;
        } else {
            halRequest->input_buffer = NULL;
        }

        outputBuffers->insertAt(camera3_stream_buffer_t(), 0,
                captureRequest->mOutputStreams.size());
        halRequest->output_buffers = outputBuffers->array();
        for (size_t j = 0; j < captureRequest->mOutputStreams.size(); j++) {
            sp<Camera3OutputStreamInterface> outputStream = captureRequest->mOutputStreams.editItemAt(j);

            // Prepare video buffers for high speed recording on the first video request.
            if (mPrepareVideoStream && outputStream->isVideoStream()) {
                // Only try to prepare video stream on the first video request.
                mPrepareVideoStream = false;

                res = outputStream->startPrepare(Camera3StreamInterface::ALLOCATE_PIPELINE_MAX);
                while (res == NOT_ENOUGH_DATA) {
                    res = outputStream->prepareNextBuffer();
                }
                if (res != OK) {
                    ALOGW("%s: Preparing video buffers for high speed failed: %s (%d)",
                        __FUNCTION__, strerror(-res), res);
                    outputStream->cancelPrepare();
                }
            }

            res = outputStream->getBuffer(&outputBuffers->editItemAt(j),
                    captureRequest->mOutputSurfaces[j]);
            if (res != OK) {
                // Can't get output buffer from gralloc queue - this could be due to
                // abandoned queue or other consumer misbehavior, so not a fatal
                // error
                ALOGE("RequestThread: Can't get output buffer, skipping request:"
                        " %s (%d)", strerror(-res), res);

                return TIMED_OUT;
            }
            halRequest->num_output_buffers++;

        }
        totalNumBuffers += halRequest->num_output_buffers;

        // Log request in the in-flight queue
        sp<Camera3Device> parent = mParent.promote();
        if (parent == NULL) {
            // Should not happen, and nowhere to send errors to, so just log it
            CLOGE("RequestThread: Parent is gone");
            return INVALID_OPERATION;
        }

        // If this request list is for constrained high speed recording (not
        // preview), and the current request is not the last one in the batch,
        // do not send callback to the app.
        bool hasCallback = true;
        if (mNextRequests[0].captureRequest->mBatchSize > 1 && i != mNextRequests.size()-1) {
            hasCallback = false;
        }
        res = parent->registerInFlight(halRequest->frame_number,
                totalNumBuffers, captureRequest->mResultExtras,
                /*hasInput*/halRequest->input_buffer != NULL,
                hasCallback,
                calculateMaxExpectedDuration(halRequest->settings));
        ALOGVV("%s: registered in flight requestId = %" PRId32 ", frameNumber = %" PRId64
               ", burstId = %" PRId32 ".",
                __FUNCTION__,
                captureRequest->mResultExtras.requestId, captureRequest->mResultExtras.frameNumber,
                captureRequest->mResultExtras.burstId);
        if (res != OK) {
            SET_ERR("RequestThread: Unable to register new in-flight request:"
                    " %s (%d)", strerror(-res), res);
            return INVALID_OPERATION;
        }
    }

    return OK;
}

CameraMetadata Camera3Device::RequestThread::getLatestRequest() const {
    ATRACE_CALL();
    Mutex::Autolock al(mLatestRequestMutex);

    ALOGV("RequestThread::%s", __FUNCTION__);

    return mLatestRequest;
}

bool Camera3Device::RequestThread::isStreamPending(
        sp<Camera3StreamInterface>& stream) {
    ATRACE_CALL();
    Mutex::Autolock l(mRequestLock);

    for (const auto& nextRequest : mNextRequests) {
        if (!nextRequest.submitted) {
            for (const auto& s : nextRequest.captureRequest->mOutputStreams) {
                if (stream == s) return true;
            }
            if (stream == nextRequest.captureRequest->mInputStream) return true;
        }
    }

    for (const auto& request : mRequestQueue) {
        for (const auto& s : request->mOutputStreams) {
            if (stream == s) return true;
        }
        if (stream == request->mInputStream) return true;
    }

    for (const auto& request : mRepeatingRequests) {
        for (const auto& s : request->mOutputStreams) {
            if (stream == s) return true;
        }
        if (stream == request->mInputStream) return true;
    }

    return false;
}

nsecs_t Camera3Device::getExpectedInFlightDuration() {
    ATRACE_CALL();
    Mutex::Autolock al(mInFlightLock);
    return mExpectedInflightDuration > kMinInflightDuration ?
            mExpectedInflightDuration : kMinInflightDuration;
}

void Camera3Device::RequestThread::cleanUpFailedRequests(bool sendRequestError) {
    if (mNextRequests.empty()) {
        return;
    }

    for (auto& nextRequest : mNextRequests) {
        // Skip the ones that have been submitted successfully.
        if (nextRequest.submitted) {
            continue;
        }

        sp<CaptureRequest> captureRequest = nextRequest.captureRequest;
        camera3_capture_request_t* halRequest = &nextRequest.halRequest;
        Vector<camera3_stream_buffer_t>* outputBuffers = &nextRequest.outputBuffers;

        if (halRequest->settings != NULL) {
            captureRequest->mSettings.unlock(halRequest->settings);
        }

        if (captureRequest->mInputStream != NULL) {
            captureRequest->mInputBuffer.status = CAMERA3_BUFFER_STATUS_ERROR;
            captureRequest->mInputStream->returnInputBuffer(captureRequest->mInputBuffer);
        }

        for (size_t i = 0; i < halRequest->num_output_buffers; i++) {
            //Buffers that failed processing could still have
            //valid acquire fence.
            int acquireFence = (*outputBuffers)[i].acquire_fence;
            if (0 <= acquireFence) {
                close(acquireFence);
                outputBuffers->editItemAt(i).acquire_fence = -1;
            }
            outputBuffers->editItemAt(i).status = CAMERA3_BUFFER_STATUS_ERROR;
            captureRequest->mOutputStreams.editItemAt(i)->returnBuffer((*outputBuffers)[i], 0);
        }

        if (sendRequestError) {
            Mutex::Autolock l(mRequestLock);
            sp<NotificationListener> listener = mListener.promote();
            if (listener != NULL) {
                listener->notifyError(
                        hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_REQUEST,
                        captureRequest->mResultExtras);
            }
        }

        // Remove yet-to-be submitted inflight request from inflightMap
        {
          sp<Camera3Device> parent = mParent.promote();
          if (parent != NULL) {
              Mutex::Autolock l(parent->mInFlightLock);
              ssize_t idx = parent->mInFlightMap.indexOfKey(captureRequest->mResultExtras.frameNumber);
              if (idx >= 0) {
                  ALOGV("%s: Remove inflight request from queue: frameNumber %" PRId64,
                        __FUNCTION__, captureRequest->mResultExtras.frameNumber);
                  parent->removeInFlightMapEntryLocked(idx);
              }
          }
        }
    }

    Mutex::Autolock l(mRequestLock);
    mNextRequests.clear();
}

void Camera3Device::RequestThread::waitForNextRequestBatch() {
    ATRACE_CALL();
    // Optimized a bit for the simple steady-state case (single repeating
    // request), to avoid putting that request in the queue temporarily.
    Mutex::Autolock l(mRequestLock);

    assert(mNextRequests.empty());

    NextRequest nextRequest;
    nextRequest.captureRequest = waitForNextRequestLocked();
    if (nextRequest.captureRequest == nullptr) {
        return;
    }

    nextRequest.halRequest = camera3_capture_request_t();
    nextRequest.submitted = false;
    mNextRequests.add(nextRequest);

    // Wait for additional requests
    const size_t batchSize = nextRequest.captureRequest->mBatchSize;

    for (size_t i = 1; i < batchSize; i++) {
        NextRequest additionalRequest;
        additionalRequest.captureRequest = waitForNextRequestLocked();
        if (additionalRequest.captureRequest == nullptr) {
            break;
        }

        additionalRequest.halRequest = camera3_capture_request_t();
        additionalRequest.submitted = false;
        mNextRequests.add(additionalRequest);
    }

    if (mNextRequests.size() < batchSize) {
        ALOGE("RequestThread: only get %zu out of %zu requests. Skipping requests.",
                mNextRequests.size(), batchSize);
        cleanUpFailedRequests(/*sendRequestError*/true);
    }

    return;
}

sp<Camera3Device::CaptureRequest>
        Camera3Device::RequestThread::waitForNextRequestLocked() {
    status_t res;
    sp<CaptureRequest> nextRequest;

    while (mRequestQueue.empty()) {
        if (!mRepeatingRequests.empty()) {
            // Always atomically enqueue all requests in a repeating request
            // list. Guarantees a complete in-sequence set of captures to
            // application.
            const RequestList &requests = mRepeatingRequests;
            RequestList::const_iterator firstRequest =
                    requests.begin();
            nextRequest = *firstRequest;
            mRequestQueue.insert(mRequestQueue.end(),
                    ++firstRequest,
                    requests.end());
            // No need to wait any longer

            mRepeatingLastFrameNumber = mFrameNumber + requests.size() - 1;

            break;
        }

        res = mRequestSignal.waitRelative(mRequestLock, kRequestTimeout);

        if ((mRequestQueue.empty() && mRepeatingRequests.empty()) ||
                exitPending()) {
            Mutex::Autolock pl(mPauseLock);
            if (mPaused == false) {
                ALOGV("%s: RequestThread: Going idle", __FUNCTION__);
                mPaused = true;
                // Let the tracker know
                sp<StatusTracker> statusTracker = mStatusTracker.promote();
                if (statusTracker != 0) {
                    statusTracker->markComponentIdle(mStatusId, Fence::NO_FENCE);
                }
            }
            // Stop waiting for now and let thread management happen
            return NULL;
        }
    }

    if (nextRequest == NULL) {
        // Don't have a repeating request already in hand, so queue
        // must have an entry now.
        RequestList::iterator firstRequest =
                mRequestQueue.begin();
        nextRequest = *firstRequest;
        mRequestQueue.erase(firstRequest);
        if (mRequestQueue.empty() && !nextRequest->mRepeating) {
            sp<NotificationListener> listener = mListener.promote();
            if (listener != NULL) {
                listener->notifyRequestQueueEmpty();
            }
        }
    }

    // In case we've been unpaused by setPaused clearing mDoPause, need to
    // update internal pause state (capture/setRepeatingRequest unpause
    // directly).
    Mutex::Autolock pl(mPauseLock);
    if (mPaused) {
        ALOGV("%s: RequestThread: Unpaused", __FUNCTION__);
        sp<StatusTracker> statusTracker = mStatusTracker.promote();
        if (statusTracker != 0) {
            statusTracker->markComponentActive(mStatusId);
        }
    }
    mPaused = false;

    // Check if we've reconfigured since last time, and reset the preview
    // request if so. Can't use 'NULL request == repeat' across configure calls.
    if (mReconfigured) {
        mPrevRequest.clear();
        mReconfigured = false;
    }

    if (nextRequest != NULL) {
        nextRequest->mResultExtras.frameNumber = mFrameNumber++;
        nextRequest->mResultExtras.afTriggerId = mCurrentAfTriggerId;
        nextRequest->mResultExtras.precaptureTriggerId = mCurrentPreCaptureTriggerId;

        // Since RequestThread::clear() removes buffers from the input stream,
        // get the right buffer here before unlocking mRequestLock
        if (nextRequest->mInputStream != NULL) {
            res = nextRequest->mInputStream->getInputBuffer(&nextRequest->mInputBuffer);
            if (res != OK) {
                // Can't get input buffer from gralloc queue - this could be due to
                // disconnected queue or other producer misbehavior, so not a fatal
                // error
                ALOGE("%s: Can't get input buffer, skipping request:"
                        " %s (%d)", __FUNCTION__, strerror(-res), res);

                sp<NotificationListener> listener = mListener.promote();
                if (listener != NULL) {
                    listener->notifyError(
                            hardware::camera2::ICameraDeviceCallbacks::ERROR_CAMERA_REQUEST,
                            nextRequest->mResultExtras);
                }
                return NULL;
            }
        }
    }

    return nextRequest;
}

bool Camera3Device::RequestThread::waitIfPaused() {
    ATRACE_CALL();
    status_t res;
    Mutex::Autolock l(mPauseLock);
    while (mDoPause) {
        if (mPaused == false) {
            mPaused = true;
            ALOGV("%s: RequestThread: Paused", __FUNCTION__);
            // Let the tracker know
            sp<StatusTracker> statusTracker = mStatusTracker.promote();
            if (statusTracker != 0) {
                statusTracker->markComponentIdle(mStatusId, Fence::NO_FENCE);
            }
        }

        res = mDoPauseSignal.waitRelative(mPauseLock, kRequestTimeout);
        if (res == TIMED_OUT || exitPending()) {
            return true;
        }
    }
    // We don't set mPaused to false here, because waitForNextRequest needs
    // to further manage the paused state in case of starvation.
    return false;
}

void Camera3Device::RequestThread::unpauseForNewRequests() {
    ATRACE_CALL();
    // With work to do, mark thread as unpaused.
    // If paused by request (setPaused), don't resume, to avoid
    // extra signaling/waiting overhead to waitUntilPaused
    mRequestSignal.signal();
    Mutex::Autolock p(mPauseLock);
    if (!mDoPause) {
        ALOGV("%s: RequestThread: Going active", __FUNCTION__);
        if (mPaused) {
            sp<StatusTracker> statusTracker = mStatusTracker.promote();
            if (statusTracker != 0) {
                statusTracker->markComponentActive(mStatusId);
            }
        }
        mPaused = false;
    }
}

void Camera3Device::RequestThread::setErrorState(const char *fmt, ...) {
    sp<Camera3Device> parent = mParent.promote();
    if (parent != NULL) {
        va_list args;
        va_start(args, fmt);

        parent->setErrorStateV(fmt, args);

        va_end(args);
    }
}

status_t Camera3Device::RequestThread::insertTriggers(
        const sp<CaptureRequest> &request) {
    ATRACE_CALL();
    Mutex::Autolock al(mTriggerMutex);

    sp<Camera3Device> parent = mParent.promote();
    if (parent == NULL) {
        CLOGE("RequestThread: Parent is gone");
        return DEAD_OBJECT;
    }

    CameraMetadata &metadata = request->mSettings;
    size_t count = mTriggerMap.size();

    for (size_t i = 0; i < count; ++i) {
        RequestTrigger trigger = mTriggerMap.valueAt(i);
        uint32_t tag = trigger.metadataTag;

        if (tag == ANDROID_CONTROL_AF_TRIGGER_ID || tag == ANDROID_CONTROL_AE_PRECAPTURE_ID) {
            bool isAeTrigger = (trigger.metadataTag == ANDROID_CONTROL_AE_PRECAPTURE_ID);
            uint32_t triggerId = static_cast<uint32_t>(trigger.entryValue);
            if (isAeTrigger) {
                request->mResultExtras.precaptureTriggerId = triggerId;
                mCurrentPreCaptureTriggerId = triggerId;
            } else {
                request->mResultExtras.afTriggerId = triggerId;
                mCurrentAfTriggerId = triggerId;
            }
            continue;
        }

        camera_metadata_entry entry = metadata.find(tag);

        if (entry.count > 0) {
            /**
             * Already has an entry for this trigger in the request.
             * Rewrite it with our requested trigger value.
             */
            RequestTrigger oldTrigger = trigger;

            oldTrigger.entryValue = entry.data.u8[0];

            mTriggerReplacedMap.add(tag, oldTrigger);
        } else {
            /**
             * More typical, no trigger entry, so we just add it
             */
            mTriggerRemovedMap.add(tag, trigger);
        }

        status_t res;

        switch (trigger.getTagType()) {
            case TYPE_BYTE: {
                uint8_t entryValue = static_cast<uint8_t>(trigger.entryValue);
                res = metadata.update(tag,
                                      &entryValue,
                                      /*count*/1);
                break;
            }
            case TYPE_INT32:
                res = metadata.update(tag,
                                      &trigger.entryValue,
                                      /*count*/1);
                break;
            default:
                ALOGE("%s: Type not supported: 0x%x",
                      __FUNCTION__,
                      trigger.getTagType());
                return INVALID_OPERATION;
        }

        if (res != OK) {
            ALOGE("%s: Failed to update request metadata with trigger tag %s"
                  ", value %d", __FUNCTION__, trigger.getTagName(),
                  trigger.entryValue);
            return res;
        }

        ALOGV("%s: Mixed in trigger %s, value %d", __FUNCTION__,
              trigger.getTagName(),
              trigger.entryValue);
    }

    mTriggerMap.clear();

    return count;
}

status_t Camera3Device::RequestThread::removeTriggers(
        const sp<CaptureRequest> &request) {
    ATRACE_CALL();
    Mutex::Autolock al(mTriggerMutex);

    CameraMetadata &metadata = request->mSettings;

    /**
     * Replace all old entries with their old values.
     */
    for (size_t i = 0; i < mTriggerReplacedMap.size(); ++i) {
        RequestTrigger trigger = mTriggerReplacedMap.valueAt(i);

        status_t res;

        uint32_t tag = trigger.metadataTag;
        switch (trigger.getTagType()) {
            case TYPE_BYTE: {
                uint8_t entryValue = static_cast<uint8_t>(trigger.entryValue);
                res = metadata.update(tag,
                                      &entryValue,
                                      /*count*/1);
                break;
            }
            case TYPE_INT32:
                res = metadata.update(tag,
                                      &trigger.entryValue,
                                      /*count*/1);
                break;
            default:
                ALOGE("%s: Type not supported: 0x%x",
                      __FUNCTION__,
                      trigger.getTagType());
                return INVALID_OPERATION;
        }

        if (res != OK) {
            ALOGE("%s: Failed to restore request metadata with trigger tag %s"
                  ", trigger value %d", __FUNCTION__,
                  trigger.getTagName(), trigger.entryValue);
            return res;
        }
    }
    mTriggerReplacedMap.clear();

    /**
     * Remove all new entries.
     */
    for (size_t i = 0; i < mTriggerRemovedMap.size(); ++i) {
        RequestTrigger trigger = mTriggerRemovedMap.valueAt(i);
        status_t res = metadata.erase(trigger.metadataTag);

        if (res != OK) {
            ALOGE("%s: Failed to erase metadata with trigger tag %s"
                  ", trigger value %d", __FUNCTION__,
                  trigger.getTagName(), trigger.entryValue);
            return res;
        }
    }
    mTriggerRemovedMap.clear();

    return OK;
}

status_t Camera3Device::RequestThread::addDummyTriggerIds(
        const sp<CaptureRequest> &request) {
    // Trigger ID 0 had special meaning in the HAL2 spec, so avoid it here
    static const int32_t dummyTriggerId = 1;
    status_t res;

    CameraMetadata &metadata = request->mSettings;

    // If AF trigger is active, insert a dummy AF trigger ID if none already
    // exists
    camera_metadata_entry afTrigger = metadata.find(ANDROID_CONTROL_AF_TRIGGER);
    camera_metadata_entry afId = metadata.find(ANDROID_CONTROL_AF_TRIGGER_ID);
    if (afTrigger.count > 0 &&
            afTrigger.data.u8[0] != ANDROID_CONTROL_AF_TRIGGER_IDLE &&
            afId.count == 0) {
        res = metadata.update(ANDROID_CONTROL_AF_TRIGGER_ID, &dummyTriggerId, 1);
        if (res != OK) return res;
    }

    // If AE precapture trigger is active, insert a dummy precapture trigger ID
    // if none already exists
    camera_metadata_entry pcTrigger =
            metadata.find(ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER);
    camera_metadata_entry pcId = metadata.find(ANDROID_CONTROL_AE_PRECAPTURE_ID);
    if (pcTrigger.count > 0 &&
            pcTrigger.data.u8[0] != ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE &&
            pcId.count == 0) {
        res = metadata.update(ANDROID_CONTROL_AE_PRECAPTURE_ID,
                &dummyTriggerId, 1);
        if (res != OK) return res;
    }

    return OK;
}

/**
 * PreparerThread inner class methods
 */

Camera3Device::PreparerThread::PreparerThread() :
        Thread(/*canCallJava*/false), mListener(nullptr),
        mActive(false), mCancelNow(false) {
}

Camera3Device::PreparerThread::~PreparerThread() {
    Thread::requestExitAndWait();
    if (mCurrentStream != nullptr) {
        mCurrentStream->cancelPrepare();
        ATRACE_ASYNC_END("stream prepare", mCurrentStream->getId());
        mCurrentStream.clear();
    }
    clear();
}

status_t Camera3Device::PreparerThread::prepare(int maxCount, sp<Camera3StreamInterface>& stream) {
    ATRACE_CALL();
    status_t res;

    Mutex::Autolock l(mLock);
    sp<NotificationListener> listener = mListener.promote();

    res = stream->startPrepare(maxCount);
    if (res == OK) {
        // No preparation needed, fire listener right off
        ALOGV("%s: Stream %d already prepared", __FUNCTION__, stream->getId());
        if (listener != NULL) {
            listener->notifyPrepared(stream->getId());
        }
        return OK;
    } else if (res != NOT_ENOUGH_DATA) {
        return res;
    }

    // Need to prepare, start up thread if necessary
    if (!mActive) {
        // mRunning will change to false before the thread fully shuts down, so wait to be sure it
        // isn't running
        Thread::requestExitAndWait();
        res = Thread::run("C3PrepThread", PRIORITY_BACKGROUND);
        if (res != OK) {
            ALOGE("%s: Unable to start preparer stream: %d (%s)", __FUNCTION__, res, strerror(-res));
            if (listener != NULL) {
                listener->notifyPrepared(stream->getId());
            }
            return res;
        }
        mCancelNow = false;
        mActive = true;
        ALOGV("%s: Preparer stream started", __FUNCTION__);
    }

    // queue up the work
    mPendingStreams.push_back(stream);
    ALOGV("%s: Stream %d queued for preparing", __FUNCTION__, stream->getId());

    return OK;
}

status_t Camera3Device::PreparerThread::clear() {
    ATRACE_CALL();
    Mutex::Autolock l(mLock);

    for (const auto& stream : mPendingStreams) {
        stream->cancelPrepare();
    }
    mPendingStreams.clear();
    mCancelNow = true;

    return OK;
}

void Camera3Device::PreparerThread::setNotificationListener(wp<NotificationListener> listener) {
    ATRACE_CALL();
    Mutex::Autolock l(mLock);
    mListener = listener;
}

bool Camera3Device::PreparerThread::threadLoop() {
    status_t res;
    {
        Mutex::Autolock l(mLock);
        if (mCurrentStream == nullptr) {
            // End thread if done with work
            if (mPendingStreams.empty()) {
                ALOGV("%s: Preparer stream out of work", __FUNCTION__);
                // threadLoop _must not_ re-acquire mLock after it sets mActive to false; would
                // cause deadlock with prepare()'s requestExitAndWait triggered by !mActive.
                mActive = false;
                return false;
            }

            // Get next stream to prepare
            auto it = mPendingStreams.begin();
            mCurrentStream = *it;
            mPendingStreams.erase(it);
            ATRACE_ASYNC_BEGIN("stream prepare", mCurrentStream->getId());
            ALOGV("%s: Preparing stream %d", __FUNCTION__, mCurrentStream->getId());
        } else if (mCancelNow) {
            mCurrentStream->cancelPrepare();
            ATRACE_ASYNC_END("stream prepare", mCurrentStream->getId());
            ALOGV("%s: Cancelling stream %d prepare", __FUNCTION__, mCurrentStream->getId());
            mCurrentStream.clear();
            mCancelNow = false;
            return true;
        }
    }

    res = mCurrentStream->prepareNextBuffer();
    if (res == NOT_ENOUGH_DATA) return true;
    if (res != OK) {
        // Something bad happened; try to recover by cancelling prepare and
        // signalling listener anyway
        ALOGE("%s: Stream %d returned error %d (%s) during prepare", __FUNCTION__,
                mCurrentStream->getId(), res, strerror(-res));
        mCurrentStream->cancelPrepare();
    }

    // This stream has finished, notify listener
    Mutex::Autolock l(mLock);
    sp<NotificationListener> listener = mListener.promote();
    if (listener != NULL) {
        ALOGV("%s: Stream %d prepare done, signaling listener", __FUNCTION__,
                mCurrentStream->getId());
        listener->notifyPrepared(mCurrentStream->getId());
    }

    ATRACE_ASYNC_END("stream prepare", mCurrentStream->getId());
    mCurrentStream.clear();

    return true;
}

/**
 * Static callback forwarding methods from HAL to instance
 */

void Camera3Device::sProcessCaptureResult(const camera3_callback_ops *cb,
        const camera3_capture_result *result) {
    Camera3Device *d =
            const_cast<Camera3Device*>(static_cast<const Camera3Device*>(cb));

    d->processCaptureResult(result);
}

void Camera3Device::sNotify(const camera3_callback_ops *cb,
        const camera3_notify_msg *msg) {
    Camera3Device *d =
            const_cast<Camera3Device*>(static_cast<const Camera3Device*>(cb));
    d->notify(msg);
}

}; // namespace android
