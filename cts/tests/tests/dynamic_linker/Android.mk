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
LOCAL_MODULE := libdynamiclinker_native_lib_a
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := native_lib_a.cpp
LOCAL_C_INCLUDES := $(JNI_H_INCLUDE)
LOCAL_SDK_VERSION := 25
LOCAL_NDK_STL_VARIANT := c++_static
LOCAL_STRIP_MODULE := false
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libdynamiclinker_native_lib_b
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := native_lib_b.cpp
LOCAL_C_INCLUDES := $(JNI_H_INCLUDE)
LOCAL_SDK_VERSION := 25
LOCAL_NDK_STL_VARIANT := c++_static
LOCAL_STRIP_MODULE := false
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)
LOCAL_STATIC_JAVA_LIBRARIES := ctstestrunner legacy-android-test
LOCAL_SRC_FILES := $(call all-java-files-under, .)
LOCAL_MULTILIB := both
LOCAL_JNI_SHARED_LIBRARIES := libdynamiclinker_native_lib_a libdynamiclinker_native_lib_b
LOCAL_MANIFEST_FILE := AndroidManifest.xml
LOCAL_PACKAGE_NAME := CtsDynamicLinkerTestCases
LOCAL_SDK_VERSION := current
LOCAL_COMPATIBILITY_SUITE := cts vts
include $(BUILD_CTS_PACKAGE)
