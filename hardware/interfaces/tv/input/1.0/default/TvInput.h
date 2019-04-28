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
#ifndef ANDROID_HARDWARE_TV_INPUT_V1_0_TVINPUT_H
#define ANDROID_HARDWARE_TV_INPUT_V1_0_TVINPUT_H

#include <android/hardware/tv/input/1.0/ITvInput.h>
#include <hidl/Status.h>
#include <hardware/tv_input.h>

#include <hidl/MQDescriptor.h>

namespace android {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::tv::input::V1_0::ITvInput;
using ::android::hardware::tv::input::V1_0::ITvInputCallback;
using ::android::hardware::tv::input::V1_0::Result;
using ::android::hardware::tv::input::V1_0::TvInputEvent;
using ::android::hardware::tv::input::V1_0::TvStreamConfig;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct TvInput : public ITvInput {
    TvInput(tv_input_device_t* device);
    ~TvInput();
    Return<void> setCallback(const sp<ITvInputCallback>& callback)  override;
    Return<void> getStreamConfigurations(int32_t deviceId,
            getStreamConfigurations_cb _hidl_cb)  override;
    Return<void> openStream(int32_t deviceId, int32_t streamId,
            openStream_cb _hidl_cb)  override;
    Return<Result> closeStream(int32_t deviceId, int32_t streamId)  override;

    static void notify(struct tv_input_device* __unused, tv_input_event_t* event,
            void* __unused);
    static uint32_t getSupportedConfigCount(uint32_t configCount,
            const tv_stream_config_t* configs);
    static bool isSupportedStreamType(int type);

    private:
    static sp<ITvInputCallback> mCallback;
    tv_input_callback_ops_t mCallbackOps;
    tv_input_device_t* mDevice;
};

extern "C" ITvInput* HIDL_FETCH_ITvInput(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace input
}  // namespace tv
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TV_INPUT_V1_0_TVINPUT_H
