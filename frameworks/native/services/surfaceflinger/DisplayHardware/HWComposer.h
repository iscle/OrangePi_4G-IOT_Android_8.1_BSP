/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef USE_HWC2
#include "HWComposer_hwc1.h"
#else

#ifndef ANDROID_SF_HWCOMPOSER_H
#define ANDROID_SF_HWCOMPOSER_H

#include "HWC2.h"

#include <stdint.h>
#include <sys/types.h>

#include <ui/Fence.h>

#include <utils/BitSet.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>
#include <utils/StrongPointer.h>
#include <utils/Thread.h>
#include <utils/Timers.h>
#include <utils/Vector.h>

#include <memory>
#include <set>
#include <vector>

extern "C" int clock_nanosleep(clockid_t clock_id, int flags,
                           const struct timespec *request,
                           struct timespec *remain);

struct framebuffer_device_t;

namespace HWC2 {
    class Device;
    class Display;
}

namespace android {
// ---------------------------------------------------------------------------

class DisplayDevice;
class Fence;
class FloatRect;
class GraphicBuffer;
class NativeHandle;
class Region;
class String8;

class HWComposer
{
public:
    // useVrComposer is passed to the composer HAL. When true, the composer HAL
    // will use the vr composer service, otherwise it uses the real hardware
    // composer.
    HWComposer(bool useVrComposer);

    ~HWComposer();

    void registerCallback(HWC2::ComposerCallback* callback,
                          int32_t sequenceId);

    bool hasCapability(HWC2::Capability capability) const;

    // Attempts to allocate a virtual display. If the virtual display is created
    // on the HWC device, outId will contain its HWC ID.
    status_t allocateVirtualDisplay(uint32_t width, uint32_t height,
            android_pixel_format_t* format, int32_t* outId);

    // Attempts to create a new layer on this display
    HWC2::Layer* createLayer(int32_t displayId);
    // Destroy a previously created layer
    void destroyLayer(int32_t displayId, HWC2::Layer* layer);

    // Asks the HAL what it can do
    status_t prepare(DisplayDevice& displayDevice);

    status_t setClientTarget(int32_t displayId, uint32_t slot,
            const sp<Fence>& acquireFence,
            const sp<GraphicBuffer>& target, android_dataspace_t dataspace);

    // Present layers to the display and read releaseFences.
    status_t presentAndGetReleaseFences(int32_t displayId);

    // set power mode
    status_t setPowerMode(int32_t displayId, int mode);

    // set active config
    status_t setActiveConfig(int32_t displayId, size_t configId);

    // Sets a color transform to be applied to the result of composition
    status_t setColorTransform(int32_t displayId, const mat4& transform);

    // reset state when an external, non-virtual display is disconnected
    void disconnectDisplay(int32_t displayId);

    // does this display have layers handled by HWC
    bool hasDeviceComposition(int32_t displayId) const;

    // does this display have layers handled by GLES
    bool hasClientComposition(int32_t displayId) const;

    // get the present fence received from the last call to present.
    sp<Fence> getPresentFence(int32_t displayId) const;

    // Get last release fence for the given layer
    sp<Fence> getLayerReleaseFence(int32_t displayId,
            HWC2::Layer* layer) const;

    // Set the output buffer and acquire fence for a virtual display.
    // Returns INVALID_OPERATION if displayId is not a virtual display.
    status_t setOutputBuffer(int32_t displayId, const sp<Fence>& acquireFence,
            const sp<GraphicBuffer>& buf);

    // After SurfaceFlinger has retrieved the release fences for all the frames,
    // it can call this to clear the shared pointers in the release fence map
    void clearReleaseFences(int32_t displayId);

    // Returns the HDR capabilities of the given display
    std::unique_ptr<HdrCapabilities> getHdrCapabilities(int32_t displayId);

    // Events handling ---------------------------------------------------------

    // Returns true if successful, false otherwise. The
    // DisplayDevice::DisplayType of the display is returned as an output param.
    bool onVsync(hwc2_display_t displayId, int64_t timestamp,
                 int32_t* outDisplay);
    void onHotplug(hwc2_display_t displayId, HWC2::Connection connection);

    void setVsyncEnabled(int32_t displayId, HWC2::Vsync enabled);

    // Query display parameters.  Pass in a display index (e.g.
    // HWC_DISPLAY_PRIMARY).
    nsecs_t getRefreshTimestamp(int32_t displayId) const;
    bool isConnected(int32_t displayId) const;

    // Non-const because it can update configMap inside of mDisplayData
    std::vector<std::shared_ptr<const HWC2::Display::Config>>
            getConfigs(int32_t displayId) const;

    std::shared_ptr<const HWC2::Display::Config>
            getActiveConfig(int32_t displayId) const;

    std::vector<android_color_mode_t> getColorModes(int32_t displayId) const;

    status_t setActiveColorMode(int32_t displayId, android_color_mode_t mode);

    bool isUsingVrComposer() const;

    // for debugging ----------------------------------------------------------
    void dump(String8& out) const;

    android::Hwc2::Composer* getComposer() const { return mHwcDevice->getComposer(); }
private:
    static const int32_t VIRTUAL_DISPLAY_ID_BASE = 2;

    bool isValidDisplay(int32_t displayId) const;
    static void validateChange(HWC2::Composition from, HWC2::Composition to);

    struct cb_context;

    struct DisplayData {
        DisplayData();
        ~DisplayData();
        void reset();

        bool hasClientComposition;
        bool hasDeviceComposition;
        HWC2::Display* hwcDisplay;
        HWC2::DisplayRequest displayRequests;
        sp<Fence> lastPresentFence;  // signals when the last set op retires
        std::unordered_map<HWC2::Layer*, sp<Fence>> releaseFences;
        buffer_handle_t outbufHandle;
        sp<Fence> outbufAcquireFence;
        mutable std::unordered_map<int32_t,
                std::shared_ptr<const HWC2::Display::Config>> configMap;

        // protected by mVsyncLock
        HWC2::Vsync vsyncEnabled;

        bool validateWasSkipped;
        HWC2::Error presentError;
    };

    std::unique_ptr<HWC2::Device>   mHwcDevice;
    std::vector<DisplayData>        mDisplayData;
    std::set<size_t>                mFreeDisplaySlots;
    std::unordered_map<hwc2_display_t, int32_t> mHwcDisplaySlots;
    // protect mDisplayData from races between prepare and dump
    mutable Mutex mDisplayLock;

    cb_context*                     mCBContext;
    size_t                          mVSyncCounts[HWC_NUM_PHYSICAL_DISPLAY_TYPES];
    uint32_t                        mRemainingHwcVirtualDisplays;

    // protected by mLock
    mutable Mutex mLock;
    mutable std::unordered_map<int32_t, nsecs_t> mLastHwVSync;

    // thread-safe
    mutable Mutex mVsyncLock;
};

// ---------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_SF_HWCOMPOSER_H

#endif // #ifdef USE_HWC2
