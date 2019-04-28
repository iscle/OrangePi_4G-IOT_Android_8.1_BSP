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
#include <errno.h>
#include "wifi_hal.h"
#include "nan_i.h"
#include "nancommand.h"
#include <errno.h>

//Function which calls the necessaryIndication callback
//based on the indication type
int NanCommand::handleNanIndication()
{
    //Based on the message_id in the header determine the Indication type
    //and call the necessary callback handler
    u16 msg_id;
    int res = 0;

    msg_id = getIndicationType();

    ALOGV("handleNanIndication msg_id:%u", msg_id);
    switch (msg_id) {
    case NAN_INDICATION_PUBLISH_REPLIED:
        NanPublishRepliedInd publishRepliedInd;
        memset(&publishRepliedInd, 0, sizeof(publishRepliedInd));
        res = getNanPublishReplied(&publishRepliedInd);
        if (!res && mHandler.EventPublishReplied) {
            (*mHandler.EventPublishReplied)(&publishRepliedInd);
        }
        break;

    case NAN_INDICATION_PUBLISH_TERMINATED:
        NanPublishTerminatedInd publishTerminatedInd;
        memset(&publishTerminatedInd, 0, sizeof(publishTerminatedInd));
        res = getNanPublishTerminated(&publishTerminatedInd);
        if (!res && mHandler.EventPublishTerminated) {
            (*mHandler.EventPublishTerminated)(&publishTerminatedInd);
        }
        break;

    case NAN_INDICATION_MATCH:
        NanMatchInd matchInd;
        memset(&matchInd, 0, sizeof(matchInd));
        res = getNanMatch(&matchInd);
        if (!res && mHandler.EventMatch) {
            (*mHandler.EventMatch)(&matchInd);
        }
        break;

    case NAN_INDICATION_MATCH_EXPIRED:
        NanMatchExpiredInd matchExpiredInd;
        memset(&matchExpiredInd, 0, sizeof(matchExpiredInd));
        res = getNanMatchExpired(&matchExpiredInd);
        if (!res && mHandler.EventMatchExpired) {
            (*mHandler.EventMatchExpired)(&matchExpiredInd);
        }
        break;

    case NAN_INDICATION_SUBSCRIBE_TERMINATED:
        NanSubscribeTerminatedInd subscribeTerminatedInd;
        memset(&subscribeTerminatedInd, 0, sizeof(subscribeTerminatedInd));
        res = getNanSubscribeTerminated(&subscribeTerminatedInd);
        if (!res && mHandler.EventSubscribeTerminated) {
            (*mHandler.EventSubscribeTerminated)(&subscribeTerminatedInd);
        }
        break;

    case NAN_INDICATION_DE_EVENT:
        NanDiscEngEventInd discEngEventInd;
        memset(&discEngEventInd, 0, sizeof(discEngEventInd));
        res = getNanDiscEngEvent(&discEngEventInd);
        if (!res && mHandler.EventDiscEngEvent) {
            (*mHandler.EventDiscEngEvent)(&discEngEventInd);
        }
        break;

    case NAN_INDICATION_FOLLOWUP:
        NanFollowupInd followupInd;
        memset(&followupInd, 0, sizeof(followupInd));
        res = getNanFollowup(&followupInd);
        if (!res && mHandler.EventFollowup) {
            (*mHandler.EventFollowup)(&followupInd);
        }
        break;

    case NAN_INDICATION_DISABLED:
        NanDisabledInd disabledInd;
        memset(&disabledInd, 0, sizeof(disabledInd));
        res = getNanDisabled(&disabledInd);
        if (!res && mHandler.EventDisabled) {
            (*mHandler.EventDisabled)(&disabledInd);
        }
        break;

    case NAN_INDICATION_TCA:
        NanTCAInd tcaInd;
        memset(&tcaInd, 0, sizeof(tcaInd));
        res = getNanTca(&tcaInd);
        if (!res && mHandler.EventTca) {
            (*mHandler.EventTca)(&tcaInd);
        }
        break;

    case NAN_INDICATION_BEACON_SDF_PAYLOAD:
        NanBeaconSdfPayloadInd beaconSdfPayloadInd;
        memset(&beaconSdfPayloadInd, 0, sizeof(beaconSdfPayloadInd));
        res = getNanBeaconSdfPayload(&beaconSdfPayloadInd);
        if (!res && mHandler.EventBeaconSdfPayload) {
            (*mHandler.EventBeaconSdfPayload)(&beaconSdfPayloadInd);
        }
        break;

    case NAN_INDICATION_SELF_TRANSMIT_FOLLOWUP:
        NanTransmitFollowupInd transmitFollowupInd;
        memset(&transmitFollowupInd, 0, sizeof(NanTransmitFollowupInd));
        res = getNanTransmitFollowupInd(&transmitFollowupInd);
        if (!res && mHandler.EventTransmitFollowup) {
            (*mHandler.EventTransmitFollowup)(&transmitFollowupInd);
        }
        break;

    case NAN_INDICATION_RANGING_REQUEST_RECEIVED:
        NanRangeRequestInd rangeRequestInd;
        memset(&rangeRequestInd, 0, sizeof(NanRangeRequestInd));
        res = getNanRangeRequestReceivedInd(&rangeRequestInd);
        if (!res && mHandler.EventRangeRequest) {
            (*mHandler.EventRangeRequest)(&rangeRequestInd);
        }
        break;

    case NAN_INDICATION_RANGING_RESULT:
        NanRangeReportInd rangeReportInd;
        memset(&rangeReportInd, 0, sizeof(NanRangeReportInd));
        res = getNanRangeReportInd(&rangeReportInd);
        if (!res && mHandler.EventRangeReport) {
            (*mHandler.EventRangeReport)(&rangeReportInd);
        }
        break;

    default:
        ALOGE("handleNanIndication error invalid msg_id:%u", msg_id);
        res = (int)WIFI_ERROR_INVALID_REQUEST_ID;
        break;
    }
    return res;
}

