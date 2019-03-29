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

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsTelecomTestCases2

# Don't include this package in any target.
LOCAL_MODULE_TAGS := optional

# When built, explicitly put it in the data partition.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_STATIC_JAVA_LIBRARIES := \
	compatibility-device-util \
	ctstestrunner \
	legacy-android-test

src_dirs := src \
    ../telecom/src/android/telecom/cts/SelfManagedConnection.java \
    ../telecom/src/android/telecom/cts/CtsSelfManagedConnectionService.java \
    ../telecom/src/android/telecom/cts/TestUtils.java

res_dirs := res \
    ../telecom/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.telecom.cts \
    --rename-manifest-package android.telecom2.cts \

LOCAL_SDK_VERSION := current

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_PACKAGE)
