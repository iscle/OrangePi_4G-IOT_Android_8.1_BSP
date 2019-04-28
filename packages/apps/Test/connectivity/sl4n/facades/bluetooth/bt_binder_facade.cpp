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

#include <base.h>
#include <base/at_exit.h>
#include <base/command_line.h>
#include <base/logging.h>
#include <base/macros.h>
#include <base/strings/string_split.h>
#include <base/strings/string_util.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

#include "bt_binder_facade.h"
#include <rapidjson/document.h>
#include <rapidjson/writer.h>
#include <rapidjson/stringbuffer.h>
#include <android/bluetooth/IBluetooth.h>
#include <android/bluetooth/IBluetoothCallback.h>
#include <android/bluetooth/IBluetoothLowEnergy.h>
#include <service/common/bluetooth/low_energy_constants.h>
#include <tuple>
#include <utils/command_receiver.h>
#include <utils/common_utils.h>

using android::bluetooth::IBluetooth;
using android::bluetooth::IBluetoothLowEnergy;
using android::getService;
using android::OK;
using android::sp;
using android::String8;
using android::String16;

std::atomic_bool ble_registering(false);
std::atomic_int ble_client_id(0);

std::string kServiceName = "bluetooth-service";

bool BtBinderFacade::SharedValidator() {
  if (bt_iface == NULL) {
    LOG(ERROR) << sl4n::kTagStr << " IBluetooth interface not initialized";
    return false;
  }
  bool ret;
  bt_iface->IsEnabled(&ret);
  if (!ret) {
    LOG(ERROR) << sl4n::kTagStr << " IBluetooth interface not enabled";
    return false;
  }
  return true;
}

std::tuple<bool, int> BtBinderFacade::BtBinderEnable() {
  if (bt_iface == NULL) {
    LOG(ERROR) << sl4n::kTagStr << ": IBluetooth interface not enabled";
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }
  bool ret;
  bt_iface->Enable(false, &ret);
  if (!ret) {
    LOG(ERROR) << sl4n::kTagStr << ": Failed to enable the Bluetooth service";
    return std::make_tuple(false, sl4n_error_codes::kPassInt);
  } else {
    return std::make_tuple(true, sl4n_error_codes::kPassInt);
  }
}

std::tuple<std::string, int> BtBinderFacade::BtBinderGetAddress() {
  if (!SharedValidator()) {
    return std::make_tuple(sl4n::kFailStr, sl4n_error_codes::kFailInt);
  }
  String16 address;
  bt_iface->GetAddress(&address);
  return std::make_tuple(std::string(String8(address).string()), sl4n_error_codes::kPassInt);
}

std::tuple<std::string, int> BtBinderFacade::BtBinderGetName() {
  if (!SharedValidator()) {
    return std::make_tuple(sl4n::kFailStr,sl4n_error_codes::kFailInt);
  }

  String16 name16;
  bt_iface->GetName(&name16);
  std::string name = std::string(String8(name16).string());
  if (name.empty()) {
    LOG(ERROR) << sl4n::kTagStr << ": Failed to get device name";
    return std::make_tuple(sl4n::kFailStr, sl4n_error_codes::kFailInt);
  } else {
    return std::make_tuple(name, sl4n_error_codes::kPassInt);
  }
}

std::tuple<bool, int> BtBinderFacade::BtBinderSetName(
  std::string name) {

  if (!SharedValidator()) {
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }

  bool result;
  bt_iface->SetName(String16(String8(name.c_str())), &result);
  if (!result) {
    LOG(ERROR) << sl4n::kTagStr << ": Failed to set device name";
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }
  return std::make_tuple(true, sl4n_error_codes::kPassInt);
}

std::tuple<bool, int> BtBinderFacade::BtBinderInitInterface() {
  status_t status = getService(String16(kServiceName.c_str()), &bt_iface);
  if (status != OK) {
    LOG(ERROR) << "Failed to get service binder: '" << kServiceName
               << "' status=" << status;
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }
  return std::make_tuple(true, sl4n_error_codes::kPassInt);
}

