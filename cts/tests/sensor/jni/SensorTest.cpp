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

#include "SensorTest.h"
#include <errno.h>

namespace android {
namespace SensorTest {

// SensorTest container class
bool SensorTest::SetUp() {
    if (mManager == nullptr) {
        mManager.reset(
              TestSensorManager::getInstanceForPackage("android.hardware.cts.SensorNativeTest"));
    }
    return mManager == nullptr;
}

void SensorTest::TearDown() {
    if (mManager == nullptr) {
        mManager.reset(nullptr);
    }
}

TestSensorManager::TestSensorManager(const char *package) {
    mManager = ASensorManager_getInstanceForPackage(package);
}

TestSensorManager::~TestSensorManager() {
    for (int channel : mSensorDirectChannel) {
        destroyDirectChannel(channel);
    }
    mSensorDirectChannel.clear();
}

TestSensorManager * TestSensorManager::getInstanceForPackage(const char *package) {
    return new TestSensorManager(package);
}

TestSensor TestSensorManager::getDefaultSensor(int type) {
    return TestSensor(ASensorManager_getDefaultSensor(mManager, type));
}

int TestSensorManager::createDirectChannel(const TestSharedMemory &mem) {
    if (!isValid()) {
        return -EINVAL;
    }
    switch (mem.getType()) {
        case ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY:
            return createSharedMemoryDirectChannel(
                    mem.getSharedMemoryFd(), mem.getSize());
        case ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER:
            return createHardwareBufferDirectChannel(
                    mem.getHardwareBuffer(), mem.getSize());
        default:
            return -1;
    }
}

int TestSensorManager::createSharedMemoryDirectChannel(int fd, size_t size) {
    int ret = ASensorManager_createSharedMemoryDirectChannel(mManager, fd, size);
    if (ret > 0) {
        mSensorDirectChannel.insert(ret);
    }
    return ret;
}

int TestSensorManager::createHardwareBufferDirectChannel(
        AHardwareBuffer const *buffer, size_t size) {
    int ret = ASensorManager_createHardwareBufferDirectChannel(mManager, buffer, size);
    if (ret > 0) {
        mSensorDirectChannel.insert(ret);
    }
    return ret;
}

void TestSensorManager::destroyDirectChannel(int channel) {
    if (!isValid()) {
        return;
    }
    ASensorManager_destroyDirectChannel(mManager, channel);
    mSensorDirectChannel.erase(channel);
    return;
}

int TestSensorManager::configureDirectReport(TestSensor sensor, int channel, int rate) {
    if (!isValid()) {
        return -EINVAL;
    }
    return ASensorManager_configureDirectReport(mManager, sensor, channel, rate);
}

char * TestSharedMemory::getBuffer() const {
    return mBuffer;
}

std::vector<ASensorEvent> TestSharedMemory::parseEvents(int64_t lastCounter, size_t offset) const {
    constexpr size_t kEventSize = sizeof(ASensorEvent);
    constexpr size_t kOffsetSize = offsetof(ASensorEvent, version);
    constexpr size_t kOffsetAtomicCounter = offsetof(ASensorEvent, reserved0);

    std::vector<ASensorEvent> events;
    while (offset + kEventSize <= mSize) {
        int64_t atomicCounter = *reinterpret_cast<uint32_t *>(mBuffer + offset + kOffsetAtomicCounter);
        if (atomicCounter <= lastCounter) {
            break;
        }

        int32_t size = *reinterpret_cast<int32_t *>(mBuffer + offset + kOffsetSize);
        if (size != kEventSize) {
            // unknown error, events parsed may be wrong, remove all
            events.clear();
            break;
        }

        events.push_back(*reinterpret_cast<ASensorEvent *>(mBuffer + offset));
        lastCounter = atomicCounter;
        offset += kEventSize;
    }

    return events;
}

TestSharedMemory::TestSharedMemory(int type, size_t size)
        : mType(type), mSize(0), mBuffer(nullptr),
            mSharedMemoryFd(-1), mHardwareBuffer(nullptr) {
    bool success = false;
    switch(type) {
        case ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY: {
            mSharedMemoryFd = ASharedMemory_create("TestSharedMemory", size);
            if (mSharedMemoryFd < 0
                    || ASharedMemory_getSize(mSharedMemoryFd) != size) {
                break;
            }

            mSize = size;
            mBuffer = reinterpret_cast<char *>(::mmap(
                    nullptr, mSize, PROT_READ | PROT_WRITE,
                    MAP_SHARED, mSharedMemoryFd, 0));

            if (mBuffer == MAP_FAILED) {
                mBuffer = nullptr;
                break;
            }
            success = true;
            break;
        }
        case ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER: {
            AHardwareBuffer_Desc desc = {
                .width = static_cast<uint32_t>(size),
                .height = 1,
                .layers = 1,
                .usage = AHARDWAREBUFFER_USAGE_SENSOR_DIRECT_DATA
                         | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                .format = AHARDWAREBUFFER_FORMAT_BLOB
            };

            // allocate
            if (AHardwareBuffer_allocate(&desc, &mHardwareBuffer) == 0) {
                // lock
                if (AHardwareBuffer_lock(mHardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY,
                                         -1, nullptr, reinterpret_cast<void **>(&mBuffer)) == 0) {
                    if (mBuffer != nullptr) {
                        mSize = size;
                        success = true;
                    }
                }
            }
            break;
        }
        default:
            break;
    }

    if (!success) {
        release();
    }
}

TestSharedMemory::~TestSharedMemory() {
    release();
}

void TestSharedMemory::release() {
    switch(mType) {
        case ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY: {
            if (mBuffer != nullptr) {
                ::munmap(mBuffer, mSize);
                mBuffer = nullptr;
            }
            if (mSharedMemoryFd > 0) {
                ::close(mSharedMemoryFd);
                mSharedMemoryFd = -1;
            }
            mSize = 0;
            break;
        }
        case ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER: {
            if (mHardwareBuffer != nullptr) {
                if (mBuffer != nullptr) {
                    int32_t fence = -1;
                    AHardwareBuffer_unlock(mHardwareBuffer, &fence);
                    mBuffer = nullptr;
                }
                AHardwareBuffer_release(mHardwareBuffer);
                mHardwareBuffer = nullptr;
            }
            mSize = 0;
            break;
        }
        default:
            break;
    }
    if (mSharedMemoryFd > 0 || mSize != 0 || mBuffer != nullptr || mHardwareBuffer != nullptr) {
        ALOGE("TestSharedMemory %p not properly destructed: "
              "type %d, shared_memory_fd %d, hardware_buffer %p, size %zu, buffer %p",
              this, static_cast<int>(mType), mSharedMemoryFd, mHardwareBuffer, mSize, mBuffer);
    }
}

TestSharedMemory* TestSharedMemory::create(int type, size_t size) {
    constexpr size_t kMaxSize = 128*1024*1024; // sensor test should not need more than 128M
    if (size == 0 || size >= kMaxSize) {
        return nullptr;
    }

    auto m = new TestSharedMemory(type, size);
    if (m->mSize != size || m->mBuffer == nullptr) {
        delete m;
        m = nullptr;
    }
    return m;
}
} // namespace SensorTest
} // namespace android
