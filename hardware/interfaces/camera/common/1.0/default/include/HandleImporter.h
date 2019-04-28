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

#ifndef CAMERA_COMMON_1_0_HANDLEIMPORTED_H
#define CAMERA_COMMON_1_0_HANDLEIMPORTED_H

#include <utils/Mutex.h>
#include <android/hardware/graphics/mapper/2.0/IMapper.h>
#include <cutils/native_handle.h>

using android::hardware::graphics::mapper::V2_0::IMapper;

namespace android {
namespace hardware {
namespace camera {
namespace common {
namespace V1_0 {
namespace helper {

// Borrowed from graphics HAL. Use this until gralloc mapper HAL is working
class HandleImporter {
public:
    HandleImporter();

    // In IComposer, any buffer_handle_t is owned by the caller and we need to
    // make a clone for hwcomposer2.  We also need to translate empty handle
    // to nullptr.  This function does that, in-place.
    bool importBuffer(buffer_handle_t& handle);
    void freeBuffer(buffer_handle_t handle);
    bool importFence(const native_handle_t* handle, int& fd) const;
    void closeFence(int fd) const;

private:
    void initializeLocked();
    void cleanup();

    Mutex mLock;
    bool mInitialized;
    sp<IMapper> mMapper;

};

} // namespace helper
} // namespace V1_0
} // namespace common
} // namespace camera
} // namespace hardware
} // namespace android

#endif // CAMERA_COMMON_1_0_HANDLEIMPORTED_H