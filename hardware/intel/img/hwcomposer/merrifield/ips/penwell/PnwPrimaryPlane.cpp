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
#include <Drm.h>
#include <penwell/PnwPrimaryPlane.h>
#include <penwell/PnwGrallocBuffer.h>
#include <common/PixelFormat.h>

namespace android {
namespace intel {

PnwPrimaryPlane::PnwPrimaryPlane(int index, int disp)
    : PnwSpritePlane(index, disp)
{
    CTRACE();
    mType = PLANE_PRIMARY;
}

PnwPrimaryPlane::~PnwPrimaryPlane()
{
    CTRACE();
}

void PnwPrimaryPlane::setFramebufferTarget(DataBuffer& buf)
{
    CTRACE();
    //TODO: implement penwell frame buffer target flip
}

bool PnwPrimaryPlane::setDataBuffer(uint32_t handle)
{
    PnwGrallocBuffer tmpBuf(handle);
    uint32_t usage;

    ATRACE("handle = %#x", handle);

    usage = tmpBuf.getUsage();
    if (!handle || (GRALLOC_USAGE_HW_FB & usage)) {
        setFramebufferTarget(tmpBuf);
        return true;
    }

    return DisplayPlane::setDataBuffer(handle);
}

bool PnwPrimaryPlane::assignToDevice(int disp)
{
    return true;
}

} // namespace intel
} // namespace android
