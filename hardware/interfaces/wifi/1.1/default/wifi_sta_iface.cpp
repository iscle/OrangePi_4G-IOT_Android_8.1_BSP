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

#include <android-base/logging.h>

#include "hidl_return_util.h"
#include "hidl_struct_util.h"
#include "wifi_sta_iface.h"
#include "wifi_status_util.h"

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
using hidl_return_util::validateAndCall;

WifiStaIface::WifiStaIface(
    const std::string& ifname,
    const std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal)
    : ifname_(ifname), legacy_hal_(legacy_hal), is_valid_(true) {
  // Turn on DFS channel usage for STA iface.
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->setDfsFlag(true);
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to set DFS flag; DFS channels may be unavailable.";
  }
}

void WifiStaIface::invalidate() {
  legacy_hal_.reset();
  event_cb_handler_.invalidate();
  is_valid_ = false;
}

bool WifiStaIface::isValid() {
  return is_valid_;
}

std::set<sp<IWifiStaIfaceEventCallback>> WifiStaIface::getEventCallbacks() {
  return event_cb_handler_.getCallbacks();
}

Return<void> WifiStaIface::getName(getName_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getNameInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::getType(getType_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getTypeInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::registerEventCallback(
    const sp<IWifiStaIfaceEventCallback>& callback,
    registerEventCallback_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::registerEventCallbackInternal,
                         hidl_status_cb,
                         callback);
}

Return<void> WifiStaIface::getCapabilities(getCapabilities_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getCapabilitiesInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::getApfPacketFilterCapabilities(
    getApfPacketFilterCapabilities_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getApfPacketFilterCapabilitiesInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::installApfPacketFilter(
    uint32_t cmd_id,
    const hidl_vec<uint8_t>& program,
    installApfPacketFilter_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::installApfPacketFilterInternal,
                         hidl_status_cb,
                         cmd_id,
                         program);
}

Return<void> WifiStaIface::getBackgroundScanCapabilities(
    getBackgroundScanCapabilities_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getBackgroundScanCapabilitiesInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::getValidFrequenciesForBand(
    WifiBand band, getValidFrequenciesForBand_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getValidFrequenciesForBandInternal,
                         hidl_status_cb,
                         band);
}

Return<void> WifiStaIface::startBackgroundScan(
    uint32_t cmd_id,
    const StaBackgroundScanParameters& params,
    startBackgroundScan_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::startBackgroundScanInternal,
                         hidl_status_cb,
                         cmd_id,
                         params);
}

Return<void> WifiStaIface::stopBackgroundScan(
    uint32_t cmd_id, stopBackgroundScan_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::stopBackgroundScanInternal,
                         hidl_status_cb,
                         cmd_id);
}

Return<void> WifiStaIface::enableLinkLayerStatsCollection(
    bool debug, enableLinkLayerStatsCollection_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::enableLinkLayerStatsCollectionInternal,
                         hidl_status_cb,
                         debug);
}

Return<void> WifiStaIface::disableLinkLayerStatsCollection(
    disableLinkLayerStatsCollection_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::disableLinkLayerStatsCollectionInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::getLinkLayerStats(
    getLinkLayerStats_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getLinkLayerStatsInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::startRssiMonitoring(
    uint32_t cmd_id,
    int32_t max_rssi,
    int32_t min_rssi,
    startRssiMonitoring_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::startRssiMonitoringInternal,
                         hidl_status_cb,
                         cmd_id,
                         max_rssi,
                         min_rssi);
}

Return<void> WifiStaIface::stopRssiMonitoring(
    uint32_t cmd_id, stopRssiMonitoring_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::stopRssiMonitoringInternal,
                         hidl_status_cb,
                         cmd_id);
}

Return<void> WifiStaIface::getRoamingCapabilities(
    getRoamingCapabilities_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getRoamingCapabilitiesInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::configureRoaming(
    const StaRoamingConfig& config, configureRoaming_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::configureRoamingInternal,
                         hidl_status_cb,
                         config);
}

Return<void> WifiStaIface::setRoamingState(StaRoamingState state,
                                           setRoamingState_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::setRoamingStateInternal,
                         hidl_status_cb,
                         state);
}

Return<void> WifiStaIface::enableNdOffload(bool enable,
                                           enableNdOffload_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::enableNdOffloadInternal,
                         hidl_status_cb,
                         enable);
}

