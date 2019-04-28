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

#ifndef android_hardware_automotive_vehicle_V2_0_impl_PipeComm_H_
#define android_hardware_automotive_vehicle_V2_0_impl_PipeComm_H_

#include <mutex>
#include <vector>
#include "CommBase.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

/**
 * PipeComm uses a qemu pipe interface to connect to the Goldfish Emulator.
 */
class PipeComm : public CommBase {
public:
    PipeComm();

    /**
     * Opens a pipe and begins listening.
     *
     * @return int Returns 0 on success.
     */
    int open() override;

    /**
     * Blocking call to read data from the connection.
     *
     * @return std::vector<uint8_t> Serialized protobuf data received from emulator.  This will be
     *              an empty vector if the connection was closed or some other error occurred.
     */
    std::vector<uint8_t> read() override;

    /**
     * Transmits a string of data to the emulator.
     *
     * @param data Serialized protobuf data to transmit.
     *
     * @return int Number of bytes transmitted, or -1 if failed.
     */
    int write(const std::vector<uint8_t>& data) override;

private:
    std::mutex mMutex;
    int mPipeFd;
};

}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android


#endif  // android_hardware_automotive_vehicle_V2_0_impl_PipeComm_H_
