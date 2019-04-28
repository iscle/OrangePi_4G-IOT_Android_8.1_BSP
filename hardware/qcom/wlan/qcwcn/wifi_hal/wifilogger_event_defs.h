/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

#ifndef WIFILOGGER_EVENT_DEFS_H
#define WIFILOGGER_EVENT_DEFS_H

typedef enum {
    EVENT_DROP_ID = 0,

    EVENT_WLAN_PE = 0x67A, /* 16 byte payload */

    /* Events between 0x67b to 0x67f are not used */

    EVENT_WLAN_ADD_BLOCK_ACK_SUCCESS = 0x67B,        /* 11 byte payload */
    EVENT_WLAN_ADD_BLOCK_ACK_FAILED = 0x67C,         /* 9 byte payload */

    EVENT_WLAN_EXTSCAN_FEATURE_STARTED =   0xA8E, /* 240 byte payload */
    EVENT_WLAN_EXTSCAN_FEATURE_CHANNEL_CONFIG = 0xA8F, /* 243 byte payload */
    EVENT_WLAN_EXTSCAN_CYCLE_STARTED = 0xA90, /* 12 byte payload */
    EVENT_WLAN_EXTSCAN_CYCLE_COMPLETED = 0xA91, /* 12 byte payload */
    EVENT_WLAN_EXTSCAN_BUCKET_STARTED = 0xA92, /* 1 byte payload */
    EVENT_WLAN_EXTSCAN_BUCKET_COMPLETED = 0xA93, /* 4 byte payload */
    EVENT_WLAN_ROAM_SCAN_STARTED = 0xA94, /* 128 byte payload */


    EVENT_WLAN_ROAM_SCAN_COMPLETE = 0xA95,
    EVENT_WLAN_ROAM_CANDIDATE_FOUND = 0xA96,
    EVENT_WLAN_ROAM_SCAN_CONFIG = 0xA97,
    EVENT_WLAN_BT_COEX_BT_SCO_START = 0xA98,
    EVENT_WLAN_BT_COEX_BT_SCO_STOP = 0xA99,
    EVENT_WLAN_BT_COEX_BT_SCAN_START = 0xA9A,
    EVENT_WLAN_BT_COEX_BT_SCAN_STOP = 0xA9B,
    EVENT_WLAN_BT_COEX_BT_HID_START = 0xA9C,
    EVENT_WLAN_BT_COEX_BT_HID_STOP = 0xA9D,
    EVENT_WLAN_WAKE_LOCK = 0xAA2, /* 96 bytes payload */
    EVENT_WLAN_EAPOL = 0xA8D, /* 96 bytes payload */
    EVENT_WLAN_EXTSCAN_FEATURE_STOP = 0xAA3,
    EVENT_WLAN_EXTSCAN_RESULTS_AVAILABLE = 0xAA4,
    EVENT_WLAN_BEACON_EVENT = 0xAA6,
    EVENT_WLAN_LOG_COMPLETE = 0xAA7,
    EVENT_WLAN_LOW_RESOURCE_FAILURE = 0xABB,

    EVENT_MAX_ID = 0x0FFF
} event_id_enum_type;

typedef enum {
    LOG_DROP_ID = 0,
    LOG_WLAN_EXTSCAN_CAPABILITIES = 0x18F1,
    LOG_WLAN_EXTSCAN_FEATURE_STARTED = 0x18F2,
} log_id_enum_type;

