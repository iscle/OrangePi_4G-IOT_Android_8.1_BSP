/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "common.h"

#include <android/log.h>
#include <jni.h>
#include <JNIHelp.h>

#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,"namespaceb",__VA_ARGS__)

int global = 0;

jint JNI_OnLoad(JavaVM*, void*) {
    LOGI("JNI_OnLoad namespaceb");
    return JNI_VERSION_1_4;
}

extern "C" JNIEXPORT void JNICALL
    Java_android_jni_cts_ClassNamespaceB_incrementGlobal(JNIEnv*, jclass) {
  incrementGlobal();
}

extern "C" JNIEXPORT jint JNICALL
    Java_android_jni_cts_ClassNamespaceB_getGlobal(JNIEnv*, jclass) {
  return getGlobal();
}
