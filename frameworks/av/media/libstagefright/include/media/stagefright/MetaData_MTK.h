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

#ifndef META_DATA_MTK_H_
#define META_DATA_MTK_H_

#include <sys/types.h>

#include <stdint.h>

#include <binder/Parcel.h>
#include <utils/RefBase.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>

namespace android {

enum {
    // for mkv
    kKeyCodecInfoIsInFirstFrame = 'CIFF',  // int32(bool), hai.li,codec info is in the first frame

    // for wfd feature,
    kKeyWFDLatency = 'wfdl',  // uint32_t
    kKeyVideoTime = 'viti',  // uint32_t (msecs)

    // for camera hal1 slowmotion
    kKeySlowMotionTag = 'slom',  // int32_t

    // for ASFExtractor/MtkAVIExtractor/MtkFLVExtractor/MatroskaExtractor
    kKeyHasUnsupportVideo = 'UnSV',

    // for common use
    kKeyEndian = 'endi',  // endian --> endi
    kKeyBitWidth = 'bwid',  // bitwidth --> bwid
    kKeyPCMType = 'pcmT',  // pcmtype --> pcmT
    kKeyNumericalType = 'NuTy',  // numericalType --> NuTy

    kKeyBlockAlign = 'bkal',  // uint32_t
    kKeyBitsPerSample = 'btps',  // uint32_t
    kKeyExtraDataSize = 'exds',  // uint32_t
    kKeyExtraDataPointer = 'exdp',  // uint8_t*
    kKeyCodecConfigInfo = 'cinf',  // raw data
    kKeyWMAC = 'wmac',  // wma codec specific data
    kKeyAVIRawAac = 'avir',
    kKeyIsAACADIF = 'adif',  // int32_t (bool)
    kKeyMediaInfoFlag = 'infg',  // add for mtk defined infos in mediarecorder.h.

    kKeyPcmFormat = 'PmFt', // for 32bit float pcm format

    // for mp3
    kKeyMp3MultiRead = 'mp3M',
    kKeyMP3Extractor = 'iF3E',
    kKeyMtkMP3Power = 'mp3P',

    // for ape
    kkeyComptype            = 'ctyp',   // int16_t compress type
    kkeyApechl              = 'chls',   // int16_t compress type
    kkeyApebit              = 'bits',   // int16_t compress type
    kKeyTotalFrame         = 'alls',    // int32_t all frame in file
    kKeyFinalSample         = 'fins',   // int32_t last frame's sample
    kKeyBufferSize            = 'bufs',  // int32_t buffer size for ape
    kKeyNemFrame            = 'nfrm',   // int32_t seek frame's numbers for ape
    kKeySeekByte            = 'sekB',   // int32_t new seek first byte  for ape
    kKeyApeFlag            = 'apef',    // int32_t new seek first byte  for ape
    kKeySamplesperframe      = 'sapf',  // int32_t samples per frame
    // for ape seek
    kKeynewframe = 'sfrm',
    kKeyseekbyte = 'sebt',

    // for CAFExtractor
    kKeyALACC             = 'alac',  // alac codec specific data
    kKeyNumSamples        = 'nsmp',
    kKeyDataSourceObserver = 'dsob',      // pointer
    kKeyUpdateDuraCallback = 'udcb',      // pointer, update duration

    // for adpcm
    //kKeyBlockAlign        = 'bkal',  // uint32_t
    //kKeyBitsPerSample     = 'btps',  // uint32_t
    //kKeyExtraDataPointer  = 'exdp',  // uint8_t*
    kKeyBlockDurationUs   = 'bkdu',  // uint64_t
};

}  // namespace android

#endif  // META_DATA_MTK_H_
