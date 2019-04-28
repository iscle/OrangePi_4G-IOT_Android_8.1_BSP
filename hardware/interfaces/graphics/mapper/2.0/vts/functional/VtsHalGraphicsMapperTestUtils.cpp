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

#include <VtsHalHidlTargetTestBase.h>

#include "VtsHalGraphicsMapperTestUtils.h"

namespace android {
namespace hardware {
namespace graphics {
namespace mapper {
namespace V2_0 {
namespace tests {

Gralloc::Gralloc() {
    init();
}

void Gralloc::init() {
    mAllocator = ::testing::VtsHalHidlTargetTestBase::getService<IAllocator>();
    ASSERT_NE(nullptr, mAllocator.get()) << "failed to get allocator service";

    mMapper = ::testing::VtsHalHidlTargetTestBase::getService<IMapper>();
    ASSERT_NE(nullptr, mMapper.get()) << "failed to get mapper service";
    ASSERT_FALSE(mMapper->isRemote()) << "mapper is not in passthrough mode";
}

Gralloc::~Gralloc() {
    for (auto bufferHandle : mClonedBuffers) {
        auto buffer = const_cast<native_handle_t*>(bufferHandle);
        native_handle_close(buffer);
        native_handle_delete(buffer);
    }
    mClonedBuffers.clear();

    for (auto bufferHandle : mImportedBuffers) {
        auto buffer = const_cast<native_handle_t*>(bufferHandle);
        EXPECT_EQ(Error::NONE, mMapper->freeBuffer(buffer))
            << "failed to free buffer " << buffer;
    }
    mImportedBuffers.clear();
}

sp<IAllocator> Gralloc::getAllocator() const {
    return mAllocator;
}

std::string Gralloc::dumpDebugInfo() {
    std::string debugInfo;
    mAllocator->dumpDebugInfo(
        [&](const auto& tmpDebugInfo) { debugInfo = tmpDebugInfo.c_str(); });

    return debugInfo;
}

const native_handle_t* Gralloc::cloneBuffer(const hidl_handle& rawHandle) {
    const native_handle_t* bufferHandle =
        native_handle_clone(rawHandle.getNativeHandle());
    EXPECT_NE(nullptr, bufferHandle);

    if (bufferHandle) {
        mClonedBuffers.insert(bufferHandle);
    }

    return bufferHandle;
}

std::vector<const native_handle_t*> Gralloc::allocate(
    const BufferDescriptor& descriptor, uint32_t count, bool import,
    uint32_t* outStride) {
    std::vector<const native_handle_t*> bufferHandles;
    bufferHandles.reserve(count);
    mAllocator->allocate(
        descriptor, count, [&](const auto& tmpError, const auto& tmpStride,
                               const auto& tmpBuffers) {
            ASSERT_EQ(Error::NONE, tmpError) << "failed to allocate buffers";
            ASSERT_EQ(count, tmpBuffers.size()) << "invalid buffer array";

            for (uint32_t i = 0; i < count; i++) {
                if (import) {
                    ASSERT_NO_FATAL_FAILURE(
                        bufferHandles.push_back(importBuffer(tmpBuffers[i])));
                } else {
                    ASSERT_NO_FATAL_FAILURE(
                        bufferHandles.push_back(cloneBuffer(tmpBuffers[i])));
                }
            }

            if (outStride) {
                *outStride = tmpStride;
            }
        });

    if (::testing::Test::HasFatalFailure()) {
        bufferHandles.clear();
    }

    return bufferHandles;
}

const native_handle_t* Gralloc::allocate(
    const IMapper::BufferDescriptorInfo& descriptorInfo, bool import,
    uint32_t* outStride) {
    BufferDescriptor descriptor = createDescriptor(descriptorInfo);
    if (::testing::Test::HasFatalFailure()) {
        return nullptr;
    }

    auto buffers = allocate(descriptor, 1, import, outStride);
    if (::testing::Test::HasFatalFailure()) {
        return nullptr;
    }

    return buffers[0];
}

sp<IMapper> Gralloc::getMapper() const {
    return mMapper;
}

BufferDescriptor Gralloc::createDescriptor(
    const IMapper::BufferDescriptorInfo& descriptorInfo) {
    BufferDescriptor descriptor;
    mMapper->createDescriptor(
        descriptorInfo, [&](const auto& tmpError, const auto& tmpDescriptor) {
            ASSERT_EQ(Error::NONE, tmpError) << "failed to create descriptor";
            descriptor = tmpDescriptor;
        });

    return descriptor;
}

const native_handle_t* Gralloc::importBuffer(const hidl_handle& rawHandle) {
    const native_handle_t* bufferHandle = nullptr;
    mMapper->importBuffer(
        rawHandle, [&](const auto& tmpError, const auto& tmpBuffer) {
            ASSERT_EQ(Error::NONE, tmpError) << "failed to import buffer %p"
                                             << rawHandle.getNativeHandle();
            bufferHandle = static_cast<const native_handle_t*>(tmpBuffer);
        });

    if (bufferHandle) {
        mImportedBuffers.insert(bufferHandle);
    }

    return bufferHandle;
}

void Gralloc::freeBuffer(const native_handle_t* bufferHandle) {
    auto buffer = const_cast<native_handle_t*>(bufferHandle);

    if (mImportedBuffers.erase(bufferHandle)) {
        Error error = mMapper->freeBuffer(buffer);
        ASSERT_EQ(Error::NONE, error) << "failed to free buffer " << buffer;
    } else {
        mClonedBuffers.erase(bufferHandle);
        native_handle_close(buffer);
        native_handle_delete(buffer);
    }
}

void* Gralloc::lock(const native_handle_t* bufferHandle, uint64_t cpuUsage,
                    const IMapper::Rect& accessRegion, int acquireFence) {
    auto buffer = const_cast<native_handle_t*>(bufferHandle);

    NATIVE_HANDLE_DECLARE_STORAGE(acquireFenceStorage, 1, 0);
    hidl_handle acquireFenceHandle;
    if (acquireFence >= 0) {
        auto h = native_handle_init(acquireFenceStorage, 1, 0);
        h->data[0] = acquireFence;
        acquireFenceHandle = h;
    }

    void* data = nullptr;
    mMapper->lock(buffer, cpuUsage, accessRegion, acquireFenceHandle,
                  [&](const auto& tmpError, const auto& tmpData) {
                      ASSERT_EQ(Error::NONE, tmpError)
                          << "failed to lock buffer " << buffer;
                      data = tmpData;
                  });

    if (acquireFence >= 0) {
        close(acquireFence);
    }

    return data;
}

YCbCrLayout Gralloc::lockYCbCr(const native_handle_t* bufferHandle,
                               uint64_t cpuUsage,
                               const IMapper::Rect& accessRegion,
                               int acquireFence) {
    auto buffer = const_cast<native_handle_t*>(bufferHandle);

    NATIVE_HANDLE_DECLARE_STORAGE(acquireFenceStorage, 1, 0);
    hidl_handle acquireFenceHandle;
    if (acquireFence >= 0) {
        auto h = native_handle_init(acquireFenceStorage, 1, 0);
        h->data[0] = acquireFence;
        acquireFenceHandle = h;
    }

    YCbCrLayout layout = {};
    mMapper->lockYCbCr(buffer, cpuUsage, accessRegion, acquireFenceHandle,
                       [&](const auto& tmpError, const auto& tmpLayout) {
                           ASSERT_EQ(Error::NONE, tmpError)
                               << "failed to lockYCbCr buffer " << buffer;
                           layout = tmpLayout;
                       });

    if (acquireFence >= 0) {
        close(acquireFence);
    }

    return layout;
}

int Gralloc::unlock(const native_handle_t* bufferHandle) {
    auto buffer = const_cast<native_handle_t*>(bufferHandle);

    int releaseFence = -1;
    mMapper->unlock(
        buffer, [&](const auto& tmpError, const auto& tmpReleaseFence) {
            ASSERT_EQ(Error::NONE, tmpError) << "failed to unlock buffer "
                                             << buffer;

            auto fenceHandle = tmpReleaseFence.getNativeHandle();
            if (fenceHandle) {
                ASSERT_EQ(0, fenceHandle->numInts) << "invalid fence handle "
                                                   << fenceHandle;
                if (fenceHandle->numFds == 1) {
                    releaseFence = dup(fenceHandle->data[0]);
                    ASSERT_LT(0, releaseFence) << "failed to dup fence fd";
                } else {
                    ASSERT_EQ(0, fenceHandle->numFds)
                        << " invalid fence handle " << fenceHandle;
                }
            }
        });

    return releaseFence;
}

}  // namespace tests
}  // namespace V2_0
}  // namespace mapper
}  // namespace graphics
}  // namespace hardware
}  // namespace android
