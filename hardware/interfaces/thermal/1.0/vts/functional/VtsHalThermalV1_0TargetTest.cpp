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

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

#define LOG_TAG "thermal_hidl_hal_test"

#include <android-base/logging.h>
#include <android/hardware/thermal/1.0/IThermal.h>
#include <android/hardware/thermal/1.0/types.h>
#include <VtsHalHidlTargetTestBase.h>
#include <unistd.h>

using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::thermal::V1_0::CoolingDevice;
using ::android::hardware::thermal::V1_0::CpuUsage;
using ::android::hardware::thermal::V1_0::IThermal;
using ::android::hardware::thermal::V1_0::Temperature;
using ::android::hardware::thermal::V1_0::TemperatureType;
using ::android::hardware::thermal::V1_0::ThermalStatus;
using ::android::hardware::thermal::V1_0::ThermalStatusCode;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

#define MONITORING_OPERATION_NUMBER 10

#define MAX_DEVICE_TEMPERATURE 200
#define MAX_FAN_SPEED 20000

// The main test class for THERMAL HIDL HAL.
class ThermalHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
    thermal_ = ::testing::VtsHalHidlTargetTestBase::getService<IThermal>();
    ASSERT_NE(thermal_, nullptr);
    baseSize_ = 0;
    names_.clear();
  }

  virtual void TearDown() override {}

 protected:
  // Check validity of temperatures returned by Thremal HAL.
  void checkTemperatures(const hidl_vec<Temperature> temperatures) {
    size_t size = temperatures.size();
    EXPECT_LE(baseSize_, size);

    for (size_t i = 0; i < size; ++i) {
      checkDeviceTemperature(temperatures[i]);
      if (i < baseSize_) {
        EXPECT_EQ(names_[i], temperatures[i].name.c_str());
      } else {
          // Names must be unique only for known temperature types.
          if (temperatures[i].type != TemperatureType::UNKNOWN) {
              EXPECT_EQ(names_.end(), std::find(names_.begin(), names_.end(),
                                                temperatures[i].name.c_str()));
          }
          names_.push_back(temperatures[i].name);
      }
    }
    baseSize_ = size;
  }

  // Check validity of CPU usages returned by Thermal HAL.
  void checkCpuUsages(const hidl_vec<CpuUsage>& cpuUsages) {
    size_t size = cpuUsages.size();
    // A number of CPU's does not change.
    if (baseSize_ != 0) EXPECT_EQ(baseSize_, size);

    for (size_t i = 0; i < size; ++i) {
      checkCpuUsage(cpuUsages[i]);
      if (i < baseSize_) {
        EXPECT_EQ(names_[i], cpuUsages[i].name.c_str());
      } else {
          // Names are not guaranteed to be unique because of the current
          // default Thermal HAL implementation.
          names_.push_back(cpuUsages[i].name);
      }
    }
    baseSize_ = size;
  }

  // Check validity of cooling devices information returned by Thermal HAL.
  void checkCoolingDevices(const hidl_vec<CoolingDevice> coolingDevices) {
    size_t size = coolingDevices.size();
    EXPECT_LE(baseSize_, size);

    for (size_t i = 0; i < size; ++i) {
      checkCoolingDevice(coolingDevices[i]);
      if (i < baseSize_) {
        EXPECT_EQ(names_[i], coolingDevices[i].name.c_str());
      } else {
        // Names must be unique.
        EXPECT_EQ(names_.end(), std::find(names_.begin(), names_.end(),
                                          coolingDevices[i].name.c_str()));
        names_.push_back(coolingDevices[i].name);
      }
    }
    baseSize_ = size;
  }

  sp<IThermal> thermal_;

 private:
  // Check validity of temperature returned by Thermal HAL.
  void checkDeviceTemperature(const Temperature& temperature) {
    // .currentValue of known type is in Celsius and must be reasonable.
    EXPECT_TRUE(temperature.type == TemperatureType::UNKNOWN ||
                std::abs(temperature.currentValue) < MAX_DEVICE_TEMPERATURE ||
                isnan(temperature.currentValue));

    // .name must not be empty.
    EXPECT_LT(0u, temperature.name.size());

    // .currentValue must not exceed .shutdwonThreshold if defined.
    EXPECT_TRUE(temperature.currentValue < temperature.shutdownThreshold ||
                isnan(temperature.currentValue) || isnan(temperature.shutdownThreshold));

    // .throttlingThreshold must not exceed .shutdownThreshold if defined.
    EXPECT_TRUE(temperature.throttlingThreshold < temperature.shutdownThreshold ||
                isnan(temperature.throttlingThreshold) || isnan(temperature.shutdownThreshold));
  }

  // Check validity of CPU usage returned by Thermal HAL.
  void checkCpuUsage(const CpuUsage& cpuUsage) {
    // .active must be less than .total if CPU is online.
    EXPECT_TRUE(!cpuUsage.isOnline ||
                (cpuUsage.active >= 0 && cpuUsage.total >= 0 &&
                 cpuUsage.total >= cpuUsage.active));

    // .name must be not empty.
    EXPECT_LT(0u, cpuUsage.name.size());
  }

  // Check validity of a cooling device information returned by Thermal HAL.
  void checkCoolingDevice(const CoolingDevice& coolingDevice) {
    EXPECT_LE(0, coolingDevice.currentValue);
    EXPECT_GT(MAX_FAN_SPEED, coolingDevice.currentValue);
    EXPECT_LT(0u, coolingDevice.name.size());
  }

  size_t baseSize_;
  std::vector<hidl_string> names_;
};

// Sanity test for Thermal::getTemperatures().
TEST_F(ThermalHidlTest, TemperatureTest) {
  hidl_vec<Temperature> passed;
  for (size_t i = 0; i < MONITORING_OPERATION_NUMBER; ++i) {
    thermal_->getTemperatures(
        [&passed](ThermalStatus status, hidl_vec<Temperature> temperatures) {
          EXPECT_EQ(ThermalStatusCode::SUCCESS, status.code);
          passed = temperatures;
        });

    checkTemperatures(passed);
    sleep(1);
  }
}

// Sanity test for Thermal::getCpuUsages().
TEST_F(ThermalHidlTest, CpuUsageTest) {
  hidl_vec<CpuUsage> passed;
  for (size_t i = 0; i < MONITORING_OPERATION_NUMBER; ++i) {
    thermal_->getCpuUsages(
        [&passed](ThermalStatus status, hidl_vec<CpuUsage> cpuUsages) {
          EXPECT_EQ(ThermalStatusCode::SUCCESS, status.code);
          passed = cpuUsages;
        });

    checkCpuUsages(passed);
    sleep(1);
  }
}

// Sanity test for Thermal::getCoolingDevices().
TEST_F(ThermalHidlTest, CoolingDeviceTest) {
  hidl_vec<CoolingDevice> passed;
  for (size_t i = 0; i < MONITORING_OPERATION_NUMBER; ++i) {
    thermal_->getCoolingDevices([&passed](
        ThermalStatus status, hidl_vec<CoolingDevice> coolingDevices) {
      EXPECT_EQ(ThermalStatusCode::SUCCESS, status.code);
      passed = coolingDevices;
    });

    checkCoolingDevices(passed);
    sleep(1);
  }
}

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  LOG(INFO) << "Test result = " << status;
  return status;
}