typedef enum
{
    WLAN_PE_DIAG_SCAN_REQ_EVENT = 0,
    WLAN_PE_DIAG_SCAN_ABORT_IND_EVENT,
    WLAN_PE_DIAG_SCAN_RSP_EVENT,
    WLAN_PE_DIAG_JOIN_REQ_EVENT,
    WLAN_PE_DIAG_JOIN_RSP_EVENT,
    WLAN_PE_DIAG_SETCONTEXT_REQ_EVENT,
    WLAN_PE_DIAG_SETCONTEXT_RSP_EVENT,
    WLAN_PE_DIAG_REASSOC_REQ_EVENT,
    WLAN_PE_DIAG_REASSOC_RSP_EVENT,
    WLAN_PE_DIAG_AUTH_REQ_EVENT,
    WLAN_PE_DIAG_AUTH_RSP_EVENT = 10,
    WLAN_PE_DIAG_DISASSOC_REQ_EVENT,
    WLAN_PE_DIAG_DISASSOC_RSP_EVENT,
    WLAN_PE_DIAG_DISASSOC_IND_EVENT,
    WLAN_PE_DIAG_DISASSOC_CNF_EVENT,
    WLAN_PE_DIAG_DEAUTH_REQ_EVENT,
    WLAN_PE_DIAG_DEAUTH_RSP_EVENT,
    WLAN_PE_DIAG_DEAUTH_IND_EVENT,
    WLAN_PE_DIAG_START_BSS_REQ_EVENT,
    WLAN_PE_DIAG_START_BSS_RSP_EVENT,
    WLAN_PE_DIAG_AUTH_IND_EVENT = 20,
    WLAN_PE_DIAG_ASSOC_IND_EVENT,
    WLAN_PE_DIAG_ASSOC_CNF_EVENT,
    WLAN_PE_DIAG_REASSOC_IND_EVENT,
    WLAN_PE_DIAG_SWITCH_CHL_REQ_EVENT,
    WLAN_PE_DIAG_SWITCH_CHL_RSP_EVENT,
    WLAN_PE_DIAG_STOP_BSS_REQ_EVENT,
    WLAN_PE_DIAG_STOP_BSS_RSP_EVENT,
    WLAN_PE_DIAG_DEAUTH_CNF_EVENT,
    WLAN_PE_DIAG_ADDTS_REQ_EVENT,
    WLAN_PE_DIAG_ADDTS_RSP_EVENT = 30,
    WLAN_PE_DIAG_DELTS_REQ_EVENT,
    WLAN_PE_DIAG_DELTS_RSP_EVENT,
    WLAN_PE_DIAG_DELTS_IND_EVENT,
    WLAN_PE_DIAG_ENTER_BMPS_REQ_EVENT,
    WLAN_PE_DIAG_ENTER_BMPS_RSP_EVENT,
    WLAN_PE_DIAG_EXIT_BMPS_REQ_EVENT,
    WLAN_PE_DIAG_EXIT_BMPS_RSP_EVENT,
    WLAN_PE_DIAG_EXIT_BMPS_IND_EVENT,
    WLAN_PE_DIAG_ENTER_IMPS_REQ_EVENT,
    WLAN_PE_DIAG_ENTER_IMPS_RSP_EVENT = 40,
    WLAN_PE_DIAG_EXIT_IMPS_REQ_EVENT,
    WLAN_PE_DIAG_EXIT_IMPS_RSP_EVENT,
    WLAN_PE_DIAG_ENTER_UAPSD_REQ_EVENT,
    WLAN_PE_DIAG_ENTER_UAPSD_RSP_EVENT,
    WLAN_PE_DIAG_EXIT_UAPSD_REQ_EVENT,
    WLAN_PE_DIAG_EXIT_UAPSD_RSP_EVENT,
    WLAN_PE_DIAG_WOWL_ADD_BCAST_PTRN_EVENT,
    WLAN_PE_DIAG_WOWL_DEL_BCAST_PTRN_EVENT,
    WLAN_PE_DIAG_ENTER_WOWL_REQ_EVENT,
    WLAN_PE_DIAG_ENTER_WOWL_RSP_EVENT = 50,
    WLAN_PE_DIAG_EXIT_WOWL_REQ_EVENT,
    WLAN_PE_DIAG_EXIT_WOWL_RSP_EVENT,
    WLAN_PE_DIAG_HAL_ADDBA_REQ_EVENT,
    WLAN_PE_DIAG_HAL_ADDBA_RSP_EVENT,
    WLAN_PE_DIAG_HAL_DELBA_IND_EVENT,
    WLAN_PE_DIAG_HB_FAILURE_TIMEOUT,
    WLAN_PE_DIAG_PRE_AUTH_REQ_EVENT,
    WLAN_PE_DIAG_PRE_AUTH_RSP_EVENT,
    WLAN_PE_DIAG_PREAUTH_DONE,
    WLAN_PE_DIAG_REASSOCIATING = 60,
    WLAN_PE_DIAG_CONNECTED,
    WLAN_PE_DIAG_ASSOC_REQ_EVENT,
    WLAN_PE_DIAG_AUTH_COMP_EVENT,
    WLAN_PE_DIAG_ASSOC_COMP_EVENT,
    WLAN_PE_DIAG_AUTH_START_EVENT,
    WLAN_PE_DIAG_ASSOC_START_EVENT,
    WLAN_PE_DIAG_REASSOC_START_EVENT,
    WLAN_PE_DIAG_ROAM_AUTH_START_EVENT,
    WLAN_PE_DIAG_ROAM_AUTH_COMP_EVENT,
    WLAN_PE_DIAG_ROAM_ASSOC_START_EVENT = 70,
    WLAN_PE_DIAG_ROAM_ASSOC_COMP_EVENT,
    WLAN_PE_DIAG_SCAN_COMP_EVENT,
    WLAN_PE_DIAG_SCAN_RES_FOUND_EVENT,
    WLAN_PE_DIAG_ASSOC_TIMEOUT,
    WLAN_PE_DIAG_AUTH_TIMEOUT,
} wlan_host_diag_event_type;

