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
#ifndef VIRTUAL_DEVICE_H
#define VIRTUAL_DEVICE_H

#include <IDisplayDevice.h>
#include <SimpleThread.h>
#include <IVideoPayloadManager.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>
#include <utils/Vector.h>
#include <utils/List.h>
#ifdef INTEL_WIDI
#include "IFrameServer.h"
#endif
#include <va/va.h>
#include <va/va_vpp.h>

namespace android {
namespace intel {

class Hwcomposer;
class DisplayPlaneManager;
class IVideoPayloadManager;
class SoftVsyncObserver;

#ifdef INTEL_WIDI
class VirtualDevice : public IDisplayDevice, public BnFrameServer {
#else
class VirtualDevice : public IDisplayDevice, public RefBase{
#endif
protected:
    class VAMappedHandle;
    class VAMappedHandleObject;
    struct CachedBuffer : public android::RefBase {
        CachedBuffer(BufferManager *mgr, buffer_handle_t handle);
        ~CachedBuffer();
        BufferManager *manager;
        BufferMapper *mapper;
        VAMappedHandle *vaMappedHandle;
        buffer_handle_t cachedKhandle;
    };
    struct HeldDecoderBuffer : public android::RefBase {
        HeldDecoderBuffer(const sp<VirtualDevice>& vd, const android::sp<CachedBuffer>& cachedBuffer);
        virtual ~HeldDecoderBuffer();
        android::sp<VirtualDevice> vd;
        android::sp<CachedBuffer> cachedBuffer;
    };
#ifdef INTEL_WIDI
    struct Configuration {
        sp<IFrameTypeChangeListener> typeChangeListener;
        sp<IFrameListener> frameListener;
        FrameProcessingPolicy policy;
        bool frameServerActive;
        bool extendedModeEnabled;
        bool forceNotifyFrameType;
        bool forceNotifyBufferInfo;
    };
#endif
    class BufferList {
    public:
        BufferList(VirtualDevice& vd, const char* name, uint32_t limit, uint32_t format, uint32_t usage);
        buffer_handle_t get(uint32_t width, uint32_t height, sp<RefBase>* heldBuffer);
        void clear();
    private:
        struct HeldBuffer;
        VirtualDevice& mVd;
        const char* mName;
        android::List<buffer_handle_t> mAvailableBuffers;
        const uint32_t mLimit;
        const uint32_t mFormat;
        const uint32_t mUsage;
        uint32_t mBuffersToCreate;
        uint32_t mWidth;
        uint32_t mHeight;
    };
    struct Task;
    struct RenderTask;
    struct ComposeTask;
    struct EnableVspTask;
    struct DisableVspTask;
    struct BlitTask;
    struct FrameTypeChangedTask;
    struct BufferInfoChangedTask;
    struct OnFrameReadyTask;

    Mutex mConfigLock;
#ifdef INTEL_WIDI
    Configuration mCurrentConfig;
    Configuration mNextConfig;
#endif
    ssize_t mRgbLayer;
    ssize_t mYuvLayer;
    bool mProtectedMode;

    buffer_handle_t mExtLastKhandle;
    int64_t mExtLastTimestamp;

    int64_t mRenderTimestamp;

    Mutex mTaskLock; // for task queue and buffer lists
    BufferList mCscBuffers;
    BufferList mRgbUpscaleBuffers;
    DECLARE_THREAD(WidiBlitThread, VirtualDevice);
    Condition mRequestQueued;
    Condition mRequestDequeued;
    Vector< sp<Task> > mTasks;

    // fence info
    int mSyncTimelineFd;
    unsigned mNextSyncPoint;
    bool mExpectAcquireFences;
#ifdef INTEL_WIDI
    FrameInfo mLastInputFrameInfo;
    FrameInfo mLastOutputFrameInfo;
#endif
    int32_t mVideoFramerate;

    android::KeyedVector<buffer_handle_t, android::sp<CachedBuffer> > mMappedBufferCache;
    android::Mutex mHeldBuffersLock;
    android::KeyedVector<buffer_handle_t, android::sp<android::RefBase> > mHeldBuffers;

