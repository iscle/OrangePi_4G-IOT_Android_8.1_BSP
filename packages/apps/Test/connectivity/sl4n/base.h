//
//  Copyright (C) 2015 Google, Inc.
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

#include <stdio.h>
#include <stdbool.h>
#include <string.h>

#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_gatt.h>
#include <hardware/bt_pan.h>
#include <hardware/bt_sock.h>
#include <hardware/hardware.h>
#include <base/macros.h>

namespace sl4n {
const char kCmdStr[] = "cmd";
const char kDeviceNameStr[] = "deviceName";
const char kErrorStr[] = "error";
const char kFailStr[] = "fail";
const char kInvalidParamStr[] = "Invalid parameter";
const char kMethodStr[] = "method";
const char kParamsStr[] = "params";
const char kResultStr[] = "result";
const char kStatusStr[] = "status";
const char kSuccessStr[] = "success";
const char kTagStr[] = "SL4N";
const int kFailedCounterInt = -1;
}

namespace sl4n_ble {
const int kAdvSettingsModeLowPowerInt = 0;
const int kAdvSettingsModeBalancedInt = 1;
const int kAdvSettingsModeLowLatencyInt = 2;

const int kAdvSettingsTxPowerLevelUltraLowInt = 0;
const int kAdvSettingsTxPowerLevelLowInt = 1;
const int kAdvSettingsTxPowerLevelMediumInt = 2;
const int kAdvSettingsTxPowerLevelHighInt = 3;
}

namespace sl4n_error_codes {
const int kFailInt = 0;
const int kPassInt = 1;
}
