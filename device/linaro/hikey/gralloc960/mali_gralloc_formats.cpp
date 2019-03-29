/*
 * Copyright (C) 2016 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#include <string.h>
#include <dlfcn.h>
#include <hardware/gralloc.h>
#include <inttypes.h>
#include <cutils/log.h>

#include "mali_gralloc_formats.h"
#include "gralloc_priv.h"

static mali_gralloc_format_caps dpu_runtime_caps;
static mali_gralloc_format_caps vpu_runtime_caps;
static mali_gralloc_format_caps gpu_runtime_caps;
static mali_gralloc_format_caps cam_runtime_caps;
static pthread_mutex_t caps_init_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool runtime_caps_read = false;

#define MALI_GRALLOC_GPU_LIB_NAME "libGLES_mali.so"
#if defined(__LP64__)
#define MALI_GRALLOC_GPU_LIBRARY_PATH1 "/vendor/lib64/egl/"
#define MALI_GRALLOC_GPU_LIBRARY_PATH2 "/system/lib64/egl/"
#else
#define MALI_GRALLOC_GPU_LIBRARY_PATH1 "/vendor/lib/egl/"
#define MALI_GRALLOC_GPU_LIBRARY_PATH2 "/system/lib/egl/"
#endif

static bool get_block_capabilities(bool hal_module, const char *name, mali_gralloc_format_caps *block_caps)
{
    void *dso_handle = NULL;
    bool rval = false;

    /* Look for MALI_GRALLOC_FORMATCAPS_SYM_NAME_STR symbol in user-space drivers
     * to determine hw format capabilities.
     */
    if(!hal_module)
    {
        dso_handle = dlopen(name, RTLD_LAZY);
    }
    else
    {
        /* libhardware does some heuristics to find hal modules
         * and then stores the dso handle internally. Use this.
         */
        const struct hw_module_t *module = {NULL};

        if(hw_get_module(name, &module) >= 0)
        {
            dso_handle = module->dso;
        }
    }

    if(dso_handle)
    {
        void *sym = dlsym(dso_handle, MALI_GRALLOC_FORMATCAPS_SYM_NAME_STR);

        if(sym)
        {
            memcpy((void*) block_caps, sym, sizeof(mali_gralloc_format_caps));
            rval = true;
        }

        if(!hal_module)
        {
            dlclose(dso_handle);
        }
    }

    return rval;
}

static int map_flex_formats(int req_format, uint64_t *producer_runtime_mask)
{
    /* Map Android flexible formats to internal base formats */
    if(req_format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED ||
       req_format == HAL_PIXEL_FORMAT_YCbCr_420_888)
    {
        req_format = MALI_GRALLOC_FORMAT_INTERNAL_NV12;

        /*
         * We disable AFBC for NV12 since neither VPU or DPU DDKs support
         * them currently.
         */
        *producer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
    }
    else if(req_format == HAL_PIXEL_FORMAT_YCbCr_422_888)
    {
        /* To be determined */

        /* Disable AFBC until we know though */
        *producer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
    }
    else if(req_format == HAL_PIXEL_FORMAT_YCbCr_444_888)
    {
        /* To be determined */

        /* Disable AFBC until we know though */
        *producer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
    }
    return req_format;
}

static bool is_afbc_supported(int req_format_mapped)
{
    bool rval = true;

    /* These base formats we currently don't support with compression */
    switch(req_format_mapped)
    {
        case MALI_GRALLOC_FORMAT_INTERNAL_RAW16:
        case MALI_GRALLOC_FORMAT_INTERNAL_RAW12:
        case MALI_GRALLOC_FORMAT_INTERNAL_RAW10:
        case MALI_GRALLOC_FORMAT_INTERNAL_BLOB:
        case MALI_GRALLOC_FORMAT_INTERNAL_P010:
        case MALI_GRALLOC_FORMAT_INTERNAL_P210:
        case MALI_GRALLOC_FORMAT_INTERNAL_Y410:
        case HAL_PIXEL_FORMAT_YCbCr_422_I:
            rval = false;
            break;
    }
    return rval;
}

static bool is_android_yuv_format(int req_format)
{
    bool rval = false;

    switch(req_format)
    {
        case HAL_PIXEL_FORMAT_YV12:
        case HAL_PIXEL_FORMAT_Y8:
        case HAL_PIXEL_FORMAT_Y16:
        case HAL_PIXEL_FORMAT_YCbCr_420_888:
        case HAL_PIXEL_FORMAT_YCbCr_422_888:
        case HAL_PIXEL_FORMAT_YCbCr_444_888:
            rval = true;
            break;
    }
    return rval;
}

