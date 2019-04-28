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

#include <thread>

#include <gtest/gtest.h>

#include <utils/SystemClock.h>

#include "vhal_v2_0/VehicleObjectPool.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace {

class VehicleObjectPoolTest : public ::testing::Test {
protected:
    void SetUp() override {
        stats = PoolStats::instance();
        resetStats();
        valuePool.reset(new VehiclePropValuePool);
    }

    void TearDown() override {
        // At the end, all created objects should be either recycled or deleted.
        // Some objects could be recycled multiple times, that's why it's <=
        ASSERT_EQ(stats->Obtained, stats->Recycled);
        ASSERT_LE(stats->Created, stats->Recycled);
    }
private:
    void resetStats() {
        stats->Obtained = 0;
        stats->Created = 0;
        stats->Recycled = 0;
    }

public:
    PoolStats* stats;
    std::unique_ptr<VehiclePropValuePool> valuePool;
};

TEST_F(VehicleObjectPoolTest, valuePoolBasicCorrectness) {
    void* raw = valuePool->obtain(VehiclePropertyType::INT32).get();
    // At this point, v1 should be recycled and the only object in the pool.
    ASSERT_EQ(raw, valuePool->obtain(VehiclePropertyType::INT32).get());
    // Obtaining value of another type - should return a new object
    ASSERT_NE(raw, valuePool->obtain(VehiclePropertyType::FLOAT).get());

    ASSERT_EQ(3u, stats->Obtained);
    ASSERT_EQ(2u, stats->Created);
}

TEST_F(VehicleObjectPoolTest, valuePoolStrings) {
    valuePool->obtain(VehiclePropertyType::STRING);
    auto vs = valuePool->obtain(VehiclePropertyType::STRING);
    vs->value.stringValue = "Hello";
    void* raw = vs.get();
    vs.reset();  // delete the pointer

    auto vs2 = valuePool->obtain(VehiclePropertyType::STRING);
    ASSERT_EQ(0u, vs2->value.stringValue.size());
    ASSERT_NE(raw, valuePool->obtain(VehiclePropertyType::STRING).get());

    ASSERT_EQ(0u, stats->Obtained);
}

TEST_F(VehicleObjectPoolTest, valuePoolMultithreadedBenchmark) {
    // In this test we have T threads that concurrently in C cycles
    // obtain and release O VehiclePropValue objects of FLOAT / INT32 types.

    const int T = 2;
    const int C = 500;
    const int O = 100;

    auto poolPtr = valuePool.get();

    std::vector<std::thread> threads;
    auto start = elapsedRealtimeNano();
    for (int i = 0; i < T; i++) {
        threads.push_back(std::thread([&poolPtr] () {
            for (int j = 0; j < C; j++) {
                std::vector<recyclable_ptr<VehiclePropValue>> vec;
                for (int k = 0; k < O; k++) {
                    vec.push_back(
                        poolPtr->obtain(k % 2 == 0
                                        ? VehiclePropertyType::FLOAT
                                        : VehiclePropertyType::INT32));
                }
            }
        }));
    }

    for (auto& t : threads) {
        t.join();
    }
    auto finish = elapsedRealtimeNano();

    ASSERT_EQ(static_cast<uint32_t>(T * C * O), stats->Obtained);
    ASSERT_EQ(static_cast<uint32_t>(T * C * O), stats->Recycled);
    // Created less than obtained.
    ASSERT_GE(static_cast<uint32_t>(T * O), stats->Created);

    auto elapsedMs = (finish - start) / 1000000;
    ASSERT_GE(1000, elapsedMs);  // Less a second to access 100K objects.
                                 // Typically it takes about 0.1s on Nexus6P.
}

}  // namespace anonymous

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
