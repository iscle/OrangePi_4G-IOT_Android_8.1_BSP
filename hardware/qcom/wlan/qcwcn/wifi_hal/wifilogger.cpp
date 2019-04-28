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

#include "wifi_hal.h"
#include "common.h"
#include "cpp_bindings.h"
#include <errno.h>
#include <utils/Log.h>
#include "wifiloggercmd.h"
#include "rb_wrapper.h"
#include <stdlib.h>

#define LOGGER_MEMDUMP_FILENAME "/proc/debug/fwdump"
#define DRIVER_MEMDUMP_FILENAME "/proc/debugdriver/driverdump"
#define LOGGER_MEMDUMP_CHUNKSIZE (4 * 1024)
#define DRIVER_MEMDUMP_MAX_FILESIZE (16 * 1024)

char power_events_ring_name[] = "power_events_rb";
char connectivity_events_ring_name[] = "connectivity_events_rb";
char pkt_stats_ring_name[] = "pkt_stats_rb";
char driver_prints_ring_name[] = "driver_prints_rb";
char firmware_prints_ring_name[] = "firmware_prints_rb";

static int get_ring_id(hal_info *info, char *ring_name)
{
    int rb_id;

    for (rb_id = 0; rb_id < NUM_RING_BUFS; rb_id++) {
        if (is_rb_name_match(&info->rb_infos[rb_id], ring_name)) {
           return rb_id;
        }
    }
    return -1;
}

//Implementation of the functions exposed in wifi_logger.h

/* Function to intiate logging */
wifi_error wifi_start_logging(wifi_interface_handle iface,
                              u32 verbose_level, u32 flags,
                              u32 max_interval_sec, u32 min_data_size,
                              char *buffer_name)
{
    int requestId, ret = 0;
    WifiLoggerCommand *wifiLoggerCommand = NULL;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);
    int ring_id = 0;

    /*
     * No request id from caller, so generate one and pass it on to the driver.
     * Generate one randomly.
     */
    requestId = get_requestid();

    if (buffer_name == NULL) {
        ALOGE("%s: Invalid Ring Name. \n", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    ring_id = get_ring_id(info, buffer_name);
    if (ring_id < 0) {
        ALOGE("%s: Invalid Ring Buffer Name ", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    wifiLoggerCommand = new WifiLoggerCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_WIFI_LOGGER_START);

    if (wifiLoggerCommand == NULL) {
       ALOGE("%s: Error WifiLoggerCommand NULL", __FUNCTION__);
       return WIFI_ERROR_UNKNOWN;
    }
    /* Create the NL message. */
    ret = wifiLoggerCommand->create();

    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = wifiLoggerCommand->set_iface_id(ifaceInfo->name);

    if (ret < 0)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiLoggerCommand->attr_start(NL80211_ATTR_VENDOR_DATA);

    if (!nlData)
        goto cleanup;

    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_WIFI_LOGGER_RING_ID, ring_id))
    {
        goto cleanup;
    }
    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_WIFI_LOGGER_VERBOSE_LEVEL,
                verbose_level))
    {
        goto cleanup;
    }
    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_WIFI_LOGGER_FLAGS,
                flags))
    {
        goto cleanup;
    }

    wifiLoggerCommand->attr_end(nlData);

    /* Send the msg and wait for a response. */
    ret = wifiLoggerCommand->requestResponse();
    if (ret) {
        ALOGE("%s: Error %d happened. ", __FUNCTION__, ret);
    }

    ALOGV("%s: Logging Started for %s.", __FUNCTION__, buffer_name);
    rb_start_logging(&info->rb_infos[ring_id], verbose_level,
                    flags, max_interval_sec, min_data_size);
cleanup:
    if (wifiLoggerCommand)
        delete wifiLoggerCommand;
    return mapErrorKernelToWifiHAL(ret);

}

/*  Function to get each ring related info */
wifi_error wifi_get_ring_buffers_status(wifi_interface_handle iface,
                                        u32 *num_buffers,
                                        wifi_ring_buffer_status *status)
{
    int ret = 0;
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);
    wifi_ring_buffer_status *rbs;
    struct rb_info *rb_info;
    int rb_id;

    if ((*num_buffers) < NUM_RING_BUFS) {
        ALOGE("%s: Input num_buffers:%u cannot be accommodated, "
              "Total ring buffer num:%d", __FUNCTION__, *num_buffers,
              NUM_RING_BUFS);
        *num_buffers = 0;
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    for (rb_id = 0; rb_id < NUM_RING_BUFS; rb_id++) {
        rb_info = &info->rb_infos[rb_id];
        rbs = status + rb_id;

        get_rb_status(rb_info, rbs);
    }
    *num_buffers = NUM_RING_BUFS;
    return mapErrorKernelToWifiHAL(ret);
}

void push_out_all_ring_buffers(hal_info *info)
{
    int rb_id;

    for (rb_id = 0; rb_id < NUM_RING_BUFS; rb_id++) {
        push_out_rb_data(&info->rb_infos[rb_id]);
    }
}

void send_alert(hal_info *info, int reason_code)
{
    wifi_alert_handler handler;

    pthread_mutex_lock(&info->ah_lock);
    handler.on_alert = info->on_alert;
    pthread_mutex_unlock(&info->ah_lock);

    if (handler.on_alert) {
        handler.on_alert(0, NULL, 0, reason_code);
    }
}

void WifiLoggerCommand::setFeatureSet(u32 *support) {
    mSupportedSet = support;
}

