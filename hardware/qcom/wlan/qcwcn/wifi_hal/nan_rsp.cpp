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


int NanCommand::isNanResponse()
{
    if (mNanVendorEvent == NULL) {
        ALOGE("NULL check failed");
        return WIFI_ERROR_INVALID_ARGS;
    }

    NanMsgHeader *pHeader = (NanMsgHeader *)mNanVendorEvent;

    switch (pHeader->msgId) {
    case NAN_MSG_ID_ERROR_RSP:
    case NAN_MSG_ID_CONFIGURATION_RSP:
    case NAN_MSG_ID_PUBLISH_SERVICE_CANCEL_RSP:
    case NAN_MSG_ID_PUBLISH_SERVICE_RSP:
    case NAN_MSG_ID_SUBSCRIBE_SERVICE_RSP:
    case NAN_MSG_ID_SUBSCRIBE_SERVICE_CANCEL_RSP:
    case NAN_MSG_ID_TRANSMIT_FOLLOWUP_RSP:
    case NAN_MSG_ID_STATS_RSP:
    case NAN_MSG_ID_ENABLE_RSP:
    case NAN_MSG_ID_DISABLE_RSP:
    case NAN_MSG_ID_TCA_RSP:
    case NAN_MSG_ID_BEACON_SDF_RSP:
    case NAN_MSG_ID_CAPABILITIES_RSP:
    case NAN_MSG_ID_TESTMODE_RSP:
        return 1;
    default:
        return 0;
    }
}

struct verboseTlv {
    NanTlvType tlvType;
    char strTlv[NAN_ERROR_STR_LEN];
};

struct verboseTlv tlvToStr[] = {
    {NAN_TLV_TYPE_SDF_MATCH_FILTER, " SDF match filter"},
    {NAN_TLV_TYPE_TX_MATCH_FILTER, " Tx match filter"},
    {NAN_TLV_TYPE_RX_MATCH_FILTER, " Rx match filter"},
    {NAN_TLV_TYPE_SERVICE_SPECIFIC_INFO,
     " Service specific info"},
    {NAN_TLV_TYPE_EXT_SERVICE_SPECIFIC_INFO,
     " Extended Service specific info"},
    {NAN_TLV_TYPE_VENDOR_SPECIFIC_ATTRIBUTE_TRANSMIT,
     " Vendor specific attribute transmit"},
    {NAN_TLV_TYPE_VENDOR_SPECIFIC_ATTRIBUTE_RECEIVE,
     " Vendor specific attribute receive"},
    {NAN_TLV_TYPE_POST_NAN_CONNECTIVITY_CAPABILITIES_RECEIVE,
     " Post Nan connectivity capability receive"},
    {NAN_TLV_TYPE_POST_NAN_DISCOVERY_ATTRIBUTE_RECEIVE,
     " Post Nan discovery attribute receive"},
    {NAN_TLV_TYPE_BEACON_SDF_PAYLOAD_RECEIVE,
     " Beacon SDF payload receive"},

    /* Configuration types */
    {NAN_TLV_TYPE_CONFIG_FIRST, " Config first"},
    {NAN_TLV_TYPE_24G_SUPPORT, " 2.4G support"},
    {NAN_TLV_TYPE_24G_BEACON, " 2.4G beacon"},
    {NAN_TLV_TYPE_24G_SDF, " 2.4G SDF"},
    {NAN_TLV_TYPE_24G_RSSI_CLOSE, " 2.4G RSSI close"},
    {NAN_TLV_TYPE_24G_RSSI_MIDDLE, " 2.4G RSSI middle"},
    {NAN_TLV_TYPE_24G_RSSI_CLOSE_PROXIMITY,
     " 2.4G RSSI close proximity"},
    {NAN_TLV_TYPE_5G_SUPPORT, " 5G support"},
    {NAN_TLV_TYPE_5G_BEACON, " 5G beacon"},
    {NAN_TLV_TYPE_5G_SDF, " 5G SDF"},
    {NAN_TLV_TYPE_5G_RSSI_CLOSE, " 5G RSSI close"},
    {NAN_TLV_TYPE_5G_RSSI_MIDDLE, " 5G RSSI middle"},
    {NAN_TLV_TYPE_5G_RSSI_CLOSE_PROXIMITY,
     " 5G RSSI close proximity"},
    {NAN_TLV_TYPE_SID_BEACON, " SID beacon"},
    {NAN_TLV_TYPE_HOP_COUNT_LIMIT, " Hop count limit"},
    {NAN_TLV_TYPE_MASTER_PREFERENCE, " Master preference"},
    {NAN_TLV_TYPE_CLUSTER_ID_LOW, " Cluster ID low"},
    {NAN_TLV_TYPE_CLUSTER_ID_HIGH, " Cluster ID high"},
    {NAN_TLV_TYPE_RSSI_AVERAGING_WINDOW_SIZE,
     " RSSI averaging window size"},
    {NAN_TLV_TYPE_CLUSTER_OUI_NETWORK_ID,
     " Cluster OUI network ID"},
    {NAN_TLV_TYPE_SOURCE_MAC_ADDRESS,
     " Source MAC address"},
    {NAN_TLV_TYPE_CLUSTER_ATTRIBUTE_IN_SDF,
     " Cluster attribute in SDF"},
    {NAN_TLV_TYPE_SOCIAL_CHANNEL_SCAN_PARAMS,
     " Social channel scan params"},
    {NAN_TLV_TYPE_DEBUGGING_FLAGS, " Debugging flags"},
    {NAN_TLV_TYPE_POST_NAN_CONNECTIVITY_CAPABILITIES_TRANSMIT,
     " Post nan connectivity capabilities transmit"},
    {NAN_TLV_TYPE_POST_NAN_DISCOVERY_ATTRIBUTE_TRANSMIT,
     " Post nan discovery attribute transmit"},
    {NAN_TLV_TYPE_FURTHER_AVAILABILITY_MAP,
     " Further availability map"},
    {NAN_TLV_TYPE_HOP_COUNT_FORCE, " Hop count force"},
    {NAN_TLV_TYPE_RANDOM_FACTOR_FORCE,
     " Random factor force"},
    {NAN_TLV_TYPE_RANDOM_UPDATE_TIME,
     " Random update time"},
    {NAN_TLV_TYPE_EARLY_WAKEUP, " Early wakeup"},
    {NAN_TLV_TYPE_PERIODIC_SCAN_INTERVAL,
     " Periodic scan interval"},
    {NAN_TLV_TYPE_DW_INTERVAL, " DW interval"},
    {NAN_TLV_TYPE_DB_INTERVAL, " DB interval"},
    {NAN_TLV_TYPE_FURTHER_AVAILABILITY,
     " Further availability"},
    {NAN_TLV_TYPE_24G_CHANNEL, " 2.4G channel"},
    {NAN_TLV_TYPE_5G_CHANNEL, " 5G channel"},
    {NAN_TLV_TYPE_CONFIG_LAST, " Config last"},

