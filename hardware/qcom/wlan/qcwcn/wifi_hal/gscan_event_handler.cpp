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

#define LOG_TAG  "WifiHAL"

#include <utils/Log.h>
#include "gscan_event_handler.h"
#include "vendor_definitions.h"

/* This function implements creation of Vendor command event handler. */
int GScanCommandEventHandler::create() {
    int ret = mMsg.create(NL80211_CMD_VENDOR, 0, 0);
    if (ret < 0) {
        return ret;
    }

    /* Insert the oui in the msg */
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_ID, mVendor_id);
    if (ret < 0)
        goto out;

    /* Insert the subcmd in the msg */
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_SUBCMD, mSubcmd);
    if (ret < 0)
        goto out;
out:
    return ret;
}

int GScanCommandEventHandler::get_request_id()
{
    return mRequestId;
}

void GScanCommandEventHandler::set_request_id(int request_id)
{
    mRequestId = request_id;
}

void GScanCommandEventHandler::enableEventHandling()
{
    mEventHandlingEnabled = true;
}

void GScanCommandEventHandler::disableEventHandling()
{
    mEventHandlingEnabled = false;
}

bool GScanCommandEventHandler::isEventHandlingEnabled()
{
    return mEventHandlingEnabled;
}

void GScanCommandEventHandler::setCallbackHandler(GScanCallbackHandler handler)
{
    mHandler = handler;
}

GScanCommandEventHandler::GScanCommandEventHandler(wifi_handle handle, int id,
                                                u32 vendor_id,
                                                u32 subcmd,
                                                GScanCallbackHandler handler)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    int ret = 0;
    mRequestId = id;
    mHandler = handler;
    mSubCommandId = subcmd;
    mHotlistApFoundResults = NULL;
    mHotlistApFoundNumResults = 0;
    mHotlistApFoundMoreData = false;
    mHotlistApLostResults = NULL;
    mHotlistApLostNumResults = 0;
    mHotlistApLostMoreData = false;
    mSignificantChangeResults = NULL;
    mSignificantChangeNumResults = 0;
    mSignificantChangeMoreData = false;
    mHotlistSsidFoundNumResults = 0;
    mHotlistSsidFoundMoreData = false;
    mHotlistSsidLostNumResults = 0;
    mHotlistSsidLostMoreData = false;
    mHotlistSsidFoundResults = NULL;
    mHotlistSsidLostResults = NULL;
    mPnoNetworkFoundResults = NULL;
    mPnoNetworkFoundNumResults = 0;
    mPnoNetworkFoundMoreData = false;
    mPasspointNetworkFoundResult = NULL;
    mPasspointAnqp = NULL;
    mPasspointAnqpLen = 0;
    mPasspointNetId = -1;
    mEventHandlingEnabled = false;

    switch(mSubCommandId)
    {
        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_START:
        {
            /* Register handlers for northbound asychronous scan events. */
            ret = registerVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_RESULTS_AVAILABLE) ||
                  registerVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_FULL_SCAN_RESULT) ||
                  registerVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_EVENT);
            if (ret)
                ALOGE("%s: Error in registering handler for "
                    "GSCAN_START. \n", __FUNCTION__);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SET_SIGNIFICANT_CHANGE:
        {
            ret = registerVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_SIGNIFICANT_CHANGE);
            if (ret)
                ALOGE("%s: Error in registering handler for "
                    "GSCAN_SIGNIFICANT_CHANGE. \n", __FUNCTION__);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SET_BSSID_HOTLIST:
        {
            ret = registerVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_FOUND);
            if (ret)
                ALOGE("%s: Error in registering handler for"
                    " GSCAN_HOTLIST_AP_FOUND. \n", __FUNCTION__);

            ret = registerVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_LOST);
            if (ret)
                ALOGE("%s: Error in registering handler for"
                    " GSCAN_HOTLIST_AP_LOST. \n", __FUNCTION__);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_PNO_SET_LIST:
        {
            ret = registerVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_PNO_NETWORK_FOUND);
            if (ret)
                ALOGE("%s: Error in registering handler for"
                    " PNO_NETWORK_FOUND. \n", __FUNCTION__);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_PNO_SET_PASSPOINT_LIST:
        {
            ret = registerVendorHandler(mVendor_id,
                QCA_NL80211_VENDOR_SUBCMD_PNO_PASSPOINT_NETWORK_FOUND);
            if (ret)
                ALOGE("%s: Error in registering handler for"
                    " PNO_PASSPOINT_NETWORK_FOUND. \n", __FUNCTION__);
        }
        break;
    }
}

GScanCommandEventHandler::~GScanCommandEventHandler()
{
    switch(mSubCommandId)
    {
        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_START:
        {
            /* Unregister event handlers. */
            unregisterVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_RESULTS_AVAILABLE);
            unregisterVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_FULL_SCAN_RESULT);
            unregisterVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_EVENT);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SET_SIGNIFICANT_CHANGE:
        {
            unregisterVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_SIGNIFICANT_CHANGE);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SET_BSSID_HOTLIST:
        {
            unregisterVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_FOUND);
            unregisterVendorHandler(mVendor_id,
                    QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_LOST);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_PNO_SET_LIST:
        {
            unregisterVendorHandler(mVendor_id,
                QCA_NL80211_VENDOR_SUBCMD_PNO_NETWORK_FOUND);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_PNO_SET_PASSPOINT_LIST:
        {
            unregisterVendorHandler(mVendor_id,
                QCA_NL80211_VENDOR_SUBCMD_PNO_PASSPOINT_NETWORK_FOUND);
        }
        break;
    }
}

wifi_error GScanCommandEventHandler::gscan_parse_hotlist_ap_results(
                                            u32 num_results,
                                            wifi_scan_result *results,
                                            u32 starting_index,
                                            struct nlattr **tb_vendor)
{
    u32 i = starting_index;
    struct nlattr *scanResultsInfo;
    int rem = 0;
    u32 len = 0;
    ALOGV("gscan_parse_hotlist_ap_results: starting counter: %d", i);

