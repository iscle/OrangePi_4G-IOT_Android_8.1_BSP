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

#include "sync.h"

#define LOG_TAG  "WifiHAL"

#include <utils/Log.h>

#include "wifi_hal.h"
#include "common.h"
#include "cpp_bindings.h"
#include "llstatscommand.h"
#include "vendor_definitions.h"

//Singleton Static Instance
LLStatsCommand* LLStatsCommand::mLLStatsCommandInstance  = NULL;

// This function implements creation of Vendor command
// For LLStats just call base Vendor command create
int LLStatsCommand::create() {
    int ret = mMsg.create(NL80211_CMD_VENDOR, 0, 0);
    if (ret < 0) {
        return ret;
    }
    // insert the oui in the msg
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_ID, mVendor_id);
    if (ret < 0)
        goto out;

    // insert the subcmd in the msg
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_SUBCMD, mSubcmd);
    if (ret < 0)
        goto out;

out:
    return ret;
}

LLStatsCommand::LLStatsCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    memset(&mClearRspParams, 0,sizeof(LLStatsClearRspParams));
    memset(&mResultsParams, 0,sizeof(LLStatsResultsParams));
    memset(&mHandler, 0,sizeof(mHandler));
    mRadioStatsSize = 0;
    mNumRadios = 0;
}

LLStatsCommand::~LLStatsCommand()
{
    mLLStatsCommandInstance = NULL;
}

LLStatsCommand* LLStatsCommand::instance(wifi_handle handle)
{
    if (handle == NULL) {
        ALOGE("Interface Handle is invalid");
        return NULL;
    }
    if (mLLStatsCommandInstance == NULL) {
        mLLStatsCommandInstance = new LLStatsCommand(handle, 0,
                OUI_QCA,
                QCA_NL80211_VENDOR_SUBCMD_LL_STATS_SET);
        return mLLStatsCommandInstance;
    }
    else
    {
        if (handle != getWifiHandle(mLLStatsCommandInstance->mInfo))
        {
            /* upper layer must have cleaned up the handle and reinitialized,
               so we need to update the same */
            ALOGE("Handle different, update the handle");
            mLLStatsCommandInstance->mInfo = (hal_info *)handle;
        }
    }
    return mLLStatsCommandInstance;
}

void LLStatsCommand::initGetContext(u32 reqId)
{
    mRequestId = reqId;
    memset(&mHandler, 0,sizeof(mHandler));
}

void LLStatsCommand::setSubCmd(u32 subcmd)
{
    mSubcmd = subcmd;
}

void LLStatsCommand::setHandler(wifi_stats_result_handler handler)
{
    mHandler = handler;
}

static wifi_error get_wifi_interface_info(wifi_interface_link_layer_info *stats,
                                          struct nlattr **tb_vendor)
{
    u32 len = 0;

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_MODE])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_MODE not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->mode = (wifi_interface_mode)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_MODE]);


    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_MAC_ADDR])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_MAC_ADDR not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_MAC_ADDR]);
    len = ((sizeof(stats->mac_addr) <= len) ? sizeof(stats->mac_addr) : len);
    memcpy(&stats->mac_addr[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_MAC_ADDR]), len);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_STATE])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_STATE not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->state = (wifi_connection_state)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_STATE]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_ROAMING])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_ROAMING not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->roaming = (wifi_roam_state)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_ROAMING]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_CAPABILITIES])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_CAPABILITIES not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->capabilities = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_CAPABILITIES]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_SSID])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_SSID not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_SSID]);
    len = ((sizeof(stats->ssid) <= len) ? sizeof(stats->ssid) : len);
    memcpy(&stats->ssid[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_SSID]), len);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_BSSID])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_BSSID not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_BSSID]);
    len = ((sizeof(stats->bssid) <= len) ? sizeof(stats->bssid) : len);
    memcpy(&stats->bssid[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_BSSID]), len);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_AP_COUNTRY_STR])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_AP_COUNTRY_STR not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_AP_COUNTRY_STR]);
    len = ((sizeof(stats->ap_country_str) <= len) ? sizeof(stats->ap_country_str) : len);
    memcpy(&stats->ap_country_str[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_AP_COUNTRY_STR]),
           len);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_COUNTRY_STR])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_COUNTRY_STR not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_COUNTRY_STR]);
    len = ((sizeof(stats->country_str) < len) ? sizeof(stats->country_str) : len);
    memcpy(&stats->country_str[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_INFO_COUNTRY_STR]),
           len);
