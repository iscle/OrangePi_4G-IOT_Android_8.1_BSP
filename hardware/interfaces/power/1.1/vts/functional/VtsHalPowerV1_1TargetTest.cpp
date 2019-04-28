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

#define LOG_TAG "power_hidl_hal_test"
#include <android-base/logging.h>
#include <android/hardware/power/1.1/IPower.h>

#include <VtsHalHidlTargetTestBase.h>

using ::android::hardware::power::V1_1::IPower;
using ::android::hardware::power::V1_1::PowerStateSubsystem;
using ::android::hardware::power::V1_0::Status;
using ::android::hardware::power::V1_0::PowerHint;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::sp;

class PowerHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
    power = ::testing::VtsHalHidlTargetTestBase::getService<IPower>();
    ASSERT_NE(power, nullptr);
  }

  virtual void TearDown() override {}

  sp<IPower> power;
};

// Sanity check Power::getSubsystemLowPowerStats().
TEST_F(PowerHidlTest, GetSubsystemLowPowerStats) {
  hidl_vec<PowerStateSubsystem> vec;
  Status s;
  auto cb = [&vec, &s](hidl_vec<PowerStateSubsystem> subsystems,
                       Status status) {
    vec = subsystems;
    s = status;
  };

  Return<void> ret = power->getSubsystemLowPowerStats(cb);
  ASSERT_TRUE(ret.isOk());
  ASSERT_TRUE(s == Status::SUCCESS || s == Status::FILESYSTEM_ERROR);
}

// Sanity check Power::powerHintAsync on good and bad inputs.
TEST_F(PowerHidlTest, PowerHintAsync) {
    PowerHint badHint = static_cast<PowerHint>(0xA);
    auto hints = {PowerHint::VSYNC,        PowerHint::INTERACTION, PowerHint::VIDEO_ENCODE,
                  PowerHint::VIDEO_DECODE, PowerHint::LOW_POWER,   PowerHint::SUSTAINED_PERFORMANCE,
                  PowerHint::VR_MODE,      PowerHint::LAUNCH,      badHint};
    Return<void> ret;
    for (auto hint : hints) {
        ret = power->powerHintAsync(hint, 30000);
        ASSERT_TRUE(ret.isOk());

        ret = power->powerHintAsync(hint, 0);
        ASSERT_TRUE(ret.isOk());
    }

    // Turning these hints on in different orders triggers different code paths,
    // so iterate over possible orderings.
    std::vector<PowerHint> hints2 = {PowerHint::LAUNCH, PowerHint::VR_MODE,
                                     PowerHint::SUSTAINED_PERFORMANCE, PowerHint::INTERACTION};
    auto compareHints = [](PowerHint l, PowerHint r) {
        return static_cast<uint32_t>(l) < static_cast<uint32_t>(r);
    };
    std::sort(hints2.begin(), hints2.end(), compareHints);
    do {
        for (auto iter = hints2.begin(); iter != hints2.end(); iter++) {
            ret = power->powerHintAsync(*iter, 0);
            ASSERT_TRUE(ret.isOk());
        }
        for (auto iter = hints2.begin(); iter != hints2.end(); iter++) {
            ret = power->powerHintAsync(*iter, 30000);
            ASSERT_TRUE(ret.isOk());
        }
    } while (std::next_permutation(hints2.begin(), hints2.end(), compareHints));
}

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  LOG(INFO) << "Test result = " << status;
  return status;
}
