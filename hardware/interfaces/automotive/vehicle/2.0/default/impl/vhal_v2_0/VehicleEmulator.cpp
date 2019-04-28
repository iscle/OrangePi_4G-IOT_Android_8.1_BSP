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
#define LOG_TAG "VehicleEmulator_v2_0"
#include <android/log.h>

#include <algorithm>
#include <android-base/properties.h>
#include <utils/SystemClock.h>

#include <vhal_v2_0/VehicleUtils.h>

#include "PipeComm.h"
#include "SocketComm.h"

#include "VehicleEmulator.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

std::unique_ptr<CommBase> CommFactory::create() {
    bool isEmulator = android::base::GetBoolProperty("ro.kernel.qemu", false);

    if (isEmulator) {
        return std::make_unique<PipeComm>();
    } else {
        return std::make_unique<SocketComm>();
    }
}

VehicleEmulator::~VehicleEmulator() {
    mExit = true;   // Notify thread to finish and wait for it to terminate.
    mComm->stop();  // Close emulator socket if it is open.
    if (mThread.joinable()) mThread.join();
}

void VehicleEmulator::doSetValueFromClient(const VehiclePropValue& propValue) {
    emulator::EmulatorMessage msg;
    emulator::VehiclePropValue *val = msg.add_value();
    populateProtoVehiclePropValue(val, &propValue);
    msg.set_status(emulator::RESULT_OK);
    msg.set_msg_type(emulator::SET_PROPERTY_ASYNC);
    txMsg(msg);
}

void VehicleEmulator::doGetConfig(VehicleEmulator::EmulatorMessage& rxMsg,
                                  VehicleEmulator::EmulatorMessage& respMsg) {
    std::vector<VehiclePropConfig> configs = mHal->listProperties();
    emulator::VehiclePropGet getProp = rxMsg.prop(0);

    respMsg.set_msg_type(emulator::GET_CONFIG_RESP);
    respMsg.set_status(emulator::ERROR_INVALID_PROPERTY);

    for (auto& config : configs) {
        // Find the config we are looking for
        if (config.prop == getProp.prop()) {
            emulator::VehiclePropConfig* protoCfg = respMsg.add_config();
            populateProtoVehicleConfig(protoCfg, config);
            respMsg.set_status(emulator::RESULT_OK);
            break;
        }
    }
}

void VehicleEmulator::doGetConfigAll(VehicleEmulator::EmulatorMessage& /* rxMsg */,
                                     VehicleEmulator::EmulatorMessage& respMsg) {
    std::vector<VehiclePropConfig> configs = mHal->listProperties();

    respMsg.set_msg_type(emulator::GET_CONFIG_ALL_RESP);
    respMsg.set_status(emulator::RESULT_OK);

    for (auto& config : configs) {
        emulator::VehiclePropConfig* protoCfg = respMsg.add_config();
        populateProtoVehicleConfig(protoCfg, config);
    }
}

void VehicleEmulator::doGetProperty(VehicleEmulator::EmulatorMessage& rxMsg,
                                    VehicleEmulator::EmulatorMessage& respMsg)  {
    int32_t areaId = 0;
    emulator::VehiclePropGet getProp = rxMsg.prop(0);
    int32_t propId = getProp.prop();
    emulator::Status status = emulator::ERROR_INVALID_PROPERTY;

    respMsg.set_msg_type(emulator::GET_PROPERTY_RESP);

    if (getProp.has_area_id()) {
        areaId = getProp.area_id();
    }

    {
        VehiclePropValue request = { .prop = propId, .areaId = areaId };
        StatusCode halStatus;
        auto val = mHal->get(request, &halStatus);
        if (val != nullptr) {
            emulator::VehiclePropValue* protoVal = respMsg.add_value();
            populateProtoVehiclePropValue(protoVal, val.get());
            status = emulator::RESULT_OK;
        }
    }

    respMsg.set_status(status);
}

void VehicleEmulator::doGetPropertyAll(VehicleEmulator::EmulatorMessage& /* rxMsg */,
                                       VehicleEmulator::EmulatorMessage& respMsg)  {
    respMsg.set_msg_type(emulator::GET_PROPERTY_ALL_RESP);
    respMsg.set_status(emulator::RESULT_OK);

    {
        for (const auto& prop : mHal->getAllProperties()) {
            emulator::VehiclePropValue* protoVal = respMsg.add_value();
            populateProtoVehiclePropValue(protoVal, &prop);
        }
    }
}

