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
#include <Hwcomposer.h>
#include <BufferManager.h>
#include <penwell/PnwSpritePlane.h>
#include <common/PixelFormat.h>

namespace android {
namespace intel {

PnwSpritePlane::PnwSpritePlane(int index, int disp)
    : SpritePlaneBase(index, disp)
{
    CTRACE();
}

PnwSpritePlane::~PnwSpritePlane()
{
    CTRACE();
}

bool PnwSpritePlane::setDataBuffer(BufferMapper& mapper)
{
    // TODO: implement setDataBuffer
    return false;
}

void* PnwSpritePlane::getContext() const
{
    CTRACE();
    // TODO: return penwell sprite context
    return 0;
}

} // namespace intel
} // namespace android
