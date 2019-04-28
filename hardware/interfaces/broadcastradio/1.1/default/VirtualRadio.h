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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_1_VIRTUALRADIO_H
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_1_VIRTUALRADIO_H

#include "VirtualProgram.h"

#include <mutex>
#include <vector>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace implementation {

/**
 * A radio frequency space mock.
 *
 * This represents all broadcast waves in the air for a given radio technology,
 * not a captured station list in the radio tuner memory.
 *
 * It's meant to abstract out radio content from default tuner implementation.
 */
class VirtualRadio {
   public:
    VirtualRadio(const std::vector<VirtualProgram> initialList);

    std::vector<VirtualProgram> getProgramList();
    bool getProgram(const ProgramSelector& selector, VirtualProgram& program);

   private:
    std::mutex mMut;
    std::vector<VirtualProgram> mPrograms;
};

/**
 * Get virtual radio space for a given radio class.
 *
 * As a space, each virtual radio always exists. For example, DAB frequencies
 * exists in US, but contains no programs.
 *
 * The lifetime of the virtual radio space is virtually infinite, but for the
 * needs of default implementation, it's bound with the lifetime of default
 * implementation process.
 *
 * Internally, it's a static object, so trying to access the reference during
 * default implementation library unloading may result in segmentation fault.
 * It's unlikely for testing purposes.
 *
 * @param classId A class of radio technology.
 * @return A reference to virtual radio space for a given technology.
 */
VirtualRadio& getRadio(V1_0::Class classId);

VirtualRadio& getAmRadio();
VirtualRadio& getFmRadio();
VirtualRadio& getSatRadio();
VirtualRadio& getDigitalRadio();

}  // namespace implementation
}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_1_VIRTUALRADIO_H
