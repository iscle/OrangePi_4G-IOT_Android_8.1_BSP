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
#define LOG_TAG "DefaultVehicleHal_v2_0"

#include <android/log.h>
#include <android-base/macros.h>

#include "EmulatedVehicleHal.h"
#include "Obd2SensorStore.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

static std::unique_ptr<Obd2SensorStore> fillDefaultObd2Frame(size_t numVendorIntegerSensors,
                                                             size_t numVendorFloatSensors) {
    std::unique_ptr<Obd2SensorStore> sensorStore(
        new Obd2SensorStore(numVendorIntegerSensors, numVendorFloatSensors));

    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::FUEL_SYSTEM_STATUS,
                                  toInt(Obd2FuelSystemStatus::CLOSED_LOOP));
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::MALFUNCTION_INDICATOR_LIGHT_ON, 0);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::IGNITION_MONITORS_SUPPORTED,
                                  toInt(Obd2IgnitionMonitorKind::SPARK));
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::IGNITION_SPECIFIC_MONITORS,
                                  Obd2CommonIgnitionMonitors::COMPONENTS_AVAILABLE |
                                      Obd2CommonIgnitionMonitors::MISFIRE_AVAILABLE |
                                      Obd2SparkIgnitionMonitors::AC_REFRIGERANT_AVAILABLE |
                                      Obd2SparkIgnitionMonitors::EVAPORATIVE_SYSTEM_AVAILABLE);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::INTAKE_AIR_TEMPERATURE, 35);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::COMMANDED_SECONDARY_AIR_STATUS,
                                  toInt(Obd2SecondaryAirStatus::FROM_OUTSIDE_OR_OFF));
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::NUM_OXYGEN_SENSORS_PRESENT, 1);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::RUNTIME_SINCE_ENGINE_START, 500);
    sensorStore->setIntegerSensor(
        DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON, 0);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::WARMUPS_SINCE_CODES_CLEARED, 51);
    sensorStore->setIntegerSensor(
        DiagnosticIntegerSensorIndex::DISTANCE_TRAVELED_SINCE_CODES_CLEARED, 365);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::ABSOLUTE_BAROMETRIC_PRESSURE, 30);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::CONTROL_MODULE_VOLTAGE, 12);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::AMBIENT_AIR_TEMPERATURE, 18);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::MAX_FUEL_AIR_EQUIVALENCE_RATIO, 1);
    sensorStore->setIntegerSensor(DiagnosticIntegerSensorIndex::FUEL_TYPE,
                                  toInt(Obd2FuelType::GASOLINE));
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::CALCULATED_ENGINE_LOAD, 0.153);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK1, -0.16);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK1, -0.16);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::SHORT_TERM_FUEL_TRIM_BANK2, -0.16);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::LONG_TERM_FUEL_TRIM_BANK2, -0.16);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::INTAKE_MANIFOLD_ABSOLUTE_PRESSURE, 7.5);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::ENGINE_RPM, 1250.);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::VEHICLE_SPEED, 40.);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::TIMING_ADVANCE, 2.5);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::THROTTLE_POSITION, 19.75);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::OXYGEN_SENSOR1_VOLTAGE, 0.265);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::FUEL_TANK_LEVEL_INPUT, 0.824);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::EVAPORATION_SYSTEM_VAPOR_PRESSURE,
                                -0.373);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::CATALYST_TEMPERATURE_BANK1_SENSOR1,
                                190.);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::RELATIVE_THROTTLE_POSITION, 3.);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::ABSOLUTE_THROTTLE_POSITION_B, 0.306);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_D, 0.188);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::ACCELERATOR_PEDAL_POSITION_E, 0.094);
    sensorStore->setFloatSensor(DiagnosticFloatSensorIndex::COMMANDED_THROTTLE_ACTUATOR, 0.024);

    return sensorStore;
}

enum class FakeDataCommand : int32_t {
    Stop = 0,
    Start = 1,
};

EmulatedVehicleHal::EmulatedVehicleHal(VehiclePropertyStore* propStore)
    : mPropStore(propStore),
      mHvacPowerProps(std::begin(kHvacPowerProperties), std::end(kHvacPowerProperties)),
      mRecurrentTimer(std::bind(&EmulatedVehicleHal::onContinuousPropertyTimer,
                                  this, std::placeholders::_1)),
      mFakeValueGenerator(std::bind(&EmulatedVehicleHal::onFakeValueGenerated,
                                    this, std::placeholders::_1, std::placeholders::_2)) {
    initStaticConfig();
    for (size_t i = 0; i < arraysize(kVehicleProperties); i++) {
        mPropStore->registerProperty(kVehicleProperties[i].config);
    }
}

