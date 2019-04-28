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

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include "ifc_print_job.h"
#include "wprint_debug.h"

#define TAG "plugin_pdf"
#define BUFF_SIZE 8192

extern int g_API_version;

typedef struct {
    const ifc_wprint_t *wprint_ifc;
    const ifc_print_job_t *print_ifc;
} plugin_data_t;

static const char *_mime_types[] = {
        MIME_TYPE_PDF,
        NULL};

static const char *_print_formats[] = {
        PRINT_FORMAT_PDF,
        NULL};

static const char **_get_mime_types(void) {
    return _mime_types;
}

static const char **_get_print_formats(void) {
    return _print_formats;
}

static int _start_job(wJob_t job_handle, const ifc_wprint_t *wprint_ifc_p,
        const ifc_print_job_t *print_job_ifc_p,
        wprint_job_params_t *job_params) {
    plugin_data_t *priv;
    if (job_params == NULL) return ERROR;

    job_params->plugin_data = NULL;
    if ((wprint_ifc_p == NULL) || (print_job_ifc_p == NULL)) return ERROR;

    priv = (plugin_data_t *) malloc(sizeof(plugin_data_t));
    priv->wprint_ifc = (ifc_wprint_t *) wprint_ifc_p;
    priv->print_ifc = (ifc_print_job_t *) print_job_ifc_p;

    job_params->plugin_data = (void *) priv;
    return OK;
}

static int _print_page(wprint_job_params_t *job_params, const char *mime_type,
        const char *pathname) {
    plugin_data_t *priv;
    int fd;
    int result = OK;
    int rbytes, wbytes, nbytes = 0;
    char *buff;

    if (job_params == NULL) return ERROR;

    priv = (plugin_data_t *) job_params->plugin_data;

    if (priv == NULL) return ERROR;

    //  open the PDF file and dump it to the socket
    if (pathname && strlen(pathname)) {
        buff = malloc(BUFF_SIZE);
        if (buff == NULL) {
            return ERROR;
        }

        fd = open(pathname, O_RDONLY);
        if (fd != ERROR) {
            rbytes = read(fd, buff, BUFF_SIZE);

            while ((rbytes > 0) && !job_params->cancelled) {
                wbytes = priv->print_ifc->send_data(priv->print_ifc, buff, rbytes);
                if (wbytes == rbytes) {
                    nbytes += wbytes;
                    rbytes = read(fd, buff, BUFF_SIZE);
                } else {
                    LOGE("ERROR: write() failed, %s", strerror(errno));
                    result = ERROR;
                    break;
                }
            }
            LOGI("dumped %d bytes of %s to printer", nbytes, pathname);
            close(fd);
        }

        free(buff);
    }
    if ((job_params->page_range != NULL) && (strcmp(job_params->page_range, "") != 0)) {
        remove(pathname);
    }
    return result;
}

static int _end_job(wprint_job_params_t *job_params) {
    if (job_params != NULL) {
        if (job_params->plugin_data != NULL) {
            free(job_params->plugin_data);
        }
    }
    return OK;
}

wprint_plugin_t *libwprintplugin_pdf_reg(void) {
    static const wprint_plugin_t _pdf_plugin = {.version = WPRINT_PLUGIN_VERSION(0),
            .priority = PRIORITY_PASSTHRU, .get_mime_types = _get_mime_types,
            .get_print_formats = _get_print_formats, .start_job = _start_job,
            .print_page = _print_page, .print_blank_page = NULL, .end_job = _end_job,};
    return ((wprint_plugin_t *) &_pdf_plugin);
}