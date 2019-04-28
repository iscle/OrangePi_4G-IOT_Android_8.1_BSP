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
#include <utils/SystemClock.h>

#include "hidl_struct_util.h"

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace hidl_struct_util {

hidl_string safeConvertChar(const char* str, size_t max_len) {
  const char* c = str;
  size_t size = 0;
  while (*c && (unsigned char)*c < 128 && size < max_len) {
    ++size;
    ++c;
  }
  return hidl_string(str, size);
}

IWifiChip::ChipCapabilityMask convertLegacyLoggerFeatureToHidlChipCapability(
    uint32_t feature) {
  using HidlChipCaps = IWifiChip::ChipCapabilityMask;
  switch (feature) {
    case legacy_hal::WIFI_LOGGER_MEMORY_DUMP_SUPPORTED:
      return HidlChipCaps::DEBUG_MEMORY_FIRMWARE_DUMP;
    case legacy_hal::WIFI_LOGGER_DRIVER_DUMP_SUPPORTED:
      return HidlChipCaps::DEBUG_MEMORY_DRIVER_DUMP;
    case legacy_hal::WIFI_LOGGER_CONNECT_EVENT_SUPPORTED:
      return HidlChipCaps::DEBUG_RING_BUFFER_CONNECT_EVENT;
    case legacy_hal::WIFI_LOGGER_POWER_EVENT_SUPPORTED:
      return HidlChipCaps::DEBUG_RING_BUFFER_POWER_EVENT;
    case legacy_hal::WIFI_LOGGER_WAKE_LOCK_SUPPORTED:
      return HidlChipCaps::DEBUG_RING_BUFFER_WAKELOCK_EVENT;
  };
  CHECK(false) << "Unknown legacy feature: " << feature;
  return {};
}

IWifiStaIface::StaIfaceCapabilityMask
convertLegacyLoggerFeatureToHidlStaIfaceCapability(uint32_t feature) {
  using HidlStaIfaceCaps = IWifiStaIface::StaIfaceCapabilityMask;
  switch (feature) {
    case legacy_hal::WIFI_LOGGER_PACKET_FATE_SUPPORTED:
      return HidlStaIfaceCaps::DEBUG_PACKET_FATE;
  };
  CHECK(false) << "Unknown legacy feature: " << feature;
  return {};
}

V1_1::IWifiChip::ChipCapabilityMask convertLegacyFeatureToHidlChipCapability(
    uint32_t feature) {
  using HidlChipCaps = V1_1::IWifiChip::ChipCapabilityMask;
  switch (feature) {
    case WIFI_FEATURE_SET_TX_POWER_LIMIT:
      return HidlChipCaps::SET_TX_POWER_LIMIT;
    case WIFI_FEATURE_D2D_RTT:
      return HidlChipCaps::D2D_RTT;
    case WIFI_FEATURE_D2AP_RTT:
      return HidlChipCaps::D2AP_RTT;
  };
  CHECK(false) << "Unknown legacy feature: " << feature;
  return {};
}

IWifiStaIface::StaIfaceCapabilityMask
convertLegacyFeatureToHidlStaIfaceCapability(uint32_t feature) {
  using HidlStaIfaceCaps = IWifiStaIface::StaIfaceCapabilityMask;
  switch (feature) {
    case WIFI_FEATURE_GSCAN:
      return HidlStaIfaceCaps::BACKGROUND_SCAN;
    case WIFI_FEATURE_LINK_LAYER_STATS:
      return HidlStaIfaceCaps::LINK_LAYER_STATS;
    case WIFI_FEATURE_RSSI_MONITOR:
      return HidlStaIfaceCaps::RSSI_MONITOR;
    case WIFI_FEATURE_CONTROL_ROAMING:
      return HidlStaIfaceCaps::CONTROL_ROAMING;
    case WIFI_FEATURE_IE_WHITELIST:
      return HidlStaIfaceCaps::PROBE_IE_WHITELIST;
    case WIFI_FEATURE_SCAN_RAND:
      return HidlStaIfaceCaps::SCAN_RAND;
    case WIFI_FEATURE_INFRA_5G:
      return HidlStaIfaceCaps::STA_5G;
    case WIFI_FEATURE_HOTSPOT:
      return HidlStaIfaceCaps::HOTSPOT;
    case WIFI_FEATURE_PNO:
      return HidlStaIfaceCaps::PNO;
    case WIFI_FEATURE_TDLS:
      return HidlStaIfaceCaps::TDLS;
    case WIFI_FEATURE_TDLS_OFFCHANNEL:
      return HidlStaIfaceCaps::TDLS_OFFCHANNEL;
    case WIFI_FEATURE_CONFIG_NDO:
      return HidlStaIfaceCaps::ND_OFFLOAD;
    case WIFI_FEATURE_MKEEP_ALIVE:
      return HidlStaIfaceCaps::KEEP_ALIVE;
  };
  CHECK(false) << "Unknown legacy feature: " << feature;
  return {};
}

bool convertLegacyFeaturesToHidlChipCapabilities(
    uint32_t legacy_feature_set,
    uint32_t legacy_logger_feature_set,
    uint32_t* hidl_caps) {
  if (!hidl_caps) {
    return false;
  }
  *hidl_caps = {};
  using HidlChipCaps = IWifiChip::ChipCapabilityMask;
  for (const auto feature : {legacy_hal::WIFI_LOGGER_MEMORY_DUMP_SUPPORTED,
                             legacy_hal::WIFI_LOGGER_DRIVER_DUMP_SUPPORTED,
                             legacy_hal::WIFI_LOGGER_CONNECT_EVENT_SUPPORTED,
                             legacy_hal::WIFI_LOGGER_POWER_EVENT_SUPPORTED,
                             legacy_hal::WIFI_LOGGER_WAKE_LOCK_SUPPORTED}) {
    if (feature & legacy_logger_feature_set) {
      *hidl_caps |= convertLegacyLoggerFeatureToHidlChipCapability(feature);
    }
  }
  for (const auto feature : {WIFI_FEATURE_SET_TX_POWER_LIMIT,
                             WIFI_FEATURE_D2D_RTT,
                             WIFI_FEATURE_D2AP_RTT}) {
    if (feature & legacy_feature_set) {
      *hidl_caps |= convertLegacyFeatureToHidlChipCapability(feature);
    }
  }
  // There are no flags for these 3 in the legacy feature set. Adding them to
  // the set because all the current devices support it.
  *hidl_caps |= HidlChipCaps::DEBUG_RING_BUFFER_VENDOR_DATA;
  *hidl_caps |= HidlChipCaps::DEBUG_HOST_WAKE_REASON_STATS;
  *hidl_caps |= HidlChipCaps::DEBUG_ERROR_ALERTS;
  return true;
}

WifiDebugRingBufferFlags convertLegacyDebugRingBufferFlagsToHidl(
    uint32_t flag) {
  switch (flag) {
    case WIFI_RING_BUFFER_FLAG_HAS_BINARY_ENTRIES:
      return WifiDebugRingBufferFlags::HAS_BINARY_ENTRIES;
    case WIFI_RING_BUFFER_FLAG_HAS_ASCII_ENTRIES:
      return WifiDebugRingBufferFlags::HAS_ASCII_ENTRIES;
  };
  CHECK(false) << "Unknown legacy flag: " << flag;
  return {};
}

bool convertLegacyDebugRingBufferStatusToHidl(
    const legacy_hal::wifi_ring_buffer_status& legacy_status,
    WifiDebugRingBufferStatus* hidl_status) {
  if (!hidl_status) {
    return false;
  }
  *hidl_status = {};
  hidl_status->ringName = safeConvertChar(reinterpret_cast<const char*>(legacy_status.name),
        sizeof(legacy_status.name));
  hidl_status->flags = 0;
  for (const auto flag : {WIFI_RING_BUFFER_FLAG_HAS_BINARY_ENTRIES,
                          WIFI_RING_BUFFER_FLAG_HAS_ASCII_ENTRIES}) {
    if (flag & legacy_status.flags) {
      hidl_status->flags |=
          static_cast<std::underlying_type<WifiDebugRingBufferFlags>::type>(
              convertLegacyDebugRingBufferFlagsToHidl(flag));
    }
  }
  hidl_status->ringId = legacy_status.ring_id;
  hidl_status->sizeInBytes = legacy_status.ring_buffer_byte_size;
  // Calculate free size of the ring the buffer. We don't need to send the
  // exact read/write pointers that were there in the legacy HAL interface.
  if (legacy_status.written_bytes >= legacy_status.read_bytes) {
    hidl_status->freeSizeInBytes =
        legacy_status.ring_buffer_byte_size -
        (legacy_status.written_bytes - legacy_status.read_bytes);
  } else {
    hidl_status->freeSizeInBytes =
        legacy_status.read_bytes - legacy_status.written_bytes;
  }
  hidl_status->verboseLevel = legacy_status.verbose_level;
  return true;
}

bool convertLegacyVectorOfDebugRingBufferStatusToHidl(
    const std::vector<legacy_hal::wifi_ring_buffer_status>& legacy_status_vec,
    std::vector<WifiDebugRingBufferStatus>* hidl_status_vec) {
  if (!hidl_status_vec) {
    return false;
  }
  *hidl_status_vec = {};
  for (const auto& legacy_status : legacy_status_vec) {
    WifiDebugRingBufferStatus hidl_status;
    if (!convertLegacyDebugRingBufferStatusToHidl(legacy_status,
                                                  &hidl_status)) {
      return false;
    }
    hidl_status_vec->push_back(hidl_status);
  }
  return true;
}

bool convertLegacyWakeReasonStatsToHidl(
    const legacy_hal::WakeReasonStats& legacy_stats,
    WifiDebugHostWakeReasonStats* hidl_stats) {
  if (!hidl_stats) {
    return false;
  }
  *hidl_stats = {};
  hidl_stats->totalCmdEventWakeCnt =
      legacy_stats.wake_reason_cnt.total_cmd_event_wake;
  hidl_stats->cmdEventWakeCntPerType = legacy_stats.cmd_event_wake_cnt;
  hidl_stats->totalDriverFwLocalWakeCnt =
      legacy_stats.wake_reason_cnt.total_driver_fw_local_wake;
  hidl_stats->driverFwLocalWakeCntPerType =
      legacy_stats.driver_fw_local_wake_cnt;
  hidl_stats->totalRxPacketWakeCnt =
      legacy_stats.wake_reason_cnt.total_rx_data_wake;
  hidl_stats->rxPktWakeDetails.rxUnicastCnt =
      legacy_stats.wake_reason_cnt.rx_wake_details.rx_unicast_cnt;
  hidl_stats->rxPktWakeDetails.rxMulticastCnt =
      legacy_stats.wake_reason_cnt.rx_wake_details.rx_multicast_cnt;
  hidl_stats->rxPktWakeDetails.rxBroadcastCnt =
      legacy_stats.wake_reason_cnt.rx_wake_details.rx_broadcast_cnt;
  hidl_stats->rxMulticastPkWakeDetails.ipv4RxMulticastAddrCnt =
      legacy_stats.wake_reason_cnt.rx_multicast_wake_pkt_info
          .ipv4_rx_multicast_addr_cnt;
  hidl_stats->rxMulticastPkWakeDetails.ipv6RxMulticastAddrCnt =
      legacy_stats.wake_reason_cnt.rx_multicast_wake_pkt_info
          .ipv6_rx_multicast_addr_cnt;
  hidl_stats->rxMulticastPkWakeDetails.otherRxMulticastAddrCnt =
      legacy_stats.wake_reason_cnt.rx_multicast_wake_pkt_info
          .other_rx_multicast_addr_cnt;
  hidl_stats->rxIcmpPkWakeDetails.icmpPkt =
      legacy_stats.wake_reason_cnt.rx_wake_pkt_classification_info.icmp_pkt;
  hidl_stats->rxIcmpPkWakeDetails.icmp6Pkt =
      legacy_stats.wake_reason_cnt.rx_wake_pkt_classification_info.icmp6_pkt;
  hidl_stats->rxIcmpPkWakeDetails.icmp6Ra =
      legacy_stats.wake_reason_cnt.rx_wake_pkt_classification_info.icmp6_ra;
  hidl_stats->rxIcmpPkWakeDetails.icmp6Na =
      legacy_stats.wake_reason_cnt.rx_wake_pkt_classification_info.icmp6_na;
  hidl_stats->rxIcmpPkWakeDetails.icmp6Ns =
      legacy_stats.wake_reason_cnt.rx_wake_pkt_classification_info.icmp6_ns;
  return true;
}

legacy_hal::wifi_power_scenario convertHidlTxPowerScenarioToLegacy(
    V1_1::IWifiChip::TxPowerScenario hidl_scenario) {
  switch (hidl_scenario) {
    case V1_1::IWifiChip::TxPowerScenario::VOICE_CALL:
      return legacy_hal::WIFI_POWER_SCENARIO_VOICE_CALL;
  };
  CHECK(false);
}

bool convertLegacyFeaturesToHidlStaCapabilities(
    uint32_t legacy_feature_set,
    uint32_t legacy_logger_feature_set,
    uint32_t* hidl_caps) {
  if (!hidl_caps) {
    return false;
  }
  *hidl_caps = {};
  using HidlStaIfaceCaps = IWifiStaIface::StaIfaceCapabilityMask;
  for (const auto feature : {legacy_hal::WIFI_LOGGER_PACKET_FATE_SUPPORTED}) {
    if (feature & legacy_logger_feature_set) {
      *hidl_caps |= convertLegacyLoggerFeatureToHidlStaIfaceCapability(feature);
    }
  }
  for (const auto feature : {WIFI_FEATURE_GSCAN,
                             WIFI_FEATURE_LINK_LAYER_STATS,
                             WIFI_FEATURE_RSSI_MONITOR,
                             WIFI_FEATURE_CONTROL_ROAMING,
                             WIFI_FEATURE_IE_WHITELIST,
                             WIFI_FEATURE_SCAN_RAND,
                             WIFI_FEATURE_INFRA_5G,
                             WIFI_FEATURE_HOTSPOT,
                             WIFI_FEATURE_PNO,
                             WIFI_FEATURE_TDLS,
                             WIFI_FEATURE_TDLS_OFFCHANNEL,
                             WIFI_FEATURE_CONFIG_NDO,
                             WIFI_FEATURE_MKEEP_ALIVE}) {
    if (feature & legacy_feature_set) {
      *hidl_caps |= convertLegacyFeatureToHidlStaIfaceCapability(feature);
    }
  }
  // There is no flag for this one in the legacy feature set. Adding it to the
  // set because all the current devices support it.
  *hidl_caps |= HidlStaIfaceCaps::APF;
  return true;
}

bool convertLegacyApfCapabilitiesToHidl(
    const legacy_hal::PacketFilterCapabilities& legacy_caps,
    StaApfPacketFilterCapabilities* hidl_caps) {
  if (!hidl_caps) {
    return false;
  }
  *hidl_caps = {};
  hidl_caps->version = legacy_caps.version;
  hidl_caps->maxLength = legacy_caps.max_len;
  return true;
}

uint8_t convertHidlGscanReportEventFlagToLegacy(
    StaBackgroundScanBucketEventReportSchemeMask hidl_flag) {
  using HidlFlag = StaBackgroundScanBucketEventReportSchemeMask;
  switch (hidl_flag) {
    case HidlFlag::EACH_SCAN:
      return REPORT_EVENTS_EACH_SCAN;
    case HidlFlag::FULL_RESULTS:
      return REPORT_EVENTS_FULL_RESULTS;
    case HidlFlag::NO_BATCH:
      return REPORT_EVENTS_NO_BATCH;
  };
  CHECK(false);
}

StaScanDataFlagMask convertLegacyGscanDataFlagToHidl(uint8_t legacy_flag) {
  switch (legacy_flag) {
    case legacy_hal::WIFI_SCAN_FLAG_INTERRUPTED:
      return StaScanDataFlagMask::INTERRUPTED;
  };
  CHECK(false) << "Unknown legacy flag: " << legacy_flag;
  // To silence the compiler warning about reaching the end of non-void
  // function.
  return {};
}

bool convertLegacyGscanCapabilitiesToHidl(
    const legacy_hal::wifi_gscan_capabilities& legacy_caps,
    StaBackgroundScanCapabilities* hidl_caps) {
  if (!hidl_caps) {
    return false;
  }
  *hidl_caps = {};
  hidl_caps->maxCacheSize = legacy_caps.max_scan_cache_size;
  hidl_caps->maxBuckets = legacy_caps.max_scan_buckets;
  hidl_caps->maxApCachePerScan = legacy_caps.max_ap_cache_per_scan;
  hidl_caps->maxReportingThreshold = legacy_caps.max_scan_reporting_threshold;
  return true;
}

legacy_hal::wifi_band convertHidlWifiBandToLegacy(WifiBand band) {
  switch (band) {
    case WifiBand::BAND_UNSPECIFIED:
      return legacy_hal::WIFI_BAND_UNSPECIFIED;
    case WifiBand::BAND_24GHZ:
      return legacy_hal::WIFI_BAND_BG;
    case WifiBand::BAND_5GHZ:
      return legacy_hal::WIFI_BAND_A;
    case WifiBand::BAND_5GHZ_DFS:
      return legacy_hal::WIFI_BAND_A_DFS;
    case WifiBand::BAND_5GHZ_WITH_DFS:
      return legacy_hal::WIFI_BAND_A_WITH_DFS;
    case WifiBand::BAND_24GHZ_5GHZ:
      return legacy_hal::WIFI_BAND_ABG;
    case WifiBand::BAND_24GHZ_5GHZ_WITH_DFS:
      return legacy_hal::WIFI_BAND_ABG_WITH_DFS;
  };
  CHECK(false);
}

bool convertHidlGscanParamsToLegacy(
    const StaBackgroundScanParameters& hidl_scan_params,
    legacy_hal::wifi_scan_cmd_params* legacy_scan_params) {
  if (!legacy_scan_params) {
    return false;
  }
  *legacy_scan_params = {};
  legacy_scan_params->base_period = hidl_scan_params.basePeriodInMs;
  legacy_scan_params->max_ap_per_scan = hidl_scan_params.maxApPerScan;
  legacy_scan_params->report_threshold_percent =
      hidl_scan_params.reportThresholdPercent;
  legacy_scan_params->report_threshold_num_scans =
      hidl_scan_params.reportThresholdNumScans;
  if (hidl_scan_params.buckets.size() > MAX_BUCKETS) {
    return false;
  }
  legacy_scan_params->num_buckets = hidl_scan_params.buckets.size();
  for (uint32_t bucket_idx = 0; bucket_idx < hidl_scan_params.buckets.size();
       bucket_idx++) {
    const StaBackgroundScanBucketParameters& hidl_bucket_spec =
        hidl_scan_params.buckets[bucket_idx];
    legacy_hal::wifi_scan_bucket_spec& legacy_bucket_spec =
        legacy_scan_params->buckets[bucket_idx];
    if (hidl_bucket_spec.bucketIdx >= MAX_BUCKETS) {
      return false;
    }
    legacy_bucket_spec.bucket = hidl_bucket_spec.bucketIdx;
    legacy_bucket_spec.band =
        convertHidlWifiBandToLegacy(hidl_bucket_spec.band);
    legacy_bucket_spec.period = hidl_bucket_spec.periodInMs;
    legacy_bucket_spec.max_period = hidl_bucket_spec.exponentialMaxPeriodInMs;
    legacy_bucket_spec.base = hidl_bucket_spec.exponentialBase;
    legacy_bucket_spec.step_count = hidl_bucket_spec.exponentialStepCount;
    legacy_bucket_spec.report_events = 0;
    using HidlFlag = StaBackgroundScanBucketEventReportSchemeMask;
    for (const auto flag :
         {HidlFlag::EACH_SCAN, HidlFlag::FULL_RESULTS, HidlFlag::NO_BATCH}) {
      if (hidl_bucket_spec.eventReportScheme &
          static_cast<std::underlying_type<HidlFlag>::type>(flag)) {
        legacy_bucket_spec.report_events |=
            convertHidlGscanReportEventFlagToLegacy(flag);
      }
    }
    if (hidl_bucket_spec.frequencies.size() > MAX_CHANNELS) {
      return false;
    }
    legacy_bucket_spec.num_channels = hidl_bucket_spec.frequencies.size();
    for (uint32_t freq_idx = 0; freq_idx < hidl_bucket_spec.frequencies.size();
         freq_idx++) {
      legacy_bucket_spec.channels[freq_idx].channel =
          hidl_bucket_spec.frequencies[freq_idx];
    }
  }
  return true;
}

bool convertLegacyIeToHidl(
    const legacy_hal::wifi_information_element& legacy_ie,
    WifiInformationElement* hidl_ie) {
  if (!hidl_ie) {
    return false;
  }
  *hidl_ie = {};
  hidl_ie->id = legacy_ie.id;
  hidl_ie->data =
      std::vector<uint8_t>(legacy_ie.data, legacy_ie.data + legacy_ie.len);
  return true;
}

bool convertLegacyIeBlobToHidl(const uint8_t* ie_blob,
                               uint32_t ie_blob_len,
                               std::vector<WifiInformationElement>* hidl_ies) {
  if (!ie_blob || !hidl_ies) {
    return false;
  }
  *hidl_ies = {};
  const uint8_t* ies_begin = ie_blob;
  const uint8_t* ies_end = ie_blob + ie_blob_len;
  const uint8_t* next_ie = ies_begin;
  using wifi_ie = legacy_hal::wifi_information_element;
  constexpr size_t kIeHeaderLen = sizeof(wifi_ie);
  // Each IE should atleast have the header (i.e |id| & |len| fields).
  while (next_ie + kIeHeaderLen <= ies_end) {
    const wifi_ie& legacy_ie = (*reinterpret_cast<const wifi_ie*>(next_ie));
    uint32_t curr_ie_len = kIeHeaderLen + legacy_ie.len;
    if (next_ie + curr_ie_len > ies_end) {
      LOG(ERROR) << "Error parsing IE blob. Next IE: " << (void *)next_ie
                 << ", Curr IE len: " << curr_ie_len << ", IEs End: " << (void *)ies_end;
      break;
    }
    WifiInformationElement hidl_ie;
    if (!convertLegacyIeToHidl(legacy_ie, &hidl_ie)) {
      LOG(ERROR) << "Error converting IE. Id: " << legacy_ie.id
                 << ", len: " << legacy_ie.len;
      break;
    }
    hidl_ies->push_back(std::move(hidl_ie));
    next_ie += curr_ie_len;
  }
  // Check if the blob has been fully consumed.
  if (next_ie != ies_end) {
    LOG(ERROR) << "Failed to fully parse IE blob. Next IE: " << (void *)next_ie
               << ", IEs End: " << (void *)ies_end;
  }
  return true;
}

bool convertLegacyGscanResultToHidl(
    const legacy_hal::wifi_scan_result& legacy_scan_result,
    bool has_ie_data,
    StaScanResult* hidl_scan_result) {
  if (!hidl_scan_result) {
    return false;
  }
  *hidl_scan_result = {};
  hidl_scan_result->timeStampInUs = legacy_scan_result.ts;
  hidl_scan_result->ssid = std::vector<uint8_t>(
      legacy_scan_result.ssid,
      legacy_scan_result.ssid + strnlen(legacy_scan_result.ssid,
            sizeof(legacy_scan_result.ssid) - 1));
  memcpy(hidl_scan_result->bssid.data(),
         legacy_scan_result.bssid,
         hidl_scan_result->bssid.size());
  hidl_scan_result->frequency = legacy_scan_result.channel;
  hidl_scan_result->rssi = legacy_scan_result.rssi;
  hidl_scan_result->beaconPeriodInMs = legacy_scan_result.beacon_period;
  hidl_scan_result->capability = legacy_scan_result.capability;
  if (has_ie_data) {
    std::vector<WifiInformationElement> ies;
    if (!convertLegacyIeBlobToHidl(
            reinterpret_cast<const uint8_t*>(legacy_scan_result.ie_data),
            legacy_scan_result.ie_length,
            &ies)) {
      return false;
    }
    hidl_scan_result->informationElements = std::move(ies);
  }
  return true;
}

bool convertLegacyCachedGscanResultsToHidl(
    const legacy_hal::wifi_cached_scan_results& legacy_cached_scan_result,
    StaScanData* hidl_scan_data) {
  if (!hidl_scan_data) {
    return false;
  }
  *hidl_scan_data = {};
  hidl_scan_data->flags = 0;
  for (const auto flag : {legacy_hal::WIFI_SCAN_FLAG_INTERRUPTED}) {
    if (legacy_cached_scan_result.flags & flag) {
      hidl_scan_data->flags |=
          static_cast<std::underlying_type<StaScanDataFlagMask>::type>(
              convertLegacyGscanDataFlagToHidl(flag));
    }
  }
  hidl_scan_data->bucketsScanned = legacy_cached_scan_result.buckets_scanned;

  CHECK(legacy_cached_scan_result.num_results >= 0 &&
        legacy_cached_scan_result.num_results <= MAX_AP_CACHE_PER_SCAN);
  std::vector<StaScanResult> hidl_scan_results;
  for (int32_t result_idx = 0;
       result_idx < legacy_cached_scan_result.num_results;
       result_idx++) {
    StaScanResult hidl_scan_result;
    if (!convertLegacyGscanResultToHidl(
            legacy_cached_scan_result.results[result_idx],
            false,
            &hidl_scan_result)) {
      return false;
    }
    hidl_scan_results.push_back(hidl_scan_result);
  }
  hidl_scan_data->results = std::move(hidl_scan_results);
  return true;
}

bool convertLegacyVectorOfCachedGscanResultsToHidl(
    const std::vector<legacy_hal::wifi_cached_scan_results>&
        legacy_cached_scan_results,
    std::vector<StaScanData>* hidl_scan_datas) {
  if (!hidl_scan_datas) {
    return false;
  }
  *hidl_scan_datas = {};
  for (const auto& legacy_cached_scan_result : legacy_cached_scan_results) {
    StaScanData hidl_scan_data;
    if (!convertLegacyCachedGscanResultsToHidl(legacy_cached_scan_result,
                                               &hidl_scan_data)) {
      return false;
    }
    hidl_scan_datas->push_back(hidl_scan_data);
  }
  return true;
}

WifiDebugTxPacketFate convertLegacyDebugTxPacketFateToHidl(
    legacy_hal::wifi_tx_packet_fate fate) {
  switch (fate) {
    case legacy_hal::TX_PKT_FATE_ACKED:
      return WifiDebugTxPacketFate::ACKED;
    case legacy_hal::TX_PKT_FATE_SENT:
      return WifiDebugTxPacketFate::SENT;
    case legacy_hal::TX_PKT_FATE_FW_QUEUED:
      return WifiDebugTxPacketFate::FW_QUEUED;
    case legacy_hal::TX_PKT_FATE_FW_DROP_INVALID:
      return WifiDebugTxPacketFate::FW_DROP_INVALID;
    case legacy_hal::TX_PKT_FATE_FW_DROP_NOBUFS:
      return WifiDebugTxPacketFate::FW_DROP_NOBUFS;
    case legacy_hal::TX_PKT_FATE_FW_DROP_OTHER:
      return WifiDebugTxPacketFate::FW_DROP_OTHER;
    case legacy_hal::TX_PKT_FATE_DRV_QUEUED:
      return WifiDebugTxPacketFate::DRV_QUEUED;
    case legacy_hal::TX_PKT_FATE_DRV_DROP_INVALID:
      return WifiDebugTxPacketFate::DRV_DROP_INVALID;
    case legacy_hal::TX_PKT_FATE_DRV_DROP_NOBUFS:
      return WifiDebugTxPacketFate::DRV_DROP_NOBUFS;
    case legacy_hal::TX_PKT_FATE_DRV_DROP_OTHER:
      return WifiDebugTxPacketFate::DRV_DROP_OTHER;
  };
  CHECK(false) << "Unknown legacy fate type: " << fate;
}

WifiDebugRxPacketFate convertLegacyDebugRxPacketFateToHidl(
    legacy_hal::wifi_rx_packet_fate fate) {
  switch (fate) {
    case legacy_hal::RX_PKT_FATE_SUCCESS:
      return WifiDebugRxPacketFate::SUCCESS;
    case legacy_hal::RX_PKT_FATE_FW_QUEUED:
      return WifiDebugRxPacketFate::FW_QUEUED;
    case legacy_hal::RX_PKT_FATE_FW_DROP_FILTER:
      return WifiDebugRxPacketFate::FW_DROP_FILTER;
    case legacy_hal::RX_PKT_FATE_FW_DROP_INVALID:
      return WifiDebugRxPacketFate::FW_DROP_INVALID;
    case legacy_hal::RX_PKT_FATE_FW_DROP_NOBUFS:
      return WifiDebugRxPacketFate::FW_DROP_NOBUFS;
    case legacy_hal::RX_PKT_FATE_FW_DROP_OTHER:
      return WifiDebugRxPacketFate::FW_DROP_OTHER;
    case legacy_hal::RX_PKT_FATE_DRV_QUEUED:
      return WifiDebugRxPacketFate::DRV_QUEUED;
    case legacy_hal::RX_PKT_FATE_DRV_DROP_FILTER:
      return WifiDebugRxPacketFate::DRV_DROP_FILTER;
    case legacy_hal::RX_PKT_FATE_DRV_DROP_INVALID:
      return WifiDebugRxPacketFate::DRV_DROP_INVALID;
    case legacy_hal::RX_PKT_FATE_DRV_DROP_NOBUFS:
      return WifiDebugRxPacketFate::DRV_DROP_NOBUFS;
    case legacy_hal::RX_PKT_FATE_DRV_DROP_OTHER:
      return WifiDebugRxPacketFate::DRV_DROP_OTHER;
  };
  CHECK(false) << "Unknown legacy fate type: " << fate;
}

WifiDebugPacketFateFrameType convertLegacyDebugPacketFateFrameTypeToHidl(
    legacy_hal::frame_type type) {
  switch (type) {
    case legacy_hal::FRAME_TYPE_UNKNOWN:
      return WifiDebugPacketFateFrameType::UNKNOWN;
    case legacy_hal::FRAME_TYPE_ETHERNET_II:
      return WifiDebugPacketFateFrameType::ETHERNET_II;
    case legacy_hal::FRAME_TYPE_80211_MGMT:
      return WifiDebugPacketFateFrameType::MGMT_80211;
  };
  CHECK(false) << "Unknown legacy frame type: " << type;
}

bool convertLegacyDebugPacketFateFrameToHidl(
    const legacy_hal::frame_info& legacy_frame,
    WifiDebugPacketFateFrameInfo* hidl_frame) {
  if (!hidl_frame) {
    return false;
  }
  *hidl_frame = {};
  hidl_frame->frameType =
      convertLegacyDebugPacketFateFrameTypeToHidl(legacy_frame.payload_type);
  hidl_frame->frameLen = legacy_frame.frame_len;
  hidl_frame->driverTimestampUsec = legacy_frame.driver_timestamp_usec;
  hidl_frame->firmwareTimestampUsec = legacy_frame.firmware_timestamp_usec;
  const uint8_t* frame_begin = reinterpret_cast<const uint8_t*>(
      legacy_frame.frame_content.ethernet_ii_bytes);
  hidl_frame->frameContent =
      std::vector<uint8_t>(frame_begin, frame_begin + legacy_frame.frame_len);
  return true;
}

bool convertLegacyDebugTxPacketFateToHidl(
    const legacy_hal::wifi_tx_report& legacy_fate,
    WifiDebugTxPacketFateReport* hidl_fate) {
  if (!hidl_fate) {
    return false;
  }
  *hidl_fate = {};
  hidl_fate->fate = convertLegacyDebugTxPacketFateToHidl(legacy_fate.fate);
  return convertLegacyDebugPacketFateFrameToHidl(legacy_fate.frame_inf,
                                                 &hidl_fate->frameInfo);
}

bool convertLegacyVectorOfDebugTxPacketFateToHidl(
    const std::vector<legacy_hal::wifi_tx_report>& legacy_fates,
    std::vector<WifiDebugTxPacketFateReport>* hidl_fates) {
  if (!hidl_fates) {
    return false;
  }
  *hidl_fates = {};
  for (const auto& legacy_fate : legacy_fates) {
    WifiDebugTxPacketFateReport hidl_fate;
    if (!convertLegacyDebugTxPacketFateToHidl(legacy_fate, &hidl_fate)) {
      return false;
    }
    hidl_fates->push_back(hidl_fate);
  }
  return true;
}

bool convertLegacyDebugRxPacketFateToHidl(
    const legacy_hal::wifi_rx_report& legacy_fate,
    WifiDebugRxPacketFateReport* hidl_fate) {
  if (!hidl_fate) {
    return false;
  }
  *hidl_fate = {};
  hidl_fate->fate = convertLegacyDebugRxPacketFateToHidl(legacy_fate.fate);
  return convertLegacyDebugPacketFateFrameToHidl(legacy_fate.frame_inf,
                                                 &hidl_fate->frameInfo);
}

bool convertLegacyVectorOfDebugRxPacketFateToHidl(
    const std::vector<legacy_hal::wifi_rx_report>& legacy_fates,
    std::vector<WifiDebugRxPacketFateReport>* hidl_fates) {
  if (!hidl_fates) {
    return false;
  }
  *hidl_fates = {};
  for (const auto& legacy_fate : legacy_fates) {
    WifiDebugRxPacketFateReport hidl_fate;
    if (!convertLegacyDebugRxPacketFateToHidl(legacy_fate, &hidl_fate)) {
      return false;
    }
    hidl_fates->push_back(hidl_fate);
  }
  return true;
}

bool convertLegacyLinkLayerStatsToHidl(
    const legacy_hal::LinkLayerStats& legacy_stats,
    StaLinkLayerStats* hidl_stats) {
  if (!hidl_stats) {
    return false;
  }
  *hidl_stats = {};
  // iface legacy_stats conversion.
  hidl_stats->iface.beaconRx = legacy_stats.iface.beacon_rx;
  hidl_stats->iface.avgRssiMgmt = legacy_stats.iface.rssi_mgmt;
  hidl_stats->iface.wmeBePktStats.rxMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BE].rx_mpdu;
  hidl_stats->iface.wmeBePktStats.txMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BE].tx_mpdu;
  hidl_stats->iface.wmeBePktStats.lostMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BE].mpdu_lost;
  hidl_stats->iface.wmeBePktStats.retries =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BE].retries;
  hidl_stats->iface.wmeBkPktStats.rxMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BK].rx_mpdu;
  hidl_stats->iface.wmeBkPktStats.txMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BK].tx_mpdu;
  hidl_stats->iface.wmeBkPktStats.lostMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BK].mpdu_lost;
  hidl_stats->iface.wmeBkPktStats.retries =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_BK].retries;
  hidl_stats->iface.wmeViPktStats.rxMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VI].rx_mpdu;
  hidl_stats->iface.wmeViPktStats.txMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VI].tx_mpdu;
  hidl_stats->iface.wmeViPktStats.lostMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VI].mpdu_lost;
  hidl_stats->iface.wmeViPktStats.retries =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VI].retries;
  hidl_stats->iface.wmeVoPktStats.rxMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VO].rx_mpdu;
  hidl_stats->iface.wmeVoPktStats.txMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VO].tx_mpdu;
  hidl_stats->iface.wmeVoPktStats.lostMpdu =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VO].mpdu_lost;
  hidl_stats->iface.wmeVoPktStats.retries =
      legacy_stats.iface.ac[legacy_hal::WIFI_AC_VO].retries;
  // radio legacy_stats conversion.
  std::vector<StaLinkLayerRadioStats> hidl_radios_stats;
  for (const auto& legacy_radio_stats : legacy_stats.radios) {
    StaLinkLayerRadioStats hidl_radio_stats;
    hidl_radio_stats.onTimeInMs = legacy_radio_stats.stats.on_time;
    hidl_radio_stats.txTimeInMs = legacy_radio_stats.stats.tx_time;
    hidl_radio_stats.rxTimeInMs = legacy_radio_stats.stats.rx_time;
    hidl_radio_stats.onTimeInMsForScan = legacy_radio_stats.stats.on_time_scan;
    hidl_radio_stats.txTimeInMsPerLevel = legacy_radio_stats.tx_time_per_levels;
    hidl_radios_stats.push_back(hidl_radio_stats);
  }
  hidl_stats->radios = hidl_radios_stats;
  // Timestamp in the HAL wrapper here since it's not provided in the legacy
  // HAL API.
  hidl_stats->timeStampInMs = uptimeMillis();
  return true;
}

