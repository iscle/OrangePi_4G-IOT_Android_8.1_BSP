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

#ifndef ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ENVIRONMENT_TEARDOWN_H
#define ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ENVIRONMENT_TEARDOWN_H

#include <functional>
#include <list>

#include <gtest/gtest.h>

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace test {
namespace utility {

/** Register callback for static object destruction
 * Avoid destroying static objects after main return.
 * Post main return destruction leads to incorrect gtest timing measurements as
 * well as harder debuging if anything goes wrong during destruction. */
class Environment : public ::testing::Environment {
   public:
    using TearDownFunc = std::function<void()>;
    void registerTearDown(TearDownFunc&& tearDown) { tearDowns.push_back(std::move(tearDown)); }

   private:
    void TearDown() override {
        // Call the tear downs in reverse order of insertion
        for (auto& tearDown : tearDowns) {
            tearDown();
        }
    }
    std::list<TearDownFunc> tearDowns;
};

}  // utility
}  // test
}  // common
}  // audio
}  // test
}  // utility

#endif  // ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ENVIRONMENT_TEARDOWN_H
