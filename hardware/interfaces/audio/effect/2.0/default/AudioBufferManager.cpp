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

#include <atomic>

#include <hidlmemory/mapping.h>

#include "AudioBufferManager.h"

namespace android {

ANDROID_SINGLETON_STATIC_INSTANCE(AudioBufferManager);

bool AudioBufferManager::wrap(const AudioBuffer& buffer, sp<AudioBufferWrapper>* wrapper) {
    // Check if we have this buffer already
    std::lock_guard<std::mutex> lock(mLock);
    ssize_t idx = mBuffers.indexOfKey(buffer.id);
    if (idx >= 0) {
        *wrapper = mBuffers[idx].promote();
        if (*wrapper != nullptr) {
            (*wrapper)->getHalBuffer()->frameCount = buffer.frameCount;
            return true;
        }
        mBuffers.removeItemsAt(idx);
    }
    // Need to create and init a new AudioBufferWrapper.
    sp<AudioBufferWrapper> tempBuffer(new AudioBufferWrapper(buffer));
    if (!tempBuffer->init()) return false;
    *wrapper = tempBuffer;
    mBuffers.add(buffer.id, *wrapper);
    return true;
}

void AudioBufferManager::removeEntry(uint64_t id) {
    std::lock_guard<std::mutex> lock(mLock);
    ssize_t idx = mBuffers.indexOfKey(id);
    if (idx >= 0) mBuffers.removeItemsAt(idx);
}

namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

AudioBufferWrapper::AudioBufferWrapper(const AudioBuffer& buffer) :
        mHidlBuffer(buffer), mHalBuffer{ 0, { nullptr } } {
}

AudioBufferWrapper::~AudioBufferWrapper() {
    AudioBufferManager::getInstance().removeEntry(mHidlBuffer.id);
}

bool AudioBufferWrapper::init() {
    if (mHalBuffer.raw != nullptr) {
        ALOGE("An attempt to init AudioBufferWrapper twice");
        return false;
    }
    mHidlMemory = mapMemory(mHidlBuffer.data);
    if (mHidlMemory == nullptr) {
        ALOGE("Could not map HIDL memory to IMemory");
        return false;
    }
    mHalBuffer.raw = static_cast<void*>(mHidlMemory->getPointer());
    if (mHalBuffer.raw == nullptr) {
        ALOGE("IMemory buffer pointer is null");
        return false;
    }
    mHalBuffer.frameCount = mHidlBuffer.frameCount;
    return true;
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