#if QC_HAL_DEBUG
    ALOGV("Mode : %d\n"
          "Mac addr : "
          MAC_ADDR_STR
          "\nState : %d\n"
          "Roaming : %d\n"
          "capabilities : %0x\n"
          "SSID :%s\n"
          "BSSID : "
          MAC_ADDR_STR
          "\nAP country str : %c%c%c\n"
          "Country String for this Association : %c%c%c",
          stats->mode,
          MAC_ADDR_ARRAY(stats->mac_addr),
          stats->state,
          stats->roaming,
          stats->capabilities,
          stats->ssid,
          MAC_ADDR_ARRAY(stats->bssid),
          stats->ap_country_str[0],
          stats->ap_country_str[1],
          stats->ap_country_str[2],
          stats->country_str[0],
          stats->country_str[1],
          stats->country_str[2]);
#endif
    return WIFI_SUCCESS;
}

static wifi_error get_wifi_wmm_ac_stat(wifi_wmm_ac_stat *stats,
                                       struct nlattr **tb_vendor)
{

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_AC])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_AC not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->ac                     = (wifi_traffic_ac)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_AC]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_MPDU])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_MPDU not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->tx_mpdu                = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_MPDU]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_MPDU])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_MPDU not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rx_mpdu                = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_MPDU]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_MCAST])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_MCAST not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->tx_mcast               = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_MCAST]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_MCAST])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_MCAST not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rx_mcast               = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_MCAST]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_AMPDU])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_AMPDU not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rx_ampdu               = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RX_AMPDU]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_AMPDU])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_AMPDU not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->tx_ampdu               = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_TX_AMPDU]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_MPDU_LOST])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_MPDU_LOST not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->mpdu_lost              = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_MPDU_LOST]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->retries                = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES_SHORT])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES_SHORT not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->retries_short          = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES_SHORT]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES_LONG])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES_LONG not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->retries_long           = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_RETRIES_LONG]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_MIN])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_MIN not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->contention_time_min    = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_MIN]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_MAX])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_MAX not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->contention_time_max    = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_MAX]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_AVG])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_AVG not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->contention_time_avg    = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_TIME_AVG]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_NUM_SAMPLES])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_NUM_SAMPLES not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->contention_num_samples = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_AC_CONTENTION_NUM_SAMPLES]);
#ifdef QC_HAL_DEBUG
    ALOGV("%4u | %6u | %6u | %7u | %7u | %7u |"
          " %7u | %8u | %7u | %12u |"
          " %11u | %17u | %17u |"
          " %17u | %20u",
          stats->ac,
          stats->tx_mpdu,
          stats->rx_mpdu,
          stats->tx_mcast,
          stats->rx_mcast,
          stats->rx_ampdu,
          stats->tx_ampdu,
          stats->mpdu_lost,
          stats->retries,
          stats->retries_short,
          stats->retries_long,
          stats->contention_time_min,
          stats->contention_time_max,
          stats->contention_time_avg,
          stats->contention_num_samples);
#endif
    return WIFI_SUCCESS;
}

static wifi_error get_wifi_rate_stat(wifi_rate_stat *stats,
                                     struct nlattr **tb_vendor)
{

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_PREAMBLE])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_PREAMBLE not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rate.preamble        = nla_get_u8(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_PREAMBLE]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_NSS])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_NSS not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rate.nss             = nla_get_u8(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_NSS]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_BW])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_BW not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rate.bw              = nla_get_u8(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_BW]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_MCS_INDEX])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_MCS_INDEX not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rate.rateMcsIdx      = nla_get_u8(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_MCS_INDEX]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_BIT_RATE])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_BIT_RATE not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rate.bitrate         = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_BIT_RATE]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_TX_MPDU])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_TX_MPDU not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->tx_mpdu              = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_TX_MPDU]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RX_MPDU])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RX_MPDU not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rx_mpdu              = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RX_MPDU]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_MPDU_LOST])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_MPDU_LOST not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->mpdu_lost            = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_MPDU_LOST]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->retries              = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES_SHORT])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES_SHORT not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->retries_short        = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES_SHORT]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES_LONG])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES_LONG not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->retries_long         = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RATE_RETRIES_LONG]);
#ifdef QC_HAL_DEBUG
    ALOGV("%8u | %3u | %2u | %10u | %7u | %6u | %6u | %8u | %7u | %12u | %11u",
          stats->rate.preamble,
          stats->rate.nss,
          stats->rate.bw,
          stats->rate.rateMcsIdx,
          stats->rate.bitrate,
          stats->tx_mpdu,
          stats->rx_mpdu,
          stats->mpdu_lost,
          stats->retries,
          stats->retries_short,
          stats->retries_long);
#endif
    return WIFI_SUCCESS;
}

