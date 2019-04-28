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

#include "GnssUtils.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

using android::hardware::gnss::V1_0::GnssLocation;

GnssLocation convertToGnssLocation(GpsLocation* location) {
    GnssLocation gnssLocation = {};
    if (location != nullptr) {
        gnssLocation = {
            // Bit operation AND with 1f below is needed to clear vertical accuracy,
            // speed accuracy and bearing accuracy flags as some vendors are found
            // to be setting these bits in pre-Android-O devices
            .gnssLocationFlags = static_cast<uint16_t>(location->flags & 0x1f),
            .latitudeDegrees = location->latitude,
            .longitudeDegrees = location->longitude,
            .altitudeMeters = location->altitude,
            .speedMetersPerSec = location->speed,
            .bearingDegrees = location->bearing,
            .horizontalAccuracyMeters = location->accuracy,
            // Older chipsets do not provide the following 3 fields, hence the flags
            // HAS_VERTICAL_ACCURACY, HAS_SPEED_ACCURACY and HAS_BEARING_ACCURACY are
            // not set and the field are set to zeros.
            .verticalAccuracyMeters = 0,
            .speedAccuracyMetersPerSecond = 0,
            .bearingAccuracyDegrees = 0,
            .timestamp = location->timestamp
        };
    }

    return gnssLocation;
}

GnssLocation convertToGnssLocation(FlpLocation* location) {
    GnssLocation gnssLocation = {};
    if (location != nullptr) {
        gnssLocation = {
            // Bit mask applied (and 0's below) for same reason as above with GpsLocation
            .gnssLocationFlags = static_cast<uint16_t>(location->flags & 0x1f),
            .latitudeDegrees = location->latitude,
            .longitudeDegrees = location->longitude,
            .altitudeMeters = location->altitude,
            .speedMetersPerSec = location->speed,
            .bearingDegrees = location->bearing,
            .horizontalAccuracyMeters = location->accuracy,
            .verticalAccuracyMeters = 0,
            .speedAccuracyMetersPerSecond = 0,
            .bearingAccuracyDegrees = 0,
            .timestamp = location->timestamp
        };
    }

    return gnssLocation;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
