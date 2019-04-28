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

#define LOG_TAG "VtsHalEvsTest"


// Note:  We have't got a great way to indicate which target
// should be tested, so we'll leave the interface served by the
// default (mock) EVS driver here for easy reference.  All
// actual EVS drivers should serve on the EvsEnumeratorHw name,
// however, so the code is checked in that way.
//const static char kEnumeratorName[]  = "EvsEnumeratorHw-Mock";
const static char kEnumeratorName[]  = "EvsEnumeratorHw";


// These values are called out in the EVS design doc (as of Mar 8, 2017)
static const int kMaxStreamStartMilliseconds = 500;
static const int kMinimumFramesPerSecond = 10;

static const int kSecondsToMilliseconds = 1000;
static const int kMillisecondsToMicroseconds = 1000;
static const float kNanoToMilliseconds = 0.000001f;
static const float kNanoToSeconds = 0.000000001f;


#include "FrameHandler.h"

#include <stdio.h>
#include <string.h>

#include <hidl/HidlTransportSupport.h>
#include <hwbinder/ProcessState.h>
#include <log/log.h>
#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include <android/log.h>
#include <android/hardware/automotive/evs/1.0/IEvsCamera.h>
#include <android/hardware/automotive/evs/1.0/IEvsEnumerator.h>
#include <android/hardware/automotive/evs/1.0/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.0/IEvsDisplay.h>

#include <VtsHalHidlTargetTestBase.h>


using namespace ::android::hardware::automotive::evs::V1_0;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_handle;
using ::android::hardware::hidl_string;
using ::android::sp;


// The main test class for EVS
class EvsHidlTest : public ::testing::VtsHalHidlTargetTestBase {
public:
    virtual void SetUp() override {
        // Make sure we can connect to the enumerator
        pEnumerator = IEvsEnumerator::getService(kEnumeratorName);
        ASSERT_NE(pEnumerator.get(), nullptr);
    }

    virtual void TearDown() override {}

protected:
    void loadCameraList() {
        // SetUp() must run first!
        assert(pEnumerator != nullptr);

        // Get the camera list
        pEnumerator->getCameraList([this](hidl_vec <CameraDesc> cameraList) {
                                       ALOGI("Camera list callback received %zu cameras",
                                             cameraList.size());
                                       cameraInfo.reserve(cameraList.size());
                                       for (auto&& cam: cameraList) {
                                           ALOGI("Found camera %s", cam.cameraId.c_str());
                                           cameraInfo.push_back(cam);
                                       }
                                   }
        );

        // We insist on at least one camera for EVS to pass any camera tests
        ASSERT_GE(cameraInfo.size(), 1u);
    }

    sp<IEvsEnumerator>          pEnumerator;    // Every test needs access to the service
    std::vector <CameraDesc>    cameraInfo;     // Empty unless/until loadCameraList() is called
};


//
// Tests start here...
//

/*
 * CameraOpenClean:
 * Opens each camera reported by the enumerator and then explicitly closes it via a
 * call to closeCamera.  Then repeats the test to ensure all cameras can be reopened.
 */
TEST_F(EvsHidlTest, CameraOpenClean) {
    ALOGI("Starting CameraOpenClean test");

    // Get the camera list
    loadCameraList();

    // Open and close each camera twice
    for (auto&& cam: cameraInfo) {
        for (int pass = 0; pass < 2; pass++) {
            sp<IEvsCamera> pCam = pEnumerator->openCamera(cam.cameraId);
            ASSERT_NE(pCam, nullptr);

            // Verify that this camera self-identifies correctly
            pCam->getCameraInfo([&cam](CameraDesc desc) {
                                    ALOGD("Found camera %s", desc.cameraId.c_str());
                                    EXPECT_EQ(cam.cameraId, desc.cameraId);
                                }
            );

            // Explicitly close the camera so resources are released right away
            pEnumerator->closeCamera(pCam);
        }
    }
}


/*
 * CameraOpenAggressive:
 * Opens each camera reported by the enumerator twice in a row without an intervening closeCamera
 * call.  This ensures that the intended "aggressive open" behavior works.  This is necessary for
 * the system to be tolerant of shutdown/restart race conditions.
 */
