# Copyright 2017 The Android Open Source Project
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
LOCAL_MODULE := CtsNativeMediaAAudioTestCases
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/nativetest
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64

LOCAL_SRC_FILES := \
    src/test_aaudio.cpp \
    src/test_aaudio_callback.cpp \
    src/test_aaudio_misc.cpp \
    src/test_aaudio_mmap.cpp \
    src/test_aaudio_stream_builder.cpp \
    src/utils.cpp \

LOCAL_SHARED_LIBRARIES := \
    libaaudio \
    liblog \

LOCAL_STATIC_LIBRARIES := \
    libgtest_ndk_c++ \

LOCAL_CTS_TEST_PACKAGE := android.nativemedia.aaudio

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_CFLAGS := -Werror -Wall

LOCAL_SDK_VERSION := current
LOCAL_NDK_STL_VARIANT := c++_static

include $(BUILD_CTS_EXECUTABLE)
