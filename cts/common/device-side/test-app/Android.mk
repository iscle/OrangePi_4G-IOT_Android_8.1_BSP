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

# Build an APK which contains the device-side libraries and their tests,
# this then gets instrumented in order to test the aforementioned libraries.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_DEX_PREOPT := false
LOCAL_PROGUARD_ENABLED := disabled
# don't include this package in any target
LOCAL_MODULE_TAGS := optional
# and when built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-test\
    compatibility-common-util-devicesidelib\
    compatibility-device-info-tests\
    compatibility-device-info\
    compatibility-device-util-tests\
    compatibility-device-util

LOCAL_PACKAGE_NAME := CompatibilityTestApp

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
