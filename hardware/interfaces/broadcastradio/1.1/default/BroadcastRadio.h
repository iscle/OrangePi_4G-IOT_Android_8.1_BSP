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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BROADCASTRADIO_H
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BROADCASTRADIO_H

#include "Tuner.h"

#include <android/hardware/broadcastradio/1.1/IBroadcastRadio.h>
#include <android/hardware/broadcastradio/1.1/types.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace implementation {

struct AmFmBandConfig {
    V1_0::Band type;
    uint32_t lowerLimit;  // kHz
    uint32_t upperLimit;  // kHz
    std::vector<uint32_t> spacings;  // kHz
};

struct ModuleConfig {
    std::string productName;
    std::vector<AmFmBandConfig> amFmBands;
};

struct BroadcastRadio : public V1_1::IBroadcastRadio {
    /**
     * Constructs new broadcast radio module.
     *
     * Before calling a constructor with a given classId, it must be checked with isSupported
     * method first. Otherwise it results in undefined behaviour.
     *
     * @param classId type of a radio.
     */
    BroadcastRadio(V1_0::Class classId);

    /**
     * Checks, if a given radio type is supported.
     *
     * @param classId type of a radio.
     */
    static bool isSupported(V1_0::Class classId);

    // V1_1::IBroadcastRadio methods
    Return<void> getProperties(getProperties_cb _hidl_cb) override;
    Return<void> getProperties_1_1(getProperties_1_1_cb _hidl_cb) override;
    Return<void> openTuner(const V1_0::BandConfig& config, bool audio,
                           const sp<V1_0::ITunerCallback>& callback,
                           openTuner_cb _hidl_cb) override;
    Return<void> getImage(int32_t id, getImage_cb _hidl_cb);

   private:
    std::mutex mMut;
    V1_0::Class mClassId;
    ModuleConfig mConfig;
    wp<Tuner> mTuner;
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BROADCASTRADIO_H
