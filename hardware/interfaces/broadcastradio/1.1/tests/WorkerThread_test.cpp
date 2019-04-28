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

#include <broadcastradio-utils/WorkerThread.h>
#include <gtest/gtest.h>

namespace {

using namespace std::chrono_literals;

using android::WorkerThread;

using std::atomic;
using std::chrono::time_point;
using std::chrono::steady_clock;
using std::is_sorted;
using std::lock_guard;
using std::mutex;
using std::this_thread::sleep_for;
using std::vector;

#define ASSERT_EQ_WITH_TOLERANCE(val1, val2, tolerance) \
    ASSERT_LE((val1) - (tolerance), (val2));            \
    ASSERT_GE((val1) + (tolerance), (val2));

TEST(WorkerThreadTest, oneTask) {
    atomic<bool> executed(false);
    atomic<time_point<steady_clock>> stop;
    WorkerThread thread;

    auto start = steady_clock::now();
    thread.schedule(
        [&]() {
            stop = steady_clock::now();
            executed = true;
        },
        100ms);

    sleep_for(150ms);

    ASSERT_TRUE(executed);
    auto delta = stop.load() - start;
    ASSERT_EQ_WITH_TOLERANCE(delta, 100ms, 50ms);
}

TEST(WorkerThreadTest, cancelSecond) {
    atomic<bool> executed1(false);
    atomic<bool> executed2(false);
    WorkerThread thread;

    thread.schedule([&]() { executed2 = true; }, 100ms);
    thread.schedule([&]() { executed1 = true; }, 25ms);

    sleep_for(50ms);
    thread.cancelAll();
    sleep_for(100ms);

    ASSERT_TRUE(executed1);
    ASSERT_FALSE(executed2);
}

TEST(WorkerThreadTest, executeInOrder) {
    mutex mut;
    vector<int> order;
    WorkerThread thread;

    thread.schedule(
        [&]() {
            lock_guard<mutex> lk(mut);
            order.push_back(0);
        },
        50ms);

    thread.schedule(
        [&]() {
            lock_guard<mutex> lk(mut);
            order.push_back(4);
        },
        400ms);

    thread.schedule(
        [&]() {
            lock_guard<mutex> lk(mut);
            order.push_back(1);
        },
        100ms);

    thread.schedule(
        [&]() {
            lock_guard<mutex> lk(mut);
            order.push_back(3);
        },
        300ms);

    thread.schedule(
        [&]() {
            lock_guard<mutex> lk(mut);
            order.push_back(2);
        },
        200ms);

    sleep_for(500ms);

    ASSERT_EQ(5u, order.size());
    ASSERT_TRUE(is_sorted(order.begin(), order.end()));
}

TEST(WorkerThreadTest, dontExecuteAfterDestruction) {
    atomic<bool> executed1(false);
    atomic<bool> executed2(false);
    {
        WorkerThread thread;

        thread.schedule([&]() { executed2 = true; }, 100ms);
        thread.schedule([&]() { executed1 = true; }, 25ms);

        sleep_for(50ms);
    }
    sleep_for(100ms);

    ASSERT_TRUE(executed1);
    ASSERT_FALSE(executed2);
}

}  // anonymous namespace
