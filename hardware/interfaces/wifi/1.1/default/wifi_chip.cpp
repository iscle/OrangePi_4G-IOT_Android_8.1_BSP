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
#include "wifi_chip.h"
#include "wifi_feature_flags.h"
#include "wifi_status_util.h"

namespace {
using android::sp;
using android::hardware::hidl_vec;
using android::hardware::hidl_string;
using android::hardware::wifi::V1_0::ChipModeId;
using android::hardware::wifi::V1_0::IWifiChip;
using android::hardware::wifi::V1_0::IfaceType;

constexpr ChipModeId kStaChipModeId = 0;
constexpr ChipModeId kApChipModeId = 1;
constexpr ChipModeId kInvalidModeId = UINT32_MAX;

template <typename Iface>
void invalidateAndClear(sp<Iface>& iface) {
  if (iface.get()) {
    iface->invalidate();
    iface.clear();
  }
}
}  // namepsace

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
using hidl_return_util::validateAndCall;

WifiChip::WifiChip(
    ChipId chip_id,
    const std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal,
    const std::weak_ptr<mode_controller::WifiModeController> mode_controller)
    : chip_id_(chip_id),
      legacy_hal_(legacy_hal),
      mode_controller_(mode_controller),
      is_valid_(true),
      current_mode_id_(kInvalidModeId),
      debug_ring_buffer_cb_registered_(false) {}

void WifiChip::invalidate() {
  invalidateAndRemoveAllIfaces();
  legacy_hal_.reset();
  event_cb_handler_.invalidate();
  is_valid_ = false;
}

bool WifiChip::isValid() {
  return is_valid_;
}

std::set<sp<IWifiChipEventCallback>> WifiChip::getEventCallbacks() {
  return event_cb_handler_.getCallbacks();
}

Return<void> WifiChip::getId(getId_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getIdInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::registerEventCallback(
    const sp<IWifiChipEventCallback>& event_callback,
    registerEventCallback_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::registerEventCallbackInternal,
                         hidl_status_cb,
                         event_callback);
}

