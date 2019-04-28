/*
 * Copyright 2016, The Android Open Source Project
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

#ifndef MEDIA_VIDEO_HIDL_TEST_COMMON_H
#define MEDIA_VIDEO_HIDL_TEST_COMMON_H

/*
 * Common video utils
 */
void enumerateProfileAndLevel(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                              std::vector<int32_t>* arrProfile,
                              std::vector<int32_t>* arrLevel);

void setupRAWPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, OMX_U32 nFrameWidth,
                  OMX_U32 nFrameHeight, OMX_U32 nBitrate, OMX_U32 xFramerate,
                  OMX_COLOR_FORMATTYPE eColorFormat);

void setupAVCPort(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_VIDEO_AVCPROFILETYPE eProfile,
                  OMX_VIDEO_AVCLEVELTYPE eLevel, OMX_U32 xFramerate);

void setupHEVCPort(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                   OMX_VIDEO_HEVCPROFILETYPE eProfile,
                   OMX_VIDEO_HEVCLEVELTYPE eLevel);

void setupMPEG4Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                    OMX_VIDEO_MPEG4PROFILETYPE eProfile,
                    OMX_VIDEO_MPEG4LEVELTYPE eLevel, OMX_U32 xFramerate);

void setupH263Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                   OMX_VIDEO_H263PROFILETYPE eProfile,
                   OMX_VIDEO_H263LEVELTYPE eLevel, OMX_U32 xFramerate);

void setupVPXPort(sp<IOmxNode> omxNode, OMX_U32 portIndex, OMX_U32 xFramerate);

void setupVP8Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_VIDEO_VP8PROFILETYPE eProfile,
                  OMX_VIDEO_VP8LEVELTYPE eLevel);

void setupVP9Port(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                  OMX_VIDEO_VP9PROFILETYPE eProfile,
                  OMX_VIDEO_VP9LEVELTYPE eLevel);

#endif  // MEDIA_VIDEO_HIDL_TEST_COMMON_H