bool convertLegacyRoamingCapabilitiesToHidl(
    const legacy_hal::wifi_roaming_capabilities& legacy_caps,
    StaRoamingCapabilities* hidl_caps) {
  if (!hidl_caps) {
    return false;
  }
  *hidl_caps = {};
  hidl_caps->maxBlacklistSize = legacy_caps.max_blacklist_size;
  hidl_caps->maxWhitelistSize = legacy_caps.max_whitelist_size;
  return true;
}

bool convertHidlRoamingConfigToLegacy(
    const StaRoamingConfig& hidl_config,
    legacy_hal::wifi_roaming_config* legacy_config) {
  if (!legacy_config) {
    return false;
  }
  *legacy_config = {};
  if (hidl_config.bssidBlacklist.size() > MAX_BLACKLIST_BSSID ||
      hidl_config.ssidWhitelist.size() > MAX_WHITELIST_SSID) {
    return false;
  }
  legacy_config->num_blacklist_bssid = hidl_config.bssidBlacklist.size();
  uint32_t i = 0;
  for (const auto& bssid : hidl_config.bssidBlacklist) {
    CHECK(bssid.size() == sizeof(legacy_hal::mac_addr));
    memcpy(legacy_config->blacklist_bssid[i++], bssid.data(), bssid.size());
  }
  legacy_config->num_whitelist_ssid = hidl_config.ssidWhitelist.size();
  i = 0;
  for (const auto& ssid : hidl_config.ssidWhitelist) {
    CHECK(ssid.size() <= sizeof(legacy_hal::ssid_t::ssid_str));
    legacy_config->whitelist_ssid[i].length = ssid.size();
    memcpy(legacy_config->whitelist_ssid[i].ssid_str, ssid.data(), ssid.size());
    i++;
  }
  return true;
}

