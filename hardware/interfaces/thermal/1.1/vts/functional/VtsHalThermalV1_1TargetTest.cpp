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

#include <android/hardware/thermal/1.1/IThermal.h>
#include <android/hardware/thermal/1.1/IThermalCallback.h>
#include <android/hardware/thermal/1.0/types.h>

#include <VtsHalHidlTargetCallbackBase.h>
#include <VtsHalHidlTargetTestBase.h>

using ::android::hardware::thermal::V1_0::Temperature;
using ::android::hardware::thermal::V1_0::TemperatureType;
using ::android::hardware::thermal::V1_1::IThermal;
using ::android::hardware::thermal::V1_1::IThermalCallback;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

constexpr char kCallbackNameNotifyThrottling[] = "notifyThrottling";
static const Temperature kThrottleTemp = {
    .type = TemperatureType::CPU,
    .name = "test temperature sensor",
    .currentValue = 98.6,
    .throttlingThreshold = 58,
    .shutdownThreshold = 60,
    .vrThrottlingThreshold = 59,
};

class ThermalCallbackArgs {
   public:
     bool isThrottling;
     Temperature temperature;
};

// Callback class for receiving thermal event notifications from main class
class ThermalCallback
    : public ::testing::VtsHalHidlTargetCallbackBase<ThermalCallbackArgs>,
      public IThermalCallback {
   public:
    virtual ~ThermalCallback() = default;

    Return<void> notifyThrottling(bool isThrottling,
                                  const Temperature& temperature) override {
        ThermalCallbackArgs args;
        args.isThrottling = isThrottling;
        args.temperature = temperature;
        NotifyFromCallback(kCallbackNameNotifyThrottling, args);
        return Void();
    }
};

// The main test class for THERMAL HIDL HAL 1.1.
class ThermalHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        mThermal = ::testing::VtsHalHidlTargetTestBase::getService<IThermal>();
        ASSERT_NE(mThermal, nullptr);
        mThermalCallback = new(std::nothrow) ThermalCallback();
        ASSERT_NE(mThermalCallback, nullptr);
        auto ret = mThermal->registerThermalCallback(mThermalCallback);
        ASSERT_TRUE(ret.isOk());
    }

    virtual void TearDown() override {
        auto ret = mThermal->registerThermalCallback(nullptr);
        ASSERT_TRUE(ret.isOk());
    }

   protected:
    sp<IThermal> mThermal;
    sp<ThermalCallback> mThermalCallback;
}; // class ThermalHidlTest

// Test ThermalCallback::notifyThrottling().
// This just calls into and back from our local ThermalCallback impl.
// Note: a real thermal throttling event from the Thermal HAL could be
// inadvertently received here.
TEST_F(ThermalHidlTest, NotifyThrottlingTest) {
    auto ret = mThermalCallback->notifyThrottling(true, kThrottleTemp);
    ASSERT_TRUE(ret.isOk());
    auto res = mThermalCallback->WaitForCallback(kCallbackNameNotifyThrottling);
    EXPECT_TRUE(res.no_timeout);
    ASSERT_TRUE(res.args);
    EXPECT_EQ(true, res.args->isThrottling);
    EXPECT_EQ(kThrottleTemp, res.args->temperature);
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    cout << "Test result = " << status << std::endl;
    return status;
}
