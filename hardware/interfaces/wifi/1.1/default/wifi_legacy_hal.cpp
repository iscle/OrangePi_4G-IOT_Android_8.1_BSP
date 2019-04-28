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

#include <array>
#include <chrono>

#include <android-base/logging.h>
#include <cutils/properties.h>

#include "hidl_sync_util.h"
#include "wifi_legacy_hal.h"
#include "wifi_legacy_hal_stubs.h"

namespace {
// Constants ported over from the legacy HAL calling code
// (com_android_server_wifi_WifiNative.cpp). This will all be thrown
// away when this shim layer is replaced by the real vendor
// implementation.
static constexpr uint32_t kMaxVersionStringLength = 256;
static constexpr uint32_t kMaxCachedGscanResults = 64;
static constexpr uint32_t kMaxGscanFrequenciesForBand = 64;
static constexpr uint32_t kLinkLayerStatsDataMpduSizeThreshold = 128;
static constexpr uint32_t kMaxWakeReasonStatsArraySize = 32;
static constexpr uint32_t kMaxRingBuffers = 10;
static constexpr uint32_t kMaxStopCompleteWaitMs = 100;

// Helper function to create a non-const char* for legacy Hal API's.
std::vector<char> makeCharVec(const std::string& str) {
  std::vector<char> vec(str.size() + 1);
  vec.assign(str.begin(), str.end());
  vec.push_back('\0');
  return vec;
}
}  // namespace

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace legacy_hal {
// Legacy HAL functions accept "C" style function pointers, so use global
// functions to pass to the legacy HAL function and store the corresponding
// std::function methods to be invoked.
//
// Callback to be invoked once |stop| is complete
std::function<void(wifi_handle handle)> on_stop_complete_internal_callback;
void onAsyncStopComplete(wifi_handle handle) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_stop_complete_internal_callback) {
    on_stop_complete_internal_callback(handle);
    // Invalidate this callback since we don't want this firing again.
    on_stop_complete_internal_callback = nullptr;
  }
}

// Callback to be invoked for driver dump.
std::function<void(char*, int)> on_driver_memory_dump_internal_callback;
void onSyncDriverMemoryDump(char* buffer, int buffer_size) {
  if (on_driver_memory_dump_internal_callback) {
    on_driver_memory_dump_internal_callback(buffer, buffer_size);
  }
}

// Callback to be invoked for firmware dump.
std::function<void(char*, int)> on_firmware_memory_dump_internal_callback;
void onSyncFirmwareMemoryDump(char* buffer, int buffer_size) {
  if (on_firmware_memory_dump_internal_callback) {
    on_firmware_memory_dump_internal_callback(buffer, buffer_size);
  }
}

// Callback to be invoked for Gscan events.
std::function<void(wifi_request_id, wifi_scan_event)>
    on_gscan_event_internal_callback;
void onAsyncGscanEvent(wifi_request_id id, wifi_scan_event event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_gscan_event_internal_callback) {
    on_gscan_event_internal_callback(id, event);
  }
}

// Callback to be invoked for Gscan full results.
std::function<void(wifi_request_id, wifi_scan_result*, uint32_t)>
    on_gscan_full_result_internal_callback;
void onAsyncGscanFullResult(wifi_request_id id,
                            wifi_scan_result* result,
                            uint32_t buckets_scanned) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_gscan_full_result_internal_callback) {
    on_gscan_full_result_internal_callback(id, result, buckets_scanned);
  }
}

// Callback to be invoked for link layer stats results.
std::function<void((wifi_request_id, wifi_iface_stat*, int, wifi_radio_stat*))>
    on_link_layer_stats_result_internal_callback;
void onSyncLinkLayerStatsResult(wifi_request_id id,
                                wifi_iface_stat* iface_stat,
                                int num_radios,
                                wifi_radio_stat* radio_stat) {
  if (on_link_layer_stats_result_internal_callback) {
    on_link_layer_stats_result_internal_callback(
        id, iface_stat, num_radios, radio_stat);
  }
}

// Callback to be invoked for rssi threshold breach.
std::function<void((wifi_request_id, uint8_t*, int8_t))>
    on_rssi_threshold_breached_internal_callback;
void onAsyncRssiThresholdBreached(wifi_request_id id,
                                  uint8_t* bssid,
                                  int8_t rssi) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_rssi_threshold_breached_internal_callback) {
    on_rssi_threshold_breached_internal_callback(id, bssid, rssi);
  }
}

// Callback to be invoked for ring buffer data indication.
std::function<void(char*, char*, int, wifi_ring_buffer_status*)>
    on_ring_buffer_data_internal_callback;
void onAsyncRingBufferData(char* ring_name,
                           char* buffer,
                           int buffer_size,
                           wifi_ring_buffer_status* status) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_ring_buffer_data_internal_callback) {
    on_ring_buffer_data_internal_callback(
        ring_name, buffer, buffer_size, status);
  }
}

// Callback to be invoked for error alert indication.
std::function<void(wifi_request_id, char*, int, int)>
    on_error_alert_internal_callback;
void onAsyncErrorAlert(wifi_request_id id,
                       char* buffer,
                       int buffer_size,
                       int err_code) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_error_alert_internal_callback) {
    on_error_alert_internal_callback(id, buffer, buffer_size, err_code);
  }
}

// Callback to be invoked for rtt results results.
std::function<void(
    wifi_request_id, unsigned num_results, wifi_rtt_result* rtt_results[])>
    on_rtt_results_internal_callback;
void onAsyncRttResults(wifi_request_id id,
                       unsigned num_results,
                       wifi_rtt_result* rtt_results[]) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_rtt_results_internal_callback) {
    on_rtt_results_internal_callback(id, num_results, rtt_results);
    on_rtt_results_internal_callback = nullptr;
  }
}

// Callbacks for the various NAN operations.
// NOTE: These have very little conversions to perform before invoking the user
// callbacks.
// So, handle all of them here directly to avoid adding an unnecessary layer.
std::function<void(transaction_id, const NanResponseMsg&)>
    on_nan_notify_response_user_callback;
void onAysncNanNotifyResponse(transaction_id id, NanResponseMsg* msg) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_notify_response_user_callback && msg) {
    on_nan_notify_response_user_callback(id, *msg);
  }
}

std::function<void(const NanPublishRepliedInd&)>
    on_nan_event_publish_replied_user_callback;
void onAysncNanEventPublishReplied(NanPublishRepliedInd* /* event */) {
  LOG(ERROR) << "onAysncNanEventPublishReplied triggered";
}

std::function<void(const NanPublishTerminatedInd&)>
    on_nan_event_publish_terminated_user_callback;
