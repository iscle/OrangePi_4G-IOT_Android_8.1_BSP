/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2011-2015, The Linux Foundation. All rights reserved.
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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)
#include <limits.h>
#include <errno.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>
#include <stdarg.h>

#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ioctl.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <utils/Trace.h>

#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#include <gralloc1-adapter.h>

#include "gralloc_priv.h"
#include "gr.h"
#include "alloc_controller.h"
#include "memalloc.h"
#include <qdMetaData.h>


using namespace gralloc;
/*****************************************************************************/

// Return the type of allocator -
// these are used for mapping/unmapping
static IMemAlloc* getAllocator(int flags)
{
    IMemAlloc* memalloc;
    IAllocController* alloc_ctrl = IAllocController::getInstance();
    memalloc = alloc_ctrl->getAllocator(flags);
    return memalloc;
}

static int gralloc_map_metadata(buffer_handle_t handle) {
    private_handle_t* hnd = (private_handle_t*)handle;
    hnd->base_metadata = 0;
    IMemAlloc* memalloc = getAllocator(hnd->flags) ;
    void *mappedAddress = MAP_FAILED;
    unsigned int size = 0;
    if (!(hnd->flags & private_handle_t::PRIV_FLAGS_FRAMEBUFFER)) {
        mappedAddress = MAP_FAILED;
        size = ROUND_UP_PAGESIZE(sizeof(MetaData_t));
        int ret = memalloc->map_buffer(&mappedAddress, size,
                                       hnd->offset_metadata, hnd->fd_metadata);
        if(ret || mappedAddress == MAP_FAILED) {
            ALOGE("Could not mmap metadata for handle %p, fd=%d (%s)",
                  hnd, hnd->fd_metadata, strerror(errno));
            return -errno;
        }
        hnd->base_metadata = uint64_t(mappedAddress);
    }
    return 0;
}

static int gralloc_map(gralloc_module_t const* module,
                       buffer_handle_t handle)
{
    ATRACE_CALL();
    if(!module)
        return -EINVAL;

    private_handle_t* hnd = (private_handle_t*)handle;
    unsigned int size = 0;
    int err = 0;
    IMemAlloc* memalloc = getAllocator(hnd->flags) ;
    void *mappedAddress = MAP_FAILED;
    hnd->base = 0;

    // Dont map framebuffer and secure buffers
    if (!(hnd->flags & private_handle_t::PRIV_FLAGS_FRAMEBUFFER) &&
        !(hnd->flags & private_handle_t::PRIV_FLAGS_SECURE_BUFFER)) {
        size = hnd->size;
        err = memalloc->map_buffer(&mappedAddress, size,
                                       hnd->offset, hnd->fd);
        if(err || mappedAddress == MAP_FAILED) {
            ALOGE("Could not mmap handle %p, fd=%d (%s)",
                  handle, hnd->fd, strerror(errno));
            return -errno;
        }

        hnd->base = uint64_t(mappedAddress);
    } else {
        // Cannot map secure buffers or framebuffers, but still need to map
        // metadata for secure buffers.
        // If mapping a secure buffers fails, the framework needs to get
        // an error code.
        err = -EACCES;
    }

    //Allow mapping of metadata for all buffers including secure ones, but not
    //of framebuffer
    int metadata_err = gralloc_map_metadata(handle);
    if(!err) {
        err = metadata_err;
    }
    return err;
}

static int gralloc_unmap(gralloc_module_t const* module,
                         buffer_handle_t handle)
{
    ATRACE_CALL();
    int err = -EINVAL;
    if(!module)
        return err;

    private_handle_t* hnd = (private_handle_t*)handle;
    IMemAlloc* memalloc = getAllocator(hnd->flags) ;
    if(!memalloc)
        return err;

    if(hnd->base) {
        err = memalloc->unmap_buffer((void*)hnd->base, hnd->size, hnd->offset);
        if (err) {
            ALOGE("Could not unmap memory at address %p, %s", (void*) hnd->base,
                    strerror(errno));
            return -errno;
        }
        hnd->base = 0;
    }

    if(hnd->base_metadata) {
        unsigned int size = ROUND_UP_PAGESIZE(sizeof(MetaData_t));
        err = memalloc->unmap_buffer((void*)hnd->base_metadata,
                size, hnd->offset_metadata);
        if (err) {
            ALOGE("Could not unmap memory at address %p, %s",
                    (void*) hnd->base_metadata, strerror(errno));
            return -errno;
        }
        hnd->base_metadata = 0;
    }

    return 0;
}