static wifi_error get_wifi_peer_info(wifi_peer_info *stats,
                                     struct nlattr **tb_vendor)
{
    u32 i = 0, len = 0;
    int rem;
    wifi_rate_stat * pRateStats;
    struct nlattr *rateInfo;
    wifi_error ret = WIFI_SUCCESS;

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_TYPE])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_TYPE not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->type                   = (wifi_peer_type)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_TYPE]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_MAC_ADDRESS])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_MAC_ADDRESS not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_MAC_ADDRESS]);
    len = ((sizeof(stats->peer_mac_address) <= len) ? sizeof(stats->peer_mac_address) : len);
    memcpy((void *)&stats->peer_mac_address[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_MAC_ADDRESS]),
            len);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_CAPABILITIES])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_CAPABILITIES not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->capabilities           = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_CAPABILITIES]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_NUM_RATES])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_NUM_RATES not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->num_rate               = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_NUM_RATES]);
#ifdef QC_HAL_DEBUG
    ALOGV("numPeers %u  Peer MAC addr :" MAC_ADDR_STR " capabilities %0x numRate %u",
           stats->type, MAC_ADDR_ARRAY(stats->peer_mac_address),
           stats->capabilities, stats->num_rate);
#endif

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_RATE_INFO])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_RATE_INFO not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
#ifdef QC_HAL_DEBUG
    ALOGV("%8s | %3s | %2s | %10s | %7s | %6s | %6s | %8s | %7s | %12s | %11s",
          "preamble", "nss", "bw", "rateMcsIdx", "bitrate", "txMpdu", "rxMpdu", "mpduLost", "retries", "retriesShort", "retriesLong");
#endif
    for (rateInfo = (struct nlattr *) nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_RATE_INFO]), rem = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_RATE_INFO]);
            nla_ok(rateInfo, rem);
            rateInfo = nla_next(rateInfo, &(rem)))
    {
        struct nlattr *tb2[ QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX+ 1];
        pRateStats = (wifi_rate_stat *) ((u8 *)stats->rate_stats + (i++ * sizeof(wifi_rate_stat)));

        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX, (struct nlattr *) nla_data(rateInfo), nla_len(rateInfo), NULL);
        ret = get_wifi_rate_stat(pRateStats, tb2);
        if(ret != WIFI_SUCCESS)
        {
            return ret;
        }
    }
    return WIFI_SUCCESS;
}

wifi_error LLStatsCommand::get_wifi_iface_stats(wifi_iface_stat *stats,
                                                struct nlattr **tb_vendor)
{
    struct nlattr *wmmInfo;
    wifi_wmm_ac_stat *pWmmStats;
    int i=0, rem;
    wifi_error ret = WIFI_SUCCESS;

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_BEACON_RX])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_BEACON_RX"
                "not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->beacon_rx = nla_get_u32(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_BEACON_RX]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_AVERAGE_TSF_OFFSET])
    {
        stats->average_tsf_offset = 0;
    } else {
        stats->average_tsf_offset = nla_get_u64(tb_vendor[
                QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_AVERAGE_TSF_OFFSET]);
    }

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_LEAKY_AP_DETECTED])
    {
        stats->leaky_ap_detected = 0;
    } else {
        stats->leaky_ap_detected = nla_get_u32(tb_vendor[
                QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_LEAKY_AP_DETECTED]);
    }

    if (!tb_vendor[
        QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_LEAKY_AP_AVG_NUM_FRAMES_LEAKED])
    {
        stats->leaky_ap_avg_num_frames_leaked = 0;
    } else {
        stats->leaky_ap_avg_num_frames_leaked = nla_get_u32(tb_vendor[
           QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_LEAKY_AP_AVG_NUM_FRAMES_LEAKED]);
    }

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_LEAKY_AP_GUARD_TIME])
    {
        stats->leaky_ap_guard_time = 0;
    } else {
        stats->leaky_ap_guard_time = nla_get_u32(tb_vendor[
                QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_LEAKY_AP_GUARD_TIME]);
    }

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_RX])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_RX"
                " not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->mgmt_rx         = nla_get_u32(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_RX]);

    if (!tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_ACTION_RX])
    {
        ALOGE("%s: "
                "QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_ACTION_RX"
                " not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->mgmt_action_rx  = nla_get_u32(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_ACTION_RX]);

    if (!tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_ACTION_TX])
    {
        ALOGE("%s: "
                "QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_ACTION_TX"
                " not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->mgmt_action_tx  = nla_get_u32(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_MGMT_ACTION_TX]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_MGMT])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_MGMT"
                " not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rssi_mgmt       = get_s32(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_MGMT]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_DATA])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_DATA"
                " not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rssi_data       = get_s32(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_DATA]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_ACK])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_ACK"
                " not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rssi_ack        = get_s32(tb_vendor[
            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_RSSI_ACK]);
#ifdef QC_HAL_DEBUG
    ALOGV("WMM STATS");
    ALOGV("beaconRx : %u "
          "mgmtRx : %u "
          "mgmtActionRx  : %u "
          "mgmtActionTx : %u "
          "rssiMgmt : %d "
          "rssiData : %d "
          "rssiAck  : %d ",
          stats->beacon_rx,
          stats->mgmt_rx,
          stats->mgmt_action_rx,
          stats->mgmt_action_tx,
          stats->rssi_mgmt,
          stats->rssi_data,
          stats->rssi_ack);
#endif
    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_INFO])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_INFO"
                " not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
