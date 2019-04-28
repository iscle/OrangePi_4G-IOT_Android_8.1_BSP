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

#define LOG_TAG "MtkAVStageFactory"
#include <utils/Log.h>

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaExtractor.h>
#include "MtkAVStageExtensions.h"

#ifdef MTK_WMV_PLAYBACK_SUPPORT
#include "include/ASFExtractor.h"
#endif
#ifdef MTK_FLV_PLAYBACK_SUPPORT
#include "include/MtkFLVExtractor.h"
#endif
#ifdef MTK_AVI_PLAYBACK_SUPPORT
#include "include/MtkAVIExtractor.h"
#endif
#ifdef MTK_AUDIO_APE_SUPPORT
#include "include/APEExtractor.h"
#endif
#ifdef MTK_AUDIO_ALAC_SUPPORT
#include "include/CAFExtractor.h"
#endif
#ifdef MTK_AUDIO_ADPCM_SUPPORT
#include "include/MtkADPCMExtractor.h"
#endif

#include <ui/GraphicBuffer.h>
#include <ui/Fence.h>
#include "include/MtkACodec.h"
#include "include/MtkMPEG4Writer.h"
#include "include/MtkMatroskaExtractor.h"
#include "include/MtkMPEG2TSExtractor.h"

namespace android {

MtkAVStageFactory::MtkAVStageFactory() {}

MtkAVStageFactory::~MtkAVStageFactory() {}

extern "C" MtkAVStageFactory* createVendorAVFactory() {
    return new MtkAVStageFactory;
}

MediaExtractor* MtkAVStageFactory::createExtractorExt(
         const sp<DataSource> &source, const char *mime, const sp<AMessage> &meta) {
    ALOGV("createExtractorExt: source %p mime %s meta %p", source.get(), mime, meta.get());
#ifdef MTK_WMV_PLAYBACK_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_ASF)) {
        return new ASFExtractor(source);
    }
#endif
#ifdef MTK_FLV_PLAYBACK_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_FLV)) {
        return new FLVExtractor(source);
    }
#endif
#ifdef MTK_AVI_PLAYBACK_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_AVI)) {
        return new MtkAVIExtractor(source);
    }
#endif
    if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MATROSKA)) {
        return new MtkMatroskaExtractor(source);
    }
    if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG2TS)) {
        return new MtkMPEG2TSExtractor(source);
    }
#ifdef MTK_AUDIO_APE_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_APE)) {
        return new APEExtractor(source, meta);
    }
#endif
#ifdef MTK_AUDIO_ALAC_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_ALAC)) {
        return new CAFExtractor(source, meta);
    }
#endif
#ifdef  MTK_AUDIO_ADPCM_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_ADPCM)) {
        return new MtkADPCMExtractor(source);
    }
#endif

    return NULL;
}

sp<ACodec> MtkAVStageFactory::createACodec() {
    return new MtkACodec;
}

sp<MPEG4Writer> MtkAVStageFactory::createMPEG4Writer(int fd) {
    return new MtkMPEG4Writer(fd);
}

}  // namespace android