/*****************************************************************************/

static pthread_mutex_t sMapLock = PTHREAD_MUTEX_INITIALIZER;

/*****************************************************************************/

int gralloc_register_buffer(gralloc_module_t const* module,
                            buffer_handle_t handle)
{
    ATRACE_CALL();
    if (!module || private_handle_t::validate(handle) < 0)
        return -EINVAL;

    int err =  gralloc_map(module, handle);
    /* Do not fail register_buffer for secure buffers*/
    if (err == -EACCES)
        err = 0;
    return err;
}

int gralloc_unregister_buffer(gralloc_module_t const* module,
                              buffer_handle_t handle)
{
    ATRACE_CALL();
    if (!module || private_handle_t::validate(handle) < 0)
        return -EINVAL;

    /*
     * If the buffer has been mapped during a lock operation, it's time
     * to un-map it. It's an error to be here with a locked buffer.
     * NOTE: the framebuffer is handled differently and is never unmapped.
     * Also base and base_metadata are reset.
     */
    return gralloc_unmap(module, handle);
}

int terminateBuffer(gralloc_module_t const* module,
                    private_handle_t* hnd)
{
    ATRACE_CALL();
    if(!module)
        return -EINVAL;

    /*
     * If the buffer has been mapped during a lock operation, it's time
     * to un-map it. It's an error to be here with a locked buffer.
     * NOTE: the framebuffer is handled differently and is never unmapped.
     * Also base and base_metadata are reset.
     */
    return gralloc_unmap(module, hnd);
}

static int gralloc_map_and_invalidate (gralloc_module_t const* module,
                                       buffer_handle_t handle, int usage)
{
    ATRACE_CALL();
    if (!module || private_handle_t::validate(handle) < 0)
        return -EINVAL;

    int err = 0;
    private_handle_t* hnd = (private_handle_t*)handle;
    if (usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK)) {
        if (hnd->base == 0) {
            // we need to map for real
            pthread_mutex_t* const lock = &sMapLock;
            pthread_mutex_lock(lock);
            err = gralloc_map(module, handle);
            pthread_mutex_unlock(lock);
        }
        if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION and
                hnd->flags & private_handle_t::PRIV_FLAGS_CACHED) {
            //Invalidate if CPU reads in software and there are non-CPU
            //writers. No need to do this for the metadata buffer as it is
            //only read/written in software.
            if ((usage & GRALLOC_USAGE_SW_READ_MASK) and
                    (hnd->flags & private_handle_t::PRIV_FLAGS_NON_CPU_WRITER))
            {
                IMemAlloc* memalloc = getAllocator(hnd->flags) ;
                err = memalloc->clean_buffer((void*)hnd->base,
                        hnd->size, hnd->offset, hnd->fd,
                        CACHE_INVALIDATE);
            }
            //Mark the buffer to be flushed after CPU write.
            if (usage & GRALLOC_USAGE_SW_WRITE_MASK) {
                hnd->flags |= private_handle_t::PRIV_FLAGS_NEEDS_FLUSH;
            }
        }
    }

    return err;
}

int gralloc_lock(gralloc_module_t const* module,
                 buffer_handle_t handle, int usage,
                 int /*l*/, int /*t*/, int /*w*/, int /*h*/,
                 void** vaddr)
{
    ATRACE_CALL();
    private_handle_t* hnd = (private_handle_t*)handle;
    int err = gralloc_map_and_invalidate(module, handle, usage);
    if(!err)
        *vaddr = (void*)hnd->base;
    return err;
}

