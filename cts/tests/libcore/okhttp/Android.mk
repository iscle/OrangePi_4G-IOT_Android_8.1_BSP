# Copyright (C) 2016 The Android Open Source Project
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

LOCAL_PACKAGE_NAME := CtsLibcoreOkHttpTestCases

LOCAL_STATIC_JAVA_LIBRARIES := \
    bouncycastle-nojarjar \
    cts-core-test-runner \
    okhttp-nojarjar \
    okhttp-tests-nojarjar

# Don't include this package in any target
LOCAL_MODULE_TAGS := tests

# When built, explicitly put it in the data partition.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_DEX_PREOPT := false
LOCAL_JACK_FLAGS := --multi-dex native

LOCAL_PROGUARD_ENABLED := disabled

# Include both the 32 and 64 bit versions of libjavacoretests,
# where applicable.
LOCAL_MULTILIB := both

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_JAVA_RESOURCE_FILES := libcore/expectations/knownfailures.txt

include $(BUILD_CTS_SUPPORT_PACKAGE)
