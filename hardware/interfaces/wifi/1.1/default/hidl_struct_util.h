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

#ifndef HIDL_STRUCT_UTIL_H_
#define HIDL_STRUCT_UTIL_H_

#include <vector>

#include <android/hardware/wifi/1.0/types.h>
#include <android/hardware/wifi/1.0/IWifiChip.h>
#include <android/hardware/wifi/1.1/IWifiChip.h>

#include "wifi_legacy_hal.h"

/**
 * This file contains a bunch of functions to convert structs from the legacy
 * HAL to HIDL and vice versa.
 * TODO(b/32093047): Add unit tests for these conversion methods in the VTS test
 * suite.
 */
namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace hidl_struct_util {
using namespace android::hardware::wifi::V1_0;

// Chip conversion methods.
bool convertLegacyFeaturesToHidlChipCapabilities(
    uint32_t legacy_feature_set,
    uint32_t legacy_logger_feature_set,
    uint32_t* hidl_caps);
bool convertLegacyDebugRingBufferStatusToHidl(
    const legacy_hal::wifi_ring_buffer_status& legacy_status,
    WifiDebugRingBufferStatus* hidl_status);
bool convertLegacyVectorOfDebugRingBufferStatusToHidl(
    const std::vector<legacy_hal::wifi_ring_buffer_status>& legacy_status_vec,
    std::vector<WifiDebugRingBufferStatus>* hidl_status_vec);
bool convertLegacyWakeReasonStatsToHidl(
    const legacy_hal::WakeReasonStats& legacy_stats,
    WifiDebugHostWakeReasonStats* hidl_stats);
legacy_hal::wifi_power_scenario convertHidlTxPowerScenarioToLegacy(
    V1_1::IWifiChip::TxPowerScenario hidl_scenario);

// STA iface conversion methods.
bool convertLegacyFeaturesToHidlStaCapabilities(
    uint32_t legacy_feature_set,
    uint32_t legacy_logger_feature_set,
    uint32_t* hidl_caps);
bool convertLegacyApfCapabilitiesToHidl(
    const legacy_hal::PacketFilterCapabilities& legacy_caps,
    StaApfPacketFilterCapabilities* hidl_caps);
bool convertLegacyGscanCapabilitiesToHidl(
    const legacy_hal::wifi_gscan_capabilities& legacy_caps,
    StaBackgroundScanCapabilities* hidl_caps);
legacy_hal::wifi_band convertHidlWifiBandToLegacy(WifiBand band);
bool convertHidlGscanParamsToLegacy(
    const StaBackgroundScanParameters& hidl_scan_params,
    legacy_hal::wifi_scan_cmd_params* legacy_scan_params);
// |has_ie_data| indicates whether or not the wifi_scan_result includes 802.11
// Information Elements (IEs)
bool convertLegacyGscanResultToHidl(
    const legacy_hal::wifi_scan_result& legacy_scan_result,
    bool has_ie_data,
    StaScanResult* hidl_scan_result);
// |cached_results| is assumed to not include IEs.
bool convertLegacyVectorOfCachedGscanResultsToHidl(
    const std::vector<legacy_hal::wifi_cached_scan_results>&
        legacy_cached_scan_results,
    std::vector<StaScanData>* hidl_scan_datas);
bool convertLegacyLinkLayerStatsToHidl(
    const legacy_hal::LinkLayerStats& legacy_stats,
    StaLinkLayerStats* hidl_stats);
bool convertLegacyRoamingCapabilitiesToHidl(
    const legacy_hal::wifi_roaming_capabilities& legacy_caps,
    StaRoamingCapabilities* hidl_caps);
bool convertHidlRoamingConfigToLegacy(
    const StaRoamingConfig& hidl_config,
    legacy_hal::wifi_roaming_config* legacy_config);
legacy_hal::fw_roaming_state_t convertHidlRoamingStateToLegacy(
    StaRoamingState state);
bool convertLegacyVectorOfDebugTxPacketFateToHidl(
    const std::vector<legacy_hal::wifi_tx_report>& legacy_fates,
    std::vector<WifiDebugTxPacketFateReport>* hidl_fates);
bool convertLegacyVectorOfDebugRxPacketFateToHidl(
    const std::vector<legacy_hal::wifi_rx_report>& legacy_fates,
    std::vector<WifiDebugRxPacketFateReport>* hidl_fates);

// NAN iface conversion methods.
void convertToWifiNanStatus(legacy_hal::NanStatusType type, const char* str, size_t max_len,
    WifiNanStatus* wifiNanStatus);
bool convertHidlNanEnableRequestToLegacy(
    const NanEnableRequest& hidl_request,
    legacy_hal::NanEnableRequest* legacy_request);
bool convertHidlNanConfigRequestToLegacy(
    const NanConfigRequest& hidl_request,
    legacy_hal::NanConfigRequest* legacy_request);
bool convertHidlNanPublishRequestToLegacy(
    const NanPublishRequest& hidl_request,
    legacy_hal::NanPublishRequest* legacy_request);
bool convertHidlNanSubscribeRequestToLegacy(
    const NanSubscribeRequest& hidl_request,
    legacy_hal::NanSubscribeRequest* legacy_request);
bool convertHidlNanTransmitFollowupRequestToLegacy(
    const NanTransmitFollowupRequest& hidl_request,
    legacy_hal::NanTransmitFollowupRequest* legacy_request);
bool convertHidlNanDataPathInitiatorRequestToLegacy(
    const NanInitiateDataPathRequest& hidl_request,
    legacy_hal::NanDataPathInitiatorRequest* legacy_request);
bool convertHidlNanDataPathIndicationResponseToLegacy(
    const NanRespondToDataPathIndicationRequest& hidl_response,
    legacy_hal::NanDataPathIndicationResponse* legacy_response);
bool convertLegacyNanResponseHeaderToHidl(
    const legacy_hal::NanResponseMsg& legacy_response,
    WifiNanStatus* wifiNanStatus);
bool convertLegacyNanCapabilitiesResponseToHidl(
    const legacy_hal::NanCapabilities& legacy_response,
    NanCapabilities* hidl_response);
bool convertLegacyNanMatchIndToHidl(const legacy_hal::NanMatchInd& legacy_ind,
                                    NanMatchInd* hidl_ind);
bool convertLegacyNanFollowupIndToHidl(
    const legacy_hal::NanFollowupInd& legacy_ind, NanFollowupReceivedInd* hidl_ind);
bool convertLegacyNanDataPathRequestIndToHidl(
    const legacy_hal::NanDataPathRequestInd& legacy_ind,
    NanDataPathRequestInd* hidl_ind);
bool convertLegacyNanDataPathConfirmIndToHidl(
    const legacy_hal::NanDataPathConfirmInd& legacy_ind,
    NanDataPathConfirmInd* hidl_ind);

// RTT controller conversion methods.
bool convertHidlVectorOfRttConfigToLegacy(
    const std::vector<RttConfig>& hidl_configs,
    std::vector<legacy_hal::wifi_rtt_config>* legacy_configs);
bool convertHidlRttLciInformationToLegacy(
    const RttLciInformation& hidl_info,
    legacy_hal::wifi_lci_information* legacy_info);
bool convertHidlRttLcrInformationToLegacy(
    const RttLcrInformation& hidl_info,
    legacy_hal::wifi_lcr_information* legacy_info);
bool convertHidlRttResponderToLegacy(
    const RttResponder& hidl_responder,
    legacy_hal::wifi_rtt_responder* legacy_responder);
bool convertHidlWifiChannelInfoToLegacy(
    const WifiChannelInfo& hidl_info,
    legacy_hal::wifi_channel_info* legacy_info);
bool convertLegacyRttResponderToHidl(
    const legacy_hal::wifi_rtt_responder& legacy_responder,
    RttResponder* hidl_responder);
bool convertLegacyRttCapabilitiesToHidl(
    const legacy_hal::wifi_rtt_capabilities& legacy_capabilities,
    RttCapabilities* hidl_capabilities);
bool convertLegacyVectorOfRttResultToHidl(
    const std::vector<const legacy_hal::wifi_rtt_result*>& legacy_results,
    std::vector<RttResult>* hidl_results);
}  // namespace hidl_struct_util
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_STRUCT_UTIL_H_
