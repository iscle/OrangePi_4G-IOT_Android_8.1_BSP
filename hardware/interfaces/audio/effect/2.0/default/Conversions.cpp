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

#include <memory.h>
#include <stdio.h>

#include "Conversions.h"
#include "HidlUtils.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

void effectDescriptorFromHal(
        const effect_descriptor_t& halDescriptor, EffectDescriptor* descriptor) {
    HidlUtils::uuidFromHal(halDescriptor.type, &descriptor->type);
    HidlUtils::uuidFromHal(halDescriptor.uuid, &descriptor->uuid);
    descriptor->flags = EffectFlags(halDescriptor.flags);
    descriptor->cpuLoad = halDescriptor.cpuLoad;
    descriptor->memoryUsage = halDescriptor.memoryUsage;
    memcpy(descriptor->name.data(), halDescriptor.name, descriptor->name.size());
    memcpy(descriptor->implementor.data(),
            halDescriptor.implementor, descriptor->implementor.size());
}

std::string uuidToString(const effect_uuid_t& halUuid) {
    char str[64];
    snprintf(str, sizeof(str), "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x",
            halUuid.timeLow,
            halUuid.timeMid,
            halUuid.timeHiAndVersion,
            halUuid.clockSeq,
            halUuid.node[0],
            halUuid.node[1],
            halUuid.node[2],
            halUuid.node[3],
            halUuid.node[4],
            halUuid.node[5]);
    return str;
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
