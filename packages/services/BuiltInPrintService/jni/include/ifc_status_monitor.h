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
#ifndef __IFC_STATUS_MONITOR_H__
#define __IFC_STATUS_MONITOR_H__

#include "wprint_status_types.h"

/*
 * Interface for monitoring printer status as a job is processed
 */
typedef struct ifc_status_monitor_st {
    /*
     * Initializes a printer status monitor with connections params
     */
    void (*init)(const struct ifc_status_monitor_st *this_p,
            const wprint_connect_info_t *);

    /*
     * Gets status from a printer
     */
    void (*get_status)(const struct ifc_status_monitor_st *this_p,
            printer_state_dyn_t *printer_state_dyn);

    /*
     * Cancels the job
     */
    status_t (*cancel)(const struct ifc_status_monitor_st *this_p, const char *requesting_user);

    /*
     * Starts monitoring printer status; accepts a callback for handling status updates
     */
    void (*start)(const struct ifc_status_monitor_st *this_p,
            void (*status_callback)(const printer_state_dyn_t *new_status,
                    const printer_state_dyn_t *old_status, void *param),
            void *param);

    /*
     * Stops monitoring printer status
     */
    void (*stop)(const struct ifc_status_monitor_st *this_p);

    /*
     * Destroy printer status monitor
     */
    void (*destroy)(const struct ifc_status_monitor_st *this_p);
} ifc_status_monitor_t;

#endif // __IFC_STATUS_MONITOR_H__