void onAysncNanEventPublishTerminated(NanPublishTerminatedInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_publish_terminated_user_callback && event) {
    on_nan_event_publish_terminated_user_callback(*event);
  }
}

std::function<void(const NanMatchInd&)> on_nan_event_match_user_callback;
void onAysncNanEventMatch(NanMatchInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_match_user_callback && event) {
    on_nan_event_match_user_callback(*event);
  }
}

std::function<void(const NanMatchExpiredInd&)>
    on_nan_event_match_expired_user_callback;
void onAysncNanEventMatchExpired(NanMatchExpiredInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_match_expired_user_callback && event) {
    on_nan_event_match_expired_user_callback(*event);
  }
}

std::function<void(const NanSubscribeTerminatedInd&)>
    on_nan_event_subscribe_terminated_user_callback;
void onAysncNanEventSubscribeTerminated(NanSubscribeTerminatedInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_subscribe_terminated_user_callback && event) {
    on_nan_event_subscribe_terminated_user_callback(*event);
  }
}

std::function<void(const NanFollowupInd&)> on_nan_event_followup_user_callback;
void onAysncNanEventFollowup(NanFollowupInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_followup_user_callback && event) {
    on_nan_event_followup_user_callback(*event);
  }
}

std::function<void(const NanDiscEngEventInd&)>
    on_nan_event_disc_eng_event_user_callback;
void onAysncNanEventDiscEngEvent(NanDiscEngEventInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_disc_eng_event_user_callback && event) {
    on_nan_event_disc_eng_event_user_callback(*event);
  }
}

std::function<void(const NanDisabledInd&)> on_nan_event_disabled_user_callback;
void onAysncNanEventDisabled(NanDisabledInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_disabled_user_callback && event) {
    on_nan_event_disabled_user_callback(*event);
  }
}

std::function<void(const NanTCAInd&)> on_nan_event_tca_user_callback;
void onAysncNanEventTca(NanTCAInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_tca_user_callback && event) {
    on_nan_event_tca_user_callback(*event);
  }
}

std::function<void(const NanBeaconSdfPayloadInd&)>
    on_nan_event_beacon_sdf_payload_user_callback;
void onAysncNanEventBeaconSdfPayload(NanBeaconSdfPayloadInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_beacon_sdf_payload_user_callback && event) {
    on_nan_event_beacon_sdf_payload_user_callback(*event);
  }
}

std::function<void(const NanDataPathRequestInd&)>
    on_nan_event_data_path_request_user_callback;
void onAysncNanEventDataPathRequest(NanDataPathRequestInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_data_path_request_user_callback && event) {
    on_nan_event_data_path_request_user_callback(*event);
  }
}
std::function<void(const NanDataPathConfirmInd&)>
    on_nan_event_data_path_confirm_user_callback;
void onAysncNanEventDataPathConfirm(NanDataPathConfirmInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_data_path_confirm_user_callback && event) {
    on_nan_event_data_path_confirm_user_callback(*event);
  }
}

std::function<void(const NanDataPathEndInd&)>
    on_nan_event_data_path_end_user_callback;
void onAysncNanEventDataPathEnd(NanDataPathEndInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_data_path_end_user_callback && event) {
    on_nan_event_data_path_end_user_callback(*event);
  }
}

std::function<void(const NanTransmitFollowupInd&)>
    on_nan_event_transmit_follow_up_user_callback;
void onAysncNanEventTransmitFollowUp(NanTransmitFollowupInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_transmit_follow_up_user_callback && event) {
    on_nan_event_transmit_follow_up_user_callback(*event);
  }
}

std::function<void(const NanRangeRequestInd&)>
    on_nan_event_range_request_user_callback;
void onAysncNanEventRangeRequest(NanRangeRequestInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_range_request_user_callback && event) {
    on_nan_event_range_request_user_callback(*event);
  }
}

std::function<void(const NanRangeReportInd&)>
    on_nan_event_range_report_user_callback;
void onAysncNanEventRangeReport(NanRangeReportInd* event) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (on_nan_event_range_report_user_callback && event) {
    on_nan_event_range_report_user_callback(*event);
  }
}
// End of the free-standing "C" style callbacks.

WifiLegacyHal::WifiLegacyHal()
    : global_handle_(nullptr),
      wlan_interface_handle_(nullptr),
      awaiting_event_loop_termination_(false),
      is_started_(false) {}

wifi_error WifiLegacyHal::initialize() {
  LOG(DEBUG) << "Initialize legacy HAL";
  // TODO: Add back the HAL Tool if we need to. All we need from the HAL tool
  // for now is this function call which we can directly call.
  if (!initHalFuncTableWithStubs(&global_func_table_)) {
    LOG(ERROR) << "Failed to initialize legacy hal function table with stubs";
    return WIFI_ERROR_UNKNOWN;
  }
  wifi_error status = init_wifi_vendor_hal_func_table(&global_func_table_);
  if (status != WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to initialize legacy hal function table";
  }
  return status;
}

wifi_error WifiLegacyHal::start() {
  // Ensure that we're starting in a good state.
  CHECK(global_func_table_.wifi_initialize && !global_handle_ &&
        !wlan_interface_handle_ && !awaiting_event_loop_termination_);
  if (is_started_) {
    LOG(DEBUG) << "Legacy HAL already started";
    return WIFI_SUCCESS;
  }
  LOG(DEBUG) << "Starting legacy HAL";
  if (!iface_tool_.SetWifiUpState(true)) {
    LOG(ERROR) << "Failed to set WiFi interface up";
    return WIFI_ERROR_UNKNOWN;
  }
  wifi_error status = global_func_table_.wifi_initialize(&global_handle_);
  if (status != WIFI_SUCCESS || !global_handle_) {
    LOG(ERROR) << "Failed to retrieve global handle";
    return status;
  }
  std::thread(&WifiLegacyHal::runEventLoop, this).detach();
  status = retrieveWlanInterfaceHandle();
  if (status != WIFI_SUCCESS || !wlan_interface_handle_) {
    LOG(ERROR) << "Failed to retrieve wlan interface handle";
    return status;
  }
  LOG(DEBUG) << "Legacy HAL start complete";
  is_started_ = true;
  return WIFI_SUCCESS;
}

