# Copyright (C) 2008 The Android Open Source Project
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

# Reusable Location test classes and helpers

include $(CLEAR_VARS)

LOCAL_MODULE := cts-location-tests

LOCAL_MODULE_TAGS := tests

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_STATIC_JAVA_LIBRARIES := \
    compatibility-device-util ctstestrunner apache-commons-math

LOCAL_SDK_VERSION := test_current

LOCAL_SRC_FILES := $(call all-java-files-under, src/android/location/cts) \
   $(call all-proto-files-under, protos)

LOCAL_PROTOC_OPTIMIZE_TYPE := nano

include $(BUILD_STATIC_JAVA_LIBRARY)

# CtsLocationTestCases package

include $(CLEAR_VARS)

# don't include this package in any target
LOCAL_MODULE_TAGS := optional
# and when built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_STATIC_JAVA_LIBRARIES := compatibility-device-util ctstestrunner  apache-commons-math

LOCAL_PROTOC_OPTIMIZE_TYPE := nano

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
   $(call all-proto-files-under, protos)

LOCAL_PACKAGE_NAME := CtsLocationTestCases

LOCAL_JACK_FLAGS := --multi-dex native
LOCAL_DX_FLAGS := --multi-dex

LOCAL_SDK_VERSION := test_current

include $(BUILD_CTS_PACKAGE)
