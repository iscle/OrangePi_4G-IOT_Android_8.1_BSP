/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "jni.h"

#include <utils/Log.h>

#include "../../StaticSharedNativeLibProvider/native/version.h"

#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))

static jint android_os_lib_consumer_UseSharedLibraryTest_getVersion(JNIEnv*, jclass) {
    return StaticSharedLib::android_os_lib_provider_getVersion();
}

static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods) {
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    static JNINativeMethod gMethods[] = {
            { "getVersion", "()I", (void*) android_os_lib_consumer_UseSharedLibraryTest_getVersion }
    };

    registerNativeMethods(env, "android/os/lib/consumer/UseSharedLibraryTest",
            gMethods, ARRAY_SIZE(gMethods));

    return JNI_VERSION_1_6;
}

