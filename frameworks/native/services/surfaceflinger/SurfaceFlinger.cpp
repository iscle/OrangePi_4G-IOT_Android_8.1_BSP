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

// #define LOG_NDEBUG 0
#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include <stdint.h>
#include <sys/types.h>
#include <algorithm>
#include <errno.h>
#include <math.h>
#include <mutex>
#include <dlfcn.h>
#include <inttypes.h>
#include <stdatomic.h>
#include <optional>

#include <EGL/egl.h>

#include <cutils/properties.h>
#include <log/log.h>

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/PermissionCache.h>

#include <dvr/vr_flinger.h>

#include <ui/DebugUtils.h>
#include <ui/DisplayInfo.h>
#include <ui/DisplayStatInfo.h>

#include <gui/BufferQueue.h>
#include <gui/GuiConfig.h>
#include <gui/IDisplayEventConnection.h>
#include <gui/Surface.h>

#include <ui/GraphicBufferAllocator.h>
#include <ui/PixelFormat.h>
#include <ui/UiConfig.h>

#include <utils/misc.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <utils/StopWatch.h>
#include <utils/Timers.h>
#include <utils/Trace.h>

#include <private/android_filesystem_config.h>
#include <private/gui/SyncFeatures.h>

#include "Client.h"
#include "clz.h"
#include "Colorizer.h"
#include "DdmConnection.h"
#include "DisplayDevice.h"
#include "DispSync.h"
#include "EventControlThread.h"
#include "EventThread.h"
#include "Layer.h"
#include "LayerVector.h"
#include "LayerDim.h"
#include "MonitoredProducer.h"
#include "SurfaceFlinger.h"

#include "DisplayHardware/ComposerHal.h"
#include "DisplayHardware/FramebufferSurface.h"
#include "DisplayHardware/HWComposer.h"
#include "DisplayHardware/VirtualDisplaySurface.h"

#include "Effects/Daltonizer.h"

#include "RenderEngine/RenderEngine.h"
#include <cutils/compiler.h>

#include <android/hardware/configstore/1.0/ISurfaceFlingerConfigs.h>
#include <configstore/Utils.h>

#ifdef MTK_SF_WATCHDOG_SUPPORT
#include "sf_debug/SurfaceFlingerWatchDogAPI.h"
#endif

#ifdef MTK_SF_DEBUG_SUPPORT
#include <ui_ext/GraphicBufferUtil.h>
#endif

#define DISPLAY_COUNT       1

/*
 * DEBUG_SCREENSHOTS: set to true to check that screenshots are not all
 * black pixels.
 */
#define DEBUG_SCREENSHOTS   false

extern "C" EGLAPI const char* eglQueryStringImplementationANDROID(EGLDisplay dpy, EGLint name);

namespace android {

using namespace android::hardware::configstore;
using namespace android::hardware::configstore::V1_0;

namespace {
class ConditionalLock {
public:
    ConditionalLock(Mutex& mutex, bool lock) : mMutex(mutex), mLocked(lock) {
        if (lock) {
            mMutex.lock();
        }
    }
    ~ConditionalLock() { if (mLocked) mMutex.unlock(); }
private:
    Mutex& mMutex;
    bool mLocked;
};
}  // namespace anonymous

// ---------------------------------------------------------------------------

const String16 sHardwareTest("android.permission.HARDWARE_TEST");
const String16 sAccessSurfaceFlinger("android.permission.ACCESS_SURFACE_FLINGER");
const String16 sReadFramebuffer("android.permission.READ_FRAME_BUFFER");
const String16 sDump("android.permission.DUMP");

// ---------------------------------------------------------------------------
int64_t SurfaceFlinger::vsyncPhaseOffsetNs;
int64_t SurfaceFlinger::sfVsyncPhaseOffsetNs;
bool SurfaceFlinger::useContextPriority;
int64_t SurfaceFlinger::dispSyncPresentTimeOffset;
bool SurfaceFlinger::useHwcForRgbToYuv;
uint64_t SurfaceFlinger::maxVirtualDisplaySize;
bool SurfaceFlinger::hasSyncFramework;
bool SurfaceFlinger::useVrFlinger;
int64_t SurfaceFlinger::maxFrameBufferAcquiredBuffers;
bool SurfaceFlinger::hasWideColorDisplay;

SurfaceFlinger::SurfaceFlinger()
    :   BnSurfaceComposer(),
        mTransactionFlags(0),
        mTransactionPending(false),
        mAnimTransactionPending(false),
        mLayersRemoved(false),
        mLayersAdded(false),
        mRepaintEverything(0),
        mRenderEngine(nullptr),
        mBootTime(systemTime()),
        mBuiltinDisplays(),
        mVisibleRegionsDirty(false),
        mGeometryInvalid(false),
        mAnimCompositionPending(false),
        mDebugRegion(0),
        mDebugDDMS(0),
        mDebugDisableHWC(0),
        mDebugDisableTransformHint(0),
        mDebugInSwapBuffers(0),
        mLastSwapBufferTime(0),
        mDebugInTransaction(0),
        mLastTransactionTime(0),
        mBootFinished(false),
        mForceFullDamage(false),
        mInterceptor(this),
        mPrimaryDispSync("PrimaryDispSync"),
        mPrimaryHWVsyncEnabled(false),
        mHWVsyncAvailable(false),
        mHasColorMatrix(false),
        mHasPoweredOff(false),
        mFrameBuckets(),
        mTotalTime(0),
        mLastSwapTime(0),
        mNumLayers(0),
        mVrFlingerRequestsDisplay(false),
        mMainThreadId(std::this_thread::get_id()),
        mComposerSequenceId(0)
#ifdef MTK_SF_HW_ROTATION_SUPPORT
        , mCorrectHwRotationSoHandle(nullptr)
        , mCorrectHwRotation(nullptr)
#endif
{
    ALOGI("SurfaceFlinger is starting");

    vsyncPhaseOffsetNs = getInt64< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::vsyncEventPhaseOffsetNs>(1000000);

    sfVsyncPhaseOffsetNs = getInt64< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::vsyncSfEventPhaseOffsetNs>(1000000);

    hasSyncFramework = getBool< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::hasSyncFramework>(true);

    useContextPriority = getBool< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::useContextPriority>(false);

    dispSyncPresentTimeOffset = getInt64< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::presentTimeOffsetFromVSyncNs>(0);

    useHwcForRgbToYuv = getBool< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::useHwcForRGBtoYUV>(false);

    maxVirtualDisplaySize = getUInt64<ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::maxVirtualDisplaySize>(0);

    // Vr flinger is only enabled on Daydream ready devices.
    useVrFlinger = getBool< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::useVrFlinger>(false);

    maxFrameBufferAcquiredBuffers = getInt64< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::maxFrameBufferAcquiredBuffers>(2);

    hasWideColorDisplay =
            getBool<ISurfaceFlingerConfigs, &ISurfaceFlingerConfigs::hasWideColorDisplay>(false);

    mPrimaryDispSync.init(hasSyncFramework, dispSyncPresentTimeOffset);

    // debugging stuff...
    char value[PROPERTY_VALUE_MAX];

    property_get("ro.bq.gpu_to_cpu_unsupported", value, "0");
    mGpuToCpuSupported = !atoi(value);

    property_get("debug.sf.showupdates", value, "0");
    mDebugRegion = atoi(value);

    property_get("debug.sf.ddms", value, "0");
    mDebugDDMS = atoi(value);
    if (mDebugDDMS) {
        if (!startDdmConnection()) {
            // start failed, and DDMS debugging not enabled
            mDebugDDMS = 0;
        }
    }
    ALOGI_IF(mDebugRegion, "showupdates enabled");
    ALOGI_IF(mDebugDDMS, "DDMS debugging enabled");

#ifdef MTK_ADJUST_FD_LIMIT
    adjustFdLimit();
#endif

    property_get("debug.sf.disable_backpressure", value, "0");
    mPropagateBackpressure = !atoi(value);
    ALOGI_IF(!mPropagateBackpressure, "Disabling backpressure propagation");

#ifdef MTK_SF_DEBUG_SUPPORT
    property_get("debug.sf.enable_hwc_vds", value, "1");
#else
    property_get("debug.sf.enable_hwc_vds", value, "0");
#endif
    mUseHwcVirtualDisplays = atoi(value);
    ALOGI_IF(!mUseHwcVirtualDisplays, "Enabling HWC virtual displays");

    property_get("ro.sf.disable_triple_buffer", value, "1");
    mLayerTripleBufferingDisabled = atoi(value);
    ALOGI_IF(mLayerTripleBufferingDisabled, "Disabling Triple Buffering");

    // We should be reading 'persist.sys.sf.color_saturation' here
    // but since /data may be encrypted, we need to wait until after vold
    // comes online to attempt to read the property. The property is
    // instead read after the boot animation
#ifdef MTK_SF_HW_ROTATION_SUPPORT
    typedef CorrectHwRotationAPI *(*createHwRotationPrototype)();
    mCorrectHwRotationSoHandle = dlopen("libcorrect_hw_rotation.so", RTLD_NOW);
    if (mCorrectHwRotationSoHandle) {
        createHwRotationPrototype createHwRotationPtr = reinterpret_cast<createHwRotationPrototype>
            (dlsym(mCorrectHwRotationSoHandle, "createHwRotationInstance"));
        if (createHwRotationPtr == nullptr) {
            ALOGD("Can't load func createHwRotationPtr");
        } else if ((mCorrectHwRotation = createHwRotationPtr()) == nullptr) {
            ALOGD("Can't get CorrectHwRotation");
        }
    } else {
        ALOGD("Can't load libcorrect_hw_rotation");
    }
#endif

#ifdef MTK_SF_DEBUG_SUPPORT
    // init properties setting first
    setMTKProperties();
#endif

#ifdef MTK_SF_WATCHDOG_SUPPORT
    typedef SFWatchDogAPI *(*createSFWDPrototype)();
    mSFWathDogSoHandle = dlopen("libsf_debug.so", RTLD_NOW);
    if (mSFWathDogSoHandle) {
        createSFWDPrototype createSFWDPtr =
            reinterpret_cast<createSFWDPrototype>(dlsym(mSFWathDogSoHandle, "createInstance"));
        if (createSFWDPtr == nullptr) {
            ALOGD("Can't load func mCreateSFWDPtr");
        } else if ((mMessageWDT = createSFWDPtr()) == nullptr) {
            ALOGD("Can't get SFWD");
        }
    } else {
        ALOGD("Can't load libsf_debug");
    }
#endif
}

void SurfaceFlinger::onFirstRef()
{
    mEventQueue.init(this);
}

SurfaceFlinger::~SurfaceFlinger()
{
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglTerminate(display);

#ifdef MTK_SF_HW_ROTATION_SUPPORT
    if (mCorrectHwRotation != nullptr) {
        delete mCorrectHwRotation;
    }
    if (mCorrectHwRotationSoHandle != nullptr) {
        dlclose(mCorrectHwRotationSoHandle);
    }
#endif

#ifdef MTK_SF_WATCHDOG_SUPPORT
    if (mMessageWDT != nullptr) {
        delete mMessageWDT;
    }
    if (mSFWathDogSoHandle != nullptr) {
        dlclose(mSFWathDogSoHandle);
    }
#endif
}

void SurfaceFlinger::binderDied(const wp<IBinder>& /* who */)
{
    // the window manager died on us. prepare its eulogy.

    // restore initial conditions (default device unblank, etc)
    initializeDisplays();

    // restart the boot-animation
    startBootAnim();
}

static sp<ISurfaceComposerClient> initClient(const sp<Client>& client) {
    status_t err = client->initCheck();
    if (err == NO_ERROR) {
        return client;
    }
    return nullptr;
}

sp<ISurfaceComposerClient> SurfaceFlinger::createConnection() {
    return initClient(new Client(this));
}

sp<ISurfaceComposerClient> SurfaceFlinger::createScopedConnection(
        const sp<IGraphicBufferProducer>& gbp) {
    if (authenticateSurfaceTexture(gbp) == false) {
        return nullptr;
    }
    const auto& layer = (static_cast<MonitoredProducer*>(gbp.get()))->getLayer();
    if (layer == nullptr) {
        return nullptr;
    }

   return initClient(new Client(this, layer));
}

sp<IBinder> SurfaceFlinger::createDisplay(const String8& displayName,
        bool secure)
{
    class DisplayToken : public BBinder {
        sp<SurfaceFlinger> flinger;
        virtual ~DisplayToken() {
             // no more references, this display must be terminated
             Mutex::Autolock _l(flinger->mStateLock);
             flinger->mCurrentState.displays.removeItem(this);
             flinger->setTransactionFlags(eDisplayTransactionNeeded);
         }
     public:
        explicit DisplayToken(const sp<SurfaceFlinger>& flinger)
            : flinger(flinger) {
        }
    };

    sp<BBinder> token = new DisplayToken(this);

    Mutex::Autolock _l(mStateLock);
    DisplayDeviceState info(DisplayDevice::DISPLAY_VIRTUAL, secure);
    info.displayName = displayName;
    mCurrentState.displays.add(token, info);
    mInterceptor.saveDisplayCreation(info);
    return token;
}

void SurfaceFlinger::destroyDisplay(const sp<IBinder>& display) {
    Mutex::Autolock _l(mStateLock);

    ssize_t idx = mCurrentState.displays.indexOfKey(display);
    if (idx < 0) {
        ALOGW("destroyDisplay: invalid display token");
        return;
    }

    const DisplayDeviceState& info(mCurrentState.displays.valueAt(idx));
    if (!info.isVirtualDisplay()) {
        ALOGE("destroyDisplay called for non-virtual display");
        return;
    }
    mInterceptor.saveDisplayDeletion(info.displayId);
    mCurrentState.displays.removeItemsAt(idx);
    setTransactionFlags(eDisplayTransactionNeeded);
}

void SurfaceFlinger::createBuiltinDisplayLocked(DisplayDevice::DisplayType type) {
    ALOGV("createBuiltinDisplayLocked(%d)", type);
    ALOGW_IF(mBuiltinDisplays[type],
            "Overwriting display token for display type %d", type);
    mBuiltinDisplays[type] = new BBinder();
    // All non-virtual displays are currently considered secure.
    DisplayDeviceState info(type, true);
    mCurrentState.displays.add(mBuiltinDisplays[type], info);
    mInterceptor.saveDisplayCreation(info);
}

sp<IBinder> SurfaceFlinger::getBuiltInDisplay(int32_t id) {
    if (uint32_t(id) >= DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES) {
        ALOGE("getDefaultDisplay: id=%d is not a valid default display id", id);
        return NULL;
    }
    return mBuiltinDisplays[id];
}

void SurfaceFlinger::bootFinished()
{
    if (mStartPropertySetThread->join() != NO_ERROR) {
        ALOGE("Join StartPropertySetThread failed!");
    }
    const nsecs_t now = systemTime();
    const nsecs_t duration = now - mBootTime;
    ALOGI("Boot is finished (%ld ms)", long(ns2ms(duration)) );

    // wait patiently for the window manager death
    const String16 name("window");
    sp<IBinder> window(defaultServiceManager()->getService(name));
    if (window != 0) {
        window->linkToDeath(static_cast<IBinder::DeathRecipient*>(this));
    }

    if (mVrFlinger) {
      mVrFlinger->OnBootFinished();
    }

    // stop boot animation
    // formerly we would just kill the process, but we now ask it to exit so it
    // can choose where to stop the animation.
    property_set("service.bootanim.exit", "1");

    const int LOGTAG_SF_STOP_BOOTANIM = 60110;
    LOG_EVENT_LONG(LOGTAG_SF_STOP_BOOTANIM,
                   ns2ms(systemTime(SYSTEM_TIME_MONOTONIC)));

    sp<LambdaMessage> readProperties = new LambdaMessage([&]() {
        readPersistentProperties();
    });
    postMessageAsync(readProperties);

#ifdef MTK_SF_DEBUG_SUPPORT
    // boot time profiling
    mStartPropertySetThread->bootProf(0);
#endif
#ifdef MTK_SF_WATCHDOG_SUPPORT
    if (mMessageWDT != nullptr) {
        mMessageWDT->setThreshold(500);
    }
#endif
}

void SurfaceFlinger::deleteTextureAsync(uint32_t texture) {
    class MessageDestroyGLTexture : public MessageBase {
        RenderEngine& engine;
        uint32_t texture;
    public:
        MessageDestroyGLTexture(RenderEngine& engine, uint32_t texture)
            : engine(engine), texture(texture) {
        }
        virtual bool handler() {
            engine.deleteTextures(1, &texture);
            return true;
        }
    };
    postMessageAsync(new MessageDestroyGLTexture(getRenderEngine(), texture));
}

class DispSyncSource : public VSyncSource, private DispSync::Callback {
public:
    DispSyncSource(DispSync* dispSync, nsecs_t phaseOffset, bool traceVsync,
        const char* name) :
            mName(name),
            mValue(0),
            mTraceVsync(traceVsync),
            mVsyncOnLabel(String8::format("VsyncOn-%s", name)),
            mVsyncEventLabel(String8::format("VSYNC-%s", name)),
            mDispSync(dispSync),
            mCallbackMutex(),
            mCallback(),
            mVsyncMutex(),
            mPhaseOffset(phaseOffset),
            mEnabled(false) {}

    virtual ~DispSyncSource() {}

    virtual void setVSyncEnabled(bool enable) {
        Mutex::Autolock lock(mVsyncMutex);
        if (enable) {
            status_t err = mDispSync->addEventListener(mName, mPhaseOffset,
                    static_cast<DispSync::Callback*>(this));
            if (err != NO_ERROR) {
                ALOGE("error registering vsync callback: %s (%d)",
                        strerror(-err), err);
            }
            //ATRACE_INT(mVsyncOnLabel.string(), 1);
        } else {
            status_t err = mDispSync->removeEventListener(
                    static_cast<DispSync::Callback*>(this));
            if (err != NO_ERROR) {
                ALOGE("error unregistering vsync callback: %s (%d)",
                        strerror(-err), err);
            }
            //ATRACE_INT(mVsyncOnLabel.string(), 0);
        }
        mEnabled = enable;
    }

    virtual void setCallback(const sp<VSyncSource::Callback>& callback) {
        Mutex::Autolock lock(mCallbackMutex);
        mCallback = callback;
    }

    virtual void setPhaseOffset(nsecs_t phaseOffset) {
        Mutex::Autolock lock(mVsyncMutex);

        // Normalize phaseOffset to [0, period)
        auto period = mDispSync->getPeriod();
        phaseOffset %= period;
        if (phaseOffset < 0) {
            // If we're here, then phaseOffset is in (-period, 0). After this
            // operation, it will be in (0, period)
            phaseOffset += period;
        }
        mPhaseOffset = phaseOffset;

        // If we're not enabled, we don't need to mess with the listeners
        if (!mEnabled) {
            return;
        }

        // Remove the listener with the old offset
        status_t err = mDispSync->removeEventListener(
                static_cast<DispSync::Callback*>(this));
        if (err != NO_ERROR) {
            ALOGE("error unregistering vsync callback: %s (%d)",
                    strerror(-err), err);
        }

        // Add a listener with the new offset
        err = mDispSync->addEventListener(mName, mPhaseOffset,
                static_cast<DispSync::Callback*>(this));
        if (err != NO_ERROR) {
            ALOGE("error registering vsync callback: %s (%d)",
                    strerror(-err), err);
        }
    }

private:
    virtual void onDispSyncEvent(nsecs_t when) {
        sp<VSyncSource::Callback> callback;
        {
            Mutex::Autolock lock(mCallbackMutex);
            callback = mCallback;

            if (mTraceVsync) {
                mValue = (mValue + 1) % 2;
                ATRACE_INT(mVsyncEventLabel.string(), mValue);
            }
        }

        if (callback != NULL) {
            callback->onVSyncEvent(when);
        }
    }

    const char* const mName;

    int mValue;

    const bool mTraceVsync;
    const String8 mVsyncOnLabel;
    const String8 mVsyncEventLabel;

    DispSync* mDispSync;

    Mutex mCallbackMutex; // Protects the following
    sp<VSyncSource::Callback> mCallback;

    Mutex mVsyncMutex; // Protects the following
    nsecs_t mPhaseOffset;
    bool mEnabled;
};

class InjectVSyncSource : public VSyncSource {
public:
    InjectVSyncSource() {}

    virtual ~InjectVSyncSource() {}

    virtual void setCallback(const sp<VSyncSource::Callback>& callback) {
        std::lock_guard<std::mutex> lock(mCallbackMutex);
        mCallback = callback;
    }

    virtual void onInjectSyncEvent(nsecs_t when) {
        std::lock_guard<std::mutex> lock(mCallbackMutex);
        if (mCallback != nullptr) {
            mCallback->onVSyncEvent(when);
        }
    }

