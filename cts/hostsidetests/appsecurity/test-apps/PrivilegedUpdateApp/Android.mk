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

###########################################################
# Package w/ tests

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test compatibility-device-util ctstestrunner
# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DEX_PREOPT := false
LOCAL_PACKAGE_NAME := CtsPrivilegedUpdateTests

LOCAL_SRC_FILES := $(call all-java-files-under, src)

include $(BUILD_CTS_SUPPORT_PACKAGE)


###########################################################
# Variant: Privileged app upgrade

include $(CLEAR_VARS)

LOCAL_MODULE := CtsShimPrivUpgradePrebuilt
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_CLASS := APPS
LOCAL_BUILT_MODULE_STEM := package.apk
# Make sure the build system doesn't try to resign the APK
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

my_archs := arm x86
my_src_arch := $(call get-prebuilt-src-arch, $(my_archs))
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/apk/$(my_src_arch)/CtsShimPrivUpgrade.apk

include $(BUILD_PREBUILT)

###########################################################
# Variant: Privileged app upgrade (wrong SHA)

include $(CLEAR_VARS)

LOCAL_MODULE := CtsShimPrivUpgradeWrongSHAPrebuilt
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_CLASS := APPS
LOCAL_BUILT_MODULE_STEM := package.apk
# Make sure the build system doesn't try to resign the APK
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

my_archs := arm x86
my_src_arch := $(call get-prebuilt-src-arch, $(my_archs))
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/apk/$(my_src_arch)/CtsShimPrivUpgradeWrongSHA.apk

include $(BUILD_PREBUILT)
