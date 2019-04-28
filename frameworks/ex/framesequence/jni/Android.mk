#
# Copyright (C) 2014 The Android Open Source Project
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

## Main library

LOCAL_STATIC_LIBRARIES = libgif

LOCAL_LDFLAGS := -llog -ljnigraphics

LOCAL_C_INCLUDES := \
	external/giflib

LOCAL_MODULE    := libframesequence
LOCAL_SRC_FILES := \
	BitmapDecoderJNI.cpp \
	FrameSequence.cpp \
	FrameSequenceJNI.cpp \
	FrameSequence_gif.cpp \
	JNIHelpers.cpp \
	Registry.cpp \
	Stream.cpp

ifeq ($(FRAMESEQUENCE_INCLUDE_WEBP),true)
	LOCAL_C_INCLUDES += external/webp/include
	LOCAL_SRC_FILES += FrameSequence_webp.cpp
	LOCAL_STATIC_LIBRARIES += libwebp-decode
endif

LOCAL_CFLAGS += -Wall -Wno-unused-parameter -Wno-unused-variable -Wno-overloaded-virtual
LOCAL_CFLAGS += -fvisibility=hidden

LOCAL_SDK_VERSION := 8

include $(BUILD_SHARED_LIBRARY)
