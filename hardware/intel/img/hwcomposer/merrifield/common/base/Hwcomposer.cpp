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
#include <Hwcomposer.h>
#include <Dump.h>
#include <UeventObserver.h>

namespace android {
namespace intel {

Hwcomposer* Hwcomposer::sInstance(0);

Hwcomposer::Hwcomposer(IPlatFactory *factory)
    : mProcs(0),
      mDrm(0),
      mPlatFactory(factory),
      mVsyncManager(0),
      mDisplayAnalyzer(0),
      mMultiDisplayObserver(0),
      mUeventObserver(0),
      mPlaneManager(0),
      mBufferManager(0),
      mDisplayContext(0),
      mInitialized(false)
{
    CTRACE();

    mDisplayDevices.setCapacity(IDisplayDevice::DEVICE_COUNT);
    mDisplayDevices.clear();
}

Hwcomposer::~Hwcomposer()
{
    CTRACE();
    deinitialize();
}

bool Hwcomposer::initCheck() const
{
    return mInitialized;
}

bool Hwcomposer::prepare(size_t numDisplays,
                          hwc_display_contents_1_t** displays)
{
    bool ret = true;

    RETURN_FALSE_IF_NOT_INIT();
    ATRACE("display count = %d", numDisplays);

    if (!numDisplays || !displays) {
        ETRACE("invalid parameters");
        return false;
    }

    mDisplayAnalyzer->analyzeContents(numDisplays, displays);

    // disable reclaimed planes
    mPlaneManager->disableReclaimedPlanes();

        if(numDisplays > mDisplayDevices.size())
                numDisplays = mDisplayDevices.size();

    // reclaim all allocated planes if possible
    for (size_t i = 0; i < numDisplays; i++) {
        IDisplayDevice *device = mDisplayDevices.itemAt(i);
        if (!device) {
            VTRACE("device %d doesn't exist", i);
            continue;
        }

        if (device->getType() == IDisplayDevice::DEVICE_VIRTUAL)
            continue;

        device->prePrepare(displays[i]);
    }

    for (size_t i = 0; i < numDisplays; i++) {
        IDisplayDevice *device = mDisplayDevices.itemAt(i);
        if (!device) {
            VTRACE("device %d doesn't exist", i);
            continue;
        }

        if (device->getType() == IDisplayDevice::DEVICE_VIRTUAL)
            continue;

        ret = device->prepare(displays[i]);
        if (ret == false) {
            ETRACE("failed to do prepare for device %d", i);
            continue;
        }
    }

    return ret;
}

bool Hwcomposer::commit(size_t numDisplays,
                         hwc_display_contents_1_t **displays)
{
    bool ret = true;

    RETURN_FALSE_IF_NOT_INIT();
    ATRACE("display count = %d", numDisplays);

    if (!numDisplays || !displays) {
        ETRACE("invalid parameters");
        return false;
    }

        if(numDisplays > mDisplayDevices.size())
                numDisplays = mDisplayDevices.size();

    mDisplayContext->commitBegin(numDisplays, displays);

    for (size_t i = 0; i < numDisplays; i++) {
        IDisplayDevice *device = mDisplayDevices.itemAt(i);
        if (!device) {
            VTRACE("device %d doesn't exist", i);
            continue;
        }

        if (!device->isConnected()) {
            VTRACE("device %d is disconnected", i);
            continue;
        }

        if (device->getType() == IDisplayDevice::DEVICE_VIRTUAL)
            continue;

        ret = device->commit(displays[i], mDisplayContext);
        if (ret == false) {
            ETRACE("failed to do commit for device %d", i);
            continue;
        }
    }

    mDisplayContext->commitEnd(numDisplays, displays);
    // return true always
    return true;
}

bool Hwcomposer::setPowerMode(int disp, int mode)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return false;
    }

    if(disp >= mDisplayDevices.size()){
        ETRACE("no device found");
        return false;
    }

    IDisplayDevice *device = mDisplayDevices.itemAt(disp);
    if (!device) {
        ETRACE("no device found");
        return false;
    }

    return device->setPowerMode(mode);
}

int Hwcomposer::getActiveConfig(int disp)
{
    RETURN_NULL_IF_NOT_INIT();

    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return -1;
    }

    IDisplayDevice *device = mDisplayDevices.itemAt(disp);
    if (!device) {
        ETRACE("no device found");
        return -1;
    }

    return device->getActiveConfig();
}

bool Hwcomposer::setActiveConfig(int disp, int index)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return false;
    }

    IDisplayDevice *device = mDisplayDevices.itemAt(disp);
    if (!device) {
        ETRACE("no device found");
        return false;
    }

    return device->setActiveConfig(index);
}

