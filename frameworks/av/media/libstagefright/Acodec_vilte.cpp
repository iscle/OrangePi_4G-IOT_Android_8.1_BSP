/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
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
/*****************************************************************************
 *
 * Filename:
 * ---------
 *   Acodec_vilte.cpp
 *
 * Project:
 * --------
 *   MT6573
 *
 * Description:
 * ------------
 *   Vilte support
 *
 ****************************************************************************/
#ifdef MTK_VILTE_SUPPORT
#include <utils/Log.h>
#undef LOG_TAG
#define LOG_TAG "ACodec_vilte"

#include <inttypes.h>
#include <utils/Trace.h>

#include <gui/Surface.h>

#include <media/stagefright/ACodec.h>

#include <binder/MemoryDealer.h>

#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AUtils.h>

#include <media/stagefright/BufferProducerWrapper.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/MediaCodecList.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/OMXClient.h>
#include <media/stagefright/PersistentSurface.h>
#include <media/stagefright/SurfaceUtils.h>
#include <media/hardware/HardwareAPI.h>
#include <media/OMXBuffer.h>
#include <media/omx/1.0/WOmxNode.h>

#include <hidlmemory/mapping.h>

#include <media/openmax/OMX_AudioExt.h>
#include <media/openmax/OMX_VideoExt.h>
#include <media/openmax/OMX_Component.h>
#include <media/openmax/OMX_IndexExt.h>
#include <media/openmax/OMX_AsString.h>

#include "include/avc_utils.h"
#include "include/ACodecBufferChannel.h"
#include "include/DataConverter.h"
#include "include/SecureBuffer.h"
#include "include/SharedMemoryBuffer.h"
#include <media/stagefright/omx/OMXUtils.h>

#include <android/hidl/allocator/1.0/IAllocator.h>
#include <android/hidl/memory/1.0/IMemory.h>