/*  Function to get the supported feature set for logging.*/
wifi_error wifi_get_logger_supported_feature_set(wifi_interface_handle iface,
                                                 u32 *support)
{

    int requestId, ret = 0;
    WifiLoggerCommand *wifiLoggerCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate one randomly.
     */
    requestId = get_requestid();

    wifiLoggerCommand = new WifiLoggerCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_GET_LOGGER_FEATURE_SET);

    if (wifiLoggerCommand == NULL) {
        ALOGE("%s: Error WifiLoggerCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    /* Create the NL message. */
    ret = wifiLoggerCommand->create();

    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = wifiLoggerCommand->set_iface_id(ifaceInfo->name);

    if (ret < 0)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiLoggerCommand->attr_start(NL80211_ATTR_VENDOR_DATA);

    if (!nlData)
        goto cleanup;

    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_FEATURE_SET, requestId))
    {
        goto cleanup;
    }
    wifiLoggerCommand->attr_end(nlData);

    wifiLoggerCommand->setFeatureSet(support);

    /* Send the msg and wait for a response. */
    ret = wifiLoggerCommand->requestResponse();
    if (ret) {
        ALOGE("%s: Error %d happened. ", __FUNCTION__, ret);
    }

cleanup:
    delete wifiLoggerCommand;
    return mapErrorKernelToWifiHAL(ret);
}

/*  Function to get the data in each ring for the given ring ID.*/
wifi_error wifi_get_ring_data(wifi_interface_handle iface,
                              char *ring_name)
{

    int requestId, ret = 0;
    WifiLoggerCommand *wifiLoggerCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);
    int ring_id = 0;

    ring_id = get_ring_id(info, ring_name);
    if (ring_id < 0) {
        ALOGE("%s: Invalid Ring Buffer Name ", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    requestId = get_requestid();

    wifiLoggerCommand = new WifiLoggerCommand(
                                wifiHandle,
                                requestId,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_GET_RING_DATA);
    if (wifiLoggerCommand == NULL) {
        ALOGE("%s: Error WifiLoggerCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    /* Create the NL message. */
    ret = wifiLoggerCommand->create();

    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = wifiLoggerCommand->set_iface_id(ifaceInfo->name);

    if (ret < 0)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiLoggerCommand->attr_start(NL80211_ATTR_VENDOR_DATA);

    if (!nlData)
        goto cleanup;

    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_WIFI_LOGGER_RING_ID, ring_id))
    {
        goto cleanup;
    }
    wifiLoggerCommand->attr_end(nlData);

    /* Send the msg and wait for a response. */
    ret = wifiLoggerCommand->requestResponse();
    if (ret) {
        ALOGE("%s: Error %d happened. ", __FUNCTION__, ret);
    }

cleanup:
    delete wifiLoggerCommand;
    return mapErrorKernelToWifiHAL(ret);
}

void WifiLoggerCommand::setVersionInfo(char *buffer, int buffer_size) {
    mVersion = buffer;
    mVersionLen = buffer_size;
}

/*  Function to send enable request to the wifi driver.*/
wifi_error wifi_get_firmware_version(wifi_interface_handle iface,
                                     char *buffer, int buffer_size)
{
    int requestId, ret = 0;
    WifiLoggerCommand *wifiLoggerCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate one randomly.
     */
    requestId = get_requestid();

    wifiLoggerCommand = new WifiLoggerCommand(
                                wifiHandle,
                                requestId,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_GET_WIFI_INFO);
    if (wifiLoggerCommand == NULL) {
        ALOGE("%s: Error WifiLoggerCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    /* Create the NL message. */
    ret = wifiLoggerCommand->create();

    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = wifiLoggerCommand->set_iface_id(ifaceInfo->name);

    if (ret < 0)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiLoggerCommand->attr_start(NL80211_ATTR_VENDOR_DATA);

    if (!nlData)
        goto cleanup;

    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_WIFI_INFO_FIRMWARE_VERSION, requestId))
    {
        goto cleanup;
    }
    wifiLoggerCommand->attr_end(nlData);

    wifiLoggerCommand->setVersionInfo(buffer, buffer_size);

    /* Send the msg and wait for a response. */
    ret = wifiLoggerCommand->requestResponse();
    if (ret) {
        ALOGE("%s: Error %d happened. ", __FUNCTION__, ret);
    }
cleanup:
    delete wifiLoggerCommand;
    return mapErrorKernelToWifiHAL(ret);

}

/*  Function to get wlan driver version.*/
wifi_error wifi_get_driver_version(wifi_interface_handle iface,
                                   char *buffer, int buffer_size)
{

    int requestId, ret = 0;
    WifiLoggerCommand *wifiLoggerCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate one randomly.
     */
    requestId = get_requestid();

    wifiLoggerCommand = new WifiLoggerCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_GET_WIFI_INFO);
    if (wifiLoggerCommand == NULL) {
        ALOGE("%s: Error WifiLoggerCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    /* Create the NL message. */
    ret = wifiLoggerCommand->create();

    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = wifiLoggerCommand->set_iface_id(ifaceInfo->name);

    if (ret < 0)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiLoggerCommand->attr_start(NL80211_ATTR_VENDOR_DATA);

    if (!nlData)
        goto cleanup;

    if (wifiLoggerCommand->put_u32(
            QCA_WLAN_VENDOR_ATTR_WIFI_INFO_DRIVER_VERSION, requestId))
    {
        goto cleanup;
    }
    wifiLoggerCommand->attr_end(nlData);

    wifiLoggerCommand->setVersionInfo(buffer, buffer_size);

    /* Send the msg and wait for a response. */
    ret = wifiLoggerCommand->requestResponse();
    if (ret) {
        ALOGE("%s: Error %d happened. ", __FUNCTION__, ret);
    }
cleanup:
    delete wifiLoggerCommand;
    return mapErrorKernelToWifiHAL(ret);
}


/* Function to get the Firmware memory dump. */
wifi_error wifi_get_firmware_memory_dump(wifi_interface_handle iface,
                                wifi_firmware_memory_dump_handler handler)
{
    int requestId, ret = 0;
    WifiLoggerCommand *wifiLoggerCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate one randomly.
     */
    requestId = get_requestid();

    wifiLoggerCommand = new WifiLoggerCommand(
                            wifiHandle,
                            requestId,
                            OUI_QCA,
                            QCA_NL80211_VENDOR_SUBCMD_WIFI_LOGGER_MEMORY_DUMP);
    if (wifiLoggerCommand == NULL) {
        ALOGE("%s: Error WifiLoggerCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }
    /* Create the NL message. */
    ret = wifiLoggerCommand->create();

    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = wifiLoggerCommand->set_iface_id(ifaceInfo->name);

    if (ret < 0)
        goto cleanup;

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiLoggerCommand->attr_start(NL80211_ATTR_VENDOR_DATA);

    if (!nlData)
        goto cleanup;

    wifiLoggerCommand->attr_end(nlData);

    /* copy the callback into callback handler */
    WifiLoggerCallbackHandler callbackHandler;
    memset(&callbackHandler, 0, sizeof(callbackHandler));
    callbackHandler.on_firmware_memory_dump = \
        handler.on_firmware_memory_dump;

    ret = wifiLoggerCommand->setCallbackHandler(callbackHandler);
    if (ret < 0)
        goto cleanup;

    /* Send the msg and wait for the memory dump response */
    ret = wifiLoggerCommand->requestResponse();
    if (ret) {
        ALOGE("%s: Error %d happened. ", __FUNCTION__, ret);
    }

cleanup:
    delete wifiLoggerCommand;
    return mapErrorKernelToWifiHAL(ret);
}

wifi_error wifi_set_log_handler(wifi_request_id id,
                                wifi_interface_handle iface,
                                wifi_ring_buffer_data_handler handler)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    pthread_mutex_lock(&info->lh_lock);
    info->on_ring_buffer_data = handler.on_ring_buffer_data;
    pthread_mutex_unlock(&info->lh_lock);
    if (handler.on_ring_buffer_data == NULL) {
        ALOGE("Set log handler is NULL");
        return WIFI_ERROR_UNKNOWN;
    }
    return WIFI_SUCCESS;
}

wifi_error wifi_reset_log_handler(wifi_request_id id,
                                  wifi_interface_handle iface)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    pthread_mutex_lock(&info->lh_lock);
    info->on_ring_buffer_data = NULL;
    pthread_mutex_unlock(&info->lh_lock);
    return WIFI_SUCCESS;
}

wifi_error wifi_set_alert_handler(wifi_request_id id,
                                  wifi_interface_handle iface,
                                  wifi_alert_handler handler)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (handler.on_alert == NULL) {
        ALOGE("Set alert handler is NULL");
        return WIFI_ERROR_UNKNOWN;
    }
    pthread_mutex_lock(&info->ah_lock);
    info->on_alert = handler.on_alert;
    pthread_mutex_unlock(&info->ah_lock);
    return WIFI_SUCCESS;
}

wifi_error wifi_reset_alert_handler(wifi_request_id id,
                                    wifi_interface_handle iface)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    pthread_mutex_lock(&info->ah_lock);
    info->on_alert = NULL;
    pthread_mutex_unlock(&info->ah_lock);
    return WIFI_SUCCESS;
}