TEST_F(EvsHidlTest, CameraOpenAggressive) {
    ALOGI("Starting CameraOpenAggressive test");

    // Get the camera list
    loadCameraList();

    // Open and close each camera twice
    for (auto&& cam: cameraInfo) {
        sp<IEvsCamera> pCam = pEnumerator->openCamera(cam.cameraId);
        ASSERT_NE(pCam, nullptr);

        // Verify that this camera self-identifies correctly
        pCam->getCameraInfo([&cam](CameraDesc desc) {
                                ALOGD("Found camera %s", desc.cameraId.c_str());
                                EXPECT_EQ(cam.cameraId, desc.cameraId);
                            }
        );

        sp<IEvsCamera> pCam2 = pEnumerator->openCamera(cam.cameraId);
        ASSERT_NE(pCam, pCam2);
        ASSERT_NE(pCam2, nullptr);

        // Verify that the old camera rejects calls
        Return<EvsResult> badResult = pCam->setMaxFramesInFlight(2);
        EXPECT_EQ(EvsResult::OWNERSHIP_LOST, EvsResult(badResult));

        // Close the superceded camera
        pEnumerator->closeCamera(pCam);

        // Verify that the second camera instance self-identifies correctly
        pCam2->getCameraInfo([&cam](CameraDesc desc) {
                                 ALOGD("Found camera %s", desc.cameraId.c_str());
                                 EXPECT_EQ(cam.cameraId, desc.cameraId);
                             }
        );

        // Leave the second camera dangling so it gets cleaned up by the destructor path
    }

    // Sleep here to ensure the destructor cleanup has time to run so we don't break follow on tests
    sleep(1);   // I hate that this is an arbitrary time to wait.  :(  b/36122635
}


/*
 * DisplayOpen:
 * Test both clean shut down and "aggressive open" device stealing behavior.
 */
TEST_F(EvsHidlTest, DisplayOpen) {
    ALOGI("Starting DisplayOpen test");

    // Request exclusive access to the EVS display, then let it go
    {
        sp<IEvsDisplay> pDisplay = pEnumerator->openDisplay();
        ASSERT_NE(pDisplay, nullptr);

        // Ask the display what it's name is
        pDisplay->getDisplayInfo([](DisplayDesc desc) {
                                     ALOGD("Found display %s", desc.displayId.c_str());
                                 }
        );

        pEnumerator->closeDisplay(pDisplay);
    }

    // Ensure we can reopen the display after it has been closed
    {
        // Reopen the display
        sp<IEvsDisplay> pDisplay = pEnumerator->openDisplay();
        ASSERT_NE(pDisplay, nullptr);

        // Open the display while its already open -- ownership should be transferred
        sp<IEvsDisplay> pDisplay2 = pEnumerator->openDisplay();
        ASSERT_NE(pDisplay2, nullptr);

        // Ensure the old display properly reports its assassination
        Return<DisplayState> badResult = pDisplay->getDisplayState();
        EXPECT_EQ(badResult, DisplayState::DEAD);

        // Close only the newest display instance -- the other should already be a zombie
        pEnumerator->closeDisplay(pDisplay2);
    }

    // Finally, validate that we can open the display after the provoked failure above
    sp<IEvsDisplay> pDisplay = pEnumerator->openDisplay();
    ASSERT_NE(pDisplay, nullptr);

    pEnumerator->closeDisplay(pDisplay);
}


/*
 * DisplayStates:
 * Validate that display states transition as expected and can be queried from either the display
 * object itself or the owning enumerator.
 */
TEST_F(EvsHidlTest, DisplayStates) {
    ALOGI("Starting DisplayStates test");

    // Ensure the display starts in the expected state
    EXPECT_EQ((DisplayState)pEnumerator->getDisplayState(), DisplayState::NOT_OPEN);

    // Scope to limit the lifetime of the pDisplay pointer, and thus the IEvsDisplay object
    {
        // Request exclusive access to the EVS display
        sp<IEvsDisplay> pDisplay = pEnumerator->openDisplay();
        ASSERT_NE(pDisplay, nullptr);
        EXPECT_EQ((DisplayState)pEnumerator->getDisplayState(), DisplayState::NOT_VISIBLE);

        // Activate the display
        pDisplay->setDisplayState(DisplayState::VISIBLE_ON_NEXT_FRAME);
        EXPECT_EQ((DisplayState)pEnumerator->getDisplayState(), DisplayState::VISIBLE_ON_NEXT_FRAME);
        EXPECT_EQ((DisplayState)pDisplay->getDisplayState(), DisplayState::VISIBLE_ON_NEXT_FRAME);

        // Get the output buffer we'd use to display the imagery
        BufferDesc tgtBuffer = {};
        pDisplay->getTargetBuffer([&tgtBuffer](const BufferDesc& buff) {
                                      tgtBuffer = buff;
                                  }
        );
        EXPECT_NE(tgtBuffer.memHandle, nullptr);

        // Send the target buffer back for display (we didn't actually fill anything)
        pDisplay->returnTargetBufferForDisplay(tgtBuffer);

        // Sleep for a tenth of a second to ensure the driver has time to get the image displayed
        usleep(100 * kMillisecondsToMicroseconds);
        EXPECT_EQ((DisplayState)pEnumerator->getDisplayState(), DisplayState::VISIBLE);
        EXPECT_EQ((DisplayState)pDisplay->getDisplayState(), DisplayState::VISIBLE);

        // Turn off the display
        pDisplay->setDisplayState(DisplayState::NOT_VISIBLE);
        usleep(100 * kMillisecondsToMicroseconds);
        EXPECT_EQ((DisplayState)pEnumerator->getDisplayState(), DisplayState::NOT_VISIBLE);

        // Close the display
        pEnumerator->closeDisplay(pDisplay);
    }

    // TODO:  This hack shouldn't be necessary.  b/36122635
    sleep(1);

    // Now that the display pointer has gone out of scope, causing the IEvsDisplay interface
    // object to be destroyed, we should be back to the "not open" state.
    // NOTE:  If we want this to pass without the sleep above, we'd have to add the
    //        (now recommended) closeDisplay() call instead of relying on the smarter pointer
    //        going out of scope.  I've not done that because I want to verify that the deletion
    //        of the object does actually clean up (eventually).
    EXPECT_EQ((DisplayState)pEnumerator->getDisplayState(), DisplayState::NOT_OPEN);
}


