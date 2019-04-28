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

#define LOG_TAG "MtkAVStageUtils"
#include <utils/Log.h>

#include "MtkAVStageExtensions.h"
#include <media/stagefright/MediaExtractor.h>

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
#include "include/MtkMPEG2TSExtractor.h"

namespace android {

MtkAVStageUtils::MtkAVStageUtils() {}

MtkAVStageUtils::~MtkAVStageUtils() {}

extern "C" MtkAVStageUtils* createVendorAVUtils() {
    return new MtkAVStageUtils;
}

void MtkAVStageUtils::RegisterSniffer_ext() {
#ifdef MTK_WMV_PLAYBACK_SUPPORT
    MediaExtractor::RegisterSniffer_l(SniffASF);
#endif
#ifdef MTK_FLV_PLAYBACK_SUPPORT
    MediaExtractor::RegisterSniffer_l(SniffFLV);
#endif
#ifdef MTK_AVI_PLAYBACK_SUPPORT
    MediaExtractor::RegisterSniffer_l(MtkSniffAVI);
#endif
#ifdef MTK_AUDIO_APE_SUPPORT
    MediaExtractor::RegisterSniffer_l(SniffAPE);
#endif
#ifdef MTK_AUDIO_ALAC_SUPPORT
    MediaExtractor::RegisterSniffer_l(SniffCAF);
#endif
#ifdef MTK_AUDIO_ADPCM_SUPPORT
    MediaExtractor::RegisterSniffer_l(SniffADPCM);
#endif
    MediaExtractor::RegisterSniffer_l(MtkSniffMPEG2TS);
}

bool MtkAVStageUtils::isVendorAcceptableExtension(const char *extension) {
    static const char *kValidExtensions[] = {
        ".m2ts", ".mts",".oga",".m4v",".3ga",
#ifdef MTK_FLV_PLAYBACK_SUPPORT
        ".flv", ".f4v",
#endif
#ifdef MTK_AUDIO_APE_SUPPORT
        ".ape",
#endif
#ifdef MTK_AUDIO_ALAC_SUPPORT
        ".caf",
#endif
#ifdef MTK_DRM_APP
        ".dcf",
#endif
#ifdef MTK_MP2_PLAYBACK_SUPPORT
        ".mp2",
#endif
    };

    static const size_t kNumValidExtensions =
        sizeof(kValidExtensions) / sizeof(kValidExtensions[0]);

    for (size_t i = 0; i < kNumValidExtensions; ++i) {
        if (!strcasecmp(extension, kValidExtensions[i])) {
            return true;
        }
    }

    return false;
}

}  // namespace android

