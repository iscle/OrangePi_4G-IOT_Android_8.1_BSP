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

#ifndef android_hardware_automotive_vehicle_V2_0_impl_DefaultConfig_H_
#define android_hardware_automotive_vehicle_V2_0_impl_DefaultConfig_H_

#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>
#include <vhal_v2_0/VehicleUtils.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {
//
// Some handy constants to avoid conversions from enum to int.
constexpr int ABS_ACTIVE = (int)VehicleProperty::ABS_ACTIVE;
constexpr int OBD2_LIVE_FRAME = (int)VehicleProperty::OBD2_LIVE_FRAME;
constexpr int OBD2_FREEZE_FRAME = (int)VehicleProperty::OBD2_FREEZE_FRAME;
constexpr int OBD2_FREEZE_FRAME_INFO = (int)VehicleProperty::OBD2_FREEZE_FRAME_INFO;
constexpr int OBD2_FREEZE_FRAME_CLEAR = (int)VehicleProperty::OBD2_FREEZE_FRAME_CLEAR;
constexpr int TRACTION_CONTROL_ACTIVE = (int)VehicleProperty::TRACTION_CONTROL_ACTIVE;
constexpr int VEHICLE_MAP_SERVICE = (int)VehicleProperty::VEHICLE_MAP_SERVICE;
constexpr int WHEEL_TICK = (int)VehicleProperty::WHEEL_TICK;
constexpr int ALL_WHEELS =
    (int)(Wheel::LEFT_FRONT | Wheel::RIGHT_FRONT | Wheel::LEFT_REAR | Wheel::RIGHT_REAR);

/*
 * This property is used for test purpose to generate fake events.
 *
 * It has the following format:
 *
 * int32Values[0] - command (1 - start fake data generation, 0 - stop)
 * int32Values[1] - VehicleProperty to which command applies
 *
 * For start command, additional data should be provided:
 *   int64Values[0] - periodic interval in nanoseconds
 *   floatValues[0] - initial value
 *   floatValues[1] - dispersion defines min and max range relative to initial value
 *   floatValues[2] - increment, with every timer tick the value will be incremented by this amount
 */
const int32_t kGenerateFakeDataControllingProperty = 0x0666
        | VehiclePropertyGroup::VENDOR
        | VehicleArea::GLOBAL
        | VehiclePropertyType::COMPLEX;

const int32_t kHvacPowerProperties[] = {
    toInt(VehicleProperty::HVAC_FAN_SPEED),
    toInt(VehicleProperty::HVAC_FAN_DIRECTION),
};

struct ConfigDeclaration {
    VehiclePropConfig config;

    /* This value will be used as an initial value for the property. If this field is specified for
     * property that supports multiple areas then it will be used for all areas unless particular
     * area is overridden in initialAreaValue field. */
    VehiclePropValue::RawValue initialValue;
    /* Use initialAreaValues if it is necessary to specify different values per each area. */
    std::map<int32_t, VehiclePropValue::RawValue> initialAreaValues;
};

