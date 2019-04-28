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
#ifndef __MIME_TYPES_H__
#define __MIME_TYPES_H__

/*
 * input MIME types
 */
#define MIME_TYPE_PDF     "application/pdf"
#define MIME_TYPE_PCLM    "application/PCLm"
#define MIME_TYPE_PWG     "image/pwg-raster"
typedef enum {
    INPUT_MIME_TYPE_PDF,
    INPUT_MIME_TYPE_PCLM,
    INPUT_MIME_TYPE_PWG,
} input_mime_types_t;

/*
 * output print data formats
 */
#define PRINT_FORMAT_PCLM  MIME_TYPE_PCLM
#define PRINT_FORMAT_PDF   MIME_TYPE_PDF
#define PRINT_FORMAT_PWG   MIME_TYPE_PWG
#define PRINT_FORMAT_AUTO  "application/octet-stream"

#endif // __MIME_TYPES_H__