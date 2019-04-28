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

#define LOG_TAG "avc_utils_mtk"

#include <avc_utils.h>
#include <media/MediaDefs.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/foundation/ABitReader.h>
// #include <foundation/ADebug.h>
// #include <utils/misc.h>
// #include <utils/Log.h>
// #include <media/stagefright/foundation/hexdump.h>
// #include <media/stagefright/MediaErrors.h>

namespace android {
    void EncodeSize14_1(uint8_t **_ptr, size_t size) {
        // CHECK_LE(size, 0x3fff);
        if (size > 0x3fff) {
            ALOGE("Error in EncodeSize14_1, size(%zu) > 0x3fff", size);
        }

        uint8_t *ptr = *_ptr;

        *ptr++ = 0x80 | (size >> 7);
        *ptr++ = size & 0x7f;

        *_ptr = ptr;
    }

    sp<ABuffer> MakeESDS(const sp<ABuffer> &csd) {
        sp<ABuffer> esds = new ABuffer(csd->size() + 25);

        uint8_t *ptr = esds->data();
        *ptr++ = 0x03;
        EncodeSize14_1(&ptr, 22 + csd->size());

        *ptr++ = 0x00;  // ES_ID
        *ptr++ = 0x00;

        *ptr++ = 0x00;  // streamDependenceFlag, URL_Flag, OCRstreamFlag

        *ptr++ = 0x04;
        EncodeSize14_1(&ptr, 16 + csd->size());

        *ptr++ = 0x40;  // Audio ISO/IEC 14496-3

        for (size_t i = 0; i < 12; ++i) {
            *ptr++ = 0x00;
        }

        *ptr++ = 0x05;
        EncodeSize14_1(&ptr, csd->size());

        memcpy(ptr, csd->data(), csd->size());

        return esds;
    }

// add support for HEVC Codec Config data
/*turn 00 00 03 to 00 00*/
status_t AdjustSPS(uint8_t *sps, unsigned *spsLen) { // internal
    uint8_t *data = sps;
    size_t  size = *spsLen;
    size_t  offset = 0;

    while (offset + 2 <= size) {
        if (data[offset] == 0x00 && data[offset+1] == 0x00 && data[offset+2] == 0x03) {
            // found 00 00 03
            if (offset + 2 == size) {  // 00 00 03 as suffix
                *spsLen -= 1;
                return OK;
            }

            offset += 2;  // point to 0x03
            memcpy(data+offset, data+(offset+1), size - offset);  // cover ox03

            size -= 1;
            *spsLen -= 1;
            continue;
        }
        ++offset;
    }

    return OK;
}

// Determine video dimensions from the sequence parameterset.
void FindHEVCDimensions(
        const sp<ABuffer> &seqParamSet, int32_t *width, int32_t *height) { // internal
    ALOGI("FindHEVCDimensions ++");

    uint8_t* sps = seqParamSet->data();
    unsigned spsLen = seqParamSet->size();

    sps += 2;
    spsLen -= 2;
    AdjustSPS(sps, &spsLen);  // clear emulation_prevention_three_byte

    ABitReader br(sps, spsLen);

    br.skipBits(4);  // sps_video_parameter_set_id;
    unsigned sps_max_sub_layers_minus1 = br.getBits(3);
    ALOGI("sps_max_sub_layers_minus1 =%d", sps_max_sub_layers_minus1);
    br.skipBits(1);  // sps_temporal_id_nesting_flag;

    /*-----------profile_tier_level start-----------------------*/

    br.skipBits(3);  // general_profile_spac, general_tier_flag

    unsigned general_profile_idc = br.getBits(5);
    ALOGI("general_profile_idc =%d", general_profile_idc);
    br.skipBits(32);  // general_profile_compatibility_flag

    br.skipBits(48);  // general_constraint_indicator_flags

    unsigned general_level_idc = br.getBits(8);
    ALOGI("general_level_idc =%d", general_level_idc);

    uint8_t sub_layer_profile_present_flag[sps_max_sub_layers_minus1];
    uint8_t sub_layer_level_present_flag[sps_max_sub_layers_minus1];
    for (int i = 0; (unsigned)i < sps_max_sub_layers_minus1; i++) {
        sub_layer_profile_present_flag[i] = br.getBits(1);
        sub_layer_level_present_flag[i] = br.getBits(1);
    }

    if (sps_max_sub_layers_minus1 > 0) {
        for (int j = sps_max_sub_layers_minus1; j < 8; j++) {
            br.skipBits(2);
        }
    }
    for (int i = 0; (unsigned)i < sps_max_sub_layers_minus1; i++) {
        if (sub_layer_profile_present_flag[i]) {
            br.skipBits(2);   // sub_layer_profile_space
            br.skipBits(1);   // sub_layer_tier_flag
            br.skipBits(5);   // sub_layer_profile_idc
            br.skipBits(32);  // sub_layer_profile_compatibility_flag
            br.skipBits(48);  // sub_layer_constraint_indicator_flags
        }
        if (sub_layer_level_present_flag[i]) {
            br.skipBits(8);  // sub_layer_level_idc
        }
    }
    /*-----------profile_tier_level done-----------------------*/

    parseUE(&br);  // sps_seq_parameter_set_id
    unsigned chroma_format_idc, separate_colour_plane_flag;
    chroma_format_idc = parseUE(&br);
    ALOGI("chroma_format_idc=%d", chroma_format_idc);

    if (chroma_format_idc == 3) {
        separate_colour_plane_flag = br.getBits(1);
    }

    int32_t pic_width_in_luma_samples = parseUE(&br);
    int32_t pic_height_in_luma_samples = parseUE(&br);
    ALOGI("pic_width_in_luma_samples =%d", pic_width_in_luma_samples);
    ALOGI("pic_height_in_luma_samples =%d", pic_height_in_luma_samples);

    *width = pic_width_in_luma_samples;
    *height = pic_height_in_luma_samples;
    uint8_t conformance_window_flag = br.getBits(1);
    ALOGI("conformance_window_flag =%d", conformance_window_flag);
    if (conformance_window_flag) {
        unsigned conf_win_left_offset = parseUE(&br);
        unsigned conf_win_right_offset = parseUE(&br);
        unsigned conf_win_top_offset = parseUE(&br);
        unsigned conf_win_bottom_offset = parseUE(&br);

        *width -= conf_win_left_offset + conf_win_right_offset;
        *height -= conf_win_top_offset + conf_win_bottom_offset;

        ALOGI("frame_crop = (%u, %u, %u, %u)",
                conf_win_left_offset, conf_win_right_offset,
                conf_win_top_offset, conf_win_bottom_offset);
    }

    unsigned bit_depth_luma_minus8 = parseUE(&br);
    unsigned bit_depth_chroma_minus8 = parseUE(&br);
    ALOGI("bit_depth_luma_minus8 =%u, bit_depth_chroma_minus8 =%u", bit_depth_luma_minus8, bit_depth_chroma_minus8);
    ALOGI("FindHEVCDimensions --");
}
static sp<ABuffer> FindHEVCNAL(
        const uint8_t *data, size_t size, unsigned nalType,
        size_t *stopOffset __unused) {  // internal
    const uint8_t *nalStart;
    size_t nalSize;
    while (getNextNALUnit(&data, &size, &nalStart, &nalSize, true) == OK) {
        if (((nalStart[0] >> 1) & 0x3f) == nalType) {
            sp<ABuffer> buffer = new ABuffer(nalSize);
            memcpy(buffer->data(), nalStart, nalSize);
            return buffer;
        }
    }

    return NULL;
}
const char *HEVCProfileToString(uint8_t profile) { //internal
    switch (profile) {
        case kHEVCProfileMain:
            return "Main Profile";
        case kHEVCProfileMain10:
            return "Main 10 profile";
        case kHEVCProfileMainStillPicture:
            return "Main Still Picture profile";
        default:
            return "Unknown";
    }
}

sp<MetaData> MakeHEVCCodecSpecificData(const sp<ABuffer> &accessUnit) { //external
    ALOGI("MakeHEVCCodecSpecificData ++");
    const uint8_t *data = accessUnit->data();
    size_t size = accessUnit->size();
    size_t numOfParamSets = 0;
    const uint8_t VPS_NAL_TYPE = 32;
    const uint8_t SPS_NAL_TYPE = 33;
    const uint8_t PPS_NAL_TYPE = 34;

    // find vps,only choose the first vps,
    // need check whether need sent all the vps to decoder
    sp<ABuffer> videoParamSet = FindHEVCNAL(data, size, VPS_NAL_TYPE, NULL);
    if (videoParamSet == NULL) {
        ALOGW("no vps found !!!");
        // return NULL;
    } else {
        numOfParamSets++;
        ALOGI("find vps, size =%zu", videoParamSet->size());
    }

    // find sps,only choose the first sps,
    // need check whether need sent all the sps to decoder
    sp<ABuffer> seqParamSet = FindHEVCNAL(data, size, SPS_NAL_TYPE, NULL);
    if (seqParamSet == NULL) {
        ALOGW("no sps found !!!");
        return NULL;
    } else {
        numOfParamSets++;
        ALOGI("find sps, size =%zu", seqParamSet->size());
    }


    int32_t width, height;
    FindHEVCDimensions(seqParamSet, &width, &height);

    // find pps,only choose the first pps,
    // need check whether need sent all the pps to decoder
    size_t stopOffset;
    sp<ABuffer> picParamSet = FindHEVCNAL(data, size, PPS_NAL_TYPE, &stopOffset);
    if (picParamSet == NULL) {
        ALOGW("no sps found !!!");
        return NULL;
    } else {
        numOfParamSets++;
        ALOGI("find pps, size =%zu", picParamSet->size());
    }

    int32_t numbOfArrays = numOfParamSets;
    int32_t paramSetSize = 0;

    // only save one vps,sps,pps in codecConfig data
    if (videoParamSet != NULL) {
        paramSetSize += 1 + 2 + 2 + videoParamSet->size();
    }
    if (seqParamSet != NULL) {
        paramSetSize += 1 + 2 + 2 + seqParamSet->size();
    }
    if (picParamSet != NULL) {
        paramSetSize += 1 + 2 + 2 + picParamSet->size();
    }

    size_t csdSize =
        1 + 1 + 4 + 6 + 1 + 2 + 1 + 1 + 1 + 1 + 2 + 1
        + 1 + paramSetSize;
    ALOGI("MakeHEVCCodecSpecificData codec config data size =%zu", csdSize);
    sp<ABuffer> csd = new ABuffer(csdSize);
    uint8_t *out = csd->data();

    *out++ = 0x01;  // configurationVersion

    /*copy profile_tier_leve info in sps, containing
      1 byte:general_profile_space(2),general_tier_flag(1),general_profile_idc(5)
      4 bytes: general_profile_compatibility_flags, 6 bytes: general_constraint_indicator_flags
      1 byte:general_level_idc
     */
    memcpy(out, seqParamSet->data() + 3, 1 + 4 + 6 + 1);

    uint8_t profile = out[0] & 0x1f;
    uint8_t level = out[11];

    out += 1 + 4 + 6 + 1;

    *out++ = 0xf0;  // reserved(1111b) + min_spatial_segmentation_idc(4)
    *out++ = 0x00;  // min_spatial_segmentation_idc(8)
    *out++ = 0xfc;  // reserved(6bits,111111b) + parallelismType(2)(0=unknow,1=slices,2=tiles,3=WPP)
    *out++ = 0xfd;  // reserved(6bits,111111b)+chromaFormat(2)(0=monochrome, 1=4:2:0, 2=4:2:2, 3=4:4:4)

    *out++ = 0xf8;  // reserved(5bits,11111b) + bitDepthLumaMinus8(3)
    *out++ = 0xf8;  // reserved(5bits,11111b) + bitDepthChromaMinus8(3)

    uint16_t avgFrameRate = 0;
    // avgFrameRate (16bits,in units of frames/256 seconds,0 indicates an unspecified average frame rate)
    *out++ = avgFrameRate >> 8;
    *out++ = avgFrameRate & 0xff;

    // constantFrameRate(2bits,0=not be of constant frame rate),numTemporalLayers(3bits),temporalIdNested(1bits),
    *out++ = 0x03;
    // lengthSizeMinusOne(2bits)

    *out++ = numbOfArrays;  // numOfArrays

    if (videoParamSet != NULL) {
        *out++ = 0x3f & VPS_NAL_TYPE;  // array_completeness(1bit)+reserved(1bit,0)+NAL_unit_type(6bits)

        // num of vps
        uint16_t numNalus = 1;
        *out++ = numNalus >> 8;
        *out++ =  numNalus & 0xff;

        // vps nal length
        *out++ = videoParamSet->size() >> 8;
        *out++ = videoParamSet->size() & 0xff;

        memcpy(out, videoParamSet->data(), videoParamSet->size());
        out += videoParamSet->size();
    }

    if (seqParamSet != NULL) {
        *out++ = 0x3f & SPS_NAL_TYPE;  // array_completeness(1bit)+reserved(1bit,0)+NAL_unit_type(6bits)

        // num of sps
        uint16_t numNalus = 1;
        *out++ = numNalus >> 8;
        *out++ = numNalus & 0xff;

        // sps nal length
        *out++ = seqParamSet->size() >> 8;
        *out++ = seqParamSet->size() & 0xff;

            memcpy(out, seqParamSet->data(), seqParamSet->size());
            out += seqParamSet->size();
        }
        if (picParamSet != NULL) {
            *out++ = 0x3f & PPS_NAL_TYPE;  // array_completeness(1bit)+reserved(1bit,0)+NAL_unit_type(6bits)

            // num of pps
            uint16_t numNalus = 1;
            *out++ = numNalus >> 8;
            *out++ = numNalus & 0xff;

            // pps nal length
            *out++ = picParamSet->size() >> 8;
            *out++ = picParamSet->size() & 0xff;

            memcpy(out, picParamSet->data(), picParamSet->size());
            // no need add out offset
        }


        sp<MetaData> meta = new MetaData;
        meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);

        meta->setData(kKeyHVCC, kTypeHVCC, csd->data(), csd->size());
        meta->setInt32(kKeyWidth, width);
        meta->setInt32(kKeyHeight, height);

        ALOGI("found HEVC codec config (%d x %d, %s-profile level %d.%d)",
                width, height, HEVCProfileToString(profile), level / 10, level % 10);
        ALOGI("MakeHEVCCodecSpecificData --");
        return meta;
    }
}  // namespace android

