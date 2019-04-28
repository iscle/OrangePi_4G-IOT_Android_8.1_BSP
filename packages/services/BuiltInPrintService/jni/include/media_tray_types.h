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
#ifndef __MEDIA_TRAY_TYPES_H__
#define __MEDIA_TRAY_TYPES_H__

/*
 * Media source/tray enumeration of types the print system supports
 */
typedef enum {
    TRAY_UNSPECIFIED = 0,
    TRAY_SOURCE_TRAY_1 = 1,
    TRAY_SOURCE_PHOTO_TRAY = 6,
    TRAY_SRC_AUTO_SELECT = 7,
} media_tray_t;

#endif // __MEDIA_TRAY_TYPES_H__