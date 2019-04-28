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

#ifndef ANDROID_AUTOMOTIVE_EVS_V1_0_HALCAMERA_H
#define ANDROID_AUTOMOTIVE_EVS_V1_0_HALCAMERA_H

#include <android/hardware/automotive/evs/1.0/types.h>
#include <android/hardware/automotive/evs/1.0/IEvsCamera.h>
#include <ui/GraphicBuffer.h>

#include <thread>
#include <list>


using namespace ::android::hardware::automotive::evs::V1_0;
using ::android::hardware::Return;
using ::android::hardware::hidl_handle;

namespace android {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


class VirtualCamera;    // From VirtualCamera.h


// This class wraps the actual hardware IEvsCamera objects.  There is a one to many
// relationship between instances of this class and instances of the VirtualCamera class.
// This class implements the IEvsCameraStream interface so that it can receive the video
// stream from the hardware camera and distribute it to the associated VirtualCamera objects.
class HalCamera : public IEvsCameraStream {
public:
    HalCamera(sp<IEvsCamera> hwCamera) : mHwCamera(hwCamera) {};

    // Factory methods for client VirtualCameras
    sp<VirtualCamera>   makeVirtualCamera();
    void                disownVirtualCamera(sp<VirtualCamera> virtualCamera);

    // Implementation details
    sp<IEvsCamera>      getHwCamera()       { return mHwCamera; };
    unsigned            getClientCount()    { return mClients.size(); };
    bool                changeFramesInFlight(int delta);

    Return<EvsResult>   clientStreamStarting();
    void                clientStreamEnding();
    Return<void>        doneWithFrame(const BufferDesc& buffer);

    // Methods from ::android::hardware::automotive::evs::V1_0::ICarCameraStream follow.
    Return<void> deliverFrame(const BufferDesc& buffer)  override;

private:
    sp<IEvsCamera>                  mHwCamera;
    std::list<wp<VirtualCamera>>    mClients;   // Weak pointers -> objects destruct if client dies

    enum {
        STOPPED,
        RUNNING,
        STOPPING,
    }                               mStreamState = STOPPED;

    struct FrameRecord {
        uint32_t    frameId;
        uint32_t    refCount;
        FrameRecord(uint32_t id) : frameId(id), refCount(0) {};
    };
    std::vector<FrameRecord>        mFrames;
};

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace android

#endif  // ANDROID_AUTOMOTIVE_EVS_V1_0_HALCAMERA_H
