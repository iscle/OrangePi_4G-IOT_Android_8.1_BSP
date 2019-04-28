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
#ifndef __WPRINT_DF_TYPES_H__
#define __WPRINT_DF_TYPES_H__

#include "media_sizes.h"
#include "media_tray_types.h"

/*
 * Enumeration for supported color spaces
 */
typedef enum {
    COLOR_SPACE_MONO, // MONO job
    COLOR_SPACE_COLOR, // sRGB COLOR job
    COLOR_SPACE_ADOBE_RGB // supported in pclm only
} color_space_t;

/*
 * Enumeration for supported duplex modes
 */
typedef enum {
    DUPLEX_MODE_NONE, // no duplex
    DUPLEX_MODE_BOOK, // long edge binding
    DUPLEX_MODE_TABLET, // short edge binding
} duplex_t;

/*
 * Enumeration for supported media types
 */
typedef enum {
    MEDIA_PLAIN,
    MEDIA_SPECIAL,
    MEDIA_PHOTO,
    MEDIA_TRANSPARENCY,
    MEDIA_IRON_ON,
    MEDIA_IRON_ON_MIRROR,
    MEDIA_ADVANCED_PHOTO,
    MEDIA_FAST_TRANSPARENCY,
    MEDIA_BROCHURE_GLOSSY,
    MEDIA_BROCHURE_MATTE,
    MEDIA_PHOTO_GLOSSY,
    MEDIA_PHOTO_MATTE,
    MEDIA_PREMIUM_PHOTO,
    MEDIA_OTHER_PHOTO,
    MEDIA_PRINTABLE_CD,
    MEDIA_PREMIUM_PRESENTATION,

    MEDIA_UNKNOWN = 99 // New types above this line
} media_type_t;

#endif // __WPRINT_DF_TYPES_H__