    for (scanResultsInfo = (struct nlattr *) nla_data(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]),
            rem = nla_len(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST
            ]);
                nla_ok(scanResultsInfo, rem);
                scanResultsInfo = nla_next(scanResultsInfo, &(rem)))
    {
        struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
        (struct nlattr *) nla_data(scanResultsInfo),
                nla_len(scanResultsInfo), NULL);

        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                ])
        {
            ALOGE("gscan_parse_hotlist_ap_results: "
                "RESULTS_SCAN_RESULT_TIME_STAMP not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].ts =
            nla_get_u64(
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                ]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID
                ])
        {
            ALOGE("gscan_parse_hotlist_ap_results: "
                "RESULTS_SCAN_RESULT_SSID not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        len = nla_len(tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]);
        len =
            sizeof(results->ssid) <= len ? sizeof(results->ssid) : len;
        memcpy((void *)&results[i].ssid,
            nla_data(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]), len);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID
                ])
        {
            ALOGE("gscan_parse_hotlist_ap_results: "
                "RESULTS_SCAN_RESULT_BSSID not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        len = nla_len(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]);
        len =
            sizeof(results->bssid) <= len ? sizeof(results->bssid) : len;
        memcpy(&results[i].bssid,
            nla_data(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]), len);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL
                ])
        {
            ALOGE("gscan_parse_hotlist_ap_results: "
                "RESULTS_SCAN_RESULT_CHANNEL not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].channel =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI
                ])
        {
            ALOGE("gscan_parse_hotlist_ap_results: "
                "RESULTS_SCAN_RESULT_RSSI not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rssi =
            get_s32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT
                ])
        {
            ALOGE("gscan_parse_hotlist_ap_results: "
                "RESULTS_SCAN_RESULT_RTT not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rtt =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD
            ])
        {
            ALOGE("gscan_parse_hotlist_ap_results: "
                "RESULTS_SCAN_RESULT_RTT_SD not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rtt_sd =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD]);

        ALOGV("gscan_parse_hotlist_ap_results: ts %" PRId64 " SSID  %s "
              "BSSID: %02x:%02x:%02x:%02x:%02x:%02x channel %d rssi %d "
              "rtt %" PRId64" rtt_sd %" PRId64,
              results[i].ts, results[i].ssid,
              results[i].bssid[0], results[i].bssid[1], results[i].bssid[2],
              results[i].bssid[3], results[i].bssid[4], results[i].bssid[5],
              results[i].channel, results[i].rssi, results[i].rtt,
              results[i].rtt_sd);
        /* Increment loop index for next record */
        i++;
    }
    return WIFI_SUCCESS;
}

static wifi_error gscan_get_significant_change_results(u32 num_results,
                                    wifi_significant_change_result **results,
                                    u32 starting_index,
                                    struct nlattr **tb_vendor)
{
    u32 i = starting_index;
    int j;
    int rem = 0;
    u32 len = 0;
    char rssi_buf[1024]; //TODO: sizeof buf
    int rem_size;
    struct nlattr *scanResultsInfo;

    for (scanResultsInfo = (struct nlattr *) nla_data(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]),
            rem = nla_len(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]);
        nla_ok(scanResultsInfo, rem);
        scanResultsInfo = nla_next(scanResultsInfo, &(rem)))
    {
        struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
            (struct nlattr *) nla_data(scanResultsInfo),
                nla_len(scanResultsInfo), NULL);
        if (!
            tb2[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_BSSID
            ])
        {
            ALOGE("gscan_get_significant_change_results: "
                "SIGNIFICANT_CHANGE_RESULT_BSSID not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        len = nla_len(
            tb2[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_BSSID]
            );
        len =
            sizeof(results[i]->bssid) <= len ? sizeof(results[i]->bssid) : len;
        memcpy(&results[i]->bssid[0],
            nla_data(
            tb2[
        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_BSSID]),
            len);

        if (!
            tb2[
        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_CHANNEL
                ])
        {
            ALOGE("gscan_get_significant_change_results: "
                "SIGNIFICANT_CHANGE_RESULT_CHANNEL not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i]->channel =
            nla_get_u32(
            tb2[
        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_CHANNEL]);

        if (!
            tb2[
        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_NUM_RSSI
            ])
        {
            ALOGE("gscan_get_significant_change_results: "
                "SIGNIFICANT_CHANGE_RESULT_NUM_RSSI not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i]->num_rssi =
            nla_get_u32(
            tb2[
        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_NUM_RSSI]);

        if (!
            tb2[
        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_RSSI_LIST
            ])
        {
            ALOGE("gscan_get_significant_change_results: "
                "SIGNIFICANT_CHANGE_RESULT_RSSI_LIST not found");
            return WIFI_ERROR_INVALID_ARGS;
        }

        memcpy(&(results[i]->rssi[0]),
            nla_data(
            tb2[
        QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_RSSI_LIST]
            ), results[i]->num_rssi * sizeof(wifi_rssi));

        ALOGV("significant_change_result:%d, BSSID:"
            "%02x:%02x:%02x:%02x:%02x:%02x channel:%d  num_rssi:%d ",
            i, results[i]->bssid[0], results[i]->bssid[1], results[i]->bssid[2],
            results[i]->bssid[3], results[i]->bssid[4], results[i]->bssid[5],
            results[i]->channel, results[i]->num_rssi);

        rem_size = sizeof(rssi_buf);
        char *dst = rssi_buf;
        for (j = 0; j < results[i]->num_rssi && rem_size > 0; j++) {
            len = snprintf(dst, rem_size, "rssi[%d]:%d, ", j, results[i]->rssi[j]);
            dst += len;
            rem_size -= len;
        }
        ALOGV("RSSI LIST: %s", rssi_buf);

        /* Increment loop index to prase next record. */
        i++;
    }
    return WIFI_SUCCESS;
}

wifi_error GScanCommandEventHandler::gscan_parse_hotlist_ssid_results(
                                            u32 num_results,
                                            wifi_scan_result *results,
                                            u32 starting_index,
                                            struct nlattr **tb_vendor)
{
    u32 i = starting_index;
    struct nlattr *scanResultsInfo;
    int rem = 0;
    u32 len = 0;

    for (scanResultsInfo = (struct nlattr *) nla_data(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]),
            rem = nla_len(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST
            ]);
                nla_ok(scanResultsInfo, rem);
                scanResultsInfo = nla_next(scanResultsInfo, &(rem)))
    {
        struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
        (struct nlattr *) nla_data(scanResultsInfo),
                nla_len(scanResultsInfo), NULL);

        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                ])
        {
            ALOGE("gscan_parse_hotlist_ssid_results: "
                "RESULTS_SCAN_RESULT_TIME_STAMP not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].ts =
            nla_get_u64(
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                ]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID
                ])
        {
            ALOGE("gscan_parse_hotlist_ssid_results: "
                "RESULTS_SCAN_RESULT_SSID not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        len = nla_len(tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]);
        len =
            sizeof(results->ssid) <= len ? sizeof(results->ssid) : len;
        memcpy((void *)&results[i].ssid,
            nla_data(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]), len);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID
                ])
        {
            ALOGE("gscan_parse_hotlist_ssid_results: "
                "RESULTS_SCAN_RESULT_BSSID not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        len = nla_len(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]);
        len =
            sizeof(results->bssid) <= len ? sizeof(results->bssid) : len;
        memcpy(&results[i].bssid,
            nla_data(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]), len);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL
                ])
        {
            ALOGE("gscan_parse_hotlist_ssid_results: "
                "RESULTS_SCAN_RESULT_CHANNEL not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].channel =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI
                ])
        {
            ALOGE("gscan_parse_hotlist_ssid_results: "
                "RESULTS_SCAN_RESULT_RSSI not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rssi =
            get_s32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT
                ])
        {
            ALOGE("gscan_parse_hotlist_ssid_results: "
                "RESULTS_SCAN_RESULT_RTT not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rtt =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD
            ])
        {
            ALOGE("gscan_parse_hotlist_ssid_results: "
                "RESULTS_SCAN_RESULT_RTT_SD not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rtt_sd =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD]);

        ALOGV("gscan_parse_hotlist_ssid_results: ts %" PRId64 " SSID  %s "
              "BSSID: %02x:%02x:%02x:%02x:%02x:%02x channel %d rssi %d "
              "rtt %" PRId64 " rtt_sd %" PRId64,
              results[i].ts, results[i].ssid,
              results[i].bssid[0], results[i].bssid[1], results[i].bssid[2],
              results[i].bssid[3], results[i].bssid[4], results[i].bssid[5],
              results[i].channel, results[i].rssi, results[i].rtt,
              results[i].rtt_sd);
        /* Increment loop index for next record */
        i++;
    }
    return WIFI_SUCCESS;
}

