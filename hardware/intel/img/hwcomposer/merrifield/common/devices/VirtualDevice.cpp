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
#include <DisplayPlaneManager.h>
#include <DisplayQuery.h>
#include <VirtualDevice.h>
#include <SoftVsyncObserver.h>

#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

#include <hal_public.h>
#include <libsync/sw_sync.h>
#include <sync/sync.h>

#include <va/va_android.h>
#include <va/va_vpp.h>
#include <va/va_tpi.h>

#include <cutils/properties.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#define NUM_CSC_BUFFERS 6
#define NUM_SCALING_BUFFERS 3

#define QCIF_WIDTH 176
#define QCIF_HEIGHT 144

namespace android {
namespace intel {

static inline uint32_t align_width(uint32_t val)
{
    return align_to(val, 64);
}

static inline uint32_t align_height(uint32_t val)
{
    return align_to(val, 16);
}

static void my_close_fence(const char* func, const char* fenceName, int& fenceFd)
{
    if (fenceFd != -1) {
        ALOGV("%s: closing fence %s (fd=%d)", func, fenceName, fenceFd);
        int err = close(fenceFd);
        if (err < 0) {
            ALOGE("%s: fence %s close error %d: %s", func, fenceName, err, strerror(errno));
        }
        fenceFd = -1;
    }
}

static void my_sync_wait_and_close(const char* func, const char* fenceName, int& fenceFd)
{
    if (fenceFd != -1) {
        ALOGV("%s: waiting on fence %s (fd=%d)", func, fenceName, fenceFd);
        int err = sync_wait(fenceFd, 300);
        if (err < 0) {
            ALOGE("%s: fence %s sync_wait error %d: %s", func, fenceName, err, strerror(errno));
        }
        my_close_fence(func, fenceName, fenceFd);
    }
}

static void my_timeline_inc(const char* func, const char* timelineName, int& syncTimelineFd)
{
    if (syncTimelineFd != -1) {
        ALOGV("%s: incrementing timeline %s (fd=%d)", func, timelineName, syncTimelineFd);
        int err = sw_sync_timeline_inc(syncTimelineFd, 1);
        if (err < 0)
            ALOGE("%s sync timeline %s increment error %d: %s", func, timelineName, errno, strerror(errno));
        syncTimelineFd = -1;
    }
}

#define CLOSE_FENCE(fenceName)          my_close_fence(__func__, #fenceName, fenceName)
#define SYNC_WAIT_AND_CLOSE(fenceName)  my_sync_wait_and_close(__func__, #fenceName, fenceName)
#define TIMELINE_INC(timelineName)      my_timeline_inc(__func__, #timelineName, timelineName)

class MappedSurface {
public:
    MappedSurface(VADisplay dpy, VASurfaceID surf)
        : va_dpy(dpy),
          ptr(NULL)
    {
        VAStatus va_status;
        va_status = vaDeriveImage(va_dpy, surf, &image);
        if (va_status != VA_STATUS_SUCCESS) {
            ETRACE("vaDeriveImage returns %08x", va_status);
            return;
        }
        va_status = vaMapBuffer(va_dpy, image.buf, (void**)&ptr);
        if (va_status != VA_STATUS_SUCCESS) {
            ETRACE("vaMapBuffer returns %08x", va_status);
            vaDestroyImage(va_dpy, image.image_id);
            return;
        }
    }
    ~MappedSurface() {
        if (ptr == NULL)
            return;

        VAStatus va_status;

        va_status = vaUnmapBuffer(va_dpy, image.buf);
        if (va_status != VA_STATUS_SUCCESS) ETRACE("vaUnmapBuffer returns %08x", va_status);

        va_status = vaDestroyImage(va_dpy, image.image_id);
        if (va_status != VA_STATUS_SUCCESS) ETRACE("vaDestroyImage returns %08x", va_status);
    }
    bool valid() { return ptr != NULL; }
    uint8_t* getPtr() { return ptr; }
private:
    VADisplay va_dpy;
    VAImage image;
    uint8_t* ptr;
};

class VirtualDevice::VAMappedHandle {
public:
    VAMappedHandle(VADisplay dpy, buffer_handle_t handle, uint32_t stride, uint32_t height, unsigned int pixel_format)
        : va_dpy(dpy),
          surface(0)
    {
        VTRACE("Map gralloc %p size=%ux%u", handle, stride, height);

        unsigned int format;
        unsigned long buffer = reinterpret_cast<unsigned long>(handle);
        VASurfaceAttribExternalBuffers buf;
        buf.pixel_format = pixel_format;
        buf.width = stride;
        buf.height = height;
        buf.buffers = &buffer;
        buf.num_buffers = 1;
        buf.flags = 0;
        buf.private_data = NULL;

        if (pixel_format == VA_FOURCC_RGBA || pixel_format == VA_FOURCC_BGRA) {
            format = VA_RT_FORMAT_RGB32;
            buf.data_size = stride * height * 4;
            buf.num_planes = 3;
            buf.pitches[0] = stride;
            buf.pitches[1] = stride;
            buf.pitches[2] = stride;
            buf.pitches[3] = 0;
            buf.offsets[0] = 0;
            buf.offsets[1] = 0;
            buf.offsets[2] = 0;
            buf.offsets[3] = 0;
        }
        else {
            format = VA_RT_FORMAT_YUV420;
            buf.data_size = stride * height * 3/2;
            buf.num_planes = 2;
            buf.pitches[0] = stride;
            buf.pitches[1] = stride;
            buf.pitches[2] = 0;
            buf.pitches[3] = 0;
            buf.offsets[0] = 0;
            buf.offsets[1] = stride * height;
        }

        VASurfaceAttrib attrib_list[3];
        attrib_list[0].type = (VASurfaceAttribType)VASurfaceAttribMemoryType;
        attrib_list[0].flags = VA_SURFACE_ATTRIB_SETTABLE;
        attrib_list[0].value.type = VAGenericValueTypeInteger;
        attrib_list[0].value.value.i = VA_SURFACE_ATTRIB_MEM_TYPE_ANDROID_GRALLOC;
        attrib_list[1].type = (VASurfaceAttribType)VASurfaceAttribExternalBufferDescriptor;
        attrib_list[1].flags = VA_SURFACE_ATTRIB_SETTABLE;
        attrib_list[1].value.type = VAGenericValueTypePointer;
        attrib_list[1].value.value.p = (void *)&buf;
        attrib_list[2].type = (VASurfaceAttribType)VASurfaceAttribPixelFormat;
        attrib_list[2].flags = VA_SURFACE_ATTRIB_SETTABLE;
        attrib_list[2].value.type = VAGenericValueTypeInteger;
        attrib_list[2].value.value.i = pixel_format;

        VAStatus va_status;
        va_status = vaCreateSurfaces(va_dpy,
                    format,
                    stride,
                    height,
                    &surface,
                    1,
                    attrib_list,
                    3);
        if (va_status != VA_STATUS_SUCCESS) {
            ETRACE("vaCreateSurfaces returns %08x, surface = %x", va_status, surface);
            surface = 0;
        }
    }
    VAMappedHandle(VADisplay dpy, buffer_handle_t khandle, uint32_t stride, uint32_t height, bool tiled)
        : va_dpy(dpy),
          surface(0)
    {
        int format;
        VASurfaceAttributeTPI attribTpi;
        memset(&attribTpi, 0, sizeof(attribTpi));
        VTRACE("Map khandle 0x%x size=%ux%u", khandle, stride, height);
        attribTpi.type = VAExternalMemoryKernelDRMBufffer;
        attribTpi.width = stride;
        attribTpi.height = height;
        attribTpi.size = stride*height*3/2;
        attribTpi.pixel_format = VA_FOURCC_NV12;
        attribTpi.tiling = tiled;
        attribTpi.luma_stride = stride;
        attribTpi.chroma_u_stride = stride;
        attribTpi.chroma_v_stride = stride;
        attribTpi.luma_offset = 0;
        attribTpi.chroma_u_offset = stride*height;
        attribTpi.chroma_v_offset = stride*height+1;
        format = VA_RT_FORMAT_YUV420;
        attribTpi.count = 1;
        attribTpi.buffers = (long unsigned int*) &khandle;

        VAStatus va_status;
        va_status = vaCreateSurfacesWithAttribute(va_dpy,
                    stride,
                    height,
                    format,
                    1,
                    &surface,
                    &attribTpi);
        if (va_status != VA_STATUS_SUCCESS) {
            ETRACE("vaCreateSurfacesWithAttribute returns %08x", va_status);
            surface = 0;
        }
    }
    ~VAMappedHandle()
    {
        if (surface == 0)
            return;
        VAStatus va_status;
        va_status = vaDestroySurfaces(va_dpy, &surface, 1);
        if (va_status != VA_STATUS_SUCCESS) ETRACE("vaDestroySurfaces returns %08x", va_status);
    }
private:
    VADisplay va_dpy;
public:
    VASurfaceID surface;
};

// refcounted version of VAMappedHandle, to make caching easier
class VirtualDevice::VAMappedHandleObject : public RefBase, public VAMappedHandle {
public:
    VAMappedHandleObject(VADisplay dpy, buffer_handle_t handle, uint32_t stride, uint32_t height, unsigned int pixel_format)
        : VAMappedHandle(dpy, handle, stride, height, pixel_format) { }
    VAMappedHandleObject(VADisplay dpy, buffer_handle_t khandle, uint32_t stride, uint32_t height, bool tiled)
        : VAMappedHandle(dpy, khandle, stride, height, tiled) { }
protected:
    ~VAMappedHandleObject() {}
};

VirtualDevice::CachedBuffer::CachedBuffer(BufferManager *mgr, buffer_handle_t handle)
    : manager(mgr),
      mapper(NULL),
      vaMappedHandle(NULL),
      cachedKhandle(0)
{
    DataBuffer *buffer = manager->lockDataBuffer((buffer_handle_t)handle);
    mapper = manager->map(*buffer);
    manager->unlockDataBuffer(buffer);
}

VirtualDevice::CachedBuffer::~CachedBuffer()
{
    if (vaMappedHandle != NULL)
        delete vaMappedHandle;
    manager->unmap(mapper);
}

VirtualDevice::HeldDecoderBuffer::HeldDecoderBuffer(const sp<VirtualDevice>& vd, const android::sp<CachedBuffer>& cachedBuffer)
    : vd(vd),
      cachedBuffer(cachedBuffer)
{
    if (!vd->mPayloadManager->setRenderStatus(cachedBuffer->mapper, true)) {
        ETRACE("Failed to set render status");
    }
}

VirtualDevice::HeldDecoderBuffer::~HeldDecoderBuffer()
{
    if (!vd->mPayloadManager->setRenderStatus(cachedBuffer->mapper, false)) {
        ETRACE("Failed to set render status");
    }
}

struct VirtualDevice::Task : public RefBase {
    virtual void run(VirtualDevice& vd) = 0;
    virtual ~Task() {}
};

struct VirtualDevice::RenderTask : public VirtualDevice::Task {
    RenderTask() : successful(false) { }
    virtual void run(VirtualDevice& vd) = 0;
    bool successful;
};

struct VirtualDevice::ComposeTask : public VirtualDevice::RenderTask {
    ComposeTask()
        : videoKhandle(0),
          rgbHandle(NULL),
          mappedRgbIn(NULL),
          outputHandle(NULL),
          yuvAcquireFenceFd(-1),
          rgbAcquireFenceFd(-1),
          outbufAcquireFenceFd(-1),
          syncTimelineFd(-1) { }

    virtual ~ComposeTask() {
        // If queueCompose() creates this object and sets up fences,
        // but aborts before enqueuing the task, or if the task runs
        // but errors out, make sure our acquire fences get closed
        // and any release fences get signaled.
        CLOSE_FENCE(yuvAcquireFenceFd);
        CLOSE_FENCE(rgbAcquireFenceFd);
        CLOSE_FENCE(outbufAcquireFenceFd);
        TIMELINE_INC(syncTimelineFd);
    }

