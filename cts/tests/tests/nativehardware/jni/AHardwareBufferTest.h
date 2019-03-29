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

#ifndef AHARDWAREBUFFER_TEST_H
#define AHARDWAREBUFFER_TEST_H

#include "NativeHardwareTest.h"

#include <jni.h>

namespace android {

class AHardwareBufferTest : public NativeHardwareTest {
public:
    bool SetUp() override;
    void TearDown() override;
    virtual ~AHardwareBufferTest() = default;

    // tests
    void testAHardwareBuffer_allocate_FailsWithNullInput(JNIEnv *env);
    void testAHardwareBuffer_allocate_BlobFormatRequiresHeight1(JNIEnv *env);
    void testAHardwareBuffer_allocate_Succeeds(JNIEnv *env);
    void testAHardwareBuffer_describe_Succeeds(JNIEnv *env);
    void testAHardwareBuffer_SendAndRecv_Succeeds(JNIEnv *env);
    void testAHardwareBuffer_Lock_and_Unlock_Succeed(JNIEnv *env);
    void testAHardwareBufferSupportsLayeredBuffersForVr(JNIEnv *env);
};

} // namespace android

#endif // AHARDWAREBUFFER_TEST_H