std::tuple<bool, int> BtBinderFacade::BtBinderRegisterBLE() {
  // TODO (tturney): verify bt_iface initialized everywhere
  if (!SharedValidator()) {
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }
  bt_iface->GetLowEnergyInterface(&ble_iface);
  if(!ble_iface.get()) {
    LOG(ERROR) << sl4n::kTagStr << ": Failed to register BLE";
    return std::make_tuple(false, sl4n_error_codes::kFailInt);
  }
  return std::make_tuple(true, sl4n_error_codes::kPassInt);
}

std::tuple<int, int> BtBinderFacade::BtBinderSetAdvSettings(
  int mode, int timeout_seconds, int tx_power_level, bool is_connectable) {
  if (!SharedValidator()) {
    return std::make_tuple(false,sl4n_error_codes::kFailInt);
  }
  bluetooth::AdvertiseSettings::Mode adv_mode;
  switch (mode) {
    case sl4n_ble::kAdvSettingsModeLowPowerInt :
      adv_mode = bluetooth::AdvertiseSettings::Mode::MODE_LOW_POWER;
      break;
    case sl4n_ble::kAdvSettingsModeBalancedInt :
      adv_mode = bluetooth::AdvertiseSettings::Mode::MODE_BALANCED;
      break;
    case sl4n_ble::kAdvSettingsModeLowLatencyInt :
      adv_mode = bluetooth::AdvertiseSettings::Mode::MODE_LOW_LATENCY;
      break;
    default :
      LOG(ERROR) << sl4n::kTagStr <<
        ": Input mode is outside the accepted values";
      return std::make_tuple(
        sl4n::kFailedCounterInt, sl4n_error_codes::kFailInt);
  }

  base::TimeDelta adv_timeout = base::TimeDelta::FromSeconds(
    timeout_seconds);

  bluetooth::AdvertiseSettings::TxPowerLevel adv_tx_power_level;
  switch (tx_power_level) {
    case sl4n_ble::kAdvSettingsTxPowerLevelUltraLowInt: adv_tx_power_level =
      bluetooth::AdvertiseSettings::TxPowerLevel::TX_POWER_LEVEL_ULTRA_LOW;
      break;
    case sl4n_ble::kAdvSettingsTxPowerLevelLowInt: adv_tx_power_level =
      bluetooth::AdvertiseSettings::TxPowerLevel::TX_POWER_LEVEL_LOW;
      break;
    case sl4n_ble::kAdvSettingsTxPowerLevelMediumInt: adv_tx_power_level =
      bluetooth::AdvertiseSettings::TxPowerLevel::TX_POWER_LEVEL_MEDIUM;
      break;
    case sl4n_ble::kAdvSettingsTxPowerLevelHighInt: adv_tx_power_level =
      bluetooth::AdvertiseSettings::TxPowerLevel::TX_POWER_LEVEL_HIGH;
      break;
    default :
      LOG(ERROR) << sl4n::kTagStr <<
        ": Input tx power level is outside the accepted values";
      return std::make_tuple(
        sl4n::kFailedCounterInt, sl4n_error_codes::kFailInt);
  }

  bluetooth::AdvertiseSettings adv_settings = bluetooth::AdvertiseSettings(
    adv_mode, adv_timeout, adv_tx_power_level, is_connectable);
  adv_settings_map[adv_settings_count] = adv_settings;
  int adv_settings_id = adv_settings_count;
  adv_settings_count++;
  return std::make_tuple(adv_settings_id, sl4n_error_codes::kPassInt);
}

//////////////////
// wrappers
//////////////////

static BtBinderFacade facade;  // triggers registration with CommandReceiver

void bt_binder_get_local_name_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  //check for kfailedstr or NULL???
  std::string name;
  int error_code;
  std::tie(name, error_code) = facade.BtBinderGetName();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, sl4n::kFailStr, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
    return;
  }
  rapidjson::Value tmp;
  tmp.SetString(name.c_str(), doc.GetAllocator());
  doc.AddMember(sl4n::kResultStr, tmp, doc.GetAllocator());
  doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  return;
}