/*
 * CameraStreamPerformance:
 * Measure and qualify the stream start up time and streaming frame rate of each reported camera
 */
TEST_F(EvsHidlTest, CameraStreamPerformance) {
    ALOGI("Starting CameraStreamPerformance test");

    // Get the camera list
    loadCameraList();

    // Test each reported camera
    for (auto&& cam: cameraInfo) {
        sp <IEvsCamera> pCam = pEnumerator->openCamera(cam.cameraId);
        ASSERT_NE(pCam, nullptr);

        // Set up a frame receiver object which will fire up its own thread
        sp<FrameHandler> frameHandler = new FrameHandler(pCam, cam,
                                                         nullptr,
                                                         FrameHandler::eAutoReturn);

        // Start the camera's video stream
        nsecs_t start = systemTime(SYSTEM_TIME_MONOTONIC);
        bool startResult = frameHandler->startStream();
        ASSERT_TRUE(startResult);

        // Ensure the first frame arrived within the expected time
        frameHandler->waitForFrameCount(1);
        nsecs_t firstFrame = systemTime(SYSTEM_TIME_MONOTONIC);
        nsecs_t timeToFirstFrame = systemTime(SYSTEM_TIME_MONOTONIC) - start;
        EXPECT_LE(nanoseconds_to_milliseconds(timeToFirstFrame), kMaxStreamStartMilliseconds);
        printf("Measured time to first frame %0.2f ms\n", timeToFirstFrame * kNanoToMilliseconds);
        ALOGI("Measured time to first frame %0.2f ms", timeToFirstFrame * kNanoToMilliseconds);

        // Wait a bit, then ensure we get at least the required minimum number of frames
        sleep(5);
        nsecs_t end = systemTime(SYSTEM_TIME_MONOTONIC);
        unsigned framesReceived = 0;
        frameHandler->getFramesCounters(&framesReceived, nullptr);
        framesReceived = framesReceived - 1;    // Back out the first frame we already waited for
        nsecs_t runTime = end - firstFrame;
        float framesPerSecond = framesReceived / (runTime * kNanoToSeconds);
        printf("Measured camera rate %3.2f fps\n", framesPerSecond);
        ALOGI("Measured camera rate %3.2f fps", framesPerSecond);
        EXPECT_GE(framesPerSecond, kMinimumFramesPerSecond);

        // Even when the camera pointer goes out of scope, the FrameHandler object will
        // keep the stream alive unless we tell it to shutdown.
        // Also note that the FrameHandle and the Camera have a mutual circular reference, so
        // we have to break that cycle in order for either of them to get cleaned up.
        frameHandler->shutdown();

        // Explicitly release the camera
        pEnumerator->closeCamera(pCam);
    }
}


/*
 * CameraStreamBuffering:
 * Ensure the camera implementation behaves properly when the client holds onto buffers for more
 * than one frame time.  The camera must cleanly skip frames until the client is ready again.
 */