VehicleHal::VehiclePropValuePtr EmulatedVehicleHal::get(
        const VehiclePropValue& requestedPropValue, StatusCode* outStatus) {
    auto propId = requestedPropValue.prop;
    auto& pool = *getValuePool();
    VehiclePropValuePtr v = nullptr;

    switch (propId) {
        case OBD2_FREEZE_FRAME:
            v = pool.obtainComplex();
            *outStatus = fillObd2FreezeFrame(requestedPropValue, v.get());
            break;
        case OBD2_FREEZE_FRAME_INFO:
            v = pool.obtainComplex();
            *outStatus = fillObd2DtcInfo(v.get());
            break;
        default:
            auto internalPropValue = mPropStore->readValueOrNull(requestedPropValue);
            if (internalPropValue != nullptr) {
                v = getValuePool()->obtain(*internalPropValue);
            }

            *outStatus = v != nullptr ? StatusCode::OK : StatusCode::INVALID_ARG;
            break;
    }

    return v;
}

StatusCode EmulatedVehicleHal::set(const VehiclePropValue& propValue) {
    if (propValue.prop == kGenerateFakeDataControllingProperty) {
        StatusCode status = handleGenerateFakeDataRequest(propValue);
        if (status != StatusCode::OK) {
            return status;
        }
    } else if (mHvacPowerProps.count(propValue.prop)) {
        auto hvacPowerOn = mPropStore->readValueOrNull(toInt(VehicleProperty::HVAC_POWER_ON),
                                                      toInt(VehicleAreaZone::ROW_1));

        if (hvacPowerOn && hvacPowerOn->value.int32Values.size() == 1
                && hvacPowerOn->value.int32Values[0] == 0) {
            return StatusCode::NOT_AVAILABLE;
        }
    } else if (propValue.prop == OBD2_FREEZE_FRAME_CLEAR) {
        return clearObd2FreezeFrames(propValue);
    } else if (propValue.prop == VEHICLE_MAP_SERVICE) {
        // Placeholder for future implementation of VMS property in the default hal. For now, just
        // returns OK; otherwise, hal clients crash with property not supported.
        return StatusCode::OK;
    }

    if (!mPropStore->writeValue(propValue)) {
        return StatusCode::INVALID_ARG;
    }

    getEmulatorOrDie()->doSetValueFromClient(propValue);

    return StatusCode::OK;
}

static bool isDiagnosticProperty(VehiclePropConfig propConfig) {
    switch (propConfig.prop) {
        case OBD2_LIVE_FRAME:
        case OBD2_FREEZE_FRAME:
        case OBD2_FREEZE_FRAME_CLEAR:
        case OBD2_FREEZE_FRAME_INFO:
            return true;
    }
    return false;
}

// Parse supported properties list and generate vector of property values to hold current values.
void EmulatedVehicleHal::onCreate() {
    for (auto& it : kVehicleProperties) {
        VehiclePropConfig cfg = it.config;
        int32_t supportedAreas = cfg.supportedAreas;

        if (isDiagnosticProperty(cfg)) {
            // do not write an initial empty value for the diagnostic properties
            // as we will initialize those separately.
            continue;
        }

        //  A global property will have supportedAreas = 0
        if (isGlobalProp(cfg.prop)) {
            supportedAreas = 0;
        }

        // This loop is a do-while so it executes at least once to handle global properties
        do {
            int32_t curArea = supportedAreas;
            supportedAreas &= supportedAreas - 1;  // Clear the right-most bit of supportedAreas.
            curArea ^= supportedAreas;  // Set curArea to the previously cleared bit.

            // Create a separate instance for each individual zone
            VehiclePropValue prop = {
                .prop = cfg.prop,
                .areaId = curArea,
            };
            if (it.initialAreaValues.size() > 0) {
                auto valueForAreaIt = it.initialAreaValues.find(curArea);
                if (valueForAreaIt != it.initialAreaValues.end()) {
                    prop.value = valueForAreaIt->second;
                } else {
                    ALOGW("%s failed to get default value for prop 0x%x area 0x%x",
                            __func__, cfg.prop, curArea);
                }
            } else {
                prop.value = it.initialValue;
            }
            mPropStore->writeValue(prop);

        } while (supportedAreas != 0);
    }
    initObd2LiveFrame(*mPropStore->getConfigOrDie(OBD2_LIVE_FRAME));
    initObd2FreezeFrame(*mPropStore->getConfigOrDie(OBD2_FREEZE_FRAME));
}

std::vector<VehiclePropConfig> EmulatedVehicleHal::listProperties()  {
    return mPropStore->getAllConfigs();
}