Return<void> WifiStaIface::startSendingKeepAlivePackets(
    uint32_t cmd_id,
    const hidl_vec<uint8_t>& ip_packet_data,
    uint16_t ether_type,
    const hidl_array<uint8_t, 6>& src_address,
    const hidl_array<uint8_t, 6>& dst_address,
    uint32_t period_in_ms,
    startSendingKeepAlivePackets_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::startSendingKeepAlivePacketsInternal,
                         hidl_status_cb,
                         cmd_id,
                         ip_packet_data,
                         ether_type,
                         src_address,
                         dst_address,
                         period_in_ms);
}

Return<void> WifiStaIface::stopSendingKeepAlivePackets(
    uint32_t cmd_id, stopSendingKeepAlivePackets_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::stopSendingKeepAlivePacketsInternal,
                         hidl_status_cb,
                         cmd_id);
}

Return<void> WifiStaIface::setScanningMacOui(
    const hidl_array<uint8_t, 3>& oui, setScanningMacOui_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::setScanningMacOuiInternal,
                         hidl_status_cb,
                         oui);
}

Return<void> WifiStaIface::startDebugPacketFateMonitoring(
    startDebugPacketFateMonitoring_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::startDebugPacketFateMonitoringInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::getDebugTxPacketFates(
    getDebugTxPacketFates_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getDebugTxPacketFatesInternal,
                         hidl_status_cb);
}

Return<void> WifiStaIface::getDebugRxPacketFates(
    getDebugRxPacketFates_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
                         &WifiStaIface::getDebugRxPacketFatesInternal,
                         hidl_status_cb);
}

std::pair<WifiStatus, std::string> WifiStaIface::getNameInternal() {
  return {createWifiStatus(WifiStatusCode::SUCCESS), ifname_};
}

std::pair<WifiStatus, IfaceType> WifiStaIface::getTypeInternal() {
  return {createWifiStatus(WifiStatusCode::SUCCESS), IfaceType::STA};
}

WifiStatus WifiStaIface::registerEventCallbackInternal(
    const sp<IWifiStaIfaceEventCallback>& callback) {
  if (!event_cb_handler_.addCallback(callback)) {
    return createWifiStatus(WifiStatusCode::ERROR_UNKNOWN);
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

std::pair<WifiStatus, uint32_t> WifiStaIface::getCapabilitiesInternal() {
  legacy_hal::wifi_error legacy_status;
  uint32_t legacy_feature_set;
  std::tie(legacy_status, legacy_feature_set) =
      legacy_hal_.lock()->getSupportedFeatureSet();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), 0};
  }
  uint32_t legacy_logger_feature_set;
  std::tie(legacy_status, legacy_logger_feature_set) =
      legacy_hal_.lock()->getLoggerSupportedFeatureSet();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    // some devices don't support querying logger feature set
    legacy_logger_feature_set = 0;
  }
  uint32_t hidl_caps;
  if (!hidl_struct_util::convertLegacyFeaturesToHidlStaCapabilities(
          legacy_feature_set, legacy_logger_feature_set, &hidl_caps)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), 0};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_caps};
}

std::pair<WifiStatus, StaApfPacketFilterCapabilities>
WifiStaIface::getApfPacketFilterCapabilitiesInternal() {
  legacy_hal::wifi_error legacy_status;
  legacy_hal::PacketFilterCapabilities legacy_caps;
  std::tie(legacy_status, legacy_caps) =
      legacy_hal_.lock()->getPacketFilterCapabilities();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  StaApfPacketFilterCapabilities hidl_caps;
  if (!hidl_struct_util::convertLegacyApfCapabilitiesToHidl(legacy_caps,
                                                            &hidl_caps)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_caps};
}

WifiStatus WifiStaIface::installApfPacketFilterInternal(
    uint32_t /* cmd_id */, const std::vector<uint8_t>& program) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->setPacketFilter(program);
  return createWifiStatusFromLegacyError(legacy_status);
}

std::pair<WifiStatus, StaBackgroundScanCapabilities>
WifiStaIface::getBackgroundScanCapabilitiesInternal() {
  legacy_hal::wifi_error legacy_status;
  legacy_hal::wifi_gscan_capabilities legacy_caps;
  std::tie(legacy_status, legacy_caps) =
      legacy_hal_.lock()->getGscanCapabilities();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  StaBackgroundScanCapabilities hidl_caps;
  if (!hidl_struct_util::convertLegacyGscanCapabilitiesToHidl(legacy_caps,
                                                              &hidl_caps)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_caps};
}

