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
#ifndef HWCOMPOSER_H
#define HWCOMPOSER_H

#include <EGL/egl.h>
#include <hardware/hwcomposer.h>
#include <utils/Vector.h>

#include <IDisplayDevice.h>
#include <BufferManager.h>
#include <IDisplayContext.h>
#include <Drm.h>
#include <DisplayPlaneManager.h>
#include <DisplayAnalyzer.h>
#include <VsyncManager.h>
#include <MultiDisplayObserver.h>
#include <UeventObserver.h>
#include <IPlatFactory.h>


namespace android {
namespace intel {

class Hwcomposer : public hwc_composer_device_1_t {
public:
    virtual ~Hwcomposer();
public:
    // callbacks implementation
    virtual bool prepare(size_t numDisplays,
                           hwc_display_contents_1_t** displays);
    virtual bool commit(size_t numDisplays,
                           hwc_display_contents_1_t** displays);
    virtual bool vsyncControl(int disp, int enabled);
    virtual bool release();
    virtual bool dump(char *buff, int buff_len, int *cur_len);
    virtual void registerProcs(hwc_procs_t const *procs);

    virtual bool blank(int disp, int blank);
    virtual bool getDisplayConfigs(int disp,
                                       uint32_t *configs,
                                       size_t *numConfigs);
    virtual bool getDisplayAttributes(int disp,
                                          uint32_t config,
                                          const uint32_t *attributes,
                                          int32_t *values);
    virtual bool compositionComplete(int disp);

    virtual bool setPowerMode(int disp, int mode);
    virtual int  getActiveConfig(int disp);
    virtual bool setActiveConfig(int disp, int index);
    virtual bool setCursorPositionAsync(int disp, int x, int y);

    // callbacks
    virtual void vsync(int disp, int64_t timestamp);
    virtual void hotplug(int disp, bool connected);
    virtual void invalidate();

    virtual bool initCheck() const;
    virtual bool initialize();
    virtual void deinitialize();


public:
    Drm* getDrm();
    DisplayPlaneManager* getPlaneManager();
    BufferManager* getBufferManager();
    IDisplayContext* getDisplayContext();
    DisplayAnalyzer* getDisplayAnalyzer();
    VsyncManager* getVsyncManager();
    MultiDisplayObserver* getMultiDisplayObserver();
    IDisplayDevice* getDisplayDevice(int disp);
    UeventObserver* getUeventObserver();
    IPlatFactory* getPlatFactory() {return mPlatFactory;}
protected:
    Hwcomposer(IPlatFactory *factory);

public:
    static Hwcomposer& getInstance() {
        Hwcomposer *instance = sInstance;
        if (instance == 0) {
            instance = createHwcomposer();
            sInstance = instance;
        }
        return *sInstance;
    }
    static void releaseInstance() {
        delete sInstance;
        sInstance = NULL;
    }
    // Need to be implemented
    static Hwcomposer* createHwcomposer();


private:
    hwc_procs_t const *mProcs;
    Drm *mDrm;

    // plugin through set
    IPlatFactory *mPlatFactory;
    VsyncManager *mVsyncManager;
    DisplayAnalyzer *mDisplayAnalyzer;
    MultiDisplayObserver *mMultiDisplayObserver;
    UeventObserver *mUeventObserver;

    // created from IPlatFactory
    DisplayPlaneManager *mPlaneManager;
    BufferManager *mBufferManager;
    IDisplayContext *mDisplayContext;

    Vector<IDisplayDevice*> mDisplayDevices;

    bool mInitialized;



    static Hwcomposer *sInstance;
};

} // namespace intel
}

#endif /*HW_COMPOSER_H*/