void EmulatedVehicleHal::onContinuousPropertyTimer(const std::vector<int32_t>& properties) {
    VehiclePropValuePtr v;

    auto& pool = *getValuePool();

    for (int32_t property : properties) {
        if (isContinuousProperty(property)) {
            auto internalPropValue = mPropStore->readValueOrNull(property);
            if (internalPropValue != nullptr) {
                v = pool.obtain(*internalPropValue);
            }
        } else {
            ALOGE("Unexpected onContinuousPropertyTimer for property: 0x%x", property);
        }

        if (v.get()) {
            v->timestamp = elapsedRealtimeNano();
            doHalEvent(std::move(v));
        }
    }
}

StatusCode EmulatedVehicleHal::subscribe(int32_t property, int32_t,
                                        float sampleRate) {
    ALOGI("%s propId: 0x%x, sampleRate: %f", __func__, property, sampleRate);

    if (isContinuousProperty(property)) {
        mRecurrentTimer.registerRecurrentEvent(hertzToNanoseconds(sampleRate), property);
    }
    return StatusCode::OK;
}

StatusCode EmulatedVehicleHal::unsubscribe(int32_t property) {
    ALOGI("%s propId: 0x%x", __func__, property);
    if (isContinuousProperty(property)) {
        mRecurrentTimer.unregisterRecurrentEvent(property);
    }
    return StatusCode::OK;
}

bool EmulatedVehicleHal::isContinuousProperty(int32_t propId) const {
    const VehiclePropConfig* config = mPropStore->getConfigOrNull(propId);
    if (config == nullptr) {
        ALOGW("Config not found for property: 0x%x", propId);
        return false;
    }
    return config->changeMode == VehiclePropertyChangeMode::CONTINUOUS;
}

bool EmulatedVehicleHal::setPropertyFromVehicle(const VehiclePropValue& propValue) {
    if (propValue.prop == kGenerateFakeDataControllingProperty) {
        StatusCode status = handleGenerateFakeDataRequest(propValue);
        if (status != StatusCode::OK) {
            return false;
        }
    }

    if (mPropStore->writeValue(propValue)) {
        doHalEvent(getValuePool()->obtain(propValue));
        return true;
    } else {
        return false;
    }
}

std::vector<VehiclePropValue> EmulatedVehicleHal::getAllProperties() const  {
    return mPropStore->readAllValues();
}

StatusCode EmulatedVehicleHal::handleGenerateFakeDataRequest(const VehiclePropValue& request) {
    ALOGI("%s", __func__);
    const auto& v = request.value;
    if (v.int32Values.size() < 2) {
        ALOGE("%s: expected at least 2 elements in int32Values, got: %zu", __func__,
                v.int32Values.size());
        return StatusCode::INVALID_ARG;
    }

    FakeDataCommand command = static_cast<FakeDataCommand>(v.int32Values[0]);
    int32_t propId = v.int32Values[1];

    switch (command) {
        case FakeDataCommand::Start: {
            if (!v.int64Values.size()) {
                ALOGE("%s: interval is not provided in int64Values", __func__);
                return StatusCode::INVALID_ARG;
            }
            auto interval = std::chrono::nanoseconds(v.int64Values[0]);

            if (v.floatValues.size() < 3) {
                ALOGE("%s: expected at least 3 element sin floatValues, got: %zu", __func__,
                        v.floatValues.size());
                return StatusCode::INVALID_ARG;
            }
            float initialValue = v.floatValues[0];
            float dispersion = v.floatValues[1];
            float increment = v.floatValues[2];

            ALOGI("%s, propId: %d, initalValue: %f", __func__, propId, initialValue);
            mFakeValueGenerator.startGeneratingHalEvents(
                interval, propId, initialValue, dispersion, increment);

            break;
        }
        case FakeDataCommand::Stop: {
            ALOGI("%s, FakeDataCommandStop", __func__);
            mFakeValueGenerator.stopGeneratingHalEvents(propId);
            break;
        }
        default: {
            ALOGE("%s: unexpected command: %d", __func__, command);
            return StatusCode::INVALID_ARG;
        }
    }
    return StatusCode::OK;
}

void EmulatedVehicleHal::onFakeValueGenerated(int32_t propId, float value) {
    VehiclePropValuePtr updatedPropValue {};
    switch (getPropType(propId)) {
        case VehiclePropertyType::FLOAT:
            updatedPropValue = getValuePool()->obtainFloat(value);
            break;
        case VehiclePropertyType::INT32:
            updatedPropValue = getValuePool()->obtainInt32(static_cast<int32_t>(value));
            break;
        default:
            ALOGE("%s: data type for property: 0x%x not supported", __func__, propId);
            return;

    }

    if (updatedPropValue) {
        updatedPropValue->prop = propId;
        updatedPropValue->areaId = 0;  // Add area support if necessary.
        updatedPropValue->timestamp = elapsedRealtimeNano();
        mPropStore->writeValue(*updatedPropValue);
        auto changeMode = mPropStore->getConfigOrDie(propId)->changeMode;
        if (VehiclePropertyChangeMode::ON_CHANGE == changeMode) {
            doHalEvent(move(updatedPropValue));
        }
    }
}

