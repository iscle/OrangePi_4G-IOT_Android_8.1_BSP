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

#ifndef android_hardware_automotive_vehicle_V2_0_impl_CommBase_H_
#define android_hardware_automotive_vehicle_V2_0_impl_CommBase_H_

#include <string>
#include <vector>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

/**
 * This is the communications base class.  It defines the interface used in DefaultVehicleHal to
 * send and receive data to and from the emulator.
 */
class CommBase {
public:
    virtual ~CommBase() = default;

    /**
     * Closes a connection if it is open.
     */
    virtual void stop() {}

    /**
     * Creates a connection to the other side.
     *
     * @return int Returns fd or socket number if connection is successful.
     *              Otherwise, returns -1 if no connection is availble.
     */
    virtual int connect() { return 0; }

    /**
     * Opens the communications channel.
     *
     * @return int Returns 0 if channel is opened, else -errno if failed.
     */
    virtual int open() = 0;

    /**
     * Blocking call to read data from the connection.
     *
     * @return std::vector<uint8_t> Serialized protobuf data received from emulator.  This will be
     *              an empty vector if the connection was closed or some other error occurred.
     */
    virtual std::vector<uint8_t> read() = 0;

    /**
     * Transmits a string of data to the emulator.
     *
     * @param data Serialized protobuf data to transmit.
     *
     * @return int Number of bytes transmitted, or -1 if failed.
     */
    virtual int write(const std::vector<uint8_t>& data) = 0;
};

}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android


#endif  // android_hardware_automotive_vehicle_V2_0_impl_CommBase_H_