#ifdef QC_HAL_DEBUG
    ALOGV("%4s | %6s | %6s | %7s | %7s | %7s |"
          " %7s | %8s | %7s | %12s |"
          " %11s | %17s | %17s |"
          " %17s | %20s",
          "ac","txMpdu", "rxMpdu", "txMcast", "rxMcast", "rxAmpdu",
          "txAmpdu", "mpduLost", "retries", "retriesShort",
          "retriesLong", "contentionTimeMin", "contentionTimeMax",
          "contentionTimeAvg", "contentionNumSamples");
#endif
    for (wmmInfo = (struct nlattr *) nla_data(tb_vendor[
                QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_INFO]),
            rem = nla_len(tb_vendor[
                QCA_WLAN_VENDOR_ATTR_LL_STATS_WMM_INFO]);
            nla_ok(wmmInfo, rem);
            wmmInfo = nla_next(wmmInfo, &(rem)))
    {
        struct nlattr *tb2[ QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX+ 1];
        pWmmStats = (wifi_wmm_ac_stat *) ((u8 *)stats->ac
                + (i++ * sizeof(wifi_wmm_ac_stat)));
        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX,
                (struct nlattr *) nla_data(wmmInfo),
                nla_len(wmmInfo), NULL);
        ret = get_wifi_wmm_ac_stat(pWmmStats, tb2);
        if(ret != WIFI_SUCCESS)
        {
            return ret;
        }
    }

    return WIFI_SUCCESS;
}

static wifi_error get_wifi_radio_stats(wifi_radio_stat *stats,
                                       struct nlattr **tb_vendor)
{
    u32 i = 0;
    struct nlattr *chInfo;
    wifi_channel_stat *pChStats;
    int rem;

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ID])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ID not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->radio             = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ID]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->on_time           = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_TX_TIME])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_TX_TIME not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->tx_time           = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_TX_TIME]);

    if (stats->num_tx_levels) {
        if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_TX_TIME_PER_LEVEL]) {
            ALOGE("%s: num_tx_levels is %u but QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_TX_TIME_PER_LEVEL not found", __func__, stats->num_tx_levels);
            stats->num_tx_levels = 0;
            return WIFI_ERROR_INVALID_ARGS;
        }
        stats->tx_time_per_levels =
                             (u32 *) malloc(sizeof(u32) * stats->num_tx_levels);
        if (!stats->tx_time_per_levels) {
            ALOGE("%s: radio_stat: tx_time_per_levels malloc Failed", __func__);
            stats->num_tx_levels = 0;
            return WIFI_ERROR_OUT_OF_MEMORY;
        }

        nla_memcpy(stats->tx_time_per_levels,
            tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_TX_TIME_PER_LEVEL],
            sizeof(u32) * stats->num_tx_levels);
    }

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_RX_TIME])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_RX_TIME not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->rx_time           = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_RX_TIME]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_SCAN])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_SCAN not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->on_time_scan      = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_SCAN]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_NBD])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_NBD not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->on_time_nbd       = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_NBD]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_GSCAN])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_GSCAN not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->on_time_gscan     = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_GSCAN]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_ROAM_SCAN])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_ROAM_SCAN not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->on_time_roam_scan = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_ROAM_SCAN]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_PNO_SCAN])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_PNO_SCAN not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->on_time_pno_scan  = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_PNO_SCAN]);

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_HS20])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_HS20 not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->on_time_hs20      = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_ON_TIME_HS20]);


    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_CHANNELS])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_CHANNELS not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    stats->num_channels                           = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_CHANNELS]);

    if (stats->num_channels == 0) {
        return WIFI_SUCCESS;
    }

    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CH_INFO])
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CH_INFO not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    for (chInfo = (struct nlattr *) nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CH_INFO]), rem = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CH_INFO]);
            nla_ok(chInfo, rem);
            chInfo = nla_next(chInfo, &(rem)))
    {
        struct nlattr *tb2[ QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX+ 1];
        pChStats = (wifi_channel_stat *) ((u8 *)stats->channels + (i++ * (sizeof(wifi_channel_stat))));
        nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX, (struct nlattr *) nla_data(chInfo), nla_len(chInfo), NULL);

        if (!tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_WIDTH])
        {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_WIDTH not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        pChStats->channel.width                  = (wifi_channel_width)nla_get_u32(tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_WIDTH]);

        if (!tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ])
        {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        pChStats->channel.center_freq            = (wifi_channel)nla_get_u32(tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ]);

        if (!tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ0])
        {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ0 not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        pChStats->channel.center_freq0           = (wifi_channel)nla_get_u32(tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ0]);

        if (!tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ1])
        {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ1 not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        pChStats->channel.center_freq1           = (wifi_channel)nla_get_u32(tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_INFO_CENTER_FREQ1]);

        if (!tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_ON_TIME])
        {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_ON_TIME not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        pChStats->on_time                = nla_get_u32(tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_ON_TIME]);

        if (!tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_CCA_BUSY_TIME])
        {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_CCA_BUSY_TIME not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        pChStats->cca_busy_time          = nla_get_u32(tb2[QCA_WLAN_VENDOR_ATTR_LL_STATS_CHANNEL_CCA_BUSY_TIME]);
    }
    return WIFI_SUCCESS;
}

