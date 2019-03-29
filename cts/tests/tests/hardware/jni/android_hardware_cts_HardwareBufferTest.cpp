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

#include <android/hardware_buffer.h>

#include <jni.h>

#include <android/hardware_buffer_jni.h>
#include <utils/Errors.h>

#define LOG_TAG "HardwareBufferTest"

static jobject android_hardware_HardwareBuffer_nativeCreateHardwareBuffer(JNIEnv* env, jclass,
        jint width, jint height, jint format, jint layers, jlong usage) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = {};

    desc.width = width;
    desc.height = height;
    desc.layers = layers;
    desc.usage = usage;
    desc.format = format;
    int res = AHardwareBuffer_allocate(&desc, &buffer);
    if (res == android::NO_ERROR) {
        return AHardwareBuffer_toHardwareBuffer(env, buffer);
    } else {
        return 0;
    }
}

static void android_hardware_HardwareBuffer_nativeReleaseHardwareBuffer(JNIEnv* env, jclass,
        jobject hardwareBufferObj) {
    AHardwareBuffer* buffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBufferObj);
    AHardwareBuffer_release(buffer);
}

static JNINativeMethod gMethods[] = {
    { "nativeCreateHardwareBuffer", "(IIIIJ)Landroid/hardware/HardwareBuffer;",
            (void *) android_hardware_HardwareBuffer_nativeCreateHardwareBuffer },
    { "nativeReleaseHardwareBuffer", "(Landroid/hardware/HardwareBuffer;)V",
           (void *) android_hardware_HardwareBuffer_nativeReleaseHardwareBuffer },
};

int register_android_hardware_cts_HardwareBufferTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/hardware/cts/HardwareBufferTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
