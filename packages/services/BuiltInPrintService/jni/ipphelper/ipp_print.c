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

#include "ipp_print.h"
#include <math.h>
#include "ipphelper.h"
#include "wprint_debug.h"

#include "plugins/media.h"

#define TAG "ipp_print"

static status_t _init(const ifc_print_job_t *this_p, const char *printer_address, int port,
        const char *printer_uri, bool use_secure_uri);

static status_t _validate_job(const ifc_print_job_t *this_p, wprint_job_params_t *job_params);

static status_t _start_job(const ifc_print_job_t *this_p, const wprint_job_params_t *job_params);

static int _send_data(const ifc_print_job_t *this_p, const char *buffer, size_t length);

static status_t _end_job(const ifc_print_job_t *this_p);

static void _destroy(const ifc_print_job_t *this_p);

static const ifc_print_job_t _print_job_ifc = {
        .init = _init, .validate_job = _validate_job, .start_job = _start_job,
        .send_data = _send_data, .end_job = _end_job, .destroy = _destroy, .enable_timeout = NULL,
};

/*
 * Struct for handling an ipp print job
 */
typedef struct {
    http_t *http;
    char printer_uri[1024];
    char http_resource[1024];
    http_status_t status;
    ifc_print_job_t ifc;
    const char *useragent;
} ipp_print_job_t;

/*
 * Returns a print job handle for an ipp print job
 */
const ifc_print_job_t *ipp_get_print_ifc(const ifc_wprint_t *wprint_ifc) {
    LOGD("ipp_get_print_ifc: Enter");
    ipp_print_job_t *ipp_job = (ipp_print_job_t *) malloc(sizeof(ipp_print_job_t));

    if (ipp_job == NULL) {
        return NULL;
    }

    memset(ipp_job, 0, sizeof(ipp_print_job_t));
    ipp_job->status = HTTP_CONTINUE;

    memcpy(&ipp_job->ifc, &_print_job_ifc, sizeof(ifc_print_job_t));

    return &ipp_job->ifc;
}

static status_t _init(const ifc_print_job_t *this_p, const char *printer_address, int port,
        const char *printer_uri, bool use_secure_uri) {
    LOGD("_init: Enter");
    ipp_print_job_t *ipp_job;
    const char *ipp_scheme;

    if (this_p == NULL) {
        return ERROR;
    }

    ipp_job = IMPL(ipp_print_job_t, ifc, this_p);
    if (ipp_job->http != NULL) {
        httpClose(ipp_job->http);
    }

    if ((printer_uri == NULL) || (strlen(printer_uri) == 0)) {
        printer_uri = DEFAULT_IPP_URI_RESOURCE;
    }

    int ippPortNumber = ((port == IPP_PORT) ? ippPort() : port);
    LOGD("Normal URI for %s:%d", printer_address, ippPortNumber);
    ipp_scheme = (use_secure_uri) ? IPPS_PREFIX : IPP_PREFIX;

    httpAssembleURIf(HTTP_URI_CODING_ALL, ipp_job->printer_uri, sizeof(ipp_job->printer_uri),
            ipp_scheme, NULL, printer_address, ippPortNumber, printer_uri);
    getResourceFromURI(ipp_job->printer_uri, ipp_job->http_resource, 1024);
    if (use_secure_uri) {
        ipp_job->http = httpConnectEncrypt(printer_address, ippPortNumber, HTTP_ENCRYPTION_ALWAYS);

        // If ALWAYS doesn't work, fall back to REQUIRED
        if (ipp_job->http == NULL) {
            ipp_job->http = httpConnectEncrypt(printer_address, ippPortNumber, HTTP_ENCRYPT_REQUIRED);
        }
    } else {
        ipp_job->http = httpConnectEncrypt(printer_address, ippPortNumber, HTTP_ENCRYPTION_IF_REQUESTED);
    }

    httpSetTimeout(ipp_job->http, DEFAULT_IPP_TIMEOUT, NULL, 0);

    return OK;
}