legacy_hal::fw_roaming_state_t convertHidlRoamingStateToLegacy(
    StaRoamingState state) {
  switch (state) {
    case StaRoamingState::ENABLED:
      return legacy_hal::ROAMING_ENABLE;
    case StaRoamingState::DISABLED:
      return legacy_hal::ROAMING_DISABLE;
  };
  CHECK(false);
}

legacy_hal::NanMatchAlg convertHidlNanMatchAlgToLegacy(NanMatchAlg type) {
  switch (type) {
    case NanMatchAlg::MATCH_ONCE:
      return legacy_hal::NAN_MATCH_ALG_MATCH_ONCE;
    case NanMatchAlg::MATCH_CONTINUOUS:
      return legacy_hal::NAN_MATCH_ALG_MATCH_CONTINUOUS;
    case NanMatchAlg::MATCH_NEVER:
      return legacy_hal::NAN_MATCH_ALG_MATCH_NEVER;
  }
  CHECK(false);
}

legacy_hal::NanPublishType convertHidlNanPublishTypeToLegacy(NanPublishType type) {
  switch (type) {
    case NanPublishType::UNSOLICITED:
      return legacy_hal::NAN_PUBLISH_TYPE_UNSOLICITED;
    case NanPublishType::SOLICITED:
      return legacy_hal::NAN_PUBLISH_TYPE_SOLICITED;
    case NanPublishType::UNSOLICITED_SOLICITED:
      return legacy_hal::NAN_PUBLISH_TYPE_UNSOLICITED_SOLICITED;
  }
  CHECK(false);
}

legacy_hal::NanTxType convertHidlNanTxTypeToLegacy(NanTxType type) {
  switch (type) {
    case NanTxType::BROADCAST:
      return legacy_hal::NAN_TX_TYPE_BROADCAST;
    case NanTxType::UNICAST:
      return legacy_hal::NAN_TX_TYPE_UNICAST;
  }
  CHECK(false);
}

legacy_hal::NanSubscribeType convertHidlNanSubscribeTypeToLegacy(NanSubscribeType type) {
  switch (type) {
    case NanSubscribeType::PASSIVE:
      return legacy_hal::NAN_SUBSCRIBE_TYPE_PASSIVE;
    case NanSubscribeType::ACTIVE:
      return legacy_hal::NAN_SUBSCRIBE_TYPE_ACTIVE;
  }
  CHECK(false);
}

legacy_hal::NanSRFType convertHidlNanSrfTypeToLegacy(NanSrfType type) {
  switch (type) {
    case NanSrfType::BLOOM_FILTER:
      return legacy_hal::NAN_SRF_ATTR_BLOOM_FILTER;
    case NanSrfType::PARTIAL_MAC_ADDR:
      return legacy_hal::NAN_SRF_ATTR_PARTIAL_MAC_ADDR;
  }
  CHECK(false);
}