    virtual void setVSyncEnabled(bool) {}
    virtual void setPhaseOffset(nsecs_t) {}

private:
    std::mutex mCallbackMutex; // Protects the following
    sp<VSyncSource::Callback> mCallback;
};

// Do not call property_set on main thread which will be blocked by init
// Use StartPropertySetThread instead.
void SurfaceFlinger::init() {
    ALOGI(  "SurfaceFlinger's main thread ready to run. "
            "Initializing graphics H/W...");

    ALOGI("Phase offest NS: %" PRId64 "", vsyncPhaseOffsetNs);
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    mPrimaryDispSync.setSurfaceFlinger(this);
#endif

    Mutex::Autolock _l(mStateLock);

    // initialize EGL for the default display
    mEGLDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(mEGLDisplay, NULL, NULL);

    // start the EventThread
    sp<VSyncSource> vsyncSrc = new DispSyncSource(&mPrimaryDispSync,
            vsyncPhaseOffsetNs, true, "app");
    mEventThread = new EventThread(vsyncSrc, *this, false);
    sp<VSyncSource> sfVsyncSrc = new DispSyncSource(&mPrimaryDispSync,
            sfVsyncPhaseOffsetNs, true, "sf");
    mSFEventThread = new EventThread(sfVsyncSrc, *this, true);
    mEventQueue.setEventThread(mSFEventThread);

    // set EventThread and SFEventThread to SCHED_FIFO to minimize jitter
    struct sched_param param = {0};
    param.sched_priority = 2;
    if (sched_setscheduler(mSFEventThread->getTid(), SCHED_FIFO, &param) != 0) {
        ALOGE("Couldn't set SCHED_FIFO for SFEventThread");
    }
    if (sched_setscheduler(mEventThread->getTid(), SCHED_FIFO, &param) != 0) {
        ALOGE("Couldn't set SCHED_FIFO for EventThread");
    }

    // Get a RenderEngine for the given display / config (can't fail)
    mRenderEngine = RenderEngine::create(mEGLDisplay,
            HAL_PIXEL_FORMAT_RGBA_8888,
            hasWideColorDisplay ? RenderEngine::WIDE_COLOR_SUPPORT : 0);

    // retrieve the EGL context that was selected/created
    mEGLContext = mRenderEngine->getEGLContext();

#ifdef MTK_SF_DEBUG_SUPPORT
    // make sure 3D init success
    if (mEGLContext == EGL_NO_CONTEXT)
    {
        ALOGE("FATAL: couldn't create EGLContext");
        eglTerminate(mEGLDisplay);
        exit(0);
    }

    // init properties setting first
    setMTKProperties();
#else
    LOG_ALWAYS_FATAL_IF(mEGLContext == EGL_NO_CONTEXT,
            "couldn't create EGLContext");
#endif

    LOG_ALWAYS_FATAL_IF(mVrFlingerRequestsDisplay,
            "Starting with vr flinger active is not currently supported.");
    mHwc.reset(new HWComposer(false));
    mHwc->registerCallback(this, mComposerSequenceId);

    if (useVrFlinger) {
        auto vrFlingerRequestDisplayCallback = [this] (bool requestDisplay) {
            // This callback is called from the vr flinger dispatch thread. We
            // need to call signalTransaction(), which requires holding
            // mStateLock when we're not on the main thread. Acquiring
            // mStateLock from the vr flinger dispatch thread might trigger a
            // deadlock in surface flinger (see b/66916578), so post a message
            // to be handled on the main thread instead.
            sp<LambdaMessage> message = new LambdaMessage([=]() {
                ALOGI("VR request display mode: requestDisplay=%d", requestDisplay);
                mVrFlingerRequestsDisplay = requestDisplay;
                signalTransaction();
            });
            postMessageAsync(message);
        };
        mVrFlinger = dvr::VrFlinger::Create(mHwc->getComposer(),
                                            vrFlingerRequestDisplayCallback);
        if (!mVrFlinger) {
            ALOGE("Failed to start vrflinger");
        }
    }

    mEventControlThread = new EventControlThread(this);
    mEventControlThread->run("EventControl", PRIORITY_URGENT_DISPLAY);

    // initialize our drawing state
    mDrawingState = mCurrentState;

    // set initial conditions (e.g. unblank default device)
    initializeDisplays();

    mRenderEngine->primeCache();

    // Inform native graphics APIs whether the present timestamp is supported:
    if (getHwComposer().hasCapability(
            HWC2::Capability::PresentFenceIsNotReliable)) {
        mStartPropertySetThread = new StartPropertySetThread(false);
    } else {
        mStartPropertySetThread = new StartPropertySetThread(true);
    }

    if (mStartPropertySetThread->Start() != NO_ERROR) {
        ALOGE("Run StartPropertySetThread failed!");
    }

    ALOGV("Done initializing");
}

void SurfaceFlinger::readPersistentProperties() {
    char value[PROPERTY_VALUE_MAX];

    property_get("persist.sys.sf.color_saturation", value, "1.0");
    mSaturation = atof(value);
    ALOGV("Saturation is set to %.2f", mSaturation);

    property_get("persist.sys.sf.native_mode", value, "0");
    mForceNativeColorMode = atoi(value) == 1;
    if (mForceNativeColorMode) {
        ALOGV("Forcing native color mode");
    }
}

void SurfaceFlinger::startBootAnim() {
    // Start boot animation service by setting a property mailbox
    // if property setting thread is already running, Start() will be just a NOP
    mStartPropertySetThread->Start();
    // Wait until property was set
    if (mStartPropertySetThread->join() != NO_ERROR) {
        ALOGE("Join StartPropertySetThread failed!");
    }
}

size_t SurfaceFlinger::getMaxTextureSize() const {
    return mRenderEngine->getMaxTextureSize();
}

size_t SurfaceFlinger::getMaxViewportDims() const {
    return mRenderEngine->getMaxViewportDims();
}

// ----------------------------------------------------------------------------

bool SurfaceFlinger::authenticateSurfaceTexture(
        const sp<IGraphicBufferProducer>& bufferProducer) const {
    Mutex::Autolock _l(mStateLock);
    return authenticateSurfaceTextureLocked(bufferProducer);
}

bool SurfaceFlinger::authenticateSurfaceTextureLocked(
        const sp<IGraphicBufferProducer>& bufferProducer) const {
    sp<IBinder> surfaceTextureBinder(IInterface::asBinder(bufferProducer));
    return mGraphicBufferProducerList.indexOf(surfaceTextureBinder) >= 0;
}

status_t SurfaceFlinger::getSupportedFrameTimestamps(
        std::vector<FrameEvent>* outSupported) const {
    *outSupported = {
        FrameEvent::REQUESTED_PRESENT,
        FrameEvent::ACQUIRE,
        FrameEvent::LATCH,
        FrameEvent::FIRST_REFRESH_START,
        FrameEvent::LAST_REFRESH_START,
        FrameEvent::GPU_COMPOSITION_DONE,
        FrameEvent::DEQUEUE_READY,
        FrameEvent::RELEASE,
    };
    ConditionalLock _l(mStateLock,
            std::this_thread::get_id() != mMainThreadId);
    if (!getHwComposer().hasCapability(
            HWC2::Capability::PresentFenceIsNotReliable)) {
        outSupported->push_back(FrameEvent::DISPLAY_PRESENT);
    }
    return NO_ERROR;
}

status_t SurfaceFlinger::getDisplayConfigs(const sp<IBinder>& display,
        Vector<DisplayInfo>* configs) {
    if ((configs == NULL) || (display.get() == NULL)) {
        return BAD_VALUE;
    }

    if (!display.get())
        return NAME_NOT_FOUND;

    int32_t type = NAME_NOT_FOUND;
    for (int i=0 ; i<DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES ; i++) {
        if (display == mBuiltinDisplays[i]) {
            type = i;
            break;
        }
    }

    if (type < 0) {
        return type;
    }

    // TODO: Not sure if display density should handled by SF any longer
    class Density {
        static int getDensityFromProperty(char const* propName) {
            char property[PROPERTY_VALUE_MAX];
            int density = 0;
            if (property_get(propName, property, NULL) > 0) {
                density = atoi(property);
            }
            return density;
        }
    public:
        static int getEmuDensity() {
            return getDensityFromProperty("qemu.sf.lcd_density"); }
        static int getBuildDensity()  {
            return getDensityFromProperty("ro.sf.lcd_density"); }
    };

    configs->clear();

    ConditionalLock _l(mStateLock,
            std::this_thread::get_id() != mMainThreadId);
    for (const auto& hwConfig : getHwComposer().getConfigs(type)) {
        DisplayInfo info = DisplayInfo();

        float xdpi = hwConfig->getDpiX();
        float ydpi = hwConfig->getDpiY();

        if (type == DisplayDevice::DISPLAY_PRIMARY) {
            // The density of the device is provided by a build property
            float density = Density::getBuildDensity() / 160.0f;
            if (density == 0) {
                // the build doesn't provide a density -- this is wrong!
                // use xdpi instead
                ALOGE("ro.sf.lcd_density must be defined as a build property");
                density = xdpi / 160.0f;
            }
            if (Density::getEmuDensity()) {
                // if "qemu.sf.lcd_density" is specified, it overrides everything
                xdpi = ydpi = density = Density::getEmuDensity();
                density /= 160.0f;
            }
            info.density = density;

            // TODO: this needs to go away (currently needed only by webkit)
            sp<const DisplayDevice> hw(getDefaultDisplayDeviceLocked());
            info.orientation = hw->getOrientation();
        } else {
            // TODO: where should this value come from?
            static const int TV_DENSITY = 213;
            info.density = TV_DENSITY / 160.0f;
            info.orientation = 0;
        }

        info.w = hwConfig->getWidth();
        info.h = hwConfig->getHeight();
        info.xdpi = xdpi;
        info.ydpi = ydpi;
        info.fps = 1e9 / hwConfig->getVsyncPeriod();
        info.appVsyncOffset = vsyncPhaseOffsetNs;

        // This is how far in advance a buffer must be queued for
        // presentation at a given time.  If you want a buffer to appear
        // on the screen at time N, you must submit the buffer before
        // (N - presentationDeadline).
        //
        // Normally it's one full refresh period (to give SF a chance to
        // latch the buffer), but this can be reduced by configuring a
        // DispSync offset.  Any additional delays introduced by the hardware
        // composer or panel must be accounted for here.
        //
        // We add an additional 1ms to allow for processing time and
        // differences between the ideal and actual refresh rate.
        info.presentationDeadline = hwConfig->getVsyncPeriod() -
                sfVsyncPhaseOffsetNs + 1000000;

        // All non-virtual displays are currently considered secure.
        info.secure = true;

#ifdef MTK_SF_HW_ROTATION_SUPPORT
        if (mCorrectHwRotation != nullptr &&
            DisplayDevice::DISPLAY_PRIMARY == type) {
            // correct for primary display to normalize graphic plane
            mCorrectHwRotation->correctSize(&info.w, &info.h);
        }
#endif

        configs->push_back(info);
    }

    return NO_ERROR;
}

status_t SurfaceFlinger::getDisplayStats(const sp<IBinder>& /* display */,
        DisplayStatInfo* stats) {
    if (stats == NULL) {
        return BAD_VALUE;
    }

    // FIXME for now we always return stats for the primary display
    memset(stats, 0, sizeof(*stats));
    stats->vsyncTime   = mPrimaryDispSync.computeNextRefresh(0);
    stats->vsyncPeriod = mPrimaryDispSync.getPeriod();
    return NO_ERROR;
}

int SurfaceFlinger::getActiveConfig(const sp<IBinder>& display) {
    if (display == NULL) {
        ALOGE("%s : display is NULL", __func__);
        return BAD_VALUE;
    }

    sp<const DisplayDevice> device(getDisplayDevice(display));
    if (device != NULL) {
        return device->getActiveConfig();
    }

    return BAD_VALUE;
}

void SurfaceFlinger::setActiveConfigInternal(const sp<DisplayDevice>& hw, int mode) {
    ALOGD("Set active config mode=%d, type=%d flinger=%p", mode, hw->getDisplayType(),
          this);
    int32_t type = hw->getDisplayType();
    int currentMode = hw->getActiveConfig();

    if (mode == currentMode) {
        ALOGD("Screen type=%d is already mode=%d", hw->getDisplayType(), mode);
        return;
    }

    if (type >= DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES) {
        ALOGW("Trying to set config for virtual display");
        return;
    }

    hw->setActiveConfig(mode);
    getHwComposer().setActiveConfig(type, mode);
}

status_t SurfaceFlinger::setActiveConfig(const sp<IBinder>& display, int mode) {
    class MessageSetActiveConfig: public MessageBase {
        SurfaceFlinger& mFlinger;
        sp<IBinder> mDisplay;
        int mMode;
    public:
        MessageSetActiveConfig(SurfaceFlinger& flinger, const sp<IBinder>& disp,
                               int mode) :
            mFlinger(flinger), mDisplay(disp) { mMode = mode; }
        virtual bool handler() {
            Vector<DisplayInfo> configs;
            mFlinger.getDisplayConfigs(mDisplay, &configs);
            if (mMode < 0 || mMode >= static_cast<int>(configs.size())) {
                ALOGE("Attempt to set active config = %d for display with %zu configs",
                        mMode, configs.size());
                return true;
            }
            sp<DisplayDevice> hw(mFlinger.getDisplayDevice(mDisplay));
            if (hw == NULL) {
                ALOGE("Attempt to set active config = %d for null display %p",
                        mMode, mDisplay.get());
            } else if (hw->getDisplayType() >= DisplayDevice::DISPLAY_VIRTUAL) {
                ALOGW("Attempt to set active config = %d for virtual display",
                        mMode);
            } else {
                mFlinger.setActiveConfigInternal(hw, mMode);
            }
            return true;
        }
    };
    sp<MessageBase> msg = new MessageSetActiveConfig(*this, display, mode);
    postMessageSync(msg);
    return NO_ERROR;
}
status_t SurfaceFlinger::getDisplayColorModes(const sp<IBinder>& display,
        Vector<android_color_mode_t>* outColorModes) {
    if ((outColorModes == nullptr) || (display.get() == nullptr)) {
        return BAD_VALUE;
    }

    if (!display.get()) {
        return NAME_NOT_FOUND;
    }

    int32_t type = NAME_NOT_FOUND;
    for (int i=0 ; i<DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES ; i++) {
        if (display == mBuiltinDisplays[i]) {
            type = i;
            break;
        }
    }

    if (type < 0) {
        return type;
    }

    std::vector<android_color_mode_t> modes;
    {
        ConditionalLock _l(mStateLock,
                std::this_thread::get_id() != mMainThreadId);
        modes = getHwComposer().getColorModes(type);
    }
    outColorModes->clear();
    std::copy(modes.cbegin(), modes.cend(), std::back_inserter(*outColorModes));

    return NO_ERROR;
}

android_color_mode_t SurfaceFlinger::getActiveColorMode(const sp<IBinder>& display) {
    sp<const DisplayDevice> device(getDisplayDevice(display));
    if (device != nullptr) {
        return device->getActiveColorMode();
    }
    return static_cast<android_color_mode_t>(BAD_VALUE);
}

void SurfaceFlinger::setActiveColorModeInternal(const sp<DisplayDevice>& hw,
        android_color_mode_t mode) {
    int32_t type = hw->getDisplayType();
    android_color_mode_t currentMode = hw->getActiveColorMode();

    if (mode == currentMode) {
        return;
    }

    if (type >= DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES) {
        ALOGW("Trying to set config for virtual display");
        return;
    }

    ALOGD("Set active color mode: %s (%d), type=%d", decodeColorMode(mode).c_str(), mode,
          hw->getDisplayType());

    hw->setActiveColorMode(mode);
    getHwComposer().setActiveColorMode(type, mode);
}


status_t SurfaceFlinger::setActiveColorMode(const sp<IBinder>& display,
        android_color_mode_t colorMode) {
    class MessageSetActiveColorMode: public MessageBase {
        SurfaceFlinger& mFlinger;
        sp<IBinder> mDisplay;
        android_color_mode_t mMode;
    public:
        MessageSetActiveColorMode(SurfaceFlinger& flinger, const sp<IBinder>& disp,
                               android_color_mode_t mode) :
            mFlinger(flinger), mDisplay(disp) { mMode = mode; }
        virtual bool handler() {
            Vector<android_color_mode_t> modes;
            mFlinger.getDisplayColorModes(mDisplay, &modes);
            bool exists = std::find(std::begin(modes), std::end(modes), mMode) != std::end(modes);
            if (mMode < 0 || !exists) {
                ALOGE("Attempt to set invalid active color mode %s (%d) for display %p",
                      decodeColorMode(mMode).c_str(), mMode, mDisplay.get());
                return true;
            }
            sp<DisplayDevice> hw(mFlinger.getDisplayDevice(mDisplay));
            if (hw == nullptr) {
                ALOGE("Attempt to set active color mode %s (%d) for null display %p",
                      decodeColorMode(mMode).c_str(), mMode, mDisplay.get());
            } else if (hw->getDisplayType() >= DisplayDevice::DISPLAY_VIRTUAL) {
                ALOGW("Attempt to set active color mode %s %d for virtual display",
                      decodeColorMode(mMode).c_str(), mMode);
            } else {
                mFlinger.setActiveColorModeInternal(hw, mMode);
            }
            return true;
        }
    };
    sp<MessageBase> msg = new MessageSetActiveColorMode(*this, display, colorMode);
    postMessageSync(msg);
    return NO_ERROR;
}

status_t SurfaceFlinger::clearAnimationFrameStats() {
    Mutex::Autolock _l(mStateLock);
    mAnimFrameTracker.clearStats();
    return NO_ERROR;
}

status_t SurfaceFlinger::getAnimationFrameStats(FrameStats* outStats) const {
    Mutex::Autolock _l(mStateLock);
    mAnimFrameTracker.getStats(outStats);
    return NO_ERROR;
}

status_t SurfaceFlinger::getHdrCapabilities(const sp<IBinder>& display,
        HdrCapabilities* outCapabilities) const {
    Mutex::Autolock _l(mStateLock);

    sp<const DisplayDevice> displayDevice(getDisplayDeviceLocked(display));
    if (displayDevice == nullptr) {
        ALOGE("getHdrCapabilities: Invalid display %p", displayDevice.get());
        return BAD_VALUE;
    }

    std::unique_ptr<HdrCapabilities> capabilities =
            mHwc->getHdrCapabilities(displayDevice->getHwcDisplayId());
    if (capabilities) {
        std::swap(*outCapabilities, *capabilities);
    } else {
        return BAD_VALUE;
    }

    return NO_ERROR;
}

void SurfaceFlinger::enableVSyncInjectionsInternal(bool enable) {
    Mutex::Autolock _l(mStateLock);

    if (mInjectVSyncs == enable) {
        return;
    }

    if (enable) {
        ALOGV("VSync Injections enabled");
        if (mVSyncInjector.get() == nullptr) {
            mVSyncInjector = new InjectVSyncSource();
            mInjectorEventThread = new EventThread(mVSyncInjector, *this, false);
        }
        mEventQueue.setEventThread(mInjectorEventThread);
    } else {
        ALOGV("VSync Injections disabled");
        mEventQueue.setEventThread(mSFEventThread);
    }

    mInjectVSyncs = enable;
}

status_t SurfaceFlinger::enableVSyncInjections(bool enable) {
    class MessageEnableVSyncInjections : public MessageBase {
        SurfaceFlinger* mFlinger;
        bool mEnable;
    public:
        MessageEnableVSyncInjections(SurfaceFlinger* flinger, bool enable)
            : mFlinger(flinger), mEnable(enable) { }
        virtual bool handler() {
            mFlinger->enableVSyncInjectionsInternal(mEnable);
            return true;
        }
    };
    sp<MessageBase> msg = new MessageEnableVSyncInjections(this, enable);
    postMessageSync(msg);
    return NO_ERROR;
}

status_t SurfaceFlinger::injectVSync(nsecs_t when) {
    Mutex::Autolock _l(mStateLock);

    if (!mInjectVSyncs) {
        ALOGE("VSync Injections not enabled");
        return BAD_VALUE;
    }
    if (mInjectVSyncs && mInjectorEventThread.get() != nullptr) {
        ALOGV("Injecting VSync inside SurfaceFlinger");
        mVSyncInjector->onInjectSyncEvent(when);
    }
    return NO_ERROR;
}

// ----------------------------------------------------------------------------

sp<IDisplayEventConnection> SurfaceFlinger::createDisplayEventConnection(
        ISurfaceComposer::VsyncSource vsyncSource) {
    if (vsyncSource == eVsyncSourceSurfaceFlinger) {
        return mSFEventThread->createEventConnection();
    } else {
        return mEventThread->createEventConnection();
    }
}

// ----------------------------------------------------------------------------

void SurfaceFlinger::waitForEvent() {
    mEventQueue.waitMessage();
}

void SurfaceFlinger::signalTransaction() {
    mEventQueue.invalidate();
}

void SurfaceFlinger::signalLayerUpdate() {
    mEventQueue.invalidate();
}

void SurfaceFlinger::signalRefresh() {
    mRefreshPending = true;
    mEventQueue.refresh();
}

status_t SurfaceFlinger::postMessageAsync(const sp<MessageBase>& msg,
        nsecs_t reltime, uint32_t /* flags */) {
    return mEventQueue.postMessage(msg, reltime);
}

status_t SurfaceFlinger::postMessageSync(const sp<MessageBase>& msg,
        nsecs_t reltime, uint32_t /* flags */) {
    status_t res = mEventQueue.postMessage(msg, reltime);
    if (res == NO_ERROR) {
        msg->wait();
    }
    return res;
}

void SurfaceFlinger::run() {
    do {
        waitForEvent();
    } while (true);
}

void SurfaceFlinger::enableHardwareVsync() {
    Mutex::Autolock _l(mHWVsyncLock);
    if (!mPrimaryHWVsyncEnabled && mHWVsyncAvailable) {
        mPrimaryDispSync.beginResync();
        //eventControl(HWC_DISPLAY_PRIMARY, SurfaceFlinger::EVENT_VSYNC, true);
        mEventControlThread->setVsyncEnabled(true);
        mPrimaryHWVsyncEnabled = true;
    }
}

void SurfaceFlinger::resyncToHardwareVsync(bool makeAvailable) {
    Mutex::Autolock _l(mHWVsyncLock);

    if (makeAvailable) {
        mHWVsyncAvailable = true;
    } else if (!mHWVsyncAvailable) {
        // Hardware vsync is not currently available, so abort the resync
        // attempt for now
        return;
    }

#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    if (mPrimaryDispSync.obeyResync()) {
#endif
    const auto& activeConfig = mHwc->getActiveConfig(HWC_DISPLAY_PRIMARY);
    const nsecs_t period = activeConfig->getVsyncPeriod();

    mPrimaryDispSync.reset();
    mPrimaryDispSync.setPeriod(period);
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    }
#endif

    if (!mPrimaryHWVsyncEnabled) {
        mPrimaryDispSync.beginResync();
        //eventControl(HWC_DISPLAY_PRIMARY, SurfaceFlinger::EVENT_VSYNC, true);
        mEventControlThread->setVsyncEnabled(true);
        mPrimaryHWVsyncEnabled = true;
    }
}

void SurfaceFlinger::disableHardwareVsync(bool makeUnavailable) {
    Mutex::Autolock _l(mHWVsyncLock);
    if (mPrimaryHWVsyncEnabled) {
        //eventControl(HWC_DISPLAY_PRIMARY, SurfaceFlinger::EVENT_VSYNC, false);
        mEventControlThread->setVsyncEnabled(false);
        mPrimaryDispSync.endResync();
        mPrimaryHWVsyncEnabled = false;
    }
    if (makeUnavailable) {
        mHWVsyncAvailable = false;
    }
}

void SurfaceFlinger::resyncWithRateLimit() {
    static constexpr nsecs_t kIgnoreDelay = ms2ns(500);

    // No explicit locking is needed here since EventThread holds a lock while calling this method
    static nsecs_t sLastResyncAttempted = 0;
    const nsecs_t now = systemTime();
    if (now - sLastResyncAttempted > kIgnoreDelay) {
        resyncToHardwareVsync(false);
    }
    sLastResyncAttempted = now;
}