bool Hwcomposer::setCursorPositionAsync(int disp, int x, int y)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (disp != HWC_DISPLAY_PRIMARY && disp != HWC_DISPLAY_EXTERNAL) {
        ETRACE("invalid disp %d", disp);
        return false;
    }

    return mDisplayContext->setCursorPosition(disp, x, y);
}

bool Hwcomposer::vsyncControl(int disp, int enabled)
{
    RETURN_FALSE_IF_NOT_INIT();
    ATRACE("disp = %d, enabled = %d", disp, enabled);
    return mVsyncManager->handleVsyncControl(disp, enabled ? true : false);
}

bool Hwcomposer::blank(int disp, int blank)
{
    RETURN_FALSE_IF_NOT_INIT();
    ATRACE("disp = %d, blank = %d", disp, blank);

    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return false;
    }

    IDisplayDevice *device = mDisplayDevices.itemAt(disp);
    if (!device) {
        ETRACE("no device found");
        return false;
    }

    return device->blank(blank ? true : false);
}

bool Hwcomposer::getDisplayConfigs(int disp,
                                      uint32_t *configs,
                                      size_t *numConfigs)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return false;
    }

    if(disp >= mDisplayDevices.size()){
        ETRACE("no device found");
        return false;
    }

    IDisplayDevice *device = mDisplayDevices.itemAt(disp);
    if (!device) {
        ETRACE("no device %d found", disp);
        return false;
    }

    return device->getDisplayConfigs(configs, numConfigs);
}

bool Hwcomposer::getDisplayAttributes(int disp,
                                         uint32_t config,
                                         const uint32_t *attributes,
                                         int32_t *values)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return false;
    }
    if(disp >= mDisplayDevices.size()){
        ETRACE("no device found");
        return false;
    }


    IDisplayDevice *device = mDisplayDevices.itemAt(disp);
    if (!device) {
        ETRACE("no device found");
        return false;
    }

    return device->getDisplayAttributes(config, attributes, values);
}

bool Hwcomposer::compositionComplete(int disp)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return false;
    }

    mDisplayContext->compositionComplete();

    if(disp >= mDisplayDevices.size()){
        ETRACE("no device found");
        return false;
    }

    IDisplayDevice *device = mDisplayDevices.itemAt(disp);
    if (!device) {
        ETRACE("no device found");
        return false;
    }

    return device->compositionComplete();
}

void Hwcomposer::vsync(int disp, int64_t timestamp)
{
    RETURN_VOID_IF_NOT_INIT();

    if (mProcs && mProcs->vsync) {
        VTRACE("report vsync on disp %d, timestamp %llu", disp, timestamp);
        // workaround to pretend vsync is from primary display
        // Display will freeze if vsync is from external display.
        mProcs->vsync(const_cast<hwc_procs_t*>(mProcs), IDisplayDevice::DEVICE_PRIMARY, timestamp);
    }
}

void Hwcomposer::hotplug(int disp, bool connected)
{
    RETURN_VOID_IF_NOT_INIT();

    // TODO: Two fake hotplug events are sent during mode setting. To avoid
    // unnecessary audio switch, real connection status should be sent to MDS
    mMultiDisplayObserver->notifyHotPlug(mDrm->isConnected(disp));

    if (mProcs && mProcs->hotplug) {
        DTRACE("report hotplug on disp %d, connected %d", disp, connected);
        mProcs->hotplug(const_cast<hwc_procs_t*>(mProcs), disp, connected);
        DTRACE("hotplug callback processed and returned!");
    }

    mDisplayAnalyzer->postHotplugEvent(connected);
}

void Hwcomposer::invalidate()
{
    RETURN_VOID_IF_NOT_INIT();

    if (mProcs && mProcs->invalidate) {
        DTRACE("invalidating screen...");
        mProcs->invalidate(const_cast<hwc_procs_t*>(mProcs));
    }
}

bool Hwcomposer::release()
{
    RETURN_FALSE_IF_NOT_INIT();

    return true;
}

bool Hwcomposer::dump(char *buff, int buff_len, int *cur_len)
{
    RETURN_FALSE_IF_NOT_INIT();

    Dump d(buff, buff_len);

    // dump composer status
    d.append("Hardware Composer state:");
    // dump device status
    for (size_t i= 0; i < mDisplayDevices.size(); i++) {
        IDisplayDevice *device = mDisplayDevices.itemAt(i);
        if (device)
            device->dump(d);
    }

    // dump plane manager status
    if (mPlaneManager)
        mPlaneManager->dump(d);

    // dump buffer manager status
    if (mBufferManager)
        mBufferManager->dump(d);

    return true;
}