/**
    API to start packet fate monitoring.
    - Once stared, monitoring should remain active until HAL is unloaded.
    - When HAL is unloaded, all packet fate buffers should be cleared.
*/
wifi_error wifi_start_pkt_fate_monitoring(wifi_interface_handle iface)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);

    if (info->fate_monitoring_enabled == true) {
        ALOGV("Packet monitoring is already enabled");
        return WIFI_SUCCESS;
    }

    info->pkt_fate_stats = (packet_fate_monitor_info *) malloc (
                                              sizeof(packet_fate_monitor_info));
    if (info->pkt_fate_stats == NULL) {
        ALOGE("Failed to allocate memory for : %zu bytes",
              sizeof(packet_fate_monitor_info));
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    memset(info->pkt_fate_stats, 0, sizeof(packet_fate_monitor_info));

    pthread_mutex_lock(&info->pkt_fate_stats_lock);
    info->fate_monitoring_enabled = true;
    pthread_mutex_unlock(&info->pkt_fate_stats_lock);

    return WIFI_SUCCESS;
}


/**
    API to retrieve fates of outbound packets.
    - HAL implementation should fill |tx_report_bufs| with fates of
      _first_ min(n_requested_fates, actual packets) frames
      transmitted for the most recent association. The fate reports
      should follow the same order as their respective packets.
    - Packets reported by firmware, but not recognized by driver
      should be included.  However, the ordering of the corresponding
      reports is at the discretion of HAL implementation.
    - Framework may call this API multiple times for the same association.
    - Framework will ensure |n_requested_fates <= MAX_FATE_LOG_LEN|.
    - Framework will allocate and free the referenced storage.
*/
wifi_error wifi_get_tx_pkt_fates(wifi_interface_handle iface,
                                 wifi_tx_report *tx_report_bufs,
                                 size_t n_requested_fates,
                                 size_t *n_provided_fates)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);
    wifi_tx_report_i *tx_fate_stats;
    size_t i;

    if (info->fate_monitoring_enabled != true) {
        ALOGE("Packet monitoring is not yet triggered");
        return WIFI_ERROR_UNINITIALIZED;
    }
    pthread_mutex_lock(&info->pkt_fate_stats_lock);

    tx_fate_stats = &info->pkt_fate_stats->tx_fate_stats[0];

    *n_provided_fates = min(n_requested_fates,
                            info->pkt_fate_stats->n_tx_stats_collected);

    for (i=0; i < *n_provided_fates; i++) {
        memcpy(tx_report_bufs[i].md5_prefix,
                    tx_fate_stats[i].md5_prefix, MD5_PREFIX_LEN);
        tx_report_bufs[i].fate = tx_fate_stats[i].fate;
        tx_report_bufs[i].frame_inf.payload_type =
            tx_fate_stats[i].frame_inf.payload_type;
        tx_report_bufs[i].frame_inf.driver_timestamp_usec =
            tx_fate_stats[i].frame_inf.driver_timestamp_usec;
        tx_report_bufs[i].frame_inf.firmware_timestamp_usec =
            tx_fate_stats[i].frame_inf.firmware_timestamp_usec;
        tx_report_bufs[i].frame_inf.frame_len =
            tx_fate_stats[i].frame_inf.frame_len;

        if (tx_report_bufs[i].frame_inf.payload_type == FRAME_TYPE_ETHERNET_II)
            memcpy(tx_report_bufs[i].frame_inf.frame_content.ethernet_ii_bytes,
                   tx_fate_stats[i].frame_inf.frame_content,
                   min(tx_fate_stats[i].frame_inf.frame_len,
                       MAX_FRAME_LEN_ETHERNET));
        else if (tx_report_bufs[i].frame_inf.payload_type ==
                                                         FRAME_TYPE_80211_MGMT)
            memcpy(
                tx_report_bufs[i].frame_inf.frame_content.ieee_80211_mgmt_bytes,
                tx_fate_stats[i].frame_inf.frame_content,
                min(tx_fate_stats[i].frame_inf.frame_len,
                    MAX_FRAME_LEN_80211_MGMT));
        else
            /* Currently framework is interested only two types(
             * FRAME_TYPE_ETHERNET_II and FRAME_TYPE_80211_MGMT) of packets, so
             * ignore the all other types of packets received from driver */
            ALOGI("Unknown format packet");
    }
    pthread_mutex_unlock(&info->pkt_fate_stats_lock);

    return WIFI_SUCCESS;
}

