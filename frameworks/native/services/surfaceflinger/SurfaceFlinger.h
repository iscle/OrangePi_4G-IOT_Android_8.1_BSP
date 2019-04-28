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

#ifndef ANDROID_SURFACE_FLINGER_H
#define ANDROID_SURFACE_FLINGER_H

#include <memory>
#include <stdint.h>
#include <sys/types.h>

#include <EGL/egl.h>

/*
 * NOTE: Make sure this file doesn't include  anything from <gl/ > or <gl2/ >
 */

#include <cutils/compiler.h>

#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/SortedVector.h>
#include <utils/threads.h>

#include <ui/FenceTime.h>
#include <ui/PixelFormat.h>
#include <math/mat4.h>

#include <gui/FrameTimestamps.h>
#include <gui/ISurfaceComposer.h>
#include <gui/ISurfaceComposerClient.h>
#include <gui/OccupancyTracker.h>

#include <hardware/hwcomposer_defs.h>

#include <system/graphics.h>

#include <private/gui/LayerState.h>

#include "Barrier.h"
#include "DisplayDevice.h"
#include "DispSync.h"
#include "FrameTracker.h"
#include "LayerVector.h"
#include "MessageQueue.h"
#include "SurfaceInterceptor.h"
#include "StartPropertySetThread.h"

#ifdef USE_HWC2
#include "DisplayHardware/HWC2.h"
#include "DisplayHardware/HWComposer.h"
#else
#include "DisplayHardware/HWComposer_hwc1.h"
#endif

#include "Effects/Daltonizer.h"

#ifdef MTK_SF_DEBUG_SUPPORT
#include "RenderEngine/RenderEngine.h"
#endif

#ifdef MTK_SF_WATCHDOG_SUPPORT
#include "sf_debug/SurfaceFlingerWatchDogAPI.h"
#endif

#include <map>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <utility>

#ifdef MTK_SF_HW_ROTATION_SUPPORT
#include "mediatek/CorrectHwRotationAPI.h"
#endif

