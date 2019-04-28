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

#define LOG_TAG "vr_hidl_hal_test"
#include <VtsHalHidlTargetTestBase.h>
#include <android-base/logging.h>
#include <android/hardware/vr/1.0/IVr.h>
#include <hardware/vr.h>
#include <log/log.h>

using ::android::hardware::vr::V1_0::IVr;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

// The main test class for VR HIDL HAL.
class VrHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  void SetUp() override {
    vr = ::testing::VtsHalHidlTargetTestBase::getService<IVr>();
    ASSERT_NE(vr, nullptr);
  }

  void TearDown() override {}

  sp<IVr> vr;
};


// A class for test environment setup (kept since this file is a template).
class VrHidlEnvironment : public ::testing::Environment {
 public:
  void SetUp() {}
  void TearDown() {}

 private:
};

// Sanity check that Vr::init does not crash.
TEST_F(VrHidlTest, Init) {
  EXPECT_TRUE(vr->init().isOk());
}

// Sanity check Vr::setVrMode is able to enable and disable VR mode.
TEST_F(VrHidlTest, SetVrMode) {
  EXPECT_TRUE(vr->init().isOk());
  EXPECT_TRUE(vr->setVrMode(true).isOk());
  EXPECT_TRUE(vr->setVrMode(false).isOk());
}

// Sanity check that Vr::init and Vr::setVrMode can be used in any order.
TEST_F(VrHidlTest, ReInit) {
  EXPECT_TRUE(vr->init().isOk());
  EXPECT_TRUE(vr->setVrMode(true).isOk());
  EXPECT_TRUE(vr->init().isOk());
  EXPECT_TRUE(vr->setVrMode(false).isOk());
  EXPECT_TRUE(vr->init().isOk());
  EXPECT_TRUE(vr->setVrMode(false).isOk());
}

int main(int argc, char **argv) {
  ::testing::AddGlobalTestEnvironment(new VrHidlEnvironment);
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