/**
    API to retrieve fates of inbound packets.
    - HAL implementation should fill |rx_report_bufs| with fates of
      _first_ min(n_requested_fates, actual packets) frames
      received for the most recent association. The fate reports
      should follow the same order as their respective packets.
    - Packets reported by firmware, but not recognized by driver
      should be included.  However, the ordering of the corresponding
      reports is at the discretion of HAL implementation.
    - Framework may call this API multiple times for the same association.
    - Framework will ensure |n_requested_fates <= MAX_FATE_LOG_LEN|.
    - Framework will allocate and free the referenced storage.
*/
wifi_error wifi_get_rx_pkt_fates(wifi_interface_handle iface,
                                 wifi_rx_report *rx_report_bufs,
                                 size_t n_requested_fates,
                                 size_t *n_provided_fates)
{
    wifi_handle wifiHandle = getWifiHandle(iface);
    hal_info *info = getHalInfo(wifiHandle);
    wifi_rx_report_i *rx_fate_stats;
    size_t i;

    if (info->fate_monitoring_enabled != true) {
        ALOGE("Packet monitoring is not yet triggered");
        return WIFI_ERROR_UNINITIALIZED;
    }
    pthread_mutex_lock(&info->pkt_fate_stats_lock);

    rx_fate_stats = &info->pkt_fate_stats->rx_fate_stats[0];

    *n_provided_fates = min(n_requested_fates,
                            info->pkt_fate_stats->n_rx_stats_collected);

    for (i=0; i < *n_provided_fates; i++) {
        memcpy(rx_report_bufs[i].md5_prefix,
                    rx_fate_stats[i].md5_prefix, MD5_PREFIX_LEN);
        rx_report_bufs[i].fate = rx_fate_stats[i].fate;
        rx_report_bufs[i].frame_inf.payload_type =
            rx_fate_stats[i].frame_inf.payload_type;
        rx_report_bufs[i].frame_inf.driver_timestamp_usec =
            rx_fate_stats[i].frame_inf.driver_timestamp_usec;
        rx_report_bufs[i].frame_inf.firmware_timestamp_usec =
            rx_fate_stats[i].frame_inf.firmware_timestamp_usec;
        rx_report_bufs[i].frame_inf.frame_len =
            rx_fate_stats[i].frame_inf.frame_len;

        if (rx_report_bufs[i].frame_inf.payload_type == FRAME_TYPE_ETHERNET_II)
            memcpy(rx_report_bufs[i].frame_inf.frame_content.ethernet_ii_bytes,
                   rx_fate_stats[i].frame_inf.frame_content,
                   min(rx_fate_stats[i].frame_inf.frame_len,
                   MAX_FRAME_LEN_ETHERNET));
        else if (rx_report_bufs[i].frame_inf.payload_type ==
                                                         FRAME_TYPE_80211_MGMT)
            memcpy(
                rx_report_bufs[i].frame_inf.frame_content.ieee_80211_mgmt_bytes,
                rx_fate_stats[i].frame_inf.frame_content,
                min(rx_fate_stats[i].frame_inf.frame_len,
                    MAX_FRAME_LEN_80211_MGMT));
        else
            /* Currently framework is interested only two types(
             * FRAME_TYPE_ETHERNET_II and FRAME_TYPE_80211_MGMT) of packets, so
             * ignore the all other types of packets received from driver */
            ALOGI("Unknown format packet");
    }
    pthread_mutex_unlock(&info->pkt_fate_stats_lock);

    return WIFI_SUCCESS;
}

WifiLoggerCommand::WifiLoggerCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    mVersion = NULL;
    mVersionLen = 0;
    mRequestId = id;
    memset(&mHandler, 0,sizeof(mHandler));
    mWaitforRsp = false;
    mMoreData = false;
    mSupportedSet = NULL;
}

WifiLoggerCommand::~WifiLoggerCommand()
{
    unregisterVendorHandler(mVendor_id, mSubcmd);
}

/* This function implements creation of Vendor command */
int WifiLoggerCommand::create() {
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

     ALOGV("%s: mVendor_id = %d, Subcmd = %d.",
        __FUNCTION__, mVendor_id, mSubcmd);

out:
    return ret;
}

void rb_timerhandler(hal_info *info)
{
   struct timeval now;
   int rb_id;

   gettimeofday(&now,NULL);
   for (rb_id = 0; rb_id < NUM_RING_BUFS; rb_id++) {
       rb_check_for_timeout(&info->rb_infos[rb_id], &now);
   }
}