int gralloc_lock_ycbcr(gralloc_module_t const* module,
                 buffer_handle_t handle, int usage,
                 int /*l*/, int /*t*/, int /*w*/, int /*h*/,
                 struct android_ycbcr *ycbcr)
{
    ATRACE_CALL();
    private_handle_t* hnd = (private_handle_t*)handle;
    int err = gralloc_map_and_invalidate(module, handle, usage);
    if(!err)
        err = getYUVPlaneInfo(hnd, ycbcr);
    return err;
}

int gralloc_unlock(gralloc_module_t const* module,
                   buffer_handle_t handle)
{
    ATRACE_CALL();
    if (!module || private_handle_t::validate(handle) < 0)
        return -EINVAL;

    int err = 0;
    private_handle_t* hnd = (private_handle_t*)handle;

    IMemAlloc* memalloc = getAllocator(hnd->flags);
    if (hnd->flags & private_handle_t::PRIV_FLAGS_NEEDS_FLUSH) {
        err = memalloc->clean_buffer((void*)hnd->base,
                hnd->size, hnd->offset, hnd->fd,
                CACHE_CLEAN);
        hnd->flags &= ~private_handle_t::PRIV_FLAGS_NEEDS_FLUSH;
    }

    return err;
}

/*****************************************************************************/

static bool isYUV(private_handle_t* hnd)
{
    bool is_yuv;

    switch (hnd->format) {
        //Semiplanar
        case HAL_PIXEL_FORMAT_YCbCr_420_SP:
        case HAL_PIXEL_FORMAT_YCbCr_422_SP:
        case HAL_PIXEL_FORMAT_YCbCr_420_SP_VENUS:
        case HAL_PIXEL_FORMAT_NV12_ENCODEABLE: //Same as YCbCr_420_SP_VENUS
        case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        case HAL_PIXEL_FORMAT_YCrCb_422_SP:
        case HAL_PIXEL_FORMAT_YCrCb_420_SP_ADRENO:
        case HAL_PIXEL_FORMAT_NV21_ZSL:
        case HAL_PIXEL_FORMAT_RAW10:
        case HAL_PIXEL_FORMAT_RAW16:
        //Planar
        case HAL_PIXEL_FORMAT_YV12:
            is_yuv = true;
        break;
        //Unsupported formats
        case HAL_PIXEL_FORMAT_YCbCr_422_I:
        case HAL_PIXEL_FORMAT_YCrCb_422_I:
        case HAL_PIXEL_FORMAT_YCbCr_420_SP_TILED:
        default:
            is_yuv = false;
            break;
    }

    return is_yuv;
}

static void ycbcr_to_flexible_layout(const struct android_ycbcr* ycbcr,
        struct android_flex_layout* layout)
{
    layout->format = FLEX_FORMAT_YCbCr;
    layout->num_planes = 3;

    for (uint32_t i = 0; i < layout->num_planes; i++) {
        layout->planes[i].bits_per_component = 8;
        layout->planes[i].bits_used = 8;
        layout->planes[i].h_increment = 1;
        layout->planes[i].v_increment = 1;
        layout->planes[i].h_subsampling = 2;
        layout->planes[i].v_subsampling = 2;
    }

    layout->planes[0].top_left = (uint8_t*)ycbcr->y;
    layout->planes[0].component = FLEX_COMPONENT_Y;
    layout->planes[0].v_increment = (int32_t)ycbcr->ystride;

    layout->planes[1].top_left = (uint8_t*)ycbcr->cb;
    layout->planes[1].component = FLEX_COMPONENT_Cb;
    layout->planes[1].h_increment = (int32_t)ycbcr->chroma_step;
    layout->planes[1].v_increment = (int32_t)ycbcr->cstride;

    layout->planes[2].top_left = (uint8_t*)ycbcr->cr;
    layout->planes[2].component = FLEX_COMPONENT_Cr;
    layout->planes[2].h_increment = (int32_t)ycbcr->chroma_step;
    layout->planes[2].v_increment = (int32_t)ycbcr->cstride;
}

