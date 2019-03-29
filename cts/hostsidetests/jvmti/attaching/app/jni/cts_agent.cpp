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
#include <jvmti.h>

#include <algorithm>
#include <mutex>
#include <vector>

#include "android-base/logging.h"
#include "jvmti_helper.h"
#include "scoped_utf_chars.h"
#include "test_env.h"

namespace art {

static std::mutex gVectorMutex;
static std::vector<std::string> gLoadedDescriptors;

static std::string GetClassName(jvmtiEnv* jenv, JNIEnv* jni_env, jclass klass) {
  char* name;
  jvmtiError result = jenv->GetClassSignature(klass, &name, nullptr);
  if (result != JVMTI_ERROR_NONE) {
    if (jni_env != nullptr) {
      JvmtiErrorToException(jni_env, jenv, result);
    } else {
      printf("Failed to get class signature.\n");
    }
    return "";
  }

  std::string tmp(name);
  jenv->Deallocate(reinterpret_cast<unsigned char*>(name));

  return tmp;
}

static void EnableEvents(JNIEnv* env,
                         jboolean enable,
                         decltype(jvmtiEventCallbacks().ClassLoad) class_load,
                         decltype(jvmtiEventCallbacks().ClassPrepare) class_prepare) {
  if (enable == JNI_FALSE) {
    jvmtiError ret = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE,
                                                         JVMTI_EVENT_CLASS_LOAD,
                                                         nullptr);
    if (JvmtiErrorToException(env, jvmti_env, ret)) {
      return;
    }
    ret = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE,
                                              JVMTI_EVENT_CLASS_PREPARE,
                                              nullptr);
    JvmtiErrorToException(env, jvmti_env, ret);
    return;
  }

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
  callbacks.ClassLoad = class_load;
  callbacks.ClassPrepare = class_prepare;
  jvmtiError ret = jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (JvmtiErrorToException(env, jvmti_env, ret)) {
    return;
  }

  ret = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
                                            JVMTI_EVENT_CLASS_LOAD,
                                            nullptr);
  if (JvmtiErrorToException(env, jvmti_env, ret)) {
    return;
  }
  ret = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
                                            JVMTI_EVENT_CLASS_PREPARE,
                                            nullptr);
  JvmtiErrorToException(env, jvmti_env, ret);
}

static void JNICALL ClassPrepareCallback(jvmtiEnv* jenv,
                                         JNIEnv* jni_env,
                                         jthread thread ATTRIBUTE_UNUSED,
                                         jclass klass) {
  std::string name = GetClassName(jenv, jni_env, klass);
  if (name == "") {
    return;
  }
  std::lock_guard<std::mutex> guard(gVectorMutex);
  gLoadedDescriptors.push_back(name);
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_jvmti_JvmtiActivity_didSeeLoadOf(
    JNIEnv* env, jclass Main_klass ATTRIBUTE_UNUSED, jstring descriptor) {
  std::lock_guard<std::mutex> guard(gVectorMutex);
  ScopedUtfChars str(env, descriptor);
  std::string tmp = str.c_str();
  bool found = std::find(gLoadedDescriptors.begin(), gLoadedDescriptors.end(), tmp) !=
      gLoadedDescriptors.end();
  return found ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm,
                                               char* options ATTRIBUTE_UNUSED,
                                               void* reserved ATTRIBUTE_UNUSED) {
  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti_env), JVMTI_VERSION_1_0) != 0) {
    LOG(FATAL) << "Could not get shared jvmtiEnv";
  }

  SetAllCapabilities(jvmti_env);
  return 0;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm,
                                                 char* options ATTRIBUTE_UNUSED,
                                                 void* reserved ATTRIBUTE_UNUSED) {
  JNIEnv* env;
  CHECK_EQ(0, vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6))
      << "Could not get JNIEnv";

  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti_env), JVMTI_VERSION_1_0) != 0) {
    LOG(FATAL) << "Could not get shared jvmtiEnv";
  }

  SetAllCapabilities(jvmti_env);

  EnableEvents(env, JNI_TRUE, nullptr, ClassPrepareCallback);

  return 0;
}

}  // namespace art
