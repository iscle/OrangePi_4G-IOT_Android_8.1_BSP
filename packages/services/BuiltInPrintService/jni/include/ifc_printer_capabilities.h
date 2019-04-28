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
#ifndef __IFC_PRINTER_CAPABILITIES_H__
#define __IFC_PRINTER_CAPABILITIES_H__

#include "printer_capabilities_types.h"

/*
 * Interface for handling printer capabilities
 */
typedef struct ifc_printer_capabilities_st {
    /*
     * Initialize capabilities handle with conn params
     */
    void (*init)(const struct ifc_printer_capabilities_st *this_p,
            const wprint_connect_info_t *connect_info);

    /*
     * Fetch printer capabilities and load them into supplied capabilities structure
     */
    status_t (*get_capabilities)(const struct ifc_printer_capabilities_st *this_p,
            printer_capabilities_t *capabilities);

    /*
     * Gets margin info for given paper size
     */
    status_t (*get_margins)(const struct ifc_printer_capabilities_st *this_p,
            const media_size_t paper_size, unsigned char borderless, unsigned char duplex,
            float *top_margin, float *left_margin, float *right_margin, float *bottom_margin);

    /*
     * Destroys printer capabilities handle
     */
    void (*destroy)(const struct ifc_printer_capabilities_st *this_p);
} ifc_printer_capabilities_t;

#endif // __IFC_PRINTER_CAPABILITIES_H__