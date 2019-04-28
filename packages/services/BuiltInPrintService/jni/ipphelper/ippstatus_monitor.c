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

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stdlib.h>
#include <stdio.h>
#include <semaphore.h>
#include <fcntl.h>

#include "lib_wprint.h"
#include "ippstatus_monitor.h"
#include "ipphelper.h"

#include "cups.h"
#include "http-private.h"
#include <pthread.h>
#include "wprint_debug.h"

#define TAG "ippstatus_monitor"

static void _init(const ifc_status_monitor_t *this_p, const wprint_connect_info_t *);

static void _get_status(const ifc_status_monitor_t *this_p, printer_state_dyn_t *printer_state_dyn);

static void _start(const ifc_status_monitor_t *this_p, void (*status_cb)(
        const printer_state_dyn_t *new_status, const printer_state_dyn_t *old_status,
                void *status_param), void *param);

static void _stop(const ifc_status_monitor_t *this_p);

static status_t _cancel(const ifc_status_monitor_t *this_p, const char *requesting_user);

static void _destroy(const ifc_status_monitor_t *this_p);

static const ifc_status_monitor_t _status_ifc = {.init = _init, .get_status = _get_status,
        .cancel = _cancel, .start = _start, .stop = _stop, .destroy = _destroy,};

typedef struct {
    unsigned char initialized;
    http_t *http;
    char printer_uri[1024];
    char http_resource[1024];
    unsigned char stop_monitor;
    unsigned char monitor_running;
    sem_t monitor_sem;
    pthread_mutex_t mutex;
    pthread_mutexattr_t mutexattr;
    ifc_status_monitor_t ifc;
} ipp_monitor_t;

const ifc_status_monitor_t *ipp_status_get_monitor_ifc(const ifc_wprint_t *wprint_ifc) {
    ipp_monitor_t *monitor = (ipp_monitor_t *) malloc(sizeof(ipp_monitor_t));

    // setup the interface
    monitor->initialized = 0;
    monitor->http = NULL;
    memcpy(&monitor->ifc, &_status_ifc, sizeof(ifc_status_monitor_t));
    return &monitor->ifc;
}

static void _init(const ifc_status_monitor_t *this_p, const wprint_connect_info_t *connect_info) {
    ipp_monitor_t *monitor;
    LOGD("_init(): enter");
    do {
        if (this_p == NULL) {
            continue;
        }
        monitor = IMPL(ipp_monitor_t, ifc, this_p);

        if (monitor->initialized != 0) {
            sem_post(&monitor->monitor_sem);
            sem_destroy(&monitor->monitor_sem);

            pthread_mutex_unlock(&monitor->mutex);
            pthread_mutex_destroy(&monitor->mutex);
        }

        if (monitor->http != NULL) {
            httpClose(monitor->http);
        }

        monitor->http = ipp_cups_connect(connect_info, monitor->printer_uri,
                sizeof(monitor->printer_uri));
        getResourceFromURI(monitor->printer_uri, monitor->http_resource, 1024);

        monitor->monitor_running = 0;
        monitor->stop_monitor = 0;

        pthread_mutexattr_init(&monitor->mutexattr);
        pthread_mutexattr_settype(&(monitor->mutexattr), PTHREAD_MUTEX_RECURSIVE_NP);
        pthread_mutex_init(&monitor->mutex, &monitor->mutexattr);
        sem_init(&monitor->monitor_sem, 0, 0);
        monitor->initialized = 1;
    } while (0);
}

static void _destroy(const ifc_status_monitor_t *this_p) {
    ipp_monitor_t *monitor;
    LOGD("_destroy(): enter");
    do {
        if (this_p == NULL) {
            continue;
        }

        monitor = IMPL(ipp_monitor_t, ifc, this_p);
        if (monitor->initialized) {
            pthread_mutex_lock(&monitor->mutex);

            sem_post(&monitor->monitor_sem);
            sem_destroy(&monitor->monitor_sem);

            pthread_mutex_unlock(&monitor->mutex);
            pthread_mutex_destroy(&monitor->mutex);
        }

        if (monitor->http != NULL) {
            httpClose(monitor->http);
        }

        free(monitor);
    } while (0);
}