wifi_error GScanCommandEventHandler::gscan_parse_passpoint_network_result(
            struct nlattr **tb_vendor)
{
    struct nlattr *scanResultsInfo, *wifiScanResultsInfo;
    u32 resultsBufSize = 0;
    u32 len = 0;
    int rem = 0;

    for (scanResultsInfo = (struct nlattr *) nla_data(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_RESULT_LIST]),
            rem = nla_len(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_RESULT_LIST
            ]);
                nla_ok(scanResultsInfo, rem);
                scanResultsInfo = nla_next(scanResultsInfo, &(rem)))
    {
        struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
        (struct nlattr *) nla_data(scanResultsInfo),
                nla_len(scanResultsInfo), NULL);

        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_ID
                ])
        {
            ALOGE("%s: GSCAN_PNO_RESULTS_PASSPOINT_MATCH_ID not found",
                  __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        mPasspointNetId =
            nla_get_u32(
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_ID
                ]);

        for (wifiScanResultsInfo = (struct nlattr *) nla_data(tb2[
             QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]),
             rem = nla_len(tb2[
             QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST
             ]);
             nla_ok(wifiScanResultsInfo, rem);
             wifiScanResultsInfo = nla_next(wifiScanResultsInfo, &(rem)))
        {
            struct nlattr *tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
            nla_parse(tb3, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
            (struct nlattr *) nla_data(wifiScanResultsInfo),
                     nla_len(wifiScanResultsInfo), NULL);

            resultsBufSize = sizeof(wifi_scan_result);
            if (!
                tb3[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_LENGTH
                ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_IE_LENGTH not found", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            resultsBufSize +=
                nla_get_u32(
                tb3[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_LENGTH]);

            /* Allocate the appropriate memory for mPasspointNetworkFoundResult */
            mPasspointNetworkFoundResult = (wifi_scan_result *)
                                malloc (resultsBufSize);

            if (!mPasspointNetworkFoundResult) {
                ALOGE("%s: Failed to alloc memory for result struct. Exit.\n",
                    __FUNCTION__);
                return WIFI_ERROR_OUT_OF_MEMORY;
            }
            memset(mPasspointNetworkFoundResult, 0, resultsBufSize);

            mPasspointNetworkFoundResult->ie_length =
                nla_get_u32(
                tb3[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_LENGTH]);

            if (!
                tb3[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_TIME_STAMP not found",
                      __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            mPasspointNetworkFoundResult->ts =
                nla_get_u64(
                tb3[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                    ]);
            if (!
                tb3[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_SSID not found", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
             len = nla_len(tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]);
             len =
                 sizeof(mPasspointNetworkFoundResult->ssid) <= len ?
                 sizeof(mPasspointNetworkFoundResult->ssid) : len;
             memcpy((void *)&(mPasspointNetworkFoundResult->ssid[0]),
                 nla_data(
                 tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]), len);
             if (!
                 tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID
                     ])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_BSSID not found", __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             len = nla_len(
                 tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]);
             len =
                 sizeof(mPasspointNetworkFoundResult->bssid) <= len ?
                 sizeof(mPasspointNetworkFoundResult->bssid) : len;
             memcpy(&(mPasspointNetworkFoundResult->bssid[0]),
                 nla_data(
                 tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]),
                 len);
             if (!
                 tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL
                     ])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_CHANNEL not found", __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             mPasspointNetworkFoundResult->channel =
                 nla_get_u32(
                 tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL]);
             if (!
                 tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI
                     ])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_RSSI not found", __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             mPasspointNetworkFoundResult->rssi =
                 get_s32(
                 tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI]);
             if (!
                 tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT
                     ])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_RTT not found", __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             mPasspointNetworkFoundResult->rtt =
                 nla_get_u32(
                 tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT]);
             if (!
                 tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD
                 ])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_RTT_SD not found", __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             mPasspointNetworkFoundResult->rtt_sd =
                 nla_get_u32(
                 tb3[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD]);

             if (!
                 tb3[
                 QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BEACON_PERIOD])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_BEACON_PERIOD not found",
                     __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             mPasspointNetworkFoundResult->beacon_period =
                 nla_get_u16(
                 tb3[
                 QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BEACON_PERIOD]);

             if (!
                 tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CAPABILITY
                     ])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_CAPABILITY not found", __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             mPasspointNetworkFoundResult->capability =
                 nla_get_u16(
                 tb3[
                 QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CAPABILITY]);

             if (!
                 tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_DATA
                 ])
             {
                 ALOGE("%s: RESULTS_SCAN_RESULT_IE_DATA not found", __FUNCTION__);
                 return WIFI_ERROR_INVALID_ARGS;
             }
             memcpy(&(mPasspointNetworkFoundResult->ie_data[0]),
                 nla_data(tb3[
                     QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_DATA]),
                 mPasspointNetworkFoundResult->ie_length);

             ALOGV("%s: ts: %" PRId64 " SSID: %s "
                   "BSSID: %02x:%02x:%02x:%02x:%02x:%02x  channel: %d  rssi: %d"
                   " rtt: % " PRId64 " rtt_sd  %" PRId64 " ie_length  %u ",
                   __FUNCTION__, mPasspointNetworkFoundResult->ts,
                   mPasspointNetworkFoundResult->ssid,
                   mPasspointNetworkFoundResult->bssid[0],
                   mPasspointNetworkFoundResult->bssid[1],
                   mPasspointNetworkFoundResult->bssid[2],
                   mPasspointNetworkFoundResult->bssid[3],
                   mPasspointNetworkFoundResult->bssid[4],
                   mPasspointNetworkFoundResult->bssid[5],
                   mPasspointNetworkFoundResult->channel,
                   mPasspointNetworkFoundResult->rssi,
                   mPasspointNetworkFoundResult->rtt,
                   mPasspointNetworkFoundResult->rtt_sd,
                   mPasspointNetworkFoundResult->ie_length);
             ALOGV("%s: ie_data: ", __FUNCTION__);
             hexdump(mPasspointNetworkFoundResult->ie_data,
                     mPasspointNetworkFoundResult->ie_length);
        }

        if (!
           tb2[
               QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_ANQP_LEN
           ])
        {
            ALOGE("%s:PNO_RESULTS_PASSPOINT_MATCH_ANQP_LEN not found",
                  __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        mPasspointAnqpLen =
            nla_get_u32(
                tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_ANQP_LEN]);

        if (!mPasspointAnqpLen)
        {
            break;
        }
        mPasspointAnqp = (u8 *) malloc (mPasspointAnqpLen);
        if (!mPasspointAnqp) {
            ALOGE("%s: Failed to alloc memory for result struct. Exit.\n",
                  __FUNCTION__);
            return WIFI_ERROR_OUT_OF_MEMORY;
        }

        memset(mPasspointAnqp, 0, mPasspointAnqpLen);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_ANQP
            ])
            {
            ALOGE("%s: RESULTS_PASSPOINT_MATCH_ANQP not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        memcpy(&(mPasspointAnqp[0]),
               nla_data(tb2[
                 QCA_WLAN_VENDOR_ATTR_GSCAN_PNO_RESULTS_PASSPOINT_MATCH_ANQP]),
               mPasspointAnqpLen);

        ALOGV("%s: ANQP LEN:%d, ANQP IE:", __FUNCTION__, mPasspointAnqpLen);
        hexdump((char*)mPasspointAnqp, mPasspointAnqpLen);

        /* expecting only one result break out after the first loop */
        break;
    }
    return WIFI_SUCCESS;
}

