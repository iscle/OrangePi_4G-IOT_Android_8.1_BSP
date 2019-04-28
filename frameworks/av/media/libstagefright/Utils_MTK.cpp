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

//#define LOG_DEBUG 0
#define LOG_TAG "Utils_MTK"

#include <utils/Log.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/Utils_MTK.h>

namespace android {

void convertMeta_Audio(const sp<MetaData> &meta, sp<AMessage> msg) {
    int32_t bitWidth;
    if (meta->findInt32(kKeyBitWidth, &bitWidth)) {
        msg->setInt32("bit-width", bitWidth);
        ALOGD("kKeyBitWidth in utils is %d", bitWidth);
    }

    int32_t isFromMp3Extractor;
    if (meta->findInt32(kKeyMP3Extractor, &isFromMp3Extractor)) {
        msg->setInt32("from-mp3extractor", isFromMp3Extractor);
        ALOGD("kKeyMP3Extractor in utils is %d", isFromMp3Extractor);
    }

    int32_t isAviRawAac;
    if (meta->findInt32(kKeyAVIRawAac, &isAviRawAac)) {
        msg->setInt32("is-rawAacInAvi", isAviRawAac);
    }

#ifdef MTK_AUDIO_APE_SUPPORT
    int32_t FinalSample;
    if (meta->findInt32(kKeyFinalSample, &FinalSample)) {
        msg->setInt32("ape-final-sample", FinalSample);
        ALOGD("kKeyFinalSample in utils is %d", FinalSample);
    }
    int32_t TotalFrame;
    if (meta->findInt32(kKeyTotalFrame, &TotalFrame)) {
        msg->setInt32("ape-total-frame", TotalFrame);
        ALOGD("kKeyTotalFrame in utils is %d", TotalFrame);
    }
    int32_t SampPerFrame;
    if (meta->findInt32(kKeySamplesperframe, &SampPerFrame)) {
        msg->setInt32("ape-sample-per-frame", SampPerFrame);
        ALOGD("kKeySamplesperframe in utils is %d", SampPerFrame);
    }
    int32_t CompType;
    if (meta->findInt32(kkeyComptype, &CompType)) {
        msg->setInt32("ape-compression-type", CompType);
        ALOGD("kkeyComptype in utils is %d", CompType);
    }
    int32_t FileType;
    if (meta->findInt32(kKeyFileType, &FileType)) {
        msg->setInt32("ape-file-type", FileType);
        ALOGD("kKeyFileType in utils is %d", FileType);
    }
    int32_t BufferSize;
    if (meta->findInt32(kKeyBufferSize, &BufferSize)) {
        msg->setInt32("ape-buffer-size", BufferSize);
        ALOGD("kKeyBufferSize in utils is %d", BufferSize);
    }
    int32_t ApeBitRate;
    if (meta->findInt32(kkeyApebit, &ApeBitRate)) {
        msg->setInt32("ape-bit-rate", ApeBitRate);
        ALOGD("kkeyApebit in utils is %d", ApeBitRate);
    }
    int32_t ApeChl;
    if (meta->findInt32(kkeyApechl, &ApeChl)) {
        msg->setInt32("ape-chl", ApeChl);
        ALOGD("kkeyApechl in utils is %d", ApeChl);
    }
    int64_t newframe;  // for ape seek on acodec
    if (meta->findInt64(kKeynewframe, &newframe)) {
        msg->setInt64("newframe", newframe);
    }

    int64_t seekbyte;  // for ape seek on acodec
    if (meta->findInt64(kKeyseekbyte, &seekbyte)) {
        msg->setInt64("seekbyte", seekbyte);
    }
#endif
#ifdef MTK_AUDIO_RAW_SUPPORT
    int32_t endian;
    if (meta->findInt32(kKeyEndian, &endian)) {
        msg->setInt32("endian", endian);
        ALOGD("kKeyEndian in utils is %d", endian);
    }

    int32_t numericalType;
    if (meta->findInt32(kKeyNumericalType, &numericalType)) {
        msg->setInt32("numerical-type", numericalType);
        ALOGD("kKeyNumericalType in utils is %d", numericalType);
    }

    int32_t pcmType;
    if (meta->findInt32(kKeyPCMType, &pcmType)) {
        msg->setInt32("pcm-type", pcmType);
        ALOGD("kKeyPCMType in utils is %d", pcmType);
    }

    int32_t pcmFormat;
    if (meta->findInt32(kKeyPcmFormat, &pcmFormat)) {
        msg->setInt32("pcm-format", pcmFormat);
        ALOGD("kKeyPcmFormat in utils is %d", pcmFormat);
    }
#endif
#ifdef MTK_AUDIO_ALAC_SUPPORT
    int32_t numSamples;
    if (meta->findInt32(kKeyNumSamples, &numSamples)) {
        msg->setInt32("number-samples", numSamples);
        ALOGD("kKeyNumSamples in utils is %d", numSamples);
    }
#endif
#ifdef MTK_AUDIO_ADPCM_SUPPORT
    int32_t blockAlign;
    if (meta->findInt32(kKeyBlockAlign, &blockAlign)) {
        msg->setInt32("block-align", blockAlign);
        ALOGD("kKeyBlockAlign in utils is %d", blockAlign);
    }

    int32_t bitPerSample;
    if (meta->findInt32(kKeyBitsPerSample, &bitPerSample)) {
        msg->setInt32("bit-per-sample", bitPerSample);
        ALOGD("kKeyBitsPerSample in utils is %d", bitPerSample);
    }
#endif
}
}  // namespace android