void VehicleEmulator::doSetProperty(VehicleEmulator::EmulatorMessage& rxMsg,
                                    VehicleEmulator::EmulatorMessage& respMsg) {
    emulator::VehiclePropValue protoVal = rxMsg.value(0);
    VehiclePropValue val = {
        .prop = protoVal.prop(),
        .areaId = protoVal.area_id(),
        .timestamp = elapsedRealtimeNano(),
    };

    respMsg.set_msg_type(emulator::SET_PROPERTY_RESP);

    // Copy value data if it is set.  This automatically handles complex data types if needed.
    if (protoVal.has_string_value()) {
        val.value.stringValue = protoVal.string_value().c_str();
    }

    if (protoVal.has_bytes_value()) {
        val.value.bytes = std::vector<uint8_t> { protoVal.bytes_value().begin(),
                                                 protoVal.bytes_value().end() };
    }

    if (protoVal.int32_values_size() > 0) {
        val.value.int32Values = std::vector<int32_t> { protoVal.int32_values().begin(),
                                                       protoVal.int32_values().end() };
    }

    if (protoVal.int64_values_size() > 0) {
        val.value.int64Values = std::vector<int64_t> { protoVal.int64_values().begin(),
                                                       protoVal.int64_values().end() };
    }

    if (protoVal.float_values_size() > 0) {
        val.value.floatValues = std::vector<float> { protoVal.float_values().begin(),
                                                     protoVal.float_values().end() };
    }

    bool halRes = mHal->setPropertyFromVehicle(val);
    respMsg.set_status(halRes ? emulator::RESULT_OK : emulator::ERROR_INVALID_PROPERTY);
}

void VehicleEmulator::txMsg(emulator::EmulatorMessage& txMsg) {
    int numBytes = txMsg.ByteSize();
    std::vector<uint8_t> msg(static_cast<size_t>(numBytes));

    if (!txMsg.SerializeToArray(msg.data(), static_cast<int32_t>(msg.size()))) {
        ALOGE("%s: SerializeToString failed!", __func__);
        return;
    }

    if (mExit) {
        ALOGW("%s: unable to transmit a message, connection closed", __func__);
        return;
    }

    // Send the message
    int retVal = mComm->write(msg);
    if (retVal < 0) {
        ALOGE("%s: Failed to tx message: retval=%d, errno=%d", __func__, retVal, errno);
    }
}

void VehicleEmulator::parseRxProtoBuf(std::vector<uint8_t>& msg) {
    emulator::EmulatorMessage rxMsg;
    emulator::EmulatorMessage respMsg;

    if (rxMsg.ParseFromArray(msg.data(), static_cast<int32_t>(msg.size()))) {
        switch (rxMsg.msg_type()) {
            case emulator::GET_CONFIG_CMD:
                doGetConfig(rxMsg, respMsg);
                break;
            case emulator::GET_CONFIG_ALL_CMD:
                doGetConfigAll(rxMsg, respMsg);
                break;
            case emulator::GET_PROPERTY_CMD:
                doGetProperty(rxMsg, respMsg);
                break;
            case emulator::GET_PROPERTY_ALL_CMD:
                doGetPropertyAll(rxMsg, respMsg);
                break;
            case emulator::SET_PROPERTY_CMD:
                doSetProperty(rxMsg, respMsg);
                break;
            default:
                ALOGW("%s: Unknown message received, type = %d", __func__, rxMsg.msg_type());
                respMsg.set_status(emulator::ERROR_UNIMPLEMENTED_CMD);
                break;
        }

        // Send the reply
        txMsg(respMsg);
    } else {
        ALOGE("%s: ParseFromString() failed. msgSize=%d", __func__, static_cast<int>(msg.size()));
    }
}

