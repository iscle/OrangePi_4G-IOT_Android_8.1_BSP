/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
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
#include "rssi_monitor.h"
#include "vendor_definitions.h"

/* Used to handle rssi command events from driver/firmware.*/
typedef struct rssi_monitor_event_handler_s {
    RSSIMonitorCommand* mRSSIMonitorCommandInstance;
} rssi_monitor_event_handlers;

wifi_error initializeRSSIMonitorHandler(hal_info *info)
{
    info->rssi_handlers = (rssi_monitor_event_handlers *)malloc(sizeof(
                              rssi_monitor_event_handlers));
    if (info->rssi_handlers) {
        memset(info->rssi_handlers, 0, sizeof(rssi_monitor_event_handlers));
    }
    else {
        ALOGE("%s: Allocation of RSSI event handlers failed",
              __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    return WIFI_SUCCESS;
}

wifi_error cleanupRSSIMonitorHandler(hal_info *info)
{
    rssi_monitor_event_handlers* event_handlers;
    if (info && info->rssi_handlers) {
        event_handlers = (rssi_monitor_event_handlers*) info->rssi_handlers;
        if (event_handlers->mRSSIMonitorCommandInstance) {
            delete event_handlers->mRSSIMonitorCommandInstance;
        }
        memset(event_handlers, 0, sizeof(rssi_monitor_event_handlers));
        return WIFI_SUCCESS;
    }
    ALOGE ("%s: info or info->rssi_handlers NULL", __FUNCTION__);
    return WIFI_ERROR_UNKNOWN;
}

void RSSIMonitorCommand::enableEventHandling()
{
    pthread_mutex_lock(&rm_lock);
    mEventHandlingEnabled = true;
    pthread_mutex_unlock(&rm_lock);
}

void RSSIMonitorCommand::disableEventHandling()
{
    pthread_mutex_lock(&rm_lock);
    mEventHandlingEnabled = false;
    pthread_mutex_unlock(&rm_lock);
}

bool RSSIMonitorCommand::isEventHandlingEnabled()
{
    bool eventHandlingEnabled;
    pthread_mutex_lock(&rm_lock);
    eventHandlingEnabled = mEventHandlingEnabled;
    pthread_mutex_unlock(&rm_lock);

    return eventHandlingEnabled;
}

void RSSIMonitorCommand::setCallbackHandler(wifi_rssi_event_handler handler)
{
    mHandler = handler;
}

RSSIMonitorCommand::RSSIMonitorCommand(wifi_handle handle, int id,
                                       u32 vendor_id, u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    memset(&mHandler, 0, sizeof(mHandler));
    if (registerVendorHandler(vendor_id, subcmd)) {
        /* Error case should not happen print log */
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x subcmd=%u",
              __FUNCTION__, vendor_id, subcmd);
    }
    pthread_mutex_init(&rm_lock, NULL);
    disableEventHandling();
}

RSSIMonitorCommand::~RSSIMonitorCommand()
{
    unregisterVendorHandler(mVendor_id, mSubcmd);
    pthread_mutex_destroy(&rm_lock);
}

void RSSIMonitorCommand::setReqId(wifi_request_id reqid)
{
    mId = reqid;
}

RSSIMonitorCommand* RSSIMonitorCommand::instance(wifi_handle handle,
                                                 wifi_request_id id)
{
    if (handle == NULL) {
        ALOGE("Interface Handle is invalid");
        return NULL;
    }
    hal_info *info = getHalInfo(handle);
    if (!info || !info->rssi_handlers) {
        ALOGE("rssi_handlers is invalid");
        return NULL;
    }

    RSSIMonitorCommand* mRSSIMonitorCommandInstance =
        info->rssi_handlers->mRSSIMonitorCommandInstance;

    if (mRSSIMonitorCommandInstance == NULL) {
        mRSSIMonitorCommandInstance = new RSSIMonitorCommand(handle, id,
                OUI_QCA,
                QCA_NL80211_VENDOR_SUBCMD_MONITOR_RSSI);
        info->rssi_handlers->mRSSIMonitorCommandInstance = mRSSIMonitorCommandInstance;
        return mRSSIMonitorCommandInstance;
    }
    else
    {
        if (handle != getWifiHandle(mRSSIMonitorCommandInstance->mInfo))
        {
            /* upper layer must have cleaned up the handle and reinitialized,
               so we need to update the same */
            ALOGV("Handle different, update the handle");
            mRSSIMonitorCommandInstance->mInfo = (hal_info *)handle;
        }
        mRSSIMonitorCommandInstance->setReqId(id);
    }
    return mRSSIMonitorCommandInstance;
}

/* This function will be the main handler for incoming event.
 * Call the appropriate callback handler after parsing the vendor data.
 */
int RSSIMonitorCommand::handleEvent(WifiEvent &event)
{
    int ret = WIFI_SUCCESS;

    if (isEventHandlingEnabled() == false) {
        ALOGE("%s: RSSI monitor isn't running or already stopped. "
              "Nothing to do. Exit", __FUNCTION__);
        return ret;
    }

    WifiVendorCommand::handleEvent(event);

    /* Parse the vendordata and get the attribute */
    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_MONITOR_RSSI:
        {
            mac_addr addr;
            s8 rssi;
            wifi_request_id reqId;
            struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_MAX
                                     + 1];
            nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_MAX,
                    (struct nlattr *)mVendorData,
                    mDataLen, NULL);

            memset(addr, 0, sizeof(mac_addr));

            if (!tb_vendor[
                QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_REQUEST_ID])
            {
                ALOGE("%s: ATTR_RSSI_MONITORING_REQUEST_ID not found. Exit.",
                    __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                break;
            }
            reqId = nla_get_u32(
                    tb_vendor[QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_REQUEST_ID]
                    );
            /* If event has a different request_id, ignore that and use the
             *  request_id value which we're maintaining.
             */
            if (reqId != id()) {
                ALOGV("%s: Event has Req. ID:%d <> Ours:%d, continue...",
                    __FUNCTION__, reqId, id());
                reqId = id();
            }
            ret = get_mac_addr(tb_vendor,
                    QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_CUR_BSSID,
                    addr);
            if (ret != WIFI_SUCCESS) {
                return ret;
            }
            ALOGV(MAC_ADDR_STR, MAC_ADDR_ARRAY(addr));

            if (!tb_vendor[QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_CUR_RSSI])
            {
                ALOGE("%s: QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_CUR_RSSI"
                      " not found", __FUNCTION__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = get_s8(tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_CUR_RSSI]);
            ALOGV("Current RSSI : %d ", rssi);

            if (mHandler.on_rssi_threshold_breached)
                (*mHandler.on_rssi_threshold_breached)(reqId, addr, rssi);
            else
                ALOGE("RSSI Monitoring: No Callback registered: ");
        }
        break;

        default:
            /* Error case should not happen print log */
            ALOGE("%s: Wrong subcmd received %d", __FUNCTION__, mSubcmd);
    }

    return ret;
}

