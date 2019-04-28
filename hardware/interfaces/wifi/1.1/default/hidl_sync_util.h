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

#ifndef HIDL_SYNC_UTIL_H_
#define HIDL_SYNC_UTIL_H_

#include <mutex>

// Utility that provides a global lock to synchronize access between
// the HIDL thread and the legacy HAL's event loop.
namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace hidl_sync_util {
std::unique_lock<std::recursive_mutex> acquireGlobalLock();
}  // namespace hidl_sync_util
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android
#endif  // HIDL_SYNC_UTIL_H_
