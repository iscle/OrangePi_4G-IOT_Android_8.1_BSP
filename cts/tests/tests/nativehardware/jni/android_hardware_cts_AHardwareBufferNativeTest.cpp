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

#include "AHardwareBufferTest.h"
#define TAG "AHardwareBufferTest"
#include "NativeTestHelper.h"

namespace {

using android::AHardwareBufferTest;

#define RETURN_ON_EXCEPTION() do { if (env->ExceptionCheck()) { return; } } while(false)

jlong setUp(JNIEnv*, jclass) {
    AHardwareBufferTest* test = new AHardwareBufferTest();
    if (test != nullptr) {
        test->SetUp();
    }
    return reinterpret_cast<jlong>(test);
}

void tearDown(JNIEnv*, jclass, jlong instance) {
    delete reinterpret_cast<AHardwareBufferTest*>(instance);
}

void test(JNIEnv* env, jclass, jlong instance, jboolean vrHighPerformanceSupported) {
    AHardwareBufferTest *test = reinterpret_cast<AHardwareBufferTest*>(instance);
    ASSERT_NOT_NULL(test);

    ALOGI("testAHardwareBuffer_allocate_FailsWithNullInput");
    test->testAHardwareBuffer_allocate_FailsWithNullInput(env);
    RETURN_ON_EXCEPTION();

    ALOGI("testAHardwareBuffer_allocate_BlobFormatRequiresHeight1");
    test->testAHardwareBuffer_allocate_BlobFormatRequiresHeight1(env);
    RETURN_ON_EXCEPTION();

    ALOGI("testAHardwareBuffer_allocate_Succeeds");
    test->testAHardwareBuffer_allocate_Succeeds(env);
    RETURN_ON_EXCEPTION();

    ALOGI("testAHardwareBuffer_describe_Succeeds");
    test->testAHardwareBuffer_describe_Succeeds(env);
    RETURN_ON_EXCEPTION();

    ALOGI("testAHardwareBuffer_SendAndRecv_Succeeds");
    test->testAHardwareBuffer_SendAndRecv_Succeeds(env);
    RETURN_ON_EXCEPTION();

    ALOGI("testAHardwareBuffer_Lock_and_Unlock_Succeed");
    test->testAHardwareBuffer_Lock_and_Unlock_Succeed(env);
    RETURN_ON_EXCEPTION();

    if (vrHighPerformanceSupported == JNI_TRUE) {
        ALOGI("testAHardwareBuffer_Lock_and_Unlock_Succeed");
        test->testAHardwareBufferSupportsLayeredBuffersForVr(env);
        RETURN_ON_EXCEPTION();
    }
}

JNINativeMethod gMethods[] = {
    {"nativeSetUp", "()J", (void *)setUp},
    {"nativeTearDown", "(J)V", (void *)tearDown},
    {"nativeTest", "(JZ)V", (void *)test},
};

} // namespace

int register_android_hardware_cts_AHardwareBufferNativeTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/hardware/cts/AHardwareBufferNativeTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
