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

#include "wifi_hal.h"
#include "nan_i.h"
#include "common.h"
#include "cpp_bindings.h"
#include <utils/Log.h>
#include <errno.h>
#include "nancommand.h"
#include "vendor_definitions.h"

#ifdef __GNUC__
#define PRINTF_FORMAT(a,b) __attribute__ ((format (printf, (a), (b))))
#define STRUCT_PACKED __attribute__ ((packed))
#else
#define PRINTF_FORMAT(a,b)
#define STRUCT_PACKED
#endif

#define OUT_OF_BAND_SERVICE_INSTANCE_ID 0

//Singleton Static Instance
NanCommand* NanCommand::mNanCommandInstance  = NULL;

//Implementation of the functions exposed in nan.h
wifi_error nan_register_handler(wifi_interface_handle iface,
                                NanCallbackHandler handlers)
{
    // Obtain the singleton instance
    int ret = 0;
    NanCommand *nanCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = NanCommand::instance(wifiHandle);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->setCallbackHandler(handlers);
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error nan_get_version(wifi_handle handle,
                           NanVersion* version)
{
    *version = (NAN_MAJOR_VERSION <<16 | NAN_MINOR_VERSION << 8 | NAN_MICRO_VERSION);
    return WIFI_SUCCESS;
}

/*  Function to send enable request to the wifi driver.*/
wifi_error nan_enable_request(transaction_id id,
                              wifi_interface_handle iface,
                              NanEnableRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanEnable(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanEnable Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send disable request to the wifi driver.*/
wifi_error nan_disable_request(transaction_id id,
                               wifi_interface_handle iface)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanDisable(id);
    if (ret != 0) {
        ALOGE("%s: putNanDisable Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send publish request to the wifi driver.*/
wifi_error nan_publish_request(transaction_id id,
                               wifi_interface_handle iface,
                               NanPublishRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanPublish(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanPublish Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send publish cancel to the wifi driver.*/
wifi_error nan_publish_cancel_request(transaction_id id,
                                      wifi_interface_handle iface,
                                      NanPublishCancelRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanPublishCancel(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanPublishCancel Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send Subscribe request to the wifi driver.*/
wifi_error nan_subscribe_request(transaction_id id,
                                 wifi_interface_handle iface,
                                 NanSubscribeRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanSubscribe(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanSubscribe Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to cancel subscribe to the wifi driver.*/
wifi_error nan_subscribe_cancel_request(transaction_id id,
                                        wifi_interface_handle iface,
                                        NanSubscribeCancelRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanSubscribeCancel(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanSubscribeCancel Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send NAN follow up request to the wifi driver.*/
wifi_error nan_transmit_followup_request(transaction_id id,
                                         wifi_interface_handle iface,
                                         NanTransmitFollowupRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanTransmitFollowup(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanTransmitFollowup Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send NAN statistics request to the wifi driver.*/
wifi_error nan_stats_request(transaction_id id,
                             wifi_interface_handle iface,
                             NanStatsRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanStats(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanStats Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send NAN configuration request to the wifi driver.*/
wifi_error nan_config_request(transaction_id id,
                              wifi_interface_handle iface,
                              NanConfigRequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanConfig(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanConfig Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send NAN request to the wifi driver.*/
wifi_error nan_tca_request(transaction_id id,
                           wifi_interface_handle iface,
                           NanTCARequest* msg)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanTCA(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanTCA Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to send NAN Beacon sdf payload to the wifi driver.
    This instructs the Discovery Engine to begin publishing the
    received payload in any Beacon or Service Discovery Frame
    transmitted*/
wifi_error nan_beacon_sdf_payload_request(transaction_id id,
                                         wifi_interface_handle iface,
                                         NanBeaconSdfPayloadRequest* msg)
{
    int ret = WIFI_ERROR_NOT_SUPPORTED;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanBeaconSdfPayload(id, msg);
    if (ret != 0) {
        ALOGE("%s: putNanBeaconSdfPayload Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }

cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error nan_get_sta_parameter(transaction_id id,
                                 wifi_interface_handle iface,
                                 NanStaParameter* msg)
{
    int ret = WIFI_ERROR_NOT_SUPPORTED;
    NanCommand *nanCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = NanCommand::instance(wifiHandle);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->getNanStaParameter(iface, msg);
    if (ret != 0) {
        ALOGE("%s: getNanStaParameter Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to get NAN capabilities */
wifi_error nan_get_capabilities(transaction_id id,
                                wifi_interface_handle iface)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanCapabilities(id);
    if (ret != 0) {
        ALOGE("%s: putNanCapabilities Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to get NAN capabilities */
wifi_error nan_debug_command_config(transaction_id id,
                                   wifi_interface_handle iface,
                                   NanDebugParams debug,
                                   int debug_msg_length)
{
    int ret = 0;
    NanCommand *nanCommand = NULL;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    nanCommand = new NanCommand(wifiHandle,
                                0,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (nanCommand == NULL) {
        ALOGE("%s: Error NanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    if (debug_msg_length <= 0) {
        ALOGE("%s: Invalid debug message length = %d", __FUNCTION__,
                                                       debug_msg_length);
        return WIFI_ERROR_UNKNOWN;
    }

    ret = nanCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = nanCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    ret = nanCommand->putNanDebugCommand(debug, debug_msg_length);
    if (ret != 0) {
        ALOGE("%s: putNanDebugCommand Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d",__FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return (wifi_error)ret;
}

wifi_error nan_initialize_vendor_cmd(wifi_interface_handle iface,
                                     NanCommand **nanCommand)
{
    int ret = 0;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    if (nanCommand == NULL) {
        ALOGE("%s: Error nanCommand NULL", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    *nanCommand = new NanCommand(wifiHandle,
                                 0,
                                 OUI_QCA,
                                 QCA_NL80211_VENDOR_SUBCMD_NDP);
    if (*nanCommand == NULL) {
        ALOGE("%s: Object creation failed", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    /* Create the message */
    ret = (*nanCommand)->create();
    if (ret < 0)
        goto cleanup;

    ret = (*nanCommand)->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    return WIFI_SUCCESS;
cleanup:
    delete *nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error nan_data_interface_create(transaction_id id,
                                     wifi_interface_handle iface,
                                     char* iface_name)
{
    ALOGV("NAN_DP_INTERFACE_CREATE");
    int ret = WIFI_SUCCESS;
    struct nlattr *nlData;
    NanCommand *nanCommand = NULL;

    if (iface_name == NULL) {
        ALOGE("%s: Invalid Nan Data Interface Name. \n", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return mapErrorKernelToWifiHAL(ret);
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_CREATE) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            iface_name)) {
        goto cleanup;
    }

    nanCommand->attr_end(nlData);
    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error nan_data_interface_delete(transaction_id id,
                                     wifi_interface_handle iface,
                                     char* iface_name)
{
    ALOGV("NAN_DP_INTERFACE_DELETE");
    int ret = WIFI_SUCCESS;
    struct nlattr *nlData;
    NanCommand *nanCommand = NULL;

    if (iface_name == NULL) {
        ALOGE("%s: Invalid Nan Data Interface Name. \n", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return mapErrorKernelToWifiHAL(ret);
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_DELETE) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            iface_name)) {
        goto cleanup;
    }

    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error nan_data_request_initiator(transaction_id id,
                                      wifi_interface_handle iface,
                                      NanDataPathInitiatorRequest* msg)
{
    ALOGV("NAN_DP_REQUEST_INITIATOR");
    int ret = WIFI_SUCCESS;
    struct nlattr *nlData, *nlCfgSecurity, *nlCfgQos;
    NanCommand *nanCommand = NULL;

    if (msg == NULL)
        return WIFI_ERROR_INVALID_ARGS;

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return mapErrorKernelToWifiHAL(ret);
    }

    if ((msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) &&
        (msg->key_info.body.pmk_info.pmk_len == 0) &&
        (msg->key_info.body.passphrase_info.passphrase_len == 0)) {
        ALOGE("%s: Failed-Initiator req, missing pmk and passphrase",
               __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    if ((msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) &&
        (msg->requestor_instance_id == OUT_OF_BAND_SERVICE_INSTANCE_ID) &&
        (msg->service_name_len == 0)) {
        ALOGE("%s: Failed-Initiator req, missing service name for out of band request",
              __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_INITIATOR_REQUEST) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_INSTANCE_ID,
            msg->requestor_instance_id) ||
        nanCommand->put_bytes(
            QCA_WLAN_VENDOR_ATTR_NDP_PEER_DISCOVERY_MAC_ADDR,
            (char *)msg->peer_disc_mac_addr,
            NAN_MAC_ADDR_LEN) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            msg->ndp_iface)) {
        goto cleanup;
    }

    if (msg->channel_request_type != NAN_DP_CHANNEL_NOT_REQUESTED) {
        if (nanCommand->put_u32 (
                QCA_WLAN_VENDOR_ATTR_NDP_CHANNEL_CONFIG,
                msg->channel_request_type) ||
            nanCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_NDP_CHANNEL,
                msg->channel))
            goto cleanup;
    }

    if (msg->app_info.ndp_app_info_len != 0) {
        if (nanCommand->put_bytes(
                QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO,
                (char *)msg->app_info.ndp_app_info,
                msg->app_info.ndp_app_info_len)) {
            goto cleanup;
        }
    }
    if (msg->ndp_cfg.security_cfg == NAN_DP_CONFIG_SECURITY) {
        nlCfgSecurity =
            nanCommand->attr_start(QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_SECURITY);
        if (!nlCfgSecurity)
            goto cleanup;

        if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SECURITY_TYPE,
            0)) {
            goto cleanup;
        }
        nanCommand->attr_end(nlCfgSecurity);
    }
    if (msg->ndp_cfg.qos_cfg == NAN_DP_CONFIG_QOS) {
        nlCfgQos =
            nanCommand->attr_start(QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_QOS);
        if (!nlCfgQos)
            goto cleanup;
        /* TBD Qos Info */
        nanCommand->attr_end(nlCfgQos);
    }
    if (msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) {
        if (nanCommand->put_u32(QCA_WLAN_VENDOR_ATTR_NDP_CSID,
                msg->cipher_type))
            goto cleanup;
    }
    if ( msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK &&
         msg->key_info.body.pmk_info.pmk_len == NAN_PMK_INFO_LEN) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PMK,
            (char *)msg->key_info.body.pmk_info.pmk,
            msg->key_info.body.pmk_info.pmk_len))
            goto cleanup;
    } else if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE &&
        msg->key_info.body.passphrase_info.passphrase_len >=
        NAN_SECURITY_MIN_PASSPHRASE_LEN &&
        msg->key_info.body.passphrase_info.passphrase_len <=
        NAN_SECURITY_MAX_PASSPHRASE_LEN) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PASSPHRASE,
            (char *)msg->key_info.body.passphrase_info.passphrase,
            msg->key_info.body.passphrase_info.passphrase_len))
            goto cleanup;
    }
    if (msg->service_name_len) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_NAME,
            (char *)msg->service_name, msg->service_name_len))
            goto cleanup;
    }
    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error nan_data_indication_response(transaction_id id,
                                        wifi_interface_handle iface,
                                        NanDataPathIndicationResponse* msg)
{
    ALOGV("NAN_DP_INDICATION_RESPONSE");
    int ret = WIFI_SUCCESS;
    struct nlattr *nlData, *nlCfgSecurity, *nlCfgQos;
    NanCommand *nanCommand = NULL;

    if (msg == NULL)
        return WIFI_ERROR_INVALID_ARGS;

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return mapErrorKernelToWifiHAL(ret);
    }

    if ((msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) &&
        (msg->key_info.body.pmk_info.pmk_len == 0) &&
        (msg->key_info.body.passphrase_info.passphrase_len == 0)) {
        ALOGE("%s: Failed-Initiator req, missing pmk and passphrase",
               __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_RESPONDER_REQUEST) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID,
            msg->ndp_instance_id) ||
        nanCommand->put_string(
            QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
            msg->ndp_iface) ||
        nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_RESPONSE_CODE,
            msg->rsp_code)) {
        goto cleanup;
    }
    if (msg->app_info.ndp_app_info_len != 0) {
        if (nanCommand->put_bytes(
                QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO,
                (char *)msg->app_info.ndp_app_info,
                msg->app_info.ndp_app_info_len)) {
            goto cleanup;
        }
    }
    if (msg->ndp_cfg.security_cfg == NAN_DP_CONFIG_SECURITY) {
        nlCfgSecurity =
            nanCommand->attr_start(QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_SECURITY);
        if (!nlCfgSecurity)
            goto cleanup;
        /* Setting value to 0 for now */
        if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SECURITY_TYPE,
            0)) {
            goto cleanup;
        }
        nanCommand->attr_end(nlCfgSecurity);
    }
    if (msg->ndp_cfg.qos_cfg == NAN_DP_CONFIG_QOS) {
        nlCfgQos =
            nanCommand->attr_start(QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_QOS);
        if (!nlCfgQos)
            goto cleanup;

        /* TBD Qos Info */
        nanCommand->attr_end(nlCfgQos);
    }
    if (msg->cipher_type != NAN_CIPHER_SUITE_SHARED_KEY_NONE) {
        if (nanCommand->put_u32(QCA_WLAN_VENDOR_ATTR_NDP_CSID,
                msg->cipher_type))
            goto cleanup;
    }
    if ( msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK &&
         msg->key_info.body.pmk_info.pmk_len == NAN_PMK_INFO_LEN) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PMK,
            (char *)msg->key_info.body.pmk_info.pmk,
            msg->key_info.body.pmk_info.pmk_len))
            goto cleanup;
    } else if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE &&
        msg->key_info.body.passphrase_info.passphrase_len >=
        NAN_SECURITY_MIN_PASSPHRASE_LEN &&
        msg->key_info.body.passphrase_info.passphrase_len <=
        NAN_SECURITY_MAX_PASSPHRASE_LEN) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_PASSPHRASE,
            (char *)msg->key_info.body.passphrase_info.passphrase,
            msg->key_info.body.passphrase_info.passphrase_len))
            goto cleanup;
    }

    if (msg->service_name_len) {
        if (nanCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_NAME,
            (char *)msg->service_name, msg->service_name_len))
            goto cleanup;
    }
    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error nan_data_end(transaction_id id,
                        wifi_interface_handle iface,
                        NanDataPathEndRequest* msg)
{
    ALOGV("NAN_DP_END");
    int ret = WIFI_SUCCESS;
    struct nlattr *nlData;
    NanCommand *nanCommand = NULL;

    if (msg == NULL)
        return WIFI_ERROR_INVALID_ARGS;

    ret = nan_initialize_vendor_cmd(iface,
                                    &nanCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return mapErrorKernelToWifiHAL(ret);
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = nanCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (nanCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
            QCA_WLAN_VENDOR_ATTR_NDP_END_REQUEST) ||
        nanCommand->put_u16(
            QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
            id) ||
        nanCommand->put_bytes(
            QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID_ARRAY,
            (char *)msg->ndp_instance_id,
            msg->num_ndp_instances * sizeof(u32))) {
        goto cleanup;
    }
    nanCommand->attr_end(nlData);

    ret = nanCommand->requestEvent();
    if (ret != 0) {
        ALOGE("%s: requestEvent Error:%d", __FUNCTION__, ret);
    }
cleanup:
    delete nanCommand;
    return mapErrorKernelToWifiHAL(ret);
}

// Implementation related to nan class common functions
// Constructor
//Making the constructor private since this class is a singleton
NanCommand::NanCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    memset(&mHandler, 0,sizeof(mHandler));
    mNanVendorEvent = NULL;
    mNanDataLen = 0;
    mStaParam = NULL;
}

NanCommand* NanCommand::instance(wifi_handle handle)
{
    if (handle == NULL) {
        ALOGE("Handle is invalid");
        return NULL;
    }
    if (mNanCommandInstance == NULL) {
        mNanCommandInstance = new NanCommand(handle, 0,
                                             OUI_QCA,
                                             QCA_NL80211_VENDOR_SUBCMD_NAN);
        ALOGV("NanCommand %p created", mNanCommandInstance);
        return mNanCommandInstance;
    } else {
        if (handle != getWifiHandle(mNanCommandInstance->mInfo)) {
            /* upper layer must have cleaned up the handle and reinitialized,
               so we need to update the same */
            ALOGI("Handle different, update the handle");
            mNanCommandInstance->mInfo = (hal_info *)handle;
        }
    }
    ALOGV("NanCommand %p created already", mNanCommandInstance);
    return mNanCommandInstance;
}

void NanCommand::cleanup()
{
    //free the VendorData
    if (mVendorData) {
        free(mVendorData);
    }
    mVendorData = NULL;
    //cleanup the mMsg
    mMsg.destroy();
}

NanCommand::~NanCommand()
{
    ALOGV("NanCommand %p destroyed", this);
}

int NanCommand::handleResponse(WifiEvent &reply){
    return NL_SKIP;
}

int NanCommand::setCallbackHandler(NanCallbackHandler nHandler)
{
    int res = WIFI_SUCCESS;
    mHandler = nHandler;
    res = registerVendorHandler(mVendor_id, QCA_NL80211_VENDOR_SUBCMD_NAN);
    if (res != 0) {
        //error case should not happen print log
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x"
              "subcmd=QCA_NL80211_VENDOR_SUBCMD_NAN", __FUNCTION__, mVendor_id);
        return res;
    }

    res = registerVendorHandler(mVendor_id, QCA_NL80211_VENDOR_SUBCMD_NDP);
    if (res != 0) {
        //error case should not happen print log
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x"
              "subcmd=QCA_NL80211_VENDOR_SUBCMD_NDP", __FUNCTION__, mVendor_id);
        return res;
    }
    return res;
}

/* This function implements creation of Vendor command */
int NanCommand::create() {
    int ret = mMsg.create(NL80211_CMD_VENDOR, 0, 0);
    if (ret < 0) {
        goto out;
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
    if (ret < 0) {
        mMsg.destroy();
    }
    return ret;
}

// This function will be the main handler for incoming event
// QCA_NL80211_VENDOR_SUBCMD_NAN
//Call the appropriate callback handler after parsing the vendor data.
int NanCommand::handleEvent(WifiEvent &event)
{
    WifiVendorCommand::handleEvent(event);
    ALOGV("%s: Subcmd=%u Vendor data len received:%d",
          __FUNCTION__, mSubcmd, mDataLen);
    hexdump(mVendorData, mDataLen);

    if (mSubcmd == QCA_NL80211_VENDOR_SUBCMD_NAN){
        // Parse the vendordata and get the NAN attribute
        struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_MAX + 1];
        nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_MAX,
                  (struct nlattr *)mVendorData,
                  mDataLen, NULL);
        // Populating the mNanVendorEvent and mNanDataLen to point to NAN data.
        mNanVendorEvent = (char *)nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_NAN]);
        mNanDataLen = nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_NAN]);

        if (isNanResponse()) {
            //handleNanResponse will parse the data and call
            //the response callback handler with the populated
            //NanResponseMsg
            handleNanResponse();
        } else {
            //handleNanIndication will parse the data and call
            //the corresponding Indication callback handler
            //with the corresponding populated Indication event
            handleNanIndication();
        }
    } else if (mSubcmd == QCA_NL80211_VENDOR_SUBCMD_NDP) {
        // Parse the vendordata and get the NAN attribute
        u32 ndpCmdType;
        struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_AFTER_LAST + 1];
        nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_NDP_MAX,
                  (struct nlattr *)mVendorData,
                  mDataLen, NULL);

        if (tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD]) {
            ndpCmdType =
                nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD]);
                ALOGD("%s: NDP Cmd Type : val 0x%x",
                      __FUNCTION__, ndpCmdType);
                switch (ndpCmdType) {
                case QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_CREATE:
                    handleNdpResponse(NAN_DP_INTERFACE_CREATE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_INTERFACE_DELETE:
                    handleNdpResponse(NAN_DP_INTERFACE_DELETE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_INITIATOR_RESPONSE:
                    handleNdpResponse(NAN_DP_INITIATOR_RESPONSE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_RESPONDER_RESPONSE:
                    handleNdpResponse(NAN_DP_RESPONDER_RESPONSE, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_END_RESPONSE:
                    handleNdpResponse(NAN_DP_END, tb_vendor);
                    break;
                case QCA_WLAN_VENDOR_ATTR_NDP_DATA_REQUEST_IND:
                case QCA_WLAN_VENDOR_ATTR_NDP_CONFIRM_IND:
                case QCA_WLAN_VENDOR_ATTR_NDP_END_IND:
                    handleNdpIndication(ndpCmdType, tb_vendor);
                    break;
                default:
                    ALOGE("%s: Invalid NDP subcmd response received %d",
                          __FUNCTION__, ndpCmdType);
                }
        }
    } else {
        //error case should not happen print log
        ALOGE("%s: Wrong NAN subcmd received %d", __FUNCTION__, mSubcmd);
    }
    return NL_SKIP;
}

/*Helper function to Write and Read TLV called in indication as well as request */
u16 NANTLV_WriteTlv(pNanTlv pInTlv, u8 *pOutTlv)
{
    u16 writeLen = 0;
    u16 i;

    if (!pInTlv)
    {
        ALOGE("NULL pInTlv");
        return writeLen;
    }

    if (!pOutTlv)
    {
        ALOGE("NULL pOutTlv");
        return writeLen;
    }

    *pOutTlv++ = pInTlv->type & 0xFF;
    *pOutTlv++ = (pInTlv->type & 0xFF00) >> 8;
    writeLen += 2;

    ALOGV("WRITE TLV type %u, writeLen %u", pInTlv->type, writeLen);

    *pOutTlv++ = pInTlv->length & 0xFF;
    *pOutTlv++ = (pInTlv->length & 0xFF00) >> 8;
    writeLen += 2;

    ALOGV("WRITE TLV length %u, writeLen %u", pInTlv->length, writeLen);

    for (i=0; i < pInTlv->length; ++i)
    {
        *pOutTlv++ = pInTlv->value[i];
    }

    writeLen += pInTlv->length;
    ALOGV("WRITE TLV value, writeLen %u", writeLen);
    return writeLen;
}

u16 NANTLV_ReadTlv(u8 *pInTlv, pNanTlv pOutTlv)
{
    u16 readLen = 0;

    if (!pInTlv)
    {
        ALOGE("NULL pInTlv");
        return readLen;
    }

    if (!pOutTlv)
    {
        ALOGE("NULL pOutTlv");
        return readLen;
    }

    pOutTlv->type = *pInTlv++;
    pOutTlv->type |= *pInTlv++ << 8;
    readLen += 2;

    ALOGV("READ TLV type %u, readLen %u", pOutTlv->type, readLen);

    pOutTlv->length = *pInTlv++;
    pOutTlv->length |= *pInTlv++ << 8;
    readLen += 2;

    ALOGV("READ TLV length %u, readLen %u", pOutTlv->length, readLen);

    if (pOutTlv->length) {
        pOutTlv->value = pInTlv;
        readLen += pOutTlv->length;
    } else {
        pOutTlv->value = NULL;
    }

    ALOGV("READ TLV  readLen %u", readLen);
    return readLen;
}

u8* addTlv(u16 type, u16 length, const u8* value, u8* pOutTlv)
{
   NanTlv nanTlv;
   u16 len;

   nanTlv.type = type;
   nanTlv.length = length;
   nanTlv.value = (u8*)value;

   len = NANTLV_WriteTlv(&nanTlv, pOutTlv);
   return (pOutTlv + len);
}
