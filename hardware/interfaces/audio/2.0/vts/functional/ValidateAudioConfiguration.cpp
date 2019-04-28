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

#include <string>
#include <unistd.h>

#include "utility/ValidateXml.h"

TEST(CheckConfig, audioPolicyConfigurationValidation) {
    const char* configName = "audio_policy_configuration.xml";
    const char* possibleConfigLocations[] = {"/odm/etc", "/vendor/etc", "/system/etc"};
    const char* configSchemaPath = "/data/local/tmp/audio_policy_configuration.xsd";

    for (std::string folder : possibleConfigLocations) {
        const auto configPath = folder + '/' + configName;
        if (access(configPath.c_str(), R_OK) == 0) {
            ASSERT_VALID_XML(configPath.c_str(), configSchemaPath);
            return; // The framework does not read past the first config file found
        }
    }
}
