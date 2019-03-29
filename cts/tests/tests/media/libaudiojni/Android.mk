# Copyright (C) 2015 The Android Open Source Project
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

LOCAL_MODULE    := libaudio_jni

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
	appendix-b-1-1-buffer-queue.cpp \
	appendix-b-1-2-recording.cpp \
	audio-record-native.cpp \
	audio-track-native.cpp \
	sl-utils.cpp

LOCAL_C_INCLUDES := \
	$(JNI_H_INCLUDE) \
	system/core/include

LOCAL_C_INCLUDES += $(call include-path-for, libaudiojni) \
	$(call include-path-for, wilhelm)

LOCAL_SHARED_LIBRARIES := libandroid liblog libnativehelper_compat_libc++ libOpenSLES
LOCAL_CXX_STL := libc++_static

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_SHARED_LIBRARY)
