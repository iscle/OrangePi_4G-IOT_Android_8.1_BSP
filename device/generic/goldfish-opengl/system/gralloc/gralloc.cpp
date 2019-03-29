/*
* Copyright (C) 2011 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
#include <string.h>
#include <pthread.h>
#include <limits.h>
#include <cutils/ashmem.h>
#include <unistd.h>
#include <errno.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include "gralloc_cb.h"
#include "goldfish_dma.h"
#include "FormatConversions.h"
#include "HostConnection.h"
#include "ProcessPipe.h"
#include "glUtils.h"
#include <utils/CallStack.h>
#include <cutils/log.h>
#include <cutils/properties.h>

#include <set>
#include <string>
#include <sstream>

/* Set to 1 or 2 to enable debug traces */
#define DEBUG  0

#if DEBUG >= 1
#  define D(...)   ALOGD(__VA_ARGS__)
#else
#  define D(...)   ((void)0)
#endif

#if DEBUG >= 2
#  define DD(...)  ALOGD(__VA_ARGS__)
#else
#  define DD(...)  ((void)0)
#endif

#define DBG_FUNC DBG("%s\n", __FUNCTION__)

#ifdef GOLDFISH_HIDL_GRALLOC
static bool isHidlGralloc = true;
#else
static bool isHidlGralloc = false;
#endif

int32_t* getOpenCountPtr(cb_handle_t* cb) {
    return ((int32_t*)cb->ashmemBase) + 1;
}

uint32_t getAshmemColorOffset(cb_handle_t* cb) {
    uint32_t res = 0;
    if (cb->canBePosted()) res = sizeof(intptr_t);
    if (isHidlGralloc) res = sizeof(intptr_t) * 2;
    return res;
}

//
// our private gralloc module structure
//
struct private_module_t {
    gralloc_module_t base;
};

/* If not NULL, this is a pointer to the fallback module.
 * This really is gralloc.default, which we'll use if we detect
 * that the emulator we're running in does not support GPU emulation.
 */
static gralloc_module_t*  sFallback;
static pthread_once_t     sFallbackOnce = PTHREAD_ONCE_INIT;

static void fallback_init(void);  // forward

typedef struct _alloc_list_node {
    buffer_handle_t handle;
    _alloc_list_node *next;
    _alloc_list_node *prev;
} AllocListNode;

struct MemRegionInfo {
    void* ashmemBase;
    mutable uint32_t refCount;
};

struct MemRegionInfoCmp {
    bool operator()(const MemRegionInfo& a, const MemRegionInfo& b) const {
        return a.ashmemBase < b.ashmemBase;
    }
};

typedef std::set<MemRegionInfo, MemRegionInfoCmp> MemRegionSet;
typedef MemRegionSet::iterator mem_region_handle_t;

//
// Our gralloc device structure (alloc interface)
//
struct gralloc_device_t {
    alloc_device_t  device;

    AllocListNode *allocListHead;    // double linked list of allocated buffers
    MemRegionSet ashmemRegions; // to track allocations of each ashmem region
    pthread_mutex_t lock;
};

struct gralloc_memregions_t {
    MemRegionSet ashmemRegions;
};

#define INITIAL_DMA_REGION_SIZE 4096
struct gralloc_dmaregion_t {
    goldfish_dma_context goldfish_dma;
    uint32_t sz;
    uint32_t refcount;
    pthread_mutex_t lock;
};

// global device instance
static gralloc_memregions_t* s_grdev = NULL;
static gralloc_dmaregion_t* s_grdma = NULL;

void init_gralloc_memregions() {
    if (s_grdev) return;
    s_grdev = new gralloc_memregions_t;
}

void init_gralloc_dmaregion() {
    D("%s: call\n", __FUNCTION__);
    if (s_grdma) return;

    s_grdma = new gralloc_dmaregion_t;
    s_grdma->sz = INITIAL_DMA_REGION_SIZE;
    s_grdma->refcount = 0;

    pthread_mutex_init(&s_grdma->lock, NULL);
    pthread_mutex_lock(&s_grdma->lock);
    goldfish_dma_create_region(s_grdma->sz, &s_grdma->goldfish_dma);
    pthread_mutex_unlock(&s_grdma->lock);
}

void get_gralloc_dmaregion() {
    if (!s_grdma) return;
    pthread_mutex_lock(&s_grdma->lock);
    s_grdma->refcount++;
    D("%s: call. refcount: %u\n", __FUNCTION__, s_grdma->refcount);
    pthread_mutex_unlock(&s_grdma->lock);
}

static void resize_gralloc_dmaregion_locked(uint32_t new_sz) {
    if (!s_grdma) return;
    if (s_grdma->goldfish_dma.mapped) {
        goldfish_dma_unmap(&s_grdma->goldfish_dma);
    }
    close(s_grdma->goldfish_dma.fd);
    goldfish_dma_create_region(new_sz, &s_grdma->goldfish_dma);
    s_grdma->sz = new_sz;
}

bool put_gralloc_dmaregion() {
    if (!s_grdma) return false;
    pthread_mutex_lock(&s_grdma->lock);
    D("%s: call. refcount before: %u\n", __FUNCTION__, s_grdma->refcount);
    s_grdma->refcount--;
    bool shouldDelete = !s_grdma->refcount;
    if (shouldDelete) {
        D("%s: should delete!\n", __FUNCTION__);
        resize_gralloc_dmaregion_locked(INITIAL_DMA_REGION_SIZE);
        D("%s: done\n", __FUNCTION__);
    }
    pthread_mutex_unlock(&s_grdma->lock);
    D("%s: exit\n", __FUNCTION__);
    return shouldDelete;
}

void gralloc_dmaregion_register_ashmem(uint32_t sz) {
    if (!s_grdma) return;
    pthread_mutex_lock(&s_grdma->lock);
    D("%s: for sz %u, refcount %u", __FUNCTION__, sz, s_grdma->refcount);
    uint32_t new_sz = std::max(s_grdma->sz, sz);
    if (new_sz != s_grdma->sz) {
        D("%s: change sz from %u to %u", __FUNCTION__, s_grdma->sz, sz);
        resize_gralloc_dmaregion_locked(new_sz);
    }
    if (!s_grdma->goldfish_dma.mapped) {
        goldfish_dma_map(&s_grdma->goldfish_dma);
    }
    pthread_mutex_unlock(&s_grdma->lock);
}

void get_mem_region(void* ashmemBase) {
    init_gralloc_memregions();
    D("%s: call for %p", __FUNCTION__, ashmemBase);
    MemRegionInfo lookup;
    lookup.ashmemBase = ashmemBase;
    mem_region_handle_t handle = s_grdev->ashmemRegions.find(lookup);
    if (handle == s_grdev->ashmemRegions.end()) {
        MemRegionInfo newRegion;
        newRegion.ashmemBase = ashmemBase;
        newRegion.refCount = 1;
        s_grdev->ashmemRegions.insert(newRegion);
    } else {
        handle->refCount++;
    }
}