legacy_hal::NanDataPathChannelCfg convertHidlNanDataPathChannelCfgToLegacy(
    NanDataPathChannelCfg type) {
  switch (type) {
    case NanDataPathChannelCfg::CHANNEL_NOT_REQUESTED:
      return legacy_hal::NAN_DP_CHANNEL_NOT_REQUESTED;
    case NanDataPathChannelCfg::REQUEST_CHANNEL_SETUP:
      return legacy_hal::NAN_DP_REQUEST_CHANNEL_SETUP;
    case NanDataPathChannelCfg::FORCE_CHANNEL_SETUP:
      return legacy_hal::NAN_DP_FORCE_CHANNEL_SETUP;
  }
  CHECK(false);
}

NanStatusType convertLegacyNanStatusTypeToHidl(
    legacy_hal::NanStatusType type) {
  switch (type) {
    case legacy_hal::NAN_STATUS_SUCCESS:
      return NanStatusType::SUCCESS;
    case legacy_hal::NAN_STATUS_INTERNAL_FAILURE:
      return NanStatusType::INTERNAL_FAILURE;
    case legacy_hal::NAN_STATUS_PROTOCOL_FAILURE:
      return NanStatusType::PROTOCOL_FAILURE;
    case legacy_hal::NAN_STATUS_INVALID_PUBLISH_SUBSCRIBE_ID:
      return NanStatusType::INVALID_SESSION_ID;
    case legacy_hal::NAN_STATUS_NO_RESOURCE_AVAILABLE:
      return NanStatusType::NO_RESOURCES_AVAILABLE;
    case legacy_hal::NAN_STATUS_INVALID_PARAM:
      return NanStatusType::INVALID_ARGS;
    case legacy_hal::NAN_STATUS_INVALID_REQUESTOR_INSTANCE_ID:
      return NanStatusType::INVALID_PEER_ID;
    case legacy_hal::NAN_STATUS_INVALID_NDP_ID:
      return NanStatusType::INVALID_NDP_ID;
    case legacy_hal::NAN_STATUS_NAN_NOT_ALLOWED:
      return NanStatusType::NAN_NOT_ALLOWED;
    case legacy_hal::NAN_STATUS_NO_OTA_ACK:
      return NanStatusType::NO_OTA_ACK;
    case legacy_hal::NAN_STATUS_ALREADY_ENABLED:
      return NanStatusType::ALREADY_ENABLED;
    case legacy_hal::NAN_STATUS_FOLLOWUP_QUEUE_FULL:
      return NanStatusType::FOLLOWUP_TX_QUEUE_FULL;
    case legacy_hal::NAN_STATUS_UNSUPPORTED_CONCURRENCY_NAN_DISABLED:
      return NanStatusType::UNSUPPORTED_CONCURRENCY_NAN_DISABLED;
  }
  CHECK(false);
}

void convertToWifiNanStatus(legacy_hal::NanStatusType type, const char* str, size_t max_len,
    WifiNanStatus* wifiNanStatus) {
  wifiNanStatus->status = convertLegacyNanStatusTypeToHidl(type);
  wifiNanStatus->description = safeConvertChar(str, max_len);
}

bool convertHidlNanEnableRequestToLegacy(
    const NanEnableRequest& hidl_request,
    legacy_hal::NanEnableRequest* legacy_request) {
  if (!legacy_request) {
    LOG(ERROR) << "convertHidlNanEnableRequestToLegacy: null legacy_request";
    return false;
  }
  *legacy_request = {};

  legacy_request->config_2dot4g_support = 1;
  legacy_request->support_2dot4g_val = hidl_request.operateInBand[
        (size_t) NanBandIndex::NAN_BAND_24GHZ];
  legacy_request->config_support_5g = 1;
  legacy_request->support_5g_val = hidl_request.operateInBand[(size_t) NanBandIndex::NAN_BAND_5GHZ];
  legacy_request->config_hop_count_limit = 1;
  legacy_request->hop_count_limit_val = hidl_request.hopCountMax;
  legacy_request->master_pref = hidl_request.configParams.masterPref;
  legacy_request->discovery_indication_cfg = 0;
  legacy_request->discovery_indication_cfg |=
        hidl_request.configParams.disableDiscoveryAddressChangeIndication ? 0x1 : 0x0;
  legacy_request->discovery_indication_cfg |=
        hidl_request.configParams.disableStartedClusterIndication ? 0x2 : 0x0;
  legacy_request->discovery_indication_cfg |=
        hidl_request.configParams.disableJoinedClusterIndication ? 0x4 : 0x0;
  legacy_request->config_sid_beacon = 1;
  if (hidl_request.configParams.numberOfPublishServiceIdsInBeacon > 127) {
    LOG(ERROR) << "convertHidlNanEnableRequestToLegacy: numberOfPublishServiceIdsInBeacon > 127";
    return false;
  }
  legacy_request->sid_beacon_val =
        (hidl_request.configParams.includePublishServiceIdsInBeacon ? 0x1 : 0x0)
            | (hidl_request.configParams.numberOfPublishServiceIdsInBeacon << 1);
  legacy_request->config_subscribe_sid_beacon = 1;
  if (hidl_request.configParams.numberOfSubscribeServiceIdsInBeacon > 127) {
    LOG(ERROR) << "convertHidlNanEnableRequestToLegacy: numberOfSubscribeServiceIdsInBeacon > 127";
    return false;
  }
  legacy_request->subscribe_sid_beacon_val =
        (hidl_request.configParams.includeSubscribeServiceIdsInBeacon ? 0x1 : 0x0)
            | (hidl_request.configParams.numberOfSubscribeServiceIdsInBeacon << 1);
  legacy_request->config_rssi_window_size = 1;
  legacy_request->rssi_window_size_val = hidl_request.configParams.rssiWindowSize;
  legacy_request->config_disc_mac_addr_randomization = 1;
  legacy_request->disc_mac_addr_rand_interval_sec =
        hidl_request.configParams.macAddressRandomizationIntervalSec;
  legacy_request->config_2dot4g_rssi_close = 1;
  if (hidl_request.configParams.bandSpecificConfig.size() != 2) {
    LOG(ERROR) << "convertHidlNanEnableRequestToLegacy: bandSpecificConfig.size() != 2";
    return false;
  }
  legacy_request->rssi_close_2dot4g_val =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].rssiClose;
  legacy_request->config_2dot4g_rssi_middle = 1;
  legacy_request->rssi_middle_2dot4g_val =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].rssiMiddle;
  legacy_request->config_2dot4g_rssi_proximity = 1;
  legacy_request->rssi_proximity_2dot4g_val =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].rssiCloseProximity;
  legacy_request->config_scan_params = 1;
  legacy_request->scan_params_val.dwell_time[legacy_hal::NAN_CHANNEL_24G_BAND] =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].dwellTimeMs;
  legacy_request->scan_params_val.scan_period[legacy_hal::NAN_CHANNEL_24G_BAND] =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].scanPeriodSec;
  legacy_request->config_dw.config_2dot4g_dw_band = hidl_request.configParams
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_24GHZ].validDiscoveryWindowIntervalVal;
  legacy_request->config_dw.dw_2dot4g_interval_val = hidl_request.configParams
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_24GHZ].discoveryWindowIntervalVal;
  legacy_request->config_5g_rssi_close = 1;
  legacy_request->rssi_close_5g_val =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].rssiClose;
  legacy_request->config_5g_rssi_middle = 1;
  legacy_request->rssi_middle_5g_val =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].rssiMiddle;
  legacy_request->config_5g_rssi_close_proximity = 1;
  legacy_request->rssi_close_proximity_5g_val =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].rssiCloseProximity;
  legacy_request->scan_params_val.dwell_time[legacy_hal::NAN_CHANNEL_5G_BAND_LOW] =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].dwellTimeMs;
  legacy_request->scan_params_val.scan_period[legacy_hal::NAN_CHANNEL_5G_BAND_LOW] =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].scanPeriodSec;
  legacy_request->scan_params_val.dwell_time[legacy_hal::NAN_CHANNEL_5G_BAND_HIGH] =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].dwellTimeMs;
  legacy_request->scan_params_val.scan_period[legacy_hal::NAN_CHANNEL_5G_BAND_HIGH] =
        hidl_request.configParams.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].scanPeriodSec;
  legacy_request->config_dw.config_5g_dw_band = hidl_request.configParams
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_5GHZ].validDiscoveryWindowIntervalVal;
  legacy_request->config_dw.dw_5g_interval_val = hidl_request.configParams
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_5GHZ].discoveryWindowIntervalVal;
  if (hidl_request.debugConfigs.validClusterIdVals) {
    legacy_request->cluster_low = hidl_request.debugConfigs.clusterIdBottomRangeVal;
    legacy_request->cluster_high = hidl_request.debugConfigs.clusterIdTopRangeVal;
  } else { // need 'else' since not configurable in legacy HAL
    legacy_request->cluster_low = 0x0000;
    legacy_request->cluster_high = 0xFFFF;
  }
  legacy_request->config_intf_addr = hidl_request.debugConfigs.validIntfAddrVal;
  memcpy(legacy_request->intf_addr_val, hidl_request.debugConfigs.intfAddrVal.data(), 6);
  legacy_request->config_oui = hidl_request.debugConfigs.validOuiVal;
  legacy_request->oui_val = hidl_request.debugConfigs.ouiVal;
  legacy_request->config_random_factor_force = hidl_request.debugConfigs.validRandomFactorForceVal;
  legacy_request->random_factor_force_val = hidl_request.debugConfigs.randomFactorForceVal;
  legacy_request->config_hop_count_force = hidl_request.debugConfigs.validHopCountForceVal;
  legacy_request->hop_count_force_val = hidl_request.debugConfigs.hopCountForceVal;
  legacy_request->config_24g_channel = hidl_request.debugConfigs.validDiscoveryChannelVal;
  legacy_request->channel_24g_val =
        hidl_request.debugConfigs.discoveryChannelMhzVal[(size_t) NanBandIndex::NAN_BAND_24GHZ];
  legacy_request->config_5g_channel = hidl_request.debugConfigs.validDiscoveryChannelVal;
  legacy_request->channel_5g_val = hidl_request.debugConfigs
        .discoveryChannelMhzVal[(size_t) NanBandIndex::NAN_BAND_5GHZ];
  legacy_request->config_2dot4g_beacons = hidl_request.debugConfigs.validUseBeaconsInBandVal;
  legacy_request->beacon_2dot4g_val = hidl_request.debugConfigs
        .useBeaconsInBandVal[(size_t) NanBandIndex::NAN_BAND_24GHZ];
  legacy_request->config_5g_beacons = hidl_request.debugConfigs.validUseBeaconsInBandVal;
  legacy_request->beacon_5g_val = hidl_request.debugConfigs
        .useBeaconsInBandVal[(size_t) NanBandIndex::NAN_BAND_5GHZ];
  legacy_request->config_2dot4g_sdf = hidl_request.debugConfigs.validUseSdfInBandVal;
  legacy_request->sdf_2dot4g_val = hidl_request.debugConfigs
        .useSdfInBandVal[(size_t) NanBandIndex::NAN_BAND_24GHZ];
  legacy_request->config_5g_sdf = hidl_request.debugConfigs.validUseSdfInBandVal;
  legacy_request->sdf_5g_val = hidl_request.debugConfigs
        .useSdfInBandVal[(size_t) NanBandIndex::NAN_BAND_5GHZ];

  return true;
}

