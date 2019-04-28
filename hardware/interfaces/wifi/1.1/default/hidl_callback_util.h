/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef HIDL_CALLBACK_UTIL_H_
#define HIDL_CALLBACK_UTIL_H_

#include <set>

#include <hidl/HidlSupport.h>

namespace {
// Type of callback invoked by the death handler.
using on_death_cb_function = std::function<void(uint64_t)>;

// Private class used to keep track of death of individual
// callbacks stored in HidlCallbackHandler.
template <typename CallbackType>
class HidlDeathHandler : public android::hardware::hidl_death_recipient {
 public:
  HidlDeathHandler(const on_death_cb_function& user_cb_function)
      : cb_function_(user_cb_function) {}
  ~HidlDeathHandler() = default;

  // Death notification for callbacks.
  void serviceDied(
      uint64_t cookie,
      const android::wp<android::hidl::base::V1_0::IBase>& /* who */) override {
    cb_function_(cookie);
  }

 private:
  on_death_cb_function cb_function_;

  DISALLOW_COPY_AND_ASSIGN(HidlDeathHandler);
};
}  // namespace

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace hidl_callback_util {
template <typename CallbackType>
// Provides a class to manage callbacks for the various HIDL interfaces and
// handle the death of the process hosting each callback.
class HidlCallbackHandler {
 public:
  HidlCallbackHandler()
      : death_handler_(new HidlDeathHandler<CallbackType>(
            std::bind(&HidlCallbackHandler::onObjectDeath,
                      this,
                      std::placeholders::_1))) {}
  ~HidlCallbackHandler() = default;

  bool addCallback(const sp<CallbackType>& cb) {
    // TODO(b/33818800): Can't compare proxies yet. So, use the cookie
    // (callback proxy's raw pointer) to track the death of individual clients.
    uint64_t cookie = reinterpret_cast<uint64_t>(cb.get());
    if (cb_set_.find(cb) != cb_set_.end()) {
      LOG(WARNING) << "Duplicate death notification registration";
      return true;
    }
    if (!cb->linkToDeath(death_handler_, cookie)) {
      LOG(ERROR) << "Failed to register death notification";
      return false;
    }
    cb_set_.insert(cb);
    return true;
  }

  const std::set<android::sp<CallbackType>>& getCallbacks() { return cb_set_; }

  // Death notification for callbacks.
  void onObjectDeath(uint64_t cookie) {
    CallbackType* cb = reinterpret_cast<CallbackType*>(cookie);
    const auto& iter = cb_set_.find(cb);
    if (iter == cb_set_.end()) {
      LOG(ERROR) << "Unknown callback death notification received";
      return;
    }
    cb_set_.erase(iter);
    LOG(DEBUG) << "Dead callback removed from list";
  }

  void invalidate() {
    for (const sp<CallbackType>& cb : cb_set_) {
      if (!cb->unlinkToDeath(death_handler_)) {
        LOG(ERROR) << "Failed to deregister death notification";
      }
    }
    cb_set_.clear();
  }

 private:
  std::set<sp<CallbackType>> cb_set_;
  sp<HidlDeathHandler<CallbackType>> death_handler_;

  DISALLOW_COPY_AND_ASSIGN(HidlCallbackHandler);
};

}  // namespace hidl_callback_util
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
#endif  // HIDL_CALLBACK_UTIL_H_
