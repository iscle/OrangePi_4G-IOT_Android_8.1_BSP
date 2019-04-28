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

#ifndef android_hardware_automotive_vehicle_V2_0_VehicleDebugUtils_H_
#define android_hardware_automotive_vehicle_V2_0_VehicleDebugUtils_H_

#include <android/hardware/automotive/vehicle/2.0/types.h>
#include <ios>
#include <sstream>

#include "vhal_v2_0/VehicleUtils.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

constexpr int32_t kCustomComplexProperty = 0xbeef
        | VehiclePropertyGroup::VENDOR
        | VehiclePropertyType::COMPLEX
        | VehicleArea::GLOBAL;

const VehiclePropConfig kVehicleProperties[] = {
    {
        .prop = toInt(VehicleProperty::INFO_MAKE),
        .access = VehiclePropertyAccess::READ,
        .changeMode = VehiclePropertyChangeMode::STATIC,
        .configString = "Some=config,options=if,you=have_any",
    },

    {
        .prop = toInt(VehicleProperty::HVAC_FAN_SPEED),
        .access = VehiclePropertyAccess::READ_WRITE,
        .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
        .supportedAreas = static_cast<int32_t>(
            VehicleAreaZone::ROW_1_LEFT | VehicleAreaZone::ROW_1_RIGHT),
        .areaConfigs = {
            VehicleAreaConfig {
                .areaId = toInt(VehicleAreaZone::ROW_1_LEFT),
                .minInt32Value = 1,
                .maxInt32Value = 7},
            VehicleAreaConfig {
                .areaId = toInt(VehicleAreaZone::ROW_1_RIGHT),
                .minInt32Value = 1,
                .maxInt32Value = 5,
            }
        }
    },

    // Write-only property
    {
        .prop = toInt(VehicleProperty::HVAC_SEAT_TEMPERATURE),
        .access = VehiclePropertyAccess::WRITE,
        .changeMode = VehiclePropertyChangeMode::ON_SET,
        .supportedAreas = static_cast<int32_t>(
            VehicleAreaZone::ROW_1_LEFT | VehicleAreaZone::ROW_1_RIGHT),
        .areaConfigs = {
            VehicleAreaConfig {
                .areaId = toInt(VehicleAreaZone::ROW_1_LEFT),
                .minInt32Value = 64,
                .maxInt32Value = 80},
            VehicleAreaConfig {
                .areaId = toInt(VehicleAreaZone::ROW_1_RIGHT),
                .minInt32Value = 64,
                .maxInt32Value = 80,
            }
        }
    },

    {
        .prop = toInt(VehicleProperty::INFO_FUEL_CAPACITY),
        .access = VehiclePropertyAccess::READ,
        .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
        .areaConfigs = {
            VehicleAreaConfig {
                .minFloatValue = 0,
                .maxFloatValue = 1.0
            }
        }
    },

    {
        .prop = toInt(VehicleProperty::DISPLAY_BRIGHTNESS),
        .access = VehiclePropertyAccess::READ_WRITE,
        .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
        .areaConfigs = {
            VehicleAreaConfig {
                .minInt32Value = 0,
                .maxInt32Value = 10
            }
        }
    },

    {
        .prop = toInt(VehicleProperty::MIRROR_FOLD),
        .access = VehiclePropertyAccess::READ_WRITE,
        .changeMode = VehiclePropertyChangeMode::ON_CHANGE,

    },

    // Complex data type.
    {
        .prop = kCustomComplexProperty,
        .access = VehiclePropertyAccess::READ_WRITE,
        .changeMode = VehiclePropertyChangeMode::ON_CHANGE
    }
};

constexpr auto kTimeout = std::chrono::milliseconds(500);

class MockedVehicleCallback : public IVehicleCallback {
private:
    using MuxGuard = std::lock_guard<std::mutex>;
    using HidlVecOfValues = hidl_vec<VehiclePropValue>;
public:
    // Methods from ::android::hardware::automotive::vehicle::V2_0::IVehicleCallback follow.
    Return<void> onPropertyEvent(
            const hidl_vec<VehiclePropValue>& values) override {
        {
            MuxGuard  g(mLock);
            mReceivedEvents.push_back(values);
        }
        mEventCond.notify_one();
        return Return<void>();
    }
    Return<void> onPropertySet(const VehiclePropValue& /* value */) override {
        return Return<void>();
    }
    Return<void> onPropertySetError(StatusCode /* errorCode */,
                                    int32_t /* propId */,
                                    int32_t /* areaId */) override {
        return Return<void>();
    }

    bool waitForExpectedEvents(size_t expectedEvents) {
        std::unique_lock<std::mutex> g(mLock);

        if (expectedEvents == 0 && mReceivedEvents.size() == 0) {
            // No events expected, let's sleep a little bit to make sure
            // nothing will show up.
            return mEventCond.wait_for(g, kTimeout) == std::cv_status::timeout;
        }

        while (expectedEvents != mReceivedEvents.size()) {
            if (mEventCond.wait_for(g, kTimeout) == std::cv_status::timeout) {
                return false;
            }
        }
        return true;
    }

    void reset() {
        mReceivedEvents.clear();
    }

    const std::vector<HidlVecOfValues>& getReceivedEvents() {
        return mReceivedEvents;
    }

private:
    std::mutex mLock;
    std::condition_variable mEventCond;
    std::vector<HidlVecOfValues> mReceivedEvents;
};

template<typename T>
inline std::string hexString(T value) {
    std::stringstream ss;
    ss << std::showbase << std::hex << value;
    return ss.str();
}

template <typename T, typename Collection>
inline void assertAllExistsAnyOrder(
        std::initializer_list<T> expected,
        const Collection& actual,
        const char* msg) {
    std::set<T> expectedSet = expected;

    for (auto a: actual) {
        ASSERT_EQ(1u, expectedSet.erase(a))
                << msg << "\nContains not unexpected value.\n";
    }

    ASSERT_EQ(0u, expectedSet.size())
            << msg
            << "\nDoesn't contain expected value.";
}

#define ASSERT_ALL_EXISTS(...) \
    assertAllExistsAnyOrder(__VA_ARGS__, (std::string("Called from: ") + \
            std::string(__FILE__) + std::string(":") + \
            std::to_string(__LINE__)).c_str()); \

template<typename T>
inline std::string enumToHexString(T value) {
    return hexString(toInt(value));
}

template <typename T>
inline std::string vecToString(const hidl_vec<T>& vec) {
    std::stringstream ss("[");
    for (size_t i = 0; i < vec.size(); i++) {
        if (i != 0) ss << ",";
        ss << vec[i];
    }
    ss << "]";
    return ss.str();
}

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android


#endif //android_hardware_automotive_vehicle_V2_0_VehicleDebugUtils_H_