    /* Attributes types */
    {NAN_TLV_TYPE_ATTRS_FIRST, " Attributes first"},
    {NAN_TLV_TYPE_AVAILABILITY_INTERVALS_MAP,
     " Availability intervals map"},
    {NAN_TLV_TYPE_WLAN_MESH_ID, " WLAN mesh ID"},
    {NAN_TLV_TYPE_MAC_ADDRESS, " MAC address"},
    {NAN_TLV_TYPE_RECEIVED_RSSI_VALUE,
     " Received RSSI value"},
    {NAN_TLV_TYPE_CLUSTER_ATTRIBUTE,
     " Cluster attribute"},
    {NAN_TLV_TYPE_WLAN_INFRA_SSID, " WLAN infra SSID"},
    {NAN_TLV_TYPE_ATTRS_LAST, " Attributes last"},

    /* Events Type */
    {NAN_TLV_TYPE_EVENTS_FIRST, " Events first"},
    {NAN_TLV_TYPE_EVENT_SELF_STATION_MAC_ADDRESS,
     " Event Self station MAC address"},
    {NAN_TLV_TYPE_EVENT_STARTED_CLUSTER,
     " Event started cluster"},
    {NAN_TLV_TYPE_EVENT_JOINED_CLUSTER,
     " Event joined cluster"},
    {NAN_TLV_TYPE_EVENT_CLUSTER_SCAN_RESULTS,
     " Event cluster scan results"},
    {NAN_TLV_TYPE_FAW_MEM_AVAIL,
     " FAW memory availability"},
    {NAN_TLV_TYPE_EVENTS_LAST, " Events last"},

    /* TCA types */
    {NAN_TLV_TYPE_TCA_FIRST, " TCA-Threshold Crossing Alert first"},
    {NAN_TLV_TYPE_CLUSTER_SIZE_REQ,
     " Cluster size request"},
    {NAN_TLV_TYPE_CLUSTER_SIZE_RSP,
     " Cluster size response"},
    {NAN_TLV_TYPE_TCA_LAST, " TCA last"},

    /* Statistics types */
    {NAN_TLV_TYPE_STATS_FIRST, " Stats first"},
    {NAN_TLV_TYPE_DE_PUBLISH_STATS,
     " Discovery engine publish stats"},
    {NAN_TLV_TYPE_DE_SUBSCRIBE_STATS,
     " Discovery engine subscribe stats"},
    {NAN_TLV_TYPE_DE_MAC_STATS,
     " Discovery engine MAC stats"},
    {NAN_TLV_TYPE_DE_TIMING_SYNC_STATS,
     " Discovery engine timing sync stats"},
    {NAN_TLV_TYPE_DE_DW_STATS,
     " Discovery engine DW stats"},
    {NAN_TLV_TYPE_DE_STATS, " Discovery engine stats"},
    {NAN_TLV_TYPE_STATS_LAST, " Stats last"},

    {NAN_TLV_TYPE_LAST, " Last"}
};

struct errorCode {
    NanStatusType frameworkError;
    NanInternalStatusType firmwareError;
    char nan_error[NAN_ERROR_STR_LEN];
};

struct errorCode errorCodeTranslation[] = {
    {NAN_STATUS_SUCCESS, NAN_I_STATUS_SUCCESS,
     "NAN status success"},

    {NAN_STATUS_INTERNAL_FAILURE, NAN_I_STATUS_DE_FAILURE,
     "NAN Discovery engine failure"},

    {NAN_STATUS_INVALID_PUBLISH_SUBSCRIBE_ID, NAN_I_STATUS_INVALID_HANDLE,
     "Invalid Publish/Subscribe ID"},

