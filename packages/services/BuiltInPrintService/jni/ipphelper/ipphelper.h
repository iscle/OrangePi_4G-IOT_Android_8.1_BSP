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

#ifndef _IPP_HELPER_H_
#define _IPP_HELPER_H_

#include "lib_wprint.h"
#include "ifc_printer_capabilities.h"
#include "ippstatus_capabilities.h"
#include "ippstatus.h"
#include "ifc_status_monitor.h"
#include "http.h"
#include "ipp.h"
#include "ifc_wprint.h"

/* Default timeout for most operations */
#define DEFAULT_IPP_TIMEOUT (15 * 1000)

#ifdef __cplusplus
extern "C" {
#endif // __cplusplus

/*
 * Strcture of supported IPP versions
 */
typedef struct ipp_version_supported_s {
    unsigned char supportsIpp10;
    unsigned char supportsIpp11;
    unsigned char supportsIpp20;
} ipp_version_supported_t;

/*
 * Enumeration of IPP version states
 */
typedef enum {
    NEW_REQUEST_SEQUENCE,
    IPP_VERSION_RESOLVED,
    IPP_VERSION_UNSUPPORTED,
} ipp_version_state;

#define IPP_SERVICE_ERROR_MAX_RETRIES 3
#define IPP_BAD_REQUEST_MAX_RETRIES 2
#define IPP_INTERNAL_ERROR_MAX_RETRIES 1

extern const ifc_wprint_t *ipp_wprint_ifc;

#define PAGE_STATUS_MAX 200

/*
 * Structure for supported media sizes
 */
typedef struct media_supported_s {
    // All supported media sizes
    media_size_t media_size[PAGE_STATUS_MAX];

    // Index to entry in keyword trans table.
    int idxKeywordTranTable[PAGE_STATUS_MAX];
} media_supported_t;

/*
 * Returns the status of a given printer
 */
extern ipp_status_t get_PrinterState(http_t *http, char *printer_uri,
        printer_state_dyn_t *printer_state_dyn, ipp_pstate_t *printer_state);

/*
 * Outputs printer state reasons int printer_state
 */
extern void get_PrinterStateReason(ipp_t *response, ipp_pstate_t *printer_state,
        printer_state_dyn_t *printer_state_dyn);

/*
 * Parses printer attributes from the IPP response and copies them to capabilities
 */
extern void parse_printerAttributes(ipp_t *response, printer_capabilities_t *capabilities);

/*
 * Sets IPP version
 */
extern status_t set_ipp_version(ipp_t *, char *, http_t *, ipp_version_state);

/*
 * Parses supported media from the IPP response and copies the list into capabilities
 */
extern void parse_getMediaSupported(ipp_t *response, media_supported_t *media_supported,
        printer_capabilities_t *capabilities);

/*
 * Logs printer capabilities
 */
extern void debuglist_printerCapabilities(printer_capabilities_t *capabilities);

/*
 * Logs printer status
 */
extern void debuglist_printerStatus(printer_state_dyn_t *printer_state_dyn);

/*
 * Logs an IPP attribute
 */
extern void print_attr(ipp_attribute_t *attr);

/*
 * Returns index of the supported media size, else returns -1
 */
extern int ipp_find_media_size(const char *ipp_media_keyword, media_size_t *media_size);

/*
 * Returns the PWG name of a media size given it's enumeration
 */
extern const char *mapDFMediaToIPPKeyword(media_size_t media_size);

/*
 * Gets the requested resource from a printer
 */
extern void getResourceFromURI(const char *uri, char *resource, int resourcelen);

/*
 * Set up a new CUPS connection. All parameters for connection should be in 'info' structure.
 * The printer_uri is copied into the 'printer_uri' parameter.
 *
 * Returns (non-NULL) http session on success.
 */
http_t *ipp_cups_connect(const wprint_connect_info_t *info, char *printer_uri,
        unsigned int uriLength);

/*
 * Executes a CUPS request with the given ipp request structure
 */
ipp_t *ipp_doCupsRequest(http_t *http, ipp_t *request, char *http_resource, char *printer_uri);

#define IPP_PREFIX "ipp"
#define IPPS_PREFIX "ipps"
#define DEFAULT_IPP_URI_RESOURCE "/ipp/print"

#ifdef __cplusplus
}
#endif // __cplusplus

#endif // !_IPP_HELPER_H_
