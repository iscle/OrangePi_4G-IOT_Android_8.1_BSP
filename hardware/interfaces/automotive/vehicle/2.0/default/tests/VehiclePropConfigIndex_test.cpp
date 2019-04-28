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

#include <gtest/gtest.h>

#include "vhal_v2_0/VehiclePropConfigIndex.h"

#include "VehicleHalTestUtils.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace {

class PropConfigTest : public ::testing::Test {
protected:
    void SetUp() override {
        configs.assign(std::begin(kVehicleProperties),
                       std::end(kVehicleProperties));
    }

    void TearDown() override {}

public:
    std::vector<VehiclePropConfig> configs;
};

TEST_F(PropConfigTest, hasConfig) {
    VehiclePropConfigIndex index(configs);

    ASSERT_TRUE(index.hasConfig(toInt(VehicleProperty::HVAC_FAN_SPEED)));
    ASSERT_TRUE(index.hasConfig(toInt(VehicleProperty::INFO_MAKE)));
    ASSERT_TRUE(index.hasConfig(toInt(VehicleProperty::INFO_FUEL_CAPACITY)));

    ASSERT_FALSE(index.hasConfig(toInt(VehicleProperty::INVALID)));
}

TEST_F(PropConfigTest, getAllConfig) {
    VehiclePropConfigIndex index(configs);

    std::vector<VehiclePropConfig> actualConfigs = index.getAllConfigs();
    ASSERT_EQ(configs.size(), actualConfigs.size());

    for (size_t i = 0; i < actualConfigs.size(); i++) {
        ASSERT_EQ(toString(configs[i]), toString(actualConfigs[i]));
    }
}

TEST_F(PropConfigTest, getConfigs) {
    VehiclePropConfigIndex index(configs);
    auto actualConfig = index.getConfig(toInt(VehicleProperty::HVAC_FAN_SPEED));
    ASSERT_EQ(toString(configs[1]), toString(actualConfig));
}

}  // namespace anonymous

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
