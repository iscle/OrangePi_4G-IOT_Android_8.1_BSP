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

#ifndef PCLM_COMMON_DEFINES
#define PCLM_COMMON_DEFINES

#include <stdbool.h>
#include "wtypes.h"

#define PCLM_Ver 0.98

typedef enum {
    RGB,
    AdobeRGB,
    GRAY,
    unknown
} colorSpaceEnum;

typedef enum {
    jpeg,
    zlib,
    rle
} compTypeEnum;

typedef enum {
    simplex,
    duplex_longEdge,
    duplex_shortEdge
} duplexDispositionEnum;

typedef enum {
    job_open,
    job_closed,
    job_errored
} jobStateEnum;

typedef enum {
    deviceRGB,
    adobeRGB,
    grayScale
} colorSpaceDisposition;

typedef enum {
    debugOn,
    debugOff
} debugDisposition;

typedef enum {
    compressRLE,
    compressDCT,
    compressFlate,
    compressDefault,
    compressNone
} compressionDisposition;

typedef enum {
    portraitOrientation,
    landscapeOrientation
} mediaOrientationDisposition;

typedef enum {
    res300,
    res600,
    res1200
} renderResolution;

typedef enum {
    top_left,
    bottom_right
} pageOriginType;

typedef enum {
    color_content,
    gray_content,
    unknown_content
} pageCromaticContent;

typedef enum {
    draft,
    normal,
    best,
} pageOutputQuality;

typedef enum {
    alternate,
    alternate_roll,
    auto_select,
    bottom,
    center,
    disc,
    envelope,
    hagaki,
    large_capacity,
    left,
    main_tray,
    main_roll,
    manual,
    middle,
    photo,
    rear,
    right,
    side,
    top,
    tray_1,
    tray_2,
    tray_3,
    tray_4,
    tray_5,
    tray_N,
} jobInputBin;

typedef enum {
    top_output,
    middle_output,
    bottom_output,
    side_output,
    center_output,
    rear_output,
    face_up,
    face_down,
    large_capacity_output,
    stacker_N,
    mailbox_N,
    tray_1_output,
    tray_2_output,
    tray_3_output,
    tray_4_output,
} jobOutputBin;

typedef struct {
    pageCromaticContent userCromaticMode;
    pageOutputQuality userPageQuality;
    mediaOrientationDisposition userOrientation;
    char userMediaType[256];
    jobInputBin userInputBin;
    int userCopies;
    char userDocumentName[256];
    jobOutputBin userOutputBin;
} PCLmSUserSettingsType;

typedef struct {
    char mediaSizeName[256];
    char clientLocale[256];
    float mediaHeight;
    float mediaWidth;
    float sourceHeight;
    float sourceWidth;
    float mediaWidthOffset;
    float mediaHeightOffset;
    pageCromaticContent colorContent; // Did the page contain any "real" color
    pageOriginType pageOrigin;
    compressionDisposition compTypeRequested;
    colorSpaceDisposition srcColorSpaceSpefication;
    colorSpaceDisposition dstColorSpaceSpefication;
    int stripHeight;
    renderResolution destinationResolution;

    duplexDispositionEnum duplexDisposition;
    int scaleFactor;
    bool genExtraPage;
    bool mirrorBackside;
    int mediaWidthInPixels;
    int mediaHeightInPixels;
    int SourceWidthPixels;
    int SourceHeightPixels;
} PCLmPageSetup;

typedef enum {
    success = 0,
    genericFailure = -1,
} PCLmGenerator_returnType;

#endif