//Function which will return the Nan Indication type based on
//the initial few bytes of mNanVendorEvent
NanIndicationType NanCommand::getIndicationType()
{
    if (mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid argument mNanVendorEvent:%p",
              __func__, mNanVendorEvent);
        return NAN_INDICATION_UNKNOWN;
    }

    NanMsgHeader *pHeader = (NanMsgHeader *)mNanVendorEvent;

    switch (pHeader->msgId) {
    case NAN_MSG_ID_PUBLISH_REPLIED_IND:
        return NAN_INDICATION_PUBLISH_REPLIED;
    case NAN_MSG_ID_PUBLISH_TERMINATED_IND:
        return NAN_INDICATION_PUBLISH_TERMINATED;
    case NAN_MSG_ID_MATCH_IND:
        return NAN_INDICATION_MATCH;
    case NAN_MSG_ID_MATCH_EXPIRED_IND:
        return NAN_INDICATION_MATCH_EXPIRED;
    case NAN_MSG_ID_FOLLOWUP_IND:
        return NAN_INDICATION_FOLLOWUP;
    case NAN_MSG_ID_SUBSCRIBE_TERMINATED_IND:
        return NAN_INDICATION_SUBSCRIBE_TERMINATED;
    case  NAN_MSG_ID_DE_EVENT_IND:
        return NAN_INDICATION_DE_EVENT;
    case NAN_MSG_ID_DISABLE_IND:
        return NAN_INDICATION_DISABLED;
    case NAN_MSG_ID_TCA_IND:
        return NAN_INDICATION_TCA;
    case NAN_MSG_ID_BEACON_SDF_IND:
        return NAN_INDICATION_BEACON_SDF_PAYLOAD;
    case NAN_MSG_ID_SELF_TRANSMIT_FOLLOWUP_IND:
        return NAN_INDICATION_SELF_TRANSMIT_FOLLOWUP;
    case NAN_MSG_ID_RANGING_REQUEST_RECEVD_IND:
        return NAN_INDICATION_RANGING_REQUEST_RECEIVED;
    case NAN_MSG_ID_RANGING_RESULT_IND:
        return NAN_INDICATION_RANGING_RESULT;
    default:
        return NAN_INDICATION_UNKNOWN;
    }
}