wifi_error GScanCommandEventHandler::gscan_parse_pno_network_results(
                                            u32 num_results,
                                            wifi_scan_result *results,
                                            u32 starting_index,
                                            struct nlattr **tb_vendor)
{
    u32 i = starting_index;
    struct nlattr *scanResultsInfo;
    int rem = 0;
    u32 len = 0;

    for (scanResultsInfo = (struct nlattr *) nla_data(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]),
            rem = nla_len(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST
            ]);
                nla_ok(scanResultsInfo, rem);
                scanResultsInfo = nla_next(scanResultsInfo, &(rem)))
    {
        struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
        (struct nlattr *) nla_data(scanResultsInfo),
                nla_len(scanResultsInfo), NULL);

        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_TIME_STAMP not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].ts =
            nla_get_u64(
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                ]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID
                ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_SSID not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        len = nla_len(tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]);
        len =
            sizeof(results->ssid) <= len ? sizeof(results->ssid) : len;
        memcpy((void *)&results[i].ssid,
            nla_data(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]), len);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID
                ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_BSSID not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        len = nla_len(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]);
        len =
            sizeof(results->bssid) <= len ? sizeof(results->bssid) : len;
        memcpy(&results[i].bssid,
            nla_data(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]), len);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL
                ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_CHANNEL not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].channel =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI
                ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_RSSI not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rssi =
            get_s32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT
                ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_RTT not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rtt =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT]);
        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD
            ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_RTT_SD not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].rtt_sd =
            nla_get_u32(
            tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD]);

        if (!
            tb2[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BEACON_PERIOD])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_BEACON_PERIOD not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].beacon_period =
            nla_get_u16(
            tb2[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BEACON_PERIOD]);

        if (!
            tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CAPABILITY
                ])
        {
            ALOGE("gscan_parse_pno_network_results: "
                "RESULTS_SCAN_RESULT_CAPABILITY not found");
            return WIFI_ERROR_INVALID_ARGS;
        }
        results[i].capability =
            nla_get_u16(
            tb2[
            QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CAPABILITY]);

        ALOGV("gscan_parse_pno_network_results: ts %" PRId64 " SSID  %s "
              "BSSID: %02x:%02x:%02x:%02x:%02x:%02x channel %d rssi %d "
              "rtt %" PRId64 " rtt_sd %" PRId64,
              results[i].ts, results[i].ssid,
              results[i].bssid[0], results[i].bssid[1], results[i].bssid[2],
              results[i].bssid[3], results[i].bssid[4], results[i].bssid[5],
              results[i].channel, results[i].rssi, results[i].rtt,
              results[i].rtt_sd);
        /* Increment loop index for next record */
        i++;
    }
    return WIFI_SUCCESS;
}

/* This function will be the main handler for incoming (from driver)
 * GScan_SUBCMD. Calls the appropriate callback handler after parsing
 * the vendor data.
 */
