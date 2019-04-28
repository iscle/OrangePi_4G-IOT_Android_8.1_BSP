/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
#ifndef __MULTIDISPLAY_OBSERVER_H
#define __MULTIDISPLAY_OBSERVER_H

#ifdef TARGET_HAS_MULTIPLE_DISPLAY
#include <display/MultiDisplayService.h>
#include <SimpleThread.h>
#else
#include <utils/Errors.h>
#endif
#include <string.h>

namespace android {
namespace intel {

struct VideoSourceInfo {
    VideoSourceInfo() {
        memset(this, 0, sizeof(VideoSourceInfo));
    }
    int width;
    int height;
    int frameRate;
    bool isProtected;
};


#ifdef TARGET_HAS_MULTIPLE_DISPLAY

class MultiDisplayObserver;

class MultiDisplayCallback : public BnMultiDisplayCallback {
public:
    MultiDisplayCallback(MultiDisplayObserver *observer);
    virtual ~MultiDisplayCallback();

    status_t blankSecondaryDisplay(bool blank);
    status_t updateVideoState(int sessionId, MDS_VIDEO_STATE state);
    status_t setHdmiTiming(const MDSHdmiTiming& timing);
    status_t setHdmiScalingType(MDS_SCALING_TYPE type);
    status_t setHdmiOverscan(int hValue, int vValue);
    status_t updateInputState(bool state);

private:
    MultiDisplayObserver *mDispObserver;
    MDS_VIDEO_STATE mVideoState;
};

class MultiDisplayObserver {
public:
    MultiDisplayObserver();
    virtual ~MultiDisplayObserver();

public:
    bool initialize();
    void deinitialize();
    status_t notifyHotPlug(bool connected);
    status_t getVideoSourceInfo(int sessionID, VideoSourceInfo* info);
    int  getVideoSessionNumber();
    bool isExternalDeviceTimingFixed() const;
    status_t notifyWidiConnectionStatus(bool connected);
    status_t setDecoderOutputResolution(int sessionID,
            int32_t width, int32_t height,
            int32_t offX,  int32_t offY,
            int32_t bufWidth, int32_t bufHeight);

private:
    bool isMDSRunning();
    bool initMDSClient();
    bool initMDSClientAsync();
    void deinitMDSClient();
    status_t blankSecondaryDisplay(bool blank);
    status_t updateVideoState(int sessionId, MDS_VIDEO_STATE state);
    status_t setHdmiTiming(const MDSHdmiTiming& timing);
    status_t updateInputState(bool active);
    friend class MultiDisplayCallback;

private:
    enum {
        THREAD_LOOP_DELAY = 10, // 10 ms
        THREAD_LOOP_BOUND = 2000, // 20s
    };

private:
    sp<IMultiDisplayCallbackRegistrar> mMDSCbRegistrar;
    sp<IMultiDisplayInfoProvider> mMDSInfoProvider;
    sp<IMultiDisplayConnectionObserver> mMDSConnObserver;
    sp<IMultiDisplayDecoderConfig> mMDSDecoderConfig;
    sp<MultiDisplayCallback> mMDSCallback;
    mutable Mutex mLock;
    Condition mCondition;
    int mThreadLoopCount;
    bool mDeviceConnected;
    // indicate external devices's timing is set
    bool mExternalHdmiTiming;
    bool mInitialized;

private:
    DECLARE_THREAD(MDSClientInitThread, MultiDisplayObserver);
};

#else

// dummy declaration and implementation of MultiDisplayObserver
class MultiDisplayObserver {
public:
    MultiDisplayObserver() {}
    virtual ~MultiDisplayObserver() {}

    bool initialize() { return true; }
    void deinitialize() {}
    status_t notifyHotPlug(bool connected) { return NO_ERROR; }
    status_t getVideoSourceInfo(int sessionID, VideoSourceInfo* info) { return INVALID_OPERATION; }
    int  getVideoSessionNumber() { return 0; }
    bool isExternalDeviceTimingFixed() const { return false; }
    status_t notifyWidiConnectionStatus(bool connected) { return NO_ERROR; }
    status_t setDecoderOutputResolution(
            int sessionID,
            int32_t width, int32_t height,
            int32_t, int32_t, int32_t, int32_t) { return NO_ERROR; }
};

#endif //TARGET_HAS_MULTIPLE_DISPLAY

} // namespace intel
} // namespace android

#endif /* __MULTIMultiDisplayObserver_H_ */
