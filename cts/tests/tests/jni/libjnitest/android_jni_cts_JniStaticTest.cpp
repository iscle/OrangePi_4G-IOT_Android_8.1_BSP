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
 */

/*
 * Native implementation for the JniStaticTest parts.
 */

#include <jni.h>
#include <JNIHelp.h>

extern "C" JNIEXPORT jint JNICALL Java_android_jni_cts_ClassLoaderHelper_nativeGetHashCode(
        JNIEnv* env,
        jobject obj __attribute__((unused)),
        jobject appLoader,
        jclass appLoaderClass) {
  jmethodID midFindClass = env->GetMethodID(appLoaderClass, "findClass",
          "(Ljava/lang/String;)Ljava/lang/Class;");
  jstring coreClsName = env->NewStringUTF("android.jni.cts.ClassLoaderStaticNonce");
  jobject coreClass = env->CallObjectMethod(appLoader, midFindClass, coreClsName);
  jmethodID midHashCode = env->GetMethodID((jclass)coreClass, "hashCode", "()I");
  jint hash = env->CallIntMethod(coreClass, midHashCode);

  return hash;
}
