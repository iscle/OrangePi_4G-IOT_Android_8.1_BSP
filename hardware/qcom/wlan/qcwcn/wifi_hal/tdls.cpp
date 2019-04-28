/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "sync.h"

#define LOG_TAG  "WifiHAL"

#include <utils/Log.h>

#include "wifi_hal.h"
#include "common.h"
#include "cpp_bindings.h"
#include "tdlsCommand.h"
#include "vendor_definitions.h"

/* Singleton Static Instance */
TdlsCommand* TdlsCommand::mTdlsCommandInstance  = NULL;
TdlsCommand::TdlsCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    memset(&mHandler, 0, sizeof(mHandler));
    memset(&mTDLSgetStatusRspParams, 0, sizeof(wifi_tdls_status));
    mRequestId = 0;
}

TdlsCommand::~TdlsCommand()
{
    mTdlsCommandInstance = NULL;
    unregisterVendorHandler(mVendor_id, mSubcmd);
}

TdlsCommand* TdlsCommand::instance(wifi_handle handle)
{
    if (handle == NULL) {
        ALOGE("Interface Handle is invalid");
        return NULL;
    }
    if (mTdlsCommandInstance == NULL) {
        mTdlsCommandInstance = new TdlsCommand(handle, 0,
                OUI_QCA,
                QCA_NL80211_VENDOR_SUBCMD_TDLS_ENABLE);
        ALOGV("TdlsCommand %p created", mTdlsCommandInstance);
        return mTdlsCommandInstance;
    }
    else
    {
        if (handle != getWifiHandle(mTdlsCommandInstance->mInfo))
        {
            /* upper layer must have cleaned up the handle and reinitialized,
               so we need to update the same */
            ALOGV("Handle different, update the handle");
            mTdlsCommandInstance->mInfo = (hal_info *)handle;
        }
    }
    ALOGV("TdlsCommand %p created already", mTdlsCommandInstance);
    return mTdlsCommandInstance;
}

void TdlsCommand::setSubCmd(u32 subcmd)
{
    mSubcmd = subcmd;
}

/* This function will be the main handler for incoming event SUBCMD_TDLS
 * Call the appropriate callback handler after parsing the vendor data.
 */
int TdlsCommand::handleEvent(WifiEvent &event)
{
    ALOGV("Got a TDLS message from Driver");
    WifiVendorCommand::handleEvent(event);

    /* Parse the vendordata and get the attribute */
    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_TDLS_STATE:
            {
                struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_STATE_MAX
                    + 1];
                mac_addr addr;
                wifi_tdls_status status;

                memset(&addr, 0, sizeof(mac_addr));
                memset(&status, 0, sizeof(wifi_tdls_status));
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_TDLS_STATE_MAX,
                        (struct nlattr *)mVendorData,
                        mDataLen, NULL);

                ALOGV("QCA_NL80211_VENDOR_SUBCMD_TDLS_STATE Received");
                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_MAC_ADDR])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_MAC_ADDR not found",
                            __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                memcpy(addr,
                  (u8 *)nla_data(tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_MAC_ADDR]),
                  nla_len(tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_MAC_ADDR]));

                ALOGV(MAC_ADDR_STR, MAC_ADDR_ARRAY(addr));

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_STATE])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_STATE not found",
                            __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                status.state = (wifi_tdls_state)
                    get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_STATE]);
                ALOGV("TDLS: State New : %d ", status.state);

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_REASON])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_REASON not found",
                            __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                status.reason = (wifi_tdls_reason)
                    get_s32(tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_REASON]);
                ALOGV("TDLS: Reason : %d ", status.reason);

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_CHANNEL])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_CHANNEL not found",
                            __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                status.channel =
                    get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_CHANNEL]);
                ALOGV("TDLS: channel : %d ", status.channel);

                if (!tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_TDLS_GLOBAL_OPERATING_CLASS])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_GLOBAL_OPERATING_CLASS"
                            " not found", __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                status.global_operating_class = get_u32(
                   tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_GLOBAL_OPERATING_CLASS]);
                ALOGV("TDLS: global_operating_class: %d ",
                        status.global_operating_class);

                if (mHandler.on_tdls_state_changed)
                    (*mHandler.on_tdls_state_changed)(addr, status);
                else
                    ALOGE("TDLS: No Callback registered: ");
            }
            break;

        default:
            /* Error case should not happen print log */
            ALOGE("%s: Wrong TDLS subcmd received %d", __FUNCTION__, mSubcmd);
    }

    return NL_SKIP;
}

