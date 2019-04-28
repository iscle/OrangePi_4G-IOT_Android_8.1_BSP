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

#ifndef _IPP_STATUS_H_
#define _IPP_STATUS_H_

#include "http.h"
#include "ipp.h"
#include "ifc_printer_capabilities.h"

#ifdef __cplusplus
extern "C" {
#endif // __cplusplus

#define IPP_PRINT_ORIENTATION_PORTRAIT    3
#define IPP_PRINT_ORIENTATION_LANDSCAPE    4

/*
 * IPP sides
 */
#define IPP_SIDES_TAG                  "sides"
#define IPP_SIDES_ONE_SIDED            "one-sided"
#define IPP_SIDES_TWO_SIDED_LONG_EDGE  "two-sided-long-edge"
#define IPP_SIDES_TWO_SIDED_SHORT_EDGE "two-sided-short-edge"

/*
 * IPP output mode
 */
#define IPP_OUTPUT_MODE_TAG          "print-color-mode"
#define IPP_OUTPUT_MODE_COLOR        "color"
#define IPP_OUTPUT_MODE_MONO         "monochrome"

/*
 * IPP Printer State Reasons
 */
#define IPP_NONE_STR                        "none"

/*
 * According to RFC2911, any of these can have -error, -warning, or -report appended to end
 */
#define IPP_PRNT_STATE_OTHER_ERR            "other"
#define IPP_PRNT_STATE_OTHER_WARN           "other-warning"
#define IPP_PRNT_STATE_NONE                 IPP_NONE_STR
#define IPP_PRNT_STATE_MEDIA_JAM            "media-jam"
#define IPP_PRNT_PAUSED                     "paused"
#define IPP_PRNT_SHUTDOWN                   "shutdown"
#define IPP_PRNT_STATE_TONER_LOW            "toner-low"
#define IPP_PRNT_STATE_TONER_EMPTY          "toner-empty"
#define IPP_PRNT_STATE_SPOOL_FULL           "spool-area-full"
#define IPP_PRNT_STATE_DOOR_OPEN            "door-open"
#define IPP_PRNT_STATE_MEDIA_EMPTY          "media-empty"
#define IPP_PRNT_STATE_MEDIA_NEEDED         "media-needed"
#define IPP_PRNT_STATE_MARKER_SUPPLY_LOW    "marker-supply-low"
#define IPP_PRNT_STATE_MARKER_SUPPLY_EMPTY  "marker-supply-empty"
#define IPP_PRNT_STATE_COVER_OPEN           "cover-open"

#ifdef __cplusplus
}
#endif // __cplusplus

#endif // !_IPP_STATUS_H_