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
#ifndef __IFC_PRINT_JOB_H__
#define __IFC_PRINT_JOB_H__

#include "lib_wprint.h"
#include "ifc_wprint.h"

/*
 * Interface for handling jobs
 */
typedef struct ifc_print_job_st {
    /*
     * Initializes print job handle with given connection params.
     */
    status_t (*init)(const struct ifc_print_job_st *this_p, const char *printer_address, int port,
            const char *printer_uri, bool use_secure_uri);

    /*
     * Validates job and connection params, updating parameters as necessary.
     */
    status_t (*validate_job)(const struct ifc_print_job_st *this_p,
            wprint_job_params_t *job_params);

    /*
     * Start a print job with given params
     */
    status_t (*start_job)(const struct ifc_print_job_st *this_p,
            const wprint_job_params_t *job_params);

    /*
     * Sends data to the ip address set on initialization, returning the amount of data
     * written or -1 for an error.
     */
    int (*send_data)(const struct ifc_print_job_st *this_p, const char *buffer,
            size_t bufferLength);

    /*
     * Returns print job status
     */
    status_t (*check_status)(const struct ifc_print_job_st *this_p);

    /*
     * Ends a print job
     */
    status_t (*end_job)(const struct ifc_print_job_st *this_p);

    /*
     * Destroys a print job handle
     */
    void (*destroy)(const struct ifc_print_job_st *this_p);

    /*
     * Enable a timeout for a print job
     */
    void (*enable_timeout)(const struct ifc_print_job_st *this_p,
            int enable);
} ifc_print_job_t;

/*
 * Connect to a printer with a given protocol. Returns a job handle.
 */
const ifc_print_job_t *printer_connect(int port_num);

/*
 * Opens a socket to printer:port and returns it.
 */
int wConnect(const char *printer_addr, int port_num, long int timeout_msec);

#endif // __IFC_PRINT_JOB_H__