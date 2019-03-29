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
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libctsvrextensions_jni

LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS += -Werror -Wall -Wextra -std=c++11

LOCAL_SRC_FILES := VrExtensionsJni.cpp

LOCAL_C_INCLUDES := $(JNI_H_INCLUDE) $(call include-path-for, system-core) frameworks/native/opengl/include

LOCAL_SHARED_LIBRARIES := libandroid libnativehelper_compat_libc++ liblog libEGL libGLESv2

LOCAL_SDK_VERSION := current

LOCAL_NDK_STL_VARIANT := c++_static

include $(BUILD_SHARED_LIBRARY)
