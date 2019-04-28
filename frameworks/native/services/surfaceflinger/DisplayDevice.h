/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_DISPLAY_DEVICE_H
#define ANDROID_DISPLAY_DEVICE_H

#include "Transform.h"

#include <stdlib.h>

#ifndef USE_HWC2
#include <ui/PixelFormat.h>
#endif
#include <ui/Region.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>

#ifdef USE_HWC2
#include <binder/IBinder.h>
#include <utils/RefBase.h>
#endif
#include <utils/Mutex.h>
#include <utils/String8.h>
#include <utils/Timers.h>

#include <hardware/hwcomposer_defs.h>

#ifdef USE_HWC2
#include <memory>
#endif

#ifdef MTK_SF_DEBUG_SUPPORT
#include <ui_ext/FpsCounter.h>
#endif // MTK_SF_DEBUG_SUPPORT

struct ANativeWindow;

namespace android {

struct DisplayInfo;
class DisplaySurface;
class Fence;
class IGraphicBufferProducer;
class Layer;
class SurfaceFlinger;
class HWComposer;

class DisplayDevice : public LightRefBase<DisplayDevice>
{
public:
    // region in layer-stack space
    mutable Region dirtyRegion;
    // region in screen space
    mutable Region swapRegion;
    // region in screen space
    Region undefinedRegion;
    bool lastCompositionHadVisibleLayers;

    enum DisplayType {
        DISPLAY_ID_INVALID = -1,
        DISPLAY_PRIMARY     = HWC_DISPLAY_PRIMARY,
        DISPLAY_EXTERNAL    = HWC_DISPLAY_EXTERNAL,
        DISPLAY_VIRTUAL     = HWC_DISPLAY_VIRTUAL,
        NUM_BUILTIN_DISPLAY_TYPES = HWC_NUM_PHYSICAL_DISPLAY_TYPES,
    };

    enum {
        PARTIAL_UPDATES = 0x00020000, // video driver feature
        SWAP_RECTANGLE  = 0x00080000,
    };

    enum {
        NO_LAYER_STACK = 0xFFFFFFFF,
    };

    // clang-format off
    DisplayDevice(
            const sp<SurfaceFlinger>& flinger,
            DisplayType type,
            int32_t hwcId,
#ifndef USE_HWC2
            int format,
#endif
            bool isSecure,
            const wp<IBinder>& displayToken,
            const sp<DisplaySurface>& displaySurface,
            const sp<IGraphicBufferProducer>& producer,
            EGLConfig config,
            bool supportWideColor);
    // clang-format on

    ~DisplayDevice();

    // whether this is a valid object. An invalid DisplayDevice is returned
    // when an non existing id is requested
    bool isValid() const;

    // isSecure indicates whether this display can be trusted to display
    // secure surfaces.
    bool isSecure() const { return mIsSecure; }

    // Flip the front and back buffers if the back buffer is "dirty".  Might
    // be instantaneous, might involve copying the frame buffer around.
    void flip(const Region& dirty) const;

    int         getWidth() const;
    int         getHeight() const;
#ifndef USE_HWC2
    PixelFormat getFormat() const;
#endif
    uint32_t    getFlags() const;

    EGLSurface  getEGLSurface() const;

    void                    setVisibleLayersSortedByZ(const Vector< sp<Layer> >& layers);
    const Vector< sp<Layer> >& getVisibleLayersSortedByZ() const;
    Region                  getDirtyRegion(bool repaintEverything) const;

    void                    setLayerStack(uint32_t stack);
    void                    setDisplaySize(const int newWidth, const int newHeight);
    void                    setProjection(int orientation, const Rect& viewport, const Rect& frame);

    int                     getOrientation() const { return mOrientation; }
    uint32_t                getOrientationTransform() const;
    static uint32_t         getPrimaryDisplayOrientationTransform();
    const Transform&        getTransform() const { return mGlobalTransform; }
    const Rect              getViewport() const { return mViewport; }
    const Rect              getFrame() const { return mFrame; }
    const Rect&             getScissor() const { return mScissor; }
    bool                    needsFiltering() const { return mNeedsFiltering; }

    uint32_t                getLayerStack() const { return mLayerStack; }
    int32_t                 getDisplayType() const { return mType; }
    bool                    isPrimary() const { return mType == DISPLAY_PRIMARY; }
    int32_t                 getHwcDisplayId() const { return mHwcDisplayId; }
    const wp<IBinder>&      getDisplayToken() const { return mDisplayToken; }

    // We pass in mustRecompose so we can keep VirtualDisplaySurface's state
    // machine happy without actually queueing a buffer if nothing has changed
    status_t beginFrame(bool mustRecompose) const;
#ifdef USE_HWC2
    status_t prepareFrame(HWComposer& hwc);
    bool getWideColorSupport() const { return mDisplayHasWideColor; }
#else
    status_t prepareFrame(const HWComposer& hwc) const;
#endif