bool put_mem_region(void* ashmemBase) {
    init_gralloc_memregions();
    D("%s: call for %p", __FUNCTION__, ashmemBase);
    MemRegionInfo lookup;
    lookup.ashmemBase = ashmemBase;
    mem_region_handle_t handle = s_grdev->ashmemRegions.find(lookup);
    if (handle == s_grdev->ashmemRegions.end()) {
        ALOGE("%s: error: tried to put nonexistent mem region!", __FUNCTION__);
        return true;
    } else {
        handle->refCount--;
        bool shouldRemove = !handle->refCount;
        if (shouldRemove) {
            s_grdev->ashmemRegions.erase(lookup);
        }
        return shouldRemove;
    }
}

void dump_regions() {
    init_gralloc_memregions();
    mem_region_handle_t curr = s_grdev->ashmemRegions.begin();
    std::stringstream res;
    for (; curr != s_grdev->ashmemRegions.end(); curr++) {
        res << "\tashmem base " << curr->ashmemBase << " refcount " << curr->refCount << "\n";
    }
    ALOGD("ashmem region dump [\n%s]", res.str().c_str());
}

#if DEBUG

#define GET_ASHMEM_REGION(cb) \
    dump_regions(); \
    get_mem_region((void*)cb->ashmemBase); \
    dump_regions(); \

#define PUT_ASHMEM_REGION(cb) \
    dump_regions(); \
    bool SHOULD_UNMAP = put_mem_region((void*)cb->ashmemBase); \
    dump_regions(); \

#else

#define GET_ASHMEM_REGION(cb) \
    get_mem_region((void*)cb->ashmemBase); \

#define PUT_ASHMEM_REGION(cb) \
    bool SHOULD_UNMAP = put_mem_region((void*)cb->ashmemBase); \

#endif

//
// Our framebuffer device structure
//
struct fb_device_t {
    framebuffer_device_t  device;
};

static int map_buffer(cb_handle_t *cb, void **vaddr)
{
    if (cb->fd < 0 || cb->ashmemSize <= 0) {
        return -EINVAL;
    }

    int map_flags = MAP_SHARED;
    if (isHidlGralloc) map_flags |= MAP_ANONYMOUS;

    void *addr = mmap(0, cb->ashmemSize, PROT_READ | PROT_WRITE,
                      MAP_SHARED, cb->fd, 0);
    if (addr == MAP_FAILED) {
        ALOGE("%s: failed to map ashmem region!", __FUNCTION__);
        return -errno;
    }

    cb->ashmemBase = intptr_t(addr);
    cb->ashmemBasePid = getpid();
    D("%s: %p mapped ashmem base %p size %d\n", __FUNCTION__,
      cb, cb->ashmemBase, cb->ashmemSize);

    *vaddr = addr;
    return 0;
}

#define DEFINE_HOST_CONNECTION \
    HostConnection *hostCon = HostConnection::get(); \
    ExtendedRCEncoderContext *rcEnc = (hostCon ? hostCon->rcEncoder() : NULL)

#define DEFINE_AND_VALIDATE_HOST_CONNECTION \
    HostConnection *hostCon = HostConnection::get(); \
    if (!hostCon) { \
        ALOGE("gralloc: Failed to get host connection\n"); \
        return -EIO; \
    } \
    ExtendedRCEncoderContext *rcEnc = hostCon->rcEncoder(); \
    if (!rcEnc) { \
        ALOGE("gralloc: Failed to get renderControl encoder context\n"); \
        return -EIO; \
    }

#if PLATFORM_SDK_VERSION < 18
// On older APIs, just define it as a value no one is going to use.
#define HAL_PIXEL_FORMAT_YCbCr_420_888 0xFFFFFFFF
#endif

static void updateHostColorBuffer(cb_handle_t* cb,
                              bool doLocked,
                              char* pixels) {
    D("%s: call. doLocked=%d", __FUNCTION__, doLocked);
    DEFINE_HOST_CONNECTION;
    int bpp = glUtilsPixelBitSize(cb->glFormat, cb->glType) >> 3;
    int left = doLocked ? cb->lockedLeft : 0;
    int top = doLocked ? cb->lockedTop : 0;
    int width = doLocked ? cb->lockedWidth : cb->width;
    int height = doLocked ? cb->lockedHeight : cb->height;

    char* to_send = pixels;
    uint32_t rgbSz = width * height * bpp;
    uint32_t send_buffer_size = rgbSz;
    bool is_rgb_format =
        cb->frameworkFormat != HAL_PIXEL_FORMAT_YV12 &&
        cb->frameworkFormat != HAL_PIXEL_FORMAT_YCbCr_420_888;

    char* convertedBuf = NULL;
    if ((doLocked && is_rgb_format) ||
        (!s_grdma &&
         (doLocked || !is_rgb_format))) {
        convertedBuf = new char[rgbSz];
        to_send = convertedBuf;
        send_buffer_size = rgbSz;
    }

    if (doLocked && is_rgb_format) {
        copy_rgb_buffer_from_unlocked(
                to_send, pixels,
                cb->width,
                width, height, top, left, bpp);
    }

    if (s_grdma) {
        if (cb->frameworkFormat == HAL_PIXEL_FORMAT_YV12) {
            get_yv12_offsets(width, height, NULL, NULL,
                             &send_buffer_size);
        }
        if (cb->frameworkFormat == HAL_PIXEL_FORMAT_YCbCr_420_888) {
            get_yuv420p_offsets(width, height, NULL, NULL,
                                &send_buffer_size);
        }

        rcEnc->bindDmaContext(&s_grdma->goldfish_dma);
        D("%s: call. dma update with sz=%u", __FUNCTION__, send_buffer_size);
        pthread_mutex_lock(&s_grdma->lock);
        rcEnc->rcUpdateColorBufferDMA(rcEnc, cb->hostHandle,
                left, top, width, height,
                cb->glFormat, cb->glType,
                to_send, send_buffer_size);
        pthread_mutex_unlock(&s_grdma->lock);
    } else {
        if (cb->frameworkFormat == HAL_PIXEL_FORMAT_YV12) {
            yv12_to_rgb888(to_send, pixels,
                           width, height, left, top,
                           left + width - 1, top + height - 1);
        }
        if (cb->frameworkFormat == HAL_PIXEL_FORMAT_YCbCr_420_888) {
            yuv420p_to_rgb888(to_send, pixels,
                              width, height, left, top,
                              left + width - 1, top + height - 1);
        }
        rcEnc->rcUpdateColorBuffer(rcEnc, cb->hostHandle,
                left, top, width, height,
                cb->glFormat, cb->glType, to_send);
    }

    if (convertedBuf) delete [] convertedBuf;
}