void SurfaceFlinger::onVsyncReceived(int32_t sequenceId,
        hwc2_display_t displayId, int64_t timestamp) {
    Mutex::Autolock lock(mStateLock);
    // Ignore any vsyncs from a previous hardware composer.
    if (sequenceId != mComposerSequenceId) {
        return;
    }

    int32_t type;
    if (!mHwc->onVsync(displayId, timestamp, &type)) {
        return;
    }

    bool needsHwVsync = false;

    { // Scope for the lock
        Mutex::Autolock _l(mHWVsyncLock);
        if (type == DisplayDevice::DISPLAY_PRIMARY && mPrimaryHWVsyncEnabled) {
            needsHwVsync = mPrimaryDispSync.addResyncSample(timestamp);
        }
    }

    if (needsHwVsync) {
        enableHardwareVsync();
    } else {
        disableHardwareVsync(false);
    }
}

void SurfaceFlinger::getCompositorTiming(CompositorTiming* compositorTiming) {
    std::lock_guard<std::mutex> lock(mCompositorTimingLock);
    *compositorTiming = mCompositorTiming;
}

void SurfaceFlinger::createDefaultDisplayDevice() {
    const DisplayDevice::DisplayType type = DisplayDevice::DISPLAY_PRIMARY;
    wp<IBinder> token = mBuiltinDisplays[type];

    // All non-virtual displays are currently considered secure.
    const bool isSecure = true;

    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);

    sp<FramebufferSurface> fbs = new FramebufferSurface(*mHwc, type, consumer);

    bool hasWideColorModes = false;
    std::vector<android_color_mode_t> modes = getHwComposer().getColorModes(type);
    for (android_color_mode_t colorMode : modes) {
        switch (colorMode) {
            case HAL_COLOR_MODE_DISPLAY_P3:
            case HAL_COLOR_MODE_ADOBE_RGB:
            case HAL_COLOR_MODE_DCI_P3:
                hasWideColorModes = true;
                break;
            default:
                break;
        }
    }
    bool useWideColorMode = hasWideColorModes && hasWideColorDisplay && !mForceNativeColorMode;
    sp<DisplayDevice> hw = new DisplayDevice(this, DisplayDevice::DISPLAY_PRIMARY, type, isSecure,
                                             token, fbs, producer, mRenderEngine->getEGLConfig(),
                                             useWideColorMode);
    mDisplays.add(token, hw);
    android_color_mode defaultColorMode = HAL_COLOR_MODE_NATIVE;
    if (useWideColorMode) {
        defaultColorMode = HAL_COLOR_MODE_SRGB;
    }
    setActiveColorModeInternal(hw, defaultColorMode);
    hw->setCompositionDataSpace(HAL_DATASPACE_UNKNOWN);

    // Add the primary display token to mDrawingState so we don't try to
    // recreate the DisplayDevice for the primary display.
    mDrawingState.displays.add(token, DisplayDeviceState(type, true));

    // make the GLContext current so that we can create textures when creating
    // Layers (which may happens before we render something)
    hw->makeCurrent(mEGLDisplay, mEGLContext);
}

void SurfaceFlinger::onHotplugReceived(int32_t sequenceId,
        hwc2_display_t display, HWC2::Connection connection,
        bool primaryDisplay) {
    ALOGV("onHotplugReceived(%d, %" PRIu64 ", %s, %s)",
          sequenceId, display,
          connection == HWC2::Connection::Connected ?
                  "connected" : "disconnected",
          primaryDisplay ? "primary" : "external");

    // Only lock if we're not on the main thread. This function is normally
    // called on a hwbinder thread, but for the primary display it's called on
    // the main thread with the state lock already held, so don't attempt to
    // acquire it here.
    ConditionalLock lock(mStateLock,
            std::this_thread::get_id() != mMainThreadId);

    if (primaryDisplay) {
        mHwc->onHotplug(display, connection);
        if (!mBuiltinDisplays[DisplayDevice::DISPLAY_PRIMARY].get()) {
            createBuiltinDisplayLocked(DisplayDevice::DISPLAY_PRIMARY);
        }
        createDefaultDisplayDevice();
    } else {
        if (sequenceId != mComposerSequenceId) {
            return;
        }
        if (mHwc->isUsingVrComposer()) {
            ALOGE("External displays are not supported by the vr hardware composer.");
            return;
        }
        mHwc->onHotplug(display, connection);
        auto type = DisplayDevice::DISPLAY_EXTERNAL;
        if (connection == HWC2::Connection::Connected) {
            createBuiltinDisplayLocked(type);
        } else {
            mCurrentState.displays.removeItem(mBuiltinDisplays[type]);
            mBuiltinDisplays[type].clear();
        }
        setTransactionFlags(eDisplayTransactionNeeded);

        // Defer EventThread notification until SF has updated mDisplays.
    }
}

void SurfaceFlinger::onRefreshReceived(int sequenceId,
                                       hwc2_display_t /*display*/) {
    Mutex::Autolock lock(mStateLock);
    if (sequenceId != mComposerSequenceId) {
        return;
    }
    repaintEverythingLocked();
}

void SurfaceFlinger::setVsyncEnabled(int disp, int enabled) {
    ATRACE_CALL();
    Mutex::Autolock lock(mStateLock);
    getHwComposer().setVsyncEnabled(disp,
            enabled ? HWC2::Vsync::Enable : HWC2::Vsync::Disable);
}

// Note: it is assumed the caller holds |mStateLock| when this is called
void SurfaceFlinger::resetDisplayState() {
    disableHardwareVsync(true);
    // Clear the drawing state so that the logic inside of
    // handleTransactionLocked will fire. It will determine the delta between
    // mCurrentState and mDrawingState and re-apply all changes when we make the
    // transition.
    mDrawingState.displays.clear();
    eglMakeCurrent(mEGLDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    mDisplays.clear();
}

void SurfaceFlinger::updateVrFlinger() {
    if (!mVrFlinger)
        return;
    bool vrFlingerRequestsDisplay = mVrFlingerRequestsDisplay;
    if (vrFlingerRequestsDisplay == mHwc->isUsingVrComposer()) {
        return;
    }

    if (vrFlingerRequestsDisplay && !mHwc->getComposer()->isRemote()) {
        ALOGE("Vr flinger is only supported for remote hardware composer"
              " service connections. Ignoring request to transition to vr"
              " flinger.");
        mVrFlingerRequestsDisplay = false;
        return;
    }

    Mutex::Autolock _l(mStateLock);

    int currentDisplayPowerMode = getDisplayDeviceLocked(
            mBuiltinDisplays[DisplayDevice::DISPLAY_PRIMARY])->getPowerMode();

    if (!vrFlingerRequestsDisplay) {
        mVrFlinger->SeizeDisplayOwnership();
    }

    resetDisplayState();
    mHwc.reset();  // Delete the current instance before creating the new one
    mHwc.reset(new HWComposer(vrFlingerRequestsDisplay));
    mHwc->registerCallback(this, ++mComposerSequenceId);

    LOG_ALWAYS_FATAL_IF(!mHwc->getComposer()->isRemote(),
            "Switched to non-remote hardware composer");

    if (vrFlingerRequestsDisplay) {
        mVrFlinger->GrantDisplayOwnership();
    } else {
        enableHardwareVsync();
    }

    mVisibleRegionsDirty = true;
    invalidateHwcGeometry();

    // Re-enable default display.
    sp<DisplayDevice> hw(getDisplayDeviceLocked(
            mBuiltinDisplays[DisplayDevice::DISPLAY_PRIMARY]));
    setPowerModeInternal(hw, currentDisplayPowerMode, /*stateLockHeld*/ true);

    // Reset the timing values to account for the period of the swapped in HWC
    const auto& activeConfig = mHwc->getActiveConfig(HWC_DISPLAY_PRIMARY);
    const nsecs_t period = activeConfig->getVsyncPeriod();
    mAnimFrameTracker.setDisplayRefreshPeriod(period);

    // Use phase of 0 since phase is not known.
    // Use latency of 0, which will snap to the ideal latency.
    setCompositorTimingSnapped(0, period, 0);

    android_atomic_or(1, &mRepaintEverything);
    setTransactionFlags(eDisplayTransactionNeeded);
}

void SurfaceFlinger::onMessageReceived(int32_t what) {
    ATRACE_CALL();
#ifdef MTK_SF_WATCHDOG_SUPPORT
    if (mMessageWDT != nullptr) {
        // start watchdog
        mMessageWDT->setAnchor(String8::format("[%s] what=%d", __func__, what));
    }
#endif
    switch (what) {
        case MessageQueue::INVALIDATE: {
            bool frameMissed = !mHadClientComposition &&
                    mPreviousPresentFence != Fence::NO_FENCE &&
                    (mPreviousPresentFence->getSignalTime() ==
                            Fence::SIGNAL_TIME_PENDING);
            ATRACE_INT("FrameMissed", static_cast<int>(frameMissed));
            if (mPropagateBackpressure && frameMissed) {
                signalLayerUpdate();
                break;
            }

            // Now that we're going to make it to the handleMessageTransaction()
            // call below it's safe to call updateVrFlinger(), which will
            // potentially trigger a display handoff.
            updateVrFlinger();

            bool refreshNeeded = handleMessageTransaction();
            refreshNeeded |= handleMessageInvalidate();
            refreshNeeded |= mRepaintEverything;
            if (refreshNeeded) {
                // Signal a refresh if a transaction modified the window state,
                // a new buffer was latched, or if HWC has requested a full
                // repaint
                signalRefresh();
            }
            break;
        }
        case MessageQueue::REFRESH: {
            handleMessageRefresh();
            break;
        }
    }
#ifdef MTK_SF_WATCHDOG_SUPPORT
    if (mMessageWDT != nullptr) {
        mMessageWDT->delAnchor();
    }
#endif
}

bool SurfaceFlinger::handleMessageTransaction() {
    uint32_t transactionFlags = peekTransactionFlags();
    if (transactionFlags) {
        handleTransaction(transactionFlags);
        return true;
    }
    return false;
}

bool SurfaceFlinger::handleMessageInvalidate() {
    ATRACE_CALL();
#ifdef MTK_SF_WATCHDOG_SUPPORT
    // This mutex may be held by dump function, so stop monitor temporarily.
    if (mMessageWDT != nullptr) {
        mMessageWDT->delAnchor();
    }
#endif
#ifdef MTK_SF_DEBUG_SUPPORT
    Mutex::Autolock _l(mDumpLock);
#endif
#ifdef MTK_SF_WATCHDOG_SUPPORT
    if (mMessageWDT != nullptr) {
        mMessageWDT->setAnchor(String8::format("reset anchor by [%s]", __func__));
    }
#endif
    return handlePageFlip();
}

void SurfaceFlinger::handleMessageRefresh() {
    ATRACE_CALL();
    mRefreshPending = false;

#ifdef MTK_SF_WATCHDOG_SUPPORT
    // This mutex may be held by dump function, so stop monitor temporarily.
    if (mMessageWDT != nullptr) {
        mMessageWDT->delAnchor();
    }
#endif
#ifdef MTK_SF_DEBUG_SUPPORT
    Mutex::Autolock _l(mDumpLock);
#endif
#ifdef MTK_SF_WATCHDOG_SUPPORT
    if (mMessageWDT != nullptr) {
        mMessageWDT->setAnchor(String8::format("reset anchor by [%s]", __func__));
    }
#endif
    nsecs_t refreshStartTime = systemTime(SYSTEM_TIME_MONOTONIC);

    preComposition(refreshStartTime);
    rebuildLayerStacks();
    setUpHWComposer();
    doDebugFlashRegions();
    doComposition();
    postComposition(refreshStartTime);

    mPreviousPresentFence = mHwc->getPresentFence(HWC_DISPLAY_PRIMARY);

    mHadClientComposition = false;
    for (size_t displayId = 0; displayId < mDisplays.size(); ++displayId) {
        const sp<DisplayDevice>& displayDevice = mDisplays[displayId];
        mHadClientComposition = mHadClientComposition ||
                mHwc->hasClientComposition(displayDevice->getHwcDisplayId());
    }

    mLayersWithQueuedFrames.clear();
}

void SurfaceFlinger::doDebugFlashRegions()
{
    // is debugging enabled
    if (CC_LIKELY(!mDebugRegion))
        return;

    const bool repaintEverything = mRepaintEverything;
    for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
        const sp<DisplayDevice>& hw(mDisplays[dpy]);
        if (hw->isDisplayOn()) {
            // transform the dirty region into this screen's coordinate space
            const Region dirtyRegion(hw->getDirtyRegion(repaintEverything));
            if (!dirtyRegion.isEmpty()) {
                // redraw the whole screen
                doComposeSurfaces(hw, Region(hw->bounds()));

                // and draw the dirty region
                const int32_t height = hw->getHeight();
                RenderEngine& engine(getRenderEngine());
                engine.fillRegionWithColor(dirtyRegion, height, 1, 0, 1, 1);

                hw->swapBuffers(getHwComposer());
            }
        }
    }

    postFramebuffer();

    if (mDebugRegion > 1) {
        usleep(mDebugRegion * 1000);
    }

    for (size_t displayId = 0; displayId < mDisplays.size(); ++displayId) {
        auto& displayDevice = mDisplays[displayId];
        if (!displayDevice->isDisplayOn()) {
            continue;
        }

        status_t result = displayDevice->prepareFrame(*mHwc);
        ALOGE_IF(result != NO_ERROR, "prepareFrame for display %zd failed:"
                " %d (%s)", displayId, result, strerror(-result));
    }
}

void SurfaceFlinger::preComposition(nsecs_t refreshStartTime)
{
    ATRACE_CALL();
    ALOGV("preComposition");

    bool needExtraInvalidate = false;
    mDrawingState.traverseInZOrder([&](Layer* layer) {
        if (layer->onPreComposition(refreshStartTime)) {
            needExtraInvalidate = true;
        }
    });

    if (needExtraInvalidate) {
        signalLayerUpdate();
    }
}

void SurfaceFlinger::updateCompositorTiming(
        nsecs_t vsyncPhase, nsecs_t vsyncInterval, nsecs_t compositeTime,
        std::shared_ptr<FenceTime>& presentFenceTime) {
    // Update queue of past composite+present times and determine the
    // most recently known composite to present latency.
    mCompositePresentTimes.push({compositeTime, presentFenceTime});
    nsecs_t compositeToPresentLatency = -1;
    while (!mCompositePresentTimes.empty()) {
        CompositePresentTime& cpt = mCompositePresentTimes.front();
        // Cached values should have been updated before calling this method,
        // which helps avoid duplicate syscalls.
        nsecs_t displayTime = cpt.display->getCachedSignalTime();
        if (displayTime == Fence::SIGNAL_TIME_PENDING) {
            break;
        }
        compositeToPresentLatency = displayTime - cpt.composite;
        mCompositePresentTimes.pop();
    }

    // Don't let mCompositePresentTimes grow unbounded, just in case.
    while (mCompositePresentTimes.size() > 16) {
        mCompositePresentTimes.pop();
    }

    setCompositorTimingSnapped(
            vsyncPhase, vsyncInterval, compositeToPresentLatency);
}

void SurfaceFlinger::setCompositorTimingSnapped(nsecs_t vsyncPhase,
        nsecs_t vsyncInterval, nsecs_t compositeToPresentLatency) {
    // Integer division and modulo round toward 0 not -inf, so we need to
    // treat negative and positive offsets differently.
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    nsecs_t sfPhase = mPrimaryDispSync.getSfPhase();
    nsecs_t idealLatency = (sfPhase > 0) ?
            (vsyncInterval - (sfPhase % vsyncInterval)) :
            ((-sfPhase) % vsyncInterval);
#else
    nsecs_t idealLatency = (sfVsyncPhaseOffsetNs > 0) ?
            (vsyncInterval - (sfVsyncPhaseOffsetNs % vsyncInterval)) :
            ((-sfVsyncPhaseOffsetNs) % vsyncInterval);
#endif

    // Just in case sfVsyncPhaseOffsetNs == -vsyncInterval.
    if (idealLatency <= 0) {
        idealLatency = vsyncInterval;
    }

    // Snap the latency to a value that removes scheduling jitter from the
    // composition and present times, which often have >1ms of jitter.
    // Reducing jitter is important if an app attempts to extrapolate
    // something (such as user input) to an accurate diasplay time.
    // Snapping also allows an app to precisely calculate sfVsyncPhaseOffsetNs
    // with (presentLatency % interval).
    nsecs_t bias = vsyncInterval / 2;
    int64_t extraVsyncs =
            (compositeToPresentLatency - idealLatency + bias) / vsyncInterval;
    nsecs_t snappedCompositeToPresentLatency = (extraVsyncs > 0) ?
            idealLatency + (extraVsyncs * vsyncInterval) : idealLatency;

    std::lock_guard<std::mutex> lock(mCompositorTimingLock);
    mCompositorTiming.deadline = vsyncPhase - idealLatency;
    mCompositorTiming.interval = vsyncInterval;
    mCompositorTiming.presentLatency = snappedCompositeToPresentLatency;
}

void SurfaceFlinger::postComposition(nsecs_t refreshStartTime)
{
    ATRACE_CALL();
    ALOGV("postComposition");

    // Release any buffers which were replaced this frame
    nsecs_t dequeueReadyTime = systemTime();
    for (auto& layer : mLayersWithQueuedFrames) {
        layer->releasePendingBuffer(dequeueReadyTime);
    }

    // |mStateLock| not needed as we are on the main thread
    const sp<const DisplayDevice> hw(getDefaultDisplayDeviceLocked());

    mGlCompositionDoneTimeline.updateSignalTimes();
    std::shared_ptr<FenceTime> glCompositionDoneFenceTime;
    if (mHwc->hasClientComposition(HWC_DISPLAY_PRIMARY)) {
        glCompositionDoneFenceTime =
                std::make_shared<FenceTime>(hw->getClientTargetAcquireFence());
        mGlCompositionDoneTimeline.push(glCompositionDoneFenceTime);
    } else {
        glCompositionDoneFenceTime = FenceTime::NO_FENCE;
    }

    mDisplayTimeline.updateSignalTimes();
    sp<Fence> presentFence = mHwc->getPresentFence(HWC_DISPLAY_PRIMARY);
    auto presentFenceTime = std::make_shared<FenceTime>(presentFence);
    mDisplayTimeline.push(presentFenceTime);

    nsecs_t vsyncPhase = mPrimaryDispSync.computeNextRefresh(0);
    nsecs_t vsyncInterval = mPrimaryDispSync.getPeriod();

    // We use the refreshStartTime which might be sampled a little later than
    // when we started doing work for this frame, but that should be okay
    // since updateCompositorTiming has snapping logic.
    updateCompositorTiming(
        vsyncPhase, vsyncInterval, refreshStartTime, presentFenceTime);
    CompositorTiming compositorTiming;
    {
        std::lock_guard<std::mutex> lock(mCompositorTimingLock);
        compositorTiming = mCompositorTiming;
    }

    mDrawingState.traverseInZOrder([&](Layer* layer) {
        bool frameLatched = layer->onPostComposition(glCompositionDoneFenceTime,
                presentFenceTime, compositorTiming);
        if (frameLatched) {
            recordBufferingStats(layer->getName().string(),
                    layer->getOccupancyHistory(false));
        }
    });

    if (presentFenceTime->isValid()) {
        if (mPrimaryDispSync.addPresentFence(presentFenceTime)) {
            enableHardwareVsync();
        } else {
            disableHardwareVsync(false);
        }
    }

    if (!hasSyncFramework) {
        if (hw->isDisplayOn()) {
            enableHardwareVsync();
        }
    }

    if (mAnimCompositionPending) {
        mAnimCompositionPending = false;

        if (presentFenceTime->isValid()) {
            mAnimFrameTracker.setActualPresentFence(
                    std::move(presentFenceTime));
        } else {
            // The HWC doesn't support present fences, so use the refresh
            // timestamp instead.
            nsecs_t presentTime =
                    mHwc->getRefreshTimestamp(HWC_DISPLAY_PRIMARY);
            mAnimFrameTracker.setActualPresentTime(presentTime);
        }
        mAnimFrameTracker.advanceFrame();
    }

    if (hw->getPowerMode() == HWC_POWER_MODE_OFF) {
        return;
    }

    nsecs_t currentTime = systemTime();
    if (mHasPoweredOff) {
        mHasPoweredOff = false;
    } else {
        nsecs_t elapsedTime = currentTime - mLastSwapTime;
        size_t numPeriods = static_cast<size_t>(elapsedTime / vsyncInterval);
        if (numPeriods < NUM_BUCKETS - 1) {
            mFrameBuckets[numPeriods] += elapsedTime;
        } else {
            mFrameBuckets[NUM_BUCKETS - 1] += elapsedTime;
        }
        mTotalTime += elapsedTime;
    }
    mLastSwapTime = currentTime;
}

void SurfaceFlinger::rebuildLayerStacks() {
    ATRACE_CALL();
    ALOGV("rebuildLayerStacks");

    // rebuild the visible layer list per screen
    if (CC_UNLIKELY(mVisibleRegionsDirty)) {
        ATRACE_CALL();
        mVisibleRegionsDirty = false;
        invalidateHwcGeometry();

        for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
            Region opaqueRegion;
            Region dirtyRegion;
            Vector<sp<Layer>> layersSortedByZ;
            const sp<DisplayDevice>& displayDevice(mDisplays[dpy]);
            const Transform& tr(displayDevice->getTransform());
            const Rect bounds(displayDevice->getBounds());
            if (displayDevice->isDisplayOn()) {
                computeVisibleRegions(displayDevice, dirtyRegion, opaqueRegion);

                mDrawingState.traverseInZOrder([&](Layer* layer) {
                    if (layer->belongsToDisplay(displayDevice->getLayerStack(),
                                displayDevice->isPrimary())) {
                        Region drawRegion(tr.transform(
                                layer->visibleNonTransparentRegion));
                        drawRegion.andSelf(bounds);
                        if (!drawRegion.isEmpty()) {
                            layersSortedByZ.add(layer);
                        } else {
                            // Clear out the HWC layer if this layer was
                            // previously visible, but no longer is
                            layer->destroyHwcLayer(
                                    displayDevice->getHwcDisplayId());
                        }
                    } else {
                        // WM changes displayDevice->layerStack upon sleep/awake.
                        // Here we make sure we delete the HWC layers even if
                        // WM changed their layer stack.
                        layer->destroyHwcLayer(displayDevice->getHwcDisplayId());
                    }
                });
            }
            displayDevice->setVisibleLayersSortedByZ(layersSortedByZ);
            displayDevice->undefinedRegion.set(bounds);
            displayDevice->undefinedRegion.subtractSelf(
                    tr.transform(opaqueRegion));
            displayDevice->dirtyRegion.orSelf(dirtyRegion);
        }
    }
}

