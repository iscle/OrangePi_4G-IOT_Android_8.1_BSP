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

#ifndef WIFI_CHIP_H_
#define WIFI_CHIP_H_

#include <map>

#include <android-base/macros.h>
#include <android/hardware/wifi/1.1/IWifiChip.h>

#include "hidl_callback_util.h"
#include "wifi_ap_iface.h"
#include "wifi_legacy_hal.h"
#include "wifi_mode_controller.h"
#include "wifi_nan_iface.h"
#include "wifi_p2p_iface.h"
#include "wifi_rtt_controller.h"
#include "wifi_sta_iface.h"

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
using namespace android::hardware::wifi::V1_0;

/**
 * HIDL interface object used to control a Wifi HAL chip instance.
 * Since there is only a single chip instance used today, there is no
 * identifying handle information stored here.
 */
class WifiChip : public V1_1::IWifiChip {
 public:
  WifiChip(
      ChipId chip_id,
      const std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal,
      const std::weak_ptr<mode_controller::WifiModeController> mode_controller);
  // HIDL does not provide a built-in mechanism to let the server invalidate
  // a HIDL interface object after creation. If any client process holds onto
  // a reference to the object in their context, any method calls on that
  // reference will continue to be directed to the server.
  //
  // However Wifi HAL needs to control the lifetime of these objects. So, add
  // a public |invalidate| method to |WifiChip| and it's child objects. This
  // will be used to mark an object invalid when either:
  // a) Wifi HAL is stopped, or
  // b) Wifi Chip is reconfigured.
  //
  // All HIDL method implementations should check if the object is still marked
  // valid before processing them.
  void invalidate();
  bool isValid();
  std::set<sp<IWifiChipEventCallback>> getEventCallbacks();

  // HIDL methods exposed.
  Return<void> getId(getId_cb hidl_status_cb) override;
  Return<void> registerEventCallback(
      const sp<IWifiChipEventCallback>& event_callback,
      registerEventCallback_cb hidl_status_cb) override;
  Return<void> getCapabilities(getCapabilities_cb hidl_status_cb) override;
  Return<void> getAvailableModes(getAvailableModes_cb hidl_status_cb) override;
  Return<void> configureChip(ChipModeId mode_id,
                             configureChip_cb hidl_status_cb) override;
  Return<void> getMode(getMode_cb hidl_status_cb) override;
  Return<void> requestChipDebugInfo(
      requestChipDebugInfo_cb hidl_status_cb) override;
  Return<void> requestDriverDebugDump(
      requestDriverDebugDump_cb hidl_status_cb) override;
  Return<void> requestFirmwareDebugDump(
      requestFirmwareDebugDump_cb hidl_status_cb) override;
  Return<void> createApIface(createApIface_cb hidl_status_cb) override;
  Return<void> getApIfaceNames(getApIfaceNames_cb hidl_status_cb) override;
  Return<void> getApIface(const hidl_string& ifname,
                          getApIface_cb hidl_status_cb) override;
  Return<void> removeApIface(const hidl_string& ifname,
                             removeApIface_cb hidl_status_cb) override;
  Return<void> createNanIface(createNanIface_cb hidl_status_cb) override;
  Return<void> getNanIfaceNames(getNanIfaceNames_cb hidl_status_cb) override;
  Return<void> getNanIface(const hidl_string& ifname,
                           getNanIface_cb hidl_status_cb) override;
  Return<void> removeNanIface(const hidl_string& ifname,
                              removeNanIface_cb hidl_status_cb) override;
  Return<void> createP2pIface(createP2pIface_cb hidl_status_cb) override;
  Return<void> getP2pIfaceNames(getP2pIfaceNames_cb hidl_status_cb) override;
  Return<void> getP2pIface(const hidl_string& ifname,
                           getP2pIface_cb hidl_status_cb) override;
  Return<void> removeP2pIface(const hidl_string& ifname,
                              removeP2pIface_cb hidl_status_cb) override;
  Return<void> createStaIface(createStaIface_cb hidl_status_cb) override;
  Return<void> getStaIfaceNames(getStaIfaceNames_cb hidl_status_cb) override;
  Return<void> getStaIface(const hidl_string& ifname,
                           getStaIface_cb hidl_status_cb) override;
  Return<void> removeStaIface(const hidl_string& ifname,
                              removeStaIface_cb hidl_status_cb) override;
  Return<void> createRttController(
      const sp<IWifiIface>& bound_iface,
      createRttController_cb hidl_status_cb) override;
  Return<void> getDebugRingBuffersStatus(
      getDebugRingBuffersStatus_cb hidl_status_cb) override;
  Return<void> startLoggingToDebugRingBuffer(
      const hidl_string& ring_name,
      WifiDebugRingBufferVerboseLevel verbose_level,
      uint32_t max_interval_in_sec,
      uint32_t min_data_size_in_bytes,
      startLoggingToDebugRingBuffer_cb hidl_status_cb) override;
  Return<void> forceDumpToDebugRingBuffer(
      const hidl_string& ring_name,
      forceDumpToDebugRingBuffer_cb hidl_status_cb) override;
  Return<void> stopLoggingToDebugRingBuffer(
      stopLoggingToDebugRingBuffer_cb hidl_status_cb) override;
  Return<void> getDebugHostWakeReasonStats(
      getDebugHostWakeReasonStats_cb hidl_status_cb) override;
  Return<void> enableDebugErrorAlerts(
      bool enable, enableDebugErrorAlerts_cb hidl_status_cb) override;
  Return<void> selectTxPowerScenario(
      TxPowerScenario scenario,
      selectTxPowerScenario_cb hidl_status_cb) override;
  Return<void> resetTxPowerScenario(
      resetTxPowerScenario_cb hidl_status_cb) override;

