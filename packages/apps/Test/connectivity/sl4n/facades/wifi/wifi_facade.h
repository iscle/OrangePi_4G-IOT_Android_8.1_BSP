//
//  Copyright (C) 2016 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#pragma once

#include <rapidjson/document.h>
#include <tuple>

// WifiFacade provides simple wrappers to call Wi-Fi HAL APIs.
//
// Each public function returns a tuple: <result, code>, where:
//     result: result of HAL API or a dummy value (of the correct type)
//             on failure.
//     code: sl4n_error_codes::kPassInt or sl4n_error_codes::kFailInt on
//           success or failure respectively.
//
// The wrapper must check whether or not it is possible to call the API.
// Note the function "SharedValidator()" should be used by wrapper to check
// whether or not the HAL is configured correctly.
class WifiFacade {
 public:
  WifiFacade();
  std::tuple<bool, int> WifiInit();
  std::tuple<int, int> WifiGetSupportedFeatureSet();
 private:
  wifi_hal_fn hal_fn;
  wifi_handle wifi_hal_handle;
  wifi_interface_handle* wifi_iface_handles;
  int num_wifi_iface_handles;
  int wlan0_index;
  int p2p0_index;

  bool SharedValidator();
  bool WifiStartHal();
  bool WifiGetInterfaces();
};
