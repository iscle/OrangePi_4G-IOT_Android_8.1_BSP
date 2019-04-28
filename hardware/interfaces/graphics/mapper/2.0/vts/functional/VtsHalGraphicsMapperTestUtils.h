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

#ifndef VTS_HAL_GRAPHICS_MAPPER_UTILS
#define VTS_HAL_GRAPHICS_MAPPER_UTILS

#include <unordered_set>

#include <android/hardware/graphics/allocator/2.0/IAllocator.h>
#include <android/hardware/graphics/mapper/2.0/IMapper.h>
#include <utils/StrongPointer.h>

namespace android {
namespace hardware {
namespace graphics {
namespace mapper {
namespace V2_0 {
namespace tests {

using android::hardware::graphics::allocator::V2_0::IAllocator;

// A wrapper to IAllocator and IMapper.
class Gralloc {
   public:
    Gralloc();
    ~Gralloc();

    // IAllocator methods

    sp<IAllocator> getAllocator() const;

    std::string dumpDebugInfo();

    // When import is false, this simply calls IAllocator::allocate. When import
    // is true, the returned buffers are also imported into the mapper.
    //
    // Either case, the returned buffers must be freed with freeBuffer.
    std::vector<const native_handle_t*> allocate(
        const BufferDescriptor& descriptor, uint32_t count, bool import = true,
        uint32_t* outStride = nullptr);
    const native_handle_t* allocate(
        const IMapper::BufferDescriptorInfo& descriptorInfo, bool import = true,
        uint32_t* outStride = nullptr);

    // IMapper methods

    sp<IMapper> getMapper() const;

    BufferDescriptor createDescriptor(
        const IMapper::BufferDescriptorInfo& descriptorInfo);

    const native_handle_t* importBuffer(const hidl_handle& rawHandle);
    void freeBuffer(const native_handle_t* bufferHandle);

    // We use fd instead of hidl_handle in these functions to pass fences
    // in and out of the mapper.  The ownership of the fd is always transferred
    // with each of these functions.
    void* lock(const native_handle_t* bufferHandle, uint64_t cpuUsage,
               const IMapper::Rect& accessRegion, int acquireFence);
    YCbCrLayout lockYCbCr(const native_handle_t* bufferHandle,
                          uint64_t cpuUsage, const IMapper::Rect& accessRegion,
                          int acquireFence);
    int unlock(const native_handle_t* bufferHandle);

   private:
    void init();
    const native_handle_t* cloneBuffer(const hidl_handle& rawHandle);

    sp<IAllocator> mAllocator;
    sp<IMapper> mMapper;

    // Keep track of all cloned and imported handles.  When a test fails with
    // ASSERT_*, the destructor will free the handles for the test.
    std::unordered_set<const native_handle_t*> mClonedBuffers;
    std::unordered_set<const native_handle_t*> mImportedBuffers;
};

}  // namespace tests
}  // namespace V2_0
}  // namespace mapper
}  // namespace graphics
}  // namespace hardware
}  // namespace android

#endif  // VTS_HAL_GRAPHICS_MAPPER_UTILS
