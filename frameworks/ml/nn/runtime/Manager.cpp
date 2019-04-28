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

#define LOG_TAG "Manager"

#include "Manager.h"
#include "HalInterfaces.h"
#include "Utils.h"

#include <android/hidl/manager/1.0/IServiceManager.h>
#include <hidl/HidlTransportSupport.h>
#include <hidl/ServiceManagement.h>

#include <algorithm>
#include <functional>

namespace android {
namespace nn {

// TODO: handle errors from initialize correctly
bool Device::initialize() {
#ifdef NN_DEBUGGABLE
    static const char samplePrefix[] = "sample";

    mSupported =
            (mName.substr(0, sizeof(samplePrefix) - 1)  == samplePrefix)
            ? getProp("debug.nn.sample.supported") : 0;
#endif  // NN_DEBUGGABLE
    bool success = false;
    auto ret = mInterface->getCapabilities([&](ErrorStatus status,
                                               const Capabilities& capabilities) {
        if (status != ErrorStatus::NONE) {
            LOG(ERROR) << "IDevice::getCapabilities returned the error " << toString(status);
        } else {
            VLOG(MANAGER) << "Capab " << capabilities.float32Performance.execTime;
            VLOG(MANAGER) << "Capab " << capabilities.quantized8Performance.execTime;
            mFloat32Performance = capabilities.float32Performance;
            mQuantized8Performance = capabilities.quantized8Performance;
            success = true;
        }
    });
    if (!ret.isOk()) {
        LOG(ERROR) << "IDevice::getCapabilities failed for " << getName()
                   << ": " << ret.description();
    }
    return success;
}

void Device::getSupportedOperations(const Model& hidlModel,
                                    hidl_vec<bool>* outSupportedOperations) const {
    auto ret = mInterface->getSupportedOperations(
        hidlModel,
        [outSupportedOperations](ErrorStatus status, const hidl_vec<bool>& supportedOperations) {
            if (status != ErrorStatus::NONE) {
                LOG(ERROR)
                        << "IDevice::getSupportedOperations returned the error "
                        << toString(status);
            }
            *outSupportedOperations = supportedOperations;
        });

    if (!ret.isOk()) {
        LOG(ERROR) << "IDevice::getSupportedOperations failed for " << getName()
                   << ": " << ret.description();
        outSupportedOperations->resize(hidlModel.operations.size());
        std::fill(outSupportedOperations->begin(), outSupportedOperations->end(), false);
        return;
    }

#ifdef NN_DEBUGGABLE
    if (mSupported != 1) {
        return;
    }

    const uint32_t baseAccumulator = std::hash<std::string>{}(mName);
    for (size_t operationIndex = 0; operationIndex < outSupportedOperations->size();
         operationIndex++) {
        if (!(*outSupportedOperations)[operationIndex]) {
            continue;
        }

        uint32_t accumulator = baseAccumulator;
        const Operation &operation = hidlModel.operations[operationIndex];
        accumulator ^= static_cast<uint32_t>(operation.type);
        auto accumulateOperands = [&hidlModel, &accumulator](const hidl_vec<uint32_t>& operands) {
            for (uint32_t operandIndex : operands) {
                const Operand& operand = hidlModel.operands[operandIndex];
                accumulator ^= static_cast<uint32_t>(operand.type);
                accumulator ^= operand.dimensions.size();
                for (uint32_t dimension : operand.dimensions) {
                    accumulator ^= dimension;
                    if (operand.lifetime == OperandLifeTime::CONSTANT_COPY ||
                        operand.lifetime == OperandLifeTime::CONSTANT_REFERENCE) {
                        accumulator ^= 1;
                    }
                }
            }
        };
        accumulateOperands(operation.inputs);
        accumulateOperands(operation.outputs);
        if (accumulator & 1) {
            (*outSupportedOperations)[operationIndex] = false;
        }
    }
#endif  // NN_DEBUGGABLE
}

DeviceManager* DeviceManager::get() {
    static DeviceManager manager;
    return &manager;
}

void DeviceManager::findAvailableDevices() {
    using ::android::hardware::neuralnetworks::V1_0::IDevice;
    using ::android::hidl::manager::V1_0::IServiceManager;
    VLOG(MANAGER) << "findAvailableDevices";

    sp<IServiceManager> manager = hardware::defaultServiceManager();
    if (manager == nullptr) {
        LOG(ERROR) << "Unable to open defaultServiceManager";
        return;
    }

    manager->listByInterface(IDevice::descriptor, [this](const hidl_vec<hidl_string>& names) {
        for (const auto& name : names) {
            VLOG(MANAGER) << "Found interface " << name.c_str();
            sp<IDevice> device = IDevice::getService(name);
            if (device == nullptr) {
                LOG(ERROR) << "Got a null IDEVICE for " << name.c_str();
                continue;
            }
            registerDevice(name.c_str(), device);
        }
    });
}

void DeviceManager::registerDevice(const char* name, const sp<IDevice>& device) {
    auto d = std::make_shared<Device>(name, device);
    if (d->initialize()) {
        mDevices.push_back(d);
    }
}

DeviceManager::DeviceManager() {
    VLOG(MANAGER) << "DeviceManager::DeviceManager";
    findAvailableDevices();
#ifdef NN_DEBUGGABLE
    mPartitioning = getProp("debug.nn.partition", kPartitioningDefault);
#endif  // NN_DEBUGGABLE
}

}  // namespace nn
}  // namespace android
