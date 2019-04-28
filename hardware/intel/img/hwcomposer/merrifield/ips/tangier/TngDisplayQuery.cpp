/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/

#include <HwcTrace.h>
#include <DisplayPlane.h>
#include <hal_public.h>
#include <OMX_IVCommon.h>
#include <OMX_IntelVideoExt.h>
#include <DisplayQuery.h>


namespace android {
namespace intel {

bool DisplayQuery::isVideoFormat(uint32_t format)
{
    switch (format) {
    case OMX_INTEL_COLOR_FormatYUV420PackedSemiPlanar:
    case OMX_INTEL_COLOR_FormatYUV420PackedSemiPlanar_Tiled:
    // Expand format to support the case: Software decoder + HW rendering
    // Only VP9 use this foramt now
    case HAL_PIXEL_FORMAT_YV12:
        return true;
    default:
        return false;
    }
}

int DisplayQuery::getOverlayLumaStrideAlignment(uint32_t format)
{
    // both luma and chroma stride need to be 64-byte aligned for overlay
    switch (format) {
    case HAL_PIXEL_FORMAT_YV12:
    case HAL_PIXEL_FORMAT_I420:
        // for these two formats, chroma stride is calculated as half of luma stride
        // so luma stride needs to be 128-byte aligned.
        return 128;
    default:
        return 64;
    }
}

uint32_t DisplayQuery::queryNV12Format()
{
    return HAL_PIXEL_FORMAT_NV12;
}

} // namespace intel
} // namespace android

