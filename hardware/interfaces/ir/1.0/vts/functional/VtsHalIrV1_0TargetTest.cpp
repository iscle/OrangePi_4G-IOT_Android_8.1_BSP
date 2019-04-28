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

#define LOG_TAG "ir_hidl_hal_test"

#include <android-base/logging.h>

#include <android/hardware/ir/1.0/IConsumerIr.h>
#include <android/hardware/ir/1.0/types.h>

#include <VtsHalHidlTargetTestBase.h>
#include <algorithm>

using ::android::hardware::ir::V1_0::IConsumerIr;
using ::android::hardware::ir::V1_0::ConsumerIrFreqRange;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::sp;

// The main test class for IR HIDL HAL.
class ConsumerIrHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
    ir = ::testing::VtsHalHidlTargetTestBase::getService<IConsumerIr>();
    ASSERT_NE(ir, nullptr);
  }

  virtual void TearDown() override {}

  sp<IConsumerIr> ir;
};

// Test transmit() for the min and max frequency of every available range
TEST_F(ConsumerIrHidlTest, TransmitTest) {
  bool success;
  hidl_vec<ConsumerIrFreqRange> ranges;
  auto cb = [&](bool s, hidl_vec<ConsumerIrFreqRange> v) {
    ranges = v;
    success = s;
  };
  Return<void> ret = ir->getCarrierFreqs(cb);
  ASSERT_TRUE(ret.isOk());
  ASSERT_TRUE(success);

  if (ranges.size() > 0) {
    uint32_t len = 16;
    hidl_vec<int32_t> vec;
    vec.resize(len);
    std::fill(vec.begin(), vec.end(), 1000);
    for (auto range = ranges.begin(); range != ranges.end(); range++) {
      EXPECT_TRUE(ir->transmit(range->min, vec));
      EXPECT_TRUE(ir->transmit(range->max, vec));
    }
  }
}

// Test transmit() when called with invalid frequencies
TEST_F(ConsumerIrHidlTest, BadFreqTest) {
  uint32_t len = 16;
  hidl_vec<int32_t> vec;
  vec.resize(len);
  std::fill(vec.begin(), vec.end(), 1);
  EXPECT_FALSE(ir->transmit(-1, vec));
}

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  LOG(INFO) << "Test result = " << status;
  return status;
}