int NanCommand::getNanPublishReplied(NanPublishRepliedInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanPublishRepliedIndMsg pRsp = (pNanPublishRepliedIndMsg)mNanVendorEvent;
    event->requestor_instance_id = pRsp->publishRepliedIndParams.matchHandle;

    event->rssi_value = 0;
    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;
    int remainingLen = (mNanDataLen - \
        (sizeof(NanMsgHeader)));

    if (remainingLen <= 0) {
        ALOGI("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_MAC_ADDRESS:
            if (outputTlv.length > sizeof(event->addr)) {
                outputTlv.length = sizeof(event->addr);
            }
            memcpy(event->addr, outputTlv.value, outputTlv.length);
            break;
        case NAN_TLV_TYPE_RECEIVED_RSSI_VALUE:
            if (outputTlv.length > sizeof(event->rssi_value)) {
                outputTlv.length = sizeof(event->rssi_value);
            }
            memcpy(&event->rssi_value, outputTlv.value,
                   outputTlv.length);
            break;
        default:
            ALOGI("Unknown TLV type skipped");
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv, 0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}

int NanCommand::getNanPublishTerminated(NanPublishTerminatedInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanPublishTerminatedIndMsg pRsp = (pNanPublishTerminatedIndMsg)mNanVendorEvent;
    event->publish_id = pRsp->fwHeader.handle;
    NanErrorTranslation((NanInternalStatusType)pRsp->reason, 0,
                        (void*)event, false);
    return WIFI_SUCCESS;
}

int NanCommand::getNanMatch(NanMatchInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanMatchIndMsg pRsp = (pNanMatchIndMsg)mNanVendorEvent;
    event->publish_subscribe_id = pRsp->fwHeader.handle;
    event->requestor_instance_id = pRsp->matchIndParams.matchHandle;
    event->match_occured_flag = pRsp->matchIndParams.matchOccuredFlag;
    event->out_of_resource_flag = pRsp->matchIndParams.outOfResourceFlag;

    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;
    int remainingLen = (mNanDataLen - \
        (sizeof(NanMsgHeader) + sizeof(NanMatchIndParams)));
    int ret = 0, idx = 0;

    //Has SDF match filter and service specific info TLV
    if (remainingLen <= 0) {
        ALOGV("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }
    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_SERVICE_SPECIFIC_INFO:
            if (outputTlv.length > NAN_MAX_SERVICE_NAME_LEN) {
                outputTlv.length = NAN_MAX_SERVICE_NAME_LEN;
            }
            event->service_specific_info_len = outputTlv.length;
            memcpy(event->service_specific_info, outputTlv.value,
                   outputTlv.length);
            break;
        case NAN_TLV_TYPE_SDF_MATCH_FILTER:
            if (outputTlv.length > NAN_MAX_MATCH_FILTER_LEN) {
                outputTlv.length = NAN_MAX_MATCH_FILTER_LEN;
            }
            event->sdf_match_filter_len = outputTlv.length;
            memcpy(event->sdf_match_filter, outputTlv.value,
                   outputTlv.length);
            break;
        case NAN_TLV_TYPE_MAC_ADDRESS:
            if (outputTlv.length > sizeof(event->addr)) {
                outputTlv.length = sizeof(event->addr);
            }
            memcpy(event->addr, outputTlv.value, outputTlv.length);
            break;
        case NAN_TLV_TYPE_RECEIVED_RSSI_VALUE:
            if (outputTlv.length > sizeof(event->rssi_value)) {
                outputTlv.length = sizeof(event->rssi_value);
            }
            memcpy(&event->rssi_value, outputTlv.value,
                   outputTlv.length);
            break;
        case NAN_TLV_TYPE_POST_NAN_CONNECTIVITY_CAPABILITIES_RECEIVE:
            if (outputTlv.length != sizeof(u32)) {
                ALOGE("NAN_TLV_TYPE_POST_NAN_CONNECTIVITY_CAPABILITIES_RECEIVE"
                      "Incorrect size:%d expecting %zu", outputTlv.length,
                      sizeof(u32));
                break;
            }
            event->is_conn_capability_valid = 1;
            /* Populate conn_capability from received TLV */
            getNanReceivePostConnectivityCapabilityVal(outputTlv.value,
                                                       &event->conn_capability);
            break;
        case NAN_TLV_TYPE_POST_NAN_DISCOVERY_ATTRIBUTE_RECEIVE:
            /* Populate receive discovery attribute from
               received TLV */
            idx = event->num_rx_discovery_attr;
            ret = getNanReceivePostDiscoveryVal(outputTlv.value,
                                                outputTlv.length,
                                                &event->discovery_attr[idx]);
            if (ret == 0) {
                event->num_rx_discovery_attr++;
            } else {
                ALOGE("NAN_TLV_TYPE_POST_NAN_DISCOVERY_ATTRIBUTE_RECEIVE"
                      "Incorrect");
            }
            break;
        case NAN_TLV_TYPE_FURTHER_AVAILABILITY_MAP:
            /* Populate further availability bitmap from
               received TLV */
            ret = getNanFurtherAvailabilityMap(outputTlv.value,
                                               outputTlv.length,
                                               &event->num_chans,
                                               &event->famchan[0]);
            if (ret < 0)
                ALOGE("NAN_TLV_TYPE_FURTHER_AVAILABILITY_MAP"
                      "Incorrect");
            break;
        case NAN_TLV_TYPE_CLUSTER_ATTRIBUTE:
            if (outputTlv.length > sizeof(event->cluster_attribute)) {
                outputTlv.length = sizeof(event->cluster_attribute);
            }
            memcpy(event->cluster_attribute,
                   outputTlv.value, outputTlv.length);
            event->cluster_attribute_len = outputTlv.length;
            break;
        case NAN_TLV_TYPE_NAN_CSID:
            if (outputTlv.length > sizeof(event->peer_cipher_type)) {
                outputTlv.length = sizeof(event->peer_cipher_type);
            }
            memcpy(&event->peer_cipher_type, outputTlv.value,
                   outputTlv.length);
            break;
        case NAN_TLV_TYPE_NAN_SCID:
            if (outputTlv.length > sizeof(event->scid)) {
                outputTlv.length = sizeof(event->scid);
            }
            event->scid_len = outputTlv.length;
            memcpy(event->scid, outputTlv.value, outputTlv.length);
            break;
        case NAN_TLV_TYPE_SDEA_CTRL_PARAMS:
            if (outputTlv.length != sizeof(u32)) {
                ALOGE("NAN_TLV_TYPE_SDEA_CTRL_PARAMS"
                      "Incorrect size:%d expecting %zu", outputTlv.length,
                      sizeof(u32));
                break;
            }
            getNanReceiveSdeaCtrlParams(outputTlv.value,
                                             &event->peer_sdea_params);
            break;
        case NAN_TLV_TYPE_NAN20_RANGING_RESULT:
            if (outputTlv.length > sizeof(event->range_info)) {
                outputTlv.length = sizeof(event->range_info);
            }
            memcpy(&event->range_info, outputTlv.value, outputTlv.length);
            break;
        case NAN_TLV_TYPE_SDEA_SERVICE_SPECIFIC_INFO:
            if (outputTlv.length > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                outputTlv.length = NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN;
            }
            event->sdea_service_specific_info_len = outputTlv.length;
            memcpy(event->sdea_service_specific_info, outputTlv.value,
                   outputTlv.length);
            break;
        default:
            ALOGV("Unknown TLV type skipped");
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv, 0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}

int NanCommand::getNanMatchExpired(NanMatchExpiredInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanMatchExpiredIndMsg pRsp = (pNanMatchExpiredIndMsg)mNanVendorEvent;
    event->publish_subscribe_id = pRsp->fwHeader.handle;
    event->requestor_instance_id = pRsp->matchExpiredIndParams.matchHandle;
    return WIFI_SUCCESS;
}

int NanCommand::getNanSubscribeTerminated(NanSubscribeTerminatedInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanSubscribeTerminatedIndMsg pRsp = (pNanSubscribeTerminatedIndMsg)mNanVendorEvent;
    event->subscribe_id = pRsp->fwHeader.handle;
    NanErrorTranslation((NanInternalStatusType)pRsp->reason, 0,
                        (void*)event, false);
    return WIFI_SUCCESS;
}

int NanCommand::getNanFollowup(NanFollowupInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanFollowupIndMsg pRsp = (pNanFollowupIndMsg)mNanVendorEvent;
    event->publish_subscribe_id = pRsp->fwHeader.handle;
    event->requestor_instance_id = pRsp->followupIndParams.matchHandle;
    event->dw_or_faw = pRsp->followupIndParams.window;

    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;
    int remainingLen = (mNanDataLen -  \
        (sizeof(NanMsgHeader) + sizeof(NanFollowupIndParams)));

    //Has service specific info and extended service specific info TLV
    if (remainingLen <= 0) {
        ALOGV("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }
    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_SERVICE_SPECIFIC_INFO:
        case NAN_TLV_TYPE_EXT_SERVICE_SPECIFIC_INFO:
            if (outputTlv.length > NAN_MAX_SERVICE_SPECIFIC_INFO_LEN) {
                outputTlv.length = NAN_MAX_SERVICE_SPECIFIC_INFO_LEN;
            }
            event->service_specific_info_len = outputTlv.length;
            memcpy(event->service_specific_info, outputTlv.value,
                   outputTlv.length);
            break;
        case NAN_TLV_TYPE_MAC_ADDRESS:
            if (outputTlv.length > sizeof(event->addr)) {
                outputTlv.length = sizeof(event->addr);
            }
            memcpy(event->addr, outputTlv.value, outputTlv.length);
            break;
        case NAN_TLV_TYPE_SDEA_SERVICE_SPECIFIC_INFO:
            if (outputTlv.length > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                outputTlv.length = NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN;
            }
            event->sdea_service_specific_info_len = outputTlv.length;
            memcpy(event->sdea_service_specific_info, outputTlv.value,
                   outputTlv.length);
            break;
        default:
            ALOGV("Unknown TLV type skipped");
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv, 0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}

int NanCommand::getNanDiscEngEvent(NanDiscEngEventInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanEventIndMsg pRsp = (pNanEventIndMsg)mNanVendorEvent;
    memset(&event->data, 0, sizeof(event->data));

    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;
    int remainingLen = (mNanDataLen -  \
        (sizeof(NanMsgHeader)));

    //Has Self-STA Mac TLV
    if (remainingLen <= 0) {
        ALOGE("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }

    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_EVENT_SELF_STATION_MAC_ADDRESS:
            if (outputTlv.length > NAN_MAC_ADDR_LEN) {
                ALOGV("%s: Reading only first %d bytes of TLV",
                      __func__, NAN_MAC_ADDR_LEN);
                outputTlv.length = NAN_MAC_ADDR_LEN;
            }
            memcpy(event->data.mac_addr.addr, outputTlv.value,
                   outputTlv.length);
            event->event_type = NAN_EVENT_ID_DISC_MAC_ADDR;
            break;
        case NAN_TLV_TYPE_EVENT_STARTED_CLUSTER:
            if (outputTlv.length > NAN_MAC_ADDR_LEN) {
                ALOGV("%s: Reading only first %d bytes of TLV",
                      __func__, NAN_MAC_ADDR_LEN);
                outputTlv.length = NAN_MAC_ADDR_LEN;
            }
            memcpy(event->data.cluster.addr, outputTlv.value,
                   outputTlv.length);
            event->event_type = NAN_EVENT_ID_STARTED_CLUSTER;
            break;
        case NAN_TLV_TYPE_EVENT_JOINED_CLUSTER:
            if (outputTlv.length > NAN_MAC_ADDR_LEN) {
                ALOGV("%s: Reading only first %d bytes of TLV",
                      __func__, NAN_MAC_ADDR_LEN);
                outputTlv.length = NAN_MAC_ADDR_LEN;
            }
            memcpy(event->data.cluster.addr, outputTlv.value,
                   outputTlv.length);
            event->event_type = NAN_EVENT_ID_JOINED_CLUSTER;
            break;
        default:
            ALOGV("Unhandled TLV type:%d", outputTlv.type);
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv,0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}

int NanCommand::getNanDisabled(NanDisabledInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanDisableIndMsg pRsp = (pNanDisableIndMsg)mNanVendorEvent;
    NanErrorTranslation((NanInternalStatusType)pRsp->reason, 0,
                        (void*)event, false);
    return WIFI_SUCCESS;

}

int NanCommand::getNanTca(NanTCAInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanTcaIndMsg pRsp = (pNanTcaIndMsg)mNanVendorEvent;
    memset(&event->data, 0, sizeof(event->data));

    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;

    int remainingLen = (mNanDataLen -  \
        (sizeof(NanMsgHeader)));

    //Has NAN_TCA_ID_CLUSTER_SIZE
    if (remainingLen <= 0) {
        ALOGE("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }

    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_CLUSTER_SIZE_RSP:
            if (outputTlv.length != 2 * sizeof(u32)) {
                ALOGE("%s: Wrong length %d in Tca Indication expecting %zu bytes",
                      __func__, outputTlv.length, 2 * sizeof(u32));
                break;
            }
            event->rising_direction_evt_flag = outputTlv.value[0] & 0x01;
            event->falling_direction_evt_flag = (outputTlv.value[0] & 0x02) >> 1;
            memcpy(&(event->data.cluster.cluster_size), &outputTlv.value[4],
                   sizeof(event->data.cluster.cluster_size));
            event->tca_type = NAN_TCA_ID_CLUSTER_SIZE;
            break;
        default:
            ALOGV("Unhandled TLV type:%d", outputTlv.type);
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv,0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}

int NanCommand::getNanBeaconSdfPayload(NanBeaconSdfPayloadInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanBeaconSdfPayloadIndMsg pRsp = (pNanBeaconSdfPayloadIndMsg)mNanVendorEvent;
    memset(&event->data, 0, sizeof(event->data));

    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;
    int remainingLen = (mNanDataLen -  \
        (sizeof(NanMsgHeader)));

    //Has Mac address
    if (remainingLen <= 0) {
        ALOGV("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }

    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_MAC_ADDRESS:
            if (outputTlv.length > sizeof(event->addr)) {
                outputTlv.length = sizeof(event->addr);
            }
            memcpy(event->addr, outputTlv.value,
                   outputTlv.length);
            break;

        case NAN_TLV_TYPE_VENDOR_SPECIFIC_ATTRIBUTE_RECEIVE:
        {
            NanReceiveVendorSpecificAttribute* recvVsaattr = &event->vsa;
            if (outputTlv.length < sizeof(u32)) {
                ALOGE("NAN_TLV_TYPE_VENDOR_SPECIFIC_ATTRIBUTE_RECEIVE"
                      "Incorrect length:%d", outputTlv.length);
                break;
            }
            event->is_vsa_received = 1;
            recvVsaattr->vsa_received_on = (outputTlv.value[0] >> 1) & 0x07;
            memcpy(&recvVsaattr->vendor_oui, &outputTlv.value[1],
                   3);
            recvVsaattr->attr_len = outputTlv.length - 4;
            if (recvVsaattr->attr_len > NAN_MAX_VSA_DATA_LEN) {
                recvVsaattr->attr_len = NAN_MAX_VSA_DATA_LEN;
            }
            if (recvVsaattr->attr_len) {
                memcpy(recvVsaattr->vsa, &outputTlv.value[4],
                       recvVsaattr->attr_len);
            }
            break;
        }

        case NAN_TLV_TYPE_BEACON_SDF_PAYLOAD_RECEIVE:
            event->is_beacon_sdf_payload_received = 1;
            event->data.frame_len = outputTlv.length;
            if (event->data.frame_len > NAN_MAX_FRAME_DATA_LEN) {
                event->data.frame_len = NAN_MAX_FRAME_DATA_LEN;
            }
            memcpy(&event->data.frame_data, &outputTlv.value[0],
                   event->data.frame_len);
            break;

        default:
            ALOGV("Unhandled TLV Type:%d", outputTlv.type);
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv,0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}

void NanCommand::getNanReceivePostConnectivityCapabilityVal(
    const u8 *pInValue,
    NanReceivePostConnectivityCapability *pRxCapab)
{
    if (pInValue && pRxCapab) {
        pRxCapab->is_mesh_supported = (pInValue[0] & (0x01 << 5));
        pRxCapab->is_ibss_supported = (pInValue[0] & (0x01 << 4));
        pRxCapab->wlan_infra_field = (pInValue[0] & (0x01 << 3));
        pRxCapab->is_tdls_supported = (pInValue[0] & (0x01 << 2));
        pRxCapab->is_wfds_supported = (pInValue[0] & (0x01 << 1));
        pRxCapab->is_wfd_supported = pInValue[0] & 0x01;
    }
}

void NanCommand::getNanReceiveSdeaCtrlParams(const u8* pInValue,
    NanSdeaCtrlParams *pPeerSdeaParams)
{
    if (pInValue && pPeerSdeaParams) {
        pPeerSdeaParams->security_cfg =
                          (NanDataPathSecurityCfgStatus)((pInValue[0] & BIT_6) ?
                           NAN_DP_CONFIG_SECURITY : NAN_DP_CONFIG_NO_SECURITY);
        pPeerSdeaParams->ranging_state =
                           (NanRangingState)((pInValue[0] & BIT_7) ?
                            NAN_RANGING_ENABLE : NAN_RANGING_DISABLE);
#if 0
        pPeerSdeaParams->enable_ranging_limit =
                         (NanRangingLimitState)((pInValue[0] & BIT_8) ?
                          NAN_RANGING_LIMIT_ENABLE : NAN_RANGING_LIMIT_DISABLE);
#endif
    }
    return;
}

int NanCommand::getNanReceivePostDiscoveryVal(const u8 *pInValue,
                                              u32 length,
                                              NanReceivePostDiscovery *pRxDisc)
{
    int ret = 0;

    if (length <= 8 || pInValue == NULL) {
        ALOGE("%s: Invalid Arg TLV Len %d < 4",
              __func__, length);
        return -1;
    }

    pRxDisc->type = (NanConnectionType) pInValue[0];
    pRxDisc->role = (NanDeviceRole) pInValue[1];
    pRxDisc->duration = (NanAvailDuration) (pInValue[2] & 0x03);
    pRxDisc->mapid = ((pInValue[2] >> 2) & 0x0F);
    memcpy(&pRxDisc->avail_interval_bitmap,
           &pInValue[4],
           sizeof(pRxDisc->avail_interval_bitmap));

    u8 *pInputTlv = (u8 *)&pInValue[8];
    NanTlv outputTlv;
    u16 readLen = 0;
    int remainingLen = (length - 8);

    //Has Mac address
    if (remainingLen <= 0) {
        ALOGE("%s: No TLV's present",__func__);
        return -1;
    }

    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_MAC_ADDRESS:
            if (outputTlv.length > sizeof(pRxDisc->addr)) {
                outputTlv.length = sizeof(pRxDisc->addr);
            }
            memcpy(pRxDisc->addr, outputTlv.value, outputTlv.length);
            break;
        case NAN_TLV_TYPE_WLAN_MESH_ID:
            if (outputTlv.length > sizeof(pRxDisc->mesh_id)) {
                outputTlv.length = sizeof(pRxDisc->mesh_id);
            }
            memcpy(pRxDisc->mesh_id, outputTlv.value, outputTlv.length);
            pRxDisc->mesh_id_len = outputTlv.length;
            break;
        case NAN_TLV_TYPE_WLAN_INFRA_SSID:
            if (outputTlv.length > sizeof(pRxDisc->infrastructure_ssid_val)) {
                outputTlv.length = sizeof(pRxDisc->infrastructure_ssid_val);
            }
            memcpy(pRxDisc->infrastructure_ssid_val, outputTlv.value,
                   outputTlv.length);
            pRxDisc->infrastructure_ssid_len = outputTlv.length;
        default:
            ALOGV("Unhandled TLV Type:%d", outputTlv.type);
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv,0, sizeof(outputTlv));
    }
    return ret;
}

int NanCommand::getNanFurtherAvailabilityMap(const u8 *pInValue,
                                             u32 length,
                                             u8 *num_chans,
                                             NanFurtherAvailabilityChannel *pFac)
{
    int idx = 0;

    if ((length == 0) || pInValue == NULL) {
        ALOGE("%s: Invalid Arg TLV Len %d or pInValue NULL",
              __func__, length);
        return -1;
    }

    *num_chans = pInValue[0];
    if (*num_chans > NAN_MAX_FAM_CHANNELS) {
        ALOGE("%s: Unable to accommodate numchans %d",
              __func__, *num_chans);
        return -1;
    }

    if (length < (sizeof(u8) +
        (*num_chans * sizeof(NanFurtherAvailabilityChan)))) {
        ALOGE("%s: Invalid TLV Length", __func__);
        return -1;
    }

    for (idx = 0; idx < *num_chans; idx++) {
        pNanFurtherAvailabilityChan pRsp = \
              (pNanFurtherAvailabilityChan)((u8 *)&pInValue[1] + \
              (idx * sizeof(NanFurtherAvailabilityChan)));

        pFac->entry_control = \
            (NanAvailDuration)(pRsp->entryCtrl.availIntDuration);
        pFac->mapid = pRsp->entryCtrl.mapId;
        pFac->class_val = pRsp->opClass;
        pFac->channel = pRsp->channel;
        memcpy(&pFac->avail_interval_bitmap,
               &pRsp->availIntBitmap,
               sizeof(pFac->avail_interval_bitmap));
        pFac++;
    }
    return 0;
}

int NanCommand::getNanStaParameter(wifi_interface_handle iface,
                                   NanStaParameter *pRsp)
{
    int ret = WIFI_ERROR_NONE;
    int res = -1;
    transaction_id id = 1;
    interface_info *ifaceInfo = getIfaceInfo(iface);

    ret = create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    /*
       Construct NL message to get the sync stats parameter
       which has all the parameter required by staparameter.
    */
    NanStatsRequest syncStats;
    memset(&syncStats, 0, sizeof(syncStats));
    syncStats.stats_type = NAN_STATS_ID_DE_TIMING_SYNC;
    syncStats.clear = 0;

    mStaParam = pRsp;
    ret = putNanStats(id, &syncStats);
    if (ret != 0) {
        ALOGE("%s: putNanStats Error:%d",__func__, ret);
        goto cleanup;
    }
    ret = requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d",__func__, ret);
        goto cleanup;
    }

    struct timespec abstime;
    abstime.tv_sec = 4;
    abstime.tv_nsec = 0;
    res = mCondition.wait(abstime);
    if (res == ETIMEDOUT)
    {
        ALOGE("%s: Time out happened.", __func__);
        ret = WIFI_ERROR_TIMED_OUT;
        goto cleanup;
    }
    ALOGV("%s: NanStaparameter Master_pref:%x," \
          " Random_factor:%x, hop_count:%x " \
          " beacon_transmit_time:%d" \
          " ndp_channel_freq:%d", __func__,
          pRsp->master_pref, pRsp->random_factor,
          pRsp->hop_count, pRsp->beacon_transmit_time, pRsp->ndp_channel_freq);
cleanup:
    mStaParam = NULL;
    return (int)ret;
}

int NanCommand::getNanTransmitFollowupInd(NanTransmitFollowupInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanSelfTransmitFollowupIndMsg pRsp = (pNanSelfTransmitFollowupIndMsg)mNanVendorEvent;
    event->id = pRsp->fwHeader.transactionId;
    NanErrorTranslation((NanInternalStatusType)pRsp->reason, 0,
                        (void*)event, false);
    return WIFI_SUCCESS;
}

//Function which calls the necessaryIndication callback
//based on the indication type
int NanCommand::handleNdpIndication(u32 ndpCmdType, struct nlattr **tb_vendor)
{
    //Based on the message_id in the header determine the Indication type
    //and call the necessary callback handler
    int res = 0;

    ALOGI("handleNdpIndication msg_id:%u", ndpCmdType);
    switch (ndpCmdType) {
    case QCA_WLAN_VENDOR_ATTR_NDP_DATA_REQUEST_IND:
        NanDataPathRequestInd ndpRequestInd;
        memset(&ndpRequestInd, 0, sizeof(ndpRequestInd));

        res = getNdpRequest(tb_vendor, &ndpRequestInd);
        if (!res && mHandler.EventDataRequest) {
            (*mHandler.EventDataRequest)(&ndpRequestInd);
        }
        break;

    case QCA_WLAN_VENDOR_ATTR_NDP_CONFIRM_IND:
        NanDataPathConfirmInd ndpConfirmInd;
        memset(&ndpConfirmInd, 0, sizeof(ndpConfirmInd));

        res = getNdpConfirm(tb_vendor, &ndpConfirmInd);
        if (!res && mHandler.EventDataConfirm) {
            (*mHandler.EventDataConfirm)(&ndpConfirmInd);
        }
        break;

    case QCA_WLAN_VENDOR_ATTR_NDP_END_IND:
    {
        NanDataPathEndInd *ndpEndInd = NULL;
        u8 num_ndp_ids = 0;

        if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID_ARRAY]) {
            ALOGE("%s: QCA_WLAN_VENDOR_ATTR_NDP not found", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }

        num_ndp_ids = (u8)(nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID_ARRAY])/sizeof(u32));
        ALOGD("%s: NDP Num Instance Ids : val %d", __FUNCTION__, num_ndp_ids);

        if (num_ndp_ids) {
            ndpEndInd =
                (NanDataPathEndInd *)malloc(sizeof(NanDataPathEndInd)+ (sizeof(u32) * num_ndp_ids));
            if (!ndpEndInd) {
                ALOGE("%s: ndp_instance_id malloc Failed", __FUNCTION__);
                return WIFI_ERROR_OUT_OF_MEMORY;
            }
            ndpEndInd->num_ndp_instances = num_ndp_ids;
            nla_memcpy(ndpEndInd->ndp_instance_id,
                       tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID_ARRAY],
                       sizeof(u32) * ndpEndInd->num_ndp_instances);
        }
        if (mHandler.EventDataEnd) {
            (*mHandler.EventDataEnd)(ndpEndInd);
        }
        free(ndpEndInd);
        break;
    }
    default:
        ALOGE("handleNdpIndication error invalid ndpCmdType:%u", ndpCmdType);
        res = (int)WIFI_ERROR_INVALID_REQUEST_ID;
        break;
    }
    return res;
}

int NanCommand::getNdpRequest(struct nlattr **tb_vendor,
                              NanDataPathRequestInd *event)
{
    u32 len = 0;

    if (event == NULL || tb_vendor == NULL) {
        ALOGE("%s: Invalid input argument event:%p tb_vendor:%p",
              __FUNCTION__, event, tb_vendor);
        return WIFI_ERROR_INVALID_ARGS;
    }
    if ((!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_INSTANCE_ID]) ||
        (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_PEER_DISCOVERY_MAC_ADDR]) ||
        (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID])) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_NDP not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    event->service_instance_id = nla_get_u16(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_INSTANCE_ID]);
    ALOGD("%s: Service Instance id : val %d", __FUNCTION__, event->service_instance_id);

    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_PEER_DISCOVERY_MAC_ADDR]);
    len = ((sizeof(event->peer_disc_mac_addr) <= len) ? sizeof(event->peer_disc_mac_addr) : len);
    memcpy(&event->peer_disc_mac_addr[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_PEER_DISCOVERY_MAC_ADDR]), len);

    event->ndp_instance_id = nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID]);
    ALOGD("%s: Ndp Instance id: %d", __FUNCTION__, event->ndp_instance_id);
    if (tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO]) {
        len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO]);
        len = ((sizeof(event->app_info.ndp_app_info) <= len) ? sizeof(event->app_info.ndp_app_info) : len);
        memcpy(&event->app_info.ndp_app_info[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO]), len);
        event->app_info.ndp_app_info_len = len;
    } else {
        ALOGD("%s: NDP App Info not present", __FUNCTION__);
    }
    return WIFI_SUCCESS;
}

