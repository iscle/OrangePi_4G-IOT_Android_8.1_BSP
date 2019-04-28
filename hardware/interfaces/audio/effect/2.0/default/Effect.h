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

#ifndef ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_EFFECT_H
#define ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_EFFECT_H

#include <atomic>
#include <memory>
#include <vector>

#include <android/hardware/audio/effect/2.0/IEffect.h>
#include <fmq/EventFlag.h>
#include <fmq/MessageQueue.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/Thread.h>

#include <hardware/audio_effect.h>

#include "AudioBufferManager.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::audio::common::V2_0::AudioMode;
using ::android::hardware::audio::common::V2_0::AudioSource;
using ::android::hardware::audio::common::V2_0::Uuid;
using ::android::hardware::audio::effect::V2_0::AudioBuffer;
using ::android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig;
using ::android::hardware::audio::effect::V2_0::EffectConfig;
using ::android::hardware::audio::effect::V2_0::EffectDescriptor;
using ::android::hardware::audio::effect::V2_0::EffectFeature;
using ::android::hardware::audio::effect::V2_0::EffectOffloadParameter;
using ::android::hardware::audio::effect::V2_0::IEffect;
using ::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback;
using ::android::hardware::audio::effect::V2_0::Result;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Effect : public IEffect {
    typedef MessageQueue<Result, kSynchronizedReadWrite> StatusMQ;
    using GetParameterSuccessCallback =
            std::function<void(uint32_t valueSize, const void* valueData)>;

    explicit Effect(effect_handle_t handle);

    // Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
    Return<Result> init()  override;
    Return<Result> setConfig(
            const EffectConfig& config,
            const sp<IEffectBufferProviderCallback>& inputBufferProvider,
            const sp<IEffectBufferProviderCallback>& outputBufferProvider)  override;
    Return<Result> reset()  override;
    Return<Result> enable()  override;
    Return<Result> disable()  override;
    Return<Result> setDevice(AudioDevice device)  override;
    Return<void> setAndGetVolume(
            const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb)  override;
    Return<Result> volumeChangeNotification(const hidl_vec<uint32_t>& volumes)  override;
    Return<Result> setAudioMode(AudioMode mode)  override;
    Return<Result> setConfigReverse(
            const EffectConfig& config,
            const sp<IEffectBufferProviderCallback>& inputBufferProvider,
            const sp<IEffectBufferProviderCallback>& outputBufferProvider)  override;
    Return<Result> setInputDevice(AudioDevice device)  override;
    Return<void> getConfig(getConfig_cb _hidl_cb)  override;
    Return<void> getConfigReverse(getConfigReverse_cb _hidl_cb)  override;
    Return<void> getSupportedAuxChannelsConfigs(
            uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb)  override;
    Return<void> getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb)  override;
    Return<Result> setAuxChannelsConfig(const EffectAuxChannelsConfig& config)  override;
    Return<Result> setAudioSource(AudioSource source)  override;
    Return<Result> offload(const EffectOffloadParameter& param)  override;
    Return<void> getDescriptor(getDescriptor_cb _hidl_cb)  override;
    Return<void> prepareForProcessing(prepareForProcessing_cb _hidl_cb)  override;
    Return<Result> setProcessBuffers(
            const AudioBuffer& inBuffer, const AudioBuffer& outBuffer)  override;
    Return<void> command(
            uint32_t commandId,
            const hidl_vec<uint8_t>& data,
            uint32_t resultMaxSize,
            command_cb _hidl_cb)  override;
    Return<Result> setParameter(
            const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value)  override;
    Return<void> getParameter(
            const hidl_vec<uint8_t>& parameter,
            uint32_t valueMaxSize,
            getParameter_cb _hidl_cb)  override;
    Return<void> getSupportedConfigsForFeature(
            uint32_t featureId,
            uint32_t maxConfigs,
            uint32_t configSize,
            getSupportedConfigsForFeature_cb _hidl_cb)  override;
    Return<void> getCurrentConfigForFeature(
            uint32_t featureId,
            uint32_t configSize,
            getCurrentConfigForFeature_cb _hidl_cb)  override;
    Return<Result> setCurrentConfigForFeature(
            uint32_t featureId, const hidl_vec<uint8_t>& configData)  override;
    Return<Result> close()  override;

    // Utility methods for extending interfaces.
    template<typename T> Return<void> getIntegerParam(
            uint32_t paramId, std::function<void(Result retval, T paramValue)> cb) {
        T value;
        Result retval = getParameterImpl(
                sizeof(uint32_t), &paramId,
                sizeof(T),
                [&] (uint32_t valueSize, const void* valueData) {
                    if (valueSize > sizeof(T)) valueSize = sizeof(T);
                    memcpy(&value, valueData, valueSize);
                });
        cb(retval, value);
        return Void();
    }

    template<typename T> Result getParam(uint32_t paramId, T& paramValue) {
        return getParameterImpl(
                sizeof(uint32_t), &paramId,
                sizeof(T),
                [&] (uint32_t valueSize, const void* valueData) {
                    if (valueSize > sizeof(T)) valueSize = sizeof(T);
                    memcpy(&paramValue, valueData, valueSize);
                });
    }

    template<typename T> Result getParam(uint32_t paramId, uint32_t paramArg, T& paramValue) {
        uint32_t params[2] = { paramId, paramArg };
        return getParameterImpl(
                sizeof(params), params,
                sizeof(T),
                [&] (uint32_t valueSize, const void* valueData) {
                    if (valueSize > sizeof(T)) valueSize = sizeof(T);
                    memcpy(&paramValue, valueData, valueSize);
                });
    }

    template<typename T> Result setParam(uint32_t paramId, const T& paramValue) {
        return setParameterImpl(sizeof(uint32_t), &paramId, sizeof(T), &paramValue);
    }

    template<typename T> Result setParam(uint32_t paramId, uint32_t paramArg, const T& paramValue) {
        uint32_t params[2] = { paramId, paramArg };
        return setParameterImpl(sizeof(params), params, sizeof(T), &paramValue);
    }

    Result getParameterImpl(
            uint32_t paramSize,
            const void* paramData,
            uint32_t valueSize,
            GetParameterSuccessCallback onSuccess) {
        return getParameterImpl(paramSize, paramData, valueSize, valueSize, onSuccess);
    }
    Result getParameterImpl(
            uint32_t paramSize,
            const void* paramData,
            uint32_t requestValueSize,
            uint32_t replyValueSize,
            GetParameterSuccessCallback onSuccess);
    Result setParameterImpl(
            uint32_t paramSize, const void* paramData, uint32_t valueSize, const void* valueData);

  private:
    friend struct VirtualizerEffect;  // for getParameterImpl
    friend struct VisualizerEffect;   // to allow executing commands

    using CommandSuccessCallback = std::function<void()>;
    using GetConfigCallback = std::function<void(Result retval, const EffectConfig& config)>;
    using GetCurrentConfigSuccessCallback = std::function<void(void* configData)>;
    using GetSupportedConfigsSuccessCallback =
            std::function<void(uint32_t supportedConfigs, void* configsData)>;

    static const char *sContextResultOfCommand;
    static const char *sContextCallToCommand;
    static const char *sContextCallFunction;

    bool mIsClosed;
    effect_handle_t mHandle;
    sp<AudioBufferWrapper> mInBuffer;
    sp<AudioBufferWrapper> mOutBuffer;
    std::atomic<audio_buffer_t*> mHalInBufferPtr;
    std::atomic<audio_buffer_t*> mHalOutBufferPtr;
    std::unique_ptr<StatusMQ> mStatusMQ;
    EventFlag* mEfGroup;
    std::atomic<bool> mStopProcessThread;
    sp<Thread> mProcessThread;

    virtual ~Effect();

    template<typename T> static size_t alignedSizeIn(size_t s);
    template<typename T> std::unique_ptr<uint8_t[]> hidlVecToHal(
            const hidl_vec<T>& vec, uint32_t* halDataSize);
    static void effectAuxChannelsConfigFromHal(
            const channel_config_t& halConfig, EffectAuxChannelsConfig* config);
    static void effectAuxChannelsConfigToHal(
            const EffectAuxChannelsConfig& config, channel_config_t* halConfig);
    static void effectBufferConfigFromHal(
            const buffer_config_t& halConfig, EffectBufferConfig* config);
    static void effectBufferConfigToHal(
            const EffectBufferConfig& config, buffer_config_t* halConfig);
    static void effectConfigFromHal(const effect_config_t& halConfig, EffectConfig* config);
    static void effectConfigToHal(const EffectConfig& config, effect_config_t* halConfig);
    static void effectOffloadParamToHal(
            const EffectOffloadParameter& offload, effect_offload_param_t* halOffload);
    static std::vector<uint8_t> parameterToHal(
            uint32_t paramSize, const void* paramData, uint32_t valueSize, const void** valueData);

    Result analyzeCommandStatus(
            const char* commandName, const char* context, status_t status);
    Result analyzeStatus(
            const char* funcName,
            const char* subFuncName,
            const char* contextDescription,
            status_t status);
    void getConfigImpl(int commandCode, const char* commandName, GetConfigCallback cb);
    Result getCurrentConfigImpl(
            uint32_t featureId, uint32_t configSize, GetCurrentConfigSuccessCallback onSuccess);
    Result getSupportedConfigsImpl(
            uint32_t featureId,
            uint32_t maxConfigs,
            uint32_t configSize,
            GetSupportedConfigsSuccessCallback onSuccess);
    Result sendCommand(int commandCode, const char* commandName);
    Result sendCommand(int commandCode, const char* commandName, uint32_t size, void* data);
    Result sendCommandReturningData(
            int commandCode, const char* commandName, uint32_t* replySize, void* replyData);
    Result sendCommandReturningData(
            int commandCode, const char* commandName,
            uint32_t size, void* data,
            uint32_t* replySize, void* replyData);
    Result sendCommandReturningStatus(int commandCode, const char* commandName);
    Result sendCommandReturningStatus(
            int commandCode, const char* commandName, uint32_t size, void* data);
    Result sendCommandReturningStatusAndData(
            int commandCode, const char* commandName,
            uint32_t size, void* data,
            uint32_t* replySize, void* replyData,
            uint32_t minReplySize,
            CommandSuccessCallback onSuccess);
    Result setConfigImpl(
            int commandCode, const char* commandName,
            const EffectConfig& config,
            const sp<IEffectBufferProviderCallback>& inputBufferProvider,
            const sp<IEffectBufferProviderCallback>& outputBufferProvider);
};

}  // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_EFFECT_H
