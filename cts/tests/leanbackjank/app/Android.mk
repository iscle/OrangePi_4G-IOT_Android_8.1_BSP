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

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsLeanbackJankApp

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_RESOURCE_DIR := \
    $(TOP)/frameworks/support/v17/leanback/res \
    $(TOP)/frameworks/support/v7/recyclerview/res \
    $(LOCAL_PATH)/res

LOCAL_STATIC_JAVA_LIBRARIES := \
    compatibility-device-util \
    ctstestrunner \
    ub-uiautomator \
    ub-janktesthelper \
    android-support-v4 \
    android-support-v7-recyclerview \
    android-support-v17-leanback \
    glide

LOCAL_AAPT_FLAGS := \
        --auto-add-overlay \
        --extra-packages android.support.v17.leanback \
        --extra-packages android.support.v7.recyclerview

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_CTS_SUPPORT_PACKAGE)
