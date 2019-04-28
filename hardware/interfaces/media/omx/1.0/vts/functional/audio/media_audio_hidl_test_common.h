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

#ifndef MEDIA_AUDIO_HIDL_TEST_COMMON_H
#define MEDIA_AUDIO_HIDL_TEST_COMMON_H

#include <media_hidl_test_common.h>

/*
 * Common audio utils
 */
void enumerateProfile(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                      std::vector<int32_t>* arrProfile);

void setupPCMPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                  OMX_NUMERICALDATATYPE eNumData, int32_t nBitPerSample,
                  int32_t nSamplingRate, OMX_AUDIO_PCMMODETYPE ePCMMode);

void setupMP3Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_AUDIO_MP3STREAMFORMATTYPE eFormat, int32_t nChannels,
                  int32_t nBitRate, int32_t nSampleRate);

void setupFLACPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                   int32_t nSampleRate, int32_t nCompressionLevel);

void setupOPUSPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                   int32_t nBitRate, int32_t nSampleRate);

void setupAMRPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nBitRate,
                  bool isAMRWB);

void setupVORBISPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, int32_t nChannels,
                     int32_t nBitRate, int32_t nSampleRate, int32_t nQuality);

void setupAACPort(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_AUDIO_AACPROFILETYPE eAACProfile,
                  OMX_AUDIO_AACSTREAMFORMATTYPE eAACStreamFormat,
                  int32_t nChannels, int32_t nBitRate, int32_t nSampleRate);

#endif  // MEDIA_AUDIO_HIDL_TEST_COMMON_H
