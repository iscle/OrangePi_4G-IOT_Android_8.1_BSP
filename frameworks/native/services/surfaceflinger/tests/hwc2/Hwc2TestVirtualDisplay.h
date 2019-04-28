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

#ifndef _HWC2_TEST_VIRTUAL_DISPLAY_H
#define _HWC2_TEST_VIRTUAL_DISPLAY_H

#include "Hwc2TestBuffer.h"
#include "Hwc2TestProperties.h"

#define HWC2_INCLUDE_STRINGIFICATION
#define HWC2_USE_CPP11
#include <hardware/hwcomposer2.h>
#undef HWC2_INCLUDE_STRINGIFICATION
#undef HWC2_USE_CPP11

class Hwc2TestVirtualDisplay {
public:
    Hwc2TestVirtualDisplay(Hwc2TestCoverage coverage);

    std::string dump() const;

    int getBuffer(buffer_handle_t* outHandle,
            android::base::unique_fd* outAcquireFence);

    void reset();
    bool advance();

    UnsignedArea getDisplayDimension() const;

private:
    Hwc2TestBuffer mBuffer;

    Hwc2TestDisplayDimension mDisplayDimension;
};

#endif /* ifndef _HWC2_TEST_VIRTUAL_DISPLAY_H */