int gralloc_perform(struct gralloc_module_t const* module,
                    int operation, ... )
{
    int res = -EINVAL;
    va_list args;
    if(!module)
        return res;

    va_start(args, operation);
    switch (operation) {
        case GRALLOC_MODULE_PERFORM_CREATE_HANDLE_FROM_BUFFER:
            {
                int fd = va_arg(args, int);
                unsigned int size = va_arg(args, unsigned int);
                unsigned int offset = va_arg(args, unsigned int);
                void* base = va_arg(args, void*);
                int width = va_arg(args, int);
                int height = va_arg(args, int);
                int format = va_arg(args, int);

                native_handle_t** handle = va_arg(args, native_handle_t**);
                private_handle_t* hnd = (private_handle_t*)native_handle_create(
                    private_handle_t::sNumFds, private_handle_t::sNumInts());
                hnd->magic = private_handle_t::sMagic;
                hnd->fd = fd;
                hnd->flags =  private_handle_t::PRIV_FLAGS_USES_ION;
                hnd->size = size;
                hnd->offset = offset;
                hnd->base = uint64_t(base) + offset;
                hnd->gpuaddr = 0;
                hnd->width = width;
                hnd->height = height;
                hnd->format = format;
                *handle = (native_handle_t *)hnd;
                res = 0;
                break;

            }
        case GRALLOC_MODULE_PERFORM_GET_STRIDE:
            {
                int width   = va_arg(args, int);
                int format  = va_arg(args, int);
                int *stride = va_arg(args, int *);
                int alignedw = 0, alignedh = 0;
                AdrenoMemInfo::getInstance().getAlignedWidthAndHeight(width,
                        0, format, 0, alignedw, alignedh);
                *stride = alignedw;
                res = 0;
            } break;

        case GRALLOC_MODULE_PERFORM_GET_CUSTOM_STRIDE_FROM_HANDLE:
            {
                private_handle_t* hnd =  va_arg(args, private_handle_t*);
                int *stride = va_arg(args, int *);
                if (private_handle_t::validate(hnd)) {
                    return res;
                }
                MetaData_t *metadata = (MetaData_t *)hnd->base_metadata;
                if(metadata && metadata->operation & UPDATE_BUFFER_GEOMETRY) {
                    *stride = metadata->bufferDim.sliceWidth;
                } else {
                    *stride = hnd->width;
                }
                res = 0;
            } break;

        case GRALLOC_MODULE_PERFORM_GET_CUSTOM_STRIDE_AND_HEIGHT_FROM_HANDLE:
            {
                private_handle_t* hnd =  va_arg(args, private_handle_t*);
                int *stride = va_arg(args, int *);
                int *height = va_arg(args, int *);
                if (private_handle_t::validate(hnd)) {
                    return res;
                }
                MetaData_t *metadata = (MetaData_t *)hnd->base_metadata;
                if(metadata && metadata->operation & UPDATE_BUFFER_GEOMETRY) {
                    *stride = metadata->bufferDim.sliceWidth;
                    *height = metadata->bufferDim.sliceHeight;
                } else {
                    *stride = hnd->width;
                    *height = hnd->height;
                }
                res = 0;
            } break;

        case GRALLOC_MODULE_PERFORM_GET_ATTRIBUTES:
            {
                int width   = va_arg(args, int);
                int height  = va_arg(args, int);
                int format  = va_arg(args, int);
                int usage   = va_arg(args, int);
                int *alignedWidth = va_arg(args, int *);
                int *alignedHeight = va_arg(args, int *);
                int *tileEnabled = va_arg(args,int *);
                *tileEnabled = isMacroTileEnabled(format, usage);
                AdrenoMemInfo::getInstance().getAlignedWidthAndHeight(width,
                        height, format, usage, *alignedWidth, *alignedHeight);
                res = 0;
            } break;

        case GRALLOC_MODULE_PERFORM_GET_COLOR_SPACE_FROM_HANDLE:
            {
                private_handle_t* hnd =  va_arg(args, private_handle_t*);
                int *color_space = va_arg(args, int *);
                if (private_handle_t::validate(hnd)) {
                    return res;
                }
                MetaData_t *metadata = (MetaData_t *)hnd->base_metadata;
                if(metadata && metadata->operation & UPDATE_COLOR_SPACE) {
                    *color_space = metadata->colorSpace;
                    res = 0;
                }
            } break;

        case GRALLOC_MODULE_PERFORM_GET_YUV_PLANE_INFO:
            {
                private_handle_t* hnd =  va_arg(args, private_handle_t*);
                android_ycbcr* ycbcr = va_arg(args, struct android_ycbcr *);
                if (!private_handle_t::validate(hnd)) {
                    res = getYUVPlaneInfo(hnd, ycbcr);
                }
            } break;

        case GRALLOC_MODULE_PERFORM_GET_MAP_SECURE_BUFFER_INFO:
            {
                private_handle_t* hnd =  va_arg(args, private_handle_t*);
                int *map_secure_buffer = va_arg(args, int *);
                if (private_handle_t::validate(hnd)) {
                    return res;
                }
                MetaData_t *metadata = (MetaData_t *)hnd->base_metadata;
                if(metadata && metadata->operation & MAP_SECURE_BUFFER) {
                    *map_secure_buffer = metadata->mapSecureBuffer;
                    res = 0;
                } else {
                    *map_secure_buffer = 0;
                }
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_REAL_MODULE_API_VERSION_MINOR:
            {
                auto outMinorVersion = va_arg(args, int*);
                *outMinorVersion = 1; // GRALLOC_MODULE_API_VERSION_0_1
            } break;

        case GRALLOC1_ADAPTER_PERFORM_SET_USAGES:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto producerUsage = va_arg(args, int);
                auto consumerUsage = va_arg(args, int);
                hnd->producer_usage = producerUsage;
                hnd->consumer_usage = consumerUsage;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_DIMENSIONS:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto outWidth = va_arg(args, int*);
                auto outHeight = va_arg(args, int*);
                *outWidth = hnd->original_width;
                *outHeight = hnd->height;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_FORMAT:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto outFormat = va_arg(args, int*);
                *outFormat = hnd->original_format;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_PRODUCER_USAGE:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto outUsage = va_arg(args, int*);
                *outUsage = hnd->producer_usage;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_CONSUMER_USAGE:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto outUsage = va_arg(args, int*);
                *outUsage = hnd->consumer_usage;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_BACKING_STORE:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto outBackingStore = va_arg(args, uint64_t*);
                *outBackingStore = hnd->backing_store;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_NUM_FLEX_PLANES:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto outNumFlexPlanes = va_arg(args, int*);

                (void) hnd;
                // for simpilicity
                *outNumFlexPlanes = 4;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_GET_STRIDE:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto outStride = va_arg(args, int*);
                *outStride = hnd->width;
            } break;

        case GRALLOC1_ADAPTER_PERFORM_LOCK_FLEX:
            {
                auto hnd =  va_arg(args, private_handle_t*);
                auto producerUsage = va_arg(args, int);
                auto consumerUsage = va_arg(args, int);
                auto left = va_arg(args, int);
                auto top = va_arg(args, int);
                auto width = va_arg(args, int);
                auto height = va_arg(args, int);
                auto outLayout = va_arg(args, android_flex_layout*);
                // always -1
                auto acquireFence = va_arg(args, int);
                (void) acquireFence;

                // TODO lock RGB as a flexible format
                if (!isYUV(hnd)) {
                    return -EINVAL;
                }

                struct android_ycbcr ycbcr;
                res = gralloc_lock_ycbcr(module, hnd,
                        producerUsage | consumerUsage,
                        left, top, width, height, &ycbcr);
                if (res != 0) {
                    return res;
                }

                ycbcr_to_flexible_layout(&ycbcr, outLayout);
            } break;

        default:
            break;
    }
    va_end(args);
    return res;
}