bool convertHidlNanPublishRequestToLegacy(
    const NanPublishRequest& hidl_request,
    legacy_hal::NanPublishRequest* legacy_request) {
  if (!legacy_request) {
    LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: null legacy_request";
    return false;
  }
  *legacy_request = {};

  legacy_request->publish_id = hidl_request.baseConfigs.sessionId;
  legacy_request->ttl = hidl_request.baseConfigs.ttlSec;
  legacy_request->period = hidl_request.baseConfigs.discoveryWindowPeriod;
  legacy_request->publish_count = hidl_request.baseConfigs.discoveryCount;
  legacy_request->service_name_len = hidl_request.baseConfigs.serviceName.size();
  if (legacy_request->service_name_len > NAN_MAX_SERVICE_NAME_LEN) {
    LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: service_name_len too large";
    return false;
  }
  memcpy(legacy_request->service_name, hidl_request.baseConfigs.serviceName.data(),
        legacy_request->service_name_len);
  legacy_request->publish_match_indicator =
        convertHidlNanMatchAlgToLegacy(hidl_request.baseConfigs.discoveryMatchIndicator);
  legacy_request->service_specific_info_len = hidl_request.baseConfigs.serviceSpecificInfo.size();
  if (legacy_request->service_specific_info_len > NAN_MAX_SERVICE_SPECIFIC_INFO_LEN) {
    LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: service_specific_info_len too large";
    return false;
  }
  memcpy(legacy_request->service_specific_info,
        hidl_request.baseConfigs.serviceSpecificInfo.data(),
        legacy_request->service_specific_info_len);
  legacy_request->sdea_service_specific_info_len =
        hidl_request.baseConfigs.extendedServiceSpecificInfo.size();
  if (legacy_request->sdea_service_specific_info_len > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
    LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: sdea_service_specific_info_len too large";
    return false;
  }
  memcpy(legacy_request->sdea_service_specific_info,
        hidl_request.baseConfigs.extendedServiceSpecificInfo.data(),
        legacy_request->sdea_service_specific_info_len);
  legacy_request->rx_match_filter_len = hidl_request.baseConfigs.rxMatchFilter.size();
  if (legacy_request->rx_match_filter_len > NAN_MAX_MATCH_FILTER_LEN) {
    LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: rx_match_filter_len too large";
    return false;
  }
  memcpy(legacy_request->rx_match_filter,
        hidl_request.baseConfigs.rxMatchFilter.data(),
        legacy_request->rx_match_filter_len);
  legacy_request->tx_match_filter_len = hidl_request.baseConfigs.txMatchFilter.size();
  if (legacy_request->tx_match_filter_len > NAN_MAX_MATCH_FILTER_LEN) {
    LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: tx_match_filter_len too large";
    return false;
  }
  memcpy(legacy_request->tx_match_filter,
        hidl_request.baseConfigs.txMatchFilter.data(),
        legacy_request->tx_match_filter_len);
  legacy_request->rssi_threshold_flag = hidl_request.baseConfigs.useRssiThreshold;
  legacy_request->recv_indication_cfg = 0;
  legacy_request->recv_indication_cfg |=
        hidl_request.baseConfigs.disableDiscoveryTerminationIndication ? 0x1 : 0x0;
  legacy_request->recv_indication_cfg |=
        hidl_request.baseConfigs.disableMatchExpirationIndication ? 0x2 : 0x0;
  legacy_request->recv_indication_cfg |=
        hidl_request.baseConfigs.disableFollowupReceivedIndication ? 0x4 : 0x0;
  legacy_request->recv_indication_cfg |= 0x8;
  legacy_request->cipher_type = (unsigned int) hidl_request.baseConfigs.securityConfig.cipherType;
  if (hidl_request.baseConfigs.securityConfig.securityType == NanDataPathSecurityType::PMK) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PMK;
    legacy_request->key_info.body.pmk_info.pmk_len =
        hidl_request.baseConfigs.securityConfig.pmk.size();
    if (legacy_request->key_info.body.pmk_info.pmk_len != NAN_PMK_INFO_LEN) {
      LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: invalid pmk_len";
      return false;
    }
    memcpy(legacy_request->key_info.body.pmk_info.pmk,
          hidl_request.baseConfigs.securityConfig.pmk.data(),
          legacy_request->key_info.body.pmk_info.pmk_len);
  }
  if (hidl_request.baseConfigs.securityConfig.securityType
        == NanDataPathSecurityType::PASSPHRASE) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PASSPHRASE;
    legacy_request->key_info.body.passphrase_info.passphrase_len =
        hidl_request.baseConfigs.securityConfig.passphrase.size();
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            < NAN_SECURITY_MIN_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: passphrase_len too small";
      return false;
    }
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanPublishRequestToLegacy: passphrase_len too large";
      return false;
    }
    memcpy(legacy_request->key_info.body.passphrase_info.passphrase,
          hidl_request.baseConfigs.securityConfig.passphrase.data(),
          legacy_request->key_info.body.passphrase_info.passphrase_len);
  }
  legacy_request->sdea_params.security_cfg = (hidl_request.baseConfigs.securityConfig.securityType
        != NanDataPathSecurityType::OPEN) ? legacy_hal::NAN_DP_CONFIG_SECURITY
            : legacy_hal::NAN_DP_CONFIG_NO_SECURITY;
  legacy_request->sdea_params.ranging_state = hidl_request.baseConfigs.rangingRequired ?
        legacy_hal::NAN_RANGING_ENABLE : legacy_hal::NAN_RANGING_DISABLE;
  legacy_request->ranging_cfg.ranging_interval_msec = hidl_request.baseConfigs.rangingIntervalMsec;
  legacy_request->ranging_cfg.config_ranging_indications =
        hidl_request.baseConfigs.configRangingIndications;
  legacy_request->ranging_cfg.distance_ingress_cm = hidl_request.baseConfigs.distanceIngressCm;
  legacy_request->ranging_cfg.distance_egress_cm = hidl_request.baseConfigs.distanceEgressCm;
  legacy_request->ranging_auto_response = hidl_request.baseConfigs.rangingRequired ?
        legacy_hal::NAN_RANGING_AUTO_RESPONSE_ENABLE : legacy_hal::NAN_RANGING_AUTO_RESPONSE_DISABLE;
  legacy_request->sdea_params.range_report = legacy_hal::NAN_DISABLE_RANGE_REPORT;
  legacy_request->publish_type = convertHidlNanPublishTypeToLegacy(hidl_request.publishType);
  legacy_request->tx_type = convertHidlNanTxTypeToLegacy(hidl_request.txType);
  legacy_request->service_responder_policy = hidl_request.autoAcceptDataPathRequests ?
        legacy_hal::NAN_SERVICE_ACCEPT_POLICY_ALL : legacy_hal::NAN_SERVICE_ACCEPT_POLICY_NONE;

  return true;
}

bool convertHidlNanSubscribeRequestToLegacy(
    const NanSubscribeRequest& hidl_request,
    legacy_hal::NanSubscribeRequest* legacy_request) {
  if (!legacy_request) {
    LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: legacy_request is null";
    return false;
  }
  *legacy_request = {};

  legacy_request->subscribe_id = hidl_request.baseConfigs.sessionId;
  legacy_request->ttl = hidl_request.baseConfigs.ttlSec;
  legacy_request->period = hidl_request.baseConfigs.discoveryWindowPeriod;
  legacy_request->subscribe_count = hidl_request.baseConfigs.discoveryCount;
  legacy_request->service_name_len = hidl_request.baseConfigs.serviceName.size();
  if (legacy_request->service_name_len > NAN_MAX_SERVICE_NAME_LEN) {
    LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: service_name_len too large";
    return false;
  }
  memcpy(legacy_request->service_name, hidl_request.baseConfigs.serviceName.data(),
        legacy_request->service_name_len);
  legacy_request->subscribe_match_indicator =
        convertHidlNanMatchAlgToLegacy(hidl_request.baseConfigs.discoveryMatchIndicator);
  legacy_request->service_specific_info_len = hidl_request.baseConfigs.serviceSpecificInfo.size();
  if (legacy_request->service_specific_info_len > NAN_MAX_SERVICE_SPECIFIC_INFO_LEN) {
    LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: service_specific_info_len too large";
    return false;
  }
  memcpy(legacy_request->service_specific_info,
        hidl_request.baseConfigs.serviceSpecificInfo.data(),
        legacy_request->service_specific_info_len);
  legacy_request->sdea_service_specific_info_len =
        hidl_request.baseConfigs.extendedServiceSpecificInfo.size();
  if (legacy_request->sdea_service_specific_info_len > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
    LOG(ERROR) <<
        "convertHidlNanSubscribeRequestToLegacy: sdea_service_specific_info_len too large";
    return false;
  }
  memcpy(legacy_request->sdea_service_specific_info,
        hidl_request.baseConfigs.extendedServiceSpecificInfo.data(),
        legacy_request->sdea_service_specific_info_len);
  legacy_request->rx_match_filter_len = hidl_request.baseConfigs.rxMatchFilter.size();
  if (legacy_request->rx_match_filter_len > NAN_MAX_MATCH_FILTER_LEN) {
    LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: rx_match_filter_len too large";
    return false;
  }
  memcpy(legacy_request->rx_match_filter,
        hidl_request.baseConfigs.rxMatchFilter.data(),
        legacy_request->rx_match_filter_len);
  legacy_request->tx_match_filter_len = hidl_request.baseConfigs.txMatchFilter.size();
  if (legacy_request->tx_match_filter_len > NAN_MAX_MATCH_FILTER_LEN) {
    LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: tx_match_filter_len too large";
    return false;
  }
  memcpy(legacy_request->tx_match_filter,
        hidl_request.baseConfigs.txMatchFilter.data(),
        legacy_request->tx_match_filter_len);
  legacy_request->rssi_threshold_flag = hidl_request.baseConfigs.useRssiThreshold;
  legacy_request->recv_indication_cfg = 0;
  legacy_request->recv_indication_cfg |=
        hidl_request.baseConfigs.disableDiscoveryTerminationIndication ? 0x1 : 0x0;
  legacy_request->recv_indication_cfg |=
        hidl_request.baseConfigs.disableMatchExpirationIndication ? 0x2 : 0x0;
  legacy_request->recv_indication_cfg |=
        hidl_request.baseConfigs.disableFollowupReceivedIndication ? 0x4 : 0x0;
  legacy_request->cipher_type = (unsigned int) hidl_request.baseConfigs.securityConfig.cipherType;
  if (hidl_request.baseConfigs.securityConfig.securityType == NanDataPathSecurityType::PMK) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PMK;
    legacy_request->key_info.body.pmk_info.pmk_len =
        hidl_request.baseConfigs.securityConfig.pmk.size();
    if (legacy_request->key_info.body.pmk_info.pmk_len != NAN_PMK_INFO_LEN) {
      LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: invalid pmk_len";
      return false;
    }
    memcpy(legacy_request->key_info.body.pmk_info.pmk,
          hidl_request.baseConfigs.securityConfig.pmk.data(),
          legacy_request->key_info.body.pmk_info.pmk_len);
  }
  if (hidl_request.baseConfigs.securityConfig.securityType == NanDataPathSecurityType::PASSPHRASE) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PASSPHRASE;
    legacy_request->key_info.body.passphrase_info.passphrase_len =
        hidl_request.baseConfigs.securityConfig.passphrase.size();
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            < NAN_SECURITY_MIN_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: passphrase_len too small";
      return false;
    }
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: passphrase_len too large";
      return false;
    }
    memcpy(legacy_request->key_info.body.passphrase_info.passphrase,
          hidl_request.baseConfigs.securityConfig.passphrase.data(),
          legacy_request->key_info.body.passphrase_info.passphrase_len);
  }
  legacy_request->sdea_params.security_cfg = (hidl_request.baseConfigs.securityConfig.securityType
          != NanDataPathSecurityType::OPEN) ? legacy_hal::NAN_DP_CONFIG_SECURITY
              : legacy_hal::NAN_DP_CONFIG_NO_SECURITY;
  legacy_request->sdea_params.ranging_state = hidl_request.baseConfigs.rangingRequired ?
        legacy_hal::NAN_RANGING_ENABLE : legacy_hal::NAN_RANGING_DISABLE;
  legacy_request->ranging_cfg.ranging_interval_msec = hidl_request.baseConfigs.rangingIntervalMsec;
  legacy_request->ranging_cfg.config_ranging_indications =
        hidl_request.baseConfigs.configRangingIndications;
  legacy_request->ranging_cfg.distance_ingress_cm = hidl_request.baseConfigs.distanceIngressCm;
  legacy_request->ranging_cfg.distance_egress_cm = hidl_request.baseConfigs.distanceEgressCm;
  legacy_request->ranging_auto_response = hidl_request.baseConfigs.rangingRequired ?
        legacy_hal::NAN_RANGING_AUTO_RESPONSE_ENABLE : legacy_hal::NAN_RANGING_AUTO_RESPONSE_DISABLE;
  legacy_request->sdea_params.range_report = legacy_hal::NAN_DISABLE_RANGE_REPORT;
  legacy_request->subscribe_type = convertHidlNanSubscribeTypeToLegacy(hidl_request.subscribeType);
  legacy_request->serviceResponseFilter = convertHidlNanSrfTypeToLegacy(hidl_request.srfType);
  legacy_request->serviceResponseInclude = hidl_request.srfRespondIfInAddressSet ?
        legacy_hal::NAN_SRF_INCLUDE_RESPOND : legacy_hal::NAN_SRF_INCLUDE_DO_NOT_RESPOND;
  legacy_request->useServiceResponseFilter = hidl_request.shouldUseSrf ?
        legacy_hal::NAN_USE_SRF : legacy_hal::NAN_DO_NOT_USE_SRF;
  legacy_request->ssiRequiredForMatchIndication = hidl_request.isSsiRequiredForMatch ?
        legacy_hal::NAN_SSI_REQUIRED_IN_MATCH_IND : legacy_hal::NAN_SSI_NOT_REQUIRED_IN_MATCH_IND;
  legacy_request->num_intf_addr_present = hidl_request.intfAddr.size();
  if (legacy_request->num_intf_addr_present > NAN_MAX_SUBSCRIBE_MAX_ADDRESS) {
    LOG(ERROR) << "convertHidlNanSubscribeRequestToLegacy: num_intf_addr_present - too many";
    return false;
  }
  for (int i = 0; i < legacy_request->num_intf_addr_present; i++) {
    memcpy(legacy_request->intf_addr[i], hidl_request.intfAddr[i].data(), 6);
  }

  return true;
}

bool convertHidlNanTransmitFollowupRequestToLegacy(
    const NanTransmitFollowupRequest& hidl_request,
    legacy_hal::NanTransmitFollowupRequest* legacy_request) {
  if (!legacy_request) {
    LOG(ERROR) << "convertHidlNanTransmitFollowupRequestToLegacy: legacy_request is null";
    return false;
  }
  *legacy_request = {};

  legacy_request->publish_subscribe_id = hidl_request.discoverySessionId;
  legacy_request->requestor_instance_id = hidl_request.peerId;
  memcpy(legacy_request->addr, hidl_request.addr.data(), 6);
  legacy_request->priority = hidl_request.isHighPriority ?
        legacy_hal::NAN_TX_PRIORITY_HIGH : legacy_hal::NAN_TX_PRIORITY_NORMAL;
  legacy_request->dw_or_faw = hidl_request.shouldUseDiscoveryWindow ?
        legacy_hal::NAN_TRANSMIT_IN_DW : legacy_hal::NAN_TRANSMIT_IN_FAW;
  legacy_request->service_specific_info_len = hidl_request.serviceSpecificInfo.size();
  if (legacy_request->service_specific_info_len > NAN_MAX_SERVICE_SPECIFIC_INFO_LEN) {
    LOG(ERROR) <<
        "convertHidlNanTransmitFollowupRequestToLegacy: service_specific_info_len too large";
    return false;
  }
  memcpy(legacy_request->service_specific_info,
        hidl_request.serviceSpecificInfo.data(),
        legacy_request->service_specific_info_len);
  legacy_request->sdea_service_specific_info_len = hidl_request.extendedServiceSpecificInfo.size();
  if (legacy_request->sdea_service_specific_info_len > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
    LOG(ERROR) <<
        "convertHidlNanTransmitFollowupRequestToLegacy: sdea_service_specific_info_len too large";
    return false;
  }
  memcpy(legacy_request->sdea_service_specific_info,
        hidl_request.extendedServiceSpecificInfo.data(),
        legacy_request->sdea_service_specific_info_len);
  legacy_request->recv_indication_cfg = hidl_request.disableFollowupResultIndication ? 0x1 : 0x0;

  return true;
}