int TdlsCommand::handleResponse(WifiEvent &reply)
{
    WifiVendorCommand::handleResponse(reply);

    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_TDLS_GET_STATUS:
            {
                struct nlattr *tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_MAX + 1];
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_MAX,
                        (struct nlattr *)mVendorData,
                        mDataLen, NULL);

                ALOGV("QCA_NL80211_VENDOR_SUBCMD_TDLS_GET_STATUS Received");
                memset(&mTDLSgetStatusRspParams, 0, sizeof(wifi_tdls_status));

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_STATE])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_STATE"
                            " not found", __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                mTDLSgetStatusRspParams.state = (wifi_tdls_state)get_u32(
                        tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_STATE]);
                ALOGV("TDLS: State : %u ", mTDLSgetStatusRspParams.state);

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_REASON])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_REASON"
                            " not found", __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                mTDLSgetStatusRspParams.reason = (wifi_tdls_reason)get_s32(
                        tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_REASON]);
                ALOGV("TDLS: Reason : %d ", mTDLSgetStatusRspParams.reason);

                if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_CHANNEL])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_CHANNEL"
                            " not found", __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                mTDLSgetStatusRspParams.channel = get_u32(tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_CHANNEL]);
                ALOGV("TDLS: channel : %d ", mTDLSgetStatusRspParams.channel);

                if (!tb_vendor[
                  QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_GLOBAL_OPERATING_CLASS])
                {
                    ALOGE("%s:"
                   "QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_GLOBAL_OPERATING_CLASS"
                    " not found", __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                mTDLSgetStatusRspParams.global_operating_class =
                  get_u32(tb_vendor[
                  QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_GLOBAL_OPERATING_CLASS]);
                ALOGV("TDLS: global_operating_class: %d ",
                        mTDLSgetStatusRspParams.global_operating_class);
            }
            break;
        case QCA_NL80211_VENDOR_SUBCMD_TDLS_GET_CAPABILITIES:
            {
                struct nlattr *tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_MAX + 1];
                nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_MAX,
                        (struct nlattr *)mVendorData,
                        mDataLen, NULL);

                memset(&mTDLSgetCaps, 0, sizeof(wifiTdlsCapabilities));

                if (!tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_MAX_CONC_SESSIONS]
                   )
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_"
                          "MAX_CONC_SESSIONS not found", __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                mTDLSgetCaps.maxConcurrentTdlsSessionNum = get_u32(tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_MAX_CONC_SESSIONS]);

                if (!tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_FEATURES_SUPPORTED])
                {
                    ALOGE("%s: QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_"
                          "FEATURES_SUPPORTED not found", __FUNCTION__);
                    return WIFI_ERROR_INVALID_ARGS;
                }
                mTDLSgetCaps.tdlsSupportedFeatures = get_u32(tb_vendor[
                    QCA_WLAN_VENDOR_ATTR_TDLS_GET_CAPS_FEATURES_SUPPORTED]);
            }
            break;
        default :
            ALOGE("%s: Wrong TDLS subcmd response received %d",
                __FUNCTION__, mSubcmd);
    }
    return NL_SKIP;
}


int TdlsCommand::setCallbackHandler(wifi_tdls_handler nHandler, u32 event)
{
    int res = 0;
    mHandler = nHandler;
    res = registerVendorHandler(mVendor_id, event);
    if (res != 0) {
        /* Error case should not happen print log */
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x subcmd=%u",
              __FUNCTION__, mVendor_id, mSubcmd);
    }
    return res;
}

void TdlsCommand::unregisterHandler(u32 subCmd)
{
    unregisterVendorHandler(mVendor_id, subCmd);
}

void TdlsCommand::getStatusRspParams(wifi_tdls_status *status)
{
    status->channel = mTDLSgetStatusRspParams.channel;
    status->global_operating_class =
        mTDLSgetStatusRspParams.global_operating_class;
    status->state = mTDLSgetStatusRspParams.state;
    status->reason = mTDLSgetStatusRspParams.reason;
}

int TdlsCommand::requestResponse()
{
    return WifiCommand::requestResponse(mMsg);
}

