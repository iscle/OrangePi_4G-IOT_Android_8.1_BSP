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
#ifndef VSYNC_MANAGER_H
#define VSYNC_MANAGER_H

#include <IDisplayDevice.h>
#include <utils/threads.h>

namespace android {
namespace intel {


class Hwcomposer;

class VsyncManager {
public:
    VsyncManager(Hwcomposer& hwc);
    virtual ~VsyncManager();

public:
    bool initialize();
    void deinitialize();
    bool handleVsyncControl(int disp, bool enabled);
    void resetVsyncSource();
    int getVsyncSource();
    void enableDynamicVsync(bool enable);

private:
    inline int getCandidate();
    inline bool enableVsync(int candidate);
    inline void disableVsync();
    IDisplayDevice* getDisplayDevice(int dispType);

private:
    Hwcomposer &mHwc;
    bool mInitialized;
    bool mEnableDynamicVsync;
    bool mEnabled;
    int  mVsyncSource;
    Mutex mLock;

private:
    // toggle this constant to use primary vsync only or enable dynamic vsync.
    static const bool scUsePrimaryVsyncOnly = false;
};

} // namespace intel
} // namespace android



#endif /* VSYNC_MANAGER_H */