#ifndef GL_RGBA16F
#define GL_RGBA16F                        0x881A
#endif // GL_RGBA16F
#ifndef GL_UNSIGNED_INT_10_10_10_2
#define GL_UNSIGNED_INT_10_10_10_2        0x8DF6
#endif // GL_UNSIGNED_INT_10_10_10_2
#ifndef GL_HALF_FLOAT
#define GL_HALF_FLOAT                     0x140B
#endif // GL_HALF_FLOAT
//
// gralloc device functions (alloc interface)
//
static int gralloc_alloc(alloc_device_t* dev,
                         int w, int h, int format, int usage,
                         buffer_handle_t* pHandle, int* pStride)
{
    D("gralloc_alloc w=%d h=%d usage=0x%x format=0x%x\n", w, h, usage, format);

    gralloc_device_t *grdev = (gralloc_device_t *)dev;
    if (!grdev || !pHandle || !pStride) {
        ALOGE("gralloc_alloc: Bad inputs (grdev: %p, pHandle: %p, pStride: %p",
                grdev, pHandle, pStride);
        return -EINVAL;
    }

    //
    // Note: in screen capture mode, both sw_write and hw_write will be on
    // and this is a valid usage
    //
    bool sw_write = (0 != (usage & GRALLOC_USAGE_SW_WRITE_MASK));
    bool hw_write = (usage & GRALLOC_USAGE_HW_RENDER);
    bool sw_read = (0 != (usage & GRALLOC_USAGE_SW_READ_MASK));
#if PLATFORM_SDK_VERSION >= 17
    bool hw_cam_write = (usage & GRALLOC_USAGE_HW_CAMERA_WRITE);
    bool hw_cam_read = (usage & GRALLOC_USAGE_HW_CAMERA_READ);
#else // PLATFORM_SDK_VERSION
    bool hw_cam_write = false;
    bool hw_cam_read = false;
#endif // PLATFORM_SDK_VERSION
#if PLATFORM_SDK_VERSION >= 15
    bool hw_vid_enc_read = usage & GRALLOC_USAGE_HW_VIDEO_ENCODER;
#else // PLATFORM_SDK_VERSION
    bool hw_vid_enc_read = false;
#endif // PLATFORM_SDK_VERSION

    // Keep around original requested format for later validation
    int frameworkFormat = format;
    // Pick the right concrete pixel format given the endpoints as encoded in
    // the usage bits.  Every end-point pair needs explicit listing here.
#if PLATFORM_SDK_VERSION >= 17
    if (format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
        // Camera as producer
        if (usage & GRALLOC_USAGE_HW_CAMERA_WRITE) {
            if (usage & GRALLOC_USAGE_HW_TEXTURE) {
                // Camera-to-display is RGBA
                format = HAL_PIXEL_FORMAT_RGBA_8888;
            } else if (usage & GRALLOC_USAGE_HW_VIDEO_ENCODER) {
                // Camera-to-encoder is NV21
                format = HAL_PIXEL_FORMAT_YCrCb_420_SP;
            } else if ((usage & GRALLOC_USAGE_HW_CAMERA_MASK) ==
                    GRALLOC_USAGE_HW_CAMERA_ZSL) {
                // Camera-to-ZSL-queue is RGB_888
                format = HAL_PIXEL_FORMAT_RGB_888;
            }
        }

        if (format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
            ALOGE("gralloc_alloc: Requested auto format selection, "
                    "but no known format for this usage: %d x %d, usage %x",
                    w, h, usage);
            return -EINVAL;
        }
    }
    else if (format == HAL_PIXEL_FORMAT_YCbCr_420_888) {
        ALOGW("gralloc_alloc: Requested YCbCr_420_888, taking experimental path. "
                "usage: %d x %d, usage %x",
                w, h, usage);
    }
#endif // PLATFORM_SDK_VERSION >= 17
    bool yuv_format = false;

    int ashmem_size = 0;
    int stride = w;

    GLenum glFormat = 0;
    GLenum glType = 0;
    EmulatorFrameworkFormat selectedEmuFrameworkFormat = FRAMEWORK_FORMAT_GL_COMPATIBLE;

    int bpp = 0;
    int align = 1;
    switch (format) {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_BGRA_8888:
            bpp = 4;
            glFormat = GL_RGBA;
            glType = GL_UNSIGNED_BYTE;
            break;
        case HAL_PIXEL_FORMAT_RGB_888:
            bpp = 3;
            glFormat = GL_RGB;
            glType = GL_UNSIGNED_BYTE;
            break;
        case HAL_PIXEL_FORMAT_RGB_565:
            bpp = 2;
            glFormat = GL_RGB;
            glType = GL_UNSIGNED_SHORT_5_6_5;
            break;
#if PLATFORM_SDK_VERSION >= 26
        case HAL_PIXEL_FORMAT_RGBA_FP16:
            bpp = 8;
            glFormat = GL_RGBA16F;
            glType = GL_HALF_FLOAT;
            break;
        case HAL_PIXEL_FORMAT_RGBA_1010102:
            bpp = 4;
            glFormat = GL_RGBA;
            glType = GL_UNSIGNED_INT_10_10_10_2;
            break;
#endif // PLATFORM_SDK_VERSION >= 26
#if PLATFORM_SDK_VERSION >= 21
        case HAL_PIXEL_FORMAT_RAW16:
        case HAL_PIXEL_FORMAT_Y16:
#elif PLATFORM_SDK_VERSION >= 16
        case HAL_PIXEL_FORMAT_RAW_SENSOR:
#endif
            bpp = 2;
            align = 16*bpp;
            if (! ((sw_read || hw_cam_read) && (sw_write || hw_cam_write) ) ) {
                // Raw sensor data or Y16 only goes between camera and CPU
                return -EINVAL;
            }
            // Not expecting to actually create any GL surfaces for this
            glFormat = GL_LUMINANCE;
            glType = GL_UNSIGNED_SHORT;
            break;
#if PLATFORM_SDK_VERSION >= 17
        case HAL_PIXEL_FORMAT_BLOB:
            bpp = 1;
            if (! (sw_read) ) {
                // Blob data cannot be used by HW other than camera emulator
                // But there is a CTS test trying to have access to it
                // BUG: https://buganizer.corp.google.com/issues/37719518
                return -EINVAL;
            }
            // Not expecting to actually create any GL surfaces for this
            glFormat = GL_LUMINANCE;
            glType = GL_UNSIGNED_BYTE;
            break;
#endif // PLATFORM_SDK_VERSION >= 17
        case HAL_PIXEL_FORMAT_YCrCb_420_SP:
            align = 1;
            bpp = 1; // per-channel bpp
            yuv_format = true;
            // Not expecting to actually create any GL surfaces for this
            break;
        case HAL_PIXEL_FORMAT_YV12:
            align = 16;
            bpp = 1; // per-channel bpp
            yuv_format = true;
            // We are going to use RGB888 on the host
            glFormat = GL_RGB;
            glType = GL_UNSIGNED_BYTE;
            selectedEmuFrameworkFormat = FRAMEWORK_FORMAT_YV12;
            break;
        case HAL_PIXEL_FORMAT_YCbCr_420_888:
            align = 1;
            bpp = 1; // per-channel bpp
            yuv_format = true;
            // We are going to use RGB888 on the host
            glFormat = GL_RGB;
            glType = GL_UNSIGNED_BYTE;
            selectedEmuFrameworkFormat = FRAMEWORK_FORMAT_YUV_420_888;
            break;
        default:
            ALOGE("gralloc_alloc: Unknown format %d", format);
            return -EINVAL;
    }

    //
    // Allocate ColorBuffer handle on the host (only if h/w access is allowed)
    // Only do this for some h/w usages, not all.
    // Also do this if we need to read from the surface, in this case the
    // rendering will still happen on the host but we also need to be able to
    // read back from the color buffer, which requires that there is a buffer
    //
    bool needHostCb = (!yuv_format ||
                       frameworkFormat == HAL_PIXEL_FORMAT_YV12 ||
                       frameworkFormat == HAL_PIXEL_FORMAT_YCbCr_420_888) &&
#if PLATFORM_SDK_VERSION >= 15
                      (usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER |
                                GRALLOC_USAGE_HW_2D | GRALLOC_USAGE_HW_COMPOSER |
                                GRALLOC_USAGE_HW_VIDEO_ENCODER |
                                GRALLOC_USAGE_HW_FB | GRALLOC_USAGE_SW_READ_MASK))