 private:
  void invalidateAndRemoveAllIfaces();

  // Corresponding worker functions for the HIDL methods.
  std::pair<WifiStatus, ChipId> getIdInternal();
  WifiStatus registerEventCallbackInternal(
      const sp<IWifiChipEventCallback>& event_callback);
  std::pair<WifiStatus, uint32_t> getCapabilitiesInternal();
  std::pair<WifiStatus, std::vector<ChipMode>> getAvailableModesInternal();
  WifiStatus configureChipInternal(ChipModeId mode_id);
  std::pair<WifiStatus, uint32_t> getModeInternal();
  std::pair<WifiStatus, IWifiChip::ChipDebugInfo>
  requestChipDebugInfoInternal();
  std::pair<WifiStatus, std::vector<uint8_t>> requestDriverDebugDumpInternal();
  std::pair<WifiStatus, std::vector<uint8_t>>
  requestFirmwareDebugDumpInternal();
  std::pair<WifiStatus, sp<IWifiApIface>> createApIfaceInternal();
  std::pair<WifiStatus, std::vector<hidl_string>> getApIfaceNamesInternal();
  std::pair<WifiStatus, sp<IWifiApIface>> getApIfaceInternal(
      const std::string& ifname);
  WifiStatus removeApIfaceInternal(const std::string& ifname);
  std::pair<WifiStatus, sp<IWifiNanIface>> createNanIfaceInternal();
  std::pair<WifiStatus, std::vector<hidl_string>> getNanIfaceNamesInternal();
  std::pair<WifiStatus, sp<IWifiNanIface>> getNanIfaceInternal(
      const std::string& ifname);
  WifiStatus removeNanIfaceInternal(const std::string& ifname);
  std::pair<WifiStatus, sp<IWifiP2pIface>> createP2pIfaceInternal();
  std::pair<WifiStatus, std::vector<hidl_string>> getP2pIfaceNamesInternal();
  std::pair<WifiStatus, sp<IWifiP2pIface>> getP2pIfaceInternal(
      const std::string& ifname);
  WifiStatus removeP2pIfaceInternal(const std::string& ifname);
  std::pair<WifiStatus, sp<IWifiStaIface>> createStaIfaceInternal();
  std::pair<WifiStatus, std::vector<hidl_string>> getStaIfaceNamesInternal();
  std::pair<WifiStatus, sp<IWifiStaIface>> getStaIfaceInternal(
      const std::string& ifname);
  WifiStatus removeStaIfaceInternal(const std::string& ifname);
  std::pair<WifiStatus, sp<IWifiRttController>> createRttControllerInternal(
      const sp<IWifiIface>& bound_iface);
  std::pair<WifiStatus, std::vector<WifiDebugRingBufferStatus>>
  getDebugRingBuffersStatusInternal();
  WifiStatus startLoggingToDebugRingBufferInternal(
      const hidl_string& ring_name,
      WifiDebugRingBufferVerboseLevel verbose_level,
      uint32_t max_interval_in_sec,
      uint32_t min_data_size_in_bytes);
  WifiStatus forceDumpToDebugRingBufferInternal(const hidl_string& ring_name);
  WifiStatus stopLoggingToDebugRingBufferInternal();
  std::pair<WifiStatus, WifiDebugHostWakeReasonStats>
  getDebugHostWakeReasonStatsInternal();
  WifiStatus enableDebugErrorAlertsInternal(bool enable);
  WifiStatus selectTxPowerScenarioInternal(TxPowerScenario scenario);
  WifiStatus resetTxPowerScenarioInternal();

  WifiStatus handleChipConfiguration(ChipModeId mode_id);
  WifiStatus registerDebugRingBufferCallback();

  ChipId chip_id_;
  std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal_;
  std::weak_ptr<mode_controller::WifiModeController> mode_controller_;
  sp<WifiApIface> ap_iface_;
  sp<WifiNanIface> nan_iface_;
  sp<WifiP2pIface> p2p_iface_;
  sp<WifiStaIface> sta_iface_;
  std::vector<sp<WifiRttController>> rtt_controllers_;
  bool is_valid_;
  uint32_t current_mode_id_;
  // The legacy ring buffer callback API has only a global callback
  // registration mechanism. Use this to check if we have already
  // registered a callback.
  bool debug_ring_buffer_cb_registered_;
  hidl_callback_util::HidlCallbackHandler<IWifiChipEventCallback>
      event_cb_handler_;

  DISALLOW_COPY_AND_ASSIGN(WifiChip);
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // WIFI_CHIP_H_