wifi_error WifiLegacyHal::stop(
    /* NONNULL */ std::unique_lock<std::recursive_mutex>* lock,
    const std::function<void()>& on_stop_complete_user_callback) {
  if (!is_started_) {
    LOG(DEBUG) << "Legacy HAL already stopped";
    on_stop_complete_user_callback();
    return WIFI_SUCCESS;
  }
  LOG(DEBUG) << "Stopping legacy HAL";
  on_stop_complete_internal_callback =
      [on_stop_complete_user_callback, this](wifi_handle handle) {
    CHECK_EQ(global_handle_, handle) << "Handle mismatch";
    LOG(INFO) << "Legacy HAL stop complete callback received";
    // Invalidate all the internal pointers now that the HAL is
    // stopped.
    invalidate();
    iface_tool_.SetWifiUpState(false);
    on_stop_complete_user_callback();
    is_started_ = false;
  };
  awaiting_event_loop_termination_ = true;
  global_func_table_.wifi_cleanup(global_handle_, onAsyncStopComplete);
  const auto status = stop_wait_cv_.wait_for(
      *lock, std::chrono::milliseconds(kMaxStopCompleteWaitMs),
      [this] { return !awaiting_event_loop_termination_; });
  if (!status) {
    LOG(ERROR) << "Legacy HAL stop failed or timed out";
    return WIFI_ERROR_UNKNOWN;
  }
  LOG(DEBUG) << "Legacy HAL stop complete";
  return WIFI_SUCCESS;
}

std::string WifiLegacyHal::getApIfaceName() {
  // Fake name. This interface does not exist in legacy HAL
  // API's.
  return "ap0";
}

std::string WifiLegacyHal::getNanIfaceName() {
  // Fake name. This interface does not exist in legacy HAL
  // API's.
  return "nan0";
}

std::string WifiLegacyHal::getP2pIfaceName() {
  std::array<char, PROPERTY_VALUE_MAX> buffer;
  property_get("wifi.direct.interface", buffer.data(), "p2p0");
  return buffer.data();
}

std::string WifiLegacyHal::getStaIfaceName() {
  std::array<char, PROPERTY_VALUE_MAX> buffer;
  property_get("wifi.interface", buffer.data(), "wlan0");
  return buffer.data();
}

std::pair<wifi_error, std::string> WifiLegacyHal::getDriverVersion() {
  std::array<char, kMaxVersionStringLength> buffer;
  buffer.fill(0);
  wifi_error status = global_func_table_.wifi_get_driver_version(
      wlan_interface_handle_, buffer.data(), buffer.size());
  return {status, buffer.data()};
}

std::pair<wifi_error, std::string> WifiLegacyHal::getFirmwareVersion() {
  std::array<char, kMaxVersionStringLength> buffer;
  buffer.fill(0);
  wifi_error status = global_func_table_.wifi_get_firmware_version(
      wlan_interface_handle_, buffer.data(), buffer.size());
  return {status, buffer.data()};
}

std::pair<wifi_error, std::vector<uint8_t>>
WifiLegacyHal::requestDriverMemoryDump() {
  std::vector<uint8_t> driver_dump;
  on_driver_memory_dump_internal_callback = [&driver_dump](char* buffer,
                                                           int buffer_size) {
    driver_dump.insert(driver_dump.end(),
                       reinterpret_cast<uint8_t*>(buffer),
                       reinterpret_cast<uint8_t*>(buffer) + buffer_size);
  };
  wifi_error status = global_func_table_.wifi_get_driver_memory_dump(
      wlan_interface_handle_, {onSyncDriverMemoryDump});
  on_driver_memory_dump_internal_callback = nullptr;
  return {status, std::move(driver_dump)};
}

std::pair<wifi_error, std::vector<uint8_t>>
WifiLegacyHal::requestFirmwareMemoryDump() {
  std::vector<uint8_t> firmware_dump;
  on_firmware_memory_dump_internal_callback = [&firmware_dump](
      char* buffer, int buffer_size) {
    firmware_dump.insert(firmware_dump.end(),
                         reinterpret_cast<uint8_t*>(buffer),
                         reinterpret_cast<uint8_t*>(buffer) + buffer_size);
  };
  wifi_error status = global_func_table_.wifi_get_firmware_memory_dump(
      wlan_interface_handle_, {onSyncFirmwareMemoryDump});
  on_firmware_memory_dump_internal_callback = nullptr;
  return {status, std::move(firmware_dump)};
}

std::pair<wifi_error, uint32_t> WifiLegacyHal::getSupportedFeatureSet() {
  feature_set set;
  static_assert(sizeof(set) == sizeof(uint32_t),
                "Some features can not be represented in output");
  wifi_error status = global_func_table_.wifi_get_supported_feature_set(
      wlan_interface_handle_, &set);
  return {status, static_cast<uint32_t>(set)};
}

std::pair<wifi_error, PacketFilterCapabilities>
WifiLegacyHal::getPacketFilterCapabilities() {
  PacketFilterCapabilities caps;
  wifi_error status = global_func_table_.wifi_get_packet_filter_capabilities(
      wlan_interface_handle_, &caps.version, &caps.max_len);
  return {status, caps};
}

wifi_error WifiLegacyHal::setPacketFilter(const std::vector<uint8_t>& program) {
  return global_func_table_.wifi_set_packet_filter(
      wlan_interface_handle_, program.data(), program.size());
}

std::pair<wifi_error, wifi_gscan_capabilities>
WifiLegacyHal::getGscanCapabilities() {
  wifi_gscan_capabilities caps;
  wifi_error status = global_func_table_.wifi_get_gscan_capabilities(
      wlan_interface_handle_, &caps);
  return {status, caps};
}

wifi_error WifiLegacyHal::startGscan(
    wifi_request_id id,
    const wifi_scan_cmd_params& params,
    const std::function<void(wifi_request_id)>& on_failure_user_callback,
    const on_gscan_results_callback& on_results_user_callback,
    const on_gscan_full_result_callback& on_full_result_user_callback) {
  // If there is already an ongoing background scan, reject new scan requests.
  if (on_gscan_event_internal_callback ||
      on_gscan_full_result_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }

  // This callback will be used to either trigger |on_results_user_callback| or
  // |on_failure_user_callback|.
  on_gscan_event_internal_callback =
      [on_failure_user_callback, on_results_user_callback, this](
          wifi_request_id id, wifi_scan_event event) {
        switch (event) {
          case WIFI_SCAN_RESULTS_AVAILABLE:
          case WIFI_SCAN_THRESHOLD_NUM_SCANS:
          case WIFI_SCAN_THRESHOLD_PERCENT: {
            wifi_error status;
            std::vector<wifi_cached_scan_results> cached_scan_results;
            std::tie(status, cached_scan_results) = getGscanCachedResults();
            if (status == WIFI_SUCCESS) {
              on_results_user_callback(id, cached_scan_results);
              return;
            }
          }
          // Fall through if failed. Failure to retrieve cached scan results
          // should trigger a background scan failure.
          case WIFI_SCAN_FAILED:
            on_failure_user_callback(id);
            on_gscan_event_internal_callback = nullptr;
            on_gscan_full_result_internal_callback = nullptr;
            return;
        }
        LOG(FATAL) << "Unexpected gscan event received: " << event;
      };

  on_gscan_full_result_internal_callback = [on_full_result_user_callback](
      wifi_request_id id, wifi_scan_result* result, uint32_t buckets_scanned) {
    if (result) {
      on_full_result_user_callback(id, result, buckets_scanned);
    }
  };

  wifi_scan_result_handler handler = {onAsyncGscanFullResult,
                                      onAsyncGscanEvent};
  wifi_error status = global_func_table_.wifi_start_gscan(
      id, wlan_interface_handle_, params, handler);
  if (status != WIFI_SUCCESS) {
    on_gscan_event_internal_callback = nullptr;
    on_gscan_full_result_internal_callback = nullptr;
  }
  return status;
}