void EmulatedVehicleHal::initStaticConfig() {
    for (auto&& it = std::begin(kVehicleProperties); it != std::end(kVehicleProperties); ++it) {
        const auto& cfg = it->config;
        VehiclePropertyStore::TokenFunction tokenFunction = nullptr;

        switch (cfg.prop) {
            case OBD2_FREEZE_FRAME: {
                tokenFunction = [](const VehiclePropValue& propValue) {
                    return propValue.timestamp;
                };
                break;
            }
            default:
                break;
        }

        mPropStore->registerProperty(cfg, tokenFunction);
    }
}

void EmulatedVehicleHal::initObd2LiveFrame(const VehiclePropConfig& propConfig) {
    auto liveObd2Frame = createVehiclePropValue(VehiclePropertyType::COMPLEX, 0);
    auto sensorStore = fillDefaultObd2Frame(static_cast<size_t>(propConfig.configArray[0]),
                                            static_cast<size_t>(propConfig.configArray[1]));
    sensorStore->fillPropValue("", liveObd2Frame.get());
    liveObd2Frame->prop = OBD2_LIVE_FRAME;

    mPropStore->writeValue(*liveObd2Frame);
}

void EmulatedVehicleHal::initObd2FreezeFrame(const VehiclePropConfig& propConfig) {
    auto sensorStore = fillDefaultObd2Frame(static_cast<size_t>(propConfig.configArray[0]),
                                            static_cast<size_t>(propConfig.configArray[1]));

    static std::vector<std::string> sampleDtcs = {"P0070",
                                                  "P0102"
                                                  "P0123"};
    for (auto&& dtc : sampleDtcs) {
        auto freezeFrame = createVehiclePropValue(VehiclePropertyType::COMPLEX, 0);
        sensorStore->fillPropValue(dtc, freezeFrame.get());
        freezeFrame->prop = OBD2_FREEZE_FRAME;

        mPropStore->writeValue(*freezeFrame);
    }
}

StatusCode EmulatedVehicleHal::fillObd2FreezeFrame(const VehiclePropValue& requestedPropValue,
                                                   VehiclePropValue* outValue) {
    if (requestedPropValue.value.int64Values.size() != 1) {
        ALOGE("asked for OBD2_FREEZE_FRAME without valid timestamp");
        return StatusCode::INVALID_ARG;
    }
    auto timestamp = requestedPropValue.value.int64Values[0];
    auto freezeFrame = mPropStore->readValueOrNull(OBD2_FREEZE_FRAME, 0, timestamp);
    if (freezeFrame == nullptr) {
        ALOGE("asked for OBD2_FREEZE_FRAME at invalid timestamp");
        return StatusCode::INVALID_ARG;
    }
    outValue->prop = OBD2_FREEZE_FRAME;
    outValue->value.int32Values = freezeFrame->value.int32Values;
    outValue->value.floatValues = freezeFrame->value.floatValues;
    outValue->value.bytes = freezeFrame->value.bytes;
    outValue->value.stringValue = freezeFrame->value.stringValue;
    outValue->timestamp = freezeFrame->timestamp;
    return StatusCode::OK;
}

StatusCode EmulatedVehicleHal::clearObd2FreezeFrames(const VehiclePropValue& propValue) {
    if (propValue.value.int64Values.size() == 0) {
        mPropStore->removeValuesForProperty(OBD2_FREEZE_FRAME);
        return StatusCode::OK;
    } else {
        for (int64_t timestamp : propValue.value.int64Values) {
            auto freezeFrame = mPropStore->readValueOrNull(OBD2_FREEZE_FRAME, 0, timestamp);
            if (freezeFrame == nullptr) {
                ALOGE("asked for OBD2_FREEZE_FRAME at invalid timestamp");
                return StatusCode::INVALID_ARG;
            }
            mPropStore->removeValue(*freezeFrame);
        }
    }
    return StatusCode::OK;
}

StatusCode EmulatedVehicleHal::fillObd2DtcInfo(VehiclePropValue* outValue) {
    std::vector<int64_t> timestamps;
    for (const auto& freezeFrame : mPropStore->readValuesForProperty(OBD2_FREEZE_FRAME)) {
        timestamps.push_back(freezeFrame.timestamp);
    }
    outValue->value.int64Values = timestamps;
    outValue->prop = OBD2_FREEZE_FRAME_INFO;
    return StatusCode::OK;
}

}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
