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

#ifndef android_hardware_automotive_vehicle_V2_0_impl_VehicleHalEmulator_H_
#define android_hardware_automotive_vehicle_V2_0_impl_VehicleHalEmulator_H_

#include <log/log.h>
#include <memory>
#include <thread>
#include <vector>

#include "vhal_v2_0/VehicleHal.h"

#include "CommBase.h"
#include "VehicleHalProto.pb.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

class VehicleEmulator;  // Forward declaration.

/** Extension of VehicleHal that used by VehicleEmulator. */
class EmulatedVehicleHalIface : public VehicleHal {
public:
    virtual bool setPropertyFromVehicle(const VehiclePropValue& propValue) = 0;
    virtual std::vector<VehiclePropValue> getAllProperties() const = 0;

    void registerEmulator(VehicleEmulator* emulator) {
        ALOGI("%s, emulator: %p", __func__, emulator);
        std::lock_guard<std::mutex> g(mEmulatorLock);
        mEmulator = emulator;
    }

protected:
    VehicleEmulator* getEmulatorOrDie() {
        std::lock_guard<std::mutex> g(mEmulatorLock);
        if (mEmulator == nullptr) abort();
        return mEmulator;
    }

private:
    mutable std::mutex mEmulatorLock;
    VehicleEmulator* mEmulator;
};

struct CommFactory {
    static std::unique_ptr<CommBase> create();
};

/**
 * Emulates vehicle by providing controlling interface from host side either through ADB or Pipe.
 */
class VehicleEmulator {
public:
    VehicleEmulator(EmulatedVehicleHalIface* hal,
                    std::unique_ptr<CommBase> comm = CommFactory::create())
            : mHal { hal },
              mComm(comm.release()),
              mThread { &VehicleEmulator::rxThread, this} {
        mHal->registerEmulator(this);
    }
    virtual ~VehicleEmulator();

    void doSetValueFromClient(const VehiclePropValue& propValue);

private:
    using EmulatorMessage = emulator::EmulatorMessage;

    void doGetConfig(EmulatorMessage& rxMsg, EmulatorMessage& respMsg);
    void doGetConfigAll(EmulatorMessage& rxMsg, EmulatorMessage& respMsg);
    void doGetProperty(EmulatorMessage& rxMsg, EmulatorMessage& respMsg);
    void doGetPropertyAll(EmulatorMessage& rxMsg, EmulatorMessage& respMsg);
    void doSetProperty(EmulatorMessage& rxMsg, EmulatorMessage& respMsg);
    void txMsg(emulator::EmulatorMessage& txMsg);
    void parseRxProtoBuf(std::vector<uint8_t>& msg);
    void populateProtoVehicleConfig(emulator::VehiclePropConfig* protoCfg,
                                    const VehiclePropConfig& cfg);
    void populateProtoVehiclePropValue(emulator::VehiclePropValue* protoVal,
                                       const VehiclePropValue* val);
    void rxMsg();
    void rxThread();

private:
    std::atomic<bool> mExit { false };
    EmulatedVehicleHalIface* mHal;
    std::unique_ptr<CommBase> mComm;
    std::thread mThread;
};

}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif // android_hardware_automotive_vehicle_V2_0_impl_VehicleHalEmulator_H_