static bool is_afbc_allowed(int buffer_size)
{
    bool afbc_allowed = false;

    (void) buffer_size;

#if GRALLOC_DISP_W != 0 && GRALLOC_DISP_H != 0
    afbc_allowed = ((buffer_size*100) / (GRALLOC_DISP_W*GRALLOC_DISP_H)) >= GRALLOC_AFBC_MIN_SIZE;

#else
    /* If display size is not valid then always allow AFBC */
    afbc_allowed = true;

#endif

    return afbc_allowed;
}

static bool is_afbc_format(uint64_t internal_format)
{
    return (internal_format & MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK) != 0;
}

static uint64_t determine_best_format(int req_format, mali_gralloc_producer_type producer, mali_gralloc_consumer_type consumer,
                                      uint64_t producer_runtime_mask, uint64_t consumer_runtime_mask)
{
    /* Default is to return the requested format */
    uint64_t internal_format = req_format;
    uint64_t dpu_mask = dpu_runtime_caps.caps_mask;
    uint64_t gpu_mask = gpu_runtime_caps.caps_mask;
    uint64_t vpu_mask = vpu_runtime_caps.caps_mask;
    uint64_t cam_mask = cam_runtime_caps.caps_mask;

    if(producer == MALI_GRALLOC_PRODUCER_GPU && gpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
    {
        gpu_mask &= producer_runtime_mask;

        if(consumer == MALI_GRALLOC_CONSUMER_GPU_OR_DISPLAY)
        {
            gpu_mask &= consumer_runtime_mask;
            dpu_mask &= consumer_runtime_mask;

            if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK &&
               dpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK)
            {
                internal_format |= MALI_GRALLOC_INTFMT_AFBC_SPLITBLK;
            }
            else if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC &&
                    dpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC)
            {
                internal_format |= MALI_GRALLOC_INTFMT_AFBC_BASIC;

                if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS &&
                   dpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS)
                {
                    internal_format |= MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS;
                }
            }
        }
        else if(consumer == MALI_GRALLOC_CONSUMER_GPU_EXCL)
        {
            gpu_mask &= consumer_runtime_mask;

            /* When GPU acts as both producer and consumer it prefers 16x16 superblocks */
            if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC)
            {
                internal_format |= MALI_GRALLOC_INTFMT_AFBC_BASIC;
            }

            if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS)
            {
                internal_format |= MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS;
            }
        }
        else if(consumer == MALI_GRALLOC_CONSUMER_VIDEO_ENCODER)
        {
            vpu_mask &= consumer_runtime_mask;

            if(req_format == HAL_PIXEL_FORMAT_YV12)
            {
                if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC &&
                   vpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC)
                {
                    internal_format |= MALI_GRALLOC_INTFMT_AFBC_BASIC;
                }

                if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS &&
                   vpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS)
                {
                    internal_format |= MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS;
                }
            }
        }
    }
    else if(producer == MALI_GRALLOC_PRODUCER_VIDEO_DECODER && vpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
    {
        vpu_mask &= producer_runtime_mask;

        if(consumer == MALI_GRALLOC_CONSUMER_GPU_OR_DISPLAY)
        {
            gpu_mask &= consumer_runtime_mask;
            dpu_mask &= consumer_runtime_mask;

            if(internal_format == HAL_PIXEL_FORMAT_YV12)
            {
                if(vpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC &&
                   gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC &&
                   dpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC)
                {
                    internal_format |= MALI_GRALLOC_INTFMT_AFBC_BASIC;
                }

                if(vpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS &&
                   gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS &&
                   dpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS)
                {
                    internal_format |= MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS;
                }
            }
        }
        else if(consumer == MALI_GRALLOC_CONSUMER_GPU_EXCL)
        {
            gpu_mask &= consumer_runtime_mask;

            if(internal_format == HAL_PIXEL_FORMAT_YV12)
            {
                if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC &&
                   vpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC)
                {
                    internal_format |= MALI_GRALLOC_INTFMT_AFBC_BASIC;
                }

                if(gpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS &&
                   vpu_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS)
                {
                    internal_format |= MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS;
                }
            }
        }
        else if(consumer == MALI_GRALLOC_CONSUMER_VIDEO_ENCODER)
        {
            /* Fall-through. To be decided.*/
        }
  }
  else if(producer == MALI_GRALLOC_PRODUCER_CAMERA && cam_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
  {
        if(consumer == MALI_GRALLOC_CONSUMER_GPU_OR_DISPLAY)
        {
            /* Fall-through. To be decided.*/
        }
        else if(consumer == MALI_GRALLOC_CONSUMER_GPU_EXCL)
        {
            /* Fall-through. To be decided.*/
        }
        else if(consumer == MALI_GRALLOC_CONSUMER_VIDEO_ENCODER)
        {
            /* Fall-through. To be decided.*/
        }
  }
  return internal_format;
}

