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
#include <utils/Log.h>
#include "wifi_hal.h"
#include "nan_i.h"
#include "nancommand.h"

int NanCommand::putNanEnable(transaction_id id, const NanEnableRequest *pReq)
{
    ALOGV("NAN_ENABLE");
    size_t message_len = NAN_MAX_ENABLE_REQ_SIZE;

    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }

    message_len += \
        (
          pReq->config_support_5g ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->support_5g_val)) : 0 \
        ) + \
        (
          pReq->config_sid_beacon ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->sid_beacon_val)) : 0 \
        ) + \
        (
          pReq->config_2dot4g_rssi_close ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->rssi_close_2dot4g_val)) : 0 \
        ) + \
        (
          pReq->config_2dot4g_rssi_middle ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->rssi_middle_2dot4g_val)) : 0 \
        ) + \
        (
          pReq->config_hop_count_limit ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->hop_count_limit_val)) : 0 \
        ) + \
        (
          pReq->config_2dot4g_support ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->support_2dot4g_val)) : 0 \
        ) + \
        (
          pReq->config_2dot4g_beacons ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->beacon_2dot4g_val)) : 0 \
        ) + \
        (
          pReq->config_2dot4g_sdf ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->sdf_2dot4g_val)) : 0 \
        ) + \
        (
          pReq->config_5g_beacons ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->beacon_5g_val)) : 0 \
        ) + \
        (
          pReq->config_5g_sdf ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->sdf_5g_val)) : 0 \
        ) + \
        (
          pReq->config_5g_rssi_close ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->rssi_close_5g_val)) : 0 \
        ) + \
        (
          pReq->config_5g_rssi_middle ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->rssi_middle_5g_val)) : 0 \
        ) + \
        (
          pReq->config_2dot4g_rssi_proximity ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->rssi_proximity_2dot4g_val)) : 0 \
        ) + \
        (
          pReq->config_5g_rssi_close_proximity ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->rssi_close_proximity_5g_val)) : 0 \
        ) + \
        (
          pReq->config_rssi_window_size ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->rssi_window_size_val)) : 0 \
        ) + \
        (
          pReq->config_oui ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->oui_val)) : 0 \
        ) + \
        (
          pReq->config_intf_addr ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->intf_addr_val)) : 0 \
        ) + \
        (
          pReq->config_cluster_attribute_val ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->config_cluster_attribute_val)) : 0 \
        ) + \
        (
          pReq->config_scan_params ? NAN_MAX_SOCIAL_CHANNELS *
          (SIZEOF_TLV_HDR + sizeof(u32)) : 0 \
        ) + \
        (
          pReq->config_random_factor_force ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->random_factor_force_val)) : 0 \
        ) + \
        (
          pReq->config_hop_count_force ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->hop_count_force_val)) : 0 \
        ) + \
        (
          pReq->config_24g_channel ? (SIZEOF_TLV_HDR + \
          sizeof(u32)) : 0 \
        ) + \
        (
          pReq->config_5g_channel ? (SIZEOF_TLV_HDR + \
          sizeof(u32)) : 0 \
        ) + \
        (
           pReq->config_dw.config_2dot4g_dw_band ? (SIZEOF_TLV_HDR + \
           sizeof(u32)) : 0 \
        ) + \
        (
           pReq->config_dw.config_5g_dw_band ? (SIZEOF_TLV_HDR + \
           sizeof(u32)) : 0 \
        ) + \
        (
           pReq->config_disc_mac_addr_randomization ? (SIZEOF_TLV_HDR + \
           sizeof(u32)) : 0 \
        ) + \
        (
           /* Always include cfg discovery indication TLV */
           SIZEOF_TLV_HDR + sizeof(u32) \
        ) + \
        (
          pReq->config_subscribe_sid_beacon ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->subscribe_sid_beacon_val)) : 0 \
        ) ;
    pNanEnableReqMsg pFwReq = (pNanEnableReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset (pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_ENABLE_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = id;

    u8* tlvs = pFwReq->ptlv;

    /* Write the TLVs to the message. */

    tlvs = addTlv(NAN_TLV_TYPE_CLUSTER_ID_LOW, sizeof(pReq->cluster_low),
                  (const u8*)&pReq->cluster_low, tlvs);
    tlvs = addTlv(NAN_TLV_TYPE_CLUSTER_ID_HIGH, sizeof(pReq->cluster_high),
                  (const u8*)&pReq->cluster_high, tlvs);
    tlvs = addTlv(NAN_TLV_TYPE_MASTER_PREFERENCE, sizeof(pReq->master_pref),
                  (const u8*)&pReq->master_pref, tlvs);
    if (pReq->config_support_5g) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_SUPPORT, sizeof(pReq->support_5g_val),
                     (const u8*)&pReq->support_5g_val, tlvs);
    }
    if (pReq->config_sid_beacon) {
        tlvs = addTlv(NAN_TLV_TYPE_SID_BEACON, sizeof(pReq->sid_beacon_val),
                      (const u8*)&pReq->sid_beacon_val, tlvs);
    }
    if (pReq->config_2dot4g_rssi_close) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_RSSI_CLOSE,
                      sizeof(pReq->rssi_close_2dot4g_val),
                      (const u8*)&pReq->rssi_close_2dot4g_val, tlvs);
    }
    if (pReq->config_2dot4g_rssi_middle) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_RSSI_MIDDLE,
                      sizeof(pReq->rssi_middle_2dot4g_val),
                      (const u8*)&pReq->rssi_middle_2dot4g_val, tlvs);
    }
    if (pReq->config_hop_count_limit) {
        tlvs = addTlv(NAN_TLV_TYPE_HOP_COUNT_LIMIT,
                      sizeof(pReq->hop_count_limit_val),
                      (const u8*)&pReq->hop_count_limit_val, tlvs);
    }
    if (pReq->config_2dot4g_support) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_SUPPORT, sizeof(pReq->support_2dot4g_val),
                      (const u8*)&pReq->support_2dot4g_val, tlvs);
    }
    if (pReq->config_2dot4g_beacons) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_BEACON, sizeof(pReq->beacon_2dot4g_val),
                      (const u8*)&pReq->beacon_2dot4g_val, tlvs);
    }
    if (pReq->config_2dot4g_sdf) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_SDF, sizeof(pReq->sdf_2dot4g_val),
                      (const u8*)&pReq->sdf_2dot4g_val, tlvs);
    }
    if (pReq->config_5g_beacons) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_BEACON, sizeof(pReq->beacon_5g_val),
                      (const u8*)&pReq->beacon_5g_val, tlvs);
    }
    if (pReq->config_5g_sdf) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_SDF, sizeof(pReq->sdf_5g_val),
                      (const u8*)&pReq->sdf_5g_val, tlvs);
    }
    if (pReq->config_2dot4g_rssi_proximity) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_RSSI_CLOSE_PROXIMITY,
                      sizeof(pReq->rssi_proximity_2dot4g_val),
                      (const u8*)&pReq->rssi_proximity_2dot4g_val, tlvs);
    }
    /* Add the support of sending 5G RSSI values */
    if (pReq->config_5g_rssi_close) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_RSSI_CLOSE, sizeof(pReq->rssi_close_5g_val),
                      (const u8*)&pReq->rssi_close_5g_val, tlvs);
    }
    if (pReq->config_5g_rssi_middle) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_RSSI_MIDDLE, sizeof(pReq->rssi_middle_5g_val),
                      (const u8*)&pReq->rssi_middle_5g_val, tlvs);
    }
    if (pReq->config_5g_rssi_close_proximity) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_RSSI_CLOSE_PROXIMITY,
                      sizeof(pReq->rssi_close_proximity_5g_val),
                      (const u8*)&pReq->rssi_close_proximity_5g_val, tlvs);
    }
    if (pReq->config_rssi_window_size) {
        tlvs = addTlv(NAN_TLV_TYPE_RSSI_AVERAGING_WINDOW_SIZE, sizeof(pReq->rssi_window_size_val),
                      (const u8*)&pReq->rssi_window_size_val, tlvs);
    }
    if (pReq->config_oui) {
        tlvs = addTlv(NAN_TLV_TYPE_CLUSTER_OUI_NETWORK_ID, sizeof(pReq->oui_val),
                      (const u8*)&pReq->oui_val, tlvs);
    }
    if (pReq->config_intf_addr) {
        tlvs = addTlv(NAN_TLV_TYPE_SOURCE_MAC_ADDRESS, sizeof(pReq->intf_addr_val),
                      (const u8*)&pReq->intf_addr_val[0], tlvs);
    }
    if (pReq->config_cluster_attribute_val) {
        tlvs = addTlv(NAN_TLV_TYPE_CLUSTER_ATTRIBUTE_IN_SDF, sizeof(pReq->config_cluster_attribute_val),
                      (const u8*)&pReq->config_cluster_attribute_val, tlvs);
    }
    if (pReq->config_scan_params) {
        u32 socialChannelParamVal[NAN_MAX_SOCIAL_CHANNELS];
        /* Fill the social channel param */
        fillNanSocialChannelParamVal(&pReq->scan_params_val,
                                     socialChannelParamVal);
        int i;
        for (i = 0; i < NAN_MAX_SOCIAL_CHANNELS; i++) {
            tlvs = addTlv(NAN_TLV_TYPE_SOCIAL_CHANNEL_SCAN_PARAMS,
                          sizeof(socialChannelParamVal[i]),
                          (const u8*)&socialChannelParamVal[i], tlvs);
        }
    }
    if (pReq->config_random_factor_force) {
        tlvs = addTlv(NAN_TLV_TYPE_RANDOM_FACTOR_FORCE,
                      sizeof(pReq->random_factor_force_val),
                      (const u8*)&pReq->random_factor_force_val, tlvs);
    }
    if (pReq->config_hop_count_force) {
        tlvs = addTlv(NAN_TLV_TYPE_HOP_COUNT_FORCE,
                      sizeof(pReq->hop_count_force_val),
                      (const u8*)&pReq->hop_count_force_val, tlvs);
    }
    if (pReq->config_24g_channel) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_CHANNEL,
                      sizeof(u32),
                      (const u8*)&pReq->channel_24g_val, tlvs);
    }
    if (pReq->config_5g_channel) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_CHANNEL,
                      sizeof(u32),
                      (const u8*)&pReq->channel_5g_val, tlvs);
    }
    if (pReq->config_dw.config_2dot4g_dw_band) {
        tlvs = addTlv(NAN_TLV_TYPE_2G_COMMITTED_DW,
                      sizeof(pReq->config_dw.dw_2dot4g_interval_val),
                      (const u8*)&pReq->config_dw.dw_2dot4g_interval_val, tlvs);
    }
    if (pReq->config_dw.config_5g_dw_band) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_COMMITTED_DW,
                      sizeof(pReq->config_dw.dw_5g_interval_val),
                      (const u8*)&pReq->config_dw.dw_5g_interval_val, tlvs);
    }
    if (pReq->config_disc_mac_addr_randomization) {
        tlvs = addTlv(NAN_TLV_TYPE_DISC_MAC_ADDR_RANDOM_INTERVAL,
                      sizeof(u32),
                      (const u8*)&pReq->disc_mac_addr_rand_interval_sec, tlvs);
    }

    u32 config_discovery_indications;
    config_discovery_indications = (u32)pReq->discovery_indication_cfg;
    tlvs = addTlv(NAN_TLV_TYPE_CONFIG_DISCOVERY_INDICATIONS,
                  sizeof(u32),
                  (const u8*)&config_discovery_indications, tlvs);

    if (pReq->config_subscribe_sid_beacon) {
        tlvs = addTlv(NAN_TLV_TYPE_SUBSCRIBE_SID_BEACON,
                      sizeof(pReq->subscribe_sid_beacon_val),
                      (const u8*)&pReq->subscribe_sid_beacon_val, tlvs);
    }

    mVendorData = (char*)pFwReq;
    mDataLen = message_len;

    //Insert the vendor specific data
    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanDisable(transaction_id id)
{
    ALOGV("NAN_DISABLE");
    size_t message_len = sizeof(NanDisableReqMsg);

    pNanDisableReqMsg pFwReq = (pNanDisableReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset (pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_DISABLE_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = id;

    mVendorData = (char*)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanConfig(transaction_id id, const NanConfigRequest *pReq)
{
    ALOGV("NAN_CONFIG");
    size_t message_len = 0;
    int idx = 0;

    if (pReq == NULL ||
        pReq->num_config_discovery_attr > NAN_MAX_POSTDISCOVERY_LEN) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }

    message_len = sizeof(NanMsgHeader);

    message_len += \
        (
           pReq->config_sid_beacon ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->sid_beacon)) : 0 \
        ) + \
        (
           pReq->config_master_pref ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->master_pref)) : 0 \
        ) + \
        (
           pReq->config_rssi_proximity ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->rssi_proximity)) : 0 \
        ) + \
        (
           pReq->config_5g_rssi_close_proximity ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->rssi_close_proximity_5g_val)) : 0 \
        ) + \
        (
           pReq->config_rssi_window_size ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->rssi_window_size_val)) : 0 \
        ) + \
        (
           pReq->config_cluster_attribute_val ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->config_cluster_attribute_val)) : 0 \
        ) + \
        (
           pReq->config_scan_params ? NAN_MAX_SOCIAL_CHANNELS *
           (SIZEOF_TLV_HDR + sizeof(u32)) : 0 \
        ) + \
        (
           pReq->config_random_factor_force ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->random_factor_force_val)) : 0 \
        ) + \
        (
           pReq->config_hop_count_force ? (SIZEOF_TLV_HDR + \
           sizeof(pReq->hop_count_force_val)) : 0 \
        ) + \
        (
           pReq->config_conn_capability ? (SIZEOF_TLV_HDR + \
           sizeof(u32)) : 0 \
        ) + \
        (
           pReq->config_dw.config_2dot4g_dw_band ? (SIZEOF_TLV_HDR + \
           sizeof(u32)) : 0 \
        ) + \
        (
           pReq->config_dw.config_5g_dw_band ? (SIZEOF_TLV_HDR + \
           sizeof(u32)) : 0 \
        ) + \
        (
           pReq->config_disc_mac_addr_randomization ? (SIZEOF_TLV_HDR + \
           sizeof(u32)) : 0 \
        ) + \
        (
          pReq->config_subscribe_sid_beacon ? (SIZEOF_TLV_HDR + \
          sizeof(pReq->subscribe_sid_beacon_val)) : 0 \
        ) + \
        (
           /* Always include cfg discovery indication TLV */
           SIZEOF_TLV_HDR + sizeof(u32) \
        );

    if (pReq->num_config_discovery_attr) {
        for (idx = 0; idx < pReq->num_config_discovery_attr; idx ++) {
            message_len += SIZEOF_TLV_HDR +\
                calcNanTransmitPostDiscoverySize(&pReq->discovery_attr_val[idx]);
        }
    }

    if (pReq->config_fam && \
        calcNanFurtherAvailabilityMapSize(&pReq->fam_val)) {
        message_len += (SIZEOF_TLV_HDR + \
           calcNanFurtherAvailabilityMapSize(&pReq->fam_val));
    }

    pNanConfigurationReqMsg pFwReq = (pNanConfigurationReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset (pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_CONFIGURATION_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = id;

    u8* tlvs = pFwReq->ptlv;
    if (pReq->config_sid_beacon) {
        tlvs = addTlv(NAN_TLV_TYPE_SID_BEACON, sizeof(pReq->sid_beacon),
                      (const u8*)&pReq->sid_beacon, tlvs);
    }
    if (pReq->config_master_pref) {
        tlvs = addTlv(NAN_TLV_TYPE_MASTER_PREFERENCE, sizeof(pReq->master_pref),
                      (const u8*)&pReq->master_pref, tlvs);
    }
    if (pReq->config_rssi_window_size) {
        tlvs = addTlv(NAN_TLV_TYPE_RSSI_AVERAGING_WINDOW_SIZE, sizeof(pReq->rssi_window_size_val),
                      (const u8*)&pReq->rssi_window_size_val, tlvs);
    }
    if (pReq->config_rssi_proximity) {
        tlvs = addTlv(NAN_TLV_TYPE_24G_RSSI_CLOSE_PROXIMITY, sizeof(pReq->rssi_proximity),
                      (const u8*)&pReq->rssi_proximity, tlvs);
    }
    if (pReq->config_5g_rssi_close_proximity) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_RSSI_CLOSE_PROXIMITY,
                      sizeof(pReq->rssi_close_proximity_5g_val),
                      (const u8*)&pReq->rssi_close_proximity_5g_val, tlvs);
    }
    if (pReq->config_cluster_attribute_val) {
        tlvs = addTlv(NAN_TLV_TYPE_CLUSTER_ATTRIBUTE_IN_SDF, sizeof(pReq->config_cluster_attribute_val),
                      (const u8*)&pReq->config_cluster_attribute_val, tlvs);
    }
    if (pReq->config_scan_params) {
        u32 socialChannelParamVal[NAN_MAX_SOCIAL_CHANNELS];
        /* Fill the social channel param */
        fillNanSocialChannelParamVal(&pReq->scan_params_val,
                                 socialChannelParamVal);
        int i;
        for (i = 0; i < NAN_MAX_SOCIAL_CHANNELS; i++) {
            tlvs = addTlv(NAN_TLV_TYPE_SOCIAL_CHANNEL_SCAN_PARAMS,
                          sizeof(socialChannelParamVal[i]),
                          (const u8*)&socialChannelParamVal[i], tlvs);
        }
    }
    if (pReq->config_random_factor_force) {
        tlvs = addTlv(NAN_TLV_TYPE_RANDOM_FACTOR_FORCE,
                      sizeof(pReq->random_factor_force_val),
                      (const u8*)&pReq->random_factor_force_val, tlvs);
    }
    if (pReq->config_hop_count_force) {
        tlvs = addTlv(NAN_TLV_TYPE_HOP_COUNT_FORCE,
                      sizeof(pReq->hop_count_force_val),
                      (const u8*)&pReq->hop_count_force_val, tlvs);
    }
    if (pReq->config_conn_capability) {
        u32 val = \
        getNanTransmitPostConnectivityCapabilityVal(&pReq->conn_capability_val);
        tlvs = addTlv(NAN_TLV_TYPE_POST_NAN_CONNECTIVITY_CAPABILITIES_TRANSMIT,
                      sizeof(val), (const u8*)&val, tlvs);
    }
    if (pReq->num_config_discovery_attr) {
        for (idx = 0; idx < pReq->num_config_discovery_attr; idx ++) {
            fillNanTransmitPostDiscoveryVal(&pReq->discovery_attr_val[idx],
                                            (u8*)(tlvs + SIZEOF_TLV_HDR));
            tlvs = addTlv(NAN_TLV_TYPE_POST_NAN_DISCOVERY_ATTRIBUTE_TRANSMIT,
                          calcNanTransmitPostDiscoverySize(
                              &pReq->discovery_attr_val[idx]),
                          (const u8*)(tlvs + SIZEOF_TLV_HDR), tlvs);
        }
    }
    if (pReq->config_fam && \
        calcNanFurtherAvailabilityMapSize(&pReq->fam_val)) {
        fillNanFurtherAvailabilityMapVal(&pReq->fam_val,
                                        (u8*)(tlvs + SIZEOF_TLV_HDR));
        tlvs = addTlv(NAN_TLV_TYPE_FURTHER_AVAILABILITY_MAP,
                      calcNanFurtherAvailabilityMapSize(&pReq->fam_val),
                      (const u8*)(tlvs + SIZEOF_TLV_HDR), tlvs);
    }
    if (pReq->config_dw.config_2dot4g_dw_band) {
        tlvs = addTlv(NAN_TLV_TYPE_2G_COMMITTED_DW,
                      sizeof(pReq->config_dw.dw_2dot4g_interval_val),
                      (const u8*)&pReq->config_dw.dw_2dot4g_interval_val, tlvs);
    }
    if (pReq->config_dw.config_5g_dw_band) {
        tlvs = addTlv(NAN_TLV_TYPE_5G_COMMITTED_DW,
                      sizeof(pReq->config_dw.dw_5g_interval_val),
                      (const u8*)&pReq->config_dw.dw_5g_interval_val, tlvs);
    }
    if (pReq->config_disc_mac_addr_randomization) {
        tlvs = addTlv(NAN_TLV_TYPE_DISC_MAC_ADDR_RANDOM_INTERVAL,
                      sizeof(u32),
                      (const u8*)&pReq->disc_mac_addr_rand_interval_sec, tlvs);
    }
    if (pReq->config_subscribe_sid_beacon) {
        tlvs = addTlv(NAN_TLV_TYPE_SUBSCRIBE_SID_BEACON,
                      sizeof(pReq->subscribe_sid_beacon_val),
                      (const u8*)&pReq->subscribe_sid_beacon_val, tlvs);
    }

    u32 config_discovery_indications;
    config_discovery_indications = (u32)(pReq->discovery_indication_cfg);
    /* Always include the discovery cfg TLV as there is no cfg flag */
    tlvs = addTlv(NAN_TLV_TYPE_CONFIG_DISCOVERY_INDICATIONS,
                  sizeof(u32),
                  (const u8*)&config_discovery_indications, tlvs);

    mVendorData = (char*)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanPublish(transaction_id id, const NanPublishRequest *pReq)
{
    ALOGV("NAN_PUBLISH");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }

    size_t message_len =
        sizeof(NanMsgHeader) + sizeof(NanPublishServiceReqParams) +
        (pReq->service_name_len ? SIZEOF_TLV_HDR + pReq->service_name_len : 0) +
        (pReq->service_specific_info_len ? SIZEOF_TLV_HDR + pReq->service_specific_info_len : 0) +
        (pReq->rx_match_filter_len ? SIZEOF_TLV_HDR + pReq->rx_match_filter_len : 0) +
        (pReq->tx_match_filter_len ? SIZEOF_TLV_HDR + pReq->tx_match_filter_len : 0) +
        (SIZEOF_TLV_HDR + sizeof(NanServiceAcceptPolicy)) +
        (pReq->cipher_type ? SIZEOF_TLV_HDR + sizeof(NanCsidType) : 0) +
        ((pReq->sdea_params.config_nan_data_path || pReq->sdea_params.security_cfg ||
          pReq->sdea_params.ranging_state || pReq->sdea_params.range_report) ?
          SIZEOF_TLV_HDR + sizeof(NanFWSdeaCtrlParams) : 0) +
        ((pReq->ranging_cfg.ranging_interval_msec || pReq->ranging_cfg.config_ranging_indications ||
          pReq->ranging_cfg.distance_ingress_cm || pReq->ranging_cfg.distance_egress_cm) ?
          SIZEOF_TLV_HDR + sizeof(NanFWRangeConfigParams) : 0) +
        ((pReq->range_response_cfg.publish_id ||
          pReq->range_response_cfg.ranging_response) ?
          SIZEOF_TLV_HDR + sizeof(NanFWRangeReqMsg) : 0)  +
        (pReq->sdea_service_specific_info_len ? SIZEOF_TLV_HDR + pReq->sdea_service_specific_info_len : 0);

    if ((pReq->key_info.key_type ==  NAN_SECURITY_KEY_INPUT_PMK) &&
        (pReq->key_info.body.pmk_info.pmk_len == NAN_PMK_INFO_LEN))
        message_len += SIZEOF_TLV_HDR + NAN_PMK_INFO_LEN;
    else if ((pReq->key_info.key_type ==  NAN_SECURITY_KEY_INPUT_PASSPHRASE) &&
             (pReq->key_info.body.passphrase_info.passphrase_len >=
              NAN_SECURITY_MIN_PASSPHRASE_LEN) &&
             (pReq->key_info.body.passphrase_info.passphrase_len <=
              NAN_SECURITY_MAX_PASSPHRASE_LEN))
        message_len += SIZEOF_TLV_HDR +
                       pReq->key_info.body.passphrase_info.passphrase_len;

    pNanPublishServiceReqMsg pFwReq = (pNanPublishServiceReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset(pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_PUBLISH_SERVICE_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    if (pReq->publish_id == 0) {
        pFwReq->fwHeader.handle = 0xFFFF;
    } else {
        pFwReq->fwHeader.handle = pReq->publish_id;
    }
    pFwReq->fwHeader.transactionId = id;

    pFwReq->publishServiceReqParams.ttl = pReq->ttl;
    pFwReq->publishServiceReqParams.period = pReq->period;
    pFwReq->publishServiceReqParams.replyIndFlag =
                                   (pReq->recv_indication_cfg & BIT_3) ? 0 : 1;
    pFwReq->publishServiceReqParams.publishType = pReq->publish_type;
    pFwReq->publishServiceReqParams.txType = pReq->tx_type;

    pFwReq->publishServiceReqParams.rssiThresholdFlag = pReq->rssi_threshold_flag;
    pFwReq->publishServiceReqParams.matchAlg = pReq->publish_match_indicator;
    pFwReq->publishServiceReqParams.count = pReq->publish_count;
    pFwReq->publishServiceReqParams.connmap = pReq->connmap;
    pFwReq->publishServiceReqParams.pubTerminatedIndDisableFlag =
                                   (pReq->recv_indication_cfg & BIT_0) ? 1 : 0;
    pFwReq->publishServiceReqParams.pubMatchExpiredIndDisableFlag =
                                   (pReq->recv_indication_cfg & BIT_1) ? 1 : 0;
    pFwReq->publishServiceReqParams.followupRxIndDisableFlag =
                                   (pReq->recv_indication_cfg & BIT_2) ? 1 : 0;

    pFwReq->publishServiceReqParams.reserved2 = 0;

    u8* tlvs = pFwReq->ptlv;
    if (pReq->service_name_len) {
        tlvs = addTlv(NAN_TLV_TYPE_SERVICE_NAME, pReq->service_name_len,
                      (const u8*)&pReq->service_name[0], tlvs);
    }
    if (pReq->service_specific_info_len) {
        tlvs = addTlv(NAN_TLV_TYPE_SERVICE_SPECIFIC_INFO, pReq->service_specific_info_len,
                      (const u8*)&pReq->service_specific_info[0], tlvs);
    }
    if (pReq->rx_match_filter_len) {
        tlvs = addTlv(NAN_TLV_TYPE_RX_MATCH_FILTER, pReq->rx_match_filter_len,
                      (const u8*)&pReq->rx_match_filter[0], tlvs);
    }
    if (pReq->tx_match_filter_len) {
        tlvs = addTlv(NAN_TLV_TYPE_TX_MATCH_FILTER, pReq->tx_match_filter_len,
                      (const u8*)&pReq->tx_match_filter[0], tlvs);
    }

    /* Pass the Accept policy always */
    tlvs = addTlv(NAN_TLV_TYPE_NAN_SERVICE_ACCEPT_POLICY, sizeof(NanServiceAcceptPolicy),
                  (const u8*)&pReq->service_responder_policy, tlvs);

    if (pReq->cipher_type) {
        NanCsidType pNanCsidType;
        pNanCsidType.csid_type = pReq->cipher_type;
        tlvs = addTlv(NAN_TLV_TYPE_NAN_CSID, sizeof(NanCsidType),
                        (const u8*)&pNanCsidType, tlvs);
    }

    if ((pReq->key_info.key_type ==  NAN_SECURITY_KEY_INPUT_PMK) &&
        (pReq->key_info.body.pmk_info.pmk_len == NAN_PMK_INFO_LEN)) {
        tlvs = addTlv(NAN_TLV_TYPE_NAN_PMK,
                      pReq->key_info.body.pmk_info.pmk_len,
                      (const u8*)&pReq->key_info.body.pmk_info.pmk[0], tlvs);
    } else if ((pReq->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) &&
        (pReq->key_info.body.passphrase_info.passphrase_len >=
         NAN_SECURITY_MIN_PASSPHRASE_LEN) &&
        (pReq->key_info.body.passphrase_info.passphrase_len <=
         NAN_SECURITY_MAX_PASSPHRASE_LEN)) {
        tlvs = addTlv(NAN_TLV_TYPE_NAN_PASSPHRASE,
                  pReq->key_info.body.passphrase_info.passphrase_len,
                  (const u8*)&pReq->key_info.body.passphrase_info.passphrase[0],
                  tlvs);
    }

    if (pReq->sdea_params.config_nan_data_path ||
        pReq->sdea_params.security_cfg ||
        pReq->sdea_params.ranging_state ||
        pReq->sdea_params.range_report) {
        NanFWSdeaCtrlParams pNanFWSdeaCtrlParams;
        memset(&pNanFWSdeaCtrlParams, 0, sizeof(NanFWSdeaCtrlParams));

        if (pReq->sdea_params.config_nan_data_path) {
            pNanFWSdeaCtrlParams.data_path_required = 1;
            pNanFWSdeaCtrlParams.data_path_type =
                                  (pReq->sdea_params.ndp_type & BIT_0) ?
                                  NAN_DATA_PATH_MULTICAST_MSG :
                                  NAN_DATA_PATH_UNICAST_MSG;

        }
        if (pReq->sdea_params.security_cfg) {
            pNanFWSdeaCtrlParams.security_required =
                                         pReq->sdea_params.security_cfg;
        }
        if (pReq->sdea_params.ranging_state) {
            pNanFWSdeaCtrlParams.ranging_required =
                                         pReq->sdea_params.ranging_state;
        }
        if (pReq->sdea_params.range_report) {
            pNanFWSdeaCtrlParams.range_report =
                (((pReq->sdea_params.range_report & NAN_ENABLE_RANGE_REPORT) >> 1) ? 1 : 0);
        }
        tlvs = addTlv(NAN_TLV_TYPE_SDEA_CTRL_PARAMS, sizeof(NanFWSdeaCtrlParams),
                        (const u8*)&pNanFWSdeaCtrlParams, tlvs);
    }

    if (pReq->ranging_cfg.ranging_interval_msec ||
        pReq->ranging_cfg.config_ranging_indications ||
        pReq->ranging_cfg.distance_ingress_cm ||
        pReq->ranging_cfg.distance_ingress_cm) {
        NanFWRangeConfigParams pNanFWRangingCfg;

        memset(&pNanFWRangingCfg, 0, sizeof(NanFWRangeConfigParams));
        pNanFWRangingCfg.range_interval =
                                pReq->ranging_cfg.ranging_interval_msec;
        pNanFWRangingCfg.ranging_indication_event =
            ((pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_CONTINUOUS_MASK) |
            (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_INGRESS_MET_MASK) |
            (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_EGRESS_MET_MASK));

        pNanFWRangingCfg.ranging_indication_event = pReq->ranging_cfg.config_ranging_indications;
        if (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_INGRESS_MET_MASK)
            pNanFWRangingCfg.geo_fence_threshold.inner_threshold =
                                        pReq->ranging_cfg.distance_ingress_cm;
        if (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_EGRESS_MET_MASK)
            pNanFWRangingCfg.geo_fence_threshold.outer_threshold =
                                       pReq->ranging_cfg.distance_egress_cm;
        tlvs = addTlv(NAN_TLV_TYPE_NAN_RANGING_CFG, sizeof(NanFWRangeConfigParams),
                                                    (const u8*)&pNanFWRangingCfg, tlvs);
    }

    if (pReq->sdea_service_specific_info_len) {
        tlvs = addTlv(NAN_TLV_TYPE_SDEA_SERVICE_SPECIFIC_INFO, pReq->sdea_service_specific_info_len,
                      (const u8*)&pReq->sdea_service_specific_info[0], tlvs);
    }

    if (pReq->range_response_cfg.publish_id || pReq->range_response_cfg.ranging_response) {

        NanFWRangeReqMsg pNanFWRangeReqMsg;
        memset(&pNanFWRangeReqMsg, 0, sizeof(NanFWRangeReqMsg));
        pNanFWRangeReqMsg.range_id =
                                (u16)pReq->range_response_cfg.publish_id;
        CHAR_ARRAY_TO_MAC_ADDR(pReq->range_response_cfg.peer_addr, pNanFWRangeReqMsg.range_mac_addr);
        pNanFWRangeReqMsg.ranging_accept =
            ((pReq->range_response_cfg.ranging_response == NAN_RANGE_REQUEST_ACCEPT) ? 1 : 0);
        pNanFWRangeReqMsg.ranging_reject =
            ((pReq->range_response_cfg.ranging_response == NAN_RANGE_REQUEST_REJECT) ? 1 : 0);
        pNanFWRangeReqMsg.ranging_cancel =
            ((pReq->range_response_cfg.ranging_response == NAN_RANGE_REQUEST_CANCEL) ? 1 : 0);
        tlvs = addTlv(NAN_TLV_TYPE_NAN20_RANGING_REQUEST, sizeof(NanFWRangeReqMsg),
                                                    (const u8*)&pNanFWRangeReqMsg, tlvs);
    }

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanPublishCancel(transaction_id id, const NanPublishCancelRequest *pReq)
{
    ALOGV("NAN_PUBLISH_CANCEL");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }
    size_t message_len = sizeof(NanPublishServiceCancelReqMsg);

    pNanPublishServiceCancelReqMsg pFwReq =
        (pNanPublishServiceCancelReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset(pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_PUBLISH_SERVICE_CANCEL_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.handle = pReq->publish_id;
    pFwReq->fwHeader.transactionId = id;

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanSubscribe(transaction_id id,
                                const NanSubscribeRequest *pReq)
{

    ALOGV("NAN_SUBSCRIBE");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }

    size_t message_len =
        sizeof(NanMsgHeader) + sizeof(NanSubscribeServiceReqParams) +
        (pReq->service_name_len ? SIZEOF_TLV_HDR + pReq->service_name_len : 0) +
        (pReq->service_specific_info_len ? SIZEOF_TLV_HDR + pReq->service_specific_info_len : 0) +
        (pReq->rx_match_filter_len ? SIZEOF_TLV_HDR + pReq->rx_match_filter_len : 0) +
        (pReq->tx_match_filter_len ? SIZEOF_TLV_HDR + pReq->tx_match_filter_len : 0) +
        (pReq->cipher_type ? SIZEOF_TLV_HDR + sizeof(NanCsidType) : 0) +
        ((pReq->sdea_params.config_nan_data_path || pReq->sdea_params.security_cfg ||
          pReq->sdea_params.ranging_state || pReq->sdea_params.range_report) ?
          SIZEOF_TLV_HDR + sizeof(NanFWSdeaCtrlParams) : 0) +
        ((pReq->ranging_cfg.ranging_interval_msec || pReq->ranging_cfg.config_ranging_indications ||
          pReq->ranging_cfg.distance_ingress_cm || pReq->ranging_cfg.distance_egress_cm) ?
          SIZEOF_TLV_HDR + sizeof(NanFWRangeConfigParams) : 0) +
        ((pReq->range_response_cfg.requestor_instance_id ||
          pReq->range_response_cfg.ranging_response) ?
          SIZEOF_TLV_HDR + sizeof(NanFWRangeReqMsg) : 0) +
        (pReq->sdea_service_specific_info_len ? SIZEOF_TLV_HDR + pReq->sdea_service_specific_info_len : 0);

    message_len += \
        (pReq->num_intf_addr_present * (SIZEOF_TLV_HDR + NAN_MAC_ADDR_LEN));


    if ((pReq->key_info.key_type ==  NAN_SECURITY_KEY_INPUT_PMK) &&
        (pReq->key_info.body.pmk_info.pmk_len == NAN_PMK_INFO_LEN))
        message_len += SIZEOF_TLV_HDR + NAN_PMK_INFO_LEN;
    else if ((pReq->key_info.key_type ==  NAN_SECURITY_KEY_INPUT_PASSPHRASE) &&
             (pReq->key_info.body.passphrase_info.passphrase_len >=
              NAN_SECURITY_MIN_PASSPHRASE_LEN) &&
             (pReq->key_info.body.passphrase_info.passphrase_len <=
              NAN_SECURITY_MAX_PASSPHRASE_LEN))
        message_len += SIZEOF_TLV_HDR +
                       pReq->key_info.body.passphrase_info.passphrase_len;


    pNanSubscribeServiceReqMsg pFwReq = (pNanSubscribeServiceReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset(pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_SUBSCRIBE_SERVICE_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    if (pReq->subscribe_id == 0) {
        pFwReq->fwHeader.handle = 0xFFFF;
    } else {
        pFwReq->fwHeader.handle = pReq->subscribe_id;
    }
    pFwReq->fwHeader.transactionId = id;

    pFwReq->subscribeServiceReqParams.ttl = pReq->ttl;
    pFwReq->subscribeServiceReqParams.period = pReq->period;
    pFwReq->subscribeServiceReqParams.subscribeType = pReq->subscribe_type;
    pFwReq->subscribeServiceReqParams.srfAttr = pReq->serviceResponseFilter;
    pFwReq->subscribeServiceReqParams.srfInclude = pReq->serviceResponseInclude;
    pFwReq->subscribeServiceReqParams.srfSend = pReq->useServiceResponseFilter;
    pFwReq->subscribeServiceReqParams.ssiRequired = pReq->ssiRequiredForMatchIndication;
    pFwReq->subscribeServiceReqParams.matchAlg = pReq->subscribe_match_indicator;
    pFwReq->subscribeServiceReqParams.count = pReq->subscribe_count;
    pFwReq->subscribeServiceReqParams.rssiThresholdFlag = pReq->rssi_threshold_flag;
    pFwReq->subscribeServiceReqParams.subTerminatedIndDisableFlag =
                                   (pReq->recv_indication_cfg & BIT_0) ? 1 : 0;
    pFwReq->subscribeServiceReqParams.subMatchExpiredIndDisableFlag =
                                   (pReq->recv_indication_cfg & BIT_1) ? 1 : 0;
    pFwReq->subscribeServiceReqParams.followupRxIndDisableFlag =
                                   (pReq->recv_indication_cfg & BIT_2) ? 1 : 0;
    pFwReq->subscribeServiceReqParams.connmap = pReq->connmap;
    pFwReq->subscribeServiceReqParams.reserved = 0;

    u8* tlvs = pFwReq->ptlv;
    if (pReq->service_name_len) {
        tlvs = addTlv(NAN_TLV_TYPE_SERVICE_NAME, pReq->service_name_len,
                      (const u8*)&pReq->service_name[0], tlvs);
    }
    if (pReq->service_specific_info_len) {
        tlvs = addTlv(NAN_TLV_TYPE_SERVICE_SPECIFIC_INFO, pReq->service_specific_info_len,
                      (const u8*)&pReq->service_specific_info[0], tlvs);
    }
    if (pReq->rx_match_filter_len) {
        tlvs = addTlv(NAN_TLV_TYPE_RX_MATCH_FILTER, pReq->rx_match_filter_len,
                      (const u8*)&pReq->rx_match_filter[0], tlvs);
    }
    if (pReq->tx_match_filter_len) {
        tlvs = addTlv(NAN_TLV_TYPE_TX_MATCH_FILTER, pReq->tx_match_filter_len,
                      (const u8*)&pReq->tx_match_filter[0], tlvs);
    }

    int i = 0;
    for (i = 0; i < pReq->num_intf_addr_present; i++)
    {
        tlvs = addTlv(NAN_TLV_TYPE_MAC_ADDRESS,
                      NAN_MAC_ADDR_LEN,
                      (const u8*)&pReq->intf_addr[i][0], tlvs);
    }

    if (pReq->cipher_type) {
        NanCsidType pNanCsidType;
        pNanCsidType.csid_type = pReq->cipher_type;
        tlvs = addTlv(NAN_TLV_TYPE_NAN_CSID, sizeof(NanCsidType),
                        (const u8*)&pNanCsidType, tlvs);
    }

    if ((pReq->key_info.key_type ==  NAN_SECURITY_KEY_INPUT_PMK) &&
        (pReq->key_info.body.pmk_info.pmk_len == NAN_PMK_INFO_LEN)) {
        tlvs = addTlv(NAN_TLV_TYPE_NAN_PMK,
                      pReq->key_info.body.pmk_info.pmk_len,
                      (const u8*)&pReq->key_info.body.pmk_info.pmk[0], tlvs);
    } else if ((pReq->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) &&
        (pReq->key_info.body.passphrase_info.passphrase_len >=
         NAN_SECURITY_MIN_PASSPHRASE_LEN) &&
        (pReq->key_info.body.passphrase_info.passphrase_len <=
         NAN_SECURITY_MAX_PASSPHRASE_LEN)) {
        tlvs = addTlv(NAN_TLV_TYPE_NAN_PASSPHRASE,
                  pReq->key_info.body.passphrase_info.passphrase_len,
                  (const u8*)&pReq->key_info.body.passphrase_info.passphrase[0],
                  tlvs);
    }

    if (pReq->sdea_params.config_nan_data_path ||
        pReq->sdea_params.security_cfg ||
        pReq->sdea_params.ranging_state ||
        pReq->sdea_params.range_report) {
        NanFWSdeaCtrlParams pNanFWSdeaCtrlParams;
        memset(&pNanFWSdeaCtrlParams, 0, sizeof(NanFWSdeaCtrlParams));

        if (pReq->sdea_params.config_nan_data_path) {
            pNanFWSdeaCtrlParams.data_path_required = 1;
            pNanFWSdeaCtrlParams.data_path_type =
                                  (pReq->sdea_params.ndp_type & BIT_0) ?
                                  NAN_DATA_PATH_MULTICAST_MSG :
                                  NAN_DATA_PATH_UNICAST_MSG;

        }
        if (pReq->sdea_params.security_cfg) {
            pNanFWSdeaCtrlParams.security_required =
                                         pReq->sdea_params.security_cfg;
        }
        if (pReq->sdea_params.ranging_state) {
            pNanFWSdeaCtrlParams.ranging_required =
                                         pReq->sdea_params.ranging_state;
        }
        if (pReq->sdea_params.range_report) {
            pNanFWSdeaCtrlParams.range_report =
                ((pReq->sdea_params.range_report & NAN_ENABLE_RANGE_REPORT >> 1) ? 1 : 0);
        }
        tlvs = addTlv(NAN_TLV_TYPE_SDEA_CTRL_PARAMS, sizeof(NanFWSdeaCtrlParams),
                        (const u8*)&pNanFWSdeaCtrlParams, tlvs);

    }

    if (pReq->ranging_cfg.ranging_interval_msec || pReq->ranging_cfg.config_ranging_indications || pReq->ranging_cfg.distance_ingress_cm
        || pReq->ranging_cfg.distance_ingress_cm) {
        NanFWRangeConfigParams pNanFWRangingCfg;
        memset(&pNanFWRangingCfg, 0, sizeof(NanFWRangeConfigParams));
        pNanFWRangingCfg.range_interval =
                                pReq->ranging_cfg.ranging_interval_msec;
        pNanFWRangingCfg.ranging_indication_event =
            ((pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_CONTINUOUS_MASK) |
            (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_INGRESS_MET_MASK) |
            (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_EGRESS_MET_MASK));

        pNanFWRangingCfg.ranging_indication_event =
                                          pReq->ranging_cfg.config_ranging_indications;
        if (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_INGRESS_MET_MASK)
            pNanFWRangingCfg.geo_fence_threshold.inner_threshold =
                                        pReq->ranging_cfg.distance_ingress_cm;
        if (pReq->ranging_cfg.config_ranging_indications & NAN_RANGING_INDICATE_EGRESS_MET_MASK)
            pNanFWRangingCfg.geo_fence_threshold.outer_threshold =
                                       pReq->ranging_cfg.distance_egress_cm;
        tlvs = addTlv(NAN_TLV_TYPE_NAN_RANGING_CFG, sizeof(NanFWRangeConfigParams),
                                                    (const u8*)&pNanFWRangingCfg, tlvs);
    }

    if (pReq->sdea_service_specific_info_len) {
        tlvs = addTlv(NAN_TLV_TYPE_SDEA_SERVICE_SPECIFIC_INFO, pReq->sdea_service_specific_info_len,
                      (const u8*)&pReq->sdea_service_specific_info[0], tlvs);
    }

    if (pReq->range_response_cfg.requestor_instance_id || pReq->range_response_cfg.ranging_response) {
        NanFWRangeReqMsg pNanFWRangeReqMsg;
        memset(&pNanFWRangeReqMsg, 0, sizeof(NanFWRangeReqMsg));
        pNanFWRangeReqMsg.range_id =
                                pReq->range_response_cfg.requestor_instance_id;
        memcpy(&pNanFWRangeReqMsg.range_mac_addr, &pReq->range_response_cfg.peer_addr, NAN_MAC_ADDR_LEN);
        pNanFWRangeReqMsg.ranging_accept =
            ((pReq->range_response_cfg.ranging_response == NAN_RANGE_REQUEST_ACCEPT) ? 1 : 0);
        pNanFWRangeReqMsg.ranging_reject =
            ((pReq->range_response_cfg.ranging_response == NAN_RANGE_REQUEST_REJECT) ? 1 : 0);
        pNanFWRangeReqMsg.ranging_cancel =
            ((pReq->range_response_cfg.ranging_response == NAN_RANGE_REQUEST_CANCEL) ? 1 : 0);
        tlvs = addTlv(NAN_TLV_TYPE_NAN20_RANGING_REQUEST, sizeof(NanFWRangeReqMsg),
                                                    (const u8*)&pNanFWRangeReqMsg, tlvs);
    }

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;
    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanSubscribeCancel(transaction_id id,
                                      const NanSubscribeCancelRequest *pReq)
{
    ALOGV("NAN_SUBSCRIBE_CANCEL");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }
    size_t message_len = sizeof(NanSubscribeServiceCancelReqMsg);

    pNanSubscribeServiceCancelReqMsg pFwReq =
        (pNanSubscribeServiceCancelReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset(pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_SUBSCRIBE_SERVICE_CANCEL_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.handle = pReq->subscribe_id;
    pFwReq->fwHeader.transactionId = id;

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;
    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanTransmitFollowup(transaction_id id,
                                       const NanTransmitFollowupRequest *pReq)
{
    ALOGV("TRANSMIT_FOLLOWUP");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }

    size_t message_len =
        sizeof(NanMsgHeader) + sizeof(NanTransmitFollowupReqParams) +
        (pReq->service_specific_info_len ? SIZEOF_TLV_HDR +
         pReq->service_specific_info_len : 0) +
        (pReq->sdea_service_specific_info_len ? SIZEOF_TLV_HDR + pReq->sdea_service_specific_info_len : 0);

    /* Mac address needs to be added in TLV */
    message_len += (SIZEOF_TLV_HDR + sizeof(pReq->addr));

    pNanTransmitFollowupReqMsg pFwReq = (pNanTransmitFollowupReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset (pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_TRANSMIT_FOLLOWUP_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.handle = pReq->publish_subscribe_id;
    pFwReq->fwHeader.transactionId = id;

    pFwReq->transmitFollowupReqParams.matchHandle = pReq->requestor_instance_id;
    if (pReq->priority != NAN_TX_PRIORITY_HIGH) {
        pFwReq->transmitFollowupReqParams.priority = 1;
    } else {
        pFwReq->transmitFollowupReqParams.priority = 2;
    }
    pFwReq->transmitFollowupReqParams.window = pReq->dw_or_faw;
    pFwReq->transmitFollowupReqParams.followupTxRspDisableFlag =
                                   (pReq->recv_indication_cfg & BIT_0) ? 1 : 0;
    pFwReq->transmitFollowupReqParams.reserved = 0;

    u8* tlvs = pFwReq->ptlv;

    /* Mac address needs to be added in TLV */
    tlvs = addTlv(NAN_TLV_TYPE_MAC_ADDRESS, sizeof(pReq->addr),
                  (const u8*)&pReq->addr[0], tlvs);
    u16 tlv_type = NAN_TLV_TYPE_SERVICE_SPECIFIC_INFO;

    if (pReq->service_specific_info_len) {
        tlvs = addTlv(tlv_type, pReq->service_specific_info_len,
                      (const u8*)&pReq->service_specific_info[0], tlvs);
    }

    if (pReq->sdea_service_specific_info_len) {
        tlvs = addTlv(NAN_TLV_TYPE_SDEA_SERVICE_SPECIFIC_INFO, pReq->sdea_service_specific_info_len,
                      (const u8*)&pReq->sdea_service_specific_info[0], tlvs);
    }

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanStats(transaction_id id, const NanStatsRequest *pReq)
{
    ALOGV("NAN_STATS");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }
    size_t message_len = sizeof(NanStatsReqMsg);

    pNanStatsReqMsg pFwReq =
        (pNanStatsReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset(pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_STATS_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = id;

    pFwReq->statsReqParams.statsType = pReq->stats_type;
    pFwReq->statsReqParams.clear = pReq->clear;
    pFwReq->statsReqParams.reserved = 0;

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanTCA(transaction_id id, const NanTCARequest *pReq)
{
    ALOGV("NAN_TCA");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }
    size_t message_len = sizeof(NanTcaReqMsg);

    message_len += (SIZEOF_TLV_HDR + 2 * sizeof(u32));
    pNanTcaReqMsg pFwReq =
        (pNanTcaReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset(pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_TCA_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = id;

    u32 tcaReqParams[2];
    memset (tcaReqParams, 0, sizeof(tcaReqParams));
    tcaReqParams[0] = (pReq->rising_direction_evt_flag & 0x01);
    tcaReqParams[0] |= (pReq->falling_direction_evt_flag & 0x01) << 1;
    tcaReqParams[0] |= (pReq->clear & 0x01) << 2;
    tcaReqParams[1] = pReq->threshold;

    u8* tlvs = pFwReq->ptlv;

    if (pReq->tca_type == NAN_TCA_ID_CLUSTER_SIZE) {
        tlvs = addTlv(NAN_TLV_TYPE_CLUSTER_SIZE_REQ, sizeof(tcaReqParams),
                      (const u8*)&tcaReqParams[0], tlvs);
    } else {
        ALOGE("%s: Unrecognized tca_type:%u", __FUNCTION__, pReq->tca_type);
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanBeaconSdfPayload(transaction_id id,
                                       const NanBeaconSdfPayloadRequest *pReq)
{
    ALOGV("NAN_BEACON_SDF_PAYLAOD");
    if (pReq == NULL) {
        cleanup();
        return WIFI_ERROR_INVALID_ARGS;
    }
    size_t message_len = sizeof(NanMsgHeader) + \
        SIZEOF_TLV_HDR + sizeof(u32) + \
        pReq->vsa.vsa_len;

    pNanBeaconSdfPayloadReqMsg pFwReq =
        (pNanBeaconSdfPayloadReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu", message_len);
    memset(pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_BEACON_SDF_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = id;

    /* Construct First 4 bytes of NanBeaconSdfPayloadReqMsg */
    u32 temp = 0;
    temp = pReq->vsa.payload_transmit_flag & 0x01;
    temp |= (pReq->vsa.tx_in_discovery_beacon & 0x01) << 1;
    temp |= (pReq->vsa.tx_in_sync_beacon & 0x01) << 2;
    temp |= (pReq->vsa.tx_in_service_discovery & 0x01) << 3;
    temp |= (pReq->vsa.vendor_oui & 0x00FFFFFF) << 8;

    int tlv_len = sizeof(u32) + pReq->vsa.vsa_len;
    u8* tempBuf = (u8*)malloc(tlv_len);
    if (tempBuf == NULL) {
        ALOGE("%s: Malloc failed", __func__);
        free(pFwReq);
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    memset(tempBuf, 0, tlv_len);
    memcpy(tempBuf, &temp, sizeof(u32));
    memcpy((tempBuf + sizeof(u32)), pReq->vsa.vsa, pReq->vsa.vsa_len);

    u8* tlvs = pFwReq->ptlv;

    /* Write the TLVs to the message. */
    tlvs = addTlv(NAN_TLV_TYPE_VENDOR_SPECIFIC_ATTRIBUTE_TRANSMIT, tlv_len,
                  (const u8*)tempBuf, tlvs);
    free(tempBuf);

    mVendorData = (char *)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

//callback handlers registered for nl message send
static int error_handler_nan(struct sockaddr_nl *nla, struct nlmsgerr *err,
                         void *arg)
{
    struct sockaddr_nl * tmp;
    int *ret = (int *)arg;
    tmp = nla;
    *ret = err->error;
    ALOGE("%s: Error code:%d (%s)", __func__, *ret, strerror(-(*ret)));
    return NL_STOP;
}

//callback handlers registered for nl message send
static int ack_handler_nan(struct nl_msg *msg, void *arg)
{
    int *ret = (int *)arg;
    struct nl_msg * a;

    ALOGE("%s: called", __func__);
    a = msg;
    *ret = 0;
    return NL_STOP;
}

//callback handlers registered for nl message send
static int finish_handler_nan(struct nl_msg *msg, void *arg)
{
  int *ret = (int *)arg;
  struct nl_msg * a;

  ALOGE("%s: called", __func__);
  a = msg;
  *ret = 0;
  return NL_SKIP;
}


//Override base class requestEvent and implement little differently here
//This will send the request message
//We dont wait for any response back in case of Nan as it is asynchronous
//thus no wait for condition.
int NanCommand::requestEvent()
{
    int res;
    struct nl_cb * cb;

    cb = nl_cb_alloc(NL_CB_DEFAULT);
    if (!cb) {
        ALOGE("%s: Callback allocation failed",__func__);
        res = -1;
        goto out;
    }

    if (!mInfo->cmd_sock) {
        ALOGE("%s: Command socket is null",__func__);
        res = -1;
        goto out;
    }

    /* send message */
    ALOGV("%s:Handle:%p Socket Value:%p", __func__, mInfo, mInfo->cmd_sock);
    res = nl_send_auto_complete(mInfo->cmd_sock, mMsg.getMessage());
    if (res < 0)
        goto out;
    res = 1;

    nl_cb_err(cb, NL_CB_CUSTOM, error_handler_nan, &res);
    nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler_nan, &res);
    nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler_nan, &res);

    // err is populated as part of finish_handler
    while (res > 0)
        nl_recvmsgs(mInfo->cmd_sock, cb);

out:
    //free the VendorData
    if (mVendorData) {
        free(mVendorData);
    }
    mVendorData = NULL;
    //cleanup the mMsg
    mMsg.destroy();
    return res;
}

int NanCommand::calcNanTransmitPostDiscoverySize(
    const NanTransmitPostDiscovery *pPostDiscovery)
{
    /* Fixed size of u32 for Conn Type, Device Role and R flag + Dur + Rsvd*/
    int ret = sizeof(u32);
    /* size of availability interval bit map is 4 bytes */
    ret += sizeof(u32);
    /* size of mac address is 6 bytes*/
    ret += (SIZEOF_TLV_HDR + NAN_MAC_ADDR_LEN);
    if (pPostDiscovery &&
        pPostDiscovery->type == NAN_CONN_WLAN_MESH) {
        /* size of WLAN_MESH_ID  */
        ret += (SIZEOF_TLV_HDR + \
                pPostDiscovery->mesh_id_len);
    }
    if (pPostDiscovery &&
        pPostDiscovery->type == NAN_CONN_WLAN_INFRA) {
        /* size of Infrastructure ssid  */
        ret += (SIZEOF_TLV_HDR + \
                pPostDiscovery->infrastructure_ssid_len);
    }
    ALOGV("%s:size:%d", __func__, ret);
    return ret;
}

void NanCommand::fillNanSocialChannelParamVal(
    const NanSocialChannelScanParams *pScanParams,
    u32* pChannelParamArr)
{
    int i;
    if (pChannelParamArr) {
        memset(pChannelParamArr, 0,
               NAN_MAX_SOCIAL_CHANNELS * sizeof(u32));
        for (i= 0; i < NAN_MAX_SOCIAL_CHANNELS; i++) {
            pChannelParamArr[i] = pScanParams->scan_period[i] << 16;
            pChannelParamArr[i] |= pScanParams->dwell_time[i] << 8;
        }
        pChannelParamArr[NAN_CHANNEL_24G_BAND] |= 6;
        pChannelParamArr[NAN_CHANNEL_5G_BAND_LOW]|= 44;
        pChannelParamArr[NAN_CHANNEL_5G_BAND_HIGH]|= 149;
        ALOGV("%s: Filled SocialChannelParamVal", __func__);
        hexdump((char*)pChannelParamArr, NAN_MAX_SOCIAL_CHANNELS * sizeof(u32));
    }
    return;
}

u32 NanCommand::getNanTransmitPostConnectivityCapabilityVal(
    const NanTransmitPostConnectivityCapability *pCapab)
{
    u32 ret = 0;
    ret |= (pCapab->payload_transmit_flag? 1:0) << 16;
    ret |= (pCapab->is_mesh_supported? 1:0) << 5;
    ret |= (pCapab->is_ibss_supported? 1:0) << 4;
    ret |= (pCapab->wlan_infra_field? 1:0) << 3;
    ret |= (pCapab->is_tdls_supported? 1:0) << 2;
    ret |= (pCapab->is_wfds_supported? 1:0) << 1;
    ret |= (pCapab->is_wfd_supported? 1:0);
    ALOGV("%s: val:%d", __func__, ret);
    return ret;
}

void NanCommand::fillNanTransmitPostDiscoveryVal(
    const NanTransmitPostDiscovery *pTxDisc,
    u8 *pOutValue)
{

    if (pTxDisc && pOutValue) {
        u8 *tlvs = &pOutValue[8];
        pOutValue[0] = pTxDisc->type;
        pOutValue[1] = pTxDisc->role;
        pOutValue[2] = (pTxDisc->transmit_freq? 1:0);
        pOutValue[2] |= ((pTxDisc->duration & 0x03) << 1);
        memcpy(&pOutValue[4], &pTxDisc->avail_interval_bitmap,
               sizeof(pTxDisc->avail_interval_bitmap));
        tlvs = addTlv(NAN_TLV_TYPE_MAC_ADDRESS,
                    NAN_MAC_ADDR_LEN,
                    (const u8*)&pTxDisc->addr[0],
                    tlvs);
        if (pTxDisc->type == NAN_CONN_WLAN_MESH) {
            tlvs = addTlv(NAN_TLV_TYPE_WLAN_MESH_ID,
                        pTxDisc->mesh_id_len,
                        (const u8*)&pTxDisc->mesh_id[0],
                        tlvs);
        }
        if (pTxDisc->type == NAN_CONN_WLAN_INFRA) {
            tlvs = addTlv(NAN_TLV_TYPE_WLAN_INFRA_SSID,
                        pTxDisc->infrastructure_ssid_len,
                        (const u8*)&pTxDisc->infrastructure_ssid_val[0],
                        tlvs);
        }
        ALOGV("%s: Filled TransmitPostDiscoveryVal", __func__);
        hexdump((char*)pOutValue, calcNanTransmitPostDiscoverySize(pTxDisc));
    }

    return;
}

void NanCommand::fillNanFurtherAvailabilityMapVal(
    const NanFurtherAvailabilityMap *pFam,
    u8 *pOutValue)
{
    int idx = 0;

    if (pFam && pOutValue) {
        u32 famsize = calcNanFurtherAvailabilityMapSize(pFam);
        pNanFurtherAvailabilityMapAttrTlv pFwReq = \
            (pNanFurtherAvailabilityMapAttrTlv)pOutValue;

        memset(pOutValue, 0, famsize);
        pFwReq->numChan = pFam->numchans;
        for (idx = 0; idx < pFam->numchans; idx++) {
            const NanFurtherAvailabilityChannel *pFamChan =  \
                &pFam->famchan[idx];
            pNanFurtherAvailabilityChan pFwFamChan = \
                (pNanFurtherAvailabilityChan)((u8*)&pFwReq->pFaChan[0] + \
                (idx * sizeof(NanFurtherAvailabilityChan)));

            pFwFamChan->entryCtrl.availIntDuration = \
                pFamChan->entry_control;
            pFwFamChan->entryCtrl.mapId = \
                pFamChan->mapid;
            pFwFamChan->opClass =  pFamChan->class_val;
            pFwFamChan->channel = pFamChan->channel;
            memcpy(&pFwFamChan->availIntBitmap,
                   &pFamChan->avail_interval_bitmap,
                   sizeof(pFwFamChan->availIntBitmap));
        }
        ALOGV("%s: Filled FurtherAvailabilityMapVal", __func__);
        hexdump((char*)pOutValue, famsize);
    }
    return;
}

int NanCommand::calcNanFurtherAvailabilityMapSize(
    const NanFurtherAvailabilityMap *pFam)
{
    int ret = 0;
    if (pFam && pFam->numchans &&
        pFam->numchans <= NAN_MAX_FAM_CHANNELS) {
        /* Fixed size of u8 for numchans*/
        ret = sizeof(u8);
        /* numchans * sizeof(FamChannels) */
        ret += (pFam->numchans * sizeof(NanFurtherAvailabilityChan));
    }
    ALOGV("%s:size:%d", __func__, ret);
    return ret;
}

int NanCommand::putNanCapabilities(transaction_id id)
{
    ALOGV("NAN_CAPABILITIES");
    size_t message_len = sizeof(NanCapabilitiesReqMsg);

    pNanCapabilitiesReqMsg pFwReq = (pNanCapabilitiesReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    memset (pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_CAPABILITIES_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = id;

    mVendorData = (char*)pFwReq;
    mDataLen = message_len;

    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}

int NanCommand::putNanDebugCommand(NanDebugParams debug,
                                   int debug_msg_length)
{
    ALOGV("NAN_AVAILABILITY_DEBUG");
    size_t message_len = sizeof(NanTestModeReqMsg);

    message_len += (SIZEOF_TLV_HDR + debug_msg_length);
    pNanTestModeReqMsg pFwReq = (pNanTestModeReqMsg)malloc(message_len);
    if (pFwReq == NULL) {
        cleanup();
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ALOGV("Message Len %zu\n", message_len);
    ALOGV("%s: Debug Command Type = 0x%x \n", __func__, debug.cmd);
    ALOGV("%s: ** Debug Command Data Start **", __func__);
    hexdump(debug.debug_cmd_data, debug_msg_length);
    ALOGV("%s: ** Debug Command Data End **", __func__);

    memset (pFwReq, 0, message_len);
    pFwReq->fwHeader.msgVersion = (u16)NAN_MSG_VERSION1;
    pFwReq->fwHeader.msgId = NAN_MSG_ID_TESTMODE_REQ;
    pFwReq->fwHeader.msgLen = message_len;
    pFwReq->fwHeader.transactionId = 0;

    u8* tlvs = pFwReq->ptlv;
    tlvs = addTlv(NAN_TLV_TYPE_TESTMODE_GENERIC_CMD, debug_msg_length,
                  (const u8*)&debug, tlvs);

    mVendorData = (char*)pFwReq;
    mDataLen = message_len;

    /* Write the TLVs to the message. */
    int ret = mMsg.put_bytes(NL80211_ATTR_VENDOR_DATA, mVendorData, mDataLen);
    if (ret < 0) {
        ALOGE("%s: put_bytes Error:%d",__func__, ret);
        cleanup();
        return ret;
    }
    hexdump(mVendorData, mDataLen);
    return ret;
}