void LLStatsCommand::getClearRspParams(u32 *stats_clear_rsp_mask, u8 *stop_rsp)
{
    *stats_clear_rsp_mask =  mClearRspParams.stats_clear_rsp_mask;
    *stop_rsp = mClearRspParams.stop_rsp;
}

int LLStatsCommand::requestResponse()
{
    return WifiCommand::requestResponse(mMsg);
}

wifi_error LLStatsCommand::notifyResponse()
{
    wifi_error ret = WIFI_SUCCESS;

    /* Indicate stats to framework only if both radio and iface stats
     * are present */
    if (mResultsParams.radio_stat && mResultsParams.iface_stat) {
        mHandler.on_link_stats_results(mRequestId,
                                       mResultsParams.iface_stat, mNumRadios,
                                       mResultsParams.radio_stat);
    } else {
        ret = WIFI_ERROR_INVALID_ARGS;
    }

    clearStats();

    return ret;
}


void LLStatsCommand::clearStats()
{
    if(mResultsParams.radio_stat)
    {
        if (mResultsParams.radio_stat->tx_time_per_levels)
        {
            free(mResultsParams.radio_stat->tx_time_per_levels);
            mResultsParams.radio_stat->tx_time_per_levels = NULL;
        }
        free(mResultsParams.radio_stat);
        mResultsParams.radio_stat = NULL;
        mRadioStatsSize = 0;
        mNumRadios = 0;
     }
     if(mResultsParams.iface_stat)
     {
        free(mResultsParams.iface_stat);
        mResultsParams.iface_stat = NULL;
     }
}


int LLStatsCommand::handleResponse(WifiEvent &reply)
{
    unsigned i=0;
    int status = WIFI_ERROR_NONE;
    WifiVendorCommand::handleResponse(reply);

    // Parse the vendordata and get the attribute

    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_LL_STATS_GET:
        {
            u32 resultsBufSize = 0;
            struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX + 1];
            int rem;
            wifi_radio_stat *radioStatsBuf;

            nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX,
                    (struct nlattr *)mVendorData,
                    mDataLen, NULL);

            if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_TYPE])
            {
                ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_TYPE not found",
                        __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }

            switch(nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_TYPE]))
            {
                case QCA_NL80211_VENDOR_SUBCMD_LL_STATS_TYPE_RADIO:
                {
                    if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_NUM_RADIOS])
                    {
                        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_NUM_RADIOS"
                              " not found", __FUNCTION__);
                        return WIFI_ERROR_INVALID_ARGS;
                    }
                    mNumRadios = nla_get_u32(tb_vendor[
                                    QCA_WLAN_VENDOR_ATTR_LL_STATS_NUM_RADIOS]);

                    if (!tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_CHANNELS
                        ])
                    {
                        ALOGE("%s:"
                            "QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_CHANNELS"
                            " not found", __FUNCTION__);
                        status = WIFI_ERROR_INVALID_ARGS;
                        goto cleanup;
                    }

                    resultsBufSize += (nla_get_u32(tb_vendor[
                            QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_CHANNELS])
                            * sizeof(wifi_channel_stat)
                            + sizeof(wifi_radio_stat));

                    radioStatsBuf = (wifi_radio_stat *)realloc(
                                              mResultsParams.radio_stat,
                                              mRadioStatsSize + resultsBufSize);
                    if (!radioStatsBuf)
                    {
                        ALOGE("%s: radio_stat: malloc Failed", __FUNCTION__);
                        status = WIFI_ERROR_OUT_OF_MEMORY;
                        goto cleanup;
                    }
                    mResultsParams.radio_stat = radioStatsBuf;

                    //Move the buffer to populate current radio stats
                    radioStatsBuf = (wifi_radio_stat *)(
                                                (u8 *)mResultsParams.radio_stat
                                                            + mRadioStatsSize);
                    memset(radioStatsBuf, 0, resultsBufSize);
                    mRadioStatsSize += resultsBufSize;

                    if (tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_TX_LEVELS])
                        radioStatsBuf->num_tx_levels = nla_get_u32(tb_vendor[
                                            QCA_WLAN_VENDOR_ATTR_LL_STATS_RADIO_NUM_TX_LEVELS]);

                    wifi_channel_stat *pWifiChannelStats;
                    status = get_wifi_radio_stats(radioStatsBuf,
                              tb_vendor);
                    if(status != WIFI_SUCCESS)
                    {
                        goto cleanup;
                    }