typedef struct wlan_pe_event {
    char bssid[6];
    u16 event_type;
    u16 sme_state;
    u16 mlm_state;
    u16 status;
    u16 reason_code;
} __attribute__((packed)) wlan_pe_event_t;

typedef enum {
    WLAN_DRIVER_EAPOL_FRAME_TRANSMIT_REQUESTED = 0,
    WLAN_DRIVER_EAPOL_FRAME_RECEIVED,
} wlan_eapol_event_type;

#define EAPOL_MASK 0x8013
#define EAPOL_M1_MASK 0x8000
#define EAPOL_M2_MASK 0x0001
#define EAPOL_M3_MASK 0x8013
#define EAPOL_M4_MASK 0x0003

typedef struct wlan_eapol_event {
    u8 event_sub_type;
    u8 eapol_packet_type;
    u16 eapol_key_info;
    u16 eapol_rate;
    u8 dest_addr[6];
    u8 src_addr[6];
} __attribute__((packed)) wlan_eapol_event_t;

/*EVENT_WLAN_EXTSCAN_FEATURE_STARTED */
typedef struct wlan_ext_bucket {
    u8 bucket_id;
    u8 base_period_multiplier;
    u16 min_dwell_time_active;
    u16 max_dwell_time_active;
    u16 min_dwell_time_passive;
    u16 max_dwell_time_passive;
    u8 num_channels;
    u8 channel_offset;
    u8 forwarding_flags;
    u8 channel_band;
    u32 notify_extscan_events;
} __attribute__((packed)) wlan_ext_bucket_t;

typedef struct {
    u32 base_period;
    u32 max_iterations;
    u32 forwarding_flags;
    u32 configuration_flags;
    u32 notify_extscan_events;
    u32 scan_priority;
    u32 max_bssids_per_scan_cycle;
    u32 min_rssi;
    u32 max_table_usage;
    u32 min_dwell_time_active;
    u32 max_dwell_time_active;
    u32 min_dwell_time_passive;
    u32 max_dwell_time_passive;
    u32 min_rest_time;
    u32 max_rest_time;
    u32 n_probes;
    u32 repeat_probe_time;
    u32 probe_spacing_time;
    u32 idle_time;
    u32 max_scan_time;
    u32 probe_delay;
    u32 scan_ctrl_flags;
    u32 burst_duration;
    u32 num_buckets;
    wlan_ext_bucket bucket_list[8];
} __attribute__((packed)) wlan_ext_scan_feature_started_payload_type;
/*End EVENT_WLAN_EXTSCAN_FEATURE_STARTED*/

/*EVENT_WLAN_EXTSCAN_FEATURE_CHANNEL_CONFIG*/
typedef struct {
    u8 bucket_id;
    u16 scan_channels[40];
} __attribute__((packed)) wlan_ext_bucket_channels;

typedef struct {
    wlan_ext_bucket_channels bucket_list[3];
} __attribute__((packed)) wlan_ext_bucket_channel_config_payload_type;

/*End EVENT_WLAN_EXTSCAN_FEATURE_CHANNEL_CONFIG*/

