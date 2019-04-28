/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
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
#include <time.h>
#include <errno.h>
#include <stdlib.h>
#include <unistd.h>
#include "wificonfigcommand.h"

/* Implementation of the API functions exposed in wifi_config.h */
wifi_error wifi_extended_dtim_config_set(wifi_request_id id,
                                         wifi_interface_handle iface,
                                         int extended_dtim)
{
    int ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    ALOGV("%s: extended_dtim:%d", __FUNCTION__, extended_dtim);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            id,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);

    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("wifi_extended_dtim_config_set: failed to create NL msg. "
            "Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("wifi_extended_dtim_config_set: failed to set iface id. "
            "Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ALOGE("wifi_extended_dtim_config_set: failed attr_start for "
            "VENDOR_DATA. Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
        QCA_WLAN_VENDOR_ATTR_WIFI_CONFIG_DYNAMIC_DTIM, extended_dtim)) {
        ALOGE("wifi_extended_dtim_config_set(): failed to put vendor data. "
            "Error:%d", ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("wifi_extended_dtim_config_set(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/* Set the country code to driver. */
wifi_error wifi_set_country_code(wifi_interface_handle iface,
                                 const char* country_code)
{
    int requestId, ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    wifi_handle wifiHandle = getWifiHandle(iface);

    ALOGV("%s: %s", __FUNCTION__, country_code);

    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate it randomly.
     */
    requestId = get_requestid();

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message with NL80211_CMD_REQ_SET_REG NL cmd. */
    ret = wifiConfigCommand->create_generic(NL80211_CMD_REQ_SET_REG);
    if (ret < 0) {
        ALOGE("wifi_set_country_code: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_string(NL80211_ATTR_REG_ALPHA2, country_code)) {
        ALOGE("wifi_set_country_code: put country code failed. Error:%d", ret);
        goto cleanup;
    }

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("wifi_set_country_code(): requestEvent Error:%d", ret);
        goto cleanup;
    }
    usleep(WAIT_TIME_FOR_SET_REG_DOMAIN);

cleanup:
    delete wifiConfigCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error wifi_set_beacon_wifi_iface_stats_averaging_factor(
                                                wifi_request_id id,
                                                wifi_interface_handle iface,
                                                u16 factor)
{
    int ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    ALOGV("%s factor:%u", __FUNCTION__, factor);
    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            id,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor: failed to "
            "create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor: failed to "
            "set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor: failed "
            "attr_start for VENDOR_DATA. Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
        QCA_WLAN_VENDOR_ATTR_WIFI_CONFIG_STATS_AVG_FACTOR, factor)) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor(): failed to "
            "put vendor data. Error:%d", ret);
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("wifi_set_beacon_wifi_iface_stats_averaging_factor(): "
            "requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error wifi_set_guard_time(wifi_request_id id,
                               wifi_interface_handle iface,
                               u32 guard_time)
{
    int ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    ALOGV("%s : guard_time:%u", __FUNCTION__, guard_time);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            id,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("wifi_set_guard_time: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("wifi_set_guard_time: failed to set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ALOGE("wifi_set_guard_time: failed attr_start for VENDOR_DATA. "
            "Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(
        QCA_WLAN_VENDOR_ATTR_WIFI_CONFIG_GUARD_TIME, guard_time)) {
        ALOGE("wifi_set_guard_time: failed to add vendor data.");
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    /* Send the NL msg. */
    wifiConfigCommand->waitForRsp(false);
    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("wifi_set_guard_time(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error wifi_select_tx_power_scenario(wifi_interface_handle handle,
                                         wifi_power_scenario scenario)
{
    int ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);
    u32 bdf_file = 0;

    ALOGV("%s : power scenario:%d", __FUNCTION__, scenario);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            1,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_SAR_LIMITS);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("wifi_select_tx_power_scenario: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("wifi_select_tx_power_scenario: failed to set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ALOGE("wifi_select_tx_power_scenario: failed attr_start for VENDOR_DATA. "
            "Error:%d", ret);
        goto cleanup;
    }

    if (scenario == WIFI_POWER_SCENARIO_VOICE_CALL) {
        bdf_file = QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_BDF0;
    } else {
        ALOGE("wifi_select_tx_power_scenario: invalid scenario %d", scenario);
        ret = WIFI_ERROR_INVALID_ARGS;
        goto cleanup;
    }
    if (wifiConfigCommand->put_u32(
                      QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SAR_ENABLE,
                      bdf_file)) {
        ALOGE("failed to put SAR_ENABLE");
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("wifi_select_tx_power_scenario(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error wifi_reset_tx_power_scenario(wifi_interface_handle handle)
{
    int ret = 0;
    WiFiConfigCommand *wifiConfigCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(handle);
    wifi_handle wifiHandle = getWifiHandle(handle);

    wifiConfigCommand = new WiFiConfigCommand(
                            wifiHandle,
                            1,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_SET_SAR_LIMITS);
    if (wifiConfigCommand == NULL) {
        ALOGE("%s: Error wifiConfigCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiConfigCommand->create();
    if (ret < 0) {
        ALOGE("wifi_reset_tx_power_scenario: failed to create NL msg. Error:%d", ret);
        goto cleanup;
    }

    /* Set the interface Id of the message. */
    ret = wifiConfigCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0) {
        ALOGE("wifi_reset_tx_power_scenario: failed to set iface id. Error:%d", ret);
        goto cleanup;
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiConfigCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData) {
        ALOGE("wifi_reset_tx_power_scenario: failed attr_start for VENDOR_DATA. "
            "Error:%d", ret);
        goto cleanup;
    }

    if (wifiConfigCommand->put_u32(QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SAR_ENABLE,
                               QCA_WLAN_VENDOR_ATTR_SAR_LIMITS_SELECT_NONE)) {
        ALOGE("failed to put SAR_ENABLE or NUM_SPECS");
        goto cleanup;
    }
    wifiConfigCommand->attr_end(nlData);

    ret = wifiConfigCommand->requestEvent();
    if (ret != 0) {
        ALOGE("wifi_reset_tx_power_scenario(): requestEvent Error:%d", ret);
        goto cleanup;
    }

cleanup:
    delete wifiConfigCommand;
    return mapErrorKernelToWifiHAL(ret);
}

WiFiConfigCommand::WiFiConfigCommand(wifi_handle handle,
                                     int id, u32 vendor_id,
                                     u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    /* Initialize the member data variables here */
    mWaitforRsp = false;
    mRequestId = id;
}

WiFiConfigCommand::~WiFiConfigCommand()
{
    unregisterVendorHandler(mVendor_id, mSubcmd);
}

/* This function implements creation of Vendor command */
int WiFiConfigCommand::create() {
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

/* This function implements creation of generic NL command */
int WiFiConfigCommand::create_generic(u8 cmdId) {
    int ret = mMsg.create(cmdId, 0, 0);
    return ret;
}

void WiFiConfigCommand::waitForRsp(bool wait)
{
    mWaitforRsp = wait;
}

/* Callback handlers registered for nl message send */
static int error_handler_wifi_config(struct sockaddr_nl *nla,
                                     struct nlmsgerr *err,
                                     void *arg)
{
    struct sockaddr_nl *tmp;
    int *ret = (int *)arg;
    tmp = nla;
    *ret = err->error;
    ALOGE("%s: Error code:%d (%s)", __FUNCTION__, *ret, strerror(-(*ret)));
    return NL_STOP;
}

/* Callback handlers registered for nl message send */
static int ack_handler_wifi_config(struct nl_msg *msg, void *arg)
{
    int *ret = (int *)arg;
    struct nl_msg * a;

    a = msg;
    *ret = 0;
    return NL_STOP;
}

/* Callback handlers registered for nl message send */
static int finish_handler_wifi_config(struct nl_msg *msg, void *arg)
{
  int *ret = (int *)arg;
  struct nl_msg * a;

  a = msg;
  *ret = 0;
  return NL_SKIP;
}

/*
 * Override base class requestEvent and implement little differently here.
 * This will send the request message.
 * We don't wait for any response back in case of wificonfig,
 * thus no wait for condition.
 */
int WiFiConfigCommand::requestEvent()
{
    int res = -1;
    struct nl_cb *cb;

    cb = nl_cb_alloc(NL_CB_DEFAULT);
    if (!cb) {
        ALOGE("%s: Callback allocation failed",__FUNCTION__);
        res = -1;
        goto out;
    }

    res = nl_send_auto_complete(mInfo->cmd_sock, mMsg.getMessage());
    if (res < 0)
        goto out;
    res = 1;

    nl_cb_err(cb, NL_CB_CUSTOM, error_handler_wifi_config, &res);
    nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler_wifi_config,
        &res);
    nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler_wifi_config, &res);

    /* Err is populated as part of finish_handler. */
    while (res > 0){
         nl_recvmsgs(mInfo->cmd_sock, cb);
    }

    /* Only wait for the asynchronous event if HDD returns success, res=0 */
    if (!res && (mWaitforRsp == true)) {
        struct timespec abstime;
        abstime.tv_sec = 4;
        abstime.tv_nsec = 0;
        res = mCondition.wait(abstime);
        if (res == ETIMEDOUT)
        {
            ALOGE("%s: Time out happened.", __FUNCTION__);
        }
        ALOGV("%s: Command invoked return value:%d, mWaitForRsp=%d",
            __FUNCTION__, res, mWaitforRsp);
    }
out:
    /* Cleanup the mMsg */
    mMsg.destroy();
    return res;
}

