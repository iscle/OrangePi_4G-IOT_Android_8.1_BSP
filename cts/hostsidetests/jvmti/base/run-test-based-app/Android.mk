# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := CtsJvmtiDeviceRunTestAppBase

# We explicitly enumerate, as we have a definition of art.Main to simplify development
# in an IDE (but want the implementation of said class to come from the ART run-tests).
LOCAL_SRC_FILES := \
  src/android/jvmti/cts/JvmtiRunTestBasedTest.java \

LOCAL_SDK_VERSION := current
LOCAL_DEX_PREOPT := false
LOCAL_JAVA_LIBRARIES := android.test.runner cts-junit
LOCAL_STATIC_JAVA_LIBRARIES := CtsJvmtiDeviceAppBase
LOCAL_STATIC_JAVA_LIBRARIES += run-test-jvmti-java
LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_STATIC_JAVA_LIBRARY)
