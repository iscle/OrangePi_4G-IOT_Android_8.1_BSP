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

#include "wifi_system/hostapd_manager.h"

#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <cutils/properties.h>
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <private/android_filesystem_config.h>

#include "wifi_system/supplicant_manager.h"

using android::base::ParseInt;
using android::base::ReadFileToString;
using android::base::RemoveFileIfExists;
using android::base::StringPrintf;
using android::base::WriteStringToFile;
using std::string;
using std::vector;
using std::stringstream;

namespace android {
namespace wifi_system {
namespace {

const int kDefaultApChannel = 6;
const char kHostapdServiceName[] = "hostapd";
const char kHostapdConfigFilePath[] = "/data/misc/wifi/hostapd.conf";


string GeneratePsk(const vector<uint8_t>& ssid,
                   const vector<uint8_t>& passphrase) {
  string result;
  unsigned char psk[SHA256_DIGEST_LENGTH];

  // Use the PKCS#5 PBKDF2 with 4096 iterations
  if (PKCS5_PBKDF2_HMAC_SHA1(reinterpret_cast<const char*>(passphrase.data()),
                             passphrase.size(),
                             ssid.data(), ssid.size(),
                             4096, sizeof(psk), psk) != 1) {
    LOG(ERROR) << "Cannot generate PSK using PKCS#5 PBKDF2";
    return result;
  }

  stringstream ss;
  ss << std::hex;
  ss << std::setfill('0');
  for (int j = 0; j < SHA256_DIGEST_LENGTH; j++) {
    ss << std::setw(2) << static_cast<unsigned int>(psk[j]);
  }
  result = ss.str();

  return result;
}

}  // namespace

bool HostapdManager::StartHostapd() {
  if (!SupplicantManager::EnsureEntropyFileExists()) {
    LOG(WARNING) << "Wi-Fi entropy file was not created";
  }

  if (property_set("ctl.start", kHostapdServiceName) != 0) {
    LOG(ERROR) << "Failed to start SoftAP";
    return false;
  }

  LOG(DEBUG) << "SoftAP started successfully";
  return true;
}

bool HostapdManager::StopHostapd() {
  LOG(DEBUG) << "Stopping the SoftAP service...";

  if (property_set("ctl.stop", kHostapdServiceName) < 0) {
    LOG(ERROR) << "Failed to stop hostapd service!";
    return false;
  }

  LOG(DEBUG) << "SoftAP stopped successfully";
  return true;
}

bool HostapdManager::WriteHostapdConfig(const string& config) {
  // Remove hostapd.conf because its file owner might be system
  // in previous OS and chmod fails in that case.
  RemoveFileIfExists(kHostapdConfigFilePath);
  if (!WriteStringToFile(config, kHostapdConfigFilePath,
                         S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP,
                         AID_WIFI, AID_WIFI)) {
    int error = errno;
    LOG(ERROR) << "Cannot write hostapd config to \""
               << kHostapdConfigFilePath << "\": " << strerror(error);
    struct stat st;
    int result = stat(kHostapdConfigFilePath, &st);
    if (result == 0) {
      LOG(ERROR) << "hostapd config file uid: "<< st.st_uid << ", gid: " << st.st_gid
                 << ", mode: " << st.st_mode;
    } else {
      LOG(ERROR) << "Error calling stat() on hostapd config file: " << strerror(errno);
    }
    return false;
  }
  return true;
}

string HostapdManager::CreateHostapdConfig(
    const string& interface_name,
    const vector<uint8_t> ssid,
    bool is_hidden,
    int channel,
    EncryptionType encryption_type,
    const vector<uint8_t> passphrase) {
  string result;

  if (channel < 0) {
    channel = kDefaultApChannel;
  }

  if (ssid.size() > 32) {
    LOG(ERROR) << "SSIDs must be <= 32 bytes long";
    return result;
  }

  stringstream ss;
  ss << std::hex;
  ss << std::setfill('0');
  for (uint8_t b : ssid) {
    ss << std::setw(2) << static_cast<unsigned int>(b);
  }
  const string ssid_as_string  = ss.str();

  string encryption_config;
  if (encryption_type != EncryptionType::kOpen) {
    string psk = GeneratePsk(ssid, passphrase);
    if (psk.empty()) {
      return result;
    }
    if (encryption_type == EncryptionType::kWpa) {
      encryption_config = StringPrintf("wpa=3\n"
                                       "wpa_pairwise=TKIP CCMP\n"
                                       "wpa_psk=%s\n", psk.c_str());
    } else if (encryption_type == EncryptionType::kWpa2) {
      encryption_config = StringPrintf("wpa=2\n"
                                       "rsn_pairwise=CCMP\n"
                                       "wpa_psk=%s\n", psk.c_str());
    } else {
      using encryption_t = std::underlying_type<EncryptionType>::type;
      LOG(ERROR) << "Unknown encryption type ("
                 << static_cast<encryption_t>(encryption_type)
                 << ")";
      return result;
    }
  }

  result = StringPrintf(
      "interface=%s\n"
      "driver=nl80211\n"
      "ctrl_interface=/data/misc/wifi/hostapd/ctrl\n"
      // ssid2 signals to hostapd that the value is not a literal value
      // for use as a SSID.  In this case, we're giving it a hex string
      // and hostapd needs to expect that.
      "ssid2=%s\n"
      "channel=%d\n"
      "ieee80211n=1\n"
      "hw_mode=%c\n"
      "ignore_broadcast_ssid=%d\n"
      "wowlan_triggers=any\n"
      "%s",
      interface_name.c_str(),
      ssid_as_string.c_str(),
      channel,
      (channel <= 14) ? 'g' : 'a',
      (is_hidden) ? 1 : 0,
      encryption_config.c_str());
  return result;
}

}  // namespace wifi_system
}  // namespace android
