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

#ifndef __WIFI_HAL_GSCAN_COMMAND_H__
#define __WIFI_HAL_GSCAN_COMMAND_H__

#include "common.h"
#include "cpp_bindings.h"
#ifdef __GNUC__
#define PRINTF_FORMAT(a,b) __attribute__ ((format (printf, (a), (b))))
#define STRUCT_PACKED __attribute__ ((packed))
#else
#define PRINTF_FORMAT(a,b)
#define STRUCT_PACKED
#endif
#include "vendor_definitions.h"
#include "gscan.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

typedef struct{
    u32 status;
    u32 num_channels;
    wifi_channel channels[];
} GScanGetValidChannelsRspParams;

typedef struct{
    wifi_gscan_capabilities capabilities;
} GScanGetCapabilitiesRspParams;

typedef struct{
    u8  more_data;
    u32 num_cached_results;
    u32 cachedResultsStartingIndex; /* Used in filling cached scan results */
    int lastProcessedScanId; /* Last scan id in gscan cached results block */
    u32 wifiScanResultsStartingIndex; /* For the lastProcessedScanId */
    u32 max;                /* max num of cached results specified by caller */
    wifi_cached_scan_results *cached_results;
} GScanGetCachedResultsRspParams;

typedef struct {
    int max_channels;
    wifi_channel *channels;
    int *number_channels;
} GScan_get_valid_channels_cb_data;

typedef enum{
    eGScanRspParamsInvalid = 0,
    eGScanGetValidChannelsRspParams,
    eGScanGetCapabilitiesRspParams,
    eGScanGetCachedResultsRspParams,
} eGScanRspRarams;

/* Response and Event Callbacks */
typedef struct {
    /* Various Events Callback */
    void (*on_hotlist_ap_found)(wifi_request_id id,
        unsigned num_results, wifi_scan_result *results);
    void (*on_hotlist_ap_lost)(wifi_request_id id,
        unsigned num_results, wifi_scan_result *results);
    void (*on_significant_change)(wifi_request_id id,
                unsigned num_results,
                wifi_significant_change_result **results);
    /* Reported when each probe response is received, if report_events
     * enabled in wifi_scan_cmd_params
     */
    void (*on_full_scan_result) (wifi_request_id id, wifi_scan_result *result,
                                                   unsigned buckets_scanned);
    /* Optional event - indicates progress of scanning statemachine */
    void (*on_scan_event) (wifi_request_id id, wifi_scan_event event);
    void (*on_hotlist_ssid_found)(wifi_request_id id,
            unsigned num_results, wifi_scan_result *results);
    void (*on_hotlist_ssid_lost)(wifi_request_id id,
            unsigned num_results, wifi_scan_result *results);
    void (*on_pno_network_found)(wifi_request_id id,
            unsigned num_results, wifi_scan_result *results);
    void (*on_passpoint_network_found)(wifi_request_id id,
                                       int net_id,
                                       wifi_scan_result *result,
                                       int anqp_len,
                                       byte *anqp
                                       );
} GScanCallbackHandler;

class GScanCommand: public WifiVendorCommand
{
private:
    GScanGetCachedResultsRspParams      *mGetCachedResultsRspParams;
    GScanCallbackHandler                mHandler;
    int                                 mRequestId;
    int                                 *mChannels;
    int                                 mMaxChannels;
    int                                 *mNumChannelsPtr;

public:
    GScanCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);
    virtual ~GScanCommand();

    /* This function implements creation of GSCAN specific Request
     * based on  the request type.
     */
    virtual int create();
    virtual int requestResponse();
    virtual int handleResponse(WifiEvent &reply);
    virtual void setMaxChannels(int max_channels);
    virtual void setChannels(int *channels);
    virtual void setNumChannelsPtr(int *num_channels);
    virtual int allocRspParams(eGScanRspRarams cmd);
    virtual void freeRspParams(eGScanRspRarams cmd);
    virtual wifi_error copyCachedScanResults(int *numResults,
                                             wifi_cached_scan_results *cached_results);
    virtual int gscan_get_cached_results(wifi_cached_scan_results *results,
                                         struct nlattr **tb_vendor);
    wifi_error validateGscanConfig(wifi_scan_cmd_params params);
    wifi_error validateSignificantChangeParams(
            wifi_significant_change_params params);
    virtual int allocCachedResultsTemp(int max,
                                       wifi_cached_scan_results *results);
};

#define GSCAN_BASE_PERIOD_MIN 1
#define GSCAN_MAX_AP_PER_SCAN_MIN 1
#define GSCAN_REPORT_THRESHOLD_MIN 1
#define GSCAN_NUM_BUCKETS_MIN 1
#define GSCAN_BUCKET_INDEX_MIN 0
#define GSCAN_REPORT_EVENT0 0
#define GSCAN_REPORT_EVENT1 1
#define GSCAN_REPORT_EVENT2 2
#define GSCAN_MIN_CHANNELS 0
#define GSCAN_ACTIVE_SCAN 0
#define GSCAN_PASSIVE_SCAN 1

#define BSSID_HOTLIST_NUM_AP_MIN 1

#define RSSI_SAMPLE_SIZE_MIN 1
#define LOSTAP_SAMPLE_SIZE_MIN 1
#define MIN_BREACHING_MIN 1
#define SIGNIFICANT_CHANGE_NUM_AP_MIN 1

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