int GScanCommandEventHandler::handleEvent(WifiEvent &event)
{
    unsigned i=0;
    int ret = WIFI_SUCCESS;
    wifi_scan_result *result = NULL;
    struct nlattr *tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];

    if (mEventHandlingEnabled == false)
    {
        ALOGV("%s:Discarding event: %d",
              __FUNCTION__, mSubcmd);
        return NL_SKIP;
    }

    WifiVendorCommand::handleEvent(event);

    nla_parse(tbVendor, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
                        (struct nlattr *)mVendorData,
                        mDataLen, NULL);

    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_FULL_SCAN_RESULT:
        {
            wifi_request_id reqId;
            u32 len = 0;
            u32 resultsBufSize = 0;
            u32 lengthOfInfoElements = 0;
            u32 buckets_scanned = 0;

            ALOGV("Event QCA_NL80211_VENDOR_SUBCMD_GSCAN_FULL_SCAN_RESULT "
                "received.");
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID])
            {
                ALOGE("%s: ATTR_GSCAN_RESULTS_REQUEST_ID not found. Exit.",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            reqId = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                    );
            /* If event has a different request_id, ignore that and use the
             *  request_id value which we're maintaining.
             */
            if (reqId != mRequestId) {
#ifdef QC_HAL_DEBUG
                ALOGE("%s: Event has Req. ID:%d <> Ours:%d, continue...",
                    __FUNCTION__, reqId, mRequestId);
#endif
                reqId = mRequestId;
            }

            /* Parse and extract the results. */
            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_LENGTH
                ])
            {
                ALOGE("%s:RESULTS_SCAN_RESULT_IE_LENGTH not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            lengthOfInfoElements =
                nla_get_u32(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_LENGTH]);

            ALOGV("%s: RESULTS_SCAN_RESULT_IE_LENGTH =%d",
                __FUNCTION__, lengthOfInfoElements);

            resultsBufSize =
                lengthOfInfoElements + sizeof(wifi_scan_result);
            result = (wifi_scan_result *) malloc (resultsBufSize);
            if (!result) {
                ALOGE("%s: Failed to alloc memory for result struct. Exit.\n",
                    __FUNCTION__);
                ret = WIFI_ERROR_OUT_OF_MEMORY;
                break;
            }
            memset(result, 0, resultsBufSize);

            result->ie_length = lengthOfInfoElements;

            /* Extract and fill out the wifi_scan_result struct. */
            if (!
            tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_TIME_STAMP not found",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            result->ts =
                nla_get_u64(
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_TIME_STAMP
                    ]);

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_SSID not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            len = nla_len(tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]);
            len =
                sizeof(result->ssid) <= len ? sizeof(result->ssid) : len;
            memcpy((void *)&result->ssid,
                nla_data(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_SSID]), len);

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_BSSID not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            len = nla_len(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]);
            len =
                sizeof(result->bssid) <= len ? sizeof(result->bssid) : len;
            memcpy(&result->bssid,
                nla_data(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BSSID]), len);

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_CHANNEL not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            result->channel =
                nla_get_u32(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CHANNEL]);

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_RSSI not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            result->rssi =
                get_s32(
                tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RSSI]
                );

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_RTT not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            result->rtt =
                nla_get_u32(
                tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT]);

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD
                ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_RTT_SD not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            result->rtt_sd =
                nla_get_u32(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_RTT_SD]);

            if (!
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BEACON_PERIOD])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_BEACON_PERIOD not found",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            result->beacon_period =
                nla_get_u16(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_BEACON_PERIOD]);

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CAPABILITY
                    ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_CAPABILITY not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            result->capability =
                nla_get_u16(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_CAPABILITY]);

            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_DATA
                ])
            {
                ALOGE("%s: RESULTS_SCAN_RESULT_IE_DATA not found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            memcpy(&(result->ie_data[0]),
                nla_data(tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_IE_DATA]),
                lengthOfInfoElements);
            if (!
                tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_BUCKETS_SCANNED
                    ])
            {
                ALOGD("%s: RESULTS_BUCKETS_SCANNED not found", __FUNCTION__);
            } else {
                buckets_scanned = get_u32(tbVendor[
                           QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_BUCKETS_SCANNED]);
            }
#ifdef QC_HAL_DEBUG
            ALOGD("handleEvent:FULL_SCAN_RESULTS: ts  %" PRId64, result->ts);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: SSID  %s ", result->ssid) ;
            ALOGD("handleEvent:FULL_SCAN_RESULTS: "
                "BSSID: %02x:%02x:%02x:%02x:%02x:%02x \n",
                result->bssid[0], result->bssid[1], result->bssid[2],
                result->bssid[3], result->bssid[4], result->bssid[5]);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: channel %d ",
                result->channel);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: rssi  %d ", result->rssi);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: rtt  %" PRId64, result->rtt);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: rtt_sd  %" PRId64,
                result->rtt_sd);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: beacon period  %d ",
                result->beacon_period);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: capability  %d ",
                result->capability);
            ALOGD("handleEvent:FULL_SCAN_RESULTS: IE length  %d ",
                result->ie_length);

            ALOGD("%s: Invoking the callback. \n", __FUNCTION__);
