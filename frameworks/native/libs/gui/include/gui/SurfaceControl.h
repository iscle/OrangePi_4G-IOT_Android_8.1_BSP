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

#ifndef ANDROID_GUI_SURFACE_CONTROL_H
#define ANDROID_GUI_SURFACE_CONTROL_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/threads.h>

#include <ui/FrameStats.h>
#include <ui/PixelFormat.h>
#include <ui/Region.h>

#include <gui/ISurfaceComposerClient.h>

namespace android {

// ---------------------------------------------------------------------------

class IGraphicBufferProducer;
class Surface;
class SurfaceComposerClient;

// ---------------------------------------------------------------------------

class SurfaceControl : public RefBase
{
public:
    static bool isValid(const sp<SurfaceControl>& surface) {
        return (surface != 0) && surface->isValid();
    }

    bool isValid() {
        return mHandle!=0 && mClient!=0;
    }

    static bool isSameSurface(
            const sp<SurfaceControl>& lhs, const sp<SurfaceControl>& rhs);

    // release surface data from java
    void        clear();

    // disconnect any api that's connected
    void        disconnect();

    status_t    setLayerStack(uint32_t layerStack);
    status_t    setLayer(int32_t layer);

    // Sets a Z order relative to the Surface specified by "relativeTo" but
    // without becoming a full child of the relative. Z-ordering works exactly
    // as if it were a child however.
    //
    // As a nod to sanity, only non-child surfaces may have a relative Z-order.
    //
    // This overrides any previous and is overriden by any future calls
    // to setLayer.
    //
    // If the relative dissapears, the Surface will have no layer and be
    // invisible, until the next time set(Relative)Layer is called.
    //
    // TODO: This is probably a hack. Currently it exists only to work around
    // some framework usage of the hidden APPLICATION_MEDIA_OVERLAY window type
    // which allows inserting a window between a SurfaceView and it's main application
    // window. However, since we are using child windows for the SurfaceView, but not using
    // child windows elsewhere in O, the WindowManager can't set the layer appropriately.
    // This is only used by the "TvInputService" and following the port of ViewRootImpl
    // to child surfaces, we can then port this and remove this method.
    status_t    setRelativeLayer(const sp<IBinder>& relativeTo, int32_t layer);
    status_t    setPosition(float x, float y);
    status_t    setSize(uint32_t w, uint32_t h);
    status_t    hide();
    status_t    show();
    status_t    setFlags(uint32_t flags, uint32_t mask);
    status_t    setTransparentRegionHint(const Region& transparent);
    status_t    setAlpha(float alpha=1.0f);
    status_t    setMatrix(float dsdx, float dtdx, float dtdy, float dsdy);
    status_t    setCrop(const Rect& crop);
    status_t    setFinalCrop(const Rect& crop);

    // If the size changes in this transaction, all geometry updates specified
    // in this transaction will not complete until a buffer of the new size
    // arrives. As some elements normally apply immediately, this enables
    // freezing the total geometry of a surface until a resize is completed.
    status_t    setGeometryAppliesWithResize();

    // Defers applying any changes made in this transaction until the Layer
    // identified by handle reaches the given frameNumber. If the Layer identified
    // by handle is removed, then we will apply this transaction regardless of
    // what frame number has been reached.
    status_t deferTransactionUntil(const sp<IBinder>& handle, uint64_t frameNumber);

    // A variant of deferTransactionUntil which identifies the Layer we wait for by
    // Surface instead of Handle. Useful for clients which may not have the
    // SurfaceControl for some of their Surfaces. Otherwise behaves identically.
    status_t deferTransactionUntil(const sp<Surface>& barrier, uint64_t frameNumber);

    // Reparents all children of this layer to the new parent handle.
    status_t reparentChildren(const sp<IBinder>& newParentHandle);

    // Detaches all child surfaces (and their children recursively)
    // from their SurfaceControl.
    // The child SurfaceControl's will not throw exceptions or return errors,
    // but transactions will have no effect.
    // The child surfaces will continue to follow their parent surfaces,
    // and remain eligible for rendering, but their relative state will be
    // frozen. We use this in the WindowManager, in app shutdown/relaunch
    // scenarios, where the app would otherwise clean up its child Surfaces.
    // Sometimes the WindowManager needs to extend their lifetime slightly
    // in order to perform an exit animation or prevent flicker.
    status_t detachChildren();

    // Set an override scaling mode as documented in <system/window.h>
    // the override scaling mode will take precedence over any client
    // specified scaling mode. -1 will clear the override scaling mode.
    status_t setOverrideScalingMode(int32_t overrideScalingMode);

    static status_t writeSurfaceToParcel(
            const sp<SurfaceControl>& control, Parcel* parcel);

    sp<Surface> getSurface() const;
    sp<Surface> createSurface() const;
    sp<IBinder> getHandle() const;

    status_t clearLayerFrameStats() const;
    status_t getLayerFrameStats(FrameStats* outStats) const;

private:
    // can't be copied
    SurfaceControl& operator = (SurfaceControl& rhs);
    SurfaceControl(const SurfaceControl& rhs);

    friend class SurfaceComposerClient;
    friend class Surface;

    SurfaceControl(
            const sp<SurfaceComposerClient>& client,
            const sp<IBinder>& handle,
            const sp<IGraphicBufferProducer>& gbp);

    ~SurfaceControl();

    sp<Surface> generateSurfaceLocked() const;
    status_t validate() const;
    void destroy();

    sp<SurfaceComposerClient>   mClient;
    sp<IBinder>                 mHandle;
    sp<IGraphicBufferProducer>  mGraphicBufferProducer;
    mutable Mutex               mLock;
    mutable sp<Surface>         mSurfaceData;
};

}; // namespace android

#endif // ANDROID_GUI_SURFACE_CONTROL_H
