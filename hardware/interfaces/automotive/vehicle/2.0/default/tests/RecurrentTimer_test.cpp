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

#include <thread>

#include <gtest/gtest.h>

#include "vhal_v2_0/RecurrentTimer.h"

namespace {

using std::chrono::nanoseconds;
using std::chrono::milliseconds;

#define ASSERT_EQ_WITH_TOLERANCE(val1, val2, tolerance) \
ASSERT_LE(val1 - tolerance, val2); \
ASSERT_GE(val1 + tolerance, val2); \


TEST(RecurrentTimerTest, oneInterval) {
    std::atomic<int64_t> counter { 0L };
    auto counterRef = std::ref(counter);
    RecurrentTimer timer([&counterRef](const std::vector<int32_t>& cookies) {
        ASSERT_EQ(1u, cookies.size());
        ASSERT_EQ(0xdead, cookies.front());
        counterRef.get()++;
    });

    timer.registerRecurrentEvent(milliseconds(1), 0xdead);
    std::this_thread::sleep_for(milliseconds(100));
    ASSERT_EQ_WITH_TOLERANCE(100, counter.load(), 20);
}

TEST(RecurrentTimerTest, multipleIntervals) {
    std::atomic<int64_t> counter1ms { 0L };
    std::atomic<int64_t> counter5ms { 0L };
    auto counter1msRef = std::ref(counter1ms);
    auto counter5msRef = std::ref(counter5ms);
    RecurrentTimer timer(
            [&counter1msRef, &counter5msRef](const std::vector<int32_t>& cookies) {
        for (int32_t cookie : cookies) {
            if (cookie == 0xdead) {
                counter1msRef.get()++;
            } else if (cookie == 0xbeef) {
                counter5msRef.get()++;
            } else {
                FAIL();
            }
        }
    });

    timer.registerRecurrentEvent(milliseconds(1), 0xdead);
    timer.registerRecurrentEvent(milliseconds(5), 0xbeef);

    std::this_thread::sleep_for(milliseconds(100));
    ASSERT_EQ_WITH_TOLERANCE(100, counter1ms.load(), 20);
    ASSERT_EQ_WITH_TOLERANCE(20, counter5ms.load(), 5);
}

}  // anonymous namespace