void Hwcomposer::registerProcs(hwc_procs_t const *procs)
{
    CTRACE();

    if (!procs) {
        WTRACE("procs is NULL");
    }
    mProcs = procs;
}

bool Hwcomposer::initialize()
{
    CTRACE();

    // create drm
    mDrm = new Drm();
    if (!mDrm || !mDrm->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to create DRM");
    }

    if (!mPlatFactory){
        DEINIT_AND_RETURN_FALSE("failed to provide a PlatFactory");
    }

    // create buffer manager
    mBufferManager = mPlatFactory->createBufferManager();
    if (!mBufferManager || !mBufferManager->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to create buffer manager");
    }

    // create display plane manager
    mPlaneManager = mPlatFactory->createDisplayPlaneManager();
    if (!mPlaneManager || !mPlaneManager->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to create display plane manager");
    }

    mDisplayContext = mPlatFactory->createDisplayContext();
    if (!mDisplayContext || !mDisplayContext->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to create display context");
    }

    mUeventObserver = new UeventObserver();
    if (!mUeventObserver || !mUeventObserver->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to initialize uevent observer");
    }

    // create display device
    mDisplayDevices.clear();
    for (int i = 0; i < IDisplayDevice::DEVICE_COUNT; i++) {
        IDisplayDevice *device = mPlatFactory->createDisplayDevice(i);
        if (!device || !device->initialize()) {
            DEINIT_AND_DELETE_OBJ(device);
            DEINIT_AND_RETURN_FALSE("failed to create device %d", i);
        }
        // add this device
        ETRACE("HWC devices initialize device is %p at %d", device, i);
        mDisplayDevices.insertAt(device, i, 1);
    }

    mVsyncManager = new VsyncManager(*this);
    if (!mVsyncManager || !mVsyncManager->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to create Vsync Manager");
    }

    mDisplayAnalyzer = new DisplayAnalyzer();
    if (!mDisplayAnalyzer || !mDisplayAnalyzer->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to initialize display analyzer");
    }

    mMultiDisplayObserver = new MultiDisplayObserver();
    if (!mMultiDisplayObserver || !mMultiDisplayObserver->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to initialize display observer");
    }

    // all initialized, starting uevent observer
    mUeventObserver->start();

    mInitialized = true;
    return true;
}

void Hwcomposer::deinitialize()
{
    DEINIT_AND_DELETE_OBJ(mMultiDisplayObserver);
    DEINIT_AND_DELETE_OBJ(mDisplayAnalyzer);
    // delete mVsyncManager first as it holds reference to display devices.
    DEINIT_AND_DELETE_OBJ(mVsyncManager);

    DEINIT_AND_DELETE_OBJ(mUeventObserver);
    // destroy display devices
    for (size_t i = 0; i < mDisplayDevices.size(); i++) {
        IDisplayDevice *device = mDisplayDevices.itemAt(i);
        DEINIT_AND_DELETE_OBJ(device);
    }
    mDisplayDevices.clear();

    if (mPlatFactory) {
        delete mPlatFactory;
        mPlatFactory = 0;
    }

    DEINIT_AND_DELETE_OBJ(mDisplayContext);
    DEINIT_AND_DELETE_OBJ(mPlaneManager);
    DEINIT_AND_DELETE_OBJ(mBufferManager);
    DEINIT_AND_DELETE_OBJ(mDrm);
    mInitialized = false;
}

Drm* Hwcomposer::getDrm()
{
    return mDrm;
}

DisplayPlaneManager* Hwcomposer::getPlaneManager()
{
    return mPlaneManager;
}

BufferManager* Hwcomposer::getBufferManager()
{
    return mBufferManager;
}

IDisplayContext* Hwcomposer::getDisplayContext()
{
    return mDisplayContext;
}

DisplayAnalyzer* Hwcomposer::getDisplayAnalyzer()
{
    return mDisplayAnalyzer;
}

MultiDisplayObserver* Hwcomposer::getMultiDisplayObserver()
{
    return mMultiDisplayObserver;
}

IDisplayDevice* Hwcomposer::getDisplayDevice(int disp)
{
    if (disp < 0 || disp >= IDisplayDevice::DEVICE_COUNT) {
        ETRACE("invalid disp %d", disp);
        return NULL;
    }
    return mDisplayDevices.itemAt(disp);
}

VsyncManager* Hwcomposer::getVsyncManager()
{
    return mVsyncManager;
}

UeventObserver* Hwcomposer::getUeventObserver()
{
    return mUeventObserver;
}

} // namespace intel
} // namespace android