    virtual void run(VirtualDevice& vd) {
        bool dump = false;
        if (vd.mDebugVspDump && ++vd.mDebugCounter > 200) {
            dump = true;
            vd.mDebugCounter = 0;
        }

        SYNC_WAIT_AND_CLOSE(yuvAcquireFenceFd);

        VASurfaceID videoInSurface;
        if (videoKhandle == 0) {
            videoInSurface = vd.va_blank_yuv_in;
        } else {
            if (videoCachedBuffer->cachedKhandle != videoKhandle || videoCachedBuffer->vaMappedHandle == NULL) {
                if (videoCachedBuffer->vaMappedHandle != NULL)
                    delete videoCachedBuffer->vaMappedHandle;
                videoCachedBuffer->vaMappedHandle = new VAMappedHandle(vd.va_dpy, videoKhandle, videoStride, videoBufHeight, videoTiled);
                videoCachedBuffer->cachedKhandle = videoKhandle;
            }
            videoInSurface = videoCachedBuffer->vaMappedHandle->surface;
        }

        if (videoInSurface == 0) {
            ETRACE("Couldn't map video");
            return;
        }
        SYNC_WAIT_AND_CLOSE(rgbAcquireFenceFd);
        SYNC_WAIT_AND_CLOSE(outbufAcquireFenceFd);

        VAMappedHandle mappedVideoOut(vd.va_dpy, outputHandle, align_width(outWidth), align_height(outHeight), (unsigned int)VA_FOURCC_NV12);
        if (mappedVideoOut.surface == 0) {
            ETRACE("Unable to map outbuf");
            return;
        }

        if (dump)
            dumpSurface(vd.va_dpy, "/data/misc/vsp_in.yuv", videoInSurface, videoStride*videoBufHeight*3/2);

        if (mappedRgbIn != NULL) {
            if (dump)
                dumpSurface(vd.va_dpy, "/data/misc/vsp_in.rgb", mappedRgbIn->surface, align_width(outWidth)*align_height(outHeight)*4);
            vd.vspCompose(videoInSurface, mappedRgbIn->surface, mappedVideoOut.surface, &surface_region, &output_region);
        }
        else if (rgbHandle != NULL) {
            VAMappedHandle localMappedRgbIn(vd.va_dpy, rgbHandle, align_width(outWidth), align_height(outHeight), (unsigned int)VA_FOURCC_BGRA);
            vd.vspCompose(videoInSurface, localMappedRgbIn.surface, mappedVideoOut.surface, &surface_region, &output_region);
        }
        else {
            // No RGBA, so compose with 100% transparent RGBA frame.
            if (dump)
                dumpSurface(vd.va_dpy, "/data/misc/vsp_in.rgb", vd.va_blank_rgb_in, align_width(outWidth)*align_height(outHeight)*4);
            vd.vspCompose(videoInSurface, vd.va_blank_rgb_in, mappedVideoOut.surface, &surface_region, &output_region);
        }
        if (dump)
            dumpSurface(vd.va_dpy, "/data/misc/vsp_out.yuv", mappedVideoOut.surface, align_width(outWidth)*align_height(outHeight)*3/2);
        TIMELINE_INC(syncTimelineFd);
        successful = true;
    }
    void dumpSurface(VADisplay va_dpy, const char* filename, VASurfaceID surf, int size) {
        MappedSurface dumpSurface(va_dpy, surf);
        if (dumpSurface.valid()) {
            int fd = open(filename, O_CREAT | O_TRUNC | O_WRONLY, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP);
            if (fd > 0) {
                write(fd, dumpSurface.getPtr(), size);
                close(fd);
                ALOGI("Output dumped");
            }
            else
                ALOGE("Error %d opening output file: %s", errno, strerror(errno));
        }
        else
            ALOGE("Failed to map output for dump");
    }
    buffer_handle_t videoKhandle;
    uint32_t videoStride;
    uint32_t videoBufHeight;
    bool videoTiled;
    buffer_handle_t rgbHandle;
    sp<RefBase> heldRgbHandle;
    sp<VAMappedHandleObject> mappedRgbIn;
    buffer_handle_t outputHandle;
    VARectangle surface_region;
    VARectangle output_region;
    uint32_t outWidth;
    uint32_t outHeight;
    sp<CachedBuffer> videoCachedBuffer;
    sp<RefBase> heldVideoBuffer;
    int yuvAcquireFenceFd;
    int rgbAcquireFenceFd;
    int outbufAcquireFenceFd;
    int syncTimelineFd;
};

struct VirtualDevice::EnableVspTask : public VirtualDevice::Task {
    virtual void run(VirtualDevice& vd) {
        vd.vspEnable(width, height);
    }
    uint32_t width;
    uint32_t height;
};

struct VirtualDevice::DisableVspTask : public VirtualDevice::Task {
    virtual void run(VirtualDevice& vd) {
        vd.vspDisable();
    }
};

struct VirtualDevice::BlitTask : public VirtualDevice::RenderTask {
    BlitTask()
        : srcAcquireFenceFd(-1),
          destAcquireFenceFd(-1),
          syncTimelineFd(-1) { }

    virtual ~BlitTask()
    {
        // If queueColorConvert() creates this object and sets up fences,
        // but aborts before enqueuing the task, or if the task runs
        // but errors out, make sure our acquire fences get closed
        // and any release fences get signaled.
        CLOSE_FENCE(srcAcquireFenceFd);
        CLOSE_FENCE(destAcquireFenceFd);
        TIMELINE_INC(syncTimelineFd);
    }

    virtual void run(VirtualDevice& vd) {
        SYNC_WAIT_AND_CLOSE(srcAcquireFenceFd);
        SYNC_WAIT_AND_CLOSE(destAcquireFenceFd);
        BufferManager* mgr = vd.mHwc.getBufferManager();
        if (!(mgr->blit(srcHandle, destHandle, destRect, false, false))) {
            ETRACE("color space conversion from RGB to NV12 failed");
        }
        else
            successful = true;
        TIMELINE_INC(syncTimelineFd);
    }
    buffer_handle_t srcHandle;
    buffer_handle_t destHandle;
    int srcAcquireFenceFd;
    int destAcquireFenceFd;
    int syncTimelineFd;
    crop_t destRect;
};

struct VirtualDevice::FrameTypeChangedTask : public VirtualDevice::Task {
    virtual void run(VirtualDevice& vd) {
#ifdef INTEL_WIDI
        typeChangeListener->frameTypeChanged(inputFrameInfo);
        ITRACE("Notify frameTypeChanged: %dx%d in %dx%d @ %d fps",
            inputFrameInfo.contentWidth, inputFrameInfo.contentHeight,
            inputFrameInfo.bufferWidth, inputFrameInfo.bufferHeight,
            inputFrameInfo.contentFrameRateN);
#endif
    }
#ifdef INTEL_WIDI
    sp<IFrameTypeChangeListener> typeChangeListener;
    FrameInfo inputFrameInfo;
#endif
};

struct VirtualDevice::BufferInfoChangedTask : public VirtualDevice::Task {
    virtual void run(VirtualDevice& vd) {
#ifdef INTEL_WIDI
        typeChangeListener->bufferInfoChanged(outputFrameInfo);
        ITRACE("Notify bufferInfoChanged: %dx%d in %dx%d @ %d fps",
            outputFrameInfo.contentWidth, outputFrameInfo.contentHeight,
            outputFrameInfo.bufferWidth, outputFrameInfo.bufferHeight,
            outputFrameInfo.contentFrameRateN);
#endif
    }
#ifdef INTEL_WIDI
    sp<IFrameTypeChangeListener> typeChangeListener;
    FrameInfo outputFrameInfo;
#endif
};

struct VirtualDevice::OnFrameReadyTask : public VirtualDevice::Task {
    virtual void run(VirtualDevice& vd) {
        if (renderTask != NULL && !renderTask->successful)
            return;

        {
            Mutex::Autolock _l(vd.mHeldBuffersLock);
            //Add the heldbuffer to the vector before calling onFrameReady, so that the buffer will be removed
            //from the vector properly even if the notifyBufferReturned call acquires mHeldBuffersLock first.
            vd.mHeldBuffers.add(handle, heldBuffer);
        }
#ifdef INTEL_WIDI
        // FIXME: we could remove this casting once onFrameReady receives
        // a buffer_handle_t handle
        status_t result = frameListener->onFrameReady((uint32_t)handle, handleType, renderTimestamp, mediaTimestamp);
        if (result != OK) {
            Mutex::Autolock _l(vd.mHeldBuffersLock);
            vd.mHeldBuffers.removeItem(handle);
        }
#else
        Mutex::Autolock _l(vd.mHeldBuffersLock);
        vd.mHeldBuffers.removeItem(handle);
#endif
    }
    sp<RenderTask> renderTask;
    sp<RefBase> heldBuffer;
    buffer_handle_t handle;
#ifdef INTEL_WIDI
    sp<IFrameListener> frameListener;
    HWCBufferHandleType handleType;
#endif
    int64_t renderTimestamp;
    int64_t mediaTimestamp;
};

struct VirtualDevice::BufferList::HeldBuffer : public RefBase {
    HeldBuffer(BufferList& list, buffer_handle_t handle, uint32_t w, uint32_t h)
        : mList(list),
          mHandle(handle),
          mWidth(w),
          mHeight(h) { }
    virtual ~HeldBuffer()
    {
        Mutex::Autolock _l(mList.mVd.mTaskLock);
        if (mWidth == mList.mWidth && mHeight == mList.mHeight) {
            VTRACE("Returning %s buffer %p (%ux%u) to list", mList.mName, mHandle, mWidth, mHeight);
            mList.mAvailableBuffers.push_back(mHandle);
        } else {
            VTRACE("Deleting %s buffer %p (%ux%u)", mList.mName, mHandle, mWidth, mHeight);
            BufferManager* mgr = mList.mVd.mHwc.getBufferManager();
            mgr->freeGrallocBuffer((mHandle));
            if (mList.mBuffersToCreate < mList.mLimit)
                mList.mBuffersToCreate++;
        }
    }