bool convertHidlNanConfigRequestToLegacy(
    const NanConfigRequest& hidl_request,
    legacy_hal::NanConfigRequest* legacy_request) {
  if (!legacy_request) {
    LOG(ERROR) << "convertHidlNanConfigRequestToLegacy: legacy_request is null";
    return false;
  }
  *legacy_request = {};

  // TODO: b/34059183 tracks missing configurations in legacy HAL or uknown defaults
  legacy_request->master_pref = hidl_request.masterPref;
  legacy_request->discovery_indication_cfg = 0;
  legacy_request->discovery_indication_cfg |=
        hidl_request.disableDiscoveryAddressChangeIndication ? 0x1 : 0x0;
  legacy_request->discovery_indication_cfg |=
        hidl_request.disableStartedClusterIndication ? 0x2 : 0x0;
  legacy_request->discovery_indication_cfg |=
        hidl_request.disableJoinedClusterIndication ? 0x4 : 0x0;
  legacy_request->config_sid_beacon = 1;
  if (hidl_request.numberOfPublishServiceIdsInBeacon > 127) {
    LOG(ERROR) << "convertHidlNanConfigRequestToLegacy: numberOfPublishServiceIdsInBeacon > 127";
    return false;
  }
  legacy_request->sid_beacon = (hidl_request.includePublishServiceIdsInBeacon ? 0x1 : 0x0)
        | (hidl_request.numberOfPublishServiceIdsInBeacon << 1);
  legacy_request->config_subscribe_sid_beacon = 1;
  if (hidl_request.numberOfSubscribeServiceIdsInBeacon > 127) {
    LOG(ERROR) << "convertHidlNanConfigRequestToLegacy: numberOfSubscribeServiceIdsInBeacon > 127";
    return false;
  }
  legacy_request->subscribe_sid_beacon_val =
        (hidl_request.includeSubscribeServiceIdsInBeacon ? 0x1 : 0x0)
            | (hidl_request.numberOfSubscribeServiceIdsInBeacon << 1);
  legacy_request->config_rssi_window_size = 1;
  legacy_request->rssi_window_size_val = hidl_request.rssiWindowSize;
  legacy_request->config_disc_mac_addr_randomization = 1;
  legacy_request->disc_mac_addr_rand_interval_sec =
        hidl_request.macAddressRandomizationIntervalSec;
  /* TODO : missing
  legacy_request->config_2dot4g_rssi_close = 1;
  legacy_request->rssi_close_2dot4g_val =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].rssiClose;
  legacy_request->config_2dot4g_rssi_middle = 1;
  legacy_request->rssi_middle_2dot4g_val =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].rssiMiddle;
  legacy_request->config_2dot4g_rssi_proximity = 1;
  legacy_request->rssi_proximity_2dot4g_val =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].rssiCloseProximity;
  */
  legacy_request->config_scan_params = 1;
  legacy_request->scan_params_val.dwell_time[legacy_hal::NAN_CHANNEL_24G_BAND] =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].dwellTimeMs;
  legacy_request->scan_params_val.scan_period[legacy_hal::NAN_CHANNEL_24G_BAND] =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_24GHZ].scanPeriodSec;
  legacy_request->config_dw.config_2dot4g_dw_band = hidl_request
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_24GHZ].validDiscoveryWindowIntervalVal;
  legacy_request->config_dw.dw_2dot4g_interval_val = hidl_request
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_24GHZ].discoveryWindowIntervalVal;
  /* TODO: missing
  legacy_request->config_5g_rssi_close = 1;
  legacy_request->rssi_close_5g_val =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].rssiClose;
  legacy_request->config_5g_rssi_middle = 1;
  legacy_request->rssi_middle_5g_val =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].rssiMiddle;
  */
  legacy_request->config_5g_rssi_close_proximity = 1;
  legacy_request->rssi_close_proximity_5g_val =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].rssiCloseProximity;
  legacy_request->scan_params_val.dwell_time[legacy_hal::NAN_CHANNEL_5G_BAND_LOW] =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].dwellTimeMs;
  legacy_request->scan_params_val.scan_period[legacy_hal::NAN_CHANNEL_5G_BAND_LOW] =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].scanPeriodSec;
  legacy_request->scan_params_val.dwell_time[legacy_hal::NAN_CHANNEL_5G_BAND_HIGH] =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].dwellTimeMs;
  legacy_request->scan_params_val.scan_period[legacy_hal::NAN_CHANNEL_5G_BAND_HIGH] =
        hidl_request.bandSpecificConfig[
            (size_t) NanBandIndex::NAN_BAND_5GHZ].scanPeriodSec;
  legacy_request->config_dw.config_5g_dw_band = hidl_request
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_5GHZ].validDiscoveryWindowIntervalVal;
  legacy_request->config_dw.dw_5g_interval_val = hidl_request
        .bandSpecificConfig[(size_t) NanBandIndex::NAN_BAND_5GHZ].discoveryWindowIntervalVal;

  return true;
}

bool convertHidlNanDataPathInitiatorRequestToLegacy(
    const NanInitiateDataPathRequest& hidl_request,
    legacy_hal::NanDataPathInitiatorRequest* legacy_request) {
  if (!legacy_request) {
    LOG(ERROR) << "convertHidlNanDataPathInitiatorRequestToLegacy: legacy_request is null";
    return false;
  }
  *legacy_request = {};

  legacy_request->requestor_instance_id = hidl_request.peerId;
  memcpy(legacy_request->peer_disc_mac_addr, hidl_request.peerDiscMacAddr.data(), 6);
  legacy_request->channel_request_type =
        convertHidlNanDataPathChannelCfgToLegacy(hidl_request.channelRequestType);
  legacy_request->channel = hidl_request.channel;
  strcpy(legacy_request->ndp_iface, hidl_request.ifaceName.c_str());
  legacy_request->ndp_cfg.security_cfg = (hidl_request.securityConfig.securityType
        != NanDataPathSecurityType::OPEN) ? legacy_hal::NAN_DP_CONFIG_SECURITY
            : legacy_hal::NAN_DP_CONFIG_NO_SECURITY;
  legacy_request->app_info.ndp_app_info_len = hidl_request.appInfo.size();
  if (legacy_request->app_info.ndp_app_info_len > NAN_DP_MAX_APP_INFO_LEN) {
    LOG(ERROR) << "convertHidlNanDataPathInitiatorRequestToLegacy: ndp_app_info_len too large";
    return false;
  }
  memcpy(legacy_request->app_info.ndp_app_info, hidl_request.appInfo.data(),
        legacy_request->app_info.ndp_app_info_len);
  legacy_request->cipher_type = (unsigned int) hidl_request.securityConfig.cipherType;
  if (hidl_request.securityConfig.securityType == NanDataPathSecurityType::PMK) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PMK;
    legacy_request->key_info.body.pmk_info.pmk_len = hidl_request.securityConfig.pmk.size();
    if (legacy_request->key_info.body.pmk_info.pmk_len != NAN_PMK_INFO_LEN) {
      LOG(ERROR) << "convertHidlNanDataPathInitiatorRequestToLegacy: invalid pmk_len";
      return false;
    }
    memcpy(legacy_request->key_info.body.pmk_info.pmk,
          hidl_request.securityConfig.pmk.data(),
          legacy_request->key_info.body.pmk_info.pmk_len);
  }
  if (hidl_request.securityConfig.securityType == NanDataPathSecurityType::PASSPHRASE) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PASSPHRASE;
    legacy_request->key_info.body.passphrase_info.passphrase_len =
        hidl_request.securityConfig.passphrase.size();
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            < NAN_SECURITY_MIN_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanDataPathInitiatorRequestToLegacy: passphrase_len too small";
      return false;
    }
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanDataPathInitiatorRequestToLegacy: passphrase_len too large";
      return false;
    }
    memcpy(legacy_request->key_info.body.passphrase_info.passphrase,
          hidl_request.securityConfig.passphrase.data(),
          legacy_request->key_info.body.passphrase_info.passphrase_len);
  }
  legacy_request->service_name_len = hidl_request.serviceNameOutOfBand.size();
  if (legacy_request->service_name_len > NAN_MAX_SERVICE_NAME_LEN) {
    LOG(ERROR) << "convertHidlNanDataPathInitiatorRequestToLegacy: service_name_len too large";
    return false;
  }
  memcpy(legacy_request->service_name, hidl_request.serviceNameOutOfBand.data(),
        legacy_request->service_name_len);

  return true;
}

bool convertHidlNanDataPathIndicationResponseToLegacy(
    const NanRespondToDataPathIndicationRequest& hidl_request,
    legacy_hal::NanDataPathIndicationResponse* legacy_request) {
  if (!legacy_request) {
    LOG(ERROR) << "convertHidlNanDataPathIndicationResponseToLegacy: legacy_request is null";
    return false;
  }
  *legacy_request = {};

  legacy_request->rsp_code = hidl_request.acceptRequest ?
        legacy_hal::NAN_DP_REQUEST_ACCEPT : legacy_hal::NAN_DP_REQUEST_REJECT;
  legacy_request->ndp_instance_id = hidl_request.ndpInstanceId;
  strcpy(legacy_request->ndp_iface, hidl_request.ifaceName.c_str());
  legacy_request->ndp_cfg.security_cfg = (hidl_request.securityConfig.securityType
        != NanDataPathSecurityType::OPEN) ? legacy_hal::NAN_DP_CONFIG_SECURITY
            : legacy_hal::NAN_DP_CONFIG_NO_SECURITY;
  legacy_request->app_info.ndp_app_info_len = hidl_request.appInfo.size();
  if (legacy_request->app_info.ndp_app_info_len > NAN_DP_MAX_APP_INFO_LEN) {
    LOG(ERROR) << "convertHidlNanDataPathIndicationResponseToLegacy: ndp_app_info_len too large";
    return false;
  }
  memcpy(legacy_request->app_info.ndp_app_info, hidl_request.appInfo.data(),
        legacy_request->app_info.ndp_app_info_len);
  legacy_request->cipher_type = (unsigned int) hidl_request.securityConfig.cipherType;
  if (hidl_request.securityConfig.securityType == NanDataPathSecurityType::PMK) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PMK;
    legacy_request->key_info.body.pmk_info.pmk_len = hidl_request.securityConfig.pmk.size();
    if (legacy_request->key_info.body.pmk_info.pmk_len != NAN_PMK_INFO_LEN) {
      LOG(ERROR) << "convertHidlNanDataPathIndicationResponseToLegacy: invalid pmk_len";
      return false;
    }
    memcpy(legacy_request->key_info.body.pmk_info.pmk,
          hidl_request.securityConfig.pmk.data(),
          legacy_request->key_info.body.pmk_info.pmk_len);
  }
  if (hidl_request.securityConfig.securityType == NanDataPathSecurityType::PASSPHRASE) {
    legacy_request->key_info.key_type = legacy_hal::NAN_SECURITY_KEY_INPUT_PASSPHRASE;
    legacy_request->key_info.body.passphrase_info.passphrase_len =
        hidl_request.securityConfig.passphrase.size();
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            < NAN_SECURITY_MIN_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanDataPathIndicationResponseToLegacy: passphrase_len too small";
      return false;
    }
    if (legacy_request->key_info.body.passphrase_info.passphrase_len
            > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
      LOG(ERROR) << "convertHidlNanDataPathIndicationResponseToLegacy: passphrase_len too large";
      return false;
    }
    memcpy(legacy_request->key_info.body.passphrase_info.passphrase,
          hidl_request.securityConfig.passphrase.data(),
          legacy_request->key_info.body.passphrase_info.passphrase_len);
  }
  legacy_request->service_name_len = hidl_request.serviceNameOutOfBand.size();
  if (legacy_request->service_name_len > NAN_MAX_SERVICE_NAME_LEN) {
    LOG(ERROR) << "convertHidlNanDataPathIndicationResponseToLegacy: service_name_len too large";
    return false;
  }
  memcpy(legacy_request->service_name, hidl_request.serviceNameOutOfBand.data(),
        legacy_request->service_name_len);

  return true;
}

bool convertLegacyNanResponseHeaderToHidl(
    const legacy_hal::NanResponseMsg& legacy_response,
    WifiNanStatus* wifiNanStatus) {
  if (!wifiNanStatus) {
    LOG(ERROR) << "convertLegacyNanResponseHeaderToHidl: wifiNanStatus is null";
    return false;
  }
  *wifiNanStatus = {};

  convertToWifiNanStatus(legacy_response.status, legacy_response.nan_error,
        sizeof(legacy_response.nan_error), wifiNanStatus);
  return true;
}

bool convertLegacyNanCapabilitiesResponseToHidl(
    const legacy_hal::NanCapabilities& legacy_response,
    NanCapabilities* hidl_response) {
  if (!hidl_response) {
    LOG(ERROR) << "convertLegacyNanCapabilitiesResponseToHidl: hidl_response is null";
    return false;
  }
  *hidl_response = {};

  hidl_response->maxConcurrentClusters = legacy_response.max_concurrent_nan_clusters;
  hidl_response->maxPublishes = legacy_response.max_publishes;
  hidl_response->maxSubscribes = legacy_response.max_subscribes;
  hidl_response->maxServiceNameLen = legacy_response.max_service_name_len;
  hidl_response->maxMatchFilterLen = legacy_response.max_match_filter_len;
  hidl_response->maxTotalMatchFilterLen = legacy_response.max_total_match_filter_len;
  hidl_response->maxServiceSpecificInfoLen = legacy_response.max_service_specific_info_len;
  hidl_response->maxExtendedServiceSpecificInfoLen =
    legacy_response.max_sdea_service_specific_info_len;
  hidl_response->maxNdiInterfaces = legacy_response.max_ndi_interfaces;
  hidl_response->maxNdpSessions = legacy_response.max_ndp_sessions;
  hidl_response->maxAppInfoLen = legacy_response.max_app_info_len;
  hidl_response->maxQueuedTransmitFollowupMsgs = legacy_response.max_queued_transmit_followup_msgs;
  hidl_response->maxSubscribeInterfaceAddresses = legacy_response.max_subscribe_address;
  hidl_response->supportedCipherSuites = legacy_response.cipher_suites_supported;

  return true;
}

