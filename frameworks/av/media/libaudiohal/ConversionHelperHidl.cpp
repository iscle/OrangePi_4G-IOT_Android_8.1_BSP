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

#include <string.h>

#define LOG_TAG "HalHidl"
#include <media/AudioParameter.h>
#include <utils/Log.h>

#include "ConversionHelperHidl.h"

using ::android::hardware::audio::V2_0::Result;

namespace android {

// static
status_t ConversionHelperHidl::keysFromHal(const String8& keys, hidl_vec<hidl_string> *hidlKeys) {
    AudioParameter halKeys(keys);
    if (halKeys.size() == 0) return BAD_VALUE;
    hidlKeys->resize(halKeys.size());
    //FIXME:  keyStreamSupportedChannels and keyStreamSupportedSamplingRates come with a
    // "keyFormat=<value>" pair. We need to transform it into a single key string so that it is
    // carried over to the legacy HAL via HIDL.
    String8 value;
    bool keepFormatValue = halKeys.size() == 2 &&
         (halKeys.get(String8(AudioParameter::keyStreamSupportedChannels), value) == NO_ERROR ||
         halKeys.get(String8(AudioParameter::keyStreamSupportedSamplingRates), value) == NO_ERROR);

    for (size_t i = 0; i < halKeys.size(); ++i) {
        String8 key;
        status_t status = halKeys.getAt(i, key);
        if (status != OK) return status;
        if (keepFormatValue && key == AudioParameter::keyFormat) {
            AudioParameter formatParam;
            halKeys.getAt(i, key, value);
            formatParam.add(key, value);
            key = formatParam.toString();
        }
        (*hidlKeys)[i] = key.string();
    }
    return OK;
}

// static
status_t ConversionHelperHidl::parametersFromHal(
        const String8& kvPairs, hidl_vec<ParameterValue> *hidlParams) {
    AudioParameter params(kvPairs);
    if (params.size() == 0) return BAD_VALUE;
    hidlParams->resize(params.size());
    for (size_t i = 0; i < params.size(); ++i) {
        String8 key, value;
        status_t status = params.getAt(i, key, value);
        if (status != OK) return status;
        (*hidlParams)[i].key = key.string();
        (*hidlParams)[i].value = value.string();
    }
    return OK;
}

// static
void ConversionHelperHidl::parametersToHal(
        const hidl_vec<ParameterValue>& parameters, String8 *values) {
    AudioParameter params;
    for (size_t i = 0; i < parameters.size(); ++i) {
        params.add(String8(parameters[i].key.c_str()), String8(parameters[i].value.c_str()));
    }
    values->setTo(params.toString());
}

ConversionHelperHidl::ConversionHelperHidl(const char* className)
        : mClassName(className) {
}

// static
status_t ConversionHelperHidl::analyzeResult(const Result& result) {
    switch (result) {
        case Result::OK: return OK;
        case Result::INVALID_ARGUMENTS: return BAD_VALUE;
        case Result::INVALID_STATE: return NOT_ENOUGH_DATA;
        case Result::NOT_INITIALIZED: return NO_INIT;
        case Result::NOT_SUPPORTED: return INVALID_OPERATION;
        default: return NO_INIT;
    }
}

void ConversionHelperHidl::emitError(const char* funcName, const char* description) {
    ALOGE("%s %p %s: %s (from rpc)", mClassName, this, funcName, description);
}

}  // namespace android