#else // PLATFORM_SDK_VERSION
                      (usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER |
                                GRALLOC_USAGE_HW_2D |
                                GRALLOC_USAGE_HW_FB | GRALLOC_USAGE_SW_READ_MASK))
#endif // PLATFORM_SDK_VERSION
                      ;

    if (isHidlGralloc) {
        if (needHostCb || (usage & GRALLOC_USAGE_HW_FB)) {
            // keep space for postCounter
            // AND openCounter for all host cb
            ashmem_size += sizeof(uint32_t) * 2;
        }
    } else {
        if (usage & GRALLOC_USAGE_HW_FB) {
            // keep space for postCounter
            ashmem_size += sizeof(uint32_t) * 1;
        }
    }

    // API26 always expect at least one file descriptor is associated with
    // one color buffer
    // BUG: 37719038
    if (PLATFORM_SDK_VERSION >= 26 ||
        sw_read || sw_write || hw_cam_write || hw_vid_enc_read) {
        // keep space for image on guest memory if SW access is needed
        // or if the camera is doing writing
        if (yuv_format) {
            size_t yStride = (w*bpp + (align - 1)) & ~(align-1);
            size_t uvStride = (yStride / 2 + (align - 1)) & ~(align-1);
            size_t uvHeight = h / 2;
            ashmem_size += yStride * h + 2 * (uvHeight * uvStride);
            stride = yStride / bpp;
        } else {
            size_t bpr = (w*bpp + (align-1)) & ~(align-1);
            ashmem_size += (bpr * h);
            stride = bpr / bpp;
        }
    }

    D("gralloc_alloc format=%d, ashmem_size=%d, stride=%d, tid %d\n", format,
            ashmem_size, stride, gettid());

    //
    // Allocate space in ashmem if needed
    //
    int fd = -1;
    if (ashmem_size > 0) {
        // round to page size;
        ashmem_size = (ashmem_size + (PAGE_SIZE-1)) & ~(PAGE_SIZE-1);

        ALOGD("%s: Creating ashmem region of size %lu\n", __FUNCTION__, ashmem_size);
        fd = ashmem_create_region("gralloc-buffer", ashmem_size);
        if (fd < 0) {
            ALOGE("gralloc_alloc failed to create ashmem region: %s\n",
                    strerror(errno));
            return -errno;
        }
    }

    cb_handle_t *cb = new cb_handle_t(fd, ashmem_size, usage,
                                      w, h, frameworkFormat, format,
                                      glFormat, glType, selectedEmuFrameworkFormat);

    DEFINE_HOST_CONNECTION;
    if (ashmem_size > 0) {

        //
        // map ashmem region if exist
        //
        void *vaddr;
        int err = map_buffer(cb, &vaddr);
        if (err) {
            close(fd);
            delete cb;
            return err;
        }

        cb->setFd(fd);

        if (rcEnc->getDmaVersion() > 0) {
            D("%s: creating goldfish dma region of size %lu (cb fd %d)\n", __FUNCTION__, ashmem_size, cb->fd);
            init_gralloc_dmaregion();
            get_gralloc_dmaregion();
        } else {
            cb->goldfish_dma.fd = -1;
        }
    } else {
        cb->goldfish_dma.fd = -1;
    }

    if (needHostCb) {
        if (hostCon && rcEnc) {
            if (s_grdma) {
                cb->hostHandle = rcEnc->rcCreateColorBufferDMA(rcEnc, w, h, glFormat, cb->emuFrameworkFormat);
            } else {
                cb->hostHandle = rcEnc->rcCreateColorBuffer(rcEnc, w, h, glFormat);
            }
            D("Created host ColorBuffer 0x%x\n", cb->hostHandle);
        }

        if (!cb->hostHandle) {
            // Could not create colorbuffer on host !!!
            close(fd);
            delete cb;
            ALOGD("%s: failed to create host cb! -EIO", __FUNCTION__);
            return -EIO;
        }

        if (isHidlGralloc) { *getOpenCountPtr(cb) = 0; }
    }

    //
    // alloc succeeded - insert the allocated handle to the allocated list
    //
    AllocListNode *node = new AllocListNode();
    pthread_mutex_lock(&grdev->lock);
    node->handle = cb;
    node->next =  grdev->allocListHead;
    node->prev =  NULL;
    if (grdev->allocListHead) {
        grdev->allocListHead->prev = node;
    }
    grdev->allocListHead = node;
    pthread_mutex_unlock(&grdev->lock);

    *pHandle = cb;
    D("%s: alloc succeded, new ashmem base and size: %p %d handle: %p",
      __FUNCTION__, cb->ashmemBase, cb->ashmemSize, cb);
    switch (frameworkFormat) {
    case HAL_PIXEL_FORMAT_YCbCr_420_888:
        *pStride = 0;
        break;
    default:
        *pStride = stride;
        break;
    }
    return 0;
}