    {NAN_STATUS_NO_RESOURCE_AVAILABLE, NAN_I_STATUS_NO_SPACE_AVAILABLE,
     "No space available"},

    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_PUBLISH_TYPE,
     "Invalid Publish type, can be 0 or 1"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_TX_TYPE,
     "Invalid Tx type"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_MSG_VERSION,
     "Invalid internal message version"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_MSG_LEN,
     "Invalid message length"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_MSG_ID,
     "Invalid message ID"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_MATCH_ALGORITHM,
     "Invalid matching algorithm, can be 0(match once), 1(match continuous) or 2(match never)"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_TLV_LEN,
     "Invalid TLV length"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_TLV_TYPE,
     "Invalid TLV type"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_MISSING_TLV_TYPE,
     "Missing TLV type"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_TOTAL_TLVS_LEN,
     "Invalid total TLV length"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_TLV_VALUE,
     "Invalid TLV value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_TX_PRIORITY,
     "Invalid Tx priority"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_CONNECTION_MAP,
     "Invalid connection map"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_THRESHOLD_CROSSING_ALERT_ID,
     "Invalid TCA-Threshold Crossing Alert ID"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_STATS_ID,
     "Invalid STATS ID"},

    {NAN_STATUS_PROTOCOL_FAILURE, NAN_I_STATUS_TX_FAIL,
     "Tx Fail"},

    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_RSSI_CLOSE_VALUE,
     "Invalid RSSI close value range is 20dbm to 60dbm"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_RSSI_MIDDLE_VALUE,
     "Invalid RSSI middle value range is 20dbm to 75dbm"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_HOP_COUNT_LIMIT,
     "Invalid hop count limit, max hop count limit is 5"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_HIGH_CLUSTER_ID_VALUE,
     "Invalid cluster ID value. Please set the cluster id high greater than the cluster id low"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_BACKGROUND_SCAN_PERIOD,
     "Invalid background scan period. The range is 10 to 30 milliseconds"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_SCAN_CHANNEL,
     "Invalid scan channel. Only valid channels are the NAN social channels"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_POST_NAN_CONNECTIVITY_CAPABILITIES_BITMAP,
     "Invalid post nan connectivity bitmap"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_NUMCHAN_VALUE,
     "Invalid further availability map number of channel value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_DURATION_VALUE,
     "Invalid further availability map duration value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_CLASS_VALUE,
     "Invalid further availability map class value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_CHANNEL_VALUE,
     "Invalid further availability map channel value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_AVAILABILITY_INTERVAL_BITMAP_VALUE,
     "Invalid further availability map availability interval bitmap value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_MAP_ID,
     "Invalid further availability map map ID"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_CONN_TYPE_VALUE,
     "Invalid post nan discovery connection type value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_DEVICE_ROLE_VALUE,
     "Invalid post nan discovery device role value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_DURATION_VALUE,
     "Invalid post nan discovery duration value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_BITMAP_VALUE,
     "Invalid post nan discovery bitmap value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_MISSING_FUTHER_AVAILABILITY_MAP,
     "Missing further availability map"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_BAND_CONFIG_FLAGS,
     "Invalid band configuration flags"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_RANDOM_FACTOR_UPDATE_TIME_VALUE,
     "Invalid random factor update time value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_ONGOING_SCAN_PERIOD,
     "Invalid ongoing scan period"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_DW_INTERVAL_VALUE,
     "Invalid DW interval value"},
    {NAN_STATUS_INVALID_PARAM, NAN_I_STATUS_INVALID_DB_INTERVAL_VALUE,
     "Invalid DB interval value"},

    {NAN_STATUS_SUCCESS, NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_TIMEOUT,
     "Terminated Reason: Timeout"},
    {NAN_STATUS_SUCCESS, NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_USER_REQUEST,
     "Terminated Reason: User Request"},
    {NAN_STATUS_SUCCESS, NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_COUNT_REACHED,
     "Terminated Reason: Count Reached"},

    {NAN_STATUS_INVALID_REQUESTOR_INSTANCE_ID, NAN_I_STATUS_INVALID_REQUESTER_INSTANCE_ID,
     "Invalid match handle"},
    {NAN_STATUS_NAN_NOT_ALLOWED, NAN_I_STATUS_NAN_NOT_ALLOWED,
     "Nan not allowed"},
    {NAN_STATUS_NO_OTA_ACK, NAN_I_STATUS_NO_OTA_ACK,
     "No OTA ack"},
    {NAN_STATUS_ALREADY_ENABLED, NAN_I_STATUS_NAN_ALREADY_ENABLED,
     "NAN is Already enabled"},
    {NAN_STATUS_FOLLOWUP_QUEUE_FULL, NAN_I_STATUS_FOLLOWUP_QUEUE_FULL,
     "Follow-up queue full"},

    {NAN_STATUS_UNSUPPORTED_CONCURRENCY_NAN_DISABLED, NDP_I_UNSUPPORTED_CONCURRENCY,
     "Unsupported Concurrency"},

    {NAN_STATUS_INTERNAL_FAILURE, NDP_I_NAN_DATA_IFACE_CREATE_FAILED,
     "NAN data interface create failed"},
    {NAN_STATUS_INTERNAL_FAILURE, NDP_I_NAN_DATA_IFACE_DELETE_FAILED,
     "NAN data interface delete failed"},
    {NAN_STATUS_INTERNAL_FAILURE, NDP_I_DATA_INITIATOR_REQUEST_FAILED,
     "NAN data initiator request failed"},
    {NAN_STATUS_INTERNAL_FAILURE, NDP_I_DATA_RESPONDER_REQUEST_FAILED,
     "NAN data responder request failed"},

    {NAN_STATUS_INVALID_NDP_ID, NDP_I_INVALID_NDP_INSTANCE_ID,
     "Invalid NDP instance ID"},

    {NAN_STATUS_INVALID_PARAM, NDP_I_INVALID_RESPONSE_CODE,
     "Invalid response code"},
    {NAN_STATUS_INVALID_PARAM, NDP_I_INVALID_APP_INFO_LEN,
     "Invalid app info length"},

    {NAN_STATUS_PROTOCOL_FAILURE, NDP_I_MGMT_FRAME_REQUEST_FAILED,
     "Management frame request failed"},
    {NAN_STATUS_PROTOCOL_FAILURE, NDP_I_MGMT_FRAME_RESPONSE_FAILED,
     "Management frame response failed"},
    {NAN_STATUS_PROTOCOL_FAILURE, NDP_I_MGMT_FRAME_CONFIRM_FAILED,
     "Management frame confirm failed"},

    {NAN_STATUS_INTERNAL_FAILURE, NDP_I_END_FAILED,
     "NDP end failed"},

    {NAN_STATUS_PROTOCOL_FAILURE, NDP_I_MGMT_FRAME_END_REQUEST_FAILED,
     "Management frame end request failed"},

    {NAN_STATUS_INTERNAL_FAILURE, NDP_I_VENDOR_SPECIFIC_ERROR,
     "Vendor specific error"}
};

