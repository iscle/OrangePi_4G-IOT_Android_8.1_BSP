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
#include <VsyncManager.h>


namespace android {
namespace intel {

VsyncManager::VsyncManager(Hwcomposer &hwc)
     :mHwc(hwc),
      mInitialized(false),
      mEnableDynamicVsync(true),
      mEnabled(false),
      mVsyncSource(IDisplayDevice::DEVICE_COUNT),
      mLock()
{
}

VsyncManager::~VsyncManager()
{
    WARN_IF_NOT_DEINIT();
}

bool VsyncManager::initialize()
{

    mEnabled = false;
    mVsyncSource = IDisplayDevice::DEVICE_COUNT;
    mEnableDynamicVsync = !scUsePrimaryVsyncOnly;
    mInitialized = true;
    return true;
}

void VsyncManager::deinitialize()
{
    if (mEnabled) {
        WTRACE("vsync is still enabled");
    }

    mVsyncSource = IDisplayDevice::DEVICE_COUNT;
    mEnabled = false;
    mEnableDynamicVsync = !scUsePrimaryVsyncOnly;
    mInitialized = false;
}

bool VsyncManager::handleVsyncControl(int disp, bool enabled)
{
    Mutex::Autolock l(mLock);

    if (disp != IDisplayDevice::DEVICE_PRIMARY) {
        WTRACE("vsync control on non-primary device %d", disp);
        return false;
    }

    if (mEnabled == enabled) {
        WTRACE("vsync state %d is not changed", enabled);
        return true;
    }

    if (!enabled) {
        disableVsync();
        mEnabled = false;
        return true;
    } else {
        mEnabled = enableVsync(getCandidate());
        return mEnabled;
    }

    return false;
}

void VsyncManager::resetVsyncSource()
{
    Mutex::Autolock l(mLock);

    if (!mEnableDynamicVsync) {
        ITRACE("dynamic vsync source switch is not supported");
        return;
    }

    if (!mEnabled) {
        return;
    }

    int vsyncSource = getCandidate();
    if (vsyncSource == mVsyncSource) {
        return;
    }

    disableVsync();
    enableVsync(vsyncSource);
}

int VsyncManager::getVsyncSource()
{
    return mVsyncSource;
}

void VsyncManager::enableDynamicVsync(bool enable)
{
    Mutex::Autolock l(mLock);
    if (scUsePrimaryVsyncOnly) {
        WTRACE("dynamic vsync is not supported");
        return;
    }

    mEnableDynamicVsync = enable;

    if (!mEnabled) {
        return;
    }

    int vsyncSource = getCandidate();
    if (vsyncSource == mVsyncSource) {
        return;
    }

    disableVsync();
    enableVsync(vsyncSource);
}

IDisplayDevice* VsyncManager::getDisplayDevice(int dispType ) {
    return mHwc.getDisplayDevice(dispType);
}

int VsyncManager::getCandidate()
{
    if (!mEnableDynamicVsync) {
        return IDisplayDevice::DEVICE_PRIMARY;
    }

    IDisplayDevice *device = NULL;
    // use HDMI vsync when connected
    device = getDisplayDevice(IDisplayDevice::DEVICE_EXTERNAL);
    if (device && device->isConnected()) {
        return IDisplayDevice::DEVICE_EXTERNAL;
    }

    // use vsync from virtual display when video extended mode is entered
    if (Hwcomposer::getInstance().getDisplayAnalyzer()->isVideoExtModeActive()) {
        device = getDisplayDevice(IDisplayDevice::DEVICE_VIRTUAL);
        if (device && device->isConnected()) {
            return IDisplayDevice::DEVICE_VIRTUAL;
        }
        WTRACE("Could not use vsync from secondary device");
    }
    return IDisplayDevice::DEVICE_PRIMARY;
}

bool VsyncManager::enableVsync(int candidate)
{
    if (mVsyncSource != IDisplayDevice::DEVICE_COUNT) {
        WTRACE("vsync has been enabled on %d", mVsyncSource);
        return true;
    }

    IDisplayDevice *device = getDisplayDevice(candidate);
    if (!device) {
        ETRACE("invalid vsync source candidate %d", candidate);
        return false;
    }

    if (device->vsyncControl(true)) {
        mVsyncSource = candidate;
        return true;
    }

    if (candidate != IDisplayDevice::DEVICE_PRIMARY) {
        WTRACE("failed to enable vsync on display %d, fall back to primary", candidate);
        device = getDisplayDevice(IDisplayDevice::DEVICE_PRIMARY);
        if (device && device->vsyncControl(true)) {
            mVsyncSource = IDisplayDevice::DEVICE_PRIMARY;
            return true;
        }
    }
    ETRACE("failed to enable vsync on the primary display");
    return false;
}

void VsyncManager::disableVsync()
{
    if (mVsyncSource == IDisplayDevice::DEVICE_COUNT) {
        WTRACE("vsync has been disabled");
        return;
    }

    IDisplayDevice *device = getDisplayDevice(mVsyncSource);
    if (device && !device->vsyncControl(false)) {
        WTRACE("failed to disable vsync on device %d", mVsyncSource);
    }
    mVsyncSource = IDisplayDevice::DEVICE_COUNT;
}

} // namespace intel
} // namespace android

