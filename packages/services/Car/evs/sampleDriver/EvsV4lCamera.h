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

#ifndef ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_EVSV4LCAMERA_H
#define ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_EVSV4LCAMERA_H

#include <android/hardware/automotive/evs/1.0/types.h>
#include <android/hardware/automotive/evs/1.0/IEvsCamera.h>
#include <ui/GraphicBuffer.h>

#include <thread>
#include <functional>

#include "VideoCapture.h"


namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


// From EvsEnumerator.h
class EvsEnumerator;


class EvsV4lCamera : public IEvsCamera {
public:
    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
    Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb)  override;
    Return <EvsResult> setMaxFramesInFlight(uint32_t bufferCount) override;
    Return <EvsResult> startVideoStream(const ::android::sp<IEvsCameraStream>& stream) override;
    Return<void> doneWithFrame(const BufferDesc& buffer) override;
    Return<void> stopVideoStream() override;
    Return <int32_t> getExtendedInfo(uint32_t opaqueIdentifier) override;
    Return <EvsResult> setExtendedInfo(uint32_t opaqueIdentifier, int32_t opaqueValue) override;

    // Implementation details
    EvsV4lCamera(const char *deviceName);
    virtual ~EvsV4lCamera() override;
    void shutdown();

    const CameraDesc& getDesc() { return mDescription; };

private:
    // These three functions are expected to be called while mAccessLock is held
    bool setAvailableFrames_Locked(unsigned bufferCount);
    unsigned increaseAvailableFrames_Locked(unsigned numToAdd);
    unsigned decreaseAvailableFrames_Locked(unsigned numToRemove);

    void forwardFrame(imageBuffer* tgt, void* data);

    sp <IEvsCameraStream> mStream = nullptr;  // The callback used to deliver each frame

    VideoCapture          mVideo;   // Interface to the v4l device

    CameraDesc mDescription = {};   // The properties of this camera
    uint32_t mFormat = 0;           // Values from android_pixel_format_t
    uint32_t mUsage  = 0;           // Values from from Gralloc.h
    uint32_t mStride = 0;           // Pixels per row (may be greater than image width)

    struct BufferRecord {
        buffer_handle_t handle;
        bool inUse;

        explicit BufferRecord(buffer_handle_t h) : handle(h), inUse(false) {};
    };

    std::vector <BufferRecord> mBuffers;    // Graphics buffers to transfer images
    unsigned mFramesAllowed;                // How many buffers are we currently using
    unsigned mFramesInUse;                  // How many buffers are currently outstanding

    // Which format specific function we need to use to move camera imagery into our output buffers
    void(*mFillBufferFromVideo)(const BufferDesc& tgtBuff, uint8_t* tgt,
                                void* imgData, unsigned imgStride);

    // Synchronization necessary to deconflict the capture thread from the main service thread
    // Note that the service interface remains single threaded (ie: not reentrant)
    std::mutex mAccessLock;
};

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace hardware
} // namespace android

#endif  // ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_EVSV4LCAMERA_H