bool convertLegacyNanMatchIndToHidl(
    const legacy_hal::NanMatchInd& legacy_ind,
    NanMatchInd* hidl_ind) {
  if (!hidl_ind) {
    LOG(ERROR) << "convertLegacyNanMatchIndToHidl: hidl_ind is null";
    return false;
  }
  *hidl_ind = {};

  hidl_ind->discoverySessionId = legacy_ind.publish_subscribe_id;
  hidl_ind->peerId = legacy_ind.requestor_instance_id;
  hidl_ind->addr = hidl_array<uint8_t, 6>(legacy_ind.addr);
  hidl_ind->serviceSpecificInfo = std::vector<uint8_t>(legacy_ind.service_specific_info,
        legacy_ind.service_specific_info + legacy_ind.service_specific_info_len);
  hidl_ind->extendedServiceSpecificInfo = std::vector<uint8_t>(
        legacy_ind.sdea_service_specific_info,
        legacy_ind.sdea_service_specific_info + legacy_ind.sdea_service_specific_info_len);
  hidl_ind->matchFilter = std::vector<uint8_t>(legacy_ind.sdf_match_filter,
        legacy_ind.sdf_match_filter + legacy_ind.sdf_match_filter_len);
  hidl_ind->matchOccuredInBeaconFlag = legacy_ind.match_occured_flag == 1;
  hidl_ind->outOfResourceFlag = legacy_ind.out_of_resource_flag == 1;
  hidl_ind->rssiValue = legacy_ind.rssi_value;
  hidl_ind->peerCipherType = (NanCipherSuiteType) legacy_ind.peer_cipher_type;
  hidl_ind->peerRequiresSecurityEnabledInNdp =
        legacy_ind.peer_sdea_params.security_cfg == legacy_hal::NAN_DP_CONFIG_SECURITY;
  hidl_ind->peerRequiresRanging =
        legacy_ind.peer_sdea_params.ranging_state == legacy_hal::NAN_RANGING_ENABLE;
  hidl_ind->rangingMeasurementInCm = legacy_ind.range_info.range_measurement_cm;
  hidl_ind->rangingIndicationType = legacy_ind.range_info.ranging_event_type;

  return true;
}

bool convertLegacyNanFollowupIndToHidl(
    const legacy_hal::NanFollowupInd& legacy_ind,
    NanFollowupReceivedInd* hidl_ind) {
  if (!hidl_ind) {
    LOG(ERROR) << "convertLegacyNanFollowupIndToHidl: hidl_ind is null";
    return false;
  }
  *hidl_ind = {};

  hidl_ind->discoverySessionId = legacy_ind.publish_subscribe_id;
  hidl_ind->peerId = legacy_ind.requestor_instance_id;
  hidl_ind->addr = hidl_array<uint8_t, 6>(legacy_ind.addr);
  hidl_ind->receivedInFaw = legacy_ind.dw_or_faw == 1;
  hidl_ind->serviceSpecificInfo = std::vector<uint8_t>(legacy_ind.service_specific_info,
        legacy_ind.service_specific_info + legacy_ind.service_specific_info_len);
  hidl_ind->extendedServiceSpecificInfo = std::vector<uint8_t>(
        legacy_ind.sdea_service_specific_info,
        legacy_ind.sdea_service_specific_info + legacy_ind.sdea_service_specific_info_len);

  return true;
}

bool convertLegacyNanDataPathRequestIndToHidl(
    const legacy_hal::NanDataPathRequestInd& legacy_ind,
    NanDataPathRequestInd* hidl_ind) {
  if (!hidl_ind) {
    LOG(ERROR) << "convertLegacyNanDataPathRequestIndToHidl: hidl_ind is null";
    return false;
  }
  *hidl_ind = {};

  hidl_ind->discoverySessionId = legacy_ind.service_instance_id;
  hidl_ind->peerDiscMacAddr = hidl_array<uint8_t, 6>(legacy_ind.peer_disc_mac_addr);
  hidl_ind->ndpInstanceId = legacy_ind.ndp_instance_id;
  hidl_ind->securityRequired =
        legacy_ind.ndp_cfg.security_cfg == legacy_hal::NAN_DP_CONFIG_SECURITY;
  hidl_ind->appInfo = std::vector<uint8_t>(legacy_ind.app_info.ndp_app_info,
        legacy_ind.app_info.ndp_app_info + legacy_ind.app_info.ndp_app_info_len);

  return true;
}

bool convertLegacyNanDataPathConfirmIndToHidl(
    const legacy_hal::NanDataPathConfirmInd& legacy_ind,
    NanDataPathConfirmInd* hidl_ind) {
  if (!hidl_ind) {
    LOG(ERROR) << "convertLegacyNanDataPathConfirmIndToHidl: hidl_ind is null";
    return false;
  }
  *hidl_ind = {};

  hidl_ind->ndpInstanceId = legacy_ind.ndp_instance_id;
  hidl_ind->dataPathSetupSuccess = legacy_ind.rsp_code == legacy_hal::NAN_DP_REQUEST_ACCEPT;
  hidl_ind->peerNdiMacAddr = hidl_array<uint8_t, 6>(legacy_ind.peer_ndi_mac_addr);
  hidl_ind->appInfo = std::vector<uint8_t>(legacy_ind.app_info.ndp_app_info,
          legacy_ind.app_info.ndp_app_info + legacy_ind.app_info.ndp_app_info_len);
  hidl_ind->status.status = convertLegacyNanStatusTypeToHidl(legacy_ind.reason_code);
  hidl_ind->status.description = ""; // TODO: b/34059183

  return true;
}

legacy_hal::wifi_rtt_type convertHidlRttTypeToLegacy(RttType type) {
  switch (type) {
    case RttType::ONE_SIDED:
      return legacy_hal::RTT_TYPE_1_SIDED;
    case RttType::TWO_SIDED:
      return legacy_hal::RTT_TYPE_2_SIDED;
  };
  CHECK(false);
}

RttType convertLegacyRttTypeToHidl(legacy_hal::wifi_rtt_type type) {
  switch (type) {
    case legacy_hal::RTT_TYPE_1_SIDED:
      return RttType::ONE_SIDED;
    case legacy_hal::RTT_TYPE_2_SIDED:
      return RttType::TWO_SIDED;
  };
  CHECK(false) << "Unknown legacy type: " << type;
}

legacy_hal::rtt_peer_type convertHidlRttPeerTypeToLegacy(RttPeerType type) {
  switch (type) {
    case RttPeerType::AP:
      return legacy_hal::RTT_PEER_AP;
    case RttPeerType::STA:
      return legacy_hal::RTT_PEER_STA;
    case RttPeerType::P2P_GO:
      return legacy_hal::RTT_PEER_P2P_GO;
    case RttPeerType::P2P_CLIENT:
      return legacy_hal::RTT_PEER_P2P_CLIENT;
    case RttPeerType::NAN:
      return legacy_hal::RTT_PEER_NAN;
  };
  CHECK(false);
}

legacy_hal::wifi_channel_width convertHidlWifiChannelWidthToLegacy(
    WifiChannelWidthInMhz type) {
  switch (type) {
    case WifiChannelWidthInMhz::WIDTH_20:
      return legacy_hal::WIFI_CHAN_WIDTH_20;
    case WifiChannelWidthInMhz::WIDTH_40:
      return legacy_hal::WIFI_CHAN_WIDTH_40;
    case WifiChannelWidthInMhz::WIDTH_80:
      return legacy_hal::WIFI_CHAN_WIDTH_80;
    case WifiChannelWidthInMhz::WIDTH_160:
      return legacy_hal::WIFI_CHAN_WIDTH_160;
    case WifiChannelWidthInMhz::WIDTH_80P80:
      return legacy_hal::WIFI_CHAN_WIDTH_80P80;
    case WifiChannelWidthInMhz::WIDTH_5:
      return legacy_hal::WIFI_CHAN_WIDTH_5;
    case WifiChannelWidthInMhz::WIDTH_10:
      return legacy_hal::WIFI_CHAN_WIDTH_10;
    case WifiChannelWidthInMhz::WIDTH_INVALID:
      return legacy_hal::WIFI_CHAN_WIDTH_INVALID;
  };
  CHECK(false);
}

WifiChannelWidthInMhz convertLegacyWifiChannelWidthToHidl(
    legacy_hal::wifi_channel_width type) {
  switch (type) {
    case legacy_hal::WIFI_CHAN_WIDTH_20:
      return WifiChannelWidthInMhz::WIDTH_20;
    case legacy_hal::WIFI_CHAN_WIDTH_40:
      return WifiChannelWidthInMhz::WIDTH_40;
    case legacy_hal::WIFI_CHAN_WIDTH_80:
      return WifiChannelWidthInMhz::WIDTH_80;
    case legacy_hal::WIFI_CHAN_WIDTH_160:
      return WifiChannelWidthInMhz::WIDTH_160;
    case legacy_hal::WIFI_CHAN_WIDTH_80P80:
      return WifiChannelWidthInMhz::WIDTH_80P80;
    case legacy_hal::WIFI_CHAN_WIDTH_5:
      return WifiChannelWidthInMhz::WIDTH_5;
    case legacy_hal::WIFI_CHAN_WIDTH_10:
      return WifiChannelWidthInMhz::WIDTH_10;
    case legacy_hal::WIFI_CHAN_WIDTH_INVALID:
      return WifiChannelWidthInMhz::WIDTH_INVALID;
  };
  CHECK(false) << "Unknown legacy type: " << type;
}

legacy_hal::wifi_rtt_preamble convertHidlRttPreambleToLegacy(RttPreamble type) {
  switch (type) {
    case RttPreamble::LEGACY:
      return legacy_hal::WIFI_RTT_PREAMBLE_LEGACY;
    case RttPreamble::HT:
      return legacy_hal::WIFI_RTT_PREAMBLE_HT;
    case RttPreamble::VHT:
      return legacy_hal::WIFI_RTT_PREAMBLE_VHT;
  };
  CHECK(false);
}

RttPreamble convertLegacyRttPreambleToHidl(legacy_hal::wifi_rtt_preamble type) {
  switch (type) {
    case legacy_hal::WIFI_RTT_PREAMBLE_LEGACY:
      return RttPreamble::LEGACY;
    case legacy_hal::WIFI_RTT_PREAMBLE_HT:
      return RttPreamble::HT;
    case legacy_hal::WIFI_RTT_PREAMBLE_VHT:
      return RttPreamble::VHT;
  };
  CHECK(false) << "Unknown legacy type: " << type;
}

legacy_hal::wifi_rtt_bw convertHidlRttBwToLegacy(RttBw type) {
  switch (type) {
    case RttBw::BW_5MHZ:
      return legacy_hal::WIFI_RTT_BW_5;
    case RttBw::BW_10MHZ:
      return legacy_hal::WIFI_RTT_BW_10;
    case RttBw::BW_20MHZ:
      return legacy_hal::WIFI_RTT_BW_20;
    case RttBw::BW_40MHZ:
      return legacy_hal::WIFI_RTT_BW_40;
    case RttBw::BW_80MHZ:
      return legacy_hal::WIFI_RTT_BW_80;
    case RttBw::BW_160MHZ:
      return legacy_hal::WIFI_RTT_BW_160;
  };
  CHECK(false);
}

RttBw convertLegacyRttBwToHidl(legacy_hal::wifi_rtt_bw type) {
  switch (type) {
    case legacy_hal::WIFI_RTT_BW_5:
      return RttBw::BW_5MHZ;
    case legacy_hal::WIFI_RTT_BW_10:
      return RttBw::BW_10MHZ;
    case legacy_hal::WIFI_RTT_BW_20:
      return RttBw::BW_20MHZ;
    case legacy_hal::WIFI_RTT_BW_40:
      return RttBw::BW_40MHZ;
    case legacy_hal::WIFI_RTT_BW_80:
      return RttBw::BW_80MHZ;
    case legacy_hal::WIFI_RTT_BW_160:
      return RttBw::BW_160MHZ;
  };
  CHECK(false) << "Unknown legacy type: " << type;
}

legacy_hal::wifi_motion_pattern convertHidlRttMotionPatternToLegacy(
    RttMotionPattern type) {
  switch (type) {
    case RttMotionPattern::NOT_EXPECTED:
      return legacy_hal::WIFI_MOTION_NOT_EXPECTED;
    case RttMotionPattern::EXPECTED:
      return legacy_hal::WIFI_MOTION_EXPECTED;
    case RttMotionPattern::UNKNOWN:
      return legacy_hal::WIFI_MOTION_UNKNOWN;
  };
  CHECK(false);
}

WifiRatePreamble convertLegacyWifiRatePreambleToHidl(uint8_t preamble) {
  switch (preamble) {
    case 0:
      return WifiRatePreamble::OFDM;
    case 1:
      return WifiRatePreamble::CCK;
    case 2:
      return WifiRatePreamble::HT;
    case 3:
      return WifiRatePreamble::VHT;
    default:
      return WifiRatePreamble::RESERVED;
  };
  CHECK(false) << "Unknown legacy preamble: " << preamble;
}

WifiRateNss convertLegacyWifiRateNssToHidl(uint8_t nss) {
  switch (nss) {
    case 0:
      return WifiRateNss::NSS_1x1;
    case 1:
      return WifiRateNss::NSS_2x2;
    case 2:
      return WifiRateNss::NSS_3x3;
    case 3:
      return WifiRateNss::NSS_4x4;
  };
  CHECK(false) << "Unknown legacy nss: " << nss;
  return {};
}

