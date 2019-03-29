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

# Build the unit tests.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := CtsNativeNetTestCases
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/nativetest
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64

LOCAL_SRC_FILES := \
    src/NativeQtaguidTest.cpp

LOCAL_SHARED_LIBRARIES := \
    libutils \
    liblog \
    libcutils \

LOCAL_STATIC_LIBRARIES := \
    libgtest

LOCAL_CTS_TEST_PACKAGE := android.net.native
# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_CTS_EXECUTABLE)