static void _destroy(const ifc_print_job_t *this_p) {
    LOGD("_destroy: Enter");
    ipp_print_job_t *ipp_job;
    if (this_p == NULL) {
        return;
    }

    ipp_job = IMPL(ipp_print_job_t, ifc, this_p);
    if (ipp_job->http != NULL) {
        httpClose(ipp_job->http);
    }

    free(ipp_job);
}

/*
 * Outputs width, height, and name for a given media size
 */
static void _get_pwg_media_size(media_size_t media_size, float *mediaWidth, float *mediaHeight,
        const char **mediaSizeName) {
    int i = 0;

    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (media_size == SupportedMediaSizes[i].media_size) {
            // Get the dimensions in 100 mm
            if ((SupportedMediaSizes[i].WidthInMm == UNKNOWN_VALUE) ||
                    (SupportedMediaSizes[i].HeightInMm == UNKNOWN_VALUE)) {
                *mediaWidth = floorf(_MI_TO_100MM(SupportedMediaSizes[i].WidthInInches));
                *mediaHeight = floorf(_MI_TO_100MM(SupportedMediaSizes[i].HeightInInches));
            } else {
                *mediaWidth = SupportedMediaSizes[i].WidthInMm * 100;
                *mediaHeight = SupportedMediaSizes[i].HeightInMm * 100;
            }
            *mediaSizeName = (char *) SupportedMediaSizes[i].PWGName;

            LOGD("_get_pwg_media_size(): match found: %d, %s, width=%f, height=%f",
                    media_size, SupportedMediaSizes[i].PCL6Name, *mediaWidth, *mediaHeight);
            break;  // we found a match, so break out of loop
        }
    }

    if (i == SUPPORTED_MEDIA_SIZE_COUNT) {
        // media size not found, defaulting to letter
        LOGD("_get_pwg_media_size(): media size, %d, NOT FOUND, setting to letter", media_size);
        _get_pwg_media_size(US_LETTER, mediaWidth, mediaHeight, mediaSizeName);
    }
}

/*
 * Fills and returns an ipp request object with the given job parameters
 */