Return<void> WifiChip::getCapabilities(getCapabilities_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getCapabilitiesInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getAvailableModes(getAvailableModes_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getAvailableModesInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::configureChip(ChipModeId mode_id,
                                     configureChip_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::configureChipInternal,
                         hidl_status_cb,
                         mode_id);
}

Return<void> WifiChip::getMode(getMode_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getModeInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::requestChipDebugInfo(
    requestChipDebugInfo_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::requestChipDebugInfoInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::requestDriverDebugDump(
    requestDriverDebugDump_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::requestDriverDebugDumpInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::requestFirmwareDebugDump(
    requestFirmwareDebugDump_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::requestFirmwareDebugDumpInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::createApIface(createApIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::createApIfaceInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getApIfaceNames(getApIfaceNames_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getApIfaceNamesInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getApIface(const hidl_string& ifname,
                                  getApIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getApIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::removeApIface(const hidl_string& ifname,
                                     removeApIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::removeApIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::createNanIface(createNanIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::createNanIfaceInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getNanIfaceNames(getNanIfaceNames_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getNanIfaceNamesInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getNanIface(const hidl_string& ifname,
                                   getNanIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getNanIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::removeNanIface(const hidl_string& ifname,
                                      removeNanIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::removeNanIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::createP2pIface(createP2pIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::createP2pIfaceInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getP2pIfaceNames(getP2pIfaceNames_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getP2pIfaceNamesInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getP2pIface(const hidl_string& ifname,
                                   getP2pIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getP2pIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::removeP2pIface(const hidl_string& ifname,
                                      removeP2pIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::removeP2pIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::createStaIface(createStaIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::createStaIfaceInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getStaIfaceNames(getStaIfaceNames_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getStaIfaceNamesInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getStaIface(const hidl_string& ifname,
                                   getStaIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getStaIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::removeStaIface(const hidl_string& ifname,
                                      removeStaIface_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::removeStaIfaceInternal,
                         hidl_status_cb,
                         ifname);
}

Return<void> WifiChip::createRttController(
    const sp<IWifiIface>& bound_iface, createRttController_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::createRttControllerInternal,
                         hidl_status_cb,
                         bound_iface);
}

Return<void> WifiChip::getDebugRingBuffersStatus(
    getDebugRingBuffersStatus_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getDebugRingBuffersStatusInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::startLoggingToDebugRingBuffer(
    const hidl_string& ring_name,
    WifiDebugRingBufferVerboseLevel verbose_level,
    uint32_t max_interval_in_sec,
    uint32_t min_data_size_in_bytes,
    startLoggingToDebugRingBuffer_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::startLoggingToDebugRingBufferInternal,
                         hidl_status_cb,
                         ring_name,
                         verbose_level,
                         max_interval_in_sec,
                         min_data_size_in_bytes);
}

Return<void> WifiChip::forceDumpToDebugRingBuffer(
    const hidl_string& ring_name,
    forceDumpToDebugRingBuffer_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::forceDumpToDebugRingBufferInternal,
                         hidl_status_cb,
                         ring_name);
}

Return<void> WifiChip::stopLoggingToDebugRingBuffer(
    stopLoggingToDebugRingBuffer_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::stopLoggingToDebugRingBufferInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::getDebugHostWakeReasonStats(
    getDebugHostWakeReasonStats_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::getDebugHostWakeReasonStatsInternal,
                         hidl_status_cb);
}

Return<void> WifiChip::enableDebugErrorAlerts(
    bool enable, enableDebugErrorAlerts_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::enableDebugErrorAlertsInternal,
                         hidl_status_cb,
                         enable);
}

Return<void> WifiChip::selectTxPowerScenario(
    TxPowerScenario scenario, selectTxPowerScenario_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::selectTxPowerScenarioInternal,
                         hidl_status_cb,
                         scenario);
}

Return<void> WifiChip::resetTxPowerScenario(
    resetTxPowerScenario_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
                         &WifiChip::resetTxPowerScenarioInternal,
                         hidl_status_cb);
}

void WifiChip::invalidateAndRemoveAllIfaces() {
  invalidateAndClear(ap_iface_);
  invalidateAndClear(nan_iface_);
  invalidateAndClear(p2p_iface_);
  invalidateAndClear(sta_iface_);
  // Since all the ifaces are invalid now, all RTT controller objects
  // using those ifaces also need to be invalidated.
  for (const auto& rtt : rtt_controllers_) {
    rtt->invalidate();
  }
  rtt_controllers_.clear();
}

std::pair<WifiStatus, ChipId> WifiChip::getIdInternal() {
  return {createWifiStatus(WifiStatusCode::SUCCESS), chip_id_};
}

WifiStatus WifiChip::registerEventCallbackInternal(
    const sp<IWifiChipEventCallback>& event_callback) {
  if (!event_cb_handler_.addCallback(event_callback)) {
    return createWifiStatus(WifiStatusCode::ERROR_UNKNOWN);
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

std::pair<WifiStatus, uint32_t> WifiChip::getCapabilitiesInternal() {
  legacy_hal::wifi_error legacy_status;
  uint32_t legacy_feature_set;
  uint32_t legacy_logger_feature_set;
  std::tie(legacy_status, legacy_feature_set) =
      legacy_hal_.lock()->getSupportedFeatureSet();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), 0};
  }
  std::tie(legacy_status, legacy_logger_feature_set) =
      legacy_hal_.lock()->getLoggerSupportedFeatureSet();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), 0};
  }
  uint32_t hidl_caps;
  if (!hidl_struct_util::convertLegacyFeaturesToHidlChipCapabilities(
          legacy_feature_set, legacy_logger_feature_set, &hidl_caps)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), 0};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_caps};
}

std::pair<WifiStatus, std::vector<IWifiChip::ChipMode>>
WifiChip::getAvailableModesInternal() {
  // The chip combination supported for current devices is fixed for now with
  // 2 separate modes of operation:
  // Mode 1 (STA mode): Will support 1 STA and 1 P2P or NAN iface operations
  // concurrently [NAN conditional on wifiHidlFeatureAware]
  // Mode 2 (AP mode): Will support 1 AP iface operations.
  // TODO (b/32997844): Read this from some device specific flags in the
  // makefile.
  // STA mode iface combinations.
  const IWifiChip::ChipIfaceCombinationLimit
      sta_chip_iface_combination_limit_1 = {{IfaceType::STA}, 1};
  IWifiChip::ChipIfaceCombinationLimit sta_chip_iface_combination_limit_2;
  if (WifiFeatureFlags::wifiHidlFeatureAware) {
    sta_chip_iface_combination_limit_2 = {{IfaceType::P2P, IfaceType::NAN},
                                          1};
  } else {
    sta_chip_iface_combination_limit_2 = {{IfaceType::P2P},
                                          1};
  }
  const IWifiChip::ChipIfaceCombination sta_chip_iface_combination = {
      {sta_chip_iface_combination_limit_1, sta_chip_iface_combination_limit_2}};
  const IWifiChip::ChipMode sta_chip_mode = {kStaChipModeId,
                                             {sta_chip_iface_combination}};
  // AP mode iface combinations.
  const IWifiChip::ChipIfaceCombinationLimit ap_chip_iface_combination_limit = {
      {IfaceType::AP}, 1};
  const IWifiChip::ChipIfaceCombination ap_chip_iface_combination = {
      {ap_chip_iface_combination_limit}};
  const IWifiChip::ChipMode ap_chip_mode = {kApChipModeId,
                                            {ap_chip_iface_combination}};
  return {createWifiStatus(WifiStatusCode::SUCCESS),
          {sta_chip_mode, ap_chip_mode}};
}

WifiStatus WifiChip::configureChipInternal(ChipModeId mode_id) {
  if (mode_id != kStaChipModeId && mode_id != kApChipModeId) {
    return createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS);
  }
  if (mode_id == current_mode_id_) {
    LOG(DEBUG) << "Already in the specified mode " << mode_id;
    return createWifiStatus(WifiStatusCode::SUCCESS);
  }
  WifiStatus status = handleChipConfiguration(mode_id);
  if (status.code != WifiStatusCode::SUCCESS) {
    for (const auto& callback : event_cb_handler_.getCallbacks()) {
      if (!callback->onChipReconfigureFailure(status).isOk()) {
        LOG(ERROR) << "Failed to invoke onChipReconfigureFailure callback";
      }
    }
    return status;
  }
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onChipReconfigured(mode_id).isOk()) {
      LOG(ERROR) << "Failed to invoke onChipReconfigured callback";
    }
  }
  current_mode_id_ = mode_id;
  return status;
}

std::pair<WifiStatus, uint32_t> WifiChip::getModeInternal() {
  if (current_mode_id_ == kInvalidModeId) {
    return {createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE),
            current_mode_id_};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), current_mode_id_};
}

std::pair<WifiStatus, IWifiChip::ChipDebugInfo>
WifiChip::requestChipDebugInfoInternal() {
  IWifiChip::ChipDebugInfo result;
  legacy_hal::wifi_error legacy_status;
  std::string driver_desc;
  std::tie(legacy_status, driver_desc) = legacy_hal_.lock()->getDriverVersion();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to get driver version: "
               << legacyErrorToString(legacy_status);
    WifiStatus status = createWifiStatusFromLegacyError(
        legacy_status, "failed to get driver version");
    return {status, result};
  }
  result.driverDescription = driver_desc.c_str();

  std::string firmware_desc;
  std::tie(legacy_status, firmware_desc) =
      legacy_hal_.lock()->getFirmwareVersion();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to get firmware version: "
               << legacyErrorToString(legacy_status);
    WifiStatus status = createWifiStatusFromLegacyError(
        legacy_status, "failed to get firmware version");
    return {status, result};
  }
  result.firmwareDescription = firmware_desc.c_str();

  return {createWifiStatus(WifiStatusCode::SUCCESS), result};
}

std::pair<WifiStatus, std::vector<uint8_t>>
WifiChip::requestDriverDebugDumpInternal() {
  legacy_hal::wifi_error legacy_status;
  std::vector<uint8_t> driver_dump;
  std::tie(legacy_status, driver_dump) =
      legacy_hal_.lock()->requestDriverMemoryDump();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to get driver debug dump: "
               << legacyErrorToString(legacy_status);
    return {createWifiStatusFromLegacyError(legacy_status),
            std::vector<uint8_t>()};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), driver_dump};
}