wifi_error wifi_logger_ring_buffers_init(hal_info *info)
{
    wifi_error ret;

    ret = rb_init(info, &info->rb_infos[POWER_EVENTS_RB_ID],
                  POWER_EVENTS_RB_ID,
                  POWER_EVENTS_RB_BUF_SIZE,
                  POWER_EVENTS_NUM_BUFS,
                  power_events_ring_name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to initialize power events ring buffer");
        goto cleanup;
    }

    ret = rb_init(info, &info->rb_infos[CONNECTIVITY_EVENTS_RB_ID],
                  CONNECTIVITY_EVENTS_RB_ID,
                  CONNECTIVITY_EVENTS_RB_BUF_SIZE,
                  CONNECTIVITY_EVENTS_NUM_BUFS,
                  connectivity_events_ring_name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to initialize connectivity events ring buffer");
        goto cleanup;
    }

    ret = rb_init(info, &info->rb_infos[PKT_STATS_RB_ID],
                  PKT_STATS_RB_ID,
                  PKT_STATS_RB_BUF_SIZE,
                  PKT_STATS_NUM_BUFS,
                  pkt_stats_ring_name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to initialize per packet stats ring buffer");
        goto cleanup;
    }

    ret = rb_init(info, &info->rb_infos[DRIVER_PRINTS_RB_ID],
                  DRIVER_PRINTS_RB_ID,
                  DRIVER_PRINTS_RB_BUF_SIZE,
                  DRIVER_PRINTS_NUM_BUFS,
                  driver_prints_ring_name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to initialize driver prints ring buffer");
        goto cleanup;
    }

    ret = rb_init(info, &info->rb_infos[FIRMWARE_PRINTS_RB_ID],
                  FIRMWARE_PRINTS_RB_ID,
                  FIRMWARE_PRINTS_RB_BUF_SIZE,
                  FIRMWARE_PRINTS_NUM_BUFS,
                  firmware_prints_ring_name);
    if (ret != WIFI_SUCCESS) {
        ALOGE("Failed to initialize firmware prints ring buffer");
        goto cleanup;
    }

    pthread_mutex_init(&info->lh_lock, NULL);
    pthread_mutex_init(&info->ah_lock, NULL);

    return ret;

cleanup:
    wifi_logger_ring_buffers_deinit(info);
    return ret;
}

void wifi_logger_ring_buffers_deinit(hal_info *info)
{
    int i;

    for (i = 0; i < NUM_RING_BUFS; i++) {
        rb_deinit(&info->rb_infos[i]);
    }
    pthread_mutex_destroy(&info->lh_lock);
    pthread_mutex_destroy(&info->ah_lock);
}