static ipp_t *_fill_job(int ipp_op, char *printer_uri, const wprint_job_params_t *job_params) {
    LOGD("_fill_job: Enter");
    ipp_t *request = NULL; // IPP request object
    ipp_attribute_t *attrptr; // Attribute pointer
    ipp_t *col[2];
    int col_index = -1;

    if (job_params == NULL) return NULL;

    request = ippNewRequest(ipp_op);
    if (request == NULL) {
        return request;
    }

    if (set_ipp_version(request, printer_uri, NULL, IPP_VERSION_RESOLVED) != 0) {
        ippDelete(request);
        return NULL;
    }
    bool is_2_0_capable = job_params->ipp_2_0_supported;
    bool is_ePCL_ipp_capable = job_params->epcl_ipp_supported;

    ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL,
            printer_uri);

    ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_NAME, "requesting-user-name", NULL,
            job_params->job_originating_user_name);
    ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_NAME, "job-name", NULL, job_params->job_name);

    // Fields for Document source application and source OS
    bool is_doc_format_details_supported = (
            job_params->accepts_app_name ||
                    job_params->accepts_app_version ||
                    job_params->accepts_os_name ||
                    job_params->accepts_os_version);

    if (is_doc_format_details_supported) {
        ipp_t *document_format_details = ippNew();
        if (job_params->accepts_app_name) {
            ippAddString(document_format_details, IPP_TAG_OPERATION, IPP_TAG_NAME,
                    "document-source-application-name", NULL, g_appName);
        }
        if (job_params->accepts_app_version) {
            ippAddString(document_format_details, IPP_TAG_OPERATION, IPP_TAG_TEXT,
                    "document-source-application-version", NULL, g_appVersion);
        }
        if (job_params->accepts_os_name) {
            ippAddString(document_format_details, IPP_TAG_OPERATION, IPP_TAG_NAME,
                    "document-source-os-name", NULL, g_osName);
        }
        if (job_params->accepts_os_version) {
            char version[40];
            sprintf(version, "%d", g_API_version);
            ippAddString(document_format_details, IPP_TAG_OPERATION, IPP_TAG_TEXT,
                    "document-source-os-version", NULL, version);
        }

        ippAddCollection(request, IPP_TAG_OPERATION, "document-format-details",
                document_format_details);
        ippDelete(document_format_details);
    }

    LOGD("_fill_job: pcl_type(%d), print_format(%s)", job_params->pcl_type,
            job_params->print_format);
    if (strcmp(job_params->print_format, PRINT_FORMAT_PDF) == 0) {
        if (is_2_0_capable) {
            // document-format needs to be the very next attribute for some printers
            ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_MIMETYPE, "document-format", NULL,
                    PRINT_FORMAT_PDF);
            LOGD("_fill_job: setting document-format: %s", PRINT_FORMAT_PDF);
        } else {
            // some earlier devices don't print pdfs when we send the other PRINT_FORMAT_PDF
            ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_MIMETYPE, "document-format", NULL,
                    (job_params->accepts_pclm ? PRINT_FORMAT_PCLM : PRINT_FORMAT_PDF));
            LOGD("_fill_job: setting document-format: %s",
                    (job_params->accepts_pclm ? PRINT_FORMAT_PCLM : PRINT_FORMAT_PDF));
        }

        if (is_ePCL_ipp_capable) {
            if (job_params->render_flags & RENDER_FLAG_AUTO_SCALE) {
                ippAddBoolean(request, IPP_TAG_JOB, "pdf-fit-to-page", 1); // true
            }
        }

        // Fix Orientation bug for PDF printers only.
        if (job_params->render_flags & RENDER_FLAG_PORTRAIT_MODE) {
            ippAddInteger(request, IPP_TAG_JOB, IPP_TAG_ENUM, "orientation-requested",
                    IPP_PRINT_ORIENTATION_PORTRAIT);
        }
        if (job_params->render_flags & RENDER_FLAG_LANDSCAPE_MODE) {
            ippAddInteger(request, IPP_TAG_JOB, IPP_TAG_ENUM, "orientation-requested",
                    IPP_PRINT_ORIENTATION_LANDSCAPE);
        }
    } else {
        switch (job_params->pcl_type) {
            case PCLm:
                ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_MIMETYPE, "document-format", NULL,
                        PRINT_FORMAT_PCLM);
                LOGD("_fill_job: setting document-format: %s", PRINT_FORMAT_PCLM);
                if (is_ePCL_ipp_capable) {
                    ippAddResolution(request, IPP_TAG_JOB, "pclm-source-resolution",
                            IPP_RES_PER_INCH, job_params->pixel_units,
                            job_params->pixel_units);
                }
                break;
            case PCLPWG:
                ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_MIMETYPE, "document-format", NULL,
                        PRINT_FORMAT_PWG);
                LOGD("_fill_job: setting document-format: %s", PRINT_FORMAT_PWG);
                break;
            default:
                LOGD("_fill_job: unrecognized pcl_type: %d", job_params->pcl_type);
                ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_MIMETYPE, "document-format", NULL,
                        PRINT_FORMAT_AUTO);
                break;
        }

        if (is_ePCL_ipp_capable) {
            ippAddBoolean(request, IPP_TAG_JOB, "margins-pre-applied", 1); // true
        }
    }

    // Add copies support if required and allowed
    if (job_params->copies_supported && (strcmp(job_params->print_format, PRINT_FORMAT_PDF) == 0)) {
        ippAddInteger(request, IPP_TAG_JOB, IPP_TAG_INTEGER, "copies", job_params->num_copies);
    }

    ippAddResolution(request, IPP_TAG_JOB, "printer-resolution", IPP_RES_PER_INCH,
            job_params->pixel_units, job_params->pixel_units);
    if (job_params->duplex == DUPLEX_MODE_BOOK) {
        ippAddString(request, IPP_TAG_JOB, IPP_TAG_KEYWORD, IPP_SIDES_TAG, NULL,
                IPP_SIDES_TWO_SIDED_LONG_EDGE);
    } else if (job_params->duplex == DUPLEX_MODE_TABLET) {
        ippAddString(request, IPP_TAG_JOB, IPP_TAG_KEYWORD, IPP_SIDES_TAG, NULL,
                IPP_SIDES_TWO_SIDED_SHORT_EDGE);
    } else {
        ippAddString(request, IPP_TAG_JOB, IPP_TAG_KEYWORD, IPP_SIDES_TAG, NULL,
                IPP_SIDES_ONE_SIDED);
    }

    if (job_params->color_space == COLOR_SPACE_MONO) {
        ippAddString(request, IPP_TAG_JOB, IPP_TAG_KEYWORD, IPP_OUTPUT_MODE_TAG, NULL,
                IPP_OUTPUT_MODE_MONO);
    } else {
        ippAddString(request, IPP_TAG_JOB, IPP_TAG_KEYWORD, IPP_OUTPUT_MODE_TAG, NULL,
                IPP_OUTPUT_MODE_COLOR);
    }

    if (is_2_0_capable) {
        // Not friendly to 1.1 devices.
        if (job_params->media_tray != TRAY_SRC_AUTO_SELECT) {
            if (job_params->media_tray == TRAY_SOURCE_PHOTO_TRAY) {
                col[++col_index] = ippNew();
                ippAddString(col[col_index], IPP_TAG_JOB, IPP_TAG_KEYWORD, "media-source", NULL,
                        "main-tray");
            } else if (job_params->media_tray == TRAY_SOURCE_TRAY_1) {
                col[++col_index] = ippNew();
                ippAddString(col[col_index], IPP_TAG_JOB, IPP_TAG_KEYWORD, "media-source", NULL,
                        "main-tray");
            }
        }

        // MEDIA-Type is IPP 2.0 only
        // put margins in with media-type
        // Client-Error-Attribute-Or-Values-Not-Supported
        col[++col_index] = ippNew();

        // set margins - if negative margins, set to full-bleed; otherwise set calculated values
        if (job_params->borderless) {
            LOGD("Setting Up BORDERLESS");
            ippAddInteger(col[col_index], IPP_TAG_JOB, IPP_TAG_INTEGER, "media-bottom-margin", 0);
            ippAddInteger(col[col_index], IPP_TAG_JOB, IPP_TAG_INTEGER, "media-top-margin", 0);
            ippAddInteger(col[col_index], IPP_TAG_JOB, IPP_TAG_INTEGER, "media-left-margin", 0);
            ippAddInteger(col[col_index], IPP_TAG_JOB, IPP_TAG_INTEGER, "media-right-margin", 0);
        }

        switch (job_params->media_type) {
            case MEDIA_PHOTO_GLOSSY:
                ippAddString(col[col_index], IPP_TAG_JOB, IPP_TAG_KEYWORD, "media-type", NULL,
                        "photographic-glossy");
                break;
            case MEDIA_PHOTO:
            case MEDIA_ADVANCED_PHOTO:
            case MEDIA_PHOTO_MATTE:
            case MEDIA_PREMIUM_PHOTO:
            case MEDIA_OTHER_PHOTO:
                ippAddString(col[col_index], IPP_TAG_JOB, IPP_TAG_KEYWORD, "media-type", NULL,
                        "photographic");
                break;
            default:
                ippAddString(col[col_index], IPP_TAG_JOB, IPP_TAG_KEYWORD, "media-type", NULL,
                        "stationery");
                break;
        }

        float mediaWidth;
        float mediaHeight;
        const char *mediaSizeName = NULL;
        _get_pwg_media_size(job_params->media_size, &mediaWidth, &mediaHeight, &mediaSizeName);
        ipp_t *mediaSize = ippNew();

        if ((job_params->media_size_name) && (mediaSizeName != NULL)) {
            ippAddString(mediaSize, IPP_TAG_JOB, IPP_TAG_KEYWORD, "media-size-name", NULL,
                    mediaSizeName);
        } else {
            ippAddInteger(mediaSize, IPP_TAG_JOB, IPP_TAG_INTEGER, "x-dimension", (int) mediaWidth);
            ippAddInteger(mediaSize, IPP_TAG_JOB, IPP_TAG_INTEGER, "y-dimension",
                    (int) mediaHeight);
        }
        ippAddCollection(col[col_index], IPP_TAG_JOB, "media-size", mediaSize);

        // can either set media or media-col.
        // if both sent, device should return client-error-bad-request
        ippAddCollections(request, IPP_TAG_JOB, "media-col", col_index + 1, (const ipp_t **) col);
        while (col_index >= 0) {
            ippDelete(col[col_index--]);
        }
    } else {
        ippAddString(request, IPP_TAG_JOB, IPP_TAG_KEYWORD, "media", NULL,
                mapDFMediaToIPPKeyword(job_params->media_size));
    }

    LOGI("_fill_job (%d): request", ipp_op);
    for (attrptr = ippFirstAttribute(request); attrptr; attrptr = ippNextAttribute(request)) {
        print_attr(attrptr);
    }

    return request;
}

