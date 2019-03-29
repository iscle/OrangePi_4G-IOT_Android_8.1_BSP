/* 
 * Copyright (C) 2010 The Android Open Source Project
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

#include <jni.h>
#include <stdio.h>

extern int register_android_os_cts_CpuFeatures(JNIEnv*);

extern int register_android_os_cts_CpuInstructions(JNIEnv*);

extern int register_android_os_cts_TaggedPointer(JNIEnv*);

extern int register_android_os_cts_HardwareName(JNIEnv*);

extern int register_android_os_cts_OSFeatures(JNIEnv*);

extern int register_android_os_cts_NoExecutePermissionTest(JNIEnv*);

extern int register_android_os_cts_SeccompTest(JNIEnv*);

extern int register_android_os_cts_SharedMemoryTest(JNIEnv*);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return JNI_ERR;
    }

    if (register_android_os_cts_CpuInstructions(env)) {
        return JNI_ERR;
    }

    if (register_android_os_cts_TaggedPointer(env)) {
        return JNI_ERR;
    }

    if (register_android_os_cts_HardwareName(env)) {
        return JNI_ERR;
    }

    if (register_android_os_cts_OSFeatures(env)) {
        return JNI_ERR;
    }

    if (register_android_os_cts_NoExecutePermissionTest(env)) {
        return JNI_ERR;
    }

    if (register_android_os_cts_SeccompTest(env)) {
        return JNI_ERR;
    }

    if (register_android_os_cts_SharedMemoryTest(env)) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_4;
}
