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

#ifndef android_hardware_audio_effect_V2_0_AudioBufferManager_H_
#define android_hardware_audio_effect_V2_0_AudioBufferManager_H_

#include <mutex>

#include <android/hardware/audio/effect/2.0/types.h>
#include <android/hidl/memory/1.0/IMemory.h>
#include <system/audio_effect.h>
#include <utils/RefBase.h>
#include <utils/KeyedVector.h>
#include <utils/Singleton.h>

using ::android::hardware::audio::effect::V2_0::AudioBuffer;
using ::android::hidl::memory::V1_0::IMemory;

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

class AudioBufferWrapper : public RefBase {
  public:
    explicit AudioBufferWrapper(const AudioBuffer& buffer);
    virtual ~AudioBufferWrapper();
    bool init();
    audio_buffer_t* getHalBuffer() { return &mHalBuffer; }
  private:
    AudioBufferWrapper(const AudioBufferWrapper&) = delete;
    void operator=(AudioBufferWrapper) = delete;

    AudioBuffer mHidlBuffer;
    sp<IMemory> mHidlMemory;
    audio_buffer_t mHalBuffer;
};

}  // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

using ::android::hardware::audio::effect::V2_0::implementation::AudioBufferWrapper;

namespace android {

// This class needs to be in 'android' ns because Singleton macros require that.
class AudioBufferManager : public Singleton<AudioBufferManager> {
  public:
    bool wrap(const AudioBuffer& buffer, sp<AudioBufferWrapper>* wrapper);

  private:
    friend class hardware::audio::effect::V2_0::implementation::AudioBufferWrapper;

    // Called by AudioBufferWrapper.
    void removeEntry(uint64_t id);

    std::mutex mLock;
    KeyedVector<uint64_t, wp<AudioBufferWrapper>> mBuffers;
};

}  // namespace android

#endif  // android_hardware_audio_effect_V2_0_AudioBufferManager_H_
