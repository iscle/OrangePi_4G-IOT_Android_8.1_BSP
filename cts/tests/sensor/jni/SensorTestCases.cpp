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

// Test if test environment is correctly initialized
void SensorTest::testInitialized(JNIEnv *env) {
    ASSERT_TRUE(mManager->isValid());
}

// Test if invalid parameter cases are handled correctly
void SensorTest::testInvalidParameter(JNIEnv *env) {
    ASensorList dummyList;
    ASSERT_EQ(ASensorManager_getSensorList(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorManager_getSensorList(nullptr, &dummyList), -EINVAL);

    ASSERT_EQ(ASensorManager_getDefaultSensor(nullptr, ASENSOR_TYPE_ACCELEROMETER), nullptr);

    ASSERT_EQ(ASensorManager_getDefaultSensorEx(
            nullptr, ASENSOR_TYPE_ACCELEROMETER, false), nullptr);

    ALooper *nonNullLooper = reinterpret_cast<ALooper *>(1);
    ASensorManager *nonNullManager = reinterpret_cast<ASensorManager *>(1);
    ASSERT_EQ(ASensorManager_createEventQueue(nullptr, nullptr, 0, nullptr, nullptr), nullptr);
    ASSERT_EQ(ASensorManager_createEventQueue(
            nullptr, nonNullLooper, 0, nullptr, nullptr), nullptr);
    ASSERT_EQ(ASensorManager_createEventQueue(
            nonNullManager, nullptr, 0, nullptr, nullptr), nullptr);

    ASensorEventQueue *nonNullQueue = reinterpret_cast<ASensorEventQueue *>(1);
    ASSERT_EQ(ASensorManager_destroyEventQueue(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorManager_destroyEventQueue(nullptr, nonNullQueue), -EINVAL);
    ASSERT_EQ(ASensorManager_destroyEventQueue(nonNullManager, nullptr), -EINVAL);

    int fakeValidFd = 1;
    int invalidFd = -1;
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nullptr, fakeValidFd, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nonNullManager, invalidFd, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nonNullManager, fakeValidFd, sizeof(ASensorEvent) - 1), -EINVAL);
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nonNullManager, fakeValidFd, 0), -EINVAL);

    AHardwareBuffer *nonNullHardwareBuffer = reinterpret_cast<AHardwareBuffer *>(1);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nullptr, nonNullHardwareBuffer, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nonNullManager, nullptr, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nonNullManager, nonNullHardwareBuffer, sizeof(ASensorEvent) - 1), -EINVAL);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nonNullManager, nonNullHardwareBuffer, 0), -EINVAL);

    // no return value to test, but call this to test if it will crash
    ASensorManager_destroyDirectChannel(nullptr, 1);

    ASensor *nonNullSensor = reinterpret_cast<ASensor *>(1);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nullptr, nullptr, 1, ASENSOR_DIRECT_RATE_NORMAL), -EINVAL);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nullptr, nonNullSensor, 1, ASENSOR_DIRECT_RATE_NORMAL), -EINVAL);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nullptr, nonNullSensor, 1, ASENSOR_DIRECT_RATE_STOP), -EINVAL);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nonNullManager, nullptr, 1, ASENSOR_DIRECT_RATE_NORMAL), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_registerSensor(nullptr, nullptr, 1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nullptr, nonNullSensor, 1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nullptr, 1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nonNullSensor, -1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nonNullSensor, 1, -1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nonNullSensor, -1, -1), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_enableSensor(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_enableSensor(nullptr, nonNullSensor), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_enableSensor(nonNullQueue, nullptr), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_disableSensor(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_disableSensor(nullptr, nonNullSensor), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_disableSensor(nonNullQueue, nullptr), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_setEventRate(nullptr, nullptr, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_setEventRate(nullptr, nonNullSensor, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_setEventRate(nonNullQueue, nullptr, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_setEventRate(nonNullQueue, nonNullSensor, -1), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_hasEvents(nullptr), -EINVAL);

    ASensorEvent event;
    ASensorEvent *nonNullEvent = &event;
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nullptr, 1), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nullptr, 0), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nonNullEvent, 1), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nonNullEvent, 0), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_getEvents(nonNullQueue, nullptr, 1), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nonNullQueue, nullptr, 0), -EINVAL);

    ASSERT_NULL(ASensor_getName(nullptr));
    ASSERT_NULL(ASensor_getVendor(nullptr));
    ASSERT_EQ(ASensor_getType(nullptr), ASENSOR_TYPE_INVALID);
    // cannot use ASSERT_EQ as nan compare always returns false
    ASSERT_NAN(ASensor_getResolution(nullptr));
    ASSERT_EQ(ASensor_getMinDelay(nullptr), ASENSOR_DELAY_INVALID);
    ASSERT_EQ(ASensor_getFifoMaxEventCount(nullptr), ASENSOR_FIFO_COUNT_INVALID);
    ASSERT_EQ(ASensor_getFifoReservedEventCount(nullptr), ASENSOR_FIFO_COUNT_INVALID);
    ASSERT_NULL(ASensor_getStringType(nullptr));
    ASSERT_EQ(ASensor_getReportingMode(nullptr), AREPORTING_MODE_INVALID);
    ASSERT_EQ(ASensor_isWakeUpSensor(nullptr), false);
    ASSERT_EQ(ASensor_isDirectChannelTypeSupported(
            nullptr, ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY), false);
    ASSERT_EQ(ASensor_isDirectChannelTypeSupported(
            nullptr, ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER), false);
    ASSERT_EQ(ASensor_getHighestDirectReportRateLevel(nullptr), ASENSOR_DIRECT_RATE_STOP);
}

