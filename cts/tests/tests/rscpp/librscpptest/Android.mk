# Copyright (C) 2013 The Android Open Source Project
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

#
# This is the shared library included by the JNI test app.
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
LOCAL_CLANG := true
LOCAL_MODULE := librscpptest_jni
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    rs_jni.cpp \
    rs_jni_allocation.cpp \
    rs_jni_element.cpp \
    rs_jni_foreach.cpp \
    rs_jni_script.cpp \
    rs_jni_type.cpp \
    rs_jni_object.cpp

LOCAL_SRC_FILES += \
    setelementat.rs \
    primitives.rs \
    instance.rs \
    clear_object.rs \
    foreach.rs \
    fe_all.rs \
    noroot.rs \
    vector.rs

LOCAL_C_INCLUDES := $(JNI_H_INCLUDE)
LOCAL_C_INCLUDES += frameworks/rs/cpp
LOCAL_C_INCLUDES += frameworks/rs

LOCAL_CPPFLAGS := -std=c++11
LOCAL_CFLAGS := -Wno-unused-parameter

LOCAL_SHARED_LIBRARIES := libdl liblog
LOCAL_STATIC_LIBRARIES := libRScpp_static

LOCAL_SDK_VERSION := 21

LOCAL_NDK_STL_VARIANT := stlport_static

include $(BUILD_SHARED_LIBRARY)