void TdlsCommand::getCapsRspParams(wifi_tdls_capabilities *caps)
{
    caps->max_concurrent_tdls_session_num =
        mTDLSgetCaps.maxConcurrentTdlsSessionNum;
    caps->is_global_tdls_supported =
        !!(mTDLSgetCaps.tdlsSupportedFeatures & IS_GLOBAL_TDLS_SUPPORTED);
    caps->is_per_mac_tdls_supported =
        !!(mTDLSgetCaps.tdlsSupportedFeatures & IS_PER_MAC_TDLS_SUPPORTED);
    caps->is_off_channel_tdls_supported =
        !!(mTDLSgetCaps.tdlsSupportedFeatures & IS_OFF_CHANNEL_TDLS_SUPPORTED);
    ALOGV("TDLS capabilities:");
    ALOGV("max_concurrent_tdls_session_numChannel : %d\n",
            caps->max_concurrent_tdls_session_num);
    ALOGV("is_global_tdls_supported : %d\n",
            caps->is_global_tdls_supported);
    ALOGV("is_per_mac_tdls_supported : %d\n",
            caps->is_per_mac_tdls_supported);
    ALOGV("is_off_channel_tdls_supported : %d \n",
            caps->is_off_channel_tdls_supported);
}

/* wifi_enable_tdls - enables TDLS-auto mode for a specific route
 *
 * params specifies hints, which provide more information about
 * why TDLS is being sought. The firmware should do its best to
 * honor the hints before downgrading regular AP link
 *
 * On successful completion, must fire on_tdls_state_changed event
 * to indicate the status of TDLS operation.
 */
