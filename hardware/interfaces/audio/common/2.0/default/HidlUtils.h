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

#ifndef android_hardware_audio_V2_0_Hidl_Utils_H_
#define android_hardware_audio_V2_0_Hidl_Utils_H_

#include <memory>

#include <android/hardware/audio/common/2.0/types.h>
#include <system/audio.h>

using ::android::hardware::audio::common::V2_0::AudioConfig;
using ::android::hardware::audio::common::V2_0::AudioGain;
using ::android::hardware::audio::common::V2_0::AudioGainConfig;
using ::android::hardware::audio::common::V2_0::AudioOffloadInfo;
using ::android::hardware::audio::common::V2_0::AudioPort;
using ::android::hardware::audio::common::V2_0::AudioPortConfig;
using ::android::hardware::audio::common::V2_0::Uuid;
using ::android::hardware::hidl_vec;

namespace android {

class HidlUtils {
  public:
    static void audioConfigFromHal(const audio_config_t& halConfig, AudioConfig* config);
    static void audioConfigToHal(const AudioConfig& config, audio_config_t* halConfig);
    static void audioGainConfigFromHal(
            const struct audio_gain_config& halConfig, AudioGainConfig* config);
    static void audioGainConfigToHal(
            const AudioGainConfig& config, struct audio_gain_config* halConfig);
    static void audioGainFromHal(const struct audio_gain& halGain, AudioGain* gain);
    static void audioGainToHal(const AudioGain& gain, struct audio_gain* halGain);
    static void audioOffloadInfoFromHal(
            const audio_offload_info_t& halOffload, AudioOffloadInfo* offload);
    static void audioOffloadInfoToHal(
            const AudioOffloadInfo& offload, audio_offload_info_t* halOffload);
    static void audioPortConfigFromHal(
            const struct audio_port_config& halConfig, AudioPortConfig* config);
    static void audioPortConfigToHal(
            const AudioPortConfig& config, struct audio_port_config* halConfig);
    static void audioPortConfigsFromHal(
            unsigned int numHalConfigs, const struct audio_port_config *halConfigs,
            hidl_vec<AudioPortConfig> *configs);
    static std::unique_ptr<audio_port_config[]> audioPortConfigsToHal(
            const hidl_vec<AudioPortConfig>& configs);
    static void audioPortFromHal(const struct audio_port& halPort, AudioPort* port);
    static void audioPortToHal(const AudioPort& port, struct audio_port* halPort);
    static void uuidFromHal(const audio_uuid_t& halUuid, Uuid* uuid);
    static void uuidToHal(const Uuid& uuid, audio_uuid_t* halUuid);
};

}  // namespace android

#endif  // android_hardware_audio_V2_0_Hidl_Utils_H_
