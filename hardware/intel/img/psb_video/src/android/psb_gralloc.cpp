/*
 * Copyright (c) 2011 Intel Corporation. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sub license, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice (including the
 * next paragraph) shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
 * IN NO EVENT SHALL PRECISION INSIGHT AND/OR ITS SUPPLIERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Authors:
 *    Fei Jiang  <fei.jiang@intel.com>
 *    Austin Yuan <austin.yuan@intel.com>
 *
 */

#include "android/psb_gralloc.h"
#include <cutils/log.h>
#include <utils/threads.h>
#include <ui/PixelFormat.h>
#include <hardware/gralloc.h>
#include <system/graphics.h>
#include <hardware/hardware.h>
#ifdef BAYTRAIL
#include <ufo/gralloc.h>
#else
#include <hal/hal_public.h>
#include <sync/sync.h>
#endif

using namespace android;

#ifdef  LOG_TAG
#undef  LOG_TAG
#endif

#define LOG_TAG "pvr_drv_video"

#ifdef BAYTRAIL
static const gralloc_module_t *mGralloc;
#else
static const hw_device_t *mGralloc;
#endif

int gralloc_lock(buffer_handle_t handle,
                 int usage, int left, int top, int width, int height,
                 void** vaddr)
{
    int err, j;

    if (!mGralloc) {
        ALOGW("%s: gralloc module has not been initialized. Should initialize it first", __func__);
        if (gralloc_init()) {
            ALOGE("%s: can't find the %s module", __func__, GRALLOC_HARDWARE_MODULE_ID);
            return -1;
        }
    }

#ifdef BAYTRAIL
    err = mGralloc->lock(mGralloc, handle, usage,
                         left, top, width, height,
                         vaddr);
#else
    const gralloc1_rect_t r = {
        .left   = left,
        .top    = top,
        .width  = width,
        .height = height
    };
    err = gralloc_lock_async_img(mGralloc, handle, usage, &r, vaddr, -1);
#endif
    ALOGV("gralloc_lock: handle is %p, usage is %x, vaddr is %p.\n", handle, usage, *vaddr);
    if (err){
        ALOGE("lock(...) failed %d (%s).\n", err, strerror(-err));
        return -1;
    } else {
        ALOGV("lock returned with address %p\n", *vaddr);
    }

    return err;
}

int gralloc_unlock(buffer_handle_t handle)
{
    int err;

    if (!mGralloc) {
        ALOGW("%s: gralloc module has not been initialized. Should initialize it first", __func__);
        if (gralloc_init()) {
            ALOGE("%s: can't find the %s module", __func__, GRALLOC_HARDWARE_MODULE_ID);
            return -1;
        }
    }

#ifdef BAYTRAIL
    err = mGralloc->unlock(mGralloc, handle);
#else
    int releaseFence = -1;
    err = gralloc_unlock_async_img(mGralloc, handle, &releaseFence);
    if (releaseFence >= 0) {
        sync_wait(releaseFence, -1);
        close(releaseFence);
    }
#endif
    if (err) {
        ALOGE("unlock(...) failed %d (%s)", err, strerror(-err));
        return -1;
    } else {
        ALOGV("unlock returned\n");
    }

    return err;
}

int gralloc_register(buffer_handle_t handle)
{
    int err = 0;

    if (!mGralloc) {
        ALOGW("%s: gralloc module has not been initialized.", __func__);
        if (gralloc_init()) {
            ALOGE("%s: can't find the %s module", __func__,
                    GRALLOC_HARDWARE_MODULE_ID);
            return -1;
        }
    }

    err = gralloc_register_img(mGralloc, handle);
    if (err) {
        ALOGE("%s failed with %d (%s).\n", __func__, err, strerror(-err));
        return -1;
    } else {
        ALOGV("registered buffer %p successfully\n", handle);
    }

    return err;
}

int gralloc_unregister(buffer_handle_t handle)
{
    int err = 0;

    if (!mGralloc) {
        ALOGW("%s: gralloc module has not been initialized.", __func__);
        if (gralloc_init()) {
            ALOGE("%s: can't find the %s module", __func__,
                    GRALLOC_HARDWARE_MODULE_ID);
            return -1;
        }
    }

    err = gralloc_unregister_img(mGralloc, handle);
    if (err) {
        ALOGE("%s failed with %d (%s).\n", __func__, err, strerror(-err));
        return -1;
    } else {
        ALOGV("unregistered buffer %p successfully\n", handle);
    }

    return err;
}

int gralloc_init(void)
{
    int err;

#ifdef BAYTRAIL
    err = hw_get_module(GRALLOC_HW_MODULE_ID, (const hw_module_t **)&mGralloc);
#else
    err = gralloc_open_img(&mGralloc);
#endif
    if (err) {
        ALOGE("FATAL: can't find the %s module", GRALLOC_HARDWARE_MODULE_ID);
        return -1;
    } else
        ALOGD("hw_get_module returned\n");

    return 0;
}

int gralloc_getdisplaystatus(buffer_handle_t handle,  int* status)
{
    int err;

#ifdef BAYTRAIL
    *status = mGralloc->perform(mGralloc, INTEL_UFO_GRALLOC_MODULE_PERFORM_GET_BO_STATUS, handle);
    err = 0;
#else
    uint32_t _status = 0U;
    err = gralloc_get_display_status_img(mGralloc, handle, &_status);
    *status = (int)_status;
#endif
    if (err){
        ALOGE("gralloc_getdisplaystatus(...) failed %d (%s).\n", err, strerror(-err));
        return -1;
    }

    return err;
}

int gralloc_getbuffd(buffer_handle_t handle)
{
    return ((IMG_native_handle_t*)handle)->fd[0];
}
