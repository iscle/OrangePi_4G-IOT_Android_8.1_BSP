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
#ifndef __WPRINT_IO_PLUGIN_H__
#define __WPRINT_IO_PLUGIN_H__

#include "ifc_printer_capabilities.h"
#include "ifc_print_job.h"
#include "ifc_status_monitor.h"
#include "ifc_wprint.h"

/*
 * Structure for handling input to and output from wprint.
 * Includes interfaces for capabilities, status, and job handling.
 */
typedef struct {
    int version;
    port_t port_num;

    /*
     * Returns capabilities interface
     */
    const ifc_printer_capabilities_t *(*getCapsIFC)(const ifc_wprint_t *wprint_ifc);

    /*
     * Returns status interface
     */
    const ifc_status_monitor_t *(*getStatusIFC)(const ifc_wprint_t *wprint_ifc);

    /*
     * Returns print job handle interface
     */
    const ifc_print_job_t *(*getPrintIFC)(const ifc_wprint_t *wprint_ifc);
} wprint_io_plugin_t;

typedef wprint_io_plugin_t *(*plugin_io_reg_t)(void);

#endif // __WPRINT_IO_PLUGIN_H__