/* Callback handlers registered for nl message send */
static int error_handler_wifi_logger(struct sockaddr_nl *nla,
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
static int ack_handler_wifi_logger(struct nl_msg *msg, void *arg)
{
    int *ret = (int *)arg;
    struct nl_msg * a;

    a = msg;
    *ret = 0;
    return NL_STOP;
}

/* Callback handlers registered for nl message send */
static int finish_handler_wifi_logger(struct nl_msg *msg, void *arg)
{
  int *ret = (int *)arg;
  struct nl_msg * a;

  a = msg;
  *ret = 0;
  return NL_SKIP;
}

int WifiLoggerCommand::requestEvent()
{
    int res = -1;
    struct nl_cb *cb;

    cb = nl_cb_alloc(NL_CB_DEFAULT);
    if (!cb) {
        ALOGE("%s: Callback allocation failed",__FUNCTION__);
        res = -1;
        goto out;
    }

    /* Send message */
    res = nl_send_auto_complete(mInfo->cmd_sock, mMsg.getMessage());
    if (res < 0)
        goto out;
    res = 1;

    nl_cb_err(cb, NL_CB_CUSTOM, error_handler_wifi_logger, &res);
    nl_cb_set(cb, NL_CB_FINISH, NL_CB_CUSTOM, finish_handler_wifi_logger, &res);
    nl_cb_set(cb, NL_CB_ACK, NL_CB_CUSTOM, ack_handler_wifi_logger, &res);

    /* Err is populated as part of finish_handler. */
    while (res > 0){
         nl_recvmsgs(mInfo->cmd_sock, cb);
    }

    ALOGV("%s: Msg sent, res=%d, mWaitForRsp=%d", __FUNCTION__, res, mWaitforRsp);
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

int WifiLoggerCommand::requestResponse()
{
    return WifiCommand::requestResponse(mMsg);
}

int WifiLoggerCommand::handleResponse(WifiEvent &reply) {
    int len = 0, version;
    char version_type[20];
    char* memBuffer = NULL;
    FILE* memDumpFilePtr = NULL;
    WifiVendorCommand::handleResponse(reply);

    memset(version_type, 0, 20);
    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_GET_WIFI_INFO:
        {
            struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_WIFI_INFO_GET_MAX + 1];

            nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_WIFI_INFO_GET_MAX,
                            (struct nlattr *)mVendorData, mDataLen, NULL);

            if (tb_vendor[QCA_WLAN_VENDOR_ATTR_WIFI_INFO_DRIVER_VERSION]) {
                len = nla_len(tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_WIFI_INFO_DRIVER_VERSION]);
                memcpy(version_type, "Driver", strlen("Driver"));
                version = QCA_WLAN_VENDOR_ATTR_WIFI_INFO_DRIVER_VERSION;
            } else if (
                tb_vendor[QCA_WLAN_VENDOR_ATTR_WIFI_INFO_FIRMWARE_VERSION]) {
                len = nla_len(
                        tb_vendor[
                        QCA_WLAN_VENDOR_ATTR_WIFI_INFO_FIRMWARE_VERSION]);
                memcpy(version_type, "Firmware", strlen("Firmware"));
                version = QCA_WLAN_VENDOR_ATTR_WIFI_INFO_FIRMWARE_VERSION;
            }
            if (len && mVersion && mVersionLen) {
                memset(mVersion, 0, mVersionLen);
                /* if len is greater than the incoming length then
                   accommodate 1 lesser than mVersionLen to have the
                   string terminated with '\0' */
                len = (len > mVersionLen)? (mVersionLen - 1) : len;
                memcpy(mVersion, nla_data(tb_vendor[version]), len);
                ALOGV("%s: WLAN %s version : %s ", __FUNCTION__,
                      version_type, mVersion);
            }
        }
        break;
        case QCA_NL80211_VENDOR_SUBCMD_GET_LOGGER_FEATURE_SET:
        {
            struct nlattr *tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_SET_MAX + 1];

            nla_parse(tb_vendor, QCA_WLAN_VENDOR_ATTR_FEATURE_SET_MAX,
                            (struct nlattr *)mVendorData, mDataLen, NULL);

            if (tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_SET]) {
                *mSupportedSet =
                nla_get_u32(tb_vendor[QCA_WLAN_VENDOR_ATTR_FEATURE_SET]);
#ifdef QC_HAL_DEBUG
                ALOGV("%s: Supported Feature Set : val 0x%x",
                      __FUNCTION__, *mSupportedSet);
#endif
            }
        }
        break;

        case QCA_NL80211_VENDOR_SUBCMD_WIFI_LOGGER_MEMORY_DUMP:
        {
            u32 memDumpSize = 0;
            int numRecordsRead = 0;
            u32 remaining = 0;
            char* buffer = NULL;
            struct nlattr *tbVendor[
                QCA_WLAN_VENDOR_ATTR_LOGGER_RESULTS_MAX + 1];

            nla_parse(tbVendor, QCA_WLAN_VENDOR_ATTR_LOGGER_RESULTS_MAX,
                    (struct nlattr *)mVendorData,
                    mDataLen, NULL);

            if (!tbVendor[
                QCA_WLAN_VENDOR_ATTR_LOGGER_RESULTS_MEMDUMP_SIZE]) {
                ALOGE("%s: LOGGER_RESULTS_MEMDUMP_SIZE not"
                      "found", __FUNCTION__);
                break;
            }

            memDumpSize = nla_get_u32(
                tbVendor[QCA_WLAN_VENDOR_ATTR_LOGGER_RESULTS_MEMDUMP_SIZE]
                );

            /* Allocate the memory indicated in memDumpSize */
            memBuffer = (char*) malloc(sizeof(char) * memDumpSize);
            if (memBuffer == NULL) {
                ALOGE("%s: No Memory for allocating Buffer size of %d",
                      __func__, memDumpSize);
                break;
            }
            memset(memBuffer, 0, sizeof(char) * memDumpSize);

            ALOGI("%s: Memory Dump size: %u", __func__,
                  memDumpSize);

            /* Open the proc or debugfs filesystem */
            memDumpFilePtr = fopen(LOGGER_MEMDUMP_FILENAME, "r");
            if (memDumpFilePtr == NULL) {
                ALOGE("Failed to open %s file", LOGGER_MEMDUMP_FILENAME);
                break;
            }

            /* Read the memDumpSize value at once */
            numRecordsRead = fread(memBuffer, 1, memDumpSize,
                                   memDumpFilePtr);
            if (numRecordsRead <= 0 ||
                numRecordsRead != (int) memDumpSize) {
                ALOGE("%s: Read %d failed for reading at once.",
                      __func__, numRecordsRead);
                /* Lets try to read in chunks */
                rewind(memDumpFilePtr);
                remaining = memDumpSize;
                buffer = memBuffer;
                while (remaining) {
                    u32 readSize = 0;
                    if (remaining >= LOGGER_MEMDUMP_CHUNKSIZE) {
                        readSize = LOGGER_MEMDUMP_CHUNKSIZE;
                    }
                    else {
                        readSize = remaining;
                    }
                    numRecordsRead = fread(buffer, 1,
                                           readSize, memDumpFilePtr);
                    if (numRecordsRead) {
                        remaining -= readSize;
                        buffer += readSize;
                        ALOGV("%s: Read successful for size:%u "
                              "remaining:%u", __func__, readSize,
                              remaining);
                    }
                    else {
                        ALOGE("%s: Chunk read failed for size:%u",
                              __func__, readSize);
                        break;
                    }
                }
            }

            /* After successful read, call the callback handler*/
            if (mHandler.on_firmware_memory_dump) {
                mHandler.on_firmware_memory_dump(memBuffer,
                                                 memDumpSize);

            }
        }
        break;
        case QCA_NL80211_VENDOR_SUBCMD_GET_WAKE_REASON_STATS:
        {
            struct nlattr *tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_MAX +1];

            /* parse and extract wake reason stats */
            nla_parse(tbVendor, QCA_WLAN_VENDOR_ATTR_WAKE_STATS_MAX,
                      (struct nlattr *)mVendorData,
                      mDataLen, NULL);

            if (!tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_TOTAL_CMD_EVENT_WAKE]) {
                ALOGE("%s: TOTAL_CMD_EVENT_WAKE not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->total_cmd_event_wake = nla_get_u32(
                tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_TOTAL_CMD_EVENT_WAKE]);

            if (mGetWakeStats->total_cmd_event_wake &&
                    mGetWakeStats->cmd_event_wake_cnt) {
                if (!tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_CMD_EVENT_WAKE_CNT_PTR]) {
                    ALOGE("%s: CMD_EVENT_WAKE_CNT_PTR not found", __FUNCTION__);
                    break;
                }
                len = nla_len(tbVendor[
                        QCA_WLAN_VENDOR_ATTR_WAKE_STATS_CMD_EVENT_WAKE_CNT_PTR]);
                mGetWakeStats->cmd_event_wake_cnt_used =
                        (len < mGetWakeStats->cmd_event_wake_cnt_sz) ? len :
                                    mGetWakeStats->cmd_event_wake_cnt_sz;
                memcpy(mGetWakeStats->cmd_event_wake_cnt,
                    nla_data(tbVendor[
                        QCA_WLAN_VENDOR_ATTR_WAKE_STATS_CMD_EVENT_WAKE_CNT_PTR]),
                    (mGetWakeStats->cmd_event_wake_cnt_used * sizeof(int)));
            } else
                mGetWakeStats->cmd_event_wake_cnt_used = 0;

            if (!tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_TOTAL_DRIVER_FW_LOCAL_WAKE])
            {
                ALOGE("%s: TOTAL_DRIVER_FW_LOCAL_WAKE not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->total_driver_fw_local_wake = nla_get_u32(tbVendor[
                QCA_WLAN_VENDOR_ATTR_WAKE_STATS_TOTAL_DRIVER_FW_LOCAL_WAKE]);

            if (mGetWakeStats->total_driver_fw_local_wake &&
                    mGetWakeStats->driver_fw_local_wake_cnt) {
                if (!tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_DRIVER_FW_LOCAL_WAKE_CNT_PTR])
                {
                    ALOGE("%s: DRIVER_FW_LOCAL_WAKE_CNT_PTR not found",
                        __FUNCTION__);
                    break;
                }
                len = nla_len(tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_DRIVER_FW_LOCAL_WAKE_CNT_PTR]);
                mGetWakeStats->driver_fw_local_wake_cnt_used =
                    (len < mGetWakeStats->driver_fw_local_wake_cnt_sz) ? len :
                                    mGetWakeStats->driver_fw_local_wake_cnt_sz;

                memcpy(mGetWakeStats->driver_fw_local_wake_cnt,
                    nla_data(tbVendor[
                        QCA_WLAN_VENDOR_ATTR_WAKE_STATS_DRIVER_FW_LOCAL_WAKE_CNT_PTR]),
                    (mGetWakeStats->driver_fw_local_wake_cnt_used * sizeof(int)));
            } else
                mGetWakeStats->driver_fw_local_wake_cnt_used = 0;

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_TOTAL_RX_DATA_WAKE]) {
                ALOGE("%s: TOTAL_RX_DATA_WAKE not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->total_rx_data_wake = nla_get_u32(tbVendor[
                        QCA_WLAN_VENDOR_ATTR_WAKE_STATS_TOTAL_RX_DATA_WAKE]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_RX_UNICAST_CNT]) {
                ALOGE("%s: RX_UNICAST_CNT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_details.rx_unicast_cnt = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_RX_UNICAST_CNT]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_RX_MULTICAST_CNT]) {
                ALOGE("%s: RX_MULTICAST_CNT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_details.rx_multicast_cnt = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_RX_MULTICAST_CNT]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_RX_BROADCAST_CNT]) {
                ALOGE("%s: RX_BROADCAST_CNT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_details.rx_broadcast_cnt = nla_get_u32(
                    tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_RX_BROADCAST_CNT]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP_PKT]) {
                ALOGE("%s: ICMP_PKT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_pkt_classification_info.icmp_pkt =
                nla_get_u32(tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP_PKT]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_PKT]) {
                ALOGE("%s: ICMP6_PKT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_pkt_classification_info.icmp6_pkt =
                nla_get_u32(tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_PKT]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_RA]) {
                ALOGE("%s: ICMP6_RA not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_pkt_classification_info.icmp6_ra =
                nla_get_u32(tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_RA]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_NA]) {
                ALOGE("%s: ICMP6_NA not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_pkt_classification_info.icmp6_na =
                nla_get_u32(tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_NA]);

            if (!tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_NS]) {
                ALOGE("%s: ICMP6_NS not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_wake_pkt_classification_info.icmp6_ns =
                nla_get_u32(tbVendor[QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_NS]);

            if (!tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP4_RX_MULTICAST_CNT]) {
                ALOGE("%s: ICMP4_RX_MULTICAST_CNT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_multicast_wake_pkt_info.ipv4_rx_multicast_addr_cnt =
                nla_get_u32(tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP4_RX_MULTICAST_CNT]);

            if (!tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_RX_MULTICAST_CNT]) {
                ALOGE("%s: ICMP6_RX_MULTICAST_CNT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_multicast_wake_pkt_info.ipv6_rx_multicast_addr_cnt =
                nla_get_u32(tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_ICMP6_RX_MULTICAST_CNT]);

            if (!tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_OTHER_RX_MULTICAST_CNT]) {
                ALOGE("%s: OTHER_RX_MULTICAST_CNT not found", __FUNCTION__);
                break;
            }
            mGetWakeStats->rx_multicast_wake_pkt_info.other_rx_multicast_addr_cnt =
                nla_get_u32(tbVendor[
                    QCA_WLAN_VENDOR_ATTR_WAKE_STATS_OTHER_RX_MULTICAST_CNT]);

        }
        break;

        default :
            ALOGE("%s: Wrong Wifi Logger subcmd response received %d",
                __FUNCTION__, mSubcmd);
    }

    /* free the allocated memory */
    if (memBuffer) {
        free(memBuffer);
    }
    if (memDumpFilePtr) {
        fclose(memDumpFilePtr);
    }
    return NL_SKIP;
}

