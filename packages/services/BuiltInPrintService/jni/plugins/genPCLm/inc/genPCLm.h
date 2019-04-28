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

#ifndef _GEN_PCLM_H
#define _GEN_PCLM_H

#include <jpeglib.h>

/*
 * Encode JPEG data from imageBuffer into to an output buffer
 */
extern void write_JPEG_Buff(ubyte *outBuff, int quality, int image_width, int image_height,
        JSAMPLE *imageBuffer, int resolution, colorSpaceDisposition, int *numCompBytes);

#endif // _GEN_PCLM_H