static status_t  _validate_job(const ifc_print_job_t *this_p, wprint_job_params_t *job_params) {
    LOGD("_validate_job: Enter");
    status_t result = ERROR;
    ipp_print_job_t *ipp_job;
    ipp_t *response;
    ipp_t *request = NULL;
    ipp_status_t ipp_status;

    LOGD("_validate_job: ** validatePrintJob:  Entry");
    do {
        if (this_p == NULL) {
            break;
        }

        if (job_params == NULL) {
            break;
        }

        ipp_job = IMPL(ipp_print_job_t, ifc, this_p);
        if (ipp_job->http == NULL) {
            break;
        }

        ipp_job->useragent = NULL;
        if ((job_params->useragent != NULL) && (strlen(job_params->useragent) > 0)) {
            ipp_job->useragent = job_params->useragent;
        }

        request = _fill_job(IPP_VALIDATE_JOB, ipp_job->printer_uri, job_params);

        if (ipp_job->useragent != NULL) {
            httpSetDefaultField(ipp_job->http, HTTP_FIELD_USER_AGENT, ipp_job->useragent);
        }
        if ((response = ipp_doCupsRequest(ipp_job->http, request, ipp_job->http_resource,
                ipp_job->printer_uri))
                == NULL) {
            ipp_status = cupsLastError();
            LOGE("_validate_job:  validatePrintJob:  response is null:  ipp_status %d %s",
                    ipp_status, ippErrorString(ipp_status));
        } else {
            ipp_status = cupsLastError();
            LOGI("_validate_job: %s ipp_status %d  %x received:", ippOpString(IPP_VALIDATE_JOB),
                    ipp_status, ipp_status);
            ipp_attribute_t *attrptr;
            for (attrptr = ippFirstAttribute(response); attrptr; attrptr = ippNextAttribute(
                    response)) {
                print_attr(attrptr);
            }

            ippDelete(response);
        }

        LOGD("_validate_job : ipp_status: %d", ipp_status);
        if (strncmp(ippErrorString(ipp_status), ippErrorString(IPP_OK),
                strlen(ippErrorString(IPP_OK))) == 0) {
            result = OK;
        } else {
            result = ERROR;
        }
    } while (0);

    ippDelete(request);

    LOGD("_validate_job: ** validate_job result: %d", result);

    return result;
}

