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

LOCAL_MODULE := libctsjvmtiagent

# Don't include this package in any configuration by default.
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := cts_agent.cpp

# Tagging.
LOCAL_SRC_FILES += tagging.cpp

# Tracking.
LOCAL_SRC_FILES += tracking.cpp

# redefinition.
LOCAL_SRC_FILES += redefine.cpp

LOCAL_C_INCLUDES := $(JNI_H_INCLUDE)
LOCAL_HEADER_LIBRARIES := libopenjdkjvmti_headers

LOCAL_SHARED_LIBRARIES := liblog \
                          libdl

# The test implementation. We get this provided by ART.
# Note: Needs to be "whole" as this exposes JNI functions.
LOCAL_WHOLE_STATIC_LIBRARIES := libctstiagent

# Platform libraries that are not available to apps. Link in statically.
LOCAL_STATIC_LIBRARIES += libbase

LOCAL_STRIP_MODULE := keep_symbols

# Turn on all warnings.
LOCAL_CFLAGS :=  -fno-rtti \
                 -ggdb3 \
                 -Wall \
                 -Wextra \
                 -Werror \
                 -Wunreachable-code \
                 -Wredundant-decls \
                 -Wshadow \
                 -Wunused \
                 -Wimplicit-fallthrough \
                 -Wfloat-equal \
                 -Wint-to-void-pointer-cast \
                 -Wused-but-marked-unused \
                 -Wdeprecated \
                 -Wunreachable-code-break \
                 -Wunreachable-code-return \
                 -g \
                 -O0 \

LOCAL_CXX_STL := libc++_static

include $(BUILD_SHARED_LIBRARY)
