/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "wifi_legacy_hal_stubs.h"

// TODO: Remove these stubs from HalTool in libwifi-system.
namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace legacy_hal {
template <typename>
struct stubFunction;

template <typename R, typename... Args>
struct stubFunction<R (*)(Args...)> {
  static constexpr R invoke(Args...) { return WIFI_ERROR_NOT_SUPPORTED; }
};
template <typename... Args>
struct stubFunction<void (*)(Args...)> {
  static constexpr void invoke(Args...) {}
};

template <typename T>
void populateStubFor(T* val) {
  *val = &stubFunction<T>::invoke;
}

bool initHalFuncTableWithStubs(wifi_hal_fn* hal_fn) {
  if (hal_fn == nullptr) {
    return false;
  }
  populateStubFor(&hal_fn->wifi_initialize);
  populateStubFor(&hal_fn->wifi_cleanup);
  populateStubFor(&hal_fn->wifi_event_loop);
  populateStubFor(&hal_fn->wifi_get_error_info);
  populateStubFor(&hal_fn->wifi_get_supported_feature_set);
  populateStubFor(&hal_fn->wifi_get_concurrency_matrix);
  populateStubFor(&hal_fn->wifi_set_scanning_mac_oui);
  populateStubFor(&hal_fn->wifi_get_supported_channels);
  populateStubFor(&hal_fn->wifi_is_epr_supported);
  populateStubFor(&hal_fn->wifi_get_ifaces);
  populateStubFor(&hal_fn->wifi_get_iface_name);
  populateStubFor(&hal_fn->wifi_set_iface_event_handler);
  populateStubFor(&hal_fn->wifi_reset_iface_event_handler);
  populateStubFor(&hal_fn->wifi_start_gscan);
  populateStubFor(&hal_fn->wifi_stop_gscan);
  populateStubFor(&hal_fn->wifi_get_cached_gscan_results);
  populateStubFor(&hal_fn->wifi_set_bssid_hotlist);
  populateStubFor(&hal_fn->wifi_reset_bssid_hotlist);
  populateStubFor(&hal_fn->wifi_set_significant_change_handler);
  populateStubFor(&hal_fn->wifi_reset_significant_change_handler);
  populateStubFor(&hal_fn->wifi_get_gscan_capabilities);
  populateStubFor(&hal_fn->wifi_set_link_stats);
  populateStubFor(&hal_fn->wifi_get_link_stats);
  populateStubFor(&hal_fn->wifi_clear_link_stats);
  populateStubFor(&hal_fn->wifi_get_valid_channels);
  populateStubFor(&hal_fn->wifi_rtt_range_request);
  populateStubFor(&hal_fn->wifi_rtt_range_cancel);
  populateStubFor(&hal_fn->wifi_get_rtt_capabilities);
  populateStubFor(&hal_fn->wifi_rtt_get_responder_info);
  populateStubFor(&hal_fn->wifi_enable_responder);
  populateStubFor(&hal_fn->wifi_disable_responder);
  populateStubFor(&hal_fn->wifi_set_nodfs_flag);
  populateStubFor(&hal_fn->wifi_start_logging);
  populateStubFor(&hal_fn->wifi_set_epno_list);
  populateStubFor(&hal_fn->wifi_reset_epno_list);
  populateStubFor(&hal_fn->wifi_set_country_code);
  populateStubFor(&hal_fn->wifi_get_firmware_memory_dump);
  populateStubFor(&hal_fn->wifi_set_log_handler);
  populateStubFor(&hal_fn->wifi_reset_log_handler);
  populateStubFor(&hal_fn->wifi_set_alert_handler);
  populateStubFor(&hal_fn->wifi_reset_alert_handler);
  populateStubFor(&hal_fn->wifi_get_firmware_version);
  populateStubFor(&hal_fn->wifi_get_ring_buffers_status);
  populateStubFor(&hal_fn->wifi_get_logger_supported_feature_set);
  populateStubFor(&hal_fn->wifi_get_ring_data);
  populateStubFor(&hal_fn->wifi_enable_tdls);
  populateStubFor(&hal_fn->wifi_disable_tdls);
  populateStubFor(&hal_fn->wifi_get_tdls_status);
  populateStubFor(&hal_fn->wifi_get_tdls_capabilities);
  populateStubFor(&hal_fn->wifi_get_driver_version);
  populateStubFor(&hal_fn->wifi_set_passpoint_list);
  populateStubFor(&hal_fn->wifi_reset_passpoint_list);
  populateStubFor(&hal_fn->wifi_set_lci);
  populateStubFor(&hal_fn->wifi_set_lcr);
  populateStubFor(&hal_fn->wifi_start_sending_offloaded_packet);
  populateStubFor(&hal_fn->wifi_stop_sending_offloaded_packet);
  populateStubFor(&hal_fn->wifi_start_rssi_monitoring);
  populateStubFor(&hal_fn->wifi_stop_rssi_monitoring);
  populateStubFor(&hal_fn->wifi_get_wake_reason_stats);
  populateStubFor(&hal_fn->wifi_configure_nd_offload);
  populateStubFor(&hal_fn->wifi_get_driver_memory_dump);
  populateStubFor(&hal_fn->wifi_start_pkt_fate_monitoring);
  populateStubFor(&hal_fn->wifi_get_tx_pkt_fates);
  populateStubFor(&hal_fn->wifi_get_rx_pkt_fates);
  populateStubFor(&hal_fn->wifi_nan_enable_request);
  populateStubFor(&hal_fn->wifi_nan_disable_request);
  populateStubFor(&hal_fn->wifi_nan_publish_request);
  populateStubFor(&hal_fn->wifi_nan_publish_cancel_request);
  populateStubFor(&hal_fn->wifi_nan_subscribe_request);
  populateStubFor(&hal_fn->wifi_nan_subscribe_cancel_request);
  populateStubFor(&hal_fn->wifi_nan_transmit_followup_request);
  populateStubFor(&hal_fn->wifi_nan_stats_request);
  populateStubFor(&hal_fn->wifi_nan_config_request);
  populateStubFor(&hal_fn->wifi_nan_tca_request);
  populateStubFor(&hal_fn->wifi_nan_beacon_sdf_payload_request);
  populateStubFor(&hal_fn->wifi_nan_register_handler);
  populateStubFor(&hal_fn->wifi_nan_get_version);
  populateStubFor(&hal_fn->wifi_nan_get_capabilities);
  populateStubFor(&hal_fn->wifi_nan_data_interface_create);
  populateStubFor(&hal_fn->wifi_nan_data_interface_delete);
  populateStubFor(&hal_fn->wifi_nan_data_request_initiator);
  populateStubFor(&hal_fn->wifi_nan_data_indication_response);
  populateStubFor(&hal_fn->wifi_nan_data_end);
  populateStubFor(&hal_fn->wifi_get_packet_filter_capabilities);
  populateStubFor(&hal_fn->wifi_set_packet_filter);
  populateStubFor(&hal_fn->wifi_get_roaming_capabilities);
  populateStubFor(&hal_fn->wifi_enable_firmware_roaming);
  populateStubFor(&hal_fn->wifi_configure_roaming);
  populateStubFor(&hal_fn->wifi_select_tx_power_scenario);
  populateStubFor(&hal_fn->wifi_reset_tx_power_scenario);
  return true;
}
}  // namespace legacy_hal
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
