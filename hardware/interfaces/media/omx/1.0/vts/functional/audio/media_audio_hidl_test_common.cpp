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

#define LOG_TAG "media_omx_hidl_audio_test_common"
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
#include <media_audio_hidl_test_common.h>
#include <media_hidl_test_common.h>
#include <memory>

void enumerateProfile(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                      std::vector<int32_t>* arrProfile) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_AUDIO_PARAM_ANDROID_PROFILETYPE param;
    param.nProfileIndex = 0;
    arrProfile->clear();
    while (1) {
        status = getPortParam(
            omxNode, (OMX_INDEXTYPE)OMX_IndexParamAudioProfileQuerySupported,
            portIndex, &param);
        if (status != ::android::hardware::media::omx::V1_0::Status::OK) break;
        arrProfile->push_back(static_cast<int32_t>(param.eProfile));
        param.nProfileIndex++;
        if (param.nProfileIndex == 512) {
            // enumerated way too many, highly unusual for this to happen.
            EXPECT_LE(param.nProfileIndex, 512U)
                << "Expecting OMX_ErrorNoMore but not received";
            break;
        }
    }
}

void setupPCMPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                  OMX_NUMERICALDATATYPE eNumData, int32_t nBitPerSample,
                  int32_t nSamplingRate, OMX_AUDIO_PCMMODETYPE ePCMMode) {
    OMX_AUDIO_PARAM_PCMMODETYPE param;
    android::hardware::media::omx::V1_0::Status status;
    status = getPortParam(omxNode, OMX_IndexParamAudioPcm, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nChannels = nChannels;
    param.eNumData = eNumData;
    param.eEndian = OMX_EndianLittle;
    param.bInterleaved = OMX_TRUE;
    param.nBitPerSample = nBitPerSample;
    param.nSamplingRate = nSamplingRate;
    param.ePCMMode = ePCMMode;
    switch (nChannels) {
        case 1:
            param.eChannelMapping[0] = OMX_AUDIO_ChannelCF;
            break;
        case 2:
            param.eChannelMapping[0] = OMX_AUDIO_ChannelLF;
            param.eChannelMapping[1] = OMX_AUDIO_ChannelRF;
            break;
        default:
            EXPECT_TRUE(false);
    }
    status = setPortParam(omxNode, OMX_IndexParamAudioPcm, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupMP3Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_AUDIO_MP3STREAMFORMATTYPE eFormat, int32_t nChannels,
                  int32_t nBitRate, int32_t nSampleRate) {
    OMX_AUDIO_PARAM_MP3TYPE param;
    android::hardware::media::omx::V1_0::Status status;
    status = getPortParam(omxNode, OMX_IndexParamAudioMp3, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nChannels = nChannels;
    param.nBitRate = nBitRate;
    param.nSampleRate = nSampleRate;
    param.nAudioBandWidth = 0;
    param.eChannelMode = (nChannels == 1) ? OMX_AUDIO_ChannelModeMono
                                          : OMX_AUDIO_ChannelModeStereo;
    param.eFormat = eFormat;
    status = setPortParam(omxNode, OMX_IndexParamAudioMp3, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupFLACPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                   int32_t nSampleRate, int32_t nCompressionLevel) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_AUDIO_PARAM_FLACTYPE param;
    status = getPortParam(omxNode, OMX_IndexParamAudioFlac, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nChannels = nChannels;
    param.nSampleRate = nSampleRate;
    param.nCompressionLevel = nCompressionLevel;
    status = setPortParam(omxNode, OMX_IndexParamAudioFlac, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupOPUSPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                   int32_t nBitRate, int32_t nSampleRate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_AUDIO_PARAM_ANDROID_OPUSTYPE param;
    status =
        getPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamAudioAndroidOpus,
                     portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nChannels = nChannels;
    param.nBitRate = nBitRate;
    param.nSampleRate = nSampleRate;
    status =
        setPortParam(omxNode, (OMX_INDEXTYPE)OMX_IndexParamAudioAndroidOpus,
                     portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

OMX_AUDIO_AMRBANDMODETYPE pickModeFromBitRate(bool isAMRWB, int32_t bps) {
    if (isAMRWB) {
        if (bps <= 6600) return OMX_AUDIO_AMRBandModeWB0;
        if (bps <= 8850) return OMX_AUDIO_AMRBandModeWB1;
        if (bps <= 12650) return OMX_AUDIO_AMRBandModeWB2;
        if (bps <= 14250) return OMX_AUDIO_AMRBandModeWB3;
        if (bps <= 15850) return OMX_AUDIO_AMRBandModeWB4;
        if (bps <= 18250) return OMX_AUDIO_AMRBandModeWB5;
        if (bps <= 19850) return OMX_AUDIO_AMRBandModeWB6;
        if (bps <= 23050) return OMX_AUDIO_AMRBandModeWB7;
        return OMX_AUDIO_AMRBandModeWB8;
    } else {
        if (bps <= 4750) return OMX_AUDIO_AMRBandModeNB0;
        if (bps <= 5150) return OMX_AUDIO_AMRBandModeNB1;
        if (bps <= 5900) return OMX_AUDIO_AMRBandModeNB2;
        if (bps <= 6700) return OMX_AUDIO_AMRBandModeNB3;
        if (bps <= 7400) return OMX_AUDIO_AMRBandModeNB4;
        if (bps <= 7950) return OMX_AUDIO_AMRBandModeNB5;
        if (bps <= 10200) return OMX_AUDIO_AMRBandModeNB6;
        return OMX_AUDIO_AMRBandModeNB7;
    }
}

void setupAMRPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nBitRate,
                  bool isAMRWB) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_AUDIO_PARAM_AMRTYPE param;
    status = getPortParam(omxNode, OMX_IndexParamAudioAmr, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nChannels = 1;
    param.nBitRate = nBitRate;
    param.eAMRBandMode = pickModeFromBitRate(isAMRWB, nBitRate);
    status = setPortParam(omxNode, OMX_IndexParamAudioAmr, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupVORBISPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                     int32_t nBitRate, int32_t nSampleRate, int32_t nQuality) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_AUDIO_PARAM_VORBISTYPE param;
    status =
        getPortParam(omxNode, OMX_IndexParamAudioVorbis, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nChannels = nChannels;
    param.nBitRate = nBitRate;
    param.nSampleRate = nSampleRate;
    param.nQuality = nQuality;
    status =
        setPortParam(omxNode, OMX_IndexParamAudioVorbis, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

void setupAACPort(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_AUDIO_AACPROFILETYPE eAACProfile,
                  OMX_AUDIO_AACSTREAMFORMATTYPE eAACStreamFormat,
                  int32_t nChannels, int32_t nBitRate, int32_t nSampleRate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_AUDIO_PARAM_AACPROFILETYPE param;
    status = getPortParam(omxNode, OMX_IndexParamAudioAac, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    param.nChannels = nChannels;
    param.nSampleRate = nSampleRate;
    param.nBitRate = nBitRate;
    param.eAACProfile = eAACProfile;
    param.eAACStreamFormat = eAACStreamFormat;
    param.eChannelMode = (nChannels == 1) ? OMX_AUDIO_ChannelModeMono
                                          : OMX_AUDIO_ChannelModeStereo;
    status = setPortParam(omxNode, OMX_IndexParamAudioAac, portIndex, &param);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}
