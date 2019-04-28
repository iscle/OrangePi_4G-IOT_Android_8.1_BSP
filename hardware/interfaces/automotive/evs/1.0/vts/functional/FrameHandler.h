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

#ifndef EVS_VTS_FRAMEHANDLER_H
#define EVS_VTS_FRAMEHANDLER_H

#include <queue>

#include <android/hardware/automotive/evs/1.0/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.0/IEvsCamera.h>
#include <android/hardware/automotive/evs/1.0/IEvsDisplay.h>

using namespace ::android::hardware::automotive::evs::V1_0;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_handle;
using ::android::sp;


/*
 * FrameHandler:
 * This class can be used to receive camera imagery from an IEvsCamera implementation.  Given an
 * IEvsDisplay instance at startup, it will forward the received imagery to the display,
 * providing a trivial implementation of a rear vew camera type application.
 * Note that the video frames are delivered on a background thread, while the control interface
 * is actuated from the applications foreground thread.
 */
class FrameHandler : public IEvsCameraStream {
public:
    enum BufferControlFlag {
        eAutoReturn,
        eNoAutoReturn,
    };

    FrameHandler(android::sp <IEvsCamera> pCamera, CameraDesc cameraInfo,
                 android::sp <IEvsDisplay> pDisplay = nullptr,
                 BufferControlFlag mode = eAutoReturn);
    void shutdown();

    bool startStream();
    void asyncStopStream();
    void blockingStopStream();

    bool returnHeldBuffer();

    bool isRunning();

    void waitForFrameCount(unsigned frameCount);
    void getFramesCounters(unsigned* received, unsigned* displayed);

private:
    // Implementation for ::android::hardware::automotive::evs::V1_0::ICarCameraStream
    Return<void> deliverFrame(const BufferDesc& buffer)  override;

    // Local implementation details
    bool copyBufferContents(const BufferDesc& tgtBuffer, const BufferDesc& srcBuffer);

    // Values initialized as startup
    android::sp <IEvsCamera>    mCamera;
    CameraDesc                  mCameraInfo;
    android::sp <IEvsDisplay>   mDisplay;
    BufferControlFlag           mReturnMode;

    // Since we get frames delivered to us asnchronously via the ICarCameraStream interface,
    // we need to protect all member variables that may be modified while we're streaming
    // (ie: those below)
    std::mutex                  mLock;
    std::condition_variable     mSignal;

    std::queue<BufferDesc>      mHeldBuffers;
    bool                        mRunning = false;
    unsigned                    mFramesReceived = 0;    // Simple counter -- rolls over eventually!
    unsigned                    mFramesDisplayed = 0;   // Simple counter -- rolls over eventually!
};


#endif //EVS_VTS_FRAMEHANDLER_H
