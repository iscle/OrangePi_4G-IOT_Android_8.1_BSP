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

#include "bluetooth_address.h"

#include <android-base/logging.h>
#include <cutils/properties.h>
#include <fcntl.h>
#include <utils/Log.h>

namespace android {
namespace hardware {
namespace bluetooth {
namespace V1_0 {
namespace dragon {

void BluetoothAddress::bytes_to_string(const uint8_t* addr, char* addr_str) {
  sprintf(addr_str, "%02x:%02x:%02x:%02x:%02x:%02x", addr[0], addr[1], addr[2],
          addr[3], addr[4], addr[5]);
}

bool BluetoothAddress::string_to_bytes(const char* addr_str, uint8_t* addr) {
  if (addr_str == NULL) return false;
  if (strnlen(addr_str, kStringLength) != kStringLength) return false;
  unsigned char trailing_char = '\0';

  return (sscanf(addr_str, "%02hhx:%02hhx:%02hhx:%02hhx:%02hhx:%02hhx%1c",
                 &addr[0], &addr[1], &addr[2], &addr[3], &addr[4], &addr[5],
                 &trailing_char) == kBytes);
}

bool BluetoothAddress::get_local_address(uint8_t* local_addr) {
  char property[PROPERTY_VALUE_MAX] = {0};

  // No factory BDADDR found. Look for a previously stored BDA.
  if (property_get(PERSIST_BDADDR_PROPERTY, property, NULL) &&
      string_to_bytes(property, local_addr)) {
    return true;
  }

  // Look for an the WiFi MAC from an AzureWave module.
  int wifi_mac_fd = open(AZW_WIFI_MAC_PATH, O_RDONLY);
  if (wifi_mac_fd != -1) {
    int bytes_read = read(wifi_mac_fd, property, kStringLength);
    close(wifi_mac_fd);
    if (bytes_read != kStringLength) return false;

    // Null terminate the string.
    property[kStringLength] = '\0';

    // Zero last bit to calculate the Bluetooth address. This works because the
    // WiFi address is always odd (never ending in 0x0 or 0xa).
    property[kStringLength - 1] = property[kStringLength - 1] - 1;

    ALOGD("%s: Got BDA from WiFi MAC %s", __func__, property);
    return string_to_bytes(property, local_addr);
  }

  return false;
}

}  // namespace dragon
}  // namespace V1_0
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
