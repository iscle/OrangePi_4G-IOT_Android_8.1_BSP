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
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := \
        $(LOCAL_PATH)/res \
        $(TOP)/frameworks/support/v7/recyclerview/res \

LOCAL_PACKAGE_NAME := CarDiagnosticVerifier

LOCAL_AAPT_FLAGS := --auto-add-overlay \
        --extra-packages android.support.v7.recyclerview \

LOCAL_JAVA_VERSION := 1.8

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

LOCAL_PRIVILEGED_MODULE := true

LOCAL_STATIC_JAVA_LIBRARIES += \
        vehicle-hal-support-lib \
        android-support-v4 \
        android-support-v7-recyclerview \

LOCAL_JAVA_LIBRARIES += android.car

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
