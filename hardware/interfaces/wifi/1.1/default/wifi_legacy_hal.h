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

#ifndef WIFI_LEGACY_HAL_H_
#define WIFI_LEGACY_HAL_H_

#include <functional>
#include <thread>
#include <vector>
#include <condition_variable>

#include <wifi_system/interface_tool.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
// This is in a separate namespace to prevent typename conflicts between
// the legacy HAL types and the HIDL interface types.
namespace legacy_hal {
// Wrap all the types defined inside the legacy HAL header files inside this
// namespace.
#include <hardware_legacy/wifi_hal.h>

// APF capabilities supported by the iface.
struct PacketFilterCapabilities {
  uint32_t version;
  uint32_t max_len;
};

// WARNING: We don't care about the variable sized members of either
// |wifi_iface_stat|, |wifi_radio_stat| structures. So, using the pragma
// to escape the compiler warnings regarding this.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wgnu-variable-sized-type-not-at-end"
// The |wifi_radio_stat.tx_time_per_levels| stats is provided as a pointer in
// |wifi_radio_stat| structure in the legacy HAL API. Separate that out
// into a separate return element to avoid passing pointers around.
struct LinkLayerRadioStats {
  wifi_radio_stat stats;
  std::vector<uint32_t> tx_time_per_levels;
};

struct LinkLayerStats {
  wifi_iface_stat iface;
  std::vector<LinkLayerRadioStats> radios;
};
#pragma GCC diagnostic pop

// The |WLAN_DRIVER_WAKE_REASON_CNT.cmd_event_wake_cnt| and
// |WLAN_DRIVER_WAKE_REASON_CNT.driver_fw_local_wake_cnt| stats is provided
// as a pointer in |WLAN_DRIVER_WAKE_REASON_CNT| structure in the legacy HAL
// API. Separate that out into a separate return elements to avoid passing
// pointers around.
struct WakeReasonStats {
  WLAN_DRIVER_WAKE_REASON_CNT wake_reason_cnt;
  std::vector<uint32_t> cmd_event_wake_cnt;
  std::vector<uint32_t> driver_fw_local_wake_cnt;
};

// NAN response and event callbacks struct.
struct NanCallbackHandlers {
  // NotifyResponse invoked to notify the status of the Request.
  std::function<void(transaction_id, const NanResponseMsg&)> on_notify_response;
  // Various event callbacks.
  std::function<void(const NanPublishTerminatedInd&)>
      on_event_publish_terminated;
  std::function<void(const NanMatchInd&)> on_event_match;
  std::function<void(const NanMatchExpiredInd&)> on_event_match_expired;
  std::function<void(const NanSubscribeTerminatedInd&)>
      on_event_subscribe_terminated;
  std::function<void(const NanFollowupInd&)> on_event_followup;
  std::function<void(const NanDiscEngEventInd&)> on_event_disc_eng_event;
  std::function<void(const NanDisabledInd&)> on_event_disabled;
  std::function<void(const NanTCAInd&)> on_event_tca;
  std::function<void(const NanBeaconSdfPayloadInd&)>
      on_event_beacon_sdf_payload;
  std::function<void(const NanDataPathRequestInd&)> on_event_data_path_request;
  std::function<void(const NanDataPathConfirmInd&)> on_event_data_path_confirm;
  std::function<void(const NanDataPathEndInd&)> on_event_data_path_end;
  std::function<void(const NanTransmitFollowupInd&)>
      on_event_transmit_follow_up;
  std::function<void(const NanRangeRequestInd&)>
      on_event_range_request;
  std::function<void(const NanRangeReportInd&)>
      on_event_range_report;
};

// Full scan results contain IE info and are hence passed by reference, to
// preserve the variable length array member |ie_data|. Callee must not retain
// the pointer.
using on_gscan_full_result_callback =
    std::function<void(wifi_request_id, const wifi_scan_result*, uint32_t)>;
// These scan results don't contain any IE info, so no need to pass by
// reference.
using on_gscan_results_callback = std::function<void(
    wifi_request_id, const std::vector<wifi_cached_scan_results>&)>;

// Invoked when the rssi value breaches the thresholds set.
using on_rssi_threshold_breached_callback =
    std::function<void(wifi_request_id, std::array<uint8_t, 6>, int8_t)>;

// Callback for RTT range request results.
// Rtt results contain IE info and are hence passed by reference, to
// preserve the |LCI| and |LCR| pointers. Callee must not retain
// the pointer.
using on_rtt_results_callback = std::function<void(
    wifi_request_id, const std::vector<const wifi_rtt_result*>&)>;

// Callback for ring buffer data.
using on_ring_buffer_data_callback =
    std::function<void(const std::string&,
                       const std::vector<uint8_t>&,
                       const wifi_ring_buffer_status&)>;

// Callback for alerts.
using on_error_alert_callback =
    std::function<void(int32_t, const std::vector<uint8_t>&)>;
/**
 * Class that encapsulates all legacy HAL interactions.
 * This class manages the lifetime of the event loop thread used by legacy HAL.
 *
 * Note: aThere will only be a single instance of this class created in the Wifi
 * object and will be valid for the lifetime of the process.
 */
class WifiLegacyHal {
 public:
  WifiLegacyHal();
  // Names to use for the different types of iface.
  std::string getApIfaceName();
  std::string getNanIfaceName();
  std::string getP2pIfaceName();
  std::string getStaIfaceName();