static status_t _start_job(const ifc_print_job_t *this_p, const wprint_job_params_t *job_params) {
    LOGD("_start_job: Enter");
    status_t result;
    ipp_print_job_t *ipp_job;
    ipp_t *request = NULL;
    bool retry;
    int failed_count = 0;

    LOGD("_start_job entry");
    do {
        retry = false;
        if (this_p == NULL) {
            LOGE("_start_job; this_p == NULL");
            continue;
        }

        ipp_job = IMPL(ipp_print_job_t, ifc, this_p);

        ipp_job->useragent = NULL;
        if ((job_params->useragent != NULL) && (strlen(job_params->useragent) > 0)) {
            ipp_job->useragent = job_params->useragent;
        }
        request = _fill_job(IPP_PRINT_JOB, ipp_job->printer_uri, job_params);

        if (request == NULL) {
            continue;
        }

        if (ipp_job->useragent != NULL) {
            httpSetDefaultField(ipp_job->http, HTTP_FIELD_USER_AGENT, ipp_job->useragent);
        }
        ipp_job->status = cupsSendRequest(ipp_job->http, request, ipp_job->http_resource, 0);
        if (ipp_job->status != HTTP_CONTINUE) {
            failed_count++;
            if ((failed_count == 1) &&
                    ((ipp_job->status == HTTP_ERROR) || (ipp_job->status >= HTTP_BAD_REQUEST))) {
                retry = true;
                LOGI("_start_job retry due to internal error");
                // We will retry for one of these failures since we could have just
                // lost our connection to the server and cups will not always attempt
                // a reconnect for us.
                ippDelete(request);
                continue;
            }

            _cupsSetHTTPError(ipp_job->status);
        }
        ippDelete(request);
        LOGI("_start_job httpPrint fd %d status %d ipp_status %d", ipp_job->http->fd,
                ipp_job->status, cupsLastError());

        result = ((ipp_job->status == HTTP_CONTINUE) ? OK : ERROR);
    } while (retry);

    return result;
}

