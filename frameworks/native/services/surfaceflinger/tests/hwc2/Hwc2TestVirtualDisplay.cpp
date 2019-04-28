/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <sstream>

#include "Hwc2TestVirtualDisplay.h"

Hwc2TestVirtualDisplay::Hwc2TestVirtualDisplay(
        Hwc2TestCoverage coverage)
    : mDisplayDimension(coverage)
{
    mDisplayDimension.setDependent(&mBuffer);
}

std::string Hwc2TestVirtualDisplay::dump() const
{
    std::stringstream dmp;

    dmp << "virtual display: \n";

    mDisplayDimension.dump();

    return dmp.str();
}

int Hwc2TestVirtualDisplay::getBuffer(buffer_handle_t* outHandle,
        android::base::unique_fd* outAcquireFence)
{
    int32_t acquireFence;
    int ret = mBuffer.get(outHandle, &acquireFence);
    outAcquireFence->reset(acquireFence);
    return ret;
}

void Hwc2TestVirtualDisplay::reset()
{
    return mDisplayDimension.reset();
}

bool Hwc2TestVirtualDisplay::advance()
{
    return mDisplayDimension.advance();
}

UnsignedArea Hwc2TestVirtualDisplay::getDisplayDimension() const
{
    return mDisplayDimension.get();
}