static void _get_status(const ifc_status_monitor_t *this_p,
        printer_state_dyn_t *printer_state_dyn) {
    int i;
    ipp_monitor_t *monitor;
    ipp_pstate_t printer_state;
    ipp_status_t ipp_status;
    LOGD("_get_status(): enter");
    do {
        if (printer_state_dyn == NULL) {
            LOGD("_get_status(): printer_state_dyn is null!");
            continue;
        }

        printer_state_dyn->printer_status = PRINT_STATUS_UNKNOWN;
        printer_state_dyn->printer_reasons[0] = PRINT_STATUS_UNKNOWN;
        for (i = 0; i <= PRINT_STATUS_MAX_STATE; i++) {
            printer_state_dyn->printer_reasons[i] = PRINT_STATUS_MAX_STATE;
        }

        if (this_p == NULL) {
            LOGE("_get_status(): this_p is null!");
            continue;
        }

        monitor = IMPL(ipp_monitor_t, ifc, this_p);
        if (!monitor->initialized) {
            LOGE("_get_status(): Monitor is uninitialized");
            continue;
        }

        if (monitor->http == NULL) {
            LOGE("_get_status(): monitor->http is NULL, setting Unable to Connect");
            printer_state_dyn->printer_reasons[0] = PRINT_STATUS_UNABLE_TO_CONNECT;
            continue;
        }

        printer_state_dyn->printer_status = PRINT_STATUS_IDLE;
        ipp_status = get_PrinterState(monitor->http, monitor->printer_uri, printer_state_dyn,
                &printer_state);
        LOGD("_get_status(): ipp_status=%d", ipp_status);
        debuglist_printerStatus(printer_state_dyn);
    } while (0);
}

static void _start(const ifc_status_monitor_t *this_p,
        void (*status_cb)(const printer_state_dyn_t *new_status,
                const printer_state_dyn_t *old_status, void *status_param),
        void *param) {
    int i;
    printer_state_dyn_t last_status, curr_status;
    ipp_monitor_t *monitor = NULL;

    LOGD("_start(): enter");

    // initialize our status structures
    for (i = 0; i <= PRINT_STATUS_MAX_STATE; i++) {
        curr_status.printer_reasons[i] = PRINT_STATUS_MAX_STATE;
        last_status.printer_reasons[i] = PRINT_STATUS_MAX_STATE;
    }

    last_status.printer_status = PRINT_STATUS_UNKNOWN;
    last_status.printer_reasons[0] = PRINT_STATUS_INITIALIZING;

    curr_status.printer_status = PRINT_STATUS_UNKNOWN;
    curr_status.printer_reasons[0] = PRINT_STATUS_INITIALIZING;

    // send out the first callback
    if (status_cb != NULL) {
        (*status_cb)(&curr_status, &last_status, param);
    }
    do {
        curr_status.printer_status = PRINT_STATUS_SVC_REQUEST;
        curr_status.printer_reasons[0] = PRINT_STATUS_UNABLE_TO_CONNECT;

        if (this_p == NULL) {
            continue;
        }

        monitor = IMPL(ipp_monitor_t, ifc, this_p);
        if (!monitor->initialized) {
            continue;
        }

        if (monitor->monitor_running) {
            continue;
        }

        monitor->stop_monitor = 0;
        monitor->monitor_running = 1;
        if (monitor->http == NULL) {
            if (status_cb != NULL) {
                (*status_cb)(&curr_status, &last_status, param);
            }
            sem_wait(&monitor->monitor_sem);

            last_status.printer_status = PRINT_STATUS_UNKNOWN;
            last_status.printer_reasons[0] = PRINT_STATUS_SHUTTING_DOWN;

            curr_status.printer_status = PRINT_STATUS_UNKNOWN;
            curr_status.printer_reasons[0] = PRINT_STATUS_SHUTTING_DOWN;
        } else {
            while (!monitor->stop_monitor) {
                pthread_mutex_lock(&monitor->mutex);
                _get_status(this_p, &curr_status);
                pthread_mutex_unlock(&monitor->mutex);
                if ((status_cb != NULL) &&
                        (memcmp(&curr_status, &last_status, sizeof(printer_state_dyn_t)) != 0)) {
                    (*status_cb)(&curr_status, &last_status, param);
                    memcpy(&last_status, &curr_status, sizeof(printer_state_dyn_t));
                }
                sleep(1);
            }
        }
        monitor->monitor_running = 0;
    } while (0);

    if (status_cb != NULL) {
        (*status_cb)(&curr_status, &last_status, param);
    }
}

