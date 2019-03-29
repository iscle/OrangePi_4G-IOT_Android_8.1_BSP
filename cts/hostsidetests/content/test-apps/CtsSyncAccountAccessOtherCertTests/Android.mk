#
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
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    ctstestrunner \
    ub-uiautomator \
    compatibility-device-util

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
  ../CtsSyncAccountAccessSameCertTests/src/com/android/cts/content/StubActivity.java \
  ../CtsSyncAccountAccessSameCertTests/src/com/android/cts/content/SyncAdapter.java \
  ../CtsSyncAccountAccessSameCertTests/src/com/android/cts/content/SyncService.java \
  ../CtsSyncAccountAccessSameCertTests/src/com/android/cts/content/FlakyTestRule.java

LOCAL_PACKAGE_NAME := CtsSyncAccountAccessOtherCertTestCases

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey2

LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

include $(BUILD_CTS_SUPPORT_PACKAGE)