int NanCommand::getNdpConfirm(struct nlattr **tb_vendor,
                              NanDataPathConfirmInd *event)
{
    u32 len = 0;
    NanInternalStatusType drv_reason_code;

    if (event == NULL || tb_vendor == NULL) {
        ALOGE("%s: Invalid input argument event:%p tb_vendor:%p",
              __FUNCTION__, event, tb_vendor);
        return WIFI_ERROR_INVALID_ARGS;
    }
    if ((!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID]) ||
        (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_NDI_MAC_ADDR]) ||
        (!tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_RESPONSE_CODE])) {
        ALOGE("%s: QCA_WLAN_VENDOR_ATTR_NDP not found", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    event->ndp_instance_id = nla_get_u16(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID]);
    ALOGD("%s: Service Instance id : val %d", __FUNCTION__, event->ndp_instance_id);

    len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_NDI_MAC_ADDR]);
    len = ((sizeof(event->peer_ndi_mac_addr) <= len) ? sizeof(event->peer_ndi_mac_addr) : len);
    memcpy(&event->peer_ndi_mac_addr[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_NDI_MAC_ADDR]), len);

    event->rsp_code = (NanDataPathResponseCode)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_RESPONSE_CODE]);
    ALOGD("%s: Response code %d", __FUNCTION__, event->rsp_code);

    if (tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO]) {
        len = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO]);
        len = ((sizeof(event->app_info.ndp_app_info) <= len) ? sizeof(event->app_info.ndp_app_info) : len);
        memcpy(&event->app_info.ndp_app_info[0], nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO]), len);
        event->app_info.ndp_app_info_len = len;
    } else {
        ALOGD("%s: NDP App Info not present", __FUNCTION__);
    }
    drv_reason_code = (NanInternalStatusType)nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_DRV_RETURN_VALUE]);
    ALOGD("%s: Drv reason code %d", __FUNCTION__, drv_reason_code);
    switch (drv_reason_code) {
        case NDP_I_MGMT_FRAME_REQUEST_FAILED:
        case NDP_I_MGMT_FRAME_RESPONSE_FAILED:
        case NDP_I_MGMT_FRAME_CONFIRM_FAILED:
        case NDP_I_MGMT_FRAME_SECURITY_INSTALL_FAILED:
            event->reason_code = NAN_STATUS_PROTOCOL_FAILURE;
            break;
        default:
            event->reason_code = (NanStatusType)drv_reason_code;
            break;
    }
    ALOGD("%s: Reason code %d", __FUNCTION__, event->reason_code);
    return WIFI_SUCCESS;
}