static void _stop(const ifc_status_monitor_t *this_p) {
    // request a stop and release the semaphore
    ipp_monitor_t *monitor;
    LOGD("_stop(): enter");
    do {
        if (this_p == NULL) {
            continue;
        }

        monitor = IMPL(ipp_monitor_t, ifc, this_p);
        if (!monitor->initialized) {
            continue;
        }

        sem_post(&monitor->monitor_sem);
        monitor->stop_monitor = 1;
    } while (0);
}

static status_t _cancel(const ifc_status_monitor_t *this_p, const char *requesting_user) {
    status_t return_value = ERROR;
    int job_id = -1;
    ipp_monitor_t *monitor = NULL;
    ipp_t *request = NULL;
    ipp_t *response = NULL;
    ipp_attribute_t *attr;

    LOGD("_cancel(): enter");

    monitor = IMPL(ipp_monitor_t, ifc, this_p);
    if (this_p != NULL && monitor != NULL && monitor->initialized) {
        pthread_mutex_lock(&monitor->mutex);
        do {
            if (monitor->stop_monitor) {
                break;
            }

            request = ippNewRequest(IPP_GET_JOBS);
            if (request == NULL) {
                break;
            }

            ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL,
                    monitor->printer_uri);
            ippAddBoolean(request, IPP_TAG_OPERATION, "my-jobs", 1);
            ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_NAME, "requesting-user-name",
                    NULL, requesting_user);

            // Requested printer attributes
            static const char *pattrs[] = {"job-id", "job-state", "job-state-reasons"};

            ippAddStrings(request, IPP_TAG_OPERATION, IPP_TAG_KEYWORD, "requested-attributes",
                    sizeof(pattrs) / sizeof(pattrs[1]), NULL, pattrs);

            response = ipp_doCupsRequest(monitor->http, request, monitor->http_resource,
                    monitor->printer_uri);
            if (response == NULL) {
                ipp_status_t ipp_status = cupsLastError();
                LOGD("_cancel get job attributes: response is null, ipp_status %d: %s",
                        ipp_status, ippErrorString(ipp_status));
                return_value = ERROR;
            } else {
                attr = ippFindAttribute(response, "job-id", IPP_TAG_INTEGER);
                if (attr != NULL) {
                    job_id = ippGetInteger(attr, 0);
                    LOGD("_cancel got job-id: %d", job_id);
                } else { // We need the job id to attempt a cancel
                    break;
                }

                attr = ippFindAttribute(response, "job-state", IPP_TAG_ENUM);
                if (attr != NULL) {
                    ipp_jstate_t jobState = (ipp_jstate_t)ippGetInteger(attr, 0);
                    LOGD("_cancel got job-state: %d", jobState);
                }

                attr = ippFindAttribute(response, "job-state-reasons", IPP_TAG_KEYWORD);
                if (attr != NULL) {
                    int idx;
                    for (idx = 0; idx < ippGetCount(attr); idx++) {
                        LOGD("before job-state-reason (%d): %s", idx,
                                ippGetString(attr, idx, NULL));
                    }
                }
            }
        } while (0);

        ippDelete(request);
        request = NULL;
        ippDelete(response);
        response = NULL;

        do {
            if (job_id == -1) {
                break;
            }

            request = ippNewRequest(IPP_CANCEL_JOB);
            if (request == NULL) {
                break;
            }

            ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL,
                    monitor->printer_uri);
            ippAddInteger(request, IPP_TAG_OPERATION, IPP_TAG_INTEGER, "job-id", job_id);
            ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_NAME,
                    "requesting-user-name", NULL, requesting_user);

            if ((response = ipp_doCupsRequest(monitor->http, request, monitor->http_resource,
                    monitor->printer_uri)) == NULL) {
                ipp_status_t ipp_status = cupsLastError();
                LOGD("cancel:  response is null:  ipp_status %d %s", ipp_status,
                        ippErrorString(ipp_status));
                return_value = ERROR;
            } else {
                ipp_status_t ipp_status = cupsLastError();
                LOGE("IPP_Status for cancel request was %d %s", ipp_status,
                        ippErrorString(ipp_status));
                attr = ippFindAttribute(response, "job-state-reasons", IPP_TAG_KEYWORD);
                if (attr != NULL) {
                    int idx;
                    for (idx = 0; ippGetCount(attr); idx++) {
                        LOGD("job-state-reason (%d): %s", idx, ippGetString(attr, idx, NULL));
                    }
                }
                return_value = OK;
            }
        } while (0);

        ippDelete(request);
        ippDelete(response);

        if (monitor->initialized) {
            pthread_mutex_unlock(&monitor->mutex);
        }
    }
    return return_value;
}