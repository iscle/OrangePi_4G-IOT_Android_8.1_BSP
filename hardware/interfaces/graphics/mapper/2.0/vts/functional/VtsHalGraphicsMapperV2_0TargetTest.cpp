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

#define LOG_TAG "VtsHalGraphicsMapperV2_0TargetTest"

#include <VtsHalHidlTargetTestBase.h>
#include <android-base/logging.h>
#include <sync/sync.h>
#include "VtsHalGraphicsMapperTestUtils.h"

namespace android {
namespace hardware {
namespace graphics {
namespace mapper {
namespace V2_0 {
namespace tests {
namespace {

using android::hardware::graphics::common::V1_0::BufferUsage;
using android::hardware::graphics::common::V1_0::PixelFormat;

class GraphicsMapperHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 protected:
  void SetUp() override {
      ASSERT_NO_FATAL_FAILURE(mGralloc = std::make_unique<Gralloc>());

      mDummyDescriptorInfo.width = 64;
      mDummyDescriptorInfo.height = 64;
      mDummyDescriptorInfo.layerCount = 1;
      mDummyDescriptorInfo.format = PixelFormat::RGBA_8888;
      mDummyDescriptorInfo.usage = static_cast<uint64_t>(
          BufferUsage::CPU_WRITE_OFTEN | BufferUsage::CPU_READ_OFTEN);
  }

  void TearDown() override {}

