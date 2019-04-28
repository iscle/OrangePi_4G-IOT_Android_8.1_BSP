/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

#define LOG_TAG  "WifiHAL"
#include <cutils/sched_policy.h>
#include <unistd.h>

#include <utils/Log.h>
#include <time.h>

#include "common.h"
#include "cpp_bindings.h"
#include "rtt.h"
#include "wifi_hal.h"
#include "wifihal_internal.h"

/* Implementation of the API functions exposed in rtt.h */
wifi_error wifi_get_rtt_capabilities(wifi_interface_handle iface,
                                     wifi_rtt_capabilities *capabilities)
{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL) {
        ALOGE("wifi_get_rtt_capabilities: NULL iface pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (capabilities == NULL) {
        ALOGE("wifi_get_rtt_capabilities: NULL capabilities pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* RTT commands are diverted through LOWI interface. */
    /* Open LOWI dynamic library, retrieve handler to LOWI APIs and initialize
     * LOWI if it isn't up yet.
     */
    lowiWifiHalApi = getLowiCallbackTable(
                ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->get_rtt_capabilities == NULL) {
        ALOGE("wifi_get_rtt_capabilities: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.");
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->get_rtt_capabilities(iface, capabilities);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_get_rtt_capabilities: lowi_wifihal_get_rtt_capabilities "
            "returned error:%d. Exit.", ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

/* API to request RTT measurement */
wifi_error wifi_rtt_range_request(wifi_request_id id,
                                    wifi_interface_handle iface,
                                    unsigned num_rtt_config,
                                    wifi_rtt_config rtt_config[],
                                    wifi_rtt_event_handler handler)
{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL) {
        ALOGE("wifi_rtt_range_request: NULL iface pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (rtt_config == NULL) {
        ALOGE("wifi_rtt_range_request: NULL rtt_config pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (num_rtt_config <= 0) {
        ALOGE("wifi_rtt_range_request: number of destination BSSIDs to "
            "measure RTT on = 0. Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (handler.on_rtt_results == NULL) {
        ALOGE("wifi_rtt_range_request: NULL capabilities pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* RTT commands are diverted through LOWI interface. */
    /* Open LOWI dynamic library, retrieve handler to LOWI APIs and initialize
     * LOWI if it isn't up yet.
     */
    lowiWifiHalApi = getLowiCallbackTable(
                    ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->rtt_range_request == NULL) {
        ALOGE("wifi_rtt_range_request: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.");
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->rtt_range_request(id,
                                            iface,
                                            num_rtt_config,
                                            rtt_config,
                                            handler);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_rtt_range_request: lowi_wifihal_rtt_range_request "
            "returned error:%d. Exit.", ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

/* API to cancel RTT measurements */
wifi_error wifi_rtt_range_cancel(wifi_request_id id,
                                   wifi_interface_handle iface,
                                   unsigned num_devices,
                                   mac_addr addr[])
{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL) {
        ALOGE("wifi_rtt_range_cancel: NULL iface pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (addr == NULL) {
        ALOGE("wifi_rtt_range_cancel: NULL addr pointer provided."
            " Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (num_devices <= 0) {
        ALOGE("wifi_rtt_range_cancel: number of destination BSSIDs to "
            "measure RTT on = 0. Exit.");
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* RTT commands are diverted through LOWI interface. */
    /* Open LOWI dynamic library, retrieve handler to LOWI APIs and initialize
     * LOWI if it isn't up yet.
     */
    lowiWifiHalApi = getLowiCallbackTable(
                    ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->rtt_range_cancel == NULL) {
        ALOGE("wifi_rtt_range_cancel: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.");
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->rtt_range_cancel(id, num_devices, addr);
    if (ret != WIFI_SUCCESS) {
        ALOGE("wifi_rtt_range_cancel: lowi_wifihal_rtt_range_cancel "
            "returned error:%d. Exit.", ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

// API to configure the LCI. Used in RTT Responder mode only
wifi_error wifi_set_lci(wifi_request_id id, wifi_interface_handle iface,
                        wifi_lci_information *lci)
{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL) {
        ALOGE("%s: NULL iface pointer provided."
            " Exit.", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (lci == NULL) {
        ALOGE("%s: NULL lci pointer provided."
            " Exit.", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* RTT commands are diverted through LOWI interface. */
    /* Open LOWI dynamic library, retrieve handler to LOWI APIs and initialize
     * LOWI if it isn't up yet.
     */
    lowiWifiHalApi = getLowiCallbackTable(
                    ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->rtt_set_lci == NULL) {
        ALOGE("%s: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.", __FUNCTION__);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->rtt_set_lci(id, iface, lci);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: returned error:%d. Exit.",
              __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

// API to configure the LCR. Used in RTT Responder mode only.
wifi_error wifi_set_lcr(wifi_request_id id, wifi_interface_handle iface,
                        wifi_lcr_information *lcr)
{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL) {
        ALOGE("%s: NULL iface pointer provided."
            " Exit.", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    if (lcr == NULL) {
        ALOGE("%s: NULL lcr pointer provided."
            " Exit.", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* RTT commands are diverted through LOWI interface. */
    /* Open LOWI dynamic library, retrieve handler to LOWI APIs and initialize
     * LOWI if it isn't up yet.
     */
    lowiWifiHalApi = getLowiCallbackTable(
                    ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->rtt_set_lcr == NULL) {
        ALOGE("%s: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.", __FUNCTION__);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->rtt_set_lcr(id, iface, lcr);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: returned error:%d. Exit.",
              __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

/*
 * Get RTT responder information e.g. WiFi channel to enable responder on.
 */
wifi_error wifi_rtt_get_responder_info(wifi_interface_handle iface,
                                      wifi_rtt_responder *responder_info)
{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL || responder_info == NULL) {
        ALOGE("%s: iface : %p responder_info : %p", __FUNCTION__, iface,
               responder_info);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* Open LOWI dynamic library, retrieve handler to LOWI APIs */
    lowiWifiHalApi = getLowiCallbackTable(
                    ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->rtt_get_responder_info == NULL) {
        ALOGE("%s: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.", __FUNCTION__);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->rtt_get_responder_info(iface, responder_info);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: returned error:%d. Exit.",
              __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}

/**
 * Enable RTT responder mode.
 * channel_hint - hint of the channel information where RTT responder should
 *                be enabled on.
 * max_duration_seconds - timeout of responder mode.
 * responder_info - responder information e.g. channel used for RTT responder,
 *                  NULL if responder is not enabled.
 */
wifi_error wifi_enable_responder(wifi_request_id id,
                                 wifi_interface_handle iface,
                                 wifi_channel_info channel_hint,
                                 unsigned max_duration_seconds,
                                 wifi_rtt_responder *responder_info)
{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL || responder_info == NULL) {
        ALOGE("%s: iface : %p responder_info : %p", __FUNCTION__, iface, responder_info);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* Open LOWI dynamic library, retrieve handler to LOWI APIs */
    lowiWifiHalApi = getLowiCallbackTable(
                    ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->enable_responder == NULL) {
        ALOGE("%s: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.", __FUNCTION__);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->enable_responder(id, iface, channel_hint,
                                           max_duration_seconds,
                                           responder_info);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: returned error:%d. Exit.",
              __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}


/**
 * Disable RTT responder mode.
 */
wifi_error wifi_disable_responder(wifi_request_id id,
                                  wifi_interface_handle iface)

{
    int ret = WIFI_SUCCESS;
    lowi_cb_table_t *lowiWifiHalApi = NULL;

    if (iface == NULL) {
        ALOGE("%s: iface : %p", __FUNCTION__, iface);
        return WIFI_ERROR_INVALID_ARGS;
    }

    /* Open LOWI dynamic library, retrieve handler to LOWI APIs */
    lowiWifiHalApi = getLowiCallbackTable(
                    ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
    if (lowiWifiHalApi == NULL ||
        lowiWifiHalApi->disable_responder == NULL) {
        ALOGE("%s: getLowiCallbackTable returned NULL or "
            "the function pointer is NULL. Exit.", __FUNCTION__);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto cleanup;
    }

    ret = lowiWifiHalApi->disable_responder(id, iface);
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: returned error:%d. Exit.",
              __FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return mapErrorKernelToWifiHAL(ret);
}