static uint64_t decode_internal_format(int req_format)
{
    uint64_t internal_format, me_mask, base_format, mapped_base_format;
    uint64_t ignore_mask;

    internal_format = GRALLOC_PRIVATE_FORMAT_UNWRAP(req_format);

    me_mask = internal_format & MALI_GRALLOC_INTFMT_ME_EXT_MASK;
    if(me_mask > 0 && ((me_mask - 1) & me_mask) != 0)
    {
        ALOGE("Internal format contains multiple mutually exclusive modifier bits: %" PRIx64, internal_format);
        internal_format = 0;
        goto out;
    }

    base_format = internal_format & MALI_GRALLOC_INTFMT_FMT_MASK;

    /* Even though private format allocations are intended to be for specific
     * formats, certain test cases uses the flexible formats that needs to be mapped
     * to internal ones.
     */
    mapped_base_format = map_flex_formats((uint32_t ) base_format, &ignore_mask);

    /* Validate the internal base format passed in */
    switch(mapped_base_format)
    {
        case MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888:
        case MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888:
        case MALI_GRALLOC_FORMAT_INTERNAL_RGB_888:
        case MALI_GRALLOC_FORMAT_INTERNAL_RGB_565:
        case MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888:
        case MALI_GRALLOC_FORMAT_INTERNAL_YV12:
        case MALI_GRALLOC_FORMAT_INTERNAL_Y8:
        case MALI_GRALLOC_FORMAT_INTERNAL_Y16:
        case MALI_GRALLOC_FORMAT_INTERNAL_RAW16:
        case MALI_GRALLOC_FORMAT_INTERNAL_RAW12:
        case MALI_GRALLOC_FORMAT_INTERNAL_RAW10:
        case MALI_GRALLOC_FORMAT_INTERNAL_BLOB:
        case MALI_GRALLOC_FORMAT_INTERNAL_NV12:
        case MALI_GRALLOC_FORMAT_INTERNAL_NV21:
        case MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT:
        case MALI_GRALLOC_FORMAT_INTERNAL_Y0L2:
        case MALI_GRALLOC_FORMAT_INTERNAL_P010:
        case MALI_GRALLOC_FORMAT_INTERNAL_P210:
        case MALI_GRALLOC_FORMAT_INTERNAL_Y210:
        case MALI_GRALLOC_FORMAT_INTERNAL_Y410:
            if(mapped_base_format != base_format)
            {
                internal_format = (internal_format & MALI_GRALLOC_INTFMT_EXT_MASK) | mapped_base_format;
            }
            break;

        default:
            ALOGE("Internal base format requested is unrecognized: %" PRIx64 ,internal_format);
            internal_format = 0;
            break;
    }
out:
    return internal_format;
}

static bool determine_producer(mali_gralloc_producer_type *producer, uint64_t *producer_runtime_mask, int req_format, int usage)
{
    bool rval = true;

    /* Default to GPU */
    *producer = MALI_GRALLOC_PRODUCER_GPU;

    if(usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK))
    {
        rval = false;
    }
    else if(usage & GRALLOC_USAGE_HW_RENDER)
    {
        if(is_android_yuv_format(req_format))
        {
            if(gpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_NOWRITE)
            {
                *producer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
            }
            else
            {
                /* All GPUs that can write YUV AFBC can only do it in 16x16, optionally with tiled */
                *producer_runtime_mask &= ~(MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK | MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK);
            }
        }
        *producer = MALI_GRALLOC_PRODUCER_GPU;
    }
    else if(usage & GRALLOC_USAGE_HW_CAMERA_MASK)
    {
        *producer = MALI_GRALLOC_PRODUCER_CAMERA;
    }
    /* HW_TEXTURE+HW_COMPOSER+EXTERNAL_DISP is a definition set by
     * stagefright for "video decoder". We check for it here.
     */
    else if((usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_EXTERNAL_DISP)) ==
                    (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_EXTERNAL_DISP))
    {
        *producer = MALI_GRALLOC_PRODUCER_VIDEO_DECODER;
    }

   return rval;
}