mat4 SurfaceFlinger::computeSaturationMatrix() const {
    if (mSaturation == 1.0f) {
        return mat4();
    }

    // Rec.709 luma coefficients
    float3 luminance{0.213f, 0.715f, 0.072f};
    luminance *= 1.0f - mSaturation;
    return mat4(
        vec4{luminance.r + mSaturation, luminance.r, luminance.r, 0.0f},
        vec4{luminance.g, luminance.g + mSaturation, luminance.g, 0.0f},
        vec4{luminance.b, luminance.b, luminance.b + mSaturation, 0.0f},
        vec4{0.0f, 0.0f, 0.0f, 1.0f}
    );
}

// pickColorMode translates a given dataspace into the best available color mode.
// Currently only support sRGB and Display-P3.
android_color_mode SurfaceFlinger::pickColorMode(android_dataspace dataSpace) const {
    if (mForceNativeColorMode) {
        return HAL_COLOR_MODE_NATIVE;
    }

    switch (dataSpace) {
        // treat Unknown as regular SRGB buffer, since that's what the rest of the
        // system expects.
        case HAL_DATASPACE_UNKNOWN:
        case HAL_DATASPACE_SRGB:
        case HAL_DATASPACE_V0_SRGB:
            return HAL_COLOR_MODE_SRGB;
            break;

        case HAL_DATASPACE_DISPLAY_P3:
            return HAL_COLOR_MODE_DISPLAY_P3;
            break;

        default:
            // TODO (courtneygo): Do we want to assert an error here?
            ALOGE("No color mode mapping for %s (%#x)", dataspaceDetails(dataSpace).c_str(),
                  dataSpace);
            return HAL_COLOR_MODE_SRGB;
            break;
    }
}

android_dataspace SurfaceFlinger::bestTargetDataSpace(
        android_dataspace a, android_dataspace b) const {
    // Only support sRGB and Display-P3 right now.
    if (a == HAL_DATASPACE_DISPLAY_P3 || b == HAL_DATASPACE_DISPLAY_P3) {
        return HAL_DATASPACE_DISPLAY_P3;
    }
    if (a == HAL_DATASPACE_V0_SCRGB_LINEAR || b == HAL_DATASPACE_V0_SCRGB_LINEAR) {
        return HAL_DATASPACE_DISPLAY_P3;
    }
    if (a == HAL_DATASPACE_V0_SCRGB || b == HAL_DATASPACE_V0_SCRGB) {
        return HAL_DATASPACE_DISPLAY_P3;
    }

    return HAL_DATASPACE_V0_SRGB;
}

void SurfaceFlinger::setUpHWComposer() {
    ATRACE_CALL();
    ALOGV("setUpHWComposer");

    for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
        bool dirty = !mDisplays[dpy]->getDirtyRegion(false).isEmpty();
        bool empty = mDisplays[dpy]->getVisibleLayersSortedByZ().size() == 0;
        bool wasEmpty = !mDisplays[dpy]->lastCompositionHadVisibleLayers;

        // If nothing has changed (!dirty), don't recompose.
        // If something changed, but we don't currently have any visible layers,
        //   and didn't when we last did a composition, then skip it this time.
        // The second rule does two things:
        // - When all layers are removed from a display, we'll emit one black
        //   frame, then nothing more until we get new layers.
        // - When a display is created with a private layer stack, we won't
        //   emit any black frames until a layer is added to the layer stack.
        bool mustRecompose = dirty && !(empty && wasEmpty);

        ALOGV_IF(mDisplays[dpy]->getDisplayType() == DisplayDevice::DISPLAY_VIRTUAL,
                "dpy[%zu]: %s composition (%sdirty %sempty %swasEmpty)", dpy,
                mustRecompose ? "doing" : "skipping",
                dirty ? "+" : "-",
                empty ? "+" : "-",
                wasEmpty ? "+" : "-");

        mDisplays[dpy]->beginFrame(mustRecompose);

        if (mustRecompose) {
            mDisplays[dpy]->lastCompositionHadVisibleLayers = !empty;
        }
    }

    // build the h/w work list
    if (CC_UNLIKELY(mGeometryInvalid)) {
        mGeometryInvalid = false;
        for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
            sp<const DisplayDevice> displayDevice(mDisplays[dpy]);
            const auto hwcId = displayDevice->getHwcDisplayId();
            if (hwcId >= 0) {
                const Vector<sp<Layer>>& currentLayers(
                        displayDevice->getVisibleLayersSortedByZ());
                for (size_t i = 0; i < currentLayers.size(); i++) {
                    const auto& layer = currentLayers[i];
                    if (!layer->hasHwcLayer(hwcId)) {
                        if (!layer->createHwcLayer(mHwc.get(), hwcId)) {
                            layer->forceClientComposition(hwcId);
                            continue;
                        }
                    }

                    layer->setGeometry(displayDevice, i);
                    if (mDebugDisableHWC || mDebugRegion) {
                        layer->forceClientComposition(hwcId);
                    }
                }
            }
        }
    }


    mat4 colorMatrix = mColorMatrix * computeSaturationMatrix() * mDaltonizer();

    // Set the per-frame data
    for (size_t displayId = 0; displayId < mDisplays.size(); ++displayId) {
        auto& displayDevice = mDisplays[displayId];
        const auto hwcId = displayDevice->getHwcDisplayId();

        if (hwcId < 0) {
            continue;
        }
        if (colorMatrix != mPreviousColorMatrix) {
            status_t result = mHwc->setColorTransform(hwcId, colorMatrix);
            ALOGE_IF(result != NO_ERROR, "Failed to set color transform on "
                    "display %zd: %d", displayId, result);
        }
        for (auto& layer : displayDevice->getVisibleLayersSortedByZ()) {
            layer->setPerFrameData(displayDevice);
        }

        if (hasWideColorDisplay) {
            android_color_mode newColorMode;
            android_dataspace newDataSpace = HAL_DATASPACE_V0_SRGB;

            for (auto& layer : displayDevice->getVisibleLayersSortedByZ()) {
                newDataSpace = bestTargetDataSpace(layer->getDataSpace(), newDataSpace);
                ALOGV("layer: %s, dataspace: %s (%#x), newDataSpace: %s (%#x)",
                      layer->getName().string(), dataspaceDetails(layer->getDataSpace()).c_str(),
                      layer->getDataSpace(), dataspaceDetails(newDataSpace).c_str(), newDataSpace);
            }
            newColorMode = pickColorMode(newDataSpace);

            setActiveColorModeInternal(displayDevice, newColorMode);
        }
    }

    mPreviousColorMatrix = colorMatrix;

    for (size_t displayId = 0; displayId < mDisplays.size(); ++displayId) {
        auto& displayDevice = mDisplays[displayId];
        if (!displayDevice->isDisplayOn()) {
            continue;
        }

        status_t result = displayDevice->prepareFrame(*mHwc);
        ALOGE_IF(result != NO_ERROR, "prepareFrame for display %zd failed:"
                " %d (%s)", displayId, result, strerror(-result));
    }
}

void SurfaceFlinger::doComposition() {
#ifdef MTK_SF_DEBUG_SUPPORT
    // to slow down FPS
    if (CC_UNLIKELY(0 != sPropertiesState.mDelayTime)) {
        ALOGI("SurfaceFlinger slow motion timer: %d ms", sPropertiesState.mDelayTime);
        usleep(1000 * sPropertiesState.mDelayTime);
    }
#endif
    ATRACE_CALL();
    ALOGV("doComposition");

    const bool repaintEverything = android_atomic_and(0, &mRepaintEverything);
    for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
        const sp<DisplayDevice>& hw(mDisplays[dpy]);
        if (hw->isDisplayOn()) {
#ifdef MTK_SF_DEBUG_SUPPORT
            char tag[32];
            snprintf(tag, sizeof(tag), "doDisplayComposition_%d", hw->getHwcDisplayId());
            ATRACE_NAME(tag);
#endif
            // transform the dirty region into this screen's coordinate space
            const Region dirtyRegion(hw->getDirtyRegion(repaintEverything));

            // repaint the framebuffer (if needed)
            doDisplayComposition(hw, dirtyRegion);

            hw->dirtyRegion.clear();
            hw->flip(hw->swapRegion);
            hw->swapRegion.clear();
        }
    }
    postFramebuffer();
}

void SurfaceFlinger::postFramebuffer()
{
    ATRACE_CALL();
    ALOGV("postFramebuffer");

    const nsecs_t now = systemTime();
    mDebugInSwapBuffers = now;

    for (size_t displayId = 0; displayId < mDisplays.size(); ++displayId) {
        auto& displayDevice = mDisplays[displayId];
        if (!displayDevice->isDisplayOn()) {
            continue;
        }
        const auto hwcId = displayDevice->getHwcDisplayId();
        if (hwcId >= 0) {
            mHwc->presentAndGetReleaseFences(hwcId);
        }
        displayDevice->onSwapBuffersCompleted();

#ifdef MTK_IMG_DDK_BUGFIX
        if (hwcId != HWC_DISPLAY_VIRTUAL)
            displayDevice->makeCurrent(mEGLDisplay, mEGLContext);
#else
        displayDevice->makeCurrent(mEGLDisplay, mEGLContext);
#endif
        for (auto& layer : displayDevice->getVisibleLayersSortedByZ()) {
            sp<Fence> releaseFence = Fence::NO_FENCE;
            if (layer->getCompositionType(hwcId) == HWC2::Composition::Client) {
                releaseFence = displayDevice->getClientTargetAcquireFence();
            } else {
                auto hwcLayer = layer->getHwcLayer(hwcId);
                releaseFence = mHwc->getLayerReleaseFence(hwcId, hwcLayer);
            }
            layer->onLayerDisplayed(releaseFence);
        }
        if (hwcId >= 0) {
            mHwc->clearReleaseFences(hwcId);
        }
    }

    mLastSwapBufferTime = systemTime() - now;
    mDebugInSwapBuffers = 0;

    // |mStateLock| not needed as we are on the main thread
    uint32_t flipCount = getDefaultDisplayDeviceLocked()->getPageFlipCount();
    if (flipCount % LOG_FRAME_STATS_PERIOD == 0) {
        logFrameStats();
    }
}

void SurfaceFlinger::handleTransaction(uint32_t transactionFlags)
{
    ATRACE_CALL();

    // here we keep a copy of the drawing state (that is the state that's
    // going to be overwritten by handleTransactionLocked()) outside of
    // mStateLock so that the side-effects of the State assignment
    // don't happen with mStateLock held (which can cause deadlocks).
    State drawingState(mDrawingState);

#ifdef MTK_AOSP_DISPLAY_BUGFIX
    // In some cases, SF will call to ~Clinet in the becoming flow.
    // And it will cause re-entry and makes system deadlock.
    // So we grap the strong pointer of Clients to avoid the flow
    // call to ~Client.
    mReservedClientPointer.clear();
    for (const auto& layer : mDrawingState.layersSortedByZ) {
        sp<Client> c(layer->getClinet());
        if (c != nullptr)
            mReservedClientPointer.push_back(c);
    }
#endif

#ifdef MTK_SF_WATCHDOG_SUPPORT
    // This mutex may be held by dump function, so stop monitor temporarily.
    if (mMessageWDT != nullptr) {
        mMessageWDT->delAnchor();
    }
    Mutex::Autolock _l(mStateLock);
    if (mMessageWDT != nullptr) {
        mMessageWDT->setAnchor(String8::format("reset anchor by [%s]", __func__));
    }
#else
    Mutex::Autolock _l(mStateLock);
#endif
    const nsecs_t now = systemTime();
    mDebugInTransaction = now;

    // Here we're guaranteed that some transaction flags are set
    // so we can call handleTransactionLocked() unconditionally.
    // We call getTransactionFlags(), which will also clear the flags,
    // with mStateLock held to guarantee that mCurrentState won't change
    // until the transaction is committed.

    transactionFlags = getTransactionFlags(eTransactionMask);
    handleTransactionLocked(transactionFlags);

    mLastTransactionTime = systemTime() - now;
    mDebugInTransaction = 0;
    invalidateHwcGeometry();
    // here the transaction has been committed
}

void SurfaceFlinger::handleTransactionLocked(uint32_t transactionFlags)
{
    // Notify all layers of available frames
    mCurrentState.traverseInZOrder([](Layer* layer) {
        layer->notifyAvailableFrames();
    });

    /*
     * Traversal of the children
     * (perform the transaction for each of them if needed)
     */

    if (transactionFlags & eTraversalNeeded) {
        mCurrentState.traverseInZOrder([&](Layer* layer) {
            uint32_t trFlags = layer->getTransactionFlags(eTransactionNeeded);
            if (!trFlags) return;

            const uint32_t flags = layer->doTransaction(0);
            if (flags & Layer::eVisibleRegion)
                mVisibleRegionsDirty = true;
        });
    }

    /*
     * Perform display own transactions if needed
     */

    if (transactionFlags & eDisplayTransactionNeeded) {
        // here we take advantage of Vector's copy-on-write semantics to
        // improve performance by skipping the transaction entirely when
        // know that the lists are identical
        const KeyedVector<  wp<IBinder>, DisplayDeviceState>& curr(mCurrentState.displays);
        const KeyedVector<  wp<IBinder>, DisplayDeviceState>& draw(mDrawingState.displays);
        if (!curr.isIdenticalTo(draw)) {
            mVisibleRegionsDirty = true;
            const size_t cc = curr.size();
                  size_t dc = draw.size();

            // find the displays that were removed
            // (ie: in drawing state but not in current state)
            // also handle displays that changed
            // (ie: displays that are in both lists)
            for (size_t i=0 ; i<dc ; i++) {
                const ssize_t j = curr.indexOfKey(draw.keyAt(i));
                if (j < 0) {
                    // in drawing state but not in current state
                    if (!draw[i].isMainDisplay()) {
                        // Call makeCurrent() on the primary display so we can
                        // be sure that nothing associated with this display
                        // is current.
                        const sp<const DisplayDevice> defaultDisplay(getDefaultDisplayDeviceLocked());
                        defaultDisplay->makeCurrent(mEGLDisplay, mEGLContext);
                        sp<DisplayDevice> hw(getDisplayDeviceLocked(draw.keyAt(i)));
                        if (hw != NULL)
                            hw->disconnect(getHwComposer());
                        if (draw[i].type < DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES)
                            mEventThread->onHotplugReceived(draw[i].type, false);
                        mDisplays.removeItem(draw.keyAt(i));
                    } else {
                        ALOGW("trying to remove the main display");
                    }
                } else {
                    // this display is in both lists. see if something changed.
                    const DisplayDeviceState& state(curr[j]);
                    const wp<IBinder>& display(curr.keyAt(j));
                    const sp<IBinder> state_binder = IInterface::asBinder(state.surface);
                    const sp<IBinder> draw_binder = IInterface::asBinder(draw[i].surface);
                    if (state_binder != draw_binder) {
                        // changing the surface is like destroying and
                        // recreating the DisplayDevice, so we just remove it
                        // from the drawing state, so that it get re-added
                        // below.
                        sp<DisplayDevice> hw(getDisplayDeviceLocked(display));
                        if (hw != NULL)
                            hw->disconnect(getHwComposer());
                        mDisplays.removeItem(display);
                        mDrawingState.displays.removeItemsAt(i);
                        dc--; i--;
                        // at this point we must loop to the next item
                        continue;
                    }

                    const sp<DisplayDevice> disp(getDisplayDeviceLocked(display));
                    if (disp != NULL) {
                        if (state.layerStack != draw[i].layerStack) {
                            disp->setLayerStack(state.layerStack);
                        }
                        if ((state.orientation != draw[i].orientation)
                                || (state.viewport != draw[i].viewport)
                                || (state.frame != draw[i].frame))
                        {
                            disp->setProjection(state.orientation,
                                    state.viewport, state.frame);
                        }
                        if (state.width != draw[i].width || state.height != draw[i].height) {
                            disp->setDisplaySize(state.width, state.height);
                        }
                    }
                }
            }

            // find displays that were added
            // (ie: in current state but not in drawing state)
            for (size_t i=0 ; i<cc ; i++) {
                if (draw.indexOfKey(curr.keyAt(i)) < 0) {
                    const DisplayDeviceState& state(curr[i]);

                    sp<DisplaySurface> dispSurface;
                    sp<IGraphicBufferProducer> producer;
                    sp<IGraphicBufferProducer> bqProducer;
                    sp<IGraphicBufferConsumer> bqConsumer;
                    BufferQueue::createBufferQueue(&bqProducer, &bqConsumer);

                    int32_t hwcId = -1;
                    if (state.isVirtualDisplay()) {
                        // Virtual displays without a surface are dormant:
                        // they have external state (layer stack, projection,
                        // etc.) but no internal state (i.e. a DisplayDevice).
                        if (state.surface != NULL) {

                            // Allow VR composer to use virtual displays.
                            if (mUseHwcVirtualDisplays || mHwc->isUsingVrComposer()) {
                                int width = 0;
                                int status = state.surface->query(
                                        NATIVE_WINDOW_WIDTH, &width);
                                ALOGE_IF(status != NO_ERROR,
                                        "Unable to query width (%d)", status);
                                int height = 0;
                                status = state.surface->query(
                                        NATIVE_WINDOW_HEIGHT, &height);
                                ALOGE_IF(status != NO_ERROR,
                                        "Unable to query height (%d)", status);
                                int intFormat = 0;
                                status = state.surface->query(
                                        NATIVE_WINDOW_FORMAT, &intFormat);
                                ALOGE_IF(status != NO_ERROR,
                                        "Unable to query format (%d)", status);
                                auto format = static_cast<android_pixel_format_t>(
                                        intFormat);

                                mHwc->allocateVirtualDisplay(width, height, &format,
                                        &hwcId);
                            }

                            // TODO: Plumb requested format back up to consumer

                            sp<VirtualDisplaySurface> vds =
                                    new VirtualDisplaySurface(*mHwc,
                                            hwcId, state.surface, bqProducer,
                                            bqConsumer, state.displayName);

                            dispSurface = vds;
                            producer = vds;
                        }
                    } else {
                        ALOGE_IF(state.surface!=NULL,
                                "adding a supported display, but rendering "
                                "surface is provided (%p), ignoring it",
                                state.surface.get());

                        hwcId = state.type;
                        dispSurface = new FramebufferSurface(*mHwc, hwcId, bqConsumer);
                        producer = bqProducer;
                    }

                    const wp<IBinder>& display(curr.keyAt(i));
                    if (dispSurface != NULL) {
                        sp<DisplayDevice> hw =
                                new DisplayDevice(this, state.type, hwcId, state.isSecure, display,
                                                  dispSurface, producer,
                                                  mRenderEngine->getEGLConfig(),
                                                  hasWideColorDisplay);
                        hw->setLayerStack(state.layerStack);
                        hw->setProjection(state.orientation,
                                state.viewport, state.frame);
                        hw->setDisplayName(state.displayName);
                        mDisplays.add(display, hw);
                        if (!state.isVirtualDisplay()) {
                            mEventThread->onHotplugReceived(state.type, true);
                        }
                    }
                }
            }
        }
    }

    if (transactionFlags & (eTraversalNeeded|eDisplayTransactionNeeded)) {
        // The transform hint might have changed for some layers
        // (either because a display has changed, or because a layer
        // as changed).
        //
        // Walk through all the layers in currentLayers,
        // and update their transform hint.
        //
        // If a layer is visible only on a single display, then that
        // display is used to calculate the hint, otherwise we use the
        // default display.
        //
        // NOTE: we do this here, rather than in rebuildLayerStacks() so that
        // the hint is set before we acquire a buffer from the surface texture.
        //
        // NOTE: layer transactions have taken place already, so we use their
        // drawing state. However, SurfaceFlinger's own transaction has not
        // happened yet, so we must use the current state layer list
        // (soon to become the drawing state list).
        //
        sp<const DisplayDevice> disp;
        uint32_t currentlayerStack = 0;
        bool first = true;
        mCurrentState.traverseInZOrder([&](Layer* layer) {
            // NOTE: we rely on the fact that layers are sorted by
            // layerStack first (so we don't have to traverse the list
            // of displays for every layer).
            uint32_t layerStack = layer->getLayerStack();
            if (first || currentlayerStack != layerStack) {
                currentlayerStack = layerStack;
                // figure out if this layerstack is mirrored
                // (more than one display) if so, pick the default display,
                // if not, pick the only display it's on.
                disp.clear();
                for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
                    sp<const DisplayDevice> hw(mDisplays[dpy]);
                    if (layer->belongsToDisplay(hw->getLayerStack(), hw->isPrimary())) {
                        if (disp == NULL) {
                            disp = hw;
                        } else {
                            disp = NULL;
                            break;
                        }
                    }
                }
            }
            if (disp == NULL) {
                // NOTE: TEMPORARY FIX ONLY. Real fix should cause layers to
                // redraw after transform hint changes. See bug 8508397.

                // could be null when this layer is using a layerStack
                // that is not visible on any display. Also can occur at
                // screen off/on times.
                disp = getDefaultDisplayDeviceLocked();
            }
            layer->updateTransformHint(disp);

            first = false;
        });
    }


    /*
     * Perform our own transaction if needed
     */

    if (mLayersAdded) {
        mLayersAdded = false;
        // Layers have been added.
        mVisibleRegionsDirty = true;
    }

    // some layers might have been removed, so
    // we need to update the regions they're exposing.
    if (mLayersRemoved) {
        mLayersRemoved = false;
        mVisibleRegionsDirty = true;
        mDrawingState.traverseInZOrder([&](Layer* layer) {
            if (mLayersPendingRemoval.indexOf(layer) >= 0) {
                // this layer is not visible anymore
                // TODO: we could traverse the tree from front to back and
                //       compute the actual visible region
                // TODO: we could cache the transformed region
                Region visibleReg;
                visibleReg.set(layer->computeScreenBounds());
                invalidateLayerStack(layer, visibleReg);
            }
        });
    }

    commitTransaction();

    updateCursorAsync();
}

void SurfaceFlinger::updateCursorAsync()
{
    for (size_t displayId = 0; displayId < mDisplays.size(); ++displayId) {
        auto& displayDevice = mDisplays[displayId];
        if (displayDevice->getHwcDisplayId() < 0) {
            continue;
        }

        for (auto& layer : displayDevice->getVisibleLayersSortedByZ()) {
            layer->updateCursorPosition(displayDevice);
        }
    }
}

