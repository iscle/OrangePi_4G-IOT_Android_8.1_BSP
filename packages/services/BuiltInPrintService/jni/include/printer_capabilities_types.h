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
#ifndef __PRINTER_CAPABILITIES_TYPES_H__
#define __PRINTER_CAPABILITIES_TYPES_H__

#define MAX_SIZES_SUPPORTED 50
#define MAX_MEDIA_TRAYS_SUPPORTED 10
#define MAX_MEDIA_TYPES_SUPPORTED 20
#define MAX_RESOLUTIONS_SUPPORTED 10
#define MAX_URI_LENGTH 1024
#define MAX_STRING 256
#define MAX_UUID 46

#include "wprint_df_types.h"

/*
 * Printer Capabilities structure.
 */
typedef struct {
    unsigned char duplex;
    unsigned char borderless;
    unsigned char canPrintPDF;
    unsigned char canPrintPCLm;
    unsigned char canPrintPWG;
    char make[MAX_STRING];
    char name[MAX_STRING];
    char uuid[MAX_UUID];
    char location[MAX_STRING];
    unsigned char canRotateDuplexBackPage;
    unsigned char color;
    unsigned char faceDownTray;
    media_size_t supportedMediaSizes[MAX_SIZES_SUPPORTED];
    unsigned int numSupportedMediaSizes;

    // IPP major version (0 = not supported)
    int ippVersionMajor;

    int ippVersionMinor;

    // ePCL over IPP supported version
    int ePclIppVersion;

    int stripHeight;
    unsigned long long supportedInputMimeTypes;
    media_tray_t supportedMediaTrays[MAX_MEDIA_TRAYS_SUPPORTED];
    unsigned int numSupportedMediaTrays;
    media_type_t supportedMediaTypes[MAX_MEDIA_TYPES_SUPPORTED];
    unsigned int numSupportedMediaTypes;
    unsigned char isSupported;
    unsigned char canCopy;
    unsigned char isMediaSizeNameSupported;
    unsigned int printerTopMargin;
    unsigned int printerBottomMargin;
    unsigned int printerLeftMargin;
    unsigned int printerRightMargin;
    unsigned char inkjet;
    int supportedResolutions[MAX_RESOLUTIONS_SUPPORTED];
    unsigned int numSupportedResolutions;
    char printerUri[MAX_URI_LENGTH + 1];
    char httpResource[MAX_URI_LENGTH + 1];
    char mediaDefault[MAX_STRING];
    unsigned char docSourceAppName;
    unsigned char docSourceAppVersion;
    unsigned char docSourceOsName;
    unsigned char docSourceOsVersion;
} printer_capabilities_t;

#endif // __PRINTER_CAPABILITIES_TYPES_H__