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

#ifndef ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ENVIRONMENT_TEARDOWN
#define ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ENVIRONMENT_TEARDOWN

#include <android-base/logging.h>

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace test {
namespace utility {

namespace doc {
namespace detail {
const char* getTestName() {
    return ::testing::UnitTest::GetInstance()->current_test_info()->name();
}
}  // namespace detail

/** Document the current test case.
 * Eg: calling `doc::test("Dump the state of the hal")` in the "debugDump" test
 * will output:
 *   <testcase name="debugDump" status="run" time="6"
 *             classname="AudioPrimaryHidlTest"
               description="Dump the state of the hal." />
 * see
 https://github.com/google/googletest/blob/master/googletest/docs/AdvancedGuide.md#logging-additional-information
 */
void test(const std::string& testCaseDocumentation) {
    ::testing::Test::RecordProperty("description", testCaseDocumentation);
}

/** Document why a test was not fully run. Usually due to an optional feature
 * not implemented. */
void partialTest(const std::string& reason) {
    LOG(INFO) << "Test " << detail::getTestName() << " partially run: " << reason;
    ::testing::Test::RecordProperty("partialyRunTest", reason);
}

/** Add a note to the test. */
void note(const std::string& note) {
    LOG(INFO) << "Test " << detail::getTestName() << " noted: " << note;
    ::testing::Test::RecordProperty("note", note);
}
}  // namespace doc

}  // utility
}  // test
}  // common
}  // audio
}  // test
}  // utility

#endif  // ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ENVIRONMENT_TEARDOWN
