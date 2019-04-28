/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef IPA_QMI_SERVICE_V01_H
#define IPA_QMI_SERVICE_V01_H
#define QMI_IPA_IPFLTR_NUM_IHL_RANGE_16_EQNS_V01 2
#define QMI_IPA_IPFLTR_NUM_MEQ_32_EQNS_V01 2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_IPFLTR_NUM_IHL_MEQ_32_EQNS_V01 2
#define QMI_IPA_IPFLTR_NUM_MEQ_128_EQNS_V01 2
#define QMI_IPA_MAX_FILTERS_V01 64
#define QMI_IPA_MAX_FILTERS_EX_V01 128
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_MAX_PIPES_V01 20
#define QMI_IPA_MAX_APN_V01 8
#define IPA_INT_MAX ((int) (~0U >> 1))
#define IPA_INT_MIN (- IPA_INT_MAX - 1)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum ipa_qmi_result_type_v01 {
  IPA_QMI_RESULT_TYPE_MIN_ENUM_VAL_V01 = IPA_INT_MIN,
  IPA_QMI_RESULT_SUCCESS_V01 = 0,
  IPA_QMI_RESULT_FAILURE_V01 = 1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_QMI_RESULT_TYPE_MAX_ENUM_VAL_V01 = IPA_INT_MAX,
};
enum ipa_qmi_error_type_v01 {
  IPA_QMI_ERROR_TYPE_MIN_ENUM_VAL_V01 = IPA_INT_MIN,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_QMI_ERR_NONE_V01 = 0x0000,
  IPA_QMI_ERR_MALFORMED_MSG_V01 = 0x0001,
  IPA_QMI_ERR_NO_MEMORY_V01 = 0x0002,
  IPA_QMI_ERR_INTERNAL_V01 = 0x0003,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_QMI_ERR_CLIENT_IDS_EXHAUSTED_V01 = 0x0005,
  IPA_QMI_ERR_INVALID_ID_V01 = 0x0029,
  IPA_QMI_ERR_ENCODING_V01 = 0x003A,
  IPA_QMI_ERR_INCOMPATIBLE_STATE_V01 = 0x005A,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_QMI_ERR_NOT_SUPPORTED_V01 = 0x005E,
  IPA_QMI_ERROR_TYPE_MAX_ENUM_VAL_V01 = IPA_INT_MAX,
};
struct ipa_qmi_response_type_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_qmi_result_type_v01 result;
  enum ipa_qmi_error_type_v01 error;
};
enum ipa_platform_type_enum_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_PLATFORM_TYPE_ENUM_MIN_ENUM_VAL_V01 = - 2147483647,
  QMI_IPA_PLATFORM_TYPE_INVALID_V01 = 0,
  QMI_IPA_PLATFORM_TYPE_TN_V01 = 1,
  QMI_IPA_PLATFORM_TYPE_LE_V01 = 2,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  QMI_IPA_PLATFORM_TYPE_MSM_ANDROID_V01 = 3,
  QMI_IPA_PLATFORM_TYPE_MSM_WINDOWS_V01 = 4,
  QMI_IPA_PLATFORM_TYPE_MSM_QNX_V01 = 5,
  IPA_PLATFORM_TYPE_ENUM_MAX_ENUM_VAL_V01 = 2147483647
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_hdr_tbl_info_type_v01 {
  uint32_t modem_offset_start;
  uint32_t modem_offset_end;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_route_tbl_info_type_v01 {
  uint32_t route_tbl_start_addr;
  uint32_t num_indices;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_modem_mem_info_type_v01 {
  uint32_t block_start_addr;
  uint32_t size;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_hdr_proc_ctx_tbl_info_type_v01 {
  uint32_t modem_offset_start;
  uint32_t modem_offset_end;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_zip_tbl_info_type_v01 {
  uint32_t modem_offset_start;
  uint32_t modem_offset_end;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_init_modem_driver_req_msg_v01 {
  uint8_t platform_type_valid;
  enum ipa_platform_type_enum_v01 platform_type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t hdr_tbl_info_valid;
  struct ipa_hdr_tbl_info_type_v01 hdr_tbl_info;
  uint8_t v4_route_tbl_info_valid;
  struct ipa_route_tbl_info_type_v01 v4_route_tbl_info;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t v6_route_tbl_info_valid;
  struct ipa_route_tbl_info_type_v01 v6_route_tbl_info;
  uint8_t v4_filter_tbl_start_addr_valid;
  uint32_t v4_filter_tbl_start_addr;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t v6_filter_tbl_start_addr_valid;
  uint32_t v6_filter_tbl_start_addr;
  uint8_t modem_mem_info_valid;
  struct ipa_modem_mem_info_type_v01 modem_mem_info;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t ctrl_comm_dest_end_pt_valid;
  uint32_t ctrl_comm_dest_end_pt;
  uint8_t is_ssr_bootup_valid;
  uint8_t is_ssr_bootup;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t hdr_proc_ctx_tbl_info_valid;
  struct ipa_hdr_proc_ctx_tbl_info_type_v01 hdr_proc_ctx_tbl_info;
  uint8_t zip_tbl_info_valid;
  struct ipa_zip_tbl_info_type_v01 zip_tbl_info;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t v4_hash_route_tbl_info_valid;
  struct ipa_route_tbl_info_type_v01 v4_hash_route_tbl_info;
  uint8_t v6_hash_route_tbl_info_valid;
  struct ipa_route_tbl_info_type_v01 v6_hash_route_tbl_info;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t v4_hash_filter_tbl_start_addr_valid;
  uint32_t v4_hash_filter_tbl_start_addr;
  uint8_t v6_hash_filter_tbl_start_addr_valid;
  uint32_t v6_hash_filter_tbl_start_addr;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_init_modem_driver_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
  uint8_t ctrl_comm_dest_end_pt_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t ctrl_comm_dest_end_pt;
  uint8_t default_end_pt_valid;
  uint32_t default_end_pt;
  uint8_t modem_driver_init_pending_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t modem_driver_init_pending;
};
struct ipa_init_modem_driver_cmplt_req_msg_v01 {
  uint8_t status;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_init_modem_driver_cmplt_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_indication_reg_req_msg_v01 {
  uint8_t master_driver_init_complete_valid;
  uint8_t master_driver_init_complete;
  uint8_t data_usage_quota_reached_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t data_usage_quota_reached;
};
struct ipa_indication_reg_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_master_driver_init_complt_ind_msg_v01 {
  struct ipa_qmi_response_type_v01 master_driver_init_status;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_ipfltr_range_eq_16_type_v01 {
  uint8_t offset;
  uint16_t range_low;
  uint16_t range_high;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_ipfltr_mask_eq_32_type_v01 {
  uint8_t offset;
  uint32_t mask;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t value;
};
struct ipa_ipfltr_eq_16_type_v01 {
  uint8_t offset;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t value;
};
struct ipa_ipfltr_eq_32_type_v01 {
  uint8_t offset;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t value;
};
struct ipa_ipfltr_mask_eq_128_type_v01 {
  uint8_t offset;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t mask[16];
  uint8_t value[16];
};
struct ipa_filter_rule_type_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t rule_eq_bitmap;
  uint8_t tos_eq_present;
  uint8_t tos_eq;
  uint8_t protocol_eq_present;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t protocol_eq;
  uint8_t num_ihl_offset_range_16;
  struct ipa_ipfltr_range_eq_16_type_v01 ihl_offset_range_16[QMI_IPA_IPFLTR_NUM_IHL_RANGE_16_EQNS_V01];
  uint8_t num_offset_meq_32;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_ipfltr_mask_eq_32_type_v01 offset_meq_32[QMI_IPA_IPFLTR_NUM_MEQ_32_EQNS_V01];
  uint8_t tc_eq_present;
  uint8_t tc_eq;
  uint8_t flow_eq_present;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t flow_eq;
  uint8_t ihl_offset_eq_16_present;
  struct ipa_ipfltr_eq_16_type_v01 ihl_offset_eq_16;
  uint8_t ihl_offset_eq_32_present;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_ipfltr_eq_32_type_v01 ihl_offset_eq_32;
  uint8_t num_ihl_offset_meq_32;
  struct ipa_ipfltr_mask_eq_32_type_v01 ihl_offset_meq_32[QMI_IPA_IPFLTR_NUM_IHL_MEQ_32_EQNS_V01];
  uint8_t num_offset_meq_128;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_ipfltr_mask_eq_128_type_v01 offset_meq_128[QMI_IPA_IPFLTR_NUM_MEQ_128_EQNS_V01];
  uint8_t metadata_meq32_present;
  struct ipa_ipfltr_mask_eq_32_type_v01 metadata_meq32;
  uint8_t ipv4_frag_eq_present;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum ipa_ip_type_enum_v01 {
  IPA_IP_TYPE_ENUM_MIN_ENUM_VAL_V01 = - 2147483647,
  QMI_IPA_IP_TYPE_INVALID_V01 = 0,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  QMI_IPA_IP_TYPE_V4_V01 = 1,
  QMI_IPA_IP_TYPE_V6_V01 = 2,
  QMI_IPA_IP_TYPE_V4V6_V01 = 3,
  IPA_IP_TYPE_ENUM_MAX_ENUM_VAL_V01 = 2147483647
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum ipa_filter_action_enum_v01 {
  IPA_FILTER_ACTION_ENUM_MIN_ENUM_VAL_V01 = - 2147483647,
  QMI_IPA_FILTER_ACTION_INVALID_V01 = 0,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  QMI_IPA_FILTER_ACTION_SRC_NAT_V01 = 1,
  QMI_IPA_FILTER_ACTION_DST_NAT_V01 = 2,
  QMI_IPA_FILTER_ACTION_ROUTING_V01 = 3,
  QMI_IPA_FILTER_ACTION_EXCEPTION_V01 = 4,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_FILTER_ACTION_ENUM_MAX_ENUM_VAL_V01 = 2147483647
};
struct ipa_filter_spec_type_v01 {
  uint32_t filter_spec_identifier;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_ip_type_enum_v01 ip_type;
  struct ipa_filter_rule_type_v01 filter_rule;
  enum ipa_filter_action_enum_v01 filter_action;
  uint8_t is_routing_table_index_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t route_table_index;
  uint8_t is_mux_id_valid;
  uint32_t mux_id;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_filter_spec_ex_type_v01 {
  enum ipa_ip_type_enum_v01 ip_type;
  struct ipa_filter_rule_type_v01 filter_rule;
  enum ipa_filter_action_enum_v01 filter_action;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t is_routing_table_index_valid;
  uint32_t route_table_index;
  uint8_t is_mux_id_valid;
  uint32_t mux_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t rule_id;
  uint8_t is_rule_hashable;
};
struct ipa_install_fltr_rule_req_msg_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t filter_spec_list_valid;
  uint32_t filter_spec_list_len;
  struct ipa_filter_spec_type_v01 filter_spec_list[QMI_IPA_MAX_FILTERS_V01];
  uint8_t source_pipe_index_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t source_pipe_index;
  uint8_t num_ipv4_filters_valid;
  uint32_t num_ipv4_filters;
  uint8_t num_ipv6_filters_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t num_ipv6_filters;
  uint8_t xlat_filter_indices_list_valid;
  uint32_t xlat_filter_indices_list_len;
  uint32_t xlat_filter_indices_list[QMI_IPA_MAX_FILTERS_V01];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t filter_spec_ex_list_valid;
  uint32_t filter_spec_ex_list_len;
  struct ipa_filter_spec_ex_type_v01 filter_spec_ex_list[QMI_IPA_MAX_FILTERS_V01];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_filter_rule_identifier_to_handle_map_v01 {
  uint32_t filter_spec_identifier;
  uint32_t filter_handle;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_install_fltr_rule_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
  uint8_t filter_handle_list_valid;
  uint32_t filter_handle_list_len;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_filter_rule_identifier_to_handle_map_v01 filter_handle_list[QMI_IPA_MAX_FILTERS_V01];
  uint8_t rule_id_valid;
  uint32_t rule_id_len;
  uint32_t rule_id[QMI_IPA_MAX_FILTERS_V01];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_filter_handle_to_index_map_v01 {
  uint32_t filter_handle;
  uint32_t filter_index;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_fltr_installed_notif_req_msg_v01 {
  uint32_t source_pipe_index;
  enum ipa_qmi_result_type_v01 install_status;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t filter_index_list_len;
  struct ipa_filter_handle_to_index_map_v01 filter_index_list[QMI_IPA_MAX_FILTERS_V01];
  uint8_t embedded_pipe_index_valid;
  uint32_t embedded_pipe_index;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t retain_header_valid;
  uint8_t retain_header;
  uint8_t embedded_call_mux_id_valid;
  uint32_t embedded_call_mux_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_ipv4_filters_valid;
  uint32_t num_ipv4_filters;
  uint8_t num_ipv6_filters_valid;
  uint32_t num_ipv6_filters;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t start_ipv4_filter_idx_valid;
  uint32_t start_ipv4_filter_idx;
  uint8_t start_ipv6_filter_idx_valid;
  uint32_t start_ipv6_filter_idx;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t rule_id_valid;
  uint32_t rule_id_len;
  uint32_t rule_id[QMI_IPA_MAX_FILTERS_V01];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_fltr_installed_notif_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
};
struct ipa_enable_force_clear_datapath_req_msg_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t source_pipe_bitmask;
  uint32_t request_id;
  uint8_t throttle_source_valid;
  uint8_t throttle_source;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_enable_force_clear_datapath_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_disable_force_clear_datapath_req_msg_v01 {
  uint32_t request_id;
};
struct ipa_disable_force_clear_datapath_resp_msg_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_qmi_response_type_v01 resp;
};
enum ipa_peripheral_speed_enum_v01 {
  IPA_PERIPHERAL_SPEED_ENUM_MIN_ENUM_VAL_V01 = - 2147483647,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  QMI_IPA_PER_USB_FS_V01 = 1,
  QMI_IPA_PER_USB_HS_V01 = 2,
  QMI_IPA_PER_USB_SS_V01 = 3,
  QMI_IPA_PER_WLAN_V01 = 4,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IPA_PERIPHERAL_SPEED_ENUM_MAX_ENUM_VAL_V01 = 2147483647
};
enum ipa_pipe_mode_enum_v01 {
  IPA_PIPE_MODE_ENUM_MIN_ENUM_VAL_V01 = - 2147483647,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  QMI_IPA_PIPE_MODE_HW_V01 = 1,
  QMI_IPA_PIPE_MODE_SW_V01 = 2,
  IPA_PIPE_MODE_ENUM_MAX_ENUM_VAL_V01 = 2147483647
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum ipa_peripheral_type_enum_v01 {
  IPA_PERIPHERAL_TYPE_ENUM_MIN_ENUM_VAL_V01 = - 2147483647,
  QMI_IPA_PERIPHERAL_USB_V01 = 1,
  QMI_IPA_PERIPHERAL_HSIC_V01 = 2,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  QMI_IPA_PERIPHERAL_PCIE_V01 = 3,
  IPA_PERIPHERAL_TYPE_ENUM_MAX_ENUM_VAL_V01 = 2147483647
};
struct ipa_config_req_msg_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t peripheral_type_valid;
  enum ipa_peripheral_type_enum_v01 peripheral_type;
  uint8_t hw_deaggr_supported_valid;
  uint8_t hw_deaggr_supported;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t max_aggr_frame_size_valid;
  uint32_t max_aggr_frame_size;
  uint8_t ipa_ingress_pipe_mode_valid;
  enum ipa_pipe_mode_enum_v01 ipa_ingress_pipe_mode;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t peripheral_speed_info_valid;
  enum ipa_peripheral_speed_enum_v01 peripheral_speed_info;
  uint8_t dl_accumulation_time_limit_valid;
  uint32_t dl_accumulation_time_limit;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t dl_accumulation_pkt_limit_valid;
  uint32_t dl_accumulation_pkt_limit;
  uint8_t dl_accumulation_byte_limit_valid;
  uint32_t dl_accumulation_byte_limit;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t ul_accumulation_time_limit_valid;
  uint32_t ul_accumulation_time_limit;
  uint8_t hw_control_flags_valid;
  uint32_t hw_control_flags;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t ul_msi_event_threshold_valid;
  uint32_t ul_msi_event_threshold;
  uint8_t dl_msi_event_threshold_valid;
  uint32_t dl_msi_event_threshold;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t ul_fifo_size_valid;
  uint32_t ul_fifo_size;
  uint8_t dl_fifo_size_valid;
  uint32_t dl_fifo_size;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t dl_buf_size_valid;
  uint32_t dl_buf_size;
};
struct ipa_config_resp_msg_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_qmi_response_type_v01 resp;
};
enum ipa_stats_type_enum_v01 {
  IPA_STATS_TYPE_ENUM_MIN_ENUM_VAL_V01 = - 2147483647,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  QMI_IPA_STATS_TYPE_INVALID_V01 = 0,
  QMI_IPA_STATS_TYPE_PIPE_V01 = 1,
  QMI_IPA_STATS_TYPE_FILTER_RULES_V01 = 2,
  IPA_STATS_TYPE_ENUM_MAX_ENUM_VAL_V01 = 2147483647
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_pipe_stats_info_type_v01 {
  uint32_t pipe_index;
  uint64_t num_ipv4_packets;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint64_t num_ipv4_bytes;
  uint64_t num_ipv6_packets;
  uint64_t num_ipv6_bytes;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_stats_type_filter_rule_v01 {
  uint32_t filter_rule_index;
  uint64_t num_packets;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_get_data_stats_req_msg_v01 {
  enum ipa_stats_type_enum_v01 ipa_stats_type;
  uint8_t reset_stats_valid;
  uint8_t reset_stats;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_get_data_stats_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
  uint8_t ipa_stats_type_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ipa_stats_type_enum_v01 ipa_stats_type;
  uint8_t ul_src_pipe_stats_list_valid;
  uint32_t ul_src_pipe_stats_list_len;
  struct ipa_pipe_stats_info_type_v01 ul_src_pipe_stats_list[QMI_IPA_MAX_PIPES_V01];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t dl_dst_pipe_stats_list_valid;
  uint32_t dl_dst_pipe_stats_list_len;
  struct ipa_pipe_stats_info_type_v01 dl_dst_pipe_stats_list[QMI_IPA_MAX_PIPES_V01];
  uint8_t dl_filter_rule_stats_list_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t dl_filter_rule_stats_list_len;
  struct ipa_stats_type_filter_rule_v01 dl_filter_rule_stats_list[QMI_IPA_MAX_FILTERS_V01];
};
struct ipa_apn_data_stats_info_type_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t mux_id;
  uint64_t num_ul_packets;
  uint64_t num_ul_bytes;
  uint64_t num_dl_packets;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint64_t num_dl_bytes;
};
struct ipa_get_apn_data_stats_req_msg_v01 {
  uint8_t mux_id_list_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t mux_id_list_len;
  uint32_t mux_id_list[QMI_IPA_MAX_APN_V01];
};
struct ipa_get_apn_data_stats_resp_msg_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_qmi_response_type_v01 resp;
  uint8_t apn_data_stats_list_valid;
  uint32_t apn_data_stats_list_len;
  struct ipa_apn_data_stats_info_type_v01 apn_data_stats_list[QMI_IPA_MAX_APN_V01];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_data_usage_quota_info_type_v01 {
  uint32_t mux_id;
  uint64_t num_Mbytes;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_set_data_usage_quota_req_msg_v01 {
  uint8_t apn_quota_list_valid;
  uint32_t apn_quota_list_len;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_data_usage_quota_info_type_v01 apn_quota_list[QMI_IPA_MAX_APN_V01];
};
struct ipa_set_data_usage_quota_resp_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct ipa_data_usage_quota_reached_ind_msg_v01 {
  struct ipa_data_usage_quota_info_type_v01 apn;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_stop_data_usage_quota_req_msg_v01 {
  char __placeholder;
};
struct ipa_stop_data_usage_quota_resp_msg_v01 {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct ipa_qmi_response_type_v01 resp;
};
struct ipa_install_fltr_rule_req_ex_msg_v01 {
  uint8_t filter_spec_ex_list_valid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t filter_spec_ex_list_len;
  struct ipa_filter_spec_ex_type_v01 filter_spec_ex_list[QMI_IPA_MAX_FILTERS_EX_V01];
  uint8_t source_pipe_index_valid;
  uint32_t source_pipe_index;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t num_ipv4_filters_valid;
  uint32_t num_ipv4_filters;
  uint8_t num_ipv6_filters_valid;
  uint32_t num_ipv6_filters;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t xlat_filter_indices_list_valid;
  uint32_t xlat_filter_indices_list_len;
  uint32_t xlat_filter_indices_list[QMI_IPA_MAX_FILTERS_EX_V01];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ipa_install_fltr_rule_resp_ex_msg_v01 {
  struct ipa_qmi_response_type_v01 resp;
  uint8_t rule_id_valid;
  uint32_t rule_id_len;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t rule_id[QMI_IPA_MAX_FILTERS_EX_V01];
};
#define QMI_IPA_INDICATION_REGISTER_REQ_V01 0x0020
#define QMI_IPA_INDICATION_REGISTER_RESP_V01 0x0020
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_INIT_MODEM_DRIVER_REQ_V01 0x0021
#define QMI_IPA_INIT_MODEM_DRIVER_RESP_V01 0x0021
#define QMI_IPA_MASTER_DRIVER_INIT_COMPLETE_IND_V01 0x0022
#define QMI_IPA_INSTALL_FILTER_RULE_REQ_V01 0x0023
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_INSTALL_FILTER_RULE_RESP_V01 0x0023
#define QMI_IPA_FILTER_INSTALLED_NOTIF_REQ_V01 0x0024
#define QMI_IPA_FILTER_INSTALLED_NOTIF_RESP_V01 0x0024
#define QMI_IPA_ENABLE_FORCE_CLEAR_DATAPATH_REQ_V01 0x0025
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_ENABLE_FORCE_CLEAR_DATAPATH_RESP_V01 0x0025
#define QMI_IPA_DISABLE_FORCE_CLEAR_DATAPATH_REQ_V01 0x0026
#define QMI_IPA_DISABLE_FORCE_CLEAR_DATAPATH_RESP_V01 0x0026
#define QMI_IPA_CONFIG_REQ_V01 0x0027
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_CONFIG_RESP_V01 0x0027
#define QMI_IPA_DISABLE_LINK_LOW_PWR_STATE_REQ_V01 0x0028
#define QMI_IPA_DISABLE_LINK_LOW_PWR_STATE_RESP_V01 0x0028
#define QMI_IPA_ENABLE_LINK_LOW_PWR_STATE_REQ_V01 0x0029
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_ENABLE_LINK_LOW_PWR_STATE_RESP_V01 0x0029
#define QMI_IPA_GET_DATA_STATS_REQ_V01 0x0030
#define QMI_IPA_GET_DATA_STATS_RESP_V01 0x0030
#define QMI_IPA_GET_APN_DATA_STATS_REQ_V01 0x0031
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_GET_APN_DATA_STATS_RESP_V01 0x0031
#define QMI_IPA_SET_DATA_USAGE_QUOTA_REQ_V01 0x0032
#define QMI_IPA_SET_DATA_USAGE_QUOTA_RESP_V01 0x0032
#define QMI_IPA_DATA_USAGE_QUOTA_REACHED_IND_V01 0x0033
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_STOP_DATA_USAGE_QUOTA_REQ_V01 0x0034
#define QMI_IPA_STOP_DATA_USAGE_QUOTA_RESP_V01 0x0034
#define QMI_IPA_INIT_MODEM_DRIVER_CMPLT_REQ_V01 0x0035
#define QMI_IPA_INIT_MODEM_DRIVER_CMPLT_RESP_V01 0x0035
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_INSTALL_FILTER_RULE_EX_REQ_V01 0x0037
#define QMI_IPA_INSTALL_FILTER_RULE_EX_RESP_V01 0x0037
#define QMI_IPA_INIT_MODEM_DRIVER_REQ_MAX_MSG_LEN_V01 134
#define QMI_IPA_INIT_MODEM_DRIVER_RESP_MAX_MSG_LEN_V01 25
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_INDICATION_REGISTER_REQ_MAX_MSG_LEN_V01 8
#define QMI_IPA_INDICATION_REGISTER_RESP_MAX_MSG_LEN_V01 7
#define QMI_IPA_INSTALL_FILTER_RULE_REQ_MAX_MSG_LEN_V01 22369
#define QMI_IPA_INSTALL_FILTER_RULE_RESP_MAX_MSG_LEN_V01 783
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_FILTER_INSTALLED_NOTIF_REQ_MAX_MSG_LEN_V01 834
#define QMI_IPA_FILTER_INSTALLED_NOTIF_RESP_MAX_MSG_LEN_V01 7
#define QMI_IPA_MASTER_DRIVER_INIT_COMPLETE_IND_MAX_MSG_LEN_V01 7
#define QMI_IPA_DATA_USAGE_QUOTA_REACHED_IND_MAX_MSG_LEN_V01 15
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_ENABLE_FORCE_CLEAR_DATAPATH_REQ_MAX_MSG_LEN_V01 18
#define QMI_IPA_DISABLE_FORCE_CLEAR_DATAPATH_REQ_MAX_MSG_LEN_V01 7
#define QMI_IPA_ENABLE_FORCE_CLEAR_DATAPATH_RESP_MAX_MSG_LEN_V01 7
#define QMI_IPA_DISABLE_FORCE_CLEAR_DATAPATH_RESP_MAX_MSG_LEN_V01 7
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_CONFIG_REQ_MAX_MSG_LEN_V01 102
#define QMI_IPA_CONFIG_RESP_MAX_MSG_LEN_V01 7
#define QMI_IPA_DISABLE_LINK_LOW_PWR_STATE_REQ_MAX_MSG_LEN_V01 18
#define QMI_IPA_DISABLE_LINK_LOW_PWR_STATE_RESP_MAX_MSG_LEN_V01 7
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_ENABLE_LINK_LOW_PWR_STATE_REQ_MAX_MSG_LEN_V01 7
#define QMI_IPA_ENABLE_LINK_LOW_PWR_STATE_RESP_MAX_MSG_LEN_V01 7
#define QMI_IPA_GET_DATA_STATS_REQ_MAX_MSG_LEN_V01 11
#define QMI_IPA_GET_DATA_STATS_RESP_MAX_MSG_LEN_V01 2234
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_GET_APN_DATA_STATS_REQ_MAX_MSG_LEN_V01 36
#define QMI_IPA_GET_APN_DATA_STATS_RESP_MAX_MSG_LEN_V01 299
#define QMI_IPA_SET_DATA_USAGE_QUOTA_REQ_MAX_MSG_LEN_V01 100
#define QMI_IPA_SET_DATA_USAGE_QUOTA_RESP_MAX_MSG_LEN_V01 7
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_STOP_DATA_USAGE_QUOTA_REQ_MAX_MSG_LEN_V01 0
#define QMI_IPA_STOP_DATA_USAGE_QUOTA_RESP_MAX_MSG_LEN_V01 7
#define QMI_IPA_INIT_MODEM_DRIVER_CMPLT_REQ_MAX_MSG_LEN_V01 4
#define QMI_IPA_INIT_MODEM_DRIVER_CMPLT_RESP_MAX_MSG_LEN_V01 7
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define QMI_IPA_INSTALL_FILTER_RULE_EX_REQ_MAX_MSG_LEN_V01 22685
#define QMI_IPA_INSTALL_FILTER_RULE_EX_RESP_MAX_MSG_LEN_V01 523
#endif

