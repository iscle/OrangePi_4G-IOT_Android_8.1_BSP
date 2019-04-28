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
#include <penwell/PnwGrallocBuffer.h>

namespace android {
namespace intel {

PnwGrallocBuffer::PnwGrallocBuffer(uint32_t handle)
    :GrallocBufferBase(handle)
{
    struct PnwIMGGrallocBuffer *grallocHandle =
        (struct PnwIMGGrallocBuffer*)handle;

    CTRACE();

    if (!grallocHandle) {
        ETRACE("gralloc handle is null");
        return;
    }

    mFormat = grallocHandle->format;
    mWidth = grallocHandle->width;
    mHeight = grallocHandle->height;
    mUsage = grallocHandle->usage;
    mKey = grallocHandle->stamp;
    mBpp = grallocHandle->bpp;

    initialize();
}

}
}