/* This function will be the main handler for incoming (from driver)
 * WIFI_LOGGER_SUBCMD.
 * Calls the appropriate callback handler after parsing the vendor data.
 */
int WifiLoggerCommand::handleEvent(WifiEvent &event)
{
    WifiVendorCommand::handleEvent(event);

    switch(mSubcmd)
    {
       default:
           /* Error case should not happen print log */
           ALOGE("%s: Wrong subcmd received %d", __func__, mSubcmd);
           break;
    }

    return NL_SKIP;
}

int WifiLoggerCommand::setCallbackHandler(WifiLoggerCallbackHandler nHandler)
{
    int res = 0;
    mHandler = nHandler;
    res = registerVendorHandler(mVendor_id, mSubcmd);
    if (res != 0) {
        ALOGE("%s: Unable to register Vendor Handler Vendor Id=0x%x subcmd=%u",
              __FUNCTION__, mVendor_id, mSubcmd);
    }
    return res;
}

void WifiLoggerCommand::unregisterHandler(u32 subCmd)
{
    unregisterVendorHandler(mVendor_id, subCmd);
}

int WifiLoggerCommand::timed_wait(u16 wait_time)
{
    struct timespec absTime;
    absTime.tv_sec = wait_time;
    absTime.tv_nsec = 0;
    return mCondition.wait(absTime);
}

