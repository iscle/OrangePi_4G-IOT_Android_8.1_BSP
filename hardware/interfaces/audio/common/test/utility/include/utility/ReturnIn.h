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

#ifndef ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_RETURN_IN_H
#define ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_RETURN_IN_H

#include <tuple>

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace test {
namespace utility {

namespace detail {
// Helper class to generate the HIDL synchronous callback
template <class... ResultStore>
class ReturnIn {
   public:
    // Provide to the constructor the variables where the output parameters must be copied
    // TODO: take pointers to match google output parameter style ?
    ReturnIn(ResultStore&... ts) : results(ts...) {}
    // Synchronous callback
    template <class... Results>
    void operator()(Results&&... results) {
        set(std::forward<Results>(results)...);
    }

   private:
    // Recursively set all output parameters
    template <class Head, class... Tail>
    void set(Head&& head, Tail&&... tail) {
        std::get<sizeof...(ResultStore) - sizeof...(Tail) - 1>(results) = std::forward<Head>(head);
        set(tail...);
    }
    // Trivial case
    void set() {}

    // All variables to set are stored here
    std::tuple<ResultStore&...> results;
};
}  // namespace detail

// Generate the HIDL synchronous callback with a copy policy
// Input: the variables (lvalue reference) where to save the return values
// Output: the callback to provide to a HIDL call with a synchronous callback
// The output parameters *will be copied* do not use this function if you have
// a zero copy policy
template <class... ResultStore>
detail::ReturnIn<ResultStore...> returnIn(ResultStore&... ts) {
    return {ts...};
}

}  // utility
}  // test
}  // common
}  // audio
}  // test
}  // utility

#endif  // ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_RETURN_IN_H