const ConfigDeclaration kVehicleProperties[]{
    {.config =
         {
             .prop = toInt(VehicleProperty::INFO_MAKE),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::STATIC,
         },
     .initialValue = {.stringValue = "Toy Vehicle"}},
    {.config =
         {
             .prop = toInt(VehicleProperty::PERF_VEHICLE_SPEED),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
             .minSampleRate = 1.0f,
             .maxSampleRate = 1000.0f,
         },
     .initialValue = {.floatValues = {0.0f}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::PERF_ODOMETER),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.floatValues = {0.0f}}},

    {
        .config =
            {
                .prop = toInt(VehicleProperty::ENGINE_RPM),
                .access = VehiclePropertyAccess::READ,
                .changeMode = VehiclePropertyChangeMode::CONTINUOUS,
                .minSampleRate = 1.0f,
                .maxSampleRate = 1000.0f,
            },
        .initialValue = {.floatValues = {0.0f}},
    },

    {.config =
         {
             .prop = toInt(VehicleProperty::CURRENT_GEAR),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {toInt(VehicleGear::GEAR_PARK)}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::PARKING_BRAKE_ON),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {1}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::FUEL_LEVEL_LOW),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {0}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::HVAC_POWER_ON),
             .access = VehiclePropertyAccess::READ_WRITE,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
             .supportedAreas = toInt(VehicleAreaZone::ROW_1),
             // TODO(bryaneyler): Ideally, this is generated dynamically from
             // kHvacPowerProperties.
             .configString = "0x12400500,0x12400501"  // HVAC_FAN_SPEED,HVAC_FAN_DIRECTION
         },
     .initialValue = {.int32Values = {1}}},

    {
        .config = {.prop = toInt(VehicleProperty::HVAC_DEFROSTER),
                   .access = VehiclePropertyAccess::READ_WRITE,
                   .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                   .supportedAreas =
                       VehicleAreaWindow::FRONT_WINDSHIELD | VehicleAreaWindow::REAR_WINDSHIELD},
        .initialValue = {.int32Values = {0}}  // Will be used for all areas.
    },

    {.config = {.prop = toInt(VehicleProperty::HVAC_RECIRC_ON),
                .access = VehiclePropertyAccess::READ_WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                .supportedAreas = toInt(VehicleAreaZone::ROW_1)},
     .initialValue = {.int32Values = {1}}},

    {.config = {.prop = toInt(VehicleProperty::HVAC_AC_ON),
                .access = VehiclePropertyAccess::READ_WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                .supportedAreas = toInt(VehicleAreaZone::ROW_1)},
     .initialValue = {.int32Values = {1}}},

    {.config = {.prop = toInt(VehicleProperty::HVAC_AUTO_ON),
                .access = VehiclePropertyAccess::READ_WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                .supportedAreas = toInt(VehicleAreaZone::ROW_1)},
     .initialValue = {.int32Values = {1}}},

    {.config = {.prop = toInt(VehicleProperty::HVAC_FAN_SPEED),
                .access = VehiclePropertyAccess::READ_WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                .supportedAreas = toInt(VehicleAreaZone::ROW_1),
                .areaConfigs = {VehicleAreaConfig{.areaId = toInt(VehicleAreaZone::ROW_1),
                                                  .minInt32Value = 1,
                                                  .maxInt32Value = 7}}},
     .initialValue = {.int32Values = {3}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::HVAC_FAN_DIRECTION),
             .access = VehiclePropertyAccess::READ_WRITE,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
             .supportedAreas = toInt(VehicleAreaZone::ROW_1),
         },
     .initialValue = {.int32Values = {toInt(VehicleHvacFanDirection::FACE)}}},

    {.config = {.prop = toInt(VehicleProperty::HVAC_TEMPERATURE_SET),
                .access = VehiclePropertyAccess::READ_WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                .supportedAreas = VehicleAreaZone::ROW_1_LEFT | VehicleAreaZone::ROW_1_RIGHT,
                .areaConfigs = {VehicleAreaConfig{
                                    .areaId = toInt(VehicleAreaZone::ROW_1_LEFT),
                                    .minFloatValue = 16,
                                    .maxFloatValue = 32,
                                },
                                VehicleAreaConfig{
                                    .areaId = toInt(VehicleAreaZone::ROW_1_RIGHT),
                                    .minFloatValue = 16,
                                    .maxFloatValue = 32,
                                }}},
     .initialAreaValues = {{toInt(VehicleAreaZone::ROW_1_LEFT), {.floatValues = {16}}},
                           {toInt(VehicleAreaZone::ROW_1_RIGHT), {.floatValues = {20}}}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::ENV_OUTSIDE_TEMPERATURE),
             .access = VehiclePropertyAccess::READ,
             // TODO(bryaneyler): Support ON_CHANGE as well.
             .changeMode = VehiclePropertyChangeMode::CONTINUOUS,
             .minSampleRate = 1.0f,
             .maxSampleRate = 2.0f,
         },
     .initialValue = {.floatValues = {25.0f}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::NIGHT_MODE),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {0}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::DRIVING_STATUS),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {toInt(VehicleDrivingStatus::UNRESTRICTED)}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::GEAR_SELECTION),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {toInt(VehicleGear::GEAR_PARK)}}},

    {
        .config =
            {
                .prop = toInt(VehicleProperty::INFO_FUEL_CAPACITY),
                .access = VehiclePropertyAccess::READ,
                .changeMode = VehiclePropertyChangeMode::STATIC,
            },
        .initialValue = {.floatValues = {123000.0f}}  // In Milliliters
    },

    {.config = {.prop = toInt(VehicleProperty::DISPLAY_BRIGHTNESS),
                .access = VehiclePropertyAccess::READ_WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                .areaConfigs = {VehicleAreaConfig{.minInt32Value = 0, .maxInt32Value = 10}}},
     .initialValue = {.int32Values = {7}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::IGNITION_STATE),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {toInt(VehicleIgnitionState::ON)}}},

    {.config =
         {
             .prop = toInt(VehicleProperty::ENGINE_OIL_TEMP),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::CONTINUOUS,
             .minSampleRate = 0.1,  // 0.1 Hz, every 10 seconds
             .maxSampleRate = 10,   // 10 Hz, every 100 ms
         },
     .initialValue = {.floatValues = {101.0f}}},

    {
        .config =
            {
                .prop = kGenerateFakeDataControllingProperty,
                .access = VehiclePropertyAccess::WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
            },
    },

    {.config =
         {
             .prop = toInt(VehicleProperty::DOOR_LOCK),
             .access = VehiclePropertyAccess::READ,
             .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
         },
     .initialValue = {.int32Values = {1}}},

    {
        .config =
            {
                .prop = WHEEL_TICK,
                .access = VehiclePropertyAccess::READ,
                .changeMode = VehiclePropertyChangeMode::CONTINUOUS,
                .configArray = {ALL_WHEELS, 50000, 50000, 50000, 50000},
                .minSampleRate = 1.0f,
                .maxSampleRate = 100.0f,
            },
    },

    {
        .config =
            {
                .prop = ABS_ACTIVE,
                .access = VehiclePropertyAccess::READ,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
            },
    },

    {
        .config =
            {
                .prop = TRACTION_CONTROL_ACTIVE,
                .access = VehiclePropertyAccess::READ,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
            },
    },

    {
        .config = {.prop = OBD2_LIVE_FRAME,
                   .access = VehiclePropertyAccess::READ,
                   .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                   .configArray = {0, 0}},
    },

    {
        .config = {.prop = OBD2_FREEZE_FRAME,
                   .access = VehiclePropertyAccess::READ,
                   .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                   .configArray = {0, 0}},
    },

    {
        .config = {.prop = OBD2_FREEZE_FRAME_INFO,
                   .access = VehiclePropertyAccess::READ,
                   .changeMode = VehiclePropertyChangeMode::ON_CHANGE},
    },

    {
        .config = {.prop = OBD2_FREEZE_FRAME_CLEAR,
                   .access = VehiclePropertyAccess::WRITE,
                   .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
                   .configArray = {1}},
    },

    {.config = {.prop = VEHICLE_MAP_SERVICE,
                .access = VehiclePropertyAccess::READ_WRITE,
                .changeMode = VehiclePropertyChangeMode::ON_CHANGE}},
};

}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif // android_hardware_automotive_vehicle_V2_0_impl_DefaultConfig_H_
