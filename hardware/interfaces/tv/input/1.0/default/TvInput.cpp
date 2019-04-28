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

#define LOG_TAG "android.hardware.tv.input@1.0-service"
#include <android-base/logging.h>

#include "TvInput.h"

namespace android {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {
namespace implementation {

static_assert(TV_INPUT_TYPE_OTHER_HARDWARE == static_cast<int>(TvInputType::OTHER),
        "TvInputType::OTHER must match legacy value.");
static_assert(TV_INPUT_TYPE_TUNER == static_cast<int>(TvInputType::TUNER),
        "TvInputType::TUNER must match legacy value.");
static_assert(TV_INPUT_TYPE_COMPOSITE == static_cast<int>(TvInputType::COMPOSITE),
        "TvInputType::COMPOSITE must match legacy value.");
static_assert(TV_INPUT_TYPE_SVIDEO == static_cast<int>(TvInputType::SVIDEO),
        "TvInputType::SVIDEO must match legacy value.");
static_assert(TV_INPUT_TYPE_SCART == static_cast<int>(TvInputType::SCART),
        "TvInputType::SCART must match legacy value.");
static_assert(TV_INPUT_TYPE_COMPONENT == static_cast<int>(TvInputType::COMPONENT),
        "TvInputType::COMPONENT must match legacy value.");
static_assert(TV_INPUT_TYPE_VGA == static_cast<int>(TvInputType::VGA),
        "TvInputType::VGA must match legacy value.");
static_assert(TV_INPUT_TYPE_DVI == static_cast<int>(TvInputType::DVI),
        "TvInputType::DVI must match legacy value.");
static_assert(TV_INPUT_TYPE_HDMI == static_cast<int>(TvInputType::HDMI),
        "TvInputType::HDMI must match legacy value.");
static_assert(TV_INPUT_TYPE_DISPLAY_PORT == static_cast<int>(TvInputType::DISPLAY_PORT),
        "TvInputType::DISPLAY_PORT must match legacy value.");

static_assert(TV_INPUT_EVENT_DEVICE_AVAILABLE == static_cast<int>(
        TvInputEventType::DEVICE_AVAILABLE),
        "TvInputEventType::DEVICE_AVAILABLE must match legacy value.");
static_assert(TV_INPUT_EVENT_DEVICE_UNAVAILABLE == static_cast<int>(
        TvInputEventType::DEVICE_UNAVAILABLE),
        "TvInputEventType::DEVICE_UNAVAILABLE must match legacy value.");
static_assert(TV_INPUT_EVENT_STREAM_CONFIGURATIONS_CHANGED == static_cast<int>(
        TvInputEventType::STREAM_CONFIGURATIONS_CHANGED),
        "TvInputEventType::STREAM_CONFIGURATIONS_CHANGED must match legacy value.");

sp<ITvInputCallback> TvInput::mCallback = nullptr;

TvInput::TvInput(tv_input_device_t* device) : mDevice(device) {
    mCallbackOps.notify = &TvInput::notify;
}

TvInput::~TvInput() {
    if (mDevice != nullptr) {
        free(mDevice);
    }
}

// Methods from ::android::hardware::tv_input::V1_0::ITvInput follow.
Return<void> TvInput::setCallback(const sp<ITvInputCallback>& callback)  {
    mCallback = callback;
    if (mCallback != nullptr) {
        mDevice->initialize(mDevice, &mCallbackOps, nullptr);
    }
    return Void();
}

Return<void> TvInput::getStreamConfigurations(int32_t deviceId, getStreamConfigurations_cb cb)  {
    int32_t configCount = 0;
    const tv_stream_config_t* configs = nullptr;
    int ret = mDevice->get_stream_configurations(mDevice, deviceId, &configCount, &configs);
    Result res = Result::UNKNOWN;
    hidl_vec<TvStreamConfig> tvStreamConfigs;
    if (ret == 0) {
        res = Result::OK;
        tvStreamConfigs.resize(getSupportedConfigCount(configCount, configs));
        int32_t pos = 0;
        for (int32_t i = 0; i < configCount; ++i) {
            if (isSupportedStreamType(configs[i].type)) {
                tvStreamConfigs[pos].streamId = configs[i].stream_id;
                tvStreamConfigs[pos].maxVideoWidth = configs[i].max_video_width;
                tvStreamConfigs[pos].maxVideoHeight = configs[i].max_video_height;
                ++pos;
            }
        }
    } else if (ret == -EINVAL) {
        res = Result::INVALID_ARGUMENTS;
    }
    cb(res, tvStreamConfigs);
    return Void();
}

Return<void> TvInput::openStream(int32_t deviceId, int32_t streamId, openStream_cb cb)  {
    tv_stream_t stream;
    stream.stream_id = streamId;
    int ret = mDevice->open_stream(mDevice, deviceId, &stream);
    Result res = Result::UNKNOWN;
    native_handle_t* sidebandStream = nullptr;
    if (ret == 0) {
        if (isSupportedStreamType(stream.type)) {
            res = Result::OK;
            sidebandStream = stream.sideband_stream_source_handle;
        }
    } else {
        if (ret == -EBUSY) {
            res = Result::NO_RESOURCE;
        } else if (ret == -EEXIST) {
            res = Result::INVALID_STATE;
        } else if (ret == -EINVAL) {
            res = Result::INVALID_ARGUMENTS;
        }
    }
    cb(res, sidebandStream);
    return Void();
}

Return<Result> TvInput::closeStream(int32_t deviceId, int32_t streamId)  {
    int ret = mDevice->close_stream(mDevice, deviceId, streamId);
    Result res = Result::UNKNOWN;
    if (ret == 0) {
        res = Result::OK;
    } else if (ret == -ENOENT) {
        res = Result::INVALID_STATE;
    } else if (ret == -EINVAL) {
        res = Result::INVALID_ARGUMENTS;
    }
    return res;
}

// static
void TvInput::notify(struct tv_input_device* __unused, tv_input_event_t* event,
        void* __unused) {
    if (mCallback != nullptr && event != nullptr) {
        // Capturing is no longer supported.
        if (event->type >= TV_INPUT_EVENT_CAPTURE_SUCCEEDED) {
            return;
        }
        TvInputEvent tvInputEvent;
        tvInputEvent.type = static_cast<TvInputEventType>(event->type);
        tvInputEvent.deviceInfo.deviceId = event->device_info.device_id;
        tvInputEvent.deviceInfo.type = static_cast<TvInputType>(
                event->device_info.type);
        tvInputEvent.deviceInfo.portId = event->device_info.hdmi.port_id;
        tvInputEvent.deviceInfo.cableConnectionStatus = CableConnectionStatus::UNKNOWN;
        // TODO: Ensure the legacy audio type code is the same once audio HAL default
        // implementation is ready.
        tvInputEvent.deviceInfo.audioType = static_cast<AudioDevice>(
                event->device_info.audio_type);
        memset(tvInputEvent.deviceInfo.audioAddress.data(), 0,
                tvInputEvent.deviceInfo.audioAddress.size());
        const char* address = event->device_info.audio_address;
        if (address != nullptr) {
            size_t size = strlen(address);
            if (size > tvInputEvent.deviceInfo.audioAddress.size()) {
                LOG(ERROR) << "Audio address is too long. Address:" << address << "";
                return;
            }
            for (size_t i = 0; i < size; ++i) {
                tvInputEvent.deviceInfo.audioAddress[i] =
                    static_cast<uint8_t>(event->device_info.audio_address[i]);
            }
        }
        mCallback->notify(tvInputEvent);
    }
}

// static
uint32_t TvInput::getSupportedConfigCount(uint32_t configCount,
        const tv_stream_config_t* configs) {
    uint32_t supportedConfigCount = 0;
    for (uint32_t i = 0; i < configCount; ++i) {
        if (isSupportedStreamType(configs[i].type)) {
            supportedConfigCount++;
        }
    }
    return supportedConfigCount;
}

// static
bool TvInput::isSupportedStreamType(int type) {
    // Buffer producer type is no longer supported.
    return type != TV_STREAM_TYPE_BUFFER_PRODUCER;
}

ITvInput* HIDL_FETCH_ITvInput(const char* /* name */) {
    int ret = 0;
    const hw_module_t* hw_module = nullptr;
    tv_input_device_t* input_device;
    ret = hw_get_module(TV_INPUT_HARDWARE_MODULE_ID, &hw_module);
    if (ret == 0 && hw_module->methods->open != nullptr) {
        ret = hw_module->methods->open(hw_module, TV_INPUT_DEFAULT_DEVICE,
                reinterpret_cast<hw_device_t**>(&input_device));
        if (ret == 0) {
            return new TvInput(input_device);
        }
        else {
            LOG(ERROR) << "Passthrough failed to load legacy HAL.";
            return nullptr;
        }
    }
    else {
        LOG(ERROR) << "hw_get_module " << TV_INPUT_HARDWARE_MODULE_ID
                   << " failed: " << ret;
        return nullptr;
    }
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace input
}  // namespace tv
}  // namespace hardware
}  // namespace android