static int gralloc_free(alloc_device_t* dev,
                        buffer_handle_t handle)
{
    cb_handle_t *cb = (cb_handle_t *)handle;
    if (!cb_handle_t::validate((cb_handle_t*)cb)) {
        ERR("gralloc_free: invalid handle");
        return -EINVAL;
    }

    D("%s: for buf %p ptr %p size %d\n",
      __FUNCTION__, handle, cb->ashmemBase, cb->ashmemSize);

    if (cb->hostHandle) {
        int32_t openCount = 1;
        int32_t* openCountPtr = &openCount;

        if (isHidlGralloc) { openCountPtr = getOpenCountPtr(cb); }

        if (*openCountPtr > 0) {
            DEFINE_AND_VALIDATE_HOST_CONNECTION;
            D("Closing host ColorBuffer 0x%x\n", cb->hostHandle);
            rcEnc->rcCloseColorBuffer(rcEnc, cb->hostHandle);
        } else {
            D("A rcCloseColorBuffer is owed!!! sdk ver: %d", PLATFORM_SDK_VERSION);
            *openCountPtr = -1;
        }
    }

    //
    // detach and unmap ashmem area if present
    //
    if (cb->fd > 0) {
        if (cb->ashmemSize > 0 && cb->ashmemBase) {
            D("%s: unmapped %p", __FUNCTION__, cb->ashmemBase);
            munmap((void *)cb->ashmemBase, cb->ashmemSize);
            put_gralloc_dmaregion();
        }
        close(cb->fd);
    }

    D("%s: done", __FUNCTION__);
    // remove it from the allocated list
    gralloc_device_t *grdev = (gralloc_device_t *)dev;
    pthread_mutex_lock(&grdev->lock);
    AllocListNode *n = grdev->allocListHead;
    while( n && n->handle != cb ) {
        n = n->next;
    }
    if (n) {
       // buffer found on list - remove it from list
       if (n->next) {
           n->next->prev = n->prev;
       }
       if (n->prev) {
           n->prev->next = n->next;
       }
       else {
           grdev->allocListHead = n->next;
       }

       delete n;
    }
    pthread_mutex_unlock(&grdev->lock);

    delete cb;

    D("%s: exit", __FUNCTION__);
    return 0;
}

static int gralloc_device_close(struct hw_device_t *dev)
{
    gralloc_device_t* d = reinterpret_cast<gralloc_device_t*>(dev);
    if (d) {

        // free still allocated buffers
        while( d->allocListHead != NULL ) {
            gralloc_free(&d->device, d->allocListHead->handle);
        }

        // free device
        free(d);
    }
    return 0;
}

static int fb_compositionComplete(struct framebuffer_device_t* dev)
{
    (void)dev;

    return 0;
}

//
// Framebuffer device functions
//
static int fb_post(struct framebuffer_device_t* dev, buffer_handle_t buffer)
{
    fb_device_t *fbdev = (fb_device_t *)dev;
    cb_handle_t *cb = (cb_handle_t *)buffer;

    if (!fbdev || !cb_handle_t::validate(cb) || !cb->canBePosted()) {
        return -EINVAL;
    }

    // Make sure we have host connection
    DEFINE_AND_VALIDATE_HOST_CONNECTION;

    // increment the post count of the buffer
    intptr_t *postCountPtr = (intptr_t *)cb->ashmemBase;
    if (!postCountPtr) {
        // This should not happen
        return -EINVAL;
    }
    (*postCountPtr)++;

    // send post request to host
    rcEnc->rcFBPost(rcEnc, cb->hostHandle);
    hostCon->flush();

    return 0;
}

static int fb_setUpdateRect(struct framebuffer_device_t* dev,
        int l, int t, int w, int h)
{
    fb_device_t *fbdev = (fb_device_t *)dev;

    (void)l;
    (void)t;
    (void)w;
    (void)h;

    if (!fbdev) {
        return -EINVAL;
    }

    // Make sure we have host connection
    DEFINE_AND_VALIDATE_HOST_CONNECTION;

    // send request to host
    // TODO: XXX - should be implemented
    //rcEnc->rc_XXX

    return 0;
}

static int fb_setSwapInterval(struct framebuffer_device_t* dev,
            int interval)
{
    fb_device_t *fbdev = (fb_device_t *)dev;

    if (!fbdev) {
        return -EINVAL;
    }

    // Make sure we have host connection
    DEFINE_AND_VALIDATE_HOST_CONNECTION;

    // send request to host
    rcEnc->rcFBSetSwapInterval(rcEnc, interval);
    hostCon->flush();

    return 0;
}

static int fb_close(struct hw_device_t *dev)
{
    fb_device_t *fbdev = (fb_device_t *)dev;

    delete fbdev;

    return 0;
}


//
// gralloc module functions - refcount + locking interface
//
static int gralloc_register_buffer(gralloc_module_t const* module,
                                   buffer_handle_t handle)
{

    D("%s: start", __FUNCTION__);
    pthread_once(&sFallbackOnce, fallback_init);
    if (sFallback != NULL) {
        return sFallback->registerBuffer(sFallback, handle);
    }

    D("gralloc_register_buffer(%p) called", handle);

    private_module_t *gr = (private_module_t *)module;
    cb_handle_t *cb = (cb_handle_t *)handle;

    if (!gr || !cb_handle_t::validate(cb)) {
        ERR("gralloc_register_buffer(%p): invalid buffer", cb);
        return -EINVAL;
    }

    if (cb->hostHandle != 0) {
        DEFINE_AND_VALIDATE_HOST_CONNECTION;
        D("Opening host ColorBuffer 0x%x\n", cb->hostHandle);
        rcEnc->rcOpenColorBuffer2(rcEnc, cb->hostHandle);
    }

    //
    // if the color buffer has ashmem region and it is not mapped in this
    // process map it now.
    //
    if (cb->ashmemSize > 0 && cb->mappedPid != getpid()) {
        void *vaddr;
        int err = map_buffer(cb, &vaddr);
        if (err) {
            ERR("gralloc_register_buffer(%p): map failed: %s", cb, strerror(-err));
            return -err;
        }
        cb->mappedPid = getpid();

        if (isHidlGralloc) {
            int32_t* openCountPtr = getOpenCountPtr(cb);
            if (!*openCountPtr) *openCountPtr = 1;
        }

        DEFINE_AND_VALIDATE_HOST_CONNECTION;
        if (rcEnc->getDmaVersion() > 0) {
            init_gralloc_dmaregion();
            gralloc_dmaregion_register_ashmem(cb->ashmemSize);
        }

    }

    if (cb->ashmemSize > 0) {
        GET_ASHMEM_REGION(cb);
        get_gralloc_dmaregion();
    }

    return 0;
}