std::pair<WifiStatus, std::vector<uint8_t>>
WifiChip::requestFirmwareDebugDumpInternal() {
  legacy_hal::wifi_error legacy_status;
  std::vector<uint8_t> firmware_dump;
  std::tie(legacy_status, firmware_dump) =
      legacy_hal_.lock()->requestFirmwareMemoryDump();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to get firmware debug dump: "
               << legacyErrorToString(legacy_status);
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), firmware_dump};
}

std::pair<WifiStatus, sp<IWifiApIface>> WifiChip::createApIfaceInternal() {
  if (current_mode_id_ != kApChipModeId || ap_iface_.get()) {
    return {createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE), {}};
  }
  std::string ifname = legacy_hal_.lock()->getApIfaceName();
  ap_iface_ = new WifiApIface(ifname, legacy_hal_);
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onIfaceAdded(IfaceType::AP, ifname).isOk()) {
      LOG(ERROR) << "Failed to invoke onIfaceAdded callback";
    }
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), ap_iface_};
}

std::pair<WifiStatus, std::vector<hidl_string>>
WifiChip::getApIfaceNamesInternal() {
  if (!ap_iface_.get()) {
    return {createWifiStatus(WifiStatusCode::SUCCESS), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS),
          {legacy_hal_.lock()->getApIfaceName()}};
}

