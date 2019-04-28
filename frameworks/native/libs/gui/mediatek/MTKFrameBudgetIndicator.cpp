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

#define LOG_TAG "FBI"
#include <dlfcn.h>
#include <stdio.h>
#include <unistd.h>
#include "utils/Log.h"
#include "gui/mediatek/MTKFrameBudgetIndicator.h"
#include <linux/types.h>
#include <pthread.h>

static int (*fpNotifyQueue)(__u32, __u64, __s32) = nullptr;
static int (*fpNotifyDequeue)(__u32, __u64, __s32) = nullptr;
static int (*fpNotifyConnect)(__u32, __u64, __u32) = nullptr;
static void *handle = nullptr, *func = nullptr;

typedef int (*FpNotifyQueue)(__u32, __u64, __s32);
typedef int (*FpNotifyDequeue)(__u32, __u64, __s32);
typedef int (*FpNotifyConnect)(__u32, __u64, __u32);

#define LIB_FULL_NAME "libperfctl.so"

void fbcInit() {
    static bool inited = false;
    static pthread_mutex_t mMutex = PTHREAD_MUTEX_INITIALIZER;

    pthread_mutex_lock(&mMutex);
    if (inited) {
        pthread_mutex_unlock(&mMutex);
        return;
    }

    /*
     * Consider if library or funcion is missing, re-try helps
     * nothing but lots of error logs. Thus, just init once no
     * matter if anything is well-prepared or not. However,
     * entire init flow should be lock-protected.
     */
    inited = true;

    handle = dlopen(LIB_FULL_NAME, RTLD_NOW);
    if (handle == NULL) {
        ALOGE("Can't load library: %s", dlerror());
        pthread_mutex_unlock(&mMutex);
        return;
    }

    func = dlsym(handle, "xgfNotifyQueue");
    fpNotifyQueue = reinterpret_cast<FpNotifyQueue>(func);

    if (fpNotifyQueue == NULL) {
        ALOGE("xgfNotifyQueue error: %s", dlerror());
        goto err_fbcInit;
    }

    func = dlsym(handle, "xgfNotifyDequeue");
    fpNotifyDequeue = reinterpret_cast<FpNotifyDequeue>(func);

    if (fpNotifyDequeue == NULL) {
        ALOGE("xgfNotifyDequeue error: %s", dlerror());
        goto err_fbcInit;
    }

    func = dlsym(handle, "xgfNotifyConnect");
    fpNotifyConnect = reinterpret_cast<FpNotifyConnect>(func);

    if (fpNotifyConnect == NULL) {
        ALOGE("xgfNotifyConnect error: %s", dlerror());
        goto err_fbcInit;
    }

    pthread_mutex_unlock(&mMutex);
    return;

err_fbcInit:
    fpNotifyQueue = NULL;
    fpNotifyDequeue = NULL;
    dlclose(handle);
    pthread_mutex_unlock(&mMutex);
}

android::status_t notifyFbc(const int32_t& value, const uint64_t& bufID, const int32_t& param) {
    fbcInit();

    switch (value) {
        case FBC_CNT:
        case FBC_DISCNT:
            if (fpNotifyConnect) {
                const int32_t err = fpNotifyConnect(value == FBC_CNT ? 1 : 0, bufID, param);
                if (err != android::NO_ERROR) {
                    ALOGE("notifyConnect(%d %d) err:%d", value, param, err);
                    return android::INVALID_OPERATION;
                }
            } else {
                ALOGE("notifyConnect load error");
                return android::NO_INIT;
            }
            break;

        case FBC_QUEUE_BEG:
        case FBC_QUEUE_END:
            if (fpNotifyQueue) {
                const int32_t err = fpNotifyQueue(value == FBC_QUEUE_BEG ? 1 : 0, bufID, param);
                if (err != android::NO_ERROR) {
                    ALOGE("notifyQueue(%d) err:%d", value, err);
                    return android::INVALID_OPERATION;
                }
            } else {
                ALOGE("notifyQueue load error");
                return android::NO_INIT;
            }
            break;

        case FBC_DEQUEUE_BEG:
        case FBC_DEQUEUE_END:
            if (fpNotifyDequeue) {
                const int32_t err = fpNotifyDequeue(value == FBC_DEQUEUE_BEG ? 1 : 0, bufID, param);
                if (err != android::NO_ERROR) {
                    ALOGE("notifyDequeue(%d) err:%d", value, err);
                    return android::INVALID_OPERATION;
                }
            } else {
                ALOGE("notifyDequeue load error");
                return android::NO_INIT;
            }
            break;
    }
    return android::NO_ERROR;
}