RttStatus convertLegacyRttStatusToHidl(legacy_hal::wifi_rtt_status status) {
  switch (status) {
    case legacy_hal::RTT_STATUS_SUCCESS:
      return RttStatus::SUCCESS;
    case legacy_hal::RTT_STATUS_FAILURE:
      return RttStatus::FAILURE;
    case legacy_hal::RTT_STATUS_FAIL_NO_RSP:
      return RttStatus::FAIL_NO_RSP;
    case legacy_hal::RTT_STATUS_FAIL_REJECTED:
      return RttStatus::FAIL_REJECTED;
    case legacy_hal::RTT_STATUS_FAIL_NOT_SCHEDULED_YET:
      return RttStatus::FAIL_NOT_SCHEDULED_YET;
    case legacy_hal::RTT_STATUS_FAIL_TM_TIMEOUT:
      return RttStatus::FAIL_TM_TIMEOUT;
    case legacy_hal::RTT_STATUS_FAIL_AP_ON_DIFF_CHANNEL:
      return RttStatus::FAIL_AP_ON_DIFF_CHANNEL;
    case legacy_hal::RTT_STATUS_FAIL_NO_CAPABILITY:
      return RttStatus::FAIL_NO_CAPABILITY;
    case legacy_hal::RTT_STATUS_ABORTED:
      return RttStatus::ABORTED;
    case legacy_hal::RTT_STATUS_FAIL_INVALID_TS:
      return RttStatus::FAIL_INVALID_TS;
    case legacy_hal::RTT_STATUS_FAIL_PROTOCOL:
      return RttStatus::FAIL_PROTOCOL;
    case legacy_hal::RTT_STATUS_FAIL_SCHEDULE:
      return RttStatus::FAIL_SCHEDULE;
    case legacy_hal::RTT_STATUS_FAIL_BUSY_TRY_LATER:
      return RttStatus::FAIL_BUSY_TRY_LATER;
    case legacy_hal::RTT_STATUS_INVALID_REQ:
      return RttStatus::INVALID_REQ;
    case legacy_hal::RTT_STATUS_NO_WIFI:
      return RttStatus::NO_WIFI;
    case legacy_hal::RTT_STATUS_FAIL_FTM_PARAM_OVERRIDE:
      return RttStatus::FAIL_FTM_PARAM_OVERRIDE;
  };
  CHECK(false) << "Unknown legacy status: " << status;
}

bool convertHidlWifiChannelInfoToLegacy(
    const WifiChannelInfo& hidl_info,
    legacy_hal::wifi_channel_info* legacy_info) {
  if (!legacy_info) {
    return false;
  }
  *legacy_info = {};
  legacy_info->width = convertHidlWifiChannelWidthToLegacy(hidl_info.width);
  legacy_info->center_freq = hidl_info.centerFreq;
  legacy_info->center_freq0 = hidl_info.centerFreq0;
  legacy_info->center_freq1 = hidl_info.centerFreq1;
  return true;
}

bool convertLegacyWifiChannelInfoToHidl(
    const legacy_hal::wifi_channel_info& legacy_info,
    WifiChannelInfo* hidl_info) {
  if (!hidl_info) {
    return false;
  }
  *hidl_info = {};
  hidl_info->width = convertLegacyWifiChannelWidthToHidl(legacy_info.width);
  hidl_info->centerFreq = legacy_info.center_freq;
  hidl_info->centerFreq0 = legacy_info.center_freq0;
  hidl_info->centerFreq1 = legacy_info.center_freq1;
  return true;
}

bool convertHidlRttConfigToLegacy(const RttConfig& hidl_config,
                                  legacy_hal::wifi_rtt_config* legacy_config) {
  if (!legacy_config) {
    return false;
  }
  *legacy_config = {};
  CHECK(hidl_config.addr.size() == sizeof(legacy_config->addr));
  memcpy(legacy_config->addr, hidl_config.addr.data(), hidl_config.addr.size());
  legacy_config->type = convertHidlRttTypeToLegacy(hidl_config.type);
  legacy_config->peer = convertHidlRttPeerTypeToLegacy(hidl_config.peer);
  if (!convertHidlWifiChannelInfoToLegacy(hidl_config.channel,
                                          &legacy_config->channel)) {
    return false;
  }
  legacy_config->burst_period = hidl_config.burstPeriod;
  legacy_config->num_burst = hidl_config.numBurst;
  legacy_config->num_frames_per_burst = hidl_config.numFramesPerBurst;
  legacy_config->num_retries_per_rtt_frame = hidl_config.numRetriesPerRttFrame;
  legacy_config->num_retries_per_ftmr = hidl_config.numRetriesPerFtmr;
  legacy_config->LCI_request = hidl_config.mustRequestLci;
  legacy_config->LCR_request = hidl_config.mustRequestLcr;
  legacy_config->burst_duration = hidl_config.burstDuration;
  legacy_config->preamble =
      convertHidlRttPreambleToLegacy(hidl_config.preamble);
  legacy_config->bw = convertHidlRttBwToLegacy(hidl_config.bw);
  return true;
}

bool convertHidlVectorOfRttConfigToLegacy(
    const std::vector<RttConfig>& hidl_configs,
    std::vector<legacy_hal::wifi_rtt_config>* legacy_configs) {
  if (!legacy_configs) {
    return false;
  }
  *legacy_configs = {};
  for (const auto& hidl_config : hidl_configs) {
    legacy_hal::wifi_rtt_config legacy_config;
    if (!convertHidlRttConfigToLegacy(hidl_config, &legacy_config)) {
      return false;
    }
    legacy_configs->push_back(legacy_config);
  }
  return true;
}

bool convertHidlRttLciInformationToLegacy(
    const RttLciInformation& hidl_info,
    legacy_hal::wifi_lci_information* legacy_info) {
  if (!legacy_info) {
    return false;
  }
  *legacy_info = {};
  legacy_info->latitude = hidl_info.latitude;
  legacy_info->longitude = hidl_info.longitude;
  legacy_info->altitude = hidl_info.altitude;
  legacy_info->latitude_unc = hidl_info.latitudeUnc;
  legacy_info->longitude_unc = hidl_info.longitudeUnc;
  legacy_info->altitude_unc = hidl_info.altitudeUnc;
  legacy_info->motion_pattern =
      convertHidlRttMotionPatternToLegacy(hidl_info.motionPattern);
  legacy_info->floor = hidl_info.floor;
  legacy_info->height_above_floor = hidl_info.heightAboveFloor;
  legacy_info->height_unc = hidl_info.heightUnc;
  return true;
}

bool convertHidlRttLcrInformationToLegacy(
    const RttLcrInformation& hidl_info,
    legacy_hal::wifi_lcr_information* legacy_info) {
  if (!legacy_info) {
    return false;
  }
  *legacy_info = {};
  CHECK(hidl_info.countryCode.size() == sizeof(legacy_info->country_code));
  memcpy(legacy_info->country_code,
         hidl_info.countryCode.data(),
         hidl_info.countryCode.size());
  if (hidl_info.civicInfo.size() > sizeof(legacy_info->civic_info)) {
    return false;
  }
  legacy_info->length = hidl_info.civicInfo.size();
  memcpy(legacy_info->civic_info,
         hidl_info.civicInfo.c_str(),
         hidl_info.civicInfo.size());
  return true;
}

bool convertHidlRttResponderToLegacy(
    const RttResponder& hidl_responder,
    legacy_hal::wifi_rtt_responder* legacy_responder) {
  if (!legacy_responder) {
    return false;
  }
  *legacy_responder = {};
  if (!convertHidlWifiChannelInfoToLegacy(hidl_responder.channel,
                                          &legacy_responder->channel)) {
    return false;
  }
  legacy_responder->preamble =
      convertHidlRttPreambleToLegacy(hidl_responder.preamble);
  return true;
}

bool convertLegacyRttResponderToHidl(
    const legacy_hal::wifi_rtt_responder& legacy_responder,
    RttResponder* hidl_responder) {
  if (!hidl_responder) {
    return false;
  }
  *hidl_responder = {};
  if (!convertLegacyWifiChannelInfoToHidl(legacy_responder.channel,
                                          &hidl_responder->channel)) {
    return false;
  }
  hidl_responder->preamble =
      convertLegacyRttPreambleToHidl(legacy_responder.preamble);
  return true;
}

bool convertLegacyRttCapabilitiesToHidl(
    const legacy_hal::wifi_rtt_capabilities& legacy_capabilities,
    RttCapabilities* hidl_capabilities) {
  if (!hidl_capabilities) {
    return false;
  }
  *hidl_capabilities = {};
  hidl_capabilities->rttOneSidedSupported =
      legacy_capabilities.rtt_one_sided_supported;
  hidl_capabilities->rttFtmSupported = legacy_capabilities.rtt_ftm_supported;
  hidl_capabilities->lciSupported = legacy_capabilities.lci_support;
  hidl_capabilities->lcrSupported = legacy_capabilities.lcr_support;
  hidl_capabilities->responderSupported =
      legacy_capabilities.responder_supported;
  hidl_capabilities->preambleSupport = 0;
  for (const auto flag : {legacy_hal::WIFI_RTT_PREAMBLE_LEGACY,
                          legacy_hal::WIFI_RTT_PREAMBLE_HT,
                          legacy_hal::WIFI_RTT_PREAMBLE_VHT}) {
    if (legacy_capabilities.preamble_support & flag) {
      hidl_capabilities->preambleSupport |=
          static_cast<std::underlying_type<RttPreamble>::type>(
              convertLegacyRttPreambleToHidl(flag));
    }
  }
  hidl_capabilities->bwSupport = 0;
  for (const auto flag : {legacy_hal::WIFI_RTT_BW_5,
                          legacy_hal::WIFI_RTT_BW_10,
                          legacy_hal::WIFI_RTT_BW_20,
                          legacy_hal::WIFI_RTT_BW_40,
                          legacy_hal::WIFI_RTT_BW_80,
                          legacy_hal::WIFI_RTT_BW_160}) {
    if (legacy_capabilities.bw_support & flag) {
      hidl_capabilities->bwSupport |=
          static_cast<std::underlying_type<RttBw>::type>(
              convertLegacyRttBwToHidl(flag));
    }
  }
  hidl_capabilities->mcVersion = legacy_capabilities.mc_version;
  return true;
}

bool convertLegacyWifiRateInfoToHidl(const legacy_hal::wifi_rate& legacy_rate,
                                     WifiRateInfo* hidl_rate) {
  if (!hidl_rate) {
    return false;
  }
  *hidl_rate = {};
  hidl_rate->preamble =
      convertLegacyWifiRatePreambleToHidl(legacy_rate.preamble);
  hidl_rate->nss = convertLegacyWifiRateNssToHidl(legacy_rate.nss);
  hidl_rate->bw = convertLegacyWifiChannelWidthToHidl(
      static_cast<legacy_hal::wifi_channel_width>(legacy_rate.bw));
  hidl_rate->rateMcsIdx = legacy_rate.rateMcsIdx;
  hidl_rate->bitRateInKbps = legacy_rate.bitrate;
  return true;
}

bool convertLegacyRttResultToHidl(
    const legacy_hal::wifi_rtt_result& legacy_result, RttResult* hidl_result) {
  if (!hidl_result) {
    return false;
  }
  *hidl_result = {};
  CHECK(sizeof(legacy_result.addr) == hidl_result->addr.size());
  memcpy(
      hidl_result->addr.data(), legacy_result.addr, sizeof(legacy_result.addr));
  hidl_result->burstNum = legacy_result.burst_num;
  hidl_result->measurementNumber = legacy_result.measurement_number;
  hidl_result->successNumber = legacy_result.success_number;
  hidl_result->numberPerBurstPeer = legacy_result.number_per_burst_peer;
  hidl_result->status = convertLegacyRttStatusToHidl(legacy_result.status);
  hidl_result->retryAfterDuration = legacy_result.retry_after_duration;
  hidl_result->type = convertLegacyRttTypeToHidl(legacy_result.type);
  hidl_result->rssi = legacy_result.rssi;
  hidl_result->rssiSpread = legacy_result.rssi_spread;
  if (!convertLegacyWifiRateInfoToHidl(legacy_result.tx_rate,
                                       &hidl_result->txRate)) {
    return false;
  }
  if (!convertLegacyWifiRateInfoToHidl(legacy_result.rx_rate,
                                       &hidl_result->rxRate)) {
    return false;
  }
  hidl_result->rtt = legacy_result.rtt;
  hidl_result->rttSd = legacy_result.rtt_sd;
  hidl_result->rttSpread = legacy_result.rtt_spread;
  hidl_result->distanceInMm = legacy_result.distance_mm;
  hidl_result->distanceSdInMm = legacy_result.distance_sd_mm;
  hidl_result->distanceSpreadInMm = legacy_result.distance_spread_mm;
  hidl_result->timeStampInUs = legacy_result.ts;
  hidl_result->burstDurationInMs = legacy_result.burst_duration;
  hidl_result->negotiatedBurstNum = legacy_result.negotiated_burst_num;
  if (legacy_result.LCI && !convertLegacyIeToHidl(*legacy_result.LCI,
                                                  &hidl_result->lci)) {
    return false;
  }
  if (legacy_result.LCR && !convertLegacyIeToHidl(*legacy_result.LCR,
                                                  &hidl_result->lcr)) {
    return false;
  }
  return true;
}

bool convertLegacyVectorOfRttResultToHidl(
    const std::vector<const legacy_hal::wifi_rtt_result*>& legacy_results,
    std::vector<RttResult>* hidl_results) {
  if (!hidl_results) {
    return false;
  }
  *hidl_results = {};
  for (const auto legacy_result : legacy_results) {
    RttResult hidl_result;
    if (!convertLegacyRttResultToHidl(*legacy_result, &hidl_result)) {
      return false;
    }
    hidl_results->push_back(hidl_result);
  }
  return true;
}
}  // namespace hidl_struct_util
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