int NanCommand::getNanRangeRequestReceivedInd(NanRangeRequestInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanFWRangeReqRecvdInd pRsp = (pNanFWRangeReqRecvdInd)mNanVendorEvent;

    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;

    int remainingLen = (mNanDataLen -  \
        (sizeof(NanMsgHeader)));

    if (remainingLen <= 0) {
        ALOGE("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }

    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_NAN20_RANGING_REQUEST_RECEIVED:
            NanFWRangeReqRecvdMsg fwRangeReqRecvd;
            if (outputTlv.length > sizeof(fwRangeReqRecvd)) {
                outputTlv.length = sizeof(fwRangeReqRecvd);
            }
            memcpy(&fwRangeReqRecvd, outputTlv.value, outputTlv.length);
            FW_MAC_ADDR_TO_CHAR_ARRAY(fwRangeReqRecvd.range_mac_addr, event->range_req_intf_addr);
            event->publish_id = fwRangeReqRecvd.range_id;
            break;
        default:
            ALOGV("Unhandled TLV type:%d", outputTlv.type);
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv,0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}

int NanCommand::getNanRangeReportInd(NanRangeReportInd *event)
{
    if (event == NULL || mNanVendorEvent == NULL) {
        ALOGE("%s: Invalid input argument event:%p mNanVendorEvent:%p",
              __func__, event, mNanVendorEvent);
        return WIFI_ERROR_INVALID_ARGS;
    }

    pNanFWRangeReportInd pRsp = (pNanFWRangeReportInd)mNanVendorEvent;

    u8 *pInputTlv = pRsp->ptlv;
    NanTlv outputTlv;
    u16 readLen = 0;

    int remainingLen = (mNanDataLen -  \
        (sizeof(NanMsgHeader)));

    if (remainingLen <= 0) {
        ALOGE("%s: No TLV's present",__func__);
        return WIFI_SUCCESS;
    }

    ALOGV("%s: TLV remaining Len:%d",__func__, remainingLen);
    while ((remainingLen > 0) &&
           (0 != (readLen = NANTLV_ReadTlv(pInputTlv, &outputTlv)))) {
        ALOGV("%s: Remaining Len:%d readLen:%d type:%d length:%d",
              __func__, remainingLen, readLen, outputTlv.type,
              outputTlv.length);
        switch (outputTlv.type) {
        case NAN_TLV_TYPE_MAC_ADDRESS:
            if (outputTlv.length > NAN_MAC_ADDR_LEN) {
                outputTlv.length = NAN_MAC_ADDR_LEN;
            }
            memcpy(event->range_req_intf_addr, outputTlv.value, outputTlv.length);
            break;

        case NAN_TLV_TYPE_NAN20_RANGING_RESULT:
            NanFWRangeReportParams range_params;
            if (outputTlv.length > sizeof(NanFWRangeReportParams)) {
                outputTlv.length = sizeof(NanFWRangeReportParams);
            }
            memcpy(&range_params, outputTlv.value, outputTlv.length);
            event->range_measurement_cm = range_params.range_measurement;
            event->publish_id = range_params.publish_id;
//          event->event_type = range_params.event_type;
            break;
        default:
            ALOGV("Unhandled TLV type:%d", outputTlv.type);
            break;
        }
        remainingLen -= readLen;
        pInputTlv += readLen;
        memset(&outputTlv,0, sizeof(outputTlv));
    }
    return WIFI_SUCCESS;
}