void SurfaceFlinger::commitTransaction()
{
    if (!mLayersPendingRemoval.isEmpty()) {
        // Notify removed layers now that they can't be drawn from
        for (const auto& l : mLayersPendingRemoval) {
            recordBufferingStats(l->getName().string(),
                    l->getOccupancyHistory(true));
            l->onRemoved();
        }
        mLayersPendingRemoval.clear();
    }

    // If this transaction is part of a window animation then the next frame
    // we composite should be considered an animation as well.
    mAnimCompositionPending = mAnimTransactionPending;

    mDrawingState = mCurrentState;
    mDrawingState.traverseInZOrder([](Layer* layer) {
        layer->commitChildList();
    });
    mTransactionPending = false;
    mAnimTransactionPending = false;
    mTransactionCV.broadcast();
}

void SurfaceFlinger::computeVisibleRegions(const sp<const DisplayDevice>& displayDevice,
        Region& outDirtyRegion, Region& outOpaqueRegion)
{
    ATRACE_CALL();
    ALOGV("computeVisibleRegions");

    Region aboveOpaqueLayers;
    Region aboveCoveredLayers;
    Region dirty;

    outDirtyRegion.clear();

    mDrawingState.traverseInReverseZOrder([&](Layer* layer) {
        // start with the whole surface at its current location
        const Layer::State& s(layer->getDrawingState());

        // only consider the layers on the given layer stack
        if (!layer->belongsToDisplay(displayDevice->getLayerStack(), displayDevice->isPrimary()))
            return;

        /*
         * opaqueRegion: area of a surface that is fully opaque.
         */
        Region opaqueRegion;

        /*
         * visibleRegion: area of a surface that is visible on screen
         * and not fully transparent. This is essentially the layer's
         * footprint minus the opaque regions above it.
         * Areas covered by a translucent surface are considered visible.
         */
        Region visibleRegion;

        /*
         * coveredRegion: area of a surface that is covered by all
         * visible regions above it (which includes the translucent areas).
         */
        Region coveredRegion;

        /*
         * transparentRegion: area of a surface that is hinted to be completely
         * transparent. This is only used to tell when the layer has no visible
         * non-transparent regions and can be removed from the layer list. It
         * does not affect the visibleRegion of this layer or any layers
         * beneath it. The hint may not be correct if apps don't respect the
         * SurfaceView restrictions (which, sadly, some don't).
         */
        Region transparentRegion;


        // handle hidden surfaces by setting the visible region to empty
        if (CC_LIKELY(layer->isVisible())) {
            const bool translucent = !layer->isOpaque(s);
            Rect bounds(layer->computeScreenBounds());
            visibleRegion.set(bounds);
            Transform tr = layer->getTransform();
            if (!visibleRegion.isEmpty()) {
                // Remove the transparent area from the visible region
                if (translucent) {
                    if (tr.preserveRects()) {
                        // transform the transparent region
                        transparentRegion = tr.transform(s.activeTransparentRegion);
                    } else {
                        // transformation too complex, can't do the
                        // transparent region optimization.
                        transparentRegion.clear();
                    }
                }

                // compute the opaque region
                const int32_t layerOrientation = tr.getOrientation();
                if (s.alpha == 1.0f && !translucent &&
                        ((layerOrientation & Transform::ROT_INVALID) == false)) {
                    // the opaque region is the layer's footprint
                    opaqueRegion = visibleRegion;
                }
            }
        }

        // Clip the covered region to the visible region
        coveredRegion = aboveCoveredLayers.intersect(visibleRegion);

        // Update aboveCoveredLayers for next (lower) layer
        aboveCoveredLayers.orSelf(visibleRegion);

        // subtract the opaque region covered by the layers above us
        visibleRegion.subtractSelf(aboveOpaqueLayers);

        // compute this layer's dirty region
        if (layer->contentDirty) {
            // we need to invalidate the whole region
            dirty = visibleRegion;
            // as well, as the old visible region
            dirty.orSelf(layer->visibleRegion);
            layer->contentDirty = false;
        } else {
            /* compute the exposed region:
             *   the exposed region consists of two components:
             *   1) what's VISIBLE now and was COVERED before
             *   2) what's EXPOSED now less what was EXPOSED before
             *
             * note that (1) is conservative, we start with the whole
             * visible region but only keep what used to be covered by
             * something -- which mean it may have been exposed.
             *
             * (2) handles areas that were not covered by anything but got
             * exposed because of a resize.
             */
            const Region newExposed = visibleRegion - coveredRegion;
            const Region oldVisibleRegion = layer->visibleRegion;
            const Region oldCoveredRegion = layer->coveredRegion;
            const Region oldExposed = oldVisibleRegion - oldCoveredRegion;
            dirty = (visibleRegion&oldCoveredRegion) | (newExposed-oldExposed);
        }
        dirty.subtractSelf(aboveOpaqueLayers);

        // accumulate to the screen dirty region
        outDirtyRegion.orSelf(dirty);

        // Update aboveOpaqueLayers for next (lower) layer
        aboveOpaqueLayers.orSelf(opaqueRegion);

        // Store the visible region in screen space
        layer->setVisibleRegion(visibleRegion);
        layer->setCoveredRegion(coveredRegion);
        layer->setVisibleNonTransparentRegion(
                visibleRegion.subtract(transparentRegion));
    });

    outOpaqueRegion = aboveOpaqueLayers;
}

void SurfaceFlinger::invalidateLayerStack(const sp<const Layer>& layer, const Region& dirty) {
    for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
        const sp<DisplayDevice>& hw(mDisplays[dpy]);
        if (layer->belongsToDisplay(hw->getLayerStack(), hw->isPrimary())) {
            hw->dirtyRegion.orSelf(dirty);
        }
    }
}

bool SurfaceFlinger::handlePageFlip()
{
    ALOGV("handlePageFlip");

    nsecs_t latchTime = systemTime();

    bool visibleRegions = false;
    bool frameQueued = false;
    bool newDataLatched = false;

    // Store the set of layers that need updates. This set must not change as
    // buffers are being latched, as this could result in a deadlock.
    // Example: Two producers share the same command stream and:
    // 1.) Layer 0 is latched
    // 2.) Layer 0 gets a new frame
    // 2.) Layer 1 gets a new frame
    // 3.) Layer 1 is latched.
    // Display is now waiting on Layer 1's frame, which is behind layer 0's
    // second frame. But layer 0's second frame could be waiting on display.
    mDrawingState.traverseInZOrder([&](Layer* layer) {
        if (layer->hasQueuedFrame()) {
            frameQueued = true;
            if (layer->shouldPresentNow(mPrimaryDispSync)) {
                mLayersWithQueuedFrames.push_back(layer);
            } else {
                layer->useEmptyDamage();
            }
        } else {
            layer->useEmptyDamage();
        }
    });

    for (auto& layer : mLayersWithQueuedFrames) {
#ifdef MTK_AOSP_DISPLAY_BUGFIX
        // If buffer is rejected during latchbuffer().
        // The Layer::mCurrentState will be accessed,
        // and will need to hold transaction lock.
        const Region dirty(({
            Mutex::Autolock _l(mStateLock);
            layer->latchBuffer(visibleRegions, latchTime);}));
#else
        const Region dirty(layer->latchBuffer(visibleRegions, latchTime));
#endif
        layer->useSurfaceDamage();
        invalidateLayerStack(layer, dirty);
        if (layer->isBufferLatched()) {
            newDataLatched = true;
        }
    }

    mVisibleRegionsDirty |= visibleRegions;

    // If we will need to wake up at some time in the future to deal with a
    // queued frame that shouldn't be displayed during this vsync period, wake
    // up during the next vsync period to check again.
    if (frameQueued && (mLayersWithQueuedFrames.empty() || !newDataLatched)) {
        signalLayerUpdate();
    }

    // Only continue with the refresh if there is actually new work to do
    return !mLayersWithQueuedFrames.empty() && newDataLatched;
}

void SurfaceFlinger::invalidateHwcGeometry()
{
    mGeometryInvalid = true;
}


void SurfaceFlinger::doDisplayComposition(
        const sp<const DisplayDevice>& displayDevice,
        const Region& inDirtyRegion)
{
    // We only need to actually compose the display if:
    // 1) It is being handled by hardware composer, which may need this to
    //    keep its virtual display state machine in sync, or
    // 2) There is work to be done (the dirty region isn't empty)
    bool isHwcDisplay = displayDevice->getHwcDisplayId() >= 0;
    if (!isHwcDisplay && inDirtyRegion.isEmpty()) {
        ALOGV("Skipping display composition");
        return;
    }

    ALOGV("doDisplayComposition");

#ifdef MTK_SF_DEBUG_SUPPORT

    // doDisplayComposition debug msg
    if (CC_UNLIKELY(sPropertiesState.mLogRepaint)) {
        ALOGD("[doDisplayComposition] (type:%d hwcid:%d name:%s) +",
            displayDevice->getDisplayType(), displayDevice->getHwcDisplayId(),
            displayDevice->getDisplayName().string());
    }
#endif

    Region dirtyRegion(inDirtyRegion);

    // compute the invalid region
    displayDevice->swapRegion.orSelf(dirtyRegion);

    uint32_t flags = displayDevice->getFlags();
    if (flags & DisplayDevice::SWAP_RECTANGLE) {
        // we can redraw only what's dirty, but since SWAP_RECTANGLE only
        // takes a rectangle, we must make sure to update that whole
        // rectangle in that case
        dirtyRegion.set(displayDevice->swapRegion.bounds());
    } else {
        if (flags & DisplayDevice::PARTIAL_UPDATES) {
            // We need to redraw the rectangle that will be updated
            // (pushed to the framebuffer).
            // This is needed because PARTIAL_UPDATES only takes one
            // rectangle instead of a region (see DisplayDevice::flip())
            dirtyRegion.set(displayDevice->swapRegion.bounds());
        } else {
            // we need to redraw everything (the whole screen)
            dirtyRegion.set(displayDevice->bounds());
            displayDevice->swapRegion = dirtyRegion;
        }
    }

    if (!doComposeSurfaces(displayDevice, dirtyRegion)) return;

    // update the swap region and clear the dirty region
    displayDevice->swapRegion.orSelf(dirtyRegion);

    // swap buffers (presentation)
    displayDevice->swapBuffers(getHwComposer());

#ifdef MTK_SF_DEBUG_SUPPORT
    // doDisplayComposition debug msg
    if (CC_UNLIKELY(sPropertiesState.mLogRepaint)) {
        ALOGD("[doDisplayComposition] -");
    }
#endif
}