static int gralloc_unregister_buffer(gralloc_module_t const* module,
                                     buffer_handle_t handle)
{
    if (sFallback != NULL) {
        return sFallback->unregisterBuffer(sFallback, handle);
    }

    private_module_t *gr = (private_module_t *)module;
    cb_handle_t *cb = (cb_handle_t *)handle;

    if (!gr || !cb_handle_t::validate(cb)) {
        ERR("gralloc_unregister_buffer(%p): invalid buffer", cb);
        return -EINVAL;
    }


    if (cb->hostHandle) {
        D("Closing host ColorBuffer 0x%x\n", cb->hostHandle);
        DEFINE_AND_VALIDATE_HOST_CONNECTION;
        rcEnc->rcCloseColorBuffer(rcEnc, cb->hostHandle);

        if (isHidlGralloc) {
            // Queue up another rcCloseColorBuffer if applicable.
            // invariant: have ashmem.
            if (cb->ashmemSize > 0 && cb->mappedPid == getpid()) {
                int32_t* openCountPtr = getOpenCountPtr(cb);
                if (*openCountPtr == -1) {
                    D("%s: revenge of the rcCloseColorBuffer!", __func__);
                    rcEnc->rcCloseColorBuffer(rcEnc, cb->hostHandle);
                    *openCountPtr = -2;
                }
            }
        }
    }

    //
    // unmap ashmem region if it was previously mapped in this process
    // (through register_buffer)
    //
    if (cb->ashmemSize > 0 && cb->mappedPid == getpid()) {

        PUT_ASHMEM_REGION(cb);
        put_gralloc_dmaregion();

        if (!SHOULD_UNMAP) goto done;

        DEFINE_AND_VALIDATE_HOST_CONNECTION;

        void *vaddr;
        int err = munmap((void *)cb->ashmemBase, cb->ashmemSize);
        if (err) {
            ERR("gralloc_unregister_buffer(%p): unmap failed", cb);
            return -EINVAL;
        }
        cb->ashmemBase = 0;
        cb->mappedPid = 0;
        D("%s: Unregister buffer previous mapped to pid %d", __FUNCTION__, getpid());
    }

done:
    D("gralloc_unregister_buffer(%p) done\n", cb);
    return 0;
}



static int gralloc_lock(gralloc_module_t const* module,
                        buffer_handle_t handle, int usage,
                        int l, int t, int w, int h,
                        void** vaddr)
{
    if (sFallback != NULL) {
        return sFallback->lock(sFallback, handle, usage, l, t, w, h, vaddr);
    }

    private_module_t *gr = (private_module_t *)module;
    cb_handle_t *cb = (cb_handle_t *)handle;

    if (!gr || !cb_handle_t::validate(cb)) {
        ALOGE("gralloc_lock bad handle\n");
        return -EINVAL;
    }

    // Validate usage,
    //   1. cannot be locked for hw access
    //   2. lock for either sw read or write.
    //   3. locked sw access must match usage during alloc time.
    bool sw_read = (0 != (usage & GRALLOC_USAGE_SW_READ_MASK));
    bool sw_write = (0 != (usage & GRALLOC_USAGE_SW_WRITE_MASK));
    bool hw_read = (usage & GRALLOC_USAGE_HW_TEXTURE);
    bool hw_write = (usage & GRALLOC_USAGE_HW_RENDER);
#if PLATFORM_SDK_VERSION >= 17
    bool hw_cam_write = (usage & GRALLOC_USAGE_HW_CAMERA_WRITE);
    bool hw_cam_read = (usage & GRALLOC_USAGE_HW_CAMERA_READ);
#else // PLATFORM_SDK_VERSION
    bool hw_cam_write = false;
    bool hw_cam_read = false;
#endif // PLATFORM_SDK_VERSION

#if PLATFORM_SDK_VERSION >= 15
    bool hw_vid_enc_read = (usage & GRALLOC_USAGE_HW_VIDEO_ENCODER);
#else // PLATFORM_SDK_VERSION
    bool hw_vid_enc_read = false;
#endif // PLATFORM_SDK_VERSION

    bool sw_read_allowed = (0 != (cb->usage & GRALLOC_USAGE_SW_READ_MASK));

#if PLATFORM_SDK_VERSION >= 15
    // bug: 30088791
    // a buffer was created for GRALLOC_USAGE_HW_VIDEO_ENCODER usage but
    // later a software encoder is reading this buffer: this is actually
    // legit usage.
    sw_read_allowed = sw_read_allowed || (cb->usage & GRALLOC_USAGE_HW_VIDEO_ENCODER);
#endif // PLATFORM_SDK_VERSION >= 15

    bool sw_write_allowed = (0 != (cb->usage & GRALLOC_USAGE_SW_WRITE_MASK));

    if ( (hw_read || hw_write) ||
         (!sw_read && !sw_write &&
                 !hw_cam_write && !hw_cam_read &&
                 !hw_vid_enc_read) ||
         (sw_read && !sw_read_allowed) ||
         (sw_write && !sw_write_allowed) ) {
        ALOGE("gralloc_lock usage mismatch usage=0x%x cb->usage=0x%x\n", usage,
                cb->usage);
        //This is not exactly an error and loose it up.
        //bug: 30784436
        //return -EINVAL;
    }

    intptr_t postCount = 0;
    void *cpu_addr = NULL;

    //
    // make sure ashmem area is mapped if needed
    //
    if (cb->canBePosted() || sw_read || sw_write ||
            hw_cam_write || hw_cam_read ||
            hw_vid_enc_read) {
        if (cb->ashmemBasePid != getpid() || !cb->ashmemBase) {
            return -EACCES;
        }

        cpu_addr = (void *)(cb->ashmemBase + getAshmemColorOffset(cb));
    }

    if (cb->hostHandle) {
        // Make sure we have host connection
        DEFINE_AND_VALIDATE_HOST_CONNECTION;

        //
        // flush color buffer write cache on host and get its sync status.
        //
        int hostSyncStatus = rcEnc->rcColorBufferCacheFlush(rcEnc, cb->hostHandle,
                                                            postCount,
                                                            sw_read);
        if (hostSyncStatus < 0) {
            // host failed the color buffer sync - probably since it was already
            // locked for write access. fail the lock.
            ALOGE("gralloc_lock cacheFlush failed postCount=%d sw_read=%d\n",
                 postCount, sw_read);
            return -EBUSY;
        }

        if (sw_read) {
            void* rgb_addr = cpu_addr;
            char* tmpBuf = 0;
            if (cb->frameworkFormat == HAL_PIXEL_FORMAT_YV12 ||
                cb->frameworkFormat == HAL_PIXEL_FORMAT_YCbCr_420_888) {
                // We are using RGB888
                tmpBuf = new char[cb->width * cb->height * 3];
                rgb_addr = tmpBuf;
            }
            D("gralloc_lock read back color buffer %d %d ashmem base %p sz %d\n",
              cb->width, cb->height, cb->ashmemBase, cb->ashmemSize);
            rcEnc->rcReadColorBuffer(rcEnc, cb->hostHandle,
                    0, 0, cb->width, cb->height, cb->glFormat, cb->glType, rgb_addr);
            if (tmpBuf) {
                if (cb->frameworkFormat == HAL_PIXEL_FORMAT_YV12) {
                    rgb888_to_yv12((char*)cpu_addr, tmpBuf, cb->width, cb->height, l, t, l+w-1, t+h-1);
                } else if (cb->frameworkFormat == HAL_PIXEL_FORMAT_YCbCr_420_888) {
                    rgb888_to_yuv420p((char*)cpu_addr, tmpBuf, cb->width, cb->height, l, t, l+w-1, t+h-1);
                }
                delete [] tmpBuf;
            }
        }
    }

    //
    // is virtual address required ?
    //
    if (sw_read || sw_write || hw_cam_write || hw_cam_read || hw_vid_enc_read) {
        *vaddr = cpu_addr;
    }

    if (sw_write || hw_cam_write) {
        //
        // Keep locked region if locked for s/w write access.
        //
        cb->lockedLeft = l;
        cb->lockedTop = t;
        cb->lockedWidth = w;
        cb->lockedHeight = h;
    }

    DD("gralloc_lock success. vaddr: %p, *vaddr: %p, usage: %x, cpu_addr: %p",
            vaddr, vaddr ? *vaddr : 0, usage, cpu_addr);

    return 0;
}