void bt_binder_init_interface_wapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  bool init_result;
  int error_code;
  std::tie(init_result, error_code) = facade.BtBinderInitInterface();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
  doc.AddMember(sl4n::kResultStr, init_result, doc.GetAllocator());
  return;
}

void bt_binder_set_local_name_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 1;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  std::string name;
  if (!doc[sl4n::kParamsStr][0].IsString()) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected String input for name";
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
    return;
  } else {
    name = doc[sl4n::kParamsStr][0].GetString();
  }
  bool set_result;
  int error_code;
  std::tie(set_result, error_code) = facade.BtBinderSetName(name);
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, set_result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
  return;
}

void bt_binder_get_local_address_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  //check for kfailedstr or NULL???
  std::string address;
  int error_code;
  std::tie(address, error_code) = facade.BtBinderGetAddress();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, sl4n::kFailStr, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    rapidjson::Value tmp;
    tmp.SetString(address.c_str(), doc.GetAllocator());
    doc.AddMember(sl4n::kResultStr, tmp, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
  return;
}

void bt_binder_enable_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  bool enable_result;
  int error_code;
  std::tie(enable_result, error_code) = facade.BtBinderEnable();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, enable_result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void bt_binder_register_ble_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  bool register_result;
  int error_code;
  std::tie(register_result, error_code) =
    facade.BtBinderRegisterBLE();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, register_result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void bt_binder_set_adv_settings_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 4;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  int mode;
  int timeout_seconds;
  int tx_power_level;
  bool is_connectable;
  // TODO(tturney) Verify inputs better
  if (!doc[sl4n::kParamsStr][0].IsInt()) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected Int input for mode";
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kInvalidParamStr, doc.GetAllocator());
    return;
  } else {
    mode = doc[sl4n::kParamsStr][0].GetInt();
  }
  if (!doc[sl4n::kParamsStr][1].IsInt()) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected Int input for timeout";
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kInvalidParamStr, doc.GetAllocator());
    return;
  } else {
    timeout_seconds = doc[sl4n::kParamsStr][1].GetInt();
  }
  if (!doc[sl4n::kParamsStr][2].IsInt()) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected Int input for tx power level";
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kInvalidParamStr, doc.GetAllocator());
    return;
  } else {
    tx_power_level = doc[sl4n::kParamsStr][2].GetInt();
  }
  if (!doc[sl4n::kParamsStr][3].IsBool()) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected Bool input for connectable";
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kInvalidParamStr, doc.GetAllocator());
    return;
  } else {
    is_connectable = doc[sl4n::kParamsStr][3].GetBool();
  }

  int adv_settings;
  int error_code;
  std::tie(adv_settings, error_code) = facade.BtBinderSetAdvSettings(
    mode, timeout_seconds, tx_power_level, is_connectable);
  if(error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(
      sl4n::kResultStr, sl4n_error_codes::kFailInt, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailedCounterInt, doc.GetAllocator());
    return;
  } else {
    doc.AddMember(sl4n::kResultStr, adv_settings, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

////////////////
// constructor
////////////////

BtBinderFacade::BtBinderFacade() {
  adv_settings_count = 0;
  manu_data_count = 0;

  CommandReceiver::RegisterCommand("BtBinderInitInterface",
    &bt_binder_init_interface_wapper);
  CommandReceiver::RegisterCommand("BtBinderGetName",
    &bt_binder_get_local_name_wrapper);
  CommandReceiver::RegisterCommand("BtBinderSetName",
    &bt_binder_set_local_name_wrapper);
  CommandReceiver::RegisterCommand("BtBinderGetAddress",
    &bt_binder_get_local_address_wrapper);
  CommandReceiver::RegisterCommand("BtBinderEnable",
    &bt_binder_enable_wrapper);
  CommandReceiver::RegisterCommand("BtBinderRegisterBLE",
    &bt_binder_register_ble_wrapper);
  CommandReceiver::RegisterCommand("BtBinderSetAdvSettings",
    &bt_binder_set_adv_settings_wrapper);
}

