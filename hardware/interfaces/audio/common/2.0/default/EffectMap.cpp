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

#include <atomic>

#include "EffectMap.h"

namespace android {

ANDROID_SINGLETON_STATIC_INSTANCE(EffectMap);

// static
const uint64_t EffectMap::INVALID_ID = 0;

// static
uint64_t EffectMap::makeUniqueId() {
    static std::atomic<uint64_t> counter{INVALID_ID + 1};
    return counter++;
}

uint64_t EffectMap::add(effect_handle_t handle) {
    uint64_t newId = makeUniqueId();
    std::lock_guard<std::mutex> lock(mLock);
    mEffects.add(newId, handle);
    return newId;
}

effect_handle_t EffectMap::get(const uint64_t& id) {
    std::lock_guard<std::mutex> lock(mLock);
    ssize_t idx = mEffects.indexOfKey(id);
    return idx >= 0 ? mEffects[idx] : NULL;
}

void EffectMap::remove(effect_handle_t handle) {
    std::lock_guard<std::mutex> lock(mLock);
    for (size_t i = 0; i < mEffects.size(); ++i) {
        if (mEffects[i] == handle) {
            mEffects.removeItemsAt(i);
            break;
        }
    }
}

}  // namespace android
