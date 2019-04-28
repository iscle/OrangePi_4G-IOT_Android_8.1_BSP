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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_0_UTILS_H
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_0_UTILS_H

#include <android/hardware/broadcastradio/1.0/types.h>
#include <hardware/radio.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {
namespace implementation {

class Utils {
public:
    static const char * getClassString(Class ClassId);
    static Result convertHalResult(int rc);
    static void convertBandConfigFromHal(BandConfig *config,
            const radio_hal_band_config_t *halConfig);
    static void convertPropertiesFromHal(Properties *properties,
            const radio_hal_properties_t *halProperties);
    static void convertBandConfigToHal(radio_hal_band_config_t *halConfig,
            const BandConfig *config);
    static void convertProgramInfoFromHal(ProgramInfo *info,
                                          radio_program_info_t *halInfo);
    static int convertMetaDataFromHal(hidl_vec<MetaData>& metadata,
                                       radio_metadata_t *halMetadata);
private:
    static const char * sClassModuleNames[];

};

}  // namespace implementation
}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_0_UTILS_H
