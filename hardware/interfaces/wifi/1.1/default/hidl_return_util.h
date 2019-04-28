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

#ifndef HIDL_RETURN_UTIL_H_
#define HIDL_RETURN_UTIL_H_

#include "hidl_sync_util.h"
#include "wifi_status_util.h"

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace hidl_return_util {
using namespace android::hardware::wifi::V1_0;

/**
 * These utility functions are used to invoke a method on the provided
 * HIDL interface object.
 * These functions checks if the provided HIDL interface object is valid.
 * a) if valid, Invokes the corresponding internal implementation function of
 * the HIDL method. It then invokes the HIDL continuation callback with
 * the status and any returned values.
 * b) if invalid, invokes the HIDL continuation callback with the
 * provided error status and default values.
 */
// Use for HIDL methods which return only an instance of WifiStatus.
template <typename ObjT, typename WorkFuncT, typename... Args>
Return<void> validateAndCall(
    ObjT* obj,
    WifiStatusCode status_code_if_invalid,
    WorkFuncT&& work,
    const std::function<void(const WifiStatus&)>& hidl_cb,
    Args&&... args) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (obj->isValid()) {
    hidl_cb((obj->*work)(std::forward<Args>(args)...));
  } else {
    hidl_cb(createWifiStatus(status_code_if_invalid));
  }
  return Void();
}

// Use for HIDL methods which return only an instance of WifiStatus.
// This version passes the global lock acquired to the body of the method.
// Note: Only used by IWifi::stop() currently.
template <typename ObjT, typename WorkFuncT, typename... Args>
Return<void> validateAndCallWithLock(
    ObjT* obj,
    WifiStatusCode status_code_if_invalid,
    WorkFuncT&& work,
    const std::function<void(const WifiStatus&)>& hidl_cb,
    Args&&... args) {
  auto lock = hidl_sync_util::acquireGlobalLock();
  if (obj->isValid()) {
    hidl_cb((obj->*work)(&lock, std::forward<Args>(args)...));
  } else {
    hidl_cb(createWifiStatus(status_code_if_invalid));
  }
  return Void();
}

// Use for HIDL methods which return instance of WifiStatus and a single return
// value.
template <typename ObjT, typename WorkFuncT, typename ReturnT, typename... Args>
Return<void> validateAndCall(
    ObjT* obj,
    WifiStatusCode status_code_if_invalid,
    WorkFuncT&& work,
    const std::function<void(const WifiStatus&, ReturnT)>& hidl_cb,
    Args&&... args) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (obj->isValid()) {
    const auto& ret_pair = (obj->*work)(std::forward<Args>(args)...);
    const WifiStatus& status = std::get<0>(ret_pair);
    const auto& ret_value = std::get<1>(ret_pair);
    hidl_cb(status, ret_value);
  } else {
    hidl_cb(createWifiStatus(status_code_if_invalid),
            typename std::remove_reference<ReturnT>::type());
  }
  return Void();
}

// Use for HIDL methods which return instance of WifiStatus and 2 return
// values.
template <typename ObjT,
          typename WorkFuncT,
          typename ReturnT1,
          typename ReturnT2,
          typename... Args>
Return<void> validateAndCall(
    ObjT* obj,
    WifiStatusCode status_code_if_invalid,
    WorkFuncT&& work,
    const std::function<void(const WifiStatus&, ReturnT1, ReturnT2)>& hidl_cb,
    Args&&... args) {
  const auto lock = hidl_sync_util::acquireGlobalLock();
  if (obj->isValid()) {
    const auto& ret_tuple = (obj->*work)(std::forward<Args>(args)...);
    const WifiStatus& status = std::get<0>(ret_tuple);
    const auto& ret_value1 = std::get<1>(ret_tuple);
    const auto& ret_value2 = std::get<2>(ret_tuple);
    hidl_cb(status, ret_value1, ret_value2);
  } else {
    hidl_cb(createWifiStatus(status_code_if_invalid),
            typename std::remove_reference<ReturnT1>::type(),
            typename std::remove_reference<ReturnT2>::type());
  }
  return Void();
}

}  // namespace hidl_util
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
#endif  // HIDL_RETURN_UTIL_H_
