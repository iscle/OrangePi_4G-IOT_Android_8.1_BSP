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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_0_TUNER_H
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_0_TUNER_H

#include <android/hardware/broadcastradio/1.0/ITuner.h>
#include <android/hardware/broadcastradio/1.0/ITunerCallback.h>
#include <hidl/Status.h>
#include <hardware/radio.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {
namespace implementation {

struct BroadcastRadio;

struct Tuner : public ITuner {

    Tuner(const sp<ITunerCallback>& callback, const wp<BroadcastRadio>& mParentDevice);

    // Methods from ::android::hardware::broadcastradio::V1_0::ITuner follow.
    Return<Result> setConfiguration(const BandConfig& config)  override;
    Return<void> getConfiguration(getConfiguration_cb _hidl_cb)  override;
    Return<Result> scan(Direction direction, bool skipSubChannel)  override;
    Return<Result> step(Direction direction, bool skipSubChannel)  override;
    Return<Result> tune(uint32_t channel, uint32_t subChannel)  override;
    Return<Result> cancel()  override;
    Return<void> getProgramInformation(getProgramInformation_cb _hidl_cb)  override;

    static void callback(radio_hal_event_t *halEvent, void *cookie);
           void onCallback(radio_hal_event_t *halEvent);

    void setHalTuner(const struct radio_tuner *halTuner) { mHalTuner = halTuner; }
    const struct radio_tuner *getHalTuner() { return mHalTuner; }

 private:
    ~Tuner();

    const struct radio_tuner    *mHalTuner;
    const sp<ITunerCallback>     mCallback;
    const wp<BroadcastRadio>     mParentDevice;
};


}  // namespace implementation
}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_0_TUNER_H