std::pair<WifiStatus, sp<IWifiApIface>> WifiChip::getApIfaceInternal(
    const std::string& ifname) {
  if (!ap_iface_.get() || (ifname != legacy_hal_.lock()->getApIfaceName())) {
    return {createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS), nullptr};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), ap_iface_};
}

WifiStatus WifiChip::removeApIfaceInternal(const std::string& ifname) {
  if (!ap_iface_.get() || (ifname != legacy_hal_.lock()->getApIfaceName())) {
    return createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS);
  }
  invalidateAndClear(ap_iface_);
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onIfaceRemoved(IfaceType::AP, ifname).isOk()) {
      LOG(ERROR) << "Failed to invoke onIfaceRemoved callback";
    }
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

std::pair<WifiStatus, sp<IWifiNanIface>> WifiChip::createNanIfaceInternal() {
  // Only 1 of NAN or P2P iface can be active at a time.
  if (WifiFeatureFlags::wifiHidlFeatureAware) {
    if (current_mode_id_ != kStaChipModeId || nan_iface_.get() ||
        p2p_iface_.get()) {
      return {createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE), {}};
    }
    std::string ifname = legacy_hal_.lock()->getNanIfaceName();
    nan_iface_ = new WifiNanIface(ifname, legacy_hal_);
    for (const auto& callback : event_cb_handler_.getCallbacks()) {
      if (!callback->onIfaceAdded(IfaceType::NAN, ifname).isOk()) {
        LOG(ERROR) << "Failed to invoke onIfaceAdded callback";
      }
    }
    return {createWifiStatus(WifiStatusCode::SUCCESS), nan_iface_};
  } else {
    return {createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE), {}};
  }
}