    BufferList& mList;
    buffer_handle_t mHandle;
    uint32_t mWidth;
    uint32_t mHeight;
};

VirtualDevice::BufferList::BufferList(VirtualDevice& vd, const char* name,
                                      uint32_t limit, uint32_t format, uint32_t usage)
    : mVd(vd),
      mName(name),
      mLimit(limit),
      mFormat(format),
      mUsage(usage),
      mBuffersToCreate(0),
      mWidth(0),
      mHeight(0)
{
}

buffer_handle_t VirtualDevice::BufferList::get(uint32_t width, uint32_t height, sp<RefBase>* heldBuffer)
{
    width = align_width(width);
    height = align_height(height);
    if (mWidth != width || mHeight != height) {
        ITRACE("%s buffers changing from %dx%d to %dx%d",
                mName, mWidth, mHeight, width, height);
        clear();
        mWidth = width;
        mHeight = height;
        mBuffersToCreate = mLimit;
    }

    buffer_handle_t handle;
    if (mAvailableBuffers.empty()) {
        if (mBuffersToCreate <= 0)
            return NULL;
        BufferManager* mgr = mVd.mHwc.getBufferManager();
        handle = reinterpret_cast<buffer_handle_t>(
            mgr->allocGrallocBuffer(width, height, mFormat, mUsage));
        if (handle == NULL){
            ETRACE("failed to allocate %s buffer", mName);
            return NULL;
        }
        mBuffersToCreate--;
    }
    else {
        handle = *mAvailableBuffers.begin();
        mAvailableBuffers.erase(mAvailableBuffers.begin());
    }
    *heldBuffer = new HeldBuffer(*this, handle, width, height);
    return handle;
}

void VirtualDevice::BufferList::clear()
{
    if (mWidth != 0 || mHeight != 0)
        ITRACE("Releasing %s buffers (%ux%u)", mName, mWidth, mHeight);
    if (!mAvailableBuffers.empty()) {
        // iterate the list and call freeGraphicBuffer
        for (List<buffer_handle_t>::iterator i = mAvailableBuffers.begin(); i != mAvailableBuffers.end(); ++i) {
            VTRACE("Deleting the gralloc buffer associated with handle (%p)", (*i));
            mVd.mHwc.getBufferManager()->freeGrallocBuffer((*i));
        }
        mAvailableBuffers.clear();
    }
    mWidth = 0;
    mHeight = 0;
}

VirtualDevice::VirtualDevice(Hwcomposer& hwc)
    : mProtectedMode(false),
      mCscBuffers(*this, "CSC",
                  NUM_CSC_BUFFERS, DisplayQuery::queryNV12Format(),
                  GRALLOC_USAGE_HW_VIDEO_ENCODER | GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_PRIVATE_1),
      mRgbUpscaleBuffers(*this, "RGB upscale",
                         NUM_SCALING_BUFFERS, HAL_PIXEL_FORMAT_BGRA_8888,
                         GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER),
      mInitialized(false),
      mHwc(hwc),
      mPayloadManager(NULL),
      mVsyncObserver(NULL),
      mOrigContentWidth(0),
      mOrigContentHeight(0),
      mFirstVideoFrame(true),
      mLastConnectionStatus(false),
      mCachedBufferCapcity(16),
      mDecWidth(0),
      mDecHeight(0),
      mFpsDivider(1)
{
    CTRACE();
#ifdef INTEL_WIDI
    mNextConfig.frameServerActive = false;
#endif
}

VirtualDevice::~VirtualDevice()
{
    WARN_IF_NOT_DEINIT();
}

sp<VirtualDevice::CachedBuffer> VirtualDevice::getMappedBuffer(buffer_handle_t handle)
{
    ssize_t index = mMappedBufferCache.indexOfKey(handle);
    sp<CachedBuffer> cachedBuffer;
    if (index == NAME_NOT_FOUND) {
        if (mMappedBufferCache.size() > mCachedBufferCapcity)
            mMappedBufferCache.clear();

        cachedBuffer = new CachedBuffer(mHwc.getBufferManager(), handle);
        mMappedBufferCache.add(handle, cachedBuffer);
    } else {
        cachedBuffer = mMappedBufferCache[index];
    }

    return cachedBuffer;
}

bool VirtualDevice::threadLoop()
{
    sp<Task> task;
    {
        Mutex::Autolock _l(mTaskLock);
        while (mTasks.empty()) {
            mRequestQueued.wait(mTaskLock);
        }
        task = *mTasks.begin();
        mTasks.erase(mTasks.begin());
    }
    if (task != NULL) {
        task->run(*this);
        task = NULL;
    }
    mRequestDequeued.signal();

    return true;
}
#ifdef INTEL_WIDI
status_t VirtualDevice::start(sp<IFrameTypeChangeListener> typeChangeListener)
{
    ITRACE();
    Mutex::Autolock _l(mConfigLock);
    mNextConfig.typeChangeListener = typeChangeListener;
    mNextConfig.frameListener = NULL;
    mNextConfig.policy.scaledWidth = 0;
    mNextConfig.policy.scaledHeight = 0;
    mNextConfig.policy.xdpi = 96;
    mNextConfig.policy.ydpi = 96;
    mNextConfig.policy.refresh = 60;
    mNextConfig.extendedModeEnabled =
        Hwcomposer::getInstance().getDisplayAnalyzer()->isVideoExtModeEnabled();
    mVideoFramerate = 0;
    mFirstVideoFrame = true;
    mNextConfig.frameServerActive = true;
    mNextConfig.forceNotifyFrameType = true;
    mNextConfig.forceNotifyBufferInfo = true;

    return NO_ERROR;
}

status_t VirtualDevice::stop(bool isConnected)
{
    ITRACE();
    Mutex::Autolock _l(mConfigLock);
    mNextConfig.typeChangeListener = NULL;
    mNextConfig.frameListener = NULL;
    mNextConfig.policy.scaledWidth = 0;
    mNextConfig.policy.scaledHeight = 0;
    mNextConfig.policy.xdpi = 96;
    mNextConfig.policy.ydpi = 96;
    mNextConfig.policy.refresh = 60;
    mNextConfig.frameServerActive = false;
    mNextConfig.extendedModeEnabled = false;
    mNextConfig.forceNotifyFrameType = false;
    mNextConfig.forceNotifyBufferInfo = false;
    {
        Mutex::Autolock _l(mTaskLock);
        mCscBuffers.clear();
    }
    return NO_ERROR;
}
#endif

bool VirtualDevice::isFrameServerActive() const
{
#ifdef INTEL_WIDI
    return  mCurrentConfig.frameServerActive;
#endif
    return false;
}

#ifdef INTEL_WIDI
/* TODO: 64-bit - this handle of size 32-bit is a problem for 64-bit */
status_t VirtualDevice::notifyBufferReturned(int handle)
{
    CTRACE();
    Mutex::Autolock _l(mHeldBuffersLock);
    ssize_t index = mHeldBuffers.indexOfKey((buffer_handle_t)handle);
    if (index == NAME_NOT_FOUND) {
        ETRACE("Couldn't find returned khandle %p", handle);
    } else {
        VTRACE("Removing heldBuffer associated with handle (%p)", handle);
        mHeldBuffers.removeItemsAt(index, 1);
    }
    return NO_ERROR;
}

status_t VirtualDevice::setResolution(const FrameProcessingPolicy& policy, sp<IFrameListener> listener)
{
    ITRACE();
    Mutex::Autolock _l(mConfigLock);
    mNextConfig.frameListener = listener;
    mNextConfig.policy = policy;
    return NO_ERROR;
}
#endif
static bool canUseDirectly(const hwc_display_contents_1_t *display, size_t n)
{
    const hwc_layer_1_t& fbTarget = display->hwLayers[display->numHwLayers-1];
    const hwc_layer_1_t& layer = display->hwLayers[n];
    const IMG_native_handle_t* nativeHandle = reinterpret_cast<const IMG_native_handle_t*>(layer.handle);
    return !(layer.flags & HWC_SKIP_LAYER) && layer.transform == 0 &&
            layer.blending == HWC_BLENDING_PREMULT &&
            layer.sourceCropf.left == 0 && layer.sourceCropf.top == 0 &&
            layer.displayFrame.left == 0 && layer.displayFrame.top == 0 &&
            layer.sourceCropf.right == fbTarget.sourceCropf.right &&
            layer.sourceCropf.bottom == fbTarget.sourceCropf.bottom &&
            layer.displayFrame.right == fbTarget.displayFrame.right &&
            layer.displayFrame.bottom == fbTarget.displayFrame.bottom &&
            layer.planeAlpha == 255 && layer.handle != NULL &&
            (nativeHandle->iFormat == HAL_PIXEL_FORMAT_RGBA_8888 ||
             nativeHandle->iFormat == HAL_PIXEL_FORMAT_BGRA_8888);
}

bool VirtualDevice::prePrepare(hwc_display_contents_1_t *display)
{
    RETURN_FALSE_IF_NOT_INIT();
    return true;
}

bool VirtualDevice::prepare(hwc_display_contents_1_t *display)
{
    RETURN_FALSE_IF_NOT_INIT();

    mRenderTimestamp = systemTime();
    mVspInUse = false;
    mExpectAcquireFences = false;
    mIsForceCloneMode = false;
#ifdef INTEL_WIDI
    {
        Mutex::Autolock _l(mConfigLock);
        mCurrentConfig = mNextConfig;
    }
#endif

    bool shouldBeConnected = (display != NULL);
    if (shouldBeConnected != mLastConnectionStatus) {
        // calling this will reload the property 'hwc.video.extmode.enable'
        Hwcomposer::getInstance().getDisplayAnalyzer()->isVideoExtModeEnabled();
        char propertyVal[PROPERTY_VALUE_MAX];
        if (property_get("widi.compose.rgb_upscale", propertyVal, NULL) > 0)
            mVspUpscale = atoi(propertyVal);
        if (property_get("widi.compose.all_video", propertyVal, NULL) > 0)
            mDebugVspClear = atoi(propertyVal);
        if (property_get("widi.compose.dump", propertyVal, NULL) > 0)
            mDebugVspDump = atoi(propertyVal);

        Hwcomposer::getInstance().getMultiDisplayObserver()->notifyWidiConnectionStatus(shouldBeConnected);
        mLastConnectionStatus = shouldBeConnected;
    }

    if (!display) {
        // No image. We're done with any mappings and CSC buffers.
        mMappedBufferCache.clear();
        Mutex::Autolock _l(mTaskLock);
        mCscBuffers.clear();
        return true;
    }

#ifdef INTEL_WIDI
    if (!mCurrentConfig.frameServerActive) {
        // We're done with CSC buffers, since we blit to outbuf in this mode.
        // We want to keep mappings cached, so we don't clear mMappedBufferCache.
        Mutex::Autolock _l(mTaskLock);
        mCscBuffers.clear();
    }
#else
    Mutex::Autolock _l(mTaskLock);
    mCscBuffers.clear();
#endif

    // by default send the FRAMEBUFFER_TARGET layer (composited image)
    const ssize_t fbTarget = display->numHwLayers-1;
    mRgbLayer = fbTarget;
    mYuvLayer = -1;

    DisplayAnalyzer *analyzer = mHwc.getDisplayAnalyzer();

    mProtectedMode = false;
#ifdef INTEL_WIDI
    if (mCurrentConfig.typeChangeListener != NULL &&
        !analyzer->isOverlayAllowed() &&
        analyzer->getVideoInstances() <= 1) {
        if (mCurrentConfig.typeChangeListener->shutdownVideo() != OK) {
            ITRACE("Waiting for prior encoder session to shut down...");
        }
        /* Setting following flag to true will enable us to call bufferInfoChanged() in clone mode. */
        mNextConfig.forceNotifyBufferInfo = true;
        mYuvLayer = -1;
        mRgbLayer = -1;
        // Skipping frames.
        // Fences aren't set in prepare, and we don't need them here, but they'll
        // be set later and we have to close them. Don't log a warning in this case.
        mExpectAcquireFences = true;
        for (ssize_t i = 0; i < fbTarget; i++)
            display->hwLayers[i].compositionType = HWC_OVERLAY;
        return true;
    }

    for (ssize_t i = 0; i < fbTarget; i++) {
        hwc_layer_1_t& layer = display->hwLayers[i];
        if (analyzer->isVideoLayer(layer) && (mCurrentConfig.extendedModeEnabled || mDebugVspClear || analyzer->isProtectedLayer(layer))) {
            if (mCurrentConfig.frameServerActive && mCurrentConfig.extendedModeEnabled) {
                // If composed in surface flinger, then stream fbtarget.
                if ((layer.flags & HWC_SKIP_LAYER) && !analyzer->ignoreVideoSkipFlag()) {
                    continue;
                }

                /* If the resolution of the video layer is less than QCIF, then we are going to play it in clone mode only.*/
                uint32_t vidContentWidth = layer.sourceCropf.right - layer.sourceCropf.left;
                uint32_t vidContentHeight = layer.sourceCropf.bottom - layer.sourceCropf.top;
                if (vidContentWidth < QCIF_WIDTH || vidContentHeight < QCIF_HEIGHT) {
                    VTRACE("Ingoring layer %d which is too small for extended mode", i);
                    continue;
                }
            }
            mYuvLayer = i;
            mProtectedMode = analyzer->isProtectedLayer(layer);
            break;
        }
    }
#endif

    if (mYuvLayer == -1) {
        mFirstVideoFrame = true;
        mDecWidth = 0;
        mDecHeight = 0;
    }
#ifdef INTEL_WIDI
    if (mCurrentConfig.frameServerActive && mCurrentConfig.extendedModeEnabled && mYuvLayer != -1) {
        if (handleExtendedMode(display)) {
            mYuvLayer = -1;
            mRgbLayer = -1;
            // Extended mode is successful.
            // Fences aren't set in prepare, and we don't need them here, but they'll
            // be set later and we have to close them. Don't log a warning in this case.
            mExpectAcquireFences = true;
            for (ssize_t i = 0; i < fbTarget; i++)
                display->hwLayers[i].compositionType = HWC_OVERLAY;
            return true;
        }
        // if error in playback file , switch to clone mode
        WTRACE("Error, falling back to clone mode");
        mIsForceCloneMode = true;
        mYuvLayer = -1;
    }
#endif
    if (mYuvLayer == 0 && fbTarget == 1) {
        // No RGB layer, so tell queueCompose to use blank RGB in fbtarget.
        mRgbLayer = -1;
    }
    else if (mYuvLayer == 0 && fbTarget == 2) {
        if (canUseDirectly(display, 1))
            mRgbLayer = 1;
    }
    else if (mYuvLayer == -1 && fbTarget == 1) {
        if (canUseDirectly(display, 0))
            mRgbLayer = 0;
    }

    for (ssize_t i = 0; i < fbTarget; i++) {
        hwc_layer_1_t& layer = display->hwLayers[i];
        if (i == mYuvLayer || i == mRgbLayer || mRgbLayer != fbTarget)
            layer.compositionType = HWC_OVERLAY;
        else
            layer.compositionType = HWC_FRAMEBUFFER;
    }
    if (mYuvLayer != -1 && mRgbLayer == fbTarget)
        // This tells SurfaceFlinger to render this layer by writing transparent pixels
        // to this layer's target region within the framebuffer. This effectively punches
        // a hole through any content that is supposed to show below the video, and the
        // video can be seen through this hole when we composite the YUV and RGBA layers
        // together. Content above will draw on top of this hole and can cover the video.
        // This has no effect when the video is the bottommost layer.
        display->hwLayers[mYuvLayer].hints |= HWC_HINT_CLEAR_FB;

#ifdef INTEL_WIDI
    // we're streaming fbtarget, so send onFramePrepare and wait for composition to happen
    if (mCurrentConfig.frameListener != NULL)
        mCurrentConfig.frameListener->onFramePrepare(mRenderTimestamp, -1);
#endif
    return true;
}

bool VirtualDevice::commit(hwc_display_contents_1_t *display, IDisplayContext *context)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (display != NULL && (mRgbLayer != -1 || mYuvLayer != -1))
        sendToWidi(display);