static bool determine_consumer(mali_gralloc_consumer_type *consumer, uint64_t *consumer_runtime_mask, int req_format, int usage)
{
    bool rval = true;

    /* Default to GPU */
    *consumer = MALI_GRALLOC_CONSUMER_GPU_EXCL;

    if(usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK))
    {
        rval = false;
    }
    /* When usage explicitly targets a consumer, as it does with GRALLOC_USAGE_HW_FB,
     * we pick DPU even if there are no runtime capabilities present.
     */
    else if( usage & GRALLOC_USAGE_HW_FB )
    {
        *consumer = MALI_GRALLOC_CONSUMER_GPU_OR_DISPLAY;
    }
    else if(usage & GRALLOC_USAGE_HW_VIDEO_ENCODER)
    {
        if((vpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_NOREAD) &&
           is_android_yuv_format(req_format))
        {
            *consumer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
        }
        *consumer = MALI_GRALLOC_CONSUMER_VIDEO_ENCODER;
    }
    /* GRALLOC_USAGE_HW_COMPOSER is by default applied by SurfaceFlinger so we can't exclusively rely on it
     * to determine consumer. When a buffer is targeted for either we reject the DPU when it lacks
     * runtime capabilities, in favor of the more capable GPU.
     */
    else if((usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER )) == (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER ) &&
            dpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
    {
        *consumer = MALI_GRALLOC_CONSUMER_GPU_OR_DISPLAY;
    }
    else if(usage & GRALLOC_USAGE_HW_TEXTURE)
    {
        *consumer = MALI_GRALLOC_CONSUMER_GPU_EXCL;
    }
    return rval;
}

/*
 * Here we determine format capabilities for the 4 IPs we support.
 * For now these are controlled by build defines, but in the future
 * they should be read out from each user-space driver.
 */
static void determine_format_capabilities()
{
    /* Loading libraries can take some time and
     * we may see many allocations at boot.
     */
    pthread_mutex_lock(&caps_init_mutex);

    if(runtime_caps_read)
    {
        goto already_init;
    }

    memset((void*) &dpu_runtime_caps,0,sizeof(dpu_runtime_caps));
    memset((void*) &vpu_runtime_caps,0,sizeof(vpu_runtime_caps));
    memset((void*) &gpu_runtime_caps,0,sizeof(gpu_runtime_caps));
    memset((void*) &cam_runtime_caps,0,sizeof(cam_runtime_caps));

    /* Determine DPU format capabilities */
    if(!get_block_capabilities(true, "hwcomposer", &dpu_runtime_caps))
    {
#if MALI_DISPLAY_VERSION >= 500
        dpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
        dpu_runtime_caps.caps_mask |=  MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;

#if MALI_DISPLAY_VERSION >= 550
        dpu_runtime_caps.caps_mask |=  MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
#endif
#endif
    }

    /* Determine GPU format capabilities */
    if(access(MALI_GRALLOC_GPU_LIBRARY_PATH1 MALI_GRALLOC_GPU_LIB_NAME,R_OK) == 0)
    {
        get_block_capabilities(false, MALI_GRALLOC_GPU_LIBRARY_PATH1 MALI_GRALLOC_GPU_LIB_NAME, &gpu_runtime_caps);
    }
    else if(access(MALI_GRALLOC_GPU_LIBRARY_PATH2 MALI_GRALLOC_GPU_LIB_NAME,R_OK) == 0)
    {
        get_block_capabilities(false, MALI_GRALLOC_GPU_LIBRARY_PATH2 MALI_GRALLOC_GPU_LIB_NAME, &gpu_runtime_caps);
    }

    if((gpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT) == 0)
    {
        ALOGW("Failed to find GPU block configuration in %s. Using static build configuration.", MALI_GRALLOC_GPU_LIB_NAME);

#if MALI_GPU_SUPPORT_AFBC_BASIC == 1
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;

        /* Need to verify when to remove this */
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_NOWRITE;

#if MALI_SUPPORT_AFBC_SPLITBLK == 1
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
#endif

#if MALI_SUPPORT_AFBC_WIDEBLK == 1
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK;
#endif

#if MALI_USE_YUV_AFBC_WIDEBLK != 1
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK_YUV_DISABLE;
#endif

#if MALI_SUPPORT_AFBC_TILED_HEADERS == 1
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK;
        gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS;
#endif
#endif /* MALI_GPU_SUPPORT_AFBC_BASIC == 1 */
    }

    /* Determine VPU format capabilities */
#if MALI_VIDEO_VERSION == 500 || MALI_VIDEO_VERSION == 550
    vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
    vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;
    vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_NOREAD;
#endif

#if MALI_VIDEO_VERSION == 61
    vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
    vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;
    vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS;
#endif


    /* Build specific capability changes */
#if GRALLOC_ARM_NO_EXTERNAL_AFBC == 1
    {
        dpu_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
        gpu_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
        vpu_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
        cam_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
    }
#endif

    runtime_caps_read = true;

already_init:
    pthread_mutex_unlock(&caps_init_mutex);

    ALOGV("GPU format capabilities 0x%" PRIx64 , gpu_runtime_caps.caps_mask);
    ALOGV("DPU format capabilities 0x%" PRIx64 , dpu_runtime_caps.caps_mask);
    ALOGV("VPU format capabilities 0x%" PRIx64 , vpu_runtime_caps.caps_mask);
    ALOGV("CAM format capabilities 0x%" PRIx64 , cam_runtime_caps.caps_mask);
}

