/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef __WIFI_HAL_GSCAN_EVENT_HANDLE_H__
#define __WIFI_HAL_GSCAN_EVENT_HANDLE_H__

#include "common.h"
#include "cpp_bindings.h"
#include "gscancommand.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

class GScanCommandEventHandler: public WifiVendorCommand
{
private:
    // TODO: derive 3 other command event handler classes from this base and separate
    // the data member vars
    wifi_scan_result *mHotlistApFoundResults;
    wifi_scan_result *mHotlistApLostResults;
    u32 mHotlistApFoundNumResults;
    u32 mHotlistApLostNumResults;
    bool mHotlistApFoundMoreData;
    bool mHotlistApLostMoreData;
    wifi_significant_change_result **mSignificantChangeResults;
    u32 mSignificantChangeNumResults;
    bool mSignificantChangeMoreData;
    GScanCallbackHandler mHandler;
    int mRequestId;
    u32 mHotlistSsidFoundNumResults;
    bool mHotlistSsidFoundMoreData;
    u32 mHotlistSsidLostNumResults;
    bool mHotlistSsidLostMoreData;
    wifi_scan_result *mHotlistSsidFoundResults;
    wifi_scan_result *mHotlistSsidLostResults;
    wifi_scan_result *mPnoNetworkFoundResults;
    u32 mPnoNetworkFoundNumResults;
    bool mPnoNetworkFoundMoreData;
    wifi_scan_result *mPasspointNetworkFoundResult;
    byte *mPasspointAnqp;
    int mPasspointAnqpLen;
    int mPasspointNetId;

    /* Needed because mSubcmd gets overwritten in
     * WifiVendorCommand::handleEvent()
     */
    u32 mSubCommandId;
    bool mEventHandlingEnabled;

public:
    GScanCommandEventHandler(wifi_handle handle, int id, u32 vendor_id,
                                    u32 subcmd, GScanCallbackHandler nHandler);
    virtual ~GScanCommandEventHandler();
    virtual int create();
    virtual int get_request_id();
    virtual void set_request_id(int request_id);
    virtual int handleEvent(WifiEvent &event);
    void enableEventHandling();
    void disableEventHandling();
    bool isEventHandlingEnabled();
    void setCallbackHandler(GScanCallbackHandler handler);
    wifi_error gscan_parse_hotlist_ap_results(
            u32 num_results,
            wifi_scan_result *results,
            u32 starting_index,
            struct nlattr **tb_vendor);
    wifi_error gscan_parse_hotlist_ssid_results(
            u32 num_results,
            wifi_scan_result *results,
            u32 starting_index,
            struct nlattr **tb_vendor);
    wifi_error gscan_parse_passpoint_network_result(
            struct nlattr **tb_vendor);
    wifi_error gscan_parse_pno_network_results(
            u32 numResults,
            wifi_scan_result *mPnoNetworkFoundResults,
            u32 startingIndex,
            struct nlattr **tbVendor);
};

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
