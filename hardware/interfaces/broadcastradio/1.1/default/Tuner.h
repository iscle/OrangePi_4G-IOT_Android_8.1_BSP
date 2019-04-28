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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_1_TUNER_H
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_1_TUNER_H

#include "VirtualRadio.h"

#include <android/hardware/broadcastradio/1.1/ITuner.h>
#include <android/hardware/broadcastradio/1.1/ITunerCallback.h>
#include <broadcastradio-utils/WorkerThread.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace implementation {

struct Tuner : public ITuner {
    Tuner(V1_0::Class classId, const sp<V1_0::ITunerCallback>& callback);

    void forceClose();

    // V1_1::ITuner methods
    virtual Return<Result> setConfiguration(const V1_0::BandConfig& config) override;
    virtual Return<void> getConfiguration(getConfiguration_cb _hidl_cb) override;
    virtual Return<Result> scan(V1_0::Direction direction, bool skipSubChannel) override;
    virtual Return<Result> step(V1_0::Direction direction, bool skipSubChannel) override;
    virtual Return<Result> tune(uint32_t channel, uint32_t subChannel) override;
    virtual Return<Result> tuneByProgramSelector(const ProgramSelector& program) override;
    virtual Return<Result> cancel() override;
    virtual Return<Result> cancelAnnouncement() override;
    virtual Return<void> getProgramInformation(getProgramInformation_cb _hidl_cb) override;
    virtual Return<void> getProgramInformation_1_1(getProgramInformation_1_1_cb _hidl_cb) override;
    virtual Return<ProgramListResult> startBackgroundScan() override;
    virtual Return<void> getProgramList(const hidl_vec<VendorKeyValue>& filter,
                                        getProgramList_cb _hidl_cb) override;
    virtual Return<Result> setAnalogForced(bool isForced) override;
    virtual Return<void> isAnalogForced(isAnalogForced_cb _hidl_cb) override;

   private:
    std::mutex mMut;
    WorkerThread mThread;
    bool mIsClosed = false;

    V1_0::Class mClassId;
    const sp<V1_0::ITunerCallback> mCallback;
    const sp<V1_1::ITunerCallback> mCallback1_1;

    std::reference_wrapper<VirtualRadio> mVirtualRadio;
    bool mIsAmfmConfigSet = false;
    V1_0::BandConfig mAmfmConfig;
    bool mIsTuneCompleted = false;
    ProgramSelector mCurrentProgram = {};
    ProgramInfo mCurrentProgramInfo = {};
    std::atomic<bool> mIsAnalogForced;

    utils::HalRevision getHalRev() const;
    void tuneInternalLocked(const ProgramSelector& sel);
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_1_TUNER_H