wifi_error wifi_start_rssi_monitoring(wifi_request_id id,
                                      wifi_interface_handle iface,
                                      s8 max_rssi,
                                      s8 min_rssi,
                                      wifi_rssi_event_handler eh)
{
    int ret = WIFI_SUCCESS;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);
    RSSIMonitorCommand *rssiCommand;

    ret = initialize_vendor_cmd(iface, id,
                                QCA_NL80211_VENDOR_SUBCMD_MONITOR_RSSI,
                                &vCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return mapErrorKernelToWifiHAL(ret);
    }

    ALOGV("%s: Max RSSI:%d Min RSSI:%d", __FUNCTION__,
          max_rssi, min_rssi);
    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_CONTROL,
            QCA_WLAN_RSSI_MONITORING_START) ||
        vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_REQUEST_ID,
            id) ||
        vCommand->put_s8(
            QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_MAX_RSSI,
            max_rssi) ||
        vCommand->put_s8(
            QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_MIN_RSSI,
            min_rssi))
    {
        goto cleanup;
    }

    vCommand->attr_end(nlData);

    rssiCommand = RSSIMonitorCommand::instance(wifiHandle, id);
    if (rssiCommand == NULL) {
        ALOGE("%s: Error rssiCommand NULL", __FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    rssiCommand->setCallbackHandler(eh);

    ret = vCommand->requestResponse();
    if (ret < 0)
        goto cleanup;

    rssiCommand->enableEventHandling();

cleanup:
    delete vCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error wifi_stop_rssi_monitoring(wifi_request_id id,
                                     wifi_interface_handle iface)
{
    int ret = WIFI_SUCCESS;
    struct nlattr *nlData;
    WifiVendorCommand *vCommand = NULL;
    wifi_handle wifiHandle = getWifiHandle(iface);
    RSSIMonitorCommand *rssiCommand;
    rssi_monitor_event_handlers* event_handlers;
    hal_info *info = getHalInfo(wifiHandle);

    event_handlers = (rssi_monitor_event_handlers*)info->rssi_handlers;
    rssiCommand = event_handlers->mRSSIMonitorCommandInstance;

    if (rssiCommand == NULL ||
        rssiCommand->isEventHandlingEnabled() == false) {
        ALOGE("%s: RSSI monitor isn't running or already stopped. "
            "Nothing to do. Exit", __FUNCTION__);
        return WIFI_ERROR_NOT_AVAILABLE;
    }

    ret = initialize_vendor_cmd(iface, id,
                                QCA_NL80211_VENDOR_SUBCMD_MONITOR_RSSI,
                                &vCommand);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: Initialization failed", __FUNCTION__);
        return mapErrorKernelToWifiHAL(ret);
    }

    /* Add the vendor specific attributes for the NL command. */
    nlData = vCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_CONTROL,
            QCA_WLAN_RSSI_MONITORING_STOP) ||
        vCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_RSSI_MONITORING_REQUEST_ID,
            id))
    {
        goto cleanup;
    }

    vCommand->attr_end(nlData);

    ret = vCommand->requestResponse();
    if (ret < 0)
        goto cleanup;

    rssiCommand->disableEventHandling();


cleanup:
    delete vCommand;
    return mapErrorKernelToWifiHAL(ret);
}