/*EVENT_WLAN_EXTSCAN_CYCLE_STARTED*/
typedef struct {
    u32 scan_id;
    u32 timer_tick;
    u32 scheduled_bucket_mask;
    u32 scan_cycle_count;
} __attribute__((packed)) wlan_ext_scan_cycle_started_payload_type;
/*End EVENT_WLAN_EXTSCAN_CYCLE_STARTED*/

/*EVENT_WLAN_EXTSCAN_CYCLE_COMPLETED*/
typedef struct {
    u32 scan_id;
    u32 timer_tick;
    u32 scheduled_bucket_mask;
    u32 scan_cycle_count;
} __attribute__((packed)) wlan_ext_scan_cycle_completed_payload_type;
/*End EVENT_WLAN_EXTSCAN_CYCLE_COMPLETED*/

/*EVENT_WLAN_EXTSCAN_BUCKET_STARTED*/
typedef struct {
    u8 bucket_id;
} __attribute__((packed)) wlan_ext_scan_bucket_started_payload_type;
/*End EVENT_WLAN_EXTSCAN_BUCKET_STARTED*/

/*EVENT_WLAN_EXTSCAN_BUCKET_COMPLETED*/
typedef struct {
    u8 bucket_id;
} __attribute__((packed))  wlan_ext_scan_bucket_completed_payload_type;
/*End EVENT_WLAN_EXTSCAN_BUCKET_COMPLETED*/

/*EVENT_WLAN_ROAM_SCAN_STARTED*/
typedef struct {
    u32 scan_id;
    u32 roam_scan_flags;
    u32 cur_rssi;
    u16 scan_params[18];
    u16 scan_channels[40]; // first 40 channels only
} __attribute__((packed)) wlan_roam_scan_started_payload_type;
/*End EVENT_WLAN_ROAM_SCAN_STARTED*/

/*EVENT_WLAN_ROAM_SCAN_COMPLETE*/
typedef struct {
    u32 scan_id;
    u32 reason;
    u32 completion_flags;
    u32 num_candidate;
    u32 flags;
} __attribute__((packed)) wlan_roam_scan_complete_payload_type;
/*End EVENT_WLAN_ROAM_SCAN_COMPLETE*/

/*EVENT_WLAN_ROAM_CANDIDATE_FOUND*/
typedef struct {
    u8 channel;
    u8 rssi;
    u8 bssid[6];
    u8 ssid[33];
    u8 auth_mode;
    u8 ucast_cipher;
    u8 mcast_cipher;
} __attribute__((packed)) wlan_roam_candidate_found_payload_type;
/*End EVENT_WLAN_ROAM_CANDIDATE_FOUND*/

/*EVENT_WLAN_ROAM_SCAN_CONFIG*/
typedef struct {
    u32 flags;
    u32 roam_scan_config[8];
} __attribute__((packed)) wlan_roam_scan_config_payload_type;
/*End EVENT_WLAN_ROAM_SCAN_CONFIG*/

/* EVENT_WLAN_BT_COEX_BT_SCO_START */
typedef struct {
    u8 link_id;
    u8 link_state;
    u8 link_role;
    u8 link_type;
    u16 Tsco;
    u8 Rsco;
} __attribute__((packed)) wlan_bt_coex_bt_sco_start_payload_type;
/* End EVENT_WLAN_BT_COEX_BT_SCO_START */

/* EVENT_WLAN_BT_COEX_BT_SCO_STOP */
typedef struct {
    u8 link_id;
    u8 link_state;
    u8 link_role;
    u8 link_type;
    u16 Tsco;
    u8 Rsco;
} __attribute__((packed)) wlan_bt_coex_bt_sco_stop_payload_type;
/* End EVENT_WLAN_BT_COEX_BT_SCO_STOP */

/* EVENT_WLAN_BT_COEX_BT_SCAN_START */
typedef struct {
    u8 scan_type;
    u8 scan_bitmap;
} __attribute__((packed)) wlan_bt_coex_bt_scan_start_payload_type;

/* End EVENT_WLAN_BT_COEX_BT_SCAN_START */

/* EVENT_WLAN_BT_COEX_BT_SCAN_STOP */
typedef struct {
    u8 scan_type;
    u8 scan_bitmap;
} __attribute__((packed)) wlan_bt_coex_bt_scan_stop_payload_type;
/* End EVENT_WLAN_BT_COEX_BT_SCAN_STOP */

