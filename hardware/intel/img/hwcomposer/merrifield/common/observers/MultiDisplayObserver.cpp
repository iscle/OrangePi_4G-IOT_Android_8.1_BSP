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
#ifdef TARGET_HAS_MULTIPLE_DISPLAY
#include <HwcTrace.h>
#include <binder/IServiceManager.h>
#include <Hwcomposer.h>
#include <DisplayAnalyzer.h>
#include <ExternalDevice.h>
#endif

#include <MultiDisplayObserver.h>

namespace android {
namespace intel {

#ifdef TARGET_HAS_MULTIPLE_DISPLAY

////// MultiDisplayCallback

MultiDisplayCallback::MultiDisplayCallback(MultiDisplayObserver *dispObserver)
    : mDispObserver(dispObserver),
      mVideoState(MDS_VIDEO_STATE_UNKNOWN)
{
}

MultiDisplayCallback::~MultiDisplayCallback()
{
    CTRACE();
    mDispObserver = NULL;
}

status_t MultiDisplayCallback::blankSecondaryDisplay(bool blank)
{
    ITRACE("blank: %d", blank);
    mDispObserver->blankSecondaryDisplay(blank);
    return NO_ERROR;
}

status_t MultiDisplayCallback::updateVideoState(int sessionId, MDS_VIDEO_STATE state)
{
    mVideoState = state;
    ITRACE("state: %d", state);
    mDispObserver->updateVideoState(sessionId, state);
    return NO_ERROR;
}

status_t MultiDisplayCallback::setHdmiTiming(const MDSHdmiTiming& timing)
{
    mDispObserver->setHdmiTiming(timing);
    return NO_ERROR;
}

status_t MultiDisplayCallback::updateInputState(bool state)
{
    //ITRACE("input state: %d", state);
    mDispObserver->updateInputState(state);
    return NO_ERROR;
}

status_t MultiDisplayCallback::setHdmiScalingType(MDS_SCALING_TYPE type)
{
    ITRACE("scaling type: %d", type);
    // Merrifield doesn't implement this API
    return INVALID_OPERATION;
}

status_t MultiDisplayCallback::setHdmiOverscan(int hValue, int vValue)
{
    ITRACE("oversacn compensation, h: %d v: %d", hValue, vValue);
    // Merrifield doesn't implement this API
    return INVALID_OPERATION;
}

////// MultiDisplayObserver

MultiDisplayObserver::MultiDisplayObserver()
    : mMDSCbRegistrar(NULL),
      mMDSInfoProvider(NULL),
      mMDSConnObserver(NULL),
      mMDSDecoderConfig(NULL),
      mMDSCallback(NULL),
      mLock(),
      mCondition(),
      mThreadLoopCount(0),
      mDeviceConnected(false),
      mExternalHdmiTiming(false),
      mInitialized(false)
{
    CTRACE();
}

MultiDisplayObserver::~MultiDisplayObserver()
{
    WARN_IF_NOT_DEINIT();
}

bool MultiDisplayObserver::isMDSRunning()
{
    // Check if Multi Display service is running
    sp<IServiceManager> sm = defaultServiceManager();
    if (sm == NULL) {
        ETRACE("fail to get service manager!");
        return false;
    }

    sp<IBinder> service = sm->checkService(String16(INTEL_MDS_SERVICE_NAME));
    if (service == NULL) {
        VTRACE("fail to get MultiDisplay service!");
        return false;
    }

    return true;
}

bool MultiDisplayObserver::initMDSClient()
{
    sp<IServiceManager> sm = defaultServiceManager();
    if (sm == NULL) {
        ETRACE("Fail to get service manager");
        return false;
    }
    sp<IMDService> mds = interface_cast<IMDService>(
            sm->getService(String16(INTEL_MDS_SERVICE_NAME)));
    if (mds == NULL) {
        ETRACE("Fail to get MDS service");
        return false;
    }
    mMDSCbRegistrar = mds->getCallbackRegistrar();
    if (mMDSCbRegistrar.get() == NULL) {
        ETRACE("failed to create mds base Client");
        return false;
    }

    mMDSCallback = new MultiDisplayCallback(this);
    if (mMDSCallback.get() == NULL) {
        ETRACE("failed to create MultiDisplayCallback");
        deinitMDSClient();
        return false;
    }
    mMDSInfoProvider = mds->getInfoProvider();
    if (mMDSInfoProvider.get() == NULL) {
        ETRACE("failed to create mds video Client");
        return false;
    }

    mMDSConnObserver = mds->getConnectionObserver();
    if (mMDSConnObserver.get() == NULL) {
        ETRACE("failed to create mds video Client");
        return false;
    }
    mMDSDecoderConfig = mds->getDecoderConfig();
    if (mMDSDecoderConfig.get() == NULL) {
        ETRACE("failed to create mds decoder Client");
        return false;
    }

    status_t ret = mMDSCbRegistrar->registerCallback(mMDSCallback);
    if (ret != NO_ERROR) {
        ETRACE("failed to register callback");
        deinitMDSClient();
        return false;
    }

    Drm *drm = Hwcomposer::getInstance().getDrm();
    mDeviceConnected = drm->isConnected(IDisplayDevice::DEVICE_EXTERNAL);
    ITRACE("MDS client is initialized");
    return true;
}

void MultiDisplayObserver::deinitMDSClient()
{
    if (mMDSCallback.get() && mMDSCbRegistrar.get()) {
        mMDSCbRegistrar->unregisterCallback(mMDSCallback);
    }

    mDeviceConnected = false;
    mMDSCbRegistrar = NULL;
    mMDSInfoProvider = NULL;
    mMDSCallback = NULL;
    mMDSConnObserver = NULL;
    mMDSDecoderConfig = NULL;
}

bool MultiDisplayObserver::initMDSClientAsync()
{
    if (mThread.get()) {
        WTRACE("working thread has been already created.");
        return true;
    }

    mThread = new MDSClientInitThread(this);
    if (mThread.get() == NULL) {
        ETRACE("failed to create MDS client init thread");
        return false;
    }
    mThreadLoopCount = 0;
    // TODO: check return value
    mThread->run("MDSClientInitThread", PRIORITY_URGENT_DISPLAY);
    return true;
}

bool MultiDisplayObserver::initialize()
{
    bool ret = true;
    Mutex::Autolock _l(mLock);

    if (mInitialized) {
        WTRACE("display observer has been initialized");
        return true;
    }

    // initialize MDS client once. This should succeed if MDS service starts
    // before surfaceflinger service is started.
    // if surface flinger runs first, MDS client will be initialized asynchronously in
    // a working thread
    if (isMDSRunning()) {
        if (!initMDSClient()) {
            ETRACE("failed to initialize MDS client");
            // FIXME: NOT a common case for system server crash.
            // Start a working thread to initialize MDS client if exception happens
            ret = initMDSClientAsync();
        }
    } else {
        ret = initMDSClientAsync();
    }

    mInitialized = true;
    return ret;
}

void MultiDisplayObserver::deinitialize()
{
    sp<MDSClientInitThread> detachedThread;
    do {
        Mutex::Autolock _l(mLock);

        if (mThread.get()) {
            mCondition.signal();
            detachedThread = mThread;
            mThread = NULL;
        }
        mThreadLoopCount = 0;
        deinitMDSClient();
        mInitialized = false;
    } while (0);

    if (detachedThread.get()) {
        detachedThread->requestExitAndWait();
        detachedThread = NULL;
    }
}

bool MultiDisplayObserver::threadLoop()
{
    Mutex::Autolock _l(mLock);

    // try to create MDS client in the working thread
    // multiple delayed attempts are made until MDS service starts.

    // Return false if MDS service is running or loop limit is reached
    // such that thread becomes inactive.
    if (isMDSRunning()) {
        if (!initMDSClient()) {
            ETRACE("failed to initialize MDS client");
        }
        return false;
    }

    if (mThreadLoopCount++ > THREAD_LOOP_BOUND) {
        ETRACE("failed to initialize MDS client, loop limit reached");
        return false;
    }

    status_t err = mCondition.waitRelative(mLock, milliseconds(THREAD_LOOP_DELAY));
    if (err != -ETIMEDOUT) {
        ITRACE("thread is interrupted");
        return false;
    }

    return true; // keep trying
}


status_t MultiDisplayObserver::blankSecondaryDisplay(bool blank)
{
    // blank secondary display
    Hwcomposer::getInstance().getDisplayAnalyzer()->postBlankEvent(blank);
    return 0;
}

status_t MultiDisplayObserver::updateVideoState(int sessionId, MDS_VIDEO_STATE state)
{
    Hwcomposer::getInstance().getDisplayAnalyzer()->postVideoEvent(
        sessionId, (int)state);
    return 0;
}

status_t MultiDisplayObserver::setHdmiTiming(const MDSHdmiTiming& timing)
{
    drmModeModeInfo mode;
    mode.hdisplay = timing.width;
    mode.vdisplay = timing.height;
    mode.vrefresh = timing.refresh;
    mode.flags = timing.flags;
    ITRACE("timing to set: %dx%d@%dHz", timing.width, timing.height, timing.refresh);
    ExternalDevice *dev =
        (ExternalDevice *)Hwcomposer::getInstance().getDisplayDevice(HWC_DISPLAY_EXTERNAL);
    if (dev) {
        dev->setDrmMode(mode);
    }

    mExternalHdmiTiming = true;
    return 0;
}

status_t MultiDisplayObserver::updateInputState(bool active)
{
    Hwcomposer::getInstance().getDisplayAnalyzer()->postInputEvent(active);
    return 0;
}


/// Public interfaces

status_t MultiDisplayObserver::notifyHotPlug( bool connected)
{
    {
        // lock scope
        Mutex::Autolock _l(mLock);
        if (mMDSConnObserver.get() == NULL) {
            return NO_INIT;
        }

        if (connected == mDeviceConnected) {
            WTRACE("hotplug event ignored");
            return NO_ERROR;
        }

        // clear it after externel device is disconnected
        if (!connected) mExternalHdmiTiming = false;

        mDeviceConnected = connected;
    }
    return mMDSConnObserver->updateHdmiConnectionStatus(connected);
}

status_t MultiDisplayObserver::getVideoSourceInfo(int sessionID, VideoSourceInfo* info)
{
    Mutex::Autolock _l(mLock);
    if (mMDSInfoProvider.get() == NULL) {
        return NO_INIT;
    }

    if (info == NULL) {
        ETRACE("invalid parameter");
        return UNKNOWN_ERROR;
    }

    MDSVideoSourceInfo videoInfo;
    memset(&videoInfo, 0, sizeof(MDSVideoSourceInfo));
    status_t ret = mMDSInfoProvider->getVideoSourceInfo(sessionID, &videoInfo);
    if (ret == NO_ERROR) {
        info->width     = videoInfo.displayW;
        info->height    = videoInfo.displayH;
        info->frameRate = videoInfo.frameRate;
        info->isProtected = videoInfo.isProtected;
        VTRACE("Video Session[%d] source info: %dx%d@%d", sessionID,
                info->width, info->height, info->frameRate);
    }
    return ret;
}

int MultiDisplayObserver::getVideoSessionNumber()
{
    Mutex::Autolock _l(mLock);
    if (mMDSInfoProvider.get() == NULL) {
        return 0;
    }

    return mMDSInfoProvider->getVideoSessionNumber();
}

bool MultiDisplayObserver::isExternalDeviceTimingFixed() const
{
    Mutex::Autolock _l(mLock);
    return mExternalHdmiTiming;
}

status_t MultiDisplayObserver::notifyWidiConnectionStatus( bool connected)
{
    Mutex::Autolock _l(mLock);
    if (mMDSConnObserver.get() == NULL) {
        return NO_INIT;
    }
    return mMDSConnObserver->updateWidiConnectionStatus(connected);
}

status_t MultiDisplayObserver::setDecoderOutputResolution(
        int sessionID,
        int32_t width, int32_t height,
        int32_t offX, int32_t offY,
        int32_t bufWidth, int32_t bufHeight)
{
    Mutex::Autolock _l(mLock);
    if (mMDSDecoderConfig.get() == NULL) {
        return NO_INIT;
    }
    if (width <= 0 || height <= 0 ||
            offX < 0 || offY < 0 ||
            bufWidth <= 0 || bufHeight <= 0) {
        ETRACE(" Invalid parameter: %dx%d, %dx%d, %dx%d", width, height, offX, offY, bufWidth, bufHeight);
        return UNKNOWN_ERROR;
    }

    status_t ret = mMDSDecoderConfig->setDecoderOutputResolution(sessionID, width, height, offX, offY, bufWidth, bufHeight);
    if (ret == NO_ERROR) {
        ITRACE("Video Session[%d] output resolution %dx%d ", sessionID, width, height);
    }
    return ret;
}


#endif //TARGET_HAS_MULTIPLE_DISPLAY

} // namespace intel
} // namespace android