namespace android {

status_t ACodec::ResolutionChange(int32_t width, int32_t height) {

    OMX_VIDEO_PARAM_RESOLUTION config;
    InitOMXParams(&config);
    config.nFrameWidth = (OMX_U32)width;
    config.nFrameHeight = (OMX_U32)height;

    ALOGI("ACodec::ResolutionChange");

    status_t temp = mOMXNode->setConfig(
            (OMX_INDEXTYPE)OMX_IndexVendorMtkOmxVencSeResolutionChange,
            &config, sizeof(config));
    if (temp != OK) {
        ALOGI("codec does not support config resolution change (err %d)", temp);
        return BAD_VALUE;
    }
    return OK;
}

status_t ACodec::setViLTEParameters(const sp<AMessage> &msg, bool fgCheckResolutionChange)
{
    status_t err = OK;

    int32_t width = 0;
    int32_t height = 0;

    {
        OMX_PARAM_U32TYPE param;
        InitOMXParams(&param);
        param.nPortIndex = mIsEncoder ? kPortIndexOutput : kPortIndexInput;

        int32_t flag = 0;
        if(msg->findInt32("vdec-lowlatency", &flag)){
           OMX_INDEXTYPE index = OMX_IndexMax;
           err = mOMXNode->getExtensionIndex("OMX.MTK.index.param.video.LowLatencyDecode", &index);
           if (err == OK) {
               param.nU32 = flag;
               ALOGD("setParameter vdec-lowlatency");
               mOMXNode->setParameter(index, &param, sizeof(param));
           }
        }

    }
    {
        int32_t ViLTE = 0;
        OMX_PARAM_U32TYPE param;
        InitOMXParams(&param);
        param.nPortIndex = mIsEncoder ? kPortIndexOutput : kPortIndexInput;

        if (msg->findInt32("vilte-mode", &ViLTE) && ViLTE)
        {
            OMX_INDEXTYPE index = OMX_IndexMax;

            if (mIsEncoder)
            {
                err = mOMXNode->getExtensionIndex(
                    "OMX.MTK.index.param.video.SetVencScenario",
                    &index);
                if (err == OK) {
                    param.nU32 = OMX_VIDEO_MTKSpecificScenario_ViLTE;
                    err = mOMXNode->setParameter(
                        index, &param, sizeof(param));
                    mIsViLTE = true;
                }
                else {
                    ALOGE("setParameter('OMX.MTK.index.param.video.SetVencScenario') "
                        "returned error 0x%08x", err);
                }
            }
            else
            {
                err = mOMXNode->getExtensionIndex(
                    "OMX.MTK.index.param.video.SetVdecScenario",
                    &index);
                if (err == OK) {
                    param.nU32 = (OMX_U32)OMX_VIDEO_MTKSpecificScenario_ViLTE;
                    err = mOMXNode->setParameter(
                            index, &param, sizeof(param));
                }
                else {
                    ALOGE("setParameter('OMX.MTK.index.param.video.SetVdecScenario') "
                        "returned error 0x%08x", err);
                }
            }
        }

    }

    {
        int32_t iFrameInterval = 0;
        if (msg->findInt32("i-frame-interval", &iFrameInterval)) {
            //We can set AVC I-frame-interval by default declaration
            //OMX_IndexConfigVideoAVCIntraPeriod
            if (!mIsEncoder) {
                return ERROR_UNSUPPORTED;
            }
            ALOGI("set I frame rate");
            OMX_INDEXTYPE index;
            OMX_PARAM_U32TYPE mFrameIntervalInfo;
            InitOMXParams(&mFrameIntervalInfo);
            mFrameIntervalInfo.nPortIndex = mIsEncoder ? kPortIndexOutput : kPortIndexInput;
            mFrameIntervalInfo.nU32 = iFrameInterval;

            err = mOMXNode->getExtensionIndex(
                        "OMX.MTK.index.param.video.EncSetIFrameRate",
                        &index);
            if (err == OK) {
                //OMX_BOOL enable = OMX_TRUE;
                err = mOMXNode->setConfig(index, &mFrameIntervalInfo, sizeof(mFrameIntervalInfo));

                if (err != OK) {
                    ALOGE("setConfig('OMX.MTK.index.param.video.EncSetIFrameRate') "
                          "returned error 0x%08x", err);
                    return err;
                }
            }
            else {
                ALOGE("Get I Frame Rate Extension Fail!");
                return err;
            }
        }
    }

    {
        int32_t iFrameRate = 0;
        if (msg->findInt32("frame-rate", &iFrameRate)) {
            if (!mIsEncoder) {
                return ERROR_UNSUPPORTED;
            }
            ALOGI("set framerate");
            OMX_CONFIG_FRAMERATETYPE    framerateType;
            InitOMXParams(&framerateType);
            framerateType.nPortIndex = kPortIndexOutput;
            framerateType.xEncodeFramerate = iFrameRate<<16;
            err = mOMXNode->setConfig(
                    OMX_IndexConfigVideoFramerate,
                    &framerateType, sizeof(framerateType));

            if (err != OK) {
                ALOGE("setConfig(OMX_IndexConfigVideoFramerate) "
                      "returned error 0x%08x", err);
                return err;
            }
        }
    }

    {
        sp<ABuffer> buffer;
        if (msg->findBuffer("sli", &buffer))
        {
            OMX_CONFIG_SLICE_LOSS_INDICATION SliceLossIndication;
            InitOMXParams(&SliceLossIndication);

            SliceLossIndication.nSize = sizeof(OMX_CONFIG_SLICE_LOSS_INDICATION);
            SliceLossIndication.nPortIndex = kPortIndexOutput;
            SliceLossIndication.nSliceCount = buffer->size() / sizeof(OMX_U32);
            memcpy(SliceLossIndication.SliceLoss, buffer->data(), buffer->size());

            OMX_INDEXTYPE index;
            err = mOMXNode->getExtensionIndex(
                        "OMX.MTK.index.param.video.SlicelossIndication",
                        &index);

            if (err == OK) {
                mOMXNode->setConfig(
                    index,
                    &SliceLossIndication, sizeof(SliceLossIndication));
            }
        }
    }

    {
        int32_t dummy;
        if (msg->findInt32("request-full-sync", &dummy)) {
            ALOGI("request full IDR frame");
            OMX_INDEXTYPE index;
            OMX_PARAM_U32TYPE pSetForceFullIInfo;
            InitOMXParams(&pSetForceFullIInfo);
            pSetForceFullIInfo.nPortIndex = mIsEncoder ? kPortIndexOutput : kPortIndexInput;

            err = mOMXNode->getExtensionIndex(
                "OMX.MTK.index.param.video.EncSetForceFullIframe",
                &index);

            if (err == OK) {
                pSetForceFullIInfo.nU32 = OMX_TRUE;
                err = mOMXNode->setConfig(index, &pSetForceFullIInfo, sizeof(pSetForceFullIInfo));

                if (err != OK) {
                    ALOGE("Requesting a full sync frame failed w/ err %d", err);
                }
            }
        }
    }

    if (msg->findMessage("avpf-notify", &mAVPFNotify))
    {
        ALOGI("avpf-notify is registered");
    }

    if (msg->findInt32("width", &width))
    {
        ALOGI("setMTKParameters, width: %d", width);
    }

    if (msg->findInt32("height", &height))
    {
        ALOGI("setMTKParameters, height: %d", height);
    }

    if (mIsViLTE == true && fgCheckResolutionChange && width != 0 && height!= 0) {
        err = ResolutionChange(width, height);
        return err;
    }

    return OK;
}

bool ACodec::signalViLTEError(OMX_ERRORTYPE error, status_t internalError)
{
    bool fgViLTE_error = false;

    if(error == OMX_ErrorSliceLossIndication ||
               error == OMX_ErrorPictureLossIndication ||
               error == OMX_ErrorFullIntraRequestStart ||
               error == OMX_ErrorFullIntraRequestEnd) {
        if (mAVPFNotify != NULL)
        {
            if (error == OMX_ErrorSliceLossIndication)
            {
                ALOGW("onEvent--OMX_ErrorSliceLossIndication!!");

                OMX_CONFIG_SLICE_LOSS_INDICATION SliceLossIndication;
                OMX_INDEXTYPE index;
                status_t err =
                    mOMXNode->getExtensionIndex(
                            "OMX.MTK.index.param.video.SlicelossIndication",
                            &index);

                sp<ABuffer> buffer;
                InitOMXParams(&SliceLossIndication);
                if (err == OK) {
                    if(mOMXNode->getConfig(
                        index,
                        &SliceLossIndication, sizeof(SliceLossIndication)) != OK)
                        ALOGE("get config SliceLossIndication fail");
                    else {
                        sp<AMessage> avpfnotify = mAVPFNotify->dup();

                        buffer = new ABuffer(SliceLossIndication.nSliceCount * sizeof(OMX_U32));

                        if (buffer->size() > 0)
                        {
                            memcpy(buffer->data(), SliceLossIndication.SliceLoss, SliceLossIndication.nSliceCount * sizeof(OMX_U32));
                            avpfnotify->setBuffer("sli", buffer);
                        }
                        avpfnotify->setInt32("err", internalError);
                        avpfnotify->setInt32("actionCode", ACTION_CODE_FATAL); // could translate from OMX error.
                        avpfnotify->post();
                    }
                }
            } else {
                sp<AMessage> avpfnotify = mAVPFNotify->dup();

                avpfnotify->setInt32("err", internalError);
                avpfnotify->setInt32("actionCode", ACTION_CODE_FATAL); // could translate from OMX error.
                avpfnotify->post();
            }
        }
        else
        {
            ALOGW("no avpf callback is register!!");
        }
        fgViLTE_error = true;

    }

    return fgViLTE_error;
}

}  // namespace android

#endif