/* EVENT_WIFI_BT_COEX_BT_HID_START */
typedef struct {
    u8 link_id;
    u8 link_state;
    u8 link_role;
    u8 Tsniff;
    u8 attempts;
} __attribute__((packed)) wlan_bt_coex_bt_hid_start_payload_type;
/* End EVENT_WIFI_BT_COEX_BT_HID_START */

/* EVENT_WIFI_BT_COEX_BT_HID_STOP */
typedef struct {
    u8 link_id;
    u8 link_state;
    u8 link_role;
    u8 Tsniff;
    u8 attempts;
} __attribute__((packed)) wlan_bt_coex_bt_hid_stop_payload_type;
/* End EVENT_WIFI_BT_COEX_BT_HID_STOP */

/* EVENT_WLAN_EXTSCAN_FEATURE_STOP */
typedef struct {
    u32 request_id;
} __attribute__((packed)) wlan_ext_scan_feature_stop_payload_type;
/* End EVENT_WLAN_EXTSCAN_FEATURE_STOP */

/* EVENT_WLAN_EXTSCAN_RESULTS_AVAILABLE */
typedef struct {
    u32 request_id;
    u32 table_type;
    u32 entries_in_use;
    u32 maximum_entries;
    u32 scan_count_after_getResults;
    u8 threshold_num_scans;
} __attribute__((packed)) wlan_ext_scan_results_available_payload_type;
/* End EVENT_WLAN_EXTSCAN_RESULTS_AVAILABLE */

/* Log LOG_WLAN_EXTSCAN_CAPABILITIES */
typedef struct {
    u32 header;
    u32 request_id;
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
    u32 num_roam_ssid_whitelist;
    u32 num_roam_bssid_blacklist;
    u32 num_roam_bssid_preferred_list;
    u32 num_extscan_hotlist_ssid;
    u32 num_epno_networks;
} __attribute__((packed)) wlan_extscan_capabilities_event_fixed_param;

typedef struct {
    u32 header;
    u32 table_id;
    u32 scan_cache_entry_size;
    u32 max_scan_cache_entries;
    u32 max_buckets;
    u32 max_bssid_per_scan;
    u32 max_table_usage_threshold;
} __attribute__((packed)) wlan_extscan_cache_capabilities;

typedef struct {
    u32 tlv_header;
    u32 table_id;
    u32 wlan_hotlist_entry_size;
    u32 max_hotlist_entries;
} __attribute__((packed)) wlan_extscan_hotlist_monitor_capabilities;

typedef struct {
    u32 request_id;
    wlan_extscan_capabilities_event_fixed_param extscan_capabilities;
    wlan_extscan_cache_capabilities extscan_cache_capabilities;
    wlan_extscan_hotlist_monitor_capabilities extscan_hotlist_monitor_capabilities;
} __attribute__((packed)) wlan_ext_scan_capabilities_payload_type;
/* End LOG_WLAN_EXTSCAN_CAPABILITIES */

/* EVENT_WLAN_BEACON_RECEIVED */
typedef struct {
    u8 bssid[6];
    u32 beacon_rssi;
} __attribute__((packed)) wlan_beacon_received_payload_type;
/* End EVENT_WLAN_BEACON_RECEIVED */

typedef struct {
    u8 ucBaPeerMac[6];
    u8 ucBaTid;
    u8 ucBaBufferSize;
    u16 ucBaSSN;
    u8 fInitiator;
} __attribute__((packed)) wlan_add_block_ack_success_payload_type;

/* EVENT_WLAN_ADD_BLOCK_ACK_FAILED */
typedef struct {
    u8 ucBaPeerMac[6];
    u8 ucBaTid;
    u8 ucReasonCode;
    u8 fInitiator;
} __attribute__((packed)) wlan_add_block_ack_failed_payload_type;

typedef enum
{
    WIFI_EVENT_MEMORY_FAILURE,
} resource_failure_type;

typedef struct wlan_low_resource_failure_event
{
    resource_failure_type event_sub_type;
} __attribute__((packed)) wlan_low_resource_failure_event_t;

#endif /* WIFILOGGER_EVENT_DEFS_H */