std::pair<WifiStatus, std::vector<hidl_string>>
WifiChip::getNanIfaceNamesInternal() {
  if (!nan_iface_.get()) {
    return {createWifiStatus(WifiStatusCode::SUCCESS), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS),
          {legacy_hal_.lock()->getNanIfaceName()}};
}

std::pair<WifiStatus, sp<IWifiNanIface>> WifiChip::getNanIfaceInternal(
    const std::string& ifname) {
  if (!nan_iface_.get() || (ifname != legacy_hal_.lock()->getNanIfaceName())) {
    return {createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS), nullptr};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), nan_iface_};
}

WifiStatus WifiChip::removeNanIfaceInternal(const std::string& ifname) {
  if (!nan_iface_.get() || (ifname != legacy_hal_.lock()->getNanIfaceName())) {
    return createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS);
  }
  invalidateAndClear(nan_iface_);
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onIfaceRemoved(IfaceType::NAN, ifname).isOk()) {
      LOG(ERROR) << "Failed to invoke onIfaceAdded callback";
    }
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

std::pair<WifiStatus, sp<IWifiP2pIface>> WifiChip::createP2pIfaceInternal() {
  // Only 1 of NAN or P2P iface can be active at a time.
  if (current_mode_id_ != kStaChipModeId || p2p_iface_.get() ||
      nan_iface_.get()) {
    return {createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE), {}};
  }
  std::string ifname = legacy_hal_.lock()->getP2pIfaceName();
  p2p_iface_ = new WifiP2pIface(ifname, legacy_hal_);
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onIfaceAdded(IfaceType::P2P, ifname).isOk()) {
      LOG(ERROR) << "Failed to invoke onIfaceAdded callback";
    }
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), p2p_iface_};
}

std::pair<WifiStatus, std::vector<hidl_string>>
WifiChip::getP2pIfaceNamesInternal() {
  if (!p2p_iface_.get()) {
    return {createWifiStatus(WifiStatusCode::SUCCESS), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS),
          {legacy_hal_.lock()->getP2pIfaceName()}};
}

std::pair<WifiStatus, sp<IWifiP2pIface>> WifiChip::getP2pIfaceInternal(
    const std::string& ifname) {
  if (!p2p_iface_.get() || (ifname != legacy_hal_.lock()->getP2pIfaceName())) {
    return {createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS), nullptr};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), p2p_iface_};
}

WifiStatus WifiChip::removeP2pIfaceInternal(const std::string& ifname) {
  if (!p2p_iface_.get() || (ifname != legacy_hal_.lock()->getP2pIfaceName())) {
    return createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS);
  }
  invalidateAndClear(p2p_iface_);
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onIfaceRemoved(IfaceType::P2P, ifname).isOk()) {
      LOG(ERROR) << "Failed to invoke onIfaceRemoved callback";
    }
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

std::pair<WifiStatus, sp<IWifiStaIface>> WifiChip::createStaIfaceInternal() {
  if (current_mode_id_ != kStaChipModeId || sta_iface_.get()) {
    return {createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE), {}};
  }
  std::string ifname = legacy_hal_.lock()->getStaIfaceName();
  sta_iface_ = new WifiStaIface(ifname, legacy_hal_);
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onIfaceAdded(IfaceType::STA, ifname).isOk()) {
      LOG(ERROR) << "Failed to invoke onIfaceAdded callback";
    }
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), sta_iface_};
}

std::pair<WifiStatus, std::vector<hidl_string>>
WifiChip::getStaIfaceNamesInternal() {
  if (!sta_iface_.get()) {
    return {createWifiStatus(WifiStatusCode::SUCCESS), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS),
          {legacy_hal_.lock()->getStaIfaceName()}};
}

std::pair<WifiStatus, sp<IWifiStaIface>> WifiChip::getStaIfaceInternal(
    const std::string& ifname) {
  if (!sta_iface_.get() || (ifname != legacy_hal_.lock()->getStaIfaceName())) {
    return {createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS), nullptr};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), sta_iface_};
}

