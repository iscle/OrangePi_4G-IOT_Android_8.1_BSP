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

# IMPORTANT: We build two apps from the same source but with different package name.
# This allow us to have different device owner and profile owner, some APIs may behave differently
# in this situation.

# === App 1 ===
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsCorpOwnedManagedProfile

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
                   $(call all-Iaidl-files-under, src)

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/src

LOCAL_JAVA_LIBRARIES := android.test.runner cts-junit

LOCAL_STATIC_JAVA_LIBRARIES := ctstestrunner compatibility-device-util

LOCAL_SDK_VERSION := test_current

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_PACKAGE)

# === App 2 ===
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsCorpOwnedManagedProfile2

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
                   $(call all-Iaidl-files-under, src)

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/src

LOCAL_JAVA_LIBRARIES := android.test.runner cts-junit

LOCAL_STATIC_JAVA_LIBRARIES := ctstestrunner compatibility-device-util

LOCAL_SDK_VERSION := test_current

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests
LOCAL_AAPT_FLAGS += --rename-manifest-package com.android.cts.comp2 \
                    --rename-instrumentation-target-package com.android.cts.comp2

include $(BUILD_CTS_PACKAGE)
