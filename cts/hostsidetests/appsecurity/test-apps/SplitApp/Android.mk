#
# Copyright (C) 2014 The Android Open Source Project
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
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test legacy-android-test

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsSplitApp
LOCAL_PACKAGE_SPLITS := mdpi-v4 hdpi-v4 xhdpi-v4 xxhdpi-v4 v7 fr de

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_ASSET_DIR := $(LOCAL_PATH)/assets

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey1
LOCAL_AAPT_FLAGS := --version-code 100 --version-name OneHundred --replace-version

LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DEX_PREOPT := false

include $(BUILD_CTS_SUPPORT_PACKAGE)


#################################################
# Define a variant with a different revision code

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test legacy-android-test

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsSplitAppDiffRevision
LOCAL_PACKAGE_SPLITS := v7

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_MANIFEST_FILE := revision/AndroidManifest.xml
LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey1
LOCAL_AAPT_FLAGS := --version-code 100 --version-name OneHundredRevisionTwelve --replace-version

LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DEX_PREOPT := false

include $(BUILD_CTS_SUPPORT_PACKAGE)


################################################
# Define a variant with a different version code

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test legacy-android-test

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsSplitAppDiffVersion
LOCAL_PACKAGE_SPLITS := v7

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey1
LOCAL_AAPT_FLAGS := --version-code 101 --version-name OneHundredOne --replace-version

LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DEX_PREOPT := false

include $(BUILD_CTS_SUPPORT_PACKAGE)


################################################
# Define a variant with a different signature

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test legacy-android-test

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsSplitAppDiffCert
LOCAL_PACKAGE_SPLITS := v7

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey2
LOCAL_AAPT_FLAGS := --version-code 100 --version-name OneHundred --replace-version

LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DEX_PREOPT := false

include $(BUILD_CTS_SUPPORT_PACKAGE)


ifeq (,$(ONE_SHOT_MAKEFILE))
include $(LOCAL_PATH)/libs/Android.mk $(LOCAL_PATH)/feature/Android.mk
endif