static int gralloc_unlock(gralloc_module_t const* module,
                          buffer_handle_t handle)
{
    if (sFallback != NULL) {
        return sFallback->unlock(sFallback, handle);
    }

    private_module_t *gr = (private_module_t *)module;
    cb_handle_t *cb = (cb_handle_t *)handle;

    if (!gr || !cb_handle_t::validate(cb)) {
        ALOGD("%s: invalid gr or cb handle. -EINVAL", __FUNCTION__);
        return -EINVAL;
    }

    //
    // if buffer was locked for s/w write, we need to update the host with
    // the updated data
    //
    if (cb->hostHandle) {

        // Make sure we have host connection
        DEFINE_AND_VALIDATE_HOST_CONNECTION;

        void *cpu_addr = (void *)(cb->ashmemBase + getAshmemColorOffset(cb));

        char* rgb_addr = (char *)cpu_addr;
        if (cb->lockedWidth < cb->width || cb->lockedHeight < cb->height) {
            updateHostColorBuffer(cb, true, rgb_addr);
        }
        else {
            updateHostColorBuffer(cb, false, rgb_addr);
        }

        DD("gralloc_unlock success. cpu_addr: %p", cpu_addr);
    }

    cb->lockedWidth = cb->lockedHeight = 0;
    return 0;
}

#if PLATFORM_SDK_VERSION >= 18
static int gralloc_lock_ycbcr(gralloc_module_t const* module,
                        buffer_handle_t handle, int usage,
                        int l, int t, int w, int h,
                        android_ycbcr *ycbcr)
{
    // Not supporting fallback module for YCbCr
    if (sFallback != NULL) {
        ALOGD("%s: has fallback, return -EINVAL", __FUNCTION__);
        return -EINVAL;
    }

    if (!ycbcr) {
        ALOGE("%s: got NULL ycbcr struct! -EINVAL", __FUNCTION__);
        return -EINVAL;
    }

    private_module_t *gr = (private_module_t *)module;
    cb_handle_t *cb = (cb_handle_t *)handle;
    if (!gr || !cb_handle_t::validate(cb)) {
        ALOGE("%s: bad colorbuffer handle. -EINVAL", __FUNCTION__);
        return -EINVAL;
    }

    if (cb->frameworkFormat != HAL_PIXEL_FORMAT_YV12 &&
        cb->frameworkFormat != HAL_PIXEL_FORMAT_YCbCr_420_888) {
        ALOGE("gralloc_lock_ycbcr can only be used with "
                "HAL_PIXEL_FORMAT_YCbCr_420_888 or HAL_PIXEL_FORMAT_YV12, got %x instead. "
                "-EINVAL",
                cb->frameworkFormat);
        return -EINVAL;
    }

    // Make sure memory is mapped, get address
    if (cb->ashmemBasePid != getpid() || !cb->ashmemBase) {
        ALOGD("%s: ashmembase not mapped. -EACCESS", __FUNCTION__);
        return -EACCES;
    }

    uint8_t *cpu_addr = NULL;
    cpu_addr = (uint8_t *)(cb->ashmemBase) + getAshmemColorOffset(cb);

    // Calculate offsets to underlying YUV data
    size_t yStride;
    size_t cStride;
    size_t cSize;
    size_t yOffset;
    size_t uOffset;
    size_t vOffset;
    size_t cStep;
    size_t align;
    switch (cb->format) {
        case HAL_PIXEL_FORMAT_YCrCb_420_SP:
            yStride = cb->width;
            cStride = cb->width;
            yOffset = 0;
            vOffset = yStride * cb->height;
            uOffset = vOffset + 1;
            cStep = 2;
            break;
        case HAL_PIXEL_FORMAT_YV12:
            // https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12
            align = 16;
            yStride = (cb->width + (align -1)) & ~(align-1);
            cStride = (yStride / 2 + (align - 1)) & ~(align-1);
            yOffset = 0;
            cSize = cStride * cb->height/2;
            vOffset = yStride * cb->height;
            uOffset = vOffset + cSize;
            cStep = 1;
            break;
        case HAL_PIXEL_FORMAT_YCbCr_420_888:
            align = 1;
            yStride = cb->width;
            cStride = yStride / 2;
            yOffset = 0;
            cSize = cStride * cb->height/2;
            uOffset = yStride * cb->height;
            vOffset = uOffset + cSize;
            cStep = 1;
            break;
        default:
            ALOGE("gralloc_lock_ycbcr unexpected internal format %x",
                    cb->format);
            return -EINVAL;
    }

    ycbcr->y = cpu_addr + yOffset;
    ycbcr->cb = cpu_addr + uOffset;
    ycbcr->cr = cpu_addr + vOffset;
    ycbcr->ystride = yStride;
    ycbcr->cstride = cStride;
    ycbcr->chroma_step = cStep;

    // Zero out reserved fields
    memset(ycbcr->reserved, 0, sizeof(ycbcr->reserved));

    //
    // Keep locked region if locked for s/w write access.
    //
    cb->lockedLeft = l;
    cb->lockedTop = t;
    cb->lockedWidth = w;
    cb->lockedHeight = h;

    DD("gralloc_lock_ycbcr success. usage: %x, ycbcr.y: %p, .cb: %p, .cr: %p, "
            ".ystride: %d , .cstride: %d, .chroma_step: %d", usage,
            ycbcr->y, ycbcr->cb, ycbcr->cr, ycbcr->ystride, ycbcr->cstride,
            ycbcr->chroma_step);

    return 0;
}
#endif // PLATFORM_SDK_VERSION >= 18