#ifdef QC_HAL_DEBUG
                    ALOGV("radio :%u onTime :%u txTime :%u rxTime :%u"
                          " onTimeScan :%u onTimeNbd :%u onTimeGscan :%u"
                          " onTimeRoamScan :%u onTimePnoScan :%u"
                          " onTimeHs20 :%u numChannels :%u num_tx_levels: %u",
                          radioStatsBuf->radio,
                          radioStatsBuf->on_time,
                          radioStatsBuf->tx_time,
                          radioStatsBuf->rx_time,
                          radioStatsBuf->on_time_scan,
                          radioStatsBuf->on_time_nbd,
                          radioStatsBuf->on_time_gscan,
                          radioStatsBuf->on_time_roam_scan,
                          radioStatsBuf->on_time_pno_scan,
                          radioStatsBuf->on_time_hs20,
                          radioStatsBuf->num_channels,
                          radioStatsBuf->num_tx_levels);
#ifdef QC_HAL_DEBUG
                    for (i = 0; i < radioStatsBuf->num_tx_levels; i++) {
                        ALOGV("Power level: %u  tx_time: %u", i,
                              radioStatsBuf->tx_time_per_levels[i]);
                    }
#endif
                    ALOGV("%5s | %10s | %11s | %11s | %6s | %11s", "width",
                          "CenterFreq", "CenterFreq0", "CenterFreq1",
                          "onTime", "ccaBusyTime");
