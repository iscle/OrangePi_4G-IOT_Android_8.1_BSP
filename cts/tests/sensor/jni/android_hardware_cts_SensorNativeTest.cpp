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
 *
 */

#include "nativeTestHelper.h"
#include "SensorTest.h"

namespace {
using android::SensorTest::SensorTest;

#define RETURN_ON_EXCEPTION() do { if (env->ExceptionCheck()) { return;} } while(false)

jlong setUp(JNIEnv*, jclass) {
    SensorTest *test = new SensorTest();
    if (test != nullptr) {
        test->SetUp();
    }
    return reinterpret_cast<jlong>(test);
}

void tearDown(JNIEnv*, jclass, jlong instance) {
    delete reinterpret_cast<SensorTest *>(instance);
}

void test(JNIEnv* env, jclass, jlong instance) {
    SensorTest *test = reinterpret_cast<SensorTest *>(instance);
    ASSERT_NOT_NULL(test);

    // test if SensorTest is intialized
    ALOGI("testInitialized");
    test->testInitialized(env);
    RETURN_ON_EXCEPTION();

    // test if SensorTest is intialized
    ALOGI("testInvalidParameter");
    test->testInvalidParameter(env);
    RETURN_ON_EXCEPTION();

    // test sensor direct report
    std::vector<int32_t> sensorTypes ={ASENSOR_TYPE_ACCELEROMETER, ASENSOR_TYPE_GYROSCOPE};
    std::vector<int32_t> rates = {
        ASENSOR_DIRECT_RATE_NORMAL, ASENSOR_DIRECT_RATE_FAST, ASENSOR_DIRECT_RATE_VERY_FAST};
    std::vector<int32_t> channelTypes =
        {ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY, ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER};
    for (auto s : sensorTypes) {
        for (auto c : channelTypes) {
            for (auto r : rates) {
                ALOGI("testDirectReport: sensorType = %d, channelType = %d, ratelevel = %d",
                      s, c, r);
                test->testDirectReport(env, s, c, r);
                RETURN_ON_EXCEPTION();
            }
        }
    }
}

JNINativeMethod gMethods[] = {
    {  "nativeSetUp", "()J",
            (void *) setUp},
    {  "nativeTearDown", "(J)V",
            (void *) tearDown},
    {  "nativeTest", "(J)V",
            (void *) test},
};
} // unamed namespace

int register_android_hardware_cts_SensorNativeTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/hardware/cts/SensorNativeTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