static int gralloc_device_open(const hw_module_t* module,
                               const char* name,
                               hw_device_t** device)
{
    int status = -EINVAL;

    D("gralloc_device_open %s\n", name);

    pthread_once( &sFallbackOnce, fallback_init );
    if (sFallback != NULL) {
        return sFallback->common.methods->open(&sFallback->common, name, device);
    }

    if (!strcmp(name, GRALLOC_HARDWARE_GPU0)) {

        // Create host connection and keep it in the TLS.
        // return error if connection with host can not be established
        HostConnection *hostCon = HostConnection::get();
        if (!hostCon) {
            ALOGE("gralloc: failed to get host connection while opening %s\n", name);
            return -EIO;
        }

        //
        // Allocate memory for the gralloc device (alloc interface)
        //
        gralloc_device_t *dev;
        dev = (gralloc_device_t*)malloc(sizeof(gralloc_device_t));
        if (NULL == dev) {
            return -ENOMEM;
        }
        memset(dev, 0, sizeof(gralloc_device_t));

        // Initialize our device structure
        //
        dev->device.common.tag = HARDWARE_DEVICE_TAG;
        dev->device.common.version = 0;
        dev->device.common.module = const_cast<hw_module_t*>(module);
        dev->device.common.close = gralloc_device_close;

        dev->device.alloc   = gralloc_alloc;
        dev->device.free    = gralloc_free;
        dev->allocListHead  = NULL;
        pthread_mutex_init(&dev->lock, NULL);

        *device = &dev->device.common;
        status = 0;
    }
    else if (!strcmp(name, GRALLOC_HARDWARE_FB0)) {

        // return error if connection with host can not be established
        DEFINE_AND_VALIDATE_HOST_CONNECTION;

        //
        // Query the host for Framebuffer attributes
        //
        D("gralloc: query Frabuffer attribs\n");
        EGLint width = rcEnc->rcGetFBParam(rcEnc, FB_WIDTH);
        D("gralloc: width=%d\n", width);
        EGLint height = rcEnc->rcGetFBParam(rcEnc, FB_HEIGHT);
        D("gralloc: height=%d\n", height);
        EGLint xdpi = rcEnc->rcGetFBParam(rcEnc, FB_XDPI);
        D("gralloc: xdpi=%d\n", xdpi);
        EGLint ydpi = rcEnc->rcGetFBParam(rcEnc, FB_YDPI);
        D("gralloc: ydpi=%d\n", ydpi);
        EGLint fps = rcEnc->rcGetFBParam(rcEnc, FB_FPS);
        D("gralloc: fps=%d\n", fps);
        EGLint min_si = rcEnc->rcGetFBParam(rcEnc, FB_MIN_SWAP_INTERVAL);
        D("gralloc: min_swap=%d\n", min_si);
        EGLint max_si = rcEnc->rcGetFBParam(rcEnc, FB_MAX_SWAP_INTERVAL);
        D("gralloc: max_swap=%d\n", max_si);

        //
        // Allocate memory for the framebuffer device
        //
        fb_device_t *dev;
        dev = (fb_device_t*)malloc(sizeof(fb_device_t));
        if (NULL == dev) {
            return -ENOMEM;
        }
        memset(dev, 0, sizeof(fb_device_t));

        // Initialize our device structure
        //
        dev->device.common.tag = HARDWARE_DEVICE_TAG;
        dev->device.common.version = 0;
        dev->device.common.module = const_cast<hw_module_t*>(module);
        dev->device.common.close = fb_close;
        dev->device.setSwapInterval = fb_setSwapInterval;
        dev->device.post            = fb_post;
        dev->device.setUpdateRect   = 0; //fb_setUpdateRect;
        dev->device.compositionComplete = fb_compositionComplete; //XXX: this is a dummy

        const_cast<uint32_t&>(dev->device.flags) = 0;
        const_cast<uint32_t&>(dev->device.width) = width;
        const_cast<uint32_t&>(dev->device.height) = height;
        const_cast<int&>(dev->device.stride) = width;
        const_cast<int&>(dev->device.format) = HAL_PIXEL_FORMAT_RGBA_8888;
        const_cast<float&>(dev->device.xdpi) = xdpi;
        const_cast<float&>(dev->device.ydpi) = ydpi;
        const_cast<float&>(dev->device.fps) = fps;
        const_cast<int&>(dev->device.minSwapInterval) = min_si;
        const_cast<int&>(dev->device.maxSwapInterval) = max_si;
        *device = &dev->device.common;

        status = 0;
    }

    return status;
}

//
// define the HMI symbol - our module interface
//
static struct hw_module_methods_t gralloc_module_methods = {
        open: gralloc_device_open
};

struct private_module_t HAL_MODULE_INFO_SYM = {
    base: {
        common: {
            tag: HARDWARE_MODULE_TAG,
#if PLATFORM_SDK_VERSION >= 18
            module_api_version: GRALLOC_MODULE_API_VERSION_0_2,
            hal_api_version: 0,
#elif PLATFORM_SDK_VERSION >= 16
            module_api_version: 1,
            hal_api_version: 0,
#else // PLATFORM_SDK_VERSION
            version_major: 1,
            version_minor: 0,
#endif // PLATFORM_SDK_VERSION
            id: GRALLOC_HARDWARE_MODULE_ID,
            name: "Graphics Memory Allocator Module",
            author: "The Android Open Source Project",
            methods: &gralloc_module_methods,
            dso: NULL,
            reserved: {0, }
        },
        registerBuffer: gralloc_register_buffer,
        unregisterBuffer: gralloc_unregister_buffer,
        lock: gralloc_lock,
        unlock: gralloc_unlock,
        perform: NULL,
#if PLATFORM_SDK_VERSION >= 18
        lock_ycbcr: gralloc_lock_ycbcr,
#endif // PLATFORM_SDK_VERSION >= 18
    }
};

/* This function is called once to detect whether the emulator supports
 * GPU emulation (this is done by looking at the qemu.gles kernel
 * parameter, which must be == 1 if this is the case).
 *
 * If not, then load gralloc.default instead as a fallback.
 */

#if __LP64__
static const char kGrallocDefaultSystemPath[] = "/system/lib64/hw/gralloc.default.so";
static const char kGrallocDefaultVendorPath[] = "/vendor/lib64/hw/gralloc.default.so";
#else
static const char kGrallocDefaultSystemPath[] = "/system/lib/hw/gralloc.default.so";
static const char kGrallocDefaultVendorPath[] = "/vendor/lib/hw/gralloc.default.so";
#endif

static void
fallback_init(void)
{
    char  prop[PROPERTY_VALUE_MAX];
    void* module;

    // qemu.gles=0 -> no GLES 2.x support (only 1.x through software).
    // qemu.gles=1 -> host-side GPU emulation through EmuGL
    // qemu.gles=2 -> guest-side GPU emulation.
    property_get("ro.kernel.qemu.gles", prop, "0");
    if (atoi(prop) == 1) {
        return;
    }
    ALOGD("Emulator without host-side GPU emulation detected. "
          "Loading gralloc.default.so from %s...",
          kGrallocDefaultVendorPath);
    module = dlopen(kGrallocDefaultVendorPath, RTLD_LAZY | RTLD_LOCAL);
    if (!module) {
        // vendor folder didn't work. try system
        ALOGD("gralloc.default.so not found in /vendor. Trying %s...",
              kGrallocDefaultSystemPath);
        module = dlopen(kGrallocDefaultSystemPath, RTLD_LAZY | RTLD_LOCAL);
    }

    if (module != NULL) {
        sFallback = reinterpret_cast<gralloc_module_t*>(dlsym(module, HAL_MODULE_INFO_SYM_AS_STR));
        if (sFallback == NULL) {
            dlclose(module);
        }
    }
    if (sFallback == NULL) {
        ALOGE("FATAL: Could not find gralloc.default.so!");
    }
}
