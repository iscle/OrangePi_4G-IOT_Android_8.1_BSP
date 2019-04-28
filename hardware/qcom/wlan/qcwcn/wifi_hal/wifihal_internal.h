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

#ifndef __WIFI_HAL_LOWI_INTERNAL_H__
#define __WIFI_HAL_LOWI_INTERNAL_H__

/*
 * The file defines the interface by which wifihal can call LOWI for the
 * purposes of initialization, rtt and gscan.
 */

#include "wifi_hal.h"

#define WIFIHAL_LOWI_MAJOR_VERSION      2
#define WIFIHAL_LOWI_MINOR_VERSION      1
#define WIFIHAL_LOWI_MICRO_VERSION      1

/* LOWI supported capabilities bit masks */
#define ONE_SIDED_RANGING_SUPPORTED   0x00000001
#define DUAL_SIDED_RANGING_SUPPORED   0x00000002
#define GSCAN_SUPPORTED               0x00000004

/*
 * This structure is a table of function pointers to the functions
 * used by the wifihal to interface with LOWI
 */
typedef struct
{
  /* lowi-client interface functions */
  int (*init)();
  int (*destroy)();
  /* rtt functions */
  int (*get_rtt_capabilities)(wifi_interface_handle iface,
                              wifi_rtt_capabilities *capabilities);
  int (*rtt_range_request)(u32 request_id,
                           wifi_interface_handle iface,
                           u32 num_rtt_config,
                           wifi_rtt_config rtt_config[],
                           wifi_rtt_event_handler handler);
  int (*rtt_range_cancel)(u32 request_id,
                          u32 num_devices,
                          mac_addr addr[]);
  /* Additional lowi-client interface functions */
  int (*get_lowi_version) (u16* major_version,
                           u16* minor_version,
                           u16* micro_version);
  int (*get_lowi_capabilities)(u32* capabilities);
 /* gscan functions */
  wifi_error (*get_valid_channels)(wifi_interface_handle iface,
                                   u32 band,
                                   u32 max_channels,
                                   wifi_channel *channels,
                                   int *num_channels);

  wifi_error (*get_gscan_capabilities)(wifi_interface_handle handle,
                                       wifi_gscan_capabilities *capabilities);

  wifi_error (*start_gscan)(wifi_request_id request_id,
                            wifi_interface_handle iface,
                            wifi_scan_cmd_params params,
                            wifi_scan_result_handler handler);

  wifi_error (*stop_gscan)(wifi_request_id request_id,
                           wifi_interface_handle iface);

  wifi_error (*get_cached_gscan_results)(wifi_interface_handle iface,
                                         byte flush,
                                         u32 max,
                                         wifi_cached_scan_results *results,
                                         int *num);

  wifi_error (*set_bssid_hotlist)(wifi_request_id request_id,
                                  wifi_interface_handle iface,
                                  wifi_bssid_hotlist_params params,
                                  wifi_hotlist_ap_found_handler handler);

  wifi_error (*reset_bssid_hotlist)(wifi_request_id request_id,
                                    wifi_interface_handle iface);

  wifi_error (*set_significant_change_handler)(wifi_request_id id,
                                               wifi_interface_handle iface,
                                               wifi_significant_change_params params,
                                               wifi_significant_change_handler handler);

  wifi_error (*reset_significant_change_handler)(wifi_request_id id,
                                                 wifi_interface_handle iface);

  wifi_error (*set_ssid_hotlist)(wifi_request_id id,
                                 wifi_interface_handle iface,
                                 wifi_ssid_hotlist_params params,
                                 wifi_hotlist_ssid_handler handler);

  wifi_error (*reset_ssid_hotlist)(wifi_request_id id,
                                   wifi_interface_handle iface);

  // API to configure the LCI. Used in RTT Responder mode only
  wifi_error (*rtt_set_lci)(wifi_request_id id,
                            wifi_interface_handle iface,
                            wifi_lci_information *lci);

  // API to configure the LCR. Used in RTT Responder mode only.
  wifi_error (*rtt_set_lcr)(wifi_request_id id,
                            wifi_interface_handle iface,
                            wifi_lcr_information *lcr);

  /**
   * Get RTT responder information e.g. WiFi channel to enable responder on.
   */
  wifi_error (*rtt_get_responder_info)(wifi_interface_handle iface,
                                       wifi_rtt_responder *responder_info);

  /**
   * Enable RTT responder mode.
   * channel_hint - hint of the channel information where RTT responder should
   *                be enabled on.
   * max_duration_seconds - timeout of responder mode.
   * responder_info - responder information e.g. channel used for RTT responder,
   *                  NULL if responder is not enabled.
   */
  wifi_error (*enable_responder)(wifi_request_id id,
                                 wifi_interface_handle iface,
                                 wifi_channel_info channel_hint,
                                 unsigned max_duration_seconds,
                                 wifi_rtt_responder *responder_info);

  /**
   * Disable RTT responder mode.
   */
  wifi_error (*disable_responder)(wifi_request_id id,
                                  wifi_interface_handle iface);

} lowi_cb_table_t;

/*
  * This is a function pointer to a function that gets the table
  * of callback functions populated by LOWI and to be used by wifihal
  */
typedef lowi_cb_table_t* (getCbTable_t)();

#endif
