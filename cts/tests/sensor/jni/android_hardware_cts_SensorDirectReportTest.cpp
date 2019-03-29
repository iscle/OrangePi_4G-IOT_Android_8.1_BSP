/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"nclude
 * <android/hardware_buffer_jni.h>);
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

#include <android/hardware_buffer_jni.h>

namespace {
jboolean readHardwareBuffer(JNIEnv* env, jclass,
        jobject hardwareBufferObj, jbyteArray buffer, jint srcOffset, jint destOffset, jint count) {
    if (hardwareBufferObj == nullptr || buffer == nullptr ||
        srcOffset < 0 || destOffset < 0 || count <= 0) {
        return false;
    }

    if (env->GetArrayLength(buffer) < destOffset + count) {
        ALOGE("Byte array is not large enough.");
        return false;
    }

    AHardwareBuffer *hardwareBuffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBufferObj);
    if (hardwareBuffer == nullptr) {
        ALOGE("Cannot get AHardwareBuffer from HardwareBuffer");
        return false;
    }

    void *address;
    int32_t fence = -1;
    jboolean ret = false;
    if (AHardwareBuffer_lock(hardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY,
                             fence, nullptr, &address) == 0) {
        if (address != nullptr) {
            env->SetByteArrayRegion(buffer, destOffset, count,
                                    reinterpret_cast<const jbyte *>(address) + srcOffset);
            ret = true;
        } else {
            ALOGE("AHardwareBuffer locked but address is invalid");
        }
        AHardwareBuffer_unlock(hardwareBuffer, &fence);
    }
    return ret;
}

JNINativeMethod gMethods[] = {
    {  "nativeReadHardwareBuffer", "(Landroid/hardware/HardwareBuffer;[BIII)Z",
            (void *) readHardwareBuffer},
};
} // unamed namespace

int register_android_hardware_cts_SensorDirectReportTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/hardware/cts/SensorDirectReportTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