wifi_error wifi_enable_tdls(wifi_interface_handle iface,
                            mac_addr addr,
                            wifi_tdls_params *params,
                            wifi_tdls_handler handler)
{
    int ret = 0;
    TdlsCommand *pTdlsCommand;
    struct nlattr *nl_data;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    pTdlsCommand = TdlsCommand::instance(handle);

    if (pTdlsCommand == NULL) {
        ALOGE("%s: Error TdlsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    pTdlsCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_TDLS_ENABLE);

    /* Create the message */
    ret = pTdlsCommand->create();
    if (ret < 0)
        goto cleanup;

    ret = pTdlsCommand->set_iface_id(iinfo->name);
    if (ret < 0)
        goto cleanup;

    /* Add the attributes */
    nl_data = pTdlsCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data)
        goto cleanup;
    ALOGV("%s: MAC_ADDR: " MAC_ADDR_STR, __FUNCTION__, MAC_ADDR_ARRAY(addr));
    ret = pTdlsCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_TDLS_ENABLE_MAC_ADDR,
                                  (char *)addr, 6);
    if (ret < 0)
        goto cleanup;

    if (params != NULL) {
        ALOGV("%s: Channel: %d, Global operating class: %d, "
            "Max Latency: %dms, Min Bandwidth: %dKbps",
            __FUNCTION__, params->channel, params->global_operating_class,
            params->max_latency_ms, params->min_bandwidth_kbps);
        ret = pTdlsCommand->put_u32(
                            QCA_WLAN_VENDOR_ATTR_TDLS_ENABLE_CHANNEL,
                            params->channel) |
              pTdlsCommand->put_u32(
                            QCA_WLAN_VENDOR_ATTR_TDLS_ENABLE_GLOBAL_OPERATING_CLASS,
                            params->global_operating_class) |
              pTdlsCommand->put_u32(
                            QCA_WLAN_VENDOR_ATTR_TDLS_ENABLE_MAX_LATENCY_MS,
                            params->max_latency_ms) |
              pTdlsCommand->put_u32(
                            QCA_WLAN_VENDOR_ATTR_TDLS_ENABLE_MIN_BANDWIDTH_KBPS,
                            params->min_bandwidth_kbps);
        if (ret < 0)
            goto cleanup;
    }

    pTdlsCommand->attr_end(nl_data);

    ret = pTdlsCommand->setCallbackHandler(handler,
                        QCA_NL80211_VENDOR_SUBCMD_TDLS_STATE);
    if (ret < 0)
        goto cleanup;

    ret = pTdlsCommand->requestResponse();
    if (ret != 0) {
        ALOGE("%s: requestResponse Error:%d", __FUNCTION__, ret);
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

/* wifi_disable_tdls - disables TDLS-auto mode for a specific route
 *
 * This terminates any existing TDLS with addr device, and frees the
 * device resources to make TDLS connections on new routes.
 *
 * DON'T fire any more events on 'handler' specified in earlier call to
 * wifi_enable_tdls after this action.
 */
wifi_error wifi_disable_tdls(wifi_interface_handle iface, mac_addr addr)
{
    int ret = 0;
    TdlsCommand *pTdlsCommand;
    struct nlattr *nl_data;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    pTdlsCommand = TdlsCommand::instance(handle);

    if (pTdlsCommand == NULL) {
        ALOGE("%s: Error TdlsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    pTdlsCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_TDLS_DISABLE);

    /* Create the message */
    ret = pTdlsCommand->create();
    if (ret < 0)
        goto cleanup;

    ret = pTdlsCommand->set_iface_id(iinfo->name);
    if (ret < 0)
        goto cleanup;
    ALOGV("%s: ifindex obtained:%d", __FUNCTION__, ret);
    ALOGV("%s: MAC_ADDR: " MAC_ADDR_STR, __FUNCTION__, MAC_ADDR_ARRAY(addr));

    /* Add the attributes */
    nl_data = pTdlsCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data)
        goto cleanup;
    ret = pTdlsCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_TDLS_DISABLE_MAC_ADDR,
                                  (char *)addr, 6);
    if (ret < 0)
        goto cleanup;
    pTdlsCommand->attr_end(nl_data);

    ret = pTdlsCommand->requestResponse();
    if (ret != 0) {
        ALOGE("%s: requestResponse Error:%d", __FUNCTION__, ret);
    }

cleanup:
    delete pTdlsCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/* wifi_get_tdls_status - allows getting the status of TDLS for a specific
 * route
 */
wifi_error wifi_get_tdls_status(wifi_interface_handle iface, mac_addr addr,
                                wifi_tdls_status *status)
{
    int ret = 0;
    TdlsCommand *pTdlsCommand;
    struct nlattr *nl_data;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    pTdlsCommand = TdlsCommand::instance(handle);

    if (pTdlsCommand == NULL) {
        ALOGE("%s: Error TdlsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    pTdlsCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_TDLS_GET_STATUS);

    /* Create the message */
    ret = pTdlsCommand->create();
    if (ret < 0)
        goto cleanup;

    ret = pTdlsCommand->set_iface_id(iinfo->name);
    if (ret < 0)
        goto cleanup;
    ALOGV("%s: ifindex obtained:%d", __FUNCTION__, ret);

    /* Add the attributes */
    nl_data = pTdlsCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data)
        goto cleanup;
    ret = pTdlsCommand->put_bytes(QCA_WLAN_VENDOR_ATTR_TDLS_GET_STATUS_MAC_ADDR,
                                  (char *)addr, 6);
    if (ret < 0)
        goto cleanup;
    pTdlsCommand->attr_end(nl_data);

    ret = pTdlsCommand->requestResponse();
    if (ret != 0) {
        ALOGE("%s: requestResponse Error:%d", __FUNCTION__, ret);
    }
    pTdlsCommand->getStatusRspParams(status);

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

/* return the current HW + Firmware combination's TDLS capabilities */
wifi_error wifi_get_tdls_capabilities(wifi_interface_handle iface,
                                      wifi_tdls_capabilities *capabilities)
{
    int ret = 0;
    TdlsCommand *pTdlsCommand;

    if (capabilities == NULL) {
        ALOGE("%s: capabilities is NULL", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    pTdlsCommand = TdlsCommand::instance(handle);

    if (pTdlsCommand == NULL) {
        ALOGE("%s: Error TdlsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    pTdlsCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_TDLS_GET_CAPABILITIES);

    /* Create the message */
    ret = pTdlsCommand->create();
    if (ret < 0)
        goto cleanup;

    ret = pTdlsCommand->set_iface_id(iinfo->name);
    if (ret < 0)
        goto cleanup;

    ret = pTdlsCommand->requestResponse();
    if (ret != 0) {
        ALOGE("%s: requestResponse Error:%d", __FUNCTION__, ret);
        goto cleanup;
    }
    pTdlsCommand->getCapsRspParams(capabilities);

cleanup:
    if (ret < 0)
        memset(capabilities, 0, sizeof(wifi_tdls_capabilities));
    delete pTdlsCommand;
    return mapErrorKernelToWifiHAL(ret);
}