std::pair<WifiStatus, std::vector<WifiChannelInMhz>>
WifiStaIface::getValidFrequenciesForBandInternal(WifiBand band) {
  static_assert(sizeof(WifiChannelInMhz) == sizeof(uint32_t), "Size mismatch");
  legacy_hal::wifi_error legacy_status;
  std::vector<uint32_t> valid_frequencies;
  std::tie(legacy_status, valid_frequencies) =
      legacy_hal_.lock()->getValidFrequenciesForBand(
          hidl_struct_util::convertHidlWifiBandToLegacy(band));
  return {createWifiStatusFromLegacyError(legacy_status), valid_frequencies};
}

WifiStatus WifiStaIface::startBackgroundScanInternal(
    uint32_t cmd_id, const StaBackgroundScanParameters& params) {
  legacy_hal::wifi_scan_cmd_params legacy_params;
  if (!hidl_struct_util::convertHidlGscanParamsToLegacy(params,
                                                        &legacy_params)) {
    return createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS);
  }
  android::wp<WifiStaIface> weak_ptr_this(this);
  const auto& on_failure_callback =
      [weak_ptr_this](legacy_hal::wifi_request_id id) {
        const auto shared_ptr_this = weak_ptr_this.promote();
        if (!shared_ptr_this.get() || !shared_ptr_this->isValid()) {
          LOG(ERROR) << "Callback invoked on an invalid object";
          return;
        }
        for (const auto& callback : shared_ptr_this->getEventCallbacks()) {
          if (!callback->onBackgroundScanFailure(id).isOk()) {
            LOG(ERROR) << "Failed to invoke onBackgroundScanFailure callback";
          }
        }
      };
  const auto& on_results_callback = [weak_ptr_this](
      legacy_hal::wifi_request_id id,
      const std::vector<legacy_hal::wifi_cached_scan_results>& results) {
    const auto shared_ptr_this = weak_ptr_this.promote();
    if (!shared_ptr_this.get() || !shared_ptr_this->isValid()) {
      LOG(ERROR) << "Callback invoked on an invalid object";
      return;
    }
    std::vector<StaScanData> hidl_scan_datas;
    if (!hidl_struct_util::convertLegacyVectorOfCachedGscanResultsToHidl(
            results, &hidl_scan_datas)) {
      LOG(ERROR) << "Failed to convert scan results to HIDL structs";
      return;
    }
    for (const auto& callback : shared_ptr_this->getEventCallbacks()) {
      if (!callback->onBackgroundScanResults(id, hidl_scan_datas).isOk()) {
        LOG(ERROR) << "Failed to invoke onBackgroundScanResults callback";
      }
    }
  };
  const auto& on_full_result_callback = [weak_ptr_this](
      legacy_hal::wifi_request_id id,
      const legacy_hal::wifi_scan_result* result,
      uint32_t buckets_scanned) {
    const auto shared_ptr_this = weak_ptr_this.promote();
    if (!shared_ptr_this.get() || !shared_ptr_this->isValid()) {
      LOG(ERROR) << "Callback invoked on an invalid object";
      return;
    }
    StaScanResult hidl_scan_result;
    if (!hidl_struct_util::convertLegacyGscanResultToHidl(
            *result, true, &hidl_scan_result)) {
      LOG(ERROR) << "Failed to convert full scan results to HIDL structs";
      return;
    }
    for (const auto& callback : shared_ptr_this->getEventCallbacks()) {
      if (!callback->onBackgroundFullScanResult(
              id, buckets_scanned, hidl_scan_result).isOk()) {
        LOG(ERROR) << "Failed to invoke onBackgroundFullScanResult callback";
      }
    }
  };
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->startGscan(cmd_id,
                                     legacy_params,
                                     on_failure_callback,
                                     on_results_callback,
                                     on_full_result_callback);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::stopBackgroundScanInternal(uint32_t cmd_id) {
  legacy_hal::wifi_error legacy_status = legacy_hal_.lock()->stopGscan(cmd_id);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::enableLinkLayerStatsCollectionInternal(bool debug) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->enableLinkLayerStats(debug);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::disableLinkLayerStatsCollectionInternal() {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->disableLinkLayerStats();
  return createWifiStatusFromLegacyError(legacy_status);
}

std::pair<WifiStatus, StaLinkLayerStats>
WifiStaIface::getLinkLayerStatsInternal() {
  legacy_hal::wifi_error legacy_status;
  legacy_hal::LinkLayerStats legacy_stats;
  std::tie(legacy_status, legacy_stats) =
      legacy_hal_.lock()->getLinkLayerStats();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  StaLinkLayerStats hidl_stats;
  if (!hidl_struct_util::convertLegacyLinkLayerStatsToHidl(legacy_stats,
                                                           &hidl_stats)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_stats};
}

WifiStatus WifiStaIface::startRssiMonitoringInternal(uint32_t cmd_id,
                                                     int32_t max_rssi,
                                                     int32_t min_rssi) {
  android::wp<WifiStaIface> weak_ptr_this(this);
  const auto& on_threshold_breached_callback = [weak_ptr_this](
      legacy_hal::wifi_request_id id,
      std::array<uint8_t, 6> bssid,
      int8_t rssi) {
    const auto shared_ptr_this = weak_ptr_this.promote();
    if (!shared_ptr_this.get() || !shared_ptr_this->isValid()) {
      LOG(ERROR) << "Callback invoked on an invalid object";
      return;
    }
    for (const auto& callback : shared_ptr_this->getEventCallbacks()) {
      if (!callback->onRssiThresholdBreached(id, bssid, rssi).isOk()) {
        LOG(ERROR) << "Failed to invoke onRssiThresholdBreached callback";
      }
    }
  };
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->startRssiMonitoring(
          cmd_id, max_rssi, min_rssi, on_threshold_breached_callback);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::stopRssiMonitoringInternal(uint32_t cmd_id) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->stopRssiMonitoring(cmd_id);
  return createWifiStatusFromLegacyError(legacy_status);
}

std::pair<WifiStatus, StaRoamingCapabilities>
WifiStaIface::getRoamingCapabilitiesInternal() {
  legacy_hal::wifi_error legacy_status;
  legacy_hal::wifi_roaming_capabilities legacy_caps;
  std::tie(legacy_status, legacy_caps) =
      legacy_hal_.lock()->getRoamingCapabilities();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  StaRoamingCapabilities hidl_caps;
  if (!hidl_struct_util::convertLegacyRoamingCapabilitiesToHidl(legacy_caps,
                                                                &hidl_caps)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_caps};
}

WifiStatus WifiStaIface::configureRoamingInternal(
    const StaRoamingConfig& config) {
  legacy_hal::wifi_roaming_config legacy_config;
  if (!hidl_struct_util::convertHidlRoamingConfigToLegacy(config,
                                                          &legacy_config)) {
    return createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS);
  }
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->configureRoaming(legacy_config);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::setRoamingStateInternal(StaRoamingState state) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->enableFirmwareRoaming(
          hidl_struct_util::convertHidlRoamingStateToLegacy(state));
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::enableNdOffloadInternal(bool enable) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->configureNdOffload(enable);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::startSendingKeepAlivePacketsInternal(
    uint32_t cmd_id,
    const std::vector<uint8_t>& ip_packet_data,
    uint16_t /* ether_type */,
    const std::array<uint8_t, 6>& src_address,
    const std::array<uint8_t, 6>& dst_address,
    uint32_t period_in_ms) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->startSendingOffloadedPacket(
          cmd_id, ip_packet_data, src_address, dst_address, period_in_ms);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::stopSendingKeepAlivePacketsInternal(uint32_t cmd_id) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->stopSendingOffloadedPacket(cmd_id);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::setScanningMacOuiInternal(
    const std::array<uint8_t, 3>& oui) {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->setScanningMacOui(oui);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiStaIface::startDebugPacketFateMonitoringInternal() {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->startPktFateMonitoring();
  return createWifiStatusFromLegacyError(legacy_status);
}

std::pair<WifiStatus, std::vector<WifiDebugTxPacketFateReport>>
WifiStaIface::getDebugTxPacketFatesInternal() {
  legacy_hal::wifi_error legacy_status;
  std::vector<legacy_hal::wifi_tx_report> legacy_fates;
  std::tie(legacy_status, legacy_fates) = legacy_hal_.lock()->getTxPktFates();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  std::vector<WifiDebugTxPacketFateReport> hidl_fates;
  if (!hidl_struct_util::convertLegacyVectorOfDebugTxPacketFateToHidl(
          legacy_fates, &hidl_fates)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_fates};
}

std::pair<WifiStatus, std::vector<WifiDebugRxPacketFateReport>>
WifiStaIface::getDebugRxPacketFatesInternal() {
  legacy_hal::wifi_error legacy_status;
  std::vector<legacy_hal::wifi_rx_report> legacy_fates;
  std::tie(legacy_status, legacy_fates) = legacy_hal_.lock()->getRxPktFates();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  std::vector<WifiDebugRxPacketFateReport> hidl_fates;
  if (!hidl_struct_util::convertLegacyVectorOfDebugRxPacketFateToHidl(
          legacy_fates, &hidl_fates)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_fates};
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
