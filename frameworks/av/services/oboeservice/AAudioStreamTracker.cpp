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

#define LOG_TAG "AAudioStreamTracker"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <iomanip>
#include <iostream>
#include <sstream>

#include <aaudio/AAudio.h>
#include <utils/String16.h>

#include "AAudioStreamTracker.h"

using namespace android;
using namespace aaudio;

sp<AAudioServiceStreamBase> AAudioStreamTracker::removeStreamByHandle(
        aaudio_handle_t streamHandle) {
    std::lock_guard<std::mutex> lock(mHandleLock);
    sp<AAudioServiceStreamBase> serviceStream;
    auto it = mStreamsByHandle.find(streamHandle);
    if (it != mStreamsByHandle.end()) {
        serviceStream = it->second;
        mStreamsByHandle.erase(it);
    }
    return serviceStream;
}

sp<AAudioServiceStreamBase> AAudioStreamTracker::getStreamByHandle(
        aaudio_handle_t streamHandle) {
    std::lock_guard<std::mutex> lock(mHandleLock);
    sp<AAudioServiceStreamBase> serviceStream;
    auto it = mStreamsByHandle.find(streamHandle);
    if (it != mStreamsByHandle.end()) {
        serviceStream = it->second;
    }
    return serviceStream;
}

// advance to next legal handle value
__attribute__((no_sanitize("integer")))
aaudio_handle_t AAudioStreamTracker::bumpHandle(aaudio_handle_t handle) {
    handle++;
    // Only use positive integers.
    if (handle <= 0) {
        handle = 1;
    }
    return handle;
}

aaudio_handle_t AAudioStreamTracker::addStreamForHandle(sp<AAudioServiceStreamBase> serviceStream) {
    std::lock_guard<std::mutex> lock(mHandleLock);
    aaudio_handle_t handle = mPreviousHandle.load();
    // Assign a unique handle.
    while (true) {
        handle = bumpHandle(handle);
        sp<AAudioServiceStreamBase> oldServiceStream = mStreamsByHandle[handle];
        // Is this an unused handle? It would be extremely unlikely to wrap
        // around and collide with a very old handle. But just in case.
        if (oldServiceStream.get() == nullptr) {
            mStreamsByHandle[handle] = serviceStream;
            break;
        }
    }
    mPreviousHandle.store(handle);
    return handle;
}

std::string AAudioStreamTracker::dump() const {
    std::stringstream result;
    const bool isLocked = AAudio_tryUntilTrue(
            [this]()->bool { return mHandleLock.try_lock(); } /* f */,
            50 /* times */,
            20 /* sleepMs */);
    if (!isLocked) {
        result << "AAudioStreamTracker may be deadlocked\n";
    } else {
        result << "Stream Handles:\n";
        for (const auto&  it : mStreamsByHandle) {
            aaudio_handle_t handle = it.second->getHandle();
            result << "    0x" << std::setfill('0') << std::setw(8) << std::hex << handle
                   << std::dec << std::setfill(' ') << "\n";
        }

        mHandleLock.unlock();
    }
    return result.str();
}