void WifiLoggerCommand::waitForRsp(bool wait)
{
    mWaitforRsp = wait;
}

/* Function to get Driver memory dump */
wifi_error wifi_get_driver_memory_dump(wifi_interface_handle iface,
                                    wifi_driver_memory_dump_callbacks callback)
{
    FILE *fp;
    size_t fileSize, remaining, readSize;
    size_t numRecordsRead;
    char *memBuffer = NULL, *buffer = NULL;

    /* Open File */
    fp = fopen(DRIVER_MEMDUMP_FILENAME, "r");
    if (fp == NULL) {
        ALOGE("Failed to open %s file", DRIVER_MEMDUMP_FILENAME);
        return WIFI_ERROR_UNKNOWN;
    }

    memBuffer = (char *) malloc(DRIVER_MEMDUMP_MAX_FILESIZE);
    if (memBuffer == NULL) {
        ALOGE("%s: malloc failed for size %d", __FUNCTION__,
                    DRIVER_MEMDUMP_MAX_FILESIZE);
        fclose(fp);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    /* Read the DRIVER_MEMDUMP_MAX_FILESIZE value at once */
    numRecordsRead = fread(memBuffer, 1, DRIVER_MEMDUMP_MAX_FILESIZE, fp);
    if (feof(fp))
        fileSize = numRecordsRead;
    else if (numRecordsRead == DRIVER_MEMDUMP_MAX_FILESIZE) {
        ALOGE("%s: Reading only first %zu bytes from file", __FUNCTION__,
                numRecordsRead);
        fileSize = numRecordsRead;
    } else {
        ALOGE("%s: Read failed for reading at once, ret: %zu. Trying to read in"
                "chunks", __FUNCTION__, numRecordsRead);
        /* Lets try to read in chunks */
        rewind(fp);
        remaining = DRIVER_MEMDUMP_MAX_FILESIZE;
        buffer = memBuffer;
        fileSize = 0;
        while (remaining) {
            readSize = 0;
            if (remaining >= LOGGER_MEMDUMP_CHUNKSIZE)
                readSize = LOGGER_MEMDUMP_CHUNKSIZE;
            else
                readSize = remaining;

            numRecordsRead = fread(buffer, 1, readSize, fp);
            fileSize += numRecordsRead;
            if (feof(fp))
                break;
            else if (numRecordsRead == readSize) {
                remaining -= readSize;
                buffer += readSize;
                ALOGV("%s: Read successful for size:%zu remaining:%zu",
                         __FUNCTION__, readSize, remaining);
            } else {
                ALOGE("%s: Chunk read failed for size:%zu", __FUNCTION__,
                        readSize);
                free(memBuffer);
                memBuffer = NULL;
                fclose(fp);
                return WIFI_ERROR_UNKNOWN;
            }
        }
    }
    ALOGV("%s filename: %s fileSize: %zu", __FUNCTION__, DRIVER_MEMDUMP_FILENAME,
            fileSize);
    /* After successful read, call the callback function*/
    callback.on_driver_memory_dump(memBuffer, fileSize);

    /* free the allocated memory */
    free(memBuffer);
    fclose(fp);
    return WIFI_SUCCESS;
}

/* Function to get wake lock stats */
wifi_error wifi_get_wake_reason_stats(wifi_interface_handle iface,
                             WLAN_DRIVER_WAKE_REASON_CNT *wifi_wake_reason_cnt)
{
    int requestId, ret = WIFI_SUCCESS;
    WifiLoggerCommand *wifiLoggerCommand;
    struct nlattr *nlData;
    interface_info *ifaceInfo = getIfaceInfo(iface);
    wifi_handle wifiHandle = getWifiHandle(iface);

    /* No request id from caller, so generate one and pass it on to the driver.
     * Generate it randomly.
     */
    requestId = get_requestid();

    if (!wifi_wake_reason_cnt) {
        ALOGE("%s: Invalid buffer provided. Exit.",
            __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    wifiLoggerCommand = new WifiLoggerCommand(
                                wifiHandle,
                                requestId,
                                OUI_QCA,
                                QCA_NL80211_VENDOR_SUBCMD_GET_WAKE_REASON_STATS);
    if (wifiLoggerCommand == NULL) {
        ALOGE("%s: Error WifiLoggerCommand NULL", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    /* Create the NL message. */
    ret = wifiLoggerCommand->create();
    if (ret < 0)
        goto cleanup;

    /* Set the interface Id of the message. */
    ret = wifiLoggerCommand->set_iface_id(ifaceInfo->name);
    if (ret < 0)
        goto cleanup;

    wifiLoggerCommand->getWakeStatsRspParams(wifi_wake_reason_cnt);

    /* Add the vendor specific attributes for the NL command. */
    nlData = wifiLoggerCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nlData)
        goto cleanup;

    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_WAKE_STATS_CMD_EVENT_WAKE_CNT_SZ,
                wifi_wake_reason_cnt->cmd_event_wake_cnt_sz))
    {
        goto cleanup;
    }

    if (wifiLoggerCommand->put_u32(
                QCA_WLAN_VENDOR_ATTR_WAKE_STATS_DRIVER_FW_LOCAL_WAKE_CNT_SZ,
                wifi_wake_reason_cnt->driver_fw_local_wake_cnt_sz))
    {
        goto cleanup;
    }
    wifiLoggerCommand->attr_end(nlData);

    /* Send the msg and wait for a response. */
    ret = wifiLoggerCommand->requestResponse();
    if (ret) {
        ALOGE("%s: Error %d happened. ", __FUNCTION__, ret);
    }

cleanup:
    delete wifiLoggerCommand;
    return mapErrorKernelToWifiHAL(ret);
}

void WifiLoggerCommand::getWakeStatsRspParams(
                            WLAN_DRIVER_WAKE_REASON_CNT *wifi_wake_reason_cnt)
{
    mGetWakeStats = wifi_wake_reason_cnt;
}