static int _send_data(const ifc_print_job_t *this_p, const char *buffer, size_t length) {
    ipp_print_job_t *ipp_job;
    if (this_p == NULL) {
        return ERROR;
    }

    ipp_job = IMPL(ipp_print_job_t, ifc, this_p);
    if (ipp_job->http == NULL) {
        return ERROR;
    }

    if (ipp_job->status != HTTP_CONTINUE) {
        return ERROR;
    }

    if (length != 0) {
        if (ipp_job->useragent != NULL) {
            httpSetDefaultField(ipp_job->http, HTTP_FIELD_USER_AGENT, ipp_job->useragent);
        }
        ipp_job->status = cupsWriteRequestData(ipp_job->http, buffer, length);
    }
    return ((ipp_job->status == HTTP_CONTINUE) ? length : (int) ERROR);
}

static status_t _end_job(const ifc_print_job_t *this_p) {
    LOGD("_end_job: Enter");
    status_t result = ERROR;
    ipp_t *response;
    ipp_attribute_t *attrptr;
    int op = IPP_PRINT_JOB;
    ipp_print_job_t *ipp_job;
    int job_id = -1;

    char buffer[1024];

    if (this_p == NULL) {
        return result;
    }

    ipp_job = IMPL(ipp_print_job_t, ifc, this_p);

    if (ipp_job->http == NULL) {
        return result;
    }

    LOGD("_end_job: entry httpPrint %d", ipp_job->http->fd);

    if (ipp_job->useragent != NULL) {
        httpSetDefaultField(ipp_job->http, HTTP_FIELD_USER_AGENT, ipp_job->useragent);
    }
    ipp_job->status = cupsWriteRequestData(ipp_job->http, buffer, 0);

    if (ipp_job->status != HTTP_CONTINUE) {
        LOGE("Error: from cupsWriteRequestData http.fd %d:  status %d",
                ipp_job->http->fd, ipp_job->status);
    } else {
        result = OK;
        LOGD("0 length Bytes sent, status %d", ipp_job->status);
        response = cupsGetResponse(ipp_job->http, ipp_job->http_resource);

        if ((attrptr = ippFindAttribute(response, "job-id", IPP_TAG_INTEGER)) == NULL) {
            LOGE("sent cupsGetResponse %s job id is null; received", ippOpString(op));
        } else {
            job_id = ippGetInteger(attrptr, 0);
            LOGI("sent cupsGetResponse %s job_id %d; received", ippOpString(op), job_id);
        }

        if (response != NULL) {
            for (attrptr = ippFirstAttribute(response); attrptr; attrptr = ippNextAttribute(
                    response)) {
                print_attr(attrptr);
                if (strcmp(ippGetName(attrptr), "job-state-reasons") == 0) {
                    int i;
                    for (i = 0; i < ippGetCount(attrptr); i++) {
                        if (strcmp(ippGetString(attrptr, i, NULL), "job-canceled-at-device")
                                == 0) {
                            result = CANCELLED;
                            break;
                        }
                    }
                }
            }
            ippDelete(response);
        }
    }
    LOGD("_end_job: exit status %d job_id %d", ipp_job->status, job_id);

    return result;
}