WifiStatus WifiChip::removeStaIfaceInternal(const std::string& ifname) {
  if (!sta_iface_.get() || (ifname != legacy_hal_.lock()->getStaIfaceName())) {
    return createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS);
  }
  invalidateAndClear(sta_iface_);
  for (const auto& callback : event_cb_handler_.getCallbacks()) {
    if (!callback->onIfaceRemoved(IfaceType::STA, ifname).isOk()) {
      LOG(ERROR) << "Failed to invoke onIfaceRemoved callback";
    }
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

std::pair<WifiStatus, sp<IWifiRttController>>
WifiChip::createRttControllerInternal(const sp<IWifiIface>& bound_iface) {
  sp<WifiRttController> rtt = new WifiRttController(bound_iface, legacy_hal_);
  rtt_controllers_.emplace_back(rtt);
  return {createWifiStatus(WifiStatusCode::SUCCESS), rtt};
}

std::pair<WifiStatus, std::vector<WifiDebugRingBufferStatus>>
WifiChip::getDebugRingBuffersStatusInternal() {
  legacy_hal::wifi_error legacy_status;
  std::vector<legacy_hal::wifi_ring_buffer_status>
      legacy_ring_buffer_status_vec;
  std::tie(legacy_status, legacy_ring_buffer_status_vec) =
      legacy_hal_.lock()->getRingBuffersStatus();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  std::vector<WifiDebugRingBufferStatus> hidl_ring_buffer_status_vec;
  if (!hidl_struct_util::convertLegacyVectorOfDebugRingBufferStatusToHidl(
          legacy_ring_buffer_status_vec, &hidl_ring_buffer_status_vec)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS),
          hidl_ring_buffer_status_vec};
}

WifiStatus WifiChip::startLoggingToDebugRingBufferInternal(
    const hidl_string& ring_name,
    WifiDebugRingBufferVerboseLevel verbose_level,
    uint32_t max_interval_in_sec,
    uint32_t min_data_size_in_bytes) {
  WifiStatus status = registerDebugRingBufferCallback();
  if (status.code != WifiStatusCode::SUCCESS) {
    return status;
  }
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->startRingBufferLogging(
          ring_name,
          static_cast<
              std::underlying_type<WifiDebugRingBufferVerboseLevel>::type>(
              verbose_level),
          max_interval_in_sec,
          min_data_size_in_bytes);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiChip::forceDumpToDebugRingBufferInternal(
    const hidl_string& ring_name) {
  WifiStatus status = registerDebugRingBufferCallback();
  if (status.code != WifiStatusCode::SUCCESS) {
    return status;
  }
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->getRingBufferData(ring_name);
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiChip::stopLoggingToDebugRingBufferInternal() {
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->deregisterRingBufferCallbackHandler();
  return createWifiStatusFromLegacyError(legacy_status);
}

std::pair<WifiStatus, WifiDebugHostWakeReasonStats>
WifiChip::getDebugHostWakeReasonStatsInternal() {
  legacy_hal::wifi_error legacy_status;
  legacy_hal::WakeReasonStats legacy_stats;
  std::tie(legacy_status, legacy_stats) =
      legacy_hal_.lock()->getWakeReasonStats();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    return {createWifiStatusFromLegacyError(legacy_status), {}};
  }
  WifiDebugHostWakeReasonStats hidl_stats;
  if (!hidl_struct_util::convertLegacyWakeReasonStatsToHidl(legacy_stats,
                                                            &hidl_stats)) {
    return {createWifiStatus(WifiStatusCode::ERROR_UNKNOWN), {}};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), hidl_stats};
}

