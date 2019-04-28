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

#ifndef _IPP_STATUS_MONITOR_H_
#define _IPP_STATUS_MONITOR_H_

#include "ifc_status_monitor.h"
#include "ifc_wprint.h"

/*
 * Returns a printer status monitor interface
 */
extern const ifc_status_monitor_t *ipp_status_get_monitor_ifc(const ifc_wprint_t *wprint_ifc);

#endif // _IPP_STATUS_MONITOR_H_