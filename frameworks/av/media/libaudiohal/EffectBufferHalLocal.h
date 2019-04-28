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

#ifndef ANDROID_HARDWARE_EFFECT_BUFFER_HAL_LOCAL_H
#define ANDROID_HARDWARE_EFFECT_BUFFER_HAL_LOCAL_H

#include <memory>

#include <media/audiohal/EffectBufferHalInterface.h>
#include <system/audio_effect.h>

namespace android {

class EffectBufferHalLocal : public EffectBufferHalInterface
{
  public:
    virtual audio_buffer_t* audioBuffer();
    virtual void* externalData() const;

    virtual void setExternalData(void* external);
    virtual void setFrameCount(size_t frameCount);
    virtual bool checkFrameCountChange();

    virtual void update();
    virtual void commit();
    virtual void update(size_t size);
    virtual void commit(size_t size);

  private:
    friend class EffectBufferHalInterface;

    std::unique_ptr<uint8_t[]> mOwnBuffer;
    const size_t mBufferSize;
    bool mFrameCountChanged;
    audio_buffer_t mAudioBuffer;

    // Can not be constructed directly by clients.
    explicit EffectBufferHalLocal(size_t size);
    EffectBufferHalLocal(void* external, size_t size);

    virtual ~EffectBufferHalLocal();

    status_t init();
};

} // namespace android

#endif // ANDROID_HARDWARE_EFFECT_BUFFER_HAL_LOCAL_H
