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

#include "jni.h"

#include <stack>
#include <string>
#include <unordered_map>
#include <vector>

#include "android-base/logging.h"
#include "android-base/macros.h"
#include "jni_helper.h"
#include "jvmti_helper.h"
#include "jvmti.h"
#include "scoped_primitive_array.h"
#include "test_env.h"

namespace art {

extern "C" JNIEXPORT jint JNICALL Java_android_jvmti_cts_JvmtiRedefineClassesTest_redefineClass(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jclass target, jbyteArray dex_bytes) {
  jvmtiClassDefinition def;
  def.klass = target;
  def.class_byte_count = static_cast<jint>(env->GetArrayLength(dex_bytes));
  signed char* redef_bytes = env->GetByteArrayElements(dex_bytes, nullptr);
  jvmtiError res =jvmti_env->Allocate(def.class_byte_count,
                                      const_cast<unsigned char**>(&def.class_bytes));
  if (res != JVMTI_ERROR_NONE) {
    return static_cast<jint>(res);
  }
  memcpy(const_cast<unsigned char*>(def.class_bytes), redef_bytes, def.class_byte_count);
  env->ReleaseByteArrayElements(dex_bytes, redef_bytes, 0);
  // Do the redefinition.
  res = jvmti_env->RedefineClasses(1, &def);
  return static_cast<jint>(res);
}

extern "C" JNIEXPORT jint JNICALL Java_android_jvmti_cts_JvmtiRedefineClassesTest_retransformClass(
    JNIEnv* env ATTRIBUTE_UNUSED, jclass klass ATTRIBUTE_UNUSED, jclass target) {
  return jvmti_env->RetransformClasses(1, &target);
}

class TransformationData {
 public:
  TransformationData() : redefinitions_(), should_pop_(false) {}

  void SetPop(bool val) {
    should_pop_ = val;
  }

  void ClearRedefinitions() {
    redefinitions_.clear();
  }

  void PushRedefinition(std::string name, std::vector<unsigned char> data) {
    if (redefinitions_.find(name) == redefinitions_.end()) {
      std::stack<std::vector<unsigned char>> stack;
      redefinitions_[name] = std::move(stack);
    }
    redefinitions_[name].push(std::move(data));
  }

  bool RetrieveRedefinition(std::string name, /*out*/std::vector<unsigned char>* data) {
    auto stack = redefinitions_.find(name);
    if (stack == redefinitions_.end() || stack->second.empty()) {
      return false;
    } else {
      *data = stack->second.top();
      return true;
    }
  }

  void PopRedefinition(std::string name) {
    if (should_pop_) {
      auto stack = redefinitions_.find(name);
      if (stack == redefinitions_.end() || stack->second.empty()) {
        return;
      } else {
        stack->second.pop();
      }
    }
  }

 private:
  std::unordered_map<std::string, std::stack<std::vector<unsigned char>>> redefinitions_;
  bool should_pop_;
};

static TransformationData data;

// The hook we are using.
void JNICALL CommonClassFileLoadHookRetransformable(jvmtiEnv* local_jvmti_env,
                                                    JNIEnv* jni_env ATTRIBUTE_UNUSED,
                                                    jclass class_being_redefined ATTRIBUTE_UNUSED,
                                                    jobject loader ATTRIBUTE_UNUSED,
                                                    const char* name,
                                                    jobject protection_domain ATTRIBUTE_UNUSED,
                                                    jint class_data_len ATTRIBUTE_UNUSED,
                                                    const unsigned char* class_dat ATTRIBUTE_UNUSED,
                                                    jint* new_class_data_len,
                                                    unsigned char** new_class_data) {
  std::string name_str(name);
  std::vector<unsigned char> dex_data;
  if (data.RetrieveRedefinition(name_str, &dex_data)) {
    unsigned char* jvmti_dex_data;
    if (JVMTI_ERROR_NONE != local_jvmti_env->Allocate(dex_data.size(), &jvmti_dex_data)) {
      LOG(FATAL) << "Unable to allocate output buffer for " << name;
      return;
    }
    memcpy(jvmti_dex_data, dex_data.data(), dex_data.size());
    *new_class_data_len = dex_data.size();
    *new_class_data = jvmti_dex_data;
    data.PopRedefinition(name);
  }
}

extern "C"
JNIEXPORT void JNICALL Java_android_jvmti_cts_JvmtiRedefineClassesTest_setTransformationEvent(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jboolean enable) {
  jvmtiEventCallbacks cb;
  memset(&cb, 0, sizeof(cb));
  cb.ClassFileLoadHook = CommonClassFileLoadHookRetransformable;
  if (JvmtiErrorToException(env, jvmti_env, jvmti_env->SetEventCallbacks(&cb, sizeof(cb)))) {
    return;
  }
  JvmtiErrorToException(env,
                        jvmti_env,
                        jvmti_env->SetEventNotificationMode(
                            enable == JNI_TRUE ? JVMTI_ENABLE : JVMTI_DISABLE,
                            JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,
                            nullptr));
  return;
}

extern "C"
JNIEXPORT void JNICALL Java_android_jvmti_cts_JvmtiRedefineClassesTest_clearTransformations(
    JNIEnv* env ATTRIBUTE_UNUSED, jclass klass ATTRIBUTE_UNUSED) {
  data.ClearRedefinitions();
}

extern "C"
JNIEXPORT void JNICALL Java_android_jvmti_cts_JvmtiRedefineClassesTest_setPopTransformations(
    JNIEnv* env ATTRIBUTE_UNUSED, jclass klass ATTRIBUTE_UNUSED, jboolean enable) {
  data.SetPop(enable == JNI_TRUE ? true : false);
}

extern "C"
JNIEXPORT void JNICALL Java_android_jvmti_cts_JvmtiRedefineClassesTest_pushTransformationResult(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jstring class_name, jbyteArray dex_bytes) {
  const char* name_chrs = env->GetStringUTFChars(class_name, nullptr);
  std::string name_str(name_chrs);
  env->ReleaseStringUTFChars(class_name, name_chrs);
  std::vector<unsigned char> dex_data;
  dex_data.resize(env->GetArrayLength(dex_bytes));
  signed char* redef_bytes = env->GetByteArrayElements(dex_bytes, nullptr);
  memcpy(dex_data.data(), redef_bytes, env->GetArrayLength(dex_bytes));
  data.PushRedefinition(std::move(name_str), std::move(dex_data));
  env->ReleaseByteArrayElements(dex_bytes, redef_bytes, 0);
}

}  // namespace art

