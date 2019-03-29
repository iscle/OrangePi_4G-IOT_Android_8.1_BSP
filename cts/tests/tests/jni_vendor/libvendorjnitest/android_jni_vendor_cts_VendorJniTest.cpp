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

#include <jni.h>
#include <dlfcn.h>

extern "C" JNIEXPORT jstring JNICALL
Java_android_jni_vendor_cts_VendorJniTest_dlopen(JNIEnv* env, jclass clazz, jstring name) {
    const char* libname = env->GetStringUTFChars(name, nullptr);
    jstring error_msg;
    dlerror(); // clear any existing error
    void* handle = dlopen(libname, RTLD_NOW);
    env->ReleaseStringUTFChars(name, libname);
    if (handle == nullptr) {
        error_msg = env->NewStringUTF(dlerror());
    } else {
        error_msg = env->NewStringUTF("");
    }
    return error_msg;
}