#endif
            if (mHandler.on_full_scan_result) {
                (*mHandler.on_full_scan_result)(reqId, result, buckets_scanned);
                /* Reset flag and num counter. */
                free(result);
                result = NULL;
            }
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_RESULTS_AVAILABLE:
        {
            wifi_request_id id;

#ifdef QC_HAL_DEBUG
            ALOGV("Event "
                    "QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_RESULTS_AVAILABLE "
                    "received.");
#endif

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]) {
                ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID"
                        "not found. Exit", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            id = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                             );
            /* If this is not for us, then ignore it. */
            if (id != mRequestId) {
                ALOGE("%s: Event has Req. ID:%d <> ours:%d",
                        __FUNCTION__, id, mRequestId);
                break;
            }

            /* Invoke the callback func to report the number of results. */
            ALOGV("%s: Calling on_scan_event handler", __FUNCTION__);
            (*mHandler.on_scan_event)(id, WIFI_SCAN_THRESHOLD_NUM_SCANS);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_FOUND:
        {
            wifi_request_id id;
            u32 resultsBufSize = 0;
            u32 numResults = 0;
            u32 startingIndex, sizeOfObtainedResults;

            id = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                    );
            /* If this is not for us, just ignore it. */
            if (id != mRequestId) {
                ALOGE("%s: Event has Req. ID:%d <> ours:%d",
                    __FUNCTION__, id, mRequestId);
                break;
            }
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE]) {
                ALOGE("%s: GSCAN_RESULTS_NUM_RESULTS_AVAILABLE not found",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            numResults = nla_get_u32(tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE]);
            ALOGV("%s: number of results:%d", __FUNCTION__, numResults);

            /* Get the memory size of previous fragments, if any. */
            sizeOfObtainedResults = mHotlistApFoundNumResults *
                          sizeof(wifi_scan_result);

            mHotlistApFoundNumResults += numResults;
            resultsBufSize += mHotlistApFoundNumResults *
                                            sizeof(wifi_scan_result);

            /* Check if this chunck of scan results is a continuation of
             * a previous one.
             */
            if (mHotlistApFoundMoreData) {
                mHotlistApFoundResults = (wifi_scan_result *)
                            realloc (mHotlistApFoundResults, resultsBufSize);
            } else {
                mHotlistApFoundResults = (wifi_scan_result *)
                            malloc (resultsBufSize);
            }

            if (!mHotlistApFoundResults) {
                ALOGE("%s: Failed to alloc memory for results array. Exit.\n",
                    __FUNCTION__);
                ret = WIFI_ERROR_OUT_OF_MEMORY;
                break;
            }
            /* Initialize the newly allocated memory area with 0. */
            memset((u8 *)mHotlistApFoundResults + sizeOfObtainedResults, 0,
                    resultsBufSize - sizeOfObtainedResults);

            ALOGV("%s: Num of AP FOUND results = %d. \n", __FUNCTION__,
                                            mHotlistApFoundNumResults);

            /* To support fragmentation from firmware, monitor the
             * MORE_DATA flag and cache results until MORE_DATA = 0.
             * Only then we can pass on the results to framework through
             * the callback function.
             */
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]) {
                ALOGE("%s: GSCAN_RESULTS_NUM_RESULTS_MORE_DATA not"
                    " found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            } else {
                mHotlistApFoundMoreData = nla_get_u8(
                    tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]);
                ALOGE("%s: More data = %d. \n",
                    __FUNCTION__, mHotlistApFoundMoreData);
            }

            ALOGV("%s: Extract hotlist_ap_found results.\n", __FUNCTION__);
            startingIndex = mHotlistApFoundNumResults - numResults;
            ALOGV("%s: starting_index:%d",
                __FUNCTION__, startingIndex);
            ret = gscan_parse_hotlist_ap_results(numResults,
                                                mHotlistApFoundResults,
                                                startingIndex,
                                                tbVendor);
            /* If a parsing error occurred, exit and proceed for cleanup. */
            if (ret)
                break;
            /* Send the results if no more result data fragments are expected */
            if (!mHotlistApFoundMoreData) {
                (*mHandler.on_hotlist_ap_found)(id,
                                                mHotlistApFoundNumResults,
                                                mHotlistApFoundResults);
                /* Reset flag and num counter. */
                free(mHotlistApFoundResults);
                mHotlistApFoundResults = NULL;
                mHotlistApFoundMoreData = false;
                mHotlistApFoundNumResults = 0;
            }
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_LOST:
        {
            wifi_request_id id;
            u32 resultsBufSize = 0;
            u32 numResults = 0;
            u32 startingIndex, sizeOfObtainedResults;

            id = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                    );
            /* If this is not for us, just ignore it. */
            if (id != mRequestId) {
                ALOGE("%s: Event has Req. ID:%d <> ours:%d",
                    __FUNCTION__, id, mRequestId);
                break;
            }
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE]) {
                ALOGE("%s: GSCAN_RESULTS_NUM_RESULTS_AVAILABLE not found",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            numResults = nla_get_u32(tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE]);
            ALOGV("%s: number of results:%d", __FUNCTION__, numResults);

            /* Get the memory size of previous fragments, if any. */
            sizeOfObtainedResults = mHotlistApLostNumResults *
                          sizeof(wifi_scan_result);

            mHotlistApLostNumResults += numResults;
            resultsBufSize += mHotlistApLostNumResults *
                                            sizeof(wifi_scan_result);

            /* Check if this chunck of scan results is a continuation of
             * a previous one.
             */
            if (mHotlistApLostMoreData) {
                mHotlistApLostResults = (wifi_scan_result *)
                            realloc (mHotlistApLostResults, resultsBufSize);
            } else {
                mHotlistApLostResults = (wifi_scan_result *)
                            malloc (resultsBufSize);
            }

            if (!mHotlistApLostResults) {
                ALOGE("%s: Failed to alloc memory for results array. Exit.\n",
                    __FUNCTION__);
                ret = WIFI_ERROR_OUT_OF_MEMORY;
                break;
            }
            /* Initialize the newly allocated memory area with 0. */
            memset((u8 *)mHotlistApLostResults + sizeOfObtainedResults, 0,
                    resultsBufSize - sizeOfObtainedResults);

            ALOGV("%s: Num of AP Lost results = %d. \n", __FUNCTION__,
                                            mHotlistApLostNumResults);

            /* To support fragmentation from firmware, monitor the
             * MORE_DATA flag and cache results until MORE_DATA = 0.
             * Only then we can pass on the results to framework through
             * the callback function.
             */
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]) {
                ALOGE("%s: GSCAN_RESULTS_NUM_RESULTS_MORE_DATA not"
                    " found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            } else {
                mHotlistApLostMoreData = nla_get_u8(
                    tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]);
                ALOGV("%s: More data = %d. \n",
                    __FUNCTION__, mHotlistApLostMoreData);
            }

            ALOGV("%s: Extract hotlist_ap_Lost results.\n", __FUNCTION__);
            startingIndex = mHotlistApLostNumResults - numResults;
            ALOGV("%s: starting_index:%d",
                __FUNCTION__, startingIndex);
            ret = gscan_parse_hotlist_ap_results(numResults,
                                                mHotlistApLostResults,
                                                startingIndex,
                                                tbVendor);
            /* If a parsing error occurred, exit and proceed for cleanup. */
            if (ret)
                break;
            /* Send the results if no more result data fragments are expected */
            if (!mHotlistApLostMoreData) {
                (*mHandler.on_hotlist_ap_lost)(id,
                                               mHotlistApLostNumResults,
                                               mHotlistApLostResults);
                /* Reset flag and num counter. */
                free(mHotlistApLostResults);
                mHotlistApLostResults = NULL;
                mHotlistApLostMoreData = false;
                mHotlistApLostNumResults = 0;
            }
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SIGNIFICANT_CHANGE:
        {
            wifi_request_id reqId;
            u32 numResults = 0, sizeOfObtainedResults;
            u32 startingIndex, index = 0;
            struct nlattr *scanResultsInfo;
            int rem = 0;

            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID])
            {
                ALOGE("%s: ATTR_GSCAN_RESULTS_REQUEST_ID not found. Exit.",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            reqId = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                    );
            /* If this is not for us, just ignore it. */
            if (reqId != mRequestId) {
                ALOGE("%s: Event has Req. ID:%d <> ours:%d",
                    __FUNCTION__, reqId, mRequestId);
                break;
            }
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE])
            {
                ALOGE("%s: ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE not found."
                    "Exit.", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            numResults = nla_get_u32(tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE]);
            /* Get the memory size of previous fragments, if any. */
            sizeOfObtainedResults = sizeof(wifi_significant_change_result *) *
                                mSignificantChangeNumResults;

            index = mSignificantChangeNumResults;
            mSignificantChangeNumResults += numResults;
            /*
             * Check if this chunck of wifi_significant_change results is a
             * continuation of a previous one.
             */
            if (mSignificantChangeMoreData) {
                mSignificantChangeResults =
                    (wifi_significant_change_result **)
                        realloc (mSignificantChangeResults,
                        sizeof(wifi_significant_change_result *) *
                                mSignificantChangeNumResults);
            } else {
                mSignificantChangeResults =
                    (wifi_significant_change_result **)
                        malloc (sizeof(wifi_significant_change_result *) *
                                mSignificantChangeNumResults);
            }

            if (!mSignificantChangeResults) {
                ALOGE("%s: Failed to alloc memory for results array. Exit.\n",
                    __FUNCTION__);
                ret = WIFI_ERROR_OUT_OF_MEMORY;
                break;
            }
            /* Initialize the newly allocated memory area with 0. */
            memset((u8 *)mSignificantChangeResults + sizeOfObtainedResults, 0,
                    sizeof(wifi_significant_change_result *) *
                                numResults);
            ALOGV("%s: mSignificantChangeMoreData = %d",
                    __FUNCTION__, mSignificantChangeMoreData);

            for (scanResultsInfo = (struct nlattr *) nla_data(tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]),
                rem = nla_len(tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_LIST]);
                nla_ok(scanResultsInfo, rem);
                scanResultsInfo = nla_next(scanResultsInfo, &(rem)))
            {
                u32 num_rssi = 0;
                u32 resultsBufSize = 0;
                struct nlattr *tb2[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX + 1];
                nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_MAX,
                    (struct nlattr *) nla_data(scanResultsInfo),
                    nla_len(scanResultsInfo), NULL);
                if (!tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_NUM_RSSI
                    ])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_"
                        "SIGNIFICANT_CHANGE_RESULT_NUM_RSSI not found. "
                        "Exit.", __FUNCTION__);
                    ret = WIFI_ERROR_INVALID_ARGS;
                    break;
                }
                num_rssi = nla_get_u32(tb2[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SIGNIFICANT_CHANGE_RESULT_NUM_RSSI
                        ]);
                resultsBufSize = sizeof(wifi_significant_change_result) +
                            num_rssi * sizeof(wifi_rssi);
                mSignificantChangeResults[index] =
                    (wifi_significant_change_result *) malloc (resultsBufSize);

                if (!mSignificantChangeResults[index]) {
                    ALOGE("%s: Failed to alloc memory for results array Exit",
                        __FUNCTION__);
                    ret = WIFI_ERROR_OUT_OF_MEMORY;
                    break;
                }
                /* Initialize the newly allocated memory area with 0. */
                memset((u8 *)mSignificantChangeResults[index],
                        0, resultsBufSize);

                ALOGV("%s: For Significant Change results[%d], num_rssi:%d\n",
                    __FUNCTION__, index, num_rssi);
                index++;
            }

            ALOGV("%s: Extract significant change results.\n", __FUNCTION__);
            startingIndex =
                mSignificantChangeNumResults - numResults;
            ret = gscan_get_significant_change_results(numResults,
                                                mSignificantChangeResults,
                                                startingIndex,
                                                tbVendor);
            /* If a parsing error occurred, exit and proceed for cleanup. */
            if (ret)
                break;
            /* To support fragmentation from firmware, monitor the
             * MORE_DATA flag and cache results until MORE_DATA = 0.
             * Only then we can pass on the results to framework through
             * the callback function.
             */
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]) {
                ALOGE("%s: GSCAN_RESULTS_NUM_RESULTS_MORE_DATA not"
                    " found. Stop parsing and exit.", __FUNCTION__);
                break;
            }
            mSignificantChangeMoreData = nla_get_u8(
                tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]);
            ALOGV("%s: More data = %d. \n",
                __FUNCTION__, mSignificantChangeMoreData);

            /* Send the results if no more result fragments are expected */
            if (!mSignificantChangeMoreData) {
                ALOGV("%s: Invoking the callback. \n", __FUNCTION__);
                (*mHandler.on_significant_change)(reqId,
                                              mSignificantChangeNumResults,
                                              mSignificantChangeResults);
                if (mSignificantChangeResults) {
                    /* Reset flag and num counter. */
                    for (index = 0; index < mSignificantChangeNumResults;
                         index++)
                    {
                        free(mSignificantChangeResults[index]);
                        mSignificantChangeResults[index] = NULL;
                    }
                    free(mSignificantChangeResults);
                    mSignificantChangeResults = NULL;
                }
                mSignificantChangeNumResults = 0;
                mSignificantChangeMoreData = false;
            }
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_EVENT:
        {
            wifi_scan_event scanEvent;
            wifi_request_id reqId;

            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID])
            {
                ALOGE("%s: ATTR_GSCAN_RESULTS_REQUEST_ID not found. Exit.",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            reqId = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                    );
            /* If this is not for us, just ignore it. */
            if (reqId != mRequestId) {
                ALOGE("%s: Event has Req. ID:%d <> ours:%d",
                    __FUNCTION__, reqId, mRequestId);
                break;
            }

            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_EVENT_TYPE]) {
                ALOGE("%s: GSCAN_RESULTS_SCAN_EVENT_TYPE not"
                    " found. Stop parsing and exit.", __FUNCTION__);
                break;
            }
            scanEvent = (wifi_scan_event) nla_get_u8(tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_EVENT_TYPE]);

            ALOGV("%s: Scan event type: %d\n", __FUNCTION__, scanEvent);
            /* Send the results if no more result fragments are expected. */
            (*mHandler.on_scan_event)(reqId, scanEvent);
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_PNO_NETWORK_FOUND:
        {
            wifi_request_id id;
            u32 resultsBufSize = 0;
            u32 numResults = 0;
            u32 startingIndex, sizeOfObtainedResults;

            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID])
            {
                /* RequestId is not provided by FW/Driver for this event */
                ALOGE("%s: ATTR_GSCAN_RESULTS_REQUEST_ID not found. Continue.",
                    __FUNCTION__);
                id = mRequestId; /* Use the saved mRequestId instead. */
            } else {
                id = nla_get_u32(
                        tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                        );
                /* If this is not for us, use the saved requestId */
                if (id != mRequestId) {
                    ALOGE("%s: Event has Req. ID:%d <> ours:%d",
                        __FUNCTION__, id, mRequestId);
                    id = mRequestId;
                }
            }

            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE]) {
                ALOGE("%s: GSCAN_RESULTS_NUM_RESULTS_AVAILABLE not found",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            numResults = nla_get_u32(tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_NUM_RESULTS_AVAILABLE]);
            ALOGV("%s: number of results:%d", __FUNCTION__, numResults);

            /* Get the memory size of previous fragments, if any. */
            sizeOfObtainedResults = mPnoNetworkFoundNumResults *
                          sizeof(wifi_scan_result);

            mPnoNetworkFoundNumResults += numResults;
            resultsBufSize += mPnoNetworkFoundNumResults *
                                            sizeof(wifi_scan_result);

            /* Check if this chunck of scan results is a continuation of
             * a previous one.
             */
            if (mPnoNetworkFoundMoreData) {
                mPnoNetworkFoundResults = (wifi_scan_result *)
                            realloc (mPnoNetworkFoundResults, resultsBufSize);
            } else {
                mPnoNetworkFoundResults = (wifi_scan_result *)
                            malloc (resultsBufSize);
            }

            if (!mPnoNetworkFoundResults) {
                ALOGE("%s: Failed to alloc memory for results array. Exit.\n",
                    __FUNCTION__);
                ret = WIFI_ERROR_OUT_OF_MEMORY;
                break;
            }
            /* Initialize the newly allocated memory area with 0. */
            memset((u8 *)mPnoNetworkFoundResults + sizeOfObtainedResults, 0,
                    resultsBufSize - sizeOfObtainedResults);

            ALOGV("%s: Num of AP FOUND results = %d. \n", __FUNCTION__,
                                            mPnoNetworkFoundNumResults);

            /* To support fragmentation from firmware, monitor the
             * MORE_DATA flag and cache results until MORE_DATA = 0.
             * Only then we can pass on the results to framework through
             * the callback function.
             */
            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]) {
                ALOGE("%s: GSCAN_RESULTS_NUM_RESULTS_MORE_DATA not"
                    " found", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            } else {
                mPnoNetworkFoundMoreData = nla_get_u8(
                    tbVendor[
                    QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_SCAN_RESULT_MORE_DATA]);
                ALOGV("%s: More data = %d. \n",
                    __FUNCTION__, mPnoNetworkFoundMoreData);
            }

            ALOGV("%s: Extract PNO_NETWORK_FOUND results.\n", __FUNCTION__);
            startingIndex = mPnoNetworkFoundNumResults - numResults;
            ALOGV("%s: starting_index:%d",
                __FUNCTION__, startingIndex);
            ret = gscan_parse_pno_network_results(numResults,
                                                mPnoNetworkFoundResults,
                                                startingIndex,
                                                tbVendor);
            /* If a parsing error occurred, exit and proceed for cleanup. */
            if (ret)
                break;
            /* Send the results if no more result data fragments are expected */
            if (!mPnoNetworkFoundMoreData) {
                (*mHandler.on_pno_network_found)(id,
                                                mPnoNetworkFoundNumResults,
                                                mPnoNetworkFoundResults);
                /* Reset flag and num counter. */
                if (mPnoNetworkFoundResults) {
                    free(mPnoNetworkFoundResults);
                    mPnoNetworkFoundResults = NULL;
                }
                mPnoNetworkFoundMoreData = false;
                mPnoNetworkFoundNumResults = 0;
            }
        }
        break;
        case QCA_NL80211_VENDOR_SUBCMD_PNO_PASSPOINT_NETWORK_FOUND:
        {
            wifi_request_id id;

            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID])
            {
                /* RequestId is not provided by FW/Driver for this event */
                ALOGE("%s: ATTR_GSCAN_RESULTS_REQUEST_ID not found. Continue.",
                    __FUNCTION__);
                id = mRequestId; /* Use the saved mRequestId instead. */
            } else {
                id = nla_get_u32(
                        tbVendor[QCA_WLAN_VENDOR_ATTR_GSCAN_RESULTS_REQUEST_ID]
                        );
                /* If this is not for us, use the saved requestId */
                if (id != mRequestId) {
                    ALOGE("%s: Event has Req. ID:%d <> ours:%d",
                        __FUNCTION__, id, mRequestId);
                    id = mRequestId;
                }
            }

            ret = gscan_parse_passpoint_network_result(tbVendor);
            /* If a parsing error occurred, exit and proceed for cleanup. */
            if (ret)
            {
                ALOGE("%s: gscan_parse_passpoint_network_result"
                      "returned error: %d.\n", __FUNCTION__, ret);
                break;
            }
            (*mHandler.on_passpoint_network_found)(id,
                                                   mPasspointNetId,
                                                   mPasspointNetworkFoundResult,
                                                   mPasspointAnqpLen,
                                                   mPasspointAnqp);
            if (mPasspointNetworkFoundResult)
            {
                free(mPasspointNetworkFoundResult);
                mPasspointNetworkFoundResult = NULL;
            }
            if (mPasspointAnqp)
            {
                free(mPasspointAnqp);
                mPasspointAnqp = NULL;
            }
            mPasspointNetId = -1;
            mPasspointAnqpLen = 0;
        }
        break;
        default:
            /* Error case should not happen print log */
            ALOGE("%s: Wrong GScan subcmd received %d", __FUNCTION__, mSubcmd);
    }

    /* A parsing error occurred, do the cleanup of gscan result lists. */
    if (ret) {
        switch(mSubcmd)
        {
            case QCA_NL80211_VENDOR_SUBCMD_GSCAN_FULL_SCAN_RESULT:
            {
                free(result);
                result = NULL;
            }
            break;

            case QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_FOUND:
            {
                /* Reset flag and num counter. */
                free(mHotlistApFoundResults);
                mHotlistApFoundResults = NULL;
                mHotlistApFoundMoreData = false;
                mHotlistApFoundNumResults = 0;
            }
            break;

            case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SIGNIFICANT_CHANGE:
            {
                if (mSignificantChangeResults) {
                    for (i = 0; i < mSignificantChangeNumResults; i++)
                    {
                        if (mSignificantChangeResults[i]) {
                            free(mSignificantChangeResults[i]);
                            mSignificantChangeResults[i] = NULL;
                        }
                    }
                    free(mSignificantChangeResults);
                    mSignificantChangeResults = NULL;
                }
                mSignificantChangeNumResults = 0;
                mSignificantChangeMoreData = false;
            }
            break;

            case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_RESULTS_AVAILABLE:
            break;

            case QCA_NL80211_VENDOR_SUBCMD_GSCAN_SCAN_EVENT:
            break;

            case QCA_NL80211_VENDOR_SUBCMD_GSCAN_HOTLIST_AP_LOST:
            {
                /* Reset flag and num counter. */
                free(mHotlistApLostResults);
                mHotlistApLostResults = NULL;
                mHotlistApLostMoreData = false;
                mHotlistApLostNumResults = 0;
            }
            break;

            case QCA_NL80211_VENDOR_SUBCMD_PNO_NETWORK_FOUND:
            {
                /* Reset flag and num counter. */
                if (mPnoNetworkFoundResults) {
                    free(mPnoNetworkFoundResults);
                    mPnoNetworkFoundResults = NULL;
                }
                mPnoNetworkFoundMoreData = false;
                mPnoNetworkFoundNumResults = 0;
            }
            break;

            case QCA_NL80211_VENDOR_SUBCMD_PNO_PASSPOINT_NETWORK_FOUND:
            {
                if (mPasspointNetworkFoundResult)
                {
                    free(mPasspointNetworkFoundResult);
                    mPasspointNetworkFoundResult = NULL;
                }
                if (mPasspointAnqp)
                {
                    free(mPasspointAnqp);
                    mPasspointAnqp = NULL;
                }
                mPasspointNetId = -1;
                mPasspointAnqpLen = 0;
            }
            break;

            default:
                ALOGE("%s: Parsing err handler: wrong GScan subcmd "
                    "received %d", __FUNCTION__, mSubcmd);
        }
    }
    return NL_SKIP;
}
