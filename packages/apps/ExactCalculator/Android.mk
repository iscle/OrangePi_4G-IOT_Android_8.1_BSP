#
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
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_RESOURCE_DIR := packages/apps/ExactCalculator/res

ifeq ($(TARGET_BUILD_APPS),)
LOCAL_RESOURCE_DIR += frameworks/support/v7/gridlayout/res
LOCAL_RESOURCE_DIR += frameworks/support/v7/recyclerview/res
else
LOCAL_RESOURCE_DIR += prebuilts/sdk/current/support/v7/gridlayout/res
LOCAL_RESOURCE_DIR += prebuilts/sdk/current/support/v7/recyclerview/res
endif

LOCAL_MODULE_TAGS := optional
LOCAL_SDK_VERSION := current

LOCAL_PACKAGE_NAME := ExactCalculator
LOCAL_OVERRIDES_PACKAGES := Calculator

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_PROGUARD_FLAG_FILES += ../../../frameworks/support/v7/recyclerview/proguard-rules.pro

LOCAL_STATIC_JAVA_LIBRARIES := cr
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-gridlayout
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-recyclerview

LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.gridlayout
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.recyclerview

include $(BUILD_PACKAGE)