    if (mVspEnabled && !mVspInUse) {
        mVaMapCache.clear();
        sp<DisableVspTask> disableVsp = new DisableVspTask();
        mMappedBufferCache.clear();
        Mutex::Autolock _l(mTaskLock);
        mRgbUpscaleBuffers.clear();
        mTasks.push(disableVsp);
        mRequestQueued.signal();
        mVspEnabled = false;
    }

    if (display != NULL) {
        // All acquire fences should be copied somewhere else or closed by now
        // and set to -1 in these structs except in the case of extended mode.
        // Make sure the fences are closed and log a warning if not in extended mode.
        if (display->outbufAcquireFenceFd != -1) {
            if (!mExpectAcquireFences)
                WTRACE("outbuf acquire fence (fd=%d) not yet saved or closed", display->outbufAcquireFenceFd);
            CLOSE_FENCE(display->outbufAcquireFenceFd);
        }
        for (size_t i = 0; i < display->numHwLayers; i++) {
            hwc_layer_1_t& layer = display->hwLayers[i];
            if (layer.acquireFenceFd != -1) {
                if (!mExpectAcquireFences && (i < display->numHwLayers-1 || i == (size_t) mRgbLayer))
                    WTRACE("layer %zd acquire fence (fd=%zd) not yet saved or closed", i, layer.acquireFenceFd);
                CLOSE_FENCE(layer.acquireFenceFd);
            }
        }
    }

