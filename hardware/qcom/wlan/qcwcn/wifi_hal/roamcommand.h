/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __WIFI_HAL_ROAM_COMMAND_H__
#define __WIFI_HAL_ROAM_COMMAND_H__

#include "common.h"
#include "cpp_bindings.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

/* BSSID blacklist */
typedef struct {
    int num_bssid;                           // number of blacklisted BSSIDs
    mac_addr bssids[MAX_BLACKLIST_BSSID];    // blacklisted BSSIDs
} wifi_bssid_params;

class RoamCommand: public WifiVendorCommand
{
private:

public:
    RoamCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);
    virtual ~RoamCommand();

    virtual int create();
    virtual int requestResponse();
};

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