#endif
                    for ( i=0; i < radioStatsBuf->num_channels; i++)
                    {
                        pWifiChannelStats =
                            (wifi_channel_stat *) (
                                (u8 *)radioStatsBuf->channels
                                + (i * sizeof(wifi_channel_stat)));

#ifdef QC_HAL_DEBUG
                        ALOGV("%5u | %10u | %11u | %11u | %6u | %11u",
                              pWifiChannelStats->channel.width,
                              pWifiChannelStats->channel.center_freq,
                              pWifiChannelStats->channel.center_freq0,
                              pWifiChannelStats->channel.center_freq1,
                              pWifiChannelStats->on_time,
                              pWifiChannelStats->cca_busy_time);
#endif
                    }
                }
                break;

                case QCA_NL80211_VENDOR_SUBCMD_LL_STATS_TYPE_IFACE:
                {
                    resultsBufSize = sizeof(wifi_iface_stat);
                    mResultsParams.iface_stat =
                        (wifi_iface_stat *) malloc (resultsBufSize);
                    if (!mResultsParams.iface_stat)
                    {
                        ALOGE("%s: iface_stat: malloc Failed", __FUNCTION__);
                        status = WIFI_ERROR_OUT_OF_MEMORY;
                        goto cleanup;
                    }
                    memset(mResultsParams.iface_stat, 0, resultsBufSize);
                    status = get_wifi_interface_info(
                            &mResultsParams.iface_stat->info, tb_vendor);
                    if(status != WIFI_SUCCESS)
                    {
                        goto cleanup;
                    }
                    status = get_wifi_iface_stats(mResultsParams.iface_stat,
                            tb_vendor);
                    if(status != WIFI_SUCCESS)
                    {
                        goto cleanup;
                    }

                    /* Driver/firmware might send this attribute when there
                     * are no peers connected.
                     * So that, the event
                     * QCA_NL80211_VENDOR_SUBCMD_LL_STATS_TYPE_PEERS can be
                     * avoided.
                     */
                    if (tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_NUM_PEERS])
                    {
                        mResultsParams.iface_stat->num_peers =
                            nla_get_u32(tb_vendor[
                                QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_NUM_PEERS]);
#ifdef QC_HAL_DEBUG
                        ALOGV("%s: numPeers is %u\n", __FUNCTION__,
                                mResultsParams.iface_stat->num_peers);
#endif
                    }
                }
                break;

                case QCA_NL80211_VENDOR_SUBCMD_LL_STATS_TYPE_PEERS:
                {
                    struct nlattr *peerInfo;
                    wifi_iface_stat *pIfaceStat = NULL;
                    u32 numPeers, num_rates = 0;
                    if (!tb_vendor[
                            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_NUM_PEERS])
                    {
                        ALOGE("%s:QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_NUM_PEERS"
                              " not found", __FUNCTION__);
                        status = WIFI_ERROR_INVALID_ARGS;
                        goto cleanup;
                    }
#ifdef QC_HAL_DEBUG
                    ALOGV(" numPeers is %u in %s\n",
                            nla_get_u32(tb_vendor[
                            QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_NUM_PEERS]),
                            __FUNCTION__);
#endif
                    if((numPeers = nla_get_u32(tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_LL_STATS_IFACE_NUM_PEERS])) > 0)
                    {
                        if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO])
                        {
                            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO"
                                    " not found", __FUNCTION__);
                            status = WIFI_ERROR_INVALID_ARGS;
                            goto cleanup;
                        }
                        for (peerInfo = (struct nlattr *) nla_data(tb_vendor[
                             QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO]),
                             rem = nla_len(tb_vendor[
                             QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO]);
                                nla_ok(peerInfo, rem);
                                peerInfo = nla_next(peerInfo, &(rem)))
                        {
                            struct nlattr *tb2[
                                QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX+ 1];

                            nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX,
                                    (struct nlattr *) nla_data(peerInfo),
                                    nla_len(peerInfo), NULL);

                            if (!tb2[
                             QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_NUM_RATES])
                            {
                                ALOGE("%s:"
                             "QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_NUM_RATES"
                             " not found", __FUNCTION__);
                                status = WIFI_ERROR_INVALID_ARGS;
                                goto cleanup;
                            }
                            num_rates += nla_get_u32(tb2[
                            QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO_NUM_RATES]);
                        }
                        resultsBufSize += (numPeers * sizeof(wifi_peer_info)
                                + num_rates * sizeof(wifi_rate_stat)
                                + sizeof (wifi_iface_stat));
                        pIfaceStat = (wifi_iface_stat *) malloc (
                                resultsBufSize);
                        if (!pIfaceStat)
                        {
                            ALOGE("%s: pIfaceStat: malloc Failed", __FUNCTION__);
                            status = WIFI_ERROR_OUT_OF_MEMORY;
                            goto cleanup;
                        }

                        memset(pIfaceStat, 0, resultsBufSize);
                        if(mResultsParams.iface_stat) {
                            memcpy ( pIfaceStat, mResultsParams.iface_stat,
                                sizeof(wifi_iface_stat));
                            free (mResultsParams.iface_stat);
                            mResultsParams.iface_stat = pIfaceStat;
                        }
                        wifi_peer_info *pPeerStats;
                        pIfaceStat->num_peers = numPeers;

                        if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO])
                        {
                            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO"
                                  " not found", __FUNCTION__);
                            status = WIFI_ERROR_INVALID_ARGS;
                            goto cleanup;
                        }
                        for (peerInfo = (struct nlattr *) nla_data(tb_vendor[
                            QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO]),
                            rem = nla_len(tb_vendor[
                                QCA_WLAN_VENDOR_ATTR_LL_STATS_PEER_INFO]);
                                nla_ok(peerInfo, rem);
                                peerInfo = nla_next(peerInfo, &(rem)))
                        {
                            struct nlattr *tb2[
                                QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX+ 1];
                            pPeerStats = (wifi_peer_info *) (
                                           (u8 *)pIfaceStat->peer_info
                                           + (i++ * sizeof(wifi_peer_info)));
                            nla_parse(tb2, QCA_WLAN_VENDOR_ATTR_LL_STATS_MAX,
                                (struct nlattr *) nla_data(peerInfo),
                                nla_len(peerInfo), NULL);
                            status = get_wifi_peer_info(pPeerStats, tb2);
                            if(status != WIFI_SUCCESS)
                            {
                                goto cleanup;
                            }
                        }
                    }

                }
                break;

                case QCA_NL80211_VENDOR_SUBCMD_LL_STATS_TYPE_INVALID:
                default:
                    //error case should not happen print log
                    ALOGE("%s: Wrong LLStats subcmd received %d", __FUNCTION__,
                           mSubcmd);
            }
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_LL_STATS_CLR:
        {
            struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_MAX + 1];
            nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_MAX,
                    (struct nlattr *)mVendorData,
                    mDataLen, NULL);

            if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_RSP_MASK])
            {
                ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_RSP_MASK not found", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            ALOGI("Resp mask : %d\n", nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_RSP_MASK]));

            if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_STOP_RSP])
            {
                ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_STOP_RSP not found", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            ALOGI("STOP resp : %d\n", nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_STOP_RSP]));

            if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_RSP_MASK])
            {
                ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_RSP_MASK not found", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            mClearRspParams.stats_clear_rsp_mask = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_RSP_MASK]);

            if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_STOP_RSP])
            {
                ALOGE("%s: QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_STOP_RSP not found", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            mClearRspParams.stop_rsp = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_STOP_RSP]);
            break;
        }
        default :
            ALOGE("%s: Wrong LLStats subcmd received %d", __FUNCTION__, mSubcmd);
    }
    return NL_SKIP;

cleanup:
    clearStats();
    return status;
}