// Test sensor direct report functionality
void SensorTest::testDirectReport(JNIEnv* env, int32_t sensorType, int32_t channelType, int32_t rateLevel) {
    constexpr size_t kEventSize = sizeof(ASensorEvent);
    constexpr size_t kNEvent = 4096; // enough to contain 1.5 * 800 * 2.2 events
    constexpr size_t kMemSize = kEventSize * kNEvent;

    // value check criterion
    constexpr float GRAVITY_MIN = 9.81f - 0.5f;
    constexpr float GRAVITY_MAX = 9.81f + 0.5f;
    constexpr float GYRO_MAX = 0.1f; // ~5 dps

    constexpr float RATE_NORMAL_NOMINAL = 50;
    constexpr float RATE_FAST_NOMINAL = 200;
    constexpr float RATE_VERY_FAST_NOMINAL = 800;

    TestSensor sensor = mManager->getDefaultSensor(sensorType);
    if (!sensor.isValid()
        || sensor.getHighestDirectReportRateLevel() < rateLevel
        || !sensor.isDirectChannelTypeSupported(channelType)) {
        // no sensor of type sensorType or it does not declare support of channelType or rateLevel
        return;
    }

    std::unique_ptr<TestSharedMemory> mem(TestSharedMemory::create(channelType, kMemSize));
    ASSERT_NE(mem, nullptr);
    ASSERT_NE(mem->getBuffer(), nullptr);
    switch (channelType) {
        case ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY:
            ASSERT_GT(mem->getSharedMemoryFd(), 0);
            break;
        case ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER:
            ASSERT_NOT_NULL(mem->getHardwareBuffer());
            break;
    }

    char* buffer = mem->getBuffer();
    // fill memory with data
    for (size_t i = 0; i < kMemSize; ++i) {
        buffer[i] = '\xcc';
    }

    int32_t channel;
    channel = mManager->createDirectChannel(*mem);
    ASSERT_GT(channel, 0);

    // check memory is zeroed
    for (size_t i = 0; i < kMemSize; ++i) {
        ASSERT_EQ(buffer[i], '\0');
    }

    int32_t eventToken;
    eventToken = mManager->configureDirectReport(sensor, channel, rateLevel);
    usleep(1500000); // sleep 1 sec for data, plus 0.5 sec for initialization
    auto events = mem->parseEvents();

    // find norminal rate
    float nominalFreq = 0.f;
    float nominalTestTimeSec = 1.f;
    float maxTestTimeSec = 1.5f;
    switch (rateLevel) {
        case ASENSOR_DIRECT_RATE_NORMAL:
            nominalFreq = RATE_NORMAL_NOMINAL;
            break;
        case ASENSOR_DIRECT_RATE_FAST:
            nominalFreq = RATE_FAST_NOMINAL;
            break;
        case ASENSOR_DIRECT_RATE_VERY_FAST:
            nominalFreq = RATE_VERY_FAST_NOMINAL;
            break;
    }

    // allowed to be between 55% and 220% of nominal freq
    ASSERT_GT(events.size(), static_cast<size_t>(nominalFreq * 0.55f * nominalTestTimeSec));
    ASSERT_LT(events.size(), static_cast<size_t>(nominalFreq * 2.2f * maxTestTimeSec));

    int64_t lastTimestamp = 0;
    for (auto &e : events) {
        ASSERT_EQ(e.type, sensorType);
        ASSERT_EQ(e.sensor, eventToken);
        ASSERT_GT(e.timestamp, lastTimestamp);

        // type specific value check
        switch(sensorType) {
            case ASENSOR_TYPE_ACCELEROMETER: {
                ASensorVector &acc = e.vector;
                double accNorm = std::sqrt(acc.x * acc.x + acc.y * acc.y + acc.z * acc.z);
                if (accNorm > GRAVITY_MAX || accNorm < GRAVITY_MIN) {
                    ALOGE("Gravity norm = %f", accNorm);
                }
                ASSERT_GE(accNorm, GRAVITY_MIN);
                ASSERT_LE(accNorm, GRAVITY_MAX);
                break;
            }
            case ASENSOR_TYPE_GYROSCOPE: {
                ASensorVector &gyro = e.vector;
                double gyroNorm = std::sqrt(gyro.x * gyro.x + gyro.y * gyro.y + gyro.z * gyro.z);
                // assert not drifting
                ASSERT_LE(gyroNorm, GYRO_MAX);  // < ~2.5 degree/s
                break;
            }
        }

        lastTimestamp = e.timestamp;
    }

    // stop sensor and unregister channel
    mManager->configureDirectReport(sensor, channel, ASENSOR_DIRECT_RATE_STOP);
    mManager->destroyDirectChannel(channel);
}
} // namespace SensorTest
} // namespace android