  // Initialize the legacy HAL function table.
  wifi_error initialize();
  // Start the legacy HAL and the event looper thread.
  wifi_error start();
  // Deinitialize the legacy HAL and wait for the event loop thread to exit
  // using a predefined timeout.
  wifi_error stop(std::unique_lock<std::recursive_mutex>* lock,
                  const std::function<void()>& on_complete_callback);
  // Wrappers for all the functions in the legacy HAL function table.
  std::pair<wifi_error, std::string> getDriverVersion();
  std::pair<wifi_error, std::string> getFirmwareVersion();
  std::pair<wifi_error, std::vector<uint8_t>> requestDriverMemoryDump();
  std::pair<wifi_error, std::vector<uint8_t>> requestFirmwareMemoryDump();
  std::pair<wifi_error, uint32_t> getSupportedFeatureSet();
  // APF functions.
  std::pair<wifi_error, PacketFilterCapabilities> getPacketFilterCapabilities();
  wifi_error setPacketFilter(const std::vector<uint8_t>& program);
  // Gscan functions.
  std::pair<wifi_error, wifi_gscan_capabilities> getGscanCapabilities();
  // These API's provides a simplified interface over the legacy Gscan API's:
  // a) All scan events from the legacy HAL API other than the
  //    |WIFI_SCAN_FAILED| are treated as notification of results.
  //    This method then retrieves the cached scan results from the legacy
  //    HAL API and triggers the externally provided |on_results_user_callback|
  //    on success.
  // b) |WIFI_SCAN_FAILED| scan event or failure to retrieve cached scan results
  //    triggers the externally provided |on_failure_user_callback|.
  // c) Full scan result event triggers the externally provided
  //    |on_full_result_user_callback|.
  wifi_error startGscan(
      wifi_request_id id,
      const wifi_scan_cmd_params& params,
      const std::function<void(wifi_request_id)>& on_failure_callback,
      const on_gscan_results_callback& on_results_callback,
      const on_gscan_full_result_callback& on_full_result_callback);
  wifi_error stopGscan(wifi_request_id id);
  std::pair<wifi_error, std::vector<uint32_t>> getValidFrequenciesForBand(
      wifi_band band);
  wifi_error setDfsFlag(bool dfs_on);
  // Link layer stats functions.
  wifi_error enableLinkLayerStats(bool debug);
  wifi_error disableLinkLayerStats();
  std::pair<wifi_error, LinkLayerStats> getLinkLayerStats();
  // RSSI monitor functions.
  wifi_error startRssiMonitoring(wifi_request_id id,
                                 int8_t max_rssi,
                                 int8_t min_rssi,
                                 const on_rssi_threshold_breached_callback&
                                     on_threshold_breached_callback);
  wifi_error stopRssiMonitoring(wifi_request_id id);
  std::pair<wifi_error, wifi_roaming_capabilities> getRoamingCapabilities();
  wifi_error configureRoaming(const wifi_roaming_config& config);
  wifi_error enableFirmwareRoaming(fw_roaming_state_t state);
  wifi_error configureNdOffload(bool enable);
  wifi_error startSendingOffloadedPacket(
      uint32_t cmd_id,
      const std::vector<uint8_t>& ip_packet_data,
      const std::array<uint8_t, 6>& src_address,
      const std::array<uint8_t, 6>& dst_address,
      uint32_t period_in_ms);
  wifi_error stopSendingOffloadedPacket(uint32_t cmd_id);
  wifi_error setScanningMacOui(const std::array<uint8_t, 3>& oui);
  wifi_error selectTxPowerScenario(wifi_power_scenario scenario);
  wifi_error resetTxPowerScenario();
  // Logger/debug functions.
  std::pair<wifi_error, uint32_t> getLoggerSupportedFeatureSet();
  wifi_error startPktFateMonitoring();
  std::pair<wifi_error, std::vector<wifi_tx_report>> getTxPktFates();
  std::pair<wifi_error, std::vector<wifi_rx_report>> getRxPktFates();
  std::pair<wifi_error, WakeReasonStats> getWakeReasonStats();
  wifi_error registerRingBufferCallbackHandler(
      const on_ring_buffer_data_callback& on_data_callback);
  wifi_error deregisterRingBufferCallbackHandler();
  std::pair<wifi_error, std::vector<wifi_ring_buffer_status>>
  getRingBuffersStatus();
  wifi_error startRingBufferLogging(const std::string& ring_name,
                                    uint32_t verbose_level,
                                    uint32_t max_interval_sec,
                                    uint32_t min_data_size);
  wifi_error getRingBufferData(const std::string& ring_name);
  wifi_error registerErrorAlertCallbackHandler(
      const on_error_alert_callback& on_alert_callback);
  wifi_error deregisterErrorAlertCallbackHandler();
  // RTT functions.
  wifi_error startRttRangeRequest(
      wifi_request_id id,
      const std::vector<wifi_rtt_config>& rtt_configs,
      const on_rtt_results_callback& on_results_callback);
  wifi_error cancelRttRangeRequest(
      wifi_request_id id, const std::vector<std::array<uint8_t, 6>>& mac_addrs);
  std::pair<wifi_error, wifi_rtt_capabilities> getRttCapabilities();
  std::pair<wifi_error, wifi_rtt_responder> getRttResponderInfo();
  wifi_error enableRttResponder(wifi_request_id id,
                                const wifi_channel_info& channel_hint,
                                uint32_t max_duration_secs,
                                const wifi_rtt_responder& info);
  wifi_error disableRttResponder(wifi_request_id id);
  wifi_error setRttLci(wifi_request_id id, const wifi_lci_information& info);
  wifi_error setRttLcr(wifi_request_id id, const wifi_lcr_information& info);
  // NAN functions.
  wifi_error nanRegisterCallbackHandlers(const NanCallbackHandlers& callbacks);
  wifi_error nanEnableRequest(transaction_id id, const NanEnableRequest& msg);
  wifi_error nanDisableRequest(transaction_id id);
  wifi_error nanPublishRequest(transaction_id id, const NanPublishRequest& msg);
  wifi_error nanPublishCancelRequest(transaction_id id,
                                     const NanPublishCancelRequest& msg);
  wifi_error nanSubscribeRequest(transaction_id id,
                                 const NanSubscribeRequest& msg);
  wifi_error nanSubscribeCancelRequest(transaction_id id,
                                       const NanSubscribeCancelRequest& msg);
  wifi_error nanTransmitFollowupRequest(transaction_id id,
                                        const NanTransmitFollowupRequest& msg);
  wifi_error nanStatsRequest(transaction_id id, const NanStatsRequest& msg);
  wifi_error nanConfigRequest(transaction_id id, const NanConfigRequest& msg);
  wifi_error nanTcaRequest(transaction_id id, const NanTCARequest& msg);
  wifi_error nanBeaconSdfPayloadRequest(transaction_id id,
                                        const NanBeaconSdfPayloadRequest& msg);
  std::pair<wifi_error, NanVersion> nanGetVersion();
  wifi_error nanGetCapabilities(transaction_id id);
  wifi_error nanDataInterfaceCreate(transaction_id id,
                                    const std::string& iface_name);
  wifi_error nanDataInterfaceDelete(transaction_id id,
                                    const std::string& iface_name);
  wifi_error nanDataRequestInitiator(transaction_id id,
                                     const NanDataPathInitiatorRequest& msg);
  wifi_error nanDataIndicationResponse(
      transaction_id id, const NanDataPathIndicationResponse& msg);
  wifi_error nanDataEnd(transaction_id id, uint32_t ndpInstanceId);
  // AP functions.
  wifi_error setCountryCode(std::array<int8_t, 2> code);

 private:
  // Retrieve the interface handle to be used for the "wlan" interface.
  wifi_error retrieveWlanInterfaceHandle();
  // Run the legacy HAL event loop thread.
  void runEventLoop();
  // Retrieve the cached gscan results to pass the results back to the external
  // callbacks.
  std::pair<wifi_error, std::vector<wifi_cached_scan_results>>
  getGscanCachedResults();
  void invalidate();

  // Global function table of legacy HAL.
  wifi_hal_fn global_func_table_;
  // Opaque handle to be used for all global operations.
  wifi_handle global_handle_;
  // Opaque handle to be used for all wlan0 interface specific operations.
  wifi_interface_handle wlan_interface_handle_;
  // Flag to indicate if we have initiated the cleanup of legacy HAL.
  std::atomic<bool> awaiting_event_loop_termination_;
  std::condition_variable_any stop_wait_cv_;
  // Flag to indicate if the legacy HAL has been started.
  bool is_started_;
  wifi_system::InterfaceTool iface_tool_;
};

}  // namespace legacy_hal
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // WIFI_LEGACY_HAL_H_
