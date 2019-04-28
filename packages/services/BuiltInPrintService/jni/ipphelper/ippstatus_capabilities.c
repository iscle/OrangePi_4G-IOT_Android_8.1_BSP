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

#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include "lib_wprint.h"
#include "ippstatus_capabilities.h"   // move these to above the calls to cups files
#include "ipphelper.h"
#include "cups.h"
#include "http-private.h"
#include "wprint_debug.h"

#define TAG "ippstatus_capabilities"

/*
 * Requested printer attributes
 */
static const char *pattrs[] = {
        "ipp-versions-supported",
        "printer-make-and-model",
        "printer-info",
        "printer-dns-sd-name",
        "printer-name",
        "printer-location",
        "printer-uuid",
        "printer-uri-supported",
        "uri-security-supported",
        "uri-authentication-supported",
        "color-supported",
        "copies-supported",
        "document-format-supported",
        "media-col-default",
        "media-default",
        "media-left-margin-supported",
        "media-right-margin-supported",
        "media-top-margin-supported",
        "media-bottom-margin-supported",
        "media-supported",
        "media-type-supported",
        "output-bin-supported",
        "print-color-mode-supported",
        "printer-resolution-supported",
        "sides-supported",
        "printer-device-id",
        "epcl-version-supported",
        "pclm-raster-back-side",
        "pclm-strip-height-preferred",
        "pclm-compression-method-preferred",
        "pclm-source-resolution-supported",
        "document-format-details-supported"
};

static void _init(const ifc_printer_capabilities_t *this_p,
        const wprint_connect_info_t *info);

static status_t _get_capabilities(const ifc_printer_capabilities_t *this_p,
        printer_capabilities_t *capabilities);

static void _destroy(const ifc_printer_capabilities_t *this_p);

static ifc_printer_capabilities_t _capabilities_ifc = {
        .init = _init, .get_capabilities = _get_capabilities, .get_margins = NULL,
        .destroy = _destroy,
};

typedef struct {
    http_t *http;
    printer_capabilities_t printer_caps;
    ifc_printer_capabilities_t ifc;
} ipp_capabilities_t;

const ifc_printer_capabilities_t *ipp_status_get_capabilities_ifc(const ifc_wprint_t *wprint_ifc) {
    LOGD("ipp_status_get_capabilities_ifc: Enter");
    ipp_capabilities_t *caps = (ipp_capabilities_t *) malloc(sizeof(ipp_capabilities_t));
    if (caps == NULL) {
        return NULL;
    }

    memset(caps, 0, sizeof(ipp_capabilities_t));
    caps->http = NULL;

    memcpy(&caps->ifc, &_capabilities_ifc, sizeof(ifc_printer_capabilities_t));
    return &caps->ifc;
}

static void _init(const ifc_printer_capabilities_t *this_p,
        const wprint_connect_info_t *connect_info) {
    LOGD("_init: Enter");
    ipp_capabilities_t *caps;
    do {
        if (this_p == NULL) {
            continue;
        }
        caps = IMPL(ipp_capabilities_t, ifc, this_p);

        if (caps->http != NULL) {
            LOGD("_init(): http != NULL closing HTTP");
            httpClose(caps->http);
        }

        caps->http = ipp_cups_connect(connect_info, caps->printer_caps.printerUri,
                sizeof(caps->printer_caps.printerUri));
        getResourceFromURI(caps->printer_caps.printerUri, caps->printer_caps.httpResource, 1024);
        if (caps->http == NULL) {
            LOGE("_init(): http is NULL ");
        }
    } while (0);
}

static status_t _get_capabilities(const ifc_printer_capabilities_t *this_p,
        printer_capabilities_t *capabilities) {
    LOGD("_get_capabilities: Enter");
    status_t result = ERROR;
    ipp_capabilities_t *caps = NULL;
    ipp_t *request = NULL; // IPP request object
    ipp_t *response = NULL; // IPP response object
    ipp_attribute_t *attrptr; // Attribute pointer
    int op = IPP_GET_PRINTER_ATTRIBUTES;

    ipp_status_t ipp_status; // Status of IPP request

    if (capabilities != NULL) {
        memset(capabilities, 0, sizeof(printer_capabilities_t));
    }

    do {
        if (this_p == NULL) {
            break;
        }

        caps = IMPL(ipp_capabilities_t, ifc, this_p);
        if (caps->http == NULL) {
            LOGD("_get_capabilities: caps->http is NULL");
            break;
        }

        request = ippNewRequest(op);

        ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL,
                caps->printer_caps.printerUri);

        ippAddStrings(request, IPP_TAG_OPERATION, IPP_TAG_KEYWORD, "requested-attributes",
                sizeof(pattrs) / sizeof(pattrs[0]), NULL, pattrs);

        LOGD("IPP_GET_PRINTER_ATTRIBUTES %s request:", ippOpString(op));
        for (attrptr = ippFirstAttribute(request); attrptr; attrptr = ippNextAttribute(request)) {
            print_attr(attrptr);
        }

        response = ipp_doCupsRequest(caps->http, request, caps->printer_caps.httpResource,
                caps->printer_caps.printerUri);
        if (response == NULL) {
            ipp_status = cupsLastError();
            LOGE("_get_capabilities: %s response is null:  ipp_status %d %s",
                    caps->printer_caps.printerUri, ipp_status, ippErrorString(ipp_status));
        } else {
            ipp_status = cupsLastError();
            LOGD("ipp CUPS last ERROR: %d, %s", ipp_status, ippErrorString(ipp_status));
            LOGD("%s received, now call parse_printerAttributes:", ippOpString(op));
            parse_printerAttributes(response, capabilities);

#if LOG_LEVEL <= LEVEL_DEBUG
            for (attrptr = ippFirstAttribute(response); attrptr; attrptr = ippNextAttribute(
                    response)) {
                print_attr(attrptr);
            }
#endif // LOG_LEVEL <= LEVEL_DEBUG
            if ((attrptr = ippFindAttribute(response, "printer-state", IPP_TAG_ENUM)) == NULL) {
                LOGD("printer-state: null");
            } else {
                LOGI("printer-state %d", (ipp_pstate_t) ippGetInteger(attrptr, 0));
            }
        }

        if (ipp_status >= IPP_OK && ipp_status < IPP_REDIRECTION_OTHER_SITE && response != NULL) {
            result = OK;
        } else {
            result = ERROR;
        }
    } while (0);

    ippDelete(response);
    ippDelete(request);

    if ((caps != NULL) && (capabilities != NULL)) {
        memcpy(capabilities->httpResource, caps->printer_caps.httpResource,
                sizeof(capabilities->httpResource));
    }

    LOGI(" ippstatus_capabilities: _get_capabilities: returning %d:", result);
    return result;
}

static void _destroy(const ifc_printer_capabilities_t *this_p) {
    ipp_capabilities_t *caps;
    LOGD("_destroy(): enter");
    do {
        if (this_p == NULL) {
            continue;
        }

        caps = IMPL(ipp_capabilities_t, ifc, this_p);
        if (caps->http != NULL) {
            httpClose(caps->http);
        }
        free(caps);
    } while (0);
}