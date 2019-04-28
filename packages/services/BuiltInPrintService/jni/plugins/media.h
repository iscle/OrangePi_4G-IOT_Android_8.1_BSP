/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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

#ifndef __MEDIA_H__
#define __MEDIA_H__

#include "lib_wprint.h"

#define STANDARD_SCALE_FOR_PDF    72.0
#define _MI_TO_PIXELS(n, res)     ((n)*(res)+500)/1000.0
#define _MI_TO_POINTS(n)          _MI_TO_PIXELS(n, STANDARD_SCALE_FOR_PDF)
#define _MI_TO_100MM(n)           (n/1000) * 2540 // Convert 1k inch to 100 mm
#define UNKNOWN_VALUE             -1

struct MediaSizeTableElement {
    media_size_t media_size;
    const char *PCL6Name;
    const float WidthInInches; // Width in thousands of an inch
    const float HeightInInches; // Height in thousands of an inch
    const float WidthInMm; // Width in mm for metric based media sizes or UNKNOWN_VALUE otherwise
    const float HeightInMm; // Height in mm for metric based media sizes or UNKNOWN_VALUE otherwise
    const char *PWGName;
};

#define SUPPORTED_MEDIA_SIZE_COUNT 15
extern struct MediaSizeTableElement SupportedMediaSizes[SUPPORTED_MEDIA_SIZE_COUNT];

#endif // __MEDIA_H__