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

#ifndef WIFI_P2P_IFACE_H_
#define WIFI_P2P_IFACE_H_

#include <android-base/macros.h>
#include <android/hardware/wifi/1.0/IWifiP2pIface.h>

#include "wifi_legacy_hal.h"

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
using namespace android::hardware::wifi::V1_0;

/**
 * HIDL interface object used to control a P2P Iface instance.
 */
class WifiP2pIface : public V1_0::IWifiP2pIface {
 public:
  WifiP2pIface(const std::string& ifname,
               const std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal);
  // Refer to |WifiChip::invalidate()|.
  void invalidate();
  bool isValid();

  // HIDL methods exposed.
  Return<void> getName(getName_cb hidl_status_cb) override;
  Return<void> getType(getType_cb hidl_status_cb) override;

 private:
  // Corresponding worker functions for the HIDL methods.
  std::pair<WifiStatus, std::string> getNameInternal();
  std::pair<WifiStatus, IfaceType> getTypeInternal();

  std::string ifname_;
  std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal_;
  bool is_valid_;

  DISALLOW_COPY_AND_ASSIGN(WifiP2pIface);
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // WIFI_P2P_IFACE_H_
