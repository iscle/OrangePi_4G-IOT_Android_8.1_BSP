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

#include <stdio.h>

#include "Conversions.h"

namespace android {
namespace hardware {
namespace audio {
namespace V2_0 {
namespace implementation {

std::string deviceAddressToHal(const DeviceAddress& address) {
    // HAL assumes that the address is NUL-terminated.
    char halAddress[AUDIO_DEVICE_MAX_ADDRESS_LEN];
    memset(halAddress, 0, sizeof(halAddress));
    uint32_t halDevice = static_cast<uint32_t>(address.device);
    const bool isInput = (halDevice & AUDIO_DEVICE_BIT_IN) != 0;
    if (isInput) halDevice &= ~AUDIO_DEVICE_BIT_IN;
    if ((!isInput && (halDevice & AUDIO_DEVICE_OUT_ALL_A2DP) != 0)
            || (isInput && (halDevice & AUDIO_DEVICE_IN_BLUETOOTH_A2DP) != 0)) {
        snprintf(halAddress, sizeof(halAddress),
                "%02X:%02X:%02X:%02X:%02X:%02X",
                address.address.mac[0], address.address.mac[1], address.address.mac[2],
                address.address.mac[3], address.address.mac[4], address.address.mac[5]);
    } else if ((!isInput && (halDevice & AUDIO_DEVICE_OUT_IP) != 0)
            || (isInput && (halDevice & AUDIO_DEVICE_IN_IP) != 0)) {
        snprintf(halAddress, sizeof(halAddress),
                "%d.%d.%d.%d",
                address.address.ipv4[0], address.address.ipv4[1],
                address.address.ipv4[2], address.address.ipv4[3]);
    } else if ((!isInput && (halDevice & AUDIO_DEVICE_OUT_ALL_USB) != 0)
            || (isInput && (halDevice & AUDIO_DEVICE_IN_ALL_USB) != 0)) {
        snprintf(halAddress, sizeof(halAddress),
                "card=%d;device=%d",
                address.address.alsa.card, address.address.alsa.device);
    } else if ((!isInput && (halDevice & AUDIO_DEVICE_OUT_BUS) != 0)
            || (isInput && (halDevice & AUDIO_DEVICE_IN_BUS) != 0)) {
        snprintf(halAddress, sizeof(halAddress),
                "%s", address.busAddress.c_str());
    } else if ((!isInput && (halDevice & AUDIO_DEVICE_OUT_REMOTE_SUBMIX)) != 0
            || (isInput && (halDevice & AUDIO_DEVICE_IN_REMOTE_SUBMIX) != 0)) {
        snprintf(halAddress, sizeof(halAddress),
                "%s", address.rSubmixAddress.c_str());
    }
    return halAddress;
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace audio
}  // namespace hardware
}  // namespace android
