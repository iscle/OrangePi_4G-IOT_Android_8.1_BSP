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

#include <cutils/properties.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <utils/Log.h>

namespace android {
namespace hardware {
namespace bluetooth {
namespace V1_0 {
namespace implementation {

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
  bool valid_bda = false;

  // Get local bdaddr storage path from a system property.
  if (property_get(PROPERTY_BT_BDADDR_PATH, property, NULL)) {
    int addr_fd;

    ALOGD("%s: Trying %s", __func__, property);

    addr_fd = open(property, O_RDONLY);
    if (addr_fd != -1) {
      char address[kStringLength + 1] = {0};
      int bytes_read = read(addr_fd, address, kStringLength);
      if (bytes_read == -1) {
        ALOGE("%s: Error reading address from %s: %s", __func__, property,
              strerror(errno));
      }
      close(addr_fd);

      // Null terminate the string.
      address[kStringLength] = '\0';

      // If the address is not all zeros, then use it.
      const uint8_t zero_bdaddr[kBytes] = {0, 0, 0, 0, 0, 0};
      if ((string_to_bytes(address, local_addr)) &&
          (memcmp(local_addr, zero_bdaddr, kBytes) != 0)) {
        valid_bda = true;
        ALOGD("%s: Got Factory BDA %s", __func__, address);
      } else {
        ALOGE("%s: Got Invalid BDA '%s' from %s", __func__, address, property);
      }
    }
  }

  // No BDADDR found in the file. Look for BDA in a factory property.
  if (!valid_bda && property_get(FACTORY_BDADDR_PROPERTY, property, NULL) &&
      string_to_bytes(property, local_addr)) {
    valid_bda = true;
  }

  // No factory BDADDR found. Look for a previously stored BDA.
  if (!valid_bda && property_get(PERSIST_BDADDR_PROPERTY, property, NULL) &&
      string_to_bytes(property, local_addr)) {
    valid_bda = true;
  }

  /* Generate new BDA if necessary */
  if (!valid_bda) {
    char bdstr[kStringLength + 1];

    /* No autogen BDA. Generate one now. */
    local_addr[0] = 0x22;
    local_addr[1] = 0x22;
    local_addr[2] = (uint8_t)rand();
    local_addr[3] = (uint8_t)rand();
    local_addr[4] = (uint8_t)rand();
    local_addr[5] = (uint8_t)rand();

    /* Convert to ascii, and store as a persistent property */
    bytes_to_string(local_addr, bdstr);

    ALOGE("%s: No preset BDA! Generating BDA: %s for prop %s", __func__,
          (char*)bdstr, PERSIST_BDADDR_PROPERTY);
    ALOGE("%s: This is a bug in the platform!  Please fix!", __func__);

    if (property_set(PERSIST_BDADDR_PROPERTY, (char*)bdstr) < 0) {
      ALOGE("%s: Failed to set random BDA in prop %s", __func__,
            PERSIST_BDADDR_PROPERTY);
      valid_bda = false;
    } else {
      valid_bda = true;
    }
  }

  return valid_bda;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