    void swapBuffers(HWComposer& hwc) const;
#ifndef USE_HWC2
    status_t compositionComplete() const;
#endif

    // called after h/w composer has completed its set() call
#ifdef USE_HWC2
    void onSwapBuffersCompleted() const;
#else
    void onSwapBuffersCompleted(HWComposer& hwc) const;
#endif

    Rect getBounds() const {
        return Rect(mDisplayWidth, mDisplayHeight);
    }
    inline Rect bounds() const { return getBounds(); }

    void setDisplayName(const String8& displayName);
    const String8& getDisplayName() const { return mDisplayName; }

    EGLBoolean makeCurrent(EGLDisplay dpy, EGLContext ctx) const;
    void setViewportAndProjection() const;

    const sp<Fence>& getClientTargetAcquireFence() const;

    /* ------------------------------------------------------------------------
     * Display power mode management.
     */
    int getPowerMode() const;
    void setPowerMode(int mode);
    bool isDisplayOn() const;

#ifdef USE_HWC2
    android_color_mode_t getActiveColorMode() const;
    void setActiveColorMode(android_color_mode_t mode);
    void setCompositionDataSpace(android_dataspace dataspace);
#endif

    /* ------------------------------------------------------------------------
     * Display active config management.
     */
    int getActiveConfig() const;
    void setActiveConfig(int mode);

    // release HWC resources (if any) for removable displays
    void disconnect(HWComposer& hwc);

    /* ------------------------------------------------------------------------
     * Debugging
     */
    uint32_t getPageFlipCount() const;
    void dump(String8& result) const;

private:
    /*
     *  Constants, set during initialization
     */
    sp<SurfaceFlinger> mFlinger;
    DisplayType mType;
    int32_t mHwcDisplayId;
    wp<IBinder> mDisplayToken;

    // ANativeWindow this display is rendering into
    sp<ANativeWindow> mNativeWindow;
    sp<DisplaySurface> mDisplaySurface;

    EGLConfig       mConfig;
    EGLDisplay      mDisplay;
    EGLSurface      mSurface;
    int             mDisplayWidth;
    int             mDisplayHeight;
#ifndef USE_HWC2
    PixelFormat     mFormat;
#endif
    uint32_t        mFlags;
    mutable uint32_t mPageFlipCount;
    String8         mDisplayName;
    bool            mIsSecure;

    /*
     * Can only accessed from the main thread, these members
     * don't need synchronization.
     */

    // list of visible layers on that display
    Vector< sp<Layer> > mVisibleLayersSortedByZ;

    /*
     * Transaction state
     */
    static status_t orientationToTransfrom(int orientation,
            int w, int h, Transform* tr);

    // The identifier of the active layer stack for this display. Several displays
    // can use the same layer stack: A z-ordered group of layers (sometimes called
    // "surfaces"). Any given layer can only be on a single layer stack.
    uint32_t mLayerStack;

    int mOrientation;
    static uint32_t sPrimaryDisplayOrientation;
    // user-provided visible area of the layer stack
    Rect mViewport;
    // user-provided rectangle where mViewport gets mapped to
    Rect mFrame;
    // pre-computed scissor to apply to the display
    Rect mScissor;
    Transform mGlobalTransform;
    bool mNeedsFiltering;
    // Current power mode
    int mPowerMode;
    // Current active config
    int mActiveConfig;
#ifdef USE_HWC2
    // current active color mode
    android_color_mode_t mActiveColorMode;

    // Need to know if display is wide-color capable or not.
    // Initialized by SurfaceFlinger when the DisplayDevice is created.
    // Fed to RenderEngine during composition.
    bool mDisplayHasWideColor;
#endif

#ifdef MTK_SF_DEBUG_SUPPORT
private:
    // debugging
    void drawDebugLine() const;

    // for performance check
    mutable FpsCounter mFps;
#endif // MTK_SF_DEBUG_SUPPORT

};

struct DisplayDeviceState {
    DisplayDeviceState() = default;
    DisplayDeviceState(DisplayDevice::DisplayType type, bool isSecure);

    bool isValid() const { return type >= 0; }
    bool isMainDisplay() const { return type == DisplayDevice::DISPLAY_PRIMARY; }
    bool isVirtualDisplay() const { return type >= DisplayDevice::DISPLAY_VIRTUAL; }

    static std::atomic<int32_t> nextDisplayId;
    int32_t displayId = nextDisplayId++;
    DisplayDevice::DisplayType type = DisplayDevice::DISPLAY_ID_INVALID;
    sp<IGraphicBufferProducer> surface;
    uint32_t layerStack = DisplayDevice::NO_LAYER_STACK;
    Rect viewport;
    Rect frame;
    uint8_t orientation = 0;
    uint32_t width = 0;
    uint32_t height = 0;
    String8 displayName;
    bool isSecure = false;
};

}; // namespace android

#endif // ANDROID_DISPLAY_DEVICE_H
