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

#include <HwcTrace.h>
#include <IDisplayDevice.h>
#include <DisplayQuery.h>
#include <BufferManager.h>
#include <DisplayPlaneManager.h>
#include <Hwcomposer.h>
#include <DisplayAnalyzer.h>
#include <cutils/properties.h>
#include <GraphicBuffer.h>
#include <ExternalDevice.h>
#include <VirtualDevice.h>

namespace android {
namespace intel {

DisplayAnalyzer::DisplayAnalyzer()
    : mInitialized(false),
      mVideoExtModeEnabled(true),
      mVideoExtModeEligible(false),
      mVideoExtModeActive(false),
      mBlankDevice(false),
      mOverlayAllowed(true),
      mActiveInputState(true),
      mIgnoreVideoSkipFlag(false),
      mProtectedVideoSession(false),
      mCachedNumDisplays(0),
      mCachedDisplays(0),
      mPendingEvents(),
      mEventMutex(),
      mEventHandledCondition()
{
}

DisplayAnalyzer::~DisplayAnalyzer()
{
}

bool DisplayAnalyzer::initialize()
{
    // by default video extended mode is enabled
    char prop[PROPERTY_VALUE_MAX];
    if (property_get("hwc.video.extmode.enable", prop, "1") > 0) {
        mVideoExtModeEnabled = atoi(prop) ? true : false;
    }
    mVideoExtModeEligible = false;
    mVideoExtModeActive = false;
    mBlankDevice = false;
    mOverlayAllowed = true;
    mActiveInputState = true;
    mIgnoreVideoSkipFlag = false;
    mProtectedVideoSession = false;
    mCachedNumDisplays = 0;
    mCachedDisplays = 0;
    mPendingEvents.clear();
    mVideoStateMap.clear();
    mInitialized = true;

    return true;
}

void DisplayAnalyzer::deinitialize()
{
    mPendingEvents.clear();
    mVideoStateMap.clear();
    mInitialized = false;
}

void DisplayAnalyzer::analyzeContents(
        size_t numDisplays, hwc_display_contents_1_t** displays)
{
    // cache and use them only in this context during analysis
    mCachedNumDisplays = numDisplays;
    mCachedDisplays = displays;

    handlePendingEvents();

    if (mVideoExtModeEnabled) {
        handleVideoExtMode();
    }

    if (mBlankDevice) {
        // this will make sure device is blanked after geometry changes.
        // blank event is only processed once
        blankSecondaryDevice();
    }
}

void DisplayAnalyzer::handleVideoExtMode()
{
    bool eligible = mVideoExtModeEligible;
    checkVideoExtMode();
    if (eligible == mVideoExtModeEligible) {
        if (mVideoExtModeActive) {
            // need to mark all layers
            setCompositionType(0, HWC_OVERLAY, false);
        }
        return;
    }

    if (mVideoExtModeEligible) {
        if (mActiveInputState) {
            VTRACE("input is active");
        } else {
            enterVideoExtMode();
        }
    } else {
        exitVideoExtMode();
    }
}

void DisplayAnalyzer::checkVideoExtMode()
{
    if (mVideoStateMap.size() != 1) {
        mVideoExtModeEligible = false;
        return;
    }

    Hwcomposer *hwc = &Hwcomposer::getInstance();

    ExternalDevice *eDev = static_cast<ExternalDevice *>(hwc->getDisplayDevice(IDisplayDevice::DEVICE_EXTERNAL));
    VirtualDevice  *vDev = static_cast<VirtualDevice  *>(hwc->getDisplayDevice(IDisplayDevice::DEVICE_VIRTUAL));

    if ((!eDev || !eDev->isConnected()) && (!vDev || !vDev->isFrameServerActive())) {
        mVideoExtModeEligible = false;
        return;
    }

    bool geometryChanged = false;
    int activeDisplays = 0;

    hwc_display_contents_1_t *content = NULL;
    for (int i = 0; i < (int)mCachedNumDisplays; i++) {
        content = mCachedDisplays[i];
        if (content == NULL) {
            continue;
        }
        activeDisplays++;
        if (content->flags & HWC_GEOMETRY_CHANGED) {
            geometryChanged = true;
        }
    }

    if (activeDisplays <= 1) {
        mVideoExtModeEligible = false;
        return;
    }

    // video state update event may come later than geometry change event.
    // in that case, video extended mode is not detected properly.
#if 0
    if (geometryChanged == false) {
        // use previous analysis result
        return;
    }
#endif
    // reset eligibility of video extended mode
    mVideoExtModeEligible = false;

    // check if there is video layer in the primary device
    content = mCachedDisplays[0];
    if (content == NULL) {
        return;
    }

    buffer_handle_t videoHandle = 0;
    bool videoLayerExist = false;
    bool videoFullScreenOnPrimary = false;
    bool isVideoLayerSkipped = false;

    // exclude the frame buffer target layer
    for (int j = 0; j < (int)content->numHwLayers - 1; j++) {
        videoLayerExist = isVideoLayer(content->hwLayers[j]);
        if (videoLayerExist) {
            if ((content->hwLayers[j].flags & HWC_SKIP_LAYER)) {
                isVideoLayerSkipped = true;
            }
            videoHandle = content->hwLayers[j].handle;
            videoFullScreenOnPrimary = isVideoFullScreen(0, content->hwLayers[j]);
            break;
        }
    }

    if (videoLayerExist == false) {
        // no video layer is found in the primary layer
        return;
    }

    // check whether video layer exists in external device or virtual device
    // TODO: video may exist in virtual device but no in external device or vice versa
    // TODO: multiple video layers are not addressed here
    for (int i = 1; i < (int)mCachedNumDisplays; i++) {
        content = mCachedDisplays[i];
        if (content == NULL) {
            continue;
        }

        // exclude the frame buffer target layer
        for (int j = 0; j < (int)content->numHwLayers - 1; j++) {
            if (content->hwLayers[j].handle == videoHandle) {
                isVideoLayerSkipped |= (content->hwLayers[j].flags & HWC_SKIP_LAYER);
                VTRACE("video layer exists in device %d", i);
                if (isVideoLayerSkipped || videoFullScreenOnPrimary){
                    VTRACE("Video ext mode eligible, %d, %d",
                            isVideoLayerSkipped, videoFullScreenOnPrimary);
                    mVideoExtModeEligible = true;
                } else {
                    mVideoExtModeEligible = isVideoFullScreen(i, content->hwLayers[j]);
                }
                return;
            }
        }
    }
}

bool DisplayAnalyzer::isVideoExtModeActive()
{
    return mVideoExtModeActive;
}

bool DisplayAnalyzer::isVideoExtModeEnabled()
{
#if 1
    // enable it for run-time debugging purpose.
    char prop[PROPERTY_VALUE_MAX];
    if (property_get("hwc.video.extmode.enable", prop, "1") > 0) {
        mVideoExtModeEnabled = atoi(prop) ? true : false;
    }
    ITRACE("video extended mode enabled: %d", mVideoExtModeEnabled);
#endif

    return mVideoExtModeEnabled;
}

bool DisplayAnalyzer::isVideoLayer(hwc_layer_1_t &layer)
{
    bool ret = false;
    BufferManager *bm = Hwcomposer::getInstance().getBufferManager();
    if (!layer.handle) {
        return false;
    }
    DataBuffer *buffer = bm->lockDataBuffer(layer.handle);
     if (!buffer) {
         ETRACE("failed to get buffer");
     } else {
        ret = DisplayQuery::isVideoFormat(buffer->getFormat());
        bm->unlockDataBuffer(buffer);
    }
    return ret;
}

bool DisplayAnalyzer::isVideoFullScreen(int device, hwc_layer_1_t &layer)
{
    IDisplayDevice *displayDevice = Hwcomposer::getInstance().getDisplayDevice(device);
    if (!displayDevice) {
        return false;
    }
    int width = 0, height = 0;
    if (!displayDevice->getDisplaySize(&width, &height)) {
        return false;
    }

    VTRACE("video left %d, right %d, top %d, bottom %d, device width %d, height %d",
        layer.displayFrame.left, layer.displayFrame.right,
        layer.displayFrame.top, layer.displayFrame.bottom,
        width, height);

    // full-screen defintion:
    // width of target display frame == width of target device, with 1 pixel of tolerance, or
    // Height of target display frame == height of target device, with 1 pixel of tolerance, or
    // width * height of display frame > 90% of width * height of display device, or
    // any of above condition is met on either primary display or secondary display
    int dstW = layer.displayFrame.right - layer.displayFrame.left;
    int dstH = layer.displayFrame.bottom - layer.displayFrame.top;

    if (abs(dstW - width) > 1 &&
        abs(dstH - height) > 1 &&
        dstW * dstH * 10 < width * height * 9) {
        VTRACE("video is not full-screen");
        return false;
    }
    return true;
}

bool DisplayAnalyzer::isOverlayAllowed()
{
    return mOverlayAllowed;
}

int DisplayAnalyzer::getVideoInstances()
{
    return (int)mVideoStateMap.size();
}

void DisplayAnalyzer::postHotplugEvent(bool connected)
{
    if (!connected) {
        // enable vsync on the primary device immediately
        Hwcomposer::getInstance().getVsyncManager()->enableDynamicVsync(true);
    }

    // handle hotplug event (vsync switch) asynchronously
    Event e;
    e.type = HOTPLUG_EVENT;
    e.bValue = connected;
    postEvent(e);
    Hwcomposer::getInstance().invalidate();
}

void DisplayAnalyzer::postVideoEvent(int instanceID, int state)
{
    Event e;
    e.type = VIDEO_EVENT;
    e.videoEvent.instanceID = instanceID;
    e.videoEvent.state = state;
    postEvent(e);
    if ((state == VIDEO_PLAYBACK_STARTING) ||
        (state == VIDEO_PLAYBACK_STOPPING && mProtectedVideoSession)) {
        Hwcomposer::getInstance().invalidate();
        mOverlayAllowed = false;
        hwc_display_contents_1_t *content = NULL;
        for (int i = 0; i < (int)mCachedNumDisplays; i++) {
            setCompositionType(i, HWC_FRAMEBUFFER, true);
        }
        // wait for up to 100ms until overlay is disabled.
        int loop = 0;
        while (loop++ < 6) {
            if (Hwcomposer::getInstance().getPlaneManager()->isOverlayPlanesDisabled())
                break;
            usleep(16700);
        }
        if (loop >= 6) {
            WTRACE("timeout disabling overlay ");
        }
    }
}

void DisplayAnalyzer::postBlankEvent(bool blank)
{
    Event e;
    e.type = BLANK_EVENT;
    e.bValue = blank;
    postEvent(e);
    Hwcomposer::getInstance().invalidate();
}

void DisplayAnalyzer::postInputEvent(bool active)
{
    Event e;
    e.type = INPUT_EVENT;
    e.bValue = active;
    postEvent(e);
    Hwcomposer::getInstance().invalidate();
}

void DisplayAnalyzer::postIdleEntryEvent(void)
{
    Event e;
    e.type = IDLE_ENTRY_EVENT;
    e.nValue = 0;
    postEvent(e);
}

void DisplayAnalyzer::postEvent(Event& e)
{
    Mutex::Autolock lock(mEventMutex);
    mPendingEvents.add(e);
}

bool DisplayAnalyzer::getEvent(Event& e)
{
    Mutex::Autolock lock(mEventMutex);
    if (mPendingEvents.size() == 0) {
        return false;
    }
    e = mPendingEvents[0];
    mPendingEvents.removeAt(0);
    return true;
}

void DisplayAnalyzer::handlePendingEvents()
{
    // handle one event per analysis to avoid blocking surface flinger
    // some event may take lengthy time to process
    Event e;
    if (!getEvent(e)) {
        return;
    }

    switch (e.type) {
    case HOTPLUG_EVENT:
        handleHotplugEvent(e.bValue);
        break;
    case BLANK_EVENT:
        handleBlankEvent(e.bValue);
        break;
    case VIDEO_EVENT:
        handleVideoEvent(e.videoEvent.instanceID, e.videoEvent.state);
        break;
    case TIMING_EVENT:
        handleTimingEvent();
        break;
    case INPUT_EVENT:
        handleInputEvent(e.bValue);
        break;
    case DPMS_EVENT:
        handleDpmsEvent(e.nValue);
        break;
    case IDLE_ENTRY_EVENT:
        handleIdleEntryEvent(e.nValue);
        break;
    case IDLE_EXIT_EVENT:
        handleIdleExitEvent();
        break;
    case VIDEO_CHECK_EVENT:
        handleVideoCheckEvent();
        break;
    }
}

void DisplayAnalyzer::handleHotplugEvent(bool connected)
{
    Hwcomposer *hwc = &Hwcomposer::getInstance();
    if (connected) {
        if (mVideoStateMap.size() == 1) {
            // Some video apps wouldn't update video state again when plugin HDMI
            // and fail to reset refresh rate
            ExternalDevice *dev = NULL;
            dev = (ExternalDevice *)hwc->getDisplayDevice(IDisplayDevice::DEVICE_EXTERNAL);
            if (!dev || !dev->isConnected()) {
                ITRACE("External device isn't connected");
                return;
            }
            if (hwc->getMultiDisplayObserver()->isExternalDeviceTimingFixed()) {
                VTRACE("Timing of external device is fixed.");
                return;
            }
            VideoSourceInfo info;
            int instanceID = mVideoStateMap.keyAt(0);
            status_t err = hwc->getMultiDisplayObserver()->getVideoSourceInfo(
                    instanceID, &info);
            if (err == NO_ERROR) {
                int hz = dev->getRefreshRate();
                if (hz > 0 && info.frameRate > 0 && hz != info.frameRate) {
                    ITRACE("Old Hz %d, new one %d", hz, info.frameRate);
                    dev->setRefreshRate(info.frameRate);
                } else
                    WTRACE("Old Hz %d is invalid, %d", hz, info.frameRate);
            }
        }
    } else {
        if (mVideoStateMap.size() == 1) {
            // Reset input state if HDMI is plug out to
            // avoid entering extended mode immediately after HDMI is plug in
            mActiveInputState = true;
        }
    }
}

void DisplayAnalyzer::handleBlankEvent(bool blank)
{
    mBlankDevice = blank;
    // force geometry changed in the secondary device to reset layer composition type
    for (int i = 0; i < (int)mCachedNumDisplays; i++) {
        if (i == IDisplayDevice::DEVICE_PRIMARY) {
            continue;
        }
        if (mCachedDisplays[i]) {
            mCachedDisplays[i]->flags |= HWC_GEOMETRY_CHANGED;
        }
    }
    blankSecondaryDevice();
}

void DisplayAnalyzer::handleTimingEvent()
{
    // check whether external device is connected, reset refresh rate to match video frame rate
    // if video is in playing state or reset refresh rate to default preferred one if video is not
    // at playing state
    Hwcomposer *hwc = &Hwcomposer::getInstance();
    ExternalDevice *dev = NULL;
    dev = (ExternalDevice *)hwc->getDisplayDevice(IDisplayDevice::DEVICE_EXTERNAL);
    if (!dev) {
        return;
    }

    if (!dev->isConnected()) {
        return;
    }

    if (hwc->getMultiDisplayObserver()->isExternalDeviceTimingFixed()) {
        VTRACE("Timing of external device is fixed.");
        return;
    }

    int hz = 0;
    if (mVideoStateMap.size() == 1) {
        VideoSourceInfo info;
        int instanceID = mVideoStateMap.keyAt(0);
        status_t err = hwc->getMultiDisplayObserver()->getVideoSourceInfo(
                instanceID, &info);
        if (err == NO_ERROR) {
            hz = info.frameRate;
        }
    }

    dev->setRefreshRate(hz);
}

void DisplayAnalyzer::handleVideoEvent(int instanceID, int state)
{
    mVideoStateMap.removeItem(instanceID);
    if (state != VIDEO_PLAYBACK_STOPPED) {
        mVideoStateMap.add(instanceID, state);
    }

    Hwcomposer *hwc = &Hwcomposer::getInstance();

    // sanity check
    if (hwc->getMultiDisplayObserver()->getVideoSessionNumber() !=
        (int)mVideoStateMap.size()) {
        WTRACE("session number does not match!!");
        mVideoStateMap.clear();
        if (state != VIDEO_PLAYBACK_STOPPED) {
            mVideoStateMap.add(instanceID, state);
        }
    }

    // check if composition type needs to be reset
    bool reset = false;
    if ((state == VIDEO_PLAYBACK_STARTING) ||
        (state == VIDEO_PLAYBACK_STOPPING && mProtectedVideoSession)) {
        // if video is in starting or stopping stage, overlay use is temporarily not allowed to
        // avoid scrambed RGB overlay if video is protected.
        mOverlayAllowed = false;
        reset = true;
    } else {
        reset = !mOverlayAllowed;
        mOverlayAllowed = true;
    }

    if (reset) {
        hwc_display_contents_1_t *content = NULL;
        for (int i = 0; i < (int)mCachedNumDisplays; i++) {
            setCompositionType(i, HWC_FRAMEBUFFER, true);
        }
    }

    if (mVideoStateMap.size() == 0) {
        // reset active input state after video playback stops.
        // MDS should update input state in 5 seconds after video playback starts
        mActiveInputState = true;
    }

    mProtectedVideoSession = false;
    if (state == VIDEO_PLAYBACK_STARTED) {
        VideoSourceInfo info;
        hwc->getMultiDisplayObserver()->getVideoSourceInfo(
                getFirstVideoInstanceSessionID(), &info);
        mProtectedVideoSession = info.isProtected;
    }
    // Setting timing immediately,
    // Don't posthone to next circle
    handleTimingEvent();

    handleVideoCheckEvent();
}

void DisplayAnalyzer::blankSecondaryDevice()
{
    hwc_display_contents_1_t *content = NULL;
    hwc_layer_1 *layer = NULL;
    for (int i = 0; i < (int)mCachedNumDisplays; i++) {
        if (i == IDisplayDevice::DEVICE_PRIMARY) {
            continue;
        }
        content = mCachedDisplays[i];
        if (content == NULL) {
            continue;
        }

        for (int j = 0; j < (int)content->numHwLayers - 1; j++) {
            layer = &content->hwLayers[j];
            if (!layer) {
                continue;
            }
            if (mBlankDevice) {
                layer->hints |= HWC_HINT_CLEAR_FB;
                layer->flags &= ~HWC_SKIP_LAYER;
                layer->compositionType = HWC_OVERLAY;
            } else {
                layer->hints &= ~HWC_HINT_CLEAR_FB;
                layer->compositionType = HWC_FRAMEBUFFER;
            }
        }
    }
}

void DisplayAnalyzer::handleInputEvent(bool active)
{
    if (active == mActiveInputState) {
        WTRACE("same input state: %d", active);
    }
    mActiveInputState = active;
    if (!mVideoExtModeEligible) {
        ITRACE("not eligible for video extended mode");
        return;
    }

    if (active) {
        exitVideoExtMode();
    } else {
        enterVideoExtMode();
    }
}

void DisplayAnalyzer::handleDpmsEvent(int delayCount)
{
    if (mActiveInputState || !mVideoExtModeEligible) {
        ITRACE("aborting display power off in video extended mode");
        return;
    }

    if (delayCount < DELAY_BEFORE_DPMS_OFF) {
        Event e;
        e.type = DPMS_EVENT;
        e.nValue = delayCount + 1;
        postEvent(e);
        Hwcomposer::getInstance().invalidate();
        return;
    }

    if (Hwcomposer::getInstance().getVsyncManager()->getVsyncSource() ==
        IDisplayDevice::DEVICE_PRIMARY) {
            Hwcomposer::getInstance().getDrm()->setDpmsMode(
            IDisplayDevice::DEVICE_PRIMARY,
                IDisplayDevice::DEVICE_DISPLAY_STANDBY);
        ETRACE("primary display is source of vsync, we only dim backlight");
        return;
    }

    // panel can't be powered off as touch panel shares the power supply with LCD.
    DTRACE("primary display coupled with touch on Saltbay, only dim backlight");
    Hwcomposer::getInstance().getDrm()->setDpmsMode(
               IDisplayDevice::DEVICE_PRIMARY,
               IDisplayDevice::DEVICE_DISPLAY_STANDBY);
               //IDisplayDevice::DEVICE_DISPLAY_OFF);
    return;
}


void DisplayAnalyzer::handleIdleEntryEvent(int count)
{
    DTRACE("handling idle entry event, count %d", count);
    if (hasProtectedLayer()) {
        ITRACE("Ignoring idle entry as protected layer exists.");
        setCompositionType(0, HWC_FRAMEBUFFER, true);
        return;
    }

    // stop idle entry if external device is connected
    if (mCachedDisplays && mCachedDisplays[IDisplayDevice::DEVICE_EXTERNAL]) {
        ITRACE("Ignoring idle entry as external device is connected.");
        setCompositionType(0, HWC_FRAMEBUFFER, true);
        return;
    }

    // stop idle entry if video playback is active
    // TODO: remove this check for Annidale
    if (mVideoStateMap.size() > 0) {
        ITRACE("Ignoring idle entry as video session is active.");
        setCompositionType(0, HWC_FRAMEBUFFER, true);
        return;
    }

    setCompositionType(0, HWC_FORCE_FRAMEBUFFER, true);

    // next prepare/set will exit idle state.
    Event e;
    e.type = IDLE_EXIT_EVENT;
    postEvent(e);
}

void DisplayAnalyzer::handleIdleExitEvent()
{
    DTRACE("handling idle exit event");

    setCompositionType(0, HWC_FRAMEBUFFER, true);
}

void DisplayAnalyzer::handleVideoCheckEvent()
{
    // check if the first seen video layer on secondary device (HDMI/WFD) is marked as skipped
    // it is assumed video is always skipped if the first seen video layer is skipped
    // this is to workaround secure video layer transmitted over non secure output
    // and HWC_SKIP_LAYER set during rotation animation.
    mIgnoreVideoSkipFlag = false;

    if (mVideoStateMap.size() != 1 ||
        mCachedNumDisplays <= 1) {
        return;
    }

    intptr_t videoHandles[mCachedNumDisplays];
    for (int i = 0; i < (int)mCachedNumDisplays; i++) {
        videoHandles[i] = 0;
        hwc_display_contents_1_t *content = mCachedDisplays[i];
        if (content == NULL) {
            continue;
        }
        for (int j = 0; j < (int)content->numHwLayers - 1; j++) {
            if (isVideoLayer(content->hwLayers[j])) {
                videoHandles[i] = (intptr_t)content->hwLayers[j].handle;
                if (i > 0) {
                    mIgnoreVideoSkipFlag = !(content->hwLayers[j].flags & HWC_SKIP_LAYER);
                    ITRACE("Ignoring video HWC_SKIP_LAYER: %d on output %d", mIgnoreVideoSkipFlag, i);
                    return;
                }
                break;
            }
        }
    }

    if (videoHandles[0]) {
        WTRACE("Video is on the primary panel only");
        return;
    }

    // video state map indicates video session is active and there is secondary
    // display, need to continue checking as video is not found in the buffers yet
    Event e;
    e.type = VIDEO_CHECK_EVENT;
    postEvent(e);
}

void DisplayAnalyzer::enterVideoExtMode()
{
    if (mVideoExtModeActive) {
        WTRACE("already in video extended mode.");
        return;
    }

    ITRACE("entering video extended mode...");
    mVideoExtModeActive = true;
    Hwcomposer::getInstance().getVsyncManager()->resetVsyncSource();

    setCompositionType(0, HWC_OVERLAY, true);

    // Do not power off primary display immediately as flip is asynchronous
    Event e;
    e.type = DPMS_EVENT;
    e.nValue = 0;
    postEvent(e);
    Hwcomposer::getInstance().invalidate();
}

void DisplayAnalyzer::exitVideoExtMode()
{
    if (!mVideoExtModeActive) {
        WTRACE("Not in video extended mode");
        return;
    }

    ITRACE("exiting video extended mode...");

    mVideoExtModeActive = false;

    Hwcomposer::getInstance().getDrm()->setDpmsMode(
        IDisplayDevice::DEVICE_PRIMARY,
        IDisplayDevice::DEVICE_DISPLAY_ON);

    Hwcomposer::getInstance().getVsyncManager()->resetVsyncSource();

    setCompositionType(0, HWC_FRAMEBUFFER, true);
}

bool DisplayAnalyzer::isPresentationLayer(hwc_layer_1_t &layer)
{
    if (layer.handle == NULL) {
        return false;
    }
    if (mCachedDisplays == NULL) {
        return false;
    }
    // check if the given layer exists in the primary device
    hwc_display_contents_1_t *content = mCachedDisplays[0];
    if (content == NULL) {
        return false;
    }
    for (size_t i = 0; i < content->numHwLayers - 1; i++) {
        if (content->hwLayers[i].handle == layer.handle) {
            VTRACE("Layer exists for Primary device");
            return false;
        }
    }
    return true;
}

bool DisplayAnalyzer::hasProtectedLayer()
{
    DataBuffer * buffer = NULL;
    hwc_display_contents_1_t *content = NULL;
    BufferManager *bm = Hwcomposer::getInstance().getBufferManager();

    if (bm == NULL){
        return false;
    }

    if (mCachedDisplays == NULL) {
        return false;
    }
    // check if the given layer exists in the primary device
    for (int index = 0; index < (int)mCachedNumDisplays; index++) {
        content = mCachedDisplays[index];
        if (content == NULL) {
            continue;
        }

        for (size_t i = 0; i < content->numHwLayers - 1; i++) {
            if (isProtectedLayer(content->hwLayers[i]))
                return true;
        }
    }

    return false;
}

bool DisplayAnalyzer::isProtectedLayer(hwc_layer_1_t &layer)
{
    if (!layer.handle) {
        return false;
    }
    bool ret = false;
    BufferManager *bm = Hwcomposer::getInstance().getBufferManager();
    DataBuffer *buffer = bm->lockDataBuffer(layer.handle);
    if (!buffer) {
        ETRACE("failed to get buffer");
    } else {
        ret = GraphicBuffer::isProtectedBuffer((GraphicBuffer*)buffer);
        bm->unlockDataBuffer(buffer);
    }
    return ret;
}

bool DisplayAnalyzer::ignoreVideoSkipFlag()
{
    return mIgnoreVideoSkipFlag;
}

void DisplayAnalyzer::setCompositionType(hwc_display_contents_1_t *display, int type)
{
    for (size_t i = 0; i < display->numHwLayers - 1; i++) {
        hwc_layer_1_t *layer = &display->hwLayers[i];
        if (layer) layer->compositionType = type;
    }
}

void DisplayAnalyzer::setCompositionType(int device, int type, bool reset)
{
    hwc_display_contents_1_t *content = mCachedDisplays[device];
    if (content == NULL) {
        ETRACE("Invalid device %d", device);
        return;
    }

    // don't need to set geometry changed if layers are just needed to be marked
    if (reset) {
        content->flags |= HWC_GEOMETRY_CHANGED;
    }

    setCompositionType(content, type);
}

int DisplayAnalyzer::getFirstVideoInstanceSessionID() {
    if (mVideoStateMap.size() >= 1) {
        return mVideoStateMap.keyAt(0);
    }
    return -1;
}

} // namespace intel
} // namespace android

