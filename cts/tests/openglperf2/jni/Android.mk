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
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libctsopengl_jni

# Needed in order to use fences for synchronization
LOCAL_CFLAGS += -DEGL_EGLEXT_PROTOTYPES -funsigned-char

LOCAL_CFLAGS += -Wno-unused-parameter

# Get all cpp files but not hidden files
LOCAL_SRC_FILES := $(call all-subdir-cpp-files)

LOCAL_C_INCLUDES := $(JNI_H_INCLUDE)

LOCAL_SHARED_LIBRARIES := libEGL libGLESv2 libandroid liblog

LOCAL_CXX_STL := libc++_static

# TODO (dimitry): replace LOCAL_CXX_STL with LOCAL_SDK_VERSION+LOCAL_NDK_STL_VARIANT
# LOCAL_SDK_VERSION := 23
# LOCAL_NDK_STL_VARIANT := c++_static

include $(BUILD_SHARED_LIBRARY)
