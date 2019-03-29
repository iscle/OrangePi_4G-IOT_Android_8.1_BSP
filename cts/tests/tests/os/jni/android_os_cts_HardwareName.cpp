/*
 * Copyright (C) 2015 The Android Open Source Project
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
#include <jni.h>

#include <sys/system_properties.h>

jstring android_os_cts_HardwareName_getName(JNIEnv* env, jobject thiz)
{
    char name[PROP_VALUE_MAX];

    if (__system_property_get("ro.boot.hardware", name) <= 0) {
        return NULL;
    }

    return env->NewStringUTF(name);
}

static JNINativeMethod gMethods[] = {
    {  "getName", "()Ljava/lang/String;",
            (void *) android_os_cts_HardwareName_getName },
};

int register_android_os_cts_HardwareName(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/os/cts/HardwareName");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
