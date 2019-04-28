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

#define LOG_TAG "health_hidl_hal_test"

#include <android/hardware/health/1.0/IHealth.h>
#include <android/hardware/health/1.0/types.h>
#include <log/log.h>
#include <VtsHalHidlTargetTestBase.h>

using HealthConfig = ::android::hardware::health::V1_0::HealthConfig;
using HealthInfo = ::android::hardware::health::V1_0::HealthInfo;
using IHealth = ::android::hardware::health::V1_0::IHealth;
using Result = ::android::hardware::health::V1_0::Result;

using ::android::sp;

class HealthHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        health = ::testing::VtsHalHidlTargetTestBase::getService<IHealth>();
        ASSERT_NE(health, nullptr);
        health->init(config,
                     [&](const auto& halConfigOut) { config = halConfigOut; });
    }

    sp<IHealth> health;
    HealthConfig config;
};

/**
 * Ensure EnergyCounter call returns positive energy counter or NOT_SUPPORTED
 */
TEST_F(HealthHidlTest, TestEnergyCounter) {
    Result result;
    int64_t energy = 0;
    health->energyCounter([&](Result ret, int64_t energyOut) {
        result = ret;
        energy = energyOut;
    });

    ASSERT_TRUE(result == Result::SUCCESS || result == Result::NOT_SUPPORTED);
    ASSERT_TRUE(result != Result::SUCCESS || energy > 0);
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    ALOGI("Test result = %d", status);
    return status;
}
