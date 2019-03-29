# Copyright 2013 The Android Open Source Project
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

LOCAL_MODULE_TAGS := tests

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_JAVA_LIBRARIES := bouncycastle

LOCAL_STATIC_JAVA_LIBRARIES := \
        android-support-test \
        core-tests-support \
        compatibility-device-util \
        ctstestrunner \
        guava \
        junit \
        legacy-android-test

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JACK_FLAGS := --multi-dex native

LOCAL_PACKAGE_NAME := CtsKeystoreTestCases

# Can't use public/test API only because some tests use hidden API
# (e.g. device-provided Bouncy Castle).
#
# The comment below is not particularly accurate, but it's copied from other
# tests that do the same thing, so anyone grepping for it will find it here.
#
# Uncomment when b/13282254 is fixed.
# LOCAL_SDK_VERSION := current
LOCAL_JAVA_LIBRARIES += android.test.runner

include $(BUILD_CTS_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