void NanCommand::NanErrorTranslation(NanInternalStatusType firmwareErrorRecvd,
                                     u32 valueRcvd,
                                     void* pResponse,
                                     bool is_ndp_rsp)
{
    int i = 0, j = 0;
    u16 msg_id; /* Based on the message_id in the header determine the Indication type */
    NanResponseMsg *pRsp;
    NanPublishTerminatedInd* pRspInd;
    NanDisabledInd* pRspdInd;
    char tlvInfo[NAN_ERROR_STR_LEN];
    tlvInfo[0] = '\0';

    if (isNanResponse() || (is_ndp_rsp == true)){
        pRsp = (NanResponseMsg*)pResponse;
        for (i = 0; i < (int)(sizeof(errorCodeTranslation)/ sizeof(errorCode)); i++) {
            if (errorCodeTranslation[i].firmwareError == firmwareErrorRecvd) {
                pRsp->status =  errorCodeTranslation[i].frameworkError;
                strlcpy(pRsp->nan_error, errorCodeTranslation[i].nan_error, NAN_ERROR_STR_LEN);
                if (NAN_I_STATUS_INVALID_TLV_TYPE == firmwareErrorRecvd) {
                    for (j = 0; j < (int)(sizeof(tlvToStr)/sizeof(verboseTlv)); j++) {
                        if (tlvToStr[j].tlvType == valueRcvd) {
                            strlcpy(tlvInfo, tlvToStr[i].strTlv, NAN_ERROR_STR_LEN);
                            break;
                        }
                    }
                }
                strlcat(pRsp->nan_error, tlvInfo, sizeof(pRsp->nan_error));
                break;
            }
        }
        if (i == (int)(sizeof(errorCodeTranslation)/sizeof(errorCode))) {
                pRsp->status =  NAN_STATUS_INTERNAL_FAILURE;
                strlcpy(pRsp->nan_error, "NAN Discovery engine failure", NAN_ERROR_STR_LEN);
        }
        ALOGD("%s: Status: %d Error Info[value %d]: %s", __FUNCTION__, pRsp->status, valueRcvd, pRsp->nan_error);
    } else {
        msg_id = getIndicationType();

        switch(msg_id) {
        case NAN_INDICATION_PUBLISH_TERMINATED:
        case NAN_INDICATION_SUBSCRIBE_TERMINATED:
        case NAN_INDICATION_SELF_TRANSMIT_FOLLOWUP:
                pRspInd = (NanPublishTerminatedInd*)pResponse;
                for (i = 0; i < (int)(sizeof(errorCodeTranslation)/ sizeof(errorCode)); i++) {
                        if (errorCodeTranslation[i].firmwareError == firmwareErrorRecvd) {
                                pRspInd->reason =  errorCodeTranslation[i].frameworkError;
                                strlcpy(pRspInd->nan_reason, errorCodeTranslation[i].nan_error, NAN_ERROR_STR_LEN);
                                break;
                        }
                }
                if (i == (int)(sizeof(errorCodeTranslation)/sizeof(errorCode))) {
                        pRspInd->reason =  NAN_STATUS_INTERNAL_FAILURE;
                        strlcpy(pRspInd->nan_reason, "NAN Discovery engine failure", NAN_ERROR_STR_LEN);
                }
                ALOGD("%s: Status: %d Error Info[value %d]: %s", __FUNCTION__, pRspInd->reason, valueRcvd, pRspInd->nan_reason);
                break;
        case NAN_INDICATION_DISABLED:
                pRspdInd = (NanDisabledInd*)pResponse;
                for (i = 0; i < (int)(sizeof(errorCodeTranslation)/ sizeof(errorCode)); i++) {
                        if (errorCodeTranslation[i].firmwareError == firmwareErrorRecvd) {
                                pRspdInd->reason =  errorCodeTranslation[i].frameworkError;
                                strlcpy(pRspdInd->nan_reason, errorCodeTranslation[i].nan_error, NAN_ERROR_STR_LEN);
                                break;
                        }
                }
                if (i == (int)(sizeof(errorCodeTranslation)/sizeof(errorCode))) {
                        pRspdInd->reason =  NAN_STATUS_INTERNAL_FAILURE;
                        strlcpy(pRspdInd->nan_reason, "NAN Discovery engine failure", NAN_ERROR_STR_LEN);
                }
                ALOGD("%s: Status: %d Error Info[value %d]: %s", __FUNCTION__, pRspdInd->reason, valueRcvd, pRspdInd->nan_reason);
                break;
        }
    }
}

