//
// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#ifndef ANDROID_HARDWARE_CONFIGSTORE_UTILS_H
#define ANDROID_HARDWARE_CONFIGSTORE_UTILS_H

#include <android/hardware/configstore/1.0/types.h>
#include <hidl/Status.h>

#include <sstream>

namespace android {
namespace hardware {

namespace details {
// Templated classes can use the below method
// to avoid creating dependencies on liblog.
bool wouldLogInfo();
void logAlwaysInfo(const std::string& message);
void logAlwaysError(const std::string& message);
}  // namespace details

namespace configstore {
// import types from V1_0
using ::android::hardware::configstore::V1_0::OptionalBool;
using ::android::hardware::configstore::V1_0::OptionalInt32;
using ::android::hardware::configstore::V1_0::OptionalUInt32;
using ::android::hardware::configstore::V1_0::OptionalInt64;
using ::android::hardware::configstore::V1_0::OptionalUInt64;
using ::android::hardware::configstore::V1_0::OptionalString;

// a function to retrieve and cache the service handle
// for a particular interface
template <typename I>
sp<I> getService() {
    // static initializer used for synchronizations
    static sp<I> configs = I::getService();
    return configs;
}

// arguments V: type for the value (i.e., OptionalXXX)
//           I: interface class name
//           func: member function pointer
template<typename V, typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const V&)>)>
decltype(V::value) get(const decltype(V::value) &defValue) {
    using namespace android::hardware::details;
    // static initializer used for synchronizations
    auto getHelper = []()->V {
        V ret;
        sp<I> configs = getService<I>();

        if (!configs.get()) {
            // fallback to the default value
            ret.specified = false;
        } else {
            auto status = (*configs.*func)([&ret](V v) {
                ret = v;
            });
            if (!status.isOk()) {
                std::ostringstream oss;
                oss << "HIDL call failed for retrieving a config item from "
                       "configstore : "
                    << status.description().c_str();
                logAlwaysError(oss.str());
                ret.specified = false;
            }
        }

        return ret;
    };
    static V cachedValue = getHelper();

    if (wouldLogInfo()) {
        std::string iname = __PRETTY_FUNCTION__;
        // func name starts with "func = " in __PRETTY_FUNCTION__
        auto pos = iname.find("func = ");
        if (pos != std::string::npos) {
            iname = iname.substr(pos + sizeof("func = "));
            iname.pop_back();  // remove trailing ']'
        } else {
            iname += " (unknown)";
        }

        std::ostringstream oss;
        oss << iname << " retrieved: "
            << (cachedValue.specified ? cachedValue.value : defValue)
            << (cachedValue.specified ? "" : " (default)");
        logAlwaysInfo(oss.str());
    }

    return cachedValue.specified ? cachedValue.value : defValue;
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const OptionalBool&)>)>
bool getBool(const bool defValue) {
    return get<OptionalBool, I, func>(defValue);
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const OptionalInt32&)>)>
int32_t getInt32(const int32_t defValue) {
    return get<OptionalInt32, I, func>(defValue);
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const OptionalUInt32&)>)>
uint32_t getUInt32(const uint32_t defValue) {
    return get<OptionalUInt32, I, func>(defValue);
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const OptionalInt64&)>)>
int64_t getInt64(const int64_t defValue) {
    return get<OptionalInt64, I, func>(defValue);
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const OptionalUInt64&)>)>
uint64_t getUInt64(const uint64_t defValue) {
    return get<OptionalUInt64, I, func>(defValue);
}

template<typename I, android::hardware::Return<void> (I::* func)
        (std::function<void(const OptionalString&)>)>
std::string getString(const std::string &defValue) {
    return get<OptionalString, I, func>(defValue);
}

}  // namespace configstore
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CONFIGSTORE_UTILS_H
