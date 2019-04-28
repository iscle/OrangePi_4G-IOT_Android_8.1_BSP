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
#ifndef ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ASSERTOK_H
#define ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ASSERTOK_H

#include <algorithm>
#include <vector>

#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace test {
namespace utility {

namespace detail {

// This is a detail namespace, thus it is OK to import a class as nobody else is
// allowed to use it
using ::android::hardware::Return;
using ::android::hardware::audio::V2_0::Result;

template <class T>
inline ::testing::AssertionResult assertIsOk(const char* expr, const Return<T>& ret) {
    return ::testing::AssertionResult(ret.isOk())
           << "Expected: " << expr << "\n to be an OK Return but it is not: " << ret.description();
}

// Call continuation if the provided result isOk
template <class T, class Continuation>
inline ::testing::AssertionResult continueIfIsOk(const char* expr, const Return<T>& ret,
                                                 Continuation continuation) {
    auto isOkStatus = assertIsOk(expr, ret);
    return !isOkStatus ? isOkStatus : continuation();
}

// Expect two equal Results
inline ::testing::AssertionResult assertResult(const char* e_expr, const char* r_expr,
                                               Result expected, Result result) {
    return ::testing::AssertionResult(expected == result)
           << "Value of: " << r_expr << "\n  Actual: " << ::testing::PrintToString(result)
           << "\nExpected: " << e_expr << "\nWhich is: " << ::testing::PrintToString(expected);
}

// Expect two equal Results one being wrapped in an OK Return
inline ::testing::AssertionResult assertResult(const char* e_expr, const char* r_expr,
                                               Result expected, const Return<Result>& ret) {
    return continueIfIsOk(r_expr, ret,
                          [&] { return assertResult(e_expr, r_expr, expected, Result{ret}); });
}

// Expect a Result to be part of a list of Results
inline ::testing::AssertionResult assertResult(const char* e_expr, const char* r_expr,
                                               const std::vector<Result>& expected, Result result) {
    if (std::find(expected.begin(), expected.end(), result) != expected.end()) {
        return ::testing::AssertionSuccess();  // result is in expected
    }
    return ::testing::AssertionFailure()
           << "Value of: " << r_expr << "\n  Actual: " << ::testing::PrintToString(result)
           << "\nExpected one of: " << e_expr
           << "\n       Which is: " << ::testing::PrintToString(expected);
}

// Expect a Result wrapped in an OK Return to be part of a list of Results
inline ::testing::AssertionResult assertResult(const char* e_expr, const char* r_expr,
                                               const std::vector<Result>& expected,
                                               const Return<Result>& ret) {
    return continueIfIsOk(r_expr, ret,
                          [&] { return assertResult(e_expr, r_expr, expected, Result{ret}); });
}

inline ::testing::AssertionResult assertOk(const char* expr, const Return<void>& ret) {
    return assertIsOk(expr, ret);
}

inline ::testing::AssertionResult assertOk(const char* expr, Result result) {
    return ::testing::AssertionResult(result == Result::OK)
           << "Expected success: " << expr << "\nActual: " << ::testing::PrintToString(result);
}

inline ::testing::AssertionResult assertOk(const char* expr, const Return<Result>& ret) {
    return continueIfIsOk(expr, ret, [&] { return assertOk(expr, Result{ret}); });
}
}

#define ASSERT_IS_OK(ret) ASSERT_PRED_FORMAT1(detail::assertIsOk, ret)
#define EXPECT_IS_OK(ret) EXPECT_PRED_FORMAT1(detail::assertIsOk, ret)

// Test anything provided is and contains only OK
#define ASSERT_OK(ret) ASSERT_PRED_FORMAT1(detail::assertOk, ret)
#define EXPECT_OK(ret) EXPECT_PRED_FORMAT1(detail::assertOk, ret)

#define ASSERT_RESULT(expected, ret) ASSERT_PRED_FORMAT2(detail::assertResult, expected, ret)
#define EXPECT_RESULT(expected, ret) EXPECT_PRED_FORMAT2(detail::assertResult, expected, ret)

}  // utility
}  // test
}  // common
}  // audio
}  // test
}  // utility

#endif  // ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_ASSERTOK_H