void VehicleEmulator::populateProtoVehicleConfig(emulator::VehiclePropConfig* protoCfg,
                                                 const VehiclePropConfig& cfg) {
    protoCfg->set_prop(cfg.prop);
    protoCfg->set_access(toInt(cfg.access));
    protoCfg->set_change_mode(toInt(cfg.changeMode));
    protoCfg->set_value_type(toInt(getPropType(cfg.prop)));

    if (!isGlobalProp(cfg.prop)) {
        protoCfg->set_supported_areas(cfg.supportedAreas);
    }

    for (auto& configElement : cfg.configArray) {
        protoCfg->add_config_array(configElement);
    }

    if (cfg.configString.size() > 0) {
        protoCfg->set_config_string(cfg.configString.c_str(), cfg.configString.size());
    }

    // Populate the min/max values based on property type
    switch (getPropType(cfg.prop)) {
        case VehiclePropertyType::STRING:
        case VehiclePropertyType::BOOLEAN:
        case VehiclePropertyType::INT32_VEC:
        case VehiclePropertyType::FLOAT_VEC:
        case VehiclePropertyType::BYTES:
        case VehiclePropertyType::COMPLEX:
            // Do nothing.  These types don't have min/max values
            break;
        case VehiclePropertyType::INT64:
            if (cfg.areaConfigs.size() > 0) {
                emulator::VehicleAreaConfig* aCfg = protoCfg->add_area_configs();
                aCfg->set_min_int64_value(cfg.areaConfigs[0].minInt64Value);
                aCfg->set_max_int64_value(cfg.areaConfigs[0].maxInt64Value);
            }
            break;
        case VehiclePropertyType::FLOAT:
            if (cfg.areaConfigs.size() > 0) {
                emulator::VehicleAreaConfig* aCfg = protoCfg->add_area_configs();
                aCfg->set_min_float_value(cfg.areaConfigs[0].minFloatValue);
                aCfg->set_max_float_value(cfg.areaConfigs[0].maxFloatValue);
            }
            break;
        case VehiclePropertyType::INT32:
            if (cfg.areaConfigs.size() > 0) {
                emulator::VehicleAreaConfig* aCfg = protoCfg->add_area_configs();
                aCfg->set_min_int32_value(cfg.areaConfigs[0].minInt32Value);
                aCfg->set_max_int32_value(cfg.areaConfigs[0].maxInt32Value);
            }
            break;
        default:
            ALOGW("%s: Unknown property type:  0x%x", __func__, toInt(getPropType(cfg.prop)));
            break;
    }

    protoCfg->set_min_sample_rate(cfg.minSampleRate);
    protoCfg->set_max_sample_rate(cfg.maxSampleRate);
}

void VehicleEmulator::populateProtoVehiclePropValue(emulator::VehiclePropValue* protoVal,
                                                    const VehiclePropValue* val) {
    protoVal->set_prop(val->prop);
    protoVal->set_value_type(toInt(getPropType(val->prop)));
    protoVal->set_timestamp(val->timestamp);
    protoVal->set_area_id(val->areaId);

    // Copy value data if it is set.
    //  - for bytes and strings, this is indicated by size > 0
    //  - for int32, int64, and float, copy the values if vectors have data
    if (val->value.stringValue.size() > 0) {
        protoVal->set_string_value(val->value.stringValue.c_str(), val->value.stringValue.size());
    }

    if (val->value.bytes.size() > 0) {
        protoVal->set_bytes_value(val->value.bytes.data(), val->value.bytes.size());
    }

    for (auto& int32Value : val->value.int32Values) {
        protoVal->add_int32_values(int32Value);
    }

    for (auto& int64Value : val->value.int64Values) {
        protoVal->add_int64_values(int64Value);
    }

    for (auto& floatValue : val->value.floatValues) {
        protoVal->add_float_values(floatValue);
    }
}

void VehicleEmulator::rxMsg() {
    while (!mExit) {
        std::vector<uint8_t> msg = mComm->read();

        if (msg.size() > 0) {
            // Received a message.
            parseRxProtoBuf(msg);
        } else {
            // This happens when connection is closed
            ALOGD("%s: msgSize=%zu", __func__, msg.size());
            break;
        }
    }
}

void VehicleEmulator::rxThread() {
    if (mExit) return;

    int retVal = mComm->open();
    if (retVal != 0) mExit = true;

    // Comms are properly opened
    while (!mExit) {
        retVal = mComm->connect();

        if (retVal >= 0) {
            rxMsg();
        }

        // Check every 100ms for a new connection
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
}

}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
