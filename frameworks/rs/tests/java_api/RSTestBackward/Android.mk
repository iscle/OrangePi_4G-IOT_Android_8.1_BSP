#
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
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := RSTestBackward
LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src)\
    $(call all-java-files-under, ../RSUnitTests/src)\
    $(call all-renderscript-files-under, ../RSUnitTests/src)\

LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
LOCAL_COMPATIBILITY_SUITE := device-tests

LOCAL_RENDERSCRIPT_TARGET_API := 0
LOCAL_MIN_SDK_VERSION := 21

include $(BUILD_PACKAGE)

