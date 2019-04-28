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

#define LOG_TAG "GnssHAL_GnssConfigurationInterface"

#include <log/log.h>

#include "GnssConfiguration.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

GnssConfiguration::GnssConfiguration(const GnssConfigurationInterface* gnssConfigInfc)
    : mGnssConfigIface(gnssConfigInfc) {}

// Methods from ::android::hardware::gps::V1_0::IGnssConfiguration follow.
Return<bool> GnssConfiguration::setSuplEs(bool enabled)  {
    if (mGnssConfigIface == nullptr) {
        ALOGE("%s: GNSS Configuration interface is not available.", __func__);
        return false;
    }

    std::string config = "SUPL_ES=" + std::to_string(enabled ? 1 : 0) + "\n";
    mGnssConfigIface->configuration_update(config.c_str(), config.size());
    return true;
}

Return<bool> GnssConfiguration::setSuplVersion(uint32_t version)  {
    if (mGnssConfigIface == nullptr) {
        ALOGE("%s: GNSS Configuration interface is not available.", __func__);
        return false;
    }

    std::string config = "SUPL_VER=" + std::to_string(version) + "\n";
    mGnssConfigIface->configuration_update(config.c_str(), config.size());

    return true;
}

Return<bool> GnssConfiguration::setSuplMode(uint8_t mode)  {
    if (mGnssConfigIface == nullptr) {
        ALOGE("%s: GNSS Configuration interface is not available.", __func__);
        return false;
    }

    std::string config = "SUPL_MODE=" + std::to_string(mode) + "\n";
    mGnssConfigIface->configuration_update(config.c_str(), config.size());
    return true;
}

Return<bool> GnssConfiguration::setLppProfile(uint8_t lppProfile) {
    if (mGnssConfigIface == nullptr) {
        ALOGE("%s: GNSS Configuration interface is not available.", __func__);
        return false;
    }

    std::string config = "LPP_PROFILE=" + std::to_string(lppProfile) + "\n";
    mGnssConfigIface->configuration_update(config.c_str(), config.size());
    return true;
}

Return<bool> GnssConfiguration::setGlonassPositioningProtocol(uint8_t protocol) {
    if (mGnssConfigIface == nullptr) {
        ALOGE("%s: GNSS Configuration interface is not available.", __func__);
        return false;
    }

    std::string config = "A_GLONASS_POS_PROTOCOL_SELECT=" +
            std::to_string(protocol) + "\n";
    mGnssConfigIface->configuration_update(config.c_str(), config.size());
    return true;
}

Return<bool> GnssConfiguration::setGpsLock(uint8_t lock) {
    if (mGnssConfigIface == nullptr) {
        ALOGE("%s: GNSS Configuration interface is not available.", __func__);
        return false;
    }

    std::string config = "GPS_LOCK=" + std::to_string(lock) + "\n";
    mGnssConfigIface->configuration_update(config.c_str(), config.size());
    return true;
}

Return<bool> GnssConfiguration::setEmergencySuplPdn(bool enabled) {
    if (mGnssConfigIface == nullptr) {
        ALOGE("%s: GNSS Configuration interface is not available.", __func__);
        return false;
    }

    std::string config = "USE_EMERGENCY_PDN_FOR_EMERGENCY_SUPL=" + std::to_string(enabled ? 1 : 0)
            + "\n";
    mGnssConfigIface->configuration_update(config.c_str(), config.size());
    return true;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