uint64_t mali_gralloc_select_format(int req_format, int usage, int buffer_size)
{
    uint64_t internal_format = 0;
    mali_gralloc_consumer_type consumer;
    mali_gralloc_producer_type producer;
    uint64_t producer_runtime_mask = ~(0ULL);
    uint64_t consumer_runtime_mask = ~(0ULL);
    int req_format_mapped=0;

    if(!runtime_caps_read)
    {
        /*
         * It is better to initialize these when needed because
         * not all processes allocates memory.
         */
        determine_format_capabilities();
    }

    /* A unique usage specifies that an internal format is in req_format */
    if(usage & MALI_GRALLOC_USAGE_PRIVATE_FORMAT)
    {
        internal_format = decode_internal_format(req_format);
        goto out;
    }

    /* Re-map special Android formats */
    req_format_mapped = map_flex_formats(req_format, &producer_runtime_mask);

    /* Determine producer/consumer */
    if(!determine_producer(&producer, &producer_runtime_mask, req_format, usage) ||
       !determine_consumer(&consumer, &consumer_runtime_mask, req_format, usage))
    {
        /* Failing to determine producer/consumer usually means
         * client has requested sw rendering.
         */
        internal_format = req_format_mapped;
        goto out;
    }

    /*
     * Determine runtime capability limitations
     */

    /* Disable AFBC based on unique usage */
    if ((usage & MALI_GRALLOC_USAGE_NO_AFBC) == MALI_GRALLOC_USAGE_NO_AFBC)
    {
        if(is_android_yuv_format(req_format_mapped))
        {
            ALOGE("It is invalid to specify NO_AFBC usage flags when allocating YUV formats.\
                   Requested fmt: 0x%08X Re-Mapped fmt: 0x%08X",req_format,req_format_mapped);
            internal_format = 0;
            goto out;
        }
        producer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
    }
    /* Disable AFBC based on buffer dimensions */
    else if(!is_afbc_allowed(buffer_size))
    {
        producer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
    }
    else if(!is_afbc_supported(req_format_mapped))
    {
        producer_runtime_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
    }

    /* Automatically select format in case producer/consumer identified */
    internal_format = determine_best_format(req_format_mapped, producer, consumer, producer_runtime_mask, consumer_runtime_mask);

out:
    ALOGV("mali_gralloc_select_format: req_format=0x%08X req_fmt_mapped=0x%08X internal_format=0x%" PRIx64 " usage=0x%08X",req_format, req_format_mapped, internal_format, usage);

    return internal_format;
}

extern "C"
{
void mali_gralloc_get_gpu_caps(struct mali_gralloc_format_caps *gpu_caps)
{
    if(gpu_caps != NULL)
    {
        if(!runtime_caps_read)
        {
            determine_format_capabilities();
        }
        memcpy(gpu_caps,(void*) &gpu_runtime_caps,sizeof(struct mali_gralloc_format_caps));
    }
}
}