namespace android {

// ---------------------------------------------------------------------------

class Client;
class DisplayEventConnection;
class EventThread;
class Layer;
class LayerDim;
class Surface;
class RenderEngine;
class EventControlThread;
class VSyncSource;
class InjectVSyncSource;

namespace dvr {
class VrFlinger;
} // namespace dvr

// ---------------------------------------------------------------------------

enum {
    eTransactionNeeded        = 0x01,
    eTraversalNeeded          = 0x02,
    eDisplayTransactionNeeded = 0x04,
    eTransactionMask          = 0x07
};

class SurfaceFlinger : public BnSurfaceComposer,
                       private IBinder::DeathRecipient,
#ifdef USE_HWC2
                       private HWC2::ComposerCallback
#else
                       private HWComposer::EventHandler
#endif
{
public:

    // This is the phase offset in nanoseconds of the software vsync event
    // relative to the vsync event reported by HWComposer.  The software vsync
    // event is when SurfaceFlinger and Choreographer-based applications run each
    // frame.
    //
    // This phase offset allows adjustment of the minimum latency from application
    // wake-up time (by Choreographer) to the time at which the resulting window
    // image is displayed.  This value may be either positive (after the HW vsync)
    // or negative (before the HW vsync). Setting it to 0 will result in a lower
    // latency bound of two vsync periods because the app and SurfaceFlinger
    // will run just after the HW vsync.  Setting it to a positive number will
    // result in the minimum latency being:
    //
    //     (2 * VSYNC_PERIOD - (vsyncPhaseOffsetNs % VSYNC_PERIOD))
    //
    // Note that reducing this latency makes it more likely for the applications
    // to not have their window content image ready in time.  When this happens
    // the latency will end up being an additional vsync period, and animations
    // will hiccup.  Therefore, this latency should be tuned somewhat
    // conservatively (or at least with awareness of the trade-off being made).
    static int64_t vsyncPhaseOffsetNs;
    static int64_t sfVsyncPhaseOffsetNs;

    // If fences from sync Framework are supported.
    static bool hasSyncFramework;

    // Instruct the Render Engine to use EGL_IMG_context_priority is available.
    static bool useContextPriority;

    // The offset in nanoseconds to use when DispSync timestamps present fence
    // signaling time.
    static int64_t dispSyncPresentTimeOffset;

    // Some hardware can do RGB->YUV conversion more efficiently in hardware
    // controlled by HWC than in hardware controlled by the video encoder.
    // This instruct VirtualDisplaySurface to use HWC for such conversion on
    // GL composition.
    static bool useHwcForRgbToYuv;

    // Maximum dimension supported by HWC for virtual display.
    // Equal to min(max_height, max_width).
    static uint64_t maxVirtualDisplaySize;

    // Controls the number of buffers SurfaceFlinger will allocate for use in
    // FramebufferSurface
    static int64_t maxFrameBufferAcquiredBuffers;

    // Indicate if platform supports color management on its
    // wide-color display. This is typically found on devices
    // with wide gamut (e.g. Display-P3) display.
    // This also allows devices with wide-color displays that don't
    // want to support color management to disable color management.
    static bool hasWideColorDisplay;

    static char const* getServiceName() ANDROID_API {
        return "SurfaceFlinger";
    }

    SurfaceFlinger() ANDROID_API;

    // must be called before clients can connect
    void init() ANDROID_API;

    // starts SurfaceFlinger main loop in the current thread
    void run() ANDROID_API;

    enum {
        EVENT_VSYNC = HWC_EVENT_VSYNC
    };

    // post an asynchronous message to the main thread
    status_t postMessageAsync(const sp<MessageBase>& msg, nsecs_t reltime = 0, uint32_t flags = 0);

    // post a synchronous message to the main thread
    status_t postMessageSync(const sp<MessageBase>& msg, nsecs_t reltime = 0, uint32_t flags = 0);

    // force full composition on all displays
    void repaintEverything();
    // Can only be called from the main thread or with mStateLock held
    void repaintEverythingLocked();

    // returns the default Display
    sp<const DisplayDevice> getDefaultDisplayDevice() const {
        Mutex::Autolock _l(mStateLock);
        return getDefaultDisplayDeviceLocked();
    }

    // utility function to delete a texture on the main thread
    void deleteTextureAsync(uint32_t texture);

    // enable/disable h/w composer event
    // TODO: this should be made accessible only to EventThread
#ifdef USE_HWC2
    void setVsyncEnabled(int disp, int enabled);
#else
    void eventControl(int disp, int event, int enabled);
#endif

    // called on the main thread by MessageQueue when an internal message
    // is received
    // TODO: this should be made accessible only to MessageQueue
    void onMessageReceived(int32_t what);

    // for debugging only
    // TODO: this should be made accessible only to HWComposer
    const Vector< sp<Layer> >& getLayerSortedByZForHwcDisplay(int id);

    RenderEngine& getRenderEngine() const {
        return *mRenderEngine;
    }

    bool authenticateSurfaceTextureLocked(
        const sp<IGraphicBufferProducer>& bufferProducer) const;

private:
    friend class Client;
    friend class DisplayEventConnection;
    friend class EventThread;
    friend class Layer;
    friend class MonitoredProducer;
#ifdef MTK_SF_DEBUG_SUPPORT
    friend class DisplayDevice;
#endif
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    friend class DispSync;
#endif

    // This value is specified in number of frames.  Log frame stats at most
    // every half hour.
    enum { LOG_FRAME_STATS_PERIOD =  30*60*60 };

    static const size_t MAX_LAYERS = 4096;

    // We're reference counted, never destroy SurfaceFlinger directly
    virtual ~SurfaceFlinger();

    /* ------------------------------------------------------------------------
     * Internal data structures
     */

    class State {
    public:
        explicit State(LayerVector::StateSet set) : stateSet(set) {}
        State& operator=(const State& other) {
            // We explicitly don't copy stateSet so that, e.g., mDrawingState
            // always uses the Drawing StateSet.
            layersSortedByZ = other.layersSortedByZ;
            displays = other.displays;
            return *this;
        }

        const LayerVector::StateSet stateSet = LayerVector::StateSet::Invalid;
        LayerVector layersSortedByZ;
        DefaultKeyedVector< wp<IBinder>, DisplayDeviceState> displays;

        void traverseInZOrder(const LayerVector::Visitor& visitor) const;
        void traverseInReverseZOrder(const LayerVector::Visitor& visitor) const;
    };

    /* ------------------------------------------------------------------------
     * IBinder interface
     */
    virtual status_t onTransact(uint32_t code, const Parcel& data,
        Parcel* reply, uint32_t flags);
    virtual status_t dump(int fd, const Vector<String16>& args);

    /* ------------------------------------------------------------------------
     * ISurfaceComposer interface
     */
    virtual sp<ISurfaceComposerClient> createConnection();
    virtual sp<ISurfaceComposerClient> createScopedConnection(const sp<IGraphicBufferProducer>& gbp);
    virtual sp<IBinder> createDisplay(const String8& displayName, bool secure);
    virtual void destroyDisplay(const sp<IBinder>& display);
    virtual sp<IBinder> getBuiltInDisplay(int32_t id);
    virtual void setTransactionState(const Vector<ComposerState>& state,
            const Vector<DisplayState>& displays, uint32_t flags);
    virtual void bootFinished();
    virtual bool authenticateSurfaceTexture(
        const sp<IGraphicBufferProducer>& bufferProducer) const;
    virtual status_t getSupportedFrameTimestamps(
            std::vector<FrameEvent>* outSupported) const;
    virtual sp<IDisplayEventConnection> createDisplayEventConnection(
            ISurfaceComposer::VsyncSource vsyncSource = eVsyncSourceApp);
    virtual status_t captureScreen(const sp<IBinder>& display,
            const sp<IGraphicBufferProducer>& producer,
            Rect sourceCrop, uint32_t reqWidth, uint32_t reqHeight,
            int32_t minLayerZ, int32_t maxLayerZ,
            bool useIdentityTransform, ISurfaceComposer::Rotation rotation);
    virtual status_t getDisplayStats(const sp<IBinder>& display,
            DisplayStatInfo* stats);
    virtual status_t getDisplayConfigs(const sp<IBinder>& display,
            Vector<DisplayInfo>* configs);
    virtual int getActiveConfig(const sp<IBinder>& display);
    virtual status_t getDisplayColorModes(const sp<IBinder>& display,
            Vector<android_color_mode_t>* configs);
    virtual android_color_mode_t getActiveColorMode(const sp<IBinder>& display);
    virtual status_t setActiveColorMode(const sp<IBinder>& display, android_color_mode_t colorMode);
    virtual void setPowerMode(const sp<IBinder>& display, int mode);
    virtual status_t setActiveConfig(const sp<IBinder>& display, int id);
    virtual status_t clearAnimationFrameStats();
    virtual status_t getAnimationFrameStats(FrameStats* outStats) const;
    virtual status_t getHdrCapabilities(const sp<IBinder>& display,
            HdrCapabilities* outCapabilities) const;
    virtual status_t enableVSyncInjections(bool enable);
    virtual status_t injectVSync(nsecs_t when);


    /* ------------------------------------------------------------------------
     * DeathRecipient interface
     */
    virtual void binderDied(const wp<IBinder>& who);

    /* ------------------------------------------------------------------------
     * RefBase interface
     */
    virtual void onFirstRef();

    /* ------------------------------------------------------------------------
     * HWC2::ComposerCallback / HWComposer::EventHandler interface
     */
#ifdef USE_HWC2
    void onVsyncReceived(int32_t sequenceId, hwc2_display_t display,
                         int64_t timestamp) override;
    void onHotplugReceived(int32_t sequenceId, hwc2_display_t display,
                           HWC2::Connection connection,
                           bool primaryDisplay) override;
    void onRefreshReceived(int32_t sequenceId, hwc2_display_t display) override;
#else
    void onVSyncReceived(HWComposer* composer, int type, nsecs_t timestamp) override;
    void onHotplugReceived(HWComposer* composer, int disp, bool connected) override;
    void onInvalidateReceived(HWComposer* composer) override;
#endif

    /* ------------------------------------------------------------------------
     * Message handling
     */
    void waitForEvent();
    // Can only be called from the main thread or with mStateLock held
    void signalTransaction();
    // Can only be called from the main thread or with mStateLock held
    void signalLayerUpdate();
    void signalRefresh();

    // called on the main thread in response to initializeDisplays()
    void onInitializeDisplays();
    // called on the main thread in response to setActiveConfig()
    void setActiveConfigInternal(const sp<DisplayDevice>& hw, int mode);
    // called on the main thread in response to setPowerMode()
#ifdef USE_HWC2
    void setPowerModeInternal(const sp<DisplayDevice>& hw, int mode,
                              bool stateLockHeld);
#else
    void setPowerModeInternal(const sp<DisplayDevice>& hw, int mode);
#endif

    // Called on the main thread in response to setActiveColorMode()
    void setActiveColorModeInternal(const sp<DisplayDevice>& hw, android_color_mode_t colorMode);

    // Called on the main thread in response to enableVSyncInjections()
    void enableVSyncInjectionsInternal(bool enable);

    // Returns whether the transaction actually modified any state
    bool handleMessageTransaction();

    // Returns whether a new buffer has been latched (see handlePageFlip())
    bool handleMessageInvalidate();

    void handleMessageRefresh();

    void handleTransaction(uint32_t transactionFlags);
    void handleTransactionLocked(uint32_t transactionFlags);

    void updateCursorAsync();

    /* handlePageFlip - latch a new buffer if available and compute the dirty
     * region. Returns whether a new buffer has been latched, i.e., whether it
     * is necessary to perform a refresh during this vsync.
     */
    bool handlePageFlip();

    /* ------------------------------------------------------------------------
     * Transactions
     */
    uint32_t getTransactionFlags(uint32_t flags);
    uint32_t peekTransactionFlags();
    // Can only be called from the main thread or with mStateLock held
    uint32_t setTransactionFlags(uint32_t flags);
    void commitTransaction();
    uint32_t setClientStateLocked(const sp<Client>& client, const layer_state_t& s);
    uint32_t setDisplayStateLocked(const DisplayState& s);

    /* ------------------------------------------------------------------------
     * Layer management
     */
    status_t createLayer(const String8& name, const sp<Client>& client,
            uint32_t w, uint32_t h, PixelFormat format, uint32_t flags,
            uint32_t windowType, uint32_t ownerUid, sp<IBinder>* handle,
            sp<IGraphicBufferProducer>* gbp, sp<Layer>* parent);

    status_t createNormalLayer(const sp<Client>& client, const String8& name,
            uint32_t w, uint32_t h, uint32_t flags, PixelFormat& format,
            sp<IBinder>* outHandle, sp<IGraphicBufferProducer>* outGbp,
            sp<Layer>* outLayer);

    status_t createDimLayer(const sp<Client>& client, const String8& name,
            uint32_t w, uint32_t h, uint32_t flags, sp<IBinder>* outHandle,
            sp<IGraphicBufferProducer>* outGbp, sp<Layer>* outLayer);

    String8 getUniqueLayerName(const String8& name);

    // called in response to the window-manager calling
    // ISurfaceComposerClient::destroySurface()
    status_t onLayerRemoved(const sp<Client>& client, const sp<IBinder>& handle);

    // called when all clients have released all their references to
    // this layer meaning it is entirely safe to destroy all
    // resources associated to this layer.
    status_t onLayerDestroyed(const wp<Layer>& layer);

    // remove a layer from SurfaceFlinger immediately
    status_t removeLayer(const sp<Layer>& layer, bool topLevelOnly = false);

    // add a layer to SurfaceFlinger
    status_t addClientLayer(const sp<Client>& client,
            const sp<IBinder>& handle,
            const sp<IGraphicBufferProducer>& gbc,
            const sp<Layer>& lbc,
            const sp<Layer>& parent);

    /* ------------------------------------------------------------------------
     * Boot animation, on/off animations and screen capture
     */

    void startBootAnim();

    void renderScreenImplLocked(
            const sp<const DisplayDevice>& hw,
            Rect sourceCrop, uint32_t reqWidth, uint32_t reqHeight,
            int32_t minLayerZ, int32_t maxLayerZ,
            bool yswap, bool useIdentityTransform, Transform::orientation_flags rotation);

#ifdef USE_HWC2
    status_t captureScreenImplLocked(const sp<const DisplayDevice>& device,
                                     ANativeWindowBuffer* buffer, Rect sourceCrop,
                                     uint32_t reqWidth, uint32_t reqHeight, int32_t minLayerZ,
                                     int32_t maxLayerZ, bool useIdentityTransform,
                                     Transform::orientation_flags rotation, bool isLocalScreenshot,
                                     int* outSyncFd);
#else
    status_t captureScreenImplLocked(
            const sp<const DisplayDevice>& hw,
            const sp<IGraphicBufferProducer>& producer,
            Rect sourceCrop, uint32_t reqWidth, uint32_t reqHeight,
            int32_t minLayerZ, int32_t maxLayerZ,
            bool useIdentityTransform, Transform::orientation_flags rotation,
            bool isLocalScreenshot);
#endif

    sp<StartPropertySetThread> mStartPropertySetThread = nullptr;

    /* ------------------------------------------------------------------------
     * Properties
     */
    void readPersistentProperties();

    /* ------------------------------------------------------------------------
     * EGL
     */
    size_t getMaxTextureSize() const;
    size_t getMaxViewportDims() const;

    /* ------------------------------------------------------------------------
     * Display and layer stack management
     */
    // called when starting, or restarting after system_server death
    void initializeDisplays();

    // Create an IBinder for a builtin display and add it to current state
    void createBuiltinDisplayLocked(DisplayDevice::DisplayType type);


    sp<const DisplayDevice> getDisplayDevice(const wp<IBinder>& dpy) const {
      Mutex::Autolock _l(mStateLock);
      return getDisplayDeviceLocked(dpy);
    }

    sp<DisplayDevice> getDisplayDevice(const wp<IBinder>& dpy) {
      Mutex::Autolock _l(mStateLock);
      return getDisplayDeviceLocked(dpy);
    }

    // NOTE: can only be called from the main thread or with mStateLock held
    sp<const DisplayDevice> getDisplayDeviceLocked(const wp<IBinder>& dpy) const {
        return mDisplays.valueFor(dpy);
    }

    // NOTE: can only be called from the main thread or with mStateLock held
    sp<DisplayDevice> getDisplayDeviceLocked(const wp<IBinder>& dpy) {
        return mDisplays.valueFor(dpy);
    }

    sp<const DisplayDevice> getDefaultDisplayDeviceLocked() const {
        return getDisplayDeviceLocked(mBuiltinDisplays[DisplayDevice::DISPLAY_PRIMARY]);
    }

    void createDefaultDisplayDevice();

    int32_t getDisplayType(const sp<IBinder>& display) {
        if (!display.get()) return NAME_NOT_FOUND;
        for (int i = 0; i < DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES; ++i) {
            if (display == mBuiltinDisplays[i]) {
                return i;
            }
        }
        return NAME_NOT_FOUND;
    }

    // mark a region of a layer stack dirty. this updates the dirty
    // region of all screens presenting this layer stack.
    void invalidateLayerStack(const sp<const Layer>& layer, const Region& dirty);

#ifndef USE_HWC2
    int32_t allocateHwcDisplayId(DisplayDevice::DisplayType type);
#endif

    /* ------------------------------------------------------------------------
     * H/W composer
     */

    HWComposer& getHwComposer() const { return *mHwc; }

    /* ------------------------------------------------------------------------
     * Compositing
     */
    void invalidateHwcGeometry();
    void computeVisibleRegions(const sp<const DisplayDevice>& displayDevice,
            Region& dirtyRegion, Region& opaqueRegion);

    void preComposition(nsecs_t refreshStartTime);
    void postComposition(nsecs_t refreshStartTime);
    void updateCompositorTiming(
            nsecs_t vsyncPhase, nsecs_t vsyncInterval, nsecs_t compositeTime,
            std::shared_ptr<FenceTime>& presentFenceTime);
    void setCompositorTimingSnapped(
            nsecs_t vsyncPhase, nsecs_t vsyncInterval,
            nsecs_t compositeToPresentLatency);
    void rebuildLayerStacks();

    // Given a dataSpace, returns the appropriate color_mode to use
    // to display that dataSpace.
    android_color_mode pickColorMode(android_dataspace dataSpace) const;
    android_dataspace bestTargetDataSpace(android_dataspace a, android_dataspace b) const;

    mat4 computeSaturationMatrix() const;

    void setUpHWComposer();
    void doComposition();
    void doDebugFlashRegions();
    void doDisplayComposition(const sp<const DisplayDevice>& displayDevice, const Region& dirtyRegion);

    // compose surfaces for display hw. this fails if using GL and the surface
    // has been destroyed and is no longer valid.
    bool doComposeSurfaces(const sp<const DisplayDevice>& displayDevice, const Region& dirty);

    void postFramebuffer();
    void drawWormhole(const sp<const DisplayDevice>& displayDevice, const Region& region) const;

    /* ------------------------------------------------------------------------
     * Display management
     */

    /* ------------------------------------------------------------------------
     * VSync
     */
    void enableHardwareVsync();
    void resyncToHardwareVsync(bool makeAvailable);
    void disableHardwareVsync(bool makeUnavailable);

public:
    void resyncWithRateLimit();
    void getCompositorTiming(CompositorTiming* compositorTiming);
private:

    /* ------------------------------------------------------------------------
     * Debugging & dumpsys
     */
    void listLayersLocked(const Vector<String16>& args, size_t& index, String8& result) const;
    void dumpStatsLocked(const Vector<String16>& args, size_t& index, String8& result) const;
    void clearStatsLocked(const Vector<String16>& args, size_t& index, String8& result);
    void dumpAllLocked(const Vector<String16>& args, size_t& index, String8& result) const;
    bool startDdmConnection();
    void appendSfConfigString(String8& result) const;
    void checkScreenshot(size_t w, size_t s, size_t h, void const* vaddr,
            const sp<const DisplayDevice>& hw,
            int32_t minLayerZ, int32_t maxLayerZ);

    void logFrameStats();

    void dumpStaticScreenStats(String8& result) const;
    // Not const because each Layer needs to query Fences and cache timestamps.
    void dumpFrameEventsLocked(String8& result);

    void recordBufferingStats(const char* layerName,
            std::vector<OccupancyTracker::Segment>&& history);
    void dumpBufferingStats(String8& result) const;
    void dumpWideColorInfo(String8& result) const;

    bool isLayerTripleBufferingDisabled() const {
        return this->mLayerTripleBufferingDisabled;
    }

    static status_t getProcessName(int pid, String8& name);
#ifdef USE_HWC2
    /* ------------------------------------------------------------------------
     * VrFlinger
     */
    void resetDisplayState();

    // Check to see if we should handoff to vr flinger.
    void updateVrFlinger();
#endif

    /* ------------------------------------------------------------------------
     * Attributes
     */

    // access must be protected by mStateLock
    mutable Mutex mStateLock;
    State mCurrentState{LayerVector::StateSet::Current};
    volatile int32_t mTransactionFlags;
    Condition mTransactionCV;
    bool mTransactionPending;
    bool mAnimTransactionPending;
    SortedVector< sp<Layer> > mLayersPendingRemoval;
    SortedVector< wp<IBinder> > mGraphicBufferProducerList;

    // protected by mStateLock (but we could use another lock)
    bool mLayersRemoved;
    bool mLayersAdded;

    // access must be protected by mInvalidateLock
    volatile int32_t mRepaintEverything;

    // The current hardware composer interface.
    //
    // The following thread safety rules apply when accessing mHwc, either
    // directly or via getHwComposer():
    //
    // 1. When recreating mHwc, acquire mStateLock. We currently recreate mHwc
    //    only when switching into and out of vr. Recreating mHwc must only be
    //    done on the main thread.
    //
    // 2. When accessing mHwc on the main thread, it's not necessary to acquire
    //    mStateLock.
    //
    // 3. When accessing mHwc on a thread other than the main thread, we always
    //    need to acquire mStateLock. This is because the main thread could be
    //    in the process of destroying the current mHwc instance.
    //
    // The above thread safety rules only apply to SurfaceFlinger.cpp. In
    // SurfaceFlinger_hwc1.cpp we create mHwc at surface flinger init and never
    // destroy it, so it's always safe to access mHwc from any thread without
    // acquiring mStateLock.
    std::unique_ptr<HWComposer> mHwc;

    // constant members (no synchronization needed for access)
    RenderEngine* mRenderEngine;
    nsecs_t mBootTime;
    bool mGpuToCpuSupported;
    sp<EventThread> mEventThread;
    sp<EventThread> mSFEventThread;
    sp<EventThread> mInjectorEventThread;
    sp<InjectVSyncSource> mVSyncInjector;
    sp<EventControlThread> mEventControlThread;
    EGLContext mEGLContext;
    EGLDisplay mEGLDisplay;
    sp<IBinder> mBuiltinDisplays[DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES];

    // Can only accessed from the main thread, these members
    // don't need synchronization
    State mDrawingState{LayerVector::StateSet::Drawing};
    bool mVisibleRegionsDirty;
#ifndef USE_HWC2
    bool mHwWorkListDirty;
#else
    bool mGeometryInvalid;
#endif
    bool mAnimCompositionPending;
#ifdef USE_HWC2
    std::vector<sp<Layer>> mLayersWithQueuedFrames;
    sp<Fence> mPreviousPresentFence = Fence::NO_FENCE;
    bool mHadClientComposition = false;
#endif
    FenceTimeline mGlCompositionDoneTimeline;
    FenceTimeline mDisplayTimeline;

    // this may only be written from the main thread with mStateLock held
    // it may be read from other threads with mStateLock held
    DefaultKeyedVector< wp<IBinder>, sp<DisplayDevice> > mDisplays;

    // don't use a lock for these, we don't care
    int mDebugRegion;
    int mDebugDDMS;
    int mDebugDisableHWC;
    int mDebugDisableTransformHint;
    volatile nsecs_t mDebugInSwapBuffers;
    nsecs_t mLastSwapBufferTime;
    volatile nsecs_t mDebugInTransaction;
    nsecs_t mLastTransactionTime;
    bool mBootFinished;
    bool mForceFullDamage;
#ifdef USE_HWC2
    bool mPropagateBackpressure = true;
#endif
    SurfaceInterceptor mInterceptor;
    bool mUseHwcVirtualDisplays = false;

    // Restrict layers to use two buffers in their bufferqueues.
    bool mLayerTripleBufferingDisabled = false;

    // these are thread safe
    mutable MessageQueue mEventQueue;
    FrameTracker mAnimFrameTracker;
    DispSync mPrimaryDispSync;

    // protected by mDestroyedLayerLock;
    mutable Mutex mDestroyedLayerLock;
    Vector<Layer const *> mDestroyedLayers;

    // protected by mHWVsyncLock
    Mutex mHWVsyncLock;
    bool mPrimaryHWVsyncEnabled;
    bool mHWVsyncAvailable;

    // protected by mCompositorTimingLock;
    mutable std::mutex mCompositorTimingLock;
    CompositorTiming mCompositorTiming;

    // Only accessed from the main thread.
    struct CompositePresentTime {
        nsecs_t composite { -1 };
        std::shared_ptr<FenceTime> display { FenceTime::NO_FENCE };
    };
    std::queue<CompositePresentTime> mCompositePresentTimes;

    std::atomic<bool> mRefreshPending{false};

    /* ------------------------------------------------------------------------
     * Feature prototyping
     */

    bool mInjectVSyncs;

    Daltonizer mDaltonizer;
#ifndef USE_HWC2
    bool mDaltonize;
#endif

    mat4 mPreviousColorMatrix;
    mat4 mColorMatrix;
    bool mHasColorMatrix;

    // Static screen stats
    bool mHasPoweredOff;
    static const size_t NUM_BUCKETS = 8; // < 1-7, 7+
    nsecs_t mFrameBuckets[NUM_BUCKETS];
    nsecs_t mTotalTime;
    std::atomic<nsecs_t> mLastSwapTime;

    size_t mNumLayers;

    // Double- vs. triple-buffering stats
    struct BufferingStats {
        BufferingStats()
          : numSegments(0),
            totalTime(0),
            twoBufferTime(0),
            doubleBufferedTime(0),
            tripleBufferedTime(0) {}

        size_t numSegments;
        nsecs_t totalTime;

        // "Two buffer" means that a third buffer was never used, whereas
        // "double-buffered" means that on average the segment only used two
        // buffers (though it may have used a third for some part of the
        // segment)
        nsecs_t twoBufferTime;
        nsecs_t doubleBufferedTime;
        nsecs_t tripleBufferedTime;
    };
    mutable Mutex mBufferingStatsMutex;
    std::unordered_map<std::string, BufferingStats> mBufferingStats;

    // Verify that transaction is being called by an approved process:
    // either AID_GRAPHICS or AID_SYSTEM.
    status_t CheckTransactCodeCredentials(uint32_t code);

#ifdef MTK_AOSP_DISPLAY_BUGFIX
private:
    std::vector< sp<Client> > mReservedClientPointer;
#endif

#ifdef USE_HWC2
    std::unique_ptr<dvr::VrFlinger> mVrFlinger;
    std::atomic<bool> mVrFlingerRequestsDisplay;
    static bool useVrFlinger;
    std::thread::id mMainThreadId;
    // The composer sequence id is a monotonically increasing integer that we
    // use to differentiate callbacks from different hardware composer
    // instances. Each hardware composer instance gets a different sequence id.
    int32_t mComposerSequenceId;
#endif

    float mSaturation = 1.0f;
    bool mForceNativeColorMode = false;
#ifdef MTK_SF_HW_ROTATION_SUPPORT
public:
    CorrectHwRotationAPI* getCorrectHwRotation() { return mCorrectHwRotation; }
private:
    void* mCorrectHwRotationSoHandle;
    CorrectHwRotationAPI* mCorrectHwRotation;
#endif

#ifdef MTK_ADJUST_FD_LIMIT
private:
    void adjustFdLimit();
#endif

#ifdef MTK_SF_DEBUG_SUPPORT
private:
    // used to avoid race condition between handleMessageRefresh() and dumpAllLocked()
    mutable Mutex mDumpLock;

    // for debug
    void setMTKProperties();
    void setMTKProperties(String8 &result);

public:
    // helper class for collect related property settings
    struct PropertiesState {
        PropertiesState()
            : mHwRotation(0)
            , mLogRepaint(false)
            , mLogTransaction(false)
            , mLineG3D(false)
            , mLineSS(false)
            , mDumpScreenShot(0)
            , mDelayTime(0)
        { }

        // for phyical panel rotation info
        int mHwRotation;

        // sf repaint log info
        bool mLogRepaint;

        // log layer state transaction
        bool mLogTransaction;

        // debug G3D render
        bool mLineG3D;

        // debug screenshot
        bool mLineSS;

        // debug screen shot result, enabled if value > 0, and increases after each dump
        uint32_t mDumpScreenShot;

        // for enabling slow motion
        uint32_t mDelayTime;
    };
    static PropertiesState sPropertiesState;
#endif

#ifdef MTK_SF_WATCHDOG_SUPPORT
    void* mSFWathDogSoHandle;

    // for SF watchdog
    SFWatchDogAPI* mMessageWDT;
#endif

#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
private:
    // transact MTK binder request
    status_t onMtkTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);

    // for adjust vsync fps
    void adjustSwVsyncPeriod(int32_t fps);

    // for adjust vsync offset
    void adjustSwVsyncOffset(nsecs_t offset);
#endif
};
}; // namespace android

#endif // ANDROID_SURFACE_FLINGER_H
