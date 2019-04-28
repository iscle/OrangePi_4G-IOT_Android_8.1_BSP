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
#ifndef __LIB_PRINTABLE_AREA_H__
#define __LIB_PRINTABLE_AREA_H__

#include "wprint_df_types.h"
#include "lib_wprint.h"

/*
 * Fills printable area parameter in job_params given margins
 */
extern void printable_area_get(wprint_job_params_t *job_params, float top_margin,
        float left_margin, float right_margin, float bottom_margin);

/*
 * Returns default margins for given printer specified in the job params struct
 */
extern void printable_area_get_default_margins(const wprint_job_params_t *job_params,
        const printer_capabilities_t *printer_cap, float *top_margin, float *left_margin,
        float *right_margin, float *bottom_margin);

#endif // __LIB_PRINTABLE_AREA_H__