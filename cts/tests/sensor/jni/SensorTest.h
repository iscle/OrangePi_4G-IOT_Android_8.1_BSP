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

#ifndef SENSOR_TEST_H
#define SENSOR_TEST_H

#include "nativeTestHelper.h"
#include <android/sensor.h>
#include <android/hardware_buffer.h>
#include <android/sharedmem.h>

#include <unordered_set>
#include <vector>
#include <sys/mman.h>
#include <unistd.h>

namespace android {
namespace SensorTest {

class TestSensor;
class TestSensorManager;
class TestSharedMemory;

class SensorTest {
public:
    virtual bool SetUp();
    virtual void TearDown();
    virtual ~SensorTest() = default;

    // tests
    void testInitialized(JNIEnv *env);
    void testInvalidParameter(JNIEnv *env);
    void testDirectReport(JNIEnv *env, int32_t sensorType, int32_t channelType, int32_t rateLevel);

private:
    std::unique_ptr<TestSensorManager> mManager;
};

// NDK ASensorManager wrapper
class TestSensorManager {
public:
    static TestSensorManager * getInstanceForPackage(const char *package);
    virtual ~TestSensorManager();

    TestSensor getDefaultSensor(int type);
    int createDirectChannel(const TestSharedMemory &mem);
    void destroyDirectChannel(int channel);
    int configureDirectReport(TestSensor sensor, int channel, int rateLevel);
    bool isValid() const { return mManager != nullptr; }
private:
    TestSensorManager(const char *package);
    int createSharedMemoryDirectChannel(int fd, size_t size);
    int createHardwareBufferDirectChannel(AHardwareBuffer const *buffer, size_t size);

    ASensorManager *mManager; // singleton, does not need delete

    // book keeping
    std::unordered_set<int> mSensorDirectChannel;
};

// NDK ASensor warpper
class TestSensor {
public:
    TestSensor(ASensor const *s) : mSensor(s) { }

    int getType() const {
        if (!isValid()) {
            return -1;
        }
        return ASensor_getType(mSensor);
    }

    bool isDirectChannelTypeSupported(int channelType) const {
        if (!isValid()) {
            return false;
        }
        return ASensor_isDirectChannelTypeSupported(mSensor, channelType);
    }

    int getHighestDirectReportRateLevel() const {
        if (!isValid()) {
            return ASENSOR_DIRECT_RATE_STOP;
        }
        return ASensor_getHighestDirectReportRateLevel(mSensor);
    }

    operator ASensor const * () { return mSensor; }

    bool isValid() const { return mSensor != nullptr; }
private:
    ASensor const * mSensor;
};

// Shared memory wrapper class
class TestSharedMemory {
public:
    static TestSharedMemory* create(int type, size_t size);
    char * getBuffer() const;
    std::vector<ASensorEvent> parseEvents(int64_t lastCounter = -1, size_t offset = 0) const;
    virtual ~TestSharedMemory();

    int getSharedMemoryFd() const {
        return mSharedMemoryFd;
    }

    AHardwareBuffer const * getHardwareBuffer() const {
        return mHardwareBuffer;
    }

    int getType() const {
        return mType;
    }

    size_t getSize() const {
        return mSize;
    }
private:
    TestSharedMemory(int type, size_t size);
    void release();

    const int mType;
    size_t mSize;
    char* mBuffer;
    int mSharedMemoryFd;
    AHardwareBuffer *mHardwareBuffer;
};
} // namespace SensorTest
} // namespace android

#endif // SENSOR_TEST_H