  std::unique_ptr<Gralloc> mGralloc;
  IMapper::BufferDescriptorInfo mDummyDescriptorInfo{};
};

/**
 * Test IAllocator::dumpDebugInfo by calling it.
 */
TEST_F(GraphicsMapperHidlTest, AllocatorDumpDebugInfo) {
    mGralloc->dumpDebugInfo();
}

/**
 * Test IAllocator::allocate with valid buffer descriptors.
 */
TEST_F(GraphicsMapperHidlTest, AllocatorAllocate) {
    BufferDescriptor descriptor;
    ASSERT_NO_FATAL_FAILURE(
        descriptor = mGralloc->createDescriptor(mDummyDescriptorInfo));

    for (uint32_t count = 0; count < 5; count++) {
        std::vector<const native_handle_t*> bufferHandles;
        uint32_t stride;
        ASSERT_NO_FATAL_FAILURE(bufferHandles = mGralloc->allocate(
                                    descriptor, count, false, &stride));

        if (count >= 1) {
            EXPECT_LE(mDummyDescriptorInfo.width, stride)
                << "invalid buffer stride";
        }

        for (auto bufferHandle : bufferHandles) {
            mGralloc->freeBuffer(bufferHandle);
        }
    }
}

/**
 * Test IAllocator::allocate with invalid buffer descriptors.
 */
TEST_F(GraphicsMapperHidlTest, AllocatorAllocateNegative) {
    // this assumes any valid descriptor is non-empty
    BufferDescriptor descriptor;
    mGralloc->getAllocator()->allocate(
        descriptor, 1, [&](const auto& tmpError, const auto&, const auto&) {
            EXPECT_EQ(Error::BAD_DESCRIPTOR, tmpError);
        });
}

/**
 * Test IAllocator::allocate does not leak.
 */
TEST_F(GraphicsMapperHidlTest, AllocatorAllocateNoLeak) {
    auto info = mDummyDescriptorInfo;
    info.width = 1024;
    info.height = 1024;

    for (int i = 0; i < 2048; i++) {
        auto bufferHandle = mGralloc->allocate(info, false);
        mGralloc->freeBuffer(bufferHandle);
    }
}

/**
 * Test IMapper::createDescriptor with valid descriptor info.
 */
TEST_F(GraphicsMapperHidlTest, CreateDescriptorBasic) {
    ASSERT_NO_FATAL_FAILURE(mGralloc->createDescriptor(mDummyDescriptorInfo));
}

/**
 * Test IMapper::createDescriptor with invalid descriptor info.
 */
TEST_F(GraphicsMapperHidlTest, CreateDescriptorNegative) {
    auto info = mDummyDescriptorInfo;
    info.width = 0;
    mGralloc->getMapper()->createDescriptor(
        info, [&](const auto& tmpError, const auto&) {
            EXPECT_EQ(Error::BAD_VALUE, tmpError)
                << "createDescriptor did not fail with BAD_VALUE";
        });
}

/**
 * Test IMapper::importBuffer and IMapper::freeBuffer with allocated buffers.
 */
TEST_F(GraphicsMapperHidlTest, ImportFreeBufferBasic) {
    const native_handle_t* bufferHandle;
    ASSERT_NO_FATAL_FAILURE(bufferHandle =
                                mGralloc->allocate(mDummyDescriptorInfo, true));
    ASSERT_NO_FATAL_FAILURE(mGralloc->freeBuffer(bufferHandle));
}

/**
 * Test IMapper::importBuffer and IMapper::freeBuffer with cloned buffers.
 */
TEST_F(GraphicsMapperHidlTest, ImportFreeBufferClone) {
    const native_handle_t* clonedBufferHandle;
    ASSERT_NO_FATAL_FAILURE(
        clonedBufferHandle = mGralloc->allocate(mDummyDescriptorInfo, false));

    // A cloned handle is a raw handle. Check that we can import it multiple
    // times.
    const native_handle_t* importedBufferHandles[2];
    ASSERT_NO_FATAL_FAILURE(importedBufferHandles[0] =
                                mGralloc->importBuffer(clonedBufferHandle));
    ASSERT_NO_FATAL_FAILURE(importedBufferHandles[1] =
                                mGralloc->importBuffer(clonedBufferHandle));
    ASSERT_NO_FATAL_FAILURE(mGralloc->freeBuffer(importedBufferHandles[0]));
    ASSERT_NO_FATAL_FAILURE(mGralloc->freeBuffer(importedBufferHandles[1]));

    ASSERT_NO_FATAL_FAILURE(mGralloc->freeBuffer(clonedBufferHandle));
}

/**
 * Test IMapper::importBuffer and IMapper::freeBuffer cross mapper instances.
 */
TEST_F(GraphicsMapperHidlTest, ImportFreeBufferSingleton) {
    const native_handle_t* rawHandle;
    ASSERT_NO_FATAL_FAILURE(
        rawHandle = mGralloc->allocate(mDummyDescriptorInfo, false));

    native_handle_t* importedHandle = nullptr;
    mGralloc->getMapper()->importBuffer(
        rawHandle, [&](const auto& tmpError, const auto& buffer) {
            ASSERT_EQ(Error::NONE, tmpError);
            importedHandle = static_cast<native_handle_t*>(buffer);
        });

    // free the imported handle with another mapper
    std::unique_ptr<Gralloc> anotherGralloc;
    ASSERT_NO_FATAL_FAILURE(anotherGralloc = std::make_unique<Gralloc>());
    Error error = mGralloc->getMapper()->freeBuffer(importedHandle);
    ASSERT_EQ(Error::NONE, error);

    ASSERT_NO_FATAL_FAILURE(mGralloc->freeBuffer(rawHandle));
}

/**
 * Test IMapper::importBuffer and IMapper::freeBuffer do not leak.
 */
TEST_F(GraphicsMapperHidlTest, ImportFreeBufferNoLeak) {
    auto info = mDummyDescriptorInfo;
    info.width = 1024;
    info.height = 1024;

    for (int i = 0; i < 2048; i++) {
        auto bufferHandle = mGralloc->allocate(info, true);
        mGralloc->freeBuffer(bufferHandle);
    }
}

/**
 * Test IMapper::importBuffer with invalid buffers.
 */
TEST_F(GraphicsMapperHidlTest, ImportBufferNegative) {
    native_handle_t* invalidHandle = nullptr;
    mGralloc->getMapper()->importBuffer(
        invalidHandle, [&](const auto& tmpError, const auto&) {
            EXPECT_EQ(Error::BAD_BUFFER, tmpError)
                << "importBuffer with nullptr did not fail with BAD_BUFFER";
        });

    invalidHandle = native_handle_create(0, 0);
    mGralloc->getMapper()->importBuffer(invalidHandle, [&](const auto& tmpError,
                                                           const auto&) {
        EXPECT_EQ(Error::BAD_BUFFER, tmpError)
            << "importBuffer with invalid handle did not fail with BAD_BUFFER";
    });
    native_handle_delete(invalidHandle);
}

/**
 * Test IMapper::freeBuffer with invalid buffers.
 */
TEST_F(GraphicsMapperHidlTest, FreeBufferNegative) {
    native_handle_t* invalidHandle = nullptr;
    Error error = mGralloc->getMapper()->freeBuffer(invalidHandle);
    EXPECT_EQ(Error::BAD_BUFFER, error)
        << "freeBuffer with nullptr did not fail with BAD_BUFFER";

    invalidHandle = native_handle_create(0, 0);
    error = mGralloc->getMapper()->freeBuffer(invalidHandle);
    EXPECT_EQ(Error::BAD_BUFFER, error)
        << "freeBuffer with invalid handle did not fail with BAD_BUFFER";
    native_handle_delete(invalidHandle);

    const native_handle_t* clonedBufferHandle;
    ASSERT_NO_FATAL_FAILURE(
        clonedBufferHandle = mGralloc->allocate(mDummyDescriptorInfo, false));
    error = mGralloc->getMapper()->freeBuffer(invalidHandle);
    EXPECT_EQ(Error::BAD_BUFFER, error)
        << "freeBuffer with un-imported handle did not fail with BAD_BUFFER";

    mGralloc->freeBuffer(clonedBufferHandle);
}

/**
 * Test IMapper::lock and IMapper::unlock.
 */
TEST_F(GraphicsMapperHidlTest, LockUnlockBasic) {
    const auto& info = mDummyDescriptorInfo;

    const native_handle_t* bufferHandle;
    uint32_t stride;
    ASSERT_NO_FATAL_FAILURE(bufferHandle =
                                mGralloc->allocate(info, true, &stride));

    // lock buffer for writing
    const IMapper::Rect region{0, 0, static_cast<int32_t>(info.width),
                               static_cast<int32_t>(info.height)};
    int fence = -1;
    uint8_t* data;
    ASSERT_NO_FATAL_FAILURE(data = static_cast<uint8_t*>(mGralloc->lock(
                                bufferHandle, info.usage, region, fence)));

    // RGBA_8888
    size_t strideInBytes = stride * 4;
    size_t writeInBytes = info.width * 4;

    for (uint32_t y = 0; y < info.height; y++) {
        memset(data, y, writeInBytes);
        data += strideInBytes;
    }

    ASSERT_NO_FATAL_FAILURE(fence = mGralloc->unlock(bufferHandle));

    // lock again for reading
    ASSERT_NO_FATAL_FAILURE(data = static_cast<uint8_t*>(mGralloc->lock(
                                bufferHandle, info.usage, region, fence)));
    for (uint32_t y = 0; y < info.height; y++) {
        for (size_t i = 0; i < writeInBytes; i++) {
            EXPECT_EQ(static_cast<uint8_t>(y), data[i]);
        }
        data += strideInBytes;
    }

    ASSERT_NO_FATAL_FAILURE(fence = mGralloc->unlock(bufferHandle));
    if (fence >= 0) {
        close(fence);
    }
}

/**
 * Test IMapper::lockYCbCr.  This locks a YV12 buffer, and makes sure we can
 * write to and read from it.
 */
TEST_F(GraphicsMapperHidlTest, LockYCbCrBasic) {
    auto info = mDummyDescriptorInfo;
    info.format = PixelFormat::YV12;

    const native_handle_t* bufferHandle;
    uint32_t stride;
    ASSERT_NO_FATAL_FAILURE(bufferHandle =
                                mGralloc->allocate(info, true, &stride));

    // lock buffer for writing
    const IMapper::Rect region{0, 0, static_cast<int32_t>(info.width),
                               static_cast<int32_t>(info.height)};
    int fence = -1;
    YCbCrLayout layout;
    ASSERT_NO_FATAL_FAILURE(
        layout = mGralloc->lockYCbCr(bufferHandle, info.usage, region, fence));

    auto yData = static_cast<uint8_t*>(layout.y);
    auto cbData = static_cast<uint8_t*>(layout.cb);
    auto crData = static_cast<uint8_t*>(layout.cr);
    for (uint32_t y = 0; y < info.height; y++) {
        for (uint32_t x = 0; x < info.width; x++) {
            auto val = static_cast<uint8_t>(info.height * y + x);

            yData[layout.yStride * y + x] = val;
            if (y % 2 == 0 && x % 2 == 0) {
                cbData[layout.cStride * y / 2 + x / 2] = val;
                crData[layout.cStride * y / 2 + x / 2] = val;
            }
        }
    }

    ASSERT_NO_FATAL_FAILURE(fence = mGralloc->unlock(bufferHandle));

    // lock again for reading
    ASSERT_NO_FATAL_FAILURE(
        layout = mGralloc->lockYCbCr(bufferHandle, info.usage, region, fence));

    yData = static_cast<uint8_t*>(layout.y);
    cbData = static_cast<uint8_t*>(layout.cb);
    crData = static_cast<uint8_t*>(layout.cr);
    for (uint32_t y = 0; y < info.height; y++) {
        for (uint32_t x = 0; x < info.width; x++) {
            auto val = static_cast<uint8_t>(info.height * y + x);

            EXPECT_EQ(val, yData[layout.yStride * y + x]);
            if (y % 2 == 0 && x % 2 == 0) {
                EXPECT_EQ(val, cbData[layout.cStride * y / 2 + x / 2]);
                EXPECT_EQ(val, crData[layout.cStride * y / 2 + x / 2]);
            }
        }
    }

    ASSERT_NO_FATAL_FAILURE(fence = mGralloc->unlock(bufferHandle));
    if (fence >= 0) {
        close(fence);
    }
}

/**
 * Test IMapper::unlock with invalid buffers.
 */
TEST_F(GraphicsMapperHidlTest, UnlockNegative) {
    native_handle_t* invalidHandle = nullptr;
    mGralloc->getMapper()->unlock(
        invalidHandle, [&](const auto& tmpError, const auto&) {
            EXPECT_EQ(Error::BAD_BUFFER, tmpError)
                << "unlock with nullptr did not fail with BAD_BUFFER";
        });

    invalidHandle = native_handle_create(0, 0);
    mGralloc->getMapper()->unlock(
        invalidHandle, [&](const auto& tmpError, const auto&) {
            EXPECT_EQ(Error::BAD_BUFFER, tmpError)
                << "unlock with invalid handle did not fail with BAD_BUFFER";
        });
    native_handle_delete(invalidHandle);

    ASSERT_NO_FATAL_FAILURE(invalidHandle =
                                const_cast<native_handle_t*>(mGralloc->allocate(
                                    mDummyDescriptorInfo, false)));
    mGralloc->getMapper()->unlock(invalidHandle, [&](const auto& tmpError,
                                                     const auto&) {
        EXPECT_EQ(Error::BAD_BUFFER, tmpError)
            << "unlock with un-imported handle did not fail with BAD_BUFFER";
    });
    mGralloc->freeBuffer(invalidHandle);

// disabled as it fails on many existing drivers
#if 0
  ASSERT_NO_FATAL_FAILURE(invalidHandle = const_cast<native_handle_t*>(
                              mGralloc->allocate(mDummyDescriptorInfo, true)));
  mGralloc->getMapper()->unlock(
      invalidHandle, [&](const auto& tmpError, const auto&) {
        EXPECT_EQ(Error::BAD_BUFFER, tmpError)
            << "unlock with unlocked handle did not fail with BAD_BUFFER";
      });
  mGralloc->freeBuffer(invalidHandle);
#endif
}

}  // namespace
}  // namespace tests
}  // namespace V2_0
}  // namespace mapper
}  // namespace graphics
}  // namespace hardware
}  // namespace android

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);

  int status = RUN_ALL_TESTS();
  LOG(INFO) << "Test result = " << status;

  return status;
}
