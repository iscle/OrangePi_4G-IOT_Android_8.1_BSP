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

#ifndef ANDROID_WIFI_SYSTEM_HOSTAPD_MANAGER_H
#define ANDROID_WIFI_SYSTEM_HOSTAPD_MANAGER_H

#include <string>
#include <vector>

#include <android-base/macros.h>

namespace android {
namespace wifi_system {

class HostapdManager {
 public:
  enum class EncryptionType {
    kOpen,
    kWpa,
    kWpa2,  // Strongly prefer this if at all possible.
  };

  HostapdManager() = default;
  virtual ~HostapdManager() = default;

  // Request that hostapd be started.
  // Returns true on success.
  virtual bool StartHostapd();

  // Request that a running instance of hostapd be stopped.
  // Returns true on success.
  virtual bool StopHostapd();

  // Create a string suitable for writing to the hostapd configuration file.
  // |interface_name| is a network interface name (e.g. "wlan0").
  // |ssid| is the SSID used by the configurated network.
  // |is_hidden| is true iff hostapd should not broadcast the SSID.
  // |channel| is the WiFi channel (e.g. 6) or <0 for a default value.
  // |encryption_type| defines the encryption of the configured network.
  // |passphrase| is ignored for kOpen networks.
  //
  // Returns an empty string on failure.
  virtual std::string CreateHostapdConfig(
      const std::string& interface_name,
      const std::vector<uint8_t> ssid,
      bool is_hidden,
      int channel,
      EncryptionType encryption,
      const std::vector<uint8_t> passphrase);

  // Write out a hostapd configuration file created via CreateHostapdConfig().
  // Future instances of hostapd will use this new configuration.
  // Returns true if the configuration file is successfully written.
  virtual bool WriteHostapdConfig(const std::string& config_file);

 private:
  DISALLOW_COPY_AND_ASSIGN(HostapdManager);
};  // class HostapdManager

}  // namespace wifi_system
}  // namespace android

#endif  // ANDROID_WIFI_SYSTEM_HOSTAPD_MANAGER_H