bool SurfaceFlinger::doComposeSurfaces(
        const sp<const DisplayDevice>& displayDevice, const Region& dirty)
{
    ALOGV("doComposeSurfaces");

    const auto hwcId = displayDevice->getHwcDisplayId();

    mat4 oldColorMatrix;
    const bool applyColorMatrix = !mHwc->hasDeviceComposition(hwcId) &&
            !mHwc->hasCapability(HWC2::Capability::SkipClientColorTransform);
    if (applyColorMatrix) {
        mat4 colorMatrix = mColorMatrix * mDaltonizer();
        oldColorMatrix = getRenderEngine().setupColorTransform(colorMatrix);
    }

    bool hasClientComposition = mHwc->hasClientComposition(hwcId);
    if (hasClientComposition) {
        ALOGV("hasClientComposition");

#ifdef USE_HWC2
        mRenderEngine->setWideColor(
                displayDevice->getWideColorSupport() && !mForceNativeColorMode);
        mRenderEngine->setColorMode(mForceNativeColorMode ?
                HAL_COLOR_MODE_NATIVE : displayDevice->getActiveColorMode());
#endif
        if (!displayDevice->makeCurrent(mEGLDisplay, mEGLContext)) {
            ALOGW("DisplayDevice::makeCurrent failed. Aborting surface composition for display %s",
                  displayDevice->getDisplayName().string());
            eglMakeCurrent(mEGLDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

            // |mStateLock| not needed as we are on the main thread
            if(!getDefaultDisplayDeviceLocked()->makeCurrent(mEGLDisplay, mEGLContext)) {
              ALOGE("DisplayDevice::makeCurrent on default display failed. Aborting.");
            }
            return false;
        }

        // Never touch the framebuffer if we don't have any framebuffer layers
        const bool hasDeviceComposition = mHwc->hasDeviceComposition(hwcId);
        if (hasDeviceComposition) {
            // when using overlays, we assume a fully transparent framebuffer
            // NOTE: we could reduce how much we need to clear, for instance
            // remove where there are opaque FB layers. however, on some
            // GPUs doing a "clean slate" clear might be more efficient.
            // We'll revisit later if needed.
            mRenderEngine->clearWithColor(0, 0, 0, 0);
        } else {
            // we start with the whole screen area
            const Region bounds(displayDevice->getBounds());

            // we remove the scissor part
            // we're left with the letterbox region
            // (common case is that letterbox ends-up being empty)
            const Region letterbox(bounds.subtract(displayDevice->getScissor()));

            // compute the area to clear
            Region region(displayDevice->undefinedRegion.merge(letterbox));

            // but limit it to the dirty region
            region.andSelf(dirty);

            // screen is already cleared here
            if (!region.isEmpty()) {
#ifdef MTK_SF_DEBUG_SUPPORT
                // debug log
                if (CC_UNLIKELY(sPropertiesState.mLogRepaint)) {
                    String8 str;
                    region.dump(str, "");
                    ALOGD("@ (wormhole)\n%s", str.string());
                }
#endif
                // can happen with SurfaceView
                drawWormhole(displayDevice, region);
            }
        }

        if (displayDevice->getDisplayType() != DisplayDevice::DISPLAY_PRIMARY) {
            // just to be on the safe side, we don't set the
            // scissor on the main display. It should never be needed
            // anyways (though in theory it could since the API allows it).
            const Rect& bounds(displayDevice->getBounds());
            const Rect& scissor(displayDevice->getScissor());
            if (scissor != bounds) {
                // scissor doesn't match the screen's dimensions, so we
                // need to clear everything outside of it and enable
                // the GL scissor so we don't draw anything where we shouldn't

                // enable scissor for this frame
                const uint32_t height = displayDevice->getHeight();
                mRenderEngine->setScissor(scissor.left, height - scissor.bottom,
                        scissor.getWidth(), scissor.getHeight());
            }
        }
    }

    /*
     * and then, render the layers targeted at the framebuffer
     */

    ALOGV("Rendering client layers");
    const Transform& displayTransform = displayDevice->getTransform();
    if (hwcId >= 0) {
        // we're using h/w composer
        bool firstLayer = true;
        for (auto& layer : displayDevice->getVisibleLayersSortedByZ()) {
            const Region clip(dirty.intersect(
                    displayTransform.transform(layer->visibleRegion)));
            ALOGV("Layer: %s", layer->getName().string());
            ALOGV("  Composition type: %s",
                    to_string(layer->getCompositionType(hwcId)).c_str());
            if (!clip.isEmpty()) {
                switch (layer->getCompositionType(hwcId)) {
                    case HWC2::Composition::Cursor:
                    case HWC2::Composition::Device:
                    case HWC2::Composition::Sideband:
                    case HWC2::Composition::SolidColor: {
                        const Layer::State& state(layer->getDrawingState());
                        if (layer->getClearClientTarget(hwcId) && !firstLayer &&
                                layer->isOpaque(state) && (state.alpha == 1.0f)
                                && hasClientComposition) {
                            // never clear the very first layer since we're
                            // guaranteed the FB is already cleared
                            layer->clearWithOpenGL(displayDevice);
                        }
#ifdef MTK_SF_DEBUG_SUPPORT
                        // debug log
                        if (CC_UNLIKELY(sPropertiesState.mLogRepaint)) {
                            String8 str;
                            clip.dump(str, "");
                            ALOGD("hwc (%s\n%s)",
                                  layer->getName().string(), str.string());
                        }
#endif
                        break;
                    }
                    case HWC2::Composition::Client: {
                        layer->draw(displayDevice, clip);
#ifdef MTK_SF_DEBUG_SUPPORT
                        // debug log
                        if (CC_UNLIKELY(sPropertiesState.mLogRepaint)) {
                            String8 str;
                            clip.dump(str, "");
                            ALOGD("gles (%s\n%s)", layer->getName().string(), str.string());
                        }
#endif
                        break;
                    }
                    default:
                        break;
                }
            } else {
                ALOGV("  Skipping for empty clip");
            }
            firstLayer = false;
        }
    } else {
        // we're not using h/w composer
        for (auto& layer : displayDevice->getVisibleLayersSortedByZ()) {
            const Region clip(dirty.intersect(
                    displayTransform.transform(layer->visibleRegion)));
            if (!clip.isEmpty()) {
                layer->draw(displayDevice, clip);
#ifdef MTK_SF_DEBUG_SUPPORT
                // debug log
                if (CC_UNLIKELY(sPropertiesState.mLogRepaint)) {
                    String8 str;
                    clip.dump(str, "");
                    ALOGD("gles (%s\n%s)", layer->getName().string(), str.string());
                }
#endif
            }
        }
    }

    if (applyColorMatrix) {
        getRenderEngine().setupColorTransform(oldColorMatrix);
    }

    // disable scissor at the end of the frame
    mRenderEngine->disableScissor();
    return true;
}

void SurfaceFlinger::drawWormhole(const sp<const DisplayDevice>& displayDevice, const Region& region) const {
    const int32_t height = displayDevice->getHeight();
    RenderEngine& engine(getRenderEngine());
#ifdef MTK_SF_DEBUG_SUPPORT
    // set special color instead black for wormhole debug
    if (CC_UNLIKELY(sPropertiesState.mLineG3D))
        engine.fillRegionWithColor(region, height, 1, 0, 1, 1);
    else
#endif
    engine.fillRegionWithColor(region, height, 0, 0, 0, 0);
}

status_t SurfaceFlinger::addClientLayer(const sp<Client>& client,
        const sp<IBinder>& handle,
        const sp<IGraphicBufferProducer>& gbc,
        const sp<Layer>& lbc,
        const sp<Layer>& parent)
{
    // add this layer to the current state list
    {
        Mutex::Autolock _l(mStateLock);
        if (mNumLayers >= MAX_LAYERS) {
            ALOGE("AddClientLayer failed, mNumLayers (%zu) >= MAX_LAYERS (%zu)", mNumLayers,
                  MAX_LAYERS);
            return NO_MEMORY;
        }
        if (parent == nullptr) {
            mCurrentState.layersSortedByZ.add(lbc);
        } else {
            if (mCurrentState.layersSortedByZ.indexOf(parent) < 0) {
                ALOGE("addClientLayer called with a removed parent");
                return NAME_NOT_FOUND;
            }
            parent->addChild(lbc);
        }

        mGraphicBufferProducerList.add(IInterface::asBinder(gbc));
        mLayersAdded = true;
        mNumLayers++;
    }

    // attach this layer to the client
    client->attachLayer(handle, lbc);

    return NO_ERROR;
}

status_t SurfaceFlinger::removeLayer(const sp<Layer>& layer, bool topLevelOnly) {
    Mutex::Autolock _l(mStateLock);

    const auto& p = layer->getParent();
    ssize_t index;
    if (p != nullptr) {
        if (topLevelOnly) {
#ifdef MTK_SF_DEBUG_SUPPORT
            layer->setDestroyCalled(true);
#endif
            return NO_ERROR;
        }

        sp<Layer> ancestor = p;
        while (ancestor->getParent() != nullptr) {
            ancestor = ancestor->getParent();
        }
        if (mCurrentState.layersSortedByZ.indexOf(ancestor) < 0) {
            ALOGE("removeLayer called with a layer whose parent has been removed");
            return NAME_NOT_FOUND;
        }

        index = p->removeChild(layer);
    } else {
        index = mCurrentState.layersSortedByZ.remove(layer);
    }

    // As a matter of normal operation, the LayerCleaner will produce a second
    // attempt to remove the surface. The Layer will be kept alive in mDrawingState
    // so we will succeed in promoting it, but it's already been removed
    // from mCurrentState. As long as we can find it in mDrawingState we have no problem
    // otherwise something has gone wrong and we are leaking the layer.
    if (index < 0 && mDrawingState.layersSortedByZ.indexOf(layer) < 0) {
        ALOGE("Failed to find layer (%s) in layer parent (%s).",
                layer->getName().string(),
                (p != nullptr) ? p->getName().string() : "no-parent");
        return BAD_VALUE;
    } else if (index < 0) {
        return NO_ERROR;
    }

    layer->onRemovedFromCurrentState();
    mLayersPendingRemoval.add(layer);
    mLayersRemoved = true;
    mNumLayers -= 1 + layer->getChildrenCount();
    setTransactionFlags(eTransactionNeeded);
    return NO_ERROR;
}

uint32_t SurfaceFlinger::peekTransactionFlags() {
    return android_atomic_release_load(&mTransactionFlags);
}

uint32_t SurfaceFlinger::getTransactionFlags(uint32_t flags) {
    return android_atomic_and(~flags, &mTransactionFlags) & flags;
}

uint32_t SurfaceFlinger::setTransactionFlags(uint32_t flags) {
    uint32_t old = android_atomic_or(flags, &mTransactionFlags);
    if ((old & flags)==0) { // wake the server up
        signalTransaction();
    }
    return old;
}

void SurfaceFlinger::setTransactionState(
        const Vector<ComposerState>& state,
        const Vector<DisplayState>& displays,
        uint32_t flags)
{
    ATRACE_CALL();
    Mutex::Autolock _l(mStateLock);
    uint32_t transactionFlags = 0;

    if (flags & eAnimation) {
        // For window updates that are part of an animation we must wait for
        // previous animation "frames" to be handled.
        while (mAnimTransactionPending) {
            status_t err = mTransactionCV.waitRelative(mStateLock, s2ns(5));
            if (CC_UNLIKELY(err != NO_ERROR)) {
                // just in case something goes wrong in SF, return to the
                // caller after a few seconds.
                ALOGW_IF(err == TIMED_OUT, "setTransactionState timed out "
                        "waiting for previous animation frame");
                mAnimTransactionPending = false;
                break;
            }
        }
    }

    size_t count = displays.size();
    for (size_t i=0 ; i<count ; i++) {
        const DisplayState& s(displays[i]);
        transactionFlags |= setDisplayStateLocked(s);
    }

    count = state.size();
    for (size_t i=0 ; i<count ; i++) {
        const ComposerState& s(state[i]);
        // Here we need to check that the interface we're given is indeed
        // one of our own. A malicious client could give us a NULL
        // IInterface, or one of its own or even one of our own but a
        // different type. All these situations would cause us to crash.
        //
        // NOTE: it would be better to use RTTI as we could directly check
        // that we have a Client*. however, RTTI is disabled in Android.
        if (s.client != NULL) {
            sp<IBinder> binder = IInterface::asBinder(s.client);
            if (binder != NULL) {
                if (binder->queryLocalInterface(ISurfaceComposerClient::descriptor) != NULL) {
                    sp<Client> client( static_cast<Client *>(s.client.get()) );
                    transactionFlags |= setClientStateLocked(client, s.state);
                }
            }
        }
    }

    // If a synchronous transaction is explicitly requested without any changes, force a transaction
    // anyway. This can be used as a flush mechanism for previous async transactions.
    // Empty animation transaction can be used to simulate back-pressure, so also force a
    // transaction for empty animation transactions.
    if (transactionFlags == 0 &&
            ((flags & eSynchronous) || (flags & eAnimation))) {
        transactionFlags = eTransactionNeeded;
    }

    if (transactionFlags) {
        if (mInterceptor.isEnabled()) {
            mInterceptor.saveTransaction(state, mCurrentState.displays, displays, flags);
        }

        // this triggers the transaction
        setTransactionFlags(transactionFlags);

        // if this is a synchronous transaction, wait for it to take effect
        // before returning.
        if (flags & eSynchronous) {
            mTransactionPending = true;
        }
        if (flags & eAnimation) {
            mAnimTransactionPending = true;
        }
        while (mTransactionPending) {
            status_t err = mTransactionCV.waitRelative(mStateLock, s2ns(5));
            if (CC_UNLIKELY(err != NO_ERROR)) {
                // just in case something goes wrong in SF, return to the
                // called after a few seconds.
                ALOGW_IF(err == TIMED_OUT, "setTransactionState timed out!");
                mTransactionPending = false;
                break;
            }
        }
    }
}

uint32_t SurfaceFlinger::setDisplayStateLocked(const DisplayState& s)
{
    ssize_t dpyIdx = mCurrentState.displays.indexOfKey(s.token);
    if (dpyIdx < 0)
        return 0;

    uint32_t flags = 0;
    DisplayDeviceState& disp(mCurrentState.displays.editValueAt(dpyIdx));
    if (disp.isValid()) {
        const uint32_t what = s.what;
        if (what & DisplayState::eSurfaceChanged) {
            if (IInterface::asBinder(disp.surface) != IInterface::asBinder(s.surface)) {
                disp.surface = s.surface;
                flags |= eDisplayTransactionNeeded;
            }
        }
        if (what & DisplayState::eLayerStackChanged) {
            if (disp.layerStack != s.layerStack) {
                disp.layerStack = s.layerStack;
                flags |= eDisplayTransactionNeeded;
            }
        }
        if (what & DisplayState::eDisplayProjectionChanged) {
            if (disp.orientation != s.orientation) {
                disp.orientation = s.orientation;
                flags |= eDisplayTransactionNeeded;
            }
            if (disp.frame != s.frame) {
                disp.frame = s.frame;
                flags |= eDisplayTransactionNeeded;
            }
            if (disp.viewport != s.viewport) {
                disp.viewport = s.viewport;
                flags |= eDisplayTransactionNeeded;
            }
        }
        if (what & DisplayState::eDisplaySizeChanged) {
            if (disp.width != s.width) {
                disp.width = s.width;
                flags |= eDisplayTransactionNeeded;
            }
            if (disp.height != s.height) {
                disp.height = s.height;
                flags |= eDisplayTransactionNeeded;
            }
        }
    }
    return flags;
}

uint32_t SurfaceFlinger::setClientStateLocked(
        const sp<Client>& client,
        const layer_state_t& s)
{
    uint32_t flags = 0;
    sp<Layer> layer(client->getLayerUser(s.surface));
    if (layer != 0) {
        const uint32_t what = s.what;
        bool geometryAppliesWithResize =
                what & layer_state_t::eGeometryAppliesWithResize;
        if (what & layer_state_t::ePositionChanged) {
            if (layer->setPosition(s.x, s.y, !geometryAppliesWithResize)) {
                flags |= eTraversalNeeded;
            }
        }
        if (what & layer_state_t::eLayerChanged) {
            // NOTE: index needs to be calculated before we update the state
            const auto& p = layer->getParent();
            if (p == nullptr) {
                ssize_t idx = mCurrentState.layersSortedByZ.indexOf(layer);
                if (layer->setLayer(s.z) && idx >= 0) {
                    mCurrentState.layersSortedByZ.removeAt(idx);
                    mCurrentState.layersSortedByZ.add(layer);
                    // we need traversal (state changed)
                    // AND transaction (list changed)
                    flags |= eTransactionNeeded|eTraversalNeeded;
                }
            } else {
                if (p->setChildLayer(layer, s.z)) {
                    flags |= eTransactionNeeded|eTraversalNeeded;
                }
            }
        }
        if (what & layer_state_t::eRelativeLayerChanged) {
            if (layer->setRelativeLayer(s.relativeLayerHandle, s.z)) {
                flags |= eTransactionNeeded|eTraversalNeeded;
            }
        }
        if (what & layer_state_t::eSizeChanged) {
            if (layer->setSize(s.w, s.h)) {
                flags |= eTraversalNeeded;
            }
        }
        if (what & layer_state_t::eAlphaChanged) {
            if (layer->setAlpha(s.alpha))
                flags |= eTraversalNeeded;
        }
        if (what & layer_state_t::eMatrixChanged) {
            if (layer->setMatrix(s.matrix))
                flags |= eTraversalNeeded;
        }
        if (what & layer_state_t::eTransparentRegionChanged) {
            if (layer->setTransparentRegionHint(s.transparentRegion))
                flags |= eTraversalNeeded;
        }
        if (what & layer_state_t::eFlagsChanged) {
            if (layer->setFlags(s.flags, s.mask))
                flags |= eTraversalNeeded;
        }
        if (what & layer_state_t::eCropChanged) {
            if (layer->setCrop(s.crop, !geometryAppliesWithResize))
                flags |= eTraversalNeeded;
        }
        if (what & layer_state_t::eFinalCropChanged) {
            if (layer->setFinalCrop(s.finalCrop, !geometryAppliesWithResize))
                flags |= eTraversalNeeded;
        }
        if (what & layer_state_t::eLayerStackChanged) {
            ssize_t idx = mCurrentState.layersSortedByZ.indexOf(layer);
            // We only allow setting layer stacks for top level layers,
            // everything else inherits layer stack from its parent.
            if (layer->hasParent()) {
                ALOGE("Attempt to set layer stack on layer with parent (%s) is invalid",
                        layer->getName().string());
            } else if (idx < 0) {
                ALOGE("Attempt to set layer stack on layer without parent (%s) that "
                        "that also does not appear in the top level layer list. Something"
                        " has gone wrong.", layer->getName().string());
            } else if (layer->setLayerStack(s.layerStack)) {
                mCurrentState.layersSortedByZ.removeAt(idx);
                mCurrentState.layersSortedByZ.add(layer);
                // we need traversal (state changed)
                // AND transaction (list changed)
                flags |= eTransactionNeeded|eTraversalNeeded;
            }
        }
        if (what & layer_state_t::eDeferTransaction) {
            if (s.barrierHandle != nullptr) {
                layer->deferTransactionUntil(s.barrierHandle, s.frameNumber);
            } else if (s.barrierGbp != nullptr) {
                const sp<IGraphicBufferProducer>& gbp = s.barrierGbp;
                if (authenticateSurfaceTextureLocked(gbp)) {
                    const auto& otherLayer =
                        (static_cast<MonitoredProducer*>(gbp.get()))->getLayer();
                    layer->deferTransactionUntil(otherLayer, s.frameNumber);
                } else {
                    ALOGE("Attempt to defer transaction to to an"
                            " unrecognized GraphicBufferProducer");
                }
            }
            // We don't trigger a traversal here because if no other state is
            // changed, we don't want this to cause any more work
        }
        if (what & layer_state_t::eReparentChildren) {
            if (layer->reparentChildren(s.reparentHandle)) {
                flags |= eTransactionNeeded|eTraversalNeeded;
            }
        }
        if (what & layer_state_t::eDetachChildren) {
            layer->detachChildren();
        }
        if (what & layer_state_t::eOverrideScalingModeChanged) {
            layer->setOverrideScalingMode(s.overrideScalingMode);
            // We don't trigger a traversal here because if no other state is
            // changed, we don't want this to cause any more work
        }
    }
    return flags;
}

status_t SurfaceFlinger::createLayer(
        const String8& name,
        const sp<Client>& client,
        uint32_t w, uint32_t h, PixelFormat format, uint32_t flags,
        uint32_t windowType, uint32_t ownerUid, sp<IBinder>* handle,
        sp<IGraphicBufferProducer>* gbp, sp<Layer>* parent)
{
    if (int32_t(w|h) < 0) {
        ALOGE("createLayer() failed, w or h is negative (w=%d, h=%d)",
                int(w), int(h));
        return BAD_VALUE;
    }

    status_t result = NO_ERROR;

    sp<Layer> layer;

    String8 uniqueName = getUniqueLayerName(name);

    switch (flags & ISurfaceComposerClient::eFXSurfaceMask) {
        case ISurfaceComposerClient::eFXSurfaceNormal:
            result = createNormalLayer(client,
                    uniqueName, w, h, flags, format,
                    handle, gbp, &layer);
            break;
        case ISurfaceComposerClient::eFXSurfaceDim:
            result = createDimLayer(client,
                    uniqueName, w, h, flags,
                    handle, gbp, &layer);
            break;
        default:
            result = BAD_VALUE;
            break;
    }

    if (result != NO_ERROR) {
        return result;
    }

    // window type is WINDOW_TYPE_DONT_SCREENSHOT from SurfaceControl.java
    // TODO b/64227542
    if (windowType == 441731) {
        windowType = 2024; // TYPE_NAVIGATION_BAR_PANEL
        layer->setPrimaryDisplayOnly();
    }

    layer->setInfo(windowType, ownerUid);

    result = addClientLayer(client, *handle, *gbp, layer, *parent);
    if (result != NO_ERROR) {
        return result;
    }
    mInterceptor.saveSurfaceCreation(layer);

    setTransactionFlags(eTransactionNeeded);
    return result;
}

String8 SurfaceFlinger::getUniqueLayerName(const String8& name)
{
    bool matchFound = true;
    uint32_t dupeCounter = 0;

    // Tack on our counter whether there is a hit or not, so everyone gets a tag
    String8 uniqueName = name + "#" + String8(std::to_string(dupeCounter).c_str());

    // Loop over layers until we're sure there is no matching name
    while (matchFound) {
        matchFound = false;
        mDrawingState.traverseInZOrder([&](Layer* layer) {
            if (layer->getName() == uniqueName) {
                matchFound = true;
                uniqueName = name + "#" + String8(std::to_string(++dupeCounter).c_str());
            }
        });
    }

    ALOGD_IF(dupeCounter > 0, "duplicate layer name: changing %s to %s", name.c_str(), uniqueName.c_str());

    return uniqueName;
}

status_t SurfaceFlinger::createNormalLayer(const sp<Client>& client,
        const String8& name, uint32_t w, uint32_t h, uint32_t flags, PixelFormat& format,
        sp<IBinder>* handle, sp<IGraphicBufferProducer>* gbp, sp<Layer>* outLayer)
{
    // initialize the surfaces
    switch (format) {
    case PIXEL_FORMAT_TRANSPARENT:
    case PIXEL_FORMAT_TRANSLUCENT:
        format = PIXEL_FORMAT_RGBA_8888;
        break;
    case PIXEL_FORMAT_OPAQUE:
        format = PIXEL_FORMAT_RGBX_8888;
        break;
    }

    *outLayer = new Layer(this, client, name, w, h, flags);
    status_t err = (*outLayer)->setBuffers(w, h, format, flags);
    if (err == NO_ERROR) {
        *handle = (*outLayer)->getHandle();
        *gbp = (*outLayer)->getProducer();
    }

    ALOGE_IF(err, "createNormalLayer() failed (%s)", strerror(-err));
    return err;
}

status_t SurfaceFlinger::createDimLayer(const sp<Client>& client,
        const String8& name, uint32_t w, uint32_t h, uint32_t flags,
        sp<IBinder>* handle, sp<IGraphicBufferProducer>* gbp, sp<Layer>* outLayer)
{
    *outLayer = new LayerDim(this, client, name, w, h, flags);
    *handle = (*outLayer)->getHandle();
    *gbp = (*outLayer)->getProducer();
    return NO_ERROR;
}

status_t SurfaceFlinger::onLayerRemoved(const sp<Client>& client, const sp<IBinder>& handle)
{
    // called by a client when it wants to remove a Layer
    status_t err = NO_ERROR;
    sp<Layer> l(client->getLayerUser(handle));
    if (l != NULL) {
        mInterceptor.saveSurfaceDeletion(l);
        err = removeLayer(l);
        ALOGE_IF(err<0 && err != NAME_NOT_FOUND,
                "error removing layer=%p (%s)", l.get(), strerror(-err));
    }
    return err;
}

status_t SurfaceFlinger::onLayerDestroyed(const wp<Layer>& layer)
{
    // called by ~LayerCleaner() when all references to the IBinder (handle)
    // are gone
    sp<Layer> l = layer.promote();
    if (l == nullptr) {
        // The layer has already been removed, carry on
        return NO_ERROR;
    }
    // If we have a parent, then we can continue to live as long as it does.
    return removeLayer(l, true);
}

// ---------------------------------------------------------------------------

void SurfaceFlinger::onInitializeDisplays() {
    // reset screen orientation and use primary layer stack
    Vector<ComposerState> state;
    Vector<DisplayState> displays;
    DisplayState d;
    d.what = DisplayState::eDisplayProjectionChanged |
             DisplayState::eLayerStackChanged;
    d.token = mBuiltinDisplays[DisplayDevice::DISPLAY_PRIMARY];
    d.layerStack = 0;
    d.orientation = DisplayState::eOrientationDefault;
    d.frame.makeInvalid();
    d.viewport.makeInvalid();
    d.width = 0;
    d.height = 0;
    displays.add(d);
    setTransactionState(state, displays, 0);
    setPowerModeInternal(getDisplayDevice(d.token), HWC_POWER_MODE_NORMAL,
                         /*stateLockHeld*/ false);

    const auto& activeConfig = mHwc->getActiveConfig(HWC_DISPLAY_PRIMARY);
    const nsecs_t period = activeConfig->getVsyncPeriod();
    mAnimFrameTracker.setDisplayRefreshPeriod(period);

    // Use phase of 0 since phase is not known.
    // Use latency of 0, which will snap to the ideal latency.
    setCompositorTimingSnapped(0, period, 0);
}

void SurfaceFlinger::initializeDisplays() {
    class MessageScreenInitialized : public MessageBase {
        SurfaceFlinger* flinger;
    public:
        explicit MessageScreenInitialized(SurfaceFlinger* flinger) : flinger(flinger) { }
        virtual bool handler() {
            flinger->onInitializeDisplays();
            return true;
        }
    };
    sp<MessageBase> msg = new MessageScreenInitialized(this);
    postMessageAsync(msg);  // we may be called from main thread, use async message
}

void SurfaceFlinger::setPowerModeInternal(const sp<DisplayDevice>& hw,
             int mode, bool stateLockHeld) {
    ALOGD("Set power mode=%d, type=%d flinger=%p", mode, hw->getDisplayType(),
            this);
    int32_t type = hw->getDisplayType();
    int currentMode = hw->getPowerMode();

#ifdef MTK_SF_DEBUG_SUPPORT
    // tracking screen blank/unblank
    ATRACE_CALL();
#endif

    if (mode == currentMode) {
        return;
    }

    hw->setPowerMode(mode);
    if (type >= DisplayDevice::NUM_BUILTIN_DISPLAY_TYPES) {
        ALOGW("Trying to set power mode for virtual display");
        return;
    }

    if (mInterceptor.isEnabled()) {
        ConditionalLock lock(mStateLock, !stateLockHeld);
        ssize_t idx = mCurrentState.displays.indexOfKey(hw->getDisplayToken());
        if (idx < 0) {
            ALOGW("Surface Interceptor SavePowerMode: invalid display token");
            return;
        }
        mInterceptor.savePowerModeUpdate(mCurrentState.displays.valueAt(idx).displayId, mode);
    }

    if (currentMode == HWC_POWER_MODE_OFF) {
        // Turn on the display
        getHwComposer().setPowerMode(type, mode);
        if (type == DisplayDevice::DISPLAY_PRIMARY &&
            mode != HWC_POWER_MODE_DOZE_SUSPEND) {
            // FIXME: eventthread only knows about the main display right now
            mEventThread->onScreenAcquired();
            resyncToHardwareVsync(true);
#ifdef MTK_SF_WATCHDOG_SUPPORT
            if (mMessageWDT != nullptr) {
                mMessageWDT->setResume();
            }
#endif
        }

        mVisibleRegionsDirty = true;
        mHasPoweredOff = true;
        repaintEverythingLocked();

        struct sched_param param = {0};
        param.sched_priority = 1;
        if (sched_setscheduler(0, SCHED_FIFO, &param) != 0) {
            ALOGW("Couldn't set SCHED_FIFO on display on");
        }
    } else if (mode == HWC_POWER_MODE_OFF) {
        // Turn off the display
        struct sched_param param = {0};
        if (sched_setscheduler(0, SCHED_OTHER, &param) != 0) {
            ALOGW("Couldn't set SCHED_OTHER on display off");
        }

        if (type == DisplayDevice::DISPLAY_PRIMARY) {
            disableHardwareVsync(true); // also cancels any in-progress resync

            // FIXME: eventthread only knows about the main display right now
            mEventThread->onScreenReleased();
#ifdef MTK_SF_WATCHDOG_SUPPORT
            if (mMessageWDT != nullptr) {
                mMessageWDT->setSuspend();
            }
#endif
        }

        getHwComposer().setPowerMode(type, mode);
        mVisibleRegionsDirty = true;
        // from this point on, SF will stop drawing on this display
    } else if (mode == HWC_POWER_MODE_DOZE ||
               mode == HWC_POWER_MODE_NORMAL) {
        // Update display while dozing
        getHwComposer().setPowerMode(type, mode);
        if (type == DisplayDevice::DISPLAY_PRIMARY) {
            // FIXME: eventthread only knows about the main display right now
            mEventThread->onScreenAcquired();
            resyncToHardwareVsync(true);
        }
    } else if (mode == HWC_POWER_MODE_DOZE_SUSPEND) {
        // Leave display going to doze
        if (type == DisplayDevice::DISPLAY_PRIMARY) {
            disableHardwareVsync(true); // also cancels any in-progress resync
            // FIXME: eventthread only knows about the main display right now
            mEventThread->onScreenReleased();
        }
        getHwComposer().setPowerMode(type, mode);
    } else {
        ALOGE("Attempting to set unknown power mode: %d\n", mode);
        getHwComposer().setPowerMode(type, mode);
    }
}

void SurfaceFlinger::setPowerMode(const sp<IBinder>& display, int mode) {
    class MessageSetPowerMode: public MessageBase {
        SurfaceFlinger& mFlinger;
        sp<IBinder> mDisplay;
        int mMode;
    public:
        MessageSetPowerMode(SurfaceFlinger& flinger,
                const sp<IBinder>& disp, int mode) : mFlinger(flinger),
                    mDisplay(disp) { mMode = mode; }
        virtual bool handler() {
            sp<DisplayDevice> hw(mFlinger.getDisplayDevice(mDisplay));
            if (hw == NULL) {
                ALOGE("Attempt to set power mode = %d for null display %p",
                        mMode, mDisplay.get());
            } else if (hw->getDisplayType() >= DisplayDevice::DISPLAY_VIRTUAL) {
                ALOGW("Attempt to set power mode = %d for virtual display",
                        mMode);
            } else {
                mFlinger.setPowerModeInternal(
                        hw, mMode, /*stateLockHeld*/ false);
            }
            return true;
        }
    };
    sp<MessageBase> msg = new MessageSetPowerMode(*this, display, mode);
    postMessageSync(msg);
#ifdef MTK_SF_DEBUG_SUPPORT
    // notify IPO for black screen sync issue
    if (mode != HWC_POWER_MODE_OFF) {
        usleep(16667);
        property_set("sys.ipowin.done", "1");
    }
#endif
}

// ---------------------------------------------------------------------------

status_t SurfaceFlinger::dump(int fd, const Vector<String16>& args)
{
    String8 result;
    IPCThreadState* ipc = IPCThreadState::self();
    const int pid = ipc->getCallingPid();
    const int uid = ipc->getCallingUid();
    if ((uid != AID_SHELL) &&
            !PermissionCache::checkPermission(sDump, pid, uid)) {
        result.appendFormat("Permission Denial: "
                "can't dump SurfaceFlinger from pid=%d, uid=%d\n", pid, uid);
    } else {
        // Try to get the main lock, but give up after one second
        // (this would indicate SF is stuck, but we want to be able to
        // print something in dumpsys).
#ifdef MTK_SF_DEBUG_SUPPORT
        status_t err = mDumpLock.timedLock(s2ns(1));
        bool dumpLocked = (err == NO_ERROR);
        if (!dumpLocked) {
            result.appendFormat(
                    "SurfaceFlinger appears to be unresponsive (%s [%d]), "
                    "dumping anyways (no locks held)\n", strerror(-err), err);
        }
        err = mStateLock.timedLock(s2ns(1));
        nsecs_t start = systemTime(SYSTEM_TIME_MONOTONIC);
        ALOGD("start SF dump");
#else
        status_t err = mStateLock.timedLock(s2ns(1));
#endif

        bool locked = (err == NO_ERROR);
        if (!locked) {
            result.appendFormat(
                    "SurfaceFlinger appears to be unresponsive (%s [%d]), "
                    "dumping anyways (no locks held)\n", strerror(-err), err);
        }

        bool dumpAll = true;
        size_t index = 0;
        size_t numArgs = args.size();
        if (numArgs) {
            if ((index < numArgs) &&
                    (args[index] == String16("--list"))) {
                index++;
                listLayersLocked(args, index, result);
                dumpAll = false;
            }

            if ((index < numArgs) &&
                    (args[index] == String16("--latency"))) {
                index++;
                dumpStatsLocked(args, index, result);
                dumpAll = false;
            }

            if ((index < numArgs) &&
                    (args[index] == String16("--latency-clear"))) {
                index++;
                clearStatsLocked(args, index, result);
                dumpAll = false;
            }

            if ((index < numArgs) &&
                    (args[index] == String16("--dispsync"))) {
                index++;
                mPrimaryDispSync.dump(result);
                dumpAll = false;
            }

            if ((index < numArgs) &&
                    (args[index] == String16("--static-screen"))) {
                index++;
                dumpStaticScreenStats(result);
                dumpAll = false;
            }

            if ((index < numArgs) &&
                    (args[index] == String16("--frame-events"))) {
                index++;
                dumpFrameEventsLocked(result);
                dumpAll = false;
            }

            if ((index < numArgs) && (args[index] == String16("--wide-color"))) {
                index++;
                dumpWideColorInfo(result);
                dumpAll = false;
            }

#ifdef MTK_SF_DEBUG_SUPPORT
            if ((index < numArgs) &&
                    (args[index] == String16("--mtk"))) {
                index++;
                clearStatsLocked(args, index, result);
                // for run-time enable property
                setMTKProperties(result);
                dumpAll = false;
            }
#endif
        }

        if (dumpAll) {
            dumpAllLocked(args, index, result);
        }

        if (locked) {
            mStateLock.unlock();
        }
#ifdef MTK_SF_DEBUG_SUPPORT
        if (dumpLocked) {
            mDumpLock.unlock();
        }
        nsecs_t end = systemTime(SYSTEM_TIME_MONOTONIC);
        ALOGD("end SF dump [%" PRId64 "ns]", end - start);
#endif
    }
    write(fd, result.string(), result.size());
    return NO_ERROR;
}

void SurfaceFlinger::listLayersLocked(const Vector<String16>& /* args */,
        size_t& /* index */, String8& result) const
{
    mCurrentState.traverseInZOrder([&](Layer* layer) {
        result.appendFormat("%s\n", layer->getName().string());
    });
}

void SurfaceFlinger::dumpStatsLocked(const Vector<String16>& args, size_t& index,
        String8& result) const
{
    String8 name;
    if (index < args.size()) {
        name = String8(args[index]);
        index++;
    }

    const auto& activeConfig = mHwc->getActiveConfig(HWC_DISPLAY_PRIMARY);
    const nsecs_t period = activeConfig->getVsyncPeriod();
    result.appendFormat("%" PRId64 "\n", period);

    if (name.isEmpty()) {
        mAnimFrameTracker.dumpStats(result);
    } else {
        mCurrentState.traverseInZOrder([&](Layer* layer) {
            if (name == layer->getName()) {
                layer->dumpFrameStats(result);
            }
        });
    }
}

void SurfaceFlinger::clearStatsLocked(const Vector<String16>& args, size_t& index,
        String8& /* result */)
{
    String8 name;
    if (index < args.size()) {
        name = String8(args[index]);
        index++;
    }

    mCurrentState.traverseInZOrder([&](Layer* layer) {
        if (name.isEmpty() || (name == layer->getName())) {
            layer->clearFrameStats();
        }
    });

    mAnimFrameTracker.clearStats();
}

// This should only be called from the main thread.  Otherwise it would need
// the lock and should use mCurrentState rather than mDrawingState.
void SurfaceFlinger::logFrameStats() {
    mDrawingState.traverseInZOrder([&](Layer* layer) {
        layer->logFrameStats();
    });

    mAnimFrameTracker.logAndResetStats(String8("<win-anim>"));
}

void SurfaceFlinger::appendSfConfigString(String8& result) const
{
    result.append(" [sf");
    result.appendFormat(" HAS_CONTEXT_PRIORITY=%d", useContextPriority);

    if (isLayerTripleBufferingDisabled())
        result.append(" DISABLE_TRIPLE_BUFFERING");

    result.appendFormat(" PRESENT_TIME_OFFSET=%" PRId64 , dispSyncPresentTimeOffset);
    result.appendFormat(" FORCE_HWC_FOR_RBG_TO_YUV=%d", useHwcForRgbToYuv);
    result.appendFormat(" MAX_VIRT_DISPLAY_DIM=%" PRIu64, maxVirtualDisplaySize);
    result.appendFormat(" RUNNING_WITHOUT_SYNC_FRAMEWORK=%d", !hasSyncFramework);
    result.appendFormat(" NUM_FRAMEBUFFER_SURFACE_BUFFERS=%" PRId64,
                        maxFrameBufferAcquiredBuffers);
    result.append("]");
}

void SurfaceFlinger::dumpStaticScreenStats(String8& result) const
{
    result.appendFormat("Static screen stats:\n");
    for (size_t b = 0; b < NUM_BUCKETS - 1; ++b) {
        float bucketTimeSec = mFrameBuckets[b] / 1e9;
        float percent = 100.0f *
                static_cast<float>(mFrameBuckets[b]) / mTotalTime;
        result.appendFormat("  < %zd frames: %.3f s (%.1f%%)\n",
                b + 1, bucketTimeSec, percent);
    }
    float bucketTimeSec = mFrameBuckets[NUM_BUCKETS - 1] / 1e9;
    float percent = 100.0f *
            static_cast<float>(mFrameBuckets[NUM_BUCKETS - 1]) / mTotalTime;
    result.appendFormat("  %zd+ frames: %.3f s (%.1f%%)\n",
            NUM_BUCKETS - 1, bucketTimeSec, percent);
}

void SurfaceFlinger::recordBufferingStats(const char* layerName,
        std::vector<OccupancyTracker::Segment>&& history) {
    Mutex::Autolock lock(mBufferingStatsMutex);
    auto& stats = mBufferingStats[layerName];
    for (const auto& segment : history) {
        if (!segment.usedThirdBuffer) {
            stats.twoBufferTime += segment.totalTime;
        }
        if (segment.occupancyAverage < 1.0f) {
            stats.doubleBufferedTime += segment.totalTime;
        } else if (segment.occupancyAverage < 2.0f) {
            stats.tripleBufferedTime += segment.totalTime;
        }
        ++stats.numSegments;
        stats.totalTime += segment.totalTime;
    }
}

void SurfaceFlinger::dumpFrameEventsLocked(String8& result) {
    result.appendFormat("Layer frame timestamps:\n");

    const LayerVector& currentLayers = mCurrentState.layersSortedByZ;
    const size_t count = currentLayers.size();
    for (size_t i=0 ; i<count ; i++) {
        currentLayers[i]->dumpFrameEvents(result);
    }
}

void SurfaceFlinger::dumpBufferingStats(String8& result) const {
    result.append("Buffering stats:\n");
    result.append("  [Layer name] <Active time> <Two buffer> "
            "<Double buffered> <Triple buffered>\n");
    Mutex::Autolock lock(mBufferingStatsMutex);
    typedef std::tuple<std::string, float, float, float> BufferTuple;
    std::map<float, BufferTuple, std::greater<float>> sorted;
    for (const auto& statsPair : mBufferingStats) {
        const char* name = statsPair.first.c_str();
        const BufferingStats& stats = statsPair.second;
        if (stats.numSegments == 0) {
            continue;
        }
        float activeTime = ns2ms(stats.totalTime) / 1000.0f;
        float twoBufferRatio = static_cast<float>(stats.twoBufferTime) /
                stats.totalTime;
        float doubleBufferRatio = static_cast<float>(
                stats.doubleBufferedTime) / stats.totalTime;
        float tripleBufferRatio = static_cast<float>(
                stats.tripleBufferedTime) / stats.totalTime;
        sorted.insert({activeTime, {name, twoBufferRatio,
                doubleBufferRatio, tripleBufferRatio}});
    }
    for (const auto& sortedPair : sorted) {
        float activeTime = sortedPair.first;
        const BufferTuple& values = sortedPair.second;
        result.appendFormat("  [%s] %.2f %.3f %.3f %.3f\n",
                std::get<0>(values).c_str(), activeTime,
                std::get<1>(values), std::get<2>(values),
                std::get<3>(values));
    }
    result.append("\n");
}

void SurfaceFlinger::dumpWideColorInfo(String8& result) const {
    result.appendFormat("hasWideColorDisplay: %d\n", hasWideColorDisplay);
    result.appendFormat("forceNativeColorMode: %d\n", mForceNativeColorMode);

    // TODO: print out if wide-color mode is active or not

    for (size_t d = 0; d < mDisplays.size(); d++) {
        const sp<const DisplayDevice>& displayDevice(mDisplays[d]);
        int32_t hwcId = displayDevice->getHwcDisplayId();
        if (hwcId == DisplayDevice::DISPLAY_ID_INVALID) {
            continue;
        }

        result.appendFormat("Display %d color modes:\n", hwcId);
        std::vector<android_color_mode_t> modes = getHwComposer().getColorModes(hwcId);
        for (auto&& mode : modes) {
            result.appendFormat("    %s (%d)\n", decodeColorMode(mode).c_str(), mode);
        }

        android_color_mode_t currentMode = displayDevice->getActiveColorMode();
        result.appendFormat("    Current color mode: %s (%d)\n",
                            decodeColorMode(currentMode).c_str(), currentMode);
    }
    result.append("\n");
}

void SurfaceFlinger::dumpAllLocked(const Vector<String16>& args, size_t& index,
        String8& result) const
{
    bool colorize = false;
    if (index < args.size()
            && (args[index] == String16("--color"))) {
        colorize = true;
        index++;
    }

    Colorizer colorizer(colorize);

    // figure out if we're stuck somewhere
    const nsecs_t now = systemTime();
    const nsecs_t inSwapBuffers(mDebugInSwapBuffers);
    const nsecs_t inTransaction(mDebugInTransaction);
    nsecs_t inSwapBuffersDuration = (inSwapBuffers) ? now-inSwapBuffers : 0;
    nsecs_t inTransactionDuration = (inTransaction) ? now-inTransaction : 0;

    /*
     * Dump library configuration.
     */

    colorizer.bold(result);
    result.append("Build configuration:");
    colorizer.reset(result);
    appendSfConfigString(result);
    appendUiConfigString(result);
    appendGuiConfigString(result);
    result.append("\n");

    result.append("\nWide-Color information:\n");
    dumpWideColorInfo(result);

    colorizer.bold(result);
    result.append("Sync configuration: ");
    colorizer.reset(result);
    result.append(SyncFeatures::getInstance().toString());
    result.append("\n");

    const auto& activeConfig = mHwc->getActiveConfig(HWC_DISPLAY_PRIMARY);

    colorizer.bold(result);
    result.append("DispSync configuration: ");
    colorizer.reset(result);
    result.appendFormat("app phase %" PRId64 " ns, sf phase %" PRId64 " ns, "
            "present offset %" PRId64 " ns (refresh %" PRId64 " ns)",
        vsyncPhaseOffsetNs, sfVsyncPhaseOffsetNs,
        dispSyncPresentTimeOffset, activeConfig->getVsyncPeriod());
    result.append("\n");

    // Dump static screen stats
    result.append("\n");
    dumpStaticScreenStats(result);
    result.append("\n");

    dumpBufferingStats(result);

    /*
     * Dump the visible layer list
     */
    colorizer.bold(result);
    result.appendFormat("Visible layers (count = %zu)\n", mNumLayers);
    colorizer.reset(result);
#ifdef MTK_SF_DEBUG_SUPPORT
    mDrawingState.traverseInZOrder([&](Layer* layer) {
        layer->dump(result, colorizer);
    });
#else
    mCurrentState.traverseInZOrder([&](Layer* layer) {
        layer->dump(result, colorizer);
    });
#endif
    /*
     * Dump Display state
     */

    colorizer.bold(result);
    result.appendFormat("Displays (%zu entries)\n", mDisplays.size());
    colorizer.reset(result);
    for (size_t dpy=0 ; dpy<mDisplays.size() ; dpy++) {
        const sp<const DisplayDevice>& hw(mDisplays[dpy]);
        hw->dump(result);
    }

    /*
     * Dump SurfaceFlinger global state
     */

    colorizer.bold(result);
    result.append("SurfaceFlinger global state:\n");
    colorizer.reset(result);

    HWComposer& hwc(getHwComposer());
    sp<const DisplayDevice> hw(getDefaultDisplayDeviceLocked());

    colorizer.bold(result);
    result.appendFormat("EGL implementation : %s\n",
            eglQueryStringImplementationANDROID(mEGLDisplay, EGL_VERSION));
    colorizer.reset(result);
    result.appendFormat("%s\n",
            eglQueryStringImplementationANDROID(mEGLDisplay, EGL_EXTENSIONS));

    mRenderEngine->dump(result);

    hw->undefinedRegion.dump(result, "undefinedRegion");
    result.appendFormat("  orientation=%d, isDisplayOn=%d\n",
            hw->getOrientation(), hw->isDisplayOn());
    result.appendFormat(
            "  last eglSwapBuffers() time: %f us\n"
            "  last transaction time     : %f us\n"
            "  transaction-flags         : %08x\n"
            "  refresh-rate              : %f fps\n"
            "  x-dpi                     : %f\n"
            "  y-dpi                     : %f\n"
            "  gpu_to_cpu_unsupported    : %d\n"
            ,
            mLastSwapBufferTime/1000.0,
            mLastTransactionTime/1000.0,
            mTransactionFlags,
            1e9 / activeConfig->getVsyncPeriod(),
            activeConfig->getDpiX(),
            activeConfig->getDpiY(),
            !mGpuToCpuSupported);

    result.appendFormat("  eglSwapBuffers time: %f us\n",
            inSwapBuffersDuration/1000.0);

    result.appendFormat("  transaction time: %f us\n",
            inTransactionDuration/1000.0);

    /*
     * VSYNC state
     */
    mEventThread->dump(result);
    result.append("\n");

    /*
     * HWC layer minidump
     */
    for (size_t d = 0; d < mDisplays.size(); d++) {
        const sp<const DisplayDevice>& displayDevice(mDisplays[d]);
        int32_t hwcId = displayDevice->getHwcDisplayId();
        if (hwcId == DisplayDevice::DISPLAY_ID_INVALID) {
            continue;
        }

        result.appendFormat("Display %d HWC layers:\n", hwcId);
        Layer::miniDumpHeader(result);
        mCurrentState.traverseInZOrder([&](Layer* layer) {
            layer->miniDump(result, hwcId);
        });
        result.append("\n");
    }

    /*
     * Dump HWComposer state
     */
    colorizer.bold(result);
    result.append("h/w composer state:\n");
    colorizer.reset(result);
    bool hwcDisabled = mDebugDisableHWC || mDebugRegion;
    result.appendFormat("  h/w composer %s\n",
            hwcDisabled ? "disabled" : "enabled");
    hwc.dump(result);

    /*
     * Dump gralloc state
     */
    const GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());
    alloc.dump(result);

    /*
     * Dump VrFlinger state if in use.
     */
    if (mVrFlingerRequestsDisplay && mVrFlinger) {
        result.append("VrFlinger state:\n");
        result.append(mVrFlinger->Dump().c_str());
        result.append("\n");
    }
}