int NanCommand::getNanResponse(transaction_id *id, NanResponseMsg *pRsp)
{
    if (mNanVendorEvent == NULL || pRsp == NULL) {
        ALOGE("NULL check failed");
        return WIFI_ERROR_INVALID_ARGS;
    }

    NanMsgHeader *pHeader = (NanMsgHeader *)mNanVendorEvent;

    switch (pHeader->msgId) {
        case NAN_MSG_ID_ERROR_RSP:
        {
            pNanErrorRspMsg pFwRsp = \
                (pNanErrorRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_ERROR;
            break;
        }
        case NAN_MSG_ID_CONFIGURATION_RSP:
        {
            pNanConfigurationRspMsg pFwRsp = \
                (pNanConfigurationRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_CONFIG;
        }
        break;
        case NAN_MSG_ID_PUBLISH_SERVICE_CANCEL_RSP:
        {
            pNanPublishServiceCancelRspMsg pFwRsp = \
                (pNanPublishServiceCancelRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_PUBLISH_CANCEL;
            pRsp->body.publish_response.publish_id = \
                pFwRsp->fwHeader.handle;
            break;
        }
        case NAN_MSG_ID_PUBLISH_SERVICE_RSP:
        {
            pNanPublishServiceRspMsg pFwRsp = \
                (pNanPublishServiceRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_PUBLISH;
            pRsp->body.publish_response.publish_id = \
                pFwRsp->fwHeader.handle;
            break;
        }
        case NAN_MSG_ID_SUBSCRIBE_SERVICE_RSP:
        {
            pNanSubscribeServiceRspMsg pFwRsp = \
                (pNanSubscribeServiceRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_SUBSCRIBE;
            pRsp->body.subscribe_response.subscribe_id = \
                pFwRsp->fwHeader.handle;
        }
        break;
        case NAN_MSG_ID_SUBSCRIBE_SERVICE_CANCEL_RSP:
        {
            pNanSubscribeServiceCancelRspMsg pFwRsp = \
                (pNanSubscribeServiceCancelRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_SUBSCRIBE_CANCEL;
            pRsp->body.subscribe_response.subscribe_id = \
                pFwRsp->fwHeader.handle;
            break;
        }
        case NAN_MSG_ID_TRANSMIT_FOLLOWUP_RSP:
        {
            pNanTransmitFollowupRspMsg pFwRsp = \
                (pNanTransmitFollowupRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_TRANSMIT_FOLLOWUP;
            break;
        }
        case NAN_MSG_ID_STATS_RSP:
        {
            pNanStatsRspMsg pFwRsp = \
                (pNanStatsRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->statsRspParams.status,
                                            pFwRsp->statsRspParams.value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_STATS;
            pRsp->body.stats_response.stats_type = \
                (NanStatsType)pFwRsp->statsRspParams.statsType;
            ALOGV("%s: stats_type:%d",__func__,
                  pRsp->body.stats_response.stats_type);
            u8 *pInputTlv = pFwRsp->ptlv;
            NanTlv outputTlv;
            memset(&outputTlv, 0, sizeof(outputTlv));
            u16 readLen = 0;
            int remainingLen = (mNanDataLen -  \
                (sizeof(NanMsgHeader) + sizeof(NanStatsRspParams)));
            if (remainingLen > 0) {
                readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv);
                ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
                      __func__, remainingLen, readLen, outputTlv.type,
                      outputTlv.length);
                if (outputTlv.length <= \
                    sizeof(pRsp->body.stats_response.data)) {
                    handleNanStatsResponse(pRsp->body.stats_response.stats_type,
                                           (char *)outputTlv.value,
                                           &pRsp->body.stats_response,
                                           outputTlv.length);
                }
            } else
                ALOGV("%s: No TLV's present",__func__);
            break;
        }
        case NAN_MSG_ID_ENABLE_RSP:
        {
            pNanEnableRspMsg pFwRsp = \
                (pNanEnableRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_ENABLED;
            break;
        }
        case NAN_MSG_ID_DISABLE_RSP:
        {
            pNanDisableRspMsg pFwRsp = \
                (pNanDisableRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, 0, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_DISABLED;
            break;
        }
        case NAN_MSG_ID_TCA_RSP:
        {
            pNanTcaRspMsg pFwRsp = \
                (pNanTcaRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_TCA;
            break;
        }
        case NAN_MSG_ID_BEACON_SDF_RSP:
        {
            pNanBeaconSdfPayloadRspMsg pFwRsp = \
                (pNanBeaconSdfPayloadRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, 0, pRsp, false);
            pRsp->response_type = NAN_RESPONSE_BEACON_SDF_PAYLOAD;
            break;
        }
        case NAN_MSG_ID_CAPABILITIES_RSP:
        {
            pNanCapabilitiesRspMsg pFwRsp = \
                (pNanCapabilitiesRspMsg)mNanVendorEvent;
            *id = (transaction_id)pFwRsp->fwHeader.transactionId;
            NanErrorTranslation((NanInternalStatusType)pFwRsp->status, pFwRsp->value, pRsp, false);
            pRsp->response_type = NAN_GET_CAPABILITIES;
            pRsp->body.nan_capabilities.max_concurrent_nan_clusters = \
                        pFwRsp->max_concurrent_nan_clusters;
            pRsp->body.nan_capabilities.max_publishes = \
                        pFwRsp->max_publishes;
            pRsp->body.nan_capabilities.max_subscribes = \
                        pFwRsp->max_subscribes;
            pRsp->body.nan_capabilities.max_service_name_len = \
                        pFwRsp->max_service_name_len;
            pRsp->body.nan_capabilities.max_match_filter_len = \
                        pFwRsp->max_match_filter_len;
            pRsp->body.nan_capabilities.max_total_match_filter_len = \
                        pFwRsp->max_total_match_filter_len;
            pRsp->body.nan_capabilities.max_service_specific_info_len = \
                        pFwRsp->max_service_specific_info_len;
            pRsp->body.nan_capabilities.max_vsa_data_len = \
                        pFwRsp->max_vsa_data_len;
            pRsp->body.nan_capabilities.max_mesh_data_len = \
                        pFwRsp->max_mesh_data_len;
            pRsp->body.nan_capabilities.max_ndi_interfaces = \
                       pFwRsp->max_ndi_interfaces;
            pRsp->body.nan_capabilities.max_ndp_sessions = \
                       pFwRsp->max_ndp_sessions;
            pRsp->body.nan_capabilities.max_app_info_len = \
                       pFwRsp->max_app_info_len;
            pRsp->body.nan_capabilities.max_queued_transmit_followup_msgs = \
                       pFwRsp->max_queued_transmit_followup_msgs;
            pRsp->body.nan_capabilities.ndp_supported_bands = \
                       pFwRsp->ndp_supported_bands;
            pRsp->body.nan_capabilities.cipher_suites_supported = \
                       pFwRsp->cipher_suites_supported;
            pRsp->body.nan_capabilities.max_scid_len = \
                       pFwRsp->max_scid_len;
            pRsp->body.nan_capabilities.is_ndp_security_supported = \
                       pFwRsp->is_ndp_security_supported;
            pRsp->body.nan_capabilities.max_sdea_service_specific_info_len = \
                       pFwRsp->max_sdea_service_specific_info_len;
            pRsp->body.nan_capabilities.max_subscribe_address = \
                       pFwRsp->max_subscribe_address;
            break;
        }
        default:
            return  -1;
    }
    return  0;
}

int NanCommand::handleNanResponse()
{
    //parse the data and call
    //the response callback handler with the populated
    //NanResponseMsg
    NanResponseMsg  rsp_data;
    int ret;
    transaction_id id;

    ALOGV("handleNanResponse called %p", this);
    memset(&rsp_data, 0, sizeof(rsp_data));
    //get the rsp_data
    ret = getNanResponse(&id, &rsp_data);

    ALOGI("handleNanResponse ret:%d status:%u value:%s response_type:%u",
          ret, rsp_data.status, rsp_data.nan_error, rsp_data.response_type);
    if (ret == 0 && (rsp_data.response_type == NAN_RESPONSE_STATS) &&
        (mStaParam != NULL) &&
        (rsp_data.body.stats_response.stats_type == NAN_STATS_ID_DE_TIMING_SYNC)) {
        /*
           Fill the staParam with appropriate values and return from here.
           No need to call NotifyResponse as the request is for getting the
           STA response
        */
        NanSyncStats *pSyncStats = &rsp_data.body.stats_response.data.sync_stats;
        mStaParam->master_rank = pSyncStats->myRank;
        mStaParam->master_pref = (pSyncStats->myRank & 0xFF00000000000000) >> 56;
        mStaParam->random_factor = (pSyncStats->myRank & 0x00FF000000000000) >> 48;
        mStaParam->hop_count = pSyncStats->currAmHopCount;
        mStaParam->beacon_transmit_time = pSyncStats->currAmBTT;
        mStaParam->ndp_channel_freq = pSyncStats->ndpChannelFreq;

        ALOGI("%s:0x%02x master_pref 0x%02x random_factor 0x%02x hop_count %u Channel",
                __func__, mStaParam->master_pref, mStaParam->random_factor,
                mStaParam->hop_count, mStaParam->ndp_channel_freq);

        return ret;
    }
    //Call the NotifyResponse Handler
    if (ret == 0 && mHandler.NotifyResponse) {
        (*mHandler.NotifyResponse)(id, &rsp_data);
    }
    return ret;
}

void NanCommand::handleNanStatsResponse(NanStatsType stats_type,
                                       char *rspBuf,
                                       NanStatsResponse *pRsp,
                                       u32 message_len)
{
    if (stats_type == NAN_STATS_ID_DE_PUBLISH) {
        NanPublishStats publish_stats;
        if (message_len != sizeof(NanPublishStats)) {
            ALOGE("%s: stats_type = %d invalid stats length = %u expected length = %zu\n",
                    __func__, stats_type, message_len, sizeof(NanPublishStats));
            return;
        }
        FwNanPublishStats *pPubStats = (FwNanPublishStats *)rspBuf;

        publish_stats.validPublishServiceReqMsgs =
                                    pPubStats->validPublishServiceReqMsgs;
        publish_stats.validPublishServiceRspMsgs =
                                    pPubStats->validPublishServiceRspMsgs;
        publish_stats.validPublishServiceCancelReqMsgs =
                                    pPubStats->validPublishServiceCancelReqMsgs;
        publish_stats.validPublishServiceCancelRspMsgs =
                                    pPubStats->validPublishServiceCancelRspMsgs;
        publish_stats.validPublishRepliedIndMsgs =
                                    pPubStats->validPublishRepliedIndMsgs;
        publish_stats.validPublishTerminatedIndMsgs =
                                    pPubStats->validPublishTerminatedIndMsgs;
        publish_stats.validActiveSubscribes = pPubStats->validActiveSubscribes;
        publish_stats.validMatches = pPubStats->validMatches;
        publish_stats.validFollowups = pPubStats->validFollowups;
        publish_stats.invalidPublishServiceReqMsgs =
                                    pPubStats->invalidPublishServiceReqMsgs;
        publish_stats.invalidPublishServiceCancelReqMsgs =
                                pPubStats->invalidPublishServiceCancelReqMsgs;
        publish_stats.invalidActiveSubscribes =
                                pPubStats->invalidActiveSubscribes;
        publish_stats.invalidMatches = pPubStats->invalidMatches;
        publish_stats.invalidFollowups = pPubStats->invalidFollowups;
        publish_stats.publishCount = pPubStats->publishCount;
        publish_stats.publishNewMatchCount = pPubStats->publishNewMatchCount;
        publish_stats.pubsubGlobalNewMatchCount =
                               pPubStats->pubsubGlobalNewMatchCount;
        memcpy(&pRsp->data, &publish_stats, sizeof(NanPublishStats));
    } else if (stats_type == NAN_STATS_ID_DE_SUBSCRIBE) {
        NanSubscribeStats sub_stats;
        if (message_len != sizeof(NanSubscribeStats)) {
            ALOGE("%s: stats_type = %d invalid stats length = %u expected length = %zu\n",
                   __func__, stats_type, message_len, sizeof(NanSubscribeStats));
            return;
        }
        FwNanSubscribeStats *pSubStats = (FwNanSubscribeStats *)rspBuf;

        sub_stats.validSubscribeServiceReqMsgs =
                                pSubStats->validSubscribeServiceReqMsgs;
        sub_stats.validSubscribeServiceRspMsgs =
                                pSubStats->validSubscribeServiceRspMsgs;
        sub_stats.validSubscribeServiceCancelReqMsgs =
                                pSubStats->validSubscribeServiceCancelReqMsgs;
        sub_stats.validSubscribeServiceCancelRspMsgs =
                                pSubStats->validSubscribeServiceCancelRspMsgs;
        sub_stats.validSubscribeTerminatedIndMsgs =
                                pSubStats->validSubscribeTerminatedIndMsgs;
        sub_stats.validSubscribeMatchIndMsgs =
                                pSubStats->validSubscribeMatchIndMsgs;
        sub_stats.validSubscribeUnmatchIndMsgs =
                                pSubStats->validSubscribeUnmatchIndMsgs;
        sub_stats.validSolicitedPublishes =
                                pSubStats->validSolicitedPublishes;
        sub_stats.validMatches = pSubStats->validMatches;
        sub_stats.validFollowups = pSubStats->validFollowups;
        sub_stats.invalidSubscribeServiceReqMsgs =
                            pSubStats->invalidSubscribeServiceReqMsgs;
        sub_stats.invalidSubscribeServiceCancelReqMsgs =
                            pSubStats->invalidSubscribeServiceCancelReqMsgs;
        sub_stats.invalidSubscribeFollowupReqMsgs =
                            pSubStats->invalidSubscribeFollowupReqMsgs;
        sub_stats.invalidSolicitedPublishes =
                            pSubStats->invalidSolicitedPublishes;
        sub_stats.invalidMatches = pSubStats->invalidMatches;
        sub_stats.invalidFollowups = pSubStats->invalidFollowups;
        sub_stats.subscribeCount = pSubStats->subscribeCount;
        sub_stats.bloomFilterIndex = pSubStats->bloomFilterIndex;
        sub_stats.subscribeNewMatchCount = pSubStats->subscribeNewMatchCount;
        sub_stats.pubsubGlobalNewMatchCount =
                                      pSubStats->pubsubGlobalNewMatchCount;
        memcpy(&pRsp->data, &sub_stats, sizeof(NanSubscribeStats));
    } else if (stats_type == NAN_STATS_ID_DE_DW) {
        NanDWStats dw_stats;
        if (message_len != sizeof(NanDWStats)) {
            ALOGE("%s: stats_type = %d invalid stats length = %u expected length = %zu\n",
                   __func__, stats_type, message_len, sizeof(NanDWStats));
            return;
        }
        FwNanMacStats *pMacStats = (FwNanMacStats *)rspBuf;

        dw_stats.validFrames = pMacStats->validFrames;
        dw_stats.validActionFrames = pMacStats->validActionFrames;
        dw_stats.validBeaconFrames = pMacStats->validBeaconFrames;
        dw_stats.ignoredActionFrames = pMacStats->ignoredActionFrames;
        dw_stats.invalidFrames = pMacStats->invalidFrames;
        dw_stats.invalidActionFrames = pMacStats->invalidActionFrames;
        dw_stats.invalidBeaconFrames = pMacStats->invalidBeaconFrames;
        dw_stats.invalidMacHeaders = pMacStats->invalidMacHeaders;
        dw_stats.invalidPafHeaders  = pMacStats->invalidPafHeaders;
        dw_stats.nonNanBeaconFrames = pMacStats->nonNanBeaconFrames;
        dw_stats.earlyActionFrames = pMacStats->earlyActionFrames;
        dw_stats.inDwActionFrames = pMacStats->inDwActionFrames;
        dw_stats.lateActionFrames = pMacStats->lateActionFrames;
        dw_stats.framesQueued =  pMacStats->framesQueued;
        dw_stats.totalTRSpUpdates = pMacStats->totalTRSpUpdates;
        dw_stats.completeByTRSp = pMacStats->completeByTRSp;
        dw_stats.completeByTp75DW = pMacStats->completeByTp75DW;
        dw_stats.completeByTendDW = pMacStats->completeByTendDW;
        dw_stats.lateActionFramesTx = pMacStats->lateActionFramesTx;
        memcpy(&pRsp->data, &dw_stats, sizeof(NanDWStats));
    } else if (stats_type == NAN_STATS_ID_DE_MAC) {
        NanMacStats mac_stats;
        if (message_len != sizeof(NanMacStats)) {
            ALOGE("%s: stats_type = %d invalid stats length = %u expected length = %zu\n",
                   __func__, stats_type, message_len, sizeof(NanMacStats));
            return;
        }
        FwNanMacStats *pMacStats = (FwNanMacStats *)rspBuf;

        mac_stats.validFrames = pMacStats->validFrames;
        mac_stats.validActionFrames = pMacStats->validActionFrames;
        mac_stats.validBeaconFrames = pMacStats->validBeaconFrames;
        mac_stats.ignoredActionFrames = pMacStats->ignoredActionFrames;
        mac_stats.invalidFrames = pMacStats->invalidFrames;
        mac_stats.invalidActionFrames = pMacStats->invalidActionFrames;
        mac_stats.invalidBeaconFrames = pMacStats->invalidBeaconFrames;
        mac_stats.invalidMacHeaders = pMacStats->invalidMacHeaders;
        mac_stats.invalidPafHeaders  = pMacStats->invalidPafHeaders;
        mac_stats.nonNanBeaconFrames = pMacStats->nonNanBeaconFrames;
        mac_stats.earlyActionFrames = pMacStats->earlyActionFrames;
        mac_stats.inDwActionFrames = pMacStats->inDwActionFrames;
        mac_stats.lateActionFrames = pMacStats->lateActionFrames;
        mac_stats.framesQueued =  pMacStats->framesQueued;
        mac_stats.totalTRSpUpdates = pMacStats->totalTRSpUpdates;
        mac_stats.completeByTRSp = pMacStats->completeByTRSp;
        mac_stats.completeByTp75DW = pMacStats->completeByTp75DW;
        mac_stats.completeByTendDW = pMacStats->completeByTendDW;
        mac_stats.lateActionFramesTx = pMacStats->lateActionFramesTx;
        mac_stats.twIncreases = pMacStats->twIncreases;
        mac_stats.twDecreases = pMacStats->twDecreases;
        mac_stats.twChanges = pMacStats->twChanges;
        mac_stats.twHighwater = pMacStats->twHighwater;
        mac_stats.bloomFilterIndex = pMacStats->bloomFilterIndex;
        memcpy(&pRsp->data, &mac_stats, sizeof(NanMacStats));
    } else if (stats_type == NAN_STATS_ID_DE_TIMING_SYNC) {
        NanSyncStats sync_stats;
        if (message_len != sizeof(NanSyncStats)) {
            ALOGE("%s: stats_type = %d invalid stats length = %u expected length = %zu\n",
                   __func__, stats_type, message_len, sizeof(NanSyncStats));
            return;
        }
        FwNanSyncStats *pSyncStats = (FwNanSyncStats *)rspBuf;

        sync_stats.currTsf = pSyncStats->currTsf;
        sync_stats.myRank = pSyncStats->myRank;
        sync_stats.currAmRank = pSyncStats->currAmRank;
        sync_stats.lastAmRank = pSyncStats->lastAmRank;
        sync_stats.currAmBTT = pSyncStats->currAmBTT;
        sync_stats.lastAmBTT = pSyncStats->lastAmBTT;
        sync_stats.currAmHopCount = pSyncStats->currAmHopCount;
        sync_stats.currRole = pSyncStats->currRole;
        sync_stats.currClusterId = pSyncStats->currClusterId;

        sync_stats.timeSpentInCurrRole = pSyncStats->timeSpentInCurrRole;
        sync_stats.totalTimeSpentAsMaster = pSyncStats->totalTimeSpentAsMaster;
        sync_stats.totalTimeSpentAsNonMasterSync =
                            pSyncStats->totalTimeSpentAsNonMasterSync;
        sync_stats.totalTimeSpentAsNonMasterNonSync =
                            pSyncStats->totalTimeSpentAsNonMasterNonSync;
        sync_stats.transitionsToAnchorMaster =
                            pSyncStats->transitionsToAnchorMaster;
        sync_stats.transitionsToMaster =
                            pSyncStats->transitionsToMaster;
        sync_stats.transitionsToNonMasterSync =
                            pSyncStats->transitionsToNonMasterSync;
        sync_stats.transitionsToNonMasterNonSync =
                            pSyncStats->transitionsToNonMasterNonSync;
        sync_stats.amrUpdateCount = pSyncStats->amrUpdateCount;
        sync_stats.amrUpdateRankChangedCount =
                            pSyncStats->amrUpdateRankChangedCount;
        sync_stats.amrUpdateBTTChangedCount =
                            pSyncStats->amrUpdateBTTChangedCount;
        sync_stats.amrUpdateHcChangedCount =
                            pSyncStats->amrUpdateHcChangedCount;
        sync_stats.amrUpdateNewDeviceCount =
                            pSyncStats->amrUpdateNewDeviceCount;
        sync_stats.amrExpireCount = pSyncStats->amrExpireCount;
        sync_stats.mergeCount = pSyncStats->mergeCount;
        sync_stats.beaconsAboveHcLimit = pSyncStats->beaconsAboveHcLimit;
        sync_stats.beaconsBelowRssiThresh = pSyncStats->beaconsBelowRssiThresh;
        sync_stats.beaconsIgnoredNoSpace = pSyncStats->beaconsIgnoredNoSpace;
        sync_stats.beaconsForOurCluster = pSyncStats->beaconsForOtherCluster;
        sync_stats.beaconsForOtherCluster = pSyncStats->beaconsForOtherCluster;
        sync_stats.beaconCancelRequests = pSyncStats->beaconCancelRequests;
        sync_stats.beaconCancelFailures = pSyncStats->beaconCancelFailures;
        sync_stats.beaconUpdateRequests = pSyncStats->beaconUpdateRequests;
        sync_stats.beaconUpdateFailures = pSyncStats->beaconUpdateFailures;
        sync_stats.syncBeaconTxAttempts = pSyncStats->syncBeaconTxAttempts;
        sync_stats.syncBeaconTxFailures = pSyncStats->syncBeaconTxFailures;
        sync_stats.discBeaconTxAttempts = pSyncStats->discBeaconTxAttempts;
        sync_stats.discBeaconTxFailures = pSyncStats->discBeaconTxFailures;
        sync_stats.amHopCountExpireCount = pSyncStats->amHopCountExpireCount;
        sync_stats.ndpChannelFreq = pSyncStats->ndpChannelFreq;
        sync_stats.ndpChannelFreq2 = pSyncStats->ndpChannelFreq2;
        memcpy(&pRsp->data, &sync_stats, sizeof(NanSyncStats));
    } else if (stats_type == NAN_STATS_ID_DE) {
        NanDeStats de_stats;
        if (message_len != sizeof(NanDeStats)) {
            ALOGE("%s: stats_type = %d invalid stats length = %u expected length = %zu\n",
                   __func__, stats_type, message_len, sizeof(NanDeStats));
            return;
        }
        FwNanDeStats *pDeStats = (FwNanDeStats *)rspBuf;

        de_stats.validErrorRspMsgs = pDeStats->validErrorRspMsgs;
        de_stats.validTransmitFollowupReqMsgs =
                        pDeStats->validTransmitFollowupReqMsgs;
        de_stats.validTransmitFollowupRspMsgs =
                        pDeStats->validTransmitFollowupRspMsgs;
        de_stats.validFollowupIndMsgs =
                        pDeStats->validFollowupIndMsgs;
        de_stats.validConfigurationReqMsgs =
                        pDeStats->validConfigurationReqMsgs;
        de_stats.validConfigurationRspMsgs =
                        pDeStats->validConfigurationRspMsgs;
        de_stats.validStatsReqMsgs = pDeStats->validStatsReqMsgs;
        de_stats.validStatsRspMsgs = pDeStats->validStatsRspMsgs;
        de_stats.validEnableReqMsgs = pDeStats->validEnableReqMsgs;
        de_stats.validEnableRspMsgs = pDeStats->validEnableRspMsgs;
        de_stats.validDisableReqMsgs = pDeStats->validDisableReqMsgs;
        de_stats.validDisableRspMsgs = pDeStats->validDisableRspMsgs;
        de_stats.validDisableIndMsgs = pDeStats->validDisableIndMsgs;
        de_stats.validEventIndMsgs = pDeStats->validEventIndMsgs;
        de_stats.validTcaReqMsgs = pDeStats->validTcaReqMsgs;
        de_stats.validTcaRspMsgs = pDeStats->validTcaRspMsgs;
        de_stats.validTcaIndMsgs = pDeStats->validTcaIndMsgs;
        de_stats.invalidTransmitFollowupReqMsgs =
                            pDeStats->invalidTransmitFollowupReqMsgs;
        de_stats.invalidConfigurationReqMsgs =
                            pDeStats->invalidConfigurationReqMsgs;
        de_stats.invalidStatsReqMsgs = pDeStats->invalidStatsReqMsgs;
        de_stats.invalidEnableReqMsgs = pDeStats->invalidEnableReqMsgs;
        de_stats.invalidDisableReqMsgs = pDeStats->invalidDisableReqMsgs;
        de_stats.invalidTcaReqMsgs = pDeStats->invalidTcaReqMsgs;
        memcpy(&pRsp->data, &de_stats, sizeof(NanDeStats));
    } else {
        ALOGE("Unknown stats_type:%d\n", stats_type);
    }
}

int NanCommand::handleNdpResponse(NanResponseType ndpCmdType,
                                  struct nlattr **tb_vendor)
{
    //parse the data and call
    //the response callback handler with the populated
    //NanResponseMsg
    NanResponseMsg  rsp_data;
    transaction_id id;

    memset(&rsp_data, 0, sizeof(rsp_data));

    if ((!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID]) ||
        (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_DRV_RESPONSE_STATUS_TYPE]) ||
        (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_DRV_RETURN_VALUE]))
    {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_NDP not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    id = nla_get_u16(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID]);
    ALOGD("%s: Transaction id : val %d", __FUNCTION__, id);

    NanErrorTranslation((NanInternalStatusType)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_DRV_RESPONSE_STATUS_TYPE]),
                        nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_DRV_RETURN_VALUE]), &rsp_data, true);
    rsp_data.response_type = ndpCmdType;

    if (ndpCmdType == NAN_DP_INITIATOR_RESPONSE)
    {
        if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID])
        {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_NDP not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        rsp_data.body.data_request_response.ndp_instance_id =
        nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID]);
    }
    //Call the NotifyResponse Handler
    if (mHandler.NotifyResponse) {
        (*mHandler.NotifyResponse)(id, &rsp_data);
    }
    return WIFI_SUCCESS;
}