WifiStatus WifiChip::enableDebugErrorAlertsInternal(bool enable) {
  legacy_hal::wifi_error legacy_status;
  if (enable) {
    android::wp<WifiChip> weak_ptr_this(this);
    const auto& on_alert_callback = [weak_ptr_this](
        int32_t error_code, std::vector<uint8_t> debug_data) {
      const auto shared_ptr_this = weak_ptr_this.promote();
      if (!shared_ptr_this.get() || !shared_ptr_this->isValid()) {
        LOG(ERROR) << "Callback invoked on an invalid object";
        return;
      }
      for (const auto& callback : shared_ptr_this->getEventCallbacks()) {
        if (!callback->onDebugErrorAlert(error_code, debug_data).isOk()) {
          LOG(ERROR) << "Failed to invoke onDebugErrorAlert callback";
        }
      }
    };
    legacy_status = legacy_hal_.lock()->registerErrorAlertCallbackHandler(
        on_alert_callback);
  } else {
    legacy_status = legacy_hal_.lock()->deregisterErrorAlertCallbackHandler();
  }
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiChip::selectTxPowerScenarioInternal(TxPowerScenario scenario) {
  auto legacy_status = legacy_hal_.lock()->selectTxPowerScenario(
      hidl_struct_util::convertHidlTxPowerScenarioToLegacy(scenario));
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiChip::resetTxPowerScenarioInternal() {
  auto legacy_status = legacy_hal_.lock()->resetTxPowerScenario();
  return createWifiStatusFromLegacyError(legacy_status);
}

WifiStatus WifiChip::handleChipConfiguration(ChipModeId mode_id) {
  // If the chip is already configured in a different mode, stop
  // the legacy HAL and then start it after firmware mode change.
  // Currently the underlying implementation has a deadlock issue.
  // We should return ERROR_NOT_SUPPORTED if chip is already configured in
  // a different mode.
  if (current_mode_id_ != kInvalidModeId) {
    // TODO(b/37446050): Fix the deadlock.
    return createWifiStatus(WifiStatusCode::ERROR_NOT_SUPPORTED);
  }
  bool success;
  if (mode_id == kStaChipModeId) {
    success = mode_controller_.lock()->changeFirmwareMode(IfaceType::STA);
  } else {
    success = mode_controller_.lock()->changeFirmwareMode(IfaceType::AP);
  }
  if (!success) {
    return createWifiStatus(WifiStatusCode::ERROR_UNKNOWN);
  }
  legacy_hal::wifi_error legacy_status = legacy_hal_.lock()->start();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to start legacy HAL: "
               << legacyErrorToString(legacy_status);
    return createWifiStatusFromLegacyError(legacy_status);
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

WifiStatus WifiChip::registerDebugRingBufferCallback() {
  if (debug_ring_buffer_cb_registered_) {
    return createWifiStatus(WifiStatusCode::SUCCESS);
  }

  android::wp<WifiChip> weak_ptr_this(this);
  const auto& on_ring_buffer_data_callback = [weak_ptr_this](
      const std::string& /* name */,
      const std::vector<uint8_t>& data,
      const legacy_hal::wifi_ring_buffer_status& status) {
    const auto shared_ptr_this = weak_ptr_this.promote();
    if (!shared_ptr_this.get() || !shared_ptr_this->isValid()) {
      LOG(ERROR) << "Callback invoked on an invalid object";
      return;
    }
    WifiDebugRingBufferStatus hidl_status;
    if (!hidl_struct_util::convertLegacyDebugRingBufferStatusToHidl(
            status, &hidl_status)) {
      LOG(ERROR) << "Error converting ring buffer status";
      return;
    }
    for (const auto& callback : shared_ptr_this->getEventCallbacks()) {
      if (!callback->onDebugRingBufferDataAvailable(hidl_status, data).isOk()) {
        LOG(ERROR) << "Failed to invoke onDebugRingBufferDataAvailable"
                   << " callback on: " << toString(callback);

      }
    }
  };
  legacy_hal::wifi_error legacy_status =
      legacy_hal_.lock()->registerRingBufferCallbackHandler(
          on_ring_buffer_data_callback);

  if (legacy_status == legacy_hal::WIFI_SUCCESS) {
    debug_ring_buffer_cb_registered_ = true;
  }
  return createWifiStatusFromLegacyError(legacy_status);
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