//Implementation of the functions exposed in linklayer.h
wifi_error wifi_set_link_stats(wifi_interface_handle iface,
                               wifi_link_layer_params params)
{
    int ret = 0;
    LLStatsCommand *LLCommand;
    struct nlattr *nl_data;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);

    ALOGI("mpdu_size_threshold : %u, aggressive_statistics_gathering : %u",
          params.mpdu_size_threshold, params.aggressive_statistics_gathering);
    LLCommand = LLStatsCommand::instance(handle);
    if (LLCommand == NULL) {
        ALOGE("%s: Error LLStatsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    LLCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_LL_STATS_SET);

    /* create the message */
    ret = LLCommand->create();
    if (ret < 0)
        goto cleanup;

    ret = LLCommand->set_iface_id(iinfo->name);
    if (ret < 0)
        goto cleanup;

    /*add the attributes*/
    nl_data = LLCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data)
        goto cleanup;
    /**/
    ret = LLCommand->put_u32(QCA_WLAN_VENDOR_ATTR_LL_STATS_SET_CONFIG_MPDU_SIZE_THRESHOLD,
                                  params.mpdu_size_threshold);
    if (ret < 0)
        goto cleanup;
    /**/
    ret = LLCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_LL_STATS_SET_CONFIG_AGGRESSIVE_STATS_GATHERING,
                params.aggressive_statistics_gathering);
    if (ret < 0)
        goto cleanup;
    LLCommand->attr_end(nl_data);

    ret = LLCommand->requestResponse();
    if (ret != 0) {
        ALOGE("%s: requestResponse Error:%d",__FUNCTION__, ret);
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

//Implementation of the functions exposed in LLStats.h
wifi_error wifi_get_link_stats(wifi_request_id id,
                               wifi_interface_handle iface,
                               wifi_stats_result_handler handler)
{
    int ret = 0;
    LLStatsCommand *LLCommand;
    struct nlattr *nl_data;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);

    LLCommand = LLStatsCommand::instance(handle);
    if (LLCommand == NULL) {
        ALOGE("%s: Error LLStatsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    LLCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_LL_STATS_GET);

    LLCommand->initGetContext(id);

    LLCommand->setHandler(handler);

    /* create the message */
    ret = LLCommand->create();
    if (ret < 0)
        goto cleanup;

    ret = LLCommand->set_iface_id(iinfo->name);
    if (ret < 0)
        goto cleanup;
    /*add the attributes*/
    nl_data = LLCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data)
        goto cleanup;
    ret = LLCommand->put_u32(QCA_WLAN_VENDOR_ATTR_LL_STATS_GET_CONFIG_REQ_ID,
                                  id);
    if (ret < 0)
        goto cleanup;
    ret = LLCommand->put_u32(QCA_WLAN_VENDOR_ATTR_LL_STATS_GET_CONFIG_REQ_MASK,
                                  7);
    if (ret < 0)
        goto cleanup;

    /**/
    LLCommand->attr_end(nl_data);

    ret = LLCommand->requestResponse();
    if (ret != 0) {
        ALOGE("%s: requestResponse Error:%d",__FUNCTION__, ret);
    }
    if (ret < 0) {
        LLCommand->clearStats();
        goto cleanup;
    }

    if (ret == 0) {
        ret = LLCommand->notifyResponse();
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}


//Implementation of the functions exposed in LLStats.h
wifi_error wifi_clear_link_stats(wifi_interface_handle iface,
                                 u32 stats_clear_req_mask,
                                 u32 *stats_clear_rsp_mask,
                                 u8 stop_req, u8 *stop_rsp)
{
    int ret = 0;
    LLStatsCommand *LLCommand;
    struct nlattr *nl_data;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);

    ALOGI("clear_req : %x, stop_req : %u", stats_clear_req_mask, stop_req);
    LLCommand = LLStatsCommand::instance(handle);
    if (LLCommand == NULL) {
        ALOGE("%s: Error LLStatsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    LLCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_LL_STATS_CLR);

    /* create the message */
    ret = LLCommand->create();
    if (ret < 0)
        goto cleanup;

    ret = LLCommand->set_iface_id(iinfo->name);
    if (ret < 0)
        goto cleanup;
    /*add the attributes*/
    nl_data = LLCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data)
        goto cleanup;
    /**/
    ret = LLCommand->put_u32(QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_REQ_MASK,
                                  stats_clear_req_mask);
    if (ret < 0)
        goto cleanup;
    /**/
    ret = LLCommand->put_u8(QCA_WLAN_VENDOR_ATTR_LL_STATS_CLR_CONFIG_STOP_REQ,
                                   stop_req);
    if (ret < 0)
        goto cleanup;
    LLCommand->attr_end(nl_data);

    ret = LLCommand->requestResponse();
    if (ret != 0) {
        ALOGE("%s: requestResponse Error:%d",__FUNCTION__, ret);
    }

    LLCommand->getClearRspParams(stats_clear_rsp_mask, stop_rsp);

cleanup:
    delete LLCommand;
    return mapErrorKernelToWifiHAL(ret);
}
