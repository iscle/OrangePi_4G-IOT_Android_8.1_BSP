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

#define LOG_TAG "HandleImporter"
#include "HandleImporter.h"
#include <log/log.h>

namespace android {
namespace hardware {
namespace camera {
namespace common {
namespace V1_0 {
namespace helper {

using MapperError = android::hardware::graphics::mapper::V2_0::Error;

HandleImporter::HandleImporter() : mInitialized(false) {}

void HandleImporter::initializeLocked() {
    if (mInitialized) {
        return;
    }

    mMapper = IMapper::getService();
    if (mMapper == nullptr) {
        ALOGE("%s: cannnot acccess graphics mapper HAL!", __FUNCTION__);
        return;
    }

    mInitialized = true;
    return;
}

void HandleImporter::cleanup() {
    mMapper.clear();
    mInitialized = false;
}

// In IComposer, any buffer_handle_t is owned by the caller and we need to
// make a clone for hwcomposer2.  We also need to translate empty handle
// to nullptr.  This function does that, in-place.
bool HandleImporter::importBuffer(buffer_handle_t& handle) {
    if (!handle->numFds && !handle->numInts) {
        handle = nullptr;
        return true;
    }

    Mutex::Autolock lock(mLock);
    if (!mInitialized) {
        initializeLocked();
    }

    if (mMapper == nullptr) {
        ALOGE("%s: mMapper is null!", __FUNCTION__);
        return false;
    }

    MapperError error;
    buffer_handle_t importedHandle;
    auto ret = mMapper->importBuffer(
        hidl_handle(handle),
        [&](const auto& tmpError, const auto& tmpBufferHandle) {
            error = tmpError;
            importedHandle = static_cast<buffer_handle_t>(tmpBufferHandle);
        });

    if (!ret.isOk()) {
        ALOGE("%s: mapper importBuffer failed: %s",
                __FUNCTION__, ret.description().c_str());
        return false;
    }

    if (error != MapperError::NONE) {
        return false;
    }

    handle = importedHandle;

    return true;
}

void HandleImporter::freeBuffer(buffer_handle_t handle) {
    if (!handle) {
        return;
    }

    Mutex::Autolock lock(mLock);
    if (mMapper == nullptr) {
        ALOGE("%s: mMapper is null!", __FUNCTION__);
        return;
    }

    auto ret = mMapper->freeBuffer(const_cast<native_handle_t*>(handle));
    if (!ret.isOk()) {
        ALOGE("%s: mapper freeBuffer failed: %s",
                __FUNCTION__, ret.description().c_str());
    }
}

bool HandleImporter::importFence(const native_handle_t* handle, int& fd) const {
    if (handle == nullptr || handle->numFds == 0) {
        fd = -1;
    } else if (handle->numFds == 1) {
        fd = dup(handle->data[0]);
        if (fd < 0) {
            ALOGE("failed to dup fence fd %d", handle->data[0]);
            return false;
        }
    } else {
        ALOGE("invalid fence handle with %d file descriptors",
                handle->numFds);
        return false;
    }

    return true;
}

void HandleImporter::closeFence(int fd) const {
    if (fd >= 0) {
        close(fd);
    }
}

} // namespace helper
} // namespace V1_0
} // namespace common
} // namespace camera
} // namespace hardware
} // namespace android
