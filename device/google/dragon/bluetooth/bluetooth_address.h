//
// Copyright 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#pragma once

#include <fcntl.h>

#include <cstdint>
#include <string>
#include <vector>

namespace android {
namespace hardware {
namespace bluetooth {
namespace V1_0 {
namespace dragon {

// Check for a legacy address stored as a property.
static constexpr char PERSIST_BDADDR_PROPERTY[] =
    "persist.service.bdroid.bdaddr";

// Use the WiFi MAC with bit 0 cleared for AzureWave modules
static constexpr char AZW_WIFI_MAC_PATH[] =
    "/sys/devices/700b0200.sdhci/mmc_host/mmc0/"
    "mmc0:0001/mmc0:0001:2/net/wlan0/address";

// Encapsulate handling for Bluetooth Addresses:
class BluetoothAddress {
 public:
  // Conversion constants
  static constexpr size_t kStringLength = sizeof("XX:XX:XX:XX:XX:XX") - 1;
  static constexpr size_t kBytes = (kStringLength + 1) / 3;

  static void bytes_to_string(const uint8_t* addr, char* addr_str);

  static bool string_to_bytes(const char* addr_str, uint8_t* addr);

  static bool get_local_address(uint8_t* addr);
};

}  // namespace dragon
}  // namespace V1_0
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
