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

#include <android-base/logging.h>

#include "hidl_return_util.h"
#include "wifi.h"
#include "wifi_status_util.h"

namespace {
// Chip ID to use for the only supported chip.
static constexpr android::hardware::wifi::V1_0::ChipId kChipId = 0;
}  // namespace

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
using hidl_return_util::validateAndCall;
using hidl_return_util::validateAndCallWithLock;

Wifi::Wifi()
    : legacy_hal_(new legacy_hal::WifiLegacyHal()),
      mode_controller_(new mode_controller::WifiModeController()),
      run_state_(RunState::STOPPED) {}

bool Wifi::isValid() {
  // This object is always valid.
  return true;
}

Return<void> Wifi::registerEventCallback(
    const sp<IWifiEventCallback>& event_callback,
    registerEventCallback_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_UNKNOWN,
                         &Wifi::registerEventCallbackInternal,
                         hidl_status_cb,
                         event_callback);
}

Return<bool> Wifi::isStarted() {
  return run_state_ != RunState::STOPPED;
}

Return<void> Wifi::start(start_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_UNKNOWN,
                         &Wifi::startInternal,
                         hidl_status_cb);
}

Return<void> Wifi::stop(stop_cb hidl_status_cb) {
  return validateAndCallWithLock(this, WifiStatusCode::ERROR_UNKNOWN,
                                 &Wifi::stopInternal, hidl_status_cb);
}

Return<void> Wifi::getChipIds(getChipIds_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_UNKNOWN,
                         &Wifi::getChipIdsInternal,
                         hidl_status_cb);
}

Return<void> Wifi::getChip(ChipId chip_id, getChip_cb hidl_status_cb) {
  return validateAndCall(this,
                         WifiStatusCode::ERROR_UNKNOWN,
                         &Wifi::getChipInternal,
                         hidl_status_cb,
                         chip_id);
}

WifiStatus Wifi::registerEventCallbackInternal(
    const sp<IWifiEventCallback>& event_callback) {
  if (!event_cb_handler_.addCallback(event_callback)) {
    return createWifiStatus(WifiStatusCode::ERROR_UNKNOWN);
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

WifiStatus Wifi::startInternal() {
  if (run_state_ == RunState::STARTED) {
    return createWifiStatus(WifiStatusCode::SUCCESS);
  } else if (run_state_ == RunState::STOPPING) {
    return createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE,
                            "HAL is stopping");
  }
  WifiStatus wifi_status = initializeLegacyHal();
  if (wifi_status.code == WifiStatusCode::SUCCESS) {
    // Create the chip instance once the HAL is started.
    chip_ = new WifiChip(kChipId, legacy_hal_, mode_controller_);
    run_state_ = RunState::STARTED;
    for (const auto& callback : event_cb_handler_.getCallbacks()) {
      if (!callback->onStart().isOk()) {
        LOG(ERROR) << "Failed to invoke onStart callback";
      };
    }
    LOG(INFO) << "Wifi HAL started";
  } else {
    for (const auto& callback : event_cb_handler_.getCallbacks()) {
      if (!callback->onFailure(wifi_status).isOk()) {
        LOG(ERROR) << "Failed to invoke onFailure callback";
      }
    }
    LOG(ERROR) << "Wifi HAL start failed";
  }
  return wifi_status;
}

WifiStatus Wifi::stopInternal(
    /* NONNULL */ std::unique_lock<std::recursive_mutex>* lock) {
  if (run_state_ == RunState::STOPPED) {
    return createWifiStatus(WifiStatusCode::SUCCESS);
  } else if (run_state_ == RunState::STOPPING) {
    return createWifiStatus(WifiStatusCode::ERROR_NOT_AVAILABLE,
                            "HAL is stopping");
  }
  // Clear the chip object and its child objects since the HAL is now
  // stopped.
  if (chip_.get()) {
    chip_->invalidate();
    chip_.clear();
  }
  WifiStatus wifi_status = stopLegacyHalAndDeinitializeModeController(lock);
  if (wifi_status.code == WifiStatusCode::SUCCESS) {
    for (const auto& callback : event_cb_handler_.getCallbacks()) {
      if (!callback->onStop().isOk()) {
        LOG(ERROR) << "Failed to invoke onStop callback";
      };
    }
    LOG(INFO) << "Wifi HAL stopped";
  } else {
    for (const auto& callback : event_cb_handler_.getCallbacks()) {
      if (!callback->onFailure(wifi_status).isOk()) {
        LOG(ERROR) << "Failed to invoke onFailure callback";
      }
    }
    LOG(ERROR) << "Wifi HAL stop failed";
  }
  return wifi_status;
}

std::pair<WifiStatus, std::vector<ChipId>> Wifi::getChipIdsInternal() {
  std::vector<ChipId> chip_ids;
  if (chip_.get()) {
    chip_ids.emplace_back(kChipId);
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), std::move(chip_ids)};
}

std::pair<WifiStatus, sp<IWifiChip>> Wifi::getChipInternal(ChipId chip_id) {
  if (!chip_.get()) {
    return {createWifiStatus(WifiStatusCode::ERROR_NOT_STARTED), nullptr};
  }
  if (chip_id != kChipId) {
    return {createWifiStatus(WifiStatusCode::ERROR_INVALID_ARGS), nullptr};
  }
  return {createWifiStatus(WifiStatusCode::SUCCESS), chip_};
}

WifiStatus Wifi::initializeLegacyHal() {
  legacy_hal::wifi_error legacy_status = legacy_hal_->initialize();
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to initialize legacy HAL: "
               << legacyErrorToString(legacy_status);
    return createWifiStatusFromLegacyError(legacy_status);
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}

WifiStatus Wifi::stopLegacyHalAndDeinitializeModeController(
    /* NONNULL */ std::unique_lock<std::recursive_mutex>* lock) {
  run_state_ = RunState::STOPPING;
  legacy_hal::wifi_error legacy_status =
      legacy_hal_->stop(lock, [&]() { run_state_ = RunState::STOPPED; });
  if (legacy_status != legacy_hal::WIFI_SUCCESS) {
    LOG(ERROR) << "Failed to stop legacy HAL: "
               << legacyErrorToString(legacy_status);
    return createWifiStatusFromLegacyError(legacy_status);
  }
  if (!mode_controller_->deinitialize()) {
    LOG(ERROR) << "Failed to deinitialize firmware mode controller";
    return createWifiStatus(WifiStatusCode::ERROR_UNKNOWN);
  }
  return createWifiStatus(WifiStatusCode::SUCCESS);
}
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
