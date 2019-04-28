/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#include <dlfcn.h>
#include <stdio.h>
#include <unistd.h>
#include "utils/Log.h"

static int inited = false;

static int (*fbcNotifyFrameComplete)(int, int, int, int64_t) = NULL;
static int (*fbcNotifySwapBuffers)(void) = NULL;
static int (*fbcNotifyNoRender)(int64_t) = NULL;

typedef int (*notify_frame_complete)(int, int, int, int64_t);
typedef int (*notify_swap_buffers)(void);
typedef int (*notify_no_render)(int64_t);

#define LIB_FULL_NAME "libperfctl.so"

#define HWUI 1

static void init()
{
    void *handle, *func;

    // only enter once
    inited = true;

    handle = dlopen(LIB_FULL_NAME, RTLD_NOW);
    if (handle == NULL) {
        ALOGE("Can't load library: %s", dlerror());
        return;
    }

    func = dlsym(handle, "fbcNotifyFrameComplete");
    fbcNotifyFrameComplete = reinterpret_cast<notify_frame_complete>(func);

    if (fbcNotifyFrameComplete == NULL) {
        ALOGE("fbcNotifyFrameComplete error: %s", dlerror());
        fbcNotifyFrameComplete = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "fbcNotifySwapBuffers");
    fbcNotifySwapBuffers = reinterpret_cast<notify_swap_buffers>(func);

    if (fbcNotifySwapBuffers == NULL) {
        ALOGE("fbcNotifySwapBuffers error: %s", dlerror());
        fbcNotifySwapBuffers = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "fbcNotifyNoRender");
    fbcNotifyNoRender = reinterpret_cast<notify_no_render>(func);

    if (fbcNotifyNoRender == NULL) {
        ALOGE("fbcNotifyNoRender error: %s", dlerror());
        fbcNotifyNoRender = NULL;
        dlclose(handle);
        return;
    }
}

int notifyFrameComplete(int tid, int duration, int64_t frame_id)
{
    if (!inited)
        init();

    //ALOGE("MTKfbc frame complete %d", duration);
    if (fbcNotifyFrameComplete) {
        return fbcNotifyFrameComplete(tid, duration, HWUI, frame_id);
    }

    return 0;
}

int notifySwapBuffers(void)
{
    if (!inited)
        init();

    //ALOGE("MTKfbc swap buffers");
    if (fbcNotifySwapBuffers) {
        return fbcNotifySwapBuffers();
    }

    return 0;
}

int notifyNoRender(int64_t frame_id)
{
    if (!inited)
        init();

    //ALOGE("MTKfbc swap buffers");
    if (fbcNotifyNoRender) {
        return fbcNotifyNoRender(frame_id);
    }

    return 0;
}
