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

#ifndef _IPP_PRINT_H_
#define _IPP_PRINT_H_

#include "ifc_print_job.h"
#include "ifc_wprint.h"
#include "cups-private.h"

#ifdef __cplusplus
extern "C" {
#endif // __cplusplus

const ifc_print_job_t *ipp_get_print_ifc(const ifc_wprint_t *wprint_ifc);

#ifdef __cplusplus
#endif // __cplusplus
#endif // !_IPP_PRINT_H_