const Vector< sp<Layer> >&
SurfaceFlinger::getLayerSortedByZForHwcDisplay(int id) {
    // Note: mStateLock is held here
    wp<IBinder> dpy;
    for (size_t i=0 ; i<mDisplays.size() ; i++) {
        if (mDisplays.valueAt(i)->getHwcDisplayId() == id) {
            dpy = mDisplays.keyAt(i);
            break;
        }
    }
    if (dpy == NULL) {
        ALOGE("getLayerSortedByZForHwcDisplay: invalid hwc display id %d", id);
        // Just use the primary display so we have something to return
        dpy = getBuiltInDisplay(DisplayDevice::DISPLAY_PRIMARY);
    }
    return getDisplayDeviceLocked(dpy)->getVisibleLayersSortedByZ();
}

bool SurfaceFlinger::startDdmConnection()
{
    void* libddmconnection_dso =
            dlopen("libsurfaceflinger_ddmconnection.so", RTLD_NOW);
    if (!libddmconnection_dso) {
        return false;
    }
    void (*DdmConnection_start)(const char* name);
    DdmConnection_start =
            (decltype(DdmConnection_start))dlsym(libddmconnection_dso, "DdmConnection_start");
    if (!DdmConnection_start) {
        dlclose(libddmconnection_dso);
        return false;
    }
    (*DdmConnection_start)(getServiceName());
    return true;
}

status_t SurfaceFlinger::CheckTransactCodeCredentials(uint32_t code) {
    switch (code) {
        case CREATE_CONNECTION:
        case CREATE_DISPLAY:
        case BOOT_FINISHED:
        case CLEAR_ANIMATION_FRAME_STATS:
        case GET_ANIMATION_FRAME_STATS:
        case SET_POWER_MODE:
        case GET_HDR_CAPABILITIES:
        case ENABLE_VSYNC_INJECTIONS:
        case INJECT_VSYNC:
        {
            // codes that require permission check
            IPCThreadState* ipc = IPCThreadState::self();
            const int pid = ipc->getCallingPid();
            const int uid = ipc->getCallingUid();
            if ((uid != AID_GRAPHICS && uid != AID_SYSTEM) &&
                    !PermissionCache::checkPermission(sAccessSurfaceFlinger, pid, uid)) {
                ALOGE("Permission Denial: can't access SurfaceFlinger pid=%d, uid=%d", pid, uid);
                return PERMISSION_DENIED;
            }
            break;
        }
        /*
         * Calling setTransactionState is safe, because you need to have been
         * granted a reference to Client* and Handle* to do anything with it.
         *
         * Creating a scoped connection is safe, as per discussion in ISurfaceComposer.h
         */
        case SET_TRANSACTION_STATE:
        case CREATE_SCOPED_CONNECTION:
        {
            return OK;
        }
        case CAPTURE_SCREEN:
        {
            // codes that require permission check
            IPCThreadState* ipc = IPCThreadState::self();
            const int pid = ipc->getCallingPid();
            const int uid = ipc->getCallingUid();
            if ((uid != AID_GRAPHICS) &&
                    !PermissionCache::checkPermission(sReadFramebuffer, pid, uid)) {
                ALOGE("Permission Denial: can't read framebuffer pid=%d, uid=%d", pid, uid);
                return PERMISSION_DENIED;
            }
            break;
        }
    }
    return OK;
}

status_t SurfaceFlinger::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    status_t credentialCheck = CheckTransactCodeCredentials(code);
    if (credentialCheck != OK) {
        return credentialCheck;
    }

    status_t err = BnSurfaceComposer::onTransact(code, data, reply, flags);
    if (err == UNKNOWN_TRANSACTION || err == PERMISSION_DENIED) {
        CHECK_INTERFACE(ISurfaceComposer, data, reply);
        IPCThreadState* ipc = IPCThreadState::self();
        const int uid = ipc->getCallingUid();
        if (CC_UNLIKELY(uid != AID_SYSTEM
                && !PermissionCache::checkCallingPermission(sHardwareTest))) {
            const int pid = ipc->getCallingPid();
            ALOGE("Permission Denial: "
                    "can't access SurfaceFlinger pid=%d, uid=%d", pid, uid);
            return PERMISSION_DENIED;
        }
        int n;
        switch (code) {
            case 1000: // SHOW_CPU, NOT SUPPORTED ANYMORE
            case 1001: // SHOW_FPS, NOT SUPPORTED ANYMORE
                return NO_ERROR;
            case 1002:  // SHOW_UPDATES
                n = data.readInt32();
                mDebugRegion = n ? n : (mDebugRegion ? 0 : 1);
                invalidateHwcGeometry();
                repaintEverything();
                return NO_ERROR;
            case 1004:{ // repaint everything
                repaintEverything();
                return NO_ERROR;
            }
            case 1005:{ // force transaction
                Mutex::Autolock _l(mStateLock);
                setTransactionFlags(
                        eTransactionNeeded|
                        eDisplayTransactionNeeded|
                        eTraversalNeeded);
                return NO_ERROR;
            }
            case 1006:{ // send empty update
                signalRefresh();
                return NO_ERROR;
            }
            case 1008:  // toggle use of hw composer
                n = data.readInt32();
                mDebugDisableHWC = n ? 1 : 0;
                invalidateHwcGeometry();
                repaintEverything();
                return NO_ERROR;
            case 1009:  // toggle use of transform hint
                n = data.readInt32();
                mDebugDisableTransformHint = n ? 1 : 0;
                invalidateHwcGeometry();
                repaintEverything();
                return NO_ERROR;
            case 1010:  // interrogate.
                reply->writeInt32(0);
                reply->writeInt32(0);
                reply->writeInt32(mDebugRegion);
                reply->writeInt32(0);
                reply->writeInt32(mDebugDisableHWC);
                return NO_ERROR;
            case 1013: {
                sp<const DisplayDevice> hw(getDefaultDisplayDevice());
                reply->writeInt32(hw->getPageFlipCount());
                return NO_ERROR;
            }
            case 1014: {
                // daltonize
                n = data.readInt32();
                switch (n % 10) {
                    case 1:
                        mDaltonizer.setType(ColorBlindnessType::Protanomaly);
                        break;
                    case 2:
                        mDaltonizer.setType(ColorBlindnessType::Deuteranomaly);
                        break;
                    case 3:
                        mDaltonizer.setType(ColorBlindnessType::Tritanomaly);
                        break;
                    default:
                        mDaltonizer.setType(ColorBlindnessType::None);
                        break;
                }
                if (n >= 10) {
                    mDaltonizer.setMode(ColorBlindnessMode::Correction);
                } else {
                    mDaltonizer.setMode(ColorBlindnessMode::Simulation);
                }
                invalidateHwcGeometry();
                repaintEverything();
                return NO_ERROR;
            }
            case 1015: {
                // apply a color matrix
                n = data.readInt32();
                if (n) {
                    // color matrix is sent as a column-major mat4 matrix
                    for (size_t i = 0 ; i < 4; i++) {
                        for (size_t j = 0; j < 4; j++) {
                            mColorMatrix[i][j] = data.readFloat();
                        }
                    }
                } else {
                    mColorMatrix = mat4();
                }

                // Check that supplied matrix's last row is {0,0,0,1} so we can avoid
                // the division by w in the fragment shader
                float4 lastRow(transpose(mColorMatrix)[3]);
                if (any(greaterThan(abs(lastRow - float4{0, 0, 0, 1}), float4{1e-4f}))) {
                    ALOGE("The color transform's last row must be (0, 0, 0, 1)");
                }

                invalidateHwcGeometry();
                repaintEverything();
                return NO_ERROR;
            }
            // This is an experimental interface
            // Needs to be shifted to proper binder interface when we productize
            case 1016: {
                n = data.readInt32();
                mPrimaryDispSync.setRefreshSkipCount(n);
                return NO_ERROR;
            }
            case 1017: {
                n = data.readInt32();
                mForceFullDamage = static_cast<bool>(n);
                return NO_ERROR;
            }
            case 1018: { // Modify Choreographer's phase offset
                n = data.readInt32();
                mEventThread->setPhaseOffset(static_cast<nsecs_t>(n));
                return NO_ERROR;
            }
            case 1019: { // Modify SurfaceFlinger's phase offset
                n = data.readInt32();
                mSFEventThread->setPhaseOffset(static_cast<nsecs_t>(n));
                return NO_ERROR;
            }
            case 1020: { // Layer updates interceptor
                n = data.readInt32();
                if (n) {
                    ALOGV("Interceptor enabled");
                    mInterceptor.enable(mDrawingState.layersSortedByZ, mDrawingState.displays);
                }
                else{
                    ALOGV("Interceptor disabled");
                    mInterceptor.disable();
                }
                return NO_ERROR;
            }
            case 1021: { // Disable HWC virtual displays
                n = data.readInt32();
                mUseHwcVirtualDisplays = !n;
                return NO_ERROR;
            }
            case 1022: { // Set saturation boost
                mSaturation = std::max(0.0f, std::min(data.readFloat(), 2.0f));

                invalidateHwcGeometry();
                repaintEverything();
                return NO_ERROR;
            }
            case 1023: { // Set native mode
                mForceNativeColorMode = data.readInt32() == 1;

                invalidateHwcGeometry();
                repaintEverything();
                return NO_ERROR;
            }
            case 1024: { // Is wide color gamut rendering/color management supported?
                reply->writeBool(hasWideColorDisplay);
                return NO_ERROR;
            }
        }
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
        err = onMtkTransact(code, data , reply, flags);
#endif
    }
    return err;
}

