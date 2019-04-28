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

#ifndef __WIFI_HAL_WIFILOGGER_VENDOR_EVENTS_H__
#define __WIFI_HAL_WIFILOGGER_VENDOR_EVENTS_H__

#include "common.h"

typedef struct {
    u8 Tsniff;
    u8 attempts;
} bt_coex_hid_vendor_data_t;

typedef struct {
    u32 timer_tick;
    u32 scheduled_bucket_mask;
    u32 scan_cycle_count;
} __attribute__((packed)) ext_scan_cycle_vendor_data_t;

typedef struct {
    u32 table_type;
    u32 entries_in_use;
    u32 maximum_entries;
    u32 scan_count_after_getResults;
    u8 threshold_num_scans;
} __attribute__((packed)) ext_scan_results_available_vendor_data_t;

typedef struct {
    u32 roam_scan_flags;
    u32 cur_rssi;
    u16 scan_params[18];
    u16 scan_channels[40]; // first 40 channels only
} __attribute__((packed)) roam_scan_started_vendor_data_t;

typedef struct {
    u32 reason;
    u32 completion_flags;
    u32 num_candidate;
    u32 flags;
} __attribute__((packed)) roam_scan_complete_vendor_data_t;

typedef struct {
    u8 ssid[33];
    u8 auth_mode;
    u8 ucast_cipher;
    u8 mcast_cipher;
} __attribute__((packed)) roam_candidate_found_vendor_data_t;

typedef struct {
    u32 flags;
    u32 roam_scan_config[8];
} __attribute__((packed)) roam_scan_config_vendor_data_t;

typedef struct {
    u8 scan_type;
    u8 scan_bitmap;
} __attribute__((packed)) bt_coex_bt_scan_start_vendor_data_t;

typedef struct {
    u8 scan_type;
    u8 scan_bitmap;
} __attribute__((packed)) bt_coex_bt_scan_stop_vendor_data_t;

typedef struct {
    u16 sme_state;
    u16 mlm_state;
} __attribute__((packed)) pe_event_vendor_data_t;

typedef enum {
    ADDBA_SUCCESS = 0,
    ADDBA_FAILURE = -1,
} addba_status_t;

typedef struct {
    u8 ucBaTid;
    u8 ucBaBufferSize;
    u16 ucBaSSN;
    u8 fInitiator;
} __attribute__((packed)) addba_success_vendor_data_t;

typedef struct {
    u8 ucBaTid;
    u8 fInitiator;
} __attribute__((packed)) addba_failed_vendor_data_t;

typedef struct {
    u32 hotlist_mon_table_id;
    u32 wlan_hotlist_entry_size;
    u32 cache_cap_table_id;
    u32 max_scan_cache_entries;
    u32 requestor_id;
    u32 vdev_id;
    u32 num_extscan_cache_tables;
    u32 num_wlan_change_monitor_tables;
    u32 num_hotlist_monitor_tables;
    u32 rtt_one_sided_supported;
    u32 rtt_11v_supported;
    u32 rtt_ftm_supported;
    u32 num_extscan_cache_capabilities;
    u32 num_extscan_wlan_change_capabilities;
    u32 num_extscan_hotlist_capabilities;
    u32 num_roam_bssid_blacklist;
    u32 num_roam_bssid_preferred_list;
} __attribute__((packed)) gscan_capabilities_vendor_data_t;

typedef struct
{
    resource_failure_type event_sub_type;
} __attribute__((packed)) resource_failure_vendor_data_t;
#endif /* __WIFI_HAL_WIFILOGGER_VENDOR_EVENTS_H__ */