TEST_F(EvsHidlTest, CameraStreamBuffering) {
    ALOGI("Starting CameraStreamBuffering test");

    // Arbitrary constant (should be > 1 and less than crazy)
    static const unsigned int kBuffersToHold = 6;

    // Get the camera list
    loadCameraList();

    // Test each reported camera
    for (auto&& cam: cameraInfo) {

        sp<IEvsCamera> pCam = pEnumerator->openCamera(cam.cameraId);
        ASSERT_NE(pCam, nullptr);

        // Ask for a crazy number of buffers in flight to ensure it errors correctly
        Return<EvsResult> badResult = pCam->setMaxFramesInFlight(0xFFFFFFFF);
        EXPECT_EQ(EvsResult::BUFFER_NOT_AVAILABLE, badResult);

        // Now ask for exactly two buffers in flight as we'll test behavior in that case
        Return<EvsResult> goodResult = pCam->setMaxFramesInFlight(kBuffersToHold);
        EXPECT_EQ(EvsResult::OK, goodResult);


        // Set up a frame receiver object which will fire up its own thread.
        sp<FrameHandler> frameHandler = new FrameHandler(pCam, cam,
                                                         nullptr,
                                                         FrameHandler::eNoAutoReturn);

        // Start the camera's video stream
        bool startResult = frameHandler->startStream();
        ASSERT_TRUE(startResult);

        // Check that the video stream stalls once we've gotten exactly the number of buffers
        // we requested since we told the frameHandler not to return them.
        sleep(2);   // 1 second should be enough for at least 5 frames to be delivered worst case
        unsigned framesReceived = 0;
        frameHandler->getFramesCounters(&framesReceived, nullptr);
        ASSERT_EQ(kBuffersToHold, framesReceived) << "Stream didn't stall at expected buffer limit";


        // Give back one buffer
        bool didReturnBuffer = frameHandler->returnHeldBuffer();
        EXPECT_TRUE(didReturnBuffer);

        // Once we return a buffer, it shouldn't take more than 1/10 second to get a new one
        // filled since we require 10fps minimum -- but give a 10% allowance just in case.
        usleep(110 * kMillisecondsToMicroseconds);
        frameHandler->getFramesCounters(&framesReceived, nullptr);
        EXPECT_EQ(kBuffersToHold+1, framesReceived) << "Stream should've resumed";

        // Even when the camera pointer goes out of scope, the FrameHandler object will
        // keep the stream alive unless we tell it to shutdown.
        // Also note that the FrameHandle and the Camera have a mutual circular reference, so
        // we have to break that cycle in order for either of them to get cleaned up.
        frameHandler->shutdown();

        // Explicitly release the camera
        pEnumerator->closeCamera(pCam);
    }
}


/*
 * CameraToDisplayRoundTrip:
 * End to end test of data flowing from the camera to the display.  Each delivered frame of camera
 * imagery is simply copied to the display buffer and presented on screen.  This is the one test
 * which a human could observe to see the operation of the system on the physical display.
 */
TEST_F(EvsHidlTest, CameraToDisplayRoundTrip) {
    ALOGI("Starting CameraToDisplayRoundTrip test");

    // Get the camera list
    loadCameraList();

    // Request exclusive access to the EVS display
    sp<IEvsDisplay> pDisplay = pEnumerator->openDisplay();
    ASSERT_NE(pDisplay, nullptr);

    // Test each reported camera
    for (auto&& cam: cameraInfo) {
        sp <IEvsCamera> pCam = pEnumerator->openCamera(cam.cameraId);
        ASSERT_NE(pCam, nullptr);

        // Set up a frame receiver object which will fire up its own thread.
        sp<FrameHandler> frameHandler = new FrameHandler(pCam, cam,
                                                         pDisplay,
                                                         FrameHandler::eAutoReturn);


        // Activate the display
        pDisplay->setDisplayState(DisplayState::VISIBLE_ON_NEXT_FRAME);

        // Start the camera's video stream
        bool startResult = frameHandler->startStream();
        ASSERT_TRUE(startResult);

        // Wait a while to let the data flow
        static const int kSecondsToWait = 5;
        const int streamTimeMs = kSecondsToWait * kSecondsToMilliseconds -
                                 kMaxStreamStartMilliseconds;
        const unsigned minimumFramesExpected = streamTimeMs * kMinimumFramesPerSecond /
                                               kSecondsToMilliseconds;
        sleep(kSecondsToWait);
        unsigned framesReceived = 0;
        unsigned framesDisplayed = 0;
        frameHandler->getFramesCounters(&framesReceived, &framesDisplayed);
        EXPECT_EQ(framesReceived, framesDisplayed);
        EXPECT_GE(framesDisplayed, minimumFramesExpected);

        // Turn off the display (yes, before the stream stops -- it should be handled)
        pDisplay->setDisplayState(DisplayState::NOT_VISIBLE);

        // Shut down the streamer
        frameHandler->shutdown();

        // Explicitly release the camera
        pEnumerator->closeCamera(pCam);
    }

    // Explicitly release the display
    pEnumerator->closeDisplay(pDisplay);
}
