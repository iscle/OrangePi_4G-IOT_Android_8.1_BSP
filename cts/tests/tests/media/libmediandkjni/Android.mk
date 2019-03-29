# Copyright (C) 2012 The Android Open Source Project
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

#------------------------------------------------------------------------------
# Builds libctsmediacodec_jni.so
#
include $(CLEAR_VARS)

LOCAL_MODULE := libctsmediacodec_jni

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
  native-media-jni.cpp \
  codec-utils-jni.cpp  \
  md5_utils.cpp \
  native_media_utils.cpp \
  native_media_decoder_source.cpp \
  native_media_encoder_jni.cpp

LOCAL_C_INCLUDES := \
  $(JNI_H_INCLUDE) \
  system/core/include

LOCAL_C_INCLUDES += $(call include-path-for, mediandk)

LOCAL_SHARED_LIBRARIES := \
  libandroid libnativehelper_compat_libc++ \
  liblog libmediandk libEGL
LOCAL_NDK_STL_VARIANT := c++_static

LOCAL_SDK_VERSION := current

LOCAL_CFLAGS := -Werror -Wall -DEGL_EGLEXT_PROTOTYPES -std=gnu++14

include $(BUILD_SHARED_LIBRARY)

#------------------------------------------------------------------------------
# Builds libctsmediadrm_jni.so
#
include $(CLEAR_VARS)

LOCAL_MODULE := libctsmediadrm_jni

# Don't include this package in any configuration by default.
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
  CtsMediaDrmJniOnLoad.cpp \
  codec-utils-jni.cpp  \
  md5_utils.cpp \
  native-mediadrm-jni.cpp \

LOCAL_C_INCLUDES := \
  $(JNI_H_INCLUDE) \
  system/core/include


LOCAL_C_INCLUDES += $(call include-path-for, mediandk)

LOCAL_SHARED_LIBRARIES := \
  libandroid libnativehelper_compat_libc++ \
  liblog libmediandk libdl libEGL

LOCAL_SDK_VERSION := current

LOCAL_CFLAGS := -Werror -Wall -DEGL_EGLEXT_PROTOTYPES

LOCAL_NDK_STL_VARIANT := c++_static

include $(BUILD_SHARED_LIBRARY)
