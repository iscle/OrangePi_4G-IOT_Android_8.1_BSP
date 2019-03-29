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

#include "android-base/logging.h"
#include "android-base/macros.h"
#include "jni_helper.h"
#include "jvmti_helper.h"
#include "jvmti.h"
#include "scoped_primitive_array.h"
#include "test_env.h"

namespace art {

extern "C" JNIEXPORT void JNICALL Java_android_jvmti_cts_JniBindings_setTag(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jobject obj, jlong tag) {
  jvmtiError ret = jvmti_env->SetTag(obj, tag);
  JvmtiErrorToException(env, jvmti_env, ret);
}

extern "C" JNIEXPORT jlong JNICALL Java_android_jvmti_cts_JniBindings_getTag(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jobject obj) {
  jlong tag = 0;
  jvmtiError ret = jvmti_env->GetTag(obj, &tag);
  if (JvmtiErrorToException(env, jvmti_env, ret)) {
    return 0;
  }
  return tag;
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_android_jvmti_cts_JvmtiTaggingTest_getTaggedObjects(
    JNIEnv* env,
    jclass klass ATTRIBUTE_UNUSED,
    jlongArray searchTags,
    jboolean returnObjects,
    jboolean returnTags) {
  ScopedLongArrayRO scoped_array(env);
  if (searchTags != nullptr) {
    scoped_array.reset(searchTags);
  }
  const jlong* tag_ptr = scoped_array.get();
  if (tag_ptr == nullptr) {
    // Can never pass null.
    tag_ptr = reinterpret_cast<const jlong*>(1);
  }

  jint result_count = -1;
  jobject* result_object_array = nullptr;
  jobject** result_object_array_ptr = returnObjects == JNI_TRUE ? &result_object_array : nullptr;
  jlong* result_tag_array = nullptr;
  jlong** result_tag_array_ptr = returnTags == JNI_TRUE ? &result_tag_array : nullptr;

  jvmtiError ret = jvmti_env->GetObjectsWithTags(scoped_array.size(),
                                                 tag_ptr,
                                                 &result_count,
                                                 result_object_array_ptr,
                                                 result_tag_array_ptr);
  if (JvmtiErrorToException(env, jvmti_env, ret)) {
    return nullptr;
  }

  CHECK_GE(result_count, 0);

  jobjectArray resultObjectArray = nullptr;
  if (returnObjects == JNI_TRUE) {
    auto callback = [&](jint i) {
      return result_object_array[i];
    };
    resultObjectArray = CreateObjectArray(env, result_count, "java/lang/Object", callback);
    if (resultObjectArray == nullptr) {
      return nullptr;
    }
  }
  if (result_object_array != nullptr) {
    CheckJvmtiError(jvmti_env, Deallocate(jvmti_env, result_object_array));
  }

  jlongArray resultTagArray = nullptr;
  if (returnTags == JNI_TRUE) {
    resultTagArray = env->NewLongArray(result_count);
    if (resultTagArray == nullptr) {
      return nullptr;
    }
    env->SetLongArrayRegion(resultTagArray, 0, result_count, result_tag_array);
  }
  if (result_tag_array != nullptr) {
    CheckJvmtiError(jvmti_env, Deallocate(jvmti_env, result_tag_array));
  }

  jobject count_integer;
  {
    ScopedLocalRef<jclass> integer_class(env, env->FindClass("java/lang/Integer"));
    jmethodID methodID = env->GetMethodID(integer_class.get(), "<init>", "(I)V");
    count_integer = env->NewObject(integer_class.get(), methodID, result_count);
    if (count_integer == nullptr) {
      return nullptr;
    }
  }

  auto callback = [&](jint i) -> jobject {
    switch(i) {
      case 0:
        return resultObjectArray;
      case 1:
        return resultTagArray;
      case 2:
        return count_integer;
      default:
        LOG(FATAL) << "Unexpected";
        return nullptr;
    }
  };
  return CreateObjectArray(env, 3, "java/lang/Object", callback);
}

}  // namespace art