    // VSP
    bool mVspInUse;
    bool mVspEnabled;
    uint32_t mVspWidth;
    uint32_t mVspHeight;
    VADisplay va_dpy;
    VAConfigID va_config;
    VAContextID va_context;
    VASurfaceID va_blank_yuv_in;
    VASurfaceID va_blank_rgb_in;
    android::KeyedVector<buffer_handle_t, android::sp<VAMappedHandleObject> > mVaMapCache;

    bool mVspUpscale;
    bool mDebugVspClear;
    bool mDebugVspDump;
    uint32_t mDebugCounter;

private:
    android::sp<CachedBuffer> getMappedBuffer(buffer_handle_t handle);

    bool sendToWidi(hwc_display_contents_1_t *display);
    bool queueCompose(hwc_display_contents_1_t *display);
    bool queueColorConvert(hwc_display_contents_1_t *display);
#ifdef INTEL_WIDI
    bool handleExtendedMode(hwc_display_contents_1_t *display);

    void queueFrameTypeInfo(const FrameInfo& inputFrameInfo);
    void queueBufferInfo(const FrameInfo& outputFrameInfo);
#endif
    void colorSwap(buffer_handle_t src, buffer_handle_t dest, uint32_t pixelCount);
    void vspPrepare(uint32_t width, uint32_t height);
    void vspEnable(uint32_t width, uint32_t height);
    void vspDisable();
    void vspCompose(VASurfaceID videoIn, VASurfaceID rgbIn, VASurfaceID videoOut,
                    const VARectangle* surface_region, const VARectangle* output_region);

    bool getFrameOfSize(uint32_t width, uint32_t height, const IVideoPayloadManager::MetaData& metadata, IVideoPayloadManager::Buffer& info);
    void setMaxDecodeResolution(uint32_t width, uint32_t height);

public:
    VirtualDevice(Hwcomposer& hwc);
    virtual ~VirtualDevice();
    bool isFrameServerActive() const;

public:
    virtual bool prePrepare(hwc_display_contents_1_t *display);
    virtual bool prepare(hwc_display_contents_1_t *display);
    virtual bool commit(hwc_display_contents_1_t *display,
                          IDisplayContext *context);

    virtual bool vsyncControl(bool enabled);
    virtual bool blank(bool blank);
    virtual bool getDisplaySize(int *width, int *height);
    virtual bool getDisplayConfigs(uint32_t *configs,
                                       size_t *numConfigs);
    virtual bool getDisplayAttributes(uint32_t config,
                                          const uint32_t *attributes,
                                          int32_t *values);
    virtual bool compositionComplete();
    virtual bool initialize();
    virtual void deinitialize();
    virtual bool isConnected() const;
    virtual const char* getName() const;
    virtual int getType() const;
    virtual void onVsync(int64_t timestamp);
    virtual void dump(Dump& d);
    virtual uint32_t getFpsDivider();
#ifdef INTEL_WIDI
    // IFrameServer methods
    virtual android::status_t start(sp<IFrameTypeChangeListener> frameTypeChangeListener);
    virtual android::status_t stop(bool isConnected);
	/* TODO: 64-bit - this handle of size 32-bit is a problem for 64-bit */
    virtual android::status_t notifyBufferReturned(int handle);
    virtual android::status_t setResolution(const FrameProcessingPolicy& policy, android::sp<IFrameListener> listener);
#endif
    virtual bool setPowerMode(int mode);
    virtual int  getActiveConfig();
    virtual bool setActiveConfig(int index);

protected:
    bool mInitialized;
    Hwcomposer& mHwc;
    IVideoPayloadManager *mPayloadManager;
    SoftVsyncObserver *mVsyncObserver;
    uint32_t mOrigContentWidth;
    uint32_t mOrigContentHeight;
    bool mFirstVideoFrame;
    bool mLastConnectionStatus;
    uint32_t mCachedBufferCapcity;
    uint32_t mDecWidth;
    uint32_t mDecHeight;
    bool mIsForceCloneMode;
    uint32_t mFpsDivider;
};

}
}

#endif /* VIRTUAL_DEVICE_H */
