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

# don't include this package in any target
LOCAL_MODULE_TAGS := optional
# and when built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)
LOCAL_USE_AAPT2 := true

LOCAL_JNI_SHARED_LIBRARIES := libnativecursorwindow_jni libnativehelper_compat_libc++

LOCAL_JAVA_LIBRARIES := android.test.runner

LOCAL_STATIC_JAVA_LIBRARIES :=  \
    compatibility-device-util \
    ctstestrunner \
    services.core \
    junit \
    legacy-android-test \
    truth-prebuilt

LOCAL_STATIC_ANDROID_LIBRARIES := android-support-v4

# Use multi-dex as the compatibility-common-util-devicesidelib dependency
# on compatibility-device-util pushes us beyond 64k methods.
LOCAL_JACK_FLAGS := --multi-dex native
LOCAL_DX_FLAGS := --multi-dex

# Resource unit tests use a private locale and some densities
LOCAL_AAPT_INCLUDE_ALL_RESOURCES := true

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_MULTILIB := both
LOCAL_PACKAGE_NAME := CtsContentTestCases

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
