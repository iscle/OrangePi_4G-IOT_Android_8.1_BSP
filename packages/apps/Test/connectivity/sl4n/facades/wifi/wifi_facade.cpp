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
#include <rapidjson/document.h>
#include <rapidjson/writer.h>
#include <rapidjson/stringbuffer.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#include <base.h>
#include <base/at_exit.h>
#include <base/command_line.h>
#include <base/logging.h>
#include <base/macros.h>
#include <base/strings/string_split.h>
#include <base/strings/string_util.h>
#include <utils/command_receiver.h>
#include <utils/common_utils.h>
#include <hardware_legacy/wifi_hal.h>
#include <wifi_system/hal_tool.h>
#include <wifi_system/interface_tool.h>

#include "wifi_facade.h"

const char kWlanInterface[] = "wlan0";
const char kP2pInterface[] = "p2p0";

std::tuple<bool, int> WifiFacade::WifiInit() {
  if (!WifiStartHal()) {
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }

  if (!WifiGetInterfaces() || wlan0_index == -1) {
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }

  return std::make_tuple(true, sl4n_error_codes::kPassInt);
}

bool WifiFacade::WifiStartHal() {
  android::wifi_system::InterfaceTool if_tool;
  if (wifi_hal_handle == NULL) {
    android::wifi_system::HalTool hal_tool;
    if (!hal_tool.InitFunctionTable(&hal_fn)) {
      return false;
    }

    if (!if_tool.SetWifiUpState(true)) {
      return false;
    }

    res = hal_fn.wifi_initialize(&wifi_hal_handle);
    return res == WIFI_SUCCESS;
  } else {
    return if_tool.SetWifiUpState(true);
  }
}

bool WifiFacade::WifiGetInterfaces() {
  int num_ifaces;
  int result = hal_fn.wifi_get_ifaces(wifi_hal_handle, &num_ifaces,
                                      &wifi_iface_handles);
  if (result < 0) {
    LOG(ERROR) << sl4n::kTagStr << ": Can not get Wi-Fi interfaces";
    return false;
  }

  if (num_ifaces < 0) {
    LOG(ERROR) << sl4n::kTagStr << ": Negative number of interfaces";
    return false;
  }

  if (wifi_iface_handles == NULL) {
    LOG(ERROR) << sl4n::kTagStr
        << "wifi_get_ifaces returned null interface array";
    return false;
  }

  if (num_ifaces > 8) {
    LOG(ERROR) << sl4n::kTagStr
        << "wifi_get_ifaces returned too many interfaces";
    return false;
  }

  char buf[128];
  for (int i = 0; i < num_ifaces; ++i) {
    int result = hal_fn.wifi_get_iface_name(wifi_iface_handles[i], buf,
                                            sizeof(buf));
    if (result < 0) {
      LOG(ERROR) << sl4n::kTagStr
          << "Can't obtain interface name for interface #" << i;
      continue;
    }
    if (!strcmp(buf, kWlanInterface)) {
      wlan0_index = i;
    } else if (!strcmp(buf, kP2pInterface)) {
      p2p0_index = i;
    }
  }

  return true;
}

bool WifiFacade::SharedValidator() {
  if (wifi_hal_handle == NULL) {
    LOG(ERROR) << sl4n::kTagStr << "HAL handle not initialized";
    return false;
  }

  if (wifi_iface_handles == NULL) {
    LOG(ERROR) << sl4n::kTagStr << "HAL interfaces not initialized";
    return false;
  }

  if (wlan0_index == -1) {
    LOG(ERROR) << sl4n::kTagStr << kWlanInterface << " interface not found";
    return false;
  }

  return true;
}

std::tuple<int, int> WifiFacade::WifiGetSupportedFeatureSet() {
  if (!SharedValidator()) {
    return std::make_tuple(0, sl4n_error_codes::kFailInt);
  }

  feature_set set = 0;
  int result = hal_fn.wifi_get_supported_feature_set(
      wifi_iface_handles[wlan0_index], &set);
  if (result == WIFI_SUCCESS) {
    return std::make_tuple(set, sl4n_error_codes::kPassInt);
  } else {
    return std::make_tuple(0, sl4n_error_codes::kFailInt);
  }
}

//////////////////
// wrappers
/////////////////

static WifiFacade facade;  // triggers registration with CommandReceiver

void wifi_init_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  bool result;
  int error_code;
  std::tie(result, error_code) = facade.WifiInit();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void wifi_get_supported_feature_set_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  int result;
  int error_code;
  std::tie(result, error_code) = facade.WifiGetSupportedFeatureSet();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

////////////////
// constructor
////////////////

WifiFacade::WifiFacade() {
  wifi_hal_handle = NULL;
  wifi_iface_handles = NULL;
  num_wifi_iface_handles = 0;
  wlan0_index = -1;
  p2p0_index = -1;

  CommandReceiver::RegisterCommand("WifiInit", &wifi_init_wrapper);
  CommandReceiver::RegisterCommand("WifiGetSupportedFeatureSet",
                                   &wifi_get_supported_feature_set_wrapper);
}
