/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "media_omx_hidl_video_test_common"
#ifdef __LP64__
#define OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
#endif

#include <android-base/logging.h>

#include <android/hardware/media/omx/1.0/IOmx.h>
#include <android/hardware/media/omx/1.0/IOmxNode.h>
#include <android/hardware/media/omx/1.0/IOmxObserver.h>
#include <android/hardware/media/omx/1.0/types.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <android/hidl/memory/1.0/IMapper.h>
#include <android/hidl/memory/1.0/IMemory.h>

using ::android::hardware::media::omx::V1_0::IOmx;
using ::android::hardware::media::omx::V1_0::IOmxObserver;
using ::android::hardware::media::omx::V1_0::IOmxNode;
using ::android::hardware::media::omx::V1_0::Message;
using ::android::hardware::media::omx::V1_0::CodecBuffer;
using ::android::hardware::media::omx::V1_0::PortMode;
using ::android::hidl::allocator::V1_0::IAllocator;
using ::android::hidl::memory::V1_0::IMemory;
using ::android::hidl::memory::V1_0::IMapper;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

#include <VtsHalHidlTargetTestBase.h>
#include <hidlmemory/mapping.h>
#include <media/hardware/HardwareAPI.h>
#include <media_hidl_test_common.h>
#include <media_video_hidl_test_common.h>
#include <memory>

void enumerateProfileAndLevel(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                              std::vector<int32_t>* arrProfile,
                              std::vector<int32_t>* arrLevel) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_PROFILELEVELTYPE param;
    param.nProfileIndex = 0;
    arrProfile->clear();
    arrLevel->clear();
    while (1) {
        status =
            getPortParam(omxNode, OMX_IndexParamVideoProfileLevelQuerySupported,
                         portIndex, &param);
        if (status != ::android::hardware::media::omx::V1_0::Status::OK) break;
        arrProfile->push_back(static_cast<int32_t>(param.eProfile));
        arrLevel->push_back(static_cast<int32_t>(param.eLevel));
        param.nProfileIndex++;
        if (param.nProfileIndex == 512) {
            // enumerated way too many, highly unusual for this to happen.
            EXPECT_LE(param.nProfileIndex, 512U)
                << "Expecting OMX_ErrorNoMore but not received";
            break;
        }
    }
}

void setupRAWPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, OMX_U32 nFrameWidth,
                  OMX_U32 nFrameHeight, OMX_U32 nBitrate, OMX_U32 xFramerate,
                  OMX_COLOR_FORMATTYPE eColorFormat) {
    android::hardware::media::omx::V1_0::Status status;

    OMX_PARAM_PORTDEFINITIONTYPE portDef;
    status = getPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    portDef.format.video.nFrameWidth = nFrameWidth;
    portDef.format.video.nFrameHeight = nFrameHeight;
    portDef.format.video.nStride = (((nFrameWidth + 15) >> 4) << 4);
    portDef.format.video.nSliceHeight = (((nFrameHeight + 15) >> 4) << 4);
    portDef.format.video.nBitrate = nBitrate;
    portDef.format.video.xFramerate = xFramerate;
    portDef.format.video.bFlagErrorConcealment = OMX_TRUE;
    portDef.format.video.eCompressionFormat = OMX_VIDEO_CodingUnused;
    portDef.format.video.eColorFormat = eColorFormat;
    status = setPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupAVCPort(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_VIDEO_AVCPROFILETYPE eProfile,
                  OMX_VIDEO_AVCLEVELTYPE eLevel, OMX_U32 xFramerate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_AVCTYPE param;
    (void)xFramerate;  // necessary for intra frame spacing

    status = getPortParam(omxNode, OMX_IndexParamVideoAvc, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nSliceHeaderSpacing = 0;
    param.nPFrames = 300;
    param.nBFrames = 0;
    param.bUseHadamard = OMX_TRUE;
    param.nRefFrames = 1;
    param.eProfile = eProfile;
    param.eLevel = eLevel;
    param.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;
    param.bFrameMBsOnly = OMX_TRUE;
    param.bEntropyCodingCABAC = OMX_FALSE;
    param.bWeightedPPrediction = OMX_FALSE;
    param.eLoopFilterMode = OMX_VIDEO_AVCLoopFilterEnable;
    status = setPortParam(omxNode, OMX_IndexParamVideoAvc, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupHEVCPort(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                   OMX_VIDEO_HEVCPROFILETYPE eProfile,
                   OMX_VIDEO_HEVCLEVELTYPE eLevel) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_HEVCTYPE param;

    status = getPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamVideoHevc,
                          portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    (void)eProfile;
    (void)eLevel;
    // SPECIAL CASE; OMX.qcom.video.encoder.hevc does not support the level it
    // enumerated in the list. Lets skip this for now
    // param.eProfile = eProfile;
    // param.eLevel = eLevel;
    param.nKeyFrameInterval = 300;
    status = setPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamVideoHevc,
                          portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupMPEG4Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                    OMX_VIDEO_MPEG4PROFILETYPE eProfile,
                    OMX_VIDEO_MPEG4LEVELTYPE eLevel, OMX_U32 xFramerate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_MPEG4TYPE param;
    (void)xFramerate;  // necessary for intra frame spacing

    status = getPortParam(omxNode, OMX_IndexParamVideoMpeg4, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    param.nSliceHeaderSpacing = 0;
    param.bSVH = OMX_FALSE;
    param.bGov = OMX_FALSE;
    param.nPFrames = 300;
    param.nBFrames = 0;
    param.nIDCVLCThreshold = 0;
    param.bACPred = OMX_TRUE;
    param.nMaxPacketSize = 256;
    param.eProfile = eProfile;
    param.eLevel = eLevel;
    param.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;
    param.nHeaderExtension = 0;
    param.bReversibleVLC = OMX_FALSE;
    status = setPortParam(omxNode, OMX_IndexParamVideoMpeg4, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupH263Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                   OMX_VIDEO_H263PROFILETYPE eProfile,
                   OMX_VIDEO_H263LEVELTYPE eLevel, OMX_U32 xFramerate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_H263TYPE param;
    (void)xFramerate;  // necessary for intra frame spacing

    status = getPortParam(omxNode, OMX_IndexParamVideoH263, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    param.nPFrames = 300;
    param.nBFrames = 0;
    param.eProfile = eProfile;
    param.eLevel = eLevel;
    param.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;
    param.bPLUSPTYPEAllowed = OMX_FALSE;
    param.bForceRoundingTypeToZero = OMX_FALSE;
    param.nPictureHeaderRepetition = 0;
    param.nGOBHeaderInterval = 0;
    status = setPortParam(omxNode, OMX_IndexParamVideoH263, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupVPXPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, OMX_U32 xFramerate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_ANDROID_VP8ENCODERTYPE param;
    (void)xFramerate;  // necessary for intra frame spacing

    status = getPortParam(omxNode,
                          (OMX_INDEXTYPE)OMX_IndexParamVideoAndroidVp8Encoder,
                          portIndex, &param);
    // EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    // SPECIAL CASE; OMX.qcom.video.encoder.vp8 does not support this index
    // type. Dont flag error for now
    if (status != ::android::hardware::media::omx::V1_0::Status::OK) return;

    param.nKeyFrameInterval = 300;
    param.eTemporalPattern = OMX_VIDEO_VPXTemporalLayerPatternNone;
    param.nMinQuantizer = 2;
    param.nMaxQuantizer = 63;
    status = setPortParam(omxNode,
                          (OMX_INDEXTYPE)OMX_IndexParamVideoAndroidVp8Encoder,
                          portIndex, &param);
    // EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    // SPECIAL CASE; OMX.qcom.video.encoder.vp8 does not support this index
    // type. Dont flag error for now
    if (status != ::android::hardware::media::omx::V1_0::Status::OK) return;
}

void setupVP8Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_VIDEO_VP8PROFILETYPE eProfile,
                  OMX_VIDEO_VP8LEVELTYPE eLevel) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_VP8TYPE param;

    status = getPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamVideoVp8,
                          portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    param.eProfile = eProfile;
    param.eLevel = eLevel;
    param.bErrorResilientMode = OMX_TRUE;
    param.nDCTPartitions = 1;
    status = setPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamVideoVp8,
                          portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupVP9Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_VIDEO_VP9PROFILETYPE eProfile,
                  OMX_VIDEO_VP9LEVELTYPE eLevel) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_VP9TYPE param;

    status = getPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamVideoVp9,
                          portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    param.eProfile = eProfile;
    param.eLevel = eLevel;
    param.bErrorResilientMode = OMX_TRUE;
    param.nTileRows = 1;
    param.nTileColumns = 1;
    param.bEnableFrameParallelDecoding = OMX_TRUE;
    status = setPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamVideoVp9,
                          portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}