void SurfaceFlinger::repaintEverythingLocked() {
    android_atomic_or(1, &mRepaintEverything);
    signalTransaction();
}

void SurfaceFlinger::repaintEverything() {
    ConditionalLock _l(mStateLock,
            std::this_thread::get_id() != mMainThreadId);
    repaintEverythingLocked();
}

// Checks that the requested width and height are valid and updates them to the display dimensions
// if they are set to 0
static status_t updateDimensionsLocked(const sp<const DisplayDevice>& displayDevice,
                                       Transform::orientation_flags rotation,
                                       uint32_t* requestedWidth, uint32_t* requestedHeight) {
    // get screen geometry
    uint32_t displayWidth = displayDevice->getWidth();
    uint32_t displayHeight = displayDevice->getHeight();

    if (rotation & Transform::ROT_90) {
        std::swap(displayWidth, displayHeight);
    }

    if ((*requestedWidth > displayWidth) || (*requestedHeight > displayHeight)) {
        ALOGE("size mismatch (%d, %d) > (%d, %d)",
                *requestedWidth, *requestedHeight, displayWidth, displayHeight);
        return BAD_VALUE;
    }

    if (*requestedWidth == 0) {
        *requestedWidth = displayWidth;
    }
    if (*requestedHeight == 0) {
        *requestedHeight = displayHeight;
    }

    return NO_ERROR;
}

// A simple RAII class to disconnect from an ANativeWindow* when it goes out of scope
class WindowDisconnector {
public:
    WindowDisconnector(ANativeWindow* window, int api) : mWindow(window), mApi(api) {}
    ~WindowDisconnector() {
        native_window_api_disconnect(mWindow, mApi);
    }

private:
    ANativeWindow* mWindow;
    const int mApi;
};

static status_t getWindowBuffer(ANativeWindow* window, uint32_t requestedWidth,
                                uint32_t requestedHeight, bool hasWideColorDisplay,
                                bool renderEngineUsesWideColor, ANativeWindowBuffer** outBuffer) {
    const uint32_t usage = GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN |
            GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_TEXTURE;

    int err = 0;
    err = native_window_set_buffers_dimensions(window, requestedWidth, requestedHeight);
    err |= native_window_set_scaling_mode(window, NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);
    err |= native_window_set_buffers_format(window, HAL_PIXEL_FORMAT_RGBA_8888);
    err |= native_window_set_usage(window, usage);

    if (hasWideColorDisplay) {
        err |= native_window_set_buffers_data_space(window,
                                                    renderEngineUsesWideColor
                                                            ? HAL_DATASPACE_DISPLAY_P3
                                                            : HAL_DATASPACE_V0_SRGB);
    }

    if (err != NO_ERROR) {
        return BAD_VALUE;
    }

    /* TODO: Once we have the sync framework everywhere this can use
     * server-side waits on the fence that dequeueBuffer returns.
     */
    err = native_window_dequeue_buffer_and_wait(window, outBuffer);
    if (err != NO_ERROR) {
        return err;
    }

    return NO_ERROR;
}

status_t SurfaceFlinger::captureScreen(const sp<IBinder>& display,
        const sp<IGraphicBufferProducer>& producer,
        Rect sourceCrop, uint32_t reqWidth, uint32_t reqHeight,
        int32_t minLayerZ, int32_t maxLayerZ,
        bool useIdentityTransform, ISurfaceComposer::Rotation rotation) {
#ifdef MTK_SF_DEBUG_SUPPORT
    ATRACE_CALL();
#endif
    if (CC_UNLIKELY(display == 0))
        return BAD_VALUE;

    if (CC_UNLIKELY(producer == 0))
        return BAD_VALUE;

    // if we have secure windows on this display, never allow the screen capture
    // unless the producer interface is local (i.e.: we can take a screenshot for
    // ourselves).
    bool isLocalScreenshot = IInterface::asBinder(producer)->localBinder();

    // Convert to surfaceflinger's internal rotation type.
    Transform::orientation_flags rotationFlags;
    switch (rotation) {
        case ISurfaceComposer::eRotateNone:
            rotationFlags = Transform::ROT_0;
            break;
        case ISurfaceComposer::eRotate90:
            rotationFlags = Transform::ROT_90;
            break;
        case ISurfaceComposer::eRotate180:
            rotationFlags = Transform::ROT_180;
            break;
        case ISurfaceComposer::eRotate270:
            rotationFlags = Transform::ROT_270;
            break;
        default:
            rotationFlags = Transform::ROT_0;
            ALOGE("Invalid rotation passed to captureScreen(): %d\n", rotation);
            break;
    }

    { // Autolock scope
        Mutex::Autolock lock(mStateLock);
        sp<const DisplayDevice> displayDevice(getDisplayDeviceLocked(display));
#ifdef MTK_SF_HW_ROTATION_SUPPORT
        if (mCorrectHwRotation != nullptr) {
            uint32_t rot = (uint32_t)rotation;
            status_t ret = mCorrectHwRotation->correctCaptureSize(&reqWidth, &reqHeight,
                    displayDevice->getWidth(), displayDevice->getHeight(), rot);
            if (ret != NO_ERROR)
                return ret;
        } else {
            updateDimensionsLocked(displayDevice, rotationFlags, &reqWidth, &reqHeight);
        }
#else
        updateDimensionsLocked(displayDevice, rotationFlags, &reqWidth, &reqHeight);
#endif
    }

    // create a surface (because we're a producer, and we need to
    // dequeue/queue a buffer)
    sp<Surface> surface = new Surface(producer, false);

    // Put the screenshot Surface into async mode so that
    // Layer::headFenceHasSignaled will always return true and we'll latch the
    // first buffer regardless of whether or not its acquire fence has
    // signaled. This is needed to avoid a race condition in the rotation
    // animation. See b/30209608
    surface->setAsyncMode(true);

    ANativeWindow* window = surface.get();

    status_t result = native_window_api_connect(window, NATIVE_WINDOW_API_EGL);
    if (result != NO_ERROR) {
        return result;
    }
    WindowDisconnector disconnector(window, NATIVE_WINDOW_API_EGL);

    ANativeWindowBuffer* buffer = nullptr;
    result = getWindowBuffer(window, reqWidth, reqHeight,
            hasWideColorDisplay && !mForceNativeColorMode,
            getRenderEngine().usesWideColor(), &buffer);
    if (result != NO_ERROR) {
        return result;
    }

    // This mutex protects syncFd and captureResult for communication of the return values from the
    // main thread back to this Binder thread
    std::mutex captureMutex;
    std::condition_variable captureCondition;
    std::unique_lock<std::mutex> captureLock(captureMutex);
    int syncFd = -1;
    std::optional<status_t> captureResult;

    sp<LambdaMessage> message = new LambdaMessage([&]() {
        // If there is a refresh pending, bug out early and tell the binder thread to try again
        // after the refresh.
        if (mRefreshPending) {
            ATRACE_NAME("Skipping screenshot for now");
            std::unique_lock<std::mutex> captureLock(captureMutex);
            captureResult = std::make_optional<status_t>(EAGAIN);
            captureCondition.notify_one();
            return;
        }

        status_t result = NO_ERROR;
        int fd = -1;
        {
            Mutex::Autolock _l(mStateLock);
            sp<const DisplayDevice> device(getDisplayDeviceLocked(display));
#ifdef MTK_AOSP_DISPLAY_BUGFIX
            if (device == nullptr)
            {
                std::unique_lock<std::mutex> captureLock(captureMutex);
                captureResult = std::make_optional<status_t>(BAD_VALUE);
                captureCondition.notify_one();
                return;
            }
#endif
            result = captureScreenImplLocked(device, buffer, sourceCrop, reqWidth, reqHeight,
                                             minLayerZ, maxLayerZ, useIdentityTransform,
                                             rotationFlags, isLocalScreenshot, &fd);
        }

        {
            std::unique_lock<std::mutex> captureLock(captureMutex);
            syncFd = fd;
            captureResult = std::make_optional<status_t>(result);
            captureCondition.notify_one();
        }
    });

    result = postMessageAsync(message);
    if (result == NO_ERROR) {
        captureCondition.wait(captureLock, [&]() { return captureResult; });
        while (*captureResult == EAGAIN) {
            captureResult.reset();
            result = postMessageAsync(message);
            if (result != NO_ERROR) {
                return result;
            }
            captureCondition.wait(captureLock, [&]() { return captureResult; });
        }
        result = *captureResult;
    }

#ifdef MTK_SF_DEBUG_SUPPORT
    // dump screenshot by SF into file
    if (CC_UNLIKELY(sPropertiesState.mDumpScreenShot > 0)) {
        String8 s = String8::format("captureScreen_%08d", sPropertiesState.mDumpScreenShot);
        if (-1 != syncFd) {
            sp<Fence> fence(new Fence(dup(syncFd)));
            fence->waitForever(s.string());
        }
        if (buffer != nullptr)
        {
            if (buffer->handle != nullptr)
            {
                getGraphicBufferUtil().dump(buffer->handle, s.string(), "/data/SF_dump");
            }
        }
        sPropertiesState.mDumpScreenShot++;
    }
#endif

    if (result == NO_ERROR) {
        // queueBuffer takes ownership of syncFd
        result = window->queueBuffer(window, buffer, syncFd);
    }

    return result;
}


void SurfaceFlinger::renderScreenImplLocked(
        const sp<const DisplayDevice>& hw,
        Rect sourceCrop, uint32_t reqWidth, uint32_t reqHeight,
        int32_t minLayerZ, int32_t maxLayerZ,
        bool yswap, bool useIdentityTransform, Transform::orientation_flags rotation)
{
    ATRACE_CALL();
    RenderEngine& engine(getRenderEngine());

    // get screen geometry
    const int32_t hw_w = hw->getWidth();
    const int32_t hw_h = hw->getHeight();
    const bool filtering = static_cast<int32_t>(reqWidth) != hw_w ||
                           static_cast<int32_t>(reqHeight) != hw_h;

    // if a default or invalid sourceCrop is passed in, set reasonable values
    if (sourceCrop.width() == 0 || sourceCrop.height() == 0 ||
            !sourceCrop.isValid()) {
        sourceCrop.setLeftTop(Point(0, 0));
        sourceCrop.setRightBottom(Point(hw_w, hw_h));
    }
#ifdef MTK_SF_HW_ROTATION_SUPPORT
    else if (mCorrectHwRotation != nullptr) {
        if (hw->getDisplayType() == DisplayDevice::DISPLAY_PRIMARY) {
            Transform tr;
            uint32_t flags = 0x00;
            mCorrectHwRotation->getCropFlags(&flags);
            tr.set(flags, hw->getWidth(), hw->getHeight());
            sourceCrop = tr.transform(sourceCrop);
        }
    }
#endif

    // ensure that sourceCrop is inside screen
    if (sourceCrop.left < 0) {
        ALOGE("Invalid crop rect: l = %d (< 0)", sourceCrop.left);
    }
    if (sourceCrop.right > hw_w) {
        ALOGE("Invalid crop rect: r = %d (> %d)", sourceCrop.right, hw_w);
    }
    if (sourceCrop.top < 0) {
        ALOGE("Invalid crop rect: t = %d (< 0)", sourceCrop.top);
    }
    if (sourceCrop.bottom > hw_h) {
        ALOGE("Invalid crop rect: b = %d (> %d)", sourceCrop.bottom, hw_h);
    }

#ifdef USE_HWC2
     engine.setWideColor(hw->getWideColorSupport() && !mForceNativeColorMode);
     engine.setColorMode(mForceNativeColorMode ? HAL_COLOR_MODE_NATIVE : hw->getActiveColorMode());
#endif

    // make sure to clear all GL error flags
    engine.checkErrors();

#ifdef MTK_SF_HW_ROTATION_SUPPORT
    if (mCorrectHwRotation != nullptr) {
        if (hw->getDisplayType() == DisplayDevice::DISPLAY_PRIMARY) {
            uint32_t rot = (uint32_t)rotation;
            mCorrectHwRotation->correctRotation(&rot);
            rotation = static_cast<Transform::orientation_flags>(rot);
        }
    }
#endif

    // set-up our viewport
    engine.setViewportAndProjection(
        reqWidth, reqHeight, sourceCrop, hw_h, yswap, rotation);
    engine.disableTexturing();

    // redraw the screen entirely...
    engine.clearWithColor(0, 0, 0, 1);

    // We loop through the first level of layers without traversing,
    // as we need to interpret min/max layer Z in the top level Z space.
    for (const auto& layer : mDrawingState.layersSortedByZ) {
        if (!layer->belongsToDisplay(hw->getLayerStack(), false)) {
            continue;
        }
        const Layer::State& state(layer->getDrawingState());
        if (state.z < minLayerZ || state.z > maxLayerZ) {
            continue;
        }
        layer->traverseInZOrder(LayerVector::StateSet::Drawing, [&](Layer* layer) {
            if (!layer->isVisible()) {
                return;
            }
            if (filtering) layer->setFiltering(true);
            layer->draw(hw, useIdentityTransform);
#ifdef MTK_SF_DEBUG_SUPPORT
            ALOGI("screenshot (%s)\n", layer->getName().string());
#endif
            if (filtering) layer->setFiltering(false);
        });
    }

#ifdef MTK_SF_DEBUG_SUPPORT
    // log info of screenshot making of
    if (CC_UNLIKELY(sPropertiesState.mDumpScreenShot > 0)) {
        String8 s;
        for (const auto& layer : mDrawingState.layersSortedByZ) {
            const Layer::State& state(layer->getDrawingState());
            s.appendFormat("    * name=%s, layerStack=%d, z=%d, visible=%d, flags=%x, alpha=%f\n",
                    layer->getName().string(), state.layerStack, state.z, layer->isVisible(), state.flags, state.alpha);
        }
        ALOGI("[renderScreenImplLocked] count=%d, minLayerZ=%u, maxLayerZ=%u +\n%s[renderScreenImplLocked] -",
                sPropertiesState.mDumpScreenShot, minLayerZ, maxLayerZ, s.string());

        // debug line that indicates drawing screenshot
        if (CC_UNLIKELY(sPropertiesState.mLineSS))
            engine.drawDebugLine(hw, 0xFFFFFF00);
    }
#endif
    hw->setViewportAndProjection();
}

// A simple RAII class that holds an EGLImage and destroys it either:
//   a) When the destroy() method is called
//   b) When the object goes out of scope
class ImageHolder {
public:
    ImageHolder(EGLDisplay display, EGLImageKHR image) : mDisplay(display), mImage(image) {}
    ~ImageHolder() { destroy(); }

    void destroy() {
        if (mImage != EGL_NO_IMAGE_KHR) {
            eglDestroyImageKHR(mDisplay, mImage);
            mImage = EGL_NO_IMAGE_KHR;
        }
    }

private:
    const EGLDisplay mDisplay;
    EGLImageKHR mImage;
};

status_t SurfaceFlinger::captureScreenImplLocked(const sp<const DisplayDevice>& hw,
                                                 ANativeWindowBuffer* buffer, Rect sourceCrop,
                                                 uint32_t reqWidth, uint32_t reqHeight,
                                                 int32_t minLayerZ, int32_t maxLayerZ,
                                                 bool useIdentityTransform,
                                                 Transform::orientation_flags rotation,
                                                 bool isLocalScreenshot, int* outSyncFd) {
    ATRACE_CALL();

    bool secureLayerIsVisible = false;
    for (const auto& layer : mDrawingState.layersSortedByZ) {
        const Layer::State& state(layer->getDrawingState());
        if (!layer->belongsToDisplay(hw->getLayerStack(), false) ||
                (state.z < minLayerZ || state.z > maxLayerZ)) {
            continue;
        }
        layer->traverseInZOrder(LayerVector::StateSet::Drawing, [&](Layer *layer) {
            secureLayerIsVisible = secureLayerIsVisible || (layer->isVisible() &&
                    layer->isSecure());
        });
    }

    if (!isLocalScreenshot && secureLayerIsVisible) {
        ALOGW("FB is protected: PERMISSION_DENIED");
        return PERMISSION_DENIED;
    }

    int syncFd = -1;
    // create an EGLImage from the buffer so we can later
    // turn it into a texture
    EGLImageKHR image = eglCreateImageKHR(mEGLDisplay, EGL_NO_CONTEXT,
            EGL_NATIVE_BUFFER_ANDROID, buffer, NULL);
    if (image == EGL_NO_IMAGE_KHR) {
        return BAD_VALUE;
    }

    // This will automatically destroy the image if we return before calling its destroy method
    ImageHolder imageHolder(mEGLDisplay, image);

    // this binds the given EGLImage as a framebuffer for the
    // duration of this scope.
    RenderEngine::BindImageAsFramebuffer imageBond(getRenderEngine(), image);
    if (imageBond.getStatus() != NO_ERROR) {
        ALOGE("got GL_FRAMEBUFFER_COMPLETE_OES error while taking screenshot");
        return INVALID_OPERATION;
    }

    // this will in fact render into our dequeued buffer
    // via an FBO, which means we didn't have to create
    // an EGLSurface and therefore we're not
    // dependent on the context's EGLConfig.
    renderScreenImplLocked(
        hw, sourceCrop, reqWidth, reqHeight, minLayerZ, maxLayerZ, true,
        useIdentityTransform, rotation);

    // Attempt to create a sync khr object that can produce a sync point. If that
    // isn't available, create a non-dupable sync object in the fallback path and
    // wait on it directly.
    EGLSyncKHR sync = EGL_NO_SYNC_KHR;
    if (!DEBUG_SCREENSHOTS) {
       sync = eglCreateSyncKHR(mEGLDisplay, EGL_SYNC_NATIVE_FENCE_ANDROID, NULL);
       // native fence fd will not be populated until flush() is done.
       getRenderEngine().flush();
    }

    if (sync != EGL_NO_SYNC_KHR) {
        // get the sync fd
        syncFd = eglDupNativeFenceFDANDROID(mEGLDisplay, sync);
        if (syncFd == EGL_NO_NATIVE_FENCE_FD_ANDROID) {
            ALOGW("captureScreen: failed to dup sync khr object");
            syncFd = -1;
        }
        eglDestroySyncKHR(mEGLDisplay, sync);
    } else {
        // fallback path
        sync = eglCreateSyncKHR(mEGLDisplay, EGL_SYNC_FENCE_KHR, NULL);
        if (sync != EGL_NO_SYNC_KHR) {
            EGLint result = eglClientWaitSyncKHR(mEGLDisplay, sync,
                EGL_SYNC_FLUSH_COMMANDS_BIT_KHR, 2000000000 /*2 sec*/);
            EGLint eglErr = eglGetError();
            if (result == EGL_TIMEOUT_EXPIRED_KHR) {
                ALOGW("captureScreen: fence wait timed out");
            } else {
                ALOGW_IF(eglErr != EGL_SUCCESS,
                        "captureScreen: error waiting on EGL fence: %#x", eglErr);
            }
            eglDestroySyncKHR(mEGLDisplay, sync);
        } else {
            ALOGW("captureScreen: error creating EGL fence: %#x", eglGetError());
        }
    }
    *outSyncFd = syncFd;

    if (DEBUG_SCREENSHOTS) {
        uint32_t* pixels = new uint32_t[reqWidth*reqHeight];
        getRenderEngine().readPixels(0, 0, reqWidth, reqHeight, pixels);
        checkScreenshot(reqWidth, reqHeight, reqWidth, pixels,
                hw, minLayerZ, maxLayerZ);
        delete [] pixels;
    }

    // destroy our image
    imageHolder.destroy();

    return NO_ERROR;
}

void SurfaceFlinger::checkScreenshot(size_t w, size_t s, size_t h, void const* vaddr,
        const sp<const DisplayDevice>& hw, int32_t minLayerZ, int32_t maxLayerZ) {
    if (DEBUG_SCREENSHOTS) {
        for (size_t y=0 ; y<h ; y++) {
            uint32_t const * p = (uint32_t const *)vaddr + y*s;
            for (size_t x=0 ; x<w ; x++) {
                if (p[x] != 0xFF000000) return;
            }
        }
        ALOGE("*** we just took a black screenshot ***\n"
                "requested minz=%d, maxz=%d, layerStack=%d",
                minLayerZ, maxLayerZ, hw->getLayerStack());

        size_t i = 0;
        for (const auto& layer : mDrawingState.layersSortedByZ) {
            const Layer::State& state(layer->getDrawingState());
            if (layer->belongsToDisplay(hw->getLayerStack(), false) && state.z >= minLayerZ &&
                    state.z <= maxLayerZ) {
                layer->traverseInZOrder(LayerVector::StateSet::Drawing, [&](Layer* layer) {
                    ALOGE("%c index=%zu, name=%s, layerStack=%d, z=%d, visible=%d, flags=%x, alpha=%.3f",
                            layer->isVisible() ? '+' : '-',
                            i, layer->getName().string(), layer->getLayerStack(), state.z,
                            layer->isVisible(), state.flags, state.alpha);
                    i++;
                });
            }
        }
    }
}

// ---------------------------------------------------------------------------

void SurfaceFlinger::State::traverseInZOrder(const LayerVector::Visitor& visitor) const {
    layersSortedByZ.traverseInZOrder(stateSet, visitor);
}

void SurfaceFlinger::State::traverseInReverseZOrder(const LayerVector::Visitor& visitor) const {
    layersSortedByZ.traverseInReverseZOrder(stateSet, visitor);
}

}; // namespace android


#if defined(__gl_h_)
#error "don't include gl/gl.h in this file"
#endif

#if defined(__gl2_h_)
#error "don't include gl2/gl2.h in this file"
#endif