    return true;
}

bool VirtualDevice::sendToWidi(hwc_display_contents_1_t *display)
{
    VTRACE("RGB=%d, YUV=%d", mRgbLayer, mYuvLayer);

    if (mYuvLayer == -1 && mRgbLayer == -1)
        return true;

    if (mYuvLayer != -1) {
        mVspInUse = true;
        if (queueCompose(display))
            return true;
    }

    return queueColorConvert(display);
}

bool VirtualDevice::queueCompose(hwc_display_contents_1_t *display)
{
    hwc_layer_1_t& yuvLayer = display->hwLayers[mYuvLayer];
    if (yuvLayer.handle == NULL) {
        ETRACE("No video handle");
        return false;
    }
#ifdef INTEL_WIDI
    if (!mCurrentConfig.frameServerActive && display->outbuf == NULL) {
#else
    if (display->outbuf == NULL) {
#endif
        ETRACE("No outbuf");
        return true; // fallback would be pointless
    }

    sp<ComposeTask> composeTask = new ComposeTask();

    sp<RefBase> heldBuffer;
    sp<OnFrameReadyTask> frameReadyTask;
    Mutex::Autolock _l(mTaskLock);

    float upscale_x = 1.0;
    float upscale_y = 1.0;
    hwc_layer_1_t& fbTarget = display->hwLayers[display->numHwLayers-1];
    composeTask->outWidth = fbTarget.sourceCropf.right - fbTarget.sourceCropf.left;
    composeTask->outHeight = fbTarget.sourceCropf.bottom - fbTarget.sourceCropf.top;

    bool scaleRgb = false;
#ifdef INTEL_WIDI
    if (mCurrentConfig.frameServerActive) {
        if (mVspUpscale) {
            composeTask->outWidth = mCurrentConfig.policy.scaledWidth;
            composeTask->outHeight = mCurrentConfig.policy.scaledHeight;
            upscale_x = mCurrentConfig.policy.scaledWidth/(fbTarget.sourceCropf.right - fbTarget.sourceCropf.left);
            upscale_y = mCurrentConfig.policy.scaledHeight/(fbTarget.sourceCropf.bottom - fbTarget.sourceCropf.top);
            scaleRgb = composeTask->outWidth != fbTarget.sourceCropf.right - fbTarget.sourceCropf.left ||
                       composeTask->outHeight != fbTarget.sourceCropf.bottom - fbTarget.sourceCropf.top;
        }

        composeTask->outputHandle = mCscBuffers.get(composeTask->outWidth, composeTask->outHeight, &heldBuffer);
        if (composeTask->outputHandle == NULL) {
            WTRACE("Out of CSC buffers, dropping frame");
            return true;
        }
    } else {
        composeTask->outputHandle = display->outbuf;
    }
#else
    composeTask->outputHandle = display->outbuf;
#endif

    vspPrepare(composeTask->outWidth, composeTask->outHeight);

    composeTask->videoCachedBuffer = getMappedBuffer(yuvLayer.handle);
    if (composeTask->videoCachedBuffer == NULL) {
        ETRACE("Couldn't map video handle %p", yuvLayer.handle);
        return false;
    }
    if (composeTask->videoCachedBuffer->mapper == NULL) {
        ETRACE("Src mapper gone");
        return false;
    }
    composeTask->heldVideoBuffer = new HeldDecoderBuffer(this, composeTask->videoCachedBuffer);
    IVideoPayloadManager::MetaData videoMetadata;
    if (!mPayloadManager->getMetaData(composeTask->videoCachedBuffer->mapper, &videoMetadata)) {
        ETRACE("Failed to map video payload info");
        return false;
    }
    if (videoMetadata.normalBuffer.width == 0 || videoMetadata.normalBuffer.height == 0) {
        ETRACE("Bad video metadata for handle %p", yuvLayer.handle);
        return false;
    }
    if (videoMetadata.normalBuffer.khandle == 0) {
        ETRACE("Bad khandle");
        return false;
    }

    VARectangle& output_region = composeTask->output_region;
    output_region.x = static_cast<uint32_t>(yuvLayer.displayFrame.left*upscale_x) & ~1;
    output_region.y = static_cast<uint32_t>(yuvLayer.displayFrame.top*upscale_y) & ~1;
    output_region.width = (static_cast<uint32_t>(yuvLayer.displayFrame.right*upscale_y+1) & ~1) - output_region.x;
    output_region.height = (static_cast<uint32_t>(yuvLayer.displayFrame.bottom*upscale_y+1) & ~1) - output_region.y;

    uint32_t videoWidth;
    uint32_t videoHeight;
    if (videoMetadata.transform == 0 || videoMetadata.transform == HAL_TRANSFORM_ROT_180) {
        videoWidth = videoMetadata.normalBuffer.width;
        videoHeight = videoMetadata.normalBuffer.height;
    } else {
        videoWidth = videoMetadata.normalBuffer.height;
        videoHeight = videoMetadata.normalBuffer.width;
    }

    // Layer source crop info is based on an unrotated, unscaled buffer.
    // Rotate the rectangle to get the source crop we'd use for a rotated, unscaled buffer.
    hwc_frect_t rotatedCrop;
    switch (videoMetadata.transform) {
    default:
        rotatedCrop = yuvLayer.sourceCropf;
        break;
    case HAL_TRANSFORM_ROT_90:
        rotatedCrop.left = yuvLayer.sourceCropf.top;
        rotatedCrop.top = videoHeight - yuvLayer.sourceCropf.right;
        rotatedCrop.right = yuvLayer.sourceCropf.bottom;
        rotatedCrop.bottom = videoHeight - yuvLayer.sourceCropf.left;
        break;
    case HAL_TRANSFORM_ROT_180:
        rotatedCrop.left = videoWidth - yuvLayer.sourceCropf.right;
        rotatedCrop.top = videoHeight - yuvLayer.sourceCropf.bottom;
        rotatedCrop.right = videoWidth - yuvLayer.sourceCropf.left;
        rotatedCrop.bottom = videoHeight - yuvLayer.sourceCropf.top;
        break;
    case HAL_TRANSFORM_ROT_270:
        rotatedCrop.left = videoWidth - yuvLayer.sourceCropf.bottom;
        rotatedCrop.top = yuvLayer.sourceCropf.left;
        rotatedCrop.right = videoWidth - yuvLayer.sourceCropf.top;
        rotatedCrop.bottom = yuvLayer.sourceCropf.right;
        break;
    }

    float factor_x = output_region.width / (rotatedCrop.right - rotatedCrop.left);
    float factor_y = output_region.height / (rotatedCrop.bottom - rotatedCrop.top);

    uint32_t scaleWidth = videoWidth * factor_x;
    uint32_t scaleHeight = videoHeight * factor_y;

    scaleWidth &= ~1;
    scaleHeight &= ~1;

    IVideoPayloadManager::Buffer info;
    if (!getFrameOfSize(scaleWidth, scaleHeight, videoMetadata, info)) {
        //Returning true as else we fall into the queueColorConvert
        //resulting into scrambled frames for protected content.
        ITRACE("scaled frame not yet available.");
        return true;
    }

    composeTask->videoKhandle = info.khandle;
    composeTask->videoStride = info.lumaStride;
    composeTask->videoBufHeight = info.bufHeight;
    composeTask->videoTiled = info.tiled;

    // rotatedCrop accounts for rotation. Now account for any scaling along each dimension.
    hwc_frect_t scaledCrop = rotatedCrop;
    if (info.width < videoWidth) {
        float factor = static_cast<float>(info.width) / videoWidth;
        scaledCrop.left *= factor;
        scaledCrop.right *= factor;
    }
    if (info.height < videoHeight) {
        float factor = static_cast<float>(info.height) / videoHeight;
        scaledCrop.top *= factor;
        scaledCrop.bottom *= factor;
    }

    VARectangle& surface_region = composeTask->surface_region;
    surface_region.x = static_cast<int>(scaledCrop.left) + info.offsetX;
    surface_region.y = static_cast<int>(scaledCrop.top) + info.offsetY;
    surface_region.width = static_cast<int>(scaledCrop.right - scaledCrop.left);
    surface_region.height = static_cast<int>(scaledCrop.bottom - scaledCrop.top);

    VTRACE("Want to take (%d,%d)-(%d,%d) region from %dx%d video (in %dx%d buffer) and output to (%d,%d)-(%d,%d)",
            surface_region.x, surface_region.y,
            surface_region.x + surface_region.width, surface_region.y + surface_region.height,
            info.width, info.height,
            info.bufWidth, info.bufHeight,
            output_region.x, output_region.y,
            output_region.x + output_region.width, output_region.y + output_region.height);

    if (surface_region.x + surface_region.width > static_cast<int>(info.width + info.offsetX) ||
        surface_region.y + surface_region.height > static_cast<int>(info.height + info.offsetY))
    {
        ETRACE("Source crop exceeds video dimensions: (%d,%d)-(%d,%d) > %ux%u",
                surface_region.x, surface_region.y,
                surface_region.x + surface_region.width, surface_region.y + surface_region.height,
                info.width, info.height);
        return false;
    }

    if (surface_region.width > output_region.width || surface_region.height > output_region.height) {
        // VSP can upscale but can't downscale video, so use blank video
        // until we start getting downscaled frames.
        surface_region.x = 0;
        surface_region.y = 0;
        surface_region.width = composeTask->outWidth;
        surface_region.height = composeTask->outHeight;
        output_region = surface_region;
        composeTask->videoKhandle = 0;
        composeTask->videoStride = composeTask->outWidth;
        composeTask->videoBufHeight = composeTask->outHeight;
        composeTask->videoTiled = false;
    }

    composeTask->yuvAcquireFenceFd = yuvLayer.acquireFenceFd;
    yuvLayer.acquireFenceFd = -1;

    composeTask->outbufAcquireFenceFd = display->outbufAcquireFenceFd;
    display->outbufAcquireFenceFd = -1;

    int retireFd = sw_sync_fence_create(mSyncTimelineFd, "widi_compose_retire", mNextSyncPoint);
    yuvLayer.releaseFenceFd = retireFd;

    if (mRgbLayer == -1) {
        CLOSE_FENCE(fbTarget.acquireFenceFd);
    } else {
        hwc_layer_1_t& rgbLayer = display->hwLayers[mRgbLayer];
        composeTask->rgbAcquireFenceFd = rgbLayer.acquireFenceFd;
        rgbLayer.acquireFenceFd = -1;
        rgbLayer.releaseFenceFd = dup(retireFd);
    }

    mNextSyncPoint++;
    composeTask->syncTimelineFd = mSyncTimelineFd;

    if (mRgbLayer != -1)
    {
        hwc_layer_1_t& rgbLayer = display->hwLayers[mRgbLayer];
        if (rgbLayer.handle == NULL) {
            ETRACE("No RGB handle");
            return false;
        }

        if (scaleRgb) {
            buffer_handle_t scalingBuffer;
            sp<RefBase> heldUpscaleBuffer;
            while ((scalingBuffer = mRgbUpscaleBuffers.get(composeTask->outWidth, composeTask->outHeight, &heldUpscaleBuffer)) == NULL &&
                   !mTasks.empty()) {
                VTRACE("Waiting for free RGB upscale buffer...");
                mRequestDequeued.wait(mTaskLock);
            }
            if (scalingBuffer == NULL) {
                ETRACE("Couldn't get scaling buffer");
                return false;
            }
            BufferManager* mgr = mHwc.getBufferManager();
            crop_t destRect;
            destRect.x = 0;
            destRect.y = 0;
            destRect.w = composeTask->outWidth;
            destRect.h = composeTask->outHeight;
            if (!mgr->blit(rgbLayer.handle, scalingBuffer, destRect, true, true))
                return true;
            composeTask->rgbHandle = scalingBuffer;
            composeTask->heldRgbHandle = heldUpscaleBuffer;
        }
        else {
            unsigned int pixel_format = VA_FOURCC_BGRA;
            const IMG_native_handle_t* nativeHandle = reinterpret_cast<const IMG_native_handle_t*>(rgbLayer.handle);
            if (nativeHandle->iFormat == HAL_PIXEL_FORMAT_RGBA_8888)
                pixel_format = VA_FOURCC_RGBA;
            mRgbUpscaleBuffers.clear();
            ssize_t index = mVaMapCache.indexOfKey(rgbLayer.handle);
            if (index == NAME_NOT_FOUND) {
                composeTask->mappedRgbIn = new VAMappedHandleObject(va_dpy, rgbLayer.handle, composeTask->outWidth, composeTask->outHeight, pixel_format);
                mVaMapCache.add(rgbLayer.handle, composeTask->mappedRgbIn);
            }
            else
                composeTask->mappedRgbIn = mVaMapCache[index];
            if (composeTask->mappedRgbIn->surface == 0) {
                ETRACE("Unable to map RGB surface");
                return false;
            }
        }
    }
    else
        composeTask->mappedRgbIn = NULL;

    mTasks.push_back(composeTask);
    mRequestQueued.signal();
#ifdef INTEL_WIDI
    if (mCurrentConfig.frameServerActive) {

        FrameInfo inputFrameInfo;
        memset(&inputFrameInfo, 0, sizeof(inputFrameInfo));
        inputFrameInfo.isProtected = mProtectedMode;
        inputFrameInfo.frameType = HWC_FRAMETYPE_FRAME_BUFFER;
        if (mVspUpscale) {
            float upscale_x = (rotatedCrop.right - rotatedCrop.left) /
                              (yuvLayer.displayFrame.right - yuvLayer.displayFrame.left);
            float upscale_y = (rotatedCrop.bottom - rotatedCrop.top) /
                              (yuvLayer.displayFrame.bottom - yuvLayer.displayFrame.top);
            float upscale = upscale_x > upscale_y ? upscale_x : upscale_y;
            if (upscale <= 1.0)
                upscale = 1.0;
            inputFrameInfo.contentWidth = (fbTarget.sourceCropf.right - fbTarget.sourceCropf.left)*upscale;
            inputFrameInfo.contentHeight = (fbTarget.sourceCropf.bottom - fbTarget.sourceCropf.top)*upscale;
        }
        else {
            inputFrameInfo.contentWidth = composeTask->outWidth;
            inputFrameInfo.contentHeight = composeTask->outHeight;
        }
        inputFrameInfo.contentFrameRateN = 0;
        inputFrameInfo.contentFrameRateD = 0;
        FrameInfo outputFrameInfo = inputFrameInfo;

        BufferManager* mgr = mHwc.getBufferManager();
        DataBuffer* dataBuf = mgr->lockDataBuffer(composeTask->outputHandle);
        outputFrameInfo.contentWidth = composeTask->outWidth;
        outputFrameInfo.contentHeight = composeTask->outHeight;
        outputFrameInfo.bufferWidth = dataBuf->getWidth();
        outputFrameInfo.bufferHeight = dataBuf->getHeight();
        outputFrameInfo.lumaUStride = dataBuf->getWidth();
        outputFrameInfo.chromaUStride = dataBuf->getWidth();
        outputFrameInfo.chromaVStride = dataBuf->getWidth();
        mgr->unlockDataBuffer(dataBuf);

        queueFrameTypeInfo(inputFrameInfo);
        if (mCurrentConfig.policy.scaledWidth == 0 || mCurrentConfig.policy.scaledHeight == 0)
            return true; // This isn't a failure, WiDi just doesn't want frames right now.
        queueBufferInfo(outputFrameInfo);

        if (mCurrentConfig.frameListener != NULL) {
            frameReadyTask = new OnFrameReadyTask();
            frameReadyTask->renderTask = composeTask;
            frameReadyTask->heldBuffer = heldBuffer;
            frameReadyTask->frameListener = mCurrentConfig.frameListener;
            frameReadyTask->handle = composeTask->outputHandle;
            frameReadyTask->handleType = HWC_HANDLE_TYPE_GRALLOC;
            frameReadyTask->renderTimestamp = mRenderTimestamp;
            frameReadyTask->mediaTimestamp = -1;
            mTasks.push_back(frameReadyTask);
        }
    }
    else {
        display->retireFenceFd = dup(retireFd);
    }
#else
    display->retireFenceFd = dup(retireFd);
#endif

    return true;
}

bool VirtualDevice::queueColorConvert(hwc_display_contents_1_t *display)
{
    if (mRgbLayer == -1) {
        ETRACE("RGB layer not set");
        return false;
    }
    hwc_layer_1_t& layer = display->hwLayers[mRgbLayer];
    if (layer.handle == NULL) {
        ETRACE("RGB layer has no handle set");
        return false;
    }
    if (display->outbuf == NULL) {
        ETRACE("outbuf is not set");
        return false;
    }

    {
        const IMG_native_handle_t* nativeSrcHandle = reinterpret_cast<const IMG_native_handle_t*>(layer.handle);
        const IMG_native_handle_t* nativeDestHandle = reinterpret_cast<const IMG_native_handle_t*>(display->outbuf);

        if ((nativeSrcHandle->iFormat == HAL_PIXEL_FORMAT_RGBA_8888 &&
            nativeDestHandle->iFormat == HAL_PIXEL_FORMAT_BGRA_8888) ||
            (nativeSrcHandle->iFormat == HAL_PIXEL_FORMAT_BGRA_8888 &&
            nativeDestHandle->iFormat == HAL_PIXEL_FORMAT_RGBA_8888))
        {
            SYNC_WAIT_AND_CLOSE(layer.acquireFenceFd);
            SYNC_WAIT_AND_CLOSE(display->outbufAcquireFenceFd);
            display->retireFenceFd = -1;

            // synchronous in this case
            colorSwap(layer.handle, display->outbuf, ((nativeSrcHandle->iWidth+31)&~31)*nativeSrcHandle->iHeight);
            // Workaround: Don't keep cached buffers. If the VirtualDisplaySurface gets destroyed,
            //             these would be unmapped on the next frame, after the buffers are destroyed,
            //             which is causing heap corruption, probably due to a double-free somewhere.
            mMappedBufferCache.clear();
            return true;
        }
    }

    sp<BlitTask> blitTask = new BlitTask();
    sp<OnFrameReadyTask> frameReadyTask;
    blitTask->destRect.x = 0;
    blitTask->destRect.y = 0;
    blitTask->destRect.w = layer.sourceCropf.right - layer.sourceCropf.left;
    blitTask->destRect.h = layer.sourceCropf.bottom - layer.sourceCropf.top;
    blitTask->srcHandle = layer.handle;

    sp<RefBase> heldBuffer;
    Mutex::Autolock _l(mTaskLock);

    blitTask->srcAcquireFenceFd = layer.acquireFenceFd;
    layer.acquireFenceFd = -1;

    blitTask->syncTimelineFd = mSyncTimelineFd;
    // Framebuffer after BlitTask::run() calls sw_sync_timeline_inc().
    layer.releaseFenceFd = sw_sync_fence_create(mSyncTimelineFd, "widi_blit_retire", mNextSyncPoint);
    mNextSyncPoint++;
#ifdef INTEL_WIDI
    if (mCurrentConfig.frameServerActive) {
        blitTask->destHandle = mCscBuffers.get(blitTask->destRect.w, blitTask->destRect.h, &heldBuffer);
        blitTask->destAcquireFenceFd = -1;

        // we do not use retire fence in frameServerActive path.
        CLOSE_FENCE(display->retireFenceFd);

        // we use our own buffer, so just close this fence without a wait
        CLOSE_FENCE(display->outbufAcquireFenceFd);
    }
    else {
        blitTask->destHandle = display->outbuf;
        blitTask->destAcquireFenceFd = display->outbufAcquireFenceFd;
        // don't let TngDisplayContext::commitEnd() close this
        display->outbufAcquireFenceFd = -1;
        display->retireFenceFd = dup(layer.releaseFenceFd);
    }
#else
    blitTask->destHandle = display->outbuf;
    blitTask->destAcquireFenceFd = display->outbufAcquireFenceFd;
    // don't let TngDisplayContext::commitEnd() close this
    display->outbufAcquireFenceFd = -1;
    display->retireFenceFd = dup(layer.releaseFenceFd);
#endif
    if (blitTask->destHandle == NULL) {
        WTRACE("Out of CSC buffers, dropping frame");
        return false;
    }

    mTasks.push_back(blitTask);
    mRequestQueued.signal();
#ifdef INTEL_WIDI
    if (mCurrentConfig.frameServerActive) {
        FrameInfo inputFrameInfo;
        memset(&inputFrameInfo, 0, sizeof(inputFrameInfo));
        inputFrameInfo.isProtected = mProtectedMode;
        FrameInfo outputFrameInfo;

        inputFrameInfo.frameType = HWC_FRAMETYPE_FRAME_BUFFER;
        inputFrameInfo.contentWidth = blitTask->destRect.w;
        inputFrameInfo.contentHeight = blitTask->destRect.h;
        inputFrameInfo.contentFrameRateN = 0;
        inputFrameInfo.contentFrameRateD = 0;
        outputFrameInfo = inputFrameInfo;

        BufferManager* mgr = mHwc.getBufferManager();
        DataBuffer* dataBuf = mgr->lockDataBuffer(blitTask->destHandle);
        outputFrameInfo.bufferWidth = dataBuf->getWidth();
        outputFrameInfo.bufferHeight = dataBuf->getHeight();
        outputFrameInfo.lumaUStride = dataBuf->getWidth();
        outputFrameInfo.chromaUStride = dataBuf->getWidth();
        outputFrameInfo.chromaVStride = dataBuf->getWidth();
        mgr->unlockDataBuffer(dataBuf);

        if (!mIsForceCloneMode)
            queueFrameTypeInfo(inputFrameInfo);

        if (mCurrentConfig.policy.scaledWidth == 0 || mCurrentConfig.policy.scaledHeight == 0)
            return true; // This isn't a failure, WiDi just doesn't want frames right now.
        queueBufferInfo(outputFrameInfo);

        if (mCurrentConfig.frameListener != NULL) {
            frameReadyTask = new OnFrameReadyTask();
            frameReadyTask->renderTask = blitTask;
            frameReadyTask->heldBuffer = heldBuffer;
            frameReadyTask->frameListener = mCurrentConfig.frameListener;
            frameReadyTask->handle = blitTask->destHandle;
            frameReadyTask->handleType = HWC_HANDLE_TYPE_GRALLOC;
            frameReadyTask->renderTimestamp = mRenderTimestamp;
            frameReadyTask->mediaTimestamp = -1;
            mTasks.push_back(frameReadyTask);
        }
    }
#endif
    return true;
}
#ifdef INTEL_WIDI
bool VirtualDevice::handleExtendedMode(hwc_display_contents_1_t *display)
{
    FrameInfo inputFrameInfo;
    memset(&inputFrameInfo, 0, sizeof(inputFrameInfo));
    inputFrameInfo.isProtected = mProtectedMode;

    hwc_layer_1_t& layer = display->hwLayers[mYuvLayer];
    if (layer.handle == NULL) {
        ETRACE("video layer has no handle set");
        return false;
    }
    sp<CachedBuffer> cachedBuffer;
    if ((cachedBuffer = getMappedBuffer(layer.handle)) == NULL) {
        ETRACE("Failed to map display buffer");
        return false;
    }

    inputFrameInfo.frameType = HWC_FRAMETYPE_VIDEO;
    // for video mode let 30 fps be the default value.
    inputFrameInfo.contentFrameRateN = 30;
    inputFrameInfo.contentFrameRateD = 1;

    IVideoPayloadManager::MetaData metadata;
    if (!mPayloadManager->getMetaData(cachedBuffer->mapper, &metadata)) {
        ETRACE("Failed to get metadata");
        return false;
    }

    if (metadata.transform == 0 || metadata.transform == HAL_TRANSFORM_ROT_180) {
        inputFrameInfo.contentWidth = metadata.normalBuffer.width;
        inputFrameInfo.contentHeight = metadata.normalBuffer.height;
    } else {
        inputFrameInfo.contentWidth = metadata.normalBuffer.height;
        inputFrameInfo.contentHeight = metadata.normalBuffer.width;
        // 90 and 270 have some issues that appear to be decoder bugs
        ITRACE("Skipping extended mode due to rotation of 90 or 270");
        return false;
    }
    // Use the crop size if something changed derive it again..
    // Only get video source info if frame rate has not been initialized.
    // getVideoSourceInfo() is a fairly expensive operation. This optimization
    // will save us a few milliseconds per frame
    if (mFirstVideoFrame || (mOrigContentWidth != metadata.normalBuffer.width) ||
        (mOrigContentHeight != metadata.normalBuffer.height)) {
        mVideoFramerate = inputFrameInfo.contentFrameRateN;
        VTRACE("VideoWidth = %d, VideoHeight = %d", metadata.normalBuffer.width, metadata.normalBuffer.height);
        mOrigContentWidth = metadata.normalBuffer.width;
        mOrigContentHeight = metadata.normalBuffer.height;

        // For the first video session by default
        int sessionID = Hwcomposer::getInstance().getDisplayAnalyzer()->getFirstVideoInstanceSessionID();
        if (sessionID >= 0) {
            ITRACE("Session id = %d", sessionID);
            VideoSourceInfo videoInfo;
            memset(&videoInfo, 0, sizeof(videoInfo));
            status_t ret = mHwc.getMultiDisplayObserver()->getVideoSourceInfo(sessionID, &videoInfo);
            if (ret == NO_ERROR) {
                ITRACE("width = %d, height = %d, fps = %d", videoInfo.width, videoInfo.height,
                        videoInfo.frameRate);
                if (videoInfo.frameRate > 0) {
                    mVideoFramerate = videoInfo.frameRate;
                }
            }
        }
        mFirstVideoFrame = false;
    }
    inputFrameInfo.contentFrameRateN = mVideoFramerate;
    inputFrameInfo.contentFrameRateD = 1;

    sp<ComposeTask> composeTask;
    sp<RefBase> heldBuffer;
    Mutex::Autolock _l(mTaskLock);

    if (mCurrentConfig.policy.scaledWidth == 0 || mCurrentConfig.policy.scaledHeight == 0) {
        queueFrameTypeInfo(inputFrameInfo);
        return true; // This isn't a failure, WiDi just doesn't want frames right now.
    }

    IVideoPayloadManager::Buffer info;
    if (!getFrameOfSize(mCurrentConfig.policy.scaledWidth, mCurrentConfig.policy.scaledHeight, metadata, info)) {
        ITRACE("Extended mode waiting for scaled frame");
        return false;
    }

    queueFrameTypeInfo(inputFrameInfo);

    heldBuffer = new HeldDecoderBuffer(this, cachedBuffer);
    int64_t mediaTimestamp = metadata.timestamp;

    VARectangle surface_region;
    surface_region.x = info.offsetX;
    surface_region.y = info.offsetY;
    surface_region.width = info.width;
    surface_region.height = info.height;
    FrameInfo outputFrameInfo = inputFrameInfo;
    outputFrameInfo.bufferFormat = metadata.format;

    outputFrameInfo.contentWidth = info.width;
    outputFrameInfo.contentHeight = info.height;
    outputFrameInfo.bufferWidth = info.bufWidth;
    outputFrameInfo.bufferHeight = info.bufHeight;
    outputFrameInfo.lumaUStride = info.lumaStride;
    outputFrameInfo.chromaUStride = info.chromaUStride;
    outputFrameInfo.chromaVStride = info.chromaVStride;

    if (outputFrameInfo.bufferFormat == 0 ||
        outputFrameInfo.bufferWidth < outputFrameInfo.contentWidth ||
        outputFrameInfo.bufferHeight < outputFrameInfo.contentHeight ||
        outputFrameInfo.contentWidth <= 0 || outputFrameInfo.contentHeight <= 0 ||
        outputFrameInfo.lumaUStride <= 0 ||
        outputFrameInfo.chromaUStride <= 0 || outputFrameInfo.chromaVStride <= 0) {
        ITRACE("Payload cleared or inconsistent info, not sending frame");
        ITRACE("outputFrameInfo.bufferFormat  = %d ", outputFrameInfo.bufferFormat);
        ITRACE("outputFrameInfo.bufferWidth   = %d ", outputFrameInfo.bufferWidth);
        ITRACE("outputFrameInfo.contentWidth  = %d ", outputFrameInfo.contentWidth);
        ITRACE("outputFrameInfo.bufferHeight  = %d ", outputFrameInfo.bufferHeight);
        ITRACE("outputFrameInfo.contentHeight = %d ", outputFrameInfo.contentHeight);
        ITRACE("outputFrameInfo.lumaUStride   = %d ", outputFrameInfo.lumaUStride);
        ITRACE("outputFrameInfo.chromaUStride = %d ", outputFrameInfo.chromaUStride);
        ITRACE("outputFrameInfo.chromaVStride = %d ", outputFrameInfo.chromaVStride);
        return false;
    }

    if (mCurrentConfig.policy.scaledWidth == 0 || mCurrentConfig.policy.scaledHeight == 0)
        return true; // This isn't a failure, WiDi just doesn't want frames right now.

    if (info.khandle == mExtLastKhandle && mediaTimestamp == mExtLastTimestamp) {
        // Same frame again. We don't send a frame, but we return true because
        // this isn't an error.
        if (metadata.transform != 0)
            mVspInUse = true; // Don't shut down VSP just to start it again really quick.
        return true;
    }
    mExtLastKhandle = info.khandle;
    mExtLastTimestamp = mediaTimestamp;

    HWCBufferHandleType handleType = HWC_HANDLE_TYPE_KBUF;

    buffer_handle_t handle = info.khandle;

    // Ideally we'd check if there's an offset (info.offsetX > 0 || info.offsetY > 0),
    // so we use VSP only when cropping is needed. But using the khandle directly when
    // both rotation and scaling are involved can encode the frame with the wrong
    // tiling status, so use VSP to normalize if any rotation is involved.
    if (metadata.transform != 0) {
        // Cropping (or above workaround) needed, so use VSP to do it.
        mVspInUse = true;
        vspPrepare(info.width, info.height);

        composeTask = new ComposeTask();
        composeTask->heldVideoBuffer = heldBuffer;
        heldBuffer = NULL;
        composeTask->outWidth = info.width;
        composeTask->outHeight = info.height;
        composeTask->outputHandle = mCscBuffers.get(composeTask->outWidth, composeTask->outHeight, &heldBuffer);
        if (composeTask->outputHandle == NULL) {
            ITRACE("Out of CSC buffers, dropping frame");
            return true;
        }

        composeTask->surface_region = surface_region;
        composeTask->videoCachedBuffer = cachedBuffer;
        VARectangle& output_region = composeTask->output_region;
        output_region.x = 0;
        output_region.y = 0;
        output_region.width = info.width;
        output_region.height = info.height;

        composeTask->videoKhandle = info.khandle;
        composeTask->videoStride = info.lumaStride;
        composeTask->videoBufHeight = info.bufHeight;
        composeTask->videoTiled = info.tiled;

        BufferManager* mgr = mHwc.getBufferManager();
        DataBuffer* dataBuf = mgr->lockDataBuffer(composeTask->outputHandle);
        outputFrameInfo.contentWidth = composeTask->outWidth;
        outputFrameInfo.contentHeight = composeTask->outHeight;
        outputFrameInfo.bufferWidth = dataBuf->getWidth();
        outputFrameInfo.bufferHeight = dataBuf->getHeight();
        outputFrameInfo.lumaUStride = dataBuf->getWidth();
        outputFrameInfo.chromaUStride = dataBuf->getWidth();
        outputFrameInfo.chromaVStride = dataBuf->getWidth();
        mgr->unlockDataBuffer(dataBuf);

        handle = composeTask->outputHandle;
        handleType = HWC_HANDLE_TYPE_GRALLOC;

        mTasks.push_back(composeTask);
        mRequestQueued.signal();
    }

    queueBufferInfo(outputFrameInfo);

    if (mCurrentConfig.frameListener != NULL) {
        sp<OnFrameReadyTask> frameReadyTask = new OnFrameReadyTask();
        frameReadyTask->renderTask = composeTask;
        frameReadyTask->heldBuffer = heldBuffer;
        frameReadyTask->frameListener = mCurrentConfig.frameListener;
        frameReadyTask->handle = handle;
        frameReadyTask->handleType = handleType;
        frameReadyTask->renderTimestamp = mRenderTimestamp;
        frameReadyTask->mediaTimestamp = mediaTimestamp;

        mTasks.push_back(frameReadyTask);
        mRequestQueued.signal();
    }

    return true;
}

void VirtualDevice::queueFrameTypeInfo(const FrameInfo& inputFrameInfo)
{
    if (mCurrentConfig.forceNotifyFrameType ||
        memcmp(&inputFrameInfo, &mLastInputFrameInfo, sizeof(inputFrameInfo)) != 0) {
        // something changed, notify type change listener
        mNextConfig.forceNotifyFrameType = false;
        mLastInputFrameInfo = inputFrameInfo;

        sp<FrameTypeChangedTask> notifyTask = new FrameTypeChangedTask;
        notifyTask->typeChangeListener = mCurrentConfig.typeChangeListener;
        notifyTask->inputFrameInfo = inputFrameInfo;
        mTasks.push_back(notifyTask);
    }
}

void VirtualDevice::queueBufferInfo(const FrameInfo& outputFrameInfo)
{
    if (mCurrentConfig.forceNotifyBufferInfo ||
        memcmp(&outputFrameInfo, &mLastOutputFrameInfo, sizeof(outputFrameInfo)) != 0) {
        mNextConfig.forceNotifyBufferInfo = false;
        mLastOutputFrameInfo = outputFrameInfo;

        sp<BufferInfoChangedTask> notifyTask = new BufferInfoChangedTask;
        notifyTask->typeChangeListener = mCurrentConfig.typeChangeListener;
        notifyTask->outputFrameInfo = outputFrameInfo;

        //if (handleType == HWC_HANDLE_TYPE_GRALLOC)
        //    mMappedBufferCache.clear(); // !
        mTasks.push_back(notifyTask);
    }
}
#endif

void VirtualDevice::colorSwap(buffer_handle_t src, buffer_handle_t dest, uint32_t pixelCount)
{
    sp<CachedBuffer> srcCachedBuffer;
    sp<CachedBuffer> destCachedBuffer;

    {
        srcCachedBuffer = getMappedBuffer(src);
        if (srcCachedBuffer == NULL || srcCachedBuffer->mapper == NULL)
            return;
        destCachedBuffer = getMappedBuffer(dest);
        if (destCachedBuffer == NULL || destCachedBuffer->mapper == NULL)
            return;
    }

    uint8_t* srcPtr = static_cast<uint8_t*>(srcCachedBuffer->mapper->getCpuAddress(0));
    uint8_t* destPtr = static_cast<uint8_t*>(destCachedBuffer->mapper->getCpuAddress(0));
    if (srcPtr == NULL || destPtr == NULL)
        return;
    while (pixelCount > 0) {
        destPtr[0] = srcPtr[2];
        destPtr[1] = srcPtr[1];
        destPtr[2] = srcPtr[0];
        destPtr[3] = srcPtr[3];
        srcPtr += 4;
        destPtr += 4;
        pixelCount--;
    }
}

void VirtualDevice::vspPrepare(uint32_t width, uint32_t height)
{
    if (mVspEnabled && width == mVspWidth && height == mVspHeight)
        return;

    if (mVspEnabled)
    {
        ITRACE("Going to switch VSP from %ux%u to %ux%u", mVspWidth, mVspHeight, width, height);
        mMappedBufferCache.clear();
        mVaMapCache.clear();
        sp<DisableVspTask> disableVsp = new DisableVspTask();
        mTasks.push_back(disableVsp);
    }
    mVspWidth = width;
    mVspHeight = height;

    sp<EnableVspTask> enableTask = new EnableVspTask();
    enableTask->width = width;
    enableTask->height = height;
    mTasks.push_back(enableTask);
    mRequestQueued.signal();
    // to map a buffer from this thread, we need this task to complete on the other thread
    while (enableTask->getStrongCount() > 1) {
        VTRACE("Waiting for WidiBlit thread to enable VSP...");
        mRequestDequeued.wait(mTaskLock);
    }
    mVspEnabled = true;
}

void VirtualDevice::vspEnable(uint32_t width, uint32_t height)
{
    width = align_width(width);
    height = align_height(height);
    ITRACE("Start VSP at %ux%u", width, height);
    VAStatus va_status;

    int display = 0;
    int major_ver, minor_ver;
    va_dpy = vaGetDisplay(&display);
    va_status = vaInitialize(va_dpy, &major_ver, &minor_ver);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaInitialize returns %08x", va_status);

    VAConfigAttrib va_attr;
    va_attr.type = VAConfigAttribRTFormat;
    va_status = vaGetConfigAttributes(va_dpy,
                VAProfileNone,
                VAEntrypointVideoProc,
                &va_attr,
                1);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaGetConfigAttributes returns %08x", va_status);

    va_status = vaCreateConfig(
                va_dpy,
                VAProfileNone,
                VAEntrypointVideoProc,
                &(va_attr),
                1,
                &va_config
                );
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaCreateConfig returns %08x", va_status);

    VADisplayAttribute attr;
    attr.type = VADisplayAttribRenderMode;
    attr.value = VA_RENDER_MODE_LOCAL_OVERLAY;
    va_status = vaSetDisplayAttributes(va_dpy, &attr, 1);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaSetDisplayAttributes returns %08x", va_status);


    va_status = vaCreateSurfaces(
                va_dpy,
                VA_RT_FORMAT_YUV420,
                width,
                height,
                &va_blank_yuv_in,
                1,
                NULL,
                0);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaCreateSurfaces (video in) returns %08x", va_status);

    unsigned long buffer;
    VASurfaceAttribExternalBuffers buf;
    int stride = align_width(width);
    int bufHeight = align_height(height);
    buf.pixel_format = VA_FOURCC_RGBA;
    buf.width = width;
    buf.height = height;
    buf.data_size = stride * bufHeight * 4;
    buf.num_planes = 3;
    buf.pitches[0] = stride;
    buf.pitches[1] = stride;
    buf.pitches[2] = stride;
    buf.pitches[3] = 0;
    buf.offsets[0] = 0;
    buf.offsets[1] = stride * bufHeight;
    buf.offsets[2] = buf.offsets[1];
    buf.offsets[3] = 0;
    buf.buffers = &buffer;
    buf.num_buffers = 1;
    buf.flags = 0;
    buf.private_data = NULL;

    VASurfaceAttrib attrib_list[2];
    attrib_list[0].type = (VASurfaceAttribType)VASurfaceAttribMemoryType;
    attrib_list[0].flags = VA_SURFACE_ATTRIB_SETTABLE;
    attrib_list[0].value.type = VAGenericValueTypeInteger;
    attrib_list[0].value.value.i = VA_SURFACE_ATTRIB_MEM_TYPE_VA;
    attrib_list[1].type = (VASurfaceAttribType)VASurfaceAttribExternalBufferDescriptor;
    attrib_list[1].flags = VA_SURFACE_ATTRIB_SETTABLE;
    attrib_list[1].value.type = VAGenericValueTypePointer;
    attrib_list[1].value.value.p = (void *)&buf;

    va_status = vaCreateSurfaces(
                va_dpy,
                VA_RT_FORMAT_RGB32,
                stride,
                bufHeight,
                &va_blank_rgb_in,
                1,
                attrib_list,
                2);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaCreateSurfaces (blank rgba in) returns %08x", va_status);

    va_status = vaCreateContext(
                va_dpy,
                va_config,
                stride,
                bufHeight,
                0,
                &va_blank_yuv_in /* not used by VSP, but libva checks for it */,
                1,
                &va_context);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaCreateContext returns %08x", va_status);

    VASurfaceID tmp_yuv;
    va_status = vaCreateSurfaces(
                va_dpy,
                VA_RT_FORMAT_YUV420,
                stride,
                bufHeight,
                &tmp_yuv,
                1,
                NULL,
                0);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaCreateSurfaces (temp yuv) returns %08x", va_status);
    {
        MappedSurface mappedVideoIn(va_dpy, tmp_yuv);
        if (mappedVideoIn.valid()) {
            // Value doesn't matter, as RGBA will be opaque,
            // but I don't want random data in here.
            memset(mappedVideoIn.getPtr(), 0x0, width*height*3/2);
        }
        else
            ETRACE("Unable to map tmp black surface");
    }

    {
        MappedSurface mappedBlankIn(va_dpy, va_blank_rgb_in);
        if (mappedBlankIn.valid()) {
            // Fill RGBA with opaque black temporarily, in order to generate an
            // encrypted black buffer in va_blank_yuv_in to use in place of the
            // real frame data during the short interval where we're waiting for
            // downscaling to kick in.
            uint32_t* pixels = reinterpret_cast<uint32_t*>(mappedBlankIn.getPtr());
            for (size_t i = 0; i < stride*height; i++)
                pixels[i] = 0xff000000;
        }
        else
            ETRACE("Unable to map blank rgba in");
    }

    // Compose opaque black with temp yuv to produce encrypted black yuv.
    VARectangle region;
    region.x = 0;
    region.y = 0;
    region.width = width;
    region.height = height;
    vspCompose(tmp_yuv, va_blank_rgb_in, va_blank_yuv_in, &region, &region);

    va_status = vaDestroySurfaces(va_dpy, &tmp_yuv, 1);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaDestroySurfaces (temp yuv) returns %08x", va_status);

    {
        // Fill RGBA with transparent black now, to be used when there is no
        // UI to compose on top of the video.
        MappedSurface mappedBlankIn(va_dpy, va_blank_rgb_in);
        if (mappedBlankIn.valid())
            memset(mappedBlankIn.getPtr(), 0, stride*height*4);
        else
            ETRACE("Unable to map blank rgba in");
    }
}

void VirtualDevice::vspDisable()
{
    ITRACE("Shut down VSP");

    if (va_context == 0 && va_blank_yuv_in == 0) {
        ITRACE("Already shut down");
        return;
    }

    VABufferID pipeline_param_id;
    VAStatus va_status;
    va_status = vaCreateBuffer(va_dpy,
                va_context,
                VAProcPipelineParameterBufferType,
                sizeof(VAProcPipelineParameterBuffer),
                1,
                NULL,
                &pipeline_param_id);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaCreateBuffer returns %08x", va_status);

    VABlendState blend_state;
    VAProcPipelineParameterBuffer *pipeline_param;
    va_status = vaMapBuffer(va_dpy,
                pipeline_param_id,
                (void **)&pipeline_param);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaMapBuffer returns %08x", va_status);

    memset(pipeline_param, 0, sizeof(VAProcPipelineParameterBuffer));
    pipeline_param->pipeline_flags = VA_PIPELINE_FLAG_END;
    pipeline_param->num_filters = 0;
    pipeline_param->blend_state = &blend_state;

    va_status = vaUnmapBuffer(va_dpy, pipeline_param_id);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaUnmapBuffer returns %08x", va_status);

    va_status = vaBeginPicture(va_dpy, va_context, va_blank_yuv_in /* just need some valid surface */);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaBeginPicture returns %08x", va_status);

    va_status = vaRenderPicture(va_dpy, va_context, &pipeline_param_id, 1);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaRenderPicture returns %08x", va_status);

    va_status = vaEndPicture(va_dpy, va_context);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaEndPicture returns %08x", va_status);

    va_status = vaDestroyContext(va_dpy, va_context);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaDestroyContext returns %08x", va_status);
    va_context = 0;

    va_status = vaDestroySurfaces(va_dpy, &va_blank_yuv_in, 1);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaDestroySurfaces (video in) returns %08x", va_status);
    va_blank_yuv_in = 0;

    va_status = vaDestroySurfaces(va_dpy, &va_blank_rgb_in, 1);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaDestroySurfaces (blank rgba in) returns %08x", va_status);

    if (va_config) {
        vaDestroyConfig(va_dpy, va_config);
        va_config = 0;
    }
    if (va_dpy) {
        vaTerminate(va_dpy);
        va_dpy = NULL;
    }
}

void VirtualDevice::vspCompose(VASurfaceID videoIn, VASurfaceID rgbIn, VASurfaceID videoOut,
                               const VARectangle* surface_region, const VARectangle* output_region)
{
    VAStatus va_status;

    VABufferID pipeline_param_id;
    va_status = vaCreateBuffer(va_dpy,
                va_context,
                VAProcPipelineParameterBufferType,
                sizeof(VAProcPipelineParameterBuffer),
                1,
                NULL,
                &pipeline_param_id);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaCreateBuffer returns %08x", va_status);

    VABlendState blend_state;

    VAProcPipelineParameterBuffer *pipeline_param;
    va_status = vaMapBuffer(va_dpy,
                pipeline_param_id,
                (void **)&pipeline_param);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaMapBuffer returns %08x", va_status);

    memset(pipeline_param, 0, sizeof(VAProcPipelineParameterBuffer));
    pipeline_param->surface = videoIn;
    pipeline_param->surface_region = surface_region;
    pipeline_param->output_region = output_region;

    pipeline_param->pipeline_flags = 0;
    pipeline_param->num_filters = 0;
    pipeline_param->blend_state = &blend_state;
    pipeline_param->num_additional_outputs = 1;
    pipeline_param->additional_outputs = &rgbIn;

    va_status = vaUnmapBuffer(va_dpy, pipeline_param_id);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaUnmapBuffer returns %08x", va_status);

    va_status = vaBeginPicture(va_dpy, va_context, videoOut);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaBeginPicture returns %08x", va_status);

    va_status = vaRenderPicture(va_dpy, va_context, &pipeline_param_id, 1);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaRenderPicture returns %08x", va_status);

    va_status = vaEndPicture(va_dpy, va_context);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaEndPicture returns %08x", va_status);

    va_status = vaSyncSurface(va_dpy, videoOut);
    if (va_status != VA_STATUS_SUCCESS) ETRACE("vaSyncSurface returns %08x", va_status);
}

static uint32_t min(uint32_t a, uint32_t b)
{
    return (a < b) ? a : b;
}

bool VirtualDevice::getFrameOfSize(uint32_t width, uint32_t height, const IVideoPayloadManager::MetaData& metadata, IVideoPayloadManager::Buffer& info)
{
    if (metadata.transform == 0 || metadata.transform == HAL_TRANSFORM_ROT_180)
        setMaxDecodeResolution(min(width, metadata.normalBuffer.width), min(height, metadata.normalBuffer.height));
    else
        setMaxDecodeResolution(min(height, metadata.normalBuffer.width), min(width, metadata.normalBuffer.height));

    if (metadata.transform == 0) {
        if (metadata.normalBuffer.khandle != 0 && metadata.normalBuffer.width <= width && metadata.normalBuffer.height <= height) {
            info = metadata.normalBuffer;
            return true;
        }

        if (metadata.scalingBuffer.khandle != 0 && metadata.scalingBuffer.width <= width && metadata.scalingBuffer.height <= height) {
            info = metadata.scalingBuffer;
            return true;
        }
    } else {
        if (metadata.rotationBuffer.khandle != 0 && metadata.rotationBuffer.width <= width && metadata.rotationBuffer.height <= height) {
            info = metadata.rotationBuffer;
            return true;
        }
    }

    return false;
}

void VirtualDevice::setMaxDecodeResolution(uint32_t width, uint32_t height)
{
    if (mDecWidth == width && mDecHeight == height)
        return;

    int sessionID = mHwc.getDisplayAnalyzer()->getFirstVideoInstanceSessionID();
    if (sessionID < 0) {
        ETRACE("Session id is less than 0");
        return;
    }

    MultiDisplayObserver* mds = mHwc.getMultiDisplayObserver();
    status_t ret = mds->setDecoderOutputResolution(sessionID, width, height, 0, 0, width, height);
    if (ret != NO_ERROR) {
        ETRACE("Failed to set scaling to %ux%u: %x", width, height, ret);
        return;
    }

    mDecWidth = width;
    mDecHeight = height;
    ITRACE("Set scaling to %ux%u",mDecWidth, mDecHeight);
}

bool VirtualDevice::vsyncControl(bool enabled)
{
    RETURN_FALSE_IF_NOT_INIT();
    return mVsyncObserver->control(enabled);
}

bool VirtualDevice::blank(bool blank)
{
    RETURN_FALSE_IF_NOT_INIT();
    return true;
}

bool VirtualDevice::getDisplaySize(int *width, int *height)
{
    RETURN_FALSE_IF_NOT_INIT();
    if (!width || !height) {
        ETRACE("invalid parameters");
        return false;
    }

    // TODO: make this platform specifc
    *width = 1280;
    *height = 720;
    return true;
}

bool VirtualDevice::getDisplayConfigs(uint32_t *configs,
                                         size_t *numConfigs)
{
    RETURN_FALSE_IF_NOT_INIT();
    if (!configs || !numConfigs) {
        ETRACE("invalid parameters");
        return false;
    }

    *configs = 0;
    *numConfigs = 1;

    return true;
}

bool VirtualDevice::getDisplayAttributes(uint32_t configs,
                                            const uint32_t *attributes,
                                            int32_t *values)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (!attributes || !values) {
        ETRACE("invalid parameters");
        return false;
    }

    int i = 0;
    while (attributes[i] != HWC_DISPLAY_NO_ATTRIBUTE) {
        switch (attributes[i]) {
        case HWC_DISPLAY_VSYNC_PERIOD:
            values[i] = 1e9 / 60;
            break;
        case HWC_DISPLAY_WIDTH:
            values[i] = 1280;
            break;
        case HWC_DISPLAY_HEIGHT:
            values[i] = 720;
            break;
        case HWC_DISPLAY_DPI_X:
            values[i] = 0;
            break;
        case HWC_DISPLAY_DPI_Y:
            values[i] = 0;
            break;
        default:
            ETRACE("unknown attribute %d", attributes[i]);
            break;
        }
        i++;
    }

    return true;
}

bool VirtualDevice::compositionComplete()
{
    RETURN_FALSE_IF_NOT_INIT();
    return true;
}

bool VirtualDevice::initialize()
{
    mRgbLayer = -1;
    mYuvLayer = -1;
    char prop[PROPERTY_VALUE_MAX];
    char *retptr;

    if (property_get("hwc.fps_divider", prop, "1") > 0) {
        uint32_t divider = strtoul(prop, &retptr, 10);
        if (*retptr == '\0' && divider > 1 && divider < 60) {
            mFpsDivider = divider;
            ALOGI("Virtual display, setting HWC FPS divider to %d", mFpsDivider);
        }
    }

#ifdef INTEL_WIDI
    // Add initialization codes here. If init fails, invoke DEINIT_AND_RETURN_FALSE();
    mNextConfig.typeChangeListener = NULL;
    mNextConfig.policy.scaledWidth = 0;
    mNextConfig.policy.scaledHeight = 0;
    mNextConfig.policy.xdpi = 96;
    mNextConfig.policy.ydpi = 96;
    mNextConfig.policy.refresh = 60;
    mNextConfig.extendedModeEnabled = false;
    mNextConfig.forceNotifyFrameType = false;
    mNextConfig.forceNotifyBufferInfo = false;
    mCurrentConfig = mNextConfig;

    memset(&mLastInputFrameInfo, 0, sizeof(mLastInputFrameInfo));
    memset(&mLastOutputFrameInfo, 0, sizeof(mLastOutputFrameInfo));
#endif
    mPayloadManager = mHwc.getPlatFactory()->createVideoPayloadManager();

    if (!mPayloadManager) {
        DEINIT_AND_RETURN_FALSE("Failed to create payload manager");
    }

    mVsyncObserver = new SoftVsyncObserver(*this);
    if (!mVsyncObserver || !mVsyncObserver->initialize()) {
        DEINIT_AND_RETURN_FALSE("Failed to create Soft Vsync Observer");
    }

    mSyncTimelineFd = sw_sync_timeline_create();
    mNextSyncPoint = 1;
    mExpectAcquireFences = false;

    mThread = new WidiBlitThread(this);
    mThread->run("WidiBlit", PRIORITY_URGENT_DISPLAY);

#ifdef INTEL_WIDI
    // Publish frame server service with service manager
    status_t ret = defaultServiceManager()->addService(String16("hwc.widi"), this);
    if (ret == NO_ERROR) {
        ProcessState::self()->startThreadPool();
        mInitialized = true;
    } else {
        ETRACE("Could not register hwc.widi with service manager, error = %d", ret);
        deinitialize();
    }
#else
    mInitialized = true;
#endif
    mVspEnabled = false;
    mVspInUse = false;
    mVspWidth = 0;
    mVspHeight = 0;
    va_dpy = NULL;
    va_config = 0;
    va_context = 0;
    va_blank_yuv_in = 0;
    va_blank_rgb_in = 0;
    mVspUpscale = false;
    mDebugVspClear = false;
    mDebugVspDump = false;
    mDebugCounter = 0;

    ITRACE("Init done.");

    return mInitialized;
}

bool VirtualDevice::isConnected() const
{
    return true;
}

const char* VirtualDevice::getName() const
{
    return "Virtual";
}

int VirtualDevice::getType() const
{
    return DEVICE_VIRTUAL;
}

void VirtualDevice::onVsync(int64_t timestamp)
{
    mHwc.vsync(DEVICE_VIRTUAL, timestamp);
}

void VirtualDevice::dump(Dump& d)
{
}

uint32_t VirtualDevice::getFpsDivider()
{
    return mFpsDivider;
}

void VirtualDevice::deinitialize()
{
    VAStatus va_status;

    if (mPayloadManager) {
        delete mPayloadManager;
        mPayloadManager = NULL;
    }
    DEINIT_AND_DELETE_OBJ(mVsyncObserver);
    mInitialized = false;
}

bool VirtualDevice::setPowerMode(int /*mode*/)
{
    return true;
}

int VirtualDevice::getActiveConfig()
{
    return 0;
}

bool VirtualDevice::setActiveConfig(int /*index*/)
{
    return false;
}

} // namespace intel
} // namespace android