wifi_error WifiLegacyHal::stopGscan(wifi_request_id id) {
  // If there is no an ongoing background scan, reject stop requests.
  // TODO(b/32337212): This needs to be handled by the HIDL object because we
  // need to return the NOT_STARTED error code.
  if (!on_gscan_event_internal_callback &&
      !on_gscan_full_result_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  wifi_error status =
      global_func_table_.wifi_stop_gscan(id, wlan_interface_handle_);
  // If the request Id is wrong, don't stop the ongoing background scan. Any
  // other error should be treated as the end of background scan.
  if (status != WIFI_ERROR_INVALID_REQUEST_ID) {
    on_gscan_event_internal_callback = nullptr;
    on_gscan_full_result_internal_callback = nullptr;
  }
  return status;
}

std::pair<wifi_error, std::vector<uint32_t>>
WifiLegacyHal::getValidFrequenciesForBand(wifi_band band) {
  static_assert(sizeof(uint32_t) >= sizeof(wifi_channel),
                "Wifi Channel cannot be represented in output");
  std::vector<uint32_t> freqs;
  freqs.resize(kMaxGscanFrequenciesForBand);
  int32_t num_freqs = 0;
  wifi_error status = global_func_table_.wifi_get_valid_channels(
      wlan_interface_handle_,
      band,
      freqs.size(),
      reinterpret_cast<wifi_channel*>(freqs.data()),
      &num_freqs);
  CHECK(num_freqs >= 0 &&
        static_cast<uint32_t>(num_freqs) <= kMaxGscanFrequenciesForBand);
  freqs.resize(num_freqs);
  return {status, std::move(freqs)};
}

wifi_error WifiLegacyHal::setDfsFlag(bool dfs_on) {
  return global_func_table_.wifi_set_nodfs_flag(
      wlan_interface_handle_, dfs_on ? 0 : 1);
}

wifi_error WifiLegacyHal::enableLinkLayerStats(bool debug) {
  wifi_link_layer_params params;
  params.mpdu_size_threshold = kLinkLayerStatsDataMpduSizeThreshold;
  params.aggressive_statistics_gathering = debug;
  return global_func_table_.wifi_set_link_stats(wlan_interface_handle_, params);
}

wifi_error WifiLegacyHal::disableLinkLayerStats() {
  // TODO: Do we care about these responses?
  uint32_t clear_mask_rsp;
  uint8_t stop_rsp;
  return global_func_table_.wifi_clear_link_stats(
      wlan_interface_handle_, 0xFFFFFFFF, &clear_mask_rsp, 1, &stop_rsp);
}

std::pair<wifi_error, LinkLayerStats> WifiLegacyHal::getLinkLayerStats() {
  LinkLayerStats link_stats{};
  LinkLayerStats* link_stats_ptr = &link_stats;

  on_link_layer_stats_result_internal_callback =
      [&link_stats_ptr](wifi_request_id /* id */,
                        wifi_iface_stat* iface_stats_ptr,
                        int num_radios,
                        wifi_radio_stat* radio_stats_ptr) {
        if (iface_stats_ptr != nullptr) {
          link_stats_ptr->iface = *iface_stats_ptr;
          link_stats_ptr->iface.num_peers = 0;
        } else {
          LOG(ERROR) << "Invalid iface stats in link layer stats";
        }
        if (num_radios <= 0 || radio_stats_ptr == nullptr) {
          LOG(ERROR) << "Invalid radio stats in link layer stats";
          return;
        }
        for (int i = 0; i < num_radios; i++) {
          LinkLayerRadioStats radio;
          radio.stats = radio_stats_ptr[i];
          // Copy over the tx level array to the separate vector.
          if (radio_stats_ptr[i].num_tx_levels > 0 &&
              radio_stats_ptr[i].tx_time_per_levels != nullptr) {
            radio.tx_time_per_levels.assign(
                radio_stats_ptr[i].tx_time_per_levels,
                radio_stats_ptr[i].tx_time_per_levels +
                    radio_stats_ptr[i].num_tx_levels);
          }
          radio.stats.num_tx_levels = 0;
          radio.stats.tx_time_per_levels = nullptr;
          link_stats_ptr->radios.push_back(radio);
        }
      };

  wifi_error status = global_func_table_.wifi_get_link_stats(
      0, wlan_interface_handle_, {onSyncLinkLayerStatsResult});
  on_link_layer_stats_result_internal_callback = nullptr;
  return {status, link_stats};
}

wifi_error WifiLegacyHal::startRssiMonitoring(
    wifi_request_id id,
    int8_t max_rssi,
    int8_t min_rssi,
    const on_rssi_threshold_breached_callback&
        on_threshold_breached_user_callback) {
  if (on_rssi_threshold_breached_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  on_rssi_threshold_breached_internal_callback =
      [on_threshold_breached_user_callback](
          wifi_request_id id, uint8_t* bssid_ptr, int8_t rssi) {
        if (!bssid_ptr) {
          return;
        }
        std::array<uint8_t, 6> bssid_arr;
        // |bssid_ptr| pointer is assumed to have 6 bytes for the mac address.
        std::copy(bssid_ptr, bssid_ptr + 6, std::begin(bssid_arr));
        on_threshold_breached_user_callback(id, bssid_arr, rssi);
      };
  wifi_error status = global_func_table_.wifi_start_rssi_monitoring(
      id,
      wlan_interface_handle_,
      max_rssi,
      min_rssi,
      {onAsyncRssiThresholdBreached});
  if (status != WIFI_SUCCESS) {
    on_rssi_threshold_breached_internal_callback = nullptr;
  }
  return status;
}

wifi_error WifiLegacyHal::stopRssiMonitoring(wifi_request_id id) {
  if (!on_rssi_threshold_breached_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  wifi_error status =
      global_func_table_.wifi_stop_rssi_monitoring(id, wlan_interface_handle_);
  // If the request Id is wrong, don't stop the ongoing rssi monitoring. Any
  // other error should be treated as the end of background scan.
  if (status != WIFI_ERROR_INVALID_REQUEST_ID) {
    on_rssi_threshold_breached_internal_callback = nullptr;
  }
  return status;
}

std::pair<wifi_error, wifi_roaming_capabilities>
WifiLegacyHal::getRoamingCapabilities() {
  wifi_roaming_capabilities caps;
  wifi_error status = global_func_table_.wifi_get_roaming_capabilities(
      wlan_interface_handle_, &caps);
  return {status, caps};
}

wifi_error WifiLegacyHal::configureRoaming(const wifi_roaming_config& config) {
  wifi_roaming_config config_internal = config;
  return global_func_table_.wifi_configure_roaming(wlan_interface_handle_,
                                                   &config_internal);
}

wifi_error WifiLegacyHal::enableFirmwareRoaming(fw_roaming_state_t state) {
  return global_func_table_.wifi_enable_firmware_roaming(wlan_interface_handle_,
                                                         state);
}

wifi_error WifiLegacyHal::configureNdOffload(bool enable) {
  return global_func_table_.wifi_configure_nd_offload(wlan_interface_handle_,
                                                      enable);
}

wifi_error WifiLegacyHal::startSendingOffloadedPacket(
    uint32_t cmd_id,
    const std::vector<uint8_t>& ip_packet_data,
    const std::array<uint8_t, 6>& src_address,
    const std::array<uint8_t, 6>& dst_address,
    uint32_t period_in_ms) {
  std::vector<uint8_t> ip_packet_data_internal(ip_packet_data);
  std::vector<uint8_t> src_address_internal(
      src_address.data(), src_address.data() + src_address.size());
  std::vector<uint8_t> dst_address_internal(
      dst_address.data(), dst_address.data() + dst_address.size());
  return global_func_table_.wifi_start_sending_offloaded_packet(
      cmd_id,
      wlan_interface_handle_,
      ip_packet_data_internal.data(),
      ip_packet_data_internal.size(),
      src_address_internal.data(),
      dst_address_internal.data(),
      period_in_ms);
}

wifi_error WifiLegacyHal::stopSendingOffloadedPacket(uint32_t cmd_id) {
  return global_func_table_.wifi_stop_sending_offloaded_packet(
      cmd_id, wlan_interface_handle_);
}

wifi_error WifiLegacyHal::setScanningMacOui(const std::array<uint8_t, 3>& oui) {
  std::vector<uint8_t> oui_internal(oui.data(), oui.data() + oui.size());
  return global_func_table_.wifi_set_scanning_mac_oui(wlan_interface_handle_,
                                                      oui_internal.data());
}

wifi_error WifiLegacyHal::selectTxPowerScenario(wifi_power_scenario scenario) {
  return global_func_table_.wifi_select_tx_power_scenario(
      wlan_interface_handle_, scenario);
}

wifi_error WifiLegacyHal::resetTxPowerScenario() {
  return global_func_table_.wifi_reset_tx_power_scenario(wlan_interface_handle_);
}

std::pair<wifi_error, uint32_t> WifiLegacyHal::getLoggerSupportedFeatureSet() {
  uint32_t supported_features;
  wifi_error status = global_func_table_.wifi_get_logger_supported_feature_set(
      wlan_interface_handle_, &supported_features);
  return {status, supported_features};
}

wifi_error WifiLegacyHal::startPktFateMonitoring() {
  return global_func_table_.wifi_start_pkt_fate_monitoring(
      wlan_interface_handle_);
}

std::pair<wifi_error, std::vector<wifi_tx_report>>
WifiLegacyHal::getTxPktFates() {
  std::vector<wifi_tx_report> tx_pkt_fates;
  tx_pkt_fates.resize(MAX_FATE_LOG_LEN);
  size_t num_fates = 0;
  wifi_error status =
      global_func_table_.wifi_get_tx_pkt_fates(wlan_interface_handle_,
                                               tx_pkt_fates.data(),
                                               tx_pkt_fates.size(),
                                               &num_fates);
  CHECK(num_fates <= MAX_FATE_LOG_LEN);
  tx_pkt_fates.resize(num_fates);
  return {status, std::move(tx_pkt_fates)};
}

std::pair<wifi_error, std::vector<wifi_rx_report>>
WifiLegacyHal::getRxPktFates() {
  std::vector<wifi_rx_report> rx_pkt_fates;
  rx_pkt_fates.resize(MAX_FATE_LOG_LEN);
  size_t num_fates = 0;
  wifi_error status =
      global_func_table_.wifi_get_rx_pkt_fates(wlan_interface_handle_,
                                               rx_pkt_fates.data(),
                                               rx_pkt_fates.size(),
                                               &num_fates);
  CHECK(num_fates <= MAX_FATE_LOG_LEN);
  rx_pkt_fates.resize(num_fates);
  return {status, std::move(rx_pkt_fates)};
}

std::pair<wifi_error, WakeReasonStats> WifiLegacyHal::getWakeReasonStats() {
  WakeReasonStats stats;
  stats.cmd_event_wake_cnt.resize(kMaxWakeReasonStatsArraySize);
  stats.driver_fw_local_wake_cnt.resize(kMaxWakeReasonStatsArraySize);

  // This legacy struct needs separate memory to store the variable sized wake
  // reason types.
  stats.wake_reason_cnt.cmd_event_wake_cnt =
      reinterpret_cast<int32_t*>(stats.cmd_event_wake_cnt.data());
  stats.wake_reason_cnt.cmd_event_wake_cnt_sz = stats.cmd_event_wake_cnt.size();
  stats.wake_reason_cnt.cmd_event_wake_cnt_used = 0;
  stats.wake_reason_cnt.driver_fw_local_wake_cnt =
      reinterpret_cast<int32_t*>(stats.driver_fw_local_wake_cnt.data());
  stats.wake_reason_cnt.driver_fw_local_wake_cnt_sz =
      stats.driver_fw_local_wake_cnt.size();
  stats.wake_reason_cnt.driver_fw_local_wake_cnt_used = 0;

  wifi_error status = global_func_table_.wifi_get_wake_reason_stats(
      wlan_interface_handle_, &stats.wake_reason_cnt);

  CHECK(stats.wake_reason_cnt.cmd_event_wake_cnt_used >= 0 &&
        static_cast<uint32_t>(stats.wake_reason_cnt.cmd_event_wake_cnt_used) <=
            kMaxWakeReasonStatsArraySize);
  stats.cmd_event_wake_cnt.resize(
      stats.wake_reason_cnt.cmd_event_wake_cnt_used);
  stats.wake_reason_cnt.cmd_event_wake_cnt = nullptr;

  CHECK(stats.wake_reason_cnt.driver_fw_local_wake_cnt_used >= 0 &&
        static_cast<uint32_t>(
            stats.wake_reason_cnt.driver_fw_local_wake_cnt_used) <=
            kMaxWakeReasonStatsArraySize);
  stats.driver_fw_local_wake_cnt.resize(
      stats.wake_reason_cnt.driver_fw_local_wake_cnt_used);
  stats.wake_reason_cnt.driver_fw_local_wake_cnt = nullptr;

  return {status, stats};
}

wifi_error WifiLegacyHal::registerRingBufferCallbackHandler(
    const on_ring_buffer_data_callback& on_user_data_callback) {
  if (on_ring_buffer_data_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  on_ring_buffer_data_internal_callback = [on_user_data_callback](
      char* ring_name,
      char* buffer,
      int buffer_size,
      wifi_ring_buffer_status* status) {
    if (status && buffer) {
      std::vector<uint8_t> buffer_vector(
          reinterpret_cast<uint8_t*>(buffer),
          reinterpret_cast<uint8_t*>(buffer) + buffer_size);
      on_user_data_callback(ring_name, buffer_vector, *status);
    }
  };
  wifi_error status = global_func_table_.wifi_set_log_handler(
      0, wlan_interface_handle_, {onAsyncRingBufferData});
  if (status != WIFI_SUCCESS) {
    on_ring_buffer_data_internal_callback = nullptr;
  }
  return status;
}

wifi_error WifiLegacyHal::deregisterRingBufferCallbackHandler() {
  if (!on_ring_buffer_data_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  on_ring_buffer_data_internal_callback = nullptr;
  return global_func_table_.wifi_reset_log_handler(0, wlan_interface_handle_);
}

std::pair<wifi_error, std::vector<wifi_ring_buffer_status>>
WifiLegacyHal::getRingBuffersStatus() {
  std::vector<wifi_ring_buffer_status> ring_buffers_status;
  ring_buffers_status.resize(kMaxRingBuffers);
  uint32_t num_rings = kMaxRingBuffers;
  wifi_error status = global_func_table_.wifi_get_ring_buffers_status(
      wlan_interface_handle_, &num_rings, ring_buffers_status.data());
  CHECK(num_rings <= kMaxRingBuffers);
  ring_buffers_status.resize(num_rings);
  return {status, std::move(ring_buffers_status)};
}

wifi_error WifiLegacyHal::startRingBufferLogging(const std::string& ring_name,
                                                 uint32_t verbose_level,
                                                 uint32_t max_interval_sec,
                                                 uint32_t min_data_size) {
  return global_func_table_.wifi_start_logging(wlan_interface_handle_,
                                               verbose_level,
                                               0,
                                               max_interval_sec,
                                               min_data_size,
                                               makeCharVec(ring_name).data());
}

wifi_error WifiLegacyHal::getRingBufferData(const std::string& ring_name) {
  return global_func_table_.wifi_get_ring_data(wlan_interface_handle_,
                                               makeCharVec(ring_name).data());
}

wifi_error WifiLegacyHal::registerErrorAlertCallbackHandler(
    const on_error_alert_callback& on_user_alert_callback) {
  if (on_error_alert_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  on_error_alert_internal_callback = [on_user_alert_callback](
      wifi_request_id id, char* buffer, int buffer_size, int err_code) {
    if (buffer) {
      CHECK(id == 0);
      on_user_alert_callback(
          err_code,
          std::vector<uint8_t>(
              reinterpret_cast<uint8_t*>(buffer),
              reinterpret_cast<uint8_t*>(buffer) + buffer_size));
    }
  };
  wifi_error status = global_func_table_.wifi_set_alert_handler(
      0, wlan_interface_handle_, {onAsyncErrorAlert});
  if (status != WIFI_SUCCESS) {
    on_error_alert_internal_callback = nullptr;
  }
  return status;
}

wifi_error WifiLegacyHal::deregisterErrorAlertCallbackHandler() {
  if (!on_error_alert_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  on_error_alert_internal_callback = nullptr;
  return global_func_table_.wifi_reset_alert_handler(0, wlan_interface_handle_);
}

wifi_error WifiLegacyHal::startRttRangeRequest(
    wifi_request_id id,
    const std::vector<wifi_rtt_config>& rtt_configs,
    const on_rtt_results_callback& on_results_user_callback) {
  if (on_rtt_results_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }

  on_rtt_results_internal_callback = [on_results_user_callback](
      wifi_request_id id,
      unsigned num_results,
      wifi_rtt_result* rtt_results[]) {
    if (num_results > 0 && !rtt_results) {
      LOG(ERROR) << "Unexpected nullptr in RTT results";
      return;
    }
    std::vector<const wifi_rtt_result*> rtt_results_vec;
    std::copy_if(
        rtt_results,
        rtt_results + num_results,
        back_inserter(rtt_results_vec),
        [](wifi_rtt_result* rtt_result) { return rtt_result != nullptr; });
    on_results_user_callback(id, rtt_results_vec);
  };

  std::vector<wifi_rtt_config> rtt_configs_internal(rtt_configs);
  wifi_error status =
      global_func_table_.wifi_rtt_range_request(id,
                                                wlan_interface_handle_,
                                                rtt_configs.size(),
                                                rtt_configs_internal.data(),
                                                {onAsyncRttResults});
  if (status != WIFI_SUCCESS) {
    on_rtt_results_internal_callback = nullptr;
  }
  return status;
}

wifi_error WifiLegacyHal::cancelRttRangeRequest(
    wifi_request_id id, const std::vector<std::array<uint8_t, 6>>& mac_addrs) {
  if (!on_rtt_results_internal_callback) {
    return WIFI_ERROR_NOT_AVAILABLE;
  }
  static_assert(sizeof(mac_addr) == sizeof(std::array<uint8_t, 6>),
                "MAC address size mismatch");
  // TODO: How do we handle partial cancels (i.e only a subset of enabled mac
  // addressed are cancelled).
  std::vector<std::array<uint8_t, 6>> mac_addrs_internal(mac_addrs);
  wifi_error status = global_func_table_.wifi_rtt_range_cancel(
      id,
      wlan_interface_handle_,
      mac_addrs.size(),
      reinterpret_cast<mac_addr*>(mac_addrs_internal.data()));
  // If the request Id is wrong, don't stop the ongoing range request. Any
  // other error should be treated as the end of rtt ranging.
  if (status != WIFI_ERROR_INVALID_REQUEST_ID) {
    on_rtt_results_internal_callback = nullptr;
  }
  return status;
}

std::pair<wifi_error, wifi_rtt_capabilities>
WifiLegacyHal::getRttCapabilities() {
  wifi_rtt_capabilities rtt_caps;
  wifi_error status = global_func_table_.wifi_get_rtt_capabilities(
      wlan_interface_handle_, &rtt_caps);
  return {status, rtt_caps};
}

std::pair<wifi_error, wifi_rtt_responder> WifiLegacyHal::getRttResponderInfo() {
  wifi_rtt_responder rtt_responder;
  wifi_error status = global_func_table_.wifi_rtt_get_responder_info(
      wlan_interface_handle_, &rtt_responder);
  return {status, rtt_responder};
}

wifi_error WifiLegacyHal::enableRttResponder(
    wifi_request_id id,
    const wifi_channel_info& channel_hint,
    uint32_t max_duration_secs,
    const wifi_rtt_responder& info) {
  wifi_rtt_responder info_internal(info);
  return global_func_table_.wifi_enable_responder(id,
                                                  wlan_interface_handle_,
                                                  channel_hint,
                                                  max_duration_secs,
                                                  &info_internal);
}

wifi_error WifiLegacyHal::disableRttResponder(wifi_request_id id) {
  return global_func_table_.wifi_disable_responder(id, wlan_interface_handle_);
}

wifi_error WifiLegacyHal::setRttLci(wifi_request_id id,
                                    const wifi_lci_information& info) {
  wifi_lci_information info_internal(info);
  return global_func_table_.wifi_set_lci(
      id, wlan_interface_handle_, &info_internal);
}

wifi_error WifiLegacyHal::setRttLcr(wifi_request_id id,
                                    const wifi_lcr_information& info) {
  wifi_lcr_information info_internal(info);
  return global_func_table_.wifi_set_lcr(
      id, wlan_interface_handle_, &info_internal);
}

wifi_error WifiLegacyHal::nanRegisterCallbackHandlers(
    const NanCallbackHandlers& user_callbacks) {
  on_nan_notify_response_user_callback = user_callbacks.on_notify_response;
  on_nan_event_publish_terminated_user_callback =
      user_callbacks.on_event_publish_terminated;
  on_nan_event_match_user_callback = user_callbacks.on_event_match;
  on_nan_event_match_expired_user_callback =
      user_callbacks.on_event_match_expired;
  on_nan_event_subscribe_terminated_user_callback =
      user_callbacks.on_event_subscribe_terminated;
  on_nan_event_followup_user_callback = user_callbacks.on_event_followup;
  on_nan_event_disc_eng_event_user_callback =
      user_callbacks.on_event_disc_eng_event;
  on_nan_event_disabled_user_callback = user_callbacks.on_event_disabled;
  on_nan_event_tca_user_callback = user_callbacks.on_event_tca;
  on_nan_event_beacon_sdf_payload_user_callback =
      user_callbacks.on_event_beacon_sdf_payload;
  on_nan_event_data_path_request_user_callback =
      user_callbacks.on_event_data_path_request;
  on_nan_event_data_path_confirm_user_callback =
      user_callbacks.on_event_data_path_confirm;
  on_nan_event_data_path_end_user_callback =
      user_callbacks.on_event_data_path_end;
  on_nan_event_transmit_follow_up_user_callback =
      user_callbacks.on_event_transmit_follow_up;
  on_nan_event_range_request_user_callback =
      user_callbacks.on_event_range_request;
  on_nan_event_range_report_user_callback =
      user_callbacks.on_event_range_report;

  return global_func_table_.wifi_nan_register_handler(
      wlan_interface_handle_,
      {onAysncNanNotifyResponse,
       onAysncNanEventPublishReplied,
       onAysncNanEventPublishTerminated,
       onAysncNanEventMatch,
       onAysncNanEventMatchExpired,
       onAysncNanEventSubscribeTerminated,
       onAysncNanEventFollowup,
       onAysncNanEventDiscEngEvent,
       onAysncNanEventDisabled,
       onAysncNanEventTca,
       onAysncNanEventBeaconSdfPayload,
       onAysncNanEventDataPathRequest,
       onAysncNanEventDataPathConfirm,
       onAysncNanEventDataPathEnd,
       onAysncNanEventTransmitFollowUp,
       onAysncNanEventRangeRequest,
       onAysncNanEventRangeReport});
}

wifi_error WifiLegacyHal::nanEnableRequest(transaction_id id,
                                           const NanEnableRequest& msg) {
  NanEnableRequest msg_internal(msg);
  return global_func_table_.wifi_nan_enable_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanDisableRequest(transaction_id id) {
  return global_func_table_.wifi_nan_disable_request(id,
                                                     wlan_interface_handle_);
}

wifi_error WifiLegacyHal::nanPublishRequest(transaction_id id,
                                            const NanPublishRequest& msg) {
  NanPublishRequest msg_internal(msg);
  return global_func_table_.wifi_nan_publish_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanPublishCancelRequest(
    transaction_id id, const NanPublishCancelRequest& msg) {
  NanPublishCancelRequest msg_internal(msg);
  return global_func_table_.wifi_nan_publish_cancel_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanSubscribeRequest(transaction_id id,
                                              const NanSubscribeRequest& msg) {
  NanSubscribeRequest msg_internal(msg);
  return global_func_table_.wifi_nan_subscribe_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanSubscribeCancelRequest(
    transaction_id id, const NanSubscribeCancelRequest& msg) {
  NanSubscribeCancelRequest msg_internal(msg);
  return global_func_table_.wifi_nan_subscribe_cancel_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanTransmitFollowupRequest(
    transaction_id id, const NanTransmitFollowupRequest& msg) {
  NanTransmitFollowupRequest msg_internal(msg);
  return global_func_table_.wifi_nan_transmit_followup_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanStatsRequest(transaction_id id,
                                          const NanStatsRequest& msg) {
  NanStatsRequest msg_internal(msg);
  return global_func_table_.wifi_nan_stats_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanConfigRequest(transaction_id id,
                                           const NanConfigRequest& msg) {
  NanConfigRequest msg_internal(msg);
  return global_func_table_.wifi_nan_config_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanTcaRequest(transaction_id id,
                                        const NanTCARequest& msg) {
  NanTCARequest msg_internal(msg);
  return global_func_table_.wifi_nan_tca_request(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanBeaconSdfPayloadRequest(
    transaction_id id, const NanBeaconSdfPayloadRequest& msg) {
  NanBeaconSdfPayloadRequest msg_internal(msg);
  return global_func_table_.wifi_nan_beacon_sdf_payload_request(
      id, wlan_interface_handle_, &msg_internal);
}

std::pair<wifi_error, NanVersion> WifiLegacyHal::nanGetVersion() {
  NanVersion version;
  wifi_error status =
      global_func_table_.wifi_nan_get_version(global_handle_, &version);
  return {status, version};
}

wifi_error WifiLegacyHal::nanGetCapabilities(transaction_id id) {
  return global_func_table_.wifi_nan_get_capabilities(id,
                                                      wlan_interface_handle_);
}

wifi_error WifiLegacyHal::nanDataInterfaceCreate(
    transaction_id id, const std::string& iface_name) {
  return global_func_table_.wifi_nan_data_interface_create(
      id, wlan_interface_handle_, makeCharVec(iface_name).data());
}

wifi_error WifiLegacyHal::nanDataInterfaceDelete(
    transaction_id id, const std::string& iface_name) {
  return global_func_table_.wifi_nan_data_interface_delete(
      id, wlan_interface_handle_, makeCharVec(iface_name).data());
}

wifi_error WifiLegacyHal::nanDataRequestInitiator(
    transaction_id id, const NanDataPathInitiatorRequest& msg) {
  NanDataPathInitiatorRequest msg_internal(msg);
  return global_func_table_.wifi_nan_data_request_initiator(
      id, wlan_interface_handle_, &msg_internal);
}

wifi_error WifiLegacyHal::nanDataIndicationResponse(
    transaction_id id, const NanDataPathIndicationResponse& msg) {
  NanDataPathIndicationResponse msg_internal(msg);
  return global_func_table_.wifi_nan_data_indication_response(
      id, wlan_interface_handle_, &msg_internal);
}

typedef struct {
    u8 num_ndp_instances;
    NanDataPathId ndp_instance_id;
} NanDataPathEndSingleNdpIdRequest;

wifi_error WifiLegacyHal::nanDataEnd(transaction_id id,
                                     uint32_t ndpInstanceId) {
  NanDataPathEndSingleNdpIdRequest msg;
  msg.num_ndp_instances = 1;
  msg.ndp_instance_id = ndpInstanceId;
  wifi_error status = global_func_table_.wifi_nan_data_end(
      id, wlan_interface_handle_, (NanDataPathEndRequest*)&msg);
  return status;
}

wifi_error WifiLegacyHal::setCountryCode(std::array<int8_t, 2> code) {
  std::string code_str(code.data(), code.data() + code.size());
  return global_func_table_.wifi_set_country_code(wlan_interface_handle_,
                                                  code_str.c_str());
}

wifi_error WifiLegacyHal::retrieveWlanInterfaceHandle() {
  const std::string& ifname_to_find = getStaIfaceName();
  wifi_interface_handle* iface_handles = nullptr;
  int num_iface_handles = 0;
  wifi_error status = global_func_table_.wifi_get_ifaces(
      global_handle_, &num_iface_handles, &iface_handles);
  if (status != WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to enumerate interface handles";
    return status;
  }
  for (int i = 0; i < num_iface_handles; ++i) {
    std::array<char, IFNAMSIZ> current_ifname;
    current_ifname.fill(0);
    status = global_func_table_.wifi_get_iface_name(
        iface_handles[i], current_ifname.data(), current_ifname.size());
    if (status != WIFI_SUCCESS) {
      LOG(WARNING) << "Failed to get interface handle name";
      continue;
    }
    if (ifname_to_find == current_ifname.data()) {
      wlan_interface_handle_ = iface_handles[i];
      return WIFI_SUCCESS;
    }
  }
  return WIFI_ERROR_UNKNOWN;
}

void WifiLegacyHal::runEventLoop() {
  LOG(DEBUG) << "Starting legacy HAL event loop";
  global_func_table_.wifi_event_loop(global_handle_);
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (!awaiting_event_loop_termination_) {
    LOG(FATAL) << "Legacy HAL event loop terminated, but HAL was not stopping";
  }
  LOG(DEBUG) << "Legacy HAL event loop terminated";
  awaiting_event_loop_termination_ = false;
  stop_wait_cv_.notify_one();
}

std::pair<wifi_error, std::vector<wifi_cached_scan_results>>
WifiLegacyHal::getGscanCachedResults() {
  std::vector<wifi_cached_scan_results> cached_scan_results;
  cached_scan_results.resize(kMaxCachedGscanResults);
  int32_t num_results = 0;
  wifi_error status = global_func_table_.wifi_get_cached_gscan_results(
      wlan_interface_handle_,
      true /* always flush */,
      cached_scan_results.size(),
      cached_scan_results.data(),
      &num_results);
  CHECK(num_results >= 0 &&
        static_cast<uint32_t>(num_results) <= kMaxCachedGscanResults);
  cached_scan_results.resize(num_results);
  // Check for invalid IE lengths in these cached scan results and correct it.
  for (auto& cached_scan_result : cached_scan_results) {
    int num_scan_results = cached_scan_result.num_results;
    for (int i = 0; i < num_scan_results; i++) {
      auto& scan_result = cached_scan_result.results[i];
      if (scan_result.ie_length > 0) {
        LOG(DEBUG) << "Cached scan result has non-zero IE length "
                   << scan_result.ie_length;
        scan_result.ie_length = 0;
      }
    }
  }
  return {status, std::move(cached_scan_results)};
}

void WifiLegacyHal::invalidate() {
  global_handle_ = nullptr;
  wlan_interface_handle_ = nullptr;
  on_driver_memory_dump_internal_callback = nullptr;
  on_firmware_memory_dump_internal_callback = nullptr;
  on_gscan_event_internal_callback = nullptr;
  on_gscan_full_result_internal_callback = nullptr;
  on_link_layer_stats_result_internal_callback = nullptr;
  on_rssi_threshold_breached_internal_callback = nullptr;
  on_ring_buffer_data_internal_callback = nullptr;
  on_error_alert_internal_callback = nullptr;
  on_rtt_results_internal_callback = nullptr;
  on_nan_notify_response_user_callback = nullptr;
  on_nan_event_publish_terminated_user_callback = nullptr;
  on_nan_event_match_user_callback = nullptr;
  on_nan_event_match_expired_user_callback = nullptr;
  on_nan_event_subscribe_terminated_user_callback = nullptr;
  on_nan_event_followup_user_callback = nullptr;
  on_nan_event_disc_eng_event_user_callback = nullptr;
  on_nan_event_disabled_user_callback = nullptr;
  on_nan_event_tca_user_callback = nullptr;
  on_nan_event_beacon_sdf_payload_user_callback = nullptr;
  on_nan_event_data_path_request_user_callback = nullptr;
  on_nan_event_data_path_confirm_user_callback = nullptr;
  on_nan_event_data_path_end_user_callback = nullptr;
  on_nan_event_transmit_follow_up_user_callback = nullptr;
  on_nan_event_range_request_user_callback = nullptr;
  on_nan_event_range_report_user_callback = nullptr;
}

}  // namespace legacy_hal
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
