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

#include "HWComposerBufferCache.h"

#include <gui/BufferQueue.h>

namespace android {

HWComposerBufferCache::HWComposerBufferCache()
{
    mBuffers.reserve(BufferQueue::NUM_BUFFER_SLOTS);
}

void HWComposerBufferCache::getHwcBuffer(int slot,
        const sp<GraphicBuffer>& buffer,
        uint32_t* outSlot, sp<GraphicBuffer>* outBuffer)
{
    if (slot == BufferQueue::INVALID_BUFFER_SLOT || slot < 0) {
        // default to slot 0
        slot = 0;
    }

    if (static_cast<size_t>(slot) >= mBuffers.size()) {
        mBuffers.resize(slot + 1);
    }

    *outSlot = slot;

    if (mBuffers[slot] == buffer) {
        // already cached in HWC, skip sending the buffer
        *outBuffer = nullptr;
    } else {
        *outBuffer = buffer;

        // update cache
        mBuffers[slot] = buffer;
    }
}

} // namespace android
